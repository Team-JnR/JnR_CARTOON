package io.github.picturehouse.cartoon.model

enum class Turn { OFF, LEFT, RIGHT }

/**
 * 와이퍼 작동 단계와 '왕복 1회' 주기(ms).
 * Vehicle SDK는 와이퍼 왕복마다 이벤트를 주지 않으므로, 단계별 일반적인 왕복 주기로 소리 타이밍을 만든다.
 * (실차 주기와 다를 수 있는 추정값 — 차종별로 조정)
 */
enum class WiperLevel(val periodMs: Long) {
    OFF(0),
    /** 1회만 닦기 (MIST) */
    MIST(0),
    /** 간헐 (단계별 주기는 [VehicleSignal.Wiper.periodMs]로 따로 줄 수 있음) */
    INTERMITTENT(4_500),
    /** 연속 저속: 분당 약 46회 */
    SLOW(1_300),
    /** 연속 고속: 분당 약 67회 */
    FAST(900),
}

enum class Gear(val label: String) { PARK("P"), REVERSE("R"), NEUTRAL("N"), DRIVE("D"), UNKNOWN("-") }

/** 차량 신호 수신부가 트리거 엔진에 넘겨주는 '정규화된' 차량 상태 변화 */
sealed interface VehicleSignal {
    data class TurnSignal(val value: Turn) : VehicleSignal
    data class Wiper(val level: WiperLevel, val periodMs: Long = level.periodMs) : VehicleSignal
    data class GearChanged(val gear: Gear) : VehicleSignal
    data class Headlight(val on: Boolean) : VehicleSignal
    /** 창문 한 짝의 열림 상태 (area: 운전석/조수석 등) */
    data class Window(val area: String, val open: Boolean) : VehicleSignal
}

/** UI 표시용 현재 차량 상태 */
data class VehicleState(
    val turn: Turn = Turn.OFF,
    val wiper: WiperLevel = WiperLevel.OFF,
    val gear: Gear = Gear.PARK,
    val headlight: Boolean = false,
    val openWindows: Set<String> = emptySet(),
) {
    val windowOpen: Boolean get() = openWindows.isNotEmpty()

    fun apply(signal: VehicleSignal): VehicleState = when (signal) {
        is VehicleSignal.TurnSignal -> copy(turn = signal.value)
        is VehicleSignal.Wiper -> copy(wiper = signal.level)
        is VehicleSignal.GearChanged -> copy(gear = signal.gear)
        is VehicleSignal.Headlight -> copy(headlight = signal.on)
        is VehicleSignal.Window -> copy(openWindows = if (signal.open) openWindows + signal.area else openWindows - signal.area)
    }
}
