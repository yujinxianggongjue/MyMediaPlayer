package com.example.mymediaplayer

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
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
import java.io.File

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
        private const val REQUEST_CODE_MEDIA_PROJECTION = 2 // 用于申请MediaProjection
        private const val TAG = "MainActivity"
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

    // ====== 新增：AudioPlaybackCapture，用于捕获系统音频 ======
    private var mediaProjection: MediaProjection? = null // 记录授权后得到的 MediaProjection
    private val audioPlaybackCapture = AudioPlaybackCapture() // 核心录音类

    // UI显示录音状态
    private lateinit var tvCapturePath: TextView
    private lateinit var btnAudioCapture: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化控件
        initViews()
        handler = Handler(Looper.getMainLooper())

        // 初始化权限
        permissionManager = PermissionManager(this, this)
        permissionManager.checkAndRequestRecordAudioPermission()

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

        audioPlaybackCapture.setRecordingStatusTextView(findViewById(R.id.tvRecordingStatus))
        audioPlaybackCapture.setRecordingPathTextView(tvCapturePath)

        // 2) 点击开始/停止录制
        btnAudioCapture.setOnClickListener {
            if (audioPlaybackCapture.isRecording) {
                // 如果正在录制，则停止
                val path = audioPlaybackCapture.stopCapture()
                tvCapturePath.text = "Capture Path: $path"
            } else {
                // 未在录制 -> 开始录制
                // 如果没 MediaProjection，就先申请
                if (mediaProjection == null) {
                    requestMediaProjection()
                } else {
                    startSystemAudioCapture()
                }
            }
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
    private fun requestMediaProjection() {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mpManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_CODE_MEDIA_PROJECTION)
    }

    private fun startSystemAudioCapture() {
        // 我们将录制结果存到 /Android/data/your_package/files/Music/audio_capture.wav
        val outputFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "audio_capture.wav")
        Log.d(TAG, "开始录制系统音频到: ${outputFile.absolutePath}")
        mediaProjection?.let { projection ->
            audioPlaybackCapture.startCapture(projection, outputFile)
            tvCapturePath.text = "Recording..."
        }
    }

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
        mediaPlayerManager.setLooping(true)
    }

    private fun pausePlayback() {
        Log.d(TAG, "pausePlayback")
        mediaPlayerManager.pause()
        isPaused = true
    }

    private fun stopPlayback() {
        Log.d(TAG, "stopPlayback")
        mediaPlayerManager.stop()
        isPaused = false
        visualizerManager.release()
        seekBar.progress = 0
        tvCurrentTime.text = formatTime(0)
        isFirstPlay = true
    }

    private fun changePlaybackSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.size
        mediaPlayerManager.setPlaybackSpeed(playbackSpeeds[currentSpeedIndex])
        updateSpeedButtonText()
        Toast.makeText(this, "速度: ${playbackSpeeds[currentSpeedIndex]}x", Toast.LENGTH_SHORT).show()
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
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
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
        val volume = volumePercent / 100f
        mediaPlayerManager.setVolume(volume)
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

            REQUEST_CODE_MEDIA_PROJECTION -> {
                if (resultCode == RESULT_OK && data != null) {
                    // 用户同意屏幕捕捉
                    val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjection = mpManager.getMediaProjection(resultCode, data)
                    if (mediaProjection != null) {
                        // 正式开始录制系统音频
                        startSystemAudioCapture()
                    }
                } else {
                    Toast.makeText(this, "用户拒绝屏幕捕捉权限", Toast.LENGTH_SHORT).show()
                }
            }
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
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // ============ onDestroy ============
    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        mediaPlayerManager.release()
        if(::visualizerManager.isInitialized) {
            visualizerManager.release()
        }
        handler.removeCallbacksAndMessages(null)
    }
}