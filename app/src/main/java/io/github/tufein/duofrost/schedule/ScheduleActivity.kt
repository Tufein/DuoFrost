package io.github.tufein.duofrost.schedule

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.ui.SecondaryScreenUi
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.MonthDay
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

class ScheduleActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE) }

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyLabel: TextView
    private var rules: MutableList<ScheduleRule> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Schedule"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        rules = ScheduleStore.load(prefs).toMutableList()
        setContentView(buildRoot())
        renderRules()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }


    private fun buildRoot(): View {
        val (scroll, root) = SecondaryScreenUi.screen(this, "Schedule",
            "Choose when your lighting runs. When the schedule is enabled, lights stay off " +
                "outside your rules. Dated rules take priority; otherwise the first matching rule applies.")

        root.addView(SecondaryScreenUi.toggle(this, "Enable schedule", ScheduleStore.isEnabled(prefs)) { checked ->
            ScheduleStore.setEnabled(prefs, checked)
            ScheduleApplier.apply(this)
        })

        root.addView(SecondaryScreenUi.action(this, "Add a rule", primary = true) { showRuleEditor(null) })

        emptyLabel = SecondaryScreenUi.body(this, "No rules yet. Add one to choose a preset and its active hours.").apply {
            setPadding(0, dp(24), 0, 0)
        }
        root.addView(emptyLabel)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(listContainer)

        return scroll
    }


    private fun renderRules() {
        listContainer.removeAllViews()
        emptyLabel.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE

        rules.forEachIndexed { index, rule ->
            listContainer.addView(buildRuleRow(index, rule))
        }
    }

    private fun buildRuleRow(index: Int, rule: ScheduleRule): View {
        val (card, row) = SecondaryScreenUi.card(this)
        row.addView(SecondaryScreenUi.body(this, rule.label.ifBlank { describeAction(rule.action) }, secondary = false))
        row.addView(SecondaryScreenUi.body(this, describe(rule)).apply { setPadding(0, dp(8), 0, dp(8)) })
        row.addView(SecondaryScreenUi.toggle(this, "Enabled", rule.enabled) { checked ->
            replace(index, rule.copy(enabled = checked))
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        controls.addView(SecondaryScreenUi.action(this, "Edit") { showRuleEditor(index) })
        controls.addView(SecondaryScreenUi.action(this, "Delete") {
            rules.removeAt(index)
            persist()
        })
        if (index > 0) {
            controls.addView(SecondaryScreenUi.action(this, "Move up") {
                val moved = rules.removeAt(index)
                rules.add(index - 1, moved)
                persist()
            })
        }
        for (childIndex in 0 until controls.childCount) {
            controls.getChildAt(childIndex).layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (childIndex > 0) marginStart = dp(8)
            }
        }

        row.addView(controls)
        return card
    }

    private fun describe(rule: ScheduleRule): String {
        val time = "${formatMinute(rule.startMinuteOfDay)} – ${formatMinute(rule.endMinuteOfDay)}"
        val season = rule.dateWindow?.let {
            ", ${formatMonthDay(it.start)} → ${formatMonthDay(it.end)}"
        } ?: ""
        val days = if (rule.daysOfWeek == ScheduleRule.ALL_DAYS) {
            "Every day"
        } else {
            rule.daysOfWeek.sortedBy { it.value }.joinToString(", ") {
                it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        }
        return "$days · $time$season · ${describeAction(rule.action)}"
    }

    private fun describeAction(action: ScheduleAction): String = when (action) {
        is ScheduleAction.PlayPreset -> "play “${action.presetName}”"
        ScheduleAction.TurnOff -> "lights off"
    }

    private fun formatMinute(minuteOfDay: Int): String =
        "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    private fun formatMonthDay(monthDay: MonthDay): String =
        "%02d/%02d".format(monthDay.monthValue, monthDay.dayOfMonth)

    private fun replace(index: Int, rule: ScheduleRule) {
        rules[index] = rule
        persist()
    }

    private fun persist() {
        ScheduleStore.save(prefs, rules)
        ScheduleApplier.apply(this)
        renderRules()
    }


    private fun showRuleEditor(index: Int?) {
        val existing = index?.let { rules[it] }
        val presetNames = loadPresetNames()

        var startMinute = existing?.startMinuteOfDay ?: 20 * 60
        var endMinute = existing?.endMinuteOfDay ?: 7 * 60
        var window = existing?.dateWindow

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }

        val labelField = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 16f
            minHeight = dp(48)
            setTextColor(SecondaryScreenUi.color(this@ScheduleActivity, R.color.bifrost_text))
            setText(existing?.label.orEmpty())
        }
        content.addView(TextInputLayout(this).apply {
            hint = "Name (optional)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(labelField)
        })

        val startButton = SecondaryScreenUi.action(this, "From ${formatMinute(startMinute)}") {}
        startButton.setOnClickListener {
            pickTime(startMinute) { picked ->
                startMinute = picked
                startButton.text = "From ${formatMinute(picked)}"
            }
        }
        content.addView(startButton)

        val endButton = SecondaryScreenUi.action(this, "Until ${formatMinute(endMinute)}") {}
        endButton.setOnClickListener {
            pickTime(endMinute) { picked ->
                endMinute = picked
                endButton.text = "Until ${formatMinute(picked)}"
            }
        }
        content.addView(endButton)

        content.addView(SecondaryScreenUi.body(this,
            "An end before the start continues overnight, using the starting day. Equal times cover the full day.")
            .apply { setPadding(0, dp(8), 0, dp(16)) })

        content.addView(SecondaryScreenUi.body(this, "Days", secondary = false))
        val selectedDays = existing?.daysOfWeek ?: ScheduleRule.ALL_DAYS
        val dayCheckboxes = DayOfWeek.values().map { day ->
            day to MaterialCheckBox(this).apply {
                text = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                textSize = 16f
                minHeight = dp(48)
                setTextColor(SecondaryScreenUi.color(this@ScheduleActivity, R.color.bifrost_text))
                isChecked = day in selectedDays
                content.addView(this)
            }
        }

        val actionLabels = presetNames + OFF_LABEL
        val actionSpinner = Spinner(this).apply {
            minimumHeight = dp(48)
            adapter = ArrayAdapter(
                this@ScheduleActivity,
                android.R.layout.simple_spinner_dropdown_item,
                actionLabels
            )
            val current = when (val action = existing?.action) {
                is ScheduleAction.PlayPreset -> actionLabels.indexOf(action.presetName)
                ScheduleAction.TurnOff -> actionLabels.lastIndex
                null -> 0
            }
            setSelection(current.coerceAtLeast(0))
        }
        content.addView(SecondaryScreenUi.body(this, "Lighting action", secondary = false)
            .apply { setPadding(0, dp(16), 0, dp(8)) })
        content.addView(actionSpinner)

        val seasonButton = SecondaryScreenUi.action(this, "All year round") {}
        fun renderSeason() {
            seasonButton.text = window
                ?.let { "Only ${formatMonthDay(it.start)} → ${formatMonthDay(it.end)}" }
                ?: "All year round"
        }
        renderSeason()
        seasonButton.setOnClickListener {
            if (window != null) {
                window = null
                renderSeason()
            } else {
                pickMonthDay("Season starts") { start ->
                    pickMonthDay("Season ends") { end ->
                        window = DateWindow(start, end)
                        renderSeason()
                    }
                }
            }
        }
        content.addView(seasonButton)
        content.addView(SecondaryScreenUi.body(this, "Tap a date range again to use all year."))

        val editorScroll = ScrollView(this).apply { addView(content) }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "New rule" else "Edit rule")
            .setView(editorScroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val days = dayCheckboxes.filter { it.second.isChecked }.map { it.first }.toSet()
                if (days.isEmpty()) {
                    Toast.makeText(this, "Choose at least one day.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val chosen = actionLabels[actionSpinner.selectedItemPosition]
                val action = if (actionSpinner.selectedItemPosition == presetNames.size) {
                    ScheduleAction.TurnOff
                } else {
                    ScheduleAction.PlayPreset(chosen)
                }

                val rule = ScheduleRule(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    label = labelField.text.toString().trim(),
                    enabled = existing?.enabled ?: true,
                    startMinuteOfDay = startMinute,
                    endMinuteOfDay = endMinute,
                    dateWindow = window,
                    action = action,
                    daysOfWeek = days
                )

                if (index == null) rules.add(rule) else rules[index] = rule
                persist()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun pickTime(initialMinuteOfDay: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinuteOfDay / 60,
            initialMinuteOfDay % 60,
            true
        ).show()
    }

    private fun pickMonthDay(title: String, onPicked: (MonthDay) -> Unit) {
        val monthPicker = NumberPicker(this).apply {
            contentDescription = "Month"
            minValue = 1
            maxValue = 12
            value = 1
        }
        val dayPicker = NumberPicker(this).apply {
            contentDescription = "Day"
            minValue = 1
            maxValue = 31
            value = 1
        }
        monthPicker.setOnValueChangedListener { _, _, month ->
            dayPicker.maxValue = MonthDay.of(month, 1).month.maxLength()
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(monthPicker)
            addView(dayPicker)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(row)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                runCatching { MonthDay.of(monthPicker.value, dayPicker.value) }
                    .getOrNull()
                    ?.let(onPicked)
            }
            .show()
    }

    private fun loadPresetNames(): List<String> {
        val raw = prefs.getString("presets_json", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.optString("name") }
            .filter { it.isNotBlank() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val OFF_LABEL = "Switch the LEDs off"
    }
}
