package com.lonnnnnng.codereader.domain

import com.lonnnnnng.codereader.model.FileType
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** 源码与项目搜索共享同一组文本规则，避免界面显示的选项和实际匹配行为不一致。 @author long */
data class TextSearchOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val regularExpression: Boolean = false,
) {
    val activeCount: Int
        get() = listOf(caseSensitive, wholeWord, regularExpression).count { it }
}

/** @author long */
data class TextSearchMatch(
    val start: Int,
    val endExclusive: Int,
)

/** 命中列表可以设置保留上限，但总数仍保持准确，便于移动端只渲染必要范围。 @author long */
data class TextSearchScan(
    val matches: List<TextSearchMatch>,
    val totalMatches: Int,
    val truncated: Boolean,
)

/** 文件级搜索使用一基行号和零基列号，便于直接映射到 Sora Editor 的光标位置。 @author long */
data class TextSearchPosition(
    val line: Int,
    val column: Int,
    val endColumnExclusive: Int,
)

/** 大文件只保留有限数量的可跳转位置，但总命中数继续扫描到文件末尾。 @author long */
data class TextSearchPage(
    val matches: List<TextSearchPosition>,
    val totalMatches: Int,
    val truncated: Boolean,
)

/** 流式搜索只上报已扫描行数和当前命中数，避免为了百分比再次遍历整个文件。 @author long */
data class TextSearchProgress(
    val scannedLines: Int,
    val matchesFound: Int,
)

/** 批量替换同时返回新正文和真实替换次数，调用方可以据此反馈而无需再次扫描。 @author long */
data class TextReplacementResult(
    val text: String,
    val replacementCount: Int,
)

/**
 * 替换规则与文件内搜索保持逐行一致，避免 `^`、`$` 和整词选项在搜索与替换时产生不同结果。
 * 原始 CRLF/LF/CR 分隔符原样写回，配置文件不会因为一次替换被全量改换行格式。
 *
 * @author long
 */
