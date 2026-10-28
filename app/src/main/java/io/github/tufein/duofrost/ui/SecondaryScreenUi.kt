package io.github.tufein.duofrost.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.tufein.duofrost.R

/** Shared, lightweight presentation for the programmatic utility screens. */
internal object SecondaryScreenUi {
    fun screen(activity: AppCompatActivity, title: String, description: String): Pair<View, LinearLayout> {
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(color(activity, R.color.bifrost_bg))
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 24))
        }
        scroll.addView(content)
        content.addView(action(activity, activity.getString(R.string.gui_back_to_lighting)) {
            activity.finish()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        content.addView(heading(activity, title).apply { setPadding(0, dp(activity, 20), 0, dp(activity, 8)) })
        content.addView(body(activity, description).apply { setPadding(0, 0, 0, dp(activity, 16)) })
        return safeScrollContainer(activity, scroll) to content
    }

    /** Keep native scroll/focus calculations inside the safe viewport. */
    fun safeScrollContainer(context: Context, scroll: ScrollView): View = FrameLayout(context).apply {
        addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        applyInsets(this)
    }

    fun applyInsets(root: View) {
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
            windowInsets
        }
        root.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }

    fun heading(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 26f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(color(context, R.color.bifrost_text))
        isAccessibilityHeading = true
    }

    fun body(context: Context, text: String, secondary: Boolean = true): TextView = TextView(context).apply {
        this.text = text
        textSize = if (secondary) 14f else 16f
        setTextColor(color(context, if (secondary) R.color.bifrost_text_secondary else R.color.bifrost_text))
        setLineSpacing(dp(context, 3).toFloat(), 1f)
    }

    fun action(context: Context, label: String, primary: Boolean = false, onClick: () -> Unit): MaterialButton =
        MaterialButton(context, null, if (primary) com.google.android.material.R.attr.materialButtonStyle
            else com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAllCaps = false
            minimumHeight = dp(context, 48)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(context, 14)
            val surface = color(context, R.color.bifrost_card)
            val foreground = color(context, R.color.bifrost_text)
            val accent = color(context, R.color.bifrost_accent)
            if (primary) {
                backgroundTintList = UiPalette.enabledColors(accent, ColorUtils.blendARGB(surface, accent, 0.3f))
                setTextColor(UiPalette.enabledColors(UiPalette.foreground(accent, surface),
                    color(context, R.color.bifrost_text_secondary)))
                strokeWidth = 0
            } else {
                backgroundTintList = UiPalette.enabledColors(color(context, R.color.bifrost_surface), surface)
                strokeColor = ColorStateList.valueOf(color(context, R.color.bifrost_outline))
                strokeWidth = dp(context, 1)
                setTextColor(UiPalette.enabledColors(foreground,
                    ColorUtils.blendARGB(surface, foreground, 0.45f)))
            }
            iconTint = textColors
            setOnClickListener { onClick() }
        }

    fun toggle(context: Context, label: String, checked: Boolean, onChange: (Boolean) -> Unit): SwitchMaterial =
        SwitchMaterial(context).apply {
            text = label
            textSize = 16f
            setTextColor(color(context, R.color.bifrost_text))
            minHeight = dp(context, 56)
            gravity = Gravity.CENTER_VERTICAL
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }

    fun card(context: Context): Pair<MaterialCardView, LinearLayout> {
        val card = MaterialCardView(context).apply {
            radius = dp(context, 20).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(color(context, R.color.bifrost_card))
            strokeColor = color(context, R.color.bifrost_outline)
            strokeWidth = dp(context, 1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 16) }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20))
        }
        card.addView(content)
        return card to content
    }

    fun color(context: Context, id: Int): Int = ThemeCatalog.color(context, id)
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
