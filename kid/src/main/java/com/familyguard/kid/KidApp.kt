package com.familyguard.kid

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.familyguard.core.backend.CloudBaseClient
import com.familyguard.core.session.RuleCacheStore
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.stats.HeartbeatState
import com.familyguard.kid.stats.GuardWorker
import com.familyguard.kid.guard.AccessibilityHealthStore
import java.util.concurrent.TimeUnit

class KidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
        RuleCacheStore.init(this)
        HeartbeatState.init(this)
        AccessibilityHealthStore.markProcessStarted(this)
        client = CloudBaseClient(BuildConfig.CLOUDBASE_ENV_ID).apply {
            // 被控端使用匿名身份，token 可随时重新获取
        }
        scheduleGuardWorker()
    }

    /** 保活兜底：每 15 分钟检查心跳服务是否存活（借鉴 cst 的 WorkManager 方案）。 */
    private fun scheduleGuardWorker() {
        val request = PeriodicWorkRequestBuilder<GuardWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "guard_worker", ExistingPeriodicWorkPolicy.KEEP, request,
        )
    }

    companion object {
        /** 全局 CloudBase 客户端（被控端匿名认证）。 */
        lateinit var client: CloudBaseClient
    }
}
