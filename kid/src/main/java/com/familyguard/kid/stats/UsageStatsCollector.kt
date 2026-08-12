package com.familyguard.kid.stats

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import com.familyguard.core.stats.ForegroundEvent
import com.familyguard.core.stats.UsageAggregator
import com.familyguard.core.stats.UsageEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 使用情况采集（UsageStatsManager）。
 * 需要"使用情况访问"特殊权限（引导页开启），Android 11+ 还需 QUERY_ALL_PACKAGES。
 */
object UsageStatsCollector {

    /** 自身及系统 UI 类，不参与统计。 */
    private val excludedPackages = setOf(
        "com.familyguard.kid",
        "com.familyguard.admin",
        "com.android.systemui",
        "com.android.settings",
        "com.android.launcher",
        "com.bbk.launcher2",
        "com.vivo.launcher",
    )

    /** 今日 0 点毫秒。 */
    fun todayStartMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** 当日日期字符串 yyyy-MM-dd。 */
    fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** 采集当日各 app 使用记录（排除自身与系统）。 */
    fun collectToday(context: Context): List<UsageEntry> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val start = todayStartMillis()
        val stats: List<UsageStats> = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, start, System.currentTimeMillis(),
        ) ?: return emptyList()
        return stats
            .filter { it.packageName !in excludedPackages }
            .map { UsageEntry(it.packageName, it.totalTimeInForeground) }
    }

    /** 当日各 app 分钟数（聚合后）。 */
    fun collectTodayMinutes(context: Context): Map<String, Long> =
        UsageAggregator.aggregateMinutes(collectToday(context))

    /** 当前前台 app（可能为 null）。 */
    fun currentForegroundApp(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val events = usm.queryEvents(todayStartMillis(), System.currentTimeMillis())
            ?: return null
        val list = mutableListOf<ForegroundEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            ) {
                if (e.packageName !in excludedPackages) {
                    list.add(
                        ForegroundEvent(
                            e.timeStamp,
                            e.packageName,
                            e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND,
                        ),
                    )
                }
            }
        }
        return UsageAggregator.currentForegroundApp(list)
    }
}
