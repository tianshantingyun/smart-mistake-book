#!/usr/bin/env node
/**
 * 产品提示词最小样例（FigurePromptAssembler.JS_STATIC_SAMPLE）可编译性探针。
 *
 * **它消灭的具体失败**：产品 JS 契约里的样例曾写出 `stroke: '#26354a'`（字符串）与
 * 无 fill 的 path——生产编译器要求 stroke 是对象且 fill 必填（KD-78 ①），模型照样例写
 * 必然编译失败（批次 51 MVA 作者链 120 题 RENDERED 0/104 的产品侧根因）。本探针从
 * Kotlin 源码**逐字提取**样例，经真实 `visual-sdk.js` + 真实 `visual-compiler.js` 执行，
 * 样例不可编译即退出码 1。
 *
 * 用法：
 *   node tools/figure_eval/prompt_sample_compile_probe.mjs \
 *     [kotlinPath] [compilerPath] [sdkPath]
 *
 * 输出一行 JSON：{"compiles":true,"pathCommands":N,"labels":N} 或
 * {"compiles":false,"error":"..."}。
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import vm from 'node:vm';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..', '..');
const kotlinPath = process.argv[2]
  || path.join(repoRoot, 'core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/FigurePromptAssembler.kt');
const compilerPath = process.argv[3]
  || path.join(repoRoot, 'core/data/src/main/resources/visual/visual-compiler.js');
const sdkPath = process.argv[4]
  || path.join(repoRoot, 'core/data/src/main/resources/visual/visual-sdk.js');

function fail(message) {
  console.log(JSON.stringify({ compiles: false, error: String(message) }));
  process.exit(1);
}

let kotlin;
try {
  kotlin = readFileSync(kotlinPath, 'utf8');
} catch (error) {
  fail('cannot read Kotlin source: ' + error.message);
}
const match = kotlin.match(/JS_STATIC_SAMPLE: String = """([\s\S]*?)"""\.trimIndent\(\)/);
if (!match) {
  fail('JS_STATIC_SAMPLE literal not found in ' + kotlinPath);
}
const sample = match[1];

const context = vm.createContext({ __ktRandomNext: () => 0.5 });
vm.runInContext('const __ktRandom = __ktRandomNext;\nvoid 0;', context, { filename: 'bootstrap.js' });
vm.runInContext(readFileSync(sdkPath, 'utf8'), context, { filename: 'visual-sdk.js' });
vm.runInContext(readFileSync(compilerPath, 'utf8'), context, { filename: 'visual-compiler.js' });
vm.runInContext(sample + '\n;globalThis.__sample = visual;', context, { filename: 'JS_STATIC_SAMPLE' });

const input = {
  revision: 1,
  timeMs: 0,
  seed: 42,
  theme: 'light',
  viewport: { widthDu: 360, heightDu: 240 },
};

let prepared;
try {
  prepared = JSON.parse(vm.runInContext(
    'JSON.stringify(__sample.prepare(' + JSON.stringify(input) + '))', context));
} catch (error) {
  fail('prepare threw: ' + (error && error.message || error));
}
if (!Array.isArray(prepared)) {
  fail('prepare must return an array');
}
const metrics = prepared.map((entry, index) => ({
  contentId: 'c' + index,
  widthDu: Math.max(8, String(entry.payload || '').length * 8),
  heightDu: 18,
  baselineDu: 14,
  fontSizeSp: 16,
}));

let scene;
try {
  scene = vm.runInContext(
    'JSON.stringify(__sample.render(' + JSON.stringify(input) + ',' + JSON.stringify(metrics) + '))',
    context);
} catch (error) {
  fail('render threw: ' + (error && error.message || error));
}

const expectJson = JSON.stringify({ sceneRevision: 1, viewport: { widthDu: 360, heightDu: 240 } });
try {
  const out = vm.runInContext(
    '__visualCompile(' + JSON.stringify(scene) + ',' + JSON.stringify(JSON.stringify(metrics)) +
      ',' + JSON.stringify(expectJson) + ')',
    context,
  );
  const compiled = JSON.parse(out);
  const ops = compiled.ops;
  const pathOps = ops.filter((op) => op.op === 'path').length;
  const labelOps = ops.filter((op) => op.op === 'label').length;
  console.log(JSON.stringify({
    compiles: true,
    ops: ops.length,
    pathOps,
    labelOps,
    stats: compiled.stats,
  }));
  process.exit(0);
} catch (error) {
  fail(error && error.message || error);
}
