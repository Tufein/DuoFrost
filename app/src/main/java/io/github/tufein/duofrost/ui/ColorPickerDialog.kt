package io.github.tufein.duofrost.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import io.github.tufein.duofrost.R

class ColorPickerDialog {

    fun show(
        activity: AppCompatActivity,
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val preview = dialogView.findViewById<View>(R.id.colorPreview)
        val seekR = dialogView.findViewById<SeekBar>(R.id.seekRed)
        val seekG = dialogView.findViewById<SeekBar>(R.id.seekGreen)
        val seekB = dialogView.findViewById<SeekBar>(R.id.seekBlue)
        val editHex = dialogView.findViewById<EditText>(R.id.editHex)

        var ignoreChanges = false

        seekR.max = 255
        seekG.max = 255
        seekB.max = 255
        seekR.progress = Color.red(initialColor)
        seekG.progress = Color.green(initialColor)
        seekB.progress = Color.blue(initialColor)

        fun slidersToHex(): String {
            return String.format("#%02X%02X%02X", seekR.progress, seekG.progress, seekB.progress)
        }

        fun hexToSliders(hex: String) {
            try {
                val clean = hex.replace("#", "")
                val r = clean.substring(0, 2).toInt(16)
                val g = clean.substring(2, 4).toInt(16)
                val b = clean.substring(4, 6).toInt(16)
                seekR.progress = r
                seekG.progress = g
                seekB.progress = b
            } catch (_: Exception) {}
        }

        fun updatePreview() {
            val color = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
            setPreviewColor(preview, color)
            if (!ignoreChanges) {
                ignoreChanges = true
                editHex.setText(slidersToHex())
                ignoreChanges = false
            }
        }

        updatePreview()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                if (!ignoreChanges) updatePreview()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }

        seekR.setOnSeekBarChangeListener(listener)
        seekG.setOnSeekBarChangeListener(listener)
        seekB.setOnSeekBarChangeListener(listener)

        editHex.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (ignoreChanges) return
                val hex = s.toString().uppercase()
                if (hex.matches(Regex("^#?[0-9A-F]{6}$"))) {
                    ignoreChanges = true
                    hexToSliders(hex)
                    updatePreview()
                    ignoreChanges = false
                }
            }
        })

        val dialog = AlertDialog.Builder(activity)
            .setView(dialogView)
            .setPositiveButton("OK") { _, _ ->
                val color = Color.rgb(seekR.progress, seekG.progress, seekB.progress)
                onColorSelected(color)
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(activity, android.R.color.transparent)
        )
        dialog.show()
    }

    private fun setPreviewColor(view: View, color: Int) {
        val bg = view.background.mutate() as LayerDrawable
        val colorLayer = bg.findDrawableByLayerId(R.id.color_layer) as GradientDrawable
        colorLayer.setColor(color)
    }
}
