package com.lonnnnnng.codereader.domain

import java.util.Locale

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

    fun searchText(path: String, text: String, query: String): List<ProjectSearchHit> {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        if (normalizedQuery.isEmpty()) return emptyList()
        return text.lineSequence().mapIndexedNotNull { index, line ->
            if (line.lowercase(Locale.ROOT).contains(normalizedQuery)) {
                ProjectSearchHit(path, index + 1, line.trim().take(180))
            } else {
                null
            }
        }.toList()
    }
}
