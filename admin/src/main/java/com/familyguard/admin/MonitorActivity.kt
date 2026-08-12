package com.familyguard.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.admin.databinding.ActivityMonitorBinding
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.backend.CloudBaseUsage
import com.familyguard.core.categories.AppCategory
import com.familyguard.core.session.SessionStore
import com.familyguard.core.stats.UsageAggregator
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
        binding.tvReport.text = ""
        binding.btnRefresh.isEnabled = false
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            if (kidId == null) {
                binding.tvStatus.text = getString(R.string.monitor_not_bound)
                binding.btnRefresh.isEnabled = true
                return@launch
            }
            val snapshot = CloudBaseUsage.fetchLatest(AdminApp.client, kidId)
            if (snapshot == null) {
                binding.tvStatus.text = getString(R.string.monitor_no_data)
                binding.btnRefresh.isEnabled = true
                return@launch
            }
            // 在线状态（5 分钟阈值）
            val online = UsageAggregator.isOnline(snapshot.reportedAt, System.currentTimeMillis())
            val current = snapshot.currentApp
            binding.tvStatus.text = getString(
                R.string.monitor_status,
                if (online) getString(R.string.monitor_online) else getString(R.string.monitor_offline),
                current ?: getString(R.string.monitor_no_app),
                snapshot.date,
            )
            // 报告
            val sb = StringBuilder()
            sb.append(getString(R.string.monitor_total)).append(snapshot.totalMinutes).append(getString(R.string.unit_minutes)).append('\n')
            val sorted = snapshot.byPackage.entries.sortedByDescending { it.value }
            sorted.forEach { (pkg, min) ->
                sb.append("  ").append(pkg).append(": ").append(min).append(getString(R.string.unit_minutes)).append('\n')
            }
            val byCategory = UsageAggregator.categoryMinutes(snapshot.byPackage)
            if (byCategory.isNotEmpty()) {
                sb.append(getString(R.string.monitor_by_category)).append('\n')
                AppCategory.entries.forEach { cat ->
                    byCategory[cat]?.let { sb.append("  ").append(categoryName(cat)).append(": ").append(it).append(getString(R.string.unit_minutes)).append('\n') }
                }
            }
            binding.tvReport.text = sb.toString()
            binding.tvReport.visibility = View.VISIBLE
            binding.btnRefresh.isEnabled = true
        }
    }

    private fun categoryName(c: AppCategory): String = when (c) {
        AppCategory.GAME -> getString(R.string.cat_game)
        AppCategory.SHORT_VIDEO -> getString(R.string.cat_short_video)
        AppCategory.VIDEO -> getString(R.string.cat_video)
        AppCategory.SOCIAL -> getString(R.string.cat_social)
        AppCategory.TOOL -> getString(R.string.cat_tool)
        AppCategory.OTHER -> getString(R.string.cat_other)
    }
}
