package io.github.tufein.duofrost.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.tufein.duofrost.external.ExternalApiGate
import io.github.tufein.duofrost.external.ExternalProfileStore
import io.github.tufein.duofrost.services.AppProfileManager
import io.github.tufein.duofrost.services.LEDService

class PackageRemovedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        if (pkg == context.packageName) return

        val prefs = context.getSharedPreferences(ExternalApiGate.PREFS_NAME, Context.MODE_PRIVATE)
        val removedPresetNames = ExternalProfileStore.removePresetsOwnedBy(prefs, pkg)
        if (removedPresetNames.isNotEmpty()) {
            AppProfileManager(prefs).removeMappingsReferencing(removedPresetNames)
        }

        if (LEDService.isRunning) {
            val clear = Intent(context, LEDService::class.java).apply {
                action = LEDService.ACTION_EXTERNAL_CLEAR
                putExtra(LEDService.EXTRA_EXTERNAL_CALLER_PACKAGE, pkg)
            }
            runCatching { context.startService(clear) }
        }
    }
}
