package com.familyguard.core.session

import android.content.Context
import android.content.SharedPreferences
import com.familyguard.core.data.RuleSet
import com.familyguard.core.data.RuleSetEnvelope
import com.familyguard.core.rules.RuleEnvelopeCodec
import com.familyguard.core.rules.RuleCodec
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 规则本地缓存（被控端）：拦截服务读取用，断网时兜底旧规则。
 */
object RuleCacheStore {

    private const val PREFS_NAME = "familyguard_rule_cache"
    private const val KEY_RULES = "rules_json"
    private const val KEY_UPDATED_AT = "updated_at"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun save(rules: RuleSet) {
        prefs.edit()
            .putString(KEY_RULES, RuleCodec.toJson(rules))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun save(envelope: RuleSetEnvelope) {
        prefs.edit()
            .putString(KEY_RULES, RuleEnvelopeCodec.toJson(envelope))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    val envelope: RuleSetEnvelope?
        get() = prefs.getString(KEY_RULES, null)?.let(RuleEnvelopeCodec::fromJson)

    val rules: RuleSet?
        get() = rulesAt(System.currentTimeMillis())

    fun rulesAt(nowMs: Long): RuleSet? {
        val current = envelope ?: return null
        val zone = runCatching { ZoneId.of(current.timezoneId) }.getOrDefault(ZoneId.of("Asia/Shanghai"))
        val dateTime = Instant.ofEpochMilli(nowMs).atZone(zone)
        return current.rulesFor(dateTime.toLocalDate(), dateTime.toLocalTime())
    }

    val updatedAt: Long get() = prefs.getLong(KEY_UPDATED_AT, 0L)
}
