# 批次 48 记录（2026-11-07）：金标 v2 + 切分修复 + D 域改判（收口版）

> 终态：**NOT ACCEPTED 维持**（批次 48 验收不通过；发版不成立）。台账 **PASS 898 / FAIL 23 / BLOCKED 279**（280→279，唯一 verdict 变更 = D-089 BLOCKED→PASS）；23 条 FAIL 原样保留。金标 v2 已落盘并过校验，但等级如实 **G1（model-labeled）**；AUD-08 切分门对 v2 **FAIL 如实落盘**（① 10/24=0.4167 < 0.9），不因金标存在而转绿。
> 计划：`.zcode/plans/plan-2026-11-07-batch-48-golden-v2.md`（56 项 / WS1–WS5；WS1=1–14，WS2=15–30，WS3=31–42，WS4=43–48，WS5=49–56）。
> 上一批终裁：`docs/research/2026-11-06-batch-47-record.md` §9（NOT ACCEPTED；897/23/280）+ `docs/acceptance/adjudication.md` 2026-11-06 用户三项裁定（真机豁免 / Provider 冒烟 PASS / 金标走代理路径）。
> 本文件结构：§0 口径 → §8 提交清单为本收口记录员新写；各执行件（WS1–WS4、修复环）原文逐字收存于**附录 A**。外部钉值引用（勘误文件所引「record.md §7」「WS4 记录 §6.5」）解析见 §0.3。

---

## 0. 口径与声明

### 0.1 判定与证据口径

- **verdict 四值**：PASS=行判据在当前证据下成立；FAIL=判据不成立且缺陷已登记；BLOCKED=解除条件缺失（**不是通过**）；UNVERIFIED=本环境无法复核。本批不改阈值、不洗绿：FAIL 23 条零变更，BLOCKED 仅 D-089 一条以新证据转 PASS（见 §5）。
- **证据等级**：v2 金标 `evidenceGradeLevel=G1`（单模型标注，GLM-5.3-Flash via ZCode harness，自由文本 `evidenceGrade` 含 `model-labeled, no human expert`——门报告 `/golden/evidenceGrade` 逐字实录）。校验器 C7 枚举仅 G0–G5，任务书字面 "model-labeled" 与枚举冲突，按协议以 G1 落字段、文本承载语义（附录 A §1.2）。
- **盲标红线**：v2 只看页面图像标注；未读切分算法输出、未读 v1 `boxes`。时间线证据：v2 `generatedAt=2026-11-06T16:53:00Z` 早于全部 6 个 run 目录（01:31:44–02:16:02）（附录 A §7.1 #1 实跑复证）。
- **dev/acceptance 切分 16/8**：键=sha256(文件名) 升序，dev 16 页 / acceptance 8 页；**acceptance 只复算一次**（runs/acceptance-once-01，runId 断言，跑后零调参）。修复环复证：final 24 页逐页 0 mismatch、dev 四 runs 页清单 ∩ acceptance = 空集（附录 A §7.1 #5）。
- **门阈值**：① ≥0.9、③ ≤0.05、切残硬红线 `fragmentedQuestions=0` 全部未改（附录 A §7.1 #10 复证 + 本收口 §3 实测）。
- **密钥卫生**：key 只存 gitignored `local.properties`（`git check-ignore -v` = `.gitignore:14`）；一切文件/日志只允许 sha8 `cd10efbc`；全证据树 `sk-[0-9a-zA-Z]{20,}` 命中逐一实读均为 `model-task-<32hex>` 内部任务 ID 子串，三串 sha8 `52272650/2ed9fa64/4c7349b2` 均 ≠ 授权标识（附录 A §7.1 #9、§6.4）。
- **Gradle/设备**：本批 Gradle 仅 WS3 设备取证一次 `:app:assembleLocalFirstDebug`（exit 0）；**单测门/强门/d0 未跑**（任务 47，如实，见 §7）；未跑 connectedLocalFirstDebugAndroidTest；设备轨经 adb（emulator-5554）。
- **6C-2 三项裁定不可变**：真机豁免 / Provider 冒烟 PASS / 金标代理路径。修复环只读复核未漂移：`SmbkSelectionPlan.kt` worktree blob `e550f5f8…` 与 HEAD 逐字节一致、KD-76 仍 open（附录 A §6.3）。
- **热区禁改**：`RoomCaptureWorkflowRepository` 全程零 status 条目；dev 选优=A 后候选 hunk 全回退，切分主码零改动（附录 A §7.1 #6、§7.3）；本批唯一主码旁改动 = 门工具 `check_split_gate.py` 最小 hunk（12+/2-，2 hunk，v1 判定文本逐字保留）。

### 0.2 台账基线迁移

| 量 | 批次 47 终态 | 批次 48 终态 |
|---|---|---|
| ledger sha256 | `a9912c2a…254737` | `28e96a8d…76375` |
| PASS / FAIL / BLOCKED | 897 / 23 / 280 | **898 / 23 / 279** |
| verdict 变更 | — | 恰 1 处（D-089 BLOCKED→PASS） |

### 0.3 节号映射（外部引用解析）

本记录收口前由各执行件逐段追加，原节号 §1–§7 现收存于附录 A（正文逐字未动，仅标题层级降一级）。**勘误文件 `PROVENANCE-ERRATUM.md` 所引「record.md §7」= 附录 A §7（修复环收口，内含勘误文件钉值 sha256=`76c3543f…`）；「WS4 记录 §6.5」= 附录 A §6.5（三份哈希清单）。**

### 0.4 收口复跑声明（本记录员，2026-11-07）

收口时对三项报告门做**只读复跑**（`--out` 指向 gitignored `build/` 新路径，被 B48-5 钉值的产品文件一律未重生成）：`acceptance_report --require-complete` exit 1（898/23/279、shared 18 组、review_errors=[]、dirty 5061）、`trace_requirements` exit 0（1200/gaps 0）、`acceptance_lint` exit 1（144 EVIDENCE_REUSED）——与 B48-1 归档值一致，仅 dirty 5045→5061（他线并行改动）。三份哈希清单 `--verify` 复跑：REVIEWS OK（exit 0，root=docs/acceptance/verdicts）、GATES OK（exit 0）、**EVIDENCE exit 1**（2 处 UNLISTED，见 §6.3）。

---

## 1. 56 项对账

来源：计划 `.zcode/plans/plan-2026-11-07-batch-48-golden-v2.md` §1（已完整读取）。任务号 1–56 全覆盖；✓=完成并有证据在盘；✓*=完成但带如实保留项；✗=未执行。详细证据指针见 §2 各轨与附录 A。

