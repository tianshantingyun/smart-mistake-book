# E-12 崩溃签名与 worker 生命周期收口（26 窗全量扫描）

| 字段 | 值 |
|---|---|
| evidence-id | E-12 |
| convention-version | 1 |
| batch | 2026-11-01-batch-38 |
| step | 步骤五（崩溃签名 + worker 生命周期收口 + 红例归因汇总） |
| gate-or-kd | 批次 38 设备收口（批次 37 判据 §3.3 九类签名扫描原命令，目录换 batch38） |
| verdict | PASS（26 窗九类签名全 0；worker 生命周期逐窗对账成立；3 例首跑红全部归因并被同 APK 绿复跑取代） |
| executed-at | 2026-11-01T05:4x+08:00 |
| operator | 设备验证轨（批次 38 工作流子代理） |
| head | `dcfdc5b5` |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | 三包（E-01 §3/§4） |
| apk-sha256 | `94c82daa…`（app 主包）/ `33e1d6a2…`（app androidTest）/ `7baf8c46…`（database androidTest）/ `e86fce38…`（data androidTest） |
| exit-code | 扫描循环 0（计数全 0；`grep -c` 无命中退 1 属预期，输出全表） |
| raw-log | `build/device-track-batch38/*-logcat.txt`（26 份） |
| record-ref | `docs/research/2026-11-01-batch-38-record.md` 设备节 |
| sanitized | 是（00-index §6） |

## 1. 目的与判据

对全部 26 个 `am instrument` 窗口的 logcat 做九类崩溃签名全量计数（含 0），并对 `:visual_js` worker 做 spawn/died/watchdog-kill/`death was not confirmed`/`warm-up failed` 生命周期对账；红例逐条归因（批次 37 §五 序）。

## 2. 命令（可复跑）

```bash
cd build/device-track-batch38
for f in *-logcat.txt; do printf "%-42s " "$(basename "$f" -logcat.txt)"; \
  grep -cE "FATAL EXCEPTION|ANR in |NoSuchMethodError|NoSuchMethodException|NoClassDefFoundError|ClassNotFoundException|UnsatisfiedLinkError|CANNOT LINK|failed to map segment|SIGSEGV" "$f"; done
for f in *-logcat.txt; do d=$(grep -cF "death was not confirmed" "$f"); w=$(grep -cF "warm-up failed" "$f"); \
  k=$(grep -cF "watchdog hard deadline" "$f"); s=$(grep -cE "Start proc .*visual_js" "$f"); die=$(grep -cE "Process .*visual_js.*has died" "$f"); \
  printf "%-40s unconfirmed=%s warmup-failed=%s watchdog-kill=%s spawn=%s died=%s\n" "$(basename "$f" -logcat.txt)" "$d" "$w" "$k" "$s" "$die"; done
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| 九类签名（26 窗合计） | **0**（逐窗全 0：step03/06/07/08/09/10/11/12/13/14/14b/15/16/17/18/19/20/21/21b/22×2/23×2/24×2/25） | 期望 0 |
| `warm-up failed` | 26 窗全 0（每个被 spawn 的 worker 都到达 ready） | 期望 0 |
| `death was not confirmed` | 恰 1，仅在 step23-p10 窗（**设计测试主体**，E-04 §5） | 非异常 |
| watchdog kill | step21=1（非设计，E-02 已归因）、step14=7（5 设计+2 非设计）、step14b=5（全设计，15/15 绿自证）、step21b=0、其余窗 0 | 见 E-02/E-06 |
| spawn/died | 触 worker 的窗：step14=20/6、step14b=19/5、step19=2/0、step21=3/1、step21b=2/0、step22-p09=2/0、step24-p0405=3/0；died 全部有 kill/abandon 对应，无无因死亡 | 无进程放大 |

## 4. 关键输出（有界）

```text
（九类扫描）step14-isolation 0 / step14b-isolation-rerun 0 / step21-a09-ads 0 / step21b-a09-ads-rerun 0 /
step23-p10-death-unconfirmed 0 / ……（26 行全部 0）
（生命周期）step14: unconfirmed=0 warmup-failed=0 watchdog-kill=7 spawn=20 died=6
          step14b: unconfirmed=0 warmup-failed=0 watchdog-kill=5 spawn=19 died=5
          step21:  unconfirmed=0 warmup-failed=0 watchdog-kill=1 spawn=3  died=1
          step21b: unconfirmed=0 warmup-failed=0 watchdog-kill=0 spawn=2  died=0
          step22-p09-queue: watchdog-kill=0 spawn=2 died=0 ; step23-p10: unconfirmed=1 spawn=0
          step24-p0405-reconnect: watchdog-kill=0 spawn=3 died=0 ; step19-v01-app: spawn=2 died=0
```

设计杀基数说明：隔离套件的 issue-240/watchdog 用例按设计触发 worker 杀（全绿复跑轮本身含 5 次 kill），kill≠异常；非设计 kill 3 次全部集中于首跑高负载窗并被归因（§5）。

## 5. 红例逐条归因汇总（本批全部红例 = 3，均首跑出现、均被同 APK 绿复跑取代）

| # | 红例 | 套件/step | 失败形状 | 归因 | 取代证据 |
|---|---|---|---|---|---|
| 1 | `anAdvertisedJsSnapshotRendersThroughTheCoordinatorWithZeroRemoteDispatchWitnesses` | A-09（E-02，step21） | worker 看门狗 1500 ms 超限杀（pid 10780）→ HARD_DEADLINE_EXCEEDED | **D 环境**（4 模拟器高负载窗突发停顿；同形状大量通过；非 A/B/C 证据链见 E-02 §5） | step21b **OK (3 tests)** 同 APK |
| 2 | `anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse` | 隔离（E-06，step14） | 同上（pid 11260） | **D 环境**（同上；纯几何程序不经度量路径排除 B） | step14b **OK (15 tests)** 同 APK |
| 3 | `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram` | 隔离（E-06，step14） | 同上（pid 11505） | **D 环境**（同上） | step14b **OK (15 tests)** 同 APK |

绝对禁止清单遵行：未删/弱化断言、未 @Ignore/Assume、未 `-e class ...#method` 过滤红例（复跑为整套装件）、未改 `OK (N)` 语义、未以旧批次绿轮替代（复跑绑定当前树 + 本批 APK sha）。

## 6. 脱敏与去噪

- 删除：无；保留：全部计数与红例原文。
- 扫描：00-index §6 → 0 hits。

## 7. 判定

**PASS**：崩溃面零命中、worker 生命周期逐窗对账成立、3 例首跑红全部环境归因且被同 APK 绿复跑取代；无遗留红例。
