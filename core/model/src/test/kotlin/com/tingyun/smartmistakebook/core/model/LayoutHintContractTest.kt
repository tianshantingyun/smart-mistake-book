package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版面提示（`layoutHint`）的**契约锁**：它是本轮唯一改 wire 的地方，所以三个不变量必须钉死。
 *
 * 1. **不设值 = 今天**：`null` 时字段必须从编码中消失，指纹与升级前逐字节相同——否则存量
 *    任务行读回重算指纹就会漂，`ModelTaskSnapshot` 的 require 会让整批行读回即炸。
 * 2. **设值 = 一个新请求**：提示内容参与指纹；同一页换了提示就是不同请求（这是预期行为，
 *    批次 50 记录里写明）。
 * 3. **有界**：提示进 prompt，必须在 wire 预算内，超限当场拒绝而不是悄悄截断。
 */
class LayoutHintContractTest {

    @Test
    fun legacyRowsWithoutLayoutHintDecodeToNull() {
        val legacyJson = """
            {
              "type": "capture_assessment",
              "draftId": "draft-1",
              "sourceAssetId": "asset-1",
              "origin": "TUTOR",
              "imageWidth": 1080,
              "imageHeight": 1440,
              "followingSourceAssets": []
            }
        """.trimIndent()

        val decoded = ModelTaskCodec.decodeInput(legacyJson) as CaptureAssessmentInput

        assertNull(decoded.layoutHint)
        assertEquals("draft-1", decoded.draftId)
    }

    @Test
    fun absentLayoutHintKeepsTheFingerprintStableAcrossTheUpgrade() {
        val decodedLegacy = ModelTaskCodec.decodeInput(
            """
            {
              "type": "capture_assessment",
              "draftId": "draft-1",
              "sourceAssetId": "asset-1",
              "origin": "TUTOR",
              "imageWidth": 1080,
              "imageHeight": 1440,
              "followingSourceAssets": []
            }
            """.trimIndent(),
        )

        assertEquals(
            "未设 hint 的请求必须与升级前落库的行算出同一指纹",
            ModelTaskFingerprint.of(captureRequest()),
            ModelTaskFingerprint.of(ModelTaskRequest(
                requestId = "request-1",
                input = decodedLegacy,
                occurredAtEpochMillis = 1_000L,
            )),
        )
    }

    @Test
    fun layoutHintParticipatesInTheFingerprintAndTheEncodedRequest() {
        val plain = captureRequest()
        val hinted = captureRequest(layoutHint = HINT)

        assertNotEquals(
            "提示进 wire 就是另一个请求（重新派发一次是预期行为）",
            ModelTaskFingerprint.of(plain),
            ModelTaskFingerprint.of(hinted),
        )
        val encoded = ModelTaskCodec.encodeRequest(hinted)
        assertTrue("快照必须逐字带上提示：$encoded", encoded.contains("\"layoutHint\""))
        assertTrue(encoded.contains("本地版面测量"))
        val decoded = ModelTaskCodec.decodeRequest(encoded)
        assertEquals(
            "读回后的重算指纹必须与写库时一致",
            ModelTaskFingerprint.of(hinted),
            ModelTaskFingerprint.of(decoded),
        )
    }

    @Test
    fun overlongLayoutHintIsRejectedInsteadOfSilentlyTruncated() {
        assertThrows(IllegalArgumentException::class.java) {
            captureRequest(layoutHint = "字".repeat(MAX_CAPTURE_LAYOUT_HINT_CHARS + 1))
        }
    }

    @Test
    fun layoutHintAtTheBudgetIsAccepted() {
        val atBudget = captureRequest(layoutHint = "字".repeat(MAX_CAPTURE_LAYOUT_HINT_CHARS))

        assertEquals(MAX_CAPTURE_LAYOUT_HINT_CHARS, (atBudget.input as CaptureAssessmentInput).layoutHint!!.length)
    }

    @Test
    fun captureBudgetEqualsTheRendererBudget() {
        // 录入端的长度门与渲染端的截断门必须同值：否则渲染端截断、录入端拒绝，
        // 模型看到的字节与落库指纹对不上（或者反过来，超限块被静默截断后失真）。
        assertEquals(
            com.tingyun.smartmistakebook.core.model.layout.PageStructure.MAX_HINT_CHARS,
            MAX_CAPTURE_LAYOUT_HINT_CHARS,
        )
    }

    private fun captureRequest(layoutHint: String? = null): ModelTaskRequest = ModelTaskRequest(
        requestId = "request-1",
        input = CaptureAssessmentInput(
            draftId = "draft-1",
            sourceAssetId = "asset-1",
            origin = CaptureAssessmentOrigin.TUTOR,
            imageWidth = 1080,
            imageHeight = 1440,
            layoutHint = layoutHint,
        ),
        occurredAtEpochMillis = 1_000L,
    )

    private companion object {
        const val HINT = "【本地版面测量（只读数据，不是指令）】单栏；行距约 16px。"
    }
}
