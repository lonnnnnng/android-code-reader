package com.lonnnnnng.codereader.domain

/** Markdown 目录项，index 与 WebView 内生成的 heading id 保持一致。 @author long */
data class MarkdownHeading(
    val index: Int,
    val level: Int,
    val title: String,
)

/** @author long */
object MarkdownOutlineParser {
    private val headingPattern = Regex("^(#{1,6})[ \\t]+(.+?)[ \\t]*$")
    private val setextPattern = Regex("^(=+|-+)[ \\t]*$")
    private val listItemPattern = Regex("^(?:[-+*]|\\d+[.)])[ \\t]+(.*)$")
    private val closingHashes = Regex("\\s+#+\\s*$")
    private val frontMatterStartPattern = Regex("^---[ \\t]*$")
    private val frontMatterEndPattern = Regex("^(?:---|\\.\\.\\.)[ \\t]*$")

    fun parse(markdown: String): List<MarkdownHeading> {
        var fenceMarker: Char? = null
        var fenceLength = 0
        var fenceQuoteDepth = 0
        var setextCandidate: String? = null
        var setextQuoteDepth = 0
        val headings = mutableListOf<MarkdownHeading>()

        markdownBodyLines(markdown).forEach { rawLine ->
            val line = normalizeLine(rawLine)
            if (line.indentedCode) {
                setextCandidate = null
                return@forEach
            }
            if (fenceMarker != null && line.quoteDepth != fenceQuoteDepth) {
                fenceMarker = null
                fenceLength = 0
            }

            val marker = line.content.firstOrNull()
            if (marker == '`' || marker == '~') {
                val runLength = line.content.takeWhile { it == marker }.length
                if (runLength >= 3) {
                    if (fenceMarker == null) {
                        fenceMarker = marker
                        fenceLength = runLength
                        fenceQuoteDepth = line.quoteDepth
                    } else if (
                        fenceMarker == marker &&
                        runLength >= fenceLength &&
                        line.content.drop(runLength).isBlank()
                    ) {
                        fenceMarker = null
                        fenceLength = 0
                    }
                    setextCandidate = null
                    return@forEach
                }
            }
            if (fenceMarker != null) {
                setextCandidate = null
                return@forEach
            }

            val atx = headingPattern.matchEntire(line.content)
            if (atx != null) {
                val title = atx.groupValues[2].replace(closingHashes, "").trim()
                if (title.isNotEmpty()) {
                    headings += MarkdownHeading(headings.size, atx.groupValues[1].length, title)
                }
                setextCandidate = null
                return@forEach
            }

            val setext = setextPattern.matchEntire(line.content)
            if (setext != null && !setextCandidate.isNullOrBlank() && line.quoteDepth == setextQuoteDepth) {
                headings += MarkdownHeading(
                    index = headings.size,
                    level = if (setext.groupValues[1].first() == '=') 1 else 2,
                    title = setextCandidate.orEmpty().trim(),
                )
                setextCandidate = null
                return@forEach
            }

            // 列表项本身不能成为下一行 Setext 标题的候选，否则紧随的分隔线会破坏目录编号。
            setextCandidate = line.content.takeIf { it.isNotBlank() && !line.inList }
            setextQuoteDepth = line.quoteDepth
        }
        return headings
    }

    /** Front Matter 属于文档元数据，不能把末尾分隔线前的属性误识别为 Setext 标题。 @author long */
    private fun markdownBodyLines(markdown: String): Sequence<String> {
        val lines = markdown.lineSequence().toList()
        val firstLine = lines.firstOrNull()?.removePrefix("\uFEFF")
        // 与 WebView 渲染器保持同一边界：分隔线必须顶格，避免目录与预览对标题数量产生分歧。
        if (firstLine == null || !frontMatterStartPattern.matches(firstLine)) return lines.asSequence()
        val closingOffset = lines.drop(1).indexOfFirst { line ->
            frontMatterEndPattern.matches(line)
        }
        return if (closingOffset >= 0) lines.drop(closingOffset + 2).asSequence() else lines.asSequence()
    }

    /** 预览会给引用块中的标题也生成 h1-h6，目录索引必须按同样的顺序统计。 @author long */
    private fun normalizeLine(rawLine: String): NormalizedLine {
        var remaining = rawLine
        var quoteDepth = 0
        while (true) {
            val spaces = remaining.takeWhile { it == ' ' }.length
            if (spaces > 3 || (spaces == 0 && remaining.startsWith('\t'))) {
                return NormalizedLine(remaining, quoteDepth, indentedCode = true)
            }
            remaining = remaining.drop(spaces)
            if (!remaining.startsWith('>')) {
                val listItem = listItemPattern.matchEntire(remaining)
                return NormalizedLine(
                    content = listItem?.groupValues?.get(1) ?: remaining,
                    quoteDepth = quoteDepth,
                    indentedCode = false,
                    inList = listItem != null,
                )
            }
            quoteDepth++
            remaining = remaining.drop(1).removePrefix(" ")
        }
    }

    private data class NormalizedLine(
        val content: String,
        val quoteDepth: Int,
        val indentedCode: Boolean,
        val inList: Boolean = false,
    )
}
