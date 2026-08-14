# Implementation Plan: 双端手机管控 App

> 当前执行批次：`tasks/2026-08-14-protection-and-remaining-closures.md`。该批次处理守护返回栈回归、远程更新、异常生命周期、规则补齐与韧性闭环。

## Overview

为哥哥（你）和弟弟（小学五年级）构建双端 Android 原生 Kotlin 应用：
- `:admin` 管理端：注册登录、生成邀请码、配置规则（按 app / 按类别 / 禁玩时段 / 每日总额）、实时状态、每日报告、异常通知（轮询+本地通知）
- `:kid` 被控端：邀请码绑定、无障碍服务实时拦截、UsageStats 统计上报、设备管理器防卸载、前台服务保活、异常检测上报
- `:core` 共享：数据模型、内置分类表、规则引擎（纯 Kotlin，JUnit ≥90%）、后端封装层（CloudBase，可替换）
- 后端：腾讯云开发 CloudBase 免费体验版（环境 `YOUR_ENV_ID`，3000 资源点/月）

验收设备：OPPO A92s（Android 10/11，无 GMS）。

## Architecture Decisions

1. **三模块单仓库**（core/admin/kid）：共享逻辑放 core，避免双端重复；core 不依赖 Android 框架（规则引擎可 JVM 单测）
2. **后端封装为可替换层**（core/backend）：CloudBase 接入方式（官方 Android SDK vs 云函数 HTTP API）先用最小验证任务（Task 3）探明，业务代码只依赖 backend 接口
3. **拦截=无障碍服务 + 统计=UsageStatsManager**：无障碍监听窗口变化，规则引擎判定超限/禁玩即 `Intent(HOME)` 踢出；使用时长从 UsageStats 取（不自己计时，避免被杀后失效）
4. **防卸载=DeviceAdmin**：激活设备管理器后系统拦截直接卸载；停用设备管理器即上报异常（对小学生防护足够，不防 root）
5. **心跳合并上报**：kid 60–120s 一次心跳，携带使用时长增量与防护状态；用量小的策略控制 CloudBase 资源点消耗（预算：2 设备 ≈ 3000 次/天内）
6. **管理端通知=30s 轮询异常事件表+本地通知**：规避国内厂商推送需企业资质的问题
7. **规则叠加=取最短可用时间**：app 级、类别级、时段禁用、每日总额同时生效时，以最先到期的为准

## Task List

### Phase 1: 仓库骨架与 CloudBase 验证

- [ ] **Task 1: 工程骨架** — Gradle 多模块（:core/:admin/:kid）+ version catalog + 初始化 git + .gitignore + AGENTS.md 已有
  - 验收：三模块 `assembleDebug` 出空 APK；`./gradlew build` 通过
  - Verify: `./gradlew :core:build :admin:assembleDebug :kid:assembleDebug`
  - Files: settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, core/admin/kid 各 build.gradle.kts + AndroidManifest.xml, .gitignore
