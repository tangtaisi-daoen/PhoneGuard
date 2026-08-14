# 被控端韧性、远程更新、日历规则与防护规格

状态：总体方案已于 2026-08-13 确认，正式采用 Fully Managed 设备所有者模式；Phase 0 实施中。

## 1. 背景与目标

目标设备为 OPPO A92s（Android 10/11，无 GMS）。管理者之后可能长期无法通过 USB 接触被控端，因此系统必须从“能运行”提升为“能远程判断是否健康、能恢复、能更新、能证明规则已生效”。

本阶段解决四类核心问题：

1. 被控端可安全远程更新，并能回报下载、校验、安装和新版本启动状态。
2. 规则区分周中、周末和假期，并支持中国节假日的调休工作日。
3. 无障碍、前台服务和后台任务被系统终止时，能检测、分级恢复并由服务端报警。
4. 防卸载、防改规则、防断权限和异常检测形成闭环，不再只显示一条事件。

## 2. 本轮源码调研

### 2.1 TestDPC

- `PackageInstallationUtils` 使用 `PackageInstaller.Session` 流式写入 APK，再通过 `IntentSender` 获取安装结果。
- `DevicePolicyManagerGatewayImpl` 直接展示设备所有者调用 `setUninstallBlocked` 的正确边界。
- 借鉴：安装会话、状态回调、设备所有者 API 的封装方式。
- 不直接复制代码，按本项目 Kotlin/MVVM 架构重新实现。

源码：

- https://github.com/googlesamples/android-testdpc/blob/d42d7f196d2db3d22ba4fca1e74faa5bc9b58d4e/src/main/java/com/afwsamples/testdpc/common/PackageInstallationUtils.java
- https://github.com/googlesamples/android-testdpc/blob/d42d7f196d2db3d22ba4fca1e74faa5bc9b58d4e/src/main/java/com/afwsamples/testdpc/DevicePolicyManagerGatewayImpl.java

### 2.2 Headwind MDM

- `ConfigUpdater`/`InstallUtils` 实现配置下发、APK 下载、安装队列和 `STATUS_PENDING_USER_ACTION`/成功/失败回调。
- `PushNotificationWorker` 采用实时通道失败后 15 分钟兜底，并定期强制全量同步，避免设备永久“丢失”。
- `BootReceiver` 以本次开机时间和最近启动时间判断是否需要重新初始化。
- `RemoteLogger` 本地落库、条件过滤、延迟上传，适合远程诊断。
- 借鉴：实时同步 + 持久任务兜底 + 定期全量对账、安装状态机、离线日志队列。
- 不借鉴：SHA-1 请求签名、可选信任任意 TLS 证书、写系统文件成为设备所有者等不安全/非公开 API 做法。

源码：

- https://github.com/h-mdm/hmdm-android/blob/6bf2ea29159a5aeb2a9d2bfd5e4128649d69f45c/app/src/main/java/com/hmdm/launcher/helper/ConfigUpdater.java
- https://github.com/h-mdm/hmdm-android/blob/6bf2ea29159a5aeb2a9d2bfd5e4128649d69f45c/app/src/main/java/com/hmdm/launcher/util/InstallUtils.java
- https://github.com/h-mdm/hmdm-android/blob/6bf2ea29159a5aeb2a9d2bfd5e4128649d69f45c/app/src/main/java/com/hmdm/launcher/worker/PushNotificationWorker.java
- https://github.com/h-mdm/hmdm-android/blob/6bf2ea29159a5aeb2a9d2bfd5e4128649d69f45c/app/src/main/java/com/hmdm/launcher/receiver/BootReceiver.java
- https://github.com/h-mdm/hmdm-android/blob/6bf2ea29159a5aeb2a9d2bfd5e4128649d69f45c/app/src/main/java/com/hmdm/launcher/util/RemoteLogger.java

### 2.3 Curbox

