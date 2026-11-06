# E-11 批次 39 收尾轮·isolation 沉降窗与 T4 控制轮（原题"低负载窗"已按 §5.2 更正）

| 字段 | 值 |
|---|---|
| evidence-id | E-11 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39（收尾轮 2026-11-02） |
| step | 收尾轮·步三 |
| gate-or-kd | 强门 isolation 15/15 / 批次 39 T4 判别 |
| verdict | **FAIL**（沉降窗 8/15 红；控制窗 10/15 红，但两窗**红集不同**、且控制轮构建身份不可证 ⇒ **T4 未排除**；D 不能单独归因。原写"低负载窗 / T4 排除"已按 §5 更正） |
| executed-at | 2026-11-02T01:07:00+08:00（基线窗）/ 01:19:30（控制窗） |
| operator | 设备验证轨（子代理） |
| head | `f10a1e4e` |
| worktree | 514 M/??（开窗时，仅记录） |
| device-serial | `emulator-5562` |
| device-avd | `test_device` |
| device-api | 34 |
| device-abi | x86_64 |
| device-page-size | 4096 |
| apk | **基线窗** app `f97f2972…` + appTest `3c222fca…`（00:55 安装，01:06 开窗）；**控制窗** app `4c1508c1…` + appTest `b4242b7d…`（01:13 安装）。原写基线 `fe9452aa…`/`b4242b7d…` 为时点错配，见 §5.1 |
| apk-sha256 | baseline app `f97f29726ec13bba626d1480583217dd461902076555ff086b25e288ad6346bf`；baseline appTest `3c222fca7560327ab7200523b5c1332ebfd6228277a0eb133c34338985f9bfaf`；control app `4c1508c184859722155ff308a05001a0c9d316c5f3b29abcf286431f0a442ec4`；control appTest `b4242b7d535b02026bcc6ddd6c7cc2e0c4322ed82fef52e768ec596181cda5d0` |
| exit-code | adb-exit=0；`INSTRUMENTATION_CODE: -1` |
| raw-log | `build/device-track-batch39-tail/suite-isolation{,-logcat}.txt`、`suite-isolation-control{,-logcat}.txt`、`load-*-window.txt`、`control-*.txt` |
| record-ref | `docs/research/evidence/2026-11-01-batch-39/00-index.md` §11 |
| sanitized | 是 |

## 1. 沉降前置（基线窗）

```text
2026-11-02 01:06:19 loadavg = 0.34 0.83 1.33 1/1821 23345   （开窗瞬间 1-min ≤ 1.0）
ps -A | grep com.tingyun|androidx.test → 仅 app 主进程（无 instrumentation 进程）
主机 CPU（powershell Get-CimInstance … LoadPercentage 平均）= 62
dumpsys activity instrumentation → "Unknown command: instrumentation"（API 34 无此子命令；
   该输出已原样落盘 device-settle-probe.txt；以 ps 复核代替）
```

**这不是"低负载窗"（本复核轮更正）**：沉降门只证明**开窗瞬间** 1-min ≤ 1.0；窗内 4 个采样点为 0.62 → 2.80 → 3.99 → 4.79（`load-isolation-window.txt`），峰值远超 1.0，主机 CPU 开窗时已有 62%。按 `docs/research/2026-11-01-isolation-attribution.md` §3.2 的定义，P1（窗内主机进程列表快照）与 P3（窗前 5 min / 窗内每 10 s / 窗后 5 min 采样）**均未按定义记录**；因此本窗**不能**被判为"低负载窗被接受"，只能称"沉降后窗"。

## 2. 基线窗（同 APK/同快照）

```bash
adb -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
```

