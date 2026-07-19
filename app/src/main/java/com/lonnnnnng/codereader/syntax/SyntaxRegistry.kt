package com.lonnnnnng.codereader.syntax

import android.content.Context
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.model.ReaderTheme
import com.lonnnnnng.codereader.qa.SampleCase
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.registry.IThemeSource

/** @author long */
data class SyntaxCoverageResult(
    val total: Int,
    val passed: Int,
    val failures: List<String>,
) {
    val isSuccess: Boolean = total == passed && failures.isEmpty()
}

/**
 * TextMate 注册器只初始化一次，避免每次打开文件都重复解析多份语法定义。
 *
 * @author long
 */
object SyntaxRegistry {
    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return

        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
        val themeRegistry = ThemeRegistry.getInstance()
        loadTheme(themeRegistry, ReaderTheme.HIGH_CONTRAST_LIGHT.textMateName, dark = false)
        loadTheme(themeRegistry, "darcula", dark = true)
        check(themeRegistry.setTheme(ReaderTheme.HIGH_CONTRAST_LIGHT.textMateName)) {
            "无法启用默认高对比亮色主题"
        }
        GrammarRegistry.getInstance().loadGrammars("languages.json")
        initialized = true
    }

    fun createLanguage(type: FileType): TextMateLanguage? {
        val scopeName = type.scopeName ?: return null
        return TextMateLanguage.create(scopeName, false)
    }

    fun createColorScheme(): TextMateColorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())

    /**
     * TextMateColorScheme 会监听注册器的主题变化，因此切换后当前打开文件无需重建即可重新着色。
     *
     * @author long
     */
    fun setTheme(context: Context, theme: ReaderTheme) {
        initialize(context)
        check(ThemeRegistry.getInstance().setTheme(theme.textMateName)) {
            "代码主题不存在：${theme.textMateName}"
        }
    }

    fun verify(context: Context, cases: List<SampleCase>): SyntaxCoverageResult {
        initialize(context)
        val failures = mutableListOf<String>()
        var passed = 0
        cases.forEach { sample ->
            val fileName = sample.assetPath.substringAfterLast('/')
            runCatching {
                val detected = FileType.detect(fileName)
                check(detected == sample.expectedType) { "识别为 ${detected.displayName}" }
                val scope = detected.scopeName ?: error("没有配置 TextMate scope")
                val grammar = GrammarRegistry.getInstance().findGrammar(scope) ?: error("语法未加载：$scope")
                val text = context.assets.open(sample.assetPath).bufferedReader().use { it.readText() }
                var stack: IStateStack? = null
                var semanticTokenCount = 0
                text.lineSequence().forEach { line ->
                    val result = grammar.tokenizeLine(line, stack, null)
                    check(!result.isStoppedEarly) { "单行语法分析超时" }
                    stack = result.ruleStack
                    semanticTokenCount += result.tokens.count { token ->
                        token.scopes.any { tokenScope -> tokenScope != scope && tokenScope.contains('.') }
                    }
                }
                check(semanticTokenCount > 0) { "没有产生语义 token" }
            }.onSuccess {
                passed++
            }.onFailure { error ->
                failures += "$fileName: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        return SyntaxCoverageResult(cases.size, passed, failures)
    }

    private fun loadTheme(registry: ThemeRegistry, name: String, dark: Boolean) {
        val path = "textmate/$name.json"
        val stream = FileProviderRegistry.getInstance().tryGetInputStream(path)
            ?: error("主题资源不存在：$path")
        registry.loadTheme(
            ThemeModel(IThemeSource.fromInputStream(stream, path, null), name).apply {
                isDark = dark
            },
        )
    }
}
