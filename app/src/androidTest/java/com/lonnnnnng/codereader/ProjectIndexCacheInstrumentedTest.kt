package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.DocumentRepository
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.ProjectIndexProgress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 验证目录指纹命中缓存，深层结构变化后只重扫祖先路径并复用兄弟子树。 @author long */
@RunWith(AndroidJUnit4::class)
class ProjectIndexCacheInstrumentedTest {
    @Test
    fun unchangedProjectUsesCacheAndChangedProjectRefreshes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "project-index-cache").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val stableDirectory = File(root, "stable").apply { mkdirs() }
            File(stableDirectory, "Keep.kt").writeText("class Keep")
            val changedDirectory = File(root, "changed/deep").apply { mkdirs() }
            File(changedDirectory, "First.kt").writeText("class First")
            val repository = DocumentRepository(context)
            val firstProgress = mutableListOf<ProjectIndexProgress>()
            val first = repository.indexProject(EntryLocation.Local(root), firstProgress::add)
            val cachedProgress = mutableListOf<ProjectIndexProgress>()
            val cached = repository.indexProject(EntryLocation.Local(root), cachedProgress::add)

            assertEquals(first, cached)
            assertEquals(0L, cachedProgress.single().elapsedMs)
            assertEquals(cached.size, cachedProgress.single().reusedEntries)

            File(changedDirectory, "Second.kt").writeText("class Second")
            val refreshProgress = mutableListOf<ProjectIndexProgress>()
            val refreshed = repository.indexProject(
                EntryLocation.Local(root),
                onProgress = refreshProgress::add,
                forceRefresh = true,
            )
            assertEquals(3, refreshed.count { !it.source.isDirectory })
            assertTrue(refreshed.any { it.path == "changed/deep/Second.kt" })
            assertTrue("未变化的 stable 子树应该直接复用", refreshProgress.any { it.reusedEntries > 0 })
            assertTrue("首次扫描应报告已扫描条目", firstProgress.any { it.scannedEntries > 0 })
        } finally {
            root.deleteRecursively()
        }
    }
}
