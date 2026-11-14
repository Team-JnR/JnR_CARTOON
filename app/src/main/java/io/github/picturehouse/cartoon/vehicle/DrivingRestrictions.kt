package io.github.picturehouse.cartoon.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 주행 규제 상태(CarUxRestrictions)를 구독한다. Pleos '주행 규제 개발 가이드'에 따라
 * DO(distractionOptimized)를 선언한 앱은 플랫폼이 내려주는 ActiveRestrictions를 직접 반영해야 한다.
 *
 * CARTOON에서 '설정'에 해당하는 사운드 팩 바꾸기·미리 듣기는 UX_RESTRICTIONS_NO_SETUP이 걸리면 잠근다.
 * 차량 서비스가 없는 기기(일반 에뮬레이터 등)에서는 규제 없음으로 본다.
 */
class DrivingRestrictions(private val context: Context) {
    private val _setupRestricted = MutableStateFlow(false)
    /** true면 주행 규제로 설정 변경이 금지된 상태 */
    val setupRestricted: StateFlow<Boolean> = _setupRestricted

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        if (car != null) return
        // Car.createCar(Context)는 API 29부터. 그 이하에서는 기어 기준 잠금만 쓴다.
        if (Build.VERSION.SDK_INT < 29) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return
        try {
            val c = Car.createCar(context) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            apply(m.currentCarUxRestrictions)
            m.registerListener { apply(it) }
        } catch (e: Throwable) {
            // android.car 라이브러리가 없거나 서비스 연결 실패: 기어 기준 잠금으로 동작
            Log.w(TAG, "CarUxRestrictions unavailable: ${e.message}")
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private fun apply(r: CarUxRestrictions?) {
        val restricted = r != null && r.isRequiresDistractionOptimization &&
            (r.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP) != 0
        if (restricted != _setupRestricted.value) Log.i(TAG, "UX restriction NO_SETUP → $restricted (active=${r?.activeRestrictions})")
        _setupRestricted.value = restricted
    }

    companion object {
        private const val TAG = "DrivingRestrictions"
    }
}
