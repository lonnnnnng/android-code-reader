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
}
