# PhoneGuard 安全审计报告（阶段四）

> ⚠️ **历史基线报告**：记录 2026-08-14 审计时点的状态；后续变更以当前代码与文档为准。
> 审计时间：2026-08-14
> 审计范围：认证/绑定/邀请码、CloudBase 数据库 API、双端鉴权、远程更新链（签名/版本/回滚）、Device Owner/无障碍/悬浮窗/用量权限边界、启动恢复/离线、防卸载/防篡改、release 清洁度
> 结论先行：**无 Critical 风险**；2 项 High（均为"需控制台/流程配合"性质，非代码本身缺陷）、6 项 Medium、3 项 Low。

## 1. 风险分级总表

| 编号 | 级别 | 风险 | 位置 | 处理 |
|---|---|---|---|---|
| **H1** | **Critical→（发布形态变更后降级）** | **后端数据权限全开放（已实测）：bindings/rules/events/usage/apps 五集合安全规则均为 `{"read":true,"write":true}`。2026-08-14 用户决策改为纯自托管发布（APK 不内置真实环境 ID），"公开即暴露"路径切断 → 降级为自有环境加固建议（代码侧修复已完成，线上规则应用待用户控制台操作，指引见 BACKEND_RULES_AUDIT.md §3a/3b）** | CloudBase 控制台安全规则 | 代码侧已完成并提交；线上规则按 §3b 指引应用 |
| H2 | High | 绑定无调用者身份约束：6 位邀请码（32 字符集）即唯一凭证；泄露/被猜中可抢先绑定；kidDeviceId 自报可冒名上报；刷新邀请码不使旧码失效（旧 PENDING 码长期有效） | CloudBaseBindings.generateInviteCode / bindWithCode | 建议：绑定校验管理员身份字段、刷新码时旧码置失效、邀请码有效期；评估服务端限流 |
| M1 | Medium | 会话 token 明文存 SharedPreferences（MODE_PRIVATE） | SessionStore.kt（注释已自标） | allowBackup=false 已缓解备份提取；root 设备仍可读；**发布前升级 EncryptedSharedPreferences 或记录为已知限制**；✅ **2026-08-14 已完成**：SessionStore 迁移 EncryptedSharedPreferences（AES256-GCM，密钥由 Keystore 持有），含旧明文数据自动迁移 |
| M2 | Medium | 匿名身份 x-device-id 自报，可伪造（知晓 deviceId 可冒名上报使用数据/事件） | CloudBaseAuth.signInAnonymously | 风险依赖 CloudBase 对 x-device-id 的处理；缓解：deviceId 16 位随机不易猜 |
| M3 | Medium | 无 TLS 证书 pinning（信任系统 CA） | CloudBaseClient (OkHttp) | 默认 CA 链可接受；加固可选（配合控制台域名固定） |
| M4 | Medium | release 包残留 Log.d 调试日志（内容仅包名/判定文本，无敏感数据） | GuardAccessibilityService 等 | 阶段七清理或 ProGuard 移除 |
| M5 | Medium | getMyInviteCode 取"最近创建"但查询无显式排序（依赖服务端默认行为）；多 PENDING 码语义不明确 | CloudBaseBindings.getMyInviteCode | 显式 sort createdAt desc |
| M6 | Medium | 登录/验证码无客户端速率限制（依赖 CloudBase 服务端策略，未知） | CloudBaseAuth | 核对服务端策略；必要时客户端加防爆破间隔 |
| L1 | Low | admin LoginActivity exported=true（可被外部拉起，无敏感操作） | admin AndroidManifest | 可改 exported=false（非必须） |
| L2 | Low | lastError 含响应前 120 字符（仅 UI 诊断展示） | CloudBaseClient | 可接受 |
| L3 | Low | 防卸载/防退出/防篡改真实能力边界（见 §5） | 各保护组件 | 文档化限制 |

## 2. 已确认的安全设计（通过项）

### 2.1 远程更新链（最强环节）✅
- 更新清单 ECDSA 签名（SHA256withECDSA），公钥内嵌客户端（release-keys/update-manifest-public.pem 与 KidUpdateManager 常量一致）
- **签名验证先于一切元数据校验**（UpdateManifestVerifier 注释明确："tampering is never treated as trusted metadata"）
- 防回滚：versionCode 必须大于已安装版本；防重放：issuedAt/expiresAt 时间窗（±5 分钟时钟偏移容忍）；HTTPS URL 强制；packageName 匹配；APK 下载后 SHA-256 校验 + 安装会话 FileProvider
- 私钥仅本地（gitignored），公钥/证书指纹为公开验证值

