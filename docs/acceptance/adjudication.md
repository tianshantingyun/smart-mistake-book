# 批次 46 报告重生成终裁（2026-11-05/06）

> 台账：`docs/acceptance/ledger.md` sha256 `19ea6b57c5bbe73281232e170fb491a1360b6c1c87e0c9205e15c3f075f33e7b`（1200 行）
> run-id：`2026-11-06T09:00:00Z-batch46-line-review`（本文件为报告重生成轮）
> 机器报告：`docs/acceptance/acceptance-report.json`（sha256 `a7927de6…`，exit 1）
> 记录：`docs/research/2026-11-06-batch-46-record.md`（首轮记录；本轮重生成后的
> 哈希、6 份 FAIL 行修复与复算统计以本文件 §4/§8 为准）
> **2026-11-05 修复环**：审计 findings 1–6 已修（行级复核改为逐项复算、294 份
> batch44 run_id 重绑、checks 共享检测、lint 默认阻断、单会话机械复算标注）；
> 修复后的门禁与统计见 §10，旧 §0–§8 的数字以 §10 为准。
> 本文件只写结论、签字边界与统计，不替代台账、行级 JSON 与证据。

## 0. 结论

**批次 46 验收不通过（NOT ACCEPTED）；发版不成立。**

本轮回合完成了 920 份行级复核 JSON 的逐份机械复算（888 PASS + 32 FAIL），
修复了 6 份 FAIL 行中与当前字节矛盾的检查，重生成 `verdicts.md`、`domain-index.md`
与三份哈希清单，并重跑全部验收门。`acceptance_report --require-complete` 仍 **exit 1**：

```text
NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=32 BLOCKED=280)
REVIEW_CHECKS_SHARED （24 组 checks 被 ≥5 行共用）
```

| 项 | 实值 | 判定 |
|---|---|---|
| 台账 | 1200 行；sha `19ea6b57…` | — |
| 计数 | **PASS 888 / FAIL 32 / BLOCKED 280 / UNVERIFIED 0** | 非 PASS 312 行 |
| `acceptance_report` | exit 1；`violations=[NOT_ACCEPTANCE_COMPLETE, REVIEW_CHECKS_SHARED]` | 阻断 |
| `pass_with_review` | **888 / 888**（逐项复算通过）；`pass_without_review=0` | 机械覆盖成立 |
| `pass_with_row_specific_review` | **575 / 888**；318 行 `checks_scope=shared-suite` | 行级判别力缺口 |
| 行级 JSON 复算 | **920 / 920 clean**；0 缺失、0 硬错误 | 机械面成立 |
| `acceptance_lint` | **exit 1**；violations=144；warnings=144 | 反作弊面阻断（GATE-042） |
| `acceptance_lint --warn-only` | exit 0；warnings=144 | 仅诊断 |
| `trace_requirements` | exit 0；total=1200；gaps=0 | 追溯缺口为 0 |
| `d0_self_acceptance --group all` | 213 CHECK / 0 FAILURES / exit 0 | 自验收可复现 |
| 三份哈希清单 `--verify` | EVIDENCE / REVIEWS / GATES 均 exit 0 | 字节自洽 |
| 6C-2 | `setOf(9, 13, 15)`；零 diff；不变量测试 3/3 | 行为未漂移 |
| AUD-08 | `4/7=0.5714 < 0.9`；exit 1 | FAIL |

## 1. 签字条件逐条

