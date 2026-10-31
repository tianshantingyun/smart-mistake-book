package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * X-02 源证据基准（`VisualSourceBasis` + `VisualSourceSeenRecord`）与策略门的 JVM 契约测试。
 *
 * 验收向量（清单 :240：缺源/错图/SHA 变化/授权不足）钉在策略门：
 * - 冻结基准未授权披露原图时，任何会把源图字节送出网的 IMAGE_MODEL 派发一律拒绝；
 * - 基准的 canonical 引用/SHA 与实际源证据不一致（错图/SHA 变化）fail-closed；
 * - facts-only 基准不挡"无源字节"的纯文生图（没有字节出网就没有披露）；
 * - 旧调用（basis = null）逐位零变化。
 *
 * 类型自身钉住：canonical 引用与 SHA 成对、授权必须先有冻结引用、事实文本有界且自带
 * 追溯摘要。旧调用回归（basis = null 的 EDIT 照常接受）由
 * `FigureGenerationPolicyTest` 的既有用例覆盖，本文件不重复。
 */
class VisualSourceBasisTest {

    // -----------------------------------------------------------------
    // 类型不变量
    // -----------------------------------------------------------------

    @Test
    fun canonicalReferenceAndShaMustBePaired() {
        val bytes = "photo".toByteArray()
        try {
            VisualSourceBasis(
                subjectId = "session-1",
                revision = 1,
                canonicalAssetId = "asset-1",
                contentSha256 = null,
            )
            fail("canonical 引用缺 SHA 必须拒绝")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("paired"))
        }
        try {
            VisualSourceBasis(
                subjectId = "session-1",
                revision = 1,
                canonicalAssetId = null,
                contentSha256 = figureSha256Hex(bytes),
            )
            fail("SHA 缺 canonical 引用必须拒绝")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("paired"))
        }
    }

    @Test
    fun imageDisclosureRequiresAFrozenCanonicalReference() {
        try {
            VisualSourceBasis(
                subjectId = "session-1",
                revision = 1,
                canonicalAssetId = null,
                contentSha256 = null,
                allowsImageDisclosure = true,
            )
            fail("授权披露原图必须先冻结 canonical 引用/SHA")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("canonical"))
        }
    }

    @Test
    fun structuredFactsDisclosureRequiresFrozenFacts() {
        try {
            VisualSourceBasis(
                subjectId = "session-1",
                revision = 1,
                canonicalAssetId = null,
                contentSha256 = null,
                structuredFacts = null,
                allowsStructuredFactsDisclosure = true,
            )
            fail("授权披露结构化源事实必须先冻结事实文本")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("frozen facts"))
        }
    }

    @Test
    fun structuredFactsAreBounded() {
        try {
            basisWithFacts("事".repeat(VisualSourceBasis.MAX_STRUCTURED_FACTS_CHARS + 1))
            fail("结构化源事实超出预算必须拒绝")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("budget"))
        }
        basisWithFacts("事".repeat(VisualSourceBasis.MAX_STRUCTURED_FACTS_CHARS))
    }

    @Test
    fun frozenShaMustBeLowerHex64() {
        try {
            VisualSourceBasis(
                subjectId = "session-1",
                revision = 1,
                canonicalAssetId = "asset-1",
                contentSha256 = "ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789",
            )
            fail("基准 SHA 必须是小写 hex-64")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("SHA"))
        }
    }

    @Test
    fun structuredFactsSha256IsTheTraceabilityDigest() {
        val facts = "三角形 ABC 中，AB=5，∠B=90°。"
        val basis = basisWithFacts(facts)
        assertEquals(
            figureSha256Hex(facts.toByteArray(Charsets.UTF_8)),
            basis.structuredFactsSha256,
        )
        assertNull(
            "没有冻结事实就没有事实摘要",
            VisualSourceBasis(
                subjectId = "session-1",
                revision = 1,
                canonicalAssetId = "asset-1",
                contentSha256 = figureSha256Hex("photo".toByteArray()),
            ).structuredFactsSha256,
        )
    }

    @Test
    fun subjectAndRevisionAreBounded() {
        try {
            VisualSourceBasis(
                subjectId = "  ",
                revision = 1,
                canonicalAssetId = null,
                contentSha256 = null,
            )
            fail("subject 不得为空白")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("subject"))
        }
        try {
            VisualSourceBasis(
                subjectId = "session-1",
                revision = -1,
                canonicalAssetId = null,
                contentSha256 = null,
            )
            fail("revision 不得为负")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("revision"))
        }
    }

    @Test
    fun toStringNeverEchoesTheFactsText() {
        val facts = "需要保密的题面细节内容"
        val basis = basisWithFacts(facts)
        assertTrue(basis.toString().contains(basis.structuredFactsSha256!!))
        assertTrue("toString 不得回显事实原文", !basis.toString().contains(facts))
    }

    // -----------------------------------------------------------------
    // 策略门：验收向量（清单 :240）
    // -----------------------------------------------------------------

    @Test
    fun anUnauthorizedBasisRejectsEditBeforeAnyDispatch() {
        val request = request(basis = factsOnlyBasis())
        assertEquals(
            FigureGenerationRejectionCode.SOURCE_DISCLOSURE_UNAUTHORIZED,
            rejectCode(request, editProposal(request)),
        )
    }

    @Test
    fun aBasisWithReferenceButNoImageAuthorizationStillRejectsByteDispatch() {
        val bytes = SOURCE_BYTES
        val request = request(
            basis = VisualSourceBasis(
                subjectId = "session-1",
                revision = 1,
                canonicalAssetId = "asset-1",
                contentSha256 = figureSha256Hex(bytes),
            ),
        )
        assertEquals(
            FigureGenerationRejectionCode.SOURCE_DISCLOSURE_UNAUTHORIZED,
            rejectCode(request, editProposal(request)),
        )
    }

    @Test
    fun aMismatchedAssetAgainstTheFrozenBasisIsRejected() {
        val request = request(basis = authorizedBasis(assetId = "asset-other"))
        assertEquals(
            FigureGenerationRejectionCode.SOURCE_EVIDENCE_MISMATCH,
            rejectCode(request, editProposal(request)),
        )
    }

    @Test
    fun changedSourceBytesAgainstTheFrozenShaAreRejected() {
        val request = request(
            basis = authorizedBasis(
                assetId = "asset-1",
                sha = figureSha256Hex("其它字节".toByteArray()),
            ),
        )
        assertEquals(
            FigureGenerationRejectionCode.SOURCE_EVIDENCE_MISMATCH,
            rejectCode(request, editProposal(request)),
        )
    }

    @Test
    fun anAuthorizedBasisAllowsEdit() {
        val request = request(basis = authorizedBasis(assetId = "asset-1"))
        assertEquals(FigureGenerationValidation.Accepted, FigureGenerationPolicy.validate(request, editProposal(request)))
    }

    @Test
    fun aFactsOnlyBasisStillAllowsSourcelessGenerate() {
        val request = request(
            basis = factsOnlyBasis(),
            source = null,
        )
        val proposal = FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.GENERATE,
            imagePrompt = "示意三角形构图",
            reason = "测试",
        )
        assertEquals(
            "没有字节出网就没有披露：facts-only 基准不挡无源文生图",
            FigureGenerationValidation.Accepted,
            FigureGenerationPolicy.validate(request, proposal),
        )
    }

    @Test
    fun jsGenerationIsUnaffectedByTheBasisGate() {
        val request = FigureGenerationRequest(
            operationId = "op-1",
            purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
            target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
            capabilities = FigureGenerationCapabilities(
                availableMethods = setOf(FigureGenerationMethod.JS),
                supportsParametricInteraction = true,
            ),
            budget = FigureGenerationBudget(remainingRemoteDispatches = 6, reserveChatFinalAnswer = false),
            dispatchParent = FigureDispatchParent.ModelTask("task-1", false),
            source = defaultSource(),
            visualSourceBasis = factsOnlyBasis(),
        )
        val proposal = FigureGenerationProposal(
            policyVersion = FigureGenerationPolicy.POLICY_VERSION,
            decision = FigureGenerationDecision.GENERATE,
            purpose = request.purpose,
            method = FigureGenerationMethod.JS,
            reason = "测试",
            program = "const visual = {};",
            fidelity = FigureFidelity.FAITHFUL,
        )
        assertEquals(
            "JS 本地执行不出网，源证据披露门不适用",
            FigureGenerationValidation.Accepted,
            FigureGenerationPolicy.validate(request, proposal),
        )
    }

    // -----------------------------------------------------------------
    // VisualSourceSeenRecord
    // -----------------------------------------------------------------

    @Test
    fun seenRecordRequiresADispatchIdAndASha() {
        try {
            VisualSourceSeenRecord(sourceSeenDispatchId = " ", contentSha256 = SHA)
            fail("seen 记录必须有派发 id")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("dispatch id"))
        }
        try {
            VisualSourceSeenRecord(sourceSeenDispatchId = "attempt-1", contentSha256 = "short")
            fail("seen 记录摘要必须是 SHA-256")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("SHA"))
        }
    }

    @Test
    fun seenRecordEqualityIsValueIdentity() {
        val record = VisualSourceSeenRecord(sourceSeenDispatchId = "attempt-1", contentSha256 = SHA)
        assertEquals(record, VisualSourceSeenRecord(sourceSeenDispatchId = "attempt-1", contentSha256 = SHA))
        assertNotEquals(
            record,
            VisualSourceSeenRecord(sourceSeenDispatchId = "attempt-2", contentSha256 = SHA),
        )
    }

    // -----------------------------------------------------------------
    // 夹具
    // -----------------------------------------------------------------

    private fun basisWithFacts(facts: String): VisualSourceBasis = VisualSourceBasis(
        subjectId = "session-1",
        revision = 1,
        canonicalAssetId = null,
        contentSha256 = null,
        structuredFacts = facts,
    )

    private fun factsOnlyBasis(): VisualSourceBasis = VisualSourceBasis(
        subjectId = "session-1",
        revision = 1,
        canonicalAssetId = null,
        contentSha256 = null,
        structuredFacts = "三角形 ABC，AB=5，∠B=90°。",
        allowsStructuredFactsDisclosure = true,
    )

    private fun authorizedBasis(
        assetId: String,
        sha: String = figureSha256Hex(SOURCE_BYTES),
    ): VisualSourceBasis = VisualSourceBasis(
        subjectId = "session-1",
        revision = 1,
        canonicalAssetId = assetId,
        contentSha256 = sha,
        allowsImageDisclosure = true,
    )

    private fun request(
        basis: VisualSourceBasis?,
        source: FigureSourceEvidence? = defaultSource(),
    ): FigureGenerationRequest = FigureGenerationRequest(
        operationId = "op-1",
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        target = FigureGenerationTarget(fidelity = FigureFidelity.FAITHFUL),
        capabilities = FigureGenerationCapabilities.IMAGE_MODEL_ONLY,
        budget = FigureGenerationBudget(remainingRemoteDispatches = 6, reserveChatFinalAnswer = false),
        dispatchParent = FigureDispatchParent.ModelTask("task-1", false),
        source = source,
        visualSourceBasis = basis,
    )

    private fun editProposal(request: FigureGenerationRequest): FigureGenerationProposal =
        FigureGenerationPolicy.imageModelProposal(
            request = request,
            imageOperation = FigureImageOperation.EDIT,
            imagePrompt = "基于已授权原图重建干净题面。",
            reason = "测试",
        )

    private fun rejectCode(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationRejectionCode {
        val validation = FigureGenerationPolicy.validate(request, proposal)
        assertTrue("预期拒绝，实际 $validation", validation is FigureGenerationValidation.Rejected)
        return (validation as FigureGenerationValidation.Rejected).code
    }

    private fun defaultSource(): FigureSourceEvidence = FigureSourceEvidence(
        assetId = "asset-1",
        contentSha256 = figureSha256Hex(SOURCE_BYTES),
        mimeType = "image/png",
        imageBytes = SOURCE_BYTES,
    )

    private companion object {
        val SOURCE_BYTES = "source-photo".toByteArray()

        // `const` 要求编译期常量，`"a".repeat(64)` 不是（门禁第 1 轮
        // "Const 'val' initializer must be a constant value"）——按运行期常量持有。
        val SHA: String = "a".repeat(64)
    }
}
