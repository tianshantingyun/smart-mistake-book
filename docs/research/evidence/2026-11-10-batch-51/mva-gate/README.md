# MVA 门执行（批次 51 / G4 T29 · 子线「MVA 跑分判门」）

> 范围：JS 臂 120 题：作者链 → 设备渲染 → 确定性事实 → VLM 盲判（C 通道 + 注入错值校准）
> → `mva_gate` 六道门读数。IMG 臂无 provider 记 BLOCKED。
> **结论：FAIL（不是 PASS）。** 见 §7 的失败推导（上界 95/120 < 108/120，与未判事实无关）。
> 阈值 90/95/90/0、p95 30 s/120 s 未改；分母 120 未改；未伪造任何 PASS。

## 1. 本轮改动与"消灭的具体失败"

| 文件 | 性质 | 消灭的具体失败 |
|---|---|---|
| `core/data/src/main/kotlin/.../model/FigurePromptAssembler.kt` | 产品修复 | `JS_STATIC_SAMPLE` 的 `stroke:'#26354a'` 不可编译（`requireStroke` 要求对象）、path 无 `fill`（KD-78 ①）；契约正文未写 fill/stroke/label 必填字段、未写 `metrics[i].contentId` 约定——模型照样例写 ⇒ 120 题 RENDERED 0/104（批次 51 前序轨实证）。样例改为可编译的 path(fill+stroke 对象)+label，契约补必填字段与 contentId 来源 |
| `core/data/src/androidTest/.../visual/MvaJsArmAdapterInstrumentedTest.kt` | 设备适配器对齐 | 适配器 contentId 曾为 `c<index>`、生产 `VisualContentMeasuring` 为 `c<index>-<digest8>`——作者硬编码 `'c0'` 在设备能过、到生产会 `SCENE_REF_CONTENT`。改为生产同形 |
| `tools/figure_eval/prompt_sample_compile_probe.mjs` | 新增（探针） | 产品样例与真实编译器之间没有逐字节回归面 |
| `tools/figure_eval/test_prompt_sample_compiles.py` | 新增（回归测试） | 样例不可编译只能靠人肉发现；测试从 Kotlin 源提取样例，交真实 `visual-sdk.js`+`visual-compiler.js` 编译 |
| `tools/figure_eval/author_program_compile_check.mjs` | 新增（分诊） | 120 题逐题上设备前无法区分"程序协议错/SceneV1 编译错"；与设备同源资产做离设备编译分诊（不是门证据） |
| `tools/figure_eval/mva_vlm_judge.py` | 新增（C 通道） | direction/ordering/count/label_absent 无视觉判官；本工具盲判 PNG（提示词不含 expected/身份），先做注入错值校准；SSRF 边界：仅 https + 公网单播 + 禁重定向 |
| `tools/figure_eval/test_mva_vlm_judge.py` | 新增（9 例） | 判官通道越界、prompt 泄漏 expected、规范化错误、校准"正确图被误记错值" |
| `tools/figure_eval/mva_gate_run.py` | 新增（读数装配） | 适配器/提取器/判官三类产物没有合成 `mva_gate.gate_reading` 记录的入口 |
| `tools/figure_eval/mva_gate.py` | 最小修复 | `python -I tools/figure_eval/mva_gate.py`（计划点名命令）因 `-I` 不前置脚本目录而 `ModuleNotFoundError`；补 `sys.path` 注入（与 `mva_facts_run.py` 同形） |

未改：任何门阈值/分母、冻结夹具、`check_fact_expectations` 比较语义、6C-2、JS 广告/注入状态、
`app/`/`feature/` 他线文件。**JS 注入/广告状态未动**：生产装配仍是 `visualEngine=null`、
`capabilities=IMAGE_MODEL_ONLY/NONE`（`ConfiguredCleanImageGenerator.kt:95` 等），符合用户裁定
「MVA 未过 → 不注入、不广告」；本轮的提示词修复在 JS 未启用前不改变任何生产行为。

## 2. 产品提示词修复：先红后绿

先红（样例原样交真实编译器）：

```
$ node docs/research/evidence/2026-11-10-batch-51/mva/product-sample-probe/probe_product_sample.mjs
{"compiles":false,"error":"SCENE_FIELD_TYPE: scene.root.children[0].stroke must be an object"}  EXIT=1
```

