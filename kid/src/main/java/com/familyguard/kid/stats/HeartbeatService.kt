package com.familyguard.kid.stats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.BatteryManager
import android.os.SystemClock
import android.content.IntentFilter
import com.familyguard.core.backend.CloudBaseApps
import com.familyguard.core.backend.CloudBaseAuth
import com.familyguard.core.backend.CloudBaseEvents
import com.familyguard.core.backend.CloudBaseRules
import com.familyguard.core.backend.CloudBaseUsage
import com.familyguard.core.protect.DeviceManagementMode
import com.familyguard.core.protect.ProtectionHealth
import com.familyguard.core.protect.evaluateProtectionHealth
import com.familyguard.core.session.RuleCacheStore
import com.familyguard.core.session.SessionStore
import com.familyguard.core.stats.shouldReconcileConditions
import com.familyguard.kid.KidApp
import com.familyguard.kid.MainActivity
import com.familyguard.kid.R
import com.familyguard.kid.guard.GuardAccessibilityService
import com.familyguard.kid.guard.AccessibilityHealth
import com.familyguard.kid.guard.AccessibilityHealthStore
import com.familyguard.kid.guard.evaluateAccessibilityHealth
import com.familyguard.kid.guard.ProtectionAttemptStore
import com.familyguard.kid.guide.GuardStatus
import com.familyguard.kid.protect.KidDevicePolicyController
import com.familyguard.kid.update.UpdateDeliveryPhase
import com.familyguard.kid.update.UpdateDeliveryPolicy
import com.familyguard.kid.update.UpdateDeliveryStatus
import com.familyguard.kid.update.UpdateDeliveryStore
import com.familyguard.kid.update.KidUpdateManager
import com.familyguard.kid.update.KidUpdateResult
import com.familyguard.kid.update.shouldRunUpdateDelivery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 前台心跳服务：每 90 秒上报使用量快照 + 刷新规则缓存 + 检测防护异常。
 * 前台服务保证后台常驻（需忽略电池优化配合）。
 */
