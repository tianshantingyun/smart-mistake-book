# E-05 P-04/P-05 收尾：重建预算 10 s 注入形设备例

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤二·新增设备例 4（P-04/P-05 收尾，`VisualJsReconnectBudgetInstrumentedTest`） |
| gate-or-kd | 批次 38 §1 B.8（清单 P-04 行 `:127` 预算三元组注入形设备例：首连 15 s 已有、死亡确认 2 s 由 E-04 补、**重建 10 s 本例补齐**） |
| verdict | PASS |
| executed-at | 2026-11-01T04:17+08:00 |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest（E-01 §3 同包） |
| apk-sha256 | `33e1d6a2…161fa` |
| exit-code | 0 |
| raw-log | `build/device-track-batch38/step24-p0405-reconnect.log` + `step24-p0405-reconnect-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

`DelayedAttachContext` 形态（真实 bindService、仅推迟 onServiceConnected 11 s）：首连 15 s 预算内成功（attach 11 s + ready），随后死亡后的重建 attach 超**重建预算 10 s**（`VisualJsClientEngine.kt:1233`，测试以字面量 "10000 ms" 钉住）→ 重建按 loading 如实失败：①`ENGINE_CLOSED` + 诊断含 "loading" 与 "10000 ms"；②`unavailableForTest()` 保持 false（loading 不锁存）；③`connectionCountForTest()` 保持 1；④重试仍被允许（重新 bind、再次如实 FAILED、仍不锁存）；⑤每次失败 attach 被解绑（无孤儿 binding）。判据 = 套件断言面（`VisualJsReconnectBudgetInstrumentedTest.kt:83-…`），不放宽。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.VisualJsReconnectBudgetInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -v threadtime -d
```

- 退出码：0。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 1 | =构建前计数 1 |
| failures / skipped | 0 / 0 | 期望 0 |
| summary | **OK (1 test)**，Time 34.651 s（首连 11 s attach + 重建 10 s 超时 + 重试再 11 s attach，量级与设计一致） | OK (1) |
| 签名命中（九类） | 0 | 期望 0 |
| worker spawn/died | 3 / 0 | 首连 + 两次重试 bind，无进程放大 |

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: test=aRebuildAttachBeyondTheReconnectBudgetFailsAsLoadingWithoutLatchingAndRetriesStayAllowed
OK (1 test)
```

## 5. 失败 / 跳过 / 异常

无。

## 6. 脱敏与去噪

- 删除：无；保留：签名计数（0）。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：1/1 绿，重建预算 loading 形状五断言位确实执行通过。至此预算三元组（首连 15 s / 重建 10 s / 死亡确认 2 s）注入形设备例成套（首连半边 = 隔离套件既有例 E-06 复跑绿、死亡确认半边 = E-04）。
