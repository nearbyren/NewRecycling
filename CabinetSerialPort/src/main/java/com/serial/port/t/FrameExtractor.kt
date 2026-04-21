package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/21 下午9:10
 * @description:
 */

import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.Loge
import java.io.ByteArrayOutputStream

/**
 * 帧提取器：专门处理字节流中的粘包、断帧和噪音数据
 */


class FrameExtractor(private val onFrameFound: (ByteArray) -> Unit) {
    private val tempBuffer = ByteArrayOutputStream()
    private var lastPushTime = 0L
    private val TIMEOUT = 3000L // 3秒超时

    @Synchronized
    fun push(input: ByteArray) {
        val now = System.currentTimeMillis()
        // 超时清理碎片
        if (now - lastPushTime > TIMEOUT) tempBuffer.reset()
        lastPushTime = now

        tempBuffer.write(input)
        var data = tempBuffer.toByteArray()
        var head = 0

        while (head < data.size) {
            if ((data[head].toInt() and 0xFF) == 0x9B) {
                if (head + 3 < data.size) {
                    val payloadLen = data[head + 3].toInt() and 0xFF
                    val totalLen = payloadLen + 6

                    if (head + totalLen <= data.size) {
                        val packet = data.copyOfRange(head, head + totalLen)

                        // 同时验证 帧尾 和 校验和
                        if ((packet.last().toInt() and 0xFF) == 0x9A && validate(packet)) {
                            onFrameFound(packet)
                            BoxToolLogUtils.savePush("完整 head：$head ${ByteUtils.toHexString(packet)}")
                            head += totalLen
                            continue
                        }
                    } else break
                } else break
            }
            head++
        }

        // 刷新缓冲区
        val remaining = data.size - head
        tempBuffer.reset()
        if (remaining > 0) tempBuffer.write(data, head, remaining)
    }

    private fun validate(packet: ByteArray): Boolean {
        if (packet.size < 6) {
            Loge.i("串口", "接232 测试新的方式 数据包长度不足: ${packet.size}")
            return false
        }

        // 获取数据长度
        val dataLength = packet[3].toInt() and 0xFF

        // 验证包长度是否匹配
        val expectedTotalLength = 6 + dataLength
        if (packet.size != expectedTotalLength) {
            Loge.i("串口", "接232 测试新的方式 数据包长度不匹配: 期望=$expectedTotalLength, 实际=${packet.size}")
            return false
        }

        // 计算校验和的范围：从帧头开始到数据区域结束
        // 数据区域结束位置 = 帧头(1) + 地址(1) + 命令(1) + 长度(1) + 数据(dataLength) = 4 + dataLength
        val dataEndIndex = 4 + dataLength  // 数据区域结束位置（不包括校验码）

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
        Loge.d(
            """接232 测试新的方式 
        校验码验证:
        - 数据长度: $dataLength
        - 计算范围: 0~${dataEndIndex - 1} (${dataEndIndex}字节)
        - 字节和: $sum
        - 计算校验码: $calculatedCheckCode (0x${calculatedCheckCode.toString(16).uppercase()})
        - 实际校验码: $actualCheckCode (0x${actualCheckCode.toString(16).uppercase()})
        - 验证结果: ${calculatedCheckCode == actualCheckCode}
    """.trimIndent()
        )
        BoxToolLogUtils.savePush("验证结果: ${calculatedCheckCode == actualCheckCode}")
        return calculatedCheckCode == actualCheckCode
    }

    fun clear() {
        tempBuffer.reset()
    }
}