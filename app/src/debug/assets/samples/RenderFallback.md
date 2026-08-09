# Markdown 渲染失败回退

这份文档只用于验证局部渲染失败不会让其余正文白屏。

## 缺失图片

![不存在的架构图](media/not-found.png)

## 非法公式

这里包含无法识别的公式命令：$\unknowncommand{x}$。

$$
\unknowncommand{x}
$$

## 非法流程图

```mermaid
flowchart TD
  A[开始] -->
```

## 仍可阅读的正文

即使图片、公式或流程图失败，这段正文仍然应当可见。
