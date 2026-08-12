package com.familyguard.core.backend

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit
import com.familyguard.core.data.CategoryLimit
import com.familyguard.core.data.DailyTotalLimit
import com.familyguard.core.data.RuleSet
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * CloudBase 接入验证（真实链路，REST API）。
 *
 * 手动执行：设置环境变量 CLOUDBASE_VERIFY=1（常规 :core:test 会自动跳过）
 *
 * 步骤：
 * 2. signin_db_roundtrip：账号密码登录 → 数据库写/读/删
 * 3. anonymousLogin：匿名登录（被控端认证基础）
 * 4. binding：邀请码生成 → 绑定 → 重复绑定拒绝
 * 5. tokenAutoRefresh：token 损坏 → 自动刷新重试
 *
 * 前置条件：控制台已开启用户名密码登录、匿名登录；集合 rules/events/bindings/usage 已创建。
 */
class CloudBaseVerifyTest {

    private lateinit var client: CloudBaseClient

    @Before
    fun setup() {
        assumeTrue("真实链路验证需设置环境变量 CLOUDBASE_VERIFY=1", CloudBaseTestConfig.manualVerify)
        assertTrue("缺少 envId（见 cloudbase.local.properties）", CloudBaseTestConfig.envId.isNotBlank())
        client = CloudBaseClient(CloudBaseTestConfig.envId)
    }

    @Test
    fun step2_signin_db_roundtrip() {
        runBlocking {
            val signin = CloudBaseAuth.signIn(client, CloudBaseTestConfig.username, CloudBaseTestConfig.password)
            if (signin == null) {
                println("登录失败原因: ${client.lastError}")
            }
            assertNotNull("账号密码登录失败", signin)
            println("登录成功: uid=${signin!!.userId} refresh=${signin.refreshToken?.take(8)}...")
            runDbRoundtrip(client)
        }
    }

    @Test
    fun step3_anonymousLogin() {
        runBlocking {
            val auth = CloudBaseAuth.signInAnonymously(client, "verify-device-${System.currentTimeMillis()}")
            assertNotNull("匿名登录失败", auth)
            println("匿名登录成功: uid=${auth!!.userId}")
            val ids = CloudBaseDb.insertDocuments(
                client, "events",
                listOf(mapOf("type" to "ANON_VERIFY", "message" to "匿名写入验证", "occurredAt" to System.currentTimeMillis())),
            )
            assertNotNull("匿名用户写入失败", ids)
            println("匿名写入成功: id=${ids!!.first()}")
            CloudBaseDb.deleteDocument(client, "events", ids.first())
            println("匿名数据已清理")
        }
    }

    @Test
    fun step4_binding() {
        runBlocking {
            // 用管理端账号登录
            val signin = CloudBaseAuth.signIn(client, CloudBaseTestConfig.username, CloudBaseTestConfig.password)
            assertNotNull("登录失败", signin)

            // 生成邀请码
            val code = CloudBaseBindings.generateInviteCode(client, signin!!.userId)
            assertNotNull("邀请码生成失败", code)
            assertTrue("邀请码应为 6 位", code!!.length == 6)
            println("邀请码生成成功: $code")

            // 被控端绑定
            val binding = CloudBaseBindings.bindWithCode(client, code, "test-kid-device-001")
            assertNotNull("绑定失败", binding)
            println("绑定成功: code=$code adminUid=${binding!!.adminUid}")

            // 重复绑定应失败（status 已为 BOUND）
            val rebind = CloudBaseBindings.bindWithCode(client, code, "test-kid-device-002")
            assertTrue("重复绑定应失败", rebind == null)
            println("重复绑定正确被拒绝")

            // 管理端查询当前邀请码
            val myCode = CloudBaseBindings.getMyInviteCode(client, signin.userId)
            assertNotNull("查询邀请码失败", myCode)
            println("管理端当前邀请码: $myCode")

            // 清理测试数据
            val docs = CloudBaseDb.queryDocuments(client, "bindings", where = mapOf("inviteCode" to code), limit = 1)
            docs?.firstOrNull()?.get("_id")?.toString()?.let { docId ->
                CloudBaseDb.deleteDocument(client, "bindings", docId)
                println("测试数据已清理")
            }
        }
    }

    @Test
    fun step5_tokenAutoRefresh() {
        runBlocking {
            // 正常登录拿双 token
            val signin = CloudBaseAuth.signIn(client, CloudBaseTestConfig.username, CloudBaseTestConfig.password)
            assertNotNull("登录失败", signin)
            client.refreshToken = signin!!.refreshToken
            println("登录成功，测试损坏 access_token 触发自动刷新")

            // 人为损坏 access_token → 下一次请求应触发刷新 → 重试成功
            client.accessToken = "expired.fake.token"
            val docs = CloudBaseDb.queryDocuments(client, "bindings", where = mapOf("inviteCode" to "zzzzzz"), limit = 1)
            assertNotNull("自动刷新后请求仍失败", docs)
            assertTrue("刷新后 access_token 应已更新", !client.accessToken.isNullOrBlank() && client.accessToken != "expired.fake.token")
            assertNotNull("refresh_token 应已轮换", client.refreshToken)
            println("自动刷新成功: accessToken 已更新(${client.accessToken!!.take(20)}...), refreshToken 已轮换")
        }
    }

