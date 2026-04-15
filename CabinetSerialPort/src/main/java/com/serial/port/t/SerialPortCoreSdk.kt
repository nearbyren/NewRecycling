package com.serial.port.t

import com.serial.port.BuildConfig
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.CmdCode.CLEAR_OPEN_1_1
import com.serial.port.utils.CmdCode.CLEAR_OPEN_2_1
import com.serial.port.utils.CmdCode.CLEAR_QUERY_1_0
import com.serial.port.utils.CmdCode.CLEAR_QUERY_2_0
import com.serial.port.utils.HexConverter
import com.serial.port.utils.Loge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * @author: lr
 * @created on: 2026/3/21 下午4:06
 * @description:
 */


class SerialPortCoreSdk private constructor() {
    // 使用核心线程作用域
    private val coreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scheduler = CommandScheduler(coreScope)

    companion object {
        val instance by lazy { SerialPortCoreSdk() }
    }

    private val vm get() = SerialPortManagerSdk.instance.serialVM

    private suspend fun execute(cmd: Byte, data: ByteArray): Result<ByteArray> {
        val frame = ProtocolCodec.encode(cmd, SerialPortSdk.ADDR, data)
        return vm?.sendWithRetry(frame) ?: Result.failure(Exception("Communication VM is null"))
    }

    private suspend fun executeStatus(cmd: Byte, data: ByteArray): Result<ByteArray> {
        val frame = ProtocolCodec.encode(cmd, SerialPortSdk.ADDR, data)
        return vm?.sendWithRetryStatus(frame)
            ?: Result.failure(Exception("Communication VM is null"))
    }

    private suspend fun executeChip(cmd: Byte, data: ByteArray): Result<ByteArray> {
        val frame = ProtocolCodec.encode(cmd, SerialPortSdk.ADDR, data)
        return vm?.sendWithRetryChip(frame) ?: Result.failure(Exception("Communication VM is null"))
    }

    suspend fun executeChip2(cmd: Byte, data: ByteArray): Result<ByteArray> {
        val frame = ProtocolCodec.encode(cmd, SerialPortSdk.ADDR, data)
        return vm?.executeDirect(cmd, frame)
            ?: Result.failure(Exception("Communication VM is null"))
    }


    /**
     * 核心执行器：对接调度器与底层 VM
     */
    private suspend fun <T> execute(
        priority: Priority,
        maxRetries: Int,
        cmd: Byte,
        data: ByteArray,
        parser: (ByteArray) -> T,
    ): Result<T> {
        return scheduler.submit(priority, maxRetries) {
            // 1. 封包
            val frame = ProtocolCodec.encode(cmd, SerialPortSdk.ADDR, data)

            // 2. 调用 VM 的基础发送方法 (替换掉不存在的 sendDirect)
            val rawResult = vm?.sendOnce(frame) ?: Result.failure(Exception("串口服务未初始化"))

            // 3. 解析校验
            rawResult.mapCatching { bytes ->
                val payload = ProtocolCodec.getSafePayload(bytes)
                    ?: throw Exception("协议格式错误或长度校验失败")
                parser(payload)
            }
        }
    }

    /**
     * 修改后的内部公共发送方法，接入调度器
     */
    private suspend fun <T> executeWithPriority(
        priority: Priority,
        cmd: Byte,
        data: ByteArray,
        parser: (ByteArray) -> T,
    ): Result<T> {
        // 提交到调度器
        return scheduler.submit(priority) {
            val frame = ProtocolCodec.encode(cmd, SerialPortSdk.ADDR, data)
            val rawResult = vm?.sendWithRetry(frame) ?: Result.failure(Exception("串口不可用"))

            rawResult.mapCatching { bytes ->
                val payload = ProtocolCodec.getSafePayload(bytes)
                    ?: throw Exception("解析Payload失败")
                parser(payload)
            }
        }
    }

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

