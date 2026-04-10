import android.os.Build
import androidx.annotation.RequiresApi
import com.serial.port.t.ProtocolCodec
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.HexConverter
import com.serial.port.utils.SendByteData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.random.Random

///测试  单独运行文件 便于测试某些功能
/***
 * 相减
 * @param after
 * @param before
 * @return 如果 (value2 - value1) < -0.5 返回 true
 */
fun subtractFloatsBoolean(after: String, before: String): Boolean {
    val bd1 = BigDecimal(after)
    val bd2 = BigDecimal(before)
    val b = bd1.subtract(bd2).setScale(2, RoundingMode.HALF_UP).toDouble()
    return b < -0.5
}

@RequiresApi(Build.VERSION_CODES.O)
fun main() {


    send485(
        frameHeader = 0x9B.toByte(), command = 0x03.toByte(), add = 0x00.toByte(), data = byteArrayOf(
            0x01, 0x01, 0x00,

            ), frameTail = 0x9A.toByte()
    )
    send485(
        frameHeader = 0x9B.toByte(), command = 0x03.toByte(), add = 0x00.toByte(), data = byteArrayOf(
            0x01, 0x00, 0x00,

            ), frameTail = 0x9A.toByte()
    )

    send485(
        frameHeader = 0x9B.toByte(), command = 0x03.toByte(), add = 0x00.toByte(), data = byteArrayOf(
            0x01, 0x01, 0x01,

            ), frameTail = 0x9A.toByte()
    )
    send485(
        frameHeader = 0x9B.toByte(), command = 0x03.toByte(), add = 0x00.toByte(), data = byteArrayOf(
            0x01, 0x00, 0x01,

            ), frameTail = 0x9A.toByte()
    )


    //测试重量问题
    val aa = HexConverter.intToByteArray(0)
    val bb = HexConverter.intToByteArray(50000)

    val cc = byteArrayOf(
        0x01, 0x00, 0x00, 0x61, 0xA8.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x02, 0x00, 0x00, 0x4E, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )

    val dd = byteArrayOf(
        0x01, 0x00, 0x00, 0x4E, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x02, 0x00, 0x00, 0x4E, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    )
    send485(
        frameHeader = 0x9B.toByte(), command = 0x04.toByte(), add = 0x00.toByte(), data = aa, frameTail = 0x9A.toByte()
    )
    send485(
        frameHeader = 0x9B.toByte(), command = 0x04.toByte(), add = 0x00.toByte(), data = bb, frameTail = 0x9A.toByte()
    )

    send485(
        frameHeader = 0x9B.toByte(), command = 0x05.toByte(), add = 0x00.toByte(), data = cc, frameTail = 0x9A.toByte()
    )
    send485(
        frameHeader = 0x9B.toByte(), command = 0x05.toByte(), add = 0x00.toByte(), data = dd, frameTail = 0x9A.toByte()
    )
    val r1 = byteArrayOf(
        0xA1.toByte(), 0xA2 .toByte(),0xA3.toByte(), 0xF1.toByte() ,0x00.toByte(), 0x00.toByte() ,0x75.toByte() ,0x88 .toByte(),0x01.toByte() ,0x35.toByte() ,0x26.toByte() ,0x33.toByte(), 0x6B.toByte() ,0xC3.toByte(),0xC0.toByte() ,0x27.toByte()
    )


    println("测试 发送的字节 结果1：${ByteUtils.toHexString(ProtocolCodec.encode(0x08.toByte(),0x00.toByte(),r1))}")
    println("测试 发送的字节 结果2：${ByteUtils.toHexString(ProtocolCodec.encode2(0x08.toByte(),0x00.toByte(),r1))}")

    println("测试 发送的字节 weight：${HexConverter.byteArrayToInt(HexConverter.intToByteArray(19750)) / 1000}")
//    println("测试 发送的字节 byte:${ByteUtils.toHexString(byteArray)}")
//    val weight = HexConverter.byteArrayToInt(byteArray)
//    println("测试 发送的字节 weight：${weight}")
    val result = subtractFloatsBoolean("0.00", "0.96")
    println("测试 发送的字节 result：${result}")
//
    val weight2 = HexConverter.byteArrayToInt(byteArrayOf(0x01.toByte(), 35.toByte(), 26.toByte(), 34.toByte()))
    println("测试 发送的字节 weight2：${ByteUtils.toHexString(HexConverter.intToByteArray(20260401))}")
    println("测试 发送的字节 weight2：${ByteUtils.toHexString(HexConverter.intToByteArray(20260402))}")
    println("测试 发送的字节 weight2：${ByteUtils.toHexString(HexConverter.intToByteArray(20260403))}")
    println("测试 发送的字节 weight2：${ByteUtils.toHexString(HexConverter.intToByteArray(20260404))}")
    val weight3 = HexConverter.byteArrayToInt(byteArrayOf(0x00, 0x00, 0x60, 0x04.toByte()))

    println("测试 发送的字节 weight3：${weight3}")

    val a = ByteUtils.toHexString(HexConverter.intToByteArray(-2))
    val b = ByteUtils.toHexString(HexConverter.intToByteArray(-122))
    val c = ByteUtils.toHexString(HexConverter.intToByteArray(-22))
    println("测试 发送的字节 weight：${a}-- 22")
    println("测试 发送的字节 weight：${b}-- 122")
    println("测试 发送的字节 weight：${c}-- 22")


    println("测试 发送的字节 weight：${HexConverter.byteArrayToInt(HexConverter.intToByteArray(-2))}-- 2")
    println("测试 发送的字节 weight：${HexConverter.byteArrayToInt(HexConverter.intToByteArray(-112))}-- 112")
    println("测试 发送的字节 weight：${HexConverter.byteArrayToInt(HexConverter.intToByteArray(-22))}-- 22")



    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(2))}-2")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(4))}-4")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(6))}-6")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(8))}-8")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(10))}-10")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(12))}-12")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(14))}-14")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(16))}-16)")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(18))}-18")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(20))}-20")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(22))}-22")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(24))}-24")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(26))}-26")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(28))}-28")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(30))}-30")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(32))}-32")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(34))}-34")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(36))}-36")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(38))}-38")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(40))}-40")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(42))}-42")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(44))}-44")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(46))}-46")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(48))}-48")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(50))}-50")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(52))}-50")
    println("测试 发送的字节 weight：--------------------------------------------------------------")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(800))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(1000))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(1200))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(1400))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(1600))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(1800))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(2000))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(2200))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(2400))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(2600))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(2800))}")
    println("测试 发送的字节 weight：${ByteUtils.toHexString(HexConverter.intToByteArray(3000))}")

    //发送
    send485(frameHeader = 0x9A.toByte(), command = 0x05.toByte(), add = 0x00.toByte(), data = byteArrayOf(0x01, 0x01), frameTail = 0x9b.toByte())
    send485(frameHeader = 0x9B.toByte(), command = 0x05.toByte(), add = 0x00.toByte(), data = byteArrayOf(0x01, 0x01), frameTail = 0x9A.toByte())


    send485(frameHeader = 0x9B.toByte(), command = 0x12.toByte(), add = 0x00.toByte(), data = byteArrayOf(0x00, 0x00, 0x00, 0x00), frameTail = 0x9A.toByte())
    send485(frameHeader = 0x9B.toByte(), command = 0x07.toByte(), add = 0x00.toByte(), data = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte()), frameTail = 0x9A.toByte())


    send485(
        frameHeader = 0x9B.toByte(), command = 0x03.toByte(), add = 0x00.toByte(), data = byteArrayOf(0x01.toByte(), 0x01.toByte(), 0x01.toByte()), frameTail = 0x9A.toByte()
    )

    send485(
        frameHeader = 0x8A.toByte(), command = 0x05.toByte(), add = 0xF5.toByte(), data = byteArrayOf(), frameTail = 0x8B.toByte()
    )

    send485(
        frameHeader = 0x8A.toByte(), command = 0x06.toByte(), add = 0xF5.toByte(), data = byteArrayOf(), frameTail = 0x8B.toByte()
    )


    send485(
        frameHeader = 0x9B.toByte(), command = 0x02.toByte(), add = 0x00.toByte(),

        data =

        byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x49.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),

            ),

        frameTail = 0x9A.toByte()
    )
    ///高阶函数
