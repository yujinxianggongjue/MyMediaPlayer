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
import kotlin.math.hypot

/**
 * Custom View to display audio visualizations.
 * Supports waveform, bar, and line visualizations.
 */
class VisualizerView : View {
    private var mWaveformBytes: ByteArray? = null
    private var mFftBytes: ByteArray? = null
    private var mPoints: FloatArray? = null
    private var mBarHeights: FloatArray? = null
    private val mRect = Rect()

    private val mForePaint = Paint()

    private var type = 0 // 0: Waveform, 1: Bar, 2: Line

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        mWaveformBytes = null
        mFftBytes = null

        mForePaint.strokeWidth = 2f
        mForePaint.isAntiAlias = true
        mForePaint.color = Color.rgb(0, 128, 255)
    }

    /**
     * Update waveform data and redraw the view.
     */
    fun updateWaveform(bytes: ByteArray?) {
        mWaveformBytes = bytes
        invalidate()
    }

    /**
     * Update FFT data and redraw the view.
     */
    fun updateFft(bytes: ByteArray?) {
        mFftBytes = bytes
        invalidate()
    }

    /**
     * Toggle visualization type between Waveform, Bar, and Line.
     */
    fun toggleVisualizationType() {
        type = (type + 1) % 3
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Toggle visualization type on touch
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
            0 -> drawWaveform(canvas)
            1 -> drawBarVisualizer(canvas)
            2 -> drawLineVisualizer(canvas)
            else -> drawWaveform(canvas)
        }
    }

    private fun drawWaveform(canvas: Canvas) {
        // 绘制波形可视化
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

    private fun drawBarVisualizer(canvas: Canvas) {
        // Draw bar visualization using FFT data
        if (mFftBytes == null) return

        val numBars = 50 // Number of bars to display
        val barWidth = mRect.width() / numBars.toFloat()
        val maxHeight = mRect.height().toFloat()

        for (i in 0 until numBars) {
            val fftIndex = (i * (mFftBytes!!.size / 2)) / numBars
            val magnitude = abs(mFftBytes!![fftIndex].toFloat())
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

    private fun drawLineVisualizer(canvas: Canvas) {
        // 绘制使用 FFT 数据的线条可视化
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