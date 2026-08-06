package com.lonnnnnng.codereader

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.Lifecycle
import com.lonnnnnng.codereader.ui.ReaderDimens
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/** @author long */
class SettingsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun displayPreferencesCanPersistAfterActivityRecreation() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithTag("settings-page-root").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-category-reading").performClick()
        selectDropdown("background-selector", "background-eye_care")

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithTag("settings-category-appearance").performClick()
        selectDropdown("palette-selector", "palette-ocean")
        selectDropdown("theme-selector", "theme-darcula")

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        // 配置变更会保留 ViewModel，重建后仍留在当前二级页，避免用户调整到一半丢失上下文。
        composeRule.onNodeWithTag("settings-page-appearance").assertIsDisplayed()

        assertDropdownValue("palette-selector", "海洋蓝")
        assertDropdownValue("theme-selector", "Darcula 暗色")

        // 结束时恢复默认显示偏好，避免持久化状态改变其他 UI 验收的视觉基线。
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithTag("settings-category-reading").performClick()
        assertDropdownValue("background-selector", "护眼绿")
        selectDropdown("background-selector", "background-follow_theme")

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithTag("settings-category-appearance").performClick()
        selectDropdown("palette-selector", "palette-emerald")
        selectDropdown("theme-selector", "theme-high_contrast_light")
    }

    @Test
    fun previewRemainsFixedAndReflectsFontSizeChanges() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithTag("settings-category-reading").performClick()
        val previewBeforeScroll = composeRule.onNodeWithTag("settings-preview").fetchSemanticsNode().boundsInRoot

        // 滚到列表深处仍应只移动设置项，预览的位置和尺寸必须保持不变，方便边调边看。
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("word-wrap-setting"))
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

    @Test
    fun wordWrapSettingExposesAndUpdatesSwitchState() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithTag("settings-category-reading").performClick()
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("word-wrap-setting"))

        val setting = composeRule.onNodeWithTag("word-wrap-setting")
        val before = setting.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        setting.performClick()
        composeRule.waitForIdle()
        val after = setting.fetchSemanticsNode().config[SemanticsProperties.ToggleableState]
        assertNotEquals("自动换行设置必须向无障碍服务暴露并更新开关状态", before, after)

        // 恢复测试前状态，避免持久化配置影响后续源码阅读用例。
        setting.performClick()
    }

    @Test
    fun onlineUpdateCanCheckPublishedGitHubRelease() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithTag("settings-category-update").performClick()
        composeRule.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("update-setting"))
        composeRule.onNodeWithTag("update-status").assertIsDisplayed()
        composeRule.onNodeWithTag("check-update-button").performClick()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText("当前已是最新版本", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("发现新版本", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "在线检查应返回最新版或可用更新",
            composeRule.onAllNodesWithText("当前已是最新版本", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("发现新版本", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun settingsCategoriesUseTwoLevelBackNavigation() {
        composeRule.onNodeWithContentDescription("设置").performClick()
        composeRule.onNodeWithTag("settings-page-root").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-category-reading").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-category-appearance").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-category-update").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-category-reading").performClick()
        composeRule.onNodeWithTag("settings-page-reading").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithTag("settings-page-root").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-category-appearance").performClick()
        composeRule.onNodeWithTag("settings-page-appearance").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-page-root").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("打开内容").assertIsDisplayed()
    }

    @Test
    fun recentProjectsMenuUsesStablePageNavigation() {
        // 首页在较大系统字号下会将“最近打开”推出首屏，先滚动到入口才能验证真实的跨设备导航。
        composeRule.onNodeWithTag("recent-projects-menu").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("recent-projects-page").assertIsDisplayed()
        // 页面切换动画会短暂保留首页同名入口，限定标题标识可避免误命中正在退出的节点。
        composeRule.onNode(hasTestTag("product-header-title") and hasText("最近打开")).assertIsDisplayed()

        // 模拟器可能保留手工验收记录；通过产品自己的移除入口归一为空状态，再验证空页和持久化导航。
        while (composeRule.onAllNodesWithContentDescription("移除最近项目").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onAllNodesWithContentDescription("移除最近项目")[0].performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("recent-projects-empty").assertIsDisplayed()
        composeRule.onNodeWithText("还没有最近项目").assertIsDisplayed()
        composeRule.onNodeWithText("打开项目").assertIsDisplayed()
        composeRule.onNodeWithText("导入 ZIP").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("打开内容").assertIsDisplayed()
        composeRule.onNodeWithTag("recent-projects-menu").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("recent-projects-empty").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("打开内容").assertIsDisplayed()
        composeRule.onNodeWithTag("recent-projects-menu").assertIsDisplayed()
    }

    @Test
    fun exitApplicationRequiresConfirmation() {
        assertCompactHeader("product-header")
        composeRule.onNodeWithText("灵阅").assertIsDisplayed()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag("exit-confirmation-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("退出灵阅？").assertIsDisplayed()
        composeRule.onNodeWithText("未保存的修改不会自动保存", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag("exit-cancel-button").performClick()
        composeRule.onNodeWithText("打开内容").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.onNodeWithTag("exit-confirmation-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("exit-confirm-button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activityRule.scenario.state == Lifecycle.State.DESTROYED
        }
        assertEquals(Lifecycle.State.DESTROYED, composeRule.activityRule.scenario.state)
    }

    private fun assertCompactHeader(tag: String) {
        val actualHeight = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
        val expectedHeight = composeRule.activity.resources.displayMetrics.density * ReaderDimens.topBarHeight.value
        assertEquals("顶部标题栏应跟随统一紧凑高度", expectedHeight, actualHeight, 1.5f)
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
