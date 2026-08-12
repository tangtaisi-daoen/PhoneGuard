package com.familyguard.core.rules

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.TimeRange
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * RuleSet 的 JSON 编解码。
 * 说明：Gson 对 Kotlin data class 的反射反序列化不可靠（val 字段），读取采用手动解析。
 */
object RuleCodec {

    private val gson = Gson()

    /** 序列化（写入缓存/云端）。 */
    fun toJson(rules: RuleSet): String = gson.toJson(rules)

    /** 反序列化；解析失败返回 null。 */
    fun fromJson(json: String): RuleSet? = runCatching {
        parse(gson.fromJson(json, JsonObject::class.java))
    }.getOrNull()

    /** 从 JsonObject 手动解析 RuleSet。 */
    fun parse(json: JsonObject): RuleSet {
        val appLimits = json.getAsJsonArray("appLimits")?.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                AppLimit(
                    packageName = o.get("packageName")?.asString ?: "",
                    category = runCatching {
                        AppCategory.valueOf(o.get("category")?.asString ?: "OTHER")
                    }.getOrDefault(AppCategory.OTHER),
                    dailyMinutes = o.get("dailyMinutes")?.asInt ?: 0,
                    bannedRanges = o.getAsJsonArray("bannedRanges")?.mapNotNull { r ->
                        val ro = r.asJsonObject
                        TimeRange(
                            ro.get("startMinutes")?.asInt ?: 0,
                            ro.get("endMinutes")?.asInt ?: 0,
                        )
                    } ?: emptyList(),
                )
            }.getOrNull()
        } ?: emptyList()

        val categoryLimits = json.getAsJsonArray("categoryLimits")?.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                CategoryLimit(
                    category = runCatching {
                        AppCategory.valueOf(o.get("category")?.asString ?: "OTHER")
                    }.getOrDefault(AppCategory.OTHER),
                    dailyMinutes = o.get("dailyMinutes")?.asInt ?: 0,
                )
            }.getOrNull()
        } ?: emptyList()

        val dailyTotal = runCatching {
            val o = json.getAsJsonObject("dailyTotal")
            DailyTotalLimit(totalMinutes = o?.get("totalMinutes")?.asInt ?: 0)
        }.getOrDefault(DailyTotalLimit())

        return RuleSet(
            appLimits = appLimits,
            categoryLimits = categoryLimits,
            dailyTotal = dailyTotal,
            version = json.get("version")?.asLong ?: 0,
        )
    }
}
