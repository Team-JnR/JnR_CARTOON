# CARTOON (카툰)

> 깜빡이·와이퍼·기어 같은 차량의 평범한 동작에 "왼쪽! 왼쪽! 왼쪽!", "슥싹슥싹!" 같은 짧고 재미있는 소리를 입혀, 매일 하는 운전을 만화처럼 즐겁게 바꿔 주는 Pleos Connect 차량용 사운드 앱입니다.

CARTOON = Car(자동차) + Cartoon(만화). 현대자동차그룹 차량용 앱 개발 공모전 출품작의 **프로토타입**으로, "차량 동작이 바뀌면 고른 사운드 팩의 소리를 한 번 낸다"는 기본 동작을 담았습니다.

## APK

- 위치: [`apk/JnR_CARTOON_release.apk`](apk/JnR_CARTOON_release.apk)
- 설치: `adb install -r -g apk/JnR_CARTOON_release.apk`
  - `-g`를 붙이면 설치할 때 차량 조회 권한(`pleos.car.permission.*`)을 함께 허용합니다. 빼고 설치하면 첫 실행 때 권한을 요청합니다.

## 테스트 환경

| 항목 | 내용 |
|---|---|
| 에뮬레이터 | **Pleos Connect Emulator v2.1.0** (AVD `Pleos_Connect_v2.1.0`, Android API 34, arm64-v8a) |
| 화면 규격 | **2560 × 1440 px, 160 dpi** (앱은 런처 오른쪽 영역, 약 1700 × 1220 px에 표시됨) |
| Pleos SDK | Vehicle SDK `ai.pleos.playground:Vehicle:2.3.2` |
| 빌드 | Android Studio, AGP 9.4.1, Gradle 9.6, Kotlin 2.2.10 (AGP 내장), Jetpack Compose (Material 3) |
| SDK 버전 | minSdk 28, targetSdk/compileSdk 37 |

## 실행 방법

1. Pleos Connect 에뮬레이터(v2.1.0, Cold boot)를 실행합니다.
2. 빌드 후 설치합니다.
   ```bash
   ./gradlew :app:assembleRelease
   adb install -r -g app/build/outputs/apk/release/app-release.apk
   ```
3. 앱 목록에서 **CARTOON**을 실행합니다. 상단에 `Pleos Vehicle SDK 연결됨`이 보이면 실제 차량 신호를 받고 있는 상태입니다.
4. 차량 신호를 바꾸는 방법은 두 가지입니다.
   - **Virtual Console** (실제 Vehicle SDK 경로):
     ```bash
     adb forward tcp:8088 tcp:8088
     ```
     브라우저에서 `http://127.0.0.1:8088/`을 열고 Turn Signal State(Blink left/right), Front Wiper(Low/High), 기어, 하향등, 창문을 바꿉니다.
   - **앱 안의 차량 시뮬레이터** (시연용): 오른쪽 패널에서 기어 P/R/N/D, 방향지시등, 와이퍼, 전조등, 창문 신호를 바로 넣을 수 있습니다. Pleos 차량 서비스가 없는 기기에서는 자동으로 시뮬레이션 모드로 동작합니다.

## 사운드 팩

메인 화면 아래쪽의 사운드 팩 카드를 눌러 골라 두면 모든 동작에 그 팩의 소리가 납니다. 카드 오른쪽 위 **⚙ 설정**에서 그 팩의 동작별 소리를 보고 `▶ 듣기`로 미리 들어 볼 수 있습니다. 팩 바꾸기와 설정 화면은 **주차(P) 중에만** 쓸 수 있고, 설정 화면을 연 채 주행을 시작하면 자동으로 닫힙니다. 기어가 P여도 플랫폼 주행 규제(`CarUxRestrictions`의 `UX_RESTRICTIONS_NO_SETUP`)가 걸려 있으면 잠급니다.

**차량 동작 1회에 소리 1번**이 대응합니다.

