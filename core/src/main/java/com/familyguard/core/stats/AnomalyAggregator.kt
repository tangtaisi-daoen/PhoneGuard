package com.familyguard.core.stats

import com.familyguard.core.data.AnomalyEvent
import com.familyguard.core.data.IncidentStatus

enum class AnomalySeverity { HIGH, MEDIUM, LOW }

data class AnomalyGroup(
    val type: String,
    val message: String,
    val occurredAt: Long,
    val count: Int,
    val severity: AnomalySeverity,
    val suggestion: String,
    val status: IncidentStatus,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val resolvedAt: Long,
)

/** 将短时间内重复上报的同类事件合并，避免心跳造成异常列表刷屏。 */
object AnomalyAggregator {
    const val DUPLICATE_WINDOW_MS = 30 * 60_000L

    fun group(events: List<AnomalyEvent>): List<AnomalyGroup> {
        val groups = mutableListOf<MutableGroup>()
        events.sortedByDescending { it.occurredAt }.forEach { event ->
            val existing = groups.firstOrNull {
                it.type == event.type && it.status == event.status &&
                    it.lastSeenAt - event.lastSeenAt <= DUPLICATE_WINDOW_MS
            }
            if (existing == null) {
                groups += MutableGroup(event)
            } else {
                existing.count += event.occurrenceCount
                existing.firstSeenAt = minOf(existing.firstSeenAt, event.firstSeenAt)
                existing.lastSeenAt = maxOf(existing.lastSeenAt, event.lastSeenAt)
                existing.resolvedAt = maxOf(existing.resolvedAt, event.resolvedAt)
            }
        }
        return groups.map { it.toGroup() }
    }

    fun severity(type: String): AnomalySeverity = when (type) {
        "ADMIN_DISABLED", "PERMISSION_DISABLED", "ACCESSIBILITY_DISABLED",
        "ACCESSIBILITY_DISCONNECTED", "DEVICE_OWNER_MISSING", "UNINSTALL_PROTECTION_MISSING",
        "TIME_CHANGED", "UNINSTALL_ATTEMPT", "ADMIN_DISABLE_ATTEMPT",
        "ACCESSIBILITY_DISABLE_ATTEMPT", "OVERLAY_PERMISSION_DISABLED",
        "USAGE_ACCESS_DISABLE_ATTEMPT", "OVERLAY_DISABLE_ATTEMPT",
        "AUTOSTART_DISABLE_ATTEMPT" -> AnomalySeverity.HIGH
        "OFFLINE", "UPDATE_CHECK_FAILED", "RULE_NOT_APPLIED", "BATTERY_OPTIMIZATION_ENABLED",
        "AUTOSTART_NOT_CONFIRMED", "NOTIFICATION_PERMISSION_DISABLED", "LOW_STORAGE" -> AnomalySeverity.MEDIUM
        else -> AnomalySeverity.LOW
    }

    fun suggestion(type: String): String = when (type) {
        "DEVICE_OWNER_MISSING" -> "当前设备使用兼容防护模式；请保持设备管理员、无障碍和关键权限开启"
        "UNINSTALL_PROTECTION_MISSING" -> "设备所有者已存在，但防卸载基线未生效；请检查设备策略并重新应用"
        "ADMIN_DISABLE_ATTEMPT", "ACCESSIBILITY_DISABLE_ATTEMPT", "UNINSTALL_ATTEMPT" ->
            "请确认该操作是否经过监护人授权，并检查被控端防护权限仍然有效"
        "USAGE_ACCESS_DISABLE_ATTEMPT", "OVERLAY_DISABLE_ATTEMPT", "AUTOSTART_DISABLE_ATTEMPT" ->
            "检测到关键权限关闭操作，请确认被控端对应权限仍处于开启状态"
        "ACCESSIBILITY_DISABLED" -> "请在被控端重新开启手机守护无障碍服务"
        "ACCESSIBILITY_DISCONNECTED" -> "请检查 OPPO 后台活动、自启动和最近任务锁定，并重启手机守护"
        "UPDATE_AVAILABLE" -> "请在被控端点击更新通知，完成系统安装确认"
        "UPDATE_CHECK_FAILED" -> "请检查被控端网络；系统会在稍后自动重试"
        "RULE_NOT_APPLIED" -> "请检查被控端网络和守护服务；恢复同步后该事故会自动关闭"
        "ADMIN_DISABLED" -> "请在被控端重新启用设备管理器，恢复防卸载保护"
        "PERMISSION_DISABLED" -> "请打开手机守护，引导页中重新授予相关权限"
        "OVERLAY_PERMISSION_DISABLED" -> "请重新允许手机守护显示悬浮窗，否则限时拦截层可能失效"
        "BATTERY_OPTIMIZATION_ENABLED" -> "请重新允许手机守护忽略电池优化，降低 ColorOS 杀后台概率"
        "AUTOSTART_NOT_CONFIRMED" -> "请开启允许自启动，并在手机守护引导页确认已开启"
        "NOTIFICATION_PERMISSION_DISABLED" -> "请在被控端重新允许手机守护通知，以接收守护和更新提醒"
        "LOW_STORAGE" -> "请清理被控端存储空间，至少保留 500 MB 供远程更新使用"
        "TIME_CHANGED" -> "请检查系统日期和时间，建议开启自动设置时间"
        "UNINSTALL_ATTEMPT" -> "确认设备管理器仍处于启用状态，并检查是否有人尝试卸载"
        "OFFLINE" -> "检查被控端网络、电量优化和后台自启动设置"
        "NEW_APP" -> "确认该应用是否适合使用，必要时回到规则页添加限时"
        else -> "打开被控端守护页查看详细状态"
    }

    private class MutableGroup(event: AnomalyEvent) {
        val type = event.type
        val message = event.message
        val occurredAt = event.lastSeenAt
        val status = event.status
        var firstSeenAt = event.firstSeenAt
        var lastSeenAt = event.lastSeenAt
        var resolvedAt = event.resolvedAt
        var count = event.occurrenceCount

        fun toGroup() = AnomalyGroup(
            type = type,
            message = message,
            occurredAt = occurredAt,
            count = count,
            severity = severity(type),
            suggestion = suggestion(type),
            status = status,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            resolvedAt = resolvedAt,
        )
    }
}