- `AppTimeConfig` 支持逐星期的时间段和逐日额度。
- `TimeGroupWindow` 正确处理跨午夜区间，并计算下一个规则切换时刻。
- `AppBlockerService` 用 conflated channel 将高频无障碍事件移出回调线程，避免堆积。
- `RestrictionComparator` 对“收紧/放宽”规则做保守比较，放宽限制可以延迟生效。
- `AntiUninstallBlocker` 会阻止进入设备管理和无障碍关闭页面，但它依赖无障碍本身，不能作为真正防卸载根基。
- 借鉴：规则日历模型、跨午夜算法、轻量事件入口、限制变更审计/延迟。
- 不直接复制 GPL 代码，仅借鉴设计模式并独立实现。

源码：

- https://github.com/curbox-app/curbox-android/blob/f86e6bfffd05b9b08edc33aebbb840fae23d4208/app/src/main/java/neth/iecal/curbox/data/models/AppBlocker.kt
- https://github.com/curbox-app/curbox-android/blob/f86e6bfffd05b9b08edc33aebbb840fae23d4208/app/src/main/java/neth/iecal/curbox/utils/TimeGroupWindow.kt
- https://github.com/curbox-app/curbox-android/blob/f86e6bfffd05b9b08edc33aebbb840fae23d4208/app/src/main/java/neth/iecal/curbox/services/AppBlockerService.kt
- https://github.com/curbox-app/curbox-android/blob/f86e6bfffd05b9b08edc33aebbb840fae23d4208/app/src/main/java/neth/iecal/curbox/utils/RestrictionComparator.kt
- https://github.com/curbox-app/curbox-android/blob/f86e6bfffd05b9b08edc33aebbb840fae23d4208/app/src/main/java/neth/iecal/curbox/blockers/AntiUninstallBlocker.kt

### 2.4 APKUpdater 与 Child Screen Time

- APKUpdater 的 `SessionInstaller` 处理安装会话、分包、进度、用户确认和会话清理；`UpdatesWorker` 用唯一周期任务调度检查。
- Child Screen Time 同时使用前台服务和 WorkManager，但其源码也证明 WorkManager 周期任务最低仍是 15 分钟，不能把它描述成实时保活。
- 借鉴：安装状态与进度模型、唯一工作、会话清理、前台/持久任务分层。

源码：

- https://github.com/rumboalla/apkupdater/blob/69b6fcdf52a7735ae17101efe1a0cd26222fb276/app/src/main/kotlin/com/apkupdater/util/SessionInstaller.kt
- https://github.com/rumboalla/apkupdater/blob/69b6fcdf52a7735ae17101efe1a0cd26222fb276/app/src/main/kotlin/com/apkupdater/worker/UpdatesWorker.kt
- https://github.com/childscreentime/cst/blob/78d17036d1e0feac0812402f88ac9825d1eb4d01/app/src/main/java/io/github/childscreentime/core/ScreenTimeApplication.java

## 3. Android 平台硬边界

### 3.1 普通安装模式不能承诺无人值守静默更新

普通侧载应用可以下载并校验自己的 APK，再提交 `PackageInstaller.Session`，但系统可能返回 `STATUS_PENDING_USER_ACTION`。Android 文档明确要求安装器始终准备处理用户确认；设备所有者或关联的配置文件所有者才属于可自动安装的明确例外。

结论：

- 普通模式：可以远程发现、下载、校验并弹出安装确认，不能保证孩子不操作时完成安装。
- 设备所有者模式：可以静默安装/更新，是离开前应完成的一次性迁移方案。

官方依据：

- https://developer.android.com/reference/android/content/pm/PackageInstaller
- https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)
- https://developer.android.com/work/dpc/dedicated-devices/cookbook

### 3.2 普通 Device Admin 不等于可靠防卸载

当前 `KidDeviceAdminReceiver` 只是旧式 Device Admin。Android 9 起部分策略已弃用，Android 10 进一步限制。`setUninstallBlocked` 要求设备所有者/配置文件所有者，普通 Device Admin 无权调用。

官方依据：

- https://developer.android.com/work/device-admin
- https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setUninstallBlocked(android.content.ComponentName,%20java.lang.String,%20boolean)

### 3.3 无障碍不能由普通应用自行保证开启

