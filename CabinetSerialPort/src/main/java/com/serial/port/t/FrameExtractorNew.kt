package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/21 下午9:10
 * @description:
 */

import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.Loge
import com.serial.port.utils.SendByteData
import java.io.ByteArrayOutputStream

/**
 * 帧提取器：专门处理字节流中的粘包、断帧和噪音数据
 */


class FrameExtractorNew(private val onFrameFound: (ByteArray) -> Unit) {
    /***
     * 缓冲区管理
     */
    private val bufferNew232 = ByteArrayOutputStream(1024)
    private var lastProcessTime = 0L
//    private val PROCESS_TIMEOUT = 5000L // 5秒超时
    private val PROCESS_TIMEOUT = 1000L // 1秒超时

    /*** 指令位 2x*/
    val CMD_POS = 2

    /***x* 取出校验码位 2*/
    val CHECK_POS_DATA = 2

    /*** 取出数据域位 3  */
    val DATA_POS_LENGTH = 3

    /***  前四位 4*/
    val BEFORE_FOUR_POS = 4

    /*** 完整包 6x*/
    val COMPLETE_PACKAGE = 6

    /***
     * @param buffer 完整数据域
     * @param startIndex
     * 查找帧头
     */
    private fun findFrameHeader(buffer: ByteArray, startIndex: Int): Int {
        for (i in startIndex until buffer.size) {
            if (buffer[i] == SendByteData.RE_FRAME_HEADER) return i
        }
        return -1
    }

    /***
     * @param packet 完整数据域
     * 验证校验码 即是末尾前一位
     */
    private fun validateCheckCode(packet: ByteArray): Boolean {
        if (packet.size < COMPLETE_PACKAGE) {
//            Loge.i("串口", "接232 测试新的方式 数据包长度不足: ${packet.size}")
            return false
        }

        // 获取数据长度
        val dataLength = packet[DATA_POS_LENGTH].toInt() and 0xFF

        // 验证包长度是否匹配
        val expectedTotalLength = COMPLETE_PACKAGE + dataLength
        if (packet.size != expectedTotalLength) {
//            Loge.i("串口", "接232 测试新的方式 数据包长度不匹配: 期望=$expectedTotalLength, 实际=${packet.size}")
            return false
        }

        // 计算校验和的范围：从帧头开始到数据区域结束
        // 数据区域结束位置 = 帧头(1) + 地址(1) + 命令(1) + 长度(1) + 数据(dataLength) = 4 + dataLength
        val dataEndIndex = BEFORE_FOUR_POS + dataLength  // 数据区域结束位置（不包括校验码）

        // 计算从帧头到数据区域结束的所有字节的无符号和
        var sum = 0
        for (i in 0 until dataEndIndex) {
            sum += packet[i].toInt() and 0xFF
        }

        // 计算校验码：和除以256的余数
        val calculatedCheckCode = sum % 256

        // 获取包中的实际校验码（位置在数据区域之后）
        val actualCheckCode = packet[dataEndIndex].toInt() and 0xFF

        // 记录校验信息（调试用）
//        Loge.d(
//            """接232 测试新的方式
//        校验码验证:
//        - 数据长度: $dataLength
//        - 计算范围: 0~${dataEndIndex - 1} (${dataEndIndex}字节)
//        - 字节和: $sum
//        - 计算校验码: $calculatedCheckCode (0x${calculatedCheckCode.toString(16).uppercase()})
//        - 实际校验码: $actualCheckCode (0x${actualCheckCode.toString(16).uppercase()})
//        - 验证结果: ${calculatedCheckCode == actualCheckCode}
//    """.trimIndent()
//        )
//        BoxToolLogUtils.savePush("验证结果: ${calcul/atedCheckCode == actualCheckCode}")
        return calculatedCheckCode == actualCheckCode
    }

    /**
     * 记录缓冲区状态
     */
    private fun logBufferStatus(totalSize: Int, processedBytes: Int) {
        val remaining = totalSize - processedBytes
//        Loge.d("缓冲区处理: 总共${totalSize}字节, 已处理${processedBytes}字节, 剩余${remaining}字节")
    }

