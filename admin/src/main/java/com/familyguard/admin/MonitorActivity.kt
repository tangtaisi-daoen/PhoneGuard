package com.familyguard.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.admin.databinding.ActivityMonitorBinding
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.backend.CloudBaseApps
import com.familyguard.core.backend.CloudBaseUsage
import com.familyguard.core.backend.CloudBaseRules
import com.familyguard.core.rules.RuleSyncHealth
import com.familyguard.core.rules.evaluateRuleSync
import com.familyguard.core.session.SessionStore
import com.familyguard.core.stats.UsageAggregator
import com.familyguard.core.stats.UsageReportBuilder
import kotlinx.coroutines.launch

/** 被控端实时状态 + 当日使用报告。 */
class MonitorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRefresh.setOnClickListener { load() }
        load()
    }

    private fun load() {
        val uid = SessionStore.userId ?: return
        binding.tvStatus.text = getString(R.string.monitor_loading)
        binding.tvSummary.text = ""
        binding.tvRuleStatus.text = ""
        binding.tvTopApps.text = ""
        binding.tvCategories.text = ""
        binding.tvTrend.text = ""
        binding.btnRefresh.isEnabled = false
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            if (kidId == null) {
                binding.tvStatus.text = getString(R.string.monitor_not_bound)
                binding.btnRefresh.isEnabled = true
                return@launch
            }
            val recent = CloudBaseUsage.fetchRecent(AdminApp.client, kidId)
            if (recent == null) {
                binding.tvStatus.text = getString(R.string.monitor_failed)
                binding.btnRefresh.isEnabled = true
                return@launch
            }
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val snapshot = UsageReportBuilder.latestSnapshotForDate(recent, today)
            if (snapshot == null) {
                binding.tvStatus.text = getString(R.string.monitor_no_data)
                binding.btnRefresh.isEnabled = true
                return@launch
            }
            val expectedRules = CloudBaseRules.fetchEnvelope(AdminApp.client, uid)
            val ruleSyncHealth = expectedRules?.let {
                evaluateRuleSync(
                    expectedRevision = it.revision,
                    appliedRevision = snapshot.appliedRuleRevision,
                    generatedAt = it.generatedAt,
                    nowMs = System.currentTimeMillis(),
                    graceMs = RULE_SYNC_GRACE_MS,
                )
            }
            // 在线状态（5 分钟阈值）
            val online = UsageAggregator.isOnline(snapshot.reportedAt, System.currentTimeMillis())
            val current = snapshot.currentApp
            val appNames = CloudBaseApps.fetchOrNull(AdminApp.client, kidId).orEmpty().toMap()
            binding.tvStatus.text = getString(
                R.string.monitor_status,
                if (online) getString(R.string.monitor_online) else getString(R.string.monitor_offline),
                current?.let(appNames::get) ?: getString(R.string.monitor_no_app),
                snapshot.date,
            )
            val report = UsageReportBuilder.build(
                byPackage = snapshot.byPackage,
                appNames = appNames,
                dailySnapshots = recent
                    .filter { it.date <= today }
                    .map {
                        it.date to UsageReportBuilder.visibleTotalMinutes(
                            it.byPackage,
                            appNames.keys,
                        )
                    },
            )
            binding.tvSummary.text = getString(
                R.string.monitor_summary,
                report.totalMinutes,
                report.appCount,
                report.topApps.firstOrNull()?.appName
                    ?: getString(R.string.monitor_no_app),
            )
            binding.tvRuleStatus.text = if (
                expectedRules != null &&
                (ruleSyncHealth == RuleSyncHealth.PENDING || ruleSyncHealth == RuleSyncHealth.STALE)
            ) {
                getString(
                    R.string.monitor_rule_outdated,
                    expectedRules.revision,
                    snapshot.appliedRuleRevision,
                )
            } else if (snapshot.appliedRuleRevision > 0L) {
                getString(
                    R.string.monitor_rule_status,
                    profileName(snapshot.evaluatedProfile),
                    snapshot.evaluatedLocalDate,
                    snapshot.appliedRuleRevision,
                )
            } else {
                getString(R.string.monitor_rule_pending)
            } + "\n" + getString(
                R.string.monitor_accessibility_status,
                if (!snapshot.accessibilityConfigured) {
                    getString(R.string.monitor_accessibility_disabled)
                } else if (snapshot.accessibilityConnected) {
                    getString(R.string.monitor_accessibility_connected)
                } else {
                    getString(R.string.monitor_accessibility_disconnected)
                },
            )
            binding.tvRuleStatus.append(
                "\n" + getString(
                    R.string.monitor_protection_status,
                    if (snapshot.deviceManagementMode == "FULLY_MANAGED" && snapshot.selfUninstallBlocked) {
                        getString(R.string.monitor_protection_managed)
                    } else {
                        getString(R.string.monitor_protection_compatibility)
                    },
                ),
            )
            binding.tvRuleStatus.append(
                "\n" + if (snapshot.permissionHealthReported) {
                    getString(
                        R.string.monitor_permissions_status,
                        permissionStatus(snapshot.usageAccessGranted),
                        permissionStatus(snapshot.overlayGranted),
                        permissionStatus(snapshot.batteryOptimizationIgnored),
                        permissionStatus(snapshot.autostartConfirmed),
                        permissionStatus(snapshot.notificationPermissionGranted),
                    )
                } else {
                    getString(R.string.monitor_permissions_pending)
                },
            )
            binding.tvRuleStatus.append("\n" + updateStatusText(snapshot))
            binding.tvRuleStatus.append("\n" + diagnosticText(snapshot))
            binding.tvTopApps.text = report.topApps.mapIndexed { index, app ->
                getString(
                    R.string.monitor_app_row,
                    index + 1,
                    app.appName,
                    app.minutes,
                )
            }.joinToString("\n").ifBlank { getString(R.string.monitor_no_app_data) }
            binding.tvCategories.text = report.categories.map { category ->
                getString(
                    R.string.monitor_category_row,
                    categoryName(category.category),
                    category.minutes,
                    category.percentage,
                )
            }.joinToString("\n").ifBlank { getString(R.string.monitor_no_category_data) }
            binding.tvTrend.text = report.dailyTrend.map { day ->
                getString(R.string.monitor_trend_row, day.date, day.totalMinutes)
            }.joinToString("\n").ifBlank { getString(R.string.monitor_no_trend_data) }
            binding.btnRefresh.isEnabled = true
        }
    }

    private fun categoryName(c: com.familyguard.core.categories.AppCategory): String = when (c) {
        com.familyguard.core.categories.AppCategory.GAME -> getString(R.string.cat_game)
        com.familyguard.core.categories.AppCategory.SHORT_VIDEO -> getString(R.string.cat_short_video)
        com.familyguard.core.categories.AppCategory.VIDEO -> getString(R.string.cat_video)
        com.familyguard.core.categories.AppCategory.SOCIAL -> getString(R.string.cat_social)
        com.familyguard.core.categories.AppCategory.TOOL -> getString(R.string.cat_tool)
        com.familyguard.core.categories.AppCategory.OTHER -> getString(R.string.cat_other)
    }

    private fun profileName(profile: String): String = when (profile) {
        "WEEKDAY" -> getString(R.string.rules_profile_weekday)
        "WEEKEND" -> getString(R.string.rules_profile_weekend)
        "HOLIDAY" -> getString(R.string.rules_profile_holiday)
        else -> getString(R.string.monitor_rule_unknown)
    }

    private fun permissionStatus(granted: Boolean): String =
        getString(if (granted) R.string.monitor_permission_ok else R.string.monitor_permission_missing)

    private fun updateStatusText(snapshot: com.familyguard.core.backend.HeartbeatSnapshot): String =
        when (snapshot.updatePhase) {
            "AWAITING_USER_CONFIRMATION", "READY_TO_INSTALL", "INSTALLING" -> getString(
                R.string.monitor_update_waiting,
                snapshot.updateTargetVersionName.ifBlank { snapshot.updateTargetVersionCode.toString() },
            )
            "CHECKING", "DOWNLOADING" -> getString(R.string.monitor_update_progress)
            "FAILED" -> getString(
                R.string.monitor_update_failed,
                snapshot.updateFailureReason.ifBlank { getString(R.string.monitor_update_unknown_error) },
            )
            "SUCCEEDED" -> getString(
                R.string.monitor_update_succeeded,
                snapshot.installedVersionName.ifBlank { snapshot.installedVersionCode.toString() },
            )
            else -> if (snapshot.installedVersionCode > 0L) {
                getString(
                    R.string.monitor_update_current,
                    snapshot.installedVersionName,
                    snapshot.installedVersionCode,
                )
            } else {
                getString(R.string.monitor_update_pending)
            }
        }

    private fun diagnosticText(snapshot: com.familyguard.core.backend.HeartbeatSnapshot): String {
        val battery = if (snapshot.batteryPercent >= 0) {
            "${snapshot.batteryPercent}%${if (snapshot.charging) getString(R.string.monitor_charging) else ""}"
        } else {
            getString(R.string.monitor_diag_unknown)
        }
        val storage = if (snapshot.availableStorageBytes > 0L) {
            String.format(java.util.Locale.US, "%.1f GB", snapshot.availableStorageBytes / 1_000_000_000.0)
        } else {
            getString(R.string.monitor_diag_unknown)
        }
        val uptime = if (snapshot.deviceUptimeMs > 0L) {
            getString(R.string.monitor_uptime_hours, snapshot.deviceUptimeMs / 3_600_000L)
        } else {
            getString(R.string.monitor_diag_unknown)
        }
        return getString(
            R.string.monitor_device_diagnostics,
            snapshot.deviceModel.ifBlank { getString(R.string.monitor_diag_unknown) },
            snapshot.androidVersion.ifBlank { getString(R.string.monitor_diag_unknown) },
            battery,
            storage,
            uptime,
        )
    }

    private companion object {
        const val RULE_SYNC_GRACE_MS = 5 * 60_000L
    }
}
