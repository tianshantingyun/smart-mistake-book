package com.tingyun.smartmistakebook.core.database

import androidx.room3.executeSQL
import androidx.room3.withWriteTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.database.port.MistakeRevisionWritePort
import com.tingyun.smartmistakebook.core.database.port.ReviseCommittedProblemCommand
import com.tingyun.smartmistakebook.core.database.port.ReviseCommittedProblemResult
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocument
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentCodec
import com.tingyun.smartmistakebook.core.model.CapturedQuestionDocumentFingerprint
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.NormalizedSourceRegion
import com.tingyun.smartmistakebook.core.model.QuestionBlockEvidence
import com.tingyun.smartmistakebook.core.model.QuestionBlockProvenance
import com.tingyun.smartmistakebook.core.model.QuestionBlockReviewStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.VisualRole
import com.tingyun.smartmistakebook.core.model.WritingLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PRD-03 database proof: controlled revision append + CAS + durable receipt + old-basis
 * invalidation, against a real in-memory Room database.
 */
@RunWith(AndroidJUnit4::class)
class ProblemRevisionEditDatabaseInstrumentedTest {
    private lateinit var context: android.content.Context
    private lateinit var store: StudyDatabasePort

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = StudyDatabaseFactory.openInMemory(context)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun editAppendsImmutableRevisionAndInvalidatesOnlyTheOldBasis() = runBlocking {
        seedCommittedProblem()
        seedOldBasisWork()

        val edited = document("第二版题面")
        val result = store.writer().reviseCommittedProblem(command(edited, commandId = "cmd-1"))

        val saved = result as ReviseCommittedProblemResult.Saved
        assertTrue(saved.created)
        assertEquals(2, saved.record.revisionNumber)
        assertEquals(REVISION_TWO, saved.record.problemRevisionId)
        assertEquals(REVISION_ONE, saved.record.previousRevisionId)

        val current = checkNotNull(store.readMistakeDetail(ENTRY_ID))
        assertEquals(REVISION_TWO, current.problemRevisionId)
        assertEquals(2, current.revisionNumber)
        assertEquals(CapturedQuestionDocumentFingerprint.of(edited), current.contentFingerprint)
        assertEquals(
            "题面确实换成了新内容",
            CapturedQuestionDocumentCodec.decode(current.questionDocumentSnapshot.orEmpty()),
            edited,
        )
        // Original photo copied to the new revision; generated clean image is not.
        assertEquals(listOf(ASSET_ONE), current.sourceAssets.map { it.sourceAsset.sourceAssetId })
        assertEquals("QUESTION_SOURCE", current.sourceAssets.single().role)

        val old = checkNotNull(store.readExactMistakeDetail(ENTRY_ID, PROBLEM_ID, REVISION_ONE))
        assertEquals(REVISION_ONE, old.problemRevisionId)
        assertEquals(
            "旧 revision 不被改写",
            CapturedQuestionDocumentFingerprint.of(document("第一版题面")),
            old.contentFingerprint,
        )
        assertEquals(
            setOf(ASSET_ONE, ASSET_CLEAN),
            old.sourceAssets.map { it.sourceAsset.sourceAssetId }.toSet(),
        )

        val history = store.readMistakeRevisionHistory(ENTRY_ID)
        assertEquals(listOf(REVISION_TWO, REVISION_ONE), history.map { it.problemRevisionId })
        assertEquals(listOf(2, 1), history.map { it.revisionNumber })
        assertEquals(listOf(true, false), history.map { it.isCurrent })

        assertEquals("CANCELLED", scalar("SELECT status FROM model_task WHERE task_id = 'task-1'"))
        assertEquals("1", scalar("SELECT state_version FROM model_task WHERE task_id = 'task-1'"))
        assertEquals(
            "1",
            scalar("SELECT COUNT(*) FROM model_task_event WHERE task_id = 'task-1' " +
                "AND previous_status = 'WAITING_FOR_MODEL' AND next_status = 'CANCELLED'"),
        )
        assertEquals(
            "SUPERSEDED",
            scalar("SELECT status FROM clean_redraw_task WHERE task_id = 'clean-1'"),
        )
        assertEquals(
            "STALE",
            scalar("SELECT status FROM problem_relation WHERE relation_id = 'relation-1'"),
        )

        // A late generated image for the old revision cannot attach to the book's current record.
        assertFalse(
            store.attachCleanRedrawAsset(
                REVISION_ONE,
                com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord(
                    sourceAssetId = "late-clean",
                    contentSha256 = "e".repeat(64),
                    relativePath = "source-assets/late.jpg",
                    mimeType = "image/jpeg",
                    byteSize = 4_096,
                    width = 1_200,
                    height = 1_600,
                    sourceType = "CLEAN_REDRAW",
                    createdAtEpochMillis = 9_000,
                ),
            ),
        )
        assertNull(
            scalar("SELECT problem_revision_id FROM problem_revision_source_asset " +
                "WHERE source_asset_id = 'late-clean'"),
        )
    }

