package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.DocumentRepository
import com.lonnnnnng.codereader.data.RecentProjectRecord
import com.lonnnnnng.codereader.model.BinaryFileException
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.SourceEntry
import com.lonnnnnng.codereader.model.TextEncoding
import com.lonnnnnng.codereader.ui.AppScreen
import com.lonnnnnng.codereader.ui.ReaderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.Charset

/** 编码识别必须经过真实 Android 文件读取和保存链路，避免 JVM 与设备 Charset 行为不一致。 @author long */
@RunWith(AndroidJUnit4::class)
class TextEncodingInstrumentedTest {

    @Test
    fun gb18030SourceCanBeOpenedEditedAndSavedWithoutChangingEncoding() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = "public class 用户服务 { // 查询订单\n}"
        val updated = "$source\n// 已修改"
        val charset = Charset.forName("GB18030")
        val file = File(context.cacheDir, "gb18030-source.java").apply {
            writeBytes(source.toByteArray(charset))
        }
        val repository = DocumentRepository(context)

        val document = repository.openLocal(file)
        repository.save(document, updated)

        assertEquals(TextEncoding.GB18030, document.encoding)
        assertEquals(source, document.text)
        assertArrayEquals(updated.toByteArray(charset), file.readBytes())
    }

    @Test
    fun manualEncodingSelectionReopensTheOriginalFile() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = "// café déjà vu"
        val file = File(context.cacheDir, "latin1-source.properties").apply {
            writeBytes(source.toByteArray(Charsets.ISO_8859_1))
        }
        val repository = DocumentRepository(context)
        val document = repository.openLocal(file)

        val reopened = repository.reopen(document, TextEncoding.LATIN_1)

        assertEquals(TextEncoding.LATIN_1, reopened.encoding)
        assertEquals(source, reopened.text)
    }

    @Test
    fun binaryFileOpensRecognitionPageAndReturnsToPreviousScreen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "projects/binary-test/archive.bin").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x01, 0x02, 0x00, 0x7F))
        }
        val repository = DocumentRepository(context)

        val error = runCatching { repository.openLocal(file) }.exceptionOrNull()
        require(error is BinaryFileException)
        assertEquals(file.name, error.fileInfo.name)
        assertEquals(file.length(), error.fileInfo.size)
        assertEquals("application/octet-stream", error.fileInfo.mimeType)

        val viewModel = ReaderViewModel(context.applicationContext as android.app.Application)
        viewModel.openEntry(
            SourceEntry(
                name = file.name,
                isDirectory = false,
                size = file.length(),
                canWrite = true,
                location = EntryLocation.Local(file),
            ),
        )
        val binaryState = withTimeout(5_000) {
            viewModel.state.first { it.screen == AppScreen.BINARY && it.binaryFile != null }
        }

        assertEquals(file.name, binaryState.binaryFile?.name)
        assertEquals(AppScreen.HOME, binaryState.binaryBackTarget)
        assertEquals(true, viewModel.navigateBack())
        assertEquals(AppScreen.HOME, viewModel.state.value.screen)
    }

    @Test
    fun missingRecentProjectCanRetryAfterTheDirectoryReturns() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "projects/retry-project").apply { deleteRecursively() }
        val project = RecentProjectRecord("local", "retry-project", directory.absolutePath)
        val viewModel = ReaderViewModel(context.applicationContext as android.app.Application)

        viewModel.openRecentProject(project)
        val failureState = withTimeout(5_000) {
            viewModel.state.first { it.screen == AppScreen.ERROR && it.failure != null }
        }
        assertEquals("文件或项目已经不存在", failureState.failure?.title)
        assertEquals(AppScreen.HOME, failureState.errorBackTarget)

        directory.mkdirs()
        File(directory, "README.md").writeText("# Retry")
        viewModel.retryLastFailure()
        val recoveredState = withTimeout(5_000) {
            viewModel.state.first { it.screen == AppScreen.BROWSER && it.browserTitle == "retry-project" }
        }

        assertEquals(null, recoveredState.failure)
        assertEquals(1, recoveredState.projectEntries.count { !it.source.isDirectory })
    }
}
