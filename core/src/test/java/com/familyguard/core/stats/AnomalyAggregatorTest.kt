package com.familyguard.core.stats

import com.familyguard.core.data.AnomalyEvent
import com.familyguard.core.data.IncidentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class AnomalyAggregatorTest {
    @Test
    fun `same type within thirty minutes is grouped`() {
        val events = listOf(
            AnomalyEvent("PERMISSION_DISABLED", "权限关闭", 1_800_000L),
            AnomalyEvent("PERMISSION_DISABLED", "权限关闭", 1_200_000L),
            AnomalyEvent("NEW_APP", "安装了应用", 1_700_000L),
        )

        val groups = AnomalyAggregator.group(events)

        assertEquals(2, groups.size)
        assertEquals(2, groups.first { it.type == "PERMISSION_DISABLED" }.count)
        assertEquals(AnomalySeverity.HIGH, groups.first { it.type == "PERMISSION_DISABLED" }.severity)
    }

    @Test
    fun `same type outside window remains separate`() {
        val groups = AnomalyAggregator.group(
            listOf(
                AnomalyEvent("NEW_APP", "a", 3_600_000L),
                AnomalyEvent("NEW_APP", "b", 0L),
            ),
        )

        assertEquals(2, groups.size)
    }

    @Test
    fun `update events have actionable severity and suggestions`() {
        assertEquals(AnomalySeverity.LOW, AnomalyAggregator.severity("UPDATE_AVAILABLE"))
        assertEquals(AnomalySeverity.MEDIUM, AnomalyAggregator.severity("UPDATE_CHECK_FAILED"))
        assertEquals(
            "请在被控端点击更新通知，完成系统安装确认",
            AnomalyAggregator.suggestion("UPDATE_AVAILABLE"),
        )
    }

    @Test
    fun `accessibility disabled and disconnected are distinct high severity events`() {
        assertEquals(AnomalySeverity.HIGH, AnomalyAggregator.severity("ACCESSIBILITY_DISABLED"))
        assertEquals(AnomalySeverity.HIGH, AnomalyAggregator.severity("ACCESSIBILITY_DISCONNECTED"))
        assertEquals(
            "请在被控端重新开启手机守护无障碍服务",
            AnomalyAggregator.suggestion("ACCESSIBILITY_DISABLED"),
        )
    }

    @Test
    fun `missing device owner is a high severity protection gap`() {
        assertEquals(AnomalySeverity.HIGH, AnomalyAggregator.severity("DEVICE_OWNER_MISSING"))
        assertEquals(
            "当前设备使用兼容防护模式；请保持设备管理员、无障碍和关键权限开启",
            AnomalyAggregator.suggestion("DEVICE_OWNER_MISSING"),
        )
    }

    @Test
    fun `critical permission and protection attempts have actionable severity`() {
        assertEquals(AnomalySeverity.HIGH, AnomalyAggregator.severity("OVERLAY_PERMISSION_DISABLED"))
        assertEquals(AnomalySeverity.HIGH, AnomalyAggregator.severity("USAGE_ACCESS_DISABLE_ATTEMPT"))
        assertEquals(AnomalySeverity.HIGH, AnomalyAggregator.severity("OVERLAY_DISABLE_ATTEMPT"))
        assertEquals(AnomalySeverity.HIGH, AnomalyAggregator.severity("AUTOSTART_DISABLE_ATTEMPT"))
        assertEquals(AnomalySeverity.MEDIUM, AnomalyAggregator.severity("BATTERY_OPTIMIZATION_ENABLED"))
        assertEquals(AnomalySeverity.MEDIUM, AnomalyAggregator.severity("AUTOSTART_NOT_CONFIRMED"))
    }

    @Test
    fun `stored incident occurrence count and status are preserved`() {
        val groups = AnomalyAggregator.group(
            listOf(
                AnomalyEvent(
                    type = "ACCESSIBILITY_DISABLED",
                    message = "仍未恢复",
                    occurredAt = 2_000,
                    status = IncidentStatus.ACKNOWLEDGED,
                    firstSeenAt = 1_000,
                    lastSeenAt = 2_000,
                    occurrenceCount = 4,
                ),
            ),
        )

        assertEquals(4, groups.single().count)
        assertEquals(IncidentStatus.ACKNOWLEDGED, groups.single().status)
        assertEquals(1_000, groups.single().firstSeenAt)
    }
}
