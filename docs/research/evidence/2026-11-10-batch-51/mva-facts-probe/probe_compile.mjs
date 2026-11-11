#!/usr/bin/env node
/**
 * 研究探针（批次 51 / R3，2026-11-10）：用**生产同源**的 SceneV1 编译器
 * `core/data/src/main/resources/visual/visual-compiler.js` 把 4 个最小场景编译成
 * DrawOps V1，写出 `probe_outputs.json`，供 `probe_extract.py` 做 A 通道提取。
 *
 * 这不是产品代码、不是评测夹具、不进入冻结清单；它只证明一件事：
 * "RenderScene（= 校验后的显示列表 DrawOps V1）里确实带着可判定的几何/文本事实"，
 * 且"同一份编译器输出可以被离线代码读成 fact_expectations"。
 *
 * 运行：node docs/research/evidence/2026-11-10-batch-51/mva-facts-probe/probe_compile.mjs
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import vm from 'node:vm';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..', '..', '..', '..', '..');
const compilerPath = path.join(
  repoRoot, 'core/data/src/main/resources/visual/visual-compiler.js');

function makeContext() {
  const context = vm.createContext({ __ktRandomNext: () => 0.5 });
  // 镜像 QuickJsVisualExecutionEngine.BOOTSTRAP_JS 的 PRNG 捕获行；本探针不用 SDK。
  vm.runInContext('const __ktRandom = __ktRandomNext;\nvoid 0;', context);
  vm.runInContext(readFileSync(compilerPath, 'utf8'), context, {
    filename: 'visual-compiler.js',
  });
  return context;
}

function compile(context, scene, metrics, expect) {
  return vm.runInContext(
    '__visualCompile(' +
      JSON.stringify(JSON.stringify(scene)) + ',' +
      JSON.stringify(JSON.stringify(metrics)) + ',' +
      JSON.stringify(JSON.stringify(expect)) +
      ')',
    context,
  );
}

const VIEWPORT = { widthDu: 360, heightDu: 240 };
const EXPECT = { sceneRevision: 1, viewport: VIEWPORT };

/** label 的度量条目；真实事务由原生 measurer 给出，这里取固定值（宽度只影响对齐，不影响比例）。 */
function metric(contentId, widthDu, heightDu = 14, baselineDu = 11) {
  return { contentId, widthDu, heightDu, baselineDu, fontSizeSp: 14, digest: 'probe:' + contentId };
}

/** 一条水平线段路径（局部坐标 = 根坐标：本探针不设组矩阵）。 */
function hline(x1, x2, y) {
  return [['M', x1, y], ['L', x2, y]];
}

function ratioBarScene(sceneId, split, xEnd, y, label) {
  return {
    schemaVersion: 1,
    sceneId,
    revision: 1,
    viewBox: { x: 0, y: 0, width: VIEWPORT.widthDu, height: VIEWPORT.heightDu },
    paths: [
      // 两个子路径共享分割点：一条线段被分成两段。
      { id: 'bar', commands: [['M', 40, y], ['L', split, y], ['M', split, y], ['L', xEnd, y]] },
    ],
    root: {
      type: 'group',
      children: [
        {
          type: 'path', id: 'bar-segments', pathId: 'bar',
          fill: '#333333',
          stroke: { width: 4, cap: 'butt', join: 'miter', miterLimit: 4, dash: null },
        },
        {
          type: 'label', id: 'ratio-label', contentId: 'c-ratio',
          anchor: { x: (40 + xEnd) / 2, y: y + 18 },
          hAlign: 'center', vAlign: 'top', color: '#333333',
        },
      ],
    },
  };
}

/** 三张纵向卡片：用于 step_count（计数）与 step_order（自上而下顺序）。 */
function flowCardScene(sceneId, labelsInDrawOrder, yPositions) {
  const children = [];
  labelsInDrawOrder.forEach((text, index) => {
    const y = yPositions[index];
    const contentId = 'c-step-' + index;
    children.push({
      type: 'path', id: 'card-' + index, pathId: 'card',
      fill: '#eef2f7', stroke: { width: 1, cap: 'butt', join: 'miter', miterLimit: 4, dash: null },
    });
    // 卡片矩形用组矩阵平移到各自位置：目的之一是让探针不得不回放 concat 栈。
    children[children.length - 1] = {
      type: 'group', id: 'card-group-' + index,
      matrix: [1, 0, 0, 1, 0, y],
      children: [{
        type: 'path', id: 'card-' + index, pathId: 'card',
        fill: '#eef2f7', stroke: { width: 1, cap: 'butt', join: 'miter', miterLimit: 4, dash: null },
      }],
    };
    children.push({
      type: 'label', id: 'step-label-' + index, contentId,
      anchor: { x: 180, y: y + 25 }, hAlign: 'center', vAlign: 'middle',
      color: '#1f2933',
    });
  });
  return {
    schemaVersion: 1,
    sceneId,
    revision: 1,
    viewBox: { x: 0, y: 0, width: VIEWPORT.widthDu, height: VIEWPORT.heightDu },
    paths: [{ id: 'card', commands: [['M', 60, 0], ['L', 300, 0], ['L', 300, 50], ['L', 60, 50], ['Z']] }],
    root: { type: 'group', children },
  };
}

function singleLabelScene(sceneId, text, contentId) {
  return {
    schemaVersion: 1,
    sceneId,
    revision: 1,
    viewBox: { x: 0, y: 0, width: VIEWPORT.widthDu, height: VIEWPORT.heightDu },
    paths: [{ id: 'base', commands: hline(40, 320, 200) }],
    root: {
      type: 'group',
      children: [
        {
          type: 'path', id: 'base-line', pathId: 'base', fill: '#333333',
          stroke: { width: 2, cap: 'butt', join: 'miter', miterLimit: 4, dash: null },
        },
        {
          type: 'label', id: 'the-label', contentId,
          anchor: { x: 180, y: 170 }, hAlign: 'center', vAlign: 'bottom', color: '#333333',
        },
      ],
    },
  };
}

