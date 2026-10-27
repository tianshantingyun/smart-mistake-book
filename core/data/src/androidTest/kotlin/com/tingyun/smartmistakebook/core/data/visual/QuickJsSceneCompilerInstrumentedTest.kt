package com.tingyun.smartmistakebook.core.data.visual

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureBatch
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasureResult
import com.tingyun.smartmistakebook.core.domain.VisualContentMeasurer
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRejection
import com.tingyun.smartmistakebook.core.domain.VisualExecutionRequest
import com.tingyun.smartmistakebook.core.domain.VisualExecutionResult
import com.tingyun.smartmistakebook.core.domain.VisualMeasuredContent
import com.tingyun.smartmistakebook.core.domain.VisualViewport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * SceneV1 编译接入的引擎级仪器化用例（批次 27 T1；R1 §7.2/§7.3 的设备轨承载）。
 *
 * **先红后绿的边界（如实登记）**：本批未运行 Gradle/设备命令（门禁由脚本串行执行、
 * 设备轨归批次收口），下列断言是设备轨要执行的判据，不是已通过的结果。JS 侧行为已在
 * Node 测试台（`tools/visual_compiler/run_tests.mjs`，84 项断言）对**同一资产文件**验证：
 * 合法场景/错误码矩阵/同输入逐字节确定性/预算拒绝/上限场景 3.5ms——但 QuickJS（原生
 * JNI）与 Node(V8) 在数字格式化与超越函数上有末位 ULP 差异，跨引擎不做字节级金样，
 * 设备上的确定性由本文件用同引擎两次执行钉住。
 *
 * 覆盖映射（任务书 T1 第 4 条测试矩阵）：
 * - 合法场景编译 → `aFullSceneCompilesIntoTypedDrawOps`（全节点类型 + Q 提升 + SDK 圆；
 *   经 [VisualDrawOpsValidator] 类型化消费——任务书第 2 条"可 fake 消费"的形态）；
 * - 种子确定性 → `theSameSceneCompilesToByteIdenticalDrawOps`（同 (scene, metrics, expect)
 *   两次事务逐字节相同——编译器无随机/时钟的结构性判据在真引擎上成立）；
 * - 非法形状拒 → `anIllegalSceneShapeIsRejectedAsInvalidSceneWithTypedCode`（不进
 *   INVALID_PROGRAM 泛化）+ `anSdkArgumentViolationIsRejectedAsInvalidScene`；
 * - 超预算拒 → `aSceneOverTheNodeBudgetIsRejectedWithTypedCode`；
 * - 100ms 协作期内 → `aMaxBudgetSceneCompilesWithinTheCooperativePhaseWindow`（256 节点
 *   /4080 展开命令；R1 §5.2 的设备实测证据项——若设备 QuickJS 超 100ms 落 PHASE_TIMEOUT，
 *   本用例红，按 R1 §8 如实登记预算复判，不得放宽协作期限来变绿）。
 */
@RunWith(AndroidJUnit4::class)
class QuickJsSceneCompilerInstrumentedTest {

    private val engines = CopyOnWriteArrayList<QuickJsVisualExecutionEngine>()

    @After
    fun closeEngines() {
        engines.forEach { it.close() }
        engines.clear()
    }

    /** 度量桩：单条 text，contentId 恒为 content-0，w=10/h=12/baseline=9（标签放置判据）。 */
    private class SingleContentMeasurer : VisualContentMeasurer {
        override suspend fun measure(batch: VisualContentMeasureBatch): VisualContentMeasureResult =
            VisualContentMeasureResult(
                batch.entries.mapIndexed { index, _ ->
                    VisualMeasuredContent(
                        contentId = "content-$index",
                        widthDu = 10f,
                        heightDu = 12f,
                        baselineDu = 9f,
                        fontSizeSp = 16f,
                        digest = "digest-$index",
                    )
                },
            )
    }

    private fun engine(
        cooperativeTimeoutMillis: Long = 100L,
        hardDeadlineMillis: Long = 1_500L,
    ): QuickJsVisualExecutionEngine = QuickJsVisualExecutionEngine(
        SingleContentMeasurer(),
        cooperativeTimeoutMillis = cooperativeTimeoutMillis,
        hardDeadlineMillis = hardDeadlineMillis,
    ).also { engines.add(it) }

