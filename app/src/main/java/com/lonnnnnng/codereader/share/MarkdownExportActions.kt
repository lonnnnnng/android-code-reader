package com.lonnnnnng.codereader.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import androidx.core.content.FileProvider
import com.lonnnnnng.codereader.BuildConfig
import org.json.JSONTokener
import java.io.File

/**
 * 统一处理 Markdown 预览的系统交互，避免 WebView 直接持有分享、文件权限和打印生命周期。
 *
 * @author long
 */
object MarkdownExportActions {

    fun copyRenderedText(
        context: Context,
        webView: WebView,
        onResult: (String) -> Unit,
    ) {
        evaluate(webView, "renderedMarkdownText()") { text ->
            if (text.isBlank()) {
                onResult("当前预览没有可复制的正文")
                return@evaluate
            }
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText("Markdown 渲染文本", text))
            onResult("已复制渲染文本")
        }
    }

    fun shareRenderedHtml(
        context: Context,
        webView: WebView,
        title: String,
        onResult: (String) -> Unit,
    ) {
        evaluate(webView, "renderedMarkdownHtml()") { html ->
            if (html.isBlank()) {
                onResult("当前预览还没有完成，暂时无法分享")
                return@evaluate
            }
            var target: File? = null
            runCatching {
                val preparedFile = createPreparedHtmlFile(context, html, title)
                target = preparedFile
                val uri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.files",
                    preparedFile,
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, "${title}\n\nHTML 渲染结果见附件")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(title, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "分享 Markdown 渲染结果"))
                onResult("已打开分享面板")
            }.onFailure { error ->
                target?.delete()
                onResult("分享失败：${error.message ?: error.javaClass.simpleName}")
            }
        }
    }

    fun renderedHtml(webView: WebView, onResult: (String) -> Unit) {
        evaluate(webView, "renderedMarkdownHtml()", onResult)
    }

    fun prepareHtml(context: Context, html: String, title: String): String {
        val katexCss = portableKatexCss(context)
        val style = if (katexCss.isBlank()) "" else "<style>\n$katexCss\n</style>"
        return addDocumentTitle(html, title).replaceFirst("</head>", "$style</head>")
    }

    fun createPreparedHtmlFile(context: Context, html: String, title: String): File {
        val exportDir = File(context.cacheDir, "markdown-exports").apply {
            mkdirs()
            // 分享 URI 无法可靠获知外部应用何时读取完成，因此只回收超过一天的旧文件，避免提前撤销有效内容。 @author long
            val deadline = System.currentTimeMillis() - EXPORT_CACHE_MAX_AGE_MS
            listFiles()?.filter { it.isFile && it.lastModified() < deadline }?.forEach(File::delete)
        }
        val target = File.createTempFile("markdown-", ".html", exportDir)
        return runCatching {
            target.writeText(prepareHtml(context, html, title), Charsets.UTF_8)
            target
        }.getOrElse { error ->
            target.delete()
            throw error
        }
    }

    fun printRenderedMarkdown(
        context: Context,
        webView: WebView,
        title: String,
        onResult: (String) -> Unit,
    ) {
        runCatching {
            // PDF 由系统打印服务生成和保存，用户能看到目标、纸张与取消状态，应用不静默写入未知目录。 @author long
            val printManager = context.getSystemService(PrintManager::class.java)
                ?: error("系统打印服务不可用")
            printManager.print(
                title,
                webView.createPrintDocumentAdapter(title),
                PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build(),
            )
            onResult("已打开 PDF 打印与保存面板")
        }.onFailure { error ->
            onResult("导出 PDF 失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun evaluate(webView: WebView, expression: String, onResult: (String) -> Unit) {
        webView.evaluateJavascript(expression) { raw ->
            val value = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull().orEmpty()
            onResult(value)
        }
    }

    private fun addDocumentTitle(html: String, title: String): String {
        val escapedTitle = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        return html.replaceFirst("<head>", "<head><title>$escapedTitle</title>")
    }

    private fun portableKatexCss(context: Context): String {
        val css = runCatching {
            context.assets.open("markdown/vendor/katex/katex.min.css")
                .bufferedReader()
                .use { it.readText() }
        }.getOrDefault("")
        if (css.isBlank()) return ""

        // KaTeX 的 DOM 依赖专用字体排版；只内联现代浏览器首选的 WOFF2，并移除不可携带的旧格式相对地址。 @author long
        val withEmbeddedWoff2 = KATEX_WOFF2_URL.replace(css) { match ->
            val fileName = match.groupValues[1]
            runCatching {
                val encoded = context.assets.open("markdown/vendor/katex/fonts/$fileName")
                    .use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
                "url(data:font/woff2;base64,$encoded)"
            }.getOrDefault(match.value)
        }
        return KATEX_LEGACY_FONT_FALLBACKS.replace(withEmbeddedWoff2, "")
    }

    private const val EXPORT_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
    private val KATEX_WOFF2_URL = Regex("""url\(fonts/([^)]+\.woff2)\)""")
    private val KATEX_LEGACY_FONT_FALLBACKS = Regex(
        """,url\(fonts/[^)]+\.woff\) format\("woff"\),url\(fonts/[^)]+\.ttf\) format\("truetype"\)""",
    )
}
