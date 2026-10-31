# E-04 P-10 死亡未确认 fail-closed 设备负向

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤二·新增设备例 3（P-10，`VisualJsDeathUnconfirmedFailClosedInstrumentedTest`） |
| gate-or-kd | 批次 38 §1 B.7（清单 P-10 行 `:133`："死亡未确认 fail-closed"设备负向） |
| verdict | PASS |
| executed-at | 2026-11-01T04:16+08:00 |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest（E-01 §3 同包） |
| apk-sha256 | `33e1d6a2…161fa` |
| exit-code | 0 |
| raw-log | `build/device-track-batch38/step23-p10-death-unconfirmed.log` + `step23-p10-death-unconfirmed-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

死亡事实与 `isBinderAlive` 双双缺席时（`UnconfirmableDeathWorkerContext` 夹具：bindService 拦截、握手/ready 即过、execute ACCEPTED 后回调永不到达）：①有界回 `HARD_DEADLINE_EXCEEDED`（宿主阶段期限路径）；②端到端释放 ≤ 2500+2000+500 ms 计时门；③`unavailableForTest()` 锁存真（结构失败，与 loading 非锁存成对相反）；④`connectForTest()` 拒绝 + `bindCount==1` 不再 bind；⑤显式重试也被 `ENGINE_CLOSED` 拒且诊断含 "unconfirmed death"；⑥同源码非重试回 `PROGRAM_FUSED`（熔断先于结构门）。判据 = 套件断言面（`VisualJsDeathUnconfirmedFailClosedInstrumentedTest.kt:93-179`），不放宽。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class com.tingyun.smartmistakebook.visualjs.VisualJsDeathUnconfirmedFailClosedInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
$A -s $S logcat -v threadtime -d
```

- 退出码：0。

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 1 | =构建前计数 1 |
| failures / skipped | 0 / 0 | 期望 0 |
| summary | **OK (1 test)**，Time 5.878 s（≤ 计时门 5000 ms + 断言开销） | OK (1) |
| 签名命中（九类） | 0 | 期望 0 |
| `death was not confirmed` 命中 | **1** | **设计测试主体**（假 worker 死亡永不确认 → 引擎该日志路径被真实触发并被断言消费），非异常 |

## 4. 关键输出（有界）

```text
INSTRUMENTATION_STATUS: test=aWorkerDeathThatIsNeverConfirmedFailsClosedWithinItsBudgetAndNeverBindsAgain
OK (1 test)
```

## 5. 失败 / 跳过 / 异常

无。`death was not confirmed` 1 次为被测行为本身（⑤断言直接消费该诊断串）；spawn=0 为夹具设计（bindService 被拦截，不启动真隔离进程——隔离进程语义由 E-06 隔离套件承担，套件 KDoc 同口径登记）。

## 6. 脱敏与去噪

- 删除：无；保留：`death was not confirmed` 计数与理由。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：1/1 绿，fail-closed 六断言位在指定设备与 APK 上确实执行通过；清单 P-10 行"无死亡未确认设备负向"缺口由本证据补齐。
