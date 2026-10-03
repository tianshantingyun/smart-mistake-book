package com.tingyun.smartmistakebook.core.data.model

import com.tingyun.smartmistakebook.core.domain.ModelConfigurationStore
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult

/**
 * Shared credential gate for the image-to-image / generation channels: returns
 * the available credential (with a verified image-capable model) or null when
 * networking is not allowed / no credential is configured / the capability test
 * did not confirm image input.
 *
 * A3：这是门的**唯一实现**。`ConfiguredCleanImageGenerator` 曾内联抄一份同样的判定
 * （本 KDoc 当时写的是"mirrors the gating in the clean-redraw generator"——方向已反过来），
 * 现在三条图片链都调用本函数：保存时重绘、`attachedImages` 兼容配图、4B A1 的
 * `GENERATE_FIGURE` 工具。
 */
internal suspend fun resolveImageCredential(
    configurationStore: ModelConfigurationStore,
    networkRequestsAllowed: Boolean,
): ModelCredentialReadResult.Available? {
    if (!networkRequestsAllowed) return null
    val read = configurationStore.readCredential()
    val available = read as? ModelCredentialReadResult.Available ?: return null
    val verification = available.configuration.capabilityVerification
    if (verification == null || !verification.supportsImageInput) return null
    return available
}
