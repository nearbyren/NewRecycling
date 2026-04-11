package com.recycling.toolsapp.http

/**
 * @author: lr
 * @created on: 2025/5/14 上午11:25
 * @description:定时清理非三天内的数据
 */
import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.serial.port.utils.AppUtils
import com.serial.port.utils.Loge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/***
 * 定时清理非三天内的数据
 */
class DailyDelDateWorker(
    context: Context, params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 在此调用你的 copyDB 方法
        delDB()
        return Result.success()
    }

    private fun delDB() {
//        CoroutineScope(Dispatchers.IO).launch {
        Loge.d("清理非七天内数据开始")
        try {
            // 清理下载目录
            val downloadDir =
                    AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadDir?.let { dir ->
                val files = dir.listFiles() ?: return@let
                // 获取一周前的日期
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -5)
                val oneWeekAgo = calendar.time
                // 日期格式解析器
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                files.forEach { file ->
                    Loge.d("清理非三天内数据开始 目录 ${file.name}")
                    if (shouldDeleteDir(file.name)) {
                        file.listFiles()?.forEach { log ->
                            if (log.isFile && log.name.contains("---")) {
                                try {
                                    Loge.d("清理非三天内数据开始 文件 ${log.name}")
                                    // 提取文件名中的日期部分
                                    val fileName = log.name
                                    val datePart =
                                            fileName.substringAfterLast("---").substringBefore(".txt")
                                    // 解析日期
                                    val fileDate = dateFormat.parse(datePart) ?: return@forEach
                                    // 检查是否超过一周
                                    if (fileDate.before(oneWeekAgo)||fileName.contains("-db-")) {
                                        Loge.d("清理非三天内数据开始 删除过期文件: ${log.name} (日期: $datePart)")
                                        if (log.delete()) {
                                            Loge.d("清理非三天内数据开始 文件删除成功")
                                        } else {
                                            Loge.d("清理非三天内数据开始 文件删除失败")
                                        }
                                    } else {
                                        Loge.d("清理非三天内数据开始 保留文件: ${log.name} (日期: $datePart)")
                                    }
                                } catch (e: Exception) {
                                    Loge.d("清理非三天内数据开始 处理文件 ${log.name} 出错: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 处理异常（如日志记录）
            Loge.d("清理非七天内数据开始 catch ${e.message}")
        }
//        }
    }

    // 判断是否该文件目录
    private fun shouldDeleteDir(fileName: String): Boolean {
        return fileName.contains("socket_box_crash", ignoreCase = true) || fileName.contains("action", ignoreCase = true)
    }


}