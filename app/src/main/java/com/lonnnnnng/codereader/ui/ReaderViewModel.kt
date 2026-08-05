package com.lonnnnnng.codereader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.codereader.BuildConfig
import com.lonnnnnng.codereader.data.DocumentRepository
import com.lonnnnnng.codereader.data.GitOperationCancelledException
import com.lonnnnnng.codereader.data.GitOperationProgressMonitor
import com.lonnnnnng.codereader.data.ProjectImporter
import com.lonnnnnng.codereader.data.RecentProjectCodec
import com.lonnnnnng.codereader.data.RecentProjectPolicy
import com.lonnnnnng.codereader.data.RecentProjectRecord
import com.lonnnnnng.codereader.domain.IndexedProjectEntry
import com.lonnnnnng.codereader.domain.MarkdownHeading
import com.lonnnnnng.codereader.domain.MarkdownOutlineParser
import com.lonnnnnng.codereader.domain.ProjectIndex
import com.lonnnnnng.codereader.model.AppColorPalette
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.OpenDocument
import com.lonnnnnng.codereader.model.ProjectSearchResult
import com.lonnnnnng.codereader.model.ProjectTreeEntry
import com.lonnnnnng.codereader.model.ReaderBackground
import com.lonnnnnng.codereader.model.ReaderTheme
import com.lonnnnnng.codereader.model.SourceEntry
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import com.lonnnnnng.codereader.update.AppRelease
import com.lonnnnnng.codereader.update.AppUpdateInstaller
import com.lonnnnnng.codereader.update.AppUpdateRepository
import com.lonnnnnng.codereader.update.isNewerVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** @author long */
enum class AppScreen { HOME, RECENT, SETTINGS, BROWSER, READER }

/** 设置采用一级分类、二级详情，返回时先退回分类页再离开设置。 @author long */
enum class SettingsPage { ROOT, READING, APPEARANCE, UPDATE }

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
    val background: ReaderBackground = ReaderBackground.FOLLOW_THEME,
    val appPalette: AppColorPalette = AppColorPalette.EMERALD,
)

/** 长耗时操作在全局遮罩中持续展示阶段、进度和取消能力，避免用户误以为应用没有响应。 @author long */
enum class ReaderOperationKind { GENERAL, GIT }

