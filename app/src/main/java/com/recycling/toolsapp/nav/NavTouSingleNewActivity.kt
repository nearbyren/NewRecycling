package com.recycling.toolsapp.nav

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import com.cabinet.toolsapp.tools.bus.FlowBus
import com.cabinet.toolsapp.tools.bus.ResEvent
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.recycling.toolsapp.BuildConfig
import com.recycling.toolsapp.FaceApplication
import com.recycling.toolsapp.R
import com.recycling.toolsapp.databinding.NavTouSingleActivityBinding
import com.recycling.toolsapp.fitsystembar.showSystemBar
import com.recycling.toolsapp.http.TaskRestartScheduler
import com.recycling.toolsapp.model.LogEntity
import com.recycling.toolsapp.socket.AdminOverflowBean
import com.recycling.toolsapp.socket.ConfigBean
import com.recycling.toolsapp.socket.DoorCloseBean
import com.recycling.toolsapp.socket.DoorOpenBean
import com.recycling.toolsapp.socket.OtaBean
import com.recycling.toolsapp.socket.PhotoBean
import com.recycling.toolsapp.socket.RestartBean
import com.recycling.toolsapp.utils.CmdValue
import com.recycling.toolsapp.utils.CommandParser
import com.recycling.toolsapp.utils.EnumSignal
import com.recycling.toolsapp.utils.FaultType
import com.recycling.toolsapp.utils.NetworkStateManager
import com.recycling.toolsapp.utils.OSUtils
import com.recycling.toolsapp.utils.SignalStrengthAnalyzer
import com.recycling.toolsapp.utils.SnackbarUtils
import com.recycling.toolsapp.vm.CabinetVM
import com.recycling.toolsapp.vm.NewDualUsbCameraManager.CameraErrorListener
import com.serial.port.utils.AppUtils
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.Loge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nearby.lib.netwrok.response.SPreUtil
import nearby.lib.signal.livebus.BusType
import nearby.lib.signal.livebus.LiveBus
import java.io.File
import kotlin.random.Random


