# 批次 51 · 修复环（高/中）报告

- 角色：修复工程师（独立于证据生产线的修复环）
- 输入：批次 51 独立复核 findings（24 条；最后一条在输入中截断于「3E 门未签字：设备 OK(4) 已覆盖全库捞回/F17 封顶/同」，按可见内容与 `acceptance/README.md:28`、`mapping-b51.md:51,73` 补全为「3E 批次级无条件 PASS 未签字」）
- 纪律：不改门阈值、不伪造 PASS、不可评记 `NOT_EVALUABLE`/`BLOCKED`、不打印密钥、不用 `--no-verify`、不并发 Gradle、不整文件抢占 KB/JS/UI 热区
- **底线结论：本修复环没有把任何门从 FAIL 改成 PASS。3D / AUD-08 / MVA 仍 FAIL，3E 仍无批次级签字，发布仍 BLOCKED；批次 51 仍不能称完成/发版水平。**

## 1. 一屏结果

| 处置 | 数量 | 条目 |
|---|---:|---|
| 已修复（文档/机器可读/代码） | 8 | F3/F17（MVA 同源披露）、F6/F16（3D 事实错误）、F10（MVA 关键错误可引用性）、F18（MVA p95 端到端）、F19（行级 review JSON 自相矛盾）、F22（gate-evidence md 第三次观测更正；gate-report JSON 仍旧文案） |
| 如实登记为阻塞（未变绿） | 9 | F1/F15（无门 PASS）、F2（acceptance 切片复用）、F4（κ NOT RUN）、F5/F20（发布）、F9（独立/对抗复核）、F21（D-3 召回）、F24（3E 未签字） |
| 低危未改（登记原因） | 4 | F7（冻结框 v28）、F8（验收投影）、F11（提示词 sha 口径）、F23（3E patch 过期） |
| 正向核实，无需动作 | 3 | F12（3E 接线）、F14（3D 三项复算）、F13（设备/JVM 未由复核方重跑） |

> F1 与 F15、F3 与 F17、F5 与 F20、F6 与 F16 是同一事实的重复条目，合并处置。

## 2. 已修复项（含先红后绿与 exit）

### 2.1 F6/F16：3D REPORT §6.2 与 record 的事实错误

原表述「B50 的 43 条错误材料在本批样本中出现 0 条」不成立。只读复算
`fix-loop/verify_b50_b51_overlap.py`（读 B50 `errors.csv`/`sample.csv`、B51 `sample.csv`/`judgments.csv`/`errors.csv`）：

```
{"b50_errors_in_b51_sample": 4, "b50_errors_in_b51_sample_all_correct": true,
 "b50_errors_intersect_b51_errors": 0, "sample_overlap": 68,
 "counts": {"b50_errors": 43, "b51_sample": 1600, "b51_judgments": 1600, "b51_errors": 99}}
expected(4 correct, 0 error overlap, 68 sample overlap)=True
EXIT=0
```

已改：

- `3d-audit/REPORT.md` §6.2：改为「43 条中 4 条进入本批样本且均判 CORRECT；两轮**错误** slug 交集才是 0；其余 36 条改绑 + 3 条撤料未直接测量」。
- `3d-audit/REPORT.md` §7 新增第 10 条：这 4 条由与 B50 改绑判定相同的 `deepseek-flash` 复测，属同模型复测，不是独立验证。
- `docs/research/2026-11-10-batch-51-record.md` 3D 小节同步改正。
- 新证据：`fix-loop/b50-b51-overlap.json` / `.csv`（4 条逐条 B50/B51 层、verdict、confidence）。

### 2.2 F18：MVA p95 机器可读门从「仅适配器」改为「请求→首图」

问题：`mva_gate_run.py` 把适配器渲染延迟（35.15 ms）塞进 `latency_ms`，`mva_gate.py` 的
`first_image_p95_ms` 因此 `met=true`；设计 §6 的门是「首张合格图 p95」（请求→首图，含作者模型耗时）。

先红：在 `tools/figure_eval/test_mva_gate.py` 新增两条断言后实跑：

```
python -m pytest tools/figure_eval/test_mva_gate.py -q -k p95
2 failed, 1 passed ...  (KeyError: 'scope' / 'render_only')
PIPE_EXIT=1
```

