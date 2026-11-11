package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult

/**
 * Leases credentials for the shared image coordinator. Dedicated image settings use the
 * OpenAI-compatible image protocol. Legacy stores retain their image-input verification gate.
 */
internal suspend fun resolveImageCredential(
    configurationStore: ModelConfigurationStore,
    networkRequestsAllowed: Boolean,
): ModelCredentialReadResult.Available? {
    if (!networkRequestsAllowed) return null
    val read = configurationStore.readCredential()
    val available = read as? ModelCredentialReadResult.Available ?: return null
    val configuration = available.configuration
    val allowed = if (configurationStore.isImageGenerationStore) {
        configuration.isConfigured && configuration.modelId.isNotBlank() &&
            configuration.protocol == com.tingyun.smartmistakebook.core.model.ModelProviderProtocol.DEFAULT
    } else configuration.capabilityVerification?.supportsImageInput == true
    if (!allowed) {
        available.apiKey.close()
        return null
    }
    return available
}
