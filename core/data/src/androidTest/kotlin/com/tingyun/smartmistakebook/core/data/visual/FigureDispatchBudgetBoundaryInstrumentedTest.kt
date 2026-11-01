package com.tingyun.smartmistakebook.core.data.visual

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.CreateModelTaskCommand
import com.tingyun.smartmistakebook.core.database.ReserveModelTaskRemoteDispatchCommand
import com.tingyun.smartmistakebook.core.database.TransitionModelTaskCommand
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDecision
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchDenialCode
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchKind
import com.tingyun.smartmistakebook.core.domain.RemoteModelDispatchRequest
import com.tingyun.smartmistakebook.core.model.FigureDispatchParent
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.FigureGenerationPolicyVersion
import com.tingyun.smartmistakebook.core.model.FigureGenerationPurpose
import com.tingyun.smartmistakebook.core.model.ImagePipelineClassifyInput
import com.tingyun.smartmistakebook.core.model.ModelTaskFingerprint
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskStage
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A-10 逐阈值边界（计划任务 3 第一组的**账本半边**；清单 :198 验收原文"第 7 次派发拒绝 /
 * 同 operationId 换 method 计数不重置"）。
 *
 * 载体：真 Room 库（`StudyDatabaseFactory.open` 同名开库惯例，见
 * `TutorVisualCommitRepositoryInstrumentedTest`）+ 生产账本 [RoomFigureDispatchLedger]。
 * 计数语义的真身在 `ModelTaskTransactionDao.reserveOperationRemoteDispatch` 的 SQL 里
 * （core/data JVM 侧没有能替代它的载体：fake port 只能证明 fake），所以这两个向量必须
 * 设备跑。本轮实查：仓内此前**没有任何**测试触达 `reserveOperationRemoteDispatch`
 * （grep main/androidTest 均零命中）——6 上限与"换 method 不重置"都是无测试语义。
 *
 * 每条向量灭掉的失败（先红后绿：落树前断言红）：
 * 1. 第 7 次派发拒——真实 SQL 边界 off-by-one 或 reservedLimit 计算错会让第 7 次照样授予；
 * 2. 换 method 不重置——"operationId 不含 method、换方法不复制新预算"（端口 KDoc :20）
 *    只是声明，无测试钉住；
 * 3. 终答保留位——讲题父操作必须在图像预算耗尽后仍留 1 次给终答（"文字降级并使用原派发
 *    余额终答"的账本半边；编排半边在 runner，属热区另轨）。批次 36 step20 该例红于夹具
 *    （seedModelTask 落库 WAITING_FOR_MODEL 撞 ReserveModelTaskRemoteDispatchCommand 的
 *    QUEUED 守卫，构造即抛）：修复走生产迁移链路落 QUEUED（见 [seedModelTask]），并把
 *    "耗尽→终答位保留"补成完整证明——终答预留落 durable 运行快照 + 终答消费后预算闭死。
 */
@RunWith(AndroidJUnit4::class)
class FigureDispatchBudgetBoundaryInstrumentedTest {

    @Test
    fun aSeventhOperationDispatchIsDeniedWithTheRealCount() = runBlocking {
        withDatabase { database ->
            val ledger = RoomFigureDispatchLedger(database, now = { clock += 1_000; clock })
            val parent = seedModelTask(database, "boundary-7th")

            val ordinals = (1..6).map { attempt ->
                val decision = ledger.reserve(dispatchRequest(parent.requestId, FigureGenerationMethod.IMAGE_MODEL))
                val granted = decision as RemoteModelDispatchDecision.Granted
                attempt to granted.reservation
            }
            assertEquals(
                "前 6 次授予且序号单调 1..6",
                (1..6).toList(),
                ordinals.map { it.first },
            )
            assertTrue(ordinals.all { it.second.operationId == "figure-op:boundary" })

            val seventh = ledger.reserve(dispatchRequest(parent.requestId, FigureGenerationMethod.IMAGE_MODEL))

            val denied = seventh as RemoteModelDispatchDecision.Denied
            assertEquals(RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED, denied.code)
            assertTrue(
                "拒绝必须给出真实计数：${denied.message}",
                denied.message.contains("已派发 6") &&
                    denied.message.contains("上限 6 次") &&
                    denied.message.contains("未出网"),
            )
        }
    }

