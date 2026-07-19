package com.lonnnnnng.codereader

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** @author long */
class SampleProjectUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun openSampleProjectAndReadCSharpFileInReadOnlyMode() {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project-list").performScrollToNode(hasText("Program.cs"))
        composeRule.onNodeWithText("Program.cs").performClick()
        composeRule.onNodeWithText("C#").assertIsDisplayed()
        composeRule.onNodeWithText("只读").assertIsDisplayed()
    }

    @Test
    fun markdownSearchResultOpensSourceAndScrollsToMatchedLine() {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("项目全局搜索").performClick()
        composeRule.onNodeWithText("搜索项目内容").performTextInput("Mermaid")
        composeRule.onNodeWithContentDescription("开始搜索").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("README.md:120").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("README.md:120").performClick()
        composeRule.onNodeWithContentDescription("预览 Markdown").assertIsDisplayed()

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 10_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                editor = findCodeEditor(composeRule.activity.findViewById(android.R.id.content))
            }
            editor?.let { it.firstVisibleLine <= 119 && it.lastVisibleLine >= 119 } == true
        }
        assertNotNull("没有找到 Sora 编辑器", editor)
        assertTrue("第 120 行没有进入可见区域", editor!!.firstVisibleLine <= 119 && editor!!.lastVisibleLine >= 119)
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
