package com.example.mymediaplayer

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * SoundEffectManager 负责管理和应用各种音效效果，如均衡器、虚拟化器和低音增强。
 */
class SoundEffectManager(private val audioSessionId: Int) {

    private var equalizer: Equalizer? = null
    private var virtualizer: Virtualizer? = null
    private var bassBoost: BassBoost? = null

    init {
        try {
            // 初始化均衡器
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                // 设置默认预设为 "Flat"
                usePreset(0)
            }

            // 初始化虚拟化器（立体环绕）
            virtualizer = Virtualizer(0, audioSessionId).apply {
                setStrength(1000.toShort()) // 使用方法调用设置强度
                enabled = true
            }

            // 初始化低音增强
            bassBoost = BassBoost(0, audioSessionId).apply {
                setStrength(1000.toShort()) // 使用方法调用设置强度
                enabled = true
            }

            Log.d(TAG, "SoundEffectManager 初始化成功。")
        } catch (e: Exception) {
            Log.e(TAG, "初始化音效失败: ${e.message}", e)
        }
    }

    /**
     * 设置均衡器预设
     * @param presetIndex 预设索引
     */
    fun setEqualizerPreset(presetIndex: Short) {
        equalizer?.let {
            if (it.numberOfPresets > presetIndex) {
                it.usePreset(presetIndex)
                Log.d(TAG, "均衡器预设已设置为索引: $presetIndex")
            } else {
                Log.e(TAG, "无效的均衡器预设索引: $presetIndex")
            }
        }
    }

    /**
     * 启用或禁用虚拟化器（立体环绕）
     * @param enabled 是否启用
     */
    fun enableVirtualizer(enabled: Boolean) {
        virtualizer?.enabled = enabled
        Log.d(TAG, "虚拟化器已${if (enabled) "启用" else "禁用"}。")
    }

    /**
     * 启用或禁用低音增强
     * @param enabled 是否启用
     */
    fun enableBassBoost(enabled: Boolean) {
        bassBoost?.enabled = enabled
        Log.d(TAG, "低音增强已${if (enabled) "启用" else "禁用"}。")
    }

    /**
     * 释放音效资源
     */
    fun release() {
        try {
            equalizer?.release()
            virtualizer?.release()
            bassBoost?.release()
            Log.d(TAG, "SoundEffectManager 资源已释放。")
        } catch (e: Exception) {
            Log.e(TAG, "释放音效资源失败: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "SoundEffectManager"
    }
}