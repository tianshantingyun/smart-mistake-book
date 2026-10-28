#!/usr/bin/env node
/**
 * SceneV1 编译器 + visualSdk 资产的 Node 测试台（批次 27 T1；R1 `docs/research/
 * 2026-10-27-scenev1-compiler-design.md` §7.2 的夹具生成器/测试矩阵）。
 *
 * 被测资产与生产完全同源：
 * - core/data/src/main/resources/visual/visual-compiler.js
 * - core/data/src/main/resources/visual/visual-sdk.js
 *
 * 运行：`node tools/visual_compiler/run_tests.mjs`（Node ≥ 18；本机实测 v26.4.0）。
 *
 * 覆盖（任务书 T1 第 4 条测试矩阵的 JS 侧承载）：
 * - 合法场景编译（纯几何 / 全节点类型 / Q 提升 / SDK 弧拆分 / 标签放置 / 空组）；
 * - 非法形状拒（§3.4 每个错误码族至少一例，断言精确 code）；
 * - 种子确定性：同 (scene, metrics, expect) 两次编译输出**逐字节相同**
 *   （编译器结构上无随机/时钟；SDK random 只走注入的 PRNG 桩）；
 * - 超预算/超尺寸拒（节点 257 / 展开命令 4097 / 命中区 129 / 输出 >256 KiB 字符）；
 * - 上限场景（256 节点 / 4096 命令）在本机编译耗时（桌面代理值；设备 QuickJS 实测
 *   UNVERIFIED，归设备轨——断言只对"不超 Node 侧 100ms 量级"做粗校验并打印实测值）。
 *
 * 跨引擎口径：数字→字符串（Number::toString）与超越函数（sin/cos/tan）在 QuickJS 与 V8
 * 末位 ULP 可能不同，因此**跨引擎不做字节级金样断言**；字节级断言只对同一引擎内
 * （本 Node 进程内两次运行）与纯整数场景成立。设备上的确定性由仪器化用例
 * （QuickJsSceneCompilerInstrumentedTest）以"同输入两次 execute 输出逐字节相同"钉住。
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import vm from 'node:vm';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..', '..');
const compilerPath = path.join(
  repoRoot,
  'core/data/src/main/resources/visual/visual-compiler.js',
);
const sdkPath = path.join(
  repoRoot,
  'core/data/src/main/resources/visual/visual-sdk.js',
);

let passed = 0;
let failed = 0;
const failures = [];

function check(name, condition, detail) {
  if (condition) {
    passed += 1;
  } else {
    failed += 1;
    failures.push(`${name}${detail ? ` :: ${detail}` : ''}`);
  }
}

/** 新建干净 VM 上下文：注入 bootstrap 等价物（PRNG 桩），再求值给定资产。 */
function makeContext({ withSdk = false, prng = () => 0.5 } = {}) {
  const context = vm.createContext({
    __ktRandomNext: prng,
  });
  // bootstrap 的 PRNG 捕获行（镜像 QuickJsVisualExecutionEngine.BOOTSTRAP_JS 的
  // `const __ktRandom = __ktRandomNext;`；harness 不需要 Date 封闭/Math 冻结）。
  vm.runInContext('const __ktRandom = __ktRandomNext;\nvoid 0;', context);
  vm.runInContext(readFileSync(compilerPath, 'utf8'), context, {
    filename: 'visual-compiler.js',
  });
  if (withSdk) {
    vm.runInContext(readFileSync(sdkPath, 'utf8'), context, {
      filename: 'visual-sdk.js',
    });
  }
  return context;
}

function compile(context, scene, metrics = [], expect = { sceneRevision: 1, viewport: { widthDu: 360, heightDu: 240 } }) {
  return vm.runInContext(
    '__visualCompile(' +
      JSON.stringify(typeof scene === 'string' ? scene : JSON.stringify(scene)) + ',' +
      JSON.stringify(typeof metrics === 'string' ? metrics : JSON.stringify(metrics)) + ',' +
      JSON.stringify(JSON.stringify(expect)) +
      ')',
    context,
  );
}

function expectReject(name, scene, code, options) {
  const context = makeContext(options?.context);
  try {
    compile(context, scene, options?.metrics, options?.expect);
    check(name, false, 'expected rejection but compile succeeded');
  } catch (error) {
    const message = String(error && error.message);
    check(name, message.startsWith(code + ': '), `expected code ${code}, got: ${message.slice(0, 120)}`);
  }
}

