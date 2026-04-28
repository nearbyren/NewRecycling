package com.recycling.toolsapp.vm

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.blankj.utilcode.util.ToastUtils
import com.serial.port.utils.AppUtils
import com.serial.port.utils.BoxToolLogUtils
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume


class NewDualUsbCameraManager(context: Context) {

    val TAG = "DualUsbCamera"
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameras = ConcurrentHashMap<String, CameraHolder>()
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isReceiverRegistered = false

    private inner class CameraHolder(val cameraId: String) {
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var reader: ImageReader? = null
        var thread: HandlerThread? = null
        var handler: Handler? = null
        var previewSurface: Surface? = null
        var isPreviewing = false
        var isCapturing = false

        fun release() {
            try {
                isPreviewing = false
                isCapturing = false
                session?.stopRepeating()
                session?.abortCaptures()
                session?.close()
                device?.close()
                reader?.close()
                previewSurface?.release()
                thread?.quitSafely()
            } catch (e: Exception) {
                BoxToolLogUtils.saveCamera("释放摄像头 $cameraId 异常: ${e.message}")
            } finally {
                session = null; device = null; reader = null
                previewSurface = null; thread = null; handler = null
            }
        }
    }

    // ================== 生命周期管理 ==================

    fun registerUsbReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            appContext.registerReceiver(usbReceiver, filter)
            isReceiverRegistered = true
        }
    }

    fun unregisterUsbReceiver() {
        if (isReceiverRegistered) {
            try { appContext.unregisterReceiver(usbReceiver) } catch (e: Exception) {}
            isReceiverRegistered = false
        }
    }

    fun interface CameraErrorListener {
        fun cameraStatus(status: Boolean, index: String, text: String)
    }

    var cameraErrorListener: CameraErrorListener? = null

    // ================== 启动逻辑 ==================

    /**
     * 原逻辑保留：顺序启动或根据 openAll 启动
     */
    fun autoStartUsbCameras(openAll: Boolean = true, view1: TextureView? = null, view2: TextureView? = null, delayMs: Long = 3000, startPaused: Boolean = false, listener: CameraErrorListener) {
        cameraErrorListener = listener
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        scope.launch {
            val usbIds = getExternalCameraIds()
            if (usbIds.isEmpty()) {
                BoxToolLogUtils.saveCamera("未检测到外置 USB 摄像头")
                return@launch
            }

            if (openAll) {
                if (view1 != null) openSingleCamera(usbIds[0], view1, startPaused)
                if (usbIds.size >= 2 && view2 != null) {
                    delay(delayMs)
                    openSingleCamera(usbIds[1], view2, startPaused)
                }
            } else {
                if (view1 != null) openSingleCamera(usbIds[0], view1, startPaused)
                else if (usbIds.size >= 2 && view2 != null) openSingleCamera(usbIds[1], view2, startPaused)
            }
        }
    }

    /**
     * 新增：并行启动双摄像头 (提高启动速度)
     */
    fun startDualCamerasParallel(view1: TextureView, view2: TextureView, startPaused: Boolean = false, listener: CameraErrorListener) {
        cameraErrorListener = listener
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        scope.launch {
            val usbIds = getExternalCameraIds()
            if (usbIds.size < 2) {
                cameraErrorListener?.cameraStatus(false, "all", "摄像头数量不足，无法并行开启")
                return@launch
            }
            // 使用 async 同时发起开启请求
            val task1 = async { openSingleCamera(usbIds[0], view1, startPaused) }
            val task2 = async { openSingleCamera(usbIds[1], view2, startPaused) }
            awaitAll(task1, task2)
            BoxToolLogUtils.saveCamera("双摄像头并行开启请求已完成")
        }
    }

    fun destroy() {
        cameraErrorListener = null
        unregisterUsbReceiver()
        cameras.values.forEach { it.release() }
        cameras.clear()
        scope.cancel()
    }

    // ================== 预览控制 ==================

    fun pausePreview(cameraId: String? = null) {
        val targets = if (cameraId != null) listOfNotNull(cameras[cameraId]) else cameras.values
        targets.forEach { holder ->
            if (holder.isPreviewing) {
                try {
                    holder.session?.stopRepeating()
                    holder.isPreviewing = false
                } catch (e: Exception) {
                    cameraErrorListener?.cameraStatus(false, holder.cameraId, "暂停失败")
                }
            }
        }
    }

    fun resumePreview(cameraId: String? = null) {
        val targets = if (cameraId != null) listOfNotNull(cameras[cameraId]) else cameras.values
        targets.forEach { holder -> resumeSingleCamera(holder.cameraId, holder) }
    }

    private fun resumeSingleCamera(cameraId: String, holder: CameraHolder) {
        if (!holder.isPreviewing && holder.session != null && holder.previewSurface != null) {
            try {
                val builder = holder.device?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder?.addTarget(holder.previewSurface!!)

                val characteristics = manager.getCameraCharacteristics(holder.cameraId)
                val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                builder?.set(CaptureRequest.CONTROL_AF_MODE,
                    if (afModes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true)
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE else CaptureRequest.CONTROL_AF_MODE_OFF)

                holder.session?.setRepeatingRequest(builder!!.build(), null, holder.handler)
                holder.isPreviewing = true
            } catch (e: Exception) {
                cameraErrorListener?.cameraStatus(false, cameraId, "恢复失败")
            }
        }
    }

    // ================== 拍照控制 (并行增强) ==================

    /**
     * 新增：支持同时拍摄多个摄像头
     * @param requests 包含 (cameraId, switchType, inOut, saveFile) 的列表
     */
    suspend fun takePicturesParallel(requests: List<PhotoRequest>): List<File?> = coroutineScope {
        requests.map { req ->
            async(Dispatchers.IO) {
                delay(1000)
                takePictureSuspend(req.cameraId, req.switchType, req.inOut, req.saveFile, req.remoteOpenType)

            }
        }.awaitAll()
    }

    data class PhotoRequest(val cameraId: String, val switchType: Int, val inOut: String, val saveFile: File, val remoteOpenType: Int)

    /**
     * 挂起函数版拍照
     */
    suspend fun takePictureSuspend(cameraId: String, switchType: Int, inOut: String, saveFile: File, remoteOpenType: Int): File? =
        suspendCancellableCoroutine { cont ->
            this.takePicture(cameraId, switchType, inOut, saveFile, remoteOpenType) { file ->
                if (cont.isActive) cont.resume(file)
            }
        }

    /**
     * 基础拍照逻辑
     */
    fun takePicture(cameraId: String, switchType: Int = -1, inOut: String, saveFile: File, remoteOpenType: Int, onComplete: (File?) -> Unit) {
        val holder = cameras[cameraId]
        if (holder == null || holder.isCapturing) {
            onComplete(null)
            return
        }

        holder.isCapturing = true

        scope.launch {
            try {
                if (!holder.isPreviewing) {
                    resumeSingleCamera(cameraId, holder)
                    delay(800)
                }

                val imageFile = captureImageInternal(cameraId, inOut, switchType, holder, saveFile, remoteOpenType)
                withContext(Dispatchers.IO) { onComplete(imageFile) }
            } catch (e: Exception) {
                BoxToolLogUtils.saveCamera("[$cameraId] 拍照异常: ${e.message}")
                withContext(Dispatchers.Main) { onComplete(null) }
            } finally {
                holder.isCapturing = false
            }
        }
    }

    private suspend fun captureImageInternal(cameraId: String, inOut: String, switchType: Int = -1, holder: CameraHolder, saveFile: File, remoteOpenType: Int): File? =
        suspendCancellableCoroutine { cont ->
            try {
                val builder = holder.device?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                builder?.addTarget(holder.reader!!.surface)

                holder.reader?.setOnImageAvailableListener({ reader ->
                    reader.setOnImageAvailableListener(null, null)
                    val img = reader.acquireLatestImage() ?: run {
                        if (cont.isActive) cont.resume(null)
                        return@setOnImageAvailableListener
                    }

                    val buffer = img.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    img.close()

                    scope.launch(Dispatchers.IO) {
                        var text = ""
                        if (remoteOpenType == 1) {
                            text = when (switchType) {
                                1 -> "投递前"
                                0 -> "投递后"
                                else -> "远程拍照"
                            }

                        } else if (remoteOpenType == 2) {
                            text = when (switchType) {
                                1 -> "清运前"
                                0 -> "清运后"
                                else -> "远程拍照"
                            }
                        }
                        val watermarkText = "$text-$inOut-${AppUtils.getDateYMDHMS2()}"
                        val finalPath = saveImageWithWatermarkSync(bytes, saveFile, watermarkText)
                        if (cont.isActive) {
                            if (finalPath != null) {
                                val resultFile = File(finalPath)
                                if (resultFile.exists() && resultFile.length() > 0) {
                                    BoxToolLogUtils.saveCamera("[$cameraId] 物理落盘成功: $finalPath (${resultFile.length()} bytes)")
                                    cameraErrorListener?.cameraStatus(true, cameraId, "拍照完成")
                                    cont.resume(resultFile)
                                } else {
                                    cont.resume(null)
                                }
                            } else {
                                cont.resume(null)
                            }
                        }
                    }
                }, holder.handler)

                holder.session?.capture(builder!!.build(), null, holder.handler)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun saveImageWithWatermarkSync(imageBytes: ByteArray, destFile: File, watermarkText: String): String? {
        return try {
            // 1. 解码
            val options = BitmapFactory.Options().apply {
                inMutable = true
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                ?: return null

            // 2. 绘图
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                textSize = bitmap.width / 40f
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
            }
            val margin = bitmap.width * 0.02f
            canvas.drawText(watermarkText, margin, margin + 40f, paint)

            // 3. 写入并物理同步
            // 注意：destFile 应该在 cacheDir 下，避免权限问题
            if (destFile.exists()) destFile.delete()

            FileOutputStream(destFile).use { out ->
                // 建议质量设为 90，100 会导致文件体积剧增且无明显画质提升
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                // 【核心】强制将内核缓冲区数据同步到存储介质，确保文件流彻底关闭且释放占用
                out.fd.sync()
            }

            // 4. 回收内存
            bitmap.recycle()
            destFile.absolutePath
        } catch (e: Exception) {
            BoxToolLogUtils.saveCamera("水印写入失败: ${e.message}")
            null
        }
    }

    // ================== 初始化辅助 ==================

     fun getExternalCameraIds(): List<String> {
        val externalIds = mutableListOf<String>()
        try {
            for (id in manager.cameraIdList) {
                if (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) != null) {
                    externalIds.add(id)
                }
            }
        } catch (e: Exception) { }
        return externalIds
    }

    @SuppressLint("MissingPermission")
    private suspend fun openSingleCamera(cameraId: String, textureView: TextureView, startPaused: Boolean) =
        suspendCancellableCoroutine<Unit> { cont ->
            if (cameras.containsKey(cameraId)) {
                if (cont.isActive) cont.resume(Unit)
                return@suspendCancellableCoroutine
            }

            val holder = CameraHolder(cameraId)
            cameras[cameraId] = holder
            val thread = HandlerThread("Cam-$cameraId").apply { start() }
            holder.thread = thread
            holder.handler = Handler(thread.looper)

            try {
                holder.reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        holder.device = camera
                        initPreviewSession(cameraId, holder, textureView, Size(640, 480), startPaused)
                        if (cont.isActive) cont.resume(Unit)
                    }
                    override fun onDisconnected(camera: CameraDevice) { holder.release(); cameras.remove(cameraId) }
                    override fun onError(camera: CameraDevice, error: Int) {
                        holder.release(); cameras.remove(cameraId)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }, holder.handler)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Unit)
            }
        }

    private fun initPreviewSession(cameraId: String, holder: CameraHolder, textureView: TextureView, previewSize: Size, startPaused: Boolean) {
        // 1. 安全检查：确保设备没有断开
        val device = holder.device ?: return

        val startSession = { surfaceTexture: SurfaceTexture ->
            try {
                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                holder.previewSurface = Surface(surfaceTexture)

                // 2. 构造请求：明确指定预览模板
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(holder.previewSurface!!)
                    // 降低 USB 带宽压力：如果可以，稍微降低预览的帧率
//                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(10, 20))
                }

                // 3. 准备输出配置（适配 API 级别）
                val outputSurfaces = mutableListOf<Surface>().apply {
                    add(holder.previewSurface!!)
                    holder.reader?.surface?.let { add(it) }
                }

                // 4. 创建会话（加入异常捕获）
                device.createCaptureSession(outputSurfaces, object :
                    CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (holder.device == null) return // 过程中设备可能断开了
                        holder.session = session
                        try {
                            if (!startPaused) {
                                session.setRepeatingRequest(builder.build(), null, holder.handler)
                                holder.isPreviewing = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        BoxToolLogUtils.savePush2("摄像头 $cameraId 配置会话失败")
                    }
                }, holder.handler)

            } catch (e: CameraAccessException) {
                BoxToolLogUtils.savePush2("创建会话异常: ${e.message}")
                cameraErrorListener?.cameraStatus(false, "all", "创建会话异常")
            } catch (e: IllegalArgumentException) {
                BoxToolLogUtils.savePush2("分辨率或 Surface 异常: ${e.message}")
                cameraErrorListener?.cameraStatus(false, "all", "分辨率或 Surface 异常")
            }
        }

        if (textureView.isAvailable) {
            startSession(textureView.surfaceTexture!!)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                    startSession(p0)
                }

                override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
                override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
            }
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                if (device?.deviceClass == UsbConstants.USB_CLASS_VIDEO || device?.deviceClass == UsbConstants.USB_CLASS_MISC) {
                    destroy()
                    ToastUtils.showLong("摄像头已断开")
                }
            }
        }
    }
}