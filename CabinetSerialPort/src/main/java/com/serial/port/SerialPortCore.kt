package com.serial.port


import com.serial.port.utils.CmdCode
import com.serial.port.utils.CmdCode.CLEAR_OPEN_1_1
import com.serial.port.utils.CmdCode.CLEAR_OPEN_2_1
import com.serial.port.utils.CmdCode.CLEAR_QUERY_1_0
import com.serial.port.utils.CmdCode.CLEAR_QUERY_2_0
import com.serial.port.utils.HexConverter
import com.serial.port.utils.SendByteData


class SerialPortCore {

    /***
     * 格口阻力值
     */
    private val rodHinder: MutableMap<Int, ByteArray> = mutableMapOf(
        //格口一
        CmdCode.GE1 to byteArrayOf(0x01),
        //格口二
        CmdCode.GE2 to byteArrayOf(0x02),

        )

    /***
     * 启动格口开关
     */
    private val startDoor: MutableMap<Int, ByteArray> = mutableMapOf(
        //格口一
        CmdCode.GE11 to byteArrayOf(0x01, 0x01),//开
        CmdCode.GE10 to byteArrayOf(0x01, 0x00),//关
        CmdCode.GE12 to byteArrayOf(0x01, 0x02),//关
        //格口二
        CmdCode.GE21 to byteArrayOf(0x02, 0x01),//开
        CmdCode.GE20 to byteArrayOf(0x02, 0x00),//关
        CmdCode.GE22 to byteArrayOf(0x02, 0x02),//关

    )

    /***
     * 启动格口状态查询
     */
    private val startDoorStatus: MutableMap<Int, ByteArray> = mutableMapOf(
        //格口一
        CmdCode.GE1 to byteArrayOf(0x01, 0x01),
        //格口二
        CmdCode.GE2 to byteArrayOf(0x02, 0x02),

        )

    /***
     * 启动清运门
     */
    private val clearDoor: MutableMap<Int, ByteArray> = mutableMapOf(
        //清运门一开启
        CLEAR_OPEN_1_1 to byteArrayOf(0x01, 0x01),
        //清运门二开启
        CLEAR_OPEN_2_1 to byteArrayOf(0x02, 0x01),

        //清运门一查询
        CLEAR_QUERY_1_0 to byteArrayOf(0x01, 0x00),
        //清运门二查询
        CLEAR_QUERY_2_0 to byteArrayOf(0x02, 0x00),
    )

    /***
     * 查询重量
     */
    private val weightDoor: MutableMap<Int, ByteArray> = mutableMapOf(
        //格口一
        CmdCode.GE1 to byteArrayOf(0x01, 0x01),
        //格口二
        CmdCode.GE2 to byteArrayOf(0x02, 0x01),
    )

    /***
     * 设备状态
     */
    private val deviceStatus: MutableMap<Int, ByteArray> = mutableMapOf(
        //格口一
        CmdCode.GE_DEVICE_STATUS to byteArrayOf(0x01, 0x01),
    )

    /***
     * 灯光
     */
    private val inOutLights: MutableMap<Int, ByteArray> = mutableMapOf(
        //内部灯打开
        CmdCode.IN_LIGHTS_OPEN to byteArrayOf(0x01, 0x01),
        //内部灯关闭
        CmdCode.IN_LIGHTS_CLOSE to byteArrayOf(0x01, 0x00),
        //外部灯打开
        CmdCode.OUT_LIGHTS_OPEN to byteArrayOf(0x02, 0x01),
        //外部灯关闭
        CmdCode.OUT_LIGHTS_CLOSE to byteArrayOf(0x02, 0x00),
    )

    /***
     * 校准
     */
    private val calibration: MutableMap<Int, ByteArray> = mutableMapOf(
        //清皮去零
        CmdCode.CALIBRATION_0 to byteArrayOf(0x01),
        //零点校准
        CmdCode.CALIBRATION_1 to byteArrayOf(0x01),
        //校准2KG
        CmdCode.CALIBRATION_2 to byteArrayOf(0x02),
        //校准25KG
        CmdCode.CALIBRATION_3 to byteArrayOf(0x03),
        //校准100KG
        CmdCode.CALIBRATION_4 to byteArrayOf(0x04),
        //校准板框重量
        CmdCode.CALIBRATION_5 to byteArrayOf(0x05),
    )

