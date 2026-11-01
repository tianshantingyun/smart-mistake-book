# E-03 P-09 客户端队列背压设备负向（真进程端到端）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤二·新增设备例 2（P-09，`VisualJsClientFrameQueueDeviceInstrumentedTest`） |
| gate-or-kd | 批次 38 §1 B.6（清单 P-09 行 `:132` 设备负向缺口："经 engine.execute 的 4 program 并发 → 第 4 个 BUSY / latest-wins"） |
| verdict | PASS |
| executed-at | 2026-11-01T04:15+08:00 |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest（E-01 §3 同包） |
| apk-sha256 | `33e1d6a2…161fa` |
| exit-code | 0 |
| raw-log | `build/device-track-batch38/step22-p09-queue.log` + `step22-p09-queue-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

清单 P-09 行的设备负向缺口：经**真 `:visual_js` 隔离进程 + 真 AIDL + 真 PFD** 的生产 `engine.execute`，构造确定性队列窗口（`abandonForTest()` 真终止 → 首帧在飞卡重建窗口 ≥2 s）→ 1 在飞 + B1/B2（同 program latest-wins）+ C → 第 4 个不同 program D 立即 `Rejected(BUSY)` 诊断含 "frame queue is full"；B1 被 "superseded by a newer state of the same program" 顶掉且永不渲染；A→B2→C 全 Rendered 且完成时刻单调（跨 program FIFO）；`connectionCountForTest()==2`（恰一次重建，无进程放大）。判据 = 套件断言面（`VisualJsClientFrameQueueDeviceInstrumentedTest.kt:97-176`），不放宽。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.VisualJsClientFrameQueueDeviceInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -v threadtime -d
```

- 退出码：0。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 1 | =构建前计数 1 |
| failures / skipped | 0 / 0 | 期望 0 |
| summary | **OK (1 test)**，Time 6.376 s | OK (1) |
| 签名命中（九类） | 0 | 期望 0 |
| worker spawn/died | 2 / 0（首连 + 恰一次重建；died=0 属 abandon 路径 `terminate accepted`，非崩溃） | 重建恰 1 次 |

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: test=aFourthDistinctProgramIsRejectedBusyAndLatestWinsKeepsFifoOrderWhileOneFrameIsInFlight
OK (1 test)
```

BUSY/superseded/FIFO/connectionCount 断言全部由用例内断言判定（`OK` 即全过；无 assume 跳过位）。

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：无；保留：九类签名计数（0）、spawn/died 计数。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：1/1 绿，真进程端到端队列背压负向（第 4 program BUSY、latest-wins、跨 program FIFO、恰一次重建）在指定设备与 APK 上确实执行通过。清单 P-09 行的"客户端队列层设备负向未执行"缺口由本证据补齐。