    @Test
    fun switchingMethodDoesNotResetTheOperationDispatchCount() = runBlocking {
        withDatabase { database ->
            val ledger = RoomFigureDispatchLedger(database, now = { clock += 1_000; clock })
            val parent = seedModelTask(database, "boundary-method")

            // 同一 operationId：IMAGE_MODEL → JS → IMAGE_MODEL 交替，计数必须单调累计。
            val methodSequence = listOf(
                FigureGenerationMethod.IMAGE_MODEL,
                FigureGenerationMethod.IMAGE_MODEL,
                FigureGenerationMethod.JS,
                FigureGenerationMethod.IMAGE_MODEL,
                FigureGenerationMethod.JS,
                FigureGenerationMethod.IMAGE_MODEL,
            )
            val ordinals = methodSequence.mapIndexed { index, method ->
                val decision = ledger.reserve(dispatchRequest(parent.requestId, method))
                val granted = decision as RemoteModelDispatchDecision.Granted
                assertEquals(
                    "第 ${index + 1} 次预留必须回显所请求的方法",
                    method,
                    granted.reservation.method,
                )
                granted.reservation.attemptOrdinal
            }
            assertEquals("换 method 不得重置：序号跨方法单调 1..6", (1..6).toList(), ordinals)

            val seventh = ledger.reserve(dispatchRequest(parent.requestId, FigureGenerationMethod.JS))

            val denied = seventh as RemoteModelDispatchDecision.Denied
            assertEquals(RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED, denied.code)
            assertTrue(
                "换到 JS 的第 7 次同样被拒（预算是操作身份的，不是方法的）：${denied.message}",
                denied.message.contains("已派发 6"),
            )
        }
    }

    @Test
    fun theChatFinalAnswerSlotSurvivesFigureBudgetExhaustion() = runBlocking {
        withDatabase { database ->
            val ledger = RoomFigureDispatchLedger(database, now = { clock += 1_000; clock })
            val parent = seedModelTask(database, "boundary-final-answer")

            repeat(5) { attempt ->
                val decision = ledger.reserve(
                    dispatchRequest(parent.requestId, FigureGenerationMethod.IMAGE_MODEL, chatFinalAnswer = true),
                )
                assertTrue(
                    "讲题父操作的第 ${attempt + 1} 次图像派发应授予",
                    decision is RemoteModelDispatchDecision.Granted,
                )
            }
            val sixth = ledger.reserve(
                dispatchRequest(parent.requestId, FigureGenerationMethod.IMAGE_MODEL, chatFinalAnswer = true),
            )
            val denied = sixth as RemoteModelDispatchDecision.Denied
            assertEquals(RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED, denied.code)
            assertTrue(
                "拒绝必须说明终答保留位：${denied.message}",
                denied.message.contains("上限 5 次") &&
                    denied.message.contains("另为讲题终答保留 1 次"),
            )

            // 耗尽后的终答位仍在：任务级预留（终答那一派）用第 6 个名额成功。
            val finalAnswer = database.reserveModelTaskRemoteDispatch(
                ReserveModelTaskRemoteDispatchCommand(
                    taskId = parent.taskId,
                    expectedStateVersion = parent.stateVersion,
                    expectedStatus = parent.status,
                    provider = providerSnapshot(),
                    occurredAtEpochMillis = ++clock,
                ),
            )
            assertTrue("图像耗尽后终答位必须可用：applied=${finalAnswer.applied}", finalAnswer.applied)
            assertEquals(6, finalAnswer.logicalDispatchCount)
            assertFalse(finalAnswer.budgetExhausted)
            // 子门①：终答那一派落的是 durable 运行快照（端口对 applied 预留的硬要求：
            // snapshot.status == RUNNING 且 attemptCount == logicalDispatchCount）。
            // 只看 applied=true 会放过"预留成功但任务行没推进"的假绿——编排半边接不上。
            assertEquals(ModelTaskStatus.RUNNING, finalAnswer.snapshot.status)
            assertEquals(6, finalAnswer.snapshot.attemptCount)
            // 子门②：终答位是"留给终答的那一次"，不是可反复取用的余量。终答消费后预算必须
            // 闭死——任何图像派发（含仍声明终答保留的）都要被拒，且给出真实计数 6。没有这条，
            // "保留"证明不了：第 6 个名额可能只是没被计数的余量，终答取用后图像还能接着用。
            val afterFinalAnswer = ledger.reserve(
                dispatchRequest(parent.requestId, FigureGenerationMethod.IMAGE_MODEL, chatFinalAnswer = true),
            )
            val closed = afterFinalAnswer as RemoteModelDispatchDecision.Denied
            assertEquals(RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED, closed.code)
            assertTrue(
                "终答消费后图像预算必须闭死在真实计数 6：${closed.message}",
                closed.message.contains("已派发 6") && closed.message.contains("未出网"),
            )
            val stealAttempt = ledger.reserve(
                dispatchRequest(parent.requestId, FigureGenerationMethod.IMAGE_MODEL, chatFinalAnswer = false),
            )
            val steal = stealAttempt as RemoteModelDispatchDecision.Denied
            assertEquals(RemoteModelDispatchDenialCode.BUDGET_EXHAUSTED, steal.code)
            assertTrue(
                "不声明终答保留的图像派发同样拿不到第 7 次：${steal.message}",
                steal.message.contains("已派发 6"),
            )
        }
    }