//    println("${cal(1, 2, ::add)}")
//    testSend485S()
//    testSend485()
//    testOpen232()
//    testStatus232()

//    monitorCarProperties()
//    LicenseGenerator.main()
    val packet = byteArrayOf(
        0x8a.toByte(),
        0x00,
        0x02,
        0x02,
    )
//    //指令位置
//    val seek = 2
//    //数据长度位置
//    val length = 3
//    //提取指令
//    val command = packet[seek]
//    var dataLength = -1
//    when (command) {
//        1.toByte(), 2.toByte(), 3.toByte() -> {
//            // 提取数据长度，并将其转换为无符号整数
//            dataLength = packet[length].toUByte().toInt()  // 将有符号字节转换为无符号整数
//        }
//    }
//    val before = packet.size - 1
//    when (command) {
//        2.toByte() -> {
//            //取出完整数据
//            val toIndex = 4 + dataLength
//            if (before != toIndex) {
//                println("接232 1.toByte 数据长度与数据域不匹配")
//                return
//            }
//        }
//    }
//    println("长度 $dataLength")
//    val a = -0.03f
//    val b = 1.0f
//    val bool1 = a.greaterOrEqual(b)
//    val bool2 = a.lessOrEqual(b)
//    println("大于等于 $bool1 | 小于等于 $bool2")
//    buffer2322 = byteArrayOf(0x9B.toByte(), 0x9B.toByte(), 0x02.toByte(), 0x9A.toByte())
//    testCmd()
//    val snBytes =
//            byteArrayOf(0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte())
//    val snBytesValue = snBytes.map { byte ->
//        if (byte == 0x00.toByte()) 0x30.toByte() else byte
//    }.toByteArray()
//    // 将每个字节转换为十进制整数并拼接成字符串
//    val sn = String(snBytesValue, Charsets.UTF_8)
//    println("sn $sn ")
//
//    val byteArrayDL1 = byteArrayOf()
//    val address = 0xf5.toByte()
//    //电量
//    send485(command = 0x06.toByte(), add = address, data = byteArrayDL1)

