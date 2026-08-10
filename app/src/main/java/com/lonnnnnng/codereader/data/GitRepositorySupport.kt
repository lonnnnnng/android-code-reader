package com.lonnnnnng.codereader.data

import org.eclipse.jgit.lib.ProgressMonitor
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/** 经过校验的公开 HTTPS 仓库地址，同时提供适合作为本地目录的仓库名。 @author long */
internal class GitRepositoryAddress private constructor(
    val url: String,
    val repositoryName: String,
) {
    companion object {
        fun parse(value: String): GitRepositoryAddress {
            val normalized = value.trim()
            val uri = runCatching { URI(normalized) }
                .getOrElse { throw IllegalArgumentException("请输入完整的 HTTPS Git 仓库地址") }
            require(uri.scheme.equals("https", ignoreCase = true)) { "当前仅支持 HTTPS Git 地址" }
            require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "请输入公开 HTTPS Git 仓库地址" }
            require(uri.query == null && uri.fragment == null) { "Git 仓库地址不能包含查询参数或片段" }

            val path = uri.path.orEmpty().trimEnd('/')
            val pathName = path.substringAfterLast('/').let { name ->
                if (name.endsWith(".git", ignoreCase = true)) name.dropLast(4) else name
            }
            val safeName = pathName
                .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
                .trim('-', '.', ' ')
                .take(MAX_DIRECTORY_NAME_LENGTH)
            require(safeName.isNotBlank()) { "Git 地址中缺少仓库名称" }
            return GitRepositoryAddress(normalized, safeName)
        }

        fun isValid(value: String): Boolean = runCatching { parse(value) }.isSuccess

        private const val MAX_DIRECTORY_NAME_LENGTH = 100
    }
}

/** JGit 的阶段性任务会转换为可直接呈现在界面上的进度。 @author long */
internal data class GitOperationProgress(
    val detail: String,
    val percent: Int?,
)

/** Git 更新结果区分“已拉到新提交”和“原本就是最新”，避免所有成功都显示同一句话。 @author long */
internal data class GitUpdateResult(val updated: Boolean)

/** 更新预览先区分提交关系，只有纯快进才允许继续应用。 @author long */
enum class GitUpdateRelation {
    UP_TO_DATE,
    FAST_FORWARD,
    LOCAL_AHEAD,
    DIVERGED,
    DETACHED,
    NO_UPSTREAM,
}

/** 远端树差异只表达文件层级变化，首个切片不把重命名伪装成可安全自动处理的语义。 @author long */
enum class GitRemoteChangeKind { ADDED, MODIFIED, DELETED }

/** 工作区摘要用于解释为什么更新被阻止，不能只给出笼统的“Git 失败”。 @author long */
enum class GitLocalChangeKind { ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICTED, UNSAVED }

/** @author long */
data class GitCommitSummary(
    val revision: String,
    val title: String,
    val authorName: String,
    val committedAtEpochSeconds: Long,
)

/** @author long */
data class GitRemoteChange(
    val path: String,
    val kind: GitRemoteChangeKind,
)

/** @author long */
data class GitLocalChange(
    val path: String,
    val kind: GitLocalChangeKind,
)

/**
 * fetch 只更新远端跟踪引用；真正修改工作区前，界面必须先展示这份不可变预览并取得用户确认。
 *
 * @author long
 */
