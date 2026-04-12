package com.recycling.toolsapp.nav

import android.content.Context
import android.content.Context.TELEPHONY_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.text.Html
import android.text.InputFilter
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import com.recycling.toolsapp.R
import com.recycling.toolsapp.databinding.NavFragmentDebugTypeSelfBinding
import com.recycling.toolsapp.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapp.utils.EntityType
import com.recycling.toolsapp.utils.MediaPlayerHelper
import com.recycling.toolsapp.utils.ResultType
import com.recycling.toolsapp.utils.TestByteData
import com.recycling.toolsapp.vm.CabinetVM
import com.serial.port.EnumCabState
import com.serial.port.utils.AppUtils
import com.serial.port.utils.ByteUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.Loge
import com.serial.port.utils.SendByteData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nearby.lib.netwrok.response.SPreUtil
import java.io.ByteArrayOutputStream
import java.util.regex.Pattern


/***
 * 校准状态 需要进行校准
 */
@AndroidEntryPoint
class NavDeBugTypeSelfFragment : BaseBindLazyTimeFragment<NavFragmentDebugTypeSelfBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })

    companion object {
        /***
         * is_index 0.左  1.右
         */
        const val IS_INDEX = "is_index"
        const val IS_LEFT = 0
        const val IS_RIGHT = 1
        const val IS_SHOW = "is_show"
    }

    fun getIMEI(context: Context): String? {
        val telephonyManager = context.getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 在 Android 8.0 及以上使用 getImei 方法
            try {
                // 获取默认IMEI（通常对应卡槽1）
                return telephonyManager.imei
            } catch (e: SecurityException) {
                e.printStackTrace()
                return null
            }
        } else {
            // 在 Android 8.0 以下使用 getDeviceId 方法
            try {
                return telephonyManager.deviceId
            } catch (e: SecurityException) {
                e.printStackTrace()
                return null
            }
        }
    }

    /***
     * is_index 0.左  1.右
     */
    private var isIndex: Int = -1
    private var isShow: Boolean = false

    //是否点击切换状态
    var isCheckSwitch = false

    //当前格口
    var currentGe = CmdCode.GE1

    /**
     * 1.推杆关 2.内灯开 3.外灯关 4.电磁锁关
     */
    var mRbMap1 = mutableMapOf(1 to false, 2 to true, 3 to false, 4 to false)
    var mRodHinderValue1: Int = EntityType.ROD_HINDER_MIN1

    /**
     * 1.推杆关 2.内灯开 3.外灯关 4.电磁锁关
     */
    var mRbMap2 = mutableMapOf(1 to false, 2 to true, 3 to false, 4 to false)
    var mRodHinderValue2: Int = EntityType.ROD_HINDER_MIN1

    var weightKg = -1

    override fun isAutoCloseEnabled(): Boolean = false

    override fun getDisplayDuration(): Long = ResultType.DEBUGGING_SECONDS2

    // 创建任务队列
    override fun layoutRes(): Int {
        return R.layout.nav_fragment_debug_type_self
    }

    /**
     * 为 AppCompatEditText 设置数字范围限制
     */
    fun AppCompatEditText.setupNumberRange(
        minValue: Int = EntityType.ROD_HINDER_MIN,
        maxValue: Int = EntityType.ROD_HINDER_MAX,
        onValueChange: ((Int) -> Unit)? = null,
    ) {

        // 设置只能输入数字
        val digitsFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val pattern = Pattern.compile("[0-9]*")
            if (pattern.matcher(source).matches()) {
                null
            } else {
                ""
            }
        }
        filters = arrayOf(digitsFilter)
        inputType = android.text.InputType.TYPE_CLASS_NUMBER

        // 设置焦点监听
