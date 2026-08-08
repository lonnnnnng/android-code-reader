package com.lonnnnnng.codereader.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** @author long */
class TextEncodingTest {

    @Test
    fun `识别 UTF BOM 并移除正文中的 BOM`() {
        val utf8 = TextEncoding.UTF_8_BOM.encode("你好 UTF-8")
        val utf16Le = TextEncoding.UTF_16_LE.encode("你好 UTF-16LE")
        val utf16Be = TextEncoding.UTF_16_BE.encode("你好 UTF-16BE")

        assertDecoded(utf8, TextEncoding.UTF_8_BOM, "你好 UTF-8")
        assertDecoded(utf16Le, TextEncoding.UTF_16_LE, "你好 UTF-16LE")
        assertDecoded(utf16Be, TextEncoding.UTF_16_BE, "你好 UTF-16BE")
    }

    @Test
    fun `严格 UTF8 失败后识别 GB18030 中文源码`() {
        val source = "public class 用户服务 { // 查询订单\n}"
        val bytes = source.toByteArray(Charset.forName("GB18030"))

        val decoded = TextEncodingDetector.decode(bytes, "UserService.java")

        assertEquals(TextEncoding.GB18030, decoded.encoding)
        assertEquals(source, decoded.text)
    }

    @Test
    fun `识别 Big5 中文源码`() {
        val source = "class 使用者服務 { // 查詢訂單\n}"
        val bytes = source.toByteArray(Charset.forName("Big5"))

        val decoded = TextEncodingDetector.decode(bytes, "UserService.cs")

        assertEquals(TextEncoding.BIG5, decoded.encoding)
        assertEquals(source, decoded.text)
    }

    @Test
    fun `中文编码均不匹配时回退 Latin1`() {
        val source = "// café déjà vu"
        val bytes = source.toByteArray(StandardCharsets.ISO_8859_1)

        val decoded = TextEncodingDetector.decode(bytes, "notes.properties")

        assertEquals(TextEncoding.LATIN_1, decoded.encoding)
        assertEquals(source, decoded.text)
    }

    @Test
    fun `无 BOM UTF16 仍保留无 BOM 保存形式`() {
        val source = "class 用户服务 {}"
        val bytes = source.toByteArray(StandardCharsets.UTF_16LE)

        val decoded = TextEncodingDetector.decode(bytes, "UserService.java")

        assertEquals(TextEncoding.UTF_16_LE_NO_BOM, decoded.encoding)
        assertEquals(source, decoded.text)
        assertArrayEquals(bytes, decoded.encoding.encode(decoded.text))
    }

    @Test
    fun `二进制内容不会误当作源码`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x00, 0x7F)

        val error = assertThrows(IllegalArgumentException::class.java) {
            TextEncodingDetector.decode(bytes, "archive.bin")
        }

        assertEquals("检测到二进制内容，不能作为源码打开：archive.bin", error.message)
    }

    @Test
    fun `保存时保留 UTF8 BOM`() {
        val bytes = TextEncoding.UTF_8_BOM.encode("package demo")

        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3),
        )
    }

    private fun assertDecoded(bytes: ByteArray, encoding: TextEncoding, text: String) {
        val decoded = TextEncodingDetector.decode(bytes, "sample.txt")
        assertEquals(encoding, decoded.encoding)
        assertEquals(text, decoded.text)
    }
}
