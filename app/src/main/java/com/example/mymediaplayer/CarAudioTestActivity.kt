package com.example.mymediaplayer

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 车载音频测试Activity
 * 演示如何使用CarAudioVolumeManager和音量变化监听器
 * 
 * @author CarAudioVolumeManager
 * @since 1.0
 */
class CarAudioTestActivity : AppCompatActivity(), VolumeChangeListener {
    
    companion object {
        private const val TAG = "CarAudioTestActivity"
    }
    
    /**
     * 车载音频音量管理器实例
     */
    private lateinit var carAudioVolumeManager: CarAudioVolumeManager
    
    /**
     * 音量进度条UI组件
     */
    private lateinit var volumeProgressBar: ProgressBar
    
    /**
     * 音量显示文本UI组件
     */
    private lateinit var volumeTextView: TextView
    
    /**
     * 状态显示文本UI组件
     */
    private lateinit var statusTextView: TextView
    
    /**
     * 音频模式显示文本UI组件
     */
    private lateinit var audioModeTextView: TextView
    
    /**
     * 增加音量按钮
     */
    private lateinit var volumeUpButton: Button
    
    /**
     * 减少音量按钮
     */
    private lateinit var volumeDownButton: Button
    
    /**
     * 获取状态信息按钮
     */
    private lateinit var getStatusButton: Button
    
    /**
     * Activity创建时的初始化方法
     * 
     * @param savedInstanceState Bundle? 保存的实例状态
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_car_audio_test)
        
        // 初始化UI组件
        initViews()
        
        // 初始化车载音频管理器
        initCarAudioManager()
        
        // 设置按钮点击事件
        setupButtonListeners()
        
        // 更新初始状态
        updateStatus()
    }
    
    /**
     * 初始化UI组件
     */
    private fun initViews() {
        volumeProgressBar = findViewById(R.id.volumeProgressBar)
        volumeTextView = findViewById(R.id.volumeTextView)
        statusTextView = findViewById(R.id.statusTextView)
        audioModeTextView = findViewById(R.id.audioModeTextView)
        volumeUpButton = findViewById(R.id.volumeUpButton)
        volumeDownButton = findViewById(R.id.volumeDownButton)
        getStatusButton = findViewById(R.id.getStatusButton)
        
        // 设置进度条最大值
        volumeProgressBar.max = 100
    }
    
    /**
     * 初始化车载音频管理器
     */
    private fun initCarAudioManager() {
        try {
            carAudioVolumeManager = CarAudioVolumeManager(this)
            
            // 添加音量变化监听器
            carAudioVolumeManager.addVolumeChangeListener(this)
            
            // 初始化音频管理器
            val initResult = carAudioVolumeManager.initialize()
            Log.d(TAG, "车载音频管理器初始化结果: $initResult")
            
            if (initResult) {
                // 获取当前音量并更新UI
                val currentVolume = carAudioVolumeManager.getCurrentVolumePercent()
                val maxVolume = carAudioVolumeManager.getMaxVolume()
                onVolumeChanged(currentVolume, maxVolume, currentVolume, carAudioVolumeManager.isAvailable())
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "初始化车载音频管理器失败: ${e.message}", e)
            statusTextView.text = "初始化失败: ${e.message}"
        }
    }
    
    /**
     * 设置按钮点击事件监听器
     */
    private fun setupButtonListeners() {
        // 音量增加按钮
        volumeUpButton.setOnClickListener {
            val currentVolume = carAudioVolumeManager.getCurrentVolumePercent()
            val newVolume = (currentVolume + 10).coerceAtMost(100)
            carAudioVolumeManager.setGroupVolume(newVolume)
        }
        
        // 音量减少按钮
        volumeDownButton.setOnClickListener {
            val currentVolume = carAudioVolumeManager.getCurrentVolumePercent()
            val newVolume = (currentVolume - 10).coerceAtLeast(0)
            carAudioVolumeManager.setGroupVolume(newVolume)
        }
        
        // 获取状态信息按钮
        getStatusButton.setOnClickListener {
            updateStatus()
        }
    }
    
    /**
     * 更新状态显示
     */
    private fun updateStatus() {
        try {
            val statusInfo = carAudioVolumeManager.getStatusInfo()
            statusTextView.text = statusInfo
            
            val audioZoneInfo = carAudioVolumeManager.getAudioZoneInfo()
            Log.d(TAG, "音频区域信息: $audioZoneInfo")
            
        } catch (e: Exception) {
            Log.e(TAG, "更新状态失败: ${e.message}", e)
            statusTextView.text = "状态更新失败: ${e.message}"
        }
    }
    
    /**
     * 音量变化回调方法
     * 实现VolumeChangeListener接口
     * 
     * @param volumePercent Int 当前音量百分比 (0-100)
     * @param maxVolume Int 最大音量值
     * @param currentVolume Int 当前音量值
     * @param isCarAudio Boolean 是否为车载音频模式
     */
    override fun onVolumeChanged(
        volumePercent: Int,
        maxVolume: Int,
        currentVolume: Int,
        isCarAudio: Boolean
    ) {
        runOnUiThread {
            // 更新进度条
            volumeProgressBar.progress = volumePercent
            
            // 更新音量文本显示
            volumeTextView.text = "音量: $volumePercent% ($currentVolume/$maxVolume)"
            
            // 更新音频模式显示
            val modeText = if (isCarAudio) "车载音频模式" else "标准音频模式"
            audioModeTextView.text = "当前模式: $modeText"
            
            Log.d(TAG, "音量变化: $volumePercent%, 模式: $modeText")
        }
    }
    
    /**
     * 音量设置失败回调方法
     * 实现VolumeChangeListener接口
     * 
     * @param error String 错误信息
     * @param targetVolumePercent Int 目标音量百分比
     */
    override fun onVolumeSetFailed(error: String, targetVolumePercent: Int) {
        runOnUiThread {
            statusTextView.text = "音量设置失败: $error (目标音量: $targetVolumePercent%)"
            Log.e(TAG, "音量设置失败: $error, 目标音量: $targetVolumePercent%")
        }
    }
    
    /**
     * 音频模式变化回调方法
     * 实现VolumeChangeListener接口
     * 
     * @param isCarAudio Boolean 是否切换到车载音频模式
     * @param reason String 模式变化原因
     */
    override fun onAudioModeChanged(isCarAudio: Boolean, reason: String) {
        runOnUiThread {
            val modeText = if (isCarAudio) "车载音频模式" else "标准音频模式"
            audioModeTextView.text = "模式切换: $modeText (原因: $reason)"
            Log.d(TAG, "音频模式变化: $modeText, 原因: $reason")
        }
    }
    
    /**
     * Activity销毁时的清理方法
     */
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            // 移除音量变化监听器
            if (::carAudioVolumeManager.isInitialized) {
                carAudioVolumeManager.removeVolumeChangeListener(this)
                carAudioVolumeManager.release()
            }
            
            Log.d(TAG, "Activity销毁，资源清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理资源时发生异常: ${e.message}", e)
        }
    }
}