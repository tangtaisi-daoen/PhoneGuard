package com.familyguard.kid.protect

/** Android 12+ 管理员集成配置的纯策略，避免接管时误停用 OPPO 系统应用。 */
object ProvisioningModePolicy {
    const val leaveAllSystemAppsEnabled: Boolean = true

    fun selectMode(
        allowedModes: List<Int>?,
        fullyManagedMode: Int,
    ): Int? = fullyManagedMode.takeIf { allowedModes == null || it in allowedModes }
}