无障碍服务生命周期由系统管理，启动由用户在设置中明确开启。普通应用不能把调用 `startService` 当成恢复无障碍。当前项目未声明 `WRITE_SECURE_SETTINGS`，`Settings.Secure.putString` 的“自动恢复”实际会失败；即使通过一次性 ADB 特授，也不应作为正式架构依赖。

官方依据：

- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- https://developer.android.com/guide/topics/ui/accessibility/service

### 3.4 设备所有者迁移有一次性成本

正式部署推荐在新机或恢复出厂后的设备上通过二维码完成 fully managed provisioning。开发阶段可在无账户、无其他用户/工作资料的设备上通过 ADB 设置 device owner。当前设备必须先做兼容性试验，不能假设可原地升级。

官方依据：

- https://developer.android.com/work/dpc/dedicated-devices
- https://developer.android.com/work/guide#provision_a_fully_managed_device

## 4. 现有代码审计结论

1. `HeartbeatService.onStartCommand()` 每次调用都会再启动一个无限循环，服务重复启动时可能产生多个心跳循环。
2. `GuardAccessibilityService.onServiceConnected()` 每次连接都会再启动轮询协程，缺少幂等 job 管理。
3. 无障碍每次窗口变化都会查询最长 24 小时 UsageEvents 并汇总当日数据，回调过重，增加卡顿、耗电和被杀概率。
4. 健康状态只判断“设置中是否启用”，没有记录 `onServiceConnected`、最近事件、最近成功判定，无法识别“开关开着但服务没工作”。
5. Manifest 没有开机和 `MY_PACKAGE_REPLACED` 恢复接收器；WorkManager 只能 15 分钟级兜底。
6. 异常离线判断位于管理端 30 秒轮询服务中；管理端进程被杀后，服务端不会主动形成离线事故。
7. 事件只有时间点和文案，没有 OPEN/ACK/RESOLVED 状态、严重度、去重键、证据和恢复时间。
8. 规则只有一套 `RuleSet`，没有日期、时区、规则 revision 的应用确认，也无法表达调休工作日。
9. 被控端以匿名身份登录并以 deviceId 工作；在引入远程命令和更新前，需要升级为一次性绑定令牌 + 每设备凭据，避免伪造设备身份。
10. 当前安装的是开发构建。远程更新前必须固定正式包名和正式签名证书；签名不一致的 APK 无法覆盖更新。

## 5. 推荐总体架构

### 5.1 两种能力档位

#### A. 兼容模式

- 保留普通 Device Admin、无障碍、UsageStats、悬浮窗。
- 支持远程下载/校验更新，但安装需要被控端确认。
- 防卸载和无障碍防关闭只是尽力而为。
- 用于迁移期和不愿恢复出厂的设备。

#### B. Fully Managed 设备所有者模式（推荐）

- 被控端同时成为 DPC 与业务客户端。
- `setUninstallBlocked` 阻止卸载自身。
- 通过 `PackageInstaller.Session` 静默更新。
- 对达到限制的普通应用优先用 `setPackagesSuspended` 做系统级阻断，无障碍降级为快速侦测和补充拦截，不再是唯一防线。
- 可配置禁止未知来源安装、提供受管设置说明，必要时远程重启。
- 不启用全屏 kiosk，不阻止拨号、短信、设置中的必要恢复入口，不把手机变成不可用设备。

### 5.2 同步与健康链路

被控端每次同步使用一个合并端点，一次请求完成：

- 上传健康快照、用量增量、安装清单差异和命令回执；
- 拉取最新规则 revision、待执行命令、更新 manifest；
- 返回服务端时间，供时钟回拨检测。

链路分层：

1. 在线主通道：前台守护服务，正常 2 分钟一次，状态变化立即上报。
2. 兜底：唯一 WorkManager 周期任务，15 分钟检查并强制全量对账。
3. 恢复入口：`BOOT_COMPLETED`、`LOCKED_BOOT_COMPLETED`（能否使用取决于存储设计）、`MY_PACKAGE_REPLACED`。
4. 服务端健康评估器：每 2 到 5 分钟根据最后快照生成/恢复事故，不依赖管理端存活。
5. 通知适配器：TPNS/厂商推送优先；没有资质时可配置邮件或可信 webhook。管理端轮询只做最后兜底。

