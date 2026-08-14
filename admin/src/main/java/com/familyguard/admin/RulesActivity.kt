package com.familyguard.admin

import android.os.Bundle
import android.app.DatePickerDialog
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.familyguard.admin.databinding.ActivityRulesBinding
import com.familyguard.core.backend.CloudBaseApps
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.backend.CloudBaseRules
import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.RuleProfile
import com.familyguard.core.data.RuleSetEnvelope
import com.familyguard.core.data.DateOverride
import com.familyguard.core.data.TemporaryAllowance
import com.familyguard.core.rules.dateOverridesForRange
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 规则管理页：卡片分区（每日总额 / 分类限时 / 按应用限时卡片列表）。 */
class RulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRulesBinding
    private lateinit var adapter: RuleAdapter
    private var pickerLoading = false
    private var knownApps: Map<String, String> = emptyMap()
    private var selectedProfile = RuleProfile.WEEKDAY
    private val profileDrafts = mutableMapOf<RuleProfile, ProfileDraft>()
    private val dateOverrides = mutableListOf<DateOverride>()
    private val temporaryAllowances = mutableListOf<TemporaryAllowance>()
    private var changingProfile = false

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

        binding.btnAddAppLimit.setOnClickListener { addRowAndPickApp() }
        binding.btnSave.setOnClickListener { saveRules() }
        binding.btnLoad.setOnClickListener { loadRules() }

        binding.profileToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || changingProfile) return@addOnButtonCheckedListener
            val profile = when (checkedId) {
                R.id.btnWeekendProfile -> RuleProfile.WEEKEND
                R.id.btnHolidayProfile -> RuleProfile.HOLIDAY
                else -> RuleProfile.WEEKDAY
            }
            switchProfile(profile)
        }
        binding.btnAddHoliday.setOnClickListener { pickHolidayRange() }
        binding.btnAddWorkday.setOnClickListener { pickDate(RuleProfile.WEEKDAY) }
        binding.btnClearDates.setOnClickListener {
            dateOverrides.clear()
            renderDateOverrides()
        }
        binding.btnAddTemporaryAllowance.setOnClickListener { showTemporaryAllowancePicker() }
        binding.btnClearTemporaryAllowances.setOnClickListener {
            temporaryAllowances.clear()
            renderTemporaryAllowances()
        }
        binding.btnCopyWeekday.setOnClickListener {
            profileDrafts[selectedProfile] = profileDrafts.getValue(RuleProfile.WEEKDAY).deepCopy()
            applyDraft(profileDrafts.getValue(selectedProfile))
            Toast.makeText(this, R.string.rules_copied_weekday, Toast.LENGTH_SHORT).show()
        }

        addRow()
        RuleProfile.entries.forEach { profileDrafts[it] = emptyDraft() }
        loadRules()
    }

    private fun addRow() {
        adapter.rows.add(AppLimitRowData())
        adapter.notifyItemInserted(adapter.rows.size - 1)
    }

    private fun addRowAndPickApp() {
        val selection = chooseRowForNewLimit(adapter.rows)
        if (selection.inserted) adapter.notifyItemInserted(selection.position)
        binding.rvAppLimits.post {
            binding.rvAppLimits.scrollToPosition(selection.position)
            showAppPicker(selection.row)
        }
    }

    /** 从被控端已装应用选择（点击包名输入框触发）。 */
    private fun showAppPicker(row: AppLimitRowData) {
        val uid = SessionStore.userId
        if (uid == null) {
            Toast.makeText(this, R.string.rules_pick_not_logged_in, Toast.LENGTH_SHORT).show()
            return
        }
        if (pickerLoading) return
        pickerLoading = true
        Toast.makeText(this, R.string.rules_pick_loading, Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            if (kidId == null) {
                pickerLoading = false
                Toast.makeText(this@RulesActivity, R.string.rules_pick_not_bound, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val apps = CloudBaseApps.fetchOrNull(AdminApp.client, kidId, adminUid = uid)
            pickerLoading = false
            if (apps == null) {
                Toast.makeText(this@RulesActivity, R.string.rules_pick_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (apps.isEmpty()) {
                Toast.makeText(this@RulesActivity, R.string.rules_pick_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            knownApps = apps.toMap()
            val names = apps.map { it.second }
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
        profileDrafts[selectedProfile] = captureDraft()
        val invalidProfile = RuleProfile.entries.firstOrNull { profile ->
            profileDrafts.getValue(profile).rows.any { row ->
                row.packageName.isNotBlank() && row.toAppLimitOrNull() == null
            }
        }
        if (invalidProfile != null) {
            selectProfile(invalidProfile)
            Toast.makeText(this, R.string.rules_invalid_row, Toast.LENGTH_SHORT).show()
            return
        }
        val revision = System.currentTimeMillis()
        val envelope = RuleSetEnvelope(
            revision = revision,
            timezoneId = "Asia/Shanghai",
            weekdayProfile = profileDrafts.getValue(RuleProfile.WEEKDAY).toRuleSet(revision),
            weekendProfile = profileDrafts.getValue(RuleProfile.WEEKEND).toRuleSet(revision),
            holidayProfile = profileDrafts.getValue(RuleProfile.HOLIDAY).toRuleSet(revision),
            dateOverrides = dateOverrides.sortedBy(DateOverride::localDate),
            temporaryAllowances = temporaryAllowances
                .filter { it.expiresAt > System.currentTimeMillis() }
                .sortedBy(TemporaryAllowance::packageName),
            effectiveAt = revision,
            generatedAt = revision,
        )
        binding.btnSave.isEnabled = false
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            val ok = CloudBaseRules.saveEnvelope(AdminApp.client, uid, envelope, kidDeviceId = kidId)
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
            val envelope = CloudBaseRules.fetchEnvelope(AdminApp.client, uid) ?: return@launch
            profileDrafts[RuleProfile.WEEKDAY] = envelope.weekdayProfile.toDraft()
            profileDrafts[RuleProfile.WEEKEND] = envelope.weekendProfile.toDraft()
            profileDrafts[RuleProfile.HOLIDAY] = envelope.holidayProfile.toDraft()
            dateOverrides.clear()
            dateOverrides.addAll(envelope.dateOverrides)
            temporaryAllowances.clear()
            temporaryAllowances.addAll(envelope.temporaryAllowances.filter { it.expiresAt > System.currentTimeMillis() })
            renderDateOverrides()
            renderTemporaryAllowances()
            applyDraft(profileDrafts.getValue(selectedProfile))
            refreshKnownAppNames(uid)
        }
    }

    private fun switchProfile(profile: RuleProfile) {
        profileDrafts[selectedProfile] = captureDraft()
        selectedProfile = profile
        applyDraft(profileDrafts.getValue(profile))
        binding.tvProfileHint.setText(
            when (profile) {
                RuleProfile.WEEKDAY -> R.string.rules_profile_weekday_hint
                RuleProfile.WEEKEND -> R.string.rules_profile_weekend_hint
                RuleProfile.HOLIDAY -> R.string.rules_profile_holiday_hint
            },
        )
        binding.btnCopyWeekday.visibility =
            if (profile == RuleProfile.WEEKDAY) View.GONE else View.VISIBLE
    }

    private fun selectProfile(profile: RuleProfile) {
        changingProfile = true
        binding.profileToggle.check(
            when (profile) {
                RuleProfile.WEEKDAY -> R.id.btnWeekdayProfile
                RuleProfile.WEEKEND -> R.id.btnWeekendProfile
                RuleProfile.HOLIDAY -> R.id.btnHolidayProfile
            },
        )
        changingProfile = false
        switchProfile(profile)
    }

    private fun captureDraft(): ProfileDraft = ProfileDraft(
        totalMinutes = binding.etTotalMinutes.text?.toString()?.trim().orEmpty().toIntOrNull() ?: 0,
        gameMinutes = binding.etGameMinutes.text?.toString()?.trim().orEmpty().toIntOrNull() ?: 0,
        shortVideoMinutes = binding.etShortVideoMinutes.text?.toString()?.trim().orEmpty().toIntOrNull() ?: 0,
        videoMinutes = binding.etVideoMinutes.text?.toString()?.trim().orEmpty().toIntOrNull() ?: 0,
        socialMinutes = binding.etSocialMinutes.text?.toString()?.trim().orEmpty().toIntOrNull() ?: 0,
        rows = adapter.rows.map(AppLimitRowData::copy),
    )

    private fun applyDraft(draft: ProfileDraft) {
        binding.etTotalMinutes.setText(draft.totalMinutes.positiveText())
        binding.etGameMinutes.setText(draft.gameMinutes.positiveText())
        binding.etShortVideoMinutes.setText(draft.shortVideoMinutes.positiveText())
        binding.etVideoMinutes.setText(draft.videoMinutes.positiveText())
        binding.etSocialMinutes.setText(draft.socialMinutes.positiveText())
        adapter.rows.clear()
        adapter.rows.addAll(draft.rows.map(AppLimitRowData::copy).ifEmpty { listOf(AppLimitRowData()) })
        adapter.notifyDataSetChanged()
    }

    private fun emptyDraft() = ProfileDraft(rows = listOf(AppLimitRowData()))

    private fun RuleSet.toDraft() = ProfileDraft(
        totalMinutes = dailyTotal.totalMinutes,
        gameMinutes = categoryMinutes(AppCategory.GAME),
        shortVideoMinutes = categoryMinutes(AppCategory.SHORT_VIDEO),
        videoMinutes = categoryMinutes(AppCategory.VIDEO),
        socialMinutes = categoryMinutes(AppCategory.SOCIAL),
        rows = appLimits.map { limit ->
            AppLimitRowData(
                packageName = limit.packageName,
                appName = knownApps[limit.packageName].orEmpty(),
                minutes = limit.dailyMinutes,
                category = limit.category,
            )
        }.ifEmpty { listOf(AppLimitRowData()) },
    )

    private fun RuleSet.categoryMinutes(category: AppCategory): Int =
        categoryLimits.firstOrNull { it.category == category }?.dailyMinutes ?: 0

    private fun pickDate(profile: RuleProfile) {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val date = LocalDate.of(year, month + 1, day).toString()
                dateOverrides.removeAll { it.localDate == date }
                dateOverrides += DateOverride(
                    localDate = date,
                    profile = profile,
                    label = getString(
                        if (profile == RuleProfile.HOLIDAY) R.string.rules_date_holiday
                        else R.string.rules_date_workday,
                    ),
                )
                renderDateOverrides()
            },
            today.year,
            today.monthValue - 1,
            today.dayOfMonth,
        ).show()
    }

    private fun pickHolidayRange() {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        showDatePicker(today, R.string.rules_holiday_start) { start ->
            showDatePicker(start, R.string.rules_holiday_end) { end ->
                if (end.isBefore(start)) {
                    Toast.makeText(this, R.string.rules_holiday_invalid_range, Toast.LENGTH_SHORT).show()
                    return@showDatePicker
                }
                val additions = dateOverridesForRange(
                    start,
                    end,
                    RuleProfile.HOLIDAY,
                    getString(R.string.rules_date_holiday),
                )
                val dates = additions.mapTo(mutableSetOf(), DateOverride::localDate)
                dateOverrides.removeAll { it.localDate in dates }
                dateOverrides.addAll(additions)
                renderDateOverrides()
            }
        }
    }

    private fun showDatePicker(initial: LocalDate, titleRes: Int, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
        ).apply { setTitle(titleRes) }.show()
    }

    private fun showTemporaryAllowancePicker() {
        if (knownApps.isNotEmpty()) {
            showTemporaryAppDialog(knownApps.entries.map { it.key to it.value })
            return
        }
        val uid = SessionStore.userId ?: return
        if (pickerLoading) return
        pickerLoading = true
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid)
            val apps = kidId?.let { CloudBaseApps.fetchOrNull(AdminApp.client, it, adminUid = uid) }
            pickerLoading = false
            if (apps.isNullOrEmpty()) {
                Toast.makeText(this@RulesActivity, R.string.rules_pick_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            knownApps = apps.toMap()
            showTemporaryAppDialog(apps)
        }
    }

    private fun showTemporaryAppDialog(apps: List<Pair<String, String>>) {
        val sorted = apps.sortedBy { it.second }
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.rules_pick_app)
            .setItems(sorted.map { it.second }.toTypedArray()) { _, which ->
                val (packageName, appName) = sorted[which]
                val choices = intArrayOf(15, 30, 60, 120)
                android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.rules_temporary_pick_minutes)
                    .setItems(choices.map { getString(R.string.rules_temporary_minutes, it) }.toTypedArray()) { _, index ->
                        val now = System.currentTimeMillis()
                        val zone = ZoneId.of("Asia/Shanghai")
                        val expiresAt = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        temporaryAllowances.removeAll { it.packageName == packageName }
                        temporaryAllowances += TemporaryAllowance(
                            packageName = packageName,
                            extraMinutes = choices[index],
                            expiresAt = expiresAt,
                            reason = getString(R.string.rules_temporary_reason),
                            createdAt = now,
                        )
                        knownApps = knownApps + (packageName to appName)
                        renderTemporaryAllowances()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderDateOverrides() {
        binding.tvDateOverrides.text = if (dateOverrides.isEmpty()) {
            getString(R.string.rules_dates_empty)
        } else {
            dateOverrides.sortedBy(DateOverride::localDate).joinToString("\n") { item ->
                "${item.localDate} · ${item.label}"
            }
        }
    }

    private fun renderTemporaryAllowances() {
        val now = System.currentTimeMillis()
        temporaryAllowances.removeAll { it.expiresAt <= now }
        val format = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
        binding.tvTemporaryAllowances.text = temporaryAllowances
            .sortedBy(TemporaryAllowance::expiresAt)
            .joinToString("\n") { item ->
                getString(
                    R.string.rules_temporary_row,
                    knownApps[item.packageName] ?: item.packageName,
                    item.extraMinutes,
                    format.format(java.util.Date(item.expiresAt)),
                )
            }
            .ifBlank { getString(R.string.rules_temporary_empty) }
    }

    /** 后台补全已保存规则的应用中文名，不阻塞规则显示。 */
    private fun refreshKnownAppNames(uid: String) {
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid) ?: return@launch
            val apps = CloudBaseApps.fetchOrNull(AdminApp.client, kidId, adminUid = uid) ?: return@launch
            knownApps = apps.toMap()
            var changed = false
            adapter.rows.forEach { row ->
                val name = knownApps[row.packageName].orEmpty()
                if (name.isNotBlank() && name != row.appName) {
                    row.appName = name
                    changed = true
                }
            }
            if (changed) adapter.notifyDataSetChanged()
            renderTemporaryAllowances()
            RuleProfile.entries.forEach { profile ->
                val draft = profileDrafts.getValue(profile)
                profileDrafts[profile] = draft.copy(
                    rows = draft.rows.map { row ->
                        row.copy(appName = knownApps[row.packageName].orEmpty().ifBlank { row.appName })
                    },
                )
            }
        }
    }

    private fun Int.positiveText(): String = if (this > 0) toString() else ""

    private data class ProfileDraft(
        val totalMinutes: Int = 0,
        val gameMinutes: Int = 0,
        val shortVideoMinutes: Int = 0,
        val videoMinutes: Int = 0,
        val socialMinutes: Int = 0,
        val rows: List<AppLimitRowData> = emptyList(),
    ) {
        fun deepCopy(): ProfileDraft = copy(rows = rows.map(AppLimitRowData::copy))

        fun toRuleSet(version: Long): RuleSet {
            val categoryLimits = listOf(
                AppCategory.GAME to gameMinutes,
                AppCategory.SHORT_VIDEO to shortVideoMinutes,
                AppCategory.VIDEO to videoMinutes,
                AppCategory.SOCIAL to socialMinutes,
            ).mapNotNull { (category, minutes) ->
                if (minutes > 0) CategoryLimit(category, minutes) else null
            }
            return RuleSet(
                appLimits = rows.mapNotNull(AppLimitRowData::toAppLimitOrNull),
                categoryLimits = categoryLimits,
                dailyTotal = DailyTotalLimit(totalMinutes),
                version = version,
            )
        }
    }
}