| # | 条件 | 实值 | 是否满足 |
|---:|---|---|---|
| 1 | 全部 PASS 有行级复核 JSON | 888 PASS，920 JSON，missing=0 | **满足（机械面）** |
| 2 | `reviewer ∉ authors` | 920/920 | **满足（仅字符串不等，非主体独立）** |
| 3 | `checks` 非空且可复算 | 920/920 通过逐项复算；318 行 checks 与其他行逐字节相同 | **部分满足（575/888 有行级判别力）** |
| 4 | 行级 JSON 绑定当前台账 sha | 920/920 = `19ea6b57…` | **满足** |
| 5 | `evidence_sha256` 与磁盘一致 | 0 mismatch | **满足** |
| 6 | FAIL 未洗绿 | 32 条仍 FAIL，重跑证据仍红 | **满足** |
| 7 | UNVERIFIED 收敛 | 0 | **满足** |
| 8 | `acceptance_report --require-complete` exit 0 | **exit 1**（`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED`） | **不满足** |
| 9 | `acceptance_lint`（默认即阻断复用） exit 0 | **exit 1**（144） | **不满足** |
| 10 | 第二人/第二模型语义复核 | `independence.independent_agent=false`；无第二个 agent | **不满足** |
| 11 | 发版就绪 | J 域 13 FAIL、P0 未闭环 | **不满足** |

**判词**：第 1–7 项是机械面条件，本轮已满足；第 8–11 项不满足，因此
**不签“批次 46 验收通过”**，也不构成产品放行。行级复核证明“证据存在、字节未变、
可判定字段为绿”，**不证明**检查项与证据的语义充分性。

## 2. 我签什么 / 不签什么

**签：**

1. 结论「批次 46 验收不通过（NOT ACCEPTED）」。
2. 888 条 PASS 均有行级 JSON，且本轮 920 份逐份复算为 0 硬错误；
   reviewer∉authors、ledger sha 绑定、evidence sha 一致。
3. 32 条 FAIL 清单与缺陷/修复单：仍红，未洗绿。
4. 280 条 BLOCKED 是“条件/授权缺失”，不是通过。
5. 不可变项只读结论：6C-2 未漂移；KD-76 open；isolation 不定案；p09 未收敛；AUD-08 FAIL。

**不签：**

1. 不签 888 条 PASS 的**语义充分性**：`reviewer` 是角色标签
   （`batch46-line-reviewer-<域>`），复核是同一会话内的机械复算，不是第二个人/第二模型；
   且 318 行的 checks 与其他行逐字节相同，只有 575 行具备行级判别力。
2. 不签 32 条 FAIL 已关闭。
3. 不签 280 条 BLOCKED 已取得条件。
4. 不签发版就绪。
5. 不签 KD-76 关闭、isolation 定案、p09 收敛、AUD-08 转绿。

## 3. 本轮实跑（逐字命令与 exit）

| # | 命令 | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete --out docs/acceptance/acceptance-report.json` | **1** | PASS 888 / FAIL 32 / BLOCKED 280 / UNVERIFIED 0；`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED`；`pass_with_review=888`、`pass_with_row_specific_review=575` |
| 2 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **1** | total=1200；violations=144；warnings=144 |
| 3 | 同命令 `--warn-only` | **0** | violations=0；warnings=144 |
| 4 | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | **0** | total=1200；gaps=0 |
| 5 | `python -I tools/ci/d0_self_acceptance.py --group all` | **0** | 213 CHECK / 0 FAILURES |
| 6 | `d0_self_acceptance --group docs --section verdicts` | **0** | 10 CHECK / 0 FAILURES；pass=888 json=920 missing=0 |
| 7 | `d0_self_acceptance --group docs --section protocol` | **0** | 6 CHECK / 0 FAILURES |
| 8 | `d0_self_acceptance --group hash` | **0** | 15 CHECK / 0 FAILURES |
| 9 | `acceptance_hash_manifest --verify` ×3 | **0 / 0 / 0** | EVIDENCE / REVIEWS / GATES 全 OK |
| 10 | `python -I build/b46/validate_rows_regen.py` | **0** | `clean_rows=920 rows_with_errors=0 missing=0` |
| 11 | `python -I build/b46/regen_docs.py` | **0** | 重生成 `verdicts.md`、`domain-index.md` |
| 12 | `python -I tools/ci/h_compliance_gate.py --only H-018/H-061/H-062` | **1 / 1 / 1** | 仍红（KD-84/KD-85） |
| 13 | `python -I tools/split_golden/check_split_gate.py` | **1** | `countExactRate=4/7=0.5714 < 0.9`；fragmentRate 0/16 |
| 14 | `j_release_verify identity --root . … --require-clean --require-head` | **1** | dirty=4733（报告 JSON 终态 4734，因新增 `gate/regen-validation.json`）；APK revision 落后 HEAD 53 |
| 15 | `j_release_verify signing --root . …` | **0** | `env_missing` 4/4；`doc_mentions_env_contract=false` |
| 16 | `j_release_verify version --gradle app/build.gradle.kts --metadata …` | **0** | versionCode=2（手写常量） |
| 17 | `j_release_verify aab16k --aab app/build/outputs/bundle/strictOfflineRelease/app-strictOffline-release.aab` | **1** | ABI 4≠2；缺 `libquickjs.so` |
| 18 | `test -f gradle/verification-metadata.xml` | — | **MISSING**（G-089 仍红） |

