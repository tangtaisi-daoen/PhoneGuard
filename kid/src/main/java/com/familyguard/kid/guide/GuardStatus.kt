package com.familyguard.kid.guide

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** 防护状态检测（无障碍/使用情况访问/电池优化/自启动）。 */
object GuardStatus {

    private const val PROTECTION_PREFERENCES = "protection_permissions"
    private const val KEY_AUTOSTART_CONFIRMED = "autostart_confirmed"

    /** 使用情况访问是否已授权。 */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    /** 无障碍服务是否已开启。 */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(context.packageName + "/" + "com.familyguard.kid.guard.GuardAccessibilityService", ignoreCase = true) }
    }

    /** 是否已忽略电池优化。 */
    fun ignoresBatteryOptimization(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 设备管理器是否已激活（防卸载）。 */
    fun isAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val cn = android.content.ComponentName(context, com.familyguard.kid.protect.KidDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(cn)
    }

    /** 悬浮窗权限（拦截浮层需要）。 */
    fun canDrawOverlays(context: Context): Boolean =
        android.provider.Settings.canDrawOverlays(context)

    /** ColorOS 没有公开查询自启动状态的接口，因此保存用户在引导页的明确确认。 */
    fun isAutostartConfirmed(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PROTECTION_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOSTART_CONFIRMED, false)

    fun confirmAutostart(context: Context) {
        context.applicationContext
            .getSharedPreferences(PROTECTION_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTOSTART_CONFIRMED, true)
            .apply()
    }

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
