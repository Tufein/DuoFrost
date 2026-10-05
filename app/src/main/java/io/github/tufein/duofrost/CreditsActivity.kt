package io.github.tufein.duofrost

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.tufein.duofrost.ui.SecondaryScreenUi

class CreditsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credits)
        SecondaryScreenUi.applyInsets(findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
        findViewById<TextView>(R.id.creditsVersion).text =
            getString(R.string.credits_version, BuildConfig.VERSION_NAME)
        findViewById<MaterialButton>(R.id.creditsRepository).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.repository_url)))
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.credits_no_browser, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.creditsLicense).setOnClickListener {
            val license = assets.open("LICENSE.txt").bufferedReader().use { it.readText() }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.credits_license)
                .setMessage(license)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        findViewById<MaterialButton>(R.id.creditsClose).setOnClickListener { finish() }
    }
}
