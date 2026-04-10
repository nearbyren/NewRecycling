package com.recycling.toolsapps.vm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.recycling.toolsapps.db.DatabaseManager
import com.recycling.toolsapps.http.FileCleaner
import com.recycling.toolsapps.http.RepoImpl
import com.recycling.toolsapps.http.TaskDelDateScheduler
import com.recycling.toolsapps.http.TaskLightsScheduler
import com.recycling.toolsapps.model.ConfigEntity
import com.recycling.toolsapps.model.FileEntity
import com.recycling.toolsapps.model.LatticeEntity
import com.recycling.toolsapps.model.LogEntity
import com.recycling.toolsapps.model.ResEntity
import com.recycling.toolsapps.model.StateEntity
import com.recycling.toolsapps.model.TransEntity
import com.recycling.toolsapps.model.WeightEntity
import com.recycling.toolsapps.socket.AdminOverflowBean
import com.recycling.toolsapps.socket.ConfigBean
import com.recycling.toolsapps.socket.DoorCloseBean
import com.recycling.toolsapps.socket.DoorOpenBean
import com.recycling.toolsapps.socket.FaultBean
import com.recycling.toolsapps.socket.FaultInfo
import com.recycling.toolsapps.socket.OtaBean
import com.recycling.toolsapps.socket.PhotoBean
import com.recycling.toolsapps.utils.CalculationUtil
import com.recycling.toolsapps.utils.CmdValue
import com.recycling.toolsapps.utils.EntityType
import com.recycling.toolsapps.utils.EnumFaultState
import com.recycling.toolsapps.utils.FaultType
import com.recycling.toolsapps.utils.JsonBuilder
import com.recycling.toolsapps.utils.MediaPlayerHelper
import com.recycling.toolsapps.utils.MonitorType
import com.recycling.toolsapps.utils.RefBusType
import com.recycling.toolsapps.utils.ResType
import com.recycling.toolsapps.utils.TelephonyUtils
import com.recycling.toolsapps.utils.WeightChangeStorage
import com.recycling.toolsapps.view.AwesomeQRCode
import com.recycling.toolsapps.vm.CabinetVM.ConnectionState.*
import com.serial.port.t.ContainersResult
import com.serial.port.t.ProtocolCodec
import com.serial.port.t.SendOpenText
import com.serial.port.t.SerialPortCoreSdk
import com.serial.port.t.SerialPortSdk
import com.serial.port.utils.AppUtils
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.CRC32MPEG2Util
import com.serial.port.utils.CmdCode
import com.serial.port.utils.FileMdUtil
import com.serial.port.utils.HexConverter
import com.serial.port.utils.Loge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import nearby.lib.netwrok.download.SingleDownloader
import nearby.lib.netwrok.response.CorHttp
import nearby.lib.netwrok.response.SPreUtil
import nearby.lib.signal.livebus.BusType
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random


@HiltViewModel
class CabinetVM @Inject constructor() : ViewModel() {

    private val httpRepo by lazy { RepoImpl() }

    /**
     * 用于处理 I/O 操作的协程作用域
     */
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 用于处理 main 操作的协程作用域
     */
    val mainScope = MainScope()

    /***
     * 用于处理 默认协程作用域
     */
    val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)


    private val sendFileByte232 = Channel<ByteArray>()

    /***
     * openDoor
     * closeDoor
     */
    var activeType = ""

    /***
     * 打开内摄像照片
     */
    var photoOpenIn = ""

    /***
     * 打开内摄像照片
     */
    var photoOpenOut = ""

    /***
     * 关闭内摄像照片
     */
    var photoCloseIn = ""

    /***
     * 关闭外摄像照片
     */
    var photoCloseOut = ""

    /***
     * 插入数据库
     */
    fun toGoInsertPhoto(setTransId: String, switchType: String, inOut: Int, filePath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileEntity = FileEntity().apply {
                cmd = switchType
                transId = setTransId
                time = AppUtils.getDateYMDHMS()
            }
            var cmdValue = ""
            fileEntity.msg = when (switchType) {
                "1" -> {
                    when (inOut) {
                        0 -> {
                            fileEntity.photoIn = filePath
                        }

                        1 -> {
                            fileEntity.photoOut = filePath
                        }
                    }
                    cmdValue = CmdValue.CMD_OPEN_DOOR
                    "开仓前照片"

                }

                "0" -> {
                    cmdValue = CmdValue.CMD_CLOSE_DOOR
                    when (inOut) {
                        0 -> {
                            fileEntity.photoIn = filePath
                        }

                        1 -> {
                            fileEntity.photoOut = filePath
                        }
                    }
                    "开仓后照片"
                }

                "45" -> {
                    cmdValue = CmdValue.CMD_CLOSE_DOOR
                    when (inOut) {
                        0 -> {
                            fileEntity.photoIn = filePath
                        }

                        1 -> {
                            fileEntity.photoOut = filePath
                        }
                    }
                    "远程拍照"
                }

                else -> {
                    "匹配拍照类型失败"
                }
            }
            val fileDb =
                DatabaseManager.queryFileEntity(AppUtils.getContext(), cmdValue, setTransId)
            if (fileDb == null) {
                val row = DatabaseManager.insertFile(AppUtils.getContext(), fileEntity)
                Loge.e("调试socket toGoInsertPhoto 插入 row $row")
            } else {
                when (switchType) {
                    "1" -> {
                        when (inOut) {
                            0 -> {
                                fileDb.photoIn = filePath
                            }

                            1 -> {
                                fileDb.photoOut = filePath
                            }
                        }

                    }

                    "0" -> {
                        when (inOut) {
                            0 -> {
                                fileDb.photoIn = filePath
                            }

                            1 -> {
                                fileDb.photoOut = filePath
                            }
                        }
                    }

                    "45" -> {
                        when (inOut) {
                            0 -> {
                                fileDb.photoIn = filePath
                            }

                            1 -> {
                                fileDb.photoOut = filePath
                            }
                        }
                    }
                }
                val row = DatabaseManager.upFileEntity(AppUtils.getContext(), fileDb)
                Loge.e("调试socket toGoInsertPhoto 更新 row $row")
            }
        }
    }

    //校准前
    private val caliBefore2 = MutableSharedFlow<Boolean>(replay = 0)
    val getCaliBefore2: SharedFlow<Boolean> = caliBefore2.asSharedFlow()

    //校准结果
    private val caliResult = MutableSharedFlow<Boolean>(replay = 0)
    val getCaliResult: SharedFlow<Boolean> = caliResult.asSharedFlow()

    //处理网络提示语
    private val flowIsNetworkMsg = MutableSharedFlow<String>(replay = 1)
    val _isNetworkMsg = flowIsNetworkMsg.asSharedFlow()

    //校准前
    private val flowTestClearDoor = MutableSharedFlow<Boolean>(replay = 0)
    val getTestClearDoor: SharedFlow<Boolean> = flowTestClearDoor.asSharedFlow()

    //启动显示格口页
    private val flowLoginCmd = MutableSharedFlow<Boolean>(replay = 0)
    val getLoginCmd = flowLoginCmd.asSharedFlow()

    //信号值读取
    private val flowIsReadSignal = MutableSharedFlow<Int>(replay = 1)
    val isReadSignal = flowIsReadSignal.asSharedFlow()

    /***
     * 刷新首页网络图片
     */
    private val netRefreshHomeRes = MutableSharedFlow<String>(replay = 0)
    val isRefreshHomeRes = netRefreshHomeRes.asSharedFlow()

    /***
     * 提示语
     */
    fun tipMessage(msg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flowIsNetworkMsg.emit("${msg}")
        }
    }

    fun closeAllScope() {
        ioScope.cancel()
        mainScope.cancel()
        clientScope.cancel()
        sendFileByte232.close()
        SerialPortSdk.release()
    }

    /******************************************* socket通信 *************************************************/
    //socket连接实例
    var isDistClient = false
    fun closeSock() {
        viewModelScope.launch(Dispatchers.IO) {
            stop()
        }
    }

    /***
     * 手机投递方式
     * @param phoneNumber
     */
    fun toGoMobile(phoneNumber: String, doorGex: Int = CmdCode.GE) {
        viewModelScope.launch(Dispatchers.IO) {
            doorGeX = doorGex
            val m = mapOf(
                "cmd" to CmdValue.CMD_PHONE_NUMBER_LOGIN, "phoneNumber" to phoneNumber, "timestamp" to System.currentTimeMillis()
            )
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
            BoxToolLogUtils.recordSocket(CmdValue.CONNECTING, "home,door-手机登录")
        }
    }

    /***
     * 接收手机号登录后,发送手机号开门
     * @param cabinId
     * @param userId
     */
    fun toGoMobileOpen(cabinId: String, userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf(
                "cmd" to CmdValue.CMD_PHONE_USER_OPEN_DOOR, "cabinId" to cabinId, "userId" to userId, "timestamp" to System.currentTimeMillis()
            )
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
            curUserId = userId
            actionType = 1
        }
    }

    /**
     * 拍照上传成功
     */
    fun toGoTPSucces(transId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf(
                "cmd" to CmdValue.CMD_ADMIN_PHOTO, "type" to "res", "transId" to transId, "retCode" to 0, "timestamp" to System.currentTimeMillis()
            )
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)

        }
    }

    /***
     * 继续登录
     */
    val flowAgainLogin = MutableStateFlow(false)
    val getAgainLogin: MutableStateFlow<Boolean> = flowAgainLogin

    var againJob: Job? = null

    fun toGoAgainLogin() {
        Loge.e("流程 toGoAgainLogin 启动toGoAgainLogin")
        if (againJob?.isActive == true) {
            Loge.e("流程 toGoAgainLogin toGoAgainLogin已在运行")
            return
        }
        againJob = ioScope.launch {
            while (isActive) {
                delay(5000L)
                val state = state?.value ?: DISCONNECTED
                Loge.e("流程 toGoAgainLogin ：${getAgainLogin.value} | state $state")
                if (!getAgainLogin.value) {
                    tipMessage("登录失败，继续登录")
                    val loginCount = SPreUtil[AppUtils.getContext(), SPreUtil.loginCount, 0] as Int
                    val result = loginCount + 1
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.loginCount, result)
                    toGoCmdLogin(result)//监听连接成功登录
                } else {
                    cancelJobAgain()
                }
            }
        }
    }

    fun cancelJobAgain() {
        flowAgainLogin.value = false //标记未登录
        againJob?.cancel()
        againJob = null
    }

    /***
     * 业务通信前，先登录
     * @param loginCount
     * @param sn 是初始化传过来的，
     * @param imsi iccid是登录传过来的
     * @param imei imei是初始化传过来的，
     * @param iccid iccid是登录传过来的
     */
    fun toGoCmdLogin(
        loginCount: Int? = 0,
        sn: String? = "0136004ST00066",
        imsi: String? = "460086886808642",
        imei: String? = "868408068812125",
        iccid: String? = "898604A70855C0049781",
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val gversion =
                SPreUtil[AppUtils.getContext(), SPreUtil.gversion, CmdCode.GJ_VERSION] as Int
            Loge.e("流程 toGoCmdOtaBin handleVersionQuery login $gversion")
            val getSn = SPreUtil[AppUtils.getContext(), SPreUtil.init_sn, ""]
            val getImsi = SPreUtil[AppUtils.getContext(), SPreUtil.setImsi, ""]
            val getImei = SPreUtil[AppUtils.getContext(), SPreUtil.setImei, ""]
            val getIccid = SPreUtil[AppUtils.getContext(), SPreUtil.setIccid, ""]
            val m = mapOf(
                "cmd" to CmdValue.CMD_LOGIN, "loginCount" to loginCount, "sn" to getSn, "imsi" to getImsi, "imei" to getImei, "iccid" to getIccid, "version" to gversion, "apkVersion" to AppUtils.getVersionName(), "timestamp" to System.currentTimeMillis()
            )
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }
    }

    /***
     * OTA apk
     */
    fun toGoCmdOtaAPK() {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf(
                "cmd" to CmdValue.CMD_OTA_APK, "retCode" to 0, "timestamp" to System.currentTimeMillis()
            )
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }
    }

    /***
     * OTA bin
     */
    fun toGoCmdOtaBin() {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf(
                "cmd" to CmdValue.CMD_OTA, "retCode" to 0, "timestamp" to System.currentTimeMillis()
            )
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }
    }

    /***
     * 日志
     */
    fun toGoCmdLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf(
                "cmd" to CmdValue.CMD_UPLOAD_LOG, "retCode" to 0, "timestamp" to System.currentTimeMillis()
            )
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
            TaskDelDateScheduler.triggerImmediately(AppUtils.getContext(), "goDelf")
        }
    }

    /***
     * 下发满溢处理
     */
    fun toGoAdminOverflow(adminOverflowModel: AdminOverflowBean) {
        viewModelScope.launch(Dispatchers.IO) {
            val autoCalcOverflow = adminOverflowModel.autoCalcOverflow
            val overflowState = adminOverflowModel.overflowState
            val cabinId = adminOverflowModel.cabinId ?: ""
            val transId = adminOverflowModel.transId ?: ""
            if (autoCalcOverflow == 0 && overflowState == 1) {
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState, true)
                when (cabinId) {
                    cur1Cabinld -> {
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState1, false)
                        maptDoorFault[FaultType.FAULT_CODE_2110] = true

                    }

                    cur2Cabinld -> {
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState2, false)
                        maptDoorFault[FaultType.FAULT_CODE_2120] = true
                    }
                }
            } else {
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState, false)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState1, false)
                maptDoorFault[FaultType.FAULT_CODE_2110] = false
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState2, false)
                maptDoorFault[FaultType.FAULT_CODE_2120] = false
            }
            val m = mapOf(
                "cmd" to CmdValue.CMD_ADMIN_OVERFLOW, "type" to "res", "retCode" to 0, "transId" to transId, "timestamp" to System.currentTimeMillis()
            )
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }

    }

    //更新资源文件下载
    private fun upNetResDb(typeMsg: String, resourceEntity: ResEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val row = DatabaseManager.upResEntity(AppUtils.getContext(), resourceEntity)
            insertInfoLog(LogEntity().apply {
                cmd = "ota/apk"
                msg = "$typeMsg"
                time = AppUtils.getDateYMDHMS()
            })
            Loge.e("调试socket 统一下载 $typeMsg $row")
            Loge.e("调试socket 统一下载 $typeMsg 更新数据 $row")
        }
    }

    /***
     * 上传日志
     */
    fun toGoCmdUpLog() {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseManager.copyDatabasesDirectory(
                AppUtils.getContext(), "socket_box_crash" // 目标目录名称
            )
            delay(9000)
            // 目标文件夹路径
            val targetFolder = File(
                AppUtils.getContext()
                    .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "socket_box_crash"
            )
            // 压缩包输出路径
            val zipOutput = File(
                AppUtils.getContext()
                    .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${AppUtils.getDateYMDHMS3()}_socket_box_crash.zip"
            )

            // 执行压缩
            val success = FileCleaner.zipFolder(targetFolder.absolutePath, zipOutput.absolutePath)
            delay(3000)
            if (success) {
                // 压缩成功处理
                uploadLog(curSn, zipOutput)
            } else {
                // 压缩失败处理
            }
        }
    }

    /***
     * 匹配服务器异常状态
     */
    private fun matchExceType(toType: Int): Int {
        return when (toType) {
            FaultType.FAULT_CODE_111, FaultType.FAULT_CODE_121 -> {
                1
            }

            FaultType.FAULT_CODE_110, FaultType.FAULT_CODE_120 -> {
                2
            }

            FaultType.FAULT_CODE_311, FaultType.FAULT_CODE_321, FaultType.FAULT_CODE_331 -> {
                3
            }

            FaultType.FAULT_CODE_410, FaultType.FAULT_CODE_420, FaultType.FAULT_CODE_430 -> {
                4
            }

            FaultType.FAULT_CODE_51, FaultType.FAULT_CODE_52 -> {
                5
            }

            FaultType.FAULT_CODE_6 -> {
                6
            }

            FaultType.FAULT_CODE_7 -> {
                7
            }

            FaultType.FAULT_CODE_8 -> {
                8
            }

            FaultType.FAULT_CODE_91, FaultType.FAULT_CODE_92 -> {
                9
            }

            else -> {
                toType
            }
        }
    }

    /***
     * 上传异常
     * @param toType
     * @param toCabinIndex
     * @param toDesc
     */
    fun toGoCmdUpFault(toType: Int, toCabinIndex: Int, toDesc: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val setToType = matchExceType(toType)
            val cabinld = when (doorGeX) {
                CmdCode.GE1 -> {
                    cur1Cabinld//根据格口编码上报故障
                }

                CmdCode.GE2 -> {
                    cur2Cabinld//根据格口编码上报故障
                }

                else -> {
                    ""
                }
            }
            val doorOpen = FaultBean().apply {
                cmd = CmdValue.CMD_FAULT//回应服务器
                imei = TelephonyUtils.getImei(AppUtils.getContext())
                sn = curSn
                data = FaultInfo().apply {
                    type = setToType
                    cabinIndex = toCabinIndex
                    cabinId = cabinld
                    desc = toDesc
                }
                timestamp = System.currentTimeMillis().toString()
            }
            val json = JsonBuilder.convertToJsonString(doorOpen)
            Loge.e("模拟故障满溢状态 上传 Fault $json")
            sendText(json)
        }
    }

    /***
     * 打开关闭语音
     * @param type 1.播报打开 2.播报关闭
     */
    fun toGoOpenCloseAudio(type: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            //检测文件是否存在，否则取asset音频
            Loge.e("流程 toGoOpenCloseAudio ${if (type == 1) "播报打开" else "播报关闭"}")
            when (type) {
                CmdCode.GE_OPEN -> {
                    val isAudio = FileMdUtil.checkAudioFileExists("opendoor.wav")
                    Loge.e("流程 播放语音 $isAudio - true：播放下载语音")
                    if (!isAudio) {
                        MediaPlayerHelper.playAudioAsset(AppUtils.getContext(), "opendoor.wav")
                    } else {
                        MediaPlayerHelper.playAudioFromAppFiles(
                            AppUtils.getContext(), "audio", "opendoor.wav"
                        )
                    }
                }

                CmdCode.GE_CLOSE -> {
                    val isAudio = FileMdUtil.checkAudioFileExists("closedoor.wav")
                    Loge.e("流程 播放语音 $isAudio - true：播放下载语音")
                    if (!isAudio) {
                        MediaPlayerHelper.playAudioAsset(AppUtils.getContext(), "closedoor.wav")
                    } else {
                        MediaPlayerHelper.playAudioFromAppFiles(
                            AppUtils.getContext(), "audio", "closedoor.wav"
                        )
                    }
                }
            }
        }
    }
    /*****************************************监听仓门是否关闭******************************************/


    /***
     * 二维码
     */
    var mQrCode: Bitmap? = null

    /***
     * 主页背景图
     */
    var mHomeBg: Bitmap? = null

    /***
     * 维护背景图
     */
    var mMaintaining: Bitmap? = null

    /***
     * 标记是否在清运状态
     */
    var isClearStatus = false

    /***
     * 区分操作类型
     * 0.二维码
     * 1.手机号
     * 2.清运
     */
    var actionType = 0

    /***
     * 当前设备格口类型
     */
    var doorGeXType = CmdCode.GE

    /***
     * 标记当前格口
     */
    var doorGeX = CmdCode.GE

    //当前价格
    var curGePrice: String? = null

    //当前格二价格
    var curG2Price: String? = null
    var closeCount1Default = 3
    var closeCount2Default = 3
    var closeCount1 = 3
    var closeCount2 = 3
    var weightPercent1 = 0
    var weightPercent2 = 0

    /***
     * 清运前重量
     */
    var weightClearBefore1: String? = "0.00"
    var weightClearBefore2: String? = "0.00"

    /***
     * 清运后重量
     */
    var weightClearAfter1: String? = "0.00"
    var weightClearAfter2: String? = "0.00"

    //当前前格一重量
    var curG1Weight: String? = "0.00"

    //当前前格二重量
    var curG2Weight: String? = "0.00"

    //格口一 物品 未上称的重量
    var weight1Before: String? = "0.00"
    var weightBeforeOpen1: String? = "0.00"

    //格口一 物品 已上称的重量中
    var weight1AfterIng: String? = "0.00"

    //格口一 物品 已上称的重量
    var weight1AfterEnd: String? = "0.00"

    //格口二 物品 未上称的重量
    var weight2Before: String? = "0.00"
    var weightBeforeOpen2: String? = "0.00"

    //格口一 物品 已上称的重量中
    var weight2AfterIng: String? = "0.00"

    //格口二 物品 已上称的重量
    var weight2AfterEnd: String? = "0.00"

    //当前格一可投递重量
    var curG1VotWeight: String? = "60.00"

    //当前格二可投递重量
    var curG2VotWeight: String? = "60.00"

    //当前当前格一总重量
    var curG1TotalWeight: String = "60.00"

    //当前当前格二总重量
    var curG2TotalWeight: String = "60.00"

    //读取配置信息
    var configEntity: ConfigEntity? = null

    //当前sn
    var curSn = ""

    //当前事务ID
    var curTransId = ""

    //相册事务id
    var curPhotoTransId = ""

    //远程拍照
    var adminTransId = ""

    //用户ID
    var curUserId = ""

    //格口一
    var cur1Cabinld = ""

    //格口二
    var cur2Cabinld = ""


    fun setRefreshHomeRes(msg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            netRefreshHomeRes.emit(msg)
        }
    }

    /***************************************** 发送 启动格口开门 查询投口门状态 查询格口重量 ******************************************/

    /***
     * 获取可投重量格口一
     */
    fun getVot1Weight(): String {
        var keTou = curG1TotalWeight
        if (weightPercent1 > 0) {
            val wp = CalculationUtil.divideFloats(weightPercent1.toString(), "100")
            //以服务器百分比换算后的重量
            keTou = CalculationUtil.multiplyFloats(curG1TotalWeight ?: "0.00", wp)
        }
        return containersDB[0]?.weigh?.let {
            CalculationUtil.subtractFloats(keTou, it.toString())
        } ?: "0.00"
    }

    /***
     * 获取可投重量格口二
     */
    fun getVot2Weight(): String {
        var keTou = curG2TotalWeight
        if (weightPercent2 > 0) {
            val wp = CalculationUtil.divideFloats(weightPercent2.toString(), "100")
            //以服务器百分比换算后的重量
            keTou = CalculationUtil.multiplyFloats(curG2TotalWeight ?: "0.00", wp)
        }
        return containersDB[1]?.weigh?.let {
            CalculationUtil.subtractFloats(keTou, it.toString())
        } ?: "0.00"
    }

    /**
     * 初始化保存网络数据
     * @param loginModel
     * @param oneInit 是否初始化
     */
    fun saveSocketInitData(loginModel: ConfigBean, oneInit: Boolean) {
        Loge.e("调试socket saveSocketInitData ${Thread.currentThread().name}")
        viewModelScope.launch(Dispatchers.IO) {
            devWeiChaMapSend[0] = false
            devWeiChaMapSend[1] = false
            flowAgainLogin.value = true//标记登录成功
            Loge.e("调试socket saveSocketInitData ioScope ${Thread.currentThread().name}")
            val heartbeatIntervalMillis = loginModel.config.heartBeatInterval?.toLongOrNull() ?: 30L
            config?.heartbeatIntervalMillis = TimeUnit.SECONDS.toMillis(heartbeatIntervalMillis)
            Loge.e("调试socket saveSocketInitData 心跳秒：$heartbeatIntervalMillis")
            val config = loginModel.config
            //控制终端维护状态
            when (config.status) {
                //未运营
                -1 -> {
                    SPreUtil.put(
                        AppUtils.getContext(), SPreUtil.netStatusText1, BusType.BUS_MAINTAINING_END
                    )
                    SPreUtil.put(
                        AppUtils.getContext(), SPreUtil.netStatusText2, BusType.BUS_MAINTAINING_END
                    )
                    _refBusStaChannel.send(MonitorWeight().apply {
                        doorGeX = CmdCode.GE1
                        refreshType = RefBusType.REFRESH_TYPE_2
                        warningContent = BusType.BUS_MAINTAINING_END
                    })
                    _refBusStaChannel.send(MonitorWeight().apply {
                        doorGeX = CmdCode.GE2
                        refreshType = RefBusType.REFRESH_TYPE_2
                        warningContent = BusType.BUS_MAINTAINING_END
                    })
                }
                //维护
                0 -> {
                    SPreUtil.put(
                        AppUtils.getContext(), SPreUtil.netStatusText1, BusType.BUS_MAINTAINING
                    )
                    SPreUtil.put(
                        AppUtils.getContext(), SPreUtil.netStatusText2, BusType.BUS_MAINTAINING
                    )
                    _refBusStaChannel.send(MonitorWeight().apply {
                        doorGeX = CmdCode.GE1
                        refreshType = RefBusType.REFRESH_TYPE_2
                        warningContent = BusType.BUS_MAINTAINING
                    })
                    _refBusStaChannel.send(MonitorWeight().apply {
                        doorGeX = CmdCode.GE2
                        refreshType = RefBusType.REFRESH_TYPE_2
                        warningContent = BusType.BUS_MAINTAINING
                    })

                }
                //正常
                1 -> {
                    SPreUtil.put(
                        AppUtils.getContext(), SPreUtil.netStatusText1, BusType.BUS_MAINTAINING_END
                    )
                    SPreUtil.put(
                        AppUtils.getContext(), SPreUtil.netStatusText2, BusType.BUS_MAINTAINING_END
                    )
                    _refBusStaChannel.send(MonitorWeight().apply {
                        doorGeX = CmdCode.GE1
                        refreshType = RefBusType.REFRESH_TYPE_2
                        warningContent = BusType.BUS_MAINTAINING_END
                    })
                    _refBusStaChannel.send(MonitorWeight().apply {
                        doorGeX = CmdCode.GE2
                        refreshType = RefBusType.REFRESH_TYPE_2
                        warningContent = BusType.BUS_MAINTAINING_END
                    })
                }
            }
            /****
             * 这里处理业务当箱体重量达到100
             * overflow 箱体超重情况下
             * irOverflow 红外感应下超重
             */
            //打开灯光
            val setTurnOnLight = config.turnOnLight
            if (setTurnOnLight != null && setTurnOnLight.length == 4) {
                val lightOn = setTurnOnLight.replaceRange(2, 2, ":")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val canceLightOn = SPreUtil[AppUtils.getContext(), SPreUtil.lightOn, "0"]
                    TaskLightsScheduler.cancelDailyTask(
                        AppUtils.getContext(), "$canceLightOn", "canceLightOn"
                    )
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.lightOn, lightOn)
                    TaskLightsScheduler.scheduleDaily(AppUtils.getContext(), lightOn, "lightOn")

                }
            }

            //关闭灯光
            val setTurnOffLight = config.turnOffLight
            if (setTurnOffLight != null && setTurnOffLight.length == 4) {
                val lightOff = setTurnOffLight.replaceRange(2, 2, ":")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val canceLightOff = SPreUtil[AppUtils.getContext(), SPreUtil.lightOff, "0"]
                    TaskLightsScheduler.cancelDailyTask(
                        AppUtils.getContext(), "$canceLightOff", "canceLightOff"
                    )
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.lightOff, lightOff)
                    TaskLightsScheduler.scheduleDaily(AppUtils.getContext(), lightOff, "lightOff")
                }
            }
            //开-关时间区间 不处理这个字段
