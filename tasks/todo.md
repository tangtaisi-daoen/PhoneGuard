# Task List: 双端手机管控 App

> 每个任务完成后勾选。验收标准见 `tasks/plan.md`。

## Phase 1: 仓库骨架与 CloudBase 验证

- [ ] Task 1: 工程骨架（Gradle 三模块 + git init + .gitignore）
- [ ] Task 2: core 数据模型 + 内置分类表（JUnit）
- [ ] Task 3: CloudBase 最小接入验证（注册/登录/写读）
- [x] ~~Checkpoint: build 全绿 + CloudBase 链路通 + 人类评审~~

## Phase 2: 账号与绑定（M1）

- [ ] Task 4: 管理端注册/登录（backend 认证封装）
- [ ] Task 5: 邀请码生成与绑定（双端配对）
- [ ] ~~Checkpoint: 绑定链路端到端 + 人类评审~~

## Phase 3: 规则与规则引擎（M2）

- [ ] Task 6: 规则引擎（JUnit ≥90%）
- [ ] Task 7: 规则管理 UI + 云端下发 + kid 缓存
- [ ] ~~Checkpoint: 规则链路全绿 + 人类评审~~

## Phase 4: 统计与报告（M3）

- [ ] Task 8: kid 使用统计（UsageStats + 心跳上报）
- [ ] Task 9: admin 实时状态 + 每日报告
- [ ] ~~Checkpoint: 统计数据一致 + 人类评审~~

## Phase 5: 实时拦截（M4）

- [ ] Task 10: 无障碍拦截 + 白名单
- [ ] Task 11: OPPO 引导流程 + 防护状态检测
- [ ] ~~Checkpoint: 拦截真机验证 + 人类评审~~

## Phase 6: 防护层与异常通知（M5）

- [ ] Task 12: 设备管理器防卸载 + 前台服务保活
- [ ] Task 13: 异常检测（断网/断权限/改时间/新装 app）
- [ ] Task 14: admin 异常轮询 + 本地通知 + 列表
- [ ] ~~Checkpoint: 异常场景全复现 + 人类评审~~

## Phase 7: 验收与分发（M6）

- [ ] Task 15: 真机验收清单 + release 打包 + 安装指引