修复后（新探针从 Kotlin 源码逐字提取样例，交同一套生产资产执行）：

```
$ python -m pytest tools/figure_eval/test_prompt_sample_compiles.py -q
1 passed
$ node tools/figure_eval/prompt_sample_compile_probe.mjs
{"compiles":true,"ops":3,"pathOps":2,"labelOps":1,"stats":{"nodes":4,"pathCommands":17,"labels":1,"ops":3}}
```

提示词契约 JVM 回归（JS 广告、样例、无交互面字面量、IMAGE_MODEL_ONLY 逐字节不变）：

```
$ ./gradlew --console=plain :core:data:testDebugUnitTest \
    --tests "...FigurePromptCapabilityInjectionTest" \
    --tests "...FigurePromptParityTest" --tests "...FigureToolPromptInjectionTest"
BUILD SUCCESSFUL（日志：logs/gradle-prompt-tests-2.log）
```

**效果（dev 24，prompt 调参允许集）**：同一作者 harness、同一模型/温度：
修复前 20 个 OK 程序 **6 编译通过**（`SCENE_STYLE_COLOR` 6、`SCENE_FIELD_UNKNOWN` 4、
`SCENE_PATH_ARGS` 2、`SCENE_SDK_ARG` 2）；补契约后 19 个 OK 程序 **18 编译通过**（唯一失败为
程序自身 `Unexpected token ';'`）。120 题上 98 个 OK 程序 **95 编译通过**（96.9%），
95/98 程序使用 `metrics[i].contentId`（与生产 contentId 形状一致）。

## 3. 作者链（120 题，`deepseek-flash`，temperature 0.1）

命令（凭据只从 `local.properties` 读出到环境变量，不落日志；`--no-daemon` 单次 JVM）：

```
MVA_AUTHOR_TASKS=<abs>/tools/figure_eval/tasks_blind_120.json \
MVA_AUTHOR_OUT=<evidence>/author-blind-2 MVA_AUTHOR_RUN_ID=mva-author-blind-120-r2 \
MVA_AUTHOR_CONCURRENCY=8 SMB_REAL_PROVIDER_{BASE_URL,KEY,MODEL}=... \
./gradlew --no-daemon :core:data:testDebugUnitTest --tests "...MvaFigureAuthorHarness" --rerun
```

| 状态 | 数量 | 说明 |
|---|---|---|
| OK | **98** | 产出可送设备的程序 |
| INVALID_PROPOSAL | 17 | 严格 proposal 解析拒收；抽查为 `NEED_SOURCE` 缺字段（去手写/源图重建为主） |
| NOT_GENERATE | 5 | `decision=NEED_SOURCE`（无源图/结构化事实） |

分组 OK：proportion 20/20、mechanics 20/20、flow 20/20、natural 20/20、
source_rebuild 16/20、handwriting_removal **2/20**（其余 NEED_SOURCE/无效 proposal）。
与上一轮（OK 104）的差异是模型采样波动 + 更严格契约；不据此调 120 集。

## 4. 设备渲染（emulator-5590，sdk_gphone64_x86_64 / SDK 34）

```
# -> :core:data:assembleDebugAndroidTest；adb install -r -t
adb shell am instrument -w -e class ...MvaJsArmAdapterInstrumentedTest \
  -e mvaProgramsDir /data/user/0/<pkg>/files/mva/js-arm/input \
  -e mvaOutDir /data/user/0/<pkg>/files/mva/js-arm <pkg>/androidx.test.runner.AndroidJUnitRunner
OK (1 test)  Time: 5.033
```

`device-blind-2/out/manifest.json`：98 程序 → **RENDERED 95、REJECTED 3**（与离设备编译分诊
逐一吻合：`SCENE_PATH_NOT_M_START` 2、`SCENE_NODE_COUNT` 1）。PNG 95 张（720×480，
1.9–42 KB，中位 8.8 KB）；目视抽查：细胞结构图（标签/箭头/形状齐全）、数轴图（刻度+“3:1”），
非空白。确定性检查（全部 95 个 RENDERED）：render 95、geometry 95、text 95。

## 5. 事实判定

### 5.1 确定性提取（`fact_extract`，A1 类）

`python -I -X utf8 tools/figure_eval/mva_facts_run.py --tasks ... --adapter-out .../device-blind-2/out`：

