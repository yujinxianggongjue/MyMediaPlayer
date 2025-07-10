package com.example.mymediaplayer

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * 音频捕获错误处理结果
 */
data class ErrorHandlingResult(
    val success: Boolean,
    val errorMessage: String,
    val hasFallback: Boolean,
    val suggestedAction: String,
    val retryRecommended: Boolean = false
)

/**
 * 音频捕获错误类型枚举
 */
enum class AudioCaptureErrorType {
    SYSTEMUI_CONFLICT,      // SystemUI冲突错误
    PERMISSION_DENIED,      // 权限被拒绝
    HARDWARE_UNAVAILABLE,   // 硬件不可用
    MEDIA_PROJECTION_FAILED, // MediaProjection失败
    AUDIO_RECORD_FAILED,    // AudioRecord失败
    UNKNOWN_ERROR           // 未知错误
}

/**
 * 音频捕获错误处理器
 * 专门处理各种音频捕获过程中的错误，并提供恢复方案
 */
class AudioCaptureErrorHandler(private val context: Context) {
    
    companion object {
        private const val TAG = "zqqtestAudioCaptureErrorHandler"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 1000L
    }
    
    private val retryCounters = mutableMapOf<AudioCaptureErrorType, AtomicInteger>()
    private val strategyManager = AudioCaptureStrategyManager(context)
    
    /**
     * 处理SystemUI冲突错误
     * @param exception 异常对象
     * @return 错误处理结果
     */
    fun handleSystemUIError(exception: Exception): ErrorHandlingResult {
        Log.e(TAG, "处理SystemUI冲突错误", exception)
        
        val errorType = AudioCaptureErrorType.SYSTEMUI_CONFLICT
        val retryCount = getRetryCount(errorType)
        
        return when {
            exception.message?.contains("NoSuchFieldError") == true -> {
                ErrorHandlingResult(
                    success = false,
                    errorMessage = "检测到androidx.lifecycle版本冲突，SystemUI不稳定",
                    hasFallback = true,
                    suggestedAction = "已自动切换到直接音频捕获模式",
                    retryRecommended = false
                )
            }
            
            exception.message?.contains("ActivityNotFoundException") == true -> {
                ErrorHandlingResult(
                    success = false,
                    errorMessage = "MediaProjection权限Activity不可用",
                    hasFallback = true,
                    suggestedAction = "使用直接音频录制作为替代方案",
                    retryRecommended = false
                )
            }
            
            retryCount < MAX_RETRY_COUNT -> {
                incrementRetryCount(errorType)
                ErrorHandlingResult(
                    success = false,
                    errorMessage = "SystemUI临时不可用",
                    hasFallback = true,
                    suggestedAction = "等待${RETRY_DELAY_MS}ms后重试 (${retryCount + 1}/$MAX_RETRY_COUNT)",
                    retryRecommended = true
                )
            }
            
            else -> {
                ErrorHandlingResult(
                    success = false,
                    errorMessage = "SystemUI持续不可用，已达到最大重试次数",
                    hasFallback = true,
                    suggestedAction = "永久切换到直接音频捕获模式",
                    retryRecommended = false
                )
            }
        }
    }
    
    /**
     * 处理权限错误
     * @param permissionType 权限类型
     * @return 错误处理结果
     */
    fun handlePermissionError(permissionType: String): ErrorHandlingResult {
        Log.w(TAG, "处理权限错误: $permissionType")
        
        return when (permissionType) {
            android.Manifest.permission.RECORD_AUDIO -> {
                ErrorHandlingResult(
                    success = false,
                    errorMessage = "缺少音频录制权限",
                    hasFallback = false,
                    suggestedAction = "请在设置中授予音频录制权限",
                    retryRecommended = false
                )
            }
            
            "MEDIA_PROJECTION" -> {
                ErrorHandlingResult(
                    success = false,
                    errorMessage = "MediaProjection权限被拒绝",
                    hasFallback = true,
                    suggestedAction = "切换到麦克风录制模式",
                    retryRecommended = true
                )
            }
            
            else -> {
                ErrorHandlingResult(
                    success = false,
                    errorMessage = "未知权限错误: $permissionType",
                    hasFallback = false,
                    suggestedAction = "检查应用权限设置",
                    retryRecommended = false
                )
            }
        }
    }
    
