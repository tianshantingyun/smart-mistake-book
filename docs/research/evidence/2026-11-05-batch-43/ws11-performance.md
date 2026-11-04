<!-- 体积自查：247 行 / 22,749 B（落盘后 wc -lc 实测）。本文件只写证据，不改行为。 -->

# 批次 43 WS11：性能基线（启动首帧 / 导出耗时 / 内存峰值 / 回归结论）

| 字段 | 值 |
|---|---|
| evidence-id | `B43-WS11-PERF-01` |
| 计划 | `.zcode/plans/plan-2026-11-05-batch-43.md` §2 WS11 项 69–72（`:114-118`）；§4 硬约束（`:133-140`）；§5 验收 |
| 上游口径 | 批次 42 收口 `docs/research/2026-11-04-batch-42-record.md` §9.5（发版就绪不成立）；批次 43 基线 `docs/research/2026-11-05-batch-43-baseline.md` |
| 指标定义源 | `benchmark/src/androidTest/kotlin/com/tingyun/smartmistakebook/benchmark/PerformanceTargets.kt:18,21,24,27,42`（冷启 2000ms、热启 1000ms、搜索 P95 350ms、首屏 500ms、内存 200MB） |
| executed-at | 2026-11-04T06:50+08:00 – 2026-11-04T07:14+08:00（设备时钟为 UTC-0，日志里的 `11-03 22:5x` 即本机 06:5x） |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（本轮 `git rev-parse HEAD` 实读） |
| 工作树 | 590 条脏（共享树；本轨**零 git 写**） |
| 设备 | `emulator-5584`（`sdk_gphone64_x86_64`，API 34，x86_64，PAGE_SIZE 4096，boot_id `4d9a0729-c675-4a1f-91b8-9b2d3d709451`） |
| 负载 | 启动段 loadavg 1.54→3.82（7 次 force-stop/冷启本身推高）；导出/预览/DB 门段 1.6–3.0；**共享 AVD，非隔离性能环境** |
| 构建身份（实测 sha256，跑前/跑后两次一致） | app `1aee470aaff2db5320eae676e8155de75efc4c1febf21c09b6c8ea63e29ed3cd`；app.test `cd35452efbc9d125242b515bf84f1cba747bd53bf849f85aa87a49c0ed5ed76d`；export.test `08ea26417f746daddf0344c8b12433702628e25670f7ca80283b2e2374e165c0`；data.test `48213049f887930b70a10bcdb611f24a242d411bc2a3ecda7c177b13a808154f`；database.test `671b381573fa1d7d7891670aa7cf8f194546788abb5b8f735fcbc32d0e458fec` |
| 身份对照 | 与批次 43 WS4 的设备绑定逐位相同（`docs/research/evidence/2026-11-05-batch-43/ws4-device-rebaseline.md:14-17`），即本基线量的是 **post-rollback APK**；WS2 证据里的 app `ec135844…` 是更早一版，已被覆盖 |
| 执行者 | WS11 性能轨（子代理）；共享工作树 |
| 未执行 | 任何 Gradle（构建/安装/connected）、任何 git 写、任何源码或测试代码改动、任何 6C-2 行为改动 |
| verdict | **PARTIAL**：模拟器上四类基线已实测落盘；**真机/arm64 基线 BLOCKED**；**“生产进程内仅导出阶段”的内存峰值 UNVERIFIED**（§5） |

> 口径：**实跑** = 本轮执行并读退出码/输出；**实读** = 本轮读源码/文档原文（给行号）；**转录** = 引既有证据。
> 所有数字都来自 `build/ws11-batch43/`（gitignored）的原始产物，可按下文 §7 复算；本文件不把模拟器数字当成真机结论。

---

## 0. 结论（pick）

