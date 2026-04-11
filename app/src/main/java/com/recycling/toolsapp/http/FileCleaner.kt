package com.recycling.toolsapp.http

import android.os.Environment
import com.serial.port.utils.AppUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileCleaner {
    // 启动清理任务（默认每24小时执行一次）
    //FileCleaner.scheduleCleanup()

    // 自定义间隔（例如每12小时）
    //FileCleaner.scheduleCleanup(intervalHours = 12)
    // 启动定时清理任务

    /***
     * @param intervalHours 小时
     */
    fun scheduleCleanup(intervalHours: Long = 24) {
        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                cleanDownloadDirectory()
                delay(TimeUnit.HOURS.toMillis(intervalHours)) // 默认每24小时执行一次
            }
        }
    }

    /***
     * 立即执行清除文件
     */
    fun scheduleCleanupNow() {
        CoroutineScope(Dispatchers.IO).launch {
            cleanDownloadDirectory()
        }
    }

    /***
     * 清理下载目录
     */
    private suspend fun cleanDownloadDirectory() {
        withContext(Dispatchers.IO) {
            try {
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
                e.printStackTrace()
            }
        }
    }

    /***
     * @param fileName 清除文件类型
     * 判断文件是否需要删除
     */
    private fun shouldDelete(fileName: String): Boolean {
        return fileName.contains("_db", ignoreCase = true) ||
                fileName.endsWith(".apk", ignoreCase = true) ||
                fileName.endsWith(".bin", ignoreCase = true)
    }

    /***
     * 取消所有清理任务
     */
    fun cancelCleanup() {
        CoroutineScope(Dispatchers.IO).cancel()
    }
    // 压缩指定文件夹
    fun zipFolder(sourcePath: String, outputPath: String): Boolean {
        return try {
            val srcFolder = File(sourcePath)
            if (!srcFolder.exists()) return false

            FileOutputStream(outputPath).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    compressDirectory(srcFolder, srcFolder.name, zos)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun compressDirectory(
        folder: File,
        parentPath: String,
        zos: ZipOutputStream
    ) {
        folder.listFiles()?.forEach { file ->
            val entryPath = "$parentPath/${file.name}"
            if (file.isDirectory) {
                compressDirectory(file, entryPath, zos)
            } else {
                compressFile(file, entryPath, zos)
            }
        }
    }

    private fun compressFile(
        file: File,
        entryPath: String,
        zos: ZipOutputStream
    ) {
        FileInputStream(file).use { fis ->
            zos.putNextEntry(ZipEntry(entryPath))
            fis.copyTo(zos, 1024)
            zos.closeEntry()
        }
    }
}