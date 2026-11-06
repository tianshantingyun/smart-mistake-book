# E-42-WS9 合同与准入：契约 P0 可应用 patch / 准入清单三次清算 / AUD-08 抽样 / 门红线 / 台账

| 字段 | 值 |
|---|---|
| evidence-id | `E-42-WS9-01` |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | WS9 / 计划项 71–75（`.zcode/plans/plan-2026-11-04-batch-42.md:111-116`） |
| gate-or-kd | 契约 V1.1 P0；4E.2 准入清单 69 行；AUD-08 切分门；KD-75/76/83 状态只读 |
| verdict | 五项交付物全部落盘；**契约未应用**（docs/current 仍热）、**人工金标未执行**（BLOCKED）、**门值 FAIL 维持**；本轨零 Gradle/零设备/零 git 写 |
| executed-at | 2026-11-03（本机时间；文档名按计划批次 42 的 2026-11-04） |
| head | `faab17f58b01eab78d5c08a9f843b3af3df2fecf` |
| device-serial | **N/A**（本轨零设备） |
| apk-sha256 | **N/A**（本轨零构建） |
| exit-code | 门复算 `EXIT=1`；`validate_rulings` `EXIT=0`；patch 生成 `EXIT=0`、`git apply --check` `APPLY_CHECK_EXIT=0`；抽样/计数脚本 exit 0 |
| raw-log | `build/ws9-batch42/`（`gate-report-batch42.json` + 生成器脚本，gitignored） |
| record-ref | 计划 WS9 71–75；R6 `docs/research/2026-11-04-release-readiness.md`；`docs/research/2026-11-03-aud08-human-gold-protocol.md`；`docs/REFACTOR-MASTER-LINE.md` 批次 42 行 |
| scope | `docs/research/`、`docs/research/evidence/2026-11-04-batch-42/`、准入清单、MASTER-LINE、决策台账；**热区零改动** |
| sanitized | 是（无密钥/账号/Cookie；抽样框只记文件名与 sha256，不复制图片） |

## 1. 交付物（逐项可指认）

| 计划项 | 交付物 | 关键事实 |
|---|---|---|
| 71 | `docs/research/2026-11-04-contract-v1.1-p0-applicable.patch` | 2 hunks / +4/−2；sha256 `2f74e68be44608774d2280e330a0e736a4fc941fd45defc4aba6eb2f61b44e9b`；`git apply --check` **exit=0**；**未应用**；C-1/C-6/C-10 与批次 41 草案逐字一致（C-6 为其所在行的段尾替换） |
| 72 | 准入清单 `v3 补记 23` + `附录 O`（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`） | 69 行；**✅20/◐35/✗10/?2/⛔1/§1**；仅 R-03 `?→◐`；ID 集合与附录 N 完全相同 |
| 73 | `docs/research/2026-11-04-aud08-human-gold-sampling-plan.md` | 框 155（仓外）；抽样 **24 页 ≥20**；逐页 sha256 冻结；dev 16 / acceptance 8；不一致页不排除 |
| 74 | `docs/research/evidence/2026-11-04-batch-42/step-aud08-gate-recompute-b42.md` + `…/rulings/20261103T044703Z-10c61be3.json` | 门 **FAIL / exit 1**；① 4/7=0.5714、③ 0/16=0.0000；阈值零改动；裁定 `validate_rulings` PASS |
| 75 | `docs/REFACTOR-MASTER-LINE.md` 批次 42 WS9 行 + `docs/agent-first-refactor-decisions-2026-09-23.md` 批次 42 节 | 只登记载体与执行状态；不代裁 KD 关闭 |

## 2. 本轨实跑命令与输出（逐字摘要）

### 2.1 门值复算（item 74）

```text
$ python -I tools/split_golden/check_split_gate.py \
    --report-json build/ws9-batch42/gate-report-batch42.json \
    --evidence-out docs/research/evidence/2026-11-04-batch-42/step-aud08-gate-recompute-b42.md \
    --rulings-out docs/research/evidence/2026-11-04-batch-42/rulings
