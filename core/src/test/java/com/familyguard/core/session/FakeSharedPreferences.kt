package com.familyguard.core.session

import android.content.SharedPreferences

/**
 * 测试用内存 SharedPreferences 实现（纯 JVM，不依赖 Android 框架）。
 * 仅覆盖本项目用到的语义：get/put/remove/clear/commit/apply/getAll。
 */
class FakeSharedPreferences : SharedPreferences {

    private val map = linkedMapOf<String, Any?>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (map[key] as? Set<String>)?.let { HashSet(it) } ?: defValues

    override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class FakeEditor : SharedPreferences.Editor {

        private val pending = linkedMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = apply { pending[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { pending[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { removed.add(key) }
        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
        override fun commit(): Boolean {
            if (clearAll) map.clear()
            removed.forEach { map.remove(it) }
            map.putAll(pending)
            pending.clear()
            removed.clear()
            clearAll = false
            listeners.forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, "") }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
