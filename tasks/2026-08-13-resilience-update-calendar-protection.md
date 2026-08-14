# 实施计划：远程更新、日历规则、守护与异常闭环

状态：用户已确认设备所有者迁移方案。Phase 0 正在实施，其余阶段尚未实现。

约束：每个任务保持小切片，原则上不超过 5 个生产文件；先写失败测试，再实现，再运行目标模块测试。每阶段通过后再进入下一阶段。

## Phase 0：发布身份与设备所有者可行性验证

- [ ] **Task 0.1 正式发布身份**
  - 生成正式 signing key，配置本地/CI 密钥注入，debug 使用独立 applicationId。
  - 记录签名证书 SHA-256，制作离线双份加密备份和恢复说明。
  - 验收：连续构建两个 release APK，签名证书一致；debug 不能覆盖 release。
  - Verify：`apksigner verify --print-certs kid-release.apk`
  - 依赖：无。

- [ ] **Task 0.2 OPPO device owner 探针**
  - 用独立测试构建验证 ADB 无账户配置或恢复出厂 QR provisioning。
  - 验证 `isDeviceOwnerApp`、`setUninstallBlocked`、`setPackagesSuspended`、PackageInstaller 静默自更新。
  - 验收：四项能力在 OPPO A92s 实际通过；失败时保留兼容模式，不实现伪静默方案。
  - Verify：真机脚本 + `dumpsys device_policy` + 安装回调记录。
  - 依赖：Task 0.1。

### Checkpoint 0

- [x] 用户确认采用 Fully Managed；恢复出厂仍须在真正执行前单独确认。
- [ ] 正式包名、签名证书和备份完成。
- [ ] 未通过 Checkpoint 0，不得部署 device owner 或远程更新到正式被控端。

## Phase 1：规则日历 v2

- [ ] **Task 1.1 日历领域模型与迁移**
  - 新增 `DayProfile`、`RuleProfile`、`DateOverride`、`RuleSetEnvelopeV2`。
  - v1 规则无损迁移为三个 profile；定义 schemaVersion/revision/timezone。
  - 验收：旧云端规则可读取，迁移后行为与旧版一致。
  - Verify：`:core:test`，覆盖序列化向前/向后兼容。
  - 依赖：无。

- [ ] **Task 1.2 日历解析器 TDD**
  - 实现明确日期 > 周末/周中的优先级、补班、跨午夜和本地日期分桶。
  - 验收：周中、周末、假期、补班、跨年、时区变更用例全绿。
  - Verify：`:core:test --tests *CalendarRule*`
  - 依赖：Task 1.1。

- [ ] **Task 1.3 管理端三套规则与假期日历**
  - 周中/周末/假期页签；复制配置；日历多选；补班标记；保存前差异预览。
  - 验收：可完成三套配置和日期覆盖，不允许重叠/非法时间段。
  - Verify：`:admin:test :admin:assembleDebug` + UI 手测。
  - 依赖：Task 1.2。

- [ ] **Task 1.4 revision 下发与 ACK**
  - CloudBase 保存不可变 revision；被控端原子写入 last-known-good；回报已应用 revision/profile/date。
  - 验收：断网仍执行旧规则；损坏新规则被拒绝并报警；管理端显示“待同步/已生效/失败”。
  - Verify：`:core:test :kid:test :admin:test` + 双端真机。
  - 依赖：Task 1.3。

### Checkpoint 1

- [ ] 真机验证工作日、周末、手选假期和补班日。
- [ ] 用量只计入被控端本地当天，跨午夜不混入昨天。

## Phase 2：健康状态与守护重构

- [ ] **Task 2.1 守护状态机与单例 job**
  - 修复 HeartbeatService/AccessibilityService 重复无限循环。
  - 建立 `GuardHealthSnapshot` 与状态枚举。
  - 验收：重复 start/reconnect 只有一个 loopId；进程重启可恢复。
  - Verify：`:kid:test` + 日志断言。
  - 依赖：无。

- [ ] **Task 2.2 轻量无障碍事件管线**
  - 事件入口只入 conflated channel；用量查询使用缓存/增量；记录 connected/event/probe 时间。
  - 验收：事件风暴不堆积，无 24 小时全量查询出现在每次回调中。
  - Verify：Robolectric/压力单测 + Android Profiler 真机抽查。
  - 依赖：Task 2.1。

- [ ] **Task 2.3 恢复入口**
  - 添加 boot/package-replaced receivers；重建通知、WorkManager、同步和健康基线。
  - 验收：重启和覆盖安装后，无需打开 UI 即恢复心跳。
  - Verify：A92s 重启/安装脚本。
  - 依赖：Task 2.1。

- [ ] **Task 2.4 OPPO 配置审计**
  - 引导并记录通知、自启动、后台活动、最近任务锁定、电池优化状态。
  - 验收：每项状态可在管理端诊断页看到，未知状态不会显示成正常。
  - Verify：A92s 逐项撤销/恢复。
  - 依赖：Task 2.1。

### Checkpoint 2

- [ ] 清最近任务、系统杀进程、重启、锁屏 8 小时后可恢复。
- [ ] 无障碍“关闭”和“开着但没连接”能被区分。

## Phase 3：服务端事故中心与远程诊断

- [ ] **Task 3.1 设备凭据与同步协议**
  - 绑定时签发每设备凭据；合并健康/规则/命令/更新同步；实现 token 轮换。
  - 验收：deviceId 单独泄露不能冒充设备；跨设备访问被拒绝。
  - Verify：backend 单测 + CloudBase 权限负向测试。
  - 依赖：无。