| 차량 동작 | 소리가 나는 때 | 🗣️ 말하는 차 | 👾 레트로 게임 |
|---|---|---|---|
| 왼쪽 깜빡이 | 켜져 있는 동안 **깜빡임(째깍)마다** | "왼쪽!" | 삐릭 (왼쪽에서) |
| 오른쪽 깜빡이 | 켜져 있는 동안 **깜빡임(째깍)마다** | "오른쪽!" | 삐릭 (오른쪽에서) |
| 와이퍼 (저속·간헐) | 작동하는 동안 **왕복마다** | "스윽— 싹" | 빗자루 쓱— 싹 |
| 와이퍼 (고속) | 작동하는 동안 **왕복마다** | "슥싹!" | 빗자루 쓱싹 |
| 출발 | 기어 P → D 할 때 | "부릉부릉~" | 게임 시작 "레디, 고!" |
| 정지 | 기어 D → P 할 때 | "정지!" | 스테이지 클리어 팡파르 |
| 전조등 | 켤 때마다 | "반짝!" | 별 획득음 |
| 창문 열기 | 창문 한 짝이 열릴 때마다 | "위이잉~" | 파워업 효과음 |
| 창문 닫기 | 열려 있던 창문 한 짝이 다 닫힐 때마다 | "위잉~ 탁!" | 파워다운 효과음 |

- **깜빡이·와이퍼 타이밍**: Vehicle SDK는 방향지시등을 레버 상태(왼쪽/오른쪽/꺼짐)로만, 와이퍼를 작동 여부와 스위치 단계로만 알려 주고 깜빡임·왕복마다 신호를 주지 않습니다(Virtual Console로 확인). 그래서 앱이 일반적인 주기로 박자를 만듭니다.
  - 깜빡이: 0.7초마다(분당 약 86회, 국제 기준 60~120회/분). 차량이 깜빡임마다 켜짐·꺼짐을 보내는 경우에는 '켜짐'마다 1번이 되어 차량 박자에 그대로 맞습니다.
  - 와이퍼: 저속 1.3초, 고속 0.9초, 간헐 1~5단 8·6·4.5·3·2초, 자동 3초, 1회 닦기(MIST)는 1번. 실차 주기와 다를 수 있는 추정값이며 `model/VehicleSignal.kt`, `vehicle/PleosVehicleSource.kt`에서 조정합니다.
  - 반복 소리는 다음 깜빡임·왕복 전에 끝나도록 깜빡이 0.64초, 와이퍼 고속 0.82초, 저속 1.2초 이내로 만들었습니다(`SoundAssetsTest`가 검사).
- 앱을 켰을 때 이미 켜져 있던 상태로는 '켜는 소리'를 내지 않습니다. 이미 동작 중인 깜빡이·와이퍼는 다음 주기부터 소리를 냅니다.
- 소리는 최대 2초입니다. 재생 전에 일시적 오디오 포커스(덕킹 허용)를 요청하고, 포커스를 받지 못하면 재생하지 않습니다. 깜빡이·와이퍼처럼 소리가 이어지는 동안에는 포커스를 계속 쥐고 있다가 마지막 소리 0.6초 뒤에 반납합니다. 그래서 음악이 소리마다 작아졌다 커졌다 하지 않고, 이어지는 동안 한 번 작아졌다가 돌아옵니다.
- 메인 화면 위쪽 가운데의 **`🔊 소리 켜짐 / 🔇 소리 꺼짐`** 버튼으로 켜고 끕니다(기본: 켜짐, 설정은 저장됨).
  - 켜져 있는 동안에는 포그라운드 서비스로 동작하므로 앱 화면을 닫거나 내비 등 다른 앱을 보고 있어도 소리가 납니다.
  - 끄면 서비스를 종료하고 Vehicle SDK 구독과 음원(SoundPool)을 모두 해제합니다. 알림의 `소리 끄기` 버튼으로도 끌 수 있습니다.
- Pleos '미사용 앱 BG→CCH 전환 가이드'(필수 항목)에 따라 **부팅 때 자동으로 시작하지 않습니다.** 사용자가 앱을 열었을 때, 소리가 켜져 있는 경우에만 서비스가 시작됩니다.
- 끈 뒤 다른 앱으로 전환하면 프로세스가 cached 상태로 내려가는 것을 `adb shell dumpsys activity lru`로 확인했습니다(`cch+20 CAC`, 서비스 0개).

## 음원

모든 소리는 앱 실행 중에 만들지 않고, **미리 만든 파일**을 `app/src/main/res/raw/`에 넣어 두었습니다. 앱이 시작할 때 18개(팩 2종 × 동작 9가지)를 모두 SoundPool에 올려 두므로 신호가 바뀌면 바로 재생됩니다.

| 파일 | 내용 | 만든 방법 |
|---|---|---|
| `talk_*.wav` | 말하는 차 | macOS 시스템 음성(Yuna)으로 만든 **임시 보이스** |
| `retro_*.wav` | 레트로 게임 | 코드로 합성한 8비트 효과음 (외부 음원 없음, 자체 제작) |

