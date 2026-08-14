package com.familyguard.core.data

import com.familyguard.core.categories.AppCategory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

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

enum class RuleProfile { WEEKDAY, WEEKEND, HOLIDAY }

data class DateOverride(
    val localDate: String,
    val profile: RuleProfile,
    val label: String = "",
)

data class TemporaryAllowance(
    val packageName: String,
    val extraMinutes: Int,
    val expiresAt: Long,
    val reason: String = "",
    val createdAt: Long = 0L,
)

/** 三套日历规则。明确日期覆盖的优先级高于星期默认值。 */
data class RuleSetEnvelope(
    val schemaVersion: Int = 1,
    val revision: Long = 0,
    val timezoneId: String = "Asia/Shanghai",
    val weekdayProfile: RuleSet = RuleSet(),
    val weekendProfile: RuleSet = RuleSet(),
    val holidayProfile: RuleSet = RuleSet(),
    val dateOverrides: List<DateOverride> = emptyList(),
    val temporaryAllowances: List<TemporaryAllowance> = emptyList(),
    val effectiveAt: Long = 0,
    val generatedAt: Long = 0,
) {
    fun profileFor(date: LocalDate): RuleProfile {
        val override = dateOverrides.lastOrNull { it.localDate == date.toString() }
        if (override != null) return override.profile
        return if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            RuleProfile.WEEKEND
        } else {
            RuleProfile.WEEKDAY
        }
    }

    fun rulesFor(date: LocalDate): RuleSet = when (profileFor(date)) {
        RuleProfile.WEEKDAY -> weekdayProfile
        RuleProfile.WEEKEND -> weekendProfile
        RuleProfile.HOLIDAY -> holidayProfile
    }

    fun activeAllowanceMinutes(packageName: String, nowMs: Long): Int = temporaryAllowances
        .asSequence()
        .filter { it.packageName == packageName && it.extraMinutes > 0 && nowMs < it.expiresAt }
        .sumOf(TemporaryAllowance::extraMinutes)

    /** 零点后的跨日禁用时段继承开始日的规则，其余额度仍按当天 profile。 */
    fun rulesFor(date: LocalDate, time: LocalTime): RuleSet {
        val today = rulesFor(date)
        val minute = time.hour * 60 + time.minute
        val previous = rulesFor(date.minusDays(1))
        val carryOver = previous.appLimits.mapNotNull { oldLimit ->
            val ranges = oldLimit.bannedRanges.filter { range ->
                range.startMinutes > range.endMinutes && minute < range.endMinutes
            }
            if (ranges.isEmpty()) null else oldLimit.copy(bannedRanges = ranges)
        }
        if (carryOver.isEmpty()) return today
        val byPackage = today.appLimits.associateBy { it.packageName }.toMutableMap()
        carryOver.forEach { carried ->
            val current = byPackage[carried.packageName]
            byPackage[carried.packageName] = if (current == null) carried.copy(dailyMinutes = 0) else {
                current.copy(bannedRanges = (current.bannedRanges + carried.bannedRanges).distinct())
            }
        }
        return today.copy(appLimits = byPackage.values.toList())
    }
}

/** 单 app 单日使用快照（分钟粒度，用于上报与判定）。 */
data class UsageSnapshot(
    val packageName: String,
    val usedMinutes: Int,
    val date: String, // yyyy-MM-dd
)

/** 异常事件（被控端上报，管理端轮询展示）。 */
enum class IncidentStatus { OPEN, ACKNOWLEDGED, RESOLVED }

data class AnomalyEvent(
    val type: String, // UNINSTALL_ATTEMPT / PERMISSION_DISABLED / OFFLINE / TIME_CHANGED / NEW_APP
    val message: String,
    val occurredAt: Long, // epoch millis
    val id: String = "",
    val dedupKey: String = type,
    val status: IncidentStatus = IncidentStatus.OPEN,
    val firstSeenAt: Long = occurredAt,
    val lastSeenAt: Long = occurredAt,
    val acknowledgedAt: Long = 0L,
    val resolvedAt: Long = 0L,
    val occurrenceCount: Int = 1,
    val read: Boolean = false,
)

/** 双端绑定关系。 */
data class Binding(
    val inviteCode: String,
    val adminUserId: String,
    val kidDeviceId: String,
    val boundAt: Long,
)
