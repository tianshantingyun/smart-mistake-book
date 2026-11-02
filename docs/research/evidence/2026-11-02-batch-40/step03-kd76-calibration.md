<!-- 体积自查：约 95 行；R4 上限 400 行 / 65536 B -->
# E-40-03 KD-76 诚实复跑（n=20 真冷启动）

| 字段 | 值 |
|---|---|
| evidence-id | E-40-03 |
| convention-version | 1 |
| batch | 2026-11-02-batch-40 |
| step | 步骤三（判据 §49） |
| gate-or-kd | KD-76；R2 §3.2/§3.3；主口径 M p95 ≤ 5000 ms |
| verdict | **BLOCKED**（代码侧无“单轮入口”；按判据不得用单进程 20 轮冒充） |
| executed-at | 2026-11-02T10:52+08:00（静态复核；未运行校准套件） |
| operator | 设备验证轨 |
| head | 执行期 HEAD 已由 `a3f367df` 变为 `dee81ea7`（见 E-40-05） |
| worktree | 源漂移 169 行 / 60 文件 |
| device-serial | emulator-5584（API 34 / x86_64 / 4096；guest load 0.01 at 10:11） |
| apk-sha256 | app test `565fc85b1edadc79e6497261976acbf8e03e1f11326fa4a6e6de9abb9a357c7b`（本项未执行） |
| exit-code | 无（未执行） |
| raw-log | 无本项 raw；静态证据见 §1/§2 |
| record-ref | `docs/research/2026-11-02-batch-40-record.md` |
| sanitized | 是 |

## 1. 硬缺口：代码没有“只跑一轮”的入口

```text
$ grep -nE "getArguments|kd76Rounds|BOOT_COUNT|reboot" \
    app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsConnectStageCalibrationInstrumentedTest.kt
86: * 同形），**不是** R2 §3.2 要求的"`adb reboot` 后不沉降即跑"的真实冷启动轮。因此：
87: * - 本用例的数字**不满足主口径的预注册执行条件**（无 reboot、两批不是两次独立设备运行），
89: * - 设备轨若按主口径执行，必须 `adb reboot` 后**只跑一轮/一次 am instrument**（或每样本
142:        // （典型：T1 形状的 reboot 后设备熄屏 → 批首 `screenIsInteractive()` 断言抛错）。
```

- 命中**全部是 KDoc/注释**；没有 `InstrumentationRegistry.getArguments()`、没有 `kd76Rounds`、没有 `BOOT_COUNT`/reboot 实现。
- 套件形状：单个 `@Test`，`CALIBRATION_ROUNDS = 20`、`BATCH_SIZE = 10`（`:912-913`），在**一个 instrumentation 进程内**跑 20 轮；这正是判据 §49.1 明确禁止冒充的形状。
- 判据给出的替代（每次 reboot 跑完整 20 轮、只取 A-01）成本约 20×，且需逐样本 reboot + 落盘 + 外部聚合；本会话未执行，也不把“单进程 20 轮”当样本。

## 2. 报告层守卫（静态 PASS；运行时负控 UNVERIFIED）

```text
678:        val preRegistered = samples.size == CALIBRATION_ROUNDS && !g1Red
685:                    if (mergedMain.p95 <= READY_GATE_MILLIS) "PASS" else "FAIL (must not be relaxed)"
697:                lines += "G2: NOT EVALUABLE — pre-registration unmet: n=${samples.size}/" +
```

- `PASS` 只出现在 `if (preRegistered)` 分支内；`n<20` 或 `g1Red` 时走 `G2: NOT EVALUABLE`，不打印 PASS。静态门满足 K49-5。
- 运行时负控（强制 G1 红、确认日志不出现 `G2 (main criterion M): ... => PASS`）**本批未做** → 该项标 UNVERIFIED；不得声称“守卫已实证”。

## 3. 主口径与常量

- 生产 `READY_TIMEOUT_MILLIS = 10_000L`（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:1325`），主口径门 = 5000 ms；本项未测得任何 M 值，**不得引用批次 39 的 9813 ms 作为本批数字**，也不得改常量。
- K49-6 判别（M 超 5 s 而 S-warmup p95 ≤5 s → 慢在 S-bind）本批无数据、未判定。

## 4. 低负载门与设备状态

- `adb devices -l`：4 个模拟器同时在线（emulator-5562/5566/5584/5596）；本轨用 emulator-5584，guest `/proc/loadavg` 10:11 = `0.01 0.02 0.00`。
- 判据 P1 要求“窗内无并发 Gradle/am instrument/adb install”；本会话在同一宿主上跑过构建与 30+ 套件，且他线仍在写源/构建，**不存在可证的 5 分钟干净窗**。
- 因 §1 的入口缺失已是硬阻塞，未继续做 P3 的 5 分钟采样；不把 guest load 低当作 P/G 通过。

## 5. 结论

- KD-76 **维持 open**；本批没有诚实的主口径结果。
- 若要关闭，最小前置是：给校准套件加 instrumentation 参数（如 `-e kd76Rounds 1`，只跑 1 轮并落 1 行）+ 20 次 reboot 逐样本执行 + 外部 nearest-rank 聚合 + P1–P4/低负载门。该改动属测试代码改动，需另立任务并重跑 E-40-01 的构建/快照门。
