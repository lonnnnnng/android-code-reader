package com.lonnnnnng.codereader.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 未保存草稿必须经过真实文件落盘和 Store 重建链路验证。 @author long */
@RunWith(AndroidJUnit4::class)
class DraftStoreInstrumentedTest {
    @Test
    fun draftSurvivesStoreRecreationWithCompleteSourceText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "draft-store-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val draft = DocumentDraft(
            locationKind = "local",
            documentId = "/项目/src/用户服务.kt",
            documentName = "用户服务.kt",
            draftText = "fun main() {\n    println(\"未保存内容\")\n}\n",
            originalFingerprint = "sha256-original",
            updatedAtEpochMillis = 1_723_200_000_000L,
        )

        DraftStore(directory).save(draft)

        assertEquals(draft, DraftStore(directory).load(draft.documentId))
    }

    @Test
    fun oldestDraftIsEvictedWhenCountLimitIsReached() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "draft-store-capacity-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val store = DraftStore(directory, maxDrafts = 2, maxTotalBytes = 1024L * 1024L, maxDraftBytes = 1024)
        val drafts = (1..3).map { index ->
            DocumentDraft(
                locationKind = "local",
                documentId = "/project/file-$index.kt",
                documentName = "file-$index.kt",
                draftText = "draft-$index",
                originalFingerprint = "fingerprint-$index",
                updatedAtEpochMillis = index.toLong(),
            )
        }

        drafts.forEach(store::save)

        assertNull(store.load(drafts[0].documentId))
        assertNotNull(store.load(drafts[1].documentId))
        assertNotNull(store.load(drafts[2].documentId))
    }

    @Test
    fun explicitDeleteRemovesPersistedDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "draft-store-delete-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val draft = DocumentDraft(
            locationKind = "saf",
            documentId = "content://provider/document/README.md",
            documentName = "README.md",
            draftText = "# 草稿",
            originalFingerprint = "fingerprint",
            updatedAtEpochMillis = 10L,
        )
        val store = DraftStore(directory)
        store.save(draft)

        store.delete(draft.documentId)

        assertNull(store.load(draft.documentId))
    }
}
