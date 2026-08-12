# Spec: 双端手机管控 App（给弟弟用）

## Objective

**构建目标**：一个双端 Android 应用，让哥哥（你）能远程管控弟弟 OPPO A92s 的手机使用：
- 按 app 限时（如抖音每天 30 分钟）
- 按类别限时（游戏类共 1 小时、短视频类共 40 分钟）
- 禁玩时段（如 21:00–7:00 娱乐类不可用）
- 每日娱乐总额（如一天最多 2 小时）
- 高防护：防卸载、防离线、异常通知

**用户**：管理端 = 你（哥哥）；被控端 = 弟弟（小学五年级）。
**成功标准**：规则由你远程配置并实时生效；弟弟超限被踢出；卸载/断网/关权限/新装 app 你都能收到通知；弟弟正常使用（电话、微信、学习类）不受影响。

## Tech Stack

- 语言：Kotlin（原生 Android）
- 构建：Gradle 8.x + Android Gradle Plugin 8.x，Kotlin 2.x
- 单仓库双 module：`:admin`（管理端）、`:kid`（被控端）、`:core`（共享：Bmob 封装、数据模型、规则引擎）
- minSdk 26 / targetSdk 34（OPPO A92s = Android 10/11，无 GMS）
- 后端：腾讯云开发 CloudBase（免费体验版环境：3000 资源点/月，超额暂停不扣费；邮箱+密码登录、云数据库、云函数）
- Android 接入：优先 CloudBase 官方 Android SDK；不成熟则走云函数 HTTP API（core 封装层隔离，二选一不影响业务代码）
- 异步：Kotlin Coroutines
- 测试：JUnit 4 + Robolectric（规则引擎/限额计算）；Espresso 冒烟；真机手工验收清单（无障碍不可模拟器）

## Commands

```bash
# 构建（在仓库根目录，使用 Gradle Wrapper）
./gradlew :admin:assembleDebug          # 管理端 APK
./gradlew :kid:assembleDebug            # 被控端 APK
# 测试与检查
./gradlew test                          # 全部单元测试
./gradlew :core:test :kid:test :admin:test
./gradlew lint
# 真机安装
adb install app/admin/build/outputs/apk/debug/admin-debug.apk
adb install app/kid/build/outputs/apk/debug/kid-debug.apk
```

## Project Structure

```
手机管控/
├── AGENTS.md                # agent 工作规则
├── specs/                   # 规格文档（本文件）
├── tasks/                   # plan.md + todo.md（规划产物）
├── docs/                    # OPPO 引导流程、部署说明
├── settings.gradle.kts
├── build.gradle.kts
├── core/                    # 共享模块
│   └── src/main/java/com/familyguard/core/
│       ├── data/            # 数据模型（规则/报告/异常事件）
│       ├── backend/         # 后端封装（CloudBase：认证/查询/上报；可替换层）
│       ├── rules/           # 规则引擎（纯 Kotlin，可单测）
│       └── categories/      # 内置分类表 + 覆盖包名
├── admin/                   # 管理端 module（包 com.familyguard.admin）
│   └── src/main/java/com/familyguard/admin/
│       ├── auth/            # 注册/登录/邀请码生成
│       ├── rules/           # 规则管理 UI（app/分类/时段/总额）
│       ├── monitor/         # 实时状态 + 报告
│       └── notify/          # 30s 轮询 + 本地通知
└── kid/                     # 被控端 module（包 com.familyguard.kid）
    └── src/main/java/com/familyguard/kid/
        ├── bind/            # 邀请码绑定
        ├── service/         # 无障碍服务 + 前台服务 + 拦截执行
        ├── stats/           # UsageStats 采集 + 上报
        ├── protect/         # 设备管理器防卸载 + 异常检测
        └── guide/           # OPPO 权限引导流程（按步骤）
```

## Code Style

Kotlin 官方风格 + 以下约定：
- MVVM：Activity/Fragment 只管 UI，逻辑在 ViewModel，纯逻辑在 core（可单测）
- 命名：包名小写、变量驼峰、常量 UPPER_SNAKE、界面后缀 Activity/Fragment/ViewModel
- 不写无用注释；关键业务（规则引擎、拦截判定）写中文注释说明"为什么"
- 字符串全部进 res/values/strings.xml；无硬编码中文
- 示例（规则引擎风格）：

