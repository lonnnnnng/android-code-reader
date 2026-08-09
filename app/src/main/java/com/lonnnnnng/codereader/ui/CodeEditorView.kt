package com.lonnnnnng.codereader.ui

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.domain.TextSearchOptions
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

private class EditorDocumentBinding {
    var documentId: String? = null
    var renderedText: String = ""
    var suppressTextCallback: Boolean = false
    var suppressPositionCallback: Boolean = false
    var commandId: Long? = null
    var searchQuery: String? = null
    var searchOptions: TextSearchOptions? = null
    val scrollOffsets = mutableMapOf<String, Int>()
    val reportedLines = mutableMapOf<String, Int>()
    val reportedCursorLines = mutableMapOf<String, Int>()
}

/**
 * Compose 与 Sora Editor 的最小桥接层，只在切换文件时重设全文，输入时只回传变化。
 *
 * @author long
 */
@Composable
fun CodeEditorView(
    documentId: String,
    text: String,
    fileType: FileType,
    editable: Boolean,
    fontSizeSp: Float,
    backgroundColorArgb: Int,
    wordWrap: Boolean,
    command: ReaderCommand?,
    onTextChanged: (String) -> Unit,
    onReadingPositionChanged: (String, Int) -> Unit,
    onCursorPositionChanged: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val binding = remember { EditorDocumentBinding() }
    val latestOnTextChanged = androidx.compose.runtime.rememberUpdatedState(onTextChanged)
    val latestOnReadingPositionChanged = androidx.compose.runtime.rememberUpdatedState(onReadingPositionChanged)
    val latestOnCursorPositionChanged = androidx.compose.runtime.rememberUpdatedState(onCursorPositionChanged)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                setTextSize(fontSizeSp)
                isWordwrap = wordWrap
                isLineNumberEnabled = true
                colorScheme = SyntaxRegistry.createColorScheme()
                applyReaderAppearance(backgroundColorArgb)
                searcher.setEnsureOccurrenceVisible(true)
                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    if (!binding.suppressTextCallback &&
                        (event.action == ContentChangeEvent.ACTION_INSERT || event.action == ContentChangeEvent.ACTION_DELETE)
                    ) {
                        binding.renderedText = this.text.toString()
                        latestOnTextChanged.value(binding.renderedText)
                    }
                }
                subscribeEvent(ScrollEvent::class.java) { _, _ ->
                    reportReadingLine(
                        binding = binding,
                        documentId = binding.documentId,
                        line = firstVisibleLine + 1,
                        onReadingPositionChanged = latestOnReadingPositionChanged.value,
                    )
                }
                subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
                    reportCursorLine(
                        binding = binding,
                        documentId = binding.documentId,
                        line = event.left.line + 1,
                        onCursorPositionChanged = latestOnCursorPositionChanged.value,
                    )
                    reportReadingLine(
                        binding = binding,
                        documentId = binding.documentId,
                        line = event.left.line + 1,
                        onReadingPositionChanged = latestOnReadingPositionChanged.value,
                    )
                }
            }
        },
        update = { editor ->
            if (binding.documentId != documentId) {
                binding.documentId?.let { previousId ->
                    binding.scrollOffsets[previousId] = editor.scrollY
                    reportReadingLine(
                        binding = binding,
                        documentId = previousId,
                        line = editor.firstVisibleLine + 1,
                        onReadingPositionChanged = latestOnReadingPositionChanged.value,
                    )
                }
                binding.suppressPositionCallback = true
                binding.documentId = documentId
                binding.commandId = null
                editor.setEditorLanguage(SyntaxRegistry.createLanguage(fileType) ?: EmptyLanguage())
                replaceEditorText(editor, binding, text)
                binding.searchQuery = null
                binding.searchOptions = null
                binding.suppressPositionCallback = false
                restoreScrollOffset(editor, binding.scrollOffsets[documentId])
            } else if (binding.renderedText != text && editor.text.toString() != text) {
                // 大文件追加和标签页恢复属于外部状态变化，不能反向标记为用户编辑。
                val currentScrollY = editor.scrollY
                binding.suppressPositionCallback = true
                replaceEditorText(editor, binding, text)
                binding.suppressPositionCallback = false
                binding.searchQuery = null
                binding.searchOptions = null
                restoreScrollOffset(editor, currentScrollY)
            }
            editor.setTextSize(fontSizeSp)
            editor.applyReaderAppearance(backgroundColorArgb)
            editor.isWordwrap = wordWrap
            editor.editable = editable
            val commandTargetsDocument = command?.targetDocumentId?.let { it == documentId } ?: true
            if (command != null && commandTargetsDocument && binding.commandId != command.id) {
                binding.commandId = command.id
                handleEditorCommand(editor, binding, command, documentId)
            }
        },
        onRelease = { editor ->
            reportReadingLine(
                binding = binding,
                documentId = binding.documentId,
                line = editor.firstVisibleLine + 1,
                onReadingPositionChanged = latestOnReadingPositionChanged.value,
            )
            editor.release()
        },
    )
}

