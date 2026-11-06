package com.tingyun.smartmistakebook.core.model.pdf

/**
 * "是否需要写 `/AIGC`"的纯判据（R1 §3.7；WS1 item 6）。
 *
 * 两类内容**任一命中**就必须打标：
 * 1. AI 生成图片（调用方用 `core:export` 的 `isAiGeneratedExportImage(role)` 判定后传入）；
 * 2. **模型解析文本**（`CapturedQuestionDocument.blockEvidence` 里出现
 *    `QuestionBlockProvenance.MODEL_DOCUMENT_PARSE`）。
 *
 * 第 2 项是 KD-75 的第二半：此前判据只看图片，纯模型解析文本/含解析证据的已提交文档会漏标。
 * 判据刻意保守：只要证据里**出现过**模型解析 provenance 就打标，即使后来被用户修正——
 * 多标一个 `Label=1` 的风险低于漏标（R1 §3.7）。
 *
 * 不该打标的输入（调用方传 false）：纯用户录入/OCR 转录/备注；`QUESTION_DIAGRAM` 且无其它
 * AI 内容；程序化 JS 图形（确定性代码绘制，不是模型推理）。这是《办法》第十条"不得伪造标识"
 * 的边界，不能为了"更安全"把所有导出都打标。
 *
 * 本对象零 Android 依赖；`core:export` 是热区，本批只落判据，不改进导出调用点（见结果登记）。
 */
object PdfAigcContentPolicy {
    /** Invalidate image-only export caches after adding parsed-text provenance. */
    const val GENERATION: Int = 2

    /** 与 `core:model` 的 `QuestionBlockProvenance.MODEL_DOCUMENT_PARSE` 同名对齐。 */
    const val MODEL_DOCUMENT_PARSE_PROVENANCE: String = "MODEL_DOCUMENT_PARSE"

    /**
     * @param hasAiGeneratedImage 任一导出图片角色命中 AI 图（`null`/`CLEAN_IMAGE`/
     *   `TUTOR_EXPLANATION` 的既有口径），或 `solutionImages` 非空。
     * @param hasModelParsedText 提交文档块证据里出现过 `MODEL_DOCUMENT_PARSE`。
     */
    fun shouldLabel(
        hasAiGeneratedImage: Boolean,
        hasModelParsedText: Boolean,
    ): Boolean = hasAiGeneratedImage || hasModelParsedText

    /** 从 provenance 名称集合派生"含模型解析文本"；大小写敏感，与枚举 `name` 一致。 */
    fun hasModelParsedText(provenanceNames: Iterable<String>): Boolean =
        provenanceNames.any { it == MODEL_DOCUMENT_PARSE_PROVENANCE }
}
