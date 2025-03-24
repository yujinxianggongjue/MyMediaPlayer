package com.example.mymediaplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AudioFocusTestActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private lateinit var tvStatus: TextView
    private val audioPlayers = HashMap<String, MediaPlayer?>()
    private val audioFocusRequests = HashMap<String, AudioFocusRequest?>()
    private val audioThreads = HashMap<String, Thread?>()
    private val audioFocusChangeListeners = HashMap<String, AudioManager.OnAudioFocusChangeListener?>()

    private data class AudioScene(
        val name: String,
        val usage: Int,
        val contentType: Int = AudioAttributes.CONTENT_TYPE_MUSIC,
        val focusGain: Int = AudioManager.AUDIOFOCUS_GAIN,
        val canDuck: Boolean = false
    )

    private val audioScenes = listOf(
        // 媒体音频
        AudioScene("Media1", AudioAttributes.USAGE_MEDIA),
        AudioScene("Media2", AudioAttributes.USAGE_MEDIA),
        // 导航音频
        AudioScene("Nav1", AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, canDuck = true),
        AudioScene("Nav2", AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, focusGain = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, canDuck = true),
        // 语音助手
        AudioScene("VR1", AudioAttributes.USAGE_ASSISTANT),
        AudioScene("VR2", AudioAttributes.USAGE_ASSISTANT),
        // 电话音频
        AudioScene("Call Ring", AudioAttributes.USAGE_NOTIFICATION_RINGTONE, AudioAttributes.CONTENT_TYPE_SONIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT),
        AudioScene("Call", AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.CONTENT_TYPE_SPEECH),
        // 系统音频
        AudioScene("Alarm", AudioAttributes.USAGE_ALARM),
        AudioScene("System", AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, AudioAttributes.CONTENT_TYPE_SONIFICATION),
        // 安全提示音
        AudioScene("PDC", AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
        AudioScene("EXW", AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
        AudioScene("Turn Light", AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
        AudioScene("Vehicle Status", AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
        AudioScene("Emergency", AudioAttributes.USAGE_ALARM)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_focus_test)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        setupAudioButtons()
    }

    private fun setupAudioButtons() {
        val loopButtonIds = mapOf(
            R.id.btnMedia1Loop to "Media1",
            R.id.btnMedia2Loop to "Media2",
            R.id.btnNav1Loop to "Nav1",
            R.id.btnNav2Loop to "Nav2",
            R.id.btnVR1Loop to "VR1",
            R.id.btnVR2Loop to "VR2",
            R.id.btnCallRingLoop to "Call Ring",
            R.id.btnCallLoop to "Call",
            R.id.btnSystemLoop to "System",
            R.id.btnPDCLoop to "PDC",
            R.id.btnEXWLoop to "EXW",
            R.id.btnTurnLightLoop to "Turn Light",
            R.id.btnVehicleStatusLoop to "Vehicle Status",
            R.id.btnEmergencyLoop to "Emergency",
            R.id.btnAlarmLoop to "Alarm"
        )

        val onceButtonIds = mapOf(
            R.id.btnMedia1Once to "Media1",
            R.id.btnMedia2Once to "Media2",
            R.id.btnNav1Once to "Nav1",
            R.id.btnNav2Once to "Nav2",
            R.id.btnVR1Once to "VR1",
            R.id.btnVR2Once to "VR2",
            R.id.btnCallRingOnce to "Call Ring",
            R.id.btnCallOnce to "Call",
            R.id.btnSystemOnce to "System",
            R.id.btnPDCOnce to "PDC",
            R.id.btnEXWOnce to "EXW",
            R.id.btnTurnLightOnce to "Turn Light",
            R.id.btnVehicleStatusOnce to "Vehicle Status",
            R.id.btnEmergencyOnce to "Emergency",
            R.id.btnAlarmOnce to "Alarm"
        )

        val stopButtonIds = mapOf(
            R.id.btnMedia1Stop to "Media1",
            R.id.btnMedia2Stop to "Media2",
            R.id.btnNav1Stop to "Nav1",
            R.id.btnNav2Stop to "Nav2",
            R.id.btnVR1Stop to "VR1",
            R.id.btnVR2Stop to "VR2",
            R.id.btnCallRingStop to "Call Ring",
            R.id.btnCallStop to "Call",
            R.id.btnSystemStop to "System",
            R.id.btnPDCStop to "PDC",
            R.id.btnEXWStop to "EXW",
            R.id.btnTurnLightStop to "Turn Light",
            R.id.btnVehicleStatusStop to "Vehicle Status",
            R.id.btnEmergencyStop to "Emergency",
            R.id.btnAlarmStop to "Alarm"
        )

        loopButtonIds.forEach { (buttonId, sceneName) ->
            findViewById<Button>(buttonId).setOnClickListener {
                playAudioScene(audioScenes.first { it.name == sceneName }, true)
            }
        }

        onceButtonIds.forEach { (buttonId, sceneName) ->
            findViewById<Button>(buttonId).setOnClickListener {
                playAudioScene(audioScenes.first { it.name == sceneName }, false)
            }
        }

        stopButtonIds.forEach { (buttonId, sceneName) ->
            findViewById<Button>(buttonId).setOnClickListener {
                stopAudioScene(sceneName)
            }
        }
    }

    private fun playAudioScene(scene: AudioScene, isLooping: Boolean) {
        // 如果该场景已经在播放，先停止
        stopAudioScene(scene.name)

        val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            val focusInfo = "场景[${scene.name}] 音频属性[Usage:${scene.usage}, ContentType:${scene.contentType}] 是否支持降音量[${scene.canDuck}]"
            when (focusChange) {
                // 获得焦点系列
                AudioManager.AUDIOFOCUS_GAIN,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                    updateStatus("${scene.name}: 获得音频焦点")
                    Log.d("AudioFocusDebug", "获得音频焦点 - $focusInfo")
                    audioPlayers[scene.name]?.apply {
                        setVolume(1.0f, 1.0f)
                        Log.d("AudioFocusDebug", "恢复正常音量 - $focusInfo")
                        if (!isPlaying) {
                            start()
                            Log.d("AudioFocusDebug", "开始播放 - $focusInfo")
                        }
                    }
                }
                // 暂时失去焦点系列
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    updateStatus("${scene.name}: 暂时失去音频焦点")
                    Log.d("AudioFocusDebug", "暂时失去音频焦点 - $focusInfo")
                    audioPlayers[scene.name]?.apply {
                        pause()
                        Log.d("AudioFocusDebug", "暂停播放 - $focusInfo")
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    updateStatus("${scene.name}: 暂时失去音频焦点，降低音量")
                    Log.d("AudioFocusDebug", "暂时失去音频焦点，降低音量 - $focusInfo")
                    audioPlayers[scene.name]?.apply {
                        setVolume(0.2f, 0.2f)
                        Log.d("AudioFocusDebug", "降低音量 - $focusInfo")
                    }
                }
                // 永久失去焦点
                AudioManager.AUDIOFOCUS_LOSS -> {
                    updateStatus("${scene.name}: 失去音频焦点")
                    Log.d("AudioFocusDebug", "失去音频焦点 - $focusInfo")
                    stopAudioScene(scene.name)
                    audioFocusRequests[scene.name] = null
                    Log.d("AudioFocusDebug", "停止播放并释放资源 - $focusInfo")
                }
            }
        }

        // 创建并存储音频焦点监听器
        audioFocusChangeListeners[scene.name] = audioFocusChangeListener

        // 在新线程中处理音频播放
        val audioThread = Thread {
            // 创建音频焦点请求
            val focusRequest = AudioFocusRequest.Builder(scene.focusGain)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(scene.usage)
                    .setContentType(scene.contentType)
                    .build())
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioFocusRequests[scene.name] = focusRequest

            // 请求音频焦点
            val result = audioManager.requestAudioFocus(focusRequest)
            Log.d("AudioFocusDebug", "请求音频焦点 - 场景[${scene.name}] 焦点类型[${scene.focusGain}] 结果[${if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "成功" else "失败"}]")
            
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                runOnUiThread { updateStatus("${scene.name}: 开始播放") }
                
                // 创建MediaPlayer并播放测试音频
                val player = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(scene.usage)
                        .setContentType(scene.contentType)
                        .build())
                    // 使用raw资源中的测试音频文件
                    val resourceId = when(scene.name) {
                        "Media1", "Media2" -> R.raw.test_media
                        "Nav1", "Nav2" -> R.raw.test_nav
                        "VR1", "VR2" -> R.raw.test_vr
                        "Call Ring", "Call" -> R.raw.test_call
                        "System" -> R.raw.test_system
                        "PDC" -> R.raw.test_pdc
                        "EXW" -> R.raw.test_exw
                        "Turn Light" -> R.raw.test_turn_light
                        "Vehicle Status" -> R.raw.test_vehicle_status
                        "Emergency" -> R.raw.test_emergency
                        "Alarm" -> R.raw.test_system
                        else -> R.raw.test_media
                    }
                    val afd = resources.openRawResourceFd(resourceId)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    setLooping(isLooping)
                    setOnCompletionListener {
                        if (!isLooping) {
                            stopAudioScene(scene.name)
                        }
                    }
                    prepare()
                    start()
                }
                
                audioPlayers[scene.name] = player
            } else {
                runOnUiThread { updateStatus("${scene.name}: 无法获取音频焦点") }
            }
        }

        audioThread.start()
        audioThreads[scene.name] = audioThread

    }

    private fun stopAudioScene(sceneName: String) {
        audioPlayers[sceneName]?.apply {
            if (isPlaying) {
                stop()
                Log.d("AudioFocusDebug", "停止场景[$sceneName]播放")
            }
            release()
            Log.d("AudioFocusDebug", "释放场景[$sceneName]的MediaPlayer资源")
        }
        audioPlayers[sceneName] = null

        audioFocusRequests[sceneName]?.let {
            audioManager.abandonAudioFocusRequest(it)
            Log.d("AudioFocusDebug", "放弃场景[$sceneName]的音频焦点")
        }
        audioFocusRequests[sceneName] = null

        audioThreads[sceneName]?.interrupt()
        audioThreads[sceneName] = null
        
        audioFocusChangeListeners[sceneName] = null
    }

    private fun stopAllAudio() {
        audioScenes.forEach { scene ->
            stopAudioScene(scene.name)
        }
    }

    private fun updateStatus(status: String) {
        tvStatus.text = status
        Log.d("AudioFocusTest", status)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllAudio()
    }
}