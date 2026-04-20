package com.serial.port.t

import android.util.Log
import com.serial.port.SerialPort
import com.serial.port.utils.Loge
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * @author: lr
 * @created on: 2026/3/21 下午4:02
 * @description:
 */





/**
 * 串口物理管理类（单例）
 * 职责：权限申请、底层的 Open 和 Close
 */

class SerialPortManagerSdk private constructor() {
    private var mSerialPort: SerialPort? = null
    private var mFileInputStream: FileInputStream? = null
    private var mFileOutputStream: FileOutputStream? = null

    companion object {
        val instance by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { SerialPortManagerSdk() }
    }

    /**
     * 开启物理设备
     */
    fun openDevice(path: String, baud: Int): Boolean {
        if (mSerialPort != null) return true

        try {
            // 权限处理
            val device = File(path)
            if (!device.canRead() || !device.canWrite()) {
                val process = Runtime.getRuntime().exec(arrayOf("/system/bin/su", "-c", "chmod 777 $path"))
                process.waitFor()
            }

            // 【关键修改点】现在 SerialPort 接收两个参数
            mSerialPort = SerialPort(path, baud)

            mFileInputStream = mSerialPort?.inputStream as? FileInputStream
            mFileOutputStream = mSerialPort?.outputStream as? FileOutputStream

            Log.d("SerialPort", "硬件串口已打开: $path")
            return true
        } catch (e: Exception) {
            Log.e("SerialPort", "硬件串口打开失败: ${e.message}")
            return false
        }
    }

    fun getInputStream() = mFileInputStream
    fun getOutputStream() = mFileOutputStream

    /**
     * 重启 App 前必须调用
     */
    fun closeAllSerialPort() {
        try {
            mFileInputStream?.close()
            mFileOutputStream?.close()
            // 触发 C++ 层的 close(fd)
            mSerialPort?.close()
        } catch (e: Exception) {
            Log.e("SerialPort", "释放硬件异常: ${e.message}")
        } finally {
            mFileInputStream = null
            mFileOutputStream = null
            mSerialPort = null
        }
    }
}