object TextReplacementEngine {
    /**
     * 当前替换必须再次核对精确命中，避免后台搜索完成后正文已变化却误改相邻代码。
     * 正则替换沿用 Java 的 `$1` 与 `${name}` 捕获组语法，普通替换则把 `$` 视为普通字符。
     *
     * @author long
     */
    fun replacementForMatch(
        line: String,
        start: Int,
        endExclusive: Int,
        query: String,
        replacement: String,
        options: TextSearchOptions,
    ): String {
        require(start >= 0 && endExclusive >= start && endExclusive <= line.length) {
            "当前匹配位置无效，请重新搜索"
        }
        val searchMatcher = TextSearchMatcher.compile(query, options)
        if (!options.regularExpression) {
            val stillMatches = searchMatcher.scan(line).matches.any {
                it.start == start && it.endExclusive == endExclusive
            }
            if (!stillMatches) throw IllegalStateException("当前匹配已失效，请重新搜索")
            return replacement
        }

        val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        val regexMatcher = Pattern.compile(query, flags).matcher(line)
        while (regexMatcher.find()) {
            if (regexMatcher.start() != start || regexMatcher.end() != endExclusive) continue
            if (options.wholeWord && !isWholeWord(line, start, endExclusive)) continue
            return try {
                val expanded = StringBuffer(line.length)
                regexMatcher.appendReplacement(expanded, replacement)
                expanded.substring(start)
            } catch (error: RuntimeException) {
                throw IllegalArgumentException("替换表达式无效：${error.message ?: error.javaClass.simpleName}", error)
            }
        }
        throw IllegalStateException("当前匹配已失效，请重新搜索")
    }

    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        options: TextSearchOptions,
    ): TextReplacementResult {
        val matcher = TextSearchMatcher.compile(query, options)
        val output = StringBuilder(text.length)
        var replacementCount = 0
        var lineStart = 0

        while (lineStart <= text.length) {
            var lineEnd = lineStart
            while (lineEnd < text.length && text[lineEnd] != '\r' && text[lineEnd] != '\n') lineEnd++
            val line = text.substring(lineStart, lineEnd)
            val lineResult = if (options.regularExpression) {
                replaceRegexLine(line, query, replacement, options)
            } else {
                replacePlainLine(line, matcher, replacement)
            }
            output.append(lineResult.text)
            replacementCount += lineResult.replacementCount
            if (lineEnd >= text.length) break

            if (text[lineEnd] == '\r' && lineEnd + 1 < text.length && text[lineEnd + 1] == '\n') {
                output.append("\r\n")
                lineStart = lineEnd + 2
            } else {
                output.append(text[lineEnd])
                lineStart = lineEnd + 1
            }
        }

        return TextReplacementResult(output.toString(), replacementCount)
    }

    private fun replacePlainLine(
        line: String,
        matcher: TextSearchMatcher,
        replacement: String,
    ): TextReplacementResult {
        val matches = matcher.scan(line).matches
        if (matches.isEmpty()) return TextReplacementResult(line, 0)
        val output = StringBuilder(line.length)
        var cursor = 0
        matches.forEach { match ->
            output.append(line, cursor, match.start)
            output.append(replacement)
            cursor = match.endExclusive
        }
        output.append(line, cursor, line.length)
        return TextReplacementResult(output.toString(), matches.size)
    }

    private fun replaceRegexLine(
        line: String,
        query: String,
        replacement: String,
        options: TextSearchOptions,
    ): TextReplacementResult {
        val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        val javaMatcher = Pattern.compile(query, flags).matcher(line)
        val output = StringBuffer(line.length)
        var replacementCount = 0
        while (javaMatcher.find()) {
            if (options.wholeWord && !isWholeWord(line, javaMatcher.start(), javaMatcher.end())) continue
            try {
                javaMatcher.appendReplacement(output, replacement)
            } catch (error: RuntimeException) {
                throw IllegalArgumentException("替换表达式无效：${error.message ?: error.javaClass.simpleName}", error)
            }
            replacementCount++
        }
        javaMatcher.appendTail(output)
        return TextReplacementResult(output.toString(), replacementCount)
    }

    private fun isWholeWord(text: CharSequence, start: Int, endExclusive: Int): Boolean {
        val beforeIsWord = start > 0 && isWordCharacter(text[start - 1])
        val afterIsWord = endExclusive < text.length && isWordCharacter(text[endExclusive])
        return !beforeIsWord && !afterIsWord
    }

    private fun isWordCharacter(character: Char): Boolean = character == '_' || character.isLetterOrDigit()
}

/** @author long */
enum class ProjectSearchScope { PROJECT, CURRENT_DIRECTORY }

/** 项目级选项除了文本规则，还约束文件类型、当前目录和文件名/路径。 @author long */
data class ProjectSearchOptions(
    val text: TextSearchOptions = TextSearchOptions(),
    val fileType: FileType? = null,
    val scope: ProjectSearchScope = ProjectSearchScope.PROJECT,
    val directoryPath: String? = null,
    val pathFilter: String = "",
) {
    val activeCount: Int
        get() = text.activeCount + listOf(
            fileType != null,
            scope != ProjectSearchScope.PROJECT,
            pathFilter.isNotBlank(),
        ).count { it }

    fun accepts(path: String, detectedType: FileType): Boolean {
        if (fileType != null && fileType != detectedType) return false
        val normalizedPath = path.replace('\\', '/').trimStart('/')
        if (scope == ProjectSearchScope.CURRENT_DIRECTORY) {
            val directory = directoryPath?.replace('\\', '/')?.trim('/')
            if (directory == null) return false
            if (directory.isNotEmpty() && !normalizedPath.startsWith("$directory/")) return false
        }
        val filter = pathFilter.trim()
        return filter.isEmpty() || normalizedPath.contains(filter, ignoreCase = true)
    }
}

/**
 * 纯 Kotlin 搜索器是项目搜索和 Sora 命中计数的公共边界。
 * 正则在提交搜索时一次性编译，非法表达式会在扫描文件前直接反馈给用户。
 *
 * @author long
 */
