package com.familyguard.core.stats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionReconcileGateTest {

    @Test
    fun `first observation reconciles immediately`() {
        assertTrue(
            shouldReconcileConditions(
                previousConditions = null,
                currentConditions = emptyMap(),
                lastReconciledAt = 0L,
                nowMs = 1_000L,
                refreshIntervalMs = 300_000L,
            ),
        )
    }

    @Test
    fun `condition change reconciles before refresh interval`() {
        assertTrue(
            shouldReconcileConditions(
                previousConditions = emptyMap(),
                currentConditions = mapOf("ACCESSIBILITY_DISABLED" to "disabled"),
                lastReconciledAt = 1_000L,
                nowMs = 2_000L,
                refreshIntervalMs = 300_000L,
            ),
        )
    }

    @Test
    fun `unchanged conditions wait until refresh interval`() {
        val conditions = mapOf("ACCESSIBILITY_DISABLED" to "disabled")

        assertFalse(
            shouldReconcileConditions(
                previousConditions = conditions,
                currentConditions = conditions,
                lastReconciledAt = 1_000L,
                nowMs = 300_999L,
                refreshIntervalMs = 300_000L,
            ),
        )
        assertTrue(
            shouldReconcileConditions(
                previousConditions = conditions,
                currentConditions = conditions,
                lastReconciledAt = 1_000L,
                nowMs = 301_000L,
                refreshIntervalMs = 300_000L,
            ),
        )
    }
}
