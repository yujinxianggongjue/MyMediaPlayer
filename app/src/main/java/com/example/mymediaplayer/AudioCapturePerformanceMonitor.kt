package com.example.mymediaplayer

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * 音频捕获性能监控器
 * 实时监控和统计音频捕获的各项性能指标
 */
class AudioCapturePerformanceMonitor {
    
    companion object {
        private const val TAG = "zqqtestAudioCapturePerformanceMonitor"
        
        // 性能阈值
        private const val SLOW_CAPTURE_THRESHOLD_MS = 5000L // 5秒
        private const val LOW_DATA_RATE_THRESHOLD_KBPS = 64L // 64 kbps
        private const val HIGH_ERROR_RATE_THRESHOLD = 0.1 // 10%
        private const val MEMORY_WARNING_THRESHOLD_MB = 50L // 50MB
        
        // 统计窗口
        private const val STATS_WINDOW_SIZE = 100
        private const val PERFORMANCE_HISTORY_SIZE = 50
    }
    
    // 基础统计
    private val captureCount = AtomicInteger(0)
    private val successCount = AtomicInteger(0)
    private val errorCount = AtomicInteger(0)
    
    // 时间统计
    private val totalCaptureTime = AtomicLong(0)
    private val minCaptureTime = AtomicLong(Long.MAX_VALUE)
    private val maxCaptureTime = AtomicLong(0)
    
    // 数据统计
    private val totalDataSize = AtomicLong(0)
    private val minDataRate = AtomicLong(Long.MAX_VALUE)
    private val maxDataRate = AtomicLong(0)
    
    // 错误统计
    private val errorsByType = ConcurrentHashMap<String, AtomicInteger>()
    private val errorsByStrategy = ConcurrentHashMap<String, AtomicInteger>()
    
    // 策略统计
    private val strategyUsage = ConcurrentHashMap<String, AtomicInteger>()
    private val strategySwitchCount = AtomicInteger(0)
    
    // 性能历史记录
    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    private val recentCaptureTimes = mutableListOf<Long>()
    private val recentDataRates = mutableListOf<Long>()
    
    // 内存监控
    private var peakMemoryUsage = 0L
    private var currentMemoryUsage = 0L
    
    // 监控开始时间
    private val monitorStartTime = System.currentTimeMillis()
    
    /**
     * 性能快照数据类
     */
    data class PerformanceSnapshot(
        val timestamp: Long,
        val captureTime: Long,
        val dataSize: Long,
        val dataRate: Long,
        val strategy: String,
        val success: Boolean,
        val errorType: String? = null,
        val memoryUsage: Long = 0
    )
    
    /**
     * 性能统计数据类
     */
    data class PerformanceStats(
        val monitorDuration: Long,
        val totalCaptures: Int,
        val successfulCaptures: Int,
        val failedCaptures: Int,
        val successRate: Double,
        val averageCaptureTime: Double,
        val minCaptureTime: Long,
        val maxCaptureTime: Long,
        val averageDataRate: Double,
        val minDataRate: Long,
        val maxDataRate: Long,
        val totalDataTransferred: Long,
        val strategySwitches: Int,
        val mostUsedStrategy: String?,
        val mostCommonError: String?,
        val peakMemoryUsage: Long,
        val currentMemoryUsage: Long,
        val performanceIssues: List<String>
    )
    
    /**
     * 记录捕获开始
     * @param strategy 使用的策略
     * @return 捕获会话ID
     */
    fun startCapture(strategy: String): String {
        val sessionId = "capture_${System.currentTimeMillis()}_${captureCount.incrementAndGet()}"
        
        // 记录策略使用
        strategyUsage.computeIfAbsent(strategy) { AtomicInteger(0) }.incrementAndGet()
        
        AudioCaptureLogger.logPerformance(
            "capture_start",
            0,
            mapOf(
                "session_id" to sessionId,
                "strategy" to strategy,
                "total_captures" to captureCount.get()
            )
        )
        
        return sessionId
    }
    
