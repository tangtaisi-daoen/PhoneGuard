package com.familyguard.core.rules

import com.familyguard.core.data.DateOverride
import com.familyguard.core.data.RuleProfile
import java.time.LocalDate

fun dateOverridesForRange(
    start: LocalDate,
    endInclusive: LocalDate,
    profile: RuleProfile,
    label: String,
): List<DateOverride> {
    require(!endInclusive.isBefore(start)) { "结束日期不能早于开始日期" }
    return generateSequence(start) { current ->
        current.plusDays(1).takeUnless { it.isAfter(endInclusive) }
    }.map { DateOverride(it.toString(), profile, label) }.toList()
}
