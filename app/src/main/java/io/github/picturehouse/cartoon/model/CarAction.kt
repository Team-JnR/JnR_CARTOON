package io.github.picturehouse.cartoon.model

/**
 * 소리가 나는 차량 동작.
 * @param pan 좌(-1) ~ 우(1) 스피커 방향 (차량 오디오가 지원하는 경우)
 * @param maxSoundMs 음원 최대 길이. 반복 동작은 다음 동작(깜빡임·왕복) 전에 끝나야 동작 1회에 소리 1번이 맞는다.
 */
enum class CarAction(
    val label: String,
    val trigger: String,
    val emoji: String,
    val pan: Float = 0f,
    val maxSoundMs: Long = 2_000,
) {
    LEFT_TURN("왼쪽 깜빡이", "켜져 있는 동안 깜빡임(째깍)마다 1번", "⬅️", pan = -1f, maxSoundMs = 640),
    RIGHT_TURN("오른쪽 깜빡이", "켜져 있는 동안 깜빡임(째깍)마다 1번", "➡️", pan = 1f, maxSoundMs = 640),
    WIPER_SLOW("와이퍼 (저속·간헐)", "작동하는 동안 왕복마다 1번", "🌧️", maxSoundMs = 1_200),
    WIPER_FAST("와이퍼 (고속)", "작동하는 동안 왕복마다 1번", "⛈️", maxSoundMs = 820),
    DEPART("출발", "기어 P → D 할 때 1번", "🚗"),
    ARRIVE("정지", "기어 D → P 할 때 1번", "🛑"),
    HEADLIGHT("전조등", "켤 때마다 1번", "💡"),
    WINDOW_OPEN("창문 열기", "창문 한 짝이 열릴 때마다 1번", "🪟"),
    WINDOW_CLOSE("창문 닫기", "열려 있던 창문 한 짝이 다 닫힐 때마다 1번", "🪟"),
}
