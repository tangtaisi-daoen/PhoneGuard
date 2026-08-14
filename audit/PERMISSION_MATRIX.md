# PhoneGuard 权限用途表（阶段五）

> 来源：admin/kid AndroidManifest.xml 实测（2026-08-14）；kid 10 项 / admin 3 项，全部有明确用途，无冗余权限。

## 被控端（kid）权限

| 权限 | 用途 | 最小化判定 |
|---|---|---|
| INTERNET | CloudBase 通信/更新下载 | ✅ 必要 |
| PACKAGE_USAGE_STATS | 使用时长统计 + 前台应用检测（核心能力） | ✅ 必要（引导页单独引导授权） |
| QUERY_ALL_PACKAGES | 展示/管控完整已装应用列表 | ✅ 必要（有代码注释说明；替代方案不可行） |
| SYSTEM_ALERT_WINDOW | 全屏拦截浮层（无法绕过） | ✅ 必要 |
| REQUEST_INSTALL_PACKAGES | 远程更新 APK 安装 | ✅ 必要 |
| WRITE_SECURE_SETTINGS | ColorOS 清除无障碍后的自恢复（仅 ADB/Device Owner 可预授权） | ✅ 必要且受限授予 |
| RECEIVE_BOOT_COMPLETED | 开机恢复防护/更新/心跳 | ✅ 必要 |
| FOREGROUND_SERVICE + DATA_SYNC | 前台服务保活（心跳/拦截支撑） | ✅ 必要 |
| POST_NOTIFICATIONS | 常驻守护通知/引导 | ✅ 必要 |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | 防电池优化杀服务 | ✅ 必要 |

## 管理端（admin）权限

| 权限 | 用途 | 最小化判定 |
|---|---|---|
| INTERNET | CloudBase 通信 | ✅ 必要 |
| FOREGROUND_SERVICE + DATA_SYNC | 异常轮询前台服务 | ✅ 必要 |
| POST_NOTIFICATIONS | 异常通知 | ✅ 必要 |

## 不申请

定位、相机、麦克风、通讯录、短信、通话记录、存储、蓝牙、NFC、后台定位、身体传感器等——全部未申请。

## 隐私政策引用

本表将作为 PRIVACY_POLICY_DRAFT.md 的"权限说明"章节事实来源；对外文案按 kid/admin 分别列出并解释用途。