    /**
     * T2a（4E WG-06）：用户改题面时只允许携带**源照片**（QUESTION_SOURCE）与**源证据**
     * （SOURCE_EVIDENCE）。干净题面重绘、讲解图、题图都冻结在旧题面上：带过去会让新题面
     * 显示旧内容，导出也会把讲解图塞进题目区。
     */
    @Test
    fun textRevisionOnlyCarriesSourcePhotosAndSourceEvidence() = runBlocking {
        seedCommittedProblem()
        val room = store as RoomStudyDatabase
        room.database.withWriteTransaction {
            listOf(
                "asset-diagram" to "QUESTION_DIAGRAM",
                "asset-explain" to "TUTOR_EXPLANATION",
                "asset-evidence" to "SOURCE_EVIDENCE",
                "asset-clean" to "CLEAN_IMAGE",
            ).forEachIndexed { index, (assetId, role) ->
                // content_sha256 有唯一约束：每个夹具资产必须用不同哈希，否则第二条会被
                // INSERT OR IGNORE 静默丢掉，后续引用行触发 FK 失败（夹具问题，非产品行为）。
                val contentSha256 = "${index + 1}".repeat(64)
                executeSQL(
                    """
                    INSERT INTO canonical_source_asset (
                        source_asset_id, content_sha256, relative_path, mime_type, byte_size,
                        width, height, source_type, created_at_epoch_millis
                    ) VALUES ('$assetId', '$contentSha256', 'source-assets/$assetId.png',
                        'image/png', 2048, 1024, 1024, 'GENERATED_FIGURE', 1000)
                    """.trimIndent(),
                )
                executeSQL(
                    """
                    INSERT INTO problem_revision_source_asset (
                        problem_revision_id, source_asset_id, role, ordinal
                    ) VALUES ('$REVISION_ONE', '$assetId', '$role', $index)
                    """.trimIndent(),
                )
            }
        }

        val result = store.writer().reviseCommittedProblem(
            command(document("第二版题面"), commandId = "cmd-roles"),
        )
        assertTrue(result is ReviseCommittedProblemResult.Saved)

        val current = checkNotNull(store.readMistakeDetail(ENTRY_ID))
        assertEquals(
            "只带源照片与源证据",
            setOf("QUESTION_SOURCE", "SOURCE_EVIDENCE"),
            current.sourceAssets.map { it.role }.toSet(),
        )
        assertEquals(
            setOf(ASSET_ONE, "asset-evidence"),
            current.sourceAssets.map { it.sourceAsset.sourceAssetId }.toSet(),
        )
        val old = checkNotNull(store.readExactMistakeDetail(ENTRY_ID, PROBLEM_ID, REVISION_ONE))
        assertEquals(
            "旧 revision 的五类引用一条不少、也不被改写",
            setOf(
                "QUESTION_SOURCE",
                "QUESTION_DIAGRAM",
                "TUTOR_EXPLANATION",
                "SOURCE_EVIDENCE",
                "CLEAN_IMAGE",
            ),
            old.sourceAssets.map { it.role }.toSet(),
        )
    }

