package com.tingyun.smartmistakebook.core.export

import com.tingyun.smartmistakebook.core.model.VisualRole

/**
 * 导出卷面上 AI 生成图的**用户可见显式标识**（T7，《人工智能生成合成内容标识办法》第四条
 * 第二款：提供下载、导出功能时须确保文件含显式标识——R3 差距清单 G3，P1；即 4B 批 2
 * F1-1 登记的"PDF 重绘丢 EXIF、显式未做"的重开项）。
 *
 * 判据 = 视觉角色单源（core:model `VisualRole`）：`CLEAN_IMAGE`（模型净化/重绘）与
 * `TUTOR_EXPLANATION`（讲题工具讲解图）只由生成路径产出（三个生产写入点全部携带
 * `GeneratedFigureProvenance` 隐式标识，见 4B 批 2），因此凡这两个角色——含旧调用方
 * `role=null` 按题目图处理的既有约定（同 `questionImageLabel` 口径）——都在图下加一行
 * 固定文案。文案含"AI"与"生成"两要素（GB 45438-2025 对文字提示的要素口径，二手转述，
 * 见 R3 §2.2 V1 待验证；此处为图注文字而非图片内水印，不受 5% 水印规格约束）。
 *
 * **不伪报**：`QUESTION_DIAGRAM` 是"题目自带的示意图"（`VisualRole` KDoc），当前代码无
 * 生产写入点、无法证明它出自模型——不加标识。宁缺勿假：把学生原题/OCR 转录（转录按
 * R3 §3.1 不是生成内容）标成"AI 生成"是伪标识，同样违反《办法》第十条不得伪造标识。
 * **逐图标识**：一卷混排 AI 图与原文时，标识跟着每一张 AI 图走，而不是把整份卷子标成
 * AI 生成。
 *
 * **渲染时派生**：图注不进 [MistakePdfExportInput]（不进 `inputSha256` 指纹、不进草稿
 * 存储），同一输入换渲染实现不会换缓存键；消灭的失败是"导出文件里的生成图离开 App 后
 * 无任何 AI 标识"（隐式 EXIF 在 PDF/DOCX 重绘时丢失）。
 */
internal const val AI_GENERATED_IMAGE_CAPTION = "本图由 AI 生成"

/** 这张图是否需要显式 AI 标识（判据与边界见 [AI_GENERATED_IMAGE_CAPTION] 的 KDoc）。 */
internal fun isAiGeneratedExportImage(role: String?): Boolean = when (role) {
    null,
    VisualRole.CLEAN_IMAGE.name,
    VisualRole.TUTOR_EXPLANATION.name,
    -> true

    else -> false
}
