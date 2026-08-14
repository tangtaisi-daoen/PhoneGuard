package com.familyguard.kid.guard

enum class ProtectionPageRisk {
    APP_REMOVAL,
    ADMIN_DEACTIVATION,
    ACCESSIBILITY_DISABLE,
    USAGE_ACCESS_DISABLE,
    OVERLAY_DISABLE,
    AUTOSTART_DISABLE,
}

internal fun classifyProtectionPage(
    packageName: String,
    className: String,
    visibleTexts: Set<String>,
): ProtectionPageRisk? {
    if (packageName !in TRUSTED_SETTINGS_PACKAGES) return null
    val text = visibleTexts.joinToString(" ").lowercase()
    val targetsKid = OWN_MARKERS.any { it in text }
    if (!targetsKid) return null

    val normalizedClass = className.lowercase()
    return when {
        (packageName in UNINSTALL_SURFACE_PACKAGES &&
            isAppRemovalSurface(packageName, normalizedClass, text)) ||
            ("installedappdetails" in normalizedClass && APP_ACTIONS.any { it in text }) ->
            ProtectionPageRisk.APP_REMOVAL
        "deviceadmin" in normalizedClass && ADMIN_ACTIONS.any { it in text } ->
            ProtectionPageRisk.ADMIN_DEACTIVATION
        isAccessibilityDetail(normalizedClass, text) ->
            ProtectionPageRisk.ACCESSIBILITY_DISABLE
        isUsageAccessDetail(normalizedClass, text) ->
            ProtectionPageRisk.USAGE_ACCESS_DISABLE
        isOverlayDetail(normalizedClass, text) ->
            ProtectionPageRisk.OVERLAY_DISABLE
        isAutostartDetail(normalizedClass, text) ->
            ProtectionPageRisk.AUTOSTART_DISABLE
        else -> null
    }
}

private fun isAppRemovalSurface(packageName: String, className: String, text: String): Boolean {
    if (APP_ACTIONS.none { it in text }) return false
    // Launcher 页面也会暴露应用图标和编辑态文案；只有明确的卸载拖拽/卸载页面才拦截。
    if (packageName in LAUNCHER_PACKAGES) {
        return "deletedroptarget" in className || "uninstall" in className
    }
    return true
}

private fun isUsageAccessDetail(className: String, text: String): Boolean =
    ("usageaccess" in className || USAGE_ACCESS_ACTIONS.any { it in text }) &&
        USAGE_ACCESS_ACTIONS.any { it in text }

private fun isOverlayDetail(className: String, text: String): Boolean =
    ("drawoverlay" in className || "overlay" in className || OVERLAY_ACTIONS.any { it in text }) &&
        OVERLAY_ACTIONS.any { it in text }

private fun isAutostartDetail(className: String, text: String): Boolean =
    ("startupapp" in className || "autostart" in className || AUTOSTART_ACTIONS.any { it in text }) &&
        AUTOSTART_ACTIONS.any { it in text }

private fun isAccessibilityDetail(className: String, text: String): Boolean {
    if (ACCESSIBILITY_DETAIL_CLASSES.any { it in className } && ACCESSIBILITY_ACTIONS.any { it in text }) {
        return true
    }
    return className.endsWith("subsettings") &&
        ACCESSIBILITY_DETAIL_MARKERS.all { it in text }
}

private val TRUSTED_SETTINGS_PACKAGES = setOf(
    "com.android.settings",
    "com.android.permissioncontroller",
    "com.coloros.safecenter",
    "com.oplus.safecenter",
    "com.coloros.securitypermission",
    "com.oplus.securitypermission",
    "com.oppo.launcher",
    "com.coloros.launcher",
    "com.android.launcher",
    "com.android.launcher3",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.oplus.appdetail",
)
private val UNINSTALL_SURFACE_PACKAGES = setOf(
    "com.oppo.launcher",
    "com.coloros.launcher",
    "com.android.launcher",
    "com.android.launcher3",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.android.permissioncontroller",
    "com.oplus.appdetail",
)
private val LAUNCHER_PACKAGES = setOf(
    "com.oppo.launcher",
    "com.coloros.launcher",
    "com.android.launcher",
    "com.android.launcher3",
)
private val OWN_MARKERS = setOf("手机守护", "com.familyguard.kid", "phoneguard")
private val APP_ACTIONS = setOf("卸载", "强行停止", "强制停止", "停用", "uninstall", "force stop", "disable")
private val ADMIN_ACTIONS = setOf("取消激活", "停用", "解除激活", "deactivate", "disable")
private val ACCESSIBILITY_ACTIONS = setOf("关闭", "停用", "不允许", "turn off", "disable", "not allowed")
private val USAGE_ACCESS_ACTIONS = setOf(
    "使用情况访问",
    "使用记录访问",
    "usage access",
    "permit usage access",
)
private val OVERLAY_ACTIONS = setOf(
    "悬浮窗",
    "显示在其他应用上层",
    "display over other apps",
    "draw over other apps",
)
private val AUTOSTART_ACTIONS = setOf(
    "自启动",
    "自动启动",
    "auto launch",
    "autostart",
)
private val ACCESSIBILITY_DETAIL_CLASSES = setOf(
    "accessibilitydetails",
    "toggleaccessibilityservicepreferencefragment",
    "accessibilityservicedialog",
)
private val ACCESSIBILITY_DETAIL_MARKERS = setOf("快捷启用", "简介", "手机守护核心功能")