    @Synchronized
    fun push(input: ByteArray) {
//        Loge.i("串口232", "接232 测试新的方式 大小：${input.size} 原始：${ByteUtils.toHexString(input)}")
        try {
            BoxToolLogUtils.savePush("业务流：input:${ByteUtils.toHexString(input)}")

            val currentTime = System.currentTimeMillis()

            // 如果距离上次处理时间过长，清空缓冲区（避免处理残留的无效数据）
            if (currentTime - lastProcessTime > PROCESS_TIMEOUT && bufferNew232.size() > 0) {
//                Loge.i("串口232", "接232 测试新的方式 处理超时，清空缓冲区残留数据: ${bufferNew232.size()}字节")
                bufferNew232.reset()
            }

            lastProcessTime = currentTime
            // 1. 追加新数据到缓冲区
            bufferNew232.write(input)
            val currentBuffer = bufferNew232.toByteArray()

            // 2. 处理缓冲区中的数据
            var processedBytes = 0
            var currentIndex = 0

            while (currentIndex < currentBuffer.size) {
                // 3. 查找帧头 (0x9B)
                val headerIndex = findFrameHeader(currentBuffer, currentIndex)
                if (headerIndex == -1) {
                    // 没有找到帧头，所有数据都无法处理
                    processedBytes = currentBuffer.size
                    BoxToolLogUtils.savePush2("业务流：没有找到帧头，所有数据都无法处理: ${ByteUtils.toHexString(currentBuffer)}}")
                    break
                }

                // 4. 检查是否有足够的数据获取长度字段 (header + 3)
                if (headerIndex + DATA_POS_LENGTH >= currentBuffer.size) {
                    BoxToolLogUtils.savePush2("业务流：检查是否有足够的数据获取长度字段: ${ByteUtils.toHexString(currentBuffer)}}")
                    // 数据不足，保留从帧头开始的所有数据
                    processedBytes = headerIndex
                    break
                }

                // 5. 获取数据长度 (第4个字节)
                val dataLength = currentBuffer[headerIndex + DATA_POS_LENGTH].toInt() and 0xFF

                // 6. 计算完整包长度 (修正：6 + dataLength)
                val totalLength = COMPLETE_PACKAGE + dataLength  // 帧头1 + 地址1 + 命令1 + 长度1 + 数据N + 校验码1 + 帧尾1

                // 7. 检查完整数据包
                if (headerIndex + totalLength > currentBuffer.size) {
                    BoxToolLogUtils.savePush2("业务流：数据包不完整，保留从帧头开始的数据: ${ByteUtils.toHexString(currentBuffer)}}")
                    // 数据包不完整，保留从帧头开始的数据
                    processedBytes = headerIndex
                    break
                }

                // 8. 检查帧尾 (0x9A)
                val frameEndIndex = headerIndex + totalLength - 1  // 帧尾在最后一个位置
                if (currentBuffer[frameEndIndex] != SendByteData.RE_FRAME_END) {
                    BoxToolLogUtils.savePush2("业务流：帧尾异常！预期0x9A, 实际: ${String.format("%02X", currentBuffer[frameEndIndex])}")
                    // 帧尾错误，跳过这个帧头继续查找
                    currentIndex = headerIndex + 1
                    continue
                }

                // 9. 提取完整数据包
                val packet = currentBuffer.copyOfRange(headerIndex, headerIndex + totalLength)

                // 10. 校验和验证
                if (!validateCheckCode(packet)) {
                    // 校验失败，跳过这个包继续查找下一个
                    BoxToolLogUtils.savePush2("业务流：校验和失败！包内容: ${ByteUtils.toHexString(packet)}")
                    currentIndex = headerIndex + 1
                    continue
                }

                // 11. 处理有效数据包
                onFrameFound(packet)//新方式2
                BoxToolLogUtils.savePush("业务流：完整：${ByteUtils.toHexString(packet)}")
                // 12. 移动处理位置到下一个包
                currentIndex = headerIndex + totalLength
                processedBytes = currentIndex
            }

            // 13. 保存未处理数据到缓冲区
            bufferNew232.reset()
            if (processedBytes < currentBuffer.size) {
                val remainingData = currentBuffer.copyOfRange(processedBytes, currentBuffer.size)
                bufferNew232.write(remainingData)

                // 调试信息：显示保留的未处理数据长度
                if (remainingData.isNotEmpty()) {
//                    Loge.d("保留未处理数据: ${remainingData.size} 字节")
                }
            }

            // 调试信息：显示缓冲区状态
            logBufferStatus(currentBuffer.size, processedBytes)

        } catch (e: Exception) {
//            Loge.e("处理接收数据时发生异常: ${e.message}")
            // 发生异常时清空缓冲区，避免错误累积
            bufferNew232.reset()
        }
    }

    fun clear() {
        bufferNew232.reset()
    }
}