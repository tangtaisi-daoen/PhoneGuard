package com.familyguard.core.data

import com.familyguard.core.categories.AppCategory

/**
 * 时间段（分钟粒度，0..1440）。
 * 同天语义：[start, end)；跨天语义（start > end）：从 start 到次日 end。
 */
data class TimeRange(
    val startMinutes: Int,
    val endMinutes: Int,
) {
    init {
        require(startMinutes in 0..1440) { "startMinutes 必须在 0..1440" }
        require(endMinutes in 0..1440) { "endMinutes 必须在 0..1440" }
    }

    /** 判断当前分钟是否落在本时间段内（半开区间）。 */
    fun contains(minuteOfDay: Int): Boolean {
        require(minuteOfDay in 0..1440) { "minuteOfDay 必须在 0..1440" }
        return if (startMinutes == endMinutes) {
            false // 零长度时间段永不命中
        } else if (startMinutes < endMinutes) {
            minuteOfDay >= startMinutes && minuteOfDay < endMinutes
        } else {
            // 跨天：minuteOfDay >= start 或 minuteOfDay < end
            minuteOfDay >= startMinutes || minuteOfDay < endMinutes
        }
    }
}

/** 单个 app 的限时规则。dailyMinutes = 0 表示不限制。 */
data class AppLimit(
    val packageName: String,
    val category: AppCategory = AppCategory.OTHER,
    val dailyMinutes: Int = 0,
    val bannedRanges: List<TimeRange> = emptyList(),
)

/** 类别共享额度。dailyMinutes = 0 表示不限制。 */
data class CategoryLimit(
    val category: AppCategory,
    val dailyMinutes: Int = 0,
)

/** 每日娱乐总额兜底。totalMinutes = 0 表示不限制。 */
data class DailyTotalLimit(
    val totalMinutes: Int = 0,
)

/** 一套完整规则（由管理端下发，被控端缓存执行）。 */
data class RuleSet(
    val appLimits: List<AppLimit> = emptyList(),
    val categoryLimits: List<CategoryLimit> = emptyList(),
    val dailyTotal: DailyTotalLimit = DailyTotalLimit(),
    val version: Long = 0, // 云端规则版本号，用于增量同步
)

/** 单 app 单日使用快照（分钟粒度，用于上报与判定）。 */
data class UsageSnapshot(
    val packageName: String,
    val usedMinutes: Int,
    val date: String, // yyyy-MM-dd
)

/** 异常事件（被控端上报，管理端轮询展示）。 */
data class AnomalyEvent(
    val type: String, // UNINSTALL_ATTEMPT / PERMISSION_DISABLED / OFFLINE / TIME_CHANGED / NEW_APP
    val message: String,
    val occurredAt: Long, // epoch millis
)

/** 双端绑定关系。 */
data class Binding(
    val inviteCode: String,
    val adminUserId: String,
    val kidDeviceId: String,
    val boundAt: Long,
)
