package com.example.mymediaplayer

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 音频捕获策略接口
 * 定义了不同音频捕获方式的统一接口
 */
interface AudioCaptureStrategy {
    /**
     * 检查当前策略是否可用
     * @return true如果策略可用，false否则
     */
    fun isAvailable(): Boolean
    
    /**
     * 开始音频捕获
     * @return true如果成功开始，false否则
     */
    fun startCapture(): Boolean
    
    /**
     * 停止音频捕获
     */
    fun stopCapture()
    
    /**
     * 获取策略名称
     * @return 策略的描述性名称
     */
    fun getStrategyName(): String
    
    /**
     * 获取策略优先级（数字越小优先级越高）
     * @return 优先级数值
     */
    fun getPriority(): Int
    
    /**
     * 清理资源
     */
    fun cleanup()
}

/**
 * MediaProjection音频捕获策略
 * 使用MediaProjection API进行系统音频捕获
 */
class MediaProjectionCaptureStrategy(
    private val context: Context,
    private val compatManager: CompatMediaProjectionManager
) : AudioCaptureStrategy {
    
    companion object {
        private const val TAG = "zqqtestMediaProjectionStrategy"
    }
    
    private var isCapturing = false
    
    /**
     * 检查MediaProjection是否可用
     */
    override fun isAvailable(): Boolean {
        return try {
            // 检查系统是否支持MediaProjection
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            val available = mediaProjectionManager != null && 
                           compatManager.hasValidMediaProjection()
            
            Log.d(TAG, "MediaProjection可用性检查: $available")
            available
        } catch (e: Exception) {
            Log.e(TAG, "检查MediaProjection可用性失败", e)
            false
        }
    }
    
    /**
     * 开始MediaProjection音频捕获
     */
    override fun startCapture(): Boolean {
        return try {
            if (isCapturing) {
                Log.w(TAG, "MediaProjection捕获已在进行中")
                return true
            }
            
            val mediaProjection = compatManager.getMediaProjection()
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection实例为空")
                return false
            }
            
            // 这里应该实现具体的音频捕获逻辑
            // 例如创建AudioRecord或使用MediaProjection的音频捕获功能
            Log.i(TAG, "开始MediaProjection音频捕获")
            isCapturing = true
            
            // TODO: 实现具体的音频捕获逻辑
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "启动MediaProjection音频捕获失败", e)
            false
        }
    }
    
    /**
     * 停止MediaProjection音频捕获
     */
    override fun stopCapture() {
        try {
            if (!isCapturing) {
                Log.w(TAG, "MediaProjection捕获未在进行中")
                return
            }
            
            Log.i(TAG, "停止MediaProjection音频捕获")
            
            // TODO: 实现具体的停止逻辑
            
            isCapturing = false
        } catch (e: Exception) {
            Log.e(TAG, "停止MediaProjection音频捕获失败", e)
        }
    }
    
    override fun getStrategyName(): String = "MediaProjection音频捕获"
    
    override fun getPriority(): Int = 1 // 高优先级
    
    /**
     * 清理MediaProjection资源
     */
    override fun cleanup() {
        stopCapture()
        compatManager.reset()
        Log.d(TAG, "MediaProjection策略资源已清理")
    }
}

/**
 * 直接音频捕获策略
 * 使用AudioRecord直接捕获麦克风音频
 */
