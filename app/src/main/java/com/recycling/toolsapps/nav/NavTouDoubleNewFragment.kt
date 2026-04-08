package com.recycling.toolsapps.nav

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.cabinet.toolsapp.tools.bus.FlowBus
import com.cabinet.toolsapp.tools.bus.ResEvent
import com.recycling.toolsapps.R
import com.recycling.toolsapps.databinding.NavTouDoubleFragmentBinding
import com.recycling.toolsapps.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapps.utils.CalculationUtil
import com.recycling.toolsapps.vm.CabinetVM
import com.serial.port.utils.AppUtils
import com.serial.port.utils.BoxToolLogUtils
import com.serial.port.utils.CmdCode
import com.serial.port.utils.Loge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nearby.lib.netwrok.response.SPreUtil
import nearby.lib.signal.livebus.BusType
import java.io.File

/**
 * 双投口
 */
@AndroidEntryPoint
class NavTouDoubleNewFragment : BaseBindLazyTimeFragment<NavTouDoubleFragmentBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })
    private var downTime = 0L
    override fun isAutoCloseEnabled(): Boolean = false
    override fun layoutRes(): Int {
        return R.layout.nav_tou_double_fragment
    }

    override fun initialize(savedInstanceState: Bundle?) {
        cabinetVM.doorGeXType = CmdCode.GE2
        latestBusiness()
        binding.clMobileNet.setOnClickListener {
            Navigation.findNavController(it).navigate(R.id.action_start_mobile)
        }
        FlowBus.with<ResEvent>("ResEvent").register(this) {
            refreshQrCodeRes()
        }
        refreshQrCodeRes()
        val warningContent1 =
            SPreUtil[AppUtils.getContext(), SPreUtil.netStatusText1, BusType.BUS_NORMAL] as String
        refreshQrCodeRes()
        val warningContent2 =
            SPreUtil[AppUtils.getContext(), SPreUtil.netStatusText2, BusType.BUS_NORMAL] as String
        initWarningContent(warningContent1, 1)
        initWarningContent(warningContent2, 2)
    }

    private fun refreshQrCodeRes() {
        cabinetVM.mQrCode?.let { bitmap ->
            binding.acivCodeNet.setImageBitmap(bitmap)
        }
    }

    private fun latestBusiness() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.refBusStaChannel.collect {
                    BoxToolLogUtils.savePrintln("业务流：刷新重量 投递双 -> $it")
                    val refreshType = it.refreshType
                    val warningContent = it.warningContent
                    val doorGeX = it.doorGeX
                    when (refreshType) {
                        1 -> {
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
                            setPriceWeight(weightPercent, cabinetVM.doorGeX)
                        }

                        2 -> {
                            initWarningContent(warningContent, doorGeX)
                        }
                    }

                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.uiCloseStep.collect {
                    BoxToolLogUtils.savePrintln("业务流：关闭页面 投递双-> $it")
                    when (it) {
                        CabinetVM.UiCloseStep.IDLE -> {}
                        CabinetVM.UiCloseStep.CLOSE_DELIVERY -> {
                            super.performCloseAction()
                        }

                        CabinetVM.UiCloseStep.CLOSE_MOBILE -> {
                        }

                        CabinetVM.UiCloseStep.CLOSE_CLEAR_DOOR -> {

                        }
                    }
                }
            }
        }
    }

    fun setPriceWeight(weightPercent: Int, doorGeX: Int) {
        when (doorGeX) {
            1 -> {
                val curWeight = cabinetVM.curG1Weight//刷新ui当前格口重量
                if (weightPercent > 0) {
                    val wp = CalculationUtil.divideFloats(weightPercent.toString(), "100")
                    //以服务器百分比换算后的重量
                    val result = CalculationUtil.multiplyFloats(curWeight ?: "0.00", wp)
                    if (CalculationUtil.lessEqual(result ?: "0.00")) {
                        binding.tvLeftCurWeightNet.text = "当前重量(kg)：$curWeight"
                    } else {
                        binding.tvLeftCurWeightNet.text = "当前重量(kg)：$result"
                    }
                } else {
                    binding.tvLeftCurWeightNet.text = "当前重量(kg)：$curWeight"
                }
                val votable = cabinetVM.getVot1Weight()
                Loge.e("流程 刷新Ui $curWeight | $votable")
                //可再投递重量
                binding.tvLeftCurWeightNet.text = "可再投递(kg)：$votable"
                val valueNet = binding.tvLeftCurWeightNet.text.toString()
                setTextColorFromPosition(binding.tvLeftCurWeightNet, valueNet, 9, Color.YELLOW)
            }

            2 -> {
                val curWeight = cabinetVM.curG1Weight//刷新ui当前格口重量
                if (weightPercent > 0) {
                    val wp = CalculationUtil.divideFloats(weightPercent.toString(), "100")
                    //以服务器百分比换算后的重量
                    val result = CalculationUtil.multiplyFloats(curWeight ?: "0.00", wp)
                    if (CalculationUtil.lessEqual(result ?: "0.00")) {
                        binding.tvRightCurWeightNet.text = "当前重量(kg)：$curWeight"
                    } else {
                        binding.tvRightCurWeightNet.text = "当前重量(kg)：$result"
                    }
                } else {
                    binding.tvRightCurWeightNet.text = "当前重量(kg)：$curWeight"
                }
                val votable = cabinetVM.getVot1Weight()
                Loge.e("流程 刷新Ui $curWeight | $votable")
                //可再投递重量
                binding.tvRightCurWeightNet.text = "可再投递(kg)：$votable"
                val valueNet = binding.tvRightCurWeightNet.text.toString()
                setTextColorFromPosition(binding.tvRightCurWeightNet, valueNet, 9, Color.YELLOW)
            }
        }
    }

    private fun initWarningContent(warningContent: String, doorGeX: Int) {
        when (doorGeX) {
            1 -> {
                when (warningContent) {
                    BusType.BUS_OVERFLOW -> {
                        binding.acivStatusLeftNet.isVisible = true
                        binding.acivStatusLeftNet.setBackgroundResource(R.drawable.ic_my1)
                    }

                    BusType.BUS_FAULT -> {
                        binding.acivStatusLeftNet.isVisible = true
                        binding.acivStatusLeftNet.setBackgroundResource(R.drawable.ic_gz1)
                    }

                    BusType.BUS_MAINTAINING -> {
                        binding.acivStatusNet.isVisible = true
                        val options = RequestOptions().skipMemoryCache(true) // 禁用内存缓存
                            .diskCacheStrategy(DiskCacheStrategy.NONE) // 禁用磁盘缓存
                        Glide.with(AppUtils.getContext()).asBitmap().apply(options)
                            .load(File("${AppUtils.getContext().filesDir}/res/maintaining.png"))
                            .into(object : CustomTarget<Bitmap?>() {
                                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                                    binding.acivStatusNet.setImageBitmap(resource)

                                }

                                override fun onLoadCleared(placeholder: Drawable?) {
                                    // 清理资源
                                }
                            })
                    }

                    BusType.BUS_MAINTAINING_END -> {
                        binding.acivStatusNet.isVisible = false
                    }

                    BusType.BUS_NORMAL -> {
                        val netStatusText1 =
                            SPreUtil[AppUtils.getContext(), SPreUtil.netStatusText1, BusType.BUS_NORMAL] as String
                        if (netStatusText1 != BusType.BUS_MAINTAINING) {
                            binding.acivStatusLeftNet.isVisible = false
                        }
                        if (netStatusText1 == BusType.BUS_MAINTAINING_END) {
                            binding.acivStatusNet.isVisible = false
                        }
                    }

                }
            }

            2 -> {
                when (warningContent) {
                    BusType.BUS_OVERFLOW -> {
                        binding.acivStatusRightNet.isVisible = true
                        binding.acivStatusRightNet.setBackgroundResource(R.drawable.ic_my2)
                    }

                    BusType.BUS_FAULT -> {
                        binding.acivStatusRightNet.isVisible = true
                        binding.acivStatusRightNet.setBackgroundResource(R.drawable.ic_gz2)
                    }

                    BusType.BUS_MAINTAINING -> {
                        binding.acivStatusNet.isVisible = true
                        cabinetVM.mMaintaining?.let { bitmap ->
                            binding.acivStatusNet.setImageBitmap(bitmap)
                        }.also {
                            if (cabinetVM.mMaintaining == null) {
                                val options = RequestOptions().skipMemoryCache(true) // 禁用内存缓存
                                    .diskCacheStrategy(DiskCacheStrategy.NONE) // 禁用磁盘缓存
                                Glide.with(AppUtils.getContext()).asBitmap().apply(options)
                                    .load(File("${AppUtils.getContext().filesDir}/res/maintaining.png"))
                                    .into(object : CustomTarget<Bitmap?>() {
                                        override fun onResourceReady(
                                            resource: Bitmap,
                                            transition: Transition<in Bitmap?>?,
                                        ) {
                                            binding.acivStatusNet.setImageBitmap(resource)

                                        }

                                        override fun onLoadCleared(placeholder: Drawable?) {
                                            // 清理资源
                                        }
                                    })
                            }
                        }
                    }

                    BusType.BUS_MAINTAINING_END -> {
                        binding.acivStatusNet.isVisible = false
                    }

                    BusType.BUS_NORMAL -> {
                        val netStatusText2 =
                            SPreUtil[AppUtils.getContext(), SPreUtil.netStatusText2, BusType.BUS_NORMAL] as String
                        if (netStatusText2 != BusType.BUS_MAINTAINING) {
                            binding.acivStatusRightNet.isVisible = false
                        }
                        if (netStatusText2 == BusType.BUS_MAINTAINING_END) {
                            binding.acivStatusNet.isVisible = false
                        }
                    }
                }
            }
        }

    }

    fun setTextColorFromPosition(textView: AppCompatTextView, text: String, startIndex: Int, color: Int) {
        val spannable = SpannableString(text)
        spannable.setSpan(ForegroundColorSpan(color), startIndex, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = spannable
    }
}