1. **item 69 已出基线**：冷启 TotalTime（到首帧）7 轮 = `4313/4266/3548/3467/3503/3432/3340 ms`，p50=3503ms；前两轮 4289.5ms → 后五轮 3458ms，**warm-up 收敛约 0.8s**。HOT 起 5 轮 = `182–214ms`（p50=189ms）。`dumpsys gfxinfo` 末进程仅 4 帧且 4 帧全 janky（p50 1700ms）——冷启首帧路径在模拟器上全部错过 vsync。
2. **item 70 已出基线**：产品路径单题 A4 导出（点确认→记录终态）= `659/731/900/706/689/763 ms`（n=6，p50≈719ms）；60 题批量导出（测试自带 `MistakeBatchPerf` 计时）= `518/545/569 ms`、64 页（n=3）；长文档导出套件 390/391ms；PDF 预览套件 346–373ms；D-04 视觉预览 652/672ms。**批量导出的 5s 内联断言三轮均通过**。
3. **item 71 已出基线（分进程口径）**：最小宿主进程（export.test）峰值 PSS——60 题导出 `48.6–55.4 MB`、长文档 `42.5–43.2 MB`、PDF 预览 `42.4–43.9 MB`；data.test 里的 D-04 视觉预览 `67.2–72.7 MB`；**生产 app 冷启稳态 PSS 188.3MB**（模拟器，低于 200MB 目标线 11.7MB）。产品 UI 仪器化进程的 PSS 峰值 `319–334MB`，**但含 Compose 测试宿主与夹具播种，不能归因于导出**（§3.1(2)）。
4. **item 72**：本轨**没有更早的启动/导出/预览基线可比**（全仓检索无同类实测记录），所以本轮只建立基线，**不宣称“无回归”**。可下的结论有二：① 既有 DB 性能门 `PerformanceGateTest` 在当前 APK 上 **6/6 绿、81.978s**，搜索页 p95 **50ms**（目标 350ms），无回归信号；② 冷启/首帧在模拟器上超出物理机目标，**不构成产品判定**（见 §5）。
5. **旁证观测（不是本轨判定）**：用普通用户方式启动 app（非仪器化）后，详情页停在 `mistake_detail_loading` > 2.5 分钟；logcat 显示连接池被一条 `UPDATE knowledge_node` 连续占用，30s/60s/90s… 逐次告警（§5.3）。该库是 WS4 夹具播种后的 229.65MB 测试库，**不能据此判生产回归**；但它直接挡住了“生产进程内导出内存”的测量，故只记事实与复现命令。

---

## 1. item 69：warm-up / 首帧基线（实跑）

**口径**：本轨的 “warm-up” 指**应用冷启前几轮的预热收敛**（首轮 vs 平台期）；KD-76 的 JS worker “warm-up finished” 是另一口径，属 WS2/KD-76，不在本轨重测。
**方法**：`am force-stop` → `am start -W`（冷启，7 轮）× 2s 间隔；HOT 起 = HOME → `am start -W`（进程常驻，5 轮）。`TotalTime` 为系统报告的“到首帧”时间；同时抓 `ActivityTaskManager: Displayed` 与 `dumpsys gfxinfo`。脚本 `build/ws11-batch43/startup_baseline.sh`，原始：`startup-cold.txt`、`startup-warm.txt`、`gfxinfo-after-cold.txt`、`meminfo-after-cold.txt`、`startup-meta.txt`。

| 轮次 | 冷启 TotalTime (ms) | logcat `Displayed` | 备注 |
|---|---|---|---|
| cold#1 | 4313 | `+4s313ms` | 首轮（JIT/页缓存未热） |
| cold#2 | 4266 | `+4s266ms` | |
| cold#3 | 3548 | `+3s548ms` | 进入平台期 |
| cold#4 | 3467 | `+3s467ms` | |
| cold#5 | 3503 | `+3s503ms` | |
| cold#6 | 3432 | `+3s432ms` | |
| cold#7 | 3340 | `+3s340ms` | |

