package com.familyguard.core.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudBaseUsageSnapshotTest {
    @Test
    fun `update delivery fields are parsed from heartbeat document`() {
        val snapshot = heartbeatSnapshotFromDocument(
            mapOf(
                "kidDeviceId" to "kid-1",
                "date" to "2026-08-14",
                "updatePhase" to "AWAITING_USER_CONFIRMATION",
                "updateTargetVersionCode" to 13,
                "updateTargetVersionName" to "0.1.12",
                "installedVersionCode" to 12,
                "installedVersionName" to "0.1.11",
                "updateFailureReason" to "",
                "updateStatusAt" to 1_000,
                "notificationPermissionGranted" to true,
                "androidVersion" to "11",
                "deviceModel" to "OPPO PDKM00",
                "batteryPercent" to 76,
                "charging" to true,
                "availableStorageBytes" to 5_000_000_000L,
                "deviceUptimeMs" to 9_000L,
            ),
        )

        assertEquals("AWAITING_USER_CONFIRMATION", snapshot.updatePhase)
        assertEquals(13, snapshot.updateTargetVersionCode)
        assertEquals("0.1.12", snapshot.updateTargetVersionName)
        assertEquals(12, snapshot.installedVersionCode)
        assertEquals("0.1.11", snapshot.installedVersionName)
        assertEquals(1_000, snapshot.updateStatusAt)
        assertEquals(true, snapshot.notificationPermissionGranted)
        assertEquals("11", snapshot.androidVersion)
        assertEquals("OPPO PDKM00", snapshot.deviceModel)
        assertEquals(76, snapshot.batteryPercent)
        assertEquals(true, snapshot.charging)
        assertEquals(5_000_000_000L, snapshot.availableStorageBytes)
        assertEquals(9_000L, snapshot.deviceUptimeMs)
    }
}
