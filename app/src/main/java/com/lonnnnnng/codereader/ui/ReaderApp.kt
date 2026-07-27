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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
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
import androidx.core.view.WindowCompat
import com.lonnnnnng.codereader.BuildConfig
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
        if (showExitConfirmation) {
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
                            onOpenRecent = viewModel::openRecentProject,
                            onRemoveRecent = viewModel::removeRecentProject,
                            onOpenSettings = viewModel::openSettings,
                            onToggleTheme = viewModel::toggleTheme,
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

                    if (state.busy) {
                        Surface(color = ComposeColor.Black.copy(alpha = 0.38f), modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.medium,
                                    shadowElevation = 6.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                        Text("正在处理", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                modifier = Modifier.testTag("exit-confirmation-dialog"),
                title = { Text("退出灵阅？") },
                text = { Text("未保存的修改不会自动保存，确定要退出应用吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitConfirmation = false
                            (context as? Activity)?.finish()
                        },
                        modifier = Modifier.testTag("exit-confirm-button"),
                    ) {
                        Text("退出应用", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showExitConfirmation = false },
                        modifier = Modifier.testTag("exit-cancel-button"),
                    ) {
                        Text("取消")
                    }
                },
            )
        }
    }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    state: ReaderUiState,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenZip: () -> Unit,
    onCloneGit: () -> Unit,
    onOpenBundledProject: (String, String) -> Unit,
    onOpenRecent: (RecentProjectRecord) -> Unit,
    onRemoveRecent: (RecentProjectRecord) -> Unit,
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
                top = 16.dp,
                end = ReaderDimens.pageHorizontal,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ReaderDimens.sectionGap),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSectionHeader("打开内容", "选择来源")
                    HomeSourceGrid(
                        onOpenFile = onOpenFile,
                        onOpenFolder = onOpenFolder,
                        onOpenZip = onOpenZip,
                        onCloneGit = onCloneGit,
                    )
                }
            }

            if (state.recentProjects.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeSectionHeader("最近项目", "${state.recentProjects.size} 个")
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column {
                                state.recentProjects.forEachIndexed { index, project ->
                                    RecentProjectRow(
                                        project = project,
                                        onOpen = { onOpenRecent(project) },
                                        onRemove = { onRemoveRecent(project) },
                                    )
                                    if (index < state.recentProjects.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 60.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeSectionHeader("示例", "离线可用")
                    HomeFeatureRow(
                        title = "Markdown 功能示例",
                        summary = "代码块、数学公式与 Mermaid",
                        icon = Icons.Outlined.Code,
                    ) { onOpenBundledProject("examples", "markdown-example") }
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HomeSectionHeader("开发工具", "Debug")
                        HomeFeatureRow(
                            title = "内置测试项目",
                            summary = "多语言源码与语法覆盖样例",
                            icon = Icons.Outlined.Code,
                        ) { onOpenBundledProject("samples", "sample-project") }
                    }
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
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ReaderDimens.topBarHeight)
                .padding(horizontal = 8.dp)
                .testTag("product-header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 12.dp)
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Row(content = actions)
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
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            SourceActionTile("文件", "单个源码", Icons.Outlined.Description, onOpenFile, Modifier.weight(1f))
            SourceActionTile("项目", "目录授权", Icons.Outlined.FolderOpen, onOpenFolder, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceActionTile("ZIP", "离线导入", Icons.Outlined.Archive, onOpenZip, Modifier.weight(1f))
            SourceActionTile("Git", "HTTPS 克隆", Icons.Outlined.CloudDownload, onCloneGit, Modifier.weight(1f))
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
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(94.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentProjectRow(project: RecentProjectRecord, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (project.kind == "saf") Icons.Outlined.FolderOpen else Icons.Outlined.Archive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(project.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (project.kind == "saf") "系统目录" else "本地导入",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(ReaderDimens.iconTouchTarget)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除最近项目", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HomeFeatureRow(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            item {
                SettingsCategoryRow(
                    title = "应用外观",
                    summary = "${state.settings.appPalette.displayName} · ${if (state.theme.isDark) "Darcula 暗色" else "高对比亮色"}",
                    icon = Icons.Outlined.DarkMode,
                    testTag = "settings-category-appearance",
                    onClick = { onOpenPage(SettingsPage.APPEARANCE) },
                )
            }
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
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).testTag(testTag),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
                    modifier = Modifier.fillMaxWidth(),
                )
                settingsContent(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

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
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        .clickable { onSetWordWrap(!settings.wordWrap) }
                        .testTag("word-wrap-setting"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("源码自动换行", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("长行不再需要横向滚动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = settings.wordWrap, onCheckedChange = onSetWordWrap)
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
        shadowElevation = 1.dp,
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
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 7.dp)) {
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
                    maxLines = 1,
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
                    modifier = Modifier.testTag("$optionTagPrefix-${optionKey(option)}"),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onEntry: (SourceEntry) -> Unit,
    onSearch: (String) -> Unit,
    onSearchResult: (ProjectSearchResult) -> Unit,
    onToggleTheme: () -> Unit,
) {
    val browserTitle = state.browserTitle ?: return
    var searchVisible by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf(state.projectSearchQuery) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ProductHeader(
            title = browserTitle,
            subtitle = "${state.projectEntries.count { !it.source.isDirectory }} 个文件",
            onBack = onBack,
            actions = {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Outlined.ManageSearch,
                    contentDescription = "项目全局搜索",
                    onClick = {
                    searchVisible = !searchVisible
                    if (!searchVisible) {
                        searchText = ""
                        onSearch("")
                    }
                    },
                )
                ThemeToggleButton(state.theme.isDark, onToggleTheme)
            },
        )
        if (searchVisible) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("搜索项目内容") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { onSearch(searchText) }) {
                        Icon(Icons.Outlined.Search, contentDescription = "开始搜索")
                    }
                },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (state.projectSearchQuery.isNotBlank()) {
            ProjectSearchResults(state.projectSearchResults, onSearchResult)
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
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onEntry(entry) },
        color = if (entry.isDirectory && entry.id in expandedIds) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            ComposeColor.Transparent
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width((indexed.depth * 16).dp))
            if (entry.isDirectory) {
                Icon(
                    if (entry.id in expandedIds) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
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
                    )
                }
            }
        }
    }
}

