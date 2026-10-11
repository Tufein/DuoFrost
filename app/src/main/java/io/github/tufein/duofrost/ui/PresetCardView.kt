package io.github.tufein.duofrost.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetVisuals
import io.github.tufein.duofrost.R
import kotlin.math.roundToInt

/** Saved-preset presentation only; lighting and editor actions belong to the caller. */
internal object PresetCardView {
    data class Palette(val surface: Int, val text: Int, val secondary: Int, val accent: Int, val outline: Int)

    fun heightDp(context: Context): Int =
        (176 + 64 * (context.resources.configuration.fontScale - 1f).coerceAtLeast(0f)).roundToInt()

    fun create(
        context: Context,
        preset: LedPreset,
        storageIndex: Int,
        effect: String,
        favorite: Boolean,
        palette: Palette,
        onSelect: () -> Unit,
        onEdit: () -> Unit,
        onOptions: () -> Unit,
        onFocusChanged: () -> Unit
    ): MaterialCardView {
        fun dp(value: Int) = SecondaryScreenUi.dp(context, value)
        val card = MaterialCardView(context).apply {
            tag = storageIndex
            setTag(R.id.gui_presetIdentity, preset.id)
            layoutParams = LinearLayout.LayoutParams(dp(160), dp(heightDp(context))).apply { marginEnd = dp(12) }
            radius = dp(16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(palette.surface)
            strokeColor = palette.outline
            strokeWidth = dp(1)
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(R.string.gui_edit_preset_accessibility, preset.name, effect)
            setOnClickListener { onSelect() }
            setOnLongClickListener { onEdit(); true }
            setOnFocusChangeListener { _, _ -> onFocusChanged() }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(24), dp(12), dp(12))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
        val artwork = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)) }
        val icon = ImageView(context).apply { layoutParams = FrameLayout.LayoutParams(-1, -1) }
        val emoji = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        PresetVisuals.bind(context, PresetVisuals.fromPreset(preset), icon, emoji, dp(48))
        artwork.addView(icon)
        artwork.addView(emoji)
        content.addView(artwork)
        content.addView(TextView(context).apply {
            text = preset.name
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        })
        content.addView(TextView(context).apply {
            text = effect
            textSize = 13f
            setTextColor(palette.secondary)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
        })
        // Colour samples describe the saved preset; they never simulate live LED output.
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(10) }
            for (savedColor in listOf(preset.color, preset.rightColor)) {
                addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(8)).apply { marginStart = dp(3); marginEnd = dp(3) }
                    background = GradientDrawable().apply {
                        cornerRadius = dp(4).toFloat()
                        setColor(ColorUtils.setAlphaComponent(savedColor, 255))
                        setStroke(dp(1), palette.outline)
                    }
                })
            }
        })
        card.addView(content)
        if (favorite) card.addView(TextView(context).apply {
            text = "★"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            setTextColor(palette.accent)
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.START)
        })
        card.addView(MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            tag = "preset-options-${preset.id}"
            text = "⋮"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f)
            insetTop = 0
            insetBottom = 0
            minimumWidth = 0
            minimumHeight = dp(48)
            setPadding(0, 0, 0, 0)
            setTextColor(palette.text)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            contentDescription = context.getString(R.string.library_actions, preset.name)
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END)
            setOnClickListener { onOptions() }
        })
        return card
    }
}
