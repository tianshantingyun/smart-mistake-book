# 批次 39 收尾轮基线核对（2026-11-01）

> 核对对象：`.zcode/plans/plan-2026-11-01-batch-39-tail.md` §0（现状）/ §1（10 项任务清单），共 28 行，本会话全文实读。
> 核对方式：**只读**——git 读命令、`grep`、`ls`、`adb devices`/`getprop`；**零 Gradle、零设备写、零 git 写**。
> 本文件是新一轮基线的落盘，不含任何"完成/通过"声明；每条结论后附本会话实跑命令或实读 `path:line`。

| 字段 | 值 |
|---|---|
| 核对时点 | 2026-11-01 22:2x–22:4x +08:00（本会话） |
| HEAD（核对开始） | `cd96a08e917497fa204f8d6c9a88134177b94283` |
| HEAD（核对结束） | `f10a1e4e081bbb257b2b8c9f66bb55fd2cc4730b`（**核对过程中前移，见 §7-D1**） |
| origin/main（本地引用，核对结束） | `f10a1e4e081bbb257b2b8c9f66bb55fd2cc4730b` |
| 工作树 | `git status --porcelain \| wc -l` = **484**（70 M + 414 ??），暂存区 0 |
| DB schema | `STUDY_DATABASE_VERSION = 71`（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139`） |

---

## 0. 逐项结论一览

| # | 核对项（ask） | 结论 | 锚点 |
|---|---|---|---|
| 一 | HEAD / origin/main 本地引用、工作树计数、DB schema=71 | **DRIFT**：schema 71 ✓；HEAD 已从计划 §0 的 `cd96a08e` 前移到 `f10a1e4e`；工作树 484 条不干净 | §1 |
| 二 | X-03 修复在树核对 + `:core:data:compileDebugAndroidTestKotlin` 主会话验证 | 修复**在树且已入库**（`assertNotEquals` `:224-228`、`deriveProgramId` 复算断言 `:229-240`）；编译验证**有主会话日志**（`build/main-session-gate-batch39/x03-fix.log`，0 个 `e:` 行），但该日志在 `build/`（不入库），`docs/research/evidence/` 无对应 step 文件 | §2 |
| 三 | isolation 现树计数 + 三窗红原文 + T4 打点清单 | 现树 **15** ✓；三窗 **6/7/5** 红、kill **10/10/6** 与证据原文一致；T4 清单 6 处写点 + 访问器 + 类（行号见 §3.3） | §3 |
| 四 | KD-76 计时 seam 行号 / `ConnectStageTimestamps` 字段 / androidTest 引用 | seam **已落码并已入库**；androidTest **0 引用** ✓ 与计划 §0 一致 | §4 |
| 五 | 「13 个既有套件」现树计数 | **口径不符**：计划 §0/§1 与仓内文档均无「13 个既有套件」集合；批次 39 不回退底册实为 **24 套件**。已给出全部 24 套件现树计数（可覆盖任何 13 子集） | §5 |
| 六 | 设备在线状态（只读） | 4 台在线；目标 `emulator-5562`（API 34 / x86_64）在线 | §6 |

---

## 1. 一、HEAD、工作树、DB schema

### 1.1 refs（本会话实跑）

```text
$ git rev-parse HEAD
cd96a08e917497fa204f8d6c9a88134177b94283          # 核对开始
f10a1e4e081bbb257b2b8c9f66bb55fd2cc4730b          # 核对结束（同一会话内前移）
$ git rev-parse origin/main
cd96a08e…  → f10a1e4e081bbb257b2b8c9f66bb55fd2cc4730b
$ git log --oneline -4
f10a1e4e fix(4e.2): X-03 A 类夹具缺陷——断言改两套 id 空间（链身份≠持久行身份 assertNotEquals）
         + 持久行 id 必须由内容六字段派生（deriveProgramId 复算）；:core:data androidTest 编译 0 错
