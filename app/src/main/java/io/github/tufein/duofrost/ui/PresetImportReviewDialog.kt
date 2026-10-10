package io.github.tufein.duofrost.ui

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetArchiveTransfer
import io.github.tufein.duofrost.PresetImportPlan
import io.github.tufein.duofrost.R
import java.util.Locale

/** Review only. The host owns committing the accepted plan and cleaning staged artwork. */
object PresetImportReviewDialog {
    fun show(
        activity: AppCompatActivity,
        result: PresetArchiveTransfer.ImportResult,
        currentPresets: List<LedPreset>,
        onApply: (PresetImportPlan.Plan) -> Unit
    ): AlertDialog? {
        if (activity.isFinishing || activity.isDestroyed) return null
        if (result.errors.isNotEmpty() || result.presets.size > PresetImportPlan.MAX_IMPORTED_PRESETS) {
            val message = if (result.errors.isNotEmpty()) result.errors.joinToString("\n").take(4_000)
                else activity.getString(R.string.import_review_limit, PresetImportPlan.MAX_IMPORTED_PRESETS)
            return MaterialAlertDialogBuilder(activity).setTitle(R.string.import_review_title)
                .setMessage(message).setPositiveButton(R.string.alert_action_ok, null).show()
        }

        val selected = result.presets.indices.toMutableSet()
        var mode = PresetImportPlan.Mode.ADD_COPIES
        var includeMappings = false
        var batchSelecting = false
        var dialog: AlertDialog? = null
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 24), dp(activity, 8), dp(activity, 24), dp(activity, 8))
        }
        content.addView(SecondaryScreenUi.body(activity, activity.getString(R.string.import_review_description)))
        val summary = SecondaryScreenUi.body(activity, "", secondary = false).apply {
            id = R.id.import_review_summary
            setPadding(0, dp(activity, 16), 0, dp(activity, 8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        fun plan() = PresetImportPlan.build(currentPresets, result.presets, selected, mode,
            result.mappings, includeMappings)
        fun refresh() {
            val planned = plan()
            summary.text = when {
                PresetImportPlan.Error.LIBRARY_LIMIT in planned.errors ->
                    activity.getString(R.string.import_review_library_limit, PresetImportPlan.MAX_LIBRARY_PRESETS)
                PresetImportPlan.Error.NO_SELECTION in planned.errors -> activity.getString(R.string.import_review_choose)
                PresetImportPlan.Error.NOTHING_ACCEPTED in planned.errors -> activity.getString(R.string.import_review_nothing)
                else -> buildString {
                    append(activity.getString(R.string.import_review_summary, planned.addedCount,
                        planned.replacedCount, planned.skippedCount, planned.mappings.size))
                    if (planned.protectedCopyCount > 0) {
                        append("\n")
                        append(activity.getString(R.string.import_review_protected, planned.protectedCopyCount))
                    }
                }
            }
            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = planned.canApply
        }

        content.addView(SecondaryScreenUi.body(activity, activity.getString(R.string.import_review_mode)).apply {
            setPadding(0, dp(activity, 16), 0, 0)
        })
        content.addView(Spinner(activity).apply {
            id = R.id.import_review_mode
            minimumHeight = dp(activity, 48)
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item,
                listOf(activity.getString(R.string.import_review_add),
                    activity.getString(R.string.import_review_replace), activity.getString(R.string.import_review_skip)))
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    mode = PresetImportPlan.Mode.entries[position]
                    refresh()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        })
        content.addView(SecondaryScreenUi.body(activity, activity.getString(R.string.import_review_conflict_help)))
        if (result.mappings.isNotEmpty()) content.addView(MaterialCheckBox(activity).apply {
            id = R.id.import_review_mappings
            text = activity.getString(R.string.import_review_assignments)
            setTextColor(SecondaryScreenUi.color(activity, R.color.bifrost_text))
            minHeight = dp(activity, 48)
            isChecked = false
            setOnCheckedChangeListener { _, checked -> includeMappings = checked; refresh() }
        })
        content.addView(summary)
        if (result.warnings.isNotEmpty()) content.addView(SecondaryScreenUi.body(activity, buildString {
            append(activity.getString(R.string.import_review_notes, result.warnings.size))
            append("\n")
            append(result.warnings.take(5).joinToString("\n") { it.take(400) })
            if (result.warnings.size > 5) {
                append("\n")
                append(activity.getString(R.string.import_review_more_notes, result.warnings.size - 5))
            }
        }))
        val selectionActions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val checks = mutableListOf<MaterialCheckBox>()
        selectionActions.addView(SecondaryScreenUi.action(activity, activity.getString(R.string.import_review_all)) {
            batchSelecting = true
            checks.forEach { it.isChecked = true }
            batchSelecting = false
            refresh()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        selectionActions.addView(SecondaryScreenUi.action(activity, activity.getString(R.string.import_review_none)) {
            batchSelecting = true
            checks.forEach { it.isChecked = false }
            batchSelecting = false
            refresh()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(selectionActions)
        result.presets.forEachIndexed { index, preset ->
            val effect = preset.animationType.name.lowercase(Locale.ROOT).replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
            val check = MaterialCheckBox(activity).apply {
                tag = "import-review-$index"
                text = activity.getString(R.string.import_review_entry, preset.name, effect)
                setTextColor(SecondaryScreenUi.color(activity, R.color.bifrost_text))
                minHeight = dp(activity, 56)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                isChecked = true
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected += index else selected -= index
                    if (!batchSelecting) refresh()
                }
            }
            checks += check
            content.addView(check)
        }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(content)
        }
        val viewport = LinearLayout(activity).apply {
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                minOf(dp(activity, 420), (activity.resources.displayMetrics.heightPixels * 0.55f).toInt())))
        }
        val created = MaterialAlertDialogBuilder(activity).setTitle(R.string.import_review_title)
            .setView(viewport).setPositiveButton(R.string.import_review_apply, null)
            .setNegativeButton(R.string.action_cancel, null).create()
        dialog = created
        created.setOnShowListener {
            refresh()
            created.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val planned = plan()
                if (!planned.canApply || activity.isFinishing || activity.isDestroyed) return@setOnClickListener
                created.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                onApply(planned)
                created.dismiss()
            }
        }
        created.show()
        return created
    }

    private fun dp(activity: AppCompatActivity, value: Int) = SecondaryScreenUi.dp(activity, value)
}
