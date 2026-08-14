# AGENTS.md

本文件为 AI 编码代理（OpenCode / Claude Code / Cursor / Codex 等）在本仓库工作时的强制规则与项目约定。

## 核心规则（必须遵守）

1. **先查 skill，再动手**：任何任务开始前，先判断是否有 skill 适用；有则**必须**通过 `skill` 工具加载并严格执行，禁止跳过直接实现。
2. **不得部分执行**：加载 skill 后按其步骤完整执行，包括验证门禁（tests、build、review 等）。
3. **禁止合理化跳步**：以下想法均为错误，必须忽略：
   - "这个改动太小，用不上 skill"
   - "我可以直接快速实现"
   - "我先了解一下上下文再说"
   - "测试以后再补"
4. **先 spec / plan，后代码**：任何非 trivial 改动必须先产出规格/计划并获得用户确认，再进入实现。

## Intent → Skill 路由

| 用户意图 | 应加载的 skill |
|---|---|
| 新功能 / 新需求 | `spec-driven-development` → `planning-and-task-breakdown` → `incremental-implementation` + `test-driven-development` |
| 设计系统 / 写 PRD | `spec-driven-development` |
| 任务拆解 / 规划 | `planning-and-task-breakdown` |
| 修复 bug / 异常行为 | `debugging-and-error-recovery` |
| 代码评审 | `code-review-and-quality` |
| 重构 / 简化 | `code-simplification` |
| API / 接口设计 | `api-and-interface-design` |
| UI / 前端工作 | `frontend-ui-engineering` |
| 安全相关 | `security-and-hardening` |
| git 提交 / 分支 | `git-workflow-and-versioning` |
| 上线 / 部署 | `shipping-and-launch` + `ci-cd-and-automation` |
| 需求模糊 / 对齐需求 | `grill-me`（追问直到需求清晰） |
| 实现逻辑 / 修 bug（严格 TDD） | `tdd`（red-green-refactor） |
| 任何代码改动 | `incremental-implementation`（切薄切片、逐片验证提交） |

## 开发生命周期（隐式流程）

- **DEFINE** → `spec-driven-development`
- **PLAN** → `planning-and-task-breakdown`
- **BUILD** → `incremental-implementation` + `test-driven-development`
- **VERIFY** → `debugging-and-error-recovery`
- **REVIEW** → `code-review-and-quality`
- **SHIP** → `shipping-and-launch`

## 执行模型（每个请求）

1. 判断是否有 skill 适用（哪怕 1% 可能也要先检查）；
2. 用 `skill` 工具加载对应 skill；
3. 严格执行 skill 工作流；
4. 完成必需步骤（spec、plan、测试、评审）后才允许提交代码；
5. 提交前确认测试通过、无回归、行为已实际验证——"看起来没问题"永远不够。

## 质量门禁

- 每次提交前：相关测试通过 + lint 通过 + 变更尽量小（约 100 行内，可拆分时拆分）。
- 每次提交：原子提交，遵循 Conventional Commits（`feat:` / `fix:` / `refactor:` / `docs:` ...）。
- 变更合入前：执行 `code-review-and-quality` 五轴评审。
- 通用完成标准见 `~/.config/opencode/references/definition-of-done.md`。

## 项目信息

- **技术栈**：Android 原生 Kotlin（minSdk 26 / targetSdk 34），Gradle 多模块：`:core`（共享逻辑）/ `:admin`（管理端）/ `:kid`（被控端）
- **后端**：腾讯云开发 CloudBase（免费体验版环境 `YOUR_ENV_ID`），接入方式见 `specs/spec.md`，封装在 `core/backend/`（可替换层）
- **构建命令**：`./gradlew :admin:assembleDebug` / `./gradlew :kid:assembleDebug`
- **测试命令**：`./gradlew test`（core 规则引擎 JUnit ≥90% 覆盖；kid 用 Robolectric）
- **Lint 命令**：`./gradlew lint`
- **目录结构**：`specs/`（规格）、`tasks/`（计划与任务）、`docs/`（OPPO 引导、部署）、`core/ admin/ kid/`（源码模块）
- **代码风格**：MVVM，UI 文案全部走 res/values/strings.xml，关键业务中文注释，详见 `specs/spec.md` Code Style
- **边界规则**：见 `specs/spec.md` Boundaries（Always / Ask first / Never 三层）
- **验收设备**：OPPO A92s（Android 10/11，无 GMS），真机验收清单见 `docs/`

## 维护

- 本项目使用全局 skill 目录：`~/.config/opencode/skills/`（agent-skills 24 个 + tdd + grill-me）。
- 更新 skill：`git -C <agent-skills 本地仓库> pull && git -C <mattpocock-skills 本地仓库> pull`，然后重新拷贝到全局目录。
