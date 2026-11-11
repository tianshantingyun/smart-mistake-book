# 批次 51 台账映射（A/B/E/F/H/J/FIX/GATE）

> 角色：验收台账员。只做台账行 → 本批证据的映射、verdict 更新与机械复算；
> 不改产品代码、不改门阈值、不把未过的门写成 PASS。
>
> 台账：`docs/acceptance/ledger.md`
> - 改前 sha256 `9044a7794dda196c6fc22c8e989a33099482ef26399e13ffbcbe28c0f045cb97`（933 PASS / 13 FAIL / 254 BLOCKED / 0 UNVERIFIED）
> - 改后 sha256 `1161bfc2dda774ce32606cfc764d428d26044a581a6a1c93f5f038425320fe48`（937 PASS / 10 FAIL / 253 BLOCKED / 0 UNVERIFIED）
> - 1200 行结构不变；`acceptance_init.py --check` exit 0。

## 1. verdict 变更（4 行，全部有本批实跑读数）

| ID | 改前 | 改后 | 本批依据 | 关键读数 |
|---|---|---|---|---|
| F-058 | BLOCKED | **PASS** | 批次50 与批次51 两个独立 2h soak 窗口，同一判据 | 两窗各 7200s / 115 轮；crash_delta=0、dropbox_delta=0、monkey 非零 0；批次50 emulator-5590 / APK `1bf98b00`，批次51 emulator-5592 / APK `b6fbd877` |
| J-002 | FAIL | **PASS** | `j_release_verify.py identity --require-head` 批次51 实跑 | revision=`8dd29e96…`（是 commit）、commits_behind_head=0；APK sha `b6fbd877…` |
| J-048 | FAIL | **PASS** | 跨批次产物 versionCode 单调 | 批次50 APK/AAB `101160` → 批次51 APK/AAB `101177`（commit_count 1160→1177） |
| J-052 | FAIL | **PASS** | 历史 AAB 与现 APK 的 vc 可比 | 同包 `com.tingyun.smartmistakebook.localfirst`；AAB `101160` → APK `101177` |

J-001 未改判：`identity --require-clean` 仍 exit 1（审计时 dirty_paths **5489**；台账员重绑 947 份 review 后终态 **6451**），干净树前提不成立。
J-020/J-021 未改判：本批签名是本地测试钥（DN `OU=NOT-PLAY-PRODUCTION`），不是生产身份。

## 2. 证据刷新（verdict 不变，27 行）

| 域 | 行 | 追加的本批证据 | 维持 verdict 的理由 |
|---|---|---|---|
| A | A-023 | `device/26-instrument-3e-rerun.log`（3E 设备 OK(4)） | 大队列耗时/内存与 OOM/ANR 未测，仍 BLOCKED |
| A | A-028 | `device/22-instrument-library-6b3-5c.log`（24 例） | 主路径有设备证据，PASS |
| B | B-001 | `device/05-instrument-migration-matrix.log`（1..75，20 例） | PASS |
| E | E-017 | `device/24-instrument-app-5b-aigc-capability.log`（17 例） | PASS |
| E | E-065 | `device/23-instrument-5c-realrepo-js-painter.log`（25 例） | PASS |
| E | E-075 | `device/26-instrument-3e-rerun.log`（3E 4 例） | PASS |
| E | E-080 | `device/21-instrument-kd67.log`（3 例） | PASS |
| F | F-055 / F-063 | `soak-b51.device.json` + `37-soak-console.log` | 基线/回归保持 PASS |
| F | F-057 | 同上 | 超限路径仍未命中（FATAL=0），维持 FAIL |
| H | H-024 / H-036 / H-053 | `release/external-blockers.md` | 公网 URL / Play 表单 / 备案号仍外部 BLOCKED |
| J | J-001 | `identity-require-clean-strictOffline.json` + `clean-clone-build.log` | 脏树，维持 FAIL |
| J | J-019 | `validateReleaseSigning-with-env.log` + `signing-localFirst.json` + 外部阻塞清单 | 生产钥未取得，维持 BLOCKED |
| J | J-020 / J-021 | `signing-localFirst.json` / `apksigner-localFirst.txt` / `keytool-keystore.txt` + 外部阻塞清单 | 本地测试身份，维持 FAIL |
| J | J-043 | `device-probe.txt` + 外部阻塞清单 | 宿主仍无 arm64（用户裁定豁免，不伪造 PASS） |
| FIX | FIX-061 / FIX-064 | `clean-clone-baseline.txt` / `clean-clone-build.log` + 外部阻塞清单 | 干净 clone 构建失败（HEAD `AiGeneratedDocumentNotice.kt` 未跟踪），维持 BLOCKED |
| GATE | GATE-091 / GATE-092 / GATE-099 | 批次51 合并门报告/证据/merged/ stdout | AUD-08 仍 FAIL（见下） |
| GATE | GATE-096 / GATE-097 / GATE-098 | 批次51 split README / 3D REPORT / MVA README | 人工金标仍不存在，维持 BLOCKED |
| GATE | GATE-100 | 3D/MVA/AUD-08 报告 + 外部阻塞清单 | 发版不成立，维持 BLOCKED |

