package com.lonnnnnng.codereader.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 编辑偏好持久化需要兼容旧版本和异常值，不能把非法 Tab 宽度传入 Sora。 @author long */
class EditorPreferencesTest {
    @Test
    fun tabWidthOnlyAcceptsProductSupportedValues() {
        assertEquals(2, normalizeEditorTabWidth(2))
        assertEquals(4, normalizeEditorTabWidth(4))
        assertEquals(8, normalizeEditorTabWidth(8))
        assertEquals(4, normalizeEditorTabWidth(0))
        assertEquals(4, normalizeEditorTabWidth(3))
        assertEquals(4, normalizeEditorTabWidth(16))
    }

    @Test
    fun indentStyleFallsBackToSpacesForUnknownPreferences() {
        assertEquals(EditorIndentStyle.SPACES, EditorIndentStyle.fromPreference(null))
        assertEquals(EditorIndentStyle.SPACES, EditorIndentStyle.fromPreference("legacy"))
        assertEquals(EditorIndentStyle.TABS, EditorIndentStyle.fromPreference("tabs"))
    }
}
