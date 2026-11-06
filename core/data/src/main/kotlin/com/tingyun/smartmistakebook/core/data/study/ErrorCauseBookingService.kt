package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.port.ErrorCauseWriteResult

/**
 * 生产装配接缝：**唯一**把 extractor → gate → retractor 串起来的自动落账入口
 * （批次 41 / WS6 item 41）。
 *
 * **它消灭的具体失败**：提取器、门、落账器是三个独立构件，若由 feature/app 各自拼装，
 * 调用方可以只接 [ErrorCauseRetractor] 而不接提取器（把任意构造的候选当"提取结果"），
 * 或自建 `ErrorCauseGate(ErrorCauseGateState(certified = true))` 绕过 D-1c 认证。
 * 本类只接受 [ErrorCauseExtractionInput]，候选在内部由 [ErrorCauseExtractor] 产生，
 * 再经**构造时注入的门**判定；调用方拿不到"跳过 extractor/gate 直接 book"的路径。
 *
 * **门仍是权威**：本类先调 [ErrorCauseGate.mayAutoBook] 只为把
 * [ErrorCauseBookingOutcome.GATE_REJECTED] 与数据库冲突区分开；真正落账仍走
 * [ErrorCauseRetractor.bookIfAllowed]，它会在同一事务边界内再判一次门。两次判定是
 * 确定性纯函数，不存在"先放行后拒绝"的漂移。
 *
 * **默认关闭**：不传 [gate] 时门是 [ErrorCauseGate] 默认态（`certified = false`），
 * 生产装配在 P0–P5 认证证据之前只能得到 [ErrorCauseBookingOutcome.GATE_REJECTED] 且零写入。
 *
 * **不碰作答/掌握度**：依赖闭包里没有 attempt/review/projection 写口（与
 * [ErrorCauseRetractor] 相同）；本接缝不提供撤回，撤回仍由调用方走
 * [ErrorCauseRetractor.retractByUser] / [ErrorCauseRetractor.retractForAnswerKeyCorrection]。
 *
 * 装配判据（供接线评审，不由本类强制）：
 * 1. 生产代码里自动落账的唯一调用点是 [proposeAndBook]；
 *    `bookIfAllowed` 只允许出现在本类与测试中（`grep -rn "bookIfAllowed(" -- core app feature`）；
 * 2. 生产构造不得显式传 `certified = true` 的门；认证状态只能来自持久化证据（当前不存在）；
 * 3. 落账后读侧只认 `readActiveBinding`（`state=ACTIVE` 且来源 observation 已 ACCEPTED）；
 * 4. 撤回后同一 scope 的 `active_key` 必须为空，重新落账须等新的独立负事件。
 */
class ErrorCauseBookingService(
    private val retractor: ErrorCauseRetractor,
    private val extractor: ErrorCauseExtractor = ErrorCauseExtractor(),
    private val gate: ErrorCauseGate = ErrorCauseGate(),
) {

    /** 门是否仍为默认关闭态（装配诊断用；生产在认证前必须为 true）。 */
    val isGateClosed: Boolean get() = gate.isDefaultClosed

    val gateVersion: String get() = gate.gateVersion

    suspend fun proposeAndBook(
        input: ErrorCauseExtractionInput,
        corroborationCount: Int,
        retractedRecently: Boolean,
        bindingId: String,
        decidedAtEpochMillis: Long,
    ): ErrorCauseBookingOutcome {
        val candidate = extractor.extract(input) ?: return ErrorCauseBookingOutcome.NO_CANDIDATE
        if (!gate.mayAutoBook(candidate, corroborationCount, retractedRecently)) {
            return ErrorCauseBookingOutcome.GATE_REJECTED
        }
        return when (
            retractor.bookIfAllowed(
                candidate = candidate,
                gate = gate,
                corroborationCount = corroborationCount,
                retractedRecently = retractedRecently,
                bindingId = bindingId,
                decidedAtEpochMillis = decidedAtEpochMillis,
            )
        ) {
            ErrorCauseWriteResult.APPLIED -> ErrorCauseBookingOutcome.BOOKED
            ErrorCauseWriteResult.CONFLICT,
            ErrorCauseWriteResult.NOT_FOUND -> ErrorCauseBookingOutcome.CONFLICT
        }
    }
}

/**
 * 接缝结果。区分"根本没有候选"、"候选被门拒绝"与"落账被账本拒绝"——三者对
 * 产品/诊断的含义不同，不能都折叠成一个 false。
 */
enum class ErrorCauseBookingOutcome {
    /** 提取器 abstain（题面修订/证据不足）；不产生任何行。 */
    NO_CANDIDATE,

    /** 门拒绝（默认关闭、CARELESS、SELF_REPORT、置信/佐证不足、迟滞）；不产生任何行。 */
    GATE_REJECTED,

    /** 已落账：observation ACCEPTED + binding ACTIVE。 */
    BOOKED,

    /** 门放行但账本冲突（同 scope 已有 ACTIVE、重放不一致等）；以账本现状为准。 */
    CONFLICT,
}
