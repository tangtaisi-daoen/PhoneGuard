package com.familyguard.core.rules

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.TimeRange
import java.time.LocalTime

/**
 * 规则引擎（纯 Kotlin，无 Android 依赖，可 JVM 单测）。
 *
 * 判定语义：
 * - 单 app 限时：dailyMinutes = 0 表示不限制
 * - 类别共享额度：同类别所有 app 合计占用类别额度
 * - 每日总额：全局娱乐 app 合计占用
 * - 禁玩时段：命中即拦截（跨天时段支持）
 * - 叠加：剩余分钟取所有生效限制的最小值；任一规则用尽即拦截
 */
object RulesEngine {

    /** 单个 app 的判定结果。 */
    data class AppVerdict(
        val remainingMinutes: Long, // 剩余可用分钟（Long.MAX_VALUE = 不限制）
        val blocked: Boolean,       // 当前是否被拦截（禁玩时段/额度用尽）
        val reason: String?,        // 拦截原因（诊断展示用），未拦截为 null
    )

    /**
     * 判定指定 app 当前剩余可用时间。
     * @param todayUsedByApp 该 app 今日已用毫秒
     * @param todayUsedByCategory 该类别今日合计已用毫秒
     * @param todayUsedTotal 今日全部娱乐类合计已用毫秒
     * @param now 当前时刻（禁玩时段判定）
     */
    fun verdict(
        packageName: String,
        category: AppCategory,
        rules: RuleSet,
        todayUsedByApp: Long,
        todayUsedByCategory: Long,
        todayUsedTotal: Long,
        now: LocalTime,
    ): AppVerdict {
        val appLimit = rules.appLimits.firstOrNull { it.packageName == packageName }

        // 1. 禁玩时段检查
        appLimit?.bannedRanges?.let { ranges ->
            val minute = now.hour * 60 + now.minute
            if (ranges.any { it.contains(minute) }) {
                return AppVerdict(0, true, "当前处于禁玩时段")
            }
        }

        var remaining = Long.MAX_VALUE
        var reason: String? = null

        // 2. 单 app 限时
        appLimit?.let { limit ->
            if (limit.dailyMinutes > 0) {
                val left = limit.dailyMinutes * 60_000L - todayUsedByApp
                if (left <= 0) return AppVerdict(0, true, "本应用今日时长已用完")
                if (left < remaining) {
                    remaining = left
                    reason = "本应用限时"
                }
            }
        }

        // 3. 类别共享额度
        rules.categoryLimits.firstOrNull { it.category == category }?.let { cl ->
            if (cl.dailyMinutes > 0) {
                val left = cl.dailyMinutes * 60_000L - todayUsedByCategory
                if (left <= 0) return AppVerdict(0, true, "${categoryName(category)}类今日时长已用完")
                if (left < remaining) {
                    remaining = left
                    reason = "类别限时"
                }
            }
        }

        // 4. 每日总额
        if (rules.dailyTotal.totalMinutes > 0) {
            val left = rules.dailyTotal.totalMinutes * 60_000L - todayUsedTotal
            if (left <= 0) return AppVerdict(0, true, "今日娱乐总额已用完")
            if (left < remaining) {
                remaining = left
                reason = "每日总额"
            }
        }

        return AppVerdict(if (remaining == Long.MAX_VALUE) Long.MAX_VALUE else remaining / 60_000L, false, reason)
    }

    private fun categoryName(c: AppCategory): String = when (c) {
        AppCategory.GAME -> "游戏"
        AppCategory.SHORT_VIDEO -> "短视频"
        AppCategory.VIDEO -> "长视频"
        AppCategory.SOCIAL -> "社交"
        AppCategory.TOOL -> "工具"
        AppCategory.OTHER -> "未分类"
    }
}
