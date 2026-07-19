package com.lonnnnnng.codereader.syntax

import android.content.Context
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.model.ReaderTheme
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

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
