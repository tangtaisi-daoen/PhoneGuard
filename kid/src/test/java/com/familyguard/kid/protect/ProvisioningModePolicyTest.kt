package com.familyguard.kid.protect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningModePolicyTest {

    @Test
    fun `fully managed mode is selected and system apps are retained`() {
        val fullyManagedMode = 1

        assertEquals(
            fullyManagedMode,
            ProvisioningModePolicy.selectMode(
                allowedModes = listOf(fullyManagedMode),
                fullyManagedMode = fullyManagedMode,
            ),
        )
        assertTrue(ProvisioningModePolicy.leaveAllSystemAppsEnabled)
    }

    @Test
    fun `unsupported provisioning mode is rejected`() {
        assertNull(
            ProvisioningModePolicy.selectMode(
                allowedModes = listOf(2),
                fullyManagedMode = 1,
            ),
        )
    }
}