    @Test
    fun machineCheckableQuestionEditStopsReusingTheOldAnswerBasis() = runBlocking {
        seedCommittedProblem()
        (store as RoomStudyDatabase).database.withWriteTransaction {
            executeSQL(
                """
                UPDATE problem_revision
                SET answer_spec_id = 'answer-spec-1',
                    answer_spec_snapshot = '{"correctChoiceId":"a"}',
                    answer_verification_status = 'VERIFIED'
                WHERE revision_id = '$REVISION_ONE'
                """.trimIndent(),
            )
        }
        assertEquals(
            "answer-spec-1",
            store.readPracticeUnitAssessment(PRACTICE_UNIT_ID)?.answerSpecId,
        )

        val result = store.writer().reviseCommittedProblem(
            command(document("第二版题面"), commandId = "cmd-verified"),
        )
        assertTrue(result is ReviseCommittedProblemResult.Saved)

        val current = checkNotNull(store.readMistakeDetail(ENTRY_ID))
        assertEquals(REVISION_TWO, current.problemRevisionId)
        assertEquals("practice-edit-$REVISION_TWO", current.practiceUnitId)
        assertNull(
            "新版不能再拿到旧版已验证答案",
            store.readPracticeUnitAssessment(current.practiceUnitId)?.answerSpecId,
        )
        assertEquals(
            "旧题面自己的判分依据仍然可读",
            "answer-spec-1",
            store.readPracticeUnitAssessment(PRACTICE_UNIT_ID)?.answerSpecId,
        )
    }

    /**
     * T2a（4E WG-06）：挂接口按调用方给的角色落库（默认仍是 CLEAN_IMAGE），且仍只挂到
     * **书里当前显示的修订**——晚到结果不许污染已被取代的旧 revision。
     */
    @Test
    fun roleAwareAttachmentRecordsTheGivenRoleOnTheCurrentRevision() = runBlocking {
        seedCommittedProblem()
        val explanation = CanonicalSourceAssetRecord(
            sourceAssetId = "asset-explain-attach",
            contentSha256 = "e".repeat(64),
            relativePath = "source-assets/explain.png",
            mimeType = "image/png",
            byteSize = 2_048,
            width = 1_024,
            height = 1_024,
            sourceType = "GENERATED_FIGURE",
            createdAtEpochMillis = 2_000,
        )

        assertTrue(
            store.attachCleanRedrawAsset(
                revisionId = REVISION_ONE,
                asset = explanation,
                role = "TUTOR_EXPLANATION",
            ),
        )
        val detail = checkNotNull(store.readMistakeDetail(ENTRY_ID))
        assertTrue(
            detail.sourceAssets.any { asset ->
                asset.sourceAsset.sourceAssetId == "asset-explain-attach" &&
                    asset.role == "TUTOR_EXPLANATION"
            },
        )

        assertFalse(
            "当前修订换了以后，晚到结果不能再挂旧 revision",
            store.attachCleanRedrawAsset(
                revisionId = "revision-not-current",
                asset = explanation.copy(sourceAssetId = "asset-late-attach"),
            ),
        )
    }

    /**
     * F7（WG-06）：同一 revision 的 CLEAN_IMAGE 是"当前干净题面"的单值事实。连续两次重绘
     * 成功挂接后，旧链接必须被新链接取代——否则详情/导出会继续拿第一张旧图。
     * 旧 canonical 资产行保留（审计 attempt.output_asset_id 仍引用它）。
     */
    @Test
    fun reattachingACleanRedrawReplacesThePreviousCurrentImage() = runBlocking {
        seedCommittedProblem()
        val first = CanonicalSourceAssetRecord(
            sourceAssetId = "asset-clean-first",
            contentSha256 = "1".repeat(64),
            relativePath = "source-assets/clean-first.png",
            mimeType = "image/png",
            byteSize = 1_024,
            width = 512,
            height = 512,
            sourceType = "GENERATED_FIGURE",
            createdAtEpochMillis = 1_000,
        )
        val second = first.copy(
            sourceAssetId = "asset-clean-second",
            contentSha256 = "2".repeat(64),
            relativePath = "source-assets/clean-second.png",
            createdAtEpochMillis = 2_000,
        )

        assertTrue(store.attachCleanRedrawAsset(REVISION_ONE, first))
        assertTrue(store.attachCleanRedrawAsset(REVISION_ONE, second))

        val detail = checkNotNull(store.readMistakeDetail(ENTRY_ID))
        val cleanLinks = detail.sourceAssets
            .filter { it.role == VisualRole.CLEAN_IMAGE.name }
            .map { it.sourceAsset.sourceAssetId }
        assertEquals(
            "新重绘必须取代旧的当前题面链接",
            listOf(second.sourceAssetId),
            cleanLinks,
        )
    }

