package io.github.tufein.duofrost.ui

import androidx.appcompat.app.AppCompatActivity
import io.github.tufein.duofrost.R

class DeletePresetDialog(
    private val alertDialog: DuoFrostAlertDialog = DuoFrostAlertDialog()
) {

    fun show(
        activity: AppCompatActivity,
        presetName: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val title = activity.getString(R.string.delete_preset_title)
        val subtitle = activity.getString(R.string.delete_preset_subtitle, presetName)
        val body = activity.getString(R.string.delete_preset_body)

        alertDialog.show(
            activity = activity,
            title = title,
            subtitle = subtitle,
            body = body,
            positiveLabelResId = R.string.action_delete,
            negativeLabelResId = R.string.action_cancel,
            cancelable = true,
            onConfirm = onConfirm,
            onCancel = onCancel
        )
    }
}
