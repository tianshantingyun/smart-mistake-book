package com.tingyun.smartmistakebook.core.data.knowledge

import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialContentAttribute
import com.tingyun.smartmistakebook.core.model.KnowledgeMaterialNodeRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8·③ 材料侧 AI/provenance 字段：侧车 schema 4 的**必填**三键。
 *
 * 钉住五件事：
 * ① v4 材料带 `contentAttribute`/`contentProvider`/`contentId` 三键可解出，且材料上的
 *    v3 裁定三元组不受影响（元数据轴与绑定裁定轴各自独立）；
 * ② v4 材料**必含**三键——全缺 / 缺一都是整包拒（不是可选语义）；
 * ③ `contentId` 必须等于材料自己的 slug（键在但取值说谎照样拒），`contentAttribute` 走
 *    闭词表，`contentProvider` 必须是 trim 后非空字符串；
 * ④ v2/v3 的材料**不许**含这三键——出现即未知键、整包拒（两版各一条独立用例，
 *    因为"更旧的版本"未必落在同一条分支上）；
 * ⑤ v4 的材料键集是闭集：多一枚键也是拒。
 *
 * 夹具全部是测试内联文本，不落任何真实包目录（`core/data/src/main/resources/knowledge/`
 * 只读）。绑定的目标节点取自随包的 2020 样例包（与
 * `ReviewedTeachingMaterialSidecarBindingVerdictTest` 同一份真实 pack 资源，不另造夹具文件）。
 */
class ReviewedTeachingMaterialSidecarProvenanceTest {

    @Test
    fun attributeVocabularyIsPinnedToOneMechanicallyTrueValue() {
        // 扩词表 = 语义变更 → 必须 bump schemaVersion 并同步 Python 侧 ATTRIBUTES；
        // 先在这里钉死，免得有人顺手加值却忘了版本门。
        assertEquals(
            listOf("AI_SYNTHESIZED"),
            enumValues<KnowledgeMaterialContentAttribute>().map { it.name },
        )
    }

    @Test
    fun schemaFourMaterialCarriesProvenanceAndKeepsItsVerdictTriple() {
        val sidecar = decodeSchemaVersioned(schemaVersion = 4, provenance = provenance(),
                                            bindings = auditedBinding)

        val material = sidecar.materials.single()
        assertEquals(
            "moe-2020-foundation-v1:math:teaching:licensed-complete-worked-example",
            material.stableCode,
        )
        // 三键是元数据，不改变材料的角色/权威与正文解出结果
        assertEquals("函数单调性的完整解答示例", material.title)
        assertEquals("LICENSED_ADAPTATION", material.derivationKind)

        val binding = sidecar.bindings.single()
        assertEquals(KnowledgeMaterialNodeRole.PRIMARY.name, binding.role)
        assertEquals("KEEP", binding.verdict)
        assertEquals("MODEL_AUDIT", binding.verdictSource)
        assertEquals(1_710_000_000_000L, requireNotNull(binding.judgedAtEpochMillis))
    }

