# 批次 47 I 域证据（可用性与无障碍）

> 一次性构建脚本存档说明见 `apply_I_rows.b47.README.md`
> （真实脚本在 gitignored `build/batch47-I/apply_I_rows.py`，sha256 `6422df36…`）。

## 0. 结论口径

- **台账 I 域 FAIL = 0**（53 PASS / 37 BLOCKED），台账 sha `19ea6b57…` 未被本域修改。
  两条命令可复算：`python -I tools/ci/acceptance_report.py`（exit 1，`counts` 里 I 域
  FAIL 计入全局 FAIL=32，I 无一行 FAIL）与台账解析（`docs/acceptance/ledger.md` 行
  852–941）。批次 44/45/46 的台账（`git show fd3bfcd5/caa57a9e/22a23e24`）里 I 域
  FAIL 同样为 0（44/45 是 53 PASS / 5 BLOCKED / 32 UNVERIFIED）。
- 本域本轮完成：**WS2 行级判别力修复（42 份行级 JSON）** 与 **WS3 runbook 出口
  （runbook §9.5，37 条 BLOCKED 全覆盖）**。

## 1. WS2：行级判别力（先红后绿）

| 步骤 | 命令 | exit | 证据文件 |
|---|---|---:|---|
| 红 | `python -I tools/ci/check_row_specific_checks.py --domain I` | 1 | `row-specific-before.log`（53 行里 42 行 0 条域内独有 check；24 行标 shared-suite） |
| 修复 | `python -I build/batch47-I/apply_I_rows.py` | 0 | `apply.log`（42 份 JSON，+61 条 check、+7 条证据挂载） |
| 绿 | `python -I tools/ci/check_row_specific_checks.py --domain I` | 0 | `row-specific-after2.log`（53/53 行 ≥1 条域内独有 check；shared-suite 标签 0） |
| 逐份复算 | `python -I build/batch47-I/validate_I_rows.py` | 0 | `validate-I-after2.log`（53 PASS 行、270/270 checks 逐字节复算通过） |
| 全局门 | `python -I tools/ci/acceptance_report.py` | 1 | `report-final.log`（PASS 888 / FAIL 32 / BLOCKED 280；I 无违规；`pass_with_row_specific_review` 575→676） |
| 反作弊 | `python -I tools/ci/acceptance_lint.py` | 1 | `lint.json2.log`（144 违规全为 EVIDENCE_REUSED 基线；I 域 0 条） |

- 新增 check 形状：`{name, source, value}`；`device_text`/`xml_testcase` 由
  `tools/ci/acceptance_review.py` 的 `check_one`（`:262`）按字节复算
  （`device_text` 做空白折叠后的逐字子串比对，`xml_testcase` 重开 XML 数 testcase）。
- 证据挂载（claim 所需但此前未挂）：I-017（暗色/字号/横屏三份 device JSON）、
  I-028（font2-review.device.json）、I-058（empty-library.audit.json 的 `min_dp`）、
  I-064/065/067（keyboard-search.xml，回车后的 dump，含 root_library/页面标签）、
  I-076（landscape-library-nav.device.json）。
- 中途返工：I-062 首次引用测试名 `test_audit_payload_marks_contrast_unverified_without_colors`
  触发 lint `UNVERIFIED_AS_PASS`（测试名含 “unverified”，`acceptance_report.py:80` 的
  `UNVERIFIED_RE` 大小写不敏感）；已改为 `test_good_tree_with_colors_passes`，lint
  回到 144 基线。

## 2. WS3：37 条 BLOCKED 的 runbook 出口（runbook §9.5）

