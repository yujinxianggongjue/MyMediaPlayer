package com.example.mymediaplayer

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

/**
 * 音频捕获配置管理类
 * 负责管理音频捕获的各种配置参数，支持动态调整和环境适配
 */
class AudioCaptureConfig(private val context: Context) {
    
    companion object {
        private const val TAG = "zqqtestAudioCaptureConfig"
        
        // 配置键名
        const val PREFER_MEDIA_PROJECTION = "prefer_media_projection"
        const val ENABLE_FALLBACK = "enable_fallback"
        const val MAX_RETRY_COUNT = "max_retry_count"
        const val SYSTEMUI_STABILITY_CHECK = "systemui_stability_check"
        const val AUTO_SWITCH_STRATEGY = "auto_switch_strategy"
        const val ENABLE_PERFORMANCE_MONITORING = "enable_performance_monitoring"
        const val AUDIO_BUFFER_SIZE_MULTIPLIER = "audio_buffer_size_multiplier"
        const val SAMPLE_RATE = "sample_rate"
        const val CHANNEL_CONFIG = "channel_config"
        const val AUDIO_FORMAT = "audio_format"
        
        // 默认值
        private const val DEFAULT_MAX_RETRY_COUNT = 3
        private const val DEFAULT_BUFFER_SIZE_MULTIPLIER = 2
        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val DEFAULT_CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_STEREO
        private const val DEFAULT_AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "audio_capture_config", 
        Context.MODE_PRIVATE
    )
    
    init {
        initializeDefaultConfig()
    }
    
    /**
     * 初始化默认配置
     */
    private fun initializeDefaultConfig() {
        if (isFirstRun()) {
            Log.i(TAG, "首次运行，初始化默认配置")
            
            // 根据设备特性设置默认配置
            val defaultConfig = getDeviceOptimizedConfig()
            applyConfig(defaultConfig)
            
            markFirstRunComplete()
        }
    }
    
    /**
     * 检查是否首次运行
     */
    private fun isFirstRun(): Boolean {
        return !prefs.contains("config_initialized")
    }
    
    /**
     * 标记首次运行完成
     */
    private fun markFirstRunComplete() {
        prefs.edit().putBoolean("config_initialized", true).apply()
    }
    
    /**
     * 获取设备优化配置
     * @return 针对当前设备优化的配置映射
     */
    private fun getDeviceOptimizedConfig(): Map<String, Any> {
        val config = mutableMapOf<String, Any>()
        
        // 基础配置
        config[ENABLE_FALLBACK] = true
        config[AUTO_SWITCH_STRATEGY] = true
        config[ENABLE_PERFORMANCE_MONITORING] = true
        config[MAX_RETRY_COUNT] = DEFAULT_MAX_RETRY_COUNT
        config[SAMPLE_RATE] = DEFAULT_SAMPLE_RATE
        config[CHANNEL_CONFIG] = DEFAULT_CHANNEL_CONFIG
        config[AUDIO_FORMAT] = DEFAULT_AUDIO_FORMAT
        
        // 根据设备特性调整配置
        when {
            // MTK车载系统特殊配置
            isMTKCarSystem() -> {
                Log.i(TAG, "检测到MTK车载系统，应用特殊配置")
                config[PREFER_MEDIA_PROJECTION] = false
                config[SYSTEMUI_STABILITY_CHECK] = false
                config[AUDIO_BUFFER_SIZE_MULTIPLIER] = 4 // 更大的缓冲区
            }
            
            // 通用车载系统配置
            isCarSystem() -> {
                Log.i(TAG, "检测到车载系统，应用车载优化配置")
                config[PREFER_MEDIA_PROJECTION] = true
                config[SYSTEMUI_STABILITY_CHECK] = true
                config[AUDIO_BUFFER_SIZE_MULTIPLIER] = 3
            }
            
            // 低端设备配置
            isLowEndDevice() -> {
                Log.i(TAG, "检测到低端设备，应用性能优化配置")
                config[PREFER_MEDIA_PROJECTION] = false
                config[ENABLE_PERFORMANCE_MONITORING] = false
                config[AUDIO_BUFFER_SIZE_MULTIPLIER] = 1
                config[SAMPLE_RATE] = 22050 // 降低采样率
            }
            
            // 默认配置
            else -> {
                Log.i(TAG, "应用默认配置")
                config[PREFER_MEDIA_PROJECTION] = true
                config[SYSTEMUI_STABILITY_CHECK] = true
                config[AUDIO_BUFFER_SIZE_MULTIPLIER] = DEFAULT_BUFFER_SIZE_MULTIPLIER
            }
        }
        
        return config
    }
    
    /**
     * 检查是否为MTK车载系统
     */
    private fun isMTKCarSystem(): Boolean {
        return Build.MANUFACTURER.equals("MTK", true) && isCarSystem()
    }
    
