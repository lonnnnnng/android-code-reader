package com.lonnnnnng.codereader.ui

import com.lonnnnnng.codereader.model.EntryLocation
import com.lonnnnnng.codereader.model.SourceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** @author long */
class MarkdownResourceIndexTest {
    private val image = source("docs/images/architecture.svg")
    private val attachment = source("attachments/readme.txt")
    private val index = MarkdownResourceIndex(
        documentPath = "docs/guide/README.md",
        projectEntries = listOf(image, attachment),
    )

    @Test
    fun `相对路径按 Markdown 所在目录解析`() {
        assertEquals(image.source, index.resolve("../images/architecture.svg"))
        assertEquals(attachment.source, index.resolve("../../attachments/readme.txt#details"))
    }

    @Test
    fun `项目根越界和外部地址不能读取本地文件`() {
        assertNull(index.resolve("../../../private.txt"))
        assertNull(index.resolve("/absolute/path.png"))
        assertNull(index.resolve("https://example.com/image.png"))
        assertNull(index.resolve("data:image/png;base64,AAAA"))
    }

    private fun source(path: String): com.lonnnnnng.codereader.model.ProjectTreeEntry {
        val file = File("/tmp/markdown-resource-test/$path")
        val entry = SourceEntry(
            name = file.name,
            isDirectory = false,
            size = 1,
            canWrite = false,
            location = EntryLocation.Local(file),
        )
        return com.lonnnnnng.codereader.model.ProjectTreeEntry(
            source = entry,
            path = path,
            parentId = null,
            depth = path.count { it == '/' },
        )
    }
}
