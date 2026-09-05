# Android 16 instrumentation 类加载器与 coroutines-test 兼容问题排查记录

> 排查时间：2026-09-05～09-06 CST
> 排查设备：Redmi Note 8 Pro（Android 16 / API 36，arm64-v8a）；对照环境：`ACRR_Smoke`（Pixel 7 配置，API 35，arm64-v8a）模拟器
> 影响范围：仅 instrumentation 测试（androidTest 源集），正式包运行时行为不受影响
> 修复提交：`47b4b20`（`ServiceLoaderBridgeRunner`）

## 结论

Android 16 起，instrumentation 的测试 APK 不再并入应用的 `PathClassLoader`，改由独立的子类加载器加载。kotlinx-coroutines-test 1.10.2 的 `TestScopeImpl.enter()` 在启动时会校验 `ExceptionCollector` 出现在 `CoroutineExceptionHandler` 的 ServiceLoader 加载结果中，而该校验依赖"测试 APK 与应用 APK 同池"的旧布局假设：测试加载器里的 `ExceptionCollectorAsService` 对应用侧 ServiceLoader 不可见，导致所有走 `runTest` 的 Compose 用例（共 52 项）在 Android 16 真机上瞬间失败。

修复方式为自定义 `AndroidJUnitRunner`（`ServiceLoaderBridgeRunner`，仅 androidTest 源集），在测试进程启动时把测试加载器中的 `ExceptionCollectorAsService` 实例反射补进应用加载器的 `platformExceptionHandlers` 集合。修复后 Redmi Note 8 Pro 真机 79 项用例恢复 76 项通过，其余 3 项为与本问题无关的既有问题（见文末）。

## 现象

- `connectedDebugAndroidTest` 在 Redmi Note 8 Pro（Android 16）上 79 项用例失败 52 项，全部集中在 Compose UI 套件（MarkdownPreview、Settings、CodeEditorEditing、DialogLayout、SampleProjectUi、ThemeSwitch、BinaryFileScreen 等），失败耗时均在毫秒级。
- 数据层用例（GitClone、DraftRecovery、DocumentExport、TextEncoding、SafeSave、AppReleaseParser 等）全部通过。
- 统一错误栈：

```text
java.lang.IllegalStateException: Exception handler was not found via a ServiceLoader
    at kotlinx.coroutines.internal.CoroutineExceptionHandlerImplKt.ensurePlatformExceptionHandlerLoaded(CoroutineExceptionHandlerImpl.kt:25)
    at kotlinx.coroutines.test.TestScopeImpl.enter(TestScope.kt:230)
    at androidx.compose.ui.test.AndroidComposeUiTestEnvironment.runTest(ComposeUiTest.android.kt:585)
```

## 排查过程与证据

1. **反汇编 coroutines 类（kotlinx-coroutines-core/test/android 1.10.2）确认抛错条件**：
   - `CoroutineExceptionHandlerImplKt` 的静态初始化通过 `ServiceLoader.load(CoroutineExceptionHandler::class.java, CoroutineExceptionHandler::class.java.classLoader).iterator().asSequence().toList()` 加载全局处理器集合；
   - `ensurePlatformExceptionHandlerLoaded(callback)` 执行 `check(callback in platformExceptionHandlers)`，即按传入实例的 `equals` 判断；
   - `ExceptionCollector.equals` 与 `ExceptionCollectorAsService.equals` 互相做 `instanceof` 等价处理——只要 ServiceLoader 实际加载到测试侧的 `ExceptionCollectorAsService`，校验即可通过。
2. **临时诊断用例在真机取证**：`CoroutineExceptionHandler::class.java.classLoader` 为只含应用 `base.apk` 的 `PathClassLoader`；`getResources("META-INF/services/kotlinx.coroutines.CoroutineExceptionHandler")` 只枚举到应用 APK 的 `AndroidExceptionPreHandler`；测试侧 `ExceptionCollectorAsService` 类虽可通过子加载器 `Class.forName` 加载，但不在父加载器的 ServiceLoader 视野内。
3. **API 35 模拟器对照实验**：同一代码、同一构建，`MarkdownPreviewInstrumentedTest` 在 API 35 上 11 项仅 1 项失败（软件渲染导致的 `waitUntil` 超时抖动），排除了 AGP 9.2.1 / Compose BOM 2025.12.00 / 依赖版本因素，锁定为 Android 16 平台行为变化。

