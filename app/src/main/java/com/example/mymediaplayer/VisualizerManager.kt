package com.example.mymediaplayer

import android.media.audiofx.Visualizer
import android.util.Log

/**
 * VisualizerManager 负责管理 Visualizer 的初始化和数据捕获
 */
class VisualizerManager(
    private val listener: VisualizerListener
) {

    private var visualizer: Visualizer? = null

    /**
     * 初始化 Visualizer 并开始捕获音频数据
     * @param audioSessionId 音频会话 ID
     */
    fun initVisualizer(audioSessionId: Int) {
        if (audioSessionId == android.media.AudioManager.ERROR) {
            Log.e(TAG, "Invalid audio session ID.")
            return
        }

        release() // 释放之前的 Visualizer

        try {
            visualizer = Visualizer(audioSessionId).apply {
                val captureSize = Visualizer.getCaptureSizeRange()[1] // 设置为最大捕获大小
                setCaptureSize(captureSize)

                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        listener.onWaveformUpdate(waveform)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        listener.onFftUpdate(fft)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)

                enabled = true
                Log.d(TAG, "Visualizer 初始化并启用成功。")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Visualizer: ${e.message}", e)
        }
    }

    /**
     * 释放 Visualizer 资源
     */
    fun release() {
        visualizer?.release()
        visualizer = null
    }

    companion object {
        private const val TAG = "VisualizerManager"
    }
}

/**
 * VisualizerManager 的回调接口
 */
interface VisualizerListener {
    /**
     * 当波形数据更新时回调
     * @param waveform 波形数据
     */
    fun onWaveformUpdate(waveform: ByteArray?)

    /**
     * 当 FFT 数据更新时回调
     * @param fft FFT 数据
     */
    fun onFftUpdate(fft: ByteArray?)
}