- 统计：min 3340 / p50 3503 / p95 4266 / max 4313 ms（n=7）。
- **warm-up 曲线**：前 2 轮均值 4289.5ms，后 5 轮均值 3458ms（−19.4%）；冷启数字必须注明“第几轮”，否则不可比。
- HOT 起：`191/182/182/214/189 ms`（p50 189ms）；`LaunchState: HOT`，logcat 明说 `Activity not started, its current task has been brought to the front`——这是热启（任务前置），**不是** warm（Activity 重建）口径。
- 启动帧：`gfxinfo-after-cold.txt`（force-stop 前 reset，读到的只是最后一次进程）——`Total frames rendered: 4`、`Janky frames: 4 (100%)`、p50 1700ms、`Number Slow UI thread: 4`。模拟器冷启逐帧超时，只作基线，不作体验结论。

**与环境目标对照（不作产品判定）**：`COLD_START_TARGET_MS=2000`（`PerformanceTargets.kt:18`）、`FIRST_SCREEN_TARGET_MS=500`（`:27`）。冷启 3.34–4.31s 超线；HOT 起 182–214ms 在线内（但 HOT≠首屏）。`StartupBenchmark` 自身 KDoc 写明数字“only meaningful on physical devices”（`benchmark/src/main/.../StartupBenchmark.kt:20-22`），本轮无真机，故 **BLOCKED**。

---

## 2. item 70：导出耗时基线（实跑）

### 2.1 产品路径单题 A4（真实 UI + WorkManager + 真实 exporter）

- 用例：`com.tingyun.smartmistakebook.MistakeExportBackgroundInstrumentedTest#exportCompletesAfterTheDetailingPageIsLeftAndStaysRetrievable`（`app/src/androidTest/.../MistakeExportBackgroundInstrumentedTest.kt:56-112`）。
- 计时源：**导出记录表**的 `created_at_epoch_millis → finished_at_epoch_millis`（`core/domain/.../MistakeExportRecord.kt:51-61`；写入路径 `app/src/main/.../SmartMistakeBookApplication.kt:966-990` 先落 RUNNING 再入队，worker 落终态）。这是“用户点确认 → 结果可回看”的端到端时延，比套件 `Time:` 更贴近用户。
- 每轮前 `force-stop`；`cold*` 4 轮先 `run-as <app> rm -rf cache/mistake_pdf_exports` 强制重渲染，`cached*` 2 轮不清缓存。

| 轮次 | 导出类型 | 页数 | `created→finished` (ms) |
|---|---|---|---|
| cold1 | SINGLE | 1 | 659 |
| cold2 | SINGLE | 1 | 731 |
| cold3 | SINGLE | 1 | 900 |
| cold4 | SINGLE | 1 | 706 |
| cached1 | SINGLE | 1 | 689 |
| cached2 | SINGLE | 1 | 763 |

统计：min 659 / p50 718.5 / p95 763 / max 900 ms（n=6）。**冷缓存与命中缓存无可分辨差异**（cold p50≈718.5、cached 689/763）——单题 PDF 的渲染成本远小于 WorkManager 入队/调度的固定开销，时延由调度主导。

原始：`product/*.instr.txt`、`product/*.pss.txt`、`product/summary.txt`；记录查询见 §7。

### 2.2 60 题批量导出（测试自带计时）

- 用例：`MistakePdfExporterInstrumentedTest#batchExportRendersBeyondSingleQuestionPageLimitWithinItsOwnBound`（`core/export/src/androidTest/.../MistakePdfExporterInstrumentedTest.kt:341-370`），测试自身 `Log.i("MistakeBatchPerf", …elapsedMs=…)` 并断言 `< 5000ms`。
- 每轮前清 `core.export.test` 的 `cache/mistake_pdf_exports`。

| 轮次 | `pdfQuestions` | `pages` | `elapsedMs` | 套件 `Time:` | 结果 |
|---|---|---|---|---|---|
| batch60-1 | 60 | 64 | 545 | 1.307s | OK (1 test) |
| batch60-2 | 60 | 64 | 569 | 1.286s | OK (1 test) |
| batch60-3 | 60 | 64 | 518 | 1.241s | OK (1 test) |

统计：min 518 / p50 545 / max 569 ms（n=3）。logcat 原文各轮：`MistakeBatchPerf: pdfQuestions=60 pages=64 elapsedMs=545/569/518`（`coreexport/*.logcat.txt`）。

