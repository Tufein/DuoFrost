package io.github.tufein.duofrost.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import org.json.JSONArray
import org.json.JSONObject

data class HeaderThemePalette(
        val introHueStart: Float,
        val introHueSpan: Float,
        val introSaturation: Float,
        val introValue: Float,
        val settleHueStart: Float,
        val settleHueSpan: Float,
        val settleHueWobbleAmplitude: Float,
        val settleSaturationBase: Float,
        val settleSaturationWave: Float,
        val settleValueBase: Float,
        val settleValueWave: Float,
        val settleAlphaBase: Int,
        val settleAlphaWave: Int,
        val settlePhaseDegrees: Float
    )

data class UiTheme(
        val id: String,
        val label: String,
        val backgroundColor: Int,
        val cardColor: Int,
        val surfaceColor: Int,
        val textColor: Int,
        val accentColor: Int,
        val accentLightColor: Int,
        val headerPalette: HeaderThemePalette
    )

object ThemeCatalog {
    val builtIns = listOf(
        UiTheme(
            id = "classic",
            label = "Classic",
            backgroundColor = Color.parseColor("#0D111B"),
            cardColor = Color.parseColor("#161D2B"),
            surfaceColor = Color.parseColor("#202A3B"),
            textColor = Color.parseColor("#F1F4FB"),
            accentColor = Color.parseColor("#5965DB"),
            accentLightColor = Color.parseColor("#A2ACFF"),
            headerPalette = HeaderThemePalette(
                introHueStart = 0f,
                introHueSpan = 360f,
                introSaturation = 0.82f,
                introValue = 1f,
                settleHueStart = 18f,
                settleHueSpan = 300f,
                settleHueWobbleAmplitude = 10f,
                settleSaturationBase = 0.28f,
                settleSaturationWave = 0.14f,
                settleValueBase = 0.92f,
                settleValueWave = 0.08f,
                settleAlphaBase = 224,
                settleAlphaWave = 31,
                settlePhaseDegrees = 18f
            )
        ),
        UiTheme(
            id = "oled_purple",
            label = "OLED Purple",
            backgroundColor = Color.parseColor("#000000"),
            cardColor = Color.parseColor("#000000"),
            surfaceColor = Color.parseColor("#000000"),
            textColor = Color.parseColor("#C084FC"),
            accentColor = Color.parseColor("#A855F7"),
            accentLightColor = Color.parseColor("#C084FC"),
            headerPalette = HeaderThemePalette(
                introHueStart = 264f,
                introHueSpan = 64f,
                introSaturation = 0.9f,
                introValue = 0.98f,
                settleHueStart = 272f,
                settleHueSpan = 48f,
                settleHueWobbleAmplitude = 5f,
                settleSaturationBase = 0.5f,
                settleSaturationWave = 0.16f,
                settleValueBase = 0.76f,
                settleValueWave = 0.2f,
                settleAlphaBase = 236,
                settleAlphaWave = 19,
                settlePhaseDegrees = 12f
            )
        ),
        additionalTheme("oled_blue", "OLED Blue", "#000000", "#000000", "#000000", "#93C5FD", "#2563EB", "#60A5FA", 215f),
        additionalTheme("oled_green", "OLED Green", "#000000", "#000000", "#000000", "#86EFAC", "#16A34A", "#4ADE80", 145f),
        additionalTheme("oled_amber", "OLED Amber", "#000000", "#000000", "#000000", "#FDE68A", "#D97706", "#FBBF24", 40f),
        additionalTheme("oled_white", "OLED White", "#000000", "#000000", "#000000", "#F5F5F5", "#737373", "#E5E5E5", 0f, 0f),
        additionalTheme("midnight_teal", "Midnight Teal", "#081A1F", "#102A30", "#193B42", "#E6F6F5", "#008577", "#5CE0C4", 166f)
    )

    private fun additionalTheme(id: String, label: String, background: String, card: String,
        surface: String, text: String, accent: String, accentLight: String, hue: Float,
        saturation: Float = 0.7f): UiTheme = UiTheme(
        id, label, Color.parseColor(background), Color.parseColor(card), Color.parseColor(surface),
        Color.parseColor(text), Color.parseColor(accent), Color.parseColor(accentLight),
        HeaderThemePalette(
            introHueStart = hue - 20f, introHueSpan = 40f, introSaturation = saturation, introValue = 0.98f,
            settleHueStart = hue, settleHueSpan = 32f, settleHueWobbleAmplitude = 4f,
            settleSaturationBase = saturation * 0.65f, settleSaturationWave = saturation * 0.15f,
            settleValueBase = 0.85f, settleValueWave = 0.1f, settleAlphaBase = 236,
            settleAlphaWave = 19, settlePhaseDegrees = 12f
        )
    )


