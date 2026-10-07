package com.tingyun.smartmistakebook.core.data.knowledge

import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialBindingVerdict
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialBindingVerdictSource
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialNodeRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-0 绑定可信度字段（内核侧批 1）：侧车 schema 3 的可选裁定三元组。
 *
 * 钉住四件事：
 * ① v3 绑定可带 `verdict`/`verdictSource`/`judgedAtEpochMillis`，同一材料上已审计与未审计
 *    绑定混排时字段值正确（未审计绑定三字段全 null）；
 * ② 三元组 all-or-none：缺一/缺二拒；
 * ③ 词表越界、时间戳负值、绑定额外未知键在 v3 下都拒；
 * ④ v1/v2 的绑定键集逐字不变——v2 包体带三元组仍按"未知键"拒，v2 两键绑定照旧解出 null。
 *
 * 夹具全部是测试内联文本，不落任何真实包目录（`core/data/src/main/resources/knowledge/` 只读）。
 */
class ReviewedTeachingMaterialSidecarBindingVerdictTest {

    @Test
    fun schemaThreeCarriesAuditedAndUnauditedBindingsOnTheSameMaterial() {
        val sidecar = decodeSchemaVersioned(
            schemaVersion = 3,
            bindings = listOf(auditedKeepBinding, auditedRebindBinding, unauditedBinding)
                .joinToString(","),
        )

        assertEquals(3, sidecar.bindings.size)
        val materialId = sidecar.materials.single().materialId
        assertTrue(sidecar.bindings.all { it.materialId == materialId })

        val keep = sidecar.bindings[0]
        assertEquals(
            "kb:moe-2020-foundation-v1:math:atomic:read-monotonicity-from-graph",
            keep.knowledgeNodeId,
        )
        assertEquals(KnowledgeMaterialNodeRole.PRIMARY.name, keep.role)
        assertEquals(KnowledgeMaterialBindingVerdict.KEEP.name, keep.verdict)
        assertEquals(KnowledgeMaterialBindingVerdictSource.MODEL_AUDIT.name, keep.verdictSource)
        assertEquals(1_710_000_000_000L, requireNotNull(keep.judgedAtEpochMillis))

        val rebind = sidecar.bindings[1]
        assertEquals(
            "kb:moe-2020-foundation-v1:math:atomic:express-monotonicity-symbolically",
            rebind.knowledgeNodeId,
        )
        assertEquals(KnowledgeMaterialNodeRole.SUPPORTING.name, rebind.role)
        assertEquals(KnowledgeMaterialBindingVerdict.REBIND.name, rebind.verdict)
        assertEquals(KnowledgeMaterialBindingVerdictSource.USER_DECISION.name, rebind.verdictSource)
        assertEquals(1_710_000_001_000L, requireNotNull(rebind.judgedAtEpochMillis))

        val unaudited = sidecar.bindings[2]
        assertEquals(
            "kb:moe-2020-foundation-v1:math:atomic:read-extrema-from-graph",
            unaudited.knowledgeNodeId,
        )
        assertEquals(KnowledgeMaterialNodeRole.SUPPORTING.name, unaudited.role)
        assertNull(unaudited.verdict)
        assertNull(unaudited.verdictSource)
        assertNull(unaudited.judgedAtEpochMillis)
    }

    @Test
    fun schemaTwoBindingWithoutVerdictTripleStillDecodesToNulls() {
        val sidecar = decodeSchemaVersioned(schemaVersion = 2, bindings = unauditedBinding)

        val binding = sidecar.bindings.single()
        assertEquals(KnowledgeMaterialNodeRole.SUPPORTING.name, binding.role)
        assertNull(binding.verdict)
        assertNull(binding.verdictSource)
        assertNull(binding.judgedAtEpochMillis)
    }

    @Test
    fun schemaTwoRejectsVerdictKeysInsteadOfSilentlyAcceptingSchemaDrift() {
        val failure = runCatching {
            decodeSchemaVersioned(schemaVersion = 2, bindings = auditedKeepBinding)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "v2 绑定键集必须恰好两键，报错应指出未知键：${failure?.message}",
            failure?.message.orEmpty().contains("unknown keys"),
        )
    }

