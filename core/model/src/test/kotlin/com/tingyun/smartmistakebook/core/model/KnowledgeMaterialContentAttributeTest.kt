package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 材料卡的"AI 整理"角标（KD-29）当前是**无条件**显示的，理由是材料来源枚举是封闭的：
 * 只有 `AI_SYNTHESIZED` 一个取值，而 sidecar schema 4 的 `contentAttribute` 是**强制**键
 * （`ReviewedTeachingMaterialSidecarJsonCodec.validateMaterialProvenance`）——凡进入库的
 * 材料都是 AI 合成。
 *
 * 这条用例消灭的失败：将来有人给枚举加一个"人工撰写/人工审校"取值，材料卡会继续把所有
 * 材料标成"AI 整理"，把一个**真实的非 AI 内容**伪标成 AI（《办法》第十条）。加值必须同时
 * 改角标判据；本断言先红，强制那次表态。
 */
class KnowledgeMaterialContentAttributeTest {

    @Test
    fun `every material content attribute is AI synthesized or the badge judgement must change`() {
        assertEquals(
            "新增材料来源属性：必须把 AiContentBadge 的'AI 整理'改成按 contentAttribute 分支",
            listOf("AI_SYNTHESIZED"),
            KnowledgeMaterialContentAttribute.entries.map { it.name },
        )
    }
}