    private fun request(sourceJs: String, seed: Long = 42L) = VisualExecutionRequest(
        programId = "program-compiler",
        sourceJs = sourceJs,
        sceneRevision = 1,
        stateJson = null,
        viewport = VisualViewport(360, 240),
        timeMs = 1_000L,
        seed = seed,
        theme = "light",
    )

    private fun renderedOrFail(result: VisualExecutionResult): VisualExecutionResult.Rendered {
        if (result !is VisualExecutionResult.Rendered) {
            fail("expected Rendered, got: $result")
        }
        // org.junit.Assert.fail 返回 Unit（非 Nothing），编译器不会据此 smart-cast；
        // fail 抛 AssertionError 后不可达，cast 仅在 Rendered 分支实际生效（与同批
        // QuickJsVisualExecutionEngineInstrumentedTest 的 `as Rendered` 惯用法同形）。
        return result as VisualExecutionResult.Rendered
    }

    private fun validatedDocument(rendered: VisualExecutionResult.Rendered): VisualDrawOpsDocument {
        assertNotNull(rendered.drawCommandsJson)
        val drawJson = rendered.drawCommandsJson!!
        val validation = VisualDrawOpsValidator.validate(drawJson, rendered.contentIds, VisualViewport(360, 240))
        if (validation !is VisualDrawOpsValidation.Valid) {
            // sealed 两变体：非 Valid 即 Invalid；显式窄化取 code/message（JUnit fail 返回
            // Unit，`!is Valid` 分支不会被编译器收窄为 Invalid）。
            val invalid = validation as VisualDrawOpsValidation.Invalid
            fail("compiled draw ops failed native validation: ${invalid.code}: ${invalid.message}")
        }
        return (validation as VisualDrawOpsValidation.Valid).document
    }

    @Test
    fun aFullSceneCompilesIntoTypedDrawOps() = runBlocking {
        val engine = engine()

        val rendered = renderedOrFail(engine.execute(request(FULL_SCENE_SOURCE)))

        // 场景 JSON 保留（契约 §8 快照"验证后的场景与 RenderScene"两者都存）。
        assertTrue(rendered.sceneJson.contains("\"sceneId\":\"scene-1\""))
        assertEquals(1, rendered.drawSchemaVersion)
        val document = validatedDocument(rendered)
        // group(save+concat) → clip(烘焙) 在 concat 之前 → path(Q 已提升为 C) → label → restore。
        assertEquals(VisualDrawOp.Save, document.ops[0])
        val clip = document.ops[1] as VisualDrawOp.Clip
        assertEquals(VisualFillRule.EVEN_ODD, clip.rule)
        // clip 烘焙组合坐标：本地 (10,10) 过矩阵 (+5,+5) → (15,15)。
        val clipMove = clip.path[0] as VisualPathCommand.MoveTo
        assertEquals(15.0, clipMove.x, 0.0)
        val concat = document.ops[2] as VisualDrawOp.Concat
        assertEquals(VisualMatrix(1.0, 0.0, 0.0, 1.0, 5.0, 5.0), concat.matrix)
        // 第一个 path 子节点：Q 定义已提升为 C（命令数 2 不变）。
        val qPath = document.ops[3] as VisualDrawOp.DrawPath
        assertEquals(2, qPath.path.size)
        assertTrue(qPath.path[1] is VisualPathCommand.CubicTo)
        assertEquals("#336699", qPath.fill)
        // 第二个 path 子节点：SDK circle = M + 4·C + Z（Q/A 词表面在 DrawOps 不存在）。
        val dotPath = document.ops[4] as VisualDrawOp.DrawPath
        assertEquals(6, dotPath.path.size)
        assertTrue(dotPath.path[1] is VisualPathCommand.CubicTo)
        assertEquals(VisualPathCommand.Close, dotPath.path[5])
        assertEquals(1.0, dotPath.alpha, 0.0)
        val label = document.ops[5] as VisualDrawOp.Label
        // anchor(100,60) 过矩阵(+5,+5) → (105,65)；offset(+2,+3) → (107,68)；
        // 居中(10du 宽) → x=102；middle(12du 高) + baseline 9 → y=71。
        assertEquals(102.0, label.x, 0.0)
        assertEquals(71.0, label.y, 0.0)
        assertEquals(VisualTextHAlign.CENTER, label.hAlign)
        assertEquals(0.5, label.alpha, 0.0)
        assertEquals(VisualDrawOp.Restore, document.ops[6])
        // 展开回放：q(2) + dot(6) + clip(4) + 命中区(4) = 16；标签 1 条。
        assertEquals(16, document.stats.pathCommands)
        assertEquals(1, document.stats.labels)
    }

