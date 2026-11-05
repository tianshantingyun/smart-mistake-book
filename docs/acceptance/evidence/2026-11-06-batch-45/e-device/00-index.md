# 设备复核会话 · 可签复核记录（2026-11-05 13:05–13:25 +08）

> 本文件是 `docs/research/evidence/2026-11-06-batch-45/e-device/00-index.md` 的
> **验收侧镜像摘要**：只写本会话亲手运行、可逐字复跑的命令与真实退出码。
> 本文件**不**改台账、**不**改源码、**不**改热区、**不**引依赖、**不**输出密钥。
> 台账 sha256 全程 `bb25ef2360562de4f00f9a49e2b0b5aed576d77500ce1bc7dcba64c4879ea686`。

## 1. 环境与身份

| 项 | 值 |
|---|---|
| HEAD | `87604198feeef198d5dba7343b8c4692c5e8f937` |
| 设备 | `emulator-5562`（API 34、x86_64），`boot_id=2d913fc3-97a5-4e63-a987-0628192a4f27` |
| 被测测试 APK | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| APK sha256 | `32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61`（设备侧 pull 回算相等） |
| 包名 | `com.tingyun.smartmistakebook.core.database.test` |

## 2. 实跑与退出码

| # | 命令 | exit | 关键输出 | 证据 |
|---:|---|---:|---|---|
| 1 | `./gradlew --offline :core:database:assembleDebugAndroidTest --console=plain` | 0 | `BUILD SUCCESSFUL`、`76 actionable tasks: 76 up-to-date` | 本会话终端 |
| 2 | `adb -s emulator-5562 install -r …/database-debug-androidTest.apk` | 0 | `Success` | — |
| 3 | `adb -s emulator-5562 pull <device base.apk>` + `sha256sum` | 0 | 与本地 APK **逐字节相等** `32235e63…` | `docs/research/evidence/2026-11-06-batch-45/e-device/apk-identity.txt` |
| 4 | `python -I tools/ci/am_instrument_recorder.py --serial emulator-5562 --package …core.database.test --class …ErrorCauseLedgerInstrumentedTest --apk-sha 32235e63… --test-apk-sha 32235e63… --label e061-errorcause-ledger --out-dir …/e-device` | **0** | `tests=3 evaluated=3 failures=0 errors=0 skipped=0`、`verdict=PASS` | `…/e061-errorcause-ledger.{log,xml,device.json,summary.txt}` |
| 5 | `python -I …/e-device/recompute.py` | 0 | 10 CHECK 全 OK、`MISMATCHES=0`、`RECOMPUTE=OK` | `…/e-device/recompute.txt` |
| 6 | 同 #4，`--class …NoSuchTestClassControl`（假绿控制） | **1** | `verdict=FAIL failures=1` | `…/e061-control-no-such-class.*`、`…/control-recorder.out` |
| 7 | `python -I …/e-device/p09_recompute.py` | 0 | WS7 三次 `2071 / 1890 / 1861 ms`；本会话三次 `— / 2794 / 2666 ms` | `…/e-device/p09-recompute.txt` |
| 7b | `adb -s emulator-5562 shell am instrument -w -r -e class '…VisualJsClientFrameQueueDeviceInstrumentedTest#aFourth…InFlight' com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner` ×3 | 0（**用例级 1 红 2 绿**） | run1 `FAILURES!!!` `AssertionError: initial connect failed`（`:109`）；run2/3 `OK (1 test)` | `docs/research/evidence/2026-11-06-batch-45/e-device/verify-p09-run{1,2,3}*`、`verify-p09-summary.txt` |
| 8 | `python -X utf8 -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete` | **1** | `882/30/144/144`；`NOT_ACCEPTANCE_COMPLETE`+`PASS_WITHOUT_REVIEW` | `…/e-device/report-require-complete.txt/.json` |
| 9 | `python -X utf8 -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | 0 | `violations=[]`、`warnings=29` | `…/e-device/lint.txt/.json` |
| 10 | `python -X utf8 -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | 0 | `gaps=[]`、`total=1200` | `…/e-device/trace.txt` |
| 11 | `python -I …/e-device/nonpass.py` | 0 | `1200 = PASS 882 + FAIL 30 + BLOCKED 144 + UNVERIFIED 144`（逐域计数） | `…/e-device/nonpass.txt` |

## 3. 对既有结论的处置（不重跑长任务）

| 项 | 本轮结论 | 依据 |
|---|---|---|
| E-061 设备事务 | **已跑绿（3/3）**，但行仍 **UNVERIFIED**（该行是“并发”，用例串行） | §2 #4–#6；`ErrorCauseLedgerInstrumentedTest.kt:43,155,268` 三个 `runBlocking` 串行用例 |
| KD-76 A6 10+10 | **UNVERIFIED 维持**；三处工具 exit 2 全部独立复现 | `a6_plan.py validate`=2、`run_kd76_rounds.sh`=2、`summarize_kd76.py`=2（`n=9/20`、`G2 NOT EVALUABLE`） |
| isolation | **BLOCKED 维持**；门 `INVALID/la_ratio_max`（0.3825>0.3），套件 15×`-4`、`STATUS_CODE: 0` 计 0、witness `body_rows=0` | `ws7/lowload-gate-5562.json`、`ws7/isolation-invalid-gate.txt`、`ws7/isolation-witness-check.txt` |
| p09 | **未收敛维持**；WS7 三次 2071/1890/1861 ms 均落慢重建绿区；**本会话三次 = 1 红（initial connect failed）/ 2 绿（2794、2666 ms）** | §2 #7、#7b；`verify-p09-summary.txt` |
| 验收 | **验收不通过**；`acceptance_report` exit 1；BLOCKED 清单见 `docs/acceptance/adjudication.md` §4（144 行，逐 id） | §2 #8–#11、`docs/acceptance/adjudication.md` |

## 4. 未做（不得读成已做）

- 未跑 20 轮 reboot、未跑 E 域 JVM 全量重跑、未跑 arm64/真机、未跑 `connectedLocalFirstDebugAndroidTest`。
- 未改台账一行（E-061 verdict 不变）；未新增 `review:` 行级绑定（`pass_with_review` 仍 0）。
- 未重生成 `acceptance-report.json` / `verdicts.md` / `domain-index.md`（由报告会话产物保持）。
