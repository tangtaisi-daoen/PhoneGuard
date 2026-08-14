# PhoneGuard 回归测试矩阵（阶段六B）

> 状态：A 部分自动测试结果已于 2026-08-14 实测回填（`gradlew test` 全绿，154 tasks BUILD SUCCESSFUL）；C 部分构建/Lint 实测回填；B 部分真机项由用户执行后勾选。
> 场景依据：DEEPSEEK_OPEN_SOURCE_RELEASE_PLAN.md 第 11 节。

## A. 自动化覆盖（:core / :admin / :kid JVM 测试，2026-08-14 实测通过）

| # | 场景 | 测试文件 | 结果 |
|---|---|---|---|
| A1 | 规则引擎（app/类别/总额/时段叠加取最短） | RulesEngineTest、TemporaryAllowanceRulesEngineTest | ✅ |
| A2 | 规则日历（周中/周末/假期/跨午夜） | RuleCalendarTest | ✅ |
| A3 | 规则信封编解码/同步健康 | RuleEnvelopeCodecTest、RuleSyncHealthTest | ✅ |
| A4 | 使用统计（分钟取整/类别/前台/在线判定） | UsageReportTest、ForegroundUsageCalculatorTest、InstalledAppFilterTest | ✅ |
| A5 | 异常生命周期（OPEN/ACK/RESOLVED/对账） | IncidentLifecycleTest、IncidentReconcilerTest、AnomalyAggregatorTest、ConditionReconcileGateTest | ✅ |
| A6 | 防护策略（无障碍健康/页面分类/返回导航） | AccessibilityHealthPolicyTest、ProtectionPageClassifierTest、ProtectionReturnNavigatorTest、ProtectionGuardPolicyTest | ✅ |
| A7 | 更新（manifest 验证器/投递策略/周期检查） | UpdateManifestVerifierTest、UpdateDeliveryPolicyTest、PeriodicUpdateCheckTest | ✅ |
| A8 | 后端编解码（绑定/事件/心跳快照） | CloudBaseBindingsTest、CloudBaseEventsCodecTest、CloudBaseUsageSnapshotTest | ✅ |
| A9 | 绑定安全加固（新增） | CloudBaseBindingsTest（isBindingEligible/selectLatestInviteCode） | ✅ |
| A10 | 托管策略/设备管理模式 | ManagedProtectionPolicyTest、ProtectionHealthTest、ProvisioningModePolicyTest | ✅ |

## B. 真机回归（OPPO A92s，用户执行）

### B1 报告与时区
- [ ] 今日/昨日使用报告边界正确；跨午夜（23:50 使用计入昨日/今日正确）
- [ ] 修改系统时区后报告归属正确（Asia/Shanghai 默认）

### B2 应用列表与规则
- [ ] 异常应用过滤（系统/无图标应用不显示）；应用名显示中文名
- [ ] 添加应用限制、时间滑块、规则保存下发；kid 90s 内生效
- [ ] 周中/周末/假期规则分别生效；跨午夜时段（21:00-7:00）拦截正确

### B3 权限与页面稳定性
- [ ] 使用情况访问/自启动/悬浮窗/无障碍四步引导页各开关状态正确
- [ ] 无障碍被杀（强制停止/系统回收）后：自动恢复 + 管理端收到异常
- [ ] 危险设置页面（无障碍开关页/用量页）退出→回到桌面→重新进入绑定首页，不循环
- [ ] 被控端桌面翻页/长按/删除应用时防护不误伤、不崩溃

### B4 Device Owner / 生命周期
- [ ] Device Owner 状态正确（Fully Managed 显示/能力）；重启后防护自动恢复
- [ ] 覆盖升级（release 安装 release）后心跳/更新状态正确（MY_PACKAGE_REPLACED）
- [ ] 解绑后：停止上报；重新绑定成功

### B5 远程更新
- [ ] 更新可用→下载→验证→用户确认→安装→版本更新；离线重试（指数退避）；失败回滚状态
- [ ] 更新清单篡改（改 URL/SHA）被拒（InvalidSignature/InvalidMetadata）

### B6 安装与安全（本次修复验证）
- [ ] 干净安装 admin/kid release 正常
- [ ] 绑定流程：生成邀请码→旧码失效（再生成时旧码 EXPIRED）→kid 绑定→重复绑定拒绝
- [ ] 邀请码过期（7 天）场景：改时间模拟过期→绑定被拒
- [ ] 升级安装（旧版明文会话数据迁移到加密存储后仍保持登录/绑定）
- [ ] 安全规则收紧后：admin 可读自己的规则/报告；kid 可上报；**跨用户读取被拒（403）**

## C. 构建与静态检查（2026-08-14 实测）

- [x] :core:test 全绿（含 :admin/:kid test，154 tasks BUILD SUCCESSFUL）
- [x] :admin:assembleDebug 全绿
- [x] :kid:assembleDebug 全绿
- [x] :admin:lint / :kid:lint 无新增错误（141 tasks BUILD SUCCESSFUL）
- [ ] release 双端构建 + SHA-256（发布构建执行；需签名配置 keystore.properties，gitignored 不入库）
