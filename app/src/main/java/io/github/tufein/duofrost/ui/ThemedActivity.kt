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
    fun apply(view: View, theme: UiTheme = ThemeCatalog.selected(view.context)) {
        val context = view.context
        fun resource(id: Int) = androidx.core.content.ContextCompat.getColor(context, id)
        when (view) {
            is MaterialCardView -> view.setCardBackgroundColor(theme.cardColor)
            is MaterialButton -> {
                // Colored LED/swatch controls keep their own backgrounds and contrast labels.
                val original = view.backgroundTintList?.defaultColor
                if (original == resource(R.color.bifrost_accent) || original == resource(R.color.bifrost_accent_light)) {
                    view.backgroundTintList = UiPalette.enabledColors(theme.accentLightColor,
                        androidx.core.graphics.ColorUtils.blendARGB(theme.cardColor, theme.accentLightColor, 0.3f))
                    view.setTextColor(UiPalette.enabledColors(UiPalette.foreground(theme.accentLightColor, theme.cardColor), ThemeCatalog.secondary(theme)))
                } else if (original == resource(R.color.bifrost_surface) || original == resource(R.color.bifrost_card)) {
                    view.backgroundTintList = UiPalette.enabledColors(theme.surfaceColor, theme.cardColor)
                    view.setTextColor(UiPalette.enabledColors(theme.textColor, ThemeCatalog.secondary(theme)))
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
                val current = view.currentTextColor
                if (current == resource(R.color.bifrost_text_secondary)) view.setTextColor(ThemeCatalog.secondary(theme))
                else if (current == resource(R.color.bifrost_text)) view.setTextColor(theme.textColor)
            }
            is SeekBar -> {
                view.progressTintList = ColorStateList.valueOf(theme.accentLightColor)
                view.thumbTintList = ColorStateList.valueOf(theme.accentLightColor)
            }
        }
        val color = if (view is ViewGroup) (view.background as? ColorDrawable)?.color else null
        if (color == resource(R.color.bifrost_bg)) view.setBackgroundColor(theme.backgroundColor)
        else if (color == resource(R.color.bifrost_card)) view.setBackgroundColor(theme.cardColor)
        else if (color == resource(R.color.bifrost_surface)) view.setBackgroundColor(theme.surfaceColor)
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
        dialog.window?.decorView?.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewDetachedFromWindow(view: View) {}
            override fun onViewAttachedToWindow(view: View) {
                val theme = ThemeCatalog.selected(context)
                dialog.window?.setBackgroundDrawable(ColorDrawable(theme.cardColor))
                ThemeStyler.apply(view, theme)
                for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL))
                    dialog.getButton(which)?.setTextColor(theme.accentLightColor)
            }
        })
    }
}
