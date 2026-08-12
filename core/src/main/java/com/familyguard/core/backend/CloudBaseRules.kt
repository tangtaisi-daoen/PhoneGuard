package com.familyguard.core.backend

import com.familyguard.core.data.RuleSet
import com.familyguard.core.rules.RuleCodec
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
        return RuleCodec.parse(ruleJson)
    }
}
