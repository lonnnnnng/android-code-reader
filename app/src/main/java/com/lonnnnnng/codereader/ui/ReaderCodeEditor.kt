package com.lonnnnnng.codereader.ui

import android.content.Context
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 在 Sora 原生输入链路外只补移动端需要的粘贴缩进治理，其他编辑行为仍由库本身负责。
 *
 * @author long
 */
internal class ReaderCodeEditor(context: Context) : CodeEditor(context) {
    var optimizePasteIndentation: Boolean = true

    override fun pasteText(text: CharSequence?) {
        val source = text?.toString() ?: return super.pasteText(text)
        super.pasteText(preparePastedText(source))
    }

    /**
     * BottomSheet 关闭后 IME 输入连接可能尚未恢复，命令粘贴直接提交剪贴板正文，避免出现无反馈的空操作。
     *
     * @author long
     */
    fun pasteClipboardText() {
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return
        val source = buildString {
            repeat(clip.itemCount) { index ->
                if (index > 0) append(lineSeparator.content)
                append(clip.getItemAt(index).coerceToText(context)?.toString().orEmpty())
            }
        }
        if (source.isEmpty()) return
        // 粘贴已经完成缩进归一，不再让自动缩进和符号补全二次改写外部源码。 @author long
        commitText(preparePastedText(source), false, false)
    }

    private fun preparePastedText(source: String): String {
        if (!optimizePasteIndentation || !source.containsLineBreak()) return source
        val cursorPosition = cursor.left()
        val sourceLine = text.getLineString(cursorPosition.line)
        val currentIndent = sourceLine.takeWhile { it == ' ' || it == '\t' }
        return EditorPasteIndentation.format(
            source = source,
            currentIndent = currentIndent,
            tabWidth = tabWidth,
            lineSeparator = lineSeparator.content,
        )
    }
}

/** 多行粘贴只调整公共外层缩进，不重排用户代码或解释语言语法。 @author long */
internal object EditorPasteIndentation {
    fun format(source: String, currentIndent: String, tabWidth: Int, lineSeparator: String): String {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        if (lines.size <= 1) return source
        val normalizedTabWidth = tabWidth.coerceIn(2, 8)
        val commonIndentColumns = lines.asSequence()
            .filter { it.isNotBlank() }
            .map { leadingIndentColumns(it, normalizedTabWidth) }
            .minOrNull()
            ?: 0
        return lines.mapIndexed { index, line ->
            val dedented = removeLeadingIndent(line, commonIndentColumns, normalizedTabWidth)
            when {
                index == 0 -> dedented
                dedented.isBlank() -> ""
                else -> currentIndent + dedented
            }
        }.joinToString(lineSeparator)
    }

    private fun leadingIndentColumns(line: String, tabWidth: Int): Int {
        var columns = 0
        for (character in line) {
            when (character) {
                ' ' -> columns++
                '\t' -> columns += tabWidth - (columns % tabWidth)
                else -> return columns
            }
        }
        return columns
    }

    private fun removeLeadingIndent(line: String, columnsToRemove: Int, tabWidth: Int): String {
        if (columnsToRemove <= 0) return line
        var columns = 0
        var index = 0
        while (index < line.length && columns < columnsToRemove) {
            when (line[index]) {
                ' ' -> columns++
                '\t' -> columns += tabWidth - (columns % tabWidth)
                else -> break
            }
            index++
        }
        val remainingColumns = (columns - columnsToRemove).coerceAtLeast(0)
        return " ".repeat(remainingColumns) + line.substring(index)
    }
}

private fun String.containsLineBreak(): Boolean = indexOf('\n') >= 0 || indexOf('\r') >= 0
