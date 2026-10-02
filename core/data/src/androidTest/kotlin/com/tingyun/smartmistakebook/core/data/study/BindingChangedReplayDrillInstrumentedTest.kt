package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.ConfirmProblemOrganizationCommand
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeBindingSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemClassificationBindingRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentCodec
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.ClassificationDimension
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.KnowledgeNodeVerificationStatus
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.StructuredChoice
import com.tingyun.smartmistakebook.core.model.WritingLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **KF-32 端到端演练（真 Room）**：改绑 → 账本事件 → 全量重放 → 历史证据挂新节点 → 旧节点归零
 * → 重放幂等。
 *
 * 这是机制唯一组合级证明：`LearningProjectorReplayFingerprintTest` 证明重派生本身、
 * `BindingChangedReplayDrainerTest` 证明 drainer 接线，本条证明**真实数据库 +
 * 真实提交/确认/排空链路**上三件事一起成立：
 *
 * 1. `confirm`（离线纠正路径）在绑定集合真变化时落 `BINDING_CHANGED` + outbox（幂等重跑不落）；
 * 2. 下一次排空触发全量重放，历史 attempt 的证据按当前绑定挂到新节点；
 * 3. 旧节点在投影表里**消失**（重放从空表累加 + 差集删除，不新增清零路径）；
 * 4. 合并 × 改绑组合：新绑定指向的节点若已被取代，重放派生 + successors 链一起生效。
 */
@RunWith(AndroidJUnit4::class)
class BindingChangedReplayDrillInstrumentedTest {