@AndroidEntryPoint
class NavTouSingleNewActivity : AppCompatActivity() {
    private val cabinetVM: CabinetVM by viewModels()
    private lateinit var binding: NavTouSingleActivityBinding
    private lateinit var networkStateManager: NetworkStateManager

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = NavTouSingleActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.showSystemBar(false)
        FaceApplication.getInstance().baseActivity = this
        initialize(savedInstanceState)

    }

    private fun initNetworkState() {
        //全局处理提示信息
        lifecycleScope.launch {
            cabinetVM._isNetworkMsg.collect {
                Loge.d("全局提示信息  $it ${Thread.currentThread().name}")
                SnackbarUtils.show(
                    activity = this@NavTouSingleNewActivity, message = it, duration = Snackbar.LENGTH_LONG, textColor = Color.WHITE, textAlignment = View.TEXT_ALIGNMENT_CENTER, horizontalCenter = true, position = SnackbarUtils.Position.CENTER
                )
            }
        }
        LiveBus.get(BusType.BUS_NET_MSG).observeForever {
            Loge.d("流程 网络请求 错误内容 $it")
            SnackbarUtils.show(
                activity = this@NavTouSingleNewActivity, message = it.toString(), duration = Snackbar.LENGTH_LONG, textColor = Color.WHITE, textAlignment = View.TEXT_ALIGNMENT_CENTER, horizontalCenter = true, position = SnackbarUtils.Position.CENTER
            )
        }
        LiveBus.get(BusType.BUS_LIGHTS_MSG).observeForever {
            Loge.d("测试我来了 ${Thread.currentThread().name}")
            //开灯
            val lightOn = SPreUtil[AppUtils.getContext(), SPreUtil.lightOn, "0"]
            if (lightOn == it) {
//                cabinetVM.testLightsCmd(CmdCode.OUT_LIGHTS_OPEN)
                cabinetVM.startLights(CmdCode.OUT_LIGHTS_OPEN)
            }
            //关灯
            val lightOff = SPreUtil[AppUtils.getContext(), SPreUtil.lightOff, "0"]
            if (lightOff == it) {
//                cabinetVM.testLightsCmd(CmdCode.OUT_LIGHTS_CLOSE)
                cabinetVM.startLights(CmdCode.OUT_LIGHTS_CLOSE)
            }
            Loge.d("流程 关开灯 testLightsCmd $it - 开：${lightOn} | 关：${lightOff}")
        }

        networkStateManager = NetworkStateManager.getInstance(this)
        networkStateManager.startMonitoring()

        observeNetworkState()
    }


    private fun observeNetworkState() {
        // 观察网络状态
        lifecycleScope.launch {
            networkStateManager.networkState.collect { state ->
                cabinetVM.saveRecordSocket(CmdValue.CONNECTING, "net,$state")
                when (state) {
                    NetworkStateManager.NetworkState.Unknown -> {
                        Loge.e("网络测试 检测网络中...")
                        binding.acivSignal.setBackgroundResource(R.drawable.ic_xinhao0)

                    }

                    NetworkStateManager.NetworkState.Disconnected -> {
                        Loge.e("网络测试 网络已断开...")
                        binding.acivSignal.setBackgroundResource(R.drawable.ic_xinhao0)

                    }

                    is NetworkStateManager.NetworkState.Connected -> {
                        val status = when (state.type) {
                            NetworkStateManager.ConnectionType.WIFI -> "已连接WiFi"
                            NetworkStateManager.ConnectionType.CELLULAR -> "已连接移动网络"
                            else -> "已连接网络"
                        }
                        binding.acivSignal.setBackgroundResource(R.drawable.ic_xinhao1)
                        Loge.e("网络测试 $status")
                        binding.tvNetwork.text = "$status"
                    }
                }
            }
        }

        // 观察网络质量
        lifecycleScope.launch {
            networkStateManager.networkQuality.collect { quality ->
                when (quality) {
                    NetworkStateManager.NetworkQuality.POOR -> {
                        Loge.e("网络测试 网络较差")
                        binding.tvNetwork.text = "网络较差"
                    }

                    else -> {
                        Loge.e("网络测试 $quality")
                    }
                }
            }
        }

        // 观察网络状态变化事件
        lifecycleScope.launch {
            networkStateManager.networkStateChangeEvents.collect { event ->
                event?.let {
                    if (it.isConnectionLost) {
                        SnackbarUtils.show(
                            activity = this@NavTouSingleNewActivity, message = "网络已经断开", duration = Snackbar.LENGTH_LONG, textColor = Color.WHITE, textAlignment = View.TEXT_ALIGNMENT_CENTER, horizontalCenter = true, position = SnackbarUtils.Position.CENTER
                        )
                        binding.acivSignal.setBackgroundResource(R.drawable.ic_xinhao0)
                        binding.tvNetwork.text = "网络已经断开"
                    } else if (it.isConnectionRestored) {
                        SnackbarUtils.show(
                            activity = this@NavTouSingleNewActivity, message = "网络连接已恢复", duration = Snackbar.LENGTH_LONG, textColor = Color.WHITE, textAlignment = View.TEXT_ALIGNMENT_CENTER, horizontalCenter = true, position = SnackbarUtils.Position.CENTER
                        )
                        binding.acivSignal.setBackgroundResource(R.drawable.ic_xinhao1)
                        binding.tvNetwork.text = "网络已经连接"
                    }
                    // 消费事件后清除
                    networkStateManager.clearStateChangeEvent()
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun initialize(savedInstanceState: Bundle?) {
        initVerSn()
        initNetworkState()
        val initSocket = SPreUtil[AppUtils.getContext(), SPreUtil.initSocket, false] as Boolean
        Loge.e("出厂配置 initSocket NavTouSingleActivity initialize $initSocket")
        if (initSocket) {
            Loge.e("测试我来了 刷新背景图 initSocket")
            initSocket()
        }
        initReadSignal()
        latestBusinessStatus()
    }

    private fun initVerSn(text: String? = "0") {
        val initSn = SPreUtil[AppUtils.getContext(), SPreUtil.init_sn, ""]
        val gversion = SPreUtil[AppUtils.getContext(), SPreUtil.gversion, CmdCode.GJ_VERSION]
        binding.tvSn.text = "$initSn"
        binding.tvVersion.text = "版本号：$text-v${AppUtils.getVersionName()}-v${gversion}"
    }

    private val signalAnalyzer = SignalStrengthAnalyzer()
    private var telephonyManager: TelephonyManager? = null
    private fun initReadSignal() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        // 监听信号强度变化
        val phoneStateListener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                updateSignalDisplay(signalStrength)
            }
        }
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        lifecycleScope.launch {
            cabinetVM.isReadSignal.collect { result ->
                Loge.d("流程 接收信号值 $result")
                if (result == -1) return@collect
                getCurrentSignalStrength()
            }
        }
        cabinetVM.pollingReadSignal()
    }

    private fun updateSignalDisplay(signalStrength: SignalStrength) {
        // 获取简化的信号等级
        val simpleLevel = signalAnalyzer.getSimpleSignalLevel(signalStrength)
        Loge.d("信号读取 当前信号: $simpleLevel")

        // 获取详细信号信息
        val detailedInfo = signalAnalyzer.getDetailedSignalInfo(signalStrength)
        detailedInfo.forEach { (key, value) ->
            Loge.d("信号读取 $key: $value")
        }
        // 原始分析结果
        val analysis = signalAnalyzer.analyzeSignalStrength(signalStrength)
        Loge.d("信号读取 分析结果: ${analysis.description}")
        val levelDesc = when (analysis.signalLevel) {
            0 -> "无服务"
            1 -> "极差"
            2 -> "差"
            3 -> "一般"
            4 -> "好"
            else -> "未知"
        }
        binding.tvSignal.text = "$levelDesc"
        Loge.d("信号读取 信号质量: ${analysis.quality.displayName}")
        val signal = EnumSignal.getDescByCode(analysis.quality.displayName)

        val setSignal = when (signal) {
            "0" -> {
                Random.nextInt(22, 33)

            }

            "1" -> {
                Random.nextInt(17, 22)
            }

            "2" -> {
                Random.nextInt(12, 17)

            }

            "3" -> {
                Random.nextInt(6, 12)
            }

            "4" -> {
                Random.nextInt(0, 6)
            }

            else -> {
                Random.nextInt(0, 6)
            }
        }
        Loge.d("信号读取 上报数据: $signal | $setSignal")
        SPreUtil.put(AppUtils.getContext(), SPreUtil.setSignal, setSignal)
    }

    /**
     * 手动获取当前信号强度
     */
    @SuppressLint("NewApi")
    private fun getCurrentSignalStrength() {
        try {
            val signalStrength = telephonyManager?.signalStrength
            signalStrength?.let {
                updateSignalDisplay(it)
            }
        } catch (e: Exception) {
            Loge.d("信号读取 获取信号强度失败: ${e.message}")
        }
    }

    private fun initPort() {
        // 启动门控制系统
        cabinetVM.startContainersStatus()
        //启动检查故障
        cabinetVM.startPollingFault()
        ///启动查询版本
        cabinetVM.startChipVersion()
//        cabinetVM.startUpgradeWorkflow()
    }

    /***
     * socket 连接 和 接收服务器下发
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun initSocket() {
        val host = intent.getStringExtra("host")
        val port = intent.getIntExtra("port", BuildConfig.socketPort)
        Loge.e("出厂配置 initSocket host: $host | port: $port ")
        //socket 登录成功加载页面
        lifecycleScope.launch {
            cabinetVM.getLoginCmd.collect {
                if (it) {
                    Loge.e("测试我来了 刷新背景图 getLoginCmd")
                    Loge.e("流程 navigateToHome saveSocketInitData 加载fragment")
                    binding.acivInit.isVisible = false
                    cabinetVM.doorGeXType = CmdCode.GE1
                    initPort()
                    FlowBus.with<ResEvent>("ResEvent").post(this, ResEvent().apply {})
                    refreshHomeRes()
                }
            }
        }
        lifecycleScope.launch {
            cabinetVM.isRefreshHomeRes.collect {
                refreshHomeRes()
            }
        }
        binding.tvNetwork.setOnClickListener {
            val targetNumber = "172604022105219755278313"
            val randomString = generateRandomNumberString(targetNumber.length)
//            cabinetVM.startLockerDoorWorkflow(
//                DoorOpenBean().apply {
//                    cmd = "openDoor"
//                    openType = 1
//                    cabinId = "20251015171646408518"
//                    transId = randomString
//
//                }, cabinetVM.curG1Weight
//                    ?: "0.00", CmdCode.GE1, CmdCode.GE11, CmdCode.GE10, CmdCode.GE12
//            )
//            cabinetVM.startLockerClearWorkflow(
//                DoorOpenBean().apply {
//                    cmd = "openDoor"
//                    openType = 2
//                    cabinId = "20251015171646408518"
//                    transId = randomString
//
//                }, cabinetVM.curG1Weight
//                    ?: "0.00", CmdCode.GE1, CmdCode.CLEAR_OPEN_1_1, CmdCode.CLEAR_QUERY_1_0
//            )
//            cabinetVM.startUpgradeWorkflow()
        }
        //socket 监听是否连接成功 接收服务器下发
        lifecycleScope.launch {
            cabinetVM.initConfigSocket(host!!, port)
            cabinetVM.state.collect {
                Loge.e("出厂配置 initSocket 连接状态: $it | ${Thread.currentThread().name}")
                cabinetVM.saveRecordSocket(CmdValue.CONNECTING, "socket,$it")
                when (it) {
                    CabinetVM.ConnectionState.START -> {
                        Loge.e("出厂配置 initSocket NavTouSingleActivity addSocketResultListener2 监 开始：${Thread.currentThread().name} | state $")
                    }

                    CabinetVM.ConnectionState.DISCONNECTED -> {
                        Loge.e("出厂配置 initSocket NavTouSingleActivity addSocketResultListener2 监 已断开连接：${Thread.currentThread().name} | state $")
                        cabinetVM.isDistClient = true
                        socketToast(true)
                        initVerSn("d")

                    }

                    CabinetVM.ConnectionState.CONNECTING -> {
                        Loge.e("出厂配置 initSocket NavTouSingleActivity addSocketResultListener2 监 正在连接：${Thread.currentThread().name} | state $")
                        initVerSn("i")
                    }

                    CabinetVM.ConnectionState.CONNECTED -> {
                        Loge.e("出厂配置 initSocket NavTouSingleActivity addSocketResultListener2 监 已连接：${Thread.currentThread().name} | state $")
                        val loginCount = SPreUtil[AppUtils.getContext(), SPreUtil.loginCount, 0] as Int
                        val result = loginCount + 1
                        SPreUtil.put(AppUtils.getContext(), SPreUtil.loginCount, result)
                        cabinetVM.toGoCmdLogin(result)//监听连接成功登录
                        initVerSn("c")

                    }
                }
            }
            Loge.e("出厂配置 initSocket ${Thread.currentThread().name} vmClient = ${cabinetVM} | state = ${cabinetVM?.state}")

        }
        lifecycleScope.launch {
            cabinetVM.incoming?.collect { bytes ->
                socketToast(false)
                initVerSn("s")
                Loge.e("出厂配置 initSocket 流程 recv: ${String(bytes)}")
                val json = String(bytes)
                val cmd = CommandParser.parseCommand(json)

                when (cmd) {
                    CmdValue.CMD_HEART_BEAT -> {

                    }

                    CmdValue.CMD_LOGIN -> {
                        val loginModel = Gson().fromJson(json, ConfigBean::class.java)
                        if (loginModel.retCode == 0) {
                            val loginCount = SPreUtil[AppUtils.getContext(), SPreUtil.loginCount, 0] as Int
                            val result = loginCount + 1
                            SPreUtil.put(AppUtils.getContext(), SPreUtil.loginCount, result)
                            cabinetVM.saveSocketInitData(loginModel, false)
                        } else {
                            //这里继续延续登录
                            BoxToolLogUtils.savePrintln("socketClient,登录失败")
                            cabinetVM.toGoAgainLogin()
                        }

                    }

                    CmdValue.CMD_INIT_CONFIG -> {
                        val initConfigModel = Gson().fromJson(json, ConfigBean::class.java)
                        cabinetVM.saveSocketInitData(initConfigModel, true)
                    }

                    CmdValue.CMD_OPEN_DOOR -> {
                        val doorOpenModel = Gson().fromJson(json, DoorOpenBean::class.java)
                        val openType = doorOpenModel.openType
                        when (openType) {
                            1 -> {
                                cabinetVM.startLockerDoorWorkflow(
                                    doorOpenModel, cabinetVM.curG1Weight
                                        ?: "0.00", CmdCode.GE1, CmdCode.GE11, CmdCode.GE10, CmdCode.GE12
                                )
                            }

                            2 -> {
                                cabinetVM.startLockerClearWorkflow(
                                    doorOpenModel, cabinetVM.curG1Weight
                                        ?: "0.00", CmdCode.GE1, CmdCode.CLEAR_OPEN_1_1, CmdCode.CLEAR_QUERY_1_0
                                )
                            }
                        }
                    }

                    CmdValue.CMD_CLOSE_DOOR -> {
                        val doorCloseModel = Gson().fromJson(json, DoorCloseBean::class.java)
                        cabinetVM.startLockerClose(doorCloseModel)
                    }

                    CmdValue.CMD_PHONE_NUMBER_LOGIN -> {
                        val doorOpenModel = Gson().fromJson(json, DoorOpenBean::class.java)
                        //处理处理两个格口的问题
                        val mobileDoorGeX = SPreUtil[AppUtils.getContext(), SPreUtil.mobileDoorGeX, 1]
                        val cabinId = when (mobileDoorGeX) {
                            1 -> {
                                cabinetVM.cur1Cabinld
                            }

                            2 -> {
                                cabinetVM.cur2Cabinld
                            }

                            else -> "-1"
                        }
                        if (doorOpenModel.retCode == 0) {
                            if (cabinId != "-1") {
                                cabinetVM.toGoMobileOpen(
                                    cabinId.toString(), doorOpenModel.userId ?: "-1"
                                )
                            }
                        } else {
                            cabinetVM.tipMessage("${doorOpenModel.msg}")
                        }


                    }

                    CmdValue.CMD_PHONE_USER_OPEN_DOOR -> {
                        val doorOpenModel = Gson().fromJson(json, DoorOpenBean::class.java)
                        if (doorOpenModel.retCode != 0) {
                            cabinetVM.tipMessage("${doorOpenModel.msg}")
                        }
                    }

                    CmdValue.CMD_RESTART -> {
                        val restartModel = Gson().fromJson(json, RestartBean::class.java)
                        when (restartModel.type) {
                            1 -> {
                                TaskRestartScheduler.triggerImmediately(
                                    AppUtils.getContext(), "restart"
                                )
                            }

                            2 -> {
                                restartModel.time?.let { time ->
                                    TaskRestartScheduler.scheduleTodayTimeRange(
                                        AppUtils.getContext(), time, time, "morning_cleanup_forced", executeIfMissed = true
                                    )
                                }

                            }
                        }
                    }

                    CmdValue.CMD_UPLOAD_LOG -> {
                        cabinetVM.toGoCmdUpLog()
                    }

                    CmdValue.CMD_DEBUG -> {
                        if (BuildConfig.DEBUG) {

                            val args: Bundle = Bundle().apply {
                                putInt(
                                    NavDeBugTypeSelfFragment.IS_INDEX, NavDeBugTypeSelfFragment.IS_LEFT
                                )
                                putBoolean(NavDeBugTypeSelfFragment.IS_SHOW, true)
                            }
                            Navigation.findNavController(
                                this@NavTouSingleNewActivity, R.id.nav_host_fragment_single
                            ).navigate(R.id.action_start_debug_type_self, args)

                        } else {
                            val args: Bundle = Bundle().apply {
                                putInt(
                                    NavDeBugTypeFragment.IS_INDEX, NavDeBugTypeFragment.IS_LEFT
                                )
                                putBoolean(NavDeBugTypeFragment.IS_SHOW, true)
                            }
                            Navigation.findNavController(
                                this@NavTouSingleNewActivity, R.id.nav_host_fragment_single
                            ).navigate(R.id.action_start_debug_type, args)
                        }
                    }

                    CmdValue.CMD_OTA -> {
                        val otaModel = Gson().fromJson(json, OtaBean::class.java)
//                        cabinetVM.startDowChip(otaModel)
                    }

                    CmdValue.CMD_OTA_APK -> {
                        val otaModel = Gson().fromJson(json, OtaBean::class.java)
                        cabinetVM.startDowApk(otaModel)
                    }

                    CmdValue.CMD_ADMIN_PHOTO -> {
                        val otaModel = Gson().fromJson(json, PhotoBean::class.java)
                        cabinetVM.takePhotoRemote(otaModel)
                    }

                    CmdValue.CMD_ADMIN_OVERFLOW -> {
                        val adminOverflowModel = Gson().fromJson(json, AdminOverflowBean::class.java)
                        cabinetVM.toGoAdminOverflow(adminOverflowModel)
                    }

                    CmdValue.CMD_PERIPHERAL_STATUS -> {

                    }

                }
            }
        }
    }

    private fun refreshHomeRes() {
        Loge.e("测试我来了 刷新背景图 refreshHomeRes ${cabinetVM.mHomeBg}")
        cabinetVM.mHomeBg?.let { bitmap ->
            binding.acivHomeNet.setImageBitmap(bitmap)
        }.also {
            if (cabinetVM.mHomeBg == null) {
                val resBitmap = BitmapFactory.decodeResource(AppUtils.getContext().resources, R.drawable.home)
                binding.acivHomeNet.setImageBitmap(resBitmap)
            }
        }
    }

    private fun socketToast(isShow: Boolean) {
        cabinetVM.mainScope.launch {
            cabinetVM.isDistClient = isShow
            binding.acivStatusSocket.isVisible = isShow
        }
    }

    private fun installApk(file: File) {
        try {
            // 触发安装
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    FileProvider.getUriForFile(
                        baseContext, "${baseContext.packageName}.fileProvider", file
                    )
                } else {
                    Uri.fromFile(file)
                }
                setDataAndType(uri, "application/vnd.android.package-archive")
            }
            cabinetVM.cancelJobInstall()
            startActivity(intent)
        } catch (e: Throwable) {
            e.printStackTrace()
            SnackbarUtils.show(
                activity = this@NavTouSingleNewActivity, message = "安装失败", duration = Snackbar.LENGTH_LONG, textColor = Color.WHITE, textAlignment = View.TEXT_ALIGNMENT_CENTER, horizontalCenter = true, position = SnackbarUtils.Position.CENTER
            )
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Loge.e("流程 home onDestroy")
        networkStateManager.stopMonitoring()
        cabinetVM.cameraManagerNew.unregisterUsbReceiver()
        cabinetVM.stopAll()
        cabinetVM.cancelServiceClose()
        cabinetVM.cancelContainersStatusJob()
        cabinetVM.cancelJobAgain()

    }

    override fun onDestroy() {
        super.onDestroy()
        Loge.e("流程 home onDestroy")
        networkStateManager.stopMonitoring()
        cabinetVM.cameraManagerNew.unregisterUsbReceiver()
        cabinetVM.stopAll()
        cabinetVM.cancelServiceClose()
        cabinetVM.cancelContainersStatusJob()
        cabinetVM.cancelJobAgain()
    }

    private val cameraErrorListener = CameraErrorListener { status, index, text ->
        if (status) {
            if ("0" == index) {
                cabinetVM.maptDoorFault[FaultType.FAULT_CODE_51] = false
            }
            if ("1" == index) {
                cabinetVM.maptDoorFault[FaultType.FAULT_CODE_52] = false
            }

        } else {
            cabinetVM.insertInfoLog(LogEntity().apply {
                msg = text
                time = AppUtils.getDateYMDHMS()
            })
            when (index) {
                "0" -> {
                    cabinetVM.maptDoorFault[FaultType.FAULT_CODE_51] = true
                }

                "1" -> {
                    cabinetVM.maptDoorFault[FaultType.FAULT_CODE_52] = true
                }
            }
        }


    }


    fun latestBusinessStatus() {
        cabinetVM.cameraManagerNew.registerUsbReceiver()/*      lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SerialPortSdk.flowBusinessSetup.collect {
                    val cmdText = CmdEnumText.fromCmdText(it.cmdByte)
                    BoxToolLogUtils.savePrintln("业务流：返回的指令 -> $cmdText | ${it.cmdStatus} | $it")
                    when (it.cmdByte) {
                        SerialPortSdk.CMD0 -> {}
                        SerialPortSdk.CMD1 -> {}
                        SerialPortSdk.CMD2 -> {}
                        SerialPortSdk.CMD3 -> {}
                        SerialPortSdk.CMD4 -> {}
                        SerialPortSdk.CMD5 -> {}
                        SerialPortSdk.CMD6 -> {}
                        SerialPortSdk.CMD7 -> {}
                        SerialPortSdk.CMD8 -> {}
                        SerialPortSdk.CMD9 -> {}
                        SerialPortSdk.CMD10 -> {}
                        SerialPortSdk.CMD11 -> {}
                        SerialPortSdk.CMD16 -> {}
                        SerialPortSdk.CMD17 -> {}
                        SerialPortSdk.CMD18 -> {}
                        SerialPortSdk.CMD19 -> {}
                    }

                }
            }
        }*/
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.chipStep.collect {
                    Loge.i("升级流程：返回的指令 -> $it")
                    when (it) {
                        CabinetVM.UpgradeStep.IDLE -> {}

                        CabinetVM.UpgradeStep.INSTALL_DOW -> {
                            binding.clPrompt.isVisible = true
                            binding.actvPrompt.text = "正在升级应用，耐心等待，请勿进行其他操作。"
                            cabinetVM.insertInfoLog(LogEntity().apply {
                                cmd = CmdValue.CMD_OTA_APK
                                msg = "下载-$it"
                                time = AppUtils.getDateYMDHMS()
                            })
                        }

                        CabinetVM.UpgradeStep.INSTALL_APK -> {
                            val file = cabinetVM.installApkUrl
                            installApk(File(file))
                        }

                        CabinetVM.UpgradeStep.INSTALL_SAME, CabinetVM.UpgradeStep.INSTALL_FUALT -> {
                            binding.clPrompt.isVisible = false
                            cabinetVM.insertInfoLog(LogEntity().apply {
                                cmd = CmdValue.CMD_OTA_APK
                                msg = "安装失败-$it"
                                time = AppUtils.getDateYMDHMS()
                            })
                        }

                        CabinetVM.UpgradeStep.UPGRADE_DOW -> {
                            binding.clPrompt.isVisible = true
                            binding.actvPrompt.text = "正在升级芯片固件中，耐心等待，请勿进行其他操作。"
                            cabinetVM.insertInfoLog(LogEntity().apply {
                                cmd = CmdValue.CMD_OTA
                                msg = "下载-$it"
                                time = AppUtils.getDateYMDHMS()
                            })
                        }

                        CabinetVM.UpgradeStep.UPGRADE_FUALT, CabinetVM.UpgradeStep.QUERY_VERSION_FUALT, CabinetVM.UpgradeStep.ENTER_STATUS_FUALT, CabinetVM.UpgradeStep.QUERY_STATUS_FUALT, CabinetVM.UpgradeStep.SEND_FILE_FUALT, CabinetVM.UpgradeStep.SEND_FILE_END_FUALT, CabinetVM.UpgradeStep.RESTART_APP_FUALT -> {
                            binding.clPrompt.isVisible = false
                            Loge.d("流程 芯片升级 接收指令${it} 没来回调")
                            cabinetVM.insertInfoLog(LogEntity().apply {
                                cmd = CmdValue.CMD_OTA
                                msg = "升级失败-$it"
                                time = AppUtils.getDateYMDHMS()
                            })
                        }

                        CabinetVM.UpgradeStep.QUERY_VERSION, CabinetVM.UpgradeStep.ENTER_STATUS, CabinetVM.UpgradeStep.QUERY_STATUS, CabinetVM.UpgradeStep.SEND_FILE, CabinetVM.UpgradeStep.SEND_FILE_END, CabinetVM.UpgradeStep.RESTART_APP -> {
                            cabinetVM.insertInfoLog(LogEntity().apply {
                                cmd = CmdValue.CMD_OTA
                                msg = "升级进行中-$it"
                                time = AppUtils.getDateYMDHMS()
                            })
                            if (it == CabinetVM.UpgradeStep.RESTART_APP) {
                                delay(3000)
                                OSUtils.restartAppFrontDesk(this@NavTouSingleNewActivity)
                            }
                        }

                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.currentStep.collect {
                    BoxToolLogUtils.savePrintln("业务流：当前步骤 -> $it")
                    when (it) {
                        CabinetVM.LockerStep.IDLE -> {}
                        CabinetVM.LockerStep.START -> {
                            cabinetVM.setFlowUiCloseStep(CabinetVM.UiCloseStep.CLOSE_MOBILE)
                            BoxToolLogUtils.savePrintln("业务流：开启相机拍照")
                            cabinetVM.cameraManagerNew.autoStartUsbCameras(true, binding.textureIn!!, binding.textureOut!!, delayMs = 3000, listener = cameraErrorListener)
                        }

                        CabinetVM.LockerStep.OPENING -> {
                            BoxToolLogUtils.savePrintln("业务流：插入数据")
                            val openType = cabinetVM.remoteOpenType
                            if (openType == 1) {
                                cabinetVM.playVoice(1)
                            }
                        }


                        CabinetVM.LockerStep.WAITING_OPEN_DOOR -> {
                            Navigation.findNavController(
                                this@NavTouSingleNewActivity, R.id.nav_host_fragment_single
                            ).navigate(R.id.action_start_delivery)
                            cabinetVM.takePhoto(1)
                        }

                        CabinetVM.LockerStep.WAITING_OPEN_CLEAR -> {
                            Navigation.findNavController(
                                this@NavTouSingleNewActivity, R.id.nav_host_fragment_single
                            ).navigate(R.id.action_start_clear_door)
                            cabinetVM.takePhoto(1)
                        }

                        CabinetVM.LockerStep.WEIGHT_TRACKING -> {
                            BoxToolLogUtils.savePrintln("业务流：持续获取重量中")
                        }

                        CabinetVM.LockerStep.CLICK_CLOSE -> {
                            BoxToolLogUtils.savePrintln("业务流：点击关闭")
                            val openType = cabinetVM.remoteOpenType
                            if (openType == 1) {
                                cabinetVM.playVoice(0)
                            }
                        }

                        CabinetVM.LockerStep.CLOSING -> {
                            BoxToolLogUtils.savePrintln("业务流：检测关闭中")

                        }

                        CabinetVM.LockerStep.CLOSE -> {
                            val openType = cabinetVM.remoteOpenType
                            cabinetVM.takePhoto(0)
                            cabinetVM.startLockerEndWeight(
                                cabinetVM.doorGeX, cabinetVM.curG1Weight ?: "0.00"
                            )
                            if (openType == 1) {
                                cabinetVM.setFlowUiCloseStep(CabinetVM.UiCloseStep.CLOSE_DELIVERY)
                            }
                            if (openType == 2) {
                                cabinetVM.setFlowUiCloseStep(CabinetVM.UiCloseStep.CLOSE_CLEAR_DOOR)
                            }
                        }

                        CabinetVM.LockerStep.WAITING_CLOSE, CabinetVM.LockerStep.FINISHED -> {
                            BoxToolLogUtils.savePrintln("业务流：上报关闭")
                            val openType = cabinetVM.remoteOpenType
                            if (openType == 1) {
                                cabinetVM.setFlowUiCloseStep(CabinetVM.UiCloseStep.CLOSE_DELIVERY)
                            }
                            if (openType == 2) {
                                cabinetVM.setFlowUiCloseStep(CabinetVM.UiCloseStep.CLOSE_CLEAR_DOOR)
                            }
                            if (it == CabinetVM.LockerStep.FINISHED) {
                                cabinetVM.deteServiceClose()
                            }
                        }

                        CabinetVM.LockerStep.CAMERA_END -> {
                            cabinetVM.cameraManagerNew.destroy()
                        }
                    }
                }
            }
        }
    }

    fun generateRandomNumberString(length: Int): String {
        val digits = ('0'..'9').joinToString("")
        return (1..length).map { digits.random() }.joinToString("")
    }
}