**本轮未跑**：Gradle 单测/强门、adb 设备轮、2h soak、arm64/16KB 真机、真实 Provider、
Play Console、人工金标、TalkBack/IME 真机、第二人语义复核。C/E 域设备与 JVM 证据
沿用批次 46 归档 XML/JSON，本轮只复算字节与字段；不从本轮重跑外推为“已复跑”。

## 4. 行级复核重算统计

`build/b46/validate_rows_regen.py` 对 920 份 `docs/acceptance/verdicts/rows/<ID>.json`
逐份复算，结果写入 `docs/acceptance/evidence/2026-11-06-batch-46/gate/regen-validation.json`：

| 检查面 | 结果 |
|---|---|
| 文件数 / 台账 PASS+FAIL | 920 / 920；missing=0 |
| id 与文件名、verdict 与台账 | 0 mismatch |
| `reviewer` 存在且 ∉ `authors` | 920/920 |
| `ledger_sha256` = 当前台账 | 920/920 |
| `checks` 非空 | 920/920 |
| `evidence_sha256` 逐文件复算 | 0 mismatch |
| XML `tests/failures/errors/skipped`、testcase 名、suite、timestamp | 0 mismatch |
| CMD `EXIT` 标记、日志 sha | 0 mismatch |
| device `serial/apk_sha256/boot_id/counts/verdict` | 0 mismatch |
| PNG MAE / 亮度 / 文本节点集 / bounds / TotalTime 统计 | 0 mismatch |
| 硬错误合计 | **0** |

`checks` 主要类型（次数）：`cmd_exit` 964、`ledger_row` 920、`xml_aggregate` 666、
`xml_testcase` 661、`device_binding` 359、`evidence_sha256` 166、`file_sha256` 104、
`xml_counts` 97、`source_sha256` 81、`testcase_presence` 81。

### 4.1 本轮修复的 6 份 FAIL 行（verdict 不变）

| 行 | 原问题 | 处置 |
|---|---|---|
| GATE-032 | 同一 batch-46 路径残留 4 个历史 sha 的重复 `cmd_exit`/`file_sha256`（含 `exit_markers=[]`） | 删除矛盾重复，保留当前字节 `06683bf1`/`11703423` 与 batch-45 历史对 |
| GATE-038 | 历史 sha `6657dc1c` 重复检查 + `evidence_missing exists=False`（文件实际存在） | 删除两项；保留当前 `08c25d0f` |
| GATE-042 | 历史 sha `232ed9f1` 重复检查 + `evidence_missing exists=False`（文件实际存在） | 删除两项；保留当前 `fc7d21a2` |
| GATE-091/092/099 | `evidence_missing exists=False`，但 `aud08-split-gate.txt` 实际存在 | 删除假检查；保留 `c89b2a6e`，exit=1 仍红 |

