package com.tingyun.smartmistakebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

/** 页面重建沿用同一操作；进程回收后只恢复中断提示，不恢复一个无人执行的忙碌状态。 */
internal class StorageOperationsViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private var busyState by mutableStateOf(false)
    private var messageState by mutableStateOf<String?>(
        if (savedState.get<Boolean>(RUNNING) == true) "上次存储操作已中断，请核对结果后重新操作。"
        else savedState[MESSAGE],
    )

    init {
        savedState[RUNNING] = false
        savedState[MESSAGE] = messageState
    }

    val operationScope get() = viewModelScope

    var busy: Boolean
        get() = busyState
        set(value) { busyState = value; savedState[RUNNING] = value }

    var message: String?
        get() = messageState
        set(value) { messageState = value; savedState[MESSAGE] = value }

    companion object {
        const val RUNNING = "storage_operation_running"
        private const val MESSAGE = "storage_operation_message"
    }
}