// 测试用例
    val testWeights = listOf(0, 500, 1000, -500, -1000, 1500, -1500, 2345, -2345)

    println("重量转换测试结果：")
    println("====================")

    testWeights.forEach { weight ->
        val result = getWeight(weight)
        println("${weight}g -> ${result}kg")
    }

    println("====================")
    println("说明：负数表示负重量，在物理上有意义")
}

fun getWeight(weight: Int): String {
    // 处理零和负数情况
    if (weight == 0) return "0.00"

    // 直接转换为千克，保留两位小数
    val kilograms = weight.toDouble() / 1000
    return "%.2f".format(kilograms)
}

private var buffer2322 = ByteArray(0)

// 定义帧头和帧尾
private val frameHeader = SendByteData.RE_FRAME_HEADER

// 定义帧头和帧尾
private val frameEnd = SendByteData.RE_FRAME_END
fun testCmd() {
    var currentPosition = 0
    while (true) {
        // 1. 查找帧头（从当前位置开始）
        val frameStart = buffer2322.indexOf(frameHeader, currentPosition)
        if (frameStart == -1) break // 没有更多帧头

        // 2. 查找帧尾（必须位于帧头之后）
        val frameEndIndex = buffer2322.indexOf(frameEnd, frameStart + 1)
        if (frameEndIndex == -1) break // 当前帧不完整，等待更多数据

        // 3. 提取数据包（包含头尾）
        val packet = buffer2322.copyOfRange(frameStart, frameEndIndex + 1)
        println("接232 解析到完整包: $packet")

        // 4. 校验数据包（可选，根据协议实现）
        // if (!validateChecksum(packet)) {
        //     Loge.d("校验失败，丢弃包: ${packet.toHexString()}")
        //     currentPosition = frameEndIndex + 1
        //     continue
        // }

        // 5. 处理有效数据包
        handlePacket232(packet)

        // 6. 移动指针到当前帧尾之后，继续查找下一帧
        currentPosition = frameEndIndex + 1
    }

    // 7. 清理已处理的数据（保留未处理部分）
    buffer2322 = if (currentPosition > 0) {
        buffer2322.copyOfRange(currentPosition, buffer2322.size)
    } else {
        buffer2322
    }
}

