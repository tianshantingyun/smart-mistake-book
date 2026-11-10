# 批次 51 · AUD-08 dev 16 页切分调参（2026-11-10）

> 范围：**只跑 dev 16 页**（`split_golden_v2.json` 的 `split.dev`）。acceptance 8 页在本次调参中
> **未被运行，也未读取其图像或金标框**（派生的 dev 切片脚本只读 `split` 块做交集 0 断言）；
> 门阈值（① 0.9 / ③ 0.05）与分母、金标 v1/v2、硬红线均未改。切题仍只走模型识别，未引入任何
> CV/几何算法或版面提示。

## 1. 通道与输入（可复算）

| 项 | 值 | 出处 |
|---|---|---|
| 通道 | JVM harness：真实 `OpenAiModelProtocol.requestBody` → 直连 Provider → 真实 `parseCompletion`（含 `CaptureAssessment` 契约）→ 真实 `isUsableSplitRegionSet` 消费判据 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/GoldenSplitEvalChannelHarness.kt:63-258` |
| 模型 / 端点 | `deepseek-flash` / `https://api.deepseek.com/chat/completions`，`temperature=0.1`、`response_format=json_object`、`detail=high` | `OpenAiModelProtocol.kt:192,198`；harness env `SPLIT_EVAL_MODEL` |
| 凭据 | 只从 gitignored `local.properties` 读入进程环境；证据只记 sha8 `cd10efbc` | `build/batch51-split/run_split_eval.sh`（脚本内断言 sha8/model/base） |
| 图像出网副本 | `EgressImageBudget` 口径（长边 2048、JPEG q88、超 1.5MiB 回落 q70；PIL BILINEAR） | `build/batch48-ws2/prepare_egress.py`；harness 用真实 `EgressImageBudget.scaledSize` 复算断言 |
| 金标 | `tools/split_golden/tables/split_golden_v2.json`，sha256 `03b1eecc…1e18e54`（与旁车一致） | `build/batch51-split/make_dev_slice.py` 实读断言 |
| dev 切片 | 16 页 / 52 题，sha256 `eb75fc5422549bbafedacd45d04c8647eb7b9fccf5f59ce04de66d9aedd78ce9`；页序 == `split.dev`，与 acceptance 交集 0（脚本硬断言） | 同上 |
| 原始回包 | 逐页 JSON 新增 `responseRaw`（全文，≤200000 字符；模型输出无凭据） | `GoldenSplitEvalChannelHarness.kt:60,193,217,255`（批次 51 新增；旧 `responseHead` 只留 1600 字符，无法复盘契约拒收） |

通道差异（沿用批次 48 的如实登记，未改）：①传输为直连 HTTPS POST，非 gateway 授权/预算管线
（请求体字节同源、解析同一函数）；②像素重采样是 PIL，不是 Android Skia（字节不同）；③无设备
DB 落库——导出由 `assemble_export.py` 组装成 `export_split_eval.py` 同 schema，来源块标
`golden-split-eval-harness-v1（非设备 DB 导出）`。门值不外推为设备产品路径准确率。

运行命令（每轮同一条，页清单只来自 dev 切片）：

```
bash build/batch51-split/run_split_eval.sh <run-id>
# → gradle :core:data:cleanTestDebugUnitTest :core:data:testDebugUnitTest
#   --tests ...GoldenSplitEvalChannelHarness（--no-daemon，串行）
# → assemble_export.py + measure_split_accuracy.py（口径 v3）→ 本轮 metrics.txt/json
```

## 2. 轮次总表（dev 16 页，单轮采样）