    @Test
    fun rebindingMovesHistoricalEvidenceToTheNewNodeAndZeroesTheOldOne() = runBlocking {
        withDrill { context, databaseName, database, repository, scope ->
            seedStudyFacts(context, database, databaseName, drillSeed())
            repository.initialize()

            // 旧绑定 kc-old 上作答一次（判对）：账本 1 条 attempt + 投影里 kc-old 有掌握度。
            val submitted = repository.submitChoice(
                com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission(
                    requestId = "drill-submit-1",
                    presentationId = "presentation:drill",
                    practiceUnitId = UNIT_ID,
                    selectedChoiceId = "B",
                    responseOrdinal = 1,
                    durationSeconds = 30,
                    occurredAtEpochMillis = START_AT,
                ),
            )
            assertTrue("作答必须落成 attempt", submitted.created)
            assertEquals(
                "改绑前证据在 kc-old",
                setOf("kc-old"),
                currentMasteryNodeIds(database),
            )

            // 改绑：kc-old → kc-new（离线纠正通道，与自动接受共用 confirm）。
            val confirm = database.confirmProblemOrganization(
                rebindCommand(commandId = "drill-rebind-1", newKnowledgeNodeId = "kc-new"),
            )
            assertTrue("改绑确认必须成功", confirm.created)
            assertEquals(
                "改绑事件 + outbox 各落一行",
                1,
                countOutbox(context, databaseName, "BINDING_CHANGED"),
            )

            // 幂等重跑：同一 commandId 再确认一次——收据命中，不再落事件。
            val replay = database.confirmProblemOrganization(
                rebindCommand(commandId = "drill-rebind-1", newKnowledgeNodeId = "kc-new"),
            )
            assertFalse("同一 command 重放不得再创建", replay.created)
            assertEquals(
                "无改绑（幂等重跑）不追加事件",
                1,
                countOutbox(context, databaseName, "BINDING_CHANGED"),
            )

            // 下一次排空：全量重放 → 历史证据重挂 kc-new，kc-old 归零（投影行消失）。
            val second = repository.submitChoice(
                com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission(
                    requestId = "drill-submit-2",
                    presentationId = "presentation:drill-2",
                    practiceUnitId = UNIT_ID,
                    selectedChoiceId = "B",
                    responseOrdinal = 1,
                    durationSeconds = 20,
                    occurredAtEpochMillis = START_AT + 60_000L,
                ),
            )
            assertTrue(second.created)

            val nodesAfterReplay = currentMasteryNodeIds(database)
            assertEquals(
                "重放后历史证据与新证据都挂在 kc-new，旧节点 kc-old 已从投影表消失",
                setOf("kc-new"),
                nodesAfterReplay,
            )
            val newState = requireNotNull(
                database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID),
            ).snapshot.knowledgeMasteryStates.getValue("kc-new")
            assertEquals(
                "两条证据（改绑前 + 改绑后）都算在 kc-new 上",
                2,
                newState.independentCorrectObservations.size,
            )

            // 重放幂等：再走一次排空，投影逐位不变。
            val snapshotBefore = requireNotNull(
                database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID),
            )
            repository.submitChoice(
                com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission(
                    requestId = "drill-submit-3",
                    presentationId = "presentation:drill-3",
                    practiceUnitId = UNIT_ID,
                    selectedChoiceId = "B",
                    responseOrdinal = 1,
                    durationSeconds = 15,
                    occurredAtEpochMillis = START_AT + 120_000L,
                ),
            )
            val snapshotAfter = requireNotNull(
                database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID),
            )
            assertEquals(
                "再排空不得改变已重放好的归属（增量在重放结果上继续）",
                setOf("kc-new"),
                snapshotAfter.snapshot.knowledgeMasteryStates.keys,
            )
            assertEquals(
                "kc-old 仍然不在",
                null,
                snapshotAfter.snapshot.knowledgeMasteryStates["kc-old"],
            )
            assertEquals(
                "kc-new 的证据继续累加（1 条新增）",
                newState.independentCorrectObservations.size + 1,
                snapshotAfter.snapshot.knowledgeMasteryStates.getValue("kc-new")
                    .independentCorrectObservations.size,
            )
            assertEquals(
                "重放幂等：同一账本前缀的归属不变（只增新事件）",
                setOf("kc-new"),
                snapshotBefore.snapshot.knowledgeMasteryStates.keys,
            )
        }
    }

    @Test
    fun aMergeFollowedByARebindLandsOnTheSuccessor() = runBlocking {
        withDrill { context, databaseName, database, repository, scope ->
            // **先合并、再改绑**（B4 验收的"合并 × 改绑组合"）：证据原本挂在 kc-old 上；
            // 内容调和把 kc-old 退役并指向 kc-final；随后这道题被改绑到 kc-final。
            // 重放重派生（当前绑定 = kc-final）与"旧节点归零"必须一起成立。
            //
            // 顺序必须是"合并先于改绑"：确认会触发仓库观察任务的异步排空
            // （`StudyExperienceObservationJobs` → `publishReadySnapshot` → drain），
            // 那次重放可能抢在改绑之后的任何一步之前跑；取代链先到位，所有时点的重放结果才一致。
            // （反向顺序不可用：确认命令不能携带已退役节点——`sameAcceptedFact` 把 status /
            // superseded_by 算作已接受事实的一部分，退役节点无法被命令重新断言为 ACTIVE。）
            seedStudyFacts(context, database, databaseName, drillSeed())
            repository.initialize()

            val submitted = repository.submitChoice(
                com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission(
                    requestId = "merge-drill-submit-1",
                    presentationId = "presentation:merge-drill",
                    practiceUnitId = UNIT_ID,
                    selectedChoiceId = "B",
                    responseOrdinal = 1,
                    durationSeconds = 30,
                    occurredAtEpochMillis = START_AT,
                ),
            )
            assertTrue(submitted.created)

            // 先合并：kc-old → kc-final（取代链经内容调和写入，既有机制）。
            markSuperseded(databaseName, context, from = "kc-old", to = "kc-final")
            assertEquals(
                "取代链必须对库读可见（重放把它当输入之一）",
                "kc-final",
                database.readKnowledgeNodeSuccessors()["kc-old"],
            )

            // 再改绑：kc-old → kc-final（存活节点，命令合法）。
            database.confirmProblemOrganization(
                rebindCommand(commandId = "merge-drill-rebind", newKnowledgeNodeId = "kc-final"),
            )

            repository.submitChoice(
                com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission(
                    requestId = "merge-drill-submit-2",
                    presentationId = "presentation:merge-drill-2",
                    practiceUnitId = UNIT_ID,
                    selectedChoiceId = "B",
                    responseOrdinal = 1,
                    durationSeconds = 20,
                    occurredAtEpochMillis = START_AT + 60_000L,
                ),
            )

            val mastery = requireNotNull(
                database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID),
            ).snapshot.knowledgeMasteryStates
            assertEquals(
                "合并 + 改绑：证据落在 successor 上、旧节点归零。" +
                    "诊断：successors=${database.readKnowledgeNodeSuccessors()}；" +
                    "states=" + mastery.entries.joinToString { (node, state) ->
                        "$node{ckpt=${state.checkpointSequence}," +
                            "obs=${state.independentCorrectObservations.map { it.bindingId }}}"
                    },
                setOf("kc-final"),
                currentMasteryNodeIds(database),
            )
        }
    }

    @Test
    fun aConfirmationThatKeepsTheSameBindingSetDoesNotAppendAnEvent() = runBlocking {
        withDrill { context, databaseName, database, repository, scope ->
            seedStudyFacts(context, database, databaseName, drillSeed())
            repository.initialize()

            repository.submitChoice(
                com.tingyun.smartmistakebook.core.domain.StudyChoiceSubmission(
                    requestId = "no-change-submit-1",
                    presentationId = "presentation:no-change",
                    practiceUnitId = UNIT_ID,
                    selectedChoiceId = "B",
                    responseOrdinal = 1,
                    durationSeconds = 30,
                    occurredAtEpochMillis = START_AT,
                ),
            )
            assertEquals("作答先落进账本", 1, countOutbox(context, databaseName, "ATTEMPT"))

            // 再确认一次同一份绑定集合（新的 commandId 会让命令真的执行，而不是收据短路）：
            // 净效果是"集合没变"→ 不落 BINDING_CHANGED。
            database.confirmProblemOrganization(
                rebindCommand(commandId = "no-change-confirm", newKnowledgeNodeId = "kc-old"),
            )

            assertEquals(
                "无改绑不落事件（验收：无改绑不触发重放）",
                0,
                countOutbox(context, databaseName, "BINDING_CHANGED"),
            )
        }
    }

    private suspend fun withDrill(
        block: suspend (
            Context,
            String,
            StudyDatabasePort,
            RoomBackedStudyExperienceRepository,
            CoroutineScope,
        ) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "kf32-rebind-drill-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        val database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = RoomBackedStudyExperienceRepository(
            database = database,
            applicationScope = applicationScope,
        )
        try {
            block(context, databaseName, database, repository, applicationScope)
        } finally {
            repository.close()
            database.close()
            applicationScope.cancel()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun currentMasteryNodeIds(database: StudyDatabasePort): Set<String> =
        requireNotNull(
            database.readCurrentLearnerSnapshot("study-experience-v1", LEARNER_ID),
        ).snapshot.knowledgeMasteryStates.keys

    /** 直接读账本 outbox（演练的"事件真的落了一行"断言；按 kind 计数）。 */
    private fun countOutbox(context: Context, databaseName: String, eventKind: String): Int {
        val sqlite = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        )
        return try {
            sqlite.rawQuery(
                "SELECT COUNT(*) FROM projection_outbox WHERE event_kind = ?",
                arrayOf(eventKind),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } finally {
            sqlite.close()
        }
    }

    /** 取代链走内容调和的既有机制（这里只写一行 `superseded_by`，与调和落库同形）。 */
    private fun markSuperseded(databaseName: String, context: Context, from: String, to: String) {
        val sqlite = android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE,
        )
        try {
            sqlite.execSQL(
                "UPDATE knowledge_node SET superseded_by = ?, status = 'RETIRED' WHERE knowledge_node_id = ?",
                arrayOf(to, from),
            )
        } finally {
            sqlite.close()
        }
    }

    private fun rebindCommand(commandId: String, newKnowledgeNodeId: String) =
        ConfirmProblemOrganizationCommand(
            commandId = commandId,
            payloadFingerprint = commandId.replace(Regex("[^0-9a-f]"), "a").padEnd(64, 'a').take(64),
            problemId = PROBLEM_ID,
            problemRevisionId = REVISION_ID,
            practiceUnitId = UNIT_ID,
            // 节点已在 drillSeed 里落库（本演练只走近"改绑"，不引入新节点）：命令携带**同一个**
            // 节点事实（同 stableCode / 同 canonicalName / 同 subject / 同 parent），落库时
            // 主键命中 → insert 被忽略，`sameAcceptedFact` 校验通过。
            // 校验器要求"可见 topic 节点的 stableCode 与 KNOWLEDGE 分类的 labelId 完全一致"
            // （`visibleTopicNodeIds == knowledgeLabels`），所以分类的 labelId 用同一个
            // `drillNodeStableCode`。
            knowledgeNodes = listOf(
                drillNode(
                    newKnowledgeNodeId,
                    DRILL_TARGET_DISPLAY_NAME,
                    createdAtEpochMillis = CONFIRM_AT,
                ),
            ),
            knowledgeBindings = listOf(
                KnowledgeBindingSeedRecord(
                    bindingId = "binding:drill:$newKnowledgeNodeId",
                    practiceUnitId = UNIT_ID,
                    knowledgeNodeId = newKnowledgeNodeId,
                    basisRevisionId = REVISION_ID,
                    strength = 1.0,
                    sourceType = "USER_CORRECTED",
                    taxonomyVersion = "user-corrected-v1",
                    acceptedAtEpochMillis = CONFIRM_AT,
                ),
            ),
            classifications = listOf(
                ProblemClassificationBindingRecord(
                    bindingId = "classification:drill:chapter",
                    problemId = PROBLEM_ID,
                    basisRevisionId = REVISION_ID,
                    dimension = ClassificationDimension.CHAPTER.name,
                    labelId = "math:chapter:drill",
                    displayName = "演练章节",
                    taxonomyVersion = "user-corrected-v1",
                    acceptanceSource = "USER_CORRECTED",
                    acceptedAtEpochMillis = CONFIRM_AT,
                ),
                ProblemClassificationBindingRecord(
                    bindingId = "classification:drill:knowledge",
                    problemId = PROBLEM_ID,
                    basisRevisionId = REVISION_ID,
                    dimension = ClassificationDimension.KNOWLEDGE.name,
                    labelId = drillNodeStableCode(newKnowledgeNodeId),
                    displayName = DRILL_TARGET_DISPLAY_NAME,
                    taxonomyVersion = "user-corrected-v1",
                    acceptanceSource = "USER_CORRECTED",
                    acceptedAtEpochMillis = CONFIRM_AT,
                ),
            ),
            relations = emptyList(),
            acceptedAtEpochMillis = CONFIRM_AT,
        )

    private fun drillSeed(): StudySeedBundle {
        val blocks = listOf(
            ContentBlock.Paragraph("kf32-stem", "下列哪一项是闭区间最值比较的正确做法？"),
            ContentBlock.ChoiceGroup(
                id = "kf32-choices",
                promptMarkdown = "请选择一个答案",
                choices = listOf(
                    StructuredChoice(id = "A", markdown = "只比较驻点"),
                    StructuredChoice(id = "B", markdown = "同时比较驻点与端点"),
                    StructuredChoice(id = "C", markdown = "只比较端点"),
                    StructuredChoice(id = "D", markdown = "取端点平均值"),
                ),
            ),
        )
        val document = CapturedQuestionDocument(
            document = QuestionDocument(
                id = "document:kf32:r1",
                title = "闭区间最值",
                blocks = blocks,
            ),
            blockEvidence = blocks.map { block ->
                QuestionBlockEvidence(
                    blockId = block.id,
                    sourceAssetId = "kf32-source",
                    sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                    writingLayer = WritingLayer.PRINTED,
                    provenance = QuestionBlockProvenance.USER_CORRECTION,
                    reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                )
            },
        )
        return StudySeedBundle(
            problems = listOf(
                ProblemSeedRecord(
                    problemId = PROBLEM_ID,
                    canonicalFingerprint = "fp-kf32",
                    subject = "MATH",
                    createdAtEpochMillis = SEED_AT,
                ),
            ),
            revisions = listOf(
                ProblemRevisionSeedRecord(
                    revisionId = REVISION_ID,
                    problemId = PROBLEM_ID,
                    revisionNumber = 1,
                    title = "闭区间最值",
                    problemMarkdown = "下列哪一项是闭区间最值比较的正确做法？",
                    questionDocumentSnapshot = CapturedQuestionDocumentCodec.encode(document),
                    answerSpecId = "answer:kf32",
                    answerSpecSnapshot = "{\"schema\":\"single-choice.v1\",\"correctChoiceId\":\"B\"}",
                    answerVerificationStatus = StudyDbValue.VerificationStatus.VERIFIED,
                    sourceType = "TEST_LOCAL",
                    sourceReference = null,
                    contentFingerprint = CapturedQuestionDocumentFingerprint.of(document),
                    createdAtEpochMillis = SEED_AT,
                ),
            ),
            practiceUnits = listOf(
                PracticeUnitSeedRecord(
                    practiceUnitId = UNIT_ID,
                    problemId = PROBLEM_ID,
                    problemRevisionId = REVISION_ID,
                    unitKey = "whole",
                    unitKind = "WHOLE_PROBLEM",
                    title = "闭区间最值",
                    promptMarkdown = "下列哪一项是闭区间最值比较的正确做法？",
                    estimatedSeconds = 120,
                    createdAtEpochMillis = SEED_AT,
                ),
            ),
            errorBookEntries = listOf(
                ErrorBookEntrySeedRecord(
                    entryId = "entry:kf32",
                    practiceUnitId = UNIT_ID,
                    problemId = PROBLEM_ID,
                    currentRevisionId = REVISION_ID,
                    sourceKey = "test:kf32",
                    acceptedAtEpochMillis = SEED_AT,
                    updatedAtEpochMillis = SEED_AT,
                ),
            ),
            knowledgeNodes = listOf(
                drillNode("kc-old", "旧目标"),
                drillNode("kc-new", "新目标"),
                drillNode("kc-merged", "被合并目标"),
                drillNode("kc-final", "链尾目标"),
            ),
            knowledgeBindings = listOf(
                KnowledgeBindingSeedRecord(
                    bindingId = "binding:kf32:old",
                    practiceUnitId = UNIT_ID,
                    knowledgeNodeId = "kc-old",
                    basisRevisionId = REVISION_ID,
                    strength = 1.0,
                    sourceType = "TEST_LOCAL",
                    taxonomyVersion = "kf32-taxonomy-v1",
                    acceptedAtEpochMillis = SEED_AT,
                ),
            ),
        )
    }

    /**
     * 演练的节点事实（种子与 confirm 命令共用同一形状）。
     *
     * `createdAtEpochMillis` 是**命令侧必须等于 command.acceptedAtEpochMillis** 的校验项
     * （`validateCommand` 的 "Every accepted knowledge node must be complete and current"），
     * 种子侧用 SEED_AT、命令侧用 CONFIRM_AT；两者其余身份字段逐位相同，落库时主键命中 →
     * insert 被忽略、`sameAcceptedFact` 通过（该方法显式容忍 createdAt/taxonomy/displayName/
     * verificationStatus 的差异）。
     */
    private fun drillNode(
        nodeId: String,
        displayName: String,
        createdAtEpochMillis: Long = SEED_AT,
    ) = KnowledgeNodeSeedRecord(
        knowledgeNodeId = nodeId,
        stableCode = drillNodeStableCode(nodeId),
        subject = "MATH",
        displayName = displayName,
        // canonicalName 必须显式给：记录默认值是 displayName，而种子与命令的 displayName
        // 不同（"旧目标" vs "演练改绑目标"），默认值会让 sameAcceptedFact 把同一个节点判成
        // "已存在但载荷不同"。这里按节点 id 取稳定值，两侧逐位相同。
        canonicalName = "演练节点 $nodeId",
        parentKnowledgeNodeId = null,
        verificationStatus = KnowledgeNodeVerificationStatus.USER_CONFIRMED.name,
        taxonomyVersion = "kf32-taxonomy-v1",
        createdAtEpochMillis = createdAtEpochMillis,
    )

    /** 节点 stableCode 的单源：种子的节点行与 confirm 命令的 KNOWLEDGE 分类 labelId 必须一致。 */
    private fun drillNodeStableCode(nodeId: String) = "math:drill:$nodeId"

    private companion object {
        const val LEARNER_ID = "learner:local"
        const val PROBLEM_ID = "problem:kf32"
        const val REVISION_ID = "revision:kf32"
        const val UNIT_ID = "practice:kf32:whole"
        const val DRILL_TARGET_DISPLAY_NAME = "演练改绑目标"
        const val SEED_AT = 1_768_010_000_000L
        const val START_AT = 1_768_010_400_000L
        const val CONFIRM_AT = 1_768_020_000_000L
    }
}
