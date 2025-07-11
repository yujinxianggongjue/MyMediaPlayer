package com.example.mymediaplayer

import android.app.Activity
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.TextView
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 用于捕获当前正在播放的音频数据 (AudioPlaybackCapture)，
 * 并将其录制到 PCM/WAV 文件。
 *
 * 注意：Android 10+ 才支持 AudioPlaybackCapture API。
 */
@RequiresApi(Build.VERSION_CODES.Q)
class AudioPlaybackCapture {

    companion object {
        private const val TAG = "zqqtestAudioPlaybackCapture"

        // 默认录制配置：44100Hz，单声道16bit
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    var isRecording = false
        private set

    private var mAudioRecord: AudioRecord? = null
    private var mRecordingThread: Thread? = null
    private var mCurrentOutputFile: File? = null

    // 用于在 UI 上显示录制状态的文本控件
    private var recordingStatusTextView: TextView? = null
    private var recordingPathTextView: TextView? = null

    /**
     * 设置 UI 控件以显示录制状态
     */
    fun setRecordingStatusTextView(textView: TextView) {
        recordingStatusTextView = textView
    }

    fun setRecordingPathTextView(textView: TextView) {
        recordingPathTextView = textView
    }

    /**
     * 开始捕获正在播放的音频数据
     *
     * @param mediaProjection 由 Activity 通过 MediaProjectionManager 获取到的 MediaProjection
     * @param outputFile 要保存的文件 (.pcm 或 .wav 后缀)
     */
    fun startCapture(mediaProjection: MediaProjection, outputFile: File) {
        if (isRecording) {
            Log.w(TAG, "Already recording!")
            return
        }
        
        try {
            // 记录输出文件
            mCurrentOutputFile = outputFile
            Log.d(TAG, "Starting audio capture to: ${outputFile.absolutePath}")

            // 1) 构建 AudioPlaybackCaptureConfiguration
            val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)     // 音乐、视频等
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                // 如果想捕获其他 usage，可继续添加
                .build()
            Log.d(TAG, "AudioPlaybackCaptureConfiguration created successfully")

            // 2) 获取最小缓冲区
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
            )
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                throw RuntimeException("Invalid buffer size: $bufferSize")
            }
            Log.d(TAG, "Buffer size: $bufferSize")

            // 3) 构建 AudioRecord，用于捕获系统播放音频
            Log.d(TAG, "Creating AudioRecord with playback capture config...")
            mAudioRecord = AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setPrivacySensitive(false)  // 设置为非隐私敏感
                .setAudioPlaybackCaptureConfig(playbackConfig)   // 关键：指定 PlaybackCaptureConfig
                .build()
            Log.d(TAG, "AudioRecord created successfully")

            if (mAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed! State: ${mAudioRecord?.state}")
                throw RuntimeException("AudioRecord initialization failed")
            }

            // 4) 开始录音
            mAudioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "startCapture: writing to ${outputFile.absolutePath}")

            // 5) 启动线程循环写文件
            mRecordingThread = Thread {
                writeAudioDataToFile(outputFile, bufferSize)
            }.apply {
                start()
            }

            // 更新 UI
            recordingStatusTextView?.post {
                recordingStatusTextView?.text = "Recording..."
                recordingPathTextView?.text = "Recording to: ${outputFile.absolutePath}"
            }
            
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "UnsupportedOperationException: ${e.message}")
            Log.e(TAG, "This usually means the app lacks proper permissions or is not signed as a system app")
            isRecording = false
            throw e
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            Log.e(TAG, "Audio capture permission denied")
            isRecording = false
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during audio capture setup: ${e.message}", e)
            isRecording = false
            throw e
        }
    }

    /**
     * 停止捕获
     */
    fun stopCapture(): String? {
        if (!isRecording) {
            Log.w(TAG, "Not recording!")
            return null
        }
        // 标记停止
        isRecording = false

        // 等待线程结束
        try {
            mRecordingThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        mRecordingThread = null

        // 停止并释放 AudioRecord
        mAudioRecord?.stop()
        mAudioRecord?.release()
        mAudioRecord = null

        val path = mCurrentOutputFile?.absolutePath
        Log.d(TAG, "stopCapture -> $path")

        // 更新 UI
        recordingStatusTextView?.post {
            recordingStatusTextView?.text = "Recording stopped"
            recordingPathTextView?.text = "Recording Path: $path"
        }

        return path
    }

    /**
     * 核心工作线程: 循环从AudioRecord读取PCM数据，并写入文件
     */
    private fun writeAudioDataToFile(outputFile: File, bufferSize: Int) {
        val data = ByteArray(bufferSize)
        var fos: FileOutputStream? = null
        try {
            fos = FileOutputStream(outputFile)
            var totalBytes = 0
            while (isRecording) {
                val read = mAudioRecord?.read(data, 0, bufferSize) ?: 0
                if (read > 0) {
                    fos.write(data, 0, read)
                    totalBytes += read
                    // 可监测是否静音
                    val isAllZero = data.take(read).all { it == 0.toByte() }
                    Log.d(TAG, "Read $read bytes, isAllZero=$isAllZero")
                }
            }
            Log.d(TAG, "Total bytes recorded: $totalBytes")
        } catch (e: IOException) {
            Log.e(TAG, "Error writing data: ${e.message}", e)
        } finally {
            try {
                fos?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Close stream fail: ${e.message}", e)
            }
        }
    }
}