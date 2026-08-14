package com.familyguard.kid.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDeliveryPolicyTest {
    @Test
    fun `verified update waits for system confirmation in compatibility mode`() {
        val status = UpdateDeliveryPolicy.readyForInstall(
            versionCode = 13,
            versionName = "0.1.12",
            requiresUserConfirmation = true,
            nowMs = 1_000,
        )

        assertEquals(UpdateDeliveryPhase.AWAITING_USER_CONFIRMATION, status.phase)
        assertEquals(13, status.targetVersionCode)
        assertEquals("0.1.12", status.targetVersionName)
    }

    @Test
    fun `successful package replacement closes pending update`() {
        val pending = UpdateDeliveryPolicy.readyForInstall(13, "0.1.12", true, 1_000)

        val completed = UpdateDeliveryPolicy.afterPackageReplaced(
            pending,
            installedVersionCode = 13,
            installedVersionName = "0.1.12",
            nowMs = 2_000,
        )

        assertEquals(UpdateDeliveryPhase.SUCCEEDED, completed.phase)
        assertEquals(13, completed.installedVersionCode)
    }

    @Test
    fun `failed delivery uses bounded exponential retry`() {
        val first = UpdateDeliveryPolicy.failed(UpdateDeliveryStatus(), "network", 1_000)
        val second = UpdateDeliveryPolicy.failed(first, "network", 2_000)

        assertFalse(UpdateDeliveryPolicy.shouldRetry(second, 2_000 + 29 * 60_000L))
        assertTrue(UpdateDeliveryPolicy.shouldRetry(second, 2_000 + 30 * 60_000L))
    }

    @Test
    fun `delivery status survives persistence round trip`() {
        val original = UpdateDeliveryStatus(
            phase = UpdateDeliveryPhase.AWAITING_USER_CONFIRMATION,
            targetVersionCode = 13,
            targetVersionName = "0.1.12",
            installedVersionCode = 12,
            installedVersionName = "0.1.11",
            updatedAt = 9_000,
            failureReason = "",
            attemptCount = 2,
        )

        assertEquals(original, UpdateDeliveryStatusCodec.decode(UpdateDeliveryStatusCodec.encode(original)))
    }
}
