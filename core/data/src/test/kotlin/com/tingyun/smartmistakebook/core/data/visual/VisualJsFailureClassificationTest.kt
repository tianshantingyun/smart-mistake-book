package com.tingyun.smartmistakebook.core.data.visual

import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [classifyVisualJsFailure] 的 JVM 纯函数用例（批次 22 T2；批次 24 T2 增补 R2 证据标记）。
 *
 * **不变量**：本类不实例化 [com.dokar.quickjs.QuickJs]，不触发 `System.loadLibrary("quickjs")`。
 * 只构造库的异常值类型（[QuickJsException]/[QuickJsInterruptedException]，纯 Kotlin 类），
 * 因此可在无原生库的 JVM 门执行。
 *
 * 覆盖三层：类型分支（中断优先于消息）、消息分支（严格前缀/沙箱/程序错误）、
 * UNKNOWN 兜底（消息丢失且无堆证据 → 内部 UNATTRIBUTED，对外折 INVALID_PROGRAM + 前缀）。
 * 阈值边界与 `unlimited` 上限也在本类钉住，防止有人为让设备变绿而下调 90%。
 *
 * **批次 24 T2 增补（R2 `docs/research/2026-10-26-quickjs-memory-accounting.md`）**：
 * 以 `mallocSize >= 90% mallocLimit` 比值作**主证据**的归因必须带
 * [VisualJsCalibration.PENDING_CALIBRATION]，因为该比值在设备上尚未校准成功——E-02 的
 * 未锚定夹具只读到 `mallocSize=177216`（1.06%）。直接由引擎原文
 * `InternalError: out of memory` 支撑的归因不带此标记。这里同时固化 E-02 的数值形状，
 * 防止后续把"读到基线"误读成阈值问题。
 */
class VisualJsFailureClassificationTest {

    private val phase = "prepare"

    private fun nearLimitHeap() = HeapSnapshot(
        mallocSize = 16_700_000L,
        mallocLimit = 16_777_216L,
    )

    private fun classify(
        message: String?,
        heap: HeapSnapshot? = null,
        error: QuickJsException = QuickJsException(message),
    ): VisualJsFailure =
        classifyVisualJsFailure(phase, error, heap, cooperativeTimeoutMillis = 10_000L)

    // ------------------------------------------------------------ 类型分支

    @Test
    fun `interrupted type wins over an out of memory message`() {
        // 消息面完全像 OOM，但类型是协作中断：类型必须优先，不能被误归 MEMORY_LIMIT。
        val failure = classify(
            message = "InternalError: out of memory",
            heap = nearLimitHeap(),
            error = QuickJsInterruptedException("InternalError: out of memory"),
        )

        assertEquals(VisualJsFailureKind.COOPERATIVE_TIMEOUT, failure.kind)
        assertEquals(VisualExecutionRejection.PHASE_TIMEOUT, failure.reason)
        assertTrue(failure.diagnostics.contains("10000 ms"))
        assertFalse(failure.diagnostics.contains("[UNATTRIBUTED]"))
    }

    // ------------------------------------------------------------ 消息分支

    @Test
    fun `the engine internal error prefix is a memory limit`() {
        val failure = classify("InternalError: out of memory\n    at prepare")

        assertEquals(VisualJsFailureKind.MEMORY_LIMIT, failure.kind)
        assertEquals(VisualExecutionRejection.MEMORY_LIMIT, failure.reason)
        assertTrue(failure.diagnostics.contains("16777216"))
        assertTrue(failure.diagnostics.contains("out of memory"))
        // 引擎原文是直接证据，不依赖未校准的堆比值。
        assertEquals(VisualJsCalibration.NONE, failure.calibration)
        assertFalse(failure.diagnostics.contains("PENDING_CALIBRATION"))
    }