- 这 6 份的 `verdict` 未改，仍为 **FAIL**；`regenerated_by`/`regeneration_note` 已写入 JSON。
- 修复前字节备份在 `build/b46/row-backup/`（gitignored，不随仓库发布）。
- 未发现任何 PASS 行的假检查；888 份 PASS JSON 文本本轮未重写，只做逐份复算。

### 4.2 诚实边界

1. **语义复核缺口**：`reviewer` 是角色标签，不是第二个人/第二模型；本轮做的是
   机械复算。计划要求的“另一 agent 抽样 5 行人工复算”仍未完成。
2. **6 条 PASS 绑未提交源码**：D-010/015/016/017/018/027 的 `source_sha256` 记录
   `sha256_equals_head_blob=false`，源文件为工作树 `M`（3 个）与 `??`（1 个）；
   XML/CMD 证据为绿，但不能从 HEAD 重放。列为遗留风险，不改判 PASS。
3. **顶层域级复核 JSON**（`verdicts/A-review.json`…`J-review.json`）仍绑旧台账
   `bb25ef23…`，本轮**不把它们当行级证据**；行级只认 `rows/<ID>.json`。
4. **Gradle 与设备本轮未跑**：C/E 域行级复核引用批次 46 归档证据，只复算字节与字段。
5. **内容锚点类检查**（`source_read`/`witness_node_assertion`/`witness_source` 等）
   本轮按文件存在、sha、关键行/常量是否命中复算；未逐条做语义充分性审查。

## 5. FAIL 重判（32 条，仍红）

分组：F 9（7 条复核矛盾 + 2h soak 2）、J 13（干净树/签名/AAB/vc）、H 3（KD-84/85）、
GATE 6（report/lint/AUD-08）、G 1（依赖校验元数据）。逐条缺陷/出口见
`docs/research/evidence/2026-11-06-batch-46/fix/fix-tickets-b46.md`（32 行）。

| 证据 | 本轮实算 | 结论 |
|---|---|---|
| F-019 | gfxinfo 逐轮 p50 = `450/2050/2150/2100/2000/2350/300`，上界 2350 > 2150 | 仍红 |
| F-028 | `export.cmd.log` `PRODUCT_TIME_S=7.973` vs 台账 487/597/820ms | 仍红 |
| F-029 | `MistakeBatchPerf` 令牌 0 命中 | 仍红 |
| F-034 | `instr.txt` 无 MistakeBatchPerf 行 | 仍红 |
| F-036 | 归档产物 0 命中 | 仍红 |
| F-055/F-057 | 归档 soak 2504s FATAL（Desired retention 越界），未完成 2h；本轮未重跑 | 仍红 |
| F-064/F-067 | `dropbox-after.txt` `data_app_crash` 实测 **6**，台账写 12 | 仍红 |
| G-089 | `gradle/verification-metadata.xml` **MISSING** | 仍红 |
| H-018/H-061/H-062 | `h_compliance_gate --only` exit **1/1/1** | 仍红 |
| J-001/J-002 | identity exit 1；dirty 4733（终态 4734）；behind 53 | 仍红 |
| J-020/J-021/J-024/J-025/J-027 | signing `env_missing` 4/4；`doc_mentions_env_contract=false` | 仍红 |
| J-033/J-034/J-041 | aab16k exit 1；ABI 4≠2；缺 `libquickjs.so` | 仍红 |
| J-048/J-051/J-052 | versionCode=2 手写常量；版本源非生成 | 仍红 |
| GATE-032/038/042/091/092/099 | report/lint/split 门仍 exit 1；AUD-08 4/7 | 仍红 |

**本轮没有 FAIL→PASS，也没有 PASS→FAIL**；只修复了 6 份 FAIL 行 JSON 中与当前
字节矛盾的检查，verdict 全部保持 FAIL。

