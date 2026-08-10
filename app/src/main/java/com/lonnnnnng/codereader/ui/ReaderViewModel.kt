package com.lonnnnnng.codereader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lonnnnnng.codereader.BuildConfig
import com.lonnnnnng.codereader.data.DocumentRepository
import com.lonnnnnng.codereader.data.DocumentExportProgress
import com.lonnnnnng.codereader.data.DocumentSaveException
import com.lonnnnnng.codereader.data.DocumentDraft
import com.lonnnnnng.codereader.data.DraftFingerprint
import com.lonnnnnng.codereader.data.DraftStore
import com.lonnnnnng.codereader.data.GitOperationCancelledException
import com.lonnnnnng.codereader.data.GitOperationProgressMonitor
import com.lonnnnnng.codereader.data.GitRepositoryManager
import com.lonnnnnng.codereader.data.GitUpdatePreview
import com.lonnnnnng.codereader.data.GitUpdateRejectedException
import com.lonnnnnng.codereader.data.GitUpdateRelation
import com.lonnnnnng.codereader.data.ProjectImporter
import com.lonnnnnng.codereader.data.ReadingDocumentState
import com.lonnnnnng.codereader.data.ReadingStateCodec
import com.lonnnnnng.codereader.data.ReadingStatePolicy
import com.lonnnnnng.codereader.data.RecentProjectCodec
import com.lonnnnnng.codereader.data.RecentProjectPolicy
import com.lonnnnnng.codereader.data.RecentProjectRecord
import com.lonnnnnng.codereader.domain.IndexedProjectEntry
import com.lonnnnnng.codereader.domain.MarkdownHeading
import com.lonnnnnng.codereader.domain.MarkdownOutlineParser
import com.lonnnnnng.codereader.domain.ProjectIndex
import com.lonnnnnng.codereader.domain.ProjectSearchOptions
import com.lonnnnnng.codereader.domain.ProjectSearchScope
import com.lonnnnnng.codereader.domain.TextSearchMatcher
import com.lonnnnnng.codereader.domain.TextSearchLineCollector
import com.lonnnnnng.codereader.domain.TextSearchOptions
import com.lonnnnnng.codereader.domain.TextSearchPage
import com.lonnnnnng.codereader.domain.TextSearchPosition
import com.lonnnnnng.codereader.domain.TextSearchProgress
import com.lonnnnnng.codereader.model.AppColorPalette
import com.lonnnnnng.codereader.model.BinaryFileException
import com.lonnnnnng.codereader.model.BinaryFileInfo
import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.OpenDocument
import com.lonnnnnng.codereader.model.ProjectIndexProgress
import com.lonnnnnng.codereader.model.ProjectSearchResult
import com.lonnnnnng.codereader.model.ProjectTreeEntry
import com.lonnnnnng.codereader.model.ReaderBackground
import com.lonnnnnng.codereader.model.ReaderTheme
import com.lonnnnnng.codereader.model.SourceEntry
import com.lonnnnnng.codereader.model.TextEncoding
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import com.lonnnnnng.codereader.update.AppRelease
import com.lonnnnnng.codereader.update.AppUpdateInstaller
import com.lonnnnnng.codereader.update.AppUpdateRepository
import com.lonnnnnng.codereader.update.isNewerVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** SAF URI 必须按 documentId 的路径边界判断，不能把 `Code` 与 `CodeBackup` 视为同一子树。 @author long */
internal fun isSafDocumentInsideTree(documentUri: Uri, treeUri: Uri): Boolean = runCatching {
    if (documentUri.scheme != treeUri.scheme || documentUri.authority != treeUri.authority) return@runCatching false
    val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
    val documentId = runCatching { DocumentsContract.getDocumentId(documentUri) }
        .getOrElse { DocumentsContract.getTreeDocumentId(documentUri) }
    documentId == treeDocumentId || documentId.startsWith("$treeDocumentId/")
}.getOrDefault(false)

