# PhoneGuard 后端与 CloudBase 安全审计（阶段四）

> ⚠️ **历史基线报告**：记录 2026-08-14 审计时点的状态；后续变更以当前代码与文档为准。
> 审计时间：2026-08-14
> 代码侧结论：客户端以 REST 直连 CloudBase（无云函数层），**所有数据权限由 CloudBase 控制台安全规则决定**——本文件列出代码侧事实 + 需要你在控制台核对的清单。

## 1. 架构事实（代码侧）

- 无自有后端服务器；客户端（admin/kid）直连 `https://{envId}.api.tcloudbasegateway.com` REST API
- 认证：邮箱验证码注册/登录（管理员）、匿名登录（被控端，x-device-id）
- 数据库集合（代码引用）：`bindings`、`rules`、`events`、`usage`（CloudBaseDb 通用 API 可访问任意集合）
- 静态托管：更新 APK 与 update-manifest.json（`*.tcloudbaseapp.com/phoneguard/...`）——公开读属预期（APK 要公开下载）
- 未发现云函数调用、未发现存储桶读写（代码侧）

## 2. 代码侧数据流

| 集合 | 写入方 | 读取方 | 文档结构 |
|---|---|---|---|
| bindings | admin 生成邀请码（PENDING）→ kid 绑定（BOUND） | admin 查自己的邀请码/已绑设备 | inviteCode/adminUid/kidDeviceId/status/createdAt/boundAt |
| rules | admin 保存 | kid 拉取 | adminUid/appLimits/categoryLimits/dailyTotal/version |
| usage | kid 心跳上报 | admin 报告查询 | kidDeviceId/date/byPackage/totalMinutes/currentApp/... |
| events | kid 异常上报 | admin 轮询 | kidDeviceId/type/message/status(OPEN/ACK/RESOLVED)/... |

## 3. 控制台实测结果（2026-08-14，CloudBase CLI 查询）

| # | 核对项 | 实测结果 | 判定 |
|---|---|---|---|
| C1 | 数据库安全规则 | **bindings / rules / events / usage 四集合：AclTag=CUSTOM，规则 `{"read": true, "write": true}`（所有用户含匿名全开放读写）；users：PRIVATE（仅创建者）** | ❌ **Critical：公开环境 ID 后，任何互联网用户可读取全部家庭数据（绑定关系/规则/使用报告/异常事件）并篡改** |
| C2 | 认证设置 | 环境存在，体验版；邮箱验证码/匿名登录已启用（代码链路可用） | ✅ 待确认控制台口令策略 |
| C3 | 静态托管 | 更新 APK 目录公开读（预期） | ✅（阶段七随环境 ID 占位符化复核） |
| C4 | 限流策略 | 平台默认，未单独配置 | ⚠️ 记录为依赖项 |
| C5 | 环境状态 | 体验版，创建 2026-08-12，**到期 2027-02-12**（6 个月免费期） | ⚠️ 公开后到期需升级/续费，README 需说明 |
| C6 | 测试/历史数据 | 数据库中存在测试文档（CloudBaseVerifyTest 使用后清理，但不保证零残留） | ⚠️ 阶段五核对 |

## 3b. 规则应用执行记录（2026-08-14）

- **发布形态决策（用户确认）**：开源发布采用**纯自托管**——APK 不内置真实环境 ID（构建时注入，默认占位符），公开仓库不含真实 envId/托管 URL。因此 H1 的"公开即暴露"路径已切断。
- **H1 状态调整**：由"发布门禁（Critical）"降级为"自有环境加固建议（High→Medium）"——你的环境仍建议应用字段级规则（防已注册用户横向读取、防环境 ID 泄露面），但不阻塞开源发布。
- 代码侧修复（usage/events/apps 附带 adminUid、rules 双归属字段、kid 按 kidDeviceId 拉规则、DiagActivity 适配）：**全部完成并已提交**（:core/:admin/:kid 测试与构建通过）。
- 线上规则应用：CloudBase CLI（当前登录态）可查询但 **SecurityRule 系 API 报 `Env Not Exists In Your Account`**（登录凭证非环境所属账号或子账号缺 CAM 权限），无法经 CLI 应用。
- 待用户执行（二选一，任意时间）：
  A. 控制台手动应用（推荐）：云开发控制台 → 数据库 → 各集合 → 安全规则 → 选择"自定义安全规则"并粘贴 §3a 的 JSON；
  B. 用户用环境所属账号重新执行 `cloudbase login` 后由 CLI 应用（ModifySecurityRule，参数见 §3a）。

