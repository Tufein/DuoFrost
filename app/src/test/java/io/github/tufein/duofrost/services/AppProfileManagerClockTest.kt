package io.github.tufein.duofrost.services

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AppProfileManagerClockTest {
    private lateinit var context: Context
    private lateinit var manager: AppProfileManager
    private var elapsed = 1_000L
    private var wallTime = 1_000_000L

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("profile_clock_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        manager = AppProfileManager(prefs, { elapsed }, { wallTime })
        shadowOf(context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
    }

    private fun event(packageName: String) {
        shadowOf(context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
            .addEvent(packageName, wallTime - 1, UsageEvents.Event.ACTIVITY_RESUMED)
    }

    @Test fun backwardWallClockChangeCannotPinForegroundCache() {
        event("com.example.first")
        assertEquals("com.example.first", manager.getForegroundPackage(context))
        wallTime -= 600_000L
        event("com.example.second")
        elapsed += 350L
        assertEquals("com.example.second", manager.getForegroundPackage(context))
    }

    @Test fun wallClockJumpDoesNotBypassShortQueryCacheAndInitialBootIsQueryable() {
        elapsed = 0
        event("com.example.first")
        assertEquals("com.example.first", manager.getForegroundPackage(context))
        wallTime += 600_000L
        event("com.example.second")
        elapsed = 349L
        assertEquals("com.example.first", manager.getForegroundPackage(context))
        elapsed = 350L
        assertEquals("com.example.second", manager.getForegroundPackage(context))
    }
}
