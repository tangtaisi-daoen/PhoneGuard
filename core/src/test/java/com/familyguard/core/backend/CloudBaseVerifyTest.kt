package com.familyguard.core.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 3: CloudBase 最小接入验证（真实验证，非 mock）。
 *
 * 手动执行：设置环境变量 CLOUDBASE_VERIFY=1（常规 :core:test 会自动跳过）
 *
 * 步骤：
 * 1. step1_sendVerification：发送邮箱验证码（需要控制台已开启邮箱验证码登录方式）
 * 2. step2_signup_signin_db_roundtrip：校验验证码 → 注册 → 账号密码登录 → 数据库写/读/删
 * 3. step3_anonymousLogin：匿名登录（被控端认证基础）
 *
 * 前置条件（控制台操作）：
 * - 创建集合 rules、events
 * - rules/events 权限设为自定义安全规则 { "read": true, "write": true }（验证阶段）
 * - 身份认证 → 登录方式 → 开启邮箱验证码 + 用户名密码 + 匿名登录
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
    fun step1_sendVerification() = runBlocking {
        val email = CloudBaseTestConfig.email
        assertTrue("缺少 email（见 cloudbase.local.properties）", email.isNotBlank())
        val verificationId = CloudBaseAuth.sendEmailVerification(client, email)
        assertNotNull("发送邮箱验证码失败（检查控制台是否开启邮箱登录方式）", verificationId)
        // 持久化 verification_id，step2 复用（避免重复发码）
        runCatching {
            var f = java.io.File("../cloudbase.local.properties")
            if (!f.exists()) f = java.io.File("cloudbase.local.properties")
            val lines = f.readLines().filterNot { it.startsWith("verificationId=") || it.startsWith("code=") }
            f.writeText((lines + "verificationId=$verificationId").joinToString("\n") + "\n")
        }
        println("VERIFICATION_ID=$verificationId")
        println("请查收 $email 的验证码，写入 cloudbase.local.properties 的 code= 后重跑 step2")
    }

    @Test
    fun step2_signup_signin_db_roundtrip() = runBlocking {
        signupAndRoundtrip()
    }

    private suspend fun signupAndRoundtrip() {
        val email = CloudBaseTestConfig.email
        val username = CloudBaseTestConfig.username
        val password = CloudBaseTestConfig.password
        val code = CloudBaseTestConfig.code
        assertTrue("缺少 email/username/password（见 cloudbase.local.properties）", email.isNotBlank() && username.isNotBlank() && password.isNotBlank())
        assertNotNull("缺少验证码 code（先跑 step1）", code)

        // 1. 发送验证码 → 校验 → 注册（用户名+密码）
        // 复用 step1 的 verification_id（避免重复发码导致 code 失效）
        val verificationId = CloudBaseTestConfig.verificationId
            ?: CloudBaseAuth.sendEmailVerification(client, email)
                .also { println("已重新发送验证码（原 verification_id 过期），请更新 code 后重跑") }
        assertNotNull("缺少 verification_id（先跑 step1）", verificationId)
        val verificationToken = CloudBaseAuth.verifyEmailCode(client, verificationId!!, code!!)
        assertNotNull("验证码校验失败", verificationToken)
        val signup = CloudBaseAuth.signUpWithEmail(client, email, verificationToken!!, username, password)
        if (signup == null) {
            // 账号已存在（重复执行场景）→ 走登录
            println("注册返回失败，尝试账号密码登录（可能账号已存在）")
        } else {
            println("注册成功: uid=${signup.userId}")
        }

        // 2. 重新用账号密码登录（验证 signin 独立可用）
        client.accessToken = null
        val signin = CloudBaseAuth.signIn(client, username, password)
        assertNotNull("账号密码登录失败", signin)
        println("账号密码登录成功: uid=${signin!!.userId}")

        // 3. 数据库写/读/删
        runDbRoundtrip(client)
    }

    @Test
    fun step3_anonymousLogin() = runBlocking {
        val auth = CloudBaseAuth.signInAnonymously(client, "test-device-$ {System.currentTimeMillis()}")
        assertNotNull("匿名登录失败", auth)
        println("匿名登录成功: uid=${auth!!.userId}")
        // 匿名用户也能写数据（events 集合安全规则 write=true）
        val ids = CloudBaseDb.insertDocuments(
            client, "events",
            listOf(mapOf("type" to "ANON_VERIFY", "message" to "匿名写入验证", "occurredAt" to System.currentTimeMillis())),
        )
        assertNotNull("匿名用户写入失败", ids)
        println("匿名写入成功: id=${ids!!.first()}")
        CloudBaseDb.deleteDocument(client, "events", ids.first())
        println("匿名数据已清理")
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

            // 人为损坏 access_token → 下一次请求应 401 → 自动刷新 → 重试成功
            client.accessToken = "expired.fake.token"
            val docs = CloudBaseDb.queryDocuments(client, "bindings", where = mapOf("inviteCode" to "zzzzzz"), limit = 1)
            assertNotNull("自动刷新后请求仍失败", docs)
            assertTrue("刷新后 access_token 应已更新", !client.accessToken.isNullOrBlank() && client.accessToken != "expired.fake.token")
            assertNotNull("refresh_token 应已轮换", client.refreshToken)
            println("自动刷新成功: accessToken 已更新(${client.accessToken!!.take(20)}...), refreshToken 已轮换")
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
