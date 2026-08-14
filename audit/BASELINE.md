# PhoneGuard 审计基线（阶段一）

> ⚠️ **历史基线报告**：记录 2026-08-14 审计时点的状态；后续变更以当前代码与文档为准。
> 冻结时间：2026-08-14 14:40 (CST)
> 状态：✅ 门禁通过——当前版本可在现有环境干净重建（release 双端构建成功）
> 说明：本文件不包含个人路径；环境位置一律用版本号与占位符表述。

## 1. 仓库身份

| 项 | 值 |
|---|---|
| 分支 | master（唯一分支，无 tag） |
| HEAD | `253edf4` feat(admin): 规则页卡片化重构 (借鉴 KidSafe...) |
| HEAD 时间 | 2026-08-12 23:50:56 +0800 |
| 提交总数 | 29 |
| 历史改写 | 已批准匿名化（阶段七执行，filter-repo） |

## 2. 工作树状态（基线时刻）

| 项 | 值 |
|---|---|
| 已跟踪修改文件 | 44（+2631 / -298 行） |
| 未跟踪文件 | 89 |
| 未跟踪目录 | admin/src/test、kid/src/test、kid/src/main/res/drawable-nodpi、values-night、core/protect、core/update、core/stats、kid/protect、kid/update、kid/guard、docs/provisioning、docs/updates、release-keys（已 gitignore）、scripts、audit（本次审计新增） |
| 敏感文件 | 4 个本地文件均 gitignored（见 PRIVACY_EXPOSURE_AUDIT.md） |

> 基线工作点决策：见第 7 节（待用户确认是否创建审计分支提交）。

## 3. 构建环境（可复现条件）

| 项 | 值 |
|---|---|
| JDK | OpenJDK 25.0.2（Android Studio JBR，经 JAVA_HOME 注入；PATH 中无 java） |
| Gradle | 9.1.0（wrapper，all 发行版，已缓存） |
| AGP | 8.13.1 |
| Kotlin | 2.2.20 |
| compileSdk / targetSdk / minSdk | 34 / 34 / 26 |
| Java 目标 | VERSION_17 (jvmTarget 17) |
| Android SDK | build-tools 36.0.0（ANDROID_HOME 环境变量；local.properties 不存在） |
| Gradle 缓存 | 用户目录 GRADLE_USER_HOME（已缓存，构建可离线复用） |
| 构建配置 | `android.overridePathCheck=true`（gradle.properties，中文路径支持） |
| 构建命令 | `gradlew :admin:assembleRelease :kid:assembleRelease`（JAVA_HOME 需显式设置） |

## 4. 模块版本

| 模块 | applicationId | versionCode | versionName | 备注 |
|---|---|---|---|---|
| :admin | com.familyguard.admin | 9 | 0.1.8 | CloudBase envId 经 BuildConfig 注入 |
| :kid | com.familyguard.kid | 15 | 0.1.14 | debug 构建加 `.debug` 后缀 |
| :core | （库模块） | — | — | 无 Android 依赖，JVM 可测 |

## 5. 后端与外部服务

| 项 | 值 |
|---|---|
| 后端 | 腾讯云开发 CloudBase 免费体验版 |
| 环境 ID | `YOUR_ENV_ID`（已批准阶段七占位符化） |
| 接入方式 | REST HTTP API（core/backend 可替换层），token 自动刷新 |
| 更新分发 | CloudBase 静态托管（`*.tcloudbaseapp.com/phoneguard/...`） |
| 认证 | 邮箱+密码注册登录 / 匿名登录（被控端） |

## 6. 基线构建产物（2026-08-14 重建验证）

| 产物 | 大小 (bytes) | SHA-256 |
|---|---|---|
| admin-release.apk | 6,902,258 | `9d71702bab02a4cd5c363638cc859af67e1fa36af9fa434f9ddd8f4beca665d8` |
| kid-release.apk | 7,147,581 | `6d4c52c29071741b3487ac4eaafef8e1e0078e3b1347a09a30bdaa3f0f61b5db` |

签名证书（apksigner 验证）：CN=FamilyGuard, OU=Family, O=Family, L=Home, ST=Home, C=CN
- 证书 SHA-256：`41091d4667b59d051895abdf1e01c32e83cb1aea0e8ca2b253c9020d6e29e937`
- 一致性：kid-release.apk 哈希与 `docs/updates/kid-stable-update-manifest.json` 的 apkSha256 完全一致；证书指纹与 provisioning JSON、签名脚本记录一致 → **基线可复现性验证通过**

## 7. 审计工作点决策（待用户确认）

**选项 A（推荐）**：新建分支 `audit-baseline-2026-08-14`，将当前工作树全部内容（44 修改 + 89 未跟踪，敏感文件已被 gitignore 排除）提交为基线提交。收益：基线可一键重建（`git checkout audit-baseline`），阶段二~六的所有改动可在该基线上做 diff 审计；master 分支与现有工作不受影响。
**选项 B**：不创建分支/提交，仅以 HEAD `253edf4` + 本文件记录的变更清单为基线。收益：不动 Git 历史；代价：审计 diff 需手工比对，基线不可整体重建（新功能文件未被跟踪）。

## 8. 门禁结果

- [x] 记录分支/状态/最近提交/未跟踪文件/构建环境
- [x] 固定可构建版本（双端 release 构建成功，1m37s）
- [x] APK 与 SHA-256 已生成并交叉验证
- [x] CloudBase 环境已记录
- [ ] 审计分支或等价安全工作点（待用户确认第 7 节）
- [x] 已知问题汇总（见 KNOWN_ISSUES.md）
