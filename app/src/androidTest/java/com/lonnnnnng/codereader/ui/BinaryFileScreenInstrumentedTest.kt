package com.lonnnnnng.codereader.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lonnnnnng.codereader.model.AppColorPalette
import com.lonnnnnng.codereader.model.BinaryFileInfo
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.ReaderTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** 二进制识别页必须呈现来源信息并保留明确的外部打开操作。 @author long */
class BinaryFileScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun binaryFileDetailsAndExternalOpenActionAreVisible() {
        var externalOpenRequested = false
        composeRule.setContent {
            MaterialTheme(
                colorScheme = appColorScheme(ReaderTheme.HIGH_CONTRAST_LIGHT, AppColorPalette.EMERALD),
                typography = ReaderTypography,
                shapes = ReaderShapes,
            ) {
                BinaryFileScreen(
                    fileInfo = BinaryFileInfo(
                        name = "archive.bin",
                        size = 2_048,
                        mimeType = "application/octet-stream",
                        location = EntryLocation.Local(File("/data/user/0/app/files/projects/demo/archive.bin")),
                    ),
                    onBack = {},
                    onOpenExternal = { externalOpenRequested = true },
                )
            }
        }

        composeRule.onNodeWithTag("binary-file-page").assertIsDisplayed()
        composeRule.onNodeWithText("archive.bin").assertIsDisplayed()
        composeRule.onNodeWithText("application/octet-stream").assertIsDisplayed()
        composeRule.onNodeWithText("2.0 KB").assertIsDisplayed()
        composeRule.onNodeWithTag("binary-open-external").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(externalOpenRequested) }
    }

    @Test
    fun recoverableFailureShowsDetailAndRetryAction() {
        var retried = false
        composeRule.setContent {
            MaterialTheme(
                colorScheme = appColorScheme(ReaderTheme.HIGH_CONTRAST_LIGHT, AppColorPalette.EMERALD),
                typography = ReaderTypography,
                shapes = ReaderShapes,
            ) {
                ReaderFailureScreen(
                    failure = ReaderFailureState(
                        title = "文件或项目已经不存在",
                        detail = "最近项目已经不存在：demo",
                        retryAction = ReaderRetryAction.OpenRecent(
                            com.lonnnnnng.codereader.data.RecentProjectRecord("local", "demo", "/tmp/demo"),
                        ),
                    ),
                    onBack = {},
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithTag("reader-failure-page").assertIsDisplayed()
        composeRule.onNodeWithText("最近项目已经不存在：demo").assertIsDisplayed()
        composeRule.onNodeWithTag("reader-failure-retry").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }
}
