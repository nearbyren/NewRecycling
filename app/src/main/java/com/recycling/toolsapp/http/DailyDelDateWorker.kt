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
import com.recycling.toolsapp.db.DatabaseManager
import com.recycling.toolsapp.model.LogEntity
import com.serial.port.utils.AppUtils
import com.serial.port.utils.Loge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
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
        CoroutineScope(Dispatchers.IO).launch {
            Loge.d("清理过期数据开始")
            try {
                val downloadDir = AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                downloadDir?.let { dir ->
                    val files = dir.listFiles() ?: return@let

                    // 计算3天前的时间戳
                    val cutoffTime = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -3)
                    }.timeInMillis

                    // 今天零点时间戳（用于跳过当天文件）
                    val startOfToday = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    files.forEach { dirItem ->
                        if (!dirItem.isDirectory || !shouldDeleteDir(dirItem.name)) return@forEach

                        val logFiles = dirItem.listFiles()?.filter { it.isFile } ?: emptyList()

                        for (logFile in logFiles) {
                            try {
                                val fileName = logFile.name
                                // 提取日期部分（兼容 --- 和 --）
                                val datePart = when {
                                    fileName.contains("---") -> fileName.substringAfterLast("---").substringBefore(".txt")
                                    fileName.contains("--") -> fileName.substringAfterLast("--").substringBefore(".txt")
                                    else -> null
                                }

                                if (datePart.isNullOrEmpty()) {
                                    Loge.w("清理过期数据 无法解析日期，跳过: $fileName")
                                    continue
                                }

                                val fileDate = dateFormat.parse(datePart) ?: continue
                                val fileTime = fileDate.time

                                // 跳过当天文件（正在写入）
                                if (fileTime >= startOfToday) {
                                    Loge.d("清理过期数据 当天文件，保留: $fileName")
                                    continue
                                }

                                val isExpired = fileTime < cutoffTime
                                val isDbFile = fileName.contains("-db-", ignoreCase = true)

                                if (isExpired || isDbFile) {
                                    Loge.d("清理过期数据 删除文件: $fileName (日期=$datePart, 过期=$isExpired, DB=$isDbFile)")
                                    if (logFile.delete()) {
                                        Loge.d("清理过期数据 删除成功: $fileName")

                                    } else {
                                        Loge.w("清理过期数据 删除失败: $fileName")
                                    }
                                } else {
                                    Loge.d("清理过期数据 保留文件: $fileName")
                                }
                            } catch (e: Exception) {
                                Loge.e("清理过期数据 处理文件异常: ${logFile.name}, ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Loge.e("清理任务异常: ${e.message}")
            } finally {
                DatabaseManager.insertLog(AppUtils.getContext(), LogEntity().apply {
                    msg = "删除日志文件finally"
                    time = AppUtils.getDateYMDHMS()
                })
            }
        }
    }

    // 判断是否该文件目录
    private fun shouldDeleteDir(fileName: String): Boolean {
        return fileName.contains("socket_box_crash", ignoreCase = true) || fileName.contains("business_logs", ignoreCase = true) || fileName.contains("socket_logs", ignoreCase = true) || fileName.contains("action", ignoreCase = true)
    }


}