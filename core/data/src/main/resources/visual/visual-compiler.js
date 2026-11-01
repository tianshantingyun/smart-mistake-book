/*
 * SceneV1 → DrawOps V1 编译器（批次 27 T1；唯一暴露面 `__visualCompile(sceneJson, metricsJson, expect)`）。
 *
 * 设计依据：`docs/research/2026-10-27-scenev1-compiler-design.md`（R1）§3；契约
 * `docs/current/visual-generation-contract.md` §3.2/§4/§5。运行环境：compile 第三上下文
 * （bootstrap 之后求值本资产，**无用户源码、无 SDK**），纯计算——无 IO、无网络、无时钟、
 * 无随机（同 (scene, metrics, expect) 输出逐字节相同）。
 *
 * 输入 SceneV1 键面（本批钉定；与既有设备夹具 `pureGeometryRequest` 同构）：
 *   { schemaVersion:1, sceneId, revision, viewBox:{x,y,width,height},
 *     background?, paths?:[{id, commands}], root?, controls?, hitRegions?,
 *     accessibility?, form?, fallbackMarkdown? }
 *   - 节点 `{type:'group'|'path'|'label', id??, ...}`；group: matrix?/opacity?/visible?/
 *     clip?/children?；path: pathId/fill?/stroke?；label: contentId/anchor/offset?/
 *     hAlign/vAlign/rotationDeg?/color/order?。
 *   - `accessibility`/`form` 在 V1 只接受缺省或 null（未启用能力在解析层拒绝，契约 §3.2）。
 *   - 未知字段一律 SCENE_FIELD_UNKNOWN 拒绝（契约 §4"严格拒绝未知字段"）。
 *   - `visible:false` 的组：整棵子树**照常校验与计数**（未知节点/悬空引用不因不可见而
 *     豁免——严格面统一），但不发射任何 op、不计入展开回放/标签统计。
 *
 * 输出 DrawOps V1（R1 §3.2 词表；宿主 VisualDrawOpsValidator 以同一套不变量复验）：
 *   { drawSchemaVersion:1, viewBox:{x,y,w,h}, background, ops[], hitRegions[],
 *     stats:{nodes,pathCommands,labels,ops} }
 *   - 路径命令仅 ["M",x,y]/["L",x,y]/["C",…]/["Z"]（Q 在此提升为 C；无 A——弧由 SDK
 *     在程序侧拆为 ≤90° 的 C 段）；绘制路径保持局部坐标（由所在组的 concat 提供变换），
 *     clip/命中区以组合后坐标烘焙（命中/裁剪点内判定无需逆变换）。
 *   - 组发射顺序：save → [clip(烘焙组合坐标)] → [concat(本地矩阵)] → children → restore。
 *     clip 在 concat 之前发射，因为烘焙坐标须在恒等变换下回放（R1 §3.2 不变量 2 的推论）。
 *   - save/restore 严格配平（仅在确有 concat/clip 需要开状态时发射）；group alpha 乘入
 *     子节点（预乘，painter 每次绘制恰一次 setAlpha）。
 *
 * 预算（契约 §4 表；与 Kotlin 侧 VisualExecutionBudget/VisualDrawOpsValidator 同值，
 * 改预算必须两端同改——JVM 负向矩阵与 Node 测试台各自钉住）：
 *   解析深度 ≤64；节点 ≤256；树深 ≤8；路径命令（定义总数与展开回放各计一次）≤4096；
 *   命中区 ≤128；输出 ≤256 KiB 字符（宿主再按 UTF-8 字节复验）。
 *
 * 错误面（fail-closed：抛 SceneCompileError，code + 有界 message；引擎折 INVALID_SCENE，
 * 诊断 ≤512 字符；无部分输出）：SCENE_PARSE_* / SCENE_FIELD_* / SCENE_NODE_* /
 * SCENE_PATH_* / SCENE_STYLE_* / SCENE_REF_* / SCENE_MATRIX_* / SCENE_BUDGET_*。
 * 注意：诊断文案刻意避开 "not defined" 字样——宿主分类器把该短语归 SANDBOX_VIOLATION。
 *
 * 算法来源（数学事实，不复制代码体）：κ = 4/3·tan(θ/4) 弧分段（kurbo arc.rs:77-86 旁证，
 * R1 §5.3 数值复核）；Q→C 次数提升为标准精确变换（误差 float64 舍入级）。本项目原创实现。
 */
