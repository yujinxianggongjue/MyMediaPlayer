package com.example.mymediaplayer

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.lang.Exception
import com.example.mymediaplayer.AudioRecoder.AudioRecorder


/**
 * MainActivity 是应用的主活动，负责初始化和协调其他组件。
 * 它实现了 MediaPlayerListener、VisualizerListener 和 PermissionCallback 接口，
 * 以处理媒体播放、可视化更新和权限请求的回调。
 */
class MainActivity : AppCompatActivity(),
    MediaPlayerListener,
    VisualizerListener,
    PermissionCallback {

    // 声明所有需要的视图和变量
    private lateinit var surfaceView: SurfaceView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnReplay: Button
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

    // 录音相关
    private lateinit var audioRecorder: AudioRecorder
    private var isRecorderViewVisible = false
    private var recordingViewContainer: ViewGroup? = null

    // 音效控件
    private lateinit var spinnerEqualizer: Spinner
    private lateinit var switchVirtualizer: Switch
    private lateinit var switchBassBoost: Switch

    // Visualizer 选择控件
    private lateinit var radioGroupVisualizer: RadioGroup
    private lateinit var rbWaveform: RadioButton
    private lateinit var rbBarGraph: RadioButton
    private lateinit var rbLineGraph: RadioButton

    // 新增的音量 SeekBar
    private lateinit var volumeSeekBar: SeekBar

    private var musicInfoDisplay: MusicInfoDisplay? = null

    private lateinit var handler: Handler

    private var currentFileUri: Uri? = null
    private var isVideo = true
    private var isFirstPlay = true
    private var isPaused = false

    // 定义可用的倍速列表
    private val playbackSpeeds = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)
    private var currentSpeedIndex = 1 // 默认播放速度索引为 1（1.0x）

    private lateinit var mediaPlayerManager: MediaPlayerManager
    private lateinit var visualizerManager: VisualizerManager
    private lateinit var permissionManager: PermissionManager

    // 新增的 videoContainer 和 musicInfoLayout
    private lateinit var videoContainer: FrameLayout
    private lateinit var musicInfoLayout: LinearLayout

    // 新增的 mainLayout
    private lateinit var mainLayout: LinearLayout

    // 定义请求码
    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 1
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置布局
        setContentView(R.layout.activity_main)

        // 初始化控件
        surfaceView = findViewById(R.id.surfaceView)
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlay)
        btnPause = findViewById(R.id.btnPause)
        btnReplay = findViewById(R.id.btnReplay)
        btnOpenFile = findViewById(R.id.btnOpenFile)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnEffects = findViewById(R.id.btnEffects)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        visualizerView = findViewById(R.id.visualizerView)
        tvArtist = findViewById(R.id.tvArtist)
        tvAlbumName = findViewById(R.id.tvAlbumName)
        ivAlbumCover = findViewById(R.id.ivAlbumCover)

        // 初始化新增的音效控件
        spinnerEqualizer = findViewById(R.id.spinnerEqualizer)
        switchVirtualizer = findViewById(R.id.switchVirtualizer)
        switchBassBoost = findViewById(R.id.switchBassBoost)

        // 初始化新增的 Visualizer 选择控件
        radioGroupVisualizer = findViewById(R.id.radioGroupVisualizer)
        rbWaveform = findViewById(R.id.rbWaveform)
        rbBarGraph = findViewById(R.id.rbBarGraph)
        rbLineGraph = findViewById(R.id.rbLineGraph)

        // 初始化音量 SeekBar
        volumeSeekBar = findViewById(R.id.volumeSeekBar)
        initializeVolumeSeekBar()

        // 初始化 MusicInfoDisplay
        musicInfoDisplay = MusicInfoDisplay(this, tvArtist, tvAlbumName, ivAlbumCover)

        // 初始化 Handler
        handler = Handler(Looper.getMainLooper())

        // 初始化 PermissionManager
        permissionManager = PermissionManager(this, this)
        permissionManager.checkAndRequestRecordAudioPermission()

        // 初始化 MediaPlayerManager
        mediaPlayerManager = MediaPlayerManager(this, this)

        // 初始化 SurfaceHolder
        val surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // 设置 MediaPlayer 的显示界面
                mediaPlayerManager.setDisplay(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // 处理 SurfaceView 尺寸改变
                if (mediaPlayerManager.isPlaying()) {
                    adjustVideoSize(mediaPlayerManager.getVideoWidth(), mediaPlayerManager.getVideoHeight())
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // 释放 MediaPlayer 资源
                mediaPlayerManager.release()
            }
        })

        // 设置按钮点击事件
        btnPlay.setOnClickListener { playOrResume() }
        btnPause.setOnClickListener { pausePlayback() }
        btnReplay.setOnClickListener { replayPlayback() }
        btnOpenFile.setOnClickListener { openFile() }
        btnSpeed.setOnClickListener { changePlaybackSpeed() }
        btnEffects.setOnClickListener { toggleSoundEffects() }
        btnRecord = findViewById(R.id.btnRecord)
        btnRecord.setOnClickListener { toggleRecordingView() }

        // 初始化录音管理器
        audioRecorder = AudioRecorder(this)

        // 默认显示当前播放速度
        updateSpeedButtonText()

        // 设置进度条事件
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayerManager.seekTo(progress)
                    tvCurrentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                // 用户开始拖动进度条
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                // 用户停止拖动进度条
            }
        })

        // 初始化音效选择控件
        initSoundEffectControls()

        // 初始化 Visualizer 选择监听器
        initVisualizerSelection()

        // 初始化新增的 videoContainer 和 musicInfoLayout
        videoContainer = findViewById(R.id.videoContainer)
        musicInfoLayout = findViewById(R.id.musicInfoLayout)

        // 初始化 mainLayout
        mainLayout = findViewById(R.id.mainLayout)
    }

    /**
     * 初始化音量 SeekBar
     */
    private fun initializeVolumeSeekBar() {
        // 获取当前音量（0-100）
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = if (maxVolume == 0) 0 else (currentVolume * 100) / maxVolume

        // 设置 SeekBar 的最大值和当前进度
        volumeSeekBar.max = 100
        volumeSeekBar.progress = volumePercent

        // 设置 SeekBar 的监听器
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setAppVolume(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // 用户开始拖动 SeekBar
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 用户停止拖动 SeekBar
            }
        })
    }

    /**
     * 设置应用的音量
     * @param volumePercent 音量百分比（0-100）
     */
    private fun setAppVolume(volumePercent: Int) {
        // 将百分比转换为 0.0f 到 1.0f 的范围
        val volume = volumePercent / 100f

        // 使用 MediaPlayerManager 设置音量
        mediaPlayerManager.setVolume(volume)

        // 更新系统音量（可选）
        /*
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (volumePercent * maxVolume) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        */
    }

    /**
     * 权限被授予后的回调
     */
    override fun onPermissionGranted() {
        // 初始化 MediaPlayer
        initializeMediaPlayer()
    }

    /**
     * 权限被拒绝后的回调
     */
    override fun onPermissionDenied() {
        // 禁用音频可视化相关功能
        btnEffects.isEnabled = false
        // 禁用音效选择控件
        spinnerEqualizer.isEnabled = false
        switchVirtualizer.isEnabled = false
        switchBassBoost.isEnabled = false
    }

    /**
     * 初始化 MediaPlayer，加载默认音频文件
     */
    private fun initializeMediaPlayer() {
        currentFileUri = Uri.parse("android.resource://${packageName}/${R.raw.sample_audio}")
        isVideo = false
        musicInfoDisplay?.displayMusicInfo(currentFileUri!!)
        musicInfoDisplay?.toggleMusicInfo(true)
    }

    /**
     * 播放或恢复播放
     */
    private fun playOrResume() {
        if (isFirstPlay) {
            currentFileUri?.let {
                mediaPlayerManager.initMediaPlayer(it, isVideo)
                isFirstPlay = false
            }
        } else if (isPaused) {
            mediaPlayerManager.play()
            isPaused = false
            updateSeekBar()
        } else {
            mediaPlayerManager.play()
            updateSeekBar()
        }
    }

    /**
     * 暂停播放
     */
    private fun pausePlayback() {
        mediaPlayerManager.pause()
        isPaused = true
    }

    /**
     * 重播
     */
    private fun replayPlayback() {
        mediaPlayerManager.replay()
        isPaused = false
        updateSeekBar()
    }

    /**
     * 改变播放速度
     */
    private fun changePlaybackSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.size
        mediaPlayerManager.setPlaybackSpeed(playbackSpeeds[currentSpeedIndex])
        updateSpeedButtonText()
        Toast.makeText(this, "速度: ${playbackSpeeds[currentSpeedIndex]}x", Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新播放速度按钮的文本
     */
    private fun updateSpeedButtonText() {
        btnSpeed.text = "速度: ${playbackSpeeds[currentSpeedIndex]}x"
    }

    /**
     * 切换音效控件的显示与隐藏
     */
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

    private fun toggleRecordingView() {
        if (!isRecorderViewVisible) {
            // 隐藏播放界面的控件
            videoContainer.visibility = View.GONE
            musicInfoLayout.visibility = View.GONE
            visualizerView.visibility = View.GONE
            seekBar.visibility = View.GONE
            tvCurrentTime.visibility = View.GONE
            tvTotalTime.visibility = View.GONE

            // 动态加载录音界面
            if (recordingViewContainer == null) {
                val inflater = layoutInflater
                recordingViewContainer = inflater.inflate(R.layout.aduiorecorder, mainLayout, false) as ViewGroup
                mainLayout.addView(recordingViewContainer)

                // 初始化录音界面的控件
                initRecordingViews(recordingViewContainer!!)
            }
            recordingViewContainer?.visibility = View.VISIBLE
            btnRecord.text = "Back"
            isRecorderViewVisible = true
        } else {
            // 显示播放界面的控件
            videoContainer.visibility = View.VISIBLE
            musicInfoLayout.visibility = View.VISIBLE
            visualizerView.visibility = View.VISIBLE
            seekBar.visibility = View.VISIBLE
            tvCurrentTime.visibility = View.VISIBLE
            tvTotalTime.visibility = View.VISIBLE

            // 隐藏录音界面
            recordingViewContainer?.visibility = View.GONE
            btnRecord.text = "Record"
            isRecorderViewVisible = false

            // 如果正在录音，停止录音
            if (audioRecorder.isRecording()) {
                audioRecorder.stopRecording()
            }
        }
    }

    private fun initRecordingViews(container: ViewGroup) {
        // 设置录音时间显示
        val recordTimeTextView = container.findViewById<TextView>(R.id.recordTimeTextView)
        audioRecorder.setRecordTimeTextView(recordTimeTextView)

        // 设置录音路径显示
        val recordPathTextView = container.findViewById<TextView>(R.id.recordPathTextView)
        audioRecorder.setRecordPathTextView(recordPathTextView)

        // 设置通道选择
        val channelSpinner = container.findViewById<Spinner>(R.id.channelSpinner)
        val channelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("单声道", "立体声", "8声道", "12声道", "16声道"))
        channelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        channelSpinner.adapter = channelAdapter
        channelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                audioRecorder.setChannel(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 设置采样率选择
        val sampleRateSpinner = container.findViewById<Spinner>(R.id.sampleRateSpinner)
        val sampleRateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("8000Hz", "16000Hz", "22050Hz", "44100Hz", "48000Hz"))
        sampleRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sampleRateSpinner.adapter = sampleRateAdapter
        sampleRateSpinner.setSelection(3) // 默认44100Hz
        sampleRateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                audioRecorder.setSampleRate(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 设置比特率选择
        val bitRateSpinner = container.findViewById<Spinner>(R.id.bitRateSpinner)
        val bitRateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, arrayOf("32kbps", "64kbps", "128kbps", "192kbps", "256kbps"))
        bitRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bitRateSpinner.adapter = bitRateAdapter
        bitRateSpinner.setSelection(2) // 默认128kbps
        bitRateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                audioRecorder.setBitRate(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 设置开始录音按钮
        val startButton = container.findViewById<Button>(R.id.startButton)
        startButton.setOnClickListener {
            audioRecorder.startRecording()
        }

        // 设置停止录音按钮
        val stopButton = container.findViewById<Button>(R.id.stopButton)
        stopButton.setOnClickListener {
            audioRecorder.stopRecording()
        }

        // 设置播放录音按钮
        val playButton = container.findViewById<Button>(R.id.playButton)
        playButton.setOnClickListener {
            audioRecorder.getRecordedUri()?.let { uri ->
                currentFileUri = uri
                isVideo = false
                mediaPlayerManager.initMediaPlayer(uri, false)
                toggleRecordingView() // 切换回播放界面
            }
        }
    }

    /**
     * 打开文件选择器
     */
    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE)
    }

    /**
     * 初始化音效选择控件
     */
    private fun initSoundEffectControls() {
        // 初始化均衡器 Spinner
        val equalizerPresets = getEqualizerPresets()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, equalizerPresets)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerEqualizer.adapter = adapter

        spinnerEqualizer.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                mediaPlayerManager.setEqualizerPreset(position.toShort())
                Log.d(TAG, "均衡器预设选择: $position")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // 初始化虚拟化器 Switch
        switchVirtualizer.setOnCheckedChangeListener { _, isChecked ->
            mediaPlayerManager.enableVirtualizer(isChecked)
            Log.d(TAG, "虚拟化器已${if (isChecked) "启用" else "禁用"}。")
        }

        // 初始化低音增强 Switch
        switchBassBoost.setOnCheckedChangeListener { _, isChecked ->
            mediaPlayerManager.enableBassBoost(isChecked)
            Log.d(TAG, "低音增强已${if (isChecked) "启用" else "禁用"}。")
        }
    }

    /**
     * 初始化 Visualizer 选择监听器
     */
    private fun initVisualizerSelection() {
        radioGroupVisualizer.setOnCheckedChangeListener { group, checkedId ->
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

    /**
     * 获取均衡器预设列表
     * @return 预设名称列表
     */
    private fun getEqualizerPresets(): List<String> {
        // 根据实际需求定义均衡器预设名称
        return listOf(
            "Flat",
            "Bass Boost",
            "Rock",
            "Pop",
            "Jazz",
            "Classical",
            "Dance",
            "Hip Hop"
        )
    }

    /**
     * 格式化时间为 "mm:ss"
     * @param millis 毫秒数
     * @return 格式化后的时间字符串
     */
    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * 处理文件选择器返回的结果
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == RESULT_OK && data != null) {
            currentFileUri = data.data
            if (currentFileUri != null) {
                try {
                    isVideo = isVideoFile(currentFileUri!!)
                    mediaPlayerManager.initMediaPlayer(currentFileUri!!, isVideo)
                    if (!isVideo) {
                        musicInfoDisplay?.displayMusicInfo(currentFileUri!!)
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

    /**
     * 判断文件是否为视频类型
     * @param uri 文件的 Uri
     * @return 如果是视频文件则返回 true，否则返回 false
     */
    private fun isVideoFile(uri: Uri): Boolean {
        val contentResolver: ContentResolver = contentResolver
        val type = contentResolver.getType(uri)
        return type != null && type.startsWith("video")
    }

    /**
     * 更新播放进度条
     */
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

    /**
     * 当 MediaPlayer 准备完成时回调
     */
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

        // 初始化 VisualizerManager，传递音频会话 ID 和监听器
        visualizerManager = VisualizerManager(mediaPlayerManager.getAudioSessionId(), this)
        visualizerManager.init()

        // 初始化音效管理器
        mediaPlayerManager.initSoundEffects()

        // 设置默认可视化类型
        visualizerManager.setVisualizerType(VisualizerType.WAVEFORM)

        // 开始更新进度条
        updateSeekBar()
    }

    /**
     * 当媒体播放完成时回调
     */
    override fun onCompletion() {
        Toast.makeText(this, "播放完成", Toast.LENGTH_SHORT).show()
        mediaPlayerManager.seekTo(0)
        isPaused = false
    }

    /**
     * 当波形数据更新时回调
     */
    override fun onWaveformUpdate(waveform: ByteArray?) {
        //Log.d(TAG, "onWaveformUpdate: 接收到波形数据，长度=${waveform?.size ?: 0}")
        visualizerView.updateWaveform(waveform)
    }

    /**
     * 当 FFT 数据更新时回调
     */
    override fun onFftUpdate(fft: ByteArray?) {
        //Log.d(TAG, "onFftUpdate: 接收到 FFT 数据，长度=${fft?.size ?: 0}")
        visualizerView.updateFft(fft)
    }

    /**
     * 调整视频尺寸以适应屏幕
     * @param videoWidth 视频宽度
     * @param videoHeight 视频高度
     */
    private fun adjustVideoSize(videoWidth: Int, videoHeight: Int) {
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

        // 设置 SurfaceView 的宽高和居中显示
        surfaceView.layoutParams = FrameLayout.LayoutParams(newWidth, newHeight, Gravity.CENTER)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放 MediaPlayer 和 Visualizer 资源
        mediaPlayerManager.release()
        visualizerManager.release()
        handler.removeCallbacksAndMessages(null)
    }
}