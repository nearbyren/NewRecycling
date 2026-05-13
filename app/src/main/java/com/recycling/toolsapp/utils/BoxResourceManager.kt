package com.recycling.toolsapp.utils

import com.serial.port.t.SerialPortEngine
import com.serial.port.utils.AsyncBatchLogger

object BoxResourceManager {
    
    // 将原先 ViewModel 里的关闭逻辑迁移到这里
    fun releaseAllResources() {

        // 1. 停止日志（使用我们之前写的异步工具）
        AsyncBatchLogger.log("timed restart Releasing Resources...",-1)
        AsyncBatchLogger.destroy()

        // 3. 关闭串口
        SerialPortEngine.stop()

        // 4. 关闭其他 Job
        // 如果有全局作用域，在这里 cancel
    }
}