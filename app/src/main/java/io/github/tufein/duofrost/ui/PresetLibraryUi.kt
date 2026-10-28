package io.github.tufein.duofrost.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputFilter
import android.widget.LinearLayout
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.tufein.duofrost.LedPreset
import io.github.tufein.duofrost.PresetLibraryStore
import io.github.tufein.duofrost.PresetRepository
import io.github.tufein.duofrost.PresetUndoStore
import io.github.tufein.duofrost.R
import io.github.tufein.duofrost.ui.preview.PresetPreviewActivity

/** Browsing and organisation do not dispatch lighting commands. */
internal class PresetLibraryUi(
    private val activity: AppCompatActivity,
    private val prefs: SharedPreferences,
    private val onChanged: () -> Unit,
    private val onRestored: () -> Unit,
    private val onEdit: (String) -> Unit
) {
    private val store = PresetLibraryStore(prefs)
    private val undo = PresetUndoStore(prefs)
    private var favoritesOnly = false
    private var collectionId: String? = null
    private var accentColor = SecondaryScreenUi.color(activity, R.color.bifrost_accent_light)
    private var surfaceColor = SecondaryScreenUi.color(activity, R.color.bifrost_surface)
    private var textColor = SecondaryScreenUi.color(activity, R.color.bifrost_text)
    private var cardColor = SecondaryScreenUi.color(activity, R.color.bifrost_card)

    fun setThemeColors(accent: Int, surface: Int, text: Int, card: Int) {
        accentColor = accent
        surfaceColor = surface
        textColor = text
        cardColor = card
    }

    fun bind() {
        activity.findViewById<MaterialButton>(R.id.gui_libraryCollections).setOnClickListener { showCollections() }
        activity.findViewById<MaterialButton>(R.id.gui_libraryUndo).setOnClickListener {
            when (undo.restore()) {
                PresetUndoStore.RestoreResult.RESTORED -> { onRestored(); toast(R.string.library_undo_done) }
                else -> { toast(R.string.library_undo_unavailable); onChanged() }
            }
        }
    }

    fun matchingIndices(presets: List<LedPreset>, query: String): List<Int> {
        val state = store.state()
        if (collectionId != null && state.collections.none { it.id == collectionId }) collectionId = null
        return store.filter(presets, query, favoritesOnly, collectionId, favoritesFirst = true).map { it.index }
    }

    fun isFavorite(presetId: String): Boolean = store.isFavorite(presetId)

    fun render() {
        val state = store.state()
        val row = activity.findViewById<LinearLayout>(R.id.gui_libraryFilters)
        val oldFocus = row.findFocus()
        val focusedFilter = oldFocus?.tag
        val touchModeFocus = oldFocus?.isFocusableInTouchMode == true
        row.removeAllViews()
        var replacementFocus: Chip? = null
        fun chip(filterId: String, label: String, checked: Boolean, choose: () -> Unit) {
            val chip = Chip(activity).apply {
                tag = filterId
                text = label
                isCheckable = true
                isChecked = checked
                isCheckedIconVisible = true
                chipBackgroundColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(accentColor, surfaceColor)
                )
                val selectedText = UiPalette.foreground(accentColor, cardColor)
                val foregrounds = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(selectedText, textColor)
                )
                setTextColor(foregrounds)
                checkedIconTint = foregrounds
                chipStrokeColor = ColorStateList.valueOf(ColorUtils.blendARGB(cardColor, textColor, 0.24f))
                chipStrokeWidth = SecondaryScreenUi.dp(activity, 1).toFloat()
                chipCornerRadius = SecondaryScreenUi.dp(activity, 14).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = SecondaryScreenUi.dp(activity, 4) }
                ensureAccessibleTouchTarget(SecondaryScreenUi.dp(activity, 48))
                setOnClickListener { choose(); onChanged() }
                if (focusedFilter == filterId) {
                    isFocusableInTouchMode = touchModeFocus
                    replacementFocus = this
                }
            }
            row.addView(chip)
        }
        chip("library-all", activity.getString(R.string.library_all), !favoritesOnly && collectionId == null) {
            favoritesOnly = false; collectionId = null
        }
        chip("library-favorites", activity.getString(R.string.library_favorites), favoritesOnly && collectionId == null) {
            favoritesOnly = true; collectionId = null
        }
        state.collections.forEach { collection ->
            chip(collection.id, collection.name, collectionId == collection.id) { favoritesOnly = false; collectionId = collection.id }
        }
        // Request controller focus only after the replacement belongs to the live view tree.
        replacementFocus?.requestFocus()
        activity.findViewById<MaterialButton>(R.id.gui_libraryUndo).apply {
            val pending = pendingUndo()
            visibility = if (pending == null) View.GONE else View.VISIBLE
            setText(if (pending?.action == PresetUndoStore.Action.IMPORT) R.string.library_undo_import else R.string.library_undo_delete)
        }
    }

    fun pendingUndo(): PresetUndoStore.PendingUndo? = undo.pending()

    fun saveState(out: Bundle) {
        out.putBoolean("library_favorites_filter", favoritesOnly)
        out.putString("library_collection_filter", collectionId)
    }

    fun restoreState(saved: Bundle) {
        favoritesOnly = saved.getBoolean("library_favorites_filter")
        collectionId = saved.getString("library_collection_filter")
    }

    fun showActions(preset: LedPreset) {
        val favorite = store.isFavorite(preset.id)
        val labels = arrayOf(
            activity.getString(R.string.library_preview),
            activity.getString(if (favorite) R.string.library_remove_favorite else R.string.library_add_favorite),
            activity.getString(R.string.library_memberships),
            activity.getString(R.string.library_edit)
        )
        io.github.tufein.duofrost.ui.ThemedDialogBuilder(activity).setTitle(preset.name).setItems(labels) { _, which ->
            val current = PresetRepository(prefs).list()
            val index = current.indexOfFirst { it.id == preset.id }
            if (index < 0) { toast(R.string.library_missing_preset); return@setItems }
            when (which) {
                0 -> activity.startActivity(Intent(activity, PresetPreviewActivity::class.java)
                    .putExtra(PresetPreviewActivity.EXTRA_PRESET_ID, preset.id))
                1 -> {
                    if (!store.setFavorite(preset.id, !favorite)) saveError()
                    onChanged()
                }
                2 -> showMemberships(preset.id)
                3 -> onEdit(preset.id)
            }
        }.show()
    }

    private fun showCollections() {
        if (store.isReadOnly) { toast(R.string.library_read_only); return }
        val collections = store.state().collections
        val labels = collections.map { it.name } + activity.getString(R.string.library_new_collection)
        io.github.tufein.duofrost.ui.ThemedDialogBuilder(activity).setTitle(R.string.library_collections)
            .setItems(labels.toTypedArray()) { _, index ->
                if (index == collections.size) editCollection(null)
                else showCollection(collections[index])
            }.show()
    }

    private fun showCollection(collection: PresetLibraryStore.Collection) {
        io.github.tufein.duofrost.ui.ThemedDialogBuilder(activity).setTitle(collection.name).setItems(arrayOf(
            activity.getString(R.string.library_show_collection),
            activity.getString(R.string.library_rename_collection),
            activity.getString(R.string.library_delete_collection)
        )) { _, action ->
            when (action) {
                0 -> { collectionId = collection.id; favoritesOnly = false; onChanged() }
                1 -> editCollection(collection)
                2 -> io.github.tufein.duofrost.ui.ThemedDialogBuilder(activity).setTitle(R.string.library_delete_collection)
                    .setMessage(R.string.library_delete_collection_body)
                    .setNegativeButton(R.string.library_cancel, null)
                    .setPositiveButton(R.string.library_delete_collection) { _, _ ->
                        if (!store.deleteCollection(collection.id)) saveError()
                        onChanged()
                    }.show()
            }
        }.show()
    }

    private fun editCollection(existing: PresetLibraryStore.Collection?, forPreset: String? = null) {
        val input = TextInputEditText(activity).apply {
            id = R.id.library_collection_name_input
            minHeight = SecondaryScreenUi.dp(activity, 56)
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(PresetLibraryStore.MAX_NAME_LENGTH))
            setText(existing?.name.orEmpty())
        }
        val field = TextInputLayout(activity).apply {
            hint = activity.getString(R.string.library_collection_name)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            addView(input)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SecondaryScreenUi.dp(activity, 24), 0, SecondaryScreenUi.dp(activity, 24), 0)
            addView(field)
        }
        val dialog = io.github.tufein.duofrost.ui.ThemedDialogBuilder(activity)
            .setTitle(if (existing == null) R.string.library_new_collection else R.string.library_rename_collection)
            .setView(content).setNegativeButton(R.string.library_cancel, null)
            .setPositiveButton(R.string.library_save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString().orEmpty()
                val saved = if (existing == null) {
                    store.createCollection(name)?.let { created ->
                        if (forPreset != null) store.setMembership(forPreset, created.id, true) else true
                    } ?: false
                } else store.renameCollection(existing.id, name)
                if (!saved) { field.error = activity.getString(R.string.library_name_error); return@setOnClickListener }
                dialog.dismiss()
                onChanged()
            }
        }
        dialog.show()
    }

    private fun showMemberships(presetId: String) {
        if (store.isReadOnly) { toast(R.string.library_read_only); return }
        val collections = store.state().collections
        if (collections.isEmpty()) { editCollection(null, presetId); return }
        val checked = collections.map { presetId in it.presetIds }.toBooleanArray()
        io.github.tufein.duofrost.ui.ThemedDialogBuilder(activity).setTitle(R.string.library_memberships)
            .setMultiChoiceItems(collections.map { it.name }.toTypedArray(), checked) { _, index, value -> checked[index] = value }
            .setNegativeButton(R.string.library_cancel, null)
            .setPositiveButton(R.string.library_save) { _, _ ->
                val chosen = collections.indices.filter { checked[it] }.map { collections[it].id }.toSet()
                if (!store.setCollectionsForPreset(presetId, chosen)) saveError()
                onChanged()
            }.show()
    }

    private fun saveError() = toast(if (store.isReadOnly) R.string.library_read_only else R.string.library_save_error)
    private fun toast(message: Int) = Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
}
