package com.lonnnnnng.codereader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** @author long */
class ReadingStateCodecTest {
    @Test
    fun `阅读状态可保存中文路径与文件和行书签`() {
        val states = listOf(
            ReadingDocumentState(
                locationKind = "local",
                documentId = "/项目\t一/src/用户服务.java",
                documentName = "用户\n服务.java",
                projectRootId = "/项目\t一",
                lastViewedLine = 128,
                fileBookmarked = true,
                lineBookmarks = listOf(12, 88, 128),
            ),
            ReadingDocumentState(
                locationKind = "saf",
                documentId = "content://tree/primary%3ACode/document/README.md",
                documentName = "README.md",
            ),
        )

        assertEquals(states, ReadingStateCodec.decode(ReadingStateCodec.encode(states)))
    }

    @Test
    fun `异常偏好数据会被过滤去重并限制书签数量`() {
        val states = listOf(
            ReadingDocumentState("local", "/project/a.kt", "a.kt", lastViewedLine = 0, lineBookmarks = listOf(8, -1, 3, 8, 5)),
            ReadingDocumentState("local", "/project/a.kt", "old-a.kt", lastViewedLine = 90),
            ReadingDocumentState("unknown", "unknown://b", "b.kt"),
            ReadingDocumentState("saf", "", "c.kt"),
        )

        val normalized = ReadingStatePolicy.normalize(states, maxDocuments = 2, maxLineBookmarks = 2)

        assertEquals(1, normalized.size)
        assertEquals(1, normalized.single().lastViewedLine)
        assertEquals(listOf(3, 5), normalized.single().lineBookmarks)
    }

    @Test
    fun `不完整或未知版本的记录不影响其他文档`() {
        val valid = ReadingDocumentState("local", "/project/ok.go", "ok.go", lastViewedLine = 42)
        val persisted = listOf(
            "broken",
            ReadingStateCodec.encode(listOf(valid)),
            "99\tlocal\t%2Fproject%2Fold.go\told.go\t\t1\tfalse\t",
        ).joinToString("\n")

        assertEquals(listOf(valid), ReadingStateCodec.decode(persisted))
    }

    @Test
    fun `容量到达上限时优先保留用户书签`() {
        val recent = (1..205).map { index ->
            ReadingDocumentState("local", "/project/recent-$index.kt", "recent-$index.kt")
        }
        val oldBookmark = ReadingDocumentState(
            "local",
            "/project/important.kt",
            "important.kt",
            fileBookmarked = true,
            lineBookmarks = listOf(42),
        )

        val normalized = ReadingStatePolicy.normalize(recent + oldBookmark, maxDocuments = 200)

        assertEquals(200, normalized.size)
        assertEquals(true, normalized.any { it.documentId == oldBookmark.documentId })
        assertEquals(false, normalized.any { it.documentId == "/project/recent-200.kt" })
    }
}
