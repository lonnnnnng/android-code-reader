package com.lonnnnnng.codereader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.codereader.data.DocumentRepository
import com.lonnnnnng.codereader.data.ProjectImporter
import com.lonnnnnng.codereader.data.RecentProjectCodec
import com.lonnnnnng.codereader.data.RecentProjectRecord
import com.lonnnnnng.codereader.domain.IndexedProjectEntry
import com.lonnnnnng.codereader.domain.MarkdownHeading
import com.lonnnnnng.codereader.domain.MarkdownOutlineParser
import com.lonnnnnng.codereader.domain.ProjectIndex
import com.lonnnnnng.codereader.model.BrowserSnapshot
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.OpenDocument
import com.lonnnnnng.codereader.model.ProjectSearchResult
import com.lonnnnnng.codereader.model.ProjectTreeEntry
import com.lonnnnnng.codereader.model.ReaderTheme
import com.lonnnnnng.codereader.model.SourceEntry
import com.lonnnnnng.codereader.qa.SampleCatalog
import com.lonnnnnng.codereader.syntax.SyntaxCoverageResult
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** @author long */
enum class AppScreen { HOME, BROWSER, READER }

/** @author long */
enum class ReaderCommandType { SEARCH_FORWARD, SEARCH_BACKWARD, GOTO_LINE, MARKDOWN_HEADING }

/** 阅读器命令使用递增 id，保证连续点击“下一个”时 Compose 仍会把命令交给原生视图。 @author long */
data class ReaderCommand(
    val id: Long,
    val type: ReaderCommandType,
    val query: String = "",
    val line: Int = 1,
    val headingIndex: Int = 0,
)

/** @author long */
data class ReaderSettings(
    val fontSizeSp: Float = 14f,
    val wordWrap: Boolean = false,
)

/** 每个标签页独立保存草稿和预览状态，切换文件不会丢失未保存内容。 @author long */
data class ReaderTabState(
    val document: OpenDocument,
    val draftText: String = document.text,
    val editable: Boolean = false,
    val dirty: Boolean = false,
    val markdownPreview: Boolean = document.fileType.markdown,
)

/** @author long */
data class ReaderUiState(
    val screen: AppScreen = AppScreen.HOME,
    val busy: Boolean = false,
    val message: String? = null,
    val browser: BrowserSnapshot? = null,
    val projectRoot: EntryLocation? = null,
    val projectEntries: List<ProjectTreeEntry> = emptyList(),
    val expandedDirectoryIds: Set<String> = emptySet(),
    val projectSearchQuery: String = "",
    val projectSearchResults: List<ProjectSearchResult> = emptyList(),
    val recentProjects: List<RecentProjectRecord> = emptyList(),
    val tabs: List<ReaderTabState> = emptyList(),
    val activeTabId: String? = null,
    val readerCommand: ReaderCommand? = null,
    val syntaxCoverage: SyntaxCoverageResult? = null,
    val theme: ReaderTheme = ReaderTheme.HIGH_CONTRAST_LIGHT,
    val settings: ReaderSettings = ReaderSettings(),
) {
    val activeTab: ReaderTabState? get() = tabs.firstOrNull { it.document.id == activeTabId }
    val document: OpenDocument? get() = activeTab?.document
    val draftText: String get() = activeTab?.draftText.orEmpty()
    val editable: Boolean get() = activeTab?.editable == true
    val dirty: Boolean get() = activeTab?.dirty == true
    val markdownPreview: Boolean get() = activeTab?.markdownPreview == true
    val markdownHeadings: List<MarkdownHeading>
        get() = if (document?.fileType?.markdown == true) MarkdownOutlineParser.parse(draftText) else emptyList()

    val visibleProjectEntries: List<ProjectTreeEntry>
        get() {
            val indexed = projectEntries.map {
                IndexedProjectEntry(it.source.id, it.parentId, it.path, it.depth, it.source.isDirectory)
            }
            val visibleIds = ProjectIndex.visible(indexed, expandedDirectoryIds).mapTo(linkedSetOf()) { it.id }
            return projectEntries.filter { it.source.id in visibleIds }
        }
}

/**
 * 统一管理来源、项目树、标签页和阅读命令，保证目录、ZIP、Git 与外部文件共享同一套阅读行为。
 *
 * @author long
 */
