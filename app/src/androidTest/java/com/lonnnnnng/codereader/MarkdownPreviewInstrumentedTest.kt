package com.lonnnnnng.codereader

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.ui.ReaderViewModel
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
    fun failedMediaMathAndDiagramKeepContentVisibleAndCanReturnToSource() {
        openMarkdownDocument("RenderFallback.md")

        val webView = waitForWebView()
        waitForMarkdown(webView)
        assertTrue("缺失图片应显示局部回退说明", domCount(webView, ".image-fallback") == 1)
        val renderedContent = evaluate(webView, "document.getElementById('content')?.innerHTML || ''")
        assertTrue(
            "非法公式应显示局部回退说明，实际 DOM：$renderedContent",
            domCount(webView, ".math-fallback") == 2,
        )
        assertTrue("非法流程图应显示局部回退说明", domCount(webView, ".diagram-fallback") == 1)
        assertEquals(
            "单个语法失败不能清空其余正文",
            "true",
            evaluate(webView, "document.body.textContent.includes('即使图片、公式或流程图失败') === true"),
        )

        evaluate(webView, "document.querySelector('.render-source-action')?.click(); true")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("预览 Markdown").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "回退按钮应切换到 Markdown 源码",
            composeRule.onAllNodesWithContentDescription("预览 Markdown").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            "源码视图应保留原始 Mermaid 内容",
            waitForCodeEditor().text.toString().contains("flowchart TD"),
        )
    }

    @Test
    fun switchingMarkdownModesKeepsNativeReaderViewsAlive() {
        openMarkdownDocument()
        val webViewBefore = waitForWebView()
        waitForMarkdown(webViewBefore)

        composeRule.onNodeWithContentDescription("展开 Markdown 目录").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("markdown-outline-list").fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithContentDescription("查看源码").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("预览 Markdown").fetchSemanticsNodes().size == 1 &&
                composeRule.onAllNodesWithTag("markdown-outline-list").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(
            "源码模式不应保留 Markdown 目录入口",
            composeRule.onAllNodesWithContentDescription("展开 Markdown 目录").fetchSemanticsNodes().isEmpty(),
        )
        val editorBefore = waitForCodeEditor()
        composeRule.onNodeWithContentDescription("预览 Markdown").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("展开 Markdown 目录").fetchSemanticsNodes().size == 1
        }
        assertTrue(
            "返回预览后目录应恢复折叠状态",
            composeRule.onAllNodesWithTag("markdown-outline-list").fetchSemanticsNodes().isEmpty(),
        )

        val webViewAfter = waitForWebView()
        // 源码和预览切换只改变可见层，保留 WebView 才能保住预览滚动位置与渲染上下文。
        assertSame("切换 Markdown 模式不应销毁并重建 WebView", webViewBefore, webViewAfter)

        composeRule.onNodeWithContentDescription("查看源码").performClick()
        val editorAfter = waitForCodeEditor()
        assertSame("切换 Markdown 模式不应销毁并重建 Sora Editor", editorBefore, editorAfter)
    }

    @Test
    fun sourceAndPreviewKeepTheSameReadingPosition() {
        val sourceMathLine = 107
        val previewCodeLine = 63
        val semanticBlockTolerance = 6
        openMarkdownDocument()
        val webView = waitForWebView()
        waitForMarkdown(webView)

        composeRule.onNodeWithContentDescription("查看源码").performClick()
        val editor = waitForCodeEditor()
        SystemClock.sleep(350)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            editor.setSelection(sourceMathLine - 1, 0)
            editor.ensurePositionVisible(sourceMathLine - 1, 0, true)
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            editor.firstVisibleLine <= sourceMathLine - 1 && editor.lastVisibleLine >= sourceMathLine - 1
        }
        val viewModel = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        val documentId = requireNotNull(viewModel.state.value.activeTabId)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // 程序化滚动不会产生用户 ScrollEvent，直接调用同一回调以验证两个阅读内核的同步契约。 @author long
            viewModel.updateReadingPosition(documentId, sourceMathLine)
            viewModel.toggleMarkdownPreview()
        }
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("查看源码").fetchSemanticsNodes().isNotEmpty()
        }
        waitForMarkdown(webView)
        val previewLineFromSource = waitForPreviewSourceLine(webView, sourceMathLine, semanticBlockTolerance)
        val sourceTargetLine = evaluate(
            webView,
            "Number(document.documentElement.dataset.sourceTargetLine || -1)",
        ).toInt()
        assertTrue(
            "从源码切到预览后应定位到第 $sourceMathLine 行附近，WebView 收到目标行 $sourceTargetLine，实际为第 $previewLineFromSource 行",
            previewLineFromSource in (sourceMathLine - semanticBlockTolerance)..(sourceMathLine + semanticBlockTolerance),
        )

        assertEquals(
            "预览应提供按源码行定位的接口",
            "true",
            evaluate(
                webView,
                "typeof scrollToSourceLine === 'function' && (scrollToSourceLine($previewCodeLine), true)",
            ),
        )
        val previewLineAfterScroll = waitForPreviewSourceLine(webView, previewCodeLine, semanticBlockTolerance)
        assertTrue(
            "预览滚动后应定位到第 $previewCodeLine 行附近，实际为第 $previewLineAfterScroll 行",
            previewLineAfterScroll in (previewCodeLine - semanticBlockTolerance)..(previewCodeLine + semanticBlockTolerance),
        )
        SystemClock.sleep(300)

        composeRule.onNodeWithContentDescription("查看源码").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            editor.firstVisibleLine <= previewCodeLine - 1 && editor.lastVisibleLine >= previewCodeLine - 1
        }
        assertTrue(
            "从预览切回源码后应让第 $previewCodeLine 行保持可见",
            editor.firstVisibleLine <= previewCodeLine - 1 && editor.lastVisibleLine >= previewCodeLine - 1,
        )
        // 该测试类复用 Activity 状态，结束时恢复预览模式，避免后续用例继承源码视图。 @author long
        composeRule.onNodeWithContentDescription("预览 Markdown").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("查看源码").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun markdownOutlineStaysAvailableAndCollapsesAfterNavigation() {
        openMarkdownDocument()
        val webView = waitForWebView()
        waitForMarkdown(webView)
        val viewModel = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java]
        val mathHeadingIndex = requireNotNull(
            viewModel.state.value.markdownHeadings.firstOrNull { it.title == "数学公式" }?.index,
        )
        val collapsedContentTop = composeRule.onNodeWithTag("reader-content-surface")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(
            "Markdown 预览应固定提供可展开的目录入口",
            composeRule.onAllNodesWithContentDescription("展开 Markdown 目录").fetchSemanticsNodes().size == 1,
        )
        composeRule.onNodeWithContentDescription("更多").performClick()
        composeRule.onNodeWithText("展开 Markdown 目录").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("折叠 Markdown 目录").fetchSemanticsNodes().size == 1 &&
                composeRule.onAllNodesWithTag("markdown-outline-list").fetchSemanticsNodes().size == 1
        }
        composeRule.waitForIdle()
        val panelBottom = composeRule.onNodeWithTag("markdown-outline-panel")
            .fetchSemanticsNode().boundsInRoot.bottom
        val expandedContentTop = composeRule.onNodeWithTag("reader-content-surface")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "展开目录应占据独立布局区域，不能覆盖 Markdown 正文",
            panelBottom <= expandedContentTop + 1f,
        )

        composeRule.onNodeWithContentDescription("折叠 Markdown 目录").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("markdown-outline-list").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
        val restoredContentTop = composeRule.onNodeWithTag("reader-content-surface")
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(
            "目录折叠后不能继续占用正文高度",
            kotlin.math.abs(restoredContentTop - collapsedContentTop) < 1f,
        )

        composeRule.onNodeWithContentDescription("展开 Markdown 目录").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("markdown-outline-list").fetchSemanticsNodes().size == 1
        }

        composeRule.onNodeWithTag("markdown-outline-list").performScrollToNode(hasText("数学公式"))
        composeRule.onNodeWithText("数学公式").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("展开 Markdown 目录").fetchSemanticsNodes().size == 1 &&
                composeRule.onAllNodesWithTag("markdown-outline-list").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            evaluate(
                webView,
                "Math.abs(document.getElementById('heading-$mathHeadingIndex')?.getBoundingClientRect().top || 9999) < 96",
            ) == "true"
        }
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
    fun switchingBetweenMarkdownTabsReusesPerDocumentPreviewCache() {
        openMarkdownDocument()
        val webViewBefore = waitForWebView()
        waitForMarkdown(webViewBefore)
        val scrollBefore = evaluate(webViewBefore, "window.scrollTo(0, 480); window.scrollY").toDouble()
        assertTrue("缓存测试文档应能滚动", scrollBefore > 0)
        SystemClock.sleep(300)
        val sourceLineBefore = currentPreviewSourceLine(webViewBefore)

        composeRule.onNodeWithContentDescription("快速切换文件").performClick()
        composeRule.onNodeWithContentDescription("打开 demo/README.md").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("关闭 README.md").fetchSemanticsNodes().size >= 2
        }
        val webViewAfterOpen = waitForWebView()
        waitForMarkdown(webViewAfterOpen)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            evaluate(webViewAfterOpen, "document.body.textContent.includes('Demo 模块') === true") == "true"
        }
        assertNotSame(
            "不同 Markdown 文档应使用独立缓存 WebView",
            webViewBefore,
            webViewAfterOpen,
        )

        assertEquals(
            "第二个 Markdown 标签打开后应完成渲染",
            "\"true\"",
            evaluate(webViewAfterOpen, "document.documentElement.dataset.markdownReady || ''"),
        )

        composeRule.onNode(hasText("README.md") and hasStateDescription("未选中文件")).performClick()
        composeRule.waitForIdle()
        SystemClock.sleep(300)
        val webViewAfterReturn = waitForWebView()
        assertSame("切回已访问 Markdown 文档应复用原 WebView", webViewBefore, webViewAfterReturn)
        assertEquals(
            "切回缓存文档后渲染状态应保持完成",
            "\"true\"",
            evaluate(webViewAfterReturn, "document.documentElement.dataset.markdownReady || ''"),
        )
        val sourceLineAfter = waitForPreviewSourceLine(webViewAfterReturn, sourceLineBefore, 6)
        assertTrue(
            "切回缓存文档后应保留同一语义阅读位置，切换前第 $sourceLineBefore 行，切换后第 $sourceLineAfter 行",
            sourceLineAfter in (sourceLineBefore - 6)..(sourceLineBefore + 6),
        )
        assertTrue("切回缓存文档后不应重置到顶部", evaluate(webViewAfterReturn, "window.scrollY").toDouble() > 0)
    }

    private fun openMarkdownDocument(name: String = "README.md") {
        composeRule.onNodeWithText("内置测试项目").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("project-list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("project-list").performScrollToNode(hasText(name))
        composeRule.onNodeWithText(name).performClick()
        ensureMarkdownPreviewVisible()
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
        ensureMarkdownPreviewVisible()
    }

    private fun ensureMarkdownPreviewVisible() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("查看源码").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithContentDescription("预览 Markdown").fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithContentDescription("预览 Markdown").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithContentDescription("预览 Markdown").performClick()
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("查看源码").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForWebView(): WebView {
        var result: WebView? = null
        val completed = runCatching {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    result = findWebView(composeRule.activity.findViewById(android.R.id.content))
                }
                result != null
            }
        }.isSuccess
        val state = ViewModelProvider(composeRule.activity)[ReaderViewModel::class.java].state.value
        assertTrue(
            "未找到 Markdown WebView：screen=${state.screen}, document=${state.document?.name}, " +
                "preview=${state.markdownPreview}, tabs=${state.tabs.map { it.document.name }}, failure=${state.failure?.detail}",
            completed && result != null,
        )
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
        var errorText = ""
        while (SystemClock.elapsedRealtime() < deadline) {
            state = evaluate(webView, "document.documentElement.dataset.markdownReady || ''")
            if (state == "\"true\"") return
            if (state == "\"error\"") {
                errorText = evaluate(
                    webView,
                    "document.querySelector('#content > .render-error:last-child')?.textContent || ''",
                )
                break
            }
            SystemClock.sleep(100)
        }
        assertEquals("Markdown WebView 未完成渲染：$errorText", "\"true\"", state)
    }

    private fun domCount(webView: WebView, selector: String): Int {
        return evaluate(webView, "document.querySelectorAll(${selector.jsQuoted()}).length").toInt()
    }

    private fun currentPreviewSourceLine(webView: WebView): Int {
        return evaluate(
            webView,
            "typeof currentSourceLine === 'function' ? currentSourceLine() : -1",
        ).toInt()
    }

    private fun waitForPreviewSourceLine(webView: WebView, expectedLine: Int, tolerance: Int): Int {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var actualLine = -1
        while (SystemClock.elapsedRealtime() < deadline) {
            actualLine = currentPreviewSourceLine(webView)
            if (actualLine in (expectedLine - tolerance)..(expectedLine + tolerance)) return actualLine
            SystemClock.sleep(100)
        }
        return actualLine
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
