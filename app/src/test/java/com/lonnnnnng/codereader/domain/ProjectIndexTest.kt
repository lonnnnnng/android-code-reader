package com.lonnnnnng.codereader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** @author long */
class ProjectIndexTest {
    private val entries = listOf(
        IndexedProjectEntry("src", null, "src", 0, true),
        IndexedProjectEntry("main", "src", "src/main", 1, true),
        IndexedProjectEntry("java", "main", "src/main/Main.java", 2, false),
        IndexedProjectEntry("readme", null, "README.md", 0, false),
    )

    @Test
    fun `目录树仅展示已展开目录的后代`() {
        assertEquals(listOf("src", "readme"), ProjectIndex.visible(entries, emptySet()).map { it.id })
        assertEquals(listOf("src", "main", "readme"), ProjectIndex.visible(entries, setOf("src")).map { it.id })
        assertEquals(listOf("src", "main", "java", "readme"), ProjectIndex.visible(entries, setOf("src", "main")).map { it.id })
    }

    @Test
    fun `源码搜索返回稳定的路径行号和摘要`() {
        val results = ProjectIndex.searchText(
            path = "src/main/Main.java",
            text = "class Main {\n  return service.findUser();\n}",
            query = "user",
        )

        assertEquals(listOf(ProjectSearchHit("src/main/Main.java", 2, "return service.findUser();")), results)
    }
}
