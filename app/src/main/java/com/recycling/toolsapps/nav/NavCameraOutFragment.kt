package com.recycling.toolsapps.nav

import android.Manifest.permission.CAMERA
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.camera.core.impl.utils.CompareSizesByArea
import androidx.core.app.ActivityCompat
import androidx.fragment.app.viewModels
import com.recycling.toolsapps.R
import com.recycling.toolsapps.databinding.NavFragmentCameraOutBinding
import com.recycling.toolsapps.fitsystembar.base.bind.BaseBindLazyTimeFragment
import com.recycling.toolsapps.utils.FaultType
import com.recycling.toolsapps.utils.PermissionsRequester
import com.recycling.toolsapps.vm.CabinetVM
import com.serial.port.utils.AppUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Arrays
import java.util.Collections


/***
 * 内外相机拍摄
 */
@AndroidEntryPoint
class NavCameraOutFragment : BaseBindLazyTimeFragment<NavFragmentCameraOutBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })
    private lateinit var permissionsManager: PermissionsRequester
    private var cameraDeviceOut: CameraDevice? = null

    private var imageReaderOut: ImageReader? = null

    private var captureSessionOut: CameraCaptureSession? = null

    private var previewSizeOut: Size? = null

    private var supportedResolutionsIn: List<Size> = ArrayList()
    private var supportedResolutionsOut: List<Size> = ArrayList()

    private var selectedCameraIdOut: String? = null

    private var isPreviewActiveOut = false

    private var cameraManager: CameraManager? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private val cameraInfoMap: MutableMap<String, CameraInfo> = mutableMapOf()
    private val cameraIds: MutableList<String> = mutableListOf()

    // 方向转换
    private val ORIENTATIONS = SparseIntArray().apply {
        append(Surface.ROTATION_0, 90)
        append(Surface.ROTATION_90, 0)
        append(Surface.ROTATION_180, 270)
        append(Surface.ROTATION_270, 180)
    }

    // 相机信息类
    class CameraInfo internal constructor(var cameraId: String, var lensFacing: Int) {
        var displayName: String? = null

        init {
            when (lensFacing) {
                CameraCharacteristics.LENS_FACING_FRONT -> this.displayName = "前置摄像头:$cameraId"
                CameraCharacteristics.LENS_FACING_BACK -> this.displayName = "后置摄像头:$cameraId"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> this.displayName =
                    "外部摄像头:$cameraId"

                else -> this.displayName = "未知摄像头:$cameraId"
            }
        }

        override fun toString(): String {
            return displayName!!
        }
    }

    override fun isAutoCloseEnabled(): Boolean = false
    override fun layoutRes(): Int {
        return R.layout.nav_fragment_camera_out
    }

    override fun initialize(savedInstanceState: Bundle?) {
        initCamera2()
        setupCameras()
        cabinetVM.mainScope.launch {
            startPreview(2)
        }
        binding.actvReturn.setOnClickListener {
//            mActivity?.fragmentCoordinator?.navigateBack()
            super.performCloseAction()
        }
    }

    private fun toGoFaultDesc(desc: String) {
        cabinetVM.toGoCmdUpFault(FaultType.FAULT_CODE_5, 0, desc)
    }

    private fun setupCameras() {
        try {
            // 获取所有摄像头ID
            val cameraIdArray = cameraManager!!.cameraIdList
            cameraIds.clear()
            cameraInfoMap.clear()

            for (cameraId in cameraIdArray) {
                val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (lensFacing != null) {
                    val cameraInfo = CameraInfo(cameraId, lensFacing)
                    cameraInfoMap[cameraId] = cameraInfo
                    cameraIds.add(cameraId)
                }
            }

            if (cameraIds.isEmpty()) {
                Toast.makeText(AppUtils.getContext(), "未找到可用摄像头", Toast.LENGTH_SHORT).show()
                return
            }

            if (cameraIds.size > 1) {
                selectedCameraIdOut = cameraIds[1]
                selectedCameraIdOut?.let { sc ->
                    setupResolutionSpinner(sc, 2)
                }
            } else {
                Toast.makeText(AppUtils.getContext(), "摄像头访问错误", Toast.LENGTH_SHORT).show()
                toGoFaultDesc("摄像头访问异常")
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(AppUtils.getContext(), "摄像头访问错误", Toast.LENGTH_SHORT).show()
            toGoFaultDesc("摄像头访问异常")
        }
    }

    @SuppressLint("RestrictedApi")
    private fun setupResolutionSpinner(cameraId: String, cameraNum: Int) {
        try {
            val characteristics = cameraManager!!.getCameraCharacteristics(cameraId)
            val map =
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return

            // 获取支持的预览尺寸
            val sizes = map.getOutputSizes(SurfaceTexture::class.java)
            val supportedSizes = Arrays.asList(*sizes)

            // 按面积排序
            Collections.sort(supportedSizes, CompareSizesByArea())

            // 保存支持的尺寸
            if (cameraNum == 2) {
                supportedResolutionsOut = supportedSizes
            }
            // 创建分辨率选项
            val resolutionOptions: MutableList<String> = java.util.ArrayList()
            for (size in supportedSizes) {
                resolutionOptions.add(size.width.toString() + "x" + size.height)
            }

            // 设置默认选择（最高分辨率）
            if (!supportedSizes.isEmpty()) {

                // 保存默认预览尺寸
                if (cameraNum == 2) {
                    previewSizeOut = supportedSizes[0]
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            toGoFaultDesc("摄像获取尺寸异常")
        }
    }

    private val textureListenerOut: TextureView.SurfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                // 表面可用，可以开始预览
                if (isPreviewActiveOut) {
                    startPreview(2)
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                // 调整预览尺寸
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }
        }

    fun startPreview(cameraNum: Int) {
        if (ActivityCompat.checkSelfPermission(
                AppUtils.getContext(), CAMERA
            ) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(AppUtils.getContext(), "需要相机权限", Toast.LENGTH_SHORT).show()
            return
        }

        val cameraId = selectedCameraIdOut
        val previewSize = previewSizeOut

        if (cameraId == null || previewSize == null) {
            Toast.makeText(AppUtils.getContext(), "请先选择摄像头和分辨率", Toast.LENGTH_SHORT)
                .show()
            return
        }

        try {
            // 关闭当前相机（如果已打开）
            if (cameraNum == 2 && cameraDeviceOut != null) {
                cameraDeviceOut?.close()
                cameraDeviceOut = null
            }

            // 打开相机
            cameraManager!!.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (cameraNum == 2) {
                        cameraDeviceOut = camera
                        // 初始化 ImageReader（JPEG 格式）
                        imageReaderOut = ImageReader.newInstance(
                            previewSizeOut!!.width, previewSizeOut!!.height, ImageFormat.JPEG, 2
                        )
                        imageReaderOut?.setOnImageAvailableListener(
                            imageAvailableListenerOut, backgroundHandler
                        )

                        isPreviewActiveOut = true
//                        updatePreviewUI(1, true)
                        cameraDeviceOut?.let { createCameraPreviewSessionOut(it, previewSize) }
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraNum == 2) {
                        cameraDeviceOut = null
                        isPreviewActiveOut = false
//                        updatePreviewUI(2, false)
                    }
                    Toast.makeText(AppUtils.getContext(), "摄像头连接断开", Toast.LENGTH_SHORT)
                        .show()

                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraNum == 2) {
                        cameraDeviceOut = null
                        isPreviewActiveOut = false
                        toGoFaultDesc("摄像头打开失败外")

//                        updatePreviewUI(2, false)
                    }
                    Toast.makeText(AppUtils.getContext(), "摄像头打开失败", Toast.LENGTH_SHORT)
                        .show()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(AppUtils.getContext(), "摄像头访问错误", Toast.LENGTH_SHORT).show()
            toGoFaultDesc("摄像头访问错误")
        }
    }


    private fun createCameraPreviewSessionOut(cameraDevice: CameraDevice?, previewSize: Size) {
        try {
            val texture: SurfaceTexture? = binding.textureOut.surfaceTexture
            texture?.setDefaultBufferSize(previewSize.width, previewSize.height)

            texture?.let {
                val previewSurface = Surface(texture)

                val previewRequestBuilder1 =
                    cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                previewRequestBuilder1?.addTarget(previewSurface)

                // **同时绑定 ImageReader Surface**
                cameraDevice?.createCaptureSession(
                    Arrays.asList(
                        previewSurface, imageReaderOut!!.surface
                    ), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            captureSessionOut = session
                            try {
                                previewRequestBuilder1?.set(
                                    CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                captureSessionOut?.setRepeatingRequest(
                                    previewRequestBuilder1!!.build(), null, backgroundHandler
                                )
                            } catch (e: CameraAccessException) {
                                e.printStackTrace()
                                toGoFaultDesc("预览配置失败外")
                            }
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Toast.makeText(AppUtils.getContext(), "预览配置失败", Toast.LENGTH_SHORT)
                                .show()
                            toGoFaultDesc("预览配置失败外")

                        }
                    }, backgroundHandler
                )
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            toGoFaultDesc("预览配置失败外")

        }
    }

    /***
     * 打开外部
     */
    private val imageAvailableListenerOut = ImageReader.OnImageAvailableListener { reader ->
        val dir = File(
            AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action"
        )
        if (!dir.exists()) dir.mkdirs()
        val activeType = cabinetVM.activeType
        val fileName =
            "yuan_out_${activeType}_${cabinetVM.curTransId}__${AppUtils.getDateYMDHMS()}.jpg"
        val destFile = File(dir, fileName)
        val image = reader.acquireNextImage()
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer[bytes]

        try {
            FileOutputStream(destFile).use { output ->
                output.write(bytes)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            toGoFaultDesc("在图像上设置可用监听器失败外")
        } finally {
            image.close()
        }
    }


    fun stopPreview(cameraNum: Int) {

        if (cameraNum == 2 && cameraDeviceOut != null) {
            cameraDeviceOut?.close()
            cameraDeviceOut = null
            isPreviewActiveOut = false
//            updatePreviewUI(2, false)
        }
    }

    private fun initCamera2() {
        // 设置纹理视图监听器
        binding.textureOut.surfaceTextureListener = textureListenerOut
        // 获取相机管理器
        cameraManager = requireActivity()?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = backgroundThread?.looper?.let { Handler(it) }
    }
}
