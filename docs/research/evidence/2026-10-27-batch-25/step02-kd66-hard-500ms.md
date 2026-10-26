# E-02 KD-66 关闭门（hard+500 ms 设备用例）——未写，如实登记

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-27-batch-25 |
| step | 二（KD-66 hard+500 ms） |
| gate-or-kd | KD-66（关闭门：watchdog 在硬期限杀进程且宿主按时返回） |
| verdict | **NOT-WRITTEN（专属用例未写；无设备执行，KD-66 维持 open）** |
| executed-at | 2026-10-27T03:52:00+08:00（核查时点） |
| operator | 设备验证轨（subagent，批次 25） |
| head | 25ddb60273e32ebd4100e8deb9f965bb0dfd4476 |
| worktree | dirty（他线在飞） |
| device-serial | —（本步无设备执行） |
| sanitized | 是（无待脱敏内容；扫描见 00-index §6） |

## 1. 目的与判据（任务书原文）

> 二、KD-66 hard+500ms 专属用例（若已写）：验证 watchdog 在硬期限杀进程且宿主按时返回；
> **未写则如实登记**。

KD-66 的关闭门（台账原文，`docs/known-defects.md` KD-66 条目，2026-10-27 T5 同步后实读）：
"剩余关闭门只有 hard+500 ms 设备用例（现有 watchdog 以 ≤6 s 判死，不算）"。判据形状：
issue #240 挂死程序下，L2 watchdog 在 1500 ms 硬期限杀进程，且宿主 `execute()` 在
hard+500 ms（≈2000 ms）内返回。

## 2. 核查方法与结果（命令 + 命中）

```bash
# 全部 androidTest 源内搜宿主按时返回形状（+500ms / 2000ms 断言 / host return）
grep -rn "500" app/src/androidTest/ core/data/src/androidTest/ | grep -v "1500\|2500\|…"
# 唯一相关命中：VisualJsIsolationInstrumentedTest.kt:52 的 KDoc 自认——
#   "≤6 s 判死，不能作为 KD-66 hard+500 ms 关闭门"
grep -rn "2_000\|2000\|hardPlus\|hostReturn\|returnsBy" \
  app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/ \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/
# 命中均为无关时间戳夹具（FigureGenerationReconciler 的 epoch 值、race 夹具的
# timeMs=2_000L），无任何"宿主在 hard+500 ms 内返回"的断言
```

设备轨读到的现用例计时口径（`VisualJsIsolationInstrumentedTest.kt:187-233` 实读）：
`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` 以
`withTimeoutOrNull(6_000L)` 等 `deathSignal()`——**≤6 s 判死，弱于关闭门**；该文件 KDoc
`:52` 同句自认。批次 25 T1 的新增交错用例（2 例，fake-binder）与 T2/T3 用例均不覆盖
#240 挂死形状。

## 3. 判定

**NOT-WRITTEN（未写 → 如实登记）**：批次 25 没有任何轨落地 hard+500 ms 专属设备用例；
本步没有可执行的用例，无设备证据产生。**KD-66 关闭门未达成，KD-66 维持 open**；
现有 ≤6 s watchdog 用例的隔离面证据见 E-01（不作为关闭证据引用）。

补写入口（供属主轨参考，本轨未实施）：新增仪器化用例应断言
`execute()` 总耗时 ≤ hard(1500 ms)+500 ms 且进程被 SIGKILL（死亡事实），并覆盖
"宿主按时返回"与"新进程无 BUSY"两断言；不得放宽为 ≤6 s。
