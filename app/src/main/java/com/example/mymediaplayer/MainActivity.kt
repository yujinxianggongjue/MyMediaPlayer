package com.example.mymediaplayer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * MainActivity 是应用的主活动，负责初始化和协调其他组件
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
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var visualizerView: VisualizerView
    private lateinit var tvArtist: TextView
    private lateinit var tvAlbumName: TextView
    private lateinit var ivAlbumCover: ImageView

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

    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 1
        private const val REQUEST_CODE_RECORD_AUDIO = 123 // 自定义请求码
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

        // 初始化 MusicInfoDisplay
        musicInfoDisplay = MusicInfoDisplay(this, tvArtist, tvAlbumName, ivAlbumCover)

        // 初始化 Handler
        handler = Handler(Looper.getMainLooper())

        // 初始化 PermissionManager
        permissionManager = PermissionManager(this, this)
        permissionManager.checkAndRequestRecordAudioPermission()

        // 初始化 MediaPlayerManager
        mediaPlayerManager = MediaPlayerManager(this, this)

        // 初始化 VisualizerManager
        visualizerManager = VisualizerManager(this)

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
        btnEffects.setOnClickListener { toggleVisualizerType() }

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
     * 切换可视化效果类型
     */
    private fun toggleVisualizerType() {
        visualizerView.toggleVisualizationType()
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

        // 初始化 Visualizer
        visualizerManager.initVisualizer(mediaPlayerManager.getAudioSessionId())

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
        visualizerView.updateWaveform(waveform)
    }

    /**
     * 当 FFT 数据更新时回调
     */
    override fun onFftUpdate(fft: ByteArray?) {
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