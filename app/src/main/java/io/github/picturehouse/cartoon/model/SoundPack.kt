package io.github.picturehouse.cartoon.model

import androidx.annotation.RawRes
import io.github.picturehouse.cartoon.R

/**
 * 사운드 팩. 모든 음원은 미리 만들어 res/raw 에 넣어 둔다 (tools/generate_sounds.py).
 * 파일 이름 규칙: {prefix}_{동작 이름 소문자}.wav  예) talk_left_turn.wav
 */
enum class SoundPack(val title: String, val description: String, val emoji: String) {
    TALKING_CAR("말하는 차", "차가 직접 말해 줘요. 째깍 할 때마다 \"왼쪽!\"", "🗣️"),
    RETRO("레트로 게임", "8비트 게임기 효과음. 깜빡이 방향에 따라 좌우에서 들려요", "👾");

    /** 화면과 로그에 보여 줄 소리 설명 */
    fun caption(action: CarAction): String = when (this) {
        TALKING_CAR -> when (action) {
            CarAction.LEFT_TURN -> "왼쪽!"
            CarAction.RIGHT_TURN -> "오른쪽!"
            CarAction.WIPER_SLOW -> "스윽— 싹"
            CarAction.WIPER_FAST -> "슥싹!"
            CarAction.DEPART -> "부릉부릉~"
            CarAction.ARRIVE -> "정지!"
            CarAction.HEADLIGHT -> "반짝!"
            CarAction.WINDOW_OPEN -> "위이잉~"
            CarAction.WINDOW_CLOSE -> "위잉~ 탁!"
        }
        RETRO -> when (action) {
            CarAction.LEFT_TURN -> "삐릭 ◀ (왼쪽에서)"
            CarAction.RIGHT_TURN -> "삐릭 ▶ (오른쪽에서)"
            CarAction.WIPER_SLOW -> "빗자루 쓱— 싹"
            CarAction.WIPER_FAST -> "빗자루 쓱싹"
            CarAction.DEPART -> "게임 시작 \"레디, 고!\""
            CarAction.ARRIVE -> "스테이지 클리어 팡파르"
            CarAction.HEADLIGHT -> "별 획득음"
            CarAction.WINDOW_OPEN -> "파워업 효과음"
            CarAction.WINDOW_CLOSE -> "파워다운 효과음"
        }
    }

    @RawRes
    fun soundRes(action: CarAction): Int = when (this) {
        TALKING_CAR -> when (action) {
            CarAction.LEFT_TURN -> R.raw.talk_left_turn
            CarAction.RIGHT_TURN -> R.raw.talk_right_turn
            CarAction.WIPER_SLOW -> R.raw.talk_wiper_slow
            CarAction.WIPER_FAST -> R.raw.talk_wiper_fast
            CarAction.DEPART -> R.raw.talk_depart
            CarAction.ARRIVE -> R.raw.talk_arrive
            CarAction.HEADLIGHT -> R.raw.talk_headlight
            CarAction.WINDOW_OPEN -> R.raw.talk_window_open
            CarAction.WINDOW_CLOSE -> R.raw.talk_window_close
        }
        RETRO -> when (action) {
            CarAction.LEFT_TURN -> R.raw.retro_left_turn
            CarAction.RIGHT_TURN -> R.raw.retro_right_turn
            CarAction.WIPER_SLOW -> R.raw.retro_wiper_slow
            CarAction.WIPER_FAST -> R.raw.retro_wiper_fast
            CarAction.DEPART -> R.raw.retro_depart
            CarAction.ARRIVE -> R.raw.retro_arrive
            CarAction.HEADLIGHT -> R.raw.retro_headlight
            CarAction.WINDOW_OPEN -> R.raw.retro_window_open
            CarAction.WINDOW_CLOSE -> R.raw.retro_window_close
        }
    }

    /** 좌우 방향 효과는 효과음 팩(레트로)에만 쓴다. 보이스는 가운데에서 들려야 알아듣기 쉽다. */
    fun pan(action: CarAction): Float = if (this == RETRO) action.pan else 0f
}
