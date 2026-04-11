package com.recycling.toolsapp.utils

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.util.LinkedList
import java.util.Queue

object MediaPlayerQueueHelper {

    private var mediaPlayer: MediaPlayer? = null
    private val queue: Queue<String> = LinkedList()  // 存储待播放的音频文件名队列
    private var isPlaying = false  // 标记当前是否正在播放音频
    private val lock = Any()  // 用于同步的锁对象

    /**
     * 播放指定的音频文件
     * @param context 上下文，用于访问 Assets
     * @param fileName 文件名，相对于 `assets/media/` 路径
     * @param onCompletion 播放完成后的回调
     */
    private fun playAudio(context: Context, fileName: String, onCompletion: () -> Unit) {
        synchronized(lock) {
            releaseMediaPlayer() // 确保释放之前的资源

            try {
                val assetFileDescriptor = context.assets.openFd("media/$fileName.mp3")
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(assetFileDescriptor.fileDescriptor, assetFileDescriptor.startOffset, assetFileDescriptor.length)
                    setOnCompletionListener {
                        // 播放完成后，回调并播放下一个音频
                        onCompletion()
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MediaPlayerQueueHelper", "播放音频出错：${e.message}")
            }
        }
    }

    /**
     * 释放 MediaPlayer 资源
     */
    private fun releaseMediaPlayer() {
        synchronized(lock) {
            mediaPlayer?.let {
                it.release()
                mediaPlayer = null
            }
        }
    }

    /**
     * 播放队列中的音频文件
     * @param context 上下文，用于访问 Assets
     * @param fileNames 音频文件名列表
     */
    fun playAudioQueue(context: Context, fileNames: List<String>) {
        synchronized(lock) {
            // 将文件添加到队列中
            queue.addAll(fileNames)

            if (!isPlaying) {
                playNext(context)
            }
        }
    }

    /**
     * 播放队列中的下一个音频文件
     * @param context 上下文，用于访问 Assets
     */
    private fun playNext(context: Context) {
        if (queue.isNotEmpty()) {
            val nextFile = queue.poll()  // 获取队列中的下一个文件
            isPlaying = true

            // 播放音频
            playAudio(context, nextFile) {
                // 播放完成后，播放队列中的下一个音频
                playNext(context)
            }
        } else {
            isPlaying = false  // 如果队列为空，停止播放
            Log.d("MediaPlayerQueueHelper", "所有音频播放完成")
        }
    }

    /**
     * 停止播放并清空队列
     */
    fun stopAudio() {
        synchronized(lock) {
            mediaPlayer?.takeIf { it.isPlaying }?.stop()
            releaseMediaPlayer()
            queue.clear()
            isPlaying = false
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
