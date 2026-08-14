package com.familyguard.core.rules

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.TimeRange
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryAllowanceRulesEngineTest {
    @Test
    fun `temporary allowance extends app category and total quotas`() {
        val rules = RuleSet(
            appLimits = listOf(AppLimit("example.app", AppCategory.GAME, 30)),
            categoryLimits = listOf(CategoryLimit(AppCategory.GAME, 30)),
            dailyTotal = DailyTotalLimit(30),
        )

        val verdict = RulesEngine.verdict(
            packageName = "example.app",
            category = AppCategory.GAME,
            rules = rules,
            todayUsedByApp = 35 * 60_000L,
            todayUsedByCategory = 35 * 60_000L,
            todayUsedTotal = 35 * 60_000L,
            now = LocalTime.NOON,
            extraAllowanceMillis = 10 * 60_000L,
        )

        assertFalse(verdict.blocked)
    }

    @Test
    fun `temporary allowance never bypasses banned time range`() {
        val rules = RuleSet(
            appLimits = listOf(
                AppLimit("example.app", AppCategory.GAME, 30, listOf(TimeRange(12 * 60, 13 * 60))),
            ),
        )

        val verdict = RulesEngine.verdict(
            "example.app",
            AppCategory.GAME,
            rules,
            todayUsedByApp = 0,
            todayUsedByCategory = 0,
            todayUsedTotal = 0,
            now = LocalTime.NOON,
            extraAllowanceMillis = 60 * 60_000L,
        )

        assertTrue(verdict.blocked)
    }
}