//        setOnFocusChangeListener { _, hasFocus ->
//            if (!hasFocus) {
//                handleNumberRange(this, minValue, maxValue, onValueChange)
//            }
//        }
        setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                v.clearFocus()  // 主动清除焦点
                (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                    v.windowToken, 0
                )
                handleNumberRange(this, minValue, maxValue, onValueChange)
                true
            } else false
        }
    }

    private fun handleNumberRange(
        editText: AppCompatEditText,
        minValue: Int,
        maxValue: Int,
        onValueChange: ((Int) -> Unit)?,
    ) {
        val text = editText.text?.toString()?.trim()

        if (text.isNullOrEmpty()) {
            setEditTextValue(editText, minValue)
            onValueChange?.invoke(minValue)
            return
        }

        try {
            var value = text.toInt()

            when {
                value < minValue -> {
                    setEditTextValue(editText, EntityType.ROD_HINDER_MIN)
                    value = EntityType.ROD_HINDER_MIN
                }

                value > maxValue -> {
                    setEditTextValue(editText, maxValue)
                    value = maxValue
                }

                else -> {
                    setEditTextValue(editText, value)
                }
            }

            onValueChange?.invoke(value)

        } catch (e: NumberFormatException) {
            setEditTextValue(editText, minValue)
            onValueChange?.invoke(minValue)
        }
    }

    private fun setEditTextValue(editText: AppCompatEditText, value: Int) {
        val valueText = value.toString()
        editText.setText(valueText)
        editText.setSelection(valueText.length)
    }

    private fun toGoRodHinderValue(value: Int) {
        cabinetVM.startRodHinder(currentGe, value, rodHinderResult = { lockerNo, rodHinderValue ->
            when (lockerNo) {
                1 -> {
                    mRodHinderValue1 = rodHinderValue
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.rodHinderValue1, mRodHinderValue1)
                }

                2 -> {
                    mRodHinderValue2 = rodHinderValue
                    SPreUtil.put(AppUtils.getContext(), SPreUtil.rodHinderValue2, mRodHinderValue2)
                }
            }
        })
    }

    fun switchData() {
        Loge.e("流程 switchData 设置扭动门阻力值 $currentGe 刷新控件状态")
        when (currentGe) {
            1 -> {
                //推杆
                if (mRbMap1[1] == true) {
                    binding.mrbPushOpen.isChecked = true
                } else {
                    binding.mrbPushClose.isChecked = true
                }
                //内灯
                if (mRbMap1[2] == true) {
                    binding.mrbInOpen.isChecked = true
                } else {
                    binding.mrbInClose.isChecked = true
                }
                //外灯
                if (mRbMap1[3] == true) {
                    binding.mrbOutOpen.isChecked = true
                } else {
                    binding.mrbOutClose.isChecked = true
                }
                //电磁锁
                if (mRbMap1[4] == true) {
                    binding.mrbLockOpen.isChecked = true
                } else {
                    binding.mrbLockClose.isChecked = true
                }
            }

            2 -> {
                //推杆
                if (mRbMap2[1] == true) {
                    binding.mrbPushOpen.isChecked = true
                } else {
                    binding.mrbPushClose.isChecked = true

                }
                //内灯
                if (mRbMap2[2] == true) {
                    binding.mrbInOpen.isChecked = true
                } else {
                    binding.mrbInClose.isChecked = true
                }
                //外灯
                if (mRbMap2[3] == true) {
                    binding.mrbOutOpen.isChecked = true
                } else {
                    binding.mrbOutClose.isChecked = true
                }
                //电磁锁
                if (mRbMap2[4] == true) {
                    binding.mrbLockOpen.isChecked = true
                } else {
                    binding.mrbLockClose.isChecked = true
                }
            }
        }
        isCheckSwitch = false
    }

    //刷新控件值
    fun refreshMapValue(key: Int, value: Boolean) {
        Loge.e("调流程 设置扭动门阻力值 格口：$currentGe key = $key | value = $value")
        when (currentGe) {
            1 -> {
                mRbMap1[key] = value
            }

            2 -> {
                mRbMap2[key] = value
            }
        }
    }


    fun initDoorStatus() {
        Loge.e("流程 初始化设置")
        cabinetVM.startDoorStratus(currentGe, onGetDoorStatus = { status ->
            isCheckSwitch = true
            Loge.e("流程 初始化设置 回调 $status")
            if (status == 0) {
                binding.mrbPushOpen.isChecked = false
                binding.mrbPushClose.isChecked = true
                refreshMapValue(1, false)
            } else if (status == 1) {
                binding.mrbPushOpen.isChecked = true
                binding.mrbPushClose.isChecked = false
                refreshMapValue(1, true)
            }
            isCheckSwitch = false
        })
    }

    private fun toGoHome() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            setPackage("com.android.launcher3")
            addCategory(Intent.CATEGORY_HOME)
        }
        startActivity(intent)
    }

    fun gotoGetInfo() {
        cabinetVM.isLookState = true
        cabinetVM.startQueryStatus(stateResult = { lowerMachines ->
            val builder1 = StringBuilder()
            val builder2 = StringBuilder()
            Loge.e("流程 testState  ${lowerMachines.size}")
            lowerMachines.withIndex().forEach { (index, lower) ->
                Loge.e("流程 testState index $index $lower")
                when (index) {
                    0 -> {
                        builder1.append("格口一").append("<br>")
                        builder1.append("烟雾警报：${EnumCabState.getDescByCode("1${lower.smokeValue}")}<br>")
                        builder1.append("红外警报：${EnumCabState.getDescByCode("2${lower.irStateValue}")}<br>")
//                        builder1.append("投门传感：${EnumCabState.getDescByCode("3${lower.touGMStatus}")}<br>")
                        builder1.append("夹手传感：${EnumCabState.getDescByCode("4${lower.touJSStatusValue}")}<br>")
                        builder1.append("投门状态：${EnumCabState.getDescByCode("5${lower.doorStatusValue}")}<br>")
                        builder1.append("清运状态：${EnumCabState.getDescByCode("6${lower.lockStatusValue}")}<br>")
                        builder1.append("校准状态：${EnumCabState.getDescByCode("7${lower.xzStatusValue}")}<br>")
//                        builder1.append("夹手状态：${EnumCabState.getDescByCode("8${lower.jsStatus}")}<br>")
                        builder1.append("当前重量：${lower.weigh}kg<br>")
                        builder1.append("<br>")
                    }

                    1 -> {
                        builder2.append("格口二").append("<br>")
                        builder2.append("烟雾警报：${EnumCabState.getDescByCode("1${lower.smokeValue}")}<br>")
                        builder2.append("红外警报：${EnumCabState.getDescByCode("2${lower.irStateValue}")}<br>")
//                        builder2.append("投门传感：${EnumCabState.getDescByCode("3${lower.touGMStatus}")}<br>")
                        builder2.append("夹手传感：${EnumCabState.getDescByCode("4${lower.touJSStatusValue}")}<br>")
                        builder2.append("投门状态：${EnumCabState.getDescByCode("5${lower.doorStatusValue}")}<br>")
                        builder2.append("清运状态：${EnumCabState.getDescByCode("6${lower.lockStatusValue}")}<br>")
                        builder2.append("校准状态：${EnumCabState.getDescByCode("7${lower.xzStatusValue}")}<br>")
//                        builder2.append("夹手状态：${EnumCabState.getDescByCode("8${lower.jsStatus}")}<br>")
                        builder2.append("当前重量：${lower.weigh}kg<br>")
                        builder2.append("<br>")
                    }
                }
            }
            binding.actvCabinetState1.text = Html.fromHtml(builder1.toString(), Html.FROM_HTML_MODE_LEGACY)
            binding.actvCabinetState2.text = Html.fromHtml(builder2.toString(), Html.FROM_HTML_MODE_LEGACY)
        })
    }

    // 为View添加防抖点击扩展函数
    fun View.setDebouncedClickListener(
        debounceTime: Long = 3000L,
        onClick: (View) -> Unit,
    ) {
        var lastClickTime = 0L

        setOnClickListener { view ->
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime >= debounceTime) {
                lastClickTime = currentTime
                onClick(view)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    override fun initialize(savedInstanceState: Bundle?) {
        val rodHinderValue1 = SPreUtil[AppUtils.getContext(), SPreUtil.rodHinderValue1, mRodHinderValue1] as Int
        val rodHinderValue2 = SPreUtil[AppUtils.getContext(), SPreUtil.rodHinderValue2, mRodHinderValue2] as Int
        val typeGrid = SPreUtil[AppUtils.getContext(), SPreUtil.type_grid, -1]
        arguments?.let { args ->
            isIndex = args.getInt(IS_INDEX, -1)
            isShow = args.getBoolean(IS_SHOW, false)
            when (isIndex) {
                0 -> {
                    binding.actvLeft.isSelected = true
                    binding.actvRight.isSelected = false
                    currentGe = CmdCode.GE1
                    binding.actvLookWeigh.text = "查看投口一重量"
                    binding.acetBoost.setText("$rodHinderValue1")

                }

                1 -> {
                    binding.actvLeft.isSelected = false
                    binding.actvRight.isSelected = true
                    currentGe = CmdCode.GE2
                    binding.actvLookWeigh.text = "查看投口二重量"
                    binding.acetBoost.setText("$rodHinderValue2")

                }
            }
        }

        if (typeGrid == 3) {
            binding.acetBoost.setText("240")
        } else if (typeGrid == 1) {
            binding.acetBoost.setText("200")
        } else if (typeGrid == 2) {
            if (currentGe == 1 || isIndex == 0) {
                binding.acetBoost.setText("200")
            } else if (currentGe == 2 || isIndex == 1) {
                binding.acetBoost.setText("200")
            }
        }
        if (isShow) {
            binding.clContent1.isVisible = true
            binding.clBooster.isVisible = true
            initDoorStatus()
        }

        binding.actvLeft.setOnClickListener {
            currentGe = CmdCode.GE1
            binding.actvLeft.isSelected = true
            binding.actvRight.isSelected = false
            binding.actvLookWeigh.text = "查看投口一重量"
            isCheckSwitch = true
            switchData()
        }

        binding.actvRight.setOnClickListener {
            val geType = cabinetVM.doorGeXType
            if (geType == CmdCode.GE2) {
                currentGe = CmdCode.GE2
                binding.actvLeft.isSelected = false
                binding.actvRight.isSelected = true
                binding.actvLookWeigh.text = "查看投口二重量"
                isCheckSwitch = true
                switchData()
            }
        }

        //减阻力阈值
        binding.del.setDebouncedClickListener {
            val number = binding.acetBoost.text.toString().toInt()
            if (number == EntityType.ROD_HINDER_MIN) return@setDebouncedClickListener
            binding.acetBoost.setText("${(number - 1)}")
            binding.acetBoost.setSelection(binding.acetBoost.length())
            val value = binding.acetBoost.text.toString().toInt()
            if (number > EntityType.ROD_HINDER_MIN) {
                toGoRodHinderValue(value)
            }
        }
        //加阻力阈值
        binding.add.setDebouncedClickListener {
            val number = binding.acetBoost.text.toString().toInt()
            if (number == EntityType.ROD_HINDER_MAX) return@setDebouncedClickListener
            binding.acetBoost.setText("${(number + 1)}")
            binding.acetBoost.setSelection(binding.acetBoost.length())
            val value = binding.acetBoost.text.toString().toInt()
            if (number < EntityType.ROD_HINDER_MAX) {
                toGoRodHinderValue(value)
            }
        }

        binding.acetBoost.setupNumberRange(
            minValue = EntityType.ROD_HINDER_MIN, maxValue = EntityType.ROD_HINDER_MAX
        ) { value ->
            binding.acetBoost.setText("$value")
            Loge.d("焦点消失后的值1: $value")
            // 处理数值
            toGoRodHinderValue(value)
        }

        binding.actvGetStateClose.setOnClickListener {
            cabinetVM.isLookState = false
            binding.clZhuti.isVisible = false
            cabinetVM.cancelStartQueryStatus()
        }
        binding.actvGetStateDesktop.setOnClickListener {
            toGoHome()
        }
        binding.actvGetState.setOnClickListener {
            binding.clZhuti.isVisible = true
            Loge.e("设备信息获取 ${getIMEI(AppUtils.getContext())}")
//            testStickyBag()
//            MediaPlayerHelper.setVolume(AppUtils.getContext(), 4)
//            MediaPlayerHelper.playAudioAsset(AppUtils.getContext(), "opendoor.wav")
            gotoGetInfo()
        }
        binding.actvLookWeigh.setOnClickListener {
            cabinetVM.startQueryWeight(currentGe)
        }
        //返回
        binding.actvReturn.setOnClickListener {
            super.performCloseAction()
        }

        //内摄像头
        binding.actvInCamera.setOnClickListener {
            Navigation.findNavController(it).navigate(R.id.action_start_camera_in)
        }

        //外摄像头
        binding.actvOutCamera.setOnClickListener {
            Navigation.findNavController(it).navigate(R.id.action_start_camera_out)
        }
        //推杆开
        binding.mrbPushOpen.setOnClickListener {
            Loge.e("流程 mrbPushOpen $isCheckSwitch")
            if (isCheckSwitch) return@setOnClickListener
            MediaPlayerHelper.playAudioAsset(AppUtils.getContext(), "opendoor.wav")
            val doorGeX = if (currentGe == CmdCode.GE1) CmdCode.GE11 else CmdCode.GE21
            cabinetVM.startTurnDoor(doorGeX)
            binding.mrbPushOpen.isChecked = true
            binding.mrbPushClose.isChecked = false
            refreshMapValue(1, true)
        }
        //推杆关
        binding.mrbPushClose.setOnClickListener {
            Loge.e("流程 mrbPushClose $isCheckSwitch")
            if (isCheckSwitch) return@setOnClickListener
            MediaPlayerHelper.playAudioAsset(AppUtils.getContext(), "opendoor.wav")
            val doorGeX = if (currentGe == CmdCode.GE1) CmdCode.GE10 else CmdCode.GE20
            cabinetVM.startTurnDoor(doorGeX)
            binding.mrbPushOpen.isChecked = false
            binding.mrbPushClose.isChecked = true
            refreshMapValue(1, false)
        }
        //内灯
        binding.rgIn.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.mrb_in_open -> "开"
                R.id.mrb_in_close -> "关"
                else -> null
            }
            if (isCheckSwitch) return@setOnCheckedChangeListener
            when (selected) {
                "开" -> {
                    cabinetVM.startLights(CmdCode.IN_LIGHTS_OPEN)
                    refreshMapValue(2, true)
                }

                "关" -> {
                    cabinetVM.startLights(CmdCode.IN_LIGHTS_CLOSE)
                    refreshMapValue(2, false)
                }
            }
        }
        //外灯
        binding.rgOut.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.mrb_out_open -> "开"
                R.id.mrb_out_close -> "关"
                else -> null
            }
            if (isCheckSwitch) return@setOnCheckedChangeListener
            when (selected) {
                "开" -> {
                    cabinetVM.startLights(CmdCode.OUT_LIGHTS_OPEN)
                    refreshMapValue(3, true)
                }

                "关" -> {
                    cabinetVM.startLights(CmdCode.OUT_LIGHTS_CLOSE)
                    refreshMapValue(3, false)

                }
            }
        }
        //电磁锁
        binding.rgLock.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.mrb_lock_open -> "开"
                R.id.mrb_lock_close -> "关"
                else -> null
            }
            if (isCheckSwitch) return@setOnCheckedChangeListener
            when (selected) {
                "开" -> {
                    val code = when (currentGe) {
                        CmdCode.GE1 -> {
                            CmdCode.CLEAR_OPEN_1_1
                        }

                        CmdCode.GE2 -> {
                            CmdCode.CLEAR_OPEN_2_1
                        }

                        else -> {
                            CmdCode.CLEAR_OPEN_1_1
                        }
                    }
                    cabinetVM.startClearDoor(code)
                    refreshMapValue(3, true)
                }

                "关" -> {

                }
            }
        }

        //去零清皮
        binding.actvClearPeel.setOnClickListener {
            cabinetVM.startCalibrationQP(currentGe, CmdCode.CALIBRATION_0)
        }
        //称重
        binding.actvWeighing.setOnClickListener {
            //校准前发送的指令
//            setRbEnabled(false)
            setRbEnabled2(true, false)
            binding.actvWeighing.isEnabled = false
            binding.clWeight.isVisible = true
            binding.actvPrompt.text = "正在进行称重校准前处理中，请勿操作其他"
            cabinetVM.startCalibration(currentGe, CmdCode.CALIBRATION_1)

        }
        setRbEnabled2(true, false)
        //校准
        binding.rgKg.setOnCheckedChangeListener { _, checkedId ->
            val selected = when (checkedId) {
                R.id.mrb_kg1 -> getString(R.string.kg_1)
                R.id.mrb_kg2 -> getString(R.string.kg_2)
                R.id.mrb_kg3 -> getString(R.string.kg_3)
                R.id.mrb_kg4 -> getString(R.string.kg_4)
                else -> null
            }
            if (weightKg == -1) {
                cabinetVM.tipMessage("请先点击称重校准")
//                setRbEnabled(false)
                setRbEnabled2(false, false)
                return@setOnCheckedChangeListener
            }
            //校准kg禁止点击称重校准
            binding.actvWeighing.isEnabled = false
            binding.clWeight.isVisible = true
            when (selected) {
                getString(R.string.kg_1) -> {
                    if (weightKg == 2 || weightKg == 3 || weightKg == 4) {
                        cabinetVM.tipMessage("正在进行${getString(R.string.kg_1)}，请勿操作其他")
                        return@setOnCheckedChangeListener
                    }
                    binding.actvPrompt.text = "正在进行${getString(R.string.kg_1)}，请勿操作其他"
                    weightKg = 1
                    cabinetVM.startCalibration(currentGe, CmdCode.CALIBRATION_2)
                }

                getString(R.string.kg_2) -> {
                    if (weightKg == 1 || weightKg == 3 || weightKg == 4) {
                        cabinetVM.tipMessage("正在进行${getString(R.string.kg_2)}，请勿操作其他")
                        return@setOnCheckedChangeListener
                    }
                    binding.actvPrompt.text = "正在进行${getString(R.string.kg_2)}，请勿操作其他"
                    weightKg = 2
                    cabinetVM.startCalibration(currentGe, CmdCode.CALIBRATION_3)
                }

                getString(R.string.kg_3) -> {
                    if (weightKg == 1 || weightKg == 2 || weightKg == 4) {
                        cabinetVM.tipMessage("正在进行${getString(R.string.kg_3)}，请勿操作其他")
                        return@setOnCheckedChangeListener
                    }
                    binding.actvPrompt.text = "正在进行${getString(R.string.kg_3)}，请勿操作其他"
                    weightKg = 3
                    cabinetVM.startCalibration(currentGe, CmdCode.CALIBRATION_4)
                }

                getString(R.string.kg_4) -> {
                    if (weightKg == 1 || weightKg == 2 || weightKg == 3) {
                        cabinetVM.tipMessage("正在进行${getString(R.string.kg_4)}，请勿操作其他")
                        return@setOnCheckedChangeListener
                    }
                    binding.actvPrompt.text = "正在进行${getString(R.string.kg_4)}，请勿操作其他"
                    weightKg = 4
                    cabinetVM.startCalibration(currentGe, CmdCode.CALIBRATION_5)
                }

            }
        }

        //清运锁状态
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.getTestClearDoor.collect { result ->
                    if (result == -1) return@collect
                    if (result == 1) {
                        binding.mrbLockOpen.isChecked = true
                        binding.mrbLockClose.isChecked = false
                        cabinetVM.tipMessage("清运开门成功")
                    } else {
                        binding.mrbLockOpen.isChecked = false
                        binding.mrbLockClose.isChecked = true
                        cabinetVM.tipMessage("清运开门失败")
                    }
                }
            }
        }

        //称重前校准操作
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.getCaliBefore2.collect { result ->
                    if (result == -1) return@collect
                    binding.clWeight.isVisible = false
                    //校准完成复原点击按钮
                    binding.actvWeighing.isEnabled = true
                    if (result == 1) {
                        cabinetVM.tipMessage("校准前处理已完成，放入砝码，请选择校准类型")
//                    setRbEnabled(true)
                        setRbEnabled2(false, false)
                        weightKg = 0
                    } else {
                        cabinetVM.tipMessage("校准前处理未完成，请重新点击称重校准")
//                    setRbEnabled(false)
                        setRbEnabled2(false, false)
                        weightKg = -1
                    }
                }

            }
        }

        //称重效验结果
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.getCaliResult.collect { result ->
                    if (result == -1) return@collect
                    if (result == 1) {
                        cabinetVM.tipMessage("校准完成")
                        setRbEnabled2(false, true)
                    } else {
                        cabinetVM.tipMessage("校准失败")
                        setRbEnabled2(false, false)
                    }
                    //校准完成复原点击按钮
                    binding.actvWeighing.isEnabled = true
                    binding.clWeight.isVisible = false
                    weightKg = -1
                }
            }
        }
    }

    /********************************************测试粘包问题***************************************************/
    var click = 1
    fun testStickyBag() {
        try {
            var data = ""
            Loge.i("粘包测试", " click $click:")
            if (click == 1) {
                data = "9B 00 02 82 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00".replace(
                    " ", ""
                )
//                data  = "9B 00 02 82 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 49 FF FF FF FF FF FF FF FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 61 9A 9B 00".replace(" ","")
                ++click
            } else {
                click = 1
                data = "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 49 FF FF FF FF FF FF FF FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 61 9A".replace(
                    " ", ""
                )
//                data = "02 82 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 49 FF FF FF FF FF FF FF FF 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 61 9A".replace(" ","")
            }

            val byteArray = TestByteData.hexStringToByteArray(data)
//             Loge.i("粘包测试", " 转换结果:")
//             Loge.i("粘包测试", " 字节数组: ${byteArray.joinToString(", ", "[", "]")}")
//             Loge.i("粘包测试", " 十六进制表示: ${byteArray.joinToString(" ") { "%02X".format(it) }}")
//             Loge.i("粘包测试", " 十进制表示: ${byteArray.joinToString(" ") { it.toString() }}")
//            // 验证转换结果
//            Loge.i("粘包测试", " \n验证转换:")
//            val backToHex = TestByteData.byteArrayToHexString(byteArray)
//            Loge.i("粘包测试", " 还原为十六进制: $backToHex")
//            Loge.i("粘包测试", " 转换是否成功: ${data == backToHex}")

            processReceivedData(byteArray)
//            processBuffer(byteArray)


        } catch (e: Exception) {
            Loge.i("粘包测试", " 转换错误: ${e.message}")
        }
    }

    private var lastProcessTime = 0L
    private val PROCESS_TIMEOUT = 50000L // 5秒超时

    // 缓冲区管理
    private val bufferNew232 = ByteArrayOutputStream(1024)

    /***
     * 指令位
     */
    val CMD_POS = 2

    /***
     * 取出校验码位
     */
    val CHECK_POS_DATA = 2

    /***
     * 取出数据域位生成效验位
     */
    val CHECK_POS = 3

    /***
     * 取出数据域位
     */
    val DATA_POS_LENGTH = 3

    /***
     * 前四位
     */
    val BEFORE_FOUR_POS = 4

    /***
     * 完成包
     */
    val COMPLETE_PACKAGE = 6

    /***
     * @param newData 接收的数据域
     */
    private fun processReceivedData(newData: ByteArray) {
        Loge.i(
            "粘包测试", "接232 测试新的方式 大小：${newData.size} 原始：${ByteUtils.toHexString(newData)}"
        )
        try {

            val currentTime = System.currentTimeMillis()

            // 如果距离上次处理时间过长，清空缓冲区（避免处理残留的无效数据）
            if (currentTime - lastProcessTime > PROCESS_TIMEOUT && bufferNew232.size() > 0) {
                Loge.i(
                    "粘包测试", "接232 测试新的方式 处理超时，清空缓冲区残留数据: ${bufferNew232.size()}字节"
                )
                bufferNew232.reset()
            }

            lastProcessTime = currentTime
            // 1. 追加新数据到缓冲区
            bufferNew232.write(newData)
            val currentBuffer = bufferNew232.toByteArray()

            // 2. 处理缓冲区中的数据
            var processedBytes = 0
            var currentIndex = 0
            Loge.i(
                "粘包测试", "接232 测试新的方式 processedBytes $processedBytes | currentIndex $currentIndex"
            )
            while (currentIndex < currentBuffer.size) {
                // 3. 查找帧头 (0x9B)
                val headerIndex = findFrameHeader(currentBuffer, currentIndex)
                if (headerIndex == -1) {
                    // 没有找到帧头，所有数据都无法处理
                    processedBytes = currentBuffer.size
                    break
                }
                Loge.i(
                    "粘包测试", "接232 测试新的方式 headerIndex $headerIndex | size ${currentBuffer.size}"
                )
                // 4. 检查是否有足够的数据获取长度字段 (header + 3)
                if (headerIndex + DATA_POS_LENGTH >= currentBuffer.size) {
                    // 数据不足，保留从帧头开始的所有数据
                    processedBytes = headerIndex
                    break
                }

                // 5. 获取数据长度 (第4个字节)
                val dataLength = currentBuffer[headerIndex + DATA_POS_LENGTH].toInt() and 0xFF

                // 6. 计算完整包长度 (修正：6 + dataLength)
                val totalLength = COMPLETE_PACKAGE + dataLength  // 帧头1 + 地址1 + 命令1 + 长度1 + 数据N + 校验码1 + 帧尾1
                Loge.i(
                    "粘包测试", "接232 测试新的方式 dataLength $dataLength | totalLength $totalLength"
                )
                // 7. 检查完整数据包
                if (headerIndex + totalLength > currentBuffer.size) {
                    // 数据包不完整，保留从帧头开始的数据
                    processedBytes = headerIndex
                    break
                }

                // 8. 检查帧尾 (0x9A)
                val frameEndIndex = headerIndex + totalLength - 1  // 帧尾在最后一个位置
                Loge.i("粘包测试", "接232 测试新的方式 frameEndIndex $frameEndIndex")
                if (currentBuffer[frameEndIndex] != SendByteData.RE_FRAME_END) {
                    // 帧尾错误，跳过这个帧头继续查找
                    currentIndex = headerIndex + 1
                    continue
                }

                // 9. 提取完整数据包
                val packet = currentBuffer.copyOfRange(headerIndex, headerIndex + totalLength)
                Loge.i("粘包测试", "接232 测试新的方式 packet ${ByteUtils.toHexString(packet)}")
                // 10. 校验和验证
                if (!validateCheckCode(packet)) {
                    // 校验失败，跳过这个包继续查找下一个
                    currentIndex = headerIndex + 1
                    continue
                }

                // 11. 处理有效数据包
                handlePacket232(packet)//新方式2

                // 12. 移动处理位置到下一个包
                currentIndex = headerIndex + totalLength
                processedBytes = currentIndex
                Loge.i(
                    "粘包测试", "接232 测试新的方式 while 内 currentIndex ${currentIndex} | processedBytes $processedBytes"
                )
            }
            Loge.i(
                "粘包测试", "接232 测试新的方式 while 外 processedBytes $processedBytes | size ${currentBuffer.size}"
            )
            // 13. 保存未处理数据到缓冲区
            bufferNew232.reset()
            if (processedBytes < currentBuffer.size) {
                val remainingData = currentBuffer.copyOfRange(processedBytes, currentBuffer.size)
                bufferNew232.write(remainingData)
                Loge.i(
                    "粘包测试", "接232 测试新的方式 while 外 拷贝数据 ${ByteUtils.toHexString(remainingData)}"
                )
                // 调试信息：显示保留的未处理数据长度
                if (remainingData.isNotEmpty()) {
                    Loge.i(
                        "粘包测试", "接232 测试新的方式 保留未处理数据: ${remainingData.size} 字节"
                    )
                }
            }

            // 调试信息：显示缓冲区状态
            logBufferStatus(currentBuffer.size, processedBytes)

        } catch (e: Exception) {
            Loge.i("粘包测试", "接232 测试新的方式 处理接收数据时发生异常: ${e.message}")
            // 发生异常时清空缓冲区，避免错误累积
            bufferNew232.reset()
        }
    }

    /**
     * 记录缓冲区状态
     */
    private fun logBufferStatus(totalSize: Int, processedBytes: Int) {
        val remaining = totalSize - processedBytes
        Loge.i(
            "粘包测试", "接232 测试新的方式 缓冲区处理: 总共${totalSize}字节, 已处理${processedBytes}字节, 剩余${remaining}字节"
        )
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
//        Loge.i("串口", "接232 测试新的方式 validateCheckCode:${ByteUtils.toHexString(packet)}")
//        if (packet.size < 6) {
//            Loge.w("数据包长度不足: ${packet.size}")
//            return false
//        }
//        val data = packet.copyOfRange(0, packet.size - CHECK_POS)
//        val checksumCalculated = data.sumOf { it.toInt() and 0xFF } % 256  // 确保字节按无符号处理
//        val checksumExpected = packet[packet.size - CHECK_POS_DATA].toInt() and 0xFF
//        val result = checksumCalculated == checksumExpected
//        Loge.i("串口", "接232 测试新的方式 算|取|果:$checksumCalculated:$checksumExpected:$result | data:${ByteUtils.toHexString(data)}")
//        return result
        if (packet.size < 6) {
            Loge.i("粘包测试", "接232 测试新的方式 数据包长度不足: ${packet.size}")
            return false
        }

        // 获取数据长度
        val dataLength = packet[3].toInt() and 0xFF

        // 验证包长度是否匹配
        val expectedTotalLength = 6 + dataLength
        if (packet.size != expectedTotalLength) {
            Loge.i(
                "粘包测试", "接232 测试新的方式 数据包长度不匹配: 期望=$expectedTotalLength, 实际=${packet.size}"
            )
            return false
        }

        // 计算校验和的范围：从帧头开始到数据区域结束
        // 数据区域结束位置 = 帧头(1) + 地址(1) + 命令(1) + 长度(1) + 数据(dataLength) = 4 + dataLength
        val dataEndIndex = 4 + dataLength  // 数据区域结束位置（不包括校验码）

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
        Loge.i(
            "粘包测试", """接232 测试新的方式 
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

    private fun handlePacket232(packet: ByteArray) {
        //此处进来的数据是没有帧尾 9B 00 0B 04 FF FF FF FF A6
        Loge.i(
            "粘包测试", "接232 测试新的方式 handlePacket232 处理数据 size ${packet.size} | ${
                ByteUtils.toHexString(packet)
            }"
        )
    }

    // 使用 ByteArray 作为缓冲区，提高处理效率
    private var buffer2322 = ByteArray(0)

    private fun processBuffer(packet: ByteArray) {
        buffer2322 += packet
        var currentPosition = 0

        while (true) {
            // 1. 查找帧头（从当前位置开始）
            val frameStart = buffer2322.indexOf(SendByteData.RE_FRAME_HEADER, currentPosition)
            if (frameStart == -1) break // 没有更多帧头

            // 2. 查找帧尾（必须位于帧头之后）
            val frameEndIndex = buffer2322.indexOf(SendByteData.RE_FRAME_END, frameStart + 1)
            if (frameEndIndex == -1) break // 当前帧不完整，等待更多数据

            // 3. 提取数据包（包含头尾）
            val packet = buffer2322.copyOfRange(frameStart, frameEndIndex + 1)
            Loge.i("粘包测试", "接232 测试新的方式 解析到完整包: ${packet}")

            // 4. 校验数据包（可选，根据协议实现）
            if (!validateCheckCode(packet)) {
                Loge.i(
                    "粘包测试", "接232 测试新的方式 校验失败，丢弃包: ${ByteUtils.toHexString(packet)}"
                )
                currentPosition = frameEndIndex + 1
                continue
            }

            // 5. 处理有效数据包
            handlePacket232(packet)//新方式2

            // 6. 移动指针到当前帧尾之后，继续查找下一帧
            currentPosition = frameEndIndex + 1
        }

        // 7. 清理已处理的数据（保留未处理部分）
        buffer2322 = if (currentPosition > 0) {
            buffer2322.copyOfRange(currentPosition, buffer2322.size)
        } else {
            buffer2322
        }
        Loge.i("粘包测试", "接232 测试新的方式 end ${ByteUtils.toHexString(buffer2322)}")
    }

    // 自定义带起始位置的 indexOf 方法
    private fun ByteArray.indexOf(byte: Byte, fromIndex: Int = 0): Int {
        for (i in fromIndex.coerceAtLeast(0) until this.size) {
            if (this[i] == byte) return i
        }
        return -1
    }

    /********************************************测试粘包问题***************************************************/

    fun setRbEnabled2(isAll: Boolean, isEnabled: Boolean) {
        if (isAll) {
            binding.mrbKg1.isChecked = false
            binding.mrbKg2.isChecked = false
            binding.mrbKg3.isChecked = false
            binding.mrbKg4.isChecked = false

            binding.mrbKg1.isEnabled = false
            binding.mrbKg2.isEnabled = false
            binding.mrbKg3.isEnabled = false
            binding.mrbKg4.isEnabled = false
        } else {
            binding.mrbKg1.isEnabled = true
            binding.mrbKg2.isEnabled = true
            binding.mrbKg3.isEnabled = true
            binding.mrbKg4.isEnabled = true
            when (weightKg) {
                1 -> {
                    binding.mrbKg1.isChecked = isEnabled
                }

                2 -> {
                    binding.mrbKg2.isChecked = isEnabled
                }

                3 -> {
                    binding.mrbKg3.isChecked = isEnabled
                }

                4 -> {
                    binding.mrbKg4.isChecked = isEnabled
                }
            }
        }
    }

    fun setRbEnabled(isEnabled: Boolean, weightKg: Int = -1) {
        for (i in 0 until binding.rgKg.childCount) {
            val child = binding.rgKg.getChildAt(i)
            child.isEnabled = isEnabled
            if (!isEnabled) {
                if (child is RadioButton) {
                    child.isChecked = false
                }
            } else {
                if ((weightKg - 1) == i) {
                    if (child is RadioButton) {
                        child.isChecked = true
                    }
                }
            }
        }
    }

    // 禁止选择
    fun setRadioGroupEnabled(rg: RadioGroup, enabled: Boolean) {
        for (i in 0 until rg.childCount) {
            val child = rg.getChildAt(i)
            child.isEnabled = enabled
            if (!enabled) {
                // 清除选中态
                if (child is RadioButton) {
                    child.isChecked = false
                }
                // 拦截点击
                child.setOnTouchListener { _, _ -> true }
            } else {
                // 恢复可点击
                child.setOnTouchListener(null)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