    /**
     * 记录捕获成功
     * @param sessionId 捕获会话ID
     * @param strategy 使用的策略
     * @param duration 捕获耗时（毫秒）
     * @param dataSize 数据大小（字节）
     */
    fun recordCaptureSuccess(
        sessionId: String,
        strategy: String,
        duration: Long,
        dataSize: Long
    ) {
        successCount.incrementAndGet()
        
        // 更新时间统计
        totalCaptureTime.addAndGet(duration)
        updateMinMax(minCaptureTime, maxCaptureTime, duration)
        
        // 更新数据统计
        totalDataSize.addAndGet(dataSize)
        val dataRate = if (duration > 0) (dataSize * 8 / duration) else 0 // bps
        updateMinMax(minDataRate, maxDataRate, dataRate)
        
        // 更新历史记录
        updatePerformanceHistory(duration, dataSize, dataRate, strategy, true)
        
        // 检查性能问题
        val issues = checkPerformanceIssues(duration, dataRate)
        
        AudioCaptureLogger.logPerformance(
            "capture_success",
            duration,
            mapOf(
                "session_id" to sessionId,
                "strategy" to strategy,
                "data_size" to dataSize,
                "data_rate_kbps" to (dataRate / 1000),
                "success_rate" to getSuccessRate(),
                "performance_issues" to issues
            )
        )
    }
    
    /**
     * 记录捕获失败
     * @param sessionId 捕获会话ID
     * @param strategy 使用的策略
     * @param errorType 错误类型
     * @param duration 失败前的耗时（毫秒）
     */
    fun recordCaptureFailure(
        sessionId: String,
        strategy: String,
        errorType: String,
        duration: Long = 0
    ) {
        errorCount.incrementAndGet()
        
        // 记录错误统计
        errorsByType.computeIfAbsent(errorType) { AtomicInteger(0) }.incrementAndGet()
        errorsByStrategy.computeIfAbsent(strategy) { AtomicInteger(0) }.incrementAndGet()
        
        // 更新历史记录
        updatePerformanceHistory(duration, 0, 0, strategy, false, errorType)
        
        AudioCaptureLogger.logPerformance(
            "capture_failure",
            duration,
            mapOf(
                "session_id" to sessionId,
                "strategy" to strategy,
                "error_type" to errorType,
                "error_rate" to getErrorRate(),
                "total_errors" to errorCount.get()
            )
        )
    }
    
    /**
     * 记录策略切换
     * @param fromStrategy 原策略
     * @param toStrategy 新策略
     * @param reason 切换原因
     */
    fun recordStrategySwitch(fromStrategy: String, toStrategy: String, reason: String) {
        strategySwitchCount.incrementAndGet()
        
        AudioCaptureLogger.logPerformance(
            "strategy_switch",
            0,
            mapOf(
                "from_strategy" to fromStrategy,
                "to_strategy" to toStrategy,
                "reason" to reason,
                "switch_count" to strategySwitchCount.get()
            )
        )
    }
    
    /**
     * 记录捕获停止
     * @param strategy 使用的策略
     * @param duration 运行时长（毫秒）
     */
    fun recordCaptureStop(strategy: String = "unknown", duration: Long = 0) {
        AudioCaptureLogger.logPerformance(
            "capture_stop",
            duration,
            mapOf(
                "strategy" to strategy,
                "total_captures" to captureCount.get(),
                "success_rate" to getSuccessRate(),
                "error_rate" to getErrorRate()
            )
        )
    }
    
    /**
     * 更新内存使用情况
     * @param memoryUsage 当前内存使用量（字节）
     */
    fun updateMemoryUsage(memoryUsage: Long) {
        currentMemoryUsage = memoryUsage
        peakMemoryUsage = max(peakMemoryUsage, memoryUsage)
        
        val memoryMB = memoryUsage / (1024 * 1024)
        if (memoryMB > MEMORY_WARNING_THRESHOLD_MB) {
            AudioCaptureLogger.warn(
                AudioCaptureLogger.LogCategory.MEMORY,
                "内存使用量过高",
                mapOf(
                    "current_memory_mb" to memoryMB,
                    "peak_memory_mb" to (peakMemoryUsage / (1024 * 1024)),
                    "threshold_mb" to MEMORY_WARNING_THRESHOLD_MB
                )
            )
        }
    }
    
    /**
     * 更新最小最大值
     */
    private fun updateMinMax(minAtomic: AtomicLong, maxAtomic: AtomicLong, value: Long) {
        // 更新最小值
        var currentMin = minAtomic.get()
        while (value < currentMin && !minAtomic.compareAndSet(currentMin, value)) {
            currentMin = minAtomic.get()
        }
        
        // 更新最大值
        var currentMax = maxAtomic.get()
        while (value > currentMax && !maxAtomic.compareAndSet(currentMax, value)) {
            currentMax = maxAtomic.get()
        }
    }
    