### 2.3 长文档导出（多页 + 超限拒绝）

`MistakePdfExporterInstrumentedTest#longDocumentPaginatesAndHardLimitFailsClosed`（`:221-239`）：套件 `Time:` **0.390s / 0.391s**（2 轮，`OK (1 test)`）。该用例含 1 次多页导出 + 1 次 `PAGE_LIMIT_EXCEEDED` 负向。

### 2.4 预览

| 路径 | 用例 | 套件 `Time:`（3 轮/2 轮） | 结果 |
|---|---|---|---|
| PDF 预览（2 次 prepare + 2 次 `renderPage(0,595,842)`） | `MistakePdfDeliveryRegressionInstrumentedTest#previewPageZeroKeepsA4SizeAndAspectUnderParameterizedLayouts`（`core/export/src/androidTest/.../MistakePdfDeliveryRegressionInstrumentedTest.kt:60-83`） | 0.373 / 0.364 / 0.346s | OK ×3 |
| D-04 视觉预览装配（真 Canvas→PNG→vault→仓储） | `TutorVisualPreviewAssemblyInstrumentedTest#switchOnRendersThroughTheProductionChainAndCommitsThePreviewId`（`core/data/src/androidTest/.../TutorVisualPreviewAssemblyInstrumentedTest.kt:41`） | 0.652 / 0.672s | OK ×2 |

> 这两条是“套件时延”，含夹具/断言开销，**不是纯渲染微基准**；仓库没有预览专用的计时打点，故不把套件时间改写成“单次预览耗时”。

---

## 3. item 71：内存峰值（实跑，PSS）

**方法**：root adb 下 50ms 轮询目标进程 `/proc/<pid>/smaps_rollup` 的 `Pss:`（脚本 `build/ws11-batch43/export_mem_baseline.sh`），取目标进程生命期内的峰值；每张表都给首样本与峰值。

| 工作负载 | 目标进程 | 峰值 PSS | 首样本 PSS | 样本数 |
|---|---|---|---|---|
| 60 题批量导出（cold1/2/3） | `core.export.test` | **55.4 / 48.6 / 48.6 MB** | 9.5 / 8.6 / 15.4 MB | 16–18 |
| 长文档导出（2 轮） | `core.export.test` | **42.5 / 43.2 MB** | 15.5 / 16.2 MB | 7 |
| PDF 预览（3 轮） | `core.export.test` | **43.7 / 42.4 / 43.9 MB** | 23.4 / 8.5 / 23.4 MB | 5–6 |
| D-04 视觉预览（2 轮） | `core.data.test` | **72.7 / 67.2 MB** | 4.8 / 16.1 MB | 14–15 |
| 产品 UI 导出（6 轮，仪器化宿主） | `localfirst`（app 进程） | **319.2–333.6 MB** | 18.9–33.2 MB | 95–100 |
| 冷启后空转（非仪器化） | `localfirst` | **188.3 MB**（`TOTAL PSS: 188349`） | — | 1 次快照 |

### 3.1 口径与限制（必须并读）

1. `core.export.test` / `core.data.test` 是**自仪器化最小宿主**，其 PSS 反映“导出/预览代码路径自身 + 测试运行器”的占用；可直接横向比较同类负载，但**不等于**生产 app 进程的全量占用。
2. 产品 UI 导出的 319–334MB 峰值里，Compose 测试宿主、Espresso、夹具播种占了绝大部分：按导出记录窗口（`created-0.6s … finished+0.6s`）切片，窗口内 PSS 中位数 294.9–305.3MB，**导出前后没有可分辨的抬升**。因此该数**不能**写成“导出内存 320MB”，也不能拿去对 200MB 目标下红/绿。
3. “生产 app 进程内仅导出阶段”的峰值 **UNVERIFIED**：手动 UI 导出被 §5.3 的 KB 批量更新挡住，本轮未取得干净数字。复跑 runbook 见 §7.3。
4. 200MB 目标（`PerformanceTargets.kt:41`）没有定义进程/相位口径；本轮按“冷启稳态 PSS”对照得到 188.3MB（低 11.7MB），**不把该对照外推到导出相位**。

