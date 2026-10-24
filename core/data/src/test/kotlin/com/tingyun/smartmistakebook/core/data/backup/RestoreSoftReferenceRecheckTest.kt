package com.tingyun.smartmistakebook.core.data.backup

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 恢复侧 S3 重查的判定语义（批次 19 T2-4，设计 §3.2-S3 / §9.4.3）。
 *
 * 判定是纯函数（[RestoreSoftReferenceRecheck.violationsFor]），不需要 SQLite；
 * 端到端（篡改包 → 交换前拒绝）由 `BackupSelectionRestoreInstrumentedTest` 盖。
 */
class RestoreSoftReferenceRecheckTest {

    private fun observations(
        modelTaskKinds: Map<String, List<String>> = emptyMap(),
        modelTaskOperationKinds: Map<String, List<String>> = emptyMap(),
        figureOperationSourceKinds: Map<String, List<String>> = emptyMap(),
        keptAssetIds: Set<String> = emptySet(),
        cleanRedrawPinnedOperationIds: Set<String> = emptySet(),
        attempts: List<RestoreSoftReferenceRecheck.StagedSplitObservations.StagedAttempt> = emptyList(),
    ) = RestoreSoftReferenceRecheck.StagedSplitObservations(
        modelTaskKinds = modelTaskKinds,
        modelTaskOperationKinds = modelTaskOperationKinds,
        figureOperationSourceKinds = figureOperationSourceKinds,
        keptAssetIds = keptAssetIds,
        cleanRedrawPinnedOperationIds = cleanRedrawPinnedOperationIds,
        attempts = attempts,
    )

    private fun attempt(
        attemptId: String,
        operationId: String,
        outputAssetId: String?,
    ) = RestoreSoftReferenceRecheck.StagedSplitObservations.StagedAttempt(
        attemptId = attemptId,
        operationId = operationId,
        outputAssetId = outputAssetId,
    )

    @Test
    fun `null and full masks are not rechecked`() {
        // v1/v2（null）没有选择语义；全选的裁剪器只裁派生索引，split 表一行不动。
        val dirty = observations(
            modelTaskKinds = mapOf("FUTURE_KIND" to listOf("task-x")),
            figureOperationSourceKinds = mapOf("TUTOR_MESSAGE" to listOf("op-x")),
        )
        assertTrue(RestoreSoftReferenceRecheck.violationsFor(null, dirty).isEmpty())
        assertTrue(RestoreSoftReferenceRecheck.violationsFor(15, dirty).isEmpty())
    }

    @Test
    fun `unknown task kinds fail closed for subsets`() {
        val problems = RestoreSoftReferenceRecheck.violationsFor(
            9,
            observations(modelTaskKinds = mapOf("FUTURE_KIND" to listOf("task-1"))),
        )
        assertTrue(
            "映射外 task_kind 必须被点名：$problems",
            problems.any { it.contains("model_task 含映射外 task_kind=FUTURE_KIND") && it.contains("task-1") },
        )
    }

    @Test
    fun `task kinds of unselected categories must not survive in subsets`() {
        // mask=9 未选 SESSIONS/MASTERY：讲题类与复习类任务行都是「split 表 kind 级谎报」。
        val problems = RestoreSoftReferenceRecheck.violationsFor(
            9,
            observations(
                modelTaskKinds = mapOf("TUTOR_RESPOND" to listOf("task-tutor")),
                modelTaskOperationKinds = mapOf("KNOWLEDGE_QUIZ" to listOf("op-mastery")),
            ),
        )
        assertTrue(
            "model_task 的讲题类行必须被拒：$problems",
            problems.any {
                it.contains("model_task 留有未选类别的 task_kind") && it.contains("TUTOR_RESPOND")
            },
        )
        assertTrue(
            "model_task_operation 的掌握类行必须被拒：$problems",
            problems.any {
                it.contains("model_task_operation 留有未选类别的 task_kind") &&
                    it.contains("KNOWLEDGE_QUIZ")
            },
        )
    }

