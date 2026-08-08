package com.lonnnnnng.codereader.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 单个文档的持续阅读状态；项目根用于重新进入项目时恢复最后阅读文件。
 *
 * @author long
 */
data class ReadingDocumentState(
    val locationKind: String,
    val documentId: String,
    val documentName: String,
    val projectRootId: String? = null,
    val lastViewedLine: Int = 1,
    val fileBookmarked: Boolean = false,
    val lineBookmarks: List<Int> = emptyList(),
)

/**
 * 偏好数据会跨版本长期保留，因此在进入界面前统一去重、修正行号并限制容量。
 *
 * @author long
 */
object ReadingStatePolicy {
    const val MAX_DOCUMENTS = 200
    const val MAX_LINE_BOOKMARKS = 100

    fun normalize(
        states: List<ReadingDocumentState>,
        maxDocuments: Int = MAX_DOCUMENTS,
        maxLineBookmarks: Int = MAX_LINE_BOOKMARKS,
    ): List<ReadingDocumentState> {
        val normalized = states.asSequence()
            .filter { it.locationKind == "local" || it.locationKind == "saf" }
            .filter { it.documentId.isNotBlank() && it.documentName.isNotBlank() }
            .distinctBy(ReadingDocumentState::documentId)
            .map { state ->
                state.copy(
                    lastViewedLine = state.lastViewedLine.coerceAtLeast(1),
                    lineBookmarks = state.lineBookmarks.asSequence()
                        .filter { it > 0 }
                        .distinct()
                        .sorted()
                        .take(maxLineBookmarks.coerceAtLeast(0))
                        .toList(),
                )
            }
            .toList()
        val limit = maxDocuments.coerceAtLeast(0)
        if (normalized.size <= limit) return normalized

        // 书签是用户明确保留的资料，不能被后续打开的普通文件挤出容量上限。 @author long
        val bookmarkedIds = normalized.asSequence()
            .filter { it.fileBookmarked || it.lineBookmarks.isNotEmpty() }
            .take(limit)
            .mapTo(linkedSetOf(), ReadingDocumentState::documentId)
        val remainingSlots = (limit - bookmarkedIds.size).coerceAtLeast(0)
        val selectedIds = normalized.asSequence()
            .filterNot { it.documentId in bookmarkedIds }
            .take(remainingSlots)
            .mapTo(bookmarkedIds, ReadingDocumentState::documentId)
        return normalized.filter { it.documentId in selectedIds }
    }
}

/**
 * 使用带版本号的逐行格式保存状态，字段经过 URL 编码后可安全容纳中文、Tab 和换行。
 *
 * @author long
 */
object ReadingStateCodec {
    private const val VERSION = "1"

    fun encode(states: List<ReadingDocumentState>): String = states.joinToString("\n") { state ->
        listOf(
            VERSION,
            state.locationKind,
            state.documentId,
            state.documentName,
            state.projectRootId.orEmpty(),
            state.lastViewedLine.toString(),
            state.fileBookmarked.toString(),
            state.lineBookmarks.joinToString(","),
        ).joinToString("\t") { encodeField(it) }
    }

    fun decode(value: String?): List<ReadingDocumentState> = value.orEmpty().lineSequence()
        .filter(String::isNotBlank)
        .mapNotNull(::decodeLine)
        .toList()

    private fun decodeLine(line: String): ReadingDocumentState? {
        val fields = line.split('\t')
        if (fields.size != 8) return null
        return runCatching {
            val decoded = fields.map(::decodeField)
            if (decoded[0] != VERSION) return null
            ReadingDocumentState(
                locationKind = decoded[1],
                documentId = decoded[2],
                documentName = decoded[3],
                projectRootId = decoded[4].ifBlank { null },
                lastViewedLine = decoded[5].toInt(),
                fileBookmarked = decoded[6].toBooleanStrict(),
                lineBookmarks = decoded[7].split(',').filter(String::isNotBlank).map(String::toInt),
            )
        }.getOrNull()
    }

    private fun encodeField(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    private fun decodeField(value: String): String = URLDecoder.decode(value, Charsets.UTF_8.name())
}
