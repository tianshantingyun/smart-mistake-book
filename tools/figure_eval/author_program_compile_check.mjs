#!/usr/bin/env node
/**
 * 作者程序编译预检（批次 51 / MVA 门执行前的快速分诊）。
 *
 * **它消灭的具体失败**：设备适配器一轮只能给"通过/拒绝"，120 题逐题上设备前没有
 * 离设备的分诊面；作者失败时无法区分"程序语法/协议错"与"SceneV1 编译错"，也无法
 * 在修提示词后快速看编译率是否变化。
 *
 * 与设备链同源：`visual-sdk.js` + `visual-compiler.js` 是设备适配器使用的同一份资产；
 * input 形状、metrics 形状（contentId=c<index>）与 `QuickJsVisualExecutionEngine` 一致。
 * **它不是门证据**：真正的 RENDERED/PNG/度量以设备适配器产物为准（本工具只做编译）。
 *
 * 用法：
 *   node tools/figure_eval/author_program_compile_check.mjs <programsDir> <outJson>
 */
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import vm from 'node:vm';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..', '..');
const sdkPath = path.join(repoRoot, 'core/data/src/main/resources/visual/visual-sdk.js');
const compilerPath = path.join(repoRoot, 'core/data/src/main/resources/visual/visual-compiler.js');
const sdkSource = readFileSync(sdkPath, 'utf8');
const compilerSource = readFileSync(compilerPath, 'utf8');

const programsDir = process.argv[2];
const outPath = process.argv[3];
if (!programsDir || !outPath) {
  console.error('usage: author_program_compile_check.mjs <programsDir> <outJson>');
  process.exit(2);
}

function compileOne(entry) {
  const input = {
    revision: entry.scene_revision || 1,
    timeMs: entry.time_ms || 0,
    seed: entry.seed === undefined ? 42 : entry.seed,
    theme: entry.theme || 'light',
    viewport: entry.viewport || { widthDu: 360, heightDu: 240 },
  };
  const context = vm.createContext({ __ktRandomNext: () => 0.5 });
  try {
    vm.runInContext('const __ktRandom = __ktRandomNext;\nvoid 0;', context, { filename: 'bootstrap.js' });
    vm.runInContext(sdkSource, context, { filename: 'visual-sdk.js' });
    vm.runInContext(compilerSource, context, { filename: 'visual-compiler.js' });
    vm.runInContext(String(entry.source_js) + '\n;globalThis.__program = visual;', context,
      { filename: 'program.js' });
    const protocol = vm.runInContext(
      'typeof __program === "object" && __program !== null && ' +
        'typeof __program.prepare === "function" && typeof __program.render === "function"',
      context);
    if (!protocol) {
      return { status: 'INVALID_PROGRAM', code: 'INVALID_PROGRAM', error: 'visual.prepare/render missing' };
    }
    const prepared = JSON.parse(vm.runInContext(
      'JSON.stringify(__program.prepare(' + JSON.stringify(input) + '))', context));
    if (!Array.isArray(prepared)) {
      return { status: 'INVALID_PROGRAM', code: 'INVALID_PROGRAM', error: 'prepare must return an array' };
    }
    const metrics = prepared.map((item, index) => {
      const payload = String((item && item.payload) || '');
      const digest = createHash('sha256').update(payload, 'utf8').digest('hex');
      return {
        contentId: 'c' + index + '-' + digest.slice(0, 8),
        widthDu: Math.max(8, payload.length * 8),
        heightDu: 18,
        baselineDu: 14,
        fontSizeSp: 16,
      };
    });
    const scene = vm.runInContext(
      'JSON.stringify(__program.render(' + JSON.stringify(input) + ',' +
        JSON.stringify(metrics) + '))', context);
    const expectJson = JSON.stringify({
      sceneRevision: input.revision,
      viewport: { widthDu: input.viewport.widthDu, heightDu: input.viewport.heightDu },
    });
    const out = vm.runInContext(
      '__visualCompile(' + JSON.stringify(scene) + ',' + JSON.stringify(JSON.stringify(metrics)) +
        ',' + JSON.stringify(expectJson) + ')', context);
    const compiled = JSON.parse(out);
    return {
      status: 'COMPILED',
      code: 'OK',
      stats: compiled.stats,
      pathOps: compiled.ops.filter((op) => op.op === 'path').length,
      labelOps: compiled.ops.filter((op) => op.op === 'label').length,
    };
  } catch (error) {
    const message = String(error && error.message || error);
    const match = message.match(/^([A-Z][A-Z0-9_]+):/);
    return {
      status: match ? 'INVALID_SCENE' : 'THROWN',
      code: match ? match[1] : 'THROWN',
      error: message.slice(0, 400),
    };
  }
}

const files = readdirSync(programsDir).filter((name) => name.endsWith('.json')).sort();
const results = files.map((name) => {
  let entry;
  try {
    entry = JSON.parse(readFileSync(path.join(programsDir, name), 'utf8'));
  } catch (error) {
    return { file: name, task_id: name.replace(/\.json$/, ''), status: 'NOT_JSON', error: String(error.message) };
  }
  const result = compileOne(entry);
  return { file: name, task_id: entry.task_id || name.replace(/\.json$/, ''), ...result };
});

const counts = {};
for (const result of results) {
  counts[result.status] = (counts[result.status] || 0) + 1;
}
const codeCounts = {};
for (const result of results) {
  if (result.status !== 'COMPILED') {
    codeCounts[result.code] = (codeCounts[result.code] || 0) + 1;
  }
}
writeFileSync(outPath, JSON.stringify({
  generated_by: 'tools/figure_eval/author_program_compile_check.mjs',
  programs: results.length,
  status_counts: Object.fromEntries(Object.entries(counts).sort()),
  error_code_counts: Object.fromEntries(Object.entries(codeCounts).sort()),
  results,
}, null, 2) + '\n', 'utf8');
console.log(JSON.stringify({ programs: results.length, status_counts: counts, error_code_counts: codeCounts }));
