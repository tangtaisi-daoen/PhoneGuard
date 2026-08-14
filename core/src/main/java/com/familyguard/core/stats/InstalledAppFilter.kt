package com.familyguard.core.stats

/** 被控端上报给管理端的应用摘要。 */
data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val hasLauncherEntry: Boolean,
)

/** 过滤系统内部组件，只保留用户可从桌面启动的应用。 */
object InstalledAppFilter {
    private val protectedPackages = setOf(
        "com.familyguard.kid",
        "com.familyguard.admin",
        "com.android.systemui",
        "com.android.settings",
    )

    fun displayable(apps: Iterable<InstalledAppInfo>): List<Pair<String, String>> = apps
        .asSequence()
        .filter { it.packageName.isNotBlank() && it.label.isNotBlank() }
        .filter { it.packageName !in protectedPackages }
        .filter { !it.isSystemApp && it.hasLauncherEntry }
        .filter { isLikelyUserFacing(it.packageName, it.label) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .map { it.packageName to it.label }
        .toList()

    /** 兼容旧版本上报的列表：没有系统标记时，按厂商内部包名前缀兜底过滤。 */
    fun displayableStored(apps: Iterable<Pair<String, String>>): List<Pair<String, String>> = apps
        .asSequence()
        .filter { (packageName, label) -> isLikelyUserFacing(packageName, label) }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
        .toList()

    private fun isLikelyUserFacing(packageName: String, label: String): Boolean {
        if (packageName.isBlank() || label.isBlank()) return false
        if (label == packageName) return false
        if (packageName in protectedPackages) return false
        if (packageName == "android" || packageName == "oplus" || packageName.startsWith("aon.")) return false
        val internalPrefixes = listOf(
            "com.android.",
            "com.google.android.",
            "com.oplus.",
            "com.coloros.",
            "com.mediatek.",
            "com.heytap.",
            "com.nearme.",
            "com.oppo.",
            "com.realme.",
            "com.oneplus.",
            "com.redteamobile.",
            "com.bbk.",
            "com.vivo.",
        )
        return internalPrefixes.none { packageName.startsWith(it) }
    }
}
