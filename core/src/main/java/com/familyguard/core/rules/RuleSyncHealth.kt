package com.familyguard.core.rules

enum class RuleSyncHealth { SYNCED, PENDING, STALE }

fun evaluateRuleSync(
    expectedRevision: Long,
    appliedRevision: Long,
    generatedAt: Long,
    nowMs: Long,
    graceMs: Long,
): RuleSyncHealth {
    if (expectedRevision <= 0L || appliedRevision >= expectedRevision) return RuleSyncHealth.SYNCED
    if (generatedAt <= 0L) return RuleSyncHealth.STALE
    if (nowMs < generatedAt || nowMs - generatedAt < graceMs) return RuleSyncHealth.PENDING
    return RuleSyncHealth.STALE
}
