# PhoneGuard 开源前审计、整理与上线总任务

> 本文件是给 DeepSeek 执行的完整任务提示词。请把它作为一个连续的总项目执行，不要拆成只修一个 Bug 的一次性任务。

## 1. 项目背景

项目目录：`<REPO_ROOT>`（执行时替换为本地仓库根目录；公开仓库文档不得写入 Windows 用户名、绝对路径或个人目录）

这是一个 Android 原生 Kotlin 多模块项目：

- `:core`：共享规则、数据和后端逻辑
- `:admin`：管理端
- `:kid`：被控端
- 后端：腾讯云 CloudBase
- 目标设备：OPPO A92s，Android 10/11，无 GMS
- 被控端已经迁移到 Fully Managed / Device Owner 使用场景

项目目前功能基本完成，下一目标不是继续无边界增加功能，而是完成“开源前审计与发布准备”。

用户希望个人用户可以自由下载使用，同时尽量避免商业公司拿源码闭源后直接收费。这个目标必须通过许可证分析来实现，不能擅自把“禁止商业使用”称为严格意义上的 Open Source。OSI 的定义明确要求开源许可证不得限制特定领域的使用，包括商业使用：

- https://opensource.org/osd
- https://opensource.org/faq

请先给出许可证选项和实际后果，再等待用户确认最终许可证策略。

## 2. 总目标

最终交付一个可以安全、合规、可复现地发布到 GitHub 的 PhoneGuard 版本，至少满足：

1. 所有外部代码、设计、依赖、图片、字体和文案来源可追溯。
2. 明确区分“直接复制”“修改使用”“参考设计”“Android 官方 API”和“普通第三方依赖”。
3. GPL、Apache、MIT 等许可证的义务得到逐项处理。
4. 不存在密钥、个人数据、测试账号、CloudBase 凭据或调试后门泄露。
5. 管理端、被控端、后端、远程更新和 Device Owner 流程通过回归测试。
6. 生成正式 release APK、校验值、变更日志和安装说明。
7. 完成 Git 整理、原子提交和版本 tag。
8. 在用户最终隐私审计和公开确认前，不得把仓库公开。

## 3. 已知参考项目

以下是当前仓库中已经明确记录或在代码注释中出现的参考来源。必须逐一核对，不能把“参考思想”直接写成“没有版权风险”。

### 3.1 KidSafe

- 项目：https://github.com/xMansour/KidSafe
- 主要参考：管理端应用列表、卡片式 UI
- 本地证据：`admin/src/main/java/com/familyguard/admin/RuleAdapter.kt`
- 许可证：以该项目当前及所引用 commit 的许可证文件为准，初步为 MIT

### 3.2 Child Screen Time / cst

- 项目：https://github.com/childscreentime/cst
- 主要参考：全屏拦截、前台拉回、WorkManager 保活、前台服务分层
- 本地证据：`kid/src/main/java/com/familyguard/kid/KidApp.kt`、`BlockActivity.kt`、`BlockOverlay.kt`、`GuardAccessibilityService.kt`、`GuardWorker.kt`
- 初步许可证：MIT

### 3.3 TestDPC

- 项目：https://github.com/googlesamples/android-testdpc
- 主要参考：Device Owner、`DevicePolicyManager`、`PackageInstaller.Session` 和安装状态处理
- 调研 commit：`d42d7f196d2db3d22ba4fca1e74faa5bc9b58d4e`
- 重点文件：`PackageInstallationUtils.java`、`DevicePolicyManagerGatewayImpl.java`
- 初步许可证：Apache-2.0

### 3.4 Headwind MDM

- 项目：https://github.com/h-mdm/hmdm-android
- 主要参考：远程配置、APK 更新队列、安装状态、启动恢复、远程日志
- 调研 commit：`6bf2ea29159a5aeb2a9d2bfd5e4128649d69f45c`
- 重点文件：`ConfigUpdater.java`、`InstallUtils.java`、`PushNotificationWorker.java`、`BootReceiver.java`、`RemoteLogger.java`
- 初步许可证：Apache-2.0

