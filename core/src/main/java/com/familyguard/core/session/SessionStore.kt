package com.familyguard.core.session

import android.content.Context
import android.content.SharedPreferences

/**
 * 会话与本地状态存储（SharedPreferences 私有文件）。
 *
 * 说明：云端 token 属于敏感凭据，正式发布前应升级为 EncryptedSharedPreferences（androidx.security）。
 * 管理端存：accessToken / refreshToken / userId / username
 * 被控端存：deviceId（匿名身份，须保持稳定）/ inviteCode / boundAdminUid
 */
object SessionStore {

    private const val PREFS_NAME = "familyguard_session"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_INVITE_CODE = "invite_code"
    private const val KEY_BOUND_ADMIN_UID = "bound_admin_uid"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // ---- 管理端会话 ----

    fun saveAuth(accessToken: String, refreshToken: String?, userId: String, username: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    val accessToken: String? get() = prefs.getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = prefs.getString(KEY_REFRESH_TOKEN, null)
    val userId: String? get() = prefs.getString(KEY_USER_ID, null)
    val username: String? get() = prefs.getString(KEY_USERNAME, null)

    val isLoggedIn: Boolean get() = !accessToken.isNullOrBlank()

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .apply()
    }

    // ---- 被控端状态 ----

    /** 被控端匿名身份：首次生成后保持稳定，卸载重装会变化（属预期）。 */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: generateDeviceId().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }

    fun saveBinding(inviteCode: String, boundAdminUid: String) {
        prefs.edit()
            .putString(KEY_INVITE_CODE, inviteCode)
            .putString(KEY_BOUND_ADMIN_UID, boundAdminUid)
            .apply()
    }

    val inviteCode: String? get() = prefs.getString(KEY_INVITE_CODE, null)
    val boundAdminUid: String? get() = prefs.getString(KEY_BOUND_ADMIN_UID, null)

    val isBound: Boolean get() = !inviteCode.isNullOrBlank() && !boundAdminUid.isNullOrBlank()

    private fun generateDeviceId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
