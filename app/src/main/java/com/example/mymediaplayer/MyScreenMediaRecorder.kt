package com.example.mymediaplayer.recorder

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.RequiresApi
import java.io.File

/**
 * 仅演示: 使用MediaRecorder录制屏幕视频(可带麦克风audioSource=MIC),
 * 不录内部音频(因为我们用MyScreenInternalAudioRecorder单独处理了).
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MyScreenMediaRecorder(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val outputFile: File,
    private val handler: Handler? = null,
    private val recordMic: Boolean = false  // true表示录麦克风
) {
    companion object {
        private const val TAG = "MyScreenMediaRecorder"
        private const val MAX_FILE_SIZE = 2L * 1024L * 1024L * 1024L  // 2GB
        private const val MAX_DURATION_MS = 60 * 60 * 1000  // 1 hour
    }

    private var mMediaRecorder: MediaRecorder? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mSurface: Surface? = null

    /**
     * 准备MediaRecorder
     */
    fun prepare() {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        mMediaRecorder = MediaRecorder().apply {
            // 音频源(只录MIC, 不录内部音频)
            if (recordMic) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(30) // 30fps
            setVideoEncodingBitRate(width * height /*大概估算码率*/)
            if (recordMic) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
            }
            setMaxFileSize(MAX_FILE_SIZE)
            setMaxDuration(MAX_DURATION_MS.toInt())
            setOutputFile(outputFile.absolutePath)
            prepare()
        }

        mSurface = mMediaRecorder?.surface
        // 创建虚拟显示, 把屏幕内容"画"到 mSurface
        mVirtualDisplay = mediaProjection.createVirtualDisplay(
            "MyScreenRecorderVDisplay",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mSurface, object : VirtualDisplay.Callback() {
                override fun onStopped() {
                    Log.d(TAG, "VirtualDisplay onStopped")
                    // 可以在此通知外部
                }
            },
            handler
        )
    }

    fun start() {
        Log.d(TAG, "start recording -> " + outputFile.absolutePath)
        mMediaRecorder?.start()
    }

    fun stop() {
        Log.d(TAG, "stop recording.")
        try {
            mMediaRecorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mMediaRecorder?.release()
        mMediaRecorder = null

        mSurface?.release()
        mSurface = null

        mVirtualDisplay?.release()
        mVirtualDisplay = null

        mediaProjection.stop()
    }
}