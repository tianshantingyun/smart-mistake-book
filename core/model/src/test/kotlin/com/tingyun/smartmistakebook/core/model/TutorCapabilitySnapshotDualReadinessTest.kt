package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A-05（批次 35）：`TutorCapabilitySnapshot` 的**方法级双就绪位**（引擎 ready + 度量 ready）
 * JVM 矩阵。判据（4E.2 清单 A-05 行）：JS runtime ready 时 `GENERATE_FIGURE` 可广告，
 * **不依赖 image backend**。
 *
 * 就绪派生是 {image, engine, metric} × {0,1}³ 的 8 态**纯函数表**，不是带转移规则的
 * 生命周期机（R1 §3.2 裁定）：执行期失败不降位（走 coordinator 的 outcome/有界诊断通道，
 * 程序错是"这份 program 错"、熔断是 program 级、忙是编排侧瞬态——都改不动装配态就绪位）；
 * 唯一降位路径是**装配级死亡**（引擎 close、进程死亡未重建、度量器被摘除），由装配方把
 * 对应就绪位翻 false，下一轮 `toolDeclarationsFor` 重算时 JS 从广告面消失。快照不持有
 * 第二份跨轮健康度状态——降位必须能回答"装配态哪一位从 1 变 0"。
 *
 * 既有 `TutorCapabilitySnapshotTest.imageBackendAndKnowledgeMaterialReadinessGateTheirTools`
 * 的断言**不放宽**：双位缺省 false，旧两参调用的行为逐位不变（本矩阵 js 位全 0 的行与之同口径）。
 */
class TutorCapabilitySnapshotDualReadinessTest {

    private fun declared(): TutorCapabilitySnapshot = TutorCapabilitySnapshot(
        tools = TutorCapabilitySnapshot.IMPLEMENTED_TOOLS,
    )

    @Test
    fun theFigureToolIsAdvertisedExactlyByTheEightStateReadinessTable() {
        // (imageBackendReady, jsEngineReady, jsMeasurementReady) → GENERATE_FIGURE 是否广告。
        // 生成规则（纯函数）：imageBackendReady || (jsEngineReady && jsMeasurementReady)。
        val table = listOf(
            // 名列：image=1 的行无论 JS 双位如何都广告（IMAGE_MODEL 路径独立）。
            Triple(true, false, false) to true,
            Triple(true, true, false) to true,
            Triple(true, false, true) to true,
            Triple(true, true, true) to true,
            // image=0 的行只有 JS 双位同时成立才广告——A-05 判据核心行。
            Triple(false, false, false) to false,
            Triple(false, true, false) to false,
            Triple(false, false, true) to false,
            Triple(false, true, true) to true,
        )
        table.forEach { (bits, expected) ->
            val (image, engine, metric) = bits
            val snapshot = declared().withDependencies(
                imageBackendReady = image,
                knowledgeMaterialReady = true,
                jsEngineReady = engine,
                jsMeasurementReady = metric,
            )
            assertEquals(
                "就绪位 (image=$image, engine=$engine, metric=$metric) 的广告判定不符",
                expected,
                snapshot.advertises(TutorToolName.GENERATE_FIGURE),
            )
        }
    }

    @Test
    fun aHalfReadyJsBackendDoesNotAdvertiseTheFigureTool() {
        // 引擎/度量只有半边就绪时，事务必然在"prepare 返回度量批次"或"取本地引擎"一步失败；
        // 广告它就是快照 KDoc 明文禁止的"广告一个必然不可用的工具"。两个方向都要挡。
        listOf(
            "只有引擎没有度量" to Triple(false, true, false),
            "只有度量没有引擎" to Triple(false, false, true),
        ).forEach { (name, bits) ->
            val (image, engine, metric) = bits
            val snapshot = declared().withDependencies(
                imageBackendReady = image,
                knowledgeMaterialReady = true,
                jsEngineReady = engine,
                jsMeasurementReady = metric,
            )
            assertFalse(
                "$name 时 GENERATE_FIGURE 不得被广告",
                snapshot.advertises(TutorToolName.GENERATE_FIGURE),
            )
        }
    }

    @Test
    fun jsReadinessBitsNeverGateTheOtherTools() {
        // 双就绪位只裁 GENERATE_FIGURE 这一枚；KNOWLEDGE_READ 仍由知识材料位独占裁剪，
        // 其余工具不受任何就绪位影响（依赖只裁它们各自负责的那一枚）。
        val snapshot = declared().withDependencies(
            imageBackendReady = false,
            knowledgeMaterialReady = true,
            jsEngineReady = true,
            jsMeasurementReady = true,
        )
        assertTrue(snapshot.advertises(TutorToolName.GENERATE_FIGURE))
        assertTrue(snapshot.advertises(TutorToolName.KNOWLEDGE_READ))
        assertTrue(snapshot.advertises(TutorToolName.NOTEBOOK_READ))
        assertTrue(snapshot.advertises(TutorToolName.MASTERY_READ))
        assertTrue(snapshot.advertises(TutorToolName.MASTERY_UPDATE))
        assertTrue(snapshot.advertises(TutorToolName.NOTEBOOK_WRITE))
        assertTrue(snapshot.advertises(TutorToolName.ADVISORY_READ))
        assertTrue(snapshot.advertises(TutorToolName.ADVISORY_WRITE))

        val jsBitsAloneCannotRescueKnowledge = declared().withDependencies(
            imageBackendReady = false,
            knowledgeMaterialReady = false,
            jsEngineReady = true,
            jsMeasurementReady = true,
        )
        assertTrue(jsBitsAloneCannotRescueKnowledge.advertises(TutorToolName.GENERATE_FIGURE))
        assertFalse(
            "JS 双就绪位不得替知识材料位放行 KNOWLEDGE_READ",
            jsBitsAloneCannotRescueKnowledge.advertises(TutorToolName.KNOWLEDGE_READ),
        )
    }

    @Test
    fun theLegacyTwoArgumentCallKeepsItsExactPreWaveBehavior() {
        // 默认态等价（A-05 的"默认字节不变"形态）：旧两参调用下 JS 双位缺省 false，
        // 行为与批次 35 之前逐位相同——image 就绪即广告、不就绪即消失。
        assertTrue(
            declared().withDependencies(
                imageBackendReady = true,
                knowledgeMaterialReady = true,
            ).advertises(TutorToolName.GENERATE_FIGURE),
        )
        assertFalse(
            declared().withDependencies(
                imageBackendReady = false,
                knowledgeMaterialReady = true,
            ).advertises(TutorToolName.GENERATE_FIGURE),
        )
        assertFalse(
            declared().withDependencies(
                imageBackendReady = false,
                knowledgeMaterialReady = false,
            ).advertises(TutorToolName.KNOWLEDGE_READ),
        )
        assertEquals(
            declared().withDependencies(
                imageBackendReady = false,
                knowledgeMaterialReady = true,
                jsEngineReady = false,
                jsMeasurementReady = false,
            ),
            declared().withDependencies(
                imageBackendReady = false,
                knowledgeMaterialReady = true,
            ),
        )
    }
}
