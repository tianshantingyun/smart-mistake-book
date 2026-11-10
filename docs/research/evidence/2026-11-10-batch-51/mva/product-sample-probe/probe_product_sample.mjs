#!/usr/bin/env node
/**
 * 批次 51 / MVA T26 反证：把 `FigurePromptAssembler.JS_STATIC_SAMPLE`
 * （`core/data/.../FigurePromptAssembler.kt:256-271`，产品 JS 契约里给作者模型的最小样例）
 * 原样送进生产同源编译器 `visual-compiler.js`，看它是否可编译。
 *
 * 结论若红：产品提示词自己在教模型写出非法 SceneV1——作者链的系统性失败不是模型"能力差"，
 * 而是提示词样例错误。运行：
 *   node docs/research/evidence/2026-11-10-batch-51/mva/product-sample-probe/probe_product_sample.mjs
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import vm from 'node:vm';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..', '..', '..', '..', '..', '..');
const compilerPath = path.join(repoRoot, 'core/data/src/main/resources/visual/visual-compiler.js');
const context = vm.createContext({ __ktRandomNext: () => 0.5 });
vm.runInContext('const __ktRandom = __ktRandomNext;\nvoid 0;', context);
vm.runInContext(readFileSync(compilerPath, 'utf8'), context, { filename: 'visual-compiler.js' });

// 逐字取自 FigurePromptAssembler.kt 的 JS_STATIC_SAMPLE（只把 input.revision 固定为 1）。
const scene = {
  schemaVersion: 1,
  sceneId: 'fig',
  revision: 1,
  viewBox: { x: 0, y: 0, width: 360, height: 240 },
  paths: [
    { id: 'axis', commands: [['M', 24, 180], ['L', 300, 180]] },
    { id: 'dot', commands: [['M', 160, 84], ['L', 160, 84]] },
  ],
  root: {
    type: 'group',
    children: [
      { type: 'path', pathId: 'axis', stroke: '#26354a' },
      { type: 'path', pathId: 'dot', stroke: '#26354a' },
    ],
  },
};
const metrics = [];
const expect = { sceneRevision: 1, viewport: { widthDu: 360, heightDu: 240 } };
try {
  const out = vm.runInContext(
    '__visualCompile(' + JSON.stringify(JSON.stringify(scene)) + ',' +
      JSON.stringify(JSON.stringify(metrics)) + ',' + JSON.stringify(JSON.stringify(expect)) + ')',
    context,
  );
  console.log(JSON.stringify({ compiles: true, drawOps: JSON.parse(out) }, null, 2));
  process.exit(0);
} catch (error) {
  console.log(JSON.stringify({ compiles: false, error: String(error && error.message || error) }, null, 2));
  process.exit(1);
}
