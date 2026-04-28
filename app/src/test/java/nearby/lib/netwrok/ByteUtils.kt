package nearby.lib.netwrok

import java.util.*

/**
字节处理工具类
 */
object ByteUtils {
    /**
    字节数组转十六进制字符串 (例如: [0x9B, 0x00] -> "9B 00")
     */
    @JvmStatic
    fun toHexString(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        val sb = StringBuilder()
        for (b in bytes) {
            val hex = Integer.toHexString(b.toInt() and 0xFF)
            if (hex.length == 1) sb.append('0')
            sb.append(hex.uppercase(Locale.getDefault())).append(" ")
        }
        return sb.toString().trim()
    }

    /**
    十六进制字符串转字节数组 (支持空格分隔或无分隔)
    例如: "9B 00 01" -> [0x9B, 0x00, 0x01]
     */
    @JvmStatic
    fun hexToBytes(hexString: String?): ByteArray {
        if (hexString == null || hexString.trim() == "") return byteArrayOf()
// 去除空格
        val cleanHex = hexString.replace(" ", "").replace("\n", "")
        val len = cleanHex.length
        if (len % 2 != 0) {
            throw IllegalArgumentException("十六进制字符串长度必须为偶数")
        }
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    /**
    截取字节数组
     */
    @JvmStatic
    fun subBytes(src: ByteArray, begin: Int, count: Int): ByteArray {
        val bs = ByteArray(count)
        System.arraycopy(src, begin, bs, 0, count)
        return bs
    }

    /**
    合并两个字节数组
     */
    @JvmStatic
    fun combine(data1: ByteArray, data2: ByteArray): ByteArray {
        val combined = ByteArray(data1.size + data2.size)
        System.arraycopy(data1, 0, combined, 0, data1.size)
        System.arraycopy(data2, 0, combined, data1.size, data2.size)
        return combined
    }
}