| # | run | 提示词 | 字符 | promptSha256 | ① 题数一致 | ② IoU≥0.5 | ③ 切残 | 弃答 | 通道不可评 | 导出 sha256 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `dev-v3-01` | v3（批次 51 前序阶段） | 1710 | `2fa06897…` | **1/16 = 0.0625** | 9/52 = 0.1731 | 1/52 = 0.0192 | 8 页 | 0 | `b0b0d6e6…` |
| 2 | `dev-v31-01` | v3.1 | 1955 | `2c698d1b…` | **9/16 = 0.5625** | 26/52 = 0.5000 | 3/52 = 0.0577 | 0 | 0 | `d5146ca5…` |
| 3 | `dev-v32-01` | v3.2 | 2137 | `591f7c63…` | **13/16 = 0.8125** | 33/52 = 0.6346 | 2/52 = 0.0385 | 0 | 0 | `ff4df058…` |
| 4 | `dev-v33-01` | v3.3（最终） | 2204 | `7f92b456…` | **14/16 = 0.8750** | 36/52 = 0.6923 | 2/52 = 0.0385 | 0 | 0 | `2bf83d48…` |
| 4R | `dev-v33-02` | v3.3 复测（同提示词） | 2204 | `7f92b456…` | **12/16 = 0.7500** | 31/52 = 0.5962 | 3/52 = 0.0577 | 0 | 0 | `5df4f6a7…` |

完整 sha256：

- 提示词：v3 `2fa068970407c2fc9dad7ec09aa4b99f6010c8fb705bcfad88cef2e212e854c4`；
  v3.1 `2c698d1b1d22b168f795135907a662fffccfacc5eea5a9b8c97c55f75ba5ab98`；
  v3.2 `591f7c631afdc89ed59467329cbae129bfbc978eb6ec76820b2610c0aefcbd5a`；
  v3.3 `7f92b456dea4bbdd5d47e4d2f1bff62375b6482298dc6444fe675c54e0a76061`。
- 导出：`b0b0d6e6577c3e9be463be656e37d39b94af05d6a3da2f346a3b60af4f737cbb`、
  `d5146ca5ea1837b8c6c90a0853b8f20243753033697d76d83cb09f04646995f1`、
  `ff4df0586331da74d9a171e2c78f190e46a8ccd5153cc6fa4a9a0b2435912809`、
  `2bf83d48f584710032e06f5df7354b6db13d65993ba289189c96f879a63c1368`、
  `5df4f6a7edf769b0210b04da4458a4d85db3104a925b959ef898d72dcac7f06b`。

提示词全文留档：`prompt-v31.txt` / `prompt-v32.txt` / `prompt-final.txt`（v3 文本见前序阶段
`prompt-v3-probe.txt`）。`prompt-final.txt` 由 `extract_prompt.py` 从 Kotlin 源抽取，其 sha256
与两轮 v3.3 的 16 页 `promptSha256` **逐字一致**——证据里的文本就是线上请求所用文本。
v3.1 文本由最终文本按本文件 §3 记录的差异回推，sha256 与 `dev-v31-01` 的 16 页记录一致。

## 3. 提示词迭代（每轮改动与实测效果）

### 3.1 v3 → v3.1（第 1→2 轮）：根因是「题号数=区域数」把边缘残段逼成弃答

第 1 轮 8 页弃答的原始 reasoning 直接指认了这条自相矛盾：

- `IMG20260810004854`（金标 2）：“The rules explicitly anchor counting to visible 题号 and require
  questionRegions count = number of 题号. So SPLIT with 2 would violate… I'll finalize
  NEED_MORE_IMAGE.”（`dev-v3-01/IMG20260810004854.json` 的 `responseRaw`）
- 其中 `011017`（“题号不可见，无法给区域…所以不应SPLIT”）、`011126`（“顶部有上一题选项，题干缺失，
  题号不可见。不纳入独立题”）也直接把「题号数=区域数」当作弃答理由；`005605/005855/010349/
  010633/011248` 以「题干/选项/关键条件在画面外」为由——两者都源于 v3 的残段弃答语义，v3.1
  同时收窄了这两处。

v3.1 改动（逐条）：

1. 数题规则：`先按阅读顺序数可见题号…questionRegions的项数必须等于题号数` →
   `先按阅读顺序数画面里可见的每一道题：有题号的按题号数；没有题号的相邻题残段（页边只露出选项、
   个别文字或图形，题号或题干在画面外）也各算一道；questionRegions的项数必须等于这样数出的题数`。
2. NEED_MORE_IMAGE 收窄：`只有题干主体或关键条件确在画面外时` → `题号、题干、选项或图形任一可见
   就照常成框…只有整页没有任何可辨认题面（全黑、全糊、整页空白）时才返回NEED_MORE_IMAGE`。
