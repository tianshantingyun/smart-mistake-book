package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 用户可见的 AI 显式标识（《人工智能生成合成内容标识办法》第四条："在生成合成内容或者
 * 交互场景界面中添加可被用户明显感知的标识"）。
 *
 * **只渲染调用方给的判据**：本组件不知道哪段内容是模型产出的，也不猜。生产判据分别是
 * - 讲题回复 / 生成配图：这些界面的内容本身就是模型输出，调用即标 [AI_GENERATED_LABEL]；
 * - 讲解材料：材料侧 schema 4 的 `contentAttribute` 是封闭枚举（当前唯一取值
 *   `AI_SYNTHESIZED`，见 `KnowledgeMaterialContentAttribute` 与
 *   `ReviewedTeachingMaterialSidecarJsonCodec.validateMaterialProvenance`），标
 *   [AI_ORGANIZED_LABEL]。
 *
 * 措辞按 R3 §4 硬规则统一用"AI 生成/整理"，不用"智能辅助""由模型优化"等弱化表述。
 * 标识**没有关闭开关**（第四条为义务性规定）。
 */
const val AI_GENERATED_LABEL: String = "AI 生成"

/** 讲解材料（AI 从教辅/讲义/扫描件整理）的标识文案（KD-29）。 */
const val AI_ORGANIZED_LABEL: String = "AI 整理"

/** 材料卡下方的一句说明：标识是事实，不是"免责声明"（KD-29 裁定的最小 UI 形态）。 */
const val AI_ORGANIZED_NOTE: String = "本材料由 AI 整理，请以教材和老师讲授为准。"

/** 一个小号描边角标；[modifier] 由调用方挂 `testTag` 与布局。 */
@Composable
fun AiContentBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = JadeSoft,
        contentColor = JadeDark,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Jade),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