| 任务号 | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 图像来源三选一 | ✓ 来源③ `C:\Users\听云\Pictures\错题数据集`（155 张，仓外；①不存在、②设备无数据） | 基线 §4 |
| 2 | 24 页清单固定 + sha256 | ✓ 24/24 逐字节一致（match=24 missing=0 mismatch=0） | 基线 §4.3 |
| 3–10 | 逐页盲标 24 页（8 项） | ✓ 24 个 `*.A.json`（题数/框/题序/科目/版式 + labelers[] + 独立性声明） | 附录 A §1；`golden-v2/pages/` |
| 11 | 第二标注员复标 ≥8 页 | ✓* B 轮 8 页乱序复标；**同模型第二轮，非独立**（如实声明） | 附录 A §1.2 |
| 12 | 裁定合并 + 等级标注 | ✓* adjudication 块在 v2；`evidenceGradeLevel=G1`（model-labeled 语义入 evidenceGrade 文本） | 附录 A §1.2；v2 JSON |
| 13 | `split_golden_v2.json` + 校验 + sha | ✓ validate **exit 0**（负控 ×3 各 exit 1）；sha=`03b1eecc…18e54`（本收口复算一致） | 附录 A §1.4；本收口 §4.1 |
| 14 | 覆盖 v1 的 7 页 | ✓ 7 页全在 v2 内（仅页清单对比，v1 boxes 未读） | 附录 A §1 |
| 15 | 切分实现与导出格式定位 | ✓ CaptureAssessment 管线（`OpenAiModelTaskAdapters.kt:154`→`CaptureWorkflowRepository.kt:439`）；导出=schemaVersion 2 | 附录 A §4.1 |
| 16 | 建导出通道 | ✓ `GoldenSplitEvalChannelHarness.kt`（测试源集，assumeTrue 门控）；前置 probe E-48-SPLIT-PROV HTTP 200 | 附录 A §4.1；`splitter/probe-vision-result.json` |
| 17–24 | dev 16 页先红后绿调参（8 项） | ✓* 先红（A ① 5/16=0.3125）达成；**"后绿"未达成**——候选 B/C/D 全部更差，选 A 回退，主码零改动 | 附录 A §4.1；本记录 §4.2 |
| 25–28 | acceptance 8 页只复算一次（4 项） | ✓ 单次（runs/acceptance-once-01）：① 5/8=0.6250、② 13/26=0.50、③ 3/26=0.1154；跑后零调参 | 本记录 §4.3 |
| 29–30 | 门 v2 接入（2 项） | ✓ `check_split_gate.py --golden v2` 最小 hunk（61/61 测试过）；最终门 **FAIL exit 1** 如实落盘 | 附录 A §4.1；本记录 §4.4 |
| 31 | Provider 行 blocked_by 更新 | ✓ D-082..090（9 行）统一更新 | 附录 A §5.2 |
| 32–35 | 产品路径设备验证（4 项） | ✓* E-48-D-DEV-01：CAPTURE_ASSESS 真实 SUCCEEDED；PROBLEM_CLASSIFY 观测窗无终态、TUTOR 未创建（如实） | 附录 A §5.1 |
| 36–40 | arm64/真机豁免注记（5 项） | ✓ 21 行 blocked_by 注记；verdict 维持 BLOCKED 不伪造 PASS | 附录 A §5.2 |
| 41–42 | 行级 JSON + 台账同步（2 项） | ✓ `rows/D-089.json`（6 checks）+ ledger 921 行 sha 重锚（每行恰 3 行 diff，本收口抽验 A-001.json 证实） | 附录 A §5.2；本收口 |
| 43 | `acceptance_report --require-complete` | ✓ exit 1（898/23/279；NOT_ACCEPTANCE_COMPLETE） | 附录 A §6.1 |
| 44 | lint / trace / 哈希清单 | ✓ lint 1（144）/warn-only 0；trace 0；三清单 verify 0/0/0 | 附录 A §6.1、§6.5 |
| 45 | verdicts.md/域索引重生成 | ✓ verdicts.md 1315 行 + domain-index.md 32 行（绑 ledger sha `28e96a8d…`） | 附录 A §6.2 |
| 46 | `adjudication.md` 更新 | ✓ B48 终裁（B48-0..B48-5 + 签字块） | `adjudication.md` |
| 47 | 单测门 + 强门 | **✗ 未跑**（不在各执行件 ask；批次 47 的 GRADLE_EXIT=0 不代表本批） | 附录 A §6.7.1 |
| 48 | 修复环（如有 findings） | ✓ 13 findings（中 2 低 11）→ 勘误 A/B/C + 残余登记，零代码零台账 | 附录 A §7 |
| 49 | 基线 | ✓ `docs/research/2026-11-07-batch-48-baseline.md` | 基线文件 |
| 50 | 证据归档 | ✓ EVIDENCE 323 / REVIEWS 934 / GATES 9（本收口复验见 §6.3） | 附录 A §6.5 |
| 51 | 对抗复核 | ✓ 13 findings | 附录 A §7.1 |
| 52 | 修复 | ✓ 勘误文件 `PROVENANCE-ERRATUM.md`（sha `76c3543f…`） | 附录 A §7 |
| 53 | 记录 | ✓ 本文件 | 本文件 |
| 54 | 台账 | ✓ ledger + D0-030..035 计数行（ledger_pass 897→898 等）复算通过 | 附录 A §5.2 |
| 55 | 记忆 | **✗ 未执行**：本记录员在 plans/docs/证据树内未找到任何批次 48 记忆更新执行件（.zcode/memory 无批次 48 条目），如实登记 | 本节 |
| 56 | 推送清单 | ✓ 本记录 §8 提交清单 | 本文件 §8 |

**对账结论**：56 项中 53 项完成（含 6 项带如实保留），2 项未执行（任务 47 单测门+强门、任务 55 记忆），0 项洗绿。计划 §3 验收判据逐条：v2 过 validate 且 sha 固化 ✓；门对 v2 ①/③ —— ③ 0.0385 ≤0.05 过、**① 0.4167 <0.9 未过，未达标原因已如实落盘**（§4.5）；D 域相关行有新证据与行级复核 ✓（部分行维持 BLOCKED 如实）；报告/终裁更新 ✓；双门 exit=0 **未达成**（require-complete exit 1、AUD-08 exit 1——与终裁 NOT ACCEPTED 一致，不构成隐藏失败）。

## 2. 各轨

| 轨 | 范围 | 终态 | 关键产物 | 如实缺口 |
|---|---|---|---|---|
| WS1 金标 v2（1–14） | 盲标 24 页 → v2 产物 | 完成；G1 如实；A/B 一致性 8/8 题数、mean IoU 0.9641 | `tools/split_golden/tables/split_golden_v2.json`（24 页 78 框）+ 旁车；`golden-v2/` 32 页级 JSON + agreement + GUIDELINE | B 非独立；坐标目测 ±0.01–0.02 无人工复核；`heldOutUnusedForTuning` 自证字段 UNVERIFIED |
| WS2 切分（15–30） | 导出通道 + dev 调参 + acceptance 单次 + 门 v2 | 完成；**门 FAIL 如实**；主码零改动 | `GoldenSplitEvalChannelHarness.kt`；`splitter/`（6 runs、逐配置导出与度量、final 导出、门报告/证据/裁定） | ① 0.4167 未达 0.9；切残 3≠0；门值单轮采样不可外推 |
| WS3 D 域改判（31–42） | 设备取证 + 行级改判 | 完成；30 行 cell 变更；唯一 verdict 变更 D-089→PASS | `d-domain/` 8 文件 + `rows/D-089.json`；ledger `28e96a8d…` | D-082..088/090 维持 BLOCKED 的条件逐行在案；TUTOR/PROBLEM_CLASSIFY 无终态 |
| WS4 报告终裁（43–46） | 门禁重跑 + 报告重生成 + 终裁 | 完成；NOT ACCEPTED 维持 | `acceptance-report.json`、`verdicts.md`、`domain-index.md`、B48 终裁、3 清单 | 单测门/强门未跑；GATE-091/092/099 台账行证据未重锚 |
| WS5 收口（49–56） | 基线/归档/复核/修复/记录/台账/记忆/推送 | 7/8 完成 | 基线、3 清单、勘误、本记录、§8 提交清单 | 任务 47 已列 WS4；任务 55 记忆未执行 |

## 3. 门禁 exit

汇总批次 48 全部门禁。**归档值**=执行件当轮实跑（证据在盘）；**收口复跑**=本记录员 2026-11-07 在当前树上复跑（`--out` 均指 gitignored `build/batch48-closeout-*`，钉值产品未重生成）。

