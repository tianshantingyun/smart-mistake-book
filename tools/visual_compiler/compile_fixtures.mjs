#!/usr/bin/env node
/**
 * Q-04 六类 fixture 静态子集的**漂移门**（批次 38 T-Q；R3 §4.3"静态 MVA 的必测子集是
 * 1/2/5/6 + 新组合"）。
 *
 * 职责：对 `core/data/src/test/resources/visual/fixtures/q04/*.fixture.json` 里的
 * SceneV1 + metrics，用**生产同源**编译器（`visual-compiler.js`，与 `run_tests.mjs`
 * 同一装载方式）重新编译，并与随 fixture 提交的 `*.drawops.json` 逐字节比对。
 *
 * 它消灭的具体失败：提交的 DrawOps 若与场景脱节（手改场景没重编、跨版本编译器产出的
 * 旧产物），JVM 侧（`VisualFigureFixtureOracleTest`）会把它当"编译器真实输出"验证——
 * fixture 语料从此失去"生产编译器产物"的身份。本门让漂移在任何一轮重跑时显式爆炸。
 *
 * 运行：
 * - `node tools/visual_compiler/compile_fixtures.mjs`            （默认 verify；漂移/缺失即非零退出）
 * - `node tools/visual_compiler/compile_fixtures.mjs --write`    （重新生成 drawops.json）
 *
 * JVM 侧不能执行 JS（无 QuickJS 原生库，见 `QuickJsVisualExecutionEngineJvmTest` 头注），
 * 因此"场景 → DrawOps"这半边只能由本门在 Node 侧钉住；"DrawOps → 类型化文档 + oracle"
 * 半边由 JVM 门钉住。两半合起来才是 fixture 语料的完整闭环。
 */
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import vm from 'node:vm';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..', '..');
const compilerPath = path.join(repoRoot, 'core/data/src/main/resources/visual/visual-compiler.js');
const fixturesDir = path.join(repoRoot, 'core/data/src/test/resources/visual/fixtures/q04');

const write = process.argv.includes('--write');

function makeContext() {
  const context = vm.createContext({ __ktRandomNext: () => 0.5 });
  vm.runInContext('const __ktRandom = __ktRandomNext;\nvoid 0;', context);
  vm.runInContext(readFileSync(compilerPath, 'utf8'), context, {
    filename: 'visual-compiler.js',
  });
  return context;
}

function compile(context, scene, metrics, viewport) {
  const expect = JSON.stringify({ sceneRevision: scene.revision, viewport });
  return vm.runInContext(
    '__visualCompile(' +
      JSON.stringify(JSON.stringify(scene)) + ',' +
      JSON.stringify(JSON.stringify(metrics)) + ',' +
      JSON.stringify(expect) +
      ')',
    context,
  );
}

const fixtureFiles = readdirSync(fixturesDir).filter((name) => name.endsWith('.fixture.json')).sort();
if (fixtureFiles.length === 0) {
  console.error(`no *.fixture.json under ${fixturesDir}`);
  process.exit(1);
}

const context = makeContext();
let failed = 0;
for (const fixtureFile of fixtureFiles) {
  const fixturePath = path.join(fixturesDir, fixtureFile);
  const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));
  const drawOpsPath = fixturePath.replace(/\.fixture\.json$/, '.drawops.json');
  let actual;
  try {
    actual = compile(context, fixture.scene, fixture.metrics, fixture.viewport);
  } catch (error) {
    console.error(`FAIL ${fixtureFile}: compiler rejected the scene :: ${String(error && error.message).slice(0, 160)}`);
    failed += 1;
    continue;
  }
  const expectedText = actual.endsWith('\n') ? actual : actual + '\n';
  if (write) {
    writeFileSync(drawOpsPath, expectedText, 'utf8');
    console.log(`WRITE ${path.basename(drawOpsPath)} (${actual.length} chars)`);
    continue;
  }
  if (!readFileSync(drawOpsPath, 'utf8').endsWith(expectedText)) {
    console.error(`FAIL ${fixtureFile}: committed drawops drifted from a fresh production-compiler run`);
    failed += 1;
    continue;
  }
  console.log(`OK    ${fixtureFile} (${actual.length} chars, byte-identical)`);
}

if (failed > 0) {
  console.error(`\n${fixtureFiles.length - failed}/${fixtureFiles.length} fixtures verified, ${failed} FAILED`);
  console.error('regenerate with: node tools/visual_compiler/compile_fixtures.mjs --write');
  process.exit(1);
}
console.log(`\n${fixtureFiles.length}/${fixtureFiles.length} fixtures byte-identical to a fresh production-compiler run`);
