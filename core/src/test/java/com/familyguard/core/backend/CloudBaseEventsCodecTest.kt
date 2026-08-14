package com.familyguard.core.backend

import com.familyguard.core.data.AnomalyEvent
import com.familyguard.core.data.IncidentStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudBaseEventsCodecTest {
    @Test
    fun `incident lifecycle fields survive cloud document round trip`() {
        val incident = AnomalyEvent(
            type = "ACCESSIBILITY_DISABLED",
            message = "无障碍已关闭",
            occurredAt = 2_000,
            id = "event-1",
            dedupKey = "ACCESSIBILITY_DISABLED",
            status = IncidentStatus.ACKNOWLEDGED,
            firstSeenAt = 1_000,
            lastSeenAt = 2_000,
            acknowledgedAt = 1_500,
            occurrenceCount = 3,
            read = true,
        )

        val document = incidentDocument("kid-1", incident).toMutableMap().apply { put("_id", "event-1") }
        val decoded = incidentFromDocument(document)

        assertEquals(incident, decoded)
    }

    @Test
    fun `legacy event document is read as open incident`() {
        val decoded = incidentFromDocument(
            mapOf(
                "_id" to "legacy-1",
                "type" to "NEW_APP",
                "message" to "安装了应用",
                "occurredAt" to 3_000,
            ),
        )

        assertEquals(IncidentStatus.OPEN, decoded?.status)
        assertEquals(3_000L, decoded?.firstSeenAt)
        assertEquals(1, decoded?.occurrenceCount)
    }
}