| 门 | 命令（逐字） | exit | 关键值 | 轮次 |
|---|---|---:|---|---|
| 金标校验 | `python -I tools/split_golden/validate_split_golden.py --file tools/split_golden/tables/split_golden_v2.json` | **0** | C1–C7 全过；24 页 78 框；G1 | WS1 归档 |
| 金标负控 ×3 | 改坐标/等级 G4/questionCount=99 副本 | **1/1/1** | 必红实证 | WS1 归档 |
| 门工具单测 | `pytest tools/tests/test_split_gate.py`（61 用例） | 0 | 61/61 | WS2+修复环归档 |
| **AUD-08 切分门（v2）** | `check_split_gate.py --golden v2`（最终导出 24 页 42 框） | **1（FAIL）** | ① 10/24=0.4167 <0.9 未过；② 32/78=0.4103（只度量）；③ 3/78=0.0385 ≤0.05 过；硬红线 fragmentedQuestions=3≠0 未满足 | WS2 归档；本收口实读 `gate-report-v2-01.json` 复核数值一致 |
| 验收报告 | `python -I tools/ci/acceptance_report.py --require-complete --out docs/acceptance/acceptance-report.json` | **1** | 898/23/279；NOT_ACCEPTANCE_COMPLETE；shared 18 组；row_specific 729；review_errors=[]；dirty 5045 | WS4 归档 |
| 验收报告（收口复跑） | 同上，`--out build/batch48-closeout-report.json` | **1** | 898/23/279；18 组；errors=[]；dirty **5061**；ledger sha `28e96a8d…` 不变 | 本收口实跑 |
| 报告（partial） | `acceptance_report.py --partial` | 1 | state=FAIL 脚本语义；violations=0 | WS3 归档 |
| lint | `python -I tools/ci/acceptance_lint.py --out …` | **1** / `--warn-only` **0** | 144 EVIDENCE_REUSED（0 条涉批次 48 新证据） | WS4 归档 |
| lint（收口复跑） | 同上新 out | **1** | violations=144（码同）、warnings=144 | 本收口实跑 |
| 追溯 | `python -I tools/ci/trace_requirements.py --out …` | **0** | total=1200、gaps=0（归档与收口复跑一致） | WS4+本收口 |
| runbook 覆盖（默认载体 runbook.md） | `check_blocked_runbook_coverage.py` | **1** | 88/279（工具-计划载体错位，批次 47 §6.4 同源） | WS4 归档 |
| runbook 覆盖（blocked-runbook.md 载体） | 进程内替换常量（不改工具源） | **0** | **279/279**、missing=0 | WS3+WS4 归档 |
| 行级复算 | `validate_review`（acceptance_review.py）×921 份 | — | **898 PASS / 23 FAIL / errors=0** | WS3 归档（`d-domain/regen-validation.json`） |
| 三份哈希清单 verify | `acceptance_hash_manifest.py --root … --verify …` | **0/0/0** | EVIDENCE 323、REVIEWS 934、GATES 9 | WS4 归档 |
| 清单 verify（收口复跑） | 同上 | **0 / 0 / 1** | REVIEWS OK、GATES OK；**EVIDENCE exit 1：2 处 UNLISTED**（§6.3） | 本收口实跑 |
| Gradle | `:app:assembleLocalFirstDebug`（设备取证用） | **0** | 28s；APK sha `147ca5a4…` | WS3 归档 |
| Provider 探针/冒烟 | E-48-PROV-01（chat ×3）；E-48-SPLIT-PROV（图像 Q7M2） | 0 / HTTP 200 | key sha8 `cd10efbc`；probe `finishReason=stop`、reply=`Q7M2` | 批次 47 末+WS2 归档（本收口实读 probe JSON） |
| **未跑** | 单测门、强门、d0、connectedLocalFirstDebugAndroidTest | — | — | 如实（附录 A §6.1"未跑"节） |

## 4. 金标 v2 与门度量（dev/acceptance 分列）

### 4.1 金标 v2 事实

- 24 页 / 78 框；sha256=`03b1eeccbbde19e5ab2e67d5e7de9bf1c1f2b1d8124c3e4b1e6cfe2091e18e54`（本收口复算与旁车一致）；validate exit 0；等级 G1（model-labeled）。
- 盲标：v2 冻结（2026-11-06T16:53:00Z）早于全部算法 run（01:31:44–02:16:02）；标注阶段零算法接触。
- 一致性（A vs B，8 页重叠，**同模型第二轮**）：题数 8/8 一致（κ=1.0）；框配对 35/35 IoU≥0.5，mean IoU 0.9641、min 0.8448、恰 5 对 IoU=1.000（`agreement-A-B.json`）。
- 指南：`GUIDELINE.md` 全文 sha=`38763dca…f480`（v1.1 含横拍坐标补遗；32 份页级 JSON `promptSha256` 均为 v1 基础版 `408d3337…`——勘误 B 全量落值，附录 A §7.1 #3）。
- v1 一个字节未动：`split_golden_v1.json` sha256=`f9e92c47…e4c9f`（**本收口复算一致**）。
- v2 语义裁定要点：questionCount=判定框数（与门分母同义）；印刷题号差异 3 页（005144/005301/011017）如实登记；附图/页边手写处置逐页 notes 在案（附录 A §1.3）。

### 4.2 dev 16 页（调参轨，可多轮）

| 配置 | ① countExact | ② IoU≥0.5 | ③ fragment | 处置 |
|---|---|---|---|---|
| A=原生产代码（prompt 原文，temp 0.1） | **5/16=0.3125** | 19/52=0.3654 | 0/52=0.0 | **选定**（RED 基线即最优） |
| B=prompt 三条款 + temp 0.1 | 1/16=0.0625 | 13/52=0.25 | 2/52=0.0385 | 回退 |
| C=prompt 三条款 + temp 0 | 1/16=0.0625 | 12/52=0.2308 | 0/52=0.0 | 回退（塌向 PASS） |
| D=原 prompt + temp 0 | 4/16=0.25 | 18/52=0.3462 | 1/52=0.0192 | 回退 |

（来源：`splitter/README.md` 运行索引 + 逐 `dev-*-metrics.txt`，本收口实读；另 dev-diag-02 诊断 run 在 runs/。）dev 失分模式：密排双栏页少计（005144 金标 7 → 应用 0–6）、相邻题合并、多题页整页判 NEED_MORE_IMAGE 零区域。

### 4.3 acceptance 8 页（**只复算一次**，runs/acceptance-once-01）

- ① 5/8=**0.6250**（未过 0.9）；② 13/26=0.5000；③ 3/26=**0.1154**（未过 0.05）；切残 3 题落在 003928/004353/010202。跑后零调参（来源：`acceptance-once-01-metrics.txt`，本收口实读全文）。

### 4.4 最终门（24 页 = dev∪acceptance，`app-split-golden-v2-final.json`，sha `abb7744f…`）

- **① 10/24=0.4167 <0.9 未过；② 32/78=0.4103；③ 3/78=0.0385 ≤0.05 过；硬红线 fragmentedQuestions=3≠0 未满足 → 门 FAIL（exit 1）**（`gate-report-v2-01.json`，本收口实读复核）。
- 门报告 scope 块声明：单轮采样值，不可外推到模型均值。

### 4.5 未达标原因（如实，不洗绿；详见附录 A §4.2）

1. 模型判题数能力与门阈值 0.9 之间为结构性差距（四配置最好 5/16）。
2. NEED_MORE_IMAGE 的产品语义 vs 金标语义差（页缘截断 vs 完整可见题）；prompt 微调反使模型塌向 PASS。
3. 模型随机性：同配置同页跨轮 ±1–2 框、decision 翻转。
4. 契约拒收零星发生（信封键/未配 ADD_IMAGE）；解析宽容化属产品裁定，未动。
5. 门 FAIL 解除属后续工作（模型能力升级或产品语义裁定），不在本批最小 hunk 范围（附录 A §4.4.2）。

## 5. 改判清单

**唯一 verdict 变更：D-089 BLOCKED→PASS**。防洗绿核验（WS4+修复环两轮独立实跑 + 本收口引用）：`git show HEAD:ledger`（`a9912c2a…`）对盘上（`28e96a8d…`）逐行比对，两侧各 1200 行，verdict 列变更恰 1 处；cell 变更恰 30 行。无 PASS→FAIL、无其他 BLOCKED→PASS、FAIL 23 条零变更。

| 组 | 行 | 变更内容 | verdict 影响 |
|---|---|---|---|
| Provider 取证 | D-082..D-090（9 行） | blocked_by 统一更新：key 具备（sha8 `cd10efbc`）+ E-48-PROV-01 冒烟 PASS + E-48-D-DEV-01 产品路径部分取证，剩余条件逐行登记 | 仅 D-089→PASS（证据头四项齐备 + `rows/D-089.json` 6 checks 可复算）；其余 8 行维持 BLOCKED（如 D-082 需上游请求 id 绑定，产品路径现状不可得——附录 A §5.2） |
| arm64/真机豁免 | F-006/015/024/033/042/051/060/069/078/087、J-035/037/038/039/042/043、FIX-041..045（21 行） | blocked_by 改「用户裁定豁免（2026-11-06，adjudication.md 追加裁定①）」注记（19 行前缀替换 + 2 行尾注） | 全部维持 BLOCKED，不伪造 PASS |
| 台账计数行 | D0-030..035（6 行） | direct_recount 声明值随批更新（ledger_pass 897→898、row_json_files 920→921、sha 重锚），复算通过 | 无 verdict 变更 |
| 行级 JSON 重锚 | 921 份 `rows/*.json` | 每行恰 3 行 diff：`ledger_sha256` `a9912c2a…`→`28e96a8d…`（本收口抽验 A-001.json diff 证实） | 无 verdict 变更 |

## 6. 复核/修复

### 6.1 对抗复核与修复环（任务 51–52）