3. 新增相邻题界线规则与「后一个框不得完全落在前一个框内」（005301 的嵌套框曾致
   `SPLIT_UNUSABLE`）。
4. 跨页规则：`内容未拍全时返回NEED_MORE_IMAGE` → `本页只给本页可见部分的框，不因相邻页内容缺失而弃答`。

效果：弃答 8→0、通道不可评 0；① 1/16→9/16。代价是**过度分题**：004854 把横拍页的每一行文字
当成一题（2→6）；010349/010633/011126/011017 把相邻页（装订线外）的栏/边条当成题
（分别 4→7、3→7、2→5、3→5）。

### 3.2 v3.1 → v3.2（第 2→3 轮）：把「残段计题」收窄到本页纸面，并处理横拍

v3.2 改动：

1. 数题规则加限定：`同一题的题号、题干、选项、小问、图形合成一题，题号行、单个选项行、一行文字
   都不单独成题；同一残段露出的多行内容合并成一个框`。
2. 新增 `只数本页纸面：画面左/右若露出另一页（装订线外、页边外）的内容，不算本页的题，也不成框`。
3. 新增 `照片可能横拍（文字在画面里转向）：按文字阅读方向判断题界；同一题在画面里是一个连续块，
   不要把每行文字当成一题`。
4. 双栏段加 `两栏都有题时左右两栏各自按题号分题，不得只数一栏`。

效果：上一轮的 5 个过度分题页全部回落（004854 6→2、010349 7→4、010633 7→3、011126 5→2、
011017 5→2）；① 9/16→13/16，③ 0.0577→0.0385（首次 ≤0.05）。

### 3.3 v3.2 → v3.3（第 3→4 轮）：双栏页「每栏顶/底残段」条款

v3.3 只加一条（作用域仅双栏页）：

> 某一栏顶部/底部若出现与本栏第一题/最后一题无关的上一题或下一题残段（题号常不可见、可能
> 只剩选项），也要单独算一题，不得并进本栏的题。

效果与如实判定：

- 005301 的失败形态变了（第 3 轮：一个覆盖左页与右页上部的宽框 + 右栏 Q15 框；第 4 轮：右栏顶
  残段 + 右栏 Q15 两框），说明该条款确实把「右栏顶残段并入本栏题」这一子失败拆开了；但整页仍
  2 vs 金标 3，因为模型把左页整页当成相邻页丢掉——条款没有救回该页。
- 005144 仍 6 vs 7：模型 reasoning 明确 `右栏B-D与13题题干是同一题，所以不应单独算`，与金标
  「把右栏顶残段单列一题」语义相反，条款未改变其判断。
- 011017 从 2→3 属**采样噪声**：v3.3 相对 v3.2 在单栏页上的规则完全未变；第 4 轮模型把主体题
  拆成两框（见 §4.4 的切残），计数碰巧凑成 3。

## 4. 失败页与归因（最终提示词两轮）

### 4.1 `IMG20260810005144.jpg`（金标 7，v3.3 两轮均 6）：两页跨版照，右栏顶部残段计不计

金标 7 = 左栏 3（Q11/Q12/Q13）+ 跨栏 1 + 右栏 3（顶部残段/Q14/Q15）。模型两轮都只数 6，
reasoning 明说：

> 右栏B-D与13题题干是同一题，所以不应单独算… 如果不包括，题数6。如果包括，题数7。

这是金标语义（把跨版右页顶部只露选项的残段单列）与提示词「同一题合成一题」的直接冲突；
见 §5.3。

### 4.2 `IMG20260810005301.jpg`（金标 3，v3.3 两轮均 2）：跨版两页各算一页

金标 3 = 左页 Q14（0.03–0.5，0.065–1.0）+ 右页 Q14 的 (4)(5) 残段（0.5–0.955，0.036–0.165）
+ 右页 Q15（0.172–0.96）。模型第 4 轮只给右页两框（0.512–0.9），把左页整页排除
（`只数本页纸面` 条款被套用到跨版照的左页）。第 3 轮则是一个覆盖左页与右页上部的宽框 +
右栏 Q15 框、漏右页顶残段。两轮都没命中金标的「每页各自计题」语义。

### 4.3 第 1 轮弃答页（v3 已修，留档归因）

