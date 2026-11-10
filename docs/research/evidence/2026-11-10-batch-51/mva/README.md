# MVA JS 臂：设备适配器 + 作者链 + 事实提取器（批次 51 / G4 T25–T28）

> 证据目录。范围：把 MVA 的 JS 臂从"0 可评样本"推进到**可复跑的产物链**：
> 程序 JSON → 真引擎 → `scene.json` / `drawops.json` / `metrics.json` / `render.png`
> → `fact_expectations` 事实核验。**IMG 臂与 C 通道（VLM 判官）不在本轮**（见 §6）。
> 本目录**不声明 MVA 门 PASS**；门值仍未读（见 §5）。

## 1. 本轮新增/修改文件

| 文件 | 性质 | 消灭的具体失败 |
|---|---|---|
| `tools/figure_eval/fact_extract.py` | 新增（产品侧 Python） | JS 臂没有从 DrawOps 到 fact_expectations 的提取器；`label_absent` 文本级假接受；像素不可判被记 0 分 |
| `tools/figure_eval/test_fact_extract.py` | 新增（26 例） | 提取器与预期值耦合、漏回放栈、未知 key 静默兜底、`label_absent` 假通过 |
| `tools/figure_eval/mva_facts_run.py` | 新增 | 设备产物到无门值的描述统计之间没有可复跑读数器；partial 被读成 PASS |
| `core/data/src/androidTest/.../visual/MvaJsArmAdapterInstrumentedTest.kt` | 新增（设备轨） | 设备上没有"程序→图→证据"的记录面；`JsPort` 只回传 scene sha |
| `core/data/src/test/.../model/MvaFigureAuthorHarness.kt` | 新增（JVM 轨，env 门控） | MVA 没有作者链，模型能否写程序、失败率与失败形态未知 |

未改：任何门阈值、冻结夹具 120/24、`check_fact_expectations` 比较语义、产品主代码、
6C-2、JS 广告/注入状态。`tools/figure_eval/figure_eval_gate.py` 的既有 M 是他线 AUD-08
改动，本轮未触碰。

## 2. 事实提取器（T27）

### 2.1 判定对象与三条纪律

- 输入是 **DrawOps V1**（painter 真正回放的东西）+ prepare 批度量（`contentId→payload`）
  + 可选 SceneV1（角色绑定）。`path` 按 save/concat/restore 栈回放到根空间；`clip` 是烘焙
  根坐标，当可见性掩码（点内判定，evenodd/nonzero）；`label` 已由编译器投影到根空间，
  **不再套栈**（再乘一次是双重变换）；`alpha==0` 的元素不计入文本/计数。
- 提取器**不读 `fact_expectations`**：`extract(draw_ops, metrics, keys=[...])` 只收 key 名，
  值全部由结构算出；判定由既有 `figure_eval_gate.check_fact_expectations` 完成。
- 不可判 = `UNDECODABLE`（无值、该题 `evaluable=false`），不记 0、不冒充通过。未知 key
  直接 `ValueError`。

### 2.2 已实现 / 未实现（如实）

已实现（A1 结构类 + 任务级可评性，26 例测试覆盖）：

| 类 | key |
|---|---|
| label_set | 16 个 key（全部可见文本；subset 语义由门函数执行，多报另记） |
| count | `segment_count`、`step_count`、`label_count`、`kept_label_count`、`marked_points`、`cell_count`、`tick_count` |
| ordering | `step_order`（卡片主散布轴 + 每卡恰 1 标签）；`ascending_order`（轴带外的数值成员按 x 排序） |
| numeric | `segment_length_ratio`、`row_count`、`column_count`、`axis_min`、`axis_max`、`converted_ms`、`discriminant`、`real_distance_cm`、`point_x`、`point_y`、`value_of_1_2`、`value_of_1_3` |

未实现（fail-closed 为 `UNDECODABLE`）：

