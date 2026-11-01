package com.tingyun.smartmistakebook.core.data.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * X-03 strictOffline 重布局判定门的 JVM 契约测试。
 *
 * 契约口径（visual-generation-contract.md :149）："strictOffline 禁止外部派发和获取新模型
 * 源码，但允许已持久程序在 API/SDK/运行库兼容且本地执行未关闭时重新布局、拖动和播放；
 * 静态冷恢复优先读快照。版本未知或执行熔断时读兼容快照/预览/文字。"
 *
 * 本测试钉住判定梯子的每一条与降级原因码；"重布局 = 0 远程派发"的协调器证据位由
 * `FigureGenerationCoordinatorTest` 的 JS 臂钉住（ledger.requests 为空、attempt 行
 * `reservation_id=null`、operation 终态 0/0），本文件不重复该断言。
 */
class StrictOfflineVisualRelayoutTest {

    @Test
    fun aCompatiblePersistedProgramMayBeRelaidOut() {
        val program = persistedProgram()
        val decision = StrictOfflineVisualRelayout.decide(
            program = program,
            localEngineAvailable = true,
            programFused = false,
        )
        assertEquals(
            StrictOfflineVisualDecision.RelayoutAllowed(
                programId = program.programId,
                sourceJs = program.sourceJs,
            ),
            decision,
        )
    }

    @Test
    fun aClosedLocalEngineFallsBackToSnapshot() {
        val decision = decide(programFused = false, localEngineAvailable = false)
        assertEquals(
            StrictOfflineVisualDecision.FallBackToSnapshot(
                StrictOfflineRelayoutFallbackReason.LOCAL_EXECUTION_CLOSED,
            ),
            decision,
        )
    }

    @Test
    fun anUnknownApiVersionFallsBackToSnapshot() {
        val decision = StrictOfflineVisualRelayout.decide(
            program = persistedProgram(apiVersion = 2),
            localEngineAvailable = true,
            programFused = false,
        )
        assertEquals(
            StrictOfflineVisualDecision.FallBackToSnapshot(
                StrictOfflineRelayoutFallbackReason.RUNTIME_VERSION_INCOMPATIBLE,
            ),
            decision,
        )
    }

    @Test
    fun anUnknownSdkVersionFallsBackToSnapshot() {
        val decision = StrictOfflineVisualRelayout.decide(
            program = persistedProgram(sdkVersion = "visual-sdk-v9"),
            localEngineAvailable = true,
            programFused = false,
        )
        assertEquals(
            StrictOfflineVisualDecision.FallBackToSnapshot(
                StrictOfflineRelayoutFallbackReason.RUNTIME_VERSION_INCOMPATIBLE,
            ),
            decision,
        )
    }

    @Test
    fun aFusedProgramFallsBackToSnapshot() {
        val decision = decide(programFused = true)
        assertEquals(
            StrictOfflineVisualDecision.FallBackToSnapshot(
                StrictOfflineRelayoutFallbackReason.EXECUTION_FUSED,
            ),
            decision,
        )
    }

    @Test
    fun aClosedEngineOutranksAnIncompatibleVersion() {
        // 判定序是契约的一部分：本地执行已关闭时，不泄露"版本也不兼容"的次要事实。
        val decision = StrictOfflineVisualRelayout.decide(
            program = persistedProgram(apiVersion = 99),
            localEngineAvailable = false,
            programFused = true,
        )
        assertEquals(
            StrictOfflineVisualDecision.FallBackToSnapshot(
                StrictOfflineRelayoutFallbackReason.LOCAL_EXECUTION_CLOSED,
            ),
            decision,
        )
    }

    @Test
    fun thePolicyIsInjectableForANewEngineGeneration() {
        val decision = StrictOfflineVisualRelayout.decide(
            program = persistedProgram(apiVersion = 2, sdkVersion = "visual-sdk-v2"),
            localEngineAvailable = true,
            programFused = false,
            policy = StrictOfflineRelayoutPolicy(
                supportedApiVersions = setOf(2),
                supportedSdkVersions = setOf("visual-sdk-v2"),
            ),
        )
        assertTrue("新引擎代次的宿主注入自己的兼容集后①档可达", decision is StrictOfflineVisualDecision.RelayoutAllowed)
    }

    @Test
    fun theDefaultPolicyPinsTheV1RuntimeVocabulary() {
        assertTrue(StrictOfflineRelayoutPolicy.CURRENT.supportedApiVersions.contains(1))
        assertTrue(StrictOfflineRelayoutPolicy.CURRENT.supportedSdkVersions.contains("visual-sdk-v1"))
    }

    @Test
    fun persistedProgramInvariants() {
        try {
            persistedProgram(programId = " ")
            fail("程序 id 不得为空白")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("id"))
        }
        try {
            persistedProgram(sourceJs = "")
            fail("持久程序必须携带源码")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("source"))
        }
        try {
            persistedProgram(apiVersion = 0)
            fail("api 版本从 1 起")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("api"))
        }
        try {
            persistedProgram(sdkVersion = " ")
            fail("sdk 版本不得为空白")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("sdk"))
        }
    }

    private fun persistedProgram(
        programId: String = "visual-program-1",
        sourceJs: String = "const visual = { draw: true };",
        apiVersion: Int = 1,
        sdkVersion: String = "visual-sdk-v1",
    ): PersistedVisualProgram = PersistedVisualProgram(
        programId = programId,
        sourceJs = sourceJs,
        apiVersion = apiVersion,
        sdkVersion = sdkVersion,
    )

    private fun decide(
        program: PersistedVisualProgram = persistedProgram(),
        programFused: Boolean,
        localEngineAvailable: Boolean = true,
    ): StrictOfflineVisualDecision = StrictOfflineVisualRelayout.decide(
        program = program,
        localEngineAvailable = localEngineAvailable,
        programFused = programFused,
    )
}
