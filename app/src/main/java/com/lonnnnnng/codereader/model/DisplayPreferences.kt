package com.lonnnnnng.codereader.model

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
