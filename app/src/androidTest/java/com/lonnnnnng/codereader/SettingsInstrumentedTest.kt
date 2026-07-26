package com.lonnnnnng.codereader

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** @author long */
class SettingsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun displayPreferencesCanPersistAfterActivityRecreation() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("设置").assertIsDisplayed()

        selectDropdown("background-selector", "background-eye_care")
        selectDropdown("palette-selector", "palette-ocean")
        selectDropdown("theme-selector", "theme-darcula")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        // 配置变更会保留 ViewModel，设置页本身也应保持打开，避免用户调整到一半被送回首页。
        composeRule.onNodeWithText("设置").assertIsDisplayed()

        assertDropdownValue("background-selector", "护眼绿")
        assertDropdownValue("palette-selector", "海洋蓝")
        assertDropdownValue("theme-selector", "Darcula 暗色")

        // 结束时恢复默认显示偏好，避免持久化状态改变其他 UI 验收的视觉基线。
        selectDropdown("background-selector", "background-follow_theme")
        selectDropdown("palette-selector", "palette-emerald")
        selectDropdown("theme-selector", "theme-high_contrast_light")
    }

    @Test
    fun previewRemainsFixedAndReflectsFontSizeChanges() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        val previewBeforeScroll = composeRule.onNodeWithTag("settings-preview").fetchSemanticsNode().boundsInRoot

        // 滚到列表深处仍应只移动设置项，预览的位置和尺寸必须保持不变，方便边调边看。
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("theme-selector"))
        composeRule.waitForIdle()
        val previewAfterScroll = composeRule.onNodeWithTag("settings-preview").fetchSemanticsNode().boundsInRoot
        assertEquals(previewBeforeScroll, previewAfterScroll)

        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("font-size-slider"))
        // 测试可能运行在保留用户偏好的安装包上，先归一到 14sp，避免旧值恰好为 19sp 导致误判。
        composeRule.onNodeWithTag("font-size-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(14f) }
        composeRule.waitForIdle()
        val sampleBefore = composeRule.onNodeWithText("@Serializable data class User(").fetchSemanticsNode().boundsInRoot.width
        composeRule.onNodeWithTag("font-size-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(19f) }
        composeRule.waitForIdle()
        val sampleAfter = composeRule.onNodeWithText("@Serializable data class User(").fetchSemanticsNode().boundsInRoot.width
        assertTrue("实时预览应立即反映新的正文字号", sampleAfter > sampleBefore)

        // 恢复默认字号，避免持久化配置改变其他 UI 测试的视觉基线。
        composeRule.onNodeWithTag("font-size-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(14f) }
    }

    private fun selectDropdown(selectorTag: String, optionTag: String) {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(selectorTag))
        composeRule.onNodeWithTag(selectorTag).performClick()
        composeRule.onNodeWithTag(optionTag).performClick()
    }

    private fun assertDropdownValue(selectorTag: String, value: String) {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(selectorTag))
        composeRule.onNodeWithTag(selectorTag).assertTextContains(value)
    }
}
