package com.recycling.toolsapps.nav

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import com.recycling.toolsapps.BuildConfig
import com.recycling.toolsapps.R
import com.recycling.toolsapps.databinding.NavFragmentMobileBinding
import com.recycling.toolsapps.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapps.utils.CalculationUtil
import com.recycling.toolsapps.utils.ResultType
import com.recycling.toolsapps.vm.CabinetVM
import com.serial.port.utils.AppUtils
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.Loge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nearby.lib.netwrok.response.SPreUtil


/***
 * 手机号登录
 */
@AndroidEntryPoint
class NavMobileFragment : BaseBindLazyTimeFragment<NavFragmentMobileBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })
    private var shoeDebug = false
    override fun isAutoCloseEnabled(): Boolean = true

    override fun getDisplayDuration(): Long = ResultType.LOGIN_MOBILE_SECONDS2

    // 创建任务队列
    override fun layoutRes(): Int {
        return R.layout.nav_fragment_mobile
    }

    fun removeLastDigit(numberStr: String): String {
        return if (numberStr.isNotEmpty() && numberStr.all { it.isDigit() }) {
            numberStr.dropLast(1).ifEmpty { "" }
        } else {
            numberStr
        }
    }

    override fun onDestroyView() {
        binding.clSelect.isVisible = false
        binding.clNumberKeysDebug.isVisible = false
        super.onDestroyView()
    }

    override fun initialize(savedInstanceState: Bundle?) {
        binding.acivLogo.setOnClickListener {
            binding.clNumberKeysDebug.isVisible = !shoeDebug
        }
        binding.acivClose.setOnClickListener {
            binding.clSelect.isVisible = !binding.clSelect.isVisible
        }

        val curWeight1 = cabinetVM.curG1Weight//手机登录读取格口重量一
        val curWeight2 = cabinetVM.curG2Weight//手机登录读取格口重量二

        val leftValue = CalculationUtil.getWeightPercent(
            curWeight1?.toFloat() ?: 0.00f, cabinetVM.curG1TotalWeight.toFloat()
        )
        val rightValue = CalculationUtil.getWeightPercent(
            curWeight2?.toFloat() ?: 0.00f, cabinetVM.curG2TotalWeight.toFloat()
        )

        binding.actvCastLeftValue.text = "容量已满$leftValue\n (${curWeight1}kg)"
        binding.actvCastRightValue.text = "容量已满$rightValue\n (${curWeight2}kg)"

        //匹配是否显示两个格口
        if (cabinetVM.doorGeXType == CmdCode.GE1) {
            binding.clCastLeft.isVisible = true
            binding.clCastRight.isVisible = false
        } else if (cabinetVM.doorGeXType == CmdCode.GE2) {
            binding.clCastLeft.isVisible = true
            binding.clCastRight.isVisible = true
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.uiCloseStep.collect {
                    BoxToolLogUtils.savePrintln("业务流：关闭页面 手机页面-> $it")
                    when (it) {
                        CabinetVM.UiCloseStep.IDLE -> {}
                        CabinetVM.UiCloseStep.CLOSE_DELIVERY -> {
                        }

                        CabinetVM.UiCloseStep.CLOSE_MOBILE -> {
                            super.performCloseAction()
                        }

                        CabinetVM.UiCloseStep.CLOSE_CLEAR_DOOR -> {
                        }
                    }
                }
            }
        }
        //左投口
        binding.acivCastLeftLogo.setOnClickListener {
            SPreUtil.put(AppUtils.getContext(), SPreUtil.mobileDoorGeX, 1)
            val mobile = binding.acetMobile.text.toString()
            SPreUtil.put(AppUtils.getContext(), SPreUtil.setMobileDoor, mobile)
            cabinetVM.toGoMobile(mobile, CmdCode.GE1)
        }
        //右投口
        binding.acivCastRightLogo.setOnClickListener {
            SPreUtil.put(AppUtils.getContext(), SPreUtil.mobileDoorGeX, 2)
            val mobile = binding.acetMobile.text.toString()
            SPreUtil.put(AppUtils.getContext(), SPreUtil.setMobileDoor, mobile)
            cabinetVM.toGoMobile(mobile, CmdCode.GE2)
        }


        binding.actvExit.setOnClickListener {
            super.performCloseAction()
        }
        binding.actvExit1.setOnClickListener {
            super.performCloseAction()
        }
        val selectableViews = listOf(
            binding.acet1,
            binding.acet2,
            binding.acet3,
            binding.acet4,
            binding.acet5,
            binding.acet6,
            binding.acet7,
            binding.acet8,
            binding.acet9,
            binding.acet0,
            binding.acetDel,
            binding.acetMobile,
//                binding.actvLogin
        )
        selectableViews.forEach { view ->
            view.setOnClickListener {
                val acetMbile = binding.acetMobile
                val value = acetMbile.text.toString()
                val builder = StringBuilder()
                builder.append(value)
                // 执行业务逻辑
                when (view.id) {
                    R.id.acet_1 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_1")
                        builder.append(1)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_2 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_2")
                        builder.append(2)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_3 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_3")
                        builder.append(3)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_4 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_4")
                        builder.append(4)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_5 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_5")
                        builder.append(5)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_6 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_6")
                        builder.append(6)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_7 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_7")
                        builder.append(7)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_8 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_8")
                        builder.append(8)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_9 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_9")
                        builder.append(9)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)
                    }

                    R.id.acet_0 -> {
                        if (value.length == 11) return@setOnClickListener
                        Loge.d("handle acet_0")
                        builder.append(0)
                        acetMbile.setText(builder.toString())
                        acetMbile.setSelection(builder.toString().length)

                    }

                    R.id.acet_del -> {
                        Loge.d("handle acet_del")
                        val result = builder.toString()
                        if (result.isEmpty()) return@setOnClickListener
                        val result2 = removeLastDigit(result)
                        acetMbile.setText(result2)
                    }
                }
            }
        }
        binding.actvLogin.setDebouncedClickListener(2000L) {
            val value = binding.acetMobile.text.toString()
            Loge.d("handle actv_login")
            if (value.length != 11) {
                cabinetVM.tipMessage("请输入手机号")
                return@setDebouncedClickListener
            }
            if (cabinetVM.isDistClient) {
                cabinetVM.tipMessage("与服务器通信已断开")
                return@setDebouncedClickListener
            }
            val typeGrid = SPreUtil[AppUtils.getContext(), SPreUtil.type_grid, -1]
            Loge.d("去开启格口 格口类型：$typeGrid")
            if (typeGrid == 2) {
                binding.clSelect.isVisible = !binding.clSelect.isVisible
            } else {
                SPreUtil.put(AppUtils.getContext(), SPreUtil.mobileDoorGeX, 1)
                val mobile = binding.acetMobile.text.toString()
                SPreUtil.put(AppUtils.getContext(), SPreUtil.setMobileDoor, mobile)
                cabinetVM.toGoMobile(mobile, CmdCode.GE1)
            }
        }
        initDebug()
    }

    fun initDebug() {
        val selectableViews = listOf(
            binding.acet11, binding.acet22, binding.acet33, binding.acet44, binding.acet55, binding.acet66, binding.acet77, binding.acet88, binding.acet99, binding.acet00, binding.acetDel2, binding.acetGo
        )
        selectableViews.forEach { view ->
            view.setOnClickListener {
                val acetDebug = binding.acetDebug
                val value = acetDebug.text.toString()
                val builder = StringBuilder()
                builder.append(value)
                // 执行业务逻辑
                when (view.id) {
                    R.id.acet_11 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_1")
                        builder.append(1)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_22 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_2")
                        builder.append(2)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_33 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_3")
                        builder.append(3)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_44 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_4")
                        builder.append(4)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_55 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_5")
                        builder.append(5)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_66 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_6")
                        builder.append(6)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_77 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_7")
                        builder.append(7)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_88 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_8")
                        builder.append(8)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_99 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_9")
                        builder.append(9)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)
                    }

                    R.id.acet_00 -> {
                        if (value.length == 6) return@setOnClickListener
                        Loge.d("handle acet_0")
                        builder.append(0)
                        acetDebug.setText(builder.toString())
                        acetDebug.setSelection(builder.toString().length)

                    }

                    R.id.acet_del2 -> {
                        Loge.d("handle acet_del")
                        val result = builder.toString()
                        if (result.isEmpty()) return@setOnClickListener
                        val result2 = removeLastDigit(result)
                        acetDebug.setText(result2)
                    }

                    R.id.acet_go -> {
                        binding.clNumberKeysDebug.isVisible = false
                        Loge.d("handle actv_go")
                        if (value.length != 6) {
                            cabinetVM.tipMessage("调试密码不正确")
                            acetDebug.setText("")
                            return@setOnClickListener
                        }
                        acetDebug.setText("")
                        val debugPasswd =
                            SPreUtil.get(AppUtils.getContext(), SPreUtil.debugPasswd, "")
                        if (value == debugPasswd) {
                            super.performCloseAction()
                            toGoDebug()
                        } else {
                            cabinetVM.tipMessage("调试密码错误")
                        }
                    }
                }
            }
        }
    }

    fun toGoDebug() {
        if (BuildConfig.DEBUG) {

            val args: Bundle = Bundle().apply {
                putInt(
                    NavDeBugTypeSelfFragment.IS_INDEX, NavDeBugTypeSelfFragment.IS_LEFT
                )
                putBoolean(NavDeBugTypeSelfFragment.IS_SHOW, true)
            }
            Navigation.findNavController(
                binding.acetDebug,
            ).navigate(R.id.action_start_debug_type_self, args)

        } else {
            val args: Bundle = Bundle().apply {
                putInt(
                    NavDeBugTypeFragment.IS_INDEX, NavDeBugTypeFragment.IS_LEFT
                )
                putBoolean(NavDeBugTypeFragment.IS_SHOW, true)
            }
            Navigation.findNavController(
                binding.acetDebug
            ).navigate(R.id.action_start_debug_type, args)
        }

    }

    // 为View添加防抖点击扩展函数
    fun View.setDebouncedClickListener(
        debounceTime: Long = 500L,
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
}
