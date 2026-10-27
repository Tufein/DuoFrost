package io.github.tufein.duofrost.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.tufein.duofrost.R

class DuoFrostAlertDialog {

    fun show(
        activity: AppCompatActivity,
        title: String,
        subtitle: String?,
        body: String?,
        positiveLabelResId: Int,
        negativeLabelResId: Int?,
        cancelable: Boolean,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {}
    ) {
        // The AYN launcher can destroy/recreate MainActivity between a posted
        // callback being scheduled and run (it relaunches across display 4 → 0),
        // so a dialog shown from such a callback hits a dead window token →
        // BadTokenException (fatal) or a leaked window. Bail if the host activity
        // is no longer live. Central guard: covers every DuoFrostAlertDialog caller.
        if (activity.isFinishing || activity.isDestroyed) return

        val inflater = LayoutInflater.from(activity)
        val view = inflater.inflate(R.layout.dialog_bifrost_alert, null)

        val titleView = view.findViewById<TextView>(R.id.dialogTitle)
        val subtitleView = view.findViewById<TextView>(R.id.dialogSubtitle)
        val bodyView = view.findViewById<TextView>(R.id.dialogBody)

        titleView.text = title

        if (subtitle.isNullOrEmpty()) {
            subtitleView.visibility = View.GONE
        } else {
            subtitleView.visibility = View.VISIBLE
            subtitleView.text = subtitle
        }

        if (body.isNullOrEmpty()) {
            bodyView.visibility = View.GONE
        } else {
            bodyView.visibility = View.VISIBLE
            bodyView.text = body
        }

        val builder = MaterialAlertDialogBuilder(activity)
            .setView(view)
            .setPositiveButton(positiveLabelResId) { _, _ -> onConfirm() }
            .setCancelable(cancelable)

        if (negativeLabelResId != null) {
            builder.setNegativeButton(negativeLabelResId) { _, _ -> onCancel() }
        }

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(cancelable)

        // A valid window can outlive its host during a Thor display relaunch.
        // Dismiss on teardown without treating it as a user confirmation/cancel.
        val lifecycle = activity.lifecycle
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
        }
        dialog.setOnDismissListener { lifecycle.removeObserver(lifecycleObserver) }
        dialog.show()
        lifecycle.addObserver(lifecycleObserver)
    }
}
