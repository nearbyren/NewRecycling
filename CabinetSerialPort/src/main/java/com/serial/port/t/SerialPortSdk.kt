package com.serial.port.t

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * @author: lr
 * @created on: 2026/3/21 下午4:07
 * @description:
 */

/**
 * CabinetSdk: 柜体控制系统对外唯一入口
 * 采用单例模式，封装了底层复杂的串口通信细节
 */

object SerialPortSdk {
    private var isInit = false

    const val CMD_POS = 2
    const val ADDR = 0x00.toByte()

    /**
     * 发送256字节文件
     */
    const val CMD0 = 0x00.toByte()

    /**
     * 打开/关闭投口 0x01
     */
    const val CMD1 = 0x01.toByte()

    /**
     * 查询投口状态 0x02
     */
    const val CMD2 = 0x02.toByte()

    /**
     * 打开清运门 0x03
     */
    const val CMD3 = 0x03.toByte()

    /**
     * 查询当前重量 0x04
     */
    const val CMD4 = 0x04.toByte()

    /**
     * 查询当前设备状态 0x05
     */
    const val CMD5 = 0x05.toByte()

    /**
     * 灯光控制 0x06
     */
    const val CMD6 = 0x06.toByte()

    /**
     * 进入升级状态 0x07
     */
    const val CMD7 = 0x07.toByte()

    /**
     * 查询升级状态 0x08
     */
    const val CMD8 = 0x08.toByte()

    /**
     * 查询升级结果 0x09
     */
    const val CMD9 = 0x09.toByte()

    /**
     * 重启指令 0x0A
     */
    const val CMD10 = 0x0A.toByte()

    /**
     * 查询软件版本 0x0B
     */
    const val CMD11 = 0x0B.toByte()

    /**
     * 去皮清零 0x10
     */
    const val CMD16 = 0x10.toByte()

    /**
     * 校准 0x11
     */
    const val CMD17 = 0x11.toByte()

    /**
     * 文件发送效验 0x12
     */
    const val CMD18 = 0x12.toByte()

    /**
     * 设置阻力值 0x13
     */
    const val CMD19 = 0x13.toByte()


    fun init(path232: String = "/dev/ttyS0", baudRate: Int = 115200) {
        if (isInit) return
        SerialPortManagerSdk.instance.init(path232, baudRate)
        isInit = true
    }

    private val _flowBusinessSetup = MutableStateFlow(DoorResult(cmd = -1, cmdStatus = false))
    val flowBusinessSetup = _flowBusinessSetup.asStateFlow()


    fun release() {
        SerialPortManagerSdk.instance.closeAllSerialPort()
        isInit = false
    }

    /** 设置阻力值 */
    suspend fun startRodHinder(code: Int, number: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.startRodHinder(code, number)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 启动投口 */
    suspend fun turnDoor(code: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.turnDoor(code)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 启动投口 */
    suspend fun turnDoorRetries(code: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.turnDoorRetries(code)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 查询投口状态 */
    suspend fun turnDoorStatus(code: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.turnDoorStatus(code)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 开启和查询清运门 */
    suspend fun openQueryClear(code: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.openQueryClear(code)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 查询重量 */
    suspend fun queryWeight(boxId: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.queryWeight(boxId)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 查询货柜状态 */
    suspend fun queryStatus(): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.queryStatus()
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 灯光操作 */
    suspend fun startLights(code: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.startLights(code)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 去皮清零 */
    suspend fun startCalibrationQP(doorGeX: Int, code: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.startCalibrationQP(doorGeX, code)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 校准 */
    suspend fun startCalibration(doorGeX: Int, code: Int): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.startCalibration(doorGeX, code)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 进入升级 升级状态 文件校验 重启 查版本*/
    suspend fun firmwareUpgrade78910(commandType: Int, data: ByteArray): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.firmwareUpgrade78910(commandType, data)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }

    /** 发送文件 */
    suspend fun firmwareUpgradeFile(byte: ByteArray): Result<DoorResult> {
        if (!isInit) return Result.failure(Exception("SDK未初始化"))
        val result = SerialPortCoreSdk.instance.firmwareUpgradeFile(byte)
        _flowBusinessSetup.value = result.getOrNull() ?: DoorResult(cmd = -1, cmdStatus = false)
        return result
    }


}