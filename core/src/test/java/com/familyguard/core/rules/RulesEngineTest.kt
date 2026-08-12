package com.familyguard.core.rules

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * 规则引擎测试：单 app 限时 / 分类共享额度 / 禁玩时段 / 每日总额 / 叠加取最短。
 */
class RulesEngineTest {

    private val douyin = "com.ss.android.ugc.aweme"
    private val wangzhe = "com.tencent.tmgp.sgame"

    @Test
    fun `单 app 限时剩余分钟正确`() {
        val rules = RuleSet(appLimits = listOf(AppLimit(douyin, AppCategory.SHORT_VIDEO, dailyMinutes = 30)))
        val v = RulesEngine.verdict(
            packageName = douyin, category = AppCategory.SHORT_VIDEO, rules = rules,
            todayUsedByApp = 20 * 60_000L, todayUsedByCategory = 20 * 60_000L, todayUsedTotal = 20 * 60_000L,
            now = LocalTime.of(10, 0),
        )
        assertEquals(10L, v.remainingMinutes)
        assertTrue(!v.blocked)
    }

    @Test
    fun `单 app 超时被拦截`() {
        val rules = RuleSet(appLimits = listOf(AppLimit(douyin, AppCategory.SHORT_VIDEO, dailyMinutes = 30)))
        val v = RulesEngine.verdict(
            packageName = douyin, category = AppCategory.SHORT_VIDEO, rules = rules,
            todayUsedByApp = 30 * 60_000L, todayUsedByCategory = 30 * 60_000L, todayUsedTotal = 30 * 60_000L,
            now = LocalTime.of(10, 0),
        )
        assertEquals(0L, v.remainingMinutes)
        assertTrue(v.blocked)
    }

    @Test
    fun `禁玩时段内直接拦截`() {
        val rules = RuleSet(
            appLimits = listOf(
                AppLimit(douyin, AppCategory.SHORT_VIDEO, dailyMinutes = 30, bannedRanges = listOf(TimeRange(21 * 60, 7 * 60))),
            ),
        )
        val v = RulesEngine.verdict(
            packageName = douyin, category = AppCategory.SHORT_VIDEO, rules = rules,
            todayUsedByApp = 0, todayUsedByCategory = 0, todayUsedTotal = 0,
            now = LocalTime.of(22, 0),
        )
        assertTrue(v.blocked)
        assertEquals(0L, v.remainingMinutes)
    }

    @Test
    fun `禁玩时段外不拦截`() {
        val rules = RuleSet(
            appLimits = listOf(
                AppLimit(douyin, AppCategory.SHORT_VIDEO, dailyMinutes = 30, bannedRanges = listOf(TimeRange(21 * 60, 7 * 60))),
            ),
        )
        val v = RulesEngine.verdict(
            packageName = douyin, category = AppCategory.SHORT_VIDEO, rules = rules,
            todayUsedByApp = 0, todayUsedByCategory = 0, todayUsedTotal = 0,
            now = LocalTime.of(10, 0),
        )
        assertTrue(!v.blocked)
        assertEquals(30L, v.remainingMinutes)
    }

    @Test
    fun `类别共享额度取类别剩余`() {
        val rules = RuleSet(
            appLimits = listOf(AppLimit(wangzhe, AppCategory.GAME, dailyMinutes = 0)), // app 本身不限
            categoryLimits = listOf(CategoryLimit(AppCategory.GAME, dailyMinutes = 60)),
        )
        val v = RulesEngine.verdict(
            packageName = wangzhe, category = AppCategory.GAME, rules = rules,
            todayUsedByApp = 40 * 60_000L, todayUsedByCategory = 50 * 60_000L, todayUsedTotal = 50 * 60_000L,
            now = LocalTime.of(15, 0),
        )
        // 类别剩余 10 分钟（60-50），app 自身不限 → 剩余 10
        assertEquals(10L, v.remainingMinutes)
    }

    @Test
    fun `每日总额兜底`() {
        val rules = RuleSet(
            appLimits = listOf(AppLimit(douyin, AppCategory.SHORT_VIDEO, dailyMinutes = 0)),
            dailyTotal = DailyTotalLimit(totalMinutes = 120),
        )
        val v = RulesEngine.verdict(
            packageName = douyin, category = AppCategory.SHORT_VIDEO, rules = rules,
            todayUsedByApp = 100 * 60_000L, todayUsedByCategory = 100 * 60_000L, todayUsedTotal = 110 * 60_000L,
            now = LocalTime.of(18, 0),
        )
        assertEquals(10L, v.remainingMinutes)
    }

    @Test
    fun `多规则叠加取最短`() {
        val rules = RuleSet(
            appLimits = listOf(AppLimit(douyin, AppCategory.SHORT_VIDEO, dailyMinutes = 30)),
            categoryLimits = listOf(CategoryLimit(AppCategory.SHORT_VIDEO, dailyMinutes = 60)),
            dailyTotal = DailyTotalLimit(totalMinutes = 120),
        )
        val v = RulesEngine.verdict(
            packageName = douyin, category = AppCategory.SHORT_VIDEO, rules = rules,
            todayUsedByApp = 25 * 60_000L,   // app 剩余 5
            todayUsedByCategory = 55 * 60_000L, // 类别剩余 5
            todayUsedTotal = 110 * 60_000L,  // 总额剩余 10
            now = LocalTime.of(10, 0),
        )
        assertEquals(5L, v.remainingMinutes)
    }

    @Test
    fun `无任何规则时不限制`() {
        val rules = RuleSet()
        val v = RulesEngine.verdict(
            packageName = "com.example.unknown", category = AppCategory.OTHER, rules = rules,
            todayUsedByApp = 999 * 60_000L, todayUsedByCategory = 999 * 60_000L, todayUsedTotal = 999 * 60_000L,
            now = LocalTime.of(10, 0),
        )
        assertEquals(Long.MAX_VALUE, v.remainingMinutes)
        assertTrue(!v.blocked)
    }

    @Test
    fun `总额用尽后即使 app 有额度也拦截`() {
        val rules = RuleSet(
            appLimits = listOf(AppLimit(douyin, AppCategory.SHORT_VIDEO, dailyMinutes = 60)),
            dailyTotal = DailyTotalLimit(totalMinutes = 60),
        )
        val v = RulesEngine.verdict(
            packageName = douyin, category = AppCategory.SHORT_VIDEO, rules = rules,
            todayUsedByApp = 10 * 60_000L, todayUsedByCategory = 10 * 60_000L, todayUsedTotal = 60 * 60_000L,
            now = LocalTime.of(10, 0),
        )
        assertEquals(0L, v.remainingMinutes)
        assertTrue(v.blocked)
    }

    @Test
    fun `类别用尽后同类别其他 app 也被拦截`() {
        val rules = RuleSet(categoryLimits = listOf(CategoryLimit(AppCategory.GAME, dailyMinutes = 60)))
        val v = RulesEngine.verdict(
            packageName = "com.tencent.tmgp.pubgmhd", category = AppCategory.GAME, rules = rules,
            todayUsedByApp = 0, todayUsedByCategory = 60 * 60_000L, todayUsedTotal = 60 * 60_000L,
            now = LocalTime.of(20, 0),
        )
        assertEquals(0L, v.remainingMinutes)
        assertTrue(v.blocked)
    }
}
