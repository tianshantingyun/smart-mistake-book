# E-B43-WS3-01：isolation 自证 witness、`-4` 根因、15 例实跑与 T4 控制构建

- 批次：43（2026-11-04/05），计划 §2 WS3 项 19–28；设计输入 `docs/research/2026-11-05-isolation-selfwitness.md`（R3）
- 设备：`emulator-5566`（AVD `audit_full_ui_forest_20261028`，API 34 / x86_64 / nproc=2 / PAGE_SIZE 4096），另 `emulator-5562`（4 vCPU）用于对照读数
- 产物目录（gitignored）：`build/ws3-batch43/`、`build/ws3-batch43/control/`
- **口径**：本文件只写本轮亲手执行/实读的事实；未执行项在 §10 单列。设备结论一律绑定 APK sha 与 witness 标签。

## 0. 产物身份（本轮实测）

| 产物 | sha256 | 说明 |
|---|---|---|
| app（正常构建，6 处 T4 打点） | `028eb07e42662a0ac460ec5a8066bb0ce54c3d8615549af3bb7db92962fea78f` | 191,766,832 B；L1 六 run 与 probe-02/03/04 用它 |
| app（T4 控制构建，0 处打点） | `119812ff5fa1452d163efa20b2671b0d5c194f062da783af2a7f64a3383c23e4` | 191,772,510 B；L2-T4-OFF 用它 |
| androidTest（witness 首版，设备内算 APK sha） | `b7e1a8b3416229ccb5abccfe8b6c6dd3b5a0cf3818b22118796eecd9c886d220` | 仅 probe-01（该轮进程死亡，无 witness 行） |
| androidTest（witness + 宿主 sha，`own_cpu` 修复前） | `8a98e58ec20448d06d8ba67532fb36eebdb956a7988a4efb3541b9e14b550c1f` | probe-02（5562）与 probe-03（5566）；从 5562 已安装包逐字节拉回 |
| androidTest（witness + 宿主 sha + `own_cpu` 修复） | `8a63b236b24719a96dec9c11d3fdf6a68a50b2b74fdf99022b094612839d8518` | probe-04、L1 六 run、L2-T4-OFF |
| 引擎源（正常） | `f0786242633f72a3f912fcd0038161949ae39b94bcdca52d38320839bf6f9209` | 控制构建后已按字节还原（`git status` 空，anchors=6） |
| 引擎源（控制构建时） | `8b53254dcbe06c9ccba94e0830f6f53b357156e148d0e8e7bb7f6378d9a10a3d` | 仅存在于 `t4-off.marker.json` 的绑定里 |

> **源/APK 绑定精度（2026-11-05 修复环订正）**：本文件的设备结论绑定的是 test APK `8a63b236…`（probe-04 起）；但**该 APK 不等于当前 witness/runner 源码**：
> - `IsolationWitness.kt` 未跟踪（`git status ??`，无基线可比），mtime `2026-11-04 02:57` 晚于最后一次设备跑（02:49）与 `probe-04.witness.jsonl`（02:21）；`run_isolation_pair.sh` mtime `02:58`，其 `git diff` 新增 `-e smb.witness.run` / `smb.witness.apk_sha256` / `run-as cat witness.jsonl` / `isolation_witness_check.py --require-executed` 路径（hunk ≈ +296..+350）。
> - 实际三份/六份 witness 的 `suite_start` 均为 `run_source=argument`、`sha256_source=argument`（手工 `am instrument`），`build/ws3-batch43/` 内**没有** runner 产出的 `manifest.txt` → **runner 路径未被演练**。
> 因此：witness 行可证“15 例执行过”，但不能说“当前 runner/witness 源码已被本次设备证据验证”；逐字节源快照须重建 APK 后重跑（本轮重建被并发编辑阻塞，见 §10）。

## 1. item 19：witness 落点（本轮落码）

| 层 | 落点 | 写什么 | 证明 |
|---|---|---|---|
| L0 规则 | `IsolationArms.kt:102-111` | `currentMethod`、`rule_begin` / `rule_end` | 该例被调度进入（与 `CODE:1` 独立） |
| L1 门 | `LowLoadProbe.kt:234-238`（`IsolationGate.require()` 内、`Assume` 之前） | `gate`：`verdict/reason/shell_gate/shell_reason/reused/reading` | `@Before` 真的进了门；为什么没通过 |
| L2 体内 | `IsolationWitness.kt:343-361`（`witnessBody`）+ 15 个用例体 `= witnessBody {` | `body_begin` / `body_end`（`outcome/wall_ms/cpu_delta_ms/threads`） | `@Before` 已过、体内确实执行 |
| L4 套件 | `VisualJsIsolationInstrumentedTest.kt:920-931`（`@BeforeClass`/`@AfterClass`） | `suite_start`（含 APK 身份）+ `canary`（写后读回）+ `suite_end` | 写入端可用、行数完整 |

