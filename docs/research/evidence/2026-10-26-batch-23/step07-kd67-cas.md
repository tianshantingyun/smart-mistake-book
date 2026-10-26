# E-07 KD-67 生产落库 CAS（core:database 仪器化）

| 字段 | 值 |
|---|---|
| evidence-id | E-07 |
| convention-version | 1 |
| batch | 2026-10-26-batch-23 |
| step | T2 KD-67 落库半边（设备执行） |
| gate-or-kd | KD-67 |
| verdict | PASS |
| executed-at | 2026-10-26T16:25:00+08:00 |
| operator | 设备验证轨（subagent，批次 23） |
| head | f0a145f850931887385c4bf2e1f798a0e8f2e391 |
| worktree | dirty（他线在飞；本轨未改产品源） |
| device-serial | emulator-5554 |
| device-avd | test_device |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | `core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk`（自含测试 APK） |
| apk-sha256 | `feb1b3601939487297b37af2c95a480b8b00995ba74d7c0da859f073aaa6df4e` |
| apk-version | — |
| exit-code | 0（adb 进程退出码；非判据） |
| raw-log | `build/device-track-batch23/step07-kd67-cas.log`（80 行）、`step07-kd67-cas-logcat.txt`（90 行） |
| record-ref | `docs/research/2026-10-26-batch-23-record.md` §4 |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

T2 轨本批新增的 `AssessmentItemVerificationStatusInstrumentedTest` 是其唯一设备判据（"新用例必须真跑过才算数"）。要求：

1. CAS 只改 `verification_status` 一列，其余字段逐字段不变；
2. 真实受影响行数：命中 = 1、过期 expected = 0；
3. 行不存在返回 0 且不插入；
4. 只有 `UNKNOWN → VERIFIED/USER_ASSERTED` 允许，其余写库前抛契约异常；
5. 并发 CAS 恰有一个赢家。

## 2. 命令（可复跑）

```bash
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 install -r -t core/database/build/outputs/apk/androidTest/debug/database-debug-androidTest.apk
$A -s emulator-5554 logcat -c
MSYS_NO_PATHCONV=1 $A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.database.AssessmentItemVerificationStatusInstrumentedTest \
  com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch23/step07-kd67-cas-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 5 | 5 |
| failures | 0 | 0 |
| skipped | 0 | — |
| `INSTRUMENTATION_CODE` | -1 | 非判据 |
| 12 类日志签名命中 | 0 | 期望 0 |

## 4. 关键输出（有界）

```text
test=casUpgradesOnlyTheVerificationStatusColumn            STATUS_CODE 0
test=aStaleExpectedStatusIsANoOp                           STATUS_CODE 0
test=aMissingRowIsNeverCreated                             STATUS_CODE 0
test=onlyUnknownCanBeUpgradedThroughTheCasPort             STATUS_CODE 0
test=concurrentCasWritersHaveExactlyOneWinner              STATUS_CODE 0
OK (5 tests)
INSTRUMENTATION_CODE: -1
```

## 5. 失败 / 跳过 / 异常

无。KD-67 的 G5 生产者半边在真 Room + 真 SQLite 上成立；KD-67 是否关闭取决于其消费面/主会话裁定，不在本轨结论内。

## 6. 脱敏与去噪

同 E-01。证据目录统一扫描见 `00-index.md` §6。

## 7. 判定

**PASS（5/5）**。新仪器化用例已真跑过，满足"新用例必须真跑过才算数"。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch23/step07-kd67-cas.log`、`step07-kd67-cas-logcat.txt`
- 行数：80 行 / 约 6 KiB；logcat 90 行
- 生成命令：见 §2