切分金标门判定（AUD-08）：FAIL（exit 1）
  金标：…split_golden_v1.json（sha256 f9e92c47…，冻结校验 PASS（子步骤）；证据等级 G2）
  应用导出：…build/split-eval/app-split.json（sha256 9b49b0c4…）
  ① countExactRate = 4/7 = 0.5714 （门 0.5714 >= 0.9 → 未过）
  ③ fragmentRate = 0/16 = 0.0000 （门 0.0000 <= 0.05 → 过）
  门判定：FAIL（阈值出处见 GATE.md；改值须先裁定并留旧值对照）
  自动化：门后自动化不启用（裁定 37 / 4C③…）
EXIT=1

$ python -I tools/split_golden/validate_rulings.py \
    --file docs/research/evidence/2026-11-04-batch-42/rulings/20261103T044703Z-10c61be3.json
裁定校验 PASS：20261103T044703Z-10c61be3.json
  · 页数 7；判词 consistent 4 / fragmented 0 / inconsistentCount 3 / missingInApp 0 / notEvaluable 0
  · 门状态 FAIL；rulingsSha256=2180f0dda7d0d7c3…
EXIT=0
```

门报告关键字段（`build/ws9-batch42/gate-report-batch42.json`）：`gate.state=FAIL`、`gate.passed=false`、`gate.count={value:0.5714,threshold:0.9,met:false}`、`gate.fragment={value:0.0,threshold:0.05,met:true}`、`hardRedLines.fragmentedQuestions={value:0,required:0,satisfied:true}`、`golden.evidenceGradeLevel=G2`、`scope.notGeneralizable=true`。

### 2.2 契约 patch（item 71）

```text
$ python -I build/ws9-batch42/make_contract_patch.py
anchors: C-10 line 182, C-6 line 208, C-1 line 212
wrote docs/research/2026-11-04-contract-v1.1-p0-applicable.patch hunks=2 +4/-2
EXIT=0

$ git apply --check --verbose docs/research/2026-11-04-contract-v1.1-p0-applicable.patch
Checking patch docs/current/visual-generation-contract.md...
APPLY_CHECK_EXIT=0
```

- 生成器只读契约并产出 patch；**没有执行 `git apply`**，`docs/current/visual-generation-contract.md` 字节未变。
- patch 内容核对：新增 `C-1` 段、`C-6` 段替换 `:208` 段尾 KD-81 旧句（旧句含 `不含 \`-wal\`/\`-shm\``）、`C-10` 段替换 `:182` 的 Room 66/67 旧段。

### 2.3 抽样计划复算（item 73）

```text
$ python -X utf8 -I - <<'PY'   # os.listdir + hashlib.sha256（见采样计划 §8）
frame 155 exts {'.jpg': 155}
draw 24
gold draw-positions [0, 1, 2, 8, 14, 18, 22]
sizes 16 8 overlap 0 non-gold-in-acceptance 5
```

- 7 张 v1 金标页在 155 框中的下标 `0/6/12/48/84/108/132`（均为 6 的倍数）→ 全部落在 24 页抽样内，证明 v1 的 7 页是该 24 页的子集。
- 切分：`sha256(文件名)` 升序前 16 = dev、后 8 = acceptance；8 张 acceptance 中 5 张非 v1 页（`…4952`、`…5711`、`…0010034`、`…010202`、`…011358`）。

### 2.4 准入清单计数（item 72）

```text
$ python -X utf8 -I -c "…按行首 ID 正则统计附录 O…"
69 Counter({'◐': 35, '✅': 20, '✗': 10, '?': 2, '⛔': 1, '§': 1})
dups []
N 69 same set True missing [] extra []
```

## 3. 逐项裁定

