package com.tingyun.smartmistakebook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 6B-3 自然语言改卷的模型层契约（批次 40 / WS4 项 23/24/27）。
 *
 * 钉住四件事：
 * 1. `PAPER_EDIT` 的持久名字（kind 名与 wire type）——它进 `model_task.task_kind`、
 *    备份包与逻辑操作指纹；
 * 2. 请求 schema 19 的构造期守卫：schema 18 的行不能携带 [PaperEditInput]；
 * 3. 快照的形状/预算（唯一 id、只读块、备注区、总量上限）；
 * 4. 输出形状门：白名单命令解析失败不可构造，超预算抛可重试的文本预算异常。
 */
class PaperEditTasksTest {

    @Test
    fun paperEditKindNameAndWireDiscriminatorsArePinned() {
        assertEquals("PAPER_EDIT", ModelTaskKind.PAPER_EDIT.name)

        val request = ModelTaskCodec.encodeRequest(request())
        assertTrue("输入 discriminator 必须稳定：$request", request.contains("\"type\":\"paper_edit_input\""))

        val output = ModelTaskCodec.encodeOutput(validOutput())
        assertTrue("输出 discriminator 必须稳定：$output", output.contains("\"type\":\"paper_edit_output\""))
    }

    @Test
    fun paperEditRequestAndOutputRoundTripAtSchemaNineteen() {
        val request = request()
        assertEquals(ModelTaskRequest.PAPER_EDIT_SCHEMA_VERSION, request.schemaVersion)

        val decodedRequest = ModelTaskCodec.decodeRequest(ModelTaskCodec.encodeRequest(request))
        assertEquals(request, decodedRequest)
        assertEquals(ModelTaskKind.PAPER_EDIT, decodedRequest.input.kind)
        assertEquals("paper-1", decodedRequest.input.subjectId)

        val decodedOutput = ModelTaskCodec.decodeOutput(ModelTaskCodec.encodeOutput(validOutput()))
        assertEquals(validOutput(), decodedOutput)
        assertEquals(
            ModelTaskLogicalOperationFingerprint.of(request.input),
            ModelTaskLogicalOperationFingerprint.of(decodedRequest.input),
        )
    }

