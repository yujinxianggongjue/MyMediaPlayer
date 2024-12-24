package com.example.mymediaplayer

import android.content.ContentResolver
import android.content.Intent
import android.media.MediaPlayer
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
import androidx.appcompat.app.AppCompatActivity

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
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private var musicInfoDisplay: MusicInfoDisplay? = null

    private var currentFileUri: Uri? = null
    private var isVideo = true
    private var isFirstPlay = true
    private var isPaused = false

    // 定义可用的倍速列表
    private val playbackSpeeds = floatArrayOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)
    private var currentSpeedIndex = 1 // 默认播放速度索引为 1（1.0x）

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
        btnSpeed = findViewById(R.id.btnSpeed) // 倍速按钮
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)

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
        btnSpeed.setOnClickListener { changePlaybackSpeed() } // 设置倍速点击事件

        // 默认显示当前播放速度
        updateSpeedButtonText()

        // 设置进度条事件
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && ::mediaPlayer.isInitialized) {
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // 默认加载音频文件
        currentFileUri = Uri.parse("android.resource://${packageName}/${R.raw.sample_audio}")
        isVideo = false
        updateMusicInfo(currentFileUri)
        toggleMusicInfo(true)
    }

    private fun initMediaPlayer(fileUri: Uri) {
        // 初始化 MediaPlayer
        // 如果 MediaPlayer 已经初始化，尝试调用 reset 重置状态，捕获可能的异常
        if (::mediaPlayer.isInitialized) {
            try {
                mediaPlayer.reset()
            } catch (e: IllegalStateException) {
                // 如果 reset 出错，释放 MediaPlayer 并重新创建
                Log.e("MainActivity", "Error resetting MediaPlayer: ${e.message}")
                mediaPlayer.release()
                mediaPlayer = MediaPlayer()
            }
        } else {
            // 如果尚未初始化，直接创建新的 MediaPlayer 实例
            mediaPlayer = MediaPlayer()
        }

        // 设置 MediaPlayer 数据源和监听器
        mediaPlayer.apply {
            setDataSource(this@MainActivity, fileUri) // 设置播放文件的 URI
            setOnPreparedListener { mp ->
                // 准备完成时的回调：设置播放进度条、播放时长和倍速
                seekBar.max = mp.duration
                tvTotalTime.text = formatTime(mp.duration)
                setPlaybackSpeed(playbackSpeeds[currentSpeedIndex]) // 设置当前倍速

                if (isVideo) {
                    // 如果是视频，调整视频尺寸并隐藏音乐信息
                    adjustVideoSize(mp.videoWidth, mp.videoHeight)
                    toggleMusicInfo(false)
                } else {
                    // 如果是音频，更新音乐信息并显示音乐相关控件
                    updateMusicInfo(fileUri)
                    toggleMusicInfo(true)
                }
                // 自动开始播放并更新播放进度条
                playOrResume()
                updateSeekBar()
            }
            setOnCompletionListener {
                // 播放完成后的回调：显示完成提示并重置到开始位置
                Toast.makeText(this@MainActivity, "Playback completed", Toast.LENGTH_SHORT).show()
                seekTo(0)
                isPaused = false
            }
            prepareAsync() // 异步准备 MediaPlayer
        }
    }

    private fun playOrResume() {
        // 开始或恢复播放
        if (isFirstPlay) {
            // 如果是第一次播放，初始化 MediaPlayer
            currentFileUri?.let { initMediaPlayer(it) }
            isFirstPlay = false
        } else if (isPaused && ::mediaPlayer.isInitialized) {
            // 如果是暂停状态，恢复播放
            mediaPlayer.start()
            isPaused = false
            updateSeekBar()
        } else if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
            // 如果未在播放，直接开始播放
            mediaPlayer.start()
            updateSeekBar()
        }
    }

    private fun pause() {
        // 暂停播放
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            isPaused = true // 标记为暂停状态
        }
    }

    private fun replay() {
        // 从头开始重新播放
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.seekTo(0) // 将播放位置设置到开始
            mediaPlayer.start() // 开始播放
            isPaused = false
            updateSeekBar() // 更新播放进度条
        }
    }

    // 改变倍速并更新按钮文本
    private fun changePlaybackSpeed() {
        // 改变播放速度
        if (::mediaPlayer.isInitialized) {
            currentSpeedIndex = (currentSpeedIndex + 1) % playbackSpeeds.size // 循环切换倍速
            setPlaybackSpeed(playbackSpeeds[currentSpeedIndex]) // 设置倍速
            updateSpeedButtonText() // 更新倍速按钮文本
            Toast.makeText(this, "Speed: ${playbackSpeeds[currentSpeedIndex]}x", Toast.LENGTH_SHORT).show()
        }
    }

    // 设置倍速
    private fun setPlaybackSpeed(speed: Float) {
        // 设置播放速度
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ::mediaPlayer.isInitialized) {
            mediaPlayer.playbackParams = mediaPlayer.playbackParams.setSpeed(speed) // 设置播放参数中的速度
        }
    }

    // 更新倍速按钮文本
    private fun updateSpeedButtonText() {
        // 更新倍速按钮上的文本
        btnSpeed.text = "Speed: ${playbackSpeeds[currentSpeedIndex]}x"
    }

    private fun updateMusicInfo(fileUri: Uri?) {
        // 更新音乐信息
        if (!isVideo) {
            fileUri?.let { musicInfoDisplay?.displayMusicInfo(it) } // 显示音乐文件的艺术家和专辑信息
            toggleMusicInfo(true) // 显示音乐相关控件
        } else {
            toggleMusicInfo(false) // 隐藏音乐相关控件
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

        // 根据屏幕宽高比和视频宽高比计算适合的宽高
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
                val currentPosition = mediaPlayer.currentPosition // 获取当前播放位置
                seekBar.progress = currentPosition // 更新进度条
                tvCurrentTime.text = formatTime(currentPosition) // 更新当前时间显示
                handler.postDelayed({ updateSeekBar() }, 500) // 每 500 毫秒更新一次
            }
        } catch (e: IllegalStateException) {
            Log.e("MainActivity", "Error updating seek bar: ${e.message}") // 捕获状态异常
        }
    }

    private fun openFile() {
        // 打开文件选择器
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*" // 允许选择任意类型的文件
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_FILE) // 启动文件选择器
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
                    isVideo = isVideoFile(currentFileUri!!) // 判断是否为视频文件
                    initMediaPlayer(currentFileUri!!) // 初始化播放器
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

    override fun onDestroy() {
        // 销毁活动时释放 MediaPlayer
        if (::mediaPlayer.isInitialized) {
            try {
                mediaPlayer.stop()
                mediaPlayer.reset()
                mediaPlayer.release()
            } catch (e: IllegalStateException) {
                Log.e("MainActivity", "Error releasing MediaPlayer: ${e.message}")
            }
        }
        handler.removeCallbacksAndMessages(null) // 清除未执行的任务
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_OPEN_FILE = 1
    }
}