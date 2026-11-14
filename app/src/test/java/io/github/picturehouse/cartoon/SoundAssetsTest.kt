package io.github.picturehouse.cartoon

import io.github.picturehouse.cartoon.model.CarAction
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 사운드 팩 음원 기준 검사: 모든 팩 × 동작 파일이 있고, 동작별 최대 길이(반복 동작은 주기보다 짧게) 이내인지 */
class SoundAssetsTest {
    private val rawDir = File("src/main/res/raw")
    private val prefixes = listOf("talk", "retro")

    @Test
    fun `모든 팩에 동작별 음원이 있고 동작별 최대 길이 이내`() {
        prefixes.forEach { prefix ->
            CarAction.entries.forEach { action ->
                val f = File(rawDir, "${prefix}_${action.name.lowercase()}.wav")
                assertTrue("없음: ${f.path}", f.exists())
                val header = ByteBuffer.wrap(f.readBytes().copyOf(44)).order(ByteOrder.LITTLE_ENDIAN)
                val byteRate = header.getInt(28)
                val durationMs = (f.length() - 44) * 1000 / byteRate
                assertTrue("${f.name}: ${durationMs}ms > ${action.maxSoundMs}ms", durationMs in 100..action.maxSoundMs)
            }
        }
    }
}