//            config.lightTime

            //保存基础配置信息
            loginModel.sn?.let { snCode ->
                Loge.e("调试socket saveSocketInitData 开始添加配置")
                SPreUtil.put(AppUtils.getContext(), SPreUtil.login_sn, snCode)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflow, config.overflow)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.irOverflow, config.irOverflow)
                SPreUtil.put(
                    AppUtils.getContext(), SPreUtil.debugPasswd, config.debugPasswd ?: "123456"
                )
                val saveConfig = ConfigEntity().apply {
                    sn = snCode
                    heartBeatInterval = config.heartBeatInterval
                    turnOnLight = config.turnOnLight
                    turnOffLight = config.turnOffLight
                    lightTime = config.lightTime
                    uploadPhotoURL = config.uploadPhotoURL
                    uploadLogURL = config.uploadLogURL
                    qrCode = config.qrCode
                    logLevel = config.logLevel
                    status = config.status
                    irOverflow = config.irOverflow
                    overflow = config.overflow
                    debugPasswd = config.debugPasswd
                    irDefaultState = config.irDefaultState
                    weightSensorMode = config.weightSensorMode
                    time = AppUtils.getDateYMDHMS()
                }
                curSn = snCode
                val queryConfig = DatabaseManager.queryInitConfig(AppUtils.getContext(), snCode)
                if (queryConfig == null) {
                    val row = DatabaseManager.insertConfig(AppUtils.getContext(), saveConfig)
                    configEntity = saveConfig
                    Loge.e("调试socket saveSocketInitData 添加配置 $row")
                } else {
                    queryConfig.heartBeatInterval = config.heartBeatInterval
                    queryConfig.heartBeatInterval = config.heartBeatInterval
                    queryConfig.turnOnLight = config.turnOnLight
                    queryConfig.turnOffLight = config.turnOffLight
                    queryConfig.lightTime = config.lightTime
                    queryConfig.uploadPhotoURL = config.uploadPhotoURL
                    queryConfig.uploadLogURL = config.uploadLogURL
                    queryConfig.qrCode = config.qrCode
                    queryConfig.logLevel = config.logLevel
                    queryConfig.status = config.status
                    queryConfig.debugPasswd = config.debugPasswd
                    queryConfig.irDefaultState = config.irDefaultState
                    queryConfig.weightSensorMode = config.weightSensorMode
                    queryConfig.irOverflow = config.irOverflow
                    queryConfig.overflow = config.overflow
                    val row = DatabaseManager.upConfigEntity(AppUtils.getContext(), saveConfig)
                    Loge.e("调试socket saveSocketInitData 更新配置 $row")
                    configEntity = queryConfig
                }
            }
            Loge.e("调试socket saveSocketInitData ${FileMdUtil.checkResFileExists("qrCode.png")}")
            val baseurl = config.uploadPhotoURL?.substringBeforeLast("/api") + "/api"
            Loge.e("调试socket saveSocketInitData 重设置http url $baseurl")
            if (baseurl != null && !TextUtils.isEmpty(baseurl)) {
                CorHttp.getInstance().updateBaseUrl(baseurl)
            }
            //是否构建二维码成功
            val isQrCode = SPreUtil[AppUtils.getContext(), SPreUtil.isQrCode, false] as Boolean
            if (oneInit) {//初始化重新构建二维码
                config.qrCode?.let { initQRCode(it) }
            } else if (!FileMdUtil.checkResFileExists("qrCode.png") || !isQrCode) {//不是初始化不存在则构建 或者 未构建成功
                config.qrCode?.let { initQRCode(it) }
            } else {
                downResBitmap(oneInit, "res/qrCode.png", 2, config.status)
            }
            //保存格口信息
            Loge.e("调试socket saveSocketInitData 开始保存格口信息 开始")
            val stateBox = mutableListOf<StateEntity>()
            var setVolume = 10
            loginModel.config.list?.let { lattices ->
                lattices.withIndex().forEach { (index, lattice) ->
                    Loge.e("调试socket saveSocketInitData 当前格口：${index} /价格：${lattice.price}")
                    when (index) {
                        0 -> {
                            curGePrice = lattice.price//初始化登录模式价格
                            cur1Cabinld = lattice.cabinId ?: ""//初始化登录模式格口编码
                            curG1Weight = lattice.weight
                            curG1TotalWeight = lattice.overweight ?: "60.00"
                            SPreUtil.put(AppUtils.getContext(), SPreUtil.saveIr1, lattice.ir)
                            weightPercent1 = lattice.weightPercent ?: 50
                            closeCount1Default = lattice.closeCount ?: 3
                            closeCount1 = closeCount1Default

                        }

                        1 -> {
                            curGePrice = lattice.price//初始化登录模式价格
                            cur2Cabinld = lattice.cabinId ?: ""//初始化登录模式格口编码
                            curG2Weight = lattice.weight
                            curG2TotalWeight = lattice.overweight ?: "60.00"
                            SPreUtil.put(AppUtils.getContext(), SPreUtil.saveIr2, lattice.ir)
                            weightPercent2 = lattice.weightPercent ?: 50
                            closeCount2Default = lattice.closeCount ?: 3
                            closeCount2 = closeCount2Default

                        }
                    }
                    val saveConfig = LatticeEntity().apply {
                        cabinId = lattice.cabinId
                        capacity = lattice.capacity
                        createTime = lattice.createTime
                        delFlag = lattice.delFlag
                        doorStatus = lattice.doorStatus
                        filledTime = lattice.filledTime
                        netId = lattice.id
                        ir = lattice.ir
                        overweight = lattice.overweight
                        price = lattice.price
                        rodHinderValue = lattice.rodHinderValue
                        sn = lattice.sn
                        smoke = lattice.smoke
                        sort = lattice.sort
                        sync = lattice.sync
                        volume = lattice.volume
                        closeCount = lattice.closeCount ?: 3
                        weightPercent = lattice.weightPercent ?: 50
                        weight = lattice.weight
                        weightMonitor = lattice.weight
                        time = AppUtils.getDateYMDHMS()
                    }
                    setVolume = lattice.volume
                    val queryLattice = lattice.cabinId?.let { cabinId ->
                        DatabaseManager.queryLatticeEntity(
                            AppUtils.getContext(), cabinId
                        )
                    }
                    if (queryLattice == null) {
                        val rowCabin =
                            DatabaseManager.insertLattice(AppUtils.getContext(), saveConfig)
                        Loge.e("调试socket saveSocketInitData 添加格口信息 $rowCabin")
                        val setCapacity = lattice.capacity?.toInt() ?: 0
                        val setIrState = lattice.ir
                        val setWeigh = lattice.weight?.toFloat() ?: 0.00f
                        val setCabinId = lattice.cabinId ?: ""
                        val state = StateEntity().apply {
                            smoke = 0
                            capacity = setCapacity
                            irState = setIrState
                            weigh = setWeigh
                            doorStatus = 0
                            lockStatus = 0
                            cabinId = setCabinId
                            time = AppUtils.getDateYMDHMS()
                        }
                        stateBox.add(state)
                    } else {
                        queryLattice.cabinId = lattice.cabinId
                        queryLattice.capacity = lattice.capacity
                        queryLattice.createTime = lattice.createTime
                        queryLattice.delFlag = lattice.delFlag
                        queryLattice.doorStatus = lattice.doorStatus
                        queryLattice.filledTime = lattice.filledTime
                        queryLattice.netId = lattice.id
                        queryLattice.ir = lattice.ir
                        queryLattice.overweight = lattice.overweight
                        queryLattice.price = lattice.price
                        queryLattice.rodHinderValue = lattice.rodHinderValue
                        queryLattice.sn = lattice.sn
                        queryLattice.smoke = lattice.smoke
                        queryLattice.sort = lattice.sort
                        queryLattice.sync = lattice.sync
                        queryLattice.volume = lattice.volume
                        queryLattice.closeCount = lattice.closeCount ?: 3
                        queryLattice.weightPercent = lattice.weightPercent ?: 50
                        queryLattice.weight = lattice.weight
                        queryLattice.weightDefault = lattice.weight
                        val rowCabin =
                            DatabaseManager.upLatticeEntity(AppUtils.getContext(), queryLattice)
                        Loge.e("调试socket saveSocketInitData 更新格口信息 $rowCabin")

                        //拿出当前心跳格口信息
                        val upperMachines = DatabaseManager.queryStateList(AppUtils.getContext())
                        upperMachines.withIndex().forEach { (index, states) ->
                            when (index) {
                                0 -> {
                                    containersDB.add(states)
                                    cur1Cabinld = states.cabinId ?: ""//初始化读取db格口编码
                                    curG1Weight = states.weigh.toString()
                                    if (!oneInit) {
//                                        restartAppCloseDoor(CmdCode.GE1)
                                    }
                                }

                                1 -> {
                                    containersDB.add(states)
                                    cur2Cabinld = states.cabinId ?: ""//初始化读取db格口编码
                                    curG2Weight = states.weigh.toString()
                                    if (!oneInit) {
//                                        restartAppCloseDoor(CmdCode.GE2)
                                    }
                                }
                            }
                        }
                    }
                }
                //配置音量
                MediaPlayerHelper.setVolume(AppUtils.getContext(), setVolume)
                for (state in stateBox) {
                    val rowState = DatabaseManager.insertState(AppUtils.getContext(), state)
                    Loge.e("调试socket saveSocketInitData 添加心跳信息 $rowState")
                }
            }

            //保存资源配置
            Loge.e("调试socket saveSocketInitData 开始加载资源")
            loginModel.config.resourceList?.let { resources ->
                for (resource in resources) {
                    val saveResource = ResEntity().apply {
                        filename = resource.filename
                        url = resource.url
                        md5 = resource.md5
                        time = AppUtils.getDateYMDHMS()
                    }

                    val queryResource =
                        DatabaseManager.queryResName(AppUtils.getContext(), resource.filename ?: "")
                    if (queryResource == null) {
                        val row = DatabaseManager.insertRes(AppUtils.getContext(), saveResource)
                        Loge.e("调试socket saveSocketInitData 添加资源 $row")
                        delay(500)
                        if (resource.url != null && !TextUtils.isEmpty(resource.url) && resource.filename != null && !TextUtils.isEmpty(
                                resource.filename
                            )) {
                            val fileName = resource.filename ?: ""
                            var dir = FileMdUtil.matchNewFileName("audio", fileName)
                            if (FileMdUtil.shouldAudio(fileName)) {
                                dir = FileMdUtil.matchNewFileName("audio", fileName)
                            } else if (FileMdUtil.shouldPGJ(fileName)) {
                                dir = FileMdUtil.matchNewFileName("res", fileName)
                            }
                            resource.url?.let { dowurl ->
                                downloadRes(dowurl, dir) { success, file ->//资源下载 未存储
                                    if (success) {
                                        Loge.e("测试我来了 刷新背景图 下载图片 ${resource.filename}")
                                        when (resource.filename) {
                                            "home.png" -> {
                                                if (FileMdUtil.checkResFileExists("home.png")) {
                                                    downResBitmap(
                                                        oneInit, "res/${resource.filename}", 1, config.status
                                                    )
                                                }
                                            }

                                            "maintaining.png" -> {
                                                if (FileMdUtil.checkResFileExists("maintaining.png")) {
                                                    downResBitmap(
                                                        oneInit, "res/${resource.filename}", 3, config.status
                                                    )
                                                }
                                            }
                                        }
                                        upNetResDb("下载资源成功插入", ResEntity().apply {
                                            id = row
                                            status = ResType.TYPE_0
                                            filename = resource.filename
                                            url = resource.url
                                            md5 = resource.md5
                                            time = AppUtils.getDateYMDHMS()
                                        })
                                    } else {
                                        upNetResDb("下载资源失败插入", ResEntity().apply {
                                            id = row
                                            status = ResType.TYPE_4
                                            filename = resource.filename
                                            url = resource.url
                                            md5 = resource.md5
                                            time = AppUtils.getDateYMDHMS()
                                        })
                                    }
                                }
                            }
                        } else {
                            Loge.e("下载资源 字段为空 $row")
                        }
                    } else {
                        Loge.e("测试我来了 刷新背景图 更新图片 ${resource.filename} ")
                        Loge.e("测试我来了 刷新背景图 md5 ${queryResource.md5} | ${resource.md5} ")
                        if (queryResource.md5 != resource.md5) {
                            queryResource.filename = resource.filename
                            queryResource.url = resource.url
                            queryResource.md5 = resource.md5
                            queryResource.time = AppUtils.getDateYMDHMS()
                            val fileName = resource.filename ?: ""
                            var dir = FileMdUtil.matchNewFileName("audio", fileName)
                            if (FileMdUtil.shouldAudio(fileName)) {
                                dir = FileMdUtil.matchNewFileName("audio", fileName)
                            } else if (FileMdUtil.shouldPGJ(fileName)) {
                                dir = FileMdUtil.matchNewFileName("res", fileName)
                            }
                            queryResource.url?.let { url ->
                                downloadRes(url, dir) { success, file ->//资源下载
                                    if (success) {
                                        Loge.e("测试我来了 刷新背景图 更新图片 ${queryResource.filename}")
                                        when (queryResource.filename) {
                                            "home.png" -> {
                                                if (FileMdUtil.checkResFileExists("home.png")) {
                                                    downResBitmap(
                                                        oneInit, "res/${resource.filename}", 1, config.status
                                                    )
                                                }
                                            }

                                            "maintaining.png" -> {
                                                if (FileMdUtil.checkResFileExists("maintaining.png")) {
                                                    downResBitmap(
                                                        oneInit, "res/${resource.filename}", 3, config.status
                                                    )
                                                }
                                            }
                                        }
                                        queryResource.status = 0
                                        upNetResDb("下载资源成功更新", queryResource)
                                        //这里同步去刷新ui
                                    } else {
                                        queryResource.status = 4
                                        upNetResDb("下载资源失败更新", queryResource)
                                    }
                                }
                            }
                        } else {
                            when (queryResource.filename) {
                                "home.png" -> {
                                    if (FileMdUtil.checkResFileExists("home.png")) {
                                        downResBitmap(
                                            oneInit, "res/${resource.filename}", 1, config.status
                                        )
                                    }
                                }

                                "maintaining.png" -> {
                                    if (FileMdUtil.checkResFileExists("maintaining.png")) {
                                        downResBitmap(
                                            oneInit, "res/${resource.filename}", 3, config.status
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Loge.e("调试socket saveSocketInitData 启动页面")
            if (!oneInit) {
                //发送心跳
                sendHeartbeat()
                delay(2000)
                Loge.e("调试socket saveSocketInitData 开始启动页面")
                flowLoginCmd.emit(true)
            }
        }
    }

    /****
     * @param path 存储路径·
     * @param res 类型 1.首页 2.二维码
     * @param status 控制终端维护状态 -1.未运营 0.维护
     */
    private fun downResBitmap(oneInit: Boolean, path: String, res: Int, status: Int) {
        val options = RequestOptions().skipMemoryCache(true) // 禁用内存缓存
            .diskCacheStrategy(DiskCacheStrategy.NONE) // 禁用磁盘缓存
        Glide.with(AppUtils.getContext()).asBitmap()
            .load(File("${AppUtils.getContext().filesDir}/${path}")).apply(options)
            .into(object : CustomTarget<Bitmap?>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap?>?,
                ) {
                    if (res == 1) {
                        mHomeBg = resource
                        if (oneInit) {
                            //刷新
//                            setCabinetStatus1(BusType.BUS_REFRESH_DATA)
//                            setCabinetStatus2(BusType.BUS_REFRESH_DATA)
                            setRefreshHomeRes(BusType.BUS_REFRESH_HOME_RES)
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_REFRESH_DATA
                                doorGeX = CmdCode.GE1
                            })
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_REFRESH_DATA
                                doorGeX = CmdCode.GE2
                            })

                        }
                    } else if (res == 2) {
                        mQrCode = resource

                    } else if (res == 3) {
                        mMaintaining = resource
                        when (status) {
                            -1 -> {
//                                setCabinetStatus1(BusType.BUS_MAINTAINING)
                                setRefBusStaChannel(MonitorWeight().apply {
                                    refreshType = RefBusType.REFRESH_TYPE_2
                                    warningContent = BusType.BUS_MAINTAINING
                                    doorGeX = CmdCode.GE1
                                })


                            }

                            0 -> {
//                                setCabinetStatus1(BusType.BUS_MAINTAINING)
                                setRefBusStaChannel(MonitorWeight().apply {
                                    refreshType = RefBusType.REFRESH_TYPE_2
                                    warningContent = BusType.BUS_MAINTAINING
                                    doorGeX = CmdCode.GE2
                                })
                            }
                        }
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // 清理资源
                }
            })
    }

    private fun initQRCode(qrcode: String) {
        //创建二维码
        val logoBitmap = BitmapFactory.decodeResource(
            AppUtils.getContext().resources, com.recycling.toolsapps.R.mipmap.ic_launcher_by
        )
        val bg = BitmapFactory.decodeResource(
            AppUtils.getContext().resources, com.recycling.toolsapps.R.color.black
        )
        AwesomeQRCode.Renderer().contents(qrcode).background(bg).size(1080) // 增加尺寸以提高可扫描性
            .roundedDots(true).dotScale(0.6f) // 增加点的大小
            .colorDark(Color.BLACK) // 深色部分为黑色
            .colorLight(Color.WHITE) // 浅色部分为白色 - 这是关键修复
            .whiteMargin(true).margin(20) // 增加边距
            .logo(logoBitmap).logoMargin(10).logoRadius(10).logoScale(0.15f) // 减小logo尺寸，避免遮挡关键信息
            .renderAsync(object : AwesomeQRCode.Callback {
                override fun onError(renderer: AwesomeQRCode.Renderer, e: Exception) {
                    Loge.e("调试socket saveSocketInitData 创建二维码失败")
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.isQrCode, false)
                    e.printStackTrace()
                }

                override fun onRendered(renderer: AwesomeQRCode.Renderer, bitmap: Bitmap) {
                    mQrCode = bitmap
                    FileMdUtil.saveBitmapToInternalStorage(bitmap, "qrCode.png")
                    Loge.e("调试socket saveSocketInitData 创建二维码成功 保存开始 ${Thread.currentThread().name}")
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.isQrCode, true)

                }
            })
    }

    //下载
    var singleDownloader: SingleDownloader? = null

    /***
     * 取消下载
     */
    fun downloadCancel() {
        singleDownloader?.cancel()
    }

    /***
     * 下载版本
     *
     */
    fun downloadRes(downloadUrl: String, filePath: String, callback: (Boolean, File?) -> Unit) {
        singleDownloader = SingleDownloader(CorHttp.getInstance().getClient())
        singleDownloader?.onStart {
            Loge.d("网络请求 downloadRes onStart $downloadUrl")

        }?.onProgress { current, total, progress ->
            Loge.d("网络请求 downloadRes onProgress $current $total $progress")

        }?.onSuccess { url, file ->
            Loge.d("网络请求 downloadRes onSuccess $url ${file.path} $")
            callback(true, file)
        }?.onError { url, cause ->
            Loge.d("网络请求 downloadRes onError $url ${cause.message} ")
            callback(false, null)
        }?.onCompletion { url, filePath ->
            Loge.d("网络请求 downloadRes onCompletion $url $filePath ")

        }?.excute(downloadUrl, filePath)

    }

    /******************************************* socket通信 *************************************************/

    /*******************************************下位机通信部分*************************************************/

    /***
     * @see 1.投送门开门异常 111 121
     * @see 2.投递门关门异常 110 120
     * @see 3.清运门开门异常 311 321 331
     * @see 4.清运门关门异常 410 420 430
     * @see 5.摄像头异常 51 52
     * @see 6.电磁锁异常
     * @see 7:内灯异常
     * @see 8:外灯异常
     * @see 9:推杆异常 91 92
     * @see 11:1红外满溢
     * @see 12:2红外满溢
     * @see 211:超重满溢
     * @see 212:超重满溢
     * @see 5101:格口一校准状态
     * @see 5102:格口一故障状态
     * @see 5201:格口二校准状态
     * @see 5202:格口二故障状态
     */
    var maptDoorFault = mutableMapOf<Int, Boolean>()

    /**
     * 处理设备重量浮动变动超过0.5kg上报
     */
    var devWeiChaMapCun = mutableMapOf<Int, String>()

    /***
     * 是否发送
     */
    var devWeiChaMapSend = mutableMapOf<Int, Boolean>()

    /***
     * 定时轮询查询信号
     */
    fun pollingReadSignal() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30000)
