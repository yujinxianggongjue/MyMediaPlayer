package com.example.mymediaplayer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/**
 * MediaSessionManager - 媒体会话管理器
 * 负责管理MediaSession的生命周期、播放状态、媒体元数据等
 * 提供与Android系统媒体控制框架的集成
 */
class MediaSessionManager(
    private val context: Context,
    private val mediaPlayerManager: MediaPlayerManager
) {
    companion object {
        private const val TAG = "MediaSessionManager"
        private const val MEDIA_SESSION_TAG = "MyMediaPlayerSession"
        
        // 播放状态常量
        private const val STATE_NONE = 0
        private const val STATE_STOPPED = 1
        private const val STATE_PAUSED = 2
        private const val STATE_PLAYING = 3
        private const val STATE_BUFFERING = 4
    }
    
    // MediaSession实例
    private var mediaSession: MediaSessionCompat? = null
    
    // 音频焦点管理
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    
    // 当前播放状态
    private var currentState = STATE_NONE
    
    // 当前媒体信息
    private var currentMediaUri: Uri? = null
    private var currentMediaTitle: String = "未知标题"
    private var currentMediaArtist: String = "未知艺术家"
    private var currentMediaAlbum: String = "未知专辑"
    private var currentMediaDuration: Long = 0L
    
    // 媒体会话回调接口
    interface MediaSessionCallback {
        /**
         * 播放状态改变回调
         * @param state 新的播放状态
         */
        fun onPlaybackStateChanged(state: Int)
        
        /**
         * 媒体元数据改变回调
         * @param metadata 新的媒体元数据
         */
        fun onMetadataChanged(metadata: MediaMetadataCompat?)
        
        /**
         * 音频焦点改变回调
         * @param focusChange 焦点变化类型
         */
        fun onAudioFocusChanged(focusChange: Int)
    }
    
    private var sessionCallback: MediaSessionCallback? = null
    
    /**
     * 初始化MediaSession
     * 创建MediaSession实例并设置回调
     */
    fun initializeMediaSession() {
        try {
            // 创建MediaSession
            mediaSession = MediaSessionCompat(context, MEDIA_SESSION_TAG).apply {
                // 设置支持的操作
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                
                // 设置回调
                setCallback(mediaSessionCallback)
                
                // 设置初始播放状态
                setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                        .setActions(getAvailableActions())
                        .build()
                )
                
                // 激活会话
                isActive = true
            }
            
            // 初始化音频管理器
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            Log.d(TAG, "MediaSession初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "MediaSession初始化失败: ${e.message}", e)
        }
    }
    
    /**
     * MediaSession回调实现
     * 处理来自系统和其他应用的媒体控制命令
     */
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        
        /**
         * 播放命令回调
         */
        override fun onPlay() {
            Log.d(TAG, "收到播放命令")
            requestAudioFocus()
            mediaPlayerManager.play()
            updatePlaybackState(STATE_PLAYING)
        }
        
        /**
         * 暂停命令回调
         */
        override fun onPause() {
            Log.d(TAG, "收到暂停命令")
            mediaPlayerManager.pause()
            updatePlaybackState(STATE_PAUSED)
        }
        
        /**
         * 停止命令回调
         */
        override fun onStop() {
            Log.d(TAG, "收到停止命令")
            mediaPlayerManager.pause()
            abandonAudioFocus()
            updatePlaybackState(STATE_STOPPED)
        }
        
        /**
         * 跳转命令回调
         * @param pos 跳转位置（毫秒）
         */
        override fun onSeekTo(pos: Long) {
            Log.d(TAG, "收到跳转命令: ${pos}ms")
            mediaPlayerManager.seekTo(pos.toInt())
            updatePlaybackState(currentState, pos)
        }
        
        /**
         * 设置播放速度回调
         * @param speed 播放速度
         */
        override fun onSetPlaybackSpeed(speed: Float) {
            Log.d(TAG, "收到设置播放速度命令: ${speed}x")
            mediaPlayerManager.setPlaybackSpeed(speed)
        }
        
        /**
         * 自定义动作回调
         * @param action 动作名称
         * @param extras 额外参数
         */
        override fun onCustomAction(action: String?, extras: Bundle?) {
            Log.d(TAG, "收到自定义动作: $action")
            when (action) {
                "REPLAY" -> {
                    mediaPlayerManager.replay()
                    updatePlaybackState(STATE_PLAYING)
                }
                "SET_EQUALIZER" -> {
                    val presetIndex = extras?.getShort("preset", 0) ?: 0
                    mediaPlayerManager.setEqualizerPreset(presetIndex)
                }
                "ENABLE_VIRTUALIZER" -> {
                    val enabled = extras?.getBoolean("enabled", false) ?: false
                    mediaPlayerManager.enableVirtualizer(enabled)
                }
                "ENABLE_BASS_BOOST" -> {
                    val enabled = extras?.getBoolean("enabled", false) ?: false
                    mediaPlayerManager.enableBassBoost(enabled)
                }
            }
        }
    }
    
    /**
     * 音频焦点变化监听器
     */
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "音频焦点变化: $focusChange")
        
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 获得音频焦点，可以正常播放
                if (currentState == STATE_PAUSED) {
                    mediaPlayerManager.play()
                    updatePlaybackState(STATE_PLAYING)
                }
                mediaPlayerManager.setVolume(1.0f)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 永久失去音频焦点，停止播放
                mediaPlayerManager.pause()
                updatePlaybackState(STATE_PAUSED)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 暂时失去音频焦点，暂停播放
                if (mediaPlayerManager.isPlaying()) {
                    mediaPlayerManager.pause()
                    updatePlaybackState(STATE_PAUSED)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 暂时失去音频焦点，但可以降低音量继续播放
                mediaPlayerManager.setVolume(0.3f)
            }
        }
        
        sessionCallback?.onAudioFocusChanged(focusChange)
    }
    
    /**
     * 请求音频焦点
     * @return 是否成功获得音频焦点
     */
    private fun requestAudioFocus(): Boolean {
        audioManager?.let { am ->
            val audioAttributes = AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                .build()
            
            audioFocusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            
            val result = AudioManagerCompat.requestAudioFocus(am, audioFocusRequest!!)
            return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return false
    }
    
    /**
     * 放弃音频焦点
     */
    private fun abandonAudioFocus() {
        audioManager?.let { am ->
            audioFocusRequest?.let { request ->
                AudioManagerCompat.abandonAudioFocusRequest(am, request)
            }
        }
    }
    
    /**
     * 更新播放状态
     * @param state 新的播放状态
     * @param position 当前播放位置（可选）
     */
    fun updatePlaybackState(state: Int, position: Long? = null) {
        currentState = state
        
        val playbackState = when (state) {
            STATE_PLAYING -> PlaybackStateCompat.STATE_PLAYING
            STATE_PAUSED -> PlaybackStateCompat.STATE_PAUSED
            STATE_STOPPED -> PlaybackStateCompat.STATE_STOPPED
            STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            else -> PlaybackStateCompat.STATE_NONE
        }
        
        val currentPosition = position ?: mediaPlayerManager.getCurrentPosition().toLong()
        
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(playbackState, currentPosition, 1.0f)
                .setActions(getAvailableActions())
                .build()
        )
        
        sessionCallback?.onPlaybackStateChanged(state)
        Log.d(TAG, "播放状态更新: $state, 位置: ${currentPosition}ms")
    }
    
    /**
     * 更新媒体元数据
     * @param uri 媒体文件URI
     * @param title 标题
     * @param artist 艺术家
     * @param album 专辑
     * @param duration 时长
     * @param albumArt 专辑封面
     */
    fun updateMediaMetadata(
        uri: Uri,
        title: String = "未知标题",
        artist: String = "未知艺术家",
        album: String = "未知专辑",
        duration: Long = 0L,
        albumArt: Bitmap? = null
    ) {
        currentMediaUri = uri
        currentMediaTitle = title
        currentMediaArtist = artist
        currentMediaAlbum = album
        currentMediaDuration = duration
        
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, uri.toString())
            .apply {
                albumArt?.let {
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                } ?: run {
                    // 使用默认专辑封面
                    val defaultArt = BitmapFactory.decodeResource(
                        context.resources,
                        R.drawable.ic_launcher_foreground
                    )
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, defaultArt)
                }
            }
            .build()
        
        mediaSession?.setMetadata(metadata)
        sessionCallback?.onMetadataChanged(metadata)
        
        Log.d(TAG, "媒体元数据更新: $title - $artist")
    }
    
    /**
     * 获取可用的播放操作
     * @return 支持的操作标志位
     */
    private fun getAvailableActions(): Long {
        return PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED or
                PlaybackStateCompat.ACTION_PLAY_PAUSE
    }
    
    /**
     * 设置会话回调
     * @param callback 回调接口实现
     */
    fun setSessionCallback(callback: MediaSessionCallback) {
        this.sessionCallback = callback
    }
    
    /**
     * 获取MediaSession令牌
     * @return MediaSession令牌，用于MediaController连接
     */
    fun getSessionToken(): MediaSessionCompat.Token? {
        return mediaSession?.sessionToken
    }
    
    /**
     * 释放MediaSession资源
     */
    fun release() {
        Log.d(TAG, "释放MediaSession资源")
        
        abandonAudioFocus()
        
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        mediaSession = null
        
        audioManager = null
        audioFocusRequest = null
        sessionCallback = null
    }
    
    /**
     * 获取当前播放状态
     * @return 当前播放状态
     */
    fun getCurrentState(): Int {
        return currentState
    }
    
    /**
     * 获取当前媒体信息
     * @return 包含当前媒体信息的Bundle
     */
    fun getCurrentMediaInfo(): Bundle {
        return Bundle().apply {
            putString("title", currentMediaTitle)
            putString("artist", currentMediaArtist)
            putString("album", currentMediaAlbum)
            putLong("duration", currentMediaDuration)
            putString("uri", currentMediaUri?.toString())
        }
    }
}