### 2.2 Device Owner / 高权限边界 ✅
- KidDevicePolicyController：每次特权调用前重新验证 isDeviceOwnerApp；仅应用最小安全基线（防卸载 + DISALLOW_INSTALL_UNKNOWN_SOURCES + 支持消息），不含 kiosk/恢复出厂等破坏性策略
- WRITE_SECURE_SETTINGS 注释明确"仅可通过 ADB/设备所有者预授权"（普通安装无法获得）
- 设备管理器停用 → 立即上报异常（onDisabled → ADMIN_DISABLED）
- exported 组件均以 BIND_DEVICE_ADMIN 权限保护；内部组件全部 exported=false

### 2.3 拦截与防护 ✅
- 无障碍事件 + UsageStats 轮询双通道；白名单防误伤；3 秒冷却防循环；设置页危险页面分类拦截（ProtectionPageClassifier + ProtectionGuardPolicy 有测试覆盖）
- BlockOverlay + BlockActivity 双保险（游戏无法压制 Activity）

### 2.4 恢复与离线 ✅
- BOOT_COMPLETED / MY_PACKAGE_REPLACED 恢复心跳与更新状态机；指数退避重试（15min→6h）；异常上报去抖（lastAnomalyReportedAt）

### 2.5 网络与传输 ✅
- 全部 HTTPS（CloudBase REST）；Bearer token + 401 自动刷新轮换；超时配置合理

### 2.6 数据备份面 ✅
- admin/kid 均 allowBackup=false（防 adb backup 提取 token 与绑定数据）

## 3. 权限最小化检查

| 权限（kid） | 用途 | 判定 |
|---|---|---|
| PACKAGE_USAGE_STATS | 使用统计+前台检测（核心能力） | ✅ 必要 |
| QUERY_ALL_PACKAGES | 展示/管控完整应用列表 | ✅ 必要（有注释说明） |
| SYSTEM_ALERT_WINDOW | 全屏拦截浮层 | ✅ 必要 |
| REQUEST_INSTALL_PACKAGES | 远程更新安装 | ✅ 必要 |
| WRITE_SECURE_SETTINGS | 无障碍自动恢复（ColorOS） | ✅ 必要+受限授予 |
| RECEIVE_BOOT_COMPLETED / FGS / 通知 / 电池优化 / INTERNET | 保活与通信 | ✅ 必要 |
| admin：INTERNET / FGS / 通知 | 轮询与通知 | ✅ 最小 |

## 4. 修复前后证据

- 本次为审计首轮（阶段四），修复项在阶段六/七执行；"修复前证据"= 本报告代码引用行号；修复后重跑本报告对应条目并记录提交。
- 代码内已存在的自我标注：SessionStore.kt 第 9 行注释（token 加密升级计划）、UpdateManifestVerifier 防篡改注释。

## 5. 防卸载/防退出/防篡改的真实能力与限制（文档化）

| 能力 | 实现 | 限制 |
|---|---|---|
| 防卸载 | DeviceAdmin 激活后系统拦截直接卸载；停用即上报异常 | 用户可在"设置→安全→设备管理器"停用（停用瞬间已被上报）；root 可绕过；Fully Managed 模式额外 setUninstallBlocked |
| 防退出 | 拦截页返回键/菜单/最近任务拦截 + 拦截 Activity 抢占前台 | Home 键由系统接管（无法拦截），返回桌面后再次进入受限 app 会重新拦截（设计如此） |
| 防篡改 | 更新清单 ECDSA 签名 + APK SHA-256 + 版本单调 + 时间窗 | 签名私钥安全即不可伪造；侧载安装由系统签名校验兜底 |
| 防权限关闭 | 设置页危险页面分类拦截（无障碍读屏） | 无障碍被关闭后失去读屏能力——依赖 WRITE_SECURE_SETTINGS 自恢复 + 异常上报 |

## 6. 门禁判定

- [x] 风险分级表完成（**1 Critical（H1 已实测实锤）** / 1 High / 6 Medium / 3 Low）
- [x] H1 实测证据与修复方案齐备（BACKEND_RULES_AUDIT.md §3a）——**执行线上规则变更前需用户确认**
- [x] H2 及 Medium 项处置路径明确（阶段六修复批次，用户已确认"全部修复"）
- [x] 更新链/高权限/恢复/离线/防篡改均有代码级证据
- [ ] 修复执行与复验（阶段六/七）
