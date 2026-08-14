# PhoneGuard（手机守护）

Android 双端家庭手机管控应用：**管理端（:admin）** 配置规则、查看使用报告、接收异常通知；**被控端（:kid）** 执行限时拦截、使用统计、防护上报。支持普通安装与 Device Owner（Fully Managed）部署。

- 技术栈：Android 原生 Kotlin（minSdk 26 / targetSdk 34），Gradle 多模块
- 后端：默认实现基于腾讯云开发 CloudBase（**可替换层**，见 `docs/BACKEND.md` 与 `docs/DEPLOY.md`）
- 许可证：**AGPL-3.0-or-later**（见 `LICENSE`）

## 功能

| 端 | 功能 |
|---|---|
| 管理端 | 注册/登录、邀请码绑定、规则配置（按应用/按类别/每日总额/时段/周末/假期）、实时状态、使用报告、异常通知（离线/权限被关/时间修改/新装应用/防卸载解除） |
| 被控端 | 邀请码绑定、无障碍实时拦截 + 全屏遮罩、到时限硬退出回桌面、UsageStats 统计上报、前台服务保活、设备管理器防卸载、Device Owner 部署、远程签名更新 |

## 目录结构

```
core/    共享逻辑：数据模型、规则引擎、后端封装（可替换层）、更新验证
admin/   管理端
kid/     被控端
docs/    部署与自建指南
specs/   规格文档
```

## 构建

需要：JDK 17+（本仓库开发环境为 JDK 25）、Android SDK（compileSdk 34）、Gradle 9.1.0（wrapper 自带）。

```bash
# 调试包
./gradlew :admin:assembleDebug :kid:assembleDebug

# 正式包（release 签名需配置 keystore.properties，见 docs/DEPLOY.md）
./gradlew :admin:assembleRelease :kid:assembleRelease
```

产物：`admin/build/outputs/apk/release/admin-release.apk`、`kid/build/outputs/apk/release/kid-release.apk`

## 部署（自建后端）

本仓库**不内置任何公共后端地址**（环境 ID 为占位符 `YOUR_ENV_ID`）。使用前请按 `docs/DEPLOY.md` 自建后端（任何你信任的云服务商或自建方案均可，后端为可替换层，默认实现基于腾讯云 CloudBase）：

1. 开通一个数据库后端，创建集合：`bindings` / `rules` / `events` / `usage` / `apps`；
2. 按 `docs/DEPLOY.md` 配置安全规则（字段级授权）；
3. 将 `admin/build.gradle.kts` 与 `kid/build.gradle.kts` 中的 `CLOUDBASE_ENV_ID` 替换为你自己的环境 ID；
4. 构建并安装双端，管理端注册账号 → 生成邀请码 → 被控端绑定。

> 说明：本仓库不推荐任何特定云服务品牌；默认实现的接入方式仅供参考，`core/backend/` 是可替换层。

## 远程更新（被控端，可选）

被控端支持签名清单 + SHA-256 校验的远程更新。发布新版本需要离线 ECDSA 私钥签名（私钥**绝不入仓库**），流程见 `docs/DEPLOY.md` 与 `scripts/New-UpdateManifest.ps1`。

## 安全与隐私

- 远程更新：ECDSA 签名先于一切校验、防回滚、防重放（`core/update/`）
- 数据库：字段级安全规则，按家庭隔离（见 `docs/DEPLOY.md`）
- 权限：仅申请管控功能必需权限；不采集定位/通讯录/短信/相册/硬件标识，无广告与统计 SDK
- 隐私政策草案见 `PRIVACY_POLICY_DRAFT.md`

## 许可证与第三方

- 本项目：**AGPL-3.0-or-later**（`LICENSE`、`NOTICE`）
- 第三方依赖与参考来源声明：`THIRD_PARTY_NOTICES.md`
- 安全说明：`SECURITY.md`