13 findings（中 2、低 11），全部处置并本会话实跑复证：#2 `ranAtUtc` 预排时刻表（中）→ 勘误 A；#3 v1.1 补遗未绑定页级 JSON（中）→ 勘误 B；#11 00-index 时间线两处出入 → 勘误 C；#4/#12 残余登记；#10 一处措辞更正（`check_split_gate.py` 实为 2 hunk）；其余确认。**修复环零代码、零台账、零冻结清单变更、门未重跑**；唯一产物 `PROVENANCE-ERRATUM.md`（sha=`76c3543f41a687aa36090f608a0b2f04b935ec8d252a5935aa249ca728834d06`，本收口复算一致；不入 EVIDENCE 清单——根清单自哈希已被 A §6.5/终裁 B48-5 钉值）。逐条复证与复核边界（6 条 UNVERIFIED）见附录 A §7.1/§7.2。

### 6.2 行级与只读复核（任务 48 附带）

- D-089 行级复核：`rows/D-089.json` 恰 6 checks（ledger_row/cmd_exit/device_binding/file_sha256×3），`ledger_sha256=28e96a8d…`，`review_kind=mechanical-recompute`；`d-domain/EVIDENCE.sha256` 8/8 OK。
- 6C-2 只读复核：`SmbkSelectionPlan.kt` blob 与 HEAD 一致（status 幻影 M 经 `git update-index --refresh` 消解）；KD-76 open。
- 密钥卫生独立抽验：`sk-` 模式命中全部为 `model-task-` 子串，无密钥本体。

### 6.3 收口新发现（本记录员实跑，登记不改动）

1. **EVIDENCE 清单 verify exit 1（2 处 UNLISTED）**：① `PROVENANCE-ERRATUM.md`——按设计自排除（勘误头声明），非缺陷；② `splitter/.mimosa/hook-state/sess_…-actor_8_1.json`——**清单冻结后落入证据树的 hook 运行态文件**。另查：冻结清单内已含 2 份 hook-state 运行态文件（`d-domain/…actor_4_1.json`、`splitter/…actor_3_1.json`），与批次 47 "误收 1 份 hook 运行态文件"同类。**处置：登记不动**（根清单自哈希 `803b0829…` 被 A §6.5/B48-5 钉值，追加会使 323 项快照失配；勘误先例即为此设）。移交下批：证据打包时排除 `.mimosa/` 运行态目录，勘误文件另册。
2. 共享树漂移：dirty 551（基线）→5045（WS4）→**5061（本收口）**；他线在场改动包括 `core/database` 20 路径、`core/model` 4 路径（KnowledgeQuizTasks/PdfAigcContentPolicy 等，知识线）、`app` ReviewReminder/visualjs、`tools/ci/h_compliance_gate.py` 等——**均非批次 48 产物**，§8 提交清单不纳入。
3. `.gitignore` 的 M（`.superdesign/tmp/`）归属 superdesign 线（diff 实证），非本批。

## 7. 遗留（全部如实，含 UNVERIFIED）

1. **AUD-08 门 FAIL 未解除**：① 0.4167<0.9 + 切残 3≠0。解除需模型能力升级或产品语义裁定（NEED_MORE_IMAGE 语义、解析宽容化、切分后处理）；在此之前门维持 FAIL（6C-2 裁定③"不洗绿"延续）。
2. **单测门/强门/d0 未跑**（任务 47）：批次 47 §9.1 的 GRADLE_EXIT=0 不代表本批；`d0_self_acceptance` 的 `h-061_fail` 陈旧预期仍未更新（批次 47 §6.5 遗留）。
3. **GATE-091/092/099 台账行证据仍指批次 46 门日志**；v2 门证据在 `splitter/`，台账行证据重锚属后续门禁轮。
4. **金标 G1 局限**：无人工复核、无跨标注员独立性（B=同模型第二轮）；坐标目测 ±0.01–0.02；`heldOutUnusedForTuning` 过程性声明无独立证据（终裁 B48-3 不签 3/4）。
5. **D 域剩余**：D-082 上游请求 id 绑定（需网关日志或代理取证）；D-083..088 失败注入类证据未取（401/429/5xx/取消/限流）；D-090 无 key 复跑场景未发生；PROBLEM_CLASSIFY 长流式无终态（需传输层日志或上游面板）、TUTOR 任务未创建。
6. **门值单轮采样**：各配置 ① 为一次 16 页采样，同配置跨轮 ±1–2 框，不可外推（附录 A §4 README 边界节）。
7. **harness 默认跳过路径未实测**（无 env → assumeTrue skip；6 次激活运行全部真跑）（附录 A §4.4.1）。
8. **任务 55（记忆）未执行**：未找到任何批次 48 记忆更新执行件；如需记忆落点，属用户/主会话动作。
9. **工具侧修正待 owner**：`check_blocked_runbook_coverage.py` 默认载体仍指 `runbook.md`（88/279 exit 1）；计划载体需进程内替换才 279/279。
10. **共享树未收口**：5061 脏路径（多线并行）；J-001 类 `--require-clean` 前提在提交前结构性不可满足（批次 47 §6.7 已记）；干净检出不可复算（批次 47 §8.4）。
11. **运行态文件混入证据树**（§6.3.1）：下批证据打包须排除 `.mimosa/`。

## 8. 提交清单

**范围声明**：以下仅为批次 48 产物。工作树另有他线改动（知识线 `core/database`/`core/model`/kb 工具、ReviewReminder/visualjs、`tools/ci/h_compliance_gate.py`、`.gitignore` 等）**不在本清单**，提交时严禁整树 `git add -A` 扫入。`local.properties`（gitignored）与 `build/batch48-*`（gitignored）不入库。

| 块 | 路径 | 状态 | 说明 |
|---|---|---|---|
| 1 金标与门工具 | `tools/split_golden/tables/split_golden_v2.json`、`…v2.json.sha256`；`tools/split_golden/check_split_gate.py`（M） | ?? / M | v2 + 旁车（sha `03b1eecc…`）；门工具最小 hunk（12+/2-，v1 文本逐字保留） |
| 2 导出通道 | `core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/model/GoldenSplitEvalChannelHarness.kt` | ?? | 测试源集 harness |
| 3 验收面 | `docs/acceptance/ledger.md`、`acceptance-report.json`、`verdicts.md`、`domain-index.md`、`blocked-runbook.md`、`adjudication.md`（均 M）；`docs/acceptance/verdicts/rows/**`（920 M + `D-089.json` ??） | M/?? | rows=921 份 sha 重锚（每行 3 行 diff）+1 新行级复核 |
| 4 验收证据 | `docs/acceptance/evidence/2026-11-07-batch-48/**`（gate/ 7 件、REVIEWS.sha256、GATES.sha256、make_verdicts_report.py） | ?? | WS4 门禁归档 |
| 5 研究证据 | `docs/research/evidence/2026-11-07-batch-48/**`（golden-v2/、splitter/、d-domain/、EVIDENCE.sha256、PROVENANCE-ERRATUM.md） | ?? | WS1/WS2/WS3/修复环证据（含 hook-state 运行态文件混入的既有事实，§6.3.1） |
| 6 记录 | `docs/research/2026-11-07-batch-48-baseline.md`、`docs/research/2026-11-07-batch-48-record.md`（本文件） | ?? | 基线 + 收口记录 |

- 建议提交顺序：块 1–2（代码面）→ 块 4–5（证据）→ 块 3（验收面，绑证据 sha）→ 块 6（记录）。提交前重跑三清单 `--verify`（收口基线：REVIEWS/GATES OK；EVIDENCE 2 处 UNLISTED 为已登记既有态）。
- 提交不改变终裁：**NOT ACCEPTED 维持**，直至 §7.1/§7.2 条件解除后重跑验收。

---

## 附录 A：执行件分段记录（收口前原文，未改动）

> 本附录收存本记录收口前由各执行件逐段写入的 §1–§7 原文：仅标题层级整体降一级（## → ###、### → ####），编号与正文**逐字未动**。文内交叉引用（如「§1.2」「§4.4」「§6.5」「§7」）均指原节号，即本附录内同号小节。
> 外部钉值引用解析：`PROVENANCE-ERRATUM.md` 所引「record.md §7」= 本附录 §7（内含勘误文件 sha256=`76c3543f…` 钉值）；「WS4 记录 §6.5」= 本附录 §6.5（三份哈希清单）。
> 原文件头（收口前最后状态）如下，收口后的总状态以本记录开头块与 §0 为准：

