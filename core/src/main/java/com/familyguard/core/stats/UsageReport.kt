package com.familyguard.core.stats

import com.familyguard.core.backend.HeartbeatSnapshot
import com.familyguard.core.categories.AppCategory

/** 管理端报告页使用的纯数据摘要。 */
data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val minutes: Long,
)

data class CategoryUsageSummary(
    val category: AppCategory,
    val minutes: Long,
    val percentage: Int,
)

data class DailyUsageSummary(
    val date: String,
    val totalMinutes: Long,
)

data class UsageReportSummary(
    val totalMinutes: Long,
    val appCount: Int,
    val topApps: List<AppUsageSummary>,
    val categories: List<CategoryUsageSummary>,
    val dailyTrend: List<DailyUsageSummary>,
)

object UsageReportBuilder {
    /** 只选择指定日期的最新快照，避免把昨天的数据当成今天的数据。 */
    fun latestSnapshotForDate(
        snapshots: Iterable<HeartbeatSnapshot>,
        date: String,
    ): HeartbeatSnapshot? = snapshots
        .asSequence()
        .filter { it.date == date }
        .maxByOrNull { it.reportedAt }

    fun build(
        byPackage: Map<String, Long>,
        appNames: Map<String, String>,
        dailySnapshots: List<Pair<String, Long>> = emptyList(),
        topLimit: Int = 8,
    ): UsageReportSummary {
        val positive = byPackage
            .filterKeys { it in appNames }
            .filterValues { it > 0 }
        val total = positive.values.sum()
        val categoryMinutes = UsageAggregator.categoryMinutes(positive)
        val categories = categoryMinutes.entries
            .sortedByDescending { it.value }
            .map { (category, minutes) ->
                CategoryUsageSummary(
                    category = category,
                    minutes = minutes,
                    percentage = if (total == 0L) 0 else ((minutes * 100) / total).toInt(),
                )
            }
        return UsageReportSummary(
            totalMinutes = total,
            appCount = positive.size,
            topApps = positive.entries
                .sortedByDescending { it.value }
                .take(topLimit.coerceAtLeast(1))
                .map { AppUsageSummary(it.key, appNames.getValue(it.key), it.value) },
            categories = categories,
            dailyTrend = dailySnapshots
                .filter { it.second >= 0 }
                .groupBy { it.first }
                .map { (date, values) -> date to (values.maxOfOrNull { it.second } ?: 0L) }
                .sortedBy { it.first }
                .takeLast(7)
                .map { DailyUsageSummary(it.first, it.second) },
        )
    }

    /** 只汇总已确认是可见普通应用的使用时间。 */
    fun visibleTotalMinutes(
        byPackage: Map<String, Long>,
        visiblePackages: Set<String>,
    ): Long = byPackage
        .asSequence()
        .filter { (packageName, minutes) -> packageName in visiblePackages && minutes > 0 }
        .sumOf { it.value }
}