private fun reportCursorLine(
    binding: EditorDocumentBinding,
    documentId: String?,
    line: Int,
    onCursorPositionChanged: (String, Int) -> Unit,
) {
    val id = documentId ?: return
    if (binding.suppressPositionCallback) return
    val normalizedLine = line.coerceAtLeast(1)
    if (binding.reportedCursorLines[id] == normalizedLine) return
    binding.reportedCursorLines[id] = normalizedLine
    onCursorPositionChanged(id, normalizedLine)
}

/** 滚动事件频率远高于行变化，先在 View 层去重可避免 Compose 状态无意义刷新。 @author long */
private fun reportReadingLine(
    binding: EditorDocumentBinding,
    documentId: String?,
    line: Int,
    onReadingPositionChanged: (String, Int) -> Unit,
) {
    val id = documentId ?: return
    if (binding.suppressPositionCallback) return
    val normalizedLine = line.coerceAtLeast(1)
    if (binding.reportedLines[id] == normalizedLine) return
    binding.reportedLines[id] = normalizedLine
    onReadingPositionChanged(id, normalizedLine)
}

private fun CodeEditor.applyReaderAppearance(backgroundColorArgb: Int) {
    // 源码阅读统一跟随手机系统字体，避免额外字体包扩大 APK，也避免不同渲染内核出现字体不一致。
    setTypefaceText(Typeface.DEFAULT)
    colorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColorArgb)
    colorScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, backgroundColorArgb)
}

private fun replaceEditorText(editor: CodeEditor, binding: EditorDocumentBinding, text: String) {
    binding.suppressTextCallback = true
    editor.setText(text)
    binding.renderedText = text
    binding.suppressTextCallback = false
}

/** 文本重设会让 Android View 回到顶部，下一帧恢复原位置以保持连续阅读体验。 @author long */
private fun restoreScrollOffset(editor: CodeEditor, scrollY: Int?) {
    val offset = scrollY ?: return
    editor.post { editor.scrollTo(editor.scrollX, offset.coerceAtLeast(0)) }
}

private fun handleEditorCommand(
    editor: CodeEditor,
    binding: EditorDocumentBinding,
    command: ReaderCommand,
    documentId: String,
) {
    when (command.type) {
        ReaderCommandType.GOTO_LINE -> {
            // 新文件 setText() 后 Sora 需要到下一帧才稳定 lineCount，否则全局搜索的首次跳转会被夹到第 1 行。
            editor.postDelayed({
                // 标签切换不会销毁同一个 View，旧命令必须核对文档和命令 id 后才能继续执行。 @author long
                if (!isCurrentEditorCommand(binding, command, documentId)) return@postDelayed
                val line = (command.line - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                editor.setSelection(line, 0, true)
                // Sora 的光标更新和滚动是两条路径，显式确保目标行可见才能稳定承接 Markdown 预览位置。 @author long
                editor.ensurePositionVisible(line, 0, true)
            }, 250)
        }
        ReaderCommandType.GOTO_SEARCH_MATCH -> {
            ensureEditorSearch(editor, binding, command)
            editor.postDelayed({
                if (!isCurrentEditorCommand(binding, command, documentId)) return@postDelayed
                val line = (command.line - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                val column = command.column.coerceIn(0, editor.text.getColumnCount(line))
                editor.setSelection(line, column, true)
            }, 180)
        }
        ReaderCommandType.SEARCH_FORWARD,
        ReaderCommandType.SEARCH_BACKWARD -> {
            if (ensureEditorSearch(editor, binding, command)) {
                editor.postDelayed({
                    if (!isCurrentEditorCommand(binding, command, documentId)) return@postDelayed
                    if (command.type == ReaderCommandType.SEARCH_FORWARD) editor.searcher.gotoNext()
                    else editor.searcher.gotoPrevious()
                }, 120)
            } else if (command.type == ReaderCommandType.SEARCH_FORWARD) {
                editor.searcher.gotoNext()
            } else {
                editor.searcher.gotoPrevious()
            }
        }
        ReaderCommandType.CLEAR_SEARCH -> {
            editor.searcher.stopSearch()
            binding.searchQuery = null
            binding.searchOptions = null
        }
        ReaderCommandType.MARKDOWN_HEADING -> Unit
    }
}

private fun ensureEditorSearch(
    editor: CodeEditor,
    binding: EditorDocumentBinding,
    command: ReaderCommand,
): Boolean {
    if (binding.searchQuery == command.query && binding.searchOptions == command.searchOptions) return false
    val type = when {
        command.searchOptions.regularExpression -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
        command.searchOptions.wholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
        else -> EditorSearcher.SearchOptions.TYPE_NORMAL
    }
    editor.searcher.search(
        command.query,
        EditorSearcher.SearchOptions(type, !command.searchOptions.caseSensitive),
    )
    binding.searchQuery = command.query
    binding.searchOptions = command.searchOptions
    return true
}

private fun isCurrentEditorCommand(
    binding: EditorDocumentBinding,
    command: ReaderCommand,
    documentId: String,
): Boolean = binding.documentId == documentId &&
    binding.commandId == command.id &&
    (command.targetDocumentId == null || command.targetDocumentId == documentId)
