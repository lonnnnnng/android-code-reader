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
