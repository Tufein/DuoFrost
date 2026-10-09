package io.github.tufein.duofrost.scenes

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.InputFilter
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.services.LightingStateEvents
import io.github.tufein.duofrost.ui.SecondaryScreenUi
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

/** Scene editing never starts lighting. The running service observes saved preferences. */
class ScenesActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE) }
    private val store by lazy { SceneStore(prefs) }
    private val presets by lazy { PresetRepository(prefs) }
    private lateinit var enableSwitch: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView
    private lateinit var ruleList: LinearLayout
    private lateinit var usageButton: View
    private lateinit var groupCount: TextView
    private lateinit var temporaryText: TextView
    private lateinit var resumeButton: View
    private var syncing = false
    private var appsLoadVersion = 0
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { if (!isFinishing && !isDestroyed) render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildScreen())
        render()
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        render()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        super.onPause()
    }

    override fun onDestroy() {
        appsLoadVersion++
        super.onDestroy()
    }

    private fun buildScreen(): View {
        val (scroll, content) = SecondaryScreenUi.screen(this,
            getString(R.string.scenes_title), getString(R.string.scenes_description))
        scroll.id = R.id.scenes_root
        val (statusCard, statusContent) = SecondaryScreenUi.card(this)
        enableSwitch = SecondaryScreenUi.toggle(this, getString(R.string.scenes_enable), false) { checked ->
            if (!syncing) {
                store.isEnabled = checked
                changed()
                if (checked && needsUsageAccess()) showUsageExplanation()
            }
        }.apply { id = R.id.scenes_enable }
        statusContent.addView(enableSwitch)
        statusText = body("").apply { id = R.id.scenes_status }
        statusContent.addView(statusText)
        usageButton = action(R.string.scenes_usage_action) { showUsageExplanation() }.apply {
            id = R.id.scenes_usage_access
        }
        statusContent.addView(usageButton)
        content.addView(statusCard)

        val (temporaryCard, temporaryContent) = SecondaryScreenUi.card(this)
        temporaryContent.addView(section(R.string.scenes_temporary_heading))
        temporaryContent.addView(body(getString(R.string.scenes_temporary_description)))
        temporaryText = body("").apply { setPadding(0, dp(8), 0, dp(8)) }
        temporaryContent.addView(temporaryText)
        temporaryContent.addView(action(R.string.scenes_temporary_action) { showTemporaryEditor() }
            .apply { id = R.id.scenes_temporary })
        resumeButton = action(R.string.scenes_resume) {
            store.clearTemporaryScene()
            changed()
        }.apply { id = R.id.scenes_resume }
        temporaryContent.addView(resumeButton)
        content.addView(temporaryCard)

        content.addView(section(R.string.scenes_rules_heading).apply { setPadding(0, dp(24), 0, dp(8)) })
        content.addView(body(getString(R.string.scenes_rules_help)))
        content.addView(action(R.string.scenes_add_rule, primary = true) { showRuleEditor(null) }
            .apply { id = R.id.scenes_add_rule })
        emptyText = body(getString(R.string.scenes_empty)).apply {
            id = R.id.scenes_empty
            setPadding(0, dp(16), 0, dp(16))
        }
        content.addView(emptyText)
        ruleList = vertical().apply { id = R.id.scenes_rules_list }
        content.addView(ruleList)

        val (groupCard, groupContent) = SecondaryScreenUi.card(this)
        groupContent.addView(section(R.string.scenes_groups))
        groupContent.addView(body(getString(R.string.scenes_groups_description)))
        groupCount = body("").apply { setPadding(0, dp(8), 0, dp(8)) }
        groupContent.addView(groupCount)
        groupContent.addView(action(R.string.scenes_manage_groups) { showGroups() }
            .apply { id = R.id.scenes_groups })
        content.addView(groupCard)
        return scroll
    }

    private fun render() {
        if (!::enableSwitch.isInitialized) return
        val rules = store.loadRules()
        syncing = true
        enableSwitch.isChecked = store.isEnabled
        syncing = false
        enableSwitch.isEnabled = !store.isReadOnly
        statusText.setText(when {
            store.isReadOnly -> R.string.scenes_read_only
            !store.isEnabled -> R.string.scenes_disabled
            rules.none { it.enabled } -> R.string.scenes_enabled_empty
            else -> R.string.scenes_enabled
        })
        usageButton.isVisible = needsUsageAccess()
        emptyText.isVisible = rules.isEmpty()
        groupCount.text = getString(R.string.scenes_group_count, store.loadGroups().size)
        renderTemporary()
        ruleList.removeAllViews()
        rules.sortedWith(compareByDescending<SceneRule> { it.priority }.thenBy { it.id }).forEach { rule ->
            val (card, content) = SecondaryScreenUi.card(this)
            content.addView(body(rule.name, secondary = false))
            content.addView(body(describe(rule)).apply { setPadding(0, dp(8), 0, dp(8)) })
            content.addView(SecondaryScreenUi.toggle(this, getString(R.string.scenes_enabled_label), rule.enabled) { checked ->
                store.saveRules(store.loadRules().map { if (it.id == rule.id) it.copy(enabled = checked) else it })
                changed()
                if (checked && store.isEnabled && rule.target != SceneTarget.ANY && !hasUsageAccess()) showUsageExplanation()
            })
            content.addView(buttonRow(
                action(R.string.scenes_edit) { showRuleEditor(rule) },
                action(R.string.scenes_delete) { confirmDeleteRule(rule) }
            ))
            ruleList.addView(card)
        }
    }

    private fun renderTemporary() {
        val bootCount = bootCount()
        val temporary = if (bootCount >= 0) store.getTemporaryScene(SystemClock.elapsedRealtime(), bootCount) else null
        resumeButton.isVisible = temporary != null
        temporaryText.text = if (temporary == null) getString(R.string.scenes_temporary_inactive) else {
            val name = presets.list().firstOrNull { it.id == temporary.presetId }?.name
                ?: getString(R.string.scenes_missing_preset)
            val minutes = ceil((temporary.expiresAtElapsed - SystemClock.elapsedRealtime()) / 60_000.0)
                .toInt().coerceAtLeast(1)
            getString(R.string.scenes_temporary_active, name, minutes)
        }
    }

    private fun describe(rule: SceneRule): String = buildList {
        add(rule.presetId?.let { id -> presets.list().firstOrNull { it.id == id }?.name
            ?: getString(R.string.scenes_missing_preset) } ?: getString(R.string.scenes_only_dimming))
        add(when (rule.target) {
            SceneTarget.ANY -> getString(R.string.scenes_target_any)
            SceneTarget.GAMES -> getString(R.string.scenes_target_games)
            SceneTarget.GROUP -> store.loadGroups().firstOrNull { it.id == rule.groupId }?.let {
                getString(R.string.scenes_target_group, it.name)
            } ?: getString(R.string.scenes_group_missing)
        })
        if (rule.startMinute != null && rule.endMinute != null) add("${time(rule.startMinute)} – ${time(rule.endMinute)}")
        if (rule.daysOfWeek.isNotEmpty()) add(daysDescription(rule.daysOfWeek))
        rule.maxBatteryPercent?.let { add(getString(R.string.scenes_summary_battery, it)) }
        if (rule.charging != ChargingCondition.ANY) add(chargingLabel(rule.charging))
        rule.maxBrightnessPercent?.let { add(getString(R.string.scenes_summary_limit, it)) }
        add(getString(R.string.scenes_summary_priority, rule.priority))
    }.joinToString(" · ")

    private fun showRuleEditor(existing: SceneRule?) {
        if (store.isReadOnly) { toast(R.string.scenes_read_only); return }
        val presetList = presets.list()
        val groups = store.loadGroups()
        var start = existing?.startMinute ?: 20 * 60
        var end = existing?.endMinute ?: 7 * 60
        var days = existing?.daysOfWeek?.takeIf { it.isNotEmpty() } ?: (1..7).toSet()
        val content = editorContent()
        val name = field(R.string.scenes_name, existing?.name.orEmpty(), R.id.scenes_rule_name)
        content.addView(name.first)

        val missingPreset = existing?.presetId != null && presetList.none { it.id == existing.presetId }
        val presetOptions = listOf(getString(R.string.scenes_only_dimming)) + presetList.map { it.name } +
            if (missingPreset) listOf(getString(R.string.scenes_missing_preset)) else emptyList()
        content.addView(label(R.string.scenes_choose_effect))
        val effect = spinner(presetOptions).apply {
            id = R.id.scenes_rule_effect
            setSelection(if (missingPreset) presetOptions.lastIndex else
                existing?.presetId?.let { id -> presetList.indexOfFirst { it.id == id } + 1 } ?: 0)
        }
        content.addView(effect)
        val missingGroup = existing?.target == SceneTarget.GROUP && groups.none { it.id == existing.groupId }
        val targetOptions = listOf(getString(R.string.scenes_target_any), getString(R.string.scenes_target_games)) +
            groups.map { getString(R.string.scenes_target_group, it.name) } +
            if (missingGroup) listOf(getString(R.string.scenes_group_missing)) else emptyList()
        content.addView(label(R.string.scenes_target))
        val target = spinner(targetOptions).apply {
            id = R.id.scenes_rule_target
            setSelection(when (existing?.target) {
                SceneTarget.GAMES -> 1
                SceneTarget.GROUP -> groups.indexOfFirst { it.id == existing.groupId }.let { if (it < 0) targetOptions.lastIndex else it + 2 }
                else -> 0
            })
        }
        content.addView(target)
        if (groups.isEmpty()) content.addView(body(getString(R.string.scenes_no_groups)))
        content.addView(section(R.string.scenes_conditions).apply { setPadding(0, dp(16), 0, dp(8)) })

        val hours = vertical()
        val startButton = actionLabel(getString(R.string.scenes_time_from, time(start))) {}
        startButton.setOnClickListener { pickTime(start) { start = it; startButton.text = getString(R.string.scenes_time_from, time(it)) } }
        val endButton = actionLabel(getString(R.string.scenes_time_until, time(end))) {}
        endButton.setOnClickListener { pickTime(end) { end = it; endButton.text = getString(R.string.scenes_time_until, time(it)) } }
        hours.addView(buttonRow(startButton, endButton))
        hours.addView(body(getString(R.string.scenes_time_help)))
        val hasHours = SecondaryScreenUi.toggle(this, getString(R.string.scenes_time_enabled), existing?.startMinute != null) {
            hours.isVisible = it
        }.apply { id = R.id.scenes_rule_time }
        hours.isVisible = hasHours.isChecked
        content.addView(hasHours)
        content.addView(hours)

        val dayButton = actionLabel(getString(R.string.scenes_days, daysDescription(days))) {}
        dayButton.setOnClickListener { chooseDays(days) { days = it; dayButton.text = getString(R.string.scenes_days, daysDescription(days)) } }
        content.addView(dayButton)

        val batteryField = field(R.string.scenes_battery_percent, (existing?.maxBatteryPercent ?: 25).toString(),
            R.id.scenes_rule_battery_percent, numeric = true)
        val hasBattery = SecondaryScreenUi.toggle(this, getString(R.string.scenes_battery_enabled), existing?.maxBatteryPercent != null) {
            batteryField.first.isVisible = it
        }.apply { id = R.id.scenes_rule_battery }
        batteryField.first.isVisible = hasBattery.isChecked
        content.addView(hasBattery)
        content.addView(batteryField.first)
        content.addView(label(R.string.scenes_charging))
        val charging = spinner(ChargingCondition.values().map(::chargingLabel)).apply {
            setSelection((existing?.charging ?: ChargingCondition.ANY).ordinal)
        }
        content.addView(charging)

        val brightnessField = field(R.string.scenes_brightness_percent,
            (existing?.maxBrightnessPercent ?: 50).toString(), R.id.scenes_rule_brightness_percent, numeric = true)
        val hasBrightness = SecondaryScreenUi.toggle(this, getString(R.string.scenes_brightness_enabled), existing?.maxBrightnessPercent != null) {
            brightnessField.first.isVisible = it
        }.apply { id = R.id.scenes_rule_brightness }
        brightnessField.first.isVisible = hasBrightness.isChecked
        content.addView(hasBrightness)
        content.addView(brightnessField.first)

        val priorityLabel = body(getString(R.string.scenes_priority, existing?.priority ?: 0), secondary = false)
            .apply { setPadding(0, dp(16), 0, 0) }
        val priority = SeekBar(this).apply {
            id = R.id.scenes_rule_priority
            max = 100
            progress = existing?.priority ?: 0
            minimumHeight = dp(48)
            contentDescription = getString(R.string.scenes_priority, progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    priorityLabel.text = getString(R.string.scenes_priority, progress)
                    seekBar.contentDescription = priorityLabel.text
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        content.addView(priorityLabel)
        content.addView(priority)
        content.addView(body(getString(R.string.scenes_priority_help)))

        val dialog = editorDialog(if (existing == null) R.string.scenes_new_rule else R.string.scenes_edit_rule, content)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val sceneName = name.second.text.toString().trim()
                if (sceneName.isBlank()) { name.first.error = getString(R.string.scenes_name_required); name.second.requestFocus(); return@setOnClickListener }
                if (store.loadRules().any { it.id != existing?.id && it.name.equals(sceneName, ignoreCase = true) }) {
                    name.first.error = getString(R.string.scenes_duplicate_name); name.second.requestFocus(); return@setOnClickListener
                }
                val battery = if (hasBattery.isChecked) validPercent(batteryField) ?: return@setOnClickListener else null
                val brightness = if (hasBrightness.isChecked) validPercent(brightnessField) ?: return@setOnClickListener else null
                if (missingPreset && effect.selectedItemPosition == presetOptions.lastIndex) {
                    toast(R.string.scenes_choose_available_preset); return@setOnClickListener
                }
                if (missingGroup && target.selectedItemPosition == targetOptions.lastIndex) {
                    toast(R.string.scenes_choose_available_group); return@setOnClickListener
                }
                val selectedPreset = presetList.getOrNull(effect.selectedItemPosition - 1)?.id
                if (selectedPreset == null && brightness == null) { toast(R.string.scenes_action_required); return@setOnClickListener }
                val selectedTarget = when (target.selectedItemPosition) { 0 -> SceneTarget.ANY; 1 -> SceneTarget.GAMES; else -> SceneTarget.GROUP }
                val rule = SceneRule(id = existing?.id ?: UUID.randomUUID().toString(), name = sceneName,
                    enabled = existing?.enabled ?: true, presetId = selectedPreset,
                    target = selectedTarget, groupId = groups.getOrNull(target.selectedItemPosition - 2)?.id,
                    startMinute = start.takeIf { hasHours.isChecked }, endMinute = end.takeIf { hasHours.isChecked },
                    daysOfWeek = days, maxBatteryPercent = battery,
                    charging = ChargingCondition.values()[charging.selectedItemPosition],
                    maxBrightnessPercent = brightness, priority = priority.progress)
                val rules = store.loadRules().toMutableList()
                val index = rules.indexOfFirst { it.id == rule.id }
                if (index < 0) rules.add(rule) else rules[index] = rule
                if (!store.saveRules(rules)) { toast(R.string.scenes_save_failed); return@setOnClickListener }
                changed()
                dialog.dismiss()
                if (store.isEnabled && selectedTarget != SceneTarget.ANY && !hasUsageAccess()) showUsageExplanation()
            }
        }
        dialog.show()
    }

    private fun showTemporaryEditor() {
        if (store.isReadOnly) { toast(R.string.scenes_read_only); return }
        val list = presets.list()
        if (list.isEmpty()) { toast(R.string.scenes_no_presets); return }
        val boot = bootCount()
        if (boot < 0) { toast(R.string.scenes_temporary_unavailable); return }
        val content = editorContent()
        content.addView(label(R.string.scenes_preset))
        val preset = spinner(list.map { it.name })
        content.addView(preset)
        content.addView(label(R.string.scenes_temporary_duration))
        val minutes = listOf(15, 30, 60, 120)
        val duration = spinner(minutes.map { getString(R.string.scenes_minutes, it) })
        content.addView(duration)
        MaterialAlertDialogBuilder(this).setTitle(R.string.scenes_temporary_heading)
            .setView(editorScroll(content)).setNegativeButton(R.string.scenes_cancel, null)
            .setPositiveButton(R.string.scenes_temporary_start) { _, _ ->
                if (store.setTemporaryScene(list[preset.selectedItemPosition].id, minutes[duration.selectedItemPosition],
                    SystemClock.elapsedRealtime(), boot)) {
                    changed()
                    toast(R.string.scenes_temporary_saved)
                } else toast(R.string.scenes_save_failed)
            }.show()
    }

    private fun showGroups() {
        if (store.isReadOnly) { toast(R.string.scenes_read_only); return }
        val content = editorContent()
        val list = vertical()
        fun refreshGroups() {
            list.removeAllViews()
            val groups = store.loadGroups()
            if (groups.isEmpty()) list.addView(body(getString(R.string.scenes_group_empty)))
            groups.forEach { group ->
                val (card, row) = SecondaryScreenUi.card(this)
                row.addView(body(group.name, secondary = false))
                row.addView(body(getString(R.string.scenes_group_apps, group.packages.size)))
                row.addView(buttonRow(action(R.string.scenes_edit) { showGroupEditor(group, ::refreshGroups) },
                    action(R.string.scenes_delete) { confirmDeleteGroup(group, ::refreshGroups) }))
                list.addView(card)
            }
        }
        content.addView(action(R.string.scenes_add_group, primary = true) { showGroupEditor(null, ::refreshGroups) }
            .apply { id = R.id.scenes_add_group })
        content.addView(list)
        refreshGroups()
        MaterialAlertDialogBuilder(this).setTitle(R.string.scenes_groups).setView(editorScroll(content))
            .setPositiveButton(R.string.scenes_done, null).show()
    }

    private fun showGroupEditor(existing: AppGroup?, onSaved: () -> Unit) {
        val content = editorContent()
        val name = field(R.string.scenes_group_name, existing?.name.orEmpty(), R.id.scenes_group_name)
        content.addView(name.first)
        var selected = existing?.packages.orEmpty()
        val apps = actionLabel(getString(R.string.scenes_group_selected, selected.size)) {}.apply { id = R.id.scenes_group_apps }
        apps.setOnClickListener { chooseApps(selected) { selected = it; apps.text = getString(R.string.scenes_group_selected, it.size) } }
        content.addView(apps)
        val dialog = editorDialog(if (existing == null) R.string.scenes_new_group else R.string.scenes_edit_group, content)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val groupName = name.second.text.toString().trim()
                if (groupName.isBlank()) { name.first.error = getString(R.string.scenes_group_name_required); name.second.requestFocus(); return@setOnClickListener }
                if (store.loadGroups().any { it.id != existing?.id && it.name.equals(groupName, ignoreCase = true) }) {
                    name.first.error = getString(R.string.scenes_group_duplicate); name.second.requestFocus(); return@setOnClickListener
                }
                if (selected.isEmpty()) { toast(R.string.scenes_group_apps_required); return@setOnClickListener }
                if (selected.size > SceneLimits.MAX_PACKAGES_PER_GROUP) { toast(R.string.scenes_group_apps_limit); return@setOnClickListener }
                val group = AppGroup(existing?.id ?: UUID.randomUUID().toString(), groupName, selected.toSet())
                val groups = store.loadGroups().toMutableList()
                val index = groups.indexOfFirst { it.id == group.id }
                if (index < 0) groups.add(group) else groups[index] = group
                if (!store.saveGroups(groups)) { toast(R.string.scenes_save_failed); return@setOnClickListener }
                changed()
                onSaved()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private data class AppChoice(val packageName: String, val label: String, val resolveInfo: ResolveInfo?)

    private fun chooseApps(initial: Set<String>, onChosen: (Set<String>) -> Unit) {
        val selected = initial.toMutableSet()
        val content = editorContent()
        val search = field(R.string.scenes_apps_search, "", R.id.scenes_apps_search)
        content.addView(search.first)
        val count = body(getString(R.string.scenes_apps_selected, selected.size))
        content.addView(count)
        val list = vertical().apply { id = R.id.scenes_apps_list }
        list.addView(body(getString(R.string.scenes_apps_loading)))
        content.addView(list)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.scenes_group_select)
            .setView(editorScroll(content)).setNegativeButton(R.string.scenes_cancel, null)
            .setPositiveButton(R.string.scenes_done) { _, _ -> onChosen(selected.toSet()) }.create()
        dialog.show()
        val loadVersion = ++appsLoadVersion
        Thread {
            val choices = runCatching {
                packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .distinctBy { it.activityInfo.packageName }
                    .filter { it.activityInfo.packageName != packageName }
                    .map { AppChoice(it.activityInfo.packageName, it.loadLabel(packageManager).toString(), it) }
                    .let { installed -> installed + initial.filter { pkg -> installed.none { it.packageName == pkg } }
                        .map { AppChoice(it, it, null) } }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            }
            runOnUiThread {
                if (isDestroyed || isFinishing || !dialog.isShowing || loadVersion != appsLoadVersion) return@runOnUiThread
                val apps = choices.getOrNull()
                if (apps == null) { list.removeAllViews(); list.addView(body(getString(R.string.scenes_apps_unavailable))); return@runOnUiThread }
                fun renderApps(query: String) {
                    list.removeAllViews()
                    val shown = apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
                    if (shown.isEmpty()) list.addView(body(getString(R.string.scenes_apps_none)))
                    shown.forEach { app ->
                        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                        val icon = ImageView(this).apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(12) }
                            setImageDrawable(runCatching { app.resolveInfo?.loadIcon(packageManager) }.getOrNull())
                        }
                        row.addView(icon)
                        row.addView(MaterialCheckBox(this).apply {
                            text = if (app.resolveInfo == null) "${app.label}\n${getString(R.string.scenes_apps_not_installed)}" else app.label
                            textSize = 16f
                            minHeight = dp(56)
                            setTextColor(SecondaryScreenUi.color(this@ScenesActivity, R.color.bifrost_text))
                            isChecked = app.packageName in selected
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
                                count.text = getString(R.string.scenes_apps_selected, selected.size)
                            }
                        })
                        list.addView(row)
                    }
                }
                renderApps("")
                search.second.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderApps(s.toString().trim()) }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
            }
        }.apply { name = "DuoFrost-app-picker"; isDaemon = true }.start()
    }

    private fun confirmDeleteRule(rule: SceneRule) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.scenes_delete_rule_title)
            .setMessage(getString(R.string.scenes_delete_rule_body, rule.name))
            .setNegativeButton(R.string.scenes_cancel, null).setPositiveButton(R.string.scenes_delete) { _, _ ->
                store.saveRules(store.loadRules().filterNot { it.id == rule.id })
                changed()
            }.show()
    }

    private fun confirmDeleteGroup(group: AppGroup, onDeleted: () -> Unit) {
        MaterialAlertDialogBuilder(this).setTitle(R.string.scenes_group_deleted_title)
            .setMessage(getString(R.string.scenes_group_deleted_body, group.name))
            .setNegativeButton(R.string.scenes_cancel, null).setPositiveButton(R.string.scenes_delete) { _, _ ->
                store.saveGroups(store.loadGroups().filterNot { it.id == group.id })
                store.saveRules(store.loadRules().map { if (it.groupId == group.id) it.copy(enabled = false) else it })
                changed()
                onDeleted()
            }.show()
    }

    private fun chooseDays(initial: Set<Int>, onChosen: (Set<Int>) -> Unit) {
        val selected = initial.toMutableSet()
        val labels = DayOfWeek.values().map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.scenes_days_title)
            .setMultiChoiceItems(labels, BooleanArray(7) { it + 1 in initial }) { _, index, checked ->
                if (checked) selected.add(index + 1) else selected.remove(index + 1)
            }.setNegativeButton(R.string.scenes_cancel, null).setPositiveButton(R.string.scenes_done, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selected.isEmpty()) { toast(R.string.scenes_days_required); return@setOnClickListener }
                onChosen(selected.toSet())
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun needsUsageAccess() = store.loadRules().any { it.enabled && it.target != SceneTarget.ANY } && !hasUsageAccess()
    private fun hasUsageAccess(): Boolean = runCatching {
        (getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
            .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    private fun showUsageExplanation() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.scenes_usage_title).setMessage(R.string.scenes_usage_notice)
            .setNegativeButton(R.string.scenes_later, null).setPositiveButton(R.string.scenes_usage_action) { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    .onFailure { toast(R.string.scenes_usage_unavailable) }
            }.show()
    }

    private fun changed() { LightingStateEvents.notifyChanged(this); render() }
    private fun bootCount() = runCatching { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
    private fun chargingLabel(condition: ChargingCondition) = getString(when (condition) {
        ChargingCondition.ANY -> R.string.scenes_charging_any
        ChargingCondition.CHARGING -> R.string.scenes_charging_yes
        ChargingCondition.ON_BATTERY -> R.string.scenes_charging_no
    })
    private fun daysDescription(days: Set<Int>) = if (days.size == 7) getString(R.string.scenes_days_all)
        else days.sorted().filter { it in 1..7 }.joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    private fun time(minute: Int) = String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)
    private fun pickTime(initial: Int, onChosen: (Int) -> Unit) {
        TimePickerDialog(this, { _, hour, minute -> onChosen(hour * 60 + minute) }, initial / 60, initial % 60, true).show()
    }
    private fun validPercent(field: Pair<TextInputLayout, TextInputEditText>): Int? {
        val number = field.second.text.toString().toIntOrNull()
        if (number == null || number !in 0..100) {
            field.first.error = getString(R.string.scenes_percent_required)
            field.second.requestFocus()
            return null
        }
        field.first.error = null
        return number
    }
    private fun editorDialog(title: Int, content: LinearLayout) = MaterialAlertDialogBuilder(this)
        .setTitle(title).setView(editorScroll(content)).setNegativeButton(R.string.scenes_cancel, null)
        .setPositiveButton(R.string.scenes_save, null).create()
    private fun editorScroll(content: LinearLayout): View = SecondaryScreenUi.safeScrollContainer(this, ScrollView(this).apply {
        isFillViewport = false
        addView(content)
    })
    private fun editorContent() = vertical().apply { setPadding(dp(24), dp(8), dp(24), dp(16)) }
    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun body(text: String, secondary: Boolean = true) = SecondaryScreenUi.body(this, text, secondary)
    private fun section(label: Int) = body(getString(label), secondary = false).apply {
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        isAccessibilityHeading = true
    }
    private fun label(label: Int) = body(getString(label), secondary = false).apply { setPadding(0, dp(16), 0, dp(8)) }
    private fun action(label: Int, primary: Boolean = false, onClick: () -> Unit) =
        SecondaryScreenUi.action(this, getString(label), primary, onClick)
    private fun actionLabel(label: String, onClick: () -> Unit) = SecondaryScreenUi.action(this, label, onClick = onClick)
    private fun buttonRow(vararg buttons: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { index, button ->
            addView(button, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
    }
    private fun spinner(labels: List<String>) = Spinner(this).apply {
        minimumHeight = dp(48)
        adapter = ArrayAdapter(this@ScenesActivity, R.layout.item_spinner_bifrost, labels).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown_bifrost)
        }
    }
    private fun field(hint: Int, value: String, id: Int, numeric: Boolean = false): Pair<TextInputLayout, TextInputEditText> {
        val edit = TextInputEditText(this).apply {
            this.id = id
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 16f
            minHeight = dp(56)
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(if (numeric) 3 else SceneLimits.MAX_NAME_LENGTH))
            setTextColor(SecondaryScreenUi.color(this@ScenesActivity, R.color.bifrost_text))
            setText(value)
        }
        val layout = TextInputLayout(this).apply {
            this.hint = getString(hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(edit)
        }
        return layout to edit
    }
    private fun toast(message: Int) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun dp(value: Int) = SecondaryScreenUi.dp(this, value)
}
