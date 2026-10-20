package com.tingyun.smartmistakebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationSnapshot
import com.tingyun.smartmistakebook.core.model.ModelProviderProtocol

internal enum class CapabilityOperation { IDLE, SAVING, TESTING, CLEARING }

/** 页面草稿及提交随 ViewModel 保留；密钥只驻留内存，不进入 Bundle 或磁盘。 */
internal class CapabilityFormViewModel : ViewModel() {
    var provider by mutableStateOf("")
    var baseUrl by mutableStateOf("")
    var modelId by mutableStateOf("")
    var protocol by mutableStateOf(ModelProviderProtocol.DEFAULT)
    var apiKey by mutableStateOf("")
    var operation by mutableStateOf(CapabilityOperation.IDLE)
    var operationMessage by mutableStateOf<String?>(null)
    private var loadedVersion: Pair<String?, Long>? = null

    val operationScope get() = viewModelScope

    fun load(configuration: ModelConfigurationSnapshot) {
        val version = configuration.configurationVersion to configuration.updatedAtEpochMillis
        if (version == loadedVersion) return
        provider = configuration.provider
        baseUrl = configuration.baseUrl
        modelId = configuration.modelId
        protocol = configuration.protocol
        loadedVersion = version
    }

    override fun onCleared() {
        apiKey = ""
    }
}
