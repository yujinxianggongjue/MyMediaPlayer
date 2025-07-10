package com.example.mymediaplayer

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import com.example.mymediaplayer.AudioCaptureConfig
import com.example.mymediaplayer.AudioCaptureLogger
import com.example.mymediaplayer.AudioCapturePerformanceMonitor
import com.example.mymediaplayer.AudioCaptureErrorHandler
import com.example.mymediaplayer.AudioCaptureStrategy
import com.example.mymediaplayer.AudioCaptureStrategyManager

/**
 * MainActivity 是应用的主活动，负责播放、可视化等功能。
 * 演示如何整合 AudioPlaybackCapture 来录制系统播放的音频。
 */
@RequiresApi(Build.VERSION_CODES.Q)
class MainActivity : AppCompatActivity(),
    MediaPlayerListener,
    VisualizerListener,
    PermissionCallback {

    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 1
        private const val REQUEST_SCREEN_CAPTURE_CODE = 1003 // 用于申请MediaProjection
        private const val REQUEST_VIDEO_RECORD_CODE = 1000 // 录音权限请求码
        private const val REQUEST_STORAGE_PERMISSION_CODE = 4 // 存储权限请求码
        private const val TAG = "zqqtestMainActivity"
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnStopplay: Button
    private lateinit var btnOpenFile: Button
    private lateinit var btnSpeed: Button
    private lateinit var btnEffects: Button
    private lateinit var btnRecord: Button
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var visualizerView: VisualizerView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbumName: TextView
    private lateinit var ivAlbumCover: ImageView

    // 新增的 音量 SeekBar
    private lateinit var volumeSeekBar: SeekBar

    // 音效控件
    private lateinit var spinnerEqualizer: Spinner
    private lateinit var switchVirtualizer: Switch
    private lateinit var switchBassBoost: Switch
    private lateinit var radioGroupVisualizer: RadioGroup
    private lateinit var rbWaveform: RadioButton
    private lateinit var rbBarGraph: RadioButton
    private lateinit var rbLineGraph: RadioButton

    private var musicInfoDisplay: MusicInfoDisplay? = null
    private lateinit var handler: Handler

    private var currentFileUri: Uri? = null
    private var isVideo = true
    private var isFirstPlay = true
    private var isPaused = false

    // 定义可用的倍速列表
    private val playbackSpeeds = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)
    private var currentSpeedIndex = 1 // 默认播放速度索引为1（1.0x）

    private lateinit var mediaPlayerManager: MediaPlayerManager
    private lateinit var visualizerManager: VisualizerManager
    private lateinit var permissionManager: PermissionManager

    // 视频容器布局
    private lateinit var videoContainer: FrameLayout
    private lateinit var musicInfoLayout: LinearLayout

    // 音频捕获组件
    private lateinit var audioCaptureConfig: AudioCaptureConfig
    private lateinit var audioCaptureLogger: AudioCaptureLogger
    private lateinit var performanceMonitor: AudioCapturePerformanceMonitor
    private lateinit var errorHandler: AudioCaptureErrorHandler
    private lateinit var strategyManager: AudioCaptureStrategyManager

    // ====== 新增：AudioPlaybackCapture，用于捕获系统音频 ======
    // UI显示录音状态
    private lateinit var tvCapturePath: TextView
    private lateinit var btnAudioCapture: Button
    private var isAudioCaptureActive = false
    
    // 屏幕捕获权限结果数据
    private var currentResultCode: Int = 0
    private var resultData: Intent? = null
    
    // MediaProjection相关
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private lateinit var compatMediaProjectionManager: CompatMediaProjectionManager
    private var isCapturing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化控件
        initViews()
        handler = Handler(Looper.getMainLooper())

        // 初始化权限
        permissionManager = PermissionManager(this, this)
        permissionManager.checkAndRequestRecordAudioPermission()
        
        // 初始化MediaProjection
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        compatMediaProjectionManager = CompatMediaProjectionManager(this)
        
        // 初始化音频捕获组件
        initializeAudioCaptureComponents()

        // 初始化 MediaPlayer
        mediaPlayerManager = MediaPlayerManager(this, this)

        // SurfaceHolder 回调
        val surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                mediaPlayerManager.setDisplay(holder)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (mediaPlayerManager.isPlaying()) {
                    adjustVideoSize(mediaPlayerManager.getVideoWidth(), mediaPlayerManager.getVideoHeight())
                }
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                mediaPlayerManager.release()
            }
        })

        // ============ 播放控制按钮 ============
        btnPlay.setOnClickListener { playOrResume() }
        btnPause.setOnClickListener { pausePlayback() }
        btnStopplay.setOnClickListener { stopPlayback() }
        btnOpenFile.setOnClickListener { openFile() }
        btnSpeed.setOnClickListener { changePlaybackSpeed() }
        btnEffects.setOnClickListener { toggleSoundEffects() }

        // 录音按钮 -> 进入录音界面(若有)
        btnRecord.setOnClickListener {
            val intent = Intent(this, AudioRecoderTest::class.java)
            startActivity(intent)
        }

        // 新增AudioFocus测试按钮点击事件
        findViewById<Button>(R.id.btnAudioFocus).setOnClickListener {
            val intent = Intent(this, AudioFocusTestActivity::class.java)
            startActivity(intent)
        }

        // ============ 音量、均衡器、可视化等 ============
        initializeVolumeSeekBar()
        initSoundEffectControls()
        initVisualizerSelection()

        // ============ AudioPlaybackCapture 逻辑 ============
        // 1) 设置显示录音状态的UI
        tvCapturePath = findViewById(R.id.tvCapturePath)
        btnAudioCapture = findViewById(R.id.btnAudioCapture)

        // 2) 点击开始/停止录制
        btnAudioCapture.setOnClickListener {
            if (isCapturing) {
                stopSystemAudioCapture()
            } else {
                startSystemAudioCaptureCompat()
            }
        }
        
        // 3) 添加系统应用状态检查按钮
        findViewById<Button>(R.id.btnSystemCheck)?.setOnClickListener {
            performSystemAppDiagnostic()
        }
    }

    private fun initViews() {
        surfaceView = findViewById(R.id.surfaceView)
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnStopplay = findViewById(R.id.btnStopplay)
        btnOpenFile = findViewById(R.id.btnOpenFile)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnEffects = findViewById(R.id.btnEffects)
        btnRecord = findViewById(R.id.btnRecord)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        visualizerView = findViewById(R.id.visualizerView)
        tvArtist = findViewById(R.id.tvArtist)
        tvAlbumName = findViewById(R.id.tvAlbumName)
        ivAlbumCover = findViewById(R.id.ivAlbumCover)

        // 音效 & 可视化
        spinnerEqualizer = findViewById(R.id.spinnerEqualizer)
        switchVirtualizer = findViewById(R.id.switchVirtualizer)
        switchBassBoost = findViewById(R.id.switchBassBoost)
        radioGroupVisualizer = findViewById(R.id.radioGroupVisualizer)
        rbWaveform = findViewById(R.id.rbWaveform)
        rbBarGraph = findViewById(R.id.rbBarGraph)
        rbLineGraph = findViewById(R.id.rbLineGraph)

        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        videoContainer = findViewById(R.id.videoContainer)
        musicInfoLayout = findViewById(R.id.musicInfoLayout)

        musicInfoDisplay = MusicInfoDisplay(this, tvArtist, tvAlbumName, ivAlbumCover)
    }

    // ============ MediaProjection 申请 ============
    /**
     * 请求MediaProjection权限
     * 参考用户提供的Java代码逻辑，简化权限申请流程
     */
    private fun requestMediaProjection() {
        try {
            Log.d(TAG, "开始请求MediaProjection权限")

            // 检查是否已有权限结果
            if (currentResultCode == RESULT_OK && resultData != null) {
                Log.d(TAG, "已有MediaProjection权限，直接启动服务")
                startAudioCaptureService(currentResultCode, resultData)
                return
            }
            
            // 创建MediaProjection权限请求
            val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent()
            
            Log.d(TAG, "启动MediaProjection权限请求")
            startActivityForResult(screenCaptureIntent, REQUEST_SCREEN_CAPTURE_CODE)
            
        } catch (e: Exception) {
            Log.e(TAG, "请求MediaProjection权限失败", e)
            AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.PERMISSION, "MediaProjection权限请求失败: ${e.message}", mapOf("errorType" to "MediaProjectionRequestFailure"))
            Toast.makeText(this, "权限请求失败，使用降级方案", Toast.LENGTH_LONG).show()
            
            // 发生错误时使用降级方案
            startDirectAudioCapture()
        }
    }
    
    /**
     * 开始系统音频捕获（兼容版本）
     */
    /**
     * 启动兼容模式系统音频捕获
     * 参考用户提供的Java代码逻辑，简化权限检查流程
     */
    private fun startSystemAudioCaptureCompat() {
        Log.d(TAG, "启动兼容模式系统音频捕获")
        
        try {
            // 检查录音权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "请求录音权限")
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_VIDEO_RECORD_CODE)
                return
            }
            
            // 检查存储权限（如果需要）
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "请求存储权限")
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION_CODE)
                    return
                }
            }
            
            // 请求MediaProjection权限
            requestMediaProjection()
            
        } catch (e: Exception) {
            Log.e(TAG, "启动兼容模式音频捕获失败", e)
            AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, "启动音频捕获异常: ${e.message}", mapOf("errorType" to "CaptureException"))
            performanceMonitor.recordCaptureFailure("session_${System.currentTimeMillis()}", "Unknown", "EXCEPTION")
            
            val result = errorHandler.handleGenericError(e)
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show()
            
            if (result.hasFallback) {
                startDirectAudioCapture()
            }
        }
    }
    
    /**
     * 使用策略管理器启动音频捕获
     */
    private fun startSystemAudioCaptureWithStrategy() {
        Log.d(TAG, "使用策略管理器启动音频捕获")
        
        try {
            // 记录捕获尝试
            AudioCaptureLogger.info(AudioCaptureLogger.LogCategory.CAPTURE_ATTEMPT, "开始MediaProjection音频捕获尝试", mapOf("reason" to "MediaProjectionStart"))
            performanceMonitor.startCapture("MediaProjection")
            
            // 使用策略管理器启动音频捕获
            strategyManager.startCapture(
                onSuccess = { strategy ->
                    Log.d(TAG, "音频捕获启动成功，使用策略: ${strategy.javaClass.simpleName}")
                    AudioCaptureLogger.info(AudioCaptureLogger.LogCategory.CAPTURE_SUCCESS, "音频捕获成功: ${strategy.javaClass.simpleName}", mapOf("reason" to "CaptureSuccess"))
                    performanceMonitor.recordCaptureSuccess("session_${System.currentTimeMillis()}", strategy.javaClass.simpleName, 0L, 0L)
                    
                    isAudioCaptureActive = true
                    isCapturing = true
                    updateCaptureButtonText()
                    Toast.makeText(this, "音频捕获已启动", Toast.LENGTH_SHORT).show()
                },
                onError = { error ->
                    Log.w(TAG, "音频捕获启动失败: $error")
                    AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, "音频捕获失败: $error", mapOf("errorType" to "CaptureFailure"))
                    performanceMonitor.recordCaptureFailure("session_${System.currentTimeMillis()}", "Unknown", "CAPTURE_ERROR")
                    
                    // 使用错误处理器处理错误
                    val result = errorHandler.handleGenericError(Exception(error))
                    
                    if (result.retryRecommended) {
                        Toast.makeText(this, "正在重试...", Toast.LENGTH_SHORT).show()
                        // 延迟重试
                        handler.postDelayed({ startSystemAudioCaptureWithStrategy() }, 1000)
                    } else if (result.hasFallback) {
                        Toast.makeText(this, "使用降级方案", Toast.LENGTH_LONG).show()
                        startDirectAudioCapture()
                    } else {
                        Toast.makeText(this, "音频捕获失败: ${result.errorMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "使用策略管理器启动音频捕获失败", e)
            AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, "启动音频捕获异常: ${e.message}", mapOf("errorType" to "CaptureException"))
            performanceMonitor.recordCaptureFailure("session_${System.currentTimeMillis()}", "Unknown", "EXCEPTION")
            
            val result = errorHandler.handleGenericError(e)
            Toast.makeText(this, result.errorMessage, Toast.LENGTH_LONG).show()
            
            if (result.hasFallback) {
                startDirectAudioCapture()
            }
        }
    }
    
    /**
     * 检查基础权限
     */
    private fun checkBasicPermissions(): Boolean {
        val recordPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (recordPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_VIDEO_RECORD_CODE
            )
            return false
        }
        return true
    }
    
    /**
     * 直接音频捕获降级方案
     */
    fun startDirectAudioCapture() {
        Log.d(TAG, "启动直接音频捕获降级方案")
        
        try {
            // 记录降级方案使用
            AudioCaptureLogger.info(AudioCaptureLogger.LogCategory.CAPTURE_ATTEMPT, "使用直接音频捕获降级方案", mapOf("reason" to "FallbackStrategy"))
            performanceMonitor.recordStrategySwitch("Unknown", "DirectAudio", "FallbackStrategy")
            
            // 检查是否有RECORD_AUDIO权限
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "缺少RECORD_AUDIO权限")
                AudioCaptureLogger.info(AudioCaptureLogger.LogCategory.PERMISSION, "请求RECORD_AUDIO权限", mapOf("reason" to "PermissionRequest"))
                Toast.makeText(this, "需要录音权限", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 尝试直接启动AudioRecord
            startDirectAudioRecord()
            
        } catch (e: Exception) {
            Log.e(TAG, "直接音频捕获失败", e)
            AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, "直接音频捕获失败: ${e.message}", mapOf("errorType" to "DirectCaptureFailure"))
            Toast.makeText(this, "音频捕获失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 直接使用AudioRecord进行音频捕获
     */
    private fun startDirectAudioRecord() {
        try {
            val audioFormat = android.media.AudioFormat.Builder()
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(android.media.AudioFormat.CHANNEL_IN_STEREO)
                .build()
            
            val audioRecord = android.media.AudioRecord.Builder()
                .setAudioSource(android.media.MediaRecorder.AudioSource.DEFAULT)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(android.media.AudioRecord.getMinBufferSize(
                    44100,
                    android.media.AudioFormat.CHANNEL_IN_STEREO,
                    android.media.AudioFormat.ENCODING_PCM_16BIT
                ) * 2)
                .build()
            
            if (audioRecord.state == android.media.AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording()
                isCapturing = true
                updateCaptureButtonText()
                
                Toast.makeText(this, "直接音频捕获已启动", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "直接音频捕获启动成功")
                
                // 在后台线程中处理音频数据
                Thread {
                    processDirectAudioData(audioRecord)
                }.start()
                
            } else {
                Log.e(TAG, "AudioRecord初始化失败")
                Toast.makeText(this, "音频录制器初始化失败", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "创建AudioRecord失败", e)
            Toast.makeText(this, "创建音频录制器失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 处理直接音频数据
     */
    private fun processDirectAudioData(audioRecord: android.media.AudioRecord) {
        val buffer = ShortArray(1024)
        
        while (isCapturing && audioRecord.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
            try {
                val readSize = audioRecord.read(buffer, 0, buffer.size)
                if (readSize > 0) {
                    // 处理音频数据（这里可以添加音频处理逻辑）
                    processAudioBuffer(buffer, readSize)
                }
            } catch (e: Exception) {
                Log.e(TAG, "读取音频数据失败", e)
                break
            }
        }
        
        // 清理资源
        try {
            audioRecord.stop()
            audioRecord.release()
            Log.d(TAG, "AudioRecord资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放AudioRecord资源失败", e)
        }
    }
    
    /**
     * 处理音频缓冲区数据
     */
    private fun processAudioBuffer(buffer: ShortArray, size: Int) {
        // 这里可以添加音频数据处理逻辑
        // 例如：音频可视化、音频分析等
        
        // 计算音频强度用于可视化
        var sum = 0L
        for (i in 0 until size) {
            sum += (buffer[i] * buffer[i]).toLong()
        }
        val rms = kotlin.math.sqrt(sum.toDouble() / size)
        
        // 更新UI（需要在主线程中执行）
        runOnUiThread {
            // 这里可以更新音频可视化UI
            Log.v(TAG, "音频强度: $rms")
        }
    }
    
    /**
     * 更新捕获按钮文本
     */
    private fun updateCaptureButtonText() {
        btnAudioCapture.text = if (isCapturing) "停止捕获" else "开始音频捕获"
    }
    
    /**
     * 显示权限诊断对话框
     */
    private fun showPermissionDiagnosticDialog(diagnosticInfo: String, solutions: String, status: PermissionStatus) {
        val message = "$diagnosticInfo\n\n$solutions"
        
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle("音频捕获权限诊断")
        builder.setMessage(message)
        
        // 如果至少有基础录音权限，提供继续尝试的选项
        if (status.canRecordMicrophone()) {
            builder.setPositiveButton("仍要尝试") { _, _ ->
                Log.i(TAG, "用户选择继续尝试音频捕获")
                requestMediaProjection()
            }
        }
        
        builder.setNegativeButton("取消", null)
        builder.show()
    }

    // 重复方法已删除，使用后面的改进版本

    // 权限回调
    override fun onPermissionGranted() {
        initializeMediaPlayer()
    }
    override fun onPermissionDenied() {
        btnEffects.isEnabled = false
        spinnerEqualizer.isEnabled = false
        switchVirtualizer.isEnabled = false
        switchBassBoost.isEnabled = false
    }

    private fun initializeMediaPlayer() {
        currentFileUri = Uri.parse("android.resource://${packageName}/${R.raw.sample_audio}")
        isVideo = false
        musicInfoDisplay?.displayMusicInfo(currentFileUri!!)
        musicInfoDisplay?.toggleMusicInfo(true)
    }

    // ============ 播放控制 ============
    private fun playOrResume() {
        Log.d(TAG, "开始播放或恢复播放")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "开始播放或恢复播放 - 首次播放: $isFirstPlay, 暂停状态: $isPaused"
        )
        
        if (isFirstPlay) {
            Log.d(TAG, "首次播放，初始化媒体播放器")
            currentFileUri?.let {
                mediaPlayerManager.initMediaPlayer(it, isVideo)
                isFirstPlay = false
                Log.d(TAG, "媒体播放器初始化完成")
            }
        } else if (isPaused) {
            Log.d(TAG, "从暂停状态恢复播放")
            mediaPlayerManager.play()
            isPaused = false
            updateSeekBar()
        } else {
            Log.d(TAG, "开始播放")
            mediaPlayerManager.play()
            updateSeekBar()
        }
        
        Log.d(TAG, "设置循环播放")
        mediaPlayerManager.setLooping(true)
        
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "播放操作完成"
        )
    }

    private fun pausePlayback() {
        Log.d(TAG, "开始暂停播放")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "开始暂停播放"
        )
        
        mediaPlayerManager.pause()
        isPaused = true
        
        Log.d(TAG, "播放已暂停")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "播放已暂停"
        )
    }

    private fun stopPlayback() {
        Log.d(TAG, "开始停止播放")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "开始停止播放"
        )
        
        Log.d(TAG, "停止媒体播放器")
        mediaPlayerManager.stop()
        isPaused = false
        
        Log.d(TAG, "释放可视化器资源")
        visualizerManager.release()
        
        Log.d(TAG, "重置UI状态")
        seekBar.progress = 0
        tvCurrentTime.text = formatTime(0)
        isFirstPlay = true
        
        Log.d(TAG, "播放停止完成")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "播放停止完成"
        )
    }

    private fun changePlaybackSpeed() {
        Log.d(TAG, "开始改变播放速度")
        currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.size
        val newSpeed = playbackSpeeds[currentSpeedIndex]
        
        Log.d(TAG, "设置播放速度为: ${newSpeed}x")
        mediaPlayerManager.setPlaybackSpeed(newSpeed)
        updateSpeedButtonText()
        
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "播放速度已改变为: ${newSpeed}x"
        )
        
        Toast.makeText(this, "速度: ${newSpeed}x", Toast.LENGTH_SHORT).show()
    }

    private fun updateSpeedButtonText() {
        btnSpeed.text = "速度: ${playbackSpeeds[currentSpeedIndex]}x"
    }

    private fun toggleSoundEffects() {
        val soundEffectsLayout: LinearLayout = findViewById(R.id.soundEffectsLayout)
        if (soundEffectsLayout.visibility == View.GONE) {
            soundEffectsLayout.visibility = View.VISIBLE
            btnEffects.text = "Hide Effects"
        } else {
            soundEffectsLayout.visibility = View.GONE
            btnEffects.text = "Effects"
        }
    }

    private fun openFile() {
        Log.d(TAG, "开始打开文件选择器")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "开始打开文件选择器"
        )
        
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        
        Log.d(TAG, "启动文件选择Activity")
        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE)
    }

    // ============ 音量控制 =============
    private fun initializeVolumeSeekBar() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = if (maxVolume == 0) 0 else (currentVolume * 100) / maxVolume

        volumeSeekBar.max = 100
        volumeSeekBar.progress = volumePercent
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) setAppVolume(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun setAppVolume(volumePercent: Int) {
        Log.d(TAG, "设置应用音量: $volumePercent%")
        val volume = volumePercent / 100f
        
        mediaPlayerManager.setVolume(volume)
        
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "应用音量已设置为: $volumePercent% (${volume})"
        )
    }

    // ============ 均衡器 & 可视化 =============
    private fun initSoundEffectControls() {
        val equalizerPresets = getEqualizerPresets()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, equalizerPresets)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEqualizer.adapter = adapter

        spinnerEqualizer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                mediaPlayerManager.setEqualizerPreset(position.toShort())
                Log.d(TAG, "均衡器预设选择: $position")
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        switchVirtualizer.setOnCheckedChangeListener { _, isChecked ->
            mediaPlayerManager.enableVirtualizer(isChecked)
            Log.d(TAG, "虚拟化器已${if (isChecked) "启用" else "禁用"}。")
        }

        switchBassBoost.setOnCheckedChangeListener { _, isChecked ->
            mediaPlayerManager.enableBassBoost(isChecked)
            Log.d(TAG, "低音增强已${if (isChecked) "启用" else "禁用"}。")
        }
    }

    private fun initVisualizerSelection() {
        radioGroupVisualizer.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbWaveform -> {
                    visualizerManager.setVisualizerType(VisualizerType.WAVEFORM)
                    visualizerView.setVisualizerType(VisualizerType.WAVEFORM)
                    Log.d(TAG, "Visualizer 类型切换为: WAVEFORM")
                }
                R.id.rbBarGraph -> {
                    visualizerManager.setVisualizerType(VisualizerType.BAR_GRAPH)
                    visualizerView.setVisualizerType(VisualizerType.BAR_GRAPH)
                    Log.d(TAG, "Visualizer 类型切换为: BAR_GRAPH")
                }
                R.id.rbLineGraph -> {
                    visualizerManager.setVisualizerType(VisualizerType.LINE_GRAPH)
                    visualizerView.setVisualizerType(VisualizerType.LINE_GRAPH)
                    Log.d(TAG, "Visualizer 类型切换为: LINE_GRAPH")
                }
            }
        }
    }

    private fun getEqualizerPresets(): List<String> {
        return listOf("Flat","Bass Boost","Rock","Pop","Jazz","Classical","Dance","Hip Hop")
    }

    // ============ 媒体回调 ============
    override fun onPrepared(duration: Int, isVideo: Boolean, videoWidth: Int, videoHeight: Int) {
        seekBar.max = duration
        tvTotalTime.text = formatTime(duration)
        mediaPlayerManager.setPlaybackSpeed(playbackSpeeds[currentSpeedIndex])

        if (isVideo) {
            adjustVideoSize(videoWidth, videoHeight)
            musicInfoDisplay?.toggleMusicInfo(false)
        } else {
            musicInfoDisplay?.displayMusicInfo(currentFileUri!!)
            musicInfoDisplay?.toggleMusicInfo(true)
        }

        // 初始化 VisualizerManager
        visualizerManager = VisualizerManager(mediaPlayerManager.getAudioSessionId(), this)
        visualizerManager.init()

        // 初始化音效
        mediaPlayerManager.initSoundEffects()

        // 默认可视化类型
        visualizerManager.setVisualizerType(VisualizerType.WAVEFORM)

        updateSeekBar()
    }

    override fun onCompletion() {
        Log.d(TAG, "onCompletion")
        Toast.makeText(this, "播放完成", Toast.LENGTH_SHORT).show()
        mediaPlayerManager.seekTo(0)
        isPaused = false
    }

    override fun onWaveformUpdate(waveform: ByteArray?) {
        visualizerView.updateWaveform(waveform)
    }

    override fun onFftUpdate(fft: ByteArray?) {
        visualizerView.updateFft(fft)
    }

    // ============ 音频捕获组件初始化 ============
    private fun initializeAudioCaptureComponents() {
        try {
            Log.d(TAG, "开始初始化音频捕获组件")
            
            // 初始化配置管理器
            Log.d(TAG, "初始化音频捕获配置管理器")
            audioCaptureConfig = AudioCaptureConfig(this)
            Log.d(TAG, "配置管理器初始化完成 - 缓冲区大小: ${audioCaptureConfig.getAudioSettings().calculateBufferSize()}")
            
            // 初始化日志系统
            Log.d(TAG, "初始化音频捕获日志系统")
            AudioCaptureLogger.initialize(this, true)
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "音频捕获组件初始化开始"
            )
            
            // 初始化性能监控
            Log.d(TAG, "初始化性能监控器")
            performanceMonitor = AudioCapturePerformanceMonitor()
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.PERFORMANCE,
                "性能监控器初始化完成"
            )
            
            // 初始化错误处理器
            Log.d(TAG, "初始化错误处理器")
            errorHandler = AudioCaptureErrorHandler(this)
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "错误处理器初始化完成"
            )
            
            // 初始化策略管理器
            Log.d(TAG, "初始化音频捕获策略管理器")
            strategyManager = AudioCaptureStrategyManager(
                context = this,
                config = audioCaptureConfig,
                performanceMonitor = performanceMonitor
            )
            
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "音频捕获组件初始化完成 - 所有组件已就绪"
            )
            Log.d(TAG, "音频捕获组件初始化完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "音频捕获组件初始化失败", e)
            Toast.makeText(this, "音频捕获组件初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    // ============ 辅助 ============
    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateSeekBar() {
        handler.postDelayed({
            if (mediaPlayerManager.isPlaying()) {
                val currentPosition = mediaPlayerManager.getCurrentPosition()
                seekBar.progress = currentPosition
                tvCurrentTime.text = formatTime(currentPosition)
                updateSeekBar()
            }
        }, 500)
    }

    fun adjustVideoSize(videoWidth: Int, videoHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0) return
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val videoAspectRatio = videoWidth.toFloat() / videoHeight
        val screenAspectRatio = screenWidth.toFloat() / screenHeight
        val (newWidth, newHeight) = if (videoAspectRatio > screenAspectRatio) {
            screenWidth to (screenWidth / videoAspectRatio).toInt()
        } else {
            (screenHeight * videoAspectRatio).toInt() to screenHeight
        }
        surfaceView.layoutParams = FrameLayout.LayoutParams(newWidth, newHeight, Gravity.CENTER)
    }

    // ============ onActivityResult 处理 ============
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_OPEN_FILE -> {
                if (resultCode == RESULT_OK && data != null) {
                    val uri = data.data
                    if (uri != null) {
                        try {
                            currentFileUri = uri
                            isVideo = isVideoFile(uri)
                            mediaPlayerManager.initMediaPlayer(uri, isVideo)
                            if (!isVideo) {
                                musicInfoDisplay?.displayMusicInfo(uri)
                                musicInfoDisplay?.toggleMusicInfo(true)
                            } else {
                                musicInfoDisplay?.toggleMusicInfo(false)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error initializing MediaPlayer: ${e.message}", e)
                            Toast.makeText(this, "无法播放所选文件", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.e(TAG, "File URI is null!")
                    }
                }
            }

            REQUEST_SCREEN_CAPTURE_CODE -> {
                handleMediaProjectionResult(resultCode, data)
            }
        }
    }
    
    /**
     * 处理MediaProjection权限结果
     * 参考用户提供的Java代码逻辑，简化权限处理流程
     */
    private fun handleMediaProjectionResult(resultCode: Int, data: Intent?) {
        try {
            Log.d(TAG, "处理MediaProjection权限结果 - resultCode: $resultCode, data: ${data != null}")
            
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "MediaProjection权限获取成功")
                
                // 保存权限结果
                currentResultCode = resultCode
                resultData = data
                
                // 启动音频捕获服务
                startAudioCaptureService(resultCode, data)
                
                Toast.makeText(this, "屏幕录制权限已获取，开始音频捕获", Toast.LENGTH_SHORT).show()
                
            } else {
                Log.w(TAG, "MediaProjection权限被拒绝")
                Toast.makeText(this, "屏幕录制权限被拒绝，使用降级方案", Toast.LENGTH_LONG).show()
                
                // 权限被拒绝时使用降级方案
                startDirectAudioCapture()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理MediaProjection结果时发生错误", e)
            Toast.makeText(this, "权限处理失败，使用降级方案", Toast.LENGTH_LONG).show()
            
            // 发生错误时使用降级方案
            startDirectAudioCapture()
        }
    }
    
    /**
     * 启动音频捕获服务
     */
    private fun startAudioCaptureService(resultCode: Int, data: Intent?) {
        try {
            val outputPath = getExternalFilesDir(null)?.absolutePath + "/captured_audio.wav"
            
            // 记录服务启动详情
            Log.d(TAG, "准备启动音频捕获服务 - outputPath: $outputPath")
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "启动音频捕获服务 - 输出路径: $outputPath"
            )
            
            // 记录性能监控开始
            if (::performanceMonitor.isInitialized) {
                performanceMonitor.startCapture("AudioCaptureService")
            }
            
            val serviceIntent = Intent(this, AudioCaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
                putExtra("outputPath", outputPath)
            }
            
            startForegroundService(serviceIntent)
            Log.d(TAG, "音频捕获服务已启动")
            
            isAudioCaptureActive = true
            isCapturing = true
            updateCaptureButtonText()
            
            // 记录成功启动
            AudioCaptureLogger.logCaptureAttempt("AudioCaptureService", mapOf("outputPath" to outputPath))
            
            Toast.makeText(this, "音频捕获已启动", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "启动音频捕获服务失败", e)
            AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.CAPTURE_ERROR, "启动音频捕获服务失败: ${e.message}", mapOf("errorType" to "ServiceStartFailure"))
            
            // 记录失败
            if (::performanceMonitor.isInitialized) {
                performanceMonitor.recordCaptureFailure("service_start_${System.currentTimeMillis()}", "AudioCaptureService", e.message ?: "未知错误")
            }
            
            Toast.makeText(this, "启动服务失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isVideoFile(uri: Uri): Boolean {
        val cr: ContentResolver = contentResolver
        val type = cr.getType(uri)
        return type != null && type.startsWith("video")
    }

    // ============ 权限请求结果 ============
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // 记录权限请求结果
        Log.d(TAG, "权限请求结果 - requestCode: $requestCode, permissions: ${permissions.joinToString(", ")}, results: ${grantResults.joinToString(", ")}")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "权限请求结果 - 请求码: $requestCode, 权限: ${permissions.joinToString(", ")}",
            mapOf("reason" to "PermissionResult")
        )
        
        when (requestCode) {
            REQUEST_VIDEO_RECORD_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "录音权限已授予")
                    AudioCaptureLogger.info(
                        AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                        "录音权限已授予",
                        mapOf("reason" to "PermissionGranted")
                    )
                    Toast.makeText(this, "录音权限已开启", Toast.LENGTH_LONG).show()
                } else {
                    Log.w(TAG, "录音权限被拒绝")
                    AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.PERMISSION, "录音权限被拒绝", mapOf("errorType" to "PermissionDenied"))
                    Toast.makeText(this, "录音权限开启失败", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "存储权限已授予")
                    AudioCaptureLogger.info(
                        AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                        "存储权限已授予",
                        mapOf("reason" to "PermissionGranted")
                    )
                    Toast.makeText(this, "存储权限已开启", Toast.LENGTH_LONG).show()
                } else {
                    Log.w(TAG, "存储权限被拒绝")
                    AudioCaptureLogger.error(AudioCaptureLogger.LogCategory.PERMISSION, "存储权限被拒绝", mapOf("errorType" to "PermissionDenied"))
                    Toast.makeText(this, "存储权限开启失败", Toast.LENGTH_LONG).show()
                }
            }
            else -> {
                // 处理其他权限请求
                Log.d(TAG, "处理其他权限请求 - requestCode: $requestCode")
                permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    // ============ 系统应用诊断 ============
    private fun performSystemAppDiagnostic() {
        try {
            Log.d(TAG, "开始执行系统应用诊断")
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "开始执行系统应用诊断",
                mapOf("reason" to "DiagnosticStart")
            )
            
            // 生成完整的系统报告
            Log.d(TAG, "生成系统应用状态报告")
            val status = SystemAppChecker.performFullCheck(this)
            val fullReport = SystemAppChecker.getDiagnosticAdvice(status)
            
            // 记录诊断结果
            Log.d(TAG, "系统应用诊断完成 - 报告长度: ${fullReport.length}")
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "系统应用诊断完成 - 系统应用: ${status.isSystemApp}, 权限状态: ${status.hasCaptureAudioOutputPermission}",
                mapOf("reason" to "DiagnosticComplete")
            )
            
            // 显示诊断结果
            android.app.AlertDialog.Builder(this)
                .setTitle("系统应用完整诊断报告")
                .setMessage(fullReport)
                .setPositiveButton("确定", null)
                .setNeutralButton("复制报告") { _, _ ->
                    Log.d(TAG, "复制诊断报告到剪贴板")
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("系统应用诊断报告", fullReport)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "报告已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("查看MTK配置") { _, _ ->
                    Log.d(TAG, "查看MTK配置详情")
                    showMtkConfigDetails()
                }
                .show()
                
        } catch (e: Exception) {
            Log.e(TAG, "系统应用诊断失败", e)
            AudioCaptureLogger.error(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "系统应用诊断失败: ${e.message}",
                mapOf("errorType" to "DiagnosticFailure")
            )
            Toast.makeText(this, "诊断失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 显示MTK车载系统配置详情
     */
    private fun showMtkConfigDetails() {
        try {
            Log.d(TAG, "开始获取MTK车载系统配置")
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "开始获取MTK车载系统配置",
                mapOf("reason" to "MtkConfigStart")
            )
            
            val mtkConfig = SystemAppChecker.checkMtkCarSystemConfig(this)
            
            Log.d(TAG, "MTK配置获取完成 - 配置长度: ${mtkConfig.length}")
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "MTK配置获取完成",
                mapOf("reason" to "MtkConfigComplete")
            )
            
            android.app.AlertDialog.Builder(this)
                .setTitle("MTK车载系统配置详情")
                .setMessage(mtkConfig)
                .setPositiveButton("确定", null)
                .setNeutralButton("复制配置") { _, _ ->
                    Log.d(TAG, "复制MTK配置到剪贴板")
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("MTK配置信息", mtkConfig)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "配置信息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                .show()
                
        } catch (e: Exception) {
            Log.e(TAG, "获取MTK配置失败", e)
            AudioCaptureLogger.error(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "获取MTK配置失败: ${e.message}",
                mapOf("errorType" to "MtkConfigFailure")
            )
            Toast.makeText(this, "获取MTK配置失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showDiagnosticDialog(advice: String) {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("系统应用状态诊断")
            .setMessage(advice)
            .setPositiveButton("确定") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("查看日志") { _, _ ->
                Toast.makeText(this, "请查看Logcat中的详细日志", Toast.LENGTH_LONG).show()
            }
            .create()
        dialog.show()
    }
    
    private fun updateSystemAppStatusUI(status: SystemAppStatus) {
        Log.d(TAG, "开始更新系统应用状态UI")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "开始更新系统应用状态UI - 系统应用: ${status.isSystemApp}, 音频权限: ${status.hasCaptureAudioOutputPermission}",
            mapOf("reason" to "UIUpdate")
        )
        
        // 更新音频捕获按钮的状态和文本
        if (status.isSystemApp && status.hasCaptureAudioOutputPermission) {
            Log.d(TAG, "系统应用配置正常，启用音频捕获按钮")
            btnAudioCapture.text = "开始音频捕获"
            btnAudioCapture.isEnabled = true
        } else {
            Log.w(TAG, "系统应用配置异常，禁用音频捕获按钮")
            btnAudioCapture.text = "系统应用配置异常"
            btnAudioCapture.isEnabled = false
        }
        
        // 更新状态显示
        val statusText = if (status.isSystemApp) {
            "✓ 系统应用 | ${if (status.hasCaptureAudioOutputPermission) "✓" else "✗"} 音频权限"
        } else {
            "✗ 非系统应用 | 位置: ${status.installLocation}"
        }
        
        Log.d(TAG, "更新状态文本: $statusText")
        tvCapturePath.text = statusText
        
        Log.d(TAG, "系统应用状态UI更新完成")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "系统应用状态UI更新完成 - 状态: $statusText",
            mapOf("reason" to "UIUpdateComplete")
        )
    }
    
    // ============ 改进的权限检查 ============
    private fun checkAndRequestPermissions() {
        Log.d(TAG, "开始权限检查流程")
        
        // 记录权限检查开始
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "开始权限检查流程",
            mapOf("reason" to "PermissionCheckStart")
        )
        
        // 首先进行系统应用状态检查
        Log.d(TAG, "执行系统应用状态检查")
        val systemStatus = SystemAppChecker.performFullCheck(this)
        
        // 记录系统应用检查结果
        Log.d(TAG, "系统应用检查结果 - isSystemApp: ${systemStatus.isSystemApp}, hasCaptureAudioOutputPermission: ${systemStatus.hasCaptureAudioOutputPermission}")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "系统应用检查结果 - 系统应用: ${systemStatus.isSystemApp}, 音频权限: ${systemStatus.hasCaptureAudioOutputPermission}"
        )
        
        if (!systemStatus.isSystemApp) {
            // 如果不是系统应用，显示警告并提供诊断
            Log.w(TAG, "应用未被识别为系统应用，安装位置: ${systemStatus.installLocation}")
            AudioCaptureLogger.error(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "应用未被识别为系统应用 - 安装位置: ${systemStatus.installLocation}"
            )
            
            val message = "警告：应用未被识别为系统应用\n" +
                    "这将导致无法获取CAPTURE_AUDIO_OUTPUT权限\n\n" +
                    "是否查看详细诊断信息？"
            
            android.app.AlertDialog.Builder(this)
                .setTitle("系统应用配置问题")
                .setMessage(message)
                .setPositiveButton("查看诊断") { _: android.content.DialogInterface, _: Int -> performSystemAppDiagnostic() }
                .setNegativeButton("继续尝试") { _: android.content.DialogInterface, _: Int -> proceedWithPermissionCheck() }
                .show()
            return
        }
        
        if (!systemStatus.hasCaptureAudioOutputPermission) {
            Log.w(TAG, "系统应用但CAPTURE_AUDIO_OUTPUT权限未授予")
            AudioCaptureLogger.error(
                AudioCaptureLogger.LogCategory.PERMISSION,
                "系统应用但缺少CAPTURE_AUDIO_OUTPUT权限"
            )
            Toast.makeText(this, "系统应用但缺少音频捕获权限，请检查SELinux策略", Toast.LENGTH_LONG).show()
        }
        
        // 继续常规权限检查
        Log.d(TAG, "系统应用检查通过，继续常规权限检查")
        proceedWithPermissionCheck()
    }
    
    private fun proceedWithPermissionCheck() {
        Log.d(TAG, "开始常规权限检查")
        
        val recordPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        // 记录权限检查结果
        Log.d(TAG, "权限检查结果 - RECORD_AUDIO: ${recordPermission == PackageManager.PERMISSION_GRANTED}, STORAGE: ${storagePermission == PackageManager.PERMISSION_GRANTED}")
        AudioCaptureLogger.info(
            AudioCaptureLogger.LogCategory.SYSTEM_INFO,
            "权限检查结果 - 录音权限: ${recordPermission == PackageManager.PERMISSION_GRANTED}, 存储权限: ${storagePermission == PackageManager.PERMISSION_GRANTED}"
        )
        
        val permissionsToRequest = mutableListOf<String>()
        
        if (recordPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            Log.d(TAG, "需要请求RECORD_AUDIO权限")
        }
        
        if (storagePermission != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                Log.d(TAG, "需要请求READ_MEDIA_AUDIO权限")
            } else {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                Log.d(TAG, "需要请求WRITE_EXTERNAL_STORAGE权限")
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "请求权限: ${permissionsToRequest.joinToString(", ")}")
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "请求权限: ${permissionsToRequest.joinToString(", ")}"
            )
            
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                if (permissionsToRequest.contains(Manifest.permission.RECORD_AUDIO)) 
                    REQUEST_VIDEO_RECORD_CODE else REQUEST_STORAGE_PERMISSION_CODE
            )
        } else {
            // 所有权限已授予，继续执行
            Log.d(TAG, "所有权限已授予，继续执行MediaProjection权限检查")
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "所有权限已授予，继续执行MediaProjection权限检查"
            )
            performPermissionCheck()
        }
    }
    
    // ============ 音频捕获控制 ============
    private fun stopSystemAudioCapture() {
        Log.d(TAG, "开始停止系统音频捕获")
        
        try {
            // 记录停止操作开始
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.CAPTURE_ATTEMPT,
                "开始停止系统音频捕获"
            )
            
            // 停止策略管理器
            if (::strategyManager.isInitialized) {
                Log.d(TAG, "停止策略管理器")
                strategyManager.stopCapture()
                AudioCaptureLogger.info(
                    AudioCaptureLogger.LogCategory.CAPTURE_SUCCESS,
                    "策略管理器已停止"
                )
            } else {
                Log.w(TAG, "策略管理器未初始化")
            }
            
            // 停止服务
            Log.d(TAG, "停止音频捕获服务")
            val serviceIntent = Intent(this, AudioCaptureService::class.java)
            stopService(serviceIntent)
            
            // 记录性能数据
            if (::performanceMonitor.isInitialized) {
                Log.d(TAG, "记录捕获停止性能数据")
                performanceMonitor.recordCaptureStop()
                AudioCaptureLogger.info(
                    AudioCaptureLogger.LogCategory.PERFORMANCE,
                    "捕获停止性能数据已记录"
                )
            } else {
                Log.w(TAG, "性能监控器未初始化")
            }
            
            // 更新状态
            Log.d(TAG, "更新捕获状态")
            isAudioCaptureActive = false
            isCapturing = false
            updateCaptureButtonText()
            
            Log.d(TAG, "系统音频捕获停止完成")
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.CAPTURE_SUCCESS,
                "系统音频捕获停止完成"
            )
            
            Toast.makeText(this, "已停止音频捕获", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "停止音频捕获时发生错误", e)
            AudioCaptureLogger.error(
                AudioCaptureLogger.LogCategory.CAPTURE_ERROR,
                "停止音频捕获失败: ${e.message}"
            )
        }
    }
    
    private fun performPermissionCheck() {
        Log.d(TAG, "执行MediaProjection权限检查")
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_SCREEN_CAPTURE_CODE)
    }

    // ============ 内存管理优化 ============
    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        
        try {
            // 清理音频捕获资源
            cleanupAudioCaptureResources()
            
            // 释放媒体播放器资源
            mediaPlayerManager.release()
            
            // 释放可视化器资源
            if(::visualizerManager.isInitialized) {
                visualizerManager.release()
            }
            
            // 清理处理器回调
            handler.removeCallbacksAndMessages(null)
            
            // 记录生命周期事件
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "Activity onDestroy"
            )
            // 清理日志缓存
            AudioCaptureLogger.clearOldLogs()
            
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy清理资源时发生错误", e)
        }
        
        super.onDestroy()
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "系统内存不足")
        
        try {
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "系统内存不足，执行内存清理"
            )
            
            // 记录内存使用情况
            if (::performanceMonitor.isInitialized) {
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                AudioCaptureLogger.logMemory("低内存状态", mapOf(
                    "used_memory_mb" to (usedMemory / (1024 * 1024)),
                    "max_memory_mb" to (runtime.maxMemory() / (1024 * 1024))
                ))
            }
            
            // 动态调整音频配置以应对低内存
            if (::audioCaptureConfig.isInitialized) {
                audioCaptureConfig.dynamicAdjustConfig(0, true)
            }
            
            // 清理非必要资源
            cleanupNonEssentialResources()
            
        } catch (e: Exception) {
            Log.e(TAG, "处理低内存情况时发生错误", e)
        }
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "内存修剪级别: $level")
        
        try {
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "内存修剪级别: $level"
            )
            
            when (level) {
                TRIM_MEMORY_RUNNING_MODERATE,
                TRIM_MEMORY_RUNNING_LOW,
                TRIM_MEMORY_RUNNING_CRITICAL -> {
                    // 应用在前台运行但内存不足
                    // 动态调整配置以应对内存压力
                    if (::audioCaptureConfig.isInitialized) {
                        audioCaptureConfig.dynamicAdjustConfig(1, true) // 有性能问题
                    }
                }
                TRIM_MEMORY_UI_HIDDEN -> {
                    // UI不可见，可以释放更多资源
                    cleanupNonEssentialResources()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理内存修剪时发生错误", e)
        }
    }
    
    /**
     * 清理音频捕获相关资源
     */
    private fun cleanupAudioCaptureResources() {
        try {
            // 停止音频捕获
            if (isAudioCaptureActive) {
                stopSystemAudioCapture()
            }
            
            // 清理策略管理器
            if (::strategyManager.isInitialized) {
                strategyManager.cleanup()
            }
            
            // 清理性能监控
            if (::performanceMonitor.isInitialized) {
                // 生成最终性能报告并记录
                val finalReport = performanceMonitor.generatePerformanceReport()
                AudioCaptureLogger.info(
                    AudioCaptureLogger.LogCategory.PERFORMANCE,
                    "最终性能报告",
                    mapOf("report" to finalReport)
                )
                performanceMonitor.reset()
            }
            
            // 释放MediaProjection资源
            mediaProjection?.stop()
            mediaProjection = null
            
            // 记录清理完成
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "音频捕获资源清理完成"
            )
            
            Log.d(TAG, "音频捕获资源清理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "清理音频捕获资源时发生错误", e)
        }
    }
    
    /**
     * 清理非必要资源（用于内存优化）
     */
    private fun cleanupNonEssentialResources() {
        try {
            // 清理性能监控历史数据
            if (::performanceMonitor.isInitialized) {
                // 保留最近的性能数据，清理较旧的历史记录
                val currentStats = performanceMonitor.getPerformanceStats()
                AudioCaptureLogger.info(
                    AudioCaptureLogger.LogCategory.PERFORMANCE,
                    "清理前性能统计: 总捕获${currentStats.totalCaptures}次"
                )
                performanceMonitor.clearHistory()
            }
            
            // 清理日志缓存（如果有的话）
            AudioCaptureLogger.info(
                AudioCaptureLogger.LogCategory.SYSTEM_INFO,
                "执行非必要资源清理"
            )
            AudioCaptureLogger.clearOldLogs()
            
            // 强制垃圾回收
            System.gc()
            
            Log.d(TAG, "非必要资源清理完成")
            
        } catch (e: Exception) {
            Log.e(TAG, "清理非必要资源时发生错误", e)
        }
    }
}