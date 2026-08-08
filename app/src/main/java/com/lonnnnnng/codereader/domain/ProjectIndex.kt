package com.lonnnnnng.codereader.domain

/** 项目树中的稳定索引项，不绑定 Android 文件来源，便于搜索与折叠逻辑独立验证。 @author long */
data class IndexedProjectEntry(
    val id: String,
    val parentId: String?,
    val path: String,
    val depth: Int,
    val isDirectory: Boolean,
)

/** @author long */
data class ProjectSearchHit(
    val path: String,
    val line: Int,
    val excerpt: String,
    val matchCount: Int = 1,
)

/** @author long */
object ProjectIndex {
    fun visible(entries: List<IndexedProjectEntry>, expandedDirectoryIds: Set<String>): List<IndexedProjectEntry> {
        val byId = entries.associateBy { it.id }
        return entries.filter { entry ->
            var parentId = entry.parentId
            while (parentId != null) {
                if (parentId !in expandedDirectoryIds) return@filter false
                parentId = byId[parentId]?.parentId
            }
            true
        }
    }

    fun searchText(
        path: String,
        text: String,
        query: String,
        options: TextSearchOptions = TextSearchOptions(),
    ): List<ProjectSearchHit> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val matcher = TextSearchMatcher.compile(normalizedQuery, options)
        return text.lineSequence().mapIndexedNotNull { index, line ->
            searchLine(path, line, index + 1, matcher)
        }.toList()
    }

    /**
     * 对分段读取的完整行做匹配，调用方负责提供真实行号，从而搜索不必把整个大文件装入内存。
     * @author long
     */
    fun searchLines(
        path: String,
        lines: Sequence<String>,
        firstLine: Int,
        query: String,
        options: TextSearchOptions = TextSearchOptions(),
    ): List<ProjectSearchHit> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        return searchLines(path, lines, firstLine, TextSearchMatcher.compile(normalizedQuery, options))
    }

    fun searchLines(
        path: String,
        lines: Sequence<String>,
        firstLine: Int,
        matcher: TextSearchMatcher,
    ): List<ProjectSearchHit> {
        return lines.mapIndexedNotNull { index, line ->
            searchLine(path, line, firstLine + index, matcher)
        }.toList()
    }

    private fun searchLine(
        path: String,
        line: String,
        lineNumber: Int,
        matcher: TextSearchMatcher,
    ): ProjectSearchHit? {
        val scan = matcher.scan(line, maxStoredMatches = 0)
        return if (scan.totalMatches > 0) {
            ProjectSearchHit(path, lineNumber, line.trim().take(180), scan.totalMatches)
        } else {
            null
        }
    }
}
