package com.familyguard.core.categories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 内置分类表测试：主流国内 app 包名应命中正确类别，未知包名回退 OTHER。
 */
class CategoryRegistryTest {

    @Test
    fun `已知短视频 app 应归为 SHORT_VIDEO`() {
        assertEquals(AppCategory.SHORT_VIDEO, CategoryRegistry.classify("com.ss.android.ugc.aweme")) // 抖音
        assertEquals(AppCategory.SHORT_VIDEO, CategoryRegistry.classify("com.smile.gifmaker")) // 快手
        assertEquals(AppCategory.SHORT_VIDEO, CategoryRegistry.classify("com.ss.android.ugc.aweme.lite")) // 抖音极速版
        assertEquals(AppCategory.SHORT_VIDEO, CategoryRegistry.classify("com.ss.android.ugc.live")) // 西瓜视频
    }

    @Test
    fun `已知游戏 app 应归为 GAME`() {
        assertEquals(AppCategory.GAME, CategoryRegistry.classify("com.tencent.tmgp.sgame")) // 王者荣耀
        assertEquals(AppCategory.GAME, CategoryRegistry.classify("com.tencent.tmgp.pubgmhd")) // 和平精英
        assertEquals(AppCategory.GAME, CategoryRegistry.classify("com.miHoYo.Yuanshen")) // 原神
        assertEquals(AppCategory.GAME, CategoryRegistry.classify("com.mojang.minecraftpe")) // 我的世界
    }

    @Test
    fun `已知社交 app 应归为 SOCIAL`() {
        assertEquals(AppCategory.SOCIAL, CategoryRegistry.classify("com.tencent.mm")) // 微信
        assertEquals(AppCategory.SOCIAL, CategoryRegistry.classify("com.tencent.mobileqq")) // QQ
        assertEquals(AppCategory.SOCIAL, CategoryRegistry.classify("com.sina.weibo")) // 微博
        assertEquals(AppCategory.SOCIAL, CategoryRegistry.classify("com.zhihu.android")) // 知乎
    }

    @Test
    fun `已知长视频平台应归为 VIDEO`() {
        assertEquals(AppCategory.VIDEO, CategoryRegistry.classify("com.tencent.qqlive")) // 腾讯视频
        assertEquals(AppCategory.VIDEO, CategoryRegistry.classify("com.qiyi.video")) // 爱奇艺
        assertEquals(AppCategory.VIDEO, CategoryRegistry.classify("com.youku.phone")) // 优酷
    }

    @Test
    fun `系统组件应归为 TOOL`() {
        assertEquals(AppCategory.TOOL, CategoryRegistry.classify("com.android.settings"))
        assertEquals(AppCategory.TOOL, CategoryRegistry.classify("com.android.systemui"))
    }

    @Test
    fun `未知或空白包名应回退 OTHER`() {
        assertEquals(AppCategory.OTHER, CategoryRegistry.classify("com.example.unknown.app"))
        assertEquals(AppCategory.OTHER, CategoryRegistry.classify(""))
        assertEquals(AppCategory.OTHER, CategoryRegistry.classify("com.tencent.tmgp.sgame.extra"))
    }

    @Test
    fun `分类表应非空且包名唯一`() {
        val table = CategoryRegistry.builtinTable()
        assertNotNull(table)
        assert(table.isNotEmpty()) { "内置分类表不应为空" }
        assert(table.keys.size == table.keys.distinct().size) { "内置分类表存在重复包名" }
    }
}
