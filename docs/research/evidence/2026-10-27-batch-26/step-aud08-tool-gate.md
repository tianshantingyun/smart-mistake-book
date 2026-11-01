# 批次 26 · T4 AUD-08 工具半边 —— 切分金标门工具面证据（2026-10-27）

- 计划：`.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md` §1 T4（"tools/split_golden 计量/门脚本扩展"）
- 基线：`docs/research/2026-10-27-batch-26-baseline.md` §7（缺口清单 6 条）与
  `docs/research/2026-10-11-new-stages-spec.md` §4C（:162-169）；最小范围沿用 R4
  （`docs/research/2026-10-26-ui-queue-sequencing.md` §3.7/§5.7）
- 执行者：T4 评测工具工程师（subagent）；**未跑 Gradle、未做 git 写操作、未触碰热区**；
  设备轨与真实 Provider 模型执行**归后续**（本工具面不产生任何模型质量结论）
- 本轨改动面：`tools/split_golden/`（新增 2 脚本 + 2 文档，扩展 measure `--json`）、
  `tools/tests/test_split_gate.py`（新）、本目录（新）、`docs/current/visual-generation-contract.md`（规范一句登记）
- 环境实况：Python 3.13.14（`python --version`）；金标 v1 冻结未动（sha 见下表，validate PASS）

## 1. E-ID 表

| E-ID | 步骤 | 判定 | 证据 |
|---|---|---|---|
| E-A08-01 | 单测套件 25/25（阈值边界/报告形状/冻结校验/导出器/入口冒烟） | PASS | §3.1 |
| E-A08-02 | 全量 tools/tests unittest discover（CI 同口径） | PASS | §3.2 |
| E-A08-03 | 真金标 validate C1–C6 | PASS | §3.3 |
| E-A08-04 | 真金标 + 存量 app 导出度量（门 false 复现 stage1 §9） | PASS（复现一致） | §3.4 |
| E-A08-05 | 门判定器首跑：FAIL（exit 1）+ 机器证据落盘 | PASS（判门成立） | §3.5 |
| E-A08-06 | measure 人读输出逐字节对照（改动前后仅 +1 行自动化裁定） | PASS | §3.6 |
| E-A08-07 | 导出器机制（DB→app-split.json，只读/确定性/拒重复/拒坏框） | PASS（单测；真设备 DB 未跑） | §3.7 |

## 2. 产物身份（sha256，本轮实算）

| 产物 | 路径 | 字节 | sha256 |
|---|---|---|---|
| 金标 v1（冻结，未动） | `tools/split_golden/tables/split_golden_v1.json` | 3809 | `f9e92c47b399252d2e92ecd633c2a2b76a52b0bfcdcff78868a513dedd1e4c9f` |
| 应用导出（4C 存量，build/ 忽略未跟踪） | `build/split-eval/app-split.json` | 1482 | `9b49b0c474d68409eba1617b888e7cf823507341ef63f54a65d9f4d68f4c2fd6` |
| 门判定器（新增） | `tools/split_golden/check_split_gate.py` | — | 见 git 提交 |
| 导出器（新增） | `tools/split_golden/export_split_eval.py` | — | 见 git 提交 |

应用导出 sha256 为**首次登记**（4C 记录未记）；该文件无 `source` 溯源块（top keys 仅
`pages`），设备/DB 来源绑定 UNVERIFIED（仅有 stage1 记录 §4 的会话内核验叙述）。

## 3. 步骤与关键结果（命令原文 + exit）

### 3.1 E-A08-01 单测套件（先红后绿）

- 红：`python -m unittest tools.tests.test_split_gate` 在实现前 `ModuleNotFoundError:
  No module named 'check_split_gate'`（1 error）。
- 绿：同命令 `Ran 25 tests ... OK`。
- 覆盖：门阈值**闭边界**（① 9/10=0.9 过、8/10=0.8 不过；③ 1/20=0.05 过、2/20=0.1 不过）、
  报告形状与常量（阈值 0.9/0.05+出处、automation.enabled 恒 false、wilson95、逐页表）、
  冻结校验联动（字节漂移 C1 红、evidenceGrade 改写 C2 红、冻结失败 exit 2 不判门）、
  `--json` 形状/确定性/与人读同源、导出器（同 DB 同 sha、DB 字节不变、页名映射、
  合成名标记、重复页名拒绝、非法框拒绝、缺表如实报）、真入口子进程冒烟（exit 3 路径）。

### 3.2 E-A08-02 全量 discover（CI 同口径）

- `python -m unittest discover -s tools/tests -t tools` → **exit 0，OK**（含本套件）。

### 3.3 E-A08-03 真金标冻结校验

- `python tools/split_golden/validate_split_golden.py` → exit 0，`拆题金标校验 PASS`，
  C1 sha 与旁车一致/canonical 逐字节/CRLF=0，7 页 16 框，C5 无重复，C6 7≥7，
  evidenceGrade=模型标注无人工专家（冻结常量原样）。

### 3.4 E-A08-04 真金标度量（存量 app 导出在场）

