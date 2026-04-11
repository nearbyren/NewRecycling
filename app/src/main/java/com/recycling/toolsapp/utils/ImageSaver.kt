package com.recycling.toolsapp.utils

/**
 * @author: lr
 * @created on: 2025/5/14 下午2:09
 * @description:
 */
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.serial.port.utils.FileMdUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

object ImageSaver {

    val setMap = mutableMapOf<String, Bitmap>()

    // 保存图片到本地
    suspend fun saveImageFromUrl(context: Context, imgUrl: String, tableId: String): String? {
        return try {
            // 1. 下载图片
            val bitmap = downloadImage(imgUrl)

            // 2. 保存到本地目录
            bitmap?.let {
                setMap[tableId] = bitmap
                saveBitmap(context, it, "${tableId}.jpg")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // 下载图片（使用OkHttp）
    private suspend fun downloadImage(imgUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(imgUrl).build()
        val response = client.newCall(request).execute()
        response.body?.byteStream()?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }

    // 保存Bitmap到文件
    private suspend fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): String? =
        withContext(Dispatchers.IO) {
            // 指定存储目录：/data/data/包名/files/userAvatar/
//            val dir = File(AppUtils.getContext().filesDir, "userAvatar")
            val dir = FileMdUtil.matchNewFile("userAvatar")
            if (!dir.exists()) {
                dir.mkdirs() // 创建目录
            }

            dir.let {
                val file = File(dir, fileName)
                FileOutputStream(file).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                    fos.flush()
                }
                file.absolutePath // 返回保存路径
            }
        }
}