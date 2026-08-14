package com.familyguard.admin

import com.familyguard.core.categories.AppCategory
import com.familyguard.core.data.AppLimit

/** 规则页单行的可测试状态；UI 输入必须先回写这里，保存时再转换为规则。 */
data class AppLimitRowData(
    var packageName: String = "",
    var appName: String = "",
    var minutes: Int = 0,
    var category: AppCategory = AppCategory.OTHER,
) {
    fun updateMinutes(input: CharSequence?) {
        minutes = input?.toString()?.trim()?.toIntOrNull() ?: 0
    }

    fun toAppLimitOrNull(): AppLimit? {
        if (!PACKAGE_NAME_REGEX.matches(packageName) || minutes <= 0) return null
        return AppLimit(packageName, category, dailyMinutes = minutes)
    }

    companion object {
        private val categories = listOf(
            AppCategory.OTHER,
            AppCategory.GAME,
            AppCategory.SHORT_VIDEO,
            AppCategory.VIDEO,
            AppCategory.SOCIAL,
            AppCategory.TOOL,
        )

        fun categoryAt(position: Int): AppCategory = categories.getOrElse(position) { AppCategory.OTHER }

        fun positionOf(category: AppCategory): Int = categories.indexOf(category).coerceAtLeast(0)

        fun isValidPackageName(value: String): Boolean = PACKAGE_NAME_REGEX.matches(value)

        private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
    }
}

data class NewLimitRowSelection(
    val row: AppLimitRowData,
    val position: Int,
    val inserted: Boolean,
)

/** 添加限制时优先复用尚未选择应用的空行，避免重复生成看不见的空卡片。 */
fun chooseRowForNewLimit(rows: MutableList<AppLimitRowData>): NewLimitRowSelection {
    val existingPosition = rows.indexOfLast { it.packageName.isBlank() }
    if (existingPosition >= 0) {
        return NewLimitRowSelection(rows[existingPosition], existingPosition, inserted = false)
    }
    val row = AppLimitRowData()
    rows += row
    return NewLimitRowSelection(row, rows.lastIndex, inserted = true)
}