@Composable
private fun FileGlyph(isDirectory: Boolean, fileType: FileType, modifier: Modifier = Modifier) {
    val background = when {
        isDirectory -> MaterialTheme.colorScheme.tertiaryContainer
        fileType.markdown -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val foreground = when {
        isDirectory -> MaterialTheme.colorScheme.onTertiaryContainer
        fileType.markdown -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        modifier = modifier.size(36.dp).background(background, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ProjectSearchResults(results: List<ProjectSearchResult>, onOpen: (ProjectSearchResult) -> Unit) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.FindInPage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("没有匹配结果", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(results, key = { "${it.path}:${it.line}:${it.excerpt}" }) { result ->
            Surface(
                onClick = { onOpen(result) },
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.FindInPage, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(19.dp))
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            "${result.path}:${result.line}",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            result.excerpt,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
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
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
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
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp, end = 4.dp)) {
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
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (!state.markdownPreview) {
                            DropdownMenuItem(
                                text = { Text("跳转到行") },
                                leadingIcon = { Icon(Icons.Outlined.UnfoldMore, contentDescription = null) },
                                onClick = { menuExpanded = false; showGotoLine = true },
                            )
                        }
                        if (document.fileType.markdown && state.markdownPreview && state.markdownHeadings.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Markdown 目录") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null) },
                                onClick = { menuExpanded = false; showOutline = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("阅读设置") },
                            leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            onClick = { menuExpanded = false; showSettings = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.theme.isDark) "切换为亮色" else "切换为暗色") },
                            leadingIcon = {
                                Icon(if (state.theme.isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, contentDescription = null)
                            },
                            onClick = { menuExpanded = false; onToggleTheme() },
                        )
                    }
                }
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
            if (document.fileType.markdown && state.markdownPreview) {
                MarkdownPreview(
                    markdownText = state.draftText,
                    darkTheme = state.theme.isDark,
                    fontSizeSp = state.settings.fontSizeSp,
                    backgroundColorArgb = state.settings.background.colorArgb(state.theme.isDark),
                    command = state.readerCommand,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
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
            AlertDialog(
                onDismissRequest = { pendingCloseTabId = null },
                title = { Text("放弃未保存修改？") },
                text = { Text("${tab.document.name} 还有未保存内容，关闭后无法恢复。") },
                confirmButton = {
                    TextButton(onClick = { pendingCloseTabId = null; onCloseTab(tabId) }) {
                        Text("放弃并关闭")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingCloseTabId = null }) { Text("继续编辑") }
                },
            )
        }
    }
}

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
private fun ReaderTabs(state: ReaderUiState, onSwitch: (String) -> Unit, onClose: (ReaderTabState) -> Unit) {
    val listState = rememberLazyListState()
    val activeIndex = state.tabs.indexOfFirst { it.document.id == state.activeTabId }
    LaunchedEffect(state.activeTabId, state.tabs.size) {
        // 新文件通常追加在标签栏末尾，主动滚动可避免标题已切换但活动标签仍在屏幕外。
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            state = listState,
        ) {
            items(state.tabs, key = { it.document.id }) { tab ->
                val active = tab.document.id == state.activeTabId
                Surface(
                    modifier = Modifier
                        .height(42.dp)
                        .widthIn(min = 108.dp, max = 220.dp)
                        .clickable { onSwitch(tab.document.id) },
                    color = if (active) MaterialTheme.colorScheme.surface else ComposeColor.Transparent,
                    shape = MaterialTheme.shapes.small,
                    border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                ) {
                    Box {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tab.document.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (tab.dirty) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = if (active) 0.dp else 12.dp)
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                                )
                            }
                            if (active) {
                                IconButton(onClick = { onClose(tab) }, modifier = Modifier.size(42.dp)) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "关闭 ${tab.document.name}",
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
                        }
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .width(28.dp)
                                    .height(2.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)),
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
private fun ReaderActionBar(
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
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    onClick = onTogglePreview,
                )
            }
            ReaderActionButton(
                icon = if (editable) Icons.Outlined.Lock else Icons.Outlined.Edit,
                contentDescription = if (editable) "退出编辑" else "编辑",
                selected = editable,
                onClick = onToggleEditable,
            )
            if (dirty) {
                ReaderActionButton(
                    icon = Icons.Outlined.Save,
                    contentDescription = "保存",
                    emphasized = true,
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
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Icon(icon, contentDescription = contentDescription)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("快速切换文件", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
            Text("${visible.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("按路径过滤") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(440.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(visible, key = { it.source.id }) { indexed ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(indexed.source) },
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Markdown 目录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("阅读设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("字体大小", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${settings.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
            }
            Slider(
                value = settings.fontSizeSp,
                onValueChange = onSetFontSize,
                valueRange = 11f..24f,
                steps = 12,
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到行") },
        text = {
            OutlinedTextField(
                value = lineText,
                onValueChange = { lineText = it.filter(Char::isDigit).take(8) },
                label = { Text("行号") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onGoto(requireNotNull(line)) }, enabled = line != null && line > 0) { Text("跳转") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
internal fun GitCloneDialog(onDismiss: () -> Unit, onClone: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val normalizedUrl = url.trim()
    val parsedUrl = remember(normalizedUrl) { Uri.parse(normalizedUrl) }
    val validUrl = parsedUrl.scheme.equals("https", ignoreCase = true) &&
        !parsedUrl.host.isNullOrBlank() && parsedUrl.pathSegments.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("git-clone-dialog"),
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text("克隆 Git 仓库", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("仓库地址") },
                placeholder = { Text("https://github.com/owner/repository.git") },
                supportingText = { Text("仅支持公开 HTTPS 仓库") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (validUrl) onClone(normalizedUrl) }),
                modifier = Modifier.fillMaxWidth().testTag("git-url-input"),
            )
        },
        confirmButton = {
            Button(
                onClick = { onClone(normalizedUrl) },
                enabled = validUrl,
                modifier = Modifier.testTag("git-clone-confirm"),
            ) {
                Text("克隆")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("git-clone-cancel")) {
                Text("取消")
            }
        },
    )
}

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
