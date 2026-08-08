package com.lonnnnnng.codereader.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.lonnnnnng.codereader.BuildConfig
import com.lonnnnnng.codereader.model.BinaryFileInfo
import com.lonnnnnng.codereader.model.EntryLocation
import java.io.File

/** 把二进制来源转换成只读临时授权，交给系统中能处理该 MIME 类型的应用。 @author long */
object ReaderFileOpener {

    fun createChooserIntent(context: Context, fileInfo: BinaryFileInfo): Intent {
        val uri = when (val location = fileInfo.location) {
            is EntryLocation.Local -> providerUri(context, location.file)
            is EntryLocation.Saf -> when (location.uri.scheme) {
                "content" -> location.uri
                "file" -> providerUri(context, File(requireNotNull(location.uri.path)))
                else -> error("当前文件来源不能交给其他应用打开")
            }
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, fileInfo.mimeType)
            clipData = ClipData.newRawUri(fileInfo.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(viewIntent, "使用其他应用打开").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun providerUri(context: Context, file: File): Uri {
        val filesRoot = context.filesDir.canonicalFile
        val target = file.canonicalFile
        require(target.path.startsWith(filesRoot.path + File.separator)) {
            "只能将灵阅管理的本地文件交给其他应用打开"
        }
        return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", target)
    }
}