## 6. 详细功能规格

### 6.1 安全远程更新

#### 发布前提

- 立即生成并固定正式 release signing key；离线加密备份至少两份。
- 正式被控端第一次安装/设备所有者配置必须使用该签名。
- debug 与 release 使用不同 applicationId，防止以后签名冲突。
- 更新只允许自己的固定包名，不提供任意 APK/任意 shell 命令能力。

#### 更新清单

`UpdateManifest` 至少包含：

- `schemaVersion`
- `releaseId`
- `channel`：canary/stable
- `packageName`
- `versionCode`、`versionName`
- `minSupportedVersionCode`
- `url`、`sizeBytes`
- `apkSha256`
- `signingCertSha256`
- `mandatory`、`deadlineAt`
- `rolloutPercent`
- `issuedAt`、`expiresAt`
- `signatureAlgorithm`、`manifestSignature`

清单使用离线 ECDSA P-256 私钥签名；被控端只内置公钥。HTTPS 负责传输，清单签名负责即使存储/CDN 被篡改也拒绝更新。

#### 客户端状态机

`IDLE -> AVAILABLE -> DOWNLOADING -> VERIFYING -> READY -> INSTALLING -> RESTART_PENDING -> HEALTH_CHECK -> SUCCEEDED/FAILED`

要求：

- 断点下载、临时文件、剩余空间预检、指数退避。
- 依次校验 manifest 签名、包名、versionCode 单调递增、过期时间、文件大小、SHA-256、APK 签名证书。
- 普通模式处理 `STATUS_PENDING_USER_ACTION`；设备所有者模式静默提交。
- 安装回调必须上报错误码和系统消息；`MY_PACKAGE_REPLACED` 后重启初始化并上报新版本健康。
- Android 不支持常规降级覆盖；“回滚”实现为停止灰度并发布更高 versionCode 的修复版。
- 先在测试设备 canary 验证 24 小时，再推 stable。单台正式被控端不能充当首个测试样本。

### 6.2 周中、周末、假期规则

新增模型：

```text
RuleSetEnvelope
  schemaVersion
  revision
  timezoneId
  weekdayProfile
  weekendProfile
  holidayProfile
  dateOverrides[]
  effectiveAt
  generatedAt

DateOverride
  localDate
  profile = WEEKDAY | WEEKEND | HOLIDAY
  label
```

规则语义：

- 周中默认周一到周五，周末默认周六/周日。
- 假期由管理端日历多选；连续范围在保存时展开为明确日期。
- 中国“补班”不能按周末处理，允许把某个周六/周日覆盖为 `WEEKDAY`。
- 优先级：明确日期覆盖 > 周末/周中默认；假期本质上也是明确日期覆盖到 HOLIDAY。
- 每个 profile 都有 app 限额、分类共享限额、每日总额、禁用时段；支持“从周中复制到周末/假期”。
- 跨午夜时间段按开始日所属 profile 计算；用量按被控端 `timezoneId + LocalDate` 分桶，不能混入前一天。
- 被控端回报 `appliedRuleRevision`、`evaluatedLocalDate`、`evaluatedProfile`、`timezoneId`，管理端明确显示是否已生效。
- 年度法定节假日可提供“导入建议”，但导入后仍变成可审阅的明确日期，不在运行时依赖外部节假日服务。

### 6.3 无障碍和守护韧性

健康状态拆为：

- `accessibilityConfigured`：设置里是否启用。
- `accessibilityConnected`：是否执行过 `onServiceConnected` 且本进程实例存活。
- `lastAccessibilityEventAt`：最近事件时间。
- `lastForegroundProbeAt`/`lastForegroundProbeSuccessAt`。
- `guardProcessStartedAt`、`guardBootId`、`guardServiceLoopId`。

实现原则：

