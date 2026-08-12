package com.familyguard.core.backend

import java.io.File
import java.util.Properties

/**
 * CloudBase 验证测试配置：从 cloudbase.local.properties（已 gitignore）读取，
 * 该文件格式：
 *   envId=YOUR_ENV_ID
 *   email=xxx@example.com
 *   username=admin
 *   password=xxx
 */
object CloudBaseTestConfig {
    private val props = Properties().apply {
        val candidates = listOf(
            File("cloudbase.local.properties"),          // 模块目录（测试默认工作目录）
            File("../cloudbase.local.properties"),       // 项目根目录
            File(System.getProperty("user.dir"), "cloudbase.local.properties"),
        )
        for (f in candidates) {
            if (f.exists()) {
                f.inputStream().use { load(it) }
                break
            }
        }
    }

    val envId: String = props.getProperty("envId") ?: System.getenv("CLOUDBASE_ENV_ID").orEmpty()
    val email: String = props.getProperty("email") ?: System.getenv("CLOUDBASE_EMAIL").orEmpty()
    val username: String = props.getProperty("username") ?: System.getenv("CLOUDBASE_USERNAME").orEmpty()
    val password: String = props.getProperty("password") ?: System.getenv("CLOUDBASE_PASSWORD").orEmpty()
    val code: String? = props.getProperty("code")?.takeIf { it.isNotBlank() } ?: System.getenv("CLOUDBASE_CODE")
    val verificationId: String? = props.getProperty("verificationId")?.takeIf { it.isNotBlank() }

    /** 移动应用安全来源凭证（控制台：安全配置 → 移动应用安全来源）。 */
    val appSign: String? = props.getProperty("appSign")
    val appAccessKeyId: String? = props.getProperty("appAccessKeyId")
    val appAccessKey: String? = props.getProperty("appAccessKey")

    /** 手动验证开关：设置环境变量 CLOUDBASE_VERIFY=1 才执行真实链路验证测试。 */
    val manualVerify: Boolean = System.getenv("CLOUDBASE_VERIFY") == "1"
}
