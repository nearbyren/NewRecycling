package com.recycling.toolsapp.utils

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import com.serial.port.utils.Loge
import java.io.File

object MediaPlayerHelper {

    private var mediaPlayer: MediaPlayer? = null

    // 锁对象，用于同步操作
    private val lock = Any()

    /**
     * 初始化音量大小
     * @param context 上下文，用于访问 Assets
     * @param volume 音量大小
     */
    fun setVolume(context: Context, volume: Int) {
        // 获取AudioManager实例
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 设置媒体音量到最大值（通常是15）
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
        // 或者设置到某个具体的值，例如5
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    /**
     * 播放指定音频文件
     * @param context 上下文，用于访问 Assets
     * @param fileName 文件名，相对于 `assets/media/` 路径
     */
    fun playAudioAsset(context: Context, fileName: String) {
        synchronized(lock) {
            if (isPlaying()) {
//               Loge.d("有语音信息来了 语音播报我来了...播报进行中")
                return@synchronized
            }
//            Loge.d("有语音信息来了 语音播报我来了...播报开始")

            releaseMediaPlayer() // 确保释放之前的资源

            try {
                val assetFileDescriptor = context.assets.openFd("media/$fileName")
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                    setOnCompletionListener {
//                        Loge.d("有语音信息来了 语音播报我来了...播报完毕")
                        releaseMediaPlayer() // 确保释放之前的资源
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                releaseMediaPlayer() // 确保释放之前的资源
                Loge.d("有语音信息来了 播放音频出错：${e.message}")
            }
        }
    }

    /**
     * 播放指定音频文件
     * @param context 上下文，用于访问 Assets
     * @param fileName 文件名，相对于 `assets/media/` 路径
     */
    fun playAudio(filePath: String) {
        synchronized(lock) {
            if (isPlaying()) {
                Loge.d("音频播放中，跳过新请求: $filePath")
                return
            }

            Loge.d("开始播放音频: $filePath")
            releaseMediaPlayer() // 确保释放之前的资源

            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Loge.d("音频文件不存在: $filePath")
                    return
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    setOnCompletionListener {
                        Loge.d("音频播放完毕: $filePath")
                        releaseMediaPlayer()
                    }
                    setOnErrorListener { mp, what, extra ->
                        Loge.d("音频播放出错: $filePath, code: $what, extra: $extra")
                        releaseMediaPlayer()
                        true
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                releaseMediaPlayer()
                Loge.d("播放音频出错：${e.message}, 文件: $filePath")
            }
        }
    }

    /**
     * 从应用文件目录播放音频
     * @param context 上下文
     * @param path 相对路径（相对于应用文件目录）
     * @param fileName 文件名（不包含扩展名）
     */
    fun playAudioFromAppFiles(context: Context, path: String, fileName: String) {
        val fileDir = context.filesDir
        val fullPath = File(fileDir, "$path/$fileName").absolutePath
        playAudio(fullPath)
    }

    /**
     * 释放 MediaPlayer 资源
     */
    fun releaseMediaPlayer() {
        synchronized(lock) {
            mediaPlayer?.let {
                it.release()
                mediaPlayer = null
            }
        }
    }

    /**
     * 停止播放音频
     */
    fun stopAudio() {
        synchronized(lock) {
            mediaPlayer?.takeIf { it.isPlaying }?.stop()
            releaseMediaPlayer()
        }
    }

    /**
     * 检查是否正在播放
     */
    fun isPlaying(): Boolean {
        synchronized(lock) {
            return mediaPlayer?.isPlaying == true
        }
    }
}

