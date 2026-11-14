package io.github.picturehouse.cartoon.data

import android.content.Context
import io.github.picturehouse.cartoon.model.SoundPack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 선택한 사운드 팩과 소리 켜기/끄기를 기억한다 */
class PackStore(context: Context) {
    private val prefs = context.getSharedPreferences("cartoon", Context.MODE_PRIVATE)

    private val _pack = MutableStateFlow(
        SoundPack.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: SoundPack.TALKING_CAR
    )
    val pack: StateFlow<SoundPack> = _pack

    fun select(pack: SoundPack) {
        _pack.value = pack
        prefs.edit().putString(KEY, pack.name).apply()
    }

    private val _soundOn = MutableStateFlow(prefs.getBoolean(KEY_SOUND_ON, true))
    /** 사용자가 켠 동안만 백그라운드 서비스를 유지한다 (기본: 켜짐) */
    val soundOn: StateFlow<Boolean> = _soundOn

    fun setSoundOn(on: Boolean) {
        _soundOn.value = on
        prefs.edit().putBoolean(KEY_SOUND_ON, on).apply()
    }

    private companion object {
        const val KEY = "sound_pack"
        const val KEY_SOUND_ON = "sound_on"
    }
}
