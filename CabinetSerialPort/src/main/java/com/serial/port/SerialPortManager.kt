package com.serial.port

import android.annotation.SuppressLint
import com.serial.port.utils.Loge
import com.serial.port.utils.ShellUtils
import com.serial.port.vm.SerialVM


class SerialPortManager private constructor() : SerialPort("/dev/ttyS0", 115200) {
    private var mSdk232: ConfigurationSdk? = null
    private var mSdk485: ConfigurationSdk? = null

    //标记是否初始化
    private var isInit = false
    var serialVM: SerialVM? = null

    /***************************************初始化信息************************************/

    private object SerialPortInstance {
        @SuppressLint("StaticFieldLeak")
        val SERIALPORT = SerialPortManager()
    }

    companion object {
        val instance: SerialPortManager
            get() = SerialPortInstance.SERIALPORT
    }

    fun init(sdk232: ConfigurationSdk?) {
        if (isInit) return
        this.mSdk232 = sdk232
        serialVM = SerialVM()
        openSerialPort232()
        serialVM?.initCollect()
        isInit = true
    }
    /***************************************初始化信息************************************/

    /***************************************指令模块************************************/

    /***
     * 下发升级指令
     * @param sendBytes
     */
    fun upgrade232(sendBytes: ByteArray) {
        serialVM?.upgrade232(-1, sendBytes)
    }

    /***
     * 下发开仓指令
     * @param sendBytes
     */
    fun issuedOpen(lockerId: Int, sendBytes: ByteArray) {
        serialVM?.open(lockerId, sendBytes)
    }

    /***
     * 下发故障指令
     * @param sendBytes
     */
    fun issuedFault(lockerId: Int, sendBytes: ByteArray) {
        serialVM?.fault(lockerId, sendBytes)
    }

    /**
     * 下发查询状态指令
     * @param sendBytes
     */
    fun issuedStatus(sendBytes: ByteArray) {
        serialVM?.status(sendBytes)
    }

    /***************************************指令模块************************************/

    /***************************************串口打开与关闭************************************/

    /**
     * 打开串口232
     *
     */
    private fun openSerialPort232() {}

    /**
     * 关闭所有串口
     *
     */
    fun closeAllSerialPort() {
        isInit = false
        close232SerialPort()
    }

    /**
     * 关闭232串口
     */
    fun close232SerialPort() {
        isInit = false
        serialVM?.fd232?.let {
//            close(1)
        }
        serialVM?.close232SerialPort()
    }

    /***************************************串口打开与关闭************************************/
}