## 3. 四门读数（不因台账行升级而改变）

| 门 | 本批读数 | 判定 | 台账落点 |
|---|---|---|---|
| **3D D-1** | 加权 0.9403574；FPC 单侧 95% Wilson 下界 **0.9291236 < 0.95**；normal 护栏 **0.8979148 < 0.90**；99 错（89 REBIND + 10 NONE）；model-labeled、κ NOT RUN | **FAIL** | 无独立台账门行；读数在 `3d-audit/REPORT.md`，GATE-100 引用 |
| **3E** | 设备 OK(4)（队外捞回 10 / 时间预算 / 同日累计 / 无考试日指纹）；JVM 3E 11 例 pass | 设备复验成立；**台账不代签“无条件门 PASS”** | E-075 / A-023 / E-017 证据刷新 |
| **MVA** | 首次=最终 32/120=0.2667；RENDERED 95/120 上界 <108；6/6 组 FAIL；端到端 p95 72.9551s（机器可读门已按请求→首图重算）；IMG 臂 BLOCKED | **FAIL**（JS 不注入、不广告） | 无独立台账门行；GATE-100 引用；JS 产品化记录 |
| **AUD-08** | 合并门 ① **19/24=0.7917 <0.9**；③ **4/78=0.0513 >0.05**；`fragmentedQuestions=4` | **FAIL**（exit 1） | GATE-091 / GATE-092 / GATE-099 |

## 4. 本批实跑命令与 exit

| # | 命令 | exit | 落点 |
|---:|---|---:|---|
| 1 | `python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/acceptance/ledger_audit_b51.py` | 0 | F-058 / J-002 / J-048 / J-052 审计日志 |
| 2 | `python -I -m pytest tools/tests/test_j_release_verify.py tools/tests/test_release_version_code.py -q --junitxml …/j-release-verifier-b51.xml` | 0 | 33 passed，0 failures/errors/skipped |
| 3 | `python -I -X utf8 docs/research/evidence/2026-11-10-batch-51/acceptance/rebind_reviews_b51.py` | 0 | 947 份 review JSON 换绑；PASS 行复算 0 错 |
| 4 | `python -I -X utf8 tools/ci/acceptance_report.py --require-complete --out …/acceptance-report-require-complete.json` | **1** | `NOT_ACCEPTANCE_COMPLETE` + `REVIEW_CHECKS_SHARED` |
| 5 | `python -I -X utf8 tools/ci/acceptance_report.py --out …/acceptance-report.json` | **1** | `state=FAIL`、`violations=[]` |
| 6 | `python -I -X utf8 tools/ci/acceptance_lint.py --out …/acceptance-lint.json` | **1** | 162 × `EVIDENCE_REUSED`（与批次50 同量级，无新增码） |
| 7 | `python -I -X utf8 tools/ci/acceptance_lint.py --warn-only …` | 0 | 告警降级诊断面 |
| 8 | `python -I -X utf8 tools/ci/trace_requirements.py --out …/trace.json` | 0 | gaps=0 |
| 9 | `python -I tools/ci/acceptance_init.py --check` | 0 | 1200 行结构无漂移 |
| 10 | `python -I -X utf8 …/make_manifests_b51.py` | 0 | REVIEWS 948 项、验收层 2 项、台账员层 26 项，0 mismatch |

## 5. 边界（不得读成）

1. 这不是“批次 51 验收通过”：`--require-complete` 仍 exit 1（FAIL=10、BLOCKED=253、20 组 shared checks）。
2. 这不是“四门通过”：3D FAIL、MVA FAIL、AUD-08 FAIL；3E 只有设备复验读数，台账无门行，不由台账员代签无条件 PASS。
3. 本批新增 PASS 是**行级机械复算**（cmd/test/device/review + 文件 sha 复算），不是第二个人/第二模型的语义复核。
4. `verdicts.md` / `domain-index.md` 仍是批次50 投影（未在本轨重生成）；其计数与本台账最新值不一致时，以 `ledger.md` + `acceptance-report.json` 为准。
5. 本批证据来自脏工作树；设备是 x86_64 模拟器；release 是本地测试钥候选包。