    @Test
    fun `selected category kinds pass without complaints`() {
        val problems = RestoreSoftReferenceRecheck.violationsFor(
            9,
            observations(
                modelTaskKinds = mapOf("CAPTURE_ASSESS" to listOf("task-1")),
                modelTaskOperationKinds = mapOf("CAPTURE_PARSE" to listOf("op-1")),
                figureOperationSourceKinds = mapOf(
                    // ISSUE_BOOK 的三个 source_kind 都是 O_seed。
                    "PROBLEM_REVISION" to listOf("op-r"),
                    "CLEAN_REDRAW" to listOf("op-c"),
                    "CAPTURE_PARSE" to listOf("op-p"),
                ),
            ),
        )
        assertTrue("选中类别的 kind 与 operation 不产生违规：$problems", problems.isEmpty())
    }

    @Test
    fun `unselected source kind operation survives only via live output or clean redraw pin`() {
        // mask=9 未选 SESSIONS：TUTOR_MESSAGE 的 operation 默认应被裁掉。
        val rejected = RestoreSoftReferenceRecheck.violationsFor(
            9,
            observations(figureOperationSourceKinds = mapOf("TUTOR_MESSAGE" to listOf("op-t"))),
        )
        assertTrue(
            "无产物、无钉住的会话图 operation 必须被拒：$rejected",
            rejected.any { it.contains("figure_generation_operation 留有") && it.contains("op-t") },
        )

        // O_ref：产物仍被存活根引用（R）的 operation 保留（图被保存的题引用）。
        val viaOutput = RestoreSoftReferenceRecheck.violationsFor(
            9,
            observations(
                figureOperationSourceKinds = mapOf("TUTOR_MESSAGE" to listOf("op-t")),
                keptAssetIds = setOf("asset-kept"),
                attempts = listOf(attempt("a-1", "op-t", "asset-kept")),
            ),
        )
        assertTrue("产物仍在 R 的 operation 必须放行：$viaOutput", viaOutput.isEmpty())

        // O_seed 第三支：存活 clean_redraw_task 钉住的 operation 保留（即使 kind 属未选类别）。
        val viaPin = RestoreSoftReferenceRecheck.violationsFor(
            9,
            observations(
                figureOperationSourceKinds = mapOf("TUTOR_MESSAGE" to listOf("op-t")),
                cleanRedrawPinnedOperationIds = setOf("op-t"),
            ),
        )
        assertTrue("被存活清图任务钉住的 operation 必须放行：$viaPin", viaPin.isEmpty())
    }

    @Test
    fun `attempts survive only when their operation is kept or their output stays referenced`() {
        val problems = RestoreSoftReferenceRecheck.violationsFor(
            9,
            observations(
                figureOperationSourceKinds = mapOf("PROBLEM_REVISION" to listOf("op-kept")),
                attempts = listOf(
                    // operation 保留 → attempt 保留。
                    attempt("a-ok", "op-kept", null),
                    // operation 不在、产物也不在 R → 应被裁掉。
                    attempt("a-gone", "op-tutor", "asset-unreferenced"),
                ),
            ),
        )
        assertTrue(
            "未保留 operation 且产物不在 R 的 attempt 必须被拒：$problems",
            problems.any { it.contains("figure_generation_attempt 留有") && it.contains("a-gone") },
        )
        assertTrue(
            "保留 operation 的 attempt 不得误报：$problems",
            problems.none { it.contains("a-ok") },
        )
    }

    @Test
    fun `unknown figure source kind fails closed for subsets`() {
        val problems = RestoreSoftReferenceRecheck.violationsFor(
            13,
            observations(figureOperationSourceKinds = mapOf("FUTURE_SOURCE" to listOf("op-f"))),
        )
        assertTrue(
            "映射外 source_kind 必须被点名：$problems",
            problems.any { it.contains("figure_generation_operation 含映射外 source_kind=FUTURE_SOURCE") },
        )
    }
}
