# Markdown 功能示例

这份文档展示源码阅读器支持的常用 Markdown 能力，所有内容均可离线渲染。

## 文本与列表

支持 **粗体**、*斜体*、~~删除线~~、`inline code` 和 [GitHub 链接](https://github.com/)。

- 源码与配置文件语法高亮
- 数学公式
- Mermaid 流程图
  - 支持嵌套列表
  - 支持任务列表

- [x] CommonMark 基础语法
- [x] 表格和代码块
- [x] 数学公式与流程图

## 表格

| 能力 | 渲染引擎 | 离线支持 |
| --- | --- | :---: |
| Markdown | markdown-it | 是 |
| 数学公式 | KaTeX | 是 |
| 流程图 | Mermaid | 是 |

## 代码高亮

```java
public final class UserService {
    public String findUser(long userId) {
        return "user-" + userId;
    }
}
```

```json
{
  "name": "android-code-reader",
  "enabled": true,
  "languages": ["java", "python", "go", "rust"]
}
```

## 数学公式

行内公式：$E = mc^2$。

块级公式：

$$
\int_{0}^{\infty} e^{-x^2}\,dx = \frac{\sqrt{\pi}}{2}
$$

## Mermaid 流程图

```mermaid
flowchart TD
    A[打开文件] --> B{识别文件类型}
    B -->|Markdown| C[离线渲染]
    B -->|源代码| D[TextMate 高亮]
    C --> E[公式与流程图]
    D --> F[只读查看或编辑]
```

## 引用与脚注

> 阅读器默认以只读方式打开文件，确认来源可写后才能进入编辑模式。

离线预览不会从网络加载渲染脚本。[^offline]

[^offline]: Markdown-it、highlight.js、KaTeX 和 Mermaid 均打包在 APK 内。
