package com.tingyun.smartmistakebook

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.tingyun.smartmistakebook.core.domain.ModelApiKey
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationMutationResult
import com.tingyun.smartmistakebook.core.domain.ModelConfigurationUpdate
import com.tingyun.smartmistakebook.core.domain.ModelCredentialReadResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 仅在无用户学习数据的专用设备运行；不保存凭证或调用远端模型。 */
class UserVisibleRecoveryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun unsavedModelConfigurationSurvivesActivityRecreation() {
        openProfile()
        compose.onNodeWithText("模型设置").performScrollTo().performClick()
        compose.onNodeWithTag("capability_provider").performScrollTo().performTextReplacement("未保存服务商")
        compose.onNodeWithTag("capability_model_id").performScrollTo().performTextReplacement("unsaved-model")
        compose.onNodeWithTag("capability_base_url").performScrollTo().performTextReplacement("https://example.invalid/v1")
        compose.onNodeWithTag("capability_api_key").performScrollTo().performTextReplacement("synthetic-rotation-key")

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("capability_provider").performScrollTo().assertTextContains("未保存服务商")
        compose.onNodeWithTag("capability_model_id").performScrollTo().assertTextContains("unsaved-model")
        compose.onNodeWithTag("capability_base_url").performScrollTo().assertTextContains("https://example.invalid/v1")
        compose.onNodeWithTag("capability_api_key").performScrollTo().assertTextContains("synthetic-rotation-key")
    }

    @Test fun masteryEmptyActionOpensTheMistakeLibrary() {
        openProfile()
        compose.onNodeWithText("查看全部知识点").performClick()
        compose.onNodeWithText("去错题本看看").performScrollTo().performClick()
        compose.onNodeWithTag("root_library").assertExists()
    }

    @Test fun editsToAnExistingConfigurationSurviveTheInitialLoadingFrameAfterRecreation() {
        val store = (compose.activity.application as SmartMistakeBookApplication).modelConfigurationStore!!
        // 只在空白专用设备建立合成配置，绝不覆盖开发者已有的真实凭证。
        val existingCredential = runBlocking { store.readCredential() }
        if (existingCredential is ModelCredentialReadResult.Available) existingCredential.apiKey.close()
        assumeTrue(existingCredential is ModelCredentialReadResult.Missing)
        assumeFalse(runBlocking { store.configuration.first().isConfigured })
        val chars = "synthetic-lifecycle-key".toCharArray()
        val key = ModelApiKey.from(chars)
        try {
            val saved = runBlocking {
                store.save(
                    ModelConfigurationUpdate("saved-provider", "https://example.invalid/v1", "saved-model"),
                    key,
                )
            }
            assertTrue(saved is ModelConfigurationMutationResult.Success)
            openProfile()
            compose.onNodeWithText("模型设置").performScrollTo().performClick()
            compose.onNodeWithTag("capability_provider").performScrollTo().performTextReplacement("edited-provider")
            compose.onNodeWithTag("capability_model_id").performScrollTo().performTextReplacement("edited-model")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("capability_provider").performScrollTo().assertTextContains("edited-provider")
            compose.onNodeWithTag("capability_model_id").performScrollTo().assertTextContains("edited-model")
        } finally {
            key.close()
            chars.fill('\u0000')
            runBlocking { store.clear() }
        }
    }

    private fun openProfile() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_profile").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_profile").performClick()
    }
}