---

## 4. 既有性能门（回归信号，实跑）

`PerformanceGateTest`（`core/database/src/androidTest/.../PerformanceGateTest.kt`，10k 行真实夹具）在当前 `core.database.test`（sha `671b…`）上：

- 命令：`adb -s emulator-5584 shell am instrument -w -r -e class com.tingyun.smartmistakebook.core.database.PerformanceGateTest com.tingyun.smartmistakebook.core.database.test/androidx.test.runner.AndroidJUnitRunner`
- 结果：`OK (6 tests)`、`Time: 81.978`、宿主墙钟 84.7s；搜索页 `p95=50ms`（打印原文 `library search page benchmark: samples=[…] p95=50ms`），目标 350ms。
- 判定：**门绿且有量级余量**，DB 路径无回归信号。该轮是 WS4 32 套件之外的补跑（WS4 的 `gates` 是 `TutorVisualStructureGatesInstrumentedTest`，不是性能门，`build/ws4-batch43/suites/suite-gates.log` 可核）。

原始：`build/ws11-batch43/dbperf/gates.instr.txt`、`dbperf/bench-lines.txt`。

---

## 5. item 72：性能回归结论

### 5.1 可下的结论

| # | 结论 | 依据 | 强度 |
|---|---|---|---|
| P1 | **本轮是启动/导出/预览的首个基线**；仓内无同类历史实测，故不对这三类宣称“无回归” | `docs/` 检索 `MistakeBatchPerf\|pdfQuestions\|elapsedMs=` 只命中 `docs/research/typst-android-feasibility.md:17` 对日志本身的引用；未发现启动/导出/预览的实测基线文件 | 实读 + 实跑 |
| P2 | **DB 性能门无回归信号** | `PerformanceGateTest` 6/6、p95 50ms（§4） | 实跑 |
| P3 | **导出/预览在最小宿主内的内存量级健康**：单次导出路径峰值 42–55MB，PDF 预览 42–44MB，D-04 视觉预览 67–73MB | §3 表 | 实跑（模拟器口径） |
| P4 | **产品路径单题导出 < 1s（p50 719ms、max 900ms）**；其中渲染不是主导，WorkManager 调度/记录落库是固定成本 | §2.1；60 题 64 页仅 518–569ms，说明单题渲染远小于端到端时延 | 实跑 |
| P5 | **冷启/首帧在模拟器上超物理机目标**（3.34–4.31s vs 2000ms；HOT 182–214ms）——只登记为模拟器基线，**不作产品红/绿** | §1；`StartupBenchmark.kt:20-22` 与 `PerformanceTargets.kt` 的真机口径 | 实跑 + 实读 |

### 5.2 不能下的结论（显式）

- **不能**说“真机性能达标/不达标”：无 arm64/真机（与批次 43 发版前置同一阻塞）。
- **不能**说“导出内存 320MB，超 200MB 目标”：该数含仪器化宿主，见 §3.1(2)。
- **不能**把 §5.3 的 KB 停顿判成性能回归：它是 229.65MB 测试库 + 批量 `UPDATE knowledge_node` 的**环境态**，不是 clean-install 产品路径；且未做对照。**登记为观测，移交 KD/冷启轨判断**。
- **不能**把批量 60 题数据当“用户典型批量”：夹具是 60×120 段重复文本，页数 64；真实用户批量规模未抽样。

### 5.3 观测：非仪器化启动后的 KB 批量更新停顿（不是本轨判定）