实现：

- `mva_gate.py`：`gate_reading` 同时收集 `latency_ms`（端到端）与 `render_latency_ms`（仅渲染）；
  `first_image_p95_ms` 增加 `scope="request_to_first_image"` 与 `render_only` 并列读数；门判只用端到端。
- `mva_gate_run.py`：`end_to_end_latency_ms = author_record.elapsed_ms + record.latency_ms`；
  缺任一侧记 `None`（fail-closed），并保留 `render_latency_ms` / `author_elapsed_ms`。

后绿：

```
python -m pytest tools/figure_eval/test_mva_gate.py -q          → 18 passed
python -m pytest tools/figure_eval -q                            → 84 passed
python -I -X utf8 tools/figure_eval/mva_gate_run.py ... --out .../gate → EXIT=0
  JS first_image_p95_ms = 72955.09999999999 ms, met=false, samples=32
  render_only            = 35.14999999999999 ms, met=true（并列，不参与门判）
```

`gate/` 的 `task_records.json`/`gate_records.json`/`mva_preflight.json` 已按同一输入重算；
`gate/readings_digest.json` 由 `fix-loop/patch_mva_digest.py` 写回（值只从 `mva_preflight.json` 读，
不手写门值），旧渲染读数保留在 `gate.p95_ms_pre_fixloop`。`mva-gate/SHA256SUMS` 774 项复算 0 mismatch。

### 2.3 F3/F17：MVA 判官与作者链同源

作者链 `deepseek-flash`（temperature 0.1）、判官 `DEFAULT_MODEL=deepseek-flash`（temperature 0.0）。
已在：

- `mva-gate/README.md` §8 增加显式条目：32/120、6 组与 6 对校准属同源自证，不作独立能力证据；
  门 FAIL 不依赖判官（RENDERED 95/120 < 108）。
- `readings_digest.json` 增加 `independence.same_model=true` 与说明。
- record MVA 方法学偏差同步点名「同模型」。

### 2.4 F10：MVA `critical_errors` 的「可引用」歧义

`readings_digest.json` 的 `critical_errors.citable=true` 只表示「有可评样本、字段可机械读出」。
已在该 JSON 增加 `interpretation`：0 是结构性结果（无「自我接受后交付」阶段），不表示模型未犯关键错误；
README §7.2 同步。字段值/门判未改。

### 2.5 F19：行级 review JSON 的判词自相矛盾

J-002/J-048/J-052 批次 51 由 FAIL 机械翻 PASS，但 notes 仍写「行级复核判 FAIL…不洗绿」并重复 4 次换绑后缀。
`fix-loop/fix_review_notes.py` 只改这三份的 notes（verdict/evidence/checks 不动），改为：

- 「历史：批次50 行级复核判 FAIL（保留原判词，不洗绿）。批次51：按本批新证据重新机械复算通过，
  verdict 由 FAIL 翻为 PASS；翻转为机械复算，不构成第二人/第二模型的独立语义复核。」
- 新增 `prior_verdict="FAIL"`、`verdict_flip={from:FAIL,to:PASS,kind:mechanical-recompute,semantic_review:false}`。

复算：

```
python -I -X utf8 fix-loop/fix_review_notes.py                  → EXIT=0（3 行）
python -I -X utf8 acceptance/make_manifests_b51.py              → REVIEWS 948 / RES 26 / mismatches=0 / EXIT=0
python -I -X utf8 tools/ci/acceptance_report.py --require-complete
  → EXIT=1（预期红，非本次改动引入）；state=FAIL；counts 937/10/253/0；
    pass_with_review=937；codes=NOT_ACCEPTANCE_COMPLETE + REVIEW_CHECKS_SHARED
```

台账 `docs/acceptance/ledger.md` 未改，1200 行结构不变。

### 2.6 其它文档纠正（不改判定）

- **F4**：`3d-audit/REPORT.md` §7.1 补明「按预注册 κ<0.60 不得用于门；本轮无 κ，即使读数达标也不得签门级 PASS」。
- **F2**：`split/acceptance/README.md` §5.1 补明「计划项 23『从未看过的新 acceptance 切片』未完成；
  本批只是同一 8 页第三次观测、本批唯一 run」。
