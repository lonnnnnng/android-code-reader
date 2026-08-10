package com.lonnnnnng.codereader.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.model.OpenDocument
import com.lonnnnnng.codereader.model.TextEncoding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** 导出副本必须验证完整原始字节，避免把大文件当前已加载分页误当成整份文件。 @author long */
@RunWith(AndroidJUnit4::class)
class DocumentExportInstrumentedTest {
    @Test
    fun largeFileExportCopiesCompleteOriginalBytesAndKeepsBom() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "export-large-utf16.log")
        val sourceText = buildString {
            repeat(90_000) { index -> append(index).append(" 用户服务.findUser()\r\n") }
        }
        val originalBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            sourceText.toByteArray(StandardCharsets.UTF_16LE)
        source.writeBytes(originalBytes)
        val targetUri = Uri.parse("content://test.provider/document/export-large-utf16.log")
        val access = RecordingSafDocumentAccess(targetUri)
        val repository = DocumentRepository(context, safDocumentAccess = access)
        var finalCopiedBytes = 0L

        try {
            val document = repository.openLocal(source)
            assertTrue("测试文件应进入大文件分段模式", document.largeFile)
            assertFalse("首个分页不应等于完整原文件", document.text == sourceText)

            val copiedBytes = repository.exportOriginal(document, targetUri) { progress ->
                finalCopiedBytes = progress.copiedBytes
            }

            assertEquals(originalBytes.size.toLong(), copiedBytes)
            assertEquals(originalBytes.size.toLong(), finalCopiedBytes)
            assertArrayEquals(originalBytes, access.output.toByteArray())
        } finally {
            repository.close()
            source.delete()
        }
    }

    @Test
    fun readOnlySafExportKeepsOriginalEncodingAndLineEndings() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceUri = Uri.parse("content://test.provider/document/read-only-config")
        val targetUri = Uri.parse("content://test.provider/document/read-only-config-copy")
        val originalBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "server.port=8080\r\nfeature.enabled=true\r\n".toByteArray(StandardCharsets.UTF_8)
        val access = RecordingSafDocumentAccess(targetUri, sourceUri, originalBytes)
        val repository = DocumentRepository(context, safDocumentAccess = access)
        val document = OpenDocument(
            name = "application.properties",
            text = "server.port=8080\nfeature.enabled=true\n",
            fileType = FileType.PROPERTIES,
            canWrite = false,
            location = EntryLocation.Saf(sourceUri),
            encoding = TextEncoding.UTF_8_BOM,
            totalBytes = originalBytes.size.toLong(),
        )

        val copiedBytes = repository.exportOriginal(document, targetUri)

        assertEquals(originalBytes.size.toLong(), copiedBytes)
        assertArrayEquals(originalBytes, access.output.toByteArray())
    }

    @Test
    fun failedExportCanDeleteTheNewIncompleteTarget() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceUri = Uri.parse("content://test.provider/document/source.json")
        val targetUri = Uri.parse("content://test.provider/document/source-copy.json")
        val sourceBytes = "{\"enabled\":true,\"port\":8080}".toByteArray(StandardCharsets.UTF_8)
        val access = FailingExportSafDocumentAccess(sourceUri, targetUri, sourceBytes)
        val repository = DocumentRepository(context, safDocumentAccess = access)
        val document = OpenDocument(
            name = "source.json",
            text = sourceBytes.toString(StandardCharsets.UTF_8),
            fileType = FileType.JSON,
            canWrite = false,
            location = EntryLocation.Saf(sourceUri),
            totalBytes = sourceBytes.size.toLong(),
        )

        val error = runCatching { repository.exportOriginal(document, targetUri) }.exceptionOrNull()

        assertNotNull("模拟写入中断应让导出失败", error)
        assertFalse("Repository 不应在调用方确认前静默删除目标", access.deleted)
        assertTrue(repository.deleteCreatedDocument(targetUri))
        assertTrue("失败后应删除 ACTION_CREATE_DOCUMENT 新建的半成品", access.deleted)
    }

    @Test
    fun exportCanBeCancelledBeforeTheWholeSourceIsCopied() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceUri = Uri.parse("content://test.provider/document/slow-source.log")
        val targetUri = Uri.parse("content://test.provider/document/slow-source-copy.log")
        val sourceBytes = ByteArray(2 * 1024 * 1024) { index -> (index % 251).toByte() }
        val access = SlowExportSafDocumentAccess(sourceUri, targetUri, sourceBytes)
        val repository = DocumentRepository(context, safDocumentAccess = access)
        val document = OpenDocument(
            name = "slow-source.log",
            text = "",
            fileType = FileType.PLAIN_TEXT,
            canWrite = false,
            location = EntryLocation.Saf(sourceUri),
            totalBytes = sourceBytes.size.toLong(),
            largeFile = true,
        )
        val progressStarted = CompletableDeferred<Unit>()
        var cancellation: Throwable? = null

        val exportJob = launch {
            try {
                repository.exportOriginal(document, targetUri) { progress ->
                    if (progress.copiedBytes > 0L) progressStarted.complete(Unit)
                }
            } catch (error: Throwable) {
                cancellation = error
                throw error
            }
        }
        progressStarted.await()
        exportJob.cancelAndJoin()

        assertTrue("取消应传播为协程取消，而不是普通成功", cancellation is CancellationException)
        assertTrue("取消后不应继续复制到文件末尾", access.output.size() < sourceBytes.size)
    }

    private class RecordingSafDocumentAccess(
        private val targetUri: Uri,
        private val sourceUri: Uri? = null,
        private val sourceBytes: ByteArray? = null,
    ) : SafDocumentAccess {
        val output = ByteArrayOutputStream()

        override fun openInput(uri: Uri): InputStream? = sourceBytes
            ?.takeIf { uri == sourceUri }
            ?.let(::ByteArrayInputStream)

        override fun openOutput(uri: Uri, mode: String): OutputStream? = output.takeIf { uri == targetUri }
    }

    private class FailingExportSafDocumentAccess(
        private val sourceUri: Uri,
        private val targetUri: Uri,
        private val sourceBytes: ByteArray,
    ) : SafDocumentAccess {
        var deleted: Boolean = false

        override fun openInput(uri: Uri): InputStream? = sourceBytes
            .takeIf { uri == sourceUri }
            ?.let(::ByteArrayInputStream)

        override fun openOutput(uri: Uri, mode: String): OutputStream? = if (uri == targetUri) {
            object : OutputStream() {
                private var written = 0

                override fun write(value: Int) {
                    written++
                    if (written >= 8) throw IOException("模拟导出写入中断")
                }
            }
        } else {
            null
        }

        override fun delete(uri: Uri): Boolean {
            deleted = uri == targetUri
            return deleted
        }
    }

    private class SlowExportSafDocumentAccess(
        private val sourceUri: Uri,
        private val targetUri: Uri,
        private val sourceBytes: ByteArray,
    ) : SafDocumentAccess {
        val output = ByteArrayOutputStream()

        override fun openInput(uri: Uri): InputStream? = if (uri == sourceUri) {
            object : ByteArrayInputStream(sourceBytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    Thread.sleep(2)
                    return super.read(buffer, offset, minOf(length, 1024))
                }
            }
        } else {
            null
        }

        override fun openOutput(uri: Uri, mode: String): OutputStream? = output.takeIf { uri == targetUri }
    }
}