    @Test
    fun schemaThreeRejectsUnknownBindingKey() {
        val failure = runCatching {
            decodeSchemaVersioned(
                schemaVersion = 3,
                bindings = auditedKeepBinding.replace(
                    """"role":"PRIMARY",""",
                    """"role":"PRIMARY","confidence":0.9,""",
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "v3 绑定未知键必须拒：${failure?.message}",
            failure?.message.orEmpty().contains("unknown keys"),
        )
    }

    @Test
    fun schemaThreeRejectsPartiallyPresentVerdictTriple() {
        val missingTimestamp = auditedKeepBinding.replace(
            ""","judgedAtEpochMillis":1710000000000""",
            "",
        )
        val onlyTimestamp = auditedKeepBinding
            .replace(""""verdict":"KEEP",""", "")
            .replace(""""verdictSource":"MODEL_AUDIT",""", "")

        listOf(missingTimestamp, onlyTimestamp).forEach { bindings ->
            val failure = runCatching {
                decodeSchemaVersioned(schemaVersion = 3, bindings = bindings)
            }.exceptionOrNull()

            assertTrue("夹具必须真的是残缺三元组：$bindings", failure is IllegalArgumentException)
            assertTrue(
                "残缺三元组必须报 all-or-none：${failure?.message}",
                failure?.message.orEmpty().contains("all present or all absent"),
            )
        }
    }

    @Test
    fun schemaThreeRejectsOutOfVocabularyVerdictAndSource() {
        val badVerdict = auditedKeepBinding.replace(""""verdict":"KEEP"""", """"verdict":"MAYBE"""")
        val badSource = auditedKeepBinding.replace(
            """"verdictSource":"MODEL_AUDIT"""",
            """"verdictSource":"GUESS"""",
        )

        listOf(badVerdict, badSource).forEach { bindings ->
            val failure = runCatching {
                decodeSchemaVersioned(schemaVersion = 3, bindings = bindings)
            }.exceptionOrNull()

            // 词表越界走 codec 的 requiredEnum，其内部用 error(...)，抛的是 IllegalStateException
            // （与 role 值非法同一条既有路径），所以这里只断言"确实拒 + 报的是未知取值"。
            assertTrue("词表越界必须拒：$bindings", failure != null)
            assertTrue(
                "词表越界必须报未知取值：${failure?.message}",
                failure?.message.orEmpty().contains("unknown value"),
            )
        }
    }

    @Test
    fun schemaThreeRejectsNegativeJudgedTimestamp() {
        val failure = runCatching {
            decodeSchemaVersioned(
                schemaVersion = 3,
                bindings = auditedKeepBinding.replace(
                    """"judgedAtEpochMillis":1710000000000""",
                    """"judgedAtEpochMillis":-1""",
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "负时间戳必须拒：${failure?.message}",
            failure?.message.orEmpty().contains("must not be negative"),
        )
    }

    @Test
    fun unknownSchemaVersionIsStillRejected() {
        val failure = runCatching {
            decodeSchemaVersioned(schemaVersion = 4, bindings = unauditedBinding)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "accepted 集合必须恰好是 {1,2,3}：${failure?.message}",
            failure?.message.orEmpty().contains("Unsupported teaching-material sidecar schema 4"),
        )
    }

    private fun decodeSchemaVersioned(
        schemaVersion: Int,
        bindings: String,
    ): ReviewedTeachingMaterialSidecar = ReviewedTeachingMaterialSidecarJsonCodec.decode(
        sidecarJson(schemaVersion, bindings),
        targetPack(),
    )

    private fun targetPack(): KnowledgeBasePack =
        ReviewedKnowledgePackJsonCodec.decode(
            requireNotNull(
                requireNotNull(ReviewedTeachingMaterialSidecarBindingVerdictTest::class.java.classLoader)
                    .getResourceAsStream("knowledge/moe-2020-foundation-v1.json"),
            ).bufferedReader().use { it.readText() },
        )

    private fun sidecarJson(schemaVersion: Int, bindings: String): String =
        """
        {
          "schemaVersion":$schemaVersion,
          "packId":"moe-2020-foundation-v1",
          "sources":[{
            "sourceId":"source:open:math:example",
            "subject":"MATH",
            "sourceType":"AUTHORIZED_EDUCATION_MATERIAL",
            "title":"开放数学教学资料",
            "publisher":"示例机构",
            "edition":"2026",
            "sourceUri":"https://example.org/math",
            "licenseStatus":"LICENSED",
            "contentFingerprint":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
            "importedAtEpochMillis":1784764800000,
            "contentUsePolicy":"ADAPTATION_ALLOWED",
            "licenseExpression":"CC-BY-NC-SA-4.0",
            "licenseUri":"https://creativecommons.org/licenses/by-nc-sa/4.0/",
            "attributionText":"示例机构《开放数学教学资料》，依 CC BY-NC-SA 4.0 改编。"
          }],
          "materials":[{
            "slug":"licensed-complete-worked-example",
            "subject":"MATH",
            "type":"WORKED_EXAMPLE",
            "title":"函数单调性的完整解答示例",
            "summaryMarkdown":"展示从题面到结论的完整解答。",
            "applicabilityMarkdown":"适用于当前题涉及函数图象单调性时。",
            "contentMarkdown":"例题与完整解答都可以保留，但该材料不能被布置、评分或加入复习队列。",
            "boundaryMarkdown":"只辅助解释学生当前提供的题。",
            "derivationKind":"LICENSED_ADAPTATION",
            "sourceId":"source:open:math:example",
            "sourceLocator":"函数性质·示例",
            "reviewedAtEpochMillis":1784764800000,
            "bindings":[$bindings]
          }]
        }
        """.trimIndent()

    private companion object {
        /** 已审计：verdict=KEEP / MODEL_AUDIT / 有时间戳。 */
        const val auditedKeepBinding =
            """{"knowledgeNodeId":"kb:moe-2020-foundation-v1:math:atomic:read-monotonicity-from-graph","role":"PRIMARY","verdict":"KEEP","verdictSource":"MODEL_AUDIT","judgedAtEpochMillis":1710000000000}"""

        /** 已审计：verdict=REBIND / USER_DECISION / 有时间戳。 */
        const val auditedRebindBinding =
            """{"knowledgeNodeId":"kb:moe-2020-foundation-v1:math:atomic:express-monotonicity-symbolically","role":"SUPPORTING","verdict":"REBIND","verdictSource":"USER_DECISION","judgedAtEpochMillis":1710000001000}"""

        /** 未审计：三元组整体缺席（v1/v2 及未裁定绑定的形态）。 */
        const val unauditedBinding =
            """{"knowledgeNodeId":"kb:moe-2020-foundation-v1:math:atomic:read-extrema-from-graph","role":"SUPPORTING"}"""
    }
}
