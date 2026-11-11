# 批次 50 / G4 MVA 门：冻结、两臂体检、确定性读数与门判（2026-11-09）

> **性质**：G4（MVA 视觉质量门）子线收口。**零生图、零真实 Provider 调用、零 VLM 判官调用、零设备安装**。
> 本目录是唯一写盘产物（README + `mva_preflight.json` + 两臂 harness 报告 + 命令原文）；
> 另外新增了两个仓内工具文件（`tools/figure_eval/mva_gate.py`、`tools/figure_eval/test_mva_gate.py`）
> 和一个派生产物（`tools/figure_eval/judge_channel_map.json`）。
> **门阈值零改动**（≥90 / 95 / 每组 90 / 关键错误 0 / p95 30 s / 120 s 全部按
> `tools/figure_eval/figure_eval_gate.py:85-141` 原值引用），**JS 不广告**（证据 §7）。
>
> **时点**：HEAD `a87fc70d`；开工前 `git status --porcelain -- tools/figure_eval/` = **0 行**（干净）。
> 设备 `emulator-5590`（x86_64 / API 34）在线——**本轨未在其上跑任何用例**。

## 0. 门判（先给结论）

**MVA 门：FAIL（不可评；未达标）。** 四道比率门在 120 分母上**没有任何可评样本**，
读数记 `null` 而不是 `0`；关键错误门**不可引用**（计数源 `figure_eval_gate.py:980`
是恒真式）；两条 p95 门无样本。按计划"未达标如实 FAIL"记录。

| 门（原值不动） | 读数 | 分母 | 判定 | 依据 |
|---|---|---:|---|---|
| 首次合格 ≥90% | **null**（0 可评样本） | 120 | NOT MET / NOT EVALUABLE | `mva_preflight.json#readings.*.first_pass_rate` |
| 预算内最终 ≥95% | **null** | 120 | NOT MET / NOT EVALUABLE | 同上 `within_budget_final_rate` |
| 每组最终 ≥90% | 六组**全部 null** | 每组 20 | NOT MET / NOT EVALUABLE | 同上 `per_group_final.groups` |
| 关键错误被接受 =0 | 0 条，但 **`citable=false`** | — | 不可引用 | `figure_eval_gate.py:980` 硬编码；`mva_gate.py` 说明见 §3 |
| 首图 p95 JS ≤30 s | **null**（0 样本） | — | NOT MET / NOT EVALUABLE | `readings.JS.first_image_p95_ms` |
| 首图 p95 IMG ≤120 s | **null**（0 样本） | — | NOT MET / NOT EVALUABLE | `readings.IMAGE_MODEL.first_image_p95_ms` |

**为什么是"不可评"而不是"0%"**：两臂都产不出带事实的产物——
IMG 臂没有图像后端（本机唯一凭据是 `SMB_REAL_PROVIDER_MODEL=deepseek-flash` 的 chat 通道，
无图像生成/编辑 key，见 §4 与 §9）；JS 臂的 Python harness 没有引擎适配器
（`tools/figure_eval/README.md:83-84`：真实设备引擎适配器未落树），而设备侧引擎也没有
"题面 → SceneV1 程序"的作者链，且 `RenderScene → fact_expectations` 提取器不存在。
若把 0/120 写成"命中率 0%"，会把"不可评"伪装成"质量很差"——这是本轨拒绝的写法。

## 1. 冻结与隔离（通过）

```text
$ python tools/figure_eval/mva_gate.py --out <本目录> --write-channel-map \
    --harness-report IMAGE_MODEL=<本目录>/arm-fake/figure_eval_fake_run_report.json \
    --harness-report JS=<本目录>/arm-js/figure_eval_fake_run_report.json
EXIT=0
```

| 检查 | 读数 | 证据 |
|---|---|---|
| manifest sha 校验（复用 `verify_freeze_manifest`，不符即拒跑） | 2/2 文件 sha 与 manifest 逐字节一致 | `mva_preflight.json#fixture.files` |
| 生成器 sha | `build_frozen_tasks.py` 与 manifest `generator_sha256` **一致** | `#fixture.generator_sha_matches=true` |
| 盲测计数/分组 | 120 = 6 组 × 20 | `#isolation.blind_count`、`#isolation.groups_complete=true` |
| 开发集 | 24（6 × 4），**不进分母** | `#isolation.dev_count`、`dev_in_denominator=false` |
| 盲测/开发交集 | **0** | `#isolation.overlap=[]` |
| 夹具自洽（288 条事实：容差/枚举/类型/重复 key） | **PASS，288 条全检** | `#fixture_sanity` |

