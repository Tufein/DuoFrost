package io.github.tufein.duofrost.schedule

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object ScheduleEvaluator {

    fun ruleInForce(rules: List<ScheduleRule>, moment: LocalDateTime): ScheduleRule? {
        val matching = rules.filter { it.covers(moment) }
        if (matching.isEmpty()) return null
        return matching.firstOrNull { it.dateWindow != null } ?: matching.first()
    }

    fun nextBoundary(rules: List<ScheduleRule>, moment: LocalDateTime): LocalDateTime? {
        val enabled = rules.filter { it.enabled }
        if (enabled.isEmpty()) return null

        val candidates = mutableListOf<LocalDateTime>()
        val startOfDay = moment.truncatedTo(ChronoUnit.DAYS)

        enabled.forEach { rule ->
            listOf(rule.startMinuteOfDay, rule.endMinuteOfDay).forEach { minute ->
                val today = startOfDay.plusMinutes(minute.toLong())
                candidates += if (today.isAfter(moment)) today else today.plusDays(1)
            }
        }

        // An all-day rule can change at midnight even if its saved time edges
        // are identical. Calendar days also keep this boundary local across DST.
        if (enabled.any { it.dateWindow != null || it.daysOfWeek != ScheduleRule.ALL_DAYS }) {
            candidates += startOfDay.plusDays(1)
        }

        return candidates.minOrNull()
    }

    /**
     * Resolve wall-clock edges before ordering them. A repeated local time has
     * two valid offsets; a skipped local time has none. The clock transition
     * itself is also an edge, since jumping or rewinding can change the rule.
     */
    fun nextBoundary(rules: List<ScheduleRule>, moment: ZonedDateTime): ZonedDateTime? {
        val enabled = rules.filter { it.enabled }
        if (enabled.isEmpty()) return null

        val now = moment.toInstant()
        val zone = moment.zone
        val zoneRules = zone.rules
        var next: Instant? = null

        fun consider(candidate: Instant) {
            val current = next
            if (candidate.isAfter(now) && (current == null || candidate.isBefore(current))) {
                next = candidate
            }
        }

        val needsMidnight = enabled.any {
            it.dateWindow != null || it.daysOfWeek != ScheduleRule.ALL_DAYS
        }
        // Two dates suffice: each enabled rule has a daily edge, so there is
        // always a valid future candidate by tomorrow in ordinary time zones.
        // Skipped days are covered by their zone transition instead.
        for (dayOffset in 0L..1L) {
            val date = moment.toLocalDate().plusDays(dayOffset)
            enabled.forEach { rule ->
                intArrayOf(rule.startMinuteOfDay, rule.endMinuteOfDay).forEach { minute ->
                    val local = date.atTime(minute / 60, minute % 60)
                    zoneRules.getValidOffsets(local).forEach { offset ->
                        consider(local.toInstant(offset))
                    }
                }
            }
            if (needsMidnight) {
                val midnight = date.atStartOfDay()
                zoneRules.getValidOffsets(midnight).forEach { offset ->
                    consider(midnight.toInstant(offset))
                }
            }
        }

        zoneRules.nextTransition(now)?.let { consider(it.instant) }
        return next?.atZone(zone)
    }
}
