package com.lonnnnnng.codereader.update

import org.json.JSONObject
import java.net.URI
import java.util.Locale

/** GitHub Release 中可安装的正式 APK 资产。 @author long */
data class ReleaseApkAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
)

/** 设置页展示和下载所需的最小 Release 信息。 @author long */
data class AppRelease(
    val tagName: String,
    val versionName: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apk: ReleaseApkAsset,
)

/** 版本比较只接受数字版本，避免把分支名或非正式标签误判成可安装升级。 @author long */
internal class AppVersion private constructor(private val parts: List<Int>) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val length = maxOf(parts.size, other.parts.size)
        repeat(length) { index ->
            val result = (parts.getOrNull(index) ?: 0).compareTo(other.parts.getOrNull(index) ?: 0)
            if (result != 0) return result
        }
        return 0
    }

    companion object {
        private val VERSION_PATTERN = Regex("^[vV]?(\\d+(?:\\.\\d+){1,3})$")

        fun parse(value: String): AppVersion {
            val match = VERSION_PATTERN.matchEntire(value.trim())
                ?: throw IllegalArgumentException("无法识别版本号：$value")
            return AppVersion(match.groupValues[1].split('.').map(String::toInt))
        }
    }
}

internal fun isNewerVersion(candidate: String, current: String): Boolean =
    AppVersion.parse(candidate) > AppVersion.parse(current)

/** 把 GitHub API 响应收紧为本项目唯一允许的 APK 命名和摘要格式。 @author long */
internal object AppReleaseParser {
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    private const val MAX_NOTES_CHARS = 20_000
    private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")

    fun parse(jsonText: String): AppRelease {
        val json = JSONObject(jsonText)
        val tagName = json.getString("tag_name").trim()
        val versionName = AppVersion.parse(tagName).let { tagName.removePrefix("v").removePrefix("V") }
        val expectedAssetName = "AndroidCodeReader-$tagName.apk"
        val assets = json.getJSONArray("assets")
        val candidates = buildList {
            repeat(assets.length()) { index ->
                val asset = assets.getJSONObject(index)
                if (asset.optString("name") == expectedAssetName && asset.optString("state") == "uploaded") {
                    add(asset)
                }
            }
        }
        require(candidates.size == 1) { "Release 中缺少唯一安装包：$expectedAssetName" }

        val asset = candidates.single()
        val size = asset.getLong("size")
        require(size in 1..MAX_APK_BYTES) { "更新安装包大小异常" }
        val contentType = asset.optString("content_type")
        require(contentType == "application/vnd.android.package-archive") { "更新资产不是 Android APK" }
        val digest = asset.optString("digest")
            .removePrefix("sha256:")
            .lowercase(Locale.US)
        require(SHA256_PATTERN.matches(digest)) { "Release 未提供有效的 SHA-256" }

        val downloadUrl = GitHubUrlPolicy.requireTrusted(asset.getString("browser_download_url"))
        val pageUrl = GitHubUrlPolicy.requireTrusted(json.getString("html_url"))
        return AppRelease(
            tagName = tagName,
            versionName = versionName,
            title = json.optString("name").ifBlank { tagName }.take(120),
            notes = json.optString("body").take(MAX_NOTES_CHARS),
            pageUrl = pageUrl,
            apk = ReleaseApkAsset(expectedAssetName, downloadUrl, size, digest),
        )
    }
}

/** 所有更新请求都必须保持 HTTPS 且只能落在 GitHub 官方域名。 @author long */
internal object GitHubUrlPolicy {
    fun requireTrusted(value: String): String {
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("更新地址无效") }
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        require(uri.scheme.equals("https", ignoreCase = true)) { "更新地址不是 HTTPS" }
        require(host == "github.com" || host == "api.github.com" || host.endsWith(".githubusercontent.com")) {
            "更新地址不属于 GitHub"
        }
        return value
    }
}
