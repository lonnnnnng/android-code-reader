package com.lonnnnnng.codereader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.lib.Constants
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 把 ZIP、Git 和内置示例转换为应用私有目录，目录浏览层无需区分导入来源。
 *
 * @author long
 */
internal class ProjectImporter(private val context: Context) {

    suspend fun importZip(uri: Uri): File = withContext(Dispatchers.IO) {
        val target = freshDirectory("zip-${System.currentTimeMillis()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entryCount = 0
                    var totalBytes = 0L
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        entryCount++
                        require(entryCount <= MAX_ZIP_ENTRIES) { "ZIP 文件条目过多" }
                        val output = File(target, entry.name)
                        // 规范化后的路径必须仍在目标目录内，否则 ZIP 可以覆盖应用的其他私有文件。
                        require(output.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                            "ZIP 中包含不安全路径：${entry.name}"
                        }
                        if (entry.isDirectory) {
                            output.mkdirs()
                        } else {
                            output.parentFile?.mkdirs()
                            output.outputStream().buffered().use { sink ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read < 0) break
                                    totalBytes += read
                                    require(totalBytes <= MAX_ZIP_BYTES) { "ZIP 解压后超过 200 MB" }
                                    sink.write(buffer, 0, read)
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            } ?: error("无法读取 ZIP 文件")
            collapseSingleRoot(target)
        } catch (error: Exception) {
            target.deleteRecursively()
            if (error is IllegalArgumentException) throw error
            throw IllegalArgumentException("ZIP 导入失败：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    suspend fun cloneGit(
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

    suspend fun updateGit(
        directory: File,
        monitor: GitOperationProgressMonitor = GitOperationProgressMonitor {},
    ): GitUpdateResult = withContext(Dispatchers.IO) {
        require(isGitRepository(directory)) { "当前项目不是可更新的 Git 仓库" }
        try {
            Git.open(directory).use { git ->
                val previousRevision = git.repository.resolve(Constants.HEAD)?.name
                val pullResult = git.pull()
                    // 只允许快进，不自动生成合并提交，也绝不覆盖用户在手机上保存的本地修改。 @author long
                    .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
                    .setRebase(false)
                    .setTimeout(GIT_TIMEOUT_SECONDS)
                    .setProgressMonitor(monitor)
                    .call()
                require(pullResult.isSuccessful) {
                    "远程更新不能安全快进，请先处理本地修改或分支差异"
                }
                val currentRevision = git.repository.resolve(Constants.HEAD)?.name
                GitUpdateResult(updated = previousRevision != currentRevision)
            }
        } catch (error: Exception) {
            if (monitor.isCancelled) throw GitOperationCancelledException("已取消获取最新代码")
            if (error is IllegalArgumentException) throw error
            throw IllegalStateException("Git 更新失败：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    fun isGitRepository(directory: File): Boolean =
        directory.isDirectory && File(directory, Constants.DOT_GIT).exists()

    suspend fun prepareBundledProject(assetPath: String, targetName: String): File = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, targetName)
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        copyAssets(assetPath, target)
        target
    }

    private fun freshDirectory(name: String): File {
        val root = File(context.filesDir, "projects").apply { mkdirs() }
        return File(root, name).apply {
            deleteRecursively()
            mkdirs()
        }
    }

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

    private fun collapseSingleRoot(directory: File): File {
        val children = directory.listFiles().orEmpty().filterNot { it.name == "__MACOSX" }
        return children.singleOrNull()?.takeIf { it.isDirectory } ?: directory
    }

    private fun copyAssets(path: String, target: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(path).use { input -> target.outputStream().use(input::copyTo) }
            return
        }
        target.mkdirs()
        children.forEach { child -> copyAssets("$path/$child", File(target, child)) }
    }

    private companion object {
        const val MAX_ZIP_ENTRIES = 10_000
        const val MAX_ZIP_BYTES = 200L * 1024 * 1024
        const val GIT_TIMEOUT_SECONDS = 60
    }
}
