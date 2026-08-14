package com.familyguard.kid.update

enum class UpdateDeliveryPhase {
    IDLE,
    CHECKING,
    DOWNLOADING,
    READY_TO_INSTALL,
    AWAITING_USER_CONFIRMATION,
    INSTALLING,
    SUCCEEDED,
    FAILED,
}

data class UpdateDeliveryStatus(
    val phase: UpdateDeliveryPhase = UpdateDeliveryPhase.IDLE,
    val targetVersionCode: Long = 0L,
    val targetVersionName: String = "",
    val installedVersionCode: Long = 0L,
    val installedVersionName: String = "",
    val updatedAt: Long = 0L,
    val failureReason: String = "",
    val attemptCount: Int = 0,
)

object UpdateDeliveryPolicy {
    fun readyForInstall(
        versionCode: Long,
        versionName: String,
        requiresUserConfirmation: Boolean,
        nowMs: Long,
    ) = UpdateDeliveryStatus(
        phase = if (requiresUserConfirmation) {
            UpdateDeliveryPhase.AWAITING_USER_CONFIRMATION
        } else {
            UpdateDeliveryPhase.READY_TO_INSTALL
        },
        targetVersionCode = versionCode,
        targetVersionName = versionName,
        updatedAt = nowMs,
    )

    fun afterPackageReplaced(
        previous: UpdateDeliveryStatus,
        installedVersionCode: Long,
        installedVersionName: String,
        nowMs: Long,
    ): UpdateDeliveryStatus = previous.copy(
        phase = if (
            previous.targetVersionCode > 0L && installedVersionCode >= previous.targetVersionCode
        ) {
            UpdateDeliveryPhase.SUCCEEDED
        } else {
            previous.phase
        },
        installedVersionCode = installedVersionCode,
        installedVersionName = installedVersionName,
        updatedAt = nowMs,
        failureReason = "",
    )

    fun failed(previous: UpdateDeliveryStatus, reason: String, nowMs: Long): UpdateDeliveryStatus =
        previous.copy(
            phase = UpdateDeliveryPhase.FAILED,
            updatedAt = nowMs,
            failureReason = reason,
            attemptCount = previous.attemptCount + 1,
        )

    fun shouldRetry(status: UpdateDeliveryStatus, nowMs: Long): Boolean {
        if (status.phase != UpdateDeliveryPhase.FAILED) return false
        if (nowMs < status.updatedAt) return true
        val exponent = (status.attemptCount - 1).coerceIn(0, 10)
        val delayMinutes = (BASE_RETRY_MINUTES * (1L shl exponent)).coerceAtMost(MAX_RETRY_MINUTES)
        return nowMs - status.updatedAt >= delayMinutes * 60_000L
    }

    private const val BASE_RETRY_MINUTES = 15L
    private const val MAX_RETRY_MINUTES = 360L
}

internal object UpdateDeliveryStatusCodec {
    fun encode(status: UpdateDeliveryStatus): Map<String, String> = mapOf(
        "phase" to status.phase.name,
        "targetVersionCode" to status.targetVersionCode.toString(),
        "targetVersionName" to status.targetVersionName,
        "installedVersionCode" to status.installedVersionCode.toString(),
        "installedVersionName" to status.installedVersionName,
        "updatedAt" to status.updatedAt.toString(),
        "failureReason" to status.failureReason,
        "attemptCount" to status.attemptCount.toString(),
    )

    fun decode(values: Map<String, String>): UpdateDeliveryStatus = UpdateDeliveryStatus(
        phase = runCatching {
            UpdateDeliveryPhase.valueOf(values["phase"].orEmpty())
        }.getOrDefault(UpdateDeliveryPhase.IDLE),
        targetVersionCode = values["targetVersionCode"]?.toLongOrNull() ?: 0L,
        targetVersionName = values["targetVersionName"].orEmpty(),
        installedVersionCode = values["installedVersionCode"]?.toLongOrNull() ?: 0L,
        installedVersionName = values["installedVersionName"].orEmpty(),
        updatedAt = values["updatedAt"]?.toLongOrNull() ?: 0L,
        failureReason = values["failureReason"].orEmpty(),
        attemptCount = values["attemptCount"]?.toIntOrNull() ?: 0,
    )
}
