package com.familyguard.core.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class InstalledAppNameFilterTest {
    @Test
    fun `does not treat package name as application label`() {
        val result = InstalledAppFilter.displayableStored(
            listOf("com.example.app" to "com.example.app"),
        )

        assertEquals(emptyList<Pair<String, String>>(), result)
    }

    @Test
    fun `live inventory and stored inventory both reject vendor internals`() {
        val live = InstalledAppFilter.displayable(
            listOf(
                InstalledAppInfo("com.oppo.ota", "System Update", false, true),
                InstalledAppInfo("com.redteamobile.roaming", "Roaming", false, true),
                InstalledAppInfo("com.tencent.mm", "WeChat", false, true),
            ),
        )
        val stored = InstalledAppFilter.displayableStored(
            listOf(
                "com.oppo.ota" to "System Update",
                "com.redteamobile.roaming" to "Roaming",
                "com.tencent.mm" to "WeChat",
            ),
        )

        assertEquals(listOf("com.tencent.mm" to "WeChat"), live)
        assertEquals(live, stored)
    }
}
