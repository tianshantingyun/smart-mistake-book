package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.model.ModelPromptPolicyVersions
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.PaperEditBlockKind
import com.tingyun.smartmistakebook.core.model.PaperEditBlockSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditChoiceSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditInput
import com.tingyun.smartmistakebook.core.model.PaperEditQuestionSnapshot
import com.tingyun.smartmistakebook.core.model.PaperEditRemovedQuestion
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 批次 41 / WS7 项 45：改卷提示词的**版本 golden**。
 *
 * 消灭的具体失败：提示词文本被改动、版本串却留在 `paper-edit-v1-…`。版本串会进逐次出网清单
 * （`ModelEgressManifest.promptPolicyVersion`，读回时逐字比对，见 `ModelEgress.kt:681`）与恢复
 * 路径；文本变了版本没变，清单就声称"学生同意的是 v1 的那段话"，而模型实际读到的是另一段——
 * 授权、审计与旧包读回三处都会指向错误的提示词。`PaperEditTasksTest` 只钉了版本常量的字面值，
 * 不覆盖文本；这里把**两个版式分支的完整提示词字节**与同一份版本串绑在一起。
 *
 * 载体：`src/test/resources/model/paper-edit-prompt-golden*.txt`，首行是版本串，其余是完整提示词
 * （行尾统一按 LF 读入）。更新提示词必须同时：改 golden、把版本串 bump 到 v2；只改其一即红。
 */
class PaperEditPromptGoldenTest {

    @Test
    fun theNoteLayoutPromptMatchesItsVersionedGoldenByteForByte() {
        val (version, expectedPrompt) = golden("/model/paper-edit-prompt-golden.txt")

        assertEquals(ModelPromptPolicyVersions.PAPER_EDIT, version)
        assertEquals(
            "提示词文本变了就必须 bump 版本串（paper-edit-v2-…），反之亦然",
            expectedPrompt,
            PaperEditPrompt.build(noteLayoutInput()),
        )
    }

    @Test
    fun theNoNoteLayoutPromptMatchesItsVersionedGoldenByteForByte() {
        val (version, expectedPrompt) = golden("/model/paper-edit-prompt-golden-no-note.txt")

        assertEquals(ModelPromptPolicyVersions.PAPER_EDIT, version)
        assertEquals(
            "不含备注区的版式是另一个提示词分支，同样必须与版本串同步",
            expectedPrompt,
            PaperEditPrompt.build(noNoteLayoutInput()),
        )
    }

    @Test
    fun theGoldenVersionIsTheOnePersistedForPaperEdit() {
        val (version, _) = golden("/model/paper-edit-prompt-golden.txt")

        assertEquals(ModelPromptPolicyVersions.PAPER_EDIT, version)
        assertEquals(
            "出网清单/恢复路径按 currentFor(kind) 取版本，必须与 golden 同一值",
            version,
            ModelPromptPolicyVersions.currentFor(ModelTaskKind.PAPER_EDIT),
        )
    }

    private fun golden(path: String): Pair<String, String> {
        val text = requireNotNull(javaClass.getResourceAsStream(path)) {
            "缺少提示词 golden：$path"
        }.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }.replace("\r\n", "\n")
        val newline = text.indexOf('\n')
        require(newline > 0) { "golden 首行必须是版本串：$path" }
        return text.substring(0, newline) to text.substring(newline + 1)
    }

    private fun noteLayoutInput() = PaperEditInput(
        paperId = "paper-1",
        revision = 3,
        instruction = "把第 1 题标题改短",
        questions = listOf(
            PaperEditQuestionSnapshot(
                entryId = "entry-a",
                title = "二次函数综合题",
                blocks = listOf(
                    PaperEditBlockSnapshot(
                        blockId = "stem-entry-a",
                        kind = PaperEditBlockKind.PARAGRAPH,
                        text = "求函数的单调区间",
                    ),
                    PaperEditBlockSnapshot(
                        blockId = "choices-entry-a",
                        kind = PaperEditBlockKind.CHOICE_GROUP,
                        text = "下列哪个是二次函数",
                        choices = listOf(
                            PaperEditChoiceSnapshot("a", "y=x^2"),
                            PaperEditChoiceSnapshot("b", "y=x"),
                        ),
                    ),
                ),
                note = "这题再练一次",
            ),
        ),
        removedQuestions = listOf(
            PaperEditRemovedQuestion(entryId = "entry-b", title = "已删的题"),
        ),
        includeNote = true,
    )

    private fun noNoteLayoutInput() = PaperEditInput(
        paperId = "paper-1",
        revision = 3,
        instruction = "把第 1 题标题改短",
        questions = listOf(
            PaperEditQuestionSnapshot(
                entryId = "entry-a",
                title = "二次函数综合题",
                blocks = listOf(
                    PaperEditBlockSnapshot(
                        blockId = "stem-entry-a",
                        kind = PaperEditBlockKind.PARAGRAPH,
                        text = "求函数的单调区间",
                    ),
                ),
            ),
        ),
        includeNote = false,
    )
}
