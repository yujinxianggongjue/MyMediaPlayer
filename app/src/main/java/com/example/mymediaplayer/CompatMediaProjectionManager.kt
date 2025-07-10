package com.example.mymediaplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.widget.Toast

/**
 * 兼容的MediaProjection管理器
 * 用于解决MTK车载系统中SystemUI崩溃问题
 */
class CompatMediaProjectionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CompatMediaProjection"
        const val REQUEST_CODE_MEDIA_PROJECTION = 1001
    }
    
    private val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    
    /**
     * 安全地请求MediaProjection权限
     * 使用try-catch包装以防止SystemUI崩溃
     */
    fun requestPermissionSafely(activity: Activity, requestCode: Int = REQUEST_CODE_MEDIA_PROJECTION) {
        try {
            Log.d(TAG, "开始请求MediaProjection权限")
            
            // 检查SystemUI状态
            if (!isSystemUIHealthy()) {
                Log.w(TAG, "SystemUI状态异常，使用降级方案")
                fallbackToDirectAudioCapture(activity)
                return
            }
            
            // 创建权限请求Intent
            val intent = mediaProjectionManager.createScreenCaptureIntent()
            
            // 添加额外的安全标志
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            
            Log.d(TAG, "启动MediaProjection权限请求Activity")
            activity.startActivityForResult(intent, requestCode)
            
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection权限请求失败", e)
            handlePermissionRequestError(activity, e)
        }
    }
    
    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(resultCode: Int, data: Intent?): MediaProjection? {
        return try {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "MediaProjection权限获取成功")
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                mediaProjection
            } else {
                Log.w(TAG, "MediaProjection权限被拒绝")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理MediaProjection权限结果失败", e)
            null
        }
    }
    
    /**
     * 检查SystemUI健康状态
     */
    private fun isSystemUIHealthy(): Boolean {
        return try {
            // 尝试检查SystemUI进程状态
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningProcesses = activityManager.runningAppProcesses
            
            val systemUIProcess = runningProcesses?.find { 
                it.processName.contains("systemui", ignoreCase = true) 
            }
            
            val isHealthy = systemUIProcess != null
            Log.d(TAG, "SystemUI健康检查: ${if (isHealthy) "正常" else "异常"}")
            isHealthy
            
        } catch (e: Exception) {
            Log.w(TAG, "无法检查SystemUI状态", e)
            // 默认认为是健康的，让系统尝试
            true
        }
    }
    
    /**
     * 处理权限请求错误
     */
    private fun handlePermissionRequestError(activity: Activity, error: Exception) {
        when {
            error.message?.contains("NoSuchFieldError") == true -> {
                Log.e(TAG, "检测到androidx.lifecycle版本冲突")
                showErrorDialog(activity, "系统版本兼容性问题", "检测到库版本冲突，正在使用降级方案")
                fallbackToDirectAudioCapture(activity)
            }
            
            error.message?.contains("ActivityNotFoundException") == true -> {
                Log.e(TAG, "MediaProjection Activity未找到")
                showErrorDialog(activity, "系统功能不可用", "屏幕录制功能不可用，尝试其他方案")
                fallbackToDirectAudioCapture(activity)
            }
            
            else -> {
                Log.e(TAG, "未知的MediaProjection错误")
                showErrorDialog(activity, "权限请求失败", "无法获取屏幕录制权限: ${error.message}")
                fallbackToDirectAudioCapture(activity)
            }
        }
    }
    
    /**
     * 显示错误对话框
     */
    private fun showErrorDialog(activity: Activity, title: String, message: String) {
        activity.runOnUiThread {
            android.app.AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        }
    }
    
    /**
     * 降级方案：直接音频捕获
     */
    private fun fallbackToDirectAudioCapture(activity: Activity) {
        Log.i(TAG, "使用直接音频捕获降级方案")
        
        activity.runOnUiThread {
            Toast.makeText(activity, "使用直接音频捕获模式", Toast.LENGTH_SHORT).show()
        }
        
        // 通知MainActivity使用降级方案
        if (activity is MainActivity) {
            activity.startDirectAudioCapture()
        }
    }
    
    /**
     * 停止MediaProjection
     */
    fun stopMediaProjection() {
        try {
            mediaProjection?.stop()
            mediaProjection = null
            Log.d(TAG, "MediaProjection已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止MediaProjection失败", e)
        }
    }
    
    /**
     * 获取当前的MediaProjection实例
     */
    fun getMediaProjection(): MediaProjection? = mediaProjection
    
    /**
     * 检查是否有有效的MediaProjection
     */
    fun hasValidMediaProjection(): Boolean = mediaProjection != null
    
    /**
     * 重置MediaProjection状态
     */
    fun reset() {
        stopMediaProjection()
        Log.d(TAG, "MediaProjection管理器已重置")
    }
}