class TextSearchMatcher private constructor(
    private val query: String,
    private val options: TextSearchOptions,
    private val pattern: Pattern?,
) {

    fun scan(text: CharSequence, maxStoredMatches: Int = Int.MAX_VALUE): TextSearchScan {
        require(maxStoredMatches >= 0) { "命中保留上限不能小于 0" }
        val matches = ArrayList<TextSearchMatch>(minOf(maxStoredMatches, 32))
        var totalMatches = 0

        fun record(start: Int, endExclusive: Int) {
            if (options.wholeWord && !isWholeWord(text, start, endExclusive)) return
            totalMatches++
            if (matches.size < maxStoredMatches) matches += TextSearchMatch(start, endExclusive)
        }

        if (pattern != null) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) record(matcher.start(), matcher.end())
        } else {
            var cursor = 0
            val lastStart = text.length - query.length
            while (cursor <= lastStart) {
                if (regionMatches(text, cursor, query, options.caseSensitive)) {
                    record(cursor, cursor + query.length)
                    cursor += query.length.coerceAtLeast(1)
                } else {
                    cursor++
                }
            }
        }

        return TextSearchScan(
            matches = matches,
            totalMatches = totalMatches,
            truncated = totalMatches > matches.size,
        )
    }

    /**
     * 逐行扫描让普通文本和流式大文件共享相同匹配规则；行列位置独立于文件编码和分页边界。
     * @author long
     */
    fun scanLines(lines: Sequence<String>, maxStoredMatches: Int = Int.MAX_VALUE): TextSearchPage {
        val collector = TextSearchLineCollector(this, maxStoredMatches)
        lines.forEachIndexed { index, line -> collector.accept(index + 1, line) }
        return collector.result()
    }

    private fun isWholeWord(text: CharSequence, start: Int, endExclusive: Int): Boolean {
        val beforeIsWord = start > 0 && isWordCharacter(text[start - 1])
        val afterIsWord = endExclusive < text.length && isWordCharacter(text[endExclusive])
        return !beforeIsWord && !afterIsWord
    }

    private fun isWordCharacter(character: Char): Boolean = character == '_' || character.isLetterOrDigit()

    companion object {
        fun compile(query: String, options: TextSearchOptions): TextSearchMatcher {
            require(query.isNotEmpty()) { "搜索内容不能为空" }
            val pattern = if (options.regularExpression) {
                val flags = if (options.caseSensitive) 0 else Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
                try {
                    Pattern.compile(query, flags)
                } catch (error: PatternSyntaxException) {
                    throw IllegalArgumentException("正则表达式无效：${error.description}", error)
                }
            } else {
                null
            }
            return TextSearchMatcher(query, options, pattern)
        }

        private fun regionMatches(text: CharSequence, start: Int, query: String, caseSensitive: Boolean): Boolean {
            if (start < 0 || start + query.length > text.length) return false
            for (index in query.indices) {
                val actual = text[start + index]
                val expected = query[index]
                if (caseSensitive) {
                    if (actual != expected) return false
                } else if (actual.lowercaseChar() != expected.lowercaseChar() &&
                    actual.uppercaseChar() != expected.uppercaseChar()
                ) {
                    return false
                }
            }
            return true
        }
    }
}

/**
 * Repository 每读取一行就交给收集器，既能响应协程取消，也无需把大文件完整装入内存。
 * @author long
 */
class TextSearchLineCollector(
    private val matcher: TextSearchMatcher,
    private val maxStoredMatches: Int = Int.MAX_VALUE,
) {
    private val matches = ArrayList<TextSearchPosition>(minOf(maxStoredMatches.coerceAtLeast(0), 32))
    private var mutableTotalMatches = 0

    val totalMatches: Int
        get() = mutableTotalMatches

    init {
        require(maxStoredMatches >= 0) { "命中保留上限不能小于 0" }
    }

    fun accept(lineNumber: Int, line: CharSequence) {
        require(lineNumber > 0) { "搜索行号必须大于 0" }
        val remaining = (maxStoredMatches - matches.size).coerceAtLeast(0)
        val scan = matcher.scan(line, remaining)
        mutableTotalMatches += scan.totalMatches
        scan.matches.forEach { match ->
            matches += TextSearchPosition(
                line = lineNumber,
                column = match.start,
                endColumnExclusive = match.endExclusive,
            )
        }
    }

    fun result(): TextSearchPage = TextSearchPage(
        matches = matches.toList(),
        totalMatches = mutableTotalMatches,
        truncated = mutableTotalMatches > matches.size,
    )
}
