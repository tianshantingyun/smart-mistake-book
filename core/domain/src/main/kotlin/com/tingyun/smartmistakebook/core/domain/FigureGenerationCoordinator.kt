package com.tingyun.smartmistakebook.core.domain

import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationOutcome
import com.tingyun.smartmistakebook.core.model.FigureGenerationProposal
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.FigureGenerationRequest

/**
 * 4E.1 的统一生图协调器端口（WG-01）。
 *
 * 所有生图入口（讲题 GENERATE_FIGURE、转写/净化分类后的生成、保存净化/重绘）只依赖这个端口：
 * 请求由宿主冻结，proposal 由承载该业务的既有模型任务/入口适配层取得。实现负责公共状态机：
 *
 * `请求 + proposal → policy 校验 → 持久派发预留 → 调用所选方法后端 → 统一产物/诊断`。
 *
 * 两条纪律：
 * - 实现**不按入口或 purpose 选择后端**；method 只来自通过校验的 proposal。
 * - 未取得持久预留时不得调用任何出网后端（“无预留 → 0 HTTP”）；本地 JS 执行不占远程派发，
 *   但本波次未接通 JS，实现应如实回 [com.tingyun.smartmistakebook.core.model.FigureGenerationStatus.UNAVAILABLE]。
 *
 * 实现位于 core:data（`DefaultFigureGenerationCoordinator`）；本端口不依赖 Android/数据库类型，
 * 便于 domain/feature 层只依赖抽象。
 */
interface FigureGenerationCoordinator {
    suspend fun generate(
        request: FigureGenerationRequest,
        proposal: FigureGenerationProposal,
    ): FigureGenerationOutcome
}

/**
 * 图像方法所需的**已预留派发上下文**：后端在 POST 之前必须收到它，并核对
 * operation/method/源证据/配置绑定。
 *
 * KD-59：构造器收窄为 `internal`——生产只能经 [fromReservation] 从账本授予的
 * [RemoteModelDispatchReservation] 派生；测试必须显式 opt-in [FigureDispatchBindingTestFactory]
 * 并使用 [forTest]（名字即声明，不得在生产路径调用）。
 * 后端实现仍应在发送前复核（拒绝旁路调用），不能信任"我拿到了一个对象"。
 */
data class FigureImageDispatchBinding internal constructor(
    /** 账本授予的预留 id（durable 记录派生的稳定 id，不是随机口令）。 */
    val reservationId: String,
    /** 宿主冻结的逻辑操作身份，不含 method。 */
    val operationId: String,
    val method: FigureGenerationMethod,
    val purpose: FigureGenerationPurpose,
    /** 同一逻辑操作内的第几次远程尝试（1 起）。 */
    val attemptOrdinal: Int,
    /** 与源图字节绑定的 sha；无源图时为 null。 */
    val sourceImageSha256: String?,
    /** 与当前图像端点/模型配置绑定的非秘密指纹。 */
    val configurationFingerprint: String,
) {
    init {
        require(reservationId.isNotBlank()) { "A figure dispatch reservation id must not be blank" }
        require(operationId.isNotBlank()) { "A figure dispatch operation id must not be blank" }
        require(method == FigureGenerationMethod.IMAGE_MODEL) {
            "An image dispatch binding can only carry IMAGE_MODEL"
        }
        require(attemptOrdinal >= 1) { "A figure dispatch attempt ordinal starts at 1" }
        require(sourceImageSha256 == null || sourceImageSha256.length == 64) {
            "A figure dispatch source sha must be SHA-256"
        }
        require(configurationFingerprint.isNotBlank()) {
            "A figure dispatch binding needs a configuration fingerprint"
        }
    }

    companion object {
        /**
         * 生产唯一构造入口：账本授予预留之后，按预留的身份派生绑定。
         *
         * 绑定字段与预留逐项一致（不复制成两份可漂移的事实）；协调器只在
         * `RemoteModelDispatchDecision.Granted` 分支调用它。
         */
        fun fromReservation(
            reservation: RemoteModelDispatchReservation,
        ): FigureImageDispatchBinding = FigureImageDispatchBinding(
            reservationId = reservation.reservationId,
            operationId = reservation.operationId,
            method = reservation.method,
            purpose = reservation.purpose,
            attemptOrdinal = reservation.attemptOrdinal,
            sourceImageSha256 = reservation.sourceImageSha256,
            configurationFingerprint = reservation.configurationFingerprint,
        )

        /**
         * 测试工厂：显式 opt-in [FigureDispatchBindingTestFactory] 才能调用。
         * 生产路径必须走 [fromReservation]（KD-59）。
         */
        @FigureDispatchBindingTestFactory
        fun forTest(
            reservationId: String,
            operationId: String,
            purpose: FigureGenerationPurpose,
            configurationFingerprint: String,
            sourceImageSha256: String? = null,
            method: FigureGenerationMethod = FigureGenerationMethod.IMAGE_MODEL,
            attemptOrdinal: Int = 1,
        ): FigureImageDispatchBinding = FigureImageDispatchBinding(
            reservationId = reservationId,
            operationId = operationId,
            method = method,
            purpose = purpose,
            attemptOrdinal = attemptOrdinal,
            sourceImageSha256 = sourceImageSha256,
            configurationFingerprint = configurationFingerprint,
        )
    }
}

/**
 * KD-59 的测试工厂 opt-in 标记。
 *
 * 为什么需要：`internal` 构造器在 Kotlin 多模块工程里只对 `core:domain` 自己可见，
 * 协调器在 `core:data`；测试同样在别的模块。把测试构造收拢到一个**名字可见**、
 * 必须显式 opt-in 的工厂里，既保留跨模块可测性，又让生产代码误用成为编译错误。
 */
@RequiresOptIn(
    message = "仅供测试构造派发绑定；生产路径必须来自账本授予的预留（fromReservation）",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class FigureDispatchBindingTestFactory
