package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.ProjectImporter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** @author long */
@RunWith(AndroidJUnit4::class)
class GitCloneInstrumentedTest {
    @Test
    fun publicHttpsRepositoryCanBeCloned() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val project = ProjectImporter(context).cloneGit("https://github.com/octocat/Hello-World.git")

        assertTrue(project.listFiles().orEmpty().any { it.name.startsWith("README", ignoreCase = true) })
    }
}