    @Test
    fun retriedCommandReturnsTheExistingReceiptAndNeverWritesTwice() = runBlocking {
        seedCommittedProblem()
        val edited = document("第二版题面")
        val first = store.writer().reviseCommittedProblem(command(edited, commandId = "cmd-1"))
        assertTrue((first as ReviseCommittedProblemResult.Saved).created)

        // A retry with a new wall-clock time but the same command id + payload.
        val retried = store.writer().reviseCommittedProblem(
            command(edited, commandId = "cmd-1", revisedAt = 50_000),
        )
        val receipt = retried as ReviseCommittedProblemResult.Saved
        assertFalse(receipt.created)
        assertEquals(REVISION_TWO, receipt.record.problemRevisionId)
        assertEquals(2, receipt.record.revisionNumber)
        assertEquals(
            "2",
            scalar("SELECT COUNT(*) FROM problem_revision WHERE problem_id = '$PROBLEM_ID'"),
        )

        // Same command id with different content is an explicit conflict, not a second write.
        val conflicting = store.writer().reviseCommittedProblem(
            command(document("完全不同的题面"), commandId = "cmd-1", revisedAt = 60_000),
        )
        assertTrue(conflicting is ReviseCommittedProblemResult.DuplicateCommandPayloadConflict)
        assertEquals(
            "2",
            scalar("SELECT COUNT(*) FROM problem_revision WHERE problem_id = '$PROBLEM_ID'"),
        )
    }

    @Test
    fun concurrentEditsAcceptOnlyTheCorrectExpectedRevision() = runBlocking {
        seedCommittedProblem()
        val first = document("A 的修改")
        val second = document("B 的修改")

        val results = coroutineScope {
            listOf(
                async(Dispatchers.IO) {
                    store.writer().reviseCommittedProblem(
                        command(first, commandId = "cmd-a", newRevisionId = "revision-a"),
                    )
                },
                async(Dispatchers.IO) {
                    store.writer().reviseCommittedProblem(
                        command(second, commandId = "cmd-b", newRevisionId = "revision-b"),
                    )
                },
            ).map { it.await() }
        }

        assertEquals(1, results.count { it is ReviseCommittedProblemResult.Saved })
        assertEquals(1, results.count { it is ReviseCommittedProblemResult.Conflict })
        assertEquals(
            "2",
            scalar("SELECT COUNT(*) FROM problem_revision WHERE problem_id = '$PROBLEM_ID'"),
        )
        val conflict = results.filterIsInstance<ReviseCommittedProblemResult.Conflict>().single()
        assertEquals(REVISION_ONE, conflict.expectedRevisionId)
        assertEquals(2, conflict.currentRevisionNumber)
    }

