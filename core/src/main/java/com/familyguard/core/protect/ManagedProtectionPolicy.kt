package com.familyguard.core.protect

/** 被控端当前拥有的系统管理能力档位。 */
enum class DeviceManagementMode {
    COMPATIBILITY,
    FULLY_MANAGED,
}

/**
 * 只描述平台已确认能提供的能力，避免兼容模式在 UI 或上报中声称具备静默更新、防卸载等特权。
 */
data class ManagedProtectionPolicy(
    val blockSelfUninstall: Boolean,
    val silentSelfUpdate: Boolean,
    val systemPackageSuspension: Boolean,
    val disallowUnknownSources: Boolean,
) {
    companion object {
        fun forMode(mode: DeviceManagementMode): ManagedProtectionPolicy = when (mode) {
            DeviceManagementMode.COMPATIBILITY -> ManagedProtectionPolicy(
                blockSelfUninstall = false,
                silentSelfUpdate = false,
                systemPackageSuspension = false,
                disallowUnknownSources = false,
            )

            DeviceManagementMode.FULLY_MANAGED -> ManagedProtectionPolicy(
                blockSelfUninstall = true,
                silentSelfUpdate = true,
                systemPackageSuspension = true,
                disallowUnknownSources = true,
            )
        }
    }
}
