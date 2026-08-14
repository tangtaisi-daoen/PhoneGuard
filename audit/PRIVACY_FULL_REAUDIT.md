# PhoneGuard 隐私完整复查报告（第二轮，以用户为中心）

> 复查时间：2026-08-14
> 方式：全部只读——工作树全量敏感扫描、Git 历史核验、release APK 解包检查、云端数据库只读统计、本地配置核验。
> 范围：以"公开仓库/已分发 APK/云端数据"三者是否会暴露用户本人及其家庭信息为唯一判据。

## 1. 复查项与结果

| # | 检查面 | 结果 |
|---|---|---|
| 1 | 工作树敏感扫描（邮箱/手机号/QQ/路径/序列号/私钥/token/密码赋值） | ✅ 命中均属：gitignored 文件（cloudbase.local.properties、backup 文档）、审计报告文本、代码参数名/字段名。无硬编码凭据 |
| 2 | 被跟踪代码中的设备信息 | ⚠️ `CloudBaseUsageSnapshotTest.kt` 含真实机型 "OPPO PDKM00"（测试数据，低敏感，发布前可改测试值） |
| 3 | Git 历史 | ⚠️ ① 作者邮箱/用户名（全部提交，已批准匿名化）；② AGENTS.md 曾含 Windows 路径（当前已去，历史待 filter-repo）；③ envId 存在于 9 个历史提交（发布时 filter-repo 替换） |
| 4 | release APK 内容 | ⚠️ 仅内嵌 envId 与更新托管 URL（buildConfig + 常量）；无邮箱/路径/序列号。**APK 是 envId 的现实载体**（已装在用户设备；外传即扩散） |
| 5 | 云端数据库现状（只读统计） | ⚠️ **真实家庭数据**：bindings 4 条 / rules 1 条 / events 70 条 / usage 5 条 / apps 3 条；安全规则 5 集合全开放（实测）。暴露链终点=这些数据 |
| 6 | 本地配置/凭据 | ✅ keystore.properties、phoneguard-release.keystore、update-manifest-private.pem、cloudbase.local.properties、cloudbaserc.json、local.properties 全部 gitignored，不随仓库发布 |
| 7 | 文档层（docs/specs/tasks） | ✅ 仅 backup 文档含序列号+路径（已 gitignore）；其余无个人数据 |
| 8 | 日志 | ✅ Log 输出仅包名/判定文本；无 token/邮箱/密码输出；DiagActivity 仅截断展示 |

## 2. 用户隐私风险清单（按严重度）

| 级别 | 风险 | 现状 | 处置（发布时） |
|---|---|---|---|
| **Critical** | 云端真实家庭数据（70 条事件/5 条使用报告/绑定关系）可被读取——条件：envId 暴露 + 规则全开放 | envId 在仓库 10 处+历史 9 提交+APK 内嵌；规则实测全开放 | ① filter-repo 替换 envId（源头切断）② 规则收紧为字段级（纵深防御，即使 APK 外传也无法读取） |
| High | Git 作者邮箱=QQ/手机号，与账号强关联 | 全部提交 | filter-repo 匿名化（已批准） |
| Medium | Windows 路径进过历史（AGENTS.md） | 当前已去 | filter-repo 一并替换 `C:\Users\` 文本 |
| Low | 测试代码含真实机型 PDKM00 | 当前存在 | 发布前改测试数据 |
| Low | 免费体验版到期（2027-02-12）后云端数据可用性 | 平台规则 | README 说明（自建指南） |

## 3. 需要用户注意的云端现状

- 云端现有数据（bindings/rules/events/usage/apps）为**真实家庭数据**，且规则全开放。在规则收紧前，任何拿到 envId 的人（含已分发 APK 的接收方）都可读取。
- 建议：**尽快收紧规则**（控制台粘贴 §BACKEND_RULES_AUDIT.md §3a 的 JSON，5 集合；或环境所属账号重新登录 CLI 后我来应用）。这不影响 App 功能（代码已回退原版，规则收紧与 App 行为兼容）。

## 4. 复查结论

- 代码/配置/文档层面：除上述 4 项待发布处理外，**无其他隐私泄露**（无硬编码凭据、无 token/密码入历史、无第三方跟踪）。
- 用户隐私的真正风险点只有一个链条：**envId（仓库/历史/APK）→ 全开放规则 → 家庭数据**。切断源头（filter-repo）+ 纵深防御（规则收紧）后即闭环。
- 全部处置均为发布时一次性动作，不影响自用代码与现有使用。