```
rendered 95, evaluable 47, accepted 25, evaluable_coverage 0.3917, gate_readable=false
undecodable_by_key: point_x 5, point_y 5, step_order 7, segment_length_ratio 4, ascending_order 1,
                    + A2: arm 6, area 5, curve 5, phase 5, u 5, resultant 3, wave 3,
                    + direction 24 条（3+10+2+2+7）
```

**新发现（真实作者场景上的 A1 解码器缺口，不是 A2 缺口）**：
- `step_order` 7 题：卡片内标签数不唯一（如 `[2,0,2,0,2,0]`），解码器拒绝猜；
- `point_x`/`point_y` 各 5 题：图上没有 ≥2 个有跨度的数值刻度标签（模型画了坐标轴但未标刻度）；
- `segment_length_ratio` 4 题：主游程只有 1 段（未画成两段比例）；
- `ascending_order` 1 题：可排序成员 <2。
这些不是"模型画错"，是提取器在真实作者产物上的覆盖缺口——记 `UNDECODABLE`，不记 0 分。

### 5.2 VLM 判官（C 通道，`deepseek-flash` 视觉）

校准（先于正式判分；6 张设备渲染的注入错值图对，`calibration/judge/calibration.json`）：

| 项 | 结果 |
|---|---|
| 判官读出图上真实内容 | **6/6** |
| 正确图被门函数接受 | **3/3** |
| 注入错值被门函数拒绝（判官如实读出） | **3/3** |
| `passed` | **true** |

正式判分：20 题有 C 通道事实、均 RENDERED；20 次调用 **OK 20/20**，回答 **32 条事实**、
未答 0。判官提示词只含题面与问题（不含 expected/task_id/group；单测钉住），
每题任务用匿名 `T<sha10>` 标识。

### 5.3 合并可评性

`mva_gate_run.py`（只有 critical 事实全部可判的题进入门读数）：

| 指标 | 值 |
|---|---|
| 可评题 | **59/120 = 0.4917** |
| 已判合格（首次=最终，无重试） | **32/120 = 0.2667** |
| 关键事实仅 A 通道（结构化）的 critical facts | **87/209**（channel map） |
| 因 A2 numeric 不可判的 critical facts | **27**（120 集全量：arm 6、area 6、curve 6、u 5、resultant 4；已渲染题上 24 条） |

## 6. 门读数（`mva_gate.gate_reading`，阈值原样）

| 门 | 读数 | 阈值 | 判定 |
|---|---|---|---|
| 首次合格 | 32/120 = **0.2667** | ≥0.90 | **FAIL** |
| 预算内最终合格 | 32/120 = **0.2667** | ≥0.95 | **FAIL** |
| 每组最终（20/组） | flow 2、handwriting 0、mechanics 0、natural 14、proportion 1、source 15 | 每组 ≥0.90 | **FAIL（6/6 组）** |
| 关键错误被接受 | 0（无自我接受阶段，结构性 0；见 §7.2） | =0 | 数值满足，但**不可作独立能力证据** |
| JS 首图 p95（端到端，门判口径） | **72.9551 s**（32 个合格样本：作者 `elapsed_ms` + 设备渲染延迟） | ≤30 s | **FAIL** |
| JS 首图 p95（仅适配器渲染，并列读数） | 35.15 ms（32 样本） | ≤30 s | 渲染路径满足；**不参与门判** |
| IMAGE_MODEL 臂 | 无记录（无图像 provider） | — | **BLOCKED / NOT_EVALUABLE** |

p95 口径（修复环修正）：设计 §6 写的是"首张合格图 p95"（请求→首图）。修复环前
`mva_gate_run.py` 把适配器渲染延迟（35.15 ms）塞进 `latency_ms`，机器可读门因此记
`met=true`；本批修复环已改为 `author_elapsed_ms + render_latency_ms` 的端到端读数
（72.9551 s，`met=false`），渲染侧 35.15 ms 只作 `render_only` 并列读数保留，不参与门判。
`gate/mva_preflight.json` 与 `gate/readings_digest.json` 均已按此重算；阈值 30 s 未改。

## 7. 为什么是 FAIL（不是"不可评"、更不是 PASS）

### 7.1 与未判事实无关的上界

