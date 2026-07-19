package com.lonnnnnng.codereader.model

/**
 * 阅读主题同时约束 Compose 界面和 TextMate 配色，避免应用外壳与代码区出现亮暗割裂。
 *
 * @author long
 */
enum class ReaderTheme(
    val preferenceValue: String,
    val textMateName: String,
    val isDark: Boolean,
) {
    HIGH_CONTRAST_LIGHT(
        preferenceValue = "high_contrast_light",
        textMateName = "high_contrast_light",
        isDark = false,
    ),
    DARCULA(
        preferenceValue = "darcula",
        textMateName = "darcula",
        isDark = true,
    ),
    ;

    fun toggled(): ReaderTheme = if (isDark) HIGH_CONTRAST_LIGHT else DARCULA

    companion object {
        fun fromPreference(value: String?): ReaderTheme =
            entries.firstOrNull { it.preferenceValue == value } ?: HIGH_CONTRAST_LIGHT
    }
}
