package com.familyguard.core.stats

import com.familyguard.core.categories.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAggregatorTest {

    @Test
    fun `分钟向上取整且过滤零值`() {
        val entries = listOf(
            UsageEntry("com.tencent.mm", 30_000L),       // 30s → 1 分钟
            UsageEntry("com.ss.android.ugc.aweme", 90_000L), // 90s → 2 分钟
            UsageEntry("com.example.zero", 0L),           // 过滤
        )
        val result = UsageAggregator.aggregateMinutes(entries)
        assertEquals(1L, result["com.tencent.mm"])
        assertEquals(2L, result["com.ss.android.ugc.aweme"])
        assertNull(result["com.example.zero"])
    }

    @Test
    fun `同一包名多次出现累加`() {
        val entries = listOf(
            UsageEntry("com.tencent.mm", 60_000L),
            UsageEntry("com.tencent.mm", 30_000L),
        )
        val result = UsageAggregator.aggregateMinutes(entries)
        assertEquals(2L, result["com.tencent.mm"])
    }

    @Test
    fun `类别汇总正确归类`() {
        val byPackage = mapOf(
            "com.tencent.tmgp.sgame" to 30L,      // GAME
            "com.ss.android.ugc.aweme" to 20L,    // SHORT_VIDEO
            "com.tencent.mm" to 10L,              // SOCIAL
            "com.example.unknown" to 5L,          // OTHER
        )
        val result = UsageAggregator.categoryMinutes(byPackage)
        assertEquals(30L, result[AppCategory.GAME])
        assertEquals(20L, result[AppCategory.SHORT_VIDEO])
        assertEquals(10L, result[AppCategory.SOCIAL])
        assertEquals(5L, result[AppCategory.OTHER])
    }

    @Test
    fun `当前前台 app 取最新前台事件`() {
        val events = listOf(
            ForegroundEvent(1000, "com.tencent.mm", true),
            ForegroundEvent(2000, "com.ss.android.ugc.aweme", true),
            ForegroundEvent(3000, "com.tencent.mm", false),
        )
        assertNull(UsageAggregator.currentForegroundApp(events))
    }

    @Test
    fun `当前前台 app 最新事件为进入前台时返回该 app`() {
        val events = listOf(
            ForegroundEvent(1000, "com.tencent.mm", true),
            ForegroundEvent(2000, "com.ss.android.ugc.aweme", true),
        )
        assertEquals("com.ss.android.ugc.aweme", UsageAggregator.currentForegroundApp(events))
    }

    @Test
    fun `空事件流返回 null`() {
        assertNull(UsageAggregator.currentForegroundApp(emptyList()))
    }

    @Test
    fun `在线判定基于心跳时间阈值`() {
        val now = 10_000_000L
        assertTrue(UsageAggregator.isOnline(now - 60_000L, now))
        assertTrue(UsageAggregator.isOnline(now - 5 * 60_000L, now))
        assertFalse(UsageAggregator.isOnline(now - 6 * 60_000L, now))
    }
}
