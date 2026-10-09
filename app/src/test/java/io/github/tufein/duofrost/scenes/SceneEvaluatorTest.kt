package io.github.tufein.duofrost.scenes

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SceneEvaluatorTest {
    private fun context(now: LocalDateTime = LocalDateTime.of(2026, 6, 19, 22, 0)) =
        SceneContext(now, "org.example.game", true, false, 50, false)

    private fun rule(id: String = "night", presetId: String? = "preset") = SceneRule(id, id, presetId = presetId)

    private fun evaluate(rules: List<SceneRule>, context: SceneContext = context(), groups: List<AppGroup> = emptyList(),
                         available: Set<String> = setOf("preset", "other")) =
        SceneEvaluator.evaluate(rules, groups, context, available)

    @Test fun highestPriorityWinsRegardlessOfInputOrder() {
        val ordinary = rule("ordinary").copy(priority = 0)
        val specific = rule("specific", "other").copy(priority = 10)
        assertEquals(specific, evaluate(listOf(ordinary, specific)).presetRule)
        assertEquals(specific, evaluate(listOf(specific, ordinary)).presetRule)
    }

    @Test fun samePriorityUsesIdInsteadOfSavedListOrder() {
        val first = rule("a", "other")
        val second = rule("b")
        assertEquals(first, evaluate(listOf(second, first)).presetRule)
        assertEquals(first, evaluate(listOf(first, second)).presetRule)
    }

    @Test fun modifiersCombineWithWinningPresetButNotLosingPreset() {
        val winner = rule("winner").copy(priority = 10, maxBrightnessPercent = 70)
        val loser = rule("loser", "other").copy(maxBrightnessPercent = 5)
        val night = rule("night", null).copy(maxBrightnessPercent = 40)
        val battery = rule("battery", null).copy(maxBrightnessPercent = 25)
        val result = evaluate(listOf(loser, battery, winner, night))
        assertEquals("preset", result.presetId)
        assertEquals(25, result.brightnessLimitPercent)
        assertEquals(25, result.modifierLimitPercent)
        assertEquals(listOf("winner", "battery", "night"), result.matchedRuleNames)
    }

    @Test fun presetLimitDoesNotBecomeAGlobalModifier() {
        val result = evaluate(listOf(rule().copy(maxBrightnessPercent = 20)))
        assertEquals(20, result.brightnessLimitPercent)
        assertNull(result.modifierLimitPercent)
    }

    @Test fun modifierWorksWithoutSelectingAPreset() {
        val result = evaluate(listOf(rule(presetId = null).copy(maxBrightnessPercent = 0)))
        assertNull(result.presetId)
        assertEquals(0, result.brightnessLimitPercent)
        assertEquals(listOf("night"), result.matchedRuleNames)
    }

    @Test fun deletedPresetRuleCannotSupplyItsLimitOrBlockAnotherRule() {
        val deleted = rule("deleted", "missing").copy(priority = 100, maxBrightnessPercent = 0)
        val valid = rule("valid")
        val result = evaluate(listOf(deleted, valid))
        assertEquals(valid, result.presetRule)
        assertNull(result.brightnessLimitPercent)
        assertEquals(listOf("valid"), result.matchedRuleNames)
    }

    @Test fun gameAndGroupRulesNeverApplyToHomeOrUnknownForeground() {
        val game = rule().copy(target = SceneTarget.GAMES)
        val group = rule("group").copy(target = SceneTarget.GROUP, groupId = "retro")
        val groups = listOf(AppGroup("retro", "Retro", setOf("org.example.game")))
        assertEquals(group, evaluate(listOf(group), groups = groups).presetRule)
        for (rule in listOf(game, group)) {
            assertNull(evaluate(listOf(rule), context().copy(isHome = true), groups).presetId)
            assertNull(evaluate(listOf(rule), context().copy(packageName = null), groups).presetId)
        }
        assertNull(evaluate(listOf(game), context().copy(isGame = false)).presetId)
    }

    @Test fun missingInvalidOrDuplicateGroupDoesNotBroadenTheRule() {
        val groupRule = rule().copy(target = SceneTarget.GROUP, groupId = "retro")
        assertNull(evaluate(listOf(groupRule)).presetId)
        val group = AppGroup("retro", "Retro", setOf("org.example.game"))
        assertNull(evaluate(listOf(groupRule), groups = listOf(group.copy(packages = setOf("bad package")))).presetId)
        assertNull(evaluate(listOf(groupRule), groups = listOf(group, group)).presetId)
    }

    @Test fun daytimeWindowIncludesStartAndExcludesEnd() {
        val timed = rule().copy(startMinute = 8 * 60, endMinute = 20 * 60)
        assertNotNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 6, 19, 8, 0))).presetId)
        assertNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 6, 19, 20, 0))).presetId)
    }

    @Test fun fridayNightContinuesIntoSaturdayUsingItsStartingDay() {
        val timed = rule().copy(startMinute = 22 * 60, endMinute = 7 * 60, daysOfWeek = setOf(5))
        assertNotNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 6, 19, 23, 0))).presetId)
        assertNotNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 6, 20, 1, 0))).presetId)
        assertNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 6, 19, 1, 0))).presetId)
        assertNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 6, 20, 7, 0))).presetId)
    }

    @Test fun equalTimesMeanAllDayOnTheCalendarDay() {
        val timed = rule().copy(startMinute = 10 * 60, endMinute = 10 * 60, daysOfWeek = setOf(6))
        assertNotNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 6, 20, 0, 0))).presetId)
        assertNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 6, 19, 23, 59))).presetId)
    }

    @Test fun repeatedDstHourKeepsLocalWallClockSemantics() {
        val timed = rule().copy(startMinute = 2 * 60, endMinute = 3 * 60, daysOfWeek = setOf(7))
        val local = LocalDateTime.of(2026, 10, 25, 2, 30)
        val zone = ZoneId.of("Europe/Brussels")
        val first = ZonedDateTime.ofLocal(local, zone, ZoneOffset.ofHours(2))
        val second = ZonedDateTime.ofLocal(local, zone, ZoneOffset.ofHours(1))
        assertNotEquals(first.toInstant(), second.toInstant())
        assertEquals(evaluate(listOf(timed), context(first.toLocalDateTime())),
            evaluate(listOf(timed), context(second.toLocalDateTime())))
    }

    @Test fun springClockJumpEvaluatesActualLocalTimeWithoutShiftingTheRule() {
        val timed = rule().copy(startMinute = 2 * 60 + 30, endMinute = 4 * 60)
        assertNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 3, 29, 1, 59))).presetId)
        assertNotNull(evaluate(listOf(timed), context(LocalDateTime.of(2026, 3, 29, 3, 0))).presetId)
    }

    @Test fun batteryAndChargingConditionsRequireKnownMatchingValues() {
        val battery = rule().copy(maxBatteryPercent = 20, charging = ChargingCondition.ON_BATTERY)
        assertNotNull(evaluate(listOf(battery), context().copy(batteryPercent = 20)).presetId)
        for (value in listOf(null, -1, 21, 101))
            assertNull(evaluate(listOf(battery), context().copy(batteryPercent = value)).presetId)
        assertNull(evaluate(listOf(battery), context().copy(batteryPercent = 10, isCharging = true)).presetId)
        assertNotNull(evaluate(listOf(rule().copy(charging = ChargingCondition.CHARGING)),
            context().copy(isCharging = true)).presetId)
    }

    @Test fun malformedAndDisabledRulesAreIgnored() {
        val invalid = listOf(rule().copy(enabled = false), rule().copy(id = " "), rule().copy(name = ""),
            rule().copy(startMinute = 60), rule().copy(startMinute = -1, endMinute = 50),
            rule().copy(daysOfWeek = emptySet()), rule().copy(daysOfWeek = setOf(8)),
            rule().copy(maxBrightnessPercent = 101), rule().copy(maxBatteryPercent = -1),
            rule().copy(priority = 101), rule(presetId = null), rule().copy(groupId = "stray"))
        invalid.forEach { assertNull("Invalid rule $it", evaluate(listOf(it)).presetId) }
    }

    @Test fun duplicateRuleIdsAreIgnoredRatherThanDependingOnInputOrder() {
        assertNull(evaluate(listOf(rule(), rule(presetId = "other"))).presetId)
    }
}
