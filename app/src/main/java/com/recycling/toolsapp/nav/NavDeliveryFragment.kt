package com.recycling.toolsapp.nav

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.recycling.toolsapp.R
import com.recycling.toolsapp.databinding.NavFragmentDeliveryBinding
import com.recycling.toolsapp.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapp.utils.CalculationUtil
import com.recycling.toolsapp.utils.CmdValue
import com.recycling.toolsapp.utils.ResultType
import com.recycling.toolsapp.vm.CabinetVM
import com.recycling.toolsapp.vm.DeliveryTimer
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.Loge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


/***
 * 称重页
 */
@AndroidEntryPoint
class NavDeliveryFragment : BaseBindLazyTimeFragment<NavFragmentDeliveryBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })
    override fun isAutoCloseEnabled(): Boolean = false


    // 创建任务队列
    override fun layoutRes(): Int {
        return R.layout.nav_fragment_delivery
    }

    private fun latestBusiness() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.refBusStaStateFlow.collect {
                    Loge.e("业务流：刷新重量 结算页面-> $it")
                    if (it == null) return@collect
                    val refreshType = it.refreshType
                    val taskPhotoPath = it.takePhotoUrl
                    when (refreshType) {
                        1 -> {
                            val weightDuringOpening = it.weightDuringOpeningValue ?: "0.00"
                            val weightAfterOpening = it.weightAfterOpeningValue ?: "0.00"
                            val resultWeight = CalculationUtil.subtractFloats(
                                weightDuringOpening, weightAfterOpening
                            )
                            val weightPercent = when (cabinetVM.doorGeX) {
                                CmdCode.GE1 -> {
                                    cabinetVM.weightPercent1
                                }

                                CmdCode.GE2 -> {
                                    cabinetVM.weightPercent2
                                }

                                else -> {
                                    0
                                }
                            }
                            val price = cabinetVM.curGe1Price ?: "0.6"
                            if (weightPercent <= 0) {
                                val floatValue = CalculationUtil.multiplyFloats(price, resultWeight)
                                //当前金额
                                binding.tvMoneyValue.text = "$floatValue 元"
                                //当前称重
                                binding.tvWeightValue.text = "$resultWeight 公斤"
                                Loge.e("业务流：刷新当前页面数据重量：$resultWeight | 价格：$price")

                            } else {
                                val wp = CalculationUtil.divideFloats(weightPercent.toString(), "100")
                                //以服务器百分比换算后的重量
                                val result = CalculationUtil.multiplyFloats(resultWeight, wp)
                                if (weightPercent > 0) {
                                    if (CalculationUtil.lessEqual(result)) {
                                        val floatValue = CalculationUtil.multiplyFloats(price, resultWeight)
                                        //当前金额
                                        binding.tvMoneyValue.text = "$floatValue 元"
                                        //当前称重
                                        binding.tvWeightValue.text = "$resultWeight 公斤"
                                        Loge.e("业务流：刷新当前页面数据重量：$resultWeight | 价格：$price")
                                    } else {
                                        val floatValue = CalculationUtil.multiplyFloats(price, result)
                                        //当前金额
                                        binding.tvMoneyValue.text = "$floatValue 元"
                                        //当前称重
                                        binding.tvWeightValue.text = "$result 公斤"
                                        Loge.e("业务流：刷新当前页面数据重量：$resultWeight | 价格：$price")
                                    }
                                }
                            }
                        }

                        3 -> {
                            binding.tvOperation.isEnabled = true
                            binding.tvOperation.text = "点击关闭仓门"
                        }

                        4 -> {
                            try {
                                if (taskPhotoPath.isNotEmpty()) {
                                    val bitmap = BitmapFactory.decodeFile(taskPhotoPath)
                                    if (bitmap != null) {
                                        val imageView = AppCompatImageView(requireActivity()).apply {
                                            layoutParams = LinearLayoutCompat.LayoutParams(
                                                LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT
                                            ).apply {
                                                setMargins(0, 0, 0, 20)
                                            }
                                            scaleType = ImageView.ScaleType.FIT_CENTER
                                            adjustViewBounds = true  // 允许根据图片比例调整边界
                                        }
                                        imageView.setImageBitmap(bitmap)
                                        binding.llPhoto.addView(imageView)
                                        binding.llPhoto.invalidate()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun initialize(savedInstanceState: Bundle?) {
        binding.tvOperation.isEnabled = false
        binding.tvOperation.text = "正在开启仓门中"
        binding.tvOperation.setOnClickListener {
            cabinetVM.deliverycancelTimer()
            binding.tvOperation.text = "正在关闭仓门中"
            cabinetVM.setFlowCurrentStep(CabinetVM.LockerStep.CLICK_CLOSE)
        }
        binding.cpvView.setMaxProgress(ResultType.DELIVERY_SECONDS)
        cabinetVM.deliveryStartTimer(ResultType.DELIVERY_SECONDS)
        upgradeAi()
        latestBusiness()

    }

    private fun upgradeAi() {
        // 在 Activity/Fragment 中收集状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.deliveryState.collect { state ->
                    when (state) {
                        is DeliveryTimer.CountdownState.Starting -> {
                            Loge.e("流程 deliveryState Starting")
                            binding.cpvView.setMaxProgress(ResultType.DELIVERY_SECONDS)
                        }

                        is DeliveryTimer.CountdownState.Running -> {
                            Loge.e("流程 deliveryState CountdownState")
                            // 更新 UI
                            binding.cpvView.setProgress(state.secondsRemaining)
                        }

                        DeliveryTimer.CountdownState.Finished -> {
                            Loge.e("流程 deliveryState Finished")
                            cabinetVM.setFlowCurrentStep(CabinetVM.LockerStep.CLICK_CLOSE)
                        }

                        is DeliveryTimer.CountdownState.Error -> {
                            Loge.e("流程 deliveryState CountdownState")
                            cabinetVM.tipMessage(state.message)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Loge.e("流程 getTakePic onDestroy ")
        cabinetVM.deliverycancelTimer()
        releaseBitmapResources()
        binding.llPhoto.removeAllViews()
    }

    private fun releaseBitmapResources() {
        for (i in 0 until binding.llPhoto.childCount) {
            val child = binding.llPhoto.getChildAt(i)
            if (child is AppCompatImageView) {
                val bitmap = (child.drawable as? BitmapDrawable)?.bitmap
                bitmap?.recycle()
                child.setImageDrawable(null)
            }
        }
    }
}
