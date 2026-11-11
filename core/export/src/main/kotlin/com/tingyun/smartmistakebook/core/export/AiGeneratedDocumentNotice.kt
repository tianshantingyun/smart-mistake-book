package com.tingyun.smartmistakebook.core.export

/**
 * 导出文件里**用户可见**的文档级 AI 说明（《人工智能生成合成内容标识办法》第四条：
 * 提供下载、导出时文件须含显式标识）。
 *
 * 为什么需要它：AI 图已有逐图图注 [AI_GENERATED_IMAGE_CAPTION]，但**模型产出的文字**
 * （`MODEL_DOCUMENT_PARSE` 解析文本、以及 6B-3 自然语言改卷写进卷面的文字）此前只有文件级
 * 隐式标识（PDF Info `/AIGC`、DOCX `docProps/custom.xml` 的 `AIGC` 属性），人打开文件
 * 看不出来。判据沿用同一次 `PdfAigcContentPolicy.shouldLabel` 的模型文本项：只在
 * [MistakePdfExportInput.hasModelParsedText] 为真时写，纯用户内容不伪标（第十条）。
 *
 * 措辞按 R3 §4 的硬规则统一用"AI 生成"，不用"智能辅助"等弱化表述；这是**整份文件**的
 * 说明，不冒充"逐块标出哪一段"——按块级 provenance 标注需要块级来源位，当前导出模型没有。
 */
internal const val AI_GENERATED_DOCUMENT_NOTICE = "本文件含 AI 生成内容"
