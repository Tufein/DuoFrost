package io.github.tufein.duofrost

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.drawable.ColorDrawable
import android.widget.Toast
import io.github.tufein.duofrost.ui.*
import org.json.JSONObject
import java.util.UUID

class ThemeEditorActivity : ThemedActivity() {
    private val fields = linkedMapOf<String, EditText>()
    private lateinit var preview: LinearLayout
    private lateinit var previewText: TextView
    private lateinit var previewTokens: List<TextView>
    private lateinit var status: TextView
    private var editingId: String? = null
    private val prefs by lazy { getSharedPreferences("bifrost_prefs", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = ThemeCatalog.selected(this)
        editingId = savedInstanceState?.getString("editingId") ?: selected.id.takeIf { it.startsWith("custom_") }
        val (root, content) = SecondaryScreenUi.screen(this, "Theme editor",
            "Choose your colors. The preview changes only this screen. Save when text has at least 4.5:1 contrast on backgrounds, cards and controls.")
        val values = ThemeCatalog.encode(selected)
        for ((key, label) in listOf("name" to "Theme name", "background" to "Background", "card" to "Cards",
            "surface" to "Controls", "text" to "Text", "accent" to "Accent", "accentLight" to "Highlight")) {
            content.addView(SecondaryScreenUi.body(this, label))
            val input = EditText(this).apply {
                isSingleLine = true
                minHeight = SecondaryScreenUi.dp(this@ThemeEditorActivity, 48)
                setText(savedInstanceState?.getString(key) ?: if (key == "name" && editingId == null) "My theme" else values.getString(key))
                contentDescription = label
                filters = arrayOf(android.text.InputFilter.LengthFilter(if (key == "name") 40 else 7))
            }
            fields[key] = input
            content.addView(input)
        }
        preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            minimumHeight = SecondaryScreenUi.dp(this@ThemeEditorActivity, 100)
        }
        previewText = SecondaryScreenUi.body(this, "Your lighting, your colors", false)
        preview.addView(previewText)
        previewTokens = listOf("Cards", "Controls", "Accent", "Highlight").map { label ->
            SecondaryScreenUi.body(this, label, false).apply {
                minHeight = SecondaryScreenUi.dp(this@ThemeEditorActivity, 48)
                setPadding(16, 8, 16, 8)
                preview.addView(this)
            }
        }
        content.addView(preview)
        status = SecondaryScreenUi.body(this, "")
        content.addView(status)
        if (editingId != null) content.addView(SecondaryScreenUi.action(this, "Save changes", true) { save(false) })
        content.addView(SecondaryScreenUi.action(this, "Save as new theme", true) { save(true) })
        for (input in fields.values) input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderPreview()
            override fun afterTextChanged(s: Editable?) {}
        })
        setContentView(root)
        renderPreview()
    }
    override fun onResume() { super.onResume(); if (::preview.isInitialized) renderPreview() }
    private fun candidate(id: String): UiTheme = ThemeCatalog.decode(JSONObject().apply {
        put("id", id)
        fields.forEach { (key, value) -> put(key, value.text.toString().trim()) }
    })
    private fun renderPreview() {
        runCatching { candidate(editingId ?: "custom_preview") }.onSuccess {
            preview.background = ColorDrawable(it.backgroundColor)
            previewText.setTextColor(it.textColor)
            val backgrounds = listOf(it.cardColor, it.surfaceColor, it.accentColor, it.accentLightColor)
            previewTokens.forEachIndexed { index, label ->
                label.setBackgroundColor(backgrounds[index])
                label.setTextColor(if (index < 2) it.textColor else UiPalette.foreground(backgrounds[index], it.cardColor))
            }
            status.text = "Contrast check passed"
        }.onFailure { status.text = it.message }
    }
    private fun save(asNew: Boolean) {
        runCatching {
            val theme = candidate(if (asNew) "custom_" + UUID.randomUUID() else editingId!!)
            ThemeCatalog.save(prefs, theme)
            prefs.edit().putString("selected_ui_theme", theme.id).apply()
        }.onSuccess { finish() }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("editingId", editingId)
        fields.forEach { (key, input) -> outState.putString(key, input.text.toString()) }
        super.onSaveInstanceState(outState)
    }
}
