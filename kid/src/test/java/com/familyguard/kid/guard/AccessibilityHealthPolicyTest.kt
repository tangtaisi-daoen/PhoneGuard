package com.familyguard.kid.guard

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityHealthPolicyTest {

    @Test
    fun `disabled setting is reported separately`() {
        assertEquals(
            AccessibilityHealth.DISABLED,
            evaluateAccessibilityHealth(configured = false, connected = false, processStartedAt = 1, nowMs = 10_000),
        )
    }

    @Test
    fun `configured service gets a startup grace period`() {
        assertEquals(
            AccessibilityHealth.STARTING,
            evaluateAccessibilityHealth(configured = true, connected = false, processStartedAt = 1_000, nowMs = 60_000),
        )
    }

    @Test
    fun `configured but disconnected after grace is unhealthy`() {
        assertEquals(
            AccessibilityHealth.DISCONNECTED,
            evaluateAccessibilityHealth(configured = true, connected = false, processStartedAt = 1_000, nowMs = 181_001),
        )
    }

    @Test
    fun `connected service is healthy`() {
        assertEquals(
            AccessibilityHealth.CONNECTED,
            evaluateAccessibilityHealth(configured = true, connected = true, processStartedAt = 1_000, nowMs = 999_999),
        )
    }

    @Test
    fun `incremental query overlaps cursor without scanning whole day`() {
        assertEquals(98_000, foregroundQueryStart(todayStartMs = 0, lastProbeAtMs = 100_000, nowMs = 105_000))
        assertEquals(0, foregroundQueryStart(todayStartMs = 0, lastProbeAtMs = 0, nowMs = 105_000))
    }
}
