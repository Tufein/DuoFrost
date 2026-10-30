package io.github.tufein.duofrost.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import android.content.res.ColorStateList
import android.widget.TextView
import android.widget.EditText
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.tufein.duofrost.R

open class ThemedActivity : AppCompatActivity() {
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applyPalette()
    }
    override fun onResume() { super.onResume(); applyPalette() }
    private fun applyPalette() {
        val root = findViewById<ViewGroup>(android.R.id.content) ?: return
        if (root.childCount == 0) return
        val palette = ThemeCatalog.selected(this)
        root.getChildAt(0).setBackgroundColor(palette.backgroundColor)
        ThemeStyler.apply(root, palette)
        window.statusBarColor = palette.backgroundColor
        window.navigationBarColor = palette.backgroundColor
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = androidx.core.graphics.ColorUtils.calculateLuminance(palette.backgroundColor) > 0.5
            isAppearanceLightNavigationBars = isAppearanceLightStatusBars
        }
    }
}

object ThemeStyler {
    // Remember semantic roles rather than comparing against colors already changed
    // by a previous pass. Explicit swatches have no role and retain their RGB.
    private fun role(view: View, tag: Int, color: Int?, resources: List<Int>): Int? {
        (view.getTag(tag) as? Int)?.let { return it }
        return resources.firstOrNull {
            color == androidx.core.content.ContextCompat.getColor(view.context, it)
        }?.also { view.setTag(tag, it) }
    }

    fun apply(view: View, theme: UiTheme = ThemeCatalog.selected(view.context)) {
        when (view) {
            is MaterialCardView -> {
                view.setCardBackgroundColor(theme.cardColor)
                view.strokeColor = androidx.core.graphics.ColorUtils.blendARGB(theme.cardColor, theme.textColor, 0.35f)
            }
            is MaterialButton -> {
                // Colored LED/swatch controls keep their own backgrounds and contrast labels.
                val background = role(view, R.id.palette_background_role, view.backgroundTintList?.defaultColor,
                    listOf(R.color.bifrost_accent, R.color.bifrost_accent_light, R.color.bifrost_surface, R.color.bifrost_card))
                if (background == R.color.bifrost_accent || background == R.color.bifrost_accent_light) {
                    val accent = if (background == R.color.bifrost_accent) theme.accentColor else theme.accentLightColor
                    view.backgroundTintList = UiPalette.enabledColors(accent,
                        androidx.core.graphics.ColorUtils.blendARGB(theme.cardColor, accent, 0.3f))
                    view.setTextColor(UiPalette.enabledColors(UiPalette.foreground(accent, theme.cardColor), ThemeCatalog.secondary(theme)))
                } else if (background == R.color.bifrost_surface || background == R.color.bifrost_card) {
                    view.backgroundTintList = UiPalette.enabledColors(theme.surfaceColor, theme.cardColor)
                    view.setTextColor(UiPalette.enabledColors(theme.textColor, ThemeCatalog.secondary(theme)))
                    view.strokeColor = ColorStateList.valueOf(androidx.core.graphics.ColorUtils.blendARGB(theme.cardColor, theme.textColor, 0.35f))
                }
                view.iconTint = view.textColors
            }
            is SwitchMaterial -> {
                view.setTextColor(theme.textColor)
                view.thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(theme.accentLightColor, ThemeCatalog.secondary(theme)))
                view.trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(theme.accentColor, androidx.core.graphics.ColorUtils.blendARGB(theme.cardColor, theme.textColor, 0.3f)))
            }
            is EditText -> { view.setTextColor(theme.textColor); view.setHintTextColor(ThemeCatalog.secondary(theme)) }
            is TextView -> {
                when (role(view, R.id.palette_text_role, view.currentTextColor,
                    listOf(R.color.bifrost_text_secondary, R.color.bifrost_text))) {
                    R.color.bifrost_text_secondary -> view.setTextColor(ThemeCatalog.secondary(theme))
                    R.color.bifrost_text -> view.setTextColor(theme.textColor)
                }
            }
            is SeekBar -> {
                view.progressTintList = ColorStateList.valueOf(theme.accentLightColor)
                view.thumbTintList = ColorStateList.valueOf(theme.accentLightColor)
            }
        }
        if (view is ViewGroup) when (role(view, R.id.palette_background_role,
            (view.background as? ColorDrawable)?.color,
            listOf(R.color.bifrost_bg, R.color.bifrost_card, R.color.bifrost_surface))) {
            R.color.bifrost_bg -> view.setBackgroundColor(theme.backgroundColor)
            R.color.bifrost_card -> view.setBackgroundColor(theme.cardColor)
            R.color.bifrost_surface -> view.setBackgroundColor(theme.surfaceColor)
        }
        if (view is android.widget.AbsListView) view.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) { child?.let { apply(it, theme) } }
            override fun onChildViewRemoved(parent: View?, child: View?) {}
        })
        if (view is ViewGroup) for (i in 0 until view.childCount) apply(view.getChildAt(i), theme)
    }
}

/** Theme dialog contents and chrome at show time, including dynamically built editors. */
class ThemedDialogBuilder(context: android.content.Context) : MaterialAlertDialogBuilder(context) {
    override fun create(): AlertDialog = super.create().also { dialog ->
        val owner = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<androidx.lifecycle.LifecycleOwner>().firstOrNull()
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) dialog.dismiss()
        }
        dialog.window?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(view: View) {
                owner?.lifecycle?.removeObserver(lifecycleObserver)
            }
            override fun onViewAttachedToWindow(view: View) {
                owner?.lifecycle?.addObserver(lifecycleObserver)
                val theme = ThemeCatalog.selected(context)
                dialog.window?.setBackgroundDrawable(ColorDrawable(theme.cardColor))
                ThemeStyler.apply(view, theme)
                val buttonColor = if (androidx.core.graphics.ColorUtils.calculateContrast(theme.accentLightColor, theme.cardColor) >= 4.5)
                    theme.accentLightColor else theme.textColor
                for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL))
                    dialog.getButton(which)?.setTextColor(UiPalette.enabledColors(buttonColor, ThemeCatalog.secondary(theme)))
            }
        })
    }
}