class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Application.MODE_PRIVATE)
    private val repository = DocumentRepository(application)
    private val importer = ProjectImporter(application)
    private val commandIds = AtomicLong()

    private val initialTheme = ReaderTheme.fromPreference(preferences.getString(KEY_THEME, null))
    private val initialSettings = ReaderSettings(
        fontSizeSp = preferences.getFloat(KEY_FONT_SIZE, 14f).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
        wordWrap = preferences.getBoolean(KEY_WORD_WRAP, false),
    )
    private val initialRecentProjects = RecentProjectCodec.decode(preferences.getString(KEY_RECENT_PROJECTS, null))
    private val _state = MutableStateFlow(
        ReaderUiState(
            theme = initialTheme,
            settings = initialSettings,
            recentProjects = initialRecentProjects,
        ),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                SyntaxRegistry.initialize(getApplication())
                SyntaxRegistry.setTheme(getApplication(), initialTheme)
            }.onFailure(::showError)
        }
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null || intent.action == Intent.ACTION_MAIN) return
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> intent.clipData?.getItemAt(0)?.uri
                ?: IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data
            else -> null
        } ?: return
        openUri(uri)
    }

    fun openUri(uri: Uri) = launchBusy {
        openDocument(repository.openUri(uri))
    }

    fun openSafTree(uri: Uri) = launchBusy {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        openProjectRoot(
            root = EntryLocation.Saf(uri),
            snapshot = repository.listRoot(uri),
            recent = RecentProjectRecord("saf", repository.listRoot(uri).title, uri.toString()),
        )
    }

    fun importZip(uri: Uri) = launchBusy {
        openLocalRoot(importer.importZip(uri), rememberRecent = true)
    }

    fun cloneGit(url: String) = launchBusy {
        openLocalRoot(importer.cloneGit(url), rememberRecent = true)
    }

    fun openSamples() = launchBusy {
        openLocalRoot(importer.prepareSamples(), rememberRecent = false)
    }

    fun openRecentProject(project: RecentProjectRecord) = launchBusy {
        when (project.kind) {
            "saf" -> {
                val uri = Uri.parse(project.value)
                openProjectRoot(EntryLocation.Saf(uri), repository.listRoot(uri), project)
            }
            "local" -> {
                val directory = File(project.value)
                require(directory.isDirectory) { "最近项目已经不存在：${project.title}" }
                openLocalRoot(directory, rememberRecent = true)
            }
            else -> error("无法识别最近项目来源")
        }
    }

    fun removeRecentProject(project: RecentProjectRecord) {
        persistRecentProjects(_state.value.recentProjects.filterNot { it == project })
    }

    fun runSyntaxCoverage() = launchBusy {
        val report = SyntaxRegistry.verify(getApplication(), SampleCatalog.all)
        _state.update {
            it.copy(
                syntaxCoverage = report,
                message = if (report.isSuccess) "语法覆盖验证通过：${report.passed}/${report.total}" else "语法覆盖存在失败",
            )
        }
    }

    fun openEntry(entry: SourceEntry) {
        if (entry.isDirectory) {
            toggleDirectory(entry.id)
            return
        }
        launchBusy { openDocument(openSource(entry)) }
    }

    fun toggleDirectory(id: String) {
        _state.update { current ->
            val expanded = current.expandedDirectoryIds.toMutableSet()
            if (!expanded.add(id)) expanded.remove(id)
            current.copy(expandedDirectoryIds = expanded)
        }
    }

    fun searchProject(query: String) {
        val normalizedQuery = query.trim()
        _state.update { it.copy(projectSearchQuery = normalizedQuery) }
        if (normalizedQuery.isBlank()) {
            _state.update { it.copy(projectSearchResults = emptyList()) }
            return
        }
        launchBusy {
            val results = repository.searchProject(_state.value.projectEntries, normalizedQuery)
            // IO 搜索结束时用户可能已换了关键词，旧请求不得覆盖新结果。
            _state.update { current ->
                if (current.projectSearchQuery == normalizedQuery) current.copy(projectSearchResults = results) else current
            }
        }
    }

    fun openSearchResult(result: ProjectSearchResult) = launchBusy {
        var document = openSource(result.source)
        while (document.largeFile && document.hasMore && document.text.lineSequence().count() < result.line) {
            val page = repository.loadMore(document)
            val updatedText = document.text + page.text
            document = document.copy(
                text = updatedText,
                loadedCharacters = page.nextCharacter,
                hasMore = page.hasMore,
            )
        }
        openDocument(document, initialLine = result.line)
    }

    fun switchTab(id: String) {
        if (_state.value.tabs.any { it.document.id == id }) {
            _state.update { it.copy(screen = AppScreen.READER, activeTabId = id, readerCommand = null) }
        }
    }

    fun closeTab(id: String) {
        _state.update { current ->
            val index = current.tabs.indexOfFirst { it.document.id == id }
            if (index < 0) return@update current
            val remaining = current.tabs.toMutableList().apply { removeAt(index) }
            val nextId = if (current.activeTabId == id) {
                remaining.getOrNull(index.coerceAtMost(remaining.lastIndex))?.document?.id
            } else {
                current.activeTabId
            }
            current.copy(
                tabs = remaining,
                activeTabId = nextId,
                screen = if (nextId == null) if (current.browser != null) AppScreen.BROWSER else AppScreen.HOME else current.screen,
            )
        }
    }

    fun setEditable(enabled: Boolean) {
        val document = _state.value.document ?: return
        if (enabled && !document.canWrite) {
            _state.update { it.copy(message = if (document.largeFile) "大文件分段模式只允许读取" else "当前来源只允许读取，无法进入编辑模式") }
            return
        }
        updateActiveTab { it.copy(editable = enabled) }
    }

    fun updateDraft(text: String) {
        updateActiveTab { tab -> if (tab.draftText == text) tab else tab.copy(draftText = text, dirty = true) }
    }

    fun save() {
        val tab = _state.value.activeTab ?: return
        if (!tab.dirty) return
        launchBusy {
            repository.save(tab.document, tab.draftText)
            updateActiveTab { it.copy(dirty = false, document = it.document.copy(text = it.draftText)) }
            _state.update { it.copy(message = "已保存 ${tab.document.name}") }
        }
    }

    fun loadMore() {
        val tab = _state.value.activeTab ?: return
        if (!tab.document.hasMore) return
        launchBusy {
            val page = repository.loadMore(tab.document)
            updateActiveTab {
                val updatedText = it.draftText + page.text
                it.copy(
                    document = it.document.copy(
                        text = updatedText,
                        loadedCharacters = page.nextCharacter,
                        hasMore = page.hasMore,
                    ),
                    draftText = updatedText,
                )
            }
        }
    }

    fun toggleMarkdownPreview() {
        updateActiveTab { it.copy(markdownPreview = !it.markdownPreview) }
    }

    fun searchInFile(query: String, forward: Boolean) {
        if (query.isBlank()) return
        dispatchCommand(
            ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = if (forward) ReaderCommandType.SEARCH_FORWARD else ReaderCommandType.SEARCH_BACKWARD,
                query = query,
            ),
        )
    }

    fun gotoLine(line: Int) {
        dispatchCommand(ReaderCommand(commandIds.incrementAndGet(), ReaderCommandType.GOTO_LINE, line = line.coerceAtLeast(1)))
    }

    fun gotoMarkdownHeading(index: Int) {
        dispatchCommand(ReaderCommand(commandIds.incrementAndGet(), ReaderCommandType.MARKDOWN_HEADING, headingIndex = index))
    }

    fun setFontSize(size: Float) {
        val normalized = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        preferences.edit().putFloat(KEY_FONT_SIZE, normalized).apply()
        _state.update { it.copy(settings = it.settings.copy(fontSizeSp = normalized)) }
    }

    fun setWordWrap(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_WORD_WRAP, enabled).apply()
        _state.update { it.copy(settings = it.settings.copy(wordWrap = enabled)) }
    }

    fun toggleTheme() {
        setTheme(_state.value.theme.toggled())
    }

    fun setTheme(theme: ReaderTheme) {
        runCatching {
            // 先更新代码区主题，再发布 Compose 状态，保证同一帧内外壳和源码区域保持一致。
            SyntaxRegistry.setTheme(getApplication(), theme)
            preferences.edit().putString(KEY_THEME, theme.preferenceValue).apply()
            _state.update { it.copy(theme = theme) }
        }.onFailure(::showError)
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun navigateBack(): Boolean = when (_state.value.screen) {
        AppScreen.READER -> {
            _state.update { it.copy(screen = if (it.browser != null) AppScreen.BROWSER else AppScreen.HOME) }
            true
        }
        AppScreen.BROWSER -> {
            _state.update {
                it.copy(
                    screen = AppScreen.HOME,
                    browser = null,
                    projectRoot = null,
                    projectEntries = emptyList(),
                    expandedDirectoryIds = emptySet(),
                    projectSearchResults = emptyList(),
                    projectSearchQuery = "",
                )
            }
            true
        }
        AppScreen.HOME -> false
    }

    private suspend fun openLocalRoot(directory: File, rememberRecent: Boolean) {
        val snapshot = repository.listLocalRoot(directory)
        openProjectRoot(
            root = EntryLocation.Local(directory),
            snapshot = snapshot,
            recent = if (rememberRecent) RecentProjectRecord("local", snapshot.title, directory.absolutePath) else null,
        )
    }

    private suspend fun openProjectRoot(
        root: EntryLocation,
        snapshot: BrowserSnapshot,
        recent: RecentProjectRecord?,
    ) {
        val index = repository.indexProject(root)
        if (recent != null) rememberRecentProject(recent)
        _state.update {
            it.copy(
                screen = AppScreen.BROWSER,
                browser = snapshot,
                projectRoot = root,
                projectEntries = index,
                expandedDirectoryIds = emptySet(),
                projectSearchQuery = "",
                projectSearchResults = emptyList(),
                message = if (index.size >= MAX_PROJECT_ENTRIES) "项目较大，仅索引前 $MAX_PROJECT_ENTRIES 个条目" else null,
            )
        }
    }

    private suspend fun openSource(source: SourceEntry): OpenDocument = when (val location = source.location) {
        is EntryLocation.Saf -> repository.openUri(location.uri, source.name)
        is EntryLocation.Local -> repository.openLocal(location.file)
    }

    private fun openDocument(document: OpenDocument, initialLine: Int? = null) {
        _state.update { current ->
            val existing = current.tabs.firstOrNull { it.document.id == document.id }
            val tabs = if (existing == null) {
                current.tabs + ReaderTabState(
                    document = document,
                    markdownPreview = document.fileType.markdown && initialLine == null,
                )
            } else {
                current.tabs.map { tab ->
                    if (tab.document.id != document.id) return@map tab
                    var updated = tab
                    if (document.largeFile && document.loadedCharacters > tab.document.loadedCharacters) {
                        updated = updated.copy(document = document, draftText = document.text)
                    }
                    if (initialLine != null && document.fileType.markdown) {
                        // Markdown 预览没有源码行号，全局搜索命中时必须切回源码再定位。
                        updated = updated.copy(markdownPreview = false)
                    }
                    updated
                }
            }
            current.copy(
                screen = AppScreen.READER,
                tabs = tabs,
                activeTabId = document.id,
                message = null,
                readerCommand = initialLine?.let {
                    ReaderCommand(commandIds.incrementAndGet(), ReaderCommandType.GOTO_LINE, line = it)
                },
            )
        }
    }

    private fun updateActiveTab(transform: (ReaderTabState) -> ReaderTabState) {
        _state.update { current ->
            val activeId = current.activeTabId ?: return@update current
            current.copy(tabs = current.tabs.map { if (it.document.id == activeId) transform(it) else it })
        }
    }

    private fun dispatchCommand(command: ReaderCommand) {
        _state.update { it.copy(readerCommand = command) }
    }

    private fun rememberRecentProject(project: RecentProjectRecord) {
        val updated = listOf(project) + _state.value.recentProjects.filterNot { it.kind == project.kind && it.value == project.value }
        persistRecentProjects(updated.take(MAX_RECENT_PROJECTS))
    }

    private fun persistRecentProjects(projects: List<RecentProjectRecord>) {
        preferences.edit().putString(KEY_RECENT_PROJECTS, RecentProjectCodec.encode(projects)).apply()
        _state.update { it.copy(recentProjects = projects) }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            runCatching { block() }.onFailure(::showError)
            _state.update { it.copy(busy = false) }
        }
    }

    private fun showError(error: Throwable) {
        _state.update { it.copy(message = error.message ?: error.javaClass.simpleName) }
    }

    private companion object {
        const val PREFERENCES_NAME = "reader_preferences"
        const val KEY_THEME = "reader_theme"
        const val KEY_FONT_SIZE = "reader_font_size"
        const val KEY_WORD_WRAP = "reader_word_wrap"
        const val KEY_RECENT_PROJECTS = "recent_projects"
        const val MIN_FONT_SIZE = 11f
        const val MAX_FONT_SIZE = 24f
        const val MAX_RECENT_PROJECTS = 6
        const val MAX_PROJECT_ENTRIES = 5_000
    }
}
