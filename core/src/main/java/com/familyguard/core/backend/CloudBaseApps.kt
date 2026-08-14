package com.familyguard.core.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.familyguard.core.stats.InstalledAppFilter

/**
 * 被控端已装应用列表（apps 集合）。
 *
 * 文档结构：
 *   kidDeviceId: String（唯一）
 *   apps: [{pkg: String, name: String}]（已装应用，供管理端规则选择）
 *   updatedAt: Long
 */
object CloudBaseApps {

    private const val COLLECTION = "apps"

    /** 上报已装应用列表（upsert）。 */
    suspend fun upsert(client: CloudBaseClient, kidDeviceId: String, apps: List<Pair<String, String>>): Boolean {
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
        val data = mapOf("apps" to list, "updatedAt" to System.currentTimeMillis())
        return if (existing.isEmpty()) {
            val doc = mutableMapOf<String, Any?>("kidDeviceId" to kidDeviceId)
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

    /** 拉取被控端已装应用列表（管理端用）。 */
    suspend fun fetch(client: CloudBaseClient, kidDeviceId: String): List<Pair<String, String>> {
        return fetchOrNull(client, kidDeviceId).orEmpty()
    }

    /** 拉取被控端应用；查询失败返回 null，无上报数据返回空列表。 */
    suspend fun fetchOrNull(client: CloudBaseClient, kidDeviceId: String): List<Pair<String, String>>? {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("kidDeviceId" to kidDeviceId), limit = 1,
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
}
