package com.example.mymediaplayer.recorder

import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 模拟内部音频捕获 + 麦克风混音, 并实时编码成 AAC, 最终输出到一个 MP4 (仅含音轨).
 */
@RequiresApi(Build.VERSION_CODES.Q) // AudioPlaybackCapture API 需要 Android 10+
class MyScreenInternalAudioRecorder(
    private val outputFile: File,             // 输出音频文件, e.g. /data/user/0/.../internal_audio.mp4
    private val mediaProjection: MediaProjection,
    private val includeMicInput: Boolean = false
) {
    companion object {
        private const val TAG = "MyScreenInternalAudio"
        private const val AUDIO_MIME = MediaFormat.MIMETYPE_AUDIO_AAC
        private const val CHANNEL_COUNT = 1    // 单声道(可自行扩展)
        private const val SAMPLE_RATE = 44100
        private const val BIT_RATE = 128_000   // 128kbps
        private const val AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC
    }

    private var mAudioRecord: AudioRecord? = null
    private var mAudioRecordMic: AudioRecord? = null
    private var mCodec: MediaCodec? = null
    private var mMuxer: MediaMuxer? = null

    private var mMic = includeMicInput
    private var mThread: Thread? = null
    private var mStarted = false

    // AudioTrack ID in muxer
    private var mTrackId = -1
    private var mMuxerStarted = false

    // 时间戳相关
    private var mPresentationTimeUs: Long = 0

    /**
     * 初始化必要组件
     * - AudioRecord(系统音频+麦克风)
     * - MediaCodec(AAC), MediaMuxer(MP4)
     */
    @Throws(IOException::class)
    fun prepare() {
        Log.d(TAG, "prepare() outputFile=$outputFile")

        // 1) 构建 CaptureConfig, 指定要捕获的 usage
        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        // 2) 创建AudioRecord(内部音频)
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        mAudioRecord = AudioRecord.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setAudioPlaybackCaptureConfig(playbackConfig)
            .build()

        // 3) 如果需要麦克风, 再创建一个AudioRecord
        if (mMic) {
            val micMinBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            mAudioRecordMic = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT, // or VOICE_COMMUNICATION
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                micMinBuffer
            )
        }

        // 4) 创建MediaCodec (AAC)
        mCodec = MediaCodec.createEncoderByType(AUDIO_MIME)
        val mediaFormat = MediaFormat.createAudioFormat(AUDIO_MIME, SAMPLE_RATE, CHANNEL_COUNT)
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_PROFILE)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
        mCodec?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // 5) 创建MediaMuxer(输出MP4)
        mMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /**
     * 开始录制
     */
    fun start() {
        if (mStarted) return
        mStarted = true

        // 启动AudioRecord
        mAudioRecord?.startRecording()
        mAudioRecordMic?.startRecording()

        // 启动编码器
        mCodec?.start()

        // 启动工作线程
        mThread = Thread(encodeLoop, "MyScreenInternalAudioRecorder")
        mThread?.start()

        Log.d(TAG, "start() done.")
    }

    /**
     * 循环读取PCM, 送去MediaCodec编码, 再写入MediaMuxer
     */
    private val encodeLoop = Runnable {
        val bufferInfo = MediaCodec.BufferInfo()

        // PCM临时缓冲(系统音频 + 麦克风混音)
        val bufferSize = 4096
        val tempBuf = ByteArray(bufferSize)

        while (mStarted) {
            // 1) 读取系统音频PCM
            val readSys = mAudioRecord?.read(tempBuf, 0, bufferSize) ?: 0

            // 2) 如果有麦克风, 再读mic
            if (mMic) {
                val tempMicBuf = ByteArray(bufferSize)
                val readMic = mAudioRecordMic?.read(tempMicBuf, 0, bufferSize) ?: 0
                // 混音: 简单示例 -> 两条流都转 short, 做加和
                mixPcm(tempBuf, readSys, tempMicBuf, readMic)
            }

            // 3) 把PCM数据送入编码器
            encodePcmToAAC(tempBuf, readSys, bufferInfo)

            // 4) 读取编码输出写到Muxer
            drainEncoder(bufferInfo)
        }

        // 录制被停止后, 再 drain 一次, 发送end of stream
        encodePcmToAAC(null, 0, bufferInfo, endOfStream = true)
        drainEncoder(bufferInfo)

        // 停止并释放
        releaseResources()
        Log.d(TAG, "encodeLoop exit.")
    }

    /**
     * 将PCM写入编码器
     */
    private fun encodePcmToAAC(
        pcmData: ByteArray?,
        length: Int,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean = false
    ) {
        val codec = mCodec ?: return
        val inIndex = codec.dequeueInputBuffer(10_000)
        if (inIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inIndex) ?: return
            if (!endOfStream) {
                inputBuffer.clear()
                inputBuffer.put(pcmData, 0, length)
                val pts = computePresentationTimeUs(length)
                codec.queueInputBuffer(inIndex, 0, length, pts, 0)
            } else {
                // 发送结束标志
                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        }
    }

    /**
     * 从编码器拉取输出AAC数据, 写入 Muxer
     */
    private fun drainEncoder(bufferInfo: MediaCodec.BufferInfo) {
        val codec = mCodec ?: return
        val muxer = mMuxer ?: return

        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    mTrackId = muxer.addTrack(newFormat)
                    muxer.start()
                    mMuxerStarted = true
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break
                }
                outIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outIndex) ?: continue
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && mMuxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(mTrackId, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.d(TAG, "End of AAC stream reached.")
                        break
                    }
                }
            }
        }
    }

    /**
     * 混合系统音频 和 麦克风PCM(简单加和)
     */
    private fun mixPcm(sysBuf: ByteArray, sysLen: Int, micBuf: ByteArray, micLen: Int) {
        // 简单示例：只加和前 minLen
        val minLen = minOf(sysLen, micLen)
        var i = 0
        while (i < minLen) {
            val s1 = sysBuf[i].toInt()
            val s2 = micBuf[i].toInt()
            // 简易混音
            val mixed = (s1 + s2).coerceIn(-128, 127)
            sysBuf[i] = mixed.toByte()
            i++
        }
    }

    /**
     * 计算时间戳(微秒)
     * 这里简单假设 readLen 字节 = readLen/2 个short
     */
    private fun computePresentationTimeUs(readLen: Int): Long {
        val samples = readLen / 2 // short
        val us = samples * 1_000_000L / SAMPLE_RATE
        mPresentationTimeUs += us
        return mPresentationTimeUs
    }

    /**
     * 停止录制
     */
    fun stop() {
        if (!mStarted) return
        mStarted = false
        // 等工作线程退出, 会自动调用 releaseResources()
        mThread?.join()
        mThread = null
        Log.d(TAG, "stop() done.")
    }

    /**
     * 真正释放AudioRecord/MediaCodec/MediaMuxer等
     */
    private fun releaseResources() {
        mAudioRecord?.stop()
        mAudioRecord?.release()
        mAudioRecord = null

        mAudioRecordMic?.stop()
        mAudioRecordMic?.release()
        mAudioRecordMic = null

        mCodec?.stop()
        mCodec?.release()
        mCodec = null

        if (mMuxerStarted) {
            mMuxer?.stop()
        }
        mMuxer?.release()
        mMuxer = null
    }
}