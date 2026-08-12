package com.familyguard.kid.stats

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.familyguard.core.backend.CloudBaseAuth
import com.familyguard.core.backend.CloudBaseUsage
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.KidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 心跳上报服务：每 90 秒上报当日使用量快照。
 * 绑定后由 MainActivity 启动；Task 12 将升级为前台服务并接入防护状态。
 */
class HeartbeatService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

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

    private suspend fun runHeartbeat() {
        if (!SessionStore.isBound) return
        // 匿名登录（refresh_token 长期有效，失败重试下次）
        val auth = CloudBaseAuth.signInAnonymously(KidApp.client, SessionStore.deviceId) ?: return
        val byPackage = UsageStatsCollector.collectTodayMinutes(this)
        val total = byPackage.values.sum()
        val current = UsageStatsCollector.currentForegroundApp(this)
        CloudBaseUsage.upsertHeartbeat(
            KidApp.client, auth.userId,
            UsageStatsCollector.todayDate(), byPackage, total, current,
        )
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 90_000L
    }
}