function viewportScene(viewport, extra) {
  return {
    schemaVersion: 1,
    sceneId: 'scene-1',
    revision: 1,
    viewBox: { x: 0, y: 0, width: viewport?.widthDu ?? 360, height: viewport?.heightDu ?? 240 },
    paths: [],
    root: null,
    controls: [],
    hitRegions: [],
    ...(extra ?? {}),
  };
}

// ---------------------------------------------------------------- 合法场景

function testLegalScenes() {
  const context = makeContext();

  // 1) 空场景（root null）→ 零 ops；与隔离门夹具 pureGeometryRequest 同构。
  {
    const out = JSON.parse(compile(context, viewportScene()));
    check('legal/empty-root drawSchemaVersion', out.drawSchemaVersion === 1);
    check(
      'legal/empty-root viewBox echoes viewport as w/h',
      out.viewBox.x === 0 && out.viewBox.y === 0 && out.viewBox.w === 360 && out.viewBox.h === 240,
    );
    check('legal/empty-root no ops', out.ops.length === 0);
    check(
      'legal/empty-root stats',
      out.stats.nodes === 0 && out.stats.pathCommands === 0 && out.stats.labels === 0 && out.stats.ops === 0,
    );
  }

  // 2) 空组（隔离门夹具形状：{type:'group', children:[]}）→ 无 save/restore（无可开状态）。
  {
    const scene = viewportScene(null, { root: { type: 'group', children: [] } });
    const out = JSON.parse(compile(context, scene));
    check('legal/empty-group zero ops', out.ops.length === 0);
    check('legal/empty-group nodes counted', out.stats.nodes === 1);
  }

  // 3) 纯几何：polyline 定义 + path 节点（fill+stroke）。
  {
    const scene = viewportScene(null, {
      paths: [{ id: 'p1', commands: [['M', 0, 0], ['L', 100, 0], ['L', 100, 50], ['Z']] }],
      root: {
        type: 'group',
        matrix: [2, 0, 0, 2, 10, 20],
        children: [
          { type: 'path', pathId: 'p1', fill: '#ff0000', stroke: { width: 2 } },
        ],
      },
    });
    const out = JSON.parse(compile(context, scene));
    check('legal/geometry op sequence', JSON.stringify(out.ops) === JSON.stringify([
      { op: 'save' },
      { op: 'concat', m: [2, 0, 0, 2, 10, 20] },
      { op: 'path', path: [['M', 0, 0], ['L', 100, 0], ['L', 100, 50], ['Z']], fill: '#ff0000', stroke: { width: 2, cap: 'butt', join: 'miter', miterLimit: 4, dash: null }, alpha: 1 },
      { op: 'restore' },
    ]));
    check('legal/geometry expanded commands', out.stats.pathCommands === 4);
    check('legal/geometry nodes', out.stats.nodes === 2);
  }

  // 4) Q 提升为 C（精确变换）：["Q",cx,cy,x,y] → ["C",(P0+2Q)/3,(P2+2Q)/3,...]。
  {
    const scene = viewportScene(null, {
      paths: [{ id: 'q', commands: [['M', 2, 0], ['Q', 7, 8, 3, 0]] }],
      root: { type: 'path', pathId: 'q', fill: '#000000ff' },
    });
    const out = JSON.parse(compile(context, scene));
    const c = out.ops[0].path[1];
    check('legal/q-elevation becomes C', c[0] === 'C');
    const expectedC1x = (2 + 2 * 7) / 3;
    const expectedC2x = (3 + 2 * 7) / 3;
    check(
      'legal/q-elevation control points',
      c[1] === expectedC1x && c[2] === (0 + 2 * 8) / 3 && c[3] === expectedC2x && c[4] === (0 + 2 * 8) / 3 && c[5] === 3 && c[6] === 0,
      `got ${JSON.stringify(c)}`,
    );
  }

  // 5) 全节点类型：group(clip + matrix + opacity) > group > path + label（对齐/偏移）。
  {
    const metrics = [{
      contentId: 'c1', widthDu: 10, heightDu: 12, baselineDu: 9, fontSizeSp: 16, digest: 'd',
    }];
    const scene = viewportScene(null, {
      background: '#ffffff',
      paths: [{ id: 'dot', commands: [['M', 0, 0], ['C', 1, 1, 2, 1, 3, 0]] }],
      root: {
        type: 'group',
        matrix: [1, 0, 0, 1, 5, 5],
        opacity: 0.5,
        clip: { path: [['M', 0, 0], ['L', 10, 0], ['L', 10, 10], ['Z']], rule: 'evenodd' },
        children: [
          { type: 'path', pathId: 'dot', fill: '#ff0000', stroke: { width: 1 } },
          {
            type: 'label',
            contentId: 'c1',
            anchor: { x: 100, y: 60 },
            offset: { x: 2, y: 3 },
            hAlign: 'center',
            vAlign: 'middle',
            color: '#26354a',
          },
        ],
      },
      hitRegions: [{ id: 'h1', path: [['M', 0, 0], ['L', 5, 0], ['L', 5, 5], ['Z']], rule: 'nonzero' }],
    });
    const out = JSON.parse(compile(context, scene, metrics));
    // clip 以组合后坐标烘焙（含本组矩阵），emit 在 concat 之前（回放时恒等变换下生效）。
    check('legal/full clip baked composed', JSON.stringify(out.ops[1].path) === JSON.stringify([
      ['M', 5, 5], ['L', 15, 5], ['L', 15, 15], ['Z'],
    ]));
    check('legal/full clip rule echoed', out.ops[1].rule === 'evenodd');
    check('legal/full concat is local matrix', JSON.stringify(out.ops[2].m) === JSON.stringify([1, 0, 0, 1, 5, 5]));
    const label = out.ops[4];
    check('legal/full op is label', label.op === 'label');
    // 投影：anchor(100,60) 过矩阵(+5,+5) → (105,65)；offset(+2,+3) → (107,68)；
    // x = 107 − 10×0.5 = 102；y = 68 − 12×0.5 + 9 = 71。
    check('legal/full label placement', label.x === 102 && label.y === 71, `got ${label.x},${label.y}`);
    check('legal/full label alpha composed', label.alpha === 0.5);
    check('legal/full hitRegions echoed', out.hitRegions.length === 1 && out.hitRegions[0].id === 'h1');
    // ops: save + clip + concat + path + label + restore = 6；nodes: group + path + label = 3。
    check('legal/full stats', out.stats.nodes === 3 && out.stats.labels === 1 && out.stats.ops === 6, JSON.stringify(out.stats));
    check('legal/full pathCommands includes clip+hit', out.stats.pathCommands === 2 + 4 + 4, `got ${out.stats.pathCommands}`);
  }

  // 6) 标签各向对齐的框定位（left/top 与 right/bottom 两端点）。
  {
    const metrics = [{ contentId: 'c', widthDu: 10, heightDu: 12, baselineDu: 9, fontSizeSp: 16, digest: 'd' }];
    const mk = (h, v) => viewportScene(null, {
      root: { type: 'label', contentId: 'c', anchor: { x: 0, y: 0 }, hAlign: h, vAlign: v, color: '#000000' },
    });
    const left = JSON.parse(compile(context, mk('left', 'top'), metrics)).ops[0];
    const right = JSON.parse(compile(context, mk('right', 'bottom'), metrics)).ops[0];
    check('legal/label left/top', left.x === 0 && left.y === 9, `got ${left.x},${left.y}`);
    check('legal/label right/bottom', right.x === -10 && right.y === 0 - 12 + 9, `got ${right.x},${right.y}`);
  }

  // 7) 不可见组：整树照常校验与计数（contentId 悬空仍拒绝），但不发射 op、不计标签。
  {
    const scene = viewportScene(null, {
      root: { type: 'group', visible: false, children: [{ type: 'label', contentId: 'ghost-ok', anchor: { x: 1, y: 1 }, hAlign: 'left', vAlign: 'top', color: '#000000' }] },
    });
    const metrics = [{ contentId: 'ghost-ok', widthDu: 10, heightDu: 12, baselineDu: 9, fontSizeSp: 16, digest: 'd' }];
    const out = JSON.parse(compile(context, scene, metrics));
    check('legal/invisible-group skipped-but-validated', out.ops.length === 0 && out.stats.nodes === 2 && out.stats.labels === 0, JSON.stringify(out.stats));
    const dangling = viewportScene(null, {
      root: { type: 'group', visible: false, children: [{ type: 'label', contentId: 'ghost-bad', anchor: { x: 1, y: 1 }, hAlign: 'left', vAlign: 'top', color: '#000000' }] },
    });
    let rejected = false;
    try {
      compile(context, dangling, metrics);
    } catch (error) {
      rejected = String(error.message).startsWith('SCENE_REF_CONTENT:');
    }
    check('legal/invisible-group still validates refs', rejected);
  }
}

