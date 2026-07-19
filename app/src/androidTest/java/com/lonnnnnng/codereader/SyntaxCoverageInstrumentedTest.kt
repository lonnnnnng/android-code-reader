package com.lonnnnnng.codereader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonnnnnng.codereader.qa.SampleCatalog
import com.lonnnnnng.codereader.syntax.SyntaxRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** @author long */
@RunWith(AndroidJUnit4::class)
class SyntaxCoverageInstrumentedTest {
    @Test
    fun allDeclaredSamplesLoadGrammarAndProduceSemanticTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = SyntaxRegistry.verify(context, SampleCatalog.all)
        assertTrue(result.failures.joinToString(separator = "\n"), result.isSuccess)
    }
}
