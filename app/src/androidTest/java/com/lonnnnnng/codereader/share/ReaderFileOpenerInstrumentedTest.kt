package com.lonnnnnng.codereader.share

import android.content.Intent
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.BuildConfig
import com.lonnnnnng.codereader.model.BinaryFileInfo
import com.lonnnnnng.codereader.model.EntryLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 外部打开只允许签发应用管理文件的临时 content URI。 @author long */
@RunWith(AndroidJUnit4::class)
class ReaderFileOpenerInstrumentedTest {

    @Test
    fun appManagedFileUsesReadOnlyContentUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "projects/share-test/archive.bin").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x01, 0x02, 0x03))
        }

        val chooser = ReaderFileOpener.createChooserIntent(
            context,
            BinaryFileInfo(file.name, file.length(), "application/octet-stream", EntryLocation.Local(file)),
        )
        val viewIntent = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)
            ?: error("Chooser 缺少 ACTION_VIEW Intent")

        assertEquals(Intent.ACTION_VIEW, viewIntent.action)
        assertEquals("content", viewIntent.data?.scheme)
        assertEquals("${BuildConfig.APPLICATION_ID}.files", viewIntent.data?.authority)
        assertTrue(viewIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun fileOutsideAppStorageIsRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outside = File(context.cacheDir, "outside.bin").apply { writeBytes(byteArrayOf(0x01)) }

        val error = assertThrows(IllegalArgumentException::class.java) {
            ReaderFileOpener.createChooserIntent(
                context,
                BinaryFileInfo(outside.name, outside.length(), "application/octet-stream", EntryLocation.Local(outside)),
            )
        }

        assertEquals("只能将灵阅管理的本地文件交给其他应用打开", error.message)
    }
}
