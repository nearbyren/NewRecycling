package com.recycling.toolsapps.http

/**
 * @author: lr
 * @created on: 2025/5/14 上午11:25
 * @description:每天删除垃圾apk和db文件
 */
import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.serial.port.utils.AppUtils
import com.serial.port.utils.Loge

/***
 * 每天删除垃圾apk和db文件
 */
class DailyDelWorker(
    context: Context, params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 在此调用你的 copyDB 方法
        delDB()
        return Result.success()
    }

    private fun delDB() {
//        CoroutineScope(Dispatchers.IO).launch {
        Loge.d("清理每天内数据开始")
        try {
            // 清理下载目录
            val downloadDir =
                    AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadDir?.let { dir ->
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && shouldDelete(file.name)) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            // 处理异常（如日志记录）
            Loge.d("每天删除垃圾apk和db文件 delDB... ${e.message}")
        }
//        }
    }

    // 判断文件是否需要删除
    private fun shouldDelete(fileName: String): Boolean {
        return fileName.contains("_db", ignoreCase = true) || fileName.endsWith(".apk", ignoreCase = true) || fileName.endsWith(".bin", ignoreCase = true) || fileName.endsWith(".zip", ignoreCase = true)|| fileName.endsWith(".xlsx", ignoreCase = true)
    }

}