// ---------------------------------------------------------------- 确定性

function testDeterminism() {
  const context = makeContext();
  const metrics = [{ contentId: 'c1', widthDu: 10.5, heightDu: 12.25, baselineDu: 9.125, fontSizeSp: 16, digest: 'd' }];
  const scene = viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0.1, 0.2], ['Q', 3.3, 4.4, 5.5, 6.6], ['Z']] }],
    root: {
      type: 'group',
      matrix: [1.5, 0.25, -0.5, 2, 7, 8],
      children: [{ type: 'label', contentId: 'c1', anchor: { x: 12.5, y: 8.25 }, hAlign: 'center', vAlign: 'middle', color: '#112233' }],
    },
  });
  const first = compile(context, scene, metrics);
  const second = compile(context, scene, metrics);
  check('determinism/same input byte-identical', first === second);

  // 新上下文重编译（每事务新上下文等价形状）同样逐字节相同。
  const fresh = makeContext();
  check('determinism/fresh context byte-identical', compile(fresh, scene, metrics) === first);

  // SDK PRNG 注入不影响编译输出（编译器无随机面）。
  const withSdk = makeContext({ withSdk: true, prng: () => 0.123456 });
  check('determinism/sdk-prng-does-not-leak-into-compile', compile(withSdk, scene, metrics) === first);
}

