package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.qa.SampleCatalog
import com.lonnnnnng.codereader.qa.SyntaxCoverageVerifier
import org.eclipse.tm4e.core.internal.oniguruma.Oniguruma
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** @author long */
@RunWith(AndroidJUnit4::class)
class SyntaxCoverageInstrumentedTest {
    @Test
    fun allDeclaredSamplesLoadGrammarAndProduceSemanticTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = SyntaxCoverageVerifier.verify(context, SampleCatalog.all)
        assertTrue(result.failures.joinToString(separator = "\n"), result.isSuccess)
        assertTrue("Android 语法扫描必须使用 Joni，避免 native Oniguruma 崩溃", !Oniguruma().isUseNativeOniguruma)
    }
}
