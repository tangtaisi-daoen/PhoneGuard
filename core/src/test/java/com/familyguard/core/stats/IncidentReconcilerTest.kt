package com.familyguard.core.stats

import com.familyguard.core.data.IncidentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentReconcilerTest {
    @Test
    fun `new active condition opens one incident`() {
        val result = IncidentReconciler.plan(
            existing = emptyList(),
            activeConditions = mapOf("PERMISSION_DISABLED" to "权限已关闭"),
            managedTypes = setOf("PERMISSION_DISABLED"),
            nowMs = 1_000,
            refreshIntervalMs = 300_000,
        )

        assertEquals(1, result.upserts.size)
        assertEquals(IncidentStatus.OPEN, result.upserts.single().status)
        assertTrue(result.resolved.isEmpty())
    }

    @Test
    fun `unchanged condition inside refresh interval makes no write`() {
        val open = IncidentLifecycle.openOrRefresh(null, "PERMISSION_DISABLED", "权限已关闭", 1_000)

        val result = IncidentReconciler.plan(
            existing = listOf(open),
            activeConditions = mapOf("PERMISSION_DISABLED" to "权限已关闭"),
            managedTypes = setOf("PERMISSION_DISABLED"),
            nowMs = 2_000,
            refreshIntervalMs = 300_000,
        )

        assertTrue(result.upserts.isEmpty())
        assertTrue(result.resolved.isEmpty())
    }

    @Test
    fun `recovered condition resolves existing incident`() {
        val open = IncidentLifecycle.openOrRefresh(null, "ACCESSIBILITY_DISABLED", "已关闭", 1_000)

        val result = IncidentReconciler.plan(
            existing = listOf(open),
            activeConditions = emptyMap(),
            managedTypes = setOf("ACCESSIBILITY_DISABLED"),
            nowMs = 4_000,
            refreshIntervalMs = 300_000,
        )

        assertEquals(IncidentStatus.RESOLVED, result.resolved.single().status)
        assertEquals(4_000, result.resolved.single().resolvedAt)
    }
}
