# PhoneGuard 个人隐私与机密泄露专项审计报告（P0）

> 审计时间：2026-08-14
> 审计范围：工作树、暂存区、全部分支（master）、全部 tag（无）、全部历史提交（29 个）、.gitignore 忽略清单、构建产物、未跟踪文件
> 依据：`DEEPSEEK_OPEN_SOURCE_RELEASE_PLAN.md` 第 5 节
> 本报告已脱敏：不含任何密钥/凭据原文；敏感文件仅以 SHA-256 指纹标识。

## 1. 审计方法

- `git log --all -p` 全量历史内容扫描（私钥头 / AKID / password= / token= / 邮箱 / 手机号 / 局域网 IP / Windows 绝对路径 / 长密钥模式）
- `git log --all --name-only --diff-filter=A` 全部历史新增文件路径扫描（keystore / .pem / .jks / .properties / .env / APK / 图片 / backup）
- 工作树全文件正则扫描（排除 build/、.git/、.gradle/、.idea/ 与二进制格式）
- 高风险文件逐一核查：存在性、gitignore 状态、Git 历史追踪情况、内容性质判定（仅输出指纹）
- Device Owner 配置 JSON/QR、更新清单、签名脚本、测试配置逐文件人工审查

## 2. 审计结论（先给结论）

**未发现任何密钥、CloudBase 凭据、签名私钥进入过 Git 历史。** 三个签名/凭据文件均被 `.gitignore` 正确忽略且从未被跟踪。发现 **3 项已确认的个人信息/路径泄露面**（其中 1 项已进入 Git 历史）、**4 项本地敏感文件**（未泄露，需模板化处理）、**1 项环境标识决策点**。

## 3. 已确认泄露面（按严重度排序）

### 3.1 Git 提交作者信息（已进入全部历史）

- 全部 29 个提交的作者为 `phoneguard-dev <dev@users.noreply.github.com>`（QQ 邮箱，号码本身即个人标识）。
- 本地 `git config user.name/user.email` 与此一致，后续提交会继续携带。
- **暴露范围**：仓库公开后任何访问者可通过 GitHub 提交页看到。GitHub 默认会隐藏已关联账号的邮箱，但仓库公开时作者字符串本身仍可见。
- **处理选项（需用户决策）**：
  1. 改写历史作者为匿名地址（如 `<USERNAME+local@users.noreply.github.com>` 或 `<phoneguard-dev@users.noreply.github.com>`）——需重写全部提交哈希，**必须用户批准**（计划第 12 节要求）；
  2. 接受公开（不推荐，该邮箱即手机号/QQ 号）。

### 3.2 AGENTS.md 含 Windows 绝对路径（已进入 Git 历史）

- `AGENTS.md` 第 74 行含 `C:\Users\<USERNAME>\AppData\Local\Temp\opencode\...` 路径，该文件被 Git 跟踪，路径已随提交进入历史。
- **暴露范围**：仓库公开后 Windows 用户名（<USERNAME>）与本地目录结构可见。
- **处理**：公开版移除该行（或整个 AGENTS.md 不纳入公开仓库——其为本地 AI 代理工作指令，对公开用户无意义）；若要求历史中也无此路径，需改写历史（用户批准）。

### 3.3 docs/backup-before-fully-managed.md 含真实设备标识（未跟踪，未进入历史）

- 未跟踪文件，内容含：真实设备 OPPO PDKM00 序列号、`C:\Users\<USERNAME>\PhoneGuard-Backups\...` 绝对路径。
- **暴露范围**：仅本地（未进入 Git、未暂存）。只要不提交即不会进入公开仓库。
- **处理**：加入 `.gitignore`（防止误提交）或移出仓库目录；如确有文档价值，脱敏（去掉序列号与路径）后再入仓库。

## 4. 本地敏感文件（均已 gitignore，未泄露；只列指纹）

| 文件 | SHA-256 | 性质 | 处理 |
|---|---|---|---|
| keystore.properties | E266CE4C309BE44EE5EA6C72C0A94A1F744C81A360F18D10B47850538426A0E2 | APK 签名密码 | 不提交；发布模板 keystore.properties.example |
| phoneguard-release.keystore | 06E7F30B0317BA7692A34D3132BF346BD12816290ABAFD78869128474FACF982 | APK 签名私钥库 | 不提交；证书指纹 41091d46...（公开验证值）可保留 |
| release-keys/update-manifest-private.pem | F682520818D782464CCAB432271A892D611A96EEB250099038B760F346919FC2 | 更新清单签名私钥 | 不提交；客户端内嵌公钥 MFkw...（公开验证值）可保留 |
| cloudbase.local.properties | 6C626BB32FBAA327D580B2F7F86050E217549220BC9AA0F3E10F7E4812A4935A | CloudBase 凭据（appAccessKey、账号、密码） | 不提交；**建议轮换 appAccessKey**；发布模板 cloudbase.local.properties.example |

