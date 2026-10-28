package io.github.tufein.duofrost.scenes

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.text.InputType
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.services.AppProfileManager
import io.github.tufein.duofrost.ui.SecondaryScreenUi
import io.github.tufein.duofrost.ui.ThemedActivity
import java.time.LocalDateTime
import java.time.ZoneId

class SceneSimulatorActivity : ThemedActivity() {
    private lateinit var time: EditText
    private lateinit var battery: EditText
    private lateinit var packageInput: EditText
    private lateinit var game: SwitchMaterial
    private lateinit var home: SwitchMaterial
    private lateinit var charging: SwitchMaterial
    private lateinit var temporary: SwitchMaterial
    private lateinit var output: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (root, content) = SecondaryScreenUi.screen(this, "Simulate scenes",
            "Preview which preset wins for an app, time and battery level. Nothing is applied to your LEDs. Times use " +
                ZoneId.systemDefault().id + ". App rules are evaluated as if Usage access were available.")
        fun input(label: String, key: String, initial: String, numeric: Boolean = false): EditText {
            content.addView(SecondaryScreenUi.body(this, label))
            return EditText(this).apply {
                contentDescription = label; minHeight = SecondaryScreenUi.dp(this@SceneSimulatorActivity, 48)
                isSingleLine = true
                if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
                setText(savedInstanceState?.getString(key) ?: initial)
                filters = arrayOf(android.text.InputFilter.LengthFilter(if (numeric) 3 else 255))
                content.addView(this)
            }
        }
        time = input("Local date and time (YYYY-MM-DDTHH:MM)", "time", LocalDateTime.now().withSecond(0).withNano(0).toString())
        battery = input("Battery percent (0–100)", "battery", "50", true)
        packageInput = input("App package", "package", "")
        val apps = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val picker = Spinner(this).apply {
            adapter = io.github.tufein.duofrost.ui.PaletteArrayAdapter(this@SceneSimulatorActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Choose an app (or enter its package)") + apps.map { it.loadLabel(packageManager).toString() })
            minimumHeight = SecondaryScreenUi.dp(this@SceneSimulatorActivity, 48)
        }
        content.addView(picker)
        picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (position > 0) {
                    val pkg = apps[position - 1].activityInfo.packageName
                    packageInput.setText(pkg)
                    game.isChecked = AppProfileManager(getSharedPreferences("bifrost_prefs", MODE_PRIVATE)).isGamePackage(this@SceneSimulatorActivity, pkg)
                }
            }
        }
        fun toggle(label: String, key: String): SwitchMaterial = SecondaryScreenUi.toggle(this, label,
            savedInstanceState?.getBoolean(key) ?: false) {}.also(content::addView)
        game = toggle("Treat as a game", "game")
        home = toggle("Treat as launcher / Home", "home")
        charging = toggle("Charging", "charging")
        temporary = toggle("Include the currently active temporary hold", "temporary")
        content.addView(SecondaryScreenUi.action(this, "Simulate", true) { simulate() })
        output = SecondaryScreenUi.body(this, savedInstanceState?.getString("output") ?: "", false)
        output.accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        content.addView(output)
        content.addView(SecondaryScreenUi.body(this, "This previews Smart Scenes and app profiles. Schedules, alerts, charging overlays, external control, mute and additional output limits can affect actual lighting. A temporary hold uses its current remaining duration, not the simulated date."))
        setContentView(root)
    }
    private fun simulate() {
        runCatching {
            val date = LocalDateTime.parse(time.text.toString().trim())
            val percent = battery.text.toString().toIntOrNull()
            require(percent != null && percent in 0..100) { "Enter a battery percentage from 0 to 100" }
            val pkg = packageInput.text.toString().trim().takeIf { it.isNotEmpty() }
            require(pkg == null || SceneLimits.validPackage(pkg)) { "Enter a valid app package" }
            val prefs = getSharedPreferences("bifrost_prefs", MODE_PRIVATE)
            val store = SceneStore(prefs)
            require(!store.isReadOnly) { "Scene data needs repair before it can be simulated" }
            val profiles = AppProfileManager(prefs)
            val hold = if (temporary.isChecked) store.getTemporaryScene(SystemClock.elapsedRealtime(),
                Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1))?.presetId else null
            // Reading a simulation must not run the repository's identity migration.
            val stored = prefs.all["presets_json"]
            require(stored == null || stored is String && stored.length <= 2_000_000) { "Preset data cannot be simulated" }
            val array = org.json.JSONArray(stored as? String ?: "[]")
            require(array.length() <= 10_000) { "Too many presets to simulate" }
            val presets = (0 until array.length()).map { io.github.tufein.duofrost.PresetCodec.decode(array.getJSONObject(it), it) }
            val result = SceneSimulator.evaluate(presets,
                if (store.isEnabled) store.loadRules() else emptyList(), store.loadGroups(),
                SceneContext(date, pkg, game.isChecked, home.isChecked, percent, charging.isChecked),
                profiles.getMappings(), profiles.isEnabled, profiles.isGameSceneEnabled,
                profiles.gameScenePresetName, prefs.getString("last_preset_name", null), hold, packageName)
            output.text = "Winning preset: " + (result.preset?.name ?: "No preset / lighting suppressed") +
                "\nSource: " + result.source.name.replace('_', ' ') +
                (if (result.source == SceneSelectionPolicy.Source.RULE) "\nWinning rule: " + result.ruleName else "") +
                "\nScene brightness limit: " + (result.limit?.let { "$it%" } ?: "None") +
                "\nMatching rules: " + result.matching.joinToString().ifEmpty { "None" } +
                if (!store.isEnabled) "\nSmart Scenes are disabled" else ""
        }.onFailure { output.text = it.message ?: "Check the date, time and battery inputs" }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("time", time.text.toString()); outState.putString("battery", battery.text.toString())
        outState.putString("package", packageInput.text.toString()); outState.putString("output", output.text.toString())
        outState.putBoolean("game", game.isChecked); outState.putBoolean("home", home.isChecked)
        outState.putBoolean("charging", charging.isChecked); outState.putBoolean("temporary", temporary.isChecked)
        super.onSaveInstanceState(outState)
    }
}
