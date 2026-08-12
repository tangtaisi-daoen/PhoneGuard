package com.familyguard.core.categories

/**
 * 内置分类表：覆盖国内主流 app 的包名 → 类别映射。
 * 命中规则为精确匹配（包名唯一）；未知包名回退 OTHER。
 * 表内包名缺失时，管理端可手动补充（见 specs/spec.md 分类策略）。
 */
object CategoryRegistry {

    private val builtin: Map<String, AppCategory> = mapOf(
        // ---- 短视频 ----
        "com.ss.android.ugc.aweme" to AppCategory.SHORT_VIDEO, // 抖音
        "com.ss.android.ugc.aweme.lite" to AppCategory.SHORT_VIDEO, // 抖音极速版
        "com.smile.gifmaker" to AppCategory.SHORT_VIDEO, // 快手
        "com.kuaishou.nebula" to AppCategory.SHORT_VIDEO, // 快手极速版
        "com.ss.android.ugc.live" to AppCategory.SHORT_VIDEO, // 西瓜视频
        "com.bilibili.app.in" to AppCategory.SHORT_VIDEO, // 哔哩哔哩
        "com.kuaishou.gift" to AppCategory.SHORT_VIDEO, // 快手礼物（关联）
        // ---- 长视频 ----
        "com.tencent.qqlive" to AppCategory.VIDEO, // 腾讯视频
        "com.qiyi.video" to AppCategory.VIDEO, // 爱奇艺
        "com.youku.phone" to AppCategory.VIDEO, // 优酷
        "com.hunantv.imgo.activity" to AppCategory.VIDEO, // 芒果TV
        // ---- 游戏 ----
        "com.tencent.tmgp.sgame" to AppCategory.GAME, // 王者荣耀
        "com.tencent.tmgp.pubgmhd" to AppCategory.GAME, // 和平精英
        "com.tencent.tmgp.jcc" to AppCategory.GAME, // 金铲铲之战
        "com.tencent.tmgp.cf" to AppCategory.GAME, // 穿越火线
        "com.tencent.tmgp.naruto" to AppCategory.GAME, // 火影忍者手游
        "com.tencent.KiHan" to AppCategory.GAME, // 火影忍者（腾讯）
        "com.tencent.gamehelper.pg" to AppCategory.GAME, // 和平营地（和平精英助手）
        "com.miHoYo.Yuanshen" to AppCategory.GAME, // 原神
        "com.miHoYo.hkrpg" to AppCategory.GAME, // 崩坏：星穹铁道
        "com.mojang.minecraftpe" to AppCategory.GAME, // 我的世界
        "com.miniteam.miniworld" to AppCategory.GAME, // 迷你世界
        "com.qqgame.hlddz" to AppCategory.GAME, // 欢乐斗地主
        // ---- OPPO/ColorOS 游戏生态 ----
        "com.nearme.gamecenter" to AppCategory.GAME, // OPPO 游戏中心
        "com.oplus.games" to AppCategory.GAME, // OPPO 游戏助手
        "com.oplus.play" to AppCategory.GAME, // OPPO 小游戏
        "com.oplus.gamecenter" to AppCategory.GAME, // ColorOS 游戏中心（新版）
        // ---- 小米/其他游戏 ----
        "com.miHoYo.bh3" to AppCategory.GAME, // 崩坏3
        "com.netease.klzj" to AppCategory.GAME, // 明日之后
        "com.tencent.tmgp.qqsm" to AppCategory.GAME, // 腾讯赛车
        "com.hypergryph.arknights" to AppCategory.GAME, // 明日方舟
        // ---- 社交 ----
        "com.tencent.mm" to AppCategory.SOCIAL, // 微信
        "com.tencent.mobileqq" to AppCategory.SOCIAL, // QQ
        "com.qzone" to AppCategory.SOCIAL, // QQ空间
        "com.xingin.xhs" to AppCategory.SOCIAL, // 小红书
        "com.sina.weibo" to AppCategory.SOCIAL, // 微博
        "com.baidu.tieba" to AppCategory.SOCIAL, // 贴吧
        "com.zhihu.android" to AppCategory.SOCIAL, // 知乎
        // ---- 工具/系统 ----
        "com.android.settings" to AppCategory.TOOL,
        "com.android.systemui" to AppCategory.TOOL,
        "com.android.launcher" to AppCategory.TOOL,
    )

    /** 按包名分类，未命中回退 OTHER。 */
    fun classify(packageName: String): AppCategory =
        builtin[packageName] ?: AppCategory.OTHER

    /** 供管理端展示/校验的内置表副本。 */
    fun builtinTable(): Map<String, AppCategory> = builtin
}
