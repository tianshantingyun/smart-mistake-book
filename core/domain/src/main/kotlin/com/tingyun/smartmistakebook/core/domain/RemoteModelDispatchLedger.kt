package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose

/**
 * 远程模型派发账本端口（4E.1 WG-01 / 提示词 §5）。
 *
 * 消灭的具体失败：图像生成/编辑此前**从未接入任何持久预算**——每次 `POST /v1/images/...`
 * 都是一次真实的外部模型执行，却可以在同一逻辑操作里无限重试、换方法后重新计数。
 * 本端口把"这一次图像请求是否已被持久预留"变成出网前的强制门：
 * - 协调器先 [reserve]；被拒即**不打开后端、不发 HTTP**；
 * - 图像通道再核对绑定（operation/method/源证据/配置），拒绝旁路调用。
 *
 * 复用既有持久记录（本波次不新增表/列）：
 * - 模型任务父操作 → `model_task_operation.dispatch_count`（同一逻辑操作最多 6 次；讲题留 1 次终答）；
 * - 净化作业 → `clean_redraw_task.attempt_count`（持久作业已领取的那一次尝试）。
 *
 * operationId 不含 method：换方法/失败重试仍是同一操作身份，不复制新预算。
 */
interface RemoteModelDispatchLedger {
    suspend fun reserve(request: RemoteModelDispatchRequest): RemoteModelDispatchDecision
}

/** 远程派发的类别。本波次只有图像生成（标记在预留请求/预留结果上，不新增 ModelTaskKind）。 */
enum class RemoteModelDispatchKind {
    IMAGE_GENERATION,
}

/**
 * 一次预留请求：绑定逻辑操作、父操作、方法、用途、源证据与配置。
 *
 * [sourceImageSha256] 是**实际要发送的源图字节**的 sha（无源图为 null）；
 * [configurationFingerprint] 是当前图像端点/模型的非秘密指纹（不含 API key）。
 * 两者都由协调器从真实请求/后端计算，不接受模型自报。
 */
data class RemoteModelDispatchRequest(
    val operationId: String,
    val parent: FigureDispatchParent,
    val remoteKind: RemoteModelDispatchKind,
    val method: FigureGenerationMethod,
    val purpose: FigureGenerationPurpose,
    /** 与请求同一版本；账本据此拒绝"旧规则下的新派发"。 */
    val policyVersion: String,
    val sourceImageSha256: String? = null,
    val configurationFingerprint: String,
    val occurredAtEpochMillis: Long,
) {
    init {
        require(operationId.isNotBlank()) { "A dispatch operation id must not be blank" }
        require(policyVersion.isNotBlank()) { "A dispatch policy version must not be blank" }
        require(sourceImageSha256 == null || sourceImageSha256.length == 64) {
            "A dispatch source sha must be SHA-256"
        }
        require(configurationFingerprint.isNotBlank()) {
            "A dispatch reservation needs a configuration fingerprint"
        }
        require(occurredAtEpochMillis >= 0) { "A dispatch reservation time must not be negative" }
    }
}

/**
 * 已授予的持久预留。[attemptOrdinal] 是同一逻辑操作内的第几次远程派发（1 起）。
 *
 * [reservationId] 由持久记录稳定派生，便于日志/审计关联；它不是可伪造的授权凭证——
 * 后端的强制门是"协调器只在 Granted 之后才构造绑定 + 通道复核绑定"。
 */
data class RemoteModelDispatchReservation(
    val reservationId: String,
    val operationId: String,
    val parent: FigureDispatchParent,
    val remoteKind: RemoteModelDispatchKind,
    val method: FigureGenerationMethod,
    val purpose: FigureGenerationPurpose,
    val policyVersion: String,
    val sourceImageSha256: String?,
    val configurationFingerprint: String,
    val attemptOrdinal: Int,
    val reservedAtEpochMillis: Long,
) {
    init {
        require(reservationId.isNotBlank()) { "A dispatch reservation id must not be blank" }
        require(operationId.isNotBlank()) { "A dispatch reservation operation id must not be blank" }
        require(policyVersion.isNotBlank()) { "A dispatch reservation policy version must not be blank" }
        require(attemptOrdinal >= 1) { "A dispatch reservation attempt ordinal starts at 1" }
        require(sourceImageSha256 == null || sourceImageSha256.length == 64) {
            "A dispatch reservation source sha must be SHA-256"
        }
        require(configurationFingerprint.isNotBlank()) {
            "A dispatch reservation needs a configuration fingerprint"
        }
    }
}

/** 账本结论：授予（带持久预留）或拒绝（带不变量原因）。 */
sealed interface RemoteModelDispatchDecision {
    data class Granted(val reservation: RemoteModelDispatchReservation) : RemoteModelDispatchDecision

    data class Denied(
        val code: RemoteModelDispatchDenialCode,
        val message: String,
    ) : RemoteModelDispatchDecision
}

/** 拒绝码：有界、可审计。 */
enum class RemoteModelDispatchDenialCode {
    /** 父操作不存在（没有可复用的持久身份/预算）。 */
    PARENT_NOT_FOUND,

    /** 父操作存在但不可预留（例如净化作业的领取状态不匹配）。 */
    PARENT_NOT_RESERVABLE,

    /** 逻辑操作的远程派发预算已耗尽（或只剩必须保留的终答位）。 */
    BUDGET_EXHAUSTED,

    /** 并发/竞态导致预留未生效。 */
    CONFLICT,

    /** 本装配没有可用的持久账本（fail-closed）。 */
    NOT_SUPPORTED,
}

/**
 * fail-closed 的默认实现：没有注入账本时一律拒绝，任何图像 HTTP 调用都会因此为 0。
 * 生产装配必须注入真实账本（`RoomFigureDispatchLedger`），不得把本对象当生产默认。
 */
object UnavailableRemoteModelDispatchLedger : RemoteModelDispatchLedger {
    override suspend fun reserve(request: RemoteModelDispatchRequest): RemoteModelDispatchDecision =
        RemoteModelDispatchDecision.Denied(
            code = RemoteModelDispatchDenialCode.NOT_SUPPORTED,
            message = "本装配没有可用的持久派发账本，未出网",
        )
}