// 自定义带起始位置的 indexOf 方法
private fun ByteArray.indexOf(byte: Byte, fromIndex: Int = 0): Int {
    for (i in fromIndex.coerceAtLeast(0) until this.size) {
        if (this[i] == byte) return i
    }
    return -1
}

private fun handlePacket232(packet: ByteArray) {
    println("接232 测试新的方式 处理数据 size ${packet.size} | ${ByteUtils.toHexString(packet)}")
}

val EPSILON = 1e-6f

// 小于等于比较
fun Float.lessOrEqual(other: Float, epsilon: Float = EPSILON): Boolean {
    return this < other || abs(this - other) < epsilon
}

// 大于等于比较
fun Float.greaterOrEqual(other: Float, epsilon: Float = EPSILON): Boolean {
    return this > other || abs(this - other) < epsilon
}

// 模拟雨量传感器的 Flow
private fun getRainSensorFlow(): Flow<Boolean> = flow {
    while (true) {
        val isRaining = Random.nextBoolean() // 模拟是否下雨
        emit(isRaining)
        delay(3000) // 每 3 秒更新一次
    }
}

// 模拟车速的 Flow
private fun getVehicleSpeedFlow(): Flow<Float> = flow {
    while (true) {
        val speed = Random.nextFloat() * 100 // 模拟车速在 0 到 100 之间变化
        emit(speed)
        delay(1000) // 每秒更新一次
    }
}

