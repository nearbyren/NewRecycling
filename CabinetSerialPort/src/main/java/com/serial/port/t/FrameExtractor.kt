package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/21 下午9:10
 * @description:
 */

import com.serial.port.BuildConfig
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.Loge
import java.io.ByteArrayOutputStream
/**
 * 帧提取器：专门处理字节流中的粘包、断帧和噪音数据
 */


class FrameExtractor(private val onFrameFound: (ByteArray) -> Unit) {

    // 累积缓冲区，用于存放不完整的碎片数据
    private val tempBuffer = ByteArrayOutputStream()
    private val MAX_BUFFER_SIZE = 8192 // 防止异常数据导致内存溢出

    @Synchronized
    fun push(input: ByteArray) {
        // 1. 自动清理超大异常缓存
        if (tempBuffer.size() > MAX_BUFFER_SIZE) {
            BoxToolLogUtils.savePrintln("业务流：自动清理超大异常缓存: ${tempBuffer.size()}")
            tempBuffer.reset()
        }

        // 2. 将新收到的字节追加到旧数据后面
        tempBuffer.write(input)

        var data = tempBuffer.toByteArray()
        var head = 0

        // 3. 滑动窗口扫描：只要缓冲区还有数据，就持续尝试找包
        while (head < data.size) {
            // 寻找协议头 0x9B
            if (data[head] == 0x9B.toByte()) {
                // 探测长度位是否到位（下标3为长度位）
                if (head + 3 < data.size) {
                    val payloadLen = data[head + 3].toInt() and 0xFF
                    val totalLen = payloadLen + 6 // 完整包长 = 头(1)+Addr(1)+Cmd(1)+Len(1) + Payload(n) + CS(1)+尾(1)

                    // 探测缓冲区内是否有足够的字节组成一个整包
                    if (head + totalLen <= data.size) {
                        val packet = data.copyOfRange(head, head + totalLen)

                        // 验证帧尾 0x9A
                        if (packet.last() == 0x9A.toByte()) {
                            // 【命中完整帧】通过回调传出
                            onFrameFound(packet)
                            head += totalLen // 指针跳过整包，继续看后面还有没有包
                            BoxToolLogUtils.savePush("完整 head：$head ${ByteUtils.toHexString(packet)}")
                            continue
                        } else {
                            // 伪帧头：虽是9B开头但尾部不符，移动1字节寻找下一个9B
                            BoxToolLogUtils.savePush("伪帧头：虽是9B开头但尾部不符，移动1字节寻找下一个9B：${ByteUtils.toHexString(data)}")
                            head++
                        }
                    } else {
                        // 【关键点】数据包不全，跳出循环，等待下一次 push 拼接
                        BoxToolLogUtils.savePush("【关键点】数据包不全，跳出循环，等待下一次 push 拼接：${ByteUtils.toHexString(data)}")
                        break
                    }
                } else {
                    // 连长度位都还没收齐
                    BoxToolLogUtils.savePush("连长度位都还没收齐：${ByteUtils.toHexString(data)}")
                    break
                }
            } else {
                // 非法开头，直接剔除
                BoxToolLogUtils.savePush("非法开头，直接剔除：${ByteUtils.toHexString(data)}")
                head++
            }
        }

        // 4. 更新缓冲区：只保留 head 之后那些还没处理的“半截”数据
        val remaining = data.size - head
        BoxToolLogUtils.savePush("末尾 " +
                "$remaining ${data.size} $head ${ByteUtils.toHexString(tempBuffer.toByteArray())}" )
        tempBuffer.reset()
        if (remaining > 0) {
            tempBuffer.write(data, head, remaining)
        }
    }

    fun clear() {
        tempBuffer.reset()
    }
}