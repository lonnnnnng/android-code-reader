package com.lonnnnnng.codereader.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** @author long */
@RunWith(AndroidJUnit4::class)
class AppReleaseParserInstrumentedTest {
    @Test
    fun latestRelease只接受约定Apk和GitHub摘要() {
        val release = AppReleaseParser.parse(validReleaseJson())

        assertEquals("v0.1.4", release.tagName)
        assertEquals("0.1.4", release.versionName)
        assertEquals("AndroidCodeReader-v0.1.4.apk", release.apk.name)
        assertEquals("a".repeat(64), release.apk.sha256)
        assertEquals(11_038_007L, release.apk.sizeBytes)
    }

    @Test
    fun 非GitHub下载地址和缺失摘要会被拒绝() {
        assertThrows(IllegalArgumentException::class.java) {
            AppReleaseParser.parse(validReleaseJson().replace("https://github.com/", "https://example.com/"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppReleaseParser.parse(validReleaseJson().replace("sha256:${"a".repeat(64)}", ""))
        }
    }

    private fun validReleaseJson(): String =
        """
        {
          "tag_name": "v0.1.4",
          "name": "源码阅读器 v0.1.4",
          "body": "增加在线更新",
          "html_url": "https://github.com/lonnnnnng/android-code-reader/releases/tag/v0.1.4",
          "assets": [
            {
              "name": "AndroidCodeReader-v0.1.4.apk",
              "state": "uploaded",
              "content_type": "application/vnd.android.package-archive",
              "size": 11038007,
              "digest": "sha256:${"a".repeat(64)}",
              "browser_download_url": "https://github.com/lonnnnnng/android-code-reader/releases/download/v0.1.4/AndroidCodeReader-v0.1.4.apk"
            }
          ]
        }
        """.trimIndent()
}