## 根因机制

- 旧布局：`ActivityThread` 将测试 APK 与应用 APK 放入同一个 `PathClassLoader`，`getResources` 同时枚举两个 APK 的 services 文件，`platformExceptionHandlers` 同时包含 `ExceptionCollectorAsService` 与 `AndroidExceptionPreHandler`，校验通过。
- Android 16 新布局：测试 APK 由子类加载器加载（父加载器仅含应用 APK）。类解析沿用父优先委托，因此 `TestScopeImpl` 解析到的仍是应用加载器的 `CoroutineExceptionHandlerImplKt`；但其 `platformExceptionHandlers` 通过应用加载器做 ServiceLoader 枚举，永远看不到测试 APK 的 services 文件，校验结构性失败。
- 注入时再遇到一个细节：Android 16 上 ServiceLoader 只命中 1 个 provider，Kotlin `Sequence.toList()` 的单元素优化返回 `Collections$SingletonList`（不可变，`add` 抛 `UnsupportedOperationException`），因此不能原地 `add`，必须整体替换 `platformExceptionHandlers` 字段为新的可变列表（ART 上 `setAccessible(true)` 后对 `static final` 字段执行 `field.set` 可行，已在真机验证）。

## 修复方案

`app/src/androidTest/java/com/lonnnnnng/codereader/ServiceLoaderBridgeRunner.kt`：

1. 在 `onCreate`（任何测试执行前）通过 `Class.forName` 定位应用加载器的 `CoroutineExceptionHandlerImplKt`；
2. 读取 `platformExceptionHandlers`，若已包含 `ExceptionCollectorAsService`（旧平台或上游未来修复）则跳过，保持零副作用；
3. 否则构建 `ArrayList`（原元素 + 测试加载器实例化的 `ExceptionCollectorAsService`）并整体替换字段值；
4. 全程 try/catch，失败时仅记录属性与日志，不阻塞测试进程；执行结果同步写入 `acrr.coroutine.bridge` 系统属性便于排查。

桥接仅存在于 androidTest 源集与 instrumented 运行期：正式签名词零变化，用户日常使用 debug 包时也不经过 runner，行为无任何改变。

## 修复后验证

| 指标 | 修复前 | 修复后 |
| --- | --- | --- |
| Redmi Note 8 Pro（Android 16）全量 | 79 项失败 52 项 | 79 项失败 3 项 |
| MarkdownPreviewInstrumentedTest | 11/11 失败 | 10/11 通过 |
| API 35 模拟器 | 未执行（历史记录 91/91 通过） | 11 项中 10 项通过 |

其余 3 项失败均已通过"暂存改动 → 原始 HEAD 基线 → API 35 模拟器复跑"确认与本修复无关：

1. `MarkdownPreviewInstrumentedTest.sourceAndPreviewKeepTheSameReadingPosition`：10 秒 `waitUntil` 超时，基线可复现，属滚动位置同步对渲染性能敏感的既有抖动；
2. `SyntaxCoverageInstrumentedTest.allDeclaredSamplesLoadGrammarAndProduceSemanticTokens`：全量语法样本加载超出应用堆上限（真机 256MB / 模拟器 192MB 均复现 OOM），属既有内存峰值问题；
3. `ThemeSwitchInstrumentedTest.gitCloneDialogUsesActiveDarkTheme`：仅在上述重内存用例之后同进程执行时级联 OOM，单独执行通过。

## 后续建议

1. 语法全覆盖用例按语言分组分批加载，或对样本集分片，消除小堆设备的 OOM；考虑在报告中记录各 AVD 的堆上限。
2. 阅读位置同步用例评估提高 `waitUntil` 超时或拆分断言步骤，降低慢设备抖动。
3. 跟踪 kotlinx-coroutines 上游对本问题的修复（校验逻辑未考虑测试 APK 类加载器隔离），若上游发布修复可移除桥接 runner，恢复默认 `AndroidJUnitRunner`。
