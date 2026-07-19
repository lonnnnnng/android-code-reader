package com.lonnnnnng.codereader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** @author long */
class MarkdownOutlineParserTest {
    @Test
    fun `目录提取忽略代码块内标题并保留层级`() {
        val markdown = """
            # 项目说明
            ## 安装
            ```bash
            # 这不是标题
            ```
            ### 配置 ###
        """.trimIndent()

        assertEquals(
            listOf(
                MarkdownHeading(0, 1, "项目说明"),
                MarkdownHeading(1, 2, "安装"),
                MarkdownHeading(2, 3, "配置"),
            ),
            MarkdownOutlineParser.parse(markdown),
        )
    }

    @Test
    fun `Setext 和引用标题参与编号但缩进代码被忽略`() {
        val markdown = """
            一级标题
            ========

            > ## 引用标题

                # 这是缩进代码

            二级标题
            --------
        """.trimIndent()

        assertEquals(
            listOf(
                MarkdownHeading(0, 1, "一级标题"),
                MarkdownHeading(1, 2, "引用标题"),
                MarkdownHeading(2, 2, "二级标题"),
            ),
            MarkdownOutlineParser.parse(markdown),
        )
    }

    @Test
    fun `列表内标题参与编号且列表后分隔线不是 Setext`() {
        val markdown = """
            - # 列表标题
            - 普通列表项
            ---
            ## 后续标题
        """.trimIndent()

        assertEquals(
            listOf(
                MarkdownHeading(0, 1, "列表标题"),
                MarkdownHeading(1, 2, "后续标题"),
            ),
            MarkdownOutlineParser.parse(markdown),
        )
    }
}