cd96a08e feat(kb): 批次31A——v25/v26 采纳内容 + 终版文书（v27 回滚至 v26）
b22c366a docs(4e.2): 批次39 记录补 10.1 merge 尝试与 revert 裁定
bf81d8f6 Revert "Merge remote-tracking branch 'origin/main'"
```

- 计划 §0 的另外两条历史断言成立：`b22c366a`（批次 39 交付）与 `bf81d8f6`（merge PR#12 revert）均在 `git log` 中。
- `f10a1e4e` 是**核对期间由他会话产生**的提交（提交时间 `Sun Nov 1 22:34:06 2026 +0800`，`git show -s` 实读），其内容 = X-03 修复 + 21 行改动（`git show --stat` = `1 file changed, 19 insertions(+), 2 deletions(-)`）。推送记录见 `build/main-session-gate-batch39/push-x03.log`：`cd96a08e..f10a1e4e  main -> main`。

### 1.2 工作树计数（本会话实跑）

```text
$ git status --porcelain | wc -l          → 484
$ git status --porcelain | awk '{print $1}' | sort | uniq -c
    414 ??
     70 M
$ git diff --cached --name-only | wc -l   → 0（暂存区空）
$ git worktree list                        → 6 个（主工作树 + 5 个副工作树/外部路径）
```

副工作树：`.worktrees/kernel-readiness`（audit/kernel-readiness `de1ff0c7`）、`~/.codex/worktrees/plans-publication`（`32026050`）、`~/.codex/worktrees/ui-green-pages`（`4baa77b8`）、`~/.codex/worktrees/verify-visible-fixes`（`a15aebb7` detached）、`D:/aud11-measure`（`a15aebb7` detached）、`.worktrees/kb-bench`（`0486a532` detached）。

**未跟踪但已参与过设备执行的 androidTest 套件 4 个**（`git status --porcelain | grep '^??'` 实跑；`git log -- <path>` 空 = 从未入库）：

| 文件 | 现树 `@Test` | 批次 39 设备读数（E-03） |
|---|---|---|
| `app/src/androidTest/.../visualjs/VisualJsClientFrameQueueDeviceInstrumentedTest.kt` | 1 | OK (1) |
| `app/src/androidTest/.../visualjs/VisualJsDeathUnconfirmedFailClosedInstrumentedTest.kt` | 1 | OK (1) |
| `app/src/androidTest/.../visualjs/VisualJsReconnectBudgetInstrumentedTest.kt` | 1 | OK (1) |
| `core/database/src/androidTest/.../TutorVisualSavedProblemRetentionInstrumentedTest.kt` | 2 | 批 39 未执行（V-03/V-04 轨，00-index §5.5） |

即：批次 38/39 的 3 个 app 设备套件在现树**仍是 `??`**（与批复 38 记录 `:124`"三件新增 app androidTest 套件…?? 未跟踪"同状态，未入库）。

### 1.3 DB schema

```text
$ grep -n "STUDY_DATABASE_VERSION" core/database/.../StudyDatabase.kt
139:internal const val STUDY_DATABASE_VERSION = 71
$ ls core/database/schemas/*/ | tail -5   → 7.json / 70.json / 71.json / 8.json / 9.json（含 71.json）
```

→ **71 ✓**，与计划 §2 硬约束一致。

---

## 2. 二、X-03 修复在树核对

### 2.1 断言位（实读，行号为现树 `f10a1e4e`）

文件：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt`（723 行，`wc -l`）。

| 位 | 行号 | 内容 |
|---|---|---|
| 夹具 KDoc（修正说明） | `:220-223` | "链身份与持久行身份是**两套派生空间**……原断言把两者划等号结构性不可能成立" |
| **①`assertNotEquals`** | `:224-228` | `链身份与持久行身份不得划等号（两套派生空间）`，参数 `chainProgramId` vs `programRow.programId` |
| **②`deriveProgramId` 复算** | `:229-240` | `assertEquals(持久 program 行 id 必须由内容六字段派生（无新源码入口）, TutorVisualIdentity.deriveProgramId(sourceJs, initialStateJson, basisFingerprint, apiVersion, sdkVersion, role), programRow.programId)` |
| ①档判定 | `:251-262` | `StrictOfflineVisualRelayout.decide(...)` → `RelayoutAllowed`，`allowed.programId == persisted.programId`、`allowed.sourceJs == persisted.sourceJs` |
| 0 派发 witness（三处） | `:333-337` / `:338-342` / `:343-353` | ①`readParentDispatchCount(...) == 0`；②operation 行 `status=SUCCEEDED`、`reserved_remote_dispatches=0`、`used_remote_dispatches=0`；③2 行 attempt 均 `method=JS`、`status=SUCCEEDED`、`reservation_id IS NULL` |

**静态一致性核对（只读，非编译）**：`TutorVisualIdentity.deriveProgramId` 形参为 `(sourceJs: String, initialStateJson: String, basisFingerprint: String, apiVersion: Int, sdkVersion: String, role: String)`（`core/database/.../TutorVisualIdentity.kt:41-48`）；`readTutorVisualProgram` 返回 `TutorVisualProgramRecord`（`core/database/.../port/TutorVisualPort.kt:80`），其字段含 `role: String :309`、`sourceJs :311`、`initialStateJson :312`、`apiVersion: Int :314`、`sdkVersion: String :315`、`basisFingerprint: String :317`（`core/database/.../TutorVisualCommit.kt:306-320`）→ 调用实参与形参类型逐一对应。

### 2.2 `:core:data:compileDebugAndroidTestKotlin` 是否已被主会话验证

**是，有日志**——但日志在 `build/`（gitignored），`docs/research/evidence/2026-11-01-batch-39/` 内**没有**对应 step 文件（该目录 10 个 `.md`，最新 mtime 15:12；`grep -rn "compileDebugAndroidTestKotlin" docs/research/evidence/2026-11-01-batch-39/` 仅命中 step01 的**修复前**构建）。

日志实读：`build/main-session-gate-batch39/x03-fix.log`（3349 B，mtime `2026-11-01 22:28:21`）：

```text
45: > Task :core:data:compileDebugAndroidTestKotlin            ← 本任务 executed（非 UP-TO-DATE）
46-48: w: ExportedSchemaTestDatabase.kt:37 / SelfReportedReviewInstrumentedTest.kt:95,127（3 条 warning）
50: BUILD SUCCESSFUL in 38s
51: 28 actionable tasks: 1 executed, 27 up-to-date
```

```text
$ grep -c "^e:" build/main-session-gate-batch39/x03-fix.log   → 0
```

时点关系：被改文件 mtime `2026-11-01 22:27:36` < 日志 mtime `22:28:21` < 提交 `22:34:06`；现树该文件 `git diff` 为空（内容 = 提交内容），故该日志编译的就是现在树上的这一版。

**口径限定**：本会话**未运行 Gradle**（纪律禁止），只引用主会话日志；该日志未入 `docs/` 证据目录，未来若需"证据在仓内"须由主会话补落盘。

### 2.3 设备复跑（计划任务 1）

**未执行**（本会话零设备写、零 `am instrument`）。计划判据 `OK (1 test)` 仍待设备轨；现树状态 = 修复在树 + 编译绿 + **设备红窗（0/1，E-02 §5 / E-06 §1）仍未刷新**。

---

## 3. 三、isolation 套件计数、三窗红原文、T4 打点清单

### 3.1 现树计数（应 15）

```text
$ grep -c "@Test" app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt
15
$ wc -l <同文件>
1219
```

逐方法行号（`grep -n "@Test" -A1`）：`:123` workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater、`:180` anUnattributedEngineFailureKeepsItsPrefixAcrossTheAidlWire、`:217` aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp、`:245` theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState、`:293` theClientRebuildsAtMostOnceAfterConfirmedAbandons、`:328` aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram、`:343` theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy、`:423` anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse、`:479` aHungWorkerReleasesTheCallerWithinHardPlus500ms、`:551` aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot、`:599` anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead、`:629` aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight、`:671` anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled、`:705` aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching、`:748` aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds。

→ **15 ✓**（与计划 §0「15/15」口径一致；批次 36 `P-12③` 落地后由 14→15，见 `git log -1 --oneline` 该套件 `5b915bf3`）。

### 3.2 三窗红证据原文（批次 39 evidence 实录）

来源：`docs/research/evidence/2026-11-01-batch-39/step06-reruns-and-attribution.md`（E-06，本会话全文实读）。

判定总表原文（E-06 `:29`）：

> \| `VisualJsIsolationInstrumentedTest` \| 首跑 9/15 \| 复跑 8/15 \| 第三轮（沉降对照 loadavg 0.66→窗内 3.34）**10/15** \| **FAIL（三窗未绿，未达不回退门）**——§2 \|

归因节原文（E-06 `:33-37`）：

> **红形事实（三窗同根）**：
> - 每窗红例集合不同（高方差）：窗1 6 红 / 窗2 7 红（含 KD-66 门例） / 窗3 5 红；三窗红例并集 10 个不同方法，重叠仅 3——无稳定复现子集，非确定性缺陷形。
> - 红形单一：全部为"帧执行超 1500 ms 服务硬期限 → 服务端 watchdog 杀 worker → 宿主见 binder died/HARD_DEADLINE_EXCEEDED/status=-3（binder 层错误，非协议码——`VisualJsProtocol.kt:35-39` 协议面为 0..4）→ 同测试内后续断言级联"。
> - watchdog kill 计数：窗1=10、窗2=10、窗3=**6**（批 38 E-06 判例：全绿轮设计基数=5）。窗3 沉降后（窗前 loadavg 0.66）kill 回落至基数+1，但窗内仍攀升（窗后 3.34）……

首窗 6 红红例方法名与堆栈（`step03-app-suites-first-run.md:47-53`）亦在案，红形同族。

**与计划 §0 的一致性**：计划 §0「isolation 三窗 6/7/5 红（HARD_DEADLINE/binder died/-3）」= E-06 原文，**相符 ✓**。计划 §1 任务 2 判据（`OK (15 tests)` + kill ≤6 + 红例集合为空）尚无新证据，**未执行**。

### 3.3 T4 打点位置清单（现树行号，`f10a1e4e`）

文件：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt`（1427 行）。

**6 处打点行 / 5 个语义写点**（`readyAt` 两处成功出口）：

| # | 行号 | 字段 | 语义点 |
|---|---|---|---|
| 1 | `:670` | `bindRequestedAtMillis` | `connectLocked` 内 `bindService` 请求前一刻（阶段①起点） |
| 2 | `:1161` | `bindConnectedAtMillis` | `onServiceConnected` → `markConnected` 锁内、与 `connections.incrementAndGet()` 同刻 |
| 3 | `:821` | `readyAtMillis` | `awaitWorkerReady` 内 `isReady()` 首真出口 |
| 4 | `:840` | `readyAtMillis` | 预算到点瞬间翻转的出口（保 `readyAt==0 ⟺ 从未见到 ready`） |
| 5 | `:401` | `executeAcceptedAtMillis` | `runSession` `STATUS_ACCEPTED` 分支 |
| 6 | `:570` | `firstByteAtMillis` | `readResultPayload` 读循环首个成功 `read`（`firstByteStaged` 保护） |

**seam 本体**：

| 构件 | 行号 |
|---|---|
| `connectStageTimestampsForTest(generation: Long? = null): ConnectStageTimestamps?` | `:1288-1292` |
| `ConnectStageTimestamps` 类 | `:1400` |
| `started(): Boolean = bindRequestedAtMillis != 0L` | `:1426` |

**其余人为缝（既有，非 T4 新增）**，同文件：`connectForTest(): Boolean :1198`、`abandonForTest(): Boolean :1205`、`connectionCountForTest(): Int :1211`、`unavailableForTest(): Boolean :1218`、`readyTimeoutOverrideForTest: Long? :1227`（读点 `:845`）、`fuseDigestForTest(String) :1245`、`workerUidForTest(): Int? :1249`、`generationForTest(): Long? :1251`、`awaitConnectedForTest(Long): Boolean :1254`（另 `VisualJsFrameQueue.kt:144/146` 的 `pendingCountForTest`/`inFlightForTest`、`VisualContentMeasuring.kt:588` 的 `paintFlagsForTest`）。KDoc 自述「既有 9 个测试 seam 与既有用例零改动」（`:1286`）与本会话枚举相符（上列 9 个 + 新 1 个）。

**核对发现（供计划任务 3「T4-reverted 控制轮」用）**：

1. 计划 §1 任务 3 把控制轮开关表述为「`beforeRegistrationHookForTest`/打点开关回退版本」。实读：`beforeRegistrationHookForTest` 声明在 `core/data/src/main/kotlin/.../visual/QuickJsVisualExecutionEngine.kt:209`、调用点 `:247`，**isolation 套件对它 0 引用**（`grep -c beforeRegistrationHookForTest VisualJsIsolationInstrumentedTest.kt` = 0），唯一使用方是 JVM 用例 `core/data/src/test/.../QuickJsVisualExecutionEngineJvmTest.kt:87`。→ 该缝与该套件红形无接线，**不能作为控制轮的开/关量**。
2. isolation 套件实际使用的缝（`grep -c` 实跑）：`connectionCountForTest` 12、`connectForTest` 8、`unavailableForTest` 4、`awaitConnectedForTest` 3、`abandonForTest` 2、`fuseDigestForTest` 2、`generationForTest` 2、`readyTimeoutOverrideForTest` 1、`workerUidForTest` 1；`connectStageTimestampsForTest` **0**、`pendingCountForTest` 0、`inFlightForTest` 0。
3. 因此 T4 对隔离套件的唯一可能影响面 = `VisualJsClientEngine` 的 6 处 `@Volatile` 时间戳写点（宿主侧）；控制轮的有效 revert 对象是该 6 行 + 访问器 + 类（`:1284-1286` KDoc 已写明"删本函数 + 类 + 6 处打点行 = 完整回退"）。

---

## 4. 四、KD-76 计时 seam 与 `ConnectStageTimestamps`

### 4.1 现树状态

- seam 已落码且**已入库**：`git status --porcelain -- <VisualJsClientEngine.kt>` 无输出（clean）；`git log -S"ConnectStageTimestamps"` → `db1ce639`（批次 39「渲染器 9 例+N5/N6/N7 设备 4 例+恢复读侧 12 例+V-01④ 例+KD-76 计时 seam（生产零改动，150/−6）」）。批次 39 记录称其为"在飞 M"（record `:26`/`:100`），该表述**已过时**（落笔时点值）。
- JVM 半边：`app/src/test/kotlin/com/tingyun/smartmistakebook/visualjs/ConnectStageTimestampsTest.kt`，`grep -c "@Test"` = **2**，已入库（同 `db1ce639`）。

### 4.2 `ConnectStageTimestamps` 字段（实读 `:1400-1427`）

| 字段 | 行号 | `@Volatile` | 语义 |
|---|---|---|---|
| `bindRequestedAtMillis: Long = 0L` | `:1403` | 是 | 阶段①起点（bindService 前） |
| `bindConnectedAtMillis: Long = 0L` | `:1407` | 是 | 阶段①终点=②起点（CONNECTED 发布同刻） |
| `readyAtMillis: Long = 0L` | `:1411` | 是 | 阶段②终点=③起点（`isReady()` 首真，两出口） |
| `executeAcceptedAtMillis: Long = 0L` | `:1415` | 是 | 阶段③终点=④起点（`STATUS_ACCEPTED`） |
| `firstByteAtMillis: Long = 0L` | `:1419` | 是 | 阶段④终点（payload 首字节 read 成功） |
| `started(): Boolean` | `:1426` | — | `bindRequestedAtMillis != 0L` |

类 KDoc 的分段口径（`:1367-1375`）：S-bind / S-warmup / S-ready-gap / S-firstbyte；`:1379-1381` 声明"未写字段=0 即事实"与"最后一个请求覆盖写"。

### 4.3 androidTest 引用数

```text
$ grep -rn "connectStageTimestampsForTest" --include=*.kt app/src/androidTest | wc -l
0
$ grep -rln "connectStageTimestampsForTest\|ConnectStageTimestamps" --include=*.kt app/src core/data/src
app/src/main/.../visualjs/VisualJsClientEngine.kt
app/src/test/.../visualjs/ConnectStageTimestampsTest.kt
```

→ **androidTest 0 引用 ✓**（与计划 §0「androidTest 0 引用、出数 UNVERIFIED」一致；与 E-08 §1 一致）。设备校准用例（n≥10 冷启动轮 → p50/p95/max、29.5 s 链分段）**仍未落码**，计划任务 4/5 **未执行**（本会话零设备）。

---

## 5. 五、「13 个既有套件」现树计数

### 5.1 口径不符说明（先报事实）

- 计划 §0/§1（28 行全文实读）**未出现**任何套件清单，也没有「13」这个数。
- 仓内检索：`grep -rn "13 套件\|13 个套件\|13 个既有" --include=*.md docs/ .zcode/` → **0 命中**。
- 批次 39 设备轨的既有套件口径是 **24 套件**（app 7 + core:data 12 + core:database 5）：`step03-app-suites-first-run.md:8`（app 7）、`step04-data-suites.md:8`（data 12）、`step05-database-suites.md:8`（database 5）；索引 `00-index.md:50` 写「三·24 套件不回退」。
- 因此**「13 个」无法在现仓材料中映射到任何集合**。为不遗漏，下表给出**全部 24 套件**的现树 `@Test` 计数（任何 13 子集可直接取用）。若「13」另有所指（例如某批次的子集），需计划/主会话指名；本文件不猜。

### 5.2 现树计数（`grep -c "@Test"`，逐个实跑）

**app（7 套件，`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/`）**

| 套件 | 现树 @Test | 批 39 设备读数（E-03） | 版本状态 |
|---|---|---|---|
| `VisualJsAdvertiseThenExecuteInstrumentedTest`（A-09） | 3 | OK (3) | 已跟踪 |
| `VisualJsClientFrameQueueDeviceInstrumentedTest`（P-09） | 1 | OK (1) | **`??` 未跟踪** |
| `VisualJsDeathUnconfirmedFailClosedInstrumentedTest`（P-10） | 1 | OK (1) | **`??` 未跟踪** |
| `VisualJsReconnectBudgetInstrumentedTest`（P-04/P-05） | 1 | OK (1) | **`??` 未跟踪** |
| `VisualMeasureReplayPaintFlagsInstrumentedTest`（flags） | 2 | OK (2) | 已跟踪 |
| `VisualJsIsolationInstrumentedTest`（隔离） | 15 | 9/15 → 8/15 → 10/15（三窗红） | 已跟踪 |
| `V01VisualChainInstrumentedTest` | 4 | 3/4 → 复跑 4/4 | 已跟踪 |

**core:data（12 套件）**

| 套件 | 现树 @Test | 批 39 设备读数（E-04） |
|---|---|---|
| `BackupRestoreInstrumentedTest` | 34 | OK (34) |
| `BackupRowCountsCoverageInstrumentedTest` | 2 | OK (2) |
| `BackupSnapshotTrimmerInstrumentedTest` | 9 | OK (9) |
| `BackupVisualReferenceInstrumentedTest`（C4） | 8 | OK (8) |
| `TutorVisualCommitRepositoryInstrumentedTest` | 12 | OK (12) |
| `VisualDrawOpsParityInstrumentedTest` | 16 | OK (16) |
| `VisualDrawOpsPainterInstrumentedTest` | 24 | OK (24) |
| `VisualDrawOpsPdfPainterInstrumentedTest` | 16 | OK (16) |
| `FigureGenerationLocalAttemptOrdinalInstrumentedTest` | 1 | OK (1) |
| `FigureGenerationReconcilerInstrumentedTest` | 6 | OK (6) |
| `QuickJsVisualExecutionEngineInstrumentedTest` | 27 | OK (27) |
| `FigureDispatchBudgetBoundaryInstrumentedTest`（A-10 预算边界） | 3 | OK (3) |

**core:database（5 套件）**

| 套件 | 现树 @Test | 批 39 设备读数（E-05） |
|---|---|---|
| `TutorVisualStructureGatesInstrumentedTest` | 5 | OK (5) |
| `TutorVisualSnapshotMigration_69_70InstrumentedTest` | 4 | OK (4) |
| `TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest` | 3 | OK (3) |
| `TutorVisualCommitInstrumentedTest` | 12 | OK (12) |
| `FullMigrationMatrixInstrumentedTest` | 15 | OK (15)（复跑窗） |

### 5.3 求和自洽（不回退基线）

- 24 套件合计 = app 27 + data 158 + database 39 = **224 例**。
- data 半边 158 与 E-04「全绿 158 例」逐套件相加一致；database 半边 39 = 24 + matrix 15，与 E-05 一致；app 半边 27 = 3+1+1+1+2+15+4，与 E-03 一致。
- 批 39 设备总账 236 例的分解在现树可对上：224（24 套件）+ 6（`VisualSourceEvidenceDeviceVectorInstrumentedTest`，X-02）+ 4（`TutorVisualPreviewRendererInstrumentedTest`，N5/N6/N7+取消重抛）+ 1（`FigureCrossMethodFinalFigureDeviceVectorInstrumentedTest`，A-10 协调器级，commit `ebfd3450`）+ 1（`StrictOfflineRelayoutDeviceVectorInstrumentedTest`，X-03）= **236**；绿 220 = 236 − 15（isolation）− 1（X-03）。
- 结论：**现树计数与批次 39 设备底册逐套件相符，未见回退**（这是计数核对，不是设备复跑；本会话未跑任何套件）。

---

## 6. 六、设备在线状态（只读）

```text
$ adb devices -l
emulator-5562   device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa
emulator-5566   device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa
emulator-5584   device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa
emulator-5596   device product:sdk_phone_x86_64   model:Android_SDK_built_for_x86_64 device:generic_x86_64
```

| serial | API (`ro.build.version.sdk`) | ABI | 用途（批 39 evidence） |
|---|---|---|---|
| emulator-5562 | 34 | x86_64 | **本批目标设备**（E-00/E-01 全套件设备） |
| emulator-5566 | 34 | x86_64 | 他线在飞，禁碰（E-00） |
| emulator-5584 | 34 | x86_64 | 他线在飞，禁碰（E-00） |
| emulator-5596 | 23 | x86_64 | API 23 轨，本批未跑（E-00） |

只读核对，未 install/instrument/force-stop；设备负载（loadavg）本会话未测，故"低负载窗"是否满足计划任务 2 的前提**未知**。

---

## 7. 与计划 §0 不符处（逐条）

| # | 计划 §0 原文 | 现树事实 | 影响 |
|---|---|---|---|
| D1 | 「现状（HEAD `cd96a08e`，树门禁绿）」 | 核对结束时 HEAD = **`f10a1e4e`**（22:34:06 由他会话提交并已推 origin，`push-x03.log`）；`origin/main` 本地引用同步前移 | 任何引用 `cd96a08e` 的快照/哈希绑定表述需改绑 `f10a1e4e`；批次 39 evidence 绑定的 `a0641bb7`/`684adb6e` 更早 |
| D2 | 「树门禁绿」 | 工作树 **484 条未提交**（70 M + 414 ??），含 4 个 androidTest 套件未跟踪（§1.2） | "门禁绿"不等于"树干净"；提交前逐路径核对暂存区的要求仍在 |
| D3 | 「主会话已修 X-03 … `:core:data:compileDebugAndroidTestKotlin` 0 错」 | 修复在树且已入库；编译日志存在（`build/main-session-gate-batch39/x03-fix.log`，`^e:` = 0） | 结论成立；但**证据未落 `docs/research/evidence/`**，日志在 gitignored `build/` 内 → 若要"证据在仓内"须补落盘 |
| D4 | 「KD-76 校准：计时 seam 已落码 … androidTest 0 引用」 | seam 已落码 **且已入库**（`db1ce639`），不是"在飞 M"；androidTest 0 引用成立 | 无功能影响；批次 39 记录 `:26/:100` 的"在飞 M"为落笔时点值，已过时 |
| D5 | （无，但 ask 项五称「13 个既有套件」） | 仓内不存在 13 套件集合；既有底册 = **24 套件** | 需计划方指名 13 的构成；本文件按 24 全量给数 |
| D6 | 任务 3 表述「`beforeRegistrationHookForTest`/打点开关回退版本」 | `beforeRegistrationHookForTest` 在 isolation 套件 0 引用（只有 JVM 用例用）；控制轮有效 revert 对象是引擎 6 处 `@Volatile` 时间戳写点 | 若不改开关对象，控制轮**不构成**对 T4 的判别实验（测的不是被 revert 的东西），结论会被误读成"T4 无关" |

其余计划 §0 断言核对**相符**：批次 39 交付 `b22c366a` 在史 ✓、merge revert `bf81d8f6` 在史 ✓、isolation 三窗 6/7/5 红与 kill 10/10/6 与 E-06 原文逐字一致 ✓、androidTest 0 引用 ✓、D-04 生产装配未接（本次未重核，沿用 E-02/00-index §10.2 结论，**本会话未验**）。

---

## 8. 本会话未执行 / 未验证项（如实登记）

1. **零 Gradle**：`compileDebugAndroidTestKotlin` 结论来自引用主会话日志（§2.2），本会话未复跑；其余任何"编译绿/单测绿"均未在本会话产生。
2. **零设备写**：X-03 复跑（`OK (1 test)`）、isolation 15/15 低负载窗、T4 控制轮、KD-76 n≥10 校准、29.5 s 链分段——**全部未执行**，判据数字一律空缺。
3. **D-04 生产装配零调用方**：本会话未重核 `grep renderPreview/previewAssetId`，沿用 00-index §10.2。
4. **设备负载**：未测 loadavg，"低负载"前提是否成立未知。
5. **「13 个既有套件」的正确构成**：未找到，已按 24 全量替代并上报（§5.1 / §7-D5）。

---

## 9. 本会话实际执行的命令（可复跑清单）

```bash
git rev-parse HEAD; git rev-parse origin/main; git log --oneline -4
git status --porcelain | wc -l; git status --porcelain | awk '{print $1}' | sort | uniq -c
git diff --cached --name-only | wc -l; git worktree list
git show --stat --oneline f10a1e4e; git show -s --format="%H%n%an%n%ad%n%s" f10a1e4e
git log -S"ConnectStageTimestamps" --oneline -- app/src/main/kotlin/.../VisualJsClientEngine.kt
grep -n "STUDY_DATABASE_VERSION" core/database/src/main/kotlin/.../StudyDatabase.kt
grep -c "@Test" <24 个套件文件>            # §5.2 全表
grep -n "@Test" -A1 app/src/androidTest/kotlin/.../visualjs/VisualJsIsolationInstrumentedTest.kt
grep -n "ConnectStage\|connectStageTimestampsForTest\|bindRequestedAt\|bindConnectedAt\|readyAt\|executeAcceptedAt\|firstByteAt\|volatile" app/src/main/kotlin/.../VisualJsClientEngine.kt
grep -rn "connectStageTimestampsForTest" --include=*.kt app/src/androidTest | wc -l
grep -c "beforeRegistrationHookForTest" app/src/androidTest/kotlin/.../visualjs/VisualJsIsolationInstrumentedTest.kt
grep -c "^e:" build/main-session-gate-batch39/x03-fix.log
adb devices -l; adb -s <serial> shell "getprop ro.build.version.sdk; getprop ro.product.cpu.abi"
```

（`grep` 一律走仓内文件路径；未运行任何 `gradlew`、`am instrument`、`adb install`、git 写命令。）
