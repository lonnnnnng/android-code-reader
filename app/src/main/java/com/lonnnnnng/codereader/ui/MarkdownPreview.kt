package com.lonnnnnng.codereader.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.lonnnnnng.codereader.model.ReaderFontFamily

private class MarkdownDocumentBinding {
    var markdownText: String? = null
    var darkTheme: Boolean? = null
    var fontSizeSp: Float? = null
    var fontFamily: ReaderFontFamily? = null
    var backgroundColorArgb: Int? = null
    var commandId: Long? = null
    var searchQuery: String? = null
}

private class MarkdownBridge(context: Context) {
    private val appContext = context.applicationContext

    /** 预览页只加载 APK 内置 HTML，复制桥接不接受网页导航后的调用。 @author long */
    @JavascriptInterface
    fun copyText(text: String) {
        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Markdown code", text))
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, "代码已复制", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * 使用 APK 内置资源渲染 Markdown，保证代码高亮、数学公式和 Mermaid 在离线环境也能工作。
 *
 * @author long
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownPreview(
    markdownText: String,
    darkTheme: Boolean,
    fontSizeSp: Float,
    fontFamily: ReaderFontFamily,
    backgroundColorArgb: Int,
    command: ReaderCommand?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val binding = remember { MarkdownDocumentBinding() }
    val htmlTemplate = remember(context) {
        context.assets.open("markdown/index.html").bufferedReader().use { it.readText() }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            WebView(viewContext).apply {
                setBackgroundColor(backgroundColorArgb)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowContentAccess = false
                    allowFileAccess = true
                    blockNetworkLoads = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        return openExternalLink(view, request.url)
                    }
                }
                addJavascriptInterface(MarkdownBridge(viewContext), "CodeReader")
            }
        },
        update = { webView ->
            val contentChanged = binding.markdownText != markdownText ||
                binding.darkTheme != darkTheme || binding.fontSizeSp != fontSizeSp ||
                binding.fontFamily != fontFamily || binding.backgroundColorArgb != backgroundColorArgb
            if (contentChanged) {
                val encodedMarkdown = Base64.encodeToString(markdownText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val html = htmlTemplate
                    .replace("__BODY_CLASS__", if (darkTheme) "dark" else "")
                    .replace("__DARK_THEME__", darkTheme.toString())
                    .replace("__FONT_SIZE__", fontSizeSp.toInt().toString())
                    .replace("__FONT_FAMILY__", fontFamily.cssFamily)
                    .replace("__BACKGROUND_COLOR__", "#%06X".format(backgroundColorArgb and 0x00FFFFFF))
                    .replace("__MARKDOWN_BASE64__", encodedMarkdown)

                // 主题和正文一起重载，避免 WebView 保留上一份文档的 Mermaid 或 KaTeX 节点。
                webView.setBackgroundColor(backgroundColorArgb)
                webView.loadDataWithBaseURL(
                    "file:///android_asset/markdown/",
                    html,
                    "text/html",
                    "UTF-8",
                    null,
                )
                binding.markdownText = markdownText
                binding.darkTheme = darkTheme
                binding.fontSizeSp = fontSizeSp
                binding.fontFamily = fontFamily
                binding.backgroundColorArgb = backgroundColorArgb
                binding.searchQuery = null
            }
            if (command != null && binding.commandId != command.id) {
                val delay = if (contentChanged) 500L else 0L
                webView.postDelayed({ handleMarkdownCommand(webView, binding, command) }, delay)
                binding.commandId = command.id
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.removeJavascriptInterface("CodeReader")
            webView.loadUrl("about:blank")
            webView.destroy()
        },
    )
}

private fun handleMarkdownCommand(webView: WebView, binding: MarkdownDocumentBinding, command: ReaderCommand) {
    when (command.type) {
        ReaderCommandType.SEARCH_FORWARD,
        ReaderCommandType.SEARCH_BACKWARD -> {
            if (binding.searchQuery != command.query) {
                webView.findAllAsync(command.query)
                binding.searchQuery = command.query
                webView.postDelayed({ webView.findNext(command.type == ReaderCommandType.SEARCH_FORWARD) }, 150)
            } else {
                webView.findNext(command.type == ReaderCommandType.SEARCH_FORWARD)
            }
        }
        ReaderCommandType.MARKDOWN_HEADING -> {
            webView.evaluateJavascript("scrollToHeading(${command.headingIndex})", null)
        }
        ReaderCommandType.GOTO_LINE -> Unit
    }
}

private fun openExternalLink(webView: WebView, uri: Uri): Boolean {
    if (uri.scheme !in setOf("http", "https", "mailto")) return true
    return runCatching {
        webView.context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    }.getOrDefault(true)
}
