package io.github.tufein.duofrost.external

import android.content.Context
import android.content.SharedPreferences

/**
 * Single gate for the external API: the master toggle plus an explicit
 * per-package approval. Approval is only trustworthy for bound-service calls,
 * where [ExternalApiService] resolves the caller UID from the binder
 * transaction; legacy broadcasts cannot recover it and stay opt-in.
 */
object ExternalApiGate {

    const val PREFS_NAME = "bifrost_prefs"
    const val PREF_ENABLED = "external_api_enabled"
    const val PREF_APPROVED_PACKAGES = "external_api_approved_packages"
    const val PREF_PENDING_PACKAGES = "external_api_pending_packages"
    const val PREF_LEGACY_BROADCAST_ENABLED = "external_api_legacy_broadcast_enabled"

    fun isMasterEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_ENABLED, false)

    /**
     * Legacy broadcasts always report this app's own UID as the caller, so no
     * allowlist entry can prove who sent the command. They are off by default
     * and only enabled deliberately for older companions.
     */
    fun isLegacyBroadcastEnabled(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(PREF_LEGACY_BROADCAST_ENABLED, false)

    fun approvedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(PREF_APPROVED_PACKAGES, emptySet())
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    fun isApproved(context: Context, callerPackage: String): Boolean =
        callerPackage.isNotBlank() && callerPackage in approvedPackages(context)

    /**
     * Packages that tried to bind before approval. Recorded so the approval
     * screen can list callers that Android's package visibility would hide.
     */
    fun pendingPackages(context: Context): Set<String> =
        prefs(context).getStringSet(PREF_PENDING_PACKAGES, emptySet())
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    fun recordPendingRequest(context: Context, callerPackage: String) {
        if (callerPackage.isBlank() || isApproved(context, callerPackage)) return
        val pending = pendingPackages(context)
        if (callerPackage in pending) return
        prefs(context).edit().putStringSet(PREF_PENDING_PACKAGES, pending + callerPackage).commit()
    }

    fun removePending(context: Context, callerPackage: String) {
        val pending = pendingPackages(context)
        if (callerPackage !in pending) return
        prefs(context).edit().putStringSet(PREF_PENDING_PACKAGES, pending - callerPackage).commit()
    }

    fun isAllowed(context: Context, callerPackage: String): Boolean =
        isMasterEnabled(prefs(context)) && isApproved(context, callerPackage)

    fun setApprovedPackages(context: Context, packages: Set<String>) {
        val cleaned = packages.filter { it.isNotBlank() }.toSet()
        // commit(): a revoked approval must not survive an immediate process kill.
        prefs(context).edit().putStringSet(PREF_APPROVED_PACKAGES, cleaned).commit()
    }

    fun revoke(context: Context, callerPackage: String) {
        val current = approvedPackages(context)
        removePending(context, callerPackage)
        if (callerPackage !in current) return
        setApprovedPackages(context, current - callerPackage)
    }

    fun setLegacyBroadcastEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_LEGACY_BROADCAST_ENABLED, enabled).commit()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
