package com.familyguard.kid.guard

import android.content.Context

enum class AccessibilityHealth { DISABLED, STARTING, DISCONNECTED, CONNECTED }

internal fun evaluateAccessibilityHealth(
    configured: Boolean,
    connected: Boolean,
    processStartedAt: Long,
    nowMs: Long,
): AccessibilityHealth {
    if (!configured) return AccessibilityHealth.DISABLED
    if (connected) return AccessibilityHealth.CONNECTED
    return if (processStartedAt > 0L && nowMs - processStartedAt <= CONNECTION_GRACE_MS) {
        AccessibilityHealth.STARTING
    } else {
        AccessibilityHealth.DISCONNECTED
    }
}

internal fun foregroundQueryStart(todayStartMs: Long, lastProbeAtMs: Long, nowMs: Long): Long {
    if (lastProbeAtMs <= 0L || lastProbeAtMs > nowMs) return todayStartMs
    return maxOf(todayStartMs, lastProbeAtMs - QUERY_OVERLAP_MS)
}

object AccessibilityHealthStore {
    private const val PREFS = "accessibility_health"
    private const val KEY_PROCESS_STARTED = "process_started_at"
    private const val KEY_CONNECTED = "connected"
    private const val KEY_CONNECTED_AT = "connected_at"
    private const val KEY_EVENT_AT = "last_event_at"
    private const val KEY_PROBE_AT = "last_probe_at"
    private const val KEY_PROBE_SUCCESS_AT = "last_probe_success_at"

    fun markProcessStarted(context: Context, nowMs: Long = System.currentTimeMillis()) {
        preferences(context).edit()
            .putLong(KEY_PROCESS_STARTED, nowMs)
            .putBoolean(KEY_CONNECTED, false)
            .putLong(KEY_PROBE_AT, 0L)
            .putLong(KEY_PROBE_SUCCESS_AT, 0L)
            .apply()
    }

    fun markConnected(context: Context, nowMs: Long = System.currentTimeMillis()) {
        preferences(context).edit()
            .putBoolean(KEY_CONNECTED, true)
            .putLong(KEY_CONNECTED_AT, nowMs)
            .apply()
    }

    fun markDisconnected(context: Context) {
        preferences(context).edit().putBoolean(KEY_CONNECTED, false).apply()
    }

    fun markEvent(context: Context, nowMs: Long = System.currentTimeMillis()) {
        preferences(context).edit().putLong(KEY_EVENT_AT, nowMs).apply()
    }

    fun markProbe(context: Context, success: Boolean, nowMs: Long = System.currentTimeMillis()) {
        preferences(context).edit()
            .putLong(KEY_PROBE_AT, nowMs)
            .apply {
                if (success) putLong(KEY_PROBE_SUCCESS_AT, nowMs)
            }
            .apply()
    }

    fun lastProbeAt(context: Context): Long = preferences(context).getLong(KEY_PROBE_AT, 0L)

    fun snapshot(context: Context): AccessibilityHealthSnapshot {
        val prefs = preferences(context)
        return AccessibilityHealthSnapshot(
            processStartedAt = prefs.getLong(KEY_PROCESS_STARTED, 0L),
            connected = prefs.getBoolean(KEY_CONNECTED, false),
            connectedAt = prefs.getLong(KEY_CONNECTED_AT, 0L),
            lastEventAt = prefs.getLong(KEY_EVENT_AT, 0L),
            lastProbeAt = prefs.getLong(KEY_PROBE_AT, 0L),
            lastProbeSuccessAt = prefs.getLong(KEY_PROBE_SUCCESS_AT, 0L),
        )
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

data class AccessibilityHealthSnapshot(
    val processStartedAt: Long,
    val connected: Boolean,
    val connectedAt: Long,
    val lastEventAt: Long,
    val lastProbeAt: Long,
    val lastProbeSuccessAt: Long,
)

private const val CONNECTION_GRACE_MS = 3 * 60_000L
private const val QUERY_OVERLAP_MS = 2_000L