    /**
     * 处理硬件不可用错误
     * @param hardwareType 硬件类型
     * @return 错误处理结果
     */
    fun handleHardwareError(hardwareType: String): ErrorHandlingResult {
        Log.e(TAG, "处理硬件错误: $hardwareType")
        
        val errorType = AudioCaptureErrorType.HARDWARE_UNAVAILABLE
        val retryCount = getRetryCount(errorType)
        
        return when {
            hardwareType.contains("AudioRecord", ignoreCase = true) -> {
                if (retryCount < MAX_RETRY_COUNT) {
                    incrementRetryCount(errorType)
                    ErrorHandlingResult(
                        success = false,
                        errorMessage = "音频录制硬件暂时不可用",
                        hasFallback = true,
                        suggestedAction = "尝试不同的音频配置参数",
                        retryRecommended = true
                    )
                } else {
                    ErrorHandlingResult(
                        success = false,
                        errorMessage = "音频录制硬件持续不可用",
                        hasFallback = false,
                        suggestedAction = "设备可能不支持音频录制功能",
                        retryRecommended = false
                    )
                }
            }
            
            else -> {
                ErrorHandlingResult(
                    success = false,
                    errorMessage = "硬件设备不可用: $hardwareType",
                    hasFallback = false,
                    suggestedAction = "检查设备硬件状态",
                    retryRecommended = false
                )
            }
        }
    }
    
    /**
     * 自动恢复音频捕获
     * @param originalError 原始错误
     * @return 恢复是否成功
     */
    fun attemptAutoRecovery(originalError: Exception): Boolean {
        Log.i(TAG, "尝试自动恢复音频捕获")
        
        return try {
            // 停止当前捕获
            strategyManager.stopCapture()
            
            // 等待一段时间
            Thread.sleep(RETRY_DELAY_MS)
            
            // 尝试使用最佳可用策略
            val success = strategyManager.startCapture()
            
            if (success) {
                Log.i(TAG, "自动恢复成功")
                resetRetryCount(AudioCaptureErrorType.SYSTEMUI_CONFLICT)
            } else {
                Log.w(TAG, "自动恢复失败")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "自动恢复过程中发生错误", e)
            false
        }
    }
    
