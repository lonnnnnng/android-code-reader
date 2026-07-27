package com.lonnnnnng.codereader.data

import java.net.URLDecoder
import java.net.URLEncoder

/** @author long */
data class RecentProjectRecord(
    val kind: String,
    val title: String,
    val value: String,
)

/**
 * 最近项目只保留应用能够稳定恢复的来源，并用来源地址作为项目唯一标识。
 *
 * @author long
 */
object RecentProjectPolicy {
    private val recoverableKinds = setOf("saf", "local")

    fun normalize(projects: List<RecentProjectRecord>, maxCount: Int): List<RecentProjectRecord> {
        // 偏好数据可能来自旧版本或异常中断；加载前收口来源和唯一键，避免重复列表 key 让页面崩溃。
        return projects.asSequence()
            .filter { it.kind in recoverableKinds && it.value.isNotBlank() }
            .distinctBy { it.kind to it.value }
            .take(maxCount.coerceAtLeast(0))
            .toList()
    }
}

/** @author long */
object RecentProjectCodec {
    fun encode(projects: List<RecentProjectRecord>): String = projects.joinToString("\n") { project ->
        listOf(project.kind, project.title, project.value).joinToString("\t") { encodeField(it) }
    }

    fun decode(value: String?): List<RecentProjectRecord> = value.orEmpty().lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 3) null else runCatching {
                RecentProjectRecord(decodeField(fields[0]), decodeField(fields[1]), decodeField(fields[2]))
            }.getOrNull()
        }
        .toList()

    private fun encodeField(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun decodeField(value: String): String = URLDecoder.decode(value, "UTF-8")
}
