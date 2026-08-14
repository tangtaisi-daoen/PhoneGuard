package com.familyguard.core.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppFilterTest {
    @Test
    fun `keeps only launchable non-system apps`() {
        val result = InstalledAppFilter.displayable(
            listOf(
                InstalledAppInfo("com.tencent.mm", "微信", false, true),
                InstalledAppInfo("com.oplus.healthservice", "健康数据平台", true, false),
                InstalledAppInfo("com.android.settings", "设置", false, true),
                InstalledAppInfo("com.android.internal.service", "内部服务", false, false),
            ),
        )

        assertEquals(listOf("com.tencent.mm" to "微信"), result)
    }

    @Test
    fun `filters legacy stored system component rows`() {
        val result = InstalledAppFilter.displayableStored(
            listOf(
                "com.tencent.mm" to "微信",
                "com.android.settings" to "设置",
                "com.oplus.healthservice" to "健康数据平台",
                "com.google.android.webview" to "Android System WebView",
                "com.xingin.xhs" to "小红书",
            ),
        )

        assertEquals(listOf("com.xingin.xhs" to "小红书", "com.tencent.mm" to "微信"), result)
    }
}
