package com.example.mymediaplayer.AudioRecoder

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.TextView
import java.io.FileDescriptor
import java.io.IOException
import java.util.Locale

/**
 * AudioRecorder 负责管理音频录制功能
 */
class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordedUri: Uri? = null
    private var recordStartTimeMs = 0L
    private val handler = Handler(Looper.getMainLooper())

    // 音频配置选项
    private val channelOptions = arrayOf("单声道", "立体声", "8声道", "12声道", "16声道")
    private val sampleRateOptions = arrayOf("8000Hz", "16000Hz", "22050Hz", "44100Hz", "48000Hz")
    private val bitRateOptions = arrayOf("32kbps", "64kbps", "128kbps", "192kbps", "256kbps")

    private val sampleRateValues = intArrayOf(8000, 16000, 22050, 44100, 48000)
    private val bitRateValues = intArrayOf(32000, 64000, 128000, 192000, 256000)

    private var currentChannelIndex = 0
    private var currentSampleRateIndex = 3 // 默认44100Hz
    private var currentBitRateIndex = 2    // 默认128kbps

    private var recordTimeTextView: TextView? = null
    private var recordPathTextView: TextView? = null

    companion object {
        private const val TAG = "AudioRecorder"
    }

    /**
     * 设置录音时间显示的TextView
     */
    fun setRecordTimeTextView(textView: TextView) {
        recordTimeTextView = textView
    }

    /**
     * 设置录音路径显示的TextView
     */
    fun setRecordPathTextView(textView: TextView) {
        recordPathTextView = textView
    }

    /**
     * 设置音频通道
     */
    fun setChannel(index: Int) {
        currentChannelIndex = index
    }

    /**
     * 设置采样率
     */
    fun setSampleRate(index: Int) {
        currentSampleRateIndex = index
    }

    /**
     * 设置比特率
     */
    fun setBitRate(index: Int) {
        currentBitRateIndex = index
    }

    private fun createMusicFd(fileName: String): FileDescriptor {
        Log.d(TAG, "createMusicFd: 创建文件名：$fileName")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(TAG, "createMusicFd: 仅支持 Android 10 以上")
            throw IOException("This demo requires API 29+")
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/aac")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
        }

        val newUri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Failed to insert MediaStore row")

        recordedUri = newUri
        Log.d(TAG, "createMusicFd: Uri 创建成功 -> $newUri")

        val fd = context.contentResolver.openFileDescriptor(newUri, "rw")?.fileDescriptor
            ?: throw IOException("Failed to open file descriptor")
        Log.d(TAG, "createMusicFd: FileDescriptor 创建成功")
        return fd
    }

    /**
     * 开始录音
     */
    fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "startRecording: 已经在录音中")
            return
        }

        try {
            val channelCount = when (currentChannelIndex) {
                0 -> 1  // 单声道
                1 -> 2  // 立体声
                2 -> 8  // 8声道
                3 -> 12 // 12声道
                4 -> 16 // 16声道
                else -> 2
            }
            val sampleRate = sampleRateValues[currentSampleRateIndex]
            val bitRate = bitRateValues[currentBitRateIndex] / 1000
            val fileName = String.format("recorded_audio_%dch_%dhz_%dkbps.aac", channelCount, sampleRate, bitRate)
            val fd = createMusicFd(fileName)

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(when (currentChannelIndex) {
                    0 -> 1  // 单声道
                    1 -> 2  // 立体声
                    2 -> 8  // 8声道
                    3 -> 12 // 12声道
                    4 -> 16 // 16声道
                    else -> 2
                })
                setAudioSamplingRate(sampleRateValues[currentSampleRateIndex])
                setAudioEncodingBitRate(bitRateValues[currentBitRateIndex])
                setOutputFile(fd)
                prepare()
                start()
            }

            isRecording = true
            recordStartTimeMs = System.currentTimeMillis()
            // 获取完整的文件路径
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            val cursor = context.contentResolver.query(recordedUri!!, projection, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val fullPath = c.getString(0)
                    recordPathTextView?.text = "Recording to: $fullPath"
                    Log.d(TAG, "File path: $fullPath")
                }
            }
            startRecordingTimer()
            Log.d(TAG, "startRecording: 开始录音")

        } catch (e: Exception) {
            Log.e(TAG, "startRecording error: ${e.message}")
            stopRecording()
        }
    }

    private fun startRecordingTimer() {
        handler.post(object : Runnable {
            override fun run() {
                if (isRecording) {
                    val duration = System.currentTimeMillis() - recordStartTimeMs
                    val seconds = (duration / 1000).toInt()
                    val minutes = seconds / 60
                    val remainingSeconds = seconds % 60
                    recordTimeTextView?.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds)
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    /**
     * 停止录音
     */
    fun stopRecording() {
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                mediaRecorder = null
                isRecording = false
                Log.d(TAG, "stopRecording: 停止录音")
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopRecording error: ${e.message}")
        } finally {
            mediaRecorder = null
            isRecording = false
        }
    }

    /**
     * 获取录音文件的Uri
     */
    fun getRecordedUri(): Uri? = recordedUri

    /**
     * 是否正在录音
     */
    fun isRecording(): Boolean = isRecording

    /**
     * 释放资源
     */
    fun release() {
        stopRecording()
        recordedUri = null
        recordTimeTextView = null
        recordPathTextView = null
    }
}