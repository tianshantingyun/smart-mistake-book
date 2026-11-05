# 批次 45 · 设备复核会话（E 域设备项 + WS7 独立复算）

> 会话身份：设备复核（reviewer 不计入任何域的 authors；本轮**未改**源码、
> **未改**台账、**未改**热区、**未引**依赖、**未**执行 `connectedLocalFirstDebugAndroidTest`）。
> 设备：`emulator-5562`（API 34、x86_64、`boot_id=2d913fc3-97a5-4e63-a987-0628192a4f27`）。
> 时间窗：2026-11-05 13:05–13:25 +08。HEAD `87604198feeef198d5dba7343b8c4692c5e8f937`。
> 证据根：`docs/research/evidence/2026-11-06-batch-45/e-device/`、
> `docs/acceptance/evidence/2026-11-06-batch-45/e-device/`。

## 0. 结论（先写，理由在后）

| # | 项 | 本轮判定 | 与既有结论的关系 |
|---:|---|---|---|
| 1 | E-061 的设备事务覆盖 | **设备用例 3/3 绿**（真 SQLite / Room 内存库） | 台账 E-061 **仍维持 UNVERIFIED**：它检查的是“错因落账**并发**”，而该用例是顺序 `runBlocking`，不覆盖并发。证据补上了“真库事务”，不补“并发” |
| 2 | KD-76 A6（10+10） | **UNVERIFIED 维持**（三处工具各自 exit 2，本轮独立复现） | 与 `ws7/00-index.md` §1 一致；未改预注册、未跑 20 轮 reboot |
| 3 | isolation 低负载门 | **BLOCKED 维持**（gate `INVALID`/`la_ratio_max`，套件 15×`-4`、0 实体执行） | 与 `ws7/00-index.md` §2 一致；本轮复算 JSON 与原始 `-4` 计数 |
| 4 | p09 | **未收敛维持**（WS7 三次 2071/1890/1861 ms > 1300 ms 窗；**本会话新跑三次 = 1 红 / 2 绿**，红在 connect 阶段） | 与 `ws7/00-index.md` §3 一致；本轮脚本从原始 logcat 重算，并新增一组同 APK 红样本 |
| 5 | `acceptance_report` | **exit 1**（`882/30/144/144`，`pass_with_review=0`） | 与 `docs/acceptance/adjudication.md` 一致；**验收不通过**不变 |

## 1. E-061：唯一被台账点名的 E 域设备项

### 1.1 为什么是它

台账 E 域 90 行中**没有任何 `device:` 证据**（`docs/research/evidence/2026-11-05-batch-44/e-domain/00-index.md:4`
自述“E 域是纯 JVM 算法域，本批没有设备/模拟器证据”）。唯一把设备写进升级路径的是
**E-061**（`docs/acceptance/ledger.md` E-061 行）：
“真 SQLite 事务由设备用例覆盖，本轮未跑设备……升级路径：门禁阶段在 emulator 上跑该用例”。
其余 9 条 UNVERIFIED 是并发/DST 类，不是设备项。

### 1.2 被测字节身份（先钉 APK，再谈结果）

| 项 | 值 |
|---|---|
| 测试 APK（本地） | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk` |
| 本地 sha256 | `32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61` |
| 设备侧包名 | `com.tingyun.smartmistakebook.core.database.test` |
| 设备侧 base.apk sha256 | `32235e63…7501a61`（`adb pull` 后逐字节计算，**相等**；拉下的 198 MB 副本已删除，只留摘要 `e-device/apk-identity.txt`） |
| 构建输入新鲜度 | `./gradlew --offline :core:database:assembleDebugAndroidTest` → `BUILD SUCCESSFUL`、`76 actionable tasks: 76 up-to-date`（Gradle 自判输入未变，APK 与当前 `core/database` 源码集一致）；最近一次主源改动 `StudyDatabasePort.kt` mtime 11-02 07:20 早于 APK 11-04 18:37 |
| 测试类 | `com.tingyun.smartmistakebook.core.database.ErrorCauseLedgerInstrumentedTest`（3 例） |

> 说明：库模块的 androidTest APK 自带被测库，无独立 app APK；故 recorder 的
> `apk_sha256` 与 `test_apk_sha256` 同为 `32235e63…`，不冒充“两个产物”。

### 1.3 实跑（逐字命令与真实退出码）

```bash
python -I tools/ci/am_instrument_recorder.py --serial emulator-5562 \
  --package com.tingyun.smartmistakebook.core.database.test \
  --class com.tingyun.smartmistakebook.core.database.ErrorCauseLedgerInstrumentedTest \
  --apk-sha 32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61 \
  --test-apk-sha 32235e6323f7caa45bdbdb6a0c566f5f50c9fb4da26cef975163deb3a7501a61 \
  --label e061-errorcause-ledger \
  --out-dir docs/research/evidence/2026-11-06-batch-45/e-device
