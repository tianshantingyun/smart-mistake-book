# 批次 50 · AUD-08 切分门（G3 item 19–23）证据与结论

> 子线：切分门（AUD-08）。计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md` G3 节。
> 本目录产物全部由本会话实跑生成；命令与退出码见 §7。

## 0. 结论（先读）

**门 = FAIL（`check_split_gate.py` exit 1）。门后自动化继续不启用（裁定 37 / 4C③）。**

| 指标 | 值 | 门 | 判定 |
|---|---:|---|---|
| ① 题数完全一致率 | **7/24 = 0.2917**（Wilson95 0.1491–0.4917，描述性） | ≥ 0.9 | **未过** |
| ③ 切残率 | **1/78 = 0.0128**（Wilson95 0.0023–0.0691） | ≤ 0.05 | 门式过 |
| 切残硬红线 `fragmentedQuestions` | **1**（要求 0） | = 0 | **未满足** |
| ② 边界 IoU≥0.5（只度量） | 27/78 = 0.3462 | — | — |

- 报告：`gate-report-b50.json`；证据：`gate-evidence-b50.md`；逐页裁定：
  `rulings/20261108T091855Z-013fad75.json`（`validate_rulings.py` **PASS**，R1–R9；
  判词 consistent 6 / fragmented 1 / inconsistentCount 17 / missingInApp 0 / notEvaluable 0）。
- 金标 v2（sha `03b1eecc…`，等级 **G1**、model-labeled、无人工专家）从产物读，未硬编码。
- **阈值零改动**（0.9 / 0.05 与 `THRESHOLDS.source` 出处随报告落盘）；未改金标、未改口径、未改分母。

## 1. 本轮实际跑了什么

| # | 动作 | 结果 | 产物 |
|---|---|---|---|
| 1 | 生产 CV 与 Python 探针 dev 16 页对拍（`:core:model:test --tests …LayoutStructureDevParityHarness`） | **PASS**（1 test / 0 failures / 0 skipped；`BUILD SUCCESSFUL`） | 见 §7 ep0 |
| 2 | dev 臂 A（LLM-only，无 `layoutHint`）16 页 | ① 4/16，② 22/52，③ 0/52，**0 个通道错误** | `runs/dev-red-01/`、`exports/dev-red-01-export.json` |
| 3 | dev 臂 B（LLM+hint，产品路径）16 页 | ① 4/16，② 16/52，③ 0/52，**3 个通道错误** | `runs/dev-hint-01/`、`exports/dev-hint-01-export.json` |
| 4 | dev 臂 B2（hint + 提示词实验）16 页 | ① 3/16，② 21/52，③ 0/52，1 个通道错误 | `runs/dev-hint-prompt-02/`、`exports/dev-hint-prompt-02-export.json` |
| 5 | **acceptance 8 页（唯一一次）** | ① 3/8，② 11/26，③ **1/26**，2 个通道错误 | `runs/acceptance-b50-01/`、`exports/acceptance-b50-01-export.json` |
| 6 | 合并 24 页 → 判门 | **FAIL**（exit 1） | `app-split-b50-final.json`、`gate-*` |
| 7 | 裁定机械校验 | **PASS** | `validate_rulings.py` 输出（§7） |

逐页对照表（三臂 + acceptance，含每页 decision/status）：`tables/dev-arms.md`。
指标机器口径：`metrics/*-metrics.json`；人读：`metrics/*-metrics.txt`。

## 2. 三臂读法（不得外推）

1. **hint 的净效应在本样本上不可分辨**：A 4/16 vs B 4/16（①相同），② 22/52 vs 16/52（B 更低）。
   16 页单轮采样下 ±2 页的波动与批次 48 已登记的模型随机性同量级；**不得**据单轮声称
   "hint 提升/降低准确率"。研究 §8.4 允许调的 CV 参数本批**未再调**（hint 内容已由
   `LayoutConfig` 默认值固定，dev 对拍 16/16 一致）。
2. **B 臂 3 个通道错误里有 2 个是 `type` 键被契约整轮拒收**（010306/010633 的原始回包以
   `{"type":"json_object",…}` 开头，`OpenAiModelResponseParsers.toAssessment` 的
   `requireOnlyKeys(CAPTURE_ASSESSMENT_WIRE_KEYS)` 拒收 → 整页 0 框）。
   acceptance 的 2 个错误**同样是 `type` 键**（004952 的 `NEED_MORE_IMAGE`、011800 的 `PASS`）。
   这是真实产品缺陷（合法判断被整轮丢弃），但**它对 ① 的实测影响未被本轮证明**：
   - 011800 是单题页，被拒收后 0 框；即使不被拒收也是 `PASS` + 空区域 = 0 框，
     **① 记"一致"在两种情况下相同**（3/8 不会变成 4/8）；
   - 004952 金标 2 题，被丢弃的判断是 `NEED_MORE_IMAGE`（区域必空）→ 两种情况都是 0 框，① 不变；
   - dev 的 010306/010633 在 B2 臂（带修复规则）里分别只拿到 3 框 / 0 框，金标 4 / 3，
     **均不构成"修复后就能一致"的证据**。
   结论：`type` 键缺陷应修，但**不得**用它解释本轮 ① 的差距，也不得据此声称修复能过门。
3. **候选修复在 dev 上实测过，但未进产品**：给提示词加"顶层只返回五个键、不得加 type"后，
   dev 的 `type` 类错误 2 → 0（B2 臂 1 个错误是另一类：NEED_MORE_IMAGE 未配 ADD_IMAGE）。
   但 B2 整体 ① 3/16 低于 B 的 4/16，**净效应未证明为正**，故按"未验证不进发版"回退
   （回退记录见 §5）。该修复留待下一批在**新的 acceptance 切片**上验证。
4. **另一类契约错误**：`NEED_MORE_IMAGE` 未在 `suggestedActions` 带 `ADD_IMAGE`
   （B 臂 011017、B2 臂 005605、acceptance 无）→ 整页 0 框。契约本身不放宽
   （`CaptureAssessment` init 既有不变量）；改进方向是提示词，同样未经验证、未进产品。

## 3. 失败页保留（门失败时必须能指出它们）

- **被切残的 1 题**：`IMG20260810004353.jpg`（acceptance；金标 2 题，应用 2 框，
  其中 1 道金标题被切成 ≥2 块 → ③ 1/26、硬红线 1）。逐框证据见 gate 报告 `rulings`。
- **题数不一致 17 页**（gate 报告 §5 / `rulings`）。其中 **8 页为 0 框**（按成因）：
  - `004854`/`005605`：`NEED_MORE_IMAGE`（模型判"未拍全"，金标记为可见完整题）；
  - `011017`：`NEED_MORE_IMAGE` 未配 `ADD_IMAGE` → `CaptureAssessment` 不变量拒收；
  - `004952`/`010306`/`010633`：`type` 键被 `requireOnlyKeys` 整轮拒收；
  - `005144`：hint 臂返回 5 个互相重叠的区域（其中一个是整页框）→
    `isUsableSplitRegionSet` 判不可用 → 0 框（A 臂同一页给 5 框）；
  - `011126`：`PASS`（模型把 2 题读成 1 题）。
- **题数差 ±1–2 的 9 页**：`004511`(3→2)、`005301`(3→2)、`005711`(3→2)、`010202`(3→2)、
  `010034`(6→5)、`011358`(5→4)、`011248`(5→4)、`010859`(4→3)、`011649`(3→2)。
- 单题页 `004304`/`011800` 的"0 框 = 一致"是口径 v2 的定义（单题页不拆分=期望），
  与逐页主判词的 `MISSING_IN_APP` 不是同一口径（GATE.md §3）。

## 4. acceptance 一次性的口径限制（必须随门值一起引用）

1. **本切片在批次 48 已被消费过一次**：`docs/research/evidence/2026-11-07-batch-48/splitter/`
   `acceptance-once-01`（8 页，① 5/8，③ 3/26）。批次 48 的逐页失分模式已落盘且可读，
   因此本轮的 acceptance **不是首次盲测**，held-out 属性不可恢复；本轮如实称为
   "同一 8 页在新配置下的第二次观测"，**不宣称**"未参与调参页面的准确率"。
   跑前冻结与承诺见 `acceptance-protocol.md`（写作时点早于运行）。
2. v2 的 `heldOutUnusedForTuning=true` 是自证字段；结合第 1 条，在 batch 50 语境下不成立
   （GATE.md §2.1 的机检不可项）。
3. acceptance 8 页 **0 张双栏** → 栏逻辑在验收集上是盲区（研究 §8.1）。
4. acceptance 8 页 ① 是 **8/8 全或无**（7/8=0.875 < 0.9）；本轮 3/8 距门很远。
5. 与批次 48 同输入的对照（**不作优劣结论**）：批次 48 合并门 ① 10/24=0.4167、③ 3/78=0.0385、
   hardRedLine 3；本轮 ① 7/24=0.2917、③ 1/78=0.0128、hardRedLine 1。两次都 FAIL；
   差异幅度在模型随机性 + 通道错误个数的量级内，不构成"变好/变坏"的证据。

## 5. 改动与未改（协作边界）

**新增（本子线）**

- `core/data/src/test/kotlin/…/model/GoldenSplitEvalHintChannelHarness.kt`（新文件）：
  hint 臂评测通道。与批次 48 已提交的 `GoldenSplitEvalChannelHarness.kt` 同构，
  **差异仅**为 `input.layoutHint`；`requestBody`/`parseCompletion`/`isUsableSplitRegionSet`
  逐字复用。默认 `assumeTrue` 跳过（不伪造通过）。
- 评测脚本（`build/`，gitignored）：`prepare_hint_gray.py`、`assemble_export.py`、
  `make_golden_slice.py`、`merge_final_export.py`、`metrics.sh`、`report_tables.py`、
  `run_split_eval_hint.sh`。
- 本证据目录全部文件。

**未改**

- 生产代码**零改动**：`git diff` 对 `core/` 只显示 CV 子线既有改动
  （`OpenAiModelTaskAdapters.kt` 的 hint 追加 hook、`OpenAiCompatibleModelGateway.kt`、
  `ModelTasks.kt`、新增 `PageLayoutHint.kt`/`LayoutStructure.kt`），本子线未再动它们。
- 提示词实验（两条规则）**已回退**：先红后绿证据见 §7 ep5/ep6（红：2 failed；
  绿：BUILD SUCCESSFUL），dev 实测无净收益后按原字节还原，实验用例同时删除。
- 金标、门阈值、口径、分母、`LayoutConfig` 默认值、模型参数、`temperature`：全部未动。
- 他线在飞文件未整文件抢占；本子线只加新文件。

## 6. 未验证 / 风险（引用门值时必须带上）

1. **通道 ≠ 设备路径**：JVM harness 直连 HTTPS（同一 `requestBody` 构建器、同一
   `parseCompletion`），图像出网副本由 PIL 生成、hint 灰度由 PIL 生成（非 Android
   Skia/BitmapFactory 字节）。差异已写进每次 run 的 `manifest.divergences`。
2. **单轮采样**：每个臂只跑一轮；① 的 ±2 页噪声与批次 48 登记一致；不报均值、不报显著性。
3. **模型版本只有名称**：`deepseek-flash`（provider 自称 DeepSeek-V4.1-Flash）；
   名称漂移会让跨批次比较失效，未取 provider 侧版本号。
4. **金标 G1**：单模型盲标、无人工专家；`004854` 的坐标系与像素系不齐（研究 §3.6），
   其金标框在旋转 90° 系里——本批未修该页 `frame`，也未从分母剔除（如实保留）。
5. **acceptance 已被批次 48 消费**（§4.1）；本轮是第二次观测。
6. **未跑真机、未跑 2h soak、未跑产品端设备路径**（计划禁令 `connectedLocalFirstDebugAndroidTest`
   未触发）。
7. **hint 不进请求指纹**（CV 子线已登记）：`ModelTaskRequest.layoutHint` 仍为 null，
   网关于出网副本现算；CV 参数变更后同一 fingerprint 可能对应不同 wire 文本。

## 7. 复跑命令（仓库根，Git Bash）

```bash
# ep0 CV × 探针对拍（16 页；需 build/layout-probe/gray 夹具）
./gradlew.bat :core:model:test --tests "com.tingyun.smartmistakebook.core.model.layout.LayoutStructureDevParityHarness" --console=plain

# ep1 dev 臂 A（无 hint）
bash build/batch50-split/run_split_eval_hint.sh dev-red-01 build/batch50-split/runs/dev-red-01 build/batch50-split/dev-pages.txt nohint
# ep2 dev 臂 B（hint）
bash build/batch50-split/run_split_eval_hint.sh dev-hint-01 build/batch50-split/runs/dev-hint-01 build/batch50-split/dev-pages.txt hint
# ep3 acceptance（只跑一次；本批已消费，复跑即破坏一次性红线）
bash build/batch50-split/run_split_eval_hint.sh acceptance-b50-01 build/batch50-split/runs/acceptance-b50-01 build/batch50-split/acceptance-pages.txt hint

# 度量
bash build/batch50-split/metrics.sh build/batch50-split/runs/dev-hint-01 build/batch50-split/slices/golden-v2-dev-slice.json build/batch50-split/reports dev-hint-01-export.json dev-hint-01-metrics

# 合并 + 判门 + 裁定校验（已存在即拒绝覆盖）
python -I build/batch50-split/merge_final_export.py build/batch50-split/reports/dev-hint-01-export.json build/batch50-split/reports/acceptance-b50-01-export.json acceptance-b50-01 docs/research/evidence/2026-11-09-batch-50/split-gate/app-split-b50-final.json
python -I tools/split_golden/check_split_gate.py --golden tools/split_golden/tables/split_golden_v2.json \
  --app docs/research/evidence/2026-11-09-batch-50/split-gate/app-split-b50-final.json \
  --report-json docs/research/evidence/2026-11-09-batch-50/split-gate/gate-report-b50.json \
  --evidence-out docs/research/evidence/2026-11-09-batch-50/split-gate/gate-evidence-b50.md \
  --rulings-out docs/research/evidence/2026-11-09-batch-50/split-gate/rulings     # exit 1 = FAIL
python -I tools/split_golden/validate_rulings.py --file docs/research/evidence/2026-11-09-batch-50/split-gate/rulings/20261108T091855Z-013fad75.json
```

ep5/ep6（提示词实验的红→绿，随后回退，仅作过程证据）：

```bash
# 红：新增 CaptureAssessmentPromptContractTest（3 tests / 2 failed）
./gradlew.bat :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.model.CaptureAssessmentPromptContractTest" --console=plain
# 绿：提示词加两条规则后（BUILD SUCCESSFUL；实验用例随后随回退删除）
./gradlew.bat :core:data:testDebugUnitTest --tests "com.tingyun.smartmistakebook.core.data.model.CaptureAssessmentPromptContractTest" --tests "com.tingyun.smartmistakebook.core.data.model.CaptureAssessmentLayoutHintPromptTest" --tests "com.tingyun.smartmistakebook.core.data.model.OpenAiCaptureAssessmentAdapterTest" --console=plain
```

## 8. 产物 sha（详见 `MANIFEST-SHA256.txt`）

| 产物 | sha256（前 12） |
|---|---|
| 金标 v2 | `03b1eeccbbde…` |
| 24 页门输入 `app-split-b50-final.json` | `0d9f375988e8…` |
| dev B 导出 `dev-hint-01-export.json` | 见 `exports/*.sha256` |
| acceptance 导出 `acceptance-b50-01-export.json` | 见 `exports/*.sha256` |
