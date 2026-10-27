package io.github.tufein.duofrost.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import io.github.tufein.duofrost.animations.LedAnimation
import io.github.tufein.duofrost.animations.LedAnimationType
import io.github.tufein.duofrost.tools.LedController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, 35])
@LooperMode(LooperMode.Mode.PAUSED)
class LightingNotificationLifecycleTest {
    private lateinit var context: Context
    private lateinit var controller: ServiceController<LEDService>
    private lateinit var service: LEDService
    private lateinit var manager: NotificationManager
    private var destroyed = false

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("duofrost_sleep_timer", Context.MODE_PRIVATE).edit().clear().commit()
        LEDService.isRunning = false
        manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("test", "Test", NotificationManager.IMPORTANCE_LOW))
        controller = Robolectric.buildService(LEDService::class.java).create()
        service = controller.get()
    }

    @After fun cleanup() {
        if (!destroyed) controller.destroy()
        LEDService.isRunning = false
    }

    @Test fun unpinningUpdatesTheNotificationWithoutDemotingLightingOrReplacingItsEffect() {
        startLighting()
        val pendingEffect = field("pendingTransitionRunnable")
        val before = ServiceRecoveryStore.buildLastConfigurationIntent(context,
            context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE))!!
        assertTrue(notification().flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(pendingEffect)
        context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("persistent_notification_enabled", false).commit()

        service.onStartCommand(Intent(context, LEDService::class.java).apply {
            action = LEDService.ACTION_UPDATE_PARAMS
            putExtra(LEDService.EXTRA_PERSISTENT_NOTIFICATION, false)
        }, 0, 2)

        assertEquals(0, notification().flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(LEDService.isRunning)
        assertTrue(ServiceRecoveryStore.isDesiredRunning(context))
        assertFalse(shadowOf(service).isForegroundStopped)
        assertSame(pendingEffect, field("pendingTransitionRunnable"))
        val after = ServiceRecoveryStore.buildLastConfigurationIntent(context,
            context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE))!!
        assertEquals(before.getStringExtra("animationType"), after.getStringExtra("animationType"))
        assertEquals(before.getIntExtra("animationColor", 0), after.getIntExtra("animationColor", 0))
        assertFalse(after.getBooleanExtra(LEDService.EXTRA_PERSISTENT_NOTIFICATION, true))
    }

    @Test fun explicitStopClearsBothLightingNotificationsAndLateCallbacksCannotRepostThem() {
        startLighting()
        post(LightingNotifications.CAPTURE_PERMISSION_ID)
        post(UNRELATED_ID)
        assertNotNull(shadowOf(manager).getNotification(LightingNotifications.RUNNING_ID))
        assertNotNull(shadowOf(manager).getNotification(LightingNotifications.CAPTURE_PERMISSION_ID))

        service.onStartCommand(Intent(context, LEDService::class.java).apply {
            action = LEDService.ACTION_STOP
        }, 0, 2)

        assertFalse(LEDService.isRunning)
        assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
        assertTrue(shadowOf(service).isForegroundStopped)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertLightingNotificationsRemoved()
        assertNotNull(shadowOf(manager).getNotification(UNRELATED_ID))

        // Another instance can make the shared flag true while old callbacks
        // still reference this stopped instance. Its own stop guard must win.
        LEDService.isRunning = true
        call("updateForegroundNotification")
        call("showProjectionPromptNotification")
        call("awaitProjectionConsent")
        LEDService::class.java.getDeclaredMethod("promoteToForeground", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(service, false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertLightingNotificationsRemoved()
        assertTrue(shadowOf(service).isForegroundStopped)
        LEDService.isRunning = false
    }

    @Test fun savedToggleWinsOverAStartIntentBuiltBeforeTheUserChangedIt() {
        val queuedStart = Intent(context, LEDService::class.java).apply {
            putExtra("animationType", "STATIC")
            putExtra(LEDService.EXTRA_PERSISTENT_NOTIFICATION, true)
        }
        context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("persistent_notification_enabled", false).commit()

        service.onStartCommand(queuedStart, 0, 1)

        assertEquals(0, notification().flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(LEDService.isRunning)
        assertFalse(shadowOf(service).isForegroundStopped)
    }

    @Test fun savedToggleReconcilesWithoutAnActivityUpdateOrEffectRestart() {
        startLighting()
        val pendingEffect = field("pendingTransitionRunnable")

        context.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE).edit()
            .putBoolean("persistent_notification_enabled", false).commit()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, notification().flags and Notification.FLAG_ONGOING_EVENT)
        assertTrue(LEDService.isRunning)
        assertFalse(shadowOf(service).isForegroundStopped)
        assertSame(pendingEffect, field("pendingTransitionRunnable"))
    }

    @Test fun destroyRemovesNotificationBeforeAnimationCleanupAndPreservesRecoveryIntent() {
        startLighting()
        post(LightingNotifications.CAPTURE_PERMISSION_ID)
        post(UNRELATED_ID)
        var animationStopped = false
        val animation = object : LedAnimation(LedController()) {
            override val type = LedAnimationType.STATIC
            override val needsColorSelection = true
            override fun start() = Unit
            override fun stop() {
                animationStopped = true
                assertLightingNotificationsRemoved()
                assertNotNull(shadowOf(manager).getNotification(UNRELATED_ID))
            }
        }
        LEDService::class.java.getDeclaredField("currentAnimation")
            .apply { isAccessible = true }.set(service, animation)
        val recovery = context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).all

        controller.destroy()
        destroyed = true

        assertTrue(animationStopped)
        assertFalse(LEDService.isRunning)
        assertLightingNotificationsRemoved()
        assertEquals(recovery, context.getSharedPreferences("duofrost_service_state", Context.MODE_PRIVATE).all)
    }

    @Test fun stopAfterProcessLossClearsOrphanNotificationsWithoutStartingAService() {
        post(LightingNotifications.RUNNING_ID)
        post(LightingNotifications.CAPTURE_PERMISSION_ID)
        post(UNRELATED_ID)
        val app = shadowOf(RuntimeEnvironment.getApplication())

        LightingStopper.stop(context)

        assertLightingNotificationsRemoved()
        assertNotNull(shadowOf(manager).getNotification(UNRELATED_ID))
        assertNull(app.nextStartedService)
        assertFalse(LEDService.isRunning)
        assertFalse(ServiceRecoveryStore.isDesiredRunning(context))
    }

    private fun startLighting() {
        service.onStartCommand(Intent(context, LEDService::class.java).apply {
            putExtra("animationType", "STATIC")
            putExtra("animationColor", 0xff224466.toInt())
            putExtra(LEDService.EXTRA_PERSISTENT_NOTIFICATION, true)
        }, 0, 1)
    }

    private fun notification(): Notification =
        shadowOf(manager).getNotification(LightingNotifications.RUNNING_ID)

    private fun post(id: Int) = manager.notify(id, Notification.Builder(context, "test")
        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Test").build())

    private fun assertLightingNotificationsRemoved() {
        assertNull(shadowOf(manager).getNotification(LightingNotifications.RUNNING_ID))
        assertNull(shadowOf(manager).getNotification(LightingNotifications.CAPTURE_PERMISSION_ID))
    }

    private fun field(name: String): Any? = LEDService::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(service)

    private fun call(name: String) = LEDService::class.java.getDeclaredMethod(name)
        .apply { isAccessible = true }.invoke(service)

    private companion object { const val UNRELATED_ID = 9001 }
}
