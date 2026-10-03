package io.github.tufein.duofrost

import android.app.Application
import android.content.Context
import io.github.tufein.duofrost.presets.PresetMigration
import io.github.tufein.duofrost.tools.CrashReporter

class DuoFrostApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        runCatching {
            PresetMigration.run(getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE))
        }
    }
}