- 现场：普通启动 → 点错题本 → 进 `closed-interval-extrema` 详情；`mistake_detail_loading` 持续 >150s（截图 `build/ws11-batch43/detail.png`，UI dump `ui-3..6.xml`），最终 force-stop。
- logcat（`build/ws11-batch43/kb-stall-logcat.txt`，6 条）逐字：`SQLiteConnectionPool: … unable to grant a connection … for 30.005001 seconds`（23:05:58）、`… 60.013004 seconds`（23:06:28）、`… 90.018005 seconds`（23:06:58），直到 23:08:28；被占用语句为 `UPDATE knowledge_node SET … WHERE knowledge_node_id = ?`，库文件 `databases/smart-mistake-book.db`（229,650,432 B，由 WS4 夹具播种留下）。
- 影响：本轮**无法**在该设备态下取得“生产进程内导出/预览”的干净 PSS/时延（见 §3.1(3)）。
- 归因边界：未与 clean-install 基线对照、未复跑第二台设备，**不得写成回归**；若主会话要追，最小复现 = §7.3 步骤 1–3 + `logcat -d | grep "unable to grant a connection"`。

---

## 6. 对 6C-2 / 热区 / 构建纪律的声明

- **6C-2**：未触碰 `mask` 语义、`ALLOWED_MASKS`、派生索引；本轨零行为改动（计划 §4 `:135`）。
- **热区**：未改 `core/domain`、`core/ui`、`feature/*`、`docs/current`、`settings.gradle.kts`、`Application/Root`；未改 `core/export` 生产代码。
- **构建**：本轮**零 Gradle**（未构建/未安装/未跑 connected），全部测量走已安装 APK + adb；符合“Gradle 只在门禁阶段串行运行”。
- **测试先红后绿**：本轨没有新增/修改测试或生产代码，故无红→绿可演；所用用例均为既有已安装用例，运行时自带断言（批量导出 <5s、预览尺寸/页数、记录 SUCCEEDED、DB 门阈值）且本轮全绿。
- **新增物**：`build/ws11-batch43/*.sh`（gitignored、一次性测量脚本）+ 本文件。它们消灭的具体失败 = **“性能结论只能靠回忆/估数，无法复算”**（此前仓内没有启动/导出/预览的可复跑基线）。
- **设备卫生**：app 最终 `am force-stop`；导出记录表内共 7 条 SUCCEEDED 行（1 条 sanity + 6 条计时轮），导出缓存按轮清理；§5.3 的 KB 批量更新被 force-stop 打断，SQLite 事务应回滚、下次启动可续装——**若后续轨要用 5584 的干净库，先 `pm clear` 再播种**。

---

## 7. 复跑命令

### 7.1 启动 / 首帧

```bash
export ADB=/c/Android/Sdk/platform-tools/adb.exe
bash build/ws11-batch43/startup_baseline.sh emulator-5584 7 5
# 产物：build/ws11-batch43/{startup-meta,startup-cold,startup-warm,gfxinfo-after-cold,meminfo-after-cold}.txt
```

### 7.2 导出 / 预览 / 内存

```bash
bash build/ws11-batch43/export_mem_baseline.sh coreexport   emulator-5584
bash build/ws11-batch43/export_mem_baseline.sh preview      emulator-5584
bash build/ws11-batch43/export_mem_baseline.sh visualpreview emulator-5584
bash build/ws11-batch43/export_mem_baseline.sh product      emulator-5584
# 每轮 summary.txt 一行：label|Time:|OK/FAIL|FQCN|host_wall_ms|pss_first_kb|pss_peak_kb|samples
```

导出记录的端到端时延（§2.1）用设备内 sqlite 直接查（WAL 可读）：

```bash
adb -s emulator-5584 shell "run-as com.tingyun.smartmistakebook.localfirst \
  sqlite3 databases/smart-mistake-book.db \
  \"select export_id,status,created_at_epoch_millis,finished_at_epoch_millis, \
    finished_at_epoch_millis-created_at_epoch_millis as dur_ms,page_count \
    from mistake_export_record order by created_at_epoch_millis desc limit 10;\""
```

批量导出计时（§2.2）从各轮 logcat 取：`grep MistakeBatchPerf build/ws11-batch43/coreexport/*.logcat.txt`。

### 7.3 真机 / 生产进程内导出内存（runbook，**BLOCKED**）

