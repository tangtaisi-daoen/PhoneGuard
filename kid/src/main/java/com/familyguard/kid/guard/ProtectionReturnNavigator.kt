package com.familyguard.kid.guard

/** 关闭危险设置页后只回桌面，不再自动拉起被控端，彻底切断任务栈循环。 */
internal class ProtectionReturnNavigator(
    private val dismissProtectedSurface: () -> Unit,
    private val scheduleLauncher: (Long) -> Unit,
) {
    fun returnToLauncher() {
        dismissProtectedSurface()
        scheduleLauncher(LAUNCHER_DELAY_MS)
    }

    private companion object {
        const val LAUNCHER_DELAY_MS = 180L
    }
}
