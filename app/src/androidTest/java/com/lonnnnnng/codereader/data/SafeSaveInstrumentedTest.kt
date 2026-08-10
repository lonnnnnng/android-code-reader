package com.lonnnnnng.codereader.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.model.OpenDocument
import com.lonnnnnng.codereader.model.TextEncoding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** 保存失败必须验证真实文件内容，而不是只检查 ViewModel 的错误提示。 @author long */
@RunWith(AndroidJUnit4::class)
class SafeSaveInstrumentedTest {
    @Test
    fun localSaveFailureLeavesOriginalFileUntouched() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "safe-save-local-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val originalText = "server.port=8080\n"
        val source = File(directory, "application.properties").apply { writeText(originalText) }
        val document = OpenDocument(
            name = source.name,
            text = originalText,
            fileType = FileType.detect(source.name),
            canWrite = true,
            location = EntryLocation.Local(source),
        )
        source.setWritable(true, false)
        directory.setWritable(false, false)

        val error = try {
            runCatching { DocumentRepository(context).save(document, "server.port=9090\n") }.exceptionOrNull()
        } finally {
            directory.setWritable(true, false)
        }

        assertNotNull(error)
        assertEquals(originalText, source.readText())
    }

    @Test
    fun safPartialWriteFailureRollsBackOriginalBytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "原始内容\n".toByteArray()
        val access = FailingSafDocumentAccess(originalBytes, failedOutputCount = 1)
        val uri = android.net.Uri.parse("content://test.provider/document/source.md")
        val document = OpenDocument(
            name = "source.md",
            text = "原始内容\n",
            fileType = FileType.detect("source.md"),
            canWrite = true,
            location = EntryLocation.Saf(uri),
            encoding = TextEncoding.UTF_8_BOM,
        )
        val repository = DocumentRepository(context, safDocumentAccess = access)

        val error = runCatching { repository.save(document, "新的内容会在中途失败\n") }.exceptionOrNull()

        assertTrue(error is DocumentSaveException)
        assertFalse((error as DocumentSaveException).originalMayBeAffected)
        assertEquals(originalBytes.toList(), access.content.toList())
        assertEquals(2, access.outputOpenCount)
    }

    @Test
    fun safRollbackFailureReportsThatOriginalMayBeAffected() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalBytes = "original-content".toByteArray()
        val access = FailingSafDocumentAccess(originalBytes, failedOutputCount = 2)
        val document = OpenDocument(
            name = "config.json",
            text = "original-content",
            fileType = FileType.detect("config.json"),
            canWrite = true,
            location = EntryLocation.Saf(android.net.Uri.parse("content://test.provider/document/config.json")),
        )

        val error = runCatching {
            DocumentRepository(context, safDocumentAccess = access).save(document, "replacement-content")
        }.exceptionOrNull()

        assertTrue(error is DocumentSaveException)
        assertTrue((error as DocumentSaveException).originalMayBeAffected)
        assertEquals(2, access.outputOpenCount)
        assertNotEquals(originalBytes.toList(), access.content.toList())
    }

    /** 只模拟 SAF 系统边界：第一次输出部分写入后失败，第二次输出允许 Repository 完成回滚。 @author long */
    private class FailingSafDocumentAccess(
        initialContent: ByteArray,
        private val failedOutputCount: Int,
    ) : SafDocumentAccess {
        var content: ByteArray = initialContent.copyOf()
        var outputOpenCount: Int = 0

        override fun openInput(uri: android.net.Uri): InputStream = ByteArrayInputStream(content)

        override fun openOutput(uri: android.net.Uri, mode: String): OutputStream {
            outputOpenCount++
            val shouldFail = outputOpenCount <= failedOutputCount
            val buffer = ByteArrayOutputStream()
            content = byteArrayOf()
            return object : OutputStream() {
                override fun write(value: Int) {
                    buffer.write(value)
                    content = buffer.toByteArray()
                    if (shouldFail && buffer.size() >= 6) throw IOException("模拟 SAF 写入中断")
                }
            }
        }
    }
}
