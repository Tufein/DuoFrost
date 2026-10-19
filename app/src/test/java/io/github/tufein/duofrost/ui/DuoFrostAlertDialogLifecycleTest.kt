package io.github.tufein.duofrost.ui

import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.tufein.duofrost.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Real dialog windows, without MainActivity's LED service and device setup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DuoFrostAlertDialogLifecycleTest {
    private val hosts = mutableListOf<ActivityController<AppCompatActivity>>()
    private val dialogs = mutableListOf<AlertDialog>()
    private var confirmed = 0
    private var cancelled = 0

    @After fun cleanUpWindows() {
        dialogs.forEach { it.dismiss() }
        hosts.forEach { host ->
            if (!host.get().isDestroyed) host.pause().stop().destroy()
        }
        idleMainLooper()
    }

    @Test fun destroyingHostClosesVisibleDialogWithoutConfirmingOrCancelling() {
        val host = newHost()
        val dialog = showAlert(host.get())
        assertTrue(dialog.isShowing)

        host.pause().stop().destroy()
        idleMainLooper()

        assertFalse("A destroyed Activity must not retain a dialog window", dialog.isShowing)
        assertEquals(0, confirmed)
        assertEquals(0, cancelled)
    }

    @Test fun recreationClosesOldWindowAndAnUnconfirmedWelcomeCanBeShownAgain() {
        val host = newHost()
        val oldActivity = host.get()
        val prefs = oldActivity.getSharedPreferences("dialog-lifecycle", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val firstDialog = showAlert(oldActivity) {
            prefs.edit().putBoolean("first_launch_alert_shown", true).apply()
        }

        host.recreate()
        idleMainLooper()

        assertTrue(oldActivity.isDestroyed)
        assertNotSame(oldActivity, host.get())
        assertFalse("Recreation must close the previous Activity's window", firstDialog.isShowing)
        assertFalse("Lifecycle teardown is not user acceptance",
            prefs.getBoolean("first_launch_alert_shown", false))
        assertEquals(0, confirmed)
        assertEquals(0, cancelled)

        val replacementDialog = showAlert(host.get()) {
            prefs.edit().putBoolean("first_launch_alert_shown", true).apply()
        }
        assertTrue(replacementDialog.isShowing)
        assertNotSame(firstDialog, replacementDialog)
        assertFalse(prefs.getBoolean("first_launch_alert_shown", false))

        replacementDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleMainLooper()
        assertFalse(replacementDialog.isShowing)
        assertTrue(prefs.getBoolean("first_launch_alert_shown", false))
        assertEquals(1, confirmed)
        assertEquals(0, cancelled)
    }

    @Test fun explicitConfirmationRunsOnceAndDestroyDoesNotRunCancel() {
        val host = newHost()
        val dialog = showAlert(host.get())

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idleMainLooper()
        assertFalse(dialog.isShowing)
        assertEquals(1, confirmed)
        assertEquals(0, cancelled)

        host.pause().stop().destroy()
        idleMainLooper()
        assertEquals(1, confirmed)
        assertEquals(0, cancelled)
    }

    @Test fun destroyedHostDoesNotShowADialogOrRunCallbacks() {
        val host = newHost()
        val activity = host.get()
        host.pause().stop().destroy()
        idleMainLooper()
        assertTrue(activity.isDestroyed)
        val previous = ShadowDialog.getLatestDialog()

        requestAlert(activity)
        idleMainLooper()

        assertSame(previous, ShadowDialog.getLatestDialog())
        assertEquals(0, confirmed)
        assertEquals(0, cancelled)
    }

    @Test fun finishingHostDoesNotShowADialogOrRunCallbacks() {
        val host = newHost()
        val activity = host.get()
        activity.finish()
        assertTrue(activity.isFinishing)
        val previous = ShadowDialog.getLatestDialog()

        requestAlert(activity)
        idleMainLooper()

        assertSame(previous, ShadowDialog.getLatestDialog())
        assertEquals(0, confirmed)
        assertEquals(0, cancelled)
    }

    private fun newHost(): ActivityController<AppCompatActivity> {
        val host = Robolectric.buildActivity(AppCompatActivity::class.java)
        host.get().setTheme(R.style.Theme_DuoFrost)
        hosts += host
        return host.setup()
    }

    private fun showAlert(activity: AppCompatActivity, onConfirm: () -> Unit = {}): AlertDialog {
        requestAlert(activity, onConfirm)
        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("The live host must show its warning", dialog)
        return (dialog as AlertDialog).also { dialogs += it }
    }

    private fun requestAlert(activity: AppCompatActivity, onConfirm: () -> Unit = {}) {
        DuoFrostAlertDialog().show(
            activity = activity,
            title = "Welcome",
            subtitle = null,
            body = "Please confirm this warning.",
            positiveLabelResId = R.string.alert_action_ok,
            negativeLabelResId = R.string.action_cancel,
            cancelable = false,
            onConfirm = { confirmed++; onConfirm() },
            onCancel = { cancelled++ }
        )
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()
}
