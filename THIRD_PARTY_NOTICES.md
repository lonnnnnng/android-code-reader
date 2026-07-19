# Third-Party Notices

## Sora Editor

- Repository: https://github.com/Rosemoe/sora-editor
- Version: `0.24.6`
- Usage: Gradle dependencies `editor`, `language-textmate`, `oniguruma-native`
- License: LGPL-2.1-or-later

## Markdown-it

- Repository: https://github.com/markdown-it/markdown-it
- Version: `14.3.0`
- Usage: offline Markdown parsing in the preview WebView
- License: MIT

## Markdown-it Task Lists

- Repository: https://github.com/revin/markdown-it-task-lists
- Version: `2.1.1`
- Usage: GitHub-style task lists
- License: ISC

## Markdown-it Footnote

- Repository: https://github.com/markdown-it/markdown-it-footnote
- Version: `4.0.0`
- Usage: Markdown footnotes
- License: MIT

## Highlight.js

- Repository: https://github.com/highlightjs/highlight.js
- Version: `11.11.1`
- Usage: fenced code block syntax highlighting
- License: BSD-3-Clause

## KaTeX

- Repository: https://github.com/KaTeX/KaTeX
- Version: `0.18.0`
- Usage: inline and block mathematical formulas
- License: MIT

## Mermaid

- Repository: https://github.com/mermaid-js/mermaid
- Version: `11.16.0`
- Usage: Mermaid diagram and flowchart rendering
- License: MIT

The bundled browser assets and full license texts are stored under
`app/src/main/assets/markdown/`.

## Eclipse JGit

- Repository: https://github.com/eclipse-jgit/jgit
- Version: `7.7.0.202606012155-r`
- Usage: public HTTPS Git clone
- License: Eclipse Distribution License 1.0

## Squircle CE Language Assets

- Repository: https://github.com/massivemadness/Squircle-CE
- Commit: `a16e1b2773938d12c33bcdc12be3415ba6ff8c9c`
- Upstream path: `feature-editor/impl/src/main/assets/languages/`
- Local path: `app/src/main/assets/languages/`
- Usage: copied TextMate grammars and language configurations; `languages.json` only received the Sora-required top-level object wrapper
- License: Apache-2.0 at repository level; individual grammar provenance is retained inside grammar files

The complete Apache License 2.0 text is available in `LICENSE`.

## tm-grammars

- Repository: https://github.com/shikijs/textmate-grammars-themes
- Package: `tm-grammars@1.31.15`
- Usage: additional TextMate grammars for mainstream source, framework, template, and infrastructure files
- License: grammar-specific permissive licenses recorded by the package

The complete package `LICENSE` and grammar provenance `NOTICE` are bundled under
`app/src/main/assets/languages/tm-grammars/`.

## TextMate INI Bundle

- Repository: https://github.com/textmate/ini.tmbundle
- Usage: `.properties` TextMate grammar
- License: permissive TextMate bundle license

The license text is bundled at `app/src/main/assets/languages/properties/LICENSE`.
