package com.familyguard.core.session

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * SessionStore 迁移与读写逻辑测试（纯 JVM，内存 SharedPreferences 实现）。
 *
 * 说明：加密本身由 androidx.security-crypto（EncryptedSharedPreferences）承担，
 * 本测试验证的是字段读写、迁移、幂等与清理逻辑；生产初始化走 [SessionStore.init]。
 */
class SessionStoreTest {

    @Before
    fun setUp() {
        SessionStore.resetForTest()
    }

    @After
    fun tearDown() {
        SessionStore.resetForTest()
    }

    @Test
    fun `saveAuth 后可从存储读回全部字段`() {
        SessionStore.initWith(FakeSharedPreferences())
        SessionStore.saveAuth("tok-1", "rt-1", "uid-9", "alice")

        assertEquals("tok-1", SessionStore.accessToken)
        assertEquals("rt-1", SessionStore.refreshToken)
        assertEquals("uid-9", SessionStore.userId)
        assertEquals("alice", SessionStore.username)
        assertTrue(SessionStore.isLoggedIn)
    }

    @Test
    fun `saveTokens 刷新不覆盖用户信息`() {
        SessionStore.initWith(FakeSharedPreferences())
        SessionStore.saveAuth("tok-1", "rt-1", "uid-9", "alice")
        SessionStore.saveTokens("tok-2", "rt-2")

        assertEquals("tok-2", SessionStore.accessToken)
        assertEquals("rt-2", SessionStore.refreshToken)
        assertEquals("uid-9", SessionStore.userId)
        assertEquals("alice", SessionStore.username)
    }

    @Test
    fun `从旧版明文 prefs 迁移全部字段`() {
        val legacy = FakeSharedPreferences().apply {
            edit()
                .putString("access_token", "legacy-token")
                .putString("refresh_token", "legacy-rt")
                .putString("user_id", "legacy-uid")
                .putString("username", "legacy-user")
                .putString("device_id", "legacy-device")
                .commit()
        }
        val secure = FakeSharedPreferences()

        assertTrue(SessionStore.migrateLegacy(legacy, secure))

        assertEquals("legacy-token", secure.getString("access_token", null))
        assertEquals("legacy-rt", secure.getString("refresh_token", null))
        assertEquals("legacy-uid", secure.getString("user_id", null))
        assertEquals("legacy-user", secure.getString("username", null))
        assertEquals("legacy-device", secure.getString("device_id", null))
        assertTrue(secure.getBoolean("migrated_v2", false))
    }

    @Test
    fun `迁移后 SessionStore 可直接读到迁移数据`() {
        val legacy = FakeSharedPreferences().apply {
            edit().putString("access_token", "legacy-token").putString("device_id", "legacy-device").commit()
        }
        val secure = FakeSharedPreferences()
        SessionStore.initWith(secure)
        SessionStore.migrateLegacy(legacy, secure)

        assertEquals("legacy-token", SessionStore.accessToken)
        assertEquals("legacy-device", SessionStore.deviceId)
    }

    @Test
    fun `迁移幂等：重复迁移不丢数据`() {
        val legacy = FakeSharedPreferences().apply {
            edit().putString("access_token", "tok").commit()
        }
        val secure = FakeSharedPreferences()

        assertTrue(SessionStore.migrateLegacy(legacy, secure))
        assertTrue(SessionStore.migrateLegacy(legacy, secure))
        assertEquals("tok", secure.getString("access_token", null))
    }

    @Test
    fun `空 legacy 迁移成功并写入完成标志`() {
        val secure = FakeSharedPreferences()
        assertTrue(SessionStore.migrateLegacy(FakeSharedPreferences(), secure))
        assertTrue(secure.getBoolean("migrated_v2", false))
    }

    @Test
    fun `重复 init 幂等且不丢数据`() {
        val secure = FakeSharedPreferences()
        SessionStore.initWith(secure)
        SessionStore.saveBinding("123456", "admin-1")
        SessionStore.initWith(FakeSharedPreferences())

        assertEquals("123456", SessionStore.inviteCode)
        assertEquals("admin-1", SessionStore.boundAdminUid)
        assertTrue(SessionStore.isBound)
    }

    @Test
    fun `clearAuth 清除会话但保留被控端绑定`() {
        SessionStore.initWith(FakeSharedPreferences())
        SessionStore.saveAuth("tok-1", "rt-1", "uid-9", "alice")
        SessionStore.saveBinding("123456", "admin-1")

        SessionStore.clearAuth()

        assertNull(SessionStore.accessToken)
        assertNull(SessionStore.userId)
        assertFalse(SessionStore.isLoggedIn)
        assertEquals("123456", SessionStore.inviteCode)
        assertEquals("admin-1", SessionStore.boundAdminUid)
    }

    @Test
    fun `deviceId 首次生成后保持稳定`() {
        SessionStore.initWith(FakeSharedPreferences())
        val first = SessionStore.deviceId
        val second = SessionStore.deviceId
        assertEquals(first, second)
        assertEquals(16, first.length)
    }
}
