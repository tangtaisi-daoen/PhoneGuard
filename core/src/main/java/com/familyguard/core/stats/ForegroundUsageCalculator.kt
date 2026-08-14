package com.familyguard.core.stats

/** UsageEvents 的最小跨平台表示。 */
data class ForegroundUsageEvent(
    val timestampMillis: Long,
    val packageName: String,
    val enteredForeground: Boolean,
)

/** 将前台事件裁剪到指定日期区间，避免跨午夜累计到今天。 */
object ForegroundUsageCalculator {
    fun minutesByPackage(
        events: Iterable<ForegroundUsageEvent>,
        startMillis: Long,
        endMillis: Long,
    ): Map<String, Long> {
        if (endMillis <= startMillis) return emptyMap()
        val millisByPackage = mutableMapOf<String, Long>()
        var activePackage: String? = null
        var activeSince = startMillis

        fun closeActive(atMillis: Long) {
            val pkg = activePackage ?: return
            val end = atMillis.coerceIn(startMillis, endMillis)
            if (end > activeSince) millisByPackage[pkg] = (millisByPackage[pkg] ?: 0L) + (end - activeSince)
            activePackage = null
        }

        events.asSequence()
            .filter { it.timestampMillis <= endMillis }
            .sortedBy { it.timestampMillis }
            .forEach { event ->
                val timestamp = event.timestampMillis.coerceAtLeast(startMillis)
                if (event.enteredForeground) {
                    if (activePackage != null) closeActive(timestamp)
                    activePackage = event.packageName
                    activeSince = timestamp
                } else if (event.packageName == activePackage) {
                    closeActive(timestamp)
                }
            }
        closeActive(endMillis)
        return millisByPackage.mapValues { (_, millis) -> (millis + 59_999L) / 60_000L }
    }
}
