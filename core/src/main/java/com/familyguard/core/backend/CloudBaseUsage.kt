package com.familyguard.core.backend

import com.google.gson.JsonObject

/**
 * 使用量心跳上报（usage 集合）。
 *
 * 文档结构（kidDeviceId + date 唯一，覆盖更新）：
 *   kidDeviceId: String（被控端匿名 uid）
 *   date: String（yyyy-MM-dd）
 *   byPackage: {pkg: minutes}（当日各 app 分钟数）
 *   totalMinutes: Long（当日总额）
 *   currentApp: String（当前前台 app，可能为空）
 *   reportedAt: Long（心跳时间）
 */
object CloudBaseUsage {

    private const val COLLECTION = "usage"

    /** 上报/覆盖当日心跳快照。 */
    suspend fun upsertHeartbeat(
        client: CloudBaseClient,
        kidDeviceId: String,
        date: String,
        byPackage: Map<String, Long>,
        totalMinutes: Long,
        currentApp: String?,
        appliedRuleRevision: Long = 0L,
        evaluatedLocalDate: String = "",
        evaluatedProfile: String = "",
        timezoneId: String = "",
        accessibilityConfigured: Boolean = false,
        accessibilityConnected: Boolean = false,
        lastAccessibilityEventAt: Long = 0L,
        lastForegroundProbeSuccessAt: Long = 0L,
        deviceManagementMode: String = "COMPATIBILITY",
        selfUninstallBlocked: Boolean = false,
        permissionHealthReported: Boolean = false,
        usageAccessGranted: Boolean = false,
        overlayGranted: Boolean = false,
        batteryOptimizationIgnored: Boolean = false,
        autostartConfirmed: Boolean = false,
        notificationPermissionGranted: Boolean = false,
        updatePhase: String = "IDLE",
        updateTargetVersionCode: Long = 0L,
        updateTargetVersionName: String = "",
        installedVersionCode: Long = 0L,
        installedVersionName: String = "",
        updateFailureReason: String = "",
        updateStatusAt: Long = 0L,
        androidVersion: String = "",
        deviceModel: String = "",
        batteryPercent: Int = -1,
        charging: Boolean = false,
        availableStorageBytes: Long = 0L,
        deviceUptimeMs: Long = 0L,
    ): Boolean {
        val existing = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("kidDeviceId" to kidDeviceId, "date" to date), limit = 1,
        ) ?: return false
        val data = mapOf(
            "byPackage" to byPackage,
            "totalMinutes" to totalMinutes,
            "currentApp" to (currentApp ?: ""),
            "reportedAt" to System.currentTimeMillis(),
            "appliedRuleRevision" to appliedRuleRevision,
            "evaluatedLocalDate" to evaluatedLocalDate,
            "evaluatedProfile" to evaluatedProfile,
            "timezoneId" to timezoneId,
            "accessibilityConfigured" to accessibilityConfigured,
            "accessibilityConnected" to accessibilityConnected,
            "lastAccessibilityEventAt" to lastAccessibilityEventAt,
            "lastForegroundProbeSuccessAt" to lastForegroundProbeSuccessAt,
            "deviceManagementMode" to deviceManagementMode,
            "selfUninstallBlocked" to selfUninstallBlocked,
            "permissionHealthReported" to permissionHealthReported,
            "usageAccessGranted" to usageAccessGranted,
            "overlayGranted" to overlayGranted,
            "batteryOptimizationIgnored" to batteryOptimizationIgnored,
            "autostartConfirmed" to autostartConfirmed,
            "notificationPermissionGranted" to notificationPermissionGranted,
            "updatePhase" to updatePhase,
            "updateTargetVersionCode" to updateTargetVersionCode,
            "updateTargetVersionName" to updateTargetVersionName,
            "installedVersionCode" to installedVersionCode,
            "installedVersionName" to installedVersionName,
            "updateFailureReason" to updateFailureReason,
            "updateStatusAt" to updateStatusAt,
            "androidVersion" to androidVersion,
            "deviceModel" to deviceModel,
            "batteryPercent" to batteryPercent,
            "charging" to charging,
            "availableStorageBytes" to availableStorageBytes,
            "deviceUptimeMs" to deviceUptimeMs,
        )
        return if (existing.isEmpty()) {
            val doc = mutableMapOf<String, Any?>("kidDeviceId" to kidDeviceId, "date" to date)
            doc.putAll(data)
            val inserted = CloudBaseDb.insertDocuments(client, COLLECTION, listOf(doc))
            inserted != null && inserted.isNotEmpty()
        } else {
            val docId = existing.first()["_id"]?.toString() ?: return false
            val updated = CloudBaseDb.updateDocuments(
                client, COLLECTION,
                where = mapOf("_id" to docId),
                data = data,
            )
            updated != null && updated > 0
        }
    }

    /** 拉取被控端最近一条心跳快照。 */
    suspend fun fetchLatest(client: CloudBaseClient, kidDeviceId: String): HeartbeatSnapshot? {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("kidDeviceId" to kidDeviceId), limit = 100,
        ) ?: return null
        return docs.map(::heartbeatSnapshotFromDocument).maxByOrNull { it.reportedAt }
    }

    /** 拉取最近心跳快照，用于管理端生成最多 7 天的本地趋势。 */
    suspend fun fetchRecent(
        client: CloudBaseClient,
        kidDeviceId: String,
        limit: Int = 100,
    ): List<HeartbeatSnapshot>? {
        val docs = CloudBaseDb.queryDocuments(
            client, COLLECTION, where = mapOf("kidDeviceId" to kidDeviceId), limit = limit.coerceIn(1, 100),
        ) ?: return null
        return docs.map(::heartbeatSnapshotFromDocument).sortedByDescending { it.reportedAt }
    }

}