- **F5/F20**：`release/release-51.md` §7 增加修复环只读复核：HEAD `8dd29e96…` 的
  `git cat-file -e HEAD:<AiGeneratedDocumentNotice.kt>` exit **128**、文件未跟踪、sha256
  `f75ac862…`；干净树仍 BLOCKED，提交动作属主会话。
- **F24**：`3e/README.md` §0/§6 补明：设备 OK(4) 已补跑（`device/26-instrument-3e-rerun.log`），
  但台账无 3E 门行，行级投影是 E-075 PASS + A-023 BLOCKED，**批次级无条件 PASS 未签字**。

## 3. 未修复 / 阻塞（逐条）

| 条目 | 严重度 | 当前事实（可复算） | 为什么本环不修 |
|---|---|---|---|
| F1/F15 无门 PASS | 高 | 3D 主判据 0.9291236 < 0.95、normal 0.8979148 < 0.90；AUD-08 ① 19/24、③ 4/78、frag 4；MVA 32/120、RENDERED 95<108；3E 无门行 | 要变绿只能提升 KB/切分/作者链质量后重跑，或改阈值/伪造；两条都禁止 |
| F2 acceptance 切片复用 | 中 | 同一 8 页已在批次 48/50/51 三次观测；仓内无未用页金标 | 需要新标注一套未见页金标；本轮不能把第三次观测写成 citable PASS（门也是 FAIL） |
| F4 3D κ NOT RUN | 中 | 单模型单轮、无第二判定会话；`stats.json` `human_expert=false` | 没有独立第二标注员/模型；同会话补做不构成盲判 |
| F5/F20 发布不可复现 | 高 | 干净 clone 在 `:core:export` 因未跟踪 `AiGeneratedDocumentNotice.kt` 失败；AAB 本地钥 DN `OU=NOT-PLAY-PRODUCTION`；Play/备案/隐私 URL/provider 外部 BLOCKED | 修复需要主会话提交未跟踪文件 + 生产身份/外部账号；本环不代提交、不伪造生产签名 |
| F9 独立/对抗复核 | 中 | 记录无「独立复核」小节；证据目录无独立对抗产物 | 本次 findings 本身是独立复核；修复环不能自封为独立/对抗复核。§4 的反例扫描明确标为「非独立」 |
| F21 D-3 召回 | 中 | 端侧/JVM 均 116/129（目标 117/129）；归因到 KD-24 别名截断位移 | 恢复 117 的路径都要动既有红线（别名/索引/α），需主会话或用户裁定 |
| F22 门报告旧文案 | 低 | `gate-report-b51-primary.json` scope 仍写「第二次观测」；`gate-evidence-b51-primary.md` / alt md 已加**修复环更正：本批是第三次观测**；README §5.1 为准 | JSON 由共享 `check_split_gate.py` 生成；改通用文案会硬编码批次历史，且非阈值项，故只更正人读证据 md，保留 README 取代声明 |
| F24 3E 门未签字 | 中 | 设备 OK(4)；E-075 PASS；A-023 BLOCKED；台账 1200 行无 3E 门行 | `acceptance_init.py:87,522` 硬校验 1200 行（GATE 100），加门行会破坏结构；本环不代签 |
| F7 冻结框 v28 | 低 | 快照 62,944 行/2,481 节点，早于 v29 40 条改绑 | 按指令沿用冻结框，替换需新 audit id/新框；报告已披露 |
| F8 验收投影 | 低 | `adjudication.md`/`verdicts.md` 仍批次 47/50 版 | 属验收投影线；record 已说明未投影；本环不改他线台账 |
| F11 提示词 sha | 低 | 文件 raw sha `ee14e9a6…`；记录值 `7f92b456…` 是去尾 CRLF 后字符串 sha | 文本内容一致；口径说明属证据注记，不影响门 |
| F23 3E patch 过期 | 低 | `code-diff.patch` 仍 `weight=0.5`；最终树 0.35；设备 rerun 日志记录了改动 | patch 是批次50 基线 diff；重生成属 3E 线，低危且不影响结论 |

### 3.1 门值原样复核（本环实跑）

