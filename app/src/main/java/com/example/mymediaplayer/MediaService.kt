package com.example.mymediaplayer

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log

/**
 * MediaService - 媒体服务
 * 集成MediaSession和MediaController框架的后台服务
 * 负责管理播放和录制功能的MediaSession集成
 */
class MediaService : Service(), MediaSessionManager.MediaSessionCallback {
    
    companion object {
        private const val TAG = "MediaService"
    }
    
    // 服务绑定器
    private val binder = MediaServiceBinder()
    
    // 媒体播放管理器
    private var mediaPlayerManager: MediaPlayerManager? = null
    
    // MediaSession管理器
    private var mediaSessionManager: MediaSessionManager? = null
    
    // MediaController管理器
    private var mediaControllerManager: MediaControllerManager? = null
    
    // 音频捕获服务引用
    private var audioCaptureService: AudioCaptureService? = null
    
    // 服务回调接口
    interface MediaServiceCallback {
        /**
         * 播放状态改变回调
         * @param state 播放状态
         */
        fun onPlaybackStateChanged(state: Int)
        
        /**
         * 媒体元数据改变回调
         * @param metadata 媒体元数据
         */
        fun onMetadataChanged(metadata: MediaMetadataCompat?)
        
        /**
         * 录制状态改变回调
         * @param isRecording 是否正在录制
         */
        fun onRecordingStateChanged(isRecording: Boolean)
        
        /**
         * 音频焦点改变回调
         * @param focusChange 焦点变化类型
         */
        fun onAudioFocusChanged(focusChange: Int)
    }
    
    private var serviceCallback: MediaServiceCallback? = null
    
