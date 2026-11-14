package io.github.picturehouse.cartoon

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.picturehouse.cartoon.audio.SoundBank
import io.github.picturehouse.cartoon.audio.SoundPlayer
import io.github.picturehouse.cartoon.data.PackStore
import io.github.picturehouse.cartoon.engine.SoundChannel
import io.github.picturehouse.cartoon.engine.Trigger
import io.github.picturehouse.cartoon.engine.TriggerEngine
import io.github.picturehouse.cartoon.model.CarAction
import io.github.picturehouse.cartoon.model.Gear
import io.github.picturehouse.cartoon.model.SoundPack
import io.github.picturehouse.cartoon.model.VehicleSignal
import io.github.picturehouse.cartoon.model.VehicleState
import io.github.picturehouse.cartoon.service.CartoonService
import io.github.picturehouse.cartoon.vehicle.DrivingRestrictions
import io.github.picturehouse.cartoon.vehicle.PleosVehicleSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 이벤트 로그 한 줄 */
data class LogEntry(val time: Long, val text: String, val played: Boolean)

/** 마지막으로 울린 소리 */
data class PlayedSound(val pack: SoundPack, val action: CarAction)

enum class SourceMode(val label: String) {
    PLEOS("Pleos Vehicle SDK 연결됨"),
    SIMULATION("시뮬레이션 모드 (차량 서비스 없음)"),
}

/**
 * 차량 신호 수신부 → 트리거 판단 엔진 → 사운드 재생부를 잇는다.
 * 모든 상태 변경은 메인 스레드에서 일어난다.
 */
class CartoonController(context: Context) {
    private val player = SoundPlayer(context)
    val soundBank = SoundBank(context, player)
    val packStore = PackStore(context)
    private val engine = TriggerEngine()
    val vehicleSource = PleosVehicleSource(context, ::onVehicleSignal)
    val restrictions = DrivingRestrictions(context)

    private val _vehicle = MutableStateFlow(VehicleState())
    val vehicle: StateFlow<VehicleState> = _vehicle

    private val _last = MutableStateFlow<PlayedSound?>(null)
    val last: StateFlow<PlayedSound?> = _last

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log

    private val _mode = MutableStateFlow(SourceMode.SIMULATION)
    val mode: StateFlow<SourceMode> = _mode

    private val _running = MutableStateFlow(false)
    /** 신호 감지·재생이 동작 중인지 ([CartoonService]가 켜고 끈다) */
    val running: StateFlow<Boolean> = _running

    /** 서비스가 시작될 때: 음원을 올리고 차량 신호 구독을 시작한다 */
    fun start() {
        if (_running.value) return
        _running.value = true
        soundBank.prepare()
        engine.seed(VehicleSignal.GearChanged(_vehicle.value.gear))
        restrictions.start()
        _mode.value = if (vehicleSource.start()) SourceMode.PLEOS else SourceMode.SIMULATION
        log("🔊 소리 켜짐 · ${_mode.value.label}", played = false)
    }

    /**
     * 서비스가 끝날 때: 차량 SDK 구독과 음원(SoundPool)을 모두 해제한다.
     * Pleos 'BG→CCH 전환 가이드'에 따라 소리를 끈 뒤에는 프로세스가 cached 상태로 내려갈 수 있게 한다.
     */
    fun stop() {
        if (!_running.value) return
        _running.value = false
        vehicleSource.stop()
        soundBank.release()
        repeaters.values.forEach { it.stop() }
        log("🔇 소리 꺼짐", played = false)
    }

    /** 화면·알림의 소리 켜기/끄기 */
    fun setSoundOn(context: Context, on: Boolean) {
        packStore.setSoundOn(on)
        if (on) CartoonService.start(context) else CartoonService.stop(context)
    }

    /**
     * 사운드 팩 바꾸기·미리 듣기를 할 수 있는지: 기어가 P이고, 플랫폼 주행 규제(UX_RESTRICTIONS_NO_SETUP)도 없을 때만.
     * 둘 중 하나라도 '주행 중'이면 잠근다.
     */
    fun canChangeSettings(
        gear: Gear = _vehicle.value.gear,
        setupRestricted: Boolean = restrictions.setupRestricted.value,
        running: Boolean = _running.value,
    ) = !setupRestricted && (!running || gear == Gear.PARK) // 소리를 끈 동안은 기어를 모르므로 플랫폼 규제만 본다

