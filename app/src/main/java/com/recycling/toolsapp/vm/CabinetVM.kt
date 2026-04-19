package com.recycling.toolsapp.vm

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
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
import com.recycling.toolsapp.BuildConfig
import com.recycling.toolsapp.FaceApplication
import com.recycling.toolsapp.db.DatabaseManager
import com.recycling.toolsapp.http.FileCleaner
import com.recycling.toolsapp.http.RepoImpl
import com.recycling.toolsapp.http.TaskDelDateScheduler
import com.recycling.toolsapp.http.TaskLightsScheduler
import com.recycling.toolsapp.model.ConfigEntity
import com.recycling.toolsapp.model.FileEntity
import com.recycling.toolsapp.model.LatticeEntity
import com.recycling.toolsapp.model.LogEntity
import com.recycling.toolsapp.model.ResEntity
import com.recycling.toolsapp.model.StateEntity
import com.recycling.toolsapp.model.TransEntity
import com.recycling.toolsapp.model.WeightEntity
import com.recycling.toolsapp.socket.AdminOverflowBean
import com.recycling.toolsapp.socket.ConfigBean
import com.recycling.toolsapp.socket.DoorCloseBean
import com.recycling.toolsapp.socket.DoorOpenBean
import com.recycling.toolsapp.socket.FaultBean
import com.recycling.toolsapp.socket.FaultInfo
import com.recycling.toolsapp.socket.OtaBean
import com.recycling.toolsapp.socket.PhotoBean
import com.recycling.toolsapp.utils.CalculationUtil
import com.recycling.toolsapp.utils.CmdValue
import com.recycling.toolsapp.utils.EntityType
import com.recycling.toolsapp.utils.EnumFaultState
import com.recycling.toolsapp.utils.FaultType
import com.recycling.toolsapp.utils.JsonBuilder
import com.recycling.toolsapp.utils.MediaPlayerHelper
import com.recycling.toolsapp.utils.RefBusType
import com.recycling.toolsapp.utils.ResType
import com.recycling.toolsapp.utils.TelephonyUtils
import com.recycling.toolsapp.utils.WeightChangeStorage
import com.recycling.toolsapp.view.AwesomeQRCode
import com.recycling.toolsapp.vm.CabinetVM.ConnectionState.*
import com.recycling.toolsapp.R
import com.recycling.toolsapp.utils.OSUtils
import com.serial.port.t.ContainersResult
import com.serial.port.t.ProtocolCodec
import com.serial.port.t.SendClearText
import com.serial.port.t.SendTurnText
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
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.internal.wait
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
import java.util.concurrent.atomic.AtomicInteger
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


    private val sendFileByte232 = Channel<ByteArray>()

    /***
     * 插入数据库
     */
    private fun toGoInsertPhoto(setTransId: String, switchType: String, inOut: Int, filePath: String) {
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
            val fileDb = DatabaseManager.queryFileEntity(AppUtils.getContext(), cmdValue, setTransId)
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
    private val caliBefore2 = MutableStateFlow<Int>(-1)
    val getCaliBefore2: StateFlow<Int> = caliBefore2.asStateFlow()

    //校准结果
    private val caliResult = MutableStateFlow<Int>(-1)
    val getCaliResult: StateFlow<Int> = caliResult.asStateFlow()

    //处理网络提示语
    private val flowIsNetworkMsg = MutableSharedFlow<String>(replay = 1)
    val _isNetworkMsg = flowIsNetworkMsg.asSharedFlow()

    //校准前
    private val flowTestClearDoor = MutableStateFlow<Int>(-1)
    val getTestClearDoor: StateFlow<Int> = flowTestClearDoor.asStateFlow()

    //启动显示格口页
    private val flowLoginCmd = MutableSharedFlow<Boolean>(replay = 0)
    val getLoginCmd = flowLoginCmd.asSharedFlow()

    //信号值读取
    private val flowIsReadSignal = MutableStateFlow<Int>(-1)
    val isReadSignal: StateFlow<Int> = flowIsReadSignal.asStateFlow()


    /***
     * 提示语
     */
    fun tipMessage(msg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flowIsNetworkMsg.emit(msg)
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
            val m = mapOf("cmd" to CmdValue.CMD_PHONE_NUMBER_LOGIN, "phoneNumber" to phoneNumber, "timestamp" to System.currentTimeMillis())
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
            BoxToolLogUtils.savePrintln("业务流：手机登录")
        }
    }

    /***
     * 接收手机号登录后,发送手机号开门
     * @param cabinId
     * @param userId
     */
    fun toGoMobileOpen(cabinId: String, userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf("cmd" to CmdValue.CMD_PHONE_USER_OPEN_DOOR, "cabinId" to cabinId, "userId" to userId, "timestamp" to System.currentTimeMillis())
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
            curUserId = userId
            actionType = 1
        }
    }

    /**
     * 拍照上传成功
     */
    private fun toGoTPSucces(transId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf("cmd" to CmdValue.CMD_ADMIN_PHOTO, "type" to "res", "transId" to transId, "retCode" to 0, "timestamp" to System.currentTimeMillis())
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }
    }

    /***
     * 业务通信前，先登录
     * @param loginCount
     * @param sn 是初始化传过来的，
     * @param imsi iccid是登录传过来的
     * @param imei imei是初始化传过来的，
     * @param iccid iccid是登录传过来的
     */
    fun toGoCmdLogin(loginCount: Int? = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            val gversion = SPreUtil[AppUtils.getContext(), SPreUtil.gversion, CmdCode.GJ_VERSION] as Int
            Loge.e("流程 toGoCmdOtaBin handleVersionQuery login $gversion")
            val getSn = SPreUtil[AppUtils.getContext(), SPreUtil.init_sn, ""]
            val getImsi = SPreUtil[AppUtils.getContext(), SPreUtil.setImsi, ""]
            val getImei = SPreUtil[AppUtils.getContext(), SPreUtil.setImei, ""]
            val getIccid = SPreUtil[AppUtils.getContext(), SPreUtil.setIccid, ""]
            val m = mapOf("cmd" to CmdValue.CMD_LOGIN, "loginCount" to loginCount, "sn" to getSn, "imsi" to getImsi, "imei" to getImei, "iccid" to getIccid, "version" to gversion, "apkVersion" to AppUtils.getVersionName(), "timestamp" to System.currentTimeMillis())
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }
    }

    /***
     * OTA apk
     */
    private fun toGoCmdOtaAPK() {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf("cmd" to CmdValue.CMD_OTA_APK, "retCode" to 0, "timestamp" to System.currentTimeMillis())
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }
    }

    /***
     * OTA bin
     */
    private fun toGoCmdOtaBin() {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf("cmd" to CmdValue.CMD_OTA, "retCode" to 0, "timestamp" to System.currentTimeMillis())
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }
    }

    /***
     * 日志
     */
    private fun toGoCmdLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val m = mapOf("cmd" to CmdValue.CMD_UPLOAD_LOG, "retCode" to 0, "timestamp" to System.currentTimeMillis())
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
            if (autoCalcOverflow == 0 && overflowState == 1) {//服务器下发满溢 弹出满溢框
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState, true)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowStateValue, overflowState)
                when (cabinId) {
                    cur1Cabinld -> {
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState1, true)
                        maptDoorFault[FaultType.FAULT_CODE_2110] = true

                    }

                    cur2Cabinld -> {
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState2, true)
                        maptDoorFault[FaultType.FAULT_CODE_2120] = true
                    }
                }
            } else if (autoCalcOverflow == 0 && overflowState == 0) {
                maptDoorFault[FaultType.FAULT_CODE_2110] = false
                maptDoorFault[FaultType.FAULT_CODE_2120] = false
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState, true)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowStateValue, overflowState)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState1, false)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState2, false)
            } else {
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState, false)
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState1, false)
                maptDoorFault[FaultType.FAULT_CODE_2110] = false
                SPreUtil.put(AppUtils.getContext(), SPreUtil.overflowState2, false)
                maptDoorFault[FaultType.FAULT_CODE_2120] = false
            }
            val m = mapOf("cmd" to CmdValue.CMD_ADMIN_OVERFLOW, "type" to "res", "retCode" to 0, "transId" to transId, "timestamp" to System.currentTimeMillis())
            val json = JsonBuilder.convertToJsonString(m)
            sendText(json)
        }

    }

    /***
     * 上传异常
     * @param toType
     * @param toCabinIndex
     * @param toDesc
     */
    private fun toGoCmdUpFault(toType: Int, toCabinIndex: Int, toDesc: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val setToType = matchErrorCode(toType)
            val cabin = when (doorGeX) {
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
                    cabinId = cabin
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
     * 继续登录
     */
    val flowAgainLogin = MutableStateFlow(false)
    val getAgainLogin: MutableStateFlow<Boolean> = flowAgainLogin

    var againJob: Job? = null

    fun toGoAgainLogin() {
        Loge.e("流程 toGoAgainLogin 启动toGoAgainLogin")
        if (againJob?.isActive == true) {
            BoxToolLogUtils.savePrintln("业务流：登录 已在运行")
            return
        }
        againJob = ioScope.launch {
            while (isActive) {
                val state = state?.value ?: DISCONNECTED
                Loge.e("流程 toGoAgainLogin ：${getAgainLogin.value} | state $state")
                if (!getAgainLogin.value) {
                    tipMessage("登录失败，继续登录")
                    val currentCount = SPreUtil[AppUtils.getContext(), SPreUtil.loginCount, 0] as Int
                    toGoCmdLogin(currentCount)//继续登陆
                } else {
                    cancelJobAgain()
                }
                delay(5000L)
            }
        }
    }

    fun cancelJobAgain() {
        flowAgainLogin.value = false //标记未登录
        againJob?.cancel()
        againJob = null
    }


    //更新资源文件下载
    private fun upNetResDb(typeMsg: String, resourceEntity: ResEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val row = DatabaseManager.upResEntity(AppUtils.getContext(), resourceEntity)
            insertInfoLog(LogEntity().apply {
                cmd = "ota/apk"
                msg = typeMsg
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
            DatabaseManager.copyDatabasesDirectory(AppUtils.getContext(), "socket_box_crash")
            delay(1000)
            // 目标文件夹路径
            val targetFolder = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "socket_box_crash")
            // 压缩包输出路径
            val zipOutput = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${AppUtils.getDateYMDHMS3()}_socket_box_crash.zip")

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
    private fun matchErrorCode(toType: Int): Int {
        return when (toType) {
            FaultType.FAULT_CODE_111, FaultType.FAULT_CODE_121 -> {
                1
            }

            FaultType.FAULT_CODE_110, FaultType.FAULT_CODE_120 -> {
                2
            }

            FaultType.FAULT_CODE_311, FaultType.FAULT_CODE_321 -> {
                3
            }

            FaultType.FAULT_CODE_410, FaultType.FAULT_CODE_420 -> {
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

    //当前格一价格
    var curGe1Price: String? = null

    //当前格二价格
    var curGe2Price: String? = null
    var closeCount1Default = 3
    var closeCount2Default = 3
    var closeCount1 = 3
    var closeCount2 = 3
    var weightPercent1 = 0
    var weightPercent2 = 0


    //当前前格一重量
    var curG1Weight: String? = "0.00"

    //当前前格二重量
    var curG2Weight: String? = "0.00"

    //当前当前格一总重量
    var curG1TotalWeight: String = "60.00"

    //当前当前格二总重量
    var curG2TotalWeight: String = "60.00"


    //当前sn
    var curSn = ""

    //用户ID
    var curUserId = ""

    //格口一
    var cur1Cabinld = ""

    //格口二
    var cur2Cabinld = ""

    /***************************************** 发送 启动格口开门 查询投口门状态 查询格口重量 ******************************************/

    /**
     * 初始化保存网络数据
     * @param loginModel
     * @param oneInit 是否初始化
     */
    fun saveSocketInitData(loginModel: ConfigBean, oneInit: Boolean) {
        Loge.e("获取socket初始化数据 ${Thread.currentThread().name}")
        flowAgainLogin.value = true//标记登录成功
        cancelJobAgain()  // 停止重试循环
        viewModelScope.launch(Dispatchers.IO) {
            try {
                devWeiChaMapSend[0] = false
                devWeiChaMapSend[1] = false
                Loge.e("获取socket初始化数据 ioScope ${Thread.currentThread().name}")
                val heartbeatIntervalMillis = loginModel.config.heartBeatInterval?.toLongOrNull()
                    ?: 30L
                config?.heartbeatIntervalMillis = TimeUnit.SECONDS.toMillis(heartbeatIntervalMillis)
                Loge.e("获取socket初始化数据 心跳秒：$heartbeatIntervalMillis")
                val config = loginModel.config
                //控制终端维护状态
                when (config.status) {
                    //未运营
                    -1 -> {
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.netStatusText1, BusType.BUS_MAINTAINING_END)
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.netStatusText2, BusType.BUS_MAINTAINING_END)
                        setRefBusStaChannel(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_MAINTAINING_END
                        })
                        if (doorGeXType == CmdCode.GE2) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                doorGeX = CmdCode.GE2
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_MAINTAINING_END
                            })
                        }
                    }
                    //维护
                    0 -> {
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.netStatusText1, BusType.BUS_MAINTAINING)
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.netStatusText2, BusType.BUS_MAINTAINING)
                        setRefBusStaChannel(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_MAINTAINING
                        })
                        if (doorGeXType == CmdCode.GE2) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                doorGeX = CmdCode.GE2
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_MAINTAINING
                            })
                        }
                    }
                    //正常
                    1 -> {
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.netStatusText1, BusType.BUS_MAINTAINING_END)
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.netStatusText2, BusType.BUS_MAINTAINING_END)
                        setRefBusStaChannel(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_MAINTAINING_END
                        })
                        if (doorGeXType == CmdCode.GE2) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                doorGeX = CmdCode.GE2
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_MAINTAINING_END
                            })
                        }
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

                //保存基础配置信息
                loginModel.sn?.let { snCode ->
                    Loge.e("获取socket初始化数据 开始添加配置")
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
                        Loge.e("获取socket初始化数据 添加配置 $row")
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
                        Loge.e("获取socket初始化数据 更新配置 $row")
                    }
                }
                Loge.e("获取socket初始化数据 ${FileMdUtil.checkResFileExists("qrCode.png")}")
                val baseurl = config.uploadPhotoURL?.substringBeforeLast("/api") + "/api"
                Loge.e("获取socket初始化数据 重设置http url $baseurl")
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
                Loge.e("获取socket初始化数据 开始保存格口信息 开始")
                val stateBox = mutableListOf<StateEntity>()
                var setVolume = 10
                loginModel.config.list?.let { lattices ->
                    lattices.withIndex().forEach { (index, lattice) ->
                        Loge.e("获取socket初始化数据 当前格口：${index} /价格：${lattice.price}")
                        when (index) {
                            0 -> {
                                SPreUtil.put(AppUtils.getContext(), SPreUtil.saveIr1, lattice.ir)
                                curGe1Price = lattice.price//初始化登录模式价格
                                cur1Cabinld = lattice.cabinId ?: ""//初始化登录模式格口编码
                                curG1Weight = lattice.weight
                                curG1TotalWeight = lattice.overweight ?: "60.00"
                                weightPercent1 = lattice.weightPercent ?: 0
                                closeCount1Default = lattice.closeCount ?: 5
                                closeCount1 = closeCount1Default

                            }

                            1 -> {
                                SPreUtil.put(AppUtils.getContext(), SPreUtil.saveIr2, lattice.ir)
                                curGe2Price = lattice.price//初始化登录模式价格
                                cur2Cabinld = lattice.cabinId ?: ""//初始化登录模式格口编码
                                curG2Weight = lattice.weight
                                curG2TotalWeight = lattice.overweight ?: "60.00"
                                weightPercent2 = lattice.weightPercent ?: 0
                                closeCount2Default = lattice.closeCount ?: 5
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
                            closeCount = lattice.closeCount ?: 5
                            weightPercent = lattice.weightPercent ?: 0
                            weight = lattice.weight
                            weightMonitor = lattice.weight
                            time = AppUtils.getDateYMDHMS()
                        }
                        setVolume = lattice.volume
                        val queryLattice = lattice.cabinId?.let { cabinId ->
                            DatabaseManager.queryLatticeEntity(AppUtils.getContext(), cabinId)
                        }
                        if (queryLattice == null) {
                            val rowCabin = DatabaseManager.insertLattice(AppUtils.getContext(), saveConfig)
                            Loge.e("获取socket初始化数据 添加格口信息 $rowCabin")
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
                            queryLattice.closeCount = lattice.closeCount ?: 5
                            queryLattice.weightPercent = lattice.weightPercent ?: 0
                            queryLattice.weight = lattice.weight
                            queryLattice.weightDefault = lattice.weight
                            val rowCabin = DatabaseManager.upLatticeEntity(AppUtils.getContext(), queryLattice)
                            Loge.e("获取socket初始化数据 更新格口信息 $rowCabin")

                            //拿出当前心跳格口信息
                            val upperMachines = DatabaseManager.queryStateList(AppUtils.getContext())
                            upperMachines.withIndex().forEach { (index, states) ->
                                when (index) {
                                    0 -> {
                                        containersDB.add(states)
                                        cur1Cabinld = states.cabinId ?: ""//初始化读取db格口编码
                                        curG1Weight = states.weigh.toString()
                                        if (!oneInit) {
                                            Loge.e("流程 toGoCmdOtaBin 进来了 1")
                                            restartAppCloseDoor(CmdCode.GE1)
                                        }
                                    }

                                    1 -> {
                                        containersDB.add(states)
                                        cur2Cabinld = states.cabinId ?: ""//初始化读取db格口编码
                                        curG2Weight = states.weigh.toString()
                                        if (!oneInit) {
                                            Loge.e("流程 toGoCmdOtaBin 进来了 2")
                                            restartAppCloseDoor(CmdCode.GE2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    //配置音量
                    MediaPlayerHelper.setVolume(AppUtils.getContext(), setVolume)
                    //首次初始化插入心跳数据
                    for (state in stateBox) {
                        val rowState = DatabaseManager.insertState(AppUtils.getContext(), state)
                        Loge.e("获取socket初始化数据 添加心跳信息 $rowState")
                    }
                }

                //保存资源配置
                Loge.e("获取socket初始化数据 开始加载资源")
                loginModel.config.resourceList?.let { resources ->
                    for (resource in resources) {
                        val saveResource = ResEntity().apply {
                            filename = resource.filename
                            url = resource.url
                            md5 = resource.md5
                            time = AppUtils.getDateYMDHMS()
                        }
                        //根据文件名称查询
                        val queryResource = DatabaseManager.queryResName(
                            AppUtils.getContext(), resource.filename ?: ""
                        )
                        if (queryResource == null) {
                            val row = DatabaseManager.insertRes(AppUtils.getContext(), saveResource)
                            Loge.e("刷新背景图 添加资源 $row")
                            delay(500)
                            if (resource.url != null && !TextUtils.isEmpty(resource.url) && resource.filename != null && !TextUtils.isEmpty(resource.filename)) {
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
                                            Loge.e("刷新背景图 下载图片 ${resource.filename}")
                                            when (resource.filename) {
                                                "home.png" -> {
                                                    //存在
                                                    if (FileMdUtil.checkResFileExists("home.png")) {
                                                        downResBitmap(oneInit, "res/${resource.filename}", 1, config.status)
                                                    }
                                                }

                                                "maintaining.png" -> {
                                                    //存在
                                                    if (FileMdUtil.checkResFileExists("maintaining.png")) {
                                                        downResBitmap(oneInit, "res/${resource.filename}", 3, config.status)
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
                            Loge.e("刷新背景图 更新图片 ${resource.filename} ")
                            Loge.e("刷新背景图 md5 ${queryResource.md5} | ${resource.md5} ")
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
                                            Loge.e("刷新背景图 更新图片 ${queryResource.filename}")
                                            when (queryResource.filename) {
                                                "home.png" -> {
                                                    if (FileMdUtil.checkResFileExists("home.png")) {
                                                        downResBitmap(oneInit, "res/${resource.filename}", 1, config.status)
                                                    }
                                                }

                                                "maintaining.png" -> {
                                                    if (FileMdUtil.checkResFileExists("maintaining.png")) {
                                                        downResBitmap(oneInit, "res/${resource.filename}", 3, config.status)
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
                                            downResBitmap(oneInit, "res/${resource.filename}", 1, config.status)
                                        }
                                    }

                                    "maintaining.png" -> {
                                        if (FileMdUtil.checkResFileExists("maintaining.png")) {
                                            downResBitmap(oneInit, "res/${resource.filename}", 3, config.status)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }


                //处理服务下发满溢重启动还需要存在
                val overflowState = SPreUtil.get(AppUtils.getContext(), SPreUtil.overflowState, false) as Boolean
                val overflowState1 = SPreUtil.get(AppUtils.getContext(), SPreUtil.overflowState1, false) as Boolean
                val overflowState2 = SPreUtil.get(AppUtils.getContext(), SPreUtil.overflowState2, false) as Boolean
                val overflowStateValue = SPreUtil.get(AppUtils.getContext(), SPreUtil.overflowStateValue, -1)
                if (overflowState && overflowStateValue == 1) {
                    if (overflowState1) {
                        maptDoorFault[FaultType.FAULT_CODE_2110] = true
                    } else {
                        maptDoorFault[FaultType.FAULT_CODE_2110] = false

                    }
                    if (overflowState2) {
                        maptDoorFault[FaultType.FAULT_CODE_2120] = true
                    } else {
                        maptDoorFault[FaultType.FAULT_CODE_2120] = false

                    }
                } else {
                    maptDoorFault[FaultType.FAULT_CODE_2110] = false
                    maptDoorFault[FaultType.FAULT_CODE_2120] = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (!oneInit) {
                    //发送心跳
                    sendHeartbeat()
                    delay(1000)
                    Loge.e("获取socket初始化数据 开始启动页面")
                    flowLoginCmd.emit(true)
                }
            }
        }
    }

    /****
     * @param path 存储路径·
     * @param res 类型 1.首页 2.二维码 3.弹框图 维护 满溢
     * @param status 控制终端维护状态 -1.未运营 0.维护 1.正常
     */
    private fun downResBitmap(oneInit: Boolean, path: String, res: Int, status: Int) {
        val options = RequestOptions().skipMemoryCache(true) // 禁用内存缓存
            .diskCacheStrategy(DiskCacheStrategy.NONE) // 禁用磁盘缓存
        Glide.with(AppUtils.getContext()).asBitmap().load(File("${AppUtils.getContext().filesDir}/${path}")).apply(options).into(object : CustomTarget<Bitmap?>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                if (res == 1) {
                    mHomeBg = resource
                    Loge.e("业务流：刷新背景圖 -> 加載發送")
                    setRefHomeCode(MonitorHomeCode().apply {
                        refreshType = RefBusType.REFRESH_TYPE_5
                        bitmap = resource
                    })
                    if (oneInit) {
                        setRefBusStaChannel(MonitorWeight().apply {
                            refreshType = RefBusType.REFRESH_TYPE_1
                            doorGeX = CmdCode.GE1
                            curG1WeightPrice = curGe1Price
                            curG1WeightValue = curG1Weight
                        })
                        setRefBusStaChannel(MonitorWeight().apply {
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_REFRESH_DATA
                            doorGeX = CmdCode.GE1
                            curG1WeightValue = curG1Weight
                        })
                        if (doorGeXType == CmdCode.GE2) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_REFRESH_DATA
                                doorGeX = CmdCode.GE1
                                curG1WeightValue = curG1Weight
                            })
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_REFRESH_DATA
                                doorGeX = CmdCode.GE2
                                curG2WeightValue = curG2Weight
                            })
                        }
                    }
                } else if (res == 2) {
                    mQrCode = resource
                    setRefHomeCode(MonitorHomeCode().apply {
                        refreshType = RefBusType.REFRESH_TYPE_6
                        bitmap = resource
                    })
                } else if (res == 3) {
                    mMaintaining = resource
                    when (status) {
                        -1 -> {
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_MAINTAINING
                                doorGeX = CmdCode.GE1
                            })
                        }

                        0 -> {
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
        val logoBitmap = BitmapFactory.decodeResource(AppUtils.getContext().resources, R.mipmap.ic_launcher_by)
        val bg = BitmapFactory.decodeResource(AppUtils.getContext().resources, R.color.black)
        AwesomeQRCode.Renderer().contents(qrcode).background(bg).size(1080) // 增加尺寸以提高可扫描性
            .roundedDots(true).dotScale(0.6f) // 增加点的大小
            .colorDark(Color.BLACK) // 深色部分为黑色
            .colorLight(Color.WHITE) // 浅色部分为白色 - 这是关键修复
            .whiteMargin(true).margin(20) // 增加边距
            .logo(logoBitmap).logoMargin(10).logoRadius(10).logoScale(0.15f) // 减小logo尺寸，避免遮挡关键信息
            .renderAsync(object : AwesomeQRCode.Callback {
                override fun onError(renderer: AwesomeQRCode.Renderer, e: Exception) {
                    Loge.e("获取socket初始化数据 创建二维码失败")
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.isQrCode, false)
                    e.printStackTrace()
                }

                override fun onRendered(renderer: AwesomeQRCode.Renderer, bitmap: Bitmap) {
                    mQrCode = bitmap
                    FileMdUtil.saveBitmapToInternalStorage(bitmap, "qrCode.png")
                    Loge.e("获取socket初始化数据 创建二维码成功 保存开始 ${Thread.currentThread().name}")
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
                delay(10000)
                flowIsReadSignal.emit(1)
            }
        }
    }

    private var pollingFaultJob: Job? = null
    fun cancelPollingFaultJob() {
        pollingFaultJob?.cancel()
        pollingFaultJob = null
    }

    /***
     * 定时轮询查询异常
     */
    fun startPollingFault() {
        if (pollingFaultJob?.isActive == true) {
            BoxToolLogUtils.savePrintln("业务流：查询柜体状态 检测故障信息")
            return
        }
        pollingFaultJob = ioScope.launch {
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

                        FaultType.FAULT_CODE_110 -> {
                            if (value) "投递门一关门异常" else null
                        }

                        FaultType.FAULT_CODE_120 -> {
                            if (value) "投递门二关门异常" else null
                        }

                        FaultType.FAULT_CODE_311 -> {
                            if (value) "清运门一开门异常" else null
                        }

                        FaultType.FAULT_CODE_321 -> {
                            if (value) "清运门二开门异常" else null
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

                        FaultType.FAULT_CODE_1111 -> {
                            if (value) {
                                doorT1Overflow = true
                                "格口一满溢"
                            } else {
                                doorT1Overflow = false
                                null
                            }
                        }

                        FaultType.FAULT_CODE_1112 -> {
                            if (value) {
                                doorT2Overflow = true
                                "格口二满溢"
                            } else {
                                doorT2Overflow = false
                                null
                            }
                        }

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
                            val irOverflow = SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                            val isTouOverflow = if (maptDoorFault[FaultType.FAULT_CODE_11] == true) {
                                //红外满溢提示加上红外可投的重量 小于当前重量则可继续投递
                                val curTotalWeight = CalculationUtil.addFloats(curG1TotalWeight, irOverflow.toString())
                                //当前小于红外总重量则不超重
                                val bl1 = CalculationUtil.isLess(
                                    curG1Weight ?: "0.00", curTotalWeight
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
                            val irOverflow = SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                            val isTouOverflow = if (maptDoorFault[FaultType.FAULT_CODE_12] == true) {
                                //红外满溢提示加上红外可投的重量 小于当前重量则可继续投递
                                val curTotalWeight = CalculationUtil.addFloats(curG2TotalWeight, irOverflow.toString())
                                //当前小于红外总重量则不超重
                                val bl2 = CalculationUtil.isLess(
                                    curG2Weight ?: "0.00", curTotalWeight
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
                        setRefBusStaChannel(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_FAULT
                        })
                    } else {
                        setRefBusStaChannel(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_OVERFLOW
                        })
                    }
                } else {
                    if (doorT1Abnormal) {
                        setRefBusStaChannel(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_FAULT
                        })
                    } else {
                        setRefBusStaChannel(MonitorWeight().apply {
                            doorGeX = CmdCode.GE1
                            refreshType = RefBusType.REFRESH_TYPE_2
                            warningContent = BusType.BUS_NORMAL
                        })
                    }
                }
                if (doorGeXType == CmdCode.GE) {
                    if (doorT2Overflow) {
                        if (doorT2Abnormal) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                doorGeX = CmdCode.GE2
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_FAULT
                            })
                        } else {
                            setRefBusStaChannel(MonitorWeight().apply {
                                doorGeX = CmdCode.GE2
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_OVERFLOW
                            })
                        }
                    } else {
                        if (doorT2Abnormal) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                doorGeX = CmdCode.GE2
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_FAULT
                            })
                        } else {
                            setRefBusStaChannel(MonitorWeight().apply {
                                doorGeX = CmdCode.GE2
                                refreshType = RefBusType.REFRESH_TYPE_2
                                warningContent = BusType.BUS_NORMAL
                            })
                        }
                    }
                }
                delay(8000L)
            }
        }
    }

    /*******************************************下位机通信部分*************************************************/
    /***
     * 下载主芯片版本名称
     */
    var chipName = "f1-20260320.bin"

    /***
     * 下载主芯片版本大小
     */
    var chipDowV = 20260320

    /***
     * 当前主芯片版本大小
     */
    var chipCurV = 20260320

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
    private fun uploadLog(sn: String, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val post = mutableMapOf<String, Any>()
            post["sn"] = sn
            post["file"] = file
            httpRepo.uploadLog(post).onSuccess { user ->
                Loge.d("网络请求 日志上传 onSuccess ${Thread.currentThread().name} ${user.toString()}")
                toGoCmdLog()
                insertInfoLog(LogEntity().apply {
                    msg = "$sn,onSuccess"
                    time = AppUtils.getDateYMDHMS()
                })

            }.onFailure { code, message ->
                Loge.d("网络请求 日志上传 onFailure $code $message")
                insertInfoLog(LogEntity().apply {
                    msg = "$sn,onFailure"
                    time = AppUtils.getDateYMDHMS()
                })

            }.onCatch { e ->
                Loge.d("网络请求 日志上传 onCatch ${e.errorMsg}")
                insertInfoLog(LogEntity().apply {
                    msg = "$sn,onCatch"
                    time = AppUtils.getDateYMDHMS()
                })
            }
        }
    }

    /***
     * 上传拍照
     */
    private fun uploadPhoto(sn: String, transId: String, photoType: String, filePath: File, activeType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (activeType == "45") {
                Loge.d("网络请求 拍照上传 延迟")
                toGoTPSucces(transId)
                delay(3000)

            }
            delay(3000) // 强制等待文件索引在 OS 层完成沉降

            val post = mutableMapOf<String, Any>()
            post["sn"] = sn
            post["transId"] = transId
            post["photoType"] = photoType
            post["file"] = filePath
            Loge.d("网络请求 拍照上传 uploadPhoto ${post}")
            httpRepo.uploadPhoto(post).onSuccess { user ->
                Loge.d("网络请求 拍照上传 onSuccess ${Thread.currentThread().name} ${user.toString()}")
                insertInfoLog(LogEntity().apply {
                    cmd = "$activeType$photoType"
                    msg = "$transId,onFileSuccess"
                    time = AppUtils.getDateYMDHMS()
                }, false)


            }.onFailure { code, message ->
                Loge.d("网络请求 拍照上传 onFailure $code $message")
                insertInfoLog(LogEntity().apply {
                    cmd = "$activeType$photoType"
                    msg = "$transId,onFileFailure"
                    time = AppUtils.getDateYMDHMS()
                })

            }.onCatch { e ->
                Loge.d("网络请求 拍照上传 onCatch ${e.errorMsg}")
                insertInfoLog(LogEntity().apply {
                    cmd = "$activeType$photoType"
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
                    BoxToolLogUtils.savePrintln("log,${logInfoEntity.msg}")
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
        require(data.size <= config?.maxFrameSizeBytes!!) { "Frame too large: ${data.size}" }
        enqueueSend(data)
    }

    /***
     * @param text
     * 发送字符串
     */
    suspend fun sendText(text: String) {
        send(text.toByteArray())
    }

    /***
     * 调查send
     * @param data
     *
     */
    private suspend fun enqueueSend(data: ByteArray) {
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
                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,runMainLoop catch1 ${e.message} running $running")
                break
            } catch (e: Exception) {
                // Swallow and backoff
                Loge.e("出厂配置 initSocket SocketClient runMainLoop catch2 ${e.message} running $running")
                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,runMainLoop catch2 ${e.message} running $running")
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
                    BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,readLoop Stream closed")
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
                        BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,readLoop JSON 不完整，继续等待更多数据")
                        // 保持缓冲区中的内容
                    }

                    is JsonResult.Invalid -> {
                        // 不是 JSON 或格式错误
                        Loge.w("出厂配置 initSocket SocketClient readLoop 检测到无效数据，清空缓冲区")
                        BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,readLoop 检测到无效数据，清空缓冲区")
                        jsonBuffer.clear()
                    }
                }

            } catch (e: IOException) {
                e.printStackTrace()
                Loge.e("出厂配置 initSocket SocketClient readLoop catch ${e.message}")
                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,readLoop catch ${e.message}}")
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
//                BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,readLoop catch ${e.message}}" )
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
                BoxToolLogUtils.recordSocket(CmdValue.SEND, JsonBuilder.toByteArrayToStringNotPretty(data))
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
                BoxToolLogUtils.recordSocket(CmdValue.SEND, "socketClient,writeLoopByte catch1 ${e.message}}")
                break
            } catch (e: IOException) {
                Loge.e("出厂配置 initSocket SocketClient writeLoop catch2 ${e.message}")
                BoxToolLogUtils.recordSocket(CmdValue.SEND, "socketClient,writeLoopByte catch2 ${e.message}}")
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
        val hasHeartbeat = config?.heartbeatIntervalMillis!! > 0 /*&& config.heartbeatPayload.isNotEmpty()*/
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
                    val overflowState = SPreUtil[AppUtils.getContext(), SPreUtil.overflowState, false] as Boolean
                    val overflowStateValue = SPreUtil[AppUtils.getContext(), SPreUtil.overflowStateValue, 1] as Int
                    val irOverflow = SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                    // 构建JSON对象
                    val jsonObject = getDeviceWeightChange(CmdValue.CMD_HEART_BEAT, stateList, setSignal, setIr1, setIr2, overflowState, overflowStateValue, irOverflow)
                    Loge.e("执行重量变动 心跳 $weightRunning")
                    if (!weightRunning) {
                        val result1 = WeightChangeStorage(AppUtils.getContext()).get("key_weight1")
                        val result2 = WeightChangeStorage(AppUtils.getContext()).get("key_weight2")
                        Loge.e("执行重量变动 结：$result1 - 重：${devWeiChaMapCun[0]}| 结：$result2 - 重：${devWeiChaMapCun[1]} 任务：${isRunning}")
                        if (result1 == "success" || result2 == "success") {
                            if (result1 == "success") {
                                devWeiChaMapSend[0] = false
                            }
                            if (result2 == "success") {
                                devWeiChaMapSend[1] = false
                            }
                            val weightChange = getDeviceWeightChange(CmdValue.CMD_PERIPHERAL_STATUS, stateList, setSignal, setIr1, setIr2, overflowState, overflowStateValue, irOverflow)
                            Loge.e("执行重量变动 数据 $weightChange ")
                            sendText(weightChange.toString())
                            BoxToolLogUtils.savePrintln("业务流：重量变动了 weight, 1：${devWeiChaMapCun[0]} | 2：${devWeiChaMapCun[1]}")
                        }
                    }
//                    Loge.e("出厂配置 initSocket SocketClient 发送心跳数据：$jsonObject")
                    val byteArray = JsonBuilder.toByteArray(jsonObject)
                    sendQueueByte.trySend(byteArray)
                } catch (e: Exception) {
                    Loge.e("出厂配置 initSocket SocketClient heartbeatAndIdleMonitor catch ${e.message}")
                    BoxToolLogUtils.recordSocket(CmdValue.SEND, "socketClient,heartbeatAndIdleMonitor catch ${e.message}")
                }
            }
            delay(maxOf(1000L, config?.heartbeatIntervalMillis!!))
        }
    }

    private fun getDeviceWeightChange(cmd: String, stateList: List<StateEntity>, setSignal: Int, setIr1: Int, setIr2: Int, overflowState: Boolean, overflowStateValue: Int, irOverflow: Int): JsonObject {
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
                        //1.满溢 0.未满溢
                        val irState = state.irState
                        //上传前
                        val curGWeight = state.weigh
                        //容量[0可投递, 1红外遮挡, 2超重, 3红外遮挡后-投递超重]
                        //当我上传心跳的时候 你就已经给我返回 有可能不会给你发 capacity =3
                        if (overflowState) {//服务器下发漫溢状态 当服务器改为false 走下面的逻辑
                            if (overflowStateValue == 1) { //
                                addProperty("capacity", 2)
                            } else if (overflowStateValue == 0) {
                                addProperty("capacity", 0)
                            }
                        } else if (curGWeight > curGTotal) {
                            addProperty("capacity", 2)
                        } else if (curGWeight > irOverflow && irState == 1) { //当前重量大于红外超重阈值(单位：Kg） 红外满溢是遮挡的
                            addProperty("capacity", 3)
                        } else {
                            addProperty("capacity", state.capacity)
                        }
                        if (setIr1 == 1 && index == 0) {
                            addProperty("irState", state.irState)
                        } else {
                            addProperty("irState", 0)
                        }
                        //处理上报红外是吧
                        if (doorGeXType == CmdCode.GE2) {
                            if (setIr2 == 1 && index == 1) {
                                addProperty("irState", state.irState)
                            } else {
                                addProperty("irState", 0)
                            }
                        }

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
                        BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,closeSocketQuietly ${e.message}}")
                    } finally {
                        socket = null
                        socketMutex.unlock()
                        BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,closeSocketQuietly finally")
                        Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly finally")
                    }
                }
            }
        } catch (e: Exception) {
            Loge.e("出厂配置 initSocket SocketClient closeSocketQuietly catch2 ${e.message}")
            BoxToolLogUtils.recordSocket(CmdValue.RECEIVE, "socketClient,closeSocketQuietly ${e.message}")
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    override fun onCleared() {
        super.onCleared()
        mHomeBg?.recycle()
        mHomeBg = null
        mQrCode?.recycle()
        mQrCode = null
        mMaintaining?.recycle()
        mMaintaining = null
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    enum class UpgradeStep {
        IDLE, INSTALL_DOW, INSTALL_APK, INSTALL_SAME, INSTALL_FUALT, UPGRADE_DOW, UPGRADE_ERROR, UPGRADE_FUALT, QUERY_VERSION, QUERY_VERSION_FUALT, ENTER_STATUS, ENTER_STATUS_FUALT, QUERY_STATUS, QUERY_STATUS_FUALT, SEND_FILE, SEND_FILE_FUALT, SEND_FILE_END, SEND_FILE_END_FUALT, RESTART_APP, RESTART_APP_FUALT, UPGRADE_END,
    }

    // 建议将这些变量放在 ViewModel 中统一管理
    private val _chipStep = MutableStateFlow(UpgradeStep.IDLE)
    val chipStep = _chipStep.asStateFlow()
    fun setFlowUpgradeStep(step: UpgradeStep) {
        _chipStep.value = step
    }

    fun startDowChip(otaModel: OtaBean) {
        BoxToolLogUtils.savePrintln("业务流：升级固件 期待 false $isRunning ")
        if (isRunning) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
//                val ChipVersionValue = SerialPortSdk.firmwareUpgrade78910(11, byteArrayOf(0xAA.toByte(), 0xAB.toByte(), 0xAC.toByte()))
//                if (ChipVersionValue.isFailure) {
//                    println("我异常了")
//                    BoxToolLogUtils.savePrintln("业务流：查询版本失败:${ChipVersionValue.exceptionOrNull()?.message}")
//                    return@launch
//                }
//                val chipVersion = ChipVersionValue.getOrNull()?.chipVersion ?: SPreUtil.gversion
//                SPreUtil.put(AppUtils.getContext(), SPreUtil.gversion, chipVersion)
//                Loge.e("流程 toGoCmdOtaBin 查询版本结束 $chipVersion")

                delay(2000)
//                Loge.e("流程 toGoCmdOtaBin 查询版本结束 延迟结束 $chipVersion")
                val gversion = SPreUtil[AppUtils.getContext(), SPreUtil.gversion, CmdCode.GJ_VERSION] as Int
                val delFileName = "f1-${gversion}.bin"
                FileMdUtil.delFileName(FileMdUtil.matchNewFile("bin"), delFileName)
                val netVersion = otaModel.version ?: ""
                if (!TextUtils.isEmpty(netVersion)) {
                    val netVersion = netVersion.replace(".", "").toIntOrNull() ?: CmdCode.GJ_VERSION
                    Loge.e("流程 toGoCmdOtaBin 添加资源 $gversion  $netVersion")
                    if (netVersion > gversion) {
                        cancelContainersStatusJob()
                        _chipStep.value = UpgradeStep.UPGRADE_DOW
                        delay(5000)
                        Loge.e("流程 toGoCmdOtaBin 进来了 $gversion  $netVersion")
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
                            AppUtils.getContext(), netVersion.toString(), otaModel.sn
                                ?: "", otaModel.cmd ?: ""
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
                                            startUpgradeWorkflow(row)
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
                                            val delFileName = "f1-${netVersion}.bin"
                                            FileMdUtil.delFileName(FileMdUtil.matchNewFile("bin"), delFileName)
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
                                                startUpgradeWorkflow(queryResource.id)
                                                upNetResDb("下载BIN成功更新", queryResource)
                                            } else {
                                                _chipStep.value = UpgradeStep.UPGRADE_FUALT
                                                queryResource.status = ResType.TYPE_4//下载失败
                                                upNetResDb("下载BIN失败更新", queryResource)
                                                val delFileName = "f1-${netVersion}.bin"
                                                FileMdUtil.delFileName(FileMdUtil.matchNewFile("bin"), delFileName)
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
                            val chipVersion = queryResource.version?.replace(".", "")?.toIntOrNull()
                                ?: CmdCode.GJ_VERSION
                            SPreUtil.put(AppUtils.getContext(), SPreUtil.gversion, chipVersion)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {

            }

        }
    }

    fun startDowApk(otaModel: OtaBean) {
        if (isRunning) {
            BoxToolLogUtils.savePrintln("业务流：升级APK 期待 false $isRunning ")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val verName = AppUtils.getVersionName()
            val oldVs = verName.replace(".", "").toIntOrNull() ?: 0
            val newVs = otaModel.version?.replace(".", "")?.toIntOrNull() ?: 0
            val delFileName = "hsg-${verName}.apk"
            FileMdUtil.delFileName(FileMdUtil.matchNewFile("apk"), delFileName)
            if (oldVs < newVs) {
                _chipStep.value = UpgradeStep.INSTALL_DOW
                delay(2000)
                val saveResource = ResEntity().apply {
                    sn = otaModel.sn
                    version = otaModel.version
                    cmd = otaModel.cmd
                    url = otaModel.url
                    md5 = otaModel.md5
                    time = AppUtils.getDateYMDHMS()
                }
                val queryResource = DatabaseManager.queryResCmd(
                    AppUtils.getContext(), otaModel.version ?: "", otaModel.sn ?: "", otaModel.cmd
                        ?: ""
                )
                if (queryResource == null) {
                    val row = DatabaseManager.insertRes(AppUtils.getContext(), saveResource)
                    Loge.e("获取socket初始化数据  Ota 下载APK $row")
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
                                    FileMdUtil.delFileName(FileMdUtil.matchNewFile("apk"), fileName)
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
                        Loge.e("获取socket初始化数据  下载APK失败插入失败 $row")
                        insertInfoLog(LogEntity().apply {
                            cmd = "ota/apk"
                            msg = "下载APK失败插入失败  $row"
                            time = AppUtils.getDateYMDHMS()
                        })
                    }
                } else {
                    Loge.e("获取socket初始化数据  Ota 下载APK 存在")
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
                        Loge.e("获取socket初始化数据  Ota 下载APK 原：${oldVs},新：${newVs}")
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
                                        FileMdUtil.delFileName(FileMdUtil.matchNewFile("apk"), fileName)
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
    }

    var jobInstall: Job? = null
    fun cancelJobInstall() {
        jobInstall?.cancel()
        jobInstall = null
    }

    var installApkUrl: String? = null
    private fun installDowApk(text: String) {
        jobInstall = ioScope.launch {
            val queryResource = DatabaseManager.queryResNewAPk(AppUtils.getContext(), CmdValue.CMD_OTA_APK)
            //版本一致更新安装了
            val verName = AppUtils.getVersionName()
            val oldVs = verName.replace(".", "").toInt()
            val newVs = queryResource.version?.replace(".", "")?.toInt() ?: 0
            if (newVs > oldVs) {
                val fileName = "hsg-${queryResource.version}.apk"
                val result = FileMdUtil.checkApkFileExists(fileName)
                installApkUrl = FileMdUtil.matchNewFileName("apk", fileName)
                upNetResDb("去安装${queryResource.version}", ResEntity().apply {
                    id = queryResource.id
                    status = ResType.TYPE_3
                    sn = queryResource.sn
                    version = queryResource.version
                    cmd = queryResource.cmd
                    url = queryResource.url
                    md5 = queryResource.md5
                    time = AppUtils.getDateYMDHMS()
                })
                Loge.e("获取socket初始化数据  进来更新APK了 文件是否存在：$result - $text")
                _chipStep.value = UpgradeStep.INSTALL_APK
            }
        }
    }

    /***
     * 升级流程
     */
    fun startUpgradeWorkflow(row: Long = -1) {
        val upgradeCount = SPreUtil.put(AppUtils.getContext(), AppUtils.getDateYMD(), 0) as Int
        if (upgradeCount > 5) {
            BoxToolLogUtils.savePrintln("升级流程：今天超过升级次数 $upgradeCount 不再继续升级")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _chipStep.value = UpgradeStep.UPGRADE_DOW
                cancelContainersStatusJob()
                delay(2000)
                Loge.d("升级流程：startUpgradeWorkflow ")
                BoxToolLogUtils.savePrintln("升级流程：开始")
                val chipStep7 = SerialPortCoreSdk.instance.executeChipNew(SerialPortSdk.CMD7, byteArrayOf(0xaa.toByte(), 0xbb.toByte(), 0xcc.toByte()))
                chipStep7.onSuccess { bytes ->
                    // 解析 Payload (逻辑同你之前的代码)
                    val payload = ProtocolCodec.getSafePayload(bytes)
                    if (payload?.size == 3) {
                        BoxToolLogUtils.savePrintln("升级流程：进入升级指令 onSuccess = ${payload?.size}")
                        _chipStep.value = UpgradeStep.ENTER_STATUS
                    }
                }.onFailure { e ->
                    Loge.d("升级流程： chipStep7 = ${e.message} ")
                    val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                    BoxToolLogUtils.savePrintln("升级流程：进入升级指令失败 $sRow ENTER_STATUS_FUALT = ${e.message} ")
                    _chipStep.value = UpgradeStep.ENTER_STATUS_FUALT
                    return@launch
                }
                delay(2000)
                if (chipStep.value == UpgradeStep.ENTER_STATUS) {
                    val file2 = FileMdUtil.matchNewFile2("bin", chipName)
                    val fileType = byteArrayOf(0xf1.toByte())
                    val filSize = file2?.length()?.toInt() ?: 0
                    if (filSize != 0) {
                        //软件大小
                        val sizeByte = HexConverter.intToByteArray(filSize)
                        Loge.d("升级流程：filSize = $filSize | sizeByte = ${ByteUtils.toHexString(sizeByte)}")
                        //软件版本
                        val vByte = HexConverter.intToByteArray(chipDowV)
                        Loge.d("升级流程：chipMasterV = $chipDowV vByte = ${ByteUtils.toHexString(vByte)}")
                        //CRC效验值
                        val crc = CRC32MPEG2Util.computeFile(file2.absolutePath)
                        val crcByte = HexConverter.intToByteArray(crc.toInt())
                        Loge.d("升级流程：crcByte = ${ByteUtils.toHexString(crcByte)}")
                        val sendByte = HexConverter.combineByteArrays(fileType, sizeByte, vByte, crcByte)
                        val sendResult = HexConverter.combineByteArrays(byteArrayOf(0xA1.toByte(), 0xA2.toByte(), 0xA3.toByte()), sendByte)
                        val chipStep8 = SerialPortCoreSdk.instance.executeChipNew(SerialPortSdk.CMD8, sendResult)
                        chipStep8.onSuccess { bytes ->
                            // 解析 Payload (逻辑同你之前的代码)
                            val payload = ProtocolCodec.getSafePayload(bytes)
                            if (payload?.size == 3) {
                                BoxToolLogUtils.savePrintln("升级流程：进入升级状态指令 onSuccess = ${payload?.size}")
                                _chipStep.value = UpgradeStep.QUERY_STATUS
                            }
                        }.onFailure { e ->
                            _chipStep.value = UpgradeStep.QUERY_STATUS_FUALT
                            Loge.d("升级流程：进入升级状态指令 = ${e.message} ")
                            val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                            BoxToolLogUtils.savePrintln("升级流程：进入升级状态指令 $sRow QUERY_STATUS_FUALT = ${e.message} ")
                            return@launch
                        }
                        delay(2000)
                        if (chipStep.value == UpgradeStep.QUERY_STATUS) {
                            val masterFile = FileMdUtil.matchNewFile2("bin", chipName)
                            masterFile?.let { file ->
                                try {
                                    // 1. 一次性读取文件字节，方便分包处理
                                    val allBytes = file.readBytes()
                                    val CHUNK_SIZE = 8 // 下位机要求的每包大小
                                    val totalBlocks = (allBytes.size + CHUNK_SIZE - 1) / CHUNK_SIZE

                                    var currentBlockIndex = 0
                                    val maxRetriesPerBlock = 10 // 每包最大重试次数

                                    Loge.d("升级流程：开始发送，总块数: $totalBlocks")
                                    BoxToolLogUtils.savePrintln("升级流程：进入发送文件 开始发送，总块数: $totalBlocks")

                                    // 2. 升级中的文件发送循环
                                    while (isActive && currentBlockIndex < totalBlocks) {
                                        // 计算当前块的起始和结束位置
                                        val start = currentBlockIndex * CHUNK_SIZE
                                        val end = minOf(start + CHUNK_SIZE, allBytes.size)
                                        val blockToSend = allBytes.copyOfRange(start, end)

                                        var isBlockSuccess = false
                                        var retryCount = 0

                                        // 3. 单包发送与匹配逻辑
                                        while (retryCount < maxRetriesPerBlock) {
                                            Loge.d("升级流程：发送块[$currentBlockIndex], 尝试次[${retryCount + 1}], 数据:${ByteUtils.toHexString(blockToSend)}")

                                            // 调用 executeDirect (内部带有 timeout)
                                            val result = SerialPortCoreSdk.instance.executeChipNew(SerialPortSdk.CMD18, blockToSend)

                                            result.onSuccess { responseBytes ->
                                                // 解析下位机返回的有效负载
                                                val returnedPayload = ProtocolCodec.getSafePayload(responseBytes)

                                                // 核心匹配逻辑：判断下位机返回的是否等于刚才发送的
                                                if (returnedPayload != null && returnedPayload.contentEquals(blockToSend)) {
                                                    Loge.d("升级流程：块[$currentBlockIndex] 匹配成功")
                                                    isBlockSuccess = true
                                                } else {
                                                    Loge.e("升级流程：块[$currentBlockIndex] 数据不匹配! 期待:${ByteUtils.toHexString(blockToSend)}, 收到:${ByteUtils.toHexString(returnedPayload ?: byteArrayOf())}")
                                                    BoxToolLogUtils.savePrintln("升级流程：进入发送文件 块[$currentBlockIndex] 数据不匹配! 期待:${ByteUtils.toHexString(blockToSend)}, 收到:${ByteUtils.toHexString(returnedPayload ?: byteArrayOf())}")
                                                }
                                            }.onFailure { e ->
                                                Loge.e("升级流程：块[$currentBlockIndex] 通信失败: ${e.message}")
                                                BoxToolLogUtils.savePrintln("升级流程：进入发送文件 块[$currentBlockIndex] 通信失败: ${e.message}")
                                            }

                                            if (isBlockSuccess) break // 匹配成功，跳出重试循环

                                            retryCount++
                                            if (retryCount < maxRetriesPerBlock) {
                                                delay(100) // 重试前稍作停顿，给下位机缓冲时间
                                            }
                                        }

                                        // 4. 判断当前块是否彻底失败
                                        if (isBlockSuccess) {
                                            currentBlockIndex++ // 只有匹配成功才发下一个块
                                        } else {
                                            _chipStep.value = UpgradeStep.SEND_FILE_FUALT
                                            Loge.e("升级流程：块[$currentBlockIndex] 达到最大重试次数，升级终止")
                                            val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                                            BoxToolLogUtils.savePrintln("升级流程：块[$currentBlockIndex] 连续失败，退出 $sRow SEND_FILE_FUALT")
                                            return@launch
                                        }
                                    }

                                    // 5. 全部发送完成校验
                                    if (currentBlockIndex == totalBlocks) {
                                        Loge.d("升级流程：所有数据块发送并匹配完成 onSuccess")
                                        BoxToolLogUtils.savePrintln("升级流程：所有数据块发送并匹配完成")
                                        _chipStep.value = UpgradeStep.SEND_FILE
                                    }

                                } catch (e: Exception) {
                                    Loge.e("升级流程：文件处理异常: ${e.message}")
                                    val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                                    BoxToolLogUtils.savePrintln("升级流程：文件处理异常 $sRow SEND_FILE_FUALT")
                                    _chipStep.value = UpgradeStep.SEND_FILE_FUALT
                                }
                            }
                        }
                        if (chipStep.value == UpgradeStep.SEND_FILE) {
                            delay(5000)
                            val chipStep9 = SerialPortCoreSdk.instance.executeChipNew(SerialPortSdk.CMD9, byteArrayOf(0xa4.toByte(), 0xa5.toByte(), 0xa6.toByte()))
                            chipStep9.onSuccess { bytes ->
                                // 解析 Payload (逻辑同你之前的代码)
                                val payload = ProtocolCodec.getSafePayload(bytes)
                                if (payload?.size == 3) {
                                    Loge.d("升级流程：查询升级结果完成")
                                    val successBytes = byteArrayOf(0xA4.toByte(), 0xA5.toByte(), 0xA6.toByte())
                                    val failBytes = byteArrayOf(0xB4.toByte(), 0xB5.toByte(), 0xB6.toByte())
                                    if (payload.contentEquals(successBytes)) {
                                        SPreUtil.put(AppUtils.getContext(), SPreUtil.gversion, chipDowV)
                                        BoxToolLogUtils.savePrintln("升级流程：进入文件校验指令 onSuccess = ${payload}")
                                        Loge.d("升级流程：查询重启指令 - 成功")
                                        _chipStep.value = UpgradeStep.RESTART_APP
                                    } else if (payload.contentEquals(failBytes)) {
                                        Loge.e("升级流程：查询重启指令 - 失败")
                                        // 处理失败逻辑，例如设置错误状态或重试
                                        val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                                        BoxToolLogUtils.savePrintln("升级流程：$sRow 进入文件校验指令 SEND_FILE_END_FUALT = 字节校验失败")
                                        _chipStep.value = UpgradeStep.RESTART_APP_FUALT
                                    } else {
                                        val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                                        BoxToolLogUtils.savePrintln("升级流程：$sRow 进入文件校验指令 SEND_FILE_END_FUALT = 非法字节")
                                        _chipStep.value = UpgradeStep.RESTART_APP_FUALT
                                    }
                                } else {
                                    val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                                    BoxToolLogUtils.savePrintln("升级流程：$sRow 进入文件校验指令 SEND_FILE_END_FUALT = 字节不够")
                                    _chipStep.value = UpgradeStep.RESTART_APP_FUALT

                                }

                            }.onFailure { e ->
                                Loge.d("升级流程：chipStep9 = ${e.message} ")
                                val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                                BoxToolLogUtils.savePrintln("升级流程：$sRow 进入文件校验指令 SEND_FILE_END_FUALT = ${e.message}")
                                _chipStep.value = UpgradeStep.SEND_FILE_END_FUALT
                                return@launch
                            }
                        }

                        if (chipStep.value == UpgradeStep.SEND_FILE_END) {
                            delay(3000)
                            val chipStep10 = SerialPortCoreSdk.instance.executeChipNew(SerialPortSdk.CMD10, byteArrayOf(0xa7.toByte(), 0xa8.toByte(), 0xa9.toByte()))
                            chipStep10.onSuccess { bytes ->
                                // 解析 Payload (逻辑同你之前的代码)
                                val payload = ProtocolCodec.getSafePayload(bytes)
                                if (payload?.size == 3) {
                                    SPreUtil.put(AppUtils.getContext(), SPreUtil.gversion, chipDowV)
                                    BoxToolLogUtils.savePrintln("升级流程：进入重启指令 onSuccess = ${payload}")
                                    _chipStep.value = UpgradeStep.RESTART_APP
                                }
                            }.onFailure { e ->
                                Loge.d("升级流程： chipStep10 = ${e.message} ")
                                val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                                BoxToolLogUtils.savePrintln("升级流程：$sRow 进入重启指令 RESTART_APP_FUALT = ${e.message}")
                                _chipStep.value = UpgradeStep.RESTART_APP_FUALT
                                return@launch
                            }
                        }

                    } else {
                        val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                        BoxToolLogUtils.savePrintln("升级流程：$sRow 进入升级状态指令 QUERY_STATUS = 文件大小有问题")
                        _chipStep.value = UpgradeStep.QUERY_STATUS_FUALT
                    }
                }
            } catch (e: Exception) {
                val sRow = DatabaseManager.deletedResEntity(AppUtils.getContext(), row)
                BoxToolLogUtils.savePrintln("升级流程：异常情况 $sRow ${e.message}")
                _chipStep.value = UpgradeStep.UPGRADE_ERROR
            } finally {
                if(chipStep.value != UpgradeStep.RESTART_APP){
                    val upgradeCount = SPreUtil[AppUtils.getContext(), AppUtils.getDateYMD(), 0] as Int
                    val result = upgradeCount + 1
                    SPreUtil.put(AppUtils.getContext(), AppUtils.getDateYMD(), result)
                }
                _chipStep.value = UpgradeStep.UPGRADE_END
                BoxToolLogUtils.savePrintln("升级流程：流程 finally ${chipStep.value}")
                println("升级流程：我进入 finally ${chipStep.value}")
                delay(1500)
                OSUtils.restartAppFrontDesk(FaceApplication.getInstance().baseActivity)
            }
        }
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // 初始化
    val cameraManagerNew = NewDualUsbCameraManager(AppUtils.getContext())


    // 定义业务状态，方便 UI 订阅显示
    enum class LockerStep {
        IDLE, START, OPENING, WAITING_OPEN_DOOR, WAITING_OPEN_CLEAR, WEIGHT_TRACKING, CLICK_CLOSE, CLOSING, CLOSE, WAITING_CLOSE, FINISHED, CAMERA_END
    }

    // 建议将这些变量放在 ViewModel 中统一管理
    private val _currentStep = MutableStateFlow(LockerStep.IDLE)
    val currentStep = _currentStep.asStateFlow()
    fun setFlowCurrentStep(step: LockerStep) {
        _currentStep.value = step
    }

    enum class LockerUiStep {
        IDLE, DELIVERY_START, DELIVERY_END, CLEAR_START, CLEAR_END, MOBILE_END
    }


    // 使用 SharedFlow，replay = 0 保证它不是粘性的
    private val _currentUiStep = MutableSharedFlow<LockerUiStep>(replay = 0)
    val currentUiStep = _currentUiStep.asSharedFlow()

    fun setCurrentUiStep(i: LockerUiStep) {
        viewModelScope.launch {
            _currentUiStep.emit(i)
        }
    }

    enum class LockerCameraStep {
        IDLE, CAMERA_STSRT, CAMERA_OPEN, CAMERA_CLOSE, CAMERA_DESTRY
    }

    //
//    private val _currentCameraStep = MutableSharedFlow<LockerCameraStep>(replay = 0)
//    val currentCameraStep = _currentCameraStep.asSharedFlow()
//    fun setCurrentCameraStep(i: LockerCameraStep) {
//        viewModelScope.launch {
//            _currentCameraStep.emit(i)
//        }
//    }
// 1. 定义通道
    private val photoActionChannel = Channel<Int>(Channel.UNLIMITED)

    init {
        // 2. 开启一个专用的串行协程，处理拍照队列
        viewModelScope.launch(Dispatchers.IO) {
            for (switchType in photoActionChannel) {
                // 这里是串行的！执行完一次循环才会处理下一个 type
                executePhotoWorkflow(switchType)
//                executePhotoWorkflow2(switchType)
            }
        }
    }

    // 3. 外部调用的接口
    fun enqueuePhotoAction(switchType: Int) {
        photoActionChannel.trySend(switchType)
    }

    // 只负责控制相机的“生”与“死”
    private val _cameraLifecycleEvent = MutableSharedFlow<CameraOp>(replay = 0)
    val cameraLifecycleEvent = _cameraLifecycleEvent.asSharedFlow()

    enum class CameraOp { START, DESTROY }

    // 业务流中调用：
    fun startWorkflow() {
        // 1. 先让 Activity 把相机开起来，画面挂载到 TextureView
        viewModelScope.launch {
            _cameraLifecycleEvent.emit(CameraOp.START)
        }
    }

    fun executePhotoWorkflow(switchType: Int) {
        viewModelScope.launch {
            val ids = cameraManagerNew.getExternalCameraIds()
            if (ids.size < 2) return@launch
            val transId = modelOpenBean?.transId ?: "transId"
            postTransId = transId
            val setTransId = removeRetryPrefix(transId)
            val a = if (switchType == 1) 0 else 3
            val b = if (switchType == 1) 2 else 1
            val e = if (a == 0) 1 else 2
            val f = if (b == 2) 3 else 4
            val nameIn = "${e}i${a}${setTransId}---${AppUtils.getDateYMD()}.jpg"
            val nameOut = "${f}o${b}${setTransId}---${AppUtils.getDateYMD()}.jpg"
//            val dir = File(AppUtils.getContext().cacheDir, "action")
            val dir = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
            if (!dir.exists()) dir.mkdirs()
            val fileIn = File(dir, nameIn)
            val fileOut = File(dir, nameOut)
            val requests = if (switchType == 1) {
                listOf(
                    NewDualUsbCameraManager.PhotoRequest(ids[0], switchType, "内", fileIn), NewDualUsbCameraManager.PhotoRequest(ids[1], switchType, "外", fileOut)
                )
            } else {
                listOf(
                    NewDualUsbCameraManager.PhotoRequest(ids[1], switchType, "外", fileOut), NewDualUsbCameraManager.PhotoRequest(ids[0], switchType, "内", fileIn)
                )
            }


            // 同时开始拍照并等待全部完成
            val results = cameraManagerNew.takePicturesParallel(requests)

            withContext(Dispatchers.IO) {
                // results 里的文件顺序与 requests 一致
                results.forEach { file ->
                    if (file != null) {
                        //结算页只显示图片打开的拍照
                        if (switchType == 1) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_4
                                takePhotoUrl = file.absolutePath
                            })
                        }
                        val photoType = extractThirdChar(file.name).toString()
                        uploadPhoto(curSn, setTransId, photoType, file, switchType.toString())


//                        val dir = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
//                        if (!dir.exists()) dir.mkdirs()
//                        val photoType = extractThirdChar(file.name).toString()
//                        // 2. 复制文件（添加时间戳避免并发重名）
//                        val tempFile = File(dir, "${file.name}")
//
//                        try {
//                            // 执行物理拷贝
//                            file.copyTo(tempFile, overwrite = true)
//
//                            delay(1000)
//                            // 3. 提取参数
////                            val photoType = extractFourthValue(tempFile.name).toString()
//
//                            BoxToolLogUtils.saveCamera("中转检查: 原始大小 ${file.length()} | 中转大小 ${tempFile.length()}")
//
//                            // 4. 从中转文件上传
//                            uploadPhoto(curSn, setTransId, photoType, tempFile, switchType.toString())
//
//                            // 5. 【关键】上传完或延迟后记得清理中转文件，防止撑爆空间
////                            viewModelScope.launch(Dispatchers.IO) {
////                                delay(30000) // 延迟30秒确保 OkHttp 彻底读完
//////                                if (tempFile.exists()) tempFile.delete()
////                            }
//
//                        } catch (e: Exception) {
//                            BoxToolLogUtils.saveCamera("中转拷贝失败: ${e.message}")
//                        }

                        delay(2000) // 双摄间隔 1s，减轻网络带宽压力
                    }
                }
            }
        }
    }

    fun takePhotoRemote(photoModel: PhotoBean) {
        if (isRunning) {
            BoxToolLogUtils.savePrintln("业务流：远程拍照 有正在业务执行中")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                BoxToolLogUtils.savePrintln("业务流：执行远程拍照")
                startWorkflow()
                delay(5000)
                val dir = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
                if (!dir.exists()) dir.mkdirs()
                val transId = photoModel?.transId ?: "transId"
                val setTransId = removeRetryPrefix(transId)
                when (photoModel.photoType) {
                    -1 -> {
                        val ids = cameraManagerNew.getExternalCameraIds()
                        if (ids.size < 2) return@launch
                        val nameIn = "yz4${setTransId}---${AppUtils.getDateYMD()}.jpg"
                        val nameOut = "yz5${setTransId}---${AppUtils.getDateYMD()}.jpg"
                        val fileIn = File(dir, nameIn)
                        val fileOut = File(dir, nameOut)
                        val requests = listOf(
                            NewDualUsbCameraManager.PhotoRequest(ids[0], 4, "内", fileIn), NewDualUsbCameraManager.PhotoRequest(ids[1], 5, "外", fileOut)
                        )
                        // 同时开始拍照并等待全部完成
                        val results = cameraManagerNew.takePicturesParallel(requests)
                        withContext(Dispatchers.IO) {
                            // results 里的文件顺序与 requests 一致
                            results.forEach { file ->
                                if (file != null) {
                                    //结算页只显示图片打开的拍照
                                    val photoType = extractThirdChar(file.name).toString()
                                    uploadPhoto(curSn, setTransId, photoType, file, "45")

                                }
                            }
                        }
                    }

                    4 -> {
                        val ids = cameraManagerNew.getExternalCameraIds()
                        if (ids.size < 2) return@launch
                        val nameIn = "45${setTransId}---${AppUtils.getDateYMD()}.jpg"
                        val fileIn = File(dir, nameIn)
                        val requests = listOf(
                            NewDualUsbCameraManager.PhotoRequest(ids[0], 4, "内", fileIn)
                        )
                        // 同时开始拍照并等待全部完成
                        val results = cameraManagerNew.takePicturesParallel(requests)
                        withContext(Dispatchers.IO) {
                            // results 里的文件顺序与 requests 一致
                            results.forEach { file ->
                                if (file != null) {
                                    val photoType = extractThirdChar(file.name).toString()
                                    uploadPhoto(curSn, setTransId, photoType, file, "45")
                                    delay(2000) // 双摄间隔 1s，减轻网络带宽压力
                                }
                            }
                        }

                    }

                    5 -> {
                        val ids = cameraManagerNew.getExternalCameraIds()
                        if (ids.size < 2) return@launch
                        val nameOut = "45${setTransId}---${AppUtils.getDateYMD()}.jpg"
                        val fileOut = File(dir, nameOut)
                        val requests = listOf(
                            NewDualUsbCameraManager.PhotoRequest(ids[1], 5, "外", fileOut)
                        )
                        // 同时开始拍照并等待全部完成
                        val results = cameraManagerNew.takePicturesParallel(requests)
                        withContext(Dispatchers.IO) {
                            // results 里的文件顺序与 requests 一致
                            results.forEach { file ->
                                if (file != null) {
                                    val photoType = extractThirdChar(file.name).toString()
                                    uploadPhoto(curSn, setTransId, photoType, file, "45")
                                    delay(2000) // 双摄间隔 1s，减轻网络带宽压力
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _cameraLifecycleEvent.emit(CameraOp.DESTROY)//远程拍照销毁
            }
        }
    }


    var postTransId: String = ""

    /***
     * @param switchType 0.关 1.开`
     */
    fun executePhotoWorkflow2(switchType: Int = -1) {
        viewModelScope.async {
            val transId = modelOpenBean?.transId ?: "transId"
            postTransId = transId
            val setTransId = removeRetryPrefix(transId)
            val a = if (switchType == 1) 0 else 3
            val b = if (switchType == 1) 2 else 1
            val e = if (a == 0) 1 else 3
            val f = if (b == 2) 2 else 4
            val nameIn = "$e-i-$switchType-$a-${setTransId}-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}.jpg"
            val nameOut = "$f-o-$switchType-$b-${setTransId}-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}.jpg"
            val dir = File(AppUtils.getContext().cacheDir, "action")
//        val dir = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
            if (!dir.exists()) dir.mkdirs()
            val fileIn = File(dir, nameIn)
            val fileOut = File(dir, nameOut)
            when (switchType) {
                1 -> {
                    val toFile1 = cameraManagerNew.takePictureSuspend("0", switchType, "内", fileIn)
                    if (toFile1 != null && toFile1.exists()) {
                        BoxToolLogUtils.saveCamera("拍照成功 开门 内 $toFile1 ${toFile1?.name} (${toFile1?.length()} bytes)")
                        uploadPhoto(curSn, setTransId, "0", toFile1, switchType.toString())
                    }
                    delay(3000)
                    val toFile2 = cameraManagerNew.takePictureSuspend("1", switchType, "外", fileOut)
                    if (toFile2 != null && toFile2.exists()) {
                        BoxToolLogUtils.saveCamera("拍照成功 开门 外 $toFile2 ${toFile2?.name} (${toFile2?.length()} bytes)")
                        uploadPhoto(curSn, setTransId, "2", toFile2, switchType.toString())
                    }
                }

                0 -> {
                    val toFile1 = cameraManagerNew.takePictureSuspend("1", switchType, "外", fileOut)
                    if (toFile1 != null && toFile1.exists()) {
                        BoxToolLogUtils.saveCamera("拍照成功 关门 外$toFile1 ${toFile1?.name} (${toFile1?.length()} bytes)")
                        uploadPhoto(curSn, setTransId, "3", toFile1, switchType.toString())
                    }
                    delay(3000)
                    val toFile2 = cameraManagerNew.takePictureSuspend("0", switchType, "内", fileIn)
                    if (toFile2 != null && toFile2.exists()) {
                        BoxToolLogUtils.saveCamera("拍照成功 关门 内 $toFile2 ${toFile2?.name} (${toFile2?.length()} bytes)")
                        uploadPhoto(curSn, setTransId, "1", toFile2, switchType.toString())
                    }
                }

                else -> {}
            }
        }
    }

    var checkStatusResult: Deferred<Boolean>? = null

    /**
     * @param model
     * 检测满溢状态信息
     */
    private fun startLockerCheck(model: DoorOpenBean): Boolean {
        checkStatusResult = ioScope.async {
            Loge.e("流程 startLockerCheck model $model")
            //清运状态则跳过检测
            val openTypeBoolean = model.openType == 2
            //非清运指令则检测
            val toGo = if (!openTypeBoolean) {
                //优先以服务器下方满溢限制投递
                val overflowState1 = SPreUtil[AppUtils.getContext(), SPreUtil.overflowState1, false] as Boolean //网络下发
                val overflowState2 = SPreUtil[AppUtils.getContext(), SPreUtil.overflowState2, false] as Boolean //网络下发
                val overflowResult = when (doorGeX) {
                    CmdCode.GE1 -> {
                        //返回false不执行业务
                        !(maptDoorFault[FaultType.FAULT_CODE_2110] == true || overflowState1)
                    }

                    CmdCode.GE2 -> {
                        //返回false不执行业务
                        !(maptDoorFault[FaultType.FAULT_CODE_2120] == true || overflowState2)
                    }

                    else -> {
                        true
                    }
                }
                //红外遮挡满溢的重量
                val irOverflowC = SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                val lrOverflowResult = when (doorGeX) {
                    CmdCode.GE1 -> {
                        if (maptDoorFault[FaultType.FAULT_CODE_11] == true) {
                            //红外满溢+可超重量
                            //格口1 当前重量小于服务器红外满溢则true
                            val bl1Overflow = CalculationUtil.isLess(
                                curG1Weight ?: "0.00", irOverflowC.toString()
                            )
                            if (bl1Overflow) {
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
                            val bl2Overflow = CalculationUtil.isLess(
                                curG2Weight ?: "0.00", irOverflowC.toString()
                            )
                            if (bl2Overflow) {
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
                        !(maptDoorFault[FaultType.FAULT_CODE_111] == true || maptDoorFault[FaultType.FAULT_CODE_110] == true || maptDoorFault[FaultType.FAULT_CODE_91] == true || maptDoorFault[FaultType.FAULT_CODE_5101] == true || maptDoorFault[FaultType.FAULT_CODE_5102] == true)
                    }

                    CmdCode.GE2 -> {
                        !(maptDoorFault[FaultType.FAULT_CODE_121] == true || maptDoorFault[FaultType.FAULT_CODE_120] == true || maptDoorFault[FaultType.FAULT_CODE_92] == true || maptDoorFault[FaultType.FAULT_CODE_5201] == true || maptDoorFault[FaultType.FAULT_CODE_5202] == true)
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

    private val _isRunning = AtomicBoolean(false)
    val isRunning: Boolean get() = _isRunning.get()

    var weightRunning = false
    private val defaultWeight = "0.00"
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
        if (!_isRunning.compareAndSet(false, true)) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            var toWeightAfterClosing = "0"  // 彻底关门后重量
            try {
                doorGeX = doorGex
                startLockerCheck(model)
                val transId = model.transId ?: ""
                val execution = checkStatusResult?.await()
                BoxToolLogUtils.savePrintln("业务流：业务正在执行中.... 格口：$doorGex 当前重量：$setWeightBeforeOpen 满溢：$execution | 运行：${isRunning} |${transId}")
                if (execution == false) {
                    return@launch
                }
                weightRunning = true
                startWorkflow()
                setCurrentUiStep(LockerUiStep.MOBILE_END)
                // 1. 核心状态初始化
                cancelPollingFaultJob()
                cancelContainersStatusJob()
                //指令下发防夹手回弹次数
                var closeCount = executeCount
                modelOpenBean = model
                remoteOpenType = model.openType
                setPhotoTransId = transId
                _currentStep.value = LockerStep.START
                delay(1000)
                enqueuePhotoAction(1)//投口关闭前的拍照
                var weightBeforeOpen = setWeightBeforeOpen   // 开门前重量
                var weightAfterOpening = "0"  // 确认开门瞬间重量
                var weightDuringOpening = "0" // 过程中最后一次重量
                var weightAfterClosing = "0"  // 彻底关门后重量

                // --- 第一阶段：下发开门 ---
                _currentStep.value = LockerStep.OPENING
                setCurrentUiStep(LockerUiStep.DELIVERY_START)
                // 记录“准备开门前”的初始重量（作为参考）
                val weightBeforeOpenCmd = SerialPortSdk.queryWeight(doorGex)
                if (weightBeforeOpenCmd.isFailure) throw Exception("业务流 开门前重量 获取重量指令失败: ${weightBeforeOpenCmd.exceptionOrNull()?.message}")
                weightBeforeOpen = weightBeforeOpenCmd.getOrNull()?.weight.toString()
                if (doorGex == CmdCode.GE1) {
                    curG1Weight = weightBeforeOpen
                } else {
                    curG2Weight = weightBeforeOpen
                }
                BoxToolLogUtils.savePrintln("业务流：正在执行开门动作【${SendTurnText.fromStatus(openType)}】 开门前重量:$weightBeforeOpen")
                dbBeforeWeight(weightBeforeOpen, model)
                val turnDoor = SerialPortSdk.turnDoor(openType)
                if (turnDoor.isFailure) throw Exception("业务流：开门指令接收失败: ${turnDoor.exceptionOrNull()?.message}")
                DatabaseManager.upTransOpenStatus(AppUtils.getContext(), EntityType.WEIGHT_TYPE_10, transId)
                // --- 第二阶段：轮询等待门开启 ---
                _currentStep.value = LockerStep.WAITING_OPEN_DOOR

                BoxToolLogUtils.savePrintln("业务流：等待门物理状态变为【开启】")
                // 使用 withTimeout 防止传感器故障导致协程永久挂起
                withTimeout(30000) { // 30秒超时
                    while (isActive) {
                        val doorStatusValue = SerialPortSdk.turnDoorStatus(doorGex)
                        if (doorStatusValue.isFailure) throw Exception("业务流：门开动作获取投门状态失败: ${doorStatusValue.exceptionOrNull()?.message}")
                        val doorStatus = doorStatusValue.getOrNull()?.status
                        BoxToolLogUtils.savePrintln("业务流：门开动作等待门物理状态变为 1 【${doorStatus}】")
                        if (doorStatus == CmdCode.GE_OPEN) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_3
                            })
//                            noticeExection(CmdCode.GE_OPEN, doorGex, BusType.BUS_NORMAL, false)
                            val weightAfterOpeningCmd = SerialPortSdk.queryWeight(doorGex)
                            if (weightAfterOpeningCmd.isFailure) throw Exception("业务流：确认开门瞬间重量 获取重量指令失败: ${weightAfterOpeningCmd.exceptionOrNull()?.message}")
                            weightAfterOpening = weightAfterOpeningCmd.getOrNull()?.weight.toString()
                            BoxToolLogUtils.savePrintln("业务流：打开后的重量：$weightAfterOpening")
                            //更新业务门开完成
                            DatabaseManager.upTransOpenStatus(AppUtils.getContext(), CmdCode.GE_OPEN, transId)
                            dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, defaultWeight, defaultWeight, openModel = model, flowEnd = false)
                            break// 门已确认开启
                        }
//                        if (doorStatus == CmdCode.GE_OPEN_CLOSE_ING) { }
                        if (doorStatus == CmdCode.GE_OPEN_CLOSE_FAULT) {
//                            noticeExection(CmdCode.GE_OPEN, doorGex, BusType.BUS_FAULT, true)
                            BoxToolLogUtils.savePrintln("业务流：门开前格口-门开故障 打开后的重量：$weightBeforeOpen")
                            throw Exception("业务流：门开前格口-门开故障: 3 $doorStatus")
                        }
                        delay(1000)
                    }
                }
                // --- 第三阶段：监测重量变化 ---
                _currentStep.value = LockerStep.WEIGHT_TRACKING
                BoxToolLogUtils.savePrintln("业务流：门已开启，开始监测实时重量。初始重量: $weightBeforeOpen ,门开重量：$weightAfterOpening")
                withTimeout(120000) { // 2分钟/超时
                    while (isActive) {
                        val weightDuringOpeningCmd = SerialPortSdk.queryWeight(doorGex)
                        if (weightDuringOpeningCmd.isFailure) throw Exception("业务流： 过程中最后一次重量 获取重量指令失败: ${weightDuringOpeningCmd.exceptionOrNull()?.message}")
                        weightDuringOpening = weightDuringOpeningCmd.getOrNull()?.weight.toString()
                        if (doorGex == CmdCode.GE1) {
                            curG1Weight = weightDuringOpening
                        } else {
                            curG2Weight = weightDuringOpening
                        }
                        BoxToolLogUtils.savePrintln("业务流：打开后持续的重量：$weightDuringOpening")
                        dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, weightDuringOpening, defaultWeight, openModel = model, flowEnd = false)
                        if (currentStep.value == LockerStep.CLOSE) {
                            BoxToolLogUtils.savePrintln("业务流：门已经关闭 跳出查询重量 ")
                            enqueuePhotoAction(0)//投口关闭后的拍照
                            break
                        }
                        // --- 第四阶段：执行关门动作 ---
                        if (currentStep.value == LockerStep.CLICK_CLOSE) {
                            _currentStep.value = LockerStep.CLOSING
                            BoxToolLogUtils.savePrintln("业务流：监测到关门信号，下发关门指令【${SendTurnText.fromStatus(closeType)}】")
                            val closeRes = SerialPortSdk.turnDoor(closeType)
                            if (closeRes.isFailure) throw Exception("业务流：关门指令发送失败")
                        }
                        // --- 第五阶段：轮询等待门关闭 ---
                        if (currentStep.value == LockerStep.CLOSING) {
                            withTimeout(30000) { // 30秒超时
                                val doorStatusValue = SerialPortSdk.turnDoorStatus(doorGex)
                                if (doorStatusValue.isFailure) throw Exception("业务流：门关动作获取投门状态失败: ${doorStatusValue.exceptionOrNull()?.message}")
                                val doorStatus = doorStatusValue.getOrNull()?.status
                                BoxToolLogUtils.savePrintln("业务流：门关动作等待门物理状态变为 0【${doorStatus}】")
                                if (doorStatus == CmdCode.GE_CLOSE) {
//                                noticeExection(CmdCode.GE_OPEN, doorGex, BusType.BUS_NORMAL, false)
                                    _currentStep.value = LockerStep.CLOSE
                                    BoxToolLogUtils.savePrintln("业务流：收到门关闭")
                                    DatabaseManager.upTransCloseStatus(AppUtils.getContext(), CmdCode.GE_CLOSE, transId)
                                    return@withTimeout
                                }
                                if (doorStatus == CmdCode.GE_OPEN) {
                                    closeCount -= 1
                                    BoxToolLogUtils.savePrintln("业务流：防夹手状态 当前循环次数 $closeCount")
                                    if (closeCount <= 0) {
                                        SerialPortSdk.turnDoor(forcedCloseType)
                                    } else {
                                        SerialPortSdk.turnDoor(closeType)
                                    }
                                }
                                if (doorStatus == CmdCode.GE_OPEN_CLOSE_FAULT) {
//                                noticeExection(CmdCode.GE_OPEN, doorGex, BusType.BUS_FAULT, true)
                                    BoxToolLogUtils.savePrintln("业务流：门开后格口-门关故障 打开后的重量：$weightDuringOpening")
                                    throw Exception("业务流：门开后格口-门关故障: 3 ${doorStatus}")
                                }
                            }
                            // 这里可以增加一个逻辑：如果检测到重量稳定增加超过 X 秒，也可以自动触发下一步
                            delay(1000)
                        }
                        //每秒查询一次重量
                        delay(1000)
                    }
                }

                _currentStep.value = LockerStep.WAITING_CLOSE
                // --- 第六阶段：结算最终重量 ---

                delay(3000) // 等待机械震动停止，获取更准的重量
                val weightAfterClosingCmd = SerialPortSdk.queryWeight(doorGex)
                if (weightAfterClosingCmd.isFailure) throw Exception("业务流 彻底关门后重量 获取重量指令失败: ${weightAfterClosingCmd.exceptionOrNull()?.message}")
                weightAfterClosing = weightAfterClosingCmd.getOrNull()?.weight.toString()
                toWeightAfterClosing = weightAfterClosing
                dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, weightDuringOpening, weightAfterClosing, openModel = model, flowEnd = true)
                BoxToolLogUtils.savePrintln("业务流：业务流完毕！ 开门前：$weightBeforeOpen, 开门后：$weightAfterOpening, 过程最高/最后：$weightDuringOpening, 关门后：$weightAfterClosing 启动业务数据上报 curWeight = $weightDuringOpening changeWeight = " + "${CalculationUtil.subtractFloats(weightAfterClosing, weightBeforeOpen)} " + "refWeight = " + "${CalculationUtil.subtractFloats(weightDuringOpening, weightAfterOpening)} " + "beforeUpWeight = $weightBeforeOpen " + "afterUpWeight = $weightAfterOpening " + "beforeDownWeight = $weightDuringOpening " + "afterDownWeight = $weightAfterClosing ")
                _currentStep.value = LockerStep.FINISHED
            } catch (e: TimeoutCancellationException) {
                startLocketErrorCloseUI(doorGex, model.openType, "${e.message}", true)
                BoxToolLogUtils.savePrintln("业务流：操作超时，请检查柜门是否卡住")
            } catch (e: Exception) {
                startLocketErrorCloseUI(doorGex, model.openType, "${e.message}", false)
                BoxToolLogUtils.savePrintln("业务流：异常中断: ${e.message}")
            } finally {
                _cameraLifecycleEvent.emit(CameraOp.DESTROY)
//                endCameraUploadPhoto()
                //保持门要关闭
                restartAppCloseDoor(doorGex)
                setCurrentUiStep(LockerUiStep.DELIVERY_END)
                BoxToolLogUtils.savePrintln("业务流：完毕 finally")
                modelOpenBean = null
                doorGeX = CmdCode.GE
                _isRunning.set(false)
                weightRunning = false
                _currentStep.value = LockerStep.IDLE
                if (doorGex == CmdCode.GE1) {
                    curG1Weight = toWeightAfterClosing
                    startLockerEndWeight(CmdCode.GE1, curG1Weight ?: "0.00")
                } else {
                    curG2Weight = toWeightAfterClosing
                    startLockerEndWeight(CmdCode.GE2, curG2Weight ?: "0.00")
                }

                startContainersStatus() // 恢复全局状态轮询
                startPollingFault()// 恢复全局异常检测
                deteServiceClose()//检测服务器是否完整下发关闭指令
            }
        }
    }


    /***
     * @param model 服务器下发开门
     * @param setWeightBeforeOpen 开门前重量
     */
    fun startLockerClearWorkflow(model: DoorOpenBean, setWeightBeforeOpen: String, doorGex: Int, openType: Int, queryType: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            var toWeightAfterClosing = "0"  // 彻底关门后重量
            try {
                val transId = model.transId ?: ""
                BoxToolLogUtils.savePrintln("业务流：业务正在执行中.... 清运：$doorGex 当前重量：$setWeightBeforeOpen 运行：${isRunning} |${transId}")
                if (_isRunning.getAndSet(true)) {
                    return@launch
                }
                // 1. 核心状态初始化
                cancelPollingFaultJob()
                cancelContainersStatusJob()
                modelOpenBean = model
                remoteOpenType = model.openType
                _currentStep.value = LockerStep.START
                startWorkflow()
                delay(1000)
                enqueuePhotoAction(1)//清运关闭前的拍照
                doorGeX = doorGex
                var weightBeforeOpen = setWeightBeforeOpen   // 开门前重量
                var weightAfterOpening = "0"  // 确认开门瞬间重量
                var weightDuringOpening = "0" // 过程中最后一次重量
                var weightAfterClosing = "0"  // 彻底关门后重量
                // --- 第一阶段：下发开门 ---
                _currentStep.value = LockerStep.OPENING
                setCurrentUiStep(LockerUiStep.CLEAR_START)
                // 记录“准备开门前”的初始重量（作为参考）
                val weightBeforeOpenCmd = SerialPortSdk.queryWeight(doorGex)
                if (weightBeforeOpenCmd.isFailure) throw Exception("业务流 开门前重量 获取重量指令失败: ${weightBeforeOpenCmd.exceptionOrNull()?.message}")
                weightBeforeOpen = weightBeforeOpenCmd.getOrNull()?.weight.toString()
                if (doorGex == CmdCode.GE1) {
                    curG1Weight = weightBeforeOpen
                } else {
                    curG2Weight = weightBeforeOpen
                }
                BoxToolLogUtils.savePrintln("业务流：正在执行开门动作【${SendTurnText.fromStatus(openType)}】 开门前重量:$weightBeforeOpen")
                dbBeforeWeight(weightBeforeOpen, model)
                val openClear = SerialPortSdk.openQueryClear(openType)
                if (openClear.isFailure) throw Exception("开门指令发送失败: ${openClear.exceptionOrNull()?.message}")
                DatabaseManager.upTransOpenStatus(AppUtils.getContext(), EntityType.WEIGHT_TYPE_10, transId)
                // --- 第二阶段：轮询等待门开启 ---
                BoxToolLogUtils.savePrintln("业务流：等待门物理状态变为【开启】")
                // 使用 withTimeout 防止传感器故障导致协程永久挂起
                // 3分钟转换为毫秒
                val timeoutOpenMillis = 3 * 60 * 1000
                withTimeout(300000) { // 30秒超时
                    while (isActive) {
                        //超时3分钟代表门开启失败
                        val startOpenTime = System.currentTimeMillis()
                        val doorClearStatus = SerialPortSdk.openQueryClear(queryType).getOrNull()
                        val clearType = doorClearStatus?.clearType ?: 0
                        val doorStatus = doorClearStatus?.status ?: 0
                        BoxToolLogUtils.savePrintln("业务流：门未开等待门物理状态变为  0 1 【${clearType}】|【${doorStatus}】")
                        if (clearType == CmdCode.GE_CLOSE && doorStatus == CmdCode.GE_OPEN) {
                            _currentStep.value = LockerStep.WAITING_OPEN_CLEAR

                            val weightAfterOpeningCmd = SerialPortSdk.queryWeight(doorGex)
                            if (weightAfterOpeningCmd.isFailure) throw Exception("业务流 确认开门瞬间重量 获取重量指令失败: ${weightAfterOpeningCmd.exceptionOrNull()?.message}")
                            weightAfterOpening = weightAfterOpeningCmd.getOrNull()?.weight.toString()
                            BoxToolLogUtils.savePrintln("业务流：打开后的重量：$weightAfterOpening")
                            //更新清运出打开成功
                            DatabaseManager.upTransOpenStatus(AppUtils.getContext(), EntityType.WEIGHT_TYPE_1, transId)
                            dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, defaultWeight, defaultWeight, openModel = model, flowEnd = false)
                            break// 门已确认开启
                        }
                        // 检查是否超过3分钟
                        if (System.currentTimeMillis() - startOpenTime > timeoutOpenMillis) {
                            BoxToolLogUtils.savePrintln("业务流：门开前清运-门开故障 3分钟内未收到开门状态 强制退出循环 门故障 打开前的重量：$weightBeforeOpen")
                            throw Exception("业务流：门开前清运-门开故障 3分钟内未收到开门状态 强制退出循环 门故障 打开前的重量：$weightBeforeOpen")
                        }
                        delay(3000)
                    }
                }
                delay(1000)
                // --- 第三阶段：监测重量变化 ---
                _currentStep.value = LockerStep.WEIGHT_TRACKING
                BoxToolLogUtils.savePrintln("业务流：门已开启，开始监测实时重量。初始重量: $weightBeforeOpen ,门开重量：$weightAfterOpening")
                // 20分钟转换为毫秒
                val timeoutCloseMillis = 20 * 60 * 1000
                while (isActive) {
                    val weightDuringOpeningCmd = SerialPortSdk.queryWeight(doorGex)
                    if (weightDuringOpeningCmd.isFailure) throw Exception("业务流 过程中最后一次重量 获取重量指令失败: ${weightDuringOpeningCmd.exceptionOrNull()?.message}")
                    weightDuringOpening = weightDuringOpeningCmd.getOrNull()?.weight.toString()
                    if (doorGex == CmdCode.GE1) {
                        curG1Weight = weightDuringOpening
                    } else {
                        curG2Weight = weightDuringOpening
                    }
                    BoxToolLogUtils.savePrintln("业务流：打开后持续的重量：$weightDuringOpening")
                    dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, weightDuringOpening, defaultWeight, openModel = model, flowEnd = false)
                    //超时20分钟代表门关闭失败
                    val startCloseTime = System.currentTimeMillis()
                    // --- 第五阶段：轮询等待门关闭 ---
                    withTimeout(300000) { // 30秒超时
                        val doorClearStatus = SerialPortSdk.openQueryClear(queryType).getOrNull()
                        val clearType = doorClearStatus?.clearType ?: 0
                        val doorStatus = doorClearStatus?.status ?: 0
                        BoxToolLogUtils.savePrintln("业务流：门开后等待门物理状态变为 0 0 【${clearType}】|【${doorStatus}】")
                        if (clearType == CmdCode.GE_CLOSE && doorStatus == CmdCode.GE_CLOSE) {
                            _currentStep.value = LockerStep.CLOSE
                            enqueuePhotoAction(0)//清运关闭后的拍照=
                            DatabaseManager.upTransCloseStatus(AppUtils.getContext(), CmdCode.GE_CLOSE, transId)
                            return@withTimeout
                        }
                    }
                    // 检查是否超过20分钟
                    if (System.currentTimeMillis() - startCloseTime > timeoutCloseMillis) {
                        BoxToolLogUtils.savePrintln("业务流：门开后清运-门关故障 20分钟内未收到关闭状态 强制退出循环 门故障 打开后的重量：$weightAfterOpening")
                        throw Exception("业务流：门开后清运-门关故障 20分钟内未收到关闭状态 强制退出循环 门故障 打开后的重量：$weightAfterOpening")
                    }
                    //门关跳出查询门和重量
                    if (currentStep.value == LockerStep.CLOSE) {
                        break
                    }
                    // 这里可以增加一个逻辑：如果检测到重量稳定增加超过 X 秒，也可以自动触发下一步
                    delay(3000)
                }
                _currentStep.value = LockerStep.WAITING_CLOSE
                // --- 第六阶段：结算最终重量 ---
                _currentStep.value = LockerStep.FINISHED
                delay(3000) // 等待机械震动停止，获取更准的重量
                val weightAfterClosingCmd = SerialPortSdk.queryWeight(doorGex)
                if (weightAfterClosingCmd.isFailure) throw Exception("业务流 彻底关门后重量 获取重量指令失败: ${weightAfterClosingCmd.exceptionOrNull()?.message}")
                toWeightAfterClosing = weightAfterClosing
                dbBeforeWeightRefresh(weightBeforeOpen, weightAfterOpening, weightDuringOpening, weightAfterClosing, openModel = model, flowEnd = true)
                BoxToolLogUtils.savePrintln("业务流：业务流完毕！ 开门前：$weightBeforeOpen, 开门后：$weightAfterOpening, 过程最高/最后：$weightDuringOpening, 关门后：$weightAfterClosing 启动业务数据上报 curWeight = $weightDuringOpening changeWeight = " + "${CalculationUtil.subtractFloats(weightAfterClosing, weightBeforeOpen)} " + "refWeight = " + "${CalculationUtil.subtractFloats(weightDuringOpening, weightAfterOpening)} " + "beforeUpWeight = $weightBeforeOpen " + "afterUpWeight = $weightAfterOpening " + "beforeDownWeight = $weightDuringOpening " + "afterDownWeight = $weightAfterClosing ")
//                _currentStep.value = LockerStep.CAMERA_END

            } catch (e: TimeoutCancellationException) {
                BoxToolLogUtils.savePrintln("业务流：操作超时，请检查柜门是否卡住")
                startLocketErrorCloseUI(doorGex, model.openType, "${e.message}", true)
            } catch (e: Exception) {
                BoxToolLogUtils.savePrintln("业务流：异常中断: ${e.message}")
                startLocketErrorCloseUI(doorGex, model.openType, "${e.message}", false)
            } finally {
                _cameraLifecycleEvent.emit(CameraOp.DESTROY)
                setCurrentUiStep(LockerUiStep.CLEAR_END)
                BoxToolLogUtils.savePrintln("业务流：完毕 finally")
                modelOpenBean = null
                doorGeX = CmdCode.GE
                _isRunning.set(false)
                doorGeX = CmdCode.GE
                _currentStep.value = LockerStep.IDLE
                if (doorGex == CmdCode.GE1) {
                    curG1Weight = toWeightAfterClosing
                    startLockerEndWeight(CmdCode.GE1, curG1Weight ?: "0.00")
                } else {
                    curG2Weight = toWeightAfterClosing
                    startLockerEndWeight(CmdCode.GE2, curG2Weight ?: "0.00")
                }
                startContainersStatus() // 恢复全局状态轮询
                startPollingFault()// 恢复全局异常检测
//                deteServiceClose()//检测服务器是否完整下发关闭指令
            }
        }
    }

    /***
     * 异常情况关闭
     * @param openType
     */
    private fun startLocketErrorCloseUI(doorGex: Int, openType: Int, message: String, timeout: Boolean) {
        //关闭流程界面
        when (openType) {
            1 -> {

                if (message.contains("业务流：门开前格口-门开故障")) {
                    when (doorGex) {
                        CmdCode.GE1 -> {
                            maptDoorFault[FaultType.FAULT_CODE_111] = true
                        }

                        CmdCode.GE2 -> {
                            maptDoorFault[FaultType.FAULT_CODE_121] = true
                        }
                    }
                }
                if (message.contains("业务流：门开后格口-门关故障")) {
                    setCurrentUiStep(LockerUiStep.DELIVERY_END)
                    when (doorGex) {
                        CmdCode.GE1 -> {
                            maptDoorFault[FaultType.FAULT_CODE_110] = true
                        }

                        CmdCode.GE2 -> {
                            maptDoorFault[FaultType.FAULT_CODE_120] = true
                        }
                    }
                    viewModelScope.launch {
                        //保持门要关闭
                        restartAppCloseDoor(doorGex)
                    }
                }
            }

            2 -> {

                if (message.contains("业务流：门开前清运-门开故障")) {
                    when (doorGex) {
                        CmdCode.GE1 -> {
                            maptDoorFault[FaultType.FAULT_CODE_311] = true
                        }

                        CmdCode.GE2 -> {
                            maptDoorFault[FaultType.FAULT_CODE_321] = true
                        }
                    }
                }
                if (message.contains("业务流：门开后清运-门关故障")) {
                    setCurrentUiStep(LockerUiStep.CLEAR_END)
                    when (doorGex) {
                        CmdCode.GE1 -> {
                            maptDoorFault[FaultType.FAULT_CODE_410] = true
                        }

                        CmdCode.GE2 -> {
                            maptDoorFault[FaultType.FAULT_CODE_420] = true
                        }
                    }
                }
            }
        }

        //关闭摄像头释放资源
//        cameraManagerNew.destroy()
    }

    /***
     * 投递完毕刷新首页
     */
    suspend fun startLockerEndWeight(doorGex: Int, toWeightAfterClosing: String) =
        withContext(Dispatchers.IO) {
            Loge.e("进来刷新 startLockerEndWeight $doorGex $toWeightAfterClosing")
            if (doorGex == CmdCode.GE1) {
                setRefBusStaChannel(MonitorWeight().apply {
                    refreshType = RefBusType.REFRESH_TYPE_1
                    doorGeX = CmdCode.GE1
                    curG1WeightPrice = curGe1Price
                    curG1WeightValue = toWeightAfterClosing
                })
            }
            if (doorGex == CmdCode.GE2) {
                setRefBusStaChannel(MonitorWeight().apply {
                    refreshType = RefBusType.REFRESH_TYPE_1
                    doorGeX = CmdCode.GE2
                    curG1WeightPrice = curGe2Price
                    curG1WeightValue = toWeightAfterClosing
                })
            }
        }

    /**通知异常
     * @param doorStartType 接收的异常类型
     * @param doorGex 当前推杆
     * @param setWarningContent 警告信息
     * */
    suspend fun noticeExection(doorStartType: Int, doorGex: Int, setWarningContent: String, isError: Boolean) =
        withContext(Dispatchers.IO) {
            setRefBusStaChannel(MonitorWeight().apply {
                doorGeX = doorGex
                refreshType = RefBusType.REFRESH_TYPE_2
                warningContent = setWarningContent
            })
            when (doorGex) {
                1 -> {
                    when (doorStartType) {
                        CmdCode.GE_OPEN -> {
                            maptDoorFault[FaultType.FAULT_CODE_111] = isError
                        }

                        CmdCode.GE_CLOSE -> {
                            maptDoorFault[FaultType.FAULT_CODE_110] = isError
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
            BoxToolLogUtils.savePrintln("业务流：启动检测门状态 轮询已在运行")
            return
        }
        deteServiceCloseJob = ioScope.launch {
            while (isActive) {
                delay(8000)
                if (!isRunning) {
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

                            timestamp = System.currentTimeMillis().toString()
                        }
                        val json = JsonBuilder.convertToJsonString(doorClose)
                        sendText(json)
                        BoxToolLogUtils.savePrintln("业务流：定时检测服务器未下发指令 -> 再次上报：${doorClose}")
                    }
                } else {
                    BoxToolLogUtils.savePrintln("业务流：定时检测服务器未下发指令 -> 有业务在处理中")
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
        val queryTrans = transId?.let { DatabaseManager.queryTransEntity(AppUtils.getContext(), it) }
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
            BoxToolLogUtils.savePrintln("接收到服务器 关闭 $transId 门打开:${queryTrans.openStatus} | 门关闭：${queryTrans.closeStatus} 业务：${weight?.status ?: -1}")
        }
    }

    //插入重量
    private suspend fun dbBeforeWeight(weightBeforeOpen: String, model: DoorOpenBean) =
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
            val rowWeight = DatabaseManager.insertWeight(AppUtils.getContext(), WeightEntity().apply {
                cmd = CmdValue.CMD_OPEN_DOOR
                openType = model.openType
                transId = model.transId
                curWeight = weightBeforeOpen
                beforeUpWeight = weightBeforeOpen
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
                timestamp = System.currentTimeMillis().toString()
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

    private fun removeRetryPrefix(input: String): String {
        return if (input.startsWith("retry-")) {
            input.removePrefix("retry-")
        } else {
            input
        }
    }


    fun getSortedFilesByNumberPrefix(): List<String> {
        val dir = File(AppUtils.getContext().cacheDir, "action")

        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        return dir.listFiles()?.filter { it.isFile }?.mapNotNull { file ->
            // 提取文件名中 "-" 前的数字部分
            val fileName = file.name
            val dashIndex = fileName.indexOf('-')
            if (dashIndex > 0) {
                val numberStr = fileName.substring(0, dashIndex)
                val number = numberStr.toIntOrNull()
                number?.let { it to file }
            } else null
        }?.sortedBy { it.first }  // 按数字排序
            ?.map { it.second.absolutePath }  // 返回完整路径
            ?: emptyList()
    }

    fun extractThirdChar(fileName: String): Char? {
        return fileName.getOrNull(2)
    }


    // 更详细的版本，带日志和错误处理
    fun getSortedFilesWithLogging(): List<String> {
        val dir = File(
            AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action"
        )

        // 检查目录是否存在
        if (!dir.exists()) {
            Loge.e("FileUtils", "目录不存在: ${dir.absolutePath}")
            return emptyList()
        }

        val files = dir.listFiles()

        if (files.isNullOrEmpty()) {
            Log.e("FileUtils", "目录为空或无读取权限")
            return emptyList()
        }

        val sortedFiles = files.filter { it.isFile }.sortedWith(compareBy { file ->
            // 按第一个字符排序，处理空文件名的情况
            when {
                file.name.isEmpty() -> '~'  // 空文件名放到最后
                else -> file.name[0]
            }
        })

        // 打印排序结果用于调试
        sortedFiles.forEach { file ->
            Loge.d("FileUtils", "排序后: ${file.name} (首字符: ${file.name.firstOrNull()})")
        }

        return sortedFiles.map { it.absolutePath }
    }

    fun endCameraUploadPhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            getSortedFilesWithLogging().forEach { postFile ->
                val file = File(postFile)
                val photoType = extractThirdChar(file.name).toString()
                Loge.d("网络请求 拍照上传 $photoType 文件大小：${file.length()} ")
                val post = mutableMapOf<String, Any>()
                post["sn"] = curSn
                post["transId"] = postTransId
                post["photoType"] = photoType.toInt()
                post["file"] = file
                Loge.d("网络请求 拍照上传 post $post")
                httpRepo.uploadPhoto(post).onSuccess { user ->
                    Loge.d("网络请求 拍照上传 onSuccess ${Thread.currentThread().name} ${user.toString()}")
                }.onFailure { code, message ->
                    Loge.d("网络请求 拍照上传 onFailure $code $message")

                }.onCatch { e ->
                    Loge.d("网络请求 拍照上传 onCatch ${e.errorMsg}")
                }
                delay(2000)
            }
        }

    }

    /**
     * 拍照业务逻辑
     * @param switchType 业务类型 (开门/关门)
     */
//    suspend fun takePhoto2(switchType: Int = -1) = withContext(Dispatchers.IO) {
//        val transId = modelOpenBean?.transId ?: "transId"
//        val setTransId = removeRetryPrefix(transId)
//
//        // 预定义路径
//        val dir = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
//        if (!dir.exists()) dir.mkdirs()
//
//        val fileIn = File(dir, "s-${setTransId}-i-$switchType-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}.jpg")
//        val fileOut = File(dir, "s-${setTransId}-o-$switchType-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}.jpg")
//
//        when (switchType) {
//            CmdCode.GE_OPEN -> {
//                // 1. 拍照：内景 (挂起直到拍照回调完成)
//                val resultIn = cameraManagerNew.takePictureSuspend("0", switchType, "内", fileIn)
//                println("测试我来了 ¥")
//                handlePhotoBusiness(resultIn, setTransId, switchType, photoPos = 0)
//
//                // 2. 间隔 3 秒 (只有第一张处理完了才会开始计时)
//                delay(8000)
//
//                // 3. 拍照：外景
//                val resultOut = cameraManagerNew.takePictureSuspend("1", switchType, "外", fileOut)
//                handlePhotoBusiness(resultOut, setTransId, switchType, photoPos = 2)
//            }
//
//            CmdCode.GE_CLOSE -> {
//                // 1. 拍照：外景
//                val resultOut = cameraManagerNew.takePictureSuspend("1", switchType, "外", fileOut)
//                handlePhotoBusiness(resultOut, setTransId, switchType, photoPos = 3)
//
//                delay(3000)
//
//                // 2. 拍照：内景
//                val resultIn = cameraManagerNew.takePictureSuspend("0", switchType, "内", fileIn)
//                handlePhotoBusiness(resultIn, setTransId, switchType, photoPos = 1)
//            }
//        }
//    }

    /**
     * 统一处理拍照后的上传、入库和 UI 刷新逻辑
     * @param toFile 拍照生成的文件
     * @param photoPos 0:开门内, 1:关门内, 2:开门外, 3:关门外 (根据你原逻辑对应)
     */
    private fun handlePhotoBusiness(toFile: File?, transId: String, switchType: Int, photoPos: Int) {
        if (toFile == null || !toFile.exists()) {
            BoxToolLogUtils.saveCamera("拍照失败或文件不存在: pos=$photoPos")
            return
        }

        val fileName = toFile.name
        val filePath = toFile.absolutePath

        BoxToolLogUtils.saveCamera("开始处理照片业务: $fileName")

        // 1. 执行上传
//        uploadPhoto(curSn, transId, photoPos, fileName, switchType.toString())

        // 2. 写入本地数据库 (0 代表内景，1 代表外景)
        val dbPos = if (photoPos == 0 || photoPos == 1) 0 else 1
        toGoInsertPhoto(transId, switchType.toString(), dbPos, filePath)

        // 3. 通知 UI 或轮询频道刷新
        setRefBusStaChannel(MonitorWeight().apply {
            refreshType = RefBusType.REFRESH_TYPE_4
            takePhotoUrl = filePath
        })
    }

    // 用于通知 Activity 跳转的事件流
    private val _navigationEvent = MutableSharedFlow<Pair<String, Int>>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    // 状态锁
    private val isFetching = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isTransitioning = java.util.concurrent.atomic.AtomicBoolean(false)

    //登录次数
    private var getCount = 1000

    fun getSocketUrl(isDelay: Boolean) {
        // 外部入口拦截
        if (isTransitioning.get() || !isFetching.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isDelay) delay(5000)
                if (isTransitioning.get()) return@launch

                val postSn = SPreUtil[AppUtils.getContext(), SPreUtil.init_sn, ""] as String
                val from = mutableMapOf<String, Any>("sn" to postSn)
                val headers = mutableMapOf<String, String>("token" to BuildConfig.initToken)

                httpRepo.connectAddress(headers, from).onSuccess { initString ->
                    if (isTransitioning.compareAndSet(false, true)) {
                        initString?.let { url ->
                            val parts = url.split(":")
                            // 发送跳转信号
                            _navigationEvent.emit(Pair(parts[0], parts[1].toInt()))
                        }
                    }
                }.onFailure { _, _ ->
                    isFetching.set(false)
                    retry()
                }.onCatch {
                    isFetching.set(false)
                    retry()
                }
            } catch (e: Exception) {
                isFetching.set(false)
                if (e !is CancellationException) retry()
            }
        }
    }

    private fun retry() {
        if (!isTransitioning.get() && getCount > 0) {
            getCount--
            getSocketUrl(true)
        }
    }


    //独立刷新背景圖
    private val _refHomeCodeStateFlow = MutableStateFlow<MonitorHomeCode?>(null)
    val refHomeCodeStateFlow: StateFlow<MonitorHomeCode?> = _refHomeCodeStateFlow.asStateFlow()

    fun setRefHomeCode(result: MonitorHomeCode) {
        viewModelScope.launch(Dispatchers.IO) {
            _refHomeCodeStateFlow.emit(result)
        }
    }

    data class MonitorHomeCode(
        /** 5.刷新背景图 6.二维码 */
        var refreshType: Int = -1, var bitmap: Bitmap? = null)

    private val _refBusStaStateFlow = MutableStateFlow<MonitorWeight?>(null)
    val refBusStaStateFlow: StateFlow<MonitorWeight?> = _refBusStaStateFlow.asStateFlow()

    data class MonitorWeight(
        /** 1.重量 2.警告 3.门开 4.结算页 5.二维码*/
        var refreshType: Int = -1,
        /** 格口几 */
        var doorGeX: Int = CmdCode.GE,
        /** 满溢 正常 故障 */
        var warningContent: String = "",
        /** 拍照的路径 */
        var takePhotoUrl: String = "",
        /** 格口一重量 */
        var curG1WeightPrice: String? = "0.60",
        /** 格口二重量 */
        var curG2WeightPrice: String? = "0.60",
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
            _refBusStaStateFlow.emit(result)
        }
    }

    /***
     * 刷新重量
     * @param weightBeforeOpen 开门前重量
     * @param weightAfterOpening 开门后重量
     * @param weightDuringOpening 关门前重量
     * @param weightAfterClosing 关门后重量
     */
    private suspend fun dbBeforeWeightRefresh(weightBeforeOpen: String, weightAfterOpening: String, weightDuringOpening: String, weightAfterClosing: String, openModel: DoorOpenBean, flowEnd: Boolean = false) =
        withContext(Dispatchers.IO) {
            val oepnTransId = openModel.transId
            val queryTrans = oepnTransId?.let { DatabaseManager.queryTransEntity(AppUtils.getContext(), it) }
            val getTransId = queryTrans?.transId ?: ""
            val weight = DatabaseManager.queryWeightId(AppUtils.getContext(), getTransId)
            val type = openModel.openType
            if (type == 1) {
                //当前重量
                weight.curWeight = if (weightAfterClosing == "0.00") weightDuringOpening else weightAfterClosing

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

                //开门前重量-关门后重量
                val result = CalculationUtil.subtractFloats(weightBeforeOpen, weightAfterClosing)

                //当前重量
                weight.curWeight = if (weightAfterClosing == "0.00") weightDuringOpening else weightAfterClosing

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
            val row = DatabaseManager.upWeightEntity(AppUtils.getContext(), weight)
            val text = when (openModel.openType) {
                1 -> {
                    SendTurnText.fromStatus(100)
                }

                2 -> {
                    SendClearText.fromStatus(200)
                }

                else -> {
                    "其他"
                }
            }
            BoxToolLogUtils.savePrintln("业务流：业务类型【${text}】刷新重量 $row ${openModel.transId} $weight")
            setRefBusStaChannel(MonitorWeight().apply {
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

                    timestamp = System.currentTimeMillis().toString()
                }
                val json = JsonBuilder.convertToJsonString(doorClose)
                sendText(json)
                BoxToolLogUtils.savePrintln("业务流：刷新重量 门已关 发送关门成功 $doorClose")

            }
        }

    private var containersDB = mutableListOf<StateEntity>()


    fun getI1PrefixJpgFiles(): List<String> {
        return try {
            val dir = File(
                AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action"
            )
            if (!dir.exists() || !dir.isDirectory) {
                return emptyList()
            }

            val files = dir.listFiles()
            if (files.isNullOrEmpty()) {
                return emptyList()
            }

            files.filter { file ->
                file.isFile
            }.map { it.absolutePath }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun isImageFile(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
    }


    fun extractThirdValue(input: String): String? {
        val parts = input.split("-")
        return if (parts.size > 2) parts[2] else null
    }


    private var containersJob: Job? = null
    fun cancelContainersStatusJob() {
        Loge.e("验证方式 取消柜体查询")
        containersJob?.cancel()
        containersJob = null
    }


    /****
     * 投递柜状态查询
     */
    fun startContainersStatus() {
        Loge.e("进来查询投递柜")
        if (containersJob?.isActive == true) {
            BoxToolLogUtils.savePrintln("业务流：查询柜体状态 轮询已在运行")
            return
        }

        containersJob = ioScope.launch {
            while (isActive) {
                try {
                    SerialPortSdk.queryStatus().onSuccess { result ->
                        if (containersDB.isEmpty()) {
                            containersDB = DatabaseManager.queryStateList(AppUtils.getContext()).toMutableList()
                        }
                        Loge.e("进来查询投递柜结果")
                        val size = containersDB.size
                        Loge.e("业务流：startStatus onSuccess $result")
                        result.containers.withIndex().forEach { (index, lower) ->
                            when (index) {
                                0 -> {
                                    val state = containersDB[0]
                                    //取出原始重量
                                    val weightNew = lower.weigh?.toFloat() ?: 0.0f
                                    curG1Weight = weightNew.toString()
                                    val weightOld = state.weigh.toString()
                                    //处理重量浮动变化
                                    val isChange = CalculationUtil.subtractFloatsBoolean(
                                        weightNew.toString(), weightOld
                                    )
                                    val result1 = WeightChangeStorage(AppUtils.getContext()).putWithCooldown(
                                        "key_weight1", if (isChange) "success" else "Failure", devWeiChaMapSend[0]
                                            ?: false
                                    )
                                    if (result1 && isChange) {
                                        devWeiChaMapCun[0] = weightNew.toString()
                                        devWeiChaMapSend[0] = true
                                    }
                                    Loge.e("执行重量变动 查询 下位机-1 |new:${lower.weigh} old:${weightOld} | 改：$isChange 结：$result1")
                                    val irStateValue = lower.irStateValue
                                    val irDoorStatusValue = lower.doorStatusValue
                                    val lockStatus = lower.lockStatusValue
                                    val runStatus = lower.xzStatusValue
                                    state.smoke = lower.smokeValue
                                    state.irState = irStateValue
                                    state.doorStatus = irDoorStatusValue
                                    state.weigh = weightNew
                                    state.lockStatus = lockStatus
                                    state.time = AppUtils.getDateYMDHMS()
                                    val curG1Total = curG1TotalWeight.toFloat()
                                    val irOverflow = SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                                    //实时总重量 心跳 上报前数据
                                    val curG1Weight = state.weigh
                                    //上报重量大于总重量则报提示 当我上传心跳的时候  capacity //容量[0可投递, 1红外遮挡, 2超重, 3红外遮挡后-投递超重]
                                    if (curG1Weight > curG1Total) {
                                        state.capacity = 2
                                    } else if (curG1Weight > irOverflow && irStateValue == 1) {
                                        state.capacity = 3
                                    } else if (irStateValue == 1) {
                                        state.capacity = 1
                                    } else if (irStateValue == 0) {
                                        state.capacity = 0
                                    }
                                    val setIr1 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr1, -1] as Int
                                    if (setIr1 == 1) {
                                        maptDoorFault[FaultType.FAULT_CODE_11] = irStateValue == 1
                                    } else {
                                        maptDoorFault[FaultType.FAULT_CODE_11] = false
                                        maptDoorFault[FaultType.FAULT_CODE_211] = false
                                    }
                                    //不等于故障则推杆正常
                                    val turnBoo = irDoorStatusValue == 3
                                    if (!turnBoo) {
                                        maptDoorFault[FaultType.FAULT_CODE_111] = false
                                        maptDoorFault[FaultType.FAULT_CODE_110] = false
                                    }
                                    maptDoorFault[FaultType.FAULT_CODE_91] = turnBoo
                                    maptDoorFault[FaultType.FAULT_CODE_5101] = runStatus == 0
                                    refreshContainers(state, 0)
                                }

                                1 -> {
                                    if (size > 1 && doorGeXType == CmdCode.GE2) {
                                        val state = containersDB[1]
                                        val weightNew = lower.weigh?.toFloat() ?: 0.0f
                                        val weightOld = state.weigh.toString()
                                        curG2Weight = weightNew.toString()
                                        //处理重量浮动变化
                                        val isChange = CalculationUtil.subtractFloatsBoolean(
                                            weightNew.toString(), weightOld
                                        )
                                        val result1 = WeightChangeStorage(AppUtils.getContext()).putWithCooldown(
                                            "key_weight2", if (isChange) "success" else "Failure", devWeiChaMapSend[1]
                                                ?: false
                                        )
                                        if (result1 && isChange) {
                                            devWeiChaMapCun[1] = weightNew.toString()
                                            devWeiChaMapSend[1] = true
                                        }
                                        Loge.e("执行重量变动 查询 下位机-1 |new:${lower.weigh} old:${weightOld} | 改：$isChange 结：$result1")
                                        val irStateValue = lower.irStateValue
                                        val irDoorStatusValue = lower.doorStatusValue
                                        val lockStatus = lower.lockStatusValue
                                        val runStatus = lower.xzStatusValue
                                        state.smoke = lower.smokeValue
                                        state.irState = irStateValue
                                        state.doorStatus = irDoorStatusValue
                                        state.weigh = weightNew
                                        state.lockStatus = lockStatus
                                        state.time = AppUtils.getDateYMDHMS()
                                        val curG2Total = curG2TotalWeight.toFloat()
                                        val irOverflow = SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                                        //实时总重量
                                        val curG2Weight = state.weigh
                                        //上报重量大于总重量则报提示
                                        if (curG2Weight > curG2Total) {
                                            state.capacity = 2
                                        } else if (curG2Weight > irOverflow && irStateValue == 1) {
                                            state.capacity = 3
                                        } else if (irStateValue == 1) {
                                            state.capacity = 1
                                        } else if (irStateValue == 0) {
                                            state.capacity = 0
                                        }
                                        val setIr2 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr2, 1] as Int
                                        if (setIr2 == 1) {
                                            maptDoorFault[FaultType.FAULT_CODE_12] = irStateValue == 1
                                        } else {
                                            maptDoorFault[FaultType.FAULT_CODE_12] = false
                                            maptDoorFault[FaultType.FAULT_CODE_212] = false
                                        }
                                        //不等于故障则推杆正常
                                        val turnBoo = irDoorStatusValue == 3
                                        if (!turnBoo) {
                                            maptDoorFault[FaultType.FAULT_CODE_121] = false
                                            maptDoorFault[FaultType.FAULT_CODE_120] = false
                                        }
                                        maptDoorFault[FaultType.FAULT_CODE_92] = turnBoo
                                        maptDoorFault[FaultType.FAULT_CODE_5202] = runStatus == 0
                                        refreshContainers(state, 1)
                                    }
                                }

                            }

                        }
                    }.onFailure { e ->
                        BoxToolLogUtils.savePrintln("业务流：轮询onFailure: ${e.message}")
                    }
                    Loge.e("查询版本开始 $isQueryVersion")
                    if (!isQueryVersion) {
                        startChipVersion()
                    }
                } catch (e: TimeoutCancellationException) {
                    BoxToolLogUtils.savePrintln("业务流：轮询超时: ${e.message}")
                } catch (e: Exception) {
                    BoxToolLogUtils.savePrintln("业务流：轮询异常: ${e.message}")
                }
                delay(2000)
            }
        }
    }


    private suspend fun refreshContainers(state: StateEntity, index: Int) =
        withContext(Dispatchers.IO) {
            val row = DatabaseManager.upStateEntity(AppUtils.getContext(), state)
            Loge.e("流程 synStateHeart 同步更新心跳上传重量 $isClearStatus $row $state ${containersDB.size}")
            containersDB[index] = state
            if (containersDB.size == 1) {
                setRefBusStaChannel(MonitorWeight().apply {
                    refreshType = RefBusType.REFRESH_TYPE_1
                    doorGeX = CmdCode.GE1
                    curG1WeightPrice = curGe1Price
                    curG1WeightValue = state.weigh.toString()
                })
            } else {
                if (index == 0) {
                    setRefBusStaChannel(MonitorWeight().apply {
                        refreshType = RefBusType.REFRESH_TYPE_1
                        doorGeX = CmdCode.GE1
                        curG1WeightPrice = curGe1Price
                        curG1WeightValue = state.weigh.toString()
                    })
                } else if (index == 1) {
                    setRefBusStaChannel(MonitorWeight().apply {
                        refreshType = RefBusType.REFRESH_TYPE_1
                        doorGeX = CmdCode.GE2
                        curG2WeightPrice = curGe2Price
                        curG2WeightValue = state.weigh.toString()
                    })
                }
            }
            val overflowState = SPreUtil[AppUtils.getContext(), SPreUtil.overflowState, false] as Boolean
            val overflowStateValue = SPreUtil[AppUtils.getContext(), SPreUtil.overflowStateValue, 1] as Int
            //刷新满溢状态
            when (index) {
                0 -> {
                    val irOverflow = SPreUtil[AppUtils.getContext(), SPreUtil.irOverflow, 10] as Int
                    val curG1Total = curG1TotalWeight.toFloat()
                    //实时总重量
                    val curG1Weight = state.weigh
                    val setIr1 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr1, -1] as Int
                    //上报重量大于总重量则报提示
                    if (overflowState) {//服务器下发漫溢状态 当服务器改为false 走下面的逻辑
                        if (overflowStateValue == 1) { //
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                doorGeX = CmdCode.GE1
                                warningContent = BusType.BUS_OVERFLOW
                            })
                            maptDoorFault[FaultType.FAULT_CODE_2110] = true
                        } else if (overflowStateValue == 0) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                doorGeX = CmdCode.GE1
                                warningContent = BusType.BUS_NORMAL
                            })
                            maptDoorFault[FaultType.FAULT_CODE_2110] = false
                        } else {

                        }
                    } else if (curG1Weight > curG1Total && setIr1 == 1) {
                        setRefBusStaChannel(MonitorWeight().apply {
                            refreshType = RefBusType.REFRESH_TYPE_2
                            doorGeX = CmdCode.GE1
                            warningContent = BusType.BUS_OVERFLOW
                        })
                        maptDoorFault[FaultType.FAULT_CODE_1111] = true
                    } else {
                        setRefBusStaChannel(MonitorWeight().apply {
                            refreshType = RefBusType.REFRESH_TYPE_2
                            doorGeX = CmdCode.GE1
                            warningContent = BusType.BUS_NORMAL
                        })
                        maptDoorFault[FaultType.FAULT_CODE_1111] = false
                    }
                }

                1 -> {
                    val curG2Total = curG2TotalWeight.toFloat()
                    //实时总重量
                    val curG2Weight = state.weigh
                    val setIr2 = SPreUtil[AppUtils.getContext(), SPreUtil.saveIr2, -1] as Int
                    //上报重量大于总重量则报提示
                    if (overflowState) {//服务器下发漫溢状态 当服务器改为false 走下面的逻辑
                        if (overflowStateValue == 1) { //
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                doorGeX = CmdCode.GE2
                                warningContent = BusType.BUS_OVERFLOW
                            })
                            maptDoorFault[FaultType.FAULT_CODE_2120] = true
                        } else if (overflowStateValue == 0) {
                            setRefBusStaChannel(MonitorWeight().apply {
                                refreshType = RefBusType.REFRESH_TYPE_2
                                doorGeX = CmdCode.GE2
                                warningContent = BusType.BUS_NORMAL
                            })
                            maptDoorFault[FaultType.FAULT_CODE_2120] = false
                        } else {

                        }
                    } else if (curG2Weight > curG2Total && setIr2 == 1) {
                        setRefBusStaChannel(MonitorWeight().apply {
                            refreshType = RefBusType.REFRESH_TYPE_2
                            doorGeX = CmdCode.GE2
                            warningContent = BusType.BUS_OVERFLOW
                        })
                        maptDoorFault[FaultType.FAULT_CODE_1112] = true
                    } else {
                        setRefBusStaChannel(MonitorWeight().apply {
                            refreshType = RefBusType.REFRESH_TYPE_2
                            doorGeX = CmdCode.GE2
                            warningContent = BusType.BUS_NORMAL
                        })
                        maptDoorFault[FaultType.FAULT_CODE_1112] = false
                    }
                }
            }
        }

    /***
     * 重启动app
     */
    private suspend fun restartAppCloseDoor(doorGeX: Int) = withContext(Dispatchers.IO) {
        Loge.e("流程 toGoCmdOtaBin 进来了 restartAppCloseDoor")
        val code = when (doorGeX) {
            CmdCode.GE1 -> CmdCode.GE12
            CmdCode.GE2 -> CmdCode.GE22
            else -> -1
        }
        val turnDoorStatusValue = SerialPortSdk.turnDoorStatus(doorGeX)
        if (turnDoorStatusValue.isFailure) {
            BoxToolLogUtils.savePrintln("业务流：重启动应用 开启启动关门失败：${turnDoorStatusValue.exceptionOrNull()?.message}")
            return@withContext
        }
        val doorStatus = turnDoorStatusValue.getOrNull()?.status ?: -1
        BoxToolLogUtils.savePrintln("业务流：重启动应用 等待门物理状态变为【$doorStatus】")
        if (doorStatus == CmdCode.GE_OPEN) {
            toGoOpenCloseAudio(CmdCode.GE_CLOSE)
            val turnDoor = SerialPortSdk.turnDoor(code)
            BoxToolLogUtils.savePrintln("业务流：开启启动关门【$turnDoor】")
        }
    }

    //////////////////////////////////////////////////////调试页面功能/////////////////////////////////////////////////////////////////////////////////
    private var queryStatusJob: Job? = null

    fun cancelStartQueryStatus() {
        BoxToolLogUtils.savePrintln("业务流： 取消柜查询柜体状态")
        queryStatusJob?.cancel()
        queryStatusJob = null
    }

    var isLookState = false

    /***
     * 查询柜体信息
     */
    fun startQueryStatus(stateResult: (containers: MutableList<ContainersResult>) -> Unit) {
        if (queryStatusJob?.isActive == true) {
            BoxToolLogUtils.savePrintln("业务流：查询柜体状态 轮询已在运行")
            return
        }
        queryStatusJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && isLookState) {
                SerialPortSdk.queryStatus().onSuccess { result ->
                    stateResult(result.containers)
                }.onFailure { e ->
                    BoxToolLogUtils.savePrintln("业务流： startStatus onFailure ${e.message}")
                }
            }
        }
    }

    var isQueryVersion = false

    /***
     * 查询版本
     */
    suspend fun startChipVersion() = withContext(Dispatchers.IO) {
        SerialPortSdk.startQueryVersion().onSuccess { result ->
            isQueryVersion = true
            SPreUtil.put(AppUtils.getContext(), SPreUtil.gversion, result.chipVersion)
            BoxToolLogUtils.savePrintln("业务流：查询版本【${result.chipVersion}】")
        }.onFailure { e ->
            BoxToolLogUtils.savePrintln("业务流：查询版本失败【${e.message}】")
        }
    }

    /***
     * @param doorGeX
     * 启动推杆
     */
    fun startTurnDoor(doorGeX: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val turnDoorValue = SerialPortSdk.turnDoor(doorGeX)
            if (turnDoorValue.isFailure) {
                tipMessage("业务流 启动推杆失败 ${turnDoorValue.exceptionOrNull()?.message}")
                return@launch
            }
            val status = turnDoorValue.getOrNull()?.status ?: -1
            BoxToolLogUtils.savePrintln("业务流：等待推杆状态【${SendTurnText.fromStatus(status)}】")
        }

    }

    /***
     * @param inOut 内外类型
     * 启动灯光开启
     */
    fun startLights(inOut: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val lightValue = SerialPortSdk.startLights(inOut)
            if (lightValue.isFailure) {
                tipMessage("业务流 启动灯光失败 ${lightValue.exceptionOrNull()?.message}")
                return@launch
            }
            val status = lightValue.getOrNull()?.status ?: -1
            tipMessage("业务流：等待灯光状态【${SendTurnText.fromStatus(status)}】")
            BoxToolLogUtils.savePrintln("业务流：等待灯光状态【${SendTurnText.fromStatus(status)}】")
        }
    }

    /***
     * @param doorGeX
     * @param rodHinderNumber
     * @param rodHinderResult
     * 启动设置助力值
     */
    fun startRodHinder(doorGeX: Int, rodHinderNumber: Int, rodHinderResult: (lockerNo: Int, rodHinderValue: Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val rodHinderValue = SerialPortSdk.startRodHinder(doorGeX, rodHinderNumber)
            if (rodHinderValue.isFailure) {
                tipMessage("业务流 设置阻力阀值失败: ${rodHinderValue.exceptionOrNull()?.message}")
                return@launch
            }
            val locker = rodHinderValue.getOrNull()?.locker ?: doorGeX
            val value = rodHinderValue.getOrNull()?.rodHinderValue ?: rodHinderNumber
            rodHinderResult(locker, value)
            tipMessage("设置阻力阀值成功 $value")
            BoxToolLogUtils.savePrintln("业务流：等待阻力值状态【$value】")
        }
    }

    /***
     * @param doorGeX
     * @param onGetDoorStatus
     * 查询投递门状态
     */
    fun startDoorStratus(doorGeX: Int, onGetDoorStatus: (status: Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val doorStatusValue = SerialPortSdk.turnDoorStatus(doorGeX)
            if (doorStatusValue.isFailure) {
                tipMessage("业务流：等待投递门失败: ${doorStatusValue.exceptionOrNull()?.message}")
                return@launch
            }
            val doorStatus = doorStatusValue.getOrNull()?.status ?: 0
            onGetDoorStatus(doorStatus)
            tipMessage("业务流：等待投递门状态【${SendTurnText.fromStatus(doorStatus)}】")
            BoxToolLogUtils.savePrintln("业务流：等待投递门状态【${SendTurnText.fromStatus(doorStatus)}】")
        }
    }

    /***
     * @param code
     * 打开清运门
     */
    fun startClearDoor(code: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val doorStatusValue = SerialPortSdk.openQueryClear(code)
            if (doorStatusValue.isFailure) {
                tipMessage("业务流：等待清运门状态失败: ${doorStatusValue.exceptionOrNull()?.message}")
                return@launch
            }
            val doorStatus = doorStatusValue.getOrNull()?.status ?: 0
            tipMessage("业务流：等待清运门状态【${SendTurnText.fromStatus(doorStatus)}】")
            BoxToolLogUtils.savePrintln("业务流：等待清运门状态【${SendTurnText.fromStatus(doorStatus)}】")
        }
    }


    /***
     * @param doorGeX
     * 获取重量
     */
    fun startQueryWeight(doorGeX: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val weightValue = SerialPortSdk.queryWeight(doorGeX)
            if (weightValue.isFailure) {
                tipMessage("业务流：等待获取重量失败: ${weightValue.exceptionOrNull()?.message}")
                return@launch
            }
            val weight = weightValue.getOrNull()?.weight ?: 0
            tipMessage("业务流：等待获取重量【$weight】")
            BoxToolLogUtils.savePrintln("业务流：等待获取重量【$weight】")
        }
    }

    /***
     * @param doorGeX
     * @param code
     * 去皮清零
     */
    fun startCalibrationQP(doorGeX: Int, code: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val calibrationQPValue = SerialPortSdk.startCalibrationQP(doorGeX, code)
            if (calibrationQPValue.isFailure) {
                tipMessage("业务流：等待去皮清零状态失败: ${calibrationQPValue.exceptionOrNull()?.message}")
                return@launch
            }
            val status = calibrationQPValue.getOrNull()?.caliStatus ?: 0
            val statusText = if (status == 1) "成功" else "失败"
            tipMessage("业务流：等待去皮清零状态【${statusText}】")
            BoxToolLogUtils.savePrintln("业务流：等待去皮清零状态【$status】")
        }
    }

    /***
     * @param doorGeX
     * @param code
     * 校准
     */
    fun startCalibration(doorGeX: Int, code: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val calibrationValue = SerialPortSdk.startCalibration(doorGeX, code)
            if (calibrationValue.isFailure) {
                tipMessage("业务流：等待校准状态失败: ${calibrationValue.exceptionOrNull()?.message}")
                return@launch
            }
            val status = calibrationValue.getOrNull()?.caliStatus ?: 0
            when (code) {
                //零点校准
                CmdCode.CALIBRATION_1 -> {
                    if (status == 1) {
                        caliBefore2.emit(1)
                    } else {
                        caliBefore2.emit(0)
                    }
                }
                //校准2KG
                CmdCode.CALIBRATION_2 -> {
                    if (status == 1) {
                        caliResult.emit(1)
                    } else {
                        caliResult.emit(0)
                    }
                }
                //校准25KG
                CmdCode.CALIBRATION_3 -> {
                    if (status == 1) {
                        caliResult.emit(1)
                    } else {
                        caliResult.emit(0)
                    }
                }
                //校准100KG
                CmdCode.CALIBRATION_4 -> {
                    if (status == 1) {
                        caliResult.emit(1)
                    } else {
                        caliResult.emit(0)
                    }
                }
                //校准100KG
                CmdCode.CALIBRATION_5 -> {
                    if (status == 1) {
                        caliResult.emit(1)
                    } else {
                        caliResult.emit(0)
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
        cancelServiceClose()
        cancelJobAgain()
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    /*******************************************新方案调试******************************************************/
}
