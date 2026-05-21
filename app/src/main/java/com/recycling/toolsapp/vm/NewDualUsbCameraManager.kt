package com.recycling.toolsapp.vm

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.blankj.utilcode.util.ToastUtils
import com.serial.port.utils.AppUtils
import com.serial.port.utils.AsyncBatchLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class NewDualUsbCameraManager(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameras = ConcurrentHashMap<String, CameraHolder>()
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isReceiverRegistered = false

    private val cameraViews = ConcurrentHashMap<String, TextureView>()
    private val cameraStartPaused = ConcurrentHashMap<String, Boolean>()
    private val recoveringJobs = ConcurrentHashMap<String, Job>()
    private val recoverCountMap = ConcurrentHashMap<String, Int>()

    @Volatile
    private var isDestroying = false

    companion object {
        const val TAG = "DualUsbCamera"
        const val PREVIEW_WIDTH = 640
        const val PREVIEW_HEIGHT = 480
        const val PREVIEW_MAX_IMAGES = 2
        const val CAPTURE_JPEG_QUALITY = 90
        const val PREVIEW_RESUME_DELAY_MS = 800L
        const val CAPTURE_TIMEOUT_MS = 8000L
        const val SEQUENTIAL_CAPTURE_DELAY_MS = 1000L
        const val WATERMARK_TEXT_SIZE_RATIO = 40f
        const val WATERMARK_MARGIN_RATIO = 0.02f
        const val CAMERA_RECOVER_DELAY_MS = 2000L
        const val CAMERA_RECOVER_MAX_COUNT = 3
    }

    fun interface CameraErrorListener {
        fun cameraStatus(status: Boolean, index: String, text: String)
    }

    var cameraErrorListener: CameraErrorListener? = null

    data class PhotoRequest(
        val cameraId: String,
        val switchType: Int,
        val inOut: String,
        val saveFile: File,
        val remoteOpenType: Int,
    )

    private inner class CameraHolder(val cameraId: String) {
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var reader: ImageReader? = null
        var thread: HandlerThread? = null
        var handler: Handler? = null
        var previewSurface: Surface? = null
        var isPreviewing = false
        var isCapturing = false
        var isReleased = false
        val captureMutex = Mutex()

        fun release() {
            if (isReleased) return
            isReleased = true
            isPreviewing = false
            isCapturing = false

            val oldSession = session
            val oldDevice = device
            val oldReader = reader
            val oldSurface = previewSurface
            val oldThread = thread

            session = null
            device = null
            reader = null
            previewSurface = null
            handler = null
            thread = null

            runCatching { oldReader?.setOnImageAvailableListener(null, null) }

            runCatching { oldSession?.stopRepeating() }
                .onFailure { AsyncBatchLogger.logBusiness("业务流", "停止预览异常[$cameraId]: ${it.message}") }

            runCatching { oldSession?.abortCaptures() }
                .onFailure { AsyncBatchLogger.logBusiness("业务流", "中止拍照异常[$cameraId]: ${it.message}") }

            runCatching { oldSession?.close() }
            runCatching { oldDevice?.close() }
            runCatching { oldReader?.close() }
            runCatching { oldSurface?.release() }
            runCatching { oldThread?.quitSafely() }
        }
    }

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
            try {
                appContext.unregisterReceiver(usbReceiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
        }
    }

    fun autoStartUsbCameras(
        openAll: Boolean = true,
        view1: TextureView? = null,
        view2: TextureView? = null,
        delayMs: Long = 3000,
        startPaused: Boolean = false,
        listener: CameraErrorListener,
    ) {
        isDestroying = false
        cameraErrorListener = listener
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        scope.launch {
            val usbIds = getExternalCameraIds()
            if (usbIds.isEmpty()) {
                AsyncBatchLogger.logBusiness("业务流", "未检测到外置 USB 摄像头")
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

    fun startDualCamerasSequentialOpen(
        view1: TextureView,
        view2: TextureView,
        startPaused: Boolean = false,
        listener: CameraErrorListener,
    ) {
        isDestroying = false
        cameraErrorListener = listener
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        scope.launch {
            val usbIds = getExternalCameraIds()
            var open1 = false
            var openId1 = ""
            var open2 = false
            var openId2 = ""

            if (usbIds.isNotEmpty()) {
                open1 = openSingleCamera(usbIds[0], view1, startPaused)
                openId1 = usbIds[0]
            }

            if (usbIds.size > 1) {
                open2 = openSingleCamera(usbIds[1], view2, startPaused)
                openId2 = usbIds[1]
            }

            AsyncBatchLogger.logBusiness("业务流", "双摄像头顺序开启 open1:$open1=$openId1 | open2:$open2=$openId2")
        }
    }

    fun destroy() {
        isDestroying = true
        cameraErrorListener = null
        unregisterUsbReceiver()

        recoveringJobs.values.forEach { it.cancel() }
        recoveringJobs.clear()

        cameras.values.forEach { it.release() }
        cameras.clear()

        cameraViews.clear()
        cameraStartPaused.clear()
        recoverCountMap.clear()

        scope.cancel()
    }

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
        if (holder.isReleased) return

        val device = holder.device ?: return
        val session = holder.session ?: return
        val surface = holder.previewSurface ?: return

        if (!holder.isPreviewing) {
            try {
                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(surface)

                val characteristics = manager.getCameraCharacteristics(holder.cameraId)
                val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                builder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    if (afModes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true) {
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    } else {
                        CaptureRequest.CONTROL_AF_MODE_OFF
                    }
                )

                session.setRepeatingRequest(builder.build(), null, holder.handler)
                holder.isPreviewing = true
            } catch (e: Exception) {
                cameraErrorListener?.cameraStatus(false, cameraId, "恢复失败")
                scheduleRecoverCamera(cameraId)
            }
        }
    }

    suspend fun takePicturesSequential(requests: List<PhotoRequest>): List<File?> {
        val results = mutableListOf<File?>()

        for (req in requests) {
            val file = try {
                takePictureSuspend(
                    cameraId = req.cameraId,
                    switchType = req.switchType,
                    inOut = req.inOut,
                    saveFile = req.saveFile,
                    remoteOpenType = req.remoteOpenType
                )
            } catch (e: Exception) {
                AsyncBatchLogger.logBusiness("业务流", "[${req.cameraId}] 顺序拍照失败: ${e.message}")
                null
            }

            results.add(file)
            delay(SEQUENTIAL_CAPTURE_DELAY_MS)
        }

        return results
    }

    suspend fun takePicturesParallel(requests: List<PhotoRequest>): List<File?> {
        return takePicturesSequential(requests)
    }

    suspend fun takePictureSuspend(
        cameraId: String,
        switchType: Int,
        inOut: String,
        saveFile: File,
        remoteOpenType: Int,
    ): File? {
        val holder = cameras[cameraId] ?: run {
            AsyncBatchLogger.logBusiness("业务流", "[$cameraId] 拍照失败：摄像头未打开")
            return null
        }

        return holder.captureMutex.withLock {
            val result = withTimeoutOrNull(CAPTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine<File?> { cont ->
                    takePicture(cameraId, switchType, inOut, saveFile, remoteOpenType) { file ->
                        if (cont.isActive) cont.resume(file)
                    }

                    cont.invokeOnCancellation {
                        holder.reader?.setOnImageAvailableListener(null, null)
                        holder.isCapturing = false
                    }
                }
            }

            if (result == null) {
                holder.reader?.setOnImageAvailableListener(null, null)
                holder.isCapturing = false
                AsyncBatchLogger.logBusiness("业务流", "[$cameraId] 拍照失败或超时")
                cameraErrorListener?.cameraStatus(false, cameraId, "拍照失败或超时")
            }

            result
        }
    }

    fun takePicture(
        cameraId: String,
        switchType: Int = -1,
        inOut: String,
        saveFile: File,
        remoteOpenType: Int,
        onComplete: (File?) -> Unit,
    ) {
        val holder = cameras[cameraId]
        if (holder == null || holder.isCapturing) {
            AsyncBatchLogger.logBusiness("业务流", "[$cameraId] 拍照异常：摄像头忙或未打开")
            onComplete(null)
            return
        }

        holder.isCapturing = true

        scope.launch {
            try {
                if (!holder.isPreviewing) {
                    resumeSingleCamera(cameraId, holder)
                    AsyncBatchLogger.logBusiness("业务流", "[$cameraId] 拍照 重新预览")
                    delay(PREVIEW_RESUME_DELAY_MS)
                }

                val imageFile = captureImageInternal(cameraId, inOut, switchType, holder, saveFile, remoteOpenType)

                withContext(Dispatchers.Main) {
                    onComplete(imageFile)
                }
            } catch (e: Exception) {
                AsyncBatchLogger.logBusiness("业务流", "[$cameraId] 拍照异常: ${e.message}")
                withContext(Dispatchers.Main) {
                    onComplete(null)
                }
            } finally {
                holder.isCapturing = false
            }
        }
    }

    private suspend fun captureImageInternal(
        cameraId: String,
        inOut: String,
        switchType: Int = -1,
        holder: CameraHolder,
        saveFile: File,
        remoteOpenType: Int,
    ): File? = suspendCancellableCoroutine { cont ->
        try {
            val device = holder.device
            val session = holder.session
            val reader = holder.reader
            if (holder.isReleased) {
                if (device == null || session == null || reader == null) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
            }

            if (reader != null) {
                clearPendingImages(reader)
            }

            val builder = device?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            if (reader != null) {
                if (builder != null) {
                    builder.addTarget(reader.surface)
                }
            }

            if (reader != null) {
                reader.setOnImageAvailableListener({ imageReader ->
                    imageReader.setOnImageAvailableListener(null, null)

                    val img = imageReader.acquireLatestImage()
                    if (img == null) {
                        if (holder.isReleased) {
                            if (cont.isActive) cont.resume(null)
                            return@setOnImageAvailableListener
                        }
                    }

                    val bytes = try {
                        val buffer = img.planes[0].buffer
                        ByteArray(buffer.remaining()).also { buffer.get(it) }
                    } finally {
                        img.close()
                    }

                    scope.launch(Dispatchers.IO) {
                        val text = when (remoteOpenType) {
                            1 -> when (switchType) {
                                1 -> "投递前"
                                0 -> "投递后"
                                else -> "远程拍照"
                            }

                            2 -> when (switchType) {
                                1 -> "清运前"
                                0 -> "清运后"
                                else -> "远程拍照"
                            }

                            else -> "远程拍照"
                        }

                        val watermarkText = "$text-$inOut-${AppUtils.getDateYMDHMS2()}"
                        val finalPath = saveImageWithWatermarkSync(bytes, saveFile, watermarkText)

                        if (cont.isActive) {
                            if (finalPath != null) {
                                val resultFile = File(finalPath)
                                if (resultFile.exists() && resultFile.length() > 0) {
                                    AsyncBatchLogger.logBusiness("业务流", "拍照完成[${cameraId}]")
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
            }

            if (builder != null) {
                session?.capture(builder.build(), null, holder.handler)
            }
        } catch (e: Exception) {
            holder.reader?.setOnImageAvailableListener(null, null)
            AsyncBatchLogger.logBusiness("业务流", "[$cameraId] capture异常: ${e.message}")
            if (cont.isActive) cont.resume(null)
        }
    }

    private fun clearPendingImages(reader: ImageReader) {
        try {
            while (true) {
                val image = reader.acquireLatestImage() ?: break
                image.close()
            }
        } catch (_: Exception) {
        }
    }

    private fun saveImageWithWatermarkSync(imageBytes: ByteArray, destFile: File, watermarkText: String): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inMutable = true
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                ?: return null

            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                textSize = bitmap.width / WATERMARK_TEXT_SIZE_RATIO
                setShadowLayer(3f, 2f, 2f, Color.BLACK)
            }

            val margin = bitmap.width * WATERMARK_MARGIN_RATIO
            canvas.drawText(watermarkText, margin, margin + paint.textSize, paint)

            if (destFile.exists()) destFile.delete()

            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, CAPTURE_JPEG_QUALITY, out)
                out.flush()
                out.fd.sync()
            }

            bitmap.recycle()
            destFile.absolutePath
        } catch (e: Exception) {
            AsyncBatchLogger.logBusiness("业务流", "水印写入失败: ${e.message}")
            null
        }
    }

    fun getExternalCameraIds(): List<String> {
        val externalIds = mutableListOf<String>()
        try {
            for (id in manager.cameraIdList) {
                if (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) != null) {
                    externalIds.add(id)
                }
            }
        } catch (e: Exception) {
            AsyncBatchLogger.logBusiness("业务流", "获取摄像头异常: ${e.message}")
        }
        return externalIds
    }

    @SuppressLint("MissingPermission")
    private suspend fun openSingleCamera(
        cameraId: String,
        textureView: TextureView,
        startPaused: Boolean,
    ): Boolean = suspendCancellableCoroutine { cont ->
        if (cameras.containsKey(cameraId)) {
            if (cont.isActive) cont.resume(true)
            return@suspendCancellableCoroutine
        }

        val holder = CameraHolder(cameraId)
        cameras[cameraId] = holder
        cameraViews[cameraId] = textureView
        cameraStartPaused[cameraId] = startPaused

        val thread = HandlerThread("Cam-$cameraId").apply { start() }
        holder.thread = thread
        holder.handler = Handler(thread.looper)

        try {
            holder.reader = ImageReader.newInstance(
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT,
                ImageFormat.JPEG,
                PREVIEW_MAX_IMAGES
            )

            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    holder.device = camera
                    initPreviewSession(
                        cameraId = cameraId,
                        holder = holder,
                        textureView = textureView,
                        previewSize = Size(PREVIEW_WIDTH, PREVIEW_HEIGHT),
                        startPaused = startPaused,
                        onResult = { success ->
                            if (cont.isActive) cont.resume(success)
                        }
                    )
                }

                override fun onDisconnected(camera: CameraDevice) {
                    holder.release()
                    cameras.remove(cameraId)
                    AsyncBatchLogger.logBusiness("业务流", "摄像头断开异常 [${cameraId}]")
                    if (cont.isActive) cont.resume(false)
                    scheduleRecoverCamera(cameraId)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    holder.release()
                    cameras.remove(cameraId)
                    AsyncBatchLogger.logBusiness("业务流", "摄像头打开异常 [${cameraId}]")
                    if (cont.isActive) cont.resume(false)
                    scheduleRecoverCamera(cameraId)
                }
            }, holder.handler)
        } catch (e: Exception) {
            holder.release()
            cameras.remove(cameraId)
            AsyncBatchLogger.logBusiness("业务流", "打开摄像头[$cameraId]异常: ${e.message}")
            if (cont.isActive) cont.resume(false)
        }
    }

    private fun scheduleRecoverCamera(cameraId: String) {
        if (isDestroying) return
        if (recoveringJobs[cameraId]?.isActive == true) return

        val view = cameraViews[cameraId]
        if (view == null) {
            AsyncBatchLogger.logBusiness("业务流", "摄像头[$cameraId]恢复失败：TextureView为空")
            return
        }

        val count = recoverCountMap[cameraId] ?: 0
        if (count >= CAMERA_RECOVER_MAX_COUNT) {
            AsyncBatchLogger.logBusiness("业务流", "摄像头[$cameraId]恢复超过最大次数")
            cameraErrorListener?.cameraStatus(false, cameraId, "摄像头恢复失败，请检查设备")
            return
        }

        recoverCountMap[cameraId] = count + 1

        recoveringJobs[cameraId] = scope.launch {
            AsyncBatchLogger.logBusiness("业务流", "摄像头[$cameraId]准备第${count + 1}次恢复")
            delay(CAMERA_RECOVER_DELAY_MS)

            if (isDestroying) return@launch

            cameras.remove(cameraId)?.release()

            if (!view.isAvailable) {
                AsyncBatchLogger.logBusiness("业务流", "摄像头[$cameraId]恢复失败：Surface不可用")
                return@launch
            }

            val startPaused = cameraStartPaused[cameraId] ?: false
            val success = openSingleCamera(cameraId, view, startPaused)

            if (success) {
                recoverCountMap[cameraId] = 0
                AsyncBatchLogger.logBusiness("业务流", "摄像头[$cameraId]恢复成功")
                cameraErrorListener?.cameraStatus(true, cameraId, "摄像头恢复成功")
            } else {
                AsyncBatchLogger.logBusiness("业务流", "摄像头[$cameraId]恢复失败，继续重试")
                scheduleRecoverCamera(cameraId)
            }
        }
    }

    private fun initPreviewSession(
        cameraId: String,
        holder: CameraHolder,
        textureView: TextureView,
        previewSize: Size,
        startPaused: Boolean,
        onResult: (Boolean) -> Unit,
    ) {
        val device = holder.device ?: run {
            onResult(false)
            return
        }

        val startSession = { surfaceTexture: SurfaceTexture ->
            try {
                surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                holder.previewSurface = Surface(surfaceTexture)

                val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(holder.previewSurface!!)
                }

                val outputSurfaces = mutableListOf<Surface>().apply {
                    add(holder.previewSurface!!)
                    holder.reader?.surface?.let { add(it) }
                }

                device.createCaptureSession(outputSurfaces, object :
                    CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (holder.isReleased || holder.device == null) {
                            runCatching { session.close() }
                            onResult(false)
                            return
                        }

                        holder.session = session

                        try {
                            if (!startPaused) {
                                session.setRepeatingRequest(builder.build(), null, holder.handler)
                                holder.isPreviewing = true
                            }
                            recoverCountMap[cameraId] = 0
                            onResult(true)
                        } catch (e: Exception) {
                            AsyncBatchLogger.logBusiness("业务流", "摄像头[$cameraId]启动预览异常: ${e.message}")
                            onResult(false)
                            scheduleRecoverCamera(cameraId)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        AsyncBatchLogger.logBusiness("业务流", "摄像头[$cameraId]配置会话失败")
                        cameraErrorListener?.cameraStatus(false, cameraId, "配置会话失败")
                        onResult(false)
                    }
                }, holder.handler)
            } catch (e: CameraAccessException) {
                AsyncBatchLogger.logBusiness("业务流", "创建会话[$cameraId]异常: ${e.message}")
                cameraErrorListener?.cameraStatus(false, cameraId, "创建会话异常")
                onResult(false)
            } catch (e: IllegalArgumentException) {
                AsyncBatchLogger.logBusiness("业务流", "[$cameraId]分辨率或 Surface 异常: ${e.message}")
                cameraErrorListener?.cameraStatus(false, cameraId, "分辨率或 Surface 异常")
                onResult(false)
            }
        }

        if (textureView.isAvailable) {
            startSession(textureView.surfaceTexture!!)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    startSession(surfaceTexture)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    holder.release()
                    cameras.remove(cameraId)
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                if (device?.deviceClass == UsbConstants.USB_CLASS_VIDEO ||
                    device?.deviceClass == UsbConstants.USB_CLASS_MISC
                ) {
                    cameras.values.forEach { it.release() }
                    cameras.clear()
                    ToastUtils.showLong("摄像头已断开")
                }
            }
        }
    }
}
