package com.familyguard.core.backend

import com.google.gson.JsonArray
import com.google.gson.JsonObject

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
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("kidDeviceId" to kidDeviceId), limit = 1,
        ) ?: return emptyList()
        val doc = docs.firstOrNull() ?: return emptyList()
        val arr = doc["apps"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val pkg = o.get("pkg")?.asString ?: return@mapNotNull null
            pkg to (o.get("name")?.asString ?: pkg)
        }
    }
}