## 2. 确定性检查与通道路由（通过）

### 2.1 A/B/C 通道（R5 §4.2 规则，确定性生成）

`judge_channel_map.json`：**56,652 B，sha256 `580c2f635ce1f0b5c0e6a05ae11a23b952ebb05354c74e3e487254c8d332f30f`**；
连续两次生成 sha 相同（`CHANNEL_MAP_BYTE_IDENTICAL=YES`，命令见 §8）。

| 量 | 实算 | 与 R5 对照 |
|---|---:|---|
| 事实总数 / critical | 288 / 209 | 一致 |
| A 只 | **94** | 一致（R5 §6.1 合计 94） |
| A\|B | 116 | 一致 |
| A\|C | 40 | 一致 |
| A\|B\|C | 38 | 一致 |
| critical 且 A 只 | **87** | 一致 |
| 含 ≥1 条 critical A 只的题 | **57/120** | 一致 |
| **全部** critical 事实均为 A 只的题 | **27/120** | 一致 |

**一处 R5 正文漂移（本轨实算更正）**：R5 §6.2 写"81（numeric 精确）+ 9（大计数）= **90** 条事实
在栅格上不可核验"。按规则实算，numeric 的 A 只共 **85** 条（容差 1e-9 ×80 + 1e-6 ×5），
加 count>6 ×9 = **94** 条；R5 的 81 是其中 critical 数（76+5），不是总数。critical 87 一致。
差 4 条为**非 critical 的 1e-9 数值事实**。**以实算 94 为准**（`mva_preflight.json#channel_totals`）。

**这条对门的意义**：27/120 题的全部 critical 事实都在像素上不可核验——任何仅凭栅格
（IMG 臂像素 + 判官）的"关键错误=0"都不可能覆盖这 27 题；57/120 题至少有一条这样的
critical 事实。这是**信息不在像素里**，不是判官能力问题。

### 2.2 确定性检查（渲染/几何/文字）——状态 NOT_RUN

读数器按 `render_ok` / `geometry_ok` / `text_ok` 三个字段分别计数；两臂记录均无产物字段：

| 检查 | IMAGE_MODEL | JS |
|---|---:|---:|
| 渲染（产物存在/可解码/引用完整） | 0 | 0 |
| 几何（numeric A 通道，容差内） | 0 | 0 |
| 文字（label_set / label_absent） | 0 | 0 |
| 状态 | **NOT_RUN** | **NOT_RUN** |

夹具自洽检查（§1 的 288 条）是**对 oracle 的检查**，不是对产物的检查，不得混称"确定性通过"。

## 3. 关键错误单列（taxonomy + 登记表）

**登记表实读数：0 条**，但 `citable=false`——此时 0 只因没有任何产物，不构成"没发生关键错误"。

- 现行 harness 的第四门来自 `figure_eval_gate.py:980` `critical_error_accepted: False`（逐记录硬编码），
  与事实核验无关；`accepted = not mismatches`（`:672`）使"接受了的 critical 错误"结构上不可能为真。
  机制自检 `:1080` 的 `critical_mismatch_accepted_zero` 读的就是这个硬编码字段，因而恒真。
  因此**该门今天恒过**，不能作为 MVA 证据（R5 §0-4 同结论）。
  - 本轨新增的读数器把该门改为：显式 `critical_error_register` 条目 + "记录自报接受但事实核验
    发现 critical 不匹配"（`deterministic_mismatch`）计数；样本为 0 或计数源缺失时 `citable=false`。
    机制用例证明"自报 GENERATED_OK + 关键事实错"会被计 1 条并挡住门
    （`test_first_pass_is_recomputed_from_facts_not_from_record_flags`）。
  - **2026-11-09 修复环补记（机器可读）**：`figure_eval_gate.py` 的 `machinery_check` 已新增
    `critical_error_evidence: {field: "exercised_paths.critical_mismatch_accepted_zero",
    citable: false, reason: …}`（同处注释指向 `mva_gate.py`），并给 `:980` 的常量 false 加了
    "不可引用为门证据"注释。标记放在 `exercised_paths` 之外，`machinery.verdict` 的
    `all(exercised_paths.values())` 语义未变。这是标注，不是行为改动：
    旧报告 `reports/figure_eval_fake_run_report.json` 仍是旧形状，任何引用其中
    `critical_mismatch_accepted_zero: true` 的文本都无效；门值仍只由 `mva_gate.py` 产生。
    `tools/tests/test_batch50_repair_invariants.py` 固定该标记，防止回退。
