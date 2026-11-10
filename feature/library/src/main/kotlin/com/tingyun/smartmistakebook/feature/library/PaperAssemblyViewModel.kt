package com.tingyun.smartmistakebook.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.tingyun.smartmistakebook.core.domain.MistakeDetailRepository
import com.tingyun.smartmistakebook.core.domain.MistakeDetailState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal class PaperAssemblyViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    var selectedEntryIds by mutableStateOf(savedState.get<List<String>>("assembly_ids").orEmpty()
        .filter(String::isNotBlank).distinct().take(MAX_LIBRARY_BATCH_EXPORT_QUESTIONS))
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var expectedRevisions by mutableStateOf(savedState.get<HashMap<String, String>>("assembly_revisions").orEmpty())
        private set

    fun toggle(id: String, revisionId: String? = null) {
        if (checking || id.isBlank()) return
        if (id in selectedEntryIds) {
            save(selectedEntryIds - id)
            saveRevisions(expectedRevisions - id)
        } else if (selectedEntryIds.size < MAX_LIBRARY_BATCH_EXPORT_QUESTIONS) {
            save(selectedEntryIds + id)
            if (!revisionId.isNullOrBlank()) acceptCurrentVersion(id, revisionId)
        }
        else notice = "已选 100 题，最多可组 100 题，请先移除部分题目。"
    }

    fun acceptCurrentVersion(id: String, revisionId: String) {
        if (id !in selectedEntryIds || revisionId.isBlank()) return
        saveRevisions(expectedRevisions + (id to revisionId))
        notice = null
    }

    suspend fun validateForEditing(repository: MistakeDetailRepository): Boolean {
        if (checking || selectedEntryIds.isEmpty()) return false
        checking = true
        try {
            return withTimeoutOrNull(10_000) {
                for ((index, id) in selectedEntryIds.withIndex()) {
                    val state = repository.observe(id).first { it != MistakeDetailState.Loading }
                    val detail = when (state) {
                        is MistakeDetailState.Ready -> state.detail
                        is MistakeDetailState.Legacy -> state.detail
                        else -> null
                    }
                    if (detail == null || detail.archived) {
                        notice = "第 ${index + 1} 题暂不可用，请在已选区查看或移除。"
                        return@withTimeoutOrNull false
                    }
                    if (expectedRevisions[id]?.let { it != detail.identity.problemRevisionId } == true) {
                        notice = "第 ${index + 1} 题已更新，请先确认当前题面。"
                        return@withTimeoutOrNull false
                    }
                }
                true
            } ?: false.also { notice = "检查题目超时，请重试。" }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            notice = "暂时无法检查题目，请重试。"
            return false
        } finally {
            checking = false
        }
    }

    private fun saveRevisions(revisions: Map<String, String>) {
        expectedRevisions = revisions
        savedState["assembly_revisions"] = HashMap(revisions)
    }

    fun move(id: String, delta: Int) {
        if (checking) return
        val from = selectedEntryIds.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in selectedEntryIds.indices) return
        val ordered = selectedEntryIds.toMutableList()
        ordered.add(to, ordered.removeAt(from))
        save(ordered)
    }

    private fun save(ids: List<String>) {
        selectedEntryIds = ids
        savedState["assembly_ids"] = ArrayList(ids)
        notice = null
    }
}
