package io.github.picturehouse.cartoon.audio

import android.content.Context
import android.util.Log
import io.github.picturehouse.cartoon.model.CarAction
import io.github.picturehouse.cartoon.model.SoundPack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** SoundPool에 올라간 소리 한 개 */
data class LoadedSound(val soundId: Int, val durationMs: Long)

/**
 * 앱에 들어 있는 사운드 팩 음원(res/raw)을 시작할 때 모두 SoundPool에 올려 둔다.
 * 재생 시점에는 파일을 읽지 않으므로 신호 변화 후 바로 소리가 난다.
 */
class SoundBank(private val context: Context, private val player: SoundPlayer) {
    private val sounds = mutableMapOf<Pair<SoundPack, CarAction>, LoadedSound>()
    private val loaded = mutableSetOf<Int>()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    fun prepare() {
        if (sounds.isNotEmpty()) return
        val total = SoundPack.entries.size * CarAction.entries.size
        val pool = player.open()
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) loaded += id else Log.w(TAG, "load failed: $id")
            if (loaded.size == total) _ready.value = true
        }
        SoundPack.entries.forEach { pack ->
            CarAction.entries.forEach { action ->
                val res = pack.soundRes(action)
                sounds[pack to action] = LoadedSound(pool.load(context, res, 1), wavDurationMs(res))
            }
        }
    }

    /** 소리를 끌 때: 올려 둔 음원과 SoundPool을 모두 해제한다 */
    fun release() {
        player.release()
        sounds.clear()
        loaded.clear()
        _ready.value = false
    }

    fun get(pack: SoundPack, action: CarAction): LoadedSound? =
        sounds[pack to action]?.takeIf { it.soundId in loaded }

    /** WAV 헤더의 byteRate와 파일 길이로 재생 시간을 구한다 */
    private fun wavDurationMs(res: Int): Long = try {
        context.resources.openRawResourceFd(res).use { afd ->
            val header = ByteArray(44)
            afd.createInputStream().use { it.read(header) }
            val byteRate = (header[28].toInt() and 0xff) or ((header[29].toInt() and 0xff) shl 8) or
                ((header[30].toInt() and 0xff) shl 16) or ((header[31].toInt() and 0xff) shl 24)
            if (byteRate > 0) (afd.length - 44) * 1000 / byteRate else SoundPlayer.MAX_SOUND_MS
        }
    } catch (e: Exception) {
        SoundPlayer.MAX_SOUND_MS
    }

    companion object {
        private const val TAG = "SoundBank"
    }
}
