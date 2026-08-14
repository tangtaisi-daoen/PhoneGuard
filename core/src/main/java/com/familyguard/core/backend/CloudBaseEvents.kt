package com.familyguard.core.backend

import com.familyguard.core.data.AnomalyEvent
import com.familyguard.core.data.IncidentStatus
import com.familyguard.core.stats.IncidentLifecycle
import com.familyguard.core.stats.IncidentReconciler

/**
 * 异常事件上报与查询（events 集合）。
 *
 * 文档结构：
 *   kidDeviceId: String（被控端匿名 uid）
 *   type: String（UNINSTALL_ATTEMPT / PERMISSION_DISABLED / OFFLINE / TIME_CHANGED / NEW_APP / ADMIN_DISABLED）
 *   message: String
 *   occurredAt: Long
 *   read: Boolean（管理端已读标记）
 */
object CloudBaseEvents {

    private const val COLLECTION = "events"

    /** 上报异常事件。adminUid 为绑定管理员（安全规则按 doc.adminUid 放行管理端读取）。 */
    suspend fun report(
        client: CloudBaseClient,
        kidDeviceId: String,
        type: String,
        message: String,
        adminUid: String? = null,
    ): Boolean {
        val now = System.currentTimeMillis()
        val existingDocs = CloudBaseDb.queryDocuments(
            client,
            COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId, "type" to type),
            limit = 100,
        ) ?: return false
        val existing = existingDocs.mapNotNull(::incidentFromDocument)
            .filter { it.status != IncidentStatus.RESOLVED }
            .maxByOrNull { it.lastSeenAt }
        val next = IncidentLifecycle.openOrRefresh(existing, type, message, now)
        if (existing == null) {
            return !CloudBaseDb.insertDocuments(
                client,
                COLLECTION,
                listOf(incidentDocument(kidDeviceId, next, adminUid)),
            ).isNullOrEmpty()
        }
        val updated = CloudBaseDb.updateDocuments(
            client,
            COLLECTION,
            where = mapOf("_id" to existing.id),
            data = incidentDocument(kidDeviceId, next, adminUid),
        )
        return updated != null && updated > 0
    }

    /** 查询未读异常事件（按时间倒序——查询按最新；这里取最近 limit 条未读）。 */
    suspend fun fetchUnread(client: CloudBaseClient, kidDeviceId: String, limit: Int = 10): List<AnomalyEvent> {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId, "read" to false),
            limit = limit,
        ) ?: return emptyList()
        return docs.mapNotNull(::incidentFromDocument)
    }

    /** 查询全部异常事件（列表页用）。 */
    suspend fun fetchAll(client: CloudBaseClient, kidDeviceId: String, limit: Int = 50): List<AnomalyEvent> {
        return fetchAllOrNull(client, kidDeviceId, limit).orEmpty()
    }

    /** 查询全部异常事件；请求失败返回 null，便于 UI 区分网络失败和空列表。 */
    suspend fun fetchAllOrNull(client: CloudBaseClient, kidDeviceId: String, limit: Int = 50): List<AnomalyEvent>? {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId),
            limit = limit,
        ) ?: return null
        return docs.mapNotNull(::incidentFromDocument)
    }

    /** 标记被控端全部事件为已读（通知弹出后调用，防止重复通知）。 */
    suspend fun markAllRead(client: CloudBaseClient, kidDeviceId: String): Boolean {
        val updated = CloudBaseDb.updateDocuments(
            client, COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId, "read" to false),
            data = mapOf("read" to true),
        )
        return updated != null && updated > 0
    }

    suspend fun acknowledgeOpen(client: CloudBaseClient, kidDeviceId: String): Boolean {
        val docs = CloudBaseDb.queryDocuments(
            client,
            COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId),
            limit = 100,
        ) ?: return false
        val now = System.currentTimeMillis()
        val open = docs.mapNotNull(::incidentFromDocument).filter { it.status == IncidentStatus.OPEN }
        if (open.isEmpty()) return true
        return open.all { incident ->
            val acknowledged = IncidentLifecycle.acknowledge(incident, now)
            val updated = CloudBaseDb.updateDocuments(
                client,
                COLLECTION,
                where = mapOf("_id" to incident.id),
                data = incidentDocument(kidDeviceId, acknowledged),
            )
            updated != null && updated > 0
        }
    }

    suspend fun resolve(client: CloudBaseClient, kidDeviceId: String, type: String): Boolean {
        val docs = CloudBaseDb.queryDocuments(
            client,
            COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId, "type" to type),
            limit = 100,
        ) ?: return false
        val active = docs.mapNotNull(::incidentFromDocument)
            .filter { it.status != IncidentStatus.RESOLVED }
        if (active.isEmpty()) return true
        val now = System.currentTimeMillis()
        return active.all { incident ->
            val resolved = IncidentLifecycle.resolve(incident, now)
            val updated = CloudBaseDb.updateDocuments(
                client,
                COLLECTION,
                where = mapOf("_id" to incident.id),
                data = incidentDocument(kidDeviceId, resolved),
            )
            updated != null && updated > 0
        }
    }

    /** 一次查询对账所有健康条件：新异常开启、持续异常节流刷新、恢复条件自动关闭。adminUid 供新事件写入归属字段。 */
    suspend fun reconcileConditions(
        client: CloudBaseClient,
        kidDeviceId: String,
        activeConditions: Map<String, String>,
        managedTypes: Set<String>,
        refreshIntervalMs: Long = 5 * 60_000L,
        adminUid: String? = null,
    ): Boolean {
        val docs = CloudBaseDb.queryDocuments(
            client,
            COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId),
            limit = 100,
        ) ?: return false
        val plan = IncidentReconciler.plan(
            existing = docs.mapNotNull(::incidentFromDocument),
            activeConditions = activeConditions,
            managedTypes = managedTypes,
            nowMs = System.currentTimeMillis(),
            refreshIntervalMs = refreshIntervalMs,
        )
        val upsertsOk = plan.upserts.all { incident ->
            if (incident.id.isBlank()) {
                !CloudBaseDb.insertDocuments(
                    client,
                    COLLECTION,
                    listOf(incidentDocument(kidDeviceId, incident, adminUid)),
                ).isNullOrEmpty()
            } else {
                val updated = CloudBaseDb.updateDocuments(
                    client,
                    COLLECTION,
                    where = mapOf("_id" to incident.id),
                    data = incidentDocument(kidDeviceId, incident, adminUid),
                )
                updated != null && updated > 0
            }
        }
        val resolvedOk = plan.resolved.all { incident ->
            val updated = CloudBaseDb.updateDocuments(
                client,
                COLLECTION,
                where = mapOf("_id" to incident.id),
                data = incidentDocument(kidDeviceId, incident, adminUid),
            )
            updated != null && updated > 0
        }
        return upsertsOk && resolvedOk
    }
}