- 作者 OK 98/120、设备 RENDERED **95/120**。即使这 95 题全部事实合格，
  首次合格率上界 = **95/120 = 0.7917 < 0.90**（阈值需 108 题）。**首次门已必然 FAIL。**
- 最终合格阈值 114 题；上界 95 → **必然 FAIL**。
- 每组上界：handwriting 作者 OK 仅 **2/20**、source_rebuild **16/20** → 两组必然 <18/20；
  mechanics 的 15 题被 A-only numeric 阻塞 → 上界 5/20。**每组门必然 FAIL。**
- 因此不依赖"59 题可评"这一部分性；未判事实只会让真实值更低，不会翻转结论。

### 7.2 关键错误门为何不能当能力证据

本轮流水线没有"自我接受后交付"阶段（作者程序直接进设备/事实核验），
`mva_gate` 的 `deterministic_mismatch` 与 `critical_error_register` 均为空 ⇒ 计数 0 是
结构性结果，不是"模型没有犯关键错误"的独立证据。如实登记，不据此加分。
`readings_digest.json` 的 `critical_errors.citable=true` 只表示"有可评样本、该字段可机械读出"，
**不**表示这个 0 是能力证据；修复环已在该文件加 `interpretation` 说明，字段值按门工具原样保留。

### 7.3 结构性阻断（下一步要补的）

1. **A2 角色解码器**：`arm_length_ratio`、`u_at_half_height`、`curve_value_at_x_equals_h`、
   `area_W_over_F0h_at_half`、`resultant_amplitude`、direction、phase_order、wave_count。
   A-only numeric 共 **27 条 critical facts**（已渲染题上 24 条）；A|C 的 direction **24/24**
   已由 C 通道回答（C 事实 32 条 = direction 24 + phase_order 5 + wave_count 3）。
2. **A1 解码器在真实场景的鲁棒性**：`step_order` 卡片标签数不唯一、`point_x/y` 无刻度、
   `segment_length_ratio` 单游程——需要按真实作者布局扩展，而不是放宽断言。
3. **作者成功率**：handwriting_removal 2/20、source_rebuild 16/20；NEED_SOURCE 的产品语义
   （无源图时是否允许结构化重绘）需要产品裁定，评分侧不能替它决定。
4. **IMG 臂**：无图像 provider → BLOCKED（外部）。

## 8. 方法学偏差（必须随读数一起引用）

- **120 集已是"回归集"**：本批前序轨已用 120 集观测到产品样例缺陷并据此修 prompt；
  设计 §6 :91 明确"若按盲测失败样例修 prompt，旧集转回归集，启用判定须使用新的未见盲测集"。
  本仓库没有第二套未见 120 集；**本轮读数只能作回归集读数，不能作启用 PASS 的依据**。
  （本会话的 prompt 迭代只在 dev 24 上做；但样例缺陷的首次观测来自 120 集，按最严口径登记。）
- 作者链与生产链路的差异（harness 自登记）：直连 HTTPS 不走网关授权/ledger；system 消息为
  harness 一行；8 路并发（产品串行）；每题只派发一次（无重试/修复环）——所以"预算内最终"
  与"首次"相同，95% 门的重试语义未被检验。
- 设备适配器与生产链路的差异：未走 app `VisualJsClientEngine` + `:visual_js` 隔离进程/AIDL 队列；
  度量器是测试内 `Paint` 度量（非 `AndroidVisualContentMeasurer`，math 不支持）；每程序新建引擎。
  contentId 形状已对齐生产（`c<index>-<digest8>`）。
- 判官为单模型（deepseek-flash）、单温度（0.0）、单轮、无人工复核；C 通道校准只覆盖
  direction/ordering/label_absent 的 6 个注入对，不外推到其他图型。
- **判官与作者链同源**：作者链模型也是 `deepseek-flash`（temperature 0.1），判官
  temperature 0.0；两者只差温度/提示，不是独立模型。因此 32/120、每组 6 组读数与
  6 对校准都是**同源自证**，不得作独立能力证据；`readings_digest.json` 已加
  `independence.same_model=true`。门 FAIL 不依赖判官：RENDERED 95/120 < 108 已使
  首次门必然 FAIL（§7.1）。
- 120 集事实预期为 **model_annotated，无人工专家**（夹具自带 `review_source` 声明）。

## 9. 复跑命令（串行；凭据只从环境变量）