## 6. UNVERIFIED 与 BLOCKED

- **UNVERIFIED=0**：`acceptance_report` 实读确认。144 条 UNVERIFIED 已在批次 46
  首轮收敛为 24 PASS / 1 FAIL / 119 BLOCKED；本轮没有新的 verdict 迁移，
  也未把任何 UNVERIFIED 直接改 PASS。
- 本轮对上述 24 条 PASS 的行级 JSON 与证据字节做了机械复算（全部在 920 份
  复算范围内，0 硬错误），但**未重新执行**其原始 JVM/设备判据的语义审查；
  它们仍依赖批次 46 归档证据。
- **BLOCKED=280**：全部带 `blocked_by`/runbook 指针（`docs/acceptance/runbook.md`
  §6/§9/§9.1/§9.2/§13）；`trace_requirements` gaps=0。
- 280 条不是通过：真实 Provider、生产签名、arm64/16KB、Play Console、人工金标、
  TalkBack/IME 真机、内部热区授权均缺失。

## 7. 不可变项（只读）

- **6C-2**：`core/data/.../backup/SmbkSelectionPlan.kt:79` 仍为 `setOf(9, 13, 15)`；
  `git diff --stat` 为空；`SixC2RulingInvariantTest` XML **tests=3 failures=0**。
  mask=8 不提供、mask=0 禁止、派生索引恒裁，行为未漂移。
- **KD-76 open**：`docs/known-defects.md` KD-76 标题仍 open（约 :2916）。
- **isolation 不定案**：`docs/known-defects.md` KD-66/isolation 段仍 PARTIAL（约 :2745）；
  本轮未重跑低负载门。
- **p09 未收敛**：沿用批次 45 三次 2071/1890/1861 ms > 1300 ms；本轮未重跑。
- **AUD-08 FAIL**：本轮实跑 exit 1，`4/7=0.5714 < 0.9`。
- **热区未改**：本轮写入仅 `docs/acceptance/*`（行级 JSON、台账相关报告、清单）
  与 `build/b46/*`（gitignored）；`core/domain`、`core/ui`、`feature/*`、`docs/current`
  无写入；未新增依赖。

## 8. 产物与哈希（本轮终态）

| 产物 | sha256 |
|---|---|
| `docs/acceptance/ledger.md` | `19ea6b57c5bbe73281232e170fb491a1360b6c1c87e0c9205e15c3f075f33e7b` |
| `docs/acceptance/acceptance-report.json` | `a7927de62b53eeb363741be9219e0961e85e0d49404366f8310fbbff3d0c473b` |
| `docs/acceptance/verdicts.md` | `c71f498a03db59d15fc9599c442ecac7656ea92f7fd5fae864ff653d97b04c6b` |
| `docs/acceptance/domain-index.md` | `c3a598a2f9bf1ef28840cf6128d61a8c21493807c922495ace952a74e01f9ebc` |
| `EVIDENCE.sha256` | `b3cfb62664fcf383980b490215c6f822a2f80ee475ce3ac3b6661105b54e38b3` |
| `REVIEWS.sha256` | `f488002f67f00f1523651411633b313e124f4d593784509afcf4911f47f80e4c` |
| `GATES.sha256` | `d40ada9ef05bcc5e4b89a000174fbfb2eded3622e84f9d80e366da0a4a8c1e9b` |
| `gate/regen-validation.json` | `824a92ce2cfcd126d6d0c4b2e0344d4da087122f7b52102d997a1e18a5738882` |

三份清单 `--verify` 均 exit 0（EVIDENCE/REVIEWS/GATES）；`d0 --group hash` 15 CHECK / 0 FAILURES。

## 9. 签字区

```text
结论：批次 46 验收不通过（NOT ACCEPTED）；发版不成立
签字人：批次 46 报告重生成轮（复核员/记录员）
签署范围：§2「签」的 1–5 项；§1 第 1–7 项机械面条件
不签范围：§2「不签」的 1–5 项；§1 第 8–11 项
日期：2026-11-06
```

