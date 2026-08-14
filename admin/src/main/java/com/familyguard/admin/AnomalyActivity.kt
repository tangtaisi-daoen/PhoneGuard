package com.familyguard.admin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.admin.databinding.ActivityAnomalyBinding
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.backend.CloudBaseEvents
import com.familyguard.core.stats.AnomalyAggregator
import com.familyguard.core.stats.AnomalySeverity
import com.familyguard.core.data.IncidentStatus
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 异常事件列表页。 */
class AnomalyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAnomalyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnomalyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnRefreshAnomaly.setOnClickListener { load() }
        binding.btnAcknowledgeAnomaly.setOnClickListener { acknowledgeOpen() }
        load()
    }

    private fun load() {
        val uid = SessionStore.userId ?: return
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            if (kidId == null) {
                binding.tvAnomalySummary.text = getString(R.string.anomaly_not_bound)
                binding.tvAnomalyList.text = getString(R.string.anomaly_none)
                binding.btnAcknowledgeAnomaly.isEnabled = false
                return@launch
            }
            val all = CloudBaseEvents.fetchAllOrNull(AdminApp.client, kidId, limit = 50, adminUid = uid)
            if (all == null) {
                binding.tvAnomalySummary.text = getString(R.string.anomaly_failed)
                binding.tvAnomalyList.text = ""
                binding.btnAcknowledgeAnomaly.isEnabled = false
                return@launch
            }
            if (all.isEmpty()) {
                binding.tvAnomalySummary.text = getString(R.string.anomaly_healthy)
                binding.tvAnomalyList.text = getString(R.string.anomaly_none)
                binding.btnAcknowledgeAnomaly.isEnabled = false
            } else {
                val groups = AnomalyAggregator.group(all)
                val activeCount = groups.count { it.status != IncidentStatus.RESOLVED }
                val resolvedCount = groups.count { it.status == IncidentStatus.RESOLVED }
                binding.tvAnomalySummary.text = getString(
                    R.string.anomaly_summary,
                    activeCount,
                    resolvedCount,
                )
                binding.btnAcknowledgeAnomaly.isEnabled = groups.any { it.status == IncidentStatus.OPEN }
                val sb = StringBuilder()
                val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
                groups.sortedWith(
                    compareBy<com.familyguard.core.stats.AnomalyGroup> {
                        if (it.status == IncidentStatus.RESOLVED) 1 else 0
                    }.thenByDescending { it.lastSeenAt },
                ).forEach { e ->
                    sb.append('[').append(statusName(e.status)).append("] ")
                        .append(typeName(e.type)).append(" · ")
                        .append(severityName(e.severity))
                        .append(if (e.count > 1) " ×${e.count}" else "")
                        .append('\n')
                    sb.append("  ")
                        .append(getString(
                            R.string.anomaly_time_range,
                            fmt.format(Date(e.firstSeenAt)),
                            fmt.format(Date(e.lastSeenAt)),
                        ))
                        .append('\n')
                    if (e.status == IncidentStatus.RESOLVED && e.resolvedAt > 0L) {
                        sb.append("  ")
                            .append(getString(R.string.anomaly_resolved_at, fmt.format(Date(e.resolvedAt))))
                            .append('\n')
                    }
                    sb.append("  ").append(e.message).append('\n')
                    sb.append("  ").append(getString(R.string.anomaly_suggestion, e.suggestion)).append('\n').append('\n')
                }
                binding.tvAnomalyList.text = sb.toString()
            }
        }
    }

    private fun acknowledgeOpen() {
        val uid = SessionStore.userId ?: return
        binding.btnAcknowledgeAnomaly.isEnabled = false
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            val ok = kidId != null && CloudBaseEvents.acknowledgeOpen(
                AdminApp.client,
                kidId,
                adminUid = uid,
            )
            android.widget.Toast.makeText(
                this@AnomalyActivity,
                if (ok) R.string.anomaly_acknowledged else R.string.anomaly_ack_failed,
                android.widget.Toast.LENGTH_SHORT,
            ).show()
            load()
        }
    }

    private fun typeName(type: String): String = when (type) {
        "ADMIN_DISABLED" -> getString(R.string.anomaly_type_admin)
        "PERMISSION_DISABLED" -> getString(R.string.anomaly_type_permission)
        "TIME_CHANGED" -> getString(R.string.anomaly_type_time)
        "NEW_APP" -> getString(R.string.anomaly_type_newapp)
        "OFFLINE" -> getString(R.string.anomaly_type_offline)
        "UNINSTALL_ATTEMPT" -> getString(R.string.anomaly_type_uninstall)
        "UPDATE_AVAILABLE" -> getString(R.string.anomaly_type_update_available)
        "UPDATE_CHECK_FAILED" -> getString(R.string.anomaly_type_update_failed)
        "ACCESSIBILITY_DISABLED" -> getString(R.string.anomaly_type_accessibility_disabled)
        "ACCESSIBILITY_DISCONNECTED" -> getString(R.string.anomaly_type_accessibility_disconnected)
        "DEVICE_OWNER_MISSING" -> getString(R.string.anomaly_type_device_owner_missing)
        "UNINSTALL_PROTECTION_MISSING" -> getString(R.string.anomaly_type_uninstall_protection_missing)
        "ADMIN_DISABLE_ATTEMPT" -> getString(R.string.anomaly_type_admin_disable_attempt)
        "ACCESSIBILITY_DISABLE_ATTEMPT" -> getString(R.string.anomaly_type_accessibility_disable_attempt)
        "USAGE_ACCESS_DISABLE_ATTEMPT" -> getString(R.string.anomaly_type_usage_access_attempt)
        "OVERLAY_DISABLE_ATTEMPT" -> getString(R.string.anomaly_type_overlay_attempt)
        "AUTOSTART_DISABLE_ATTEMPT" -> getString(R.string.anomaly_type_autostart_attempt)
        "OVERLAY_PERMISSION_DISABLED" -> getString(R.string.anomaly_type_overlay_disabled)
        "BATTERY_OPTIMIZATION_ENABLED" -> getString(R.string.anomaly_type_battery_optimization)
        "AUTOSTART_NOT_CONFIRMED" -> getString(R.string.anomaly_type_autostart_unconfirmed)
        "RULE_NOT_APPLIED" -> getString(R.string.anomaly_type_rule_not_applied)
        "NOTIFICATION_PERMISSION_DISABLED" -> getString(R.string.anomaly_type_notification_disabled)
        "LOW_STORAGE" -> getString(R.string.anomaly_type_low_storage)
        else -> type
    }

    private fun severityName(severity: AnomalySeverity): String = when (severity) {
        AnomalySeverity.HIGH -> getString(R.string.anomaly_severity_high)
        AnomalySeverity.MEDIUM -> getString(R.string.anomaly_severity_medium)
        AnomalySeverity.LOW -> getString(R.string.anomaly_severity_low)
    }

    private fun statusName(status: IncidentStatus): String = when (status) {
        IncidentStatus.OPEN -> getString(R.string.anomaly_status_open)
        IncidentStatus.ACKNOWLEDGED -> getString(R.string.anomaly_status_acknowledged)
        IncidentStatus.RESOLVED -> getString(R.string.anomaly_status_resolved)
    }
}