internal fun heartbeatSnapshotFromDocument(doc: Map<String, Any?>): HeartbeatSnapshot {
        val byPackage = (doc["byPackage"] as? JsonObject)?.let { obj ->
            obj.entrySet().associate { (k, v) -> k to (v.asLong) }
        } ?: emptyMap()
        return HeartbeatSnapshot(
            kidDeviceId = doc["kidDeviceId"]?.toString() ?: "",
            date = doc["date"]?.toString() ?: "",
            byPackage = byPackage,
            totalMinutes = doc["totalMinutes"]?.toString()?.toLongOrNull() ?: 0L,
            currentApp = doc["currentApp"]?.toString()?.takeIf { it.isNotBlank() },
            reportedAt = doc["reportedAt"]?.toString()?.toLongOrNull() ?: 0L,
            appliedRuleRevision = doc["appliedRuleRevision"]?.toString()?.toLongOrNull() ?: 0L,
            evaluatedLocalDate = doc["evaluatedLocalDate"]?.toString().orEmpty(),
            evaluatedProfile = doc["evaluatedProfile"]?.toString().orEmpty(),
            timezoneId = doc["timezoneId"]?.toString().orEmpty(),
            accessibilityConfigured = doc["accessibilityConfigured"]?.toString()?.toBooleanStrictOrNull() ?: false,
            accessibilityConnected = doc["accessibilityConnected"]?.toString()?.toBooleanStrictOrNull() ?: false,
            lastAccessibilityEventAt = doc["lastAccessibilityEventAt"]?.toString()?.toLongOrNull() ?: 0L,
            lastForegroundProbeSuccessAt = doc["lastForegroundProbeSuccessAt"]?.toString()?.toLongOrNull() ?: 0L,
            deviceManagementMode = doc["deviceManagementMode"]?.toString().orEmpty().ifBlank { "COMPATIBILITY" },
            selfUninstallBlocked = doc["selfUninstallBlocked"]?.toString()?.toBooleanStrictOrNull() ?: false,
            permissionHealthReported = doc["permissionHealthReported"]?.toString()?.toBooleanStrictOrNull() ?: false,
            usageAccessGranted = doc["usageAccessGranted"]?.toString()?.toBooleanStrictOrNull() ?: false,
            overlayGranted = doc["overlayGranted"]?.toString()?.toBooleanStrictOrNull() ?: false,
            batteryOptimizationIgnored = doc["batteryOptimizationIgnored"]?.toString()?.toBooleanStrictOrNull() ?: false,
            autostartConfirmed = doc["autostartConfirmed"]?.toString()?.toBooleanStrictOrNull() ?: false,
            notificationPermissionGranted = doc["notificationPermissionGranted"]
                ?.toString()?.toBooleanStrictOrNull() ?: false,
            updatePhase = doc["updatePhase"]?.toString().orEmpty().ifBlank { "IDLE" },
            updateTargetVersionCode = doc["updateTargetVersionCode"]?.toString()?.toLongOrNull() ?: 0L,
            updateTargetVersionName = doc["updateTargetVersionName"]?.toString().orEmpty(),
            installedVersionCode = doc["installedVersionCode"]?.toString()?.toLongOrNull() ?: 0L,
            installedVersionName = doc["installedVersionName"]?.toString().orEmpty(),
            updateFailureReason = doc["updateFailureReason"]?.toString().orEmpty(),
            updateStatusAt = doc["updateStatusAt"]?.toString()?.toLongOrNull() ?: 0L,
            androidVersion = doc["androidVersion"]?.toString().orEmpty(),
            deviceModel = doc["deviceModel"]?.toString().orEmpty(),
            batteryPercent = doc["batteryPercent"]?.toString()?.toIntOrNull() ?: -1,
            charging = doc["charging"]?.toString()?.toBooleanStrictOrNull() ?: false,
            availableStorageBytes = doc["availableStorageBytes"]?.toString()?.toLongOrNull() ?: 0L,
            deviceUptimeMs = doc["deviceUptimeMs"]?.toString()?.toLongOrNull() ?: 0L,
        )
}

/** 心跳快照。 */
data class HeartbeatSnapshot(
    val kidDeviceId: String,
    val date: String,
    val byPackage: Map<String, Long>,
    val totalMinutes: Long,
    val currentApp: String?,
    val reportedAt: Long,
    val appliedRuleRevision: Long = 0L,
    val evaluatedLocalDate: String = "",
    val evaluatedProfile: String = "",
    val timezoneId: String = "",
    val accessibilityConfigured: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val lastAccessibilityEventAt: Long = 0L,
    val lastForegroundProbeSuccessAt: Long = 0L,
    val deviceManagementMode: String = "COMPATIBILITY",
    val selfUninstallBlocked: Boolean = false,
    val permissionHealthReported: Boolean = false,
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val autostartConfirmed: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val updatePhase: String = "IDLE",
    val updateTargetVersionCode: Long = 0L,
    val updateTargetVersionName: String = "",
    val installedVersionCode: Long = 0L,
    val installedVersionName: String = "",
    val updateFailureReason: String = "",
    val updateStatusAt: Long = 0L,
    val androidVersion: String = "",
    val deviceModel: String = "",
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val availableStorageBytes: Long = 0L,
    val deviceUptimeMs: Long = 0L,
)
