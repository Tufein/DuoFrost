package io.github.tufein.duofrost.services

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.tufein.duofrost.BuildConfig
import java.time.Instant

/** Read-only metadata collected only when the user chooses to share a report. */
object BackgroundDiagnostics {
    private const val MAX_EXIT_RECORDS = 3

    fun buildReport(context: Context): String {
        val appContext = context.applicationContext ?: context
        return buildString {
            appendLine("DuoFrost background report")
            appendLine("Generated (UTC): ${readValue { Instant.now() }}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Application ID: ${BuildConfig.APPLICATION_ID}")
            appendLine("Android API: ${Build.VERSION.SDK_INT}")
            appendLine("Device: ${deviceText(Build.BRAND)} ${deviceText(Build.MODEL)}")
            appendLine("Keep running: ${readValue {
                ServiceRecoveryStore.isKeepRunningEnabled(
                    appContext.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
                )
            }}")
            appendLine("Auto start: ${readValue {
                HeimdallStartupManager.isAutoStartEnabled(
                    appContext.getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE)
                )
            }}")
            appendLine("Desired running: ${readValue { ServiceRecoveryStore.isDesiredRunning(appContext) }}")
            appendLine("Service running: ${LEDService.isRunning}")
            appendLine("Waiting for capture permission: ${LEDService.isWaitingForCapturePermission}")
            appendLine("Battery optimization exempt: ${readValue {
                appContext.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(appContext.packageName)
            }}")
            appendLine("Notifications allowed: ${readValue {
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED &&
                    NotificationManagerCompat.from(appContext).areNotificationsEnabled()
            }}")
            appendLine()
            appendLine("Recent process exits (this app only, up to $MAX_EXIT_RECORDS):")
            appendExitRecords(appContext)
            appendLine()
            appendLine("USER_REQUESTED can mean removal from Recents or a system Stop/Force stop.")
            appendLine("This report contains no traces, presets, colors or other apps' history.")
        }
    }

    private fun StringBuilder.appendExitRecords(context: Context) {
        try {
            val manager = context.getSystemService(ActivityManager::class.java)
            if (manager == null) {
                appendLine("Unavailable: Android activity service is not accessible.")
                return
            }
            val records = manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_RECORDS)
                .take(MAX_EXIT_RECORDS)
            if (records.isEmpty()) {
                appendLine("No process exit records available.")
                return
            }
            records.forEachIndexed { index, record ->
                val timestamp = readValue {
                    record.timestamp.takeIf { it > 0L }?.let(Instant::ofEpochMilli)
                }
                appendLine("${index + 1}. timestamp (UTC): $timestamp; " +
                    "reason: ${reasonLabel(record.reason)} (${record.reason}); " +
                    "importance: ${record.importance}; status: ${record.status}")
            }
        } catch (_: Exception) {
            appendLine("Unavailable: Android could not provide process exit records.")
        }
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        else -> "UNRECOGNIZED"
    }

    private fun readValue(read: () -> Any?): String = try {
        read()?.toString() ?: "unavailable"
    } catch (_: Exception) {
        "unavailable"
    }

    private fun deviceText(value: String): String = value.replace('\n', ' ').replace('\r', ' ').take(128)
}