| item | 状态 | 说明 |
|---|---|---|
| 71 | **完成（patch 未应用）** | 可应用判据只到 `git apply --check`；并入前置 = `docs/current` 热区清空（本轮 7 条 M） |
| 72 | **完成** | 69 行矩阵按本批证据刷新；**0 项转"通过"**；唯一档位变化 R-03；MVA 不成立、`availableMethods` 未动 |
| 73 | **部分（计划已执行、标注 BLOCKED）** | S1 抽样与切分可执行且可复算；S0/S2–S7、G3/G4/G5 均未执行——无人工标注员 |
| 74 | **完成** | FAIL 维持、阈值零改动；自动决策红线写成 AND 条件并列出四项独立阻断 |
| 75 | **完成** | MASTER-LINE 与决策台账各一行/一节，未代裁任何 KD 关闭 |

## 4. 热区与写入边界（实跑）

- `git status --porcelain -- docs/current/` → **7 条 M**（README / architecture-contract / data-schema / implementation-status / model-task-contracts / product-contract / release-gates），`visual-generation-contract.md` 不在其中 → patch 可应用；契约正文零改动。
- 本轨写入面：`docs/research/2026-11-04-contract-v1.1-p0-applicable.patch`、`docs/research/2026-11-04-aud08-human-gold-sampling-plan.md`、准入清单（补记 23 + 附录 O）、MASTER-LINE、决策台账、本证据文件、AUD-08 门证据 + 裁定。**`core/domain`/`core/ui`/`feature/*`/`settings.gradle.kts`/Application/Root 本轨零改动；`core/export` 本轨零改动——树上的 `MistakePdfExporter.kt` M 与两个 AIGC 测试 `??` 属 WS2 轨。**
- 生成器在 `build/ws9-batch42/`（gitignored），不入库。

## 5. UNVERIFIED / BLOCKED

| # | 项 | 状态 |
|---|---|---|
| U1 | 契约 patch 并入后的实际文本 | **UNVERIFIED**：只做 `--check`，未应用 |
| U2 | 人工金标 S0/S2–S7 与 G3/G4/G5 | **未执行**：无标注员 |
| U3 | 人工金标正确率 / 双人一致性 / 裁定质量 | **UNVERIFIED** |
| U4 | 本轨发起的任何 Gradle/设备执行 | **无**：本轨零 Gradle/零设备 |
| U5 | P-01/P-07/R-03/R-04 的运行时数字 | **转录**其他轨证据，本轨未复跑 |
| B1 | 抽样计划执行 | **BLOCKED**：需 ≥2 名独立人工标注员 + 1 名非标注员裁定者 |
| B2 | 门后自动化 | **BLOCKED**：需 4C③ 产品裁定 + `gate.passed && hardRedLines.satisfied` |
| B3 | 契约正文并入 | **BLOCKED**：`docs/current` 7 条 M 未清 |

## 6. 回链

- 计划：`.zcode/plans/plan-2026-11-04-batch-42.md` §2 WS9 71–75、§4 硬约束。
- 基线：`docs/research/2026-11-04-batch-42-baseline.md` §1/§2/§6。
- 协议：`docs/research/2026-11-03-aud08-human-gold-protocol.md`；门说明 `tools/split_golden/GATE.md` §0/§2.1/§5。
- 批次 41 草案：`docs/research/2026-11-03-contract-v1.1-p0-seams.md`（C-1/C-6/C-10 的落点与旧文）。
- 本批其他轨证据：`docs/research/evidence/2026-11-04-batch-42/`（ws2-kd75-closeout、ws3-kd76-20-rounds、ws4-isolation-causality、ws5-kd83-v4-fixture、ws7-preview-assembly、`step-aud08-gate-recompute-b42.md`、`rulings/`）；`docs/research/2026-11-04-ws8-release-prefight.md`。
- 台账：`docs/REFACTOR-MASTER-LINE.md` 批次 42 WS9 行；`docs/agent-first-refactor-decisions-2026-09-23.md` 批次 42 节。
