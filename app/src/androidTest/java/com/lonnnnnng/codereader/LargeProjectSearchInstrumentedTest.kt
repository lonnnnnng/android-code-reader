package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.DocumentRepository
import com.lonnnnnng.codereader.domain.ProjectSearchOptions
import com.lonnnnnng.codereader.domain.ProjectSearchScope
import com.lonnnnnng.codereader.domain.TextSearchOptions
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.FileType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 验证超过旧搜索上限的源码仍能扫描到文件后半段命中。 @author long */
@RunWith(AndroidJUnit4::class)
class LargeProjectSearchInstrumentedTest {
    @Test
    fun projectSearchFindsMatchAfterTwoMegabytes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "project-search-large").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val targetLine = 120_001
            File(root, "Large.kt").bufferedWriter().use { writer ->
                repeat(targetLine) { index -> writer.appendLine("val line$index = \"ordinary source\"") }
                writer.appendLine("fun targetAfterLargePage() = \"needle-after-two-megabytes\"")
            }

            val repository = DocumentRepository(context)
            val entries = repository.indexProject(EntryLocation.Local(root))
            val results = repository.searchProject(entries, "needle-after-two-megabytes")

            assertTrue("测试源码应超过 2 MB", File(root, "Large.kt").length() > 2 * 1024 * 1024)
            assertEquals(1, results.size)
            assertEquals(targetLine + 1, results.single().line)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun projectSearchAppliesTextTypeDirectoryAndPathFilters() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "project-search-options").apply {
            deleteRecursively()
            resolve("src/main/service").mkdirs()
            resolve("src/test/service").mkdirs()
        }
        try {
            File(root, "src/main/service/UserService.kt").writeText("val User = user + user")
            File(root, "src/test/service/UserService.kt").writeText("val user = user")
            File(root, "src/main/service/UserService.java").writeText("String user = user;")

            val repository = DocumentRepository(context)
            val entries = repository.indexProject(EntryLocation.Local(root))
            val page = repository.searchProject(
                entries = entries,
                query = "user",
                options = ProjectSearchOptions(
                    text = TextSearchOptions(caseSensitive = true, wholeWord = true),
                    fileType = FileType.KOTLIN,
                    scope = ProjectSearchScope.CURRENT_DIRECTORY,
                    directoryPath = "src/main",
                    pathFilter = "service",
                ),
            )

            assertEquals(1, page.results.size)
            assertEquals("src/main/service/UserService.kt", page.results.single().path)
            assertEquals(2, page.totalMatches)
            assertEquals(1, page.matchedFiles)
            assertTrue(!page.truncated)
        } finally {
            root.deleteRecursively()
        }
    }
}