```bash
# 1) 产品样例回归
python -m pytest tools/figure_eval/test_prompt_sample_compiles.py tools/figure_eval/test_mva_vlm_judge.py -q
python -m pytest tools/figure_eval -q          # 82 passed

# 2) 作者链（120）
MVA_AUTHOR_TASKS=$PWD/tools/figure_eval/tasks_blind_120.json \
MVA_AUTHOR_OUT=$PWD/docs/research/evidence/2026-11-10-batch-51/mva-gate/author-blind-2 \
MVA_AUTHOR_RUN_ID=mva-author-blind-120-r2 MVA_AUTHOR_CONCURRENCY=8 \
SMB_REAL_PROVIDER_BASE_URL=... SMB_REAL_PROVIDER_KEY=... SMB_REAL_PROVIDER_MODEL=deepseek-flash \
./gradlew --no-daemon :core:data:testDebugUnitTest --tests "...MvaFigureAuthorHarness" --rerun

# 3) 设备（一次构建；install/push/instrument 见 §4）
./gradlew --no-daemon :core:data:assembleDebugAndroidTest
adb -s emulator-5590 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
# programs.tar -> /data/local/tmp -> run-as tar -x 到 files/mva/js-arm/input
adb -s emulator-5590 shell am instrument -w -e class ...MvaJsArmAdapterInstrumentedTest \
  -e mvaProgramsDir /data/user/0/<pkg>/files/mva/js-arm/input \
  -e mvaOutDir /data/user/0/<pkg>/files/mva/js-arm <pkg>/androidx.test.runner.AndroidJUnitRunner

# 4) 事实 + 判官 + 门
python -I -X utf8 tools/figure_eval/mva_facts_run.py --tasks ... --adapter-out .../device-blind-2/out --out .../facts-without-judge
python -I -X utf8 tools/figure_eval/mva_vlm_judge.py --calibration --calibration-tasks .../calibration/calibration_tasks.json \
  --adapter-out .../calibration/device-out/out --out .../calibration/judge
python -I -X utf8 tools/figure_eval/mva_vlm_judge.py --tasks .../tasks_blind_120.json \
  --adapter-out .../device-blind-2/out --out .../judge-blind-2 --concurrency 6
python -I -X utf8 tools/figure_eval/mva_gate_run.py --tasks .../tasks_blind_120.json \
  --adapter-out .../device-blind-2/out --judge-out .../judge-blind-2 \
  --author-manifest .../author-blind-2/author_manifest.json --out .../gate
python -I -X utf8 tools/figure_eval/mva_gate.py --out .../preflight-cli
```

## 10. 未验证 / 残余风险

- 未跑 app 侧 `VisualJsClientEngine`/`:visual_js` 隔离进程与 AIDL 队列（见 §8）。
- 未跑生产 `AndroidVisualContentMeasurer`（math 不支持；contentId 形状已对齐）。
- 未验证跨模型/跨温度、重试/修复环、2h soak；判官无人工复核。
- 未生成新的未见 120 盲测集（设计 §6 的启用前提未满足）。
- `FigurePromptAssembler` 的 `policyVersion` 未随正文改动升版（`figure-generation-policy-v1`）；
  这是产品契约版本纪律的残余风险，需产品侧裁定（升版会影响 proposal 兼容面）。
- IMG 臂 BLOCKED：无图像 provider。

## 11. 目录

```
mva-gate/
  README.md                      # 本文件
  SHA256SUMS                     # 本目录逐文件 sha256（不含自身与 .mimosa/ hook 状态）
  author-blind-2/                # 作者链：author_manifest.json、authors/、programs/、compile_check.json
  author-dev-smoke/ author-dev/ author-dev-2/  # dev 冒烟 + 修复前(6/20 编译)/后(18/19)对照
  device-blind-2/out/            # 设备产物：manifest、95×(record/drawops/scene/png)
  facts-without-judge/           # A1-only 事实读数（跨查）
  judge-blind-2/                 # 判官：judge_records.json、judge_summary.json、raw/
  calibration/                   # 6 个注入对程序、设备图、calibration.json
  gate/                          # task_records、gate_records、mva_preflight、summary、readings_digest
  preflight-cli/                 # 计划点名命令 `mva_gate.py` 的 preflight 输出
  logs/                          # Gradle/仪器化日志（不含凭据）
```
