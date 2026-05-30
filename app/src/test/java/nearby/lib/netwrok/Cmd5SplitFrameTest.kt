package nearby.lib.netwrok

import com.serial.port.t.FrameExtractorNew
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证轮询间隔下分片 CMD5 仍可组帧（旧版 FrameExtractor 会在此场景丢包） */
class Cmd5SplitFrameTest {

    @Test
    fun cmd5FrameSurvivesLongGapBetweenChunks() {
        val frames = mutableListOf<ByteArray>()
        val extractor = FrameExtractorNew { frames.add(it) }
        val full = hexToBytes(
            "9B00051A010000283C000100000000010002FFFFF9C00001000000010100DD9A",
        )
        val mid = 20
        extractor.push(full.copyOfRange(0, mid))
        Thread.sleep(6000)
        extractor.push(full.copyOfRange(mid, full.size))
        assertEquals(1, frames.size)
        assertEquals(0x05, frames[0][2].toInt() and 0xFF)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
