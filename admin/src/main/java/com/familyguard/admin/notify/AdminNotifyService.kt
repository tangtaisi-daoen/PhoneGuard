package com.familyguard.admin.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.familyguard.admin.AnomalyActivity
import com.familyguard.admin.AdminApp
import com.familyguard.admin.MonitorActivity
import com.familyguard.admin.R
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.backend.CloudBaseEvents
import com.familyguard.core.backend.CloudBaseUsage
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 异常轮询服务：每 30 秒查询被控端未读异常事件，发现即弹本地通知。
 */
class AdminNotifyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            while (isActive) {
                runPoll()
                delay(POLL_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runPoll() {
        if (!SessionStore.isLoggedIn) return
        val uid = SessionStore.userId ?: return
        // 管理端 token 已在 AdminApp.client（SessionStore 恢复 + 401 自动刷新）
        val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid) ?: return

        // 离线检测：心跳超过 5 分钟未更新 → 通知一次（5 分钟内不重复）
        val snapshot = CloudBaseUsage.fetchLatest(AdminApp.client, kidId)
        val now = System.currentTimeMillis()
        if (snapshot != null && now - snapshot.reportedAt > OFFLINE_THRESHOLD_MS) {
            if (now - lastOfflineNotifiedAt > OFFLINE_NOTIFY_INTERVAL_MS) {
                lastOfflineNotifiedAt = now
                notifyOffline(snapshot.reportedAt)
            }
        }

        // 异常事件轮询
        val events = CloudBaseEvents.fetchUnread(AdminApp.client, kidId)
        if (events.isEmpty()) return
        notifyAnomalies(events)
        CloudBaseEvents.markAllRead(AdminApp.client, kidId)
    }

    private fun notifyOffline(lastHeartbeatAt: Long) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MonitorActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.offline_notif_title))
            .setContentText(getString(R.string.offline_notif_text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(OFFLINE_NOTIFICATION_ID, notification)
    }

    private fun notifyAnomalies(events: List<com.familyguard.core.data.AnomalyEvent>) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, AnomalyActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val first = events.first()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.anomaly_notif_title, events.size))
            .setContentText(first.message)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.notif_channel_anomaly),
            NotificationManager.IMPORTANCE_HIGH,
        )
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val POLL_INTERVAL_MS = 30_000L
        private const val NOTIFICATION_ID = 100
        private const val OFFLINE_NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "admin_anomaly"
        private const val OFFLINE_THRESHOLD_MS = 5 * 60_000L
        private const val OFFLINE_NOTIFY_INTERVAL_MS = 10 * 60_000L

        /** 最近一次离线通知时间（进程内）。 */
        private var lastOfflineNotifiedAt = 0L
    }
}