| 判据 | 要求 | 实测 |
|---|---|---|
| `OK (15 tests)` | 命中 | **未命中**（`Tests run: 15, Failures: 8`） |
| code 0 计数 | 15 | 7 |
| 非零 code（-1/-2/-3） | 0 | 8 |
| 红例集合 | ∅ | 8 例（下表） |
| watchdog hard kill | ≤6 | **11** |
| 非 watchdog kill | 全量列名 | `terminate(…) accepted` 2（设计内两次 abandon 路径）、`the host unbound the visual_js worker` 4（teardown）、`warm-up failed` 0 |
| 9 类崩溃签名 | 全 0 | 全 0（`isolation-crash-signatures.txt`） |
| 设备 loadavg | 记录 before/peak/after | 0.62 → 4.79 → 4.79 |

### 2.1 红例集合（8）

1. `aHungWorkerReleasesTheCallerWithinHardPlus500ms`
2. `aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`
3. `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`
4. `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`
5. `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`
6. `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`
7. `anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse`
8. `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`

### 2.2 首帧与归因（A 类排除；D 为与高负载一致的推断，非已隔离的单一原因）

首例失败原文（`suite-isolation.log:31`）：

```text
AssertionError: the worker must render before the hang is injected … :
  Rejected(reason=HARD_DEADLINE_EXCEEDED, diagnostics=visual_js worker died: binder died)
  at VisualJsIsolationInstrumentedTest.kt:485
```

四源：

- 断言位：`app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt:479-489`（预热必须 `Rendered`）。
- 产品 watchdog：`app/src/main/.../visualjs/VisualJsService.kt:525-528`（1500 ms 到点 `killSelf("watchdog hard deadline")`）。
- 预算常量：`core/domain/src/main/.../VisualExecutionEngine.kt:126` `SERVICE_HARD_DEADLINE_MILLIS = 1500L`。
- 冷启动用例：`VisualJsIsolationInstrumentedTest.kt:327-339`（7.6 s attach 后首个 execute）。

logcat 时序证明为"单次执行超 1500 ms 被 watchdog 杀"而非注入 hang：

```text
17:06:56.754 VisualJsService: visual_js warm-up finished
17:06:58.406 VisualJsService: hard deadline (1500 ms) for request 13a0b282-…
17:06:58.406 VisualJsService: killing :visual_js worker: watchdog hard deadline
17:06:58.443 ActivityManager: Process …:visual_js … has died: vis FGS
```

同一窗口设备 loadavg 在窗内自 0.62 升到 4.79（`load-isolation-window.txt`）；开窗时主机 CPU 62%。归因类别 **D（环境/负载）**，不判 C 产品回归；A 类夹具不成立（7.6 s attach 在首连 15 s 预算内，失败发生在 watchdog 侧）。

**因果证据的时点更正（本复核轮）**：原句"同一窗口主机侧有他线 Gradle 构建（`core/database/.../classes.jar` 于 02:21 被重建、源文件 01:57 起持续变动，见 E-14）"引用的构建/变动**晚于或早于本窗**——本窗是 01:06–01:09，02:21 的重建晚约 50 分钟，01:57 起的持续变动发生在本窗之后。它们**不能**作为本窗负载的因果证据。本窗可直接引用的只有：① 窗内 loadavg 0.62→4.79；② 主机 CPU 开窗时 62%；③ 失败形状全部是 1500 ms 服务硬期限被越过。因此 D 是**与高负载一致的推断**，不是被隔离出的单一原因（P1/P3 未记录，见 §1）。

## 3. T4-reverted 控制轮（批次 39 裁定强制）

