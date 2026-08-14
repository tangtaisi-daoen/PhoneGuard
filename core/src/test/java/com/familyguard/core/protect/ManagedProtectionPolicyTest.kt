package com.familyguard.core.protect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedProtectionPolicyTest {

    @Test
    fun `fully managed enables privileged protection capabilities`() {
        val policy = ManagedProtectionPolicy.forMode(DeviceManagementMode.FULLY_MANAGED)

        assertTrue(policy.blockSelfUninstall)
        assertTrue(policy.silentSelfUpdate)
        assertTrue(policy.systemPackageSuspension)
        assertTrue(policy.disallowUnknownSources)
    }

    @Test
    fun `compatibility mode never claims device owner capabilities`() {
        val policy = ManagedProtectionPolicy.forMode(DeviceManagementMode.COMPATIBILITY)

        assertFalse(policy.blockSelfUninstall)
        assertFalse(policy.silentSelfUpdate)
        assertFalse(policy.systemPackageSuspension)
        assertFalse(policy.disallowUnknownSources)
    }
}
