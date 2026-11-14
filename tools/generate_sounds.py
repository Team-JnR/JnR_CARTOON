#!/usr/bin/env python3
"""
CARTOON 사운드 팩 음원 생성 스크립트 (개발용, 앱 실행 중에는 쓰지 않음)

상황별 효과음을 미리 만들어 app/src/main/res/raw 에 넣는다.
  talk_*    '말하는 차'   macOS 시스템 음성(Yuna)으로 만든 임시 보이스
  retro_*   '레트로 게임'  코드로 합성한 8비트 효과음 (외부 음원 없음)

모든 음원 기준: 2초 이내(깜빡이·와이퍼는 동작 주기보다 짧게), 앞에 묵음(보이스 150ms·효과음 40ms), 피크 정규화, 22.05kHz 16bit 모노 WAV.
보이스는 프로토타입용 임시 음원이며, 정식 배포 전 팀이 녹음한 음원으로 같은 파일명에 덮어쓴다.

필요 도구: macOS `say`(한국어 Yuna 음성), `afconvert`, Python 3 표준 라이브러리
사용법: python3 tools/generate_sounds.py
"""
import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import wave

SR = 22050
MAX_SEC = 2.0
LEAD_MS = 40
# Pleos 'TTS / 음성 안내 선행 묵음 적용 가이드': 음성 안내는 150~200ms 선행 묵음 권장
# (trim이 남기는 20ms와 합쳐 약 170ms)
VOICE_LEAD_MS = 150
TAIL_MS = 30
PEAK = 0.89

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "raw")

# 앱의 CarAction 이름(소문자)과 1:1로 맞춘다
TALK = {
    "left_turn": "왼쪽!",
    "right_turn": "오른쪽!",
    "wiper_slow": "스윽, 싹.",
    "wiper_fast": "슥싹!",
    "depart": "부릉부릉~",
    "arrive": "정지!",
    "headlight": "반짝!",
    "window_open": "위이잉~",
    "window_close": "위잉, 탁!",
}


# 동작 1회에 소리 1번: 반복 동작의 소리는 다음 동작 전에 끝나야 한다 (앱 CarAction.maxSoundMs와 같은 값)
#   깜빡이 0.7초 주기 → 0.64초, 와이퍼 고속 0.9초 → 0.82초, 저속 1.3초 → 1.2초
MAX_SEC_BY_ACTION = {"left_turn": 0.64, "right_turn": 0.64, "wiper_fast": 0.82, "wiper_slow": 1.2}



# ── 공통 ──────────────────────────────────────────────────────
def silence(ms):
    return [0.0] * int(SR * ms / 1000)


def finish(samples, lead_ms=LEAD_MS, max_sec=MAX_SEC):
    peak = max((abs(s) for s in samples), default=0) or 1.0
    body = [s / peak * PEAK for s in samples]
    out = silence(lead_ms) + body + silence(TAIL_MS)
    if len(out) > SR * max_sec:
        raise ValueError(f"{len(out) / SR:.2f}s > {max_sec}s")
    return out


def write_wav(path, samples):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))


def read_wav(path):
    with wave.open(path, "rb") as w:
        assert w.getnchannels() == 1 and w.getsampwidth() == 2 and w.getframerate() == SR
        raw = w.readframes(w.getnframes())
    return [v / 32768 for v in struct.unpack(f"<{len(raw) // 2}h", raw)]


# ── 보이스 ────────────────────────────────────────────────────
def say(text, rate):
    with tempfile.TemporaryDirectory() as d:
        aiff, wav = os.path.join(d, "v.aiff"), os.path.join(d, "v.wav")
        subprocess.run(["say", "-v", "Yuna", "-r", str(rate), "-o", aiff, text], check=True)
        subprocess.run(["afconvert", "-f", "WAVE", "-d", f"LEI16@{SR}", "-c", "1", aiff, wav], check=True)
        return read_wav(wav)


def trim(samples, threshold=0.01):
    idx = [i for i, s in enumerate(samples) if abs(s) > threshold]
    if not idx:
        return samples
    pad = int(SR * 0.02)
    return samples[max(0, idx[0] - pad): idx[-1] + pad]


def pitch_up(samples, factor):
    n = int(len(samples) / factor)
    out = []
    for i in range(n):
        x = i * factor
        j = int(x)
        f = x - j
        a = samples[j]
        b = samples[j + 1] if j + 1 < len(samples) else a
        out.append(a + (b - a) * f)
    return out


