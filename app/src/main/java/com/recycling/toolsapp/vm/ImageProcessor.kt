import android.content.Context
import android.graphics.*
import android.net.Uri
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 图片处理工具类：支持添加文字/图片水印并压缩
 */
object ImageProcessor {

    /**
     * 水印配置
     */
    data class WatermarkConfig(
        val text: String = "",
        val position: Position = Position.BOTTOM_RIGHT,
        val textSize: Float = 40f,
        val textColor: Int = Color.WHITE,
        val alpha: Int = 180,
        val padding: Int = 20
    )

    /**
     * 水印位置枚举
     */
    enum class Position {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER
    }

    /**
     * 压缩配置
     */
    data class CompressConfig(
        val width: Int = 1080,
        val height: Int = 1920,
        val quality: Int = 80,
        val format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        val maxSize: Long = 2_097_152
    )

    /**
     * 处理图片：加水印 + 压缩
     */
    suspend fun processImage(
        context: Context,
        imagePath: String,
        watermark: WatermarkConfig? = null,
        compressConfig: CompressConfig? = CompressConfig()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
                ?: return@withContext Result.failure(Exception("无法加载图片"))

            val watermarkedBitmap = if (watermark != null && watermark.text.isNotEmpty()) {
                addWatermark(originalBitmap, watermark)
            } else {
                originalBitmap
            }

            val tempFile = File(context.cacheDir, "temp_watermarked_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { stream ->
                watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
            watermarkedBitmap.recycle()

            val finalFile = if (compressConfig != null) {
                compressImage(context, tempFile, compressConfig)
            } else {
                tempFile
            }

            if (tempFile != finalFile) {
                tempFile.delete()
            }

            Result.success(finalFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 给 Bitmap 添加文字水印
     */
    private fun addWatermark(bitmap: Bitmap, config: WatermarkConfig): Bitmap {
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = config.textColor
            textSize = config.textSize
            alpha = config.alpha
            textAlign = when (config.position) {
                Position.TOP_LEFT, Position.BOTTOM_LEFT -> Paint.Align.LEFT
                Position.TOP_RIGHT, Position.BOTTOM_RIGHT -> Paint.Align.RIGHT
                Position.CENTER -> Paint.Align.CENTER
            }
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }

        val textBounds = Rect()
        paint.getTextBounds(config.text, 0, config.text.length, textBounds)
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()

        val x = when (config.position) {
            Position.TOP_LEFT, Position.BOTTOM_LEFT -> config.padding.toFloat()
            Position.TOP_RIGHT, Position.BOTTOM_RIGHT -> (canvas.width - config.padding).toFloat()
            Position.CENTER -> (canvas.width / 2).toFloat()
        }

        val y = when (config.position) {
            Position.TOP_LEFT, Position.TOP_RIGHT -> (config.padding + textHeight).toFloat()
            Position.BOTTOM_LEFT, Position.BOTTOM_RIGHT -> (canvas.height - config.padding).toFloat()
            Position.CENTER -> (canvas.height / 2 + textHeight / 2).toFloat()
        }

        canvas.drawText(config.text, x, y, paint)
        return resultBitmap
    }

    /**
     * 使用 Compressor 压缩图片
     */
    private suspend fun compressImage(
        context: Context,
        imageFile: File,
        config: CompressConfig
    ): File {
        return Compressor.compress(context, imageFile) {
            resolution(config.width, config.height)
            quality(config.quality)
            format(config.format)
            size(config.maxSize)
        }
    }

    /**
     * 快速处理：添加水印并压缩（支持指定输出路径）
     */
    suspend fun quickProcess(
        context: Context,
        imagePath: String,
        watermarkText: String,
        outputPath: String? = null,
        outputFileName: String? = null,
        position: Position = Position.BOTTOM_RIGHT,  // 修正：使用完整的 Position
        quality: Int = 85,
        maxWidth: Int = 1080
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 加载原始图片
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
                ?: return@withContext Result.failure(Exception("无法加载图片"))

            // 2. 添加水印
            val watermark = WatermarkConfig(
                text = watermarkText,
                position = position,  // 使用传入的 position
                textSize = 36f,
                alpha = 200
            )
            val watermarkedBitmap = addWatermark(originalBitmap, watermark)

            // 3. 确定输出文件
            val outputFile = if (outputPath != null) {
                File(outputPath)
            } else {
                val fileName = outputFileName ?: "processed_${System.currentTimeMillis()}.jpg"
                File(context.cacheDir, fileName)
            }

            // 4. 创建输出目录（如果不存在）
            outputFile.parentFile?.mkdirs()

            // 5. 压缩并保存
            val outputStream = FileOutputStream(outputFile)
            watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            outputStream.close()

            // 6. 可选：如果还需要进一步压缩到指定尺寸
            if (watermarkedBitmap.width > maxWidth) {
                val scaledBitmap = Bitmap.createScaledBitmap(
                    watermarkedBitmap,
                    maxWidth,
                    (watermarkedBitmap.height * maxWidth / watermarkedBitmap.width.toFloat()).toInt(),
                    true
                )
                watermarkedBitmap.recycle()

                FileOutputStream(outputFile).use { stream ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                }
                scaledBitmap.recycle()
            } else {
                watermarkedBitmap.recycle()
            }

            Result.success(outputFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 批量处理多张图片
     */
    suspend fun batchProcess(
        context: Context,
        imagePaths: List<String>,
        watermarkText: String,
        outputDir: String? = null,
        position: Position = Position.BOTTOM_RIGHT,  // 修正：使用完整的 Position
        quality: Int = 85,
        maxWidth: Int = 1080,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<String>()
            val baseDir = outputDir?.let { File(it) } ?: File(context.cacheDir, "processed_images")
            baseDir.mkdirs()

            imagePaths.forEachIndexed { index, imagePath ->
                onProgress(index + 1, imagePaths.size)

                val outputFileName = "processed_${System.currentTimeMillis()}_$index.jpg"
                val result = quickProcess(
                    context = context,
                    imagePath = imagePath,
                    watermarkText = watermarkText,
                    outputPath = File(baseDir, outputFileName).absolutePath,
                    position = position,
                    quality = quality,
                    maxWidth = maxWidth
                )

                result.onSuccess { path ->
                    results.add(path)
                }
            }

            if (results.isEmpty()) {
                Result.failure(Exception("没有图片处理成功"))
            } else {
                Result.success(results)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Result 封装类
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val exception: Exception) : Result<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure

    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (Exception) -> Unit): Result<T> {
        if (this is Failure) action(exception)
        return this
    }

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun <T> failure(exception: Exception): Result<T> = Failure(exception)
    }
}