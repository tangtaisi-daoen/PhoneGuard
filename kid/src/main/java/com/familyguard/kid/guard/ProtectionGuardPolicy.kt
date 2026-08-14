package com.familyguard.kid.guard

data class ProtectionPermissionState(
    val usageAccessGranted: Boolean,
    val overlayGranted: Boolean,
    val autostartConfirmed: Boolean,
)

/** 初次授权必须放行；已经生效的关键权限才进入防关闭保护。 */
fun shouldBlockProtectionPage(
    risk: ProtectionPageRisk,
    state: ProtectionPermissionState,
): Boolean = when (risk) {
    ProtectionPageRisk.USAGE_ACCESS_DISABLE -> state.usageAccessGranted
    ProtectionPageRisk.OVERLAY_DISABLE -> state.overlayGranted
    ProtectionPageRisk.AUTOSTART_DISABLE -> state.autostartConfirmed
    ProtectionPageRisk.APP_REMOVAL,
    ProtectionPageRisk.ADMIN_DEACTIVATION,
    ProtectionPageRisk.ACCESSIBILITY_DISABLE,
    -> true
}
