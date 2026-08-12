package com.familyguard.admin

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.familyguard.admin.databinding.ActivityRulesBinding
import com.familyguard.core.backend.CloudBaseApps
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.backend.CloudBaseRules
import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch

/** 规则管理页：卡片分区（每日总额 / 分类限时 / 按应用限时卡片列表）。 */
class RulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRulesBinding
    private lateinit var adapter: RuleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = RuleAdapter(
            onPickApp = { row -> showAppPicker(row) },
            onRemove = { _ -> },
        )
        binding.rvAppLimits.layoutManager = LinearLayoutManager(this)
        binding.rvAppLimits.adapter = adapter

        binding.btnAddAppLimit.setOnClickListener { addRow() }
        binding.btnSave.setOnClickListener { saveRules() }
        binding.btnLoad.setOnClickListener { loadRules() }

        addRow()
        loadRules()
    }

    private fun addRow() {
        adapter.rows.add(AppLimitRowData())
        adapter.notifyItemInserted(adapter.rows.size - 1)
    }

    /** 从被控端已装应用选择（点击包名输入框触发）。 */
    private fun showAppPicker(row: AppLimitRowData) {
        val uid = SessionStore.userId ?: return
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            if (kidId == null) return@launch
            val apps = CloudBaseApps.fetch(AdminApp.client, kidId)
            if (apps.isEmpty()) {
                Toast.makeText(this@RulesActivity, R.string.rules_pick_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val names = apps.map { "${it.second}（${it.first}）" }
            android.app.AlertDialog.Builder(this@RulesActivity)
                .setTitle(R.string.rules_pick_app)
                .setItems(names.toTypedArray()) { _, which ->
                    row.packageName = apps[which].first
                    row.appName = apps[which].second
                    val idx = adapter.rows.indexOf(row)
                    if (idx >= 0) adapter.notifyItemChanged(idx)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun saveRules() {
        val uid = SessionStore.userId ?: return
        val appLimits = adapter.rows.mapNotNull { row ->
            if (!PACKAGE_NAME_REGEX.matches(row.packageName)) return@mapNotNull null
            if (row.minutes <= 0) return@mapNotNull null
            AppLimit(row.packageName, row.category, dailyMinutes = row.minutes)
        }
        val categoryLimits = listOf(
            AppCategory.GAME to binding.etGameMinutes,
            AppCategory.SHORT_VIDEO to binding.etShortVideoMinutes,
            AppCategory.VIDEO to binding.etVideoMinutes,
            AppCategory.SOCIAL to binding.etSocialMinutes,
        ).mapNotNull { (cat, et) ->
            val m = et.text?.toString()?.trim().orEmpty().toIntOrNull() ?: 0
            if (m <= 0) null else CategoryLimit(cat, m)
        }
        val total = binding.etTotalMinutes.text?.toString()?.trim().orEmpty().toIntOrNull() ?: 0
        val ruleSet = RuleSet(
            appLimits = appLimits,
            categoryLimits = categoryLimits,
            dailyTotal = DailyTotalLimit(total),
            version = System.currentTimeMillis(),
        )
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            val ok = CloudBaseRules.saveRules(AdminApp.client, uid, ruleSet)
            binding.btnSave.isEnabled = true
            Toast.makeText(
                this@RulesActivity,
                if (ok) R.string.rules_saved else R.string.rules_save_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun loadRules() {
        val uid = SessionStore.userId ?: return
        lifecycleScope.launch {
            val rules = CloudBaseRules.fetchRules(AdminApp.client, uid) ?: return@launch
            binding.etTotalMinutes.setText(if (rules.dailyTotal.totalMinutes > 0) rules.dailyTotal.totalMinutes.toString() else "")
            rules.categoryLimits.forEach { cl ->
                val et = when (cl.category) {
                    AppCategory.GAME -> binding.etGameMinutes
                    AppCategory.SHORT_VIDEO -> binding.etShortVideoMinutes
                    AppCategory.VIDEO -> binding.etVideoMinutes
                    AppCategory.SOCIAL -> binding.etSocialMinutes
                    else -> null
                }
                if (et != null && cl.dailyMinutes > 0) et.setText(cl.dailyMinutes.toString())
            }
            adapter.rows.clear()
            if (rules.appLimits.isEmpty()) {
                adapter.rows.add(AppLimitRowData())
            } else {
                rules.appLimits.forEach { al ->
                    adapter.rows.add(
                        AppLimitRowData(
                            packageName = al.packageName,
                            minutes = al.dailyMinutes,
                            category = al.category,
                        ),
                    )
                }
            }
            adapter.notifyDataSetChanged()
        }
    }

    companion object {
        /** 合法包名：小写字母/数字/下划线/点，至少两段。 */
        private val PACKAGE_NAME_REGEX = Regex("^[a-z][a-z0-9_]*\\.[a-z0-9_.]+$")
    }
}