| 步骤 | 前置 | 命令 | exit | 证据文件 |
|---|---|---|---|---|
| 红（实测口径） | runbook 无 I 域出口：工具化前的逐行统计显示 37 条里仅 FIX-T19 提到的 I-007/I-061/I-070/I-075/I-079 出现过（5/37）；`docs/acceptance/runbook.md` 修改前无 I 域出口小节 | 未工具化跑红（工具是修复后补的门） | — | 统计命令见本轮台账解析（`grep -n` + 逐行匹配，无落盘文件） |
| 修复 | 在 `docs/acceptance/runbook.md` 末尾新增 §9.5（四组表 + 边界） | — | — | `runbook.md:419` |
| 绿 | — | `python -I tools/ci/check_blocked_runbook_coverage.py --domain I` | 0 | `runbook-coverage-I-true-final.log`（37/37 覆盖） |
| 回归 | runbook 自检 | `python -I tools/ci/d0_self_acceptance.py --group docs --section runbook` | 0 | `d0-runbook.log`（6 CHECK / 0 FAILURES） |

## 3. 本轮亲手实跑的其余命令

- `python -I tools/ci/trace_requirements.py` → exit 0，gaps=0（`trace.log`）。
- 台账在本轮执行期间被并行轮连续重写（本轮实测 sha 时间线：
  `19ea6b57…`（批次 46 基线，本域全部工作的绑定基线）→ `6c4bcf63…`（23:37 重写，
  7 行 F 域 FAIL 翻转）→ `226c3107…`（再重写，FAIL 32→24）。I 域 90 行的
  verdict 与行号在**每一版**里逐行未变（逐行比对过 HEAD/6c4b/226c 三版）。
  因此：本域所有行级复核仍绑定 `19ea6b57…`；全局 `acceptance_report` 在新台账上
  显示 53 行 `REVIEW_LEDGER_SHA_MISMATCH` 等绑定类违规，属**批次级重绑缺口**
  （批次 46 修复环做过同样的 294 份重绑，`batch-46-record.md` §9 finding 1），
  归 WS4/主会话，本域不代做（避免在移动目标上双写）。
- `python -I tools/ci/acceptance_hash_manifest.py --root docs/acceptance/verdicts --verify docs/acceptance/evidence/2026-11-06-batch-46/REVIEWS.sha256` → **exit 1**：
  130 份行级 JSON 哈希不符（A/B/E/I 各域 WS2 并发改动的合计；**I 域单域为 42 份**）。
  三份清单的再生成属批次 47 WS4（主会话）。
- `python -I tools/ci/acceptance_report.py --require-complete` → exit 1：
  `NOT_ACCEPTANCE_COMPLETE`（FAIL=32 / BLOCKED=280）+ `REVIEW_CHECKS_SHARED` 19 组 +
  `PASS_REVIEW_INVALID` 9 行（H-009/H-015/H-052/H-071、J-009/J-045/J-054/J-064/J-072，
  由并发轮对 `core/export/.../MistakePdfExporter.kt`、`RELEASE.md` 的源码改动引起，
  mtime 2026-11-05 21:04:30/21:04:59，非 I 域改动）。

## 4. 已指出、未擅改的缺陷（留给主会话终裁）

1. **I-039 / I-043（深色主题）**：行文本自述“属产品决策缺口，非外部阻塞，登记未验证”，
   与台账 `BLOCKED` 判词矛盾。本轮实跑
   `grep -rn "darkColorScheme\|isSystemInDarkTheme" core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt | wc -l` → **0**，
   与 I-039 证据 `dark-scheme-scan.log`（MATCHES=0）一致——缺口是真实且当前的。
   runbook §9.5.4 只记录冲突与出口，不改判词。
2. **台账 I-017/I-028/I-058/I-064/065/067/I-076 的 claim 超出证据**（例如 I-017“四态
   0 违规”原证据只有空态一份）。本轮按“证据挂载”修复：同批同 run 的证据文件已在盘，
   本轮把缺失文件挂进行级 JSON 的 `evidence`/`evidence_sha256` 并新增可复算 checks；
   台账 evidence 列**未改**（改台账会动 1200 行共享文件，且 lint 的 EVIDENCE_REUSED
   面会漂移，属主会话动作）。
3. 9 行“独立复核人”BLOCKED 行与 14 行人工语感行需要**第二人**（`independent_agent=false`
   仍是当前事实）；runbook §9.5.2/§9.5.3 给出了签署流程与判据。