    /**
     * 服务绑定器类
     * 提供外部访问服务的接口
     */
    inner class MediaServiceBinder : Binder() {
        /**
         * 获取服务实例
         * @return MediaService实例
         */
        fun getService(): MediaService = this@MediaService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MediaService创建")
        
        // 初始化媒体播放管理器
        initializeMediaPlayerManager()
        
        // 初始化MediaSession管理器
        initializeMediaSessionManager()
        
        // 初始化MediaController管理器
        initializeMediaControllerManager()
        
        // 获取音频捕获服务实例
        audioCaptureService = AudioCaptureService.getInstance()
    }
    
    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "MediaService绑定")
        return binder
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "MediaService解绑")
        return super.onUnbind(intent)
    }
    
    override fun onDestroy() {
        Log.d(TAG, "MediaService销毁")
        
        // 释放资源
        mediaControllerManager?.release()
        mediaSessionManager?.release()
        mediaPlayerManager?.release()
        
        super.onDestroy()
    }
    
    /**
     * 初始化媒体播放管理器
     */
    private fun initializeMediaPlayerManager() {
        mediaPlayerManager = MediaPlayerManager(this, object : MediaPlayerListener {
            override fun onPrepared(duration: Int, isVideo: Boolean, videoWidth: Int, videoHeight: Int) {
                Log.d(TAG, "媒体准备完成: 时长=${duration}ms, 是否视频=$isVideo")
                
                // 更新MediaSession播放状态
                mediaSessionManager?.updatePlaybackState(1) // STATE_PLAYING
            }
            
            override fun onCompletion() {
                Log.d(TAG, "媒体播放完成")
                
                // 更新MediaSession播放状态
                mediaSessionManager?.updatePlaybackState(2) // STATE_PAUSED
            }
        })
    }
    
    /**
     * 初始化MediaSession管理器
     */
    private fun initializeMediaSessionManager() {
        mediaPlayerManager?.let { playerManager ->
            mediaSessionManager = MediaSessionManager(this, playerManager).apply {
                initializeMediaSession()
                setSessionCallback(this@MediaService)
            }
        }
    }
    
    /**
     * 初始化MediaController管理器
     */
    private fun initializeMediaControllerManager() {
        mediaControllerManager = MediaControllerManager(this).apply {
            mediaSessionManager?.getSessionToken()?.let { token ->
                if (initializeMediaController(token)) {
                    setControllerCallback(object : MediaControllerManager.MediaControllerCallback {
                        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                            Log.d(TAG, "Controller收到播放状态变化: ${state?.state}")
                        }
                        
                        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                            Log.d(TAG, "Controller收到元数据变化: ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
                        }
                        
                        override fun onSessionEvent(event: String, extras: android.os.Bundle?) {
                            Log.d(TAG, "Controller收到会话事件: $event")
                        }
                        
                        override fun onSessionDestroyed() {
                            Log.d(TAG, "Controller收到会话销毁通知")
                        }
                    })
                }
            }
        }
    }
    
    // MediaSessionManager.MediaSessionCallback 实现
    
    override fun onPlaybackStateChanged(state: Int) {
        Log.d(TAG, "播放状态改变: $state")
        serviceCallback?.onPlaybackStateChanged(state)
    }
    
    override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        Log.d(TAG, "媒体元数据改变: ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}")
        serviceCallback?.onMetadataChanged(metadata)
    }
    
    override fun onAudioFocusChanged(focusChange: Int) {
        Log.d(TAG, "音频焦点改变: $focusChange")
        serviceCallback?.onAudioFocusChanged(focusChange)
    }
    
    // 播放控制方法
    
    /**
     * 初始化媒体播放
     * @param fileUri 媒体文件URI
     * @param isVideo 是否为视频文件
     * @param title 媒体标题
     * @param artist 艺术家
     * @param album 专辑
     */
    fun initializeMedia(
        fileUri: Uri,
        isVideo: Boolean,
        title: String = "未知标题",
        artist: String = "未知艺术家",
        album: String = "未知专辑"
    ) {
        Log.d(TAG, "初始化媒体: $title")
        
        // 初始化MediaPlayer
        mediaPlayerManager?.initMediaPlayer(fileUri, isVideo)
        
        // 更新MediaSession元数据
        mediaSessionManager?.updateMediaMetadata(
            uri = fileUri,
            title = title,
            artist = artist,
            album = album,
            duration = mediaPlayerManager?.getDuration()?.toLong() ?: 0L
        )
    }
    
    /**
     * 播放媒体
     */
    fun play() {
        Log.d(TAG, "播放媒体")
        mediaControllerManager?.play()
    }
    
    /**
     * 暂停播放
     */
    fun pause() {
        Log.d(TAG, "暂停播放")
        mediaControllerManager?.pause()
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        Log.d(TAG, "停止播放")
        mediaControllerManager?.stop()
    }
    
    /**
     * 重播
     */
    fun replay() {
        Log.d(TAG, "重播")
        mediaControllerManager?.replay()
    }
    
    /**
     * 跳转到指定位置
     * @param position 位置（毫秒）
     */
    fun seekTo(position: Long) {
        Log.d(TAG, "跳转到: ${position}ms")
        mediaControllerManager?.seekTo(position)
    }
    
    /**
     * 设置播放速度
     * @param speed 播放速度
     */
    fun setPlaybackSpeed(speed: Float) {
        Log.d(TAG, "设置播放速度: ${speed}x")
        mediaControllerManager?.setPlaybackSpeed(speed)
    }
    
    /**
     * 设置均衡器预设
     * @param presetIndex 预设索引
     */
    fun setEqualizerPreset(presetIndex: Short) {
        Log.d(TAG, "设置均衡器预设: $presetIndex")
        mediaControllerManager?.setEqualizerPreset(presetIndex)
    }
    
    /**
     * 启用或禁用虚拟化器
     * @param enabled 是否启用
     */
    fun enableVirtualizer(enabled: Boolean) {
        Log.d(TAG, "虚拟化器: $enabled")
        mediaControllerManager?.enableVirtualizer(enabled)
    }
    
    /**
     * 启用或禁用低音增强
     * @param enabled 是否启用
     */
    fun enableBassBoost(enabled: Boolean) {
        Log.d(TAG, "低音增强: $enabled")
        mediaControllerManager?.enableBassBoost(enabled)
    }
    
    // 录制控制方法
    
    /**
     * 开始录制
     * @param resultCode MediaProjection结果码
     * @param data MediaProjection数据
     * @return 是否成功开始录制
     */
    fun startRecording(resultCode: Int, data: Intent?): Boolean {
        Log.d(TAG, "开始录制")
        
        return try {
            // 通过广播启动录制
            val intent = Intent(AudioCaptureService.ACTION_START).apply {
                putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                if (data != null) {
                    putExtras(data)
                }
            }
            sendBroadcast(intent)
            
            // 更新录制状态
            serviceCallback?.onRecordingStateChanged(true)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "开始录制失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 停止录制
     * @return 录制文件路径，如果失败返回null
     */
    fun stopRecording(): String? {
        Log.d(TAG, "停止录制")
        
        return try {
            // 通过广播停止录制
            val intent = Intent(AudioCaptureService.ACTION_STOP)
            sendBroadcast(intent)
            
            // 获取录制结果
            val recordingResult = audioCaptureService?.stopCapture()
            
            // 更新录制状态
            serviceCallback?.onRecordingStateChanged(false)
            
            recordingResult
        } catch (e: Exception) {
            Log.e(TAG, "停止录制失败: ${e.message}", e)
            null
        }
    }
    
    /**
     * 检查是否正在录制
     * @return true表示正在录制，false表示未录制
     */
    fun isRecording(): Boolean {
        return audioCaptureService?.isRecording() ?: false
    }
    
    // 状态查询方法
    
    /**
     * 获取当前播放位置
     * @return 当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long {
        return mediaControllerManager?.getCurrentPosition() ?: 0L
    }
    
    /**
     * 获取媒体总时长
     * @return 媒体总时长（毫秒）
     */
    fun getDuration(): Long {
        return mediaControllerManager?.getDuration() ?: 0L
    }
    
    /**
     * 判断是否正在播放
     * @return true表示正在播放，false表示未播放
     */
    fun isPlaying(): Boolean {
        return mediaControllerManager?.isPlaying() ?: false
    }
    
    /**
     * 获取播放状态描述
     * @return 播放状态的文字描述
     */
    fun getPlaybackStateDescription(): String {
        return mediaControllerManager?.getPlaybackStateDescription() ?: "未知状态"
    }
    
    /**
     * 获取当前媒体信息
     * @return 包含当前媒体信息的Bundle
     */
    fun getCurrentMediaInfo(): android.os.Bundle {
        return mediaControllerManager?.getCurrentMediaSummary() ?: android.os.Bundle()
    }
    
    /**
     * 设置服务回调
     * @param callback 回调接口实现
     */
    fun setServiceCallback(callback: MediaServiceCallback) {
        this.serviceCallback = callback
    }
    
    /**
     * 获取MediaSessionManager实例
     * @return MediaSessionManager实例
     */
    fun getMediaSessionManager(): MediaSessionManager? {
        return mediaSessionManager
    }
    
    /**
     * 获取MediaControllerManager实例
     * @return MediaControllerManager实例
     */
    fun getMediaControllerManager(): MediaControllerManager? {
        return mediaControllerManager
    }
    
    /**
     * 获取MediaPlayerManager实例
     * @return MediaPlayerManager实例
     */
    fun getMediaPlayerManager(): MediaPlayerManager? {
        return mediaPlayerManager
    }
}