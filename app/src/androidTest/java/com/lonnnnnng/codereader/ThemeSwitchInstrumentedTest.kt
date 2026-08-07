package com.lonnnnnng.codereader

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
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

    @Test
    fun gitCloneDialogUsesActiveDarkTheme() {
        if (composeRule.onAllNodesWithContentDescription("切换为亮色主题").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription("切换为亮色主题").performClick()
        }
        composeRule.onNodeWithContentDescription("切换为暗色主题").performClick()
        composeRule.onNodeWithText("Git").performClick()
        composeRule.onNodeWithTag("git-clone-dialog").assertIsDisplayed()

        // 抽样弹窗顶部空白表面，防止弹窗漂出主题树后在暗色页面回退成默认亮色。 @author long
        val pixels = composeRule.onNodeWithTag("git-clone-dialog").captureToImage().toPixelMap()
        val surfaceColor = pixels[pixels.width / 2, 8]
        assertTrue("Git 弹窗必须继承暗色主题", surfaceColor.luminance() < 0.35f)

        composeRule.onNodeWithTag("git-clone-cancel").performClick()
        composeRule.onNodeWithContentDescription("切换为亮色主题").performClick()
    }
}
