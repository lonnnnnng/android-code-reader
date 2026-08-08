package com.lonnnnnng.codereader.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.lonnnnnng.codereader.model.BinaryContentException
import com.lonnnnnng.codereader.model.BinaryFileException
import com.lonnnnnng.codereader.model.BinaryFileInfo
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.model.OpenDocument
import com.lonnnnnng.codereader.model.ProjectIndexProgress
import com.lonnnnnng.codereader.model.ProjectSearchPage
import com.lonnnnnng.codereader.model.ProjectSearchResult
import com.lonnnnnng.codereader.model.ProjectTreeEntry
import com.lonnnnnng.codereader.model.SourceEntry
import com.lonnnnnng.codereader.model.TextEncoding
import com.lonnnnnng.codereader.model.TextEncodingDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import com.lonnnnnng.codereader.domain.ProjectSearchOptions
import com.lonnnnnng.codereader.domain.TextSearchLineCollector
import com.lonnnnnng.codereader.domain.TextSearchMatcher
import com.lonnnnnng.codereader.domain.TextSearchOptions
import com.lonnnnnng.codereader.domain.TextSearchPage
import com.lonnnnnng.codereader.domain.TextSearchProgress
import java.io.ByteArrayOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.util.concurrent.ConcurrentHashMap

/** 项目搜索只返回手机端可流畅浏览的前 200 条，UI 会明确提示该显示上限。 @author long */
internal const val PROJECT_SEARCH_RESULT_LIMIT = 200

/**
 * 统一处理 SAF URI 和应用私有目录，避免阅读界面依赖具体来源。
 *
 * @author long
 */