- **A2 角色类**（`arm_length_ratio`、`u_at_half_height`、`curve_value_at_x_equals_h`、
  `area_W_over_F0h_at_half`、`resultant_amplitude`、`phase_order`、`wave_count`、5 个
  `direction` key）——`bind_scene_nodes()` 已实现 SceneV1 `node.id → path/label op 索引`
  对齐并有三例测试（对齐/不一致/角色缺省），但**依赖角色的解码器未实现**；
- **C 类**（`label_absent` 两个 key）——夹具值是类名（"红笔勾/对号/波浪线"），文本级
  disjoint 会假接受（研究 §3.6 实证），必须走已校准的 C 通道。

### 2.3 测试（先红后绿）

先红：新增测试在模块缺席时收集期报错
`ModuleNotFoundError: No module named 'fact_extract'`（exit 2）。

后绿：

```
$ python -m pytest tools/figure_eval/test_fact_extract.py -q
26 passed in 0.06s

$ python -m pytest tools/figure_eval/test_fact_extract.py \
    tools/figure_eval/test_mva_gate.py tools/figure_eval/test_figure_eval_gate.py -q
70 passed in 0.17s
```

其中包含**生产编译器回归**：`probe_outputs.json` 的 8 个场景（生产同源
`visual-compiler.js` 编译）喂给新提取器，10 个 (case, task) 的接受/拒绝全部与探针预期
一致；`handwriting-residue-present` 的 `removed_handwriting` 保持 `UNDECODABLE`（不假通过）。

## 3. 设备适配器（T25）

文件：`core/data/src/androidTest/.../visual/MvaJsArmAdapterInstrumentedTest.kt`。
每题经 `QuickJsVisualExecutionEngine`（真引擎、真编译器资产、100 ms/1500 ms 预算不放大）
执行，记录型 measurer 留下 `contentId→payload/width/height/baseline`，`Rendered` 后
`VisualDrawOpsParser.parse(rendered)` → `VisualDrawOpsPainter` 以 `pxPerDu=2` 画到
360×240 du 的位图（720×480 PNG）。

可复跑命令（按需激活；未设参数时 `assumeTrue` 跳过，不伪造通过）：

```
./gradlew :core:data:installDebugAndroidTest
PKG=com.tingyun.smartmistakebook.core.data.test
adb shell run-as $PKG mkdir -p files/mva/js-arm/input
# 程序经 /data/local/tmp 中转进 app 私有目录（Android 13+ 不允许 shell 在
# /sdcard/Android/data 下创建目录）
adb push <programs>/<task>.json /data/local/tmp/p.json
adb shell "run-as $PKG sh -c 'cat /data/local/tmp/p.json > files/mva/js-arm/input/<task>.json'"
adb shell am instrument -w \
  -e class com.tingyun.smartmistakebook.core.data.visual.MvaJsArmAdapterInstrumentedTest \
  -e mvaProgramsDir /data/user/0/$PKG/files/mva/js-arm/input \
  -e mvaOutDir /data/user/0/$PKG/files/mva/js-arm \
  $PKG/androidx.test.runner.AndroidJUnitRunner
adb exec-out run-as $PKG cat files/mva/js-arm/out/manifest.json > manifest.json
```

（`./gradlew :core:data:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments...`
也能跑，但 AGP 在测试后卸载 APK，私有目录里的产物随之丢失——本轮改用
`installDebugAndroidTest` + `am instrument`，产物可拉取。）

### 3.1 端到端实证（`adapter-fixture/`）

一个手写的最小合法程序（`FIXTURE-RATIO-1TO1`，1:1 线段 + 标签）跑通全链：

```json
{"task_id":"FIXTURE-RATIO-1TO1","status":"RENDERED","latency_ms":161,
 "draw_sha256":"d8a923a2…","png_sha256":"5299bdc6…",
 "stats":{"nodes":3,"pathCommands":4,"labels":1,"ops":2}}
```

