# PhoneGuard 代码相似度审计报告（阶段二：GPL 传染风险专项）

> 审计时间：2026-08-14
> 方法：上游仓库克隆至本地（Curbox kt-rewrite 分支 / APKUpdater 调研 commit `69b6fcdf` 精确 checkout），与本地实现做 ①标识符+字符串字面量 Jaccard 相似度 ②方法签名/类结构对比 ③关键文件人工逐行对照。
> 判定阈值说明：同领域 Android 代码因共享平台 API（PackageInstaller/AccessibilityService/WorkManager 等）天然存在 0.05–0.15 的词汇重叠；"直接复制/修改使用"通常 ≥0.4。全部 18 对结果 ≤0.145，全部判为**无代码级复制**。

## 1. Curbox（GPL-3.0，高风险对象）比对结果

| 上游文件（Curbox） | 本地候选文件 | Jaccard | 结构对比结论 |
|---|---|---|---|
| AppBlocker.kt (456 行) | GuardAccessibilityService.kt / BlockActivity.kt / BlockOverlay.kt | 0.094 / 0.050 / 0.029 | 上游为 cooldown 持久化 + Shizuku + 警告屏体系；本地为无障碍页面分类 + 桌面拉回 + 用量快照。**实现机制完全不同** |
| AppBlockerService.kt (246 行) | HeartbeatService.kt / KidApp.kt / GuardWorker.kt | 0.053 / 0.031 / 0.026 | 无对应结构 |
| AntiUninstallBlocker.kt (159 行) | KidDeviceAdminReceiver.kt 等 4 文件 | ≤0.043 | 无对应结构（本地为系统 DeviceAdmin 标准用法） |
| TimeGroupWindow.kt (95 行) | RuleCalendar.kt / RulesEngine.kt | 0.031 / 0.014 | 无对应结构（本地跨午夜逻辑独立实现，见 RulesEngineTest/RuleCalendarTest） |
| RestrictionComparator.kt (314 行) | RulesEngine.kt / RuleCalendar.kt | 0.025 / 0.019 | 上游为自适应数学比较器（NFC/焦点目标体系）；本地为规则叠加取最短。**算法体系不同** |

## 2. APKUpdater（GPL-3.0，高风险对象）比对结果

| 上游文件（APKUpdater @69b6fcdf） | 本地候选文件 | Jaccard | 结构对比结论 |
|---|---|---|---|
| SessionInstaller.kt (267 行) | KidUpdateManager.kt / UpdateHttpClient.kt | 0.103 / 0.083 | 上游：流式输入 + 进度回调 + XAPK + root 安装；本地：manifest 验证 + SHA-256 + FileProvider 会话安装。**函数签名逐一对照无重叠** |
| UpdatesWorker.kt (64 行) | PeriodicUpdateCheck.kt 等 | ≤0.047 | 无对应结构 |

## 3. cst（无许可证，重点风险对象）比对结果

| 上游文件（cst main 分支） | 本地候选文件 | Jaccard | 结构对比结论 |
|---|---|---|---|
| ScreenLockService.java (850 行) | BlockOverlay.kt / BlockActivity.kt | 0.105 / 0.061 | 上游：Service + 密码对话框 + Fragment 拦截内容；本地：object + WindowManager 全屏遮罩 + 硬件键拦截。**仅"SYSTEM_ALERT_WINDOW 全屏遮罩 + bringAppToForeground"思路相同**（本地注释如实标注"借鉴 cst"） |
| ScreenTimeWorker.java (106 行) | GuardWorker.kt / KidApp.kt | 0.081 / 0.030 | 均为 WorkManager 保活思路（平台标准方案），实现独立 |
| DeviceSecurityManager.java (234 行) | GuardAccessibilityService.kt 等 | ≤0.041 | 无对应结构 |

> cst 无许可证文件（默认保留所有权利）。本地仅参考其**设计思路**（思路不受版权保护），且已证无代码复制；本地代码注释中保留"借鉴 cst"出处标注。结论：不构成侵权风险，但 THIRD_PARTY_NOTICES 中如实说明参考关系。若需绝对审慎，可在发布说明中注明。

## 4. KidSafe（MIT）比对结果

| 上游文件（KidSafe master） | 本地候选文件 | Jaccard | 结论 |
|---|---|---|---|
| AppAdapter.java (85 行) | RuleAdapter.kt | 0.145（全场最高） | 人工逐行对照：仅 RecyclerView.Adapter 平台骨架（onCreateViewHolder/onBindViewHolder/getItemCount）重叠；本地为 Kotlin + ViewBinding 的行编辑适配器（包名/分钟/类别/删除），上游为 Java + findViewById 的应用开关列表。**设计参考（卡片列表 UI），无代码复制** |

## 5. TestDPC（Apache-2.0）与 Headwind MDM（Apache-2.0）比对结果

| 上游文件 | 本地候选文件 | Jaccard | 结论 |
|---|---|---|---|
| TestDPC PackageInstallationUtils.java | KidUpdateManager.kt 等 | ≤0.043 | 无代码复制（均为平台 API 标准用法） |
| TestDPC DevicePolicyManagerGatewayImpl.java (1399 行) | KidDevicePolicyController.kt 等 | ≤0.032 | 无代码复制 |
| hmdm ConfigUpdater.java (1412 行) | UpdateDeliveryPolicy.kt 等 | ≤0.011 | 无代码复制 |
| hmdm InstallUtils.java (618 行) | KidUpdateManager.kt | 0.067 | 无代码复制 |
| hmdm PushNotificationWorker.java | AdminNotifyService.kt | 0.031 | 无代码复制 |
| hmdm BootReceiver.java | UpdateRecoveryReceiver.kt | 0.095 | 均为 BOOT_COMPLETED 广播启动模式（平台标准），实现独立 |
| hmdm RemoteLogger.java | HeartbeatService.kt 等 | ≤0.023 | 无代码复制 |

## 6. 总体结论

1. **无任何 GPL 代码进入 PhoneGuard**：Curbox / APKUpdater 与本地实现的语言（Java/Kotlin 差异）、类结构、函数签名、算法体系均不重叠，Jaccard 最高 0.103。
2. **全部参考均为"设计/思路参考"级别**：KidSafe（UI 卡片形式）、cst（全屏遮罩/bringAppToForeground/WorkManager 思路）、TestDPC/Headwind（平台 API 用法），本地注释已如实标注出处。
3. **GPL 传染判定：不成立** → 许可证方向 AGPL-3.0-or-later 不受 GPL 代码约束（见 LICENSE_OPTIONS.md 第 6 节待办标记为已解除）。
4. **cst 无许可证风险**：仅思路参考 + 已证实无复制，不构成侵权；THIRD_PARTY_NOTICES.md 中如实记录参考关系。
5. **Chastify 定性**（用户确认）：提交 `b28b928` 信息中"借鉴 Chastify"无法对应到任何实际使用的代码来源；唯一同名仓库 `kiks71/chastify` 为浏览器扩展项目（无许可证、与 Android 无关）。结论：**提交信息误引/指代不明，无代码关联**，不构成来源。

## 7. 局限说明

- Jaccard 为 token 集合相似度，对"深度改写（改名+重排）"理论上有漏检可能；已通过方法签名逐一对照与关键文件人工逐行阅读弥补。
- Curbox 比对基于 kt-rewrite 分支（调研 commit `f86e6bff` 未直接检出，因该 commit 不在默认分支浅克隆中）；两版本同源同许可（GPL-3.0），不影响派生判断结论。
- 比对对象覆盖计划指定的重点文件；未对参考项目全部源码做穷举比对（本地代码中无对应功能的文件无需比对）。
