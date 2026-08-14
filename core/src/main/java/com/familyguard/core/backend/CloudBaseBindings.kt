package com.familyguard.core.backend

/**
 * 双端绑定：管理端生成邀请码，被控端输入邀请码完成绑定。
 *
 * bindings 集合文档结构：
 *   inviteCode: String（6 位，不含易混淆字符 0/O/1/I）
 *   adminUid: String（管理端用户 id）
 *   kidDeviceId: String（绑定后为被控端匿名 uid，未绑定为空）
 *   status: "PENDING" | "BOUND"
 *   createdAt / boundAt: Long
 */
object CloudBaseBindings {

    private const val COLLECTION = "bindings"
    private const val CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789"
    private const val CODE_LENGTH = 6

    /** 邀请码有效期：7 天（防长期泄露的旧码被利用；过期后需重新生成）。 */
    private const val INVITE_CODE_TTL_MS = 7L * 24 * 60 * 60 * 1000L

    /** 生成 6 位邀请码并写入 bindings（自动重试避免碰撞；生成前使旧 PENDING 码失效）。 */
    suspend fun generateInviteCode(client: CloudBaseClient, adminUid: String): String? {
        expirePendingCodes(client, adminUid)
        repeat(10) {
            val code = (1..CODE_LENGTH).map { CODE_CHARS.random() }.joinToString("")
            val exists = CloudBaseDb.queryDocuments(
                client, COLLECTION, where = mapOf("inviteCode" to code), limit = 1,
            )
            if (exists == null) return null // 查询失败，重试
            if (exists.isEmpty()) {
                val inserted = CloudBaseDb.insertDocuments(
                    client, COLLECTION,
                    listOf(
                        mapOf(
                            "inviteCode" to code,
                            "adminUid" to adminUid,
                            "kidDeviceId" to "",
                            "status" to "PENDING",
                            "createdAt" to System.currentTimeMillis(),
                            "boundAt" to 0L,
                            "expiresAt" to System.currentTimeMillis() + INVITE_CODE_TTL_MS,
                        ),
                    ),
                )
                if (inserted != null && inserted.isNotEmpty()) return code
            }
        }
        return null
    }

    /** 使该管理员所有旧 PENDING 邀请码失效（防旧码长期有效）。 */
    private suspend fun expirePendingCodes(client: CloudBaseClient, adminUid: String) {
        val updated = CloudBaseDb.updateDocuments(
            client, COLLECTION,
            where = mapOf("adminUid" to adminUid, "status" to "PENDING"),
            data = mapOf("status" to "EXPIRED"),
        )
        // updated == null 表示请求失败（网络等）；静默容忍，下次生成时再试。
        if (updated == null) return
    }

    /** 被控端用邀请码绑定；返回绑定信息（adminUid 等），失败返回 null。 */
    suspend fun bindWithCode(client: CloudBaseClient, inviteCode: String, kidDeviceId: String): BindingResult? {
        val matched = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("inviteCode" to inviteCode, "status" to "PENDING"),
            limit = 1,
        ) ?: return null
        if (matched.isEmpty()) return null
        val doc = matched.first()
        if (!isBindingEligible(doc, System.currentTimeMillis())) return null
        val docId = doc["_id"]?.toString() ?: return null
        val adminUid = doc["adminUid"]?.toString() ?: return null
        val updated = CloudBaseDb.updateDocuments(
            client, COLLECTION,
            where = mapOf("_id" to docId),
            data = mapOf(
                "kidDeviceId" to kidDeviceId,
                "status" to "BOUND",
                "boundAt" to System.currentTimeMillis(),
            ),
        )
        if (updated == null || updated <= 0) return null
        return BindingResult(inviteCode, adminUid, docId)
    }

    /**
     * 绑定候选判定：PENDING 且未过期（expiresAt 缺失/0 视为不过期，兼容旧文档）。
     */
    internal fun isBindingEligible(doc: Map<String, Any?>, now: Long): Boolean {
        if (doc["status"]?.toString() != "PENDING") return false
        val expiresAt = doc["expiresAt"]?.toString()?.toLongOrNull() ?: 0L
        return expiresAt <= 0L || expiresAt > now
    }

    /** 查询管理端当前生效的邀请码（最近创建的一条，本地排序避免依赖服务端默认顺序）。 */
    suspend fun getMyInviteCode(client: CloudBaseClient, adminUid: String): String? {
        val matched = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("adminUid" to adminUid),
            limit = 100,
        ) ?: return null
        return selectLatestInviteCode(matched)
    }

    /** 从同一管理员的邀请码历史中选择最近创建的一条（按 createdAt 取最大）。 */
    internal fun selectLatestInviteCode(rows: List<Map<String, Any?>>): String? =
        rows.asSequence()
            .mapNotNull { row ->
                val code = row["inviteCode"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val createdAt = row["createdAt"]?.toString()?.toLongOrNull() ?: 0L
                createdAt to code
            }
            .maxByOrNull { it.first }
            ?.second

    /** 查询管理端当前已绑定的被控端设备 id。 */
    suspend fun getBoundKidDeviceId(client: CloudBaseClient, adminUid: String): String? {
        val matched = CloudBaseDb.queryDocuments(
            client, COLLECTION,
            where = mapOf("adminUid" to adminUid, "status" to "BOUND"),
            // 管理端可能多次重新绑定，不能取历史记录中的第一条。
            limit = 100,
        ) ?: return null
        return selectLatestBoundDeviceId(matched)
    }

    /** 从同一管理员的绑定历史中选择最近一次成功绑定的设备。 */
    internal fun selectLatestBoundDeviceId(rows: List<Map<String, Any?>>): String? =
        rows.asSequence()
            .mapNotNull { row ->
                val deviceId = row["kidDeviceId"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val boundAt = row["boundAt"]?.toString()?.toLongOrNull() ?: 0L
                boundAt to deviceId
            }
            .maxByOrNull { it.first }
            ?.second
}

/** 绑定结果。 */
data class BindingResult(
    val inviteCode: String,
    val adminUid: String,
    val bindingDocId: String,
)
