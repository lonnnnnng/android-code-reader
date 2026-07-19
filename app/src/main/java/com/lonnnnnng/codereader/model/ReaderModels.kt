package com.lonnnnnng.codereader.model

import android.net.Uri
import java.io.File

/** @author long */
sealed interface EntryLocation {
    val stableId: String

    data class Saf(val uri: Uri) : EntryLocation {
        override val stableId: String = uri.toString()
    }

    data class Local(val file: File) : EntryLocation {
        override val stableId: String = file.absolutePath
    }
}

/** @author long */
data class SourceEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val canWrite: Boolean,
    val location: EntryLocation,
) {
    val id: String = location.stableId
}

/** @author long */
data class OpenDocument(
    val name: String,
    val text: String,
    val fileType: FileType,
    val canWrite: Boolean,
    val location: EntryLocation,
    val totalBytes: Long = text.toByteArray().size.toLong(),
    val loadedCharacters: Long = text.length.toLong(),
    val hasMore: Boolean = false,
    val largeFile: Boolean = false,
) {
    val id: String = location.stableId
}

/** 项目完整索引中的条目，用于折叠树、全局搜索和快速切换文件。 @author long */
data class ProjectTreeEntry(
    val source: SourceEntry,
    val path: String,
    val parentId: String?,
    val depth: Int,
)

/** @author long */
data class ProjectSearchResult(
    val source: SourceEntry,
    val path: String,
    val line: Int,
    val excerpt: String,
)

/** @author long */
data class BrowserSnapshot(
    val title: String,
    val entries: List<SourceEntry>,
    val depth: Int,
)
