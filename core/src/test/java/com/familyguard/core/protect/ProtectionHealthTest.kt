package com.familyguard.core.protect

import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionHealthTest {

    @Test
    fun `inactive device admin is reported first`() {
        assertEquals(
            ProtectionHealth.ADMIN_DISABLED,
            evaluateProtectionHealth(adminActive = false, deviceOwner = false, selfUninstallBlocked = false),
        )
    }

    @Test
    fun `legacy admin never claims fully managed protection`() {
        assertEquals(
            ProtectionHealth.DEVICE_OWNER_MISSING,
            evaluateProtectionHealth(adminActive = true, deviceOwner = false, selfUninstallBlocked = false),
        )
    }

    @Test
    fun `device owner without uninstall baseline is unhealthy`() {
        assertEquals(
            ProtectionHealth.UNINSTALL_PROTECTION_MISSING,
            evaluateProtectionHealth(adminActive = true, deviceOwner = true, selfUninstallBlocked = false),
        )
    }

    @Test
    fun `fully managed baseline is protected`() {
        assertEquals(
            ProtectionHealth.PROTECTED,
            evaluateProtectionHealth(adminActive = true, deviceOwner = true, selfUninstallBlocked = true),
        )
    }
}