    const val CUSTOM_KEY = "custom_ui_themes_json"
    fun all(prefs: SharedPreferences): List<UiTheme> = builtIns + custom(prefs)
    fun selected(context: Context): UiTheme {
        val prefs = context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
        return all(prefs).firstOrNull { it.id == (prefs.all["selected_ui_theme"] as? String ?: "classic") } ?: builtIns.first()
    }
    private var cachedRaw: String? = null
    private var cachedCustom: List<UiTheme> = emptyList()
    @Synchronized fun custom(prefs: SharedPreferences): List<UiTheme> {
        val raw = prefs.all[CUSTOM_KEY] as? String ?: return emptyList()
        if (raw.length > 100_000) return emptyList()
        if (raw == cachedRaw) return cachedCustom
        val parsed = runCatching {
            val array = JSONArray(raw)
            require(array.length() <= 20)
            val result = (0 until array.length()).map { decode(array.getJSONObject(it)) }
            require(result.map { it.id }.distinct().size == result.size)
            result
        }.getOrDefault(emptyList())
        cachedRaw = raw
        cachedCustom = parsed
        return parsed
    }
    fun encode(theme: UiTheme): JSONObject = JSONObject().apply {
        put("id", theme.id); put("name", theme.label)
        listOf("background", "card", "surface", "text", "accent", "accentLight").zip(
            listOf(theme.backgroundColor, theme.cardColor, theme.surfaceColor, theme.textColor, theme.accentColor, theme.accentLightColor)
        ).forEach { (key, color) -> put(key, String.format("#%06X", color and 0xffffff)) }
    }
    fun decode(json: JSONObject): UiTheme {
        require(json.keys().asSequence().all { it in setOf("id", "name", "background", "card", "surface", "text", "accent", "accentLight") }) { "Unsupported palette fields" }
        val id = json.getString("id")
        val name = json.getString("name")
        require(id.matches(Regex("custom_[A-Za-z0-9-]{1,64}"))) { "Invalid theme identity" }
        require(name.isNotBlank() && name.length <= 40 && name.none { it.isISOControl() }) { "Choose a name of 1–40 characters" }
        fun color(key: String): Int {
            val value = json.getString(key)
            require(value.matches(Regex("#[0-9a-fA-F]{6}"))) { "Use #RRGGBB for $key" }
            return Color.parseColor(value)
        }
        val theme = UiTheme(id, name.trim(), color("background"), color("card"), color("surface"),
            color("text"), color("accent"), color("accentLight"), builtIns.first().headerPalette)
        require(listOf(theme.backgroundColor, theme.cardColor, theme.surfaceColor).all {
            ColorUtils.calculateContrast(theme.textColor, it) >= 4.5
        }) { "Text needs at least 4.5:1 contrast against background, cards and controls" }
        return theme
    }
    fun save(prefs: SharedPreferences, theme: UiTheme) {
        decode(encode(theme))
        val stored = prefs.all[CUSTOM_KEY]
        if (stored != null) {
            require(stored is String && stored.length <= 100_000) { "Custom theme data needs repair; save a backup first" }
            require(runCatching {
                val array = JSONArray(stored)
                require(array.length() <= 20)
                val parsed = (0 until array.length()).map { decode(array.getJSONObject(it)) }
                require(parsed.map { it.id }.distinct().size == parsed.size)
            }.isSuccess) { "Custom theme data needs repair; save a backup first" }
        }
        val items = custom(prefs).filter { it.id != theme.id } + theme
        require(items.size <= 20) { "You can save up to 20 custom themes" }
        prefs.edit().putString(CUSTOM_KEY, JSONArray(items.map(::encode)).toString()).apply()
    }
    fun color(context: Context, id: Int): Int {
        val t = selected(context)
        return when(id) {
            io.github.tufein.duofrost.R.color.bifrost_bg -> t.backgroundColor
            io.github.tufein.duofrost.R.color.bifrost_card -> t.cardColor
            io.github.tufein.duofrost.R.color.bifrost_surface -> t.surfaceColor
            io.github.tufein.duofrost.R.color.bifrost_text -> t.textColor
            io.github.tufein.duofrost.R.color.bifrost_text_secondary -> secondary(t)
            io.github.tufein.duofrost.R.color.bifrost_accent -> t.accentColor
            io.github.tufein.duofrost.R.color.bifrost_accent_light -> t.accentLightColor
            io.github.tufein.duofrost.R.color.bifrost_outline -> ColorUtils.blendARGB(t.cardColor, t.textColor, 0.35f)
            else -> androidx.core.content.ContextCompat.getColor(context, id)
        }
    }
    fun secondary(t: UiTheme): Int {
        val candidate = ColorUtils.blendARGB(t.textColor, t.cardColor, 0.25f)
        return if (listOf(t.backgroundColor, t.cardColor, t.surfaceColor).all {
            ColorUtils.calculateContrast(candidate, it) >= 4.5
        }) candidate else t.textColor
    }
}
