package nearby.lib.netwrok

import com.serial.port.t.FrameExtractor
import com.serial.port.t.FrameExtractorNew
import org.junit.Test

class FrameExtractorTest {

    @Test
    fun testFrameExtraction() {
        val foundFrames = mutableListOf<String>()

        // 初始化提取器
        val extractor = FrameExtractor { packet ->
            foundFrames.add(toHexString(packet))
        }

        // --- 模拟场景 1：标准粘包 (两个完整的包连在一起) ---
        // 包1: 9B 00 05 02 AA BB CS 9A (假设长度位是 index 3, 02表示payload长2)
        // 完整包长 = 2 + 6 = 8 字节
        val stickyData = byteArrayOf(
            0x9B.toByte(), 0x00, 0x05, 0x02, 0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0x9A.toByte(), 0x9B.toByte(), 0x00, 0x05, 0x02, 0x11, 0x22, 0x33, 0x9A.toByte()
        )
        println("--- 执行场景 1: 粘包测试 ---")
        extractor.push(stickyData)
        assert(foundFrames.size == 2) { "应该找到2个包，实际找到: ${foundFrames.size}" }

        // --- 模拟场景 2：断包 (一个包分三次推入) ---
        println("--- 执行场景 2: 断包测试 ---")
        foundFrames.clear()
        extractor.push(byteArrayOf(0x9B.toByte(), 0x00, 0x05)) // 只有头和部分长度
        extractor.push(byteArrayOf(0x02, 0xEE.toByte(), 0xFF.toByte())) // payload
        extractor.push(byteArrayOf(0x77, 0x9A.toByte())) // 校验和与尾
        assert(foundFrames.size == 1) { "断包重组失败" }

        // --- 模拟场景 3：杂质干扰 (包前包后有乱码) ---
        println("--- 执行场景 3: 杂质干扰测试 ---")
        foundFrames.clear()
        val noiseData = byteArrayOf(
            0xFF.toByte(), 0xEE.toByte(), // 乱码
            0x9B.toByte(), 0x00, 0x05, 0x01, 0x0A, 0x0B, 0x9A.toByte(), // 正确包 (长度1)
            0x00, 0x11 // 乱码
        )
        extractor.push(noiseData)
        assert(foundFrames.size == 1)

        // --- 模拟场景 4：伪帧头干扰 ---
        // 9B 后面跟的长度不对，或者没 9A
        println("--- 执行场景 4: 伪帧头干扰测试 ---")
        foundFrames.clear()
        val fakeHeader = byteArrayOf(
            0x9B.toByte(), 0x9B.toByte(), 0x00, 0x05, 0x01, 0xCC.toByte(), 0xDD.toByte(), 0x9A.toByte()
        )
        extractor.push(fakeHeader)
//        assert(foundFrames.size == 1)

        println("所有测试通过！已捕获的帧：")
        foundFrames.forEach { println(it) }
    }

    @Test
    fun testStickyPackets() {
        val results = mutableListOf<ByteArray>()
//        val extractor = FrameExtractor { results.add(it) }
        val extractor = FrameExtractorNew { results.add(it) }

        // 模拟两个包粘在一起：
        // 包1: 9B 00 02 02 01 00 A0 9A (8字节)
        // 包2: 9B 00 02 02 FF FF 3E 9A (8字节)
//        val stickyData = byteArrayOf(
//            0x9B.toByte(), 0x00, 0x02, 0x02, 0x01, 0x00, 0xA0.toByte(), 0x9A.toByte(),
//            0x9B.toByte(), 0x00, 0x02, 0x02, 0x01, 0x02, 0xA1.toByte(),
//            0x9B.toByte(), 0x00, 0x04, 0x04 , 0x00, 0x00, 0x87.toByte(),  0x50.toByte(),0x7A.toByte(), 0x9A.toByte(),
//        )
        val stickyData = byteArrayOf(
            0x9B.toByte(), 0x00, 0x01, 0x02, 0x01, 0x01, 0xA0.toByte(),0x9A.toByte(),
            0x9B.toByte(), 0x00, 0x05, 0x1c,
            0x01, 0x00, 0x00, 0x04.toByte(), 0xE2.toByte(), 0x00, 0x00, 0x00, 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x02, 0x00, 0x00, 0x00.toByte(), 0x00.toByte(), 0x00, 0x01, 0x00, 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x9B.toByte(), 0x00, 0x04, 0x04, 0x00, 0x00, 0x04.toByte(), 0xe2.toByte(), 0x89.toByte(), 0x9A.toByte(),
        )

        extractor.push(stickyData)

        println("共提取到包数量: ${results.size}")
        results.forEachIndexed { index, bytes ->
            println("包 ${index + 1}: ${bytes.joinToString(" ") { "%02X".format(it) }}")
        }

        // 断言：应该解析出2个包
        assert(results.size == 2)
    }

    private fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }
}