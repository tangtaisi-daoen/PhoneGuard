package com.familyguard.kid.guard

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.familyguard.core.categories.CategoryRegistry
import com.familyguard.core.rules.RulesEngine
import com.familyguard.core.session.RuleCacheStore
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.BuildConfig
import com.familyguard.kid.R
import com.familyguard.kid.guide.GuardStatus
import com.familyguard.kid.stats.UsageStatsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
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
    private var pollJob: Job? = null
    private var lastForegroundPackage: String? = null
    private var lastUsageSnapshotAt = 0L
    private var cachedUsageMinutes: Map<String, Long> = emptyMap()
    private var lastSettingsWindowClass = ""
    private var lastProtectionBlockedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val showLauncher = Runnable { performGlobalAction(GLOBAL_ACTION_HOME) }
    private val protectionReturnNavigator by lazy {
        ProtectionReturnNavigator(
            dismissProtectedSurface = { performGlobalAction(GLOBAL_ACTION_BACK) },
            scheduleLauncher = { delayMs ->
                mainHandler.removeCallbacks(showLauncher)
                mainHandler.postDelayed(showLauncher, delayMs)
            },
        )
    }
    private val protectionRecheck = Runnable {
        if (!SessionStore.isBound) return@Runnable
        val root = rootInActiveWindow ?: return@Runnable
        val pkg = root.packageName?.toString() ?: return@Runnable
        blockProtectionPageIfNeeded(pkg, root.className?.toString().orEmpty(), root)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityHealthStore.markConnected(this)
        if (pollJob?.isActive == true) return
        // 轮询兜底：app 内操作不触发窗口事件时仍能拦截
        pollJob = pollScope.launch {
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
        if (event == null || event.eventType !in PROTECTION_EVENT_TYPES) return
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastSettingsWindowClass = if (pkg in SETTINGS_PACKAGES) {
                event.className?.toString().orEmpty()
            } else {
                ""
            }
        }
        lastForegroundPackage = pkg
        AccessibilityHealthStore.markEvent(this)
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "event: $pkg")
        val root = rootInActiveWindow ?: event.source
        if (SessionStore.isBound && root != null &&
            blockProtectionPageIfNeeded(pkg, event.className?.toString().orEmpty(), root)
        ) return
        if (pkg in SETTINGS_PACKAGES) {
            mainHandler.removeCallbacks(protectionRecheck)
            mainHandler.postDelayed(protectionRecheck, PROTECTION_RECHECK_DELAY_MS)
        }
        if (pkg !in WHITELIST && SessionStore.isBound) {
            checkAndBlock(pkg)
        }
    }

    private fun blockProtectionPageIfNeeded(
        pkg: String,
        className: String,
        root: AccessibilityNodeInfo,
    ): Boolean {
        if (pkg !in SETTINGS_PACKAGES) return false
        val now = System.currentTimeMillis()
        val visibleTexts = collectVisibleTexts(root)
        // 当前窗口类名优先；只有事件没有类名时才使用同一设置包的上一条类名。
        val effectiveClass = className.ifBlank { lastSettingsWindowClass }
        val risk = classifyProtectionPage(pkg, effectiveClass, visibleTexts) ?: return false
        val state = ProtectionPermissionState(
            usageAccessGranted = GuardStatus.hasUsageAccess(this),
            overlayGranted = GuardStatus.canDrawOverlays(this),
            autostartConfirmed = GuardStatus.isAutostartConfirmed(this),
        )
        if (!shouldBlockProtectionPage(risk, state)) return false
        if (now - lastProtectionBlockedAt >= PROTECTION_BLOCK_COOLDOWN_MS) {
            lastProtectionBlockedAt = now
            ProtectionAttemptStore.record(this, risk, now)
        }
        returnToLauncher()
        return true
    }

    private fun returnToLauncher() {
        protectionReturnNavigator.returnToLauncher()
    }

    private fun collectVisibleTexts(root: AccessibilityNodeInfo): Set<String> {
        val result = linkedSetOf<String>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited++ < MAX_PROTECTION_NODES) {
            val node = queue.removeFirst()
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(result::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(result::add)
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return result
    }

    /** 查询当前前台 app（UsageStats 最近一条进入前台事件）。 */
    private fun currentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val start = foregroundQueryStart(
            UsageStatsCollector.todayStartMillis(),
            AccessibilityHealthStore.lastProbeAt(this),
            end,
        )
        val events = usm.queryEvents(start, end) ?: return null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = e.packageName
            } else if (e.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND && e.packageName == lastForegroundPackage) {
                lastForegroundPackage = null
            }
        }
        AccessibilityHealthStore.markProbe(this, success = true, nowMs = end)
        return lastForegroundPackage
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
            if (BuildConfig.DEBUG) android.util.Log.w(TAG, "no rules cached, skip")
            return
        }
        val category = CategoryRegistry.classify(pkg)
        val now = LocalTime.now()

        val byPackage = usageMinutesSnapshot()
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
            extraAllowanceMillis = (
                RuleCacheStore.envelope?.activeAllowanceMinutes(pkg, System.currentTimeMillis()) ?: 0
                ) * 60_000L,
        )
        if (BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "$pkg verdict: blocked=${verdict.blocked} reason=${verdict.reason} rulesV=${rules.version} appLimits=${rules.appLimits}")
        }

        if (verdict.blocked) {
            // 全屏拦截：浮层 + 拦截 Activity 抢占前台（借鉴 cst；游戏无法压制 Activity）
            BlockOverlay.show(this, verdict.reason ?: getString(R.string.overlay_default_reason))
            BlockActivity.launch(this, verdict.reason ?: getString(R.string.overlay_default_reason))
        } else {
            if (BlockOverlay.isShowing()) BlockOverlay.hide()
        }
    }

    private fun usageMinutesSnapshot(): Map<String, Long> {
        val now = System.currentTimeMillis()
        if (cachedUsageMinutes.isEmpty() || now - lastUsageSnapshotAt >= USAGE_REFRESH_INTERVAL_MS) {
            val visiblePackages = UsageStatsCollector.visibleInstalledApps(this)
                .mapTo(mutableSetOf()) { it.first }
            cachedUsageMinutes = UsageStatsCollector.collectTodayMinutes(this, visiblePackages)
            lastUsageSnapshotAt = now
        }
        return cachedUsageMinutes
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityHealthStore.markDisconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityHealthStore.markDisconnected(this)
        mainHandler.removeCallbacks(protectionRecheck)
        mainHandler.removeCallbacks(showLauncher)
        pollScope.cancel()
        BlockOverlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FamilyGuard"
        private const val POLL_INTERVAL_MS = 5_000L
        private const val USAGE_REFRESH_INTERVAL_MS = 15_000L
        private const val PROTECTION_BLOCK_COOLDOWN_MS = 3_000L
        private const val PROTECTION_RECHECK_DELAY_MS = 350L
        private const val MAX_PROTECTION_NODES = 250
        private val PROTECTION_EVENT_TYPES = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
        )

        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.coloros.securitypermission",
            "com.oplus.securitypermission",
            "com.oppo.launcher",
            "com.coloros.launcher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.oplus.appdetail",
        )

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
