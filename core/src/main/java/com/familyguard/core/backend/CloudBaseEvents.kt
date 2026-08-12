package com.familyguard.core.backend

import com.familyguard.core.data.AnomalyEvent

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

    /** 上报异常事件。 */
    suspend fun report(
        client: CloudBaseClient,
        kidDeviceId: String,
        type: String,
        message: String,
    ): Boolean {
        val inserted = CloudBaseDb.insertDocuments(
            client, COLLECTION,
            listOf(
                mapOf(
                    "kidDeviceId" to kidDeviceId,
                    "type" to type,
                    "message" to message,
                    "occurredAt" to System.currentTimeMillis(),
                    "read" to false,
                ),
            ),
        )
        return inserted != null && inserted.isNotEmpty()
    }

    /** 查询未读异常事件（按时间倒序——查询按最新；这里取最近 limit 条未读）。 */
    suspend fun fetchUnread(client: CloudBaseClient, kidDeviceId: String, limit: Int = 10): List<AnomalyEvent> {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId, "read" to false),
            limit = limit,
        ) ?: return emptyList()
        return docs.mapNotNull { doc -> toEvent(doc) }
    }

    /** 查询全部异常事件（列表页用）。 */
    suspend fun fetchAll(client: CloudBaseClient, kidDeviceId: String, limit: Int = 50): List<AnomalyEvent> {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId),
            limit = limit,
        ) ?: return emptyList()
        return docs.mapNotNull { doc -> toEvent(doc) }
    }

    private fun toEvent(doc: Map<String, Any?>): AnomalyEvent? {
        val type = doc["type"]?.toString() ?: return null
        return AnomalyEvent(
            type = type,
            message = doc["message"]?.toString() ?: "",
            occurredAt = doc["occurredAt"]?.toString()?.toLongOrNull() ?: 0L,
        )
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
}
