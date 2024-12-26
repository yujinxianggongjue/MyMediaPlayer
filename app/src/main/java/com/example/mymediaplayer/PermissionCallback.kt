package com.example.mymediaplayer

/**
 * PermissionCallback 接口用于处理权限请求的回调。
 */
interface PermissionCallback {
    /**
     * 当权限被授予时调用。
     */
    fun onPermissionGranted()

    /**
     * 当权限被拒绝时调用。
     */
    fun onPermissionDenied()
}