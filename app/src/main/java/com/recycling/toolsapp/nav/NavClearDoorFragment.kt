package com.recycling.toolsapp.nav

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.recycling.toolsapp.R
import com.recycling.toolsapp.databinding.NavFragmentClearDoorBinding
import com.recycling.toolsapp.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapp.utils.CalculationUtil
import com.recycling.toolsapp.utils.MonitorType
import com.recycling.toolsapp.utils.ResultType
import com.recycling.toolsapp.vm.CabinetVM
import com.recycling.toolsapp.vm.ClearTimer
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.Loge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


/***
 * 清运门
 */
@AndroidEntryPoint
class NavClearDoorFragment : BaseBindLazyTimeFragment<NavFragmentClearDoorBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })
    override fun isAutoCloseEnabled(): Boolean = true

    override fun getDisplayDuration(): Long = ResultType.DELIVERY_CLEAR_SECONDS2

    // 创建任务队列
    override fun layoutRes(): Int {
        return R.layout.nav_fragment_clear_door
    }

    private fun latestBusiness() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.uiCloseStep.collect {
                    BoxToolLogUtils.savePrintln("业务流：关闭页面 清运页=-> $it")
                    when (it) {
                        CabinetVM.UiCloseStep.IDLE -> {}
                        CabinetVM.UiCloseStep.CLOSE_DELIVERY -> {
                        }

                        CabinetVM.UiCloseStep.CLOSE_MOBILE -> {
                        }

                        CabinetVM.UiCloseStep.CLOSE_CLEAR_DOOR -> {
                            super.performCloseAction()
                        }
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.refBusStaStateFlow.collect {
                    Loge.e("业务流：刷新重量 清运页面 -> $it")
                    if (it == null) return@collect
                    val refreshType = it.refreshType
                    when (refreshType) {
                        1 -> {
                            //清运前重量
                            val weightBeforeOpen = it.weightBeforeOpenValue
                            val weightDuringOpening = it.weightDuringOpeningValue
                            val weightAfterClosing = it.weightAfterClosingValue
                            binding.tvClearBeforeValue.text = "${weightBeforeOpen}KG"
                            //清运后重量
                            binding.tvClearAfterValue.text = "${weightDuringOpening}KG"
                            val result = if (weightAfterClosing == "0.00") weightDuringOpening else weightAfterClosing
                            //换算重量
                            val clearValue = CalculationUtil.subtractFloats(
                                weightBeforeOpen ?: "0.00", result ?: "0.00"
                            )
                            //清运重量
                            binding.tvClearValue.text = "${clearValue}KG"
                        }
                    }

                }
            }
        }
    }

    override fun initialize(savedInstanceState: Bundle?) {
        latestBusiness()
        binding.cpvView.setMaxProgress(ResultType.DELIVERY_CLEAR_SECONDS)
        cabinetVM.clearStartTimer(ResultType.DELIVERY_CLEAR_SECONDS)
        upgradeAi()
    }

    private fun upgradeAi() {
        // 在 Activity/Fragment 中收集状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.clearState.collect { state ->
                    when (state) {
                        is ClearTimer.CountdownState.Starting -> {
                            binding.cpvView.setMaxProgress(ResultType.DELIVERY_CLEAR_SECONDS)
                        }

                        is ClearTimer.CountdownState.Running -> {
                            // 更新 UI
                            binding.cpvView.setProgress(state.secondsRemaining)
                        }

                        ClearTimer.CountdownState.Finished -> {
                            Loge.e("流程 deliveryState 倒计时结束")
                        }

                        is ClearTimer.CountdownState.Error -> {
                            cabinetVM.tipMessage(state.message)
                        }
                    }
                }
            }
        }
    }
}
