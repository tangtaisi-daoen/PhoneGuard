package com.familyguard.core.stats

/**
 * Avoids querying the incident collection on every heartbeat while still
 * reconciling immediately when a monitored condition appears or recovers.
 */
fun shouldReconcileConditions(
    previousConditions: Map<String, String>?,
    currentConditions: Map<String, String>,
    lastReconciledAt: Long,
    nowMs: Long,
    refreshIntervalMs: Long,
): Boolean = previousConditions == null ||
    previousConditions != currentConditions ||
    nowMs - lastReconciledAt >= refreshIntervalMs