class DirectAudioCaptureStrategy(
    private val context: Context
) : AudioCaptureStrategy {
    
    companion object {
        private const val TAG = "zqqtestDirectAudioStrategy"
        // 音频录制参数 - 使用与用户示例相同的配置
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2
    }
    
    private var isCapturing = false
    private var audioRecord: android.media.AudioRecord? = null
    private var recordingThread: Thread? = null
    private var outputFile: java.io.File? = null
    private var mediaProjection: android.media.projection.MediaProjection? = null
    
    /**
     * 设置MediaProjection用于音频播放捕获
     */
    fun setMediaProjection(projection: android.media.projection.MediaProjection?) {
        this.mediaProjection = projection
        Log.d(TAG, "MediaProjection已设置: ${projection != null}")
    }
    
    /**
     * 检查直接音频捕获是否可用
     */
    override fun isAvailable(): Boolean {
        return try {
            // 检查录音权限
            val hasRecordPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!hasRecordPermission) {
                Log.w(TAG, "缺少录音权限")
                return false
            }
            
            // 检查是否有MediaProjection
            if (mediaProjection == null) {
                Log.w(TAG, "MediaProjection未设置")
                return false
            }
            
            // 检查Android版本（AudioPlaybackCapture需要Android 10+）
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                Log.w(TAG, "AudioPlaybackCapture需要Android 10+")
                return false
            }
            
            // 检查AudioRecord是否可用
            val minBufferSize = android.media.AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            val available = minBufferSize > 0
            Log.d(TAG, "音频播放捕获可用性: $available (缓冲区大小: $minBufferSize)")
            available
        } catch (e: Exception) {
            Log.e(TAG, "检查音频播放捕获可用性失败", e)
            false
        }
    }
    
    /**
     * 开始音频播放捕获（使用MediaProjection和AudioPlaybackCaptureConfiguration）
     */
    override fun startCapture(): Boolean {
        return try {
            if (isCapturing) {
                Log.w(TAG, "音频播放捕获已在进行中")
                return true
            }
            
            if (!isAvailable()) {
                Log.e(TAG, "音频播放捕获不可用")
                return false
            }
            
            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection未设置，无法开始音频播放捕获")
                return false
            }
            
            // 创建输出文件
            val audioDir = java.io.File(context.getExternalFilesDir(null), "audio_capture")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            outputFile = java.io.File(audioDir, "playback_audio_capture_${timestamp}.pcm")
            
            Log.i(TAG, "开始音频播放捕获")
            Log.i(TAG, "音频参数 - 采样率: ${SAMPLE_RATE}Hz, 声道: 单声道, 格式: PCM_16BIT")
            Log.i(TAG, "输出文件路径: ${outputFile?.absolutePath}")
            
            // 计算缓冲区大小
            val minBufferSize = android.media.AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            
            if (minBufferSize == android.media.AudioRecord.ERROR || minBufferSize == android.media.AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "无效的缓冲区大小: $minBufferSize")
                return false
            }
            
            val bufferSize = minBufferSize * BUFFER_SIZE_MULTIPLIER
            Log.i(TAG, "缓冲区大小: $bufferSize bytes (最小: $minBufferSize)")
            
            // 构建AudioPlaybackCaptureConfiguration
            val playbackConfig = android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)     // 音乐、视频等
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)      // 游戏音频
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)   // 未知用途音频
                .build()
            Log.d(TAG, "AudioPlaybackCaptureConfiguration创建成功")
            
            // 创建AudioRecord用于捕获系统播放音频
            audioRecord = android.media.AudioRecord.Builder()
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(playbackConfig)  // 关键：设置播放捕获配置
                .build()
            
            if (audioRecord?.state != android.media.AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败，状态: ${audioRecord?.state}")
                return false
            }
            
            // 开始录制
            audioRecord?.startRecording()
            isCapturing = true
            
            Log.i(TAG, "AudioRecord启动成功，开始捕获播放音频数据")
            
            // 启动录制线程
            recordingThread = Thread {
                writeAudioDataToFile(bufferSize)
            }.apply {
                name = "PlaybackAudioCaptureThread"
                start()
            }
            
            Log.i(TAG, "音频播放捕获启动完成")
            true
        } catch (e: Exception) {
            Log.e(TAG, "启动音频播放捕获失败", e)
            cleanup()
            false
        }
    }
    
    /**
     * 停止音频播放捕获
     */
    override fun stopCapture() {
        try {
            if (!isCapturing) {
                Log.w(TAG, "音频播放捕获未在进行中")
                return
            }
            
            Log.i(TAG, "停止音频播放捕获")
            
            // 停止录制
            isCapturing = false
            
            // 等待录制线程结束
            recordingThread?.join(3000) // 最多等待3秒
            recordingThread = null
            
            // 停止并释放AudioRecord
            audioRecord?.let { record ->
                try {
                    if (record.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                        Log.d(TAG, "AudioRecord已停止")
                    }
                    record.release()
                    Log.d(TAG, "AudioRecord资源已释放")
                } catch (e: Exception) {
                    Log.e(TAG, "释放AudioRecord失败", e)
                }
            }
            audioRecord = null
            
            // 输出文件信息
            outputFile?.let { file ->
                if (file.exists()) {
                    val fileSizeMB = file.length() / (1024.0 * 1024.0)
                    Log.i(TAG, "播放音频捕获完成 - 文件路径: ${file.absolutePath}")
                    Log.i(TAG, "播放音频捕获完成 - 文件大小: ${String.format("%.2f", fileSizeMB)} MB")
                    Log.i(TAG, "播放音频捕获完成 - 文件格式: PCM (${SAMPLE_RATE}Hz, 单声道, 16bit)")
                } else {
                    Log.w(TAG, "捕获文件不存在: ${file.absolutePath}")
                }
            }
            
            Log.i(TAG, "音频播放捕获停止完成")
        } catch (e: Exception) {
            Log.e(TAG, "停止音频播放捕获失败", e)
        }
    }
    
    override fun getStrategyName(): String = "音频播放捕获"
    
    override fun getPriority(): Int = 2 // 中等优先级
    
    /**
     * 写入播放音频数据到文件
     */
    private fun writeAudioDataToFile(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        var totalBytes = 0L
        var fos: java.io.FileOutputStream? = null
        
        try {
            outputFile?.let { file ->
                fos = java.io.FileOutputStream(file)
                Log.d(TAG, "开始写入播放音频数据到文件: ${file.absolutePath}")
                
                val startTime = System.currentTimeMillis()
                var lastLogTime = startTime
                
                while (isCapturing && audioRecord != null) {
                    val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    
                    if (bytesRead > 0) {
                        fos?.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        
                        // 检测是否为静音数据
                        val isAllZero = buffer.take(bytesRead).all { it == 0.toByte() }
                        
                        // 每5秒输出一次统计信息
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastLogTime >= 5000) {
                            val durationSeconds = (currentTime - startTime) / 1000.0
                            val fileSizeMB = totalBytes / (1024.0 * 1024.0)
                            Log.i(TAG, "播放音频捕获进行中 - 时长: ${String.format("%.1f", durationSeconds)}s, 数据量: ${String.format("%.2f", fileSizeMB)}MB, 静音: $isAllZero")
                            lastLogTime = currentTime
                        }
                        
                        // 偶尔输出详细的读取信息
                        if (totalBytes % (bufferSize * 100) == 0L) {
                            Log.d(TAG, "读取 $bytesRead 字节, 静音数据: $isAllZero")
                        }
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord读取错误: $bytesRead")
                        break
                    }
                }
                
                val totalDurationSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                val totalSizeMB = totalBytes / (1024.0 * 1024.0)
                Log.i(TAG, "播放音频数据写入完成 - 总时长: ${String.format("%.1f", totalDurationSeconds)}s, 总大小: ${String.format("%.2f", totalSizeMB)}MB")
            }
        } catch (e: Exception) {
            Log.e(TAG, "写入播放音频数据失败", e)
        } finally {
            try {
                fos?.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭文件流失败", e)
            }
        }
    }
    
    /**
     * 清理音频播放捕获资源
     */
    override fun cleanup() {
        stopCapture()
        mediaProjection = null
        Log.d(TAG, "音频播放捕获策略资源已清理")
    }
}

