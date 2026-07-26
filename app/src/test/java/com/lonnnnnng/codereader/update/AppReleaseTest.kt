package com.lonnnnnng.codereader.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** @author long */
class AppReleaseTest {
    @Test
    fun `数字版本按段比较而不是按字符串比较`() {
        assertTrue(isNewerVersion("0.1.10", "0.1.9"))
        assertTrue(isNewerVersion("v1.0.0", "0.9.99"))
        assertFalse(isNewerVersion("v0.1.3", "0.1.3"))
        assertFalse(isNewerVersion("0.1.2", "0.1.3"))
    }

    @Test
    fun `非正式版本标签不会进入更新比较`() {
        assertThrows(IllegalArgumentException::class.java) { AppVersion.parse("main") }
        assertThrows(IllegalArgumentException::class.java) { AppVersion.parse("v1.2.0-beta") }
    }
}