- 回退对象纠正：isolation 套件 0 引用 `beforeRegistrationHookForTest`；有效对象是 `VisualJsClientEngine.kt` 6 处时间戳写点（`:401`/`:570`/`:670`/`:821`/`:840`/`:1161`），保留 `ConnectStageTimestamps` 类与访问器。
- 基线 sha：`f0786242633f72a3f912fcd0038161949ae39b94bcdca52d38320839bf6f9209`（`control-baseline-engine.sha256`）。**注意：这是"基线"源文件的 sha，不是"回退后"源文件的 sha**；文件在还原后用 `sha256sum -c` 复核通过，证明的是"已回到基线"，不能证明控制构建时确实是回退态。
- 控制构建身份（本复核轮更正）：`build-control.log`（01:12:45）全文**没有** revert / 时间戳写点相关记录，收尾为 `BUILD SUCCESSFUL in 32s / 259 actionable tasks: 5 executed, 254 up-to-date`；`grep -niE 'revert|timestamps|control' build-control.log` = **0 命中**。因此"控制包 = 去掉 6 处写点的构建"**没有原始证据**。
- 控制包 vs 基线包：控制 app `4c1508c1…`（`control-apk-sha256.txt:1` + `device-apk-sha-control.txt:1`）；基线窗 app `f97f2972…`（00:55 `install.log` + `device-apk-sha.txt:1`）。**appTest 也不同**：基线窗 `3c222fca…`（`device-apk-sha.txt:6`），控制窗 `b4242b7d…`（`device-apk-sha-control.txt:2`）。app APK sha 不等本身也不是强证据——E-09 §6 已自承"同源不同字节，增量打包非字节可复现"。
- **结论**：控制轮是"另一个 app + 另一个 appTest 的一次运行"，不是可证伪的 T4-reverted 对照。
- 沉降：控制窗前 1-min loadavg 0.80（01:19:30），ps 无 instrumentation；窗内 1.24 → 4.79 → 3.92 → 5.49 → 5.49（`load-control-isolation-window.txt`），同样不是低负载窗。
- 控制窗结果：`Tests run: 15, Failures: 10`；watchdog hard kill 13；loadavg 峰值 5.49。

### 3.1 判别表落点（本复核轮更正）

| 基线窗（01:06–01:09） | 控制窗（01:19–01:24） | 结论 |
|---|---|---|
| 红 8/15；kill=11；红集 = 8 个（含 `aHungWorkerReleasesTheCallerWithinHardPlus500ms`） | 红 10/15；kill=13；红集 = 10 个（**新增** 3 个 AIDL-wire 用例：`anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead` / `anUnattributedEngineFailureKeepsItsPrefixAcrossTheAidlWire` / `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot`；基线红的 `aHungWorker…` 在控制窗转绿） | **两窗红集只有 7 例交集，不是同一组用例的"更差重跑"**；聚合 8 vs 10 不能排除 T4 的因果贡献。正确判别需要逐方法配对（名单一致才可比较），本轮**未做**。T4 **未排除**；D 也不能单独归因 |

## 4. 结论

isolation 未达 15/15，强门维持 **FAIL**。原结论"T4 关联被控制轮排除，主因是设备/主机负载（D）"**撤回**：控制轮不是可证伪对照（构建身份不可证、两窗红集不同）。当前只能得出：本批两窗都红，且两窗都不在低负载条件下；**T4 与 D 均未被隔离**。不建议改 `SERVICE_HARD_DEADLINE_MILLIS` 或测试断言来"变绿"；需在他线构建/测试静止、设备 loadavg ≤1.0 的长窗复跑，并按逐方法配对重做 T4 判别。

## 5. 第 2 轮复核修复（2026-11-02；零 Gradle/零设备）

本节由复核修复轮追加，只修口径与证据，不重写原始 raw log：

### 5.1 APK 身份时点错配（原 §头部/§2/§3）