### 3.5 Curbox

- 项目：https://github.com/curbox-app/curbox-android
- 主要参考：规则日历、跨午夜时间段、限制比较器、反卸载思路
- 调研 commit：`f86e6bfffd05b9b08edc33aebbb840fae23d4208`
- 重点文件：`AppBlocker.kt`、`TimeGroupWindow.kt`、`AppBlockerService.kt`、`RestrictionComparator.kt`、`AntiUninstallBlocker.kt`
- 初步许可证：GPL-3.0-or-later
- 这是高风险审计对象，必须做逐文件相似度和派生作品判断。

### 3.6 APKUpdater

- 项目：https://github.com/rumboalla/apkupdater
- 主要参考：安装会话、安装进度、用户确认、会话清理、唯一后台任务
- 调研 commit：`69b6fcdf52a7735ae17101efe1a0cd26222fb276`
- 重点文件：`SessionInstaller.kt`、`UpdatesWorker.kt`
- 初步许可证：GPL-3.0
- 同样需要重点检查是否存在代码级复制或不可兼容的派生实现。

Android 官方文档（`PackageInstaller`、`DevicePolicyManager`、`AccessibilityService` 等）属于平台 API 依据，不应列为第三方代码来源。

## 4. 执行规则

1. 先检查仓库、`AGENTS.md`、规格文档、Git 状态和现有测试，再修改代码。
2. 不得使用 `git reset --hard`、删除用户文件或覆盖现有工作，除非用户明确批准。
3. 不得凭猜测新增开源来源；所有来源必须有 URL、commit、文件路径或本地证据。
4. 不得把“只借鉴思想”当作最终法律结论。
5. 不得把个人隐私数据、设备备份、APK 签名私钥、CloudBase 密钥提交进 Git。
6. 每个修复都要有测试或可复现的真机验证。
7. 每个阶段都要产出报告；阶段门禁未通过时不得进入下一阶段。
8. 在许可证策略和公开 GitHub 之前必须请求用户确认。

## 5. 个人隐私与机密泄露专项审计（P0）

这一阶段必须在任何公开仓库、公开 Release、公开截图或公开日志之前完成。审计对象不仅是“代码是否泄露”，还包括用户本人、家人、设备、账号和运行环境是否被暴露。

### 5.1 仓库和 Git 历史

扫描当前工作树、暂存区、所有分支、tag、历史提交、PR、Issue、Actions 日志和 Release 附件，重点寻找：

- 姓名、手机号、邮箱、账号、家庭成员信息
- OPPO 设备序列号、IMEI、Android ID、ADB serial、设备名
- 邀请码、绑定码、二维码、配对凭据、登录凭据
- CloudBase 环境密钥、云函数密钥、访问 token、测试账号
- APK 签名私钥、更新 manifest 私钥、keystore 密码
- `logcat`、崩溃堆栈、诊断报告、屏幕截图和备份记录
- Windows 用户名、绝对路径、局域网 IP、服务器地址和个人文件路径

不能只删除当前文件：如果内容曾经进入 Git 历史、构建产物、Issue 或日志，必须记录暴露范围并进行凭据轮换或撤销。

### 5.2 当前仓库的已知高风险线索

以下文件目前属于本地忽略/未跟踪的敏感文件，不能发布，也不能把真实内容写进报告或聊天：

- `keystore.properties`
- `phoneguard-release.keystore`
- `release-keys/update-manifest-private.pem`

还要重点检查测试配置、迁移/备份文档、`build/` 产物和生成资源中是否含有真实设备或账号数据。上述只是审计线索，不代表已经确认泄露。

当前未跟踪的 `core/src/test/java/com/familyguard/core/backend/CloudBaseTestConfig.kt` 存在疑似长字符串/凭据候选，必须在提交前判断它究竟是示例环境标识、测试值还是有效凭据；报告中只能给出脱敏后的指纹，不能输出原文。

### 5.3 CloudBase 和外部服务

