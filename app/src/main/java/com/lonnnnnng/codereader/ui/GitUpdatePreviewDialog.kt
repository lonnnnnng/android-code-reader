package com.lonnnnnng.codereader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lonnnnnng.codereader.data.GitLocalChangeKind
import com.lonnnnnng.codereader.data.GitRemoteChangeKind
import com.lonnnnnng.codereader.data.GitUpdatePreview
import com.lonnnnnng.codereader.data.GitUpdateRelation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * fetch 后先展示远端范围和本地保护状态，用户确认前不修改分支或工作区。
 *
 * @author long
 */
@Composable
internal fun GitUpdatePreviewDialog(
    preview: GitUpdatePreview,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onOpenConflictFile: (String) -> Unit = {},
) {
    val upstream = preview.upstreamName ?: "未设置上游"
    val relationText = when (preview.relation) {
        GitUpdateRelation.UP_TO_DATE -> "远端已经同步"
        GitUpdateRelation.FAST_FORWARD -> "可以安全快进"
        GitUpdateRelation.LOCAL_AHEAD -> "本地分支领先远端"
        GitUpdateRelation.DIVERGED -> "本地与远端已经分叉"
        GitUpdateRelation.DETACHED -> "当前处于游离 HEAD"
        GitUpdateRelation.NO_UPSTREAM -> "当前分支没有上游"
    }
    val commitCount = if (preview.remoteCommitsTruncated) {
        "至少 ${preview.remoteCommitCount}"
    } else {
        preview.remoteCommitCount.toString()
    }
    val fileCount = if (preview.remoteChangesTruncated) {
        "至少 ${preview.remoteChangeCount}"
    } else {
        preview.remoteChangeCount.toString()
    }
    val remoteSummary = when (preview.relation) {
        GitUpdateRelation.FAST_FORWARD -> "$commitCount 个新提交 · $fileCount 个文件变化"
        GitUpdateRelation.DIVERGED -> "$commitCount 个远端独有提交 · $fileCount 个分支文件差异"
        GitUpdateRelation.LOCAL_AHEAD -> "远端无新增提交 · $fileCount 个分支文件差异"
        GitUpdateRelation.UP_TO_DATE -> "远端无新增提交 · 工作树内容一致"
        GitUpdateRelation.DETACHED,
        GitUpdateRelation.NO_UPSTREAM -> "尚未取得可比较的上游提交"
    }
    val blockedByLocalChanges = preview.localChangeCount > 0
    ReaderDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("git-update-preview-dialog"),
        title = "Git 更新预览",
        icon = Icons.Outlined.Sync,
        actions = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("git-update-cancel")) {
                Text("暂不更新")
            }
            Button(
                onClick = onApply,
                enabled = preview.canApply,
                modifier = Modifier.testTag("git-update-apply"),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("安全更新")
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = when {
                    preview.canApply -> MaterialTheme.colorScheme.primaryContainer
                    blockedByLocalChanges -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = when {
                    preview.canApply -> MaterialTheme.colorScheme.onPrimaryContainer
                    blockedByLocalChanges -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "${preview.branchName} → $upstream",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(relationText, style = MaterialTheme.typography.bodyMedium)
                    if (preview.targetRevision != null) {
                        Text(
                            "${preview.headRevision.take(8)} → ${preview.targetRevision.take(8)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }

            if (preview.localChangeCount > 0) {
                GitPreviewSectionTitle(
                    title = "本地工作区",
                    summary = "发现 ${preview.localChangeCount} 个本地修改，安全更新已暂停",
                    error = true,
                )
                preview.localChanges.take(GIT_PREVIEW_VISIBLE_ITEMS).forEach { change ->
                    GitPreviewPathRow(
                        label = when (change.kind) {
                            GitLocalChangeKind.ADDED -> "已暂存新增"
                            GitLocalChangeKind.MODIFIED -> "已修改"
                            GitLocalChangeKind.DELETED -> "已删除"
                            GitLocalChangeKind.UNTRACKED -> "未跟踪"
                            GitLocalChangeKind.CONFLICTED -> "冲突"
                            GitLocalChangeKind.UNSAVED -> "未保存"
                        },
                        path = change.path,
                        error = true,
                        onClick = if (change.kind == GitLocalChangeKind.CONFLICTED) {
                            { onOpenConflictFile(change.path) }
                        } else null,
                    )
                }
                if (preview.localChangesTruncated || preview.localChanges.size > GIT_PREVIEW_VISIBLE_ITEMS) {
                    Text(
                        "其余本地修改未展开显示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.localChanges.any { it.kind == GitLocalChangeKind.CONFLICTED }) {
                    Text(
                        "冲突文件仅支持只读查看。灵阅不会自动合并；请在外部 Git 工具处理冲突并保存后，再重新检查仓库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            GitPreviewSectionTitle(title = "远端变化", summary = remoteSummary)
            preview.remoteCommits.forEach { commit ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            commit.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "${commit.authorName} · ${formatGitCommitTime(commit.committedAtEpochSeconds)} · ${commit.revision.take(8)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            preview.remoteChanges.take(GIT_PREVIEW_VISIBLE_ITEMS).forEach { change ->
                GitPreviewPathRow(
                    label = when (change.kind) {
                        GitRemoteChangeKind.ADDED -> "新增"
                        GitRemoteChangeKind.MODIFIED -> "修改"
                        GitRemoteChangeKind.DELETED -> "删除"
                    },
                    path = change.path,
                )
            }
            if (preview.remoteChangesTruncated || preview.remoteChanges.size > GIT_PREVIEW_VISIBLE_ITEMS) {
                Text(
                    "其余远端文件变化未展开显示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!preview.canApply && preview.localChangeCount == 0) {
                Text(
                    when (preview.relation) {
                        GitUpdateRelation.LOCAL_AHEAD -> "本地分支包含远端没有的提交，灵阅不会自动回退。"
                        GitUpdateRelation.DIVERGED -> "两个分支都包含独有提交，灵阅不会自动合并。"
                        GitUpdateRelation.DETACHED -> "请先切回具备上游的本地分支，再检查更新。"
                        GitUpdateRelation.NO_UPSTREAM -> "当前分支没有可跟踪的远端分支。"
                        GitUpdateRelation.UP_TO_DATE -> "当前没有需要应用的远端提交。"
                        GitUpdateRelation.FAST_FORWARD -> "当前预览已经失效，请重新检查更新。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GitPreviewSectionTitle(
    title: String,
    summary: String,
    error: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            summary,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GitPreviewPathRow(
    label: String,
    path: String,
    error: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.testTag("git-conflict-path") else Modifier)
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = if (error) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (error) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            path,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val GIT_PREVIEW_VISIBLE_ITEMS = 6

private fun formatGitCommitTime(epochSeconds: Long): String {
    // 每次格式化都创建独立实例，兼容 API 24，同时避免 SimpleDateFormat 在线程间共享。 @author long
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(epochSeconds * 1_000L))
}
