package com.tingyun.smartmistakebook.core.data.capture

import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * T7（2026-10-25）：生成图溯源参数的**完备性守门**在 JVM 层的第一次钉住（既有钉住在
 * 仪器化层：`GeneratedFigureMetadataInstrumentedTest` 走真实落盘+读回）。
 *
 * 消灭的失败：`GeneratedFigureProvenance` 是隐式 AI 标识（《生成合成内容标识办法》第五条
 * 三要素：属性/提供者/编号）与导出侧显式图注（T7 `isAiGeneratedExportImage` 按角色判）
 * 共同依赖的输入——若哪个改造把非空校验放宽掉，就会出现 model/id 为空的生成图：文件里
 * `Software="SmartMistakeBook;model="`，溯源断裂且无人报错。空白字段必须在构造时即拒。
 */
class GeneratedFigureProvenanceTest {

    @Test
    fun `a provenance with a blank model is rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedFigureProvenance(model = " ", figureId = "figure-1", purpose = "CLEAN_REDRAW")
        }
    }

    @Test
    fun `a provenance with a blank figure id is rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedFigureProvenance(model = "gpt-image-2", figureId = "", purpose = "CLEAN_REDRAW")
        }
    }

    @Test
    fun `a provenance with a blank purpose is rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeneratedFigureProvenance(model = "gpt-image-2", figureId = "figure-1", purpose = " ")
        }
    }
}