    /**
     * 检查是否为车载系统
     */
    private fun isCarSystem(): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE) ||
            context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_TYPE_MASK == 
            android.content.res.Configuration.UI_MODE_TYPE_CAR
        } catch (e: Exception) {
            Log.w(TAG, "检查车载系统状态失败", e)
            false
        }
    }
    
    /**
     * 检查是否为低端设备
     */
    private fun isLowEndDevice(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            // 内存小于2GB认为是低端设备
            val totalMemoryGB = memoryInfo.totalMem / (1024 * 1024 * 1024)
            totalMemoryGB < 2
        } catch (e: Exception) {
            Log.w(TAG, "检查设备性能失败", e)
            false
        }
    }
    
    /**
     * 应用配置映射
     */
    private fun applyConfig(config: Map<String, Any>) {
        val editor = prefs.edit()
        
        config.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                else -> Log.w(TAG, "不支持的配置值类型: ${value.javaClass.simpleName}")
            }
        }
        
        editor.apply()
        Log.d(TAG, "已应用 ${config.size} 个配置项")
    }
    
    /**
     * 检查是否应该使用MediaProjection
     * @return true如果应该使用MediaProjection，false否则
     */
    fun shouldUseMediaProjection(): Boolean {
        val prefer = prefs.getBoolean(PREFER_MEDIA_PROJECTION, true)
        val stabilityCheck = prefs.getBoolean(SYSTEMUI_STABILITY_CHECK, true)
        
        return prefer && (!stabilityCheck || !hasKnownSystemUIIssues())
    }
    
    /**
     * 检查是否启用SystemUI稳定性检查
     */
    fun isSystemUIStabilityCheckEnabled(): Boolean {
        return prefs.getBoolean(SYSTEMUI_STABILITY_CHECK, true)
    }
    
    /**
     * 检查是否有已知的SystemUI问题
     */
    private fun hasKnownSystemUIIssues(): Boolean {
        // 检查已知的SystemUI问题
        return Build.MANUFACTURER.equals("MTK", true) && 
               Build.VERSION.SDK_INT in 29..31
    }
    
    /**
     * 更新布尔配置
     * @param key 配置键
     * @param value 配置值
     */
    fun updateConfig(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        Log.d(TAG, "更新配置: $key = $value")
    }
    
    /**
     * 更新整数配置
     * @param key 配置键
     * @param value 配置值
     */
    fun updateConfig(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        Log.d(TAG, "更新配置: $key = $value")
    }
    
    /**
     * 获取最大重试次数
     */
    fun getMaxRetryCount(): Int {
        return prefs.getInt(MAX_RETRY_COUNT, DEFAULT_MAX_RETRY_COUNT)
    }
    
    /**
     * 检查是否启用降级方案
     */
    fun isFallbackEnabled(): Boolean {
        return prefs.getBoolean(ENABLE_FALLBACK, true)
    }
    
    /**
     * 检查是否启用自动策略切换
     */
    fun isAutoSwitchEnabled(): Boolean {
        return prefs.getBoolean(AUTO_SWITCH_STRATEGY, true)
    }
    
    /**
     * 检查是否启用性能监控
     */
    fun isPerformanceMonitoringEnabled(): Boolean {
        return prefs.getBoolean(ENABLE_PERFORMANCE_MONITORING, true)
    }
    
    /**
     * 获取音频缓冲区大小倍数
     */
    fun getAudioBufferSizeMultiplier(): Int {
        return prefs.getInt(AUDIO_BUFFER_SIZE_MULTIPLIER, DEFAULT_BUFFER_SIZE_MULTIPLIER)
    }
    
    /**
     * 获取采样率
     */
    fun getSampleRate(): Int {
        return prefs.getInt(SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
    }
    
    /**
     * 获取声道配置
     */
    fun getChannelConfig(): Int {
        return prefs.getInt(CHANNEL_CONFIG, DEFAULT_CHANNEL_CONFIG)
    }
    
    /**
     * 获取音频格式
     */
    fun getAudioFormat(): Int {
        return prefs.getInt(AUDIO_FORMAT, DEFAULT_AUDIO_FORMAT)
    }
    
    /**
     * 获取音频设置对象
     * @return 音频设置
     */
    fun getAudioSettings(): AudioSettings {
        return AudioSettings(
            sampleRate = getSampleRate(),
            channelConfig = getChannelConfig(),
            audioFormat = getAudioFormat(),
            bufferSizeMultiplier = getAudioBufferSizeMultiplier()
        )
    }
    
    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        Log.i(TAG, "重置为默认配置")
        
        prefs.edit().clear().apply()
        initializeDefaultConfig()
    }
    
    /**
     * 导出配置
     * @return 配置的JSON字符串
     */
    fun exportConfig(): String {
        val allPrefs = prefs.all
        val configMap = mutableMapOf<String, Any?>()
        
        allPrefs.forEach { (key, value) ->
            configMap[key] = value
        }
        
        // 添加设备信息
        configMap["device_info"] = mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "android_version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT,
            "is_car_system" to isCarSystem(),
            "is_mtk_car" to isMTKCarSystem(),
            "is_low_end" to isLowEndDevice()
        )
        
        return configMap.toString()
    }
    
    /**
     * 获取配置摘要
     * @return 配置摘要字符串
     */
    fun getConfigSummary(): String {
        return buildString {
            appendLine("=== 音频捕获配置摘要 ===")
            appendLine("MediaProjection优先: ${shouldUseMediaProjection()}")
            appendLine("启用降级方案: ${isFallbackEnabled()}")
            appendLine("SystemUI稳定性检查: ${isSystemUIStabilityCheckEnabled()}")
            appendLine("自动策略切换: ${isAutoSwitchEnabled()}")
            appendLine("性能监控: ${isPerformanceMonitoringEnabled()}")
            appendLine("最大重试次数: ${getMaxRetryCount()}")
            appendLine("")
            appendLine("音频参数:")
            appendLine("  采样率: ${getSampleRate()} Hz")
            appendLine("  声道配置: ${getChannelConfig()}")
            appendLine("  音频格式: ${getAudioFormat()}")
            appendLine("  缓冲区倍数: ${getAudioBufferSizeMultiplier()}")
            appendLine("")
            appendLine("设备信息:")
            appendLine("  制造商: ${Build.MANUFACTURER}")
            appendLine("  型号: ${Build.MODEL}")
            appendLine("  车载系统: ${if (isCarSystem()) "是" else "否"}")
            appendLine("  MTK车载: ${if (isMTKCarSystem()) "是" else "否"}")
            appendLine("  低端设备: ${if (isLowEndDevice()) "是" else "否"}")
        }
    }
    
    /**
     * 动态调整配置（基于运行时状态）
     * @param errorCount 错误次数
     * @param performanceIssues 是否有性能问题
     */
    fun dynamicAdjustConfig(errorCount: Int, performanceIssues: Boolean) {
        Log.i(TAG, "动态调整配置，错误次数: $errorCount, 性能问题: $performanceIssues")
        
        when {
            errorCount > 5 -> {
                // 频繁错误，切换到保守配置
                updateConfig(PREFER_MEDIA_PROJECTION, false)
                updateConfig(AUDIO_BUFFER_SIZE_MULTIPLIER, 1)
                Log.i(TAG, "频繁错误，切换到保守配置")
            }
            
            performanceIssues -> {
                // 性能问题，降低音频质量
                updateConfig(SAMPLE_RATE, 22050)
                updateConfig(CHANNEL_CONFIG, android.media.AudioFormat.CHANNEL_IN_MONO)
                Log.i(TAG, "性能问题，降低音频质量")
            }
            
            errorCount == 0 && !performanceIssues -> {
                // 运行良好，可以尝试更高质量配置
                if (getSampleRate() < DEFAULT_SAMPLE_RATE) {
                    updateConfig(SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
                    updateConfig(CHANNEL_CONFIG, DEFAULT_CHANNEL_CONFIG)
                    Log.i(TAG, "运行良好，提升音频质量")
                }
            }
        }
    }
}

