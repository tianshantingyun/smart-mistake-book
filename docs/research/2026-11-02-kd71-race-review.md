# R8 · KD-71 `QuickJsVisualExecutionEngine` close/execute 竞态复核（批次 40，2026-11-02）

- 研究线：R8（计划 `.zcode/plans/plan-2026-11-02-batch-40-5x.md:90`；对应实施项 §1 item 36「KD-71 设备核销」与 item 51「KD-71 关闭核销（若修复在树）」）
- 任务：一、复核 race 修复现状（读码 + 设备用例）；二、给出关闭判据（设备证据要求）；三、若未修，给出最小修复形态
- 范围：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt` 及其两份测试、批次 39 设备证据、上游 quickjs-kt 1.0.15
- 不在范围：改代码、跑 Gradle/设备（共享纪律：禁改热区、不运行 Gradle、门禁由脚本串行执行）、git 写操作
- 结论速览：**修复已在树且形态完整（批次 22 T6 落码，批次 30 T7 补确定性红测缝）；批次 39 设备证据的源快照哈希与现树三文件逐字节一致，27 例全绿、两条 KD-71 用例逐例 `STATUS_CODE 0`。本复核建议：以"条件式关闭"核销 KD-71——批次 40 设备轨复跑同套件后关；若批次 40 期间三文件出现任何改动，则证据失效、必须重跑。**

---

## 0. 复核方法与证据等级

| 项 | 本轮做了什么 | 等级 |
|---|---|---|
| 读码 | 逐行读 `QuickJsVisualExecutionEngine.kt` 全 1111 行；仪器化用例读头部/夹具/KD-71 段（`:1-200`、`:760-950`）与全文 `@Test` 清单；JVM 用例全 135 行 | 一手（本会话实读） |
| 设备证据 | 读批次 39 E-04 与其原始日志 `build/device-track-batch39/suite-engine.log`、源快照 `src-sha-*.txt`、`src-snapshot.diff`、APK sha | 一手日志（非本会话执行） |
| JVM 证据 | 读 `core/data/build/test-results/testDebugUnitTest/TEST-...QuickJsVisualExecutionEngineJvmTest.xml` | 二手产物（`build/` 被忽略，命令归属 UNVERIFIED） |
| 上游事实 | `gh api` 实取 issue #240、main HEAD、`QuickJs.jni.kt@v1.0.15` 正文 | 一手（本会话实取） |
| **未做** | **未运行 Gradle、未跑设备用例、未做变异/回退红测** | **UNVERIFIED（见 §7）** |

设备证据之所以可用：本项目纪律"设备绿绑定源快照"（批次 39 记录 §0 第 4 条）。本轮把快照哈希与现树逐字节比对，结果见 §3.2——这是本轮能下"已修且证据在案"结论的唯一依据。

---

## 1. 缺陷本体与端口契约

- 登记原文（`docs/known-defects.md:2727`）：旧实现先 `closed.get()` 预检、再 `inFlight.compareAndSet`、最后 `currentTransaction.set`；`close()` 只 `currentTransaction.get()?.forceAbandon()`。close 落在预检与登记之间 → 读到 null → 不强弃 → 事务随后照常执行并返回成功。严重度登记为 low。
- 端口契约（`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:37-38`、`:44-45`）：

  > `close()` 幂等；关闭后 `execute()` 立即回 `ENGINE_CLOSED`，不得再启动任何事务。

  实现侧 KDoc 进一步把语义钉成五条（`QuickJsVisualExecutionEngine.kt:149-166`）：单锁二选一、强弃、有界等待、结果线性化、幂等。

---

## 2. 代码现状（修复形态，逐处行号）

### 2.1 状态机（单锁）

| 元素 | 位置 | 作用 |
|---|---|---|
| `stateLock` | `:187` | 唯一保护 `closed`/`inFlight` 的锁 |
| `closed` | `:190` | 终态标志（持锁读写） |
| `inFlight` | `:193` | 唯一在飞事务指针（持锁读写） |
| `beforeRegistrationHookForTest` | `:208-209`、调用点 `:247` | 批次 30 T7 的确定性红测缝；生产恒 `null`，一次可空读 |

登记（`execute`）与判定在同一临界区（`:251-267`）：

```kotlin
synchronized(stateLock) {
    if (closed) return rejected(ENGINE_CLOSED, "engine is closed")
    if (inFlight != null) return rejected(BUSY, "another visual transaction is already in flight")
    inFlight = transaction
    transaction.hardTimer = watchdog.schedule({ transaction.onHardDeadline() }, hardDeadlineMillis, MILLISECONDS)
}
```

`close()`（`:317-334`）：同临界区"置 `closed=true` → `watchdog.shutdownNow()` → 取走 `inFlight`"，出锁后 `forceAbandon()` + `awaitCompletion(hardDeadlineMillis + 500ms)`。幂等由 `if (!closed)` 承担；并发后来者即使 `closed==true` 也会取 `inFlight` 并等待（`:320-322` 注释与该行为一致）。

为什么 `watchdog.shutdownNow()` 必须与登记同临界区：`schedule` 在登记临界区内（`:262-266`），close 的 shutdown 也在同锁内，因此"登记成功 ⇒ 调度器尚未 shutdown"是结构性成立，不依赖时序（`:249-250` 注释）。**这是本轮读码确认的一个容易漏掉的正确性点。**

### 2.2 强弃与 Dekker 握手

- 事务标志：`abandoned`（`:893`）、`liveEngine`（`:887`）、`forceAbandon()`（`:914-917`）、`forceCloseLiveEngine()`（`:936-948`，专用 daemon 线程，避免占 watchdog）。
- 阶段侧两查：
  - 建上下文**前**：`:504-512`（`abandoned` → `ENGINE_CLOSED`）；
  - 发布活引擎**后**：`:513-526`（`liveEngine.set(js)` 之后 `abandoned.get()`；命中则 `PhaseRejection`，`finally` 里 `compareAndSet(js,null)` + `js.close()`，`:556-559`）。

握手对：close 侧 `abandoned.set(true)` → `liveEngine.getAndSet(null)`；阶段侧 `liveEngine.set(js)` → `abandoned.get()`。两个原子量都是 volatile（`AtomicBoolean`/`AtomicReference`），JLS §17.4.5 规定"volatile 写 happens-before 其后的 volatile 读"，且 §17.4.4 规定所有同步动作（含 volatile 读/写）处于**全序**；因此"两侧都没看见对方"不可构造——最坏情况是阶段自建上下文后立刻看见 `abandoned` 并自行销毁。

### 2.3 结果线性化与收尾

- 结果与 `closed` 在同一临界区判定（`:297-306`）：close 先到 → 哪怕已算出 `Rendered` 也折 `ENGINE_CLOSED`；判定先到 → 结果在关闭前成立。
- `finally`（`:307-314`）：取消本事务 timer → 持锁清 `inFlight`（仅当仍是自己）→ `markCompleted()`。三条路径（成功/拒绝/取消）都会 countDown，close 不会漏等。
- 取消路径（`:286-289`）先 `forceAbandon()` 再重抛，保证宿主取消后槽位释放（设备用例 `theHostAbandoningAnInFlightTransactionFreesTheSingleFlightSlot` 钉住，用例 `:785-798`）。

### 2.4 上游依赖事实（决定 close 语义边界）

本轮 `gh api` 实取 `dokar3/quickjs-kt` v1.0.15 的 `quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt`：

- `close()`（源码 `:499` 起）：`closed.compareAndSet(false, true)` → 幂等提前返回；随后 `requestInterruptIfOpen()`，再取 `jobsMutex`、`jsMutex` 释放原生资源。→ 本实现的"强弃幂等""close 可卡在 `jsMutex`"两个前提成立。
- `evalAndAwait` catch 分支（源码 `:429`）：`if (isClosed) throw CancellationException("Already closed.")` → 本实现 `runPhase` 的 `CancellationException` 分支（`:563-581`）按"谁关的"分层（`hardExpired` → `HARD_DEADLINE_EXCEEDED`；否则 `ENGINE_CLOSED`）是**与上游事实对齐的**，不是猜测。
- `awaitEvaluateResult`（源码 `:583-595`）：内层 `do { executePendingJob } while (executed)` 全程持 `jsMutex`，内层不查中断/`isClosed` → issue #240 的机制仍在。
- 上游状态（本会话实取）：issue #240 `state=open`、`comments=0`、`updated_at == created_at == 2026-09-26T08:42:29Z`；`commits/main` HEAD 仍 `a081755b…`（2026-09-03）；最新 release 仍 `v1.0.15`（2026-09-03）。仓库 license `Apache-2.0`、155★、未归档、`pushed_at` 2026-10-30（活动在其它分支/提交，`QuickJs.jni.kt` 最后一次变更是 2026-08-06，**排水循环未修**）。

结论：#240 的"close 可能先于 `execute()` 返回、且该 execute 永不成功"只能靠 L3 进程级 `killProcess` 收口，**不能**由本类修好；这不是 KD-71 的残留，是 KD-66 的层（见 §4 R1）。

---

## 3. 设备与执行证据核验

### 3.1 批次 39 E-04（`docs/research/evidence/2026-11-01-batch-39/step04-data-suites.md`）

| 字段 | 值 | 核对 |
|---|---|---|
| verdict | PASS：12 套件全绿、计数相符、0 跳过、签名 0 | 文件 `:10` |
| 引擎套件 | `QuickJsVisualExecutionEngineInstrumentedTest` **OK (27 tests)** | `:46` |
| 设备 | emulator-5562 / AVD test_device / API 34 / x86_64 / PAGE_SIZE 4096 | `:15` |
| APK | core:data androidTest `5b8ba39c83b6…153c3` | `:16`；与 `build/device-track-batch39/apk-sha256.txt` 第 3 行同值 |
| head | `a0641bb7…`（现 HEAD `a3f367df` 的祖先；`git merge-base --is-ancestor a0641bb7 HEAD` = 真） | `:13` |
| 原始日志 | `build/device-track-batch39/suite-engine.log`、`suite-engine-logcat.txt` | `:18` |

原始日志逐项复算（本轮实跑）：

| 检查 | 命令 | 结果 |
|---|---|---|
| 总判词 | `tail build/device-track-batch39/suite-engine.log` | `OK (27 tests)`（`:384` 行附近），`adb-exit=0` |
| 用例数 | `grep -c "INSTRUMENTATION_STATUS: test="` | **54** = 27 开始 + 27 结束 |
| 通过数 | `grep -c "INSTRUMENTATION_STATUS_CODE: 0"` | **27** |
| 失败/跳过 | `grep -c "INSTRUMENTATION_STATUS_CODE: -"` | **0** |
| `closeDuringAnInFlightTransactionWaitsAndRejectsIt` | 日志 `:301` 开始、`:309` `STATUS_CODE 0` | 绿 |
| `concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome`（32 轮） | 日志 `:189` 开始、`:197` `STATUS_CODE 0` | 绿 |
| 崩溃签名 | `grep -n "FATAL EXCEPTION\|ANR in\|AndroidRuntime" suite-engine-logcat.txt` | 5 条全部是 `am instrument` 命令进程的启动/退出行（`:2-4`、`:15`、`:148`）；**被测应用进程 0 条** |

### 3.2 源快照绑定（本轮关键核验）

`build/device-track-batch39/src-sha-before.txt`（构建窗开窗快照，mtime 10:16:46；构建 `build.log` 10:17:16；`suite-engine.log` 10:53:30）与现树逐字节比对：

| 文件 | 快照 sha256 | 现树 sha256 | 判定 |
|---|---|---|---|
| `QuickJsVisualExecutionEngine.kt` | `7e6135be8e5d…6311a08b5`（`src-sha-before.txt:356`） | 同值 | **一致** |
| `QuickJsVisualExecutionEngineInstrumentedTest.kt` | `6597f20f5ea3…4462bf5022`（`:212`） | 同值 | **一致** |
| `QuickJsVisualExecutionEngineJvmTest.kt` | `8b23975f1710…4d0968e822e5`（`:581`） | 同值 | **一致** |

并且：`src-sha-after.txt`、`src-sha-final.txt` 三行与 before 同值；`build/device-track-batch39/src-snapshot.diff` 为 **0 行**（构建窗 `diff=0`）；`src-snapshot-postbuild.diff`（92 行）中 `grep -i QuickJsVisualExecutionEngine` 零命中（后置漂移不含本文三文件）。

补充事实（不影响绑定，但必须写明）：`git diff a0641bb7..HEAD -- <三文件>` 只有引擎 **+12 行**，内容是本批次 38 的 M-03 4MP 结算门（现树 `:418-421`）——即设备构建时工作树里的引擎 M 态就是这 12 行，提交 `ebfd3450` 原样入库；现树对 HEAD 零 diff（`git status --porcelain` 空、`git diff --stat HEAD` 空）。**因此"批次 39 设备 27 例绿"绑定的字节 = 现树字节。**

### 3.3 JVM 旁证（次级）

`core/data/build/test-results/testDebugUnitTest/TEST-…QuickJsVisualExecutionEngineJvmTest.xml`：mtime `2026-11-02 05:12:32 +0800`，`tests="3" skipped="0" failures="0" errors="0"`，三例含 `close landing between the closed precheck and registration still rejects the execution`（`:82-117` 的确定性红测缝用例）。

等级：`build/` 产物、`.gitignore` 忽略、非本轮执行 → **只作旁证**，不作关闭判据（与台账 `docs/known-defects.md:2748` 的既有口径一致）。

---

## 4. 残余风险与语义边界（关闭前必须一并登记，否则会被"关后重开"）

| # | 残余 | 依据 | 归属 | 对关闭的影响 |
|---|---|---|---|---|
| R1 | **#240 下 close 可先于 execute 返回，且该 execute 永不成功**；强弃 daemon 线程在 `jsMutex` 上泄漏（`Thread` + 原生 runtime），JS 可能仍在跑 | 本类 KDoc `:102-126`；上游 `QuickJs.jni.kt:499-530`、`:583-595` | KD-66（进程级 `killProcess` 层） | 不阻塞 KD-71；但关闭文案**不得**写"close 返回即无 JS 在执行" |
| R2 | **有界等待不保证 `execute()` 调用方返回**：`measurer.measure` 或宿主侧 JNI 若不可取消地卡住超过 `hardDeadline+500ms`，close 返回（`:333`），execute 调用方仍挂；引擎已终态，后续 execute 全 `ENGINE_CLOSED`，无成功泄漏 | `:394-409`（measurer 只兜 `Throwable`，不含"永不返回"）、`:333` | 宿主设施（M-04 邻域） | 不阻塞；关闭文案写明"上界只约束 close，不约束坏掉的宿主设施" |
| R3 | **close 返回后仍可能交付 close 前已判定的 `Rendered`**：线性化判定（`:297-306`）先于 `finally` 清 `inFlight`，close 若在 `inFlight=null` 后进锁会立即返回，而调用方随后才拿到结果 | 读码；压测用例显式接受"close 调用后仍可 Rendered"（用例 `:890-899`） | 设计语义（线性化） | 不阻塞；判据里必须写明这是**已接受语义**，不是缺陷 |
| R4 | `close()` 若在事务自身的调用线程/协程里调用 → 自等待（有上界 `hard+500ms`） | `:317-334` + `:44-45` 端口注释"不得在 Binder/主线程调用" | 调用约定 | 不阻塞 |
| R5 | 生产接线目前**不可达**该竞态：`VisualJsService` 每请求新建引擎（`VisualJsService.kt:416`），`finally { engine.close() }`（`:437`）在 `runBlocking { engine.execute(...) }`（`:427`）返回之后；服务层硬期限走 `killSelf`（`:518-533`），不调 `engine.close()` | 读码 | 现状事实 | 说明严重度仍 low；但 4E.2 迁移到 `:visual_js` 后若改为复用实例，此不变式立刻变成硬门 |
| R6 | 测试缝在生产源码里（`internal`，同模块可写）；只影响登记时序，不泄露能力 | `:208-209` | 测试设施 | 不阻塞；保持 `internal` + KDoc 声明 |
| R7 | **登记段内异常会漏清 `inFlight`**：`inFlight = transaction` 与 `watchdog.schedule(...)` 都在 try/finally **之外**（`:251-267`）；若 `schedule` 抛（如该点 OOM），`inFlight` 永不复位、异常穿透端口契约（"失败一律 Rejected"），后续 execute 永远 BUSY，close 要白等满上界才返回 | 读码 `:251-267`、`:307-314` | 实现边角（非 KD-71） | 不阻塞关闭（fail-closed，无"关闭后成功执行"）；建议后续把登记段移进 try 或用 `runCatching` 兜底并登记 Progress |

---

## 5. 关闭判据（设备证据要求，可直接执行）

> 原则：判据只允许加严，不允许把"新增用例必须重跑"降级为"沿用旧绿"。以下 G1–G5 是关闭的必要条件，G6 是证据落盘格式。

### G1 源绑定（必要）

1. 证据必须给出 `src-sha-before.txt` 与 `src-sha-final.txt`，且其中三文件（引擎 + 两个测试）的 sha256 与**关闭时树上的文件**逐字节一致（`sha256sum` 实跑）。
2. 证据窗内 `git status --porcelain -- <三文件>` 为空；若期间有任何改动，判据作废，重跑。
3. `src-snapshot.diff` 必须为空（构建窗 `diff=0`）；`src-snapshot-postbuild.diff` 不得包含上述三文件。

### G2 套件与逐例（必要）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=<serial>
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

- 判词必须 `OK (N tests)`，`N == grep -c '@Test' <该测试文件>`（现为 **27**）；`STATUS_CODE: 0` 计数 == N；`STATUS_CODE: -` 计数 == 0；不得用 `connectedAndroidTest` 合并口径代替。
- 两条 KD-71 用例必须逐条可见且 `STATUS_CODE 0`：`closeDuringAnInFlightTransactionWaitsAndRejectsIt`、`concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome`（32 轮）。
- 只允许"观察窗口常量"随设备变慢调整（现为 200ms，用例 `:859-863`）；**不得**放宽"execute 必须 `ENGINE_CLOSED`"与"close 后不得再执行"的断言（与台账 Fix direction `docs/known-defects.md:2742` 同口径）。
- 同一 APK 建议连跑 **3 次**再判关闭（竞态证据是概率性的；批次 39 只有 1 轮，32 轮压测在**同一次进程内**完成）。3 次全绿才写"稳定绿"，单次写"单轮绿"。

### G3 环境与崩溃签名（必要）

- 记录 serial / AVD / API / ABI / page-size（批次 39：emulator-5562 / test_device / 34 / x86_64 / 4096）。
- core:data androidTest APK sha256 与安装包一致（批次 39：`5b8ba39c…`）。
- 覆盖整轮的 logcat：被测应用进程 `FATAL EXCEPTION` / `ANR in com.tingyun…` 为 **0**；`am instrument` 命令进程的 `AndroidRuntime` 行不算签名（批次 39 实测 5 条，均为命令进程）。

### G4 变异/回退控制（强烈建议，不必设备）

目的：证明两条用例对"修复"敏感，而不是恒绿。做法二选一（JVM 侧即可，零原生库）：

- 把 `closed` 判定移出登记临界区（还原旧窗口）→ `QuickJsVisualExecutionEngineJvmTest` 的 `close landing between the closed precheck and registration still rejects the execution` 必须红；
- 或删掉 `liveEngine.set(js)` 之后的 `abandoned.get()` 复查 → 设备用例 `closeDuringAnInFlightTransactionWaitsAndRejectsIt` 在 `hardDeadline` 足够大时必须拿到 `Rendered`（红）。

必须记录：变异 diff、红输出、还原后复跑绿。**未做 G4 不阻塞关闭**（reopen condition 只要求绿），但关闭文案应写明"红向敏感性未实证"。

### G5 关闭后的守恒（必要）

- 关闭后再次 `execute` 必须 `ENGINE_CLOSED`（`closingIsIdempotentAndExecuteAfterCloseIsRejected`，用例 `:801-813`）；
- `close()` 幂等 + 有界（`@After` 不挂死：批次 39 该套件 instrumentation 自报 `Time: 12.962`，27 例含 #240 观察例与 32 轮压测均在 13s 内收尾——若 `@After` 的 `close()` 被卡满上界，单例会多出 2s（默认 hard 1500+500）或 10.5s（`closeDuring` 夹具 hard 10000+500））。

### G6 证据落盘格式（必要）

一份 `docs/research/evidence/<batch>/stepXX-kd71-engine.md`：`evidence-id`、`verdict`、`executed-at`、`head`、`worktree`、`device-serial/avd/api/abi/page-size`、`apk sha256`、`exit-code`、`raw-log` 路径、`record-ref`；正文含 G2 的逐例表与 G1 的三个哈希。台账 `docs/known-defects.md` KD-71 追加 Progress 块并回链该文件。

### 本轮裁决建议

| 判据 | 现状 | 结论 |
|---|---|---|
| G1 | 批次 39 已满足（§3.2 三哈希与现树一致、构建窗 diff=0） | ✅ |
| G2 | 批次 39 已满足（27/27、逐例 0、0 失败） | ✅ |
| G3 | 批次 39 已满足（设备/API/APK/签名 0） | ✅ |
| G4 | 未做 | ◐（建议补，JVM 侧即可） |
| G5 | 批次 39 套件内含 `closingIsIdempotent…` 且整包 12.96s 收尾 | ✅ |

→ **建议：KD-71 可"条件式关闭"**——以批次 39 E-04 + §3.2 哈希为关闭证据写入台账；**条件**是批次 40 设备轨复跑同套件并落 G6 格式证据（批次 40 item 51）。若批次 40 期间三文件被改（含 M-03 之外的任何 hunks），本证据失效，须重跑后再关。

---

## 6. 若回归，最小修复形态

> 现树已修（§2），本节是"有人把登记拆回非原子/删掉双查/删掉等待"时的最小修复，不是新方案。目标是 3 处、约 30 行，且每条都能被现成用例钉住。

### 6.1 不变式（修复后必须全部成立）

| # | 不变式 | 违反后的可观察失败 |
|---|---|---|
| I1 | `closed` 判定与 `inFlight` 登记在**同一临界区** | close 落在预检与登记之间 → 关闭后仍渲染成功（KD-71 原始症状） |
| I2 | `inFlight != null` ⇒ `close()` 必取到该事务 | close 不等待、不强弃（旧实现 measure 期 `liveEngine=null` 的形状） |
| I3 | `abandoned` 与 `liveEngine` 构成 Dekker 握手，阶段**建上下文前 + 发布后**各查一次 | close 时活引擎为 null → 事务随后照常 render |
| I4 | 最终结果与 `closed` 同临界区判定 | close 先到仍交付 `Rendered` |
| I5 | `close()` 有界等待（`hardDeadline + 500ms`），超时 accept-the-leak | `@After`/Binder 线程被 #240 永久挂住 |

### 6.2 最小改动（与现树等价）

```kotlin
// ① 登记：判定 + 登记 + arm timer 同一临界区（唯一不可省的锁）
synchronized(stateLock) {
    if (closed) return rejected(ENGINE_CLOSED, "engine is closed")
    if (inFlight != null) return rejected(BUSY, "another visual transaction is already in flight")
    inFlight = transaction
    transaction.hardTimer = watchdog.schedule({ transaction.onHardDeadline() }, hardDeadlineMillis, MILLISECONDS)
}

