/*
 * visualSdk——prepare/render 上下文的纯函数图形原语（批次 27 T1；R1 §3.3）。
 *
 * 契约依据：`docs/current/visual-generation-contract.md` §3.2（"SDK 在同一全局上下文提供
 * 纯函数图形帮助方法"）、§5（"圆、弧、箭头、轴由 SDK 生成基础路径，弧拆为每段不超过 90°
 * 的三次曲线，误差算法随 SDK 版本固定"）。求值顺序：bootstrap → 本资产 → 用户源码
 * （`QuickJsVisualExecutionEngine` 注入）。无 IO、无网络、无时钟；随机只走
 * `visualSdk.random()`（bootstrap 捕获的宿主注入 PRNG，每阶段从 input.seed 重放）。
 *
 * 命名空间 `visualSdk` 冻结；每个原语对非有限/越界输入 fail-closed 抛
 * `SceneSdkError`（code `SCENE_SDK_*` + 有界 message；引擎经分类器折 INVALID_SCENE）。
 *
 * 段数公式与误差常数（随 SDK 版本固定，R1 §5.3 数值复核）：
 *   κ = 4/3·tan(θ/4)；圆 = 4 段（κ_circle = 4/3·tan(π/8) ≈ 0.5522847498307933）；
 *   弧 = ceil(|sweep|/90°) 段，每段 κ_seg = 4/3·tan(Δ/4)；90° 段最大径向误差
 *   2.7253e-4·r（版本化文档值，非缺陷）。sweep 归一化到 (−2π, 2π]。
 *
 * V1 明确不设 ellipse（group 矩阵缩放 circle 即得）/rect（M+3L+Z 内联即可）/
 * 主题色 token/避让/文本度量糖——每项缺席都无已发生的失败（R1 §3.3 逆向裁剪）。
 *
 * 算法来源（数学事实，不复制代码体）：κ 分段公式（kurbo arc.rs:77-86 旁证）。
 * 本项目原创实现。注意：本资产产出的坐标仍须通过编译器/宿主的 ±100000 边界复验——
 * SDK 只做参数级 fail-closed，几何越界由 SceneV1 编译器统一拒绝。
 */
