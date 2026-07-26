package com.lonnnnnng.codereader.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.lonnnnnng.codereader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** 下载完成后验证 APK 身份，并构造交给系统设置和系统安装器的 Intent。 @author long */
object AppUpdateInstaller {
    suspend fun validateDownloadedApk(context: Context, release: AppRelease, apkFile: File) = withContext(Dispatchers.IO) {
        require(apkFile.isFile) { "更新安装包不存在" }
        val packageManager = context.packageManager
        val archive = archivePackageInfo(packageManager, apkFile)
            ?: throw IllegalArgumentException("更新安装包无法解析")
        val installed = installedPackageInfo(packageManager, context.packageName)

        require(archive.packageName == BuildConfig.APPLICATION_ID) { "更新安装包的应用 ID 不匹配" }
        require(archive.versionName == release.versionName) { "更新安装包版本与 Release 不一致" }
        require(PackageInfoCompat.getLongVersionCode(archive) > BuildConfig.VERSION_CODE.toLong()) {
            "更新安装包版本不高于当前版本"
        }

        // SHA-256 防止下载损坏；当前签名集合必须完全一致，不能因多签名 APK 仅共享一张证书就放行。
        val installedSigners = currentSigningCertificateDigests(installed)
        val archiveSigners = currentSigningCertificateDigests(archive)
        require(installedSigners.isNotEmpty() && installedSigners == archiveSigners) {
            "更新安装包签名与当前应用不一致"
        }
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun createUnknownSourcesIntent(context: Context): Intent {
        require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { "当前系统不需要单独授权安装来源" }
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
    }

    fun createInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.updates", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            clipData = ClipData.newRawUri("update-apk", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageInfo(packageManager: PackageManager, apkFile: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getPackageInfo(packageName, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun currentSigningCertificateDigests(packageInfo: PackageInfo): Set<String> {
        val signatures: Array<out Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            packageInfo.signatures.orEmpty()
        }
        return signatures.mapTo(linkedSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
    }

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}