//                delay(60000)//1分钟
                flowIsReadSignal.emit(1)
            }
        }
    }

    /***
     * 定时轮询查询异常
     */
    fun pollingFault() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                //故障
                var doorT1Abnormal = false
                //故障
                var doorT2Abnormal = false
                //满溢
                var doorT1Overflow = false
                //满溢
                var doorT2Overflow = false
                maptDoorFault.forEach { (key, value) ->
                    Loge.e("模拟故障满溢状态 $key ${EnumFaultState.getDescByCode(key)} - $value")
                    val postValue = when (key) {
                        FaultType.FAULT_CODE_111 -> {
                            if (value) "投送门一开门异常" else null
                        }

                        FaultType.FAULT_CODE_121 -> {
                            if (value) "投送门二开门异常" else null
                        }

                        2 -> {
                            if (value) "投递门关门异常" else null
                        }

                        FaultType.FAULT_CODE_110 -> {
                            if (value) "投递门一关门异常" else null
                        }

                        FaultType.FAULT_CODE_120 -> {
                            if (value) "投递门二关门异常" else null
                        }

                        3 -> {
                            if (value) "清运门开门异常" else null
                        }

                        FaultType.FAULT_CODE_311 -> {
                            if (value) "清运门一开门异常" else null
                        }

                        FaultType.FAULT_CODE_321 -> {
                            if (value) "清运门二开门异常" else null
                        }

                        FaultType.FAULT_CODE_331 -> {
                            if (value) "清运门三开门异常" else null
                        }

                        4 -> {
                            if (value) "清运门关门异常" else null
                        }

                        310 -> {
                            if (value) "清运门一关门异常" else null
                        }

                        320 -> {
                            if (value) "清运门二关门异常" else null
                        }

                        330 -> {
                            if (value) "清运门三关门异常" else null
                        }

                        FaultType.FAULT_CODE_5 -> {
                            if (value) "摄像头异常" else null
                        }

                        FaultType.FAULT_CODE_51 -> {
                            if (value) "内摄像头异常" else null
                        }

                        FaultType.FAULT_CODE_52 -> {
                            if (value) "外摄像头异常" else null
                        }

                        FaultType.FAULT_CODE_6 -> {
                            if (value) "电磁锁异常" else null
                        }

                        FaultType.FAULT_CODE_7 -> {
                            if (value) "内灯异常" else null
                        }

                        FaultType.FAULT_CODE_8 -> {
                            if (value) "外灯异常" else null
                        }

                        FaultType.FAULT_CODE_91 -> {
                            if (value) {
                                doorT1Abnormal = true
                                "推杆一异常"
                            } else {
                                doorT1Abnormal = false
                                null
                            }
                        }

                        FaultType.FAULT_CODE_92 -> {
                            if (value) {
                                doorT2Abnormal = true
                                "推杆二异常"
                            } else {
                                doorT2Abnormal = false
                                null
                            }

                        }

//                        FaultType.FAULT_CODE_11 -> {
//                            if (value) {
//                                doorT1Overflow = true
//                                "格口一满溢"
//                            } else {
//                                doorT1Overflow = false
//                                null
//                            }
//                        }
//
//                        FaultType.FAULT_CODE_12 -> {
//                            if (value) {
//                                doorT2Overflow = true
//                                "格口二满溢"
//                            } else {
//                                doorT2Overflow = false
//                                null
//                            }
//                        }

                        FaultType.FAULT_CODE_2110 -> {
                            if (value) {
                                doorT1Overflow = true
                                "格口一满溢网络下发"
                            } else {
                                doorT1Overflow = false
                                null
                            }
                        }

                        FaultType.FAULT_CODE_2120 -> {
                            if (value) {
                                doorT2Overflow = true
                                "格口二满溢网络下发"
                            } else {
                                doorT2Overflow = false
                                null
                            }
                        }

                        FaultType.FAULT_CODE_211 -> {
                            //红外满溢+可超重量
                            val irOverflow =
                                SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                            val isTouOverflow =
                                if (maptDoorFault[FaultType.FAULT_CODE_11] == true) {
                                    //格口1 当前重量小于服务器红外满溢则true
                                    val bl1 = CalculationUtil.isLess(
                                        curG1Weight ?: "0.00", irOverflow.toString()
                                    )
                                    if (bl1) {
                                        maptDoorFault[FaultType.FAULT_CODE_211] = false
                                        true
                                    } else {
                                        maptDoorFault[FaultType.FAULT_CODE_211] = true
                                        false
                                    }
                                } else {
                                    true
                                }
                            if (isTouOverflow) null else "格口一超重"
                        }

                        FaultType.FAULT_CODE_212 -> {
                            //红外满溢+可超重量
                            val irOverflow =
                                SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                            val isTouOverflow =
                                if (maptDoorFault[FaultType.FAULT_CODE_12] == true) {
                                    //格口2 当前重量小于服务器红外满溢则true
                                    val bl2 = CalculationUtil.isLess(
                                        curG2Weight ?: "0.00", irOverflow.toString()
                                    )
                                    if (bl2) {
                                        maptDoorFault[FaultType.FAULT_CODE_212] = false
                                        true
                                    } else {
                                        maptDoorFault[FaultType.FAULT_CODE_212] = true
                                        false
                                    }
                                } else {
                                    true
                                }
                            if (isTouOverflow) null else "格口二超重"
                        }

                        else -> {
                            null
                        }
                    }
                    postValue?.let { pValue ->
                        Loge.e("模拟故障满溢状态 定时器 执行 $pValue")
                        toGoCmdUpFault(key, 0, pValue)
                    }

                }
                Loge.e("模拟故障满溢状态 $doorT1Overflow - $doorT1Abnormal")
                //先满溢再故障
                if (doorT1Overflow) {
                    if (doorT1Abnormal) {
                        _refBusStaChannel.send(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_FAULT
                        })
                    } else {
                        _refBusStaChannel.send(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_OVERFLOW
                        })
                    }
                } else {
                    if (doorT1Abnormal) {
                        _refBusStaChannel.send(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_FAULT
                        })
                    } else {
                        _refBusStaChannel.send(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_NORMAL
                        })
                    }
                }
                if (doorT2Overflow) {
                    if (doorT2Abnormal) {
                        _refBusStaChannel.send(MonitorWeight().apply {
                            doorGeX = CmdCode.GE2
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_FAULT
                        })
                    } else {
                        _refBusStaChannel.send(MonitorWeight().apply {
                            doorGeX = CmdCode.GE2
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_OVERFLOW
                        })
                    }
                } else {
                    if (doorT2Abnormal) {
                        _refBusStaChannel.send(MonitorWeight().apply {
                            doorGeX = CmdCode.GE2
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_FAULT
                        })
                    } else {
                        _refBusStaChannel.send(MonitorWeight().apply {
                            doorGeX = CmdCode.GE2
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_NORMAL
                        })
                    }
                }
                delay(20000L)
            }
        }
    }

    /*******************************************下位机通信部分*************************************************/
    /***
     * 下载主芯片版本名称
     */
    var chipName = "F1-20260403.bin"

    /***
     * 下载主芯片版本大小
     */
    var chipDowV = 20260403

    /***
     * 当前主芯片版本大小
     */
    var chipCurV = 20260401

    //统计能发送次数
    var sendFCount = 0

    //固件升级倒计时重启 固件
    private val firmwareTimer = CountdownTimer(viewModelScope)
    val countdownState = firmwareTimer.countdownState

    fun firmwareStartTimer(seconds: Int) {
        firmwareTimer.startCountdown(seconds)
    }

    fun firmwarePauseTimer() {
        firmwareTimer.pauseCountdown()
    }

    fun firmwareResetTimer() {
        firmwareTimer.resetCountdown()
    }

    //称重倒计时
    private val deliveryTimer = DeliveryTimer(viewModelScope)
    val deliveryState = deliveryTimer.countdownState

    fun deliveryStartTimer(seconds: Int) {
        deliveryTimer.startCountdown(seconds)
    }

    fun deliveryPauseTimer() {
        deliveryTimer.pauseCountdown()
    }

    fun deliveryResetTimer() {
        deliveryTimer.resetCountdown()
    }

    fun deliverycancelTimer() {
        deliveryTimer.cancelCountdown()
    }

    //清运倒计时
    private val clearTimer = ClearTimer(viewModelScope)
    val clearState = clearTimer.countdownState

    fun clearStartTimer(seconds: Int) {
        clearTimer.startCountdown(seconds)
    }

    fun clearPauseTimer() {
        clearTimer.pauseCountdown()
    }

    fun clearResetTimer() {
        clearTimer.resetCountdown()
    }

    fun clearcancelTimer() {
        clearTimer.cancelCountdown()
    }

    /*******************************************http模块*************************************************/

    /***
     * 上传日志
     */
    fun uploadLog(sn: String, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val post = mutableMapOf<String, Any>()
            post["sn"] = sn
            post["file"] = file
            httpRepo.uploadLog(post).onSuccess { user ->
                Loge.d("网络请求 日志上传 onSuccess ${Thread.currentThread().name} ${user.toString()}")
                toGoCmdLog()
                insertInfoLog(LogEntity().apply {
                    cmd = activeType
                    msg = "$sn,onSuccess"
                    time = AppUtils.getDateYMDHMS()
                })

            }.onFailure { code, message ->
                Loge.d("网络请求 日志上传 onFailure $code $message")
                insertInfoLog(LogEntity().apply {
                    cmd = activeType
                    msg = "$sn,onFailure"
                    time = AppUtils.getDateYMDHMS()
                })

            }.onCatch { e ->
                Loge.d("网络请求 日志上传 onCatch ${e.errorMsg}")
                insertInfoLog(LogEntity().apply {
                    cmd = activeType
                    msg = "$sn,onCatch"
                    time = AppUtils.getDateYMDHMS()
                })
            }
        }
    }

    /***
     * 上传拍照
     */
    fun uploadPhoto(
        sn: String,
        transId: String,
        photoType: Int = -1,
        fileName: String,
        activeType: String,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            println("网络请求 拍照上传 进来 $sn $transId $photoType $fileName $activeType")
            if (activeType == "45") {
                Loge.d("网络请求 拍照上传 延迟")
                toGoTPSucces(transId)
                delay(3000)

            }
            val post = mutableMapOf<String, Any>()
            post["sn"] = sn
            post["transId"] = transId
            post["photoType"] = photoType
            val file = File(
                AppUtils.getContext()
                    .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.path + "/action/${fileName}"
            )
            println("网络请求 拍照上传 进来 ${file.name} | ${file.absolutePath}")
            post["file"] = file
            httpRepo.uploadPhoto(post).onSuccess { user ->
                Loge.d("网络请求 拍照上传 onSuccess ${Thread.currentThread().name} ${user.toString()}")
                insertInfoLog(LogEntity().apply {
                    cmd = activeType
                    msg = "$transId,onFileSuccess"
                    time = AppUtils.getDateYMDHMS()
                }, false)


            }.onFailure { code, message ->
                Loge.d("网络请求 拍照上传 onFailure $code $message")
                insertInfoLog(LogEntity().apply {
                    cmd = activeType
                    msg = "$transId,onFileFailure"
                    time = AppUtils.getDateYMDHMS()
                })

            }.onCatch { e ->
                Loge.d("网络请求 拍照上传 onCatch ${e.errorMsg}")
                insertInfoLog(LogEntity().apply {
                    cmd = activeType
                    msg = "$transId,onFileCatch"
                    time = AppUtils.getDateYMDHMS()
                })
            }
        }
    }

    /***
     * 插入日志记录
     * @param logInfoEntity
     * @param completion false 记录 true 不记录
     */
    fun insertInfoLog(logInfoEntity: LogEntity, completion: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!completion) {
                if (logInfoEntity.msg?.contains("onFile") == true) {
                    BoxToolLogUtils.recordSocket(CmdValue.CONNECTING, "home,${logInfoEntity.msg}")
                }
            }
            DatabaseManager.insertLog(AppUtils.getContext(), logInfoEntity)
        }
    }
    /*******************************************http模块*************************************************/

    /*******************************************socket模块*************************************************/


    fun saveRecordSocket(type: String, json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            BoxToolLogUtils.recordSocket(type, json)
        }
    }

    data class Config(
        val host: String,
        val port: Int,
        val connectTimeoutMillis: Long = TimeUnit.SECONDS.toMillis(30),
        val readTimeoutMillis: Int = TimeUnit.SECONDS.toMillis(90).toInt(),
        val writeFlushIntervalMillis: Long = 0L,
        var heartbeatIntervalMillis: Long = TimeUnit.SECONDS.toMillis(30),
        val heartbeatPayload: ByteArray = byteArrayOf(),
        val idleTimeoutMillis: Long = TimeUnit.MINUTES.toMillis(2),
        val minReconnectDelayMillis: Long = 500,
        val maxReconnectDelayMillis: Long = TimeUnit.SECONDS.toMillis(30),
        val reconnectBackoffMultiplier: Double = 2.0,
        val maxSendQueueBytes: Int = 1_048_576,
        val maxFrameSizeBytes: Int = 4 * 1024 * 1024,
    )

    var config: Config? = null

    fun initConfigSocket(initHost: String, initPort: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            config = Config(
                host = initHost, port = initPort, heartbeatIntervalMillis = 20, heartbeatPayload = "PING".toByteArray()
            )
            start()
        }
    }

    /***
     *  START  启动
     *  DISCONNECTED  已断开连接
     *  CONNECTING  正在连接
     *  CONNECTED  已连接
     */
    enum class ConnectionState { START, DISCONNECTED, CONNECTING, CONNECTED }

    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 64)
    val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    private val sendQueueByte = Channel<ByteArray>(capacity = Channel.BUFFERED)

    @Volatile
    private var socket: Socket? = null
    private val socketMutex = Mutex()

    @Volatile
    private var lastReceivedAtMillis: Long = System.currentTimeMillis()

    @Volatile
    private var running = false

    /***
     * 启动socket连接
     */
    suspend fun start() {
        Loge.e("出厂配置 initSocket SocketClient start running $running $clientScope")
        if (running) return
        running = true
        _state.value = START
        clientScope.launch { runMainLoop() }
    }

    /***
     * 关闭socket连接
     */
    suspend fun stop() {
        Loge.e("出厂配置 initSocket SocketClient stop ")
        running = false
        try {
            clientScope.coroutineContext.job.cancelAndJoin()
        } catch (e: CancellationException) {
            Loge.e("出厂配置 initSocket SocketClient stop ${e.message}")
        }
        closeSocketQuietly()
    }

    /***
     * @param text
     * 发送字节
     */
    suspend fun send(data: ByteArray) {
//        Loge.e("出厂配置 initSocket SocketClient send ByteArray  ${ByteUtils.toHexString(data)}")
        require(data.size <= config?.maxFrameSizeBytes!!) { "Frame too large: ${data.size}" }
        // Backpressure control by counting queued bytes
        enqueueSend(data)
    }

    /***
     * @param text
     * 发送字符串
     */
    suspend fun sendText(text: String) {
//        Loge.e("出厂配置 initSocket SocketClient sendText  $text")
        send(text.toByteArray())
    }

    /***
     * 调查send
     * @param data
     *
     */
    private suspend fun enqueueSend(data: ByteArray) {
//        Loge.e("出厂配置 initSocket SocketClient enqueueSend  ${ByteUtils.toHexString(data)}")
        // Simple soft limit enforcement by suspending when over budget
        val queuedBytes = data.size
        if (queuedBytes > config?.maxSendQueueBytes!!) {
            throw IOException("Send queue bytes over limit")
        }
        sendQueueByte.send(data)
    }

    /***
     * 运行主循环
     */
    private suspend fun runMainLoop() {
        var attempt = 0
        Loge.e("出厂配置 initSocket SocketClient runMainLoop $running ${clientScope.isActive}")
        while (running && clientScope.isActive) {
            try {
                _state.value = CONNECTING
                Loge.e("出厂配置 initSocket SocketClient runMainLoop 连接中")
                connectAndServe()
                attempt = 0 // reset backoff after successful session
            } catch (e: CancellationException) {
                Loge.e("出厂配置 initSocket SocketClient runMainLoop catch1 ${e.message} running $running")
                BoxToolLogUtils.recordSocket(
                    CmdValue.CONNECTING, "socketClientrunMainLoop catch1 ${e.message} running $running"
                )
                break
            } catch (e: Exception) {
                // Swallow and backoff
                Loge.e("出厂配置 initSocket SocketClient runMainLoop catch2 ${e.message} running $running")
                BoxToolLogUtils.recordSocket(
                    CmdValue.CONNECTING, "socketClient,runMainLoop catch2 ${e.message} running $running"
                )
            } finally {
                Loge.e("出厂配置 initSocket SocketClient runMainLoop finally running $running")
                closeSocketQuietly()
                if (!running) break
                _state.value = DISCONNECTED
            }

            attempt += 1
//            val delayMs = computeReconnectDelay(attempt)
//            Loge.e("出厂配置 initSocket SocketClient runMainLoop 重连接延迟 $delayMs")
            Loge.e("出厂配置 initSocket SocketClient runMainLoop 重连接延迟 ")
            delay(10000L)
        }
    }

    /***
     * 计算重新连接延迟
     * @param attempt
     */
    private fun computeReconnectDelay(attempt: Int): Long {
        Loge.e("出厂配置 initSocket SocketClient computeReconnectDelay attempt $attempt")
        val base = (config?.minReconnectDelayMillis
            ?: 500) * config?.reconnectBackoffMultiplier!!.pow((attempt - 1).toDouble())
        val clamped = config?.maxReconnectDelayMillis?.toDouble()?.let { min(base, it).toLong() }
        val jitter = (clamped!! * 0.2 * Random.nextDouble()).toLong()
        return clamped + jitter
    }

    var input: BufferedInputStream? = null
    var output: BufferedOutputStream? = null
    var readerJob: Job? = null
    var writerJob: Job? = null
    var monitorJob: Job? = null

    /***
     * 连接和服务
     */
    private suspend fun connectAndServe() {
        Loge.e("出厂配置 initSocket SocketClient connectAndServe")
        val s = Socket()
        s.tcpNoDelay = true
        s.soTimeout = config?.readTimeoutMillis!!
        s.connect(
            config?.port?.let { InetSocketAddress(config?.host, it) }, config?.connectTimeoutMillis?.toInt()!!
        )

        socketMutex.withLock { socket = s }

        lastReceivedAtMillis = System.currentTimeMillis()

        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())
        readerJob = clientScope.launch {
            input?.let { i ->
                readLoop(i)
            }
        }
        writerJob = clientScope.launch {
            output?.let { o ->
                writeLoopByte(o)
            }

        }
