# 批次 51 验收台账员证据（2026-11-10）

> 角色：只写 ledger / verdicts / 哈希清单，不伪造证据。
> 台账最终 sha256：`1161bfc2dda774ce32606cfc764d428d26044a581a6a1c93f5f038425320fe48`。
> 本目录所有命令均为本会话实跑；未跑项在 §5 明列。

## 1. 结果一屏

| 项 | 实值 |
|---|---|
| 台账改前 | 933 PASS / 13 FAIL / 254 BLOCKED / 0 UNVERIFIED（sha `9044a779…`） |
| 台账改后 | **937 PASS / 10 FAIL / 253 BLOCKED / 0 UNVERIFIED**（sha `1161bfc2…`） |
| 新增 PASS | F-058（2h soak 重复窗口）、J-002（产物 revision=HEAD）、J-048（vc 跨构建单调）、J-052（历史 AAB 与现 APK vc 可比）。**J-002/J-048/J-052 是机械复算 PASS**（`review_kind=mechanical-recompute`、`independence.independent_agent=false`），不是第二人/第二模型语义复核；修复环已把批次50 的历史 FAIL 判词与批次51 翻转分开记录（`prior_verdict=FAIL`、`verdict_flip.semantic_review=false`）。 |
| 证据刷新 | 27 行（A/B/E/F/H/J/FIX/GATE），verdict 不变 |
| `acceptance_report --require-complete` | **exit 1**；`NOT_ACCEPTANCE_COMPLETE`（FAIL=10、BLOCKED=253）+ `REVIEW_CHECKS_SHARED`（20 组） |
| `acceptance_report`（普通） | **exit 1**；`state=FAIL`；`violations=[]`；`pass_with_review=937/937`、`invalid=0`、`without=0`、`row_specific=659` |
| `acceptance_lint` | **exit 1**；162 × `EVIDENCE_REUSED`（与批次50 同量级，无新增违规码） |
| `acceptance_lint --warn-only` | exit 0（诊断面） |
| `trace_requirements` | exit 0；gaps=0 |
| `acceptance_init --check` | exit 0（1200 行结构无漂移） |
| 复核 JSON | 947 份（946 旧 + F-058 新增）全部换绑到新台账；948 项 `REVIEWS.sha256` 0 mismatch |

## 2. 四门（不由台账员改判）

| 门 | 本批读数 | 判定 |
|---|---|---|
| 3D D-1 | 加权 0.9403574；FPC Wilson 下界 0.9291236 <0.95；normal 0.8979148 <0.90；99 错（89 REBIND + 10 NONE）；model-labeled、κ NOT RUN | **FAIL** |
| 3E | 设备 OK(4)；JVM 3E 11 例 pass | 设备复验成立；台账无 3E 门行，不代签无条件 PASS |
| MVA | 32/120=0.2667；RENDERED 95/120 上界 <108；6/6 组 FAIL；端到端 p95 72.9551s（机器可读门已按请求→首图重算）；IMG 臂 BLOCKED | **FAIL**；JS 不注入/不广告 |
| AUD-08 | ① 19/24=0.7917 <0.9；③ 4/78=0.0513 >0.05；fragmented=4 | **FAIL**；`check_split_gate.py` exit 1 |

## 3. 文件

| 文件 | 内容 |
|---|---|
| `ledger_audit_b51.py` / `*.log` | F-058 / J-002 / J-048 / J-052 的可重算审计（4 份日志 + `j002-identity-require-head.json`） |
| `j-release-verifier-b51.xml` / `.log` | `tools/tests/test_j_release_verify.py` + `test_release_version_code.py`：33 passed，EXIT=0 |
| `apply_ledger_b51.py` | 31 行台账更新的可复跑脚本（按 ID 定位，不增删行） |
| `rebind_reviews_b51.py` / `rebind-reviews.log` | 947 份 review JSON 换绑 + 4 个新增 PASS 的 checks；PASS 复算 0 错 |
| `make_b51_device_evidence.py` | 从设备轨原始 summary/soak-start/soak-summary 归一出 `soak-b51.device.json` |
| `acceptance-report.json` / `.stdout.txt` | 普通报告（exit 1、violations=0、937/10/253/0） |
| `acceptance-report-require-complete.json` / `.stdout.txt` | 终裁门（exit 1、两项阻断） |
| `acceptance-lint.json` / `.stdout.txt` | lint（exit 1、162 EVIDENCE_REUSED） |
| `acceptance-lint-warn-only.json` / `.stdout.txt` | lint 诊断面（exit 0） |
| `trace.json` / `.stdout.txt` | 追溯（exit 0、gaps=0） |
| `mapping-b51.md` | 行 → 证据映射、4 门读数、命令与 exit |
| `make_manifests_b51.py` / `SHA256SUMS` | 三份清单生成与自校验 |

验收层（`docs/acceptance/evidence/2026-11-10-batch-51/`）另有：
`device/soak-b51.device.json`、`j002-identity-require-head.json`、`REVIEWS.sha256`（948 项）、`EVIDENCE.sha256`（2 项）。

## 4. 判词

- 台账可复核性：937 个 PASS 行全部有 ≥2 类独立证据 + 有效行级复核；报告 `violations=[]`。
- **批次级不成立**：仍有 10 FAIL / 253 BLOCKED，20 组 shared checks；不得签“批次 51 验收通过”。
- 4 个新增 PASS 是机械复算（证据字节、cmd exit、device binding、XML counts），不是语义复核。

## 5. 未跑 / 未验证

- 未重跑 Gradle、设备 instrumented、2h soak、真实 Provider、MVA judge、AUD-08 切分门；相关读数读自各子线证据。
- 未生成 `verdicts.md` / `domain-index.md` 的批次51 投影（仍为批次50 版；以其文首 sha 标注为准，数值以本台账 + 报告为准）。
- 未做独立第二人/第二模型语义复核；reviewer 是台账脚本身份，`independent_agent=false`。
- 未改：门阈值、金标、6C-2、JS 广告状态、产品代码、`knowledge-production/wusan/materials.jsonl`、`tools/kb_build/*`。
