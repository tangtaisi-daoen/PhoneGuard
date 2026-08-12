package com.familyguard.kid.stats

import android.content.Context
import android.content.SharedPreferences

/** 心跳状态（保活 Worker 判断服务是否存活用）。 */
object HeartbeatState {

    private const val PREFS_NAME = "familyguard_heartbeat"
    private const val KEY_LAST = "last_heartbeat_at"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** 心跳成功后记录时间。 */
    fun touch() {
        prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
    }

    /** 最近一次心跳时间；从未心跳返回 0。 */
    val lastHeartbeatAt: Long get() = prefs.getLong(KEY_LAST, 0L)
}