// ---------------------------------------------------------------- 非法形状

function testIllegalShapes() {
  const context = makeContext();
  const metrics = [{ contentId: 'c1', widthDu: 10, heightDu: 12, baselineDu: 9, fontSizeSp: 16, digest: 'd' }];

  // SCENE_PARSE_*
  expectReject('reject/parse/not-json', 'not json at all', 'SCENE_PARSE_NOT_JSON');
  expectReject('reject/parse/trailing', '{"schemaVersion":1} {}', 'SCENE_PARSE_TRAILING');
  expectReject('reject/parse/dup-key', '{"a":1,"a":2}', 'SCENE_PARSE_DUP_KEY');
  expectReject('reject/parse/poisoned-key', '{"__proto__":1}', 'SCENE_PARSE_POISONED_KEY');
  expectReject('reject/parse/poisoned-key-constructor', '{"constructor":1}', 'SCENE_PARSE_POISONED_KEY');
  expectReject('reject/parse/non-finite', '{"a":1e999}', 'SCENE_PARSE_NON_FINITE');
  expectReject('reject/parse/depth', JSON.stringify({ a: JSON.parse('['.repeat(65) + ']'.repeat(65)) }), 'SCENE_PARSE_DEPTH');
  // 嵌套恰 64 层（场景对象 1 + 63 层数组）不触发解析深度门（负控）：
  // 解析通过后按 schema 拒绝未知字段——证明拒因是 schema 而非深度。
  {
    let deep = [];
    for (let i = 0; i < 62; i += 1) {
      deep = [deep];
    }
    let caught = '';
    try {
      compile(context, { ...viewportScene(), deep63: deep });
    } catch (error) {
      caught = String(error.message).slice(0, 40);
    }
    check('reject/parse/depth-64-negative-control', caught.startsWith('SCENE_FIELD_UNKNOWN'), `got ${caught}`);
  }

  // SCENE_FIELD_*
  expectReject('reject/field/schema-version', viewportScene(null, { schemaVersion: 2 }), 'SCENE_FIELD_SCHEMA_VERSION');
  expectReject('reject/field/unknown', viewportScene(null, { surprise: 1 }), 'SCENE_FIELD_UNKNOWN');
  expectReject('reject/field/viewport-mismatch', viewportScene({ widthDu: 361 }), 'SCENE_FIELD_VIEWPORT');
  expectReject('reject/field/revision', viewportScene(null, { revision: 2 }), 'SCENE_FIELD_REVISION');
  expectReject('reject/field/controls-non-empty', viewportScene(null, { controls: [{ type: 'button' }] }), 'SCENE_FIELD_CONTROLS');
  expectReject('reject/field/form-unavailable', viewportScene(null, { form: { formId: 'f' } }), 'SCENE_FIELD_UNAVAILABLE');
  expectReject('reject/field/accessibility-unavailable', viewportScene(null, { accessibility: {} }), 'SCENE_FIELD_UNAVAILABLE');
  expectReject('reject/field/dup-path-id', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0]] }, { id: 'p', commands: [['M', 1, 1]] }],
  }), 'SCENE_FIELD_DUP_ID');

  // SCENE_NODE_*
  expectReject('reject/node/unknown-type', viewportScene(null, { root: { type: 'rect' } }), 'SCENE_NODE_TYPE');
  expectReject('reject/node/dup-id', viewportScene(null, {
    root: {
      type: 'group',
      children: [
        { type: 'group', id: 'same', children: [] },
        { type: 'group', id: 'same', children: [] },
      ],
    },
  }), 'SCENE_NODE_DUP_ID');

  // 258 节点（根组 + 257 个兄弟 label）→ 超预算。
  {
    const children = [];
    for (let i = 0; i < 257; i += 1) {
      children.push({ type: 'label', contentId: 'c1', anchor: { x: i, y: 0 }, hAlign: 'left', vAlign: 'top', color: '#000000' });
    }
    expectReject('reject/node/count-257', viewportScene(null, { root: { type: 'group', children } }), 'SCENE_NODE_COUNT', { metrics });
  }
  // 恰 256 节点合法（负控：根组 + 255 个 label）。
  {
    const children = [];
    for (let i = 0; i < 255; i += 1) {
      children.push({ type: 'label', contentId: 'c1', anchor: { x: i, y: 0 }, hAlign: 'left', vAlign: 'top', color: '#000000' });
    }
    const out = JSON.parse(compile(context, viewportScene(null, { root: { type: 'group', children } }), metrics));
    check('reject/node/count-256-negative-control', out.stats.nodes === 256);
  }
  // 树深 9（9 层嵌套 group，最里层再放 label ⇒ 第 9 个节点超深）。
  {
    let node = { type: 'label', contentId: 'c1', anchor: { x: 0, y: 0 }, hAlign: 'left', vAlign: 'top', color: '#000000' };
    for (let i = 0; i < 9; i += 1) {
      node = { type: 'group', children: [node] };
    }
    expectReject('reject/node/depth-9', viewportScene(null, { root: node }), 'SCENE_NODE_DEPTH', { metrics });
  }
  // 树深 8 恰好合法（负控：8 层 group + 最里层 label 共 9 个节点，label 在深度 9？——
  // 深度口径：根节点深度 1；8 层 group 让最里层 group 处于深度 8，label 作为其 child 处于
  // 深度 9 → 超深。负控改为 7 层 group + label（label 深度 8）。
  {
    let node = { type: 'label', contentId: 'c1', anchor: { x: 0, y: 0 }, hAlign: 'left', vAlign: 'top', color: '#000000' };
    for (let i = 0; i < 7; i += 1) {
      node = { type: 'group', children: [node] };
    }
    const out = JSON.parse(compile(context, viewportScene(null, { root: node }), metrics));
    check('reject/node/depth-8-negative-control', out.stats.labels === 1);
  }

  // SCENE_PATH_*
  expectReject('reject/path/unknown-cmd', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0], ['A', 1, 2, 3]] }],
    root: { type: 'path', pathId: 'p', fill: '#000000' },
  }), 'SCENE_PATH_UNKNOWN_CMD');
  expectReject('reject/path/not-m-start', viewportScene(null, {
    paths: [{ id: 'p', commands: [['L', 1, 1]] }],
    root: { type: 'path', pathId: 'p', fill: '#000000' },
  }), 'SCENE_PATH_NOT_M_START');
  expectReject('reject/path/coord-bounds', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 100001, 0]] }],
    root: { type: 'path', pathId: 'p', fill: '#000000' },
  }), 'SCENE_PATH_COORD');
  expectReject('reject/path/dangling-ref', viewportScene(null, {
    root: { type: 'path', pathId: 'ghost', fill: '#000000' },
  }), 'SCENE_PATH_REF');
  // 展开回放超预算：定义总数恰 4096（2×2048），被 3 个 path 节点引用 → 展开 6144 > 4096
  //（节点只有 4 个，先撞命令预算而非节点预算——证明两条预算独立生效）。
  {
    const longCommands = [['M', 0, 0]];
    for (let i = 1; i < 2048; i += 1) {
      longCommands.push(['L', i, 0]);
    }
    expectReject('reject/path/expanded-over-budget', viewportScene(null, {
      paths: [
        { id: 'a', commands: longCommands },
        { id: 'b', commands: longCommands },
      ],
      root: {
        type: 'group',
        children: [
          { type: 'path', pathId: 'a', fill: '#000000' },
          { type: 'path', pathId: 'b', fill: '#000000' },
          { type: 'path', pathId: 'a', fill: '#000000' },
        ],
      },
    }), 'SCENE_PATH_COUNT', { metrics: [] });
  }

  // SCENE_STYLE_*
  expectReject('reject/style/bad-color', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0]] }],
    root: { type: 'path', pathId: 'p', fill: 'red' },
  }), 'SCENE_STYLE_COLOR');
  expectReject('reject/style/bad-cap', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0]] }],
    root: { type: 'path', pathId: 'p', stroke: { width: 1, cap: 'wiggly' } },
  }), 'SCENE_STYLE_CAP');
  expectReject('reject/style/no-paint', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0]] }],
    root: { type: 'path', pathId: 'p' },
  }), 'SCENE_STYLE_NO_PAINT');
  expectReject('reject/style/miter-limit', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0]] }],
    root: { type: 'path', pathId: 'p', stroke: { width: 1, miterLimit: 0.5 } },
  }), 'SCENE_STYLE_MITER_LIMIT');

  // KD-78 ①③ 生成门（批次 29 T5）：回放侧 painter 的 fail-closed 面在生成侧前置，码面与
  // painter 同词（PAINTER_STROKE_WITHOUT_FILL / PAINTER_DASH_ODD）——"合法生成、绘制时被拒"
  // 的预算浪费路径在两道生成门即被拒。
  expectReject('reject/style/stroke-without-fill', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0], ['L', 10, 0]] }],
    root: { type: 'path', pathId: 'p', stroke: { width: 1 } },
  }), 'PAINTER_STROKE_WITHOUT_FILL');
  expectReject('reject/style/dash-odd', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0], ['L', 10, 0]] }],
    root: { type: 'path', pathId: 'p', fill: '#000000', stroke: { width: 1, dash: [2, 4, 2] } },
  }), 'PAINTER_DASH_ODD');
  expectReject('reject/style/dash-single-entry', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0], ['L', 10, 0]] }],
    root: { type: 'path', pathId: 'p', fill: '#000000', stroke: { width: 1, dash: [2] } },
  }), 'PAINTER_DASH_ODD');
  expectReject('reject/style/dash-empty', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0], ['L', 10, 0]] }],
    root: { type: 'path', pathId: 'p', fill: '#000000', stroke: { width: 1, dash: [] } },
  }), 'PAINTER_DASH_ODD');
  // 负控：偶数 ≥2 的 dash 与 fill+stroke 组合仍合法（门不是"一律拒 dash"）。
  {
    const out = JSON.parse(compile(context, viewportScene(null, {
      paths: [{ id: 'p', commands: [['M', 0, 0], ['L', 10, 0]] }],
      root: { type: 'path', pathId: 'p', fill: '#000000', stroke: { width: 1, dash: [2, 2, 4, 4] } },
    })));
    check(
      'legal/style/even-dash-compiles',
      out.ops.length === 1 && JSON.stringify(out.ops[0].stroke.dash) === JSON.stringify([2, 2, 4, 4]),
    );
  }

  // 原型链逃逸负向（批次 27 复核修复）：查表改 null-proto、allowed 改自有键判定后，
  // 继承键名（constructor/toString/valueOf）不再能绕过任何 fail-closed 门。修复前
  // 五例全部 COMPILED（cap/join/rule 还把原串发射进 DrawOps；hAlign 错归
  // SCENE_FIELD_BOUNDS）——本组用例钉住不回退。
  expectReject('reject/style/cap-constructor', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0]] }],
    root: { type: 'path', pathId: 'p', stroke: { width: 1, cap: 'constructor' } },
  }), 'SCENE_STYLE_CAP');
  expectReject('reject/style/join-toString', viewportScene(null, {
    paths: [{ id: 'p', commands: [['M', 0, 0]] }],
    root: { type: 'path', pathId: 'p', stroke: { width: 1, join: 'toString' } },
  }), 'SCENE_STYLE_JOIN');
  expectReject('reject/style/hitrule-valueOf', viewportScene(null, {
    hitRegions: [{ id: 'h1', path: [['M', 0, 0]], rule: 'valueOf' }],
  }), 'SCENE_STYLE_RULE');
  expectReject('reject/field/own-key-toString', { ...viewportScene(), toString: 'polluted' }, 'SCENE_FIELD_UNKNOWN');
  expectReject('reject/field/halign-toString', viewportScene(null, {
    root: { type: 'label', contentId: 'c1', anchor: { x: 10, y: 10 }, hAlign: 'toString', vAlign: 'top', color: '#000000' },
  }), 'SCENE_FIELD_TYPE', { metrics });

  // SCENE_REF_*
  expectReject('reject/ref/content-not-frozen', viewportScene(null, {
    root: { type: 'label', contentId: 'ghost', anchor: { x: 0, y: 0 }, hAlign: 'left', vAlign: 'top', color: '#000000' },
  }), 'SCENE_REF_CONTENT', { metrics });

  // SCENE_MATRIX_*
  expectReject('reject/matrix/not-finite', viewportScene(null, {
    root: { type: 'group', matrix: [1, 0, 0, 1, NaN, 0], children: [] },
  }), 'SCENE_MATRIX_NOT_FINITE');
  expectReject('reject/matrix/singular', viewportScene(null, {
    root: { type: 'group', matrix: [1, 0, 0, 1e-13, 0, 0], children: [] },
  }), 'SCENE_MATRIX_SINGULAR');
  expectReject('reject/matrix/composed-bounds', viewportScene(null, {
    root: {
      type: 'group',
      matrix: [100000, 0, 0, 100000, 0, 0],
      children: [{ type: 'group', matrix: [100000, 0, 0, 100000, 0, 0], children: [] }],
    },
  }), 'SCENE_MATRIX_BOUNDS');

  // SCENE_BUDGET_*
  {
    const hit = [];
    for (let i = 0; i < 129; i += 1) {
      hit.push({ id: `h${i}`, path: [['M', 0, 0]], rule: 'nonzero' });
    }
    expectReject('reject/budget/hit-regions-129', viewportScene(null, { hitRegions: hit }), 'SCENE_BUDGET_HIT_REGIONS');
  }
  {
    // 输出 > 256 KiB：4096 条 17 位数字 C 命令（定义内联到一个 path 节点）。
    const commands = [['M', 0, 0]];
    for (let i = 0; i < 4095; i += 1) {
      commands.push(['C', -12345.678901234567, -12345.678901234567, -12345.678901234567, -12345.678901234567, -12345.678901234567, -12345.678901234567]);
    }
    expectReject('reject/budget/output-over-256kib', viewportScene(null, {
      paths: [{ id: 'big', commands }],
      root: { type: 'path', pathId: 'big', fill: '#000000' },
    }), 'SCENE_BUDGET_OPS', { metrics: [] });
  }
}

