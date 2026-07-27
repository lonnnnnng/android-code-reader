package com.lonnnnnng.codereader.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** @author long */
class RecentProjectCodecTest {
    @Test
    fun `最近项目可无损保存包含中文和分隔符的标题`() {
        val projects = listOf(
            RecentProjectRecord("saf", "项目\t一", "content://tree/primary%3ACode"),
            RecentProjectRecord("local", "克隆仓库", "/data/user/0/app/files/repos/a"),
        )

        assertEquals(projects, RecentProjectCodec.decode(RecentProjectCodec.encode(projects)))
    }

    @Test
    fun `最近项目加载时过滤未知来源并按地址去重限制数量`() {
        val projects = listOf(
            RecentProjectRecord("saf", "新标题", "content://project/a"),
            RecentProjectRecord("saf", "旧标题", "content://project/a"),
            RecentProjectRecord("unknown", "未知来源", "unknown://project"),
            RecentProjectRecord("local", "空地址", ""),
        ) + (1..8).map { index ->
            RecentProjectRecord("local", "项目 $index", "/projects/$index")
        }

        val normalized = RecentProjectPolicy.normalize(projects, maxCount = 6)

        assertEquals(6, normalized.size)
        assertEquals(RecentProjectRecord("saf", "新标题", "content://project/a"), normalized.first())
        assertEquals(5, normalized.count { it.kind == "local" })
    }
}
