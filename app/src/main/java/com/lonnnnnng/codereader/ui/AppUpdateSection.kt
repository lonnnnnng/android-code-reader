package com.lonnnnnng.codereader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.codereader.BuildConfig
import java.util.Locale

/** 设置页中的更新入口保持紧凑，长更新说明只在用户打开详情后展示。 @author long */
@Composable
internal fun AppUpdateSettingRow(
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onShowDetails: () -> Unit,
    onInstall: () -> Unit,
) {
    val action = when (state.phase) {
        AppUpdatePhase.AVAILABLE -> onShowDetails
        AppUpdatePhase.READY -> onInstall
        AppUpdatePhase.CHECKING, AppUpdatePhase.DOWNLOADING -> null
        else -> onCheck
    }
    val actionText = when (state.phase) {
        AppUpdatePhase.AVAILABLE -> "查看更新"
        AppUpdatePhase.READY -> "立即安装"
        AppUpdatePhase.FAILED -> "重新检查"
        else -> "检查更新"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).testTag("update-setting"),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Outlined.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("应用更新", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        updateStatusText(state),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.phase == AppUpdatePhase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("update-status"),
                    )
                }
                if (state.phase == AppUpdatePhase.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { action?.invoke() }, enabled = action != null, modifier = Modifier.testTag("check-update-button")) {
                        Text(actionText)
                    }
                }
            }
            if (state.phase == AppUpdatePhase.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { state.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).testTag("update-progress"),
                )
            }
        }
    }
}

@Composable
internal fun AppUpdateDialog(
    state: AppUpdateUiState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val release = state.release ?: return
    if (!state.dialogVisible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.phase == AppUpdatePhase.READY) "更新已下载" else "发现新版本 v${release.versionName}",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${release.title} · ${formatBytes(release.apk.sizeBytes)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "安装前会校验 SHA-256、应用 ID、版本号和签名证书。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("更新说明", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    release.notes.ifBlank { "此版本没有附加更新说明。" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("update-release-notes"),
                )
                if (state.phase == AppUpdatePhase.DOWNLOADING) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("update-download-progress-area"),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("正在下载", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.progressPercent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().testTag("update-dialog-progress"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                AppUpdatePhase.AVAILABLE -> Button(onClick = onDownload, modifier = Modifier.testTag("download-update-button")) { Text("下载更新") }
                AppUpdatePhase.READY -> Button(onClick = onInstall, modifier = Modifier.testTag("install-update-button")) { Text("立即安装") }
                AppUpdatePhase.DOWNLOADING -> TextButton(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.testTag("update-downloading-button"),
                ) { Text("下载中") }
                else -> Unit
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-update-dialog")) { Text("稍后") } },
        modifier = Modifier.testTag("update-available-dialog"),
    )
}

private fun updateStatusText(state: AppUpdateUiState): String = when (state.phase) {
    AppUpdatePhase.IDLE -> "当前版本 v${BuildConfig.VERSION_NAME}"
    AppUpdatePhase.CHECKING -> "正在连接 GitHub Releases"
    AppUpdatePhase.UP_TO_DATE -> "当前已是最新版本 v${BuildConfig.VERSION_NAME}"
    AppUpdatePhase.AVAILABLE -> "发现新版本 v${state.release?.versionName.orEmpty()}"
    AppUpdatePhase.DOWNLOADING -> "正在下载 v${state.release?.versionName.orEmpty()} · ${state.progressPercent}%"
    AppUpdatePhase.READY -> "v${state.release?.versionName.orEmpty()} 已下载并通过校验"
    AppUpdatePhase.FAILED -> state.errorMessage ?: "检查更新失败"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
