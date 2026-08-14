package com.familyguard.kid.protect

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.familyguard.core.protect.DeviceManagementMode
import com.familyguard.core.protect.ManagedProtectionPolicy
import com.familyguard.kid.R

/** Fully Managed 系统策略的唯一入口，所有特权调用前都重新验证 Device Owner 身份。 */
class KidDevicePolicyController(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, KidDeviceAdminReceiver::class.java)

    fun managementMode(): DeviceManagementMode =
        if (manager.isDeviceOwnerApp(appContext.packageName)) {
            DeviceManagementMode.FULLY_MANAGED
        } else {
            DeviceManagementMode.COMPATIBILITY
        }

    fun capabilities(): ManagedProtectionPolicy = ManagedProtectionPolicy.forMode(managementMode())

    /** Provisioning 完成后幂等应用最小安全基线，不包含 kiosk、恢复出厂等破坏性策略。 */
    fun applyBaseline(): Result<Unit> = runCatching {
        check(managementMode() == DeviceManagementMode.FULLY_MANAGED) {
            "Device owner is required before applying managed protection"
        }
        manager.setShortSupportMessage(admin, appContext.getString(R.string.device_owner_support_message))
        manager.setUninstallBlocked(admin, appContext.packageName, true)
        manager.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
    }

    fun isSelfUninstallBlocked(): Boolean =
        managementMode() == DeviceManagementMode.FULLY_MANAGED &&
            manager.isUninstallBlocked(admin, appContext.packageName)
}
