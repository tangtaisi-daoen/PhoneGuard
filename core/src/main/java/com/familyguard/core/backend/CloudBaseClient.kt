package com.familyguard.core.backend

import com.google.gson.Gson
import com.google.gson.JsonObject
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
 * 认证方式：Bearer access_token（用户身份）。
 * access_token 有效期 2 小时，收到 401 时用 refresh_token 自动刷新并重试一次。
 * 刷新接口：POST /auth/v1/token，body {"grant_type":"refresh_token","refresh_token":...}，旧 refresh_token 立即失效。
 */
class CloudBaseClient(
    val envId: String,
    private val onTokenRefreshed: ((access: String, refresh: String?) -> Unit)? = null,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    val baseUrl: String = "https://$envId.api.tcloudbasegateway.com"

    /** 当前用户 access token；登录/注册成功后写入。 */
    var accessToken: String? = null

    /** 刷新令牌；401 时自动用于换取新 token。 */
    var refreshToken: String? = null

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** 通用请求：method + path（以 / 开头）+ body，返回解析后的 JsonObject（失败返回 null）。 */
    suspend fun request(
        method: String,
        path: String,
        body: Any? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonObject? = withContext(Dispatchers.IO) {
        var (code, text) = execute(method, path, body, headers)
        if (code == 401 && accessToken != null && refreshToken != null && refreshTokens()) {
            val retried = execute(method, path, body, headers)
            code = retried.first
            text = retried.second
        }
        if (code in 200..299 && text != null) {
            runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
        } else {
            null
        }
    }

    /** 执行一次请求，返回 (HTTP code, body)。 */
    private fun execute(
        method: String,
        path: String,
        body: Any?,
        headers: Map<String, String>,
        withAuth: Boolean = true,
    ): Pair<Int, String?> {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Content-Type", jsonMediaType.toString())
        headers.forEach { (k, v) -> builder.header(k, v) }
        if (withAuth) accessToken?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) {
            builder.method(method, gson.toJson(body).toRequestBody(jsonMediaType))
        } else if (method == "POST" || method == "PUT" || method == "PATCH") {
            builder.method(method, "".toRequestBody(null))
        } else {
            builder.method(method, null)
        }
        return runCatching { http.newCall(builder.build()).execute() }
            .onFailure { return Pair(-1, null) }
            .getOrNull()
            ?.use { resp -> Pair(resp.code, resp.body?.string()) }
            ?: Pair(-1, null)
    }

    /** 用 refresh_token 换取新 token；成功更新本地并回调持久化。 */
    private fun refreshTokens(): Boolean {
        val rt = refreshToken ?: return false
        val reqBody = mapOf("grant_type" to "refresh_token", "refresh_token" to rt)
        val (code, text) = execute("POST", "/auth/v1/token", reqBody, emptyMap(), withAuth = false)
        if (code !in 200..299 || text == null) return false
        val resp = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull() ?: return false
        val newAccess = resp.get("access_token")?.asString ?: return false
        val newRefresh = resp.get("refresh_token")?.asString
        accessToken = newAccess
        if (newRefresh != null) refreshToken = newRefresh
        onTokenRefreshed?.invoke(newAccess, newRefresh)
        return true
    }
}
