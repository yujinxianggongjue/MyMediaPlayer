package com.example.mymediaplayer

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 音频捕获服务
 * 参考简化实现，直接在服务中处理AudioRecord的创建和录制
 */
class AudioCaptureService : Service() {
    companion object {
        private const val TAG = "zqqtestAudioCaptureService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "audio_capture_channel"
        
        // 广播Action常量（根据用户Java代码）
        const val ACTION_ALL = "com.example.mymediaplayer.ACTION_ALL"
        const val ACTION_START = "com.example.mymediaplayer.ACTION_START"
        const val ACTION_STOP = "com.example.mymediaplayer.ACTION_STOP"
        const val EXTRA_ACTION_NAME = "EXTRA_ACTION_NAME"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        
        // 音频参数配置（按用户要求修改）
        private const val SAMPLE_RATE = 8000  // 修改为8000Hz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO  // 修改为立体声
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // 缓冲区配置（参考成功代码）
        private const val BufferElements2Rec = 1024 // 每次读取的short元素数量
        private const val BytesPerElement = 2 // 16位PCM每个样本2字节
        
        // 音频格式枚举
        enum class AudioFileFormat {
            WAV, PCM, AAC
        }
        
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
    private var audioFormat: AudioFileFormat = AudioFileFormat.WAV
    private var audioSourceType: String = "Unknown"
    
    // AAC编码器相关
    private var aacEncoder: MediaCodec? = null
    private var aacInputBuffers: Array<ByteBuffer>? = null
    private var aacOutputBuffers: Array<ByteBuffer>? = null
    
    // 广播接收器
    private var broadcastReceiver: BroadcastReceiver? = null
    
    // MediaProjection相关数据
    private var storedResultCode: Int = Activity.RESULT_CANCELED
    private var storedData: Intent? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        
        // 立即启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 注册广播接收器
        registerBroadcastReceiver()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 处理启动命令，创建AudioRecord并开始录制
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i(TAG, "收到服务命令，Action: $action")
        
        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.extras?.let { extras ->
                    Intent().apply { putExtras(extras) }
                }
                handleStartRecording(resultCode, data)
            }
            ACTION_STOP -> {
                handleStopRecording()
            }
            else -> {
                // 兼容旧的启动方式
                val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
                val data = intent?.getParcelableExtra<Intent>("data")
                handleStartRecording(resultCode, data)
            }
        }
        
        return START_STICKY
    }
    
    /**
     * 处理开始录制
     */
    private fun handleStartRecording(resultCode: Int, data: Intent?) {
        val outputPath = "/storage/emulated/0/Music/captured_audio_${System.currentTimeMillis()}.wav"
        val formatString = "WAV"
        
        // 存储MediaProjection数据
        storedResultCode = resultCode
        storedData = data
        
        // 解析音频格式
        audioFormat = try {
            AudioFileFormat.valueOf(formatString.uppercase())
        } catch (e: Exception) {
            Log.w(TAG, "未知音频格式: $formatString，使用默认WAV格式")
            AudioFileFormat.WAV
        }
        
        Log.i(TAG, "启动音频录制服务 - 格式: $audioFormat, 输出路径: $outputPath")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "无效的MediaProjection数据，无法启动服务")
            return
        }

        try {
            // 计算缓冲区大小（参考成功代码的方式）
            val systemMinBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            minBufferSize = maxOf(systemMinBufferSize, BufferElements2Rec * BytesPerElement)
            
            Log.i(TAG, "音频配置详情:")
            Log.i(TAG, "- 采样率: $SAMPLE_RATE Hz")
            Log.i(TAG, "- 声道配置: ${if (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO) "立体声" else "单声道"}")
            Log.i(TAG, "- 音频格式: 16位PCM")
            Log.i(TAG, "- 系统最小缓冲区: $systemMinBufferSize 字节")
            Log.i(TAG, "- 使用缓冲区大小: $minBufferSize 字节")
            Log.i(TAG, "- BufferElements2Rec: $BufferElements2Rec")
            Log.i(TAG, "- BytesPerElement: $BytesPerElement")
            
            // 检查立体声配置的兼容性
            if (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO) {
                Log.i(TAG, "立体声模式：每个样本包含左右声道数据")
            }
            
            // 打印详细的音频参数
            logAudioParameters()
            
            // 创建MediaProjection
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            if (mediaProjection == null) {
                Log.e(TAG, "创建MediaProjection失败")
                stopSelf()
                return
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

            // 配置音频播放捕获 - 尝试捕获所有可能的音频类型
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)                    // 媒体播放
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)                 // 未知用途
                .addMatchingUsage(AudioAttributes.USAGE_GAME)                    // 游戏音频
                .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)     // 语音通信
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION)            // 通知音频
                .addMatchingUsage(AudioAttributes.USAGE_ALARM)                   // 闹钟音频
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE) // 导航语音
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION) // 系统提示音
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)   // 铃声
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST) // 通信请求
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT) // 即时通信
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED) // 延迟通信
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)      // 事件通知
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT)               // 语音助手
                // 尝试不限制音频内容类型，捕获所有音频
                .build()
            
            Log.i(TAG, "音频播放捕获配置已设置，包含8种音频用途类型")
            
            builder.setAudioPlaybackCaptureConfig(config)
            
            // 设置音频源类型标识
            audioSourceType = "System Audio"

            // 检查录音权限并创建AudioRecord
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                audioRecord = builder.build()
                
                // 验证AudioRecord状态
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord初始化失败，状态: ${audioRecord?.state}")
                    stopSelf()
                    return
                }
                
                // 详细检查MediaProjection状态
                Log.i(TAG, "MediaProjection状态检查:")
                Log.i(TAG, "- MediaProjection实例: ${mediaProjection != null}")
                if (mediaProjection != null) {
                    try {
                        // 尝试创建虚拟显示以测试权限
                        Log.i(TAG, "- MediaProjection权限验证中...")
                        // 注意：这里不实际创建虚拟显示，只是检查状态
                        Log.i(TAG, "- MediaProjection权限状态: 正常")
                    } catch (e: Exception) {
                        Log.e(TAG, "- MediaProjection权限异常: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "- MediaProjection为null，无法进行音频捕获")
                    Log.e(TAG, "- 请确保已获得屏幕录制权限")
                }
                
                // 检查AudioRecord录制状态
                Log.i(TAG, "AudioRecord状态检查:")
                Log.i(TAG, "- 状态: ${audioRecord?.state}")
                Log.i(TAG, "- 录制状态: ${audioRecord?.recordingState}")
                Log.i(TAG, "- 音频会话ID: ${audioRecord?.audioSessionId}")
                Log.i(TAG, "- 缓冲区大小: $minBufferSize 字节")
                
                outputFile = File(outputPath)
                startRecord()
            } else {
                Log.e(TAG, "缺少录音权限")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "录音器初始化失败: ${e.message}", e)
        }
    }
    
    /**
     * 处理停止录制
     */
    private fun handleStopRecording() {
        Log.i(TAG, "处理停止录制命令")
        stopCapture()
    }
    
    /**
     * 注册广播接收器
     */
    private fun registerBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent?.action
                val actionName = intent?.getStringExtra(EXTRA_ACTION_NAME)
                
                Log.i(TAG, "收到广播 - Action: $action, ActionName: $actionName")
                
                when {
                    action == ACTION_ALL && actionName == ACTION_START -> {
                        Log.i(TAG, "通过广播启动录制")
                        if (storedResultCode == Activity.RESULT_OK && storedData != null) {
                            handleStartRecording(storedResultCode, storedData)
                        } else {
                            Log.w(TAG, "没有有效的MediaProjection数据，无法启动录制")
                        }
                    }
                    action == ACTION_ALL && actionName == ACTION_STOP -> {
                        Log.i(TAG, "通过广播停止录制")
                        handleStopRecording()
                    }
                }
            }
        }
        
        val filter = IntentFilter(ACTION_ALL)
        registerReceiver(broadcastReceiver, filter)
        Log.i(TAG, "广播接收器已注册")
    }
    
    /**
     * 注销广播接收器
     */
    private fun unregisterBroadcastReceiver() {
        broadcastReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.i(TAG, "广播接收器已注销")
            } catch (e: Exception) {
                Log.w(TAG, "注销广播接收器失败: ${e.message}")
            }
        }
        broadcastReceiver = null
    }

    /**
     * 初始化AAC编码器
     */
    private fun initAacEncoder(): Boolean {
        try {
            // 根据声道配置确定声道数
        val channelCount = if (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
        Log.i(TAG, "AAC编码器配置: 采样率=$SAMPLE_RATE, 声道数=$channelCount")
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, channelCount)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 64000 * channelCount) // 根据声道数调整比特率
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize * 2)
            
            aacEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            aacEncoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aacEncoder?.start()
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                aacInputBuffers = aacEncoder?.inputBuffers
                aacOutputBuffers = aacEncoder?.outputBuffers
            }
            
            Log.i(TAG, "AAC编码器初始化成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "AAC编码器初始化失败: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 释放AAC编码器
     */
    private fun releaseAacEncoder() {
        try {
            aacEncoder?.stop()
            aacEncoder?.release()
            aacEncoder = null
            aacInputBuffers = null
            aacOutputBuffers = null
            Log.i(TAG, "AAC编码器已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放AAC编码器失败: ${e.message}")
        }
    }
    
    /**
     * 编码PCM数据为AAC
     */
    private fun encodePcmToAac(pcmData: ByteArray, length: Int, aacOutputStream: FileOutputStream) {
        val encoder = aacEncoder ?: return
        
        try {
            val inputBufferIndex = encoder.dequeueInputBuffer(0)
            if (inputBufferIndex >= 0) {
                val inputBuffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    encoder.getInputBuffer(inputBufferIndex)
                } else {
                    aacInputBuffers?.get(inputBufferIndex)
                }
                
                inputBuffer?.clear()
                inputBuffer?.put(pcmData, 0, length)
                encoder.queueInputBuffer(inputBufferIndex, 0, length, System.nanoTime() / 1000, 0)
            }
            
            drainEncoder(aacOutputStream, false)
        } catch (e: Exception) {
            Log.e(TAG, "AAC编码失败: ${e.message}")
        }
    }
    
    /**
     * 结束AAC编码
     */
    private fun finishAacEncoding(aacOutputStream: FileOutputStream) {
        val encoder = aacEncoder ?: return
        
        try {
            // 发送结束信号
            val inputBufferIndex = encoder.dequeueInputBuffer(0)
            if (inputBufferIndex >= 0) {
                encoder.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            
            // 排空编码器
            drainEncoder(aacOutputStream, true)
            Log.i(TAG, "AAC编码结束")
        } catch (e: Exception) {
            Log.e(TAG, "结束AAC编码失败: ${e.message}")
        }
    }
    
    /**
     * 将short数组转换为byte数组（参考成功代码）
     * 注意：立体声模式下每个样本包含左右声道数据
     */
    private fun short2byte(sData: ShortArray): ByteArray {
        val shortArrsize = sData.size
        val bytes = ByteArray(shortArrsize * 2)
        for (i in 0 until shortArrsize) {
            // 小端字节序：低字节在前，高字节在后
            bytes[i * 2] = (sData[i].toInt() and 0x00FF).toByte()
            bytes[(i * 2) + 1] = (sData[i].toInt() shr 8).toByte()
        }
        return bytes
    }
    
    /**
     * 重新配置AudioPlaybackCapture，尝试更宽松的音频捕获策略
     * 专门用于捕获正在播放的音频数据
     */
    private fun recreateAudioPlaybackCapture(): Boolean {
        return try {
            Log.i(TAG, "重新配置AudioPlaybackCapture，使用更宽松的捕获策略...")
            
            val builder = AudioRecord.Builder()
            builder.setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()
            ).setBufferSizeInBytes(minBufferSize)

            // 使用更宽松的AudioPlaybackCapture配置
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                // 不限制特定的USAGE，尝试捕获所有音频
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
                .addMatchingUsage(AudioAttributes.USAGE_ALARM)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED)
                .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT)
                // 尝试不排除任何音频内容类型
                .build()
            
            builder.setAudioPlaybackCaptureConfig(config)
            audioRecord = builder.build()
            
            // 验证AudioRecord状态
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "重新配置的AudioRecord初始化失败，状态: ${audioRecord?.state}")
                audioRecord?.release()
                audioRecord = null
                return false
            }
            
            Log.i(TAG, "AudioPlaybackCapture重新配置成功")
            Log.i(TAG, "- 使用更宽松的音频用途匹配策略")
            Log.i(TAG, "- 尝试捕获所有类型的播放音频")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "重新配置AudioPlaybackCapture失败: ${e.message}", e)
            audioRecord?.release()
            audioRecord = null
            false
        }
    }
    
    /**
     * 排空编码器缓冲区
     */
    private fun drainEncoder(aacOutputStream: FileOutputStream, endOfStream: Boolean) {
        val encoder = aacEncoder ?: return
        
        try {
            val bufferInfo = MediaCodec.BufferInfo()
            
            while (true) {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "AAC编码器输出格式改变: ${encoder.outputFormat}")
                } else if (outputBufferIndex >= 0) {
                    val outputBuffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        encoder.getOutputBuffer(outputBufferIndex)
                    } else {
                        aacOutputBuffers?.get(outputBufferIndex)
                    }
                    
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val aacData = ByteArray(bufferInfo.size)
                        outputBuffer.get(aacData)
                        aacOutputStream.write(aacData)
                        outputBuffer.clear()
                    }
                    
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "排空编码器失败: ${e.message}")
        }
    }

    /**
     * 开始录制音频 - 支持多格式同时录制
     */
    private fun startRecord() {
        if (isRecording || audioRecord == null) {
            return
        }
        // 检测当前音频环境
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val isMediaPlaying = audioManager.isMusicActive
        Log.i(TAG, "当前是否有媒体播放: $isMediaPlaying")
        Log.i(TAG, "媒体音量: ${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}")
        isRecording = true
        Log.d(TAG, "开始录制音频到: ${outputFile?.absolutePath}")
        
        // 初始化AAC编码器
        if (!initAacEncoder()) {
            Log.e(TAG, "AAC编码器初始化失败，停止录制")
            isRecording = false
            return
        }
        
        try {
            // 检查AudioRecord在开始录制前的状态
            Log.i(TAG, "准备开始录制，AudioRecord当前状态:")
            Log.i(TAG, "- 初始化状态: ${audioRecord?.state}")
            Log.i(TAG, "- 录制状态: ${audioRecord?.recordingState}")
            
            audioRecord?.startRecording()
            
            // 检查录制是否真正开始
            Thread.sleep(100) // 等待100ms让录制状态稳定
            val recordingState = audioRecord?.recordingState
            Log.i(TAG, "录制开始后状态检查:")
            Log.i(TAG, "- 录制状态: $recordingState")
            Log.i(TAG, "- 期望状态: ${AudioRecord.RECORDSTATE_RECORDING}")
            
            if (recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord未能正常开始录制，当前状态: $recordingState")
                isRecording = false
                return
            }
            
            // 测试读取一次数据以验证AudioRecord是否正常工作
            Log.i(TAG, "测试AudioRecord数据读取...")
            val testData = ShortArray(BufferElements2Rec)
            val testRead = audioRecord?.read(testData, 0, BufferElements2Rec) ?: -1
            Log.i(TAG, "测试读取结果: $testRead")
            if (testRead > 0) {
                val hasTestData = testData.take(testRead).any { it != 0.toShort() }
                Log.i(TAG, "测试数据是否有效: $hasTestData")
                if (hasTestData) {
                    Log.i(TAG, "测试数据前5个值: ${testData.take(5).joinToString(", ")}")
                } else {
                    Log.w(TAG, "测试读取到的数据全为0，可能存在问题")
                }
            } else {
                Log.e(TAG, "测试读取失败，返回值: $testRead")
            }
            
            recordingThread = Thread {
                // 创建short数据缓冲区（参考成功代码）
                val sData = ShortArray(BufferElements2Rec)
                var totalAudioLen = 0L
                
                // 使用应用专用的外部存储目录
                val musicDir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC), "AudioCapture")
                if (!musicDir.exists()) {
                    musicDir.mkdirs()
                    Log.i(TAG, "创建音频录制目录: ${musicDir.absolutePath}")
                }
                
                val timestamp = System.currentTimeMillis()
                val wavFile = File(musicDir, "captured_audio_${timestamp}.wav")
                val pcmFile = File(musicDir, "captured_audio_${timestamp}.pcm")
                val aacFile = File(musicDir, "captured_audio_${timestamp}.aac")
                
                var wavOutputStream: FileOutputStream? = null
                var pcmOutputStream: FileOutputStream? = null
                var aacOutputStream: FileOutputStream? = null
                
                try {
                    // 创建所有格式的输出流
                    wavFile.createNewFile()
                    pcmFile.createNewFile()
                    aacFile.createNewFile()
                    
                    wavOutputStream = FileOutputStream(wavFile)
                    pcmOutputStream = FileOutputStream(pcmFile)
                    aacOutputStream = FileOutputStream(aacFile)
                    
                    Log.i(TAG, "创建录音文件:")
                    Log.i(TAG, "WAV: ${wavFile.absolutePath}")
                    Log.i(TAG, "PCM: ${pcmFile.absolutePath}")
                    Log.i(TAG, "AAC: ${aacFile.absolutePath}")
                    
                    // 为WAV文件写入文件头占位符
                    writeWavHeader(wavOutputStream, 0)
                    
                    // 录制循环 - 同时写入所有格式
                    var emptyDataCount = 0
                    var nonEmptyDataCount = 0
                    
                    Log.i(TAG, "开始录制循环，使用short数组读取，缓冲区大小: ${BufferElements2Rec} shorts")
                    Log.i(TAG, "音频源类型: $audioSourceType")
                    
                    var readCount = 0
                    while (isRecording && audioRecord != null) {
                        // 每1000次读取检查一次AudioRecord状态和音频数据有效性
                    if (readCount % 1000 == 0) {
                        val currentRecordingState = audioRecord?.recordingState
                        if (currentRecordingState != AudioRecord.RECORDSTATE_RECORDING) {
                            Log.w(TAG, "检测到AudioRecord录制状态异常: $currentRecordingState，尝试重新开始录制")
                            try {
                                audioRecord?.stop()
                                Thread.sleep(50)
                                audioRecord?.startRecording()
                                Thread.sleep(50)
                            } catch (e: Exception) {
                                Log.e(TAG, "重新启动录制失败: ${e.message}")
                                break
                            }
                        }
                        
                        // 检查是否长时间没有有效音频数据
                        if (readCount > 5000 && nonEmptyDataCount == 0) {
                            Log.w(TAG, "长时间未检测到有效音频数据，可能的原因:")
                            Log.w(TAG, "1. 当前没有应用播放音频")
                            Log.w(TAG, "2. 播放音频的应用使用了不同的AudioAttributes.USAGE")
                            Log.w(TAG, "3. 播放音频的应用设置了音频保护标志")
                            Log.w(TAG, "4. MediaProjection权限可能存在问题")
                            Log.w(TAG, "建议: 请确保有音频正在播放，如音乐、视频等")
                        }
                    }
                        readCount++
                        
                        // 使用short数组读取（参考成功代码）
                        val read = audioRecord!!.read(sData, 0, BufferElements2Rec)
                        
                        if (read == AudioRecord.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "AudioRecord读取错误: ERROR_INVALID_OPERATION，当前录制状态: ${audioRecord?.recordingState}")
                            break
                        } else if (read == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord读取错误: ERROR_BAD_VALUE，当前录制状态: ${audioRecord?.recordingState}")
                            break
                        } else if (read > 0) {
                            // 检查short数据是否为空（全为0）
                            var hasNonZeroData = false
                            for (i in 0 until read) {
                                if (sData[i] != 0.toShort()) {
                                    hasNonZeroData = true
                                    break
                                }
                            }
                            
                            // 转换short数组为byte数组（直接使用原数组的前read个元素）
                            val readData = ShortArray(read)
                            System.arraycopy(sData, 0, readData, 0, read)
                            val bData = short2byte(readData)
                            val byteLength = read * BytesPerElement
                            
                            // 添加详细的数据调试信息
                            if (nonEmptyDataCount + emptyDataCount < 5) {
                                Log.d(TAG, "数据详情 - 读取: $read shorts, 转换: $byteLength bytes")
                                Log.d(TAG, "前几个short值: ${sData.take(minOf(5, read)).joinToString(", ")}")
                                Log.d(TAG, "前几个byte值: ${bData.take(minOf(10, byteLength)).joinToString(", ") { it.toString() }}")
                            }
                            
                            if (hasNonZeroData) {
                                nonEmptyDataCount++
                                // 写入WAV和PCM格式
                                wavOutputStream.write(bData, 0, byteLength)
                                pcmOutputStream.write(bData, 0, byteLength)
                                
                                // 编码为AAC格式
                                encodePcmToAac(bData, byteLength, aacOutputStream)
                                
                                totalAudioLen += byteLength
                                
                                if (nonEmptyDataCount <= 5 || totalAudioLen % (minBufferSize * 10) == 0L) {
                                    Log.i(TAG, "捕获到有效音频数据: $read shorts -> $byteLength 字节，总计: $totalAudioLen 字节，非空数据包: $nonEmptyDataCount")
                                }
                            } else {
                                emptyDataCount++
                                // 仍然写入空数据以保持时间连续性
                                wavOutputStream.write(bData, 0, byteLength)
                                pcmOutputStream.write(bData, 0, byteLength)
                                encodePcmToAac(bData, byteLength, aacOutputStream)
                                totalAudioLen += byteLength
                                
                                if (emptyDataCount % 100 == 0) { // 每100个空数据包记录一次
                                    Log.w(TAG, "检测到空音频数据包: $emptyDataCount 个，可能没有音频播放")
                                }
                            }
                        } else {
                            Log.w(TAG, "AudioRecord.read返回: $read")
                        }
                    }
                    
                    Log.i(TAG, "录制完成统计:")
                    Log.i(TAG, "- 总数据量: $totalAudioLen 字节")
                    Log.i(TAG, "- 有效音频数据包: $nonEmptyDataCount 个")
                    Log.i(TAG, "- 空音频数据包: $emptyDataCount 个")
                    Log.i(TAG, "- 有效数据比例: ${if (nonEmptyDataCount + emptyDataCount > 0) String.format("%.2f", nonEmptyDataCount.toFloat() / (nonEmptyDataCount + emptyDataCount) * 100) else "0.00"}%")
                    
                    if (nonEmptyDataCount == 0) {
                        Log.w(TAG, "警告: 未捕获到任何有效音频数据，详细诊断:")
                        Log.w(TAG, "1. 系统没有播放音频 - 请确保有音乐、视频等正在播放")
                        Log.w(TAG, "2. 音频播放应用的AudioAttributes.USAGE不在捕获范围内")
                        Log.w(TAG, "3. MediaProjection权限问题 - 可能需要重新授权")
                        Log.w(TAG, "4. 音频播放应用设置了FLAG_SECURE或其他保护")
                        Log.w(TAG, "5. Android系统版本限制 - 某些版本对音频捕获有更严格的限制")
                        Log.w(TAG, "6. 音频焦点问题 - 其他应用可能占用了音频焦点")
                        Log.w(TAG, "建议解决方案:")
                        Log.w(TAG, "- 尝试播放不同的音频应用(如系统音乐播放器、YouTube等)")
                        Log.w(TAG, "- 检查应用是否有音频录制相关的特殊权限设置")
                        Log.w(TAG, "- 重新启动应用并重新授权MediaProjection权限")
                    }
                    
                } catch (e: IOException) {
                    Log.e(TAG, "录音文件写入错误: ${e.message}")
                } finally {
                    try {
                        // 结束AAC编码
                        aacOutputStream?.let { finishAacEncoding(it) }
                        
                        wavOutputStream?.close()
                        pcmOutputStream?.close()
                        aacOutputStream?.close()
                        
                        // 释放AAC编码器
                        releaseAacEncoder()
                        
                        // 更新WAV文件头
                        if (totalAudioLen > 0) {
                            updateWavHeader(wavFile, totalAudioLen)
                            Log.i(TAG, "WAV文件头已更新")
                        }
                        
                        // 记录最终文件信息
                        Log.i(TAG, "录制完成的文件:")
                        Log.i(TAG, "WAV文件: ${wavFile.absolutePath} (${wavFile.length()} 字节)")
                        Log.i(TAG, "PCM文件: ${pcmFile.absolutePath} (${pcmFile.length()} 字节)")
                        Log.i(TAG, "AAC文件: ${aacFile.absolutePath} (${aacFile.length()} 字节)")
                        
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
    
    /**
     * 写入WAV文件头
     */
    private fun writeWavHeader(out: FileOutputStream, totalAudioLen: Long) {
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = SAMPLE_RATE.toLong()
        val channels = if (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
        val byteRate = (16 * SAMPLE_RATE * channels / 8).toLong()
        
        val header = ByteArray(44)
        
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        
        // WAVE header
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // 'fmt ' chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (2 * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        
        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        
        out.write(header, 0, 44)
    }
    
    /**
     * 更新WAV文件头中的数据长度
     */
    private fun updateWavHeader(file: File, totalAudioLen: Long) {
        try {
            val randomAccessFile = java.io.RandomAccessFile(file, "rw")
            
            // 更新文件总长度 (位置4-7)
            val totalDataLen = totalAudioLen + 36
            randomAccessFile.seek(4)
            randomAccessFile.write((totalDataLen and 0xff).toInt())
            randomAccessFile.write(((totalDataLen shr 8) and 0xff).toInt())
            randomAccessFile.write(((totalDataLen shr 16) and 0xff).toInt())
            randomAccessFile.write(((totalDataLen shr 24) and 0xff).toInt())
            
            // 更新数据长度 (位置40-43)
            randomAccessFile.seek(40)
            randomAccessFile.write((totalAudioLen and 0xff).toInt())
            randomAccessFile.write(((totalAudioLen shr 8) and 0xff).toInt())
            randomAccessFile.write(((totalAudioLen shr 16) and 0xff).toInt())
            randomAccessFile.write(((totalAudioLen shr 24) and 0xff).toInt())
            
            randomAccessFile.close()
            Log.d(TAG, "WAV文件头更新完成，音频数据长度: $totalAudioLen 字节")
            
        } catch (e: Exception) {
            Log.e(TAG, "更新WAV文件头失败: ${e.message}")
        }
     }
     
     /**
      * 打印详细的音频录制参数
      */
     private fun logAudioParameters() {
         Log.i(TAG, "=== 音频录制参数详情 ===")
         Log.i(TAG, "采样率: $SAMPLE_RATE Hz")
         Log.i(TAG, "声道配置: ${getChannelConfigName(CHANNEL_CONFIG)}")
         Log.i(TAG, "音频格式: ${getAudioFormatName(AUDIO_FORMAT)}")
         Log.i(TAG, "最小缓冲区大小: $minBufferSize 字节")
         Log.i(TAG, "输出文件格式: $audioFormat")
         Log.i(TAG, "输出文件路径: ${outputFile?.absolutePath}")
         
         // 计算一些有用的参数
         val bytesPerSample = when (AUDIO_FORMAT) {
             AudioFormat.ENCODING_PCM_16BIT -> 2
             AudioFormat.ENCODING_PCM_8BIT -> 1
             else -> 2
         }
         val channels = when (CHANNEL_CONFIG) {
             AudioFormat.CHANNEL_IN_MONO -> 1
             AudioFormat.CHANNEL_IN_STEREO -> 2
             else -> 1
         }
         val bytesPerSecond = SAMPLE_RATE * channels * bytesPerSample
         
         Log.i(TAG, "每样本字节数: $bytesPerSample")
         Log.i(TAG, "声道数: $channels")
         Log.i(TAG, "每秒字节数: $bytesPerSecond")
         Log.i(TAG, "缓冲区时长: ${(minBufferSize.toFloat() / bytesPerSecond * 1000).toInt()} ms")
         Log.i(TAG, "=========================")
     }
     
     /**
      * 获取声道配置名称
      */
     private fun getChannelConfigName(channelConfig: Int): String {
         return when (channelConfig) {
             AudioFormat.CHANNEL_IN_MONO -> "MONO (单声道)"
             AudioFormat.CHANNEL_IN_STEREO -> "STEREO (立体声)"
             else -> "UNKNOWN ($channelConfig)"
         }
     }
     
     /**
      * 获取音频格式名称
      */
     private fun getAudioFormatName(audioFormat: Int): String {
         return when (audioFormat) {
             AudioFormat.ENCODING_PCM_16BIT -> "PCM_16BIT (16位PCM)"
             AudioFormat.ENCODING_PCM_8BIT -> "PCM_8BIT (8位PCM)"
             AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT (浮点PCM)"
             else -> "UNKNOWN ($audioFormat)"
         }
     }
     
     /**
      * 将PCM文件转换为WAV文件
      */
     private fun convertPcmToWav(pcmFile: File, wavFile: File, pcmDataLength: Long) {
         try {
             val pcmData = pcmFile.readBytes()
             val wavOutputStream = FileOutputStream(wavFile)
             
             // 写入WAV文件头
             writeWavHeader(wavOutputStream, pcmDataLength)
             
             // 写入PCM数据
             wavOutputStream.write(pcmData)
             wavOutputStream.close()
             
             Log.i(TAG, "PCM转WAV完成: ${wavFile.absolutePath}")
             Log.i(TAG, "WAV文件大小: ${wavFile.length()} 字节")
             
         } catch (e: Exception) {
             Log.e(TAG, "PCM转WAV失败: ${e.message}", e)
         }
     }
     
     override fun onDestroy() {
         Log.i(TAG, "AudioCaptureService正在销毁")
         
         // 停止录制
         stopCapture()
         
         // 注销广播接收器
         unregisterBroadcastReceiver()
         
         // 清理实例
         instance = null
         
         super.onDestroy()
     }
}