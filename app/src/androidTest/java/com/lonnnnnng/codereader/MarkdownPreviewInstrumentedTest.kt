package com.lonnnnnng.codereader

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
        openMarkdownDocument()

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

    @Test
    fun yamlFrontMatterIsRenderedAsCollapsedHighlightedSection() {
        openMarkdownDocument()

        val webView = waitForWebView()
        waitForMarkdown(webView)

        assertEquals(
            "Front Matter 应使用可展开的 details 语义",
            "\"DETAILS\"",
            evaluate(webView, "document.querySelector('.front-matter')?.tagName || ''"),
        )
        assertEquals(
            "Front Matter 默认应折叠，避免挤占手机首屏",
            "false",
            evaluate(webView, "document.querySelector('.front-matter')?.open === true"),
        )
        assertTrue("Front Matter 没有显示标题", domCount(webView, ".front-matter > summary") == 1)
        assertTrue("Front Matter YAML 没有产生属性高亮", domCount(webView, ".front-matter .hljs-attr") > 0)
        assertEquals(
            "Front Matter 展开后必须保留原始 YAML 内容",
            "true",
            evaluate(webView, "document.querySelector('.front-matter')?.textContent.includes('category: Android 阅读器') === true"),
        )
    }

    @Test
    fun localImageCanBeZoomedAndRelativeAttachmentCanBeOpened() {
        openMarkdownDocument()

        val webView = waitForWebView()
        waitForMarkdown(webView)
        assertEquals(
            "本地 SVG 图片应从当前项目加载完成",
            "true",
            evaluate(
                webView,
                "document.querySelector('#content img')?.complete === true && " +
                    "document.querySelector('#content img')?.naturalWidth > 0",
            ),
        )

        evaluate(webView, "document.querySelector('#content img')?.click(); true")
        assertEquals(
            "点击图片后应打开沉浸式放大层",
            "true",
            evaluate(webView, "document.querySelector('.image-lightbox')?.classList.contains('open') === true"),
        )
        evaluate(webView, "document.querySelector('.image-lightbox')?.click(); true")
        assertEquals(
            "再次点击放大层应关闭图片预览",
            "false",
            evaluate(webView, "document.querySelector('.image-lightbox')?.classList.contains('open') === true"),
        )

        evaluate(
            webView,
            "Array.from(document.querySelectorAll('a')).find(a => a.textContent.includes('打开同项目附件'))?.click(); true",
        )
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("快速阅读说明.txt").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "附件打开后标题栏或标签页应显示文件名",
            composeRule.onAllNodesWithText("快速阅读说明.txt").fetchSemanticsNodes().isNotEmpty(),
        )
        val attachmentEditor = waitForCodeEditor()
        assertTrue(
            "附件正文应进入 Sora 阅读内核",
            attachmentEditor.text.toString().contains("灵阅会根据 Markdown 文件所在目录解析相对路径。"),
        )
    }

    @Test
    fun switchingMarkdownModesKeepsNativeReaderViewsAlive() {
        openMarkdownDocument()
        val webViewBefore = waitForWebView()
        waitForMarkdown(webViewBefore)

        composeRule.onNodeWithContentDescription("查看源码").performClick()
        val editorBefore = waitForCodeEditor()
        composeRule.onNodeWithContentDescription("预览 Markdown").performClick()

        val webViewAfter = waitForWebView()
        // 源码和预览切换只改变可见层，保留 WebView 才能保住预览滚动位置与渲染上下文。
        assertSame("切换 Markdown 模式不应销毁并重建 WebView", webViewBefore, webViewAfter)

        composeRule.onNodeWithContentDescription("查看源码").performClick()
        val editorAfter = waitForCodeEditor()
        assertSame("切换 Markdown 模式不应销毁并重建 Sora Editor", editorBefore, editorAfter)
    }

    @Test
    fun switchingFromSourceTabBackToMarkdownKeepsPreviewWebViewAlive() {
        openSourceDocument("app.js")
        openMarkdownDocumentFromHome()

        val webViewBefore = waitForWebView()
        waitForMarkdown(webViewBefore)
        composeRule.onNodeWithText("app.js").performClick()
        composeRule.onNodeWithText("Markdown示例.md").performClick()

        val webViewAfter = waitForWebView()
        assertSame("从源码标签切回 Markdown 不应销毁并重建 WebView", webViewBefore, webViewAfter)
        assertEquals(
            "切回 Markdown 时已渲染内容应立即可用",
            "\"true\"",
            evaluate(webViewAfter, "document.documentElement.dataset.markdownReady || ''"),
        )
    }

    @Test
    fun switchingBetweenMarkdownTabsReusesPreviewWebView() {
        openMarkdownDocument()
        val webViewBefore = waitForWebView()
        waitForMarkdown(webViewBefore)

        composeRule.onNodeWithContentDescription("快速切换文件").performClick()
        composeRule.onNodeWithContentDescription("打开 demo/README.md").performClick()
        val webViewAfterOpen = waitForWebView()
        waitForMarkdown(webViewAfterOpen)
        assertSame("打开第二个 Markdown 标签不应销毁 WebView", webViewBefore, webViewAfterOpen)

        assertEquals(
            "第二个 Markdown 标签打开后应完成渲染",
            "\"true\"",
            evaluate(webViewAfterOpen, "document.documentElement.dataset.markdownReady || ''"),
        )
    }

    private fun openMarkdownDocument() {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project-list").performScrollToNode(hasText("README.md"))
        composeRule.onNodeWithText("README.md").performClick()
    }

    private fun openSourceDocument(name: String) {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project-list").performScrollToNode(hasText(name))
        composeRule.onNodeWithText(name).performClick()
    }

    private fun openMarkdownDocumentFromHome() {
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("Markdown 功能示例").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Markdown示例.md").performClick()
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

    private fun waitForCodeEditor(): CodeEditor {
        var result: CodeEditor? = null
        composeRule.waitUntil(timeoutMillis = 10_000) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                result = findCodeEditor(composeRule.activity.findViewById(android.R.id.content))
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

    private fun findCodeEditor(view: View): CodeEditor? {
        if (view is CodeEditor) return view
        if (view !is ViewGroup) return null
        repeat(view.childCount) { index ->
            findCodeEditor(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun String.jsQuoted(): String = "'${replace("\\", "\\\\").replace("'", "\\'")}'"
}
