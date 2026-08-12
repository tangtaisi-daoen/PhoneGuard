package com.familyguard.admin

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
        row.findViewById<EditText>(R.id.etPkg).setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showAppPicker(row)
        }
    }

    /** 从被控端已装应用里选择（点击包名输入框触发）。 */
    private fun showAppPicker(row: View) {
        val uid = SessionStore.userId ?: return
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            if (kidId == null) return@launch
            val apps = CloudBaseApps.fetch(AdminApp.client, kidId)
            if (apps.isEmpty()) return@launch
            val names = apps.map { "${it.second}（${it.first}）" }
            android.app.AlertDialog.Builder(this@RulesActivity)
                .setTitle(R.string.rules_pick_app)
                .setItems(names.toTypedArray()) { _, which ->
                    row.findViewById<EditText>(R.id.etPkg).setText(apps[which].first)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
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
        // 包名校验：应用名（中文）不是包名，提示用户
        val badRows = appLimitRows.filter { row ->
            val pkg = row.findViewById<EditText>(R.id.etPkg).text?.toString()?.trim().orEmpty()
            pkg.isNotEmpty() && !PACKAGE_NAME_REGEX.matches(pkg)
        }
        if (badRows.isNotEmpty()) {
            Toast.makeText(this, R.string.rules_pkg_invalid, Toast.LENGTH_LONG).show()
            return
        }
        val appLimits = appLimitRows.mapNotNull { row ->
            val pkg = row.findViewById<EditText>(R.id.etPkg).text?.toString()?.trim().orEmpty()
            val minutes = row.findViewById<EditText>(R.id.etMinutes).text?.toString()?.trim()
                .orEmpty().toIntOrNull() ?: 0
            if (pkg.isBlank() || minutes <= 0) null
            else AppLimit(pkg, categoryOf(row), dailyMinutes = minutes)
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

    /** 从 Spinner 读取手动类别；「自动」= 按内置表（OTHER 交给被控端分类）。 */
    private fun categoryOf(row: View): AppCategory {
        val spinner = row.findViewById<android.widget.Spinner>(R.id.spCategory) ?: return AppCategory.OTHER
        return when (spinner.selectedItemPosition) {
            1 -> AppCategory.GAME
            2 -> AppCategory.SHORT_VIDEO
            3 -> AppCategory.SOCIAL
            else -> AppCategory.OTHER // 0=自动, 4=不分类
        }
    }

    private fun setCategoryOf(row: View, category: AppCategory?) {
        val spinner = row.findViewById<android.widget.Spinner>(R.id.spCategory) ?: return
        spinner.setSelection(
            when (category) {
                AppCategory.GAME -> 1
                AppCategory.SHORT_VIDEO -> 2
                AppCategory.SOCIAL -> 3
                else -> 0
            },
        )
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
                    setCategoryOf(row, al.category.takeIf { it != AppCategory.OTHER })
                    binding.containerAppLimits.addView(row)
                    appLimitRows.add(row)
                }
            }
        }
    }

    companion object {
        /** 合法包名：小写字母/数字/下划线/点，至少两段。 */
        private val PACKAGE_NAME_REGEX = Regex("^[a-z][a-z0-9_]*\\.[a-z0-9_.]+$")
    }
}