- 3D：`recompute.py` EXIT=0、`recompute_detail.py` EXIT=0、`sample_d1.py --check` → `match=true, 1600/1600`。
  主判据 0.9291236、normal 0.8979148 未变。
- AUD-08：`gate-report-b51-primary.json` 仍 `state=FAIL`、① 19/24、③ 4/78、fragmented=4；本环未重跑
  acceptance（一次性承诺 + 无新切片）。
- MVA：重算后首次/最终仍 32/120=0.2667、6/6 组 FAIL、端到端 p95 72.9551 s FAIL、IMG 臂 BLOCKED。
- 3E：`device/26-instrument-3e-rerun.log:64` `OK (4 tests)`、`Time: 6.376`；本环未重跑 Gradle。

## 4. 对抗反例扫描（修复环产物；**不是独立复核**）

本环按「找反例」方式重读了门证据，确认并修掉的反例：

1. **3D 报告反例**：报告称 43 条错误材料与本批样本交集 0；实际 4 条在样本中且全 CORRECT。
   已用只读脚本固定证据并改文（§2.1）。
2. **MVA p95 反例**：机器可读门 `met=true` 来自 35 ms 渲染延迟，而端到端为 72.955 s。
   已改代码/重算/改 digest（§2.2）。
3. **MVA 独立性反例**：判官与作者同模型，32/120 被 README 当作读数但未点名同源。
   已显式登记（§2.3）。
4. **验收判词反例**：J-002/J-048/J-052 verdict=PASS 与 notes「判 FAIL」并存。
   已分开历史与本次机械翻转（§2.5）。
5. **发布反例**：候选包签名/16KB 全绿，但 HEAD 自身不可编译。已在 release 文档固定
   `cat-file -e` exit 128 与未跟踪事实（§2.6）。
6. **3E 反例**：设备 OK(4) 容易被读成「3E 已通过」，但台账无门行、A-023 仍 BLOCKED。
   已在 3E README 明确「NOT SIGNED」。

仍未找到反例的部分：3D 的 Wilson 算术、样本确定性、rebind 落包（独立复核已做，本环复跑
`sample_d1.py --check` 与 `recompute*.py` 均 0）；MVA 的 RENDERED 95<108 上界（算术稳定）。

## 5. 验证命令与 exit（本环实跑）

| # | 命令 | exit | 关键结果 |
|---|---|---|---|
| 1 | `python -I -X utf8 fix-loop/verify_b50_b51_overlap.py` | 0 | 4 条进入 B51 样本、4/4 CORRECT、错误交集 0、样本交集 68 |
| 2 | `python -I -X utf8 3d-audit/recompute.py` / `recompute_detail.py` | 0 / 0 | `all_match` 保持 |
| 3 | `python -I -X utf8 3d-audit/sample_d1.py --check` | 0 | `match=true, expected=1600, actual=1600` |
| 4 | `python -m pytest tools/figure_eval/test_mva_gate.py -q`（修前） | 1 | 2 failed（新断言红） |
| 5 | `python -m pytest tools/figure_eval/test_mva_gate.py -q`（修后） | 0 | 18 passed |
| 6 | `python -m pytest tools/figure_eval -q` | 0 | 84 passed |
| 7 | `python -I -X utf8 tools/figure_eval/mva_gate_run.py ... --out .../gate` | 0 | p95 72955.1 ms / met=false；render_only 35.15 ms |
| 8 | `python -I -X utf8 fix-loop/patch_mva_digest.py` | 0 | digest p95 met=false；同源标记写入 |
| 9 | `python -I -X utf8 fix-loop/update_sha_manifest.py mva-gate/SHA256SUMS mva-gate` | 0 | 774 项、5 项更新、0 mismatch |
| 10 | `python -I -X utf8 fix-loop/update_sha_manifest.py release/EVIDENCE.sha256 release` | 0 | 56 项、1 项更新、0 mismatch |
| 11 | `python -I -X utf8 fix-loop/fix_review_notes.py` | 0 | 3 行 notes/prior_verdict/verdict_flip |
| 12 | `python -I -X utf8 acceptance/make_manifests_b51.py` | 0 | REVIEWS 948、RES 26、0 mismatch |
| 13 | `python -I -X utf8 tools/ci/acceptance_report.py --require-complete` | 1（预期） | state=FAIL、937/10/253/0、NOT_ACCEPTANCE_COMPLETE + REVIEW_CHECKS_SHARED |
| 14 | `git cat-file -e HEAD:<AiGeneratedDocumentNotice.kt>` | 128 | 未跟踪；干净树仍 BLOCKED |
| 15 | `git status --porcelain -- <AiGeneratedDocumentNotice.kt>` | 0 | `?? core/export/.../AiGeneratedDocumentNotice.kt` |
| 16 | `python -I -X utf8 tools/figure_eval/mva_gate.py --out fix-loop/mva-preflight-cli-check` | 0 | 计划点名命令可跑；无样本臂 p95=null、不伪造 |
| 17 | `python -I -X utf8 tools/ci/acceptance_lint.py` | 1（预期） | 162 × `EVIDENCE_REUSED`，与修复前同量级、无新增码 |

