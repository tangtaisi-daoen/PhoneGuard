# PhoneGuard 许可证策略：选项分析、决策记录与商业使用边界

> 状态：方向已确认（2026-08-14），正式落地（LICENSE/NOTICE/文件头）在阶段二来源审计完成后执行。
> 本文档为阶段三（许可证与发布策略）的前置决策记录，属于 `DEEPSEEK_OPEN_SOURCE_RELEASE_PLAN.md` 第 1 节 / 第 8 节的执行产物。

## 1. 目标（来自总任务）

- 个人用户可以自由下载使用；
- 尽量避免商业公司拿源码闭源后直接收费；
- 不把"禁止商业使用"误称为严格意义的 Open Source（OSI 定义：开源许可证不得限制使用领域，包括商业使用）。

## 2. 参考项目许可证实时核对（2026-08-14，以 GitHub 仓库 LICENSE 文件为准）

| 项目 | 计划初步记录 | 实际核实结果 | 结论 |
|---|---|---|---|
| KidSafe (xMansour/KidSafe) | MIT | LICENSE 文件确认为 MIT（© 2019 Mahmoud Mansour） | ✅ 与记录一致 |
| cst (childscreentime/cst) | MIT（未证实） | main 分支根目录无任何许可证文件（LICENSE / LICENSE.md 均不存在，GitHub 元数据 license 字段为空） | ⚠️ 默认"保留所有权利"，"初步 MIT"未获证实，属高风险待办 |
| TestDPC (googlesamples/android-testdpc) | Apache-2.0 | LICENSE 确认为 Apache-2.0 全文 | ✅ 一致 |
| Headwind MDM (h-mdm/hmdm-android) | Apache-2.0 | LICENSE 确认为 Apache-2.0（© 2018 Vsevolod Mayorov） | ✅ 一致 |
| Curbox (curbox-app/curbox-android) | GPL-3.0-or-later | LICENSE 确认为 GPLv3 全文（默认分支 kt-rewrite） | ✅ 高风险传染源，待阶段二逐文件相似度判断 |
| APKUpdater (rumboalla/apkupdater) | GPL-3.0 | LICENSE 确认为 GPLv3 全文（© 2024 rumboalla） | ✅ 高风险传染源，待阶段二逐文件相似度判断 |

## 3. 两个必须先接受的法律现实

1. **「禁止商业使用」≠ 开源**。OSI 的 Open Source Definition 明确要求开源许可证不得限制特定领域的使用，包括商业使用（https://opensource.org/osd 、https://opensource.org/faq）。任何带非商业条款的方案（PolyForm Noncommercial、BSL、Commons Clause 等）都只能称为 **source-available**，不得对外宣称"开源"。
2. **GPL/AGPL 防的不是"收费"，是"收钱还不给源码"**。GPLv3 第 4 节明确允许收费（"You may charge any price or no price"），但向接收者分发时必须提供对应源码，接收者可免费再分发——商业闭源收费模式因此被架空。GPLv3 只约束"分发副本"；把修改版跑成网络服务（SaaS）而无分发行为时无开源义务（俗称 SaaS 漏洞）。AGPL-3.0 第 13 节补上该漏洞：通过网络交互提供服务同样触发源码提供义务。

## 4. 候选方案与后果

| 方案 | OSI 开源 | 防商业闭源收费 | 与现有 GPL 参考代码兼容 | 维护成本 |
|---|---|---|---|---|
| 1. AGPL-3.0-or-later | ✅ | 强：分发必开源；SaaS 托管修改版也须提供源码 | ✅ GPLv3 §13 明确允许与 AGPLv3 组合 | 低（零流程） |
| 2. GPL-3.0-or-later | ✅ | 中：分发必开源；SaaS 托管修改版无义务 | ✅ 天然兼容 | 低 |
| 3. 双许可：AGPL + 商业许可 | ✅（主版本） | 最强：闭源商用必须购买商业授权 | ✅（需 CLA 才能再授权第三方贡献） | 高（授权流程 + CLA + 法律文本） |
| 4. Source-available 非商用（PolyForm Noncommercial 1.0.0 等） | ❌ 只能称 source-available | 直接禁止商业使用 | ❌ 与 GPL 冲突，须先清除 GPL 代码 | 中 |
| 5. MIT / Apache-2.0 | ✅ | ❌ 无防护 | ✅ | 低 |

## 5. 用户决策（2026-08-14 确认）

**主许可证方向：AGPL-3.0-or-later**

理由：符合 OSI 开源定义；防闭源收费效果最强（覆盖分发与 SaaS 两条路径）；与仓库内 GPL-3.0 参考代码天然兼容（GPLv3 §13）；零维护成本。

保留选项：若未来想为"公司闭源商用"提供付费通道，可在 AGPL 基础上追加商业授权（双许可），届时需引入 CLA 与授权流程。

## 6. 决策影响与待办（进入阶段二/三后的动作）

- [ ] 阶段二：对 Curbox、APKUpdater 做逐文件相似度判断。若存在代码级派生 → 相关文件保留 GPL-3.0 版权头并注明派生来源，整体仍可落 AGPL（GPLv3 §13 兼容）；若仅为设计参考 → 无传染，正常落 AGPL。
- [ ] 阶段二：cst 无许可证问题专项处理——查证计划所引用 commit 历史中是否存在许可证文件；无则视为"设计参考"或联系作者，禁止直接复制其代码；本地若存在复制片段需重写或标注。
- [ ] 阶段三：落地 `LICENSE`（AGPL-3.0 官方全文）、`NOTICE`（MIT/Apache 来源声明，含 KidSafe/TestDPC/Headwind 版权行）、`THIRD_PARTY_NOTICES.md`、`audit/LICENSE_COMPATIBILITY_MATRIX.csv`。
- [ ] 阶段三：源码文件头统一增加 AGPL 版权声明（仓库根 LICENSE 已覆盖时按项目惯例处理）。
- [ ] 阶段七/八：发布文案（README/关于页）使用"AGPL-3.0-or-later 开源"表述，不得出现"禁止商业使用"等与开源定义冲突的表述。

## 7. 商业使用边界说明（AGPL-3.0 下的权利义务，供 README/FAQ 引用）

- **个人用户**：自由使用、修改、分发（保留版权与许可证声明）。
- **公司 / 商业主体**：
  - 分发修改版（预装、销售 APK、提供下载）→ 必须以 AGPL-3.0 提供对应源码；
  - 通过网络向用户提供修改版服务（SaaS / 云托管）→ AGPL-3.0 第 13 节要求向交互用户提供对应源码；
  - 原样使用官方发布版或官方提供的后端服务 → 无额外义务；
  - 闭源收费分发、闭源托管修改版 → 不允许。
- **商标**：AGPL-3.0 不授予商标使用许可；PhoneGuard 名称与图形标识的使用规则单独声明（本项目暂无独立商标声明，后续如需保护另出文档）。
- **免责**：以上为工程层面的合规说明，不构成法律意见；重大商业决策请咨询律师。

## 8. 引用依据

- https://opensource.org/osd
- https://opensource.org/faq
- https://www.fsf.org/bulletin/2021/fall/the-fundamentals-of-the-agplv3
- https://polyformproject.org/licenses/noncommercial/1.0.0/
- 各参考项目仓库 LICENSE 文件原文（见第 2 节，2026-08-14 核验）
