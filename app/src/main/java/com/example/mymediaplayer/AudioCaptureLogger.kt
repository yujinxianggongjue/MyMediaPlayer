package com.example.mymediaplayer

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayList

/**
 * 音频捕获统一日志系统
 * 提供结构化的日志记录，支持文件输出和实时监控
 */
object AudioCaptureLogger {
    
    private const val TAG = "zqqtestAudioCaptureLogger"
    private const val LOG_FILE_NAME = "audio_capture.log"
    private const val MAX_LOG_ENTRIES = 1000
    private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024 // 5MB
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private var logFile: File? = null
    private var isFileLoggingEnabled = false
    
    /**
     * 日志条目数据类
     */
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val category: LogCategory,
        val message: String,
        val details: Map<String, Any?> = emptyMap(),
        val throwable: Throwable? = null
    ) {
        fun toFormattedString(): String {
            val timeStr = dateFormat.format(Date(timestamp))
            val detailsStr = if (details.isNotEmpty()) {
                " | ${details.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
            } else ""
            
            val throwableStr = throwable?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""
            
            return "[$timeStr] ${level.name}/${category.name}: $message$detailsStr$throwableStr"
        }
    }
    
    /**
     * 日志级别
     */
    enum class LogLevel(val priority: Int) {
        VERBOSE(2),
        DEBUG(3),
        INFO(4),
        WARN(5),
        ERROR(6)
    }
    
    /**
     * 日志分类
     */
    enum class LogCategory {
        SYSTEM_INFO,      // 系统信息
        CAPTURE_ATTEMPT,  // 捕获尝试
        CAPTURE_SUCCESS,  // 捕获成功
        CAPTURE_ERROR,    // 捕获错误
        PERFORMANCE,      // 性能相关
        STRATEGY_SWITCH,  // 策略切换
        CONFIG_CHANGE,    // 配置变更
        PERMISSION,       // 权限相关
        SYSTEMUI,         // SystemUI相关
        LIFECYCLE,        // 生命周期
        MEMORY,           // 内存管理
        NETWORK,          // 网络传输
        USER_ACTION       // 用户操作
    }
    
    /**
     * 初始化日志系统
     * @param context 应用上下文
     * @param enableFileLogging 是否启用文件日志
     */
    fun initialize(context: Context, enableFileLogging: Boolean = true) {
        try {
            if (enableFileLogging) {
                val logDir = File(context.getExternalFilesDir(null), "logs")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                
                logFile = File(logDir, LOG_FILE_NAME)
                isFileLoggingEnabled = true
                
                // 检查日志文件大小，如果过大则轮转
                rotateLogFileIfNeeded()
            }
            
            // 记录系统信息
            logSystemInfo()
            
            info(LogCategory.SYSTEM_INFO, "音频捕获日志系统已初始化", mapOf(
                "file_logging" to enableFileLogging,
                "log_file" to logFile?.absolutePath
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化日志系统失败", e)
        }
    }
    
    /**
     * 记录系统信息
     */
    private fun logSystemInfo() {
        val systemInfo = mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "android_version" to Build.VERSION.RELEASE,
            "api_level" to Build.VERSION.SDK_INT,
            "build_type" to Build.TYPE,
            "hardware" to Build.HARDWARE,
            "board" to Build.BOARD,
            "bootloader" to Build.BOOTLOADER,
            "fingerprint" to Build.FINGERPRINT.take(50), // 截取前50字符
            "timestamp" to System.currentTimeMillis()
        )
        
        info(LogCategory.SYSTEM_INFO, "设备系统信息", systemInfo)
    }
    
    /**
     * 记录捕获尝试
     * @param strategy 使用的策略
     * @param config 配置信息
     */
    fun logCaptureAttempt(strategy: String, config: Map<String, Any?> = emptyMap()) {
        info(LogCategory.CAPTURE_ATTEMPT, "开始音频捕获尝试", mapOf(
            "strategy" to strategy,
            "config" to config
        ))
    }
    
    /**
     * 记录捕获成功
     * @param strategy 使用的策略
     * @param duration 捕获耗时（毫秒）
     * @param dataSize 数据大小（字节）
     */
    fun logCaptureSuccess(strategy: String, duration: Long, dataSize: Long = 0) {
        info(LogCategory.CAPTURE_SUCCESS, "音频捕获成功", mapOf(
            "strategy" to strategy,
            "duration_ms" to duration,
            "data_size_bytes" to dataSize,
            "data_rate_kbps" to if (duration > 0) (dataSize * 8 / duration) else 0
        ))
    }
    
    /**
     * 记录捕获错误
     * @param strategy 使用的策略
     * @param error 错误信息
     * @param errorType 错误类型
     * @param throwable 异常对象
     */
    fun logCaptureError(
        strategy: String, 
        error: String, 
        errorType: String = "UNKNOWN",
        throwable: Throwable? = null
    ) {
        error(LogCategory.CAPTURE_ERROR, "音频捕获失败", mapOf(
            "strategy" to strategy,
            "error_type" to errorType,
            "error_message" to error
        ), throwable)
    }
    
    /**
     * 记录性能信息
     * @param operation 操作名称
     * @param duration 耗时（毫秒）
     * @param metrics 性能指标
     */
    fun logPerformance(operation: String, duration: Long, metrics: Map<String, Any?> = emptyMap()) {
        debug(LogCategory.PERFORMANCE, "性能统计", mapOf(
            "operation" to operation,
            "duration_ms" to duration
        ) + metrics)
    }
    
    /**
     * 记录策略切换
     * @param fromStrategy 原策略
     * @param toStrategy 新策略
     * @param reason 切换原因
     */
    fun logStrategySwitch(fromStrategy: String, toStrategy: String, reason: String) {
        warn(LogCategory.STRATEGY_SWITCH, "音频捕获策略切换", mapOf(
            "from_strategy" to fromStrategy,
            "to_strategy" to toStrategy,
            "reason" to reason
        ))
    }
    
    /**
     * 记录配置变更
     * @param configKey 配置键
     * @param oldValue 旧值
     * @param newValue 新值
     * @param reason 变更原因
     */
    fun logConfigChange(configKey: String, oldValue: Any?, newValue: Any?, reason: String = "") {
        info(LogCategory.CONFIG_CHANGE, "配置变更", mapOf(
            "config_key" to configKey,
            "old_value" to oldValue,
            "new_value" to newValue,
            "reason" to reason
        ))
    }
    
    /**
     * 记录权限相关事件
     * @param permission 权限名称
     * @param granted 是否授予
     * @param requestMethod 请求方式
     */
    fun logPermission(permission: String, granted: Boolean, requestMethod: String = "") {
        val level = if (granted) LogLevel.INFO else LogLevel.WARN
        log(level, LogCategory.PERMISSION, "权限状态", mapOf(
            "permission" to permission,
            "granted" to granted,
            "request_method" to requestMethod
        ))
    }
    
    /**
     * 记录SystemUI相关事件
     * @param event 事件描述
     * @param healthy 是否健康
     * @param details 详细信息
     */
    fun logSystemUI(event: String, healthy: Boolean = true, details: Map<String, Any?> = emptyMap()) {
        val level = if (healthy) LogLevel.DEBUG else LogLevel.ERROR
        log(level, LogCategory.SYSTEMUI, event, mapOf(
            "healthy" to healthy
        ) + details)
    }
    
    /**
     * 记录生命周期事件
     * @param component 组件名称
     * @param lifecycle 生命周期状态
     * @param details 详细信息
     */
    fun logLifecycle(component: String, lifecycle: String, details: Map<String, Any?> = emptyMap()) {
        debug(LogCategory.LIFECYCLE, "生命周期事件", mapOf(
            "component" to component,
            "lifecycle" to lifecycle
        ) + details)
    }
    
    /**
     * 记录内存管理事件
     * @param event 事件描述
     * @param memoryInfo 内存信息
     */
    fun logMemory(event: String, memoryInfo: Map<String, Any?> = emptyMap()) {
        debug(LogCategory.MEMORY, event, memoryInfo)
    }
    
    /**
     * 记录用户操作
     * @param action 用户操作
     * @param details 操作详情
     */
    fun logUserAction(action: String, details: Map<String, Any?> = emptyMap()) {
        info(LogCategory.USER_ACTION, "用户操作", mapOf(
            "action" to action
        ) + details)
    }
    
    /**
     * 记录详细日志
     */
    fun verbose(category: LogCategory, message: String, details: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, category, message, details, throwable)
    }
    
    fun debug(category: LogCategory, message: String, details: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        log(LogLevel.DEBUG, category, message, details, throwable)
    }
    
    fun info(category: LogCategory, message: String, details: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        log(LogLevel.INFO, category, message, details, throwable)
    }
    
    fun warn(category: LogCategory, message: String, details: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        log(LogLevel.WARN, category, message, details, throwable)
    }
    
    fun error(category: LogCategory, message: String, details: Map<String, Any?> = emptyMap(), throwable: Throwable? = null) {
        log(LogLevel.ERROR, category, message, details, throwable)
    }
    
    /**
     * 核心日志记录方法
     */
    private fun log(
        level: LogLevel, 
        category: LogCategory, 
        message: String, 
        details: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null
    ) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            category = category,
            message = message,
            details = details,
            throwable = throwable
        )
        
        // 添加到内存队列
        addToMemoryQueue(entry)
        
        // 输出到Android Log
        outputToAndroidLog(entry)
        
        // 输出到文件
        if (isFileLoggingEnabled) {
            outputToFile(entry)
        }
    }
    
    /**
     * 添加到内存队列
     */
    private fun addToMemoryQueue(entry: LogEntry) {
        logEntries.offer(entry)
        
        // 保持队列大小
        while (logEntries.size > MAX_LOG_ENTRIES) {
            logEntries.poll()
        }
    }
    
    /**
     * 输出到Android Log
     */
    private fun outputToAndroidLog(entry: LogEntry) {
        val tag = "AudioCapture/${entry.category.name}"
        val message = if (entry.details.isNotEmpty()) {
            "${entry.message} | ${entry.details.entries.joinToString(", ") { "${it.key}=${it.value}" }}"
        } else {
            entry.message
        }
        
        when (entry.level) {
            LogLevel.VERBOSE -> Log.v(tag, message, entry.throwable)
            LogLevel.DEBUG -> Log.d(tag, message, entry.throwable)
            LogLevel.INFO -> Log.i(tag, message, entry.throwable)
            LogLevel.WARN -> Log.w(tag, message, entry.throwable)
            LogLevel.ERROR -> Log.e(tag, message, entry.throwable)
        }
    }
    
    /**
     * 输出到文件
     */
    private fun outputToFile(entry: LogEntry) {
        try {
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.appendLine(entry.toFormattedString())
                    entry.throwable?.let { throwable ->
                        writer.appendLine("  堆栈跟踪: ${Log.getStackTraceString(throwable)}")
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "写入日志文件失败", e)
        }
    }
    
    /**
     * 轮转日志文件
     */
    private fun rotateLogFileIfNeeded() {
        try {
            logFile?.let { file ->
                if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
                    val backupFile = File(file.parent, "${LOG_FILE_NAME}.backup")
                    if (backupFile.exists()) {
                        backupFile.delete()
                    }
                    file.renameTo(backupFile)
                    
                    Log.i(TAG, "日志文件已轮转，备份到: ${backupFile.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "轮转日志文件失败", e)
        }
    }
    
    /**
     * 获取最近的日志条目
     * @param count 条目数量
     * @param level 最低日志级别
     * @param category 日志分类过滤
     * @return 日志条目列表
     */
    fun getRecentLogs(
        count: Int = 50, 
        level: LogLevel = LogLevel.VERBOSE,
        category: LogCategory? = null
    ): List<LogEntry> {
        val filteredLogs = logEntries
            .filter { it.level.priority >= level.priority }
            .filter { category == null || it.category == category }
            .toList()
        
        return if (filteredLogs.size <= count) {
            filteredLogs
        } else {
            filteredLogs.subList(filteredLogs.size - count, filteredLogs.size)
        }
    }
    
    /**
     * 获取错误统计
     * @param timeRangeMs 时间范围（毫秒）
     * @return 错误统计信息
     */
    fun getErrorStats(timeRangeMs: Long = 3600000): Map<String, Any> { // 默认1小时
        val cutoffTime = System.currentTimeMillis() - timeRangeMs
        val recentErrors = logEntries
            .filter { it.timestamp >= cutoffTime && it.level == LogLevel.ERROR }
        
        val errorsByCategory = recentErrors.groupBy { it.category }
        val errorsByType = recentErrors
            .mapNotNull { it.details["error_type"] as? String }
            .groupBy { it }
            .mapValues { it.value.size }
        
        return mapOf(
            "total_errors" to recentErrors.size,
            "time_range_hours" to (timeRangeMs / 3600000.0),
            "errors_by_category" to errorsByCategory.mapValues { it.value.size },
            "errors_by_type" to errorsByType,
            "most_recent_error" to (recentErrors.lastOrNull()?.toFormattedString() ?: "")
        )
    }
    
    /**
     * 导出日志
     * @param includeSystemInfo 是否包含系统信息
     * @return 日志内容字符串
     */
    fun exportLogs(includeSystemInfo: Boolean = true): String {
        val logs = ArrayList<String>()
        
        if (includeSystemInfo) {
            logs.add("=== 音频捕获日志导出 ===")
            logs.add("导出时间: ${dateFormat.format(Date())}")
            logs.add("日志条目数: ${logEntries.size}")
            logs.add("")
        }
        
        logEntries.forEach { entry ->
            logs.add(entry.toFormattedString())
            entry.throwable?.let { throwable ->
                logs.add("  堆栈跟踪: ${Log.getStackTraceString(throwable)}")
            }
        }
        
        return logs.joinToString("\n")
    }
    
    /**
     * 清理日志
     * @param keepRecentCount 保留最近的条目数
     */
    fun clearLogs(keepRecentCount: Int = 100) {
        val allLogs = logEntries.toList()
        val recentLogs = if (allLogs.size <= keepRecentCount) {
            allLogs
        } else {
            allLogs.subList(allLogs.size - keepRecentCount, allLogs.size)
        }
        logEntries.clear()
        logEntries.addAll(recentLogs)
        
        info(LogCategory.SYSTEM_INFO, "日志已清理", mapOf(
            "kept_entries" to keepRecentCount,
            "total_entries" to logEntries.size
        ))
    }
    
    /**
     * 清理旧日志（用于内存优化）
     * @param maxAge 最大保留时间（毫秒），默认1小时
     */
    fun clearOldLogs(maxAge: Long = 3600000) {
        val cutoffTime = System.currentTimeMillis() - maxAge
        val sizeBefore = logEntries.size
        
        // 移除过期的日志条目
        val iterator = logEntries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.timestamp < cutoffTime) {
                iterator.remove()
            }
        }
        
        val sizeAfter = logEntries.size
        val removedCount = sizeBefore - sizeAfter
        
        if (removedCount > 0) {
            info(LogCategory.SYSTEM_INFO, "清理旧日志完成", mapOf(
                "removed_entries" to removedCount,
                "remaining_entries" to sizeAfter,
                "max_age_hours" to (maxAge / 3600000.0)
            ))
        }
    }
    
    /**
     * 获取日志文件路径
     */
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    /**
     * 检查日志系统健康状态
     */
    fun getHealthStatus(): Map<String, Any> {
        return mapOf(
            "initialized" to (logFile != null || !isFileLoggingEnabled),
            "file_logging_enabled" to isFileLoggingEnabled,
            "log_file_exists" to (logFile?.exists() == true),
            "log_file_size" to (logFile?.length() ?: 0),
            "memory_entries" to logEntries.size,
            "recent_errors" to getErrorStats(300000) // 最近5分钟的错误
        )
    }
}