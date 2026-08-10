package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.data.GitOperationProgress
import com.lonnnnnng.codereader.data.GitOperationProgressMonitor
import com.lonnnnnng.codereader.data.GitLocalChangeKind
import com.lonnnnnng.codereader.data.GitRemoteChangeKind
import com.lonnnnnng.codereader.data.GitRepositoryManager
import com.lonnnnnng.codereader.data.GitUpdateRelation
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
        val project = GitRepositoryManager(context).clone(
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
    fun cleanRepositoryPreviewsRemoteChangesBeforeApplyingTheExactRevision() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = createLocalFixture(context.cacheDir, "git-preview")

        try {
            fixture.pushRemoteUpdate("release v2") { source ->
                File(source, "version.txt").writeText("v2")
                File(source, "CHANGELOG.md").writeText("# v2\n")
            }

            val manager = GitRepositoryManager(context)
            val preview = manager.previewUpdate(fixture.local)

            assertEquals("main", preview.branchName)
            assertEquals("origin/main", preview.upstreamName)
            assertEquals("refs/remotes/origin/main", preview.upstreamRef)
            assertEquals(GitUpdateRelation.FAST_FORWARD, preview.relation)
            assertEquals(1, preview.remoteCommitCount)
            assertTrue(preview.remoteCommits.any { it.title == "release v2" })
            assertTrue(
                preview.remoteChanges.any {
                    it.path == "version.txt" && it.kind == GitRemoteChangeKind.MODIFIED
                },
            )
            assertTrue(
                preview.remoteChanges.any {
                    it.path == "CHANGELOG.md" && it.kind == GitRemoteChangeKind.ADDED
                },
            )
            assertTrue(preview.canApply)
            assertEquals("v1", File(fixture.local, "version.txt").readText())

            val result = manager.applyUpdate(fixture.local, preview)

            assertTrue(result.updated)
            assertEquals("v2", File(fixture.local, "version.txt").readText())
            assertEquals(preview.targetRevision, Git.open(fixture.local).use { it.repository.resolve("HEAD").name })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun localModificationIsListedAndPreventsRemoteUpdate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = createLocalFixture(context.cacheDir, "git-dirty-preview")

        try {
            fixture.pushRemoteUpdate("remote update") { source ->
                File(source, "version.txt").writeText("remote-v2")
            }
            File(fixture.local, "version.txt").writeText("local draft")

            val preview = GitRepositoryManager(context).previewUpdate(fixture.local)

            assertEquals(GitUpdateRelation.FAST_FORWARD, preview.relation)
            assertEquals(1, preview.localChangeCount)
            assertTrue(
                preview.localChanges.any {
                    it.path == "version.txt" && it.kind == GitLocalChangeKind.MODIFIED
                },
            )
            assertFalse(preview.canApply)
            assertEquals("local draft", File(fixture.local, "version.txt").readText())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun applyingPreviewRejectsChangedBranchAndUpstreamConfiguration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = createLocalFixture(context.cacheDir, "git-stale-preview")

        try {
            fixture.pushRemoteUpdate("remote update") { source ->
                File(source, "version.txt").writeText("remote-v2")
            }

            val manager = GitRepositoryManager(context)
            val preview = manager.previewUpdate(fixture.local)
            Git.open(fixture.local).use { localGit ->
                localGit.branchCreate().setName("review").call()
                localGit.checkout().setName("review").call()
            }

            val branchError = runCatching { manager.applyUpdate(fixture.local, preview) }.exceptionOrNull()

            assertTrue(branchError?.message.orEmpty().contains("当前分支已变化"))
            assertEquals("v1", File(fixture.local, "version.txt").readText())

            Git.open(fixture.local).use { localGit ->
                localGit.checkout().setName("main").call()
                localGit.repository.config.apply {
                    setString("branch", "main", "merge", "refs/heads/other")
                    save()
                }
            }
            val upstreamError = runCatching { manager.applyUpdate(fixture.local, preview) }.exceptionOrNull()

            assertTrue(upstreamError?.message.orEmpty().contains("当前分支上游已变化"))
            assertEquals("v1", File(fixture.local, "version.txt").readText())
        } finally {
            fixture.close()
        }
    }

    /** 本地 bare remote 不依赖网络，专门验证预览和快进的安全边界。 @author long */
    private fun createLocalFixture(cacheDir: File, name: String): GitFixture {
        val root = File(cacheDir, "$name-${System.nanoTime()}").apply { mkdirs() }
        val remote = File(root, "remote.git")
        val source = File(root, "source")
        val local = File(root, "local")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(remote).call().close()
        Git.init().setInitialBranch("main").setDirectory(source).call().use { sourceGit ->
            File(source, "version.txt").writeText("v1")
            commitAll(sourceGit, "initial")
            sourceGit.remoteAdd().setName("origin").setUri(URIish(remote.toURI().toString())).call()
            sourceGit.push().setRemote("origin").setPushAll().call()
        }
        Git.cloneRepository()
            .setURI(remote.toURI().toString())
            .setDirectory(local)
            .setDepth(1)
            .call()
            .close()
        return GitFixture(root, source, local)
    }

    private fun GitFixture.pushRemoteUpdate(message: String, change: (File) -> Unit) {
        Git.open(source).use { sourceGit ->
            change(source)
            commitAll(sourceGit, message)
            sourceGit.push().setRemote("origin").setPushAll().call()
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

    private data class GitFixture(
        val root: File,
        val source: File,
        val local: File,
    ) {
        fun close() {
            root.deleteRecursively()
        }
    }
}
