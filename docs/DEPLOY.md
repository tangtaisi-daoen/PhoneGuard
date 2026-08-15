# PhoneGuard 自建后端部署指南

> 本指南**不推荐任何特定云服务品牌**。`core/backend/` 是**可替换层**：默认实现基于腾讯云开发 CloudBase（REST API），你可以选择任何你信任的数据库/云服务，或按 `docs/BACKEND.md` 的接口契约自建后端。

## 1. 总体要求

双端 App 通过 REST API 与一个"数据库后端"交互，后端需要提供：

- 用户认证：邮箱验证码注册/登录（管理端）、匿名登录（被控端）
- 5 个数据集合：`bindings`（绑定关系）、`rules`（管控规则）、`events`（异常事件）、`usage`（使用报告）、`apps`（已装应用列表）
- 字段级安全规则（按家庭隔离，见第 3 节）

## 2. 以默认实现（CloudBase）为例的开通步骤

> 以下仅为默认实现的示例步骤；若你选用其他服务或自建后端，跳过本节，直接按第 3 节的**数据与规则要求**对接。

1. 注册腾讯云账号，开通**云开发 CloudBase**（免费体验版即可；注意体验版有有效期与资源额度限制）；
2. 创建环境，得到环境 ID（形如 `xxx-xxxxxxxxxxxx`）；
3. 在数据库中创建 5 个集合：`bindings` / `rules` / `events` / `usage` / `apps`；
4. 开启邮箱验证码登录与匿名登录（认证设置）；
5. 按第 3 节为每个集合配置安全规则；
6. 将 `admin/build.gradle.kts` 与 `kid/build.gradle.kts` 中的

   ```kotlin
   buildConfigField("String", "CLOUDBASE_ENV_ID", "\"YOUR_ENV_ID\"")
   ```

   的 `YOUR_ENV_ID` 替换为你的环境 ID（两处：admin 与 kid）；
7. 构建安装：

   ```bash
   ./gradlew :admin:assembleRelease :kid:assembleRelease
   ```

8. 管理端注册账号 → 生成邀请码 → 被控端输入邀请码绑定 → 完成引导页权限授权。

## 3. 数据集合与安全规则（必须按此配置）

> 安全规则的作用：**按家庭隔离**——每个管理员只能读写自己的数据，被控端只能读写自己设备的数据，任何第三方无法跨家庭访问。规则中的 `auth.openid` 为当前登录用户的身份标识（CloudBase 语义；其他后端请按等价机制实现）。

### bindings（绑定关系：邀请码 → 管理员 → 被控端）

```json
{
  "read": "doc.adminUid == auth.openid || doc.kidDeviceId == auth.openid",
  "write": "doc.adminUid == auth.openid || doc.status == \"PENDING\""
}
```

### rules（管控规则）

```json
{
  "read": "doc.adminUid == auth.openid || doc.kidDeviceId == auth.openid",
  "write": "doc.adminUid == auth.openid"
}
```

### events / usage / apps（异常事件 / 使用报告 / 已装应用列表）

```json
{
  "read": "doc.adminUid == auth.openid || doc.kidDeviceId == auth.openid",
  "write": "doc.adminUid == auth.openid || doc.kidDeviceId == auth.openid"
}
```

### 数据写入约定（重要）

- 被控端上报 `events` / `usage` / `apps` 时**附带 `adminUid`**（绑定管理员的 uid，本地绑定后自动携带）；
- 管理端保存 `rules` 时**附带 `kidDeviceId`**（当前绑定被控端的 uid）；
- 绑定关系文档含 `inviteCode` / `adminUid` / `kidDeviceId` / `status`（PENDING/BOUND/EXPIRED）/ `expiresAt`（7 天有效期）。

### 集合字段结构

| 集合 | 关键字段 |
|---|---|
| bindings | inviteCode、adminUid、kidDeviceId、status、createdAt、boundAt、expiresAt |
| rules | adminUid、kidDeviceId、ruleEnvelope（规则集 JSON）、updatedAt |
| usage | kidDeviceId、adminUid、date、byPackage{包名:分钟}、totalMinutes、currentApp、reportedAt、权限健康/设备/更新状态字段 |
| events | kidDeviceId、adminUid、type、message、status(OPEN/ACKNOWLEDGED/RESOLVED)、occurredAt、read |
| apps | kidDeviceId、adminUid、apps[{pkg,name}]、updatedAt |

## 4. 远程更新（被控端，可选）

被控端通过"更新清单"实现签名验证的远程更新，需要：

1. **生成 ECDSA 密钥对**（P-256），私钥离线保存（**绝不放入仓库**），公钥内嵌在 `kid/.../update/KidUpdateManager.kt`；
2. 将 release APK 上传到你自己的静态托管地址（任何 HTTP(S) 文件服务）；
3. 用 `scripts/New-UpdateManifest.ps1` 生成并签名更新清单（含 APK 的 SHA-256 与证书指纹）；
4. 把更新清单的 URL 配置到 `kid/.../update/KidUpdateManager.kt` 的 `MANIFEST_URL`。

被控端验证链：清单 ECDSA 签名 → 包名/版本（防回滚）→ HTTPS → 时间窗（防重放）→ APK SHA-256 → 安装。

## 5. 被控端 Fully Managed 配网（可选）

若要让被控端成为 Device Owner（Fully Managed）设备，可在新机或恢复出厂后的设备上通过二维码/NFC/邮件完成一次性配网。仓库内 `docs/provisioning/` 提供配网 JSON 与二维码模板（**URL 为 `YOUR_ENV_ID` 占位符，必须按你的部署替换后重新生成**）：

1. 修改 JSON 三项：
   - `PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION`：改为你托管 release APK 的实际 HTTPS 地址；
   - `PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM`：改为该 APK 的 SHA-256（Base64 编码）；
   - `PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM`（仅 OPPO 变体）：改为你 release 签名证书的 SHA-256（Base64 编码）：

     ```bash
     keytool -exportcert -keystore release.keystore -alias <alias> -rfc | openssl x509 -outform der | openssl dgst -sha256 -binary | openssl base64
     ```

2. 把 JSON 文件内容用任意二维码工具生成二维码（替换 `docs/provisioning/*-qr.png`），或直接以邮件/NFC 方式下发 JSON；
3. 首次开机进入配网流程扫描二维码即可完成 Device Owner 设置，之后可静默更新、系统级防卸载（详见 `docs/fully-managed-migration.md`）。

> 说明：`PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME` 指向被控端的设备管理员组件，无需修改（除非你修改了包名）。

## 6. 常见问题

| 现象 | 处理 |
|---|---|
| 管理端报告/异常无数据 | 确认被控端已更新到最新版并正常心跳（旧版本不会附带 adminUid，需更新后自动补写）；确认安全规则已按第 3 节配置 |
| 被控端收不到规则 | 管理端重新保存一次规则（使 rules 附带 kidDeviceId）；确认被控端联网 |
| 绑定失败 | 确认邀请码在 7 天有效期内；确认 bindings 安全规则正确（PENDING 状态可写） |
