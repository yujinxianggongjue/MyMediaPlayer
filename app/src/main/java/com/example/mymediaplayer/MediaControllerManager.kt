package com.example.mymediaplayer

import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

/**
 * MediaControllerManager - 媒体控制器管理器
 * 负责管理MediaController的生命周期、发送控制命令、接收状态更新
 * 提供与MediaSession的双向通信能力
 */
class MediaControllerManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MediaControllerManager"
    }
    
    // MediaController实例
    private var mediaController: MediaControllerCompat? = null
    
    // 控制器回调接口
    interface MediaControllerCallback {
        /**
         * 播放状态改变回调
         * @param state 新的播放状态
         */
        fun onPlaybackStateChanged(state: PlaybackStateCompat?)
        
        /**
         * 媒体元数据改变回调
         * @param metadata 新的媒体元数据
         */
        fun onMetadataChanged(metadata: MediaMetadataCompat?)
        
        /**
         * 会话事件回调
         * @param event 事件名称
         * @param extras 事件参数
         */
        fun onSessionEvent(event: String, extras: Bundle?)
        
        /**
         * 会话销毁回调
         */
        fun onSessionDestroyed()
    }
    
    private var controllerCallback: MediaControllerCallback? = null
    
    /**
     * 初始化MediaController
     * @param sessionToken MediaSession的令牌
     * @return 是否初始化成功
     */
    fun initializeMediaController(sessionToken: MediaSessionCompat.Token): Boolean {
        return try {
            // 创建MediaController
            mediaController = MediaControllerCompat(context, sessionToken).apply {
                // 注册回调
                registerCallback(mediaControllerCallback)
            }
            
            Log.d(TAG, "MediaController初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "MediaController初始化失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * MediaController回调实现
     * 接收来自MediaSession的状态更新和事件
     */
    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        
        /**
         * 播放状态改变回调
         * @param state 新的播放状态
         */
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Log.d(TAG, "播放状态改变: ${state?.state}")
            controllerCallback?.onPlaybackStateChanged(state)
        }
        
        /**
         * 媒体元数据改变回调
         * @param metadata 新的媒体元数据
         */
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            Log.d(TAG, "媒体元数据改变: ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
            controllerCallback?.onMetadataChanged(metadata)
        }
        
        /**
         * 会话事件回调
         * @param event 事件名称
         * @param extras 事件参数
         */
        override fun onSessionEvent(event: String, extras: Bundle?) {
            Log.d(TAG, "收到会话事件: $event")
            controllerCallback?.onSessionEvent(event, extras)
        }
        
        /**
         * 会话销毁回调
         */
        override fun onSessionDestroyed() {
            Log.d(TAG, "MediaSession已销毁")
            controllerCallback?.onSessionDestroyed()
        }
    }
    
    /**
     * 播放控制命令
     */
    fun play() {
        mediaController?.transportControls?.play()
        Log.d(TAG, "发送播放命令")
    }
    
    /**
     * 暂停控制命令
     */
    fun pause() {
        mediaController?.transportControls?.pause()
        Log.d(TAG, "发送暂停命令")
    }
    
    /**
     * 停止控制命令
     */
    fun stop() {
        mediaController?.transportControls?.stop()
        Log.d(TAG, "发送停止命令")
    }
    
    /**
     * 跳转控制命令
     * @param position 跳转位置（毫秒）
     */
    fun seekTo(position: Long) {
        mediaController?.transportControls?.seekTo(position)
        Log.d(TAG, "发送跳转命令: ${position}ms")
    }
    
    /**
     * 设置播放速度控制命令
     * @param speed 播放速度
     */
    fun setPlaybackSpeed(speed: Float) {
        mediaController?.transportControls?.setPlaybackSpeed(speed)
        Log.d(TAG, "发送设置播放速度命令: ${speed}x")
    }
    
    /**
     * 发送自定义动作
     * @param action 动作名称
     * @param extras 额外参数
     */
    fun sendCustomAction(action: String, extras: Bundle? = null) {
        mediaController?.transportControls?.sendCustomAction(action, extras)
        Log.d(TAG, "发送自定义动作: $action")
    }
    
    /**
     * 重播控制命令
     */
    fun replay() {
        sendCustomAction("REPLAY")
    }
    
    /**
     * 设置均衡器预设
     * @param presetIndex 预设索引
     */
    fun setEqualizerPreset(presetIndex: Short) {
        val extras = Bundle().apply {
            putShort("preset", presetIndex)
        }
        sendCustomAction("SET_EQUALIZER", extras)
    }
    
    /**
     * 启用或禁用虚拟化器
     * @param enabled 是否启用
     */
    fun enableVirtualizer(enabled: Boolean) {
        val extras = Bundle().apply {
            putBoolean("enabled", enabled)
        }
        sendCustomAction("ENABLE_VIRTUALIZER", extras)
    }
    
    /**
     * 启用或禁用低音增强
     * @param enabled 是否启用
     */
    fun enableBassBoost(enabled: Boolean) {
        val extras = Bundle().apply {
            putBoolean("enabled", enabled)
        }
        sendCustomAction("ENABLE_BASS_BOOST", extras)
    }
    
    /**
     * 获取当前播放状态
     * @return 当前播放状态
     */
    fun getPlaybackState(): PlaybackStateCompat? {
        return mediaController?.playbackState
    }
    
    /**
     * 获取当前媒体元数据
     * @return 当前媒体元数据
     */
    fun getMetadata(): MediaMetadataCompat? {
        return mediaController?.metadata
    }
    
    /**
     * 获取当前播放位置
     * @return 当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long {
        return mediaController?.playbackState?.position ?: 0L
    }
    
    /**
     * 获取媒体总时长
     * @return 媒体总时长（毫秒）
     */
    fun getDuration(): Long {
        return mediaController?.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L
    }
    
    /**
     * 判断是否正在播放
     * @return true表示正在播放，false表示未播放
     */
    fun isPlaying(): Boolean {
        return mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
    }
    
    /**
     * 判断是否已暂停
     * @return true表示已暂停，false表示未暂停
     */
    fun isPaused(): Boolean {
        return mediaController?.playbackState?.state == PlaybackStateCompat.STATE_PAUSED
    }
    
    /**
     * 判断是否已停止
     * @return true表示已停止，false表示未停止
     */
    fun isStopped(): Boolean {
        val state = mediaController?.playbackState?.state
        return state == PlaybackStateCompat.STATE_STOPPED || state == PlaybackStateCompat.STATE_NONE
    }
    
    /**
     * 获取播放状态描述
     * @return 播放状态的文字描述
     */
    fun getPlaybackStateDescription(): String {
        return when (mediaController?.playbackState?.state) {
            PlaybackStateCompat.STATE_PLAYING -> "正在播放"
            PlaybackStateCompat.STATE_PAUSED -> "已暂停"
            PlaybackStateCompat.STATE_STOPPED -> "已停止"
            PlaybackStateCompat.STATE_BUFFERING -> "缓冲中"
            PlaybackStateCompat.STATE_ERROR -> "播放错误"
            PlaybackStateCompat.STATE_CONNECTING -> "连接中"
            PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> "跳转到下一首"
            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS -> "跳转到上一首"
            PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> "跳转到队列项目"
            PlaybackStateCompat.STATE_REWINDING -> "快退中"
            PlaybackStateCompat.STATE_FAST_FORWARDING -> "快进中"
            else -> "未知状态"
        }
    }
    
    /**
     * 获取当前媒体信息摘要
     * @return 包含当前媒体信息的Bundle
     */
    fun getCurrentMediaSummary(): Bundle {
        val metadata = getMetadata()
        val playbackState = getPlaybackState()
        
        return Bundle().apply {
            putString("title", metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "未知标题")
            putString("artist", metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: "未知艺术家")
            putString("album", metadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM) ?: "未知专辑")
            putLong("duration", metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L)
            putLong("position", playbackState?.position ?: 0L)
            putInt("state", playbackState?.state ?: PlaybackStateCompat.STATE_NONE)
            putString("stateDescription", getPlaybackStateDescription())
            putFloat("playbackSpeed", playbackState?.playbackSpeed ?: 1.0f)
        }
    }
    
    /**
     * 设置控制器回调
     * @param callback 回调接口实现
     */
    fun setControllerCallback(callback: MediaControllerCallback) {
        this.controllerCallback = callback
    }
    
    /**
     * 释放MediaController资源
     */
    fun release() {
        Log.d(TAG, "释放MediaController资源")
        
        mediaController?.let {
            it.unregisterCallback(mediaControllerCallback)
        }
        mediaController = null
        controllerCallback = null
    }
    
    /**
     * 检查MediaController是否已连接
     * @return true表示已连接，false表示未连接
     */
    fun isConnected(): Boolean {
        return mediaController != null
    }
    
    /**
     * 获取MediaController实例（用于高级操作）
     * @return MediaController实例，可能为null
     */
    fun getMediaController(): MediaControllerCompat? {
        return mediaController
    }
}