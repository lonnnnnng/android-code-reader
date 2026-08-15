package com.lonnnnnng.codereader.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.model.EntryLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 新建文件必须落在项目根目录内，并以空正文打开，避免创建动作只更新 UI 而没有真实文件。 @author long */
@RunWith(AndroidJUnit4::class)
class DocumentCreationInstrumentedTest {
    @Test
    fun createsEmptyLocalTextFileAndCanReopenIt() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val root = File(context.cacheDir, "document-creation-test").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                val document = DocumentRepository(context).createTextFile(
                    EntryLocation.Local(root),
                    "README.md",
                )

                assertTrue(File(root, "README.md").isFile)
                assertEquals("README.md", document.name)
                assertEquals("", document.text)
                assertTrue(document.canWrite)
            } finally {
                root.deleteRecursively()
            }
        }
    }

    @Test
    fun rejectsPathTraversalAndDuplicateLocalFile() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val root = File(context.cacheDir, "document-creation-validation-test").apply {
                deleteRecursively()
                mkdirs()
            }
            try {
                val repository = DocumentRepository(context)
                runCatching { repository.createTextFile(EntryLocation.Local(root), "../escape.md") }
                    .onSuccess { error("路径穿越文件名不应创建成功") }
                repository.createTextFile(EntryLocation.Local(root), "config.yml")
                runCatching { repository.createTextFile(EntryLocation.Local(root), "config.yml") }
                    .onSuccess { error("重复文件名不应创建成功") }
            } finally {
                root.deleteRecursively()
            }
        }
    }
}