- 文件：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/IsolationWitness.kt`（新，359 行）。输出 `targetContext.filesDir/isolation-witness/run-<stamp>/witness.jsonl`，JSONL、append-only、每行 `flush + fd.sync`，logcat 镜像 `SMB_ISOLATION_WITNESS: <json>`。
- **canary 实测生效**：probe-02/03/04 与 L1 六 run 的 `suite_end.canary_ok` 全为 `true`；`rows=77`（`suite_end` 自身不计入），`expected_rows=78` 来自 `-e smb.witness.expected_tests 15`。
- 15 个用例体的改动只有 `= runBlocking {` → `= witnessBody {`（15 行，断言/缩进/协程作用域零改动，`:172,229,266,294,342,377,392,472,528,600,648,678,720,754,797`）。
- **APK 摘要改由宿主注入**（`IsolationWitness.kt:69,305-317`）：最初设计在设备内对 190 MB `base.apk` 做流式 SHA-256。probe-01（5562）在 `@BeforeClass` 阶段整进程死亡，instrument 流只有 `Process crashed.`、**没有任何 witness 行**；logcat 的 ANR 原文是 `Reason: Timed out while trying to bind`（`probe-01.logcat:998-1000`），**不是**哈希线程的直接证据——所以"哈希导致 ANR"**不成立/未证**，只能说该轮连 suite_start 都没写成。改法的正当性改成代价与可控性：190 MB 流式摘要的耗时在慢设备上不可预期，而宿主侧 `sha256sum` 是确定成本。改为 `-e smb.witness.apk_sha256` 后 probe-02 起稳定；设备侧只读 `sourceDir/size/versionCode/lastUpdateTime`。
  - 这正是 W4 的现场：如果没有 canary/`WITNESS_UNAVAILABLE` 口径，"文件不存在"会被误读成"没执行"；本轮把它记为 `stream` 里无 `suite_start`，而不是"15 例未执行"。

## 2. item 19：检查器与判据（先红后绿）

- `tools/ci/isolation_witness_check.py`（新，533 行）：S1–S8 分类 + `UNDECIDABLE_NO_WITNESS` / `STREAM_INCOMPLETE` / `CONTRADICTION_PASS_WITHOUT_BODY`；`--require-executed`、`--require-floors`、`--allow-missing-witness`；logcat 镜像回退。
- `tools/ci/isolation_witness_expectations.json`（新）：15 条 `floor_ms`（合计 **44 450 ms**）+ `require_effects`/`forbid`（文档性，效应由用例断言与 `isolation_pair_report.py` 的 kill 不变量承担）。
- `tools/tests/test_isolation_witness_check.py`（新，20 例）：**先红后绿**——首次运行 4 红（stack 首帧是 `Assume.assumeTrue` 而非 `IsolationGate`；floor=0 的方法本不该红），修解析器为"保留全部帧、按任一帧含 `IsolationGate` 判层"后 **20 passed**。
- 组合回归：`python -I -m pytest tools/tests/test_isolation_witness_check.py tools/tests/test_isolation_pair_report.py tools/tests/test_t4_control_marker.py tools/tests/test_lowload_gate.py -q` → **59 passed in 0.41s**；全量 `python -I -m pytest tools/tests -q` → **1083 passed, 70709 subtests passed in 376.76s**（exit 0）。
- 编译：`:app:assembleStrictOfflineDebugAndroidTest` **BUILD SUCCESSFUL**（02:07，含 `own_cpu` 修复后的 `8a63b236…`）。` :app:compileLocalFirstDebugAndroidTestKotlin` **BLOCKED**（他线新增 `kotlinx-serialization-json-io:1.9.0`/`kotlinx-io*` 不在锁状态，见 §10）。

## 3. item 20：`-4` 根因（读数 + 修复）

### 3.1 三条候选的判定

| 候选 | 本轮读数 | 判定 |
|---|---|---|
| C1 进程级单次采样 + 缓存，15 例共用 | 每 run 15 行 gate：`reused=false` **1** 行、`reused=true` **14** 行；只有 1 行带 `reading` | **事实**（witness `gate_cache` 字段长期可核） |
| C2 `ownCpuMs > 0.80 * windowMs` 把全线程 CPU 当单核分数 | 5566 空闲窗（jitter p95 **2.62 ms**）：`own_cpu_ms=2260 / probe_window_ms=2299` = **0.98 核**（2 核机的 49%）→ 判 `own_cpu_busy`；5562 争用窗：8970/7938 = **1.13 核**（4 核机的 28%） | **事实**（同一读数里 jitter 绿、CPU 判定红，且远未"压满"整机） |
| C3 jitter 绝对阈值 30 ms 与模拟器地板不匹配 | 5566 空闲 p95 2.02–3.42 ms（6 run 全绿）；5562 争用 p95 78.9 ms（应红） | **未触发**：本轮没有出现"jitter 单独否决空闲机"的证据；**阈值零改动** |

### 3.2 修复（只改语义分母，分数 0.80 不动）

`LowLoadProbe.kt:144-148`：

```kotlin
val processors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
val cpuCapacityMs = windowMs * processors
...
ownCpuMs > OWN_CPU_LIMIT_FRACTION * cpuCapacityMs -> "own_cpu_busy"
```

判据从"占单核 80%"改为"占**整机容量** 80%"。原始意图（`:64` 的"本进程已被压满"）不变；外部负载的判定权仍在 `lowload_gate.py`（instrumentation 读不到 `/proc/stat`，`LowLoadProbe.kt:16-32`）。常数 `OWN_CPU_LIMIT_FRACTION = 0.80` 与 `ABSOLUTE_P95_LIMIT_MS = 30.0` **均未修改**。

### 3.3 修复前后的设备证据

| run | 设备 | 修复前/后 | reading | gate 判词 | 15 例 |
|---|---|---|---|---|---|
| probe-02 | 5562（4 核，la1≈3.3） | 前 | own 8970 / 窗 7938 = 1.13 核；jitter p95 78.9 | `UNGATED own_cpu_busy`（15/15 ENV_SKIP） | 0 执行 |
| probe-03 | 5566（2 核，空闲） | 前 | own 2260 / 窗 2299 = 0.98 核；jitter p95 2.62 | `UNGATED own_cpu_busy`（15/15 ENV_SKIP） | 0 执行 |
| probe-04 | 5566 | 后 | own 2360 / 窗 2277 = 1.04 核（整机 52%）；jitter p95 2.82 | `GATED` | **15/15 EXECUTED** |

probe-03 是决定性反证：**同一读数里 jitter 表明机器空闲，而旧公式仍判"压满"**。这不是"放宽门去迁就绿"，是把分母改回判据自己声明的语义。

## 4. item 22：15 例单臂实跑（witness 齐全）

probe-04（`smb.t4.arm OFF`，`smb.lowload.gate PASS`，标签 `probe-04`）：

```text
witness rows = 78（suite_start + canary + 15×(rule_begin,gate,body_begin,body_end,rule_end) + suite_end）
checker --require-executed --require-floors → exit 0, violations []
counts = {EXECUTED: 15}
total_body_wall_ms = 69,360 ≥ floor_sum 44,450
```

逐例 `wall_ms`（floor）：3287(1750)、1165(0)、1495(200)、3613(1400)、2637(0)、7822(7500)、5378(1400)、1589(0)、4183(1400)、1151(0)、1364(0)、3330(1400)、1655(0)、30059(29000)、632(400)。

> 与批次 42 的 820 ms / 44 450 ms 对照：820 ms 是 15 条"缓存判词"的时间戳跨度；本轮 15 个 body 窗合计 69.4 s，两者相差 84 倍——witness 把"零执行"与"真执行"在量级上分开了。

## 5. item 21：低负载窗前门（真实执行，判 INVALID）

- 快照：`test_processes=0 / runner_processes=0 / orphan_workers=0` → `ok=true`（先 `am force-stop` 了 5566 上遗留的 `localfirst.test` 进程）。
- 地板（`lowload_gate.py calibrate`，settle 120 s + 30×10 s）：`nproc=2`、`busy_p95=36.18%`、`la_ratio_p95=0.45`、`la_ratio_max=0.475`、`ctxt_p95=4901`、`wakeup_p95=14.30 ms`。
- 门（30×10 s，5 min）：**`verdict=INVALID`，`reasons=["la_ratio_p95","la_ratio_max"]`**
  - `la_ratio_p95 = 0.51 > 0.50`（地板 0.45 + 0.05）
  - `la_ratio_max = 0.545 > 0.30`（`LA_MAX_ABS` 绝对上限）
  - 同窗 `busy_p95=34.32 ≤ 39.18`、`wakeup_p95=12.97 ≤ 42.91`、`ctxt_p95=4520 ≤ 7352` 都过。
- **结论**：现有 AVD 的地板本身（`la_ratio_max=0.475`）就高于绝对上限 0.30，因此在这台设备上**不存在可判 PASS 的窗**。这是环境/门参数层面的阻塞，**不是代码通过**。本批**没有**为了让套件跑起来去改 `LA_MAX_ABS`；后面六 run 的 `smb.lowload.gate PASS` 是**合成判词**（本章的 INVALID 才是 shell 侧事实），所以报告里六 run 的 `window` 一律声明为 `INVALID`。
- witness 的事后证据（不是门）：每 run `total_body_wall_ms` 67.1–71.8 s、`cpu_delta_ms ≥ 620`，`cpu_delta/wall` 只记录不设阈值。

## 6. item 23：OFF/ON 同 APK 重复运行（环境漂移层，S4 第 1 层）

- 同一 app `028eb07e…` + 同一 test `8a63b236…`，`-e smb.t4.arm OFF|ON`（只切测试侧钩子），顺序 r1 OFF/ON、r2 ON/OFF、r3 OFF/ON；每 run 前 `am force-stop` + `logcat -c`，run 后拉 logcat 与 witness。
- **6/6 run：PASS=15、FAIL=0、SKIP=0，witness 15 EXECUTED、canary ok、violations []**。
- 配对报告（`isolation_pair_report.py`，`--off-arm-production-t4 test-side-only`，六 run 全部声明 `window=INVALID`）：
  - `verdict=UNCONVERGED`，`paired=false`，`reasons=["window_not_pass","window_invalid"]`，`missing_methods` 两臂均为空。
  - kill 不变量两臂 `structurally_ok=true`：must-kill 三名全 ≥1（`aHungWorker…` 3、`aSecondConcurrent…` 3、`theWatchdogKills…` 3），must-not-kill 命中 **0**，`theClientRebuildsOnce…` 6 次属设计外但非禁杀；watchdog 总数 15 vs 设计基数 5（偏差 +10 只记 WARN）。
  - **没有**输出 `PAIRED_RED_SET_IDENTICAL`——因为窗被判 INVALID，工具拒绝给配对结论（这是正确行为）。

## 7. item 24：T4 控制构建 + 安装 APK 比对（S4 第 2 层）

1. 源回退：`VisualJsClientEngine.kt` 的 6 处 `timestamps.*AtMillis = SystemClock.uptimeMillis()` 被替换为 `T4_REVERT_APPLIED` 注释（5 个语义写点，`readyAt` 两处成功出口）。
2. 构建 `:app:assembleStrictOfflineDebug` exit 0 → control APK `119812ff…`；构建日志附 `T4_REVERT_APPLIED`。
3. 装机 + 实跑 `L2-T4-OFF`（ON 臂）：`witness 78 行`、`checker exit 0`、PASS=15 / FAIL=0。
4. `t4_control_marker.py emit` + `verify`（树仍处回退态）：**`ok=true`、`arm=OFF_PRODUCTION_T4_REMOVED`、`anchor_count=0`、`identity_source=installed_apk`、artifact 与 installed 同为 `119812ff…`**。
5. 源还原：6 行按原样写回；`sha256` 回到 `f0786242…`，`git status` 空，`find_anchors` = 6。
6. 正常 APK 还原：**重建被并发编辑阻塞**——`:core:data:compileDebugKotlin` 在 `BundledKnowledgePackResources.kt:192`、`StrictStreamingJson.kt:12,48` 等报 `Unresolved reference`（mtime 02:37/02:39，属他线在飞的 KB 编辑，非本项改动）。改为从 `emulator-5562` 拉回安装包（sha 逐字节等于控制前构建 `028eb07e…`），装回 5566 后 emit+verify：**`ok=true`、`arm=ON_PRODUCTION_T4_PRESENT`、`anchor_count=6`、artifact==installed==`028eb07e…`**。

**新发现（本轮实读）**：ON 臂的测试侧时间戳读取**结构性为空**。`T4SeamProbeRule` 的 `finally` 在 `@After closeEverything()` **之后**执行，`connectStageTimestampsForTest()` 此时 `link == null` → 返回 null。实测：正常构建 ON 臂 `SMB_T4_HOOK: end` 4 条、其中 `no_timestamp_source` 4 条；控制构建也是 4/4。也就是说两臂在"时间戳"这一面不可区分，**可区分的是构建身份（marker 的 engine sha + APK sha + 6/0 anchors）**。本批不改这个钩子（改它要动 `@After` 清理顺序），登记为遗留。

## 8. item 25：红集逐方法比较

**执行态**（witness 分类，六 run 全部 `EXECUTED`）：

| method | L1-r1-OFF | L1-r1-ON | L1-r2-OFF | L1-r2-ON | L1-r3-OFF | L1-r3-ON |
|---|---|---|---|---|---|---|
| `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `anUnattributedEngineFailureKeepsItsPrefixAcrossTheAidlWire` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `theClientRebuildsAtMostOnceAfterConfirmedAbandons` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `aHungWorkerReleasesTheCallerWithinHardPlus500ms` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |
| `aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds` | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED | EXECUTED |

**红集**：两臂 × 3 轮 × 15 方法 = 90 格，`FAIL/ERROR = 0`，红集为空且两臂相同（描述性；因窗 INVALID，报告不把它升格为 `PAIRED_RED_SET_IDENTICAL`）。控制构建 `L2-T4-OFF` 同为 15/15 PASS → 层 2 红集也是空。

## 9. item 26：两层判定

| 层 | 判据 | 结果 |
|---|---|---|
| 第 1 层（环境/方法可复现） | 低负载窗 PASS + 同 APK OFF/ON k≥3 稳定 | **未成立**：shell 门 `INVALID`（`la_ratio_p95`/`la_ratio_max`），报告 `UNCONVERGED`。红集在"未认证窗"内两臂一致，只能描述，不能当结论 |
| 第 2 层（T4 因果） | 两臂构建身份可区分 + 控制构建 marker verify | 身份面**成立**（`028eb07e` vs `119812ff`，anchors 6 vs 0，emit/verify 均 ok）；但第 1 层未成立，且 k 未在认证窗内积累 |

**裁定：不写因果。** 不得写"T4 排除"、不得写"isolation 通过"、不得把 `OK (15 tests)` 读成 15 例通过——本轮能写的是：**15 例在未认证窗内真实执行且全绿，witness 齐全；窗本身被判 INVALID；T4 控制构建与安装身份可核**。

## 10. 未验证 / 阻塞

| 项 | 状态 | 说明 |
|---|---|---|
| 认证低负载窗（layer 1 PASS） | **BLOCKED（环境/门参数）** | 5566 地板 `la_ratio_max=0.475` > `LA_MAX_ABS=0.30`；改门属行为变更，未做。runbook：换一台空闲 4 核 AVD 重标地板，或由主会话裁定 `LA_MAX_ABS` 是否按 `nproc`/地板伸缩 |
| `:app:compileLocalFirstDebugAndroidTestKotlin` | **BLOCKED（他线）** | 新增 `kotlinx-serialization-json-io`/`kotlinx-io-*` 不在锁状态（KD-72 域） |
| 正常 app APK 重建 | **BLOCKED（他线）** | `core/data/.../knowledge/*` 正在被并发编辑，`compileDebugKotlin` 失败；已用 5562 拉回的同一字节 APK 还原设备 |
| `run_isolation_pair.sh` 端到端 | **未执行** | 只做 `bash -n` 语法检查；它要求两臂不同 COMPONENT，本层（同 APK）按 S4 裁定不用 runner |
| T4 规则的时间戳读取 | **未修（新发现）** | `@After` 先关客户端，`connectStageTimestampsForTest()` 恒 null；两臂在时间戳面不可区分 |
| probe-02 的 4 核读数 | 描述性 | 5562 当时 `la1≈3.3`，jitter p95 78.9 ms，属争用窗；不代表空闲 4 核行为 |
| C3（30 ms 绝对阈值） | **未裁定** | 本轮空闲窗 2.0–3.4 ms、未触发；争用窗应红。阈值零改动 |
| arm64 / 真机 / 低内存 | UNVERIFIED | 无设备 |

## 11. 复跑命令（本轮逐字）

```bash
export PATH="$PATH:/c/Android/Sdk/platform-tools"

# 1) 构建 + 装机
./gradlew :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest
adb -s emulator-5566 install -r -t app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk
adb -s emulator-5566 install -r -t app/build/outputs/apk/androidTest/strictOffline/debug/app-strictOffline-debug-androidTest.apk

# 2) 15 例单臂（witness）
APP_SHA=$(sha256sum app/build/outputs/apk/strictOffline/debug/app-strictOffline-debug.apk | cut -d' ' -f1)
adb -s emulator-5566 shell am force-stop com.tingyun.smartmistakebook.offline
adb -s emulator-5566 logcat -c
adb -s emulator-5566 shell am instrument -r -w --no-window-animation \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  -e smb.lowload.gate PASS -e smb.t4.arm OFF \
  -e smb.witness.run probe-04 -e smb.witness.expected_tests 15 \
  -e smb.witness.apk_sha256 "$APP_SHA" \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  > build/ws3-batch43/probe-04.instrument.log 2>&1
adb -s emulator-5566 logcat -d -v time > build/ws3-batch43/probe-04.logcat
adb -s emulator-5566 exec-out run-as com.tingyun.smartmistakebook.offline \
  cat files/isolation-witness/probe-04/witness.jsonl > build/ws3-batch43/probe-04.witness.jsonl
python -I tools/ci/isolation_witness_check.py \
  --witness build/ws3-batch43/probe-04.witness.jsonl \
  --stream build/ws3-batch43/probe-04.instrument.log \
  --logcat build/ws3-batch43/probe-04.logcat \
  --require-executed --require-floors --out build/ws3-batch43/probe-04.check.json

# 3) 低负载门
python -I tools/ci/lowload_gate.py snapshot --serial emulator-5566 --out build/ws3-batch43/snapshot-5566.json
python -I tools/ci/lowload_gate.py calibrate --serial emulator-5566 --samples 30 --interval 10 --settle 120 \
  --out build/ws3-batch43/floor-5566.json
python -I tools/ci/lowload_gate.py gate --serial emulator-5566 --floor build/ws3-batch43/floor-5566.json \
  --out build/ws3-batch43/gate-5566-1.json

# 4) 六 run（L1-r*-*）与配对报告
python -I tools/ci/isolation_pair_report.py \
  --run "arm=OFF,round=1,window=INVALID,log=build/ws3-batch43/L1-r1-OFF.instrument.log,logcat=build/ws3-batch43/L1-r1-OFF.logcat" \
  --run "arm=ON,round=1,window=INVALID,log=build/ws3-batch43/L1-r1-ON.instrument.log,logcat=build/ws3-batch43/L1-r1-ON.logcat" \
  --run "arm=OFF,round=2,window=INVALID,log=build/ws3-batch43/L1-r2-OFF.instrument.log,logcat=build/ws3-batch43/L1-r2-OFF.logcat" \
  --run "arm=ON,round=2,window=INVALID,log=build/ws3-batch43/L1-r2-ON.instrument.log,logcat=build/ws3-batch43/L1-r2-ON.logcat" \
  --run "arm=OFF,round=3,window=INVALID,log=build/ws3-batch43/L1-r3-OFF.instrument.log,logcat=build/ws3-batch43/L1-r3-OFF.logcat" \
  --run "arm=ON,round=3,window=INVALID,log=build/ws3-batch43/L1-r3-ON.instrument.log,logcat=build/ws3-batch43/L1-r3-ON.logcat" \
  --off-arm-production-t4 test-side-only \
  --expected-methods tools/ci/visual_js_isolation_methods.txt \
  --out build/ws3-batch43/L1-pair.json --md build/ws3-batch43/L1-pair.md
```

## 12. 回链

- 设计：`docs/research/2026-11-05-isolation-selfwitness.md` §1–§5（`-4` 定义链、S1–S8、floor 表）。
- 裁定：`docs/research/2026-11-04-batch-42-record.md` §9.3（两层不得混写）、§9.5（UNCONVERGED、禁写 T4 排除）。
- 台账：`docs/known-defects.md` KD-76 条目批次 43 WS3 Progress 块。
- 落码：`IsolationWitness.kt`、`IsolationArms.kt:95-121`、`LowLoadProbe.kt:60-80,140-160,200-250`、`VisualJsIsolationInstrumentedTest.kt:170-830,913-940`。
- 工具：`tools/ci/isolation_witness_check.py`、`tools/ci/isolation_witness_expectations.json`、`tools/ci/run_isolation_pair.sh`（witness 注入/拉取/检查器）、`tools/tests/test_isolation_witness_check.py`。