- [ ] **Task 2: core 数据模型与分类表** — AppLimit/Rule/AppCategory/UsageReport/AnomalyEvent/Binding + 内置分类表（抖音/快手/王者荣耀/和平精英/微信等主流包名→类别）
  - 验收：JUnit 覆盖包名→类别命中、未知包名回退 OTHER
  - Verify: `./gradlew :core:test`
  - Files: core/.../data/*.kt, core/.../categories/*.kt, core/src/test/*
- [ ] **Task 3: CloudBase 最小接入验证** — 用环境 ID 做最小集成：连接环境、邮箱注册/登录、建集合写读一条记录
  - 验收：真机或本地测试跑通注册→写入→读回；若官方 Android SDK 不可用则改用云函数 HTTP API 方案并记录
  - Verify: 手动运行验证代码 + 控制台可见数据
  - Files: core/.../backend/*.kt（先落 backend 接口定义）

### Checkpoint: Phase 1
- [ ] `./gradlew build` 全绿
- [ ] CloudBase 注册/读写链路通
- [ ] 与人类评审后进入 Phase 2

### Phase 2: 账号与绑定（M1）

- [ ] **Task 4: 管理端注册/登录** — admin 邮箱+密码注册登录 UI + core backend 认证封装 + 本地会话保持
  - 验收：注册→登录→重启 app 保持登录；错误提示正确
  - Verify: `./gradlew :admin:test` + 真机手动
  - Files: admin/.../auth/*, core/.../backend/AuthApi.kt
- [ ] **Task 5: 邀请码绑定** — admin 生成 6 位邀请码 → kid 输入绑定 → 云端建立 pairing → 两端显示绑定状态
  - 验收：kid 绑定成功后 admin 端可见被控端在线/离线；重复绑定被拒
  - Verify: 双端真机手动 + `./gradlew :core:test`
  - Files: core/.../backend/BindingApi.kt, admin/.../pairing/*, kid/.../bind/*

### Checkpoint: Phase 2
- [ ] 管理端↔被控端绑定链路端到端可用
- [ ] 与人类评审后进入 Phase 3

### Phase 3: 规则与规则引擎（M2）

- [ ] **Task 6: 规则引擎** — computeRemainingMinutes、时段判定、类别匹配、app/类别/总额/时段叠加取最短；规则序列化
  - 验收：JUnit ≥90%：单 app 限时、分类共享额度、禁玩时段、总额兜底、叠加取最短、未知类别
  - Verify: `./gradlew :core:test`
  - Files: core/.../rules/*.kt, core/src/test/.../RulesEngineTest.kt
- [ ] **Task 7: 规则管理与下发** — admin 规则 CRUD UI（app 限时/分类限时/时段/总额）+ 云端同步 + kid 拉取缓存
  - 验收：admin 保存规则 → kid 立即生效（规则缓存刷新）；断网时用旧规则兜底
  - Verify: 双端真机 + `./gradlew :admin:test :core:test`
  - Files: admin/.../rules/*, core/.../backend/RuleApi.kt, kid/.../rulecache/*

### Checkpoint: Phase 3
- [ ] 规则引擎单测全绿，规则下发链路通
- [ ] 与人类评审后进入 Phase 4

### Phase 4: 统计与报告（M3）

- [ ] **Task 8: kid 使用统计** — UsageStats 前台 app 跟踪 + 当日汇总 + 心跳合并上报 + 本地日缓存
  - 验收：Robolectric 测试解析逻辑；真机核对"抖音今天用了 X 分钟"与控制台数据一致
  - Verify: `./gradlew :kid:test` + A92s 真机
  - Files: kid/.../stats/*, core/.../backend/ReportApi.kt
- [ ] **Task 9: admin 实时状态与报告** — 在线/离线、当前使用 app、今日各 app 时长、每日报告页
  - 验收：admin 能看到 kid 心跳状态与当日使用数据；报告按类别汇总
  - Verify: 双端真机 + `./gradlew :admin:test`
  - Files: admin/.../monitor/*, core/.../data/UsageReport.kt

### Checkpoint: Phase 4
- [ ] 统计链路端到端数据一致
- [ ] 与人类评审后进入 Phase 5

### Phase 5: 实时拦截（M4）

- [ ] **Task 10: 无障碍拦截** — 窗口变化监听 + 规则引擎判定 + 超限/禁玩踢回桌面 + 系统/拨号/桌面白名单
  - 验收：A92s 上抖音超 30 分钟被踢出且当日不可再进；21:00–7:00 娱乐 app 打开即拦截；微信不受影响
  - Verify: A92s 真机分场景测试（模拟器不可用）
  - Files: kid/.../service/GuardAccessibilityService.kt, kid/.../intercept/*
- [ ] **Task 11: OPPO 引导流程** — 无障碍/使用情况访问/电池优化/自启动分步骤引导 + 防护状态检测 + 心跳携带防护状态
  - 验收：A92s 上四步引导可完成；杀后台后防护状态正确上报；引导页可回看
  - Verify: A92s 真机 + `./gradlew :kid:test`
  - Files: kid/.../guide/*, docs/oppo-guide.md

### Checkpoint: Phase 5
- [ ] 拦截/引导/保活真机验证通过
- [ ] 与人类评审后进入 Phase 6

### Phase 6: 防护层与异常通知（M5）

- [ ] **Task 12: 设备管理器防卸载 + 前台服务保活** — DeviceAdmin 激活、防卸载、停用检测；前台服务常驻 + 通知
  - 验收：A92s 上直接卸载被拦截；设备管理器停用瞬间上报异常；前台服务 24h 不被杀（夜间测试）
  - Verify: A92s 真机
  - Files: kid/.../protect/*, kid/.../service/KidForegroundService.kt
- [ ] **Task 13: 异常检测** — 断网超时（心跳丢失）、使用情况访问被关、无障碍被关、系统时间回拨、新安装 app（ACTION_PACKAGE_ADDED）
  - 验收：各异常场景触发后云端出现对应事件记录
  - Verify: A92s 真机逐项 + Robolectric
  - Files: kid/.../detect/*, core/.../backend/AnomalyApi.kt
- [ ] **Task 14: admin 异常通知** — 30s 轮询异常表 + 本地通知渠道 + 异常列表页（卸载/断权限/离线/新装 app/改时间）
  - 验收：kid 触发异常 → admin 30s 内弹通知；列表可查看已处理/未处理
  - Verify: 双端真机 + `./gradlew :admin:test`
  - Files: admin/.../notify/*, admin/.../anomaly/*

### Checkpoint: Phase 6
- [ ] 全部异常场景端到端可复现
- [ ] 与人类评审后进入 Phase 7

### Phase 7: 验收与分发（M6）

- [ ] **Task 15: 真机验收清单 + 打包分发** — 全流程验收清单（docs/acceptance.md）+ release 签名 + 安装指引
  - 验收：按清单全流程走一遍全绿；双端 APK 可安装使用
  - Verify: A92s + 你的手机，按 docs/acceptance.md 逐项
  - Files: docs/acceptance.md, docs/install.md, 签名配置

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| CloudBase Android SDK 不成熟/文档缺失 | High | Task 3 最先验证；备选云函数 HTTP API（Retrofit 调 REST），backend 层隔离 |
| 免费环境 3000 资源点/月不够 | Med | 心跳 60–120s、批量上报、控制查询频率；真不够升级套餐 |
| OPPO 后台限制杀服务，拦截失效 | High | 前台服务 + 通知 + 电池优化/自启动引导；心跳携带防护状态，admin 可见"防护已降级" |
| 弟弟改系统时间绕过限时 | Med | 心跳带设备时间，服务端比对；检测时间回拨上报异常 |
| 无障碍权限被系统回收（Android 11+ 偶发） | Med | 引导页防护状态检测 + 异常上报，admin 收到"无障碍被关闭"通知 |
| 免费环境数据权限默认仅管理员可读写 | High | 环境权限设为"所有用户可读写"或自定义安全规则（Task 3 一并验证） |
| 拦截误伤系统 UI/拨号 | Med | 包名白名单（桌面/系统 UI/拨号/设置）→ 真机重点回归 |

## Open Questions

- 无阻塞项。CloudBase 数据安全规则细节在 Task 3 验证时确定。
