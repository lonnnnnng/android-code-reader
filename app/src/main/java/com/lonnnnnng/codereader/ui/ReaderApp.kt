package com.lonnnnnng.codereader.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
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
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
import com.lonnnnnng.codereader.model.ReaderFontFamily
import com.lonnnnnng.codereader.model.ReaderTheme
import com.lonnnnnng.codereader.model.SourceEntry

private val HighContrastLightColors = androidx.compose.material3.lightColorScheme(
    primary = ComposeColor(0xFF0B6B53),
    onPrimary = ComposeColor.White,
    primaryContainer = ComposeColor(0xFFD5F1E7),
    onPrimaryContainer = ComposeColor(0xFF073B2F),
    secondary = ComposeColor(0xFF365E7D),
    secondaryContainer = ComposeColor(0xFFDCEAF4),
    onSecondaryContainer = ComposeColor(0xFF17384F),
    tertiary = ComposeColor(0xFF8A5A00),
    tertiaryContainer = ComposeColor(0xFFFFE2A8),
    onTertiaryContainer = ComposeColor(0xFF4A3000),
    surface = ComposeColor(0xFFFFFFFF),
    surfaceVariant = ComposeColor(0xFFE9EDF0),
    background = ComposeColor(0xFFF7F9FA),
    onSurface = ComposeColor(0xFF1F2529),
    outline = ComposeColor(0xFF657078),
)

private val DarculaColors = androidx.compose.material3.darkColorScheme(
    primary = ComposeColor(0xFF75D6B4),
    onPrimary = ComposeColor(0xFF00382B),
    primaryContainer = ComposeColor(0xFF174E40),
    onPrimaryContainer = ComposeColor(0xFFC4F4E3),
    secondary = ComposeColor(0xFFA9CAE0),
    secondaryContainer = ComposeColor(0xFF334A59),
    onSecondaryContainer = ComposeColor(0xFFD7ECF8),
    tertiary = ComposeColor(0xFFE6C16C),
    tertiaryContainer = ComposeColor(0xFF5B481B),
    onTertiaryContainer = ComposeColor(0xFFFFE9AD),
    surface = ComposeColor(0xFF242424),
    surfaceVariant = ComposeColor(0xFF343434),
    background = ComposeColor(0xFF1E1E1E),
    onSurface = ComposeColor(0xFFD4D4D4),
    outline = ComposeColor(0xFF9A9A9A),
)

private data class PaletteAccent(
    val primary: ComposeColor,
    val onPrimary: ComposeColor,
    val primaryContainer: ComposeColor,
    val onPrimaryContainer: ComposeColor,
    val secondary: ComposeColor,
    val secondaryContainer: ComposeColor,
    val onSecondaryContainer: ComposeColor,
)

/** 应用配色只替换语义强调色，背景明暗和文本对比仍由基础主题统一兜底。 @author long */
private fun appColorScheme(theme: ReaderTheme, palette: AppColorPalette): ColorScheme {
    val dark = theme.isDark
    val accent = when (palette) {
        AppColorPalette.EMERALD -> if (dark) {
            PaletteAccent(
                ComposeColor(0xFF75D6B4), ComposeColor(0xFF00382B), ComposeColor(0xFF174E40),
                ComposeColor(0xFFC4F4E3), ComposeColor(0xFFA9CAE0), ComposeColor(0xFF334A59), ComposeColor(0xFFD7ECF8),
            )
        } else {
            PaletteAccent(
                ComposeColor(0xFF0B6B53), ComposeColor.White, ComposeColor(0xFFD5F1E7),
                ComposeColor(0xFF073B2F), ComposeColor(0xFF365E7D), ComposeColor(0xFFDCEAF4), ComposeColor(0xFF17384F),
            )
        }
        AppColorPalette.OCEAN -> if (dark) {
            PaletteAccent(
                ComposeColor(0xFF8CC8FF), ComposeColor(0xFF003258), ComposeColor(0xFF174B72),
                ComposeColor(0xFFD5ECFF), ComposeColor(0xFFB8C8E8), ComposeColor(0xFF3B4964), ComposeColor(0xFFDFE7FF),
            )
        } else {
            PaletteAccent(
                ComposeColor(0xFF155EA8), ComposeColor.White, ComposeColor(0xFFDAE9F8),
                ComposeColor(0xFF0C365F), ComposeColor(0xFF4C5F8C), ComposeColor(0xFFE0E7F8), ComposeColor(0xFF26395F),
            )
        }
        AppColorPalette.AMBER -> if (dark) {
            PaletteAccent(
                ComposeColor(0xFFE6C16C), ComposeColor(0xFF3C2F00), ComposeColor(0xFF5B481B),
                ComposeColor(0xFFFFE9AD), ComposeColor(0xFFD6C69B), ComposeColor(0xFF514A38), ComposeColor(0xFFF2E7C8),
            )
        } else {
            PaletteAccent(
                ComposeColor(0xFF805600), ComposeColor.White, ComposeColor(0xFFFFE2A8),
                ComposeColor(0xFF4A3000), ComposeColor(0xFF685B38), ComposeColor(0xFFEFE4C5), ComposeColor(0xFF403816),
            )
        }
    }
    val base = if (dark) DarculaColors else HighContrastLightColors
    return base.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        secondary = accent.secondary,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
    )
}