class HeartbeatService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var heartbeatJob: Job? = null
    private var lastDeviceTimeMs = 0L
    private val lastAnomalyReportedAt = mutableMapOf<String, Long>()
    private var lastHealthConditions: Map<String, String>? = null
    private var lastHealthReconciledAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lastDeviceTimeMs = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 8+ startForegroundService 必须在 5 秒内 startForeground（onCreate 已做，此处兜底）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (_: Exception) {
            }
        }
        if (heartbeatJob?.isActive == true) return START_STICKY
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    runHeartbeat()
                } catch (e: Exception) {
                    // 单次心跳失败不影响循环（防止协程死亡导致心跳永久停止）
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel_guard),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.notif_guard_title))
            .setContentText(getString(R.string.notif_guard_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private suspend fun runHeartbeat() {
        if (!SessionStore.isBound) return
        // 匿名登录（refresh_token 长期有效，失败重试下次）
        val auth = CloudBaseAuth.signInAnonymously(KidApp.client, SessionStore.deviceId) ?: return
        val uid = auth.userId

        // 同步刷新规则缓存（拦截服务使用，断网兜底旧规则）
        // 兼容两级拉取：先按被控端匿名 uid（安全规则收紧后的主路径），
        // 未命中（旧文档尚无 kidDeviceId）回退按 boundAdminUid 查询（规则收紧前的存量数据）。
        var envelope = CloudBaseRules.fetchEnvelopeForKid(KidApp.client, uid)
        if (envelope == null || envelope.revision == 0L) {
            envelope = SessionStore.boundAdminUid?.let { CloudBaseRules.fetchEnvelope(KidApp.client, it) }
        }
        envelope?.let { RuleCacheStore.save(it) }

        val apps = UsageStatsCollector.visibleInstalledApps(this)
        val visiblePackages = apps.mapTo(mutableSetOf()) { it.first }

        // 使用量上报：只统计应用列表中可见的普通应用。
        val byPackage = UsageStatsCollector.collectTodayMinutes(this, visiblePackages)
        val total = byPackage.values.sum()
        val current = UsageStatsCollector.currentForegroundApp(this, visiblePackages)
        val ruleEnvelope = RuleCacheStore.envelope
        val ruleZone = runCatching { ZoneId.of(ruleEnvelope?.timezoneId ?: "Asia/Shanghai") }
            .getOrDefault(ZoneId.of("Asia/Shanghai"))
        val evaluatedDate = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ruleZone).toLocalDate()
        val accessibilityHealth = AccessibilityHealthStore.snapshot(this)
        val protectionController = KidDevicePolicyController(this)
        val managementMode = protectionController.managementMode()
        val selfUninstallBlocked = protectionController.isSelfUninstallBlocked()
        val usageAccessGranted = GuardStatus.hasUsageAccess(this)
        val overlayGranted = GuardStatus.canDrawOverlays(this)
        val batteryOptimizationIgnored = GuardStatus.ignoresBatteryOptimization(this)
        val autostartConfirmed = GuardStatus.isAutostartConfirmed(this)
        val notificationPermissionGranted = GuardStatus.notificationsEnabled(this)
        val updateStatus = UpdateDeliveryStore.load(this)
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryState = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryState == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryState == BatteryManager.BATTERY_STATUS_FULL
        val availableStorageBytes = filesDir.usableSpace
        CloudBaseUsage.upsertHeartbeat(
            KidApp.client, uid,
            UsageStatsCollector.todayDate(), byPackage, total, current,
            adminUid = SessionStore.boundAdminUid,
            appliedRuleRevision = ruleEnvelope?.revision ?: 0L,
            evaluatedLocalDate = evaluatedDate.toString(),
            evaluatedProfile = ruleEnvelope?.profileFor(evaluatedDate)?.name.orEmpty(),
            timezoneId = ruleZone.id,
            accessibilityConfigured = GuardStatus.isAccessibilityEnabled(this),
            accessibilityConnected = accessibilityHealth.connected,
            lastAccessibilityEventAt = accessibilityHealth.lastEventAt,
            lastForegroundProbeSuccessAt = accessibilityHealth.lastProbeSuccessAt,
            deviceManagementMode = managementMode.name,
            selfUninstallBlocked = selfUninstallBlocked,
            permissionHealthReported = true,
            usageAccessGranted = usageAccessGranted,
            overlayGranted = overlayGranted,
            batteryOptimizationIgnored = batteryOptimizationIgnored,
            autostartConfirmed = autostartConfirmed,
            notificationPermissionGranted = notificationPermissionGranted,
            updatePhase = updateStatus.phase.name,
            updateTargetVersionCode = updateStatus.targetVersionCode,
            updateTargetVersionName = updateStatus.targetVersionName,
            installedVersionCode = updateStatus.installedVersionCode
                .takeIf { it > 0L } ?: KidUpdateManager.installedVersionCode(this),
            installedVersionName = updateStatus.installedVersionName
                .ifBlank { KidUpdateManager.installedVersionName(this) },
            updateFailureReason = updateStatus.failureReason,
            updateStatusAt = updateStatus.updatedAt,
            androidVersion = Build.VERSION.RELEASE.orEmpty(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            batteryPercent = batteryPercent,
            charging = charging,
            availableStorageBytes = availableStorageBytes,
            deviceUptimeMs = SystemClock.elapsedRealtime(),
        )

        // 已装应用列表上报（管理端规则选择用）
        runCatching {
            CloudBaseApps.upsert(KidApp.client, uid, apps, adminUid = SessionStore.boundAdminUid)
        }

        // 防护异常检测
        detectAnomalies(uid, availableStorageBytes)
        checkForUpdate(uid)
    }

    private suspend fun checkForUpdate(uid: String) {
        val preferences = getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastAttempt = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        var status = UpdateDeliveryStore.load(this)
        if (status.phase == UpdateDeliveryPhase.AWAITING_USER_CONFIRMATION &&
            KidUpdateManager.preparedApk(this) == null
        ) {
            status = UpdateDeliveryPolicy.failed(status, "已验证安装包被系统清理，需要重新下载", now)
            UpdateDeliveryStore.save(this, status)
        }
        if (!shouldRunUpdateDelivery(now, lastAttempt, UPDATE_CHECK_INTERVAL_MS, status)) return
        preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, now).apply()
        UpdateDeliveryStore.save(
            this,
            status.copy(
                phase = UpdateDeliveryPhase.CHECKING,
                installedVersionCode = KidUpdateManager.installedVersionCode(this),
                installedVersionName = KidUpdateManager.installedVersionName(this),
                updatedAt = now,
                failureReason = "",
            ),
        )

        when (val result = KidUpdateManager.checkAndDownload(this)) {
            KidUpdateResult.UpToDate -> UpdateDeliveryStore.save(
                this,
                UpdateDeliveryStatus(
                    phase = UpdateDeliveryPhase.IDLE,
                    installedVersionCode = KidUpdateManager.installedVersionCode(this),
                    installedVersionName = KidUpdateManager.installedVersionName(this),
                    updatedAt = now,
                ),
            )
            is KidUpdateResult.Ready -> {
                UpdateDeliveryStore.save(
                    this,
                    UpdateDeliveryPolicy.readyForInstall(
                        result.manifest.versionCode,
                        result.manifest.versionName,
                        requiresUserConfirmation = true,
                        nowMs = now,
                    ).copy(
                        installedVersionCode = KidUpdateManager.installedVersionCode(this),
                        installedVersionName = KidUpdateManager.installedVersionName(this),
                    ),
                )
                reportAnomalyOnce(
                    uid,
                    "UPDATE_AVAILABLE",
                    "新版本 ${result.manifest.versionName} 已下载并验证，需要在被控端确认安装",
                )
                showUpdateNotification(result.manifest.versionName)
            }
            is KidUpdateResult.Failed -> {
                UpdateDeliveryStore.update(this) { previous ->
                    UpdateDeliveryPolicy.failed(previous, result.reason, now)
                }
                reportAnomalyOnce(uid, "UPDATE_CHECK_FAILED", "被控端更新失败：${result.reason}")
            }
        }
    }

    private fun showUpdateNotification(versionName: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL_ID,
                getString(R.string.notif_channel_update),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val intent = Intent(this, MainActivity::class.java)
            .setAction(KidUpdateManager.ACTION_CHECK_UPDATE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            UPDATE_NOTIFICATION_ID,
            Notification.Builder(this, UPDATE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(getString(R.string.notif_update_title, versionName))
                .setContentText(getString(R.string.notif_update_text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    /** 检测：使用情况访问被关 / 无障碍被关 / 设备管理器被停用 / 系统时间回拨。 */
    private suspend fun detectAnomalies(uid: String, availableStorageBytes: Long) {
        val activeConditions = linkedMapOf<String, String>()
        if (!GuardStatus.hasUsageAccess(this)) {
            activeConditions["PERMISSION_DISABLED"] = "使用情况访问权限已被关闭，统计将失效"
        }
        if (!GuardStatus.canDrawOverlays(this)) {
            activeConditions["OVERLAY_PERMISSION_DISABLED"] = "悬浮窗权限已被关闭，限时拦截层可能失效"
        }
        if (!GuardStatus.ignoresBatteryOptimization(this)) {
            activeConditions["BATTERY_OPTIMIZATION_ENABLED"] = "手机守护仍受电池优化限制，后台服务可能被终止"
        }
        if (!GuardStatus.isAutostartConfirmed(this)) {
            activeConditions["AUTOSTART_NOT_CONFIRMED"] = "尚未确认允许手机守护自启动，重启后守护可能无法恢复"
        }
        if (!GuardStatus.notificationsEnabled(this)) {
            activeConditions["NOTIFICATION_PERMISSION_DISABLED"] = "通知权限已关闭，守护异常和更新提醒可能无法显示"
        }
        if (availableStorageBytes in 1 until LOW_STORAGE_THRESHOLD_BYTES) {
            activeConditions["LOW_STORAGE"] = "被控端可用空间不足 500 MB，远程更新可能失败"
        }
        accessibilityHealthCondition()?.let { (type, message) -> activeConditions[type] = message }
        reportProtectionAttempt(uid)
        if (!isAdminActive()) {
            activeConditions["ADMIN_DISABLED"] = "设备管理器未激活，防卸载保护未生效"
        }
        // 时间回拨检测：设备时间比上次心跳倒退超过 5 分钟
        if (isAdminActive()) {
            managedProtectionCondition()?.let { (type, message) -> activeConditions[type] = message }
        }
        val updateStatus = UpdateDeliveryStore.load(this)
        if (updateStatus.phase == UpdateDeliveryPhase.FAILED) {
            activeConditions["UPDATE_CHECK_FAILED"] =
                "被控端更新失败：${updateStatus.failureReason.ifBlank { "未知错误" }}"
        }
        val now = System.currentTimeMillis()
        if (lastDeviceTimeMs > 0 && now < lastDeviceTimeMs - 5 * 60_000L) {
            reportAnomalyOnce(uid, "TIME_CHANGED", "检测到系统时间被回拨，可能试图绕过时长限制")
        }
        if (shouldReconcileConditions(
                previousConditions = lastHealthConditions,
                currentConditions = activeConditions,
                lastReconciledAt = lastHealthReconciledAt,
                nowMs = now,
                refreshIntervalMs = HEALTH_RECONCILE_INTERVAL_MS,
            ) && CloudBaseEvents.reconcileConditions(
                KidApp.client,
                uid,
                activeConditions,
                MANAGED_HEALTH_INCIDENT_TYPES,
                adminUid = SessionStore.boundAdminUid,
            )
        ) {
            lastHealthConditions = activeConditions.toMap()
            lastHealthReconciledAt = now
        }
        lastDeviceTimeMs = now
        HeartbeatState.touch()
    }

    private fun accessibilityHealthCondition(): Pair<String, String>? {
        val configured = GuardStatus.isAccessibilityEnabled(this)
        val snapshot = AccessibilityHealthStore.snapshot(this)
        return when (evaluateAccessibilityHealth(
            configured = configured,
            connected = snapshot.connected,
            processStartedAt = snapshot.processStartedAt,
            nowMs = System.currentTimeMillis(),
        )) {
            AccessibilityHealth.DISABLED -> {
                val restored = tryRestoreAccessibility()
                "ACCESSIBILITY_DISABLED" to if (restored) {
                    "无障碍开关曾被关闭，已尝试自动恢复"
                } else {
                    "无障碍开关已关闭，实时拦截失效"
                }
            }
            AccessibilityHealth.DISCONNECTED -> "ACCESSIBILITY_DISCONNECTED" to
                "无障碍开关已开启，但服务超过 3 分钟未连接，可能被系统终止"
            AccessibilityHealth.STARTING, AccessibilityHealth.CONNECTED -> null
        }
    }

    private fun managedProtectionCondition(): Pair<String, String>? {
        val controller = KidDevicePolicyController(this)
        val deviceOwner = controller.managementMode() == DeviceManagementMode.FULLY_MANAGED
        if (!deviceOwner) return null
        val selfUninstallBlocked = controller.isSelfUninstallBlocked()
        return when (evaluateProtectionHealth(true, deviceOwner, selfUninstallBlocked)) {
            ProtectionHealth.DEVICE_OWNER_MISSING -> "DEVICE_OWNER_MISSING" to
                "设备所有者状态异常，系统级防卸载能力丢失"
            ProtectionHealth.UNINSTALL_PROTECTION_MISSING -> {
                controller.applyBaseline()
                "UNINSTALL_PROTECTION_MISSING" to "设备所有者存在，但防卸载基线缺失，已尝试重新应用"
            }
            ProtectionHealth.ADMIN_DISABLED, ProtectionHealth.PROTECTED -> null
        }
    }

    private suspend fun reportProtectionAttempt(uid: String) {
        val attempt = ProtectionAttemptStore.pending(this) ?: return
        if (CloudBaseEvents.report(KidApp.client, uid, attempt.type, attempt.message, adminUid = SessionStore.boundAdminUid)) {
            ProtectionAttemptStore.clear(this)
        }
    }

    private suspend fun reportAnomalyOnce(uid: String, type: String, message: String) {
        val now = System.currentTimeMillis()
        val previous = lastAnomalyReportedAt[type] ?: 0L
        if (now - previous < ANOMALY_REPORT_INTERVAL_MS) return
        if (CloudBaseEvents.report(KidApp.client, uid, type, message, adminUid = SessionStore.boundAdminUid)) {
            lastAnomalyReportedAt[type] = now
        }
    }

    /** 尝试自动恢复无障碍服务（需要 WRITE_SECURE_SETTINGS 权限，ADB 一次性授权）。 */
    private fun tryRestoreAccessibility(): Boolean {
        return runCatching {
            val cn = ComponentName(this, GuardAccessibilityService::class.java).flattenToString()
            val existing = android.provider.Settings.Secure.getString(
                contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: ""
            val updated = if (existing.isBlank()) cn else "$existing:$cn"
            if (existing.split(':').any { it.equals(cn, ignoreCase = true) }) return true
            android.provider.Settings.Secure.putString(
                contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated,
            )
            android.provider.Settings.Secure.putString(
                contentResolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, "1",
            )
            true
        }.getOrDefault(false)
    }

    private fun isAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val cn = android.content.ComponentName(this, com.familyguard.kid.protect.KidDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(cn)
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 90_000L
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "guard_foreground"
        private const val ANOMALY_REPORT_INTERVAL_MS = 30 * 60_000L
        private const val UPDATE_CHECK_INTERVAL_MS = 6 * 60 * 60_000L
        private const val UPDATE_PREFERENCES = "periodic_update"
        private const val KEY_LAST_UPDATE_CHECK = "last_attempt_ms"
        private const val UPDATE_NOTIFICATION_ID = 2
        private const val UPDATE_CHANNEL_ID = "guard_updates"
        private val MANAGED_HEALTH_INCIDENT_TYPES = setOf(
            "PERMISSION_DISABLED",
            "OVERLAY_PERMISSION_DISABLED",
            "BATTERY_OPTIMIZATION_ENABLED",
            "AUTOSTART_NOT_CONFIRMED",
            "NOTIFICATION_PERMISSION_DISABLED",
            "LOW_STORAGE",
            "ACCESSIBILITY_DISABLED",
            "ACCESSIBILITY_DISCONNECTED",
            "ADMIN_DISABLED",
            "DEVICE_OWNER_MISSING",
            "UNINSTALL_PROTECTION_MISSING",
            "UPDATE_CHECK_FAILED",
        )
        private const val LOW_STORAGE_THRESHOLD_BYTES = 500L * 1024L * 1024L
        private const val HEALTH_RECONCILE_INTERVAL_MS = 5 * 60_000L
    }
}
