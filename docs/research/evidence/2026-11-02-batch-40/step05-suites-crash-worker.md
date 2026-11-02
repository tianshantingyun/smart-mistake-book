<!-- 体积自查：约 150 行；R4 上限 400 行 / 65536 B -->
# E-40-05 24+ 套件不回退 + 崩溃签名/worker 收口

| 字段 | 值 |
|---|---|
| evidence-id | E-40-05 |
| convention-version | 1 |
| batch | 2026-11-02-batch-40 |
| step | 步骤五（判据 §47/§48；含 §51 的 engine 3 轮） |
| gate-or-kd | R47-1…R47-6、C48-1…C48-6 |
| verdict | **PARTIAL/FAIL**：28 套件中 27 绿 237 例；`p09` 1 例红（T4/D 候选，未归因定案）；签名仅 p10 设计性 `death was not confirmed=1`；worker 账本闭合；但源窗口在执行中漂移（HEAD 变更/60 文件） |
| executed-at | 2026-11-02T10:30–10:56+08:00 |
| operator | 设备验证轨 |
| head | 构建 `a3f367df`；执行中 `dee81ea7`（10:45:47） |
| worktree | 源漂移 169 行 / 60 文件（`src-snapshot-drift.diff`） |
| device-serial | emulator-5584（API 34 / x86_64 / 4096） |
| apk-sha256 | app test `565fc85b…7c7b`；data test `34e3435d…3179`；database test `f12ef06a…6f92`（四包全程未换） |
| exit-code | 28×`am instrument` adb-exit=0；27×`OK (N tests)`；p09 `Tests run: 1, Failures: 1` |
| raw-log | `build/device-track-batch40/suites/suite-*.log` + `suite-*-logcat.txt`；`src-snapshot-drift.diff` |
| record-ref | `docs/research/2026-11-02-batch-40-record.md` |
| sanitized | 是 |

## 1. 28 套件逐套件（同一四包 APK）

| slug | 包 | N | 实测 | 判定 |
|---|---|---|---|---|
| v01app | APP | 4 | `OK (4 tests)` | PASS |
| nvec | DATA | 4 | `OK (4 tests)` | PASS |
| x02 | DATA | 6 | `OK (6 tests)` | PASS |
| a10x | DATA | 1 | `OK (1 test)` | PASS |
| a09 | APP | 3 | `OK (3 tests)` | PASS |
| **p09** | APP | 1 | `Tests run: 1, Failures: 1` | **FAIL** |
| p10 | APP | 1 | `OK (1 test)` | PASS |
| p0405 | APP | 1 | `OK (1 test)` | PASS |
| flags | APP | 2 | `OK (2 tests)` | PASS |
| backup | DATA | 34 | `OK (34 tests)` | PASS |
| rowcounts | DATA | 2 | `OK (2 tests)` | PASS |
| trimmer | DATA | 9 | `OK (9 tests)` | PASS |
| c4 | DATA | 8 | `OK (8 tests)` | PASS |
| repo | DATA | 12 | `OK (12 tests)` | PASS |
| parity | DATA | 16 | `OK (16 tests)` | PASS |
| painter | DATA | 24 | `OK (24 tests)` | PASS |
| pdf | DATA | 16 | `OK (16 tests)` | PASS |
| ordinal | DATA | 1 | `OK (1 test)` | PASS |
| reconciler | DATA | 6 | `OK (6 tests)` | PASS |
| engine | DATA | 27 | `OK (27 tests)` | PASS |
| a10 | DATA | 3 | `OK (3 tests)` | PASS |
| gates | DB | 5 | `OK (5 tests)` | PASS |
| matrix | DB | 16 | `OK (16 tests)` | PASS |
| m6970 | DB | 4 | `OK (4 tests)` | PASS |
| m7071 | DB | 3 | `OK (3 tests)` | PASS |
| dbcommit | DB | 12 | `OK (12 tests)` | PASS |
| x03 | DATA | 1 | `OK (1 test)` | PASS |
| isolation | APP | 15 | `OK (15 tests)` | PASS（单臂；见 E-40-04） |

