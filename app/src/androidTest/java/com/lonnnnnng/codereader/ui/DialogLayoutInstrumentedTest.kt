package com.lonnnnnng.codereader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.lonnnnnng.codereader.model.AppColorPalette
import com.lonnnnnng.codereader.model.ReaderTheme
import com.lonnnnnng.codereader.update.AppRelease
import com.lonnnnnng.codereader.update.ReleaseApkAsset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 弹框组件必须复用应用设计系统，并保持说明、进度和操作的稳定阅读顺序。 @author long */
class DialogLayoutInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gitAndUpdateDialogsFollowSharedVisualHierarchy() {
        val showUpdateDialog = mutableStateOf(false)
        val release = AppRelease(
            tagName = "v0.2.0",
            versionName = "0.2.0",
            title = "灵阅 v0.2.0",
            notes = "优化阅读体验\n统一弹框布局",
            pageUrl = "https://github.com/lonnnnnng/android-code-reader/releases/tag/v0.2.0",
            apk = ReleaseApkAsset(
                name = "AndroidCodeReader-v0.2.0.apk",
                downloadUrl = "https://github.com/lonnnnnng/android-code-reader/releases/download/v0.2.0/AndroidCodeReader-v0.2.0.apk",
                sizeBytes = 11_000_000,
                sha256 = "a".repeat(64),
            ),
        )

        composeRule.setContent {
            MaterialTheme(
                colorScheme = appColorScheme(ReaderTheme.HIGH_CONTRAST_LIGHT, AppColorPalette.EMERALD),
                typography = ReaderTypography,
                shapes = ReaderShapes,
            ) {
                if (showUpdateDialog.value) {
                    AppUpdateDialog(
                        state = AppUpdateUiState(
                            phase = AppUpdatePhase.DOWNLOADING,
                            release = release,
                            progressPercent = 42,
                            dialogVisible = true,
                        ),
                        onDismiss = {},
                        onDownload = {},
                        onInstall = {},
                    )
                } else {
                    GitCloneDialog(onDismiss = {}, onClone = {})
                }
            }
        }

        composeRule.onNodeWithTag("git-clone-dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("git-clone-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("git-url-input")
            .performTextInput("https://github.com/octocat/Hello-World.git")
        composeRule.onNodeWithTag("git-url-input").assertHeightIsAtLeast(150.dp)
        composeRule.onNodeWithTag("git-clone-confirm").assertIsEnabled()

        composeRule.runOnIdle { showUpdateDialog.value = true }
        composeRule.onNodeWithTag("update-available-dialog").assertIsDisplayed()

        val notesBounds = composeRule.onNodeWithTag("update-release-notes").fetchSemanticsNode().boundsInRoot
        val progressBounds = composeRule.onNodeWithTag("update-dialog-progress").fetchSemanticsNode().boundsInRoot
        val actionBounds = composeRule.onNodeWithTag("update-downloading-button").fetchSemanticsNode().boundsInRoot
        assertTrue("下载进度应位于更新说明下方", notesBounds.bottom <= progressBounds.top)
        assertTrue("下载进度应位于操作按钮上方", progressBounds.bottom <= actionBounds.top)
    }

    @Test
    fun gitOperationOverlayShowsStageProgressAndCancelAction() {
        var cancelled = false
        composeRule.setContent {
            MaterialTheme(
                colorScheme = appColorScheme(ReaderTheme.DARCULA, AppColorPalette.EMERALD),
                typography = ReaderTypography,
                shapes = ReaderShapes,
            ) {
                ReaderOperationOverlay(
                    operation = ReaderOperationState(
                        title = "正在克隆 Git 仓库",
                        detail = "正在接收对象",
                        progressPercent = 42,
                        cancellable = true,
                    ),
                    onCancel = { cancelled = true },
                )
            }
        }

        composeRule.onNodeWithTag("operation-overlay").assertIsDisplayed()
        composeRule.onNodeWithText("正在克隆 Git 仓库").assertIsDisplayed()
        composeRule.onNodeWithText("正在接收对象").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
        composeRule.onNodeWithTag("operation-progress").assertIsDisplayed()
        composeRule.onNodeWithTag("operation-cancel").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue("取消操作必须回传到 ViewModel", cancelled) }
    }

    @Test
    fun clonedProjectHeaderExposesUpdateAction() {
        var updateRequested = false
        composeRule.setContent {
            MaterialTheme(
                colorScheme = appColorScheme(ReaderTheme.HIGH_CONTRAST_LIGHT, AppColorPalette.EMERALD),
                typography = ReaderTypography,
                shapes = ReaderShapes,
            ) {
                BrowserScreen(
                    state = ReaderUiState(
                        screen = AppScreen.BROWSER,
                        browserTitle = "Hello-World",
                        gitRepositoryRoot = "/data/user/0/app/files/projects/Hello-World",
                    ),
                    onBack = {},
                    onEntry = {},
                    onSearch = {},
                    onSearchResult = {},
                    onUpdateGit = { updateRequested = true },
                    onToggleTheme = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("获取最新代码").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue("Git 项目标题栏必须触发更新动作", updateRequested) }
    }
}
