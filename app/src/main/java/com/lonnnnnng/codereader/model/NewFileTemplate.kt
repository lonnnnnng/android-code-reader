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
) {
    fun ensureExtension(name: String): String {
        val suffix = extension ?: return name
        if (name.lowercase().endsWith(suffix.lowercase())) return name
        val lastDot = name.lastIndexOf('.')
        val base = if (lastDot > 0) name.substring(0, lastDot) else name
        return base + suffix
    }

    companion object {
        /** 下拉列表优先展示高频源码、配置和 Markdown 类型，保持手机端选择效率。 @author long */
        val options: List<NewFileTemplate> = listOf(
            NewFileTemplate("markdown", "Markdown", FileType.MARKDOWN, "README.md", "text/markdown", "# 新文档\n\n"),
            NewFileTemplate("java", "Java", FileType.JAVA, "Main.java", "text/x-java-source", "public class Main {\n    public static void main(String[] args) {\n    }\n}\n"),
            NewFileTemplate("kotlin", "Kotlin", FileType.KOTLIN, "Main.kt", "text/x-kotlin", "fun main() {\n}\n"),
            NewFileTemplate("python", "Python", FileType.PYTHON, "main.py", "text/x-python", "def main():\n    pass\n\n\nif __name__ == \"__main__\":\n    main()\n"),
            NewFileTemplate("go", "Go", FileType.GO, "main.go", "text/x-go", "package main\n\nfunc main() {\n}\n"),
            NewFileTemplate("rust", "Rust", FileType.RUST, "main.rs", "text/rust", "fn main() {\n}\n"),
            NewFileTemplate("javascript", "JavaScript", FileType.JAVASCRIPT, "index.js", "text/javascript", "function main() {\n}\n\nmain();\n"),
            NewFileTemplate("typescript", "TypeScript", FileType.TYPESCRIPT, "index.ts", "text/typescript", "function main(): void {\n}\n\nmain();\n"),
            NewFileTemplate("html", "HTML", FileType.HTML, "index.html", "text/html", "<!doctype html>\n<html lang=\"zh-CN\">\n  <head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>新页面</title>\n  </head>\n  <body>\n  </body>\n</html>\n"),
            NewFileTemplate("css", "CSS", FileType.CSS, "styles.css", "text/css", "* {\n  box-sizing: border-box;\n}\n"),
            NewFileTemplate("vue", "Vue", FileType.VUE, "App.vue", "text/plain", "<script setup>\n</script>\n\n<template>\n  <main></main>\n</template>\n\n<style scoped>\n</style>\n"),
            NewFileTemplate("json", "JSON", FileType.JSON, "config.json", "application/json", "{\n}\n"),
            NewFileTemplate("yaml", "YAML", FileType.YAML, "config.yml", "application/yaml", "# 配置项\n"),
            NewFileTemplate("toml", "TOML", FileType.TOML, "config.toml", "text/plain", "[app]\nname = \"\"\n"),
            NewFileTemplate("sql", "SQL", FileType.SQL, "query.sql", "application/sql", "SELECT 1;\n"),
            NewFileTemplate("shell", "Shell", FileType.SHELL, "script.sh", "text/x-shellscript", "#!/usr/bin/env bash\n\nset -euo pipefail\n"),
            NewFileTemplate("c", "C", FileType.C, "main.c", "text/x-c", "#include <stdio.h>\n\nint main(void) {\n    return 0;\n}\n"),
            NewFileTemplate("cpp", "C++", FileType.CPP, "main.cpp", "text/x-c++src", "#include <iostream>\n\nint main() {\n    return 0;\n}\n"),
            NewFileTemplate("csharp", "C#", FileType.CSHARP, "Program.cs", "text/plain", "using System;\n\npublic class Program\n{\n    public static void Main()\n    {\n    }\n}\n"),
            NewFileTemplate("php", "PHP", FileType.PHP, "index.php", "text/x-php", "<?php\n\nfunction main(): void\n{\n}\n\nmain();\n"),
            NewFileTemplate("xml", "XML", FileType.XML, "config.xml", "application/xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root />\n"),
            NewFileTemplate("properties", "Properties", FileType.PROPERTIES, "application.properties", "text/plain", "# 应用配置\n"),
            NewFileTemplate("dotenv", "环境变量 (.env)", FileType.DOTENV, ".env", "text/plain", "# 本地环境变量\n"),
            NewFileTemplate("graphql", "GraphQL", FileType.GRAPHQL, "schema.graphql", "text/plain", "type Query {\n}\n"),
            NewFileTemplate("proto", "Protocol Buffers", FileType.PROTOBUF, "schema.proto", "text/plain", "syntax = \"proto3\";\n\npackage app;\n"),
            NewFileTemplate("latex", "LaTeX", FileType.LATEX, "document.tex", "text/x-tex", "\\documentclass{article}\n\\begin{document}\n\\end{document}\n"),
            NewFileTemplate("plain-text", "纯文本", FileType.PLAIN_TEXT, "notes.txt", "text/plain", ""),
        )

        val default: NewFileTemplate = options.first()

        fun find(id: String): NewFileTemplate = options.firstOrNull { it.id == id } ?: default
    }
}