- [ ] **Task 3.2 事故聚合器 TDD**
  - OPEN/ACK/RESOLVED、dedupKey、严重度升级、恢复、维护窗口。
  - 验收：重复心跳异常只形成一个事故，恢复后自动关闭。
  - Verify：`:core:test --tests *Incident*`
  - 依赖：Task 3.1。

- [ ] **Task 3.3 CloudBase 定时健康评估**
  - 服务端按最后快照产生离线、规则漂移、版本落后事故。
  - 验收：管理端完全退出时，云端仍在阈值内生成/恢复事故。
  - Verify：云函数集成测试与人工推进时间测试。
  - 依赖：Task 3.2。

- [ ] **Task 3.4 管理端健康与事故详情**
  - 展示最后证据、严重度、建议动作、ACK、恢复历史和诊断摘要。
  - 验收：可回答“哪项坏了、何时开始、是否恢复、当前规则/版本是什么”。
  - Verify：`:admin:test :admin:assembleDebug` + 双端演练。
  - 依赖：Task 3.3。

- [ ] **Task 3.5 通知投递适配器**
  - 实现一个正式通道（TPNS/厂商推送或确认的可信 webhook）和管理端 WorkManager 兜底。
  - 验收：管理端进程退出时 CRITICAL 仍能送达；重复事故不轰炸，恢复有通知。
  - Verify：真机 kill 后端到端测试。
  - 依赖：Task 3.3；需要用户选择通道。

### Checkpoint 3

- [ ] 模拟 10 类异常，事故状态、通知和恢复全部闭环。
- [ ] 服务端检测不依赖管理端前台服务。

## Phase 4：安全远程更新

- [ ] **Task 4.1 manifest 与校验器 TDD**
  - ECDSA P-256 清单签名、包名/version/hash/signer/过期/反降级校验。
  - 验收：任何字段篡改、错误证书、错误包名、降级均拒绝。
  - Verify：`:core:test --tests *UpdateManifest*`
  - 依赖：Task 0.1。

- [ ] **Task 4.2 下载状态机**
  - 唯一 WorkManager、约束、断点、空间检查、临时文件、重试和进度回报。
  - 验收：断网/进程重启续传；失败不留下可安装的半包。
  - Verify：`:kid:test` + 网络故障真机测试。
  - 依赖：Task 4.1、Task 3.1。

- [ ] **Task 4.3 PackageInstaller 安装器**
  - Session 写入/fsync/commit；处理 pending/success/all failures；更新后恢复。
  - 验收：兼容模式明确请求用户确认；device owner 模式静默成功；结果均上报。
  - Verify：正确/错误/旧版本 APK 矩阵。
  - 依赖：Task 4.2、Task 0.2。

- [ ] **Task 4.4 发布与灰度工具**
  - 生成签名 manifest、上传、canary/stable、暂停、最小支持版本和审计。
  - 验收：私钥不进入仓库/客户端；可暂停发布；失败率升高自动停灰度。
  - Verify：临时环境发布演练。
  - 依赖：Task 4.1、Task 3.3。

### Checkpoint 4

- [ ] 测试设备先运行新版本 24 小时，再推正式被控端。
- [ ] 正式端更新后自动回报新版本与健康状态。

## Phase 5：设备所有者防护

- [ ] **Task 5.1 DPC 能力封装**
  - 能力检测、uninstall blocked、package suspended、未知来源限制、支持文案。
  - 验收：非 device owner 不误调用；必要应用不可加入挂起列表。
  - Verify：单测 + TestDPC 对照 + A92s。
  - 依赖：Task 0.2。

- [ ] **Task 5.2 规则执行双通道**
  - device owner 优先 package suspension；无障碍做快速侦测/兼容模式拦截。
  - 验收：无障碍进程异常时，已挂起应用仍无法启动；恢复后状态对账。
  - Verify：A92s kill/toggle 矩阵。
  - 依赖：Task 5.1、Phase 1、Phase 2。

- [ ] **Task 5.3 受控解除管理**
  - 管理端二次认证、短期 token、原因、审计；被控端解除后确认。
  - 验收：过期/重放/错误设备请求无效；正常流程可恢复设备控制权。
  - Verify：安全负向测试 + 真机恢复演练。
  - 依赖：Task 3.1、Task 5.1。

### Checkpoint 5

- [ ] 防卸载、权限关闭、应用绕过、恢复入口全部演练。
- [ ] 拨号、短信、系统 UI 和紧急能力不被误锁。

## Phase 6：离开前演练与交付

- [ ] **Task 6.1 72 小时稳定性/故障注入**
  - 按规格 9.2 的 OPPO 矩阵执行，保存时间线与远程证据。
  - 验收：无 P0/P1；所有注入故障在约定时间内检测并恢复/报警。
  - 依赖：Phase 1-5。

- [ ] **Task 6.2 无 USB 灾难演练**
  - USB 拔除后完成规则修改、诊断、异常通知、更新、临时加时和受控重启。
  - 验收：整套演练不使用 adb；失败项有明确人工恢复手册。
  - 依赖：Task 6.1。

- [ ] **Task 6.3 运维文档与备份核验**
  - 发布、暂停、热修复、密钥恢复、重新绑定、设备丢失/恢复出厂说明。
  - 验收：在新环境仅按文档可构建 release、发布 canary 并恢复配置。
  - 依赖：Task 6.2。

## 完成定义

- [ ] 所有自动化测试、lint、双端构建通过。
- [ ] 五轴评审覆盖正确性、安全、性能、可维护性、可运维性。
- [ ] 72 小时真机和无 USB 演练通过。
- [ ] 用户确认正式被控端可在离开后独立运行和更新。