- `python tools/split_golden/measure_split_accuracy.py` → exit 0：
  ① = 4/7 = **0.5714**（v1 对照 2/7=0.2857，改判 2 单题页）；② = 7/16 = 0.4375；
  ③ = 0/16 = 0.0000（阈值敏感性 0.3–0.6 全 0）；门 false（① 0.5714 < 0.9）。
- **与 stage1 记录 §9 v2 逐值一致**（`docs/research/2026-10-14-4c-stage1-record.md` §9：
  0.5714 / 0.4375 / 0.0000）——同一份 1482 B 存量导出、同口径、可复跑。

### 3.5 E-A08-05 门判定器首跑

- `python tools/split_golden/check_split_gate.py
  --evidence-out docs/research/evidence/2026-10-27-batch-26/step-aud08-gate-evidence-out.md`
  → **exit 1（门 FAIL）**：① 0.5714 >= 0.9 未过、③ 0.0000 <= 0.05 过；
  打印"门后自动化不启用（裁定 37 / 4C③）"；机器证据文件落盘成功
  （51 行 / 2926 B，R4 上限内；内容见同目录 `step-aud08-gate-evidence-out.md`）。
- exit 3（应用导出缺失）与 exit 2（冻结失败/数据坏）路径由单测覆盖
  （真目录无缺失场景——存量导出在场，如实记录）。

### 3.6 E-A08-06 measure 人读输出逐字节对照

- 改动前快照：夹具（3 页金标 + 2 页应用导出）跑旧版，stdout 存档；
- 改动后同输入重跑：unified diff **仅 +1 行**（"自动化：门后自动化不启用…"追加在
  门判定行后）——指标、逐页表、门式、敏感性、注意事项全部逐字节不变。

### 3.7 E-A08-07 导出器（export_split_eval.py）

- 单测全绿（§3.1）；**真设备 DB 快照未跑**（本轨不启动模拟器/不取 DB）——
  真设备导出与同 sha 复跑属设备轨后续，UNVERIFIED。
- 页名解析三级（page-map 显式映射 > `batch_import_page.source_uri` 文件名 >
  `job:<id>#p<page>` 合成名标记）依 `BatchSplitRecognizer.kt:109/117/126-130`
  的生产常量（split job id 形如 `split:<batch>:<page>`、question `page_index=0`、
  `pageCount=1`）设计；4C 的页名配对本是会话外感知匹配（stage1 §4），故页名
  **不可从 DB 单独推出**，映射文件是显式输入而非隐式猜测。

## 4. 与基线 §7.3 缺口清单的对照

| 基线缺口 | 本批处置 |
|---|---|
| 1. 指标覆盖 3/5（缺错误合并/重复率） | **未实现，如实登记**——阈值/分母按规范（:167）属"立项自定/科学落实"流程，不在本次维护编造；GATE.md 逐类登记"未实现+未立项" |
| 2. 门说明无独立载体 | 新增 `tools/split_golden/GATE.md`（五类 × 分母/阈值/样本量/统计区间/严重度） |
| 3. 无 train/dev/acceptance 切分 | 数据半边缺口，**未做**（需新冻结集 v2+产品裁定）；GATE.md 登记 |
| 4. 标注来源/复核登记窄 | v1 冻结不可改（C1）；evidenceGrade 原样保留；GATE.md 登记代理金标口径；升级须另建 v2 |
| 5. 门未接线（无红绿判定器） | **已闭环**：check_split_gate.py（exit 0/1/2/3）+ 机器证据格式；CI 接线未做（.github 不在本批落点），如实登记 |
| 6. 度量输入依赖 git 外产物 | **工具半边闭环**：export_split_eval.py（R4 §3.7 指名的生产者）；真设备 DB 实跑归设备轨 |

## 5. 未验证 / UNVERIFIED 清单

- **目标文件共脏注明**：`docs/current/visual-generation-contract.md` 基线时干净（基线 §3 实测
  218 行 porcelain 空），本轮收尾复核发现其"终止层落地状态"段已被**在飞设备轨**先行修改
  （KD-76 warm-up 设备措辞回填）——与本轨无关、本轨未触碰；本轨改动仅为 :199 起独立一段
  "切分门（AUD-08）2026-10-27 登记"。提交时按纪律整文件纳入并双 hunk 各自注明。
- 真实模型/设备重测（新 app-split.json 产出）：UNVERIFIED（无凭据、设备轨后续）；
  当前门 FAIL 判定基于 4C 存量导出（来源绑定无溯源块，UNVERIFIED）。
- 真设备 DB 上的 export_split_eval.py 实跑与同 sha 复跑：UNVERIFIED。
- CI 门禁接线（android-check.yml 增加 gate 步骤）：未做（落点不含 .github/）。
- 错误合并/重复率指标与阈值：未立项，未实现（不得编造）。
- 金标人工专家复核：无（代理金标口径不变）。

## 6. 体积自查

本目录 2 个 `.md`：本文件 + `step-aud08-gate-evidence-out.md`（工具生成，51 行 / 2926 B），
均在 R4 单文件上限（400 行 / 64 KiB）与单批上限（16 个 / 512 KiB）内。索引归批次 26
证据轨统一维护，本轨不建 00-index（避免与他轨冲突）。
