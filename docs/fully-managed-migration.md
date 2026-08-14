# OPPO 被控端 Fully Managed 迁移手册

状态：预演版。任何移除账户、卸载旧包、设置 Device Owner 或恢复出厂操作，都必须先取得设备所有者明确确认。

## 已确认环境

- 设备：OPPO PDKM00
- 系统：Android 12 / API 31
- 用户：仅主用户 0
- 当前 Device Owner：无
- 当前账户：淘宝、`com.xingin.xhs.strategy.account`
- 旧被控端：`com.familyguard.kid`，开发签名
- 正式被控端：`com.familyguard.kid`，release 签名
- 正式证书 SHA-256：`41091d4667b59d051895abdf1e01c32e83cb1aea0e8ca2b253c9020d6e29e937`

开发签名与正式签名不同，不能直接覆盖安装。迁移必然包含一次卸载旧包，因此旧包本地偏好和权限会丢失，需要重新绑定和授权。

## 迁移前门禁

1. release APK 构建、lint、签名验证通过。
2. keystore 已有至少两份离线加密备份，并验证能读取证书。
3. 管理端可以生成新的绑定邀请码。
4. 手机照片、文件和必要应用数据已备份。
5. 已确认淘宝、小红书账户的登录恢复方式。
6. USB、充电、电量、Wi-Fi稳定，迁移过程中不拔线。

## 优先路径：不恢复出厂

以下步骤必须人工逐项确认，不做自动批量脚本：

1. 在系统设置中移除淘宝和小红书账户，仅移除账户，不卸载应用。
2. 再次运行 `adb shell dumpsys account`，确认 `Accounts: 0`。
3. 停用旧包的普通 Device Admin。
4. 卸载开发签名的 `com.familyguard.kid`。
5. 安装 `kid/build/outputs/apk/release/kid-release.apk`。
6. 验证安装包证书与本手册记录的 SHA-256 一致。
7. 执行：

   ```powershell
   adb shell dpm set-device-owner com.familyguard.kid/.protect.KidDeviceAdminReceiver
   ```

8. 验证：

   ```powershell
   adb shell dpm list-owners
   adb shell dumpsys device_policy
   ```

9. 打开被控端，用管理端新邀请码重新绑定。
10. 重新授权 Usage Access、无障碍、悬浮窗、通知、电池不优化和 OPPO 自启动。
11. 验证自身卸载被阻止、正式包版本和管理端心跳正常。

如果第 7 步提示设备已完成配置或 OEM 禁止设置 Device Owner，立即停止，不尝试修改 `/data/system`、不使用 root 工具，转入恢复出厂路径。

## 兜底路径：恢复出厂 + 二维码配网

1. 完成完整数据备份，并确认可以登录原有账户。
2. 恢复出厂。
3. 在欢迎页进入二维码 provisioning。
4. 二维码只允许 HTTPS 下载已固定签名的 release APK，并包含 admin receiver 组件名。
5. Setup Wizard 调用 `GET_PROVISIONING_MODE` 时，被控端选择 fully managed。
6. `ADMIN_POLICY_COMPLIANCE` 回调中应用最小基线：防卸载、禁止未知来源、支持说明。
7. 完成绑定和全部权限引导。

## 成功验收

- `dpm list-owners` 显示 `com.familyguard.kid/.protect.KidDeviceAdminReceiver` 为 Device Owner。
- release 包证书 SHA-256 匹配。
- `setUninstallBlocked` 已生效，系统拒绝卸载自身。
- 拨号、短信、系统 UI 和必要设置未被误锁。
- 重启后守护与心跳恢复。
- 拔掉 USB 后，管理端仍能看到规则 revision、健康快照和版本。
- 之后通过签名更新链完成一次 canary 自更新。

## 禁止事项

- 不把开发签名包设置为 Device Owner。
- 不把 keystore、密码或清单签名私钥提交到 Git。
- 不通过改写 `/data/system/device_owner_2.xml` 绕过系统 provisioning。
- 不使用“信任任意证书”、任意 URL 安装或无障碍自动点击安装界面。
- 未验证恢复路径前，不启用 kiosk、禁用设置、恢复出厂或远程清除数据策略。
