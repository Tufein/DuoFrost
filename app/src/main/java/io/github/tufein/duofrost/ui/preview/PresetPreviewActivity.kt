package io.github.tufein.duofrost.ui.preview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.ui.SecondaryScreenUi
import kotlin.math.min
import kotlin.math.roundToInt

/** Local illustration only: no LED command, capture permission, sensor read or preference edit. */
class PresetPreviewActivity : AppCompatActivity() {
    private var preset: LedPreset? = null
    private var animator: ValueAnimator? = null
    private var motionPaused = false
    private var elapsedMillis = 0L
    private lateinit var rings: VirtualSticksView
    private lateinit var status: TextView
    private lateinit var motionButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        motionPaused = savedInstanceState?.getBoolean(STATE_PAUSED) ?: false
        elapsedMillis = savedInstanceState?.getLong(STATE_ELAPSED) ?: 0L
        val raw = getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE).all["presets_json"] as? String
        preset = PresetPreviewSource.find(raw, intent.getStringExtra(EXTRA_PRESET_ID))
        val (screen, content) = SecondaryScreenUi.screen(this, getString(R.string.preview_title),
            getString(R.string.preview_description))
        screen.id = R.id.preview_root
        val selected = preset
        if (selected == null) {
            content.addView(SecondaryScreenUi.body(this, getString(R.string.preview_missing)))
            setContentView(screen)
            return
        }
        val (card, details) = SecondaryScreenUi.card(this)
        details.addView(SecondaryScreenUi.heading(this, selected.name.take(120)).apply { textSize = 20f })
        details.addView(SecondaryScreenUi.body(this, effectLabel(selected.animationType)))
        rings = VirtualSticksView(this).apply {
            id = R.id.preview_sticks
            contentDescription = getString(R.string.preview_virtual_sticks)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            frame = EffectPreviewSampler.sample(selected, elapsedMillis)
        }
        details.addView(rings, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)))
        details.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(stickLabel(R.string.preview_left), LinearLayout.LayoutParams(0, dp(32), 1f))
            addView(stickLabel(R.string.preview_right), LinearLayout.LayoutParams(0, dp(32), 1f))
        })
        details.addView(SecondaryScreenUi.body(this, getString(R.string.preview_brightness,
            (selected.brightness.coerceIn(0, 255) * 100.0 / 255.0).roundToInt())))
        status = SecondaryScreenUi.body(this, "").apply {
            id = R.id.preview_status
            setPadding(0, dp(16), 0, dp(8))
        }
        details.addView(status)
        motionButton = SecondaryScreenUi.action(this, "") {
            motionPaused = !motionPaused
            updateMotion()
        }.apply { id = R.id.preview_motion }
        details.addView(motionButton)
        content.addView(card)
        content.addView(SecondaryScreenUi.body(this, explanation(selected.animationType)).apply {
            setPadding(0, dp(16), 0, 0)
        })
        setContentView(screen)
    }

    override fun onResume() {
        super.onResume()
        if (::rings.isInitialized) updateMotion()
    }

    override fun onPause() {
        stopMotion()
        super.onPause()
    }

    override fun onDestroy() {
        stopMotion()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PAUSED, motionPaused)
        outState.putLong(STATE_ELAPSED, elapsedMillis)
        super.onSaveInstanceState(outState)
    }

    private fun updateMotion() {
        stopMotion()
        val selected = preset ?: return
        val supported = EffectPreviewSampler.canAnimate(selected.animationType)
        val allowed = ValueAnimator.areAnimatorsEnabled()
        motionButton.isVisible = supported && allowed
        motionButton.text = getString(if (motionPaused) R.string.preview_play else R.string.preview_pause)
        status.text = getString(when {
            !supported -> R.string.preview_still
            !allowed -> R.string.preview_reduced_motion
            motionPaused -> R.string.preview_paused
            else -> R.string.preview_playing
        })
        if (!supported || !allowed || motionPaused) return
        val startAt = elapsedMillis
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 60_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                elapsedMillis = startAt + animation.currentPlayTime
                rings.frame = EffectPreviewSampler.sample(selected, elapsedMillis)
            }
            start()
        }
    }

    private fun stopMotion() {
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }

    private fun stickLabel(label: Int) = SecondaryScreenUi.body(this, getString(label)).apply {
        gravity = android.view.Gravity.CENTER
    }

    private fun explanation(type: LedAnimationType): String = getString(when (type) {
        LedAnimationType.AMBIENT, LedAnimationType.AMBIAURORA, LedAnimationType.AUDIO_REACTIVE,
        LedAnimationType.BATTERY_INDICATOR, LedAnimationType.CPU_TEMPERATURE, LedAnimationType.PIPBOY ->
            R.string.preview_live_input
        LedAnimationType.STROBE, LedAnimationType.PULSE, LedAnimationType.SPARKLE, LedAnimationType.RAVE ->
            R.string.preview_flashing
        else -> R.string.preview_approximate
    })

    private fun effectLabel(type: LedAnimationType): String = getString(when (type) {
        LedAnimationType.AMBIENT -> R.string.preview_effect_ambient
        LedAnimationType.AUDIO_REACTIVE -> R.string.preview_effect_audio
        LedAnimationType.AMBIAURORA -> R.string.preview_effect_ambiaurora
        LedAnimationType.BATTERY_INDICATOR -> R.string.preview_effect_battery
        LedAnimationType.CPU_TEMPERATURE -> R.string.preview_effect_temperature
        LedAnimationType.STATIC -> R.string.preview_effect_static
        LedAnimationType.BREATH -> R.string.preview_effect_breath
        LedAnimationType.RAINBOW -> R.string.preview_effect_rainbow
        LedAnimationType.PULSE -> R.string.preview_effect_pulse
        LedAnimationType.STROBE -> R.string.preview_effect_strobe
        LedAnimationType.SPARKLE -> R.string.preview_effect_sparkle
        LedAnimationType.FADE_TRANSITION -> R.string.preview_effect_fade
        LedAnimationType.RAVE -> R.string.preview_effect_rave
        LedAnimationType.CHASE -> R.string.preview_effect_chase
        LedAnimationType.PIPBOY -> R.string.preview_effect_pipboy
    })

    private fun dp(value: Int) = SecondaryScreenUi.dp(this, value)

    companion object {
        const val EXTRA_PRESET_ID = "io.github.tufein.duofrost.preview.PRESET_ID"
        private const val STATE_PAUSED = "preview_paused"
        private const val STATE_ELAPSED = "preview_elapsed"
    }
}

/** Two abstract lighting rings; no physical device layout or brightness is implied. */
private class VirtualSticksView(context: Context) : View(context) {
    var frame = EffectPreviewSampler.Frame(-1, -1, -1, -1)
        set(value) {
            field = value
            invalidate()
        }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bounds = RectF()
    private val trackColor = SecondaryScreenUi.color(context, R.color.bifrost_surface)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = SecondaryScreenUi.dp(context, 12).toFloat()
        val radius = min(width / 5f, height / 2f - stroke).coerceAtLeast(0f)
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.BUTT
        fun ring(centerX: Float, top: Int, bottom: Int) {
            bounds.set(centerX - radius, height / 2f - radius, centerX + radius, height / 2f + radius)
            paint.color = trackColor
            canvas.drawOval(bounds, paint)
            paint.color = top
            canvas.drawArc(bounds, 182f, 176f, false, paint)
            paint.color = bottom
            canvas.drawArc(bounds, 2f, 176f, false, paint)
        }
        ring(width / 4f, frame.leftTop, frame.leftBottom)
        ring(width * 3f / 4f, frame.rightTop, frame.rightBottom)
    }
}
