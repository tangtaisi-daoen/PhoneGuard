package com.familyguard.kid.update

internal fun shouldRunPeriodicUpdateCheck(nowMs: Long, lastAttemptMs: Long, intervalMs: Long): Boolean {
    if (lastAttemptMs <= 0L) return true
    if (nowMs < lastAttemptMs) return true
    return nowMs - lastAttemptMs >= intervalMs
}

internal fun shouldRunUpdateDelivery(
    nowMs: Long,
    lastAttemptMs: Long,
    intervalMs: Long,
    status: UpdateDeliveryStatus,
): Boolean {
    if (status.phase == UpdateDeliveryPhase.AWAITING_USER_CONFIRMATION ||
        status.phase == UpdateDeliveryPhase.READY_TO_INSTALL ||
        status.phase == UpdateDeliveryPhase.INSTALLING
    ) return false
    if (status.phase == UpdateDeliveryPhase.FAILED && UpdateDeliveryPolicy.shouldRetry(status, nowMs)) {
        return true
    }
    return shouldRunPeriodicUpdateCheck(nowMs, lastAttemptMs, intervalMs)
}
