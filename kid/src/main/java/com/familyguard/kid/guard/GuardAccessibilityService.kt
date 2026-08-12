package com.familyguard.kid.guard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.familyguard.core.categories.AppCategory
import com.familyguard.core.categories.CategoryRegistry
import com.familyguard.core.rules.RulesEngine
import com.familyguard.core.session.RuleCacheStore
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.stats.UsageStatsCollector
import java.time.LocalTime

/**
 * 实时拦截服务：监听窗口变化，命中规则（额度用尽/禁玩时段）立即踢回桌面。
 * 白名单（系统/拨号/桌面）不受影响；拦截带 3 秒冷却防循环踢出。
 */
class GuardAccessibilityService : AccessibilityService() {

    private var lastBlockedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in WHITELIST) return
        if (!SessionStore.isBound) return

        val rules = RuleCacheStore.rules ?: return
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

        if (verdict.blocked) {
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastBlockedAt > BLOCK_COOLDOWN_MS) {
                lastBlockedAt = nowMs
                Toast.makeText(this, "手机守护：${verdict.reason ?: "已超时"}，已为你返回桌面", Toast.LENGTH_SHORT).show()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
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
            "com.bbk.launcher2",
            "com.vivo.launcher",
            "com.vivo.settings",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.familyguard.kid",
            "com.familyguard.admin",
            "com.android.permissioncontroller",
        )

        private const val BLOCK_COOLDOWN_MS = 3_000L
    }
}
