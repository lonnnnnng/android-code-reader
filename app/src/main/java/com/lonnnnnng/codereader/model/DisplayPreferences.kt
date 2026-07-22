package com.lonnnnnng.codereader.model

/**
 * 阅读字体同时作用于源码和 Markdown 正文，用户切换文件类型时不需要重复调整阅读习惯。
 *
 * @author long
 */
enum class ReaderFontFamily(
    val preferenceValue: String,
    val displayName: String,
    val description: String,
    val cssFamily: String,
) {
    SYSTEM_SANS(
        preferenceValue = "system_sans",
        displayName = "系统字体",
        description = "适合连续阅读 Markdown 正文",
        cssFamily = "system-ui, -apple-system, BlinkMacSystemFont, sans-serif",
    ),
    MONOSPACE(
        preferenceValue = "monospace",
        displayName = "等宽字体",
        description = "代码字符对齐更清晰",
        cssFamily = "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
    ),
    SERIF(
        preferenceValue = "serif",
        displayName = "衬线字体",
        description = "适合长篇技术文档",
        cssFamily = "ui-serif, Georgia, serif",
    ),
    ;

    companion object {
        fun fromPreference(value: String?): ReaderFontFamily =
            entries.firstOrNull { it.preferenceValue == value } ?: SYSTEM_SANS
    }
}

/**
 * 阅读背景为亮暗模式分别保存可读色值，切换主题时不会出现浅色背景配浅色文字的问题。
 *
 * @author long
 */
enum class ReaderBackground(
    val preferenceValue: String,
    val displayName: String,
    val description: String,
    private val lightArgb: Long,
    private val darkArgb: Long,
) {
    FOLLOW_THEME("follow_theme", "跟随主题", "使用当前明暗模式的标准背景", 0xFFFFFFFF, 0xFF242424),
    SOFT_GRAY("soft_gray", "柔和灰", "降低大面积纯白带来的眩光", 0xFFF3F5F7, 0xFF202326),
    EYE_CARE("eye_care", "护眼绿", "轻微绿色调，适合较长时间阅读", 0xFFEEF6EC, 0xFF202A22),
    PAPER("paper", "暖纸色", "偏暖的文档阅读背景", 0xFFFBF6E8, 0xFF2B2821),
    ;

    fun colorArgb(darkTheme: Boolean): Int = (if (darkTheme) darkArgb else lightArgb).toInt()

    fun cssColor(darkTheme: Boolean): String =
        "#%06X".format(colorArgb(darkTheme) and 0x00FFFFFF)

    companion object {
        fun fromPreference(value: String?): ReaderBackground =
            entries.firstOrNull { it.preferenceValue == value } ?: FOLLOW_THEME
    }
}

/** 应用配色只控制外壳与交互强调色，源码 token 继续交给 TextMate 主题保证语义一致。 @author long */
enum class AppColorPalette(
    val preferenceValue: String,
    val displayName: String,
    val description: String,
) {
    EMERALD("emerald", "翡翠绿", "沉稳、清晰的默认工具配色"),
    OCEAN("ocean", "海洋蓝", "更接近常见开发工具的蓝色强调"),
    AMBER("amber", "琥珀金", "温暖且高辨识度的操作强调"),
    ;

    companion object {
        fun fromPreference(value: String?): AppColorPalette =
            entries.firstOrNull { it.preferenceValue == value } ?: EMERALD
    }
}
