package com.familyguard.admin

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.admin.databinding.ActivityAnomalyBinding
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.backend.CloudBaseEvents
import com.familyguard.core.data.AnomalyEvent
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
        load()
    }

    private fun load() {
        val uid = SessionStore.userId ?: return
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            if (kidId == null) {
                binding.tvAnomalyList.text = getString(R.string.anomaly_none)
                return@launch
            }
            val all = CloudBaseEvents.fetchAll(AdminApp.client, kidId, limit = 50)
            if (all.isEmpty()) {
                binding.tvAnomalyList.text = getString(R.string.anomaly_none)
            } else {
                val sb = StringBuilder()
                val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
                all.forEach { e ->
                    sb.append('[').append(fmt.format(Date(e.occurredAt))).append("] ")
                        .append(typeName(e.type)).append('\n')
                    sb.append("  ").append(e.message).append('\n').append('\n')
                }
                binding.tvAnomalyList.text = sb.toString()
            }
        }
    }

    private fun typeName(type: String): String = when (type) {
        "ADMIN_DISABLED" -> getString(R.string.anomaly_type_admin)
        "PERMISSION_DISABLED" -> getString(R.string.anomaly_type_permission)
        "TIME_CHANGED" -> getString(R.string.anomaly_type_time)
        "NEW_APP" -> getString(R.string.anomaly_type_newapp)
        "OFFLINE" -> getString(R.string.anomaly_type_offline)
        "UNINSTALL_ATTEMPT" -> getString(R.string.anomaly_type_uninstall)
        else -> type
    }
}
