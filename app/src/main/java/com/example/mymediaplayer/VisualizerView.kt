package com.example.mymediaplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * VisualizerView 是自定义视图，用于显示音频可视化效果。
 * 支持波形、柱状图和折线图三种可视化类型。
 */
class VisualizerView : View {
    private var mWaveformBytes: ByteArray? = null // 存储波形数据
    private var mFftBytes: ByteArray? = null // 存储 FFT 数据
    private var mPoints: FloatArray? = null // 存储绘制线条的点
    private val mRect = Rect() // 用于绘制区域

    private val mForePaint = Paint() // 绘图画笔

    private var type = 0 // 当前可视化类型：0 - 波形，1 - 柱状图，2 - 折线图

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
     * 切换可视化类型：波形 -> 柱状图 -> 折线图 -> 波形 ...
     */
    fun toggleVisualizationType() {
        type = (type + 1) % 3
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 触摸事件，点击时切换可视化类型
        if (event.action == MotionEvent.ACTION_DOWN) {
            toggleVisualizationType()
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        mRect.set(0, 0, width, height)

        when (type) {
            0 -> drawWaveform(canvas) // 绘制波形
            1 -> drawBarVisualizer(canvas) // 绘制柱状图
            2 -> drawLineVisualizer(canvas) // 绘制折线图
            else -> drawWaveform(canvas) // 默认绘制波形
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
                val y1 = halfHeight + ((bytes[i] + 128) * (halfHeight) / 128f)
                val x2 = rectWidth * (i + 1) / (bytes.size - 1)
                val y2 = halfHeight + ((bytes[i + 1] + 128) * (halfHeight) / 128f)

                points[i * 4] = x1
                points[i * 4 + 1] = y1
                points[i * 4 + 2] = x2
                points[i * 4 + 3] = y2
            }

            canvas.drawLines(points, mForePaint)
        }
    }

    /**
     * 绘制柱状图可视化，使用 FFT 数据
     * @param canvas 画布
     */
    private fun drawBarVisualizer(canvas: Canvas) {
        mFftBytes?.let { bytes ->
            val numBars = 50 // 柱状图的柱数
            val barWidth = mRect.width() / numBars.toFloat()
            val maxHeight = mRect.height().toFloat()

            for (i in 0 until numBars) {
                val fftIndex = (i * (bytes.size / 2)) / numBars
                val magnitude = abs(bytes[fftIndex].toFloat())
                val barHeight = (magnitude / 128f) * maxHeight
                canvas.drawRect(
                    i * barWidth,
                    mRect.height() - barHeight,
                    (i + 1) * barWidth - 2,
                    mRect.height().toFloat(),
                    mForePaint
                )
            }
        }
    }

    /**
     * 绘制折线图可视化，使用 FFT 数据
     * @param canvas 画布
     */
    private fun drawLineVisualizer(canvas: Canvas) {
        mFftBytes?.let { bytes ->
            mRect.set(0, 0, width, height)

            if (mPoints == null || mPoints!!.size < bytes.size * 4) {
                mPoints = FloatArray(bytes.size * 4)
            }

            val rectWidth = mRect.width().toFloat()
            val rectHeight = mRect.height().toFloat()
            val halfHeight = rectHeight / 2f

            for (i in 0 until bytes.size - 1) {
                val x1 = rectWidth * i / (bytes.size - 1).toFloat()
                val y1 = halfHeight + bytes[i] * 2f
                val x2 = rectWidth * (i + 1) / (bytes.size - 1).toFloat()
                val y2 = halfHeight + bytes[i + 1] * 2f

                mPoints!![i * 4] = x1
                mPoints!![i * 4 + 1] = y1
                mPoints!![i * 4 + 2] = x2
                mPoints!![i * 4 + 3] = y2
            }

            canvas.drawLines(mPoints!!, mForePaint)
        }
    }
}