- 设备：`sdk_gphone64_x86_64`（Google，SDK 34），density 2.75，fontScale 1，pxPerDu 2；
  emulator-5590。
- `render.png` 目视：线段与 "1:1" 标签都在（不是空白图）。
- 提取器对**设备产出的 drawops + 度量**取值：
  `{"segment_length_ratio":1.0,"ratio_label":["1:1"],"segment_count":2}`，
  `check_fact_expectations` → `accepted=true`。
- `mva_facts_run.py` 在该产物上：`evaluable=1 / accepted=1 / gate_readable=true`（单题
  夹具，不是 120 门值）。

## 4. 作者链（T26）

`MvaFigureAuthorHarness`：对每题调一次真实 Provider（`deepseek-flash`，temperature 0.1，
`response_format=json_object`），提示词正文由产品 `FigurePromptAssembler.assemble(...)`
（JS 能力广告 + 静态程序契约 + 最小样例 + policyVersion）组装，回包经产品
`toCapturedFigureProposal()` 严格解析（键白名单 + 形状 + 64 KiB 预算）。凭据只从环境变量
读；记录只落 key 的 sha8（`cd10efbc`），不落本体。

与生产链路的差异（记录在 `author_manifest.json#divergences`）：传输是直连 HTTPS，不走
网关授权/ledger；system 消息是 harness 一行 JSON 指令；并发是 harness 提速（产品串行）。

### 4.1 冒烟（dev 24 的前 2 题）

- 作者：`OK 2/2`（`program_bytes` 2218 / 4135）。
- 设备渲染：`REJECTED 2/2`，同一有码失败
  `INVALID_SCENE / SCENE_FIELD_UNKNOWN: scene.root.children[0]: unknown field "strokeWidth"`。

### 4.2 产品提示词样例自身不可编译（本轮硬证据）

`FigurePromptAssembler.JS_STATIC_SAMPLE`（`core/data/.../FigurePromptAssembler.kt:256-271`）
给作者的 path 节点是 `{ type:'path', pathId:'axis', stroke:'#26354a' }`（`:267-268`），而
生产编译器要求 `stroke` 是对象（`visual-compiler.js:452 requireStroke` → `requireObject`）
且 stroke-only 无颜色源（fill 必填，同文件 `:892-894`）。把该样例原样送进生产编译器：

```
$ node docs/research/evidence/2026-11-10-batch-51/mva/product-sample-probe/probe_product_sample.mjs
{"compiles": false, "error": "SCENE_FIELD_TYPE: scene.root.children[0].stroke must be an object"}
EXIT=1
```

→ 作者模型照着产品提示词里的样例写，必然编译失败；这是 MVA 作者成功率的**产品侧阻断**，
不是模型能力读数。修复需要改产品提示词（含其 golden/测试），不在本轨最小改动内，**未改**。

### 4.3 120 盲测作者链（本轮实跑）

命令（8 路并发；产品串行，harness 提速，请求体逐题同形）：

```
MVA_AUTHOR_TASKS=tools/figure_eval/tasks_blind_120.json \
MVA_AUTHOR_OUT=docs/research/evidence/2026-11-10-batch-51/mva/author-blind \
MVA_AUTHOR_RUN_ID=mva-author-blind-120 MVA_AUTHOR_CONCURRENCY=8 \
SMB_REAL_PROVIDER_BASE_URL=... SMB_REAL_PROVIDER_KEY=... SMB_REAL_PROVIDER_MODEL=... \
./gradlew :core:data:testDebugUnitTest \
  --tests "com.tingyun.smartmistakebook.core.data.model.MvaFigureAuthorHarness" --rerun
```

作者结果（`author-blind/author_manifest.json`）：**120 题全部有记录**，
`OK 104`（86.7%）、`INVALID_PROPOSAL 10`、`NOT_GENERATE 5`（均 `decision=NEED_SOURCE`，
全在去手写/源图重建类）、`NOT_JSON 1`。按组：