```

结果：`counts={tests:3, evaluated:3, failures:0, errors:0, skipped:0}`、`verdict=PASS`、
recorder `EXIT=0`。原始 `am instrument` 文本 3×`STATUS_CODE: 1` + 3×`STATUS_CODE: 0`、
`OK (3 tests)`、`INSTRUMENTATION_CODE: -1`。

**三类证据（互不派生）**：

| 类 | 文件 | 内容 |
|---|---|---|
| device | `e-device/e061-errorcause-ledger.device.json`、`e-device/e061-logcat.txt` | 设备/启动 id/包/计数；logcat 中 `TestRunner: started/finished` 三对，`FATAL/ANR` 命中 0 |
| test | `e-device/e061-errorcause-ledger.xml` | JUnit XML：`tests=3 failures=0 errors=0 skipped=0`，三例具名 |
| cmd（机械复算） | `e-device/recompute.txt`（`recompute.py`） | 10 条对账（状态码、用例名集合、XML 计数、device.json）全 OK，`MISMATCHES=0` |

**假绿控制**：同一 APK、同一 runner，用不存在的类
`…NoSuchTestClassControl` 重跑 → recorder `EXIT=1`、`verdict=FAIL`
（`e061-control-no-such-class.*`、`control-recorder.out`）。证明本录法不是恒绿。

### 1.4 这条证据能写什么、不能写什么

**能写**：在 APK `32235e63…` + emulator-5562 上，`ErrorCauseLedgerInstrumentedTest`
的 3 个用例真实通过，覆盖真 SQLite 上的幂等重放、`active_key` 唯一、supersede
主键冲突回滚、撤回触发失败回滚与活键释放。

**不能写**：
- 不能据此把 E-061 改 PASS：该行检查的是“错因落账**并发**”，用例是顺序执行，
  没有多线程/多连接竞争（源码 `ErrorCauseLedgerInstrumentedTest.kt:43,155,268` 三个
  `@Test` 均为 `runBlocking` 串行）。
- 不能外推到真机（仅 x86_64 模拟器）、不能外推到 schema 迁移（本用例用
  `openInMemory`）。
- 因此台账 E-061 **保持 UNVERIFIED**；本轮只把“本轮未跑设备”这一条改为已跑，
  并发缺口仍在。台账字节**未被本轮修改**（改它会改变 `ledger_sha256`，使 26 份
  已绑定该 sha 的复核/报告证据失效；而 verdict 不变，收益为零）。

## 2. WS7 三项独立复算（只读，不重跑设备长任务）

### 2.1 KD-76 A6：三处门都拒绝 10+10

| 命令 | exit | 输出（逐字） |
|---|---:|---|
| `python -I tools/ci/a6_plan.py validate --plan /tmp/ws7-a6/a6-plan-10.txt` | **2** | `a6_plan: roundsPerBatch='10' expected '20'` |
| `bash tools/ci/run_kd76_rounds.sh --serial emulator-5562 --mode reboot --rounds 10 --first-round 11 --out /tmp/ws7-a6/never --batch-id B --run-id probe --a6-plan build/kd76-runs/a6b/a6-plan.txt` | **2** | `run_kd76_rounds: A6 mode requires --rounds 20 --first-round 1 (got 10/11)` |
| `python -I tools/ci/summarize_kd76.py /tmp/ws7-a6/batch10` | **2** | `samples: n=9/20 \| G1: RED`；`G2: NOT EVALUABLE`；`missing rounds: [11…20]` |

代码依据（本轮亲自读到）：`tools/ci/run_kd76_rounds.sh:116-118` 的
`die "A6 mode requires --rounds 20 --first-round 1"`；
`tools/ci/a6_plan.py:80` 把 `roundsPerBatch={MAIN_ROUNDS}` 写死、`:105` 校验同值；
`tools/ci/summarize_kd76.py:60` `MAIN_ROUNDS = 20`、`:503-507` 用
`set(range(1, MAIN_ROUNDS+1))` 判缺轮。→ **10+10 在树内没有任何判词路径**，
WS7 判 UNVERIFIED 成立，**KD-76 维持 open**。

### 2.2 isolation：门 INVALID、套件零实体执行

- `lowload-gate-5562.json`：`verdict=INVALID`、`reasons=["la_ratio_max"]`、
  `la_ratio_max.value=0.3825 > limit=0.3`；其余 6 项均未超限（`busy_p95 18.71≤21.14` 等）。
- `isolation-invalid-gate.txt`：`INSTRUMENTATION_STATUS_CODE: -4` 计 **15** 次、
  `STATUS_CODE: 0` 计 **0** 次，末行却是 `OK (15 tests)` —— 典型的跳过假绿。
- `isolation-witness-check.txt`：`body_rows=0`、15 个方法的 `cpu_ratio_per_method`
  全为 `null`、`gate_cache.reused_true=14`、`violations=[]`。
→ isolation 15 例**零执行**，**BLOCKED/未定案维持**，不得改判任何行。

### 2.3 p09：三次都落在慢重建绿区

`docs/acceptance/evidence/2026-11-06-batch-45/e-device/p09_recompute.py` 从
`ws7/p09-run{1,2,3}-logcat.txt` 原始行重算 `killing :visual_js worker` → 其后第一条
`visual_js warm-up finished`：

| run | 重算延迟 | 与 1300 ms 构造窗 |
|---:|---:|---|
| 1 | **2071 ms** | 慢区（未采样失败区） |
| 2 | **1890 ms** | 慢区 |
| 3 | **1861 ms** | 慢区 |

脚本自检：3 个 run 各解析出 1 组配对（每 run 日志内第 2 次 kill 之后没有 warm-up，
不参与配对），`P09_RECOMPUTE=OK`。

**本会话自己的三次实跑（同一 APK 身份）**：app `c5f5efd1…`、test `8ad0ff90…`
（设备 `sha256sum` 与本地产物逐字节相等）。

| run | 结果 | `Time:` | 重建延迟（脚本复算） |
|---:|---|---:|---|
| 1 | **FAILURES!!!** `AssertionError: initial connect failed`（`…DeviceInstrumentedTest.kt:109`） | 16.755 s | 无 kill→warm-up 配对（连接阶段即失败） |
| 2 | `OK (1 test)` | 17.665 s | **2794 ms**（慢区） |
| 3 | `OK (1 test)` | 17.823 s | **2666 ms**（慢区） |

原始件 `verify-p09-run{1,2,3}.txt`、`verify-p09-run{1,2,3}-logcat.txt`、摘要
`verify-p09-summary.txt`。→ **p09 未收敛维持**；本会话新增的是一个**红样本**
（connect 阶段），比“三次全绿”更能说明问题，三次全绿不能写“已修”。

## 3. 门禁实值（本会话重跑）

| 门 | 命令 | exit | 实值 |
|---|---|---:|---|
| 报告 | `python -X utf8 -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md --require-complete` | **1** | `882/30/144/144`；`violations=[NOT_ACCEPTANCE_COMPLETE, PASS_WITHOUT_REVIEW]`；`pass_with_review=0` |
| lint | `python -X utf8 -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md` | 0 | `violations=[]`、`warnings=29`（全 `EVIDENCE_REUSED`） |
| trace | `python -X utf8 -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md` | 0 | `gaps=[]`、`total=1200` |

台账 sha 仍为 `bb25ef2360562de4f00f9a49e2b0b5aed576d77500ce1bc7dcba64c4879ea686`
（本轮未改台账，故与 `docs/acceptance/adjudication.md` 一致）。

## 4. 边界（不得外推）

1. 本轮**只跑了一个设备用例类**（3 例）；没有重跑 E 域 JVM 全量，没有重跑 20 轮 reboot。
2. 设备是 x86_64 模拟器；不能外推到 arm64/真机（F 域 10 条 BLOCKED 不变）。
3. E-061 的并发缺口仍在；本轮不把它读成“E 域设备面已清”。
4. 发版仍不成立；`acceptance_report` 仍 exit 1；**验收不通过**的结论不因本文件改变。
