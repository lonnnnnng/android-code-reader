package com.lonnnnnng.codereader.update

import android.content.Context
import com.lonnnnnng.codereader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** 从固定 GitHub 仓库检查并下载正式 Release，不接受用户输入的更新地址。 @author long */
class AppUpdateRepository(private val context: Context) {
    suspend fun fetchLatestRelease(): AppRelease = withContext(Dispatchers.IO) {
        val connection = openConnection(LATEST_RELEASE_URL, "application/vnd.github+json")
        try {
            requireSuccessful(connection, "检查更新")
            GitHubUrlPolicy.requireTrusted(connection.url.toString())
            AppReleaseParser.parse(readTextLimited(connection.inputStream, MAX_RELEASE_JSON_BYTES))
        } finally {
            connection.disconnect()
        }
    }

    suspend fun downloadAndVerify(release: AppRelease, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val updatesDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(updatesDirectory, "${release.apk.name}.part")
        val target = File(updatesDirectory, release.apk.name)
        partial.delete()

        val connection = openConnection(release.apk.downloadUrl, "application/octet-stream")
        try {
            requireSuccessful(connection, "下载更新")
            GitHubUrlPolicy.requireTrusted(connection.url.toString())
            val responseLength = connection.contentLengthLong
            require(responseLength <= 0 || responseLength <= MAX_APK_BYTES) { "更新安装包超过 200 MB" }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L
            var lastProgress = -1
            partial.outputStream().buffered().use { output ->
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        require(downloaded <= MAX_APK_BYTES) { "更新安装包超过 200 MB" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        val progress = ((downloaded * 100) / release.apk.sizeBytes).coerceIn(0, 100).toInt()
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }

            require(downloaded == release.apk.sizeBytes) { "更新安装包大小与 Release 不一致" }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            require(actualSha256 == release.apk.sha256) { "更新安装包 SHA-256 校验失败" }

            // 校验完成前始终使用 .part；只有完整文件才能替换旧缓存，安装器不会读到半包。
            updatesDirectory.listFiles()
                .orEmpty()
                .filter { it.extension == "apk" && it != target }
                .forEach(File::delete)
            target.delete()
            require(partial.renameTo(target)) { "无法保存已验证的更新安装包" }
            onProgress(100)
            target
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, accept: String): HttpsURLConnection {
        GitHubUrlPolicy.requireTrusted(url)
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "AndroidCodeReader/${BuildConfig.VERSION_NAME}")
            setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
        }
    }

    private fun requireSuccessful(connection: HttpsURLConnection, operation: String) {
        val status = connection.responseCode
        require(status == HttpsURLConnection.HTTP_OK) { "$operation 失败（HTTP $status）" }
    }

    private fun readTextLimited(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        input.use { source ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "Release 响应内容过大" }
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/lonnnnnng/android-code-reader/releases/latest"
        const val GITHUB_API_VERSION = "2026-03-10"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_RELEASE_JSON_BYTES = 512 * 1024
        const val MAX_APK_BYTES = 200L * 1024 * 1024
    }
}