    /**
     * 更新性能历史记录
     */
    private fun updatePerformanceHistory(
        duration: Long,
        dataSize: Long,
        dataRate: Long,
        strategy: String,
        success: Boolean,
        errorType: String? = null
    ) {
        synchronized(performanceHistory) {
            val snapshot = PerformanceSnapshot(
                timestamp = System.currentTimeMillis(),
                captureTime = duration,
                dataSize = dataSize,
                dataRate = dataRate,
                strategy = strategy,
                success = success,
                errorType = errorType,
                memoryUsage = currentMemoryUsage
            )
            
            performanceHistory.add(snapshot)
            
            // 保持历史记录大小
            if (performanceHistory.size > PERFORMANCE_HISTORY_SIZE) {
                performanceHistory.removeAt(0)
            }
        }
        
        // 更新最近统计
        synchronized(recentCaptureTimes) {
            if (success) {
                recentCaptureTimes.add(duration)
                recentDataRates.add(dataRate)
                
                if (recentCaptureTimes.size > STATS_WINDOW_SIZE) {
                    recentCaptureTimes.removeAt(0)
                    recentDataRates.removeAt(0)
                }
            }
        }
    }
    
    /**
     * 检查性能问题
     * @param duration 捕获耗时
     * @param dataRate 数据传输率
     * @return 性能问题列表
     */
    private fun checkPerformanceIssues(duration: Long, dataRate: Long): List<String> {
        val issues = mutableListOf<String>()
        
        if (duration > SLOW_CAPTURE_THRESHOLD_MS) {
            issues.add("捕获耗时过长: ${duration}ms")
        }
        
        if (dataRate < LOW_DATA_RATE_THRESHOLD_KBPS * 1000) {
            issues.add("数据传输率过低: ${dataRate / 1000}kbps")
        }
        
        if (getErrorRate() > HIGH_ERROR_RATE_THRESHOLD) {
            issues.add("错误率过高: ${String.format("%.1f", getErrorRate() * 100)}%")
        }
        
        val memoryMB = currentMemoryUsage / (1024 * 1024)
        if (memoryMB > MEMORY_WARNING_THRESHOLD_MB) {
            issues.add("内存使用过高: ${memoryMB}MB")
        }
        
        return issues
    }
    
    /**
     * 获取成功率
     */
    fun getSuccessRate(): Double {
        val total = captureCount.get()
        return if (total > 0) successCount.get().toDouble() / total else 0.0
    }
    
    /**
     * 获取错误率
     */
    fun getErrorRate(): Double {
        val total = captureCount.get()
        return if (total > 0) errorCount.get().toDouble() / total else 0.0
    }
    
    /**
     * 获取平均捕获时间
     */
    fun getAverageCaptureTime(): Double {
        val successful = successCount.get()
        return if (successful > 0) totalCaptureTime.get().toDouble() / successful else 0.0
    }
    
    /**
     * 获取平均数据传输率
     */
    fun getAverageDataRate(): Double {
        synchronized(recentDataRates) {
            return if (recentDataRates.isNotEmpty()) {
                recentDataRates.average()
            } else 0.0
        }
    }
    
    /**
     * 获取最常用的策略
     */
    fun getMostUsedStrategy(): String? {
        return strategyUsage.maxByOrNull { it.value.get() }?.key
    }
    
    /**
     * 获取最常见的错误
     */
    fun getMostCommonError(): String? {
        return errorsByType.maxByOrNull { it.value.get() }?.key
    }
    
    /**
     * 获取当前性能问题
     */
    fun getCurrentPerformanceIssues(): List<String> {
        val issues = mutableListOf<String>()
        
        // 检查错误率
        if (getErrorRate() > HIGH_ERROR_RATE_THRESHOLD) {
            issues.add("错误率过高: ${String.format("%.1f", getErrorRate() * 100)}%")
        }
        
        // 检查平均捕获时间
        val avgTime = getAverageCaptureTime()
        if (avgTime > SLOW_CAPTURE_THRESHOLD_MS) {
            issues.add("平均捕获时间过长: ${String.format("%.1f", avgTime)}ms")
        }
        
        // 检查数据传输率
        val avgRate = getAverageDataRate()
        if (avgRate < LOW_DATA_RATE_THRESHOLD_KBPS * 1000) {
            issues.add("平均数据传输率过低: ${String.format("%.1f", avgRate / 1000)}kbps")
        }
        
        // 检查内存使用
        val memoryMB = currentMemoryUsage / (1024 * 1024)
        if (memoryMB > MEMORY_WARNING_THRESHOLD_MB) {
            issues.add("内存使用过高: ${memoryMB}MB")
        }
        
        // 检查策略切换频率
        val monitorDurationHours = (System.currentTimeMillis() - monitorStartTime) / 3600000.0
        val switchRate = if (monitorDurationHours > 0) strategySwitchCount.get() / monitorDurationHours else 0.0
        if (switchRate > 5) { // 每小时超过5次切换
            issues.add("策略切换过于频繁: ${String.format("%.1f", switchRate)}次/小时")
        }
        
        return issues
    }
    