```kotlin
// core 内纯 Kotlin，无 Android 依赖，便于 JVM 单测
data class AppLimit(
    val packageName: String,
    val dailyMinutes: Int,        // 0 = 不限制
    val category: AppCategory,    // GAME / SHORT_VIDEO / SOCIAL / TOOL / OTHER
    val bannedTimeRanges: List<TimeRange> = emptyList(),
)

fun computeRemainingMinutes(
    limit: AppLimit,
    todayUsed: Long,              // 毫秒
    now: LocalTime,
): Long {
    val banned = limit.bannedTimeRanges.any { now in it }
    if (banned) return 0L
    return maxOf(0L, limit.dailyMinutes * 60_000L - todayUsed)
}
```

## Testing Strategy

| 层级 | 框架 | 覆盖内容 |
|---|---|---|
| JVM 单元测试（core） | JUnit4 + kotlin.test | 规则引擎：限额计算、时段判定、类别匹配、总额与单 app 叠加逻辑 |
| Robolectric（kid） | Robolectric 4.x | UsageStats 解析、异常检测（断权限/卸载标记/改时间） |
| Espresso 冒烟（admin） | AndroidX Test | 注册登录、规则保存往返 |
| 真机手工验收（必须） | OPPO A92s | 无障碍拦截实测、防卸载实测、后台保活实测（模拟器无法覆盖） |

覆盖率目标：core 规则引擎 ≥ 90%，异常检测 ≥ 80%，UI 不设硬指标。

## Boundaries

- **Always**：改逻辑先写/改测试（TDD）；提交前 `test` + `lint` 全绿；规则引擎变更必须补单测；新权限申请必须过 spec 评审
- **Ask first**：Bmob 数据表 schema 变更；新增第三方依赖；无障碍拦截策略调整（涉及被踢出时机、白名单）；降低防护等级
- **Never**：向被控端收集声明范围外的数据（位置、联系人、短信）；root/系统签名等绕过方案；密码、邀请码、App Key 硬编码进仓库；删除失败测试

## 里程碑（M1→M6，规划阶段细化）

- **M1 骨架**：Gradle 双 module + Bmob 接入 + 管理端注册/登录 + 邀请码绑定
- **M2 规则管理**：内置分类表 + 规则 CRUD + 云端同步下发
- **M3 统计上报**：被控端 UsageStats 采集 + 上报 + 管理端实时状态/每日报告
- **M4 实时拦截**：无障碍服务 + 规则引擎执行（app/类别/时段/总额）+ OPPO 后台引导
- **M5 防护层**：设备管理器防卸载 + 前台服务保活 + 异常检测（卸载/断权限/断网超时/改时间/新装 app）+ 管理端轮询通知
- **M6 验收分发**：真机全流程验收清单 + 打包 + 安装指引文档

## Success Criteria

- 管理端注册登录 → 生成邀请码 → A92s 输入绑定 → 管理端可见被控端在线状态
- 配置"抖音 30 分钟/天"后，A92s 当天累计使用满 30 分钟 → 自动踢回桌面且当天无法再进（时段规则同理）
- 类别/总额规则正确叠加（最短可用时间为准）
- 21:00–7:00 娱乐类 app 打开即被拦截，微信/电话不受影响
- 弟弟卸载被控端 → 无法直接完成（设备管理器拦截）；强停设备管理器 → 管理端收到"防护被解除"通知
- 被控端断网 5 分钟 → 管理端收到"离线"通知；重连恢复
- 弟弟安装新 app → 管理端收到"新应用安装"通知
- 管理端每日报告：各 app 使用时长按类别汇总展示

## Open Questions

1. 包名偏好？（暂定 com.familyguard.*）
2. CloudBase 免费体验环境是否已创建？（需要环境 ID）
3. 是否需要"远程锁机/一键禁用"这类强制手段？（M5 之外，暂不做）
