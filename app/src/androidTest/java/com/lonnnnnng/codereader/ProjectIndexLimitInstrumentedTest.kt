package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.DocumentRepository
import com.lonnnnnng.codereader.model.EntryLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 大型工程必须完整进入目录树，不能再以 5000 条为界静默丢失后续源码。 @author long */
@RunWith(AndroidJUnit4::class)
class ProjectIndexLimitInstrumentedTest {
    @Test
    fun localProjectIndexesMoreThanFiveThousandEntries() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "project-index-over-5000").apply {
            deleteRecursively()
            mkdirs()
        }

        try {
            repeat(51) { directoryIndex ->
                val directory = File(root, "module-$directoryIndex").apply { mkdirs() }
                repeat(100) { fileIndex ->
                    File(directory, "Source$fileIndex.kt").writeText("class Source$fileIndex")
                }
            }

            val entries = DocumentRepository(context).indexProject(EntryLocation.Local(root))

            // 51 个目录加 5100 个文件必须全部出现，证明旧版 5000 条截断已从真实索引链路移除。 @author long
            assertEquals(5_151, entries.size)
        } finally {
            root.deleteRecursively()
        }
    }
}
