package com.recycling.toolsapps.nav

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.recycling.toolsapps.R
import com.recycling.toolsapps.databinding.NavFragmentClearDoorBinding
import com.recycling.toolsapps.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapps.utils.CalculationUtil
import com.recycling.toolsapps.utils.MonitorType
import com.recycling.toolsapps.utils.ResultType
import com.recycling.toolsapps.vm.CabinetVM
import com.recycling.toolsapps.vm.ClearTimer
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
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.refBusStaChannel.collect {
                    BoxToolLogUtils.savePrintln("业务流：刷新重量 清运页面 -> $it")
                    val refreshType = it.refreshType
                    when (refreshType) {
                        1 -> {
                            //清运前重量
                            val beforeValue = it.weightBeforeOpenValue
                            val afterValue = it.weightAfterClosingValue
                            binding.tvClearBeforeValue.text = "${beforeValue}KG"
                            //清运后重量
                            binding.tvClearAfterValue.text = "${afterValue}KG"
                            //换算重量
                            val clearValue = CalculationUtil.subtractFloats(
                                beforeValue ?: "0.00", afterValue ?: "0.00"
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
                            cabinetVM.setFlowMonitorDoor(MonitorType.TYPE_110)
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
