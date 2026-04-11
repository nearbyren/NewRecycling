package com.recycling.toolsapp.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object DatabaseExportHelper {

    private const val DATABASE_NAME = "cabinet_database.db"
    private const val DATABASE_PATH = "/data/data/com.recycling.toolsapp/databases/"

    // 外部存储路径（如 SD 卡）
    private const val EXTERNAL_STORAGE_PATH = "/storage/emulated/0/Download/"

    /**
     * 导出数据库文件
     */
    fun exportDatabase(context: Context) {
        // 检查权限
        if (checkPermission(context)) {
            val dbFile = File(DATABASE_PATH + DATABASE_NAME)
            val externalFile = File(EXTERNAL_STORAGE_PATH + DATABASE_NAME)

            // 确保外部存储目录存在
            val externalDir = File(EXTERNAL_STORAGE_PATH)
            if (!externalDir.exists()) {
                externalDir.mkdirs()
            }

            try {
                // 复制文件到外部存储
                copyFile(dbFile, externalFile)
                Toast.makeText(context, "数据库导出成功！", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(context, "导出数据库失败！", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 如果没有权限，要求用户授权
            requestPermission(context)
        }
    }

    /**
     * 检查是否有写入外部存储的权限
     */
    private fun checkPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 请求权限
     */
    private fun requestPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1001
            )
        }
    }

    /**
     * 复制数据库文件到外部存储
     */
    @Throws(IOException::class)
    private fun copyFile(inputFile: File, outputFile: File) {
        val inputStream: InputStream = FileInputStream(inputFile)
        val outputStream: OutputStream = FileOutputStream(outputFile)

        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            outputStream.write(buffer, 0, length)
        }

        outputStream.flush()
        outputStream.close()
        inputStream.close()
    }
}
