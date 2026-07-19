package com.lonnnnnng.codereader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.ProjectImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** @author long */
@RunWith(AndroidJUnit4::class)
class ProjectImporterInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val importer = ProjectImporter(context)

    @Test
    fun zipProjectCanBeImportedAndRead() = runBlocking {
        val zipFile = File(context.cacheDir, "valid-project.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("demo/src/Main.java"))
            zip.write("public final class Main {}".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("demo/README.md"))
            zip.write("# Demo".toByteArray())
            zip.closeEntry()
        }

        val project = importer.importZip(Uri.fromFile(zipFile))

        assertTrue(File(project, "src/Main.java").isFile)
        assertEquals("# Demo", File(project, "README.md").readText())
    }

    @Test
    fun zipPathTraversalIsRejected() {
        val zipFile = File(context.cacheDir, "unsafe-project.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../escape.txt"))
            zip.write("unsafe".toByteArray())
            zip.closeEntry()
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { importer.importZip(Uri.fromFile(zipFile)) }
        }
    }
}
