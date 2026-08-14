package com.familyguard.kid.stats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import com.familyguard.core.stats.ForegroundEvent
import com.familyguard.core.stats.ForegroundUsageCalculator
import com.familyguard.core.stats.ForegroundUsageEvent
import com.familyguard.core.stats.InstalledAppFilter
import com.familyguard.core.stats.InstalledAppInfo
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

    /** 返回报告和规则引擎共同使用的普通应用白名单及名称。 */
    fun visibleInstalledApps(context: Context): List<Pair<String, String>> {
        val packageManager = context.packageManager
        return InstalledAppFilter.displayable(
            packageManager.getInstalledApplications(0).mapNotNull { app ->
                runCatching {
                    InstalledAppInfo(
                        packageName = app.packageName,
                        label = packageManager.getApplicationLabel(app).toString(),
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        hasLauncherEntry = packageManager.getLaunchIntentForPackage(app.packageName) != null,
                    )
                }.getOrNull()
            },
        )
    }

    /** 采集当日各 app 使用记录（排除自身与系统）。 */
    fun collectToday(context: Context, visiblePackages: Set<String>): List<UsageEntry> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val start = todayStartMillis()
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(start - DAY_MILLIS, end) ?: return emptyList()
        val raw = mutableListOf<ForegroundUsageEvent>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName in excludedPackages) continue
            if (event.packageName !in visiblePackages) continue
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND
            ) {
                raw += ForegroundUsageEvent(
                    event.timeStamp,
                    event.packageName,
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND,
                )
            }
        }
        return ForegroundUsageCalculator.minutesByPackage(raw, start, end)
            .map { (packageName, minutes) -> UsageEntry(packageName, minutes * 60_000L) }
    }

    /** 当日各 app 分钟数（聚合后）。 */
    fun collectTodayMinutes(context: Context, visiblePackages: Set<String>): Map<String, Long> =
        UsageAggregator.aggregateMinutes(collectToday(context, visiblePackages))

    /** 当前前台 app（可能为 null）。 */
    fun currentForegroundApp(context: Context, visiblePackages: Set<String>? = null): String? {
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
                if (e.packageName !in excludedPackages &&
                    (visiblePackages == null || e.packageName in visiblePackages)
                ) {
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

    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
}
