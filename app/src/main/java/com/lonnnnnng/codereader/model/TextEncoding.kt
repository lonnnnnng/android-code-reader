package com.lonnnnnng.codereader.model

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

/**
 * 文件原始编码。带 BOM 的编码单独建模，保存时才能恢复文件的 BOM 习惯。
 *
 * @author long
 */
enum class TextEncoding(
    val displayName: String,
    private val charsetName: String,
    private val bom: ByteArray = byteArrayOf(),
) {
    UTF_8("UTF-8", "UTF-8"),
    UTF_8_BOM("UTF-8 with BOM", "UTF-8", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())),
    UTF_16_LE("UTF-16 LE", "UTF-16LE", byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
    UTF_16_BE("UTF-16 BE", "UTF-16BE", byteArrayOf(0xFE.toByte(), 0xFF.toByte())),
    UTF_16_LE_NO_BOM("UTF-16 LE (无 BOM)", "UTF-16LE"),
    UTF_16_BE_NO_BOM("UTF-16 BE (无 BOM)", "UTF-16BE"),
    GB18030("GB18030", "GB18030"),
    BIG5("Big5", "Big5"),
    LATIN_1("Latin-1", "ISO-8859-1"),
    ;

    val charset: Charset by lazy { Charset.forName(charsetName) }

    fun encode(text: String): ByteArray {
        val body = text.toByteArray(charset)
        if (bom.isEmpty()) return body
        return ByteArray(bom.size + body.size).also {
            bom.copyInto(it)
            body.copyInto(it, destinationOffset = bom.size)
        }
    }

    internal fun decode(bytes: ByteArray): String = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    internal fun decodeStrict(bytes: ByteArray): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}

/** 解码结果同时携带编码，保证阅读、分段加载和保存使用同一套判断。 @author long */
internal data class DecodedText(
    val text: String,
    val encoding: TextEncoding,
)

/** 二进制探测使用独立异常，数据层可以补齐来源、大小和 MIME 信息。 @author long */
internal class BinaryContentException(name: String) :
    IllegalArgumentException("检测到二进制内容，不能作为源码打开：$name")

/**
 * 根据 BOM、严格 UTF-8 校验和中文编码可读性选择最可能的文本编码。
 *
 * @author long
 */
internal object TextEncodingDetector {
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())

    fun decode(bytes: ByteArray, name: String): DecodedText {
        return decode(bytes, name, detect(bytes))
    }

    fun decode(bytes: ByteArray, name: String, encoding: TextEncoding): DecodedText {
        if (looksBinary(bytes)) throw BinaryContentException(name)
        // BOM 是文件元数据而不是正文；用户手动切换编码时也不能把它显示成不可见字符。
        val bodyOffset = bomPrefixSize(bytes)
        val body = if (bodyOffset == 0) bytes else bytes.copyOfRange(bodyOffset, bytes.size)
        return DecodedText(encoding.decode(body), encoding)
    }

    fun detect(bytes: ByteArray): TextEncoding {
        if (bytes.startsWith(utf8Bom)) return TextEncoding.UTF_8_BOM
        if (bytes.startsWith(utf16LeBom)) return TextEncoding.UTF_16_LE
        if (bytes.startsWith(utf16BeBom)) return TextEncoding.UTF_16_BE
        looksLikeUtf16WithoutBom(bytes)?.let { return it }
        if (TextEncoding.UTF_8.decodeStrict(bytes) != null) return TextEncoding.UTF_8
        return chooseLegacyEncoding(bytes)
    }

    fun detectStream(input: InputStream, name: String): TextEncoding {
        val sample = ByteArray(DETECTION_SAMPLE_BYTES)
        var total = 0
        while (total < sample.size) {
            val read = input.read(sample, total, sample.size - total)
            if (read < 0) break
            total += read
        }
        val bytes = sample.copyOf(total)
        if (looksBinary(bytes)) throw BinaryContentException(name)
        return detect(bytes)
    }

    private fun chooseLegacyEncoding(bytes: ByteArray): TextEncoding {
        val candidates = listOf(TextEncoding.GB18030, TextEncoding.BIG5, TextEncoding.LATIN_1)
        return candidates
            .mapNotNull { encoding ->
                encoding.decodeStrict(bytes)?.let { text -> encoding to score(text) }
            }
            .maxByOrNull { it.second }
            ?.first
            ?: TextEncoding.GB18030
    }

    private fun score(text: String): Int {
        var score = 0
        text.forEach { char ->
            when {
                char == '\uFFFD' -> score -= 40
                char == '\u0000' -> score -= 100
                char.isISOControl() && char !in "\r\n\t" -> score -= 8
                char in '\u3400'..'\u9FFF' -> score += 5
                !char.isWhitespace() -> score++
            }
        }
        return score
    }

    private fun looksLikeUtf16WithoutBom(bytes: ByteArray): TextEncoding? {
        if (bytes.size < 8) return null
        val sampleSize = minOf(bytes.size, 4096)
        var evenZeroes = 0
        var oddZeroes = 0
        var pairs = 0
        var index = 0
        while (index + 1 < sampleSize) {
            if (bytes[index].toInt() == 0) evenZeroes++
            if (bytes[index + 1].toInt() == 0) oddZeroes++
            pairs++
            index += 2
        }
        return when {
            oddZeroes >= pairs * 0.35f -> TextEncoding.UTF_16_LE_NO_BOM
            evenZeroes >= pairs * 0.35f -> TextEncoding.UTF_16_BE_NO_BOM
            else -> null
        }
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes.startsWith(utf16LeBom) || bytes.startsWith(utf16BeBom)) return false
        if (looksLikeUtf16WithoutBom(bytes) != null) return false
        val sampleSize = minOf(bytes.size, 8192)
        var controls = 0
        for (index in 0 until sampleSize) {
            val value = bytes[index].toInt() and 0xFF
            if (value == 0) return true
            if (value < 0x09 || value in 0x0E..0x1F) controls++
        }
        return controls > sampleSize / 20
    }

    private fun bomPrefixSize(bytes: ByteArray): Int = when {
        bytes.startsWith(utf8Bom) -> utf8Bom.size
        bytes.startsWith(utf16LeBom) || bytes.startsWith(utf16BeBom) -> 2
        else -> 0
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val DETECTION_SAMPLE_BYTES = 64 * 1024
}
