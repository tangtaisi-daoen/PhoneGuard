package com.familyguard.kid.guide

import android.content.Context
import android.os.PowerManager
import android.provider.Settings

/** 防护状态检测（无障碍/使用情况访问/电池优化/自启动）。 */
object GuardStatus {

    /** 使用情况访问是否已授权。 */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
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
}
