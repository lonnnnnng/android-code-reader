package com.lonnnnnng.codereader.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * 把 ZIP、Git 和内置示例转换为应用私有目录，目录浏览层无需区分导入来源。
 *
 * @author long
 */
class ProjectImporter(private val context: Context) {

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

    suspend fun cloneGit(url: String): File = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        require(normalized.startsWith("https://")) { "当前仅支持 HTTPS Git 地址" }
        val target = freshDirectory("git-${shortHash(normalized)}-${System.currentTimeMillis()}")
        try {
            Git.cloneRepository()
                .setURI(normalized)
                .setDirectory(target)
                .setDepth(1)
                .setCloneAllBranches(false)
                .call()
                .close()
            target
        } catch (error: Exception) {
            target.deleteRecursively()
            throw IllegalStateException("Git 克隆失败：${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    suspend fun prepareSamples(): File = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, "sample-project")
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        copyAssets("samples", target)
        // Android assets 对点文件的枚举和打开行为不稳定，复制完成后显式生成真实 `.env` 供目录阅读验证。
        File(target, ".env").writeText(
            "APP_ENV=development\nAPI_BASE_URL=https://example.com/api\nFEATURE_CODE_READER=true\n",
        )
        target
    }

    private fun freshDirectory(name: String): File {
        val root = File(context.filesDir, "projects").apply { mkdirs() }
        return File(root, name).apply {
            deleteRecursively()
            mkdirs()
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

    private fun shortHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(6)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ZIP_ENTRIES = 10_000
        const val MAX_ZIP_BYTES = 200L * 1024 * 1024
    }
}
