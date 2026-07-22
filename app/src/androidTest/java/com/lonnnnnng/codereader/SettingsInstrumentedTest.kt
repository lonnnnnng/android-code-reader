package com.lonnnnnng.codereader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test

/** @author long */
class SettingsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun displayPreferencesCanPersistAfterActivityRecreation() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithText("设置").assertIsDisplayed()

        selectSetting("font-monospace")
        selectSetting("background-eye_care")
        selectSetting("palette-ocean")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        // 配置变更会保留 ViewModel，设置页本身也应保持打开，避免用户调整到一半被送回首页。
        composeRule.onNodeWithText("设置").assertIsDisplayed()

        assertSettingSelected("font-monospace")
        assertSettingSelected("background-eye_care")
        assertSettingSelected("palette-ocean")

        // 结束时恢复默认显示偏好，避免持久化状态改变其他 UI 验收的视觉基线。
        selectSetting("font-system_sans")
        selectSetting("background-follow_theme")
        selectSetting("palette-emerald")
    }

    private fun selectSetting(tag: String) {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performClick()
    }

    private fun assertSettingSelected(tag: String) {
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).assertIsSelected()
    }
}
