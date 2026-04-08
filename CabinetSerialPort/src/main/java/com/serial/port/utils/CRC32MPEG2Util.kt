package com.serial.port.utils


import java.io.File
import java.util.zip.Checksum

object CRC32MPEG2Util : Checksum {
    private var crc = 0xFFFFFFFFL
    private val polynomial = 0x04C11DB7L  // MPEG2专用多项式

    override fun update(byte: Int) {
        var b = byte.toLong() and 0xFF
        crc = crc xor (b shl 24)
        repeat(8) {
            crc = if (crc and 0x80000000L != 0L) {
                (crc shl 1) xor polynomial
            } else {
                crc shl 1
            } and 0xFFFFFFFFL
        }
    }

    override fun update(bytes: ByteArray, off: Int, len: Int) {
        require(off >= 0 && len >= 0 && off + len <= bytes.size)
        for (i in off until off + len) {
            update(bytes[i].toInt())
        }
    }

    override fun getValue(): Long = crc xor 0x00000000L  // MPEG2不进行最终异或
    override fun reset() { crc = 0xFFFFFFFFL }

    // 扩展方法：文件流校验
    fun computeFile(path: String): Long {
        return File(path).inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } >= 0) {
                update(buffer, 0, bytesRead)
            }
            getValue().also { reset() }
        }
    }
}
