package com.familyguard.core.stats

import com.familyguard.core.data.AnomalyEvent
import com.familyguard.core.data.IncidentStatus

object IncidentLifecycle {
    fun openOrRefresh(
        previous: AnomalyEvent?,
        type: String,
        message: String,
        nowMs: Long,
    ): AnomalyEvent {
        if (previous == null || previous.status == IncidentStatus.RESOLVED || previous.type != type) {
            return AnomalyEvent(
                type = type,
                message = message,
                occurredAt = nowMs,
                dedupKey = type,
                status = IncidentStatus.OPEN,
                firstSeenAt = nowMs,
                lastSeenAt = nowMs,
            )
        }
        return previous.copy(
            message = message,
            occurredAt = nowMs,
            lastSeenAt = nowMs,
            occurrenceCount = previous.occurrenceCount + 1,
            read = false,
        )
    }

    fun acknowledge(incident: AnomalyEvent, nowMs: Long): AnomalyEvent = incident.copy(
        status = if (incident.status == IncidentStatus.RESOLVED) {
            IncidentStatus.RESOLVED
        } else {
            IncidentStatus.ACKNOWLEDGED
        },
        acknowledgedAt = if (incident.status == IncidentStatus.RESOLVED) {
            incident.acknowledgedAt
        } else {
            nowMs
        },
        read = true,
    )

    fun resolve(incident: AnomalyEvent, nowMs: Long): AnomalyEvent = incident.copy(
        status = IncidentStatus.RESOLVED,
        resolvedAt = nowMs,
        read = false,
    )
}
