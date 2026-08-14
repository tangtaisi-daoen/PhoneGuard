# PhoneGuard 凭据轮换/撤销记录（P0 附档）

> 审计时间：2026-08-14
> 结论先行：**未发现任何已进入公开或半公开渠道的凭据，无强制轮换项。** 以下为建议性处置记录。

## 1. 核查结果

| 凭据类型 | 载体 | 是否进入 Git/公开渠道 | 处置 |
|---|---|---|---|
| APK 签名私钥与密码 | phoneguard-release.keystore / keystore.properties | 否（gitignored，从未跟踪） | 无需轮换；保持本地保管 |
| 更新清单签名私钥 | release-keys/update-manifest-private.pem | 否（gitignored，从未跟踪） | 无需轮换；保持本地保管 |
| CloudBase 移动应用安全来源密钥 | cloudbase.local.properties（appAccessKey 等） | 否（gitignored，从未跟踪） | **建议轮换**（谨慎性，见下） |
| CloudBase 账号邮箱/密码 | cloudbase.local.properties | 否（gitignored，从未跟踪） | 建议定期改密（常规） |
| 环境 ID | 被跟踪代码/文档 | 是（环境标识，非密钥） | 见隐私审计第 6 节决策点 |

## 2. 建议性轮换（用户决定是否执行）

**CloudBase appAccessKey（移动应用安全来源）**：
- 原因：该值位于本地未跟踪文件，未进入任何 Git 历史或外部渠道；轮换为谨慎性最佳实践（审计链路中该文件曾被读取，且免费体验版环境后续将公开使用场景）。
- 操作：腾讯云开发控制台 → 安全配置 → 移动应用安全来源 → 删除旧密钥并新建，更新 `cloudbase.local.properties`（不提交）。
- 影响：旧客户端若内嵌该密钥（已确认未内嵌）需同步更新；当前客户端从外部文件读取，轮换无发布影响。

**Git 作者身份（非凭据，但属个人信息）**：
- 若不接受公开 `phoneguard-dev <dev@users.noreply.github.com>`，需在阶段七执行历史改写（git filter-repo 或 filter-branch 重写 author/committer 为匿名地址），**改写前必须用户批准**，且需在改写后重新验证构建与发布产物。

## 3. 已执行动作

- 无（本轮为审计记录，所有处置待用户确认后于阶段七执行）。

## 4. 验证方法

- 指纹比对：四个本地敏感文件 SHA-256 已记录于 `audit/SECRET_SCAN_REPORT.md` 第 2 节，后续发布前可复核指纹一致性。
- 防误提交：`.gitignore` 已覆盖全部四个文件（2026-08-14 复核通过）；新增 `docs/backup-before-fully-managed.md` 需补充忽略或移出仓库。