data class GitUpdatePreview(
    val branchName: String,
    val upstreamName: String?,
    val upstreamRef: String?,
    val headRevision: String,
    val targetRevision: String?,
    val relation: GitUpdateRelation,
    val remoteCommitCount: Int,
    val remoteCommits: List<GitCommitSummary>,
    val remoteCommitsTruncated: Boolean,
    val remoteChangeCount: Int,
    val remoteChanges: List<GitRemoteChange>,
    val remoteChangesTruncated: Boolean,
    val localChangeCount: Int,
    val localChanges: List<GitLocalChange>,
    val localChangesTruncated: Boolean,
) {
    val canApply: Boolean
        get() = relation == GitUpdateRelation.FAST_FORWARD &&
            targetRevision != null &&
            localChangeCount == 0

    /** 编辑器内尚未保存的草稿不会出现在 JGit Status 中，预览必须显式合并后再决定能否更新。 @author long */
    fun withUnsavedChanges(paths: Collection<String>): GitUpdatePreview {
        val normalizedPaths = paths.map(String::trim).filter(String::isNotEmpty).distinct().sorted()
        if (normalizedPaths.isEmpty()) return this
        val combined = linkedMapOf<String, GitLocalChangeKind>()
        localChanges.forEach { combined[it.path] = it.kind }
        normalizedPaths.forEach { combined[it] = GitLocalChangeKind.UNSAVED }
        val hiddenExisting = (localChangeCount - localChanges.size).coerceAtLeast(0)
        return copy(
            localChangeCount = hiddenExisting + combined.size,
            localChanges = combined.entries
                .sortedBy { it.key }
                .take(MAX_LOCAL_CHANGES_IN_PREVIEW)
                .map { GitLocalChange(it.key, it.value) },
            localChangesTruncated = hiddenExisting > 0 || combined.size > MAX_LOCAL_CHANGES_IN_PREVIEW,
        )
    }

    private companion object {
        const val MAX_LOCAL_CHANGES_IN_PREVIEW = 20
    }
}

/** 用户主动取消网络传输时使用独立异常，界面可以给出普通提示而不是失败告警。 @author long */
internal class GitOperationCancelledException(message: String) : IllegalStateException(message)

/** 预览失效或工作区变化属于可恢复保护，不应跳转到通用错误页。 @author long */
internal class GitUpdateRejectedException(message: String) : IllegalStateException(message)

/**
 * JGit 在后台线程高频回调增量值；这里只在百分比变化时通知界面，避免大型仓库传输时触发过量重组。
 *
 * @author long
 */
internal class GitOperationProgressMonitor(
    private val onProgress: (GitOperationProgress) -> Unit,
) : ProgressMonitor {
    private val cancelled = AtomicBoolean(false)
    private var detail = "正在连接远程仓库"
    private var totalWork = ProgressMonitor.UNKNOWN
    private var completedWork = 0
    private var lastPercent: Int? = null
    private var unknownProgressUpdates = 0

    override fun start(totalTasks: Int) {
        emit(force = true)
    }

    @Synchronized
    override fun beginTask(title: String, totalWork: Int) {
        detail = localizeTask(title)
        this.totalWork = totalWork
        completedWork = 0
        lastPercent = null
        unknownProgressUpdates = 0
        emit(force = true)
    }

    @Synchronized
    override fun update(completed: Int) {
        completedWork = (completedWork + completed).coerceAtLeast(0)
        if (totalWork > 0) {
            emit(force = false)
        } else {
            unknownProgressUpdates++
            if (unknownProgressUpdates == 1 || unknownProgressUpdates % UNKNOWN_PROGRESS_EMIT_INTERVAL == 0) {
                emit(force = true)
            }
        }
    }

    @Synchronized
    override fun endTask() {
        if (totalWork > 0) {
            completedWork = totalWork
            emit(force = true)
        }
    }

    override fun isCancelled(): Boolean = cancelled.get()

    override fun showDuration(enabled: Boolean) = Unit

    fun cancel() {
        cancelled.set(true)
    }

    private fun emit(force: Boolean) {
        val percent = if (totalWork > 0) {
            ((completedWork.toLong() * 100L) / totalWork.toLong()).toInt().coerceIn(0, 100)
        } else {
            null
        }
        if (!force && percent == lastPercent) return
        lastPercent = percent
        onProgress(GitOperationProgress(detail, percent))
    }

    private fun localizeTask(title: String): String {
        val normalized = title.lowercase()
        return when {
            "counting objects" in normalized -> "正在统计对象"
            "compressing objects" in normalized -> "正在压缩对象"
            "receiving objects" in normalized -> "正在接收对象"
            "resolving deltas" in normalized -> "正在整理文件差异"
            "checking out files" in normalized || "checkout" in normalized -> "正在写入工作区"
            "analyz" in normalized -> "正在分析更新内容"
            "fetch" in normalized -> "正在获取远程更新"
            title.isBlank() -> "正在处理仓库数据"
            else -> "正在处理：${title.take(MAX_TASK_TITLE_LENGTH)}"
        }
    }

    private companion object {
        const val UNKNOWN_PROGRESS_EMIT_INTERVAL = 32
        const val MAX_TASK_TITLE_LENGTH = 48
    }
}
