package com.example.mymediaplayer

/**
 * 音量变化监听器接口
 * 用于监听音量变化事件并更新UI组件（如进度条）
 * 
 * @author CarAudioVolumeManager
 * @since 1.0
 */
interface VolumeChangeListener {
    
    /**
     * 音量变化回调方法
     * 当音量发生变化时调用此方法
     * 
     * @param volumePercent Int 当前音量百分比 (0-100)
     * @param maxVolume Int 最大音量值
     * @param currentVolume Int 当前音量值
     * @param isCarAudio Boolean 是否为车载音频模式
     */
    fun onVolumeChanged(
        volumePercent: Int,
        maxVolume: Int,
        currentVolume: Int,
        isCarAudio: Boolean
    )
    
    /**
     * 音量设置失败回调方法
     * 当音量设置操作失败时调用此方法
     * 
     * @param error String 错误信息
     * @param targetVolumePercent Int 目标音量百分比
     */
    fun onVolumeSetFailed(error: String, targetVolumePercent: Int)
    
    /**
     * 音频模式变化回调方法
     * 当音频控制模式发生变化时调用此方法
     * 
     * @param isCarAudio Boolean 是否切换到车载音频模式
     * @param reason String 模式变化原因
     */
    fun onAudioModeChanged(isCarAudio: Boolean, reason: String)
}