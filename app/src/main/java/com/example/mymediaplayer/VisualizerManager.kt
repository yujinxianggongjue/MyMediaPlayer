// VisualizerManager.kt
package com.example.mymediaplayer

import android.media.audiofx.Visualizer
import android.util.Log

/**
 * VisualizerManager 负责管理 Visualizer API 并将数据传递给 VisualizerListener。
 */
class VisualizerManager(private val audioSessionId: Int, private val listener: VisualizerListener) {

    private var visualizer: Visualizer? = null

    /**
     * 初始化 Visualizer
     */
    fun init() {
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1] // 设置捕获大小为最大值
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
                }, Visualizer.getMaxCaptureRate() / 2, true, true) // 开启波形和 FFT 数据捕获
                enabled = true
            }
            Log.d(TAG, "VisualizerManager 初始化成功。")
        } catch (e: Exception) {
            Log.e(TAG, "初始化 Visualizer 失败: ${e.message}", e)
        }
    }

    /**
     * 设置可视化效果类型
     * @param type 可视化类型
     */
    fun setVisualizerType(type: VisualizerType) {
        // 这里可以根据类型调整 Visualizer 的参数或处理方式
        // 目前的 Visualizer API 不直接支持不同类型的可视化，
        // 所以类型的切换更多地由 VisualizerView 控制
        Log.d(TAG, "设置可视化类型为: $type")
    }

    /**
     * 释放 Visualizer 资源
     */
    fun release() {
        try {
            visualizer?.release()
            visualizer = null
            Log.d(TAG, "VisualizerManager 资源已释放。")
        } catch (e: Exception) {
            Log.e(TAG, "释放 Visualizer 资源失败: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "VisualizerManager"
    }
}