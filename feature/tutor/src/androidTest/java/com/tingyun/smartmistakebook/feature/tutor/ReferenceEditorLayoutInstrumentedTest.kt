package com.tingyun.smartmistakebook.feature.tutor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.ui.ReferenceEditorPage
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises wide and narrow editor layouts in a controlled test viewport. */
@RunWith(AndroidJUnit4::class)
class ReferenceEditorLayoutInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun movingBetweenWideAndNarrowKeepsTheSameEditorDraft() {
        val narrow = mutableStateOf(false)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(1000.dp, 600.dp))) {
                SmartMistakeBookTheme {
                    Box(if (narrow.value) Modifier.requiredWidth(320.dp) else Modifier.fillMaxSize()) {
                        ReferenceEditorPage(imageUris = listOf("file:///missing-reference.png")) {
                            var draft by remember { mutableStateOf("") }
                            OutlinedTextField(draft, { draft = it }, Modifier.testTag("reference_editor_draft"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("editor_source_reference").assertIsDisplayed()
        UiAuditEvidence.capture("wide-source-editor")
        compose.onNodeWithTag("reference_editor_draft").performTextInput("保留这份修改")
        compose.runOnIdle { narrow.value = true }
        compose.onNodeWithTag("editor_source_reference").assertDoesNotExist()
        compose.onNodeWithTag("reference_editor_draft").assertTextContains("保留这份修改")
        compose.runOnIdle { narrow.value = false }
        compose.onNodeWithTag("reference_editor_draft").assertTextContains("保留这份修改")
    }
}
