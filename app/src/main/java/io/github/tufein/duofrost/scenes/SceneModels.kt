package io.github.tufein.duofrost.scenes

import java.time.LocalDateTime

enum class SceneTarget { ANY, GAMES, GROUP }
enum class ChargingCondition { ANY, CHARGING, ON_BATTERY }

/** Limits apply equally to editor input and restored configuration. */
object SceneLimits {
    const val MAX_ITEMS = 100
    const val MAX_PACKAGES_PER_GROUP = 200
    const val MAX_ID_LENGTH = 128
    const val MAX_NAME_LENGTH = 120
    const val MAX_PACKAGE_LENGTH = 255
    const val MIN_PRIORITY = -100
    const val MAX_PRIORITY = 100
    const val MAX_TEMPORARY_MINUTES = 1440
    internal const val MAX_JSON_LENGTH = 2_000_000

    internal fun validId(value: String): Boolean =
        value.length in 1..MAX_ID_LENGTH && value == value.trim() && value.none { it.isISOControl() }

    internal fun validName(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_NAME_LENGTH && value == value.trim() &&
            value.none { it.isISOControl() }

    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
    internal fun validPackage(value: String): Boolean =
        value.length in 1..MAX_PACKAGE_LENGTH && packagePattern.matches(value)
}

data class AppGroup(val id: String, val name: String, val packages: Set<String>) {
    fun isValid(): Boolean = SceneLimits.validId(id) && SceneLimits.validName(name) &&
        packages.size in 1..SceneLimits.MAX_PACKAGES_PER_GROUP &&
        packages.all(SceneLimits::validPackage)
}

data class SceneRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val presetId: String? = null,
    val target: SceneTarget = SceneTarget.ANY,
    val groupId: String? = null,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val daysOfWeek: Set<Int> = (1..7).toSet(),
    val maxBatteryPercent: Int? = null,
    val charging: ChargingCondition = ChargingCondition.ANY,
    val maxBrightnessPercent: Int? = null,
    val priority: Int = 0
) {
    fun isValid(): Boolean {
        if (!SceneLimits.validId(id) || !SceneLimits.validName(name)) return false
        if (presetId != null && !SceneLimits.validId(presetId)) return false
        if (presetId == null && maxBrightnessPercent == null) return false
        if (target == SceneTarget.GROUP) {
            if (groupId == null || !SceneLimits.validId(groupId)) return false
        } else if (groupId != null) return false
        if ((startMinute == null) != (endMinute == null)) return false
        if (startMinute != null && startMinute !in 0..1439) return false
        if (endMinute != null && endMinute !in 0..1439) return false
        if (daysOfWeek.isEmpty() || daysOfWeek.any { it !in 1..7 }) return false
        if (maxBatteryPercent != null && maxBatteryPercent !in 0..100) return false
        if (maxBrightnessPercent != null && maxBrightnessPercent !in 0..100) return false
        return priority in SceneLimits.MIN_PRIORITY..SceneLimits.MAX_PRIORITY
    }
}

data class SceneContext(
    val now: LocalDateTime,
    val packageName: String?,
    val isGame: Boolean,
    val isHome: Boolean,
    val batteryPercent: Int?,
    val isCharging: Boolean
)

data class SceneEvaluation(
    val presetId: String?,
    val presetRule: SceneRule?,
    val brightnessLimitPercent: Int?,
    val matchedRuleNames: List<String>,
    val modifierLimitPercent: Int? = null
)

data class TemporaryScene(
    val presetId: String,
    val expiresAtElapsed: Long,
    val bootCount: Int
)
