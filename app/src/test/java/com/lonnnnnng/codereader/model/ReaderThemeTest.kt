package com.lonnnnnng.codereader.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** @author long */
class ReaderThemeTest {
    @Test
    fun `未知持久化值回退到高对比亮色`() {
        assertEquals(ReaderTheme.HIGH_CONTRAST_LIGHT, ReaderTheme.fromPreference("removed-theme"))
    }

    @Test
    fun `亮暗主题可以双向切换`() {
        assertEquals(ReaderTheme.DARCULA, ReaderTheme.HIGH_CONTRAST_LIGHT.toggled())
        assertEquals(ReaderTheme.HIGH_CONTRAST_LIGHT, ReaderTheme.DARCULA.toggled())
    }
}