- 关键错误 taxonomy 与 120 集实算（`mva_preflight.json#channel_totals` + R5 §3.2）：

| 码 | kind | 总量 | critical | 判它需要的通道 | 今天能判？ |
|---|---|---:|---:|---|---|
| E0-D 方向 | `direction` | 24 | 24 | A\|C | **否**（无判官；C 通道未校准） |
| E0-N 数值/比例 | `numeric` | 91 | 87 | A 只 85 / A\|B 6 | 否（无 A 提取器、无产物） |
| E0-L 标注 | `label_set` | 96 | 62 | A\|B | 否 |
| E0-X 禁写残留 | `label_absent` | 14 | 14 | A\|B | 否 |
| E0-O 顺序 | `ordering` | 16 | 16 | A\|C | **否**（同 E0-D） |
| E0-C 计数 | `count` | 47 | 6 | ≤6：A\|B\|C；>6：A 只 9 | 否 |

**单列结论**：E0-D（24 条 critical）+ E0-O（16 条 critical）只能走 A|C，而 C 通道今天
**不可入用**（§5）→ 这 40 条 critical 事实在本次运行中全部 **UNVERIFIED**，按 fail-closed
不计合格。E0-N 的 85 条 A 只事实同理（无提取器）。

## 4. 两臂体检（真实运行，均为"机制/可用性"结论，不是质量结论）

| 臂 | 命令 | 结果 | 质量可评？ |
|---|---|---|---|
| IMAGE_MODEL（fake 驱动） | `python tools/figure_eval/figure_eval_gate.py --output-dir …/arm-fake` | `machinery_verdict=PASS`；`real_provider_quality=UNVERIFIED`；`fake first_pass=48.33% within_budget_final=62.50%`（**informational**） | **否**：fake 驱动不出网，门值是机制自检输入 |
| JS（主后端） | `python tools/figure_eval/figure_eval_gate.py --backend JS --output-dir …/arm-js` | `js_backend_status=NOT_AVAILABLE`；`quality_gates=NOT_EVALUABLE`（`JS 路径事实核验未接`）；`dispatches_used=0` | **否**：`JS_NOT_AVAILABLE` |

两臂报告原文在本目录 `arm-fake/`、`arm-js/`；读数器消费后的结论：
`IMAGE_MODEL samples=0 / JS samples=0`，`evaluable=false`（`mva_preflight.json#readings`）。

**"跑图"缺口（如实）**：
1. **IMG 臂无图像后端**：`local.properties` 只有 `SMB_REAL_PROVIDER_BASE_URL/KEY/MODEL` 三键，
   模型 `deepseek-flash`（chat）；无图像生成/编辑端点或 key（`image_keys=0`）。产品侧图像通道
   `OpenAiImageGenerationChannel` 需要配置后的图像后端（`RoomModelTaskRepository.kt:968`
   `imageBackendReady = figureGenerator != null && declaredProvider.supportsImageInput`），
   本环境未配置、也未在设备上做能力探测。
2. **JS 臂无适配器**：`figure_eval_gate.JsEnginePort` 只有 Protocol 定义（`:314`），
   无 Python 侧实现；README `:83-84` 自述"真实设备引擎适配器尚未落树"。
3. **JS 臂无作者链**：120 题是题面 + 事实预期，不含 SceneV1 程序；harness 只有一条探针
   `RENDER_SCENE_PROGRAM_JS`（`:338`），不能代表"模型按题面写程序"的产品路径。
4. **JS 臂无事实提取器**：`RenderScene → fact_expectations` 不存在（README `:82-84`），
   所以即使 RENDERED 也不能判合格。

## 5. VLM judge 状态（NOT RUN；C 通道不可入用）

| 前置件 | 实测 | 结论 |
|---|---|---|
| 判官实现 | `grep -rin "judge\|判官" tools/figure_eval --include=*.py`（排除本轨两文件）→ **0 命中** | 无判官代码 |
| C 通道校准所需的"错值 → 渲染成图"脚本 | `grep -rin "png\|PIL\|matplotlib\|pillow" tools/figure_eval --include=*.py` → **0 命中** | 无法注入可见错误，C 通道不能校准（R5 §4.4 限制） |
| 视觉判官凭据 | 环境 `SMB_REAL_PROVIDER_*` = **0 键**；仓内唯一凭据是 chat 模型 | 判官无法真跑 |
| 判官与被判产物不同源 | 无产物、无判官 | 未达 |

按 R5 §4.4 的 fail-closed 规则：**未校准的通道不可引用**。因此本次运行中 A|C 事实（E0-D 24 条、
E0-O 16 条）与全部 A|B 事实一律 **UNVERIFIED**，不以"判官说对"代替核验。

