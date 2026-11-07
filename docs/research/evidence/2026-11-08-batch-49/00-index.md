# 批次 49 验收证据总索引（台账/行级复核，2026-11-08）

> 角色：验收台账员（只写 `docs/acceptance` 与本证据目录；未改主码/测试/金样/阈值）。
> 台账：`docs/acceptance/ledger.md`（1200 行），sha256 = `264425f5796f0597e7f3ed8d9b7fe359f112d774eae1e0ab7256c1e9ea9f6ba8`（批次49 修复环后；修复前 `d370ef8a…a75bba`）。
> HEAD = `ba7b5624ea0c2bc3f09dcee247f5852e4f428122`（main）；门禁报告时点 `git status --porcelain -uall` = 4898（共享脏树，含本批全部轨道与未跟踪证据）。
> 设备：`emulator-5566` / `emulator-5590`（sdk_gphone64_x86_64，API 34）；APK sha `ebb3aa34…2244cee`、test APK `8629a05d…d6ea`（设备侧回读一致，见 `device/00-snapshot-and-device-meta.txt`）。

## 0. 计数（本会话 `acceptance_model.parse_ledger` 实算）

| verdict | 批次48 | 批次49 | 变化 |
|---|---:|---:|---:|
| PASS | 898 | **900** | +2（F-055、F-063） |
| FAIL | 23 | **22** | -1（F-055 转 PASS；F-057 维持 FAIL） |
| BLOCKED | 279 | **278** | -1（F-063 转 PASS） |
| UNVERIFIED | 0 | 0 | — |
| 合计 | 1200 | 1200 | 0 |

域计数：D0 100/0/0；A 65/0/25；B 88/0/2；C 88/0/2；D 82/0/8；E 81/0/9；F 58/0/32；G 83/0/7；H 69/2/19；I 53/0/37；J 29/13/48；FIX 15/0/85；GATE 90/6/4（PASS/FAIL/BLOCKED，UNVERIFIED 全 0）。

## 1. 台账行：4 主题 × E/F/A（12 行，原位升级，行数不变）

`tools/ci/acceptance_model.py` 固定 1200 行（D0 100 + A–J 各 90 + FIX 100 + GATE 100），
不允许扩行；因此本批对既有行做**原位升级**（保留 ID/行号/域计数），每条新检查都保留原行证据并追加本批证据。
2 条 F 行由 FAIL/BLOCKED 以新设备证据转 PASS（F-055/F-063）；F-057 因「超限路径未命中」未满足正向判据，维持 FAIL。其余 9 行为 PASS 行追加本批检查。

| 主题 | 行 | 行号 | verdict | 本批新增检查 | 主证据 | 行级复核 |
|---|---|---|---:|---|---|---|
| P1 标定 | A-064 | 195 | PASS | 掌握度主路径：阈值 21/0.9 维持；P1 报告 G6/权重未过门 | `jvm/…SimulatedLearnerTest.xml`（6）、`jvm/…ReviewPlannerScaleBenchmarkTest.xml`（2）、`calibration/g6-decisions.json`、`calibration/weight-decisions.json` | `rows/A-064.json` |
| P1 标定 | E-009 | 500 | PASS | FSRS 回归 + 模拟器 6 例同 seed 逐位一致 + 0/4、0/17 未过门 | `jvm/…SimulatedLearnerTest.xml`、`calibration/{g6,weight}-decisions.json`、原四类 XML | `rows/E-009.json` |
| P1 标定 | F-045 | 626 | PASS | 预览耗时回归 + 60/500/5000 金样逐位一致 | `jvm/…ReviewPlannerScaleBenchmarkTest.xml`、原预览证据 | `rows/F-045.json` |
| KD-67 | A-019 | 150 | PASS | 复习主路径含确认卡：候选/原因上屏、作答后确认、换题/取消 | `jvm/…KnowledgeClarificationPresenterTest.xml`（14）、`device/06-instrument-green-phase.device.json`（8 例） | `rows/A-019.json` |
| KD-67 | E-080 | 571 | PASS | 排序/选择证据 + 澄清决策消费 14 例 | 同上 presenter XML + green 设备绑定 + 原 PlannerV2 XML | `rows/E-080.json` |
| KD-67 | F-055 | 636 | **FAIL→PASS** | 2h soak 基线：7200s、117 轮、同一 pid、0 新崩溃/重启 | `soak/soak-samples.csv`、`00-snapshot…txt`、`soak/EXIT-CAPTURE-CAVEAT.md`（旧脚本 EXIT=0 不作退出证明） | `rows/F-055.json` |
| 校准页 | A-067 | 198 | PASS | 掌握度空态 + 校准页无拟合记录显示 —、默认不冒充本地拟合 | `jvm/…SchedulingCalibrationViewBuilderTest.xml`（7）、`device/07-instrument-calibration-page.device.json`（8 例） | `rows/A-067.json` |
| 校准页 | E-044 | 535 | PASS | 掌握证据 + 校准读模型 7 例 | 同上 view-builder XML + 校准页设备绑定 + 原掌握 XML | `rows/E-044.json` |
| 校准页 | F-057 | 638 | **维持 FAIL** | 2h 超限：判据要求「超限路径命中」，本批 117 轮 FATAL=0/超限未命中——不能把未命中记成通过 | 批次44 crash log + `SchedulingRetentionSliderValueTest.xml`（4/4）+ 本批 `soak-samples.csv` | `rows/F-057.json` |
| 冲突处置 | A-021 | 152 | PASS | 复习中断恢复含投影 CONFLICTED 专用状态与「重新整理」 | `p2-conflict/01-…xml`（8）、`p2-conflict/03-…xml`（6）、green 设备绑定 | `rows/A-021.json` |
| 冲突处置 | E-081 | 572 | PASS | 排序稳定回归 + 投影重整理 8 例 + 冲突卡状态机 6 例 | 同上两份 XML + 原 69 例证据 | `rows/E-081.json` |
| 冲突处置 | F-063 | 644 | **BLOCKED→PASS** | 2h 回归：批次44 2504s FATAL 未复现、同 pid 未重启 | batch49 soak + 批次44 `soak-crash-logcat.txt` | `rows/F-063.json` |

