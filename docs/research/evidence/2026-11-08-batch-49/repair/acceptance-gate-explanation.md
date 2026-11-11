# acceptance 终裁门未收口说明（批次 49 修复环，2026-11-08）

## 实跑值

| 门 | 命令 | exit | 关键值 |
|---|---|---:|---|
| 报告 | `python -I tools/ci/acceptance_report.py --out <gate>` | 1 | counts **PASS 900 / FAIL 22 / BLOCKED 278 / UNVERIFIED 0**；violations=0；`pass_with_review=900/900`、`pass_with_invalid_review=0`；`pass_with_row_specific_review=731`；`REVIEW_CHECKS_SHARED` 18 组；state=FAIL |
| 终裁 | `python -I tools/ci/acceptance_report.py --require-complete` | 1 | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=22 BLOCKED=278)` + `REVIEW_CHECKS_SHARED 18 组` |
| lint | `python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | 1 | 144 × `EVIDENCE_REUSED`（全部为批次44–48 既有复用；批次49 新证据 0 条） |
| lint 诊断 | 同上 `--warn-only` | 0 | violations=0 |
| trace | `python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | 0 | gaps=0 |

## 22 条 FAIL 的归属（逐条）

| 组 | 行 | 为什么本批不关闭 |
|---|---|---|
| 本批修复对象 | F-057 | 回退为 FAIL：判据要求「2h 内至少 1 次 FATAL / 超限路径命中」，批次49 soak 117 轮未命中；不能把未命中记 PASS |
| AIGC 合规 | H-018、H-062 | 导出/水印的模型改卷文本标识，属他线合规改动；本批热区为 feature/review+app，未改导出链 |
| 发布工程 | J-001、J-002、J-020、J-021、J-024、J-025、J-027、J-033、J-034、J-041、J-048、J-051、J-052（13 条） | 需要干净树/发布钥匙/AAB/versionCode 单调等发布环境事实；本工作树为共享脏树（`git status --porcelain` 千级），不具备发布条件；改写这些行等于伪造发布证据 |
| 门禁自身 | GATE-032、GATE-038、GATE-042、GATE-091、GATE-092、GATE-099 | 报告终态 exit 0 与 require-complete 通过是**终裁门自身的输出**（当前 exit 1，不能自证通过）；AUD-08 切分门 v2 ① 0.4167<0.9 维持 FAIL |

## 278 条 BLOCKED 的分布

FIX 85、J 48、I 37（TalkBack/IME/真机/独立签字）、F 32（arm64/阈值/PSS）、A 25、H 19、E 9、D 8、G 7、GATE 4、B 2、C 2。

这些行的出口已由 `docs/acceptance/blocked-runbook.md` 覆盖（278/278，批次 49 记录）；在条件取得前改 PASS 就是伪造。因此本轮把
**“算法相关 FAIL/BLOCKED 清零或说明”**执行为：F-057 如实回退并说明；其余非算法行按上表归属说明，不冒充通过。

## 结论

- `acceptance_report` 的 exit 1 **不再是证据链违规**（violations=0、900 个 PASS 行全部有有效复核），
  而是 22 条真实 FAIL 与 278 条 BLOCKED 的**批次级未收口**。
- 终裁门通过（exit 0）需要发布环境/他线合规/真机无障碍签字等条件；本批不具备，登记为阻塞，
  不通过改判词或删行绕过。