class DocumentRepository(
    private val context: Context,
    private val memoryBudgetProvider: MemoryBudgetProvider = AndroidMemoryBudgetProvider(context),
) {

    private val resolver: ContentResolver = context.contentResolver
    private val indexCache = ConcurrentHashMap<String, CachedProjectIndex>()
    private val pageCursors = ConcurrentHashMap<String, PageCursor>()

    fun close() {
        pageCursors.values.forEach(PageCursor::close)
        pageCursors.clear()
    }

    private data class CachedProjectIndex(
        val entries: List<ProjectTreeEntry>,
        val directoryFingerprints: Map<String, String>,
    )

    suspend fun openUri(uri: Uri, preferredName: String? = null): OpenDocument = withContext(Dispatchers.IO) {
        val name = preferredName ?: queryDisplayName(uri) ?: uri.lastPathSegment ?: "untitled.txt"
        val size = querySize(uri)
        val input = { resolver.openInputStream(uri) ?: error("无法读取文件：$name") }
        val location = EntryLocation.Saf(uri)
        val memoryBudget = memoryBudgetProvider.current()
        val threshold = sourceLargeFileThreshold(memoryBudget, location, size)
        if (size?.let { it > threshold } ?: input().use { exceedsLargeFileThreshold(it, threshold) }) {
            return@withContext openLargeDocument(
                name = name,
                location = location,
                totalBytes = size ?: UNKNOWN_FILE_SIZE,
                input = input,
            )
        }
        val bytes = input().use(::readLimited)
        val decoded = decodeText(bytes, name, size ?: bytes.size.toLong(), location, mimeType(name, uri))
        OpenDocument(
            name = name,
            text = decoded.text,
            fileType = FileType.detect(name),
            canWrite = DocumentFile.fromSingleUri(context, uri)?.canWrite() == true,
            location = location,
            encoding = decoded.encoding,
            totalBytes = bytes.size.toLong(),
        )
    }

    suspend fun openLocal(file: File): OpenDocument = withContext(Dispatchers.IO) {
        require(file.isFile) { "不是文件：${file.name}" }
        val location = EntryLocation.Local(file)
        val threshold = sourceLargeFileThreshold(memoryBudgetProvider.current(), location, file.length())
        if (file.length() > threshold) {
            return@withContext openLargeDocument(
                name = file.name,
                location = location,
                totalBytes = file.length(),
                input = file::inputStream,
            )
        }
        val bytes = file.inputStream().use(::readLimited)
        val decoded = decodeText(bytes, file.name, bytes.size.toLong(), location, mimeType(file.name))
        OpenDocument(
            name = file.name,
            text = decoded.text,
            fileType = FileType.detect(file.name),
            canWrite = file.canWrite(),
            location = location,
            encoding = decoded.encoding,
            totalBytes = bytes.size.toLong(),
        )
    }

    suspend fun loadMore(document: OpenDocument): TextPage = withContext(Dispatchers.IO) {
        require(document.largeFile) { "当前文件不需要分段加载" }
        readNextDocumentPage(document)
    }

    suspend fun reopen(document: OpenDocument, encoding: TextEncoding): OpenDocument = withContext(Dispatchers.IO) {
        if (document.largeFile) {
            val page = readInitialDocumentPage(document.location, encoding)
            return@withContext document.copy(
                text = page.text,
                encoding = encoding,
                loadedCharacters = page.nextCharacter,
                hasMore = page.hasMore,
            )
        }

        val bytes = openInput(document.location).use(::readLimited)
        val decoded = TextEncodingDetector.decode(bytes, document.name, encoding)
        document.copy(
            text = decoded.text,
            encoding = encoding,
            totalBytes = bytes.size.toLong(),
            loadedCharacters = decoded.text.length.toLong(),
            hasMore = false,
        )
    }

    suspend fun save(document: OpenDocument, text: String) = withContext(Dispatchers.IO) {
        val bytes = document.encoding.encode(text)
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

    suspend fun indexProject(
        root: EntryLocation,
        onProgress: (ProjectIndexProgress) -> Unit = {},
        forceRefresh: Boolean = false,
    ): List<ProjectTreeEntry> = withContext(Dispatchers.IO) {
        val cached = indexCache[root.stableId]
        cached?.let {
            if (!forceRefresh || isCacheFresh(root, cached)) {
                onProgress(cachedProgress(cached))
                return@withContext cached.entries
            }
        }
        val result = mutableListOf<ProjectTreeEntry>()
        val progress = IndexProgressReporter(onProgress, ::directoryFingerprint)
        progress.recordRoot(root)
        when (root) {
            is EntryLocation.Local -> {
                if (forceRefresh && cached != null) {
                    indexLocalIncremental(root.file, cached, result, progress)
                } else {
                    indexLocal(root.file, null, "", 0, result, mutableSetOf(), progress)
                }
            }
            is EntryLocation.Saf -> {
                val directory = DocumentFile.fromTreeUri(context, root.uri)
                    ?: DocumentFile.fromSingleUri(context, root.uri)
                    ?: error("目录授权已经失效")
                indexSaf(directory, null, "", 0, result, mutableSetOf(), progress)
            }
        }
        progress.report(force = true)
        val indexed = result.toList()
        indexCache[root.stableId] = CachedProjectIndex(indexed, progress.directoryFingerprints.toMap())
        indexed
    }

    /** 缓存命中仍回传统计，界面可以统一结束“建立索引”状态而不显示空白等待。 @author long */
    private fun cachedProgress(cached: CachedProjectIndex): ProjectIndexProgress = ProjectIndexProgress(
        scannedEntries = cached.entries.size,
        scannedFiles = cached.entries.count { !it.source.isDirectory },
        scannedDirectories = cached.entries.count { it.source.isDirectory },
        elapsedMs = 0,
        reusedEntries = cached.entries.size,
    )

    /** 只比较目录结构元数据；文件内容变化不影响路径索引，内容搜索仍由用户主动重新搜索触发。 @author long */
    private fun isCacheFresh(root: EntryLocation, cached: CachedProjectIndex): Boolean {
        // SAF 提供方不保证目录时间戳和子项元数据可靠，显式刷新时完整重建比误用过期树更安全。
        if (root is EntryLocation.Saf) return false
        return changedLocalDirectoryIds(root as EntryLocation.Local, cached).isEmpty()
    }

    private fun changedLocalDirectoryIds(
        root: EntryLocation.Local,
        cached: CachedProjectIndex,
    ): Set<String> {
        val locations = buildMap<String, EntryLocation> {
            put(root.stableId, root)
            cached.entries.filter { it.source.isDirectory }.forEach { put(it.source.id, it.source.location) }
        }
        val directoryEntries = cached.entries.filter { it.source.isDirectory }.associateBy { it.source.id }
        val affected = cached.directoryFingerprints.mapNotNullTo(linkedSetOf()) { (id, expected) ->
            id.takeIf { locations[id]?.let(::directoryFingerprint) != expected }
        }
        affected.toList().forEach { changedId ->
            var entry = directoryEntries[changedId]
            while (entry != null) {
                val parentId = entry.parentId
                if (parentId == null) {
                    affected += root.stableId
                    break
                }
                affected += parentId
                entry = directoryEntries[parentId]
            }
        }
        return affected
    }

    private fun directoryFingerprint(location: EntryLocation): String = when (location) {
        is EntryLocation.Local -> {
            val file = location.file
            val childrenHash = file.listFiles()?.sortedBy { it.name.lowercase() }?.fold(1) { hash, child ->
                var next = 31 * hash + child.name.hashCode()
                next = 31 * next + child.isDirectory.hashCode()
                next = 31 * next + child.length().hashCode()
                31 * next + child.lastModified().hashCode()
            }
            "local:${file.exists()}:${file.lastModified()}:${file.length()}:$childrenHash"
        }
        is EntryLocation.Saf -> {
            // SAF 指纹不参与缓存新鲜度判断，仅保留稳定来源标识供诊断。
            "saf-unverified:${location.stableId}"
        }
    }

    suspend fun searchProject(
        entries: List<ProjectTreeEntry>,
        query: String,
    ): List<ProjectSearchResult> = searchProject(entries, query, ProjectSearchOptions()).results

    suspend fun searchProject(
        entries: List<ProjectTreeEntry>,
        query: String,
        options: ProjectSearchOptions,
    ): ProjectSearchPage = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return@withContext ProjectSearchPage(emptyList(), 0, 0, false)
        // 正则必须在遍历文件前编译；表达式错误属于用户输入问题，不能被单文件容错静默吞掉。 @author long
        val matcher = TextSearchMatcher.compile(normalized, options.text)
        val results = mutableListOf<ProjectSearchResult>()
        var totalMatches = 0
        var totalMatchingLines = 0
        var matchedFiles = 0
        for (indexed in entries) {
            currentCoroutineContext().ensureActive()
            if (indexed.source.isDirectory) continue
            val fileType = FileType.detect(indexed.source.name)
            if (!options.accepts(indexed.path, fileType)) continue
            val scan = try {
                readSearchHits(indexed.path, indexed.source.location, matcher)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // 单个文件可能在索引后失效或是二进制内容，不能阻断其他文件的搜索。
                SearchFileHits(emptyList(), 0, 0)
            }
            if (scan.totalMatches > 0) matchedFiles++
            totalMatches += scan.totalMatches
            totalMatchingLines += scan.totalMatchingLines
            scan.hits.forEach { hit ->
                if (results.size < PROJECT_SEARCH_RESULT_LIMIT) {
                    results += ProjectSearchResult(indexed.source, hit.path, hit.line, hit.excerpt)
                }
            }
        }
        ProjectSearchPage(
            results = results,
            totalMatches = totalMatches,
            matchedFiles = matchedFiles,
            truncated = totalMatchingLines > results.size,
        )
    }

    /**
     * 文件内搜索直接按原始编码流式读取到文件末尾，避免大文件搜索被首个 256K 字符分页截断。
     * @author long
     */
    suspend fun searchDocument(
        document: OpenDocument,
        query: String,
        options: TextSearchOptions,
        maxStoredMatches: Int,
        onProgress: (TextSearchProgress) -> Unit = {},
    ): TextSearchPage = withContext(Dispatchers.IO) {
        val matcher = TextSearchMatcher.compile(query, options)
        val collector = TextSearchLineCollector(matcher, maxStoredMatches)
        var scannedLines = 0
        openTextReader(openInput(document.location), document.encoding).use { reader ->
            var lineNumber = 1
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = reader.readLine() ?: break
                collector.accept(lineNumber, line)
                scannedLines = lineNumber
                if (scannedLines % FILE_SEARCH_PROGRESS_LINE_BATCH == 0) {
                    onProgress(TextSearchProgress(scannedLines, collector.totalMatches))
                }
                lineNumber++
            }
        }
        // 文件不足一个批次或末批未对齐时也必须交付最终进度，界面才能显示准确扫描范围。 @author long
        onProgress(TextSearchProgress(scannedLines, collector.totalMatches))
        collector.result()
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

    private fun decodeText(
        bytes: ByteArray,
        name: String,
        size: Long,
        location: EntryLocation,
        mimeType: String,
    ) = try {
        TextEncodingDetector.decode(bytes, name)
    } catch (error: BinaryContentException) {
        throw BinaryFileException(BinaryFileInfo(name, size, mimeType, location))
    }

    private fun mimeType(name: String, uri: Uri? = null): String {
        if (uri != null) resolver.getType(uri)?.takeIf { it.isNotBlank() }?.let { return it }
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun openLargeDocument(
        name: String,
        location: EntryLocation,
        totalBytes: Long,
        input: () -> InputStream,
    ): OpenDocument {
        val encoding = try {
            input().use { TextEncodingDetector.detectStream(it, name) }
        } catch (error: BinaryContentException) {
            throw BinaryFileException(
                BinaryFileInfo(
                    name,
                    totalBytes,
                    mimeType(name, (location as? EntryLocation.Saf)?.uri),
                    location,
                ),
            )
        }
        val page = readInitialDocumentPage(location, encoding, input)
        return OpenDocument(
            name = name,
            text = page.text,
            fileType = FileType.detect(name),
            // 分段文档不能安全地覆盖保存，避免只把已加载部分写回原文件。
            canWrite = false,
            location = location,
            encoding = encoding,
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

    /** 记录大文件当前解码器位置；连续加载复用流，随机/失效场景由 read() 回退。 @author long */
    private data class PageCursor(
        val encoding: TextEncoding,
        val reader: BufferedReader,
        var nextCharacter: Long,
    ) {
        fun close() = reader.close()
    }

    @Synchronized
    private fun readInitialDocumentPage(
        location: EntryLocation,
        encoding: TextEncoding,
        input: () -> InputStream = { openInput(location) },
    ): TextPage {
        pageCursors.remove(location.stableId)?.close()
        val reader = openTextReader(input(), encoding)
        val page = TextPageReader.readNext(reader, LARGE_FILE_PAGE_CHARACTERS)
        if (page.hasMore) {
            pageCursors[location.stableId] = PageCursor(encoding, reader, page.nextCharacter)
        } else {
            reader.close()
        }
        return page
    }

    @Synchronized
    private fun readNextDocumentPage(document: OpenDocument): TextPage {
        val key = document.location.stableId
        val cached = pageCursors[key]
        if (cached != null && cached.encoding == document.encoding && cached.nextCharacter == document.loadedCharacters) {
            return runCatching {
                val page = TextPageReader.readNext(cached.reader, LARGE_FILE_PAGE_CHARACTERS)
                cached.nextCharacter += page.nextCharacter
                if (!page.hasMore) pageCursors.remove(key)?.close()
                page.copy(nextCharacter = cached.nextCharacter)
            }.getOrElse {
                pageCursors.remove(key)?.close()
                readPageFromStart(document)
            }
        }
        return readPageFromStart(document)
    }

    private fun readPageFromStart(document: OpenDocument): TextPage {
        val reader = openTextReader(openInput(document.location), document.encoding)
        val page = TextPageReader.read(reader, document.loadedCharacters, LARGE_FILE_PAGE_CHARACTERS)
        if (page.hasMore) {
            pageCursors.put(
                document.location.stableId,
                PageCursor(document.encoding, reader, page.nextCharacter),
            )?.close()
        } else {
            reader.close()
        }
        return page
    }

    /**
     * 按字符页顺序扫描项目文件，命中跨页时保留未结束行，确保大文件后半段搜索的行号和摘要完整。
     * @author long
     */
    private data class SearchFileHits(
        val hits: List<com.lonnnnnng.codereader.domain.ProjectSearchHit>,
        val totalMatches: Int,
        val totalMatchingLines: Int,
    )

    private suspend fun readSearchHits(
        path: String,
        location: EntryLocation,
        matcher: TextSearchMatcher,
    ): SearchFileHits {
        val encoding = openInput(location).use { input ->
            TextEncodingDetector.detectStream(input, "项目搜索文件")
        }
        val hits = mutableListOf<com.lonnnnnng.codereader.domain.ProjectSearchHit>()
        var totalMatches = 0
        var totalMatchingLines = 0
        openInput(location).use { input ->
            val reader = openTextReader(input, encoding)
            var nextLine = 1
            var carry = ""
            while (true) {
                currentCoroutineContext().ensureActive()
                val page = TextPageReader.readNext(reader, SEARCH_PAGE_CHARACTERS)
                val parts = (carry + page.text).split('\n')
                val completeLines = parts.dropLast(1)
                if (completeLines.isNotEmpty()) {
                    val pageHits = com.lonnnnnng.codereader.domain.ProjectIndex.searchLines(
                        path = path,
                        lines = completeLines.asSequence(),
                        firstLine = nextLine,
                        matcher = matcher,
                    )
                    totalMatches += pageHits.sumOf { it.matchCount }
                    totalMatchingLines += pageHits.size
                    pageHits.forEach { if (hits.size < MAX_HITS_PER_FILE) hits += it }
                    nextLine += completeLines.size
                }
                carry = parts.lastOrNull().orEmpty()
                if (!page.hasMore) {
                    if (carry.isNotEmpty()) {
                        val finalHits = com.lonnnnnng.codereader.domain.ProjectIndex.searchLines(
                            path = path,
                            lines = sequenceOf(carry),
                            firstLine = nextLine,
                            matcher = matcher,
                        )
                        totalMatches += finalHits.sumOf { it.matchCount }
                        totalMatchingLines += finalHits.size
                        finalHits.forEach { if (hits.size < MAX_HITS_PER_FILE) hits += it }
                    }
                    break
                }
            }
        }
        return SearchFileHits(hits, totalMatches, totalMatchingLines)
    }

    /** 索引扫描统计按批次汇报，避免大型项目每个文件都触发一次 Compose 重组。 @author long */
    private class IndexProgressReporter(
        private val onProgress: (ProjectIndexProgress) -> Unit,
        private val fingerprint: (EntryLocation) -> String,
    ) {
        private val startedAt = System.nanoTime()
        private var lastReportedEntries = 0
        private var scannedEntries = 0
        private var scannedFiles = 0
        private var scannedDirectories = 0
        private var reusedEntries = 0
        val directoryFingerprints = mutableMapOf<String, String>()

        fun record(source: SourceEntry) {
            scannedEntries++
            if (source.isDirectory) {
                scannedDirectories++
                directoryFingerprints[source.id] = fingerprint(source.location)
            } else {
                scannedFiles++
            }
            if (scannedEntries - lastReportedEntries >= INDEX_PROGRESS_BATCH) report()
        }

        fun recordRoot(root: EntryLocation) {
            directoryFingerprints[root.stableId] = fingerprint(root)
        }

        fun recordCached(source: SourceEntry, cachedFingerprint: String?) {
            scannedEntries++
            reusedEntries++
            if (source.isDirectory) {
                scannedDirectories++
                cachedFingerprint?.let { directoryFingerprints[source.id] = it }
            } else {
                scannedFiles++
            }
            if (scannedEntries - lastReportedEntries >= INDEX_PROGRESS_BATCH) report()
        }

        fun report(force: Boolean = false) {
            if (!force && scannedEntries == lastReportedEntries) return
            lastReportedEntries = scannedEntries
            onProgress(
                ProjectIndexProgress(
                    scannedEntries = scannedEntries,
                    scannedFiles = scannedFiles,
                    scannedDirectories = scannedDirectories,
                    elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L,
                    reusedEntries = reusedEntries,
                ),
            )
        }
    }

    /** 分段读取会反复重开文件，这里统一识别并跳过 BOM，保证每次的字符游标完全一致。 @author long */
    private fun openTextReader(input: InputStream, encoding: TextEncoding? = null): BufferedReader {
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
        val selectedEncoding = encoding ?: when {
            bomSize == 2 && prefix.startsWithBytes(UTF16_LE_BOM) -> TextEncoding.UTF_16_LE
            bomSize == 2 && prefix.startsWithBytes(UTF16_BE_BOM) -> TextEncoding.UTF_16_BE
            bomSize == 3 && prefix.startsWithBytes(UTF8_BOM) -> TextEncoding.UTF_8_BOM
            else -> TextEncoding.UTF_8
        }
        return BufferedReader(InputStreamReader(stream, selectedEncoding.charset))
    }

    private fun exceedsLargeFileThreshold(input: InputStream, thresholdBytes: Long): Boolean {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (total <= thresholdBytes) {
            val read = input.read(buffer)
            if (read < 0) return false
            total += read
        }
        return true
    }

    /** 未提供大小的 SAF 流无法提前判断内存成本，采用更保守的探测上限。 @author long */
    private fun sourceLargeFileThreshold(
        memoryBudget: ReaderMemoryBudget,
        location: EntryLocation,
        knownSize: Long?,
    ): Long = if (location is EntryLocation.Saf && knownSize == null) {
        minOf(memoryBudget.largeFileThresholdBytes, MemoryBudgetPolicy.MIN_LARGE_FILE_THRESHOLD_BYTES)
    } else {
        memoryBudget.largeFileThresholdBytes
    }

    /**
     * 本地刷新只重扫结构发生变化的目录；未变化目录直接复用缓存子树，保持路径、深度和排序稳定。
     * @author long
     */
    private suspend fun indexLocalIncremental(
        root: File,
        cached: CachedProjectIndex,
        result: MutableList<ProjectTreeEntry>,
        progress: IndexProgressReporter,
    ) {
        val cachedByParent = cached.entries.groupBy { it.parentId }
        val affectedDirectoryIds = changedLocalDirectoryIds(EntryLocation.Local(root), cached)
        indexLocalIncrementalDirectory(
            directory = root,
            parentId = null,
            parentPath = "",
            depth = 0,
            result = result,
            progress = progress,
            cached = cached,
            cachedByParent = cachedByParent,
            affectedDirectoryIds = affectedDirectoryIds,
            visitedDirectories = mutableSetOf(),
        )
    }

    private suspend fun indexLocalIncrementalDirectory(
        directory: File,
        parentId: String?,
        parentPath: String,
        depth: Int,
        result: MutableList<ProjectTreeEntry>,
        progress: IndexProgressReporter,
        cached: CachedProjectIndex,
        cachedByParent: Map<String?, List<ProjectTreeEntry>>,
        affectedDirectoryIds: Set<String>,
        visitedDirectories: MutableSet<String>,
    ) {
        currentCoroutineContext().ensureActive()
        val canonicalPath = runCatching { directory.canonicalPath }.getOrElse { directory.absolutePath }
        if (!visitedDirectories.add(canonicalPath)) return
        listLocalChildren(directory).forEach { source ->
            val path = if (parentPath.isEmpty()) source.name else "$parentPath/${source.name}"
            result += ProjectTreeEntry(source, path, parentId, depth)
            progress.record(source)
            if (!source.isDirectory) return@forEach

            val cachedFingerprint = cached.directoryFingerprints[source.id]
            if (source.id !in affectedDirectoryIds &&
                cachedFingerprint != null &&
                directoryFingerprint(source.location) == cachedFingerprint
            ) {
                reuseCachedSubtree(source.id, cached, cachedByParent, result, progress)
            } else {
                indexLocalIncrementalDirectory(
                    directory = (source.location as EntryLocation.Local).file,
                    parentId = source.id,
                    parentPath = path,
                    depth = depth + 1,
                    result = result,
                    progress = progress,
                    cached = cached,
                    cachedByParent = cachedByParent,
                    affectedDirectoryIds = affectedDirectoryIds,
                    visitedDirectories = visitedDirectories,
                )
            }
        }
    }

    private suspend fun reuseCachedSubtree(
        parentId: String,
        cached: CachedProjectIndex,
        cachedByParent: Map<String?, List<ProjectTreeEntry>>,
        result: MutableList<ProjectTreeEntry>,
        progress: IndexProgressReporter,
    ) {
        currentCoroutineContext().ensureActive()
        cachedByParent[parentId].orEmpty().forEach { entry ->
            result += entry
            progress.recordCached(entry.source, cached.directoryFingerprints[entry.source.id])
            if (entry.source.isDirectory) {
                reuseCachedSubtree(entry.source.id, cached, cachedByParent, result, progress)
            }
        }
    }

    private suspend fun indexLocal(
        directory: File,
        parentId: String?,
        parentPath: String,
        depth: Int,
        result: MutableList<ProjectTreeEntry>,
        visitedDirectories: MutableSet<String>,
        progress: IndexProgressReporter,
    ) {
        currentCoroutineContext().ensureActive()
        // Git 工程可能包含指向父级的目录符号链接；按规范路径去重可以完整索引正常文件，又不会递归成环。 @author long
        val canonicalPath = runCatching { directory.canonicalPath }.getOrElse { directory.absolutePath }
        if (!visitedDirectories.add(canonicalPath)) return
        listLocalChildren(directory).forEach { source ->
            val path = if (parentPath.isEmpty()) source.name else "$parentPath/${source.name}"
            result += ProjectTreeEntry(source, path, parentId, depth)
            progress.record(source)
            if (source.isDirectory) {
                indexLocal(
                    (source.location as EntryLocation.Local).file,
                    source.id,
                    path,
                    depth + 1,
                    result,
                    visitedDirectories,
                    progress,
                )
            }
        }
    }

    private suspend fun indexSaf(
        directory: DocumentFile,
        parentId: String?,
        parentPath: String,
        depth: Int,
        result: MutableList<ProjectTreeEntry>,
        visitedDirectories: MutableSet<String>,
        progress: IndexProgressReporter,
    ) {
        currentCoroutineContext().ensureActive()
        if (!visitedDirectories.add(directory.uri.toString())) return
        directory.listFiles()
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name.orEmpty().lowercase() })
            .forEach { child ->
            val source = child.toSourceEntry()
            val path = if (parentPath.isEmpty()) source.name else "$parentPath/${source.name}"
            result += ProjectTreeEntry(source, path, parentId, depth)
            progress.record(source)
            if (source.isDirectory) {
                // 直接递归 listFiles() 返回的子节点，避免 fromTreeUri(childUri) 重建成整棵树的根目录。
                indexSaf(child, source.id, path, depth + 1, result, visitedDirectories, progress)
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
            val maxBytes = memoryBudgetProvider.current().maxWholeFileBytes
            require(total <= maxBytes) {
                "文件超过 ${maxBytes / (1024 * 1024)} MB，当前设备暂不支持整文件编辑"
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val LARGE_FILE_PAGE_CHARACTERS = 256 * 1024
        const val SEARCH_PAGE_CHARACTERS = 256 * 1024
        const val FILE_SEARCH_PROGRESS_LINE_BATCH = 512
        const val INDEX_PROGRESS_BATCH = 32
        const val UNKNOWN_FILE_SIZE = -1L
        const val MAX_HITS_PER_FILE = 8
        val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    }
}
