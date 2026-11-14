package io.github.picturehouse.cartoon

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import io.github.picturehouse.cartoon.service.CartoonService
import io.github.picturehouse.cartoon.ui.CartoonRoot
import io.github.picturehouse.cartoon.ui.CartoonTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // 권한 결과와 관계없이 시작한다 (권한이 없는 신호만 '미지원'으로 표시됨)
            startIfSoundOn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = (application as CartoonApp).controller
        // 사운드 팩 잠금용 주행 규제는 소리를 꺼 둔 동안에도 확인한다
        controller.restrictions.start()
        setContent { CartoonTheme { CartoonRoot(controller) } }

        val wanted = buildList {
            add("pleos.car.permission.CAR_LIGHTS")
            add("pleos.car.permission.CAR_WINDOWS")
            add("pleos.car.permission.CAR_DRIVE")
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) startIfSoundOn() else permissionLauncher.launch(wanted.toTypedArray())
    }

    /** 사용자가 소리를 켜 둔 경우에만 서비스를 시작한다 (끈 상태면 앱을 열어도 시작하지 않음) */
    private fun startIfSoundOn() {
        if ((application as CartoonApp).controller.packStore.soundOn.value) CartoonService.start(this)
    }
}
