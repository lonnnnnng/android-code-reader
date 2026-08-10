package com.lonnnnnng.codereader.ui

import com.lonnnnnng.codereader.data.DocumentExportProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 导出进度文案属于用户可见契约，未知大小和越界回调都必须保持稳定。 @author long */
class DocumentExportProgressTest {
    @Test
    fun knownSizeProducesBoundedPercentAndReadableDetail() {
        val progress = DocumentExportProgress(
            copiedBytes = 3L * 1024L * 1024L,
            totalBytes = 2L * 1024L * 1024L,
        )

        assertEquals(100, exportProgressPercent(progress))
        assertEquals("已复制 3 MB / 2 MB", exportProgressDetail(progress))
    }

    @Test
    fun unknownSizeUsesIndeterminateProgress() {
        val progress = DocumentExportProgress(copiedBytes = 1536L, totalBytes = null)

        assertNull(exportProgressPercent(progress))
        assertEquals("已复制 1 KB", exportProgressDetail(progress))
    }
}