    @Test
    fun aSchemaEighteenRequestCannotCarryAPaperEditInput() {
        val rejected = runCatching {
            ModelTaskRequest(
                schemaVersion = ModelTaskRequest.TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION,
                requestId = "paper-edit:legacy",
                input = input(),
                occurredAtEpochMillis = 1_000,
            )
        }
        assertTrue(
            "schema 18 的请求带改卷输入是自相矛盾的形状：${rejected.exceptionOrNull()}",
            rejected.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun operationFingerprintIgnoresTheEnvelopeAndFollowsThePaperContent() {
        val first = request(requestId = "paper-edit:first")
        val second = request(requestId = "paper-edit:second")
        assertEquals(
            "换 requestId 不是另一次逻辑操作",
            ModelTaskLogicalOperationFingerprint.of(first.input),
            ModelTaskLogicalOperationFingerprint.of(second.input),
        )
        assertNotEquals(
            "请求指纹包含 requestId， envelope 变化必须可区分",
            ModelTaskFingerprint.of(first),
            ModelTaskFingerprint.of(second),
        )
        assertNotEquals(
            "换一句话要求是另一次逻辑操作",
            ModelTaskLogicalOperationFingerprint.of(first.input),
            ModelTaskLogicalOperationFingerprint.of(
                input(instruction = "把第 1 题删掉"),
            ),
        )
    }

    @Test
    fun duplicateEntryAndBlockIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            input(
                questions = listOf(question(), question()),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            input(
                questions = listOf(
                    question().copy(
                        blocks = listOf(
                            PaperEditBlockSnapshot(
                                blockId = "stem",
                                kind = PaperEditBlockKind.PARAGRAPH,
                                text = "甲",
                            ),
                            PaperEditBlockSnapshot(
                                blockId = "stem",
                                kind = PaperEditBlockKind.PARAGRAPH,
                                text = "乙",
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun instructionNoteAndSnapshotBudgetsAreEnforced() {
        assertThrows(IllegalArgumentException::class.java) {
            input(instruction = "改".repeat(PaperEditInput.MAX_INSTRUCTION_CHARS + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            input(
                questions = listOf(question(note = "备".repeat(PaperEditInput.MAX_NOTE_CHARS + 1))),
                includeNote = true,
            )
        }
        // 每块都在文本预算内，但总量越过快照预算（12 000 字符）。
        assertThrows(IllegalArgumentException::class.java) {
            input(
                questions = listOf(
                    question(entryId = "entry-a", text = "甲".repeat(7_000)),
                    question(entryId = "entry-b", text = "乙".repeat(7_000)),
                ),
            )
        }
    }

    @Test
    fun readOnlyBlocksCannotCarryEditableTextOrChoices() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperEditBlockSnapshot(
                blockId = "figure-1",
                kind = PaperEditBlockKind.FIGURE,
                text = "把这张图换成另一张",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperEditBlockSnapshot(
                blockId = "image-1",
                kind = PaperEditBlockKind.IMAGE,
                choices = listOf(PaperEditChoiceSnapshot("a", "甲")),
            )
        }
    }

    @Test
    fun aPaperWithoutANoteAreaCannotCarryNotes() {
        assertThrows(IllegalArgumentException::class.java) {
            input(questions = listOf(question(note = "这题再练一次")), includeNote = false)
        }
        // 含备注区时允许备注。
        input(questions = listOf(question(note = "这题再练一次")), includeNote = true)
    }

    @Test
    fun completionValidatorBindsPaperEditInputToPaperEditOutput() {
        val request = request()
        assertTrue(ModelTaskCompletionValidator.validate(request, validOutput()).isEmpty())

        val issues = ModelTaskCompletionValidator.validate(
            request,
            ImagePipelineClassifyOutput(problemKind = ImagePipelineProblemKind.TEXT_ONLY),
        )
        assertEquals(
            setOf(ModelTaskCompletionIssueCode.REQUEST_OUTPUT_TYPE_MISMATCH),
            issues.map { it.code }.toSet(),
        )
    }

    @Test
    fun paperEditOutputRejectsShapeErrorsAndOverBudgetCommands() {
        // 白名单外的 type：形状错误，重发会逐字复现（网关归 INVALID_RESPONSE）。
        assertThrows(IllegalArgumentException::class.java) {
            PaperEditOutput(
                commandsJson = """{"commands":[{"type":"replace_image","entryId":"entry-a"}]}""",
                modelVersion = "fake/paper-v1",
            )
        }
        // 超预算：可重试的文本预算异常（网关归 INCOMPLETE_OUTPUT）。
        assertThrows(ModelTextBudgetExceededException::class.java) {
            PaperEditOutput(
                commandsJson = "x".repeat(PaperEditCommandParser.MAX_PAYLOAD_CHARS + 1),
                modelVersion = "fake/paper-v1",
            )
        }
        // 空命令是合法的"没有可执行修改"。
        PaperEditOutput(
            commandsJson = """{"commands":[]}""",
            modelVersion = "fake/paper-v1",
        )
    }

    @Test
    fun promptPolicyVersionIsPinned() {
        assertEquals("paper-edit-v1-whitelist-commands", ModelPromptPolicyVersions.PAPER_EDIT)
        assertEquals(
            ModelPromptPolicyVersions.PAPER_EDIT,
            ModelPromptPolicyVersions.currentFor(ModelTaskKind.PAPER_EDIT),
        )
        assertFalse(ModelPromptPolicyVersions.PAPER_EDIT.contains(" "))
    }

    private fun request(requestId: String = "paper-edit:request") = ModelTaskRequest(
        requestId = requestId,
        input = input(),
        occurredAtEpochMillis = 1_000,
    )

    private fun input(
        instruction: String = "把第 1 题标题改短",
        questions: List<PaperEditQuestionSnapshot> = listOf(question()),
        removedQuestions: List<PaperEditRemovedQuestion> = emptyList(),
        includeNote: Boolean = false,
    ) = PaperEditInput(
        paperId = "paper-1",
        revision = 3,
        instruction = instruction,
        questions = questions,
        removedQuestions = removedQuestions,
        includeNote = includeNote,
    )

    private fun question(
        entryId: String = "entry-a",
        text: String = "求函数的单调区间",
        note: String? = null,
    ) = PaperEditQuestionSnapshot(
        entryId = entryId,
        title = "二次函数综合题",
        blocks = listOf(
            PaperEditBlockSnapshot(
                blockId = "stem-$entryId",
                kind = PaperEditBlockKind.PARAGRAPH,
                text = text,
            ),
            PaperEditBlockSnapshot(
                blockId = "figure-$entryId",
                kind = PaperEditBlockKind.FIGURE,
            ),
        ),
        note = note,
    )

    private fun validOutput() = PaperEditOutput(
        commandsJson = """{"commands":[{"type":"remove_question","entryId":"entry-a"}]}""",
        modelVersion = "fake/paper-v1",
    )
}