//        val monitor = clientScope.launch { heartbeatAndIdleMonitor() }
        Loge.e("出厂配置 initSocket SocketClient connectAndServe 已连接")
        _state.value = CONNECTED
        try {
            readerJob?.join()
        } finally {
//            writer.cancel()
//            monitor.cancel()
//
        }
    }

    /***
     * 启动心跳查询
     */
    suspend fun sendHeartbeat() {
        monitorJob = clientScope.launch { heartbeatAndIdleMonitor() }
//        monitor.cancel()
    }

    /***
     * 读取socket数据
     * @param input
     * 缓冲输入流
     */
    /***
     * 读取socket数据
     * @param input
     * 缓冲输入流
     */
    private suspend fun readLoop(input: BufferedInputStream) {
        Loge.e("出厂配置 initSocket SocketClient readLoop ${Thread.currentThread().name}")

        // 使用 BufferedReader 按行读取
        val reader = input.bufferedReader(Charset.forName("UTF-8"))
        val jsonBuffer = StringBuilder()

        while (running && clientScope.isActive) {
            try {
                val line = withContext(Dispatchers.IO) {
                    Loge.e("出厂配置 initSocket SocketClient readLoop withContext ${Thread.currentThread().name}")
                    reader.readLine()
                }

                if (line == null) {
                    BoxToolLogUtils.recordSocket(CmdValue.CONNECTING, "Stream closed")
                    throw IOException("Stream closed")
                }

                lastReceivedAtMillis = System.currentTimeMillis()

                // 累积到缓冲区
                jsonBuffer.append(line)

                // 尝试检测 JSON
                val jsonResult = detectJson(jsonBuffer.toString())

                when (jsonResult) {
                    is JsonResult.Valid -> {
                        // 找到有效的 JSON
                        Loge.d("出厂配置 initSocket SocketClient readLoop 找到完整 JSON: ${jsonResult.json}")

                        // 发送完整的 JSON
                        sendJsonFrame(jsonResult.json)

                        // 清空缓冲区
                        jsonBuffer.clear()

                        // 如果还有剩余内容（多个 JSON 在同一行），继续处理
                        val remaining = jsonResult.remaining
                        if (remaining.isNotEmpty()) {
                            jsonBuffer.append(remaining)
                        }
                    }

                    is JsonResult.Incomplete -> {
                        // JSON 不完整，继续等待更多数据
                        Loge.d("出厂配置 initSocket SocketClient readLoop JSON 不完整，等待更多数据 $jsonBuffer")
                        // 保持缓冲区中的内容
                    }

                    is JsonResult.Invalid -> {
                        // 不是 JSON 或格式错误
                        Loge.w("出厂配置 initSocket SocketClient readLoop 检测到无效数据，清空缓冲区")
                        BoxToolLogUtils.recordSocket(CmdValue.CONNECTING, "Invalid data: $line")
                        jsonBuffer.clear()
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
                Loge.e("出厂配置 initSocket SocketClient readLoop catch ${e.message}")
                BoxToolLogUtils.recordSocket(
                    CmdValue.CONNECTING, "socketClient,readLoop catch ${e.message}}"
                )
                break
            }
        }
    }

    /**
     * 检测 JSON
     */
    private fun detectJson(data: String): JsonResult {
        if (data.isEmpty()) return JsonResult.Invalid

        // 查找第一个 JSON 开始位置
        val startIndex = data.indexOfFirst { it == '{' }
        if (startIndex == -1) {
            return JsonResult.Invalid
        }

        // 检查大括号是否匹配
        var braceCount = 0
        var inString = false
        var escapeNext = false

        for (i in startIndex until data.length) {
            val c = data[i]

            when {
                escapeNext -> {
                    escapeNext = false
                    continue
                }

                c == '\\' -> {
                    escapeNext = true
                    continue
                }

                c == '"' -> {
                    inString = !inString
                    continue
                }

                !inString -> {
                    when (c) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                // 找到完整的 JSON
                                val json = data.substring(startIndex, i + 1)
                                val remaining = data.substring(i + 1)

                                // 验证 JSON 格式
                                return if (isValidJson(json)) {
                                    JsonResult.Valid(json, remaining)
                                } else {
                                    JsonResult.Invalid
                                }
                            }
                        }
                    }
                }
            }
        }

        // 大括号没有闭合
        return JsonResult.Incomplete
    }

    /**
     * 验证 JSON 格式
     */
    private fun isValidJson(json: String): Boolean {
        return try {
            JsonParser.parseString(json)
            true
        } catch (e: JsonSyntaxException) {
            false
        }
    }

    /**
     * 发送 JSON 帧
     */
    private suspend fun sendJsonFrame(json: String) {
        try {
            val frame = json.toByteArray(Charset.forName("UTF-8"))
            BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "$json")
            _incoming.emit(frame)
        } catch (e: Exception) {
            e.printStackTrace()
            Loge.e("出厂配置 initSocket SocketClient readLoop JSON 帧失败: ${e.message}")
        }
    }


    sealed class JsonResult {
        data class Valid(val json: String, val remaining: String) : JsonResult()
        data object Incomplete : JsonResult()
        data object Invalid : JsonResult()
    }
    /***
     * 读取socket数据
     * @param input
     * 缓冲输入流
     */
