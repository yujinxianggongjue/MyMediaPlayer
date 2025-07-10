package com.example.mymediaplayer

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 权限检查工具类
 * 用于诊断音频捕获相关的权限问题
 */
class PermissionChecker(private val context: Context) {
    
    companion object {
        private const val TAG = "zqqtestPermissionChecker"
    }
    
    /**
     * 检查所有音频捕获相关权限
     */
    fun checkAllPermissions(): PermissionStatus {
        val status = PermissionStatus()
        
        // 检查基础录音权限
        status.recordAudio = checkPermission(android.Manifest.permission.RECORD_AUDIO)
        
        // 检查前台服务权限
        status.foregroundService = checkPermission(android.Manifest.permission.FOREGROUND_SERVICE)
        
        // 检查MediaProjection前台服务权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            status.foregroundServiceMediaProjection = checkPermission(
                android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION
            )
        }
        
        // 检查系统级音频捕获权限
        status.captureAudioOutput = checkPermission(android.Manifest.permission.CAPTURE_AUDIO_OUTPUT)
        
        // 检查音频设置修改权限
        status.modifyAudioSettings = checkPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
        
        // 检查应用是否为系统应用
        status.isSystemApp = isSystemApp()
        
        // 检查设备API级别
        status.apiLevel = Build.VERSION.SDK_INT
        status.supportsAudioCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        
        logPermissionStatus(status)
        return status
    }
    
    /**
     * 检查单个权限
     */
    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查应用是否为系统应用
     */
    private fun isSystemApp(): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val flags = packageInfo.applicationInfo.flags
            (flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking system app status: ${e.message}")
            false
        }
    }
    
    /**
     * 记录权限状态到日志
     */
    private fun logPermissionStatus(status: PermissionStatus) {
        Log.i(TAG, "=== 权限检查结果 ===")
        Log.i(TAG, "RECORD_AUDIO: ${status.recordAudio}")
        Log.i(TAG, "FOREGROUND_SERVICE: ${status.foregroundService}")
        Log.i(TAG, "FOREGROUND_SERVICE_MEDIA_PROJECTION: ${status.foregroundServiceMediaProjection}")
        Log.i(TAG, "CAPTURE_AUDIO_OUTPUT: ${status.captureAudioOutput}")
        Log.i(TAG, "MODIFY_AUDIO_SETTINGS: ${status.modifyAudioSettings}")
        Log.i(TAG, "是否为系统应用: ${status.isSystemApp}")
        Log.i(TAG, "API级别: ${status.apiLevel}")
        Log.i(TAG, "支持音频捕获: ${status.supportsAudioCapture}")
        Log.i(TAG, "可以进行音频捕获: ${status.canCaptureAudio()}")
        Log.i(TAG, "==================")
    }
    
    /**
     * 获取权限问题的诊断信息
     */
    fun getDiagnosticInfo(status: PermissionStatus): String {
        val issues = mutableListOf<String>()
        
        if (!status.supportsAudioCapture) {
            issues.add("设备API级别过低（需要Android 10+）")
        }
        
        if (!status.recordAudio) {
            issues.add("缺少RECORD_AUDIO权限")
        }
        
        if (!status.foregroundService) {
            issues.add("缺少FOREGROUND_SERVICE权限")
        }
        
        if (!status.foregroundServiceMediaProjection) {
            issues.add("缺少FOREGROUND_SERVICE_MEDIA_PROJECTION权限")
        }
        
        if (!status.captureAudioOutput) {
            issues.add("缺少CAPTURE_AUDIO_OUTPUT权限（系统级权限）")
        }
        
        if (!status.isSystemApp) {
            issues.add("应用不是系统应用，无法使用CAPTURE_AUDIO_OUTPUT权限")
        }
        
        return if (issues.isEmpty()) {
            "所有权限检查通过"
        } else {
            "发现以下问题：\n" + issues.joinToString("\n• ", "• ")
        }
    }
    
    /**
     * 获取解决方案建议
     */
    fun getSolutionSuggestions(status: PermissionStatus): String {
        val suggestions = mutableListOf<String>()
        
        if (!status.supportsAudioCapture) {
            suggestions.add("升级设备到Android 10或更高版本")
        }
        
        if (!status.recordAudio) {
            suggestions.add("在运行时请求RECORD_AUDIO权限")
        }
        
        if (!status.captureAudioOutput || !status.isSystemApp) {
            suggestions.add("将应用签名为系统应用或安装到/system/app/目录")
            suggestions.add("考虑使用替代方案：麦克风录音或屏幕录制")
        }
        
        if (suggestions.isEmpty()) {
            return "权限配置正确，可以尝试音频捕获"
        }
        
        return "建议解决方案：\n" + suggestions.joinToString("\n• ", "• ")
    }
}

/**
 * 权限状态数据类
 */
data class PermissionStatus(
    var recordAudio: Boolean = false,
    var foregroundService: Boolean = false,
    var foregroundServiceMediaProjection: Boolean = false,
    var captureAudioOutput: Boolean = false,
    var modifyAudioSettings: Boolean = false,
    var isSystemApp: Boolean = false,
    var apiLevel: Int = 0,
    var supportsAudioCapture: Boolean = false
) {
    /**
     * 检查是否可以进行音频捕获
     */
    fun canCaptureAudio(): Boolean {
        return supportsAudioCapture && 
               recordAudio && 
               foregroundService && 
               foregroundServiceMediaProjection && 
               captureAudioOutput && 
               isSystemApp
    }
    
    /**
     * 检查是否可以使用基础录音功能
     */
    fun canRecordMicrophone(): Boolean {
        return recordAudio
    }
}