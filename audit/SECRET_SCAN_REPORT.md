# PhoneGuard 密钥与机密扫描报告（SECRET SCAN）

> 审计时间：2026-08-14
> 方法：Git 历史全量 diff 扫描 + 工作树正则扫描 + 高风险文件人工核查
> 本报告不含任何密钥/凭据原文，仅含指纹与判定结论。

## 1. 扫描模式与命中情况

| 模式 | 工作树命中 | Git 历史命中 | 判定 |
|---|---|---|---|
| `BEGIN (RSA\|EC\|OPENSSH\|DSA\|PGP )?PRIVATE KEY` | 0 | 0 | 无私钥明文 |
| 腾讯云 `AKID...` 访问密钥 | 0 | 0 | 无 |
| `storePassword=` / `keyPassword=` 带真实值 | 0（均为 `getProperty()` 读取） | 0（同上） | 无硬编码 |
| `appAccessKey=` 带 16+ hex 值 | 1（cloudbase.local.properties，gitignored） | 0 | 本地文件，未泄露 |
| `password=` 带真实值 | 1（cloudbase.local.properties，gitignored） | 0（其余为测试/UI 参数） | 本地文件，未泄露 |
| 邮箱（非示例域） | 1（cloudbase.local.properties） | 29（Git 作者，同一地址） | 作者信息见隐私审计 3.1 |
| 中国手机号 `1[3-9]\d{9}` | 0 | 0 | 无 |
| 局域网 IP（192.168/10./172.16-31） | 0 | 0 | 无 |
| Windows 路径 `C:\Users\|C:/Users` | 2（AGENTS.md、backup 文档） | 1（AGENTS.md 提交） | 见隐私审计 3.2/3.3 |
| 长 hex（40+） / 长 base64（48+） | 命中均为 commit SHA、gradlew 哈希、ECDSA 公钥/签名、JSON 时间戳 | 同左 | 全部为公开/无害值 |
| 设备标识关键词（serial/imei/androidId/deviceId） | 命中均为序列化字段、测试设备名（test-kid-device-001）与计划文本 | 同左 | 无真实设备标识 |
| 历史新增敏感文件名（keystore/.pem/.jks/.properties/.env/apk/图片） | — | 仅 gradle.properties 与 gradle-wrapper.properties（常规） | 无敏感文件进入历史 |

## 2. 高风险文件核查结果（指纹，无原文）

| 文件 | SHA-256 | 跟踪状态 | 内容判定 |
|---|---|---|---|
| keystore.properties | E266CE4C309BE44EE5EA6C72C0A94A1F744C81A360F18D10B47850538426A0E2 | 未跟踪，gitignored | 签名配置（密码类），不发布 |
| phoneguard-release.keystore | 06E7F30B0317BA7692A34D3132BF346BD12816290ABAFD78869128474FACF982 | 未跟踪，gitignored | APK 签名私钥库，不发布 |
| release-keys/update-manifest-private.pem | F682520818D782464CCAB432271A892D611A96EEB250099038B760F346919FC2 | 未跟踪，gitignored | 更新清单私钥，不发布 |
| cloudbase.local.properties | 6C626BB32FBAA327D580B2F7F86050E217549220BC9AA0F3E10F7E4812A4935A | 未跟踪，gitignored | CloudBase 凭据，不发布，建议轮换 |
| CloudBaseTestConfig.kt | 305FD5C99BA97C65C89B0C949DE54FBA85681B4A41B73AEC2BF7DC61C5955986 | **已跟踪** | 纯加载器，无凭据，可发布 |
| CloudBaseVerifyTest.kt | （见文件本身） | 已跟踪 | 无硬编码凭据，可发布 |

## 3. 公开验证值清单（非机密，设计上需公开）

- APK 签名证书 SHA-256：`41091d4667b59d051895abdf1e01c32e83cb1aea0e8ca2b253c9020d6e29e937`（provisioning 校验用）
- 更新清单 ECDSA 公钥（DER base64，客户端内嵌）：`MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEm0Oju++9WsBDfX15UJhPjNt/wGmU9ApxjOaNToumWxCl0RY8iSto0vgmIVzhAaXE3/BuZKUunx9n1Ud9C06ObA==`
- provisioning APK 校验和（base64 SHA-256）：`twhvgLOqJZ9KmX2tKCxKDsEl1uagg3fQeN0ls42WrJg`（通用版）
- provisioning 签名证书校验和：`QQkdRme1nQUYlavfHgHDLoPLGuoOjKKyU8kCDW4p6Tc=`（OPPO 版）

## 4. 结论

1. 未发现任何凭据/密钥进入 Git 历史（含所有分支与 tag）。
2. 未发现测试账号、邀请码、绑定码、登录凭据、logcat、崩溃栈、设备备份进入仓库。
3. 4 个本地敏感文件正确 gitignored；建议对 cloudbase.local.properties 中的 appAccessKey 做一次轮换（本地文件曾用于真实链路验证，属谨慎性轮换）。
4. 需要用户决策的项：Git 作者信息（邮箱即个人标识）、AGENTS.md 绝对路径（已进历史）。