    // ---- 夹具 ----

    private class SeededParent(
        val requestId: String,
        val taskId: String,
        val stateVersion: Long,
        val status: ModelTaskStatus,
    )

    /**
     * 一次真实的模型任务行 = 账本的可预留持久身份（operationFingerprint 从请求派生）。
     *
     * 先红后绿的红的构造（批次 36 step20 实测 1 红，归因夹具）：`createModelTask` 落库初态是
     * WAITING_FOR_MODEL（`ModelTaskTransactionDao.create`，status 列写
     * `ModelTaskStatus.WAITING_FOR_MODEL.name`），而"终答位"向量要构造的
     * `ReserveModelTaskRemoteDispatchCommand` 的 init 守卫
     * `require(expectedStatus == ModelTaskStatus.QUEUED)` 在**构造时**就抛
     * `IllegalArgumentException: Only a queued model task may reserve a remote dispatch`——
     * 断言一行都没跑到就红。
     *
     * 修法走生产链路，不开旁路：真实执行环在派发前经 `prepareForExecution` 把
     * WAITING_FOR_MODEL 迁到 QUEUED（`RoomModelTaskRepository.prepareForExecution`：
     * PREPARING / "正在准备" / attemptCount 保留）。夹具用同一条
     * `TransitionModelTaskCommand` 落 QUEUED，并回传迁移后的 stateVersion/status——
     * 任务级预留的比较交换（stateVersion+status 双 CAS）对的就是这份真实快照。
     */
    private suspend fun seedModelTask(database: StudyDatabasePort, tag: String): SeededParent {
        val request = ModelTaskRequest(
            requestId = "task-$tag",
            input = ImagePipelineClassifyInput(
                sourceAssetId = "asset-$tag",
                imageWidth = 1_080,
                imageHeight = 1_440,
            ),
            occurredAtEpochMillis = 1_000,
        )
        val created = database.createModelTask(
            CreateModelTaskCommand(
                taskId = "task-$tag",
                request = request,
                requestFingerprint = ModelTaskFingerprint.of(request),
                occurredAtEpochMillis = 1_000,
            ),
        )
        assertTrue("夹具任务行必须写成功", created.applied)
        val queued = database.transitionModelTask(
            TransitionModelTaskCommand(
                taskId = created.snapshot.taskId,
                expectedStateVersion = created.snapshot.stateVersion,
                expectedStatus = created.snapshot.status,
                nextStatus = ModelTaskStatus.QUEUED,
                stage = ModelTaskStage.PREPARING,
                userMessage = "正在准备",
                attemptCount = created.snapshot.attemptCount,
                occurredAtEpochMillis = ++clock,
            ),
        )
        assertTrue(
            "夹具任务必须经生产迁移进入 QUEUED：applied=${queued.applied}",
            queued.applied,
        )
        assertEquals(ModelTaskStatus.QUEUED, queued.snapshot.status)
        return SeededParent(
            requestId = request.requestId,
            taskId = created.snapshot.taskId,
            stateVersion = queued.snapshot.stateVersion,
            status = queued.snapshot.status,
        )
    }

    private fun dispatchRequest(
        requestId: String,
        method: FigureGenerationMethod,
        chatFinalAnswer: Boolean = false,
    ): RemoteModelDispatchRequest = RemoteModelDispatchRequest(
        operationId = "figure-op:boundary",
        parent = FigureDispatchParent.ModelTask(
            requestId = requestId,
            reserveChatFinalAnswer = chatFinalAnswer,
        ),
        remoteKind = RemoteModelDispatchKind.IMAGE_GENERATION,
        method = method,
        purpose = FigureGenerationPurpose.SOURCE_RECONSTRUCTION,
        policyVersion = FigureGenerationPolicyVersion.VALUE,
        configurationFingerprint = "config-fingerprint",
        occurredAtEpochMillis = ++clock,
    )

    private fun providerSnapshot() = ProviderCapabilitySnapshot(
        providerId = "provider-boundary",
        providerDisplayName = "边界测试",
        modelId = "model-boundary",
        supportedTasks = setOf(ModelTaskKind.IMAGE_PIPELINE_CLASSIFY),
        supportsImageInput = true,
        supportsStructuredOutput = false,
        supportsStreaming = false,
    )

    private var clock: Long = 10_000L

    /** 与 `TutorVisualCommitRepositoryInstrumentedTest.withDatabase` 同一惯例：随机名、用后删。 */
    private suspend fun withDatabase(block: suspend (StudyDatabasePort) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "batch36-dispatch-boundary-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val store = StudyDatabaseFactory.open(context, databaseName)
        try {
            block(store)
        } finally {
            store.close()
            context.deleteDatabase(databaseName)
        }
    }
}
