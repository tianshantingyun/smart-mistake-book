package com.tingyun.smartmistakebook.visualjs

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.SmartMistakeBookApplication
import com.tingyun.smartmistakebook.core.data.visual.FigureGenerationCoordinationFactory
import com.tingyun.smartmistakebook.core.database.StudyDatabaseFactory
import com.tingyun.smartmistakebook.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@RunWith(AndroidJUnit4::class)
class VisualJsFigureHostInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aLabeledJsDrawingCanBeSavedAndReadWithoutAnImageModel(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<SmartMistakeBookApplication>()
        val host = withTimeout(20_000) {
            while (app.visualJsFigureHost == null) delay(50)
            requireNotNull(app.visualJsFigureHost)
        }
        assertTrue("The actual isolated engine and native measurement must be ready", host.prepare())
        val name = "js-figure-host-${System.nanoTime()}.db"
        val database = StudyDatabaseFactory.open(app, name)
        try {
            val coordinator = FigureGenerationCoordinationFactory.create(database, null, false,
                host.engine, host, host.artifactAdapter(app, database))
            val capabilities = coordinator.capabilities()
            assertEquals(setOf(FigureGenerationMethod.JS), capabilities.availableMethods)
            val request = FigureGenerationRequest("host-test-${System.nanoTime()}", FigureGenerationPurpose.EXPLANATION,
                FigureGenerationTarget(FigureFidelity.ILLUSTRATIVE), capabilities, FigureGenerationBudget(),
                FigureDispatchParent.ModelTask("host-local-test", reserveChatFinalAnswer = false))
            val proposal = FigureGenerationProposal(decision = FigureGenerationDecision.GENERATE,
                method = FigureGenerationMethod.JS, purpose = request.purpose,
                reason = "用数轴和标注说明方向", fidelity = FigureFidelity.ILLUSTRATIVE, program = PROGRAM)
            val outcome = coordinator.generate(request, proposal)
            assertEquals(outcome.diagnostics, FigureGenerationStatus.GENERATED, outcome.status)
            val artifact = outcome.artifact as FigureArtifact.Program
            val snapshot = requireNotNull(database.readTutorVisualSnapshot(requireNotNull(artifact.snapshotId)))
            assertEquals("LIVE", snapshot.status)
            assertEquals(artifact.previewAssetId, snapshot.previewAssetId)
            val preview = requireNotNull(database.readCanonicalSourceAsset(requireNotNull(artifact.previewAssetId)))
            assertTrue(preview.width >= host.forGeneration(request, proposal)!!.viewport.widthDu)
            assertNotNull(com.tingyun.smartmistakebook.core.data.capture.readVerifiedCanonicalAssetBytes(app, preview))
            assertEquals(snapshot, database.readTutorVisualSnapshot(snapshot.snapshotId))
            val generator = com.tingyun.smartmistakebook.core.data.model.TutorFigureGeneratorFactory.create(
                app, database, requireNotNull(app.modelConfigurationStore), false, coordinator)
            val tutorResult = generator.generate(com.tingyun.smartmistakebook.core.data.model.TutorFigureRequest(
                kind = TutorFigureKind.GENERATE_PROCESS, description = "数轴标出正方向",
                method = FigureGenerationMethod.JS, program = PROGRAM, parentOperationId = "host-tool-test"))
            assertEquals(tutorResult.diagnostics, com.tingyun.smartmistakebook.core.data.model.TutorFigureStatus.GENERATED, tutorResult.status)
            assertFalse(tutorResult.generatedNow)
            assertNull(tutorResult.model)
            assertNotNull(database.readCanonicalSourceAsset(requireNotNull(tutorResult.figureId)))
            database.createTutorConversation(com.tingyun.smartmistakebook.core.database.CreateTutorConversationDatabaseCommand(
                conversationId = "host-conversation", anchorKind = "TEXT_ONLY", anchorId = null,
                anchorRevisionId = null, title = null, createdAtEpochMillis = 1_000))
            database.appendTutorStudentMessage(com.tingyun.smartmistakebook.core.database.AppendTutorStudentMessageDatabaseCommand(
                conversationId = "host-conversation", messageId = "host-student", bodyMarkdown = "说明数轴方向",
                logicalOperationId = "host-tool-test", createdAtEpochMillis = 1_000))
            val trace = encodeTutorTurnToolTrace(TutorTurnToolTrace(listOf(TutorToolTraceEntry(
                tool = TutorToolName.GENERATE_FIGURE, ok = true, figure = TutorToolFigureTrace(
                    kind = TutorFigureKind.GENERATE_PROCESS, figureId = tutorResult.figureId,
                    method = FigureGenerationMethod.JS, purpose = FigureGenerationPurpose.EXPLANATION,
                    operationId = tutorResult.operationId, caption = "数轴标出正方向")))))
            database.appendTutorAssistantMessage(com.tingyun.smartmistakebook.core.database.AppendTutorAssistantMessageDatabaseCommand(
                conversationId = "host-conversation", messageId = "host-assistant", replyToMessageId = "host-student",
                bodyMarkdown = "沿箭头方向为正。\n\n[[figure:${tutorResult.figureId}]]", toolTraceJson = trace,
                sourceImageAssetIds = listOf(requireNotNull(tutorResult.figureId)), logicalOperationId = "host-tool-test",
                status = "SUCCEEDED", createdAtEpochMillis = 2_000, completedAtEpochMillis = 2_000, errorCode = null))
            val stored = database.readTutorMessageSourceAssets(listOf("host-assistant")).single()
            assertEquals("TUTOR_EXPLANATION", stored.role)
            val reopened = StudyDatabaseFactory.open(app, name)
            try {
                assertEquals(stored, reopened.readTutorMessageSourceAssets(listOf("host-assistant")).single())
            } finally {
                reopened.close()
            }
            val figureId = requireNotNull(tutorResult.figureId)
            val teachingAsset = requireNotNull(database.readCanonicalSourceAsset(figureId))
            assertNotNull(com.tingyun.smartmistakebook.core.data.capture.readVerifiedCanonicalAssetBytes(app, teachingAsset))
            val localUri = java.io.File(app.filesDir, teachingAsset.relativePath).toURI().toString()
            compose.setContent {
                com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme {
                    androidx.compose.material3.Surface {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                            com.tingyun.smartmistakebook.core.ui.TutorMessageDocument(
                                markdown = "## 先确定正方向\n\n**沿箭头方向为正。** 读数前先统一方向约定。\n\n[[figure:$figureId]]\n\n### 再读坐标\n\n1. 找到起点。\n2. 沿正方向判断位移。\n\n[[figure:$figureId]]",
                                figureAssetIds = listOf(figureId), resolveFigure = { localUri },
                                figureCaptions = tutorFigureCaptions(trace))
                        }
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("数轴标出正方向").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("先确定正方向").assertExists()
            compose.onNodeWithContentDescription("数轴标出正方向").performScrollTo().assertIsDisplayed()
            java.io.File(app.cacheDir, "visual-host-teaching.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithContentDescription("数轴标出正方向").performClick()
            compose.onNodeWithContentDescription("关闭图片查看").assertIsDisplayed().performClick()
            compose.onNodeWithText("查看图 1").performScrollTo().performClick()
            compose.onNodeWithContentDescription("数轴标出正方向").assertIsDisplayed()
        } finally {
            database.close()
            app.deleteDatabase(name)
        }
    }

    @Test fun functionAreaHasARealCurveIntervalAndNativeLabels(): Unit = runBlocking {
        verifyTeachingFigure(PROGRAM_FUNCTION, "曲线下的积分面积", setOf("y = x²", "(2,4)", "面积 = 8/3"), "function",
            "## 先确定积分区间\n\n阴影对应从 0 到 2 的区域，曲线上的高度由 **y = x²** 决定。")
    }

    @Test fun corkForcesKeepEveryResistingForceDownward(): Unit = runBlocking {
        verifyTeachingFigure(PROGRAM_FORCE, "木塞向上运动时的受力", setOf("F", "mg", "f", "ΔpS", "木塞"), "forces",
            "## 先看力的方向\n\n拉力向上，重力、摩擦力和气压差的合力向下。**箭头方向与实际受力一致。**")
    }

    @Test fun phenotypeRatiosCompareTwoGroupsWithoutRelyingOnlyOnColor(): Unit = runBlocking {
        verifyTeachingFigure(PROGRAM_RATIO, "雌雄后代的性状比例对比", setOf("F₁ 雌株", "F₁ 雄株", "3 : 1", "1 : 1"), "ratios",
            "## 分开比较雌雄后代\n\n雌株绿色与花斑为 **3∶1**，雄株为 **1∶1**。相同的总长度便于直接比较。")
    }

    @Test fun referenceReadingKeepsFormulasCenteredAndLongEquationsScrollable() {
        val app = ApplicationProvider.getApplicationContext<SmartMistakeBookApplication>()
        compose.setContent {
            com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme {
                androidx.compose.material3.Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        com.tingyun.smartmistakebook.core.ui.TutorMessageDocument(
                            "## 第一步：明确研究对象\n\n先区分常染色体与性染色体上的基因，**上标表示等位基因**。\n\n" +
                                "\$\$AaX^{B}Y\$\$\n\n### 第二步：列出关系\n\n从静止开始匀加速运动时，位移与时间的关系为\n\n" +
                                "\$\$x=\\frac{1}{2}at^2\$\$\n\n### 第三步：整理长公式\n\n长式子可以左右滑动阅读。\n\n" +
                                "\$\$W-mgh-\\Delta pSh-\\frac{1}{2}f_0h=\\frac{1}{2}mv^2=mah\$\$",
                        )
                    }
                }
            }
        }
        val equations = compose.onAllNodesWithTag("tutor_reply_formula")
        equations.assertCountEquals(3)
        equations[2].performScrollTo()
        compose.onNodeWithContentDescription(ReadableMathText.formula(
            "W-mgh-\\Delta pSh-\\frac{1}{2}f_0h=\\frac{1}{2}mv^2=mah"))
            .performTouchInput { swipeLeft() }
        equations[0].performScrollTo()
        java.io.File(app.cacheDir, "reference-reading-formulas.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private suspend fun verifyTeachingFigure(
        program: String, caption: String, expectedLabels: Set<String>, name: String, markdown: String,
    ) {
        val app = ApplicationProvider.getApplicationContext<SmartMistakeBookApplication>()
        val host = withTimeout(20_000) {
            while (app.visualJsFigureHost == null) delay(50)
            requireNotNull(app.visualJsFigureHost)
        }
        assertTrue(host.prepare())
        val databaseName = "teaching-$name-${System.nanoTime()}.db"
        val database = StudyDatabaseFactory.open(app, databaseName)
        try {
            val coordinator = FigureGenerationCoordinationFactory.create(database, null, false,
                host.engine, host, host.artifactAdapter(app, database))
            val request = FigureGenerationRequest("teaching-$name", FigureGenerationPurpose.EXPLANATION,
                FigureGenerationTarget(FigureFidelity.ILLUSTRATIVE), coordinator.capabilities(), FigureGenerationBudget(),
                FigureDispatchParent.ModelTask("teaching-$name", reserveChatFinalAnswer = false))
            val proposal = FigureGenerationProposal(decision = FigureGenerationDecision.GENERATE,
                method = FigureGenerationMethod.JS, purpose = request.purpose, reason = caption,
                fidelity = FigureFidelity.ILLUSTRATIVE, program = program)
            val outcome = coordinator.generate(request, proposal)
            assertEquals(outcome.diagnostics, FigureGenerationStatus.GENERATED, outcome.status)
            val artifact = outcome.artifact as FigureArtifact.Program
            val snapshot = requireNotNull(database.readTutorVisualSnapshot(requireNotNull(artifact.snapshotId)))
            val frame = requireNotNull(host.forGeneration(request, proposal))
            val replay = host.engine.execute(com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest(
                artifact.programId, program, sceneRevision = 1, stateJson = "{}", viewport = frame.viewport,
                timeMs = frame.timeMs, seed = frame.seed, theme = frame.theme))
            assertTrue(replay is com.tingyun.smartmistakebook.core.domain.VisualExecutionResult.Rendered)
            replay as com.tingyun.smartmistakebook.core.domain.VisualExecutionResult.Rendered
            assertEquals(snapshot.drawCommandsJson, replay.drawCommandsJson)
            assertTrue(replay.contents.map { it.declaration.payload }.containsAll(expectedLabels))
            val asset = requireNotNull(database.readCanonicalSourceAsset(requireNotNull(artifact.previewAssetId)))
            assertNotNull(com.tingyun.smartmistakebook.core.data.capture.readVerifiedCanonicalAssetBytes(app, asset))
            val uri = java.io.File(app.filesDir, asset.relativePath).toURI().toString()
            if (name == "function") verifyIllustratedAnswerExports(app, asset, uri, caption)
            compose.setContent {
                com.tingyun.smartmistakebook.core.ui.SmartMistakeBookTheme {
                    androidx.compose.material3.Surface {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                            com.tingyun.smartmistakebook.core.ui.TutorMessageDocument(
                                markdown = "$markdown\n\n[[figure:${asset.sourceAssetId}]]\n\n### 读图要点\n\n$caption。结合上面的条件判断，计算过程保留在正文中。",
                                figureAssetIds = listOf(asset.sourceAssetId), resolveFigure = { uri },
                                figureCaptions = mapOf(asset.sourceAssetId to caption))
                        }
                    }
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(caption).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(caption).performScrollTo().assertIsDisplayed()
            java.io.File(app.cacheDir, "visual-teaching-$name.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            database.close()
            app.deleteDatabase(databaseName)
        }
    }

    private fun verifyIllustratedAnswerExports(
        app: SmartMistakeBookApplication,
        asset: com.tingyun.smartmistakebook.core.database.CanonicalSourceAssetRecord,
        uri: String,
        caption: String,
    ) {
        val question = QuestionDocument("integral-question", blocks = listOf(ContentBlock.Paragraph("stem", "求曲线 y=x² 在区间 [0,2] 下的面积。")))
        val basis = SolutionBasis("integral-revision", solutionQuestionSha256(question, emptyList()))
        val manifest = FrozenAnswerManifest("native-paper", "a".repeat(64), MistakePdfLayout.DEFAULT,
            listOf(FrozenExportQuestion(1, "native-entry", basis, question)), "native-paper-snapshot")
        val figure = StoredFigureReference("figure", asset.sourceAssetId, asset.contentSha256, VisualRole.TUTOR_EXPLANATION)
        val answer = DetailedSolutionDocument(basis, "面积为 8/3。",
            QuestionDocument("steps", blocks = listOf(
                ContentBlock.Paragraph("step-1", "第一步，确定积分区间为 [0,2]，函数非负。"),
                ContentBlock.Paragraph("figure", caption),
                ContentBlock.Formula("step-2", "\\int_0^2 x^2\\,dx=\\left[\\frac{x^3}{3}\\right]_0^2=\\frac{8}{3}", "积分结果为三分之八", true))),
            origin = SolutionOrigin.MODEL, verification = SolutionVerification.UNVERIFIED, requiredFigureBlockIds = setOf("figure"), figures = listOf(figure))
        fun input(format: MistakeExportFormat) = com.tingyun.smartmistakebook.core.export.DetailedAnswerExportInput(
            manifest, listOf(answer), format, figureResolver = {
                com.tingyun.smartmistakebook.core.export.MistakePdfExportImage(
                    VisualRole.TUTOR_EXPLANATION.name, uri, asset.contentSha256, asset.byteSize)
            })
        val word = com.tingyun.smartmistakebook.core.export.MistakeDocxExporter(app).prepareDetailedAnswers(input(MistakeExportFormat.DOCX))
        assertTrue(word.verifyIntegrity())
        java.util.zip.ZipFile(word.file).use { zip ->
            val media = zip.entries().asSequence().filter { it.name.startsWith("word/media/") }.toList()
            assertEquals(1, media.size)
            assertEquals(asset.contentSha256, figureSha256Hex(zip.getInputStream(media.single()).readBytes()))
            val body = zip.getInputStream(zip.getEntry("word/document.xml")).reader(Charsets.UTF_8).readText()
            assertTrue(body.contains(caption))
            listOf("来源", "第 1 版", "未经独立核对", "读作", "本图由 AI 生成", "图 1").forEach {
                assertFalse(body.contains(it))
            }
            assertTrue(body.contains("<w:jc w:val=\"center\"/>"))
            val extent = requireNotNull(Regex("<wp:extent cx=\"([0-9]+)\" cy=\"([0-9]+)\"").find(body))
            val widthPt = extent.groupValues[1].toLong() / 12700f
            val heightPt = extent.groupValues[2].toLong() / 12700f
            assertTrue(widthPt <= (595.3f - manifest.layout.marginPt * 2) * .72f * .5f + .5f)
            assertTrue(heightPt <= (841.9f - manifest.layout.marginPt * 2) * .32f * .5f + .5f)
        }
        word.file.copyTo(java.io.File(app.cacheDir, "teaching-detailed-answer.docx"), overwrite = true)
        val pdf = com.tingyun.smartmistakebook.core.export.MistakePdfExporter(app).prepareDetailedAnswers(input(MistakeExportFormat.PDF))
        assertTrue(pdf.verifyIntegrity())
        assertTrue(Regex("/Subtype\\s*/Image").containsMatchIn(pdf.file.readBytes().toString(Charsets.ISO_8859_1)))
        pdf.file.copyTo(java.io.File(app.cacheDir, "teaching-detailed-answer.pdf"), overwrite = true)
        android.os.ParcelFileDescriptor.open(pdf.file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount > 0)
                renderer.openPage(0).use { page ->
                    val bitmap = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        java.io.File(app.cacheDir, "teaching-detailed-answer.png").outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    private companion object {
        const val PROGRAM_FUNCTION = """

const visual = {
 prepare: function () { return ['0','1','2','x','y','y = x²','(2,4)','面积 = 8/3','4'].map(function(t){return {kind:'text',payload:t};}); },
 render: function(i,m) {
  var w=i.viewport.widthDu,h=i.viewport.heightDu,x0=46,y0=h-40,pw=w-94,ph=h-85;
  function X(x){return x0+x*pw/2.4;} function Y(y){return y0-y*ph/5;}
  var paths=[],nodes=[];
  function p(id,cmd,color,stroke){paths.push({id:id,commands:cmd});var n={type:'path',pathId:id,fill:color};if(stroke)n.stroke=stroke;nodes.push(n);}
  function t(k,x,y,ha,va){nodes.push({type:'label',contentId:m[k].contentId,anchor:{x:x,y:y},hAlign:ha||'left',vAlign:va||'top',color:'#34424A'});}
  var pts=[];for(var k=0;k<=64;k++){var x=2*k/64;pts.push([X(x),Y(x*x)]);}
  var area=visualSdk.polyline(pts);area.push(['L',X(2),Y(0)],['Z']);p('area',area,'#DCEEE7');
  p('x',visualSdk.axis({x0:x0,y0:y0,x1:w-16,y1:y0}),'#4A5660',{width:1.3});
  p('y',visualSdk.axis({x0:x0,y0:y0,x1:x0,y1:22}),'#4A5660',{width:1.3});
  p('limit',visualSdk.lineSegments([[X(2),Y(0)],[X(2),Y(4)]]),'#77858B',{width:1.2,dash:[4,4]});
  p('curve',visualSdk.lineSegments(pts),'#246D63',{width:2.4,cap:'round'});
  p('point',visualSdk.circle(X(2),Y(4),3),'#246D63');
  t(0,x0-8,y0+8,'right');t(1,X(1),y0+8,'center');t(2,X(2),y0+8,'center');
  t(3,w-12,y0+8,'right');t(4,x0-12,5,'right');t(5,X(1.1),Y(3.8),'center');
  t(6,X(2)+7,Y(4)-9);t(7,X(1.6),Y(1.2),'center');t(8,x0-8,Y(4),'right','middle');
  return {schemaVersion:1,sceneId:'quadratic-area',revision:i.revision,background:'#FFFFFF',viewBox:{x:0,y:0,width:w,height:h},paths:paths,root:{type:'group',children:nodes}};
 }
};

        """

        const val PROGRAM_FORCE = """

const visual = {
 prepare: function(){return ['F','木塞','mg','f','ΔpS'].map(function(t){return {kind:'text',payload:t};});},
 render: function(i,m){
  var w=i.viewport.widthDu,h=i.viewport.heightDu,cx=w*.46,cy=h*.43,paths=[],nodes=[];
  function p(id,c,color,stroke){paths.push({id:id,commands:c});var n={type:'path',pathId:id,fill:color};if(stroke)n.stroke=stroke;nodes.push(n);}
  function t(k,x,y,ha){nodes.push({type:'label',contentId:m[k].contentId,anchor:{x:x,y:y},hAlign:ha||'center',vAlign:'top',color:'#34424A'});}
  p('bottle',visualSdk.lineSegments([[cx-38,cy-28],[cx-38,h*.74],[cx+38,h*.74],[cx+38,cy-28]]),'#97A3A5',{width:2});
  p('cork',[['M',cx-28,cy-25],['L',cx+28,cy-25],['L',cx+28,cy+25],['L',cx-28,cy+25],['Z']],'#DDC4A2');
  p('pull',visualSdk.arrow(cx,cy-25,cx,22),'#287866',{width:2});
  p('weight',visualSdk.arrow(cx-20,cy+25,cx-20,h-44),'#526679',{width:1.8});
  p('friction',visualSdk.arrow(cx,cy+25,cx,h-44),'#9A713E',{width:1.8});
  p('pressure',visualSdk.arrow(cx+20,cy+25,cx+20,h-44),'#7F6E91',{width:1.8});
  t(0,cx+14,24,'left');t(1,cx,cy-9);t(2,cx-31,h-30,'right');t(3,cx,h-30);t(4,cx+31,h-30,'left');
  return {schemaVersion:1,sceneId:'cork-forces',revision:i.revision,background:'#FFFFFF',viewBox:{x:0,y:0,width:w,height:h},paths:paths,root:{type:'group',children:nodes}};
 }
};

        """

        const val PROGRAM_RATIO = """

const visual = {
 prepare: function(){return ['F₁ 雌株','3 : 1','F₁ 雄株','1 : 1','绿色叶','花斑叶'].map(function(t){return {kind:'text',payload:t};});},
 render: function(i,m){
  var w=i.viewport.widthDu,h=i.viewport.heightDu,left=20,right=w-20,bw=right-left,bh=18,paths=[],nodes=[];
  function rect(id,x,y,width,color){paths.push({id:id,commands:[['M',x,y],['L',x+width,y],['L',x+width,y+bh],['L',x,y+bh],['Z']]});nodes.push({type:'path',pathId:id,fill:color});}
  function t(k,x,y,ha){nodes.push({type:'label',contentId:m[k].contentId,anchor:{x:x,y:y},hAlign:ha||'left',vAlign:'top',color:'#34424A'});}
  var y1=h*.25,y2=h*.63;
  rect('female-green',left,y1,bw*.75,'#398474');rect('female-pattern',left+bw*.75,y1,bw*.25,'#D7B36A');
  rect('male-green',left,y2,bw*.5,'#398474');rect('male-pattern',left+bw*.5,y2,bw*.5,'#D7B36A');
  var hatch=[];for(var x=left+bw*.75+5;x<right-8;x+=12)hatch.push(['M',x,y1+bh-3],['L',x+6,y1+3]);
  for(var x=left+bw*.5+5;x<right-8;x+=12)hatch.push(['M',x,y2+bh-3],['L',x+6,y2+3]);
  paths.push({id:'pattern',commands:hatch});nodes.push({type:'path',pathId:'pattern',fill:'#775F32',stroke:{width:1}});
  t(0,left,y1-28);t(1,right,y1-28,'right');t(2,left,y2-28);t(3,right,y2-28,'right');
  rect('legend-green',left,h-26,14,'#398474');rect('legend-pattern',w*.53,h-26,14,'#D7B36A');
  t(4,left+22,h-28);t(5,w*.53+22,h-28);
  return {schemaVersion:1,sceneId:'phenotype-ratios',revision:i.revision,background:'#FFFFFF',viewBox:{x:0,y:0,width:w,height:h},paths:paths,root:{type:'group',children:nodes}};
 }
};

        """

        const val PROGRAM = """
          const visual = {
            prepare: function () { return [{kind: 'text', payload: '正方向'}]; },
            render: function (i, m) {
              return {schemaVersion: 1, sceneId: 'direction', revision: i.revision, background: '#ffffff',
                viewBox: {x: 0, y: 0, width: i.viewport.widthDu, height: i.viewport.heightDu},
                paths: [{id: 'axis', commands: visualSdk.axis({x0: 24, y0: 100, x1: 200, y1: 100, ticks: 3})}],
                root: {type: 'group', children: [
                  {type: 'path', pathId: 'axis', fill: '#26354a', stroke: {width: 1.5}},
                  {type: 'label', contentId: m[0].contentId, anchor: {x: 100, y: 60},
                   hAlign: 'left', vAlign: 'top', color: '#26354a'}]}, controls: [], hitRegions: []};
            }
          };
        """
    }
}
