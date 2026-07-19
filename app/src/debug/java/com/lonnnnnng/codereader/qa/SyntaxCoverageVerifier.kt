package com.lonnnnnng.codereader.qa

import android.content.Context
import com.lonnnnnng.codereader.model.FileType
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import org.eclipse.tm4e.core.grammar.IStateStack

/** Debug 构建专用的语法覆盖结果，不进入正式 APK。 @author long */
data class SyntaxCoverageResult(
    val total: Int,
    val passed: Int,
    val failures: List<String>,
) {
    val isSuccess: Boolean = total == passed && failures.isEmpty()
}

/**
 * 逐个加载 Debug 样例并检查 TextMate token，正式版无需携带这套运行时自检逻辑。
 *
 * @author long
 */
object SyntaxCoverageVerifier {
    fun verify(context: Context, cases: List<SampleCase> = SampleCatalog.all): SyntaxCoverageResult {
        SyntaxRegistry.initialize(context)
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
}
