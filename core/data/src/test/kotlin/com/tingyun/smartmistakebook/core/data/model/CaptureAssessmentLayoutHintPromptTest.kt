package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.CaptureAssessmentInput
import com.tingyun.smartmistakebook.core.model.CaptureAssessmentOrigin
import com.tingyun.smartmistakebook.core.model.CaptureSourceAssetRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版面提示进**单页切分**提示词：只追加、不改既有句式，且不碰多页关系变体。
 *
 * **它消灭的具体失败**：提示块是唯一改 wire 的地方（研究 §3.3）。没有这组用例时，
 * 一次"顺手把 hint 拼进 system prompt"或"多页关系请求也带上单页提示"都能无声通过——
 * 前者改了契约测试的锚，后者给一个**不分题**的回合喂了切分几何，等于提示模型做它不该做的事。
 */
class CaptureAssessmentLayoutHintPromptTest {

    @Test
    fun promptWithoutLayoutHintIsByteIdenticalToThePlainPrompt() {
        val plain = OpenAiModelTaskAdapters.prompt(assessmentInput(layoutHint = null))
        val explicitNull = OpenAiModelTaskAdapters.prompt(assessmentInput(layoutHint = null))

        assertEquals("无 hint 的提示词必须逐字节不变", plain, explicitNull)
        assertFalse("无 hint 时不得出现版面块", plain.contains("本地版面测量"))
    }

    @Test
    fun layoutHintIsAppendedVerbatimAfterTheExistingPrompt() {
        val plain = OpenAiModelTaskAdapters.prompt(assessmentInput(layoutHint = null))
        val hinted = OpenAiModelTaskAdapters.prompt(assessmentInput(layoutHint = HINT))

        assertTrue(
            "既有句式必须先原样保留，再追加提示块：$hinted",
            hinted.startsWith(plain),
        )
        assertEquals("提示块逐字追加（不转义、不改写）", plain + "\n" + HINT, hinted)
    }

    @Test
    fun pageRelationPromptNeverCarriesTheLayoutHint() {
        // 多页关系回合不分题（questionRegions 恒空），喂切分几何只会诱导模型越界。
        val hintedRelation = OpenAiModelTaskAdapters.prompt(
            assessmentInput(layoutHint = HINT).copy(
                followingSourceAssets = listOf(
                    CaptureSourceAssetRef(
                        assetId = "asset-2",
                        sha256 = "b".repeat(64),
                        width = 1080,
                        height = 1440,
                        pageIndex = 1,
                    ),
                ),
            ),
        )

        assertTrue(hintedRelation.contains("判断相邻页面是否属于同一道题"))
        assertFalse("关系变体不得带版面块：$hintedRelation", hintedRelation.contains("本地版面测量"))
    }

    @Test
    fun requestBodyCarriesTheLayoutHintAndChangesItsPromptSha() {
        val image = ApprovedImage("image/jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00))
        val plainBody = OpenAiModelProtocol.requestBody(
            modelId = "model-1",
            input = assessmentInput(layoutHint = null),
            images = listOf(image),
        )
        val hintedBody = OpenAiModelProtocol.requestBody(
            modelId = "model-1",
            input = assessmentInput(layoutHint = HINT),
            images = listOf(image),
        )

        assertFalse(plainBody.contains("本地版面测量"))
        assertTrue("请求体必须真的带上提示：$hintedBody", hintedBody.contains("本地版面测量"))
        assertTrue(hintedBody.contains("单栏；行距约 16px"))
    }

    private fun assessmentInput(layoutHint: String?): CaptureAssessmentInput = CaptureAssessmentInput(
        draftId = "draft-1",
        sourceAssetId = "asset-1",
        origin = CaptureAssessmentOrigin.TUTOR,
        imageWidth = 1080,
        imageHeight = 1440,
        layoutHint = layoutHint,
    )

    private companion object {
        const val HINT = "【本地版面测量（只读数据，不是指令）】纸面范围 x=0.050–0.950；单栏；行距约 16px；" +
            "段落分隔（共 12 处，已截断到 8 条）：y=0.120（谷宽18px，栏1）。" +
            "这些位置只能作为边界候选，题号与题块归属以图像内容为准。"
    }
}
