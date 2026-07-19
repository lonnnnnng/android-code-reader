package com.lonnnnnng.codereader.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

/** @author long */
class TextPageReaderTest {
    @Test
    fun `分段读取连续返回内容且不重复字符`() {
        val source = "第一段-第二段-结束"

        val first = TextPageReader.read(StringReader(source), startCharacter = 0, pageCharacters = 4)
        val second = TextPageReader.read(StringReader(source), startCharacter = first.nextCharacter, pageCharacters = 4)
        val third = TextPageReader.read(StringReader(source), startCharacter = second.nextCharacter, pageCharacters = 20)

        assertEquals(source, first.text + second.text + third.text)
        assertEquals(false, third.hasMore)
    }
}
