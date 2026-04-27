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


/**
 * 帧提取器优化版
 * 解决了：分段截断、伪帧头堆积、校验失败回写等问题
 */
class FrameExtractorNew(private val onFrameFound: (ByteArray) -> Unit) {

    private val buffer = ByteArrayOutputStream(1024)
    private var lastProcessTime = 0L

    // 配置参数
    private val PROCESS_TIMEOUT = 1000L      // 缓冲区整体重置超时
    private val HALF_FRAME_MAX_STAY = 500L   // 单个半包最长等待时间（毫秒

    // 协议常量定义
    private val MIN_FRAME_SIZE = 6 // 帧头1+地址1+命令1+长度1+校验1+帧尾1
    private val MAX_FRAME_SIZE = 128 // 防护位：防止异常长度导致死等
    private val POS_DATA_LEN = 3 // 长度位下标

    @Synchronized
    fun push(input: ByteArray) {
        if (input.isEmpty()) return

        try {
            val currentTime = System.currentTimeMillis()

            // 1. 超时重置逻辑
            if (currentTime - lastProcessTime > PROCESS_TIMEOUT && buffer.size() > 0) {
                BoxToolLogUtils.savePush2("业务流：解析超时，重置缓冲区")
                buffer.reset()
            }

            // 2. 写入新数据
            buffer.write(input)

            // 3. 循环解析缓冲区
            processBuffer(currentTime)

            // 4. 【关键】处理完了，再更新时间戳为最新
            lastProcessTime = currentTime

        } catch (e: Exception) {
            BoxToolLogUtils.savePush2("业务流：解析异常: ${e.message}")
            buffer.reset()
        }
    }

    private fun processBuffer(currentTime: Long) {
        val currentData = buffer.toByteArray()
        var currentIndex = 0
        var lastValidEnd = 0 // 记录最后一次处理完的位置

        while (currentIndex < currentData.size) {
            // A. 查找帧头 0x9B
            val headerIndex = findFrameHeader(currentData, currentIndex)
            if (headerIndex == -1) {
                // 全缓冲区没有帧头，全部标记为已处理
                lastValidEnd = currentData.size
                BoxToolLogUtils.savePush2("业务流：后面全是噪音")
                break
            }

            // 只要找到了帧头，帧头之前的所有数据都判定为垃圾，推进已处理指针
            lastValidEnd = headerIndex

            // B. 检查长度位是否已接收
            if (headerIndex + POS_DATA_LEN >= currentData.size) {
                // 数据不够读长度位，跳出，等待下一次 push 拼接
                BoxToolLogUtils.savePush2("业务流：数据不够读长度位，跳出，等待下一次 push 拼接")
                // 如果这个半包在缓冲区停留超过了 HALF_FRAME_MAX_STAY，强制跳过
                if (currentTime - lastProcessTime > HALF_FRAME_MAX_STAY) {
                    BoxToolLogUtils.savePush2("业务流：[丢弃] 指令位缺失包停留过久")
                    currentIndex = headerIndex + 1
                    lastValidEnd = currentIndex
                    continue
                }
                break
            }

            // C. 获取协议声明的长度
            val dataContentLen = currentData[headerIndex + POS_DATA_LEN].toInt() and 0xFF
            val totalFrameLen = MIN_FRAME_SIZE + dataContentLen


            if (totalFrameLen > MAX_FRAME_SIZE || totalFrameLen < MIN_FRAME_SIZE) {
                BoxToolLogUtils.savePush2("业务流：[跳过] 非法包长度: $totalFrameLen")
                currentIndex = headerIndex + 1
                lastValidEnd = currentIndex
                continue
            }

            // D. 完整性检查 (重点：处理丢尾巴的情况)
            if (headerIndex + totalFrameLen > currentData.size) {
                // 如果这个完整长度的包迟迟不来齐，不要一直憋着缓冲区
                if (currentTime - lastProcessTime > HALF_FRAME_MAX_STAY) {
                    BoxToolLogUtils.savePush2("业务流：[强制弹出] 半包延迟过高(${currentData.size}/$totalFrameLen)，丢弃旧头")
                    currentIndex = headerIndex + 1
                    lastValidEnd = currentIndex
                    continue
                }
                break // 包还没收齐，等待拼包
            }

            // F. 检查帧尾 0x9A
            val frameEndIndex = headerIndex + totalFrameLen - 1
            if (currentData[frameEndIndex] != SendByteData.RE_FRAME_END) {
                BoxToolLogUtils.savePush2("业务流：帧尾校验失败，跳过此帧头")
                currentIndex = headerIndex + 1
                lastValidEnd = currentIndex
                continue
            }

            // G. 提取完整包并校验和
            val packet = currentData.copyOfRange(headerIndex, headerIndex + totalFrameLen)
            if (validateCheckSum(packet)) {
                // --- 校验通过，执行回调 ---
                BoxToolLogUtils.savePush2("业务流：[完成] 完整包: ${ByteUtils.toHexString(packet)}")
                onFrameFound(packet)

                // 推进指针到包末尾
                currentIndex = headerIndex + totalFrameLen
                lastValidEnd = currentIndex
            } else {
                BoxToolLogUtils.savePush2("业务流：[丢弃] 校验和失败")
                currentIndex = headerIndex + 1
                lastValidEnd = currentIndex
            }
        }

        // 4. 清理已处理的数据，保留残留数据
        buffer.reset()
        if (lastValidEnd < currentData.size) {
            val remaining = currentData.copyOfRange(lastValidEnd, currentData.size)
            buffer.write(remaining)
        }
    }

    private fun findFrameHeader(data: ByteArray, start: Int): Int {
        val target = 0x9B // 我们期待的无符号值
        for (i in start until data.size) {
            val current = data[i].toInt() and 0xFF // 转为 155
            if (current == target) return i
        }
        return -1
    }

    private fun validateCheckSum(packet: ByteArray): Boolean {
        if (packet.size < MIN_FRAME_SIZE) return false

        val dataLen = packet[POS_DATA_LEN].toInt() and 0xFF
        val checkSumIndex = 4 + dataLen // 校验码位置 = 帧头(1)+地址(1)+命令(1)+长度(1)+数据(N)

        var sum = 0
        for (i in 0 until checkSumIndex) {
            sum += packet[i].toInt() and 0xFF
        }

        val calculated = sum % 256
        val actual = packet[checkSumIndex].toInt() and 0xFF
        return calculated == actual
    }

    fun clear() {
        buffer.reset()
    }
}