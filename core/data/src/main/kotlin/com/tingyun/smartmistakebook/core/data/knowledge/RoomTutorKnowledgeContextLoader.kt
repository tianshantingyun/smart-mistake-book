package com.tingyun.smartmistakebook.core.data.knowledge

import com.tingyun.smartmistakebook.core.database.MAX_KNOWLEDGE_RECALL_CANDIDATES
import com.tingyun.smartmistakebook.core.database.KnowledgeSearchFeatureExtractor
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.TutorKnowledgeContextLoader
import com.tingyun.smartmistakebook.core.domain.TutorKnowledgeContextResult
import com.tingyun.smartmistakebook.core.domain.isReady
import com.tingyun.smartmistakebook.core.model.MAX_SESSION_KNOWLEDGE_CODES
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeCode
import com.tingyun.smartmistakebook.core.model.TutorKnowledgeCodeRole
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/**
 * [TutorKnowledgeContextLoader] 的 Room 实现：两段式检索与错题归类同源
 * （`readSubjectKnowledgeRecallCandidates` B 路 512 召回 → [KnowledgeContextRetriever.select]
 * A 路精排），再加一跳前置边把"讲这些节点需要先会什么"一并预披露（映射表角色：前置）。
 *
 * 消灭的失败（审计 §1.4 断链一）：拍照讲题是产品主入口，此前 `toTutorQuestionContext()`
 * 返回默认空集——Plan prompt 里没有材料、没有候选节点、没有代号，掌握度闭环在
 * 最赚钱的路径上进不了料。
 *
 * 降级口径（SavedMistakeTutorRoute 模板的修正版）：读取失败**不静默**——Log.w +
 * `loadFailed = true`，由 prompt 显式披露"教学材料未加载"；检索零命中是**合法空注入**
 * （`loadFailed = false`，维持现状语义）。
 *
 * 第三种情况（D-Q3）：知识内容还在后台就位（首装期）。改前它落进零命中那一档——学生拿到
 * 的是一句"没有相关材料"的假话。现在明确区分：`loadFailed = true`（材料确实没加载，
 * prompt 照旧不假设手里有资料）+ `knowledgeBasePreparing = true`（原因是"还没准备完"）。
 */
internal class RoomTutorKnowledgeContextLoader(
    private val database: StudyDatabasePort,
    private val knowledgeBaseAvailability: StateFlow<KnowledgeBaseAvailability>,
) : TutorKnowledgeContextLoader {

    override suspend fun knowledgePreDisclosure(
        subject: String,
        confirmedBindingNodeIds: List<String>,
        questionText: String?,
    ): TutorKnowledgeContextResult = try {
        val availability = knowledgeBaseAvailability.value
        if (!availability.isReady) {
            Log.i(
                "TutorKnowledgeContext",
                "knowledge base not ready ($availability): pre-disclosure withheld for subject=$subject",
            )
            return TutorKnowledgeContextResult(
                preDisclosures = emptyList(),
                candidateNodeIds = emptyList(),
                loadFailed = true,
                knowledgeBasePreparing = true,
            )
        }
        val (primaryIds, primaryRole) = if (confirmedBindingNodeIds.isNotEmpty()) {
            confirmedBindingNodeIds to TutorKnowledgeCodeRole.CONFIRMED_BINDING
        } else {
            val text = questionText.orEmpty()
            val features = KnowledgeSearchFeatureExtractor.fromQuestion(text)
            val candidates = if (features.isEmpty()) {
                // 没有可检索词（纯图消息/空题面）：不拿"并列前几名"凑候选——与归类路径
                // CatalogTutorRoundQuestionRetriever 同一条纪律，空就是空。
                emptyList()
            } else {
                val recall = database.readSubjectKnowledgeRecallCandidates(
                    subject = subject,
                    searchFeatures = features,
                    limit = MAX_KNOWLEDGE_RECALL_CANDIDATES,
                    // 拍照讲题是产品主入口：把原始题面交给稠密腿（可选，未装配/不可用即纯词面）。
                    queryText = text,
                )
                KnowledgeContextRetriever.select(
                    candidates = recall,
                    questionText = text,
                    limit = MAX_CANDIDATE_NODES,
                ).map { it.knowledgeNodeId }
            }
            candidates to TutorKnowledgeCodeRole.RETRIEVAL_CANDIDATE
        }
        val preDisclosure = buildPreDisclosure(subject, primaryIds, primaryRole)
        TutorKnowledgeContextResult(
            preDisclosures = preDisclosure,
            candidateNodeIds = primaryIds,
            loadFailed = false,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Log.w(
            "TutorKnowledgeContext",
            "pre-disclosure load failed for subject=$subject: $failure",
        )
        TutorKnowledgeContextResult(
            preDisclosures = emptyList(),
            candidateNodeIds = emptyList(),
            loadFailed = true,
        )
    }

    private suspend fun buildPreDisclosure(
        subject: String,
        primaryIds: List<String>,
        primaryRole: TutorKnowledgeCodeRole,
    ): List<TutorKnowledgeCode> {
        if (primaryIds.isEmpty()) return emptyList()
        // 前置一跳：primaryIds 作为 dependent 的关系边 → 前置节点（去重、去自身）。
        val prerequisiteIds = database.readKnowledgeNodeRelationsForDependents(
            subject = subject,
            dependentKnowledgeNodeIds = primaryIds.toHashSet(),
        )
            .mapTo(linkedSetOf()) { it.prerequisiteKnowledgeNodeId }
            .also { it.removeAll(primaryIds.toSet()) }
            .toList()
        val allIds = (primaryIds + prerequisiteIds).toSet()
        val names = database.readKnowledgeNodesByIds(allIds)
            .associate { node -> node.knowledgeNodeId to node.displayName }
        return buildList {
            primaryIds.distinct().forEach { id ->
                add(TutorKnowledgeCode(id, names[id] ?: id, primaryRole))
            }
            prerequisiteIds.distinct().forEach { id ->
                add(TutorKnowledgeCode(id, names[id] ?: id, TutorKnowledgeCodeRole.PREREQUISITE))
            }
        }.take(MAX_SESSION_KNOWLEDGE_CODES)
    }

    private companion object {
        /** 拍照候选上限：两段式精排 top-N，材料注入与代号预披露共用这份节点集。 */
        const val MAX_CANDIDATE_NODES = 8
    }
}

object TutorKnowledgeContextLoaderFactory {
    fun create(
        database: StudyDatabasePort,
        knowledgeBaseAvailability: StateFlow<KnowledgeBaseAvailability>,
    ): TutorKnowledgeContextLoader =
        RoomTutorKnowledgeContextLoader(database, knowledgeBaseAvailability)
}
