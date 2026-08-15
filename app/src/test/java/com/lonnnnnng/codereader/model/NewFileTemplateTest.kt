package com.lonnnnnng.codereader.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 新建文件入口必须覆盖主流工程中已经支持阅读的格式，避免创建能力落后于阅读能力。 @author long */
class NewFileTemplateTest {
    @Test
    fun `主流工程格式均有可创建模板`() {
        val expectedTypes = setOf(
            FileType.MARKDOWN,
            FileType.MDX,
            FileType.JAVA,
            FileType.KOTLIN,
            FileType.SCALA,
            FileType.GROOVY,
            FileType.CLOJURE,
            FileType.PYTHON,
            FileType.RUBY,
            FileType.PHP,
            FileType.PERL,
            FileType.LUA,
            FileType.LISP,
            FileType.DART,
            FileType.SWIFT,
            FileType.JULIA,
            FileType.R,
            FileType.FORTRAN,
            FileType.GO,
            FileType.GO_MODULE,
            FileType.RUST,
            FileType.C,
            FileType.CPP,
            FileType.OBJECTIVE_C,
            FileType.OBJECTIVE_CPP,
            FileType.ASSEMBLY,
            FileType.ZIG,
            FileType.FSHARP,
            FileType.VISUAL_BASIC,
            FileType.CSHARP,
            FileType.AIDL,
            FileType.SMALI,
            FileType.JAVASCRIPT,
            FileType.TYPESCRIPT,
            FileType.JAVASCRIPT_REACT,
            FileType.TYPESCRIPT_REACT,
            FileType.HTML,
            FileType.CSS,
            FileType.SCSS,
            FileType.SASS,
            FileType.LESS,
            FileType.VUE,
            FileType.SVELTE,
            FileType.ASTRO,
            FileType.ERB,
            FileType.TWIG,
            FileType.BLADE,
            FileType.RAZOR,
            FileType.JSON,
            FileType.YAML,
            FileType.TOML,
            FileType.INI,
            FileType.PROPERTIES,
            FileType.DOTENV,
            FileType.XML,
            FileType.SQL,
            FileType.GRAPHQL,
            FileType.PRISMA,
            FileType.PROTOBUF,
            FileType.LATEX,
            FileType.SHELL,
            FileType.BATCH,
            FileType.POWERSHELL,
            FileType.CMAKE,
            FileType.HCL,
            FileType.TERRAFORM,
            FileType.NIX,
            FileType.NGINX,
            FileType.DOCKERFILE,
            FileType.MAKEFILE,
            FileType.IGNORE,
            FileType.PROGUARD,
            FileType.SOLUTION,
            FileType.PLAIN_TEXT,
        )

        val templatesByType = NewFileTemplate.options.groupBy { it.fileType }
        expectedTypes.forEach { type ->
            assertTrue("缺少 ${type.displayName} 新建模板", templatesByType.containsKey(type))
        }
        assertEquals(
            NewFileTemplate.options.size,
            NewFileTemplate.options.map { it.id }.distinct().size,
        )
    }

    @Test
    fun `特殊工程文件保持真实文件名和格式识别`() {
        val expectedNames = mapOf(
            FileType.GO_MODULE to "go.mod",
            FileType.CMAKE to "CMakeLists.txt",
            FileType.DOCKERFILE to "Dockerfile",
            FileType.MAKEFILE to "Makefile",
            FileType.NGINX to "nginx.conf",
        )

        expectedNames.forEach { (type, name) ->
            val template = NewFileTemplate.options.first { it.fileType == type }
            assertEquals(name, template.ensureExtension(template.defaultName))
            assertEquals(type, FileType.detect(name))
        }
    }
}
