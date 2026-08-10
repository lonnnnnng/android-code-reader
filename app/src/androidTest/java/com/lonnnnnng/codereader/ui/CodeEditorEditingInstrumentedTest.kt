package com.lonnnnnng.codereader.ui

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.domain.TextSearchOptions
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 编辑行为通过真实 Sora 组件验收，确保 Compose 状态和原生撤销栈不会相互覆盖。 @author long */
class CodeEditorEditingInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun indentationSettingsApplyWithoutReopeningDocument() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "indentation.kt"
        val initialText = "fun main() {"
        val text = mutableStateOf(initialText)
        val autoIndent = mutableStateOf(true)
        val indentWithTabs = mutableStateOf(false)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                autoIndent = autoIndent.value,
                autoClosePairs = true,
                tabWidth = 2,
                indentWithTabs = indentWithTabs.value,
                optimizePasteIndentation = true,
                command = null,
                onTextChanged = { text.value = it },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { editor = rootView?.let(::findCodeEditor) }
            editor != null
        }

        composeRule.runOnIdle {
            editor!!.setSelection(0, initialText.length)
            editor!!.commitText("\n")
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value.startsWith("$initialText\n") }
        assertEquals("$initialText\n  ", text.value)

        composeRule.runOnIdle {
            text.value = initialText
            indentWithTabs.value = true
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { editor!!.text.toString() == initialText }
        composeRule.runOnIdle {
            editor!!.setSelection(0, initialText.length)
            editor!!.commitText("\n")
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value.startsWith("$initialText\n") }
        assertEquals("$initialText\n\t", text.value)

        composeRule.runOnIdle {
            text.value = initialText
            autoIndent.value = false
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { editor!!.text.toString() == initialText }
        composeRule.runOnIdle {
            editor!!.setSelection(0, initialText.length)
            editor!!.commitText("\n")
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value.startsWith("$initialText\n") }
        assertEquals("$initialText\n", text.value)
    }

    @Test
    fun pairCompletionCanBeTurnedOffWithoutReopeningDocument() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "pairs.kt"
        val text = mutableStateOf("")
        val autoClosePairs = mutableStateOf(true)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                autoClosePairs = autoClosePairs.value,
                command = null,
                onTextChanged = { text.value = it },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { editor = rootView?.let(::findCodeEditor) }
            editor != null
        }
        composeRule.runOnIdle { editor!!.commitText("(") }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value.isNotEmpty() }
        assertEquals("()", text.value)

        composeRule.runOnIdle {
            text.value = ""
            autoClosePairs.value = false
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { editor!!.text.toString().isEmpty() }
        composeRule.runOnIdle { editor!!.commitText("(") }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value.isNotEmpty() }
        assertEquals("(", text.value)
    }

    @Test
    fun multilinePasteKeepsRelativeIndentationAtCurrentLevel() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "paste.kt"
        val initialText = "fun main() {\n    \n}"
        val pastedText = "        if (ready) {\n            run()\n        }"
        val expectedText = "fun main() {\n    if (ready) {\n        run()\n    }\n}"
        val text = mutableStateOf(initialText)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                tabWidth = 4,
                indentWithTabs = false,
                optimizePasteIndentation = true,
                command = null,
                onTextChanged = { text.value = it },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { editor = rootView?.let(::findCodeEditor) }
            editor != null
        }
        composeRule.runOnIdle {
            editor!!.setSelection(1, 4)
            editor!!.pasteText(pastedText)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value != initialText }
        assertEquals(expectedText, text.value)
    }

    @Test
    fun lineCommandsSelectCopyDeleteAndRemainUndoable() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "line-actions.kt"
        val initialText = "first\nsecond\nthird"
        val text = mutableStateOf(initialText)
        val history = mutableStateOf(EditorHistoryState())
        val command = mutableStateOf<ReaderCommand?>(null)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                command = command.value,
                onTextChanged = { text.value = it },
                onHistoryChanged = { id, value -> if (id == documentId) history.value = value },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { editor = rootView?.let(::findCodeEditor) }
            editor != null
        }
        composeRule.runOnIdle {
            editor!!.setSelection(1, 3)
            command.value = ReaderCommand(10, ReaderCommandType.SELECT_LINE, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            editor!!.cursor.isSelected && editor!!.cursor.left().line == 1 && editor!!.cursor.right().column == 6
        }

        composeRule.runOnIdle {
            command.value = ReaderCommand(11, ReaderCommandType.COPY, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            editor!!.clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() == "second"
        }

        composeRule.runOnIdle {
            command.value = ReaderCommand(12, ReaderCommandType.DELETE_LINE, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == "first\nthird" && history.value.canUndo }

        composeRule.runOnIdle {
            command.value = ReaderCommand(13, ReaderCommandType.UNDO, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == initialText }
        assertEquals(initialText, text.value)
    }

    @Test
    fun clipboardCommandsCutPasteAndUndoAsIndependentEdits() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "clipboard-actions.kt"
        val initialText = "first\nsecond\nthird"
        val cutText = "first\n\nthird"
        val text = mutableStateOf(initialText)
        val history = mutableStateOf(EditorHistoryState())
        val command = mutableStateOf<ReaderCommand?>(null)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                command = command.value,
                onTextChanged = { text.value = it },
                onHistoryChanged = { id, value -> if (id == documentId) history.value = value },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { editor = rootView?.let(::findCodeEditor) }
            editor != null
        }
        composeRule.runOnIdle {
            editor!!.setSelectionRegion(1, 0, 1, 6, true)
            command.value = ReaderCommand(20, ReaderCommandType.CUT, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            text.value == cutText &&
                editor!!.clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() == "second"
        }

        composeRule.runOnIdle {
            editor!!.setSelection(1, 0)
            command.value = ReaderCommand(21, ReaderCommandType.PASTE, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == initialText && history.value.canUndo }

        composeRule.runOnIdle {
            command.value = ReaderCommand(22, ReaderCommandType.UNDO, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == cutText }
        composeRule.runOnIdle {
            command.value = ReaderCommand(23, ReaderCommandType.UNDO, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == initialText }
    }

    @Test
    fun indentCommandsAdjustSelectedLinesAndRemainUndoable() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "indent-actions.kt"
        val initialText = "alpha\nbeta"
        val indentedText = "  alpha\n  beta"
        val text = mutableStateOf(initialText)
        val history = mutableStateOf(EditorHistoryState())
        val command = mutableStateOf<ReaderCommand?>(null)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                tabWidth = 2,
                indentWithTabs = false,
                command = command.value,
                onTextChanged = { text.value = it },
                onHistoryChanged = { id, value -> if (id == documentId) history.value = value },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { editor = rootView?.let(::findCodeEditor) }
            editor != null
        }
        composeRule.runOnIdle {
            editor!!.setSelection(0, 2)
            command.value = ReaderCommand(29, ReaderCommandType.INDENT, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == "  alpha\nbeta" }
        composeRule.runOnIdle {
            command.value = ReaderCommand(30, ReaderCommandType.UNDO, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == initialText }

        composeRule.runOnIdle {
            editor!!.setSelectionRegion(0, 0, 1, 4, true)
            command.value = ReaderCommand(31, ReaderCommandType.INDENT, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == indentedText && history.value.canUndo }

        composeRule.runOnIdle {
            command.value = ReaderCommand(32, ReaderCommandType.UNINDENT, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == initialText }

        composeRule.runOnIdle {
            command.value = ReaderCommand(33, ReaderCommandType.UNDO, targetDocumentId = documentId)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == indentedText }
        assertEquals(indentedText, text.value)
    }

    @Test
    fun undoRedoUpdatesDraftAndAvailability() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "first.kt"
        val initialText = "val value = 1"
        val editedText = "$initialText // changed"
        val text = mutableStateOf(initialText)
        val history = mutableStateOf(EditorHistoryState())
        val command = mutableStateOf<ReaderCommand?>(null)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                command = command.value,
                onTextChanged = { text.value = it },
                onHistoryChanged = { id, value -> if (id == documentId) history.value = value },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { editor = rootView?.let(::findCodeEditor) }
            editor != null
        }
        composeRule.runOnIdle {
            editor!!.setSelection(0, initialText.length)
            editor!!.commitText(" // changed")
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            text.value == editedText && history.value.canUndo
        }

        composeRule.runOnIdle {
            command.value = ReaderCommand(
                id = 1,
                type = ReaderCommandType.UNDO,
                targetDocumentId = documentId,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            text.value == initialText && history.value.canRedo
        }
        assertEquals(initialText, editor?.text.toString())

        composeRule.runOnIdle {
            command.value = ReaderCommand(
                id = 2,
                type = ReaderCommandType.REDO,
                targetDocumentId = documentId,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == editedText && history.value.canUndo }
        assertEquals(editedText, editor?.text.toString())
        assertTrue(history.value.canUndo)

        val reloadedText = "val value = 2"
        composeRule.runOnIdle { text.value = reloadedText }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            editor?.text.toString() == reloadedText && !history.value.canUndo && !history.value.canRedo
        }
        assertFalse(history.value.canUndo)
        assertFalse(history.value.canRedo)
    }

    @Test
    fun regexReplaceAllSupportsCaptureGroupsAndSingleUndo() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "replace.kt"
        val initialText = "val user1 = \"user1\"\nval user2 = \"user2\""
        val replacedText = "val account1 = \"account1\"\nval account2 = \"account2\""
        val text = mutableStateOf(initialText)
        val history = mutableStateOf(EditorHistoryState())
        val replacementResult = mutableStateOf<EditorReplacementResult?>(null)
        val command = mutableStateOf<ReaderCommand?>(null)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                command = command.value,
                onTextChanged = { text.value = it },
                onHistoryChanged = { id, value -> if (id == documentId) history.value = value },
                onReplacementCompleted = { replacementResult.value = it },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { rootView?.let(::findCodeEditor) != null }
        }
        composeRule.runOnIdle {
            command.value = ReaderCommand(
                id = 3,
                type = ReaderCommandType.REPLACE_ALL,
                query = "user(\\d)",
                replacement = "account$1",
                searchOptions = TextSearchOptions(regularExpression = true),
                targetDocumentId = documentId,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            text.value == replacedText && replacementResult.value?.replacementCount == 4 && history.value.canUndo
        }

        composeRule.runOnIdle {
            command.value = ReaderCommand(
                id = 4,
                type = ReaderCommandType.UNDO,
                targetDocumentId = documentId,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == initialText }
        assertEquals(initialText, text.value)
    }

    @Test
    fun regexReplaceCurrentUsesExactMatchAndSupportsSingleUndo() {
        SyntaxRegistry.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        val documentId = "replace-current.kt"
        val initialText = "val user42 = user42"
        val replacedText = "val account42 = user42"
        val text = mutableStateOf(initialText)
        val history = mutableStateOf(EditorHistoryState())
        val replacementResult = mutableStateOf<EditorReplacementResult?>(null)
        val command = mutableStateOf<ReaderCommand?>(null)
        var rootView: View? = null

        composeRule.setContent {
            val localView = LocalView.current
            SideEffect { rootView = localView }
            CodeEditorView(
                documentId = documentId,
                text = text.value,
                fileType = FileType.KOTLIN,
                editable = true,
                fontSizeSp = 14f,
                backgroundColorArgb = 0xFFFFFFFF.toInt(),
                wordWrap = false,
                command = command.value,
                onTextChanged = { text.value = it },
                onHistoryChanged = { id, value -> if (id == documentId) history.value = value },
                onReplacementCompleted = { replacementResult.value = it },
                onReadingPositionChanged = { _, _ -> },
                onCursorPositionChanged = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.runOnIdle { rootView?.let(::findCodeEditor) != null }
        }
        composeRule.runOnIdle {
            command.value = ReaderCommand(
                id = 5,
                type = ReaderCommandType.REPLACE_CURRENT,
                query = "user(\\d+)",
                line = 1,
                column = 4,
                endColumnExclusive = 10,
                searchOptions = TextSearchOptions(regularExpression = true),
                replacement = "account$1",
                targetDocumentId = documentId,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            text.value == replacedText && replacementResult.value?.replacementCount == 1 && history.value.canUndo
        }

        composeRule.runOnIdle {
            command.value = ReaderCommand(
                id = 6,
                type = ReaderCommandType.UNDO,
                targetDocumentId = documentId,
            )
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { text.value == initialText }
        assertEquals(initialText, text.value)
    }

    private fun findCodeEditor(view: View): CodeEditor? {
        if (view is CodeEditor) return view
        if (view !is ViewGroup) return null
        repeat(view.childCount) { index ->
            findCodeEditor(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