> 状态：**WS1（v2 金标）本会话完成并过校验；WS2（切分导出通道 + dev 修复环 + acceptance 单次复算 + 门 v2 接入）已完成（门 FAIL 如实落盘）；WS3–WS5 由各自任务执行，本文件随批次推进追加。**
> 计划：`.zcode/plans/plan-2026-11-07-batch-48-golden-v2.md`（WS1 = 任务 1–14；WS2 = 任务 15–30）。
> 上一批终裁：`docs/research/2026-11-06-batch-47-record.md` §9（NOT ACCEPTED）+ `docs/acceptance/adjudication.md` 三项裁定（真机豁免 / Provider 冒烟 PASS / 金标走代理路径）。
> 红线遵守：盲标（只看页面图像）；dev/acceptance 16/8 切分未动；`evidenceGradeLevel` 如实标 G1（model-labeled）；门阈值 0.9 未改；v1 一个字节未动（sha256 复验 `f9e92c47…` 逐字节一致）；密钥卫生——WS1 零接触；WS2 从 gitignored `local.properties` 将 `SMB_REAL_PROVIDER_KEY` 注入进程环境（Provider 视觉探测与导出通道），任何仓内文件/日志只允许 sha8 `cd10efbc`，密钥本体零落盘（`sk-…` 模式全证据树 grep 零命中；`git check-ignore local.properties` 实证 ignored）。

### 1. WS1 v2 金标（本会话执行件）

| 项 | 结果 |
|---|---|
| 任务 1 图像来源 | 沿用基线裁定：来源③ `C:\Users\听云\Pictures\错题数据集`（仓外 155 张）；基线见 `docs/research/2026-11-07-batch-48-baseline.md` §4 |
| 任务 2 固定 24 页 | 本会话独立复算：抽样框 155、下标 0,6,…,138 → 24 页，24/24 sha256 与计划 §4 逐字节一致（match=24 missing=0 mismatch=0） |
| 任务 3–10 盲标 24 页 | 标注员 A（model-labeled）：每页一 JSON（含 labelers[]、独立性声明、strata、逐框 notes），落 `docs/research/evidence/2026-11-07-batch-48/golden-v2/pages/`（24 个 `*.A.json`） |
| 任务 11 第二轮复标 | 标注员 B（**同模型第二轮**，乱序 8 页：012103/003928/005144/010034/004511/011358/010859/005711；写毕 B JSON 前未读任何 A JSON）：8 个 `*.B.json`；一致性见 §1.2 |
| 任务 12 裁定合并 | A 轮为基线（网格裁片+像素行带测量）；B 轮仅作一致性证据；自裁定如实登记（非独立第三方），`adjudication` 块见 v2 |
| 任务 13 v2 产物 | `tools/split_golden/tables/split_golden_v2.json` + `.sha256` 旁车；`validate_split_golden.py` **PASS（exit 0）**；24 页 78 框；sha256=`03b1eeccbbde19e5ab2e67d5e7de9bf1c1f2b1d8124c3e4b1e6cfe2091e18e54` |
| 任务 14 v1 覆盖 | v1 的 7 页（003928/004304/004353/005605/010349/011126/011800）全部在 v2 的 24 页内（仅对比页清单，v1 boxes 全程未读取） |

#### 1.1 盲标红线执行

- 标注阶段（A/B 两轮）未打开 `tools/split_golden/` 下任何文件；未读任何切分算法代码/输出；未读 v1 `boxes`（v1 仅在本轮标完且 v2 冻结后以脚本提取 `file` 字段做页清单对比）。
- 指南先于开标冻结：`docs/research/evidence/2026-11-07-batch-48/golden-v2/GUIDELINE.md`（v1 基础版 sha256=`408d3337…a980`，即 labelers[].promptSha256；v1.1 追加横拍页坐标空间补遗 sha256=`38763dca…f480`）。
- 侧拍页旋转方向逐页实测确认（rotate90cw：004304/004511；rotate90ccw：004353/004854/004952/011358/011649/011800；无旋转：003928/005144/005605/005711/005855/010034/010202/010306/010349/010633/010859/011017/011126/011248/012103）；横拍页 v2 坐标记 upright 空间，raw 空间对照 `rawBox` 留存在页级证据 JSON。

#### 1.2 一致性（A vs B，8 页重叠）

- 逐页题数完全一致 **8/8**（原始一致率 1.0，Cohen κ=1.0）。
- 框贪心匹配 **35/35 对 IoU≥0.5**（配对率 1.0）；平均 IoU **0.9641**，最小 0.8448（004511 承接块，复核后仍取 A 轮）。
- 原始输出：`docs/research/evidence/2026-11-07-batch-48/golden-v2/agreement-A-B.json`。
- **如实声明**：B 为同模型同会话第二轮，不构成协议 §3.3 的独立双人标注；该一致性只证明同一标注者两次判读稳定，不证明跨标注员一致性。`evidenceGradeLevel` 因此为 **G1**（单模型标注），不是 G2/G4；`evidenceGrade` 自由文本含 `model-labeled`。任务书原文"evidenceGradeLevel=model-labeled"与校验器 C7 枚举（仅 G0–G5）冲突，按协议以 G1 落字段、以 `evidenceGrade` 文本承载 model-labeled 语义，未虚报等级。

#### 1.3 v2 语义裁定（adjudication 要点）

- `questionCount` 记**判定框数**（=len(boxes)，与校验器 C3 及门指标分母语义一致）；印刷题号数不同的三页如实登记：005144（6 题 7 框，题 13 跨栏）、005301（2 题 3 框，题 14 跨栏）、011017（2 题 3 框，上题尾部分两段）。
- 附图按指南不入题干框，但轴对齐外接矩形无法排除的页（004353/005144/005301/00633/011017/011126/011649/011800/012103）保留整框并登记 `strata.figureOwnership`。
- 页边手写/草图按指南不入框（003928/010306/011358 等），页级 notes 与证据 JSON 逐条登记。
- `split.heldOutUnusedForTuning=true` 为**自证字段**：标注阶段零算法接触属实；其"acceptance 未参与调参"的最终真伪取决于 WS2 是否只复算一次——本记录标注为 **UNVERIFIED**，不得据 C7 绿当作已核实。

#### 1.4 校验与负控（本会话实跑）

```text
$ python -I tools/split_golden/validate_split_golden.py --file tools/split_golden/tables/split_golden_v2.json
→ 拆题金标校验 PASS（C1 冻结字节 24522 B、C2 键集合、C3/C4 24页78框、C5 无重复、C6 24>=7、C7 G1）  exit=0
负控（改动副本必红，真机验证）：
  neg1 改一框坐标   → C1 旁车/sha 不符        exit=1
  neg2 等级改 G4    → C7 要求 review/reviewedBy/human labelers  exit=1
  neg3 questionCount=99 → C3 与框数脱钩        exit=1
$ v1 sha256 BEFORE/AFTER = f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f（逐字节未动）
$ git status --porcelain -- tools/split_golden/ → 仅新增 v2 与旁车，v1 无修改记录
```

### 2. UNVERIFIED / 移交（如实）

1. **UNVERIFIED**：坐标为模型目测估读（渲染图+网格裁片+行带测量），预期精度 ±0.01–0.02（归一化），无人工复核；等级 G1 如实。
2. **UNVERIFIED**：`heldOutUnusedForTuning` 的最终真伪（见 §1.3）；acceptance 只跑一次由 WS2 任务 25–28 执行，本批未跑任何门值复算。
3. **移交 WS2**：`--golden v2` 接入 `check_split_gate.py`、dev 调参（16 页）、acceptance 单次复算（8 页）；切分主码改动需最小 hunk（工作树 551 脏路径未收口，热区只出 patch）。
4. **边界说明**：本批 24 页中 5 页为对开书页（对页窄条不入框）、3 页为横幅照片，页级 notes 已逐页登记判定边界；这些是 model-labeled 判断，供 WS2 与复核者重点抽查。

### 3. 产物清单（本会话新增）

| 文件 | 说明 |
|---|---|
| `tools/split_golden/tables/split_golden_v2.json` | v2 金标（24 页 78 框，G1，canonical LF） |
| `tools/split_golden/tables/split_golden_v2.json.sha256` | 旁车 `03b1eecc…18e54  split_golden_v2.json` |
| `docs/research/evidence/2026-11-07-batch-48/golden-v2/GUIDELINE.md` | 标注指南（v1 + v1.1 坐标补遗） |
| `docs/research/evidence/2026-11-07-batch-48/golden-v2/pages/*.A.json`（24） | A 轮逐页标注证据 |
| `docs/research/evidence/2026-11-07-batch-48/golden-v2/pages/*.B.json`（8） | B 轮复标证据 |
| `docs/research/evidence/2026-11-07-batch-48/golden-v2/agreement-A-B.json` | 一致性计算原始输出 |
| `docs/research/2026-11-07-batch-48-record.md` | 本记录 |

