package com.lonnnnnng.codereader.data

import android.util.AtomicFile
import android.content.Context
import com.lonnnnnng.codereader.model.OpenDocument
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * 单个文档的未保存草稿；原正文指纹用于判断磁盘内容是否在草稿产生后发生变化。
 *
 * @author long
 */
data class DocumentDraft(
    val locationKind: String,
    val documentId: String,
    val documentName: String,
    val draftText: String,
    val originalFingerprint: String,
    val updatedAtEpochMillis: Long,
)

/** 指纹同时包含原编码和正文，外部改写或用户切换编码后不会静默套用旧草稿。 @author long */
object DraftFingerprint {
    fun create(document: OpenDocument): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(document.encoding.name.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(document.totalBytes.toString().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(document.text.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}

/** 草稿超过移动端恢复预算时必须明确失败，不能在后台无限占用应用私有空间。 @author long */
class DraftCapacityException(message: String) : IOException(message)

/**
 * 草稿按文档独立保存，避免大段源码进入 SharedPreferences；AtomicFile 保证进程中断时保留上一份完整记录。
 *
 * @author long
 */
class DraftStore(
    private val directory: File,
    private val maxDrafts: Int = DEFAULT_MAX_DRAFTS,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    private val maxDraftBytes: Int = DEFAULT_MAX_DRAFT_BYTES,
) {
    init {
        require(maxDrafts >= 0) { "草稿数量上限不能为负数" }
        require(maxTotalBytes >= 0L) { "草稿总容量上限不能为负数" }
        require(maxDraftBytes >= 0) { "单个草稿容量上限不能为负数" }
    }

    @Synchronized
    fun save(draft: DocumentDraft) {
        require(draft.locationKind == "local" || draft.locationKind == "saf") { "无法识别草稿来源" }
        require(draft.documentId.isNotBlank()) { "草稿文档标识不能为空" }
        require(draft.documentName.isNotBlank()) { "草稿文件名不能为空" }

        val encoded = DraftRecordCodec.encode(draft, maxDraftBytes)
        if (encoded.size > maxTotalBytes) {
            throw DraftCapacityException("草稿超过 ${formatBytes(maxTotalBytes)} 的恢复空间上限")
        }
        directory.mkdirs()
        require(directory.isDirectory) { "无法创建草稿目录：${directory.absolutePath}" }

        val target = draftFile(draft.documentId)
        val atomicFile = AtomicFile(target)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            BufferedOutputStream(output).apply {
                write(encoded)
                flush()
            }
            output.fd.sync()
            atomicFile.finishWrite(output)
            target.setLastModified(draft.updatedAtEpochMillis)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw IOException("未保存草稿写入失败", error)
        }
        trimToCapacity(target)
    }

    @Synchronized
    fun load(documentId: String): DocumentDraft? {
        if (documentId.isBlank()) return null
        val atomicFile = AtomicFile(draftFile(documentId))
        if (!atomicFile.baseFile.exists()) return null
        val maxRecordBytes = maxDraftBytes.toLong() + MAX_RECORD_OVERHEAD_BYTES
        if (atomicFile.baseFile.length() > maxRecordBytes) return null
        return runCatching {
            atomicFile.openRead().use { input ->
                DraftRecordCodec.decode(input, maxDraftBytes)
                    .takeIf { it.documentId == documentId }
            }
        }.getOrNull()
    }

    @Synchronized
    fun delete(documentId: String) {
        if (documentId.isBlank()) return
        AtomicFile(draftFile(documentId)).delete()
    }

    private fun trimToCapacity(protectedFile: File) {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
            .orEmpty()
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
            .toMutableList()
        var totalBytes = files.sumOf(File::length)
        while (files.size > maxDrafts || totalBytes > maxTotalBytes) {
            val removable = files.asReversed().firstOrNull { it != protectedFile }
                ?: files.lastOrNull()
                ?: break
            files.remove(removable)
            totalBytes -= removable.length()
            AtomicFile(removable).delete()
        }
    }

    private fun draftFile(documentId: String): File = File(directory, "${sha256(documentId)}$FILE_SUFFIX")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }

    companion object {
        const val DIRECTORY_NAME = "document-drafts"
        const val DEFAULT_MAX_DRAFTS = 20
        const val DEFAULT_MAX_TOTAL_BYTES = 64L * 1024L * 1024L
        const val DEFAULT_MAX_DRAFT_BYTES = 32 * 1024 * 1024
        private const val FILE_SUFFIX = ".draft"
        private const val MAX_RECORD_OVERHEAD_BYTES = 4L * 1024L * 1024L + 128L

        /** 草稿包含未保存源码，放入系统明确禁止备份和 FileProvider 暴露的私有目录。 @author long */
        fun defaultDirectory(context: Context): File = File(context.noBackupFilesDir, DIRECTORY_NAME)
    }
}

/** 长度前缀格式独立限制每个字段，损坏文件不能伪造超大长度触发内存分配。 @author long */
private object DraftRecordCodec {
    private const val MAGIC = 0x4C_59_52_44
    private const val VERSION = 1
    private const val MAX_METADATA_BYTES = 1024 * 1024

    fun encode(draft: DocumentDraft, maxDraftBytes: Int): ByteArray {
        val draftBytes = draft.draftText.toByteArray(Charsets.UTF_8)
        if (draftBytes.size > maxDraftBytes) {
            throw DraftCapacityException("单个草稿超过 ${maxDraftBytes / (1024 * 1024)} MB 上限")
        }
        return java.io.ByteArrayOutputStream().use { bytes ->
            DataOutputStream(BufferedOutputStream(bytes)).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeSizedString(draft.locationKind, MAX_METADATA_BYTES)
                output.writeSizedString(draft.documentId, MAX_METADATA_BYTES)
                output.writeSizedString(draft.documentName, MAX_METADATA_BYTES)
                output.writeSizedString(draft.originalFingerprint, MAX_METADATA_BYTES)
                output.writeLong(draft.updatedAtEpochMillis)
                output.writeInt(draftBytes.size)
                output.write(draftBytes)
            }
            bytes.toByteArray()
        }
    }

    fun decode(source: InputStream, maxDraftBytes: Int): DocumentDraft {
        return DataInputStream(BufferedInputStream(source)).use { input ->
            require(input.readInt() == MAGIC) { "草稿文件格式错误" }
            require(input.readInt() == VERSION) { "草稿文件版本不受支持" }
            val locationKind = input.readSizedString(MAX_METADATA_BYTES)
            val documentId = input.readSizedString(MAX_METADATA_BYTES)
            val documentName = input.readSizedString(MAX_METADATA_BYTES)
            val fingerprint = input.readSizedString(MAX_METADATA_BYTES)
            val updatedAt = input.readLong()
            val draftSize = input.readInt()
            require(draftSize in 0..maxDraftBytes) { "草稿正文长度异常" }
            val draftBytes = ByteArray(draftSize)
            input.readFully(draftBytes)
            require(input.read() == -1) { "草稿文件包含未知尾部数据" }
            DocumentDraft(
                locationKind = locationKind,
                documentId = documentId,
                documentName = documentName,
                draftText = draftBytes.toString(Charsets.UTF_8),
                originalFingerprint = fingerprint,
                updatedAtEpochMillis = updatedAt,
            )
        }
    }

    private fun DataOutputStream.writeSizedString(value: String, maxBytes: Int) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "草稿元数据过长" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readSizedString(maxBytes: Int): String {
        val size = readInt()
        require(size in 0..maxBytes) { "草稿元数据长度异常" }
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }
}
