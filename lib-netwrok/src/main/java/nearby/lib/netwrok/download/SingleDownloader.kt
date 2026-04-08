package nearby.lib.netwrok.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isCancelled
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nearby.lib.netwrok.base.HttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CancellationException

/**
 * @description: 单文件下载器
 * @since: 1.0.0
 */
open class SingleDownloader(val client: HttpClient) {
    // 下载开始
    private var startAction: (() -> Unit)? = null

    // 下载完成（成功或失败）
    private var completeAction: ((url: String, filePath: String) -> Unit)? = null

    // 下载成功
    private var successAction: ((url: String, file: File) -> Unit)? = null

    // 下载出现异常
    private var errorAction: ((url: String, cause: Throwable) -> Unit)? = null

    // 下载进度
    private var progressAction: ((currentLength: Long, totalLength: Long, progress: Int) -> Unit)? =
            null

    private var job: Job? = null

    fun onStart(action: () -> Unit): SingleDownloader {
        this.startAction = action
        return this
    }

    fun onCompletion(action: (url: String, filePath: String) -> Unit): SingleDownloader {
        this.completeAction = action
        return this
    }

    fun onSuccess(action: ((url: String, file: File) -> Unit)): SingleDownloader {
        successAction = action
        return this
    }

    fun onError(action: ((url: String, cause: Throwable) -> Unit)): SingleDownloader {
        errorAction = action
        return this
    }

    fun onProgress(action: ((currentLength: Long, totalLength: Long, progress: Int) -> Unit)): SingleDownloader {
        progressAction = action
        return this
    }

    /**
     * 开始下载
     */
    fun excute(url: String, filePath: String) {
        job = CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                download(url, filePath)
            }
        }
    }

    /**
     * 取消下载
     */
    fun cancel() {
        job?.cancel()
    }

    /**
     * 任务是否执行完毕
     */
    fun isCompleted(): Boolean {
        return job?.isCompleted ?: true
    }

    suspend fun download(url: String, filePath: String) {
        try {
            startAction?.let { action ->
                withContext(Dispatchers.Main) {
                    action.invoke()
                }
            }
            val response = client.getRequestService().downloadFile(url)
            val body = response.body()
            if (body == null) {
                completeAction?.let { action ->
                    withContext(Dispatchers.Main) {
                        action.invoke(url, filePath)
                    }
                }
            } else {
                // 获取文件总大小
                val contentLength = body.contentLength()
                var totalBytesRead: Long = 0
                var lastProgress = -1
                // 使用更大的缓冲区 (8KB)
                val buffer = ByteArray(8 * 1024)
                body.byteStream().use { inputStream ->
                    var file = File(filePath)
                    FileOutputStream(file).use { outputStream ->
                        var bytesRead: Int = 0
                        while (!isCancelled && inputStream.read(buffer).also {
                                bytesRead = it
                            } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead

                            // 限制进度更新频率 (每1%或每1秒更新一次)
                            val progress = if (contentLength > 0) {
                                (totalBytesRead * 100 / contentLength).toInt()
                            } else 0

                            if (progress != lastProgress || totalBytesRead == contentLength) {
                                lastProgress = progress
                                withContext(Dispatchers.Main) {
                                    progressAction?.invoke(totalBytesRead, contentLength, progress)
                                }
                            }
                        }
                        completeAction?.let { action ->
                            withContext(Dispatchers.Main) {
                                action.invoke(url, filePath)
                            }
                        }
                        successAction?.let { action ->
                            withContext(Dispatchers.Main) {
                                action.invoke(url, file)
                            }
                        }
                    }
                }
            }
        } catch (cause: Throwable) {
            cause.printStackTrace()
            if (cause is CancellationException) {
                // do nothing
            } else {
                completeAction?.let { action ->
                    withContext(Dispatchers.Main) {
                        action.invoke(url, filePath)
                    }
                }
                errorAction?.let { action ->
                    withContext(Dispatchers.Main) {
                        action.invoke(url, cause)
                    }
                }
            }
        }
    }
}