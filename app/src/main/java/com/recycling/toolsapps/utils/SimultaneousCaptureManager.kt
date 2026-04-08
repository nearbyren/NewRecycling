package com.recycling.toolsapps.utils

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Environment
import android.os.Handler
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.serial.port.utils.Loge
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 同时拍照和录制管理器
 */
class SimultaneousCaptureManager(
    private val context: Context,
    private val cameraDevice: CameraDevice,
    private val previewSize: Size,
    private val textureView: TextureView,
    private val backgroundHandler: Handler
) {

    private var mediaRecorder: MediaRecorder? = null
    private var imageReader: ImageReader? = null
    private var captureSession: CameraCaptureSession? = null
    private var isRecording = false
    private var isSimultaneousMode = false

    // 回调接口
    interface SimultaneousCaptureListener {
        fun onRecordingStarted()
        fun onPhotoCaptured()
        fun onRecordingStopped(outputFile: File)
        fun onError(error: String)
    }

    private var listener: SimultaneousCaptureListener? = null

    fun setCaptureListener(listener: SimultaneousCaptureListener) {
        this.listener = listener
    }

    /**
     * 开始同时拍照和录制
     */
    fun startSimultaneousCapture(photoFormat: Int = ImageFormat.JPEG): Boolean {
        return try {
            // 1. 初始化 MediaRecorder
            setupMediaRecorder()

            // 2. 初始化 ImageReader
            imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                photoFormat,
                2
            )

            // 3. 准备所有 Surface
            val texture: SurfaceTexture? = textureView.surfaceTexture
            texture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(texture)
            val recorderSurface = mediaRecorder?.surface
            val captureSurface = imageReader?.surface

            val surfaces = mutableListOf<Surface>()
            surfaces.add(previewSurface)
            recorderSurface?.let { surfaces.add(it) }
            captureSurface?.let { surfaces.add(it) }

            // 4. 创建 CaptureSession
            cameraDevice.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session

                        try {
                            // 创建录制请求
                            val recordBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                            recordBuilder.addTarget(previewSurface)
                            recorderSurface?.let { recordBuilder.addTarget(it) }

                            // 设置参数
                            recordBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                            recordBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                            // 开始录制
                            session.setRepeatingRequest(recordBuilder.build(), null, backgroundHandler)
                            mediaRecorder?.start()

                            isRecording = true
                            isSimultaneousMode = true

                            listener?.onRecordingStarted()
                            Loge.d("同时操作: 录制已开始")

                        } catch (e: Exception) {
                            listener?.onError("录制启动失败: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        listener?.onError("会话配置失败")
                    }
                },
                backgroundHandler
            )

            true
        } catch (e: Exception) {
            listener?.onError("同时操作初始化失败: ${e.message}")
            false
        }
    }

    /**
     * 在录制过程中拍照
     */
    fun capturePhotoDuringRecording(orientation: Int = 90): Boolean {
        if (!isSimultaneousMode || captureSession == null) {
            return false
        }

        return try {
            val captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            imageReader?.surface?.let { captureBuilder.addTarget(it) }

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation)

            captureSession?.capture(captureBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    listener?.onPhotoCaptured()
                    Loge.d("同时操作: 拍照完成")
                }
            }, backgroundHandler)

            true
        } catch (e: Exception) {
            listener?.onError("拍照失败: ${e.message}")
            false
        }
    }

    /**
     * 停止同时操作
     */
    fun stopSimultaneousCapture(): File? {
        return try {
            val outputFile = getOutputFile()

            if (isRecording) {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
            }

            captureSession?.close()
            captureSession = null

            imageReader?.close()
            imageReader = null

            isSimultaneousMode = false

            listener?.onRecordingStopped(outputFile)
            Loge.d("同时操作: 已停止")

            outputFile
        } catch (e: Exception) {
            listener?.onError("停止操作失败: ${e.message}")
            null
        }
    }

    /**
     * 设置 ImageReader 的可用监听器
     */
    fun setOnImageAvailableListener(listener: ImageReader.OnImageAvailableListener) {
        imageReader?.setOnImageAvailableListener(listener, backgroundHandler)
    }

    /**
     * 检查是否在同时操作模式
     */
    fun isInSimultaneousMode(): Boolean {
        return isSimultaneousMode
    }

    private fun setupMediaRecorder() {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

            val outputFile = getOutputFile()
            setOutputFile(outputFile.absolutePath)

            setVideoEncodingBitRate(10000000)
            setVideoFrameRate(30)
            setVideoSize(previewSize.width, previewSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            prepare()
        }
    }

    private fun getOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("VID_${timestamp}_", ".mp4", storageDir)
    }

    fun release() {
        stopSimultaneousCapture()
    }
}