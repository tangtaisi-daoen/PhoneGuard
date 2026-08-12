package com.familyguard.core.categories

/**
 * app 类别。管理端可手动调整单个 app 的类别，未覆盖的归 OTHER。
 */
enum class AppCategory {
    GAME,          // 游戏
    SHORT_VIDEO,   // 短视频
    VIDEO,         // 长视频平台
    SOCIAL,        // 社交
    TOOL,          // 工具/系统
    OTHER          // 未分类
}
