package com.tingyun.smartmistakebook.core.data.study

import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudyDbValue

/**
 * 证据行 ↔ 题面快照的**审计读口**（AUD-11 ③，可观察性先行）。
 *
 * 消灭的失败：错键一旦落库（AUD-11 报告 §3），审计面无法定位"这笔证据读的是哪一道题、
 * 哪一版键"——证据行里没有题面 id，题面在 `assessment_item_snapshot`，两边此前没有任何
 * 可用的对上方式（报告 §3.3(c)："至少让审计面能把证据行与题面快照对起来"）。
 *
 * **不新增列、不动 schema（"最小改动面"）**：可对上的键本来就在证据行里，只是从来没有
 * 被解析过——三处身份格式（都是本模块/本通道的既有单源）拼起来即得题面行 id：
 *
 * | 证据行上的分量 | 形态 | 单源 |
 * |---|---|---|
 * | `evidence_id` | `knowledge-quiz:<requestId>:<节点>` | [knowledgeEvidenceId]（写入口） |
 * | `conversation_id` | `knowledge-quiz-review:<会话 id>` | feature 层会话写入标识（逆读见 [knowledgeQuizSessionIdOf]） |
 * | 题面行 id | `knowledge-quiz-item:<learner>:<会话 id>:<节点>` | [knowledgeQuizItemSnapshotId] |
 *
 * 会话分量是承重键：题面行身份 = `(learner, 会话, 知识点)`，而这三项在证据行上分别是
 * `learner_id`、`conversation_id` 的会话段、`knowledge_node_id`。requestId 只是旁证
 * （对上"模型任务/会话"那一侧），不参与题面行 id。
 *
 * 只读：不改行、不补写、对不上就是 null（**不猜**）。本口先立着——错键的纠正/撤销/重放
 * 语义按裁定留 5B（KD-43），在此之前至少要能看见"哪笔证据配哪道题"。
 */
internal class KnowledgeQuizEvidenceAudit(
    private val database: StudyDatabasePort,
    private val learnerId: String,
) {

    /**
     * 该 learner 的**知识点测验**证据行逐条对回题面快照；讲题通道（MODEL_CHAT）的行不进本口
     * ——它没有题面快照（题面在模型任务/会话那一侧），混进来只会给一堆永远为 null 的引用。
     * 拒绝观察行照样列出：审计要看的正是"被拒的这笔读的是哪道题"。
     */
    suspend fun scan(): List<KnowledgeQuizEvidenceItemLink> {
        val itemStore = KnowledgeQuizItemStore(database, learnerId)
        return database.readChatEvidenceByLearner(learnerId)
            .filter { it.source_kind == StudyDbValue.ChatEvidenceSourceKind.KNOWLEDGE_QUIZ }
            .map { row ->
                val sessionId = knowledgeQuizSessionIdOf(row.conversation_id)
                KnowledgeQuizEvidenceItemLink(
                    evidenceId = row.evidence_id,
                    requestId = knowledgeQuizEvidenceRequestIdOf(
                        evidenceId = row.evidence_id,
                        knowledgeNodeId = row.knowledge_node_id,
                    ),
                    knowledgeNodeId = row.knowledge_node_id,
                    sessionId = sessionId,
                    item = sessionId?.let { itemStore.readSnapshot(it, row.knowledge_node_id) },
                )
            }
    }
}

/**
 * 一条知识点测验证据行对上的题面引用。
 *
 * 每个"对不上"的出口都留了它自己的 null，而不是合成一条看着像对上的记录：
 * - [requestId]：`evidence_id` 不是本通道形状（如空命名空间的 `knowledge-quiz-<nanoTime>`
 *   回退形态）→ null；
 * - [sessionId]：`conversation_id` 不是本通道的会话写入标识 → null；
 * - [item]：会话对不上，或题面行不存在（隔日清理/坏行）→ null。
 */
internal data class KnowledgeQuizEvidenceItemLink(
    val evidenceId: String,
    val requestId: String?,
    val knowledgeNodeId: String,
    val sessionId: Long?,
    val item: KnowledgeQuizItemSnapshotView?,
)

/**
 * 会话写入标识（证据行 `conversation_id`）的**逆**：`knowledge-quiz-review:<会话 id>` → 会话 id。
 *
 * 正向的生成者是 feature 层 `KnowledgeReviewSessionViewModel.knowledgeQuizConversationId()`；
 * 本函数是读回来的那一半。壳内没有跨模块的共用地（core:data 不能被 feature 反向依赖），
 * 故这一对形状由**仪器化用例**钉住：证据经生产写路径（repository → 写入口）落库后，本口
 * 必须对得上题面（`KnowledgeQuizEvidenceAuditInstrumentedTest`）；形态改动会让那条用例当场红。
 *
 * 不是本通道形状 = null（不猜、不截串）。
 */
internal fun knowledgeQuizSessionIdOf(conversationId: String): Long? {
    val prefix = "knowledge-quiz-review:"
    if (!conversationId.startsWith(prefix)) return null
    return conversationId.removePrefix(prefix).toLongOrNull()
}

/**
 * 证据 id 里那一段提交身份（requestId）的逆：`knowledge-quiz:<requestId>:<节点>` → requestId。
 *
 * `requestId` 自身含冒号（feature 层形态 `knowledge-quiz:review:<会话>:<题面>:<选项>`），
 * 故只能按"去掉通道前缀与尾部节点段"取中间——节点段是证据行自己那一列，逐字可信。
 * 不是本通道形状（讲题 `chat-ev:`、空命名空间回退 `knowledge-quiz-<nanoTime>`）= null。
 */
internal fun knowledgeQuizEvidenceRequestIdOf(evidenceId: String, knowledgeNodeId: String): String? {
    val prefix = "${KnowledgeEvidenceChannel.KNOWLEDGE_QUIZ.idPrefix}:"
    val suffix = ":$knowledgeNodeId"
    if (!evidenceId.startsWith(prefix) || !evidenceId.endsWith(suffix)) return null
    val requestId = evidenceId.removePrefix(prefix).removeSuffix(suffix)
    return requestId.takeIf { it.isNotEmpty() }
}
