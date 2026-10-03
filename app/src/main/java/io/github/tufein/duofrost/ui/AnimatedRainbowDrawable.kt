package io.github.tufein.duofrost.ui

import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

class AnimatedRainbowDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var shader: LinearGradient? = null
    private val matrix = Matrix()
    private var animator: ValueAnimator? = null
    private var animValue: Float = 0f

    private val density = android.content.res.Resources.getSystem().displayMetrics.density
    private var period: Float = 0f

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)

        val w = bounds.width().toFloat()
        period = w * 3f

        shader = LinearGradient(
            0f, 0f,
            period, 0f,
            intArrayOf(
                Color.parseColor("#6366F1"),
                Color.parseColor("#4D8BFF"),
                Color.parseColor("#6366F1")
            ),
            floatArrayOf(
                0f,
                1f / 2f,
                1f
            ),
            Shader.TileMode.REPEAT
        )

        paint.shader = shader
        glowPaint.strokeWidth = 6f * density
    }

    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)

        val dx = period * animValue
        matrix.setTranslate(dx, 0f)
        shader?.setLocalMatrix(matrix)

        val radius = 16f * density
        canvas.drawRoundRect(rect, radius, radius, paint)

        val glowPhase = animValue
        val glowAlpha = (0.35f + 0.25f * sin(2f * PI * glowPhase)).toFloat()
        val alphaInt = (glowAlpha.coerceIn(0f, 1f) * 255).toInt()

        glowPaint.color = Color.argb(alphaInt, 230, 200, 255)

        val glowRect = RectF(rect).apply {
            inset(-8f * density, -8f * density)
        }
        canvas.drawRoundRect(
            glowRect,
            radius + 8f * density,
            radius + 8f * density,
            glowPaint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        glowPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        glowPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun start() {
        if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2200L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener {
                    animValue = it.animatedValue as Float
                    invalidateSelf()
                }
            }
        }
        animator?.start()
    }

    fun stop() {
        animator?.cancel()
        animValue = 0f
        invalidateSelf()
    }
}
