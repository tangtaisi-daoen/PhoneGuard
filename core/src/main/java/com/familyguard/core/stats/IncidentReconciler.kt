package com.familyguard.core.stats

import com.familyguard.core.data.AnomalyEvent
import com.familyguard.core.data.IncidentStatus

data class IncidentReconciliation(
    val upserts: List<AnomalyEvent>,
    val resolved: List<AnomalyEvent>,
)

object IncidentReconciler {
    fun plan(
        existing: List<AnomalyEvent>,
        activeConditions: Map<String, String>,
        managedTypes: Set<String>,
        nowMs: Long,
        refreshIntervalMs: Long,
    ): IncidentReconciliation {
        val activeExisting = existing
            .filter { it.status != IncidentStatus.RESOLVED }
            .groupBy(AnomalyEvent::type)
            .mapValues { (_, incidents) -> incidents.maxBy { it.lastSeenAt } }
        val upserts = activeConditions.mapNotNull { (type, message) ->
            val previous = activeExisting[type]
            if (previous != null && nowMs >= previous.lastSeenAt &&
                nowMs - previous.lastSeenAt < refreshIntervalMs
            ) {
                null
            } else {
                IncidentLifecycle.openOrRefresh(previous, type, message, nowMs)
            }
        }
        val resolved = existing
            .filter {
                it.status != IncidentStatus.RESOLVED &&
                    it.type in managedTypes &&
                    it.type !in activeConditions
            }
            .map { IncidentLifecycle.resolve(it, nowMs) }
        return IncidentReconciliation(upserts, resolved)
    }
}
