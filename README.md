# 源码阅读器

原生 Android 源码与 Markdown 阅读器，默认只读，可按文件切换编辑并保存。

## 当前能力

- `ACTION_VIEW`、`ACTION_EDIT`、`ACTION_SEND` 外部文件入口。
- SAF 单文件和目录/项目浏览。
- ZIP 安全解压和项目浏览。
- 公共 HTTPS Git 仓库浅克隆。
- 最多 5000 条目的可折叠项目树，目录、ZIP、Git 和内置 Markdown 示例使用同一套项目索引。
- 文件内搜索、项目全局搜索、快速文件切换和最多 6 个可恢复的最近项目。
- 多文件标签页；每个标签页独立保留草稿、编辑状态和 Markdown 预览状态。
- 阅读页采用文件名/类型/状态两级标题、编辑器式活动标签和 48dp 上下文工具栏，搜索栏原位展开以减少正文区域跳动。
- Sora Editor + TextMate 语法高亮。
- 高对比亮色和 Darcula 暗色代码主题，支持运行时切换并持久化。
- 跳转到行、11-24 sp 字号设置和源码自动换行。
- Markdown 源码/预览切换，支持常用语法、表格、任务列表和脚注。
- Markdown 代码块语法高亮、KaTeX 数学公式和 Mermaid 流程图，全部使用 APK 内置资源离线渲染。
- Markdown 目录跳转和代码块一键复制。
- 超过 1 MB 的文件自动进入只读分段模式，每次追加约 256K 字符，避免一次性加载超大文本。
- Debug 构建包含 78 个语法覆盖文件；Release APK 不携带 QA 工具和多语言测试工程，只保留一个 Markdown 功能示例。
- 覆盖 Swift、Objective-C、Scala、Dart、Terraform/HCL、Protobuf、GraphQL、Prisma、CMake、Razor、Svelte、Astro、MDX、Nginx、Go Module、ProGuard 等工程文件。
- Pixel_9 模拟器已通过 78/78 语法 token 验证；当前项目禁止使用 Redmi 真机做验证。

模拟器验证结果见 [模拟器验证报告](docs/模拟器验证报告.md)。

## 构建

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain
```

应用 ID：`com.lonnnnnng.codereader`

最低 Android：API 24；目标 Android：API 36。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。