> 本终裁不把任何 `BLOCKED` 读成通过，不把行级机械复核读成语义复核，
> 不把 `PASS` 读成产品可用。

## 10. 修复环（2026-11-05）：审计 findings 1–6

> 本节是审计后新增的修复记录；与 §0–§8 数字冲突时以本节为准。逐项细节见
> `docs/research/2026-11-06-batch-46-record.md` §9。

### 10.1 改了什么

| 面 | 修复前 | 修复后 |
|---|---|---|
| `pass_with_review` 口径 | 只数 `review:` token 存在 | 逐项复算 `checks[]` + `evidence_sha256`；无效复核单列 `pass_with_invalid_review` |
| 294 份 batch44 run_id | 旧 run 复核换绑 batch46 台账 sha | 重新复算后重绑 `2026-11-05T11:28:35Z-batch46-repair-line-review-*`；旧值存 `rebind_from_run_id` |
| checks 行级判别力 | 未检测 | 非 `ledger_row` 指纹；24 组 ≥5 行共用 → `REVIEW_CHECKS_SHARED`；`pass_with_row_specific_review=575` |
| 证据新鲜度 | 未记录 | `evidence_freshness` + `review_evidence_batches`：2294 条归档 / 90 条本批 |
| 独立性表述 | 称“独立复核” | `review_kind=mechanical-recompute`、`independence.independent_agent=false`；文档改称“机械复算” |
| lint 复用 | 默认 exit 0 | 默认 exit 1；`--warn-only` 仅诊断 |
| XML 解析 | 先解析后查 DOCTYPE | 解析前拒绝 `<!DOCTYPE`/`<!ENTITY`（`acceptance_review.py`），不使用外部实体 |

### 10.2 修复后门禁（逐字命令与 exit）

| # | 命令 | exit | 关键输出 |
|---:|---|---:|---|
| 1 | `python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete --out docs/acceptance/acceptance-report.json` | **1** | `PASS 888 / FAIL 32 / BLOCKED 280 / UNVERIFIED 0`；`NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED`；`pass_with_review=888`、`pass_with_invalid_review=0`、`pass_with_row_specific_review=575` |
| 2 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | **1** | violations=144、warnings=144（全部 `EVIDENCE_REUSED`） |
| 3 | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md --warn-only` | **0** | violations=0、warnings=144 |
| 4 | `python -I -m pytest -q tools/tests/test_acceptance_report.py tools/tests/test_acceptance_lint.py` | **0** | 38 passed |
| 5 | `python -I tools/ci/d0_self_acceptance.py --group lint` | **0** | 24 CHECK / 0 FAILURES |
| 6 | `python -I tools/ci/d0_self_acceptance.py --group docs --section verdicts` | **0** | 10 CHECK / 0 FAILURES；json=920 missing=0 |
| 7 | `python -I tools/ci/acceptance_hash_manifest.py --root docs/acceptance/verdicts --verify docs/acceptance/evidence/2026-11-06-batch-46/REVIEWS.sha256` | **0** | OK |

### 10.3 修复后仍不签的范围

- 验收仍不通过：`NOT_ACCEPTANCE_COMPLETE`（32 FAIL + 280 BLOCKED）与原
  `REVIEW_CHECKS_SHARED`（24 组）同时阻断 `--require-complete`。
- 888 条 PASS 的复核仍是**单会话机械复算**，不是第二人/第二模型语义复核；
  318 行 checks 与其他行逐字节相同，只有 575 行有行级判别力。
- 868/920 行的复核只重算归档证据字节；本轮没有重跑这些 JVM/设备用例。
- `GATE-035` 的旧 `pass-with-review-check.log` 记录的是修复前口径；台账 verdict
  未改，作为遗留项（低 severity）保留。
