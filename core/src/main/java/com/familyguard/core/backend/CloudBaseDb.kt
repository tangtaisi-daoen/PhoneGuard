package com.familyguard.core.backend

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * CloudBase 文档型数据库 HTTP API。
 * 参考：https://docs.cloudbase.net/http-api/nosql/nosql-restful-api
 *
 * 关键格式：
 * - 插入 body: {"data": [docs]}，响应: {"insertedIds": [...]}
 * - 查询响应: {"offset":0,"limit":N,"list":[...]}（Strict EJSON：数字为 {"$numberLong":"..."}）
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

    /** 查询文档列表，返回文档（已做 EJSON 数字解包）。过滤条件走 URL 参数 query（实测 where 会被忽略）。 */
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

    /** 按 _id 删除单个文档。 */
    suspend fun deleteDocument(client: CloudBaseClient, collection: String, docId: String): Boolean {
        val resp = client.request(
            "DELETE", "${dbPath()}/collections/$collection/documents/$docId",
        ) ?: return false
        return resp.has("deleted") || resp.has("requestId")
    }

    /**
     * 按查询条件批量更新文档。
     * 实测：data 必须显式包 $set 操作符（否则 updated=0），body 为 {"query":..., "data":{"$set":...}}。
     */
    suspend fun updateDocuments(
        client: CloudBaseClient,
        collection: String,
        where: Map<String, Any?>,
        data: Map<String, Any?>,
    ): Int? {
        val resp = client.request(
            "PATCH", "${dbPath()}/collections/$collection/documents",
            mapOf("query" to where, "data" to mapOf("\u0024set" to data)),
        ) ?: return null
        return resp.get("updated")?.asInt ?: resp.get("matched")?.asInt
    }

    /** 把 Strict EJSON 字段解包为普通值（{"$numberLong":"123"} → 123L）。 */
    private fun unwrapEjson(obj: JsonObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        for ((k, v) in obj.entrySet()) {
            result[k] = unwrapValue(v)
        }
        return result
    }

    private fun unwrapValue(v: JsonElement): Any? = when {
        v.isJsonObject -> {
            val o = v.asJsonObject
            val n = o.get("\u0024numberLong")
            if (n != null && n.isJsonPrimitive) {
                n.asString.toLongOrNull() ?: n.asString
            } else {
                // 其他对象保持原样（_id 等为普通 JSON）
                v.toString()
            }
        }
        v.isJsonPrimitive -> {
            val p = v.asJsonPrimitive
            when {
                p.isBoolean -> p.asBoolean
                p.isNumber -> p.asLong
                else -> p.asString
            }
        }
        else -> v.toString()
    }

    private fun encode(map: Map<String, Any?>): String {
        val json = com.google.gson.Gson().toJson(map)
        return java.net.URLEncoder.encode(json, "UTF-8")
    }
}