未跑：Gradle/设备（红线：不与其他 agent 并发；本环无 Kotlin 代码改动）；AUD-08 重跑（一次性承诺）；
MVA 作者/判官真实调用（只重算已有产物）；soak；发布构建。

## 6. 改动文件

- `docs/research/evidence/2026-11-10-batch-51/3d-audit/REPORT.md`（事实纠正 + κ 限制 + 同模型复测限制）
- `docs/research/2026-11-10-batch-51/mva-gate/README.md`（p95 端到端、同源、critical_errors 解释）
- `docs/research/evidence/2026-11-10-batch-51/mva-gate/gate/{task_records,gate_records,mva_preflight,readings_digest}.json`（重算/派生）
- `docs/research/evidence/2026-11-10-batch-51/mva-gate/SHA256SUMS`（5 行）
- `docs/research/evidence/2026-11-10-batch-51/release/release-51.md` + `EVIDENCE.sha256`（1 行）
- `docs/research/evidence/2026-11-10-batch-51/3e/README.md`（3E 签字状态）
- `docs/research/evidence/2026-11-10-batch-51/split/acceptance/README.md`（计划项 23 未完成）
- `docs/research/evidence/2026-11-10-batch-51/split/acceptance/gate/gate-evidence-b51-primary.md` /
  `...-alt-dev-v33-01.md`（第三次观测更正）
- `docs/research/evidence/2026-11-10-batch-51/acceptance/README.md`（机械 PASS 口径 + p95 数值）
- `docs/acceptance/verdicts/rows/{J-002,J-048,J-052}.json`（notes/prior_verdict/verdict_flip）
- `docs/acceptance/evidence/2026-11-10-batch-51/REVIEWS.sha256`（重生成）
- `docs/research/2026-11-10-batch-51-record.md`（3D 事实、MVA p95/同源、验收 p95）
- `tools/figure_eval/mva_gate.py`、`mva_gate_run.py`、`test_mva_gate.py`（p95 端到端 + 2 例测试）
- 本目录脚本：`verify_b50_b51_overlap.py`、`patch_mva_digest.py`、`fix_review_notes.py`、`update_sha_manifest.py`、`REPAIR-REPORT.md`

## 7. 剩余风险 / 未验证

- **批次完成定义仍未满足**：3D/AUD-08/MVA 仍 FAIL，3E 未签批次级 PASS，发布 BLOCKED。
  本报告不写「批次完成」或「四门通过」。
- 未跑 Gradle/设备：MVA 代码改动只跑了 Python 单测与离设备重算；Kotlin 生产路径未变。
- MVA 120 集仍是回归集；无新的未见 120 盲测集，门值不能作启用 PASS 依据。
- 3D 仍为 model-labeled、κ NOT RUN；即使未来读数达标也不能签门级 PASS。
- 发布干净树要等主会话提交未跟踪文件；生产签名/Play/备案/隐私 URL/provider 仍是外部 BLOCKED。
- 行级 review JSON 的三行仍是机械复算 PASS，不是独立语义复核；`acceptance_report` 仍
  `REVIEW_CHECKS_SHARED`/`NOT_ACCEPTANCE_COMPLETE`。
- 输入 findings 最后一条在对话中被截断；本报告按可见文本与台账证据补全其含义。若原 findings
  还有未显示条目，不在本报告覆盖范围内。
