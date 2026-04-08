package com.serial.port.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileMdUtil {

    fun writeToMdFile(context: Context, content: String) {
        // 获取 log 文件路径：data/data/<包名>/log/log.md
        val logDir = File(context.filesDir, "log")
        if (!logDir.exists()) {
            Loge.d("授权成功 首次创建目录...")
            logDir.mkdirs() // 创建 log 目录
        }
        val logFile = File(logDir, "log.md")
        // 确保文件存在，如果不存在则创建
        if (!logFile.exists()) {
            Loge.d("授权成功 首次创建文件...")
            logFile.createNewFile()
        }

        // 写入内容并换行两次
        logFile.appendText("$content\n\n")
    }

    fun findAllFileAction(): List<File> {
        val f = File("${AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)}/action")
        return try {
            f.listFiles()?.filter { it.isFile } ?: emptyList()
        } catch (e: SecurityException) {
            Toast.makeText(AppUtils.getContext(), "没有权限访问该目录", Toast.LENGTH_LONG).show()
            emptyList()
        }
    }

    fun findFileInSDCard(fileName: String): File? {
        // /sdcard/Android/data//com.cabinet.toolsapp/files/Download/20250701.bin
        return if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            // 获取应用私有存储目录下的download文件夹
            val downloadDir =
                    AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            downloadDir?.walk()?.firstOrNull {
                it.name.equals(fileName, ignoreCase = true)
            }
        } else null
    }

    fun matchNewFile(path: String): File {
        return File("${AppUtils.getContext().filesDir}/${path}")
    }

    fun matchNewFileName(path: String, fileName: String): String {
        return File("${AppUtils.getContext().filesDir}/${path}/${fileName}").absolutePath
    }

    fun matchNewFile2(path: String, fileName: String): File {
        return File("${AppUtils.getContext().filesDir}/${path}/${fileName}")
    }

    fun matchDownloadsName(path: String, fileName: String): String {
        return File("${AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)}/${path}/${fileName}").absolutePath
    }

    fun matchDownloadsNameF(path: String, fileName: String): File {
        return File("${AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)}/${path}/${fileName}")
    }

    /**
     * 检测资源文件是否存在
     */
    fun checkApkFileExists(fileName: String): Boolean {
        val dataFiles = FileMdUtil.matchNewFile("apk")
        return dataFiles?.let { dir ->
            File(dir, fileName).takeIf { it.exists() && it.isFile } != null
        } ?: false
    }

    /**
     * 检测固件文件是否存在
     */
    fun checkBinFileExists(fileName: String): Boolean {
        val downloadDir = AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        return downloadDir?.let { dir ->
            File(dir, fileName).takeIf { it.exists() && it.isFile } != null
        } ?: false
    }

    /**
     * 检测音频文件是否存在
     */
    fun checkAudioFileExists(fileName: String): Boolean {
        val dataFiles = FileMdUtil.matchNewFile("audio")
        return dataFiles?.let { dir ->
            File(dir, fileName).takeIf { it.exists() && it.isFile } != null
        } ?: false
    }

    /**
     * 检测资源文件是否存在
     */
    fun checkResFileExists(fileName: String): Boolean {
        val dataFiles = FileMdUtil.matchNewFile("res")
        return dataFiles?.let { dir ->
            File(dir, fileName).takeIf { it.exists() && it.isFile } != null
        } ?: false
    }

    /**
     * 重命名指定路径下的文件
     * @param dirPath 目录路径（如：context.filesDir）
     * @param oldFileName 原文件名
     * @param newFileName 新文件名
     * @return 是否重命名成功
     */
    fun renameFileInDir(dirPath: File, oldFileName: String, newFileName: String): Boolean {
        val oldFile = File(dirPath, oldFileName).takeIf { it.exists() } ?: return false

        val newFile = File(dirPath, newFileName)
        if (newFile.exists()) {
            newFile.delete()
        }

        return try {
            oldFile.renameTo(newFile)
        } catch (e: SecurityException) {
            false
        }
    }

    /***
     * @param fileNameToDelete
     * 删除文件
     */
    fun delFileName(fileNameToDelete: String) {
        // 指定目录路径（修改为实际路径）
        // 要删除的文件名（支持通配符）
        val dir =
                File(AppUtils.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "action")

        if (!dir.exists() || !dir.isDirectory) {
            Loge.e("指定文件删除 目录不存在或不是有效目录: $dir")
            return
        }

        Loge.e("指定文件删除 目录不存在或不是有效目录: ${dir.absolutePath}")
        val files = dir.listFiles { f, name ->
            Loge.e("指定文件删除 目录不存在或不是有效目录: ${f.absolutePath}|$name")

            name == fileNameToDelete // 可修改为更复杂的匹配逻辑
        } ?: emptyArray()

        if (files.isEmpty()) {
            Loge.e("指定文件删除 未找到匹配文件: $fileNameToDelete")
            return
        }

        files.forEach { file ->
            if (file.delete()) {
                Loge.e("指定文件删除 成功删除: ${file.absolutePath}")
            } else {
                Loge.e("指定文件删除 删除失败: ${file.absolutePath}")
            }
        }
    }

    /**
     * 保存 音频 图片资源
     * @param bitmap
     * @param fileName
     */
    fun saveBitmapToInternalStorage(bitmap: Bitmap, fileName: String): String? {
        // 指定存储目录：/data/data/包名/files/userAvatar/
//        val dir = File(AppUtils.getContext().filesDir, "faceVerify")
        val dir = FileMdUtil.matchNewFile("res")
        if (!dir.exists()) {
            dir.mkdirs() // 创建目录
        }

        // 创建保存文件
        val file = File(dir, fileName)
        var fos: FileOutputStream? = null

        try {
            fos = FileOutputStream(file)
            // 将 Bitmap 压缩为 PNG 格式保存
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            return file.absolutePath // 返回保存路径
        } catch (e: IOException) {
            e.printStackTrace()

        } finally {
            fos?.close() // 关闭输出流
        }
        return null
    }

    /***
     * 检测音频文件
     */
    fun shouldAudio(fileName: String): Boolean {
        return fileName.contains("wav", ignoreCase = true) || fileName.contains("mp3", ignoreCase = true) || fileName.contains("mp4", ignoreCase = true)
    }

    /***
     * 检测png文件
     */
    fun shouldPGJ(fileName: String): Boolean {
        return fileName.contains("png", ignoreCase = true) || fileName.contains("gif", ignoreCase = true) || fileName.contains("jpg", ignoreCase = true)
    }
}