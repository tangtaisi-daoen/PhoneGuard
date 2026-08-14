package com.familyguard.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * 会话与本地状态存储（EncryptedSharedPreferences，Android Keystore 主密钥）。
 *
 * 管理端存：accessToken / refreshToken / userId / username
 * 被控端存：deviceId（匿名身份，须保持稳定）/ inviteCode / boundAdminUid
 *
 * 迁移：旧版本使用明文 SharedPreferences（familyguard_session），
 * 首次升级时自动把已有值搬入加密存储并删除明文文件。
 */
object SessionStore {

    private const val PREFS_NAME = "familyguard_session_secure"
    private const val LEGACY_PREFS_NAME = "familyguard_session"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_INVITE_CODE = "invite_code"
    private const val KEY_BOUND_ADMIN_UID = "bound_admin_uid"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val appContext = context.applicationContext
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        // 注意：security-crypto 1.0.0 的 create 签名顺序为 (fileName, masterKeyAlias, context, keyScheme, valueScheme)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            appContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        migrateLegacy(appContext)
    }

    /**
     * 一次性迁移旧明文存储：键值搬入加密存储后删除明文文件。
     * 纯逻辑判定提取为 internal 便于单元测试。
     */
    private fun migrateLegacy(appContext: Context) {
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val migrated = legacy.all
        if (migrated.isNotEmpty() && shouldMigrate(migrated)) {
            prefs.edit().apply {
                migrated.forEach { (k, v) ->
                    when (v) {
                        is String -> putString(k, v)
                        is Long -> putLong(k, v)
                        is Int -> putInt(k, v)
                        is Boolean -> putBoolean(k, v)
                        is Float -> putFloat(k, v)
                    }
                }
            }.apply()
            // 明文文件删除（allowBackup=false 下不进入备份）
            appContext.deleteSharedPreferences(LEGACY_PREFS_NAME)
        }
    }

    /** 迁移判定：明文文件中存在任一业务键即迁移。 */
    internal fun shouldMigrate(legacyValues: Map<String, *>): Boolean =
        legacyValues.containsKey(KEY_ACCESS_TOKEN) ||
            legacyValues.containsKey(KEY_REFRESH_TOKEN) ||
            legacyValues.containsKey(KEY_USER_ID) ||
            legacyValues.containsKey(KEY_DEVICE_ID) ||
            legacyValues.containsKey(KEY_INVITE_CODE)

    // ---- 管理端会话 ----

    fun saveAuth(accessToken: String, refreshToken: String?, userId: String, username: String) {
        saveTokens(accessToken, refreshToken)
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    /** token 刷新后回写（不覆盖用户信息）。 */
    fun saveTokens(accessToken: String, refreshToken: String?) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
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
