package com.tingyun.smartmistakebook.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingyun.smartmistakebook.core.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaperReviewDraft(
    val result: ReviewSelfReportKey? = null, val corrected: Boolean = false,
    val originalVisible: Boolean = false, val answeredAt: Long? = null, val note: String = "",
)
data class PaperReviewFeedback(val paperId: String?, val ordinal: Int?, val text: String, val error: Boolean)

class PaperReviewViewModel(
    private val repository: PaperReviewRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _feedback = MutableStateFlow<PaperReviewFeedback?>(null)
    val feedback = _feedback.asStateFlow()
    private val _draftVersion = MutableStateFlow(0)
    val draftVersion = _draftVersion.asStateFlow()
    private val savedBaselines = mutableMapOf<String, PaperReviewDraft>()
    private fun key(paperId: String, ordinal: Int) = "paper-draft:$paperId:$ordinal"
    private fun baseline(q: PaperReviewQuestion) = PaperReviewDraft(q.selectedResult, q.corrected, q.originalVisible, q.answeredAtEpochMillis, q.note)
    fun draft(paperId: String, q: PaperReviewQuestion): PaperReviewDraft {
        val raw = savedState.get<ArrayList<String>>(key(paperId, q.ordinal)) ?: return baseline(q)
        return PaperReviewDraft(raw[0].takeIf(String::isNotEmpty)?.let(ReviewSelfReportKey::valueOf),
            raw[1].toBoolean(), raw[2].toBoolean(), raw[3].toLongOrNull(), raw[4])
    }
    fun edit(paperId: String, q: PaperReviewQuestion, transform: (PaperReviewDraft) -> PaperReviewDraft) {
        if (_busy.value || q.applied) return
        val value = transform(draft(paperId, q))
        savedState[key(paperId, q.ordinal)] = arrayListOf(value.result?.name.orEmpty(), value.corrected.toString(),
            value.originalVisible.toString(), value.answeredAt?.toString().orEmpty(), value.note.take(4000))
        _draftVersion.value++
    }
    fun hasUnsaved(paperId: String, questions: List<PaperReviewQuestion>): Boolean = questions.any { q ->
        val k = key(paperId, q.ordinal)
        savedState.contains(k) && draft(paperId, q) != (savedBaselines[k] ?: baseline(q))
    }
    fun discard(paperId: String) {
        savedState.keys().filter { it.startsWith("paper-draft:$paperId:") }.forEach { savedState.remove<ArrayList<String>>(it) }
        _draftVersion.value++
    }
    fun setAnsweredAt(paper: PaperReview, question: PaperReviewQuestion, at: Long) {
        // The first confirmed paper time seeds unanswered questions; each can still be adjusted.
        paper.questions.filter { !it.applied && (it.ordinal == question.ordinal || draft(paper.paperId, it).answeredAt == null) }
            .forEach { q -> edit(paper.paperId, q) { it.copy(answeredAt = at) } }
    }
    fun clearMessage() { _feedback.value = null }
    fun reportFailure(message: String, paperId: String? = null, ordinal: Int? = null) {
        _feedback.value = PaperReviewFeedback(paperId, ordinal, message, true)
    }
    private fun run(paperId: String?, ordinal: Int? = null, action: suspend () -> String, onSaved: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        _feedback.value = null
        viewModelScope.launch {
            try {
                _feedback.value = PaperReviewFeedback(paperId, ordinal, action(), false)
                onSaved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { reportFailure(failure.message?.take(220) ?: "操作未完成，请重试。已保存的内容仍然保留。", paperId, ordinal) }
            finally { _busy.value = false }
        }
    }
    fun upload(paperId: String, uris: List<String>, corrected: Boolean, ordinal: Int?, onSaved: () -> Unit = {}) =
        run(paperId, ordinal, { repository.addPhotos(paperId, uris, corrected, ordinal); "作答照片已保存，请核对原始结果。" }, onSaved)
    fun confirm(paperId: String, q: PaperReviewQuestion, onSaved: () -> Unit = {}) {
        val value = draft(paperId, q)
        val at = value.answeredAt ?: return reportFailure("请选择实际作答时间", paperId, q.ordinal)
        run(paperId, q.ordinal, {
            repository.confirm(PaperReviewConfirmation(paperId, q.ordinal, value.result, value.corrected, value.originalVisible, at, value.note))
            // Retrying a confirmed result applies it idempotently after an interrupted apply.
            val message = repository.applyConfirmed(paperId)
            savedBaselines[key(paperId, q.ordinal)] = value
            if (value.result == null) "已保存为待确认，暂不计入本题复习进度。" else message
        }, onSaved)
    }
    fun apply(paperId: String) = run(paperId, action = { repository.applyConfirmed(paperId) })
    fun export(paperId: String) = run(paperId, action = { repository.export(paperId); "已开始生成原卷，完成后可打开或打印。" })
}
