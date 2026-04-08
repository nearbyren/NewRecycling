package com.serial.port.utils

object SendByteData {
    /***
     *  0x8B
     */
    const val EX_RE_FRAME_HEADER = 0x8B.toByte()

    /***
     *  0x8A
     */
    const val EX_RE_FRAME_END = 0x8A.toByte()


    /***
     * 接收 0x9B
     */
    const val RE_FRAME_HEADER = 0x9B.toByte()

    /***
     * 接收 0x9A
     */
    const val RE_FRAME_END = 0x9A.toByte()

    /***
     * 发送 0x9A
     */
    const val SE_FRAME_HEADER = 0x9A.toByte()

    /***
     * 发送 0x9B
     */
    const val SE_FRAME_END = 0x9B.toByte()


    /**
     * 构造协议帧的字节数组
     *
     * 方便开发调试去掉效验码
     * @param command 指令
     * @param data 数据域
     * @return 构造好的字节数组 不包含效验码
     */
    fun createSendNotCheckSumByte(command: Byte, data: ByteArray): ByteArray {
        val frameHeader: Byte = SE_FRAME_HEADER
        val address: Byte = 0x00.toByte()
        val frameTail: Byte = SE_FRAME_END
        val dataLength: Byte = data.size.toByte()
        // 构造帧（帧头 + 地址 + 指令 + 长度 + 数据域）
        val frame = mutableListOf<Byte>().apply {
            add(frameHeader)
            add(address)
            add(command)
            add(dataLength)
            addAll(data.toList())
        }
        frame.add(frameTail)
        // 转为字节数组返回
        return frame.toByteArray()
    }

    /**
     * 构造协议帧的字节数组
     *
     * @param command 指令
     * @param data 数据域
     * @return 构造好的字节数组 包含效验码
     */
    fun createSendCheckSumByte(command: Byte, add: Byte, data: ByteArray): ByteArray {
        val frameHeader: Byte = SE_FRAME_HEADER
        val address: Byte = add
        val frameTail: Byte = SE_FRAME_END
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