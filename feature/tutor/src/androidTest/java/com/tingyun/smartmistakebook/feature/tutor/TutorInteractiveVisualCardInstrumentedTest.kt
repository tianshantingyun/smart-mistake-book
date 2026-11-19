package com.tingyun.smartmistakebook.feature.tutor

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualFrame
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualFrameResult
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualPort
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualReduceResult
import com.tingyun.smartmistakebook.core.domain.TutorInteractiveVisualScene
import com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme
import com.tingyun.smartmistakebook.feature.tutor.components.TutorInteractiveVisualCard
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 4E.2 交互卡的真实 Compose 交互：滑杆/开关/表单/草稿/提交。 */
@RunWith(AndroidJUnit4::class)
class TutorInteractiveVisualCardInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val sceneJson = """
        {"schemaVersion":1,"sceneId":"s","revision":1,
         "viewBox":{"x":0,"y":0,"width":320,"height":240},
         "controls":[
           {"id":"k","kind":"range","label":"斜率","value":1,"min":-5,"max":5,"step":0.5},
           {"id":"show","kind":"toggle","label":"显示辅助线","value":true}],
         "form":{"formId":"check-1","title":"检查一下","rationaleLabel":"理由",
           "items":[{"id":"q1","kind":"radioGroup","prompt":"k>0 时图像怎么变？",
             "options":[{"value":"up","label":"上移"},{"value":"down","label":"下移"}]}]}}
    """.trimIndent()

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        bitmap.recycle()
        return out.toByteArray()
    }

    private class FakePort(
        private val scene: TutorInteractiveVisualScene,
        private val png: ByteArray,
    ) : TutorInteractiveVisualPort {
        val reduces = mutableListOf<String>()
        val drafts = mutableListOf<String?>()

        override suspend fun read(
            messageId: String,
            ordinal: Int,
            programId: String?,
            snapshotId: String?,
        ): TutorInteractiveVisualScene = scene

        override suspend fun render(
            scene: TutorInteractiveVisualScene,
            stateJson: String,
        ): TutorInteractiveVisualFrameResult = TutorInteractiveVisualFrameResult.Rendered(
            TutorInteractiveVisualFrame(stateJson, scene.sceneJson, "{}", png),
        )

        override suspend fun reduce(
            scene: TutorInteractiveVisualScene,
            eventJson: String,
        ): TutorInteractiveVisualReduceResult {
            reduces += eventJson
            return TutorInteractiveVisualReduceResult.Updated(
                TutorInteractiveVisualFrame("""{"k":2}""", scene.sceneJson, "{}", png),
            )
        }

        override suspend fun saveDraft(
            messageId: String,
            ordinal: Int,
            expectedRevision: Int,
            uiStateJson: String?,
            formDigest: String?,
        ): Boolean {
            drafts += uiStateJson
            return true
        }
    }

    private fun scene() = TutorInteractiveVisualScene(
        messageId = "message-1",
        ordinal = 0,
        revision = 1,
        programId = "program-1",
        sourceJs = "const visual = {};",
        initialStateJson = """{"k":1}""",
        stateJson = """{"k":1}""",
        sceneJson = sceneJson,
        drawCommandsJson = "{}",
        viewportWidthDu = 320,
        viewportHeightDu = 240,
        fontScaleMilli = 1_000,
        theme = "light",
        timeMs = 0,
        seed = 7,
        sceneRevision = 1,
        uiStateJson = null,
        formDigest = null,
    )

    @Test
    fun rangeAndToggleDispatchRealEventsAndTheCardUpdates() {
        val fake = FakePort(scene(), png())
        compose.setContent {
            SmartMistakeBookTheme {
                TutorInteractiveVisualCard(
                    messageId = "message-1",
                    toolTraceJson = null,
                    port = fake,
                    onSubmitForm = {},
                )
            }
        }

        compose.onNodeWithTag("tutor_interactive_visual").assertIsDisplayed()
        compose.onNodeWithTag("tutor_interactive_visual_canvas").assertIsDisplayed()

        compose.onNodeWithTag("tutor_visual_control_k")
            .performSemanticsAction(SemanticsActions.SetProgress) { set -> set(4f) }
        compose.waitUntil(2_000) { fake.reduces.isNotEmpty() }
        assertTrue(
            "range event must carry the control id and the new value: ${fake.reduces}",
            fake.reduces.any { it.contains("\"controlId\":\"k\"") && it.contains("4.0") },
        )

        compose.onNodeWithTag("tutor_visual_control_show").performClick()
        compose.waitUntil(2_000) { fake.reduces.size >= 2 }
        assertTrue(
            "toggle event must carry the control id and the new boolean: ${fake.reduces}",
            fake.reduces.any { it.contains("\"controlId\":\"show\"") && it.contains("\"value\":false") },
        )
    }

    @Test
    fun formInputsAreDraftedThenSubmittedAsARealStudentTurn() {
        val fake = FakePort(scene(), png())
        val submitted = mutableListOf<String>()
        compose.setContent {
            SmartMistakeBookTheme {
                TutorInteractiveVisualCard(
                    messageId = "message-1",
                    toolTraceJson = null,
                    port = fake,
                    onSubmitForm = { submitted += it },
                )
            }
        }

        compose.onNodeWithTag("tutor_form_q1_up").performClick()
        compose.onNodeWithTag("tutor_form_rationale").performTextInput("因为开口向上")
        compose.waitUntil(2_000) {
            fake.drafts.any { draft -> draft?.contains("因为开口向上") == true }
        }

        compose.onNodeWithTag("tutor_form_submit").performClick()
        compose.waitUntil(2_000) { submitted.isNotEmpty() }
        assertTrue(
            "submitted text must carry the chosen option and the reason: ${submitted.single()}",
            submitted.single().contains("上移") && submitted.single().contains("因为开口向上"),
        )
        assertTrue(
            "submit must clear the draft",
            fake.drafts.any { it == null },
        )
    }
}