// ---------------------------------------------------------------- SDK

function testSdk() {
  const prngCalls = [];
  const context = makeContext({
    withSdk: true,
    prng: () => {
      prngCalls.push(1);
      return 0.42;
    },
  });
  const sdk = vm.runInContext('visualSdk', context);

  // circle：4 段 C + Z，端点与 κ 偏移。
  {
    const cmds = sdk.circle(10, 20, 30);
    check('sdk/circle shape', cmds.length === 6 && cmds[0][0] === 'M' && cmds[5][0] === 'Z');
    check('sdk/circle start point', cmds[0][1] === 40 && cmds[0][2] === 20);
    const kappa = (4 / 3) * Math.tan(Math.PI / 8);
    check('sdk/circle kappa offset', Math.abs(cmds[1][3] - (10 + 30 * kappa)) < 1e-9, `got ${cmds[1][3]}`);
  }
  // arc：>90° sweep 拆多段；负 sweep（顺时针）方向保持。
  {
    const cmds = sdk.arc(0, 0, 10, 0, Math.PI); // 180° → 2 段
    check('sdk/arc 180deg two segments', cmds.length === 3 && cmds[0][0] === 'M');
    const cmds270 = sdk.arc(0, 0, 10, 0, Math.PI * 1.5);
    check('sdk/arc 270deg three segments', cmds270.length === 4);
    const cw = sdk.arc(0, 0, 10, 0, -Math.PI / 2);
    check('sdk/arc clockwise single segment', cw.length === 2);
    check(
      'sdk/arc clockwise endpoint',
      Math.abs(cw[1][5] - 10 * Math.cos(-Math.PI / 2)) < 1e-9 && Math.abs(cw[1][6] - 10 * Math.sin(-Math.PI / 2)) < 1e-9,
    );
  }
  // arrow：杆（M+L）+ V 形头（M barbA → L 端点 → L barbB）= 5 命令；退化长度拒绝。
  {
    const cmds = sdk.arrow(0, 0, 100, 0);
    check('sdk/arrow shape', cmds.length === 5 && cmds[0][0] === 'M' && cmds[1][1] === 100 && cmds[2][0] === 'M', JSON.stringify(cmds));
    check('sdk/arrow head touches tip', cmds[3][1] === 100 && cmds[3][2] === 0);
    let threw = false;
    try { sdk.arrow(5, 5, 5, 5); } catch (error) { threw = String(error.message).startsWith('SCENE_SDK_ARG:'); }
    check('sdk/arrow degenerate rejected', threw);
  }
  // axis：主线 + 端箭头 + 内部刻度。
  {
    const cmds = sdk.axis({ x0: 0, y0: 0, x1: 90, y1: 0, ticks: 3, tickLength: 6 });
    const subpaths = cmds.filter((c) => c[0] === 'M').length;
    check('sdk/axis subpaths (line+head barbs+ticks)', subpaths === 1 + 1 + 2, `got ${subpaths}`);
  }
  // polyline：采样折线。
  {
    const cmds = sdk.polyline([[0, 0], [10, 5], [20, 0]]);
    check('sdk/polyline shape', cmds.length === 3 && cmds[0][0] === 'M' && cmds[1][0] === 'L' && cmds[2][0] === 'L');
  }
  // random：走注入 PRNG。
  {
    const value = sdk.random();
    check('sdk/random uses injected prng', value === 0.42 && prngCalls.length === 1);
  }
  // 非有限参数 fail-closed。
  {
    let threw = false;
    try { sdk.circle(0, 0, NaN); } catch (error) { threw = String(error.message).startsWith('SCENE_SDK_ARG:'); }
    check('sdk/non-finite arg rejected', threw);
  }
  // SDK 命名空间冻结（严格模式赋值必须抛 TypeError）。
  {
    let froze = false;
    try {
      vm.runInContext('"use strict"; visualSdk.circle = function () { return []; };', context);
    } catch (_) {
      froze = true;
    }
    check('sdk/namespace frozen', froze);
  }
  // SDK 产出可直接作为 paths[] 定义被编译器接受（弧拆分端到端）。
  {
    const scene = viewportScene(null, {
      paths: [{ id: 'c', commands: sdk.circle(50, 50, 40) }],
      root: { type: 'path', pathId: 'c', fill: '#336699' },
    });
    const out = JSON.parse(compile(context, scene));
    check('sdk/circle end-to-end compiles', out.ops.length === 1 && out.ops[0].path.length === 6);
    check('sdk/circle end-to-end budget', out.stats.pathCommands === 6);
  }
}

