package com.tingyun.smartmistakebook.core.data.study

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.tingyun.smartmistakebook.core.database.ErrorBookEntrySeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeBindingSeedRecord
import com.tingyun.smartmistakebook.core.database.KnowledgeNodeSeedRecord
import com.tingyun.smartmistakebook.core.database.PracticeUnitSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRelationSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemRevisionSeedRecord
import com.tingyun.smartmistakebook.core.database.ProblemSeedRecord
import com.tingyun.smartmistakebook.core.database.StudyDatabasePort
import com.tingyun.smartmistakebook.core.database.StudySeedBundle
import kotlinx.coroutines.runBlocking

/**
 * D-M M1 测试夹具（androidTest 源集，**不是**生产接缝）：`seedFixture` 端口随 fixture 系统
 * 退场后，跨模块（core:data / app）的仪器化测试没有可直写的 DAO——`ProblemDao` 的
 * 插入方法不跨模块可见。这里用**库文件直写**落同一批行：先让 Room 建好 schema（读一次
 * 版本号），再用 `android.database.sqlite.SQLiteDatabase` 以 INSERT OR IGNORE 写入。
 * WAL 下多连接安全；Room 的 invalidation 由库内触发器驱动，后续读路径能看到行。
 *
 * 刻意不做旧夹具的"同 id 不同载荷即抛"不可变回放校验：那条契约随 fixture 系统删除，
 * 相应用例已删除/改钉真实写入路径。缺失外键仍由 SQLite 外键约束拒绝。
 */
internal fun seedStudyFacts(
    context: Context,
    database: StudyDatabasePort,
    databaseName: String,
    bundle: StudySeedBundle,
) {
    // Room 惰性建库：先读一次版本号，确保 schema（表/索引/触发器）已经在文件里。
    runBlocking { database.readDatabaseVersion() }
    val sqlite = SQLiteDatabase.openDatabase(
        context.getDatabasePath(databaseName).absolutePath,
        null,
        SQLiteDatabase.OPEN_READWRITE,
    )
    try {
        sqlite.beginTransaction()
        try {
            bundle.problems.forEach { sqlite.insertIgnoring("problem", it.toValues()) }
            bundle.revisions.forEach { sqlite.insertIgnoring("problem_revision", it.toValues()) }
            bundle.practiceUnits.forEach { sqlite.insertIgnoring("practice_unit", it.toValues()) }
            bundle.errorBookEntries.forEach {
                sqlite.insertIgnoring("error_book_entry", it.toValues())
            }
            bundle.knowledgeNodes.forEach { sqlite.insertIgnoring("knowledge_node", it.toValues()) }
            bundle.knowledgeBindings.forEach {
                sqlite.insertIgnoring("practice_unit_knowledge_binding", it.toValues())
            }
            bundle.relations.forEach { sqlite.insertIgnoring("problem_relation", it.toValues()) }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }
    } finally {
        sqlite.close()
    }
}

private fun SQLiteDatabase.insertIgnoring(table: String, values: ContentValues) {
    insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE)
}

private fun ProblemSeedRecord.toValues() = ContentValues().apply {
    put("problem_id", problemId)
    put("canonical_fingerprint", canonicalFingerprint)
    put("subject", subject)
    put("created_at_epoch_millis", createdAtEpochMillis)
    putNull("archived_at_epoch_millis")
}

private fun ProblemRevisionSeedRecord.toValues() = ContentValues().apply {
    put("revision_id", revisionId)
    put("problem_id", problemId)
    put("revision_number", revisionNumber)
    put("title", title)
    put("problem_markdown", problemMarkdown)
    put("question_document_snapshot", questionDocumentSnapshot)
    put("answer_spec_id", answerSpecId)
    put("answer_spec_snapshot", answerSpecSnapshot)
    put("answer_verification_status", answerVerificationStatus)
    put("source_type", sourceType)
    put("source_reference", sourceReference)
    put("content_fingerprint", contentFingerprint)
    put("created_at_epoch_millis", createdAtEpochMillis)
}

private fun PracticeUnitSeedRecord.toValues() = ContentValues().apply {
    put("practice_unit_id", practiceUnitId)
    put("problem_id", problemId)
    put("problem_revision_id", problemRevisionId)
    put("unit_key", unitKey)
    put("unit_kind", unitKind)
    put("title", title)
    put("prompt_markdown", promptMarkdown)
    put("estimated_seconds", estimatedSeconds)
    put("created_at_epoch_millis", createdAtEpochMillis)
}

private fun ErrorBookEntrySeedRecord.toValues() = ContentValues().apply {
    put("entry_id", entryId)
    put("practice_unit_id", practiceUnitId)
    put("problem_id", problemId)
    put("current_revision_id", currentRevisionId)
    put("source_key", sourceKey)
    put("status", status)
    put("accepted_at_epoch_millis", acceptedAtEpochMillis)
    put("updated_at_epoch_millis", updatedAtEpochMillis)
    putNull("user_note")
}

private fun KnowledgeNodeSeedRecord.toValues() = ContentValues().apply {
    put("knowledge_node_id", knowledgeNodeId)
    put("stable_code", stableCode)
    put("subject", subject)
    put("display_name", displayName)
    put("canonical_name", canonicalName)
    put("node_kind", nodeKind)
    put("granularity", granularity)
    put("aliases_text", aliases.joinToString("\u001f"))
    put("boundary_markdown", boundaryMarkdown)
    put("verification_status", verificationStatus)
    put("parent_knowledge_node_id", parentKnowledgeNodeId)
    put("taxonomy_version", taxonomyVersion)
    put("created_at_epoch_millis", createdAtEpochMillis)
}

private fun KnowledgeBindingSeedRecord.toValues() = ContentValues().apply {
    put("binding_id", bindingId)
    put("practice_unit_id", practiceUnitId)
    put("knowledge_node_id", knowledgeNodeId)
    put("basis_revision_id", basisRevisionId)
    put("strength", strength)
    put("source_type", sourceType)
    put("taxonomy_version", taxonomyVersion)
    put("accepted_at_epoch_millis", acceptedAtEpochMillis)
}

private fun ProblemRelationSeedRecord.toValues() = ContentValues().apply {
    put("relation_id", relationId)
    put("source_problem_id", sourceProblemId)
    put("target_problem_id", targetProblemId)
    put("relation_type", relationType)
    put("status", status)
    put("source_basis_revision_id", sourceBasisRevisionId)
    put("target_basis_revision_id", targetBasisRevisionId)
    put("confidence", confidence)
    put("created_at_epoch_millis", createdAtEpochMillis)
    put("updated_at_epoch_millis", updatedAtEpochMillis)
}
