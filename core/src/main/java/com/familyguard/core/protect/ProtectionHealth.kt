package com.familyguard.core.protect

enum class ProtectionHealth {
    ADMIN_DISABLED,
    DEVICE_OWNER_MISSING,
    UNINSTALL_PROTECTION_MISSING,
    PROTECTED,
}

fun evaluateProtectionHealth(
    adminActive: Boolean,
    deviceOwner: Boolean,
    selfUninstallBlocked: Boolean,
): ProtectionHealth = when {
    !adminActive -> ProtectionHealth.ADMIN_DISABLED
    !deviceOwner -> ProtectionHealth.DEVICE_OWNER_MISSING
    !selfUninstallBlocked -> ProtectionHealth.UNINSTALL_PROTECTION_MISSING
    else -> ProtectionHealth.PROTECTED
}
