package com.familyguard.kid.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionReturnNavigatorTest {
    @Test
    fun `protected settings are dismissed and guard returns only to launcher`() {
        val actions = mutableListOf<String>()
        var launcherDelayMs = 0L
        val navigator = ProtectionReturnNavigator(
            dismissProtectedSurface = { actions += "dismiss-protected-surface" },
            scheduleLauncher = {
                actions += "launcher"
                launcherDelayMs = it
            },
        )

        navigator.returnToLauncher()

        assertEquals(
            listOf("dismiss-protected-surface", "launcher"),
            actions,
        )
        assertTrue(launcherDelayMs > 0L)
    }
}
