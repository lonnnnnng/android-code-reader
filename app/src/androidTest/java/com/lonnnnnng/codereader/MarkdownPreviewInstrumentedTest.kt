package com.lonnnnnng.codereader

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** @author long */
class MarkdownPreviewInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun commonSyntaxCodeMathAndMermaidAreRendered() {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project-list").performScrollToNode(hasText("README.md"))
        composeRule.onNodeWithText("README.md").performClick()

        val webView = waitForWebView()
        waitForMarkdown(webView)

        assertTrue("Java 代码块没有产生高亮 token", domCount(webView, "pre code .hljs-keyword") > 0)
        assertTrue("没有渲染行内或块级数学公式", domCount(webView, ".katex") >= 4)
        assertTrue("多行块级公式没有完整渲染", domCount(webView, ".math-block .katex-display") >= 2)
        assertTrue("Mermaid 没有生成 SVG 流程图", domCount(webView, ".mermaid svg") == 1)
        assertTrue("任务列表没有渲染", domCount(webView, ".task-list-item") >= 5)
        assertTrue("表格没有渲染", domCount(webView, "table") == 1)
        assertTrue("脚注没有渲染", domCount(webView, ".footnotes") == 1)
        assertTrue("Markdown 标题没有生成目录锚点", domCount(webView, "[id^='heading-']") >= 6)
        assertTrue("代码块没有生成复制按钮", domCount(webView, "pre .copy-code") >= 3)
    }

    private fun waitForWebView(): WebView {
        var result: WebView? = null
        composeRule.waitUntil(timeoutMillis = 10_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                result = findWebView(composeRule.activity.findViewById(android.R.id.content))
            }
            result != null
        }
        return requireNotNull(result)
    }

    private fun waitForMarkdown(webView: WebView) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        var state = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            state = evaluate(webView, "document.documentElement.dataset.markdownReady || ''")
            if (state == "\"true\"") return
            if (state == "\"error\"") break
            SystemClock.sleep(100)
        }
        assertEquals("Markdown WebView 未完成渲染", "\"true\"", state)
    }

    private fun domCount(webView: WebView, selector: String): Int {
        return evaluate(webView, "document.querySelectorAll(${selector.jsQuoted()}).length").toInt()
    }

    private fun evaluate(webView: WebView, script: String): String {
        val latch = CountDownLatch(1)
        var result = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(script) { value ->
                result = value
                latch.countDown()
            }
        }
        assertTrue("等待 WebView JavaScript 返回超时", latch.await(10, TimeUnit.SECONDS))
        return result
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view !is ViewGroup) return null
        repeat(view.childCount) { index ->
            findWebView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun String.jsQuoted(): String = "'${replace("\\", "\\\\").replace("'", "\\'")}'"
}
