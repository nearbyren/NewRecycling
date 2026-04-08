package com.serial.port

import java.io.File

class CabinetSdk private constructor() {

    companion object {

        private var isInit = false
        private var serialPortCore: SerialPortCore? = null

        /***
         * genymotion  ttyS1
         * 平板 232 ttyS2
         * 平板 485 ttyS3
         */
        fun init() {
            if (isInit) return
            isInit = true
            //串口232 genymotion模拟器配置 ttyS1 ttyS2  真实环境 ttys2 ttys3 说明：匹配oracle VM virtualBox 串口编号（N）COM2  路径/地址COM3映射的PC串口COM3
            val sdk: ConfigurationSdk =
                    ConfigurationSdk.ConfigurationBuilder(File("/dev/ttyS0"), 115200).log("TAG", true, false).build()
            serialPortCore = SerialPortCore()
            SerialPortManager.instance.init(sdk)
        }

        /***
         * 释放资源
         */
        fun release() {
            serialPortCore = null
            isInit = false
            SerialPortManager.instance.closeAllSerialPort()
        }

        /***
         * 设置阻力值
         * @param code
         * @param turnRodHinderCallback
         * @param sendCallback 发送是否成功
         */
        @Synchronized
        fun turnRodHinder(code: Int, number: Int, turnRodHinderCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.turnRodHinder(code, number, turnRodHinderCallback, sendCallback)
        }

        /***
         * 启动投口
         * @param code
         * @param turnDoorCallback
         * @param sendCallback 发送是否成功
         */
        @Synchronized
        fun turnDoor(code: Int, turnDoorCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.turnDoor(code, turnDoorCallback, sendCallback)
        }

        /***
         * 启动投口状态查询
         * @param code
         * @param onDoorStatus
         * @param sendCallback 发送是否成功
         */
        @Synchronized
        fun turnDoorStatus(code: Int, onDoorStatus: (status: Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.turnDoorStatus(code, onDoorStatus, sendCallback)
        }

        /***
         * 清运门开启
         * @param code 类型
         * @param onOpenStatus
         * @param sendCallback
         */
        @Synchronized
        fun openClear(code: Int, onOpenStatus: (boxCode: Int, status: Int, type: Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.openClear(code, onOpenStatus, sendCallback)
        }

        /***
         * 清运门查询
         * @param code 类型
         * @param onOpenStatus
         * @param sendCallback
         */
        @Synchronized
        fun queryClear(code: Int, onOpenStatus: (boxCode: Int, status: Int, type: Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.queryClear(code, onOpenStatus, sendCallback)
        }

        /***
         * 启动查询投口重量
         * @param weightCallback
         * @param sendCallback 发送是否成功
         */
        @Synchronized
        fun queryWeight(boxCode: Int, weightCallback: (Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.queryWeight(boxCode, weightCallback, sendCallback)
        }

        /***
         * 启动查询当前设备状态
         * @param onBoxStatus
         * @param sendCallback 发送是否成功
         */
        @Synchronized
        fun queryStatus(onBoxStatus: (lowerMachines: MutableList<PortDeviceInfo>) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.queryStatus(onBoxStatus, sendCallback)
        }

        /***
         *  启动灯光控制
         * @param lightsCallback
         * @param sendCallback 发送是否成功
         */
        @Synchronized
        fun startLights(inOut: Int, lightsCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.startLights(inOut, lightsCallback, sendCallback)
        }

        /***
         *  去皮清零
         * @param calibrationCallback
         * @param doorGeX 格口x
         * @param code 指令
         * @param sendCallback 发送是否成功 校准
         */
        @Synchronized
        fun startCalibrationQP(doorGeX: Int, code: Int, calibrationCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.startCalibrationQP(doorGeX, code, calibrationCallback, sendCallback)
        }

        /***
         *  校准操作
         * @param calibrationCallback
         * @param doorGeX 格口x
         * @param code 指令
         * @param sendCallback 发送是否成功 校准
         */
        @Synchronized
        fun startCalibration(doorGeX: Int, code: Int, calibrationCallback: (Int, Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.startCalibration(doorGeX, code, calibrationCallback, sendCallback)
        }

        /***
         * 0x07.升级指令
         * 0x08.查询当前是否进入升级状态
         * 0x9.查询当前是否进入升级状态
         * 0x0A.重启指令
         * @param byte
         * @param onUpgrade 返回固件是否执行下一步
         * @param sendCallback 发送是否成功
         */
        fun firmwareUpgrade78910(commandType: Int, byte: ByteArray, onUpgrade: (status: Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.firmwareUpgrade78910(commandType, byte, onUpgrade, sendCallback)
        }

        /***
         * 发送文件来回效验
         * @param byte
         * @param onUpgrade 返回开仓是否成功
         * @param sendCallback 发送是否成功
         */
        fun firmwareUpgradeFile(byte: ByteArray, onUpgrade: (bytes: ByteArray) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.firmwareUpgradeFile(byte, onUpgrade, sendCallback)
        }

        /***
         * 芯片版本查询
         * @param byte
         * @param onUpgrade 返回开仓是否成功
         * @param sendCallback 发送是否成功
         */
        fun queryVersion(commandType: Int, byte: ByteArray, onUpgrade: (status: Int) -> Unit, sendCallback: (String) -> Unit) {
            serialPortCore?.firmwareUpgrade78910(commandType, byte, onUpgrade, sendCallback)
        }
    }
}
