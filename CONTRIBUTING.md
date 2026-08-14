# 贡献指南

感谢你对 PhoneGuard 的关注。本项目为个人维护的开源项目（AGPL-3.0-or-later）。

## 开发环境

- JDK 17+、Android SDK（compileSdk 34）、Gradle wrapper（9.1.0）
- 构建：`./gradlew :admin:assembleDebug :kid:assembleDebug`
- 测试：`./gradlew test`（core 规则引擎 JUnit；kid 为 JVM 单元测试）
- Lint：`./gradlew lint`

## 提交流程

1. 新功能/修复请先建 Issue 说明意图；
2. 分支命名 `fix/xxx` 或 `feat/xxx`；
3. 提交信息遵循 Conventional Commits（`feat:` / `fix:` / `refactor:` / `docs:` / `chore:`）；
4. 每个提交保持原子性（一个逻辑一个提交），相关测试通过后再提交；
5. 变更合入前通过 `./gradlew test` 与 lint（CI 会在 push/PR 时自动执行相同检查，见 `.github/workflows/ci.yml`）。

## 注意事项

- **不得提交**：任何真实环境 ID、数据库凭据、签名私钥、keystore、个人数据与本地路径（环境 ID 使用占位符 `YOUR_ENV_ID`）。
- 被控端新增权限前先说明用途（权限最小化原则）。
- 远程更新相关改动必须保留签名验证链（防回滚/防重放）。
- UI 文案走 `res/values/strings.xml`（中文为主）。
