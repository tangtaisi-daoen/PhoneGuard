# PhoneGuard 来源溯源报告（阶段二）

> ⚠️ **历史基线报告**：记录 2026-08-14 审计时点的状态；后续变更以当前代码与文档为准。
> 审计时间：2026-08-14
> 原则：所有外部来源均有 URL / commit / 文件路径 / 本地证据；分类为 直接复制 / 修改使用 / 设计参考 / 平台 API / 普通依赖；不允许"来源未知"。

## 1. 参考项目全景（7 个来源，逐一核验）

### 1.1 KidSafe — MIT（已核验 LICENSE © 2019 Mahmoud Mansour）
- 仓库：https://github.com/xMansour/KidSafe
- 本地证据：`admin/src/main/java/com/familyguard/admin/RuleAdapter.kt`（注释标注"借鉴 KidSafe 卡片列表设计"）、提交 `253edf4`
- 使用范围：**设计参考**（管理端应用列表卡片式 UI 形式）
- 相似度：AppAdapter.java ↔ RuleAdapter.kt Jaccard=0.145，人工对照仅平台骨架重叠
- 义务：MIT 保留版权声明 → THIRD_PARTY_NOTICES 记录

### 1.2 Child Screen Time / cst — ⚠️ 无许可证文件（默认保留所有权利）
- 仓库：https://github.com/childscreentime/cst
- 本地证据：`KidApp.kt`（WorkManager 保活）、`GuardAccessibilityService.kt`（全屏拦截）、`BlockActivity.kt`（bringAppToForeground）、`BlockOverlay.kt`（SYSTEM_ALERT_WINDOW 全屏遮罩）、`GuardWorker.kt`；提交 `f47ee7d`、`b28b928`、`d1fb843`
- 使用范围：**设计/思路参考**（全屏遮罩、前台拉回、WorkManager 保活、前台服务分层思路）
- 相似度：最高 ScreenLockService.java ↔ BlockOverlay.kt = 0.105，人工对照实现独立（本地 Kotlin object vs 上游 Java Service+密码体系）
- 处理结论：思路不受版权保护；无代码复制；THIRD_PARTY_NOTICES 如实说明参考关系。**注意：cst 无许可证，严禁复制其代码——已核查无复制**

### 1.3 TestDPC — Apache-2.0（已核验）
- 仓库：https://github.com/googlesamples/android-testdpc（调研 commit `d42d7f19`）
- 本地证据：Device Owner / DevicePolicyManager / PackageInstaller 会话实现（`KidUpdateManager.kt`、`KidDevicePolicyController.kt`、`KidDeviceAdminReceiver.kt`、`ProvisioningModePolicy.kt`）
- 使用范围：**平台 API 用法参考**（官方示例本就是 API 教学用途）
- 相似度：≤0.043，无代码复制
- 义务：Apache-2.0 保留声明 → THIRD_PARTY_NOTICES 记录

### 1.4 Headwind MDM — Apache-2.0（已核验，© 2018 Vsevolod Mayorov）
- 仓库：https://github.com/h-mdm/hmdm-android（调研 commit `6bf2ea29`）
- 本地证据：远程配置/更新队列/安装状态/启动恢复/远程日志思路（`UpdateDeliveryPolicy.kt`、`PeriodicUpdateCheck.kt`、`UpdateRecoveryReceiver.kt`、`HeartbeatService.kt`）
- 使用范围：**设计参考**（方案思路）
- 相似度：≤0.095（BootReceiver 为平台标准 BOOT_COMPLETED 模式），无代码复制
- 义务：Apache-2.0 保留声明 → THIRD_PARTY_NOTICES 记录

### 1.5 Curbox — GPL-3.0（已核验 LICENSE 全文）⚠️ 高风险对象
- 仓库：https://github.com/curbox-app/curbox-android（调研 commit `f86e6bff`；本次比对用 kt-rewrite 分支同源文件）
- 本地证据：规则日历/跨午夜时间段/限制比较器/反卸载思路（`RuleCalendar.kt`、`RulesEngine.kt`、`KidDeviceAdminReceiver.kt`）
- 使用范围：**设计参考**（思路）
- 相似度：全部 ≤0.094，方法签名与算法体系逐一对照无重叠
- 处理结论：**无 GPL 代码进入本仓库，无传染**；详见 CODE_SIMILARITY_REPORT.md

### 1.6 APKUpdater — GPL-3.0（已核验 LICENSE 全文，© 2024 rumboalla）⚠️ 高风险对象
- 仓库：https://github.com/rumboalla/apkupdater（调研 commit `69b6fcdf`，已精确检出比对）
- 本地证据：安装会话/安装进度/会话清理/唯一后台任务思路（`KidUpdateManager.kt`、`UpdateDeliveryPolicy.kt`）
- 使用范围：**设计参考**（思路）
- 相似度：SessionInstaller ↔ KidUpdateManager = 0.103（全场 GPL 对最高），函数签名逐一对照无重叠
- 处理结论：**无 GPL 代码进入本仓库，无传染**；详见 CODE_SIMILARITY_REPORT.md

### 1.7 Chastify（提交信息引用，用户确认无关）
- 提交 `b28b928` 标注"借鉴 Chastify/cst"；唯一同名仓库 `kiks71/chastify` 为浏览器扩展项目（TS/JS、无 LICENSE、与 Android 无关）
- 结论：**提交信息误引/指代不明，无代码关联，不构成来源**（用户 2026-08-14 确认）

## 2. 平台 API 依据（不列为第三方来源）

Android 官方文档与 API（PackageInstaller / DevicePolicyManager / AccessibilityService / UsageStatsManager / WorkManager / WindowManager / FileProvider 等）均属平台能力，本地实现为标准用法，无版权义务。

## 3. 自有资产（无外部来源）

| 资产 | 说明 |
|---|---|
| 应用图标 ic_app_icon.png（admin/kid 各一份，1.5MB） | **用户确认自研设计**（2026-08-14），可发布 |
| 全部中文 UI 文案（strings.xml） | 原创 |
| 内置应用分类表（CategoryRegistry） | 基于公开应用常识整理（抖音/微信/王者荣耀等包名→类别） |
| 代码注释、架构、测试 | 原创（含对上游思路的如实出处标注） |
| 字体 | 无内嵌字体（0 个 ttf/otf） |

## 4. 来源分类汇总

| 分类 | 项目 | 数量 |
|---|---|---|
| 直接复制 | 无 | 0 |
| 修改使用 | 无 | 0 |
| 设计参考 | KidSafe / cst / TestDPC / Headwind / Curbox / APKUpdater | 6 |
| 提交信息误引 | Chastify | 1（已定性排除） |
| 平台 API | Android SDK | 1 |
| 普通依赖 | 见 THIRD_PARTY_INVENTORY.csv | 12+ |

## 5. 门禁判定

- [x] 每个外部来源均有出处（URL）、许可证核验（LICENSE 原文）、使用范围、处理结论
- [x] "来源未知"清零（图标=自研确认、Chastify=定性排除）
- [x] GPL 传染排除（相似度全量 ≤0.145，结构人工对照）
- [x] cst 无许可证风险处置（思路参考+无复制+NOTICE 记录）
- [x] THIRD_PARTY_NOTICES.md 已生成（见仓库根目录）
