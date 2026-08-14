package com.familyguard.kid.guard

import android.content.Context

object ProtectionAttemptStore {
    private const val PREFS = "protection_attempt"
    private const val KEY_TYPE = "type"
    private const val KEY_MESSAGE = "message"
    private const val KEY_AT = "occurred_at"

    fun record(context: Context, risk: ProtectionPageRisk, nowMs: Long = System.currentTimeMillis()) {
        val message = when (risk) {
            ProtectionPageRisk.APP_REMOVAL -> "检测到打开手机守护的卸载或强制停止页面，已返回手机守护"
            ProtectionPageRisk.ADMIN_DEACTIVATION -> "检测到停用手机守护设备管理员的尝试，已返回手机守护"
            ProtectionPageRisk.ACCESSIBILITY_DISABLE -> "检测到关闭手机守护无障碍服务的尝试，已返回手机守护"
            ProtectionPageRisk.USAGE_ACCESS_DISABLE -> "检测到关闭手机守护使用情况访问权限的尝试，已返回手机守护"
            ProtectionPageRisk.OVERLAY_DISABLE -> "检测到关闭手机守护悬浮窗权限的尝试，已返回手机守护"
            ProtectionPageRisk.AUTOSTART_DISABLE -> "检测到关闭手机守护自启动权限的尝试，已返回手机守护"
        }
        val type = when (risk) {
            ProtectionPageRisk.APP_REMOVAL -> "UNINSTALL_ATTEMPT"
            ProtectionPageRisk.ADMIN_DEACTIVATION -> "ADMIN_DISABLE_ATTEMPT"
            ProtectionPageRisk.ACCESSIBILITY_DISABLE -> "ACCESSIBILITY_DISABLE_ATTEMPT"
            ProtectionPageRisk.USAGE_ACCESS_DISABLE -> "USAGE_ACCESS_DISABLE_ATTEMPT"
            ProtectionPageRisk.OVERLAY_DISABLE -> "OVERLAY_DISABLE_ATTEMPT"
            ProtectionPageRisk.AUTOSTART_DISABLE -> "AUTOSTART_DISABLE_ATTEMPT"
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TYPE, type)
            .putString(KEY_MESSAGE, message)
            .putLong(KEY_AT, nowMs)
            .apply()
    }

    fun pending(context: Context): ProtectionAttempt? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val type = prefs.getString(KEY_TYPE, null) ?: return null
        return ProtectionAttempt(type, prefs.getString(KEY_MESSAGE, "").orEmpty(), prefs.getLong(KEY_AT, 0L))
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

data class ProtectionAttempt(val type: String, val message: String, val occurredAt: Long)