- 无障碍单独进程 `:guard`，隔离 UI/同步崩溃；但明确说明 force-stop 仍会停止整个包。
- `onAccessibilityEvent` 只复制必要字段并放入 conflated channel；规则查询和 UsageStats 汇总移到后台缓存。
- `onServiceConnected` 和守护服务启动均使用单例 job，重复回调不能产生重复循环。
- 前台服务负责任务编排，不假装自己能直接启动无障碍。
- `BOOT_COMPLETED`、应用更新后重建同步、工作任务、通知渠道和健康基线。
- “已启用但未连接”持续 3 分钟为 HIGH；“已关闭”为 CRITICAL；设备所有者模式可先尝试受支持的进程/设备恢复，仍失败则通知管理端和被控端打开设置，不使用无障碍自动点击安装器。
- OPPO 引导新增可验证清单：自启动、允许后台活动、锁定最近任务、电池不优化、通知开启；每项有最后确认时间和健康状态。

### 6.4 防卸载与防篡改

设备所有者模式：

- 对自身调用 `setUninstallBlocked`。
- 管理端远程发起“解除管理”，需二次验证、短期令牌、原因和审计记录，被控端收到后再解除防卸载。
- 可禁止未知来源安装，减少通过新装工具绕过；不阻止系统更新和必要应用安装。
- 设置页的受限行为显示原因与管理者联系方式。
- 不启用 Accessibility 自动点击，不隐藏应用，不收集短信/聊天内容，不阻断紧急拨号。

兼容模式：

- Device Admin + 设置页检测/返回仅作为延缓措施。
- 规则放宽、关闭防护、本地解绑采用管理密码或远程批准；借鉴 Curbox，把本地“放宽”操作延迟，收紧立即生效。
- 明确提示此模式无法对抗先关闭无障碍再卸载、恢复模式卸载、root/解锁 bootloader、恢复出厂。

### 6.5 异常检测中心

异常从“事件列表”升级为事故状态机：

```text
Incident
  incidentId / dedupKey / deviceId
  type / severity
  status = OPEN | ACKNOWLEDGED | RESOLVED
  firstSeenAt / lastSeenAt / resolvedAt
  occurrenceCount
  evidence
  recommendedActions
  relatedRuleRevision / appVersion
```

设备侧检测：

- 无障碍关闭、已启用但未连接、长时间无事件/探测失败。
- Usage Access、悬浮窗、通知、Device Admin/Device Owner、防电池优化状态变化。
- 守护循环重复/停止、开机未恢复、更新后未恢复。
- 新装/卸载应用、被控端自身版本落后、更新连续失败、空间不足。
- 系统时间回拨、时区变化、服务端时间偏差。
- 规则 revision 长时间未应用或本地规则损坏。
- 重复进入卸载/权限关闭页面、反复尝试打开被禁应用。

服务端检测：

- 5 分钟无快照：WARNING；15 分钟：HIGH；30 分钟：CRITICAL，阈值可配置。
- 命令/规则/更新超过 TTL 未 ACK。
- 版本发布后失败率或失联率升高，自动暂停灰度。
- 状态恢复时自动关闭事故并发送“已恢复”，防止管理者只看到旧警报。

通知要求：

- 去重、升级、恢复通知；维护窗口静默；严重事件不可被普通信息淹没。
- 管理端展示“最后一次健康证据”而不是只显示在线/离线。
- 服务端形成事故与通知投递解耦，管理端进程被杀也不影响离线判定。

### 6.6 远程诊断与命令

这是用户未明确提出、但离开后必须具备的功能。

健康快照包括：应用版本、系统版本、启动时间、最后同步、网络、电量、充电、可用空间、规则 revision、更新状态、权限/服务状态、设备所有者状态、最近错误码。默认不上传屏幕内容、聊天内容、联系人、定位或完整日志。

远程命令只做白名单：

- `SYNC_NOW`
- `RETRY_UPDATE`
- `UPLOAD_DIAGNOSTICS`
- `REFRESH_GUARD`
- `REBOOT_DEVICE`（仅设备所有者、无通话时）
- `TEMP_ALLOWANCE`

每条命令有 commandId、设备绑定、创建者、TTL、nonce、参数 schema、ACK 和结果；严禁任意 shell、任意 URL 下载和动态代码执行。

## 7. 用户未提到但建议纳入的功能

按优先级排序：

