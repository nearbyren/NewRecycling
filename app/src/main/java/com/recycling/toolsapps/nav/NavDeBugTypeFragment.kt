package com.recycling.toolsapps.nav

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.recycling.toolsapps.R
import com.recycling.toolsapps.databinding.NavFragmentDebugTypeBinding
import com.recycling.toolsapps.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapps.utils.EntityType
import com.recycling.toolsapps.utils.ResultType
import com.recycling.toolsapps.vm.CabinetVM
import com.serial.port.utils.AppUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.Loge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nearby.lib.netwrok.response.SPreUtil
import java.util.regex.Pattern


/***
 * 网络下发调试页
 */
@AndroidEntryPoint
class NavDeBugTypeFragment : BaseBindLazyTimeFragment<NavFragmentDebugTypeBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })
    private var downTime = 0L

    companion object {
        /***
         * is_index 0.左  1.右
         */
        const val IS_INDEX = "is_index"
        const val IS_LEFT = 0
        const val IS_RIGHT = 1
        const val IS_SHOW = "is_show"
    }

    /***
     * is_index 0.左  1.右
     */
    private var isIndex: Int = -1

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

    override fun getDisplayDuration(): Long = ResultType.DEBUGGING_SECONDS2

    override fun isAutoCloseEnabled(): Boolean = false

    // 创建任务队列
    override fun layoutRes(): Int {
        return R.layout.nav_fragment_debug_type
    }


    /**
     * 为 AppCompatEditText 设置数字范围限制
     */
    fun AppCompatEditText.setupNumberRange(
        minValue: Int = EntityType.ROD_HINDER_MIN, maxValue: Int = EntityType.ROD_HINDER_MAX, onValueChange: ((Int) -> Unit)? = null
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
        editText: AppCompatEditText, minValue: Int, maxValue: Int, onValueChange: ((Int) -> Unit)?
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
        Loge.e("流程 设置扭动门阻力值 格口：$currentGe key = $key | value = $value")
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

    override fun initialize(savedInstanceState: Bundle?) {
        val rodHinderValue1 =
            SPreUtil[AppUtils.getContext(), SPreUtil.rodHinderValue1, mRodHinderValue1] as Int
        val rodHinderValue2 =
            SPreUtil[AppUtils.getContext(), SPreUtil.rodHinderValue2, mRodHinderValue2] as Int
        binding.acivLogo.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (System.currentTimeMillis() - downTime >= 5000) {
                        // 执行5秒长按回调
                        toGoHome()
                        true // 消耗事件
                    } else false
                }

                else -> false
            }
        }
        arguments?.let { args ->
            isIndex = args.getInt(IS_INDEX, -1)
            when (isIndex) {
                0 -> {
                    binding.actvLeft.isSelected = true
                    binding.actvRight.isSelected = false
                    currentGe = CmdCode.GE1
                    binding.acetBoost.setText("$rodHinderValue1")
                }

                1 -> {
                    binding.actvLeft.isSelected = false
                    binding.actvRight.isSelected = true
                    currentGe = CmdCode.GE2
                    binding.acetBoost.setText("$rodHinderValue2")
                }
            }
        }
        initDoorStatus()
        binding.actvLeft.setOnClickListener {
            currentGe = CmdCode.GE1
            binding.actvLeft.isSelected = true
            binding.actvRight.isSelected = false
            isCheckSwitch = true
            switchData()
        }

        binding.actvRight.setOnClickListener {
            val geType = cabinetVM.doorGeXType
            if (geType == CmdCode.GE2) {
                currentGe = CmdCode.GE2
                binding.actvLeft.isSelected = false
                binding.actvRight.isSelected = true
                isCheckSwitch = true
                switchData()
            }
        }

        //减阻力阈值
        binding.del.setDebouncedClickListener {
            Loge.d("焦点消失后的值1: del")
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
            Loge.d("焦点消失后的值1: add")
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
        lifecycleScope.launch {
            cabinetVM.getTestClearDoor.collect { result ->
                if (result) {
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

        //称重前校准操作
        lifecycleScope.launch {
            cabinetVM.getCaliBefore2.collect { result ->
                binding.clWeight.isVisible = false
                //校准完成复原点击按钮
                binding.actvWeighing.isEnabled = true
                if (result) {
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

        //称重效验结果
        lifecycleScope.launch {
            cabinetVM.getCaliResult.collect { result ->
                if (result) {
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