**应用规则后的验证清单**（控制台或真机）：
1. bindings：admin 可读自己的邀请码；kid 可用 PENDING 码绑定；跨用户读被拒
2. rules：admin 保存规则（自动附带 kidDeviceId）；kid 心跳拉到规则（fetchEnvelopeForKid）
3. usage/events/apps：kid 心跳/上报后 admin 可见（1-2 个心跳内补齐 adminUid）；旧文档在 kid 更新前 admin 暂不可见（预期）
4. 规则应用前建议：管理端先重新保存一次规则（补 kidDeviceId），被控端先手动触发一次心跳（补 adminUid）

## 3a. H1（Critical）修复方案（阶段六执行，用户已授权）

**实测结论（2026-08-14）：bindings / rules / events / usage / apps 五集合 AclTag 全为 CUSTOM，规则均为 `{"read": true, "write": true}`（全开放）。**

**目标规则 JSON（定稿蓝图，阶段六应用前最终确认）**：

```json
// bindings
{ "read": "doc.adminUid == auth.openid || doc.kidDeviceId == auth.openid",
  "write": "doc.adminUid == auth.openid || doc.status == \"PENDING\"" }
// rules（双归属字段：admin 按 adminUid、kid 按 kidDeviceId 读取）
{ "read": "doc.adminUid == auth.openid || doc.kidDeviceId == auth.openid",
  "write": "doc.adminUid == auth.openid" }
// usage / events / apps
{ "read": "doc.adminUid == auth.openid || doc.kidDeviceId == auth.openid",
  "write": "doc.adminUid == auth.openid || doc.kidDeviceId == auth.openid" }
```

**配套代码改动（kid/admin 端，阶段六带测试）**：

| 文件 | 改动 |
|---|---|
| CloudBaseUsage.upsertHeartbeat | 写入文档附带 `adminUid = SessionStore.boundAdminUid`（已完成） |
| CloudBaseEvents.report / reconcileConditions | 同上（已完成） |
| CloudBaseApps.upsert | 同上（已完成） |
| CloudBaseRules.saveEnvelope/saveRules | 附带 `kidDeviceId`（admin 保存时从绑定查询，已完成） |
| CloudBaseRules.fetchEnvelopeForKid | kid 按自己匿名 uid 拉取规则（已完成，HeartbeatService 切换调用） |
| HeartbeatService | 规则拉取改为 fetchEnvelopeForKid(uid)；上报全部附带 adminUid（已完成） |
| RulesActivity.saveRules | 保存前查绑定 kid uid 并附带（已完成） |
| DiagActivity | 诊断查询改 where adminUid=uid（适配规则收紧，已完成） |

**旧数据迁移（执行顺序：先迁移后改规则，避免 admin 锁死）**：
1. 先用 CLI 批量给 usage/events/apps 现有文档补 `adminUid`（从 bindings 的 BOUND 文档反查 kidDeviceId→adminUid 映射）；
2. 再应用五集合新规则（ModifyDatabaseACL）；
3. 规则变更后跑 CloudBaseVerifyTest 真实链路验证（注册→绑定→写读→跨用户拒绝）。

**遗留风险（记录）**：bindings 的 PENDING 写放行保留绑定流程可用性，绑定抢注面（H2）由邀请码有效期/旧码失效缓解（阶段六一并实施）。

## 4. 代码侧改进建议（阶段六/七可选）

1. **绑定加固（H2）**：bindWithCode 增加 adminUid 校验（kid 侧预填管理员标识后再确认）、刷新邀请码时旧码置 `EXPIRED`、邀请码加 `expiresAt`。
2. **getMyInviteCode 显式排序**：`orderBy createdAt desc`（M5）。
3. **token 加密**：SessionStore 升级 EncryptedSharedPreferences（M1，发布前）。
4. **日志清理**：release 构建移除 Log.d（M4）。
5. 控制台规则若无法做到字段级 ACL，至少按集合隔离并开启审计日志。

## 5. 门禁判定

- [x] 代码侧 API 鉴权/数据流/权限依赖面已审计
- [ ] **C1 数据库安全规则——必须由用户在控制台核对后确认**（H1 门禁依赖此项）
- [ ] C2–C6 核对结果回填本文件后，High 项方可关闭
