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
import com.recycling.toolsapps.databinding.NavFragmentCameraInBinding
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
@AndroidEntryPoint class NavCameraInFragment : BaseBindLazyTimeFragment<NavFragmentCameraInBinding>() {
    // 关键点：通过 requireActivity() 获取 Activity 作用域的 ViewModel  // 确保共享实例
    private val cabinetVM: CabinetVM by viewModels(ownerProducer = { requireActivity() })
    private lateinit var permissionsManager: PermissionsRequester
    private var cameraDeviceIn: CameraDevice? = null

    private var imageReaderIn: ImageReader? = null

    private var captureSessionIn: CameraCaptureSession? = null

    private var previewSizeIn: Size? = null

    private var supportedResolutionsIn: List<Size> = ArrayList()

    private var selectedCameraIdIn: String? = null

    private var isPreviewActiveIn = false

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
        return R.layout.nav_fragment_camera_in
    }
    override fun initialize(savedInstanceState: Bundle?) {
        initCamera2()
        setupCameras()
        cabinetVM.mainScope.launch {
            startPreview(1)
        }
        binding.actvReturn.setOnClickListener {
            super.performCloseAction()
//            mActivity?.fragmentCoordinator?.navigateBack()
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

            // 默认选择第一个摄像头
            if (cameraIds.size > 0) {
                selectedCameraIdIn = cameraIds[0]
                selectedCameraIdIn?.let { sc ->
                    setupResolutionSpinner(sc, 1)
                }

            }else{
                Toast.makeText(AppUtils.getContext(), "摄像头访问错误", Toast.LENGTH_SHORT).show()
                toGoFaultDesc("摄像头访问异常")
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(AppUtils.getContext(), "摄像头访问错误", Toast.LENGTH_SHORT).show()
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
            if (cameraNum == 1) {
                supportedResolutionsIn = supportedSizes
            }

            // 创建分辨率选项
            val resolutionOptions: MutableList<String> = java.util.ArrayList()
            for (size in supportedSizes) {
                resolutionOptions.add(size.width.toString() + "x" + size.height)
            }

            // 设置默认选择（最高分辨率）
            if (!supportedSizes.isEmpty()) {

                // 保存默认预览尺寸
                if (cameraNum == 1) {
                    previewSizeIn = supportedSizes[0]
                }
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private val textureListenerIn: TextureView.SurfaceTextureListener =
            object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    // 表面可用，可以开始预览
                    if (isPreviewActiveIn) {
                        startPreview(1)
                    }
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    // 调整预览尺寸
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    return false
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                }
            }


    fun startPreview(cameraNum: Int) {
        if (ActivityCompat.checkSelfPermission(AppUtils.getContext(), CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(AppUtils.getContext(), "需要相机权限", Toast.LENGTH_SHORT).show()
            return
        }

        val cameraId =  selectedCameraIdIn
        val previewSize =  previewSizeIn

        if (cameraId == null || previewSize == null) {
            Toast.makeText(AppUtils.getContext(), "请先选择摄像头和分辨率", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 关闭当前相机（如果已打开）
            if (cameraNum == 1 && cameraDeviceIn != null) {
                cameraDeviceIn?.close()
                cameraDeviceIn = null
            }
            // 打开相机
            cameraManager!!.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (cameraNum == 1) {
                        cameraDeviceIn = camera
                        // 初始化 ImageReader（JPEG 格式）
                        imageReaderIn =
                                ImageReader.newInstance(previewSizeIn!!.width, previewSizeIn!!.height, ImageFormat.JPEG, 2)
                        imageReaderIn?.setOnImageAvailableListener(imageAvailableListenerIn, backgroundHandler)
                        isPreviewActiveIn = true
//                        updatePreviewUI(1, true)
                        cameraDeviceIn?.let { createCameraPreviewSessionIn(it, previewSize) }
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    if (cameraNum == 1) {
                        cameraDeviceIn = null
                        isPreviewActiveIn = false
//                        updatePreviewUI(1, false)
                    }
                    Toast.makeText(AppUtils.getContext(), "摄像头连接断开", Toast.LENGTH_SHORT).show()

                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    if (cameraNum == 1) {
                        cameraDeviceIn = null
                        isPreviewActiveIn = false
                    }
                    Toast.makeText(AppUtils.getContext(), "摄像头打开失败", Toast.LENGTH_SHORT).show()
                }
            }, backgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            Toast.makeText(AppUtils.getContext(), "摄像头访问错误", Toast.LENGTH_SHORT).show()
            toGoFaultDesc("摄像头访问错误")
        }
    }

    private fun createCameraPreviewSessionIn(cameraDevice: CameraDevice, previewSize: Size) {
        try {
            val texture: SurfaceTexture? = binding.textureIn.surfaceTexture
            texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            texture?.let {
                val previewSurface = Surface(texture)

                val previewRequestBuilder1 =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                previewRequestBuilder1.addTarget(previewSurface)

                // **同时绑定 ImageReader Surface**
                cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReaderIn!!.surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSessionIn = session
                        try {
                            previewRequestBuilder1.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            captureSessionIn?.setRepeatingRequest(previewRequestBuilder1.build(), null, backgroundHandler)
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                            toGoFaultDesc("摄像头访问错误内")

                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Toast.makeText(AppUtils.getContext(), "预览配置失败", Toast.LENGTH_SHORT).show()
                        toGoFaultDesc("预览配置失败内")
                    }
                }, backgroundHandler)
            }
      } catch (e: CameraAccessException) {
            e.printStackTrace()
            toGoFaultDesc("预览配置失败内")
        }
    }

    /**
     * 打开内部
     */
    private val imageAvailableListenerIn = ImageReader.OnImageAvailableListener { reader ->
        cabinetVM.ioScope.launch {
            val dir =
                    File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
            val activeType = cabinetVM.activeType
            if (!dir.exists()) dir.mkdirs()
            val fileName =
                    "yuan_in_${activeType}_${cabinetVM.curTransId}__${AppUtils.getDateYMDHMS()}.jpg"
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
                toGoFaultDesc("在图像上设置可用监听器失败内")
            } finally {
                image.close()
            }
        }
    }

    fun stopPreview(cameraNum: Int) {

        if (cameraNum == 1 && cameraDeviceIn != null) {
            cameraDeviceIn?.close()
            cameraDeviceIn = null
            isPreviewActiveIn = false
//            updatePreviewUI(1, false)
        }
    }

    private fun initCamera2() {
        // 设置纹理视图监听器
        binding.textureIn.surfaceTextureListener = textureListenerIn
        // 获取相机管理器
        cameraManager = requireActivity()?.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = backgroundThread?.looper?.let { Handler(it) }
    }
}