### 4. WS2 切分导出通道 + 修复环 + 门 v2 接入（2026-11-07 执行件，任务 15–30）

详细证据与运行索引：`docs/research/evidence/2026-11-07-batch-48/splitter/README.md`（+ `MANIFEST-SHA256.txt`）。

#### 4.1 结果一览

| 项 | 结果 |
|---|---|
| 任务 15 切分实现定位 | 切分=模型任务：`CaptureAssessmentInput` → 单页 prompt（`OpenAiModelTaskAdapters.kt:154`）→ DeepSeek → `CaptureAssessment.questionRegions` → `isUsableSplitRegionSet` 消费判据（`CaptureWorkflowRepository.kt:439`）→ `BatchSplitRecognizer` 落台账；"应用导出"格式 = `export_split_eval.py` schemaVersion 2 |
| 任务 16 导出通道 | **已建成**：`GoldenSplitEvalChannelHarness.kt`（core:data 测试源集，assumeTrue 门控）复用真实 requestBody/parseCompletion/契约/消费判据；出网副本按 `EgressImageBudget` 口径（PIL，divergence 如实）；**前置实测 Provider 图像输入可用**（E-48-SPLIT-PROV：Q7M2 probe HTTP 200，推翻 E-48-PROV-01 "图像能力未测"保留项） |
| 任务 17–24 dev 16 页 | **先红**：A=原生产代码 ① 5/16=0.3125（③ 0.0）。三个候选修复配置全部实测**更差或未改善**：B（prompt 三条款+temp0.1）① 0.0625；C（prompt+temp0）① 0.0625；D（原 prompt+temp0）① 0.25。**dev 选优=A：候选 hunk 全部回退，主码零改动**（git status 实证 model 目录干净）。逐配置度量文件 `dev-*-metrics.txt` |
| 任务 25–28 acceptance 8 页 | **只跑一次**（runs/acceptance-once-01，runId 断言）：① 5/8=0.6250 未过；② 13/26=0.50；③ 3/26=0.1154 未过；跑后零调参 |
| 任务 29–30 门 v2 | `check_split_gate.py --golden v2`：最小 hunk 使 SCOPE_REASONS 按金标版本选择（v1 文本逐字保留；`tools/tests/test_split_gate.py` 61/61 过）。最终导出 `app-split-golden-v2-final.json`（24 页 42 框，逐页 origin 登记）→ **门 FAIL（exit 1）：① 10/24=0.4167 < 0.9 未过；③ 3/78=0.0385 ≤ 0.05 过；切残硬红线 fragmentedQuestions=3≠0 未满足**。报告/证据/裁定三件落盘 |

#### 4.2 未达标原因（如实，不洗绿）

1. **模型判题数能力与门阈值 0.9 之间是结构性差距**：门 ① 要求逐页题数完全一致；deepseek-flash 在四配置×16 页上最好一次 5/16。密排双栏页少计（005144：7→4–6）、相邻题合并（4 页差 1）、多题页判 PASS（011017/011126）。
2. **NEED_MORE_IMAGE 的产品语义 vs 金标语义差**：产品 prompt 把"页缘截断"判未拍全；金标数完整可见题。prompt 微调（迭代 B/C）反而使模型塌向 PASS（9 页零区域）。
3. **模型随机性**：同配置跨轮同页 ±1–2 框、decision 翻转；单轮采样门值只对当轮导出成立（门报告 scope 块已声明不可外推）。
4. **契约拒收零星发生**（顶层 `type` 信封键 / NEED_MORE_IMAGE 未配 ADD_IMAGE）：prompt 附加硬规则（B/C）未能净改善，随主 hunk 一并回退；后续可考虑解析层宽容化（属产品裁定，未动）。

#### 4.3 本执行件产物

| 文件 | 说明 |
|---|---|
| `core/data/src/test/.../GoldenSplitEvalChannelHarness.kt` | 新增：导出通道（测试源集，assumeTrue 门控；不读金标、不做页选择） |
| `tools/split_golden/check_split_gate.py` | 最小 hunk：SCOPE_REASONS 按金标版本（v1/v2）选择；阈值与判定逻辑零改动（61/61 测试过） |
| `docs/research/evidence/2026-11-07-batch-48/splitter/**` | probe、金标切片（dev/acceptance）、6 个 run 逐页记录+manifest、逐配置导出与度量、最终导出、门报告/证据/裁定、sha 清单、README |
| `build/batch48-ws2/*`（gitignored） | 通道运行脚本（prepare_egress/assemble/merge/wrapper） |

#### 4.4 UNVERIFIED / 移交

1. harness 默认跳过路径（无 env → assumeTrue skip）按 JUnit4 标准语义，本会话未实测（会话末段共享树被知识线在改的 `core/database` 编译错误阻断）；6 次激活运行全部真跑。
2. 门 FAIL 的解除属后续工作：需模型能力升级或产品语义裁定（NEED_MORE_IMAGE 语义、解析宽容化、切分后处理），不在本批最小 hunk 范围。
3. 通道为代理路径（JVM harness 直连 Provider），门值不等于设备产品路径准确率；divergences 逐 run manifest 登记。


### 5. WS3 D 域行级改判（2026-11-07 执行件，任务 31–42）

> 执行者：批次 48 WS3 行级改判 agent。红线遵守：FAIL/BLOCKED 不洗绿（唯一 verdict 变更=D-089 BLOCKED→PASS，
> 有新证据 + 新行级复核 JSON + 台账变更三件套同批）；6C-2 未触碰；热区（RoomCaptureWorkflowRepository 等）零改动；
> 密钥零入库零入日志（raw logcat key/sk-/Bearer 均 0 命中，证据只记 sha8 cd10efbc）。

#### 5.1 产品路径设备取证（任务 32–35，E-48-D-DEV-01）

| 项 | 实值 |
|---|---|
| 构建 | `:app:assembleLocalFirstDebug` BUILD SUCCESSFUL（28s），GRADLE_EXIT=0，APK sha256 `147ca5a4…` |
| 设备 | `emulator-5554`（Android 14，x86_64，PAGE_SIZE=4096；5562/5584 两台僵死 shell 已弃用，5584 已 kill） |
| 凭据 | 设置屏经 `adb shell input text` 注入（key 字段 `capability_api_key`，35 掩码点核实）；保存成功「配置已保存在本机，请继续测试模型。」 |
| 能力测试 | 两次「测试通过：图片读取和讲解都可用。」——app 网关→真实 DeepSeek 上游往返成功 |
| 主路径 | 拍照腿实走（系统相机回传，原图已保存，其 CAPTURE_ASSESS SUCCEEDED attempt=1）；相册腿选真实题图（源图 sha `fefd17c2…`），该页 CAPTURE_ASSESS SUCCEEDED attempt=1 |
| 未达终态 | PROBLEM_CLASSIFY（虚拟场景草稿整理）STREAMING/VALIDATING_OUTPUT ≥18 分钟无终态；按 `RoomModelTaskRepository.kt:1157-1163` 口径健康流式不被 600s 天花板打断，app 无 HTTP 日志无法区分长流式与静默停滞；讲题 TUTOR 任务因此未获创建 |
| 取证 | `d-domain/` 8 文件（00-index/任务生命周期 cmd 日志/设备 JSON/能力测试消息/logcat 关键行脱敏 483 行/2 张截图/本 regen）；`EVIDENCE.sha256` 固化 |
| FLAG_SECURE | 模型设置屏按 app 设计防截屏（`SecondaryScreens.kt:116`），设置屏无截图，以 uiautomator 文本摘录替代 |

#### 5.2 台账改判（任务 31/36–41，30 行）

| 组 | 行 | 改判 |
|---|---|---|
| Provider（任务 31+32–35） | D-082..D-090 | blocked_by 统一更新：key 已具备（sha8 cd10efbc）+ 协议冒烟 PASS（E-48-PROV-01）+ 产品路径部分取证（E-48-D-DEV-01），剩余条件逐行登记；**D-089 → PASS**（证据头四项齐备：run-id/APK sha/serial/密钥来源，全文无密钥片段，新行级复核 `rows/D-089.json` 6 checks 全部可复算） |
| arm64/真机豁免（任务 36–40） | F-006/015/024/033/042/051/060/069/078/087、J-035/037/038/039/042/043、FIX-041..045（21 行） | blocked_by 改「用户裁定豁免（2026-11-06，adjudication.md 追加裁定①）」注记；verdict 维持 BLOCKED，不伪造 PASS；19 行前缀替换 + J-035/FIX-045 方法列尾注（两行原无 BLOCKED 文本列） |
| 台账计数行 | D0-030..035 | direct_recount 声明值随批更新（ledger_pass 897→898、row_json_files 920→921、ledger_sha256 重锚），复算通过 |

