package com.familyguard.core.backend

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.TimeRange
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 规则云端同步（rules 集合）。
 *
 * 文档结构：
 *   adminUid: String（管理端用户 id，唯一）
 *   ruleSet: {appLimits:[...], categoryLimits:[...], dailyTotal:{...}, version:N}（Gson 序列化）
 *   updatedAt: Long
 */
object CloudBaseRules {

    private const val COLLECTION = "rules"
    private val gson = Gson()

    /** 保存/覆盖管理端规则（按 adminUid upsert）。 */
    suspend fun saveRules(client: CloudBaseClient, adminUid: String, ruleSet: RuleSet): Boolean {
        val ruleJson = gson.toJsonTree(ruleSet).asJsonObject
        val existing = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("adminUid" to adminUid), limit = 1,
        ) ?: return false
        val data = mapOf("ruleSet" to ruleJson, "updatedAt" to System.currentTimeMillis())
        return if (existing.isEmpty()) {
            val doc = mutableMapOf<String, Any?>("adminUid" to adminUid)
            doc.putAll(data)
            val inserted = CloudBaseDb.insertDocuments(client, COLLECTION, listOf(doc))
            inserted != null && inserted.isNotEmpty()
        } else {
            val docId = existing.first()["_id"]?.toString() ?: return false
            val updated = CloudBaseDb.updateDocuments(
                client, COLLECTION,
                where = mapOf("_id" to docId),
                data = data,
            )
            updated != null && updated > 0
        }
    }

    /** 拉取管理端的规则；不存在返回空 RuleSet。 */
    suspend fun fetchRules(client: CloudBaseClient, adminUid: String): RuleSet? {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("adminUid" to adminUid), limit = 1,
        ) ?: return null
        val doc = docs.firstOrNull() ?: return RuleSet()
        val ruleJson = doc["ruleSet"] as? JsonObject ?: return RuleSet()
        return parseRuleSet(ruleJson)
    }

    /** 手动解析 RuleSet（Gson 对 Kotlin data class 的反射反序列化不可靠）。 */
    private fun parseRuleSet(json: JsonObject): RuleSet {
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
