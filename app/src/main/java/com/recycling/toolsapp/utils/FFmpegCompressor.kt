//package com.recycling.toolsapp.utils
//
//
//import com.arthenica.mobileffmpeg.Config
//import com.arthenica.mobileffmpeg.FFmpeg
//import com.arthenica.mobileffmpeg.FFprobe
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import java.io.File
//
//
//object FFmpegCompressor {
//
//    suspend fun compressVideo(
//        inputPath: String,
//        outputPath: String,
//        targetBitrate: String = "1M",
//        targetWidth: Int = 720,
//        targetHeight: Int = 1280,
//        fps: Int = 30,
//        onProgress: ((progress: Float) -> Unit)? = null,
//        onComplete: ((success: Boolean, message: String) -> Unit)? = null
//    ) = withContext(Dispatchers.IO) {
//
//        val inputFile = File(inputPath)
//        if (!inputFile.exists()) {
//            onComplete?.invoke(false, "输入文件不存在")
//            return@withContext
//        }
//
//        val outputFile = File(outputPath)
//        if (outputFile.exists()) outputFile.delete()
//
//        val duration = getVideoDuration(inputPath)
//
//        Config.enableStatisticsCallback { stats ->
//            if (duration > 0) {
//                val time = stats.time.toFloat() / 1000f
//                val progress = (time / duration) * 100f
//                onProgress?.invoke(progress.coerceAtMost(100f))
//            }
//        }
//
//        // ✅ 新版安全参数配置
//        val cmd = arrayOf(
//            "-y",
//            "-i", inputPath,
//            "-vf", "scale=$targetWidth:$targetHeight",
//            "-r", fps.toString(),
//            "-b:v", targetBitrate,
//            "-c:v", "libx264",
//            "-profile:v", "baseline",
//            "-level", "3.0",
//            "-pix_fmt", "yuv420p",
//            "-preset", "medium",
//            "-movflags", "+faststart",
//            "-c:a", "aac",
//            "-ar", "44100",
//            "-ac", "2",
//            "-b:a", "128k",
//            outputPath
//        )
//
//        val rc = FFmpeg.execute(cmd)
//
//        if (rc == Config.RETURN_CODE_SUCCESS) {
//            onComplete?.invoke(true, "压缩成功: $outputPath")
//        } else {
//            onComplete?.invoke(false, "压缩失败: ${Config.getLastCommandOutput()}")
//        }
//    }
//
//    private fun getVideoDuration(filePath: String): Float {
//        val cmd = arrayOf(
//            "-v", "error",
//            "-show_entries", "format=duration",
//            "-of", "default=noprint_wrappers=1:nokey=1",
//            filePath
//        )
//        val rc = FFprobe.execute(cmd)
//        val output = Config.getLastCommandOutput()
//        return if (rc == 0 && !output.isNullOrEmpty()) {
//            output.trim().toFloatOrNull() ?: 0f
//        } else 0f
//    }
//}
//
//
