package com.familyguard.core.backend

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * CloudBase HTTP API 客户端（官方 Android Kotlin 推荐接入方式）。
 * 参考：https://docs.cloudbase.net/http-api/basic/overview
 *
 * 认证方式：Bearer access_token（用户身份）；未登录时可先走匿名/验证码注册流程。
 * 国内地域 baseUrl: https://{envId}.api.tcloudbasegateway.com
 */
class CloudBaseClient(
    val envId: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    val baseUrl: String = "https://$envId.api.tcloudbasegateway.com"

    /** 当前用户 access token；登录/注册成功后写入。 */
    var accessToken: String? = null

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 通用请求：method + path（以 / 开头）+ body，返回解析后的 JsonObject（失败返回 null）。 */
    suspend fun request(
        method: String,
        path: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Content-Type", jsonMediaType.toString())
        headers.forEach { (k, v) -> builder.header(k, v) }
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) {
            builder.method(method, gson.toJson(body).toRequestBody(jsonMediaType))
        } else if (method == "POST" || method == "PUT" || method == "PATCH") {
            builder.method(method, "".toRequestBody(null))
        } else {
            builder.method(method, null)
        }
        runCatching { http.newCall(builder.build()).execute() }
            .onFailure { return@withContext null }
            .getOrNull()
            ?.use { resp ->
                val text = resp.body?.string() ?: return@use null
                if (!resp.isSuccessful) return@use null
                runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
            }
    }

    /** 泛型请求：直接返回解析后的对象。 */
    suspend fun <T> requestTyped(
        method: String,
        path: String,
        body: Any? = null,
        type: TypeToken<T>,
    ): T? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Content-Type", jsonMediaType.toString())
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) {
            builder.method(method, gson.toJson(body).toRequestBody(jsonMediaType))
        } else {
            builder.method(method, null)
        }
        runCatching { http.newCall(builder.build()).execute() }
            .onFailure { return@withContext null }
            .getOrNull()
            ?.use { resp ->
                val text = resp.body?.string() ?: return@use null
                if (!resp.isSuccessful) return@use null
                runCatching { gson.fromJson<T>(text, type.type) }.getOrNull()
            }
    }
}
