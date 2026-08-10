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

    @Test
    fun `正则批量替换支持捕获组并保留原始换行符`() {
        val source = "val user1 = \"user1\"\r\nval user2 = \"user2\""

        val result = TextReplacementEngine.replaceAll(
            text = source,
            query = "user(\\d)",
            replacement = "account$1",
            options = TextSearchOptions(regularExpression = true),
        )

        assertEquals(4, result.replacementCount)
        assertEquals("val account1 = \"account1\"\r\nval account2 = \"account2\"", result.text)
    }

    @Test
    fun `当前正则命中根据精确列计算捕获组替换文本`() {
        val replacement = TextReplacementEngine.replacementForMatch(
            line = "val user42 = user42",
            start = 4,
            endExclusive = 10,
            query = "user(\\d+)",
            replacement = "account$1",
            options = TextSearchOptions(regularExpression = true),
        )

        assertEquals("account42", replacement)
    }

    @Test
    fun `当前正则命名捕获组和普通美元符号分别按各自语义处理`() {
        val regexReplacement = TextReplacementEngine.replacementForMatch(
            line = "user-42",
            start = 0,
            endExclusive = 7,
            query = "user-(?<id>\\d+)",
            replacement = "account-\${id}",
            options = TextSearchOptions(regularExpression = true),
        )
        val plainReplacement = TextReplacementEngine.replacementForMatch(
            line = "user",
            start = 0,
            endExclusive = 4,
            query = "user",
            replacement = "$1",
            options = TextSearchOptions(),
        )

        assertEquals("account-42", regexReplacement)
        assertEquals("$1", plainReplacement)
    }

    @Test
    fun `当前替换拒绝已经失效的精确行列`() {
        val error = runCatching {
            TextReplacementEngine.replacementForMatch(
                line = "val other = 1",
                start = 4,
                endExclusive = 9,
                query = "user",
                replacement = "account",
                options = TextSearchOptions(),
            )
        }.exceptionOrNull()

        assertEquals("当前匹配已失效，请重新搜索", error?.message)
    }
}
