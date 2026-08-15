# 安装与使用指引

## 双端 APK

构建产物（命令行构建）：

```bash
# debug（开发调试用）
./gradlew :admin:assembleDebug :kid:assembleDebug
# 输出：admin/build/outputs/apk/debug/admin-debug.apk
#      kid/build/outputs/apk/debug/kid-debug.apk

# release（签名版，正式使用推荐）
./gradlew :admin:assembleRelease :kid:assembleRelease
# 输出：admin/build/outputs/apk/release/admin-release.apk
#      kid/build/outputs/apk/release/kid-release.apk
```

把 APK 传到手机（微信文件传输/数据线复制均可）后安装，需允许"安装未知来源应用"。

## 管理端（家长手机）

1. 安装 `admin-release.apk` 并打开
2. 登录（用户名 `admin01`），或「去注册」新账号（需邮箱验证码）
3. 主界面点「生成/刷新邀请码」，记下 6 位邀请码
4. 点「开启异常通知」→ 允许通知权限（收异常提醒用）

## 被控端（孩子手机 OPPO A92s）

1. 安装 `kid-release.apk` 并打开
2. 输入管理端的 6 位邀请码 → 绑定
3. 点「开启防护」，按引导完成 **5 步**（每步点「去设置」并开启）：
   1. 使用情况访问 → 开启"手机守护"
   2. 无障碍服务 → 开启"手机守护"（点同意）
   3. 忽略电池优化 → 允许
   4. 自启动 → 在系统应用管理里允许"手机守护"自启动（ColorOS 版本不同入口略异）
   5. 设备管理器 → 激活（防卸载）
4. 回到「手机守护」主界面，确认显示"已绑定，守护生效中"，通知栏有常驻"手机守护运行中"

## 设置规则（管理端）

主界面 →「规则管理」：
- 每日总额：如 120 分钟（0 表示不限制）
- 分类限时：游戏 60 / 短视频 40 / 长视频 0 / 社交 0（0 表示不限制）
- 按应用限时：包名 + 分钟（如 `com.ss.android.ugc.aweme` 30）
- 点「保存并下发」，被控端 90 秒内生效

常用包名：抖音 `com.ss.android.ugc.aweme`、王者荣耀 `com.tencent.tmgp.sgame`、微信 `com.tencent.mm`、QQ `com.tencent.mobileqq`、哔哩哔哩 `com.bilibili.app.in`。其余可在手机"设置→应用"里查看包名，或在管理端直接填包名（未内置分类的按"未分类"处理）。

## 查看状态与报告（管理端）

- 「使用报告」：在线状态、当前使用 app、今日各 app 时长、类别汇总
- 「异常通知」：防卸载被解除/权限被关闭/时间被修改/新安装应用 等事件列表

## 常见问题

| 现象 | 处理 |
|---|---|
| 被控端收不到规则 | 确认绑定状态 + 引导页 5 项全开 + 网络正常（心跳 90s 同步） |
| 拦截不生效 | 检查无障碍 + 使用情况访问是否开启；重新打开「开启防护」页确认 |
| 手机重启后被控端停止 | 确认自启动已允许；前台服务 START_STICKY 会在开机后尽量恢复 |
| 管理端收不到通知 | 确认已点「开启异常通知」+ 系统通知权限已允许 |
