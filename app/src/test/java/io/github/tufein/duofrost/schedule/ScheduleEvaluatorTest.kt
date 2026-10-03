package io.github.tufein.duofrost.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ScheduleEvaluatorTest {

    private fun rule(
        id: String,
        startHour: Int,
        endHour: Int,
        window: DateWindow? = null,
        action: ScheduleAction = ScheduleAction.PlayPreset(id),
        enabled: Boolean = true,
        days: Set<DayOfWeek> = ScheduleRule.ALL_DAYS
    ) = ScheduleRule(
        id = id,
        label = id,
        enabled = enabled,
        startMinuteOfDay = startHour * 60,
        endMinuteOfDay = endHour * 60,
        dateWindow = window,
        action = action,
        daysOfWeek = days
    )

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, month, day, hour, minute)


    @Test fun plainWindowCoversItsOwnHours() {
        val night = rule("night", 20, 23)
        assertEquals(night, ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 21)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 19)))
    }

    @Test fun windowEndIsExclusiveSoNeighboursDoNotOverlap() {
        val day = rule("day", 7, 20)
        assertTrue(day.coversTime(19 * 60 + 59))
        assertTrue(!day.coversTime(20 * 60))
    }

    @Test fun windowWrappingMidnightCoversBothSidesOfIt() {
        val night = rule("night", 20, 7)
        assertEquals(night, ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 23)))
        assertEquals(night, ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 2)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 12)))
    }

    @Test fun disabledRuleNeverApplies() {
        val night = rule("night", 20, 7, enabled = false)
        assertNull(ScheduleEvaluator.ruleInForce(listOf(night), at(6, 15, 23)))
    }


    @Test fun datedRuleAppliesOnlyInsideItsSeason() {
        val christmas = rule(
            "christmas", 0, 0,
            window = DateWindow(MonthDay.of(12, 1), MonthDay.of(12, 31))
        )
        assertEquals(christmas, ScheduleEvaluator.ruleInForce(listOf(christmas), at(12, 24, 18)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(christmas), at(11, 30, 18)))
    }

    @Test fun dateWindowWrappingTheNewYearHoldsAcrossIt() {
        val newYear = DateWindow(MonthDay.of(12, 20), MonthDay.of(1, 5))
        assertTrue(newYear.contains(at(12, 31, 12).toLocalDate()))
        assertTrue(newYear.contains(at(1, 2, 12).toLocalDate()))
        assertTrue(!newYear.contains(at(6, 15, 12).toLocalDate()))
    }

    @Test fun nightRuleStartedBeforeMidnightKeepsItsStartingDaysSeason() {
        val december = rule(
            "december", 20, 7,
            window = DateWindow(MonthDay.of(12, 1), MonthDay.of(12, 31))
        )
        assertEquals(december, ScheduleEvaluator.ruleInForce(listOf(december), at(1, 1, 1)))
    }


    @Test fun datedRuleOutranksTheEverydayRuleItOverlaps() {
        val everyNight = rule("blue-night", 20, 7)
        val christmas = rule(
            "christmas", 20, 7,
            window = DateWindow(MonthDay.of(12, 1), MonthDay.of(12, 31))
        )
        val rules = listOf(everyNight, christmas)
        assertEquals(christmas, ScheduleEvaluator.ruleInForce(rules, at(12, 24, 22)))
        assertEquals(everyNight, ScheduleEvaluator.ruleInForce(rules, at(6, 24, 22)))
    }

    @Test fun amongEqualsTheFirstRuleWins() {
        val first = rule("first", 20, 23)
        val second = rule("second", 21, 23)
        assertEquals(first, ScheduleEvaluator.ruleInForce(listOf(first, second), at(6, 15, 22)))
    }

    @Test fun turnOffIsAnActionThatCanBeatAPreset() {
        val daylightOff = rule("off", 7, 20, action = ScheduleAction.TurnOff)
        val inForce = ScheduleEvaluator.ruleInForce(listOf(daylightOff), at(6, 15, 12))
        assertEquals(ScheduleAction.TurnOff, inForce?.action)
    }


    @Test fun nextBoundaryIsTheNearestEdgeOfAnyRule() {
        val night = rule("night", 20, 7)
        assertEquals(at(6, 15, 20), ScheduleEvaluator.nextBoundary(listOf(night), at(6, 15, 18)))
        assertEquals(at(6, 16, 7), ScheduleEvaluator.nextBoundary(listOf(night), at(6, 15, 21)))
    }

    @Test fun edgesOfRulesNotInForceStillCount() {
        val night = rule("night", 20, 23)
        val morning = rule("morning", 8, 9)
        assertEquals(
            at(6, 15, 8),
            ScheduleEvaluator.nextBoundary(listOf(night, morning), at(6, 15, 6))
        )
    }

    @Test fun midnightIsABoundaryWhenASeasonalRuleExists() {
        val christmas = rule(
            "christmas", 10, 10,
            window = DateWindow(MonthDay.of(12, 1), MonthDay.of(12, 31))
        )
        assertEquals(
            at(12, 1, 0),
            ScheduleEvaluator.nextBoundary(listOf(christmas), at(11, 30, 23))
        )
    }

    @Test fun emptyOrFullyDisabledScheduleHasNoBoundary() {
        assertNull(ScheduleEvaluator.nextBoundary(emptyList(), at(6, 15, 12)))
        assertNull(
            ScheduleEvaluator.nextBoundary(
                listOf(rule("off", 20, 7, enabled = false)),
                at(6, 15, 12)
            )
        )
    }


    @Test fun rulesSurviveARoundTripThroughJson() {
        val original = rule(
            "christmas", 20, 7,
            window = DateWindow(MonthDay.of(12, 20), MonthDay.of(1, 5)),
            action = ScheduleAction.PlayPreset("Red & Green")
        )
        assertEquals(original, ScheduleRule.parse(original.serialise()))
    }

    @Test fun turnOffSurvivesARoundTripThroughJson() {
        val original = rule("off", 7, 20, action = ScheduleAction.TurnOff)
        assertEquals(original, ScheduleRule.parse(original.serialise()))
    }

    @Test fun malformedRuleIsDroppedRatherThanCrashing() {
        assertNull(ScheduleRule.parse(org.json.JSONObject("""{"id":"x"}""")))
    }

    @Test fun weekdaysAndWeekendsCanUseDifferentPresets() {
        val weekdays = rule("weekdays", 7, 20, days = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
        ))
        val weekend = rule("weekend", 7, 20, days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val rules = listOf(weekdays, weekend)
        assertEquals(weekdays, ScheduleEvaluator.ruleInForce(rules, at(6, 19, 12)))
        assertEquals(weekend, ScheduleEvaluator.ruleInForce(rules, at(6, 20, 12)))
        assertEquals(weekend, ScheduleEvaluator.ruleInForce(rules, at(6, 21, 12)))
        assertEquals(weekdays, ScheduleEvaluator.ruleInForce(rules, at(6, 22, 12)))
    }

    @Test fun fridayNightContinuesIntoSaturdayButNotIntoFridayMorning() {
        val friday = rule("friday", 20, 7, days = setOf(DayOfWeek.FRIDAY))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(friday), at(6, 19, 1)))
        assertEquals(friday, ScheduleEvaluator.ruleInForce(listOf(friday), at(6, 19, 23)))
        assertEquals(friday, ScheduleEvaluator.ruleInForce(listOf(friday), at(6, 20, 1)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(friday), at(6, 20, 7)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(friday), at(6, 20, 23)))
    }

    @Test fun sundayNightContinuesAcrossTheWeekBoundary() {
        val sunday = rule("sunday", 20, 7, days = setOf(DayOfWeek.SUNDAY))
        assertEquals(sunday, ScheduleEvaluator.ruleInForce(listOf(sunday), at(6, 21, 23)))
        assertEquals(sunday, ScheduleEvaluator.ruleInForce(listOf(sunday), at(6, 22, 1)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(sunday), at(6, 22, 7)))
    }

    @Test fun allDayRuleUsesTheCalendarDayEvenWhenEqualTimesAreNotMidnight() {
        val saturday = rule("saturday", 10, 10, days = setOf(DayOfWeek.SATURDAY))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(saturday), at(6, 19, 23)))
        assertEquals(saturday, ScheduleEvaluator.ruleInForce(listOf(saturday), at(6, 20, 0)))
        assertEquals(saturday, ScheduleEvaluator.ruleInForce(listOf(saturday), at(6, 20, 23)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(saturday), at(6, 21, 0)))
    }

    @Test fun overnightRuleKeepsBothTheSeasonAndWeekdayOfItsStartingDay() {
        val christmasFriday = rule(
            "christmasFriday", 20, 7,
            window = DateWindow(MonthDay.of(12, 25), MonthDay.of(12, 25)),
            days = setOf(DayOfWeek.FRIDAY)
        )
        assertNull(ScheduleEvaluator.ruleInForce(listOf(christmasFriday), at(12, 25, 1)))
        assertEquals(christmasFriday, ScheduleEvaluator.ruleInForce(listOf(christmasFriday), at(12, 25, 23)))
        assertEquals(christmasFriday, ScheduleEvaluator.ruleInForce(listOf(christmasFriday), at(12, 26, 1)))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(christmasFriday), at(12, 26, 7)))
    }

    @Test fun restrictedAllDayRuleRechecksAtMidnightBeforeItsSavedTimeEdge() {
        val saturday = rule("saturday", 10, 10, days = setOf(DayOfWeek.SATURDAY))
        assertEquals(at(6, 20, 0), ScheduleEvaluator.nextBoundary(listOf(saturday), at(6, 19, 23)))
        assertEquals(at(6, 21, 0), ScheduleEvaluator.nextBoundary(listOf(saturday), at(6, 20, 23)))
    }

    @Test fun disabledWeekdayRestrictionDoesNotAddAnUnneededMidnightAlarm() {
        val disabled = rule("disabled", 10, 10, enabled = false, days = setOf(DayOfWeek.SATURDAY))
        val everyday = rule("everyday", 8, 9)
        assertEquals(at(6, 20, 8), ScheduleEvaluator.nextBoundary(listOf(disabled, everyday), at(6, 19, 23)))
    }

    @Test fun nextMidnightFollowsTheLocalCalendarOnTheSpringDstChange() {
        val sunday = rule("sunday", 0, 0, days = setOf(DayOfWeek.SUNDAY))
        val now = at(3, 29, 0)
        val next = ScheduleEvaluator.nextBoundary(listOf(sunday), now)
        assertEquals(at(3, 30, 0), next)
        val zone = ZoneId.of("Europe/Brussels")
        assertEquals(23L, Duration.between(now.atZone(zone), next!!.atZone(zone)).toHours())
    }

    @Test fun nextMidnightFollowsTheLocalCalendarOnTheAutumnDstChange() {
        val sunday = rule("sunday", 0, 0, days = setOf(DayOfWeek.SUNDAY))
        val now = at(10, 25, 0)
        val next = ScheduleEvaluator.nextBoundary(listOf(sunday), now)
        assertEquals(at(10, 26, 0), next)
        val zone = ZoneId.of("Europe/Brussels")
        assertEquals(25L, Duration.between(now.atZone(zone), next!!.atZone(zone)).toHours())
    }

    @Test fun legacyRuleWithoutWeekdaysStillAppliesEveryDay() {
        val original = rule("legacy", 7, 20)
        val json = original.serialise().apply { remove("daysOfWeek") }
        val restored = ScheduleRule.parse(json)
        assertEquals(original, restored)
        assertEquals(ScheduleRule.ALL_DAYS, restored!!.daysOfWeek)
        assertTrue(restored.covers(at(6, 20, 12)))
    }

    @Test fun weekdaySelectionsSurviveJsonAndUseIsoMondayToSundayNumbers() {
        val original = rule("weekend", 7, 20, days = setOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY))
        val json = original.serialise()
        assertEquals("[6,7]", json.getJSONArray("daysOfWeek").toString())
        assertEquals(original, ScheduleRule.parse(json))
    }

    @Test fun emptyInvalidOrMalformedWeekdayImportsAreDropped() {
        val invalid = listOf("[]", "[0]", "[8]", "[1.5]", "[\"1\"]", "[null]", "null", "{}")
        invalid.forEach { days ->
            val json = rule("invalid", 7, 20).serialise()
            val replacement = org.json.JSONObject("""{"daysOfWeek":$days}""").get("daysOfWeek")
            json.put("daysOfWeek", replacement)
            assertNull("Invalid weekday data: $days", ScheduleRule.parse(json))
        }
    }

    @Test fun secondRepeatedHourSchedulesTheSecondOccurrenceOfItsFutureEdge() {
        val zone = ZoneId.of("Europe/Brussels")
        val now = ZonedDateTime.ofLocal(at(10, 25, 2, 10), zone, ZoneOffset.ofHours(1))
        val timed = rule("timed", 2, 4).copy(startMinuteOfDay = 2 * 60 + 30)
        val next = ScheduleEvaluator.nextBoundary(listOf(timed), now)
        val expected = ZonedDateTime.ofLocal(at(10, 25, 2, 30), zone, ZoneOffset.ofHours(1))
        assertEquals(expected, next)
        assertTrue(next!!.toInstant().isAfter(now.toInstant()))
    }

    @Test fun firstRepeatedHourRechecksAtTheClockRewindBeforeTheSecondStart() {
        val zone = ZoneId.of("Europe/Brussels")
        val now = ZonedDateTime.ofLocal(at(10, 25, 2, 45), zone, ZoneOffset.ofHours(2))
        val timed = rule("timed", 2, 4).copy(startMinuteOfDay = 2 * 60 + 30)
        val next = ScheduleEvaluator.nextBoundary(listOf(timed), now)
        val rewind = ZonedDateTime.ofLocal(at(10, 25, 2, 0), zone, ZoneOffset.ofHours(1))
        assertEquals(rewind, next)
        assertEquals(timed, ScheduleEvaluator.ruleInForce(listOf(timed), now.toLocalDateTime()))
        assertNull(ScheduleEvaluator.ruleInForce(listOf(timed), next!!.toLocalDateTime()))
        assertTrue(next.toInstant().isAfter(now.toInstant()))
        assertEquals(
            ZonedDateTime.ofLocal(at(10, 25, 2, 30), zone, ZoneOffset.ofHours(1)),
            ScheduleEvaluator.nextBoundary(listOf(timed), next)
        )
    }

    @Test fun skippedSpringEdgeRechecksAtTheClockJumpInsteadOfShiftingTheEdge() {
        val zone = ZoneId.of("Europe/Brussels")
        val now = at(3, 29, 1, 45).atZone(zone)
        val timed = rule("timed", 2, 4).copy(startMinuteOfDay = 2 * 60 + 30)
        val next = ScheduleEvaluator.nextBoundary(listOf(timed), now)
        assertEquals(at(3, 29, 3).atZone(zone), next)
        assertNull(ScheduleEvaluator.ruleInForce(listOf(timed), now.toLocalDateTime()))
        assertEquals(timed, ScheduleEvaluator.ruleInForce(listOf(timed), next!!.toLocalDateTime()))
        assertEquals(at(3, 29, 4).atZone(zone), ScheduleEvaluator.nextBoundary(listOf(timed), next))
    }

    @Test fun anEdgeEqualToNowNeverCreatesAnImmediateAlarmInTheSecondRepeatedHour() {
        val zone = ZoneId.of("Europe/Brussels")
        val now = ZonedDateTime.ofLocal(at(10, 25, 2, 30), zone, ZoneOffset.ofHours(1))
        val timed = rule("timed", 2, 4).copy(startMinuteOfDay = 2 * 60 + 30)
        val next = ScheduleEvaluator.nextBoundary(listOf(timed), now)
        assertEquals(at(10, 25, 4).atZone(zone), next)
        assertTrue(next!!.toInstant().isAfter(now.toInstant()))
    }

    @Test fun zoneAwareWeekdayScheduleRechecksAtTheNextLocalMidnight() {
        val zone = ZoneId.of("Europe/Brussels")
        val now = at(6, 19, 23).atZone(zone)
        val saturday = rule("saturday", 10, 10, days = setOf(DayOfWeek.SATURDAY))
        assertEquals(at(6, 20, 0).atZone(zone), ScheduleEvaluator.nextBoundary(listOf(saturday), now))
    }

    @Test fun fixedOffsetZoneKeepsTheNearestFutureEdgeWithoutTransitions() {
        val now = at(6, 15, 18).atZone(ZoneOffset.UTC)
        val night = rule("night", 20, 7)
        assertEquals(at(6, 15, 20).atZone(ZoneOffset.UTC), ScheduleEvaluator.nextBoundary(listOf(night), now))
    }

    @Test fun emptyOrDisabledZoneAwareScheduleHasNoAlarmEvenBeforeDst() {
        val now = at(3, 29, 1).atZone(ZoneId.of("Europe/Brussels"))
        assertNull(ScheduleEvaluator.nextBoundary(emptyList(), now))
        assertNull(ScheduleEvaluator.nextBoundary(listOf(rule("off", 20, 7, enabled = false)), now))
    }
}
