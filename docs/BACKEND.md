# PhoneGuard 后端接口契约（core/backend 可替换层）

> `core/backend/` 是**可替换层**：业务代码只依赖本层定义的能力。默认实现为腾讯云开发 CloudBase（REST API，见 `CloudBaseClient`），自建后端时按本契约实现等价能力即可。

## 1. 能力总览

| 能力 | 入口（core/backend） | 说明 |
|---|---|---|
| 认证 | `CloudBaseAuth` | 邮箱验证码注册/登录（管理端）；匿名登录（被控端，x-device-id） |
| 数据库 | `CloudBaseDb` | 文档集合的插入/查询/更新/删除（带 EJSON 数字解包） |
| 绑定 | `CloudBaseBindings` | 邀请码生成（6 位、7 天有效、旧码失效）、绑定、查询 |
| 规则 | `CloudBaseRules` | 规则信封保存/拉取（admin 按 adminUid，kid 按 kidDeviceId） |
| 使用报告 | `CloudBaseUsage` | 心跳 upsert、最近快照/趋势查询 |
| 异常事件 | `CloudBaseEvents` | 事件上报、未读/全部查询、已读/确认/解决、健康条件对账 |
| 已装应用 | `CloudBaseApps` | 应用列表 upsert/查询 |
| 更新 | `core/update` | 更新清单获取/验证（ECDSA + SHA-256）、APK 下载 |

## 2. 认证

- **管理端**：邮箱验证码 → `verification_token` → 注册（用户名+密码）；之后用户名+密码登录。返回 `access_token`（短期）+ `refresh_token`（长期，自动轮换）。
- **被控端**：匿名登录（携带稳定的设备标识 x-device-id），同一设备长期有效。
- 所有数据请求携带 `Authorization: Bearer <access_token>`；401 时自动用 refresh_token 刷新并重试一次。

## 3. 数据模型（5 集合）

| 集合 | 文档字段 | 写入方 | 读取方 |
|---|---|---|---|
| bindings | inviteCode / adminUid / kidDeviceId / status(PENDING\|BOUND\|EXPIRED) / createdAt / boundAt / expiresAt | 管理端生成；被控端绑定（仅 PENDING） | 管理端、被控端 |
| rules | adminUid / kidDeviceId / ruleEnvelope / updatedAt | 管理端 | 管理端（adminUid）、被控端（kidDeviceId） |
| usage | kidDeviceId / adminUid / date / byPackage / totalMinutes / currentApp / reportedAt / 权限健康 / 设备 / 更新状态 | 被控端 | 管理端、被控端 |
| events | kidDeviceId / adminUid / type / message / status(OPEN\|ACKNOWLEDGED\|RESOLVED) / occurredAt / firstSeenAt / lastSeenAt / dedupKey / occurrenceCount / read | 被控端 | 管理端、被控端 |
| apps | kidDeviceId / adminUid / apps[{pkg,name}] / updatedAt | 被控端 | 管理端 |

**归属约定**（安全规则依赖）：
- 被控端写入 usage/events/apps 时携带 `adminUid`（绑定管理员）；
- 管理端写入 rules 时携带 `kidDeviceId`（当前绑定被控端）；
- 查询：管理端按 `adminUid` 作用域，被控端按自身 `kidDeviceId` 作用域。

## 4. 查询语义

- 查询条件为等值匹配（`where`）；管理端查询**必须**附带 `adminUid` 条件（满足字段级安全规则）；被控端查询按自身 `kidDeviceId`。
- 更新为按条件更新（`where` + `$set` 语义），更新条件同样携带归属字段，防止越权写。

## 5. 安全要求（自建实现必须满足）

1. 所有传输 HTTPS；
2. 字段级授权：跨家庭读写一律拒绝（等价于 docs/DEPLOY.md 第 3 节规则）；
3. token 有有效期并支持刷新轮换；
4. 邀请码：短有效期、生成新码时旧码失效；
5. 审计：管理端与被控端身份可区分（实名/匿名 uid）。
