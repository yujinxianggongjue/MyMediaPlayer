package com.example.mymediaplayer

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PermissionManager 负责管理应用所需的权限请求。
 */
class PermissionManager(
    private val activity: Activity,
    private val callback: PermissionCallback
) {

    private val RECORD_AUDIO_PERMISSION_REQUEST_CODE = 100

    /**
     * 检查并请求录音权限。
     */
    fun checkAndRequestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 权限未被授予，申请权限
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_REQUEST_CODE
            )
        } else {
            // 权限已被授予
            callback.onPermissionGranted()
        }
    }

    /**
     * 处理权限请求结果。
     */
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            RECORD_AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限被授予
                    callback.onPermissionGranted()
                } else {
                    // 权限被拒绝
                    callback.onPermissionDenied()
                }
            }
        }
    }
}