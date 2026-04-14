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
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.blankj.utilcode.util.ToastUtils
import com.serial.port.utils.AppUtils
import com.serial.port.utils.BoxToolLogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume


class NewDualUsbCameraManager(context: Context) {

    val TAG = "DualUsbCamera"

    // 1. 强制使用 ApplicationContext，彻底阻断 Activity 内存泄漏
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // 2. 线程安全的 Map，防止底层回调与主线程并发操作导致崩溃
    private val cameras = ConcurrentHashMap<String, CameraHolder>()

    // 3. SupervisorJob 确保单个协程崩溃不会摧毁整个作用域
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isReceiverRegistered = false

    private inner class CameraHolder(val cameraId: String) {
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var reader: ImageReader? = null
        var thread: HandlerThread? = null
        var handler: Handler? = null
        var previewSurface: Surface? = null

        // 状态标记
        var isPreviewing = false
        var isCapturing = false // 防连击锁

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
            try {
                appContext.unregisterReceiver(usbReceiver)
            } catch (e: Exception) {
            }
            isReceiverRegistered = false
        }
    }

    fun interface CameraErrorListener {
        fun cameraStatus(status: Boolean, index: String, text: String)
    }

    var cameraErrorListener: CameraErrorListener? = null


    /**
     * 自动识别并启动 USB 摄像头
     * @param startPaused 是否在启动后直接进入暂停休眠状态（默认 false，启动即预览）
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
                BoxToolLogUtils.saveCamera("启动第一个 USB 摄像头: ${usbIds[0]}")
                if (view1 != null) {
                    openSingleCamera(usbIds[0], view1, startPaused)
                }


                if (usbIds.size >= 2 && view2 != null) {
                    delay(delayMs)
                    BoxToolLogUtils.saveCamera("启动第二个 USB 摄像头: ${usbIds[1]}")
                    openSingleCamera(usbIds[1], view2, startPaused)
                } else {
                    BoxToolLogUtils.saveCamera("只检测到一个外置摄像头")
                    cameraErrorListener?.cameraStatus(false, "1", "只有一个摄像头")
                }
            } else {
                if (view1 != null) {
                    BoxToolLogUtils.saveCamera("启动第一个 USB 摄像头: ${usbIds[0]}")
                    openSingleCamera(usbIds[0], view1, startPaused)
                } else {
                    if (usbIds.size >= 2 && view2 != null) {
                        BoxToolLogUtils.saveCamera("启动第二个 USB 摄像头: ${usbIds[1]}")
                        openSingleCamera(usbIds[1], view2, startPaused)
                    } else {
                        BoxToolLogUtils.saveCamera("只检测到一个外置摄像头")
                        cameraErrorListener?.cameraStatus(false, "1", "只有一个摄像头")
                    }
                }
            }
        }
    }

    fun destroy() {
        cameraErrorListener = null
        unregisterUsbReceiver()
        cameras.values.forEach { it.release() }
        cameras.clear()
        scope.cancel()
        BoxToolLogUtils.saveCamera("相机资源已彻底销毁")
    }

    // ================== 公开的暂停/恢复 API ==================

    /**
     * 暂停预览（画面定格，停止耗电，但不销毁硬件连接）
     * @param cameraId 如果为 null，则暂停所有摄像头
     */
    fun pausePreview(cameraId: String? = null) {
        val targets = if (cameraId != null) listOfNotNull(cameras[cameraId]) else cameras.values
        targets.forEach { holder ->
            if (holder.isPreviewing) {
                try {
                    holder.session?.stopRepeating()
                    holder.isPreviewing = false
                    BoxToolLogUtils.saveCamera("摄像头 ${holder.cameraId} 已暂停预览，进入休眠状态")
                } catch (e: Exception) {
                    BoxToolLogUtils.saveCamera("摄像头 ${holder.cameraId} 暂停失败: ${e.message}")
                    cameraErrorListener?.cameraStatus(
                        false, cameraId ?: "0", "暂停${cameraId}摄像头失败"
                    )
                }
            }
        }
    }

    /**
     * 恢复预览（唤醒相机，画面恢复实时）
     * @param cameraId 如果为 null，则恢复所有摄像头
     */
    fun resumePreview(cameraId: String? = null) {
        val targets = if (cameraId != null) listOfNotNull(cameras[cameraId]) else cameras.values
        targets.withIndex().forEach { (index, holder) -> resumeSingleCamera(index.toString(), holder) }
    }

    private fun resumeSingleCamera(index: String, holder: CameraHolder) {
        if (!holder.isPreviewing && holder.session != null && holder.previewSurface != null) {
            try {
                val builder = holder.device?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder?.addTarget(holder.previewSurface!!)

                // 恢复时也需要判断定焦适配
                val characteristics = manager.getCameraCharacteristics(holder.cameraId)
                val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                if (afModes != null && afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                    builder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                } else {
                    builder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                }

                holder.session?.setRepeatingRequest(builder!!.build(), null, holder.handler)
                holder.isPreviewing = true
                BoxToolLogUtils.saveCamera("摄像头 ${holder.cameraId} 已唤醒，恢复预览")
            } catch (e: Exception) {
                BoxToolLogUtils.saveCamera("摄像头 ${holder.cameraId} 恢复失败: ${e.message}")
                cameraErrorListener?.cameraStatus(false, index, "恢复${index}摄像头失败")
            }
        }
    }

    // ================== 智能拍照控制 ==================
    suspend fun takePictureSuspend(cameraId: String, switchType: Int, inOut: String, saveFile: File): File? =
        suspendCancellableCoroutine { cont ->
            // 调用你原来的回调方法
            this.takePicture(cameraId, switchType, inOut, saveFile) { file ->
                if (cont.isActive) cont.resume(file)
            }
        }

    /**
     * 智能拍照（防连击，支持休眠状态自动唤醒预热，拍完后保持预览运行）
     */
    fun takePicture(cameraId: String, switchType: Int = -1, inOut: String, saveFile: File, onComplete: (File?) -> Unit) {
        val holder = cameras[cameraId]
        if (holder == null) {
            BoxToolLogUtils.saveCamera("[$cameraId] 摄像头未就绪")
            cameraErrorListener?.cameraStatus(false, cameraId, "拍照${cameraId}摄像头未就绪")
            onComplete(null)
            return
        }

        // 核心修复：防连击/防抖，保护 Camera 消息队列不溢出
        if (holder.isCapturing) {
            BoxToolLogUtils.saveCamera("[$cameraId] 正在拍照中，忽略此次点击请求")
            onComplete(null)
            return
        }

        holder.isCapturing = true // 上锁

        scope.launch {
            try {
                // 如果当前处于休眠状态，必须先唤醒并预热曝光 (AE Warm-up)
                if (!holder.isPreviewing) {
                    resumeSingleCamera(cameraId, holder)
                    BoxToolLogUtils.saveCamera("[$cameraId] 唤醒相机，等待 800ms 硬件曝光调整...")
                    delay(800) // 等待进光量稳定，防止拍出全黑/全白照片
                }

                BoxToolLogUtils.saveCamera("[$cameraId] 开始抓取图像...")
                val imageFile = captureImageSuspend(cameraId, inOut, switchType, holder, saveFile)
                withContext(Dispatchers.IO) {
                    onComplete(imageFile)
                }
            } catch (e: Exception) {
                BoxToolLogUtils.saveCamera("拍照过程异常: ${e.message}")
                withContext(Dispatchers.Main) { onComplete(null) }
                cameraErrorListener?.cameraStatus(false, cameraId, "拍照${cameraId}摄像头异常")
            } finally {
                // 无论成功失败，确保解锁
                holder.isCapturing = false
            }
        }
    }

    private suspend fun captureImageSuspend(cameraId: String, inOut: String, switchType: Int = -1, holder: CameraHolder, saveFile: File): File? =
        suspendCancellableCoroutine { cont ->
            try {
                val builder = holder.device?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                builder?.addTarget(holder.reader!!.surface)

                holder.reader?.setOnImageAvailableListener({ reader ->
                    // 立即停止监听，防止重复回调
                    reader.setOnImageAvailableListener(null, null)

                    val img = reader.acquireLatestImage()
                    if (img == null) {
                        if (cont.isActive) cont.resume(null)
                        return@setOnImageAvailableListener
                    }

                    val buffer = img.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    img.close()

                    // 使用 IO 协程确保处理过程不阻塞主线程，且 resume 逻辑同步
                    scope.launch(Dispatchers.IO) {
                        try {
                            val text = when (switchType) {
                                1 -> "投递前"
                                0 -> "投递后"
                                else -> "远程拍照"
                            }
                            val watermarkText = "$text-$inOut-${com.serial.port.utils.AppUtils.getDateYMDHMS2()}"

                            // 调用改良后的保存方法
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
                        } catch (e: Exception) {
                            BoxToolLogUtils.saveCamera("处理图片异常: ${e.message}")
                            if (cont.isActive) cont.resume(null)
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
    // ================== 核心初始化 ==================

    private fun getExternalCameraIds(): List<String> {
        val externalIds = mutableListOf<String>()
        try {
            for (id in manager.cameraIdList) {
                BoxToolLogUtils.saveCamera(
                    "getExternalCameraIds: ${
                        manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                    }"
                )
                if (manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) != null) {
                    externalIds.add(id)
                }
            }
        } catch (e: Exception) {
            BoxToolLogUtils.saveCamera("getExternalCameraIds:error ${e.message}}")
        }
//        return externalIds.sorted()
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

            val thread = HandlerThread("CamThread-$cameraId").apply { start() }
            holder.thread = thread
            holder.handler = Handler(thread.looper)

            try {
                val captureSize = Size(1280, 720)
                val previewSize = Size(640, 480) // 预览使用低分辨率，省带宽

                holder.reader = ImageReader.newInstance(captureSize.width, captureSize.height, ImageFormat.JPEG, 2)

                manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        holder.device = camera
                        BoxToolLogUtils.saveCamera("摄像头 $cameraId 打开: onOpened")
                        initPreviewSession(cameraId, holder, textureView, previewSize, startPaused)
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        holder.release()
                        cameras.remove(cameraId)
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        BoxToolLogUtils.saveCamera("摄像头 $cameraId 底层错误码: $error")
                        holder.release()
                        cameras.remove(cameraId)
                        if (cont.isActive) cont.resume(Unit)
                    }
                }, holder.handler)
            } catch (e: Exception) {
                cameraErrorListener?.cameraStatus(false, cameraId, "打开${cameraId}摄像头失败")
                BoxToolLogUtils.saveCamera("摄像头 $cameraId 打开失败: ${e.message}")
                if (cont.isActive) cont.resume(Unit)
            }
        }

    private fun initPreviewSession(cameraId: String, holder: CameraHolder, textureView: TextureView, previewSize: Size, startPaused: Boolean) {
        // 核心修复：检查 TextureView 异步挂载状态，防止直接黑屏
        if (textureView.isAvailable) {
            BoxToolLogUtils.saveCamera("摄像头 $cameraId 打开: 防止直接黑屏")
            startSessionInternal(cameraId, holder, textureView.surfaceTexture!!, previewSize, startPaused)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    BoxToolLogUtils.saveCamera("摄像头 $cameraId 打开: 画面显示")
                    startSessionInternal(cameraId, holder, surface, previewSize, startPaused)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    private fun startSessionInternal(cameraId: String, holder: CameraHolder, surfaceTexture: SurfaceTexture, previewSize: Size, startPaused: Boolean) {
        surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        holder.previewSurface = Surface(surfaceTexture)

        val builder = holder.device!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        builder.addTarget(holder.previewSurface!!)

        holder.device?.createCaptureSession(
            listOf(holder.previewSurface, holder.reader!!.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    holder.session = session

                    // 核心修复：工业级 USB 摄像头大多数是定焦（Fixed-focus），盲目设置自动对焦会导致底层崩溃
                    val characteristics = manager.getCameraCharacteristics(holder.cameraId)
                    val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                    if (afModes != null && afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)) {
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    } else {
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                    }

                    try {
                        if (!startPaused) {
                            session.setRepeatingRequest(builder.build(), null, holder.handler)
                            holder.isPreviewing = true
                            BoxToolLogUtils.saveCamera("摄像头 ${holder.cameraId} 初始化成功，正在预览")
                        } else {
                            holder.isPreviewing = false
                            BoxToolLogUtils.saveCamera("摄像头 ${holder.cameraId} 初始化完毕，已挂起进入休眠待命状态")
                        }
                    } catch (e: Exception) {
                        cameraErrorListener?.cameraStatus(false, cameraId, "初始化${cameraId}摄像头预览失败")
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    cameraErrorListener?.cameraStatus(false, cameraId, "初始化${cameraId}摄像头配置失败")
                    BoxToolLogUtils.saveCamera("摄像头 ${holder.cameraId} Session 配置失败")
                }
            }, holder.handler
        )
    }

    // ================== 水印处理 ==================

    private fun addWatermarkToBytes(imageBytes: ByteArray, fileName: String, watermarkText: String, setColor: Int = Color.RED): String? {
        val dir = File(AppUtils.getContext().cacheDir, "action")
//        val dir = File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")
        if (!dir.exists()) dir.mkdirs()
        val destFile = File(dir, fileName)

        // 保持原画尺寸，禁用缩放
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            ?: return null
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = setColor
            textSize = bitmap.width / 40f
            setShadowLayer(3f, 2f, 2f, Color.BLACK) // 增加阴影增强可见度
        }

        val margin = bitmap.width * 0.02f
        canvas.drawText(watermarkText, margin, margin + 40f, paint)

        return try {
            FileOutputStream(destFile).use { out ->
                // 写入磁盘
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
//                out.flush() // 【核心修改】确保缓冲区数据强制刷入磁盘
            }
            bitmap.recycle() // 及时回收内存
            destFile.absolutePath
        } catch (e: Exception) {
            BoxToolLogUtils.saveCamera("写入水印文件失败: ${e.message}")
            null
        }
    }

    // ================== 精确防误杀 USB 广播监听 ==================

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)

            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    // 核心修复：只拦截 USB 视频设备（摄像头）。拔除U盘、鼠标键盘等外设时不误杀相机进程。
                    val isVideoDevice = device?.deviceClass == UsbConstants.USB_CLASS_VIDEO || device?.deviceClass == UsbConstants.USB_CLASS_MISC

                    if (isVideoDevice) {
                        BoxToolLogUtils.saveCamera("检测到 USB 摄像头拔出 [${device?.productName}], 紧急释放资源")
                        destroy()
                        ToastUtils.showLong("外置摄像头已断开")
                    } else {
                        BoxToolLogUtils.saveCamera("拔出的不是摄像头 [${device?.productName}], 忽略")
                    }
                }
            }
        }
    }
}