internal fun incidentDocument(
    kidDeviceId: String,
    incident: AnomalyEvent,
    adminUid: String? = null,
): Map<String, Any?> {
    val doc = mutableMapOf<String, Any?>(
        "kidDeviceId" to kidDeviceId,
        "type" to incident.type,
        "message" to incident.message,
        "occurredAt" to incident.occurredAt,
        "dedupKey" to incident.dedupKey,
        "status" to incident.status.name,
        "firstSeenAt" to incident.firstSeenAt,
        "lastSeenAt" to incident.lastSeenAt,
        "acknowledgedAt" to incident.acknowledgedAt,
        "resolvedAt" to incident.resolvedAt,
        "occurrenceCount" to incident.occurrenceCount,
        "read" to incident.read,
    )
    // 归属字段（安全规则按 doc.adminUid 放行管理端读取；仅绑定后非空）
    if (!adminUid.isNullOrBlank()) doc["adminUid"] = adminUid
    return doc
}

internal fun incidentFromDocument(doc: Map<String, Any?>): AnomalyEvent? {
    val type = doc["type"]?.toString() ?: return null
    val occurredAt = doc["occurredAt"]?.toString()?.toLongOrNull() ?: 0L
    return AnomalyEvent(
        type = type,
        message = doc["message"]?.toString().orEmpty(),
        occurredAt = occurredAt,
        id = doc["_id"]?.toString().orEmpty(),
        dedupKey = doc["dedupKey"]?.toString().orEmpty().ifBlank { type },
        status = runCatching {
            IncidentStatus.valueOf(doc["status"]?.toString().orEmpty())
        }.getOrDefault(IncidentStatus.OPEN),
        firstSeenAt = doc["firstSeenAt"]?.toString()?.toLongOrNull() ?: occurredAt,
        lastSeenAt = doc["lastSeenAt"]?.toString()?.toLongOrNull() ?: occurredAt,
        acknowledgedAt = doc["acknowledgedAt"]?.toString()?.toLongOrNull() ?: 0L,
        resolvedAt = doc["resolvedAt"]?.toString()?.toLongOrNull() ?: 0L,
        occurrenceCount = doc["occurrenceCount"]?.toString()?.toIntOrNull() ?: 1,
        read = doc["read"]?.toString()?.toBooleanStrictOrNull() ?: false,
    )
}
