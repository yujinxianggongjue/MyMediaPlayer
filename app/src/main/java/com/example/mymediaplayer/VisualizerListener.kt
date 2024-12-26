package com.example.mymediaplayer

/**
 * VisualizerListener 接口用于接收来自 VisualizerManager 的可视化数据更新。
 */
interface VisualizerListener {
    /**
     * 当波形数据更新时调用。
     * @param waveform 包含波形数据的 ByteArray。
     */
    fun onWaveformUpdate(waveform: ByteArray?)

    /**
     * 当 FFT 数据更新时调用。
     * @param fft 包含 FFT 数据的 ByteArray。
     */
    fun onFftUpdate(fft: ByteArray?)
}