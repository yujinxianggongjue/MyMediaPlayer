package com.example.mymediaplayer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast

/**
 * PermissionManager 负责管理应用的权限请求
 */
class PermissionManager(
    private val activity: Activity,
    private val callback: PermissionCallback
) {

    /**
     * 检查并请求 RECORD_AUDIO 权限
     */
    fun checkAndRequestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // 如果需要向用户解释为何需要该权限，可以在这里添加逻辑
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                )
            ) {
                // 显示权限请求解释对话框
                AlertDialog.Builder(activity)
                    .setTitle("权限请求")
                    .setMessage("需要访问麦克风以实现音频可视化功能。")
                    .setPositiveButton("确定") { dialog, _ ->
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            REQUEST_CODE_RECORD_AUDIO
                        )
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(
                            activity,
                            "权限被拒绝，音频可视化功能将无法使用。",
                            Toast.LENGTH_SHORT
                        ).show()
                        callback.onPermissionDenied()
                    }
                    .create()
                    .show()
            } else {
                // 直接请求权限
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_CODE_RECORD_AUDIO
                )
            }
        } else {
            // 已经拥有权限，回调
            callback.onPermissionGranted()
        }
    }

    /**
     * 处理权限请求的回调
     * 应在 Activity 的 onRequestPermissionsResult 中调用
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予
                Toast.makeText(activity, "权限已授予。", Toast.LENGTH_SHORT).show()
                callback.onPermissionGranted()
            } else {
                // 权限被拒绝
                Toast.makeText(
                    activity,
                    "权限被拒绝，音频可视化功能将无法使用。",
                    Toast.LENGTH_SHORT
                ).show()
                callback.onPermissionDenied()
            }
        }
    }

    companion object {
        const val REQUEST_CODE_RECORD_AUDIO = 123 // 自定义请求码
    }
}

/**
 * PermissionManager 的回调接口
 */
interface PermissionCallback {
    /**
     * 当权限被授予时回调
     */
    fun onPermissionGranted()

    /**
     * 当权限被拒绝时回调
     */
    fun onPermissionDenied()
}