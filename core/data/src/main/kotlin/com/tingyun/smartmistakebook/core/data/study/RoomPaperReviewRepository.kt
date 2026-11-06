package com.tingyun.smartmistakebook.core.data.study

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.tingyun.smartmistakebook.core.database.*
import com.tingyun.smartmistakebook.core.database.entity.*
import com.tingyun.smartmistakebook.core.data.capture.AndroidCanonicalAssetVault
import com.tingyun.smartmistakebook.core.domain.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Durable paper loop. Raw answer photographs are registered assets, never generated replacements. */
class RoomPaperReviewRepository(
    private val context: Context,
    private val database: StudyDatabasePort,
    private val study: StudyExperienceRepository,
    private val details: MistakeDetailRepository,
    private val exports: MistakeExportRepository,
    private val exportSnapshot: suspend (String) -> String,
) : PaperReviewRepository {
    private val store = database.paperReviews
    private val vault = AndroidCanonicalAssetVault(context)
    private val mutex = Mutex()
    private val changes = MutableStateFlow(0L)

    override fun observe(): Flow<List<PaperReview>> = combine(store.observePapers(), changes, exports.observeRecords()) { rows, _, records ->
        rows.map { row -> row.domain(store.questions(row.paperId), store.photos(row.paperId), records) }
    }.flowOn(Dispatchers.IO)

    override fun observe(paperId: String): Flow<PaperReview?> = combine(store.observePapers(), store.observeQuestions(paperId),
        store.observePhotos(paperId), exports.observeRecords()) { papers, questions, photos, records ->
        papers.firstOrNull { it.paperId == paperId }?.domain(questions, photos, records)
    }.flowOn(Dispatchers.IO)

    override suspend fun create(review: PaperReview): String = withContext(Dispatchers.IO) {
        require(review.questions.isNotEmpty()) { "今日没有可导出的错题" }
        require(review.questions.map { it.ordinal }.distinct().size == review.questions.size)
        require(review.fingerprint.matches(Regex("[a-f0-9]{64}")))
        val session = requireNotNull(store.sessionState(review.sessionId)) { "复习计划已不可用" }
        require(session.planId == review.planId) { "复习计划已变化，请重新打开" }
        store.create(PaperReviewEntity(review.paperId, review.sessionId, review.planId, review.fingerprint,
            review.draftSnapshot, review.createdAtEpochMillis, review.exportId), review.questions.map { q ->
            PaperReviewQuestionEntity(review.paperId, q.ordinal, q.entryId, q.practiceUnitId, q.problemId,
                q.problemRevisionId, q.title, q.markdown, null, false, false, null, "", false)
        })
        changes.value++
        review.paperId
    }

    override suspend fun export(paperId: String): String = mutex.withLock {
        val paper = requireNotNull(store.paper(paperId)) { "纸卷记录不存在" }
        paper.exportId?.let { id ->
            exports.observeRecords().first().firstOrNull { it.exportId == id }?.let { existing ->
                if (existing.status != MistakeExportStatus.FAILED) return@withLock id
            }
        }
        val id = exportSnapshot(paper.draftSnapshot)
        store.setExport(paperId, id)
        changes.value++
        id
    }

    override suspend fun draftSnapshot(paperId: String): String = requireNotNull(store.paper(paperId)).draftSnapshot

    override suspend fun addPhotos(paperId: String, localUris: List<String>, corrected: Boolean, questionOrdinal: Int?) = withContext(Dispatchers.IO) {
        require(localUris.isNotEmpty() && localUris.size <= 30) { "一次最多上传 30 张照片" }
        requireNotNull(store.paper(paperId)) { "纸卷记录不存在" }
        if (questionOrdinal != null) require(store.questions(paperId).any { it.ordinal == questionOrdinal })
        for (localUri in localUris) {
            val uri = Uri.parse(localUri)
            require(uri.scheme == "content") { "请选择相册或相机提供的图片" }
            val bytes = ByteArrayOutputStream().use { output ->
                requireNotNull(context.contentResolver.openInputStream(uri)) { "照片无法读取，请重新选择" }.use { input ->
                    val buffer = ByteArray(16_384)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 24 * 1024 * 1024) { "单张照片不能超过 24 MB" }
                        output.write(buffer, 0, count)
                    }
                }
                output.toByteArray()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outMimeType?.startsWith("image/") == true) { "这份文件不是可读取的图片" }
            val sha = sha(bytes)
            val mime = bounds.outMimeType
            val extension = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val root = File(context.filesDir, "source-assets").apply { check(exists() || mkdirs()) }.canonicalFile
            val target = File(root, "$sha.$extension")
            if (!target.exists()) {
                val temporary = File.createTempFile(".paper-", ".tmp", root)
                try { temporary.writeBytes(bytes); check(temporary.renameTo(target) || target.exists()) }
                finally { temporary.delete() }
            }
            val at = System.currentTimeMillis()
            val asset = CanonicalSourceAssetRecord("paper-photo:$sha", sha, "source-assets/${target.name}", mime,
                bytes.size.toLong(), bounds.outWidth, bounds.outHeight, "PHOTO_PICKER", at)
            database.registerCanonicalSourceAsset(asset)
            store.addPhoto(PaperReviewPhotoEntity("$paperId:$sha", paperId, asset.sourceAssetId, corrected, questionOrdinal, at))
        }
        changes.value++
        Unit
    }

    override suspend fun confirm(command: PaperReviewConfirmation) = mutex.withLock {
        val paper = requireNotNull(store.paper(command.paperId))
        val question = requireNotNull(store.questions(command.paperId).firstOrNull { it.ordinal == command.ordinal })
        require(!question.applied) { "这道题已经计入复习记录" }
        val photos = store.photos(command.paperId).filter { it.questionOrdinal == null || it.questionOrdinal == command.ordinal }
        require(photos.isNotEmpty()) { "请先上传对应作答照片" }
        require(validPaperAnsweredAt(paper.createdAt, command.answeredAtEpochMillis, System.currentTimeMillis())) { "作答时间应在导出之后、当前时间之前" }
        val corrected = command.corrected || photos.any { it.corrected }
        val key = paperReviewEvidenceKey(corrected, command.originalResult, command.originalVisible)
        require(command.originalResult == null || key != null) { "订正后的正确答案不能代替原始作答结果，请保留待确认或补充原始证据" }
        store.updateQuestion(question.copy(result = key?.name, corrected = corrected,
            originalVisible = command.originalVisible, answeredAt = command.answeredAtEpochMillis, note = command.note.take(4000)))
        changes.value++
        Unit
    }

    override suspend fun applyConfirmed(paperId: String): String = mutex.withLock {
        val paper = requireNotNull(store.paper(paperId))
        val questions = store.questions(paperId)
        var applied = 0
        for (question in questions) {
            val session = requireNotNull(store.sessionState(paper.sessionId)) { "复习会话已不存在，作答照片和确认结果仍已保存" }
            if (question.applied) {
                require(question.ordinal < session.ordinal) { "这道题的复习已撤销，请回到手机复习继续；原卷照片仍已保留" }
                continue
            }
            if (question.ordinal < session.ordinal) {
                store.updateQuestion(question.copy(applied = true))
                continue
            }
            if (question.ordinal != session.ordinal || question.result == null) break
            val current = details.readExact(MistakeRevisionKey(question.entryId, question.problemId, question.revisionId))
            val currentHead = details.observe(question.entryId).first { it != MistakeDetailState.Loading }
            val head = when (currentHead) { is MistakeDetailState.Ready -> currentHead.detail; is MistakeDetailState.Legacy -> currentHead.detail; else -> null }
            require(current is MistakeDetailState.Ready || current is MistakeDetailState.Legacy) { "原始题目版本无法读取，暂未计入" }
            require(head != null && head.identity.problemRevisionId == question.revisionId && !head.archived) { "题目已修改或移出，请先核对原卷版本；照片和结果已保留" }
            val result = study.submitReviewSelfReport(StudySelfReportedReviewSubmission(
                requestId = "paper-answer:${paper.paperId}:${question.ordinal}", sessionId = paper.sessionId,
                expectedStateVersion = session.version,
                presentationId = "presentation:review:${paper.sessionId}:${question.practiceUnitId}",
                practiceUnitId = question.practiceUnitId, key = ReviewSelfReportKey.valueOf(requireNotNull(question.result)),
                responseOrdinal = 1, durationSeconds = 0, occurredAtEpochMillis = requireNotNull(question.answeredAt),
                hintCount = if (question.corrected) 1 else 0,
            ))
            check(result.progress.currentOrdinal > question.ordinal)
            store.updateQuestion(question.copy(applied = true)); applied++
        }
        changes.value++
        val pending = store.questions(paperId).count { !it.applied }
        if (pending == 0) "本卷作答已计入复习记录" else "已计入 $applied 道，剩余 $pending 道待确认，可分批继续"
    }

    private suspend fun PaperReviewEntity.domain(questions: List<PaperReviewQuestionEntity>, photos: List<PaperReviewPhotoEntity>, records: List<MistakeExportRecord>): PaperReview {
        val export = records.firstOrNull { it.exportId == exportId }
        return PaperReview(paperId, sessionId, planId, fingerprint, draftSnapshot, createdAt, exportId,
            questions.map { q -> PaperReviewQuestion(q.ordinal, q.entryId, q.practiceUnitId, q.problemId, q.revisionId, q.title, q.markdown,
                q.result?.let(ReviewSelfReportKey::valueOf), q.corrected, q.originalVisible, q.answeredAt, q.note, q.applied) },
            photos.map { photo ->
                val asset = database.readCanonicalSourceAsset(photo.sourceAssetId)
                val uri = asset?.let { runCatching { vault.resolve(it).toURI().toASCIIString() }.getOrNull() }.orEmpty()
                PaperReviewPhoto(photo.photoId, uri, photo.corrected, photo.questionOrdinal, photo.sourceAssetId)
            }, export?.status, export?.failureMessage)
    }
    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
