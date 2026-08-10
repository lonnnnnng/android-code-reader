package com.lonnnnnng.codereader

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.DraftStore
import com.lonnnnnng.codereader.data.RecentProjectRecord
import com.lonnnnnng.codereader.ui.AppScreen
import com.lonnnnnng.codereader.ui.ReaderViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 草稿恢复必须覆盖真实 ViewModel 重建，而不是只验证内存标签切换。 @author long */
@RunWith(AndroidJUnit4::class)
class DraftRecoveryInstrumentedTest {
    @Test
    fun unsavedDraftReturnsAfterViewModelRecreationAndProjectReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DraftStore.defaultDirectory(context).deleteRecursively()
        context.getSharedPreferences("reader_preferences", Application.MODE_PRIVATE)
            .edit()
            .remove("reading_states")
            .commit()
        val root = File(context.filesDir, "projects/draft-recovery-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val source = File(root, "src/OrderService.kt").apply {
            parentFile?.mkdirs()
            writeText("fun status() = \"saved\"\n")
        }
        val draftText = "fun status() = \"draft\"\n// 尚未保存\n"
        val recent = RecentProjectRecord("local", root.name, root.absolutePath)
        val application = context.applicationContext as Application
        val firstViewModel = ReaderViewModel(application)

        firstViewModel.openRecentProject(recent)
        val browser = withTimeout(5_000) {
            firstViewModel.state.first { it.screen == AppScreen.BROWSER && it.projectEntries.isNotEmpty() }
        }
        firstViewModel.openEntry(requireNotNull(browser.projectEntries.firstOrNull { it.source.id == source.absolutePath }).source)
        withTimeout(5_000) {
            firstViewModel.state.first { it.document?.id == source.absolutePath }
        }
        firstViewModel.setEditable(true)
        firstViewModel.updateDraft(draftText)

        val store = DraftStore(DraftStore.defaultDirectory(context))
        withTimeout(5_000) {
            while (store.load(source.absolutePath)?.draftText != draftText) delay(50)
        }

        val restoredViewModel = ReaderViewModel(application)
        restoredViewModel.openRecentProject(recent)
        val restored = withTimeout(5_000) {
            restoredViewModel.state.first {
                it.screen == AppScreen.READER &&
                    it.document?.id == source.absolutePath &&
                    it.dirty
            }
        }

        assertEquals(draftText, restored.draftText)
        assertTrue(restored.editable)

        restoredViewModel.save()
        withTimeout(5_000) {
            restoredViewModel.state.first { it.document?.id == source.absolutePath && !it.dirty }
        }
        withTimeout(5_000) {
            while (store.load(source.absolutePath) != null) delay(50)
        }
        assertEquals(draftText, source.readText())
    }

    @Test
    fun externallyChangedFileRequiresExplicitChoiceBeforeDraftRecovery() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DraftStore.defaultDirectory(context).deleteRecursively()
        context.getSharedPreferences("reader_preferences", Application.MODE_PRIVATE)
            .edit()
            .remove("reading_states")
            .commit()
        val root = File(context.filesDir, "projects/draft-conflict-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val originalText = "server.port=8080\n"
        val externalText = "server.port=9090\n"
        val draftText = "server.port=8081\n"
        val source = File(root, "application.properties").apply { writeText(originalText) }
        val recent = RecentProjectRecord("local", root.name, root.absolutePath)
        val application = context.applicationContext as Application
        val firstViewModel = ReaderViewModel(application)

        firstViewModel.openRecentProject(recent)
        val browser = withTimeout(5_000) {
            firstViewModel.state.first { it.screen == AppScreen.BROWSER && it.projectEntries.isNotEmpty() }
        }
        firstViewModel.openEntry(requireNotNull(browser.projectEntries.firstOrNull { it.source.id == source.absolutePath }).source)
        withTimeout(5_000) { firstViewModel.state.first { it.document?.id == source.absolutePath } }
        firstViewModel.setEditable(true)
        firstViewModel.updateDraft(draftText)
        val store = DraftStore(DraftStore.defaultDirectory(context))
        withTimeout(5_000) {
            while (store.load(source.absolutePath)?.draftText != draftText) delay(50)
        }

        source.writeText(externalText)
        val restoredViewModel = ReaderViewModel(application)
        restoredViewModel.openRecentProject(recent)
        val conflicted = withTimeout(5_000) {
            restoredViewModel.state.first {
                it.screen == AppScreen.READER &&
                    it.document?.id == source.absolutePath &&
                    it.draftConflict?.documentId == source.absolutePath
            }
        }

        assertEquals(externalText, conflicted.draftText)
        assertTrue(!conflicted.dirty)

        restoredViewModel.restoreConflictingDraft()
        val recovered = restoredViewModel.state.value
        assertEquals(draftText, recovered.draftText)
        assertTrue(recovered.dirty)
        assertEquals(null, recovered.draftConflict)
    }
}
