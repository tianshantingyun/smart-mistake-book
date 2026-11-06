package com.tingyun.smartmistakebook.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingyun.smartmistakebook.core.domain.KnowledgeCatalogRepository
import com.tingyun.smartmistakebook.core.domain.StaleCatalogCursorException
import com.tingyun.smartmistakebook.core.model.CatalogCursor
import com.tingyun.smartmistakebook.core.model.CatalogHit
import com.tingyun.smartmistakebook.core.model.CatalogQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

internal data class KnowledgeCatalogState(
    val query: String = "",
    val hits: List<CatalogHit> = emptyList(),
    val selected: List<CatalogHit> = emptyList(),
    val next: CatalogCursor? = null,
    val available: Boolean = true,
    val loading: Boolean = true,
    val loadingNext: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

/** Owns query identity and paging; a cancelled or late read cannot overwrite a newer query. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class KnowledgeCatalogViewModel(
    private val catalog: KnowledgeCatalogRepository,
    selected: List<CatalogHit> = emptyList(),
    available: Boolean = true,
    private val selectionLimit: Int = 32,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val workScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(KnowledgeCatalogState(selected = selected, available = available))
    val state: StateFlow<KnowledgeCatalogState> = mutableState.asStateFlow()
    private data class Request(val text: String, val available: Boolean, val epoch: Long)
    private val requests = MutableStateFlow(Request("", available, 0))

    init {
        workScope.launch {
            requests.debounce(250).distinctUntilChanged().flatMapLatest { request ->
                flow {
                    if (!request.available) return@flow
                    try {
                        val page = catalog.search(CatalogQuery(request.text))
                        if (requests.value == request) mutableState.update {
                            it.copy(hits = page.hits, next = page.next, loading = false, error = null)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (requests.value == request) mutableState.update {
                            it.copy(loading = false, error = "暂时无法读取知识目录，请重试")
                        }
                    }
                    emit(Unit)
                }
            }.collect()
        }
        workScope.launch { catalog.observeChanges().collect { restart("知识目录已更新") } }
    }

    fun setQuery(value: String) {
        val query = value.take(512)
        if (query == state.value.query) return
        mutableState.update { it.copy(query = query) }
        restart()
    }

    fun setAvailable(value: Boolean) {
        if (value == state.value.available) return
        mutableState.update { it.copy(available = value) }
        restart()
    }

    fun retry() = restart()

    private fun restart(notice: String? = null) {
        val current = state.value
        requests.value = Request(current.query, current.available, requests.value.epoch + 1)
        mutableState.update { it.copy(hits = emptyList(), next = null, loading = current.available, loadingNext = false, error = null, notice = notice) }
    }

    fun loadNext() {
        val current = state.value
        val cursor = current.next ?: return
        if (current.loading || current.loadingNext || !current.available) return
        val request = requests.value
        mutableState.update { it.copy(loadingNext = true, error = null) }
        workScope.launch {
            try {
                val page = catalog.search(CatalogQuery(request.text, cursor))
                if (requests.value != request) return@launch
                if (page.catalogRevision != cursor.catalogRevision) {
                    restart("知识目录已更新，请重新选择搜索结果")
                } else mutableState.update {
                    it.copy(hits = (it.hits + page.hits).distinctBy { hit -> hit.node.nodeId }, next = page.next, loadingNext = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: StaleCatalogCursorException) {
                if (requests.value == request) restart("知识目录已更新")
            } catch (_: Exception) {
                if (requests.value == request) mutableState.update { it.copy(loadingNext = false, error = "加载更多失败，请重试") }
            }
        }
    }

    fun toggle(hit: CatalogHit) {
        mutableState.update { current ->
            val existing = current.selected.any { it.node.nodeId == hit.node.nodeId }
            when {
                existing -> current.copy(selected = current.selected.filterNot { it.node.nodeId == hit.node.nodeId }, notice = null)
                current.selected.size >= selectionLimit -> current.copy(notice = "本次最多选择 $selectionLimit 个知识点")
                else -> current.copy(selected = current.selected + hit, notice = null)
            }
        }
    }
}
