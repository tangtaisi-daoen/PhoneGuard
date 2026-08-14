# PhoneGuard 公开版文件清单（P0 附档）

> 本清单用于公开发布前的文件级核对：**公开版 = 已跟踪清单 + 可发布未跟踪清单；排除清单绝不进入公开仓库。**
> 状态：草稿（2026-08-14），阶段八发布前复核。

## 1. 可发布：已跟踪文件（Git 历史现有内容）

| 类别 | 文件 | 发布前处理 |
|---|---|---|
| 构建 | settings.gradle.kts / build.gradle.kts / gradle.properties / gradle/libs.versions.toml / gradlew / gradlew.bat / gradle/wrapper/* / .gitattributes | 无需修改 |
| 核心 | core/ 全部源码与测试（含 CloudBaseTestConfig.kt、CloudBaseVerifyTest.kt） | 无需修改（无凭据） |
| 管理端 | admin/ 全部源码与资源 | 无需修改 |
| 被控端 | kid/ 全部源码与资源（含 KidUpdateManager.kt 公开公钥） | 无需修改 |
| 文档 | docs/install.md、docs/acceptance.md、specs/spec.md、tasks/plan.md、tasks/todo.md | 核对无个人路径（已扫描通过） |
| 工作指令 | AGENTS.md | **必须处理**：移除第 74 行 `C:\Users\<USERNAME>\...` 绝对路径，或整文件不纳入公开版 |
| 忽略 | .gitignore | 补充 backup 文档忽略项 |

## 2. 可发布：未跟踪文件（需显式添加）

| 文件 | 发布前处理 |
|---|---|
| docs/provisioning/*.json（2 个） | 环境 ID 占位符化（待用户决策） |
| docs/provisioning/*-qr.png（2 个） | 同上（QR 与 JSON 同源） |
| docs/updates/kid-stable-update-manifest.json | 环境 ID 占位符化（待用户决策） |
| docs/fully-managed-migration.md | 已扫描无敏感；通读校对后发布 |
| scripts/New-UpdateManifest.ps1 | 可发布（无私钥内容）；README 注明需私钥参数 |
| 新增源码/测试（core/protect、core/update、core/stats、kid/protect、kid/update、kid/guard、admin/src/test、kid/src/test、core 新增测试等，见 git status） | 阶段六回归验证通过后随版本提交 |
| specs/2026-08-13-*.md、tasks/2026-08-13-*.md、tasks/2026-08-14-*.md | 通读后发布（已扫描无敏感） |
| audit/ 目录（本次审计全部交付物） | 发布前复核：确认不含密钥原文（当前已脱敏）；含 Git 作者邮箱全文，发布前决定保留或脱敏 |
| DEEPSEEK_OPEN_SOURCE_RELEASE_PLAN.md | 发布前复核（含本地路径占位符说明，无真实路径） |

## 3. 绝不进入公开仓库（排除清单）

| 文件 | 原因 |
|---|---|
| keystore.properties | APK 签名密码 |
| phoneguard-release.keystore | APK 签名私钥库 |
| release-keys/update-manifest-private.pem | 更新清单签名私钥 |
| cloudbase.local.properties | CloudBase 真实凭据 |
| docs/backup-before-fully-managed.md | 真实设备序列号 + Windows 绝对路径（**加入 .gitignore**） |
| build/ 下全部产物（4 个 APK 等） | 构建产物；发布时重新构建 |
| local.properties | Android SDK 本地路径（已在 .gitignore） |

## 4. 需用户决策项（汇总）

1. Git 作者信息：改写历史（匿名化）或接受公开；
2. AGENTS.md：移除路径行或整文件排除；
3. docs/backup-before-fully-managed.md：加入 .gitignore 或移出仓库；
4. 环境 ID：占位符化或保留（免费体验版配额风险）；
5. audit/ 报告中的作者邮箱全文：保留或脱敏。

## 5. 发布前模板文件（阶段七生成）

- keystore.properties.example（占位符，说明签名配置方式）
- cloudbase.local.properties.example（占位符，说明测试配置方式）
- .gitignore 追加 backup 文档忽略
