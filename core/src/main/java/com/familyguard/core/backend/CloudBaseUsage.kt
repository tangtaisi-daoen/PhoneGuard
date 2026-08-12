package com.familyguard.core.backend

import com.google.gson.JsonObject

/**
 * 使用量心跳上报（usage 集合）。
 *
 * 文档结构（kidDeviceId + date 唯一，覆盖更新）：
 *   kidDeviceId: String（被控端匿名 uid）
 *   date: String（yyyy-MM-dd）
 *   byPackage: {pkg: minutes}（当日各 app 分钟数）
 *   totalMinutes: Long（当日总额）
 *   currentApp: String（当前前台 app，可能为空）
 *   reportedAt: Long（心跳时间）
 */
object CloudBaseUsage {

    private const val COLLECTION = "usage"

    /** 上报/覆盖当日心跳快照。 */
    suspend fun upsertHeartbeat(
        client: CloudBaseClient,
        kidDeviceId: String,
        date: String,
        byPackage: Map<String, Long>,
        totalMinutes: Long,
        currentApp: String?,
    ): Boolean {
        val existing = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId, "date" to date), limit = 1,
        ) ?: return false
        val data = mapOf(
            "byPackage" to byPackage,
            "totalMinutes" to totalMinutes,
            "currentApp" to (currentApp ?: ""),
            "reportedAt" to System.currentTimeMillis(),
        )
        return if (existing.isEmpty()) {
            val doc = mutableMapOf<String, Any?>("kidDeviceId" to kidDeviceId, "date" to date)
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

    /** 拉取被控端最近一条心跳快照。 */
    suspend fun fetchLatest(client: CloudBaseClient, kidDeviceId: String): HeartbeatSnapshot? {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("kidDeviceId" to kidDeviceId), limit = 1,
        ) ?: return null
        val doc = docs.firstOrNull() ?: return null
        val byPackage = (doc["byPackage"] as? JsonObject)?.let { obj ->
            obj.entrySet().associate { (k, v) -> k to (v.asLong) }
        } ?: emptyMap()
        return HeartbeatSnapshot(
            kidDeviceId = doc["kidDeviceId"]?.toString() ?: "",
            date = doc["date"]?.toString() ?: "",
            byPackage = byPackage,
            totalMinutes = doc["totalMinutes"]?.toString()?.toLongOrNull() ?: 0L,
            currentApp = doc["currentApp"]?.toString()?.takeIf { it.isNotBlank() },
            reportedAt = doc["reportedAt"]?.toString()?.toLongOrNull() ?: 0L,
        )
    }
}

/** 心跳快照。 */
data class HeartbeatSnapshot(
    val kidDeviceId: String,
    val date: String,
    val byPackage: Map<String, Long>,
    val totalMinutes: Long,
    val currentApp: String?,
    val reportedAt: Long,
)
