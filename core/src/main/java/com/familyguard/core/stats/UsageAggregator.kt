package com.familyguard.core.stats

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.categories.CategoryRegistry

/** 单个 app 的前台使用记录（UsageStatsManager 原始数据）。 */
data class UsageEntry(
    val packageName: String,
    val foregroundMillis: Long,
)

/** 前台事件（用于判定当前正在使用的 app）。 */
data class ForegroundEvent(
    val timestampMillis: Long,
    val packageName: String,
    val movedToForeground: Boolean,
)

/**
 * 使用统计聚合（纯 Kotlin，可 JVM 单测）。
 */
object UsageAggregator {

    /**
     * 把原始使用记录聚合为 包名 → 当日分钟数。
     * 分钟向上取整（不足 1 分钟按 1 分钟计），0 过滤。
     */
    fun aggregateMinutes(entries: List<UsageEntry>): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        for (e in entries) {
            if (e.foregroundMillis <= 0) continue
            val minutes = (e.foregroundMillis + 59_999L) / 60_000L
            result[e.packageName] = (result[e.packageName] ?: 0L) + minutes
        }
        return result
    }

    /** 按类别汇总分钟数（未内置分类的归 OTHER）。 */
    fun categoryMinutes(byPackage: Map<String, Long>): Map<AppCategory, Long> {
        val result = mutableMapOf<AppCategory, Long>()
        for ((pkg, minutes) in byPackage) {
            val cat = CategoryRegistry.classify(pkg)
            result[cat] = (result[cat] ?: 0L) + minutes
        }
        return result
    }

    /**
     * 从前台事件流中判定当前正在使用的 app。
     * 取时间最新的前台事件；若最新事件是离开前台，则当前无前台 app（返回 null）。
     */
    fun currentForegroundApp(events: List<ForegroundEvent>): String? {
        val sorted = events.sortedBy { it.timestampMillis }
        val last = sorted.lastOrNull() ?: return null
        return if (last.movedToForeground) last.packageName else null
    }

    /** 判断心跳是否在线：最近心跳时间距今未超过阈值。 */
    fun isOnline(lastHeartbeatMillis: Long, nowMillis: Long, thresholdMillis: Long = 5 * 60_000L): Boolean =
        nowMillis - lastHeartbeatMillis <= thresholdMillis
}
