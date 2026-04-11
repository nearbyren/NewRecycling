package com.recycling.toolsapp.nav

import android.os.Bundle
import androidx.fragment.app.viewModels
import com.recycling.toolsapp.R
import com.recycling.toolsapp.databinding.NavFragmentCameraInBinding
import com.recycling.toolsapp.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapp.vm.CabinetVM
import com.recycling.toolsapp.vm.NewDualUsbCameraManager


/***
 * 内外相机拍摄
 */
class NavCameraInFragment : BaseBindLazyTimeFragment<NavFragmentCameraInBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })

    override fun isAutoCloseEnabled(): Boolean = false
    override fun layoutRes(): Int {
        return R.layout.nav_fragment_camera_in
    }

    private val cameraErrorListener =
        NewDualUsbCameraManager.CameraErrorListener { status, index, text ->
            if (!status) {
                cabinetVM.tipMessage("摄像头【$index】$text")
            }
        }

    override fun initialize(savedInstanceState: Bundle?) {
        binding.actvReturn.setOnClickListener {
            super.performCloseAction()
        }
        cabinetVM.cameraManagerNew.autoStartUsbCameras(false, binding.textureIn!!, null, delayMs = 3000, listener = cameraErrorListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        cabinetVM.cameraManagerNew.destroy()
    }
}

