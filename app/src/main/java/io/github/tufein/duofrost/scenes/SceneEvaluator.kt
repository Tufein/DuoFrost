package io.github.tufein.duofrost.scenes

/** Pure selection. It never starts lighting or mutates a preset. */
object SceneEvaluator {
    fun evaluate(
        rules: List<SceneRule>,
        groups: List<AppGroup>,
        context: SceneContext,
        availablePresetIds: Set<String>
    ): SceneEvaluation {
        if (rules.size > SceneLimits.MAX_ITEMS || groups.size > SceneLimits.MAX_ITEMS) return EMPTY
        val duplicateRuleIds = rules.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val duplicateGroupIds = groups.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val usableGroups = groups.filter { it.isValid() && it.id !in duplicateGroupIds }.associateBy { it.id }
        val matching = rules.asSequence()
            .filter { it.id !in duplicateRuleIds && it.enabled && it.isValid() }
            .filter { it.presetId == null || it.presetId in availablePresetIds }
            .filter { matches(it, usableGroups, context) }
            .sortedWith(compareByDescending<SceneRule> { it.priority }.thenBy { it.id })
            .toList()
        val selected = matching.firstOrNull { it.presetId != null }
        // A losing preset rule does not dim the winning scene. Standalone
        // modifiers still combine with the selected preset's own maximum.
        val contributing = matching.filter { it.presetId == null || it === selected }
        return SceneEvaluation(
            selected?.presetId,
            selected,
            contributing.mapNotNull { it.maxBrightnessPercent }.minOrNull(),
            contributing.map { it.name },
            matching.filter { it.presetId == null }.mapNotNull { it.maxBrightnessPercent }.minOrNull()
        )
    }

    private fun matches(rule: SceneRule, groups: Map<String, AppGroup>, context: SceneContext): Boolean {
        when (rule.target) {
            SceneTarget.ANY -> Unit
            SceneTarget.GAMES -> if (context.isHome || context.packageName.isNullOrBlank() || !context.isGame) return false
            SceneTarget.GROUP -> {
                if (context.isHome || context.packageName.isNullOrBlank()) return false
                val group = groups[rule.groupId] ?: return false
                if (context.packageName !in group.packages) return false
            }
        }
        if (rule.charging == ChargingCondition.CHARGING && !context.isCharging) return false
        if (rule.charging == ChargingCondition.ON_BATTERY && context.isCharging) return false
        rule.maxBatteryPercent?.let { maximum ->
            val battery = context.batteryPercent ?: return false
            if (battery !in 0..100 || battery > maximum) return false
        }
        val minute = context.now.hour * 60 + context.now.minute
        val start = rule.startMinute
        val end = rule.endMinute
        val overnight = start != null && end != null && start > end
        val effectiveDate = if (overnight && minute < end!!) context.now.toLocalDate().minusDays(1)
            else context.now.toLocalDate()
        if (effectiveDate.dayOfWeek.value !in rule.daysOfWeek) return false
        if (start == null || end == null || start == end) return true
        return if (start < end) minute >= start && minute < end else minute >= start || minute < end
    }

    private val EMPTY = SceneEvaluation(null, null, null, emptyList())
}
