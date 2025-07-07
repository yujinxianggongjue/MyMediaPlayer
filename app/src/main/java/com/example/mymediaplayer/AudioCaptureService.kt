package com.example.mymediaplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File

class AudioCaptureService : Service() {
    companion object {
        private const val TAG = "AudioCaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_capture_channel"
        
        // 单例实例，方便MainActivity访问
        private var instance: AudioCaptureService? = null
        
        fun getInstance(): AudioCaptureService? {
            return instance
        }
    }

    private var mediaProjection: MediaProjection? = null
    private val audioPlaybackCapture = AudioPlaybackCapture()
    private var outputFile: File? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 立即启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 从Intent中获取MediaProjection数据
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        val outputPath = intent?.getStringExtra("outputPath")

        if (resultCode != Activity.RESULT_OK || data == null || outputPath == null) {
            Log.e(TAG, "无效的数据，无法启动服务")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // 创建MediaProjection
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(TAG, "创建MediaProjection失败")
                stopSelf()
                return START_NOT_STICKY
            }

            // 开始录制
            outputFile = File(outputPath)
            startCapture(outputPath)
            
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun startCapture(outputPath: String) {
        try {
            val outputFile = File(outputPath)
            Log.d(TAG, "Attempting to start audio capture to: $outputPath")
            
            // 检查MediaProjection是否有效
            if (mediaProjection == null) {
                throw IllegalStateException("MediaProjection is null")
            }
            
            audioPlaybackCapture.startCapture(mediaProjection!!, outputFile)
            Log.d(TAG, "Audio capture started successfully")
            
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "UnsupportedOperationException: ${e.message}")
            Log.e(TAG, "Audio playback capture is not supported on this device or app lacks system permissions")
            Log.e(TAG, "Make sure the app is signed as a system app and has CAPTURE_AUDIO_OUTPUT permission")
            
            // 发送错误通知
            sendErrorNotification("Audio capture not supported", "Device or permissions issue")
            stopSelf()
            
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            Log.e(TAG, "Audio capture permission denied")
            
            // 发送错误通知
            sendErrorNotification("Permission denied", "Audio capture permission required")
            stopSelf()
            
        } catch (e: IllegalStateException) {
            Log.e(TAG, "IllegalStateException: ${e.message}")
            Log.e(TAG, "Invalid state for audio capture")
            
            // 发送错误通知
            sendErrorNotification("Invalid state", "MediaProjection not available")
            stopSelf()
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during audio capture: ${e.message}", e)
            
            // 发送错误通知
            sendErrorNotification("Capture failed", "Unexpected error: ${e.message}")
            stopSelf()
        }
    }

    fun stopCapture(): String? {
        return audioPlaybackCapture.stopCapture()
    }

    fun isRecording(): Boolean {
        return audioPlaybackCapture.isRecording
    }

    override fun onDestroy() {
        audioPlaybackCapture.stopCapture()
        mediaProjection?.stop()
        mediaProjection = null
        instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音频捕获服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于捕获系统音频"
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在录制系统音频")
            .setContentText("点击返回应用")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun sendErrorNotification(title: String, message: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("录制失败: $title")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
}