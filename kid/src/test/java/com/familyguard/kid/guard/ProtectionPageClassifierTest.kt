package com.familyguard.kid.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtectionPageClassifierTest {

    @Test
    fun `own app uninstall page is protected`() {
        assertEquals(
            ProtectionPageRisk.APP_REMOVAL,
            classifyProtectionPage(
                packageName = "com.android.settings",
                className = "com.android.settings.applications.InstalledAppDetails",
                visibleTexts = setOf("手机守护", "卸载", "强行停止"),
            ),
        )
    }

    @Test
    fun `another app details page is allowed`() {
        assertNull(
            classifyProtectionPage(
                packageName = "com.android.settings",
                className = "com.android.settings.applications.InstalledAppDetails",
                visibleTexts = setOf("微信", "卸载", "强行停止"),
            ),
        )
    }

    @Test
    fun `own device admin deactivation page is protected`() {
        assertEquals(
            ProtectionPageRisk.ADMIN_DEACTIVATION,
            classifyProtectionPage(
                packageName = "com.android.settings",
                className = "com.android.settings.DeviceAdminAdd",
                visibleTexts = setOf("手机守护", "取消激活"),
            ),
        )
    }

    @Test
    fun `own accessibility disable page is protected`() {
        assertEquals(
            ProtectionPageRisk.ACCESSIBILITY_DISABLE,
            classifyProtectionPage(
                packageName = "com.android.settings",
                className = "com.android.settings.accessibility.AccessibilityDetailsSettings",
                visibleTexts = setOf("手机守护", "使用服务", "关闭"),
            ),
        )
    }

    @Test
    fun `accessibility service list stays available`() {
        assertNull(
            classifyProtectionPage(
                packageName = "com.android.settings",
                className = "com.android.settings.Settings AccessibilitySettings",
                visibleTexts = setOf("手机守护", "关闭", "其他服务"),
            ),
        )
    }

    @Test
    fun `coloros accessibility detail subsettings is protected`() {
        assertEquals(
            ProtectionPageRisk.ACCESSIBILITY_DISABLE,
            classifyProtectionPage(
                packageName = "com.android.settings",
                className = "com.android.settings.SubSettings",
                visibleTexts = setOf(
                    "手机守护",
                    "开启",
                    "快捷启用",
                    "简介",
                    "用于检测并拦截超出使用时长限制的应用（手机守护核心功能）",
                ),
            ),
        )
    }

    @Test
    fun `generic subsettings mentioning app stays available`() {
        assertNull(
            classifyProtectionPage(
                packageName = "com.android.settings",
                className = "com.android.settings.SubSettings",
                visibleTexts = setOf("手机守护", "通知管理", "允许通知"),
            ),
        )
    }

    @Test
    fun `generic settings and untrusted packages are allowed`() {
        assertNull(classifyProtectionPage("com.android.settings", "com.android.settings.Settings", setOf("卸载")))
        assertNull(
            classifyProtectionPage(
                "com.example.fake",
                "com.android.settings.applications.InstalledAppDetails",
                setOf("手机守护", "卸载"),
            ),
        )
    }

    @Test
    fun `usage access details for own app are protected`() {
        assertEquals(
            ProtectionPageRisk.USAGE_ACCESS_DISABLE,
            classifyProtectionPage(
                "com.android.settings",
                "com.android.settings.Settings UsageAccessDetailsActivity",
                setOf("PhoneGuard", "Permit usage access", "Allow"),
            ),
        )
    }

    @Test
    fun `overlay details for own app are protected`() {
        assertEquals(
            ProtectionPageRisk.OVERLAY_DISABLE,
            classifyProtectionPage(
                "com.android.settings",
                "com.android.settings.Settings DrawOverlaySettingsActivity",
                setOf("PhoneGuard", "Display over other apps", "Allow"),
            ),
        )
    }

    @Test
    fun `coloros security permission overlay row for own app is protected`() {
        assertEquals(
            ProtectionPageRisk.OVERLAY_DISABLE,
            classifyProtectionPage(
                "com.oplus.securitypermission",
                "com.oplusos.securitypermission.permission.ui.handheld.PermissionAppsActivityNew",
                setOf("PhoneGuard", "悬浮窗", "已允许"),
            ),
        )
    }

    @Test
    fun `coloros autostart row for own app is protected`() {
        assertEquals(
            ProtectionPageRisk.AUTOSTART_DISABLE,
            classifyProtectionPage(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity",
                setOf("PhoneGuard", "Allow auto launch"),
            ),
        )
    }

    @Test
    fun `launcher uninstall action for own app is protected`() {
        assertEquals(
            ProtectionPageRisk.APP_REMOVAL,
            classifyProtectionPage(
                "com.oppo.launcher",
                "com.oppo.launcher.DeleteDropTarget",
                setOf("PhoneGuard", "Uninstall"),
            ),
        )
    }

    @Test
    fun `launcher homepage is not a protected surface`() {
        assertNull(
            classifyProtectionPage(
                "com.oppo.launcher",
                "com.oppo.launcher.Launcher",
                setOf("手机守护", "卸载", "电话", "相机"),
            ),
        )
    }

    @Test
    fun `coloros uninstall activity for own app is protected`() {
        assertEquals(
            ProtectionPageRisk.APP_REMOVAL,
            classifyProtectionPage(
                "com.oplus.appdetail",
                "com.oplus.appdetail.model.uninstall.UninstallPackageActivity",
                setOf("PhoneGuard", "Uninstall app", "Uninstall"),
            ),
        )
    }

    @Test
    fun `launcher uninstall action for another app remains available`() {
        assertNull(
            classifyProtectionPage(
                "com.oppo.launcher",
                "com.oppo.launcher.DeleteDropTarget",
                setOf("WeChat", "Uninstall"),
            ),
        )
    }
}