    @Test
    fun theSameSceneCompilesToByteIdenticalDrawOps() = runBlocking {
        val engine = engine()

        val first = renderedOrFail(engine.execute(request(FULL_SCENE_SOURCE)))
        val second = renderedOrFail(engine.execute(request(FULL_SCENE_SOURCE)))
        // 不同 seed：编译器无随机面，输出仍须逐字节相同（种子只影响 SDK random 的程序侧）。
        val third = renderedOrFail(engine.execute(request(FULL_SCENE_SOURCE, seed = 7L)))

        assertNotNull(first.drawCommandsJson)
        val firstJson = first.drawCommandsJson!!
        assertEquals(firstJson, second.drawCommandsJson)
        assertEquals(firstJson, third.drawCommandsJson)
        // 场景 JSON 同样逐位重放（确定性的事务级证词）。
        assertEquals(first.sceneJson, second.sceneJson)
    }

    @Test
    fun anIllegalSceneShapeIsRejectedAsInvalidSceneWithTypedCode() = runBlocking {
        val engine = engine()

        // render 返回 `{}`：保守面（字节/JSON/冻结引用）放行，编译层按 schemaVersion 拒绝。
        val result = engine.execute(request(BLANK_SCENE_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        // 任务书第 3 条：类型化拒绝，不进 INVALID_PROGRAM 泛化。
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.INVALID_SCENE,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.contains("SCENE_FIELD_SCHEMA_VERSION"))
        assertTrue(rejected.diagnostics.length <= 512)
    }

    @Test
    fun anSdkArgumentViolationIsRejectedAsInvalidScene() = runBlocking {
        val engine = engine()

        // visualSdk 对非有限参数 fail-closed（R1 §3.3）；错误经分类器折 INVALID_SCENE。
        val result = engine.execute(request(SDK_VIOLATION_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.INVALID_SCENE,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.contains("SCENE_SDK_ARG"))
    }

    @Test
    fun aSceneOverTheNodeBudgetIsRejectedWithTypedCode() = runBlocking {
        val engine = engine()

        // 258 节点（根组 + 257 个 label）：编译器在 257 节点处 fail-closed。
        val result = engine.execute(request(OVER_NODE_BUDGET_SOURCE))

        val rejected = result as VisualExecutionResult.Rejected
        assertEquals(
            "actual: ${rejected.reason}: ${rejected.diagnostics}",
            VisualExecutionRejection.INVALID_SCENE,
            rejected.reason,
        )
        assertTrue(rejected.diagnostics.contains("SCENE_NODE_COUNT"))
    }

    @Test
    fun aMaxBudgetSceneCompilesWithinTheCooperativePhaseWindow() = runBlocking {
        // 预算上限场景（256 节点 / 4080 展开命令）必须落在 100ms 协作期内——
        // R1 §5.2 的设备实测证据项。桌面 Wasm 外推 7.5–35ms/帧；若设备 QuickJS 超限，
        // 本用例以 PHASE_TIMEOUT 变红：如实复判预算，不改宽协作期限。
        val engine = engine()

        val rendered = renderedOrFail(engine.execute(request(MAX_BUDGET_SCENE_SOURCE)))

        val document = validatedDocument(rendered)
        assertEquals(256, document.stats.nodes)
        assertEquals(240, document.stats.ops)
    }

    private companion object {
        /** 与主仪器化文件的 MINIMAL_SCENE_RENDER 同文的本地副本（须先于引用它的夹具声明）。 */
        val MINIMAL_SCENE_RENDER_HELPER = """
            render: function (input, metrics) {
              return { schemaVersion: 1, sceneId: 'scene-1', revision: input.revision,
                       viewBox: { x: 0, y: 0, width: input.viewport.widthDu,
                                  height: input.viewport.heightDu },
                       paths: [], root: null, controls: [], hitRegions: [] };
            }
        """.trimIndent()

        /**
         * 全节点类型场景：组(clip+matrix+opacity=0.5) > path(SDK circle，fill) + label。
         * paths 定义含一条 Q 命令（提升为 C）；clip 为 evenodd。度量桩给 content-0
         * w=10/h=12/baseline=9。
         */
        val FULL_SCENE_SOURCE = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: '标签' }]; },
              render: function (input, metrics) {
                return { schemaVersion: 1, sceneId: 'scene-1', revision: input.revision,
                         viewBox: { x: 0, y: 0, width: input.viewport.widthDu,
                                    height: input.viewport.heightDu },
                         background: '#ffffff',
                         paths: [{ id: 'q', commands: [['M', 2, 0], ['Q', 7, 8, 3, 0]] },
                                 { id: 'dot', commands: visualSdk.circle(50, 50, 40) }],
                         root: { type: 'group', matrix: [1, 0, 0, 1, 5, 5], opacity: 0.5,
                                 clip: { path: [['M', 10, 10], ['L', 20, 10], ['L', 20, 20], ['Z']],
                                         rule: 'evenodd' },
                                 children: [
                                   { type: 'path', pathId: 'q', fill: '#336699' },
                                   { type: 'path', pathId: 'dot', stroke: { width: 1 } },
                                   { type: 'label', contentId: metrics[0].contentId,
                                     anchor: { x: 100, y: 60 }, offset: { x: 2, y: 3 },
                                     hAlign: 'center', vAlign: 'middle', color: '#26354a' } ] },
                         controls: [],
                         hitRegions: [{ id: 'h1', path: [['M', 0, 0], ['L', 5, 0], ['L', 5, 5], ['Z']],
                                        rule: 'nonzero' }] };
              },
            };
        """.trimIndent()

        /** render 返回 `{}`：schemaVersion 缺失 → SCENE_FIELD_SCHEMA_VERSION。 */
        val BLANK_SCENE_SOURCE = """
            const visual = {
              prepare: function () { return []; },
              render: function (input, metrics) { return {}; },
            };
        """.trimIndent()

        /** SDK 参数违规：visualSdk.circle 收 NaN 半径 → SceneSdkError（prepare 阶段）。 */
        val SDK_VIOLATION_SOURCE = """
            const visual = {
              prepare: function () {
                return [{ kind: 'text', payload: visualSdk.circle(0, 0, NaN).length }];
              },
              $MINIMAL_SCENE_RENDER_HELPER,
            };
        """.trimIndent()

        /** 258 节点（根组 + 257 个兄弟 label）→ SCENE_NODE_COUNT。 */
        val OVER_NODE_BUDGET_SOURCE = """
            const visual = {
              prepare: function () { return [{ kind: 'text', payload: 'x' }]; },
              render: function (input, metrics) {
                var children = [];
                for (var i = 0; i < 257; i++) {
                  children.push({ type: 'label', contentId: metrics[0].contentId,
                                  anchor: { x: i, y: 0 }, hAlign: 'left', vAlign: 'top',
                                  color: '#000000' });
                }
                return { schemaVersion: 1, sceneId: 'scene-1', revision: input.revision,
                         viewBox: { x: 0, y: 0, width: input.viewport.widthDu,
                                    height: input.viewport.heightDu },
                         paths: [], root: { type: 'group', children: children },
                         controls: [], hitRegions: [] };
              },
            };
        """.trimIndent()

        /**
         * 预算上限场景（256 节点 / 4080 展开命令）：根组 + 15 组 × 16 path，
         * 同一条 17 命令定义被引用 240 次（240×17=4080 ≤ 4096）。
         */
        val MAX_BUDGET_SCENE_SOURCE = """
            const visual = {
              prepare: function () { return []; },
              render: function (input, metrics) {
                var commands = [['M', 0, 0]];
                for (var i = 1; i < 17; i++) { commands.push(['L', i, i % 16]); }
                var groups = [];
                for (var g = 0; g < 15; g++) {
                  var kids = [];
                  for (var p = 0; p < 16; p++) {
                    kids.push({ type: 'path', pathId: 'p', fill: '#123456' });
                  }
                  groups.push({ type: 'group', children: kids });
                }
                return { schemaVersion: 1, sceneId: 'scene-1', revision: input.revision,
                         viewBox: { x: 0, y: 0, width: input.viewport.widthDu,
                                    height: input.viewport.heightDu },
                         paths: [{ id: 'p', commands: commands }],
                         root: { type: 'group', children: groups },
                         controls: [], hitRegions: [] };
              },
            };
        """.trimIndent()
    }
}
