package com.serial.port.t

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * @author: lr
 * @created on: 2026/3/21 下午4:18
 * @description:
 */
object  ProtocolCodec {
    const val FRAME_HEADER =  0x9A.toByte()
    const val FRAME_END = 0x9B.toByte() // 假设你的帧尾是 9A

    // 协议偏移量常量 (对应你的 handlePacket232 定义)
    const val ADDR_POS = 1
    const val CMD_POS = 2
    const val DATA_LEN_POS = 3
    const val PAYLOAD_START_POS = 4

    /**
     * 【封包】
     * 结构：头(9B) + 地址 + 命令 + 长度 + 数据 + 校验 + 尾(9E)
     */
    fun encode(cmd: Byte, addr: Byte, payload: ByteArray): ByteArray {
        val size = payload.size.toByte()
        val header = byteArrayOf(FRAME_HEADER, addr, cmd, size)
        val frameNoCheck = header + payload

        var sum = 0
        for (b in frameNoCheck) {
            sum += (b.toInt() and 0xFF)
        }
        val checkSum = (sum % 256).toByte()

        return frameNoCheck + byteArrayOf(checkSum, FRAME_END)
    }

    /**
     * 校验并提取有效负载
     */
    fun decodePayload(raw: ByteArray): ByteArray? {
        if (raw.size < 6 || raw.first() != FRAME_HEADER || raw.last() != FRAME_END) return null

        // 校验和验证
        var sum = 0
        for (i in 0 until raw.size - 2) {
            sum += (raw[i].toInt() and 0xFF)
        }
        val expectedCheckSum = (sum % 256).toByte()
        if (expectedCheckSum != raw[raw.size - 2]) return null

        val len = raw[3].toInt() and 0xFF
        if (raw.size < len + 6) return null

        return raw.copyOfRange(4, 4 + len)
    }

    /**
     * 【提取有效数据域 (Payload)】
     * 自动处理无符号长度并截取数据部分
     */
    fun getSafePayload(packet: ByteArray): ByteArray? {
        if (packet.size < 6) return null

        // 提取数据长度位 (无符号转换)
        val dataLen = packet[DATA_LEN_POS].toUByte().toInt()

        // 安全截取范围检查
        val endPos = PAYLOAD_START_POS + dataLen
        return if (packet.size >= endPos + 2) { // +2 是因为后面还有校验位和帧尾
            packet.copyOfRange(PAYLOAD_START_POS, endPos)
        } else {
            null
        }
    }

    /**
     * 【字节转整数】
     * 对应你 handlePacket232 中的重量(4字节)或阻力值(4字节)转换
     */
    fun bytesToInt(data: ByteArray): Int {
        if (data.isEmpty()) return -1
        var result = 0
        for (b in data) {
            result = (result shl 8) or (b.toInt() and 0xFF)
        }
        return result
    }
    /**
     * 【字节转整数】
     * 对应你 handlePacket232 中的重量(4字节)或阻力值(4字节)转换
     */
    fun byteArrayToInt(bytes: ByteArray): Int {
        require(bytes.size == 4) { "Byte array must be 4 bytes long" }
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
    }
    /**
     * 【字节转 16 进制字符串】
     * 用于日志输出，调试利器
     */
    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString(" ") { "%02X".format(it) }
    }

    /**
     * 【解析业务组】
     * 针对你代码中 step 2 或 step 3 的逻辑，将 Payload 拆分为固定长度的小组
     * 例如：Step 3 得到 [(Locker, Status, Type), (Locker, Status, Type)]
     */
    fun parseGroups(payload: ByteArray, step: Int): List<ByteArray> {
        val groups = mutableListOf<ByteArray>()
        if (payload.isEmpty() || payload.size % step != 0) {
            Log.w("ProtocolCodec", "Payload 长度(${payload.size})与步长($step)不匹配")
        }
        for (i in payload.indices step step) {
            val end = (i + step).coerceAtMost(payload.size)
            groups.add(payload.copyOfRange(i, end))
        }
        return groups
    }
}