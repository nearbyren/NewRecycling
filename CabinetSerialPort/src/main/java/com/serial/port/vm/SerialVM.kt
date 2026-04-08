package com.serial.port.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.serial.port.PortDeviceInfo
import com.serial.port.call.CommandCalibrationResultListener
import com.serial.port.call.CommandDoorResultListener
import com.serial.port.call.CommandLightsResultListener
import com.serial.port.call.CommandOpenResultListener
import com.serial.port.call.CommandQueryListResultListener
import com.serial.port.call.CommandQueryResultListener
import com.serial.port.call.CommandReportResultListener
import com.serial.port.call.CommandRodHinderResultListener
import com.serial.port.call.CommandSendResultListener
import com.serial.port.call.CommandStatus
import com.serial.port.call.CommandTurnResultListener
import com.serial.port.call.CommandUpgrade232ResultListener
import com.serial.port.call.CommandUpgradeXYResultListener
import com.serial.port.call.CommandWeightResultListener
import com.serial.port.call.DoorStatus
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.HexConverter
import com.serial.port.utils.Loge
import com.serial.port.utils.SendByteData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class SerialVM : ViewModel() {
    /*******************基础协程和变量************************************************/

    //串口232描述文件
    val fd232 = MutableStateFlow<FileDescriptor?>(null)
    private val fileDes232: StateFlow<FileDescriptor?> = fd232

    //接收
    private val fis232 = MutableStateFlow<FileInputStream?>(null)
    private val fis232Read: StateFlow<FileInputStream?> = fis232

    //发送
    private val fos232 = MutableStateFlow<FileOutputStream?>(null)
    private val fosSend232: StateFlow<FileOutputStream?> = fos232

    /**
     * 用于处理 I/O 操作的协程作用域
     */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /***
     * 用于处理 默认协程作用域
     */
    private val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 开启发送消息的协程 232
     */
    private var sending232Job: Job? = null

    /**
     * 开启发送消息的协程 232状态查询
     */
    private var sendingStatus232Job: Job? = null

    /**
     * 开启接收消息的协程 232
     */
    private var read232Job: Job? = null

    /**
     * 重试次数
     */
    private val maxRetryCount = 4

    /**
     * 重试次当前重试次数数
     */
    private var retryCount = 0

    /***
     * 创建一个互斥锁
     */
    private val mutex = Mutex()

    /**
     * 线程安全的状态变量 接收数据
     */
    private val isOpenRecData = AtomicBoolean(false)

    /**
     * 线程安全的状态变量 接收到数据并且是开锁成功
     */
    private var isOpenStatus = AtomicBoolean(false)

    /**
     * 线程安全的状态变量 接收到数据并且是开锁成
     */
    private var isOpenStatusType = AtomicInteger(-1)

    /**
     * 线程安全的状态变量 接收数据
     */
    private val isUpgradeRecData = AtomicBoolean(false)

    /**
     * 线程安全的状态变量 接收到数据并且是开锁成功
     */
    private var isUpgradeStatus = AtomicBoolean(false)

    /*******************基础协程和变量************************************************/

    /******************************************响应回调方法************************************************/
    /***
     * 发送指令是否成功响应结果
     */
    private var commandSendResultListener: CommandSendResultListener? = null
    fun addSendCommandStatusListener(callback: (String) -> Unit) {
        // 使用 lambda 表达式作为回调
        this.commandSendResultListener = CommandSendResultListener { msg ->
            // 调用传递的回调
            callback(msg)
        }
    }

    /***
     * 所有仓查询指令发送响应结果
     */
    private var commandQueryListResultListener: CommandQueryListResultListener? = null
    fun addCommandQueryListResultListener(callback: (MutableList<PortDeviceInfo>) -> Unit) {
        // 使用 lambda 表达式作为回调
        this.commandQueryListResultListener = CommandQueryListResultListener { lockerInfos ->
            // 调用传递的回调
            callback(lockerInfos)
        }
    }

    /***
     *
     *
     * 固件升级指令发送响应结果
     */
    private var commandUpgrade232ResultListener: CommandUpgrade232ResultListener? = null
    fun addCommandUpgrade232ResultListener(callback: (Int) -> Unit) {
        // 使用 lambda 表达式作为回调
        this.commandUpgrade232ResultListener = CommandUpgrade232ResultListener { status ->
            // 调用传递的回调
            callback(status)
        }
    }

    /***
     *
     *
     * 固件文件效验
     */
    private var commandUpgradeXYResultListener: CommandUpgradeXYResultListener? = null
    fun addCommandUpgradeXYResultListener(callback: (bytes: ByteArray) -> Unit) {
        // 使用 lambda 表达式作为回调
        this.commandUpgradeXYResultListener = CommandUpgradeXYResultListener { status ->
            // 调用传递的回调
            callback(status)
        }
    }

    /***
     *
     * 开仓指令发送响应结果 清运门
     */
    private var commandOpenResultListener: CommandOpenResultListener? = null
    fun addCommandOpenResultListener(callback: (Int, Int, Int) -> Unit) {
        // 使用 lambda 表达式作为回调
        this.commandOpenResultListener = CommandOpenResultListener { number, status, type ->
            // 调用传递的回调
            callback(number, status, type)
        }
    }

    private var commandQueryResultListener: CommandQueryResultListener? = null
    fun addCommandQueryResultListener(callback: (Int, Int, Int) -> Unit) {
        // 使用 lambda 表达式作为回调
        this.commandQueryResultListener = CommandQueryResultListener { number, status, type ->
            // 调用传递的回调
            callback(number, status, type)
        }
    }


    /***
     *
     *设置阻力值
     */
    private var commandRodHinderResultListener: CommandRodHinderResultListener? = null
    fun addCommandRodHinderResultListener(callback: (Int, Int) -> Unit) {
        this.commandRodHinderResultListener = CommandRodHinderResultListener { number, status ->
            // 调用传递的回调
            callback(number, status)
        }
    }

    /***
     *
     *发起门操作
     */
    private var commandTurnResultListener: CommandTurnResultListener? = null
    fun addCommandTurnResultListener(callback: (Int, Int) -> Unit) {
        this.commandTurnResultListener = CommandTurnResultListener { number, status ->
            // 调用传递的回调
            callback(number, status)
        }
    }

    /***
     *
     *查询当前重量
     */
    private var commandWeightResultListener: CommandWeightResultListener? = null
    fun addCommandWeightResultListener(callback: (Int) -> Unit) {
        this.commandWeightResultListener = CommandWeightResultListener { weight ->
            // 调用传递的回调
            callback(weight)
        }
    }

    /***
     *
     * 灯光
     */
    private var commandLightsResultListener: CommandLightsResultListener? = null
    fun addCommandLightsResultListener(callback: (Int, Int) -> Unit) {
        this.commandLightsResultListener = CommandLightsResultListener { number, status ->
            // 调用传递的回调
            callback(number, status)
        }
    }

    /***
     *
     * 校准
     */
    private var commandCalibrationResultListener: CommandCalibrationResultListener? = null
    fun addCommandCalibrationResultListener(callback: (Int, Int) -> Unit) {
        this.commandCalibrationResultListener = CommandCalibrationResultListener { number, status ->
            // 调用传递的回调
            callback(number, status)
        }
    }

    /***
     *
     *查询门状态
     */
    private var commandDoorResultListener: CommandDoorResultListener? = null
    fun addCommandDoorResultListener(callback: (Int) -> Unit) {
        this.commandDoorResultListener = CommandDoorResultListener { status ->
            // 调用传递的回调
            callback(status)
        }
    }


    private var commandReportResultListener: CommandReportResultListener? = null
    fun addCommandReportResultListener(callback: (Int) -> Unit) {
        // 使用 lambda 表达式作为回调
        this.commandReportResultListener = CommandReportResultListener { status ->
            // 调用传递的回调
            callback(status)
        }
    }

    /******************************************响应回调方法************************************************/

    /******************************************获取串口描述符和输入输出流************************************************/
    fun initCollect() {
        viewModelScope.launch {
            fileDes232.collect { file ->
                //获取串口文件描述符成功
                file?.let {
                    Loge.i("串口232", "接232 获取串口文件描述完成开启构建读写流232注册")
                    fis232.value = FileInputStream(file)
                    fos232.value = FileOutputStream(file)
                } ?: run {
                    Loge.i("串口232", "接232 获取串口文件描述完成开启构建读写流232注销")
                }
            }
        }
        viewModelScope.launch {
            fis232Read.collect { c ->
                c?.apply {
                    Loge.i("串口232", "接232 构建读取流232注册")
                    startRead232JobNew()
                } ?: run {
                    stopRead232Job()
                    Loge.i("串口232", "接232 构建读取流流232注销")
                }
            }
        }

        viewModelScope.launch {
            fosSend232.collect { c ->
                c?.apply {
                    Loge.i("串口232", "接232 构建写入流232注册")
                } ?: run {
                    stopSend232Job()
                    Loge.i("串口232", "接232 构建写入流232注销")
                }
            }
        }
    }
    /******************************************获取串口描述符和输入输出流************************************************/

    /******************************************发送指令**************************************************************/

    /***
     * 升级指令
     * @param sendBytes
     */
    fun upgrade232(lockerId: Int, sendBytes: ByteArray) {
        ioScope.launch {
            mutex.withLock {
                startSend232Job(lockerId, -1, sendBytes)
            }
        }
    }

    /***
     * 开仓指令
     * @param sendBytes
     */
    fun open(lockerId: Int, sendBytes: ByteArray) {
        ioScope.launch {
            mutex.withLock {
                retryJobS(lockerId, -1, sendBytes)
            }
        }
    }

    /***
     * 故障指令
     * @param sendBytes
     */
    fun fault(lockerId: Int, sendBytes: ByteArray) {
        ioScope.launch {
            mutex.withLock {
                startSend232Job(lockerId, -1, sendBytes)
            }
        }
    }

    /***
     *查询状态指令
     * @param sendBytes
     */
    fun status(sendBytes: ByteArray) {
        ioScope.launch {
            mutex.withLock {
                startSendStatus232Job(sendBytes)
            }
        }
    }

    /******************************************发送指令**************************************************************/

    /******************************************发送类型指令**************************************************************/

    /***
     * 重试开仓
     */
    private suspend fun retryJobS(boxCode: Int, type: Int, sendBytes: ByteArray) {
        Loge.i("串口232", "接232 retryJobS 指令：${ByteUtils.toHexString(sendBytes)} 仓：$boxCode, ${if (type == 1) "发送开仓指令" else "发送关仓指令"}")
        while (retryCount < maxRetryCount) { // 在未达到最大重试次数且 isCan 为 false 时继续重试
            retryCount++
            val rec = isOpenRecData.get()
            val sta = isOpenStatus.get()
//            Loge.i("串口232","接232 retryJobS 尝试第 $retryCount 次 $lockerId 仓 指令：${ByteUtils.toHexString(sendBytes)}, ${if (type == 1) "发起开仓" else "发起关仓"} 读取数据：$rec,状态：$sta")
            Loge.i("串口232", "接232 retryJobS 尝试第 $retryCount 次 $boxCode 仓 指令：${ByteUtils.toHexString(sendBytes)}, 发起开仓 读取数据：$rec,状态：$sta")
            if (rec && sta) {
                Loge.i("串口232", "接232 retryJobS 接收到数据 任务成功，停止重试")
                isOpenRecData.set(false)
                isOpenStatus.set(false)
                when (type) {
                    0 -> {
                        //响应关仓
                    }

                    1 -> {
                        //响应开仓
                    }

                    else -> {

                    }
                }
                retryCount = 0
                commandOpenResultListener?.openResult(boxCode, CommandStatus.SUCCEED, 1)
                break
            }
            if (retryCount >= maxRetryCount) {
                Loge.i("串口232", "接232 retryJobS 达到最大重试次数，停止重试")
                isOpenRecData.set(false)
                isOpenStatus.set(false)
                when (type) {
                    0 -> {
                        //响应关仓
                    }

                    1 -> {
                        //响应开仓
                    }

                    else -> {

                    }
                }
                retryCount = 0
                commandOpenResultListener?.openResult(boxCode, CommandStatus.FAULT, 1)
                break
            }
            startSend232Job(boxCode, type, sendBytes)
            delay(1000)
        }
    }

    /***
     * 启动发送消息协程任务
     */
    private fun startSend232Job(lockerId: Int, type: Int, sendBytes: ByteArray) {
        // 启动协程，处理发送消息的任务
        sending232Job = ioScope.launch {
            // 保持协程任务处于活动状态
            if (isActive) {
                fosSend232.value?.apply {
                    write(sendBytes)
                    flush()
                    commandSendResultListener?.sendResult("发送数据成功232：|${ByteUtils.toHexString(sendBytes)}|")
                } ?: run {
                    commandSendResultListener?.sendResult("发送数据失败232：串口未打开 |${ByteUtils.toHexString(sendBytes)}|")
                }
            } else {
                commandSendResultListener?.sendResult("发送数据失败232：协程出现问题... |${ByteUtils.toHexString(sendBytes)}|")
            }
        }
    }

    /***
     * 发送定心查询状态232指令
     * @param sendBytes
     */
    private fun startSendStatus232Job(sendBytes: ByteArray) {
        // 启动协程，处理发送消息的任务
        sendingStatus232Job = ioScope.launch {
            // 保持协程任务处于活动状态
            if (isActive) {
                fosSend232.value?.apply {
                    write(sendBytes)
                    flush()
                    commandSendResultListener?.sendResult("发送数据成功232：|${ByteUtils.toHexString(sendBytes)}|")
//                    println("wo来了啊  send ${ByteUtils.toHexString(sendBytes)}")
                } ?: run {
                    commandSendResultListener?.sendResult("发送数据失败232：串口未打开 |${ByteUtils.toHexString(sendBytes)}|")
                    //模拟串口返回的数据
//                    val result = openAssetsJson()
//                    cabinet2StatusListener?.lockerStatusArray(result, false)
                }
            } else {
                commandSendResultListener?.sendResult("发送数据失败232：协程出现问题... |${ByteUtils.toHexString(sendBytes)}|")
                //模拟串口返回的数据
//                val result = openAssetsJson()
//                cabinet2StatusListener?.lockerStatusArray(result, false)
            }
        }
    }

    /******************************************发送类型指令**************************************************************/

    /***********************************************************针对自定义协议V1.0***************************************************************************/

    // 用于缓存数据的缓存区
    private val buffer232 = mutableListOf<Byte>()

    /***
     * 指令位 2
     */
    val CMD_POS = 2

    /***
     * 取出校验码位 2
     */
    val CHECK_POS_DATA = 2

    /***
     * 取出数据域位生成效验位 3
     */
    val CHECK_POS = 3

    /***
     * 取出数据域位 3
     */
    val DATA_POS_LENGTH = 3

    /***
     * 前四位 4
     */
    val BEFORE_FOUR_POS = 4

    /***
     * 完整包 6
     */
    val COMPLETE_PACKAGE = 6

    /***
     * 缓冲区管理
     */
    private val bufferNew232 = ByteArrayOutputStream(1024)

    /***
     * @param frameStartIndex 通过 IndexOf 函数获取帧的位置
     * 查找帧尾的位置，从给定的帧头位置之后开始查找
     */
    private fun findFrameEndIndex(frameStartIndex: Int): Int {
        for (i in frameStartIndex + 1 until buffer232.size) {
            if (buffer232[i] == SendByteData.RE_FRAME_END) {
                return i  // 找到帧尾的位置
            }
        }
        return -1  // 如果没有找到帧尾，返回 -1
    }

    /***
     * 完整数据处理业务
     * @param packet 下位机原始数据
     */
    private fun handlePacket232(packet: ByteArray) {
        //此处进来的数据是没有帧尾 9B 00 0B 04 FF FF FF FF A6
        Loge.i("串口232", "接232 handlePacket232 处理数据 size ${packet.size} | ${ByteUtils.toHexString(packet)}")
        if (packet.size < 4) return
        //指令位置
        val seek = CMD_POS
        //数据长度位置
        val length = DATA_POS_LENGTH
        //提取指令
        val command = packet[seek]
        var dataLength = -1
        when (command) {
            0.toByte(), 1.toByte(), 2.toByte(), 3.toByte(), 4.toByte(), 5.toByte(), 6.toByte(), 7.toByte(), 8.toByte(), 9.toByte(), 10.toByte(), 11.toByte(), 16.toByte(), 17.toByte(), 18.toByte(), 19.toByte() -> {
                // 提取数据长度，并将其转换为无符号整数
                dataLength = packet[length].toUByte().toInt()  // 将有符号字节转换为无符号整数
            }

//            2.toByte() -> {
//                val highByte = packet[3]
//                val lowByte = packet[4]
//                Loge.i("串口232","接232 toByte highByte = $highByte lowByte = $lowByte ")
//                dataLength = (highByte.toInt() shl 8) or (lowByte.toInt() and 0xFF)
//
//            }
        }
        val before = packet.size - CHECK_POS_DATA
        Loge.i("串口232", "接232 0.toByte 排除帧尾长度：$before 数据域长度：$dataLength")
        when (command) {
            //启动格口开关
            1.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 1.toByte 数据长度与数据域不匹配")
                    commandTurnResultListener?.openResult(-1, DoorStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 1.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                for (i in data.indices step 2) {
                    val end = (i + 2).coerceAtMost(data.size)
                    val group = data.copyOfRange(i, end)
                    val size = group.size
                    Loge.i("串口232", "接232 1.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    val locker = group[0].toInt()
                    val status = group[1].toInt()
                    if (status == 1) {
                        commandTurnResultListener?.openResult(locker, DoorStatus.SUCCEED)

                    } else if (status == 2) {
                        commandTurnResultListener?.openResult(locker, DoorStatus.ING)
                    } else if (status == 0) {
                        commandTurnResultListener?.openResult(locker, DoorStatus.FAIL)
                    } else {
                        commandTurnResultListener?.openResult(locker, DoorStatus.FAULT)
                    }
                }
            }
            //启动格口状态查询
            2.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 2.toByte 数据长度与数据域不匹配")
                    commandDoorResultListener?.openResult(DoorStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 2.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                for (i in data.indices step 2) {
                    val end = (i + 2).coerceAtMost(data.size)
                    val group = data.copyOfRange(i, end)
                    val size = group.size
                    Loge.i("串口232", "接232 2.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    val locker = group[0].toInt()
                    val status = group[1].toInt()
                    if (status == 1) {
                        commandDoorResultListener?.openResult(DoorStatus.SUCCEED)
                    } else if (status == 2) {
                        commandDoorResultListener?.openResult(DoorStatus.ING)
                    } else if (status == 0) {
                        commandDoorResultListener?.openResult(DoorStatus.FAIL)
                    } else {
                        commandDoorResultListener?.openResult(DoorStatus.FAULT)
                    }
                }
            }
            //清运门
            3.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 3.toByte 数据长度与数据域不匹配")
                    commandOpenResultListener?.openResult(-1, CommandStatus.FAIL, 1)
                    commandQueryResultListener?.openResult(-1, CommandStatus.FAIL, 0)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 3.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                isOpenRecData.set(true)
                for (i in data.indices step 3) {
                    val end = (i + 3).coerceAtMost(data.size)
                    val group = data.copyOfRange(i, end)
                    val size = group.size
                    Loge.i("串口232", "接232 3.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    val locker = group[0].toInt()
                    val status = group[1].toInt()
                    when (val type = group[2].toInt()) {
                        0 -> {
                            commandQueryResultListener?.openResult(locker, status, 0)
                        }

                        1 -> {
                            isOpenStatusType.set(type)
                            if (status == 1) {
                                isOpenStatus.set(true)
                            } else {
                                isOpenStatus.set(false)
                            }
                        }
                    }
                    Loge.i("串口232", "接232 3.toByte -----------------------------------------------------------")
                }

            }
            //查询重量
            4.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 4.toByte 数据长度与数据域不匹配")
                    commandWeightResultListener?.weightResult(CommandStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 4.toByte size = ${data.size} | ${HexConverter.byteArrayToInt(data)}")
                if (data.isNotEmpty()) {
                    for (i in data.indices step 4) {
                        val end = (i + 4).coerceAtMost(data.size)
                        val group = data.copyOfRange(i, end)
                        val size = group.size
                        Loge.i("串口232", "接232 4.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    }
                    val size = HexConverter.byteArrayToInt(data)
                    Loge.i("串口232", "接232 4.toByte 重量 $size")
                    commandWeightResultListener?.weightResult(size)
                } else {
                    commandWeightResultListener?.weightResult(CommandStatus.FAIL)
                }
            }
            //查询当前设备状态
            5.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 5.toByte 数据长度与数据域不匹配")
                    commandQueryListResultListener?.queryResult(arrayListOf())
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 5.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                val list = mutableListOf<PortDeviceInfo>()

                val tg1 = data.copyOfRange(0, 14)
                Loge.i("串口232", "接232 测试 1 ${ByteUtils.toHexString(tg1)}")
                val weight1 = tg1.copyOfRange(1, 5)
                Loge.i("串口232", "接232 5.toByte 取1前${ByteUtils.toHexString(weight1)}|取1重量：${HexConverter.byteArrayToInt(weight1)}")
                val status1 = tg1.copyOfRange(5, 14)
                //烟雾传感器
                var smokeValue1 = 1
                //红外传感器
                var irStateValue1 = -1
                //关门传感器
                var touCGStatusValue1 = 0
                //防夹传感器
                var touJSStatusValue1 = 0
                //投口门状态
                var doorStatusValue1: Int = -1
                //清运门状态
                var lockStatusValue1: Int = -1
                //校准状态
                var xzStatusValue1: Int = -1
                //是否夹手
                var jsStatusValue1: Int = -1
                for (i in status1.indices step 9) {
                    val end = (i + 9).coerceAtMost(status1.size)
                    val group = status1.copyOfRange(i, end)
                    val size = group.size
                    smokeValue1 = group[0].toUByte().toInt()
                    irStateValue1 = group[1].toUByte().toInt()
                    touCGStatusValue1 = group[2].toUByte().toInt()
                    touJSStatusValue1 = group[3].toUByte().toInt()
                    doorStatusValue1 = group[4].toUByte().toInt()
                    lockStatusValue1 = group[5].toUByte().toInt()
                    xzStatusValue1 = group[6].toUByte().toInt()
                    jsStatusValue1 = group[7].toUByte().toInt()
                    Loge.i("串口232", "接232 5.toByte 取1数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)} 重1：${HexConverter.byteArrayToInt(weight1)}| 烟：${smokeValue1}|红：${irStateValue1}|防：${touJSStatusValue1}|投：${doorStatusValue1}|清：${lockStatusValue1}|程序：${xzStatusValue1}|夹手：${jsStatusValue1}")
                }
                val weighValue1 = HexConverter.byteArrayToInt(weight1)
                list.add(PortDeviceInfo().apply {
                    weigh = HexConverter.getWeight(weighValue1)
//                    weigh = weighValue1.toString()
                    smoke = smokeValue1
                    irState = irStateValue1
                    touGMStatus = touCGStatusValue1
                    touJSStatus = touJSStatusValue1
                    doorStatus = doorStatusValue1
                    lockStatus = lockStatusValue1
                    runStatus = xzStatusValue1
                    jsStatus = jsStatusValue1
                })

//
                val tg2 = data.copyOfRange(14, 28)
                Loge.i("串口232", "接232 测试 2 ${ByteUtils.toHexString(tg2)}")
                val weight2 = tg2.copyOfRange(1, 5)
                Loge.i("串口232", "接232 5.toByte 取2前${ByteUtils.toHexString(weight2)}|取2重量：${HexConverter.byteArrayToInt(weight2)}")
                val status2 = tg2.copyOfRange(5, 14)
                //烟雾传感器
                var smokeValue2 = 1
                //红外传感器
                var irStateValue2 = -1
                //关门传感器
                var touCGStatusValue2 = 0
                //防夹传感器
                var touJSStatusValue2 = 0
                //投口门状态
                var doorStatusValue2: Int = -1
                //清运门状态
                var lockStatusValue2: Int = -1
                //校准状态
                var xzStatusValue2: Int = -1
                //是否夹手
                var jsStatusValue2: Int = -1
                for (i in status2.indices step 9) {
                    val end = (i + 9).coerceAtMost(status2.size)
                    val group = status2.copyOfRange(i, end)
                    val size = group.size
                    smokeValue2 = group[0].toUByte().toInt()
                    irStateValue2 = group[1].toUByte().toInt()
                    touCGStatusValue2 = group[2].toUByte().toInt()
                    touJSStatusValue2 = group[3].toUByte().toInt()
                    doorStatusValue2 = group[4].toUByte().toInt()
                    lockStatusValue2 = group[5].toUByte().toInt()
                    xzStatusValue2 = group[6].toUByte().toInt()
                    jsStatusValue2 = group[7].toUByte().toInt()
                    Loge.i("串口232", "接232 5.toByte 取2数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)} 重2：${HexConverter.byteArrayToInt(weight2)}| 烟：${smokeValue2}|红：${irStateValue2}|防：${touJSStatusValue2}|投：${doorStatusValue2}|清：${lockStatusValue2}|程序：${xzStatusValue2}|夹手：${jsStatusValue2}")

                }
                val weighValue2 = HexConverter.byteArrayToInt(weight2)
                list.add(PortDeviceInfo().apply {
                    weigh = HexConverter.getWeight(weighValue2)
//                    weigh = weighValue2.toString()
                    smoke = smokeValue2
                    irState = irStateValue2
                    touGMStatus = touCGStatusValue2
                    touJSStatus = touJSStatusValue2
                    doorStatus = doorStatusValue2
                    lockStatus = lockStatusValue2
                    runStatus = xzStatusValue2
                    jsStatus = jsStatusValue2
                })
                commandQueryListResultListener?.queryResult(list)

            }
            //灯光控制
            6.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 6.toByte 数据长度与数据域不匹配")
                    commandLightsResultListener?.lightsResult(-1, DoorStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 6.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                for (i in data.indices step 2) {
                    val end = (i + 2).coerceAtMost(data.size)
                    val group = data.copyOfRange(i, end)
                    val size = group.size
                    Loge.i("串口232", "接232 6.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    val locker = group[0].toInt()
                    val status = group[1].toInt()
                    if (status == 1) {
                        commandLightsResultListener?.lightsResult(locker, DoorStatus.SUCCEED)
                    } else {
                        commandLightsResultListener?.lightsResult(locker, DoorStatus.FAIL)
                    }
                }
            }
            //进入升级状态 查询状态 升级完成重启
            7.toByte(), 8.toByte(), 10.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 7910.toByte 数据长度与数据域不匹配")
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 7910.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                if (data.size == 3) {
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.SUCCEED)
                } else {
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                }
            }
            //查询升级校验结果
            9.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 9.toByte 数据长度与数据域不匹配")
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                var a = ""
                var b = ""
                var c = ""
                for (i in data.indices step 3) {
                    val end = (i + 3).coerceAtMost(data.size)
                    val group = data.copyOfRange(i, end)
                    val size = group.size
                    Loge.i("串口232", "接232 9.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    if (size > 0) {
                        a = group[0].toUByte().toString()
                    }
                    if (size > 1) {
                        b = group[1].toUByte().toString()
                    }
                    if (size > 2) {
                        c = group[2].toUByte().toString()
                    }
                }
                Loge.i("串口232", "接232 9.toByte ----------${a}-${b}-${c}-----------------------------------------------")
                if (a == "164" && b == "165" && c == "166") {
                    Loge.i("串口232", "接232 9.toByte 升级完成 ${CommandStatus.SUCCEED}")
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.SUCCEED)
                } else if (a == "180" && b == "181" && c == "182") {
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                } else {
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                }
            }
            //版本查询
            11.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 11.toByte 数据长度与数据域不匹配")
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 11.toByte size = ${data.size} | ${HexConverter.byteArrayToInt(data)}")
                if (data.isNotEmpty()) {
                    var a = ""
                    var b = ""
                    var c = ""
                    var d = ""
                    for (i in data.indices step 4) {
                        val end = (i + 4).coerceAtMost(data.size)
                        val group = data.copyOfRange(i, end)
                        val size = group.size
                        Loge.i("串口232", "接232 11.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                        if (size > 0) {
                            a = group[0].toUByte().toString()
                        }
                        if (size > 1) {
                            b = group[1].toUByte().toString()
                        }
                        if (size > 2) {
                            c = group[2].toUByte().toString()
                        }
                        if (size > 3) {
                            d = group[3].toUByte().toString()
                        }
                    }
                    Loge.i("串口232", "接232 11.toByte ----------${a}-${b}-${c}-${d}----------------------------------------------")
                    if (a == "255" && b == "255" && c == "255" && d == "255") {
                        commandUpgrade232ResultListener?.upgradeResult(CmdCode.GJ_VERSION)
                    } else {
                        val size = HexConverter.byteArrayToInt(data)
                        commandUpgrade232ResultListener?.upgradeResult(size)
                    }
                } else {
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                }
            }
            //发送256字节文件回复
            0.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 0.toByte 数据长度与数据域不匹配")
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                var a = ""
                var b = ""
                var c = ""
                for (i in data.indices step 3) {
                    val end = (i + 3).coerceAtMost(data.size)
                    val group = data.copyOfRange(i, end)
                    val size = group.size
                    Loge.i("串口232", "接232 0.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    if (size > 0) {
                        a = group[0].toUByte().toString()
                    }
                    if (size > 1) {
                        b = group[1].toUByte().toString()
                    }
                    if (size > 2) {
                        c = group[2].toUByte().toString()
                    }
                }
                Loge.i("串口232", "接232 0.toByte ----------${a}-${b}-${c}-----------------------------------------------")
                if (a == "1" && b == "2" && c == "3") {
                    Loge.i("串口232", "接232 0.toByte 升级完成 ${CommandStatus.SUCCEED}")
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.SUCCEED)
                } else {
                    commandUpgrade232ResultListener?.upgradeResult(CommandStatus.FAIL)
                }
            }

            //去零清皮
            16.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 16.toByte 数据长度与数据域不匹配")
                    commandCalibrationResultListener?.caliResult(-1, DoorStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 16.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                for (i in data.indices step 2) {
                    val end = (i + 2).coerceAtMost(data.size)
                    val group = data.copyOfRange(i, end)
                    val size = group.size
                    Loge.i("串口232", "接232 16.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    val locker = group[0].toInt()
                    val status = group[1].toInt()
                    if (status == 1) {
                        commandCalibrationResultListener?.caliResult(locker, DoorStatus.SUCCEED)
                    } else {
                        commandCalibrationResultListener?.caliResult(locker, DoorStatus.FAIL)
                    }
                }
            }
            //校准零点 三种kg校准
            17.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 17.toByte 数据长度与数据域不匹配")
                    commandCalibrationResultListener?.caliResult(-1, DoorStatus.FAIL)
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 17.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                for (i in data.indices step 2) {
                    val end = (i + 2).coerceAtMost(data.size)
                    val group = data.copyOfRange(i, end)
                    val size = group.size
                    Loge.i("串口232", "接232 17.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    val locker = group[0].toInt()
                    val status = group[1].toInt()
                    if (status == 1) {
                        commandCalibrationResultListener?.caliResult(locker, DoorStatus.SUCCEED)
                    } else {
                        commandCalibrationResultListener?.caliResult(locker, DoorStatus.FAIL)
                    }
                }
            }
            //文件发送效验
            18.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 18.toByte 数据长度与数据域不匹配")
                    commandUpgradeXYResultListener?.upgradeResult(byteArrayOf())
                    return
                }
                val data = packet.copyOfRange(4, 4 + dataLength)
                Loge.i("串口232", "接232 18.toByte 取数据源：${data.joinToString(" ") { "%02X".format(it) }}")
                if (data.isNotEmpty()) {
                    commandUpgradeXYResultListener?.upgradeResult(data)
                } else {
                    commandUpgradeXYResultListener?.upgradeResult(byteArrayOf())
                }
            }
            //设置阻力值
            19.toByte() -> {
                //取出完整数据
                val toIndex = 4 + dataLength
                if (before != toIndex) {
                    Loge.i("串口232", "接232 2.toByte 数据长度与数据域不匹配")
                    commandDoorResultListener?.openResult(DoorStatus.FAIL)
                    return
                }
                var locker = 0
                val data = packet.copyOfRange(4, 4 + dataLength)
                if (data.isNotEmpty()) {
                    //格口几
                    val code = data.copyOfRange(0, 1)
                    locker = code[0].toInt()
                    val rodHinder = data.copyOfRange(1, 5)
                    Loge.i("串口232", "接232 19.toByte size = ${data.size} | 格口：$locker 阻力值：${HexConverter.byteArrayToInt(rodHinder)}")
                    for (i in rodHinder.indices step 4) {
                        val end = (i + 4).coerceAtMost(rodHinder.size)
                        val group = rodHinder.copyOfRange(i, end)
                        val size = group.size
                        Loge.i("串口232", "接232 19.toByte 数据拆分：i = $i end $end | size $size | group ${ByteUtils.toHexString(group)}")
                    }
                    val size = HexConverter.byteArrayToInt(rodHinder)
                    Loge.i("串口232", "接232 19.toByte 阻力值 $size")
                    commandRodHinderResultListener?.setResult(locker, size)
                } else {
                    commandRodHinderResultListener?.setResult(locker, CommandStatus.FAIL)
                }
            }
        }
    }

    /***************************************************数据处理新方式***********************************************/

    private fun startRead232JobNew() {
        // 启动协程，处理接收消息的任务
        read232Job = ioScope.launch {
            // 缓存区大小可以根据需要调整
            val bufferRead = ByteArray(1024)
            try {
                // 保持协程任务处于活动状态
                while (isActive) {
                    Loge.i("串口232", "接232 协程目前状态:isActive $isActive ")
                    fis232Read.value?.let { data ->
                        val bytesRead = data.read(bufferRead)
                        Loge.i("串口232", "接232 测试新的方式 下位机原始数据包大小: $bytesRead ")
                        if (bytesRead > 0) {
                            //读取下位机数据
                            val receivedData = bufferRead.copyOf(bytesRead)
                            BoxToolLogUtils.receiveOriginalLower(232, receivedData)
                            processReceivedData(receivedData)
                        }
                    }
                    delay(10)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private val buffer = ByteArrayOutputStream()


    private var lastProcessTime = 0L
    private val PROCESS_TIMEOUT = 5000L // 5秒超时

    /***
     * @param newData 接收的数据域
     */
    private fun processReceivedData(newData: ByteArray) {
        Loge.i("串口232", "接232 测试新的方式 大小：${newData.size} 原始：${ByteUtils.toHexString(newData)}")
        try {

            val currentTime = System.currentTimeMillis()

            // 如果距离上次处理时间过长，清空缓冲区（避免处理残留的无效数据）
            if (currentTime - lastProcessTime > PROCESS_TIMEOUT && bufferNew232.size() > 0) {
                Loge.i("串口232", "接232 测试新的方式 处理超时，清空缓冲区残留数据: ${bufferNew232.size()}字节")
                bufferNew232.reset()
            }

            lastProcessTime = currentTime
            // 1. 追加新数据到缓冲区
            bufferNew232.write(newData)
            val currentBuffer = bufferNew232.toByteArray()

            // 2. 处理缓冲区中的数据
            var processedBytes = 0
            var currentIndex = 0

            while (currentIndex < currentBuffer.size) {
                // 3. 查找帧头 (0x9B)
                val headerIndex = findFrameHeader(currentBuffer, currentIndex)
                if (headerIndex == -1) {
                    // 没有找到帧头，所有数据都无法处理
                    processedBytes = currentBuffer.size
//                    BoxToolLogUtils.receiveOriginalLower(0, newData)
                    break
                }

                // 4. 检查是否有足够的数据获取长度字段 (header + 3)
                if (headerIndex + DATA_POS_LENGTH >= currentBuffer.size) {
                    // 数据不足，保留从帧头开始的所有数据
                    processedBytes = headerIndex
//                    BoxToolLogUtils.receiveOriginalLower(0, newData)
                    break
                }

                // 5. 获取数据长度 (第4个字节)
                val dataLength = currentBuffer[headerIndex + DATA_POS_LENGTH].toInt() and 0xFF

                // 6. 计算完整包长度 (修正：6 + dataLength)
                val totalLength =
                    COMPLETE_PACKAGE + dataLength  // 帧头1 + 地址1 + 命令1 + 长度1 + 数据N + 校验码1 + 帧尾1

                // 7. 检查完整数据包
                if (headerIndex + totalLength > currentBuffer.size) {
                    // 数据包不完整，保留从帧头开始的数据
                    processedBytes = headerIndex
//                    BoxToolLogUtils.receiveOriginalLower(0, newData)
                    break
                }

                // 8. 检查帧尾 (0x9A)
                val frameEndIndex = headerIndex + totalLength - 1  // 帧尾在最后一个位置
                if (currentBuffer[frameEndIndex] != SendByteData.RE_FRAME_END) {
                    // 帧尾错误，跳过这个帧头继续查找
                    currentIndex = headerIndex + 1
//                    BoxToolLogUtils.receiveOriginalLower(0, newData)
                    continue
                }

                // 9. 提取完整数据包
                val packet = currentBuffer.copyOfRange(headerIndex, headerIndex + totalLength)

                // 10. 校验和验证
                if (!validateCheckCode(packet)) {
                    // 校验失败，跳过这个包继续查找下一个
                    currentIndex = headerIndex + 1
//                    BoxToolLogUtils.receiveOriginalLower(0, packet)
                    continue
                }

                // 11. 处理有效数据包
                handlePacket232(packet)//新方式2

                // 12. 移动处理位置到下一个包
                currentIndex = headerIndex + totalLength
                processedBytes = currentIndex
            }

            // 13. 保存未处理数据到缓冲区
            bufferNew232.reset()
            if (processedBytes < currentBuffer.size) {
                val remainingData = currentBuffer.copyOfRange(processedBytes, currentBuffer.size)
                bufferNew232.write(remainingData)

                // 调试信息：显示保留的未处理数据长度
                if (remainingData.isNotEmpty()) {
                    Loge.d("保留未处理数据: ${remainingData.size} 字节")
                }
            }

            // 调试信息：显示缓冲区状态
            logBufferStatus(currentBuffer.size, processedBytes)

        } catch (e: Exception) {
            Loge.e("处理接收数据时发生异常: ${e.message}")
            // 发生异常时清空缓冲区，避免错误累积
            bufferNew232.reset()
        }
    }

    /**
     * 记录缓冲区状态
     */
    private fun logBufferStatus(totalSize: Int, processedBytes: Int) {
        val remaining = totalSize - processedBytes
        Loge.d("缓冲区处理: 总共${totalSize}字节, 已处理${processedBytes}字节, 剩余${remaining}字节")
    }

    /***
     * @param buffer 完整数据域
     * @param startIndex
     * 查找帧头
     */
    private fun findFrameHeader(buffer: ByteArray, startIndex: Int): Int {
        for (i in startIndex until buffer.size) {
            if (buffer[i] == SendByteData.RE_FRAME_HEADER) return i
        }
        return -1
    }

    /***
     * @param packet 完整数据域
     * 验证校验码 即是末尾前一位
     */
    private fun validateCheckCode(packet: ByteArray): Boolean {
        if (packet.size < COMPLETE_PACKAGE) {
            Loge.i("串口", "接232 测试新的方式 数据包长度不足: ${packet.size}")
            return false
        }

        // 获取数据长度
        val dataLength = packet[DATA_POS_LENGTH].toInt() and 0xFF

        // 验证包长度是否匹配
        val expectedTotalLength = COMPLETE_PACKAGE + dataLength
        if (packet.size != expectedTotalLength) {
            Loge.i("串口", "接232 测试新的方式 数据包长度不匹配: 期望=$expectedTotalLength, 实际=${packet.size}")
            return false
        }

        // 计算校验和的范围：从帧头开始到数据区域结束
        // 数据区域结束位置 = 帧头(1) + 地址(1) + 命令(1) + 长度(1) + 数据(dataLength) = 4 + dataLength
        val dataEndIndex = BEFORE_FOUR_POS + dataLength  // 数据区域结束位置（不包括校验码）

        // 计算从帧头到数据区域结束的所有字节的无符号和
        var sum = 0
        for (i in 0 until dataEndIndex) {
            sum += packet[i].toInt() and 0xFF
        }

        // 计算校验码：和除以256的余数
        val calculatedCheckCode = sum % 256

        // 获取包中的实际校验码（位置在数据区域之后）
        val actualCheckCode = packet[dataEndIndex].toInt() and 0xFF

        // 记录校验信息（调试用）
        Loge.d(
            """接232 测试新的方式 
        校验码验证:
        - 数据长度: $dataLength
        - 计算范围: 0~${dataEndIndex - 1} (${dataEndIndex}字节)
        - 字节和: $sum
        - 计算校验码: $calculatedCheckCode (0x${calculatedCheckCode.toString(16).uppercase()})
        - 实际校验码: $actualCheckCode (0x${actualCheckCode.toString(16).uppercase()})
        - 验证结果: ${calculatedCheckCode == actualCheckCode}
    """.trimIndent()
        )

        return calculatedCheckCode == actualCheckCode
    }
    /***************************************************数据处理新方式***********************************************/

    /***************************************************核心优化：循环处理缓冲区中的所有完整数据包***********************************************/
    // 自定义带起始位置的 indexOf 方法
    private fun ByteArray.indexOf(byte: Byte, fromIndex: Int = 0): Int {
        for (i in fromIndex.coerceAtLeast(0) until this.size) {
            if (this[i] == byte) return i
        }
        return -1
    }
    /***************************************************核心优化：循环处理缓冲区中的所有完整数据包***********************************************/

    /***********************************************************针对自定义协议V1.0***************************************************************************/

    /***********************************************************资源释放***************************************************************************/

    override fun onCleared() {
        super.onCleared()
        close232SerialPort()
    }

    /***
     * 释放232资源
     */
    fun close232SerialPort() {

        fd232.value = null
        fis232.value?.close()
        fis232.value = null
        fos232.value?.close()
        fos232.value = null

        stopSend232Job()
        stopRead232Job()

    }

    /**
     * 停止发送消息的协程任务
     */
    private fun stopSend232Job() {
        // 取消发送任务
        sending232Job?.cancel()
        sendingStatus232Job?.cancel()
        sending232Job = null
        sendingStatus232Job = null
    }

    /**
     * 停止接收消息的协程任务
     */
    private fun stopRead232Job() {
        // 取消接收任务
        read232Job?.cancel()
        read232Job = null
    }
    /***********************************************************资源释放***************************************************************************/

}