// ---------------------------------------------------------------- 预算上限场景（时间）

function testUpperBoundTiming() {
  // 256 节点 / 4096 展开命令的上限场景：根组 + 15 组 × 每组 16 个 path（240 path），
  // 每个引用同一条 17 命令定义 → 展开 240×17 = 4080 ≤ 4096（定义总数与展开回放两条
  // 预算同时成立，见编译器头注）。桌面 QuickJS(Wasm) 每帧 7.5–35ms 外推（R1 §5.2）；
  // 此处只对 Node 侧做量级观测，设备实测 UNVERIFIED（归设备轨）。
  const commands = [['M', 0, 0]];
  for (let i = 0; i < 16; i += 1) {
    commands.push(['L', i, i % 16]);
  }
  const children = [];
  for (let g = 0; g < 15; g += 1) {
    const kids = [];
    for (let p = 0; p < 16; p += 1) {
      kids.push({ type: 'path', pathId: 'p', fill: '#123456' });
    }
    children.push({ type: 'group', children: kids });
  }
  const scene = viewportScene(null, {
    paths: [{ id: 'p', commands }],
    root: { type: 'group', children },
  });
  const context = makeContext();
  const startedAt = process.hrtime.bigint();
  const out = JSON.parse(compile(context, scene));
  const elapsedMs = Number(process.hrtime.bigint() - startedAt) / 1e6;
  check('budget/upper-bound compiles', out.stats.nodes === 256 && out.stats.pathCommands === 4080, JSON.stringify(out.stats));
  // 组无矩阵/clip → 不发射 save/restore（无可开状态）；ops 恰为 240 个 path op。
  check('budget/upper-bound ops', out.ops.length === 240, `got ${out.ops.length}`);
  console.log(`[timing] upper-bound scene (256 nodes / 4096 commands): ${elapsedMs.toFixed(1)} ms on Node ${process.version}`);
  check('budget/upper-bound under 100ms on Node (desktop proxy)', elapsedMs < 100, `${elapsedMs.toFixed(1)} ms`);
}

// ---------------------------------------------------------------- 主流程

try {
  testLegalScenes();
  testDeterminism();
  testIllegalShapes();
  testSdk();
  testUpperBoundTiming();
} catch (error) {
  // 让夹具自身的意外异常也计入失败并继续汇总退出码。
  failed += 1;
  failures.push(`harness crashed: ${error && error.stack ? error.stack.split('\n').slice(0, 4).join(' | ') : String(error)}`);
}

console.log(`\n${passed} passed, ${failed} failed`);
if (failures.length > 0) {
  for (const failure of failures) {
    console.log(`  FAIL ${failure}`);
  }
  process.exit(1);
}
process.exit(0);
