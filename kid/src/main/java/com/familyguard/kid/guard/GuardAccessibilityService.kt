package com.familyguard.kid.guard

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.familyguard.core.categories.CategoryRegistry
import com.familyguard.core.rules.RulesEngine
import com.familyguard.core.session.RuleCacheStore
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.R
import com.familyguard.kid.stats.UsageStatsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * 实时拦截服务：双通道检测前台应用——
 * 1. 无障碍窗口事件（窗口切换时）
 * 2. 轮询兜底（每 5 秒查 UsageStats 当前前台 app，覆盖 app 内不切页的情况）
 * 命中规则（额度用尽/禁玩时段）立即踢回桌面；白名单不受影响；3 秒冷却防循环。
 */
class GuardAccessibilityService : AccessibilityService() {

    private var lastBlockedAt = 0L
    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 轮询兜底：app 内操作不触发窗口事件时仍能拦截
        pollScope.launch {
            while (isActive) {
                runCatching {
                    currentForegroundPackage()?.let { pkg ->
                        if (pkg !in WHITELIST && SessionStore.isBound) {
                            checkAndBlock(pkg)
                        }
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        android.util.Log.d(TAG, "event: $pkg")
        if (pkg !in WHITELIST && SessionStore.isBound) {
            checkAndBlock(pkg)
        }
    }

    /** 查询当前前台 app（UsageStats 最近一条进入前台事件）。 */
    private fun currentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val start = end - 24 * 60 * 60 * 1000L
        val events = usm.queryEvents(start, end) ?: return null
        var result: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                result = e.packageName
            }
        }
        return result
    }

    /** 规则判定 + 拦截（事件回调与轮询共用）。 */
    private fun checkAndBlock(pkg: String) {
        if (pkg == packageName) return
        // 白名单/自身在前台 → 解除拦截浮层
        if (pkg in WHITELIST) {
            if (BlockOverlay.isShowing()) BlockOverlay.hide()
            return
        }
        val rules = RuleCacheStore.rules
        if (rules == null) {
            android.util.Log.w(TAG, "no rules cached, skip")
            return
        }
        val category = CategoryRegistry.classify(pkg)
        val now = LocalTime.now()

        val byPackage = UsageStatsCollector.collectTodayMinutes(this)
        val categoryMinutes = byPackage.entries
            .filter { CategoryRegistry.classify(it.key) == category }
            .sumOf { it.value }

        val verdict = RulesEngine.verdict(
            packageName = pkg,
            category = category,
            rules = rules,
            todayUsedByApp = (byPackage[pkg] ?: 0L) * 60_000L,
            todayUsedByCategory = categoryMinutes * 60_000L,
            todayUsedTotal = byPackage.values.sum() * 60_000L,
            now = now,
        )
        android.util.Log.d(TAG, "$pkg verdict: blocked=${verdict.blocked} reason=${verdict.reason} rulesV=${rules.version} appLimits=${rules.appLimits}")

        if (verdict.blocked) {
            // 全屏拦截：浮层 + 拦截 Activity 抢占前台（借鉴 cst；游戏无法压制 Activity）
            BlockOverlay.show(this, verdict.reason ?: getString(R.string.overlay_default_reason))
            BlockActivity.launch(this, verdict.reason ?: getString(R.string.overlay_default_reason))
        } else {
            if (BlockOverlay.isShowing()) BlockOverlay.hide()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        pollScope.cancel()
        BlockOverlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FamilyGuard"
        private const val POLL_INTERVAL_MS = 5_000L

        /** 白名单：系统组件与基础通信，永不拦截。 */
        private val WHITELIST = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.dialer",
            "com.android.phone",
            "com.android.incallui",
            "com.android.mms",
            "com.android.contacts",
            "com.bbk.launcher2",
            "com.vivo.launcher",
            "com.vivo.settings",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.familyguard.kid",
            "com.familyguard.admin",
            "com.android.permissioncontroller",
        )
    }
}