    @Test
    fun schemaFourRejectsMaterialWithoutTheProvenanceTriple() {
        val failure = runCatching { decodeSchemaVersioned(schemaVersion = 4) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        val message = failure?.message.orEmpty()
        assertTrue("v4 材料缺三键必须拒：$message", message.contains("missing keys"))
        listOf("contentAttribute", "contentProvider", "contentId").forEach { key ->
            assertTrue("报错必须点名缺的那一枚 $key：$message", message.contains(key))
        }
    }

    @Test
    fun schemaFourRejectsPartiallyPresentProvenanceTriple() {
        // 只有属性 + 编号，缺提供者：不是"可选三元组的未写形态"，是残缺。
        val partial = """"contentAttribute":"AI_SYNTHESIZED",""" +
            """"contentId":"licensed-complete-worked-example","""
        val failure = runCatching {
            decodeSchemaVersioned(schemaVersion = 4, provenance = partial)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "残缺三键必须报缺 contentProvider：${failure?.message}",
            failure?.message.orEmpty().contains("missing keys") &&
                failure?.message.orEmpty().contains("contentProvider"),
        )
    }

    @Test
    fun schemaFourRejectsContentIdThatIsNotTheMaterialSlug() {
        val failure = runCatching {
            decodeSchemaVersioned(
                schemaVersion = 4,
                provenance = provenance(contentId = "a-foreign-content-id"),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "contentId 必须等于 slug：${failure?.message}",
            failure?.message.orEmpty().contains("contentId must equal the material slug"),
        )
    }

    @Test
    fun schemaFourRejectsOutOfVocabularyAttribute() {
        val failure = runCatching {
            decodeSchemaVersioned(
                schemaVersion = 4,
                provenance = provenance(attribute = "PROBABLY_AI"),
            )
        }.exceptionOrNull()

        assertTrue("属性词表越界必须拒：${failure?.message}", failure != null)
        assertTrue(
            "词表越界必须报未知取值：${failure?.message}",
            failure?.message.orEmpty().contains("unknown value"),
        )
    }

    @Test
    fun schemaFourRejectsBlankProvider() {
        val failure = runCatching {
            decodeSchemaVersioned(schemaVersion = 4, provenance = provenance(provider = "   "))
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "空白的 contentProvider 必须拒：${failure?.message}",
            failure?.message.orEmpty().contains("trimmed non-blank"),
        )
    }

    @Test
    fun schemaFourRejectsUnknownMaterialKey() {
        val failure = runCatching {
            decodeSchemaVersioned(
                schemaVersion = 4,
                provenance = provenance() + """"aiDisclosure":"内部",""",
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "v4 材料多一枚键也要拒：${failure?.message}",
            failure?.message.orEmpty().contains("unknown keys") &&
                failure?.message.orEmpty().contains("aiDisclosure"),
        )
    }

    @Test
    fun schemaThreeRejectsMaterialProvenanceKeys() {
        val failure = runCatching {
            decodeSchemaVersioned(schemaVersion = 3, provenance = provenance())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "v3 材料不许带三键：${failure?.message}",
            failure?.message.orEmpty().contains("unknown keys") &&
                failure?.message.orEmpty().contains("contentAttribute"),
        )
    }

    @Test
    fun schemaTwoRejectsMaterialProvenanceKeys() {
        val failure = runCatching {
            decodeSchemaVersioned(schemaVersion = 2, provenance = provenance())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(
            "v2 材料不许带三键：${failure?.message}",
            failure?.message.orEmpty().contains("unknown keys"),
        )
    }

    private fun decodeSchemaVersioned(
        schemaVersion: Int,
        provenance: String = "",
        bindings: String = plainBinding,
    ): ReviewedTeachingMaterialSidecar = ReviewedTeachingMaterialSidecarJsonCodec.decode(
        sidecarJson(schemaVersion, provenance, bindings),
        targetPack(),
    )

    /**
     * 三键的落位串（插在 `reviewedAtEpochMillis` 与 `bindings` 之间——这就是写入侧的落盘
     * 键序）。带尾逗号，供残缺/多余键的用例直接改参数。
     */
    private fun provenance(
        attribute: String = "AI_SYNTHESIZED",
        provider: String = "moe-2020-foundation-v1",
        contentId: String = "licensed-complete-worked-example",
    ): String = """"contentAttribute":"$attribute","contentProvider":"$provider",""" +
        """"contentId":"$contentId","""

    private fun targetPack(): KnowledgeBasePack =
        ReviewedKnowledgePackJsonCodec.decode(
            requireNotNull(
                requireNotNull(
                    ReviewedTeachingMaterialSidecarProvenanceTest::class.java.classLoader,
                ).getResourceAsStream("knowledge/moe-2020-foundation-v1.json"),
            ).bufferedReader().use { it.readText() },
        )

    /**
     * 来源块按 schemaVersion 分支：v1（legacy）键集恰好 10 枚、不含 4 枚政策键
     * （codec `toSource` 的 v1 分支 `requireOnlyKeys(*sharedKeys)`）。若 v1 用例误用
     * v2+ 形态的来源，会因「来源未知键」先拒，材料键集那条断言就测不到了。
     */
    private fun sourceJson(schemaVersion: Int): String {
        val shared = """
            "sourceId":"source:open:math:example",
            "subject":"MATH",
            "sourceType":"AUTHORIZED_EDUCATION_MATERIAL",
            "title":"开放数学教学资料",
            "publisher":"示例机构",
            "edition":"2026",
            "sourceUri":"https://example.org/math",
            "licenseStatus":"LICENSED",
            "contentFingerprint":"CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC",
            "importedAtEpochMillis":1784764800000
        """.trimIndent()
        val policies = """
            "contentUsePolicy":"ADAPTATION_ALLOWED",
            "licenseExpression":"CC-BY-NC-SA-4.0",
            "licenseUri":"https://creativecommons.org/licenses/by-nc-sa/4.0/",
            "attributionText":"示例机构《开放数学教学资料》，依 CC BY-NC-SA 4.0 改编。"
        """.trimIndent()
        return if (schemaVersion == 1) "{$shared}" else "{$shared,\n$policies}"
    }

    private fun sidecarJson(
        schemaVersion: Int,
        provenance: String,
        bindings: String,
    ): String =
        """
        {
          "schemaVersion":$schemaVersion,
          "packId":"moe-2020-foundation-v1",
          "sources":[${sourceJson(schemaVersion)}],
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
            $provenance
            "bindings":[$bindings]
          }]
        }
        """.trimIndent()

    private companion object {
        /** 未裁定绑定（v1/v2/v3/v4 的基准形态）。 */
        const val plainBinding =
            """{"knowledgeNodeId":"kb:moe-2020-foundation-v1:math:atomic:read-monotonicity-from-graph","role":"PRIMARY"}"""

        /** 已审计绑定：v3/v4 下允许的裁定三元组。 */
        const val auditedBinding =
            """{"knowledgeNodeId":"kb:moe-2020-foundation-v1:math:atomic:read-monotonicity-from-graph","role":"PRIMARY","verdict":"KEEP","verdictSource":"MODEL_AUDIT","judgedAtEpochMillis":1710000000000}"""
    }
}
