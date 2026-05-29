package com.recycling.toolsapp.nav

import android.os.Bundle
import android.os.Environment
import androidx.fragment.app.viewModels
import com.recycling.toolsapp.R
import com.recycling.toolsapp.databinding.NavFragmentCameraOutBinding
import com.recycling.toolsapp.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapp.vm.CabinetVM
import com.recycling.toolsapp.vm.NewDualUsbCameraManager
import com.serial.port.utils.AppUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random


/***
 * 内外相机拍摄
 */
@AndroidEntryPoint
class NavCameraOutFragment : BaseBindLazyTimeFragment<NavFragmentCameraOutBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })

    override fun isAutoCloseEnabled(): Boolean = false
    override fun layoutRes(): Int {
        return R.layout.nav_fragment_camera_out
    }

    private val cameraErrorListener =
        NewDualUsbCameraManager.CameraErrorListener { status, index, text  ->
            if (!status) {
                cabinetVM.tipMessage("摄像头【$index】$text")
            }
        }
    fun generateRandomDigitString(length: Int = 24): String {
        return StringBuilder(length).apply {
            repeat(length) {
                // 生成 0-9 的随机数字并追加
                append(Random.nextInt(10))
            }
        }.toString()
    }
    override fun initialize(savedInstanceState: Bundle?) {
        binding.actvReturn.setOnClickListener {
            super.performCloseAction()
        }
        cabinetVM.cameraManagerNew.autoStartUsbCameras(false, null, binding.textureOut, delayMs = 3000, listener = cameraErrorListener)
        binding.actvOutCamera.setOnClickListener {
            cabinetVM.ioScope.launch {
                val ids = cabinetVM.cameraManagerNew.getExternalCameraIds()
                val requests = mutableListOf<NewDualUsbCameraManager.PhotoRequest>()
                val tran = generateRandomDigitString()
                val nameIn = "10i${tran}-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}.jpg"
                val dirIn = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
                if (!dirIn.exists()) dirIn.mkdirs()
                val fileIn = File(dirIn, nameIn)
                requests.add(NewDualUsbCameraManager.PhotoRequest(ids[0], 1, "内", fileIn, 1))

                val nameOut = "22o${tran}-${AppUtils.getDateHMS2()}---${AppUtils.getDateYMD()}.jpg"
                val dirOut = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
                if (!dirOut.exists()) dirOut.mkdirs()
                val fileOut = File(dirOut, nameOut)
                requests.add(NewDualUsbCameraManager.PhotoRequest(ids[1], 1, "外", fileOut, 1))

                cabinetVM.cameraManagerNew.takePicturesSequentialOpenClose(requests)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cabinetVM.cameraManagerNew.destroy()
    }

}
