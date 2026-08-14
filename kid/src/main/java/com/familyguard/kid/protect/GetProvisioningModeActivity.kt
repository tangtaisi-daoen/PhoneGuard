package com.familyguard.kid.protect

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/** Android 12+ 二维码配网时只接受 Fully Managed，不创建工作资料。 */
class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowedModes = intent.getIntegerArrayListExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_ALLOWED_PROVISIONING_MODES,
        )
        val selectedMode = ProvisioningModePolicy.selectMode(
            allowedModes = allowedModes,
            fullyManagedMode = DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        )
        if (selectedMode != null) {
            setResult(
                RESULT_OK,
                Intent()
                    .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, selectedMode)
                    .putExtra(
                        DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        ProvisioningModePolicy.leaveAllSystemAppsEnabled,
                    ),
            )
        } else {
            setResult(RESULT_CANCELED)
        }
        finish()
    }
}
