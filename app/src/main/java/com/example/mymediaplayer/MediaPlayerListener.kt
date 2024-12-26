package com.example.mymediaplayer

/**
 * MediaPlayerListener 接口用于接收来自 MediaPlayerManager 的媒体播放回调。
 */
interface MediaPlayerListener {
    /**
     * 当 MediaPlayer 准备完成时调用。
     * @param duration 媒体总时长，单位毫秒。
     * @param isVideo 是否是视频文件。
     * @param videoWidth 视频宽度（仅当 isVideo 为 true 时有效）。
     * @param videoHeight 视频高度（仅当 isVideo 为 true 时有效）。
     */
    fun onPrepared(duration: Int, isVideo: Boolean, videoWidth: Int, videoHeight: Int)

    /**
     * 当媒体播放完成时调用。
     */
    fun onCompletion()
}