1. 干净安装当前 APK（`1aee470a…`），**不播种 WS4 测试库**（`adb shell pm clear <pkg>` 后走正常首启，等 KB 安装完成）。
2. 用 UI 进入错题本 → 详情 → 导出 A4 → 确认；另一条同样走详情页“预览”。
3. 全程 50ms 轮询 `PSS`：`while :; do pid=$(adb shell pidof <pkg>); [ -n "$pid" ] && adb shell grep '^Pss:' /proc/$pid/smaps_rollup; sleep 0.05; done`；导出窗口用记录表 `created/finished` 时间戳切割。
4. 记录 `Build.MODEL`/`ro.product.cpu.abi`/`ro.build.version.sdk` 与 APK sha；**验证目标**：`MEMORY_TARGET_MB=200`（`PerformanceTargets.kt:41`）在“生产进程 + 导出相位”下的口径需由主会话先定义（当前定义缺进程/相位，见 §3.1(4)）。
5. 另跑 `benchmark` 模块的 `StartupBenchmark`（需 Gradle 构建 `:benchmark` 与 Macrobenchmark，属门禁阶段）以取得真机 `StartupTimingMetric` 与 `FrameTimingMetric`。

---

## 8. 限制 / UNVERIFIED

| 项 | 状态 | 原因 |
|---|---|---|
| 真机 / arm64 启动、导出、内存 | **UNVERIFIED / BLOCKED** | 本机仅有 x86_64 模拟器；arm64 缺失与批次 43 发版前置同一阻塞 |
| 生产进程内“仅导出/预览”的 PSS 峰值 | **UNVERIFIED** | §5.3 KB 批量更新挡住手动 UI 导出；仪器化宿主数不可归因 |
| warm（Activity 重建）口径 | **UNVERIFIED** | adb 只能稳定制造 COLD 与 HOT；warm 需 `am finish`/开发者选项协助，本轮未做 |
| Macrobenchmark `StartupTimingMetric`/`FrameTimingMetric` | **UNVERIFIED** | 需 Gradle 构建 `:benchmark`；本轨零 Gradle |
| 帧率/掉帧交互基线 | 仅冷启首帧快照 | gfxinfo 只覆盖末进程 4 帧；未做滚动/导航脚本 |
| 导出/预览历史对照 | **无历史基线** | 仓内无同类实测（P1） |

## 9. 原始产物清单（gitignored）

`build/ws11-batch43/`：`startup-baseline.sh`、`startup-meta.txt`、`startup-cold.txt`（sha256 `499843ee…`）、`startup-warm.txt`（`97faf28d…`）、`gfxinfo-after-cold.txt`、`meminfo-after-cold.txt`（`e7fa5dc9…`）、`product/`（`summary.txt` `0feadc88…`、6×`*.instr.txt`/`*.pss.txt`/`*.logcat.txt`）、`coreexport/`（`5e0c25ad…`）、`preview/`（`10a3cf3c…`）、`visualpreview/`（`34cd0bab…`）、`dbperf/gates.instr.txt`（`8d749890…`）、`kb-stall-logcat.txt`、`detail.png`、`ui-1..6.xml`。

## 10. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md:114-118`（WS11 69–72）、`:133-140`（硬约束）、`:144`（验收）。
- 指标定义：`benchmark/src/androidTest/kotlin/com/tingyun/smartmistakebook/benchmark/PerformanceTargets.kt:18,21,24,27,42`。
- 导出计时来源：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/MistakeExportRecord.kt:51-61`；入队/落库：`app/src/main/kotlin/com/tingyun/smartmistakebook/SmartMistakeBookApplication.kt:966-990`。
- 既有门：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/PerformanceGateTest.kt`；`.../LibraryCatalogScalePerformanceInstrumentedTest.kt:20-27`。
- 设备身份：`docs/research/evidence/2026-11-05-batch-43/ws4-device-rebaseline.md:14-17`。
- 批次 42 收口口径：`docs/research/2026-11-04-batch-42-record.md:539-546`。
