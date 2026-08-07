package com.lonnnnnng.codereader.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.lonnnnnng.codereader.BuildConfig
import com.lonnnnnng.codereader.data.GitRepositoryAddress
import com.lonnnnnng.codereader.data.PROJECT_SEARCH_RESULT_LIMIT
import com.lonnnnnng.codereader.data.RecentProjectRecord
import com.lonnnnnng.codereader.model.AppColorPalette
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.model.ProjectSearchResult
import com.lonnnnnng.codereader.model.ProjectTreeEntry
import com.lonnnnnng.codereader.model.ReaderBackground
import com.lonnnnnng.codereader.model.ReaderTheme
import com.lonnnnnng.codereader.model.SourceEntry
import com.lonnnnnng.codereader.update.AppUpdateInstaller
import java.io.File

/** @author long */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp(viewModel: ReaderViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val snackbar = remember { SnackbarHostState() }
    val colors = appColorScheme(state.theme, state.settings.appPalette)
    var showGitDialog by remember { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            persistUri(context, it)
            viewModel.openUri(it)
        }
    }
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.openSafTree(it) }
    }
    val openZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importZip(it) }
    }
    val startUpdateInstaller: (File) -> Unit = { file ->
        context.startActivity(AppUpdateInstaller.createInstallIntent(context, file))
    }
    val installSourcePermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val apk = state.appUpdate.downloadedApk
        if (apk != null && AppUpdateInstaller.canRequestPackageInstalls(context)) {
            runCatching { startUpdateInstaller(apk) }
                .onFailure { viewModel.reportUpdateMessage("无法打开系统安装器：${it.message ?: it.javaClass.simpleName}") }
        } else {
            viewModel.reportUpdateMessage("需要允许灵阅安装更新")
        }
    }
    val installUpdate: () -> Unit = {
        val apk = state.appUpdate.downloadedApk
        if (apk == null) {
            viewModel.reportUpdateMessage("更新安装包尚未下载完成")
        } else if (AppUpdateInstaller.canRequestPackageInstalls(context)) {
            runCatching { startUpdateInstaller(apk) }
                .onFailure { viewModel.reportUpdateMessage("无法打开系统安装器：${it.message ?: it.javaClass.simpleName}") }
        } else {
            runCatching { installSourcePermission.launch(AppUpdateInstaller.createUnknownSourcesIntent(context)) }
                .onFailure { viewModel.reportUpdateMessage("无法打开安装来源设置：${it.message ?: it.javaClass.simpleName}") }
        }
    }

    // 系统侧边手势与返回键共用现有页面栈，只有首页没有可返回页面时才进入退出确认。
    BackHandler {
        val operation = state.operation
        if (operation != null) {
            if (operation.cancellable) viewModel.cancelGitOperation()
        } else if (showExitConfirmation) {
            showExitConfirmation = false
        } else if (showGitDialog) {
            showGitDialog = false
        } else if (!viewModel.navigateBack()) {
            showExitConfirmation = true
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    SideEffect {
        if (!view.isInEditMode) {
            val window = (view.context as Activity).window
            val systemBarColor = colors.background.toArgb()
            window.statusBarColor = systemBarColor
            window.navigationBarColor = systemBarColor
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !state.theme.isDark
                isAppearanceLightNavigationBars = !state.theme.isDark
            }
        }
    }

    MaterialTheme(colorScheme = colors, typography = ReaderTypography, shapes = ReaderShapes) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = colors.background,
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    AnimatedContent(
                        targetState = state.screen,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "app-screen",
                    ) { screen ->
                        when (screen) {
                            AppScreen.HOME -> HomeScreen(
                            state = state,
                            onOpenFile = { openFile.launch(arrayOf("*/*")) },
                            onOpenFolder = { openFolder.launch(null) },
                            onOpenZip = { openZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                            onCloneGit = { showGitDialog = true },
                            onOpenBundledProject = viewModel::openBundledProject,
                            onOpenRecentProjects = viewModel::openRecentProjects,
                            onOpenSettings = viewModel::openSettings,
                            onToggleTheme = viewModel::toggleTheme,
                        )
                            AppScreen.RECENT -> RecentProjectsScreen(
                            state = state,
                            onBack = viewModel::navigateBack,
                            onOpenRecent = viewModel::openRecentProject,
                            onRemoveRecent = viewModel::removeRecentProject,
                            onOpenFolder = { openFolder.launch(null) },
                            onOpenZip = { openZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        )
                            AppScreen.SETTINGS -> SettingsScreen(
                            state = state,
                            onBack = viewModel::navigateBack,
                            onSetFontSize = viewModel::setFontSize,
                            onSetReaderBackground = viewModel::setReaderBackground,
                            onSetAppPalette = viewModel::setAppPalette,
                            onSetTheme = viewModel::setTheme,
                            onSetWordWrap = viewModel::setWordWrap,
                            onOpenSettingsPage = viewModel::openSettingsPage,
                            onCheckUpdate = viewModel::checkForUpdate,
                            onShowUpdateDetails = viewModel::showUpdateDetails,
                            onDownloadUpdate = viewModel::downloadUpdate,
                            onDismissUpdate = viewModel::dismissUpdateDetails,
                            onInstallUpdate = installUpdate,
                        )
                            AppScreen.BROWSER -> BrowserScreen(
                            state = state,
                            onBack = viewModel::navigateBack,
                            onEntry = viewModel::openEntry,
                            onSearch = viewModel::searchProject,
                            onSearchResult = viewModel::openSearchResult,
                            onUpdateGit = viewModel::updateGitRepository,
                            onToggleTheme = viewModel::toggleTheme,
                        )
                            AppScreen.READER -> ReaderScreen(
                            state = state,
                            onBack = viewModel::navigateBack,
                            onEditable = viewModel::setEditable,
                            onTextChanged = viewModel::updateDraft,
                            onSave = viewModel::save,
                            onTogglePreview = viewModel::toggleMarkdownPreview,
                            onSwitchTab = viewModel::switchTab,
                            onCloseTab = viewModel::closeTab,
                            onOpenEntry = viewModel::openEntry,
                            onSearchInFile = viewModel::searchInFile,
                            onGotoLine = viewModel::gotoLine,
                            onGotoHeading = viewModel::gotoMarkdownHeading,
                            onSetFontSize = viewModel::setFontSize,
                            onSetWordWrap = viewModel::setWordWrap,
                            onLoadMore = viewModel::loadMore,
                            onToggleTheme = viewModel::toggleTheme,
                            )
                        }
                    }

                    state.operation?.let { operation ->
                        ReaderOperationOverlay(operation, viewModel::cancelGitOperation)
                    }
                }
            }
        }

        if (showExitConfirmation) {
            ReaderDialog(
                onDismissRequest = { showExitConfirmation = false },
                modifier = Modifier.testTag("exit-confirmation-dialog"),
                title = "退出灵阅？",
                icon = Icons.Outlined.Close,
                actions = {
                    TextButton(
                        onClick = { showExitConfirmation = false },
                        modifier = Modifier.testTag("exit-cancel-button"),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            showExitConfirmation = false
                            (context as? Activity)?.finish()
                        },
                        modifier = Modifier.testTag("exit-confirm-button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text("退出应用")
                    }
                },
            ) {
                Text(
                    "未保存的修改不会自动保存，确定要退出应用吗？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Git 弹窗必须留在应用主题树中，否则暗色模式会回退到 Material 默认亮色方案。 @author long
        if (showGitDialog) {
            GitCloneDialog(
                onDismiss = { showGitDialog = false },
                onClone = { url ->
                    showGitDialog = false
                    viewModel.cloneGit(url)
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: ReaderUiState,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenZip: () -> Unit,
    onCloneGit: () -> Unit,
    onOpenBundledProject: (String, String) -> Unit,
    onOpenRecentProjects: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ProductHeader(
            title = "灵阅",
            subtitle = "v${BuildConfig.VERSION_NAME}",
            actions = {
                ThemeToggleButton(state.theme.isDark, onToggleTheme)
                HeaderIconButton(
                    icon = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    onClick = onOpenSettings,
                )
            },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ReaderDimens.pageHorizontal,
                top = ReaderDimens.pageVertical,
                end = ReaderDimens.pageHorizontal,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ReaderDimens.sectionGap),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ReaderDimens.itemGap)) {
                    HomeSectionHeader("打开内容", "选择来源")
                    HomeSourceGrid(
                        onOpenFile = onOpenFile,
                        onOpenFolder = onOpenFolder,
                        onOpenZip = onOpenZip,
                        onCloneGit = onCloneGit,
                    )
                    HomeFeatureRow(
                        title = "最近打开",
                        summary = when {
                            state.recentProjects.isEmpty() -> "打开过的项目会显示在这里"
                            state.recentProjects.size == 1 -> state.recentProjects.first().title
                            else -> "${state.recentProjects.first().title} 等 ${state.recentProjects.size} 个项目"
                        },
                        icon = Icons.Outlined.History,
                        modifier = Modifier.testTag("recent-projects-menu"),
                        onClick = onOpenRecentProjects,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(ReaderDimens.itemGap)) {
                    HomeSectionHeader("示例", "离线可用")
                    HomeFeatureRow(
                        title = "Markdown 功能示例",
                        summary = "代码块、数学公式与 Mermaid",
                        icon = Icons.Outlined.Code,
                        filled = false,
                    ) { onOpenBundledProject("examples", "markdown-example") }
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(ReaderDimens.itemGap)) {
                        HomeSectionHeader("开发工具", "Debug")
                        HomeFeatureRow(
                            title = "内置测试项目",
                            summary = "多语言源码与语法覆盖样例",
                            icon = Icons.Outlined.Code,
                            filled = false,
                        ) { onOpenBundledProject("samples", "sample-project") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectsScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenRecent: (RecentProjectRecord) -> Unit,
    onRemoveRecent: (RecentProjectRecord) -> Unit,
    onOpenFolder: () -> Unit,
    onOpenZip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("recent-projects-page"),
    ) {
        ProductHeader(
            title = "最近打开",
            subtitle = if (state.recentProjects.isEmpty()) "暂无项目" else "${state.recentProjects.size} 个项目",
            onBack = onBack,
        )
        if (state.recentProjects.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp).testTag("recent-projects-empty"),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReaderIconBadge(Icons.Outlined.History, ReaderBadgeTone.SECONDARY)
                    Text(
                        "还没有最近项目",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "打开目录或导入 ZIP 后，可从这里快速继续阅读。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenFolder, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("打开项目")
                    }
                    TextButton(onClick = onOpenZip) {
                        Icon(Icons.Outlined.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("导入 ZIP")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("recent-projects-list"),
                contentPadding = PaddingValues(
                    start = ReaderDimens.pageHorizontal,
                    top = 12.dp,
                    end = ReaderDimens.pageHorizontal,
                    bottom = 28.dp,
                ),
            ) {
                items(
                    items = state.recentProjects,
                    key = { project -> "${project.kind}:${project.value}" },
                ) { project ->
                    RecentProjectRow(
                        project = project,
                        onOpen = { onOpenRecent(project) },
                        onRemove = { onRemoveRecent(project) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ReaderDimens.topBarHeight)
                    .padding(horizontal = 4.dp)
                    .testTag("product-header"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(ReaderDimens.iconTouchTarget),
                    contentAlignment = Alignment.Center,
                ) {
                    if (onBack != null) {
                        HeaderIconButton(
                            icon = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            onClick = onBack,
                        )
                    } else {
                        ReaderIconBadge(
                            icon = Icons.Outlined.Code,
                            tone = ReaderBadgeTone.PRIMARY,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = title,
                        modifier = Modifier.testTag("product-header-title"),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp), content = actions)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun HeaderIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(ReaderDimens.iconTouchTarget),
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun HomeSectionHeader(title: String, meta: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            meta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 144.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeSourceGrid(
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenZip: () -> Unit,
    onCloneGit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceActionTile(
                title = "文件",
                summary = "单个源码",
                icon = Icons.Outlined.Description,
                onClick = onOpenFile,
                modifier = Modifier.weight(1f),
            )
            SourceActionTile(
                title = "项目",
                summary = "目录授权",
                icon = Icons.Outlined.FolderOpen,
                onClick = onOpenFolder,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceActionTile(
                title = "ZIP",
                summary = "离线导入",
                icon = Icons.Outlined.Archive,
                onClick = onOpenZip,
                modifier = Modifier.weight(1f),
                compact = true,
            )
            SourceActionTile(
                title = "Git",
                summary = "HTTPS 克隆",
                icon = Icons.Outlined.CloudDownload,
                onClick = onCloneGit,
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
    }
}

@Composable
private fun SourceActionTile(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val minHeight = if (compact) ReaderDimens.homeSecondarySourceHeight else ReaderDimens.homePrimarySourceHeight
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        ) {
            ReaderIconBadge(icon = icon, tone = ReaderBadgeTone.PRIMARY, compact = compact)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentProjectRow(project: RecentProjectRecord, onOpen: () -> Unit, onRemove: () -> Unit) {
    ReaderListRow(
        title = project.title,
        summary = if (project.kind == "saf") "系统目录" else "本地导入",
        icon = if (project.kind == "saf") Icons.Outlined.FolderOpen else Icons.Outlined.Archive,
        tone = ReaderBadgeTone.SECONDARY,
        filled = false,
        onClick = onOpen,
        trailing = {
            IconButton(onClick = onRemove, modifier = Modifier.size(ReaderDimens.iconTouchTarget)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除最近项目", modifier = Modifier.size(20.dp))
            }
        },
    )
}

@Composable
private fun HomeFeatureRow(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    ReaderListRow(
        title = title,
        summary = summary,
        icon = icon,
        tone = ReaderBadgeTone.TERTIARY,
        filled = filled,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

internal enum class ReaderBadgeTone { PRIMARY, SECONDARY, TERTIARY }

@Composable
internal fun ReaderIconBadge(
    icon: ImageVector,
    tone: ReaderBadgeTone,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val container = when (tone) {
        ReaderBadgeTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
        ReaderBadgeTone.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
        ReaderBadgeTone.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = when (tone) {
        ReaderBadgeTone.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
        ReaderBadgeTone.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
        ReaderBadgeTone.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Box(
        modifier = modifier
            .size(if (compact) ReaderDimens.compactIconBadge else ReaderDimens.iconBadge)
            .background(container, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp),
        )
    }
}

@Composable
private fun ReaderListRow(
    title: String,
    summary: String,
    icon: ImageVector,
    tone: ReaderBadgeTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = if (filled) MaterialTheme.colorScheme.surfaceContainerLow else ComposeColor.Transparent,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = ReaderDimens.listRowMinHeight).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReaderIconBadge(icon = icon, tone = tone)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onSetFontSize: (Float) -> Unit,
    onSetReaderBackground: (ReaderBackground) -> Unit,
    onSetAppPalette: (AppColorPalette) -> Unit,
    onSetTheme: (ReaderTheme) -> Unit,
    onSetWordWrap: (Boolean) -> Unit,
    onOpenSettingsPage: (SettingsPage) -> Unit,
    onCheckUpdate: () -> Unit,
    onShowUpdateDetails: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val (title, subtitle) = when (state.settingsPage) {
        SettingsPage.ROOT -> "设置" to "按类型管理应用偏好"
        SettingsPage.READING -> "阅读与显示" to "源码与 Markdown"
        SettingsPage.APPEARANCE -> "应用外观" to "配色与明暗模式"
        SettingsPage.UPDATE -> "关于与更新" to "版本与在线更新"
    }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ProductHeader(title = title, subtitle = subtitle, onBack = onBack)
        when (state.settingsPage) {
            SettingsPage.ROOT -> SettingsCategoryList(
                state = state,
                onOpenPage = onOpenSettingsPage,
            )
            SettingsPage.READING -> SettingsWithPreview(
                state = state,
                pageTag = "settings-page-reading",
            ) { modifier ->
                ReadingSettingsList(
                    state = state,
                    onSetFontSize = onSetFontSize,
                    onSetReaderBackground = onSetReaderBackground,
                    onSetWordWrap = onSetWordWrap,
                    modifier = modifier,
                )
            }
            SettingsPage.APPEARANCE -> SettingsWithPreview(
                state = state,
                pageTag = "settings-page-appearance",
            ) { modifier ->
                AppearanceSettingsList(
                    state = state,
                    onSetAppPalette = onSetAppPalette,
                    onSetTheme = onSetTheme,
                    modifier = modifier,
                )
            }
            SettingsPage.UPDATE -> UpdateSettingsList(
                state = state,
                onCheckUpdate = onCheckUpdate,
                onShowUpdateDetails = onShowUpdateDetails,
                onInstallUpdate = onInstallUpdate,
            )
        }
    }
    AppUpdateDialog(
        state = state.appUpdate,
        onDismiss = onDismissUpdate,
        onDownload = onDownloadUpdate,
        onInstall = onInstallUpdate,
    )
}

@Composable
private fun SettingsCategoryList(
    state: ReaderUiState,
    onOpenPage: (SettingsPage) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().testTag("settings-page-root")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings-root-list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            item { SettingsSectionHeader("设置分类", "选择要调整的内容") }
            item {
                SettingsCategoryRow(
                    title = "阅读与显示",
                    summary = "${state.settings.fontSizeSp.toInt()} sp · ${state.settings.background.displayName} · ${if (state.settings.wordWrap) "自动换行" else "保持长行"}",
                    icon = Icons.Outlined.Visibility,
                    testTag = "settings-category-reading",
                    onClick = { onOpenPage(SettingsPage.READING) },
                )
            }
            item { SettingsNavigationDivider() }
            item {
                SettingsCategoryRow(
                    title = "应用外观",
                    summary = "${state.settings.appPalette.displayName} · ${if (state.theme.isDark) "Darcula 暗色" else "高对比亮色"}",
                    icon = Icons.Outlined.DarkMode,
                    testTag = "settings-category-appearance",
                    onClick = { onOpenPage(SettingsPage.APPEARANCE) },
                )
            }
            item { SettingsNavigationDivider() }
            item {
                SettingsCategoryRow(
                    title = "关于与更新",
                    summary = "当前版本 v${BuildConfig.VERSION_NAME} · 在线检查与安装",
                    icon = Icons.Outlined.CloudDownload,
                    testTag = "settings-category-update",
                    onClick = { onOpenPage(SettingsPage.UPDATE) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    title: String,
    summary: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
) {
    ReaderListRow(
        title = title,
        summary = summary,
        icon = icon,
        tone = ReaderBadgeTone.SECONDARY,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag(testTag),
        filled = false,
        trailing = {
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

@Composable
private fun SettingsNavigationDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun SettingsWithPreview(
    state: ReaderUiState,
    pageTag: String,
    settingsContent: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag(pageTag)) {
        // 二级设置只保留与预览相关的选项；横屏采用左右分栏，竖屏固定预览并让下方选项独立滚动。
        if (maxWidth > maxHeight) {
            Row(modifier = Modifier.fillMaxSize()) {
                SettingsPreview(
                    settings = state.settings,
                    darkTheme = state.theme.isDark,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                settingsContent(Modifier.weight(1.1f).fillMaxHeight())
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsPreview(
                    settings = state.settings,
                    darkTheme = state.theme.isDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ReaderDimens.settingsPreviewMaxHeight),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                settingsContent(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSettingsList(
    state: ReaderUiState,
    onSetFontSize: (Float) -> Unit,
    onSetReaderBackground: (ReaderBackground) -> Unit,
    onSetWordWrap: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    LazyColumn(
        modifier = modifier.testTag("settings-list"),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
            item { SettingsSectionHeader("阅读体验", "源码和 Markdown 统一使用手机系统字体") }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("字体大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text(
                                "${settings.fontSizeSp.toInt()} sp",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Text(
                            "源码和 Markdown 正文同步缩放",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = settings.fontSizeSp,
                            onValueChange = onSetFontSize,
                            valueRange = 11f..24f,
                            steps = 12,
                            thumb = { ReaderSliderThumb() },
                            modifier = Modifier.testTag("font-size-slider"),
                        )
                    }
                }
            }

            item {
                SettingsDropdownField(
                    label = "阅读背景",
                    selected = settings.background,
                    options = ReaderBackground.entries,
                    optionKey = ReaderBackground::preferenceValue,
                    optionTitle = ReaderBackground::displayName,
                    optionSummary = ReaderBackground::description,
                    selectorTag = "background-selector",
                    optionTagPrefix = "background",
                    onSelected = onSetReaderBackground,
                    leading = { background ->
                        ColorSwatch(
                            color = ComposeColor(background.colorArgb(state.theme.isDark)),
                            borderColor = MaterialTheme.colorScheme.outline,
                        )
                    },
                )
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp)
                        .toggleable(
                            value = settings.wordWrap,
                            role = Role.Switch,
                            onValueChange = onSetWordWrap,
                        )
                        .semantics {
                            stateDescription = if (settings.wordWrap) "已开启自动换行" else "已关闭自动换行"
                        }
                        .testTag("word-wrap-setting"),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("源码自动换行", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("长行不再需要横向滚动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.wordWrap,
                            onCheckedChange = null,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
    }
}

@Composable
private fun AppearanceSettingsList(
    state: ReaderUiState,
    onSetAppPalette: (AppColorPalette) -> Unit,
    onSetTheme: (ReaderTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    LazyColumn(
        modifier = modifier.testTag("settings-list"),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
    ) {
            item { SettingsSectionHeader("外观偏好", "集中选择应用强调色和明暗模式") }
            item {
                SettingsDropdownField(
                    label = "整体配色",
                    selected = settings.appPalette,
                    options = AppColorPalette.entries,
                    optionKey = AppColorPalette::preferenceValue,
                    optionTitle = AppColorPalette::displayName,
                    optionSummary = AppColorPalette::description,
                    selectorTag = "palette-selector",
                    optionTagPrefix = "palette",
                    onSelected = onSetAppPalette,
                    leading = { palette ->
                        ColorSwatch(
                            color = paletteSwatch(palette, state.theme.isDark),
                            borderColor = MaterialTheme.colorScheme.outline,
                        )
                    },
                )
            }

            item {
                SettingsDropdownField(
                    label = "明暗模式",
                    selected = state.theme,
                    options = ReaderTheme.entries,
                    optionKey = ReaderTheme::preferenceValue,
                    optionTitle = { theme -> if (theme.isDark) "Darcula 暗色" else "高对比亮色" },
                    optionSummary = { theme -> if (theme.isDark) "适合夜间和低亮度环境" else "适合白天和明亮环境" },
                    selectorTag = "theme-selector",
                    optionTagPrefix = "theme",
                    onSelected = onSetTheme,
                    leading = { theme ->
                        Icon(
                            if (theme.isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }
    }
}

@Composable
private fun UpdateSettingsList(
    state: ReaderUiState,
    onCheckUpdate: () -> Unit,
    onShowUpdateDetails: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().testTag("settings-page-update")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings-list"),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            item { SettingsSectionHeader("版本管理", "从 GitHub Releases 检查已签名版本") }
            item {
                AppUpdateSettingRow(
                    state = state.appUpdate,
                    onCheck = onCheckUpdate,
                    onShowDetails = onShowUpdateDetails,
                    onInstall = onInstallUpdate,
                )
            }
        }
    }
}

@Composable
private fun SettingsPreview(
    settings: ReaderSettings,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val background = ComposeColor(settings.background.colorArgb(darkTheme))
    val foreground = if (darkTheme) ComposeColor(0xFFE5E7EB) else ComposeColor(0xFF1F2937)
    val keyword = MaterialTheme.colorScheme.primary
    val type = MaterialTheme.colorScheme.secondary
    val literal = MaterialTheme.colorScheme.tertiary
    val syntaxLines = listOf(
        buildAnnotatedString {
            withStyle(SpanStyle(color = literal)) { append("@Serializable") }
            append(" ")
            withStyle(SpanStyle(color = keyword, fontWeight = FontWeight.SemiBold)) { append("data class") }
            append(" User(")
        },
        buildAnnotatedString {
            append("  ")
            withStyle(SpanStyle(color = keyword)) { append("val") }
            append(" id: ")
            withStyle(SpanStyle(color = type)) { append("Long") }
            append(",")
        },
        buildAnnotatedString {
            append("  ")
            withStyle(SpanStyle(color = keyword)) { append("val") }
            append(" name: ")
            withStyle(SpanStyle(color = type)) { append("String") }
            append(" = ")
            withStyle(SpanStyle(color = literal)) { append("\"guest\"") }
            append(",")
        },
        buildAnnotatedString { append(")") },
        buildAnnotatedString {
            withStyle(SpanStyle(color = keyword, fontWeight = FontWeight.SemiBold)) { append("fun") }
            append(" findUser(id: ")
            withStyle(SpanStyle(color = type)) { append("Long") }
            append("): ")
            withStyle(SpanStyle(color = type)) { append("User?") }
            append(" =")
        },
        buildAnnotatedString {
            append("  cache[id]?.")
            withStyle(SpanStyle(color = secondarySyntaxColor(darkTheme))) { append("takeIf") }
            append(" { it.active }")
        },
    )
    Surface(
        modifier = modifier.testTag("settings-preview"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 10.dp)) {
            HomeSectionHeader("实时预览", "Reader.kt")
            Spacer(Modifier.height(7.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        Text(
                            "Reader.kt",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(start = 7.dp),
                        )
                    }
                    Column(modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        syntaxLines.forEachIndexed { index, line ->
                            Row {
                                Text(
                                    "${index + 1}",
                                    color = foreground.copy(alpha = 0.42f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = (settings.fontSizeSp - 2f).coerceAtLeast(9f).sp,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    line,
                                    color = foreground,
                                    fontSize = settings.fontSizeSp.sp,
                                    lineHeight = (settings.fontSizeSp + 3f).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun secondarySyntaxColor(darkTheme: Boolean): ComposeColor =
    if (darkTheme) ComposeColor(0xFFB7D7FF) else ComposeColor(0xFF235FA4)

@Composable
private fun SettingsSectionHeader(title: String, summary: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 7.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdownField(
    label: String,
    selected: T,
    options: List<T>,
    optionKey: (T) -> String,
    optionTitle: (T) -> String,
    optionSummary: (T) -> String,
    selectorTag: String,
    optionTagPrefix: String,
    onSelected: (T) -> Unit,
    leading: @Composable (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        OutlinedTextField(
            value = optionTitle(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            supportingText = {
                Text(
                    optionSummary(selected),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) { leading(selected) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .testTag(selectorTag),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .readerMenuSurface()
                .testTag("$selectorTag-menu"),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                optionTitle(option),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Medium,
                            )
                            Text(
                                optionSummary(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingIcon = {
                        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) { leading(option) }
                    },
                    onClick = {
                        expanded = false
                        if (option != selected) onSelected(option)
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .heightIn(min = ReaderDimens.compactRowMinHeight)
                        .testTag("$optionTagPrefix-${optionKey(option)}"),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: ComposeColor, borderColor: ComposeColor) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(color, CircleShape)
            .border(1.dp, borderColor.copy(alpha = 0.7f), CircleShape),
    )
}

@Composable
private fun ReaderSliderThumb() {
    Surface(
        modifier = Modifier.size(18.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
        shadowElevation = 1.dp,
    ) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onEntry: (SourceEntry) -> Unit,
    onSearch: (String) -> Unit,
    onSearchResult: (ProjectSearchResult) -> Unit,
    onUpdateGit: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val browserTitle = state.browserTitle ?: return
    var searchVisible by rememberSaveable(browserTitle) { mutableStateOf(state.projectSearchQuery.isNotBlank()) }
    var searchText by rememberSaveable(browserTitle) { mutableStateOf(state.projectSearchQuery) }
    val searchFocusRequester = remember(browserTitle) { FocusRequester() }

    LaunchedEffect(searchVisible) {
        if (searchVisible) searchFocusRequester.requestFocus()
    }

    val clearSearch = {
        searchText = ""
        onSearch("")
    }
    val submitSearch = {
        val query = searchText.trim()
        if (query.isNotEmpty()) onSearch(query)
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ProductHeader(
            title = browserTitle,
            subtitle = when {
                state.projectSearchInProgress -> "正在搜索项目内容"
                state.projectSearchError != null -> "项目搜索失败"
                state.projectSearchQuery.isNotBlank() && state.projectSearchResults.isEmpty() -> "没有匹配结果"
                state.projectSearchQuery.isNotBlank() -> "已显示 ${state.projectSearchResults.size} 条搜索结果"
                else -> "${state.projectEntries.count { !it.source.isDirectory }} 个文件"
            },
            onBack = onBack,
            actions = {
                HeaderIconButton(
                    icon = if (searchVisible) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ManageSearch,
                    contentDescription = if (searchVisible) "关闭项目搜索" else "项目全局搜索",
                    onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) clearSearch()
                    },
                )
                // 搜索模式只保留关闭入口，避免窄屏标题栏同时堆放四组操作。 @author long
                if (!searchVisible) {
                    if (state.gitRepositoryRoot != null) {
                        HeaderIconButton(
                            icon = Icons.Outlined.Sync,
                            contentDescription = "获取最新代码",
                            onClick = onUpdateGit,
                        )
                    }
                    ThemeToggleButton(state.theme.isDark, onToggleTheme)
                }
            },
        )
        if (searchVisible) {
            Column {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { value ->
                        searchText = value
                        if (state.projectSearchQuery.isNotBlank() && value.trim() != state.projectSearchQuery) onSearch("")
                    },
                    label = { Text("搜索项目内容") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        val normalizedText = searchText.trim()
                        val querySubmitted = normalizedText.isNotEmpty() && normalizedText == state.projectSearchQuery
                        IconButton(
                            onClick = if (querySubmitted) clearSearch else submitSearch,
                            enabled = normalizedText.isNotEmpty(),
                        ) {
                            Icon(
                                if (querySubmitted) Icons.Outlined.Close else Icons.Outlined.Search,
                                contentDescription = if (querySubmitted) "清除项目搜索" else "开始搜索",
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .focusRequester(searchFocusRequester)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
                if (state.projectSearchInProgress) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        }

        if (state.projectSearchQuery.isNotBlank()) {
            ProjectSearchResults(
                query = state.projectSearchQuery,
                results = state.projectSearchResults,
                searching = state.projectSearchInProgress,
                error = state.projectSearchError,
                onRetry = { onSearch(state.projectSearchQuery) },
                onClear = clearSearch,
                onOpen = onSearchResult,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("project-list"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(state.visibleProjectEntries, key = { it.source.id }) { indexed ->
                    ProjectTreeRow(indexed, state.expandedDirectoryIds, onEntry)
                }
            }
        }
    }
}

@Composable
private fun ProjectTreeRow(
    indexed: ProjectTreeEntry,
    expandedIds: Set<String>,
    onEntry: (SourceEntry) -> Unit,
) {
    val entry = indexed.source
    val type = FileType.detect(entry.name)
    val expanded = entry.isDirectory && entry.id in expandedIds
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onEntry(entry) }
            .semantics(mergeDescendants = true) {
                contentDescription = if (entry.isDirectory) "${indexed.path}，目录" else "${indexed.path}，${type.displayName}"
                if (entry.isDirectory) stateDescription = if (expanded) "已展开" else "已折叠"
            },
        color = if (expanded) MaterialTheme.colorScheme.surfaceContainerLow else ComposeColor.Transparent,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ReaderDimens.compactRowMinHeight)
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 深层工程只压缩视觉缩进，完整相对路径仍通过无障碍描述保留。 @author long
            Spacer(Modifier.width((indexed.depth.coerceAtMost(5) * 12).dp))
            if (entry.isDirectory) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(Modifier.width(20.dp))
            }
            FileGlyph(isDirectory = entry.isDirectory, fileType = type, modifier = Modifier.padding(horizontal = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!entry.isDirectory) {
                    Text(
                        "${type.displayName} · ${formatBytes(entry.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileGlyph(isDirectory: Boolean, fileType: FileType, modifier: Modifier = Modifier) {
    val tone = when {
        isDirectory -> ReaderBadgeTone.TERTIARY
        fileType.markdown -> ReaderBadgeTone.SECONDARY
        else -> ReaderBadgeTone.PRIMARY
    }
    ReaderIconBadge(
        icon = if (isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
        tone = tone,
        modifier = modifier,
        compact = true,
    )
}

@Composable
private fun ProjectSearchResults(
    query: String,
    results: List<ProjectSearchResult>,
    searching: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onOpen: (ProjectSearchResult) -> Unit,
) {
    when {
        searching -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Text(
                        "正在搜索“$query”",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return
        }
        error != null -> {
            ProjectSearchEmptyState(
                title = "搜索失败",
                summary = error,
                actionLabel = "重试",
                onAction = onRetry,
            )
            return
        }
        results.isEmpty() -> {
            ProjectSearchEmptyState(
                title = "没有找到“$query”",
                summary = "换一个关键词，或返回项目目录继续浏览",
                actionLabel = "返回目录",
                onAction = onClear,
            )
            return
        }
    }

    val highlightBackground = MaterialTheme.colorScheme.secondaryContainer
    val highlightForeground = MaterialTheme.colorScheme.onSecondaryContainer
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("project-search-results"),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        item(key = "search-summary") {
            Text(
                if (results.size >= PROJECT_SEARCH_RESULT_LIMIT) {
                    "已显示前 ${results.size} 条，可能还有更多"
                } else {
                    "已显示 ${results.size} 条"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        items(results, key = { "${it.source.id}:${it.line}" }) { result ->
            val fileName = result.path.substringAfterLast('/')
            val parentPath = result.path.substringBeforeLast('/', missingDelimiterValue = "")
            Surface(
                onClick = { onOpen(result) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${result.path}，第 ${result.line} 行，${result.excerpt}"
                    },
                color = ComposeColor.Transparent,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ReaderIconBadge(Icons.Outlined.FindInPage, ReaderBadgeTone.SECONDARY, compact = true)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            "$fileName · 第 ${result.line} 行",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (parentPath.isNotEmpty()) {
                            Text(
                                parentPath,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Text(
                            highlightedSearchExcerpt(
                                text = result.excerpt,
                                query = query,
                                background = highlightBackground,
                                foreground = highlightForeground,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        }
    }
}

@Composable
private fun ProjectSearchEmptyState(
    title: String,
    summary: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.FindInPage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
                Text(actionLabel)
            }
        }
    }
}

private fun highlightedSearchExcerpt(
    text: String,
    query: String,
    background: ComposeColor,
    foreground: ComposeColor,
) = buildAnnotatedString {
    val needle = query.trim()
    if (needle.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var cursor = 0
    while (cursor < text.length) {
        val match = text.indexOf(needle, startIndex = cursor, ignoreCase = true)
        if (match < 0) {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, match))
        withStyle(SpanStyle(background = background, color = foreground, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(match, match + needle.length))
        }
        cursor = match + needle.length
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onEditable: (Boolean) -> Unit,
    onTextChanged: (String) -> Unit,
    onSave: () -> Unit,
    onTogglePreview: () -> Unit,
    onSwitchTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onOpenEntry: (SourceEntry) -> Unit,
    onSearchInFile: (String, Boolean) -> Unit,
    onGotoLine: (Int) -> Unit,
    onGotoHeading: (Int) -> Unit,
    onSetFontSize: (Float) -> Unit,
    onSetWordWrap: (Boolean) -> Unit,
    onLoadMore: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val document = state.document ?: return
    var searchVisible by remember(document.id) { mutableStateOf(false) }
    var fileSearchText by remember(document.id) { mutableStateOf("") }
    var showFileSwitcher by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showGotoLine by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingCloseTabId by remember { mutableStateOf<String?>(null) }
    val projectPath = state.projectEntries.firstOrNull { it.source.id == document.id }?.path
    val documentStatus = when {
        document.largeFile && document.totalBytes >= 0 -> "分段读取 · ${formatBytes(document.totalBytes)}"
        document.largeFile -> "分段读取 · 大小未知"
        state.dirty -> "未保存"
        state.editable -> "编辑中"
        else -> "只读"
    }
    val displayPath = projectPath?.takeUnless { it == document.name }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ReaderDimens.topBarHeight)
                        .padding(horizontal = 4.dp)
                        .testTag("reader-header"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
                    FileGlyph(isDirectory = false, fileType = document.fileType)
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp, end = 2.dp)) {
                        Text(
                            text = document.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                document.fileType.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace,
                            )
                            ReaderStatusBadge(documentStatus, emphasized = state.dirty || state.editable)
                            if (displayPath != null) {
                                Text(
                                    text = displayPath,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                                )
                            }
                        }
                    }
                    HeaderIconButton(
                        Icons.Outlined.Search,
                        if (searchVisible) "关闭文件内搜索" else "文件内搜索",
                    ) { searchVisible = !searchVisible }
                    Box {
                        HeaderIconButton(Icons.Outlined.MoreVert, "更多") { menuExpanded = true }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.readerMenuSurface().testTag("reader-more-menu"),
                        ) {
                            if (!state.markdownPreview) {
                                DropdownMenuItem(
                                    text = { Text("跳转到行") },
                                    leadingIcon = { Icon(Icons.Outlined.UnfoldMore, contentDescription = null) },
                                    onClick = { menuExpanded = false; showGotoLine = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.heightIn(min = ReaderDimens.iconTouchTarget),
                                )
                            }
                            if (document.fileType.markdown && state.markdownPreview && state.markdownHeadings.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Markdown 目录") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                                    onClick = { menuExpanded = false; showOutline = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.heightIn(min = ReaderDimens.iconTouchTarget),
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("阅读设置") },
                                leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                onClick = { menuExpanded = false; showSettings = true },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.heightIn(min = ReaderDimens.iconTouchTarget),
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.theme.isDark) "切换为亮色" else "切换为暗色") },
                                leadingIcon = {
                                    Icon(if (state.theme.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, contentDescription = null)
                                },
                                onClick = { menuExpanded = false; onToggleTheme() },
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.heightIn(min = ReaderDimens.iconTouchTarget),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            }
        }

        if (state.tabs.size > 1) {
            ReaderTabs(
                state = state,
                onSwitch = onSwitchTab,
                onClose = { tab ->
                    if (tab.dirty) pendingCloseTabId = tab.document.id else onCloseTab(tab.document.id)
                },
            )
        }

        AnimatedContent(
            targetState = searchVisible,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "reader-toolbar",
        ) { searching ->
            if (searching) {
                FileSearchBar(
                    text = fileSearchText,
                    onTextChanged = { fileSearchText = it },
                    onPrevious = { onSearchInFile(fileSearchText, false) },
                    onNext = { onSearchInFile(fileSearchText, true) },
                    onClose = { searchVisible = false },
                )
            } else {
                ReaderActionBar(
                    hasProject = state.projectEntries.isNotEmpty(),
                    markdown = document.fileType.markdown,
                    markdownPreview = state.markdownPreview,
                    editable = state.editable,
                    dirty = state.dirty,
                    onOpenFileSwitcher = { showFileSwitcher = true },
                    onTogglePreview = onTogglePreview,
                    onToggleEditable = { onEditable(!state.editable) },
                    onSave = onSave,
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // 跨文件标签切换时继续保留 Markdown WebView，避免从源码文件回到 Markdown 时白屏重建。
            val markdownSurfaceVisible = document.fileType.markdown && state.markdownPreview
            MarkdownPreview(
                markdownText = state.draftText,
                darkTheme = state.theme.isDark,
                fontSizeSp = state.settings.fontSizeSp,
                backgroundColorArgb = state.settings.background.colorArgb(state.theme.isDark),
                command = state.readerCommand,
                active = markdownSurfaceVisible,
                modifier = readerSurfaceLayer(markdownSurfaceVisible),
            )
            CodeEditorView(
                documentId = document.id,
                text = state.draftText,
                fileType = document.fileType,
                editable = state.editable,
                fontSizeSp = state.settings.fontSizeSp,
                backgroundColorArgb = state.settings.background.colorArgb(state.theme.isDark),
                wordWrap = state.settings.wordWrap,
                command = state.readerCommand,
                onTextChanged = onTextChanged,
                modifier = if (document.fileType.markdown) {
                    readerSurfaceLayer(!state.markdownPreview)
                } else {
                    Modifier.fillMaxSize()
                },
            )
            if (document.hasMore) {
                Button(
                    onClick = onLoadMore,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                ) {
                    Text("继续加载")
                }
            }
        }
    }

    if (showFileSwitcher) {
        FileSwitcherSheet(
            entries = state.projectEntries.filterNot { it.source.isDirectory },
            onDismiss = { showFileSwitcher = false },
            onOpen = { showFileSwitcher = false; onOpenEntry(it) },
        )
    }
    if (showOutline) {
        MarkdownOutlineSheet(
            state = state,
            onDismiss = { showOutline = false },
            onHeading = { showOutline = false; onGotoHeading(it) },
        )
    }
    if (showSettings) {
        ReaderSettingsSheet(
            settings = state.settings,
            onDismiss = { showSettings = false },
            onSetFontSize = onSetFontSize,
            onSetWordWrap = onSetWordWrap,
        )
    }
    if (showGotoLine) {
        GotoLineDialog(
            onDismiss = { showGotoLine = false },
            onGoto = { showGotoLine = false; onGotoLine(it) },
        )
    }
    pendingCloseTabId?.let { tabId ->
        val tab = state.tabs.firstOrNull { it.document.id == tabId }
        if (tab != null) {
            ReaderDialog(
                onDismissRequest = { pendingCloseTabId = null },
                title = "放弃未保存修改？",
                icon = Icons.Outlined.DeleteOutline,
                modifier = Modifier.testTag("unsaved-close-dialog"),
                actions = {
                    TextButton(onClick = { pendingCloseTabId = null }) { Text("继续编辑") }
                    Button(
                        onClick = { pendingCloseTabId = null; onCloseTab(tabId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.testTag("discard-tab-button"),
                    ) {
                        Text("放弃并关闭")
                    }
                },
            ) {
                Text(
                    "${tab.document.name} 还有未保存内容，关闭后无法恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Markdown 的两个原生阅读内核保持在同一个层叠容器中，切换时只改变可见层。 @author long */
private fun readerSurfaceLayer(visible: Boolean): Modifier = Modifier
    .fillMaxSize()
    .zIndex(if (visible) 1f else 0f)
    .alpha(if (visible) 1f else 0f)
    .then(
        if (visible) Modifier else Modifier.clearAndSetSemantics { hideFromAccessibility() },
    )

@Composable
private fun ReaderStatusBadge(text: String, emphasized: Boolean) {
    Surface(
        modifier = Modifier.padding(start = 6.dp),
        color = if (emphasized) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun ReaderTabs(state: ReaderUiState, onSwitch: (String) -> Unit, onClose: (ReaderTabState) -> Unit) {
    val listState = rememberLazyListState()
    val activeIndex = state.tabs.indexOfFirst { it.document.id == state.activeTabId }
    LaunchedEffect(state.activeTabId, state.tabs.size) {
        // 新文件通常追加在标签栏末尾，主动滚动可避免标题已切换但活动标签仍在屏幕外。
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReaderDimens.iconTouchTarget)
                .testTag("reader-tab-strip"),
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            state = listState,
        ) {
            items(state.tabs, key = { it.document.id }) { tab ->
                val active = tab.document.id == state.activeTabId
                Box(
                    modifier = Modifier
                        .height(ReaderDimens.iconTouchTarget)
                        .widthIn(min = 112.dp, max = 196.dp)
                        .selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { onSwitch(tab.document.id) },
                        )
                        .semantics {
                            stateDescription = if (active) "当前文件" else "未选中文件"
                        },
                ) {
                    // 标签的可见底板独立于 48dp 点击区域，降低阅读页占用感但不牺牲触控命中率。 @author long
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(ReaderDimens.readerTabVisualHeight)
                            .testTag(if (active) "reader-active-tab-visual" else "reader-inactive-tab-visual"),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else ComposeColor.Transparent,
                            shape = MaterialTheme.shapes.small,
                        ) {}
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .width(24.dp)
                                    .height(2.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                                    ),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.document.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (tab.dirty) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(5.dp)
                                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                            )
                        }
                        IconButton(
                            onClick = { onClose(tab) },
                            modifier = Modifier.size(ReaderDimens.iconTouchTarget),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "关闭 ${tab.document.name}",
                                tint = if (active) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSearchBar(
    text: String,
    onTextChanged: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChanged,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) onNext() }),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.FindInPage,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        )
                        Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            if (text.isBlank()) {
                                Text(
                                    "文件内查找",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f),
                                )
                            }
                            innerTextField()
                        }
                    }
                },
                modifier = Modifier.weight(1f).height(38.dp).focusRequester(focusRequester),
            )
            IconButton(onClick = onPrevious, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.NavigateBefore, contentDescription = "上一个")
            }
            IconButton(onClick = onNext, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Outlined.NavigateNext, contentDescription = "下一个")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭搜索")
            }
        }
    }
}

@Composable
internal fun ReaderActionBar(
    hasProject: Boolean,
    markdown: Boolean,
    markdownPreview: Boolean,
    editable: Boolean,
    dirty: Boolean,
    onOpenFileSwitcher: () -> Unit,
    onTogglePreview: () -> Unit,
    onToggleEditable: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReaderDimens.iconTouchTarget)
                .padding(horizontal = 2.dp)
                .testTag("reader-action-bar"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasProject) {
                ReaderActionButton(
                    icon = Icons.Outlined.FolderOpen,
                    contentDescription = "快速切换文件",
                    onClick = onOpenFileSwitcher,
                )
            }
            if (markdown) {
                ReaderActionButton(
                    icon = if (markdownPreview) Icons.Outlined.Code else Icons.Outlined.Visibility,
                    contentDescription = if (markdownPreview) "查看源码" else "预览 Markdown",
                    selected = markdownPreview,
                    stateDescription = if (markdownPreview) "当前为 Markdown 预览" else "当前为 Markdown 源码",
                    onClick = onTogglePreview,
                )
            }
            ReaderActionButton(
                icon = if (editable) Icons.Outlined.Lock else Icons.Outlined.Edit,
                contentDescription = if (editable) "退出编辑" else "编辑",
                selected = editable,
                stateDescription = if (editable) "编辑已开启" else "当前为只读模式",
                onClick = onToggleEditable,
            )
            if (dirty) {
                ReaderActionButton(
                    icon = Icons.Outlined.Save,
                    contentDescription = "保存",
                    emphasized = true,
                    stateDescription = "存在未保存修改",
                    onClick = onSave,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean = false,
    emphasized: Boolean = false,
    stateDescription: String? = null,
    onClick: () -> Unit,
) {
    val containerColor = when {
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> ComposeColor.Transparent
    }
    val contentColor = when {
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        selected -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f)
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(ReaderDimens.iconTouchTarget)
            .testTag("reader-action-touch-$contentDescription")
            .semantics {
                if (stateDescription != null) this.stateDescription = stateDescription
            },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = ComposeColor.Transparent,
            contentColor = contentColor,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(ReaderDimens.readerActionVisualSize)
                .background(containerColor, MaterialTheme.shapes.small)
                .testTag("reader-action-visual-$contentDescription"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ReaderDimens.readerActionIconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSwitcherSheet(
    entries: List<ProjectTreeEntry>,
    onDismiss: () -> Unit,
    onOpen: (SourceEntry) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val visible = entries.filter { filter.isBlank() || it.path.contains(filter, ignoreCase = true) }
    ReaderBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("file-switcher-sheet"),
    ) {
        ReaderSheetHeader(title = "快速切换文件", icon = Icons.Outlined.FolderOpen) {
            Text("${visible.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("按路径过滤") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            shape = MaterialTheme.shapes.medium,
            colors = readerOverlayTextFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(440.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(visible, key = { it.source.id }) { indexed ->
                Surface(
                    onClick = { onOpen(indexed.source) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = ReaderDimens.compactRowMinHeight)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "打开 ${indexed.path}"
                        },
                    color = ComposeColor.Transparent,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FileGlyph(isDirectory = false, fileType = FileType.detect(indexed.source.name))
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(indexed.source.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(indexed.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownOutlineSheet(
    state: ReaderUiState,
    onDismiss: () -> Unit,
    onHeading: (Int) -> Unit,
) {
    ReaderBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("markdown-outline-sheet"),
    ) {
        ReaderSheetHeader(title = "Markdown 目录", icon = Icons.AutoMirrored.Outlined.List) {
            Text("${state.markdownHeadings.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().height(460.dp)) {
            items(state.markdownHeadings, key = { it.index }) { heading ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHeading(heading.index) }
                        .padding(start = (20 + (heading.level - 1) * 18).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        "H${heading.level}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(heading.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    onSetFontSize: (Float) -> Unit,
    onSetWordWrap: (Boolean) -> Unit,
) {
    ReaderBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("reader-settings-sheet"),
    ) {
        ReaderSheetHeader(title = "阅读设置", icon = Icons.Outlined.Settings)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("字体大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${settings.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = settings.fontSizeSp,
                onValueChange = onSetFontSize,
                valueRange = 11f..24f,
                steps = 12,
                thumb = { ReaderSliderThumb() },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("源码自动换行", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("长行不再需要横向滚动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.wordWrap, onCheckedChange = onSetWordWrap)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GotoLineDialog(onDismiss: () -> Unit, onGoto: (Int) -> Unit) {
    var lineText by remember { mutableStateOf("") }
    val line = lineText.toIntOrNull()
    ReaderDialog(
        onDismissRequest = onDismiss,
        title = "跳转到行",
        icon = Icons.Outlined.UnfoldMore,
        modifier = Modifier.testTag("goto-line-dialog"),
        actions = {
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(onClick = { onGoto(requireNotNull(line)) }, enabled = line != null && line > 0) { Text("跳转") }
        },
    ) {
        OutlinedTextField(
            value = lineText,
            onValueChange = { lineText = it.filter(Char::isDigit).take(8) },
            label = { Text("行号") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = readerOverlayTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("goto-line-input"),
        )
    }
}

@Composable
private fun ThemeToggleButton(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    HeaderIconButton(
        icon = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
        contentDescription = if (darkTheme) "切换为亮色主题" else "切换为暗色主题",
        onClick = onToggleTheme,
    )
}

@Composable
internal fun ReaderOperationOverlay(operation: ReaderOperationState, onCancel: () -> Unit) {
    Surface(
        color = ComposeColor.Black.copy(alpha = 0.48f),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
            .testTag("operation-overlay"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
                modifier = Modifier.padding(horizontal = 28.dp).widthIn(max = 380.dp).fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ReaderIconBadge(
                            icon = if (operation.kind == ReaderOperationKind.GIT) Icons.Outlined.Sync else Icons.Outlined.HourglassTop,
                            tone = ReaderBadgeTone.PRIMARY,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                operation.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("operation-title"),
                            )
                            operation.detail?.let { detail ->
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp).testTag("operation-detail"),
                                )
                            }
                        }
                    }

                    if (operation.progressPercent != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("当前阶段", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${operation.progressPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { operation.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().testTag("operation-progress"),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().testTag("operation-progress"),
                        )
                    }

                    if (operation.cancellable) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onCancel, modifier = Modifier.testTag("operation-cancel")) {
                                Text("取消")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GitCloneDialog(onDismiss: () -> Unit, onClone: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val normalizedUrl = url.trim()
    val validUrl = remember(normalizedUrl) { GitRepositoryAddress.isValid(normalizedUrl) }
    val invalidUrl = normalizedUrl.isNotEmpty() && !validUrl
    ReaderDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("git-clone-dialog"),
        title = "克隆 Git 仓库",
        icon = Icons.Outlined.CloudDownload,
        actions = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("git-clone-cancel")) {
                Text("取消")
            }
            Button(
                onClick = { onClone(normalizedUrl) },
                enabled = validUrl,
                modifier = Modifier.testTag("git-clone-confirm"),
            ) {
                Icon(Icons.Outlined.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("开始克隆")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "输入公开仓库的 HTTPS 地址。克隆完成后会以仓库名称保存，并可在项目页获取最新代码。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("仓库地址") },
                placeholder = { Text("https://github.com/owner/repository.git") },
                supportingText = {
                    Text(if (invalidUrl) "请输入完整的公开 HTTPS Git 地址" else "固定显示 5 行，长地址会自动换行")
                },
                isError = invalidUrl,
                minLines = 5,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                shape = MaterialTheme.shapes.medium,
                colors = readerOverlayTextFieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (validUrl) onClone(normalizedUrl) }),
                modifier = Modifier.fillMaxWidth().testTag("git-url-input"),
            )
        }
    }
}

@Composable
private fun readerOverlayTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

private fun persistUri(context: android.content.Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
