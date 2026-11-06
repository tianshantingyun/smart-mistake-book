<!-- 体积自查（实测 wc -lc）：460 行 / 37,212 B。字节在 R4 的 64 KiB 内；行数超 400 行上限（R4 的上限写在“设备验证证据落盘规范”，形式上约束 docs/research/evidence/**；本文件是 docs/research 下的基线件，上一批同类件 312 行）。若主会话要求严守 400 行，按 R4 的处理办法拆分而非删内容。批次 43 基线核对员落盘。 -->

# 批次 43 基线核对（只读，2026-11-03）

| 字段 | 值 |
|---|---|
| evidence-id | `B43-BASELINE-01` |
| executed-at | 2026-11-03（本机时间）；文档名按计划批次 43 的 `2026-11-05` |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD` 本轮实跑） |
| origin/main | 本地 `refs/remotes/origin/main` = 同值；`git rev-list --left-right --count HEAD...origin/main` = `0	0`；**远端未 fetch 核实**（见 §1.1） |
| 执行者 | 基线核对员（子代理），共享工作树，**只读** |
| 工具（本轮实跑版本） | git 2.55.0.windows.5、Python 3.13.14、adb 1.0.41（37.0.1-15733141，`C:\Android\Sdk\platform-tools`）、emulator 37.1.11.0、pypdf 6.16.2、PyMuPDF 1.28.2 |
| 未执行 | 任何 Gradle 任务、任何 `am instrument`、任何 APK 构建/安装、任何 git 写、任何 Provider 调用、任何 emulator 新起（§6.3 说明为何） |
| verdict | 基线成立；**6 项核对全部有实跑/实读证据**；计划侧 2 条口径缺陷（§7 F1/F2）、1 条不可变清单的**文档层漂移**（§7 F3）、1 条热区红线提醒（F4）。无一条需要本批改行为 |

> **口径**：**实跑**=本轮在本机执行的命令与输出；**实读**=本轮读到的仓内源码/文档/日志；**转录**=引用既有 E 文件或台账原文（标明出处行）。
> 状态严格三值：**已核** / **未核（UNVERIFIED）** / **阻塞（BLOCKED）**。凡"绿"必指名绑哪份 APK/sha 与哪份日志。

---

## 一、HEAD / origin / 工作树 / DB schema / 请求 schema

### 1.1 HEAD 与远端

```text
$ git rev-parse HEAD            → 0fef943641f53560b92ce2afec4da22bf52f5e86
$ git rev-parse origin/main     → 0fef943641f53560b92ce2afec4da22bf52f5e86
$ git rev-list --left-right --count HEAD...origin/main → 0	0
$ git log -1 --format="%h %ci %s" origin/main
  0fef9436 2026-11-03 20:08:52 +0800 docs: 批次42 台账/准入/设计件机器块重生成（KD-83 关闭；KD-75/76 open）（5 文件）
$ ls -l .git/FETCH_HEAD → 2026-11-01 16:02
```

- HEAD = 批次 42 收口提交 `0fef9436`，与计划 §"基线：批次 42 收口 `0fef9436`（已推送）"一致（计划 `:4`）。
- **边界**：`origin/main` 是**本地 ref**；`.git/FETCH_HEAD` 停在 2026-11-01 16:02，本轮**未**执行 `git fetch`（fetch 属 git 写 refs，本 ask 禁写）。故"远端此刻仍等于该提交"**UNVERIFIED**，只能写"本地 ref 相等"。

### 1.2 工作树计数

```text
$ git status --porcelain -uall | wc -l            → 3722
$ git status --porcelain -uall | cut -c1-2 | sort | uniq -c
   3633 ??
     89  M
$ git diff --cached --name-only | wc -l           → 0
$ git status --porcelain -uall | grep -c "^ D\|^D" → 0
```

| 量 | 值 | 备注 |
|---|---:|---|
| 逐文件条目（`-uall`） | **3722** | 89 M + 3633 ?? |
| 目录折叠口径（默认 porcelain） | 510 行 | 421 ?? + 89 M；同一状态，不可与 3722 混引 |
| 暂存 | **0** | 无 staged |
| 删除（D） | **0** | 批次 42 的 14 D 已随提交消失 |
| 未跟踪锁文件 | **3** | `core/domain/gradle.lockfile`、`core/export/gradle.lockfile`、`core/ui/gradle.lockfile` —— **禁提交**（纪律） |

M 的顶层分布（`git status --porcelain -uall | grep "^ M"`）：`docs` 39、`core` 35、`tools` 7、`app` 5、`knowledge-production` 1、根 `dynamic_gui_architecture_plan.md` 1、`.gitignore` 1。

`core` 内分布：`core/data` 22（KB/dense 线：`androidTest/assets/dense`、`test/resources/dense`、knowledge 相关）、`core/database` 5、`core/export` 3（**全部是 test**：`MistakePdfEligibilityTest`/`MistakePdfPagePlannerTest`/`MistakePdfPlannerGoldenTest`）、`core/model` 2、`core/domain` 2、`core/ui` 1。

热区实况（计划 §4「热区禁改」口径对照）：

| 热区 | M 数 | 文件 |
|---|---:|---|
| `core/domain` | 2 | `BackupRepository.kt`、`TutorJudgedReview.kt`（均他线，见 §7 F3 的例外说明） |
| `core/ui` | 1 | `Theme.kt`（他线） |
| `core/export` | 3 | **仅测试**；主码 `MistakePdfExporter.kt` 与 `MistakePdfEligibility.kt` 本轮**干净（未改）** |
| `docs/current` | 7 | 见 §4 |
| `settings.gradle.kts` | 0 | — |
| `Application`/`Root` | 0 | `git status` 无命中 |

> 与批次 42 记录 §9.2 对账：`core/domain/BackupSelection.kt` 回滚**已生效**（它不在 M 列表），`ALLOWED_MASKS` 行为层维持 `{9,13,15}`（§7 F3 给出实测行号）。但同批回滚**未覆盖** `BackupRepository.kt` 与 `app/.../SecondaryScreens.kt` 的文档/UI 层 —— 这是本批必须知道的事实，见 F3。

### 1.3 DB schema 73

| 证据 | 值 |
|---|---|
| 代码 | `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:142` → `internal const val STUDY_DATABASE_VERSION = 73` |
| 导出 JSON | `core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/73.json` |
| JSON 实测（`python -I` 解析） | `version 73` / `entities 92` / `views 2` / `setupQueries 2` / `identityHash 1d086050204a0da1ed6b6e3299dd9733` / 文件 `sha256 c7e7586a4eaab6d7309e91f2b715a74229369d95bc11966cfe96a94755f39f1e` |

### 1.4 请求 schema 19

| 证据 | 值 |
|---|---|
| 常量 | `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:566` → `const val PAPER_EDIT_SCHEMA_VERSION = 19`；`:567` → `CURRENT_SCHEMA_VERSION = PAPER_EDIT_SCHEMA_VERSION` |
| 钉值测试 | `core/model/src/test/.../TutorVisualContextFingerprintTest.kt:68`（`assertEquals(19, …)`）、`PaperEditTasksTest.kt:36`、`core/data/.../PaperEditModelPortTest.kt:64` |

### 1.5 备份四方门（本轮唯一实跑的门）

```text
$ python -I tools/ci/check_backup_plan_sync.py
… OK schemaVersion / identityHash / table list 92 / trimOrder 84 / views 2 / allowedMasks == [9,13,15]
  OK v4: 91 keys and delta == {+error_cause, -material_feature}
  OK v4: frozen closure/prefix/trim limit：closure=4 prefix=smbk-selection-v4 trimLimit=83
  OK v4: empty-full digest golden：recomputed=ee9a1b4d…b006 pinned=ee9a1b4d…b006
PASS 四方一致（code / §9.11 / golden / 73.json）
EXIT=0
```

- 这条门**只**跑 `tools/ci/check_backup_plan_sync.py`（只读脚本，无 Gradle），是本轮唯一执行的门禁。
- 它的 `allowedMasks == [9,13,15]` 输出是 §7 F3"6C-2 行为层完好"的第二条独立证据。
- 同时它正是 §5 S11 的载体：`v4: empty-full digest golden` 钉的是 **v4 空包** digest，不是真夹具字节。

---

## 二、KD-75 C1–C8 与 KD-76 A6/C4 现状

### 2.1 KD-75：判据表（转录自 E-42-WS2-01）

| # | 判据 | 现状（原文口径） | 判定 |
|---|---|---|---|
| C1 | 含 AI 图 PDF 在设备被**独立读者**读回 `/AIGC` | 代码+设备用例在树 | **设备侧已达（见 2.2）**；本轨自评为 UNVERIFIED |
| C2 | 含 `MODEL_DOCUMENT_PARSE` 文本的 PDF 读回 `/AIGC` | **未实现**：`MistakePdfExportInput` 无 provenance，实参固定 `hasModelParsedText=false` | **FAIL（本批仍不可达）** |
| C3 | 纯用户内容读不到 `/AIGC` | 用例在树 | 设备侧 `pure-user-unlabeled.pdf` `/AIGC raw: None`（2.2） |
| C4 | 旧缓存"指纹已升版且无旧无标识产物" | **未按字面升 v3→v4**，改为策略代际哨兵 + 一次性清空 | **部分 / 与字面不同** |
| C5 | 显式图注逐位不变 | 用例以"注入后字节前缀 == 渲染器原始字节"断言 | UNVERIFIED（无设备行） |
| C6 | 设备 PDFium 接受增量段 | 生产路径注入后调 `validatedPageCount`（`PdfRenderer` 真实打开） | UNVERIFIED（无设备行） |
| C7 | 设备日志落盘（R4） | — | UNVERIFIED |
| C8 | 打印部分页 `writeSubset` 元数据丢失处置 | 只登记现状 | 未处置 |

出处：`docs/research/evidence/2026-11-04-batch-42/ws2-kd75-closeout.md:64-71`；结论行 `:73`「KD-75 不能关闭」。

### 2.2 KD-75 落点实读（本核对员逐行读源码）

| 事实 | 位置（本轮实读） |
|---|---|
| 注入点确实在 render → 大小判限 → `validatedPageCount` 之间 | `core/export/src/main/kotlin/.../MistakePdfExporter.kt:140`（`labelPdfInPlace(temporaryPdf, input)`） |
| 注入后字节再由 `PdfRenderer` 打开 | 同文件 `:141`（`validatedPageCount(temporaryPdf)`）→ `:175`（`PdfRenderer(descriptor).use`） |
| 代际哨兵常量 | 同文件 `:199` `internal const val AIGC_POLICY_SENTINEL = ".aigc-policy-gen-1"` |
| 哨兵清空两条路径 | `:123`（`prepare` 内、`findExisting` 之前）、`:163`（`reopenVerified` 内、`findExact` 之前） |
| C2 的硬编码 | 同文件 `:236` `hasModelParsedText = false`，KDoc `:230` 自述"补 (b) 判据时必须同时改哨兵代际名" |
| 判据函数 | `core/model/src/main/kotlin/.../model/pdf/PdfAigcContentPolicy.kt:29-38`（`shouldLabel = hasAiGeneratedImage \|\| hasModelParsedText`） |
| 输入无 provenance | `core/export/.../MistakePdfEligibility.kt:318-370`（`MistakePdfExportInput` 字段表：无 `blockEvidence`/provenance；`git grep` 在 `core/export/src/main` 对二者 **0 命中**，仅 androidTest 命中） |
| C4 字面版本**未**升版 | `MistakePdfEligibility.kt:186` 仍为 `"mistake-export-v3"`（`inputSha256` 的指纹前缀） |

设备侧证据（**转录**，非本轮执行）：E-42-DEVICE-01 §1——`core:export` androidTest `OK (5 tests)`、app `PdfAigcDeviceRoundTripInstrumentedTest` `OK (10 tests)`；原始 pypdf 读回本轮**实读**存在于盘上：

```text
build/device-track-batch42/ws2-pdf/pypdf-readback.txt
real-export-labeled.pdf pages=1 bytes=53271 sha256=77c6ba9c…
  /AIGC raw: '{"Label":"1","ContentProducer":"SmartMistakeBook","ProduceID":"586844f4…","ReservedCode1":"",…}'
  /AIGC keys: 7 ['ContentProducer','ContentPropagator','Label','ProduceID','PropagateID','ReservedCode1','ReservedCode2']
pure-user-unlabeled.pdf pages=1 bytes=40406 sha256=f48e046a…
  /AIGC raw: None
```

**KD-75 判定：维持 open。** 缺 C2（判据不可达，非执行问题）、C4 字面升版未做；C5/C6/C7/C8 无逐条设备判定行。

### 2.3 KD-76：A6 / C4 的**权威定义**与计划措辞的冲突

| 代号 | 权威定义 | 位置 | 现状 |
|---|---|---|---|
| **A6 两轮独立** | "关闭/维持证据要求两批独立冷启动均过 G1/G2" | `docs/research/2026-11-01-kd76-stats.md:235` | **未满足**：只有单批 n=20（且盘上有**两**个不同 APK 的单批，见下），不可相加 |
| **C4 死亡确认** | 链四段之 C4 = `abandon` → `awaitDeathConfirmation` 返回，预算 2 000 ms；毫秒分布**不存在** | `2026-11-01-kd76-stats.md:169`、`:273`（"C4 = `abandonForTest` 夹钟 + `confirmed`；毫秒分布待补 … **UNVERIFIED**"） | **UNVERIFIED**：代码里只有布尔 `deathConfirmed`，无夹钟 |

本轮实读的 C4 落点（证明"只有布尔、没有毫秒"）：

```text
app/src/androidTest/kotlin/.../visualjs/VisualJsConnectStageCalibrationInstrumentedTest.kt:516
    val deathConfirmed = engine.abandonForTest()      // 仅布尔，未夹 uptimeMillis
    assertTrue("$roundId INVALID: the worker death was not confirmed within the 2 s budget; …", deathConfirmed)
app/src/main/kotlin/.../visualjs/VisualJsClientEngine.kt:1205
    internal suspend fun abandonForTest(): Boolean    // 生产侧 seam，返回 confirmed
tools/ci/kd76_chain_segments.py:138-140  --c4-max-ms 由**调用方手工传入**，非实测产出
```

**冲突（本批必须先裁定，见 §7 F2）**：计划 §0 写「KD-76 open：… 缺 A6（两批独立）、C4（**产品进程首请求/其他 API**）」，把 C4 描述成"产品进程首请求"；而权威定义里 C4 是**死亡确认段**，"产品进程/UI 首请求"是 KD-76 的**③ 判据面**（`docs/known-defects.md:2950`、`:2998` 反复登记为 ③ 残余 UNVERIFIED）。两者不是同一项。按计划字面去补"产品进程首请求"不会让 C4 变绿。

### 2.4 KD-76 单批 n=20 的原始数字（本轮实读盘上产物，非转录）

```text
build/kd76-runs/ws3-batch42-20/summary.txt
samples: n=20/20 | G1: green
apkSha256: 35e10339c4606dabb17a8210763fba68a5d8acf1be67703793293b82d4bff1f8
bootCount(s): [3 … 22]        host boot_id(s): n=20 unique=20
mode(s): ['reboot']
M: min=849 p50=1336 p90=1660 p95=1889 max=2089
G2: PASS (M.p95=1889 ms <= 5000 ms)   G3: max=2089 ms
build/kd76-runs/ws3-batch42-20/boot-ids.txt → 20 行，sort -u → 20
```

- 与计划 §0「单批 n=20、p95=1889ms、boot_id 20/20 不同」**逐字相符**。
- 另有 `build/kd76-runs/ws3-batch42-r2/`（批次 42 记录 S16：不同 APK 的第二个单批）。**两份不可相加成 n=40**——A6 要的是同一构建两批独立冷启动。

**KD-76 判定：维持 open。** A6 未满足；C4 无 ms 分布；③ 产品进程/UI 首请求、29.5 s 链四段实测、低内存、arm64/真机口径仍缺（`known-defects.md:2950` 起各 Progress 块口径一致）。

---

## 三、isolation：三台设备的 `-4` 原文、assumption 条件、witness

### 3.1 `-4` 的含义与"假 OK"陷阱

`-4` = `INSTRUMENTATION_STATUS_CODE` 的**assumption failure**（`Assume.assumeTrue` 失败）。三台设备的共同形状是：`numtests=15`、`PASS(0)=0 / FAIL(-2)=0 / ERROR(-1)=0 / SKIP(-4)=15`，而 instrumentation 汇总**仍打印 `OK (15 tests)`**。

### 3.2 三台设备原文（逐行转录自 E 文件）

**A. emulator-5584（批次 41 / E-41-D05）** — `docs/research/evidence/2026-11-03-batch-41/step05-isolation-lowload.md:44-52`

| # | 结果 | code0 | skip(-4) | 自证探针原文 |
|---|---|---:|---:|---|
| 1 | `OK (15 tests)` Time 3.285 s | 0 | 15 | `verdict=UNGATED … own_cpu_ms=3190.0 probe_window_ms=3136 reason=own_cpu_busy` |
| 2 | `OK (15 tests)` Time 3.305 s | 0 | 15 | `own_cpu_ms=3170.0 probe_window_ms=3116 reason=own_cpu_busy` |
| 3 | `OK (15 tests)` Time 3.256 s（strictOffline，诊断观察） | 0 | 15 | `own_cpu_ms=3490.0 probe_window_ms=3100 reason=own_cpu_busy` |

同文件 `:52`：「**`OK (15 tests)` 在这里不表示 15 例通过**：runner 把 assumption 失败计为 `-4`，全部未执行。」

**B. emulator-5562（批次 42 WS4 / E-42-WS4-01）** — `docs/research/evidence/2026-11-04-batch-42/ws4-isolation-causality.md:151-157`

| run | 轮门 | numtests | PASS(0) | FAIL(-2) | ERROR(-1) | SKIP(-4) | 自证探针 |
|---|---|---:|---:|---:|---:|---:|---|
| r1-OFF | PASS | 15 | 0 | 0 | 0 | **15** | `UNGATED own_cpu_busy`（own 5650 ms / 窗 5046 ms） |
| r1-ON | PASS | 15 | 0 | 0 | 0 | **15** | `UNGATED own_cpu_busy`（own 5090 ms / 窗 4211 ms） |
| r2-OFF | INVALID | 15 | 0 | 0 | 0 | **15** | `shell_side_INVALID:la_ratio_p95_la_ratio_max` |
| r2-ON | INVALID | 15 | 0 | 0 | 0 | **15** | 同上 |
| r3-OFF | INVALID | 15 | 0 | 0 | 0 | **15** | 同上 |
| r3-ON | INVALID | 15 | 0 | 0 | 0 | **15** | 同上 |

臂开关有效：ON 臂 logcat 15 条 `SMB_T4_HOOK: begin`，OFF 臂 0 条；`SMB_ISOLATION_ARM: arm=OFF … test_side_hooks=false` / `arm=ON … =true`（同文件 `:159-165`）。

**C. emulator-5566（批次 42 设备轨 / E-42-DEVICE-01 §3）** — `docs/research/evidence/2026-11-04-batch-42/device-track-batch42.md:120-129`

| 臂 | 结果 | SKIP | 自证探针原文 |
|---|---|---:|---|
| OFF | `OK (15 tests)` | **15** | `UNGATED reason=jitter_p95_absolute`（jitter p95=36.7 ms；own_cpu=2060/窗 4888 ms） |
| ON | `OK (15 tests)` | **15** | `UNGATED reason=own_cpu_busy`（own_cpu=2900/窗 3613 ms） |

同一处的两条重要限定（原文）：① 该轨**未重跑** 5 min 标定/门，`-e smb.lowload.gate PASS` 是**合成判词**，故 `window=PASS` 不构成低负载门实测；② 报告 `REPORT_EXIT=1`、`verdict=UNCONVERGED`、`reasons` 含 `window_not_pass, instrumentation_ungated, rounds_short, skipped_or_unknown`、`missing_methods` 两臂均空、15 方法全 `SKIP`。

**三台设备的共同结论**：15 例在**三台**设备上都**零执行**；`own_cpu_busy` 根因未定案（批次 41 D05 在 5584、批次 42 WS4 在 5562 独立复现同形状）。

### 3.3 assumption 条件（源码实读，非转录）

链条：`@Before lowLoadGate()` → `IsolationGate.require()` → `Assume.assumeTrue(...)`。

| 环节 | 位置（本轮实读） |
|---|---|
| 每条用例前调用 | `app/src/androidTest/kotlin/.../visualjs/VisualJsIsolationInstrumentedTest.kt:141-144`（`@Before` / `fun lowLoadGate()` / `IsolationGate.require()`） |
| 单例 assumption | `app/src/androidTest/kotlin/.../visualjs/LowLoadProbe.kt:203-211`（`Assume.assumeTrue("SMB_LOWLOAD_GATE UNGATED: $failure", failure == null)`） |
| 失败码判定（fail-closed） | 同文件 `:215-240`：未传 `smb.lowload.gate` → `no_shell_side_verdict`；传了但非 `PASS` → `shell_side_<GATE>:<reason>`；`PASS` 但自证探针不过 → `instrumentation_self_probe:<reason>` |
| 自证探针 reason 枚举 | 同文件 `:130-139` 的 `val reason = when { … }`：`own_cpu_unreadable`、`probe_window_invalid`、`own_cpu_busy`、`low_memory`、`jitter_p95_over_3x_floor`、`jitter_p95_absolute`、`jitter_self_ratio` |
| 关键判据（本轮实测分支） | `:133` `ownCpuMs > OWN_CPU_LIMIT_FRACTION * windowMs -> "own_cpu_busy"` —— 三台设备命中的都是这一条 |
| 探针结果缓存 | 同文件 `:198`（`@Volatile private var cached`）+ `:201`（`cachedFailure`），整个 instrumentation 进程只测一次 |
| 臂声明 | `app/src/androidTest/kotlin/.../visualjs/IsolationArms.kt:48-76`（`IsolationRuntime.ensureDeclared()` 只读一次参数并打 `SMB_ISOLATION_ARM`） |

**缺口（对计划 item 19–21 的直接影响）**：`IsolationGate` 的缓存是"整进程一次"，所以 15 条 assumption 用的是**同一个**探针读数；一旦该读数为 `own_cpu_busy`，15 例必然全 `-4`，与用例行为无关。

### 3.4 witness 是否存在：**不存在（文件级）**

| 期望（计划 item 19） | 现状 |
|---|---|
| 测试内写 witness：**方法名 / 开始 / 结束 / CPU 窗**，证明用例真的执行 | **无**。`git grep -ni witness` 在 `app/src/androidTest/.../visualjs/` 的 `VisualJsIsolationInstrumentedTest.kt`、`LowLoadProbe.kt`、`IsolationArms.kt` 三文件对 `filesDir`/`writeText`/`FileOutputStream` **零命中**；无 witness 文件、无 XML 附件、无 JSON 落盘 |
| 现存的"最接近物" | `SMB_LOWLOAD_GATE: verdict=… reason=…`（每进程一条，`LowLoadProbe.kt:207/227`）；ON 臂的 `SMB_T4_HOOK: begin method=…` / `end method=… <时间戳>`（`IsolationArms.kt:100/115`） |

ON 臂那条标记的**证明力边界**（源码决定，非推断）：`T4SeamProbeRule.apply` 先打 `begin`、再 `base.evaluate()`（`IsolationArms.kt:96-104`），而 assumption 在 `@Before` 里抛异常 —— 所以 `begin` 只证明"该用例被调度进入"，**不证明用例体执行过**。这正是计划 item 19 要补的东西。OFF 臂连 `begin` 都没有。

**结论：witness BLOCKED（功能不存在）。** 三台设备的 `-4` 只能在"用例被调度"层面自证，不能在"用例体执行"层面自证。

---

## 四、`docs/current` 7 M 的 diff 归属

```
$ git status --porcelain -- docs/current
 M docs/current/README.md
 M docs/current/architecture-contract.md
 M docs/current/data-schema.md
 M docs/current/implementation-status.md
 M docs/current/model-task-contracts.md
 M docs/current/product-contract.md
 M docs/current/release-gates.md
$ git diff --stat -- docs/current
 7 files changed, 99 insertions(+), 5 deletions(-)
$ ls -l --time-style=long-iso docs/current/*.md   → 这 7 个 mtime 全为 2026-11-01 14:07
```

逐文件内容实读后判定：**7 个全部是他线（4E / PRD / UI 规划线），无一条属于本线（KD-75/76/isolation）**。

| 文件 | +/− | 内容（实读摘要） | 归属 |
|---|---|---:|---|
| `README.md` | +16 | 新增 `visual-generation-contract.md`、`figure-generation-prompt-policy.md` 索引与 2026-10-18/19/20 规划交接段（WG/PRD/UF/UL） | 他线（4E/UI 规划） |
| `architecture-contract.md` | +21 | `## Planned visual runtime (4E, not implemented)`：计划 `core:visual`/`core:visual-js` 边界与依赖图、`FigureGenerationCoordinator` 编排 | 他线（4E） |
| `data-schema.md` | +10 | `## Planned visual records (4E; not migrated)`：六张计划新表；正文仍写"Room 67 / 请求 17"（**已陈旧**，现 73/19） | 他线（4E） |
| `implementation-status.md` | +2 | 2026-10-18 绘图路线更新横幅 | 他线（4E） |
| `model-task-contracts.md` | +10/−1 | 删 "visual generate/review" 旧任务名 + 新增 `## Planned GENERATE_FIGURE extension (4E)`、2026-10-18 统一图策略 | 他线（4E） |
| `product-contract.md` | +15/−2 | ASK/AUTO_VISIBLE + `MasteryWriteGate` 措辞、`## Planned teaching figures (4E)`、`## Planned product-completion corrections (2026-10-19)` | 他线（4E/PRD） |
| `release-gates.md` | +25/−2 | `## Planned 4E enablement gate` / `## Planned product-completion gates` / `## Planned UI usability and visual gates`（2026-10-19） | 他线（4E/PRD/UI） |

对计划 item 45「契约 P0 patch 在 `docs/current` 清空时应用；否则出逐文件冲突清单」的实测结论：

```text
$ grep "^+++ \|^--- " docs/research/2026-11-04-contract-v1.1-p0-applicable.patch
--- a/docs/current/visual-generation-contract.md
+++ b/docs/current/visual-generation-contract.md        （仅 1 个文件）
$ git status --porcelain -- docs/current/visual-generation-contract.md   → 空（未修改，mtime 2026-10-29）
$ git apply --check docs/research/2026-11-04-contract-v1.1-p0-applicable.patch
GIT_APPLY_CHECK_EXIT=0
```

- patch **只触及** `visual-generation-contract.md`，而该文件**不在这 7 M 之列** → "逐文件冲突清单"**为空**，patch 当前可干净应用（`--check` exit 0，未应用、未写盘）。
- `docs/current` 是计划 §4 热区（禁改），所以本批仍只能"出 patch"；但"因冲突而不可应用"这个理由**不成立**，真实理由是热区纪律。
- 副作用提醒：`data-schema.md` 新增段落的"Room 67 / 请求 17"与当前 73/19 冲突。若日后整并入，会引入**两套版本号**，与 §1.5 四方门的 `schemaVersion/identityHash` 同源要求相悖。

---

## 五、计划剩余盘点与 S10 / S11 / S12 位置

### 5.1 盘点文件状态

| 项 | 值 |
|---|---|
| 文件 | `docs/research/2026-11-04-batch-42-inventory.md`（**已跟踪**，`git ls-files` 命中） |
| 体量 | 371 行 / 32,084 B（文件头自述，R4 上限 400 行/65536 B 未超） |
| 结构 | §0 口径、§1–§7 分线盘点（PRD/6C/4E/5B/5b/5c）、§8 剩余汇总、§9 分派（可做/需用户/外部/他线/热区）、§10 批次 43 草案 |
| §8 汇总（转录） | 剩余 **73** 项：**可做 30** / 热区 25 / 外部 6 / 需用户 5 / 他线 7 |
| §10 结论（转录） | 「非阻塞且非热区的剩余项只有 30 项…因此批次 43 无法诚实地再做"5× = 100 项"——要凑到 100 只能加水项，违反"不设水项"」 |

按 §8/§9 口径，本批（批次 43）落点与计划的 WS 划分**大体对齐**：WS1 KD-75 C2/C4 ↔ 4E-A2/P-07 残余；WS3 isolation ↔ 4E-A1（他线依赖：宿主低负载窗）；WS5 发版 ↔ 4E-A28/A29/A30/A32；WS6 计划收尾 ↔ §8–§10 本身。

### 5.2 S10 / S11 / S12 位置与现状（本轮逐条实核）

| # | 批次 42 登记位置 | 本轮实测位置 | 现状 |
|---|---|---|---|
| **S10** | `batch-42-record.md:409` 记「`known-defects.md:3294` 文段错位」 | **`docs/known-defects.md:3296`**（KD-83 段起于 `:3282`，其"关闭"块在 `:3294`，文件末行 `:3296` 是一段 **KD-75** 的批次 41 记录回链）；`:3294` 自己写着「本段之后仍挂有一段 … **文档错位，未在本次改动中移动**」 | **未修**。`grep -n "^## KD-"` 显示 `:3282` 之后**再无 KD 标题**，该 KD-75 段确实寄居在 KD-83 段内 |
| **S11** | `batch-42-record.md:410`「v4 夹具 golden 尚未被任何 CI 门消费」 | `tools/ci/check_backup_plan_sync.py` 全文**无** `backup_fixtures/v4` 引用；其 v4 断言只钉 `empty-full digest golden`（`ee9a1b4d…b006`）。真夹具 `tools/backup_fixtures/v4/closure4-schema72-mask15.smbk`（28,108 B，`sha256 24941ed7…f763ad`，本轮 `sha256sum` 实跑）的消费方只有 `V4PackFixtureTest`（JVM）与 `BackupV4PackRestoreInstrumentedTest`（设备），**没有任何 CI 门脚本** | **未修**，机制事实与登记一致 |
| **S12** | `batch-42-record.md:411`「isolation 15 例在三台设备上零执行」 | §3.2 三台设备逐行原文均 `skip(-4)=15`、`code0=0` | **未修**（本轮未跑隔离，仅核既有 E 文件） |

> S10 的行号从批次 42 登记的 3294 漂到目前的 **3296**，说明该段之上发生过行插入（本核对员**未**逐提交比对是哪一笔，属未核项）；**内容位置未变**（仍挂在 KD-83 段末，`:3282` 之后再无 `## KD-` 标题）。修 S10 时按内容定位，不按行号。

---

## 六、设备 / AVD / ps16k / arm64 / pypdf 可用性

### 6.1 当前在跑的三台模拟器（本轮 `adb devices` 实跑）

```text
$ adb devices
List of devices attached
emulator-5562   device
emulator-5566   device
emulator-5584   device
```

| serial | API | ABI | product | PAGE_SIZE | boot_id（本轮读） |
|---|---:|---|---|---:|---|
| emulator-5562 | 34 | x86_64 | sdk_gphone64_x86_64 | **4096** | `a0ebed2d-65fb-4421-9f93-d15d125ccfbf` |
| emulator-5566 | 34 | x86_64 | sdk_gphone64_x86_64 | **4096** | `8f54d45c-cd12-4e96-95a2-1656b3b382d1` |
| emulator-5584 | 34 | x86_64 | sdk_gphone64_x86_64 | **4096** | `01798878-aaea-4017-a33e-d6ece83c25b3` |

- 与批次 42 设备轨绑定的 boot_id 对照：`8f54d45c…`（5566）与 `01798878…`（5584）**未变**，说明这两台自批次 42 末轮后**未重启**——其上的"冷启动"结论不可复用为冷态证据。
- **三台同时在跑** = 批次 42 D1/D5 记录的共享设备竞争条件仍在（那正是 `own_cpu_busy` 的怀疑来源）。

### 6.2 AVD 与 16KB / arm64

```text
$ emulator -list-avds
api23_smoke  api23_smoke32  api23_smoke64  audit_user_visible_20261016
kb_test_device  ps16k_arm64  ps16k_x86_64  test_device
```

| AVD | abi.type | image.sysdir.1 | tag.id | 判定 |
|---|---|---|---|---|
| `ps16k_x86_64` | x86_64 | `android-36\google_apis_ps16k\x86_64\` | `page_size_16kb` | **可起**（与宿主同架构）；本轮**未起**（§6.3） |
| `ps16k_arm64` | arm64-v8a | `android-36\google_apis_ps16k\arm64-v8a\` | `page_size_16kb` | **硬阻塞**（下） |
| `kb_test_device` | x86_64 | `android-34\google_apis\x86_64\` | `google_apis` | 可起（三台在跑的都是这一类） |

arm64 可用性本轮做了**决定性探针**（90 s 超时、`-no-window -no-audio -no-snapshot -read-only`）：

```text
$ emulator -avd ps16k_arm64 -no-window -no-audio -no-snapshot -read-only -no-boot-anim
INFO  | Android emulator version 37.1.11.0 …
INFO  | Found systemPath C:\Android\Sdk\system-images\android-36\google_apis_ps16k\arm64-v8a\
FATAL | Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.
        System image must match the host architecture.
```

- 结论：**arm64 = BLOCKED（宿主架构级，非配置问题）**。系统镜像（`android-36/google_apis_ps16k/arm64-v8a`）与 AVD 配置都**在盘**，但 x86_64 宿主上 QEMU2 拒绝启动。与批次 42 R6 §0-4/§4.3 记载一致。
- 已装镜像清单：`android-23`（default/google_apis，x86/x86_64）、`android-34`（google_apis x86_64）、`android-36`（google_apis_ps16k，arm64-v8a + x86_64）。

### 6.3 16KB 运行时：**UNVERIFIED（本轮未起，且不建议本轮起）**

- 静态面（转录自 `docs/research/2026-11-04-release-readiness.md:23`）：两个 release APK `zipalign -c -P 16 4` exit 0；14 个 `.so` 全 `Stored`；逐 ELF `PT_LOAD p_align=0x4000` 14/14 PASS。
- 运行时面：**当前三台在跑的设备全是 `PAGE_SIZE=4096`**（§6.1 实测），`ps16k_x86_64` 未起。
- **本轮不新起第 4 台的理由（这是我的判断，不是事实陈述）**：计划纪律要求隔离/低负载类结论在"低负载窗"内取得；三台已在跑，再起一台会抬高宿主负载，直接劣化 `own_cpu_busy` 与后续 isolation/KD-76 窗口的可信度（批次 42 D1 已记录并发污染）。此判断已记入 §8 UNVERIFIED，未擅自执行。

### 6.4 pypdf / PyMuPDF

```text
$ python -c "import pypdf; print('pypdf', pypdf.__version__)"   → pypdf 6.16.2
$ python -c "import fitz; print(fitz.__doc__)"                  → PyMuPDF 1.28.2: … (fitz API deprecated warning)
```

- 两者都可用（KD-75 C1 的独立读者路径具备工具条件）。
- 既有独立读者产物在盘：`build/device-track-batch42/ws2-pdf/pypdf-readback.txt`（§2.2 已引原文，7 keys 齐全 / 负向 `None`）。

---

## 七、本批基线对计划的冲突与缺陷清单

> 只登记，不改计划文件、不改代码。严重度沿用批次 42 记录 §5 的口径。

| # | 发现 | 证据 | 严重度 | 处置建议 |
|---|---|---|---|---|
| **F1** | 计划自述 **100 = 6 + 74 + 20**（`:1`、`:131`），但 §2 实列 **78** 个连续编号（`python -I` 解析：`count 78 first (1,27) last (78,126) missing [] dups []`）→ 实际账 **6 + 78 + 20 = 104** | 本轮实跑解析；计划 `:24-126` | 中（口径） | 沿用批次 41/42 先例：记录按 78 行逐编号对账，把 "74/100" 登记为计划口径缺陷，不改计划文件。**这是第三批同族缺陷**（41: 55 vs 62；42: 74 vs 90） |
| **F2** | 计划 §0 把 KD-76 **C4** 写成「产品进程首请求/其他 API」，与权威定义（C4 = 死亡确认 2 000 ms 段）冲突 | 计划 `:10`；`docs/research/2026-11-01-kd76-stats.md:169`、`:273` | **高（判据）** | WS2 item 17「A6/C4 逐条对账」若按计划字面做，会拿"产品进程首请求"去顶 C4。需主会话先裁定：C4 按 stats 定义（`abandonForTest` 夹钟）执行，还是改判据名 |
| **F3** | 批次 42 §9.2 的回滚**只回滚了域层**：`BackupSelection.kt` 已还原（`ALLOWED_MASKS = linkedSetOf(9,13,15)`，`core/domain/.../BackupSelection.kt:166`），但**仍在树里的未提交改动**继续声明 0/8 合法 | ① `core/domain/.../BackupRepository.kt:33` KDoc「0/8 自 2026-11-03 起可创建」、`:58`「mask 0/8/9/13/15」；② `app/.../SecondaryScreens.kt:1195`「0/8 已合法」、`:1205-1210`「允许集合是闭包自洽的 {0,8,9,13,15}」、`:1249`「可单独备份」、`:1258-1259`「0/8 …必须如实命名」、`:1280-1290`/`:1312-1322` mask 0/8 文案分支；③ 行为层仍拒绝（`toggleBackupCategory` 两处 `candidate.mask in BackupSelection.ALLOWED_MASKS`，`:1221`/`:1231`；`SmbkArchiveCodec.kt:431`、`SmbkSelectionPlan.kt:78-81,639`、`BackupSnapshotTrimmer.kt:108` 全部拒绝 0/8；四方门输出 `allowedMasks == [9,13,15]`） | **高（纪律 + 文档一致）** | 行为层**无漂移**（可安全写"6C-2 未变"）；但 KRdoc/UI 与代码、与 WS9 item 63「三方一致断言」直接冲突。`core/domain` 是热区 → **本批不得改**，只能登记并由主会话裁定"先清这份残留 / 授权具体文件"。另：`:1149`（`enabled = enabled`）把原来的 `removable` 锁去掉后，点出 0/8 会被静默吞掉且**不再显示原因文案**，属 UX 回归 |
| **F4** | KD-75 C4 的字面升版仍未做，`"mistake-export-v3"` 原样在树 | `core/export/.../MistakePdfEligibility.kt:186` | 中 | WS1 item 4「缓存键/指纹含 AIGC 判据版本」若要按字面做，需改 `MistakePdfEligibility.kt`（`core/export` 内**第二个文件**），与计划 §4「只做 `MistakePdfExporter` 最小 hunk」冲突。沿用 R1 §0-5 的哨兵替代方案时，必须同步换哨兵名（`MistakePdfExporter.kt:229-231` KDoc 已写明这条耦合） |
| **F5** | isolation 的 witness 功能不存在 | §3.4 | **高（能力缺）** | 计划 item 19 是**新增**能力，不是"开关没打开"。落地前任何"15 例已执行"的表述都不成立 |
| **F6** | `docs/current` 7 M 是他线；契约 patch 目标文件不在这 7 M 内，patch 可干净应用 | §4（`GIT_APPLY_CHECK_EXIT=0`） | 低 | item 45 的"逐文件冲突清单"为空；不应用的理由只剩热区纪律。另 `data-schema.md` 新增段落写"Room 67/17"已陈旧（现 73/19），并入会造两套版本号 |
| **F7** | S10 位置漂移 + S11/S12 未修 | §5.2 | 低/中 | S10 按内容定位（KD-83 段末 KD-75 段）；S11 需给四方门加真夹具消费；S12 依赖 F5 的 witness 才能收敛 |

---

## 八、未验证 / 阻塞（不得写成通过）

| 项 | 状态 | 原因 |
|---|---|---|
| 远端 `origin/main` 此刻仍等于 `0fef9436` | **UNVERIFIED** | 本轮未 fetch（git 写 refs 被禁）；`.git/FETCH_HEAD` = 2026-11-01 16:02 |
| 12 模块单测门 / 强门 | **UNVERIFIED（本轮未跑）** | 纪律：Gradle 只在门禁阶段串行运行；本 ask 是基线核对 |
| KD-75 C1/C3/C5/C6/C7 的**设备**判定 | **UNVERIFIED（本轮未跑设备）** | 仅实读既有 E 文件与盘上 pypdf 产物 |
| KD-76 C4 毫秒分布 | **UNVERIFIED** | 代码只有布尔 `deathConfirmed`（§2.3） |
| KD-76 ③ 产品进程/UI 首请求、29.5 s 链实测、低内存 | **UNVERIFIED** | 与批次 42 口径一致，无新证据 |
| isolation 15 例真实行为 | **UNVERIFIED / BLOCKED** | 三台设备全 `-4`，零执行；witness 不存在（F5） |
| isolation T4 控制臂（OFF 生产回退构建） | **BLOCKED** | 无 OFF 产物（批次 42 §4.3）；需生产源码回退授权 |
| 16KB 页运行时（`ps16k_x86_64`） | **UNVERIFIED** | 本轮未起第 4 台（§6.3 理由）；既有三台 `PAGE_SIZE=4096` |
| arm64 运行时 | **BLOCKED（宿主架构）** | `FATAL \| Avd's CPU Architecture 'arm64' is not supported by the QEMU2 emulator on x86_64 host.` |
| AUD-08 人工金标 | **BLOCKED** | 需人工专家金标；门值 FAIL 维持（0.5714 < 0.9，转录） |
| 生产签名 / 真 Provider | **BLOCKED** | 需密钥与凭据；本批只出 runbook |

---

## 九、本轮实际执行的命令清单（可重跑）

```bash
git rev-parse HEAD; git rev-parse origin/main; git rev-list --left-right --count HEAD...origin/main
git log -1 --format="%h %ci %s" origin/main; ls -l .git/FETCH_HEAD; git remote -v
git status --porcelain; git status --porcelain -uall | wc -l; git diff --cached --name-only | wc -l
git status --porcelain -- docs/current core/domain core/ui core/export settings.gradle.kts
git diff --stat -- docs/current; git diff -- docs/current/{README,architecture-contract,data-schema,
  implementation-status,model-task-contracts,product-contract,release-gates}.md
git apply --check docs/research/2026-11-04-contract-v1.1-p0-applicable.patch     # exit 0，未应用
python -I -c "…json.load(open('…/schemas/…/73.json'))…"                          # 73/92/2/1d086050…
python -I -c "…re.match(r'^(\d+)\.\s', line)…" .zcode/plans/plan-2026-11-05-batch-43.md  # 78 项
python -I tools/ci/check_backup_plan_sync.py                                     # PASS / exit 0
sha256sum tools/backup_fixtures/v4/*.smbk
grep -rn "closure4-schema72|backup_fixtures/v4" tools/                           # 仅生成器，无 CI 门
adb devices; adb -s <S> shell getprop ro.build.version.sdk / ro.product.cpu.abi / getconf PAGE_SIZE
             adb -s <S> shell cat /proc/sys/kernel/random/boot_id
emulator -list-avds; emulator -avd ps16k_arm64 -no-window -no-audio -no-snapshot -read-only  # FATAL 原文
python -c "import pypdf; print(pypdf.__version__)"; python -c "import fitz; print(fitz.__doc__)"
```

> 本轮**未**执行：任何 Gradle、任何 `am instrument`、任何 APK 安装/构建、任何 git 写（含 fetch/commit/stash）、任何 emulator 启动、任何 Provider 调用。

---

## 十、体量自查与回链

- 回链：计划 `.zcode/plans/plan-2026-11-05-batch-43.md`（§1 六项研究、§2 实施、§3 流程、§4 硬约束、§5 验收）；上一批 `docs/research/2026-11-04-batch-42-record.md` §9.2/§9.3/§9.5；盘点 `docs/research/2026-11-04-batch-42-inventory.md` §8–§10；E 文件 `docs/research/evidence/2026-11-04-batch-42/{ws2-kd75-closeout,ws3-kd76-20-rounds,ws4-isolation-causality,device-track-batch42}.md`；`docs/research/evidence/2026-11-03-batch-41/step05-isolation-lowload.md`。
- 台账：`docs/known-defects.md`（KD-75 `:2879`/`:2906`、KD-76 `:2910`/`:2950`/`:2998`/`:3002`、KD-83 `:3282`/`:3294`，错位段 `:3296`）。
- 本文件不替代主会话裁定。可写的基线事实只有一句：**KD-75 open（C2 不可达、C4 未字面升版）、KD-76 open（A6/C4 未满足）、isolation UNCONVERGED 且 witness 不存在、6C-2 行为层未漂移但文档层有残留、发版就绪不成立**。
