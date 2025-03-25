package com.example.mymediaplayer

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.mymediaplayer.AudioRecoder.AudioRecorder

/**
 * 专门用于录音操作的 Activity
 * 将原先 MainActivity 里跟录音相关的功能拆分到这里
 */
class AudioRecoderTest : AppCompatActivity() {

    private lateinit var audioRecorder: AudioRecorder

    // 这些是录音界面里的各种控件
    private lateinit var recordTimeTextView: TextView
    private lateinit var recordPathTextView: TextView
    private lateinit var channelSpinner: Spinner
    private lateinit var sampleRateSpinner: Spinner
    private lateinit var bitRateSpinner: Spinner
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var playButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 使用之前用于录音的布局: aduiorecorder.xml
        setContentView(R.layout.aduiorecorder)

        // 初始化 audioRecorder
        audioRecorder = AudioRecorder(this)

        // 找到布局中的控件
        recordTimeTextView = findViewById(R.id.recordTimeTextView)
        recordPathTextView = findViewById(R.id.recordPathTextView)
        channelSpinner = findViewById(R.id.channelSpinner)
        sampleRateSpinner = findViewById(R.id.sampleRateSpinner)
        bitRateSpinner = findViewById(R.id.bitRateSpinner)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        playButton = findViewById(R.id.playButton)

        // 设置到 audioRecorder 上，让其更新文本
        audioRecorder.setRecordTimeTextView(recordTimeTextView)
        audioRecorder.setRecordPathTextView(recordPathTextView)

        // 初始化下拉框（通道、采样率、比特率）
        initChannelSpinner()
        initSampleRateSpinner()
        initBitRateSpinner()

        // 设置开始录音按钮
        startButton.setOnClickListener {
            audioRecorder.startRecording()
        }

        // 设置停止录音按钮
        stopButton.setOnClickListener {
            audioRecorder.stopRecording()
        }

        // 设置播放录音按钮
        playButton.setOnClickListener {
            audioRecorder.getRecordedUri()?.let { uri ->
                // 简单弹个 Toast 提示，或者你可以在此直接调用播放器
                Toast.makeText(this, "开始播放录音: $uri", Toast.LENGTH_SHORT).show()
                // 如果想用系统播放器:
                // val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "audio/*") }
                // startActivity(intent)
            }
        }
    }

    private fun initChannelSpinner() {
        val channels = arrayOf("单声道", "立体声", "8声道", "12声道", "16声道")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, channels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        channelSpinner.adapter = adapter
        channelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, position: Int, id: Long) {
                audioRecorder.setChannel(position)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun initSampleRateSpinner() {
        val sampleRates = arrayOf("8000Hz", "16000Hz", "22050Hz", "44100Hz", "48000Hz")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sampleRates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sampleRateSpinner.adapter = adapter
        sampleRateSpinner.setSelection(3) // 默认选 44100Hz
        sampleRateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, position: Int, id: Long) {
                audioRecorder.setSampleRate(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun initBitRateSpinner() {
        val bitRates = arrayOf("32kbps", "64kbps", "128kbps", "192kbps", "256kbps")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bitRates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bitRateSpinner.adapter = adapter
        bitRateSpinner.setSelection(2) // 默认 128kbps
        bitRateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, position: Int, id: Long) {
                audioRecorder.setBitRate(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 如果仍在录音，要停止
        if (audioRecorder.isRecording()) {
            audioRecorder.stopRecording()
        }
    }
}