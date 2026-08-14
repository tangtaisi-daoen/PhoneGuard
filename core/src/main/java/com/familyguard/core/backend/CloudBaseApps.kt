package com.familyguard.core.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.familyguard.core.stats.InstalledAppFilter

/**
 * 被控端已装应用列表（apps 集合）。
 *
 * 文档结构：
 *   adminUid: String（绑定管理员 uid，管理端读取授权字段）
 *   kidDeviceId: String（唯一）
 *   apps: [{pkg: String, name: String}]（已装应用，供管理端规则选择）
 *   updatedAt: Long
 */
object CloudBaseApps {

    private const val COLLECTION = "apps"

    /** 上报已装应用列表（upsert）。 */
    suspend fun upsert(
        client: CloudBaseClient,
        kidDeviceId: String,
        apps: List<Pair<String, String>>,
        adminUid: String? = null,
    ): Boolean {
        val existing = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("kidDeviceId" to kidDeviceId), limit = 1,
        ) ?: return false
        val list = JsonArray()
        apps.forEach { (pkg, name) ->
            val o = JsonObject()
            o.addProperty("pkg", pkg)
            o.addProperty("name", name)
            list.add(o)
        }
        val data = mutableMapOf<String, Any?>(
            "apps" to list,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (!adminUid.isNullOrBlank()) data["adminUid"] = adminUid
        return if (existing.isEmpty()) {
            val doc = mutableMapOf<String, Any?>("kidDeviceId" to kidDeviceId)
            doc.putAll(data)
            val inserted = CloudBaseDb.insertDocuments(client, COLLECTION, listOf(doc))
            inserted != null && inserted.isNotEmpty()
        } else {
            val docId = existing.first()["_id"]?.toString() ?: return false
            // 已有归属不覆盖，避免重新绑定后把旧管理员的历史应用转移给新管理员。
            if (!existing.first()["adminUid"]?.toString().isNullOrBlank()) data.remove("adminUid")
            val updated = CloudBaseDb.updateDocuments(
                client, COLLECTION,
                where = mapOf("_id" to docId, "kidDeviceId" to kidDeviceId),
                data = data,
            )
            updated != null && updated > 0
        }
    }

    /** 拉取被控端已装应用列表（管理端用）。 */
    suspend fun fetch(
        client: CloudBaseClient,
        kidDeviceId: String,
        adminUid: String? = null,
    ): List<Pair<String, String>> {
        return fetchOrNull(client, kidDeviceId, adminUid).orEmpty()
    }

    /** 拉取被控端应用；查询失败返回 null，无上报数据返回空列表。 */
    suspend fun fetchOrNull(
        client: CloudBaseClient,
        kidDeviceId: String,
        adminUid: String? = null,
    ): List<Pair<String, String>>? {
        val where = mutableMapOf<String, Any?>("kidDeviceId" to kidDeviceId)
        if (!adminUid.isNullOrBlank()) where["adminUid"] = adminUid
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = where, limit = 1,
        ) ?: return null
        val doc = docs.firstOrNull() ?: return emptyList()
        val arr = doc["apps"] as? JsonArray ?: return emptyList()
        val apps = arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val pkg = o.get("pkg")?.asString ?: return@mapNotNull null
            val name = o.get("name")?.asString ?: return@mapNotNull null
            pkg to name
        }
        return InstalledAppFilter.displayableStored(apps)
    }

    /** 被控端为历史应用列表补写管理员归属，供安全规则迁移使用。 */
    suspend fun ensureAdminUid(
        client: CloudBaseClient,
        kidDeviceId: String,
        adminUid: String,
    ): Boolean {
        if (adminUid.isBlank()) return false
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("kidDeviceId" to kidDeviceId), limit = 1,
        ) ?: return false
        return docs.filter { it["adminUid"]?.toString().isNullOrBlank() }.all { doc ->
            val id = doc["_id"]?.toString() ?: return@all false
            val updated = CloudBaseDb.updateDocuments(
                client,
                COLLECTION,
                where = mapOf("_id" to id, "kidDeviceId" to kidDeviceId),
                data = mapOf("adminUid" to adminUid),
            )
            updated != null && updated > 0
        }
    }
}
