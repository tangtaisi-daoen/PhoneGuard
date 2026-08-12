package com.familyguard.kid.stats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.familyguard.core.backend.CloudBaseAuth
import com.familyguard.core.backend.CloudBaseEvents
import com.familyguard.core.backend.CloudBaseRules
import com.familyguard.core.backend.CloudBaseUsage
import com.familyguard.core.session.RuleCacheStore
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.KidApp
import com.familyguard.kid.MainActivity
import com.familyguard.kid.R
import com.familyguard.kid.guide.GuardStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 前台心跳服务：每 90 秒上报使用量快照 + 刷新规则缓存 + 检测防护异常。
 * 前台服务保证后台常驻（需忽略电池优化配合）。
 */
class HeartbeatService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastDeviceTimeMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        lastDeviceTimeMs = System.currentTimeMillis()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            while (isActive) {
                runHeartbeat()
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
        SessionStore.boundAdminUid?.let { adminUid ->
            CloudBaseRules.fetchRules(KidApp.client, adminUid)?.let { rules ->
                RuleCacheStore.save(rules)
            }
        }

        // 使用量上报
        val byPackage = UsageStatsCollector.collectTodayMinutes(this)
        val total = byPackage.values.sum()
        val current = UsageStatsCollector.currentForegroundApp(this)
        CloudBaseUsage.upsertHeartbeat(
            KidApp.client, uid,
            UsageStatsCollector.todayDate(), byPackage, total, current,
        )

        // 防护异常检测
        detectAnomalies(uid)
    }

    /** 检测：使用情况访问被关 / 无障碍被关 / 设备管理器被停用 / 系统时间回拨。 */
    private suspend fun detectAnomalies(uid: String) {
        if (!GuardStatus.hasUsageAccess(this)) {
            CloudBaseEvents.report(KidApp.client, uid, "PERMISSION_DISABLED", "使用情况访问权限已被关闭，统计将失效")
        }
        if (!GuardStatus.isAccessibilityEnabled(this)) {
            CloudBaseEvents.report(KidApp.client, uid, "PERMISSION_DISABLED", "无障碍服务已被关闭，实时拦截失效")
        }
        if (!isAdminActive()) {
            CloudBaseEvents.report(KidApp.client, uid, "ADMIN_DISABLED", "设备管理器未激活，防卸载保护未生效")
        }
        // 时间回拨检测：设备时间比上次心跳倒退超过 5 分钟
        val now = System.currentTimeMillis()
        if (lastDeviceTimeMs > 0 && now < lastDeviceTimeMs - 5 * 60_000L) {
            CloudBaseEvents.report(KidApp.client, uid, "TIME_CHANGED", "检测到系统时间被回拨，可能试图绕过时长限制")
        }
        lastDeviceTimeMs = now
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
    }
}
