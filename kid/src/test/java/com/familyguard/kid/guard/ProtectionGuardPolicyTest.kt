package com.familyguard.kid.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionGuardPolicyTest {
    @Test
    fun `missing permissions remain reachable for initial setup`() {
        val state = ProtectionPermissionState(
            usageAccessGranted = false,
            overlayGranted = false,
            autostartConfirmed = false,
        )

        assertFalse(shouldBlockProtectionPage(ProtectionPageRisk.USAGE_ACCESS_DISABLE, state))
        assertFalse(shouldBlockProtectionPage(ProtectionPageRisk.OVERLAY_DISABLE, state))
        assertFalse(shouldBlockProtectionPage(ProtectionPageRisk.AUTOSTART_DISABLE, state))
    }

    @Test
    fun `granted critical permissions are protected`() {
        val state = ProtectionPermissionState(true, true, true)

        assertTrue(shouldBlockProtectionPage(ProtectionPageRisk.USAGE_ACCESS_DISABLE, state))
        assertTrue(shouldBlockProtectionPage(ProtectionPageRisk.OVERLAY_DISABLE, state))
        assertTrue(shouldBlockProtectionPage(ProtectionPageRisk.AUTOSTART_DISABLE, state))
        assertTrue(shouldBlockProtectionPage(ProtectionPageRisk.ACCESSIBILITY_DISABLE, state))
        assertTrue(shouldBlockProtectionPage(ProtectionPageRisk.APP_REMOVAL, state))
    }
}
