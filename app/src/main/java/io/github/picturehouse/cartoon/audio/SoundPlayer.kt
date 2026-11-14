package io.github.picturehouse.cartoon.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import kotlin.math.min

/**
 * 저지연 효과음 재생부.
 * - 음원은 SoundPool에 미리 올려 두고 바로 재생한다.
 * - 재생 전에 일시적 오디오 포커스(덕킹 허용)를 요청하고, 받지 못하면 재생하지 않는다.
 * - 효과음은 최대 2초로 자르고, 이어지는 소리가 없으면 곧바로 포커스를 반환한다.
 * - 소리를 끄면 [release]로 SoundPool까지 해제하고, 다시 켜면 [open]으로 새로 만든다.
 */
class SoundPlayer(context: Context) {
    companion object {
        const val MAX_SOUND_MS = 2_000L
        /** 볼륨 상한 */
        const val VOLUME_CAP = 0.9f
        const val DEFAULT_VOLUME = 0.8f
        /** 마지막 소리가 끝난 뒤 포커스를 더 쥐고 있는 시간: 깜빡이(0.7초)·와이퍼 연속 동작의 소리 사이를 잇는다 */
        const val FOCUS_HOLD_MS = 600L
    }

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    var pool: SoundPool? = null
        private set

    fun open(): SoundPool = pool ?: SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(attributes)
        .build()
        .also { pool = it }

    private var streamId = 0
    private var focus: AudioFocusRequest? = null
    private val stopStream = Runnable { stopStream() }
    private val releaseFocus = Runnable { abandonFocus() }

    /**
     * 소리 한 번을 재생한다. 오디오 포커스는 소리마다 새로 받지 않고, 마지막 소리가 끝나고
     * [FOCUS_HOLD_MS] 동안 다음 소리가 없을 때 반납한다. 깜빡이·와이퍼처럼 짧은 소리가 이어질 때
     * 음악이 소리마다 작아졌다 커졌다 하지 않고, 이어지는 동안 한 번만 작아지게 하기 위함.
     * @return 실제로 재생을 시작했으면 true (오디오 포커스를 못 받으면 false)
     */
    fun play(soundId: Int, durationMs: Long, pan: Float, volume: Float = DEFAULT_VOLUME): Boolean {
        val pool = pool ?: return false
        handler.removeCallbacks(releaseFocus)
        stopStream()
        if (!requestFocus()) return false

        val v = volume.coerceIn(0f, VOLUME_CAP)
        val left = v * (if (pan > 0) 1f - pan * 0.85f else 1f)
        val right = v * (if (pan < 0) 1f + pan * 0.85f else 1f)
        val id = pool.play(soundId, left, right, 1, 0, 1f)
        if (id == 0) {
            abandonFocus()
            return false
        }
        streamId = id
        val length = min(durationMs, MAX_SOUND_MS) + 60
        handler.postDelayed(stopStream, length)
        handler.postDelayed(releaseFocus, length + FOCUS_HOLD_MS)
        return true
    }

    private fun requestFocus(): Boolean {
        if (focus != null) return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                // 통화·Gleo 음성 명령 등: 즉시 멈추고 포커스를 반납한다 (Pleos 오디오 포커스 가이드)
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    handler.post {
                        handler.removeCallbacks(stopStream)
                        handler.removeCallbacks(releaseFocus)
                        stopStream()
                        abandonFocus()
                    }
                }
            }
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        focus = request
        return true
    }

    private fun stopStream() {
        if (streamId != 0) pool?.stop(streamId)
        streamId = 0
    }

    private fun abandonFocus() {
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        stopStream()
        abandonFocus()
        pool?.release()
        pool = null
    }
}
