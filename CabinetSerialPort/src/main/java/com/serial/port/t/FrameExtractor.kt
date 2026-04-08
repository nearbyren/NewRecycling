package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/21 下午9:10
 * @description:
 */

import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.ByteUtils
import java.io.ByteArrayOutputStream

/**
 * 帧提取器：专门处理字节流中的粘包、断帧和噪音数据
 */
class FrameExtractor(private val onFrameFound: (ByteArray) -> Unit) {

  private val buffer = ByteArrayOutputStream()

  /**
   * 往提取器中喂入新收到的原始字节
   */
  @Synchronized
  fun push(input: ByteArray) {
    // 1. 追加到现有缓冲区
    buffer.write(input)
    val data = buffer.toByteArray()
    var head = 0
    println("我的数据 接收处理 push ${ByteUtils.toHexString(data)}")
    BoxToolLogUtils.receiveOriginalLower(1, data)
    // 2. 滑动窗口扫描
    while (head < data.size) {
      // 寻找接收帧的法定包头 0x9B
      if (data[head] == 0x9B.toByte()) {
        // 探测长度位 (下标3) 是否已到位
        if (head + 3 < data.size) {
          val dataLen = data[head + 3].toInt() and 0xFF
          val totalLen = dataLen + 6 // 头(1)+Addr(1)+Cmd(1)+Len(1) + Data(n) + CS(1)+尾(1)

          // 探测整包是否收全
          if (head + totalLen <= data.size) {
            val packet = data.copyOfRange(head, head + totalLen)
            // 验证接收帧的法定包尾 0x9A
            if (packet.last() == 0x9A.toByte()) {
              // 【捕获完整响应包】
              onFrameFound(packet)
              head += totalLen // 跳过已处理的整包
              continue
            } else {
              // 发现伪帧头（虽然是9B开头，但尾巴不是9A），丢弃这个9B继续搜
              head++
              continue
            }
          } else {
            // 数据包还没收全，保留缓冲区，跳出循环等待下一次 push
            break
          }
        } else {
          // 连长度位都没齐，跳出循环
          break
        }
      } else {
        // 不是 0x9B 开头的字节全是干扰项（比如发送指令留下的 0x9A 或 A2），直接剔除
        head++
      }
    }

    // 3. 刷新缓冲区：只保留 head 之后未处理的字节
    val remaining = data.size - head
    buffer.reset()
    if (remaining > 0) {
      buffer.write(data, head, remaining)
      println("wo来了啊  push 刷新 $head-$remaining-${ ByteUtils.toHexString(data)}")

    }
  }

  fun clear() {
    buffer.reset()
  }
}