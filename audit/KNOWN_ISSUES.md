# PhoneGuard 基线已知问题与限制清单（阶段一）

> ⚠️ **历史基线报告**：本清单反映审计基线（2026-08-14）的状态；其中第 3 节发布前待办已在发布版全部执行完毕（见状态列）。
> 冻结时间：2026-08-14
> 本清单记录审计基线上的**已知问题、文档滞后项、设计限制与发布前待办**，供阶段六（功能修复与回归）与阶段七（发布构建）引用。
> 注意：本清单只记录问题，不在阶段一修改业务逻辑。

## 1. 文档与任务状态滞后

| 编号 | 问题 | 说明 |
|---|---|---|
| K1 | `tasks/todo.md` Phase 1 Checkpoint 标注"待人类确认" | 该 Checkpoint 未关闭；实际功能已推进到 Phase 7 |
| K2 | `tasks/todo.md` Phase 3/4 的 Task 6-9 未勾选 | 对应功能已实现（git log：规则引擎/规则云端同步/使用统计/报告均已提交），todo 未同步 |
| K3 | `tasks/plan.md` / `todo.md` 勾选状态与代码现实不一致 | 需在阶段六统一修订任务文档 |
| K4 | `docs/acceptance.md` 真机验收清单全部未勾选 | 阶段六必须逐项执行并记录结果（OPPO A92s） |

## 2. 产品设计限制（已知且接受，不视为缺陷）

| 编号 | 限制 | 来源 |
|---|---|---|
| L1 | 拦截依赖无障碍服务 + 使用情况访问；两项被关闭即失效（会通知管理端） | docs/acceptance.md |
| L2 | 防卸载不防 root / 刷机（设备管理器机制局限） | docs/acceptance.md |
| L3 | ColorOS 不同版本自启动设置入口不同（引导页已做多入口兼容） | docs/acceptance.md |
| L4 | CloudBase 免费体验版 3000 资源点/月，多设备高频使用可能超配额 | tasks/plan.md Risks |
| L5 | OPPO/ColorOS 后台限制可能杀服务（前台服务 + 自启动引导 + 防护状态上报兜底） | tasks/plan.md Risks |
| L6 | 无障碍权限 Android 11+ 可能被系统回收（引导页检测 + 异常上报兜底） | tasks/plan.md Risks |
| L7 | 系统时间回拨可绕过限时（心跳带设备时间比对 + 时间回拨异常上报，非绝对防护） | tasks/plan.md Risks |

## 3. 发布前待办（阶段七执行，基线已记录）

| 编号 | 待办 | 状态 |
|---|---|---|
| P1 | Git 历史作者信息匿名化（用户已批准改写历史） | ✅ 已完成（filter-repo 全量重写，发布版 49 个提交作者为匿名地址） |
| P2 | 环境 ID `YOUR_ENV_ID` 占位符化（用户已批准） | ✅ 已完成（全部替换为占位符，README/DEPLOY 附自建指引） |
| P3 | `keystore.properties.example` / `cloudbase.local.properties.example` 模板 | ✅ 已完成（已随发布版提交） |
| P4 | README / LICENSE / NOTICE / SECURITY.md / 贡献指南 / 隐私政策 | ✅ 已完成（随发布版提交；隐私政策生效日期待公开当天填写） |
| P5 | 更新清单 URL 与 provisioning 文档中的托管地址随 P2 同步处理 | ✅ 已完成（全部为 `YOUR_ENV_ID-0000000000.tcloudbaseapp.com` 占位符） |
| P6 | `docs/backup-before-fully-managed.md` 保持 .gitignore 忽略 | ✅ 已完成 |

## 4. 工程状态观察（非缺陷，记录在案）

| 编号 | 观察 | 说明 |
|---|---|---|
| O1 | 工作树存在 44 个已修改文件 + 89 个未跟踪文件（基线时刻） | 基线需固定为可复现工作点（见 BASELINE.md 决策） |
| O2 | 构建环境依赖 `JAVA_HOME`（本机指向 Android Studio JBR，OpenJDK 25）与 `ANDROID_HOME`；`local.properties` 不存在 | 环境变量驱动；发布文档需写明前提 |
| O3 | 全部源码中文注释与文案为 UTF-8（读工具验证正常）；Windows 终端显示乱码属显示层问题，不影响构建 | — |
| O4 | `.gitattributes` 启用 `text=auto`，Windows 下 Git 提示 LF→CRLF 转换 | 正常现象，无需处理 |
| O5 | `tasks/plan.md` 注明被控端已迁移 Fully Managed / Device Owner 场景（docs/fully-managed-migration.md），验收文档仍以引导页 5 步为准 | 阶段六需补充 Device Owner 验收条目 |
