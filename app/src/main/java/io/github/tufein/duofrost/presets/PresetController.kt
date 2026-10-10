package io.github.tufein.duofrost

import android.content.SharedPreferences
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.tufein.duofrost.tools.PerformanceProfile
import io.github.tufein.duofrost.ui.DeletePresetDialog
import io.github.tufein.duofrost.ui.DuoFrostAlertDialog

class PresetController(
    private val activity: AppCompatActivity,
    private val prefs: SharedPreferences,
    private val presetSpinner: Spinner,
    private val saveAsNewButton: MaterialButton,
    private val modifyButton: MaterialButton,
    private val deleteButton: MaterialButton,
    private val getCurrentConfig: () -> LedPreset,
    private val applyPresetToUi: (LedPreset) -> Unit,
    private val markIsUpdatingFromPreset: (Boolean) -> Unit,
    private val isUpdatingFromPreset: () -> Boolean,
    private val onPresetApplied: () -> Unit,
    private val onRequestCustomPresetImage: (Int) -> Unit,
    private val onPresetRenamed: (oldName: String, newName: String) -> Unit = { _, _ -> },
    private val onDestructiveChange: (PresetUndoStore.RecordResult) -> Unit = {},
    private val onLibraryChanged: () -> Unit = {}
) {

    companion object {
        private const val PREF_KEY_LAST_PRESET = "last_preset_name"

        internal fun hasLightingChanges(current: LedPreset, selectedPreset: LedPreset): Boolean {
            return current.animationType != selectedPreset.animationType ||
                current.performanceProfile != selectedPreset.performanceProfile ||
                current.color != selectedPreset.color ||
                current.rightColor != selectedPreset.rightColor ||
                current.fadeEndColor != selectedPreset.fadeEndColor ||
                current.fadeEndRightColor != selectedPreset.fadeEndRightColor ||
                current.brightness != selectedPreset.brightness ||
                current.speed != selectedPreset.speed ||
                current.smoothness != selectedPreset.smoothness ||
                current.sensitivity != selectedPreset.sensitivity ||
                current.saturationBoost != selectedPreset.saturationBoost ||
                current.useCustomSampling != selectedPreset.useCustomSampling ||
                current.useSingleColor != selectedPreset.useSingleColor ||
                current.breatheWhenCharging != selectedPreset.breatheWhenCharging ||
                current.indicateChargingSpeed != selectedPreset.indicateChargingSpeed ||
                current.flashWhenReady != selectedPreset.flashWhenReady ||
                current.batteryLowColorOverride != selectedPreset.batteryLowColorOverride ||
                current.batteryMidColorOverride != selectedPreset.batteryMidColorOverride ||
                current.batteryHighColorOverride != selectedPreset.batteryHighColorOverride ||
                current.cpuCoolColorOverride != selectedPreset.cpuCoolColorOverride ||
                current.cpuWarmColorOverride != selectedPreset.cpuWarmColorOverride ||
                current.cpuHotColorOverride != selectedPreset.cpuHotColorOverride
        }

        internal fun canDeleteArtwork(fileName: String?, presets: List<LedPreset>, excludedIndex: Int): Boolean {
            if (fileName.isNullOrBlank()) return false
            return presets.indices.none { index ->
                index != excludedIndex && presets[index].customImageFileName == fileName
            }
        }
    }

    private val repository = PresetRepository(prefs)
    private val undoStore = PresetUndoStore(prefs)
    private val libraryStore = PresetLibraryStore(prefs)
    private val presets: MutableList<LedPreset> = mutableListOf()
    private var selectedIndex: Int = 0
    private val deleteDialog = DeletePresetDialog()

    private enum class CreatePresetDialogMode {
        DEFAULT,
        HOME_PLUS
    }

    fun init(initialConfig: LedPreset): LedPreset {
        presets.clear()
        presets.addAll(loadPresetsFromPrefs())
        normalizeAppProfileDefaultPreset()
        val initialPreset = resolveInitialPreset(initialConfig)
        markIsUpdatingFromPreset(true)
        applyPresetToUi(initialPreset)
        markIsUpdatingFromPreset(false)
        setupPresetControls(initialPreset)
        return initialPreset
    }

    private fun normalizeAppProfileDefaultPreset() {
        val defaultIndexes = presets.mapIndexedNotNull { index, preset ->
            if (preset.isAppProfileDefault) index else null
        }
        if (defaultIndexes.size <= 1) return

        val keepIndex = defaultIndexes.first()
        presets.indices.forEach { index ->
            presets[index] = presets[index].copy(isAppProfileDefault = index == keepIndex)
        }
        savePresetsToPrefs()
    }

    fun getPresets(): List<LedPreset> = presets

    /**
     * Rebuild the in-memory list from prefs without disturbing the currently
     * selected preset. Used on activity resume so presets installed or removed
     * while the UI was backgrounded (e.g. via the external API, or on app
     * uninstall) show up when the user next opens DuoFrost.
     */
    fun reloadFromPrefs(refreshSpinner: Boolean = false) {
        val selectedId = presets.getOrNull(selectedIndex)?.id
        val selectedName = presets.getOrNull(selectedIndex)?.name
        presets.clear()
        presets.addAll(loadPresetsFromPrefs())
        selectedIndex = if (selectedId != null && presets.any { it.id == selectedId }) {
            presets.indexOfFirst { it.id == selectedId }
        } else if (selectedName != null) {
            presets.indexOfFirst { it.name == selectedName }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        if (refreshSpinner) refreshPresetSpinner(presets.getOrNull(selectedIndex)?.name, selectedId)
    }

    /** Commit an accepted review only if its original library is still current. Never applies lighting. */
    fun applyImportPlan(plan: PresetImportPlan.Plan): Boolean = synchronized(prefs) {
        if (!plan.canApply || repository.list() != plan.basePresets) return@synchronized false
        val selectedId = presets.getOrNull(selectedIndex)?.id
        val selectedName = presets.getOrNull(selectedIndex)?.name
        presets.clear()
        presets.addAll(plan.finalPresets)
        savePresetsToPrefs()
        if (repository.list() != presets) {
            reloadFromPrefs(refreshSpinner = true)
            return@synchronized false
        }
        refreshPresetSpinner(selectedName, selectedId)
        true
    }


    fun setAppProfileDefaultPreset(index: Int, isDefault: Boolean): Boolean {
        if (index !in presets.indices) return false

        val changed = if (isDefault) {
            var hasChanges = false
            presets.indices.forEach { i ->
                val shouldBeDefault = (i == index)
                if (presets[i].isAppProfileDefault != shouldBeDefault) {
                    presets[i] = presets[i].copy(isAppProfileDefault = shouldBeDefault)
                    hasChanges = true
                }
            }
            hasChanges
        } else {
            if (!presets[index].isAppProfileDefault) {
                false
            } else {
                presets[index] = presets[index].copy(isAppProfileDefault = false)
                true
            }
        }

        if (!changed) return false

        val selectedName = presets.getOrNull(selectedIndex)?.name
        savePresetsToPrefs()
        refreshPresetSpinner(selectedName)
        return true
    }

    fun applyPresetAt(index: Int, syncSpinner: Boolean = true) {
        if (index !in presets.indices) return

        selectedIndex = index
        val preset = presets[index]
        saveLastPresetName(preset.name)

        markIsUpdatingFromPreset(true)
        if (syncSpinner && presetSpinner.selectedItemPosition != index) {
            presetSpinner.setSelection(index)
        }
        applyPresetToUi(preset)
        markIsUpdatingFromPreset(false)

        onPresetApplied()
    }

    fun selectPresetForEditing(index: Int, syncSpinner: Boolean = true) {
        if (index !in presets.indices) return

        selectedIndex = index
        val preset = presets[index]

        markIsUpdatingFromPreset(true)
        if (syncSpinner && presetSpinner.selectedItemPosition != index) {
            presetSpinner.setSelection(index)
        }
        applyPresetToUi(preset)
        markIsUpdatingFromPreset(false)
    }

    fun movePreset(fromIndex: Int, toIndex: Int): Boolean {
        if (fromIndex !in presets.indices || toIndex !in presets.indices) return false
        if (fromIndex == toIndex) return false

        val selectedPresetName = presets.getOrNull(selectedIndex)?.name
        val movedPreset = presets.removeAt(fromIndex)
        presets.add(toIndex, movedPreset)

        savePresetsToPrefs()
        saveLastPresetName(selectedPresetName ?: movedPreset.name)
        refreshPresetSpinner(selectedPresetName ?: movedPreset.name)
        return true
    }

    fun updatePresetVisual(index: Int, transform: (LedPreset) -> LedPreset): LedPreset? {
        if (index !in presets.indices) return null

        val selectedPresetName = presets.getOrNull(selectedIndex)?.name
        val updatedPreset = transform(presets[index]).copy(id = presets[index].id)
        replacePreset(
            index = index,
            updatedPreset = updatedPreset,
            applyToUi = false,
            notifyPresetApplied = false,
            selectedNameAfterSave = selectedPresetName ?: updatedPreset.name
        )
        return updatedPreset
    }

    fun markRagnarokAccepted(name: String?) {
        if (name == null) return
        val index = presets.indexOfFirst { it.name == name }
        if (index < 0) return
        val p = presets[index]
        presets[index] = p.copy(ragnarokAccepted = true)
        savePresetsToPrefs()
    }

    fun saveCurrentConfigToSelectedPreset(): Boolean {
        if (selectedIndex !in presets.indices) return false

        val current = presets[selectedIndex]
        val base = getCurrentConfig()
        val accepted = current.ragnarokAccepted || base.performanceProfile == PerformanceProfile.RAGNAROK

        val updated = base.copy(
            name = current.name,
            isAppProfileDefault = current.isAppProfileDefault,
            ragnarokAccepted = accepted,
            icon = current.icon,
            customEmoji = current.customEmoji,
            customImageFileName = current.customImageFileName,
            appIconPackageName = current.appIconPackageName,
            ownerPackage = current.ownerPackage,
            id = current.id
        )

        replacePreset(
            index = selectedIndex,
            updatedPreset = updated,
            applyToUi = false,
            notifyPresetApplied = false,
            selectedNameAfterSave = current.name
        )
        return true
    }

    fun hasUnsavedChangesForSelectedPreset(): Boolean {
        if (selectedIndex !in presets.indices) return false

        val selectedPreset = presets[selectedIndex]
        val current = getCurrentConfig()

        return hasLightingChanges(current, selectedPreset)
    }

    private fun setupPresetControls(initialPreset: LedPreset) {
        refreshPresetSpinner(initialPreset.name)

        saveAsNewButton.setOnClickListener { showSaveAsNewPresetDialog() }
        modifyButton.setOnClickListener { modifyCurrentPreset() }
        deleteButton.setOnClickListener { showDeleteDialog() }
        // Long-press on delete button -> delete ALL presets after confirmation
        deleteButton.setOnLongClickListener {
            showDeleteAllDialog()
            true
        }

        presetSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (isUpdatingFromPreset() || position == selectedIndex) return
                    applyPresetAt(position, syncSpinner = false)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
    }

    private fun refreshPresetSpinner(selectedName: String?, selectedId: String? = null) {
        val adapter = IconLabelSpinnerAdapter(
            activity = activity,
            items = presets.toList(),
            itemLayoutRes = R.layout.item_spinner_preset,
            dropdownLayoutRes = R.layout.item_spinner_preset_dropdown,
            labelProvider = { it.name },
            visualProvider = { PresetVisuals.fromPreset(it) }
        )
        val index = selectedId?.let { id -> presets.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: selectedName?.let { name ->
            presets.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: 0
        } ?: 0

        selectedIndex = index.coerceIn(0, (presets.size - 1).coerceAtLeast(0))

        markIsUpdatingFromPreset(true)
        presetSpinner.adapter = adapter
        if (presets.isNotEmpty()) presetSpinner.setSelection(selectedIndex)
        markIsUpdatingFromPreset(false)
    }

    private fun loadPresetsFromPrefs(): MutableList<LedPreset> = repository.list().toMutableList()

    private fun savePresetsToPrefs() {
        val saved = repository.save(presets)
        presets.clear()
        presets.addAll(saved)
        io.github.tufein.duofrost.widgets.DuoFrostWidget.refreshFrom(activity)
    }

    private fun saveLastPresetName(name: String) {
        prefs.edit().putString(PREF_KEY_LAST_PRESET, name).apply()
    }

    private fun resolveInitialPreset(initialConfig: LedPreset): LedPreset {
        if (presets.isEmpty()) {
            val defaultPreset = initialConfig.copy(
                id = PresetIdentity.newId(),
                name = "Default",
                isAppProfileDefault = true
            )
            presets.add(defaultPreset)
            savePresetsToPrefs()
            saveLastPresetName(defaultPreset.name)
            return defaultPreset
        }

        val last = prefs.getString(PREF_KEY_LAST_PRESET, null)
        if (last != null) {
            val found = presets.firstOrNull { it.name == last }
            if (found != null) return found
        }

        val first = presets.first()
        saveLastPresetName(first.name)
        return first
    }

    fun showSaveAsNewPresetDialogFromHomePlus() {
        showSaveAsNewPresetDialog(mode = CreatePresetDialogMode.HOME_PLUS)
    }

    private fun showSaveAsNewPresetDialog(mode: CreatePresetDialogMode = CreatePresetDialogMode.DEFAULT) {
        val defaultName = "Preset ${presets.size + 1}"

        val title = if (mode == CreatePresetDialogMode.HOME_PLUS) {
            "NEW PRESET"
        } else {
            "SAVE PRESET"
        }

        showPresetEditorDialog(
            title = title,
            subtitle = "Name this configuration for quick access",
            positiveButtonLabel = "Save",
            initialName = defaultName,
            showNameInput = true,
            showCustomImageOption = mode == CreatePresetDialogMode.HOME_PLUS,
            onConfirm = { rawName ->
                createPresetFromInputs(
                    rawName = rawName,
                    defaultName = defaultName
                )
            },
            onConfirmWithCustomImage = { rawName ->
                val newIndex = createPresetFromInputs(
                    rawName = rawName,
                    defaultName = defaultName
                )
                if (newIndex >= 0) {
                    onRequestCustomPresetImage(newIndex)
                }
            }
        )
    }

    private fun createPresetFromInputs(rawName: String, defaultName: String): Int {
        val base = getCurrentConfig()
        val desired = if (rawName.isEmpty()) defaultName else rawName
        val unique = ensureUniqueName(desired)
        val baseIcon = presets.getOrNull(selectedIndex)?.icon
            ?: PresetIcon.defaultFor(base.animationType)
        val newPreset = base.copy(
            id = PresetIdentity.newId(),
            name = unique,
            isAppProfileDefault = false,
            icon = baseIcon,
            customEmoji = null,
            customImageFileName = null,
            appIconPackageName = null,
            ownerPackage = null,
            ragnarokAccepted = base.performanceProfile == PerformanceProfile.RAGNAROK
        )

        presets.add(newPreset)
        val newIndex = presets.lastIndex
        savePresetsToPrefs()
        saveLastPresetName(unique)
        refreshPresetSpinner(unique)
        onLibraryChanged()
        return newIndex
    }

    private fun ensureUniqueName(base: String): String {
        if (presets.none { it.name == base }) return base
        var i = 2
        while (true) {
            val name = "$base ($i)"
            if (presets.none { it.name == name }) return name
            i++
        }
    }

    private fun modifyCurrentPreset() {
        if (selectedIndex !in presets.indices) return

        val current = presets[selectedIndex]
        showPresetEditorDialog(
            title = "UPDATE PRESET",
            subtitle = "Update and rename ${current.name} if needed",
            positiveButtonLabel = "Update",
            initialName = current.name,
            showNameInput = true
        ) { rawName ->
            val base = getCurrentConfig()
            val accepted = current.ragnarokAccepted || base.performanceProfile == PerformanceProfile.RAGNAROK
            val desiredName = rawName.ifBlank { current.name }
            val finalName = ensureUniqueNameForUpdate(desiredName, selectedIndex)

            if (finalName != current.name) {
                onPresetRenamed(current.name, finalName)
            }

            val final = base.copy(
                name = finalName,
                isAppProfileDefault = current.isAppProfileDefault,
                ragnarokAccepted = accepted,
                icon = current.icon,
                customEmoji = current.customEmoji,
                customImageFileName = current.customImageFileName,
                appIconPackageName = current.appIconPackageName,
                ownerPackage = current.ownerPackage,
                id = current.id
            )

            replacePreset(
                index = selectedIndex,
                updatedPreset = final,
                applyToUi = true,
                notifyPresetApplied = true,
                selectedNameAfterSave = finalName
            )
        }
    }

    private fun ensureUniqueNameForUpdate(base: String, editingIndex: Int): String {
        if (presets.indices.none { it != editingIndex && presets[it].name == base }) return base
        var i = 2
        while (true) {
            val candidate = "$base ($i)"
            if (presets.indices.none { it != editingIndex && presets[it].name == candidate }) {
                return candidate
            }
            i++
        }
    }

    private fun replacePreset(
        index: Int,
        updatedPreset: LedPreset,
        applyToUi: Boolean,
        notifyPresetApplied: Boolean,
        selectedNameAfterSave: String = updatedPreset.name
    ) {
        if (index !in presets.indices) return

        val previousPreset = presets[index]
        cleanupReplacedImage(index, previousPreset, updatedPreset)

        presets[index] = updatedPreset.copy(id = previousPreset.id)
        savePresetsToPrefs()
        saveLastPresetName(selectedNameAfterSave)
        refreshPresetSpinner(selectedNameAfterSave)

        if (applyToUi) {
            markIsUpdatingFromPreset(true)
            applyPresetToUi(updatedPreset)
            markIsUpdatingFromPreset(false)
        }

        if (notifyPresetApplied) {
            onPresetApplied()
        }
    }

    private fun cleanupReplacedImage(index: Int, previousPreset: LedPreset, updatedPreset: LedPreset) {
        val previousFileName = previousPreset.customImageFileName
        if (previousFileName != updatedPreset.customImageFileName &&
            canDeleteArtwork(previousFileName, presets, excludedIndex = index) &&
            previousFileName !in undoStore.retainedArtworkFileNames()) {
            PresetImageStorage.deleteIfExists(activity, previousFileName)
        }
    }

    private fun showPresetEditorDialog(
        title: String,
        subtitle: String,
        positiveButtonLabel: String,
        initialName: String,
        showNameInput: Boolean,
        showCustomImageOption: Boolean = false,
        onConfirmWithCustomImage: ((String) -> Unit)? = null,
        onConfirm: (String) -> Unit
    ) {
        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_preset_name, null)
        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val subtitleView = view.findViewById<TextView>(R.id.dialogSubtitle)
        val nameInputLayout = view.findViewById<TextInputLayout>(R.id.presetNameInputLayout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.presetNameInput)
        val customImageButton = view.findViewById<MaterialButton>(R.id.presetCustomImageButton)

        titleView.text = title
        subtitleView.text = subtitle
        nameInput.setText(initialName)

        nameInputLayout.visibility = if (showNameInput) View.VISIBLE else View.GONE
        if (showNameInput) {
            nameInput.setSelection(initialName.length)
        }

        customImageButton.visibility = if (showCustomImageOption) View.VISIBLE else View.GONE

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(positiveButtonLabel) { _, _ ->
                val resolvedName = if (showNameInput) {
                    nameInput.text?.toString()?.trim().orEmpty()
                } else {
                    initialName
                }
                onConfirm(resolvedName)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        if (showCustomImageOption && onConfirmWithCustomImage != null) {
            customImageButton.setOnClickListener {
                val resolvedName = if (showNameInput) {
                    nameInput.text?.toString()?.trim().orEmpty()
                } else {
                    initialName
                }
                dialog.dismiss()
                onConfirmWithCustomImage.invoke(resolvedName)
            }
        }

        dialog.show()
    }

    private fun showDeleteDialog() {
        if (selectedIndex !in presets.indices) return
        val preset = presets[selectedIndex]

        deleteDialog.show(
            activity = activity,
            presetName = preset.name,
            onConfirm = {
                var nextPreset: LedPreset? = null
                val recorded = synchronized(prefs) {
                    reloadFromPrefs()
                    val deleteIndex = presets.indexOfFirst { it.id == preset.id }
                    if (deleteIndex < 0) return@synchronized null
                    val before = undoStore.captureSnapshot()
                    presets.removeAt(deleteIndex)
                    libraryStore.removePreset(preset.id)
                    savePresetsToPrefs()
                    nextPreset = presets.getOrNull(deleteIndex.coerceAtMost((presets.size - 1).coerceAtLeast(0)))
                    saveLastPresetName(nextPreset?.name.orEmpty())
                    refreshPresetSpinner(nextPreset?.name, nextPreset?.id)
                    before?.let { undoStore.record(PresetUndoStore.Action.DELETE, it) }
                        ?: PresetUndoStore.RecordResult.INVALID_SNAPSHOT
                }
                if (recorded != null) {
                    nextPreset?.let {
                        markIsUpdatingFromPreset(true)
                        applyPresetToUi(it)
                        markIsUpdatingFromPreset(false)
                        onPresetApplied()
                    }
                    onDestructiveChange(recorded)
                    PresetArtworkPruner.prune(activity, prefs)
                }
            }
        )
    }

    private fun showDeleteAllDialog() {
        val title = activity.getString(R.string.delete_all_presets_title)
        val body = activity.getString(R.string.delete_all_presets_body)

        DuoFrostAlertDialog().show(
            activity = activity,
            title = title,
            subtitle = null,
            body = body,
            positiveLabelResId = R.string.action_delete_all,
            negativeLabelResId = R.string.action_cancel,
            cancelable = true,
            onConfirm = {
                deleteAllPresets()
            },
            onCancel = {}
        )
    }

    private fun deleteAllPresets() {
        val recorded = synchronized(prefs) {
            reloadFromPrefs()
            val before = undoStore.captureSnapshot()
            libraryStore.removePresets(presets.map { it.id }.toSet())
            presets.clear()
            savePresetsToPrefs()
            saveLastPresetName("")
            // Keep a usable editor after deleting the entire library.
            val defaultPreset = resolveInitialPreset(getCurrentConfig())
            refreshPresetSpinner(defaultPreset.name, defaultPreset.id)
            before?.let { undoStore.record(PresetUndoStore.Action.DELETE, it) }
                ?: PresetUndoStore.RecordResult.INVALID_SNAPSHOT
        }
        val defaultPreset = presets.first()
        markIsUpdatingFromPreset(true)
        applyPresetToUi(defaultPreset)
        markIsUpdatingFromPreset(false)
        onPresetApplied()
        onDestructiveChange(recorded)
        PresetArtworkPruner.prune(activity, prefs)
    }
}

class IconLabelSpinnerAdapter<T>(
    activity: AppCompatActivity,
    private val items: List<T>,
    @LayoutRes private val itemLayoutRes: Int,
    @LayoutRes private val dropdownLayoutRes: Int,
    private val labelProvider: (T) -> CharSequence,
    private val visualProvider: (T) -> PresetVisualSpec
) : ArrayAdapter<T>(activity, itemLayoutRes, items) {

    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): T = items[position]

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return bindView(position, convertView, parent, itemLayoutRes)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return bindView(position, convertView, parent, dropdownLayoutRes)
    }

    private fun bindView(position: Int, convertView: View?, parent: ViewGroup, layoutRes: Int): View {
        val row = convertView ?: inflater.inflate(layoutRes, parent, false)
        val item = items[position]
        val text = row.findViewById<TextView>(android.R.id.text1)
        val icon = row.findViewById<ImageView>(android.R.id.icon)
        val emoji = row.findViewById<TextView>(R.id.presetIconEmoji)
        val visual = visualProvider(item)

        text.text = labelProvider(item)

        val iconSize = maxOf(icon.layoutParams?.width ?: 0, icon.layoutParams?.height ?: 0, 20)
        PresetVisuals.bind(context, visual, icon, emoji, iconSize)

        return row
    }
}