`004854`（MISSING_OPTIONS×2）、`005605`（OCCLUDED/KEY_TEXT_UNREADABLE）、`005855`
（MISSING_OPTIONS/KEY_TEXT_UNREADABLE）、`010349`（KEY_TEXT_UNREADABLE）、`010633`
（OCCLUDED×3）、`011017`（OCCLUDED）、`011126`（OCCLUDED）、`011248`（KEY_TEXT_UNREADABLE）
——原始回包与 reasoning 均在 `dev-v3-01/` 逐页 JSON。

### 4.4 切残（③）归因

| run | 切残页 | 归因 |
|---|---|---|
| `dev-v32-01` | 004511（金标题 2 收到 3 片）、004854（金标题 1 收到 2 片） | 004511 是金标坐标帧转置造成的**伪切残**（§5.1）；004854 是横拍页两框边界落进同一金标题（真边界偏差） |
| `dev-v33-01` | 004511（伪切残）、011017（金标题 3 收到 2 片） | 011017：模型把主体题拆成 y 0.045–0.655 与 0.665–0.86 两框（真切残，计数碰巧 =3） |
| `dev-v33-02` | 004511（伪切残）、004854（边界）、005855（边界） | 005855：第三框 top 0.82 vs 金标 0.922，按 55% 占比被判为该题碎片（真边界偏差） |

结论：③ 的 dev 读数含 1 个固定伪切残（004511），其余是框边界偏差；两轮 v3.3 的 ③ 为
2/52=0.0385 与 3/52=0.0577，**跨在 0.05 线两侧**，不能宣称 dev ③ 稳定达标。

## 5. 金标/通道的结构性限制（提示词不可修复，如实登记，未改金标）

### 5.1 004511 / 011649 的金标框相对原始像素帧转置

这两页是横拍页（原图 3072×4096，文字在画面里转 90°）。把金标框按 `(left,top,right,bottom)`
与「转置后 `(top,left,bottom,right)`」分别与模型框算每金标题的最大 IoU：

| 页 | 原始帧 best IoU | 转置帧 best IoU |
|---|---|---|
| 004511 | 0.117 / 0.256 / 0.203 | **0.023 / 0.874 / 0.888** |
| 011649 | 0.122 / 0.412 / 0.053 | **0.033 / 0.817 / 0.000** |

转置帧对得上题、原始帧对不上 ⇒ 金标这两页的坐标帧与原始像素帧不一致（横拍页在标注时被摆正）。
后果：② 在这两页被系统性压低；004511 的 ③ 伪切残（金标题 2 被两个不同题的应用框同时命中）
也是该帧差的派生结果。**金标 v2 已冻结，本阶段不改**；该限制会随门报告保留。

### 5.2 011017 金标框低于产品最小区域

