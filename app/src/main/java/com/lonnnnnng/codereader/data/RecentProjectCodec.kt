package com.lonnnnnng.codereader.data

import java.net.URLDecoder
import java.net.URLEncoder

/** @author long */
data class RecentProjectRecord(
    val kind: String,
    val title: String,
    val value: String,
)

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