    @Test
    fun step6_rulesRoundtrip() {
        runBlocking {
            val signin = CloudBaseAuth.signIn(client, CloudBaseTestConfig.username, CloudBaseTestConfig.password)
            assertNotNull("登录失败", signin)

            val ruleSet = RuleSet(
                appLimits = listOf(AppLimit("com.ss.android.ugc.aweme", AppCategory.SHORT_VIDEO, dailyMinutes = 30)),
                categoryLimits = listOf(CategoryLimit(AppCategory.GAME, dailyMinutes = 60)),
                dailyTotal = DailyTotalLimit(totalMinutes = 120),
                version = 1,
            )
            val saved = CloudBaseRules.saveRules(client, signin!!.userId, ruleSet)
            assertTrue("规则保存失败", saved)
            println("规则保存成功")

            val fetched = CloudBaseRules.fetchRules(client, signin.userId)
            assertNotNull("规则拉取失败", fetched)
            println("拉取解析结果: appLimits=${fetched!!.appLimits} categoryLimits=${fetched.categoryLimits} dailyTotal=${fetched.dailyTotal} version=${fetched.version}")
            assertTrue("appLimits 不一致", fetched.appLimits.size == 1 && fetched.appLimits[0].dailyMinutes == 30)
            assertTrue("categoryLimits 不一致", fetched.categoryLimits.size == 1 && fetched.categoryLimits[0].dailyMinutes == 60)
            assertTrue("dailyTotal 不一致", fetched.dailyTotal.totalMinutes == 120)
            println("规则拉取成功: ${fetched.appLimits.size} app 限制, ${fetched.categoryLimits.size} 类别限制")

            // 清理测试数据
            val docs = CloudBaseDb.queryDocuments(client, "rules", where = mapOf("adminUid" to signin.userId), limit = 1)
            docs?.firstOrNull()?.get("_id")?.toString()?.let { docId ->
                CloudBaseDb.deleteDocument(client, "rules", docId)
                println("测试数据已清理")
            }
        }
    }

    @Test
    fun step7_usageHeartbeat() {
        runBlocking {
            // 匿名登录（模拟被控端）
            val auth = CloudBaseAuth.signInAnonymously(client, "verify-device-${System.currentTimeMillis()}")
            assertNotNull("匿名登录失败", auth)

            val byPackage = mapOf(
                "com.tencent.mm" to 12L,
                "com.ss.android.ugc.aweme" to 30L,
                "com.tencent.tmgp.sgame" to 45L,
            )
            val ok = CloudBaseUsage.upsertHeartbeat(
                client, auth!!.userId, "2026-08-12", byPackage, byPackage.values.sum(), "com.tencent.mm",
            )
            assertTrue("心跳上报失败", ok)
            println("心跳上报成功")

            val snapshot = CloudBaseUsage.fetchLatest(client, auth.userId)
            assertNotNull("快照拉取失败", snapshot)
            assertTrue("byPackage 不一致", snapshot!!.byPackage == byPackage)
            assertTrue("totalMinutes 不一致", snapshot.totalMinutes == 87L)
            assertEquals("currentApp 不一致", "com.tencent.mm", snapshot.currentApp)
            println("快照拉取成功: total=${snapshot.totalMinutes} 分钟, currentApp=${snapshot.currentApp}")

            // 清理
            val docs = CloudBaseDb.queryDocuments(client, "usage", where = mapOf("kidDeviceId" to auth.userId), limit = 1)
            docs?.firstOrNull()?.get("_id")?.toString()?.let { docId ->
                CloudBaseDb.deleteDocument(client, "usage", docId)
                println("测试数据已清理")
            }
        }
    }

    private suspend fun runDbRoundtrip(client: CloudBaseClient) {
        val mark = System.currentTimeMillis()
        val inserted = CloudBaseDb.insertDocuments(
            client, "events",
            listOf(mapOf("type" to "VERIFY", "message" to "Task3 验证 $mark", "occurredAt" to mark)),
        )
        assertNotNull("插入文档失败（检查集合 events 是否存在、安全规则是否放行）", inserted)
        assertTrue("插入结果为空", inserted!!.isNotEmpty())
        println("插入成功: id=${inserted.first()}")

        val queried = CloudBaseDb.queryDocuments(
            client, "events",
            where = mapOf("type" to "VERIFY"),
        )
        assertNotNull("查询失败", queried)
        assertTrue("查询结果未包含刚插入的记录", queried!!.any { it["message"] == "Task3 验证 $mark" })
        println("查询成功: ${queried.size} 条 VERIFY 记录")

        // 清理：删除本测试插入的记录（验证 delete 链路）
        val deleted = CloudBaseDb.deleteDocument(client, "events", inserted.first())
        assertTrue("删除失败", deleted)
        println("删除成功: ${inserted.first()}")
    }
}
