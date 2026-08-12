package com.familyguard.kid.stats

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.guard.GuardAccessibilityService

/**
 * 保活兜底（借鉴 cst 的 WorkManager 方案）：
 * 若前台心跳服务已停（心跳超过 10 分钟未更新），尝试重启心跳服务。
 */
class GuardWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!SessionStore.isBound) return Result.success()
        val stale = System.currentTimeMillis() - HeartbeatState.lastHeartbeatAt > STALE_THRESHOLD_MS
        if (stale) {
            runCatching {
                val intent = Intent(applicationContext, HeartbeatService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            }
        }
        return Result.success()
    }

    companion object {
        private const val STALE_THRESHOLD_MS = 10 * 60_000L
    }
}
