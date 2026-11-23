package com.tingyun.smartmistakebook

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.data.capture.readVerifiedCanonicalAssetBytes
import com.tingyun.smartmistakebook.core.data.model.ModelTaskRepositoryFactory
import com.tingyun.smartmistakebook.core.data.model.TutorFigureGeneratorFactory
import com.tingyun.smartmistakebook.core.data.visual.FigureGenerationCoordinationFactory
import com.tingyun.smartmistakebook.core.domain.AppendTutorAssistantMessageCommand
import com.tingyun.smartmistakebook.core.domain.AppendTutorStudentMessageCommand
import com.tingyun.smartmistakebook.core.domain.CreateTutorConversationCommand
import com.tingyun.smartmistakebook.core.domain.KnowledgeBaseAvailability
import com.tingyun.smartmistakebook.core.domain.ModelGateway
import com.tingyun.smartmistakebook.core.domain.TutorConversationAnchorKind
import com.tingyun.smartmistakebook.core.domain.TutorMessageStatus
import com.tingyun.smartmistakebook.core.model.FigureGenerationMethod
import com.tingyun.smartmistakebook.core.model.ModelExecutionLocation
import com.tingyun.smartmistakebook.core.model.ModelGatewayEvent
import com.tingyun.smartmistakebook.core.model.ModelGatewayExecution
import com.tingyun.smartmistakebook.core.model.ModelTaskKind
import com.tingyun.smartmistakebook.core.model.ModelTaskRequest
import com.tingyun.smartmistakebook.core.model.ModelTaskStatus
import com.tingyun.smartmistakebook.core.model.ProviderCapabilitySnapshot
import com.tingyun.smartmistakebook.core.model.TutorFigureKind
import com.tingyun.smartmistakebook.core.model.TutorIntentDecision
import com.tingyun.smartmistakebook.core.model.TutorLobbyInput
import com.tingyun.smartmistakebook.core.model.TutorLobbyOutput
import com.tingyun.smartmistakebook.core.model.TutorToolCall
import com.tingyun.smartmistakebook.core.model.TutorToolName
import com.tingyun.smartmistakebook.core.model.TutorToolRequestsOutput
import com.tingyun.smartmistakebook.core.model.tutorTurnFigureAssetIds
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TutorLobbyInlineFigureInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun aMultilayerQuickJsFigureAppearsBetweenTheExplanationsAndSurvivesRecreation() {
        val app = compose.activity.application as SmartMistakeBookApplication
        val conversation = "audit-${UUID.randomUUID()}"
        val caption = "抛物线下方的积分区域"
        val title = "用图解释定积分"
        val question = "用图解释定积分表示哪个区域"
        val before = "先看曲线与横轴之间的阴影。"
        val after = "阴影从零延伸到二，因此用这两个端点确定积分上下限。"
        runBlocking {
            val db = app.studyDatabase
            val host = requireNotNull(app.visualJsFigureHost)
            assertTrue(host.prepare())
            val coordinator = FigureGenerationCoordinationFactory.create(db, null, false,
                host.engine, host, host.artifactAdapter(app, db))
            val generator = TutorFigureGeneratorFactory.create(app, db, requireNotNull(app.modelConfigurationStore), false, coordinator)
            val now = System.currentTimeMillis()
            val repository = app.tutorConversationRepository
            repository.createConversation(CreateTutorConversationCommand(
                conversationId = conversation, anchorKind = TutorConversationAnchorKind.TEXT_ONLY,
                anchorId = null, anchorRevisionId = null, title = title, createdAtEpochMillis = now,
            ))
            repository.appendStudentMessage(AppendTutorStudentMessageCommand(
                conversationId = conversation, messageId = "$conversation-s", bodyMarkdown = question,
                logicalOperationId = conversation, createdAtEpochMillis = now,
            ))
            val modelTasks = ModelTaskRepositoryFactory.create(
                database = db,
                gateway = DrawingGateway(before, after, caption),
                knowledgeBaseAvailability = MutableStateFlow(KnowledgeBaseAvailability.Ready),
                figureCapabilities = { coordinator.capabilities() },
                figureGenerator = generator,
                visualEngine = host.engine,
            )
            val result = modelTasks.execute(ModelTaskRequest(
                requestId = conversation,
                input = TutorLobbyInput(conversation, 1, question),
                occurredAtEpochMillis = now,
                agentConsentGranted = true,
            )).last()
            assertEquals(result.failure?.message, ModelTaskStatus.SUCCEEDED, result.status)
            val reply = result.output as TutorLobbyOutput
            val id = tutorTurnFigureAssetIds(reply.deliveryToolTraceJson).single()
            val asset = requireNotNull(db.readCanonicalSourceAsset(id))
            val pixels = requireNotNull(readVerifiedCanonicalAssetBytes(app, asset))
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(pixels, 0, pixels.size))
            try {
                val aboveCurve = bitmap.getPixel((bitmap.width * .47f).toInt(), (bitmap.height * .49f).toInt())
                val shaded = bitmap.getPixel((bitmap.width * .64f).toInt(), (bitmap.height * .57f).toInt())
                assertEquals(Color.parseColor("#fdfdfa"), aboveCurve)
                assertTrue(Color.green(shaded) > Color.red(shaded) + 15)
            } finally {
                bitmap.recycle()
            }
            repository.appendAssistantMessage(AppendTutorAssistantMessageCommand(
                conversationId = conversation, messageId = "$conversation-a", replyToMessageId = "$conversation-s",
                bodyMarkdown = reply.messageMarkdown,
                logicalOperationId = conversation,
                status = TutorMessageStatus.SUCCEEDED, createdAtEpochMillis = now, completedAtEpochMillis = now,
                // Exercise history from builds that persisted the trace but omitted the asset link.
                errorCode = null, toolTraceJson = reply.deliveryToolTraceJson,
            ))
        }
        compose.onNodeWithTag("nav_tutor").performClick()
        compose.onNodeWithTag("tutor_history_button").performClick()
        val row = "tutor_history_row_$conversation"
        compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag(row)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(row).performClick()
        fun awaitFigure() {
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasContentDescription(caption), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        }
        awaitFigure()
        compose.onNodeWithText("图 1 · $caption", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("AI 生成", useUnmergedTree = true).assertDoesNotExist()
        val figure = compose.onNodeWithContentDescription(caption, useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val introduction = compose.onNodeWithText(before, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val interpretation = compose.onNodeWithText(after, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(introduction.bottom <= figure.top && figure.bottom <= interpretation.top)
        compose.onNodeWithTag("tutor_reply_formula", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        app.cacheDir.resolve("tutor-inline-figure.png").outputStream().use {
            assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        compose.activityRule.scenario.recreate()
        awaitFigure()
        compose.onNodeWithContentDescription(caption, useUnmergedTree = true).assertIsDisplayed()
    }

    private class DrawingGateway(
        private val before: String,
        private val after: String,
        private val caption: String,
    ) : ModelGateway {
        private val provider = ProviderCapabilitySnapshot(
            providerId = "local-drawing-test", providerDisplayName = "Local drawing test", modelId = "test-model",
            supportedTasks = setOf(ModelTaskKind.TUTOR_LOBBY), supportsImageInput = false,
            supportsStructuredOutput = true, supportsStreaming = false,
            executionLocation = ModelExecutionLocation.LOCAL_NO_EGRESS,
        )

        override suspend fun capabilities() = provider

        override fun execute(execution: ModelGatewayExecution) = flow {
            val input = execution.request.input as TutorLobbyInput
            val intent = TutorIntentDecision.currentQuestionDefault()
            emit(ModelGatewayEvent.Started(provider))
            val output = if (input.toolRoundResults.isEmpty()) {
                TutorToolRequestsOutput(
                    intentDecision = intent,
                    calls = listOf(TutorToolCall(
                        tool = TutorToolName.GENERATE_FIGURE, rationale = "用曲线和阴影定位定积分",
                        figureKind = TutorFigureKind.GENERATE_PROCESS, figureDescription = caption,
                        figureMethod = FigureGenerationMethod.JS, figureProgram = PROGRAM,
                    )),
                    modelVersion = provider.modelId,
                )
            } else {
                val outcome = input.toolRoundResults.single().outcomes.single()
                assertTrue(outcome.summaryMarkdown, outcome.ok)
                val figure = requireNotNull(Regex("""\[\[figure:[A-Za-z0-9_.:-]+]]""").find(outcome.summaryMarkdown)).value
                TutorLobbyOutput(
                    conversationId = input.conversationId, messageOrdinal = input.messageOrdinal,
                    messageMarkdown = "$before\n\n$figure\n\n$after\n\n${'$'}${'$'}\nS = \\int_0^2 x^2 \\, dx\n${'$'}${'$'}",
                    intentDecision = intent, modelVersion = provider.modelId,
                )
            }
            emit(ModelGatewayEvent.Completed(output))
        }
    }

    companion object {
        private const val PROGRAM = """
            const visual = {
              prepare: function () {
                return [
                  {kind: 'text', payload: 'y = x²'},
                  {kind: 'text', payload: '0'},
                  {kind: 'text', payload: '2'},
                  {kind: 'text', payload: 'x'},
                  {kind: 'text', payload: 'y'},
                  {kind: 'text', payload: '积分区域'},
                  {kind: 'text', payload: '积分区间：0 到 2'}
                ];
              },
              render: function (i, m) {
                var w = i.viewport.widthDu, h = i.viewport.heightDu;
                var origin = w * 0.17, ground = h * 0.73, end = w * 0.71;
                var atX = function(t) { return origin + (end - origin) * t / 2; };
                var atY = function(t) { return ground - h * 0.12 * t * t; };
                var curve = [];
                for (var n = 0; n <= 40; n++) {
                  var t = n * 2.2 / 40;
                  curve.push([atX(t), atY(t)]);
                }
                var fillPoints = [];
                for (var k = 0; k <= 40; k++) fillPoints.push([atX(k / 20), atY(k / 20)]);
                var shade = visualSdk.polyline(fillPoints);
                shade.push(['L', end, ground], ['Z']);
                var paths = [
                  {id: 'shade', commands: shade},
                  {id: 'curve', commands: visualSdk.lineSegments(curve)},
                  {id: 'boundary', commands: visualSdk.polyline([[end, ground], [end, atY(2)]])},
                  {id: 'horizontal', commands: visualSdk.arrow(origin - 12, ground, w * 0.91, ground)},
                  {id: 'vertical', commands: visualSdk.arrow(origin, ground + 5, origin, h * 0.11)},
                  {id: 'endpoint', commands: visualSdk.circle(end, atY(2), 3)}
                ];
                var path = function(id, color, width) {
                  return width ? {type: 'path', pathId: id, fill: color, stroke: {width: width}} :
                    {type: 'path', pathId: id, fill: color};
                };
                var label = function(index, x, y, alignment, color) {
                  return {type: 'label', contentId: m[index].contentId, anchor: {x: x, y: y},
                    hAlign: alignment, vAlign: 'middle', color: color || '#202936'};
                };
                return {schemaVersion: 1, sceneId: 'integral-area', revision: i.revision, background: '#fdfdfa',
                  viewBox: {x: 0, y: 0, width: w, height: h}, paths: paths,
                  root: {type: 'group', children: [
                    path('shade', '#81cbbb88'), path('horizontal', '#202936', 1.4),
                    path('vertical', '#202936', 1.4), path('boundary', '#913c21', 1.4),
                    path('curve', '#136b60', 2.2), path('endpoint', '#913c21'),
                    label(0, w * 0.86, h * 0.15, 'center', '#136b60'),
                    label(1, origin, h * 0.80, 'center'), label(2, end, h * 0.80, 'center'),
                    label(3, w * 0.95, ground, 'center'), label(4, origin, h * 0.07, 'center'),
                    label(5, w * 0.55, h * 0.64, 'center', '#136b60'),
                    label(6, w * 0.50, h * 0.92, 'center')
                  ]}};
              }
            };
        """
    }
}
