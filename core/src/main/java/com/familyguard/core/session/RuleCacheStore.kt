package com.familyguard.core.session

import android.content.Context
import android.content.SharedPreferences
import com.familyguard.core.data.RuleSet
import com.familyguard.core.rules.RuleCodec

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

    val rules: RuleSet?
        get() = prefs.getString(KEY_RULES, null)?.let { RuleCodec.fromJson(it) }

    val updatedAt: Long get() = prefs.getLong(KEY_UPDATED_AT, 0L)
}
