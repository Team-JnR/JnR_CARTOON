package io.github.picturehouse.cartoon.vehicle

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import io.github.picturehouse.cartoon.model.Gear
import io.github.picturehouse.cartoon.model.Turn
import io.github.picturehouse.cartoon.model.VehicleSignal
import io.github.picturehouse.cartoon.model.WiperLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 차량 신호 영역 (설정 화면의 '지원 여부' 표시 단위) */
enum class SignalDomain(val label: String) {
    TURN_SIGNAL("방향지시등"),
    WIPER("와이퍼"),
    GEAR("기어"),
    HEADLIGHT("전조등"),
    WINDOW("창문"),
}

enum class Support { CHECKING, SUPPORTED, UNSUPPORTED }

/**
 * 차량 신호 수신부: Pleos Connect Vehicle SDK로 차량 상태 변화를 구독해
 * [VehicleSignal]로 정규화한 뒤 [sink]로 넘긴다. 모든 콜백은 메인 스레드로 옮겨 전달한다.
 *
 * @param sink (signal, initial) — initial=true는 앱 시작 시 현재 값(소리 없이 상태만 맞춤)
 */
class PleosVehicleSource(
    private val context: Context,
    private val sink: (VehicleSignal, Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private val cleanups = mutableListOf<() -> Unit>()

    private val _support = MutableStateFlow(SignalDomain.entries.associateWith { Support.CHECKING })
    val support: StateFlow<Map<SignalDomain, Support>> = _support

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val frontWiper = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val windowAreas = listOf(WindowArea(WindowValue.ROW_1_LEFT), WindowArea(WindowValue.ROW_1_RIGHT))
    private val openWindows = mutableSetOf<WindowArea>()
    private val seenWindows = mutableSetOf<WindowArea>()
    private var wiperOn = false
    private var wiperLevel = WiperLevel.OFF
    private var lastSwitch: WiperSwitch? = null

    /** Pleos 차량 서비스가 설치된 기기(Pleos Connect 에뮬레이터/실차)인지 */
    fun isVehicleServiceAvailable(): Boolean =
        context.packageManager.queryIntentServices(Intent("ai.pleos.playground.vehicle.VEHICLE_START"), 0).isNotEmpty()

    /** @return Vehicle SDK 연결을 시도했으면 true, 차량 서비스가 없으면 false(시뮬레이션 모드) */
    fun start(): Boolean {
        if (vehicle != null) return true
        if (!isVehicleServiceAvailable()) {
            _support.value = SignalDomain.entries.associateWith { Support.UNSUPPORTED }
            return false
        }
        return try {
            val v = Vehicle(context).also { it.initialize() }
            vehicle = v
            _connected.value = true
            // 차종·권한에 따라 일부 영역만 실패할 수 있으므로 영역별로 격리한다
            safely(SignalDomain.TURN_SIGNAL) { subscribeTurnSignal(v) }
            safely(SignalDomain.WIPER) { subscribeWiper(v) }
            safely(SignalDomain.GEAR) { subscribeGear(v) }
            safely(SignalDomain.HEADLIGHT) { subscribeHeadlight(v) }
            safely(SignalDomain.WINDOW) { subscribeWindow(v) }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Vehicle SDK init failed", e)
            _connected.value = false
            _support.value = SignalDomain.entries.associateWith { Support.UNSUPPORTED }
            false
        }
    }

    fun stop() {
        cleanups.forEach { runCatching(it) }
        cleanups.clear()
        main.removeCallbacksAndMessages(null)
        runCatching { vehicle?.release() }
        vehicle = null
        _connected.value = false
        // 다시 켤 때 처음 받은 값을 '현재 상태'로 다시 맞추도록 초기화
        seen.clear()
        openWindows.clear()
        seenWindows.clear()
        lastSwitch = null
        wiperOn = false
        wiperLevel = WiperLevel.OFF
        _support.value = SignalDomain.entries.associateWith { Support.CHECKING }
    }

    private fun safely(domain: SignalDomain, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "${domain.label} 구독 실패 → 미지원 처리: ${e.message}")
            mark(domain, Support.UNSUPPORTED)
        }
    }

    private fun mark(domain: SignalDomain, s: Support) {
        val cur = _support.value[domain]
        // 한 번 지원으로 확인되면 이후 일시적 오류로 '미지원' 처리하지 않는다
        if (cur == Support.SUPPORTED && s == Support.UNSUPPORTED) return
        _support.value = _support.value + (domain to s)
    }

    /** 영역별로 처음 받은 값은 '현재 상태'(initial)로 넘긴다. SDK는 리스너 등록 직후 현재 값을 한 번 보내 준다. */
    private val seen = mutableSetOf<SignalDomain>()

    private fun deliver(domain: SignalDomain, signal: VehicleSignal) {
        val initial = seen.add(domain)
        sink(signal, initial)
    }

    private fun emit(domain: SignalDomain, signal: VehicleSignal) = main.post { deliver(domain, signal) }

    private fun failed(domain: SignalDomain): (Exception) -> Unit = { e ->
        Log.w(TAG, "${domain.label} 신호 수신 실패: ${e.message}")
        main.post { mark(domain, Support.UNSUPPORTED) }
    }

    // ── 방향지시등 (TurnSignal API) ─────────────────────────────
    private fun subscribeTurnSignal(v: Vehicle) {
        val api = v.getTurnSignal()
        fun map(t: TurnSignalValue?) = when (t) {
            TurnSignalValue.LEFT -> Turn.LEFT
            TurnSignalValue.RIGHT -> Turn.RIGHT
            else -> Turn.OFF
        }
        api.getCurrentTurnSignalState({ t ->
            main.post { mark(SignalDomain.TURN_SIGNAL, Support.SUPPORTED) }
            emit(SignalDomain.TURN_SIGNAL, VehicleSignal.TurnSignal(map(t)))
        }, failed(SignalDomain.TURN_SIGNAL))
        val listener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                main.post { mark(SignalDomain.TURN_SIGNAL, Support.SUPPORTED) }
                emit(SignalDomain.TURN_SIGNAL, VehicleSignal.TurnSignal(map(state)))
            }
            override fun onFailed(e: Exception) = failed(SignalDomain.TURN_SIGNAL)(e)
        }
        api.registerTurnSignalState(listener)
        cleanups += { api.unregisterTurnSignalState(listener) }
    }

    // ── 와이퍼 (Wiper API, 조회만 사용) ─────────────────────────
    private fun subscribeWiper(v: Vehicle) {
        val api = v.getWiper()
        /**
         * 와이퍼 스위치 → 작동 단계와 왕복 1회 주기.
         * SDK는 왕복마다 이벤트를 주지 않으므로 단계별 일반적인 주기를 쓴다 (실차와 다를 수 있는 추정값).
         */
        fun wiperOf(sw: WiperSwitch?): VehicleSignal.Wiper? = when (sw) {
            null, WiperSwitch.OFF, WiperSwitch.OTHER, WiperSwitch.SERVICE -> null
            WiperSwitch.MIST -> VehicleSignal.Wiper(WiperLevel.MIST)
            WiperSwitch.INTERMITTENT_LEVEL_1 -> VehicleSignal.Wiper(WiperLevel.INTERMITTENT, 8_000)
            WiperSwitch.INTERMITTENT_LEVEL_2 -> VehicleSignal.Wiper(WiperLevel.INTERMITTENT, 6_000)
            WiperSwitch.INTERMITTENT_LEVEL_3 -> VehicleSignal.Wiper(WiperLevel.INTERMITTENT, 4_500)
            WiperSwitch.INTERMITTENT_LEVEL_4 -> VehicleSignal.Wiper(WiperLevel.INTERMITTENT, 3_000)
            WiperSwitch.INTERMITTENT_LEVEL_5 -> VehicleSignal.Wiper(WiperLevel.INTERMITTENT, 2_000)
            WiperSwitch.CONTINUOUS_LEVEL_1 -> VehicleSignal.Wiper(WiperLevel.SLOW)
            WiperSwitch.AUTO -> VehicleSignal.Wiper(WiperLevel.INTERMITTENT, 3_000) // 빗물 감지: 비 양에 따라 다름
            else -> VehicleSignal.Wiper(WiperLevel.FAST) // CONTINUOUS_LEVEL_2~5
        }
        var current: VehicleSignal.Wiper = VehicleSignal.Wiper(WiperLevel.OFF)
        fun publish(w: VehicleSignal.Wiper) {
            if (w != current || SignalDomain.WIPER !in seen) {
                current = w
                wiperLevel = w.level
                deliver(SignalDomain.WIPER, w)
            }
        }
        fun refreshSwitch() {
            api.getCurrentWiperSwitch(frontWiper, { sw ->
                main.post {
                    if (sw != null && sw != lastSwitch) Log.d(TAG, "wiper switch: $sw")
                    lastSwitch = sw
                    // 작동 중인데 스위치가 '꺼짐'으로 읽히면 저속으로 본다
                    publish(if (wiperOn) wiperOf(sw) ?: VehicleSignal.Wiper(WiperLevel.SLOW) else VehicleSignal.Wiper(WiperLevel.OFF))
                }
            }, {
                // 속도 스위치를 읽을 수 없는 차량: 작동 여부만으로 '저속'으로 본다
                main.post { if (wiperOn && wiperLevel == WiperLevel.OFF) publish(VehicleSignal.Wiper(WiperLevel.SLOW)) }
            })
        }
        // 와이퍼 속도 스위치는 변경 콜백이 없어, 작동 중일 때만 1초마다 확인한다
        // (Pleos 성능 가이드: 불필요한 타이머·폴링 최소화 → 꺼져 있을 때는 타이머를 아예 멈춘다)
        val poll = object : Runnable {
            override fun run() {
                if (!wiperOn) return
                refreshSwitch()
                main.postDelayed(this, 1_000)
            }
        }
        cleanups += { main.removeCallbacks(poll) }
        fun onState(s: WiperState?) {
            main.post {
                mark(SignalDomain.WIPER, Support.SUPPORTED)
                wiperOn = s == WiperState.ON
                main.removeCallbacks(poll)
                if (wiperOn) main.postDelayed(poll, 1_000)
                if (!wiperOn) {
                    publish(VehicleSignal.Wiper(WiperLevel.OFF))
                } else {
                    refreshSwitch()
                }
            }
        }
        api.getCurrentWiperState(frontWiper, { onState(it) }, failed(SignalDomain.WIPER))
        val listener = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) = onState(state)
            override fun onFailed(e: Exception) = failed(SignalDomain.WIPER)(e)
        }
        api.registerWiperState(frontWiper, listener)
        cleanups += { api.unregisterWiperState(frontWiper, listener) }
    }

    // ── 기어 (Gear API) ────────────────────────────────────────
    private fun subscribeGear(v: Vehicle) {
        val api = v.getGear()
        fun map(g: VehicleGear?) = when (g) {
            VehicleGear.GEAR_PARK -> Gear.PARK
            VehicleGear.GEAR_REVERSE -> Gear.REVERSE
            VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
            null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
            else -> Gear.DRIVE // D 및 수동 단수
        }
        api.getCurrentGearSelectionState({ g ->
            main.post { mark(SignalDomain.GEAR, Support.SUPPORTED) }
            emit(SignalDomain.GEAR, VehicleSignal.GearChanged(map(g)))
        }, failed(SignalDomain.GEAR))
        val listener = object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) {
                main.post { mark(SignalDomain.GEAR, Support.SUPPORTED) }
                emit(SignalDomain.GEAR, VehicleSignal.GearChanged(map(gear)))
            }
            override fun onFailed(e: Exception) = failed(SignalDomain.GEAR)(e)
        }
        api.registerGearSelection(listener)
        cleanups += { api.unregisterGearSelection(listener) }
    }

    // ── 전조등 (Light API, 하향등) ──────────────────────────────
    private fun subscribeHeadlight(v: Vehicle) {
        val api = v.getLight()
        api.getCurrentLowBeamLightState({ s ->
            main.post { mark(SignalDomain.HEADLIGHT, Support.SUPPORTED) }
            emit(SignalDomain.HEADLIGHT, VehicleSignal.Headlight(s == LightState.ON))
        }, failed(SignalDomain.HEADLIGHT))
        val listener = object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) {
                main.post { mark(SignalDomain.HEADLIGHT, Support.SUPPORTED) }
                emit(SignalDomain.HEADLIGHT, VehicleSignal.Headlight(state == LightState.ON))
            }
            override fun onFailed(e: Exception) = failed(SignalDomain.HEADLIGHT)(e)
        }
        api.registerLowBeamLightState(listener)
        cleanups += { api.unregisterLowBeamLightState(listener) }
    }

    // ── 창문 (Window API, 1열 좌·우) ────────────────────────────
    private fun subscribeWindow(v: Vehicle) {
        val api = v.getWindow()
        windowAreas.forEach { area ->
            val name = if (area == windowAreas[0]) "운전석" else "조수석"
            // 창문 한 짝마다 따로: 처음 받은 값은 현재 상태(initial), 이후 닫힘↔열림이 바뀔 때만 전달
            fun update(pos: Int?) = main.post {
                mark(SignalDomain.WINDOW, Support.SUPPORTED)
                val open = (pos ?: 0) > 0
                val initial = seenWindows.add(area)
                val changed = if (open) openWindows.add(area) else openWindows.remove(area)
                if (initial) seen += SignalDomain.WINDOW
                if (initial || changed) sink(VehicleSignal.Window(name, open), initial)
            }
            api.getCurrentWindowPosition(area, { update(it) }, failed(SignalDomain.WINDOW))
            val listener = object : WindowPositionListener {
                override fun onWindowPositionUpdated(position: Int?) { update(position) }
                override fun onFailed(e: Exception) = failed(SignalDomain.WINDOW)(e)
            }
            api.registerWindowPosition(area, listener)
            cleanups += { api.unregisterWindowPosition(area, listener) }
        }
    }

    companion object {
        private const val TAG = "PleosVehicleSource"
    }
}
