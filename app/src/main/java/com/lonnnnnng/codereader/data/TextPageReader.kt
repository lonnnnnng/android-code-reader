package com.lonnnnnng.codereader.data

import java.io.Reader

/** @author long */
data class TextPage(
    val text: String,
    val nextCharacter: Long,
    val hasMore: Boolean,
)

/** @author long */
object TextPageReader {
    fun read(reader: Reader, startCharacter: Long, pageCharacters: Int): TextPage {
        var remaining = startCharacter
        while (remaining > 0) {
            val skipped = reader.skip(remaining)
            if (skipped <= 0) {
                if (reader.read() < 0) return TextPage("", startCharacter, false)
                remaining--
            } else {
                remaining -= skipped
            }
        }

        return readNext(reader, pageCharacters).let { page ->
            page.copy(nextCharacter = startCharacter + page.nextCharacter)
        }
    }

    /**
     * 在同一个字符流上连续读取下一页，搜索大文件时复用解码器，避免每页都从文件头重新跳过字符。
     * @author long
     */
    fun readNext(reader: Reader, pageCharacters: Int): TextPage {
        require(pageCharacters > 0) { "分页大小必须大于 0" }
        val buffer = CharArray(pageCharacters)
        var total = 0
        while (total < buffer.size) {
            val read = reader.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        val hasMore = if (total < pageCharacters) {
            false
        } else {
            // 生产环境使用 BufferedReader，mark/reset 可以探测下一字符而不吞掉页首字符。
            check(reader.markSupported()) { "连续分页需要支持 mark/reset 的字符流" }
            reader.mark(1)
            val next = reader.read()
            reader.reset()
            next >= 0
        }
        return TextPage(
            text = String(buffer, 0, total),
            nextCharacter = total.toLong(),
            hasMore = hasMore,
        )
    }
}
