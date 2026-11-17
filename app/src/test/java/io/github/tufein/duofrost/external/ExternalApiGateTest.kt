package io.github.tufein.duofrost.external

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExternalApiGateTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)

    @Before fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    @Test fun approvedCallerStillNeedsTheMasterToggle() {
        ExternalApiGate.setApprovedPackages(app, setOf("com.example.companion"))
        assertTrue(ExternalApiGate.isApproved(app, "com.example.companion"))
        assertFalse(ExternalApiGate.isAllowed(app, "com.example.companion"))

        prefs.edit().putBoolean(ExternalApiGate.PREF_ENABLED, true).commit()
        assertTrue(ExternalApiGate.isAllowed(app, "com.example.companion"))
        assertFalse(ExternalApiGate.isAllowed(app, "com.example.other"))
        assertFalse(ExternalApiGate.isAllowed(app, ""))
    }

    @Test fun legacyBroadcastsAreOffUntilExplicitlyEnabled() {
        assertFalse(ExternalApiGate.isLegacyBroadcastEnabled(prefs))
        ExternalApiGate.setLegacyBroadcastEnabled(app, true)
        assertTrue(ExternalApiGate.isLegacyBroadcastEnabled(prefs))
        ExternalApiGate.setLegacyBroadcastEnabled(app, false)
        assertFalse(ExternalApiGate.isLegacyBroadcastEnabled(prefs))
    }

    @Test fun pendingRequestsAreRecordedOnceAndCleared() {
        ExternalApiGate.recordPendingRequest(app, "com.example.companion")
        ExternalApiGate.recordPendingRequest(app, "com.example.companion")
        assertEquals(setOf("com.example.companion"), ExternalApiGate.pendingPackages(app))

        // An approved package no longer needs to be listed as waiting.
        ExternalApiGate.setApprovedPackages(app, setOf("com.example.companion"))
        ExternalApiGate.recordPendingRequest(app, "com.example.companion")
        assertEquals(setOf("com.example.companion"), ExternalApiGate.pendingPackages(app))

        ExternalApiGate.removePending(app, "com.example.companion")
        assertTrue(ExternalApiGate.pendingPackages(app).isEmpty())
    }

    @Test fun approvalAndRevocationArePersistedForTheNextProcess() {
        ExternalApiGate.setApprovedPackages(app, setOf("com.example.companion", " "))
        assertEquals(setOf("com.example.companion"), ExternalApiGate.approvedPackages(app))
        assertEquals(setOf("com.example.companion"),
            prefs.getStringSet(ExternalApiGate.PREF_APPROVED_PACKAGES, emptySet()))

        ExternalApiGate.revoke(app, "com.example.companion")
        assertTrue(ExternalApiGate.approvedPackages(app).isEmpty())
    }

    @Test fun removingAPackageAlsoDropsItsApproval() {
        ExternalApiGate.setApprovedPackages(app, setOf("com.example.companion"))
        ExternalApiGate.recordPendingRequest(app, "com.example.companion")
        ExternalApiGate.revoke(app, "com.example.companion")
        assertFalse(ExternalApiGate.isApproved(app, "com.example.companion"))
        assertTrue(ExternalApiGate.pendingPackages(app).isEmpty())
    }
}