/** @author long */
data class ReaderOperationState(
    val title: String,
    val detail: String? = null,
    val progressPercent: Int? = null,
    val cancellable: Boolean = false,
    val kind: ReaderOperationKind = ReaderOperationKind.GENERAL,
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
    val settingsPage: SettingsPage = SettingsPage.ROOT,
    val operation: ReaderOperationState? = null,
    val message: String? = null,
    val browserTitle: String? = null,
    val browserBackTarget: AppScreen = AppScreen.HOME,
    val gitRepositoryRoot: String? = null,
    val projectEntries: List<ProjectTreeEntry> = emptyList(),
    val expandedDirectoryIds: Set<String> = emptySet(),
    val projectSearchQuery: String = "",
    val projectSearchResults: List<ProjectSearchResult> = emptyList(),
    val projectSearchInProgress: Boolean = false,
    val projectSearchError: String? = null,
    val recentProjects: List<RecentProjectRecord> = emptyList(),
    val tabs: List<ReaderTabState> = emptyList(),
    val activeTabId: String? = null,
    val readerCommand: ReaderCommand? = null,
    val theme: ReaderTheme = ReaderTheme.HIGH_CONTRAST_LIGHT,
    val settings: ReaderSettings = ReaderSettings(),
    val appUpdate: AppUpdateUiState = AppUpdateUiState(),
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
    private val updateRepository = AppUpdateRepository(application)
    private val commandIds = AtomicLong()
    private val updateOperationActive = AtomicBoolean(false)
    @Volatile private var activeGitMonitor: GitOperationProgressMonitor? = null

    private val initialTheme = ReaderTheme.fromPreference(preferences.getString(KEY_THEME, null))
    private val initialSettings = ReaderSettings(
        fontSizeSp = preferences.getFloat(KEY_FONT_SIZE, 14f).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
        wordWrap = preferences.getBoolean(KEY_WORD_WRAP, false),
        background = ReaderBackground.fromPreference(preferences.getString(KEY_READER_BACKGROUND, null)),
        appPalette = AppColorPalette.fromPreference(preferences.getString(KEY_APP_PALETTE, null)),
    )
    private val initialRecentProjects = RecentProjectPolicy.normalize(
        RecentProjectCodec.decode(preferences.getString(KEY_RECENT_PROJECTS, null)),
        MAX_RECENT_PROJECTS,
    )
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
        val title = repository.rootTitle(uri)
        openProjectRoot(
            root = EntryLocation.Saf(uri),
            title = title,
            recent = RecentProjectRecord("saf", title, uri.toString()),
        )
    }

    fun importZip(uri: Uri) = launchBusy {
        openLocalRoot(importer.importZip(uri), rememberRecent = true)
    }

    fun cloneGit(url: String) = launchGitOperation("正在克隆 Git 仓库") { monitor ->
        val directory = importer.cloneGit(url, monitor)
        updateOperationDetail("正在建立完整目录索引")
        openLocalRoot(directory, rememberRecent = true)
        "已克隆 ${directory.name}"
    }

    fun updateGitRepository() {
        val root = _state.value.gitRepositoryRoot?.let(::File)
        if (root == null || !importer.isGitRepository(root)) {
            _state.update { it.copy(message = "当前项目不是可更新的 Git 仓库") }
            return
        }
        if (_state.value.tabs.any { it.dirty && isDocumentInside(it.document, root) }) {
            _state.update { it.copy(message = "请先保存或关闭该仓库中未保存的文件，再获取最新代码") }
            return
        }

        launchGitOperation("正在获取最新代码") { monitor ->
            val result = importer.updateGit(root, monitor)
            if (result.updated) {
                updateOperationDetail("正在刷新完整目录索引")
                val index = repository.indexProject(EntryLocation.Local(root))
                _state.update { current ->
                    // 更新后的工作区文件可能已经改变，关闭该仓库的旧标签可避免继续显示拉取前的缓存内容。 @author long
                    val remainingTabs = current.tabs.filterNot { isDocumentInside(it.document, root) }
                    val activeId = current.activeTabId?.takeIf { id -> remainingTabs.any { it.document.id == id } }
                    current.copy(
                        projectEntries = index,
                        expandedDirectoryIds = emptySet(),
                        projectSearchQuery = "",
                        projectSearchResults = emptyList(),
                        projectSearchInProgress = false,
                        projectSearchError = null,
                        tabs = remainingTabs,
                        activeTabId = activeId,
                    )
                }
            }
            if (result.updated) "已获取最新代码" else "仓库已经是最新版本"
        }
    }

    fun cancelGitOperation() {
        activeGitMonitor?.cancel()
        _state.update { current ->
            val operation = current.operation ?: return@update current
            current.copy(operation = operation.copy(detail = "正在取消 Git 操作…", cancellable = false))
        }
    }

    fun openBundledProject(assetPath: String, targetName: String) = launchBusy {
        openLocalRoot(importer.prepareBundledProject(assetPath, targetName), rememberRecent = false)
    }

    fun openRecentProject(project: RecentProjectRecord) = launchBusy {
        when (project.kind) {
            "saf" -> {
                val uri = Uri.parse(project.value)
                val title = repository.rootTitle(uri)
                // SAF 目录可能在最近记录写入后被重命名，恢复成功时同步展示和持久化当前标题。
                openProjectRoot(EntryLocation.Saf(uri), title, project.copy(title = title))
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
        if (normalizedQuery.isBlank()) {
            _state.update {
                it.copy(
                    projectSearchQuery = "",
                    projectSearchResults = emptyList(),
                    projectSearchInProgress = false,
                    projectSearchError = null,
                )
            }
            return
        }

        _state.update {
            it.copy(
                projectSearchQuery = normalizedQuery,
                projectSearchResults = emptyList(),
                projectSearchInProgress = true,
                projectSearchError = null,
            )
        }
        viewModelScope.launch {
            try {
                val results = repository.searchProject(_state.value.projectEntries, normalizedQuery)
                // 用户可在搜索期间清空或提交新关键词，旧请求只能更新仍然匹配的查询。 @author long
                _state.update { current ->
                    if (current.projectSearchQuery == normalizedQuery) {
                        current.copy(projectSearchResults = results, projectSearchInProgress = false)
                    } else {
                        current
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update { current ->
                    if (current.projectSearchQuery == normalizedQuery) {
                        current.copy(
                            projectSearchInProgress = false,
                            projectSearchError = error.message ?: error.javaClass.simpleName,
                        )
                    } else {
                        current
                    }
                }
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
                screen = if (nextId == null) if (current.browserTitle != null) AppScreen.BROWSER else AppScreen.HOME else current.screen,
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

    fun setReaderBackground(background: ReaderBackground) {
        preferences.edit().putString(KEY_READER_BACKGROUND, background.preferenceValue).apply()
        _state.update { it.copy(settings = it.settings.copy(background = background)) }
    }

    fun setAppPalette(palette: AppColorPalette) {
        preferences.edit().putString(KEY_APP_PALETTE, palette.preferenceValue).apply()
        _state.update { it.copy(settings = it.settings.copy(appPalette = palette)) }
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

    fun openSettings() {
        _state.update { it.copy(screen = AppScreen.SETTINGS, settingsPage = SettingsPage.ROOT) }
    }

    fun openRecentProjects() {
        _state.update { it.copy(screen = AppScreen.RECENT) }
    }

    fun openSettingsPage(page: SettingsPage) {
        _state.update { it.copy(settingsPage = page) }
    }

    fun checkForUpdate() {
        // 检查和下载共用同一把原子门闩，避免快速连点让多个协程覆盖状态或同时写入更新缓存。
        if (!updateOperationActive.compareAndSet(false, true)) return
        _state.update {
            it.copy(
                appUpdate = AppUpdateUiState(phase = AppUpdatePhase.CHECKING),
            )
        }
        viewModelScope.launch {
            try {
                runCatching { updateRepository.fetchLatestRelease() }
                    .onSuccess { release ->
                        val available = isNewerVersion(release.versionName, BuildConfig.VERSION_NAME)
                        _state.update {
                            it.copy(
                                appUpdate = AppUpdateUiState(
                                    phase = if (available) AppUpdatePhase.AVAILABLE else AppUpdatePhase.UP_TO_DATE,
                                    release = release,
                                    dialogVisible = available,
                                ),
                            )
                        }
                    }
                    .onFailure { error -> updateFailure("检查更新失败", error, release = null) }
            } finally {
                updateOperationActive.set(false)
            }
        }
    }

    fun downloadUpdate() {
        val release = _state.value.appUpdate.release ?: return
        if (!updateOperationActive.compareAndSet(false, true)) return
        _state.update {
            it.copy(
                appUpdate = it.appUpdate.copy(
                    phase = AppUpdatePhase.DOWNLOADING,
                    progressPercent = 0,
                    downloadedApk = null,
                    errorMessage = null,
                    dialogVisible = true,
                ),
            )
        }
        viewModelScope.launch {
            try {
                runCatching {
                    val apk = updateRepository.downloadAndVerify(release) { progress ->
                        _state.update { current ->
                            val update = current.appUpdate
                            if (update.phase == AppUpdatePhase.DOWNLOADING && update.release?.tagName == release.tagName) {
                                current.copy(appUpdate = update.copy(progressPercent = progress))
                            } else {
                                current
                            }
                        }
                    }
                    try {
                        AppUpdateInstaller.validateDownloadedApk(getApplication(), release, apk)
                        apk
                    } catch (error: Exception) {
                        // 未通过身份校验的 APK 不能留给后续安装操作，也不应占用更新缓存。
                        apk.delete()
                        throw error
                    }
                }.onSuccess { apk ->
                    _state.update {
                        it.copy(
                            appUpdate = it.appUpdate.copy(
                                phase = AppUpdatePhase.READY,
                                progressPercent = 100,
                                downloadedApk = apk,
                                dialogVisible = true,
                            ),
                        )
                    }
                }.onFailure { error -> updateFailure("下载更新失败", error, release) }
            } finally {
                updateOperationActive.set(false)
            }
        }
    }

    fun showUpdateDetails() {
        _state.update { current ->
            if (current.appUpdate.release == null) current else current.copy(appUpdate = current.appUpdate.copy(dialogVisible = true))
        }
    }

    fun dismissUpdateDetails() {
        _state.update { it.copy(appUpdate = it.appUpdate.copy(dialogVisible = false)) }
    }

    fun reportUpdateMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun navigateBack(): Boolean = when (_state.value.screen) {
        AppScreen.READER -> {
            _state.update { it.copy(screen = if (it.browserTitle != null) AppScreen.BROWSER else AppScreen.HOME) }
            true
        }
        AppScreen.BROWSER -> {
            _state.update {
                it.copy(
                    screen = it.browserBackTarget,
                    browserTitle = null,
                    browserBackTarget = AppScreen.HOME,
                    gitRepositoryRoot = null,
                    projectEntries = emptyList(),
                    expandedDirectoryIds = emptySet(),
                    projectSearchResults = emptyList(),
                    projectSearchQuery = "",
                    projectSearchInProgress = false,
                    projectSearchError = null,
                )
            }
            true
        }
        AppScreen.SETTINGS -> {
            _state.update {
                if (it.settingsPage == SettingsPage.ROOT) {
                    it.copy(screen = AppScreen.HOME)
                } else {
                    it.copy(settingsPage = SettingsPage.ROOT)
                }
            }
            true
        }
        AppScreen.RECENT -> {
            _state.update { it.copy(screen = AppScreen.HOME) }
            true
        }
        AppScreen.HOME -> false
    }

    private suspend fun openLocalRoot(directory: File, rememberRecent: Boolean) {
        val title = repository.localRootTitle(directory)
        openProjectRoot(
            root = EntryLocation.Local(directory),
            title = title,
            recent = if (rememberRecent) RecentProjectRecord("local", title, directory.absolutePath) else null,
        )
    }

    private suspend fun openProjectRoot(
        root: EntryLocation,
        title: String,
        recent: RecentProjectRecord?,
    ) {
        val index = repository.indexProject(root)
        val gitRoot = (root as? EntryLocation.Local)
            ?.file
            ?.takeIf(importer::isGitRepository)
            ?.absolutePath
        if (recent != null) rememberRecentProject(recent)
        _state.update {
            // 从最近打开进入项目后，返回必须回到列表，避免用户丢失刚才浏览历史的位置。
            val backTarget = if (it.screen == AppScreen.RECENT) AppScreen.RECENT else AppScreen.HOME
            it.copy(
                screen = AppScreen.BROWSER,
                browserTitle = title,
                browserBackTarget = backTarget,
                gitRepositoryRoot = gitRoot,
                projectEntries = index,
                expandedDirectoryIds = emptySet(),
                projectSearchQuery = "",
                projectSearchResults = emptyList(),
                projectSearchInProgress = false,
                projectSearchError = null,
                message = null,
            )
        }
    }

    private suspend fun openSource(source: SourceEntry): OpenDocument = when (val location = source.location) {
        is EntryLocation.Saf -> repository.openUri(location.uri, source.name)
        is EntryLocation.Local -> repository.openLocal(location.file)
    }

    private fun isDocumentInside(document: OpenDocument, root: File): Boolean {
        val file = (document.location as? EntryLocation.Local)?.file ?: return false
        return runCatching {
            val rootPath = root.canonicalPath
            val filePath = file.canonicalPath
            filePath == rootPath || filePath.startsWith(rootPath + File.separator)
        }.getOrDefault(false)
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
        val updated = RecentProjectPolicy.normalize(
            listOf(project) + _state.value.recentProjects,
            MAX_RECENT_PROJECTS,
        )
        persistRecentProjects(updated)
    }

    private fun persistRecentProjects(projects: List<RecentProjectRecord>) {
        preferences.edit().putString(KEY_RECENT_PROJECTS, RecentProjectCodec.encode(projects)).apply()
        _state.update { it.copy(recentProjects = projects) }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(operation = ReaderOperationState("正在处理"), message = null) }
            try {
                block()
            } catch (error: Throwable) {
                showError(error)
            } finally {
                _state.update { it.copy(operation = null) }
            }
        }
    }

    private fun launchGitOperation(
        title: String,
        block: suspend (GitOperationProgressMonitor) -> String,
    ) {
        viewModelScope.launch {
            val monitor = GitOperationProgressMonitor { progress ->
                _state.update { current ->
                    val operation = current.operation ?: return@update current
                    current.copy(
                        operation = operation.copy(
                            detail = progress.detail,
                            progressPercent = progress.percent,
                        ),
                    )
                }
            }
            activeGitMonitor = monitor
            _state.update {
                it.copy(
                    operation = ReaderOperationState(
                        title = title,
                        detail = "正在连接远程仓库",
                        cancellable = true,
                        kind = ReaderOperationKind.GIT,
                    ),
                    message = null,
                )
            }
            try {
                val successMessage = block(monitor)
                _state.update { it.copy(message = successMessage) }
            } catch (cancelled: GitOperationCancelledException) {
                _state.update { it.copy(message = cancelled.message) }
            } catch (error: Throwable) {
                showError(error)
            } finally {
                if (activeGitMonitor === monitor) activeGitMonitor = null
                _state.update { it.copy(operation = null) }
            }
        }
    }

    private fun updateOperationDetail(detail: String) {
        _state.update { current ->
            val operation = current.operation ?: return@update current
            current.copy(operation = operation.copy(detail = detail, progressPercent = null, cancellable = false))
        }
    }

    private fun showError(error: Throwable) {
        _state.update { it.copy(message = error.message ?: error.javaClass.simpleName) }
    }

    private fun updateFailure(prefix: String, error: Throwable, release: AppRelease?) {
        val detail = error.message ?: error.javaClass.simpleName
        val message = "$prefix：$detail"
        _state.update {
            it.copy(
                message = message,
                appUpdate = AppUpdateUiState(
                    phase = AppUpdatePhase.FAILED,
                    release = release,
                    errorMessage = message,
                ),
            )
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "reader_preferences"
        const val KEY_THEME = "reader_theme"
        const val KEY_FONT_SIZE = "reader_font_size"
        const val KEY_WORD_WRAP = "reader_word_wrap"
        const val KEY_READER_BACKGROUND = "reader_background"
        const val KEY_APP_PALETTE = "app_color_palette"
        const val KEY_RECENT_PROJECTS = "recent_projects"
        const val MIN_FONT_SIZE = 11f
        const val MAX_FONT_SIZE = 24f
        const val MAX_RECENT_PROJECTS = 6
    }
}
