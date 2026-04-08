package com.serial.port.t

import android.util.Log
import com.serial.port.SerialPort
import java.io.File

/**
 * @author: lr
 * @created on: 2026/3/21 下午4:02
 * @description:
 */


/** JNI 驱动调用与 VM 绑定层 */
class SerialPortManagerSdk private constructor() : SerialPort() { // 继承自你的 JNI SerialPort 类
    var serialVM: SerialVM? = null
        private set

    companion object {
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SerialPortManagerSdk() }
    }

    /** 申请 root 权限并初始化 VM */
    fun init(path: String, baud: Int) {
        try {
            val device = File(path)
            if (!device.canRead() || !device.canWrite()) {
                // 执行 chmod 777 申请 Linux 权限
                Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c", "chmod 777 $path")).waitFor()
            }

            if (serialVM == null) {
                serialVM = SerialVM()
                serialVM?.startMonitor(path, baud)
            }
        } catch (e: Exception) {
            Log.e("SerialPort", "初始化权限或VM失败: ${e.message}")
        }
    }

    /** 底层打开设备的方法名，请确保与你的 JNI 方法名一致 */
    fun openDevice(path: String, baud: Int) = open(path, baud, 0)

    fun closeAllSerialPort() {
        serialVM?.stop()
        serialVM = null
        close(1) // 调用 JNI 的关闭
    }
}