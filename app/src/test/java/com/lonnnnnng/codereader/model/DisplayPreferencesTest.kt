package com.lonnnnnng.codereader.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** @author long */
class DisplayPreferencesTest {
    @Test
    fun `未知显示偏好回退到稳定默认值`() {
        assertEquals(ReaderFontFamily.SYSTEM_SANS, ReaderFontFamily.fromPreference("removed-font"))
        assertEquals(ReaderBackground.FOLLOW_THEME, ReaderBackground.fromPreference("removed-background"))
        assertEquals(AppColorPalette.EMERALD, AppColorPalette.fromPreference("removed-palette"))
    }

    @Test
    fun `阅读背景为亮暗模式提供不同颜色`() {
        ReaderBackground.entries.forEach { background ->
            // 每个预设都必须分别定义亮暗背景，主题切换后才能继续保持足够的文字对比。
            require(background.colorArgb(darkTheme = false) != background.colorArgb(darkTheme = true))
        }
    }

    @Test
    fun `背景颜色可以转换为WebView使用的CSS色值`() {
        assertEquals("#EEF6EC", ReaderBackground.EYE_CARE.cssColor(darkTheme = false))
        assertEquals("#202A22", ReaderBackground.EYE_CARE.cssColor(darkTheme = true))
    }
}