    /**
     * 获取完整的性能统计
     */
    fun getPerformanceStats(): PerformanceStats {
        val monitorDuration = System.currentTimeMillis() - monitorStartTime
        val totalCaptures = captureCount.get()
        val successfulCaptures = successCount.get()
        val failedCaptures = errorCount.get()
        
        return PerformanceStats(
            monitorDuration = monitorDuration,
            totalCaptures = totalCaptures,
            successfulCaptures = successfulCaptures,
            failedCaptures = failedCaptures,
            successRate = getSuccessRate(),
            averageCaptureTime = getAverageCaptureTime(),
            minCaptureTime = if (minCaptureTime.get() == Long.MAX_VALUE) 0 else minCaptureTime.get(),
            maxCaptureTime = maxCaptureTime.get(),
            averageDataRate = getAverageDataRate(),
            minDataRate = if (minDataRate.get() == Long.MAX_VALUE) 0 else minDataRate.get(),
            maxDataRate = maxDataRate.get(),
            totalDataTransferred = totalDataSize.get(),
            strategySwitches = strategySwitchCount.get(),
            mostUsedStrategy = getMostUsedStrategy(),
            mostCommonError = getMostCommonError(),
            peakMemoryUsage = peakMemoryUsage,
            currentMemoryUsage = currentMemoryUsage,
            performanceIssues = getCurrentPerformanceIssues()
        )
    }
    
    /**
     * 获取性能历史记录
     */
    fun getPerformanceHistory(): List<PerformanceSnapshot> {
        synchronized(performanceHistory) {
            return ArrayList(performanceHistory)
        }
    }
    
    /**
     * 获取错误统计
     */
    fun getErrorStatistics(): Map<String, Any> {
        return mapOf(
            "total_errors" to errorCount.get(),
            "error_rate" to getErrorRate(),
            "errors_by_type" to errorsByType.mapValues { it.value.get() },
            "errors_by_strategy" to errorsByStrategy.mapValues { it.value.get() },
            "most_common_error" to (getMostCommonError() ?: "")
        )
    }
    
    /**
     * 获取策略使用统计
     */
    fun getStrategyStatistics(): Map<String, Any> {
        return mapOf(
            "strategy_usage" to strategyUsage.mapValues { it.value.get() },
            "strategy_switches" to strategySwitchCount.get(),
            "most_used_strategy" to (getMostUsedStrategy() ?: "")
        )
    }
    
