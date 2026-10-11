package io.github.tufein.duofrost.ui

import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.graphics.ColorUtils

/** Shared foreground choices for accent surfaces, including imported UI palettes. */
internal object UiPalette {
    fun foreground(background: Int, underlay: Int): Int {
        val opaque = ColorUtils.compositeColors(background, underlay)
        return if (ColorUtils.calculateContrast(Color.WHITE, opaque) >= 4.5) Color.WHITE else Color.BLACK
    }

    fun enabledColors(enabled: Int, disabled: Int): ColorStateList = ColorStateList(
        arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(disabled, enabled)
    )
}