private fun paletteSwatch(palette: AppColorPalette, darkTheme: Boolean): ComposeColor =
    appColorScheme(if (darkTheme) ReaderTheme.DARCULA else ReaderTheme.HIGH_CONTRAST_LIGHT, palette).primary

private fun ReaderFontFamily.composeFamily(): FontFamily = when (this) {
    ReaderFontFamily.SYSTEM_SANS -> FontFamily.SansSerif
    ReaderFontFamily.MONOSPACE -> FontFamily.Monospace
    ReaderFontFamily.SERIF -> FontFamily.Serif
}

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

    BackHandler(enabled = state.screen != AppScreen.HOME) { viewModel.navigateBack() }

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

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (state.screen) {
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
                            onSetFontFamily = viewModel::setFontFamily,
                            onSetFontSize = viewModel::setFontSize,
                            onSetReaderBackground = viewModel::setReaderBackground,
                            onSetAppPalette = viewModel::setAppPalette,
                            onSetTheme = viewModel::setTheme,
                            onSetWordWrap = viewModel::setWordWrap,
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

                    if (state.busy) {
                        Surface(color = ComposeColor.Black.copy(alpha = 0.22f), modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                    }
                }
            }
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
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("源码阅读器", fontWeight = FontWeight.SemiBold) },
            actions = {
                ThemeToggleButton(state.theme.isDark, onToggleTheme)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置")
                }
            },
            windowInsets = WindowInsets(),
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SectionLabel("打开") }
            item { HomeActionRow("文件", "从系统或其他应用打开源码", Icons.Outlined.Description, onOpenFile) }
            item { HomeActionRow("目录 / 项目", "使用系统目录授权浏览工程", Icons.Outlined.FolderOpen, onOpenFolder) }
            item { HomeActionRow("导入 ZIP", "解压到应用目录后离线阅读", Icons.Outlined.Archive, onOpenZip) }
            item { HomeActionRow("克隆 Git 仓库", "当前支持公开 HTTPS 浅克隆", Icons.Outlined.CloudDownload, onCloneGit) }

            if (state.recentProjects.isNotEmpty()) {
                item { SectionLabel("最近项目") }
                items(state.recentProjects, key = { "${it.kind}:${it.value}" }) { project ->
                    ListItem(
                        headlineContent = { Text(project.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(if (project.kind == "saf") "系统目录" else "本地导入", maxLines = 1) },
                        leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = { onRemoveRecent(project) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除最近项目")
                            }
                        },
                        modifier = Modifier.clickable { onOpenRecent(project) },
                    )
                    HorizontalDivider()
                }
            }

            item { SectionLabel("示例") }
            item {
                HomeActionRow(
                    "Markdown 功能示例",
                    "离线查看代码、公式和 Mermaid",
                    Icons.Outlined.Code,
                ) { onOpenBundledProject("examples", "markdown-example") }
            }

            if (BuildConfig.DEBUG) {
                item { SectionLabel("开发工具") }
                item {
                    HomeActionRow("内置测试项目", "Debug 构建的多语言测试样例", Icons.Outlined.Code) {
                        onOpenBundledProject("samples", "sample-project")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onSetFontFamily: (ReaderFontFamily) -> Unit,
    onSetFontSize: (Float) -> Unit,
    onSetReaderBackground: (ReaderBackground) -> Unit,
    onSetAppPalette: (AppColorPalette) -> Unit,
    onSetTheme: (ReaderTheme) -> Unit,
    onSetWordWrap: (Boolean) -> Unit,
) {
    val settings = state.settings
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            windowInsets = WindowInsets(),
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("settings-list"),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                SettingsPreview(
                    settings = settings,
                    darkTheme = state.theme.isDark,
                )
            }

            item { SettingsSectionHeader("显示设置", "调整源码与 Markdown 的共同阅读体验") }
            item { SettingsGroupLabel("字体") }
            items(ReaderFontFamily.entries, key = { it.preferenceValue }) { font ->
                SettingsChoiceRow(
                    title = font.displayName,
                    summary = font.description,
                    selected = settings.fontFamily == font,
                    testTag = "font-${font.preferenceValue}",
                    onClick = { onSetFontFamily(font) },
                    leading = {
                        Text(
                            "Aa",
                            fontFamily = font.composeFamily(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("字体大小 ${settings.fontSizeSp.toInt()} sp", fontWeight = FontWeight.Medium)
                    Text(
                        "源码和 Markdown 正文同步缩放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    )
                    Slider(
                        value = settings.fontSizeSp,
                        onValueChange = onSetFontSize,
                        valueRange = 11f..24f,
                        steps = 12,
                        modifier = Modifier.testTag("font-size-slider"),
                    )
                }
                HorizontalDivider()
            }

            item { SettingsGroupLabel("阅读背景") }
            items(ReaderBackground.entries, key = { it.preferenceValue }) { background ->
                SettingsChoiceRow(
                    title = background.displayName,
                    summary = background.description,
                    selected = settings.background == background,
                    testTag = "background-${background.preferenceValue}",
                    onClick = { onSetReaderBackground(background) },
                    leading = {
                        ColorSwatch(
                            color = ComposeColor(background.colorArgb(state.theme.isDark)),
                            borderColor = MaterialTheme.colorScheme.outline,
                        )
                    },
                )
            }

            item { SettingsSectionHeader("整体配色", "控制应用外壳、按钮和选中状态的强调色") }
            items(AppColorPalette.entries, key = { it.preferenceValue }) { palette ->
                SettingsChoiceRow(
                    title = palette.displayName,
                    summary = palette.description,
                    selected = settings.appPalette == palette,
                    testTag = "palette-${palette.preferenceValue}",
                    onClick = { onSetAppPalette(palette) },
                    leading = {
                        ColorSwatch(
                            color = paletteSwatch(palette, state.theme.isDark),
                            borderColor = MaterialTheme.colorScheme.outline,
                        )
                    },
                )
            }

            item { SettingsGroupLabel("明暗模式") }
            items(ReaderTheme.entries, key = { it.preferenceValue }) { theme ->
                SettingsChoiceRow(
                    title = if (theme.isDark) "Darcula 暗色" else "高对比亮色",
                    summary = if (theme.isDark) "适合夜间和低亮度环境" else "适合白天和明亮环境",
                    selected = state.theme == theme,
                    testTag = "theme-${theme.preferenceValue}",
                    onClick = { onSetTheme(theme) },
                    leading = {
                        Icon(
                            if (theme.isDark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("源码自动换行") },
                    supportingContent = { Text("长行不再需要横向滚动") },
                    trailingContent = {
                        Switch(
                            checked = settings.wordWrap,
                            onCheckedChange = onSetWordWrap,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetWordWrap(!settings.wordWrap) }
                        .testTag("word-wrap-setting"),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SettingsPreview(settings: ReaderSettings, darkTheme: Boolean) {
    val background = ComposeColor(settings.background.colorArgb(darkTheme))
    val foreground = if (darkTheme) ComposeColor(0xFFE5E7EB) else ComposeColor(0xFF1F2937)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(
            "实时预览",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Surface(
            color = background,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    "fun main() { println(\"Hello, reader\") }",
                    color = foreground,
                    fontFamily = settings.fontFamily.composeFamily(),
                    fontSize = settings.fontSizeSp.sp,
                )
                Text(
                    "源码与 Markdown 会使用同一套显示偏好。",
                    color = foreground.copy(alpha = 0.76f),
                    fontFamily = settings.fontFamily.composeFamily(),
                    fontSize = (settings.fontSizeSp - 1f).coerceAtLeast(11f).sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, summary: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    summary: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = leading,
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag),
    )
    HorizontalDivider()
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun HomeActionRow(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
    HorizontalDivider()
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

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(browserTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") }
            },
            actions = {
                IconButton(onClick = {
                    searchVisible = !searchVisible
                    if (!searchVisible) {
                        searchText = ""
                        onSearch("")
                    }
                }) {
                    Icon(Icons.Outlined.ManageSearch, contentDescription = "项目全局搜索")
                }
                ThemeToggleButton(state.theme.isDark, onToggleTheme)
            },
            windowInsets = WindowInsets(),
        )
        HorizontalDivider()
        if (searchVisible) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("搜索项目内容") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { onSearch(searchText) }) {
                        Icon(Icons.Outlined.Search, contentDescription = "开始搜索")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (state.projectSearchQuery.isNotBlank()) {
            ProjectSearchResults(state.projectSearchResults, onSearchResult)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("project-list")) {
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
    ListItem(
        headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            if (!entry.isDirectory) Text("${FileType.detect(entry.name).displayName} · ${formatBytes(entry.size)}")
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width((indexed.depth * 18).dp))
                if (entry.isDirectory) {
                    Icon(
                        if (entry.id in expandedIds) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                        contentDescription = null,
                    )
                } else {
                    Spacer(Modifier.size(24.dp))
                }
                Icon(
                    if (entry.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                    contentDescription = null,
                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().clickable { onEntry(entry) },
    )
    HorizontalDivider()
}

@Composable
private fun ProjectSearchResults(results: List<ProjectSearchResult>, onOpen: (ProjectSearchResult) -> Unit) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有匹配结果") }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { "${it.path}:${it.line}:${it.excerpt}" }) { result ->
            ListItem(
                headlineContent = { Text("${result.path}:${result.line}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(result.excerpt, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(Icons.Outlined.FindInPage, contentDescription = null) },
                modifier = Modifier.clickable { onOpen(result) },
            )
            HorizontalDivider()
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

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = document.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val metaColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        Text(document.fileType.displayName, style = MaterialTheme.typography.bodySmall, color = metaColor)
                        Text(" · ", style = MaterialTheme.typography.bodySmall, color = metaColor)
                        Text(documentStatus, style = MaterialTheme.typography.bodySmall, color = metaColor)
                        if (displayPath != null) {
                            Text(
                                text = " · $displayPath",
                                style = MaterialTheme.typography.bodySmall,
                                color = metaColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                IconButton(onClick = { searchVisible = !searchVisible }) {
                    Icon(Icons.Outlined.Search, contentDescription = if (searchVisible) "关闭文件内搜索" else "文件内搜索")
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
                    }
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
                                leadingIcon = { Icon(Icons.Outlined.List, contentDescription = null) },
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

        if (searchVisible) {
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
        HorizontalDivider()

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (document.fileType.markdown && state.markdownPreview) {
                MarkdownPreview(
                    markdownText = state.draftText,
                    darkTheme = state.theme.isDark,
                    fontSizeSp = state.settings.fontSizeSp,
                    fontFamily = state.settings.fontFamily,
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
                    fontFamily = state.settings.fontFamily,
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
private fun ReaderTabs(state: ReaderUiState, onSwitch: (String) -> Unit, onClose: (ReaderTabState) -> Unit) {
    val listState = rememberLazyListState()
    val activeIndex = state.tabs.indexOfFirst { it.document.id == state.activeTabId }
    LaunchedEffect(state.activeTabId, state.tabs.size) {
        // 新文件通常追加在标签栏末尾，主动滚动可避免标题已切换但活动标签仍在屏幕外。
        if (activeIndex >= 0) listState.animateScrollToItem(activeIndex)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentPadding = PaddingValues(horizontal = 6.dp),
            state = listState,
        ) {
            items(state.tabs, key = { it.document.id }) { tab ->
                val active = tab.document.id == state.activeTabId
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .widthIn(min = 104.dp, max = 210.dp)
                        .background(if (active) MaterialTheme.colorScheme.surface else ComposeColor.Transparent)
                        .clickable { onSwitch(tab.document.id) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.document.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 1f else 0.68f),
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
                            IconButton(onClick = { onClose(tab) }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "关闭 ${tab.document.name}",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    if (active) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)) {
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
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
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
                Icon(Icons.Outlined.NavigateBefore, contentDescription = "上一个")
            }
            IconButton(onClick = onNext, enabled = text.isNotBlank()) {
                Icon(Icons.Outlined.NavigateNext, contentDescription = "下一个")
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
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
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
        Text("快速切换文件", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("按路径过滤") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
            items(visible, key = { it.source.id }) { indexed ->
                ListItem(
                    headlineContent = { Text(indexed.source.name, maxLines = 1) },
                    supportingContent = { Text(indexed.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = null) },
                    modifier = Modifier.clickable { onOpen(indexed.source) },
                )
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
        Text("Markdown 目录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().height(460.dp)) {
            items(state.markdownHeadings, key = { it.index }) { heading ->
                Text(
                    heading.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onHeading(heading.index) }
                        .padding(start = (20 + (heading.level - 1) * 18).dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                )
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
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)
            Text("字体大小 ${settings.fontSizeSp.toInt()} sp", modifier = Modifier.padding(top = 20.dp))
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
                    Text("源码自动换行")
                    Text("长行不再需要横向滚动", style = MaterialTheme.typography.bodySmall)
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
    IconButton(onClick = onToggleTheme) {
        Icon(
            imageVector = if (darkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
            contentDescription = if (darkTheme) "切换为亮色主题" else "切换为暗色主题",
        )
    }
}

@Composable
private fun GitCloneDialog(onDismiss: () -> Unit, onClone: (String) -> Unit) {
    var url by remember { mutableStateOf("https://github.com/") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("克隆 Git 仓库") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("HTTPS 地址") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onClone(url) }, enabled = url.startsWith("https://")) { Text("克隆") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
