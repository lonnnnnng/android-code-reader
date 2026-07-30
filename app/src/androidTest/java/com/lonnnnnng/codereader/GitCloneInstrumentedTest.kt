package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.GitOperationProgress
import com.lonnnnnng.codereader.data.GitOperationProgressMonitor
import com.lonnnnnng.codereader.data.ProjectImporter
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** @author long */
@RunWith(AndroidJUnit4::class)
class GitCloneInstrumentedTest {
    @Test
    fun publicHttpsRepositoryCanBeCloned() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val progress = mutableListOf<GitOperationProgress>()
        val project = ProjectImporter(context).cloneGit(
            "https://github.com/octocat/Hello-World.git",
            GitOperationProgressMonitor(progress::add),
        )

        try {
            assertTrue(project.name.matches(Regex("Hello-World(?:-\\d+)?")))
            assertTrue(project.listFiles().orEmpty().any { it.name.startsWith("README", ignoreCase = true) })
            assertTrue("克隆过程必须产生可展示的阶段反馈", progress.isNotEmpty())
        } finally {
            project.deleteRecursively()
        }
    }

    @Test
    fun clonedRepositoryCanFastForwardToLatestCommit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "git-update-${System.nanoTime()}").apply { mkdirs() }
        val remote = File(root, "remote.git")
        val source = File(root, "source")
        val local = File(root, "local")

        try {
            Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close()
            Git.init().setInitialBranch("main").setDirectory(source).call().use { sourceGit ->
                File(source, "version.txt").writeText("v1")
                commitAll(sourceGit, "initial")
                sourceGit.remoteAdd().setName("origin").setUri(URIish(remote.toURI().toString())).call()
                sourceGit.push().setRemote("origin").setPushAll().call()

                Git.cloneRepository()
                    .setURI(remote.toURI().toString())
                    .setDirectory(local)
                    .setDepth(1)
                    .call()
                    .close()

                File(source, "version.txt").writeText("v2")
                commitAll(sourceGit, "update")
                sourceGit.push().setRemote("origin").setPushAll().call()
            }

            val progress = mutableListOf<GitOperationProgress>()
            val importer = ProjectImporter(context)
            val updated = importer.updateGit(local, GitOperationProgressMonitor(progress::add))

            assertTrue(updated.updated)
            assertEquals("v2", File(local, "version.txt").readText())
            assertTrue("获取最新代码必须产生可展示的阶段反馈", progress.isNotEmpty())
            assertFalse(importer.updateGit(local).updated)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun commitAll(git: Git, message: String) {
        git.add().addFilepattern(".").call()
        git.commit()
            .setMessage(message)
            .setAuthor("long", "long@example.com")
            .setCommitter("long", "long@example.com")
            .call()
    }
}
