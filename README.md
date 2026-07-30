# 灵阅

原生 Android 源码与 Markdown 阅读器，默认只读，可按文件切换编辑并保存。

## 当前能力

- `ACTION_VIEW`、`ACTION_EDIT`、`ACTION_SEND` 外部文件入口。
- SAF 单文件和目录/项目浏览。
- ZIP 安全解压和项目浏览。
- 公共 HTTPS Git 仓库浅克隆；5 行地址输入框完整展示长链接，克隆过程持续显示阶段、百分比和取消入口，本地目录默认使用仓库名称。
- 已克隆仓库可在项目标题栏获取最新代码；更新只接受安全快进，不自动合并或覆盖手机端保存的本地修改。
- 不设条目数量上限的可折叠项目树，目录、ZIP、Git 和内置 Markdown 示例使用同一套完整项目索引。
- 文件内搜索、项目全局搜索和快速文件切换；首页“最近打开”菜单集中管理最多 6 个可恢复项目。
- 多文件标签页；每个标签页独立保留草稿、编辑状态和 Markdown 预览状态。
- 首页、设置、项目和阅读页统一使用 56dp 紧凑标题栏，同时保留 48dp 图标触控区域；来源入口、设置分类、项目搜索和更新入口复用统一的实体色图标徽标与低层级列表容器，阅读页采用文件名/类型/状态两级标题、编辑器式活动标签和 48dp 上下文工具栏。
- Sora Editor + TextMate 语法高亮。
- 首页提供两级设置页；一级按“阅读与显示 / 应用外观 / 关于与更新”分类，二级集中配置具体选项。系统侧边返回手势会逐级回退，首页退出前显示二次确认。11-24 sp 字号、阅读背景、整体强调色和明暗模式均可实时预览并持久化。
- 设置页支持从 GitHub Releases 在线检查更新；弹框按更新说明、下载进度、操作按钮的顺序展示，并在完成 SHA-256、文件大小、应用 ID、版本号和签名证书校验后交给系统安装器。
- 应用、源码和 Markdown 统一使用手机系统字体；高对比亮色和 Darcula 暗色代码主题支持运行时切换，源码与 Markdown 共用字号和阅读背景设置。
- 跳转到行和源码自动换行。
- Markdown 源码/预览切换，支持常用语法、表格、任务列表和脚注。
- Markdown 代码块语法高亮、KaTeX 数学公式和 Mermaid 流程图，全部使用 APK 内置资源离线渲染。
- Markdown 目录跳转和代码块一键复制。
- 超过 1 MB 的文件自动进入只读分段模式，每次追加约 256K 字符，避免一次性加载超大文本。
- Debug 构建包含 78 个语法覆盖文件；Release APK 不携带 QA 工具和多语言测试工程，只保留一个 Markdown 功能示例。
- 覆盖 Swift、Objective-C、Scala、Dart、Terraform/HCL、Protobuf、GraphQL、Prisma、CMake、Razor、Svelte、Astro、MDX、Nginx、Go Module、ProGuard 等工程文件。
- Pixel_9 模拟器已通过 22 项 instrumentation 和 78/78 语法 token 验证；当前项目禁止使用 Redmi 真机做验证。

模拟器验证结果见 [模拟器验证报告](docs/模拟器验证报告.md)。

## 构建

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease --console=plain
```

应用 ID：`com.lonnnnnng.codereader`

最低 Android：API 24；目标 Android：API 36。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，应用 ID 为 `com.lonnnnnng.codereader.debug`，可与正式版并存。