// ② 阶段双查（前查可省去建线程；后查与 close 的 getAndSet 构成握手，不可省）
if (transaction.abandoned.get()) throw PhaseRejection(rejected(ENGINE_CLOSED, "..."))
val js = QuickJs.create(jobDispatcher)
transaction.liveEngine.set(js)
try {
    if (transaction.abandoned.get()) throw PhaseRejection(rejected(ENGINE_CLOSED, "..."))
    // ... bootstrap / evaluate
} finally {
    transaction.liveEngine.compareAndSet(js, null)
    runCatching { js.close() }
}

// ③ 结果线性化 + 收尾
return synchronized(stateLock) { if (closed) rejected(ENGINE_CLOSED, "...") else result }
// finally: hardTimer?.cancel(false); synchronized(stateLock) { if (inFlight === transaction) inFlight = null }; markCompleted()

// ④ close：同临界区置终态 + 取事务；出锁强弃 + 有界等待
fun close() {
    val draining = synchronized(stateLock) {
        val t = inFlight
        if (!closed) { closed = true; watchdog.shutdownNow() }
        t
    } ?: return
    draining.forceAbandon()
    draining.awaitCompletion(hardDeadlineMillis + HOST_AWAIT_GRACE_MILLIS)
}
```

### 6.3 可以省与不能省

- **可省**：`execute` 开头的 `isClosed()` 预检（`:219-221`）——只是 fail-fast，正确性由 ① 承担；`beforeRegistrationHookForTest` 之外的任何额外同步。
- **不可省**：① 的"判定与登记同锁"、② 的"发布后复查"、③ 的"结果线性化"、④ 的"有界等待"。删任一条都会让 §6.1 的对应失败重现，且现成用例会红。
- **不要**引入：自旋重试、`Thread.sleep` 补偿、给 `close()` 加无限等待、把 `ENGINE_CLOSED` 换成新枚举（端口在 core/domain 热区，见 `:52-62` 的 M-04 先例）。

### 6.4 为什么不自造更复杂的机制

- 这是 JDK `ExecutorService.shutdownNow()` + `awaitTermination(timeout)` 的标准形状：`shutdownNow` "attempts to stop all actively executing tasks … does not wait … no guarantees beyond best-effort"，`awaitTermination` 超时返回 `false` 表示"没等到终止"，正对应本实现的"有界等待 + accept-the-leak"（官方 Javadoc，见 §8）。引入第三方并发库不消灭任何本缺陷的具体失败（用户要求：新增必须指认消灭的失败），故不引入依赖。

---

## 7. 未验证项

1. **本轮未运行任何 Gradle / 设备命令**：27 例绿、JVM 3 例绿均为"仓内既有产物"复核，不是本轮执行。
2. **G4 红向敏感性未实证**：`beforeRegistrationHookForTest` 用例"修复前会红"仍是代码路径推演（台账 `docs/known-defects.md:2748` 同一口径）；批次 39 只跑了绿向。
3. **真机/arm64/16KB 口径未覆盖**：批次 39 全部为 x86_64 / API 34 模拟器；KD-71 是纯 Kotlin 层竞态，ABI 不改变不变式，但证据口径须如实写"模拟器"。
4. **KD-71 在 `:visual_js` 隔离进程 + AIDL 客户端链路上的等价性未验**：`VisualJsClientEngine` 侧有自己的 generation/终止语义；本复核只覆盖 `core:data` 引擎类。
5. **M-03（`:418-421`）的 4MP 分支无专门用例**：引擎 27 例中 `grep -n "4 MP\|layoutBudget\|transactionBudgetDiagnostic"` 零命中；M-03 在 measure 之后、render 之前结算，不触碰锁面，推理上无交互，但"写了门没有超限夹具"是测试缺口（归 M-03 项，不在 KD-71）。

---

## 8. 来源

| 类型 | 来源 | 用途 |
|---|---|---|
| 仓库代码 | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt:149-166,187-193,208-209,247,251-267,273-314,317-334,342-348,488-598,882-948` | 修复形态逐处行号 |
| 仓库代码 | `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:37-38,44-45` | 端口关闭语义 |
| 仓库代码 | `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:416,427,437,518-533` | 生产接线（每请求新引擎 / close 在 execute 后 / watchdog 走 killSelf） |
| 测试 | `core/data/src/androidTest/kotlin/…/QuickJsVisualExecutionEngineInstrumentedTest.kt:130-160,817-887,889-944`；`core/data/src/test/kotlin/…/QuickJsVisualExecutionEngineJvmTest.kt:66-117` | 三条 KD-71 用例与红测缝 |
| 设备证据 | `docs/research/evidence/2026-11-01-batch-39/step04-data-suites.md:10,13,15-16,18,46` | 27 例绿、设备、APK、head |
| 原始日志 | `build/device-track-batch39/suite-engine.log:189-197,301-309,384`；`suite-engine-logcat.txt:2-4,15,148`；`src-sha-before.txt:212,356,581`；`src-sha-final.txt`；`src-snapshot.diff`（0 行）；`apk-sha256.txt:3` | 逐例、哈希、签名 |
| 台账 | `docs/known-defects.md:2727-2742,2748,2754`（KD-71 登记/Progress/Reopen/Fix direction）、`docs/REFACTOR-MASTER-LINE.md:11-14,22,82` | 既有裁定与口径 |
| 上游 | https://github.com/dokar3/quickjs-kt/issues/240（`gh api` 实取：open / comments=0 / updated_at 2026-09-26） | #240 仍未修 |
| 上游源码 | `dokar3/quickjs-kt@v1.0.15`：`quickjs/src/jniMain/kotlin/com/dokar/quickjs/QuickJs.jni.kt`（`close()` :499-530、`evalAndAwait` :405-435、`awaitEvaluateResult` :578-600），经 `gh api contents?ref=v1.0.15` 实取 | close 幂等/先中断后抢锁、`Already closed` 映射、排水循环持锁 |
| 上游元数据 | `gh api repos/dokar3/quickjs-kt`（license Apache-2.0、155★、未归档、`pushed_at` 2026-10-30）、`releases/latest`（v1.0.15, 2026-09-03）、`commits/main`（`a081755b…`, 2026-09-03） | 维护度/升级路径 |
| 官方规范 | JLS SE 21 §17.4.2/§17.4.4/§17.4.5（volatile 写 happens-before 其后读；同步动作全序）https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html | Dekker 握手正确性 |
| 官方文档 | `java.util.concurrent.ExecutorService`（`shutdownNow` best-effort / `awaitTermination` 超时语义）https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html | 有界等待与 accept-the-leak 的既有先例 |