def make_voice(text, pitch=1.0, max_sec=MAX_SEC):
    # 제한 시간 안에 들어갈 때까지 말 빠르기를 올린다
    for rate in (200, 230, 260, 290, 320, 360, 400, 440):
        s = trim(say(text, rate))
        if pitch != 1.0:
            s = pitch_up(s, pitch)
        if (len(s) + SR * (VOICE_LEAD_MS + TAIL_MS) / 1000) <= SR * max_sec:
            return finish(s, VOICE_LEAD_MS, max_sec)
    raise ValueError(f"too long even at max rate: {text}")


# ── 8비트 효과음 ──────────────────────────────────────────────
def envelope(i, n):
    fade = min(n // 4, SR // 200)
    if fade == 0:
        return 1.0
    if i < fade:
        return i / fade
    if i > n - fade:
        return (n - i) / fade
    return 1.0


def tone(freq, ms, wave_type="square", amp=0.5, decay=0.0):
    n = int(SR * ms / 1000)
    out = []
    for i in range(n):
        t = i / SR
        phase = (t * freq) % 1.0
        if wave_type == "square":
            v = 1.0 if phase < 0.25 else -1.0  # 25% 듀티: 8비트 느낌
        else:
            v = 4.0 * abs(phase - 0.5) - 1.0
        out.append(v * amp * envelope(i, n) * math.exp(-decay * t))
    return out


def glide(f_from, f_to, ms):
    n = int(SR * ms / 1000)
    out, phase = [], 0.0
    for i in range(n):
        p, t = i / n, i / SR
        f = f_from * (f_to / f_from) ** p * (1 + 0.03 * math.sin(2 * math.pi * 18 * t))
        phase = (phase + f / SR) % 1.0
        out.append((1.0 if phase < 0.5 else -1.0) * 0.4 * envelope(i, n))
    return out


def sweep_noise(ms, rising, seed):
    n = int(SR * ms / 1000)
    rnd = random.Random(seed)
    lp, out = 0.0, []
    for i in range(n):
        p = i / n
        cutoff = 0.08 + 0.5 * p if rising else 0.58 - 0.5 * p
        lp += cutoff * (rnd.uniform(-1, 1) - lp)
        out.append(max(-1.0, min(1.0, lp * 1.6 * math.sin(math.pi * p))))
    return out


def retro(name):
    s = []
    if name == "left_turn":  # 깜빡임 1회 = "삐릭" 1번 (내려가는 음)
        for f in (1760, 1397, 1175):
            s += tone(f, 55)
    elif name == "right_turn":  # 깜빡임 1회 = "삐릭" 1번 (올라가는 음)
        for f in (1175, 1397, 1760):
            s += tone(f, 55)
    elif name == "wiper_slow":  # 왕복 1회 = "쓱— 싹" (가고 오기)
        s += sweep_noise(320, True, 1) + silence(140) + sweep_noise(260, False, 2)
    elif name == "wiper_fast":  # 빠른 왕복 1회 = "쓱싹"
        s += sweep_noise(180, True, 10) + silence(60) + sweep_noise(160, False, 11)
    elif name == "depart":  # 게임 시작 "레디, 고!"
        for f in (523, 659, 784):
            s += tone(f, 110, amp=0.45) + silence(30)
        s += tone(1047, 500, decay=3.0)
    elif name == "arrive":  # 스테이지 클리어 팡파르 (자체 작곡)
        for f, d in ((392, 120), (523, 120), (659, 120), (784, 240), (659, 120), (784, 600)):
            s += tone(f, d, amp=0.45, decay=1.5)
    elif name == "headlight":  # 별 획득
        for f in (1047, 1319, 1568, 2093, 1568, 2093):
            s += tone(f, 60, "triangle", 0.6)
        s += tone(2637, 300, "triangle", 0.5, decay=8.0)
    elif name == "window_open":  # 파워업
        s += glide(300, 1400, 700)
    elif name == "window_close":  # 파워다운 + 끝에 '탁' (다 닫힘)
        s += glide(1400, 300, 600) + silence(40) + tone(150, 90, decay=25.0)
    else:
        raise KeyError(name)
    return finish(s, max_sec=MAX_SEC_BY_ACTION.get(name, MAX_SEC))


def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = []
    for k, text in TALK.items():
        jobs.append((f"talk_{k}", lambda t=text, m=MAX_SEC_BY_ACTION.get(k, MAX_SEC): make_voice(t, max_sec=m)))
    for k in TALK:
        jobs.append((f"retro_{k}", lambda n=k: retro(n)))

    for name, fn in jobs:
        samples = fn()
        write_wav(os.path.join(OUT, f"{name}.wav"), samples)
        print(f"{name:24s} {len(samples) / SR:.2f}s")


if __name__ == "__main__":
    sys.exit(main())
