package nearby.lib.netwrok

import com.serial.port.t.FrameExtractor
import com.serial.port.t.FrameExtractorNew
import com.serial.port.utils.HexConverter
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
            0x9B.toByte(), 0x00, 0x01, 0x02, 0x01, 0x01, 0xA0.toByte(), 0x9A.toByte(),
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

    private val frames = mutableListOf<ByteArray>()
    private val extractor = FrameExtractorNew { frames.add(it) }

    fun setup() {
        frames.clear()
        extractor.clear()
    }

    @Test//场景模拟：ID=5 物理丢包（33/34)
    fun testID5PacketLossRecovery() {
        // 1. 模拟发送 ID=5 的残包 (只有 33 字节，缺最后的 9A)
        // 9B 00 05 1C [28字节数据] [校验] (缺 9A)
        val partialID5 = ByteUtils.hexToBytes("9B00051C01000084D00001000000000100000200000000010000000100000017")
        extractor.push(partialID5)
        Thread.sleep(500)
        val partialID6 = ByteUtils.hexToBytes("9B00051C01000084D00001000000000100000200000000010000000100000017")
        extractor.push(partialID6)
        Thread.sleep(500)
        val partialID7 = ByteUtils.hexToBytes("9B00051C01000084D00001000000000100000200000000010000000100000017")
        extractor.push(partialID7)
        val partialID8 = ByteUtils.hexToBytes("9A")
        extractor.push(partialID8)
        Thread.sleep(1500)
        // 此时 frames 应该为空，因为包不完整
        assert(frames.isEmpty())

        // 2. 模拟等待 600ms (模拟超时发生)
        Thread.sleep(600)

        // 3. 模拟下一个正常的 ID=1 包到达
        val normalID1 = ByteUtils.hexToBytes("9B0001020101A09A")
        extractor.push(normalID1)

        // 此时 frames 应该包含 ID=1 的包
        // 核心验证：即使前面的 ID=5 坏了，ID=1 也能被解析出来，而不是被卡死
        assert(frames.any { it[2] == 0x01.toByte() })
    }

    @Test//队头阻塞（Head-of-Line Blocking）
    fun testHeadOfLineBlocking() {
        // 构造一个混合包：[半个ID5] + [完整ID4]
        val mixedInput = ByteUtils.hexToBytes(
            "9B00051C01000024EA0000000000000100000200000000000100000001000000D0" + // ID=5 残包
                    "9B000404000022F6BB9A" // 紧跟其后的 ID=4 完整包
        )

        // 模拟很久之后数据才推送进来（超过 500ms）
        Thread.sleep(600)
        extractor.push(mixedInput)

        // 验证：解析器在 500ms 后处理这一串数据时，应该强制跳过 ID=5，成功回调 ID=4
        assert(frames.size == 1)
        assert(frames[0][2] == 0x04.toByte())
    }

    @Test//场景模拟：伪帧头干扰
    fun testNoiseInterference() {
        // 伪造一段噪音 + 一个正确的 ID=2 包
        val noiseAndData = ByteUtils.hexToBytes("00009B1122FF009B0002020101A19A")

        extractor.push(noiseAndData)

        // 验证：解析器跳过前面的伪帧头，成功提取出 ID=2
        assert(frames.size == 1)
        assert(frames[0][2] == 0x02.toByte())
    }

    @Test//场景模拟：彻底断连导致的缓冲区重置
    fun testLongTimeInactivityReset() {
        // 1. 推送一些没头没尾的数据
        extractor.push(byteArrayOf(0x01, 0x02, 0x03))

        // 2. 模拟长达 2 秒的停顿 (超过 PROCESS_TIMEOUT)
        Thread.sleep(1200)

        // 3. 推送新包
        val normalID1 = ByteUtils.hexToBytes("9B0001020101A09A")
        extractor.push(normalID1)

        // 验证：之前的 01 02 03 应该被 reset 掉了，不影响新包
        assert(frames.size == 1)
        assert(frames[0].contentEquals(normalID1))
    }

    @Test
    fun testCodeAndWeight() {
        val weight = byteArrayOf(
            0x00.toByte(),
            0x00.toByte(),
            0x34.toByte(),
            0xE4.toByte(),

            0x00.toByte(),
            0x50.toByte(),
            0xB6.toByte(),
            0xF8.toByte())

        val results = convertBytesToKgWithSpacedHex(weight)
        println("转换重量结果详情:")
        results.forEachIndexed { index, res ->
            println("第 ${index + 1} 组: HEX=${res.hexFormatted}, Weight=${res.kg} kg")
        }
        val encode = constructFrame485(
            frameHeader = 0x9A.toByte(),
            address = 0x00.toByte(),
            command = 0x05.toByte(),
            data = byteArrayOf(0x01, 0x01),
            frameTail = 0x9b.toByte()
        )
        println("采取的编码值得：${com.serial.port.utils.ByteUtils.toHexString(encode)}")
    }
    data class WeightResult(val hexFormatted: String, val kg: Double)

    fun convertBytesToKgWithSpacedHex(data: ByteArray): List<WeightResult> {
        val results = mutableListOf<WeightResult>()
        val groupCount = data.size / 4
        for (i in 0 until groupCount) {
            val offset = i * 4
            val chunk = data.copyOfRange(offset, offset + 4)
            val intValue = HexConverter.byteArrayToInt(chunk)

            // 假设原始单位为克，转换为千克
            val kgValue = intValue / 1000.0

            // 格式化为带空格的十六进制字符串，例如 "00 00 34 E4"
            val hexFormatted = chunk.joinToString(" ") { byte ->
                String.format("%02X", byte.toInt() and 0xFF)
            }

            results.add(WeightResult(hexFormatted, kgValue))
        }
        return results
    }

    fun constructFrame485(frameHeader: Byte, address: Byte, command: Byte, data: ByteArray, frameTail: Byte): ByteArray {
        val dataLength: Byte = data.size.toByte()

        // 构造帧（帧头 + 地址 + 指令 + 长度 + 数据域）
        val frameWithoutChecksum = mutableListOf<Byte>().apply {
            add(frameHeader)
            add(address)
            add(command)
            add(dataLength)
            addAll(data.toList())
        }

        // 计算校验字节
        val checksum: Byte = (frameWithoutChecksum.sumOf { it.toUByte().toInt() } % 256).toByte()

        // 添加校验字节和帧尾
        frameWithoutChecksum.add(checksum)
        frameWithoutChecksum.add(frameTail)

        // 转为字节数组返回
        return frameWithoutChecksum.toByteArray()
    }

}