/** 同一根 1:1 线段，但整体经 90° 旋转组：根空间是**竖直**的 1:1 两段。 */
function rotatedRatioScene() {
  return {
    schemaVersion: 1,
    sceneId: 'probe-ratio-rotated',
    revision: 1,
    viewBox: { x: 0, y: 0, width: VIEWPORT.widthDu, height: VIEWPORT.heightDu },
    paths: [{ id: 'bar', commands: [['M', -90, 0], ['L', 0, 0], ['M', 0, 0], ['L', 90, 0]] }],
    root: {
      type: 'group',
      children: [
        {
          type: 'group',
          id: 'rot-90',
          matrix: [0, 1, -1, 0, 180, 120],
          children: [{
            type: 'path', id: 'bar-rotated', pathId: 'bar',
            fill: '#333333',
            stroke: { width: 4, cap: 'butt', join: 'miter', miterLimit: 4, dash: null },
          }],
        },
        {
          type: 'label', id: 'ratio-label', contentId: 'c-ratio',
          anchor: { x: 200, y: 120 }, hAlign: 'left', vAlign: 'middle', color: '#333333',
        },
      ],
    },
  };
}

const context = makeContext();
const cases = [];

function record(name, scene, metrics, contents, note) {
  const drawOpsText = compile(context, scene, metrics, EXPECT);
  if (typeof drawOpsText !== 'string') {
    throw new Error('compiler returned ' + typeof drawOpsText + ' for ' + name);
  }
  cases.push({ name, note, scene, metrics, contents, drawOps: JSON.parse(drawOpsText) });
}

// 1) 1:1，标签 "1:1"（对应盲测 BLD-PROPCOORD-001）
record(
  'ratio-1to1-correct',
  ratioBarScene('probe-ratio-1to1', 130, 220, 120, '1:1'),
  [metric('c-ratio', 24)],
  { 'c-ratio': '1:1' },
  '两段各 90 du；标签 "1:1"',
);

// 2) 2:3，标签 "2:3"（对应 BLD-PROPCOORD-005）：同一提取器应读到 0.6666666666666666
record(
  'ratio-2to3-correct',
  ratioBarScene('probe-ratio-2to3', 80, 140, 120, '2:3'),
  [metric('c-ratio', 24)],
  { 'c-ratio': '2:3' },
  '两段 40/60 du；标签 "2:3"',
);

// 3) 几何 1:1 但标签 "3:1"：只错标签 → label_set 必须失败、numeric 必须通过
record(
  'ratio-label-mismatch',
  ratioBarScene('probe-ratio-labelbad', 130, 220, 120, '3:1'),
  [metric('c-ratio', 24)],
  { 'c-ratio': '3:1' },
  '两段各 90 du（几何正确）；标签写成 "3:1"',
);

// 3b) 竖直 1:1（组旋转 90°）：检验提取器是否按根空间方向判读
record(
  'ratio-1to1-rotated',
  rotatedRatioScene(),
  [metric('c-ratio', 24)],
  { 'c-ratio': '1:1' },
  '两段各 90 du，但经 matrix [0,1,-1,0,180,120] 旋转成竖直',
);

// 5) 三张卡片，标签自上而下 = 移项 / 合并同类项 / 系数化为1（对应 BLD-FLOWFORM-* step_order）
record(
  'flow-order-correct',
  flowCardScene('probe-flow-ok',
    ['移项', '合并同类项', '系数化为1'], [20, 90, 160]),
  [metric('c-step-0', 28), metric('c-step-1', 84), metric('c-step-2', 84)],
  { 'c-step-0': '移项', 'c-step-1': '合并同类项', 'c-step-2': '系数化为1' },
  '卡片自上而下即正确顺序；矩形经 concat 平移',
);

// 6) 同上但顺序颠倒：ordering 必须失败、count 仍为 3
record(
  'flow-order-reversed',
  flowCardScene('probe-flow-bad',
    ['系数化为1', '合并同类项', '移项'], [20, 90, 160]),
  [metric('c-step-0', 84), metric('c-step-1', 84), metric('c-step-2', 28)],
  { 'c-step-0': '系数化为1', 'c-step-1': '合并同类项', 'c-step-2': '移项' },
  '标签文本与位置同时颠倒',
);

// 7) 去手写：图上残留 "x=5" → label_absent 必须失败
record(
  'handwriting-residue-present',
  singleLabelScene('probe-hw-bad', 'x=5', 'c-hw'),
  [metric('c-hw', 36)],
  { 'c-hw': 'x=5' },
  '禁写内容以文本 label 形式残留',
);

// 8) 去手写：只有印刷标签、无残留 → label_absent 必须通过
record(
  'handwriting-residue-absent',
  singleLabelScene('probe-hw-ok', '题干第(1)问', 'c-hw'),
  [metric('c-hw', 96)],
  { 'c-hw': '题干第(1)问' },
  '图中只有印刷标签；无 "x=5"',
);

const outPath = path.join(here, 'probe_outputs.json');
writeFileSync(outPath, JSON.stringify({
  generated_by: 'probe_compile.mjs',
  compiler: 'core/data/src/main/resources/visual/visual-compiler.js',
  expect: EXPECT,
  cases,
}, null, 2) + '\n', 'utf8');
console.log(JSON.stringify({
  out: outPath,
  cases: cases.map((c) => ({ name: c.name, ops: c.drawOps.ops.length, sha_len: JSON.stringify(c.drawOps).length })),
}, null, 2));