- 检查生产数据库、测试数据库、日志、云存储和更新托管目录。
- 区分真实家庭数据、测试数据和示例数据。
- 确认公开 GitHub 后，任何人是否能通过配置、接口或默认规则读取使用报告、设备信息或绑定信息。
- 对已暴露的 token、邀请码、数据库密钥和签名凭据执行撤销/轮换。
- 删除或脱敏数据前先列出对象和影响，涉及真实用户数据时必须等待用户确认。

### 5.4 交付物和门禁

交付：

- `audit/PRIVACY_EXPOSURE_AUDIT.md`
- `audit/SECRET_SCAN_REPORT.md`
- `audit/DATA_EXPOSURE_MATRIX.csv`
- 已暴露凭据的轮换/撤销记录
- 公开版文件清单（确认不含个人信息和机密）

门禁：只要存在未分类的个人信息、真实设备标识、有效凭据、私钥、真实使用报告或无法确认的历史泄露，就不得进入公开发布阶段。

## 6. 阶段一：冻结审计基线

任务：

- 记录当前分支、Git 状态、最近提交、未跟踪文件和构建环境。
- 固定当前可构建版本、Gradle/JDK/Android SDK 版本和 CloudBase 环境。
- 建立审计分支或等价的安全工作点。
- 汇总当前已知功能和问题，不在此阶段顺便改业务逻辑。

交付物：

- `audit/BASELINE.md`
- `audit/KNOWN_ISSUES.md`
- 当前版本 APK 和 SHA-256

门禁：能够在干净工作树中重新构建当前版本。

## 7. 阶段二：来源、依赖和版权审计

任务：

- 扫描全部 Kotlin/Java/XML/JSON/Gradle/资源文件。
- 扫描全部 Git 历史、注释、URL、版权头和许可证文本。
- 列出直接依赖、传递依赖、图片、图标、字体和文案来源。
- 将每个外部来源分类为：复制、修改使用、设计参考、平台 API 或普通依赖。
- 对 Curbox、APKUpdater 和其他 GPL 来源做文本、结构、算法和文件级相似度检查。
- 检查是否存在未记录的 GitHub 项目引用。

交付物：

- `audit/SOURCE_PROVENANCE.md`
- `audit/THIRD_PARTY_INVENTORY.csv`
- `audit/CODE_SIMILARITY_REPORT.md`
- `THIRD_PARTY_NOTICES.md`

门禁：每个外部来源都有出处、许可证、使用范围和处理结论；不允许出现“来源未知”。

## 8. 阶段三：许可证和发布策略

任务：

- 为每个依赖确认 SPDX 标识、许可证文件、版权声明、NOTICE 和专利条款。
- 判断当前代码是否有资格使用用户选择的主许可证。
- 对 GPL 代码给出三种处理建议：保留并遵守、隔离/替换、完全重写。
- 准备严格开源、强 copyleft、source-available、双许可证等方案的优缺点。
- 明确“个人免费”和“禁止商业闭源收费”之间的实际法律差异。

交付物：

- `audit/LICENSE_OPTIONS.md`
- `audit/LICENSE_COMPATIBILITY_MATRIX.csv`
- `LICENSE` 草案
- `NOTICE` 草案
- 商业使用边界说明

门禁：必须先把选项和风险交给用户确认，不能擅自选择最终许可证。

## 9. 阶段四：安全、后端和系统权限审计

重点检查：

- 绑定、邀请码、设备身份和重放攻击
- CloudBase 数据库、云函数和存储权限
- 管理端与被控端 API 鉴权
- APK 下载、签名校验、版本校验、回滚和远程更新
- Device Owner、无障碍、悬浮窗、Usage Stats 等高权限边界
- 启动恢复、进程被杀、网络断开和离线状态
- 防卸载、防退出、防篡改实现的真实能力和限制
- release 包中的调试开关、测试地址、日志和密钥

交付物：