| group | OK | 失败 |
|---|---|---|
| flow_formula | 20/20 | — |
| mechanics_phases | 20/20 | — |
| proportion_coordinates | 20/20 | — |
| natural_illustration | 19/20 | NOT_JSON 1 |
| source_rebuild | 18/20 | INVALID_PROPOSAL 1、NOT_GENERATE 1 |
| handwriting_removal | 7/20 | INVALID_PROPOSAL 9、NOT_GENERATE 4 |

设备渲染（104 个作者 OK 程序全部送适配器）：

```
$ adb shell am instrument -w -e class ...MvaJsArmAdapterInstrumentedTest \
    -e mvaProgramsDir .../blind-input -e mvaOutDir .../blind ...
OK (1 test)   Time: 3.695
$ python -I -X utf8 tools/figure_eval/mva_facts_run.py \
    --tasks tools/figure_eval/tasks_blind_120.json \
    --adapter-out docs/research/evidence/2026-11-10-batch-51/mva/device-blind \
    --out docs/research/evidence/2026-11-10-batch-51/mva/facts-blind
{ "adapter_status_counts": {"MISSING": 16, "REJECTED": 104},
  "evaluable": 0, "evaluable_coverage": 0.0, "gate_readable": false }
```

- **RENDERED 0/104**；104/104 是 `INVALID_SCENE`（编译期有界诊断）。
- 首个错误码分布（`device-blind/*.record.json` 的 diagnostics）：
  `SCENE_FIELD_UNKNOWN 72`、`SCENE_PATH_* 15`、`SCENE_FIELD_TYPE 12`、`SCENE_SDK_* 3`、
  `SCENE_STYLE_* 2`。
- 静态扫描 104 个程序：`stroke` 写成字符串 80/104、`strokeWidth` 87/104、
  label 用 `text` 字段 54/104——与 §4.2 的产品样例错误同型（模型照样例写 + 自行发挥）。
- 因此 **MVA JS 臂仍 0 可评样本**：失败不在事实提取器，而在"作者程序不可编译"。
  结论只能是 `NOT_EVALUABLE`，不是 0 分、更不是 PASS。

**修复建议（未执行，属产品提示词改动）**：把 `JS_STATIC_SAMPLE` 改成可编译样例
（`fill` 必填 + `stroke` 对象 + label 用 `contentId/anchor/hAlign/vAlign/color`），
并给 `FigurePromptAssembler` 加一条"样例必须能被 `visual-compiler.js` 编译"的测试。
改后须在 dev 24 上重新定型提示词；120 集已被本轮观测消费，不能再当未见集。

## 5. 门读数状态

`mva_facts_run.py` 的纪律：`gate_readable` 只在全部 120 题的 critical 事实都可判时为真；
A2/C 缺口会让 `evaluable_coverage < 1`，此时输出是 `NOT_EVALUABLE/partial` 的描述统计，
**不是 MVA 门值**。研究 §4.2 的算术：无 C 时最多 106/120 可评（0.8833），无角色绑定时
最多 64/120——两者都过不了 90/95 门。本目录不写 PASS。

## 6. 未做 / BLOCKED

- **C 通道（VLM 判官）未实现**：无校准件、无真判官调用；`label_absent` 14 条 critical
  事实维持 `UNDECODABLE`。
- **A2 角色解码器未实现**：`bind_scene_nodes` 只是载体；`direction`/曲线/面积类 key
  仍不可判。
- **IMG 臂 BLOCKED**：无图像 provider、且栅格上没有 1e-9 结构化事实（研究 §4.2：上限
  63/120）。
- **MVA 门未判**：没有 120 题的完整事实读数；`mva_gate.py` 未改（通道规则仍是
  `label_absent → A|B` 的旧规则，本轮未动派生文件）。

## 7. 产物 sha256（本轮实测）

见 `SHA256SUMS`（本目录内 `sha256sum` 生成）。