    suspend fun openDoor(locker: Int): Result<DoorResult> =
        scheduler.submit(Priority.IMMEDIATE, maxRetries = 5) {
            val frame = ProtocolCodec.encode(SerialPortSdk.CMD1, SerialPortSdk.ADDR, byteArrayOf(locker.toByte(), 0x01))
            // 注意：这里调用底层 VM 时不需要再写 retry，因为调度器层已经接管了 retry
            vm?.sendWithRetry(frame)?.mapCatching {
                val payload = ProtocolCodec.getSafePayload(it) ?: throw Exception("解析Payload失败")
                DoorResult(locker = payload[0].toInt(), status = payload[1].toInt())
            } ?: Result.failure(Exception("串口未就绪"))
        }

    suspend fun turnDoor(code: Int): Result<DoorResult> {
        val data = startDoor[code]!!
        return execute(SerialPortSdk.CMD1, data).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.i("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD1) DoorResult(cmd = 1, cmdByte = SerialPortSdk.CMD1, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.i("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            if (payload.size < 2) throw Exception("返回数据长度不足")
            DoorResult(
                locker = payload[0].toInt(), status = payload[1].toInt(), cmd = 1, cmdByte = SerialPortSdk.CMD1, cmdStatus = true
            )
        }
    }

    suspend fun turnDoorRetries(code: Int): Result<DoorResult> {
        val data = startDoor[code]!!
        return execute(Priority.HIGH, 3, SerialPortSdk.CMD1, data) { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.i("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD1) DoorResult(cmd = 1, cmdByte = SerialPortSdk.CMD1, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.i("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            if (payload.size < 2) throw Exception("返回数据长度不足")
            DoorResult(
                locker = payload[0].toInt(), status = payload[1].toInt(), cmd = 1, cmdByte = SerialPortSdk.CMD1, cmdStatus = true
            )
        }
    }

    /***
     * 启动格口状态查询
     */
    private val startDoorStatus: MutableMap<Int, ByteArray> = mutableMapOf(
        //格口一
        CmdCode.GE1 to byteArrayOf(0x01, 0x01),
        //格口二
        CmdCode.GE2 to byteArrayOf(0x02, 0x02),
    )

    /** 查询门状态 */
    suspend fun turnDoorStatus(code: Int): Result<DoorResult> {
        val data = startDoorStatus[code]!!
        return execute(SerialPortSdk.CMD2, data).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.i("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD2) DoorResult(cmd = 2, cmdByte = SerialPortSdk.CMD2, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.i("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            if (payload.size < 2) throw Exception("返回数据长度不足")
            DoorResult(
                locker = payload[0].toInt(), status = payload[1].toInt(), cmd = 2, cmdByte = SerialPortSdk.CMD2, cmdStatus = true
            )
        }
    }

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

    /** 开启和查询清运门 */
    suspend fun openQueryClear(code: Int): Result<DoorResult> {
        val data = clearDoor[code]!!
        val cType = when (code) {
            CLEAR_OPEN_1_1, CLEAR_OPEN_2_1 -> {
                1
            }

            else -> {
                0
            }
        }
        return execute(SerialPortSdk.CMD3, data).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.i("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD3) DoorResult(status = 3, clearType = cType, cmd = 3, cmdByte = SerialPortSdk.CMD3, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.i("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            if (payload.size < 3) throw Exception("返回数据长度不足")
            DoorResult(
                locker = payload[0].toInt(), status = payload[1].toInt(), clearType = payload[2].toInt(), cmd = 3, cmdByte = SerialPortSdk.CMD3, cmdStatus = true
            )
        }
    }

    /***
     * 查询重量
     */
    private val weightDoor: MutableMap<Int, ByteArray> = mutableMapOf(
        //格口一
        CmdCode.GE1 to byteArrayOf(0x01, 0x01),
        //格口二
        CmdCode.GE2 to byteArrayOf(0x02, 0x01),
    )

    /** 查询重量 */
    suspend fun queryWeight(code: Int): Result<DoorResult> {
        val data = weightDoor[code]!!
        return execute(SerialPortSdk.CMD4, data).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.i("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD4) DoorResult(cmd = 4, cmdByte = SerialPortSdk.CMD4, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.i("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            DoorResult(
                weight = HexConverter.getWeight(ProtocolCodec.bytesToInt(payload)), cmd = 4, cmdByte = SerialPortSdk.CMD4, cmdStatus = true
            )
        }
    }

    /** 查询货柜状态 */
    suspend fun queryStatus(): Result<DoorResult> {
        return executeStatus(SerialPortSdk.CMD5, byteArrayOf(0x01, 0x01)).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.i("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD5) DoorResult(containers = mutableListOf(), cmd = 5, cmdByte = SerialPortSdk.CMD5, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.i("我的数据 len ${payload.size} ${ByteUtils.toHexString(payload)}")
            if (payload.size < 28) throw Exception("返回数据长度不足")
            val list = mutableListOf<ContainersResult>()
            val STEP = 14 // 每组格口数据占据 14 字节
            // 使用之前 ProtocolCodec 中的分组工具
            val groups = ProtocolCodec.parseGroups(payload, STEP)
            groups.forEach { group ->
                if (group.size < STEP) return@forEach // 防护处理
                // 1. 提取重量 (index 1 到 4，共 4 字节)
                val weightBytes = group.copyOfRange(1, 5)
                val rawWeight = ProtocolCodec.bytesToInt(weightBytes)
                Loge.i("我的数据 for 重量：${rawWeight}")
                // 2. 封装对象
                list.add(
                    ContainersResult(
                        locker = group[0].toUByte().toInt(), weigh = HexConverter.getWeight(rawWeight),
                        // 状态位从 index 5 开始
                        smokeValue = group[5].toUByte().toInt(), irStateValue = group[6].toUByte().toInt(), touCGStatusValue = group[7].toUByte().toInt(), touJSStatusValue = group[8].toUByte().toInt(), doorStatusValue = group[9].toUByte().toInt(), lockStatusValue = group[10].toUByte().toInt(), xzStatusValue = group[11].toUByte().toInt(), jsStatusValue = group[12].toUByte().toInt()
                        // 如果还有第 13 位，可以在此继续映射
                    )
                )
            }
            DoorResult(containers = list, cmdByte = SerialPortSdk.CMD5, cmdStatus = true)
        }
    }

    /** 灯光操作 */
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

    suspend fun startLights(code: Int): Result<DoorResult> {
        val data = inOutLights[code]!!
        return execute(SerialPortSdk.CMD6, data).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.i("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD6) DoorResult(cmd = 6, cmdByte = SerialPortSdk.CMD6, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.i("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            DoorResult(
                locker = payload[0].toInt(), status = payload[1].toInt(), cmd = 6, cmdByte = SerialPortSdk.CMD6, cmdStatus = true
            )
        }
    }

    /** 校准 */
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

    /** 去皮清零 */
    suspend fun startCalibrationQP(doorGeX: Int, code: Int): Result<DoorResult> {
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
        val data = calibration[code]!!
        val sendByte = HexConverter.combineByteArrays(data2, data)
        return execute(SerialPortSdk.CMD16, sendByte).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.i("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD16) DoorResult(cmd = 16, cmdByte = SerialPortSdk.CMD16, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.i("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            DoorResult(
                locker = payload[0].toInt(), caliStatus = if (payload[1].toInt() == 1) 1 else 0, cmd = 16, cmdByte = SerialPortSdk.CMD16, cmdStatus = true
            )
        }
    }
    /***
     * 校准动作
     */
    suspend fun startCalibration(doorGeX: Int, code: Int): Result<DoorResult> {
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
        val data = calibration[code]!!
        val sendByte = HexConverter.combineByteArrays(data2, data)
        return execute(SerialPortSdk.CMD17, sendByte).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.e("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD17) DoorResult(cmd = 17, cmdByte = SerialPortSdk.CMD17, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.e("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            DoorResult(
                locker = payload[0].toInt(), caliStatus = if (payload[1].toInt() == 1) 1 else 0, cmd = 17, cmdByte = SerialPortSdk.CMD17, cmdStatus = true
            )
        }
    }

    /***
     * 格口阻力值
     */
    private val rodHinder: MutableMap<Int, ByteArray> = mutableMapOf(
        //格口一
        CmdCode.GE1 to byteArrayOf(0x01),
        //格口二
        CmdCode.GE2 to byteArrayOf(0x02),
    )

    /** 设置阻力值 */
    suspend fun startRodHinder(code: Int, number: Int): Result<DoorResult> {
        val data = rodHinder[code]!!
        val data2 = HexConverter.intToByteArray(number)
        val sendByte = HexConverter.combineByteArrays(data, data2)
        return execute(SerialPortSdk.CMD19, sendByte).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.e("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD19) DoorResult(cmd = 19, cmdByte = SerialPortSdk.CMD19, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.e("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            if (payload.size < 5) throw Exception("返回数据长度不足")
            val value = payload.takeLast(4).toByteArray()
            val rodHinderValueByte = ProtocolCodec.bytesToInt(value)
            DoorResult(
                locker = payload[0].toInt(), rodHinderValue = rodHinderValueByte, cmd = 19, cmdByte = SerialPortSdk.CMD19, cmdStatus = true
            )
        }
    }

    /** 进入升级 升级状态 文件校验 重启 查版本*/
    suspend fun firmwareUpgrade78910(commandType: Int, data: ByteArray): Result<DoorResult> {
        val setCmd = when (commandType) {
            7 -> {
                SerialPortSdk.CMD7
            }

            8 -> {
                SerialPortSdk.CMD8
            }

            9 -> {
                SerialPortSdk.CMD9
            }

            10 -> {
                SerialPortSdk.CMD10
            }

            else -> {
                SerialPortSdk.CMD11
            }

        }

        return executeChip(setCmd, data).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.e("我的数据 cmd $cmd ${ByteUtils.toHexString(data)}")
            if (cmd != setCmd) DoorResult(cmd = commandType, cmdByte = setCmd, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.e("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            val result = when (setCmd) {
                SerialPortSdk.CMD7, SerialPortSdk.CMD8, SerialPortSdk.CMD10 -> {
                    if (payload.size == 3) 1 else 0
                }

                SerialPortSdk.CMD9 -> {
                    val a = payload[0].toString()
                    val b = payload[1].toString()
                    val c = payload[2].toString()
                    if (a == "164" && b == "165" && c == "166") {
                        1
                    } else if (a == "180" && b == "181" && c == "182") {
                        0
                    } else {
                        0
                    }
                }

                SerialPortSdk.CMD11 -> {
                    val a = payload[0].toString()
                    val b = payload[1].toString()
                    val c = payload[2].toString()
                    val d = payload[3].toString()
                    if (a == "255" && b == "255" && c == "255" && d == "255") {
                        20260320
                    } else {
                        HexConverter.byteArrayToInt(payload)
                    }
                }

                else -> {
                    0
                }
            }
            if (setCmd == SerialPortSdk.CMD11) {
                DoorResult(chipVersion = result, cmd = commandType, cmdByte = cmd, cmdStatus = true)
            } else {
                DoorResult(upStatus = result, cmd = commandType, cmdByte = cmd, cmdStatus = true)

            }
        }
    }

    /** 发送文件 */
    suspend fun firmwareUpgradeFile(byte: ByteArray): Result<DoorResult> {
        return executeChip(SerialPortSdk.CMD18, byte).mapCatching { bytes ->
            val cmd = bytes[SerialPortSdk.CMD_POS]
            Loge.e("我的数据 cmd $cmd")
            if (cmd != SerialPortSdk.CMD18) DoorResult(cmd = 18, cmdByte = SerialPortSdk.CMD18, cmdStatus = false)
            val payload = ProtocolCodec.getSafePayload(bytes) ?: throw Exception("解析Payload失败")
            Loge.e("我的数据 $cmd payload ${ByteUtils.toHexString(payload)}")
            if (payload.size < 8) throw Exception("返回数据长度不足")
            DoorResult(byteArray = payload, cmd = 18, cmdByte = SerialPortSdk.CMD18, cmdStatus = true)
        }
    }
}