- 合计 **237 例**（底册 236 + matrix 新增 1 例 `errorCauseTablesAreCreatedEmptyWhileV72RowsSurvive`；源码 `@Test` 与实测逐套件相符）。
- R47-1（只增不减）PASS；R47-2（逐套件绿）**FAIL**（p09）；R47-3（无跳过装置）PASS（28 文件 `@Ignore|assumeTrue|Assume.` 全 0；drill 不在 28 且在 E-40-02b §3 单独披露）。

## 2. p09 红例与归因

```text
aFourthDistinctProgramIsRejectedBusyAndLatestWinsKeepsFifoOrderWhileOneFrameIsInFlight
java.lang.AssertionError: the fourth distinct program must be rejected by the client queue,
got Rendered(sceneJson={"schemaId":"scene-D",...})
  at VisualJsClientFrameQueueDeviceInstrumentedTest.kt:227
```

- 用例设计（`VisualJsClientFrameQueueDeviceInstrumentedTest.kt:92-135`）：`abandonForTest()` 制造重建窗口 → A 在飞 → B1/B2/C → D 应为第 4 个不同 program 的 BUSY；实测 D 被 `Rendered`，说明 D 到达时重建已完成、队列已排空。
- 归因候选 **T4/D（时序窗）**：用例依赖“重建窗口足够长”的时序假设；本次 D 在窗口结束后到达。app 引擎/客户端文件不在本次源漂移集（`VisualJsClientEngine.kt` 等未变），无 `FATAL/ANR`，不是崩溃；批次 39 同套件曾绿。
- 未重跑洗绿（R47-4 禁止）；该红例保持红，需由主会话决定是用例加固（确定性阻塞帧）还是产品队列语义修复。
- R47-4 归因状态：**候选 T4/D，未定案**；不是 B（KB 文件不在该用例路径上）。

## 3. 崩溃签名（C48-1/C48-2）

35 个套件 logcat 聚合（脚本逐文件 `count`）：

| 签名 | 总数 | 归属 |
|---|---|---|
| `FATAL EXCEPTION` | 0 | — |
| `ANR in` | 0 | — |
| `NoSuchMethodError` | 0 | — |
| `NoClassDefFoundError` | 0 | — |
| `UnsatisfiedLinkError` | 0 | — |
| `SIGSEGV` | 0 | — |
| `CANNOT LINK` | 0 | — |
| `failed to map segment` | 0 | — |
| `death was not confirmed` | **1** | `p10`（`VisualJsDeathUnconfirmedFailClosedInstrumentedTest` 的设计路径；非崩溃、非未归因） |

- C48-1：被测应用进程（含 `:visual_js`）无崩溃签名；唯一 dnc 是 p10 的显式负向用例。
- C48-2：非零项已逐条归属（p10 设计路径），不作放宽先例。

## 4. worker 生命周期（C48-3/C48-5/C48-6）

| 指标 | 值 |
|---|---|
| `Start proc .*:visual_js` | 29 |
| `warm-up finished` | 29 |
| `warm-up failed` | **0** |
| `watchdog hard deadline` | 5（isolation 设计杀；见 E-40-04 §2） |
| `death was not confirmed` | 1（p10 设计） |
| 未配对 spawn（spawn − warm） | **0** |
| 套件后 `ps -A` | `NO_ORPHAN` |
| 进程形态 | 日志显示 `com.tingyun.smartmistakebook.offline:visual_js`（独立进程），isolation 断言 `workerUidForTest() != Process.myUid()` 通过 |

- C48-3 PASS；C48-5 PASS；C48-6 PASS（进程名 + 用例断言双证据）。
- C48-4（isolation 逐方法 kill 分布）**不满足**：见 E-40-04 §2；不得用本节总绿替代。

## 5. 同窗同源（R47-5）与源漂移

- 28 套件全程使用同一四包 APK（sha 未变）；APK 内部一致。
- 但源快照在执行中漂移：KB 32B 的文件级还原在 **10:33:32** 把 `StudyDatabase.kt` 覆盖回 71（`src-sha-before` `ab50bbd8…` → `src-sha-final` `7210f974…`），并改动 60 个 KB 文件；`src-snapshot-drift.diff` = 169 行。`dee81ea7`（10:45:47）只提交两个 docs 文件，不是该源文件的回滚提交。
- 因此严格按 R47-5“同一源快照”**不成立**；设备结果绑定 10:13 的 APK/sha，不绑定当前 HEAD。恢复 WS2 73 注册后必须重建/重跑。