    /***
     * 设置阻力值
     */
    @Synchronized
    fun turnRodHinder(code: Int, number: Int, turnRodHinderCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandRodHinderResultListener { number, rodHinderValue ->
            turnRodHinderCallback(number, rodHinderValue)
        }
        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }
        rodHinder[code]?.let {
            val command = 0x13.toByte()
            val data = it
            val data2 = HexConverter.intToByteArray(number)
            val sendByte = HexConverter.combineByteArrays(data, data2)
            //            val byte = SendByteData.createSendNotCheckSumByte(command, data)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), sendByte)
            SerialPortManager.instance.issuedStatus(byte)

        }
    }

    /***
     * 启动格口开关
     */
    @Synchronized
    fun turnDoor(code: Int, turnDoorCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandTurnResultListener { number, status ->
            turnDoorCallback(number, status)
        }
        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }
        startDoor[code]?.let {
            val command = 0x01.toByte()
            val data = it
//            val byte = SendByteData.createSendNotCheckSumByte(command, data)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
            SerialPortManager.instance.issuedStatus(byte)

        }
    }

    /***
     * 启动格口状态查询
     * @param code 仓位
     * @param doorStatusCallback 下发指令接收反馈信息
     * @param sendCallback 下发指令是否成功回调
     */
    @Synchronized
    fun turnDoorStatus(code: Int, doorStatusCallback: (Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandDoorResultListener { status ->
            doorStatusCallback(status)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }

        startDoorStatus[code]?.let {
            val command = 0x02.toByte()
            val data = it
//            val byte = SendByteData.createSendNotCheckSumByte(command, data)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
            SerialPortManager.instance.issuedStatus(byte)
        }
    }

    /***
     * @param code 仓位
     * @param openCallback 下发指令接收反馈信息
     * @param sendCallback 下发指令是否成功回调
     */
    @Synchronized
    fun openClear(code: Int, openCallback: (Int, Int, Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandOpenResultListener { number, status, type ->
            openCallback(number, status, type)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }

        clearDoor[code]?.let {
            val command = 0x03.toByte()
            val data = it
//            val byte = SendByteData.createSendNotCheckSumByte(command, data)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
            SerialPortManager.instance.issuedOpen(code, byte)
        }
    }

    /***
     * @param code 仓位
     * @param openCallback 下发指令接收反馈信息
     * @param sendCallback 下发指令是否成功回调
     */
    @Synchronized
    fun queryClear(code: Int, openCallback: (Int, Int, Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandQueryResultListener { number, status, type ->
            openCallback(number, status, type)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }

        clearDoor[code]?.let {
            val command = 0x03.toByte()
            val data = it
//            val byte = SendByteData.createSendNotCheckSumByte(command, data)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
            SerialPortManager.instance.issuedStatus(byte)
        }
    }

    /***
     * 启动格口重量
     */
    @Synchronized
    fun queryWeight(code: Int, weightCallback: (Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandWeightResultListener { weight ->
            weightCallback(weight)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }

        weightDoor[code]?.let {
            val command = 0x04.toByte()
            val data = it
//            val byte = SendByteData.createSendNotCheckSumByte(command, data)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
            SerialPortManager.instance.issuedStatus(byte)

        }
    }

    /***
     * 查询设备状态
     */
    @Synchronized
    fun queryStatus(onBoxStatusCallback: (MutableList<PortDeviceInfo>) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandQueryListResultListener { result ->
            onBoxStatusCallback(result)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }
        deviceStatus[CmdCode.GE_DEVICE_STATUS]?.let {
            val command = 0x05.toByte()
            val data = it
//            val byte = SendByteData.createSendNotCheckSumByte(command, data)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
            SerialPortManager.instance.issuedStatus(byte)
        }

    }

    /***
     * 灯光操作
     */
    @Synchronized
    fun startLights(code: Int, lightsCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandLightsResultListener { number, status ->
            lightsCallback(number, status)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }

        inOutLights[code]?.let {
            val command = 0x06.toByte()
            val data = it
//            val byte = SendByteData.createSendNotCheckSumByte(command, data)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
            SerialPortManager.instance.issuedStatus(byte)

        }
    }

    /***
     * 去皮清零
     */
    @Synchronized
    fun startCalibrationQP(doorGeX: Int, code: Int, calibrationCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandCalibrationResultListener { number, status ->
            calibrationCallback(number, status)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }

        calibration[code]?.let {
            val command = 0x10.toByte()
            val data = it
            val data2 = when (doorGeX) {
                1 -> {
                    byteArrayOf(0x01.toByte())
                }

                2 -> {
                    byteArrayOf(0x02.toByte())
                }

                else -> {
                    byteArrayOf(0x01.toByte())
                }
            }
            val sendByte = HexConverter.combineByteArrays(data2, data)
//            val byte = SendByteData.createSendNotCheckSumByte(command, sendByte)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), sendByte)
            SerialPortManager.instance.issuedStatus(byte)

        }
    }

    /***
     * 校准操作
     */
    @Synchronized
    fun startCalibration(doorGeX: Int, code: Int, calibrationCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandCalibrationResultListener { number, status ->
            calibrationCallback(number, status)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }

        calibration[code]?.let {
            val command = 0x11.toByte()
            val data = it
            val data2 = when (doorGeX) {
                1 -> {
                    byteArrayOf(0x01.toByte())
                }

                2 -> {
                    byteArrayOf(0x02.toByte())
                }

                else -> {
                    byteArrayOf(0x01.toByte())
                }
            }
            val sendByte = HexConverter.combineByteArrays(data2, data)
//            val byte = SendByteData.createSendNotCheckSumByte(command, sendByte)
            val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), sendByte)
            SerialPortManager.instance.issuedStatus(byte)

        }
    }

    /***
     * 0x07.升级指令
     * 0x08.查询当前是否进入升级状态
     * 0x9.查询当前是否进入升级状态
     * 0x0A.重启指令
     * @param commandType 指令类型
     * @param onQueryVersion 返回固件是否执行下一步
     * @param sendCallback 下发指令是否成功回调
     */
    @Synchronized
    fun firmwareUpgrade78910(commandType: Int, data: ByteArray, onUpgrade: (status: Int) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandUpgrade232ResultListener { status ->
            onUpgrade(status)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }

        when (commandType) {
            7 -> {
                val command = 0x07.toByte()
//                val sendByte = SendByteData.createSendNotCheckSumByte(command, data)
                val sendByte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
                SerialPortManager.instance.upgrade232(sendByte)
            }

            8 -> {
                val command = 0x08.toByte()
                val data2 = byteArrayOf(0xa1.toByte(), 0xa2.toByte(), 0xa3.toByte())
                val sendByte = HexConverter.combineByteArrays(data2, data)
//                val byte = SendByteData.createSendNotCheckSumByte(command, sendByte)
                val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), sendByte)
                SerialPortManager.instance.upgrade232(byte)
            }

            9 -> {
                val command = 0x09.toByte()
//                val byte = SendByteData.createSendNotCheckSumByte(command, data)
                val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
                SerialPortManager.instance.upgrade232(byte)
            }

            10 -> {
                val command = 0x0a.toByte()
//                val sendByte = SendByteData.createSendNotCheckSumByte(command, data)
                val sendByte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
                SerialPortManager.instance.upgrade232(sendByte)
            }

            11 -> {
                val command = 0x0b.toByte()
//                val sendByte = SendByteData.createSendNotCheckSumByte(command, data)
                val sendByte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), data)
                SerialPortManager.instance.upgrade232(sendByte)
            }
        }

    }

    /***
     * 发送文件来回效验
     * @param onUpgrade 下发指令接收反馈信息
     * @param sendCallback 下发指令是否成功回调
     */
    @Synchronized
    fun firmwareUpgradeFile(byte: ByteArray, onUpgrade: (bytes: ByteArray) -> Unit, sendCallback: (String) -> Unit) {
        SerialPortManager.instance.serialVM?.addCommandUpgradeXYResultListener { status ->
            onUpgrade(status)
        }

        SerialPortManager.instance.serialVM?.addSendCommandStatusListener { msg ->
            sendCallback(msg)
        }
        val command = 0x12.toByte()
//        val byte = SendByteData.createSendNotCheckSumByte(command, byte)
        val byte = SendByteData.createSendCheckSumByte(command, 0x00.toByte(), byte)
        SerialPortManager.instance.upgrade232(byte)
    }

}