/**
 * 音频设置数据类
 */
data class AudioSettings(
    val sampleRate: Int,
    val channelConfig: Int,
    val audioFormat: Int,
    val bufferSizeMultiplier: Int
) {
    /**
     * 计算缓冲区大小
     * @return 计算出的缓冲区大小
     */
    fun calculateBufferSize(): Int {
        val minBufferSize = android.media.AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )
        
        return if (minBufferSize != android.media.AudioRecord.ERROR_BAD_VALUE) {
            minBufferSize * bufferSizeMultiplier
        } else {
            4096 * bufferSizeMultiplier // 默认缓冲区大小
        }
    }
    
    /**
     * 验证音频设置是否有效
     * @return true如果设置有效，false否则
     */
    fun isValid(): Boolean {
        return try {
            val minBufferSize = android.media.AudioRecord.getMinBufferSize(
                sampleRate,
                channelConfig,
                audioFormat
            )
            minBufferSize != android.media.AudioRecord.ERROR_BAD_VALUE
        } catch (e: Exception) {
            false
        }
    }
    
    override fun toString(): String {
        return "AudioSettings(sampleRate=$sampleRate, channelConfig=$channelConfig, " +
               "audioFormat=$audioFormat, bufferSizeMultiplier=$bufferSizeMultiplier, " +
               "bufferSize=${calculateBufferSize()})"
    }
}