(function () {
  'use strict';

  var ObjectFreeze = Object.freeze;
  var NumberIsFinite = Number.isFinite;
  var NumberIsInteger = Number.isInteger;
  var MathAbs = Math.abs;
  var MathCos = Math.cos;
  var MathSin = Math.sin;
  var MathTan = Math.tan;
  var MathCeil = Math.ceil;
  var MathHypot = Math.hypot;
  var MathPI = Math.PI;

  // bootstrap 必须先于本资产求值（`const __ktRandom = __ktRandomNext;`）。
  // 值捕获：程序此后重绑 globalThis.__ktRandomNext 不影响本命名空间。
  if (typeof __ktRandom !== 'function') {
    throw new Error('SCENE_SDK_BOOTSTRAP: visualSdk requires the bootstrap PRNG binding (__ktRandom)');
  }
  var ktRandom = __ktRandom;

  var MAX_COORD = 100000; // 与 SceneV1 编译器/宿主预算同值（契约 §4"坐标绝对值 100000"）
  var TAU = 2 * MathPI;
  var QUARTER = MathPI / 2;

  var KAPPA_CIRCLE = (4 / 3) * MathTan(MathPI / 8); // ≈ 0.5522847498307933（版本化常数）

  function SceneSdkError(message) {
    var error = new Error('SCENE_SDK_ARG: ' + String(message).slice(0, 180));
    error.name = 'SceneSdkError';
    return error;
  }

  function finiteNumber(value, name) {
    if (typeof value !== 'number' || !NumberIsFinite(value)) {
      throw SceneSdkError(name + ' must be a finite number');
    }
    return value;
  }

  function bounded(value, name, bound) {
    var n = finiteNumber(value, name);
    if (n > bound || n < -bound) {
      throw SceneSdkError(name + ' is outside the ±' + bound + ' bound');
    }
    return n;
  }

  function requirePointPair(x, y, name) {
    return [bounded(x, name + '.x', MAX_COORD), bounded(y, name + '.y', MAX_COORD)];
  }

  var visualSdk = {
    /**
     * 圆：4 段三次曲线 + Z（端点 (cx+r,cy) 起，逆时针）。r ∈ (0, ±100000]。
     */
    circle: function (cx, cy, r) {
      cx = bounded(cx, 'cx', MAX_COORD);
      cy = bounded(cy, 'cy', MAX_COORD);
      r = finiteNumber(r, 'r');
      if (r <= 0 || r > MAX_COORD) {
        throw SceneSdkError('r must be within (0, ' + MAX_COORD + ']');
      }
      var k = r * KAPPA_CIRCLE;
      return [
        ['M', cx + r, cy],
        ['C', cx + r, cy + k, cx + k, cy + r, cx, cy + r],
        ['C', cx - k, cy + r, cx - r, cy + k, cx - r, cy],
        ['C', cx - r, cy - k, cx - k, cy - r, cx, cy - r],
        ['C', cx + k, cy - r, cx + r, cy - k, cx + r, cy],
        ['Z'],
      ];
    },

    /**
     * 弧：n = ceil(|sweep|/90°) 段三次曲线（无 Z，端点不闭合）。sweep = a1 − a0
     * 归一化到 (−2π, 2π]；正 sweep 逆时针。整圆请用 circle()（arc(…, ±2π) 不闭合，
     * 且 2π 的浮点表示会让首尾端点有 1e-16 量级差）。
     */
    arc: function (cx, cy, r, a0, a1) {
      cx = bounded(cx, 'cx', MAX_COORD);
      cy = bounded(cy, 'cy', MAX_COORD);
      r = finiteNumber(r, 'r');
      if (r <= 0 || r > MAX_COORD) {
        throw SceneSdkError('r must be within (0, ' + MAX_COORD + ']');
      }
      a0 = finiteNumber(a0, 'a0');
      a1 = finiteNumber(a1, 'a1');
      var sweep = (a1 - a0) % TAU;
      if (sweep === 0 && a1 !== a0) {
        sweep = a1 > a0 ? TAU : -TAU;
      }
      var commands = [['M', cx + r * MathCos(a0), cy + r * MathSin(a0)]];
      if (sweep === 0) {
        return commands;
      }
      var segments = MathCeil(MathAbs(sweep) / QUARTER);
      var delta = sweep / segments;
      var kappa = (4 / 3) * MathTan(delta / 4) * r;
      for (var i = 0; i < segments; i += 1) {
        var theta0 = a0 + delta * i;
        var theta1 = a0 + delta * (i + 1);
        var x0 = cx + r * MathCos(theta0);
        var y0 = cy + r * MathSin(theta0);
        var x1 = cx + r * MathCos(theta1);
        var y1 = cy + r * MathSin(theta1);
        // dP/dθ = r·(−sinθ, cosθ)；切向控制偏移 = κ_seg·(−sinθ, cosθ)，Δ<0 时自动反向。
        commands.push([
          'C',
          x0 - kappa * MathSin(theta0),
          y0 + kappa * MathCos(theta0),
          x1 + kappa * MathSin(theta1),
          y1 - kappa * MathCos(theta1),
          x1,
          y1,
        ]);
      }
      return commands;
    },

    /**
     * 箭头：杆线（M 起点 → L 端点）+ 端点处的 V 形头（M barbA → L 端点 → L barbB）。
     * 默认头长 12 du、半角 30°；`o` 可选 {headLengthDu, headHalfAngleDeg} 覆盖
     * （headLengthDu ∈ (0, 1000]，headHalfAngleDeg ∈ (0°, 90°)）；默认值随 SDK 版本写死。
     */
    arrow: function (x1, y1, x2, y2, o) {
      var tail = requirePointPair(x1, y1, 'tail');
      var head = requirePointPair(x2, y2, 'head');
      var dx = head[0] - tail[0];
      var dy = head[1] - tail[1];
      var length = MathHypot(dx, dy);
      if (!(length > 1e-9)) {
        throw SceneSdkError('arrow tail and head must not coincide');
      }
      var headLength = 12;
      var halfAngleDeg = 30;
      if (o !== undefined && o !== null) {
        if (o.headLengthDu !== undefined) {
          headLength = finiteNumber(o.headLengthDu, 'o.headLengthDu');
          if (headLength <= 0 || headLength > 1000) {
            throw SceneSdkError('o.headLengthDu must be within (0, 1000]');
          }
        }
        if (o.headHalfAngleDeg !== undefined) {
          halfAngleDeg = finiteNumber(o.headHalfAngleDeg, 'o.headHalfAngleDeg');
          if (halfAngleDeg <= 0 || halfAngleDeg >= 90) {
            throw SceneSdkError('o.headHalfAngleDeg must be within (0, 90)');
          }
        }
      }
      var ux = dx / length;
      var uy = dy / length;
      var alpha = (halfAngleDeg * MathPI) / 180;
      var cosA = MathCos(alpha);
      var sinA = MathSin(alpha);
      // 倒回方向（−u）旋转 ±α 得两须方向。
      var barbAx = head[0] + headLength * (-ux * cosA - uy * sinA);
      var barbAy = head[1] + headLength * (ux * sinA - uy * cosA);
      var barbBx = head[0] + headLength * (-ux * cosA + uy * sinA);
      var barbBy = head[1] + headLength * (-ux * sinA - uy * cosA);
      return [
        ['M', tail[0], tail[1]],
        ['L', head[0], head[1]],
        ['M', barbAx, barbAy],
        ['L', head[0], head[1]],
        ['L', barbBx, barbBy],
      ];
    },

    /**
     * 轴：主线（M→L）+ 远端箭头 V + 内部刻度（i/ticks, i=1..ticks−1，垂直短线居中跨轴）。
     * `o = {x0, y0, x1, y1, ticks?, tickLength?}`；ticks ∈ 整数 [0, 64]（默认 0）、
     * tickLength ∈ (0, 100]（默认 6）。刻度数字仍由程序发 label（契约 §5）。
     */
    axis: function (o) {
      if (o === undefined || o === null || typeof o !== 'object') {
        throw SceneSdkError('axis expects an options object {x0, y0, x1, y1, ticks?, tickLength?}');
      }
      var start = requirePointPair(o.x0, o.y0, 'o');
      var end = requirePointPair(o.x1, o.y1, 'o');
      var dx = end[0] - start[0];
      var dy = end[1] - start[1];
      var length = MathHypot(dx, dy);
      if (!(length > 1e-9)) {
        throw SceneSdkError('axis endpoints must not coincide');
      }
      var ticks = 0;
      if (o.ticks !== undefined) {
        if (!NumberIsInteger(o.ticks) || o.ticks < 0 || o.ticks > 64) {
          throw SceneSdkError('o.ticks must be an integer within [0, 64]');
        }
        ticks = o.ticks;
      }
      var tickLength = 6;
      if (o.tickLength !== undefined) {
        tickLength = finiteNumber(o.tickLength, 'o.tickLength');
        if (tickLength <= 0 || tickLength > 100) {
          throw SceneSdkError('o.tickLength must be within (0, 100]');
        }
      }
      var ux = dx / length;
      var uy = dy / length;
      var nx = -uy;
      var ny = ux;
      var commands = [['M', start[0], start[1]], ['L', end[0], end[1]]];
      // 箭头 V（复用 arrow 的默认头形：12 du / 30°）。
      var alpha = (30 * MathPI) / 180;
      var cosA = MathCos(alpha);
      var sinA = MathSin(alpha);
      commands.push(
        ['M', end[0] + 12 * (-ux * cosA - uy * sinA), end[1] + 12 * (ux * sinA - uy * cosA)],
        ['L', end[0], end[1]],
        ['L', end[0] + 12 * (-ux * cosA + uy * sinA), end[1] + 12 * (-ux * sinA - uy * cosA)],
      );
      var halfTick = tickLength / 2;
      for (var i = 1; i < ticks; i += 1) {
        var t = i / ticks;
        var px = start[0] + dx * t;
        var py = start[1] + dy * t;
        commands.push(
          ['M', px - nx * halfTick, py - ny * halfTick],
          ['L', px + nx * halfTick, py + ny * halfTick],
        );
      }
      return commands;
    },

    /**
     * 采样折线：points 为 [x, y] 数对数组（≥2 个），产 M + (n−1)·L，不闭合。
     */
    polyline: function (points) {
      if (!Array.isArray(points) || points.length < 2) {
        throw SceneSdkError('polyline expects an array of at least two [x, y] pairs');
      }
      var commands = [];
      for (var i = 0; i < points.length; i += 1) {
        var pair = points[i];
        if (!Array.isArray(pair) || pair.length !== 2) {
          throw SceneSdkError('polyline point ' + i + ' must be an [x, y] pair');
        }
        var x = bounded(pair[0], 'points[' + i + '].x', MAX_COORD);
        var y = bounded(pair[1], 'points[' + i + '].y', MAX_COORD);
        commands.push(i === 0 ? ['M', x, y] : ['L', x, y]);
      }
      return commands;
    },

    /**
     * 确定性随机：bootstrap 捕获的宿主注入 PRNG（java.util.Random(seed)，每阶段从
     * input.seed 重放）。程序不得依赖本调用次数跨 prepare/render 一致——两阶段各自重放。
     */
    random: function () {
      return ktRandom();
    },
  };

  globalThis.visualSdk = ObjectFreeze(visualSdk);

  void 0;
})();