1. **正式签名与发布通道**：没有它，远程更新不可持续。
2. **远程健康面板与诊断包**：离开后定位问题的唯一证据链。
3. **规则 revision/ACK 与漂移检测**：证明管理端保存的规则确实在被控端运行。
4. **服务端事故检测与独立通知通道**：解决管理端也被杀时没人报警。
5. **紧急白名单与临时加时**：拨号、短信、相机、地图等基础能力永不误锁；加时有到期时间和审计。
6. **新应用默认策略**：新装娱乐应用默认进入待审核或受限组，避免换一个包名绕过。
7. **规则变更审计和历史版本**：记录谁在何时把什么从多少改到多少，可一键恢复上一版。
8. **到期前提醒**：剩余 10/5/1 分钟提示，减少突然被拦截带来的冲突。
9. **离线最后已知规则**：断网继续执行，禁止因服务器异常自动放开；必要应用始终 fail-open。
10. **数据保留与隐私设置**：原始事件短期保存、日报长期聚合；远程日志可筛选和脱敏。
11. **备份和灾难恢复**：签名密钥、CloudBase 配置、规则导出、重新绑定流程必须有离线备份。
12. **学校/睡眠模板和应用组共享额度**：借鉴 Curbox 的组额度、日程和临时解锁，但放在稳定性之后。

不建议当前加入：读取聊天/短信内容、隐蔽录屏/监听、自动点击系统安装界面、任意远程控制、用无障碍伪装静默安装。这些会显著扩大隐私与安全风险，也不可靠。

## 8. 安全与威胁模型

受保护资产：规则、设备绑定、更新签名密钥、远程命令、健康数据、CloudBase 权限。

主要威胁：

- 被控端用户关闭权限、结束进程、卸载、改时钟、安装替代应用。
- 网络中间人或存储桶被篡改后投放恶意 APK。
- 设备 ID 被伪造、命令被重放、管理端账户被盗。
- 错误规则或错误更新导致手机不可用。

控制措施：

- 正式签名、签名 manifest、APK hash + signer 双重校验、反降级、灰度发布。
- 每设备不可预测凭据、短期 access token、命令 TTL/nonce/idempotency。
- CloudBase 服务端按 adminUid/deviceId 做对象级授权，客户端不能写其他设备事故或 release manifest。
- 必要应用硬编码最低安全白名单；错误规则验证失败时使用最后已知良好版本。
- 所有高风险解除动作二次认证并审计。
- 签名私钥不进入仓库、不进入 APK、不由 CloudBase 客户端读取。

## 9. 验收标准

### 9.1 自动化

- 日历引擎覆盖周中、周末、假期、补班、跨午夜、月/年边界、时区变化。
- 更新校验覆盖篡改清单、hash 不符、证书不符、降级、过期、错误包名、下载中断、低空间。
- 命令处理覆盖重复命令、过期、乱序、错误设备和重试。
- 事故聚合覆盖去重、严重度升级、ACK、恢复和维护窗口。
- `gradlew test :admin:assembleDebug :kid:assembleDebug lint --no-daemon` 全绿。

### 9.2 OPPO 真机

- 重启、锁屏 8 小时、超级省电、清最近任务、手动结束进程、断网 24 小时、恢复网络。
- 关闭每项权限、关闭无障碍、停用 Device Admin、尝试卸载、改时间/时区、安装新应用。
- 安装正确/错误签名 APK、更新下载中断、空间不足、更新后重启。
- 设备所有者模式下验证静默更新、防卸载、应用挂起与远程重启；兼容模式验证安装确认降级流程。
- 至少连续 72 小时老化，期间规则、用量、事故和更新状态可从管理端完整追踪。

## 10. 实施前必须确认的决策

1. 已确认：把被控端迁移为 fully managed 设备所有者；优先尝试移除现有账户后的 ADB 配置，系统仍拒绝时才恢复出厂。
2. 是否现在固定正式 applicationId 和 release signing key；这是远程更新的硬前提。
3. 告警通知选择 TPNS/厂商推送，还是先用邮件/可信 webhook；数据库事故中心不依赖此选择。
4. 假期首版采用“手工多选 + 补班覆盖”，年度法定日历导入放第二阶段，是否接受。
