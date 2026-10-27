package io.github.tufein.duofrost.services

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Android may store DuoFrost's service in short or full form; both must count as enabled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DuoFrostAccessibilityServiceEnabledTest {
    // The release package, so the short form below is the one AccessibilityManagerService writes.
    private val context: Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getPackageName() = "io.github.tufein.duofrost"
    }

    private fun setServices(accessibilityEnabled: Int, services: String) {
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, accessibilityEnabled)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
    }

    @Test
    fun shortFormWrittenByTheSystemCounts() {
        setServices(1, "com.example.other/.OtherService:io.github.tufein.duofrost/.services.DuoFrostAccessibilityService")
        assertTrue(DuoFrostAccessibilityService.isEnabled(context))
    }

    @Test
    fun fullFormCounts() {
        setServices(1, "io.github.tufein.duofrost/io.github.tufein.duofrost.services.DuoFrostAccessibilityService")
        assertTrue(DuoFrostAccessibilityService.isEnabled(context))
    }

    @Test
    fun otherServicesDoNotCount() {
        setServices(1, "com.example.other/.OtherService:io.github.tufein.duofrost/.services.LEDService")
        assertFalse(DuoFrostAccessibilityService.isEnabled(context))
    }

    @Test
    fun accessibilityOffDoesNotCount() {
        setServices(0, "io.github.tufein.duofrost/.services.DuoFrostAccessibilityService")
        assertFalse(DuoFrostAccessibilityService.isEnabled(context))
    }
}