/** 未知大小的云端 SAF 文件使用不定进度；已知大小则把原始字节进度稳定限制在 0—100。 @author long */
internal fun exportProgressPercent(progress: DocumentExportProgress): Int? {
    val totalBytes = progress.totalBytes?.takeIf { it > 0L } ?: return null
    return ((progress.copiedBytes.coerceAtLeast(0L).toDouble() / totalBytes) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}

/** 进度说明同时显示已复制量和原文件总量，用户可以判断大文件导出是否仍在前进。 @author long */
internal fun exportProgressDetail(progress: DocumentExportProgress): String = progress.totalBytes
    ?.takeIf { it >= 0L }
    ?.let { total -> "已复制 ${formatExportBytes(progress.copiedBytes)} / ${formatExportBytes(total)}" }
    ?: "已复制 ${formatExportBytes(progress.copiedBytes)}"

private fun formatExportBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

/** @author long */
enum class AppScreen { HOME, RECENT, SETTINGS, BROWSER, READER, BINARY, ERROR }

/** 设置采用一级分类、二级详情，返回时先退回分类页再离开设置。 @author long */
enum class SettingsPage { ROOT, READING, EDITOR, APPEARANCE, UPDATE }

/** 编辑器只提供稳定的空格或 Tab 缩进策略，不暴露 Sora 的内部实现选项。 @author long */
enum class EditorIndentStyle(
    val preferenceValue: String,
    val displayName: String,
    val description: String,
) {
    SPACES("spaces", "空格", "用空格表达每一级缩进，跨平台显示更稳定"),
    TABS("tabs", "Tab", "保留制表符，适合明确约定 Tab 缩进的项目"),
    ;

    val usesTabs: Boolean get() = this == TABS

    companion object {
        fun fromPreference(value: String?): EditorIndentStyle = entries.firstOrNull {
            it.preferenceValue == value
        } ?: SPACES
    }
}

/** Tab 宽度限定为产品提供的三个档位，旧配置或异常值统一回到 4。 @author long */
internal fun normalizeEditorTabWidth(value: Int): Int = when (value) {
    2, 4, 8 -> value
    else -> 4
}

/** @author long */
enum class ReaderCommandType {
    UNDO,
    REDO,
    SELECT_LINE,
    DELETE_LINE,
    COPY,
    CUT,
    PASTE,
    INDENT,
    UNINDENT,
    REPLACE_CURRENT,
    REPLACE_ALL,
    SEARCH_FORWARD,
    SEARCH_BACKWARD,
    CLEAR_SEARCH,
    GOTO_LINE,
    GOTO_SEARCH_MATCH,
    MARKDOWN_HEADING,
}

/** 阅读器命令使用递增 id，保证连续点击“下一个”时 Compose 仍会把命令交给原生视图。 @author long */
data class ReaderCommand(
    val id: Long,
    val type: ReaderCommandType,
    val query: String = "",
    val line: Int = 1,
    val headingIndex: Int = 0,
    val searchOptions: TextSearchOptions = TextSearchOptions(),
    val column: Int = 0,
    val endColumnExclusive: Int = column,
    val replacement: String = "",
    val targetDocumentId: String? = null,
)

/** @author long */
data class ReaderSettings(
    val fontSizeSp: Float = 14f,
    val wordWrap: Boolean = false,
    val background: ReaderBackground = ReaderBackground.FOLLOW_THEME,
    val appPalette: AppColorPalette = AppColorPalette.EMERALD,
    val tabWidth: Int = 4,
    val indentStyle: EditorIndentStyle = EditorIndentStyle.SPACES,
    val autoIndent: Boolean = true,
    val autoClosePairs: Boolean = true,
    val optimizePasteIndentation: Boolean = true,
)

/** 长耗时操作在全局遮罩中持续展示阶段、进度和取消能力，避免用户误以为应用没有响应。 @author long */
enum class ReaderOperationKind { GENERAL, INDEX, GIT, EXPORT }

/** @author long */
data class ReaderOperationState(
    val title: String,
    val detail: String? = null,
    val progressPercent: Int? = null,
    val cancellable: Boolean = false,
    val kind: ReaderOperationKind = ReaderOperationKind.GENERAL,
)

/** 可重试操作只保存稳定参数，错误页销毁重建后仍能恢复原动作。 @author long */
sealed interface ReaderRetryAction {
    data class OpenUri(val uri: Uri) : ReaderRetryAction
    data class OpenTree(val uri: Uri) : ReaderRetryAction
    data class ImportZip(val uri: Uri) : ReaderRetryAction
    data class OpenRecent(val project: RecentProjectRecord) : ReaderRetryAction
    data class OpenEntry(val entry: SourceEntry) : ReaderRetryAction
    data class OpenSearchResult(val result: ProjectSearchResult) : ReaderRetryAction
    data class OpenReadingBookmark(val bookmark: ReadingDocumentState) : ReaderRetryAction
    data class OpenBundledProject(val assetPath: String, val targetName: String) : ReaderRetryAction
    data class RefreshProject(val root: EntryLocation, val title: String) : ReaderRetryAction
    data class GotoLine(val line: Int) : ReaderRetryAction
    data object Save : ReaderRetryAction
    data object LoadMore : ReaderRetryAction
    data class SetEncoding(val encoding: TextEncoding) : ReaderRetryAction
}

/** @author long */
data class ReaderFailureState(
    val title: String,
    val detail: String,
    val retryAction: ReaderRetryAction,
)

/** 每个标签页独立保存草稿和预览状态，切换文件不会丢失未保存内容。 @author long */
data class ReaderTabState(
    val document: OpenDocument,
    val draftText: String = document.text,
    val editable: Boolean = false,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val markdownPreview: Boolean = document.fileType.markdown,
    val currentLine: Int = 1,
    val cursorLine: Int = currentLine,
    val searchQuery: String = "",
    val searchOptions: TextSearchOptions = TextSearchOptions(),
    val searchMatchCount: Int = 0,
    val searchCurrentIndex: Int = -1,
    val searchCountTruncated: Boolean = false,
    val searchMatches: List<TextSearchPosition> = emptyList(),
    val searchInProgress: Boolean = false,
    val searchError: String? = null,
    val searchScannedLines: Int = 0,
)

/** 原文件变化时先暂停草稿恢复，避免旧内容在用户无感知的情况下覆盖新版本。 @author long */
data class DraftConflictState(
    val documentId: String,
    val documentName: String,
)

/** @author long */
data class ReaderUiState(
    val screen: AppScreen = AppScreen.HOME,
    val settingsPage: SettingsPage = SettingsPage.ROOT,
    val operation: ReaderOperationState? = null,
    val message: String? = null,
    val browserTitle: String? = null,
    val browserBackTarget: AppScreen = AppScreen.HOME,
    val binaryBackTarget: AppScreen = AppScreen.HOME,
    val binaryFile: BinaryFileInfo? = null,
    val errorBackTarget: AppScreen = AppScreen.HOME,
    val failure: ReaderFailureState? = null,
    val gitRepositoryRoot: String? = null,
    val gitUpdatePreview: GitUpdatePreview? = null,
    val projectRoot: EntryLocation? = null,
    val projectEntries: List<ProjectTreeEntry> = emptyList(),
    val expandedDirectoryIds: Set<String> = emptySet(),
    val projectSearchQuery: String = "",
    val projectSearchResults: List<ProjectSearchResult> = emptyList(),
    val projectSearchInProgress: Boolean = false,
    val projectSearchError: String? = null,
    val projectSearchOptions: ProjectSearchOptions = ProjectSearchOptions(),
    val projectSearchTotalMatches: Int = 0,
    val projectSearchMatchedFiles: Int = 0,
    val projectSearchResultsTruncated: Boolean = false,
    val projectSearchActiveResultIndex: Int = -1,
    val projectRevealEntryId: String? = null,
    val recentProjects: List<RecentProjectRecord> = emptyList(),
    val readingStates: List<ReadingDocumentState> = emptyList(),
    val tabs: List<ReaderTabState> = emptyList(),
    val activeTabId: String? = null,
    val draftConflict: DraftConflictState? = null,
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
    val canUndo: Boolean get() = activeTab?.canUndo == true
    val canRedo: Boolean get() = activeTab?.canRedo == true
    val markdownPreview: Boolean get() = activeTab?.markdownPreview == true
    val currentLine: Int get() = activeTab?.currentLine?.coerceAtLeast(1) ?: 1
    val cursorLine: Int get() = activeTab?.cursorLine?.coerceAtLeast(1) ?: currentLine
    val fileSearchQuery: String get() = activeTab?.searchQuery.orEmpty()
    val fileSearchOptions: TextSearchOptions get() = activeTab?.searchOptions ?: TextSearchOptions()
    val fileSearchMatchCount: Int get() = activeTab?.searchMatchCount ?: 0
    val fileSearchCurrentIndex: Int get() = activeTab?.searchCurrentIndex ?: -1
    val fileSearchCountTruncated: Boolean get() = activeTab?.searchCountTruncated == true
    val fileSearchMatches: List<TextSearchPosition> get() = activeTab?.searchMatches.orEmpty()
    val fileSearchInProgress: Boolean get() = activeTab?.searchInProgress == true
    val fileSearchError: String? get() = activeTab?.searchError
    val fileSearchScannedLines: Int get() = activeTab?.searchScannedLines ?: 0
    val activeReadingState: ReadingDocumentState?
        get() = activeTabId?.let { id -> readingStates.firstOrNull { it.documentId == id } }
    val fileBookmarks: List<ReadingDocumentState> get() = readingStates.filter(ReadingDocumentState::fileBookmarked)
    val markdownHeadings: List<MarkdownHeading>
        get() = if (document?.fileType?.markdown == true) MarkdownOutlineParser.parse(draftText) else emptyList()
    val currentProjectPath: String?
        get() = activeTabId?.let { id -> projectEntries.firstOrNull { it.source.id == id }?.path }
    val currentProjectDirectoryPath: String?
        get() = currentProjectPath?.substringBeforeLast('/', missingDelimiterValue = "")
    val projectFileTypes: List<com.lonnnnnng.codereader.model.FileType>
        get() = projectEntries.asSequence()
            .filterNot { it.source.isDirectory }
            .map { com.lonnnnnng.codereader.model.FileType.detect(it.source.name) }
            .distinct()
            .sortedBy { it.displayName }
            .toList()

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
    private val draftStore = DraftStore(DraftStore.defaultDirectory(application))
    private val importer = ProjectImporter(application)
    private val gitRepositoryManager = GitRepositoryManager(application)
    private val updateRepository = AppUpdateRepository(application)
    private val commandIds = AtomicLong()
    private val fileSearchRequestIds = AtomicLong()
    private val updateOperationActive = AtomicBoolean(false)
    @Volatile private var activeGitMonitor: GitOperationProgressMonitor? = null
    @Volatile private var activeIndexJob: Job? = null
    @Volatile private var activeExportJob: Job? = null
    private var projectSearchJob: Job? = null
    private var fileSearchJob: Job? = null
    private var fileSearchDocumentId: String? = null
    @Volatile private var activeFileSearchRequestId: Long? = null
    private var readingStatePersistJob: Job? = null
    private val draftPersistJobs = mutableMapOf<String, Job>()
    private val draftPersistenceFailures = mutableSetOf<String>()
    private var pendingDraftConflict: DocumentDraft? = null

    private val initialTheme = ReaderTheme.fromPreference(preferences.getString(KEY_THEME, null))
    private val initialSettings = ReaderSettings(
        fontSizeSp = preferences.getFloat(KEY_FONT_SIZE, 14f).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
        wordWrap = preferences.getBoolean(KEY_WORD_WRAP, false),
        background = ReaderBackground.fromPreference(preferences.getString(KEY_READER_BACKGROUND, null)),
        appPalette = AppColorPalette.fromPreference(preferences.getString(KEY_APP_PALETTE, null)),
        tabWidth = normalizeEditorTabWidth(preferences.getInt(KEY_EDITOR_TAB_WIDTH, 4)),
        indentStyle = EditorIndentStyle.fromPreference(preferences.getString(KEY_EDITOR_INDENT_STYLE, null)),
        autoIndent = preferences.getBoolean(KEY_EDITOR_AUTO_INDENT, true),
        autoClosePairs = preferences.getBoolean(KEY_EDITOR_AUTO_CLOSE_PAIRS, true),
        optimizePasteIndentation = preferences.getBoolean(KEY_EDITOR_OPTIMIZE_PASTE_INDENTATION, true),
    )
    private val initialRecentProjects = RecentProjectPolicy.normalize(
        RecentProjectCodec.decode(preferences.getString(KEY_RECENT_PROJECTS, null)),
        MAX_RECENT_PROJECTS,
    )
    private val initialReadingStates = ReadingStatePolicy.normalize(
        ReadingStateCodec.decode(preferences.getString(KEY_READING_STATES, null)),
    )
    private val _state = MutableStateFlow(
        ReaderUiState(
            theme = initialTheme,
            settings = initialSettings,
            recentProjects = initialRecentProjects,
            readingStates = initialReadingStates,
        ),
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    override fun onCleared() {
        projectSearchJob?.cancel()
        fileSearchJob?.cancel()
        activeIndexJob?.cancel()
        readingStatePersistJob?.cancel()
        val draftIdsToFlush = draftPersistJobs.keys + draftPersistenceFailures
        draftPersistJobs.values.toList().forEach(Job::cancel)
        draftPersistJobs.clear()
        // 只补写仍在防抖窗口或上次失败的草稿，避免退出时重复同步所有已落盘的大文本。 @author long
        _state.value.tabs.filter { it.dirty && it.document.id in draftIdsToFlush }.forEach { tab ->
            runCatching { draftStore.save(tab.toDocumentDraft()) }
        }
        persistReadingStates(_state.value.readingStates)
        repository.close()
        super.onCleared()
    }

    init {
        viewModelScope.launch {
            runCatching {
                SyntaxRegistry.initialize(getApplication())
                SyntaxRegistry.setTheme(getApplication(), initialTheme)
            }.onFailure { showError(it) }
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

    fun openUri(uri: Uri) = launchBusy(ReaderRetryAction.OpenUri(uri)) {
        openDocumentWithStoredPosition(repository.openUri(uri))
    }

    fun openSafTree(uri: Uri) = launchBusy(ReaderRetryAction.OpenTree(uri)) {
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

    fun importZip(uri: Uri) = launchBusy(ReaderRetryAction.ImportZip(uri)) {
        openLocalRoot(importer.importZip(uri), rememberRecent = true)
    }

    fun cloneGit(url: String) = launchGitOperation("正在克隆 Git 仓库") { monitor ->
        val directory = gitRepositoryManager.clone(url, monitor)
        updateOperationDetail("正在建立完整目录索引")
        openLocalRoot(directory, rememberRecent = true)
        "已克隆 ${directory.name}"
    }

    fun updateGitRepository() {
        val root = _state.value.gitRepositoryRoot?.let(::File)
        if (root == null || !gitRepositoryManager.isRepository(root)) {
            _state.update { it.copy(message = "当前项目不是可更新的 Git 仓库") }
            return
        }

        val unsavedPaths = unsavedGitPaths(root)
        launchGitOperation("正在检查仓库更新") { monitor ->
            val preview = gitRepositoryManager.previewUpdate(root, monitor).withUnsavedChanges(unsavedPaths)
            if (_state.value.gitRepositoryRoot != root.absolutePath) {
                "当前项目已切换，未显示旧仓库的更新预览"
            } else if (preview.relation == GitUpdateRelation.UP_TO_DATE && preview.localChangeCount == 0) {
                "仓库已经是最新版本"
            } else {
                _state.update { it.copy(gitUpdatePreview = preview, message = null) }
                null
            }
        }
    }

    fun dismissGitUpdatePreview() {
        _state.update { it.copy(gitUpdatePreview = null) }
    }

    fun applyGitUpdatePreview() {
        val preview = _state.value.gitUpdatePreview ?: return
        val root = _state.value.gitRepositoryRoot?.let(::File)
        if (root == null || !gitRepositoryManager.isRepository(root)) {
            _state.update { it.copy(gitUpdatePreview = null, message = "当前 Git 项目已经失效") }
            return
        }
        if (!preview.canApply) {
            _state.update { it.copy(message = "当前预览不满足安全快进条件") }
            return
        }
        val unsavedPaths = unsavedGitPaths(root)
        if (unsavedPaths.isNotEmpty()) {
            _state.update {
                it.copy(
                    gitUpdatePreview = preview.withUnsavedChanges(unsavedPaths),
                    message = "检测到尚未保存的文件，已暂停 Git 更新",
                )
            }
            return
        }

        _state.update { it.copy(gitUpdatePreview = null, message = null) }
        launchGitOperation(
            title = "正在应用安全更新",
            initialDetail = "正在核对工作区与预览版本",
            cancellable = false,
        ) { monitor ->
            val result = gitRepositoryManager.applyUpdate(root, preview, monitor)
            if (result.updated) {
                updateOperationDetail("正在刷新完整目录索引")
                refreshAfterGitUpdate(root)
                "已安全更新到 ${preview.targetRevision.orEmpty().take(8)}"
            } else {
                "仓库已经是最新版本"
            }
        }
    }

    fun cancelOperation() {
        val kind = _state.value.operation?.kind
        when (kind) {
            ReaderOperationKind.GIT -> activeGitMonitor?.cancel()
            ReaderOperationKind.INDEX -> activeIndexJob?.cancel()
            ReaderOperationKind.EXPORT -> activeExportJob?.cancel(CancellationException("导出已取消"))
            ReaderOperationKind.GENERAL, null -> Unit
        }
        _state.update { current ->
            val operation = current.operation ?: return@update current
            val detail = when (operation.kind) {
                ReaderOperationKind.INDEX -> "正在取消目录索引…"
                ReaderOperationKind.GIT -> "正在取消 Git 操作…"
                ReaderOperationKind.EXPORT -> "正在取消导出并清理半成品…"
                ReaderOperationKind.GENERAL -> operation.detail
            }
            current.copy(operation = operation.copy(detail = detail, cancellable = false))
        }
    }

    /** 刷新当前项目目录，普通目录、SAF、ZIP 和 Git 工作区统一重新获取来源索引。 @author long */
    fun refreshProject() {
        val root = _state.value.projectRoot
        val title = _state.value.browserTitle
        if (root == null || title.isNullOrBlank()) {
            _state.update { it.copy(message = "当前没有可刷新的项目") }
            return
        }
        if (_state.value.tabs.any { it.dirty && isDocumentInsideRoot(it.document, root) }) {
            _state.update { it.copy(message = "请先保存或关闭项目中的未保存文件，再刷新目录") }
            return
        }
        launchBusy(ReaderRetryAction.RefreshProject(root, title)) {
            val index = buildProjectIndex(root, forceRefresh = true)
            _state.update { current ->
                val remainingTabs = current.tabs.filterNot { isDocumentInsideRoot(it.document, root) }
                val activeId = current.activeTabId?.takeIf { id -> remainingTabs.any { it.document.id == id } }
                current.copy(
                    projectEntries = index,
                    expandedDirectoryIds = emptySet(),
                    projectSearchQuery = "",
                    projectSearchResults = emptyList(),
                    projectSearchInProgress = false,
                    projectSearchError = null,
                    projectSearchTotalMatches = 0,
                    projectSearchMatchedFiles = 0,
                    projectSearchResultsTruncated = false,
                    projectSearchActiveResultIndex = -1,
                    projectRevealEntryId = null,
                    tabs = remainingTabs,
                    activeTabId = activeId,
                    message = "项目目录已刷新",
                )
            }
        }
    }

    fun openBundledProject(assetPath: String, targetName: String) = launchBusy(
        ReaderRetryAction.OpenBundledProject(assetPath, targetName),
    ) {
        openLocalRoot(importer.prepareBundledProject(assetPath, targetName), rememberRecent = false)
    }

    fun openRecentProject(project: RecentProjectRecord) = launchBusy(ReaderRetryAction.OpenRecent(project)) {
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
        launchBusy(ReaderRetryAction.OpenEntry(entry)) { openDocumentWithStoredPosition(openSource(entry)) }
    }

    fun toggleDirectory(id: String) {
        _state.update { current ->
            val expanded = current.expandedDirectoryIds.toMutableSet()
            if (!expanded.add(id)) expanded.remove(id)
            current.copy(expandedDirectoryIds = expanded)
        }
    }

    fun searchProject(
        query: String,
        options: ProjectSearchOptions = _state.value.projectSearchOptions,
    ) {
        val normalizedQuery = query.trim()
        val effectiveOptions = when (options.scope) {
            ProjectSearchScope.PROJECT -> options.copy(directoryPath = null)
            ProjectSearchScope.CURRENT_DIRECTORY -> {
                val directory = options.directoryPath ?: _state.value.currentProjectDirectoryPath
                if (directory == null) {
                    _state.update { it.copy(message = "请先打开项目中的文件，再按当前目录搜索") }
                    return
                }
                options.copy(directoryPath = directory)
            }
        }
        projectSearchJob?.cancel()
        if (normalizedQuery.isBlank()) {
            _state.update {
                it.copy(
                    projectSearchQuery = "",
                    projectSearchResults = emptyList(),
                    projectSearchInProgress = false,
                    projectSearchError = null,
                    projectSearchOptions = effectiveOptions,
                    projectSearchTotalMatches = 0,
                    projectSearchMatchedFiles = 0,
                    projectSearchResultsTruncated = false,
                    projectSearchActiveResultIndex = -1,
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
                projectSearchOptions = effectiveOptions,
                projectSearchTotalMatches = 0,
                projectSearchMatchedFiles = 0,
                projectSearchResultsTruncated = false,
                projectSearchActiveResultIndex = -1,
            )
        }
        val job = viewModelScope.launch {
            try {
                val page = repository.searchProject(_state.value.projectEntries, normalizedQuery, effectiveOptions)
                // 用户可在搜索期间清空或提交新关键词，旧请求只能更新仍然匹配的查询。 @author long
                _state.update { current ->
                    if (current.projectSearchQuery == normalizedQuery && current.projectSearchOptions == effectiveOptions) {
                        current.copy(
                            projectSearchResults = page.results,
                            projectSearchInProgress = false,
                            projectSearchTotalMatches = page.totalMatches,
                            projectSearchMatchedFiles = page.matchedFiles,
                            projectSearchResultsTruncated = page.truncated,
                        )
                    } else {
                        current
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _state.update { current ->
                    if (current.projectSearchQuery == normalizedQuery && current.projectSearchOptions == effectiveOptions) {
                        current.copy(
                            projectSearchInProgress = false,
                            projectSearchError = error.message ?: error.javaClass.simpleName,
                            projectSearchTotalMatches = 0,
                            projectSearchMatchedFiles = 0,
                            projectSearchResultsTruncated = false,
                        )
                    } else {
                        current
                    }
                }
            }
        }
        projectSearchJob = job
    }

    fun updateProjectSearchOptions(options: ProjectSearchOptions) {
        val query = _state.value.projectSearchQuery
        if (query.isBlank()) {
            searchProject("", options)
        } else {
            searchProject(query, options)
        }
    }

    fun openSearchResult(result: ProjectSearchResult) {
        val index = _state.value.projectSearchResults.indexOf(result)
        if (index >= 0) _state.update { it.copy(projectSearchActiveResultIndex = index) }
        launchBusy(ReaderRetryAction.OpenSearchResult(result)) {
            val document = loadDocumentUntilLine(openSource(result.source), result.line)
            openDocument(document, initialLine = result.line)
        }
    }

    fun openAdjacentProjectSearchResult(forward: Boolean) {
        val current = _state.value
        if (current.projectSearchResults.isEmpty()) return
        val size = current.projectSearchResults.size
        val nextIndex = if (forward) {
            if (current.projectSearchActiveResultIndex < 0) 0 else (current.projectSearchActiveResultIndex + 1) % size
        } else {
            if (current.projectSearchActiveResultIndex < 0) size - 1 else (current.projectSearchActiveResultIndex - 1 + size) % size
        }
        openSearchResult(current.projectSearchResults[nextIndex])
    }

    /** 展开当前文件的全部父目录并返回项目树，用户无需手工逐层寻找文件。 @author long */
    fun locateCurrentFileInProject() {
        val current = _state.value
        val documentId = current.activeTabId ?: return
        val target = current.projectEntries.firstOrNull { it.source.id == documentId }
        if (target == null) {
            _state.update { it.copy(message = "当前文件不属于已打开的项目") }
            return
        }
        val byId = current.projectEntries.associateBy { it.source.id }
        val expanded = current.expandedDirectoryIds.toMutableSet()
        var parentId = target.parentId
        while (parentId != null) {
            expanded += parentId
            parentId = byId[parentId]?.parentId
        }
        projectSearchJob?.cancel()
        _state.update {
            it.copy(
                screen = AppScreen.BROWSER,
                expandedDirectoryIds = expanded,
                projectSearchQuery = "",
                projectSearchResults = emptyList(),
                projectSearchInProgress = false,
                projectSearchError = null,
                projectSearchTotalMatches = 0,
                projectSearchMatchedFiles = 0,
                projectSearchResultsTruncated = false,
                projectSearchActiveResultIndex = -1,
                projectRevealEntryId = target.source.id,
            )
        }
    }

    fun switchTab(id: String) {
        val tab = _state.value.tabs.firstOrNull { it.document.id == id } ?: return
        _state.update {
            it.copy(
                screen = AppScreen.READER,
                // 当前 Sora 实例切换全文后会建立新的撤销栈，不能沿用旧标签的能力提示。 @author long
                tabs = it.tabs.map { item ->
                    if (item.document.id == id) item.copy(canUndo = false, canRedo = false) else item
                },
                activeTabId = id,
                readerCommand = commandForTab(tab),
            )
        }
    }

    fun closeTab(id: String) {
        if (fileSearchDocumentId == id) cancelFileSearchTask(clearState = false)
        deleteDraft(id)
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
                draftConflict = current.draftConflict?.takeUnless { it.documentId == id },
                screen = if (nextId == null) if (current.browserTitle != null) AppScreen.BROWSER else AppScreen.HOME else current.screen,
                readerCommand = nextId?.let { activeId ->
                    remaining.firstOrNull { it.document.id == activeId }?.let(::commandForTab)
                },
            )
        }
    }

    /** Sora 只在可见行变化时回传，这里再合并密集滚动写入，避免频繁刷新偏好文件。 @author long */
    fun updateReadingPosition(documentId: String, line: Int) {
        val normalizedLine = line.coerceAtLeast(1)
        var changed = false
        _state.update { current ->
            val tab = current.tabs.firstOrNull { it.document.id == documentId } ?: return@update current
            val previous = current.readingStates.firstOrNull { it.documentId == documentId }
            if (tab.currentLine == normalizedLine && previous?.lastViewedLine == normalizedLine) return@update current
            changed = true
            current.copy(
                tabs = current.tabs.map {
                    if (it.document.id == documentId) it.copy(currentLine = normalizedLine) else it
                },
                readingStates = upsertReadingState(current, tab.document, normalizedLine),
            )
        }
        if (changed) scheduleReadingStatePersistence()
    }

    /** 行书签跟随光标而不是首个可见行，滚动和选区事件并发时不会把书签加到错误位置。 @author long */
    fun updateCursorPosition(documentId: String, line: Int) {
        val normalizedLine = line.coerceAtLeast(1)
        updateTab(documentId) { tab ->
            if (tab.cursorLine == normalizedLine) tab else tab.copy(cursorLine = normalizedLine)
        }
    }

    fun toggleFileBookmark() {
        val tab = _state.value.activeTab ?: return
        var bookmarked = false
        _state.update { current ->
            val base = readingStateFor(current, tab.document, tab.currentLine)
            bookmarked = !base.fileBookmarked
            current.copy(
                readingStates = upsertReadingState(current, base.copy(fileBookmarked = bookmarked)),
                message = if (bookmarked) "已添加文件书签" else "已取消文件书签",
            )
        }
        persistReadingStates(_state.value.readingStates)
    }

    fun toggleLineBookmark(line: Int) {
        val tab = _state.value.activeTab ?: return
        val normalizedLine = line.coerceAtLeast(1)
        var added = false
        _state.update { current ->
            val base = readingStateFor(current, tab.document, normalizedLine)
            val lines = base.lineBookmarks.toMutableSet()
            added = lines.add(normalizedLine)
            if (!added) lines.remove(normalizedLine)
            current.copy(
                tabs = current.tabs.map {
                    if (it.document.id == tab.document.id) {
                        it.copy(currentLine = normalizedLine, cursorLine = normalizedLine)
                    } else {
                        it
                    }
                },
                readingStates = upsertReadingState(current, base.copy(lineBookmarks = lines.sorted())),
                message = if (added) "已添加第 $normalizedLine 行书签" else "已移除第 $normalizedLine 行书签",
            )
        }
        persistReadingStates(_state.value.readingStates)
    }

    fun removeFileBookmark(documentId: String) {
        updateStoredBookmark(documentId) { it.copy(fileBookmarked = false) }
    }

    fun removeLineBookmark(documentId: String, line: Int) {
        updateStoredBookmark(documentId) { state ->
            state.copy(lineBookmarks = state.lineBookmarks.filterNot { it == line })
        }
    }

    fun openReadingBookmark(bookmark: ReadingDocumentState) = launchBusy(
        ReaderRetryAction.OpenReadingBookmark(bookmark),
    ) {
        val document = when (bookmark.locationKind) {
            "local" -> repository.openLocal(File(bookmark.documentId))
            "saf" -> repository.openUri(Uri.parse(bookmark.documentId), bookmark.documentName)
            else -> error("无法识别书签来源")
        }
        openDocumentWithStoredPosition(document, bookmark.lastViewedLine)
    }

    fun setEditable(enabled: Boolean) {
        val document = _state.value.document ?: return
        if (enabled && !document.canWrite) {
            _state.update {
                it.copy(
                    message = if (document.largeFile) {
                        "大文件分段模式只允许读取，可从“更多”导出完整副本"
                    } else {
                        "当前来源只允许读取，可从“更多”导出副本"
                    },
                )
            }
            return
        }
        updateActiveTab { it.copy(editable = enabled) }
    }

    fun updateDraft(text: String) {
        val tab = _state.value.activeTab ?: return
        if (tab.draftText != text && fileSearchDocumentId == tab.document.id) {
            cancelFileSearchTask(clearState = false)
        }
        updateActiveTab { tab ->
            if (tab.draftText == text) tab else tab.copy(
                draftText = text,
                dirty = text != tab.document.text,
            ).withoutFileSearch()
        }
        _state.value.activeTab?.takeIf { it.document.id == tab.document.id }?.let { updated ->
            if (updated.dirty) scheduleDraftPersistence(updated.document.id) else deleteDraft(updated.document.id)
        }
    }

    fun restoreConflictingDraft() {
        val conflict = _state.value.draftConflict ?: return
        val draft = pendingDraftConflict?.takeIf { it.documentId == conflict.documentId } ?: return
        pendingDraftConflict = null
        _state.update { current ->
            val tab = current.tabs.firstOrNull { it.document.id == conflict.documentId } ?: return@update current
            current.copy(
                tabs = current.tabs.map { item ->
                    if (item.document.id == conflict.documentId) {
                        item.copy(
                            draftText = draft.draftText,
                            dirty = draft.draftText != item.document.text,
                            editable = item.document.canWrite,
                            markdownPreview = false,
                        ).withoutFileSearch()
                    } else {
                        item
                    }
                },
                draftConflict = null,
                message = if (tab.document.canWrite) {
                    "已恢复 ${tab.document.name} 的草稿，请确认后保存"
                } else {
                    "已恢复 ${tab.document.name} 的草稿，但当前来源只读"
                },
            )
        }
        if (_state.value.tabs.any { it.document.id == conflict.documentId && it.dirty }) {
            // 用户已明确选择旧草稿，重新以当前磁盘正文为基线保存，后续重启不再重复报同一冲突。 @author long
            scheduleDraftPersistence(conflict.documentId)
        } else {
            deleteDraft(conflict.documentId)
        }
    }

    fun discardConflictingDraft() {
        val conflict = _state.value.draftConflict ?: return
        pendingDraftConflict = null
        deleteDraft(conflict.documentId)
        _state.update { it.copy(draftConflict = null, message = "已保留文件当前内容并放弃旧草稿") }
    }

    fun updateEditorHistory(documentId: String, history: EditorHistoryState) {
        updateTab(documentId) { tab ->
            if (tab.canUndo == history.canUndo && tab.canRedo == history.canRedo) tab else tab.copy(
                canUndo = history.canUndo,
                canRedo = history.canRedo,
            )
        }
    }

    fun undo() = dispatchEditingCommand(ReaderCommandType.UNDO, _state.value.canUndo)

    fun redo() = dispatchEditingCommand(ReaderCommandType.REDO, _state.value.canRedo)

    /**
     * 操作面板只分发源码视图支持的有限命令；只读状态仍可选中和复制，但不会触发任何正文修改。
     *
     * @author long
     */
    fun runEditorCommand(type: ReaderCommandType) {
        val tab = _state.value.activeTab ?: return
        if (type !in EDITOR_ACTION_COMMANDS) return
        val unavailableMessage = when {
            tab.markdownPreview -> "Markdown 预览不支持该操作，请先切换到源码"
            type in MUTATING_EDITOR_ACTION_COMMANDS && !tab.editable -> "请先进入编辑模式"
            else -> null
        }
        if (unavailableMessage != null) {
            _state.update { it.copy(message = unavailableMessage) }
            return
        }
        dispatchCommand(
            ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = type,
                targetDocumentId = tab.document.id,
            ),
        )
    }

    fun replaceCurrent(replacement: String) {
        val tab = editableSearchTab() ?: return
        val match = tab.searchMatches.getOrNull(tab.searchCurrentIndex)
        if (match == null) {
            _state.update { it.copy(message = "请先定位要替换的匹配内容") }
            return
        }
        dispatchCommand(
            ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = ReaderCommandType.REPLACE_CURRENT,
                query = tab.searchQuery,
                line = match.line,
                column = match.column,
                endColumnExclusive = match.endColumnExclusive,
                searchOptions = tab.searchOptions,
                replacement = replacement,
                targetDocumentId = tab.document.id,
            ),
        )
    }

    fun replaceAll(replacement: String) {
        val tab = editableSearchTab() ?: return
        dispatchCommand(
            ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = ReaderCommandType.REPLACE_ALL,
                query = tab.searchQuery,
                searchOptions = tab.searchOptions,
                replacement = replacement,
                targetDocumentId = tab.document.id,
            ),
        )
    }

    private fun editableSearchTab(): ReaderTabState? {
        val tab = _state.value.activeTab ?: return null
        val unavailableMessage = when {
            !tab.editable -> "请先进入编辑模式"
            tab.markdownPreview -> "Markdown 预览不支持替换，请切换到源码"
            tab.searchInProgress -> "文件搜索尚未完成"
            tab.searchQuery.isEmpty() || tab.searchMatches.isEmpty() -> "当前没有可替换的匹配内容"
            else -> null
        }
        if (unavailableMessage != null) {
            _state.update { it.copy(message = unavailableMessage) }
            return null
        }
        return tab
    }

    fun handleEditorReplacement(result: EditorReplacementResult) {
        if (_state.value.activeTabId != result.documentId) return
        if (result.errorMessage != null) {
            _state.update { it.copy(message = "替换失败：${result.errorMessage}") }
            return
        }
        if (result.replacementCount <= 0) {
            _state.update { it.copy(message = "当前没有可替换的匹配内容") }
            return
        }
        if (result.replaceAll) {
            updateTab(result.documentId) { it.withoutFileSearch() }
            dispatchCommand(
                ReaderCommand(
                    id = commandIds.incrementAndGet(),
                    type = ReaderCommandType.CLEAR_SEARCH,
                    targetDocumentId = result.documentId,
                ),
            )
            _state.update { it.copy(message = "已替换 ${result.replacementCount} 处") }
        } else {
            _state.update { it.copy(message = "已替换当前匹配") }
            // 当前替换会让旧行列失效，重新扫描后直接定位下一处，连续修订无需再次提交查询。 @author long
            searchInFile(result.query, forward = true, options = result.searchOptions)
        }
    }

    private fun dispatchEditingCommand(type: ReaderCommandType, available: Boolean) {
        val tab = _state.value.activeTab ?: return
        if (!tab.editable || tab.markdownPreview || !available) return
        dispatchCommand(
            ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = type,
                targetDocumentId = tab.document.id,
            ),
        )
    }

    fun save() {
        val tab = _state.value.activeTab ?: return
        if (!tab.dirty) return
        launchBusy(ReaderRetryAction.Save) {
            val savedBytes = repository.save(tab.document, tab.draftText)
            deleteDraft(tab.document.id)
            updateTab(tab.document.id) { current ->
                if (current.draftText == tab.draftText) {
                    current.copy(
                        dirty = false,
                        document = current.document.copy(
                            text = tab.draftText,
                            totalBytes = savedBytes,
                            loadedCharacters = tab.draftText.length.toLong(),
                            hasMore = false,
                        ),
                    )
                } else {
                    // 保存期间若正文继续变化，新内容仍保持未保存并重新进入草稿持久化。 @author long
                    current.copy(
                        document = current.document.copy(
                            text = tab.draftText,
                            totalBytes = savedBytes,
                            loadedCharacters = tab.draftText.length.toLong(),
                            hasMore = false,
                        ),
                        dirty = true,
                    )
                }
            }
            _state.value.tabs.firstOrNull { it.document.id == tab.document.id && it.dirty }
                ?.let { scheduleDraftPersistence(it.document.id) }
            _state.update { it.copy(message = "已保存 ${tab.document.name}") }
        }
    }

    /**
     * 系统文件创建器已经生成一个全新目标；导出失败或取消时尝试删除半成品，避免残缺文件被误认为成功结果。
     *
     * @author long
     */
    fun exportDocumentCopy(documentId: String, targetUri: Uri) {
        if (activeExportJob?.isActive == true) {
            _state.update { it.copy(message = "已有文件正在导出") }
            return
        }
        val document = _state.value.tabs.firstOrNull { it.document.id == documentId }?.document
        if (document == null) {
            _state.update { it.copy(message = "当前文件已经关闭，无法继续导出") }
            return
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            _state.update {
                it.copy(
                    operation = ReaderOperationState(
                        title = "正在导出原文件副本",
                        detail = "正在读取 ${document.name}",
                        progressPercent = 0.takeIf { document.totalBytes > 0 },
                        cancellable = true,
                        kind = ReaderOperationKind.EXPORT,
                    ),
                    message = null,
                )
            }
            try {
                repository.exportOriginal(document, targetUri) { progress ->
                    _state.update { current ->
                        val operation = current.operation?.takeIf { it.kind == ReaderOperationKind.EXPORT }
                            ?: return@update current
                        current.copy(
                            operation = operation.copy(
                                detail = exportProgressDetail(progress),
                                progressPercent = exportProgressPercent(progress),
                            ),
                        )
                    }
                }
                _state.update { it.copy(message = "已导出完整副本：${document.name}") }
            } catch (cancelled: CancellationException) {
                val removed = withContext(NonCancellable) { repository.deleteCreatedDocument(targetUri) }
                _state.update {
                    it.copy(
                        message = if (removed) {
                            "已取消导出，未保留不完整文件"
                        } else {
                            "已取消导出，目标文件可能不完整"
                        },
                    )
                }
            } catch (error: Throwable) {
                val removed = withContext(NonCancellable) { repository.deleteCreatedDocument(targetUri) }
                val detail = error.message ?: error.javaClass.simpleName
                _state.update {
                    it.copy(
                        message = if (removed) {
                            "导出失败，已删除不完整文件：$detail"
                        } else {
                            "导出失败，目标文件可能不完整：$detail"
                        },
                    )
                }
            } finally {
                if (activeExportJob === coroutineContext[Job]) activeExportJob = null
                _state.update { current ->
                    if (current.operation?.kind == ReaderOperationKind.EXPORT) {
                        current.copy(operation = null)
                    } else {
                        current
                    }
                }
            }
        }
        activeExportJob = job
        job.start()
    }

    fun loadMore() {
        val tab = _state.value.activeTab ?: return
        if (!tab.document.hasMore) return
        if (fileSearchDocumentId == tab.document.id) cancelFileSearchTask(clearState = false)
        launchBusy(ReaderRetryAction.LoadMore) {
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
                ).withoutFileSearch()
            }
        }
    }

    fun setDocumentEncoding(encoding: TextEncoding) {
        val tab = _state.value.activeTab ?: return
        if (tab.document.encoding == encoding) return
        if (tab.dirty) {
            _state.update { it.copy(message = "请先保存或放弃当前修改，再切换文件编码") }
            return
        }
        if (fileSearchDocumentId == tab.document.id) cancelFileSearchTask(clearState = false)
        launchBusy(ReaderRetryAction.SetEncoding(encoding)) {
            val reopened = repository.reopen(tab.document, encoding)
            updateActiveTab {
                it.copy(
                    document = reopened,
                    draftText = reopened.text,
                    dirty = false,
                ).withoutFileSearch()
            }
            _state.update { it.copy(message = "已使用 ${encoding.displayName} 重新读取 ${tab.document.name}") }
        }
    }

    fun toggleMarkdownPreview() {
        val documentId = _state.value.activeTabId ?: return
        if (fileSearchDocumentId == documentId) cancelFileSearchTask(clearState = true)
        _state.update { current ->
            val tab = current.tabs.firstOrNull { it.document.id == documentId } ?: return@update current
            if (!tab.document.fileType.markdown) return@update current
            // 两个阅读内核在切换瞬间都消费同一行号：预览定位语义块，Sora 保证该源码行进入可见区域。 @author long
            current.copy(
                tabs = current.tabs.map {
                    if (it.document.id == documentId) it.copy(markdownPreview = !it.markdownPreview) else it
                },
                readerCommand = ReaderCommand(
                    id = commandIds.incrementAndGet(),
                    type = ReaderCommandType.GOTO_LINE,
                    line = tab.currentLine,
                    targetDocumentId = documentId,
                ),
            )
        }
    }

    fun searchInFile(
        query: String,
        forward: Boolean,
        options: TextSearchOptions = _state.value.fileSearchOptions,
    ) {
        if (query.isEmpty()) return
        val tab = _state.value.activeTab ?: return
        val effectiveOptions = if (tab.document.fileType.markdown && tab.markdownPreview) {
            // WebView 原生查找不支持整词和正则；预览模式保持普通文本查找，源码模式仍支持全部选项。 @author long
            TextSearchOptions()
        } else {
            options
        }
        val sameSearch = tab.searchQuery == query && tab.searchOptions == effectiveOptions

        if (sameSearch && tab.searchInProgress) return
        if (sameSearch && tab.searchError == null) {
            if (tab.searchMatches.isEmpty()) {
                _state.update { it.copy(message = "当前文件没有匹配内容") }
            } else {
                navigateStoredFileSearch(tab, forward)
            }
            return
        }

        if (tab.document.fileType.markdown && tab.markdownPreview) {
            searchMarkdownPreview(tab, query, forward, effectiveOptions)
        } else {
            startSourceFileSearch(tab, query, forward, effectiveOptions)
        }
    }

    fun clearFileSearch() {
        val documentId = _state.value.activeTabId ?: return
        cancelFileSearchTask(clearState = false)
        updateTab(documentId) { it.withoutFileSearch() }
        dispatchCommand(
            ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = ReaderCommandType.CLEAR_SEARCH,
                targetDocumentId = documentId,
            ),
        )
    }

    fun cancelFileSearch() {
        val documentId = fileSearchDocumentId ?: _state.value.activeTabId ?: return
        cancelFileSearchTask(clearState = false)
        updateTab(documentId) { it.withoutFileSearch() }
        if (_state.value.activeTabId == documentId) {
            dispatchCommand(
                ReaderCommand(
                    id = commandIds.incrementAndGet(),
                    type = ReaderCommandType.CLEAR_SEARCH,
                    targetDocumentId = documentId,
                ),
            )
            _state.update { it.copy(message = "已取消文件搜索") }
        }
    }

    /** Markdown 预览由 WebView 查找渲染文本，源码高级选项不能伪装成已经生效。 @author long */
    private fun searchMarkdownPreview(
        tab: ReaderTabState,
        query: String,
        forward: Boolean,
        options: TextSearchOptions,
    ) {
        cancelFileSearchTask(clearState = true)
        val page = runCatching {
            TextSearchMatcher.compile(query, options).scanLines(
                lines = tab.draftText.lineSequence(),
                maxStoredMatches = FILE_SEARCH_STORED_MATCH_LIMIT,
            )
        }.getOrElse { error ->
            updateTab(tab.document.id) {
                it.withoutFileSearch().copy(
                    searchQuery = query,
                    searchOptions = options,
                    searchError = error.message ?: "搜索条件无效",
                )
            }
            return
        }
        val nextIndex = when {
            page.matches.isEmpty() -> -1
            forward -> 0
            else -> page.matches.lastIndex
        }
        updateTab(tab.document.id) {
            it.copy(
                searchQuery = query,
                searchOptions = options,
                searchMatchCount = page.totalMatches,
                searchCurrentIndex = nextIndex,
                searchCountTruncated = page.truncated,
                searchMatches = page.matches,
                searchInProgress = false,
                searchError = null,
                searchScannedLines = loadedLineCount(tab.draftText),
            )
        }
        if (page.matches.isEmpty()) {
            dispatchCommand(
                ReaderCommand(
                    id = commandIds.incrementAndGet(),
                    type = ReaderCommandType.CLEAR_SEARCH,
                    targetDocumentId = tab.document.id,
                ),
            )
            _state.update { it.copy(message = "当前预览没有匹配内容") }
        } else {
            dispatchCommand(
                ReaderCommand(
                    id = commandIds.incrementAndGet(),
                    type = if (forward) ReaderCommandType.SEARCH_FORWARD else ReaderCommandType.SEARCH_BACKWARD,
                    query = query,
                    searchOptions = options,
                    targetDocumentId = tab.document.id,
                ),
            )
        }
    }

    /**
     * 普通源码在计算线程扫描当前草稿；大文件从原始来源按编码流式扫描，避免首个分页之后的命中丢失。
     * @author long
     */
    private fun startSourceFileSearch(
        tab: ReaderTabState,
        query: String,
        forward: Boolean,
        options: TextSearchOptions,
    ) {
        val matcher = runCatching { TextSearchMatcher.compile(query, options) }.getOrElse { error ->
            cancelFileSearchTask(clearState = true)
            updateTab(tab.document.id) {
                it.withoutFileSearch().copy(
                    searchQuery = query,
                    searchOptions = options,
                    searchError = error.message ?: "搜索条件无效",
                )
            }
            return
        }
        cancelFileSearchTask(clearState = true)
        val documentId = tab.document.id
        val requestId = fileSearchRequestIds.incrementAndGet()
        activeFileSearchRequestId = requestId
        fileSearchDocumentId = documentId
        updateTab(documentId) {
            it.withoutFileSearch().copy(
                searchQuery = query,
                searchOptions = options,
                searchInProgress = true,
            )
        }
        if (_state.value.activeTabId == documentId) {
            dispatchCommand(
                ReaderCommand(
                    id = commandIds.incrementAndGet(),
                    type = ReaderCommandType.CLEAR_SEARCH,
                    targetDocumentId = documentId,
                ),
            )
        }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val progress: (TextSearchProgress) -> Unit = { value ->
                    reportFileSearchProgress(requestId, documentId, value)
                }
                val page = if (tab.document.largeFile) {
                    repository.searchDocument(
                        document = tab.document,
                        query = query,
                        options = options,
                        maxStoredMatches = FILE_SEARCH_STORED_MATCH_LIMIT,
                        onProgress = progress,
                    )
                } else {
                    scanDraftText(tab.draftText, matcher, progress)
                }
                applyFileSearchResult(requestId, documentId, query, options, forward, page)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (isCurrentFileSearch(requestId, documentId)) {
                    updateTab(documentId) {
                        it.copy(
                            searchInProgress = false,
                            searchError = error.message ?: error.javaClass.simpleName,
                        )
                    }
                }
            } finally {
                if (isCurrentFileSearch(requestId, documentId)) {
                    activeFileSearchRequestId = null
                    fileSearchDocumentId = null
                    fileSearchJob = null
                }
            }
        }
        fileSearchJob = job
        job.start()
    }

    private suspend fun scanDraftText(
        text: String,
        matcher: TextSearchMatcher,
        onProgress: (TextSearchProgress) -> Unit,
    ): TextSearchPage = withContext(Dispatchers.Default) {
        val collector = TextSearchLineCollector(matcher, FILE_SEARCH_STORED_MATCH_LIMIT)
        var scannedLines = 0
        text.lineSequence().forEachIndexed { index, line ->
            currentCoroutineContext().ensureActive()
            scannedLines = index + 1
            collector.accept(scannedLines, line)
            if (scannedLines % FILE_SEARCH_PROGRESS_LINE_BATCH == 0) {
                onProgress(TextSearchProgress(scannedLines, collector.totalMatches))
            }
        }
        onProgress(TextSearchProgress(scannedLines, collector.totalMatches))
        collector.result()
    }

    private fun reportFileSearchProgress(
        requestId: Long,
        documentId: String,
        progress: TextSearchProgress,
    ) {
        if (!isCurrentFileSearch(requestId, documentId)) return
        updateTab(documentId) {
            if (!it.searchInProgress) it else it.copy(
                searchMatchCount = progress.matchesFound,
                searchScannedLines = progress.scannedLines,
            )
        }
    }

    private suspend fun applyFileSearchResult(
        requestId: Long,
        documentId: String,
        query: String,
        options: TextSearchOptions,
        forward: Boolean,
        page: TextSearchPage,
    ) {
        if (!isCurrentFileSearch(requestId, documentId)) return
        val currentTab = _state.value.tabs.firstOrNull { it.document.id == documentId } ?: return
        val nextIndex = when {
            page.matches.isEmpty() -> -1
            forward -> 0
            else -> page.matches.lastIndex
        }
        val match = page.matches.getOrNull(nextIndex)
        val needsMoreContent = match != null && currentTab.document.largeFile && currentTab.document.hasMore &&
            loadedLineCount(currentTab.draftText) < match.line
        updateTab(documentId) {
            it.copy(
                searchQuery = query,
                searchOptions = options,
                searchMatchCount = page.totalMatches,
                searchCurrentIndex = nextIndex,
                searchCountTruncated = page.truncated,
                searchMatches = page.matches,
                searchInProgress = needsMoreContent,
                searchError = null,
                currentLine = match?.line ?: it.currentLine,
                cursorLine = match?.line ?: it.cursorLine,
            )
        }

        if (match == null) {
            if (_state.value.activeTabId == documentId) {
                dispatchCommand(
                    ReaderCommand(
                        id = commandIds.incrementAndGet(),
                        type = ReaderCommandType.CLEAR_SEARCH,
                        targetDocumentId = documentId,
                    ),
                )
                _state.update { it.copy(message = "当前文件没有匹配内容") }
            }
            return
        }

        if (needsMoreContent) {
            val loaded = loadDocumentUntilLine(currentTab.document, match.line)
            if (!isCurrentFileSearch(requestId, documentId)) return
            updateTab(documentId) {
                if (it.document.id != loaded.id) it else it.copy(
                    document = loaded,
                    draftText = loaded.text,
                    currentLine = match.line,
                    cursorLine = match.line,
                    searchInProgress = false,
                )
            }
        } else {
            updateTab(documentId) {
                it.copy(searchInProgress = false, currentLine = match.line, cursorLine = match.line)
            }
        }
        updateReadingPosition(documentId, match.line)
        if (_state.value.activeTabId == documentId) {
            dispatchSearchMatch(documentId, query, options, match)
        }
    }

    private fun navigateStoredFileSearch(tab: ReaderTabState, forward: Boolean) {
        if (tab.searchMatches.isEmpty()) return
        val nextIndex = if (forward) {
            (tab.searchCurrentIndex + 1).mod(tab.searchMatches.size)
        } else {
            (tab.searchCurrentIndex - 1).mod(tab.searchMatches.size)
        }
        if (tab.document.fileType.markdown && tab.markdownPreview) {
            updateTab(tab.document.id) { it.copy(searchCurrentIndex = nextIndex) }
            dispatchCommand(
                ReaderCommand(
                    id = commandIds.incrementAndGet(),
                    type = if (forward) ReaderCommandType.SEARCH_FORWARD else ReaderCommandType.SEARCH_BACKWARD,
                    query = tab.searchQuery,
                    searchOptions = tab.searchOptions,
                    targetDocumentId = tab.document.id,
                ),
            )
            return
        }

        val match = tab.searchMatches[nextIndex]
        val needsMoreContent = tab.document.largeFile && tab.document.hasMore && loadedLineCount(tab.draftText) < match.line
        if (needsMoreContent) {
            startSearchMatchNavigation(tab, nextIndex, match)
        } else {
            updateTab(tab.document.id) {
                it.copy(searchCurrentIndex = nextIndex, currentLine = match.line, cursorLine = match.line)
            }
            updateReadingPosition(tab.document.id, match.line)
            if (_state.value.activeTabId == tab.document.id) {
                dispatchSearchMatch(tab.document.id, tab.searchQuery, tab.searchOptions, match)
            }
        }
    }

    /** 已有命中跳到尚未加载的行时只补页，不重新扫描整份文件。 @author long */
    private fun startSearchMatchNavigation(
        tab: ReaderTabState,
        matchIndex: Int,
        match: TextSearchPosition,
    ) {
        cancelFileSearchTask(clearState = false)
        val documentId = tab.document.id
        val requestId = fileSearchRequestIds.incrementAndGet()
        activeFileSearchRequestId = requestId
        fileSearchDocumentId = documentId
        updateTab(documentId) {
            it.copy(
                searchCurrentIndex = matchIndex,
                searchInProgress = true,
                searchError = null,
            )
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val latest = _state.value.tabs.firstOrNull { it.document.id == documentId } ?: return@launch
                val loaded = loadDocumentUntilLine(latest.document, match.line)
                if (!isCurrentFileSearch(requestId, documentId)) return@launch
                updateTab(documentId) {
                    it.copy(
                        document = loaded,
                        draftText = loaded.text,
                        currentLine = match.line,
                        cursorLine = match.line,
                        searchInProgress = false,
                    )
                }
                updateReadingPosition(documentId, match.line)
                if (_state.value.activeTabId == documentId) {
                    dispatchSearchMatch(documentId, latest.searchQuery, latest.searchOptions, match)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (isCurrentFileSearch(requestId, documentId)) {
                    updateTab(documentId) {
                        it.copy(
                            searchInProgress = false,
                            searchError = error.message ?: error.javaClass.simpleName,
                        )
                    }
                }
            } finally {
                if (isCurrentFileSearch(requestId, documentId)) {
                    activeFileSearchRequestId = null
                    fileSearchDocumentId = null
                    fileSearchJob = null
                }
            }
        }
        fileSearchJob = job
        job.start()
    }

    private fun dispatchSearchMatch(
        documentId: String,
        query: String,
        options: TextSearchOptions,
        match: TextSearchPosition,
    ) {
        dispatchCommand(
            ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = ReaderCommandType.GOTO_SEARCH_MATCH,
                query = query,
                line = match.line,
                column = match.column,
                endColumnExclusive = match.endColumnExclusive,
                searchOptions = options,
                targetDocumentId = documentId,
            ),
        )
    }

    private fun isCurrentFileSearch(requestId: Long, documentId: String): Boolean =
        activeFileSearchRequestId == requestId && fileSearchDocumentId == documentId

    private fun cancelFileSearchTask(clearState: Boolean) {
        val documentId = fileSearchDocumentId
        activeFileSearchRequestId = null
        fileSearchJob?.cancel()
        fileSearchJob = null
        fileSearchDocumentId = null
        if (clearState && documentId != null) updateTab(documentId) { it.withoutFileSearch() }
    }

    fun gotoLine(line: Int) {
        val targetLine = line.coerceAtLeast(1)
        val tab = _state.value.activeTab
        if (tab?.document?.largeFile == true && tab.document.hasMore && loadedLineCount(tab.draftText) < targetLine) {
            launchBusy(ReaderRetryAction.GotoLine(targetLine)) {
                val document = loadDocumentUntilLine(tab.document, targetLine)
                updateActiveTab {
                    if (it.document.id != document.id) it else it.copy(document = document, draftText = document.text)
                }
                dispatchCommand(
                    ReaderCommand(
                        commandIds.incrementAndGet(),
                        ReaderCommandType.GOTO_LINE,
                        line = targetLine,
                        targetDocumentId = tab.document.id,
                    ),
                )
            }
        } else if (tab != null) {
            dispatchCommand(
                ReaderCommand(
                    commandIds.incrementAndGet(),
                    ReaderCommandType.GOTO_LINE,
                    line = targetLine,
                    targetDocumentId = tab.document.id,
                ),
            )
        }
    }

    fun gotoMarkdownHeading(index: Int) {
        val documentId = _state.value.activeTabId ?: return
        dispatchCommand(
            ReaderCommand(
                commandIds.incrementAndGet(),
                ReaderCommandType.MARKDOWN_HEADING,
                headingIndex = index,
                targetDocumentId = documentId,
            ),
        )
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

    fun setEditorTabWidth(width: Int) {
        val normalized = normalizeEditorTabWidth(width)
        preferences.edit().putInt(KEY_EDITOR_TAB_WIDTH, normalized).apply()
        _state.update { it.copy(settings = it.settings.copy(tabWidth = normalized)) }
    }

    fun setEditorIndentStyle(style: EditorIndentStyle) {
        preferences.edit().putString(KEY_EDITOR_INDENT_STYLE, style.preferenceValue).apply()
        _state.update { it.copy(settings = it.settings.copy(indentStyle = style)) }
    }

    fun setEditorAutoIndent(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EDITOR_AUTO_INDENT, enabled).apply()
        _state.update { it.copy(settings = it.settings.copy(autoIndent = enabled)) }
    }

    fun setEditorAutoClosePairs(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EDITOR_AUTO_CLOSE_PAIRS, enabled).apply()
        _state.update { it.copy(settings = it.settings.copy(autoClosePairs = enabled)) }
    }

    fun setEditorOptimizePasteIndentation(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_EDITOR_OPTIMIZE_PASTE_INDENTATION, enabled).apply()
        _state.update { it.copy(settings = it.settings.copy(optimizePasteIndentation = enabled)) }
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
        }.onFailure { showError(it) }
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
        reportMessage(message)
    }

    fun reportMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun retryLastFailure() {
        val current = _state.value
        val action = current.failure?.retryAction ?: return
        _state.update {
            it.copy(
                screen = it.errorBackTarget,
                errorBackTarget = AppScreen.HOME,
                failure = null,
            )
        }
        when (action) {
            is ReaderRetryAction.OpenUri -> openUri(action.uri)
            is ReaderRetryAction.OpenTree -> openSafTree(action.uri)
            is ReaderRetryAction.ImportZip -> importZip(action.uri)
            is ReaderRetryAction.OpenRecent -> openRecentProject(action.project)
            is ReaderRetryAction.OpenEntry -> openEntry(action.entry)
            is ReaderRetryAction.OpenSearchResult -> openSearchResult(action.result)
            is ReaderRetryAction.OpenReadingBookmark -> openReadingBookmark(action.bookmark)
            is ReaderRetryAction.OpenBundledProject -> openBundledProject(action.assetPath, action.targetName)
            is ReaderRetryAction.RefreshProject -> refreshProject(action.root, action.title)
            is ReaderRetryAction.GotoLine -> gotoLine(action.line)
            ReaderRetryAction.Save -> save()
            ReaderRetryAction.LoadMore -> loadMore()
            is ReaderRetryAction.SetEncoding -> setDocumentEncoding(action.encoding)
        }
    }

    private fun refreshProject(root: EntryLocation, title: String) =
        launchBusy(ReaderRetryAction.RefreshProject(root, title)) {
            val index = buildProjectIndex(root, forceRefresh = true)
            _state.update { current ->
                current.copy(
                    browserTitle = title,
                    projectEntries = index,
                    expandedDirectoryIds = emptySet(),
                    projectSearchQuery = "",
                    projectSearchResults = emptyList(),
                    projectSearchInProgress = false,
                    projectSearchError = null,
                    projectSearchTotalMatches = 0,
                    projectSearchMatchedFiles = 0,
                    projectSearchResultsTruncated = false,
                    projectSearchActiveResultIndex = -1,
                    projectRevealEntryId = null,
                    tabs = current.tabs.filterNot { isDocumentInsideRoot(it.document, root) },
                    activeTabId = current.activeTabId?.takeIf { id ->
                        current.tabs.any { it.document.id == id && !isDocumentInsideRoot(it.document, root) }
                    },
                )
            }
        }

    private suspend fun loadDocumentUntilLine(document: OpenDocument, targetLine: Int): OpenDocument {
        var loaded = document
        while (loaded.largeFile && loaded.hasMore && loadedLineCount(loaded.text) < targetLine) {
            val page = repository.loadMore(loaded)
            val updatedText = loaded.text + page.text
            loaded = loaded.copy(
                text = updatedText,
                loadedCharacters = page.nextCharacter,
                hasMore = page.hasMore,
            )
        }
        return loaded
    }

    private fun loadedLineCount(text: String): Int = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1

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
                    projectRoot = null,
                    projectEntries = emptyList(),
                    expandedDirectoryIds = emptySet(),
                    projectSearchResults = emptyList(),
                    projectSearchQuery = "",
                    projectSearchInProgress = false,
                    projectSearchError = null,
                    projectSearchTotalMatches = 0,
                    projectSearchMatchedFiles = 0,
                    projectSearchResultsTruncated = false,
                    projectSearchActiveResultIndex = -1,
                    projectRevealEntryId = null,
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
        AppScreen.BINARY -> {
            _state.update {
                it.copy(
                    screen = it.binaryBackTarget,
                    binaryBackTarget = AppScreen.HOME,
                    binaryFile = null,
                )
            }
            true
        }
        AppScreen.ERROR -> {
            _state.update {
                it.copy(
                    screen = it.errorBackTarget,
                    errorBackTarget = AppScreen.HOME,
                    failure = null,
                )
            }
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
        val index = buildProjectIndex(root)
        val gitRoot = (root as? EntryLocation.Local)
            ?.file
            ?.takeIf(gitRepositoryManager::isRepository)
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
                gitUpdatePreview = null,
                projectRoot = root,
                projectEntries = index,
                expandedDirectoryIds = emptySet(),
                projectSearchQuery = "",
                projectSearchResults = emptyList(),
                projectSearchInProgress = false,
                projectSearchError = null,
                projectSearchTotalMatches = 0,
                projectSearchMatchedFiles = 0,
                projectSearchResultsTruncated = false,
                projectSearchActiveResultIndex = -1,
                projectRevealEntryId = null,
                errorBackTarget = AppScreen.HOME,
                failure = null,
                message = null,
            )
        }
        val lastDocument = if (recent == null) {
            null
        } else {
            _state.value.readingStates
                .firstOrNull { it.projectRootId == root.stableId }
                ?.let { reading ->
                    index.firstOrNull { it.source.id == reading.documentId }?.source?.let { source -> source to reading }
                }
        }
        if (lastDocument != null) {
            // 先完成项目索引再恢复文件，文件被移动或删除时仍保留可用的项目浏览页。 @author long
            runCatching {
                openDocumentWithStoredPosition(openSource(lastDocument.first), lastDocument.second.lastViewedLine)
            }.onFailure { error ->
                _state.update { it.copy(message = "项目已打开，但无法恢复上次文件：${error.message ?: error.javaClass.simpleName}") }
            }
        }
    }

    /** 索引期间把扫描统计映射为用户可理解的反馈，并将当前协程登记为可取消任务。 @author long */
    private suspend fun buildProjectIndex(
        root: EntryLocation,
        forceRefresh: Boolean = false,
    ): List<ProjectTreeEntry> {
        val job = currentCoroutineContext()[Job]
        activeIndexJob = job
        _state.update {
            val operation = it.operation ?: ReaderOperationState("正在建立项目索引")
            it.copy(
                operation = operation.copy(
                    title = "正在建立项目索引",
                    detail = "已扫描 0 个文件、0 个目录",
                    cancellable = true,
                    kind = ReaderOperationKind.INDEX,
                ),
            )
        }
        return try {
            repository.indexProject(root, ::updateIndexProgress, forceRefresh)
        } finally {
            if (activeIndexJob === job) activeIndexJob = null
        }
    }

    private fun updateIndexProgress(progress: ProjectIndexProgress) {
        _state.update { current ->
            val operation = current.operation ?: return@update current
            val reused = if (progress.reusedEntries > 0) "，复用 ${progress.reusedEntries} 项缓存" else ""
            current.copy(
                operation = operation.copy(
                    title = "正在建立项目索引",
                    detail = "已扫描 ${progress.scannedFiles} 个文件、${progress.scannedDirectories} 个目录$reused，用时 ${progress.elapsedMs} ms",
                    cancellable = true,
                    kind = ReaderOperationKind.INDEX,
                ),
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

    /** Git Status 看不到 Sora 内尚未落盘的草稿，因此更新预览和确认前都要从标签状态补齐。 @author long */
    private fun unsavedGitPaths(root: File): List<String> = _state.value.tabs
        .asSequence()
        .filter { it.dirty && isDocumentInside(it.document, root) }
        .mapNotNull { tab ->
            val file = (tab.document.location as? EntryLocation.Local)?.file ?: return@mapNotNull null
            runCatching {
                file.canonicalFile.relativeTo(root.canonicalFile).path.replace(File.separatorChar, '/')
            }.getOrNull()
        }
        .distinct()
        .sorted()
        .toList()

    private suspend fun refreshAfterGitUpdate(root: File) {
        val index = buildProjectIndex(EntryLocation.Local(root), forceRefresh = true)
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
                projectSearchTotalMatches = 0,
                projectSearchMatchedFiles = 0,
                projectSearchResultsTruncated = false,
                projectSearchActiveResultIndex = -1,
                projectRevealEntryId = null,
                tabs = remainingTabs,
                activeTabId = activeId,
            )
        }
    }

    private fun isDocumentInsideRoot(document: OpenDocument, root: EntryLocation): Boolean = when (root) {
        is EntryLocation.Local -> isDocumentInside(document, root.file)
        is EntryLocation.Saf -> (document.location as? EntryLocation.Saf)
            ?.let { isSafDocumentInsideTree(it.uri, root.uri) }
            ?: false
    }

    private suspend fun openDocumentWithStoredPosition(document: OpenDocument, requestedLine: Int? = null) {
        val storedLine = _state.value.readingStates
            .firstOrNull { it.documentId == document.id }
            ?.lastViewedLine
            ?: 1
        val targetLine = (requestedLine ?: storedLine).coerceAtLeast(1)
        val loaded = if (document.largeFile && document.hasMore && loadedLineCount(document.text) < targetLine) {
            loadDocumentUntilLine(document, targetLine)
        } else {
            document
        }
        openDocument(
            document = loaded,
            initialLine = targetLine.takeIf { requestedLine != null || it > 1 },
            currentLine = targetLine,
        )
    }

    private suspend fun openDocument(
        document: OpenDocument,
        initialLine: Int? = null,
        currentLine: Int = initialLine ?: 1,
    ) {
        val existingBeforeOpen = _state.value.tabs.any { it.document.id == document.id }
        val storedDraft = if (existingBeforeOpen) null else withContext(Dispatchers.IO) {
            draftStore.load(document.id)
        }
        val documentFingerprint = storedDraft?.let {
            withContext(Dispatchers.IO) { DraftFingerprint.create(document) }
        }
        val documentLocationKind = when (document.location) {
            is EntryLocation.Local -> "local"
            is EntryLocation.Saf -> "saf"
        }
        val recoveredDraft = storedDraft?.takeIf {
            it.locationKind == documentLocationKind &&
                it.draftText != document.text &&
                it.originalFingerprint == documentFingerprint
        }
        val conflictingDraft = storedDraft?.takeIf {
            it.draftText != document.text &&
                (it.locationKind != documentLocationKind || it.originalFingerprint != documentFingerprint)
        }
        if (storedDraft != null && storedDraft.draftText == document.text) {
            withContext(Dispatchers.IO) { draftStore.delete(document.id) }
        }
        if (conflictingDraft != null) pendingDraftConflict = conflictingDraft
        _state.update { current ->
            val existing = current.tabs.firstOrNull { it.document.id == document.id }
            val tabs = if (existing == null) {
                current.tabs + ReaderTabState(
                    document = document,
                    draftText = recoveredDraft?.draftText ?: document.text,
                    editable = recoveredDraft != null && document.canWrite,
                    dirty = recoveredDraft != null,
                    markdownPreview = document.fileType.markdown && initialLine == null && recoveredDraft == null,
                    currentLine = currentLine,
                    cursorLine = currentLine,
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
                    updated.copy(currentLine = currentLine, cursorLine = currentLine)
                }
            }
            val next = current.copy(
                screen = AppScreen.READER,
                tabs = tabs,
                activeTabId = document.id,
                message = if (recoveredDraft != null) "已恢复 ${document.name} 的未保存草稿" else null,
                draftConflict = conflictingDraft?.let {
                    DraftConflictState(it.documentId, it.documentName)
                } ?: current.draftConflict,
                binaryFile = null,
                binaryBackTarget = AppScreen.HOME,
                errorBackTarget = AppScreen.HOME,
                failure = null,
                readerCommand = when {
                    initialLine != null -> ReaderCommand(
                        commandIds.incrementAndGet(),
                        ReaderCommandType.GOTO_LINE,
                        line = initialLine,
                        targetDocumentId = document.id,
                    )
                    existing != null -> tabs.firstOrNull { it.document.id == document.id }?.let(::commandForTab)
                    else -> null
                },
            )
            next.copy(readingStates = upsertReadingState(next, document, currentLine))
        }
        persistReadingStates(_state.value.readingStates)
    }

    private fun updateActiveTab(transform: (ReaderTabState) -> ReaderTabState) {
        _state.update { current ->
            val activeId = current.activeTabId ?: return@update current
            current.copy(tabs = current.tabs.map { if (it.document.id == activeId) transform(it) else it })
        }
    }

    private fun updateTab(documentId: String, transform: (ReaderTabState) -> ReaderTabState) {
        _state.update { current ->
            if (current.tabs.none { it.document.id == documentId }) return@update current
            current.copy(tabs = current.tabs.map { if (it.document.id == documentId) transform(it) else it })
        }
    }

    /** 正文被替换后旧命中位置已经失效，必须清空而不是继续显示错误的序号。 @author long */
    private fun ReaderTabState.withoutFileSearch(): ReaderTabState = copy(
        searchQuery = "",
        searchOptions = TextSearchOptions(),
        searchMatchCount = 0,
        searchCurrentIndex = -1,
        searchCountTruncated = false,
        searchMatches = emptyList(),
        searchInProgress = false,
        searchError = null,
        searchScannedLines = 0,
    )

    /** 标签切回时恢复该文件自己的搜索高亮和精确位置，旧文件命令不会落到新标签。 @author long */
    private fun commandForTab(tab: ReaderTabState): ReaderCommand {
        val match = tab.searchMatches.getOrNull(tab.searchCurrentIndex)
        return when {
            tab.document.fileType.markdown && tab.markdownPreview && tab.searchQuery.isNotEmpty() -> ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = ReaderCommandType.SEARCH_FORWARD,
                query = tab.searchQuery,
                searchOptions = tab.searchOptions,
                targetDocumentId = tab.document.id,
            )
            match != null -> ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = ReaderCommandType.GOTO_SEARCH_MATCH,
                query = tab.searchQuery,
                line = match.line,
                column = match.column,
                endColumnExclusive = match.endColumnExclusive,
                searchOptions = tab.searchOptions,
                targetDocumentId = tab.document.id,
            )
            else -> ReaderCommand(
                id = commandIds.incrementAndGet(),
                type = ReaderCommandType.GOTO_LINE,
                line = tab.currentLine.coerceAtLeast(1),
                targetDocumentId = tab.document.id,
            )
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

    private fun readingStateFor(
        current: ReaderUiState,
        document: OpenDocument,
        line: Int,
    ): ReadingDocumentState {
        val previous = current.readingStates.firstOrNull { it.documentId == document.id }
        val locationKind = when (document.location) {
            is EntryLocation.Local -> "local"
            is EntryLocation.Saf -> "saf"
        }
        val activeRootId = current.projectRoot
            ?.takeIf { isDocumentInsideRoot(document, it) }
            ?.stableId
        return ReadingDocumentState(
            locationKind = locationKind,
            documentId = document.id,
            documentName = document.name,
            projectRootId = activeRootId ?: previous?.projectRootId,
            lastViewedLine = line.coerceAtLeast(1),
            fileBookmarked = previous?.fileBookmarked == true,
            lineBookmarks = previous?.lineBookmarks.orEmpty(),
        )
    }

    private fun upsertReadingState(
        current: ReaderUiState,
        document: OpenDocument,
        line: Int,
    ): List<ReadingDocumentState> = upsertReadingState(current, readingStateFor(current, document, line))

    private fun upsertReadingState(
        current: ReaderUiState,
        readingState: ReadingDocumentState,
    ): List<ReadingDocumentState> = ReadingStatePolicy.normalize(
        listOf(readingState) + current.readingStates.filterNot { it.documentId == readingState.documentId },
    )

    private fun updateStoredBookmark(
        documentId: String,
        transform: (ReadingDocumentState) -> ReadingDocumentState,
    ) {
        var changed = false
        _state.update { current ->
            val existing = current.readingStates.firstOrNull { it.documentId == documentId } ?: return@update current
            changed = true
            current.copy(readingStates = upsertReadingState(current, transform(existing)))
        }
        if (changed) persistReadingStates(_state.value.readingStates)
    }

    private fun scheduleReadingStatePersistence() {
        readingStatePersistJob?.cancel()
        readingStatePersistJob = viewModelScope.launch {
            delay(READING_STATE_PERSIST_DELAY_MS)
            persistReadingStates(_state.value.readingStates)
        }
    }

    private fun persistReadingStates(states: List<ReadingDocumentState>) {
        preferences.edit().putString(KEY_READING_STATES, ReadingStateCodec.encode(states)).apply()
    }

    /** 编辑停顿后再写草稿，既覆盖进程回收场景，也避免每个按键都触发一次文件同步。 @author long */
    private fun scheduleDraftPersistence(documentId: String) {
        draftPersistJobs.remove(documentId)?.cancel()
        val job = viewModelScope.launch {
            try {
                delay(DRAFT_PERSIST_DELAY_MS)
                val tab = _state.value.tabs.firstOrNull { it.document.id == documentId && it.dirty }
                    ?: return@launch
                try {
                    withContext(Dispatchers.IO) { draftStore.save(tab.toDocumentDraft()) }
                    draftPersistenceFailures.remove(documentId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    draftPersistenceFailures += documentId
                    _state.update { current ->
                        if (current.tabs.any { it.document.id == documentId && it.dirty }) {
                            current.copy(message = "草稿自动保存失败：${error.message ?: error.javaClass.simpleName}")
                        } else {
                            current
                        }
                    }
                }
            } finally {
                if (draftPersistJobs[documentId] === coroutineContext[Job]) {
                    draftPersistJobs.remove(documentId)
                }
            }
        }
        draftPersistJobs[documentId] = job
    }

    /** 保存成功、撤销回原文或明确放弃标签后，旧草稿不能在下次打开时再次出现。 @author long */
    private fun deleteDraft(documentId: String) {
        draftPersistJobs.remove(documentId)?.cancel()
        draftPersistenceFailures.remove(documentId)
        if (pendingDraftConflict?.documentId == documentId) pendingDraftConflict = null
        runCatching { draftStore.delete(documentId) }
    }

    private fun ReaderTabState.toDocumentDraft(): DocumentDraft = DocumentDraft(
        locationKind = when (document.location) {
            is EntryLocation.Local -> "local"
            is EntryLocation.Saf -> "saf"
        },
        documentId = document.id,
        documentName = document.name,
        draftText = draftText,
        originalFingerprint = DraftFingerprint.create(document),
        updatedAtEpochMillis = System.currentTimeMillis(),
    )

    private fun launchBusy(
        retryAction: ReaderRetryAction? = null,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(operation = ReaderOperationState("正在处理"), message = null) }
            try {
                block()
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(message = cancelled.message ?: "操作已取消") }
            } catch (error: Throwable) {
                showError(error, retryAction)
            } finally {
                _state.update { it.copy(operation = null) }
            }
        }
    }

    private fun launchGitOperation(
        title: String,
        initialDetail: String = "正在连接远程仓库",
        cancellable: Boolean = true,
        block: suspend (GitOperationProgressMonitor) -> String?,
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
                        detail = initialDetail,
                        cancellable = cancellable,
                        kind = ReaderOperationKind.GIT,
                    ),
                    message = null,
                )
            }
            try {
                val successMessage = block(monitor)
                if (successMessage != null) _state.update { it.copy(message = successMessage) }
            } catch (cancelled: GitOperationCancelledException) {
                _state.update { it.copy(message = cancelled.message) }
            } catch (rejected: GitUpdateRejectedException) {
                _state.update { it.copy(message = rejected.message ?: "Git 更新预览已经失效，请重新检查") }
            } catch (cancelled: CancellationException) {
                _state.update { it.copy(message = cancelled.message ?: "操作已取消") }
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

    private fun showError(error: Throwable, retryAction: ReaderRetryAction? = null) {
        if (error is BinaryFileException) {
            _state.update { current ->
                current.copy(
                    screen = AppScreen.BINARY,
                    binaryBackTarget = if (current.screen == AppScreen.BINARY) {
                        current.binaryBackTarget
                    } else {
                        current.screen
                    },
                    binaryFile = error.fileInfo,
                    message = null,
                )
            }
            return
        }
        if (retryAction != null) {
            val detail = error.message ?: error.javaClass.simpleName
            // 文件提供方的异常类型不统一，标题同时参考异常类型和稳定中文错误片段。
            val title = when {
                error is DocumentSaveException && error.originalMayBeAffected -> "保存失败，原文件可能已受影响"
                retryAction == ReaderRetryAction.Save -> "保存文件失败"
                error is SecurityException || "权限" in detail || "授权" in detail -> "文件访问权限已经失效"
                error is FileNotFoundException || "不存在" in detail -> "文件或项目已经不存在"
                error is IOException || "无法读取" in detail || "读取失败" in detail -> "读取内容失败"
                else -> "操作没有完成"
            }
            _state.update { current ->
                current.copy(
                    screen = AppScreen.ERROR,
                    errorBackTarget = if (current.screen == AppScreen.ERROR) {
                        current.errorBackTarget
                    } else {
                        current.screen
                    },
                    failure = ReaderFailureState(title, detail, retryAction),
                    message = null,
                )
            }
            return
        }
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
        const val KEY_EDITOR_TAB_WIDTH = "editor_tab_width"
        const val KEY_EDITOR_INDENT_STYLE = "editor_indent_style"
        const val KEY_EDITOR_AUTO_INDENT = "editor_auto_indent"
        const val KEY_EDITOR_AUTO_CLOSE_PAIRS = "editor_auto_close_pairs"
        const val KEY_EDITOR_OPTIMIZE_PASTE_INDENTATION = "editor_optimize_paste_indentation"
        const val KEY_RECENT_PROJECTS = "recent_projects"
        const val KEY_READING_STATES = "reading_states"
        const val READING_STATE_PERSIST_DELAY_MS = 450L
        const val DRAFT_PERSIST_DELAY_MS = 500L
        const val MIN_FONT_SIZE = 11f
        const val MAX_FONT_SIZE = 24f
        const val MAX_RECENT_PROJECTS = 6
        const val FILE_SEARCH_STORED_MATCH_LIMIT = 10_000
        const val FILE_SEARCH_PROGRESS_LINE_BATCH = 512

        val EDITOR_ACTION_COMMANDS = setOf(
            ReaderCommandType.SELECT_LINE,
            ReaderCommandType.DELETE_LINE,
            ReaderCommandType.COPY,
            ReaderCommandType.CUT,
            ReaderCommandType.PASTE,
            ReaderCommandType.INDENT,
            ReaderCommandType.UNINDENT,
        )
        val MUTATING_EDITOR_ACTION_COMMANDS = setOf(
            ReaderCommandType.DELETE_LINE,
            ReaderCommandType.CUT,
            ReaderCommandType.PASTE,
            ReaderCommandType.INDENT,
            ReaderCommandType.UNINDENT,
        )
    }
}
