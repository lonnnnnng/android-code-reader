package com.lonnnnnng.codereader

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.RecentProjectRecord
import com.lonnnnnng.codereader.ui.AppScreen
import com.lonnnnnng.codereader.ui.ReaderCommandType
import com.lonnnnnng.codereader.ui.ReaderViewModel
import com.lonnnnnng.codereader.ui.isSafDocumentInsideTree
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 阅读位置必须经过真实 Android 偏好和 ViewModel 重建链路验证。 @author long */
@RunWith(AndroidJUnit4::class)
class ReadingStateInstrumentedTest {
    @Test
    fun readingPositionAndBookmarksSurviveProjectAndViewModelReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("reader_preferences", Application.MODE_PRIVATE)
            .edit()
            .remove("reading_states")
            .commit()
        val root = File(context.filesDir, "projects/reading-state-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val source = File(root, "src/OrderService.java").apply {
            parentFile?.mkdirs()
            writeText((1..220).joinToString("\n") { line -> "// line $line" })
        }
        val recent = RecentProjectRecord("local", root.name, root.absolutePath)
        val application = context.applicationContext as Application
        val firstViewModel = ReaderViewModel(application)

        firstViewModel.openRecentProject(recent)
        val browser = withTimeout(5_000) {
            firstViewModel.state.first { it.screen == AppScreen.BROWSER && it.projectEntries.isNotEmpty() }
        }
        val sourceEntry = requireNotNull(browser.projectEntries.firstOrNull { it.source.id == source.absolutePath }).source
        firstViewModel.openEntry(sourceEntry)
        val opened = withTimeout(5_000) {
            firstViewModel.state.first { it.screen == AppScreen.READER && it.document?.id == source.absolutePath }
        }
        firstViewModel.updateReadingPosition(requireNotNull(opened.document).id, 120)
        firstViewModel.toggleFileBookmark()
        firstViewModel.toggleLineBookmark(120)

        val firstState = firstViewModel.state.value
        assertEquals(120, firstState.currentLine)
        assertTrue(requireNotNull(firstState.activeReadingState).fileBookmarked)
        assertEquals(listOf(120), firstState.activeReadingState?.lineBookmarks)

        val restoredViewModel = ReaderViewModel(application)
        restoredViewModel.openRecentProject(recent)
        val restored = withTimeout(5_000) {
            restoredViewModel.state.first {
                it.screen == AppScreen.READER &&
                    it.document?.id == source.absolutePath &&
                    it.readerCommand?.type == ReaderCommandType.GOTO_LINE
            }
        }

        assertEquals(120, restored.currentLine)
        assertEquals(120, restored.readerCommand?.line)
        assertTrue(requireNotNull(restored.activeReadingState).fileBookmarked)
        assertEquals(listOf(120), restored.activeReadingState?.lineBookmarks)

        val fileBookmark = requireNotNull(restored.activeReadingState)
        restoredViewModel.closeTab(source.absolutePath)
        assertEquals(AppScreen.BROWSER, restoredViewModel.state.value.screen)
        assertTrue(restoredViewModel.navigateBack())
        assertEquals(AppScreen.HOME, restoredViewModel.state.value.screen)
        restoredViewModel.openReadingBookmark(fileBookmark)
        val reopenedFromBookmark = withTimeout(5_000) {
            restoredViewModel.state.first {
                it.screen == AppScreen.READER &&
                    it.document?.id == source.absolutePath &&
                    it.currentLine == 120
            }
        }
        assertEquals(120, reopenedFromBookmark.readerCommand?.line)

        restoredViewModel.removeFileBookmark(source.absolutePath)
        restoredViewModel.removeLineBookmark(source.absolutePath, 120)
        val afterRemoval = requireNotNull(restoredViewModel.state.value.activeReadingState)
        assertFalse(afterRemoval.fileBookmarked)
        assertEquals(emptyList<Int>(), afterRemoval.lineBookmarks)

        val afterRemovalViewModel = ReaderViewModel(application)
        val persistedAfterRemoval = requireNotNull(
            afterRemovalViewModel.state.value.readingStates.firstOrNull { it.documentId == source.absolutePath },
        )
        assertFalse(persistedAfterRemoval.fileBookmarked)
        assertEquals(emptyList<Int>(), persistedAfterRemoval.lineBookmarks)
    }

    @Test
    fun safTreeContainmentUsesDocumentPathBoundary() {
        val root = Uri.parse("content://provider/tree/primary%3ACode")
        val child = Uri.parse(
            "content://provider/tree/primary%3ACode/document/primary%3ACode%2Fsrc%2FMain.kt",
        )
        val siblingPrefix = Uri.parse(
            "content://provider/tree/primary%3ACodeBackup/document/primary%3ACodeBackup%2FMain.kt",
        )

        assertTrue(isSafDocumentInsideTree(child, root))
        assertFalse(isSafDocumentInsideTree(siblingPrefix, root))
        assertFalse(isSafDocumentInsideTree(child, Uri.parse("content://other/tree/primary%3ACode")))
    }
}
