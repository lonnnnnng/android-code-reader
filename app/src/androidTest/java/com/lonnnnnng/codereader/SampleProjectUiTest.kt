package com.lonnnnnng.codereader

import android.content.ClipboardManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.ui.ReaderDimens
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
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
        // 文件读取在 IO 协程中完成，等待阅读页真正发布后再检查标题，避免把调度时序误判为功能失败。 @author long
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("C#", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("C#", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("只读").assertIsDisplayed()
        assertCompactHeader("reader-header")
    }

    @Test
    fun markdownSearchResultOpensSourceAndScrollsToMatchedLine() {
        val expectedLine = InstrumentationRegistry.getInstrumentation().targetContext.assets
            .open("samples/README.md")
            .bufferedReader()
            .useLines { lines -> lines.indexOfFirst { it == "## Mermaid 流程图" } + 1 }
        check(expectedLine > 0) { "测试样例缺少 Mermaid 标题" }
        val resultTitle = "README.md · 第 $expectedLine 行"

        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("项目全局搜索").performClick()
        composeRule.onNodeWithText("搜索项目内容").performTextInput("Mermaid")
        composeRule.onNodeWithText("搜索项目内容").performImeAction()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(resultTitle).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(resultTitle).performClick()
        composeRule.onNodeWithContentDescription("预览 Markdown").assertIsDisplayed()

        val expectedEditorLine = expectedLine - 1
        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 10_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                editor = findCodeEditor(composeRule.activity.findViewById(android.R.id.content))
            }
            editor?.let { it.firstVisibleLine <= expectedEditorLine && it.lastVisibleLine >= expectedEditorLine } == true
        }
        assertNotNull("没有找到 Sora 编辑器", editor)
        assertTrue(
            "第 $expectedLine 行没有进入可见区域",
            editor!!.firstVisibleLine <= expectedEditorLine && editor!!.lastVisibleLine >= expectedEditorLine,
        )
    }

    @Test
    fun fileSearchJumpsToExactLineAndColumn() {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project-list").performScrollToNode(hasText("Program.cs"))
        composeRule.onNodeWithText("Program.cs").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("C#", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("文件内搜索").performClick()
        composeRule.onNodeWithTag("file-search-input").performTextInput("Console")
        composeRule.onNodeWithTag("file-search-input").performImeAction()

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 10_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                editor = findCodeEditor(composeRule.activity.findViewById(android.R.id.content))
            }
            editor?.cursor?.leftLine == 6 && editor?.cursor?.leftColumn == 33
        }
        assertEquals("搜索应定位到 Main 方法所在行", 6, editor?.cursor?.leftLine)
        assertEquals("搜索应定位到 Console 的真实列位置", 33, editor?.cursor?.leftColumn)
    }

    @Test
    fun soraSelectionCanCreateAndOpenLineBookmark() {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project-list").performScrollToNode(hasText("Program.cs"))
        composeRule.onNodeWithText("Program.cs").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("C#", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        var editor: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 10_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                editor = findCodeEditor(composeRule.activity.findViewById(android.R.id.content))
                editor?.setSelection(4, 0)
            }
            editor != null
        }
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("添加第 5 行书签").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("移除第 5 行书签").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("移除第 5 行书签").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("移除第 5 行书签").performClick()
            composeRule.onNodeWithContentDescription("更多").performClick()
        }
        composeRule.onNodeWithText("添加第 5 行书签").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("书签列表").performClick()

        composeRule.onNodeWithText("当前文件行书签").assertIsDisplayed()
        composeRule.onNodeWithTag("line-bookmark-5").assertIsDisplayed().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { editor?.cursor?.leftLine == 4 }
        assertEquals(4, editor?.cursor?.leftLine)

        composeRule.onNodeWithContentDescription("更多").performClick()
        // 菜单文字会被 DropdownMenuItem 合并语义；稳定标签才是可持续点击的产品测试边界。 @author long
        composeRule.onNodeWithTag("toggle-line-bookmark").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("添加第 5 行书签").assertIsDisplayed()
        if (composeRule.onAllNodesWithText("取消文件书签").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("toggle-file-bookmark").performClick()
            composeRule.onNodeWithContentDescription("更多").performClick()
        }
        composeRule.onNodeWithTag("toggle-file-bookmark").performClick()
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("书签列表").performClick()
        composeRule.onNodeWithText("文件书签").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("移除 Program.cs 文件书签").performClick()
        // 书签属于持久化阅读状态；本用例只验证自己创建的 Program.cs 书签被移除，不应假设其他文件没有书签。 @author long
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("打开文件书签 Program.cs")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    fun readerCanCopyFullPathAndLocateCurrentFileInProject() {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project-list").performScrollToNode(hasText("Program.cs"))
        composeRule.onNodeWithText("Program.cs").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("C#", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("复制完整路径").performClick()
        val clipboard = composeRule.activity.getSystemService(ClipboardManager::class.java)
        assertTrue(clipboard.primaryClip?.getItemAt(0)?.text?.contains("Program.cs") == true)

        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("在项目中定位").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Program.cs").assertIsDisplayed()
    }

    private fun findCodeEditor(view: View): CodeEditor? {
        if (view is CodeEditor) return view
        if (view !is ViewGroup) return null
        repeat(view.childCount) { index ->
            findCodeEditor(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun assertCompactHeader(tag: String) {
        val actualHeight = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
        val expectedHeight = composeRule.activity.resources.displayMetrics.density * ReaderDimens.topBarHeight.value
        assertEquals("阅读页标题栏应跟随统一紧凑高度", expectedHeight, actualHeight, 1.5f)
    }
}
