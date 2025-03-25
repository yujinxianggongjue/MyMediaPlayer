package com.example.mymediaplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder

/**
 * MediaPlayerManager 负责管理 MediaPlayer 的初始化、播放、暂停、重播等操作。
 */
class MediaPlayerManager(
    private val context: Context,
    private val listener: MediaPlayerListener
) {

    private var mediaPlayer: MediaPlayer? = null
    private var soundEffectManager: SoundEffectManager? = null

    /**
     * 初始化 MediaPlayer 并准备播放。
     * @param fileUri 要播放的文件的 Uri。
     * @param isVideo 是否是视频文件。
     */
    fun initMediaPlayer(fileUri: Uri, isVideo: Boolean) {
        release() // 释放之前的 MediaPlayer

        mediaPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )

                setDataSource(context, fileUri)
                setOnPreparedListener { mp ->
                    if (isVideo) {
                        listener.onPrepared(mp.duration, true, mp.videoWidth, mp.videoHeight)
                    } else {
                        listener.onPrepared(mp.duration, false, 0, 0)
                    }
                    mp.start()
                }
                setOnCompletionListener {
                    listener.onCompletion()
                }
                prepareAsync() // 异步准备

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MediaPlayer: ${e.message}", e)
                release()
            }
        }
    }

    /**
     * 设置播放界面。
     * @param holder SurfaceHolder。
     */
    fun setDisplay(holder: SurfaceHolder) {
        mediaPlayer?.setDisplay(holder)
    }

    /**
     * 获取音频会话 ID。
     * @return 音频会话 ID，如果 MediaPlayer 未初始化则返回错误码。
     */
    fun getAudioSessionId(): Int {
        return mediaPlayer?.audioSessionId ?: AudioManager.ERROR
    }

    /**
     * 设置均衡器预设。
     * @param presetIndex 预设索引。
     */
    fun setEqualizerPreset(presetIndex: Short) {
        soundEffectManager?.setEqualizerPreset(presetIndex)
    }

    /**
     * 启用或禁用虚拟化器（立体环绕）。
     * @param enabled 是否启用。
     */
    fun enableVirtualizer(enabled: Boolean) {
        soundEffectManager?.enableVirtualizer(enabled)
    }

    /**
     * 启用或禁用低音增强。
     * @param enabled 是否启用。
     */
    fun enableBassBoost(enabled: Boolean) {
        soundEffectManager?.enableBassBoost(enabled)
    }

    /**
     * 播放或恢复播放。
     */
    fun play() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
            }
        }
    }

    /**
     * 暂停播放。
     */
    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
    }

    /**
     * 从头开始重播。
     */
    fun replay() {
        mediaPlayer?.let {
            it.seekTo(0)
            it.start()
        }
    }

    /**
     * 设置播放速度。
     * @param speed 播放速度，例如 1.0f 为正常速度。
     */
    fun setPlaybackSpeed(speed: Float) {
        mediaPlayer?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    it.playbackParams = it.playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting playback speed: ${e.message}", e)
                }
            }
        }
    }

    /**
     * 跳转到指定位置。
     * @param position 毫秒。
     */
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    /**
     * 获取当前播放位置。
     * @return 当前播放位置，单位毫秒。
     */
    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    /**
     * 获取媒体总时长。
     * @return 总时长，单位毫秒。
     */
    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    /**
     * 获取视频宽度。
     * @return 视频宽度，如果不是视频则返回 0。
     */
    fun getVideoWidth(): Int {
        return mediaPlayer?.videoWidth ?: 0
    }

    /**
     * 获取视频高度。
     * @return 视频高度，如果不是视频则返回 0。
     */
    fun getVideoHeight(): Int {
        return mediaPlayer?.videoHeight ?: 0
    }

    /**
     * 判断是否正在播放。
     * @return true 表示正在播放，false 表示未播放。
     */
    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    /**
     * 初始化音效管理器。
     */
    fun initSoundEffects() {
        soundEffectManager = SoundEffectManager(getAudioSessionId())
    }

    /**
     * 设置音量
     * @param volume 音量值（0.0f - 1.0f）
     */
    fun setVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    /**
     * 释放 MediaPlayer 和音效资源。
     */
    fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            soundEffectManager?.release()
            soundEffectManager = null
            Log.d(TAG, "MediaPlayer 和 SoundEffectManager 已释放。")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}", e)
        }
    }

    /**
     * 设置是否循环播放。
     * @param looping 是否循环播放。
     */
    fun setLooping(looping: Boolean) {
        mediaPlayer?.isLooping = looping
    }

    /**
     * 停止播放。
     */
    fun stop() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
        }
    }

    companion object {
        private const val TAG = "MediaPlayerManager"
    }
}