- 파일 이름 규칙: `{talk|retro}_{동작}.wav` (예: `talk_left_turn.wav`). 동작 이름은 `left_turn`, `right_turn`, `wiper_slow`, `wiper_fast`, `depart`, `arrive`, `headlight`, `window_open`, `window_close`입니다.
- 다시 만들기: `python3 tools/generate_sounds.py` (macOS의 `say`·`afconvert`와 Python 3 표준 라이브러리만 사용)
- 음원 기준: 2초 이내, 앞에 묵음(보이스 150ms 이상 — Pleos 'TTS / 음성 안내 선행 묵음 적용 가이드' 권장값, 효과음 40ms), 피크 정규화, 22.05kHz 16bit 모노 WAV. `./gradlew :app:testDebugUnitTest`의 `SoundAssetsTest`가 18개 파일이 모두 있고 2초 이내인지 검사합니다.
- **팀 녹음으로 교체하기**: 같은 이름의 WAV 파일로 덮어쓰고 다시 빌드하면 됩니다. 코드는 바꿀 필요가 없습니다.

> ⚠️ `talk_*` 보이스는 macOS 시스템 음성으로 만든 **프로토타입용 임시 음원**입니다. macOS 시스템 음성은 개인적·비상업적 용도로만 쓸 수 있으므로, 앱을 배포하기 전에는 반드시 팀이 직접 녹음했거나 사용권을 확보한 음원으로 바꿔야 합니다.

## 구조

```
차량 신호 수신부                  트리거 판단 엔진            사운드 재생부
PleosVehicleSource ─┐            TriggerEngine               SoundBank + SoundPlayer
 (Vehicle SDK)      ├─ VehicleSignal ─▶ 상태 변화 → CarAction ─▶ 보이스 / 8비트 효과음
SimulatorPanel ─────┘                                         (res/raw 음원, 팩 2종)
```

| 파일 | 역할 |
|---|---|
| `vehicle/PleosVehicleSource.kt` | Vehicle SDK의 `TurnSignal`, `Wiper`(상태 + 속도 스위치), `Gear`, `Light`(하향등), `Window`(1열 좌·우)를 구독합니다. 영역별로 실패를 격리하고, 받지 못한 신호는 화면에 '이 차량에서는 지원되지 않음'으로 표시합니다. |
| `engine/TriggerEngine.kt` | 순수 Kotlin. 상태 변화를 재생 지시(한 번 / 주기 반복 시작 / 멈춤)로 바꿉니다. `./gradlew :app:testDebugUnitTest`로 엔진 테스트 8개와 음원 검사 1개를 실행합니다. |
| `model/SoundPack.kt` | 사운드 팩 2종과 동작별 음원(`R.raw.*`)·문구를 연결합니다. |
| `audio/SoundBank.kt` | 앱이 시작할 때 `res/raw`의 음원 18개를 SoundPool에 미리 올려 둡니다. |
| `data/PackStore.kt` | 고른 사운드 팩을 기억합니다. |
| `audio/SoundPlayer.kt` | SoundPool 재생, 오디오 포커스, 2초 제한, 볼륨 상한을 맡습니다. |
| `service/CartoonService.kt` | 소리를 켠 동안만 도는 포그라운드 서비스입니다. 종료 시 SDK·SoundPool을 해제합니다. Android 14 이상에서는 `specialUse` 유형을 씁니다(계속 오디오를 재생하지 않고 신호를 기다리는 서비스이므로). |
| `vehicle/DrivingRestrictions.kt` | AAOS `CarUxRestrictionsManager`로 주행 규제 상태를 구독해, 규제 중에는 사운드 팩 바꾸기·미리 듣기를 잠급니다(Pleos '주행 규제 개발 가이드'). |

## 알려진 제약

- **보이스 음원**: 위 '음원' 절의 안내대로 임시 음원입니다. 배포 전에 팀 녹음으로 바꿔야 합니다.
- **좌우 방향 효과**: 레트로 팩의 깜빡이 소리에만 적용합니다(보이스는 가운데에서 들려야 알아듣기 쉬움). 차량 오디오가 스테레오를 지원할 때만 효과가 납니다.
- **release 빌드**: 시연용으로 디버그 키로 서명했습니다. R8 코드 최적화는 Pleos SDK와 충돌(`IllegalAccessError`)해서 껐습니다.
- **차량 시뮬레이터 패널**: 시연·테스트용 개발자 도구입니다. 실차 배포 시에는 숨깁니다.
