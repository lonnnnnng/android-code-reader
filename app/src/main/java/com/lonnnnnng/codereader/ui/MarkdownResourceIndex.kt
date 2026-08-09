package com.lonnnnnng.codereader.ui

import android.net.Uri
import com.lonnnnnng.codereader.model.ProjectTreeEntry
import com.lonnnnnng.codereader.model.SourceEntry
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Markdown 相对资源只允许解析到当前项目索引，避免预览通过 ../ 读取项目外文件。 @author long */
internal class MarkdownResourceIndex(
    documentPath: String,
    projectEntries: List<ProjectTreeEntry>,
) {
    private val normalizedDocumentPath = normalizeProjectPath(documentPath) ?: documentPath.substringAfterLast('/')
    private val documentDirectory = normalizedDocumentPath.substringBeforeLast('/', missingDelimiterValue = "")
    private val entriesByPath = projectEntries
        .asSequence()
        .filterNot { it.source.isDirectory }
        .mapNotNull { entry -> normalizeProjectPath(entry.path)?.let { it to entry.source } }
        .toMap()

    val documentUrl: String = virtualProjectUrl(normalizedDocumentPath)

    fun resolve(reference: String): SourceEntry? {
        val path = reference.substringBefore('#').substringBefore('?')
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return null
        if (SCHEME_PATTERN.containsMatchIn(path)) return null
        // URLDecoder 会把 + 当成表单空格，路径中的 + 必须先保护后再执行百分号解码。 @author long
        val decoded = runCatching {
            URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrDefault(path)
        val resolvedPath = normalizeProjectPath(
            listOf(documentDirectory, decoded).filter(String::isNotBlank).joinToString("/"),
        ) ?: return null
        return entriesByPath[resolvedPath]
    }

    fun resolve(uri: Uri): SourceEntry? {
        if (uri.scheme != VIRTUAL_SCHEME || uri.host != VIRTUAL_HOST) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != PROJECT_PREFIX) return null
        val path = segments.drop(1).joinToString("/")
        return entriesByPath[normalizeProjectPath(path)]
    }

    fun isCurrentDocument(uri: Uri): Boolean = uri.scheme == VIRTUAL_SCHEME &&
        uri.host == VIRTUAL_HOST &&
        uri.pathSegments.firstOrNull() == PROJECT_PREFIX &&
        normalizeProjectPath(uri.pathSegments.drop(1).joinToString("/")) == normalizedDocumentPath

    private companion object {
        val SCHEME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
    }
}

internal const val VIRTUAL_SCHEME = "https"
internal const val VIRTUAL_HOST = "appassets.androidplatform.net"
internal const val PROJECT_PREFIX = "project"
internal const val ASSET_PREFIX = "assets"

internal fun virtualProjectUrl(path: String): String = URI(
    VIRTUAL_SCHEME,
    VIRTUAL_HOST,
    "/$PROJECT_PREFIX/$path",
    null,
).toASCIIString()

/** 将反斜杠和点路径归一化；一旦 .. 越过项目根就拒绝，不能静默回到根目录。 @author long */
internal fun normalizeProjectPath(path: String): String? {
    val segments = mutableListOf<String>()
    path.replace('\\', '/').split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
            else -> segments += segment
        }
    }
    return segments.joinToString("/").takeIf(String::isNotBlank)
}
