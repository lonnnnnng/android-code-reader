package com.lonnnnnng.codereader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.ProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.TreeFilter
import java.io.File

/**
 * Git 仓库生命周期内核负责克隆、更新预览和精确快进；ZIP 与示例导入继续由 ProjectImporter 负责。
 *
 * @author long
 */
internal class GitRepositoryManager(private val context: Context) {
    suspend fun clone(
        url: String,
        monitor: GitOperationProgressMonitor = GitOperationProgressMonitor {},
    ): File = withContext(Dispatchers.IO) {
        val address = GitRepositoryAddress.parse(url)
        val target = freshUniqueDirectory(address.repositoryName)
        try {
            Git.cloneRepository()
                .setURI(address.url)
                .setDirectory(target)
                .setDepth(1)
                .setCloneAllBranches(false)
                .setTimeout(GIT_TIMEOUT_SECONDS)
                .setProgressMonitor(monitor)
                .call()
                .close()
            target
        } catch (error: Exception) {
            target.deleteRecursively()
            if (monitor.isCancelled) throw GitOperationCancelledException("已取消克隆 Git 仓库")
            throw IllegalStateException("Git 克隆失败：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    /**
     * 预览阶段只执行 fetch 和只读分析，不修改当前分支或工作区；用户确认后才能进入 applyUpdate。
     *
     * @author long
     */
    suspend fun previewUpdate(
        directory: File,
        monitor: GitOperationProgressMonitor = GitOperationProgressMonitor {},
    ): GitUpdatePreview = withContext(Dispatchers.IO) {
        require(isRepository(directory)) { "当前项目不是可更新的 Git 仓库" }
        try {
            Git.open(directory).use { git ->
                val repository = git.repository
                val headRevision = repository.resolve(Constants.HEAD)?.name
                    ?: throw IllegalStateException("当前仓库没有可读取的 HEAD 提交")
                val localSummary = summarizeLocalChanges(git.status().call())
                val fullBranch = repository.fullBranch.orEmpty()
                if (!fullBranch.startsWith(Constants.R_HEADS)) {
                    return@use emptyPreview(
                        branchName = "游离 HEAD",
                        headRevision = headRevision,
                        relation = GitUpdateRelation.DETACHED,
                        localSummary = localSummary,
                    )
                }

                val branchName = Repository.shortenRefName(fullBranch)
                val branchConfig = BranchConfig(repository.config, branchName)
                val upstreamRef = branchConfig.trackingBranch
                if (upstreamRef.isNullOrBlank()) {
                    return@use emptyPreview(
                        branchName = branchName,
                        headRevision = headRevision,
                        relation = GitUpdateRelation.NO_UPSTREAM,
                        localSummary = localSummary,
                    )
                }

                git.fetch()
                    .setRemote(branchConfig.remote ?: Constants.DEFAULT_REMOTE_NAME)
                    .setTimeout(GIT_TIMEOUT_SECONDS)
                    .setProgressMonitor(monitor)
                    .call()
                if (monitor.isCancelled) throw GitOperationCancelledException("已取消检查 Git 更新")

                val targetRevision = repository.resolve(upstreamRef)?.name
                    ?: throw IllegalStateException("无法读取上游分支 ${Repository.shortenRefName(upstreamRef)}")
                monitor.beginTask("Analyzing update", ProgressMonitor.UNKNOWN)
                try {
                    analyzeUpdate(
                        repository = repository,
                        branchName = branchName,
                        upstreamRef = upstreamRef,
                        headRevision = headRevision,
                        targetRevision = targetRevision,
                        localSummary = localSummary,
                        monitor = monitor,
                    )
                } finally {
                    monitor.endTask()
                }
            }
        } catch (error: Exception) {
            if (monitor.isCancelled || error is GitOperationCancelledException) {
                throw GitOperationCancelledException("已取消检查 Git 更新")
            }
            if (error is IllegalArgumentException) throw error
            throw IllegalStateException("Git 更新检查失败：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    /**
     * 应用前重新核对工作区、当前分支、上游配置、HEAD 和目标引用，确保用户看到的预览没有在确认期间失效。
     *
     * @author long
     */
    suspend fun applyUpdate(
        directory: File,
        preview: GitUpdatePreview,
        monitor: GitOperationProgressMonitor = GitOperationProgressMonitor {},
    ): GitUpdateResult = withContext(Dispatchers.IO) {
        rejectUnless(isRepository(directory), "当前项目不是可更新的 Git 仓库")
        rejectUnless(preview.canApply, "当前预览不满足安全快进条件，请重新检查更新")
        val upstreamRef = preview.upstreamRef ?: throw GitUpdateRejectedException("当前分支没有上游")
        val targetRevision = preview.targetRevision ?: throw GitUpdateRejectedException("更新目标已经失效")
        try {
            Git.open(directory).use { git ->
                val repository = git.repository
                rejectUnless(repository.repositoryState == RepositoryState.SAFE, "仓库正处于其他 Git 操作中")
                val expectedBranchRef = Constants.R_HEADS + preview.branchName
                rejectUnless(repository.fullBranch == expectedBranchRef, "当前分支已变化，请重新检查更新")
                val currentUpstreamRef = BranchConfig(repository.config, preview.branchName).trackingBranch
                rejectUnless(currentUpstreamRef == upstreamRef, "当前分支上游已变化，请重新检查更新")
                rejectUnless(git.status().call().isClean, "工作区已有本地修改，未执行更新")
                rejectUnless(
                    repository.resolve(Constants.HEAD)?.name == preview.headRevision,
                    "当前提交已变化，请重新检查更新",
                )
                rejectUnless(
                    repository.resolve(upstreamRef)?.name == targetRevision,
                    "远端更新预览已变化，请重新检查更新",
                )

                val merge = git.merge()
                    .include(ObjectId.fromString(targetRevision))
                    // 这里应用的是用户刚确认的目标提交，只允许快进，禁止生成合并提交或覆盖本地历史。 @author long
                    .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                    .setProgressMonitor(monitor)
                    .call()
                rejectUnless(
                    merge.mergeStatus == MergeResult.MergeStatus.FAST_FORWARD ||
                        merge.mergeStatus == MergeResult.MergeStatus.ALREADY_UP_TO_DATE,
                    "Git 更新不能安全快进：${merge.mergeStatus}",
                )
                rejectUnless(
                    repository.resolve(Constants.HEAD)?.name == targetRevision,
                    "Git 更新完成后提交校验失败",
                )
                GitUpdateResult(updated = merge.mergeStatus == MergeResult.MergeStatus.FAST_FORWARD)
            }
        } catch (error: Exception) {
            if (monitor.isCancelled) throw GitOperationCancelledException("已取消应用 Git 更新")
            if (error is GitUpdateRejectedException) throw error
            throw IllegalStateException("Git 更新失败：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    fun isRepository(directory: File): Boolean =
        directory.isDirectory && File(directory, Constants.DOT_GIT).exists()

    private fun analyzeUpdate(
        repository: Repository,
        branchName: String,
        upstreamRef: String,
        headRevision: String,
        targetRevision: String,
        localSummary: LocalChangeSummary,
        monitor: GitOperationProgressMonitor,
    ): GitUpdatePreview {
        val headId = ObjectId.fromString(headRevision)
        val targetId = ObjectId.fromString(targetRevision)
        val relation = RevWalk(repository).use { walk ->
            val head = walk.parseCommit(headId)
            val target = walk.parseCommit(targetId)
            when {
                headId == targetId -> GitUpdateRelation.UP_TO_DATE
                walk.isMergedInto(head, target) -> GitUpdateRelation.FAST_FORWARD
                walk.isMergedInto(target, head) -> GitUpdateRelation.LOCAL_AHEAD
                else -> GitUpdateRelation.DIVERGED
            }
        }
        val commitSummary = summarizeRemoteCommits(repository, headId, targetId, monitor)
        val changeSummary = summarizeRemoteChanges(repository, headId, targetId, monitor)
        return GitUpdatePreview(
            branchName = branchName,
            upstreamName = Repository.shortenRefName(upstreamRef),
            upstreamRef = upstreamRef,
            headRevision = headRevision,
            targetRevision = targetRevision,
            relation = relation,
            remoteCommitCount = commitSummary.total,
            remoteCommits = commitSummary.items,
            remoteCommitsTruncated = commitSummary.truncated,
            remoteChangeCount = changeSummary.total,
            remoteChanges = changeSummary.items,
            remoteChangesTruncated = changeSummary.truncated,
            localChangeCount = localSummary.total,
            localChanges = localSummary.items,
            localChangesTruncated = localSummary.truncated,
        )
    }

    private fun summarizeRemoteCommits(
        repository: Repository,
        headId: ObjectId,
        targetId: ObjectId,
        monitor: GitOperationProgressMonitor,
    ): PreviewSummary<GitCommitSummary> {
        if (headId == targetId) return PreviewSummary(emptyList(), 0, false)
        val items = mutableListOf<GitCommitSummary>()
        var total = 0
        var truncated = false
        RevWalk(repository).use { walk ->
            walk.markStart(walk.parseCommit(targetId))
            walk.markUninteresting(walk.parseCommit(headId))
            while (true) {
                if (monitor.isCancelled) throw GitOperationCancelledException("已取消检查 Git 更新")
                val commit = walk.next() ?: break
                total++
                if (items.size < MAX_PREVIEW_COMMITS) items += commit.toSummary()
                if (total >= MAX_PREVIEW_COMMITS_SCANNED) {
                    truncated = walk.next() != null
                    break
                }
            }
        }
        return PreviewSummary(items, total, truncated)
    }

    private fun summarizeRemoteChanges(
        repository: Repository,
        headId: ObjectId,
        targetId: ObjectId,
        monitor: GitOperationProgressMonitor,
    ): PreviewSummary<GitRemoteChange> {
        if (headId == targetId) return PreviewSummary(emptyList(), 0, false)
        val items = mutableListOf<GitRemoteChange>()
        var total = 0
        var truncated = false
        RevWalk(repository).use { walk ->
            val headTree = walk.parseCommit(headId).tree
            val targetTree = walk.parseCommit(targetId).tree
            TreeWalk(repository).use { tree ->
                tree.addTree(headTree)
                tree.addTree(targetTree)
                tree.isRecursive = true
                tree.filter = TreeFilter.ANY_DIFF
                while (tree.next()) {
                    if (monitor.isCancelled) throw GitOperationCancelledException("已取消检查 Git 更新")
                    val kind = when {
                        tree.getFileMode(0) == FileMode.MISSING -> GitRemoteChangeKind.ADDED
                        tree.getFileMode(1) == FileMode.MISSING -> GitRemoteChangeKind.DELETED
                        else -> GitRemoteChangeKind.MODIFIED
                    }
                    total++
                    if (items.size < MAX_PREVIEW_FILES) items += GitRemoteChange(tree.pathString, kind)
                    if (total >= MAX_PREVIEW_FILES_SCANNED) {
                        truncated = tree.next()
                        break
                    }
                }
            }
        }
        return PreviewSummary(items, total, truncated)
    }

    private fun summarizeLocalChanges(status: Status): LocalChangeSummary {
        val changes = linkedMapOf<String, GitLocalChangeKind>()
        status.added.forEach { changes[it] = GitLocalChangeKind.ADDED }
        status.changed.forEach { changes[it] = GitLocalChangeKind.MODIFIED }
        status.modified.forEach { changes[it] = GitLocalChangeKind.MODIFIED }
        status.removed.forEach { changes[it] = GitLocalChangeKind.DELETED }
        status.missing.forEach { changes[it] = GitLocalChangeKind.DELETED }
        status.untracked.forEach { changes[it] = GitLocalChangeKind.UNTRACKED }
        status.conflicting.forEach { changes[it] = GitLocalChangeKind.CONFLICTED }
        val sorted = changes.entries.sortedBy { it.key }
        return LocalChangeSummary(
            items = sorted.take(MAX_PREVIEW_LOCAL_FILES).map { GitLocalChange(it.key, it.value) },
            total = sorted.size,
            truncated = sorted.size > MAX_PREVIEW_LOCAL_FILES,
        )
    }

    private fun emptyPreview(
        branchName: String,
        headRevision: String,
        relation: GitUpdateRelation,
        localSummary: LocalChangeSummary,
    ): GitUpdatePreview = GitUpdatePreview(
        branchName = branchName,
        upstreamName = null,
        upstreamRef = null,
        headRevision = headRevision,
        targetRevision = null,
        relation = relation,
        remoteCommitCount = 0,
        remoteCommits = emptyList(),
        remoteCommitsTruncated = false,
        remoteChangeCount = 0,
        remoteChanges = emptyList(),
        remoteChangesTruncated = false,
        localChangeCount = localSummary.total,
        localChanges = localSummary.items,
        localChangesTruncated = localSummary.truncated,
    )

    private fun RevCommit.toSummary(): GitCommitSummary = GitCommitSummary(
        revision = name,
        title = shortMessage.ifBlank { "无提交说明" },
        authorName = authorIdent?.name.orEmpty().ifBlank { "未知作者" },
        committedAtEpochSeconds = commitTime.toLong(),
    )

    private fun freshUniqueDirectory(baseName: String): File {
        val root = File(context.filesDir, "projects").apply { mkdirs() }
        var suffix = 1
        while (true) {
            val name = if (suffix == 1) baseName else "$baseName-$suffix"
            val candidate = File(root, name)
            if (!candidate.exists()) {
                check(candidate.mkdirs()) { "无法创建 Git 项目目录：${candidate.absolutePath}" }
                return candidate
            }
            suffix++
        }
    }

    private fun rejectUnless(condition: Boolean, message: String) {
        if (!condition) throw GitUpdateRejectedException(message)
    }

    private companion object {
        const val GIT_TIMEOUT_SECONDS = 60
        const val MAX_PREVIEW_COMMITS = 5
        const val MAX_PREVIEW_COMMITS_SCANNED = 1_000
        const val MAX_PREVIEW_FILES = 12
        const val MAX_PREVIEW_FILES_SCANNED = 10_000
        const val MAX_PREVIEW_LOCAL_FILES = 20
    }

    private data class PreviewSummary<T>(
        val items: List<T>,
        val total: Int,
        val truncated: Boolean,
    )

    private typealias LocalChangeSummary = PreviewSummary<GitLocalChange>
}
