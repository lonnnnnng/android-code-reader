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
    val encoding: TextEncoding = TextEncoding.UTF_8,
    val totalBytes: Long = text.toByteArray().size.toLong(),
    val loadedCharacters: Long = text.length.toLong(),
    val hasMore: Boolean = false,
    val largeFile: Boolean = false,
) {
    val id: String = location.stableId
}

/** 无法作为文本读取的文件仍保留完整来源信息，供识别页展示和外部打开。 @author long */
data class BinaryFileInfo(
    val name: String,
    val size: Long,
    val mimeType: String,
    val location: EntryLocation,
)

/** @author long */
class BinaryFileException(
    val fileInfo: BinaryFileInfo,
) : IllegalArgumentException("检测到二进制内容，不能作为源码打开：${fileInfo.name}")

/** 项目完整索引中的条目，用于折叠树、全局搜索和快速切换文件。 @author long */
data class ProjectTreeEntry(
    val source: SourceEntry,
    val path: String,
    val parentId: String?,
    val depth: Int,
)

/** 目录索引过程的可观测统计，目录来源无法预先知道总量，因此使用已扫描数量和耗时反馈。 @author long */
data class ProjectIndexProgress(
    val scannedEntries: Int,
    val scannedFiles: Int,
    val scannedDirectories: Int,
    val elapsedMs: Long,
    val reusedEntries: Int = 0,
)

/** @author long */
data class ProjectSearchResult(
    val source: SourceEntry,
    val path: String,
    val line: Int,
    val excerpt: String,
)

/** 项目搜索把可展示位置和完整命中统计分开，结果达到移动端上限时仍能明确提示。 @author long */
data class ProjectSearchPage(
    val results: List<ProjectSearchResult>,
    val totalMatches: Int,
    val matchedFiles: Int,
    val truncated: Boolean,
)
