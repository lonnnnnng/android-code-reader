package com.lonnnnnng.codereader.domain

import com.lonnnnnng.codereader.model.FileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** @author long */
class TextSearchTest {

    @Test
    fun `普通搜索默认忽略大小写并统计全部命中`() {
        val result = TextSearchMatcher.compile("user", TextSearchOptions())
            .scan("User userId user")

        assertEquals(3, result.totalMatches)
        assertFalse(result.truncated)
        assertEquals(
            listOf(
                TextSearchMatch(0, 4),
                TextSearchMatch(5, 9),
                TextSearchMatch(12, 16),
            ),
            result.matches,
        )
    }

    @Test
    fun `整词和大小写选项只保留完整单词`() {
        val result = TextSearchMatcher.compile(
            query = "user",
            options = TextSearchOptions(caseSensitive = true, wholeWord = true),
        ).scan("User user userId _user user_")

        assertEquals(listOf(TextSearchMatch(5, 9)), result.matches)
    }

    @Test
    fun `正则搜索支持忽略大小写并拒绝非法表达式`() {
        val result = TextSearchMatcher.compile(
            query = "user\\d+",
            options = TextSearchOptions(regularExpression = true),
        ).scan("USER42 user user7")

        assertEquals(2, result.totalMatches)
        assertEquals("正则表达式无效", runCatching {
            TextSearchMatcher.compile("[", TextSearchOptions(regularExpression = true))
        }.exceptionOrNull()?.message?.substringBefore('：'))
    }

    @Test
    fun `项目搜索筛选同时约束目录类型和路径`() {
        val options = ProjectSearchOptions(
            fileType = FileType.KOTLIN,
            scope = ProjectSearchScope.CURRENT_DIRECTORY,
            directoryPath = "src/main",
            pathFilter = "service",
        )

        assertTrue(options.accepts("src/main/service/UserService.kt", FileType.KOTLIN))
        assertFalse(options.accepts("src/test/service/UserService.kt", FileType.KOTLIN))
        assertFalse(options.accepts("src/main/service/UserService.java", FileType.JAVA))
        assertFalse(options.accepts("src/main/api/UserApi.kt", FileType.KOTLIN))
    }

    @Test
    fun `项目搜索按选项返回行号和单行命中数量`() {
        val results = ProjectIndex.searchText(
            path = "src/User.kt",
            text = "val userId = 1\nval user = user + 1\nval User = 2",
            query = "user",
            options = TextSearchOptions(caseSensitive = true, wholeWord = true),
        )

        assertEquals(
            listOf(ProjectSearchHit("src/User.kt", 2, "val user = user + 1", matchCount = 2)),
            results,
        )
    }

    @Test
    fun `逐行搜索保留精确行列并在展示上限后继续统计`() {
        val result = TextSearchMatcher.compile(
            query = "user",
            options = TextSearchOptions(wholeWord = true),
        ).scanLines(
            lines = sequenceOf(
                "user userId user",
                "no match",
                "USER",
            ),
            maxStoredMatches = 2,
        )

        assertEquals(
            listOf(
                TextSearchPosition(line = 1, column = 0, endColumnExclusive = 4),
                TextSearchPosition(line = 1, column = 12, endColumnExclusive = 16),
            ),
            result.matches,
        )
        assertEquals(3, result.totalMatches)
        assertTrue(result.truncated)
    }
}