- 台账 sha256：`28e96a8d8cfde03f5072bcd360e17ca64d3def918cd84d2722ca21b3b3b76375`（改前 `a9912c2a…`；行数不变，全部原位替换）。
- 防洗绿自查：改前/改后逐行对比，verdict 变更恰 1 处（D-089 BLOCKED→PASS），无 PASS→降级、无 BLOCKED→PASS 除 D-089 外。
- D-082 维持 BLOCKED 的诚实原因：判据要求「上游请求 id 与 APK sha、run-id 同份绑定」，而 app 不落库上游 id、网关无 HTTP 日志（grep `HttpLoggingInterceptor` 0 命中），本批无法产出该绑定。

#### 5.3 复算与门（本执行件实跑）

| 门 | 命令 | 结果 |
|---|---|---|
| 921 份行级复算 | `validate_review`（acceptance_review.py，basename_index）逐份 | **898 PASS / 23 FAIL / errors=0**；`d-domain/regen-validation.json` |
| 台账报告 | `python -I tools/ci/acceptance_report.py --partial` | counts=PASS 898/FAIL 23/BLOCKED 279；**violations=0**（D0 计数行更新后） |
| BLOCKED runbook 覆盖 | `check_blocked_runbook_coverage.py`（载体=blocked-runbook.md，批次 47 同法） | **279/279 covered，missing=0** |
| lint | `acceptance_lint.py` | EVIDENCE_REUSED=144（与批次 47 基线一致，0 处涉及本批新证据） |

#### 5.4 blocked-runbook 同批更新（任务 41–42）

- 总索引头部加批次 48 更新块（D-089 移出→279；blocked_by 更新两组说明）。
- §1 Provider：行数 17→16，行清单移除 D-089，缺失条件后补批次 48 进展；§3 arm64：标注用户裁定豁免组与「不伪造 PASS」。

#### 5.5 UNVERIFIED / 移交

1. 讲题（TUTOR）任务与分题终态：观测窗内未发生，D-082 的端到端完成与 D-087 的派遣对账留待下轮（分题流停滞原因需传输层日志或上游面板才能定位，属用户/上游侧观测点）。
2. D-083/084/085/086/088 的失败注入类证据未取（需 401/429/5xx、边界、取消、限流、失败报文注入轮）。
3. D-090 口径行维持 BLOCKED：本批未发生「无 key 复跑」场景，无新证据可演示该判据。
4. 上游请求 id 绑定（D-082 剩余条件）：需网关日志开关或代理取证，产品路径现状不可得。
5. 本轮 regen-validation 与 report 均在**脏工作树**上复算（WS1/WS2 并行改动在场）；WS4 终裁轮应重跑门禁串行 Gradle 后的完整 `--require-complete`。


### 6. WS4 报告与终裁（2026-11-07 执行件，任务 43–46）

> 执行者：批次 48 WS4 报告重生成记录员。本执行件**零台账变更、零主码变更**（只写报告/终裁/清单/证据目录）；
> 全部门值均为本会话实跑（逐字命令见 `docs/acceptance/adjudication.md` B48-1 表）；
> 密钥卫生复核通过（见 §6.4 第 3 条）。终裁已追加至 `docs/acceptance/adjudication.md`
> 「批次 48 报告重生成终裁」节（B48-0..B48-5 + 签字块）。

#### 6.1 门禁实跑（任务 43–44）

| 门 | exit | 关键值 |
|---|---:|---|
| `acceptance_report --require-complete` | **1** | PASS 898 / FAIL 23 / BLOCKED 279 / UNVERIFIED 0；`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED` 18 组；`pass_with_row_specific_review=729`；review_validation_errors=[]；head `3007a2bc`、dirty=5045 |
| `acceptance_lint` | **1** | 144 `EVIDENCE_REUSED`（与批次 47 基线一致；0 条涉及批次 48 新证据） |
| `acceptance_lint --warn-only` | **0** | violations=0、warnings=144 |
| `trace_requirements` | **0** | total=1200、gaps=0 |
| runbook 覆盖（默认载体 runbook.md） | **1** | 88/279（工具-计划载体错位，批次 47 §6.4 已登记，本批未代改工具） |
| runbook 覆盖（blocked-runbook.md 载体，进程内替换常量） | **0** | **279/279**、missing=0（与 WS3 §5.3 一致） |
| 切分门 AUD-08（v2） | — | **未重跑**（acceptance 单次复算红线）；只读引用 WS2 归档 `splitter/gate-report-v2-01.json`：① 10/24=0.4167 <0.9 未过、③ 0.0385≤0.05 过、切残 3≠0 |

**未跑（如实）**：单测门/强门/d0（任务 47，不在本执行件 ask；批次 47 已知 `h-061_fail` 陈旧预期仍在）；Gradle 全部；设备轨。

#### 6.2 报告重生成（任务 45）

- 投影器 `docs/acceptance/evidence/2026-11-07-batch-48/make_verdicts_report.py`（新增，exit 0）：
  台账 + 本批门禁 JSON → `docs/acceptance/verdicts.md`（1315 行）+ `docs/acceptance/domain-index.md`（32 行）。
  消灭的具体失败 = 批次 47 版 verdicts.md 第 526 行仍写 D-089=BLOCKED、合计 897/23/280 的报告-台账脱节。
- §2 逐项表 = 台账行逐字投影（检查/证据 >220 字符按批次 47 报告同款截断）；verdict 全部取台账，不手写。
- §0 如实行：`d0_self_acceptance --group all` 标「未跑」；AUD-08 标「未重跑、只读引用」。

#### 6.3 终裁与防洗绿（任务 46）

- **台账 verdict diff = 恰 1 处**：HEAD（`a9912c2a…`，批次 47 终态）vs 盘上（`28e96a8d…`）
  逐行比对，两侧各 1200 行，仅 `D-089 BLOCKED→PASS`；无 PASS→FAIL、无其他 BLOCKED→PASS。
- D-089 行级 JSON 本记录员实读：checks 恰 6 项（ledger_row/cmd_exit/device_binding/file_sha256×3），
  `ledger_sha256=28e96a8d…`、`review_kind=mechanical-recompute`。
- 6C-2 只读复核：`SmbkSelectionPlan.kt` worktree blob `e550f5f8…` 与 HEAD 逐字节一致
  （先见 status 幻影 M，`git update-index --refresh` 后 status 干净、两侧 `git hash-object`/`git rev-parse HEAD:` 同值）；
  `:79` 常量与 `:593` require 在位。KD-76 仍 open（`docs/known-defects.md:2916`）。
- 终判：**NOT ACCEPTED 维持**（阻塞清单缩小 280→279；`--require-complete` exit 1 ↔ 终裁一致）。

#### 6.4 密钥卫生复核（本记录员独立抽验）

- 批次 48 证据树 + 新报告 `sk-[0-9a-zA-Z]{20,}` 模式 3 命中：上下文逐一实读均为
  `model-task-<32hex>` 内部任务 ID 子串（`task-` 内含 `sk-`）；三串 sha8
  `52272650 / 2ed9fa64 / 4c7349b2` 均 ≠ 授权标识 `cd10efbc`。密钥本体零落盘成立。

#### 6.5 哈希清单（三份，--verify 全 0）

| 清单 | 项数 | 自哈希 |
|---|---:|---|
| `docs/research/evidence/2026-11-07-batch-48/EVIDENCE.sha256` | 323 | `803b082984bcf3a30bd209b30394b27b33e1cd85709bf4ccab2d8ba85636649e` |
| `docs/acceptance/evidence/2026-11-07-batch-48/REVIEWS.sha256` | 934 | `481ae538aa23135732d8354ceda921ffb3107de60bfbe8a9943ec0b0dfaea9c8` |
| `docs/acceptance/evidence/2026-11-07-batch-48/GATES.sha256` | 9 | `bed4c4c9603a8bc05da7669f4433104f04a448abb0fc505aa811498898c6a0d0` |

#### 6.6 产物哈希（WS4 终态）