    /**
     * 强制切换到降级方案
     * @return 切换是否成功
     */
    fun forceFallbackStrategy(): Boolean {
        Log.i(TAG, "强制切换到降级方案")
        
        return try {
            // 停止当前策略
            strategyManager.stopCapture()
            
            // 切换到直接音频捕获策略
            val success = strategyManager.switchToStrategy(DirectAudioCaptureStrategy::class.java)
            
            if (success) {
                Log.i(TAG, "成功切换到直接音频捕获策略")
                // 尝试开始捕获
                return strategyManager.startCapture()
            } else {
                Log.e(TAG, "无法切换到降级策略")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换降级策略时发生错误", e)
            false
        }
    }
    
    /**
     * 获取错误类型的重试次数
     */
    private fun getRetryCount(errorType: AudioCaptureErrorType): Int {
        return retryCounters.getOrPut(errorType) { AtomicInteger(0) }.get()
    }
    
    /**
     * 增加错误类型的重试次数
     */
    private fun incrementRetryCount(errorType: AudioCaptureErrorType) {
        retryCounters.getOrPut(errorType) { AtomicInteger(0) }.incrementAndGet()
    }
    
    /**
     * 重置错误类型的重试次数
     */
    private fun resetRetryCount(errorType: AudioCaptureErrorType) {
        retryCounters[errorType]?.set(0)
    }
    
    /**
     * 分析异常并确定错误类型
     * @param exception 异常对象
     * @return 错误类型
     */
    fun analyzeError(exception: Exception): AudioCaptureErrorType {
        return when {
            exception.message?.contains("NoSuchFieldError") == true ||
            exception.message?.contains("systemui", ignoreCase = true) == true -> {
                AudioCaptureErrorType.SYSTEMUI_CONFLICT
            }
            
            exception.message?.contains("permission", ignoreCase = true) == true -> {
                AudioCaptureErrorType.PERMISSION_DENIED
            }
            
            exception.message?.contains("MediaProjection") == true -> {
                AudioCaptureErrorType.MEDIA_PROJECTION_FAILED
            }
            
            exception.message?.contains("AudioRecord") == true -> {
                AudioCaptureErrorType.AUDIO_RECORD_FAILED
            }
            
            exception.message?.contains("hardware", ignoreCase = true) == true -> {
                AudioCaptureErrorType.HARDWARE_UNAVAILABLE
            }
            
            else -> AudioCaptureErrorType.UNKNOWN_ERROR
        }
    }
    
    /**
     * 处理通用错误
     * @param exception 异常对象
     * @return 错误处理结果
     */
    fun handleGenericError(exception: Exception): ErrorHandlingResult {
        val errorType = analyzeError(exception)
        
        Log.e(TAG, "处理通用错误，类型: $errorType", exception)
        
        return when (errorType) {
            AudioCaptureErrorType.SYSTEMUI_CONFLICT -> handleSystemUIError(exception)
            AudioCaptureErrorType.PERMISSION_DENIED -> handlePermissionError("UNKNOWN")
            AudioCaptureErrorType.MEDIA_PROJECTION_FAILED -> handleMediaProjectionError(exception)
            AudioCaptureErrorType.AUDIO_RECORD_FAILED -> handleAudioRecordError(exception)
            AudioCaptureErrorType.HARDWARE_UNAVAILABLE -> handleHardwareError("UNKNOWN")
            AudioCaptureErrorType.UNKNOWN_ERROR -> handleUnknownError(exception)
        }
    }
    
    /**
     * 处理MediaProjection失败错误
     */
    private fun handleMediaProjectionError(exception: Exception): ErrorHandlingResult {
        return ErrorHandlingResult(
            success = false,
            errorMessage = "MediaProjection功能失败: ${exception.message}",
            hasFallback = true,
            suggestedAction = "切换到直接音频录制模式",
            retryRecommended = false
        )
    }
    
    /**
     * 处理AudioRecord失败错误
     */
    private fun handleAudioRecordError(exception: Exception): ErrorHandlingResult {
        return ErrorHandlingResult(
            success = false,
            errorMessage = "音频录制失败: ${exception.message}",
            hasFallback = false,
            suggestedAction = "检查音频权限和硬件状态",
            retryRecommended = true
        )
    }
    
    /**
     * 处理未知错误
     */
    private fun handleUnknownError(exception: Exception): ErrorHandlingResult {
        return ErrorHandlingResult(
            success = false,
            errorMessage = "未知错误: ${exception.message}",
            hasFallback = true,
            suggestedAction = "尝试重启应用或使用其他音频捕获方式",
            retryRecommended = true
        )
    }
    
    /**
     * 获取错误统计报告
     * @return 错误统计字符串
     */
    fun getErrorStatistics(): String {
        return buildString {
            appendLine("=== 错误统计报告 ===")
            retryCounters.forEach { (errorType, counter) ->
                appendLine("$errorType: ${counter.get()} 次")
            }
            
            appendLine("\n当前策略状态:")
            appendLine(strategyManager.getStatusReport())
        }
    }
    
    /**
     * 清理错误处理器资源
     */
    fun cleanup() {
        retryCounters.clear()
        strategyManager.cleanup()
        Log.d(TAG, "错误处理器资源已清理")
    }
}