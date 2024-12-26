package com.example.mymediaplayer

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.hypot

class MainActivity : AppCompatActivity() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var seekBar: SeekBar
    private lateinit var handler: Handler
    private lateinit var btnPlay: Button
    private lateinit var btnPause: Button
    private lateinit var btnReplay: Button
    private lateinit var btnOpenFile: Button
    private lateinit var btnSpeed: Button
    private lateinit var btnEffects: Button
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var visualizerView: VisualizerView
    private var musicInfoDisplay: MusicInfoDisplay? = null
    private var visualizer: Visualizer? = null

    private var currentFileUri: Uri? = null
    private var isVideo = true
    private var isFirstPlay = true
    private var isPaused = false

    // 定义可用的倍速列表
    private val playbackSpeeds = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)
    private var currentSpeedIndex = 1 // 默认播放速度索引为 1（1.0x）

    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 1
        private const val REQUEST_CODE_RECORD_AUDIO = 123 // 自定义请求码
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val tvArtist = findViewById<TextView>(R.id.tvArtist)
        val tvAlbumName = findViewById<TextView>(R.id.tvAlbumName)
        val ivAlbumCover = findViewById<ImageView>(R.id.ivAlbumCover)

        musicInfoDisplay = MusicInfoDisplay(this, tvArtist, tvAlbumName, ivAlbumCover)

        handler = Handler(Looper.getMainLooper())

        // 初始化 SurfaceView
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                if (::mediaPlayer.isInitialized) {
                    mediaPlayer.setDisplay(holder)
                    if (mediaPlayer.isPlaying) {
                        adjustVideoSize(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
                    }
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (::mediaPlayer.isInitialized) {
                    mediaPlayer.release()
                }
            }
        })

        // 设置按钮点击事件
        btnPlay.setOnClickListener { playOrResume() }
        btnPause.setOnClickListener { pause() }
        btnReplay.setOnClickListener { replay() }
        btnOpenFile.setOnClickListener { openFile() }
        btnSpeed.setOnClickListener { changePlaybackSpeed() }
        btnEffects.setOnClickListener { toggleVisualizerType() }

        // 默认显示当前播放速度
        updateSpeedButtonText()

        // 设置进度条事件
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && ::mediaPlayer.isInitialized) {
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}

            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // 检查并请求 RECORD_AUDIO 权限
        checkAndRequestAudioPermission()
    }

    /**
     * 检查是否已获得 RECORD_AUDIO 权限，如果未获得则请求权限
     */
    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // 如果需要向用户解释为何需要该权限，可以在这里添加逻辑
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.RECORD_AUDIO
                )
            ) {
                // 显示权限请求解释对话框
                AlertDialog.Builder(this)
                    .setTitle("权限请求")
                    .setMessage("需要访问麦克风以实现音频可视化功能。")
                    .setPositiveButton("确定") { dialog, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            REQUEST_CODE_RECORD_AUDIO
                        )
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消") { dialog, _ ->
                        dialog.dismiss()
                        Toast.makeText(
                            this,
                            "权限被拒绝，音频可视化功能将无法使用。",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .create()
                    .show()
            } else {
                // 直接请求权限
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_CODE_RECORD_AUDIO
                )
            }
        } else {
            // 已经拥有权限，初始化相关功能
            initializeMediaPlayer()
        }
    }

    /**
     * 处理权限请求的回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 权限被授予，初始化相关功能
                    Toast.makeText(this, "权限已授予。", Toast.LENGTH_SHORT).show()
                    initializeMediaPlayer()
                } else {
                    // 权限被拒绝，禁用相关功能或提示用户
                    Toast.makeText(
                        this,
                        "权限被拒绝，音频可视化功能将无法使用。",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * 初始化 MediaPlayer 和相关功能
     */
    private fun initializeMediaPlayer() {
        // 默认加载音频文件
        currentFileUri = Uri.parse("android.resource://${packageName}/${R.raw.sample_audio}")
        isVideo = false
        updateMusicInfo(currentFileUri)
        toggleMusicInfo(true)
    }

    private fun initMediaPlayer(fileUri: Uri) {
        // 初始化 MediaPlayer
        if (::mediaPlayer.isInitialized) {
            try {
                mediaPlayer.reset()
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Error resetting MediaPlayer: ${e.message}")
                mediaPlayer.release()
                mediaPlayer = MediaPlayer()
            }
        } else {
            mediaPlayer = MediaPlayer()
        }

        // 设置音频属性（AudioAttributes）
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA) // 适用于媒体播放
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC) // 音乐内容类型
            .build()
        mediaPlayer.setAudioAttributes(audioAttributes)

        // 设置 MediaPlayer 数据源和监听器
        mediaPlayer.apply {
            setDataSource(this@MainActivity, fileUri)
            setOnPreparedListener { mp ->
                // 准备完成时的回调
                seekBar.max = mp.duration
                tvTotalTime.text = formatTime(mp.duration)
                setPlaybackSpeed(playbackSpeeds[currentSpeedIndex])

                if (isVideo) {
                    // 如果是视频，调整视频尺寸并隐藏音乐信息
                    adjustVideoSize(mp.videoWidth, mp.videoHeight)
                    toggleMusicInfo(false)
                } else {
                    // 如果是音频，更新音乐信息并显示音乐相关控件
                    updateMusicInfo(fileUri)
                    toggleMusicInfo(true)
                }

                // 初始化 Visualizer
                initVisualizer(mp.audioSessionId)

                // 开始播放并更新 SeekBar
                playOrResume()
                updateSeekBar()
            }
            setOnCompletionListener {
                // 播放完成后的回调
                Toast.makeText(this@MainActivity, "Playback completed", Toast.LENGTH_SHORT).show()
                seekTo(0)
                isPaused = false
            }
            prepareAsync() // 异步准备 MediaPlayer
        }
    }

    private fun initVisualizer(sessionId: Int) {
        // 确保 sessionId 有效
        if (sessionId == AudioManager.ERROR) {
            Log.e("Visualizer", "Invalid audio session ID.")
            return
        }

        try {
            visualizer = Visualizer(sessionId).apply {
                val captureSizeRange = Visualizer.getCaptureSizeRange()
                val captureSize = captureSizeRange[1] // 设置为最大捕获大小
                if (captureSize in captureSizeRange[0]..captureSizeRange[1]) {
                    setCaptureSize(captureSize)
                    Log.d("CaptureSizeRange", "Capture size set to $captureSize")
                } else {
                    Log.e("CaptureSizeRange", "Invalid capture size: $captureSize")
                    return
                }

                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        visualizerView.updateWaveform(waveform)
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        visualizerView.updateFft(fft)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)

                enabled = true
                Log.d("Visualizer", "Visualizer 初始化并启用成功。")
            }
        } catch (e: Exception) {
            Log.e("Visualizer", "Error initializing Visualizer: ${e.message}")
            Toast.makeText(this, "无法初始化可视化效果。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playOrResume() {
        // 开始或恢复播放
        if (isFirstPlay) {
            currentFileUri?.let { initMediaPlayer(it) }
            isFirstPlay = false
        } else if (isPaused && ::mediaPlayer.isInitialized) {
            mediaPlayer.start()
            isPaused = false
            updateSeekBar()
        } else if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
            mediaPlayer.start()
            updateSeekBar()
        }
    }

    private fun pause() {
        // 暂停播放
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPaused = true
        }
    }

    private fun replay() {
        // 从头开始重新播放
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.seekTo(0)
            mediaPlayer.start()
            isPaused = false
            updateSeekBar()
        }
    }

    private fun changePlaybackSpeed() {
        // 改变播放速度
        if (::mediaPlayer.isInitialized) {
            currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.size
            setPlaybackSpeed(playbackSpeeds[currentSpeedIndex])
            updateSpeedButtonText()
            Toast.makeText(this, "Speed: ${playbackSpeeds[currentSpeedIndex]}x", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        // 设置播放速度
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ::mediaPlayer.isInitialized) {
            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed)
        }
    }

    private fun updateSpeedButtonText() {
        // 更新倍速按钮文本
        btnSpeed.text = "Speed: ${playbackSpeeds[currentSpeedIndex]}x"
    }

    private fun updateMusicInfo(fileUri: Uri?) {
        // 更新音乐信息
        if (!isVideo) {
            fileUri?.let { musicInfoDisplay?.displayMusicInfo(it) }
            toggleMusicInfo(true)
        } else {
            toggleMusicInfo(false)
        }
    }

    private fun toggleMusicInfo(show: Boolean) {
        // 显示或隐藏音乐信息控件
        val visibility = if (show) View.VISIBLE else View.GONE
        musicInfoDisplay?.apply {
            tvArtist.visibility = visibility
            tvAlbumName.visibility = visibility
            ivAlbumCover.visibility = visibility
        }
    }

    private fun adjustVideoSize(videoWidth: Int, videoHeight: Int) {
        // 调整视频尺寸以适应屏幕
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

    private fun updateSeekBar() {
        // 更新播放进度条
        try {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                val currentPosition = mediaPlayer.currentPosition
                seekBar.progress = currentPosition
                tvCurrentTime.text = formatTime(currentPosition)
                handler.postDelayed({ updateSeekBar() }, 500)
            }
        } catch (e: IllegalStateException) {
            Log.e("MainActivity", "Error updating seek bar: ${e.message}")
        }
    }

    private fun openFile() {
        // 打开文件选择器
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE)
    }

    private fun formatTime(millis: Int): String {
        // 格式化时间为 "mm:ss"
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // 文件选择器返回的结果处理
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_OPEN_FILE && resultCode == RESULT_OK && data != null) {
            currentFileUri = data.data
            if (currentFileUri != null) {
                try {
                    isVideo = isVideoFile(currentFileUri!!)
                    initMediaPlayer(currentFileUri!!)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error initializing MediaPlayer: ${e.message}")
                    Toast.makeText(this, "Failed to play the selected file", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e("MainActivity", "File URI is null!")
            }
        }
    }

    private fun isVideoFile(uri: Uri): Boolean {
        // 判断文件是否为视频类型
        val contentResolver: ContentResolver = contentResolver
        val type = contentResolver.getType(uri)
        return type != null && type.startsWith("video")
    }

    private fun toggleVisualizerType() {
        // 切换可视化类型（在 VisualizerView 中处理）
        visualizerView.toggleVisualizationType()
    }

    override fun onDestroy() {
        // 销毁活动时释放 MediaPlayer 和 Visualizer
        if (::mediaPlayer.isInitialized) {
            try {
                mediaPlayer.stop()
                mediaPlayer.reset()
                mediaPlayer.release()
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Error releasing MediaPlayer: ${e.message}")
            }
        }

        visualizer?.release() // 释放 Visualizer
        handler.removeCallbacksAndMessages(null) // 清除未执行的任务
        super.onDestroy()
    }
}
