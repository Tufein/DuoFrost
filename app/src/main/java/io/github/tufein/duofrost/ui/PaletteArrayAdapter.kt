package io.github.tufein.duofrost.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

open class PaletteArrayAdapter<T>(context: Context, resource: Int, items: List<T>) :
    ArrayAdapter<T>(context, resource, items) {
    private fun palette(view: View): View = view.apply {
        val theme = ThemeCatalog.selected(context)
        (this as? TextView)?.setTextColor(theme.textColor)
        setBackgroundColor(theme.surfaceColor)
    }
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        palette(super.getView(position, convertView, parent))
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        palette(super.getDropDownView(position, convertView, parent))
}
