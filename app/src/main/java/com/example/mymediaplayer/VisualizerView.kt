// VisualizerView.kt
package com.example.mymediaplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * VisualizerView 是自定义视图，用于显示音频可视化效果。
 * 支持波形、柱状图和折线图三种可视化类型。
 */
class VisualizerView : View {
    private var currentType: VisualizerType = VisualizerType.LINE_GRAPH//默认是线性ß

    private var mWaveformBytes: ByteArray? = null // 存储波形数据
    private var mFftBytes: ByteArray? = null // 存储 FFT 数据
    private var mPoints: FloatArray? = null // 存储绘制线条的点
    private val mRect = Rect() // 用于绘制区域

    private val mForePaint = Paint() // 绘图画笔

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    /**
     * 初始化绘图相关的属性
     */
    private fun init() {
        mWaveformBytes = null
        mFftBytes = null

        mForePaint.strokeWidth = 2f // 线宽
        mForePaint.isAntiAlias = true // 抗锯齿
        mForePaint.color = Color.rgb(0, 128, 255) // 画笔颜色
    }

    /**
     * 更新波形数据并重绘视图
     * @param bytes 波形数据
     */
    fun updateWaveform(bytes: ByteArray?) {
        mWaveformBytes = bytes
        invalidate()
    }

    /**
     * 更新 FFT 数据并重绘视图
     * @param bytes FFT 数据
     */
    fun updateFft(bytes: ByteArray?) {
        mFftBytes = bytes
        invalidate()
    }

    /**
     * 设置可视化效果类型
     */
    fun setVisualizerType(type: VisualizerType) {
        currentType = type
        Log.d(TAG, "VisualizerView 类型切换为: $type")
        invalidate() // 重新绘制视图
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        mRect.set(0, 0, width, height)

        when (currentType) {
            VisualizerType.WAVEFORM -> {
                Log.d(TAG, "绘制波形图")
                drawWaveform(canvas)
            }
            VisualizerType.BAR_GRAPH -> {
                Log.d(TAG, "绘制柱状图")
                drawBarGraph(canvas)
            }
            VisualizerType.LINE_GRAPH -> {
                Log.d(TAG, "绘制折线图")
                drawLineGraph(canvas)
            }
        }
    }

    /**
     * 绘制波形可视化
     * @param canvas 画布
     */
    private fun drawWaveform(canvas: Canvas) {
        mWaveformBytes?.let { bytes ->
            mRect.set(0, 0, width, height)

            val points = FloatArray(bytes.size * 4)
            val rectWidth = mRect.width().toFloat()
            val rectHeight = mRect.height().toFloat()
            val halfHeight = rectHeight / 2f

            for (i in 0 until bytes.size - 1) {
                val x1 = rectWidth * i / (bytes.size - 1)
                val y1 = halfHeight + ((bytes[i].toFloat() + 128) * (halfHeight) / 128f)
                val x2 = rectWidth * (i + 1) / (bytes.size - 1)
                val y2 = halfHeight + ((bytes[i + 1].toFloat() + 128) * (halfHeight) / 128f)

                points[i * 4] = x1
                points[i * 4 + 1] = y1
                points[i * 4 + 2] = x2
                points[i * 4 + 3] = y2
            }

            canvas.drawLines(points, mForePaint)
        }
    }

    /**
     * 绘制柱状图可视化
     */
    private fun drawBarGraph(canvas: Canvas) {
        mFftBytes?.let { bytes ->
            Log.d(TAG, "开始绘制柱状图")
            val numBars = 50 // 柱状图的柱数
            val barWidth = width / numBars.toFloat()
            val maxHeight = height.toFloat()

            for (i in 0 until numBars) {
                // 每个柱子对应的频率索引
                val fftIndex = i * 2 * (bytes.size / 2) / numBars
                if (fftIndex + 1 < bytes.size) {
                    val re = bytes[fftIndex].toFloat()
                    val im = bytes[fftIndex + 1].toFloat()
                    val magnitude = sqrt(re * re + im * im)

                    // 归一化幅度值，根据需要调整缩放因子
                    val normalizedMagnitude = (magnitude / 256f) * maxHeight

                    //Log.d(TAG, "Bar $i: magnitude=$magnitude, normalizedHeight=$normalizedMagnitude")

                    canvas.drawRect(
                        i * barWidth,
                        height - normalizedMagnitude,
                        (i + 1) * barWidth - 2,
                        height.toFloat(),
                        mForePaint
                    )
                }
            }
            Log.d(TAG, "柱状图绘制完成")
        }
    }

    /**
     * 绘制折线图可视化
     */
    private fun drawLineGraph(canvas: Canvas) {
        mFftBytes?.let { bytes ->
            Log.d(TAG, "开始绘制折线图")
            if (mPoints == null || mPoints!!.size < (bytes.size / 2 - 1) * 4) {
                mPoints = FloatArray((bytes.size / 2 - 1) * 4)
            }

            val rectWidth = width.toFloat()
            val rectHeight = height.toFloat()
            val halfHeight = rectHeight / 2f

            for (i in 0 until bytes.size / 2 - 1) {
                val x1 = rectWidth * i / (bytes.size / 2 - 1).toFloat()
                val y1 = halfHeight + (bytes[i * 2].toFloat() * 2f) // 使用实部计算
                val x2 = rectWidth * (i + 1) / (bytes.size / 2 - 1).toFloat()
                val y2 = halfHeight + (bytes[(i + 1) * 2].toFloat() * 2f) // 使用实部计算

                mPoints!![i * 4] = x1
                mPoints!![i * 4 + 1] = y1
                mPoints!![i * 4 + 2] = x2
                mPoints!![i * 4 + 3] = y2

                //Log.d(TAG, "Point $i: ($x1, $y1) to ($x2, $y2)")
            }

            canvas.drawLines(mPoints!!, mForePaint)
            Log.d(TAG, "折线图绘制完成")
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        // 释放可视化相关资源（如果有）
    }

    companion object {
        private const val TAG = "VisualizerView"
    }
}