    @Test
    fun revisionEditSurvivesProcessStyleCloseAndReopen() = runBlocking {
        val databaseName = "revision-edit-reopen-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        var database: StudyDatabasePort = StudyDatabaseFactory.open(context, databaseName)
        try {
            seedCommittedProblem(database)
            val edited = document("重开后的第二版")
            val result = database.writer().reviseCommittedProblem(
                command(edited, commandId = "cmd-reopen", newRevisionId = REVISION_TWO),
            )
            assertTrue(result is ReviseCommittedProblemResult.Saved)
            database.close()

            database = StudyDatabaseFactory.open(context, databaseName)
            val reopened = checkNotNull(database.readMistakeDetail(ENTRY_ID))
            assertEquals(REVISION_TWO, reopened.problemRevisionId)
            assertEquals(2, reopened.revisionNumber)
            assertEquals(CapturedQuestionDocumentFingerprint.of(edited), reopened.contentFingerprint)
            assertEquals(
                listOf(REVISION_TWO, REVISION_ONE),
                database.readMistakeRevisionHistory(ENTRY_ID).map { it.problemRevisionId },
            )
            val old = checkNotNull(
                database.readExactMistakeDetail(ENTRY_ID, PROBLEM_ID, REVISION_ONE),
            )
            assertEquals(
                CapturedQuestionDocumentFingerprint.of(document("第一版题面")),
                old.contentFingerprint,
            )
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun seedCommittedProblem(database: StudyDatabasePort = store) {
        database.createProblemDraft(createDraftCommand())
        assertTrue(
            database.reviseProblemDraft(
                ReviseProblemDraftCommand(
                    draftId = DRAFT_ONE,
                    expectedRevisionNumber = 1,
                    revision = ProblemDraftRevisionRecord(
                        draftId = DRAFT_ONE,
                        revisionNumber = 2,
                        basisRevisionNumber = 1,
                        subject = "MATH",
                        title = "第一版",
                        questionDocument = document("第一版题面"),
                        documentFingerprint = CapturedQuestionDocumentFingerprint.of(
                            document("第一版题面"),
                        ),
                        author = StudyDbValue.ProblemDraftAuthor.USER,
                        createdAtEpochMillis = 2_000,
                    ),
                ),
            ).created,
        )
        database.commitProblemDraft(
            CommitProblemDraftCommand(
                commandId = "commit-r1",
                draftId = DRAFT_ONE,
                expectedRevisionNumber = 2,
                problemId = PROBLEM_ID,
                problemRevisionId = REVISION_ONE,
                practiceUnitId = PRACTICE_UNIT_ID,
                errorBookEntryId = ENTRY_ID,
                estimatedSeconds = 180,
                committedAtEpochMillis = 3_000,
            ),
        )
    }

    private suspend fun seedOldBasisWork() {
        val room = store as RoomStudyDatabase
        room.database.withWriteTransaction {
            executeSQL(
                """
                INSERT INTO canonical_source_asset (
                    source_asset_id, content_sha256, relative_path, mime_type, byte_size,
                    width, height, source_type, created_at_epoch_millis
                ) VALUES ('$ASSET_CLEAN', '${"c".repeat(64)}', 'source-assets/${"c".repeat(64)}.jpg',
                    'image/jpeg', 4096, 1200, 1600, 'CLEAN_REDRAW', 1000)
                """.trimIndent(),
            )
            executeSQL(
                """
                INSERT INTO problem_revision_source_asset (
                    problem_revision_id, source_asset_id, role
                ) VALUES ('$REVISION_ONE', '$ASSET_CLEAN', 'CLEAN_IMAGE')
                """.trimIndent(),
            )
            executeSQL(
                """
                INSERT INTO model_task_operation (
                    operation_fingerprint, subject_id, task_kind, dispatch_count,
                    created_at_epoch_millis, updated_at_epoch_millis
                ) VALUES ('operation-1', '$REVISION_ONE', 'PROBLEM_CLASSIFY', 0, 1000, 1000)
                """.trimIndent(),
            )
            executeSQL(
                """
                INSERT INTO model_task (
                    task_id, request_id, request_fingerprint, operation_fingerprint,
                    request_snapshot, task_kind, subject_id, tutor_response_ordinal,
                    status, state_version, stage, user_message, attempt_count,
                    provider_snapshot, output_snapshot, failure_code, failure_message,
                    failure_retryable, created_at_epoch_millis, updated_at_epoch_millis
                ) VALUES ('task-1', 'request-1', 'request-fp', 'operation-1', '{}',
                    'PROBLEM_CLASSIFY', '$REVISION_ONE', NULL, 'WAITING_FOR_MODEL', 0,
                    'WAITING', 'waiting', 0, NULL, NULL, NULL, NULL, NULL, 1000, 1000)
                """.trimIndent(),
            )
            executeSQL(
                """
                INSERT INTO clean_redraw_task (
                    task_id, revision_id, source_asset_id, source_image_sha256,
                    request_fingerprint, subject_id, status, attempt_count,
                    last_failure_code, created_at_epoch_millis, updated_at_epoch_millis
                ) VALUES ('clean-1', '$REVISION_ONE', '$ASSET_ONE', '${"a".repeat(64)}',
                    '${"b".repeat(64)}', 'fixture-subject', 'PENDING', 0, NULL, 1000, 1000)
                """.trimIndent(),
            )
            executeSQL(
                """
                INSERT INTO problem_relation (
                    relation_id, source_problem_id, target_problem_id, relation_type, status,
                    source_basis_revision_id, target_basis_revision_id, confidence,
                    created_at_epoch_millis, updated_at_epoch_millis
                ) VALUES ('relation-1', '$PROBLEM_ID', '$PROBLEM_ID', 'VARIANT_OF', 'ACTIVE',
                    '$REVISION_ONE', '$REVISION_ONE', 0.8, 1000, 1000)
                """.trimIndent(),
            )
        }
    }

    private fun createDraftCommand(): CreateProblemDraftCommand {
        val initial = document("第一版题面")
        return CreateProblemDraftCommand(
            sourceAsset = CanonicalSourceAssetRecord(
                sourceAssetId = ASSET_ONE,
                contentSha256 = "a".repeat(64),
                relativePath = "source-assets/${"a".repeat(64)}.jpg",
                mimeType = "image/jpeg",
                byteSize = 4_096,
                width = 1_200,
                height = 1_600,
                sourceType = StudyDbValue.SourceAssetType.PHOTO_PICKER,
                createdAtEpochMillis = 1_000,
            ),
            draftId = DRAFT_ONE,
            origin = StudyDbValue.CaptureOrigin.LIBRARY,
            initialRevision = ProblemDraftRevisionRecord(
                draftId = DRAFT_ONE,
                revisionNumber = 1,
                basisRevisionNumber = null,
                subject = null,
                title = "待校对题目",
                questionDocument = initial,
                documentFingerprint = CapturedQuestionDocumentFingerprint.of(initial),
                author = StudyDbValue.ProblemDraftAuthor.CAPTURE_IMPORT,
                createdAtEpochMillis = 1_000,
            ),
        )
    }

    private fun command(
        document: CapturedQuestionDocument,
        commandId: String,
        revisedAt: Long = 7_000,
        newRevisionId: String = REVISION_TWO,
    ) = ReviseCommittedProblemCommand(
        commandId = commandId,
        sourceReference = "revision-edit:$commandId:$REVISION_ONE:${"f".repeat(64)}",
        entryId = ENTRY_ID,
        expectedRevisionId = REVISION_ONE,
        expectedRevisionNumber = 1,
        newRevisionId = newRevisionId,
        title = "第二版",
        problemMarkdown = document.document.blocks.joinToString("\n") { block ->
            (block as ContentBlock.Paragraph).markdown
        },
        questionDocumentSnapshot = CapturedQuestionDocumentCodec.encode(document),
        contentFingerprint = CapturedQuestionDocumentFingerprint.of(document),
        revisedAtEpochMillis = revisedAt,
    )

    private suspend fun scalar(sql: String): String? {
        var value: String? = null
        (store as RoomStudyDatabase).database.withRawConnection(isReadOnly = true) { connection ->
            connection.usePrepared(sql) { statement ->
                if (statement.step()) value = statement.getText(0)
            }
        }
        return value
    }

    private fun StudyDatabasePort.writer(): MistakeRevisionWritePort = this as MistakeRevisionWritePort

    private fun document(markdown: String): CapturedQuestionDocument = CapturedQuestionDocument(
        document = QuestionDocument(
            id = "document-exact",
            title = "精确版本读取",
            blocks = listOf(ContentBlock.Paragraph("stem", markdown)),
        ),
        blockEvidence = listOf(
            QuestionBlockEvidence(
                blockId = "stem",
                sourceAssetId = ASSET_ONE,
                sourceRegion = NormalizedSourceRegion(0.0, 0.0, 1.0, 1.0),
                writingLayer = WritingLayer.PRINTED,
                provenance = QuestionBlockProvenance.USER_CORRECTION,
                reviewStatus = QuestionBlockReviewStatus.USER_CONFIRMED,
                producerVersion = "revision-edit-test-v1",
            ),
        ),
    )

    private companion object {
        const val ENTRY_ID = "entry-exact"
        const val PROBLEM_ID = "problem-exact"
        const val REVISION_ONE = "revision-exact-1"
        const val REVISION_TWO = "revision-exact-2"
        const val PRACTICE_UNIT_ID = "practice-exact"
        const val DRAFT_ONE = "draft-exact-1"
        const val ASSET_ONE = "asset-exact-1"
        const val ASSET_CLEAN = "asset-clean-1"
    }
}
