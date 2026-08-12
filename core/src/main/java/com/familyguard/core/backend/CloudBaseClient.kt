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
 * CloudBase HTTP API 客户端（官方 JS SDK 3.x 同款 REST 接口）。
 *
 * Base URL：https://{envId}.api.tcloudbasegateway.com（环境级泛域名，CNAME 到 prod.paasgw.tencentcloudbase.com）
 * 认证：Authorization: Bearer <access_token>；access_token 有效期 2 小时，
 *       收到 401 时用 refresh_token 自动刷新并重试一次（POST /auth/v1/token，grant_type=refresh_token）。
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

    /** 最近一次请求失败的可读原因（诊断用）。 */
    var lastError: String? = null

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
            lastError = null
            runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
        } else {
            lastError = describeError(code, text)
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
            .onFailure { lastError = "网络异常: ${it.message}"; return Pair(-1, null) }
            .getOrNull()
            ?.use { resp -> Pair(resp.code, resp.body?.string()) }
            ?: Pair(-1, null)
    }

    /** 用 refresh_token 换取新 token；成功更新本地并回调持久化。 */
    fun refreshTokens(): Boolean {
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

    /** 把 (code, body) 转成可读错误描述。 */
    private fun describeError(code: Int, text: String?): String = when {
        code < 0 -> "网络异常（连接失败/超时）"
        code == 401 -> "认证失败（token 无效或已过期且刷新失败）"
        code == 403 -> "无权限（HTTP 403）"
        code == 404 -> "资源不存在（HTTP 404，检查集合是否已创建）"
        code == 429 || code == 422 -> "请求过频或超限（HTTP $code）"
        else -> {
            val msg = runCatching {
                gson.fromJson(text, JsonObject::class.java)?.get("message")?.asString
            }.getOrNull()
            "HTTP $code: ${msg ?: text?.take(120) ?: "无响应"}"
        }
    }
}