金标 `box2` 高 0.030 < 产品 `MIN_SPLIT_REGION_HEIGHT=0.04`
（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/CaptureWorkflowRepository.kt:447-457`）。
即使模型逐像素复刻金标几何，`isUsableSplitRegionSet` 也会整组拒收（0 框）。计数口径可对上，
但这一页的 ② 不可能靠复刻几何拿满。

### 5.3 005144/005301 是「两页跨版照」，金标按每页各自计题

金标在这两页把同一道题在相邻页上的可见部分**分别计为一题**（005301：左页 Q14 与右页 Q14(4)(5)
各一题），而提示词写 `同一道题跨页不算多题`。这是金标语义与提示词的冲突，不是模型随机错误。
本阶段不为此改提示词的跨页条款：acceptance 8 页按批次 50 协议 **0 张双栏页**
（`docs/research/evidence/2026-11-09-batch-50/split-gate/acceptance-protocol.md` §3.3），
继续为跨版照加规则只会对 dev 过拟合。两页留在失败清单，供 G3 门如实计入。

### 5.4 单轮采样噪声

同一提示词 v3.3 两轮：① 14/16 与 12/16（差 2 页），② 36/52 与 31/52，③ 2/52 与 3/52。
差异页为 004511（3→4 框）、011017（3→2 框）、004854/005855 的边界碎片。**v3.2 的 13/16 与
v3.3 两轮均值 13/16 在噪声内不可区分**；轮间大幅提升（1→9→13）远超噪声。

## 6. 最终提示词选择

- 选定 **v3.3**：`singlePageAssessmentPrompt`（`OpenAiModelTaskAdapters.kt:161-211`），2204 字符，
  sha256 `7f92b456dea4bbdd5d47e4d2f1bff62375b6482298dc6444fe675c54e0a76061`；全文
  `prompt-final.txt`（sha 已与两轮 16 页记录的 `promptSha256` 对齐）。
- 依据（dev 指标）：单轮 ① 14/16=0.875、② 36/52=0.6923、③ 2/52=0.0385——四版中最高单轮 ① 与 ②；
  复测 ① 12/16、③ 3/52，均值 ① 13/16、③ 2.5/52。v3.2（13/16、③ 2/52）与 v3.3 在噪声内并列，
  v3.3 的增量条款只影响双栏页且未救回 005144/005301（§3.3）；保留 v3.3 是因为它是唯一单轮
  达到 14/16 的版本且两轮均无回归，**不宣称它统计上优于 v3.2**。
- 以门阈值 0.9 作参照，dev 最高 0.875 **未达 0.9**；dev 不是门值，门值只在 acceptance 一次性
  运行上成立（本阶段未跑，见 §7）。

## 7. 未验证 / 边界（不得当作已通过）

1. **acceptance 未跑**：本阶段任何命令的页清单都没有 acceptance 页（`make_dev_slice.py` 与
   `run_split_eval.sh` 均硬断言交集 0）；`check_split_gate.py` 未在本阶段对任何 dev 导出运行，
   dev ①②③ 由 `measure_split_accuracy.py`（同一口径 v3）计算。acceptance 门值未知。
2. **单臂、单模型、温度 0.1、单轮采样**：没有跨温度/跨模型复验，也没有置信区间；v3.3 两轮的
   ①差 2 页已说明点估计的抖动。
3. **通道差异**（§1）：直连传输、PIL 重采样、无设备 DB 落库；门值/读数不外推为设备产品路径准确率。
4. **金标等级 G1（model-labeled）**：不是人工专家金标；004511/011649 的坐标帧问题（§5.1）是
   G1 标注链的已知缺陷，本阶段只登记、未修复。
5. **提示词文本的可追溯性**：v3.1 文本由最终文本按 §3.1 记录的差异回推并以 sha 校验（匹配）；
   v3 文本来自前序阶段 `prompt-v3-probe.txt`（1710 字符、sha `2fa06897…`），未再独立重抽。
6. **回归**：`OpenAiCaptureAssessmentAdapterTest` 9 tests / 0 failures / 0 errors / 0 skipped
   （`final-kotlin-assessment-tests.txt`、`final-junit-summary.txt`，2026-11-09T20:52:32Z）。
   既有提示词断言（五键白名单、`不得返回type`、示例、六规则、`整页只有一道题时返回PASS`）全部保留。

## 8. 证据索引

```
docs/research/evidence/2026-11-10-batch-51/split/
├─ dev-tuning.md                        # 本文件
├─ prompt-v3-probe.txt                  # v3 文本与 sha（前序阶段产物）
├─ prompt-v31.txt                       # v3.1 全文（回推 + sha 校验）
├─ prompt-v32.txt                       # v3.2 全文
├─ prompt-final.txt                     # v3.3 全文（最终，sha 与两轮逐页记录一致）
└─ dev-runs/
   ├─ dev-v3-01/    # 16 页逐页 JSON（responseRaw 全文）+ manifest.json + export.json(+sha)
   │                #   + metrics.txt/json + gradle.log
   ├─ dev-v31-01/
   ├─ dev-v32-01/
   ├─ dev-v33-01/
   ├─ dev-v33-02/   # v3.3 复测（同提示词）
   ├─ final-kotlin-assessment-tests.txt
   └─ final-junit-summary.txt
```

可重跑脚本（在 gitignored `build/`，未提交；命令与参数见 §1）：
`build/batch51-split/{make_dev_slice.py, run_split_eval.sh, assemble_export.py, summarize_run.py,
extract_prompt.py}`；出网副本脚本沿用 `build/batch48-ws2/prepare_egress.py`（批次 48 原样，未改）。
