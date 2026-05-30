package com.serial.port.t

import com.serial.port.utils.AsyncBatchLogger
import com.serial.port.utils.SendByteData
import java.io.ByteArrayOutputStream

/**
 * 帧提取器：粘包 / 断帧重组。
 *
 * 注意：半包只缓存等待后续字节，不因「距上次 push 过久」丢弃——否则 5s 轮询间隔下
 * CMD5 分片回包会被误删，表现为任意超时时间均失败。
 */
class FrameExtractorNew(private val onFrameFound: (ByteArray) -> Unit) {

    private val buffer = ByteArrayOutputStream(1024)

    private val MIN_FRAME_SIZE = 6
    private val MAX_FRAME_SIZE = 256
    private val POS_DATA_LEN = 3

    @Synchronized
    fun push(input: ByteArray) {
        if (input.isEmpty()) return
        try {
            buffer.write(input)
            processBuffer()
        } catch (e: Exception) {
            AsyncBatchLogger.log("push Parsing exceptions: ${e.message}", -1)
            buffer.reset()
        }
    }

    private fun processBuffer() {
        val currentData = buffer.toByteArray()
        var currentIndex = 0
        var lastValidEnd = 0
        while (currentIndex < currentData.size) {
            val headerIndex = findFrameHeader(currentData, currentIndex)
            if (headerIndex == -1) {
                lastValidEnd = currentData.size
                break
            }
            lastValidEnd = headerIndex

            if (headerIndex + POS_DATA_LEN >= currentData.size) {
                break
            }

            val dataContentLen = currentData[headerIndex + POS_DATA_LEN].toInt() and 0xFF
            val totalFrameLen = MIN_FRAME_SIZE + dataContentLen

            if (totalFrameLen > MAX_FRAME_SIZE || totalFrameLen < MIN_FRAME_SIZE) {
                AsyncBatchLogger.log("push [ skip ] illegal packet length: $totalFrameLen", -1)
                currentIndex = headerIndex + 1
                lastValidEnd = currentIndex
                continue
            }

            if (headerIndex + totalFrameLen > currentData.size) {
                break
            }

            val frameEndIndex = headerIndex + totalFrameLen - 1
            if (currentData[frameEndIndex] != SendByteData.RE_FRAME_END) {
                AsyncBatchLogger.log("push Frame end check failed, skip this frame header", -1)
                currentIndex = headerIndex + 1
                lastValidEnd = currentIndex
                continue
            }

            val packet = currentData.copyOfRange(headerIndex, headerIndex + totalFrameLen)
            if (validateCheckSum(packet)) {
                onFrameFound(packet)
                currentIndex = headerIndex + totalFrameLen
                lastValidEnd = currentIndex
            } else {
                AsyncBatchLogger.log("push [Checksum] failure", -1)
                currentIndex = headerIndex + 1
                lastValidEnd = currentIndex
            }
        }

        buffer.reset()
        if (lastValidEnd < currentData.size) {
            buffer.write(currentData, lastValidEnd, currentData.size - lastValidEnd)
        }
    }

    private fun findFrameHeader(data: ByteArray, start: Int): Int {
        val target = 0x9B
        for (i in start until data.size) {
            if ((data[i].toInt() and 0xFF) == target) return i
        }
        return -1
    }

    private fun validateCheckSum(packet: ByteArray): Boolean {
        if (packet.size < MIN_FRAME_SIZE) return false
        val dataLen = packet[POS_DATA_LEN].toInt() and 0xFF
        val checkSumIndex = 4 + dataLen
        if (packet.size < checkSumIndex + 2) return false
        var sum = 0
        for (i in 0 until checkSumIndex) {
            sum += packet[i].toInt() and 0xFF
        }
        return (sum % 256) == (packet[checkSumIndex].toInt() and 0xFF)
    }

    fun clear() {
        buffer.reset()
    }
}