证据补件（本会话新增/复制，均注明来源）：
- `jvm/TEST-…SimulatedLearnerTest.xml`、`jvm/TEST-…SchedulingCalibrationViewBuilderTest.xml`、`jvm/TEST-…ReviewPlannerScaleBenchmarkTest.xml`、`jvm/TEST-…KnowledgeClarificationPresenterTest.xml`：从 `build/test-results/**` 原始 JUnit XML 逐字节复制（gates 运行产物，未编辑）。
- `device/06-instrument-green-phase.device.json`、`device/07-instrument-calibration-page.device.json`：设备绑定侧车。**counts**（OK (8 tests)）由同目录 `am instrument` 原始日志逐字转写；**serial/APK sha 不在该日志中**，取自 `device/00-snapshot-and-device-meta.txt` 的构建/安装记录与 02:03-02:05 green run 时间线（见侧车 `binding_provenance`）。`source_log_sha256` 只绑定 counts 来源，不证明 serial/APK 来自日志。

## 2. 25 份失效复核 JSON：按当前 sha 重生成

失效根因（批次49 开工前 `acceptance_report` 复算）：25 份复核的 **被引用源文件**（不是证据日志/XML）在复核后发生字节变化，`file_sha256`/`source_sha256` 与当前树不符；另叠加台账 sha 变更。

| 组 | 行 | 修复内容 |
|---|---|---|
| C | C-053 | `Theme.kt` sha/bytes（5985→7471）与 `witness_source` sha 更新 |
| D | D-019…026、D-036、D-044、D-080（11 份） | `OpenAiCompatibleModelGatewayTest.kt` `source_sha256`（e76f7228…→0811c4cb…）与 head-blob 字段重算 |
| H | H-009、H-014、H-015、H-021、H-026、H-027、H-044、H-052、H-057、H-066、H-070、H-071（12 份） | `PdfAigcContentPolicy.kt`、`MistakePdfExporter.kt`、`MistakePdfEligibility.kt`、`SecondaryScreens.kt`、`ProfileRoute.kt`、`MistakePdfPaperEdit.kt`、`MistakeExportNotifications.kt` 的 `file_sha256` 与 `evidence_sha256` 更新 |
| I | I-024 | `Theme.kt` `file_sha256` 与 `evidence_sha256` 更新 |

共同处理：`ledger_sha256`、`ledger_row`（id/verdict/line/sha）、`evidence_sha256` 全量复算；`run_id` 改为
`2026-11-08T02:45:00Z-batch49-repair-line-review-<域>`，`ledger_batch=49`；`reviewer=batch49-acceptance-ledger-mechanical`
（不在 `authors` 内）；`review_kind=mechanical-recompute`，`independence.independent_agent=false`。
**未改**任何 verdict、证据路径或语义判据；只按当前字节重算 sha。

修复环更正：F-057 假 PASS 回退后，全部 922 份复核的 `ledger_sha256`/`ledger_row` 再次统一重绑到
`264425f5…f6ba8`；F-057 复核重写为 FAIL；F-055/F-063 的 `cmd:` 证据改为 `file:`
（旧脚本 EXIT=0 为字面量，不作为退出证明，见 `device/soak/EXIT-CAPTURE-CAVEAT.md`）。

## 3. 行级复核全量重锚（921→922 份）

