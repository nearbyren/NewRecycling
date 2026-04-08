package com.recycling.toolsapps.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.recycling.toolsapps.R
import kotlin.math.min

class CircleProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 画笔定义
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 进度相关变量
    private var progress: Int = 0
    private var maxProgress: Int = 100

    // 尺寸和颜色
    private var circleRadius: Float = 0f
    private var strokeWidth: Float = 20f
    private var backgroundColor: Int = Color.LTGRAY
    private var progressColor: Int = Color.WHITE
    private var textColor: Int = Color.WHITE
    private var textSize: Float = 50f

    // 绘制区域
    private val rectF = RectF()

    init {
        // 从XML属性获取自定义属性
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.CircleProgressView)
        backgroundColor = typedArray.getColor(
            R.styleable.CircleProgressView_cpv_backgroundColor,
            Color.LTGRAY
        )
        progressColor = typedArray.getColor(
            R.styleable.CircleProgressView_cpv_progressColor,
            Color.BLUE
        )
        textColor = typedArray.getColor(
            R.styleable.CircleProgressView_cpv_textColor,
            Color.BLACK
        )
        strokeWidth = typedArray.getDimension(
            R.styleable.CircleProgressView_cpv_strokeWidth,
            20f
        )
        textSize = typedArray.getDimension(
            R.styleable.CircleProgressView_cpv_textSize,
            50f
        )
        progress = typedArray.getInt(
            R.styleable.CircleProgressView_cpv_progress,
            0
        )
        maxProgress = typedArray.getInt(
            R.styleable.CircleProgressView_cpv_maxProgress,
            100
        )
        typedArray.recycle()

        setupPaints()
    }

    private fun setupPaints() {
        // 设置背景圆环画笔
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = strokeWidth
        backgroundPaint.color = backgroundColor

        // 设置进度圆环画笔
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeWidth = strokeWidth
        progressPaint.color = progressColor
        progressPaint.strokeCap = Paint.Cap.ROUND // 圆角端点

        // 设置文字画笔
        textPaint.color = textColor
        textPaint.textSize = textSize
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 计算圆形进度条的绘制区域
        val size = min(w, h)
        circleRadius = size / 2f - strokeWidth / 2f

        // 设置绘制区域
        val left = strokeWidth / 2f
        val top = strokeWidth / 2f
        val right = size - strokeWidth / 2f
        val bottom = size - strokeWidth / 2f
        rectF.set(left, top, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制背景圆环
        canvas.drawCircle(width / 2f, height / 2f, circleRadius, backgroundPaint)

        // 绘制进度圆环
        val sweepAngle = 360f * progress / maxProgress
        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)

        // 绘制中间文字
        val text = "$progress"
        val x = width / 2f
        val y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, x, y, textPaint)
    }

    /**
     * 设置当前进度值
     * @param progress 进度值 (0 - maxProgress)
     */
    fun setProgress(progress: Int) {
        this.progress = progress.coerceIn(0, maxProgress)
        invalidate() // 重绘视图
    }

    /**
     * 获取当前进度值
     */
    fun getProgress(): Int = progress

    /**
     * 设置最大进度值
     * @param maxProgress 最大进度值
     */
    fun setMaxProgress(maxProgress: Int) {
        this.maxProgress = maxProgress
        invalidate() // 重绘视图
    }

    /**
     * 获取最大进度值
     */
    fun getMaxProgress(): Int = maxProgress

    /**
     * 设置进度条颜色
     * @param color 颜色值
     */
    fun setProgressColor(color: Int) {
        progressPaint.color = color
        invalidate()
    }

    /**
     * 设置背景圆环颜色
     * @param color 颜色值
     */
    fun setCPVBackgroundColor(color: Int) {
        backgroundPaint.color = color
        invalidate()
    }

    /**
     * 设置文字颜色
     * @param color 颜色值
     */
    fun setTextColor(color: Int) {
        textPaint.color = color
        invalidate()
    }

    /**
     * 设置文字大小
     * @param size 文字大小（像素）
     */
    fun setTextSize(size: Float) {
        textPaint.textSize = size
        invalidate()
    }

    /**
     * 设置圆环宽度
     * @param width 圆环宽度（像素）
     */
    fun setStrokeWidth(width: Float) {
        strokeWidth = width
        backgroundPaint.strokeWidth = width
        progressPaint.strokeWidth = width
        // 需要重新计算绘制区域
        requestLayout()
    }
}