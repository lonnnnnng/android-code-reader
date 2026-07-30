package com.lonnnnnng.codereader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Git 仓库地址、目录命名和进度换算必须脱离 Android 环境稳定验证。 @author long */
class GitRepositorySupportTest {
    @Test
    fun `公开 HTTPS 地址使用仓库名称作为本地目录`() {
        val address = GitRepositoryAddress.parse(" https://github.com/octocat/Hello-World.git ")

        assertEquals("https://github.com/octocat/Hello-World.git", address.url)
        assertEquals("Hello-World", address.repositoryName)
    }

    @Test
    fun `仓库名称会过滤文件系统不安全字符`() {
        val address = GitRepositoryAddress.parse("https://example.com/team/%E6%BA%90%E7%A0%81%20reader.git")

        assertEquals("源码-reader", address.repositoryName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `拒绝非 HTTPS 仓库地址`() {
        GitRepositoryAddress.parse("http://github.com/octocat/Hello-World.git")
    }

    @Test
    fun `JGit 已知任务输出中文阶段和确定进度`() {
        val progress = mutableListOf<GitOperationProgress>()
        val monitor = GitOperationProgressMonitor(progress::add)

        monitor.start(2)
        monitor.beginTask("Receiving objects", 100)
        monitor.update(25)

        assertEquals("正在接收对象", progress.last().detail)
        assertEquals(25, progress.last().percent)
        assertFalse(monitor.isCancelled)
    }

    @Test
    fun `JGit 未知总量保持不确定进度并支持取消`() {
        val progress = mutableListOf<GitOperationProgress>()
        val monitor = GitOperationProgressMonitor(progress::add)

        monitor.beginTask("Fetching origin", 0)
        monitor.update(3)
        monitor.cancel()

        assertEquals("正在获取远程更新", progress.last().detail)
        assertNull(progress.last().percent)
        assertTrue(monitor.isCancelled)
    }
}
