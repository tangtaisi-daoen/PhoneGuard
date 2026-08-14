# PhoneGuard 用户隐私暴露面审计（以用户目的为中心）

> 审计时间：2026-08-14（重新聚焦版）
> 出发点（用户目的）：① 自用——家庭管控数据存放在本人 CloudBase 环境；② 开源发布——源码公开到 GitHub。
> 核心问题：**公开仓库中的信息，能否让陌生人触达用户本人的云端家庭数据？**

## 1. 威胁模型（暴露链）

```
公开仓库（当前文件 + Git 历史）
   │  ① 提取真实环境 ID：YOUR_ENV_ID
   ▼
连接 REST API：https://{envId}.api.tcloudbasegateway.com
   │  ② 匿名登录（被控端同款通道，无需注册）
   ▼
数据库安全规则（2026-08-14 实测）：5 集合全部 {"read": true, "write": true}
   │  ③ 任意读写
   ▼
家庭数据完全暴露：绑定关系/管控规则/孩子使用报告/异常事件/已装应用列表
   + 篡改能力：解除孩子限制、伪造数据、占满资源点配额
```

**结论：按现状直接开源（不改写历史、不收紧规则）= 用户家庭隐私数据可被任何互联网用户读取与篡改。级别：Critical。**

## 2. 暴露面定位（实测）

### 2.1 真实环境 ID 在仓库中的位置

| 位置 | 文件 | 是否进入公开仓库 |
|---|---|---|
| 当前工作树 | admin/build.gradle.kts、kid/build.gradle.kts、KidUpdateManager.kt、AGENTS.md、tasks/plan.md、CloudBaseTestConfig.kt、docs/provisioning/*.json ×2、docs/updates/*.json、audit/ 报告 ×4 | ✅ 是（10 处） |
| Git 历史 | 9 个提交（从 30ff118 首个提交起） | ✅ 是（公开后任何人可挖出） |
| 本地忽略 | cloudbaserc.json、cloudbase.local.properties | ❌ 否（gitignored） |
| 云端托管域名 | `YOUR_ENV_ID-0000000000.tcloudbaseapp.com`（更新清单/provisioning/代码内） | ✅ 是（同 envId 同源） |

### 2.2 数据库访问权（实测）

| 集合 | 规则 | 含义 |
|---|---|---|
| bindings / rules / events / usage / apps | `{"read": true, "write": true}` | 任何登录/匿名用户可读写全部文档 |

### 2.3 其他个人标识（此前已审计，结论不变）

- Git 提交作者邮箱 `dev@users.noreply.github.com`（即 QQ/手机号）——全部 29+ 提交；**已批准发布时匿名化**
- Windows 用户名/绝对路径（AGENTS.md 已去路径；backup 文档已 gitignore）
- 真实设备序列号文档（已 gitignore）
- 云端本身存有真实家庭数据（usage/events/bindings）——防护依赖上述暴露链切断

## 3. 处置方案（全部在发布时执行，不动自用代码）

| 优先级 | 处置 | 作用 | 执行时点 |
|---|---|---|---|
| P0 | git filter-repo --replace-text：将 envId 与托管域名 URL 在**全部历史+当前文件**替换为占位符（与作者匿名化同批执行） | 切断暴露链源头① | 阶段七（发布准备） |
| P0 | 数据库安全规则收紧（5 集合字段级规则，JSON 见 BACKEND_RULES_AUDIT.md §3a；控制台粘贴或所属账号 CLI） | 纵深防御②③：即使 envId 泄露（如曾安装过内置 envId 的 APK 被逆向），跨用户读写被拒 | 任意时间（建议发布前） |
| P1 | Git 作者邮箱/用户名匿名化（已批准） | 防个人身份关联 | 阶段七 |
| P2 | 发布仓库文件复核（PUBLISHABLE_FILE_MANIFEST.md）：audit/ 报告脱敏、备份文档排除 | 防残留 | 阶段八前 |

## 4. 门禁结论

- [x] 暴露链各环节均已实测定位（仓库 10 处 + 历史 9 提交 + 云端规则全开放）
- [x] 处置方案覆盖全部环节（源头切断 + 纵深防御 + 身份匿名）
- [x] 所有处置均不影响自用构建（自用代码保持写死 envId 原样）
- [ ] P0 两项执行（阶段七发布准备时，需用户确认后执行）
- [ ] 规则收紧（用户操作或所属账号授权后执行）

> 用户决策记录：发布形态=纯自托管语义（公开仓库不含真实环境 ID）；自用版代码保持原样。本报告为该决策下的暴露面审计。
> 补充决策（2026-08-14）：自建指南**厂商中立**——不推荐任何云服务品牌；CloudBase 作为默认实现仅以"示例"表述，并提供 core/backend 接口文档，让个人用户自行选择云厂商或自建后端（接口契约在阶段八文档化）。
