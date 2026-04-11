package com.recycling.toolsapp.utils

object TestByteData {

    fun main() {
        println("十六进制字符串转字节数组转换器")
        println("=".repeat(40))

        // 示例输入
        val input = "0102030405060708"
        println("输入字符串: $input")

        try {
            val byteArray = hexStringToByteArray(input)
            println("转换结果:")
            println("字节数组: ${byteArray.joinToString(", ", "[", "]")}")
            println("十六进制表示: ${byteArray.joinToString(" ") { "%02X".format(it) }}")
            println("十进制表示: ${byteArray.joinToString(" ") { it.toString() }}")

            // 验证转换结果
            println("\n验证转换:")
            val backToHex = byteArrayToHexString(byteArray)
            println("还原为十六进制: $backToHex")
            println("转换是否成功: ${input == backToHex}")

        } catch (e: Exception) {
            println("转换错误: ${e.message}")
        }
    }

    /**
     * 将十六进制字符串转换为字节数组
     * @param hexString 十六进制字符串（每两个字符表示一个字节）
     * @return 对应的字节数组
     */
    fun hexStringToByteArray(hexString: String): ByteArray {
        require(hexString.length % 2 == 0) { "十六进制字符串长度必须为偶数" }

        val result = ByteArray(hexString.length / 2)

        for (i in hexString.indices step 2) {
            val byteStr = hexString.substring(i, i + 2)
            result[i / 2] = byteStr.toInt(16).toByte()
        }

        return result
    }

    /**
     * 将字节数组转换回十六进制字符串
     * @param byteArray 字节数组
     * @return 对应的十六进制字符串
     */
    fun byteArrayToHexString(byteArray: ByteArray): String {
        return byteArray.joinToString("") { "%02X".format(it) }
    }

    /**
     * 扩展函数版本 - 更简洁的使用方式
     */
    fun String.hexToBytes(): ByteArray = hexStringToByteArray(this)
    fun ByteArray.bytesToHex(): String = byteArrayToHexString(this)
}