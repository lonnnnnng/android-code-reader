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

        val buffer = CharArray(pageCharacters + 1)
        var total = 0
        while (total < buffer.size) {
            val read = reader.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        val contentLength = total.coerceAtMost(pageCharacters)
        return TextPage(
            text = String(buffer, 0, contentLength),
            nextCharacter = startCharacter + contentLength,
            hasMore = total > pageCharacters,
        )
    }
}
