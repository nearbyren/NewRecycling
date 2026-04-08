//package com.recycling.toolsapp.utils
//
//
//import android.annotation.SuppressLint
//import android.app.*
//import android.content.Context
//import android.content.Intent
//import android.os.Build
//import android.os.IBinder
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import com.recycling.toolsapp.R
//
///**
// * 前台推流服务（后台持续运行）
// * 调用方式：
// *   QiniuPushService.startPush(context, localPath, rtmpUrl)
// *   QiniuPushService.stopPush(context)
// */
//class QiniuPushService : Service() {
//
//    companion object {
//        private const val TAG = "QiniuPushService"
//        private const val CHANNEL_ID = "qiniu_push_channel"
//        private const val NOTIF_ID = 1001
//
//        private const val KEY_VIDEO = "video_path"
//        private const val KEY_URL = "rtmp_url"
//        private const val KEY_LOOP = "loop"
//
//        fun startPush(context: Context, videoPath: String, rtmpUrl: String, loop: Boolean = true) {
//            val intent = Intent(context, QiniuPushService::class.java).apply {
//                putExtra(KEY_VIDEO, videoPath)
//                putExtra(KEY_URL, rtmpUrl)
//                putExtra(KEY_LOOP, loop)
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                context.startForegroundService(intent)
//            } else {
//                context.startService(intent)
//            }
//        }
//
//        fun stopPush(context: Context) {
//            context.stopService(Intent(context, QiniuPushService::class.java))
//            QiniuPushStreamManager.stopPush()
//        }
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.i(TAG, "推流服务已创建")
//        createNotificationChannel()
//        startForeground(NOTIF_ID, buildNotification("准备推流中..."))
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        val video = intent?.getStringExtra(KEY_VIDEO) ?: return START_NOT_STICKY
//        val url = intent.getStringExtra(KEY_URL) ?: return START_NOT_STICKY
//        val loop = intent.getBooleanExtra(KEY_LOOP, true)
//
//        Log.i(TAG, "开始后台推流：$url")
//
//        QiniuPushStreamManager.startPush(video, url, loop, object : QiniuPushStreamManager.PushStreamListener {
//            override fun onStart() {
//                updateNotification("推流中...")
//            }
//
//            override fun onSuccess() {
//                updateNotification("推流完成 ✅")
//                stopSelf()
//            }
//
//            override fun onError(msg: String) {
//                updateNotification("推流错误: $msg")
//                stopSelf()
//            }
//
//            override fun onCancel() {
//                updateNotification("推流已取消 ⚠️")
//                stopSelf()
//            }
//        })
//
//        return START_STICKY
//    }
//
//    override fun onDestroy() {
//        Log.i(TAG, "推流服务已销毁")
//        QiniuPushStreamManager.stopPush()
//        super.onDestroy()
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    // === 通知相关 ===
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val channel = NotificationChannel(
//                CHANNEL_ID, "七牛推流服务", NotificationManager.IMPORTANCE_LOW
//            )
//            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
//                .createNotificationChannel(channel)
//        }
//    }
//
//    private fun buildNotification(content: String): Notification {
//        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setSmallIcon(R.mipmap.ic_launcher_by)
//            .setContentTitle("七牛云推流服务")
//            .setContentText(content)
//            .setOngoing(true)
//            .build()
//    }
//
//    @SuppressLint("NotificationPermission") private fun updateNotification(msg: String) {
//        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        manager.notify(NOTIF_ID, buildNotification(msg))
//    }
//}