| 产物 | sha256 |
|---|---|
| `docs/acceptance/ledger.md` | `28e96a8d8cfde03f5072bcd360e17ca64d3def918cd84d2722ca21b3b3b76375` |
| `docs/acceptance/acceptance-report.json` | `d9243d705e62e84e0555b6dde4b06fdd179f28401d00a500ca4686a15fe179ba` |
| `docs/acceptance/verdicts.md` | `cd9ef1f9e658d221f5e24bb57a312d12d698b080612267e1e4d610bef584ad62` |
| `docs/acceptance/domain-index.md` | `db00dee4081a80902e6e3b31f2cd896058328e79bd084c5e8e6f0fdb9cd18055` |

#### 6.7 UNVERIFIED / 移交（如实）

1. **单测门/强门未跑**（任务 47 不在本执行件 ask）：批次 47 §9.1 的 `GRADLE_EXIT=0` 不代表本批；
   台账/报告在含 WS1–WS3 改动的脏工作树上复算。
2. **GATE-091/092/099 台账行证据仍指批次 46 门日志**；v2 门证据在 `splitter/`；台账行证据重锚属后续门禁轮（本执行件零台账变更）。
3. `d0_self_acceptance` 的 `h-061_fail` 陈旧预期未更新（批次 47 §6.5 遗留）。
4. `check_blocked_runbook_coverage.py` 默认载体仍指 `runbook.md`（88/279 exit 1）；计划载体需进程内替换才能 279/279——工具侧修正待 owner（本执行件未代改工具）。
5. 金标 v2 的 G1 等级与 `heldOutUnusedForTuning` 真伪继承 WS1 §2/§1.3 的 UNVERIFIED，终裁 B48-3 不签项 3/4 已列。
6. 本节与终裁 B48 节均由同一记录员（WS4）产出——单会话机械复算口径，不构成第二主体复核。


### 7. 对抗复核（修复环）收口（2026-11-07）

> 执行者：批次 48 修复环。输入 = 对抗复核 13 条 findings（等级：中 2、低 11）。
> 本修复环**零代码改动、零台账变更、零冻结清单变更、热区零触碰、门未重跑**
> （acceptance 只复算一次红线）。唯一产物：勘误文件
> `docs/research/evidence/2026-11-07-batch-48/PROVENANCE-ERRATUM.md`
> （sha256=`76c3543f41a687aa36090f608a0b2f04b935ec8d252a5935aa249ca728834d06`，
> **不加入** EVIDENCE.sha256——根清单自哈希 `803b0829…649e` 已被 §6.5/终裁 B48-5 钉值，
> 追加会使已记录的 323 项快照失配；以本节 sha256 钉值）+ 本节。
> 被勘误文件本体一律未动（全部被根清单/d-domain 子清单/D-089.json sha 钉死）。
> 以下每条的"复证"均为修复环本会话实跑，非转抄复核者结论。

#### 7.1 逐条处置

| # | finding（severity） | 处置 | 复证（本会话实跑） |
|---|---|---|---|
| 1 | 盲标红线成立：金标冻结早于全部算法运行（低） | 确认，无需改 | v2 `generatedAt=2026-11-06T16:53:00Z`（mtime 00:53:00）早于 runs/ 全部 6 个 run 目录（01:31:44–02:16:02） |
| 2 | `ranAtUtc` 是预排时刻表非实际运行时间（**中**） | **已修**：勘误 A | 逐文件实读 32 份：mtime 窗口 15:59:42Z–16:50:15Z（51 分钟）vs ranAtUtc 跨 16:00Z–次日 10:00Z（约 18h）；仅首份 A 吻合。证据本体未动（钉值链），以勘误承载更正 |
| 3 | v1.1 补遗未被页级 JSON 绑定（**中**） | **已修**：勘误 B（全量 sha 落值） | 实算 GUIDELINE.md=38763dca…f480（全文）；32 份 promptSha256 全=408d3337…（v1 基础版）；JSON 内零处出现 38763dca；GUIDELINE §8:80–81 自身已声明该约定 |
| 4 | B 非独立 + 5 对 IoU=1.000（低） | 确认 + 残余登记（勘误文件 §残余 1） | 自写 IoU 复算：8/8 题数一致、35 对、mean 0.9641、min 0.8448、IoU==1.0 恰 5 对（010034×3、010859×1、005711×1）；B mtime 全部晚于 A |
| 5 | acceptance 只复算一次成立（低） | 确认，无需改 | final 24 页 sources=dev∪acc、逐页 (questionCount,decision,boxesSha256) 0 mismatch；dev 四 runs 页清单 ∩ acceptance = 空集 |
| 6 | 切分未面向 v2 过拟合（低） | 确认，无需改 | 按 24 页全集 sha256(name) 升序重算：dev 16/16、acceptance 8/8 与 v2 split 块全等；`RoomCaptureWorkflowRepository` git status 无条目 |
| 7 | D-089 改判合规（低） | 确认，无需改 | D-089.json `evidence_sha256` 钉 3 文件（00-index/lifecycle log/run JSON）sha 全匹配；`d-domain/EVIDENCE.sha256` 8/8 OK（finding"7 个证据文件"口径=目录 8 文件去清单自身；钉值分布以本行为准） |
| 8 | 台账改判无洗绿（低） | 确认，无需改 | HEAD vs 盘上逐行比对：verdict 列变更恰 1 处（D-089 BLOCKED→PASS）；cell 变更恰 30 行（D-082..090 共 9 + 豁免注记 21） |
| 9 | 密钥卫生成立（低） | 确认，无需改 | `git check-ignore -v local.properties`=.gitignore:14；批次 48 证据树 `sk-[0-9a-zA-Z]{20,}` 21 命中、全部含 `model-task-` 子串；key 本体未读取、未落盘 |
| 10 | 门工具最小 hunk 与清单完整性（低） | 确认（**一处措辞更正**） | `test_split_gate.py` 61/61 OK；validate v2 exit 0；GATES 9/9、REVIEWS 934/934、根清单 323/323；`check_split_gate.py` diff 实为 **2 个 hunk**（常量引入+引用切换，同一逻辑变更；v1 三条文本逐字保留、阈值/判定零触碰）——finding"唯一 hunk"按 hunk 计数不准，实质成立 |
| 11 | 00-index 复现时间线两处出入（低） | **已修**：勘误 C | lifecycle log 首任务 epoch 差 2933ms（index 写 21s）；capability-test-msg.txt 自证 19:16:51Z 同屏复测；logcat 18:45:09/18:45:12/18:47:21 有 OkHttpModelTransport.post 佐证原始测试；00-index 本体字节冻结（sha 0cfb79ee…，三处钉值） |
| 12 | v2 split 块自证字段缺口（低） | 确认 + 残余登记（勘误文件 §残余 2） | v2 split 块仅页清单+heldOutUnusedForTuning=true，无选取规则内嵌；页对象无 sha 字段；UNVERIFIED 维持（终裁 B48-3.4 不签项） |
| 13 | 本复核自身边界（未复核） | 登记为 §7.2 | 边界清单如下，如实入册 |

#### 7.2 复核边界（如实，全部 UNVERIFIED / 不可机检）

1. "标注阶段未打开 `tools/split_golden/` 任何文件"的读取行为不可机检，仅内容指纹 + 时间线间接验证。
2. 单测门/强门/设备轨 Gradle 本修复环**未重跑**（红线：acceptance 只复算一次；Gradle 仅门禁阶段串行）；§6.1 门值不代表本修复环后状态——但本修复环零代码/台账改动，门输入字节未变。
3. harness 无 env 默认跳过路径未实测（继承 §4.4 第 1 条）。
4. 24 页框坐标的人眼级语义正确性超出复核手段，仅以 A/B 稳定性（mean IoU 0.9641）与算法对比间接佐证。
5. `acceptance_report --partial` 的 exit=1 系 state=FAIL（23 FAIL 未清）的脚本语义，violations=0 与之不矛盾，易误读处在此声明。
6. 本修复环自身为单会话执行——勘误与处置不构成第二主体复核。

#### 7.3 共享树现态差异（如实）

复核时点快照与修复环时点的工作树存在漂移：`core/model/` 现有 4 个知识线修改
（KnowledgeQuizTasks.kt、PdfAigcContentPolicy.kt 及两测试，属并行知识线，与切分/热区无关）；
`core/capture/`、app capture 目录保持零改动；finding #6/#10 引用的"model 目录干净"
快照在修复环时点已不成立，特此登记现态。热区文件无任何 status 条目，结论不变。