/**
 * 音频捕获策略管理器
 * 负责选择和管理最佳的音频捕获策略
 */
class AudioCaptureStrategyManager(
    private val context: Context,
    private val config: AudioCaptureConfig? = null,
    private val logger: AudioCaptureLogger? = null,
    private val performanceMonitor: AudioCapturePerformanceMonitor? = null
) {
    
    companion object {
        private const val TAG = "zqqtestAudioStrategyManager"
    }
    
    private val strategies = mutableListOf<AudioCaptureStrategy>()
    private var currentStrategy: AudioCaptureStrategy? = null
    
    init {
        initializeStrategies()
    }
    
    /**
     * 初始化所有可用的策略
     */
    private fun initializeStrategies() {
        val compatManager = CompatMediaProjectionManager(context)
        
        // 添加MediaProjection策略
        strategies.add(MediaProjectionCaptureStrategy(context, compatManager))
        
        // 添加直接音频捕获策略
        strategies.add(DirectAudioCaptureStrategy(context))
        
        // 按优先级排序
        strategies.sortBy { it.getPriority() }
        
        Log.d(TAG, "已初始化 ${strategies.size} 个音频捕获策略")
    }
    
    /**
     * 获取最佳可用策略
     * @return 最佳可用的音频捕获策略
     */
    fun getBestStrategy(): AudioCaptureStrategy? {
        for (strategy in strategies) {
            if (strategy.isAvailable()) {
                Log.i(TAG, "选择策略: ${strategy.getStrategyName()}")
                return strategy
            }
        }
        
        Log.w(TAG, "没有可用的音频捕获策略")
        return null
    }
    
    /**
     * 开始音频捕获（自动选择最佳策略）
     * @param onSuccess 成功回调
     * @param onError 错误回调
     */
    fun startCapture(
        onSuccess: (AudioCaptureStrategy) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (currentStrategy?.isAvailable() == true) {
                Log.d(TAG, "使用当前策略继续捕获")
                AudioCaptureLogger.info(AudioCaptureLogger.LogCategory.STRATEGY_SWITCH, "继续使用策略: ${currentStrategy!!.getStrategyName()}", mapOf("reason" to "StrategyContinue"))
                if (currentStrategy!!.startCapture()) {
                    onSuccess(currentStrategy!!)
                } else {
                    onError("当前策略启动失败")
                }
                return
            }
            
            val bestStrategy = getBestStrategy()
            if (bestStrategy != null) {
                currentStrategy = bestStrategy
                AudioCaptureLogger.info(AudioCaptureLogger.LogCategory.STRATEGY_SWITCH, "选择策略: ${bestStrategy.getStrategyName()}", mapOf("reason" to "StrategySelection"))
                performanceMonitor?.recordStrategySwitch(currentStrategy?.getStrategyName() ?: "None", bestStrategy.getStrategyName(), "StrategySelection")
                
                if (bestStrategy.startCapture()) {
                    onSuccess(bestStrategy)
                } else {
                    onError("策略 ${bestStrategy.getStrategyName()} 启动失败")
                }
            } else {
                val errorMsg = "无法开始音频捕获：没有可用策略"
                Log.e(TAG, errorMsg)
                AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, errorMsg, mapOf("errorType" to "StrategyInitFailure"))
                onError(errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = "启动音频捕获时发生异常: ${e.message}"
            Log.e(TAG, errorMsg, e)
            AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, errorMsg, mapOf("errorType" to "StrategyStartFailure"))
            onError(errorMsg)
        }
    }
    
    /**
     * 开始音频捕获（自动选择最佳策略）- 兼容旧版本
     * @return true如果成功开始，false否则
     */
    fun startCapture(): Boolean {
        var success = false
        startCapture(
            onSuccess = { success = true },
            onError = { success = false }
        )
        return success
    }
    
    /**
     * 停止音频捕获
     */
    fun stopCapture() {
        try {
            currentStrategy?.let { strategy ->
                val strategyName = strategy.getStrategyName()
                AudioCaptureLogger.info(AudioCaptureLogger.LogCategory.STRATEGY_SWITCH, "停止策略: $strategyName", mapOf("reason" to "StrategyStop"))
                strategy.stopCapture()
                performanceMonitor?.recordCaptureStop(strategyName)
            }
            Log.d(TAG, "音频捕获已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止音频捕获时发生错误", e)
            AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, "停止音频捕获失败: ${e.message}", mapOf("errorType" to "StopFailure"))
        }
    }
    
    /**
     * 获取当前使用的策略
     * @return 当前策略，如果没有则返回null
     */
    fun getCurrentStrategy(): AudioCaptureStrategy? = currentStrategy
    
    /**
     * 获取所有可用策略
     */
    fun getStrategies(): List<AudioCaptureStrategy> = strategies.toList()
    
    /**
     * 强制切换到指定策略
     * @param strategyClass 要切换到的策略类
     * @return true如果切换成功，false否则
     */
    fun switchToStrategy(strategyClass: Class<out AudioCaptureStrategy>): Boolean {
        val targetStrategy = strategies.find { it.javaClass == strategyClass }
        
        if (targetStrategy != null && targetStrategy.isAvailable()) {
            currentStrategy?.stopCapture()
            currentStrategy = targetStrategy
            Log.i(TAG, "已切换到策略: ${targetStrategy.getStrategyName()}")
            return true
        }
        
        Log.w(TAG, "无法切换到策略: ${strategyClass.simpleName}")
        return false
    }
    
    /**
     * 清理所有策略资源
     */
    fun cleanup() {
        try {
            stopCapture()
            strategies.forEach { strategy ->
                try {
                    strategy.cleanup()
                } catch (e: Exception) {
                    Log.e(TAG, "清理策略 ${strategy.getStrategyName()} 时发生错误", e)
                    AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, "清理策略 ${strategy.getStrategyName()} 时发生错误: ${e.message}", mapOf("errorType" to "CleanupFailure"))
                }
            }
            currentStrategy = null
            AudioCaptureLogger.info(AudioCaptureLogger.LogCategory.LIFECYCLE, "策略管理器清理完成", mapOf("reason" to "CleanupComplete"))
            Log.d(TAG, "策略管理器资源已清理")
        } catch (e: Exception) {
            Log.e(TAG, "策略管理器清理时发生错误", e)
            AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, "策略管理器清理时发生错误: ${e.message}", mapOf("errorType" to "ManagerCleanupFailure"))
        }
    }
    
    /**
     * 获取所有策略的状态报告
     * @return 策略状态报告字符串
     */
    fun getStatusReport(): String {
        return buildString {
            appendLine("=== 音频捕获策略状态 ===")
            strategies.forEach { strategy ->
                val status = if (strategy.isAvailable()) "可用" else "不可用"
                val current = if (strategy == currentStrategy) " [当前]" else ""
                appendLine("${strategy.getStrategyName()}: $status$current")
            }
        }
    }
}