    @Test
    fun `a forged range error with the oom text stays an invalid program`() {
        // 旧实现 contains("out of memory") 会把这条误报成 MEMORY_LIMIT。
        val failure = classify("RangeError: out of memory")

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, failure.reason)
        assertFalse(failure.diagnostics.contains("[UNATTRIBUTED]"))
    }

    @Test
    fun `a bare out of memory string is not the engine oom prefix`() {
        val failure = classify("out of memory")

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
    }

    @Test
    fun `string too long is a program error even when the heap looks full`() {
        // 旧夹具红例的机制：单字符串长度上限（2^30-1）与堆上限无关；
        // 消息分支先于堆证据，堆再满也不得归 MEMORY_LIMIT。
        val failure = classify("InternalError: string too long\n    at prepare", nearLimitHeap())

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
        assertTrue(failure.diagnostics.contains("string too long"))
        assertEquals(VisualJsCalibration.NONE, failure.calibration)
    }

    @Test
    fun `a missing global is a sandbox violation`() {
        val failure = classify("ReferenceError: fetch is not defined")

        assertEquals(VisualJsFailureKind.SANDBOX_VIOLATION, failure.kind)
        assertEquals(VisualExecutionRejection.SANDBOX_VIOLATION, failure.reason)
        assertTrue(failure.diagnostics.contains("fetch"))
    }

    @Test
    fun `an ordinary type error keeps its bounded diagnostic`() {
        val failure = classify("TypeError: boom-js")

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
        assertTrue(failure.diagnostics.contains("boom-js"))
    }

    // ------------------------------------------------------------ 丢失哨兵 + 堆证据

    @Test
    fun `a lost message near the heap limit is attributed to memory`() {
        val failure = classify("null", nearLimitHeap())

        assertEquals(VisualJsFailureKind.MEMORY_LIMIT, failure.kind)
        assertEquals(VisualExecutionRejection.MEMORY_LIMIT, failure.reason)
        assertTrue(failure.diagnostics.contains("attributed by memoryUsage"))
        assertTrue(failure.diagnostics.contains("mallocSize=16700000"))
        assertTrue(failure.diagnostics.contains("mallocLimit=16777216"))
        // 比值是主证据：设备尚未校准成功，必须带 PENDING_CALIBRATION 标记。
        assertEquals(VisualJsCalibration.PENDING_CALIBRATION, failure.calibration)
        assertTrue(failure.diagnostics.contains("PENDING_CALIBRATION"))
    }

    @Test
    fun `the invalid evaluation handle sentinel is attributed by heap evidence`() {
        val failure = classify("Invalid evaluation handle: -1", nearLimitHeap())

        assertEquals(VisualJsFailureKind.MEMORY_LIMIT, failure.kind)
        assertTrue(failure.diagnostics.contains("attributed by memoryUsage"))
        assertEquals(VisualJsCalibration.PENDING_CALIBRATION, failure.calibration)
        assertTrue(failure.diagnostics.contains("PENDING_CALIBRATION"))
    }

    @Test
    fun `a java null message near the heap limit is attributed to memory`() {
        val failure = classify(null, nearLimitHeap())

        assertEquals(VisualJsFailureKind.MEMORY_LIMIT, failure.kind)
        assertEquals(VisualJsCalibration.PENDING_CALIBRATION, failure.calibration)
    }

    // ------------------------------------------------------------ UNKNOWN 兜底

    @Test
    fun `a lost message with a low heap is unattributed`() {
        val failure = classify("null", HeapSnapshot(mallocSize = 1_000_000L, mallocLimit = 16_777_216L))

        assertEquals(VisualJsFailureKind.UNATTRIBUTED, failure.kind)
        // 对外枚举不变：折成 INVALID_PROGRAM，靠诊断前缀区分。
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, failure.reason)
        assertTrue(failure.diagnostics.startsWith(UNATTRIBUTED_DIAGNOSTIC_PREFIX))
        assertTrue(failure.diagnostics.contains("mallocSize=1000000"))
        assertTrue(failure.diagnostics.contains("mallocLimit=16777216"))
        // 不做堆归因，也就没有"待校准的比值证据"。
        assertEquals(VisualJsCalibration.NONE, failure.calibration)
    }

    @Test
    fun `the batch23 unattributed witness is below the attribution bar`() {
        // E-02 设备实读（批次 23）：未锚定的 50 万节点链表在 OOM 后读到
        // mallocSize=177216（1.06% of 16 MiB），两批设备轨同值。该形状不可能产生
        // MEMORY_LIMIT——换夹具是唯一出路，不是下调 90% 阈值。
        val failure = classify("null", HeapSnapshot(mallocSize = 177_216L, mallocLimit = 16_777_216L))

        assertEquals(VisualJsFailureKind.UNATTRIBUTED, failure.kind)
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, failure.reason)
        assertTrue(failure.diagnostics.contains("mallocSize=177216"))
        assertEquals(VisualJsCalibration.NONE, failure.calibration)
    }

    @Test
    fun `a lost message without a heap snapshot is unattributed`() {
        val failure = classify("null", heap = null)

        assertEquals(VisualJsFailureKind.UNATTRIBUTED, failure.kind)
        assertEquals(VisualExecutionRejection.INVALID_PROGRAM, failure.reason)
        assertTrue(failure.diagnostics.startsWith(UNATTRIBUTED_DIAGNOSTIC_PREFIX))
        assertEquals(VisualJsCalibration.NONE, failure.calibration)
    }

    // ------------------------------------------------------------ 90% 阈值与上限

    @Test
    fun `the near limit threshold is inclusive at ninety percent`() {
        // 14_400_000 / 16_000_000 = 90%：含边界归属堆上限。
        val atThreshold = HeapSnapshot(mallocSize = 14_400_000L, mallocLimit = 16_000_000L)
        val justBelow = HeapSnapshot(mallocSize = 14_399_999L, mallocLimit = 16_000_000L)

        assertTrue(atThreshold.isNearLimit())
        assertFalse(justBelow.isNearLimit())
        // 边界内的归属仍是比值证据，带 PENDING_CALIBRATION；边界外落 UNATTRIBUTED。
        assertEquals(
            VisualJsCalibration.PENDING_CALIBRATION,
            classify("null", atThreshold).calibration,
        )
        assertEquals(VisualJsCalibration.NONE, classify("null", justBelow).calibration)
    }

    // ------------------------------------------------------------ SceneV1 编译器/SDK 类型化错误面（批次 27 T1）

    @Test
    fun `a scene compiler error code is an invalid scene`() {
        // 任务书裁定：非法场景 fail-closed 为类型化拒绝，不进 INVALID_PROGRAM 泛化。
        val failure = classify("SceneCompileError: SCENE_FIELD_UNKNOWN: scene: unknown field \"foo\"")

        assertEquals(VisualJsFailureKind.INVALID_SCENE, failure.kind)
        assertEquals(VisualExecutionRejection.INVALID_SCENE, failure.reason)
        assertTrue(failure.diagnostics.contains("SCENE_FIELD_UNKNOWN"))
        assertTrue(failure.diagnostics.length <= 512)
    }

    @Test
    fun `a scene sdk error code is an invalid scene`() {
        val failure = classify("SceneSdkError: SCENE_SDK_ARG: r must be within (0, 100000]")

        assertEquals(VisualJsFailureKind.INVALID_SCENE, failure.kind)
        assertEquals(VisualExecutionRejection.INVALID_SCENE, failure.reason)
        assertTrue(failure.diagnostics.contains("SCENE_SDK_ARG"))
    }

    @Test
    fun `a scene error mentioning a missing definition is not a sandbox violation`() {
        // 编译器诊断刻意避开 "not defined"，但同类措辞（"no definition in scene.paths"）
        // 仍须先命中 SCENE_* 令牌，不得被沙箱分支截走。
        val failure = classify(
            "SceneCompileError: SCENE_PATH_REF: scene.root: pathId \"p\" has no definition in scene.paths",
        )

        assertEquals(VisualJsFailureKind.INVALID_SCENE, failure.kind)
    }

    @Test
    fun `an interrupt carrying a scene code stays a phase timeout`() {
        // 类型优先不变量对新增分支同样成立。
        val failure = classify(
            message = "SceneCompileError: SCENE_FIELD_UNKNOWN: x",
            error = QuickJsInterruptedException("SceneCompileError: SCENE_FIELD_UNKNOWN: x"),
        )

        assertEquals(VisualJsFailureKind.COOPERATIVE_TIMEOUT, failure.kind)
        assertEquals(VisualExecutionRejection.PHASE_TIMEOUT, failure.reason)
    }

    @Test
    fun `program prose that merely mentions scene vocabulary without a code stays invalid program`() {
        // 无 SCENE_<族>_<码> 令牌的普通程序错误不换类别（严格令牌匹配，不是宽松关键词）。
        val failure = classify("TypeError: cannot read SCENE of undefined")

        assertEquals(VisualJsFailureKind.INVALID_PROGRAM, failure.kind)
    }
}
