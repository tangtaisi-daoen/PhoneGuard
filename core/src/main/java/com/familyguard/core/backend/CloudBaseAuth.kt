package com.familyguard.core.backend

import com.google.gson.JsonObject

/** 认证结果。 */
data class AuthResult(
    val accessToken: String,
    val refreshToken: String?,
    val userId: String,
)

/**
 * CloudBase 身份认证 API（v1）。
 * 参考：https://docs.cloudbase.net/http-api/auth 与官方 Android Kotlin 快速开始。
 *
 * 流程：发送邮箱验证码 → 校验验证码拿 verification_token → 注册（携带用户名密码）→ 之后账号密码登录。
 */
object CloudBaseAuth {

    /**
     * 发送邮箱验证码。
     * @param target ANY = 不限制；USER = 账号必须存在才发送（登录场景）。
     * @return verification_id
     */
    suspend fun sendEmailVerification(client: CloudBaseClient, email: String, target: String = "ANY"): String? {
        val resp = client.request(
            "POST", "/auth/v1/verification",
            mapOf("email" to email, "target" to target),
        ) ?: return null
        return resp.get("verification_id")?.asString ?: resp.get("verificationId")?.asString
    }

    /** 校验邮箱验证码，返回 verification_token。 */
    suspend fun verifyEmailCode(client: CloudBaseClient, verificationId: String, code: String): String? {
        val resp = client.request(
            "POST", "/auth/v1/verification/verify",
            mapOf("verification_id" to verificationId, "verification_code" to code),
        ) ?: return null
        return resp.get("verification_token")?.asString ?: resp.get("verificationToken")?.asString
    }

    /** 邮箱验证码注册（携带用户名+密码，注册后自动登录）。 */
    suspend fun signUpWithEmail(
        client: CloudBaseClient,
        email: String,
        verificationToken: String,
        username: String,
        password: String,
    ): AuthResult? {
        val resp = client.request(
            "POST", "/auth/v1/signup",
            mapOf(
                "email" to email,
                "verification_token" to verificationToken,
                "username" to username,
                "password" to password,
            ),
        ) ?: return null
        val token = resp.get("access_token")?.asString ?: return null
        val userId = resp.get("sub")?.asString ?: return null
        client.accessToken = token
        return AuthResult(token, resp.get("refresh_token")?.asString, userId)
    }

    /** 账号密码登录。 */
    suspend fun signIn(client: CloudBaseClient, username: String, password: String): AuthResult? {
        val resp = client.request(
            "POST", "/auth/v1/signin",
            mapOf("username" to username, "password" to password),
        ) ?: return null
        val token = resp.get("access_token")?.asString ?: return null
        val userId = resp.get("sub")?.asString ?: return null
        client.accessToken = token
        return AuthResult(token, resp.get("refresh_token")?.asString, userId)
    }

    /**
     * 匿名登录（被控端使用，无需注册）。
     * deviceId 需本地缓存并保持稳定，同一设备长期有效。
     */
    suspend fun signInAnonymously(client: CloudBaseClient, deviceId: String): AuthResult? {
        val resp = client.request(
            "POST", "/auth/v1/signin/anonymously",
            null,
            headers = mapOf("x-device-id" to deviceId),
        ) ?: return null
        val token = resp.get("access_token")?.asString ?: return null
        val userId = resp.get("sub")?.asString ?: resp.get("uid")?.asString ?: return null
        client.accessToken = token
        return AuthResult(token, resp.get("refresh_token")?.asString, userId)
    }

    /** 从任意 JsonObject 中提取可读错误信息（用于 UI 提示）。 */
    fun errorMessage(resp: JsonObject?): String? =
        resp?.get("message")?.asString ?: resp?.get("msg")?.asString
}