- 原写基线 app `fe9452aa…`：该包由 `build-restore.log` 于 **01:26** 构建、`install-restore.log` 于 **01:27** 安装，**晚于基线窗 01:06–01:09 约 17 分钟**，不可能是基线窗运行的包。
- 基线窗实际包：`install.log` 00:55:14 安装 app `f97f2972…` + appTest `3c222fca…` + data `f7e96147…`（`apk-sha256.txt:1/6/5`、`device-apk-sha.txt:1/6/5`）。该组记录在窗**前 10 分钟**，窗内没有再次核对设备 sha——这是"时序绑定"而非"窗内实测"，证据强度如实降低。
- 控制窗实际包：`install-control.log` 01:13:23 安装 app `4c1508c1…` + appTest `b4242b7d…`（`control-apk-sha256.txt`、`device-apk-sha-control.txt`）。
- 因此控制对比是 **`f97f2972`/`3c222fca` vs `4c1508c1`/`b4242b7d`**，不是原写的 `fe9452aa`/`b4242b7d` vs `4c1508c1`/`b4242b7d`。

### 5.2 低负载窗表述（原 §1/§2）

见 §1：开窗瞬间 0.34 不等于窗内低负载；窗内峰值 4.79（基线）/5.49（控制），P1/P3 未按定义记录。本文件不再使用"低负载窗"作为判定前提。

### 5.3 因果证据越窗（原 §2.2）

见 §2.2：02:21 重建与 01:57 起的变动不在本窗内，不能作为本窗负载的因果证据。

### 5.4 控制轮不可证伪 + 红集不可比（原 §3/§3.1/§4）

见 §3/§3.1/§4：`control-baseline-engine.sha256` 是基线 sha；`build-control.log` 无回退记录；两窗红集只有 7 例交集；T4 未排除。

### 5.5 判据文本字段错误（T1 §3.3 第 3 条）

T1 的红例集合命令 `awk '/INSTRUMENTATION_STATUS_CODE:/{if ($3+0 != 0) ...}'` 字段号错误：日志行是 `INSTRUMENTATION_STATUS_CODE: -2`，值在 **$2**，`$3` 为空恒 0，8 个红运行时输出为空（本复核轮实跑：输出空、exit=0），按"输出为空=PASS"会**假绿**。但**只把 `$3` 改成 `$2` 仍不对**：本日志的 status code 分布是 `1`×15（用例开始）、`0`×7（通过）、`-2`×8（失败），`$2+0 != 0` 会把 15 个开始行也算进去，输出 23 条（假红）。失败码是**负数**，正确命令必须用 `< 0`：

```bash
awk '/INSTRUMENTATION_STATUS_CODE:/{if ($2+0 < 0) print}' suite-isolation.log
# 本批复算：8 行（全部 -2）；$2+0 != 0 会得 23 行（15 个 1 + 8 个 -2），$3+0 != 0 得 0 行
```

本批复算：`grep -c 'INSTRUMENTATION_STATUS_CODE: -2'` = 8、`grep -c 'INSTRUMENTATION_STATUS_CODE: 0'` = 7、`grep -c 'INSTRUMENTATION_STATUS_CODE: 1'` = 15，与最终 `FAILURES!!!` 块的 8 个红例名一致；设备轨当时改用最终块人工提取，结论不受影响。

### 5.6 kill 门槛口径冲突（交主会话裁定）

计划任务 2 与 T1 §3.3 写"kill 计数回落到设计基数附近（≤6）"；台账/清单改用 `isolation-attribution.md:236-247` 的 A3（非设计 watchdog kill=0）。本窗两读都 FAIL（基线 kill=11、控制 13），判定不受影响；但**台账单方面改判据未标主会话裁定**，本文件按"待裁定"记录：`kill ≤ 6` 不作为通过判据，是否正式替换由主会话裁定。

### 5.7 还原记录（原 §3.2 保留，不改）

```bash
sha256sum -c control-baseline-engine.sha256   # …: OK
git status --porcelain -- VisualJsClientEngine.kt   # 空
```

控制包只作因果判别；还原后 app 包为 `fe9452aa…`（01:26 构建、01:27 安装），它只用于此后的 KD-76 校准窗与 E-12 的 26 套件窗，**不是**基线 isolation 窗的包。

**本节改动文件**：本文件；`docs/known-defects.md`、`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`、`00-index.md`（回填见各自追加块）。
