# PhoneGuard 公开版文件清单（P0 附档）

> 本清单用于公开发布前的文件级核对：**公开版 = 已跟踪清单 + 可发布未跟踪清单；排除清单绝不进入公开仓库。**
> 状态：已按用户 2026-08-14 决策复核（第二版）；"需用户决策项"已全部决策并执行。内部工作文档（specs/、tasks/、发布计划、AGENTS.md）按决策**移出公开仓库**；audit/ 保留并标注历史基线。

## 1. 可发布：已跟踪文件（Git 历史现有内容）

| 类别 | 文件 | 发布前处理 |
|---|---|---|
| 构建 | settings.gradle.kts / build.gradle.kts / gradle.properties / gradle/libs.versions.toml / gradlew / gradlew.bat / gradle/wrapper/* / .gitattributes | 无需修改 |
| 核心 | core/ 全部源码与测试（含 CloudBaseTestConfig.kt、CloudBaseVerifyTest.kt） | 无需修改（无凭据） |
| 管理端 | admin/ 全部源码与资源 | 无需修改 |
| 被控端 | kid/ 全部源码与资源（含 KidUpdateManager.kt 公开公钥） | 无需修改 |
| 文档 | docs/install.md、docs/acceptance.md | 核对无个人路径（已扫描通过） |
| 工作指令 | AGENTS.md | ✅ 已处理：移出公开仓库（本地 AI 代理工作指令，对公开用户无意义） |
| 忽略 | .gitignore | ✅ 已补充 backup 文档忽略项 + 脱敏工作文件忽略项 |

## 2. 可发布：未跟踪文件（需显式添加）

| 文件 | 发布前处理 |
|---|---|
| docs/provisioning/*.json（2 个） | ✅ 已完成：环境 ID 占位符化（YOUR_ENV_ID） |
| docs/provisioning/*-qr.png（2 个） | ✅ 已完成：QR 与 JSON 同步占位符化（需重新生成对应 QR） |
| docs/updates/kid-stable-update-manifest.json | ✅ 已完成：环境 ID 占位符化 |
| docs/fully-managed-migration.md | ✅ 已发布（扫描无敏感） |
| scripts/New-UpdateManifest.ps1 | ✅ 已发布（无私钥内容）；README 注明需私钥参数 |
| 新增源码/测试（core/protect、core/update、core/stats、kid/protect、kid/update、kid/guard、admin/src/test、kid/src/test、core 新增测试等） | ✅ 已随版本提交，`gradlew test` 全绿 |
| specs/2026-08-13-*.md、tasks/2026-08-13-*.md、tasks/2026-08-14-*.md | ✅ 已处理：按用户决策移出公开仓库（内部工作文档） |
| audit/ 目录（本次审计全部交付物） | ✅ 保留并标注"历史基线报告"；不含密钥原文（已脱敏） |
| DEEPSEEK_OPEN_SOURCE_RELEASE_PLAN.md | ✅ 已处理：按用户决策移出公开仓库（内部发布计划） |

## 3. 绝不进入公开仓库（排除清单）

| 文件 | 原因 |
|---|---|
| keystore.properties | APK 签名密码 |
| phoneguard-release.keystore | APK 签名私钥库 |
| release-keys/update-manifest-private.pem | 更新清单签名私钥 |
| cloudbase.local.properties | CloudBase 真实凭据 |
| docs/backup-before-fully-managed.md | 真实设备序列号 + Windows 绝对路径（已加入 .gitignore） |
| build/ 下全部产物（4 个 APK 等） | 构建产物；发布时重新构建 |
| local.properties | Android SDK 本地路径（已在 .gitignore） |
| mailmap.txt / replacements.txt | 脱敏工作文件，含真实环境 ID 映射（已加入 .gitignore） |

## 4. 需用户决策项（汇总，均已决策）

| # | 决策项 | 决策与状态 |
|---|---|---|
| 1 | Git 作者信息 | ✅ 已决策：改写历史匿名化，已执行（49 个提交全部 phoneguard-dev） |
| 2 | AGENTS.md | ✅ 已决策：移出公开仓库，已执行 |
| 3 | docs/backup-before-fully-managed.md | ✅ 已决策：加入 .gitignore，已执行 |
| 4 | 环境 ID | ✅ 已决策：占位符化，已执行 |
| 5 | audit/ 报告中的作者邮箱全文 | ✅ 已决策：保留但不含真实值（已脱敏为占位符） |
| 6 | specs/、tasks/、发布计划等内部工作文档 | ✅ 已决策：移出公开仓库，已执行 |

## 5. 发布前模板文件（均已生成）

- ✅ keystore.properties.example（占位符，说明签名配置方式）
- ✅ cloudbase.local.properties.example（占位符，说明测试配置方式）
- ✅ .gitignore 追加 backup 文档忽略 + 脱敏工作文件忽略
