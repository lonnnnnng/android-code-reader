package com.lonnnnnng.codereader.ui

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.domain.TextSearchOptions
import com.lonnnnnng.codereader.domain.TextReplacementEngine
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/** Compose 只消费撤销/重做能力，不持有 Sora 的可变文本对象。 @author long */
data class EditorHistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/** 替换结果回传稳定业务字段，Compose 不需要读取 Sora 搜索器内部状态。 @author long */
data class EditorReplacementResult(
    val documentId: String,
    val query: String,
    val searchOptions: TextSearchOptions,
    val replacementCount: Int,
    val replaceAll: Boolean,
    val errorMessage: String? = null,
)

private class EditorDocumentBinding {
    var documentId: String? = null
    var renderedText: String = ""
    var suppressTextCallback: Boolean = false
    var suppressPositionCallback: Boolean = false
    var commandId: Long? = null
    val scrollOffsets = mutableMapOf<String, Int>()
    val reportedLines = mutableMapOf<String, Int>()
    val reportedCursorLines = mutableMapOf<String, Int>()
    val historyStates = mutableMapOf<String, EditorHistoryState>()
    var languageFileType: FileType? = null
    var languageTabWidth: Int = 0
    var languageIndentWithTabs: Boolean = false
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
    autoIndent: Boolean = true,
    autoClosePairs: Boolean = true,
    tabWidth: Int = 4,
    indentWithTabs: Boolean = false,
    optimizePasteIndentation: Boolean = true,
    command: ReaderCommand?,
    onTextChanged: (String) -> Unit,
    onHistoryChanged: (String, EditorHistoryState) -> Unit = { _, _ -> },
    onReplacementCompleted: (EditorReplacementResult) -> Unit = {},
    onReadingPositionChanged: (String, Int) -> Unit,
    onCursorPositionChanged: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val binding = remember { EditorDocumentBinding() }
    val latestOnTextChanged = androidx.compose.runtime.rememberUpdatedState(onTextChanged)
    val latestOnHistoryChanged = androidx.compose.runtime.rememberUpdatedState(onHistoryChanged)
    val latestOnReplacementCompleted = androidx.compose.runtime.rememberUpdatedState(onReplacementCompleted)
    val latestOnReadingPositionChanged = androidx.compose.runtime.rememberUpdatedState(onReadingPositionChanged)
    val latestOnCursorPositionChanged = androidx.compose.runtime.rememberUpdatedState(onCursorPositionChanged)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ReaderCodeEditor(context).apply {
                setTextSize(fontSizeSp)
                isWordwrap = wordWrap
                isLineNumberEnabled = true
                applyEditorInputSettings(
                    autoIndent = autoIndent,
                    autoClosePairs = autoClosePairs,
                    tabWidth = tabWidth,
                    indentWithTabs = indentWithTabs,
                    optimizePasteIndentation = optimizePasteIndentation,
                )
                colorScheme = SyntaxRegistry.createColorScheme()
                applyReaderAppearance(backgroundColorArgb)
                searcher.setEnsureOccurrenceVisible(true)
                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    if (!binding.suppressTextCallback &&
                        (event.action == ContentChangeEvent.ACTION_INSERT || event.action == ContentChangeEvent.ACTION_DELETE)
                    ) {
                        binding.renderedText = this.text.toString()
                        latestOnTextChanged.value(binding.renderedText)
                        post {
                            if (isReleased) return@post
                            reportHistoryState(
                                editor = this,
                                binding = binding,
                                onHistoryChanged = latestOnHistoryChanged.value,
                            )
                        }
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
                applyEditorLanguage(editor, binding, fileType, tabWidth, indentWithTabs, force = true)
                replaceEditorText(editor, binding, text)
                binding.suppressPositionCallback = false
                restoreScrollOffset(editor, binding.scrollOffsets[documentId])
                editor.post {
                    if (editor.isReleased) return@post
                    reportHistoryState(editor, binding, latestOnHistoryChanged.value)
                }
            } else if (binding.renderedText != text && editor.text.toString() != text) {
                // 大文件追加和标签页恢复属于外部状态变化，不能反向标记为用户编辑。
                val currentScrollY = editor.scrollY
                binding.suppressPositionCallback = true
                replaceEditorText(editor, binding, text)
                binding.suppressPositionCallback = false
                restoreScrollOffset(editor, currentScrollY)
                editor.post {
                    if (editor.isReleased) return@post
                    reportHistoryState(editor, binding, latestOnHistoryChanged.value)
                }
            }
            editor.setTextSize(fontSizeSp)
            editor.applyReaderAppearance(backgroundColorArgb)
            editor.isWordwrap = wordWrap
            editor.editable = editable
            applyEditorLanguage(editor, binding, fileType, tabWidth, indentWithTabs)
            editor.applyEditorInputSettings(
                autoIndent = autoIndent,
                autoClosePairs = autoClosePairs,
                tabWidth = tabWidth,
                indentWithTabs = indentWithTabs,
                optimizePasteIndentation = optimizePasteIndentation,
            )
            val commandTargetsDocument = command?.targetDocumentId?.let { it == documentId } ?: true
            if (command != null && commandTargetsDocument && binding.commandId != command.id) {
                binding.commandId = command.id
                handleEditorCommand(
                    editor = editor,
                    binding = binding,
                    command = command,
                    documentId = documentId,
                    onTextChanged = latestOnTextChanged.value,
                    onHistoryChanged = latestOnHistoryChanged.value,
                    onReplacementCompleted = latestOnReplacementCompleted.value,
                )
            }
        },
        onRelease = { editor ->
            // Compose 销毁 AndroidView 前先中断后台查找，避免 native/文本对象释放竞态。
            // @author long
            editor.searcher.stopSearch()
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

/**
 * 输入辅助直接作用于当前 Sora 实例，设置切换后无需关闭标签或重建整份正文。
 *
 * @author long
 */
private fun CodeEditor.applyEditorInputSettings(
    autoIndent: Boolean,
    autoClosePairs: Boolean,
    tabWidth: Int,
    indentWithTabs: Boolean,
    optimizePasteIndentation: Boolean,
) {
    val normalizedTabWidth = tabWidth.coerceIn(2, 8)
    props.autoIndent = autoIndent
    props.symbolPairAutoCompletion = autoClosePairs
    // Sora 内置 formatPastedText 会调用语言 Formatter；TextMate 当前没有格式化器，后续使用应用自己的缩进优化。 @author long
    props.formatPastedText = false
    setTabWidth(normalizedTabWidth)
    (this as? ReaderCodeEditor)?.optimizePasteIndentation = optimizePasteIndentation
}

private fun applyEditorLanguage(
    editor: CodeEditor,
    binding: EditorDocumentBinding,
    fileType: FileType,
    tabWidth: Int,
    indentWithTabs: Boolean,
    force: Boolean = false,
) {
    val normalizedTabWidth = tabWidth.coerceIn(2, 8)
    if (!force &&
        binding.languageFileType == fileType &&
        binding.languageTabWidth == normalizedTabWidth &&
        binding.languageIndentWithTabs == indentWithTabs
    ) {
        return
    }
    editor.setEditorLanguage(SyntaxRegistry.createLanguage(fileType, normalizedTabWidth, indentWithTabs))
    binding.languageFileType = fileType
    binding.languageTabWidth = normalizedTabWidth
    binding.languageIndentWithTabs = indentWithTabs
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
    editor.post {
        if (editor.isReleased) return@post
        editor.scrollTo(editor.scrollX, offset.coerceAtLeast(0))
    }
}

private fun handleEditorCommand(
    editor: CodeEditor,
    binding: EditorDocumentBinding,
    command: ReaderCommand,
    documentId: String,
    onTextChanged: (String) -> Unit,
    onHistoryChanged: (String, EditorHistoryState) -> Unit,
    onReplacementCompleted: (EditorReplacementResult) -> Unit,
) {
    when (command.type) {
        ReaderCommandType.UNDO -> {
            editor.post {
                // Compose 正在提交 AndroidView.update 时不能同步改正文，否则 Sora 的行布局可能处于重建中。 @author long
                if (editor.isReleased || !isCurrentEditorCommand(binding, command, documentId)) return@post
                editor.undo()
                editor.post {
                    if (editor.isReleased) return@post
                    reportHistoryState(editor, binding, onHistoryChanged)
                }
            }
        }
        ReaderCommandType.REDO -> {
            editor.post {
                if (editor.isReleased || !isCurrentEditorCommand(binding, command, documentId)) return@post
                editor.redo()
                editor.post {
                    if (editor.isReleased) return@post
                    reportHistoryState(editor, binding, onHistoryChanged)
                }
            }
        }
        ReaderCommandType.SELECT_LINE,
        ReaderCommandType.DELETE_LINE,
        ReaderCommandType.COPY,
        ReaderCommandType.CUT,
        ReaderCommandType.PASTE,
        ReaderCommandType.INDENT,
        ReaderCommandType.UNINDENT -> {
            editor.post {
                if (editor.isReleased || !isCurrentEditorCommand(binding, command, documentId)) return@post
                when (command.type) {
                    ReaderCommandType.SELECT_LINE -> selectCurrentEditorLine(editor)
                    ReaderCommandType.DELETE_LINE -> if (editor.editable) deleteCurrentEditorLine(editor)
                    ReaderCommandType.COPY -> editor.copyText()
                    ReaderCommandType.CUT -> if (editor.editable) editor.cutText()
                    ReaderCommandType.PASTE -> if (editor.editable) {
                        // 面板关闭时不依赖 IME 重新连接，确保用户点击粘贴后正文一定收到命令。 @author long
                        (editor as? ReaderCodeEditor)?.pasteClipboardText() ?: editor.pasteText()
                    }
                    ReaderCommandType.INDENT -> if (editor.editable) editor.indentLines(false)
                    ReaderCommandType.UNINDENT -> if (editor.editable) editor.unindentSelection()
                }
                editor.post {
                    if (editor.isReleased) return@post
                    reportHistoryState(editor, binding, onHistoryChanged)
                }
            }
        }
        ReaderCommandType.REPLACE_CURRENT -> {
            editor.post {
                if (editor.isReleased || !isCurrentEditorCommand(binding, command, documentId)) return@post
                val result = runCatching {
                    val line = command.line - 1
                    if (line !in 0 until editor.lineCount) {
                        throw IllegalStateException("当前匹配位置无效，请重新搜索")
                    }
                    val replacementText = TextReplacementEngine.replacementForMatch(
                        line = editor.text.getLineString(line),
                        start = command.column,
                        endExclusive = command.endColumnExclusive,
                        query = command.query,
                        replacement = command.replacement,
                        options = command.searchOptions,
                    )
                    // 精确替换只向 Compose 回传一次最终正文，避免 delete/insert 中间态触发两轮草稿搜索。 @author long
                    binding.suppressTextCallback = true
                    try {
                        editor.text.replace(
                            line,
                            command.column,
                            line,
                            command.endColumnExclusive,
                            replacementText,
                        )
                    } finally {
                        binding.suppressTextCallback = false
                    }
                    binding.renderedText = editor.text.toString()
                    onTextChanged(binding.renderedText)
                    editor.searcher.stopSearch()
                    1
                }
                reportReplacementResult(
                    editor = editor,
                    binding = binding,
                    command = command,
                    documentId = documentId,
                    replaceAll = false,
                    replacementCount = result.getOrDefault(0),
                    error = result.exceptionOrNull(),
                    onHistoryChanged = onHistoryChanged,
                    onReplacementCompleted = onReplacementCompleted,
                )
            }
        }
        ReaderCommandType.REPLACE_ALL -> {
            val sourceText = editor.text.toString()
            val sourceVersion = editor.text.documentVersion
            Thread({
                val computed = runCatching {
                    TextReplacementEngine.replaceAll(
                        text = sourceText,
                        query = command.query,
                        replacement = command.replacement,
                        options = command.searchOptions,
                    )
                }
                editor.post {
                    if (editor.isReleased || !isCurrentEditorCommand(binding, command, documentId)) return@post
                    val result = computed.getOrNull()
                    if (result == null) {
                        reportReplacementResult(
                            editor = editor,
                            binding = binding,
                            command = command,
                            documentId = documentId,
                            replaceAll = true,
                            replacementCount = 0,
                            error = computed.exceptionOrNull(),
                            onHistoryChanged = onHistoryChanged,
                            onReplacementCompleted = onReplacementCompleted,
                        )
                        return@post
                    }
                    if (editor.text.documentVersion != sourceVersion) {
                        reportReplacementResult(
                            editor = editor,
                            binding = binding,
                            command = command,
                            documentId = documentId,
                            replaceAll = true,
                            replacementCount = 0,
                            error = IllegalStateException("正文已发生变化，请重新执行全部替换"),
                            onHistoryChanged = onHistoryChanged,
                            onReplacementCompleted = onReplacementCompleted,
                        )
                        return@post
                    }
                    val applied = runCatching {
                        if (result.replacementCount > 0) {
                            // 全文替换在一个批次内提交，撤销时一次即可回到替换前正文。 @author long
                            binding.suppressTextCallback = true
                            editor.text.beginBatchEdit()
                            try {
                                editor.text.replace(0, editor.text.length, result.text)
                            } finally {
                                editor.text.endBatchEdit()
                                binding.suppressTextCallback = false
                            }
                            binding.renderedText = result.text
                            onTextChanged(result.text)
                        }
                    }
                    reportReplacementResult(
                        editor = editor,
                        binding = binding,
                        command = command,
                        documentId = documentId,
                        replaceAll = true,
                        replacementCount = if (applied.isSuccess) result.replacementCount else 0,
                        error = applied.exceptionOrNull(),
                        onHistoryChanged = onHistoryChanged,
                        onReplacementCompleted = onReplacementCompleted,
                    )
                }
            }, "reader-replace-all").start()
        }
        ReaderCommandType.GOTO_LINE -> {
            // 新文件 setText() 后 Sora 需要到下一帧才稳定 lineCount，否则全局搜索的首次跳转会被夹到第 1 行。
            editor.postDelayed({
                // 标签切换不会销毁同一个 View，旧命令必须核对文档和命令 id 后才能继续执行。 @author long
                if (editor.isReleased || !isCurrentEditorCommand(binding, command, documentId)) return@postDelayed
                val line = (command.line - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                editor.setSelection(line, 0, true)
                // Sora 的光标更新和滚动是两条路径，显式确保目标行可见才能稳定承接 Markdown 预览位置。 @author long
                editor.ensurePositionVisible(line, 0, true)
            }, 250)
        }
        ReaderCommandType.GOTO_SEARCH_MATCH -> {
            editor.postDelayed({
                if (editor.isReleased || !isCurrentEditorCommand(binding, command, documentId)) return@postDelayed
                val line = (command.line - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                val start = command.column.coerceIn(0, editor.text.getColumnCount(line))
                val end = command.endColumnExclusive.coerceIn(start, editor.text.getColumnCount(line))
                editor.setSelectionRegion(line, start, line, end, true)
            }, 180)
        }
        ReaderCommandType.SEARCH_FORWARD,
        ReaderCommandType.SEARCH_BACKWARD -> {
            // 源码搜索由 ReaderViewModel 的 Java Pattern 扫描并派发精确位置；
            // CodeEditor 不再启动 Sora 搜索线程，避免与文档切换产生竞态。
        }
        ReaderCommandType.CLEAR_SEARCH -> {
            editor.searcher.stopSearch()
        }
        ReaderCommandType.MARKDOWN_HEADING -> Unit
    }
}

private fun selectCurrentEditorLine(editor: CodeEditor) {
    val line = editor.cursor.left().line.coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
    editor.setSelectionRegion(line, 0, line, editor.text.getColumnCount(line), true)
}

/** 删除当前完整行但不改写剪贴板；末行需要连同前一个换行符删除，避免留下空壳行。 @author long */
private fun deleteCurrentEditorLine(editor: CodeEditor) {
    if (editor.lineCount <= 0) return
    val line = editor.cursor.left().line.coerceIn(0, editor.lineCount - 1)
    val lastLine = editor.lineCount - 1
    editor.text.beginBatchEdit()
    try {
        when {
            editor.lineCount == 1 -> editor.text.delete(0, 0, 0, editor.text.getColumnCount(0))
            line < lastLine -> editor.text.delete(line, 0, line + 1, 0)
            else -> editor.text.delete(
                line - 1,
                editor.text.getColumnCount(line - 1),
                line,
                editor.text.getColumnCount(line),
            )
        }
    } finally {
        editor.text.endBatchEdit()
    }
}

private fun reportReplacementResult(
    editor: CodeEditor,
    binding: EditorDocumentBinding,
    command: ReaderCommand,
    documentId: String,
    replaceAll: Boolean,
    replacementCount: Int,
    error: Throwable? = null,
    onHistoryChanged: (String, EditorHistoryState) -> Unit,
    onReplacementCompleted: (EditorReplacementResult) -> Unit,
) {
    if (editor.isReleased || !isCurrentEditorCommand(binding, command, documentId)) return
    reportHistoryState(editor, binding, onHistoryChanged)
    onReplacementCompleted(
        EditorReplacementResult(
            documentId = documentId,
            query = command.query,
            searchOptions = command.searchOptions,
            replacementCount = replacementCount,
            replaceAll = replaceAll,
            errorMessage = error?.message ?: error?.javaClass?.simpleName,
        ),
    )
}

/** 撤销栈变化比正文事件晚一个主线程队列，延后读取才能得到 Sora 的最终能力状态。 @author long */
private fun reportHistoryState(
    editor: CodeEditor,
    binding: EditorDocumentBinding,
    onHistoryChanged: (String, EditorHistoryState) -> Unit,
) {
    if (editor.isReleased) return
    val documentId = binding.documentId ?: return
    val state = EditorHistoryState(canUndo = editor.canUndo(), canRedo = editor.canRedo())
    if (binding.historyStates[documentId] == state) return
    binding.historyStates[documentId] = state
    onHistoryChanged(documentId, state)
}

private fun isCurrentEditorCommand(
    binding: EditorDocumentBinding,
    command: ReaderCommand,
    documentId: String,
): Boolean = binding.documentId == documentId &&
    binding.commandId == command.id &&
    (command.targetDocumentId == null || command.targetDocumentId == documentId)
