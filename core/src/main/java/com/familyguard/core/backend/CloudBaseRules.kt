package com.familyguard.core.backend

import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.RuleSetEnvelope
import com.familyguard.core.rules.RuleEnvelopeCodec
import com.familyguard.core.rules.RuleCodec
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 规则云端同步（rules 集合）。
 *
 * 文档结构：
 *   adminUid: String（管理端用户 id，唯一）
 *   kidDeviceId: String（绑定的被控端匿名 uid，供被控端读取）
 *   ruleSet: {appLimits:[...], categoryLimits:[...], dailyTotal:{...}, version:N}（Gson 序列化）
 *   updatedAt: Long
 */
object CloudBaseRules {

    private const val COLLECTION = "rules"
    private val gson = Gson()

    /** 保存/覆盖管理端规则（按 adminUid upsert）。 */
    suspend fun saveRules(
        client: CloudBaseClient,
        adminUid: String,
        ruleSet: RuleSet,
        kidDeviceId: String? = null,
    ): Boolean {
        val ruleJson = gson.toJsonTree(ruleSet).asJsonObject
        val existing = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("adminUid" to adminUid), limit = 1,
        ) ?: return false
        val data = mutableMapOf<String, Any?>(
            "ruleSet" to ruleJson,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (!kidDeviceId.isNullOrBlank()) data["kidDeviceId"] = kidDeviceId
        return if (existing.isEmpty()) {
            val doc = mutableMapOf<String, Any?>("adminUid" to adminUid)
            doc.putAll(data)
            val inserted = CloudBaseDb.insertDocuments(client, COLLECTION, listOf(doc))
            inserted != null && inserted.isNotEmpty()
        } else {
            val docId = existing.first()["_id"]?.toString() ?: return false
            val updated = CloudBaseDb.updateDocuments(
                client, COLLECTION,
                where = mapOf("_id" to docId, "adminUid" to adminUid),
                data = data,
            )
            updated != null && updated > 0
        }
    }

    suspend fun saveEnvelope(
        client: CloudBaseClient,
        adminUid: String,
        envelope: RuleSetEnvelope,
        kidDeviceId: String? = null,
    ): Boolean {
        val envelopeJson = gson.toJsonTree(envelope).asJsonObject
        val existing = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("adminUid" to adminUid), limit = 1,
        ) ?: return false
        val data = mutableMapOf<String, Any?>(
            "ruleEnvelope" to envelopeJson,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (!kidDeviceId.isNullOrBlank()) data["kidDeviceId"] = kidDeviceId
        return if (existing.isEmpty()) {
            val doc = mutableMapOf<String, Any?>("adminUid" to adminUid)
            doc.putAll(data)
            !CloudBaseDb.insertDocuments(client, COLLECTION, listOf(doc)).isNullOrEmpty()
        } else {
            val docId = existing.first()["_id"]?.toString() ?: return false
            val updated = CloudBaseDb.updateDocuments(
                client,
                COLLECTION,
                where = mapOf("_id" to docId, "adminUid" to adminUid),
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

    suspend fun fetchEnvelope(client: CloudBaseClient, adminUid: String): RuleSetEnvelope? {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("adminUid" to adminUid), limit = 1,
        ) ?: return null
        val doc = docs.firstOrNull() ?: return RuleSetEnvelope()
        val envelopeJson = doc["ruleEnvelope"] as? JsonObject
        if (envelopeJson != null) return RuleEnvelopeCodec.parseCompatible(envelopeJson)
        val legacyJson = doc["ruleSet"] as? JsonObject ?: return RuleSetEnvelope()
        return RuleEnvelopeCodec.parseCompatible(legacyJson)
    }

    /** 拉取被控端可见的规则信封（按 kidDeviceId 授权）。 */
    suspend fun fetchEnvelopeForKid(client: CloudBaseClient, kidDeviceId: String): RuleSetEnvelope? {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("kidDeviceId" to kidDeviceId), limit = 1,
        ) ?: return null
        val doc = docs.firstOrNull() ?: return RuleSetEnvelope()
        val envelopeJson = doc["ruleEnvelope"] as? JsonObject
        if (envelopeJson != null) return RuleEnvelopeCodec.parseCompatible(envelopeJson)
        val legacyJson = doc["ruleSet"] as? JsonObject ?: return RuleSetEnvelope()
        return RuleEnvelopeCodec.parseCompatible(legacyJson)
    }

    /** 管理端为旧规则文档补写被控端归属，允许被控端按 kidDeviceId 拉取。 */
    suspend fun ensureKidDeviceId(
        client: CloudBaseClient,
        adminUid: String,
        kidDeviceId: String,
    ): Boolean {
        if (adminUid.isBlank() || kidDeviceId.isBlank()) return false
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("adminUid" to adminUid), limit = 1,
        ) ?: return false
        val doc = docs.firstOrNull() ?: return true
        if (!doc["kidDeviceId"]?.toString().isNullOrBlank()) return true
        val id = doc["_id"]?.toString() ?: return false
        val updated = CloudBaseDb.updateDocuments(
            client,
            COLLECTION,
            where = mapOf("_id" to id, "adminUid" to adminUid),
            data = mapOf("kidDeviceId" to kidDeviceId),
        )
        return updated != null && updated > 0
    }
}
