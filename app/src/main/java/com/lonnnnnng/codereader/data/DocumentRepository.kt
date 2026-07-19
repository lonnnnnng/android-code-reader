package com.lonnnnnng.codereader.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.model.OpenDocument
import com.lonnnnnng.codereader.model.ProjectSearchResult
import com.lonnnnnng.codereader.model.ProjectTreeEntry
import com.lonnnnnng.codereader.model.SourceEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.Reader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * 统一处理 SAF URI 和应用私有目录，避免阅读界面依赖具体来源。
 *
 * @author long
 */
class DocumentRepository(private val context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    suspend fun openUri(uri: Uri, preferredName: String? = null): OpenDocument = withContext(Dispatchers.IO) {
        val name = preferredName ?: queryDisplayName(uri) ?: uri.lastPathSegment ?: "untitled.txt"
        val size = querySize(uri)
        val input = { resolver.openInputStream(uri) ?: error("无法读取文件：$name") }
        if (size?.let { it > LARGE_FILE_THRESHOLD_BYTES } ?: input().use(::exceedsLargeFileThreshold)) {
            return@withContext openLargeDocument(
                name = name,
                location = EntryLocation.Saf(uri),
                totalBytes = size ?: UNKNOWN_FILE_SIZE,
                input = input,
            )
        }
        val bytes = input().use(::readLimited)
        OpenDocument(
            name = name,
            text = decodeText(bytes, name),
            fileType = FileType.detect(name),
            canWrite = DocumentFile.fromSingleUri(context, uri)?.canWrite() == true,
            location = EntryLocation.Saf(uri),
            totalBytes = bytes.size.toLong(),
        )
    }

    suspend fun openLocal(file: File): OpenDocument = withContext(Dispatchers.IO) {
        require(file.isFile) { "不是文件：${file.name}" }
        if (file.length() > LARGE_FILE_THRESHOLD_BYTES) {
            return@withContext openLargeDocument(
                name = file.name,
                location = EntryLocation.Local(file),
                totalBytes = file.length(),
                input = file::inputStream,
            )
        }
        val bytes = file.inputStream().use(::readLimited)
        OpenDocument(
            name = file.name,
            text = decodeText(bytes, file.name),
            fileType = FileType.detect(file.name),
            canWrite = file.canWrite(),
            location = EntryLocation.Local(file),
            totalBytes = bytes.size.toLong(),
        )
    }

    suspend fun loadMore(document: OpenDocument): TextPage = withContext(Dispatchers.IO) {
        require(document.largeFile) { "当前文件不需要分段加载" }
        openInput(document.location).use { input ->
            TextPageReader.read(
                openTextReader(input),
                startCharacter = document.loadedCharacters,
                pageCharacters = LARGE_FILE_PAGE_CHARACTERS,
            )
        }
    }

    suspend fun save(document: OpenDocument, text: String) = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        when (val location = document.location) {
            is EntryLocation.Saf -> resolver.openOutputStream(location.uri, "wt")?.use { it.write(bytes) }
                ?: error("文件提供方不允许写入：${document.name}")
            is EntryLocation.Local -> location.file.outputStream().use { it.write(bytes) }
        }
    }

    suspend fun rootTitle(uri: Uri): String = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, uri) ?: error("无法访问所选目录")
        root.name ?: "所选目录"
    }

    suspend fun localRootTitle(directory: File): String = withContext(Dispatchers.IO) {
        require(directory.isDirectory) { "目录不存在：${directory.absolutePath}" }
        directory.name.ifBlank { directory.absolutePath }
    }

    suspend fun indexProject(root: EntryLocation): List<ProjectTreeEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ProjectTreeEntry>()
        when (root) {
            is EntryLocation.Local -> indexLocal(root.file, null, "", 0, result)
            is EntryLocation.Saf -> {
                val directory = DocumentFile.fromTreeUri(context, root.uri)
                    ?: DocumentFile.fromSingleUri(context, root.uri)
                    ?: error("目录授权已经失效")
                indexSaf(directory, null, "", 0, result)
            }
        }
        result
    }

    suspend fun searchProject(
        entries: List<ProjectTreeEntry>,
        query: String,
    ): List<ProjectSearchResult> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@withContext emptyList()
        val results = mutableListOf<ProjectSearchResult>()
        for (indexed in entries) {
            if (indexed.source.isDirectory || indexed.source.size > SEARCH_FILE_LIMIT_BYTES) continue
            val text = runCatching { readSearchText(indexed.source.location) }.getOrNull() ?: continue
            val hits = com.lonnnnnng.codereader.domain.ProjectIndex.searchText(indexed.path, text, normalized)
            hits.take(MAX_HITS_PER_FILE).forEach { hit ->
                results += ProjectSearchResult(indexed.source, hit.path, hit.line, hit.excerpt)
            }
            if (results.size >= MAX_PROJECT_SEARCH_RESULTS) break
        }
        results.take(MAX_PROJECT_SEARCH_RESULTS)
    }

    private fun DocumentFile.toSourceEntry(): SourceEntry = SourceEntry(
        name = name ?: "未命名",
        isDirectory = isDirectory,
        size = length(),
        canWrite = canWrite(),
        location = EntryLocation.Saf(uri),
    )

    private fun listLocalChildren(directory: File): List<SourceEntry> = directory.listFiles().orEmpty()
        .filterNot { it.name == ".git" }
        .map { child ->
            SourceEntry(
                name = child.name,
                isDirectory = child.isDirectory,
                size = if (child.isFile) child.length() else 0L,
                canWrite = child.canWrite(),
                location = EntryLocation.Local(child),
            )
        }
        .sortedWith(compareByDescending<SourceEntry> { it.isDirectory }.thenBy { it.name.lowercase() })

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let(::File)?.name
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) cursor.getString(0) else null
        } finally {
            cursor?.close()
        }
    }

    private fun querySize(uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let(::File)?.length()
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (cursor?.moveToFirst() == true && !cursor.isNull(0)) cursor.getLong(0) else null
        } finally {
            cursor?.close()
        }
    }

    private fun openLargeDocument(
        name: String,
        location: EntryLocation,
        totalBytes: Long,
        input: () -> InputStream,
    ): OpenDocument {
        val page = input().use { stream ->
            TextPageReader.read(openTextReader(stream), 0, LARGE_FILE_PAGE_CHARACTERS)
        }
        require('\u0000' !in page.text) { "检测到二进制内容，不能作为源码打开：$name" }
        return OpenDocument(
            name = name,
            text = page.text,
            fileType = FileType.detect(name),
            // 分段文档不能安全地覆盖保存，避免只把已加载部分写回原文件。
            canWrite = false,
            location = location,
            totalBytes = totalBytes,
            loadedCharacters = page.nextCharacter,
            hasMore = page.hasMore,
            largeFile = true,
        )
    }

    private fun openInput(location: EntryLocation): InputStream = when (location) {
        is EntryLocation.Local -> location.file.inputStream()
        is EntryLocation.Saf -> resolver.openInputStream(location.uri) ?: error("无法继续读取文件")
    }

    private fun readSearchText(location: EntryLocation): String = openInput(location).use { input ->
        val page = TextPageReader.read(openTextReader(input), 0, SEARCH_PAGE_CHARACTERS)
        require('\u0000' !in page.text) { "二进制文件" }
        page.text
    }

    /** 分段读取会反复重开文件，这里统一识别并跳过 BOM，保证每次的字符游标完全一致。 @author long */
    private fun openTextReader(input: InputStream): Reader {
        val stream = PushbackInputStream(input, 3)
        val prefix = ByteArray(3)
        val count = stream.read(prefix)
        val bomSize = when {
            count >= 3 && prefix.startsWithBytes(UTF8_BOM) -> 3
            count >= 2 && prefix.startsWithBytes(UTF16_LE_BOM) -> 2
            count >= 2 && prefix.startsWithBytes(UTF16_BE_BOM) -> 2
            else -> 0
        }
        if (count > bomSize) stream.unread(prefix, bomSize, count - bomSize)
        val charset = when {
            bomSize == 2 && prefix.startsWithBytes(UTF16_LE_BOM) -> StandardCharsets.UTF_16LE
            bomSize == 2 && prefix.startsWithBytes(UTF16_BE_BOM) -> StandardCharsets.UTF_16BE
            else -> StandardCharsets.UTF_8
        }
        return InputStreamReader(stream, charset)
    }

    private fun exceedsLargeFileThreshold(input: InputStream): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (total <= LARGE_FILE_THRESHOLD_BYTES) {
            val read = input.read(buffer)
            if (read < 0) return false
            total += read
        }
        return true
    }

    private fun indexLocal(
        directory: File,
        parentId: String?,
        parentPath: String,
        depth: Int,
        result: MutableList<ProjectTreeEntry>,
    ) {
        if (result.size >= MAX_PROJECT_ENTRIES) return
        listLocalChildren(directory).forEach { source ->
            if (result.size >= MAX_PROJECT_ENTRIES) return
            val path = if (parentPath.isEmpty()) source.name else "$parentPath/${source.name}"
            result += ProjectTreeEntry(source, path, parentId, depth)
            if (source.isDirectory) {
                indexLocal((source.location as EntryLocation.Local).file, source.id, path, depth + 1, result)
            }
        }
    }

    private fun indexSaf(
        directory: DocumentFile,
        parentId: String?,
        parentPath: String,
        depth: Int,
        result: MutableList<ProjectTreeEntry>,
    ) {
        if (result.size >= MAX_PROJECT_ENTRIES) return
        directory.listFiles()
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })
            .forEach { child ->
            if (result.size >= MAX_PROJECT_ENTRIES) return
            val source = child.toSourceEntry()
            val path = if (parentPath.isEmpty()) source.name else "$parentPath/${source.name}"
            result += ProjectTreeEntry(source, path, parentId, depth)
            if (source.isDirectory) {
                // 直接递归 listFiles() 返回的子节点，避免 fromTreeUri(childUri) 重建成整棵树的根目录。
                indexSaf(child, source.id, path, depth + 1, result)
            }
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            // 手机阅读器对超大文件一次性建模会导致明显卡顿，先给出明确边界而不是让进程被系统杀死。
            require(total <= MAX_TEXT_BYTES) { "文件超过 20 MB，当前版本暂不支持整文件编辑" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decodeText(bytes: ByteArray, name: String): String {
        require(bytes.none { it == 0.toByte() } || hasUtf16Bom(bytes)) { "检测到二进制内容，不能作为源码打开：$name" }
        return when {
            bytes.startsWithBytes(UTF8_BOM) -> String(bytes, UTF8_BOM.size, bytes.size - UTF8_BOM.size, StandardCharsets.UTF_8)
            bytes.startsWithBytes(UTF16_LE_BOM) -> String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            bytes.startsWithBytes(UTF16_BE_BOM) -> String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            else -> StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }
    }

    private fun hasUtf16Bom(bytes: ByteArray): Boolean =
        bytes.startsWithBytes(UTF16_LE_BOM) || bytes.startsWithBytes(UTF16_BE_BOM)

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val MAX_TEXT_BYTES = 20 * 1024 * 1024
        const val LARGE_FILE_THRESHOLD_BYTES = 1024 * 1024L
        const val LARGE_FILE_PAGE_CHARACTERS = 256 * 1024
        const val SEARCH_PAGE_CHARACTERS = 2 * 1024 * 1024
        const val SEARCH_FILE_LIMIT_BYTES = 2 * 1024 * 1024L
        const val UNKNOWN_FILE_SIZE = -1L
        const val MAX_PROJECT_ENTRIES = 5_000
        const val MAX_PROJECT_SEARCH_RESULTS = 200
        const val MAX_HITS_PER_FILE = 8
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    }
}
