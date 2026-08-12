package com.familyguard.admin

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.admin.databinding.ActivityRulesBinding
import com.familyguard.core.backend.CloudBaseRules
import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch

/** 规则管理页：app 限时 / 分类限时 / 每日总额，保存后云端下发。 */
class RulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRulesBinding
    private val appLimitRows = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAddAppLimit.setOnClickListener { addAppLimitRow() }
        binding.btnSave.setOnClickListener { saveRules() }
        binding.btnLoad.setOnClickListener { loadRules() }

        addAppLimitRow()
        loadRules()
    }

    private fun addAppLimitRow() {
        val row = layoutInflater.inflate(R.layout.item_app_limit, binding.containerAppLimits, false)
        binding.containerAppLimits.addView(row)
        appLimitRows.add(row)
    }

    /** 供 item_app_limit 的 android:onClick 调用。 */
    fun removeAppLimitRowClick(view: View) {
        val row = view.parent as? View ?: return
        removeAppLimitRow(row)
    }

    private fun removeAppLimitRow(row: View) {
        binding.containerAppLimits.removeView(row)
        appLimitRows.remove(row)
    }

    private fun saveRules() {
        val uid = SessionStore.userId ?: return
        val appLimits = appLimitRows.mapNotNull { row ->
            val pkg = row.findViewById<EditText>(R.id.etPkg).text?.toString()?.trim().orEmpty()
            val minutes = row.findViewById<EditText>(R.id.etMinutes).text?.toString()?.trim()
                .orEmpty().toIntOrNull() ?: 0
            if (pkg.isBlank() || minutes <= 0) null
            else AppLimit(pkg, AppCategory.OTHER, dailyMinutes = minutes)
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
            // 每日总额
            binding.etTotalMinutes.setText(if (rules.dailyTotal.totalMinutes > 0) rules.dailyTotal.totalMinutes.toString() else "")
            // 分类限时
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
            // app 限时
            binding.containerAppLimits.removeAllViews()
            appLimitRows.clear()
            if (rules.appLimits.isEmpty()) {
                addAppLimitRow()
            } else {
                rules.appLimits.forEach { al ->
                    val row = layoutInflater.inflate(R.layout.item_app_limit, binding.containerAppLimits, false)
                    row.findViewById<EditText>(R.id.etPkg).setText(al.packageName)
                    row.findViewById<EditText>(R.id.etMinutes).setText(al.dailyMinutes.toString())
                    binding.containerAppLimits.addView(row)
                    appLimitRows.add(row)
                }
            }
        }
    }
}