- `audit/SECURITY_AUDIT.md`
- `audit/BACKEND_RULES_AUDIT.md`
- 风险分级表：Critical / High / Medium / Low
- 修复前后证据

门禁：Critical 和 High 风险必须修复、降级并经用户确认，不能无记录地带入公开版。

## 10. 阶段五：隐私技术审计

任务：

- 绘制管理端、被控端、CloudBase 之间的数据流。
- 列出应用使用时间、包名、应用名、设备标识、绑定信息、日志和异常信息。
- 明确采集目的、保存时间、访问者、删除方式和解绑后的处理。
- 检查权限是否最小化，日志是否脱敏，网络传输是否安全。
- 生成隐私政策初稿和权限说明。

交付物：

- `audit/DATA_FLOW.md`
- `audit/PRIVACY_DATA_INVENTORY.md`
- `PRIVACY_POLICY_DRAFT.md`
- 权限用途表

门禁：技术审计完成后，必须把最终隐私审计留给用户确认；未经用户确认不得公开。

## 11. 阶段六：功能修复和回归测试

必须覆盖：

- 今日/昨日使用报告边界、时区和跨午夜逻辑
- 异常应用过滤和应用名称显示
- 添加应用限制、时间滑块和规则保存
- 周中、周末、假期和跨午夜规则
- 使用情况访问、自启动、悬浮窗、无障碍页面稳定性
- 无障碍被杀后的恢复和状态上报
- 危险设置页面退出、回到桌面、重新进入绑定首页
- 被控端桌面翻页、长按、删除和防护行为
- Device Owner 状态、重启、升级和解绑
- 远程更新、离线重试、失败回滚和安装状态

必须执行：

- `:core` 单元测试
- `:admin` 测试和构建
- `:kid` 测试、构建和 lint
- 真机测试，至少覆盖 OPPO A92s Android 10/11
- 干净安装、覆盖升级、断网、重启、进程被杀和回滚测试

交付物：

- `audit/QA_MATRIX.md`
- `audit/REGRESSION_REPORT.md`
- 每个问题的复现步骤、修复提交和验证证据

门禁：所有阻断级问题关闭，测试结果可复现，不能只报告“看起来正常”。

## 12. 阶段七：发布构建和 Git 整理

任务：

- 清理调试配置、测试账号、密钥和本地路径。
- 固定版本号、签名配置、ProGuard/R8 和构建参数。
- 生成 release APK、SHA-256 和版本变更日志。
- 检查 Git 历史是否曾经提交过密钥；如需改写历史，必须先请求用户批准。
- 按“审计修复、许可证文档、测试、发布配置”拆分原子提交。
- 创建版本 tag。

交付物：

- release APK
- SHA-256 校验文件
- `CHANGELOG.md`
- 干净 Git 提交和 tag
- `audit/RELEASE_BUILD_REPORT.md`

门禁：仓库无敏感信息，release 构建可复现，用户确认后才能推送公开仓库。

## 13. 阶段八：GitHub 预发布和公开上线

先将仓库保持私有，完成：

- README、安装说明和系统限制说明
- LICENSE、NOTICE、第三方来源清单
- SECURITY.md、贡献说明和问题模板
- Device Owner 部署文档
- 隐私政策和权限说明
- 发布 APK、校验值和变更日志
- GitHub Secret Scanning、依赖漏洞扫描和仓库内容复核

然后等待用户做最终隐私审计和公开确认。用户明确确认后，才可以：

1. 将仓库改为公开。
2. 发布正式 GitHub Release。
3. 发布公开 APK 下载地址。
4. 启用后续远程更新渠道。

## 14. 最终交付报告

DeepSeek 最后必须交付一份总报告，至少包含：

- 来源和许可证结论
- 仍存在的第三方代码风险
- 安全审计结论
- 隐私数据流结论
- 测试和真机验证结果
- release 构建信息
- Git 提交和 tag
- 尚未解决的问题
- 需要用户最终确认的事项

没有总报告、没有测试证据、没有许可证决定或没有用户公开确认时，任务不得宣称“已完成开源上线”。
