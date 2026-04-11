package com.recycling.toolsapp.view
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.FloatRange
import androidx.core.content.ContextCompat
import androidx.core.content.res.use
import com.recycling.toolsapp.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

class SmoothProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 绘制工具
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }

    // 动画控制
    private var animator: ValueAnimator? = null
    private var animDuration: Long = 800
    private var interpolatorType: Int = 0

    // 状态参数
    @FloatRange(from = 0.0, to = 100.0)
    var currentProgress: Float = 0f
        private set(value) {
            field = value.coerceIn(0f, 100f)
            invalidate()
        }

    init {
        initDefaultValues()
        loadAttributes(attrs)
    }

    private fun initDefaultValues() {
        bgPaint.color = Color.LTGRAY
        progressPaint.color = ContextCompat.getColor(context, android.R.color.holo_blue_light)
        textPaint.color = Color.WHITE
        textPaint.textSize = 14f.spToPx
    }

    private fun loadAttributes(attrs: AttributeSet?) {
        context.obtainStyledAttributes(attrs, R.styleable.SmoothProgressView).use { ta ->
            bgPaint.color = ta.getColor(
                R.styleable.SmoothProgressView_sm_backgroundColor,
                bgPaint.color
            )
            progressPaint.color = ta.getColor(
                R.styleable.SmoothProgressView_sm_progressColor,
                progressPaint.color
            )
            textPaint.color = ta.getColor(
                R.styleable.SmoothProgressView_sm_textColor,
                textPaint.color
            )
            textPaint.textSize = ta.getDimension(
                R.styleable.SmoothProgressView_sm_textSize,
                textPaint.textSize
            )
            animDuration = ta.getInt(
                R.styleable.SmoothProgressView_sm_animDuration,
                800
            ).toLong()
            interpolatorType = ta.getInt(
                R.styleable.SmoothProgressView_sm_animInterpolator,
                0
            )
            currentProgress = ta.getFloat(
                R.styleable.SmoothProgressView_sm_progress,
                0f
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val minHeight = (textPaint.textSize * 1.5f).toInt()
        val height = resolveSize(minHeight, heightMeasureSpec)
        setMeasuredDimension(widthMeasureSpec, height)
    }

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        drawProgress(canvas)
        drawProgressText(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val radius = height / 2f
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            radius, radius, bgPaint
        )
    }

    private fun drawProgress(canvas: Canvas) {
        val progressWidth = width * currentProgress / 100f
        val radius = height / 2f
        canvas.drawRoundRect(
            0f, 0f, progressWidth, height.toFloat(),
            radius, radius, progressPaint
        )
    }

    private fun drawProgressText(canvas: Canvas) {
        val formatter = DecimalFormat("#0.0%").apply {
            decimalFormatSymbols = DecimalFormatSymbols(Locale.US) // 固定小数点符号
        }
        val text = formatter.format(currentProgress / 100)
        val yPos = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, width / 2f, yPos, textPaint)
    }

    fun setProgress(
        target: Float,
        animate: Boolean = true,
        duration: Long = animDuration
    ) {
        if (abs(target - currentProgress) < 0.01f) return

        animator?.cancel()

        if (animate) {
            animator = ValueAnimator.ofFloat(currentProgress, target).apply {
                this.duration = duration
                interpolator = when (interpolatorType) {
                    1 -> android.view.animation.AccelerateDecelerateInterpolator()
                    2 -> android.view.animation.OvershootInterpolator(2f)
                    else -> android.view.animation.LinearInterpolator()
                }
                addUpdateListener {
                    currentProgress = it.animatedValue as Float
                }
                start()
            }
        } else {
            currentProgress = target
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    // 扩展属性：sp转px
    private val Float.spToPx: Float
        get() = this * resources.displayMetrics.scaledDensity
}