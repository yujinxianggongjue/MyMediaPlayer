package com.example.mymediaplayer

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.example.mymediaplayer.R

/**
 * 一个自定义的 FrameLayout，用于根据指定的宽高比调整自身的尺寸。
 */
class AspectRatioFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var aspectRatio: Float = 16f / 9f // 默认宽高比为16:9

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AspectRatioFrameLayout)
            aspectRatio = typedArray.getFloat(R.styleable.AspectRatioFrameLayout_aspectRatio, 16f / 9f)
            typedArray.recycle()
        }
    }

    /**
     * 设置宽高比（宽度 / 高度）。
     */
    fun setAspectRatio(ratio: Float) {
        if (ratio > 0) {
            aspectRatio = ratio
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val originalWidth = MeasureSpec.getSize(widthMeasureSpec)
        val calculatedHeight = (originalWidth / aspectRatio).toInt()
        val finalHeightMeasureSpec = MeasureSpec.makeMeasureSpec(calculatedHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, finalHeightMeasureSpec)
    }
}