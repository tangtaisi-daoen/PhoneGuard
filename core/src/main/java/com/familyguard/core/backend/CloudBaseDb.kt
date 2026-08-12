package com.familyguard.core.backend

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * CloudBase 文档型数据库 HTTP API（REST，官方 JS SDK 3.x 同款）。
 *
 * 关键格式：
 * - 插入 body: {"data": [docs]}，响应: {"insertedIds": [...]}
 * - 查询过滤条件走 URL 参数 query（实测 where 会被忽略），响应: {"offset":0,"limit":N,"list":[...]}
 * - 更新 body: {"query": {...}, "data": {"$set": {...}}}（必须显式 $set）
 * - 删除: DELETE .../documents/{id}，响应: {"deleted":1}
 */
object CloudBaseDb {

    private fun dbPath(): String = "/v1/database/instances/(default)/databases/(default)"

    /** 批量插入文档，返回新文档 _id 列表。 */
    suspend fun insertDocuments(
        client: CloudBaseClient,
        collection: String,
        documents: List<Map<String, Any?>>,
    ): List<String>? {
        val resp = client.request(
            "POST", "${dbPath()}/collections/$collection/documents",
            mapOf("data" to documents),
        ) ?: return null
        val ids = resp.getAsJsonArray("insertedIds") ?: return null
        return ids.mapNotNull { it.asString }
    }

    /** 查询文档列表，返回文档（已做 EJSON 数字解包）。过滤条件走 URL 参数 query。 */
    suspend fun queryDocuments(
        client: CloudBaseClient,
        collection: String,
        where: Map<String, Any?> = emptyMap(),
        limit: Int = 20,
    ): List<Map<String, Any?>>? {
        val query = if (where.isEmpty()) "" else "?query=${encode(where)}"
        val limitStr = if (where.isEmpty()) "?limit=$limit" else "&limit=$limit"
        val resp = client.request(
            "GET", "${dbPath()}/collections/$collection/documents$query$limitStr",
        ) ?: return null
        val list = resp.getAsJsonArray("list") ?: return null
        return list.map { element -> unwrapEjson(element.asJsonObject) }
    }

    /**
     * 按查询条件批量更新文档。
     * 实测：data 必须显式包 $set 操作符（否则 updated=0），批量更新需显式 multi=true。
     */
    suspend fun updateDocuments(
        client: CloudBaseClient,
        collection: String,
        where: Map<String, Any?>,
        data: Map<String, Any?>,
    ): Int? {
        val resp = client.request(
            "PATCH", "${dbPath()}/collections/$collection/documents",
            mapOf("query" to where, "data" to mapOf("\u0024set" to data), "multi" to true),
        ) ?: return null
        return resp.get("updated")?.asInt ?: resp.get("matched")?.asInt
    }

    /** 按 _id 删除单个文档。 */
    suspend fun deleteDocument(client: CloudBaseClient, collection: String, docId: String): Boolean {
        val resp = client.request(
            "DELETE", "${dbPath()}/collections/$collection/documents/$docId",
        ) ?: return false
        return resp.has("deleted") || resp.has("requestId")
    }

    /** 把 Strict EJSON 解包为普通 JsonElement（递归：$numberLong→Long、$numberInt→Int、数组/对象递归）。 */
    private fun unwrapElement(v: JsonElement): JsonElement = when {
        v.isJsonObject -> {
            val o = v.asJsonObject
            val n = o.get("\u0024numberLong")
            if (n != null && n.isJsonPrimitive) {
                val l = n.asString.toLongOrNull()
                if (l != null) JsonPrimitive(l) else JsonPrimitive(n.asString)
            } else {
                val i = o.get("\u0024numberInt")
                if (i != null && i.isJsonPrimitive) {
                    val v2 = i.asString.toIntOrNull()
                    if (v2 != null) JsonPrimitive(v2) else JsonPrimitive(i.asString)
                } else {
                    val out = JsonObject()
                    o.entrySet().forEach { (k, vv) -> out.add(k, unwrapElement(vv)) }
                    out
                }
            }
        }
        v.isJsonArray -> {
            val out = JsonArray()
            v.asJsonArray.forEach { out.add(unwrapElement(it)) }
            out
        }
        else -> v
    }

    /** 把文档转成 Map（EJSON 已解包）。 */
    private fun unwrapEjson(obj: JsonObject): Map<String, Any?> {
        val unwrapped = unwrapElement(obj).asJsonObject
        val result = mutableMapOf<String, Any?>()
        for ((k, v) in unwrapped.entrySet()) {
            result[k] = when {
                v.isJsonPrimitive -> {
                    val p = v.asJsonPrimitive
                    when {
                        p.isBoolean -> p.asBoolean
                        p.isNumber -> p.asLong
                        else -> p.asString
                    }
                }
                else -> v // JsonObject / JsonArray 保持
            }
        }
        return result
    }

    private fun encode(map: Map<String, Any?>): String {
        val json = com.google.gson.Gson().toJson(map)
        return java.net.URLEncoder.encode(json, "UTF-8")
    }
}