判定依据（按计划 5.2 要求）：`CloudBaseTestConfig.kt`（SHA256 305FD5C99BA97C65C89B0C949DE54FBA85681B4A41B73AEC2BF7DC61C5955986）经内容审查为**纯配置加载器**——仅从 `cloudbase.local.properties`（gitignored）或环境变量（CLOUDBASE_*) 读取，含 envId 示例与 `xxx@example.com` 占位符，**无任何真实凭据**，判定为"示例环境标识 + 外部配置引用"，可发布。真实凭据只存在于上述 4 个本地文件中。

## 5. 已核查为安全的项目（可发布）

- `core/.../backend/*.kt`（CloudBaseClient/Auth/Apps/Bindings/Events/Rules/Usage）：无硬编码凭据，envId 为构造参数，token 为运行时字段。
- `CloudBaseVerifyTest.kt`：凭据全部经 CloudBaseTestConfig 读取；测试数据动态生成（verify-device-<时间戳>）且测试后清理；无真实账号。
- `kid/.../KidUpdateManager.kt`：仅含公开 ECDSA 公钥（客户端验证设计）与 CloudBase 托管 URL。
- `docs/provisioning/*.json` + 对应 QR PNG：仅含 Device Owner 配置公开参数（组件名、APK 下载 URL、APK 校验和、签名证书校验和），**无 WLAN 凭据、无 token、无绑定码**；QR 为纯压缩图像无附加文本。
- `docs/updates/kid-stable-update-manifest.json`：仅含 APK SHA-256、证书 SHA-256、ECDSA 签名值（均为公开验证数据）。
- `scripts/New-UpdateManifest.ps1`：私钥以命令行参数传入，脚本内无私钥内容、无密码。
- `gradle.properties`、`.gitattributes`、`gradlew`、`gradle/wrapper/*`：无敏感内容。
- 手机号、IMEI、Android ID、局域网 IP、logcat、崩溃堆栈、截图、备份归档：全库扫描**零命中**。
- 无 stash、无额外分支、无 tag；`.git/info/exclude` 为空。

## 6. 环境标识决策点（中风险）

环境 ID `YOUR_ENV_ID`（腾讯云开发免费体验版环境）出现在被跟踪代码与文档中（更新清单 URL、provisioning JSON、KidUpdateManager）。该 ID 属**环境标识而非密钥**，且已在项目文档中公开使用，但：

- 免费体验版有配额限制，公开后任何人可对该环境发起调用；
- 建议：公开版将代码中的 URL/环境 ID 改为占位符（如 `YOUR_ENV_ID`），README 说明自建环境步骤；或保留真实值并明确公告配额风险。

此项在阶段七（发布构建）落实，需用户确认取舍。

## 7. 已暴露凭据轮换/撤销记录

见 `audit/CREDENTIAL_ROTATION_RECORD.md`。结论：**本次未发现任何已进入公开或半公开渠道的凭据**，因此无强制轮换项；给出建议性轮换清单（cloudbase.local.properties 的 appAccessKey 等）。

## 8. P0 门禁判定

- [x] 密钥/凭据类：无泄露（4 个本地敏感文件均 gitignored 且从未跟踪）
- [x] 设备标识类：1 项（backup 文档，未跟踪未提交，可控）
- [x] 个人身份类：1 项（Git 作者邮箱，已在全部历史——**需用户决策**）
- [x] 路径类：2 项（AGENTS.md 已进历史——**需用户决策**；backup 文档未进历史）
- [x] 历史提交类：29 个提交已全量扫描，除作者信息与 AGENTS.md 路径外无其他内容

**门禁结论：进入公开发布阶段前，必须完成：(a) 用户对 Git 作者信息与 AGENTS.md 路径的处理决策；(b) 公开版文件清单核对（见 PUBLISHABLE_FILE_MANIFEST.md）；(c) backup 文档防误提交。以上完成后 P0 门禁即通过。**

## 9. 处置进展（2026-08-14，用户已确认决策）

| 项 | 用户决策 | 状态 |
|---|---|---|
| Git 作者信息 | 改写历史为匿名地址 | 已批准，阶段七执行（git filter-repo 重写 author/committer） |
| AGENTS.md 路径 | 移除路径行后保留 | ✅ 已完成（第 74 行路径已替换为占位符 `<agent-skills 本地仓库>`，验证零残留） |
| backup 文档 | 加入 .gitignore | ✅ 已完成（`docs/backup-before-fully-managed.md` 已忽略，check-ignore 验证通过） |
| 环境 ID | 占位符化 + 自建环境指引 | 已批准，阶段七执行（与构建注入配置配套，避免破坏现有更新链路） |
| CloudBase appAccessKey | 谨慎性轮换 | 待用户操作（腾讯云控制台），不阻塞发布流程 |
