package io.github.tufein.duofrost

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import io.github.tufein.duofrost.services.LEDService
import io.github.tufein.duofrost.tools.LedDiagnosticFrame

class LedTestActivity : AppCompatActivity() {
    private var step = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_led_test)
        step = (savedInstanceState?.getInt("step") ?: 0).coerceIn(LedDiagnosticFrame.steps.indices)
        findViewById<MaterialButton>(R.id.ledTestPrevious).setOnClickListener {
            step = (step + LedDiagnosticFrame.steps.size - 1) % LedDiagnosticFrame.steps.size
            showStep()
        }
        findViewById<MaterialButton>(R.id.ledTestNext).setOnClickListener {
            step = (step + 1) % LedDiagnosticFrame.steps.size
            showStep()
        }
        findViewById<MaterialButton>(R.id.ledTestFinish).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        if (!LEDService.isRunning) {
            Toast.makeText(this, R.string.quick_controls_start_first, Toast.LENGTH_LONG).show()
            finish()
        } else showStep()
    }

    private fun showStep() {
        if (!LEDService.isRunning) { finish(); return }
        findViewById<TextView>(R.id.ledTestStep).text = getString(R.string.led_test_step,
            step + 1, LedDiagnosticFrame.steps.size, LedDiagnosticFrame.steps[step].label)
        sendStep(step)
    }

    override fun onStop() {
        if (LEDService.isRunning) sendStep(-1)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("step", step)
        super.onSaveInstanceState(outState)
    }

    private fun sendStep(index: Int) = startService(Intent(this, LEDService::class.java).apply {
        action = LEDService.ACTION_LED_TEST
        putExtra(LEDService.EXTRA_TEST_STEP, index)
    })
}
