package io.github.picturehouse.cartoon.engine

import io.github.picturehouse.cartoon.model.CarAction
import io.github.picturehouse.cartoon.model.Gear
import io.github.picturehouse.cartoon.model.Turn
import io.github.picturehouse.cartoon.model.VehicleSignal
import io.github.picturehouse.cartoon.model.WiperLevel

/** 반복해서 울리는 소리의 갈래. 갈래마다 반복은 하나만 돈다. */
enum class SoundChannel { TURN, WIPER }

/** 엔진이 재생부에 내리는 지시 */
sealed interface Trigger {
    /** 한 번만 울린다 (출발·정지·전조등·창문 열기/닫기) */
    data class Once(val action: CarAction) : Trigger

    /**
     * [channel]의 반복을 (다시) 시작한다: 동작 1회(깜빡임 1회, 와이퍼 왕복 1회)마다 소리 1번.
     * @param periodMs 반복 주기. 0 이하면 한 번만 울리고 그 갈래의 반복을 멈춘다(와이퍼 1회 닦기).
     * @param immediate false면 첫 소리를 한 주기 뒤에 낸다 (앱 시작 때 이미 동작 중이던 경우)
     */
    data class Start(
        val channel: SoundChannel,
        val action: CarAction,
        val periodMs: Long,
        val immediate: Boolean = true,
    ) : Trigger

    /** [channel]의 반복을 멈춘다 */
    data class Stop(val channel: SoundChannel) : Trigger
}

/**
 * 트리거 판단 엔진: 차량 신호의 '변화'를 재생 지시([Trigger])로 바꾼다.
 * 차량의 동작 1회에 소리 1번이 대응하도록 한다.
 * - 방향지시등: 켜져 있는 동안 깜빡임 1회(째깍 1번)마다 "왼쪽"/"오른쪽" 1번.
 *   Vehicle SDK는 레버 상태(왼쪽/오른쪽/꺼짐)만 알려 주므로 깜빡임 주기는 [TURN_TICK_MS]로 만든다.
 *   차량이 깜빡임마다 켜짐/꺼짐을 보내는 경우에도 '켜짐'마다 소리 1번이 되어 그대로 맞는다.
 * - 와이퍼: 작동 중 왕복 1회마다 1번 (주기는 [WiperLevel] 단계별 추정값).
 * - 그 밖의 동작: 일어날 때마다 1번.
 * 안드로이드 의존성이 없어 단위 테스트가 가능하다.
 */
class TriggerEngine {
    companion object {
        /** 방향지시등 깜빡임 주기: 분당 약 86회 (국제 기준 60~120회/분, 일반적인 차량 85회 안팎) */
        const val TURN_TICK_MS = 700L
    }

    private var turn = Turn.OFF
    private var wiper = WiperLevel.OFF
    private var wiperPeriod = 0L
    private var gear = Gear.UNKNOWN
    private var headlight = false
    private val openWindows = mutableSetOf<String>()

    /**
     * 앱 시작 시 현재 차량 상태로 맞춘다. 켜는 순간의 소리는 내지 않지만,
     * 이미 동작 중인 깜빡이·와이퍼는 다음 주기부터 동작마다 소리를 내도록 반복 지시를 돌려준다.
     */
    fun seed(signal: VehicleSignal): Trigger? {
        when (signal) {
            is VehicleSignal.TurnSignal -> {
                turn = signal.value
                return turnTrigger(signal.value)?.let { if (it is Trigger.Start) it.copy(immediate = false) else null }
            }
            is VehicleSignal.Wiper -> {
                wiper = signal.level
                wiperPeriod = signal.periodMs
                return wiperTrigger(signal)?.let { if (it is Trigger.Start && it.periodMs > 0) it.copy(immediate = false) else null }
            }
            is VehicleSignal.GearChanged -> gear = signal.gear
            is VehicleSignal.Headlight -> headlight = signal.on
            is VehicleSignal.Window -> if (signal.open) openWindows += signal.area else openWindows -= signal.area
        }
        return null
    }

    /** @return 재생 지시, 없으면 null */
    fun onSignal(signal: VehicleSignal): Trigger? = when (signal) {
        is VehicleSignal.TurnSignal -> {
            val prev = turn
            turn = signal.value
            if (signal.value == prev) null else turnTrigger(signal.value)
        }
        is VehicleSignal.Wiper -> {
            val changed = signal.level != wiper || signal.periodMs != wiperPeriod
            wiper = signal.level
            wiperPeriod = signal.periodMs
            if (changed) wiperTrigger(signal) else null
        }
        is VehicleSignal.GearChanged -> {
            val prev = gear
            gear = signal.gear
            when {
                signal.gear == Gear.DRIVE && prev == Gear.PARK -> Trigger.Once(CarAction.DEPART)
                signal.gear == Gear.PARK && prev != Gear.PARK && prev != Gear.UNKNOWN -> Trigger.Once(CarAction.ARRIVE)
                else -> null
            }
        }
        is VehicleSignal.Headlight -> {
            val wasOn = headlight
            headlight = signal.on
            if (!wasOn && signal.on) Trigger.Once(CarAction.HEADLIGHT) else null
        }
        is VehicleSignal.Window -> {
            // 창문 한 짝이 열릴 때마다, 열려 있던 한 짝이 다 닫힐 때마다 1번 (다른 창문 상태와 상관없이)
            if (signal.open) {
                if (openWindows.add(signal.area)) Trigger.Once(CarAction.WINDOW_OPEN) else null
            } else {
                if (openWindows.remove(signal.area)) Trigger.Once(CarAction.WINDOW_CLOSE) else null
            }
        }
    }

    private fun turnTrigger(value: Turn): Trigger? = when (value) {
        Turn.OFF -> Trigger.Stop(SoundChannel.TURN)
        Turn.LEFT -> Trigger.Start(SoundChannel.TURN, CarAction.LEFT_TURN, TURN_TICK_MS)
        Turn.RIGHT -> Trigger.Start(SoundChannel.TURN, CarAction.RIGHT_TURN, TURN_TICK_MS)
    }

    private fun wiperTrigger(w: VehicleSignal.Wiper): Trigger = when (w.level) {
        WiperLevel.OFF -> Trigger.Stop(SoundChannel.WIPER)
        WiperLevel.FAST -> Trigger.Start(SoundChannel.WIPER, CarAction.WIPER_FAST, w.periodMs)
        else -> Trigger.Start(SoundChannel.WIPER, CarAction.WIPER_SLOW, w.periodMs) // MIST는 주기 0 → 1번만
    }
}
