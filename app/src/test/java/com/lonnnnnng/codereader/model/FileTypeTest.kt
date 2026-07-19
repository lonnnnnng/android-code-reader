package com.lonnnnnng.codereader.model

import com.lonnnnnng.codereader.qa.SampleCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/** @author long */
class FileTypeTest {
    @Test
    fun `所有模拟器测试文件都能识别到预期类型`() {
        SampleCatalog.all.forEach { sample ->
            val fileName = sample.assetPath.substringAfterLast('/')
            assertEquals(fileName, sample.expectedType, FileType.detect(fileName))
        }
    }

    @Test
    fun `常见工程文件和无扩展配置文件有稳定映射`() {
        val cases = mapOf(
            "Directory.Build.props" to FileType.XML,
            "service.targets" to FileType.XML,
            "backend.fsproj" to FileType.XML,
            "vite.config.ts" to FileType.TYPESCRIPT,
            ".env" to FileType.DOTENV,
            ".npmrc" to FileType.INI,
            "Dockerfile.release" to FileType.DOCKERFILE,
            "Makefile.debug" to FileType.MAKEFILE,
        )
        cases.forEach { (fileName, expected) ->
            assertEquals(fileName, expected, FileType.detect(fileName))
        }
    }

    @Test
    fun `已经打包的语言语法都能通过常见扩展名访问`() {
        val cases = mapOf(
            "build.cmd" to FileType.BATCH,
            "core.clj" to FileType.CLOJURE,
            "main.dart" to FileType.DART,
            "solver.f90" to FileType.FORTRAN,
            "analysis.jl" to FileType.JULIA,
            "paper.tex" to FileType.LATEX,
            "init.el" to FileType.LISP,
            "plugin.lua" to FileType.LUA,
            "script.pl" to FileType.PERL,
            "MainActivity.smali" to FileType.SMALI,
            "build.zig" to FileType.ZIG,
        )
        cases.forEach { (fileName, expected) ->
            assertEquals(fileName, expected, FileType.detect(fileName))
        }
    }

    @Test
    fun `主流工程中的固定文件名按真实底层格式识别`() {
        val cases = mapOf(
            "Cargo.lock" to FileType.TOML,
            "poetry.lock" to FileType.TOML,
            "uv.lock" to FileType.TOML,
            "Pipfile" to FileType.TOML,
            "composer.lock" to FileType.JSON,
            "Pipfile.lock" to FileType.JSON,
            "pubspec.lock" to FileType.YAML,
            "App.config" to FileType.XML,
            "worker.exe.config" to FileType.XML,
            "Main.storyboard" to FileType.XML,
            "Info.plist" to FileType.XML,
            "App.xaml" to FileType.XML,
            "project.vcxproj.filters" to FileType.XML,
            "gradlew" to FileType.SHELL,
            "mvnw" to FileType.SHELL,
            "Jenkinsfile" to FileType.GROOVY,
            "Podfile" to FileType.RUBY,
            "config.ru" to FileType.RUBY,
            ".env.local" to FileType.DOTENV,
            ".env.production" to FileType.DOTENV,
        )
        cases.forEach { (fileName, expected) ->
            assertEquals(fileName, expected, FileType.detect(fileName))
        }
    }

    @Test
    fun `主流移动端后端和基础设施源码使用专用语法`() {
        val cases = mapOf(
            "Package.swift" to FileType.SWIFT,
            "ViewController.m" to FileType.OBJECTIVE_C,
            "Bridge.mm" to FileType.OBJECTIVE_CPP,
            "build.sbt" to FileType.SCALA,
            "main.tf" to FileType.TERRAFORM,
            "variables.hcl" to FileType.HCL,
            "message.proto" to FileType.PROTOBUF,
            "schema.graphql" to FileType.GRAPHQL,
            "schema.prisma" to FileType.PRISMA,
            "CMakeLists.txt" to FileType.CMAKE,
            "toolchain.cmake" to FileType.CMAKE,
            "Page.razor" to FileType.RAZOR,
            "Index.cshtml" to FileType.RAZOR,
            "Component.svelte" to FileType.SVELTE,
            "Page.astro" to FileType.ASTRO,
            "guide.mdx" to FileType.MDX,
            "partial.erb" to FileType.ERB,
            "page.twig" to FileType.TWIG,
            "view.blade.php" to FileType.BLADE,
            "theme.scss" to FileType.SCSS,
            "theme.sass" to FileType.SASS,
            "theme.less" to FileType.LESS,
            "deploy.ps1" to FileType.POWERSHELL,
            "analysis.r" to FileType.R,
            "flake.nix" to FileType.NIX,
            "nginx.conf" to FileType.NGINX,
            "startup.asm" to FileType.ASSEMBLY,
            ".gitignore" to FileType.IGNORE,
            "gradle.properties" to FileType.PROPERTIES,
            "go.mod" to FileType.GO_MODULE,
            "go.work" to FileType.GO_MODULE,
            "proguard-rules.pro" to FileType.PROGUARD,
            "service.aidl" to FileType.AIDL,
            "Component.jsx" to FileType.JAVASCRIPT_REACT,
            "Component.tsx" to FileType.TYPESCRIPT_REACT,
            "app.ts" to FileType.TYPESCRIPT,
            "default.conf" to FileType.PLAIN_TEXT,
            "settings.config" to FileType.PLAIN_TEXT,
            ".eslintrc" to FileType.PLAIN_TEXT,
            ".prettierrc" to FileType.PLAIN_TEXT,
            "desktop.pro" to FileType.PLAIN_TEXT,
            "proguard-rules.pro" to FileType.PROGUARD,
            "Containerfile.release" to FileType.DOCKERFILE,
        )
        cases.forEach { (fileName, expected) ->
            assertEquals(fileName, expected, FileType.detect(fileName))
        }
    }
}