    fun selectPack(pack: SoundPack) {
        if (!canChangeSettings()) return
        packStore.select(pack)
        log("${pack.emoji} 사운드 팩 → ${pack.title}", played = false)
    }

    /** 사운드 팩 미리 듣기 (설정 화면 기능이므로 같은 조건으로 잠근다) */
    fun preview(pack: SoundPack, action: CarAction) {
        if (canChangeSettings()) play(pack, action, quiet = false)
    }

    /** 시뮬레이터 패널에서 들어오는 신호 */
    fun simulate(signal: VehicleSignal) = onVehicleSignal(signal, initial = false)

    private fun onVehicleSignal(signal: VehicleSignal, initial: Boolean) {
        if (!_running.value) return // 해제 직후 늦게 도착한 SDK 콜백은 버린다
        _vehicle.value = _vehicle.value.apply(signal)
        val trigger = if (initial) engine.seed(signal) else engine.onSignal(signal)
        trigger?.let(::handle)
    }

    private fun handle(t: Trigger) {
        when (t) {
            is Trigger.Once -> play(packStore.pack.value, t.action, quiet = false)
            is Trigger.Start -> {
                val r = repeaters.getValue(t.channel)
                r.start(t.action, t.periodMs, t.immediate)
                if (t.periodMs > 0) log("${t.action.emoji} ${t.action.label} — ${"%.1f".format(t.periodMs / 1000f)}초마다 1번", played = false)
            }
            is Trigger.Stop -> repeaters.getValue(t.channel).stop()
        }
    }

    /**
     * 깜빡임·와이퍼 왕복처럼 반복되는 동작용 타이머. 동작 1회에 소리 1번을 낸다.
     * 기준 시각에서 주기의 배수로 예약해 시간이 지나도 박자가 밀리지 않게 한다.
     */
    private inner class Repeater {
        private var action: CarAction? = null
        private var periodMs = 0L
        private var startAt = 0L
        private var count = 0L
        private val tick = object : Runnable {
            override fun run() {
                val a = action ?: return
                play(packStore.pack.value, a, quiet = true)
                count++
                handler.postAtTime(this, startAt + count * periodMs)
            }
        }

        fun start(action: CarAction, periodMs: Long, immediate: Boolean) {
            stop()
            if (periodMs <= 0) { // 1회 동작 (와이퍼 1회 닦기)
                play(packStore.pack.value, action, quiet = false)
                return
            }
            this.action = action
            this.periodMs = periodMs
            startAt = SystemClock.uptimeMillis()
            count = 0
            if (immediate) tick.run() else {
                count = 1
                handler.postAtTime(tick, startAt + periodMs)
            }
        }

        fun stop() {
            handler.removeCallbacks(tick)
            action = null
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val repeaters = SoundChannel.entries.associateWith { Repeater() }

    /** @param quiet 반복 소리는 로그를 남기지 않는다 (시작·멈춤만 기록) */
    private fun play(pack: SoundPack, action: CarAction, quiet: Boolean) {
        if (!_running.value) {
            log("${action.emoji} ${action.label} — 소리가 꺼져 있어요", played = false)
            return
        }
        val sound = soundBank.get(pack, action)
        val ok = sound != null && player.play(sound.soundId, sound.durationMs, pack.pan(action))
        if (ok) {
            Log.d("CartoonPlay", "${action.name} (${pack.name})")
            _last.value = PlayedSound(pack, action)
            if (!quiet) log("${action.emoji} ${action.label} → ${pack.caption(action)} · ${pack.title}", played = true)
        } else if (!quiet) {
            log("${action.emoji} ${action.label} — 재생하지 못했어요 (불러오는 중이거나 오디오 포커스 없음)", played = false)
        }
    }

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    private fun log(text: String, played: Boolean) {
        _log.value = (listOf(LogEntry(System.currentTimeMillis(), text, played)) + _log.value).take(50)
    }

    fun formatTime(t: Long): String = timeFmt.format(Date(t))
}