fun monitorCarProperties() = runBlocking {
    val speedFlow = getVehicleSpeedFlow()
    val rainFlow = getRainSensorFlow()

    combine(speedFlow, rainFlow) { speed, isRaining ->
        Pair(speed, isRaining)
    }.collect { (speed, isRaining) ->
        println("$speed | $isRaining")
        if (speed >= 90 && isRaining) {
            println("减速行驶")
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun testStatus232() {

    //空闲状态 完整数据包 1B 00 02 27 00 02 01 01 02 02 01 02 03 01 02 04 01 02 05 01 02 06 01 02 07 01 02 08 01 02 09 01 02 0A 01 02 0B 01 02 0C 01 02 0D C5 1A
    command02(0x00.toByte())
    //在门状态 完整数据包 1B 00 02 27 01 02 01 01 02 02 01 02 03 01 02 04 01 02 05 01 02 06 01 02 07 01 02 08 01 02 09 01 02 0A 01 02 0B 01 02 0C 01 02 0D C6 1A
    command02(0x01.toByte())

    println("门状态, -------------------------!")

    //空闲状态 完整数据包 1B 00 03 04 01 00 02 01 26 1A
    command03(0x01.toByte(), 0x00.toByte())
    //在门状态 完整数据包 1B 00 03 04 01 01 02 01 27 1A
    command03(0x01.toByte(), 0x01.toByte())

    val byteArray1 = byteArrayOf(
        0x9b.toByte(), 0x00, 0x02, 0xf4.toByte(), 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x03, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x04, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x05, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x06, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x07, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x08, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x09, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x0a, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x0b, 0x00, 0x02, 0x9a.toByte()
    )
    val byteArray2 = byteArrayOf(
        0x9b.toByte(), 0x00, 0x02, 0x115.toByte(), 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x03, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x04, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x05, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x06, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x07, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x08, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x09, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x0a, 0x01, 0x63, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x13, 0x0b, 0x00, 0x02, 0x9a.toByte()
    )
    //测试下位机发送的数据
//    handlePacket2(byteArray1)
//    handlePacket2(byteArray2)

}

fun testOpen232() {
    println("Hello, 开仓成功!")
    command01(0x01, 0x01)
    command01(0x02, 0x01)
    command01(0x03, 0x01)
    command01(0x04, 0x01)
    command01(0x05, 0x01)
    command01(0x06, 0x01)
    command01(0x07, 0x01)
    command01(0x08, 0x01)
    command01(0x09, 0x01)
    command01(0x0a, 0x01)
    command01(0x0b, 0x01)
    command01(0x0c, 0x01)
    command01(0x0d, 0x01)
    println("Hello, 开仓失败!")
//    开仓失败
//    完整数据包 1B 00 01 02 01 00 1F 1A
//    完整数据包 1B 00 01 02 02 00 20 1A
//    完整数据包 1B 00 01 02 03 00 21 1A
//    完整数据包 1B 00 01 02 04 00 22 1A
//    完整数据包 1B 00 01 02 05 00 23 1A
//    完整数据包 1B 00 01 02 06 00 24 1A
//    完整数据包 1B 00 01 02 07 00 25 1A
//    完整数据包 1B 00 01 02 08 00 26 1A
//    完整数据包 1B 00 01 02 09 00 27 1A
//    完整数据包 1B 00 01 02 0A 00 28 1A
//    完整数据包 1B 00 01 02 0B 00 29 1A
//    完整数据包 1B 00 01 02 0C 00 2A 1A
//    完整数据包 1B 00 01 02 0D 00 2B 1A

    command01(0x01, 0x00)
    command01(0x02, 0x00)
    command01(0x03, 0x00)
    command01(0x04, 0x00)
    command01(0x05, 0x00)
    command01(0x06, 0x00)
    command01(0x07, 0x00)
    command01(0x08, 0x00)
    command01(0x09, 0x00)
    command01(0x0a, 0x00)
    command01(0x0b, 0x00)
    command01(0x0c, 0x00)
    command01(0x0d, 0x00)
//    开仓成功
//    完整数据包 1B 00 01 02 01 01 20 1A
//    完整数据包 1B 00 01 02 02 01 21 1A
//    完整数据包 1B 00 01 02 03 01 22 1A
//    完整数据包 1B 00 01 02 04 01 23 1A
//    完整数据包 1B 00 01 02 05 01 24 1A
//    完整数据包 1B 00 01 02 06 01 25 1A
//    完整数据包 1B 00 01 02 07 01 26 1A
//    完整数据包 1B 00 01 02 08 01 27 1A
//    完整数据包 1B 00 01 02 09 01 28 1A
//    完整数据包 1B 00 01 02 0A 01 29 1A
//    完整数据包 1B 00 01 02 0B 01 2A 1A
//    完整数据包 1B 00 01 02 0C 01 2B 1A
//    完整数据包 1B 00 01 02 0D 01 2C 1A
}

fun testSend485S() {

    //当前工具电量 02地址电量 03地址电量
    val byteArrayDL1 =
        byteArrayOf(0x01, 0x55, 0x02, 0x55, 0x0d, 0x55, 0x0e, 0x55, 0x0f, 0x55, 0xf6.toByte(), 0x55, 0xf7.toByte(), 0x55)
    val byteArrayDL2 =
        byteArrayOf(0x01, 0x32, 0x02, 0x32, 0x0d, 0x32, 0x0e, 0x32, 0x0f, 0x32, 0xf6.toByte(), 0x32, 0xf7.toByte(), 0x32)
    val byteArrayDL3 =
        byteArrayOf(0x01, 0x14, 0x02, 0x14, 0x0d, 0x14, 0x0e, 0x14, 0x0f, 0x14, 0xf6.toByte(), 0x14, 0xf7.toByte(), 0x14)
    //02 03 08 09 0f f1 地址 在仓
    val byteArray02z =
        byteArrayOf(0x02, 0x01, 0x08, 0x01, 0x09, 0x01, 0x0b, 0x01, 0x0c, 0x01, 0x0d, 0x01, 0x0e, 0x01, 0x0f, 0x01, 0xf6.toByte(), 0x01, 0xf7.toByte(), 0x01)
    //02 03 08 09 0f f1 地址 不在仓
    val byteArray02bz =
        byteArrayOf(0x02, 0x00, 0x08, 0x00, 0x09, 0x00, 0x0b, 0x00, 0x0c, 0x00, 0x0d, 0x00, 0x0e, 0x00, 0x0f, 0x00, 0xf6.toByte(), 0x00, 0xf7.toByte(), 0x00)

    //当前箱子不故障
    val byteArray1gz =
        byteArrayOf(0x01, 0x01, 0x02, 0x01, 0x0d, 0x01, 0x0e, 0x01, 0x0f, 0x01, 0xf6.toByte(), 0x01, 0xf7.toByte(), 0x01)
    //当前箱子故障
    val byteArray2gz =
        byteArrayOf(0x01, 0x02, 0x02, 0x02, 0x0d, 0x02, 0x0e, 0x02, 0x0f, 0x02, 0xf6.toByte(), 0x02, 0xf7.toByte(), 0x02)

    //当前箱子sn
    val byteArraySn1 =
        byteArrayOf(0x47.toByte(), 0x4A.toByte(), 0x58.toByte(), 0x30.toByte(), 0x30.toByte(), 0x30.toByte(), 0x31.toByte(), 0x31.toByte())

    val snBytesValue = byteArraySn1.map { byte ->
        if (byte == 0x00.toByte()) 0x30.toByte() else byte
    }.toByteArray()
    // 将每个字节转换为十进制整数并拼接成字符串
    val sn = String(snBytesValue, Charsets.US_ASCII)
    println("我是测试的 sn $sn")

    val byteArraySn2 =
        byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())

//    val hexStringLower = String.format("0x%x", 277)
//    println("hexStringLower ：$hexStringLower")
//
//    val decimalFromHex = Integer.parseInt("0b", 16)
//    println("decimalFromHex ：$decimalFromHex")

    val address = 0x0B.toByte()
    //电量
    send485(command = 0x01.toByte(), add = address, data = byteArrayDL1)
    send485(command = 0x01.toByte(), add = address, data = byteArrayDL2)
    send485(command = 0x01.toByte(), add = address, data = byteArrayDL3)
    //是否在仓
    send485(command = 0x02.toByte(), add = address, data = byteArray02z)
    send485(command = 0x02.toByte(), add = address, data = byteArray02bz)
    //故障码
    send485(command = 0x03.toByte(), add = address, data = byteArray1gz)
    send485(command = 0x03.toByte(), add = address, data = byteArray2gz)
    //sn
    send485(command = 0x04.toByte(), add = address, data = byteArraySn1)
    send485(command = 0x04.toByte(), add = address, data = byteArraySn2)


}

fun testSend485() {

    //箱子电量和设备电量
    send485S(0x01.toByte(), null, byteArrayOf(0x01))
    send485S(0x01.toByte(), null, byteArrayOf(0x03))

    //设备在仓信息
    send485S(0x02.toByte(), null, byteArrayOf(0x04))
    send485S(0x02.toByte(), null, byteArrayOf(0x05))

    //箱子和设备故障
    send485S(0x03.toByte(), null, byteArrayOf(0x04))
    send485S(0x03.toByte(), null, byteArrayOf(0x04))

    //箱子SN
    send485S(0x04.toByte(), null, byteArrayOf())
}/*
fun handlePacket2(packet: ByteArray) {
    packet.forEach {
        print("接 it= ${it.toUByte().toInt()}")
    }

    //指令位置
    val seek = 2
    //数据长度位置
    val length = 3
    //提取指令
    val command = packet[seek]
    // 提取数据长度，并将其转换为无符号整数
//    val dataLength = packet[length].toUByte().toInt()  // 将有符号字节转换为无符号整数
    val dataLength = Integer.parseInt(packet[length].toString(), 16)  // 将有符号字节转换为无符号整数
    println("接 2.toByte command = $command dataLength = $dataLength")
    when (command) {
        2.toByte() -> {
            val data = packet.copyOfRange(4, 4 + dataLength)
            println("接 2.toByte ${data.joinToString(" ") { "%02X".format(it) }}")
            val list = mutableListOf<PortDeviceInfo>()
            list.clear()
            var locker = 1 //代表仓位号
            for (i in data.indices step 22) {
                val type = when (locker) {
                    1, 2, 3, 4, 5, 6, 7, 8 -> EnumBoxType.getDescByCode(1)
                    9, 10 -> EnumBoxType.getDescByCode(2)
                    11 -> EnumBoxType.getDescByCode(3)
                    12 -> EnumBoxType.getDescByCode(4)
                    else -> {
                        EnumBoxType.getDescByCode(5)
                    }
                }
                var status = -1
                var elec = -1
                var sn: String = ""
                // 防止最后一组数据不足22字节
                val end = (i + 22).coerceAtMost(data.size)
                val group = data.copyOfRange(i, end)
                val size = group.size
                println("接 2.toByte i = $i end $end | size ${group.size} | group ${ByteUtils.toHexString(group)}")
                when (locker) {
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 -> {
                        if (size > 0) {
                            //仓状态
                            status =
                                    if (group[0].toInt() == 0) 0 else if (group[0].toInt() == 1) 1 else 2
                        }
                        if (size > 1) {
                            //电流
                            elec = group[1] / 100
                        }

                        if (size > 2) {
                            //sn
                            val snBytes = group.copyOfRange(2, minOf(group.size, 2 + 20))
                            // 将每个字节转换为十进制整数并拼接成字符串
                            val decimalString =
                                    snBytes.joinToString(separator = " ") { it.toInt().toString() }
                            sn = decimalString.replace(" ", "")
                        }
                        val deviceinfo = PortDeviceInfo().apply {
                            boxType = "$type"
                            boxElectric = elec
                            boxCode = locker
                            boxDoorStatus = "$status"
                            boxSn = "$sn"
                        }
                        list.add(deviceinfo)
                        println("接 2.toByte 仓位：${locker} 类型：${type} | 仓状态：${status} | 电流：${elec} | sn：${sn}")
                    }

                    12 -> {//单独提取 12 13仓门状态
                        if (size > 0) {
                            //仓状态
                            val status12 =
                                    if (group[0].toInt() == 0) 0 else if (group[0].toInt() == 1) 1 else 2
                            list.add(PortDeviceInfo().apply {
                                boxType = EnumBoxType.getDescByCode(3)
                                boxElectric = 0
                                boxCode = 12
                                boxDoorStatus = "$status12"
                                boxSn = ""
                            })
                            println("接 2.toByte 仓位：12 类型：dx | 仓状态：${status12} | 电流：0 | sn：0")
                        }
                        if (size > 1) {
                            val status13 =
                                    if (group[1].toInt() == 0) 0 else if (group[1].toInt() == 1) 1 else 2
                            list.add(PortDeviceInfo().apply {
                                boxType = "zwx"
                                boxElectric = 0
                                boxCode = 13
                                boxDoorStatus = "$status13"
                                boxSn = ""
                            })
                            println("接 2.toByte 仓位：13 类型：zwx | 仓状态：${status13} | 电流：0 | sn：0")
                        }
                    }
                }

                println("接 2.toByte -----------------------------------------------------------")
                locker++
            }
        }
    }
}*/

/**
 * 下位机发送数据
 */
fun send(command: Byte, data: ByteArray) {
    //帧头
    val frameHeader: Byte = 0x9b.toByte()
    //地址
    val address: Byte = 0x00.toByte()
    //帧尾
    val frameTail: Byte = 0x9a.toByte()
    //完整数据包
    val result = constructFrame(frameHeader, address, command, data, frameTail)
    println("完整数据包 ${ByteUtils.toHexString(result)}")
}

/**
 * 下位机发送数据
 */
fun send485(frameHeader: Byte = 0x8A.toByte(), command: Byte, add: Byte, data: ByteArray, frameTail: Byte = 0x8B.toByte()) {
    //帧头
//    val frameHeader: Byte = 0x8A.toByte()
    //地址
    val address: Byte = add
    //帧尾
//    val frameTail: Byte = 0x8B.toByte()
    //完整数据包
    val result = constructFrame485(frameHeader, address, command, data, frameTail)
    println("完整数据包485  ${ByteUtils.toHexString(result)}")
}

/**
 * 下位机发送数据 单条
 */
fun send485S(command: Byte, add: Byte?, data: ByteArray) {
    //帧头
    val frameHeader: Byte = 0x8A.toByte()
    //地址
    val address: Byte = 0xFF.toByte()
    //帧尾
    val frameTail: Byte = add ?: 0x8B.toByte()
    //完整数据包
    val result = constructFrame485(frameHeader, address, command, data, frameTail)
    println("send485S  ${ByteUtils.toHexString(result)}")
}


fun command01(byte1: Byte, byte2: Byte) {
    //1A 00 01 02 01 01 1F 1B
    val command: Byte = 0x09.toByte()
    val data = byteArrayOf(byte1, byte2)
    send(command, data)
}

/***
 * 门状态 电流 工具箱SN
 */
fun command02(byte1: Byte) {
    val command: Byte = 0x02.toByte()
    //门状态 电流 sn20字节
    val data = byteArrayOf(
        byte1, 0x02, 0x01,
        0x01, 0x02, 0x02,
        0x01, 0x02, 0x03,
        0x01, 0x02, 0x04,
        0x01, 0x02, 0x05,
        0x01, 0x02, 0x06,
        0x01, 0x02, 0x07,
        0x01, 0x02, 0x08,
        0x01, 0x02, 0x09,
        0x01, 0x02, 0x0a,
        0x01, 0x02, 0x0b,
        0x01, 0x02, 0x0c,
        0x01, 0x02, 0x0d,
    )
    //12+10=22
    //后续扩展的位
    //36+30=66
    send(command, data)
}

/***
 * 仓号 门状态 电流 工具箱SN
 */
fun command03(byte1: Byte, byte2: Byte) {
    val command: Byte = 0x03.toByte()
    //仓号 门状态 电流 sn20字节
    val data = byteArrayOf(
        byte1, byte2, 0x02, 0x01,
    )
    //12+10=22
    //后续扩展的位
    //36+30=66
    send(command, data)
}

fun add(a: Int, b: Int): Int {
    return a * b
}

fun cal(a: Int, b: Int, opt: (a: Int, b: Int) -> Int): Int {
    return opt(a, b)
}

fun constructFrame(frameHeader: Byte, address: Byte, command: Byte, data: ByteArray, frameTail: Byte): ByteArray {
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
//    val checksum: Byte = (frameWithoutChecksum.sumOf { it.toUByte().toInt() } % 256).toByte()

    // 添加校验字节和帧尾
//    frameWithoutChecksum.add(checksum)
    frameWithoutChecksum.add(frameTail)

    // 转为字节数组返回
    return frameWithoutChecksum.toByteArray()
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

// 或者使用扩展属性来简化
fun Byte.toUnsignedInt(): Int {
    return this.toInt() and 0xFF
}