//    private suspend fun readLoop(input: BufferedInputStream) {
//        Loge.e("出厂配置 initSocket SocketClient readLoop ")
//        val buffer = ByteArray(8 * 1024)
//        while (running && clientScope.isActive) {
//            try {
//                val read = input.read(buffer)
//                if (read == -1) {
//                    BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "Stream closed")
////                    LiveBus.get(BusType.BUS_NET_MSG).post("读取不到socket数据 Stream closed")
//                    throw IOException("Stream closed")
//                }
//                lastReceivedAtMillis = System.currentTimeMillis()
//                val frame = buffer.copyOf(read)
////                Loge.e("出厂配置 initSocket SocketClient readLoop ${ByteUtils.toHexString(frame)}")
//                _incoming.emit(frame)
//            } catch (e: IOException) {
//                e.printStackTrace()
//                Loge.e("出厂配置 initSocket SocketClient readLoop catch ${e.message}")
//                BoxToolLogUtils.recordSocket(
//                    CmdValue.RECEIVE, "socketClient,readLoop catch ${e.message}}"
//                )
//                break
//            }
//        }
//    }

    /***
     * 读取socket数据
     * @param output
     * 缓冲输出流
     */
    private suspend fun writeLoopByte(output: BufferedOutputStream) {
        Loge.e("出厂配置 initSocket SocketClient writeLoop running $running | isActive ${clientScope.isActive}")
        while (running && clientScope.isActive) {
            try {
                val data = sendQueueByte.receive()
//                Loge.e("出厂配置 initSocket SocketClient writeLoopByte byte：${ByteUtils.toHexString(data)}")
                BoxToolLogUtils.recordSocket(
                    CmdValue.SEND, JsonBuilder.toByteArrayToStringNotPretty(data)
                )
                output.write(data)
                if (config?.writeFlushIntervalMillis!! == 0L) {
                    output.flush()
                } else {
                    // Optional coalescing
                    delay(config?.writeFlushIntervalMillis!!)
                    output.flush()
                }
            } catch (e: CancellationException) {
                Loge.e("出厂配置 initSocket SocketClient writeLoop catch1 ${e.message}")
                BoxToolLogUtils.recordSocket(
                    CmdValue.CONNECTING, "socketClient,writeLoopByte catch1 ${e.message}}"
                )
                break
            } catch (e: IOException) {
                Loge.e("出厂配置 initSocket SocketClient writeLoop catch2 ${e.message}")
                BoxToolLogUtils.recordSocket(
                    CmdValue.CONNECTING, "socketClient,writeLoopByte catch2 ${e.message}}"
                )

                break
            }
        }
    }

    /***
     *
     * 心跳启动
     */
    private suspend fun heartbeatAndIdleMonitor() {
//       Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor $running | ${clientScope.isActive}")
        val hasHeartbeat =
            config?.heartbeatIntervalMillis!! > 0 /*&& config.heartbeatPayload.isNotEmpty()*/
        while (running && clientScope.isActive) {
            val now = System.currentTimeMillis()
//            Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor 分钟：${config.idleTimeoutMillis} | 当前毫秒：$lastReceivedAtMillis | 当前-最后：${now - lastReceivedAtMillis}")
            if (config?.idleTimeoutMillis!! > 0 && now - lastReceivedAtMillis > config?.idleTimeoutMillis!!) {
                // Force reconnect by closing the socket
                Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor closeSocketQuietly")
                closeSocketQuietly()
                return
            }
//            Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor $hasHeartbeat")
            if (hasHeartbeat) {
                try {
//                    Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor trySend")
                    val stateList = DatabaseManager.queryStateList(AppUtils.getContext())

//                    Loge.e("出厂配置 initSocket SocketClient stateList：${stateList.size}")
                    val setSignal = SPreUtil[AppUtils.getContext(), SPreUtil.setSignal, 19] as Int
                    val setIr1 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr1, -1] as Int
                    val setIr2 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr2, -1] as Int
                    val overflowState =
                        SPreUtil[AppUtils.getContext(), SPreUtil.overflowState, false] as Boolean
                    // 构建JSON对象
                    val jsonObject = getDeviceWeightChange(
                        CmdValue.CMD_HEART_BEAT, stateList, setSignal, setIr1, setIr2, overflowState
                    )
                    Loge.e("执行重量变动 心跳")
                    if (flowTaskState.value == -1) {
                        val result1 = WeightChangeStorage(AppUtils.getContext()).get("key_weight1")
                        val result2 = WeightChangeStorage(AppUtils.getContext()).get("key_weight2")
                        Loge.e("执行重量变动 结：$result1 - 重：${devWeiChaMapCun[0]}| 结：$result2 - 重：${devWeiChaMapCun[1]} 任务：${flowTaskState.value}")
                        if (result1 == "success" || result2 == "success") {
                            if (result1 == "success") {
                                devWeiChaMapSend[0] = false
                            }
                            if (result2 == "success") {
                                devWeiChaMapSend[1] = false
                            }
                            val weightChange =
                                getDeviceWeightChange(CmdValue.CMD_PERIPHERAL_STATUS, stateList, setSignal, setIr1, setIr2, overflowState)
                            Loge.e("执行重量变动 数据 $weightChange ")
                            sendText(weightChange.toString())
                            saveRecordSocket(CmdValue.CONNECTING, "重量变动,重量-1：${devWeiChaMapCun[0]}|重量-2：${devWeiChaMapCun[1]}")
                        }
                    }
//                    Loge.e("出厂配置 initSocket SocketClient 发送心跳数据：$jsonObject")
                    val byteArray = JsonBuilder.toByteArray(jsonObject)
                    sendQueueByte.trySend(byteArray)
                } catch (e: Exception) {
                    Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor catch ${e.message}")
                    BoxToolLogUtils.recordSocket(
                        CmdValue.CONNECTING, "socketClient,heartbeatAndIdleMonitor catch ${e.message}"
                    )

                }
            }
            delay(maxOf(1000L, config?.heartbeatIntervalMillis!!))
        }
    }

    private fun getDeviceWeightChange(
        cmd: String,
        stateList: List<StateEntity>,
        setSignal: Int,
        setIr1: Int,
        setIr2: Int,
        overflowState: Boolean,
    ): JsonObject {
        return JsonBuilder.build {
            addProperty("cmd", cmd)
            addProperty("signal", setSignal)
            // 添加数组
            addArray("stateList") {
                stateList.withIndex().forEach { (index, state) ->
                    addObject {
                        addProperty("smoke", state.smoke)
                        val curGTotal = when (index) {
                            0 -> {
                                curG1TotalWeight.toFloat()
                            }

                            1 -> {
                                curG2TotalWeight.toFloat()
                            }

                            else -> {
                                60f
                            }
                        }
                        val curGWeight = state.weigh
                        if (overflowState && curGWeight > curGTotal) {
                            addProperty("capacity", 2)
                        } else {
                            addProperty("capacity", state.capacity)
                        }
                        if (setIr1 == 1 && index == 0) {
                            addProperty("irState", state.irState)
                        } else {
                            addProperty("irState", 0)
                        }
                        if (setIr2 == 1 && index == 1) {
                            addProperty("irState", state.irState)
                        } else {
                            addProperty("irState", 0)
                        }
//                                    addProperty("irState", 1)
//                                    addProperty("weigh", 36.00)
                        addProperty("weigh", state.weigh)
                        addProperty("doorStatus", state.doorStatus)
                        addProperty("lockStatus", state.lockStatus)
                        addProperty("cabinId", state.cabinId ?: "")
                    }
                }
            }
        }
    }

    fun closeSocketIO() {
        readerJob?.cancel()
        writerJob?.cancel()
        monitorJob?.cancel()
        readerJob = null
        writerJob = null
        monitorJob = null
    }

    /***
     * 关闭socket
     */
    private fun closeSocketQuietly() {
        Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly $running | ${clientScope.isActive}")
        try {
            socketMutex.tryLock()?.let { locked ->
                if (locked) {
                    try {
                        closeSocketIO()
                        socket?.close()
                    } catch (e: Exception) {
                        Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly catch1 ${e.message}")
                        BoxToolLogUtils.recordSocket(
                            CmdValue.CONNECTING, "socketClient,closeSocketQuietly ${e.message}}"
                        )
                    } finally {
                        socket = null
                        socketMutex.unlock()
                        BoxToolLogUtils.recordSocket(
                            CmdValue.CONNECTING, "socketClient,closeSocketQuietly finally"
                        )
                        Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly finally")
                    }
                }
            }
        } catch (e: Exception) {
            Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly catch2 ${e.message}")
            BoxToolLogUtils.recordSocket(
                CmdValue.CONNECTING, "socketClient,closeSocketQuietly catch2 ${e.message}"
            )

        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    /***
     * 当前拍照图片路径
     */
    private val _flowTakePic = MutableSharedFlow<String>(replay = 0)

    /***
     * 当前拍照图片路径
     */
    val flowTakePic = _flowTakePic.asSharedFlow()

    /***
     * 当前拍照图片路径
     */
    fun setFlowTakePic(newValue: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _flowTakePic.emit(newValue)
        }
    }

    /***
     * 123 开启相机
     * 321 关闭相机
     * 1231 内外两张
     * 1234 内摄像头拍照
     * 1235 外摄像头拍照
     * 10 格口关闭 相机拍照
     * 11 业务开始 相机拍照
     *
     */
    private val _flowCameraStatus = MutableStateFlow(-1)

    /***
     * 123 开启相机
     * 321 关闭相机
     * 1231 内外两张
     * 1234 内摄像头拍照
     * 1235 外摄像头拍照
     * 10 格口关闭 相机拍照
     * 11 业务开始 相机拍照
     *
     */
    val flowCameraStatus = _flowCameraStatus.asStateFlow()

    /***
     * 123 开启相机
     * 321 关闭相机
     * 1231 内外两张
     * 1234 内摄像头拍照
     * 1235 外摄像头拍照
     * 10 格口关闭 相机拍照
     * 11 业务开始 相机拍照
     *
     */
    fun setFlowCameraStatus(newValue: Int) {
        _flowCameraStatus.value = newValue
    }

    /***
     * 标记当前是否在执行任务
     * 1.任务执行中
     * -1.任务复原
     */
    private val _flowTaskState = MutableStateFlow(-1)

    /***
     * 标记当前是否在执行任务
     * 1.任务执行中
     * -1.任务复原
     */
    val flowTaskState = _flowTaskState.asStateFlow()

    /***
     * 标记当前是否在执行任务
     * 1.任务执行中
     * -1.任务复原
     */
    fun setFlowTaskState(newValue: Int) {
        _flowTaskState.value = newValue
    }

    /***
     * 标记当前查询重量状态
     * 0.重量查询前
     * 1.重量查询后
     * 10.重量查询重
     */
    private val _flowFrontBackState = MutableStateFlow(-1)

    /***
     * 标记当前查询重量状态
     * 0.重量查询前
     * 1.重量查询后
     * 10.重量查询重
     */
    val flowFrontBackState = _flowFrontBackState.asStateFlow()

    /***
     * 标记当前查询重量状态
     * 0.重量查询前
     * 1.重量查询后
     * 10.重量查询重
     */
    fun setFlowFrontBackState(newValue: Int) {
        _flowFrontBackState.value = newValue
    }

    /***
     * 当前操作状态
     * 1.打开状态
     * 2.关闭状态
     */
    private val _flowDoorStartType = MutableStateFlow(-1)

    /***
     * 当前操作状态
     * 1.打开状态
     * 0.关闭状态
     * 2.强制关闭
     */
    val flowDoorStartType = _flowDoorStartType.asStateFlow()

    /***
     * 当前操作状态
     * @param newValue
     * 1.打开状态
     * 2.关闭状态
     */
    fun setFlowDoorStartType(newValue: Int) {
        _flowDoorStartType.value = newValue
    }


    /***
     * 当前门状态
     * 门开门已经开启 701
     * 门关启动关闭中 703
     */
    private val _flowDoorStatus = MutableStateFlow(-1)

    /***
     * 当前门状态
     * 门开门已经开启 701
     * 门关启动关闭中 703
     */
    val flowDoorStatus = _flowDoorStatus.asStateFlow()

    /***
     * 当前门状态
     * 门开门已经开启 701
     * 门关启动关闭中 703
     */
    fun setFlowDoorStatus(newValue: Int) {
        _flowDoorStatus.value = newValue
    }

    /***
     * 相机开启 123 相机关闭 321
     * 格口开启 11 格口关闭 10
     * 清运开启 21 清运关闭 20
     * 点击启动关闭 111 计时启动关闭 110
     * 关闭手机页面 200
     */
    private val _flowMonitorDoor = MutableStateFlow(-1)

    /***
     * 相机开启 123 相机关闭 321
     * 格口开启 11 格口关闭 10
     * 清运开启 21 清运关闭 20
     * 点击启动关闭 111 计时启动关闭 110
     * 关闭手机页面 200
     */
    val flowMonitorDoor = _flowMonitorDoor.asStateFlow()

    /***
     * 相机开启 123 相机关闭 321
     * 格口开启 11 格口关闭 10
     * 清运开启 21 清运关闭 20
     * 点击启动关闭 111 计时启动关闭 110
     * 关闭手机页面 200
     */
    fun setFlowMonitorDoor(newValue: Int) {
        _flowMonitorDoor.value = newValue
    }

    /***
     * 查询重量前 0
     * 查询重量后 1
     * 查询重量后 30
     * 查询重量后 31
     */
    private val _flowMonitorWeight = MutableStateFlow(-1)

    /***
     * 查询重量前 0
     * 查询重量后 1
     * 查询重量后 30
     * 查询重量后 31
     */
    val flowMonitorWeight = _flowMonitorWeight.asStateFlow()

    /***
     * 查询重量前 0
     * 查询重量后 1
     * 查询重量前清运 30
     * 查询重量后清运 31
     */
    fun setFlowMonitorWeight(newValue: Int) {
        _flowMonitorWeight.value = newValue
    }


    private val _flowMonitorMC = MutableStateFlow(-1)

    /***
     * 格口开启 11 格口关闭 10
     * 清运开启 21 清运关闭 20
     * 点击启动关闭 111 计时启动关闭 110
     */
    val flowMonitorMC = _flowMonitorMC.asStateFlow()

    /***
     *
     * 计算页 10
     * 清运关闭 20
     */
    fun setFlowMonitorMC(newValue: Int) {
        _flowMonitorMC.value = newValue
    }

    fun getTypeName(type: Int): String {
        return when (type) {
            MonitorType.TYPE_123 -> "打开相机"
            MonitorType.TYPE_321 -> "关闭相机"
            MonitorType.GE_WEIGHT_FRONT -> "查询重量前"
            MonitorType.GE_WEIGHT_BACK -> "查询重量后"
            MonitorType.GE_WEIGHT_CLEAR_FRONT -> "查询重量前清运"
            MonitorType.GE_WEIGHT_CLEAR_BACK -> "查询重量后清运"
            MonitorType.TYPE_11 -> "格口开启"
            MonitorType.TYPE_10 -> "格口关闭"
            MonitorType.TYPE_20 -> "清运关闭"
            MonitorType.TYPE_21 -> "清运开启"
            MonitorType.TYPE_111 -> "点击启动关闭"
            MonitorType.TYPE_110 -> "计时启动关闭"
            MonitorType.TYPE_200 -> "关闭登录"
            MonitorType.TYPE_701 -> "门正在开启"
            MonitorType.TYPE_703 -> "执行门关闭中"
            MonitorType.TYPE_1110 -> "格口启动中"
            else -> {
                "$type"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mHomeBg?.recycle()
        mHomeBg = null
        mQrCode?.recycle()
        mQrCode = null
        mMaintaining?.recycle()
        mMaintaining = null
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    private val _flowByteArrayWeight = MutableStateFlow("")
    val flowByteArrayWeight = _flowByteArrayWeight.asStateFlow()
    private fun setFlowByteArrayWeight(newValue: String) {
        _flowByteArrayWeight.value = newValue
    }

    private val _flowByteArrayResult = MutableStateFlow("")
    val flowByteArrayResult = _flowByteArrayResult.asStateFlow()
    private fun setFlowByteArrayResult(newValue: String) {
        _flowByteArrayResult.value = newValue
    }

    private val _flowByteArrayStatus = MutableStateFlow("")
    val flowByteArrayStatus = _flowByteArrayStatus.asStateFlow()
    private fun setFlowByteArrayStatus(newValue: String) {
        _flowByteArrayStatus.value = newValue
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    enum class UpgradeStep {
        IDLE, INSTALL_DOW, INSTALL_APK, INSTALL_SAME, INSTALL_FUALT, UPGRADE_DOW, UPGRADE_FUALT, QUERY_VERSION, QUERY_VERSION_FUALT, ENTER_STATUS, ENTER_STATUS_FUALT, QUERY_STATUS, QUERY_STATUS_FUALT, SEND_FILE, SEND_FILE_FUALT, SEND_FILE_END, SEND_FILE_END_FUALT, RESTART_APP, RESTART_APP_FUALT,
    }

    // 建议将这些变量放在 ViewModel 中统一管理
    private val _chipStep = MutableStateFlow(UpgradeStep.IDLE)
    val chipStep = _chipStep.asStateFlow()
    fun setFlowUpgradeStep(step: UpgradeStep) {
        _chipStep.value = step
    }

    suspend fun startDowChip(otaModel: OtaBean) = withContext(Dispatchers.IO) {
        val netVersion = otaModel.version ?: ""
        if (!TextUtils.isEmpty(netVersion)) {
            val gversion =
                SPreUtil[AppUtils.getContext(), SPreUtil.gversion, CmdCode.GJ_VERSION] as Int
            val netVersion = netVersion.replace(".", "").toIntOrNull() ?: CmdCode.GJ_VERSION
            Loge.e("流程 toGoCmdOtaBin 添加资源 $gversion  $netVersion")
            if (netVersion > gversion && !isRunning.get()) {
                _chipStep.value = UpgradeStep.UPGRADE_DOW
                chipCurV = gversion
                chipDowV = netVersion
                Loge.e("流程 toGoCmdOtaBin 添加资源 回调查询版本 当前：$chipCurV  网络：$chipDowV")
                val fileName = "f1-${netVersion}.bin"
                chipName = fileName
                val saveResource = ResEntity().apply {
                    sn = otaModel.sn
                    version = netVersion.toString()
                    cmd = otaModel.cmd
                    url = otaModel.url
                    md5 = otaModel.md5
                    time = AppUtils.getDateYMDHMS()
                }
                val queryResource = DatabaseManager.queryResCmd(
                    AppUtils.getContext(), netVersion.toString(), otaModel.sn ?: "", otaModel.cmd
                        ?: ""
                )
                Loge.e("流程 toGoCmdOtaBin 添加资源 $queryResource")
                if (queryResource == null) {
                    val row = DatabaseManager.insertRes(AppUtils.getContext(), saveResource)
                    Loge.e("流程 toGoCmdOtaBin 添加资源 $row")
                    //取网络数据判断
                    if (otaModel.url != null && !TextUtils.isEmpty(otaModel.url) && chipCurV < chipDowV) {
                        Loge.e("流程 toGoCmdOtaBin 进入下载 ")
                        val dir = FileMdUtil.matchNewFileName("bin", fileName)
                        otaModel.url?.let { dowurl ->
                            downloadRes(dowurl, dir) { success, file ->//固件下载 未存储
                                Loge.e("流程 toGoCmdOtaBin success $success")
                                if (success) {
                                    toGoCmdOtaBin()
                                    startUpgradeWorkflow()
                                    upNetResDb("下载BIN成功插入", ResEntity().apply {
                                        id = row
                                        status = ResType.TYPE_2//还未升级
                                        sn = otaModel.sn
                                        version = netVersion.toString()
                                        cmd = otaModel.cmd
                                        url = otaModel.url
                                        md5 = otaModel.md5
                                        time = AppUtils.getDateYMDHMS()
                                    })
                                } else {
                                    _chipStep.value = UpgradeStep.UPGRADE_FUALT
                                    upNetResDb("下载BIN失败插入", ResEntity().apply {
                                        id = row
                                        status = ResType.TYPE_4//下载失败
                                        sn = otaModel.sn
                                        version = netVersion.toString()
                                        cmd = otaModel.cmd
                                        url = otaModel.url
                                        md5 = otaModel.md5
                                        time = AppUtils.getDateYMDHMS()
                                    })

                                }
                            }
                        }
                    } else {
                        _chipStep.value = UpgradeStep.UPGRADE_FUALT
                        Loge.e("流程 toGoCmdOtaBin 下载BIN失败插入失败 $row")
                        insertInfoLog(LogEntity().apply {
                            cmd = "ota"
                            msg = "下载BIN失败插入失败  $row"
                            time = AppUtils.getDateYMDHMS()
                        })
                    }
                } else {
                    Loge.e("流程 toGoCmdOtaBin 存在资源 二次升级")
                    if (chipCurV == netVersion) {
                        _chipStep.value = UpgradeStep.UPGRADE_FUALT
                        Loge.e("流程 toGoCmdOtaBin 存在资源 版本一致")
                        queryResource.status = ResType.TYPE_3//固件文件
                        upNetResDb("已经是最新版本${netVersion}", queryResource)
                    } else {
                        //版本不一致
                        if (chipCurV < netVersion && (queryResource.status == ResType.TYPE_F1 || queryResource.status == ResType.TYPE_2 || queryResource.status == ResType.TYPE_4)) {
                            Loge.e("流程 toGoCmdOtaBin 存在资源 版本不一致")
                            queryResource.version = netVersion.toString()
                            queryResource.url = otaModel.url
                            queryResource.md5 = otaModel.md5
                            val fileName = "f1-${netVersion}.bin"
                            val dir = FileMdUtil.matchNewFileName("bin", fileName)
                            queryResource.url?.let { url ->
                                downloadRes(url, dir) { success, file ->//固件下载
                                    if (success) {
                                        queryResource.status = ResType.TYPE_2//还未升级
                                        toGoCmdOtaBin()
                                        startUpgradeWorkflow()
                                        upNetResDb("下载BIN成功更新", queryResource)
                                    } else {
                                        _chipStep.value = UpgradeStep.UPGRADE_FUALT
                                        queryResource.status = ResType.TYPE_4//下载失败
                                        upNetResDb("下载BIN失败更新", queryResource)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                //查询出来 更新状态 在弄个定时去检测在自动执行更新
                Loge.e("流程 toGoCmdOtaBin 版本不同")
                val queryResource = DatabaseManager.queryResNewBin(
                    AppUtils.getContext(), otaModel.sn ?: "", CmdValue.CMD_OTA
                )
                if (queryResource != null) {
                    queryResource.status = ResType.TYPE_3//固件文件
                    upNetResDb("已经是最新版本${netVersion}", queryResource)
                }
            }
        }
    }

    suspend fun startDowApk(otaModel: OtaBean) = withContext(Dispatchers.IO) {
        val verName = AppUtils.getVersionName()
        val oldVs = verName.replace(".", "").toIntOrNull() ?: 0
        val newVs = otaModel.version?.replace(".", "")?.toIntOrNull() ?: 0
        if (oldVs < newVs && !isRunning.get()) {
            _chipStep.value = UpgradeStep.INSTALL_DOW
            val saveResource = ResEntity().apply {
                sn = otaModel.sn
                version = otaModel.version
                cmd = otaModel.cmd
                url = otaModel.url
                md5 = otaModel.md5
                time = AppUtils.getDateYMDHMS()
            }
            val queryResource = DatabaseManager.queryResCmd(
                AppUtils.getContext(), otaModel.version ?: "", otaModel.sn ?: "", otaModel.cmd ?: ""
            )
            if (queryResource == null) {
                val row = DatabaseManager.insertRes(AppUtils.getContext(), saveResource)
                Loge.e("调试socket Ota 下载APK $row")
                delay(3000)
                //取网络数据判断
                if (otaModel.url != null && !TextUtils.isEmpty(otaModel.url)) {
                    val fileName = "hsg-${otaModel.version}.apk"
                    val dir = FileMdUtil.matchNewFileName("apk", fileName)
                    otaModel.url?.let { dowurl ->
                        downloadRes(dowurl, dir) { success, file ->//apk下载 未存储
                            if (success) {
                                toGoCmdOtaAPK()
                                //去安装
                                installDowApk("不存在 首次更新APK")
                                upNetResDb("下载APK成功插入${otaModel.version}", ResEntity().apply {
                                    id = row
                                    status = ResType.TYPE_2//还未升级
                                    sn = otaModel.sn
                                    version = otaModel.version
                                    cmd = otaModel.cmd
                                    url = otaModel.url
                                    md5 = otaModel.md5
                                    time = AppUtils.getDateYMDHMS()
                                })
                            } else {
                                _chipStep.value = UpgradeStep.INSTALL_FUALT
                                upNetResDb("下载APK失败插入${otaModel.version}", ResEntity().apply {
                                    id = row
                                    status = ResType.TYPE_4//下载失败
                                    sn = otaModel.sn
                                    version = otaModel.version
                                    cmd = otaModel.cmd
                                    url = otaModel.url
                                    md5 = otaModel.md5
                                    time = AppUtils.getDateYMDHMS()
                                })
                            }
                        }
                    }
                } else {
                    _chipStep.value = UpgradeStep.INSTALL_FUALT
                    Loge.e("调试socket 下载APK失败插入失败 $row")
                    insertInfoLog(LogEntity().apply {
                        cmd = "ota/apk"
                        msg = "下载APK失败插入失败  $row"
                        time = AppUtils.getDateYMDHMS()
                    })
                }
            } else {
                Loge.e("调试socket Ota 下载APK 存在")
                //版本一致更新安装了
                val verName = AppUtils.getVersionName()
                if (verName == otaModel.version) {
                    _chipStep.value = UpgradeStep.INSTALL_FUALT
                    queryResource.status = ResType.TYPE_3//Apk文件
                    upNetResDb("已经是最新版本${otaModel.version}", queryResource)
                } else {
                    val verName = AppUtils.getVersionName()
                    val oldVs = verName.replace(".", "").toInt()
                    val newVs = otaModel.version?.replace(".", "")?.toInt() ?: 0
                    Loge.e("调试socket Ota 下载APK 原：${oldVs},新：${newVs}")
                    //未更新并且是下载失败
                    if (oldVs < newVs && (queryResource.status == ResType.TYPE_F1 || queryResource.status == ResType.TYPE_2 || queryResource.status == ResType.TYPE_4)) {
                        val fileName = "hsg-${otaModel.version}.apk"
                        val dir = FileMdUtil.matchNewFileName("apk", fileName)
                        queryResource.url?.let { url ->
                            downloadRes(url, dir) { success, file ->//apk下载
                                if (success) {
                                    queryResource.status = ResType.TYPE_2//还未升级
                                    toGoCmdOtaAPK()
                                    //去安装
                                    installDowApk("不存在 首次更新APK")
                                    //去安装
                                    upNetResDb("下载APK成功更新${otaModel.version}", queryResource)
                                } else {
                                    _chipStep.value = UpgradeStep.INSTALL_FUALT
                                    queryResource.status = ResType.TYPE_4//下载失败
                                    upNetResDb(
                                        "下载APK失败更新${otaModel.version}", queryResource
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    var jobInstall: Job? = null
    fun cancelJobInstall() {
        jobInstall?.cancel()
        jobInstall = null
    }

    var installApkUrl: String? = null
    fun installDowApk(text: String) {
        jobInstall = ioScope.launch {
            val queryResource =
                DatabaseManager.queryResNewAPk(AppUtils.getContext(), CmdValue.CMD_OTA_APK)
            //版本一致更新安装了
            val verName = AppUtils.getVersionName()
            val oldVs = verName.replace(".", "").toInt()
            val newVs = queryResource.version?.replace(".", "")?.toInt() ?: 0
            if (newVs > oldVs) {
                val fileName = "hsg-${queryResource.version}.apk"
                val result = FileMdUtil.checkApkFileExists(fileName)
                installApkUrl = FileMdUtil.matchNewFileName("apk", fileName)
                Loge.e("调试socket 进来更新APK了 文件是否存在：$result - $text")
                _chipStep.value = UpgradeStep.INSTALL_APK
            }
        }
    }

    fun startUpgradeWorkflow() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val chipStep7 =
                    SerialPortCoreSdk.instance.executeChip2(SerialPortSdk.CMD7, byteArrayOf(0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte()))
                chipStep7.onSuccess { bytes ->
                    // 解析 Payload (逻辑同你之前的代码)
                    val payload = ProtocolCodec.getSafePayload(bytes)
                    if (payload?.size == 3) {
                        _chipStep.value = UpgradeStep.ENTER_STATUS
                    }
                }.onFailure { e ->
                    _chipStep.value = UpgradeStep.ENTER_STATUS_FUALT
                    return@launch
                }
                if (chipStep.value == UpgradeStep.ENTER_STATUS) {
                    val file2 = FileMdUtil.matchNewFile2("bin", chipName)
                    val fileType = byteArrayOf(0xf1.toByte())
                    val filSize = file2?.length()?.toInt() ?: 0
                    filSize.let { size ->
                        if (size < 0) return@let
                        //软件大小
                        val sizeByte = HexConverter.intToByteArray(size)
                        Loge.d("流程 芯片升级 filSize = $size | sizeByte = ${ByteUtils.toHexString(sizeByte)}")
                        //软件版本
                        val vByte = HexConverter.intToByteArray(chipDowV)
                        Loge.d("流程 芯片升级 chipMasterV = $chipDowV vByte = ${ByteUtils.toHexString(vByte)}")
                        //CRC效验值
                        val crc = CRC32MPEG2Util.computeFile(file2.absolutePath)
                        val crcByte = HexConverter.intToByteArray(crc.toInt())
                        Loge.d("流程 芯片升级 crcByte = ${ByteUtils.toHexString(crcByte)}")
                        val sendByte =
                            HexConverter.combineByteArrays(fileType, sizeByte, vByte, crcByte)
                        val sendResult =
                            HexConverter.combineByteArrays(byteArrayOf(0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte()), sendByte)
                        val chipStep8 =
                            SerialPortCoreSdk.instance.executeChip2(SerialPortSdk.CMD8, sendResult)
                        chipStep8.onSuccess { bytes ->
                            // 解析 Payload (逻辑同你之前的代码)
                            val payload = ProtocolCodec.getSafePayload(bytes)
                            if (payload?.size == 3) {
                                _chipStep.value = UpgradeStep.QUERY_STATUS
                            }
                        }.onFailure { e ->
                            _chipStep.value = UpgradeStep.QUERY_STATUS_FUALT
                            return@launch
                        }
                        if (chipStep.value == UpgradeStep.QUERY_STATUS) {
                            //发送的文件数据
                            val sendBLFile = mutableListOf<ByteArray>()
                            val masterFile = FileMdUtil.matchNewFile2("bin", chipName)
                            masterFile?.let { file ->
                                sendBLFile.clear()
                                try {
                                    FileInputStream(file).use { fis ->
                                        val buffer = ByteArray(8)
                                        var bytesRead: Int
                                        var blockIndex = 0
                                        // 循环读取直到文件结束
                                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                                            val blockToSend =
                                                buffer.copyOfRange(0, bytesRead) // 或 buffer.copyOf(bytesRead)
                                            Loge.d("流程 芯片升级 封装好数据 共发送 $blockIndex 个数据块，发了数据：${ByteUtils.toHexString(blockToSend)}")
                                            sendBLFile.add(blockToSend)
                                            blockIndex++
                                        }
                                        Loge.d("流程 芯片升级 共发送 $blockIndex 个数据块")
                                    }
                                } catch (e: Exception) {
                                    Loge.d("流程 芯片升级 处理文件时出错: ${e.message}")
                                } finally {
                                    if (sendBLFile.isNotEmpty()) {
                                        Loge.d("流程 芯片升级 封装好数据 开始发送文件数据")
                                        Loge.d("流程 芯片升级 chipSet8fs ${sendBLFile.size}")
                                        var conutSendByte = 0
                                        val sendByte = sendBLFile[conutSendByte] // 从Channel中接收指令
                                        Loge.d("流程 芯片升级 发送第$sendFCount 个数据块，数据：${ByteUtils.toHexString(sendByte)}")
                                        if (sendByte.isNotEmpty()) {
                                            // 升级中的文件发送循环
                                            while (isActive && conutSendByte < sendBLFile.size) {
                                                val sendByte = sendBLFile[conutSendByte]
                                                // 直接调用，不排队，速度最快
                                                val result =
                                                    SerialPortCoreSdk.instance.executeChip2(SerialPortSdk.CMD18, sendByte)
                                                result.onSuccess { bytes ->
                                                    // 解析 Payload (逻辑同你之前的代码)
                                                    val payload =
                                                        ProtocolCodec.getSafePayload(bytes)
                                                    if (payload != null && payload.contentEquals(sendByte)) {
                                                        // 校验成功，发下一包
                                                        sendFCount++
                                                        conutSendByte++
                                                    } else {
                                                        // 校验失败逻辑...
                                                        _chipStep.value =
                                                            UpgradeStep.SEND_FILE_FUALT
                                                    }
                                                }.onFailure { e ->
                                                    Loge.e("直接发送失败: ${e.message}")
                                                    _chipStep.value = UpgradeStep.SEND_FILE_FUALT
                                                    // 此处可以实现重试逻辑，或者抛出异常中断升级
                                                }

                                                // 方案 B 已经由 executeDirect 的返回速度决定了频率，
                                                // 这里如果下位机写 Flash 快，可以不 delay，或者只 delay(5)
                                            }
                                        }
                                    } else {
                                        _chipStep.value = UpgradeStep.SEND_FILE_FUALT
                                        Loge.d("流程 芯片升级 封装好数据 没有文件数据")
                                        return@launch
                                    }
                                }
                            }
                        }

                        if (chipStep.value == UpgradeStep.SEND_FILE) {
                            delay(5000)
                            val chipStep9 =
                                SerialPortSdk.firmwareUpgrade78910(9, byteArrayOf(0xa4.toByte(), 0xa5.toByte(), 0xa6.toByte()))
                            if (chipStep9.isFailure) throw Exception("发送文件结束开始校验: ${chipStep9.exceptionOrNull()?.message}")
                            val stepStatus9 = chipStep9.getOrNull()?.upStatus
                            if (stepStatus9 == 1) {
                                _chipStep.value = UpgradeStep.SEND_FILE_END
                            } else {
                                _chipStep.value = UpgradeStep.SEND_FILE_END_FUALT
                                return@launch
                            }
                        }

                        if (chipStep.value == UpgradeStep.RESTART_APP) {
                            delay(3000)
                            val chipStep10 =
                                SerialPortSdk.firmwareUpgrade78910(10, byteArrayOf(0xa7.toByte(), 0xa8.toByte(), 0xa9.toByte()))
                            if (chipStep10.isFailure) throw Exception("进入重启: ${chipStep10.exceptionOrNull()?.message}")
                            val stepStatus10 = chipStep10.getOrNull()?.upStatus
                            if (stepStatus10 == 1) {
                                _chipStep.value = UpgradeStep.RESTART_APP
                            } else {
                                _chipStep.value = UpgradeStep.RESTART_APP_FUALT
                                return@launch
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                BoxToolLogUtils.savePrintln("升级流程：异常情况")
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // 初始化
    val cameraManagerNew = NewDualUsbCameraManager(AppUtils.getContext())


    enum class UiCloseStep {
        IDLE, CLOSE_DELIVERY, CLOSE_MOBILE, CLOSE_CLEAR_DOOR
    }

    // 建议将这些变量放在 ViewModel 中统一管理
    private val _uiCloseStep = MutableStateFlow(UiCloseStep.IDLE)
    val uiCloseStep = _uiCloseStep.asStateFlow()
    fun setFlowUiCloseStep(step: UiCloseStep) {
        _uiCloseStep.value = step
    }

    // 定义业务状态，方便 UI 订阅显示
    enum class LockerStep {
        IDLE, START, OPENING, WAITING_OPEN_DOOR, WAITING_OPEN_CLEAR, WEIGHT_TRACKING, CLICK_CLOSE, CLOSING, CLOSE, WAITING_CLOSE, FINISHED
    }

    // 建议将这些变量放在 ViewModel 中统一管理
    private val _currentStep = MutableStateFlow(LockerStep.IDLE)
    val currentStep = _currentStep.asStateFlow()
    fun setFlowCurrentStep(step: LockerStep) {
        _currentStep.value = step
    }

    var checkStatusResult: Deferred<Boolean>? = null

    /**
     * @param model
     * 检测满溢状态信息
     */
    private fun startLockerCheck(model: DoorOpenBean): Boolean {
        checkStatusResult = ioScope.async {
            Loge.e("流程 startLockerCheck model $model")
            //匹配当前投口几
            val states = DatabaseManager.queryStateList(AppUtils.getContext())
            //清运状态则跳过检测
            val openTypeBoolean = model.openType == 2
            //遍历格口
            states.withIndex().forEach { (index, state) ->
                when (index) {
                    0 -> {
                        if (state.cabinId == model.cabinId) {
                            doorGeX = CmdCode.GE1
                            cur1Cabinld = state.cabinId ?: ""//打开格口前读取db格口编码
                            curG1Weight = state.weigh.toString()//打开格口前读取db格口重量
                        }
                    }

                    1 -> {
                        if (state.cabinId == model.cabinId && doorGeXType == CmdCode.GE2) {
                            doorGeX = CmdCode.GE2
                            cur2Cabinld = state.cabinId ?: ""//打开格口前读取db格口编码
                            curG2Weight = state.weigh.toString()//打开格口前读取db格口重量
                        }
                    }
                }
            }
            //非清运指令则检测
            val toGo = if (!openTypeBoolean) {
                //处理当前超重
                //格口1 当前重量小于服务器红外满溢则true
                val bl1Overflow = CalculationUtil.isLess(curG1Weight ?: "0.00", curG1TotalWeight)
                //格口2 当前重量小于服务器红外满溢则true
                val bl2Overflow = CalculationUtil.isLess(curG2Weight ?: "0.00", curG2TotalWeight)
                //处理服务器下发满溢
                val overflowState1 =
                    SPreUtil[AppUtils.getContext(), SPreUtil.overflowState1, false] as Boolean //网络下发
                val overflowState2 =
                    SPreUtil[AppUtils.getContext(), SPreUtil.overflowState2, false] as Boolean //网络下发

                val overflowResult = when (doorGeX) {
                    CmdCode.GE1 -> {
                        if (!bl1Overflow || maptDoorFault[FaultType.FAULT_CODE_2110] == true || overflowState1) {
                            false
                        } else {
                            true
                        }
                    }

                    CmdCode.GE2 -> {
                        if (!bl2Overflow || maptDoorFault[FaultType.FAULT_CODE_2120] == true || overflowState2) {
                            false
                        } else {
                            true
                        }
                    }

                    else -> {
                        true
                    }
                }

                //红外满溢+可超重量
                val irOverflow = SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                val lrOverflowResult = when (doorGeX) {
                    CmdCode.GE1 -> {
                        if (maptDoorFault[FaultType.FAULT_CODE_11] == true) {
                            //格口1 当前重量小于服务器红外满溢则true
                            val bl1 =
                                CalculationUtil.isLess(curG1Weight ?: "0.00", irOverflow.toString())
                            if (bl1) {
                                maptDoorFault[FaultType.FAULT_CODE_211] = false
                                true
                            } else {
                                maptDoorFault[FaultType.FAULT_CODE_211] = true
                                false
                            }
                        } else {
                            true
                        }
                    }

                    CmdCode.GE2 -> {
                        if (maptDoorFault[FaultType.FAULT_CODE_12] == true) {
                            //格口2 当前重量小于服务器红外满溢则true
                            val bl2 =
                                CalculationUtil.isLess(curG2Weight ?: "0.00", irOverflow.toString())
                            if (bl2) {
                                maptDoorFault[FaultType.FAULT_CODE_212] = false
                                true
                            } else {
                                maptDoorFault[FaultType.FAULT_CODE_212] = true
                                false
                            }
                        } else {
                            true
                        }
                    }

                    else -> {
                        false
                    }
                }

                val result = when (doorGeX) {
                    CmdCode.GE1 -> {
                        if (maptDoorFault[FaultType.FAULT_CODE_111] == true || maptDoorFault[FaultType.FAULT_CODE_110] == true || maptDoorFault[FaultType.FAULT_CODE_91] == true || maptDoorFault[FaultType.FAULT_CODE_5101] == true || maptDoorFault[FaultType.FAULT_CODE_5102] == true) false else true //异常状态拦截执行任务
                    }

                    CmdCode.GE2 -> {
                        if (maptDoorFault[FaultType.FAULT_CODE_121] == true || maptDoorFault[FaultType.FAULT_CODE_120] == true || maptDoorFault[FaultType.FAULT_CODE_92] == true || maptDoorFault[FaultType.FAULT_CODE_5201] == true || maptDoorFault[FaultType.FAULT_CODE_5202] == true) false else true //异常状态拦截执行任务
                    }

                    else -> {
                        false
                    }
                }
                Loge.e("流程 executeBusiness 格口 $doorGeX result = $result overflowResult $overflowResult lrOverflowResult $lrOverflowResult")
                result && overflowResult && lrOverflowResult
                //true false true
            } else {
                true
            }
            toGo
        }
        return false
    }

    private val isRunning = AtomicBoolean(false)
    private val defauleWeight = "0.00"
    private var modelOpenBean: DoorOpenBean? = null

    /** 1.格口2.清运 */
    var remoteOpenType: Int = -1
    var setPhotoTransId: String = ""

    /***
     * @param model 服务器下发开门
     * @param setWeightBeforeOpen 开门前重量
     * @param doorGex 当前操作推杆
     * @param openType  开推杆几
     * @param closeType 关推杆几
     * @param forcedCloseType 强制关推杆几
     */
    fun startLockerDoorWorkflow(model: DoorOpenBean, setWeightBeforeOpen: String, doorGex: Int, openType: Int, closeType: Int, forcedCloseType: Int, executeCount: Int = 5) {
        viewModelScope.launch(Dispatchers.IO) {
            var toWeightAfterClosing = "0"  // 彻底关门后重量
            try {
                startLockerCheck(model)
                val transId = model.transId ?: ""
                val execution = checkStatusResult?.await()
                BoxToolLogUtils.savePrintln("业务流：业务正在执行中.... 格口：$doorGex 当前重量：$setWeightBeforeOpen $execution | 运行：${isRunning.get()} |${transId}")
                if (execution == false && isRunning.getAndSet(true)) {
                    return@launch
                }
                isRunning.set(true)
                // 1. 核心状态初始化
                var closeCount = executeCount
                cancelContainersStatusJob()
                modelOpenBean = model
                remoteOpenType = model.openType
                setPhotoTransId = transId
                _currentStep.value = LockerStep.START
                doorGeX = doorGex
                delay(1000)
                var weightBeforeOpen = setWeightBeforeOpen   // 开门前重量
                var weightAfterOpening = "0"  // 确认开门瞬间重量
                var weightDuringOpening = "0" // 过程中最后一次重量
                var weightAfterClosing = "0"  // 彻底关门后重量

                // --- 第一阶段：下发开门 ---
                _currentStep.value = LockerStep.OPENING

                // 记录“准备开门前”的初始重量（作为参考）
                val queryWeight = SerialPortSdk.queryWeight(doorGex)
                if (queryWeight.isFailure) throw Exception("获取重量指令失败: ${queryWeight.exceptionOrNull()?.message}")
                weightBeforeOpen = queryWeight.getOrNull()?.weight.toString()
                if (doorGex == CmdCode.GE1) {
                    curG1Weight = weightBeforeOpen
                } else {
                    curG2Weight = weightBeforeOpen
                }
                BoxToolLogUtils.savePrintln("业务流：正在执行开门动作 type=$openType 当前重量:$weightBeforeOpen")
                dbBeforeWeight(weightBeforeOpen, model)
                val turnDoor = SerialPortSdk.turnDoor(openType)
                if (turnDoor.isFailure) throw Exception("开门指令发送失败: ${turnDoor.exceptionOrNull()?.message}")
                DatabaseManager.upTransOpenStatus(AppUtils.getContext(), EntityType.WEIGHT_TYPE_10, transId)
                // --- 第二阶段：轮询等待门开启 ---
                _currentStep.value = LockerStep.WAITING_OPEN_DOOR
                BoxToolLogUtils.savePrintln("业务流：等待门物理状态变为【开启】")
                // 使用 withTimeout 防止传感器故障导致协程永久挂起
                withTimeout(20000) { // 20秒超时
                    while (isActive) {
                        val doorStatus = SerialPortSdk.turnDoorStatus(doorGex).getOrNull()?.status
                        BoxToolLogUtils.savePrintln("业务流：门开动作等待门物理状态变为【${SendOpenText.fromStatus(doorStatus ?: -1)}】")
                        if (doorStatus == CmdCode.GE_OPEN) {
                            _refBusStaChannel.send(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_3
                            })
                            weightAfterOpening =
                                SerialPortSdk.queryWeight(doorGex).getOrNull()?.weight.toString()
                            BoxToolLogUtils.savePrintln("业务流：打开后的重量：$weightAfterOpening")
                            DatabaseManager.upTransOpenStatus(
                                AppUtils.getContext(), EntityType.WEIGHT_TYPE_1, transId
                            )
                            dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, defauleWeight, defauleWeight, openModel = model, flowEnd = false)
                            break// 门已确认开启
                        }
//                        if (doorStatus == CmdCode.GE_OPEN_CLOSE_ING) { }
                        if (doorStatus == CmdCode.GE_OPEN_CLOSE_FAULT) {
                            noticeExection(CmdCode.GE_OPEN, doorGex, BusType.BUS_FAULT)
                            BoxToolLogUtils.savePrintln("业务流：门开前-门开故障 打开后的重量：$weightAfterOpening")
                            throw Exception("门开前-门开故障: $${SendOpenText.fromStatus(doorStatus ?: -1)}")
                        }
                        delay(500)
                    }
                }

                // --- 第三阶段：监测重量变化 ---
                _currentStep.value = LockerStep.WEIGHT_TRACKING
                BoxToolLogUtils.savePrintln("业务流：门已开启，开始监测实时重量。初始重量: $weightBeforeOpen ,门开重量：$weightAfterOpening")
                while (isActive) {
                    val curWeight =
                        SerialPortSdk.queryWeight(doorGex).getOrNull()?.weight.toString()
                    weightDuringOpening = curWeight
                    if (doorGex == CmdCode.GE1) {
                        curG1Weight = weightDuringOpening
                    } else {
                        curG2Weight = weightDuringOpening
                    }
                    BoxToolLogUtils.savePrintln("业务流：打开后持续的重量：$weightDuringOpening")
                    dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, weightDuringOpening, defauleWeight, openModel = model, flowEnd = false)
                    if (currentStep.value == LockerStep.CLOSE) {
                        BoxToolLogUtils.savePrintln("业务流：重量查询 门关闭")
                        break
                    }
                    // --- 第四阶段：执行关门动作 ---
                    if (currentStep.value == LockerStep.CLICK_CLOSE) {
                        _currentStep.value = LockerStep.CLOSING
                        BoxToolLogUtils.savePrintln("业务流：监测到关门信号，下发关门指令 type=$closeType")
                        val closeRes = SerialPortSdk.turnDoor(closeType)
                        if (closeRes.isFailure) throw Exception("关门指令发送失败")
                    }
                    // --- 第五阶段：轮询等待门关闭 ---
                    if (currentStep.value == LockerStep.CLOSING) {
                        withTimeout(30000) { // 30秒超时
                            val doorStatus =
                                SerialPortSdk.turnDoorStatus(doorGex).getOrNull()?.status
                            BoxToolLogUtils.savePrintln("业务流：门关动作等待门物理状态变为【$SendOpenText.fromStatus(doorStatus ?: -1)}】")
                            if (doorStatus == CmdCode.GE_CLOSE) {
                                _currentStep.value = LockerStep.CLOSE
                                BoxToolLogUtils.savePrintln("业务流：启动业务相机拍照后")
                                DatabaseManager.upTransCloseStatus(AppUtils.getContext(), 0, transId)
                                return@withTimeout
                            }
                            if (doorStatus == CmdCode.GE_OPEN) {
                                closeCount -= 1
                                BoxToolLogUtils.savePrintln("业务流：当前循环次数 $closeCount")
                                if (closeCount <= 0) {
                                    SerialPortSdk.turnDoor(forcedCloseType)
                                } else {
                                    SerialPortSdk.turnDoor(closeType)
                                }
                            }
                            if (doorStatus == CmdCode.GE_OPEN_CLOSE_FAULT) {
                                noticeExection(CmdCode.GE_OPEN, doorGex, BusType.BUS_FAULT)
                                BoxToolLogUtils.savePrintln("业务流：门开后-门关故障 打开后的重量：$weightAfterOpening")
                                throw Exception("门开后-门关故障: $${SendOpenText.fromStatus(doorStatus ?: -1)}")
                            }
                        }
                        // 这里可以增加一个逻辑：如果检测到重量稳定增加超过 X 秒，也可以自动触发下一步
                        delay(1000)
                    }
                    delay(1000)
                }
                _currentStep.value = LockerStep.WAITING_CLOSE

                // --- 第六阶段：结算最终重量 ---
                _currentStep.value = LockerStep.FINISHED
                delay(500) // 等待机械震动停止，获取更准的重量
                weightAfterClosing =
                    SerialPortSdk.queryWeight(doorGex).getOrNull()?.weight.toString()
                toWeightAfterClosing = weightAfterClosing
                dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, weightDuringOpening, weightAfterClosing, openModel = model, flowEnd = true)
                BoxToolLogUtils.savePrintln(
                    "业务流：业务流完毕！ 开门前：$weightBeforeOpen, 开门后：$weightAfterOpening, 过程最高/最后：$weightDuringOpening, 关门后：$weightAfterClosing 启动业务数据上报 curWeight = $weightDuringOpening changeWeight = " + "${
                        CalculationUtil.subtractFloats(weightAfterClosing, weightBeforeOpen)
                    } " + "refWeight = " + "${
                        CalculationUtil.subtractFloats(weightDuringOpening, weightAfterOpening)
                    } " + "beforeUpWeight = $weightBeforeOpen " + "afterUpWeight = $weightAfterOpening " + "beforeDownWeight = $weightDuringOpening " + "afterDownWeight = $weightAfterClosing "
                )
            } catch (e: TimeoutCancellationException) {
                BoxToolLogUtils.savePrintln("业务流：操作超时，请检查柜门是否卡住")
            } catch (e: Exception) {
                BoxToolLogUtils.savePrintln("业务流：异常中断: ${e.message}")
            } finally {
                BoxToolLogUtils.savePrintln("业务流：完毕 finally")
                modelOpenBean = null
                isRunning.set(false)
                _currentStep.value = LockerStep.IDLE
                if (doorGex == CmdCode.GE1) {
                    curG1Weight = toWeightAfterClosing
                } else {
                    curG2Weight = toWeightAfterClosing
                }
                if (containersDB.isNotEmpty()) {
                    val stateEntity = containersDB[doorGex - 1]
                    stateEntity.weigh = toWeightAfterClosing?.toFloat() ?: 0.00f
                    refrehContainers(stateEntity, doorGex - 1)
                }
                startContainersStatus() // 恢复全局状态轮询
            }
        }
    }

    /***
     * @param model 服务器下发开门
     * @param setWeightBeforeOpen 开门前重量
     */
    fun startLockerClearWorkflow(
        model: DoorOpenBean,
        setWeightBeforeOpen: String,
        doorGex: Int,
        openType: Int,
        queryType: Int,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var toWeightAfterClosing = "0"  // 彻底关门后重量
            try {
                val transId = model.transId ?: ""
                BoxToolLogUtils.savePrintln("业务流：业务正在执行中.... 清运：$doorGex 当前重量：$setWeightBeforeOpen 运行：${isRunning.get()} |${transId}")
                if (isRunning.getAndSet(true)) {
                    return@launch
                }
                isRunning.set(true)
                // 1. 核心状态初始化
                cancelContainersStatusJob()
                modelOpenBean = model
                remoteOpenType = model.openType
                _currentStep.value = LockerStep.START
                doorGeX = doorGex
                delay(500)
                var weightBeforeOpen = setWeightBeforeOpen   // 开门前重量
                var weightAfterOpening = "0"  // 确认开门瞬间重量
                var weightDuringOpening = "0" // 过程中最后一次重量
                var weightAfterClosing = "0"  // 彻底关门后重量

                // --- 第一阶段：下发开门 ---
                _currentStep.value = LockerStep.OPENING

                // 记录“准备开门前”的初始重量（作为参考）
                val queryWeight = SerialPortSdk.queryWeight(doorGex)
                if (queryWeight.isFailure) throw Exception("获取重量指令失败: ${queryWeight.exceptionOrNull()?.message}")
                weightBeforeOpen = queryWeight.getOrNull()?.weight.toString()
                if (doorGex == CmdCode.GE1) {
                    curG1Weight = weightBeforeOpen
                } else {
                    curG2Weight = weightBeforeOpen
                }
                BoxToolLogUtils.savePrintln("业务流：正在执行开门动作 type=$openType $weightBeforeOpen")
                dbBeforeWeight(weightBeforeOpen, model)
                val openClear = SerialPortSdk.openQueryClear(openType)
                if (openClear.isFailure) throw Exception("开门指令发送失败: ${openClear.exceptionOrNull()?.message}")
                DatabaseManager.upTransOpenStatus(
                    AppUtils.getContext(), EntityType.WEIGHT_TYPE_10, transId
                )
                // --- 第二阶段：轮询等待门开启 ---
                BoxToolLogUtils.savePrintln("业务流：等待门物理状态变为【开启】")
                // 使用 withTimeout 防止传感器故障导致协程永久挂起
                withTimeout(300000) { // 30秒超时
                    while (isActive) {
                        val doorClearStatus = SerialPortSdk.openQueryClear(queryType).getOrNull()
                        val clearType = doorClearStatus?.clearType ?: 0
                        val doorStatus = doorClearStatus?.status ?: 0
                        BoxToolLogUtils.savePrintln("业务流：门未开等待门物理状态变为【$clearType】|【$doorStatus】")
                        if (clearType == 0 && doorStatus == CmdCode.GE_OPEN) {
                            _currentStep.value = LockerStep.WAITING_OPEN_CLEAR
                            weightAfterOpening =
                                SerialPortSdk.queryWeight(doorGex).getOrNull()?.weight.toString()
                            BoxToolLogUtils.savePrintln("业务流：打开后的重量：$weightAfterOpening")
                            DatabaseManager.upTransOpenStatus(
                                AppUtils.getContext(), EntityType.WEIGHT_TYPE_1, transId
                            )
                            dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, defauleWeight, defauleWeight, openModel = model, flowEnd = false)
                            break// 门已确认开启
                        }
                        if (doorStatus == CmdCode.GE_OPEN_CLOSE_FAULT) {
                            noticeExection(CmdCode.GE_OPEN, doorGex, BusType.BUS_FAULT)
                            BoxToolLogUtils.savePrintln("业务流：门故障 打开后的重量：$weightAfterOpening")
                            throw Exception("门开前-门开故障: $doorStatus")
                        }
                        delay(500)
                    }
                }

                // --- 第三阶段：监测重量变化 ---
                _currentStep.value = LockerStep.WEIGHT_TRACKING
                BoxToolLogUtils.savePrintln("业务流：门已开启，开始监测实时重量。初始重量: $weightBeforeOpen")

                while (isActive) {
                    val curWeight =
                        SerialPortSdk.queryWeight(doorGex).getOrNull()?.weight.toString()
                    weightDuringOpening = curWeight
                    if (doorGex == CmdCode.GE1) {
                        curG1Weight = weightDuringOpening
                    } else {
                        curG2Weight = weightDuringOpening
                    }
                    if (_currentStep.value == LockerStep.WEIGHT_TRACKING) {
                        BoxToolLogUtils.savePrintln("业务流：打开后持续的重量：$weightDuringOpening")
                        dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, weightDuringOpening, defauleWeight, openModel = model, flowEnd = false)
                        _currentStep.value = LockerStep.WEIGHT_TRACKING
                    }

                    // --- 第五阶段：轮询等待门关闭 ---
                    withTimeout(300000) { // 30秒超时
                        val doorClearStatus = SerialPortSdk.openQueryClear(queryType).getOrNull()
                        val clearType = doorClearStatus?.clearType ?: 0
                        val doorStatus = doorClearStatus?.status ?: 0
                        BoxToolLogUtils.savePrintln("业务流：门开后等待门物理状态变为【$clearType】|【$doorStatus】")
                        if (clearType == 0 && doorStatus == CmdCode.GE_CLOSE) {
                            _currentStep.value = LockerStep.CLOSE
                            DatabaseManager.upTransCloseStatus(AppUtils.getContext(), 0, transId)
                            return@withTimeout
                        }
                        if (doorStatus == CmdCode.GE_OPEN_CLOSE_FAULT) {
                            BoxToolLogUtils.savePrintln("业务流：门故障 打开后的重量：$weightAfterOpening")
                            throw Exception("门开后-门关故障: $doorStatus")
                        }
                    }
                    //门关跳出查询门和重量
                    if (currentStep.value == LockerStep.CLOSE) {
                        break
                    }
                    // 这里可以增加一个逻辑：如果检测到重量稳定增加超过 X 秒，也可以自动触发下一步
                    delay(2000)
                }
                _currentStep.value = LockerStep.WAITING_CLOSE

                // --- 第六阶段：结算最终重量 ---
                _currentStep.value = LockerStep.FINISHED
                delay(500) // 等待机械震动停止，获取更准的重量
                weightAfterClosing =
                    SerialPortSdk.queryWeight(doorGex).getOrNull()?.weight.toString()
                toWeightAfterClosing = weightAfterClosing
                dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, weightDuringOpening, weightAfterClosing, openModel = model, flowEnd = true)
                BoxToolLogUtils.savePrintln("业务流：业务流完毕！ 开门前：$weightBeforeOpen, 开门后：$weightAfterOpening, 过程最高/最后：$weightDuringOpening, 关门后：$weightAfterClosing 启动业务数据上报 curWeight = $weightDuringOpening changeWeight = " + "${CalculationUtil.subtractFloats(weightAfterClosing, weightBeforeOpen)} " + "refWeight = " + "${CalculationUtil.subtractFloats(weightDuringOpening, weightAfterOpening)} " + "beforeUpWeight = $weightBeforeOpen " + "afterUpWeight = $weightAfterOpening " + "beforeDownWeight = $weightDuringOpening " + "afterDownWeight = $weightAfterClosing ")
            } catch (e: TimeoutCancellationException) {
                BoxToolLogUtils.savePrintln("业务流：操作超时，请检查柜门是否卡住")
            } catch (e: Exception) {
                BoxToolLogUtils.savePrintln("业务流：异常中断: ${e.message}")
            } finally {
                BoxToolLogUtils.savePrintln("业务流：完毕 finally")
                modelOpenBean = null
                isRunning.set(false)
                _currentStep.value = LockerStep.IDLE
                if (doorGex == CmdCode.GE1) {
                    curG1Weight = toWeightAfterClosing
                } else {
                    curG2Weight = toWeightAfterClosing
                }
                if (containersDB.isNotEmpty()) {
                    val index = doorGex - 1
                    val stateEntity = containersDB[index]
                    stateEntity.weigh = toWeightAfterClosing?.toFloat() ?: 0.00f
                    refrehContainers(stateEntity, index)
                }
                startContainersStatus() // 恢复全局状态轮询
            }
        }
    }

    /**通知异常
     * @param doorStartType 接收的异常类型
     * @param doorGex 当前推杆
     * @param setWarningContent 警告信息
     * */
    suspend fun noticeExection(doorStartType: Int, doorGex: Int, setWarningContent: String) =
        withContext(Dispatchers.IO) {
            _refBusStaChannel.send(MonitorWeight().apply {
                doorGeX = doorGex
                refreshType = RefBusType.REFRESH_TYPE_2
                warningContent = setWarningContent
            })
            when (doorGex) {
                1 -> {
                    when (doorStartType) {
                        CmdCode.GE_OPEN -> {
                            maptDoorFault[FaultType.FAULT_CODE_111] = true
                        }

                        CmdCode.GE_CLOSE -> {
                            maptDoorFault[FaultType.FAULT_CODE_110] = true
                        }
                    }

                }

                2 -> {
                    when (doorStartType) {
                        CmdCode.GE_OPEN -> {
                            maptDoorFault[FaultType.FAULT_CODE_121] = true
                        }

                        CmdCode.GE_CLOSE -> {
                            maptDoorFault[FaultType.FAULT_CODE_120] = true
                        }
                    }
                }
            }
        }

    var deteServiceCloseJob: Job? = null

    /***检测服务服务是否下发关闭*/
    fun deteServiceClose() {
        if (deteServiceCloseJob?.isActive == true) {
            Loge.e("流程  refreshTimerDoorClose 启动检测门状态轮询已在运行")
            return
        }
        deteServiceCloseJob = ioScope.launch {
            while (isActive) {
                delay(10000L)
                if (isRunning.getAndSet(true)) {
                    BoxToolLogUtils.savePrintln("业务流：定时检测服务器未下发指令 -> 有业务在处理中")
                    return@launch
                }
                val weights = DatabaseManager.queryWeightStatus(
                    AppUtils.getContext(), EntityType.WEIGHT_TYPE_10
                )
                BoxToolLogUtils.savePrintln("业务流：定时检测服务器未下发指令 -> 当前未处理：${weights.isEmpty()}")
                if (weights.isEmpty()) {
                    cancelServiceClose()
                }
                weights.forEach { weight ->
                    val doorClose = DoorCloseBean().apply {
                        cmd = CmdValue.CMD_CLOSE_DOOR
                        transId = weight.transId
                        cabinId = weight.cabinId
                        phoneNumber = ""
                        //換算的重量
                        curWeight = weight.curWeight
                        //上称物品的重量
                        changeWeight = weight.changeWeight
                        refWeight = weight.refWeight

                        //未上称物品前重量
                        beforeUpWeight = weight.beforeUpWeight
                        afterUpWeight = weight.afterUpWeight

                        //已上称物品前重量
                        beforeDownWeight = weight.beforeDownWeight
                        afterDownWeight = weight.afterDownWeight

                        timestamp = AppUtils.getDateYMDHMS()
                    }
                    val json = JsonBuilder.convertToJsonString(doorClose)
                    sendText(json)
                    BoxToolLogUtils.savePrintln("业务流：定时检测服务器未下发指令 -> 再次上报：${doorClose}")
                }
            }
        }
    }

    fun cancelServiceClose() {
        deteServiceCloseJob?.cancel()
        deteServiceCloseJob = null
    }

    suspend fun startLockerClose(model: DoorCloseBean) = withContext(Dispatchers.IO) {
        val transId = model.transId
        val queryTrans =
            transId?.let { DatabaseManager.queryTransEntity(AppUtils.getContext(), it) }
        if (queryTrans != null) {
            val weight = transId?.let { tid ->
                DatabaseManager.queryWeightId(AppUtils.getContext(), tid)
            }
            if (weight != null) {
                weight.status = EntityType.WEIGHT_TYPE_1 //接收服务器关闭完成
                val s = DatabaseManager.upWeightEntity(
                    AppUtils.getContext(), weight
                ) //刷新称重信息 根据事务id来
                Loge.e("流程 refreshWeightStatus 刷新本地数据 $s ${Gson().toJson(weight)}")
            }
            val trans = transId?.let { tid ->
                DatabaseManager.queryTransEntity(AppUtils.getContext(), tid)
            }
            Loge.e("流程 refreshWeightStatus trans：${trans}")
            if (trans != null) {
                trans.closeStatus = EntityType.WEIGHT_TYPE_1//接收服务器关闭完成
                val s = DatabaseManager.upTransEntity(
                    AppUtils.getContext(), trans
                ) //刷新事务信息 根据事务id来
                Loge.e("流程 refreshWeightStatus 刷新本地事务数据 $s ${Gson().toJson(trans)}")
            }
            BoxToolLogUtils.savePrintln("接收到服务器 关闭 门打开:${queryTrans.openStatus} | 门关闭：${queryTrans.closeStatus} 业务：${weight?.status ?: -1}")
        }
    }

    /**
     * 提取出来的 UI 更新私有方法，避免主逻辑过于臃肿
     */
    private fun updateWeightUI(beforeUp: String, afterUp: String, beforeDown: String, afterDown: String, isEnd: Boolean = false) {
        val prefix = if (isEnd) "业务结束--" else ""
        val msg = "${prefix}实时数据：\n" + "当前重量 = $beforeDown\n" + "总重量变化 = ${
            CalculationUtil.subtractFloats(afterDown, beforeUp)
        }\n" + "投递参考值 = ${
            CalculationUtil.subtractFloats(
                beforeDown, afterUp
            )
        }\n" + "开门前:$beforeUp | 开门后:$afterUp | 关门前:$beforeDown | 关门后:$afterDown"
        setFlowByteArrayWeight(msg)
    }


    //插入重量
    suspend fun dbBeforeWeight(weightBeforeOpen: String, model: DoorOpenBean) =
        withContext(Dispatchers.IO) {
            val rowTrans = DatabaseManager.insertTrans(AppUtils.getContext(), TransEntity().apply {
                cmd = CmdValue.CMD_OPEN_DOOR
                transId = model.transId
                openType = model.openType
                transType = actionType
                userId = model.userId
                cabinId = model.cabinId
                openStatus = -1
                time = AppUtils.getDateYMDHMS()
            })
            val rowWeight =
                DatabaseManager.insertWeight(AppUtils.getContext(), WeightEntity().apply {
                    cmd = CmdValue.CMD_OPEN_DOOR
                    openType = model.openType
                    transId = model.transId
                    curWeightY = weightBeforeOpen
                    curWeight = weightBeforeOpen
                    time = AppUtils.getDateYMDHMS()
                    cabinId = when (doorGeX) {
                        CmdCode.GE1 -> {
                            cur1Cabinld
                        }

                        CmdCode.GE2 -> {
                            cur2Cabinld
                        }

                        else -> {
                            "-1"
                        }
                    }
                    status = EntityType.WEIGHT_TYPE_10
                })
            val doorOpen = DoorOpenBean().apply {
                cmd = CmdValue.CMD_OPEN_DOOR//回应服务器
                transId = model.transId
                openType = model.openType
                cabinId = model.cabinId
                phoneNumber = model.phoneNumber
                curWeight = weightBeforeOpen
                curWeightY = weightBeforeOpen
                retCode = 0
                timestamp = AppUtils.getDateYMDHMS()
            }
            val json = JsonBuilder.convertToJsonString(doorOpen)
            sendText(json)
            BoxToolLogUtils.savePrintln("业务流：门开启中 打开结算页 $rowTrans $rowWeight $doorOpen")
        }

    //播报语音
    suspend fun playVoice(type: Int) = withContext(Dispatchers.IO) {
        Loge.e("流程 toGoOpenCloseAudio ${if (type == 1) "播报打开" else "播报关闭"}")
        when (type) {
            CmdCode.GE_OPEN -> {
                val isAudio = FileMdUtil.checkAudioFileExists("opendoor.wav")
                Loge.e("流程 播放语音 $isAudio - true：播放下载语音")
                if (!isAudio) {
                    MediaPlayerHelper.playAudioAsset(AppUtils.getContext(), "opendoor.wav")
                } else {
                    MediaPlayerHelper.playAudioFromAppFiles(
                        AppUtils.getContext(), "audio", "opendoor.wav"
                    )
                }
            }

            CmdCode.GE_CLOSE -> {
                val isAudio = FileMdUtil.checkAudioFileExists("closedoor.wav")
                Loge.e("流程 播放语音 $isAudio - true：播放下载语音")
                if (!isAudio) {
                    MediaPlayerHelper.playAudioAsset(AppUtils.getContext(), "closedoor.wav")
                } else {
                    MediaPlayerHelper.playAudioFromAppFiles(
                        AppUtils.getContext(), "audio", "closedoor.wav"
                    )
                }
            }
        }
    }

    fun removeRetryPrefix(input: String): String {
        return if (input.startsWith("retry-")) {
            input.removePrefix("retry-")
        } else {
            input
        }
    }
    //拍照前 Before taking 拍照后 After taking
    /***
     * @param switchType 0.关 1.开
     */
    suspend fun takePhoto(switchType: Int = -1) = withContext(Dispatchers.IO) {
        val transId = modelOpenBean?.transId ?: "transId"
        val setTransId = removeRetryPrefix(transId)
        val nameIn =
            "s-${setTransId}-i-$switchType-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}.jpg"
        val nameOut =
            "s-${setTransId}-o-$switchType-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}.jpg"
        val dir = File(
            AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action"
        )
        if (!dir.exists()) dir.mkdirs()
        val fileIn = File(dir, nameIn)
        val fileOut = File(dir, nameOut)
        when (switchType) {
            1 -> {
                cameraManagerNew.takePicture("0", switchType, "内", fileIn) { toFile ->
                    BoxToolLogUtils.saveCamera("拍照成功 开门 内 $toFile ${toFile?.name}")
                    toFile?.name?.let {
                        uploadPhoto(
                            curSn, setTransId, 0, it, switchType.toString()
                        )
                    }
                    toFile?.absolutePath?.let {
                        toGoInsertPhoto(
                            setTransId, switchType.toString(), 0, it
                        )
                    }
                    setRefBusStaChannel(MonitorWeight().apply {
                        refreshType = RefBusType.REFRESH_TYPE_4
                        takePhotoUrl = fileIn.absolutePath
                    })
                }

                delay(3000)
                cameraManagerNew.takePicture("1", switchType, "外", fileOut) { toFile ->
                    BoxToolLogUtils.saveCamera("拍照成功 开门 外 $toFile ${toFile?.name}")
                    toFile?.name?.let {
                        uploadPhoto(
                            curSn, setTransId, 2, it, switchType.toString()
                        )
                    }
                    toFile?.absolutePath?.let {
                        toGoInsertPhoto(
                            setTransId, switchType.toString(), 1, it
                        )
                    }
                    setRefBusStaChannel(MonitorWeight().apply {
                        refreshType = RefBusType.REFRESH_TYPE_4
                        takePhotoUrl = fileOut.absolutePath
                    })


                }
            }

            0 -> {
                cameraManagerNew.takePicture("1", switchType, "外", fileOut) { toFile ->
                    BoxToolLogUtils.saveCamera("拍照成功 关门 外$toFile ${toFile?.name}")
                    toFile?.name?.let {
                        uploadPhoto(
                            curSn, setTransId, 3, it, switchType.toString()
                        )
                    }
                    toFile?.absolutePath?.let {
                        toGoInsertPhoto(
                            setTransId, switchType.toString(), 1, it
                        )
                    }
                    setRefBusStaChannel(MonitorWeight().apply {
                        refreshType = RefBusType.REFRESH_TYPE_4
                        takePhotoUrl = fileOut.absolutePath
                    })

                }
                delay(3000)
                cameraManagerNew.takePicture("0", switchType, "内", fileIn) { toFile ->
                    BoxToolLogUtils.saveCamera("拍照成功 关门 内 $toFile ${toFile?.name}")
                    toFile?.name?.let {
                        uploadPhoto(
                            curSn, setTransId, 1, it, switchType.toString()
                        )
                    }
                    toFile?.absolutePath?.let {
                        toGoInsertPhoto(
                            setTransId, switchType.toString(), 0, it
                        )
                    }
                    setRefBusStaChannel(MonitorWeight().apply {
                        refreshType = RefBusType.REFRESH_TYPE_4
                        takePhotoUrl = fileIn.absolutePath
                    })
                }
            }
        }/**/

    }

    suspend fun takePhotoRemote(photoModel: PhotoBean) = withContext(Dispatchers.IO) {
        if (_currentStep.value == LockerStep.IDLE && !isRunning.get()) {
            delay(1000)
            val dir = File(
                AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action"
            )
            if (!dir.exists()) dir.mkdirs()
            when (photoModel.photoType) {
                -1 -> {
                    val setTransId = modelOpenBean?.transId ?: "transId"
                    val nameIn =
                        "s-${setTransId}-$45-i-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}"
                    val nameOut =
                        "s-${setTransId}-$45-o-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}"
                    val fileIn = File(dir, nameIn)
                    val fileOut = File(dir, nameOut)

                    cameraManagerNew.takePicture("0", 45, "内", fileIn) { toFile ->
                        BoxToolLogUtils.saveCamera("拍照成功 远程内外 $toFile ${toFile?.name}")
                        toFile?.name?.let { uploadPhoto(curSn, setTransId, 4, it, "45") }
                        toFile?.absolutePath?.let { toGoInsertPhoto(setTransId, "45", 0, it) }
                    }
                    delay(5000)
                    cameraManagerNew.takePicture("1", 45, "外", fileOut) { toFile ->
                        BoxToolLogUtils.saveCamera("拍照成功 远程内外 $toFile ${toFile?.name}")
                        toFile?.name?.let { uploadPhoto(curSn, setTransId, 5, it, "45") }
                        toFile?.absolutePath?.let { toGoInsertPhoto(setTransId, "45", 1, it) }
                    }
                }

                4 -> {

                    val setTransId = modelOpenBean?.transId ?: "transId"
                    val nameIn =
                        "${setTransId}-$45-in-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}"
                    val fileIn = File(dir, nameIn)
                    cameraManagerNew.takePicture("0", 45, "内", fileIn) { toFile ->
                        BoxToolLogUtils.saveCamera("拍照成功 远程 内 $toFile ${toFile?.name}")
                        toFile?.name?.let { uploadPhoto(curSn, setTransId, 4, it, "45") }
                        toFile?.absolutePath?.let { toGoInsertPhoto(setTransId, "45", 0, it) }
                    }

                }

                5 -> {
                    val setTransId = modelOpenBean?.transId ?: "transId"
                    val nameOut =
                        "${setTransId}-$45-out-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}"
                    val fileOut = File(dir, nameOut)
                    cameraManagerNew.takePicture("1", 45, "外", fileOut) { toFile ->
                        BoxToolLogUtils.saveCamera("拍照成功 远程 外 $toFile ${toFile?.name}")
                        toFile?.name?.let { uploadPhoto(curSn, setTransId, 5, it, "45") }
                        toFile?.absolutePath?.let { toGoInsertPhoto(setTransId, "45", 1, it) }
                    }
                }
            }

        }
    }

    // 使用 Channel 或 LiveData 传递结果
    private val _refBusStaChannel = Channel<MonitorWeight>()
    val refBusStaChannel = _refBusStaChannel.receiveAsFlow()

    data class MonitorWeight(
        /** 1.重量 2.警告 3.门开 4.结算页 */
        var refreshType: Int = -1,
        /** 格口几 */
        var doorGeX: Int = CmdCode.GE,
        /** 漫溢 正常 故障 */
        var warningContent: String = "",
        /** 拍照的路径 */
        var takePhotoUrl: String = "",
        /** 格口一重量 */
        var curG1WeightValue: String? = "0.00",
        /** 格口二重量 */
        var curG2WeightValue: String? = "0.00",
        /** 开门前重量 */
        var weightBeforeOpenValue: String? = "0.00",
        /** 开门后重量 */
        var weightAfterOpeningValue: String? = "0.00",
        /** 关门前重量 */
        var weightDuringOpeningValue: String? = "0.00",
        /** 关门后重量 */
        var weightAfterClosingValue: String? = "0.00",
    )

    fun setRefBusStaChannel(result: MonitorWeight) {
        viewModelScope.launch(Dispatchers.IO) {
            _refBusStaChannel.send(result)
        }
    }

    //刷新重量
    /***
     * @param weightBeforeOpen 开门前重量
     * @param weightAfterOpening 开门后重量
     * @param weightDuringOpening 关门前重量
     * @param weightAfterClosing 关门后重量
     */
    suspend fun dbBeforeWeightRefresh(
        weightBeforeOpen: String,
        weightAfterOpening: String,
        weightDuringOpening: String,
        weightAfterClosing: String,
        openModel: DoorOpenBean,
        flowEnd: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val transMax = DatabaseManager.queryTransMax(AppUtils.getContext())
        if (transMax != null) {
            val getTransId = transMax.transId ?: ""
            val weight = DatabaseManager.queryWeightId(AppUtils.getContext(), getTransId)
            if (weight != null) {
                val type = openModel.openType
                if (type == 1) {
                    //当前重量
                    weight.curWeightY = weightAfterClosing
                    //当前重量
                    weight.curWeight = weightAfterClosing

                    weight.changeWeight = CalculationUtil.subtractFloats(
                        weightAfterClosing, weightBeforeOpen
                    )//关门后重量-开门前重量
                    weight.refWeight = CalculationUtil.subtractFloats(
                        weightDuringOpening, weightAfterOpening
                    )//关门前重量-开门后重量


                    //未上称物品前重量
                    weight.beforeUpWeight = weightBeforeOpen//开门前重量
                    weight.afterUpWeight = weightAfterOpening//开门后重量

                    //已上称物品前重量
                    weight.beforeDownWeight = weightDuringOpening//关门前重量
                    weight.afterDownWeight = weightAfterClosing//关门后重量
                    //时间
                    weight.time = AppUtils.getDateYMDHMS()
                }
                if (type == 2) {
                    val result = CalculationUtil.subtractFloats(
                        weightBeforeOpen, weightAfterClosing
                    )
                    //当前重量
                    weight.curWeightY = weightAfterClosing
                    //当前重量
                    weight.curWeight = weightAfterClosing

                    weight.changeWeight = result //开门前重量-关门后重量
                    weight.refWeight = result//开门前重量-关门后重量


                    //未上称物品前重量
                    weight.beforeUpWeight = weightBeforeOpen//开门前重量
                    weight.afterUpWeight = weightAfterOpening//开门后重量

                    //已上称物品前重量
                    weight.beforeDownWeight = weightDuringOpening//关门前重量
                    weight.afterDownWeight = weightAfterClosing//关门后重量
                    //时间
                    weight.time = AppUtils.getDateYMDHMS()

                }
                val s = DatabaseManager.upWeightEntity(AppUtils.getContext(), weight)
                BoxToolLogUtils.savePrintln("业务流：刷新重量 更新成功 $s ${SendOpenText.fromStatus(if (openModel.openType == 1) 100 else 200)} ${openModel.transId} ")

            }
            _refBusStaChannel.send(MonitorWeight().apply {
                refreshType = RefBusType.REFRESH_TYPE_1
                weightBeforeOpenValue = weightBeforeOpen
                weightAfterOpeningValue = weightAfterOpening
                weightDuringOpeningValue = weightDuringOpening
                weightAfterClosingValue = weightAfterClosing
            })
            if (flowEnd) {
                val doorClose = DoorCloseBean().apply {
                    cmd = CmdValue.CMD_CLOSE_DOOR
                    transId = openModel.transId
                    cabinId = openModel.cabinId
                    phoneNumber = ""
                    //当前设备称重重量
                    curWeight = weight.curWeight
                    curWeightY = weight.curWeight

                    //上称物品的重量
                    changeWeight = weight.changeWeight
                    refWeight = weight.refWeight

                    //未上称物品前重量
                    beforeUpWeight = weight.beforeUpWeight
                    afterUpWeight = weight.afterUpWeight

                    //已上称物品前重量
                    beforeDownWeight = weight.beforeDownWeight
                    afterDownWeight = weight.afterDownWeight

                    timestamp = AppUtils.getDateYMDHMS()
                }
                val json = JsonBuilder.convertToJsonString(doorClose)
                BoxToolLogUtils.savePrintln("业务流：刷新重量 发送关门成功 $doorClose")
                sendText(json)
            }
        } else {
            BoxToolLogUtils.savePrintln("业务流：刷新重量 无")
        }
    }

    private var containersDB = mutableListOf<StateEntity>()

    private var containersJob: Job? = null
    fun cancelContainersStatusJob() {
        println("验证方式 取消柜体查询")
        containersJob?.cancel()
        containersJob = null
    }

    /****
     * 投递柜状态查询
     */
    fun startContainersStatus() {
        println("进来查询投递柜")
        if (containersJob?.isActive == true) {
            BoxToolLogUtils.savePrintln("业务流： 柜体 轮询已在运行")
            return
        }

        containersJob = ioScope.launch {
            while (isActive) {
                println("进来查询投递柜开始")
                SerialPortSdk.queryStatus().onSuccess { result ->
                    if (containersDB.isEmpty()) {
                        containersDB =
                            DatabaseManager.queryStateList(AppUtils.getContext()).toMutableList()
                    }
                    println("进来查询投递柜结果")
                    val size = containersDB.size
                    BoxToolLogUtils.savePrintln("业务流：startStatus onSuccess $result")
                    result.containers.withIndex().forEach { (index, lower) ->
                        when (index) {
                            0 -> {
                                val state = containersDB[0]
                                //取出原始重量
                                val weightNew = lower.weigh?.toFloat() ?: 0.0f
                                val weightOld = state.weigh.toString()
                                //处理重量浮动变化
                                val isChange = CalculationUtil.subtractFloatsBoolean(
                                    weightNew.toString(), weightOld
                                )
                                val result1 =
                                    WeightChangeStorage(AppUtils.getContext()).putWithCooldown(
                                        "key_weight1", if (isChange) "success" else "Failure", devWeiChaMapSend[0]
                                            ?: false
                                    )
                                if (result1 && isChange) {
                                    devWeiChaMapCun[0] = weightNew.toString()
                                    devWeiChaMapSend[0] = true
                                }
                                Loge.e("执行重量变动 查询 下位机-1 |new:${lower.weigh} old:${weightOld} | 改：$isChange 结：$result1")
                                val irStateValue = lower.irStateValue ?: 0
                                val irDoorStatusValue = lower.doorStatusValue ?: 0
                                val lockStatus = lower.lockStatusValue ?: 0
                                val runStatus = lower.xzStatusValue
                                state.smoke = lower.smokeValue ?: 0
                                state.irState = irStateValue
                                state.doorStatus = irDoorStatusValue
                                state.weigh = weightNew
                                state.lockStatus = lockStatus
                                state.time = AppUtils.getDateYMDHMS()
                                val curG1Total = curG1TotalWeight.toFloat()
                                //实时总重量
                                val curG1Weight = state.weigh
                                //上报重量大于总重量则报提示
                                if (curG1Weight > curG1Total && irStateValue == 1) {
                                    state.capacity = 3
                                } else if (curG1Weight > curG1Total) {
                                    state.capacity = 2
                                } else if (irStateValue == 1) {
                                    state.capacity = 1
                                } else if (irStateValue == 0) {
                                    state.capacity = 0
                                }
                                val setIr1 =
                                    SPreUtil[AppUtils.getContext(), SPreUtil.saveIr1, -1] as Int
                                if (setIr1 == 1) {
                                    maptDoorFault[FaultType.FAULT_CODE_11] =
                                        irStateValue == 1//下位机返回
                                } else {
                                    maptDoorFault[FaultType.FAULT_CODE_11] = false//下位机返回
                                    maptDoorFault[FaultType.FAULT_CODE_211] = false//下位机返回
                                }
                                maptDoorFault[FaultType.FAULT_CODE_91] =
                                    irDoorStatusValue == 3//下位机返回
                                maptDoorFault[FaultType.FAULT_CODE_5101] = runStatus == 0//下位机返回
                                maptDoorFault[FaultType.FAULT_CODE_5102] = runStatus == 2//下位机返回
                                refrehContainers(state, 0)
                            }

                            1 -> {
                                if (size > 1 && doorGeXType == CmdCode.GE2) {
                                    val state = containersDB[1]
                                    val weightNew = lower.weigh?.toFloat() ?: 0.0f
                                    val weightOld = state.weigh.toString()
                                    //处理重量浮动变化
                                    val isChange = CalculationUtil.subtractFloatsBoolean(
                                        weightNew.toString(), weightOld
                                    )
                                    val result1 =
                                        WeightChangeStorage(AppUtils.getContext()).putWithCooldown(
                                            "key_weight2", if (isChange) "success" else "Failure", devWeiChaMapSend[1]
                                                ?: false
                                        )
                                    if (result1 && isChange) {
                                        devWeiChaMapCun[1] = weightNew.toString()
                                        devWeiChaMapSend[1] = true
                                    }
                                    Loge.e("执行重量变动 查询 下位机-1 |new:${lower.weigh} old:${weightOld} | 改：$isChange 结：$result1")
                                    val irStateValue = lower.irStateValue ?: 0
                                    val irDoorStatusValue = lower.doorStatusValue ?: 0
                                    val lockStatus = lower.lockStatusValue ?: 0
                                    val runStatus = lower.xzStatusValue
                                    state.smoke = lower.smokeValue ?: 0
                                    state.irState = irStateValue
                                    state.doorStatus = irDoorStatusValue
                                    state.weigh = weightNew
                                    state.lockStatus = lockStatus
                                    state.time = AppUtils.getDateYMDHMS()
                                    val curG1Total = curG2TotalWeight.toFloat()
                                    //实时总重量
                                    val curG2Weight = state.weigh
                                    //上报重量大于总重量则报提示
                                    if (curG2Weight > curG1Total && irStateValue == 1) {
                                        state.capacity = 3
                                    } else if (curG2Weight > curG1Total) {
                                        state.capacity = 2
                                    } else if (irStateValue == 1) {
                                        state.capacity = 1
                                    } else if (irStateValue == 0) {
                                        state.capacity = 0
                                    }
                                    val setIr2 =
                                        SPreUtil[AppUtils.getContext(), SPreUtil.saveIr2, 1] as Int
                                    if (setIr2 == 1) {
                                        maptDoorFault[FaultType.FAULT_CODE_12] =
                                            irStateValue == 1//下位机返回
                                    } else {
                                        maptDoorFault[FaultType.FAULT_CODE_12] = false//下位机返回
                                        maptDoorFault[FaultType.FAULT_CODE_212] = false//下位机返回
                                    }
                                    maptDoorFault[FaultType.FAULT_CODE_92] =
                                        irDoorStatusValue == 3//下位机返回
                                    maptDoorFault[FaultType.FAULT_CODE_5201] = runStatus == 0//下位机返回
                                    maptDoorFault[FaultType.FAULT_CODE_5202] = runStatus == 2//下位机返回
                                    refrehContainers(state, 1)
                                }
                            }

                        }

                    }
                }.onFailure {
                    BoxToolLogUtils.savePrintln("业务流： startStatus onFailure")
                }
                delay(1000)
                println("进来查询投递柜结束")
            }
        }
    }


    suspend fun refrehContainers(state: StateEntity, index: Int) = withContext(Dispatchers.IO) {
        val row = DatabaseManager.upStateEntity(AppUtils.getContext(), state)
        Loge.e("流程 synStateHeart 同步更新心跳上传重量 $isClearStatus $row ${state}")
        containersDB[index] = state//刷新格口状态信息
        if (containersDB.size == 1) {
            _refBusStaChannel.send(MonitorWeight().apply {
                refreshType = RefBusType.REFRESH_TYPE_1
                doorGeX = CmdCode.GE1
                curG1WeightValue = state.weigh?.toString()
            })
        } else {
            if (index == 0) {
                _refBusStaChannel.send(MonitorWeight().apply {
                    refreshType = RefBusType.REFRESH_TYPE_1
                    doorGeX = CmdCode.GE1
                    curG1WeightValue = state.weigh?.toString()
                })
            } else if (index == 1) {
                _refBusStaChannel.send(MonitorWeight().apply {
                    refreshType = RefBusType.REFRESH_TYPE_1
                    doorGeX = CmdCode.GE2
                    curG2WeightValue = state.weigh?.toString()
                })
            }

        }

        //刷新满溢状态
        when (index) {
            0 -> {
                val curG1Total = curG1TotalWeight.toFloat()
                //实时总重量
                val curG1Weight = state.weigh
                val setIr1 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr1, -1] as Int
                //上报重量大于总重量则报提示
                if (curG1Weight > curG1Total && setIr1 == 1) {
                    _refBusStaChannel.send(MonitorWeight().apply {
                        refreshType = RefBusType.REFRESH_TYPE_2
                        doorGeX = CmdCode.GE1
                        warningContent = BusType.BUS_OVERFLOW
                    })
                }
            }

            1 -> {
                val curG2Total = curG2TotalWeight.toFloat()
                //实时总重量
                val curG2Weight = state.weigh
                val setIr2 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr2, -1] as Int
                //上报重量大于总重量则报提示
                if (curG2Weight > curG2Total && setIr2 == 1) {
                    _refBusStaChannel.send(MonitorWeight().apply {
                        refreshType = RefBusType.REFRESH_TYPE_2
                        doorGeX = CmdCode.GE2
                        warningContent = BusType.BUS_OVERFLOW
                    })
                }
            }
        }
    }

    suspend fun restartAppCloseDoor(doorGeX: Int) = withContext(Dispatchers.IO) {
        val code = when (doorGeX) {
            CmdCode.GE1 -> CmdCode.GE12
            CmdCode.GE2 -> CmdCode.GE22
            else -> -1
        }
        val doorStatus = SerialPortSdk.turnDoorStatus(doorGeX).getOrNull()?.status
        BoxToolLogUtils.savePrintln("业务流：重启动应用 等待门物理状态变为【$doorStatus】")
        if (doorStatus == CmdCode.GE_OPEN) {
            toGoOpenCloseAudio(CmdCode.GE_CLOSE)
            val turnDoor = SerialPortSdk.turnDoor(code)
            BoxToolLogUtils.savePrintln("业务流：开启启动关门【$turnDoor】")
        }
        delay(2000)
    }

    private var queryStatusJob: Job? = null

    fun cancelStartQueryStatus() {
        BoxToolLogUtils.savePrintln("业务流： 取消柜查询柜体状态")
        queryStatusJob?.cancel()
        queryStatusJob = null
    }

    var isLookState = false
    fun startQueryStatus(stateResult: (containers: MutableList<ContainersResult>) -> Unit) {
        if (queryStatusJob?.isActive == true) {
            BoxToolLogUtils.savePrintln("业务流：查询柜体状态 轮询已在运行")
            return
        }
        queryStatusJob = viewModelScope.launch {
            while (isActive && isLookState) {
                SerialPortSdk.queryStatus().onSuccess { result ->
                    stateResult(result.containers)
                }.onFailure {
                    BoxToolLogUtils.savePrintln("业务流： startStatus onFailure")
                }
            }
        }
    }

    /***
     * @param doorGeX
     * 启动推杆
     */
    fun startChipVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            val chipStep11 =
                SerialPortSdk.firmwareUpgrade78910(11, byteArrayOf(0xAA.toByte(), 0xAB.toByte(), 0xAC.toByte()))
            if (chipStep11.isFailure) throw Exception("查询版本失败: ${chipStep11.exceptionOrNull()?.message}")
            val stepStatus11 = chipStep11.getOrNull()?.chipVersion ?: SPreUtil.gversion
            println("查询到的版本：$stepStatus11")
//            SPreUtil.put(AppUtils.getContext(), SPreUtil.gversion, stepStatus11)
        }
    }

    /***
     * @param doorGeX
     * 启动推杆
     */
    fun startTurnDoor(doorGeX: Int) {
        viewModelScope.launch {
            val status = SerialPortSdk.turnDoor(doorGeX).getOrNull()?.status
            BoxToolLogUtils.savePrintln("业务流：等待推杆状态【$status】")
        }

    }

    /***
     * @param inOut 内外类型
     * 启动灯光开启
     */
    fun startLights(inOut: Int) {
        viewModelScope.launch {
            val status = SerialPortSdk.startLights(inOut).getOrNull()?.status
            BoxToolLogUtils.savePrintln("业务流：等待灯光状态【$status】")
        }
    }

    /***
     * @param doorGeX
     * @param rodHinderValue
     * @param rodHinderResult
     * 启动设置助力值
     */
    fun startRodHinder(
        doorGeX: Int,
        rodHinderValue: Int,
        rodHinderResult: (lockerNo: Int, rodHinderValue: Int) -> Unit,
    ) {
        viewModelScope.launch {
            val rodHinderValue = SerialPortSdk.startRodHinder(doorGeX, rodHinderValue).getOrNull()
            rodHinderValue?.let { rh ->
                rodHinderResult(rh.locker, rh.rodHinderValue)
                tipMessage("设置阻力阀值成功 ${rh.rodHinderValue}")
            }
            BoxToolLogUtils.savePrintln("业务流：等待阻力值状态【$rodHinderValue】")
        }
    }

    /***
     * @param doorGeX
     * @param onGetDoorStatus
     * 查询投递门状态
     */
    fun startDoorStratus(doorGeX: Int, onGetDoorStatus: (status: Int) -> Unit) {
        viewModelScope.launch {
            val doorStatus = SerialPortSdk.turnDoorStatus(doorGeX).getOrNull()?.status ?: 0
            onGetDoorStatus(doorStatus)
            BoxToolLogUtils.savePrintln("业务流：等待投递门状态【$doorStatus】")
        }
    }

    /***
     * @param code
     * 打开清运门
     */
    fun startClearDoor(code: Int) {
        viewModelScope.launch {
            val doorStatus = SerialPortSdk.openQueryClear(code).getOrNull()?.status ?: 0
            BoxToolLogUtils.savePrintln("业务流：等待清运门状态【$doorStatus】")
        }
    }


    /***
     * @param doorGeX
     * 获取重量
     */
    fun startQueryWeight(doorGeX: Int) {
        viewModelScope.launch {
            val weight = SerialPortSdk.queryWeight(doorGeX).getOrNull()?.weight ?: 0
            BoxToolLogUtils.savePrintln("业务流：等待获取重量【$weight】")
            tipMessage("等待获取重量 $weight")
        }
    }

    /***
     * @param doorGeX
     * @param code
     * 去皮清零
     */
    fun startCalibrationQP(doorGeX: Int, code: Int) {
        viewModelScope.launch {
            val status =
                SerialPortSdk.startCalibrationQP(doorGeX, code).getOrNull()?.caliStatus ?: 0
            tipMessage("去皮清零 ${if (status == 1) "成功" else "失败"}")
            BoxToolLogUtils.savePrintln("业务流：等待去皮清零状态【$status】")
        }
    }

    /***
     * @param doorGeX
     * @param code
     * 校准
     */
    fun startCalibration(doorGeX: Int, code: Int) {
        viewModelScope.launch {
            val status = SerialPortSdk.startCalibration(doorGeX, code).getOrNull()?.caliStatus ?: 0
            when (code) {
                //零点校准
                CmdCode.CALIBRATION_1 -> {
                    if (status == 1) {
                        caliBefore2.emit(true)
                    } else {
                        caliBefore2.emit(false)
                    }
                }
                //校准2KG
                CmdCode.CALIBRATION_2 -> {
                    if (status == 1) {
                        caliResult.emit(true)
                    } else {
                        caliResult.emit(false)
                    }
                }
                //校准25KG
                CmdCode.CALIBRATION_3 -> {
                    if (status == 1) {
                        caliResult.emit(true)
                    } else {
                        caliResult.emit(false)
                    }
                }
                //校准100KG
                CmdCode.CALIBRATION_4 -> {
                    if (status == 1) {
                        caliResult.emit(true)
                    } else {
                        caliResult.emit(false)
                    }
                }
                //校准100KG
                CmdCode.CALIBRATION_5 -> {
                    if (status == 1) {
                        caliResult.emit(true)
                    } else {
                        caliResult.emit(false)
                    }
                }
            }
        }
    }


    fun stopAll() {
        cancelContainersStatusJob()
        cancelStartQueryStatus()
        closeSock()
        closeAllScope()
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    /*******************************************新方案调试******************************************************/
}
