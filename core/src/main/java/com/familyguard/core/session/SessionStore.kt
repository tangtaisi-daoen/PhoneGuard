package com.familyguard.core.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 会话与本地状态存储（EncryptedSharedPreferences，AES256-GCM）。
 *
 * 云端 token 属于敏感凭据，落盘前加密（androidx.security-crypto；密钥由 Android Keystore
 * 持有，不可导出）。管理端存：accessToken / refreshToken / userId / username；
 * 被控端存：deviceId（匿名身份，须保持稳定）/ inviteCode / boundAdminUid。
 *
 * 迁移：首次 init 时把旧版明文 SharedPreferences（familyguard_session）中的数据
 * 完整迁移到加密存储，迁移成功（commit 返回 true）后才删除明文文件，保证幂等与不丢数据。
 */
object SessionStore {

    /** 旧版明文存储文件名（迁移完成后删除）。 */
    private const val LEGACY_PREFS_NAME = "familyguard_session"
    /** 加密存储文件名（AES256-SIV key / AES256-GCM value）。 */
    private const val SECURE_PREFS_NAME = "familyguard_session_secure"
    /** 迁移完成标志（写入加密存储内）。 */
    private const val KEY_MIGRATED = "migrated_v2"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_INVITE_CODE = "invite_code"
    private const val KEY_BOUND_ADMIN_UID = "bound_admin_uid"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        initWith(
            EncryptedSharedPreferences.create(
                appContext,
                SECURE_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            ),
        )
        val legacy = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (migrateLegacy(legacy, prefs!!)) {
            appContext.deleteSharedPreferences(LEGACY_PREFS_NAME)
        }
    }

    /**
     * 仅测试用：以给定 SharedPreferences 实现初始化（生产代码走 [init] 的加密存储）。
     * 迁移逻辑本身是纯接口操作，见 [migrateLegacy]，可在 JVM 单测中验证。
     */
    internal fun initWith(securePrefs: SharedPreferences) {
        if (prefs != null) return
        prefs = securePrefs
    }

    /**
     * 把旧版明文 prefs 迁移到加密存储。幂等：已迁移（secure 中带完成标志）则直接成功。
     * 返回是否成功；调用方仅在成功时删除明文文件。
     */
    internal fun migrateLegacy(legacy: SharedPreferences, secure: SharedPreferences): Boolean {
        if (secure.getBoolean(KEY_MIGRATED, false)) return true
        val all = legacy.all
        if (all.isEmpty()) {
            secure.edit().putBoolean(KEY_MIGRATED, true).commit()
            return true
        }
        val editor = secure.edit()
        for ((key, value) in all) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
                else -> { /* 未知类型跳过，不影响其余字段 */ }
            }
        }
        editor.putBoolean(KEY_MIGRATED, true)
        return editor.commit()
    }

    // ---- 管理端会话 ----

    fun saveAuth(accessToken: String, refreshToken: String?, userId: String, username: String) {
        saveTokens(accessToken, refreshToken)
        requirePrefs().edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .apply()
    }

    /** token 刷新后回写（不覆盖用户信息）。 */
    fun saveTokens(accessToken: String, refreshToken: String?) {
        requirePrefs().edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    val accessToken: String? get() = requirePrefs().getString(KEY_ACCESS_TOKEN, null)
    val refreshToken: String? get() = requirePrefs().getString(KEY_REFRESH_TOKEN, null)
    val userId: String? get() = requirePrefs().getString(KEY_USER_ID, null)
    val username: String? get() = requirePrefs().getString(KEY_USERNAME, null)

    val isLoggedIn: Boolean get() = !accessToken.isNullOrBlank()

    fun clearAuth() {
        requirePrefs().edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .apply()
    }

    // ---- 被控端状态 ----

    /** 被控端匿名身份：首次生成后保持稳定，卸载重装会变化（属预期）。 */
    val deviceId: String
        get() = requirePrefs().getString(KEY_DEVICE_ID, null)
            ?: generateDeviceId().also { requirePrefs().edit().putString(KEY_DEVICE_ID, it).apply() }

    fun saveBinding(inviteCode: String, boundAdminUid: String) {
        requirePrefs().edit()
            .putString(KEY_INVITE_CODE, inviteCode)
            .putString(KEY_BOUND_ADMIN_UID, boundAdminUid)
            .apply()
    }

    val inviteCode: String? get() = requirePrefs().getString(KEY_INVITE_CODE, null)
    val boundAdminUid: String? get() = requirePrefs().getString(KEY_BOUND_ADMIN_UID, null)

    val isBound: Boolean get() = !inviteCode.isNullOrBlank() && !boundAdminUid.isNullOrBlank()

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("SessionStore.init(context) 必须在读写前调用")

    /**
     * 仅测试用：清空内存态。下一次 init 会重新创建加密存储并再次执行迁移
     * （磁盘上的加密数据保留；明文文件如未删除会被再次迁移，符合幂等语义）。
     */
    fun resetForTest() {
        prefs = null
    }

    private fun generateDeviceId(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
