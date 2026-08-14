package com.familyguard.core.rules

import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.DateOverride
import com.familyguard.core.data.RuleProfile
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.RuleSetEnvelope
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.TimeRange
import com.familyguard.core.data.TemporaryAllowance
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RuleCalendarTest {

    private val weekday = RuleSet(dailyTotal = DailyTotalLimit(60), version = 1)
    private val weekend = RuleSet(dailyTotal = DailyTotalLimit(120), version = 1)
    private val holiday = RuleSet(dailyTotal = DailyTotalLimit(180), version = 1)

    @Test
    fun `weekday and weekend defaults select different profiles`() {
        val envelope = RuleSetEnvelope(weekdayProfile = weekday, weekendProfile = weekend, holidayProfile = holiday)

        assertEquals(RuleProfile.WEEKDAY, envelope.profileFor(LocalDate.of(2026, 8, 12)))
        assertEquals(60, envelope.rulesFor(LocalDate.of(2026, 8, 12)).dailyTotal.totalMinutes)
        assertEquals(RuleProfile.WEEKEND, envelope.profileFor(LocalDate.of(2026, 8, 16)))
        assertEquals(120, envelope.rulesFor(LocalDate.of(2026, 8, 16)).dailyTotal.totalMinutes)
    }

    @Test
    fun `explicit holiday overrides a weekday`() {
        val date = LocalDate.of(2026, 10, 1)
        val envelope = RuleSetEnvelope(
            weekdayProfile = weekday,
            weekendProfile = weekend,
            holidayProfile = holiday,
            dateOverrides = listOf(DateOverride(date.toString(), RuleProfile.HOLIDAY, "国庆节")),
        )

        assertEquals(RuleProfile.HOLIDAY, envelope.profileFor(date))
        assertEquals(180, envelope.rulesFor(date).dailyTotal.totalMinutes)
    }

    @Test
    fun `make up working day overrides a weekend`() {
        val date = LocalDate.of(2026, 10, 10)
        val envelope = RuleSetEnvelope(
            weekdayProfile = weekday,
            weekendProfile = weekend,
            holidayProfile = holiday,
            dateOverrides = listOf(DateOverride(date.toString(), RuleProfile.WEEKDAY, "调休上班")),
        )

        assertEquals(RuleProfile.WEEKDAY, envelope.profileFor(date))
    }

    @Test
    fun `cross midnight ban keeps the starting days profile`() {
        val fridayRules = weekday.copy(
            appLimits = listOf(AppLimit("example.app", bannedRanges = listOf(TimeRange(23 * 60, 60)))),
        )
        val envelope = RuleSetEnvelope(
            weekdayProfile = fridayRules,
            weekendProfile = weekend,
            holidayProfile = holiday,
        )

        val saturday = LocalDate.of(2026, 8, 15)
        val effective = envelope.rulesFor(saturday, LocalTime.of(0, 30))

        assertEquals(120, effective.dailyTotal.totalMinutes)
        assertEquals(listOf(TimeRange(23 * 60, 60)), effective.appLimits.single().bannedRanges)
    }

    @Test
    fun `holiday range expands to explicit inclusive dates`() {
        val dates = dateOverridesForRange(
            start = LocalDate.of(2026, 10, 1),
            endInclusive = LocalDate.of(2026, 10, 3),
            profile = RuleProfile.HOLIDAY,
            label = "国庆假期",
        )

        assertEquals(listOf("2026-10-01", "2026-10-02", "2026-10-03"), dates.map { it.localDate })
    }

    @Test
    fun `temporary allowance applies only to matching app before expiry`() {
        val envelope = RuleSetEnvelope(
            temporaryAllowances = listOf(
                TemporaryAllowance("example.app", 30, expiresAt = 2_000, reason = "临时加时"),
            ),
        )

        assertEquals(30, envelope.activeAllowanceMinutes("example.app", 1_999))
        assertEquals(0, envelope.activeAllowanceMinutes("other.app", 1_999))
        assertEquals(0, envelope.activeAllowanceMinutes("example.app", 2_000))
    }
}
