package nearby.lib.netwrok.download

import java.io.File

sealed class DownloadStatus {
    object None : DownloadStatus() //空状态
    data class Progress(val value: Int) : DownloadStatus() //下载进度
    data class Error(val throwable: Throwable) : DownloadStatus() //错误
    data class Done(val file: File) : DownloadStatus() //完成
}

