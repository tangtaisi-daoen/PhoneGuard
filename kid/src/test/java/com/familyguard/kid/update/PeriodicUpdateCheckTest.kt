package com.familyguard.kid.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicUpdateCheckTest {

    @Test
    fun `first background check runs immediately`() {
        assertTrue(shouldRunPeriodicUpdateCheck(nowMs = 100L, lastAttemptMs = 0L, intervalMs = 60L))
    }

    @Test
    fun `check is throttled inside interval`() {
        assertFalse(shouldRunPeriodicUpdateCheck(nowMs = 150L, lastAttemptMs = 100L, intervalMs = 60L))
    }

    @Test
    fun `check runs when interval elapsed`() {
        assertTrue(shouldRunPeriodicUpdateCheck(nowMs = 160L, lastAttemptMs = 100L, intervalMs = 60L))
    }

    @Test
    fun `clock rollback does not suppress checks forever`() {
        assertTrue(shouldRunPeriodicUpdateCheck(nowMs = 50L, lastAttemptMs = 100L, intervalMs = 60L))
    }

    @Test
    fun `failed update retries when backoff expires even inside periodic interval`() {
        val failed = UpdateDeliveryPolicy.failed(UpdateDeliveryStatus(), "network", 1_000L)

        assertTrue(
            shouldRunUpdateDelivery(
                nowMs = 1_000L + 15 * 60_000L,
                lastAttemptMs = 1_000L,
                intervalMs = 6 * 60 * 60_000L,
                status = failed,
            ),
        )
    }

    @Test
    fun `verified update is not downloaded again while waiting for confirmation`() {
        val ready = UpdateDeliveryPolicy.readyForInstall(13, "0.1.12", true, 1_000L)

        assertFalse(
            shouldRunUpdateDelivery(
                nowMs = 10_000_000L,
                lastAttemptMs = 0L,
                intervalMs = 1L,
                status = ready,
            ),
        )
    }
}
