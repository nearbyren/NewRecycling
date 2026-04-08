//package com.recycling.toolsapp.utils
//
//import android.util.Log
//import com.arthenica.mobileffmpeg.Config
//import com.arthenica.mobileffmpeg.FFmpeg
//import kotlinx.coroutines.*
//
///**
// * 七牛云视频推流工具类
// * 负责推流执行逻辑（非UI）
// */
//object QiniuPushStreamManager {
//
//    private const val TAG = "QiniuPushStream"
//    private var pushJob: Job? = null
//    private var listener: PushStreamListener? = null
//
//    fun startPush(
//        localVideoPath: String,
//        rtmpUrl: String,
//        loop: Boolean = false,
//        listener: PushStreamListener? = null
//    ) {
//        if (pushJob?.isActive == true) {
//            Log.w(TAG, "推流已在进行中")
//            return
//        }
//
//        this.listener = listener
//
//        pushJob = CoroutineScope(Dispatchers.IO).launch {
//            try {
//                listener?.onStart()
//
//                Config.enableLogCallback { log -> Log.d(TAG, log.text) }
//
//                val command = mutableListOf(
//                    "-re",
//                    "-i", localVideoPath,
//                    "-c", "copy",
//                    "-f", "flv",
//                    rtmpUrl
//                )
//
//                if (loop) {
//                    command.add(0, "-stream_loop")
//                    command.add(1, "-1")
//                }
//
//                Log.i(TAG, "开始推流: $rtmpUrl")
//
//                val rc = FFmpeg.execute(command.toTypedArray())
//
//                withContext(Dispatchers.Main) {
//                    when (rc) {
//                        Config.RETURN_CODE_SUCCESS -> {
//                            Log.i(TAG, "推流结束 ✅")
//                            listener?.onSuccess()
//                        }
//                        Config.RETURN_CODE_CANCEL -> {
//                            Log.w(TAG, "推流被取消 ⚠️")
//                            listener?.onCancel()
//                        }
//                        else -> {
//                            Log.e(TAG, "推流失败 ❌ rc=$rc")
//                            listener?.onError("返回码: $rc")
//                        }
//                    }
//                }
//
//            } catch (e: Exception) {
//                Log.e(TAG, "推流异常", e)
//                withContext(Dispatchers.Main) {
//                    listener?.onError(e.message ?: "未知错误")
//                }
//            }
//        }
//    }
//
//    fun stopPush() {
//        Log.i(TAG, "停止推流")
//        FFmpeg.cancel()
//        pushJob?.cancel()
//        listener = null
//    }
//
//    interface PushStreamListener {
//        fun onStart()
//        fun onSuccess()
//        fun onError(msg: String)
//        fun onCancel()
//    }
//}
