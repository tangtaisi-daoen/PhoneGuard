# Third-Party Notices — PhoneGuard

PhoneGuard 使用以下第三方组件与参考来源。完整清单见 `audit/THIRD_PARTY_INVENTORY.csv`。

## 1. 运行时依赖（全部 Apache-2.0）

AndroidX 组件（core-ktx、appcompat、material、constraintlayout、lifecycle、work、recyclerview、fragment 等及其传递依赖，Copyright The Android Open Source Project）、Kotlin 标准库与协程（Copyright JetBrains s.r.o.）、OkHttp / Okio（Copyright Square, Inc.）、Gson（Copyright Google LLC）。以上均按 Apache License 2.0 使用，许可证全文见 https://www.apache.org/licenses/LICENSE-2.0 。

## 2. 测试依赖

JUnit 4.13.2（Eclipse Public License 2.0），仅用于测试，不随 APK 分发。

## 3. 设计参考来源（未复制代码，仅参考设计思路）

- **KidSafe**（https://github.com/xMansour/KidSafe，MIT License，Copyright (c) 2019 Mahmoud Mansour）：管理端应用列表卡片式 UI 形式参考。MIT 许可文本见 https://opensource.org/licenses/MIT 。
- **Child Screen Time / cst**（https://github.com/childscreentime/cst，仓库无许可证文件）：全屏拦截遮罩（SYSTEM_ALERT_WINDOW）、前台应用拉回（bringAppToForeground）、WorkManager 保活与前台服务分层思路参考。本地实现为独立编写（Kotlin），无代码复制；思路本身不受版权保护。
- **TestDPC**（https://github.com/googlesamples/android-testdpc，Apache-2.0，Copyright Google Inc.）：Device Owner / DevicePolicyManager / PackageInstaller 平台 API 用法参考。
- **Headwind MDM**（https://github.com/h-mdm/hmdm-android，Apache-2.0，Copyright 2018 Vsevolod Mayorov）：远程配置、更新队列、启动恢复、远程日志思路参考。
- **Curbox**（https://github.com/curbox-app/curbox-android，GPL-3.0-or-later）：规则日历、跨午夜时间段、限制比较器、反卸载思路参考。经逐文件相似度审计（Jaccard ≤0.094，结构人工对照），**无 GPL 代码进入本项目**。
- **APKUpdater**（https://github.com/rumboalla/apkupdater，GPL-3.0，Copyright 2024 rumboalla）：安装会话思路参考。经逐文件相似度审计（Jaccard ≤0.103，结构人工对照），**无 GPL 代码进入本项目**。

## 4. 已排除来源

- **Chastify**（https://github.com/kiks71/chastify）：历史提交信息中出现的引用；经核验该仓库为浏览器扩展项目（无许可证、与 Android 无关），与 PhoneGuard 无代码关联，不构成来源。

## 5. 自带资产

- 应用图标：自研设计。
- 应用内文案、内置应用分类表、全部源代码与测试：原创。

## 6. 声明

本文件随项目分发。本项目源代码按 AGPL-3.0-or-later 许可（见 LICENSE），第三方组件的各自许可证不受影响。
