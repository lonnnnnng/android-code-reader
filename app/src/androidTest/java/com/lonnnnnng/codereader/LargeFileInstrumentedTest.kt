package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.DocumentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/** @author long */
@RunWith(AndroidJUnit4::class)
class LargeFileInstrumentedTest {
    @Test
    fun largeFileIsOpenedReadOnlyAndCanLoadNextPage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "large-source.log")
        file.bufferedWriter().use { writer ->
            repeat(90_000) { index -> writer.appendLine("$index service.findUser()") }
        }

        val repository = DocumentRepository(context)
        val document = repository.openLocal(file)
        val next = repository.loadMore(document)

        assertTrue("大文件没有进入分段模式", document.largeFile)
        assertFalse("分段模式不应允许覆盖保存", document.canWrite)
        assertTrue("首段内容为空", document.text.isNotEmpty())
        assertTrue("第二段内容为空", next.text.isNotEmpty())
        assertTrue("第二段游标没有前进", next.nextCharacter > document.loadedCharacters)

        val utf16File = File(context.cacheDir, "large-source-utf16.log")
        utf16File.outputStream().use { output ->
            output.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
            OutputStreamWriter(output, StandardCharsets.UTF_16LE).use { writer ->
                repeat(45_000) { index -> writer.appendLine("$index 用户服务.findUser()") }
            }
        }
        val utf16Document = repository.openLocal(utf16File)
        assertTrue("UTF-16 大文件没有进入分段模式", utf16Document.largeFile)
        assertTrue("UTF-16 大文件没有按 BOM 解码", utf16Document.text.contains("用户服务.findUser()"))
    }
}
