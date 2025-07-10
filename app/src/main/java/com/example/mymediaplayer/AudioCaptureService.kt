package com.example.mymediaplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 音频捕获服务
 * 参考简化实现，直接在服务中处理AudioRecord的创建和录制
 */
class AudioCaptureService : Service() {
    companion object {
        private const val TAG = "AudioCaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_capture_channel"
        
        // 音频参数配置
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // 单例实例，方便MainActivity访问
        private var instance: AudioCaptureService? = null
        
        fun getInstance(): AudioCaptureService? {
            return instance
        }
    }

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var minBufferSize: Int = 0
    private var isRecording = false
    private var outputFile: File? = null
    private var mediaProjection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        
        // 立即启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 处理启动命令，创建AudioRecord并开始录制
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        val outputPath = intent?.getStringExtra("outputPath")

        if (resultCode != Activity.RESULT_OK || data == null || outputPath == null) {
            Log.e(TAG, "无效的数据，无法启动服务")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // 计算最小缓冲区大小
            minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            
            // 创建MediaProjection
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Log.e(TAG, "创建MediaProjection失败")
                stopSelf()
                return START_NOT_STICKY
            }

            // 创建AudioRecord
            val builder = AudioRecord.Builder()
            builder.setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            ).setBufferSizeInBytes(minBufferSize)

            // 配置音频播放捕获
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()
            
            builder.setAudioPlaybackCaptureConfig(config)

            // 检查录音权限并创建AudioRecord
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                audioRecord = builder.build()
                outputFile = File(outputPath)
                startRecord()
            } else {
                Log.e(TAG, "缺少录音权限")
                stopSelf()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "录音器初始化失败: ${e.message}", e)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    /**
     * 开始录制音频
     */
    private fun startRecord() {
        if (isRecording || audioRecord == null) {
            return
        }
        
        isRecording = true
        Log.d(TAG, "开始录制音频到: ${outputFile?.absolutePath}")
        
        try {
            audioRecord?.startRecording()
            
            recordingThread = Thread {
                val data = ByteArray(minBufferSize)
                var fileOutputStream: FileOutputStream? = null
                
                try {
                    // 创建输出文件
                    outputFile?.let { file ->
                        if (!file.exists()) {
                            file.createNewFile()
                            Log.i(TAG, "创建录音文件: ${file.absolutePath}")
                        }
                        fileOutputStream = FileOutputStream(file)
                    }
                    
                    // 录制循环
                    while (isRecording && audioRecord != null) {
                        val read = audioRecord!!.read(data, 0, minBufferSize)
                        if (read != AudioRecord.ERROR_INVALID_OPERATION && read > 0) {
                            fileOutputStream?.write(data, 0, read)
                            Log.d(TAG, "写入录音数据: $read 字节")
                        }
                    }
                    
                } catch (e: IOException) {
                    Log.e(TAG, "录音文件写入错误: ${e.message}")
                } finally {
                    try {
                        fileOutputStream?.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "关闭文件流错误: ${e.message}")
                    }
                }
            }
            
            recordingThread?.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "开始录制失败: ${e.message}", e)
            isRecording = false
        }
    }

    /**
     * 停止录制
     */
    fun stopCapture(): String? {
        if (!isRecording) {
            return null
        }
        
        isRecording = false
        
        try {
            audioRecord?.stop()
            recordingThread?.join(1000) // 等待录制线程结束
        } catch (e: Exception) {
            Log.e(TAG, "停止录制错误: ${e.message}")
        }
        
        Log.d(TAG, "录制停止")
        return outputFile?.absolutePath
    }

    /**
     * 检查是否正在录制
     */
    fun isRecording(): Boolean {
        return isRecording
    }

    override fun onDestroy() {
        stopCapture()
        
        try {
            audioRecord?.release()
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "释放资源错误: ${e.message}")
        }
        
        audioRecord = null
        mediaProjection = null
        instance = null
        super.onDestroy()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "录音服务",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableLights(true)
                lightColor = Color.RED
                setShowBadge(true)
                description = "音频录制通知"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setAutoCancel(false)
            .setContentTitle("录音服务")
            .setContentText("录音服务正在运行...")
            .setWhen(System.currentTimeMillis())
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_foreground))
            .setContentIntent(pendingIntent)
            .build()
    }
}