## 6. 阈值与写死值的边界（未改门）

- `GATES`（`figure_eval_gate.py:85-141`）零改动：≥0.90 / ≥0.95 / 每组 ≥0.90 / =0 / 30,000 ms / 120,000 ms。
- `check_fact_expectations`（`:642-672`）比对语义零改动（含 `label_set` 子集语义——不做“顺手改门”）。
- 120/24 集零改动（sha 与 manifest 一致，§1）。
- `figure_eval_gate.py` 本体**未改**（新能力全部落在新文件 `mva_gate.py`），避免与在飞线整文件抢占。
- **未改** `:980` 的恒真式（属改判定来源，已在 `mva_gate.py` 里以独立登记表实现，harness 本体留给后续接线批）。

## 7. JS 不广告（核验，非声明）

| 证据 | 内容 |
|---|---|
| `core/data/.../FigurePromptAssembler.kt:21-23` | "生产三入口仍硬编码 `IMAGE_MODEL_ONLY`，`availableMethods` 接通是'全门绿后'最后一步" |
| `core/data/.../FigurePromptAssembler.kt:118` | "只广告真实接通的方法：默认 `IMAGE_MODEL_ONLY`" |
| 生产入口 | `RoomDetailedSolutionRepository.kt:256`、`RoomMistakeEditRepository.kt:258`、`ConfiguredCleanImageGenerator.kt:95`、`AttachedImageGenerator.kt:144` 全部 `IMAGE_MODEL_ONLY` |

本轨新增 `mva_gate.py` 不触碰能力广告面；`availableMethods` 未加入 JS。

## 8. 先红后绿（工具机制）

```text
# 红（实现前，行为层）：
$ python -m pytest tools/figure_eval/test_mva_gate.py -q
14 failed in 0.11s
# 失败形态：NotImplementedError（骨架），非导入错误
# 红相原始日志未落盘（本会话实跑，未保存）；上述计数为该次运行的实读

# 绿：
$ python -m pytest tools/figure_eval/test_mva_gate.py -q
16 passed in 0.07s
$ python -m pytest tools/figure_eval/test_figure_eval_gate.py tools/figure_eval/test_mva_gate.py -q
46 passed in 0.15s
```

限制：初始 14 例先红后绿；后续 2 例（harness 报告无事实、夹具自洽）是**同批回归用例**，
先实现后补测，不声称 TDD 红相。

## 9. 未做 / 未验证（引用本目录时必须带上）

1. **零真实生图**：两臂都没有可评样本；120 题的质量结论不存在，不是"失败率高"。
2. **零 VLM 判官**：无实现、无凭据、C 通道无校准件；A|C 的 40 条 critical 全部 UNVERIFIED。
3. **零设备执行**：`emulator-5590` 在线但本轨未安装/未跑；JS 引擎、QuickJS 装载、度量往返均未验。
4. **零真实 Provider 调用**：未使用 `local.properties` 的任何凭据；`deepseek-flash` 无图像能力
   的判断依据是"缺少图像端点/ key"，**未做**能力探测请求。
5. **确定性检查只到夹具层**：产物级渲染/几何/文字检查 NOT_RUN（无产物）。
6. **生成器可复现性未复跑**：`build_frozen_tasks.py` 在临时目录重跑会被仓内 hook 拦（直接写源码限制），
   本轨改为只校验生成器 sha 与 manifest 一致（§1）；"重跑逐字节相同"本轮**未重验**。
7. **Wilson/零事件上界是描述性统计**：`mva_preflight.json#statistics` 为算术结果；
   每组 n=20 时 20/20 的 Wilson 下界仅 0.8389 < 0.90——分组门只能按点估计读（R5 §7.1）。
8. 本轨未改任何产品代码、未改任何门值、未提交。

## 10. 本轨改动清单

| 文件 | 性质 |
|---|---|
| `tools/figure_eval/mva_gate.py` | 新增（读数器：冻结/隔离/通道/登记表/门读数） |
| `tools/figure_eval/test_mva_gate.py` | 新增（16 例机制测试） |
| `tools/figure_eval/judge_channel_map.json` | 新增派生文件（sha `580c2f63…`） |
| `docs/research/evidence/2026-11-09-batch-50/mva/` | 本目录：README、`mva_preflight.json`、`preflight-stdout.txt`、两臂报告 |
| `docs/research/2026-11-09-batch-50-record.md` | 追加本轨小节（其他子线小节未改） |
