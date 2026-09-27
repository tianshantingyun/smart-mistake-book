package com.tingyun.smartmistakebook

import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 学习掌握页空态判定（D-Q3，消费点⑤）。
 *
 * 消灭的失败：改前掌握总览的计数恒为 0 时，页面一律说"还没有学习记录"——首装期
 * （知识内容还在后台就位、知识点状态因此还没建立）这句话是假的，学生以为自己的记录没了。
 */
class LearningMasteryEmptyReasonTest {

    @Test
    fun `summaries present means no empty state at all`() {
        listOf(
            KnowledgeBaseAvailability.Preparing,
            KnowledgeBaseAvailability.Ready,
            KnowledgeBaseAvailability.Unavailable("startup:knowledge:1"),
        ).forEach { availability ->
            assertEquals(
                "有内容可显示时不得渲染空态（$availability）",
                LearningMasteryEmptyReason.NONE,
                learningMasteryEmptyReason(
                    summaryCount = 3,
                    hasLearningEvidence = true,
                    knowledgeBaseAvailability = availability,
                ),
            )
        }
    }

    @Test
    fun `records without knowledge content is preparing not missing records`() {
        assertEquals(
            LearningMasteryEmptyReason.KNOWLEDGE_PREPARING,
            learningMasteryEmptyReason(
                summaryCount = 0,
                hasLearningEvidence = true,
                knowledgeBaseAvailability = KnowledgeBaseAvailability.Preparing,
            ),
        )
        assertEquals(
            "安装失败同样是'还没准备好'，不能说成'你没有记录'",
            LearningMasteryEmptyReason.KNOWLEDGE_PREPARING,
            learningMasteryEmptyReason(
                summaryCount = 0,
                hasLearningEvidence = true,
                knowledgeBaseAvailability =
                    KnowledgeBaseAvailability.Unavailable("startup:knowledge:9"),
            ),
        )
    }

    @Test
    fun `a brand new student still hears there are no records yet`() {
        // 一条记录都没有时"还没有学习记录"是真话，且比"准备中"更有用：
        // 内容就绪与否都不会改变这个页面此刻该说的话。
        assertEquals(
            LearningMasteryEmptyReason.NO_RECORDS,
            learningMasteryEmptyReason(
                summaryCount = 0,
                hasLearningEvidence = false,
                knowledgeBaseAvailability = KnowledgeBaseAvailability.Preparing,
            ),
        )
    }

    @Test
    fun `records with content ready keep the existing no records copy`() {
        // 就绪却不显示 → 旧行为保持不变（这条不是本次要改的病灶）。
        assertEquals(
            LearningMasteryEmptyReason.NO_RECORDS,
            learningMasteryEmptyReason(
                summaryCount = 0,
                hasLearningEvidence = true,
                knowledgeBaseAvailability = KnowledgeBaseAvailability.Ready,
            ),
        )
    }
}
