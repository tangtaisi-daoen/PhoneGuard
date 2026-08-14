package com.familyguard.core.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundUsageCalculatorTest {
    @Test
    fun `clips intervals at midnight instead of carrying yesterday into today`() {
        val day = 24 * 60 * 60 * 1000L
        val events = listOf(
            ForegroundUsageEvent(-10 * 60_000L, "old", true),
            ForegroundUsageEvent(5 * 60_000L, "old", false),
            ForegroundUsageEvent(10 * 60_000L, "today", true),
            ForegroundUsageEvent(40 * 60_000L, "today", false),
        )

        assertEquals(mapOf("old" to 5L, "today" to 30L), ForegroundUsageCalculator.minutesByPackage(events, 0, day))
    }

    @Test
    fun `closes active app at report end`() {
        val result = ForegroundUsageCalculator.minutesByPackage(
            listOf(ForegroundUsageEvent(10_000L, "app", true)),
            0L,
            70_000L,
        )

        assertEquals(1L, result["app"])
    }
}
