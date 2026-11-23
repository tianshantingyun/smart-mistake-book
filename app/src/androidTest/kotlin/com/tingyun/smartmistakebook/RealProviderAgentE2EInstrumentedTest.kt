package com.tingyun.smartmistakebook

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.model.ConfiguredModelCapabilityTesterFactory
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelCapabilityTestResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.TUTOR_TOOL_DECLARATIONS
import com.tingyun.smartmistakebook.core.model.ContentBlock
import com.tingyun.smartmistakebook.core.model.ModelProviderProtocol
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.QuestionDocument
import com.tingyun.smartmistakebook.core.model.TutorPlanInput
import com.tingyun.smartmistakebook.core.model.TutorPlanOutput
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真实第三方 Provider 端到端（受环境门控）：只有设备上存在
 * `filesDir/real-provider.env`（由设备外脚本从 gitignored `local.properties` 注入）时才运行。
 *
 * 它做两件真实的事：跑一次官方能力探测（结构化输出），然后用生产 `modelTaskRepository`
 * 派发一次真实的 TutorPlan 工具环。密钥只存在于应用私有目录/Keystore，测试输出不打印它。
 */
@RunWith(AndroidJUnit4::class)
class RealProviderAgentE2EInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<SmartMistakeBookApplication>()

    @Test
    fun aRealProviderCompletesACapabilityProbeAndATutorPlanTurn() = runBlocking {
        val envFile = File(context.filesDir, "real-provider.env")
        assumeTrue("No real-provider env file on this device", envFile.isFile)
        val env = envFile.readLines()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()
        val baseUrl = env["base_url"].orEmpty()
        val modelId = env["model_id"].orEmpty()
        val apiKey = env["api_key"].orEmpty()
        assertTrue("env file is incomplete", baseUrl.isNotBlank() && modelId.isNotBlank() && apiKey.isNotBlank())

        val store = requireNotNull(context.modelConfigurationStore) { "The model store must be assembled" }
        val captured = mutableListOf<String>()
        com.tingyun.smartmistakebook.core.data.model.ModelCallFailureDiagnostics.sink = { record ->
            captured += "${record.failureCode}|${record.httpStatus ?: "-"}|${(record.rawResponse ?: "").take(600)}"
        }
        val key = ModelApiKey.from(apiKey.toCharArray())
        try {
            key.use {
                val saved = store.save(
                    ModelConfigurationUpdate(
                        provider = "deepseek",
                        baseUrl = baseUrl,
                        modelId = modelId,
                        protocol = ModelProviderProtocol.DEFAULT,
                    ),
                    it,
                )
                assertNotNull(saved)
            }

            val probe = ConfiguredModelCapabilityTesterFactory.create(store).testSavedConfiguration()
            val completed = probe as? ModelCapabilityTestResult.Completed
            assertTrue("capability probe failed: $probe", completed != null)
            assertTrue(
                "the provider did not verify structured output: ${completed!!.verification}",
                completed.verification.supportsStructuredOutput,
            )

            val request = ModelTaskRequest(
                requestId = "real-provider-plan-${System.nanoTime()}",
                input = TutorPlanInput(
                    sessionId = "real-provider-session",
                    draftRevisionNumber = 1,
                    subject = "MATH",
                    questionDocument = QuestionDocument(
                        id = "real-provider-question",
                        blocks = listOf(ContentBlock.Paragraph("stem", "求函数 f(x)=x^2-4x+3 在区间 [0,3] 上的最值。")),
                    ),
                    relevantLearningEvidence = emptyList(),
                    projectionIsCurrent = true,
                    toolDeclarations = TUTOR_TOOL_DECLARATIONS.toList(),
                ),
                occurredAtEpochMillis = System.currentTimeMillis(),
                agentConsentGranted = true,
            )

            val snapshots = context.modelTaskRepository.execute(request).toList()
            val terminal = snapshots.last()
            assertTrue(
                "real provider turn failed: ${terminal.status} / ${terminal.failure?.code} " +
                    "${terminal.failure?.message} :: ${captured.joinToString(" || ")}",
                terminal.status == ModelTaskStatus.SUCCEEDED,
            )
            val output = terminal.output as? TutorPlanOutput
            assertNotNull("a successful plan turn must carry TutorPlanOutput", output)
            assertTrue("model version must name the configured provider model",
                output!!.modelVersion.isNotBlank())
        } finally {
            com.tingyun.smartmistakebook.core.data.model.ModelCallFailureDiagnostics.sink = null
            runCatching { store.clear() }
            envFile.delete()
        }
    }
}
