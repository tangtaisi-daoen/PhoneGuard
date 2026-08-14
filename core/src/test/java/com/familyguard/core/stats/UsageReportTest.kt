package com.familyguard.core.stats

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.backend.HeartbeatSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageReportTest {
    @Test
    fun `report sorts top apps and categories with percentages`() {
        val report = UsageReportBuilder.build(
            byPackage = mapOf(
                "com.tencent.mm" to 10L,
                "com.tencent.tmgp.sgame" to 30L,
                "com.example.other" to 5L,
            ),
            appNames = mapOf(
                "com.tencent.mm" to "WeChat",
                "com.tencent.tmgp.sgame" to "Game",
                "com.example.other" to "Other",
            ),
        )

        assertEquals(45L, report.totalMinutes)
        assertEquals("com.tencent.tmgp.sgame", report.topApps.first().packageName)
        assertEquals(AppCategory.GAME, report.categories.first().category)
        assertEquals(66, report.categories.first().percentage)
    }

    @Test
    fun `report keeps only latest seven trend days`() {
        val snapshots = (1..9).map { "2026-08-${it.toString().padStart(2, '0')}" to it.toLong() }

        val report = UsageReportBuilder.build(emptyMap(), emptyMap(), snapshots)

        assertEquals(7, report.dailyTrend.size)
        assertEquals("2026-08-03", report.dailyTrend.first().date)
        assertEquals("2026-08-09", report.dailyTrend.last().date)
    }

    @Test
    fun `latest snapshot is selected only from requested date`() {
        val snapshots = listOf(
            HeartbeatSnapshot("kid", "2026-08-12", mapOf("old" to 999), 999, null, 300),
            HeartbeatSnapshot("kid", "2026-08-13", mapOf("early" to 10), 10, null, 100),
            HeartbeatSnapshot("kid", "2026-08-13", mapOf("latest" to 20), 20, null, 200),
        )

        assertEquals("latest", UsageReportBuilder.latestSnapshotForDate(snapshots, "2026-08-13")!!.byPackage.keys.single())
        assertNull(UsageReportBuilder.latestSnapshotForDate(snapshots, "2026-08-14"))
    }

    @Test
    fun `report collapses duplicate trend dates`() {
        val report = UsageReportBuilder.build(
            emptyMap(),
            emptyMap(),
            listOf("2026-08-13" to 10L, "2026-08-13" to 20L),
        )

        assertEquals(1, report.dailyTrend.size)
        assertEquals(20L, report.dailyTrend.single().totalMinutes)
    }

    @Test
    fun `report excludes unknown packages and exposes application labels`() {
        val report = UsageReportBuilder.build(
            byPackage = mapOf(
                "com.tencent.mm" to 12L,
                "com.coloros.assistantscreen" to 40L,
            ),
            appNames = mapOf("com.tencent.mm" to "WeChat"),
        )

        assertEquals(12L, report.totalMinutes)
        assertEquals(1, report.appCount)
        assertEquals("WeChat", report.topApps.single().appName)
        assertEquals("com.tencent.mm", report.topApps.single().packageName)
    }

    @Test
    fun `visible total ignores packages missing from installed app list`() {
        val total = UsageReportBuilder.visibleTotalMinutes(
            byPackage = mapOf("normal" to 8L, "internal" to 99L),
            visiblePackages = setOf("normal"),
        )

        assertEquals(8L, total)
    }
}