    /**
     * 重置统计数据
     */
    fun reset() {
        captureCount.set(0)
        successCount.set(0)
        errorCount.set(0)
        
        totalCaptureTime.set(0)
        minCaptureTime.set(Long.MAX_VALUE)
        maxCaptureTime.set(0)
        
        totalDataSize.set(0)
        minDataRate.set(Long.MAX_VALUE)
        maxDataRate.set(0)
        
        errorsByType.clear()
        errorsByStrategy.clear()
        strategyUsage.clear()
        strategySwitchCount.set(0)
        
        synchronized(performanceHistory) {
            performanceHistory.clear()
        }
        
        synchronized(recentCaptureTimes) {
            recentCaptureTimes.clear()
            recentDataRates.clear()
        }
        
        peakMemoryUsage = 0L
        currentMemoryUsage = 0L
        
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.PERFORMANCE,
            "性能监控器已重置"
        )
    }
    
    /**
     * 清理历史数据（用于内存优化）
     * @param keepRecentCount 保留最近的历史记录数量
     */
    fun clearHistory(keepRecentCount: Int = 10) {
        synchronized(performanceHistory) {
            val sizeBefore = performanceHistory.size
            if (performanceHistory.size > keepRecentCount) {
                val recentHistory = if (performanceHistory.size <= keepRecentCount) {
                    ArrayList(performanceHistory)
                } else {
                    ArrayList(performanceHistory.subList(performanceHistory.size - keepRecentCount, performanceHistory.size))
                }
                performanceHistory.clear()
                performanceHistory.addAll(recentHistory)
            }
            val sizeAfter = performanceHistory.size
            val removedCount = sizeBefore - sizeAfter
            
            if (removedCount > 0) {
                AudioCaptureLogger.info(
                    AudioCaptureLogger.LogCategory.PERFORMANCE,
                    "清理性能历史数据完成",
                    mapOf(
                        "removed_entries" to removedCount,
                        "remaining_entries" to sizeAfter,
                        "kept_recent_count" to keepRecentCount
                    )
                )
            }
        }
        
        synchronized(recentCaptureTimes) {
            val timesSizeBefore = recentCaptureTimes.size
            val ratesSizeBefore = recentDataRates.size
            
            if (recentCaptureTimes.size > keepRecentCount) {
                val recentTimes = if (recentCaptureTimes.size <= keepRecentCount) {
                    ArrayList(recentCaptureTimes)
                } else {
                    ArrayList(recentCaptureTimes.subList(recentCaptureTimes.size - keepRecentCount, recentCaptureTimes.size))
                }
                recentCaptureTimes.clear()
                recentCaptureTimes.addAll(recentTimes)
            }
            
            if (recentDataRates.size > keepRecentCount) {
                val recentRates = if (recentDataRates.size <= keepRecentCount) {
                    ArrayList(recentDataRates)
                } else {
                    ArrayList(recentDataRates.subList(recentDataRates.size - keepRecentCount, recentDataRates.size))
                }
                recentDataRates.clear()
                recentDataRates.addAll(recentRates)
            }
        }
    }
    
    /**
     * 生成性能报告
     */
    fun generatePerformanceReport(): String {
        val stats = getPerformanceStats()
        val monitorDurationHours = stats.monitorDuration / 3600000.0
        
        return buildString {
            appendLine("=== 音频捕获性能报告 ===")
            appendLine("监控时长: ${String.format("%.2f", monitorDurationHours)} 小时")
            appendLine("")
            
            appendLine("=== 基础统计 ===")
            appendLine("总捕获次数: ${stats.totalCaptures}")
            appendLine("成功次数: ${stats.successfulCaptures}")
            appendLine("失败次数: ${stats.failedCaptures}")
            appendLine("成功率: ${String.format("%.2f", stats.successRate * 100)}%")
            appendLine("")
            
            appendLine("=== 时间统计 ===")
            appendLine("平均捕获时间: ${String.format("%.2f", stats.averageCaptureTime)} ms")
            appendLine("最短捕获时间: ${stats.minCaptureTime} ms")
            appendLine("最长捕获时间: ${stats.maxCaptureTime} ms")
            appendLine("")
            
            appendLine("=== 数据传输统计 ===")
            appendLine("总传输数据: ${stats.totalDataTransferred / (1024 * 1024)} MB")
            appendLine("平均传输率: ${String.format("%.2f", stats.averageDataRate / 1000)} kbps")
            appendLine("最低传输率: ${stats.minDataRate / 1000} kbps")
            appendLine("最高传输率: ${stats.maxDataRate / 1000} kbps")
            appendLine("")
            
            appendLine("=== 策略统计 ===")
            appendLine("策略切换次数: ${stats.strategySwitches}")
            appendLine("最常用策略: ${stats.mostUsedStrategy ?: "无"}")
            getStrategyStatistics()["strategy_usage"]?.let { usage ->
                appendLine("策略使用分布:")
                (usage as Map<*, *>).forEach { (strategy, count) ->
                    appendLine("  $strategy: $count 次")
                }
            }
            appendLine("")
            
            appendLine("=== 错误统计 ===")
            appendLine("最常见错误: ${stats.mostCommonError ?: "无"}")
            getErrorStatistics()["errors_by_type"]?.let { errors ->
                appendLine("错误类型分布:")
                (errors as Map<*, *>).forEach { (errorType, count) ->
                    appendLine("  $errorType: $count 次")
                }
            }
            appendLine("")
            
            appendLine("=== 内存统计 ===")
            appendLine("当前内存使用: ${stats.currentMemoryUsage / (1024 * 1024)} MB")
            appendLine("峰值内存使用: ${stats.peakMemoryUsage / (1024 * 1024)} MB")
            appendLine("")
            
            appendLine("=== 性能问题 ===")
            if (stats.performanceIssues.isNotEmpty()) {
                stats.performanceIssues.forEach { issue ->
                    appendLine("⚠️ $issue")
                }
            } else {
                appendLine("✅ 未发现性能问题")
            }
        }
    }
}