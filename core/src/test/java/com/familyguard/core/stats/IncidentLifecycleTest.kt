package com.familyguard.core.stats

import com.familyguard.core.data.IncidentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class IncidentLifecycleTest {
    @Test
    fun `repeated condition refreshes one open incident`() {
        val opened = IncidentLifecycle.openOrRefresh(null, "PERMISSION_DISABLED", "权限关闭", 1_000)
        val refreshed = IncidentLifecycle.openOrRefresh(opened, "PERMISSION_DISABLED", "仍未恢复", 2_000)

        assertEquals(IncidentStatus.OPEN, refreshed.status)
        assertEquals(1_000, refreshed.firstSeenAt)
        assertEquals(2_000, refreshed.lastSeenAt)
        assertEquals(2, refreshed.occurrenceCount)
    }

    @Test
    fun `acknowledged incident stays acknowledged when condition repeats`() {
        val opened = IncidentLifecycle.openOrRefresh(null, "ACCESSIBILITY_DISABLED", "已关闭", 1_000)
        val acknowledged = IncidentLifecycle.acknowledge(opened, 1_500)

        val refreshed = IncidentLifecycle.openOrRefresh(
            acknowledged,
            "ACCESSIBILITY_DISABLED",
            "仍关闭",
            2_000,
        )

        assertEquals(IncidentStatus.ACKNOWLEDGED, refreshed.status)
        assertEquals(2, refreshed.occurrenceCount)
    }

    @Test
    fun `recovered condition resolves incident with timestamp`() {
        val opened = IncidentLifecycle.openOrRefresh(null, "ADMIN_DISABLED", "已停用", 1_000)

        val resolved = IncidentLifecycle.resolve(opened, 3_000)

        assertEquals(IncidentStatus.RESOLVED, resolved.status)
        assertEquals(3_000, resolved.resolvedAt)
        assertEquals(1_000, resolved.lastSeenAt)
    }
}
