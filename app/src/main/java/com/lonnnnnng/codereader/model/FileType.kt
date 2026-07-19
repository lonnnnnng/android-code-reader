package com.lonnnnnng.codereader.model

import java.util.Locale

/**
 * 阅读器支持的文本类型及其 TextMate scope。
 *
 * @author long
 */
enum class FileType(
    val displayName: String,
    val scopeName: String?,
    private val extensions: Set<String> = emptySet(),
    private val exactNames: Set<String> = emptySet(),
    val markdown: Boolean = false,
) {
    JAVA("Java", "source.java", setOf("java")),
    AIDL("AIDL", "source.java", setOf("aidl")),
    KOTLIN("Kotlin", "source.kotlin", setOf("kt", "kts")),
    CLOJURE("Clojure", "source.clojure", setOf("clj", "cljs", "cljc", "edn")),
    SCALA("Scala / SBT", "source.scala", setOf("scala", "sc", "sbt")),
    PYTHON("Python", "source.python", setOf("py", "pyw")),
    GO("Go", "source.go", setOf("go")),
    GO_MODULE("Go Module", "source.go.mod", exactNames = setOf("go.mod", "go.work")),
    RUST("Rust", "source.rust", setOf("rs")),
    DART("Dart", "source.dart", setOf("dart")),
    SWIFT("Swift", "source.swift", setOf("swift")),
    ZIG("Zig", "source.zig", setOf("zig")),
    JULIA("Julia", "source.julia", setOf("jl")),
    FORTRAN("Fortran", "source.fortran", setOf("f", "for", "f77", "f90", "f95", "f03", "f08")),
    R("R", "source.r", setOf("r")),
    PHP("PHP", "source.php", setOf("php", "phtml")),
    BLADE("Blade", "text.html.php.blade", setOf("blade")),
    PERL("Perl", "source.perl", setOf("pl", "pm", "t")),
    LUA("Lua", "source.lua", setOf("lua")),
    LISP("Lisp", "source.lisp", setOf("lisp", "lsp", "el")),
    C("C", "source.c", setOf("c", "h")),
    CPP("C++", "source.cpp", setOf("cc", "cpp", "cxx", "hpp", "hh", "hxx")),
    OBJECTIVE_C("Objective-C", "source.objc", setOf("m")),
    OBJECTIVE_CPP("Objective-C++", "source.objcpp", setOf("mm")),
    ASSEMBLY("Assembly", "source.asm.x86_64", setOf("asm")),
    CSHARP("C#", "source.cs", setOf("cs")),
    FSHARP("F#", "source.fsharp", setOf("fs", "fsx")),
    VISUAL_BASIC("Visual Basic", "source.vb", setOf("vb")),
    JAVASCRIPT("JavaScript", "source.js", setOf("js", "mjs", "cjs")),
    JAVASCRIPT_REACT("JavaScript React", "source.js.jsx", setOf("jsx")),
    TYPESCRIPT("TypeScript", "source.ts", setOf("ts", "mts", "cts")),
    TYPESCRIPT_REACT("TypeScript React", "source.tsx", setOf("tsx")),
    VUE("Vue", "source.vue", setOf("vue")),
    SVELTE("Svelte", "source.svelte", setOf("svelte")),
    ASTRO("Astro", "source.astro", setOf("astro")),
    HTML("HTML", "text.html.basic", setOf("html", "htm")),
    CSS("CSS", "source.css", setOf("css")),
    SCSS("SCSS", "source.css.scss", setOf("scss")),
    SASS("Sass", "source.sass", setOf("sass")),
    LESS("Less", "source.css.less", setOf("less")),
    ERB("ERB", "text.html.erb", setOf("erb")),
    TWIG("Twig", "text.html.twig", setOf("twig")),
    RAZOR("Razor", "text.aspnetcorerazor", setOf("razor", "cshtml")),
    XML(
        "XML / .NET Project",
        "text.xml",
        setOf(
            "xml", "xsd", "xsl", "xslt", "svg",
            "csproj", "fsproj", "vbproj", "vcxproj", "slnx", "props", "targets", "nuspec", "pubxml",
            "xaml", "resx", "plist", "storyboard", "xib",
        ),
        setOf("App.config", "Web.config", "NuGet.config"),
    ),
    SQL("SQL", "source.sql", setOf("sql")),
    PROTOBUF("Protocol Buffers", "source.proto", setOf("proto")),
    GRAPHQL("GraphQL", "source.graphql", setOf("graphql", "gql")),
    PRISMA("Prisma", "source.prisma", setOf("prisma")),
    TOML(
        "TOML",
        "source.toml",
        setOf("toml"),
        setOf("Cargo.lock", "poetry.lock", "uv.lock", "Pipfile"),
    ),
    PROPERTIES("Properties", "source.properties", setOf("properties")),
    DOTENV("Dotenv", "source.dotenv", setOf("env")),
    INI(
        "Properties / Config",
        "source.ini",
        setOf("ini", "cfg", "editorconfig", "npmrc"),
    ),
    JSON(
        "JSON",
        "source.json",
        setOf("json", "jsonc", "geojson"),
        setOf("composer.lock", "Pipfile.lock"),
    ),
    YAML("YAML", "source.yaml", setOf("yaml", "yml", "ymal"), setOf("pubspec.lock")),
    MARKDOWN("Markdown", "text.html.markdown", setOf("md", "markdown", "mdown", "mkd"), markdown = true),
    MDX("MDX", "source.mdx", setOf("mdx")),
    LATEX("LaTeX", "text.tex", setOf("tex", "sty", "cls")),
    SHELL("Shell", "source.shell", setOf("sh", "bash", "zsh", "fish"), setOf("gradlew", "mvnw", "configure")),
    BATCH("Batch", "source.batchfile", setOf("bat", "cmd")),
    POWERSHELL("PowerShell", "source.powershell", setOf("ps1", "psm1", "psd1")),
    SMALI("Smali", "source.smali", setOf("smali")),
    RUBY(
        "Ruby",
        "source.ruby",
        setOf("rb", "rake"),
        setOf("Gemfile", "Rakefile", "Podfile", "Vagrantfile", "Fastfile", "Appfile", "Deliverfile", "config.ru"),
    ),
    GROOVY("Groovy / Gradle", "source.groovy", setOf("groovy", "gradle"), setOf("Jenkinsfile")),
    CMAKE("CMake", "source.cmake", setOf("cmake"), setOf("CMakeLists.txt")),
    HCL("HCL", "source.hcl", setOf("hcl")),
    TERRAFORM("Terraform", "source.hcl.terraform", setOf("tf", "tfvars"), setOf(".terraformrc")),
    NIX("Nix", "source.nix", setOf("nix")),
    NGINX("Nginx", "source.nginx", exactNames = setOf("nginx.conf")),
    IGNORE(
        "Ignore Rules",
        "source.ignore",
        setOf("ignore"),
        exactNames = setOf(
            ".gitignore", ".dockerignore", ".npmignore", ".eslintignore", ".prettierignore",
            ".stylelintignore", ".rgignore", ".ignore",
        ),
    ),
    PROGUARD(
        "ProGuard / R8",
        "source.proguard",
        setOf("proguard"),
        setOf("proguard-rules.pro", "consumer-rules.pro", "proguard-project.txt", "proguard.pro"),
    ),
    DOCKERFILE("Dockerfile", "source.dockerfile", exactNames = setOf("Dockerfile", "Containerfile")),
    MAKEFILE("Makefile", "source.makefile", exactNames = setOf("Makefile", "GNUmakefile")),
    SOLUTION(".NET Solution", "source.ini", setOf("sln")),
    PLAIN_TEXT("Plain text", null, setOf("txt", "log", "csv")),
    ;

    companion object {
        private val exactNameIndex = entries
            .flatMap { type -> type.exactNames.map { name -> name.lowercase(Locale.ROOT) to type } }
            .toMap()

        private val extensionIndex = entries
            .flatMap { type -> type.extensions.map { extension -> extension.lowercase(Locale.ROOT) to type } }
            .toMap()

        fun detect(fileName: String): FileType {
            val normalizedName = fileName.substringAfterLast('/').lowercase(Locale.ROOT)
            exactNameIndex[normalizedName]?.let { return it }

            // 多段后缀的最后一段不足以表达真实格式，必须在普通扩展名判断前按工程约定识别。
            when {
                normalizedName.startsWith(".env.") -> return DOTENV
                normalizedName.endsWith(".blade.php") -> return BLADE
                normalizedName.endsWith(".exe.config") || normalizedName.endsWith(".dll.config") -> return XML
                normalizedName.endsWith(".vcxproj.filters") || normalizedName.endsWith(".vcxproj.user") -> return XML
            }

            // `.env`、`.npmrc` 等点文件没有常规扩展名，需要把完整名称作为后缀再判断。
            if (normalizedName.startsWith('.') && normalizedName.count { it == '.' } == 1) {
                extensionIndex[normalizedName.removePrefix(".")]?.let { return it }
            }

            return extensionIndex[normalizedName.substringAfterLast('.', "")] ?: when {
                normalizedName.startsWith("dockerfile.") -> DOCKERFILE
                normalizedName.startsWith("containerfile.") -> DOCKERFILE
                normalizedName.startsWith("makefile.") -> MAKEFILE
                else -> PLAIN_TEXT
            }
        }
    }
}
