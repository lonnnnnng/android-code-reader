package com.lonnnnnng.codereader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** @author long */
class ThemeSwitchInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun themeCanSwitchAndSurviveActivityRecreation() {
        // 测试可能继承上次手工验证的主题，先归一到亮色再验证完整持久化链路。
        if (composeRule.onAllNodesWithContentDescription("切换为亮色主题").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription("切换为亮色主题").performClick()
        }

        composeRule.onNodeWithContentDescription("切换为暗色主题").performClick()
        composeRule.onNodeWithContentDescription("切换为亮色主题").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("切换为亮色主题").assertIsDisplayed()

        // 结束时恢复默认主题，避免其他 UI 测试受到持久化状态影响。
        composeRule.onNodeWithContentDescription("切换为亮色主题").performClick()
        composeRule.onNodeWithContentDescription("切换为暗色主题").assertIsDisplayed()
    }
}
