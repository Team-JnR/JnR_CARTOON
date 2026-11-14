package io.github.picturehouse.cartoon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import io.github.picturehouse.cartoon.CartoonApp
import io.github.picturehouse.cartoon.MainActivity
import io.github.picturehouse.cartoon.R

/**
 * 사용자가 '소리 켜기'를 켠 동안에만 신호 감지와 재생을 포그라운드 서비스로 유지해,
 * 앱 화면을 닫거나 내비 등 다른 앱을 보고 있어도 효과음이 이어지게 한다.
 *
 * Pleos '미사용 앱 BG→CCH 전환 가이드'에 따라
 * - 부팅 때 자동으로 시작하지 않는다 (사용자가 앱을 열어야 시작).
 * - 소리를 끄면 즉시 FGS를 종료하고 차량 SDK·SoundPool을 해제해 프로세스가 cached 상태로 내려가게 한다.
 */
class CartoonService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val controller get() = (application as CartoonApp).controller

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 알림의 '끄기' 버튼, 또는 꺼진 상태에서 시스템이 서비스를 되살린 경우: 바로 끝낸다
        if (intent?.action == ACTION_STOP) controller.packStore.setSoundOn(false)
        if (!controller.packStore.soundOn.value) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Android 14 이상은 specialUse(오디오를 계속 재생하지 않고 신호 대기만 하므로), 그 이하는 mediaPlayback
        val type = when {
            Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        controller.start()
        return START_STICKY
    }

    override fun onDestroy() {
        controller.stop()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CartoonService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_launcher_foreground),
                    getString(R.string.notif_stop),
                    stop,
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "cartoon"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "io.github.picturehouse.cartoon.action.STOP"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, CartoonService::class.java))
            } catch (e: IllegalStateException) {
                // 시스템이 백그라운드 시작을 막은 경우: 다음에 앱을 열 때 시작된다
                Log.w("CartoonService", "foreground service start not allowed", e)
            }
        }

        /** 소리 끄기: 서비스가 끝나면 onDestroy에서 자원을 모두 해제한다 */
        fun stop(context: Context) {
            context.stopService(Intent(context, CartoonService::class.java))
        }
    }
}