(function () {
  'use strict';

  // 求值期值捕获（与 bootstrap 捕获 __ktRandom 同型）：杜绝后续脚本重绑内建影响本资产。
  var ObjectCreate = Object.create;
  var ObjectProtoHasOwn = Object.prototype.hasOwnProperty;
  var ArrayIsArray = Array.isArray;
  var NumberIsFinite = Number.isFinite;
  var NumberIsInteger = Number.isInteger;
  var NumberIsNaN = Number.isNaN;
  var MathAbs = Math.abs;
  var JSONStringify = JSON.stringify;

  // ---- 预算与边界（与 Kotlin 侧同值；见文件头） ----
  var MAX_PARSE_DEPTH = 64;
  var MAX_NODES = 256;
  var MAX_TREE_DEPTH = 8;
  var MAX_PATH_COMMANDS = 4096;
  var MAX_HIT_REGIONS = 128;
  var MAX_OUTPUT_CHARS = 262144; // 256 KiB（字符口径；宿主按 UTF-8 字节复验）
  var MAX_COORD = 100000;
  var MIN_ABS_DETERMINANT = 1e-12;
  var MAX_DASH_ENTRIES = 32;
  var MAX_TEXT_CHARS = 4096;
  var MAX_ID_CHARS = 128;

  function SceneCompileError(code, message) {
    var error = new Error(code + ': ' + String(message).slice(0, 180));
    error.name = 'SceneCompileError';
    return error;
  }

  function fail(code, message) {
    throw SceneCompileError(code, message);
  }

  function clipText(value) {
    var text = String(value);
    return text.length > 48 ? text.slice(0, 45) + '…' : text;
  }

  // ==================================================================
  // 严格 JSON 解析器（契约 §4：重复键 / 原型污染键 / 深度 / 非有限数 / 尾随内容）
  // 产物为 null-prototype 纯对象——原型污染在解析产物上结构性不可表达。
  // ==================================================================

  function strictParseJson(text, label) {
    if (typeof text !== 'string') {
      fail('SCENE_FIELD_TYPE', label + ' must be a JSON text');
    }
    var pos = 0;
    var length = text.length;
    var depth = 0;

    function syntaxError(detail) {
      fail('SCENE_PARSE_NOT_JSON', label + ': ' + detail + ' at offset ' + pos);
    }

    function skipWhitespace() {
      while (pos < length) {
        var c = text.charCodeAt(pos);
        if (c === 32 || c === 9 || c === 10 || c === 13) {
          pos += 1;
        } else {
          break;
        }
      }
    }

    function expectLiteral(word) {
      if (text.slice(pos, pos + word.length) !== word) {
        syntaxError('invalid literal');
      }
      pos += word.length;
      return word === 'true' ? true : word === 'false' ? false : null;
    }

    function parseString() {
      // 进入时 text[pos] === '"'。
      pos += 1;
      var out = [];
      var chunk = '';
      for (;;) {
        if (pos >= length) {
          syntaxError('unterminated string');
        }
        var code = text.charCodeAt(pos);
        if (code === 0x22) {
          pos += 1;
          if (chunk !== '') {
            out.push(chunk);
          }
          return out.join('');
        }
        if (code === 0x5c) {
          pos += 1;
          if (pos >= length) {
            syntaxError('unterminated escape');
          }
          var escape = text.charAt(pos);
          if (escape === '"') {
            chunk += '"';
            pos += 1;
          } else if (escape === '\\') {
            chunk += '\\';
            pos += 1;
          } else if (escape === '/') {
            chunk += '/';
            pos += 1;
          } else if (escape === 'b') {
            chunk += '\b';
            pos += 1;
          } else if (escape === 'f') {
            chunk += '\f';
            pos += 1;
          } else if (escape === 'n') {
            chunk += '\n';
            pos += 1;
          } else if (escape === 'r') {
            chunk += '\r';
            pos += 1;
          } else if (escape === 't') {
            chunk += '\t';
            pos += 1;
          } else if (escape === 'u') {
            if (pos + 5 > length) {
              syntaxError('truncated unicode escape');
            }
            var value = 0;
            for (var h = 1; h <= 4; h += 1) {
              var digit = parseInt(text.charAt(pos + h), 16);
              if (NumberIsNaN(digit)) {
                syntaxError('bad unicode escape');
              }
              value = value * 16 + digit;
            }
            chunk += String.fromCharCode(value);
            pos += 5;
          } else {
            syntaxError('bad escape');
          }
        } else if (code < 0x20) {
          syntaxError('raw control character in string');
        } else {
          chunk += text.charAt(pos);
          pos += 1;
          if (chunk.length >= 4096) {
            out.push(chunk);
            chunk = '';
          }
        }
      }
    }

    function parseNumber() {
      var start = pos;
      if (text.charAt(pos) === '-') {
        pos += 1;
      }
      var lead = text.charAt(pos);
      if (lead === '0') {
        pos += 1;
      } else if (lead >= '1' && lead <= '9') {
        pos += 1;
        while (pos < length && text.charCodeAt(pos) >= 48 && text.charCodeAt(pos) <= 57) {
          pos += 1;
        }
      } else {
        syntaxError('bad number');
      }
      if (text.charAt(pos) === '.') {
        pos += 1;
        var sawDigit = false;
        while (pos < length && text.charCodeAt(pos) >= 48 && text.charCodeAt(pos) <= 57) {
          pos += 1;
          sawDigit = true;
        }
        if (!sawDigit) {
          syntaxError('bad number fraction');
        }
      }
      var exponent = text.charAt(pos);
      if (exponent === 'e' || exponent === 'E') {
        pos += 1;
        var sign = text.charAt(pos);
        if (sign === '+' || sign === '-') {
          pos += 1;
        }
        var sawExponentDigit = false;
        while (pos < length && text.charCodeAt(pos) >= 48 && text.charCodeAt(pos) <= 57) {
          pos += 1;
          sawExponentDigit = true;
        }
        if (!sawExponentDigit) {
          syntaxError('bad number exponent');
        }
      }
      var token = text.slice(start, pos);
      var value = Number(token);
      if (!NumberIsFinite(value)) {
        fail('SCENE_PARSE_NON_FINITE', label + ': number ' + clipText(token) + ' is not finite');
      }
      return value;
    }

    function parseObject() {
      pos += 1; // '{'
      depth += 1;
      if (depth > MAX_PARSE_DEPTH) {
        fail('SCENE_PARSE_DEPTH', label + ': JSON nesting depth exceeds ' + MAX_PARSE_DEPTH);
      }
      var obj = ObjectCreate(null);
      var seen = ObjectCreate(null);
      skipWhitespace();
      if (text.charAt(pos) === '}') {
        pos += 1;
        depth -= 1;
        return obj;
      }
      for (;;) {
        skipWhitespace();
        if (text.charAt(pos) !== '"') {
          syntaxError('expected object key');
        }
        var key = parseString();
        if (key === '__proto__' || key === 'constructor' || key === 'prototype') {
          fail('SCENE_PARSE_POISONED_KEY', label + ': prototype-pollution key "' + clipText(key) + '"');
        }
        skipWhitespace();
        if (text.charAt(pos) !== ':') {
          syntaxError('expected ":" after key');
        }
        pos += 1;
        var value = parseValue();
        if (seen[key] === 1) {
          fail('SCENE_PARSE_DUP_KEY', label + ': duplicate key "' + clipText(key) + '"');
        }
        seen[key] = 1;
        obj[key] = value;
        skipWhitespace();
        var separator = text.charAt(pos);
        if (separator === ',') {
          pos += 1;
          continue;
        }
        if (separator === '}') {
          pos += 1;
          break;
        }
        syntaxError('expected "," or "}"');
      }
      depth -= 1;
      return obj;
    }

    function parseArray() {
      pos += 1; // '['
      depth += 1;
      if (depth > MAX_PARSE_DEPTH) {
        fail('SCENE_PARSE_DEPTH', label + ': JSON nesting depth exceeds ' + MAX_PARSE_DEPTH);
      }
      var arr = [];
      skipWhitespace();
      if (text.charAt(pos) === ']') {
        pos += 1;
        depth -= 1;
        return arr;
      }
      for (;;) {
        arr.push(parseValue());
        skipWhitespace();
        var separator = text.charAt(pos);
        if (separator === ',') {
          pos += 1;
          continue;
        }
        if (separator === ']') {
          pos += 1;
          break;
        }
        syntaxError('expected "," or "]"');
      }
      depth -= 1;
      return arr;
    }

    function parseValue() {
      skipWhitespace();
      if (pos >= length) {
        syntaxError('unexpected end of input');
      }
      var c = text.charAt(pos);
      if (c === '{') {
        return parseObject();
      }
      if (c === '[') {
        return parseArray();
      }
      if (c === '"') {
        return parseString();
      }
      if (c === 't' || c === 'f' || c === 'n') {
        return expectLiteral(c === 't' ? 'true' : c === 'f' ? 'false' : 'null');
      }
      if (c === '-' || (c >= '0' && c <= '9')) {
        return parseNumber();
      }
      syntaxError('unexpected character');
      return undefined;
    }

    var parsed = parseValue();
    skipWhitespace();
    if (pos !== length) {
      fail('SCENE_PARSE_TRAILING', label + ': trailing content after JSON value at offset ' + pos);
    }
    return parsed;
  }

  // ==================================================================
  // 校验帮助函数
  // ==================================================================

  function isPlainObject(value) {
    return value !== null && typeof value === 'object' && !ArrayIsArray(value);
  }

  function requireObject(value, at) {
    if (!isPlainObject(value)) {
      fail('SCENE_FIELD_TYPE', at + ' must be an object');
    }
    return value;
  }

  // 未知字段门用**自有键判定**（ObjectProtoHasOwn.call），不做真值查表：allowed 是
  // 普通对象字面量，真值查表会经 Object.prototype 命中继承键——场景自有键 'toString'
  // 曾因此绕过 SCENE_FIELD_UNKNOWN（"严格拒绝未知字段"契约被破，批次 27 复核修复）。
  // for...in 只枚举 obj 的自有可枚举键（解析产物 null-proto，见 strictParseJson），
  // 继承键不会从 obj 侧进来，泄出面只在 allowed 查表一侧。
  function checkKeys(obj, allowed, at) {
    for (var key in obj) {
      if (!ObjectProtoHasOwn.call(allowed, key)) {
        fail('SCENE_FIELD_UNKNOWN', at + ': unknown field "' + clipText(key) + '"');
      }
    }
  }

  function requireString(value, at, maxChars) {
    if (typeof value !== 'string' || value.length === 0 || value.length > maxChars) {
      fail('SCENE_FIELD_TYPE', at + ' must be a non-empty string of at most ' + maxChars + ' chars');
    }
    return value;
  }

  function requireFiniteNumber(value, at) {
    if (typeof value !== 'number' || !NumberIsFinite(value)) {
      fail('SCENE_FIELD_TYPE', at + ' must be a finite number');
    }
    return value;
  }

  function requireBoundedNumber(value, at, bound) {
    var n = requireFiniteNumber(value, at);
    if (n > bound || n < -bound) {
      fail('SCENE_FIELD_BOUNDS', at + ' is outside the ±' + bound + ' bound');
    }
    return n;
  }

  var COLOR_PATTERN = /^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$/;

  function requireColor(value, at) {
    if (typeof value !== 'string' || !COLOR_PATTERN.test(value)) {
      fail('SCENE_STYLE_COLOR', at + ' must be "#RRGGBB" or "#RRGGBBAA"');
    }
    return value;
  }

  function requirePoint(value, at) {
    requireObject(value, at);
    checkKeys(value, { x: 1, y: 1 }, at);
    return {
      x: requireBoundedNumber(value.x, at + '.x', MAX_COORD),
      y: requireBoundedNumber(value.y, at + '.y', MAX_COORD),
    };
  }

  // 词表查表一律 null-prototype（与 strictParseJson 解析产物同纪律，:255 ObjectCreate(null)）。
  // **它消灭的失败**：普通对象字面量做真值查表会经 Object.prototype 命中继承键——
  // stroke.cap='constructor' 曾绕过 SCENE_STYLE_CAP、join='toString' 绕过 SCENE_STYLE_JOIN、
  // 命中区/clip rule='valueOf' 绕过 SCENE_STYLE_RULE，并把原串发射进 DrawOps（宿主第二道门
  // 只能错归 DRAWOPS_* 码族）；hAlign='toString' 曾经 H_ALIGNS 继承命中漏到 NaN 位置，
  // 错归 SCENE_FIELD_BOUNDS。批次 27 复核修复（Node 台有逐例负向钉）。
  function nullProtoTable(pairs) {
    var table = ObjectCreate(null);
    for (var i = 0; i < pairs.length; i += 1) {
      table[pairs[i][0]] = pairs[i][1];
    }
    return table;
  }

  var FILL_RULES = nullProtoTable([['nonzero', 1], ['evenodd', 1]]);
  var CAPS = nullProtoTable([['butt', 1], ['round', 1], ['square', 1]]);
  var JOINS = nullProtoTable([['miter', 1], ['round', 1], ['bevel', 1]]);
  var H_ALIGNS = nullProtoTable([['left', 0], ['center', 0.5], ['right', 1]]);
  var V_ALIGNS = nullProtoTable([['top', 0], ['middle', 0.5], ['bottom', 1]]);

  function requireStroke(value, at) {
    var stroke = requireObject(value, at);
    checkKeys(stroke, { width: 1, cap: 1, join: 1, miterLimit: 1, dash: 1 }, at);
    var width = requireFiniteNumber(stroke.width, at + '.width');
    if (width <= 0 || width > MAX_COORD) {
      fail('SCENE_STYLE_WIDTH', at + '.width must be within (0, ' + MAX_COORD + ']');
    }
    var cap = stroke.cap === undefined ? 'butt' : stroke.cap;
    if (!CAPS[cap]) {
      fail('SCENE_STYLE_CAP', at + '.cap must be "butt", "round" or "square"');
    }
    var join = stroke.join === undefined ? 'miter' : stroke.join;
    if (!JOINS[join]) {
      fail('SCENE_STYLE_JOIN', at + '.join must be "miter", "round" or "bevel"');
    }
    var miterLimit = stroke.miterLimit === undefined ? 4 : stroke.miterLimit;
    miterLimit = requireFiniteNumber(miterLimit, at + '.miterLimit');
    if (miterLimit < 1 || miterLimit > MAX_COORD) {
      fail('SCENE_STYLE_MITER_LIMIT', at + '.miterLimit must be within [1, ' + MAX_COORD + ']');
    }
    var dash = null;
    if (stroke.dash !== undefined && stroke.dash !== null) {
      if (!ArrayIsArray(stroke.dash) || stroke.dash.length > MAX_DASH_ENTRIES) {
        fail('SCENE_STYLE_DASH', at + '.dash must be null or an array of at most ' + MAX_DASH_ENTRIES + ' numbers');
      }
      dash = [];
      for (var i = 0; i < stroke.dash.length; i += 1) {
        var entry = requireFiniteNumber(stroke.dash[i], at + '.dash[' + i + ']');
        if (entry <= 0 || entry > MAX_COORD) {
          fail('SCENE_STYLE_DASH', at + '.dash[' + i + '] must be within (0, ' + MAX_COORD + ']');
        }
        dash.push(entry);
      }
      // KD-78 ② 形状门（批次 29 T5）：DashPathEffect 契约要求偶数个 interval，且
      // length<2（含空数组）被 AOSP 构造器直接抛**无码** ArrayIndexOutOfBoundsException
      //（aosp-mirror DashPathEffect.java:34-39）。生成侧前置拒绝，码面与回放 painter 同词
      //（PAINTER_DASH_ODD）；回放侧兜底保留。它消灭"合法生成 → 回放时才拒/才无码崩"的
      // 预算浪费与诊断丢失。
      if (dash.length < 2 || dash.length % 2 !== 0) {
        fail('PAINTER_DASH_ODD', at + '.dash must have an even number of entries (at least 2), got ' + dash.length);
      }
    }
    return { width: width, cap: cap, join: join, miterLimit: miterLimit, dash: dash };
  }

  // ---- 路径命令校验 + Q→C 提升（局部坐标） ----

  function validatePathCommands(commands, at) {
    if (!ArrayIsArray(commands) || commands.length === 0) {
      fail('SCENE_PATH_NOT_M_START', at + ' must be a non-empty command array starting with "M"');
    }
    var first = commands[0];
    if (!ArrayIsArray(first) || first[0] !== 'M') {
      fail('SCENE_PATH_NOT_M_START', at + ' must start with an "M" command');
    }
    var out = [];
    var currentX = 0;
    var currentY = 0;
    var subpathStartX = 0;
    var subpathStartY = 0;
    for (var i = 0; i < commands.length; i += 1) {
      var cmd = commands[i];
      var where = at + '[' + i + ']';
      if (!ArrayIsArray(cmd) || cmd.length === 0 || typeof cmd[0] !== 'string') {
        fail('SCENE_PATH_ARGS', where + ' must be a command array like ["M", x, y]');
      }
      var code = cmd[0];
      var coordinate = function (index) {
        var v = cmd[index];
        if (typeof v !== 'number' || !NumberIsFinite(v)) {
          fail('SCENE_PATH_COORD', where + ': coordinates must be finite numbers');
        }
        if (v > MAX_COORD || v < -MAX_COORD) {
          fail('SCENE_PATH_COORD', where + ': coordinate outside the ±' + MAX_COORD + ' bound');
        }
        return v;
      };
      if (code === 'M') {
        if (cmd.length !== 3) {
          fail('SCENE_PATH_ARGS', where + ' "M" takes exactly [x, y]');
        }
        currentX = coordinate(1);
        currentY = coordinate(2);
        subpathStartX = currentX;
        subpathStartY = currentY;
        out.push(['M', currentX, currentY]);
      } else if (code === 'L') {
        if (cmd.length !== 3) {
          fail('SCENE_PATH_ARGS', where + ' "L" takes exactly [x, y]');
        }
        currentX = coordinate(1);
        currentY = coordinate(2);
        out.push(['L', currentX, currentY]);
      } else if (code === 'C') {
        if (cmd.length !== 7) {
          fail('SCENE_PATH_ARGS', where + ' "C" takes exactly [x1, y1, x2, y2, x, y]');
        }
        var c1x = coordinate(1);
        var c1y = coordinate(2);
        var c2x = coordinate(3);
        var c2y = coordinate(4);
        currentX = coordinate(5);
        currentY = coordinate(6);
        out.push(['C', c1x, c1y, c2x, c2y, currentX, currentY]);
      } else if (code === 'Q') {
        // Q→C 次数提升（精确变换）：C1=(P0+2Q)/3, C2=(P2+2Q)/3（R1 §5.3 数值复核）。
        if (cmd.length !== 5) {
          fail('SCENE_PATH_ARGS', where + ' "Q" takes exactly [cx, cy, x, y]');
        }
        var qx = coordinate(1);
        var qy = coordinate(2);
        var endX = coordinate(3);
        var endY = coordinate(4);
        out.push(['C', (currentX + 2 * qx) / 3, (currentY + 2 * qy) / 3, (endX + 2 * qx) / 3, (endY + 2 * qy) / 3, endX, endY]);
        currentX = endX;
        currentY = endY;
      } else if (code === 'Z') {
        if (cmd.length !== 1) {
          fail('SCENE_PATH_ARGS', where + ' "Z" takes no arguments');
        }
        out.push(['Z']);
        currentX = subpathStartX;
        currentY = subpathStartY;
      } else {
        fail('SCENE_PATH_UNKNOWN_CMD', where + ': unknown path command "' + clipText(code) + '"');
      }
    }
    return out;
  }

  // ---- 矩阵：[a,b,c,d,e,f]，x' = a·x + c·y + e；y' = b·x + d·y + f（契约 §5） ----

  function validateMatrix(value, at) {
    if (!ArrayIsArray(value) || value.length !== 6) {
      fail('SCENE_FIELD_TYPE', at + ' must be an array of 6 numbers [a,b,c,d,e,f]');
    }
    for (var i = 0; i < 6; i += 1) {
      var v = value[i];
      if (typeof v !== 'number' || !NumberIsFinite(v)) {
        fail('SCENE_MATRIX_NOT_FINITE', at + ': matrix entries must be finite numbers');
      }
      if (v > MAX_COORD || v < -MAX_COORD) {
        fail('SCENE_MATRIX_BOUNDS', at + ': matrix entry outside the ±' + MAX_COORD + ' bound');
      }
    }
    var determinant = value[0] * value[3] - value[1] * value[2];
    if (!NumberIsFinite(determinant) || MathAbs(determinant) < MIN_ABS_DETERMINANT) {
      fail('SCENE_MATRIX_SINGULAR', at + ': |det| < ' + MIN_ABS_DETERMINANT + ' (non-invertible)');
    }
    return [value[0], value[1], value[2], value[3], value[4], value[5]];
  }

  /** parent * local（先 local 后 parent；契约 §5"组合 parent*local"）。 */
  function multiplyMatrix(parent, local) {
    return [
      parent[0] * local[0] + parent[2] * local[1],
      parent[1] * local[0] + parent[3] * local[1],
      parent[0] * local[2] + parent[2] * local[3],
      parent[1] * local[2] + parent[3] * local[3],
      parent[0] * local[4] + parent[2] * local[5] + parent[4],
      parent[1] * local[4] + parent[3] * local[5] + parent[5],
    ];
  }

  function checkComposedMatrix(matrix, at) {
    for (var i = 0; i < 6; i += 1) {
      var v = matrix[i];
      if (!NumberIsFinite(v)) {
        fail('SCENE_MATRIX_NOT_FINITE', at + ': composed matrix is not finite');
      }
      if (v > MAX_COORD || v < -MAX_COORD) {
        fail('SCENE_MATRIX_BOUNDS', at + ': composed matrix entry outside the ±' + MAX_COORD + ' bound');
      }
    }
    var determinant = matrix[0] * matrix[3] - matrix[1] * matrix[2];
    if (MathAbs(determinant) < MIN_ABS_DETERMINANT) {
      fail('SCENE_MATRIX_SINGULAR', at + ': composed matrix is non-invertible');
    }
  }

  /** 以组合矩阵烘焙路径命令坐标（clip/命中区；产物已是场景空间）。 */
  function bakeCommands(commands, matrix, at) {
    var out = [];
    for (var i = 0; i < commands.length; i += 1) {
      var cmd = commands[i];
      if (cmd[0] === 'Z') {
        out.push(['Z']);
        continue;
      }
      var baked = [cmd[0]];
      for (var j = 1; j < cmd.length; j += 2) {
        var x = cmd[j];
        var y = cmd[j + 1];
        var tx = matrix[0] * x + matrix[2] * y + matrix[4];
        var ty = matrix[1] * x + matrix[3] * y + matrix[5];
        if (!NumberIsFinite(tx) || !NumberIsFinite(ty) || tx > MAX_COORD || tx < -MAX_COORD || ty > MAX_COORD || ty < -MAX_COORD) {
          fail('SCENE_MATRIX_BOUNDS', at + ': baked coordinate outside the ±' + MAX_COORD + ' bound');
        }
        baked.push(tx, ty);
      }
      out.push(baked);
    }
    return out;
  }

  // ==================================================================
  // 输入三件套：metrics / expect / scene
  // ==================================================================

  function parseMetrics(text) {
    var arr = strictParseJson(text, 'metrics');
    if (!ArrayIsArray(arr)) {
      fail('SCENE_REF_METRICS', 'metrics must be an array');
    }
    var byContentId = ObjectCreate(null);
    for (var i = 0; i < arr.length; i += 1) {
      var at = 'metrics[' + i + ']';
      var entry = requireObject(arr[i], at);
      checkKeys(entry, { contentId: 1, widthDu: 1, heightDu: 1, baselineDu: 1, fontSizeSp: 1, digest: 1 }, at);
      var contentId = requireString(entry.contentId, at + '.contentId', 256);
      if (byContentId[contentId]) {
        fail('SCENE_REF_METRICS', at + ': duplicate contentId "' + clipText(contentId) + '"');
      }
      byContentId[contentId] = {
        width: requireFiniteNumber(entry.widthDu, at + '.widthDu'),
        height: requireFiniteNumber(entry.heightDu, at + '.heightDu'),
        baseline: requireFiniteNumber(entry.baselineDu, at + '.baselineDu'),
      };
    }
    return byContentId;
  }

  function parseExpect(text) {
    var value = strictParseJson(text, 'expect');
    requireObject(value, 'expect');
    checkKeys(value, { sceneRevision: 1, viewport: 1 }, 'expect');
    if (!NumberIsInteger(value.sceneRevision) || value.sceneRevision < 1) {
      fail('SCENE_FIELD_TYPE', 'expect.sceneRevision must be an integer >= 1');
    }
    var viewport = requireObject(value.viewport, 'expect.viewport');
    checkKeys(viewport, { widthDu: 1, heightDu: 1 }, 'expect.viewport');
    if (!NumberIsInteger(viewport.widthDu) || !NumberIsInteger(viewport.heightDu)) {
      fail('SCENE_FIELD_TYPE', 'expect.viewport must carry integer du sizes');
    }
    return { sceneRevision: value.sceneRevision, widthDu: viewport.widthDu, heightDu: viewport.heightDu };
  }

  // ==================================================================
  // 编译主流程
  // ==================================================================

  function compile(sceneText, metricsText, expectText) {
    var expect = parseExpect(expectText);
    var metrics = parseMetrics(metricsText);
    var scene = strictParseJson(sceneText, 'scene');
    requireObject(scene, 'scene');
    checkKeys(scene, {
      schemaVersion: 1, sceneId: 1, revision: 1, viewBox: 1, background: 1,
      paths: 1, root: 1, controls: 1, hitRegions: 1, accessibility: 1,
      form: 1, fallbackMarkdown: 1,
    }, 'scene');

    if (!NumberIsInteger(scene.schemaVersion) || scene.schemaVersion !== 1) {
      fail('SCENE_FIELD_SCHEMA_VERSION', 'scene.schemaVersion must be exactly 1');
    }
    requireString(scene.sceneId, 'scene.sceneId', MAX_ID_CHARS);
    if (!NumberIsInteger(scene.revision) || scene.revision !== expect.sceneRevision) {
      fail('SCENE_FIELD_REVISION', 'scene.revision must echo expect.sceneRevision (' + expect.sceneRevision + ')');
    }

    // viewBox：V1 必须等于视口 {0,0,viewport}（R1 §3.2"否则 SCENE_FIELD 拒绝"）。
    var viewBox = requireObject(scene.viewBox, 'scene.viewBox');
    checkKeys(viewBox, { x: 1, y: 1, width: 1, height: 1 }, 'scene.viewBox');
    var vx = requireFiniteNumber(viewBox.x, 'scene.viewBox.x');
    var vy = requireFiniteNumber(viewBox.y, 'scene.viewBox.y');
    var vw = requireFiniteNumber(viewBox.width, 'scene.viewBox.width');
    var vh = requireFiniteNumber(viewBox.height, 'scene.viewBox.height');
    if (vx !== 0 || vy !== 0 || vw !== expect.widthDu || vh !== expect.heightDu) {
      fail('SCENE_FIELD_VIEWPORT', 'scene.viewBox must equal the viewport {0,0,' + expect.widthDu + ',' + expect.heightDu + '}');
    }

    var background = null;
    if (scene.background !== undefined && scene.background !== null) {
      background = requireColor(scene.background, 'scene.background');
    }

    // 未启用能力（契约 §3.2 能力门：解析层拒绝，不是隐藏 UI）。
    if (scene.controls !== undefined && !(ArrayIsArray(scene.controls) && scene.controls.length === 0)) {
      fail('SCENE_FIELD_CONTROLS', 'scene.controls must be an empty array in V1 (native controls land in 4E.2)');
    }
    if (scene.accessibility !== undefined && scene.accessibility !== null) {
      fail('SCENE_FIELD_UNAVAILABLE', 'scene.accessibility is not enabled in V1');
    }
    if (scene.form !== undefined && scene.form !== null) {
      fail('SCENE_FIELD_UNAVAILABLE', 'scene.form is not enabled in V1 (4E.2)');
    }
    var fallbackMarkdown = null;
    if (scene.fallbackMarkdown !== undefined && scene.fallbackMarkdown !== null) {
      fallbackMarkdown = requireString(scene.fallbackMarkdown, 'scene.fallbackMarkdown', MAX_TEXT_CHARS);
    }

    // paths[] 定义：校验 + 一次性提升；定义总数计入命令预算（展开回放另计，防重复引用放大）。
    var definitions = ObjectCreate(null);
    var definitionCommandTotal = 0;
    if (scene.paths !== undefined && scene.paths !== null) {
      if (!ArrayIsArray(scene.paths)) {
        fail('SCENE_FIELD_TYPE', 'scene.paths must be an array');
      }
      for (var d = 0; d < scene.paths.length; d += 1) {
        var defAt = 'scene.paths[' + d + ']';
        var def = requireObject(scene.paths[d], defAt);
        checkKeys(def, { id: 1, commands: 1 }, defAt);
        var defId = requireString(def.id, defAt + '.id', 64);
        if (definitions[defId]) {
          fail('SCENE_FIELD_DUP_ID', defAt + ': duplicate path definition id "' + clipText(defId) + '"');
        }
        var promoted = validatePathCommands(def.commands, defAt + '.commands');
        definitionCommandTotal += promoted.length;
        if (definitionCommandTotal > MAX_PATH_COMMANDS) {
          fail('SCENE_PATH_COUNT', 'path definition commands total ' + definitionCommandTotal + ' exceeds ' + MAX_PATH_COMMANDS);
        }
        definitions[defId] = promoted;
      }
    }

    var ops = [];
    var nodeCount = 0;
    var labelCount = 0;
    var expandedPathCommands = 0;
    var nodeIds = ObjectCreate(null);
    var IDENTITY = [1, 0, 0, 1, 0, 0];

    function walkNode(node, at, depth, matrix, opacity, emit) {
      nodeCount += 1;
      if (nodeCount > MAX_NODES) {
        fail('SCENE_NODE_COUNT', 'scene has more than ' + MAX_NODES + ' nodes');
      }
      if (depth > MAX_TREE_DEPTH) {
        fail('SCENE_NODE_DEPTH', at + ': tree depth exceeds ' + MAX_TREE_DEPTH);
      }
      requireObject(node, at);
      var type = node.type;
      if (type === 'group') {
        checkKeys(node, { type: 1, id: 1, matrix: 1, opacity: 1, visible: 1, clip: 1, children: 1 }, at);
      } else if (type === 'path') {
        checkKeys(node, { type: 1, id: 1, pathId: 1, fill: 1, stroke: 1 }, at);
      } else if (type === 'label') {
        checkKeys(node, { type: 1, id: 1, contentId: 1, anchor: 1, offset: 1, hAlign: 1, vAlign: 1, rotationDeg: 1, color: 1, order: 1 }, at);
      } else {
        fail('SCENE_NODE_TYPE', at + ': unknown node type "' + clipText(type) + '" (only group/path/label)');
      }
      if (node.id !== undefined && node.id !== null) {
        var nodeId = requireString(node.id, at + '.id', 64);
        if (nodeIds[nodeId]) {
          fail('SCENE_NODE_DUP_ID', at + ': duplicate node id "' + clipText(nodeId) + '"');
        }
        nodeIds[nodeId] = 1;
      }

      if (type === 'group') {
        if (node.visible !== undefined && typeof node.visible !== 'boolean') {
          fail('SCENE_FIELD_TYPE', at + '.visible must be a boolean');
        }
        // visible:false：整树仍校验与计数，但不发射、不计回放/标签（见文件头）。
        var subtreeEmits = emit && node.visible !== false;
        var groupOpacity = 1;
        if (node.opacity !== undefined && node.opacity !== null) {
          groupOpacity = requireFiniteNumber(node.opacity, at + '.opacity');
          if (groupOpacity < 0 || groupOpacity > 1) {
            fail('SCENE_STYLE_ALPHA', at + '.opacity must be within [0, 1]');
          }
        }
        var localMatrix = null;
        if (node.matrix !== undefined && node.matrix !== null) {
          localMatrix = validateMatrix(node.matrix, at + '.matrix');
        }
        var composed = localMatrix ? multiplyMatrix(matrix, localMatrix) : matrix;
        if (localMatrix) {
          checkComposedMatrix(composed, at + '.matrix');
        }
        var clip = null;
        if (node.clip !== undefined && node.clip !== null) {
          var clipAt = at + '.clip';
          var clipSpec = requireObject(node.clip, clipAt);
          checkKeys(clipSpec, { path: 1, rule: 1 }, clipAt);
          var clipRule = clipSpec.rule === undefined ? 'nonzero' : clipSpec.rule;
          if (!FILL_RULES[clipRule]) {
            fail('SCENE_STYLE_RULE', clipAt + '.rule must be "nonzero" or "evenodd"');
          }
          // clip 路径按"组本地空间（含本组自身矩阵）"解释（SVG 同型约定，本批钉定）。
          var clipCommands = validatePathCommands(clipSpec.path, clipAt + '.path');
          if (subtreeEmits) {
            expandedPathCommands += clipCommands.length;
            if (expandedPathCommands > MAX_PATH_COMMANDS) {
              fail('SCENE_PATH_COUNT', 'path commands replay total ' + expandedPathCommands + ' exceeds ' + MAX_PATH_COMMANDS);
            }
          }
          clip = { commands: clipCommands, rule: clipRule };
        }
        var children = node.children === undefined ? [] : node.children;
        if (!ArrayIsArray(children)) {
          fail('SCENE_FIELD_TYPE', at + '.children must be an array');
        }
        var opened = false;
        if (subtreeEmits && (clip || localMatrix)) {
          ops.push({ op: 'save' });
          opened = true;
        }
        if (subtreeEmits && clip) {
          // 烘焙组合坐标（含本组矩阵）；发射在 concat 之前 → 回放时恒等变换下生效。
          ops.push({ op: 'clip', path: bakeCommands(clip.commands, composed, at + '.clip'), rule: clip.rule });
        }
        if (subtreeEmits && localMatrix) {
          ops.push({ op: 'concat', m: localMatrix });
        }
        for (var c = 0; c < children.length; c += 1) {
          walkNode(children[c], at + '.children[' + c + ']', depth + 1, composed, opacity * groupOpacity, subtreeEmits);
        }
        if (opened) {
          ops.push({ op: 'restore' });
        }
      } else if (type === 'path') {
        var pathId = node.pathId;
        if (typeof pathId !== 'string' || !definitions[pathId]) {
          fail('SCENE_PATH_REF', at + ': pathId "' + clipText(pathId) + '" has no definition in scene.paths');
        }
        var promoted = definitions[pathId];
        if (emit) {
          expandedPathCommands += promoted.length;
          if (expandedPathCommands > MAX_PATH_COMMANDS) {
            fail('SCENE_PATH_COUNT', 'path commands replay total ' + expandedPathCommands + ' exceeds ' + MAX_PATH_COMMANDS);
          }
          var fill = node.fill === undefined || node.fill === null ? null : requireColor(node.fill, at + '.fill');
          var stroke = null;
          if (node.stroke !== undefined && node.stroke !== null) {
            stroke = requireStroke(node.stroke, at + '.stroke');
          }
          if (fill === null && stroke === null) {
            fail('SCENE_STYLE_NO_PAINT', at + ': path needs a fill or a stroke');
          }
          // KD-78 ① 颜色源门（批次 29 T5）：DrawOps V1 的 stroke 无 color 字段，stroke-only
          // 在回放侧无颜色可画（回放裁定"stroke 色取 fill 色"要求 fill 必填）。生成侧前置
          // 拒绝，码面与 painter 同词（PAINTER_STROKE_WITHOUT_FILL）；回放侧兜底保留。
          if (fill === null && stroke !== null) {
            fail('PAINTER_STROKE_WITHOUT_FILL', at + ': stroke-only path has no color source in DrawOps V1 (fill is required)');
          }
          ops.push({ op: 'path', path: promoted, fill: fill, stroke: stroke, alpha: opacity });
        }
      } else {
        // label
        var contentId = requireString(node.contentId, at + '.contentId', 256);
        var metric = metrics[contentId];
        if (!metric) {
          fail('SCENE_REF_CONTENT', at + ': contentId "' + clipText(contentId) + '" is not frozen in this transaction');
        }
        var anchor = requirePoint(node.anchor, at + '.anchor');
        var offsetX = 0;
        var offsetY = 0;
        if (node.offset !== undefined && node.offset !== null) {
          var offset = requirePoint(node.offset, at + '.offset');
          offsetX = offset.x;
          offsetY = offset.y;
        }
        if (typeof node.hAlign !== 'string' || H_ALIGNS[node.hAlign] === undefined) {
          fail('SCENE_FIELD_TYPE', at + '.hAlign must be "left", "center" or "right"');
        }
        if (typeof node.vAlign !== 'string' || V_ALIGNS[node.vAlign] === undefined) {
          fail('SCENE_FIELD_TYPE', at + '.vAlign must be "top", "middle" or "bottom"');
        }
        var rotationDeg = 0;
        if (node.rotationDeg !== undefined && node.rotationDeg !== null) {
          rotationDeg = requireBoundedNumber(node.rotationDeg, at + '.rotationDeg', MAX_COORD);
        }
        var color = requireColor(node.color, at + '.color');
        if (node.order !== undefined && node.order !== null) {
          if (!NumberIsInteger(node.order) || node.order < 0 || node.order > MAX_PATH_COMMANDS) {
            fail('SCENE_FIELD_TYPE', at + '.order must be an integer within [0, ' + MAX_PATH_COMMANDS + ']');
          }
        }
        if (emit) {
          // 位置一次算定（契约 §5"compiler 一次算出最终位置"）：anchor 经组合矩阵投影 →
          // 屏幕空间 offset → 度量宽高对齐 → baseline。旋转绕 (x, y)（文本原点）。
          var px = matrix[0] * anchor.x + matrix[2] * anchor.y + matrix[4] + offsetX;
          var py = matrix[1] * anchor.x + matrix[3] * anchor.y + matrix[5] + offsetY;
          var x = px - metric.width * H_ALIGNS[node.hAlign];
          var y = py - metric.height * V_ALIGNS[node.vAlign] + metric.baseline;
          if (!NumberIsFinite(x) || !NumberIsFinite(y) || x > MAX_COORD || x < -MAX_COORD || y > MAX_COORD || y < -MAX_COORD) {
            fail('SCENE_FIELD_BOUNDS', at + ': computed label position is outside the ±' + MAX_COORD + ' bound');
          }
          labelCount += 1;
          ops.push({
            op: 'label',
            contentId: contentId,
            x: x,
            y: y,
            rotationDeg: rotationDeg,
            hAlign: node.hAlign,
            vAlign: node.vAlign,
            color: color,
            alpha: opacity,
          });
        }
      }
    }

    if (scene.root !== undefined && scene.root !== null) {
      walkNode(scene.root, 'scene.root', 1, IDENTITY, 1, true);
    }

    // 命中区：场景空间（恒等变换）——命中点内判定无需逆变换（R1 §3.2 不变量 2）。
    var hitRegions = [];
    var hitIds = ObjectCreate(null);
    if (scene.hitRegions !== undefined && scene.hitRegions !== null) {
      if (!ArrayIsArray(scene.hitRegions)) {
        fail('SCENE_FIELD_TYPE', 'scene.hitRegions must be an array');
      }
      if (scene.hitRegions.length > MAX_HIT_REGIONS) {
        fail('SCENE_BUDGET_HIT_REGIONS', 'scene has ' + scene.hitRegions.length + ' hit regions, over ' + MAX_HIT_REGIONS);
      }
      for (var h = 0; h < scene.hitRegions.length; h += 1) {
        var hitAt = 'scene.hitRegions[' + h + ']';
        var region = requireObject(scene.hitRegions[h], hitAt);
        checkKeys(region, { id: 1, path: 1, rule: 1 }, hitAt);
        var hitId = requireString(region.id, hitAt + '.id', 64);
        if (hitIds[hitId]) {
          fail('SCENE_FIELD_DUP_ID', hitAt + ': duplicate hit region id "' + clipText(hitId) + '"');
        }
        hitIds[hitId] = 1;
        var hitRule = region.rule === undefined ? 'nonzero' : region.rule;
        if (!FILL_RULES[hitRule]) {
          fail('SCENE_STYLE_RULE', hitAt + '.rule must be "nonzero" or "evenodd"');
        }
        var hitPath = validatePathCommands(region.path, hitAt + '.path');
        expandedPathCommands += hitPath.length;
        if (expandedPathCommands > MAX_PATH_COMMANDS) {
          fail('SCENE_PATH_COUNT', 'path commands replay total ' + expandedPathCommands + ' exceeds ' + MAX_PATH_COMMANDS);
        }
        hitRegions.push({ id: hitId, path: hitPath, rule: hitRule });
      }
    }

    return serialize({
      viewBox: { x: vx, y: vy, w: vw, h: vh },
      background: background,
      ops: ops,
      hitRegions: hitRegions,
      stats: {
        nodes: nodeCount,
        pathCommands: expandedPathCommands,
        labels: labelCount,
        ops: ops.length,
      },
    });
  }

  // ==================================================================
  // 确定性序列化（固定键序；数字经 Number::toString，同引擎内逐字节稳定）
  // ==================================================================

  function num(value) {
    return String(value);
  }

  function jsonText(value) {
    return JSONStringify(value);
  }

  function serializeCommands(commands) {
    var parts = new Array(commands.length);
    for (var i = 0; i < commands.length; i += 1) {
      var cmd = commands[i];
      if (cmd[0] === 'Z') {
        parts[i] = '["Z"]';
        continue;
      }
      var body = '';
      for (var j = 1; j < cmd.length; j += 1) {
        body += (j > 1 ? ',' : '') + num(cmd[j]);
      }
      parts[i] = '["' + cmd[0] + '",' + body + ']';
    }
    return '[' + parts.join(',') + ']';
  }

  function serializeStroke(stroke) {
    if (!stroke) {
      return 'null';
    }
    var dash = 'null';
    if (stroke.dash) {
      var entries = new Array(stroke.dash.length);
      for (var i = 0; i < stroke.dash.length; i += 1) {
        entries[i] = num(stroke.dash[i]);
      }
      dash = '[' + entries.join(',') + ']';
    }
    return '{"width":' + num(stroke.width) +
      ',"cap":"' + stroke.cap +
      '","join":"' + stroke.join +
      '","miterLimit":' + num(stroke.miterLimit) +
      ',"dash":' + dash + '}';
  }

  function serializeOp(op) {
    if (op.op === 'save') {
      return '{"op":"save"}';
    }
    if (op.op === 'restore') {
      return '{"op":"restore"}';
    }
    if (op.op === 'concat') {
      return '{"op":"concat","m":[' + num(op.m[0]) + ',' + num(op.m[1]) + ',' + num(op.m[2]) + ',' +
        num(op.m[3]) + ',' + num(op.m[4]) + ',' + num(op.m[5]) + ']}';
    }
    if (op.op === 'clip') {
      return '{"op":"clip","path":' + serializeCommands(op.path) + ',"rule":"' + op.rule + '"}';
    }
    if (op.op === 'path') {
      return '{"op":"path","path":' + serializeCommands(op.path) +
        ',"fill":' + (op.fill ? jsonText(op.fill) : 'null') +
        ',"stroke":' + serializeStroke(op.stroke) +
        ',"alpha":' + num(op.alpha) + '}';
    }
    // label
    return '{"op":"label","contentId":' + jsonText(op.contentId) +
      ',"x":' + num(op.x) +
      ',"y":' + num(op.y) +
      ',"rotationDeg":' + num(op.rotationDeg) +
      ',"hAlign":"' + op.hAlign +
      '","vAlign":"' + op.vAlign +
      '","color":' + jsonText(op.color) +
      ',"alpha":' + num(op.alpha) + '}';
  }

  function serialize(doc) {
    var opParts = new Array(doc.ops.length);
    for (var i = 0; i < doc.ops.length; i += 1) {
      opParts[i] = serializeOp(doc.ops[i]);
    }
    var hitParts = new Array(doc.hitRegions.length);
    for (var h = 0; h < doc.hitRegions.length; h += 1) {
      hitParts[h] = '{"id":' + jsonText(doc.hitRegions[h].id) +
        ',"path":' + serializeCommands(doc.hitRegions[h].path) +
        ',"rule":"' + doc.hitRegions[h].rule + '"}';
    }
    var out = '{"drawSchemaVersion":1' +
      ',"viewBox":{"x":' + num(doc.viewBox.x) + ',"y":' + num(doc.viewBox.y) +
      ',"w":' + num(doc.viewBox.w) + ',"h":' + num(doc.viewBox.h) + '}' +
      ',"background":' + (doc.background ? jsonText(doc.background) : 'null') +
      ',"ops":[' + opParts.join(',') + ']' +
      ',"hitRegions":[' + hitParts.join(',') + ']' +
      ',"stats":{"nodes":' + doc.stats.nodes +
      ',"pathCommands":' + doc.stats.pathCommands +
      ',"labels":' + doc.stats.labels +
      ',"ops":' + doc.stats.ops + '}}';
    if (out.length > MAX_OUTPUT_CHARS) {
      fail('SCENE_BUDGET_OPS', 'compiled draw ops are ' + out.length + ' chars, over ' + MAX_OUTPUT_CHARS);
    }
    return out;
  }

  globalThis.__visualCompile = function (sceneText, metricsText, expectText) {
    if (typeof sceneText !== 'string' || typeof metricsText !== 'string' || typeof expectText !== 'string') {
      fail('SCENE_FIELD_TYPE', '__visualCompile expects three JSON text arguments');
    }
    return compile(sceneText, metricsText, expectText);
  };

  void 0;
})();
