package com.tingyun.smartmistakebook.feature.profile

import com.tingyun.smartmistakebook.core.model.AppCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TutorCapabilitySubtitleTest {
    @Test
    fun strictOfflineWithoutAModelShowsTutorAsUnavailable() {
        val capabilities = AppCapabilitySnapshot(
            networkMode = NetworkMode.STRICT_OFFLINE,
            cameraCaptureAvailable = true,
            trustedOcrAvailable = false,
            tutorTeachingEnabled = true,
            remoteModelConfigured = false,
        )

        assertEquals(
            "讲题暂不可用 · 查看设置",
            tutorCapabilitySubtitle(capabilities),
        )
    }

    @Test
    fun savedButUntestedModelAsksForTheExplicitCapabilityTest() {
        val capabilities = localFirstCapabilities(remoteModelConfigured = true)

        assertEquals(
            "配置已保存 · 请测试模型",
            tutorCapabilitySubtitle(capabilities),
        )
    }

    @Test
    fun profileOnlyAdvertisesTheCapabilitiesThatPassed() {
        val capabilities = localFirstCapabilities(
            remoteModelConfigured = true,
            remoteModelStructuredOutputVerified = true,
            remoteModelImageInputVerified = false,
        )

        assertEquals(
            "文字讲题可用 · 图片识别暂不可用",
            tutorCapabilitySubtitle(capabilities),
        )
    }

    private fun localFirstCapabilities(
        remoteModelConfigured: Boolean,
        remoteModelStructuredOutputVerified: Boolean = false,
        remoteModelImageInputVerified: Boolean = false,
    ) = AppCapabilitySnapshot(
        networkMode = NetworkMode.LOCAL_FIRST,
        cameraCaptureAvailable = true,
        trustedOcrAvailable = false,
        tutorTeachingEnabled = true,
        remoteModelConfigured = remoteModelConfigured,
        remoteModelStructuredOutputVerified = remoteModelStructuredOutputVerified,
        remoteModelImageInputVerified = remoteModelImageInputVerified,
    )
}
