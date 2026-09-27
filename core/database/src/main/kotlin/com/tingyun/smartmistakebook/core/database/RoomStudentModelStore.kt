package com.tingyun.smartmistakebook.core.database

import com.tingyun.smartmistakebook.core.database.entity.PredictionOutcomeEntity
import com.tingyun.smartmistakebook.core.database.entity.StudentModelPredictionEntity
import com.tingyun.smartmistakebook.core.database.port.ResolvedStudentModelPredictionRecord
import com.tingyun.smartmistakebook.core.database.port.StudentModelPredictionRecord

/** Prediction-audit trail (student-model domain). */
internal class RoomStudentModelStore(
    private val database: StudyDatabase,
) {
    suspend fun recordPredictions(predictions: List<StudentModelPredictionRecord>) {
        if (predictions.isEmpty()) return
        database.predictionAuditDao().insertPredictions(
            predictions.map { record ->
                StudentModelPredictionEntity(
                    predictionId = record.predictionId,
                    modelId = record.modelId,
                    modelVersion = record.modelVersion,
                    algorithmHash = record.algorithmHash,
                    practiceUnitId = record.practiceUnitId,
                    knowledgeNodeId = record.knowledgeNodeId,
                    featureFingerprint = record.featureFingerprint,
                    predictedScore = record.predictedScore,
                    conservativeScore = record.conservativeScore,
                    predictionWindowStartEpochMillis = record.predictionWindowStartEpochMillis,
                    predictionWindowEndEpochMillis = record.predictionWindowEndEpochMillis,
                    predictedAtEpochMillis = record.predictedAtEpochMillis,
                )
            },
        )
    }

    suspend fun resolvePredictions(
        practiceUnitId: String,
        wasIndependentCorrect: Boolean,
        observedAtEpochMillis: Long,
        responseLatencyMs: Long?,
        hintCount: Int,
    ): Int {
        val auditDao = database.predictionAuditDao()
        val pending = auditDao
            .findPendingForPracticeUnit(practiceUnitId, observedAtEpochMillis)
        pending.forEach { prediction ->
            auditDao.upsertOutcome(
                PredictionOutcomeEntity(
                    predictionId = prediction.predictionId,
                    observedAtEpochMillis = observedAtEpochMillis,
                    wasIndependentCorrect = wasIndependentCorrect,
                    responseLatencyMs = responseLatencyMs,
                    hintCount = hintCount,
                ),
            )
            auditDao.markResolved(prediction.predictionId)
        }
        return pending.size
    }

    suspend fun readResolvedPredictions(
        modelId: String,
        modelVersion: String,
    ): List<ResolvedStudentModelPredictionRecord> =
        database.predictionAuditDao()
            .findResolvedForModel(modelId, modelVersion)
            .map { row ->
                ResolvedStudentModelPredictionRecord(
                    predictionId = row.predictionId,
                    modelId = row.modelId,
                    modelVersion = row.modelVersion,
                    algorithmHash = row.algorithmHash,
                    predictedScore = row.predictedScore,
                    conservativeScore = row.conservativeScore,
                    wasIndependentCorrect = row.wasIndependentCorrect,
                    observedAtEpochMillis = row.observedAtEpochMillis,
                )
            }

    suspend fun findLastLatencyMs(practiceUnitId: String): Long? =
        database.predictionAuditDao().findLastLatencyMs(practiceUnitId)
}
