package com.serial.port.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

object HexConverter {
    /**
     * 将字符串转换为字节数组（ByteArray）
     * @param input 原始字符串
     * @param charset 字符编码（默认UTF-8）
     * @return 字节数组
     */
    fun stringToBytes(input: String, charset: Charset = Charsets.UTF_8): ByteArray {
        return input.toByteArray(charset)
    }

    fun intToByteArray(num: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(num).array()
    }

    fun byteArrayToInt(bytes: ByteArray): Int {
        require(bytes.size == 4) { "Byte array must be 4 bytes long" }
        return ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).int
    }

    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串（每两个字符表示一个字节）
     */
    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    /**
     * 获取重量
     * @param weight 下位机上报的重量
     */
    fun getWeight(weight: Int): String {
        if (weight == 0) return "0.00"
        return "%.2f".format(weight.toDouble() / 1000)
//        return new BigDecimal(weight).divide(BigDecimal.valueOf(1000)).setScale(2, RoundingMode.HALF_DOWN).toString();
    }

    /**
     * 一步转换：字符串 → 字节数组 → 十六进制字符串
     * @param input 原始字符串
     * @param charset 字符编码（默认UTF-8）
     */
    fun stringToHexString(input: String, charset: Charset = Charsets.UTF_8): String {
        return bytesToHex(input.toByteArray(charset))
    }

    fun hexStringToByteArray(hexString: String): ByteArray {
        // 去除所有空格
        val cleanHex = hexString.replace(" ", "")
        // 检查长度是否为偶数
        require(cleanHex.length % 2 == 0) { "Hex string must have even number of characters" }

        return ByteArray(cleanHex.length / 2) {
            val byteStr = cleanHex.substring(it * 2, it * 2 + 2)
            byteStr.toInt(16).toByte()
        }
    }

    /**
     * @param arrays 合并所有的byteArray
     */
    fun combineByteArrays(vararg arrays: ByteArray): ByteArray {
        var totalLength = 0
        arrays.forEach { totalLength += it.size }

        val result = ByteArray(totalLength)
        var offset = 0
        arrays.forEach {
            System.arraycopy(it, 0, result, offset, it.size)
            offset += it.size
        }
        return result
    }

    fun combineByteArrays(byteArrays: List<ByteArray>): ByteArray {
        // 计算总长度
        val totalSize = byteArrays.sumOf { it.size }
        val result = ByteArray(totalSize)

        // 逐个拷贝数组
        var offset = 0
        byteArrays.forEach { array ->
            System.arraycopy(array, 0, result, offset, array.size)
            offset += array.size
        }

        return result
    }
}