- 台账变更后，全部既有行级 JSON 的 `ledger_sha256` 与 `ledger_row` 的 line/sha 统一重绑到
  `264425f5…f6ba8`（与批次 48 的“冻结后重锚”同口径，每行只改绑定字段）。
- 新增 12 份复核（上表），其中 F-055 由 FAIL 复核重写为 PASS 复核、F-063 新建、F-057 回退为 FAIL 复核；其余 9 行替换旧复核。
- `REVIEWS.sha256` 覆盖 922 份 `rows/*.json` + `verdict.schema.json`。

## 4. 门禁复算（本会话实跑，非引用）

| 门 | 命令（逐字） | exit | 关键值 | 证据 |
|---|---|---:|---|---|
| 结构自测 | `python -I tools/ci/acceptance_init.py --check` | **0** | 无输出 | `gate/init-check.txt` |
| 报告 | `python -I tools/ci/acceptance_report.py --out build/b49-acceptance/report-v3.json` | **1** | violations=0；counts 900/22/278/0；`pass_with_review=900`、`pass_with_invalid_review=0`、`pass_without_review=0`；`pass_with_row_specific_review=731`；shared groups=18；state=FAIL（因 22 FAIL 行） | `gate/acceptance-report.json`、`gate/acceptance-report-stdout.txt` |
| 终裁门 | `python -I tools/ci/acceptance_report.py --require-complete` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=22 BLOCKED=278)` + `REVIEW_CHECKS_SHARED 18 组` | `gate/acceptance-report-require-complete.json` |
| lint | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **1** | 144 × `EVIDENCE_REUSED`（全部为批次44–48 既有复用；批次49 证据 0 条入告警）；无新增违规码 | `gate/lint.json` |
| lint 诊断 | 同上 `--warn-only` | **0** | violations=0 | `gate/lint-warn-only.json` |
| 追溯 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | **0** | gaps=0 | `gate/trace.json` |
| runbook 覆盖（默认载体） | `python -I tools/ci/check_blocked_runbook_coverage.py` | **1** | 88/278（默认载体与计划载体错位，批次 47 §6.4 已登记） | `gate/blocked-runbook-coverage-runbook-md.json` |
| runbook 覆盖（计划载体） | 同脚本以 `blocked-runbook.md` 计算 | **0** | **278/278** | `gate/blocked-runbook-coverage-blocked-runbook-md.json` |

说明：`acceptance_report` 的 exit 1 与 `--require-complete` exit 1 是**批次级未收口**（22 FAIL/278 BLOCKED 仍在），
不是本次 12 行或复核链的违规；本次 900 个 PASS 行全部取得有效行级复核（invalid=0）。
18 组 shared-check 与批次 48 同源（D0/GATE 门面），本批未新增共享组。

`docs/acceptance/verdicts.md` 与 `docs/acceptance/domain-index.md` 已由
`docs/acceptance/evidence/2026-11-08-batch-49/make_verdicts_report.py` 按本台账重生成
（逐项表 1200 行、计数 900/22/278/0、行级复核 900/900）；后续复核/修复/终裁轨若再改台账，需重跑该生成器。

## 5. 证据目录布局

| 目录/文件 | 内容 | 来源轨道 |
|---|---|---|
| `p0-closure.md`、`review-log-window/`、`targeted-jvm/`、`lint-prefix/` | P0 预算/窗口/校准口径/前缀判定 | P0 收尾 |
| `calibration/`（含 `cross-process-rerun/`、`report-recompute/`）、`R1-calibration-method-evidence.md` | P1 预注册、G6/权重扫描、复算与确定性 | 标定实验 |
| `jvm/`（4 份 XML） | 本批 JVM 测试原始 JUnit XML（gates 产物复制） | 台账员复制 |
| `calibration-page/` | 校准可见页 JVM/编译/设备证据 | 校准页 |
| `kd67-clarification-closure.md`、`clarification-ux-sources.md` | KD-67 实现与交互研究 | KD-67 |
| `p2-conflict/`、`p2-conflict-handling.md` | 投影/知识点冲突处置 5 份 XML + 说明 | 冲突处置 |
| `device/`（`soak/` 117 轮 monkey 日志 + CSV） | 设备 8 例绿跑、校准页 8 例、DAO 用例、2h soak、APK 绑定 | 设备轨 |
| `unit-gate.md`、`strong-gate.md` | 12 模块单测门（exit 1，9 红均 HEAD 既存）与强门（exit 0） | 门禁轨 |
| `integration-check.md`、`commit-manifest-add.txt` | 集成静态检查与提交清单 | 集成轨 |
| `make_verdicts_report.py` | `docs/acceptance/verdicts.md` / `domain-index.md` 的机械投影器（台账+门禁 JSON → 报告） | 台账员 |
| `EVIDENCE.sha256` | 本目录非 `.mimosa` 全部文件哈希（冻结时点） | 台账员 |

注意：证据树 `.log` 受仓库 `.gitignore:31`（`*.log`）忽略，`git add` 不会入库；本批沿用既有惯例并在
`gate/` 与 00-index 保留命令与摘要。`.mimosa/**` 为 hook 状态，已排除且被 `.gitignore:29` 忽略。

## 6. 未验证 / 残余（不得读成通过）

1. **真实库样本**：P1 标定、per-reason/G6 因果效应、校准页真库读数均为 **BLOCKED**（本批零真库样本）；所有标定数字只是 **model-based**（30 KC/300 题/120 天合成世界）。
2. **arm64 / 真机**：2h soak 与全部设备证据仅覆盖 x86_64 模拟器（API 34）；不外推 arm64/真机。
3. **2h soak 内存**：总 PSS 176,669→242,118 kB（末轮 225,466 kB，约 +49 MB），未做归因；F-056（2h 阈值/PSS 有界）仍 **BLOCKED**，本批不据此宣称内存无问题。
4. **KD-67 生产 app 级链路**：设备用例从 `KnowledgeReviewSessionScreen` 公开入口注入 `clarificationState`；`SmartMistakeBookRoot` 的真实选择器→presenter→状态流未在设备上跑（真 Room 端到端确认/升级未跑）。修复环已补决策消费（Root 返回值不再丢弃、状态机持久化并显示；JVM/设备用例），真 Room e2e 仍阻塞，见 `repair/unit-gate-and-kd67-blockers.md` §3。
5. **冲突队列真实来源**：设备用例用 `reasons={CONFLICTED_KNOWLEDGE}` 构造队列项；planner 打分→reasons→队列的设备级链路未跑（JVM 覆盖）。
6. **校准页截图/无障碍专项（修复环已补）**：`device/screenshots/` 四张 PNG（冲突卡/确认卡/校准页/投影冲突卡）+ `device/a11y/batch49/*.xml` 语义树；`tools/ci/a11y_audit.py audit` 四屏 **STRUCTURAL=PASS**，**CONTRAST=UNVERIFIED**（无颜色输入，不冒充整体通过）。绑定侧车 `device/12-batch49-repair-artifacts.device.json`（APK e19f1b22、test APK 0252bc24、17/17）。
7. **单测门本身 exit 1**：修复环复核 12 模块 4165 例中 9 红仍为 HEAD 既存他线语义红（`repair/unit-gate-after.log`、`repair/unit-gate-and-kd67-blockers.md` §1）；`feature:review` 106/0、`core:domain` 741/0（含 P1-10 模拟上线门）。门判据未达成，登记阻塞。
8. **同批其余 BLOCKED**：E 域并发 7 行 + DST 1 行、F 域 arm64/阈值 32 行等保持原判，未借本批证据转 PASS。
9. **本索引与哈希清单是 2026-11-08 台账冻结时点快照**；后续复核/修复轨若再改台账或证据，需重算 `REVIEWS.sha256`/`EVIDENCE.sha256`。
10. **F-057 维持 FAIL**：本批 2h soak 未命中「超限路径」，不能把未命中当作超限测试通过；回退记录见 §0/§1 与 `rows/F-057.json`。
11. **soak 退出码证据缺口**：历史 `soak.cmd.log` 的 `EXIT=0` 是旧脚本字面量，不作为命令退出证明；修复后的 `f-soak.sh` 已按真实结果写 `OUTCOME`/`EXIT` 并以状态退出（`device/soak/EXIT-CAPTURE-CAVEAT.md`）。
12. **“先做校准题”的第二道题未实现**：现有候选池只有本会话已落库的那道题，选择器 `ASK_CALIBRATION` 在 `FOUNDATION` 候选上没有可达题面；本批只消费决策并如实显示（未给校准题就说未给），卡片不再承诺「多问一道」。要做真正的第二道题需改候选池/持久化身份（`(会话,知识点)` 单槽）——超出本批 feature/review+app 热区与 `RoomStudyDatabase` 不新增 DAO 的约束，登记为阻塞。

## 7. 复跑命令

```bash
cd "D:/smart mistake book"
python -I tools/ci/acceptance_init.py --check
python -I tools/ci/acceptance_report.py --out build/b49-acceptance/report.json
python -I tools/ci/acceptance_report.py --require-complete --out build/b49-acceptance/report-require-complete.json
python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --out build/b49-acceptance/lint.json
python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md --out build/b49-acceptance/trace.json
python -I tools/ci/check_blocked_runbook_coverage.py --out build/b49-acceptance/coverage-runbook.json
python -I docs/acceptance/evidence/2026-11-08-batch-49/make_verdicts_report.py
```
