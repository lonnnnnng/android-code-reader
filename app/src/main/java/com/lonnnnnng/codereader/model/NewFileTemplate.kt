package com.lonnnnnng.codereader.model

/**
 * 新增文件的产品化模板：类型、默认文件名、MIME 和首屏骨架必须一起传递，避免创建后被当成普通 txt。
 *
 * @author long
 */
data class NewFileTemplate(
    val id: String,
    val displayName: String,
    val fileType: FileType,
    val defaultName: String,
    val mimeType: String,
    val content: String,
    val extension: String? = defaultName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".${it}" },
    val category: String = "其他",
) {
    fun ensureExtension(name: String): String {
        val suffix = extension ?: return name
        if (name.lowercase().endsWith(suffix.lowercase())) return name
        val lastDot = name.lastIndexOf('.')
        val base = if (lastDot > 0) name.substring(0, lastDot) else name
        return base + suffix
    }

    companion object {
        /**
         * 新建入口与 FileType 共用同一套主流工程格式，避免“能阅读但不能创建”的断层。
         * 分类只影响手机端下拉菜单的组织方式，不改变底层文件识别和语法高亮。 @author long
         */
        val options: List<NewFileTemplate> = listOf(
            // Markdown 与 JVM / 后端源码。
            NewFileTemplate("markdown", "Markdown", FileType.MARKDOWN, "README.md", "text/markdown", "# 新文档\n\n", category = "文档与后端"),
            NewFileTemplate("mdx", "MDX", FileType.MDX, "README.mdx", "text/markdown", "# 新文档\n\n", category = "文档与后端"),
            NewFileTemplate("java", "Java", FileType.JAVA, "Main.java", "text/x-java-source", "public class Main {\n    public static void main(String[] args) {\n    }\n}\n", category = "文档与后端"),
            NewFileTemplate("kotlin", "Kotlin", FileType.KOTLIN, "Main.kt", "text/x-kotlin", "fun main() {\n}\n", category = "文档与后端"),
            NewFileTemplate("python", "Python", FileType.PYTHON, "main.py", "text/x-python", "def main():\n    pass\n\n\nif __name__ == \"__main__\":\n    main()\n", category = "文档与后端"),
            NewFileTemplate("go", "Go", FileType.GO, "main.go", "text/x-go", "package main\n\nfunc main() {\n}\n", category = "文档与后端"),
            NewFileTemplate("rust", "Rust", FileType.RUST, "main.rs", "text/rust", "fn main() {\n}\n", category = "文档与后端"),
            NewFileTemplate("javascript", "JavaScript", FileType.JAVASCRIPT, "index.js", "text/javascript", "function main() {\n}\n\nmain();\n", category = "文档与后端"),
            NewFileTemplate("typescript", "TypeScript", FileType.TYPESCRIPT, "index.ts", "text/typescript", "function main(): void {\n}\n\nmain();\n", category = "文档与后端"),
            NewFileTemplate("kotlin-script", "Kotlin Script", FileType.KOTLIN, "build.kts", "text/x-kotlin", "println(\"Hello\")\n", category = "文档与后端"),
            NewFileTemplate("scala", "Scala", FileType.SCALA, "Main.scala", "text/x-scala", "object Main extends App {\n}\n", category = "文档与后端"),
            NewFileTemplate("groovy", "Groovy / Gradle", FileType.GROOVY, "build.gradle", "text/x-groovy", "plugins {\n}\n", category = "文档与后端"),
            NewFileTemplate("clojure", "Clojure", FileType.CLOJURE, "core.clj", "text/x-clojure", "(ns app.core)\n\n(defn -main []\n  (println \"Hello\"))\n", category = "文档与后端"),
            NewFileTemplate("ruby", "Ruby", FileType.RUBY, "main.rb", "text/x-ruby", "def main\nend\n\nmain\n", category = "文档与后端"),
            NewFileTemplate("php", "PHP", FileType.PHP, "index.php", "text/x-php", "<?php\n\nfunction main(): void\n{\n}\n\nmain();\n", category = "文档与后端"),
            NewFileTemplate("perl", "Perl", FileType.PERL, "script.pl", "text/x-perl", "use strict;\nuse warnings;\n\nprint \"Hello\\n\";\n", category = "文档与后端"),
            NewFileTemplate("lua", "Lua", FileType.LUA, "main.lua", "text/x-lua", "local function main()\nend\n\nmain()\n", category = "文档与后端"),
            NewFileTemplate("lisp", "Lisp", FileType.LISP, "init.el", "text/x-lisp", ";; 新的 Lisp 文件\n", category = "文档与后端"),
            NewFileTemplate("dart", "Dart", FileType.DART, "main.dart", "text/x-dart", "void main() {\n}\n", category = "文档与后端"),
            NewFileTemplate("swift", "Swift", FileType.SWIFT, "main.swift", "text/x-swift", "import Foundation\n\nprint(\"Hello\")\n", category = "文档与后端"),
            NewFileTemplate("julia", "Julia", FileType.JULIA, "main.jl", "text/x-julia", "function main()\nend\n\nmain()\n", category = "文档与后端"),
            NewFileTemplate("r", "R", FileType.R, "analysis.r", "text/x-r", "main <- function() {\n}\n\nmain()\n", category = "文档与后端"),
            NewFileTemplate("fortran", "Fortran", FileType.FORTRAN, "main.f90", "text/x-fortran", "program main\n  implicit none\nend program main\n", category = "文档与后端"),

            // Go、Rust、C/C++ 与系统级源码。
            NewFileTemplate("go-module", "Go Module", FileType.GO_MODULE, "go.mod", "text/plain", "module example.com/app\n\ngo 1.24\n", extension = null, category = "系统与原生"),
            NewFileTemplate("c", "C", FileType.C, "main.c", "text/x-c", "#include <stdio.h>\n\nint main(void) {\n    return 0;\n}\n", category = "系统与原生"),
            NewFileTemplate("cpp", "C++", FileType.CPP, "main.cpp", "text/x-c++src", "#include <iostream>\n\nint main() {\n    return 0;\n}\n", category = "系统与原生"),
            NewFileTemplate("objective-c", "Objective-C", FileType.OBJECTIVE_C, "ViewController.m", "text/x-objective-c", "#import <Foundation/Foundation.h>\n", category = "系统与原生"),
            NewFileTemplate("objective-cpp", "Objective-C++", FileType.OBJECTIVE_CPP, "Bridge.mm", "text/x-objective-c++", "#import <Foundation/Foundation.h>\n", category = "系统与原生"),
            NewFileTemplate("assembly", "Assembly", FileType.ASSEMBLY, "startup.asm", "text/x-asm", "; 汇编入口\n", category = "系统与原生"),
            NewFileTemplate("zig", "Zig", FileType.ZIG, "main.zig", "text/x-zig", "const std = @import(\"std\");\n\n pub fn main() void {}\n", category = "系统与原生"),
            NewFileTemplate("fsharp", "F#", FileType.FSHARP, "Program.fs", "text/x-fsharp", "[<EntryPoint>]\nlet main _ =\n    0\n", category = "系统与原生"),
            NewFileTemplate("visual-basic", "Visual Basic", FileType.VISUAL_BASIC, "Program.vb", "text/x-vb", "Module Program\n    Sub Main()\n    End Sub\nEnd Module\n", category = "系统与原生"),
            NewFileTemplate("csharp", "C#", FileType.CSHARP, "Program.cs", "text/plain", "using System;\n\npublic class Program\n{\n    public static void Main()\n    {\n    }\n}\n", category = "系统与原生"),
            NewFileTemplate("aidl", "Android AIDL", FileType.AIDL, "Service.aidl", "text/plain", "interface IService {\n}\n", category = "系统与原生"),
            NewFileTemplate("smali", "Smali", FileType.SMALI, "MainActivity.smali", "text/plain", ".class public LMainActivity;\n.super Ljava/lang/Object;\n", category = "系统与原生"),

            // Web、跨端与模板文件。
            NewFileTemplate("javascript-react", "React JSX", FileType.JAVASCRIPT_REACT, "Component.jsx", "text/jsx", "export default function Component() {\n  return <main />;\n}\n", category = "Web 与跨端"),
            NewFileTemplate("typescript-react", "React TSX", FileType.TYPESCRIPT_REACT, "Component.tsx", "text/tsx", "export default function Component() {\n  return <main />;\n}\n", category = "Web 与跨端"),
            NewFileTemplate("html", "HTML", FileType.HTML, "index.html", "text/html", "<!doctype html>\n<html lang=\"zh-CN\">\n  <head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>新页面</title>\n  </head>\n  <body>\n  </body>\n</html>\n", category = "Web 与跨端"),
            NewFileTemplate("css", "CSS", FileType.CSS, "styles.css", "text/css", "* {\n  box-sizing: border-box;\n}\n", category = "Web 与跨端"),
            NewFileTemplate("scss", "SCSS", FileType.SCSS, "styles.scss", "text/x-scss", "\$accent: #16745a;\n\n.container {\n  color: \$accent;\n}\n", category = "Web 与跨端"),
            NewFileTemplate("sass", "Sass", FileType.SASS, "styles.sass", "text/x-sass", ".container\n  color: #16745a\n", category = "Web 与跨端"),
            NewFileTemplate("less", "Less", FileType.LESS, "styles.less", "text/x-less", "@accent: #16745a;\n\n.container {\n  color: @accent;\n}\n", category = "Web 与跨端"),
            NewFileTemplate("vue", "Vue", FileType.VUE, "App.vue", "text/plain", "<script setup>\n</script>\n\n<template>\n  <main></main>\n</template>\n\n<style scoped>\n</style>\n", category = "Web 与跨端"),
            NewFileTemplate("svelte", "Svelte", FileType.SVELTE, "App.svelte", "text/plain", "<script>\n  let message = 'Hello';\n</script>\n\n<main>{message}</main>\n", category = "Web 与跨端"),
            NewFileTemplate("astro", "Astro", FileType.ASTRO, "index.astro", "text/plain", "---\nconst title = '新页面';\n---\n\n<html lang=\"zh-CN\">\n  <body><h1>{title}</h1></body>\n</html>\n", category = "Web 与跨端"),
            NewFileTemplate("erb", "ERB", FileType.ERB, "index.html.erb", "text/plain", "<h1><%= title %></h1>\n", category = "Web 与跨端"),
            NewFileTemplate("twig", "Twig", FileType.TWIG, "index.html.twig", "text/plain", "<h1>{{ title }}</h1>\n", category = "Web 与跨端"),
            NewFileTemplate("blade", "Blade", FileType.BLADE, "index.blade.php", "text/plain", "<main>{{ \$title }}</main>\n", extension = ".blade.php", category = "Web 与跨端"),
            NewFileTemplate("razor", "Razor", FileType.RAZOR, "Index.cshtml", "text/plain", "<h1>@ViewData[\"Title\"]</h1>\n", category = "Web 与跨端"),

            // 数据、接口和项目配置。
            NewFileTemplate("json", "JSON", FileType.JSON, "config.json", "application/json", "{\n}\n", category = "数据与配置"),
            NewFileTemplate("jsonc", "JSON with Comments", FileType.JSON, "settings.jsonc", "application/json", "{\n  // 支持注释的 JSON 配置\n}\n", category = "数据与配置"),
            NewFileTemplate("geojson", "GeoJSON", FileType.JSON, "map.geojson", "application/geo+json", "{\n  \"type\": \"FeatureCollection\",\n  \"features\": []\n}\n", category = "数据与配置"),
            NewFileTemplate("yaml", "YAML", FileType.YAML, "config.yml", "application/yaml", "# 配置项\n", category = "数据与配置"),
            NewFileTemplate("toml", "TOML", FileType.TOML, "config.toml", "text/plain", "[app]\nname = \"\"\n", category = "数据与配置"),
            NewFileTemplate("ini", "INI / CFG", FileType.INI, "config.ini", "text/plain", "[app]\nname=\n", category = "数据与配置"),
            NewFileTemplate("properties", "Properties", FileType.PROPERTIES, "application.properties", "text/plain", "# 应用配置\n", category = "数据与配置"),
            NewFileTemplate("dotenv", "环境变量 (.env)", FileType.DOTENV, ".env", "text/plain", "# 本地环境变量\n", category = "数据与配置"),
            NewFileTemplate("xml", "XML / .NET Project", FileType.XML, "config.xml", "application/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root />\n", category = "数据与配置"),
            NewFileTemplate("sql", "SQL", FileType.SQL, "query.sql", "application/sql", "SELECT 1;\n", category = "数据与配置"),
            NewFileTemplate("graphql", "GraphQL", FileType.GRAPHQL, "schema.graphql", "text/plain", "type Query {\n}\n", category = "数据与配置"),
            NewFileTemplate("prisma", "Prisma", FileType.PRISMA, "schema.prisma", "text/plain", "generator client {\n  provider = \"prisma-client-js\"\n}\n", category = "数据与配置"),
            NewFileTemplate("proto", "Protocol Buffers", FileType.PROTOBUF, "schema.proto", "text/plain", "syntax = \"proto3\";\n\npackage app;\n", category = "数据与配置"),
            NewFileTemplate("latex", "LaTeX", FileType.LATEX, "document.tex", "text/x-tex", "\\documentclass{article}\n\\begin{document}\n\\end{document}\n", category = "数据与配置"),

            // 脚本、容器和基础设施。
            NewFileTemplate("shell", "Shell", FileType.SHELL, "script.sh", "text/x-shellscript", "#!/usr/bin/env bash\n\nset -euo pipefail\n", category = "脚本与基础设施"),
            NewFileTemplate("batch", "Windows Batch", FileType.BATCH, "build.bat", "text/plain", "@echo off\nsetlocal\n", category = "脚本与基础设施"),
            NewFileTemplate("powershell", "PowerShell", FileType.POWERSHELL, "script.ps1", "text/plain", "Set-StrictMode -Version Latest\n", category = "脚本与基础设施"),
            NewFileTemplate("cmake", "CMake", FileType.CMAKE, "CMakeLists.txt", "text/plain", "cmake_minimum_required(VERSION 3.24)\nproject(app)\n", extension = null, category = "脚本与基础设施"),
            NewFileTemplate("hcl", "HCL", FileType.HCL, "main.hcl", "text/plain", "variable \"name\" {\n  type = string\n}\n", category = "脚本与基础设施"),
            NewFileTemplate("terraform", "Terraform", FileType.TERRAFORM, "main.tf", "text/plain", "terraform {\n  required_version = \">= 1.0\"\n}\n", category = "脚本与基础设施"),
            NewFileTemplate("nix", "Nix", FileType.NIX, "flake.nix", "text/plain", "{\n  description = \"新 Nix 项目\";\n}\n", category = "脚本与基础设施"),
            NewFileTemplate("nginx", "Nginx", FileType.NGINX, "nginx.conf", "text/plain", "events {}\n\nhttp {\n}\n", extension = null, category = "脚本与基础设施"),
            NewFileTemplate("dockerfile", "Dockerfile", FileType.DOCKERFILE, "Dockerfile", "text/plain", "FROM alpine:latest\n\nCMD [\"sh\"]\n", extension = null, category = "脚本与基础设施"),
            NewFileTemplate("makefile", "Makefile", FileType.MAKEFILE, "Makefile", "text/plain", ".PHONY: all\n\nall:\n\t@echo \"build\"\n", extension = null, category = "脚本与基础设施"),
            NewFileTemplate("ignore", "Ignore Rules", FileType.IGNORE, ".gitignore", "text/plain", "build/\n.DS_Store\n", category = "脚本与基础设施"),
            NewFileTemplate("proguard", "ProGuard / R8", FileType.PROGUARD, "proguard-rules.pro", "text/plain", "# 自定义混淆规则\n", category = "脚本与基础设施"),
            NewFileTemplate("dotnet-solution", ".NET Solution", FileType.SOLUTION, "App.sln", "text/plain", "Microsoft Visual Studio Solution File, Format Version 12.00\n", category = "脚本与基础设施"),
            NewFileTemplate("plain-text", "纯文本", FileType.PLAIN_TEXT, "notes.txt", "text/plain", "", category = "其他"),
        )

        val default: NewFileTemplate = options.first()

        fun find(id: String): NewFileTemplate = options.firstOrNull { it.id == id } ?: default
    }
}
