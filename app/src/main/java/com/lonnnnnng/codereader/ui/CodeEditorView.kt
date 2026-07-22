package com.lonnnnnng.codereader.ui

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.model.ReaderFontFamily
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

private class EditorDocumentBinding {
    var documentId: String? = null
    var renderedText: String = ""
    var suppressTextCallback: Boolean = false
    var commandId: Long? = null
    var searchQuery: String? = null
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
    fontFamily: ReaderFontFamily,
    backgroundColorArgb: Int,
    wordWrap: Boolean,
    command: ReaderCommand?,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val binding = remember { EditorDocumentBinding() }
    val latestOnTextChanged = androidx.compose.runtime.rememberUpdatedState(onTextChanged)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                setTextSize(fontSizeSp)
                isWordwrap = wordWrap
                isLineNumberEnabled = true
                colorScheme = SyntaxRegistry.createColorScheme()
                applyReaderAppearance(fontFamily, backgroundColorArgb)
                searcher.setEnsureOccurrenceVisible(true)
                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    if (!binding.suppressTextCallback &&
                        (event.action == ContentChangeEvent.ACTION_INSERT || event.action == ContentChangeEvent.ACTION_DELETE)
                    ) {
                        binding.renderedText = this.text.toString()
                        latestOnTextChanged.value(binding.renderedText)
                    }
                }
            }
        },
        update = { editor ->
            if (binding.documentId != documentId) {
                editor.setEditorLanguage(SyntaxRegistry.createLanguage(fileType) ?: EmptyLanguage())
                replaceEditorText(editor, binding, text)
                binding.documentId = documentId
                binding.searchQuery = null
            } else if (binding.renderedText != text && editor.text.toString() != text) {
                // 大文件追加和标签页恢复属于外部状态变化，不能反向标记为用户编辑。
                replaceEditorText(editor, binding, text)
            }
            editor.setTextSize(fontSizeSp)
            editor.applyReaderAppearance(fontFamily, backgroundColorArgb)
            editor.isWordwrap = wordWrap
            editor.editable = editable
            if (command != null && binding.commandId != command.id) {
                handleEditorCommand(editor, binding, command)
                binding.commandId = command.id
            }
        },
        onRelease = { editor -> editor.release() },
    )
}

private fun CodeEditor.applyReaderAppearance(fontFamily: ReaderFontFamily, backgroundColorArgb: Int) {
    // 字体和背景必须直接落到 Sora 视图；只修改 Compose 外层不会影响编辑器自己的绘制画布。
    setTypefaceText(
        when (fontFamily) {
            ReaderFontFamily.SYSTEM_SANS -> Typeface.DEFAULT
            ReaderFontFamily.MONOSPACE -> Typeface.MONOSPACE
            ReaderFontFamily.SERIF -> Typeface.SERIF
        },
    )
    colorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColorArgb)
    colorScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, backgroundColorArgb)
}

private fun replaceEditorText(editor: CodeEditor, binding: EditorDocumentBinding, text: String) {
    binding.suppressTextCallback = true
    editor.setText(text)
    binding.renderedText = text
    binding.suppressTextCallback = false
}

private fun handleEditorCommand(editor: CodeEditor, binding: EditorDocumentBinding, command: ReaderCommand) {
    when (command.type) {
        ReaderCommandType.GOTO_LINE -> {
            // 新文件 setText() 后 Sora 需要到下一帧才稳定 lineCount，否则全局搜索的首次跳转会被夹到第 1 行。
            editor.postDelayed({
                val line = (command.line - 1).coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                editor.setSelection(line, 0, true)
            }, 250)
        }
        ReaderCommandType.SEARCH_FORWARD,
        ReaderCommandType.SEARCH_BACKWARD -> {
            if (binding.searchQuery != command.query) {
                editor.searcher.search(command.query, EditorSearcher.SearchOptions(true, false))
                binding.searchQuery = command.query
                editor.postDelayed({
                    if (command.type == ReaderCommandType.SEARCH_FORWARD) editor.searcher.gotoNext()
                    else editor.searcher.gotoPrevious()
                }, 120)
            } else if (command.type == ReaderCommandType.SEARCH_FORWARD) {
                editor.searcher.gotoNext()
            } else {
                editor.searcher.gotoPrevious()
            }
        }
        ReaderCommandType.MARKDOWN_HEADING -> Unit
    }
}
