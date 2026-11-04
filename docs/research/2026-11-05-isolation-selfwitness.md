# R3：`-4`（assumption）的三分类与 15 例自证判据

- 批次：43（2026-11-04/05），计划 §1 研究线 R3（item 19–22 的设计输入）
- 输入：`.zcode/plans/plan-2026-11-05-batch-43.md` §1/§2 WS3；`docs/research/2026-11-04-batch-42-record.md` §9.3/§9.5；`docs/research/2026-11-05-batch-43-baseline.md` §三
- 本文件**只做设计与判据**，不改任何代码；WS3 的落码留在 item 19–21。

## 0. 结论（先给答案）

1. **`-4` 单独看不能区分任何东西。** 它只说明"某条用例的 statement 抛了 `AssumptionViolatedException`"；JUnit 在抛之前已经发过 `testStarted`（`-r` 流的 `CODE:1`），而 `OK (N tests)` 依据的是 `failures.isEmpty()`，与 assumption **无关**——所以 15 例全跳过照样打印 `OK (15 tests)`。源码链见 §1，本仓实证见 §2。
2. **可区分的判据是三条独立证据的交叉**：① `-r` 流里的 start 事件与 `-4` 块里的 `stack` 首帧；② witness 的三级行（rule / gate / body）；③ 每例的墙钟窗与进程 CPU 窗。三者合起来能把 8 种观测态收敛到题目要的 3 类（未执行 / 环境不满足 / 夹具跳过），见 §3。
3. **witness 设计**：`IsolationWitness`（androidTest 新文件）+ 现有 `T4SeamProbeRule` 补两行 + `VisualJsIsolationInstrumentedTest` 15 个用例体各包一层 `witnessBody { }`。每例落 `method / t_begin / t_end / wall_ms / self_cpu_ms / cpu_delta_ms` + 效应字段，写 `targetContext.filesDir/isolation-witness/run-<stamp>/witness.jsonl`，logcat 同步镜像。见 §4。
4. **15 例判据**在 §5：每例给出结构下限（墙钟）与必现效应（体内可观测）。全绿一轮的 body 窗之和下限 **44 450 ms**；而本仓 r1-OFF 一臂的 15 例判词跨度只有 **820 ms**（§2.3）——这两个数差 54 倍，本身就是"零执行"的强反证。
5. **`-4` 根因**：本轮把候选收敛到三条（§6）。C1（进程级单次采样 + 缓存）有源码、logcat 计数与时间戳三重证据；C2（`own_cpu` 多线程累加 vs 单核分数阈值）是**推断**；C3（jitter 绝对阈值 30 ms 与模拟器读数 32.9/51.6 冲突）有读数事实，但"应否放宽阈值"需单独裁定。r1 的 shell 侧门是 PASS 而 instrumentation 否决——所以那批 `-4` **不是"宿主忙"的证据**，这一点必须写进任何台账。

---

## 1. `-4` 的定义链（源码级，逐跳可核）

### 1.1 从 `Assume` 到 JUnit 事件

`IsolationGate.require()` 在未通过时调 `Assume.assumeTrue(msg, false)`（`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/LowLoadProbe.kt:203-210`）。JUnit 4.13.2（`gradle/libs.versions.toml:24`）的 `ParentRunner.runLeaf`：

```java
// junit4 r4.13.2 ParentRunner.java:361-374
protected final void runLeaf(Statement statement, Description description, RunNotifier notifier) {
    EachTestNotifier eachNotifier = new EachTestNotifier(notifier, description);
    eachNotifier.fireTestStarted();          // ← 先发"开始"
    try { statement.evaluate(); }
    catch (AssumptionViolatedException e) { eachNotifier.addFailedAssumption(e); }
    catch (Throwable e) { eachNotifier.addFailure(e); }
    finally { eachNotifier.fireTestFinished(); }
}
```

要点：**`testStarted` 在 `statement.evaluate()` 之前触发**，而 `statement` 里包含 `@Before`（见 1.2）。所以"有 start 事件"只证明**被调度**，不证明用例体执行过——这与基线 §3.4（`2026-11-05-batch-43-baseline.md:254`）的结论一致，本文件给出源码依据。

### 1.2 为什么 `@Rule` 与 `@After` 都不能证明"体内执行过"

```java
// junit4 r4.13.2 BlockJUnit4ClassRunner.java:316-323
Statement statement = methodInvoker(method, test);
statement = possiblyExpectingExceptions(method, test, statement);
statement = withPotentialTimeout(method, test, statement);
statement = withBefores(method, test, statement);   // @Before
statement = withAfters(method, test, statement);    // @After 包住 @Before
statement = withRules(method, test, statement);     // @Rule 包住全部
```

- `@Rule` 在最外层：它的 `apply/evaluate` 在 `@Before` **之前**进入，因此 rule 的 "begin" 与 `CODE:1` 同级，不能证明体内执行。
- `withAfters` 包住 `withBefores`，且其 KDoc 明写"After methods **are always executed**: exceptions thrown by previous steps are combined"（`BlockJUnit4ClassRunner.java:381-394`）。本套件的 `@After closeEverything()`（`VisualJsIsolationInstrumentedTest.kt:152-158`）在 gate 跳过的 15 例上也都跑了——**`@After` 存在同样不构成体内证据**。
- 因此唯一的进程内"体内已进入"信号，只能由用例体自身写出（§4.3 的 `body_begin`），或由进程外解析 `stack` 首帧（§3）。

### 1.3 androidx 侧把 assumption 映射成 `-4`

```java
// androidx.test runner main: InstrumentationResultPrinter.java:80-94
REPORT_VALUE_RESULT_START = 1;
REPORT_VALUE_RESULT_OK = 0;
REPORT_VALUE_RESULT_FAILURE = -2;
REPORT_VALUE_RESULT_IGNORED = -3;
REPORT_VALUE_RESULT_ASSUMPTION_FAILURE = -4;
```

- `testStarted` 发 `1`，并把 `Description` 缓存"in case of a crash"（同上 `:123-142`）。
- `testAssumptionFailure` 把结果码置 `-4`，并把裁剪后的堆栈塞进 `stack` 键（同上 `:178-186`）。本仓 `r1-OFF.instrument.log` 的 `-4` 块正是这个形状（§2.2）。
- `reportProcessCrash` 用缓存下来的 `Description` 区分"进程在用例之前崩"与"在用例执行中崩"（同上 `:205-230`，文案 `Process crashed before executing the test(s)` / `Process crashed while executing …`）。

### 1.4 为什么 `OK (15 tests)` 是假 OK

`TestExecutor.execute` 用 JUnit 的 `Result` 汇总后交给 `TextListener`（`InstrumentationResultPrinter.java:240-246`）。而：

```java
// junit4 r4.13.2 TextListener.java:80-90
if (result.wasSuccessful()) { print("OK"); print(" (" + result.getRunCount() + " tests)"); }
// Result.java:109-111      wasSuccessful() = getFailureCount() == 0
// Result.java:141-143      testFinished → count++
// Result.java:146-148      testFailure → failures.add
// Result.java:156-158      testAssumptionFailure → assumptionFailureCount++（不进 failures）
```

即：**assumption 失败只增加 `assumptionFailureCount`，永远不进 `failures`**；`getRunCount()` 仍按 `testFinished` 计 15。于是 15 个 `-4` 打印 `OK (15 tests)`。

logcat 侧还有第二条假绿通道：`LogRunListener.testRunFinished` 用同一组计数打印

```java
// androidx.test LogRunListener.java:40-50
"run finished: %d tests, %d failed, %d ignored",
result.getRunCount(), result.getFailureCount(), result.getIgnoreCount()
```

assumption 既不算 failed 也不算 ignored，于是设备上真实打出了 `run finished: 15 tests, 0 failed, 0 ignored`（`build/ws4-isolation-b42/r1-OFF.logcat:1343`）。**这句话与 15 个 `-4` 同时存在，且不矛盾**——它是本批最容易误导人的一行。同一 `LogRunListener` 的 `:67` 会逐例打印 `assumption failed: <method>(<class>)`（本轮该文件命中 15 条，如 `00:48:23.181` 那条）——**这条给了"哪个用例被跳"，但没给"为什么跳"和"体内跑没跑"**，后两者仍分别归 `-r` 流的 `stack` 与 witness。

`INSTRUMENTATION_CODE: -1` 也不是用例结论：它是 instrumentation 自己的结果码（`RESULT_OK = -1`），由 `TextStatusReporter.onInstrumentationFinishedLocked` 原样打印（AOSP `frameworks/base/cmds/am/src/com/android/commands/am/Instrument.java:186-205`）。

---

## 2. 本仓实证（盘上产物，非转录）

> 这些文件在 `build/` 下（gitignored）。它们是**本轮的实测读数**，不是提交物；重跑命令见 §7。产物可能被下一次构建覆盖，引用时按"当时盘上内容"理解。

### 2.1 15 例全 `-4` 的原始流

`build/ws4-isolation-b42/r1-OFF.instrument.log`（295 行）逐块形状：

```
INSTRUMENTATION_STATUS: numtests=15
INSTRUMENTATION_STATUS: test=aHungWorkerReleasesTheCallerWithinHardPlus500ms
INSTRUMENTATION_STATUS_CODE: 1                     ← 调度（JUnit testStarted）
INSTRUMENTATION_STATUS: stack=org.junit.AssumptionViolatedException: SMB_LOWLOAD_GATE UNGATED: instrumentation_self_probe:own_cpu_busy
	at org.junit.Assume.assumeTrue(Assume.java:68)
	at com.tingyun.smartmistakebook.visualjs.IsolationGate.require(LowLoadProbe.kt:209)
	at com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest.lowLoadGate(VisualJsIsolationInstrumentedTest.kt:143)
INSTRUMENTATION_STATUS_CODE: -4                    ← 结论
```

计数（本轮实跑）：`CODE: 1` = 15，`CODE: -4` = 15，`numtests=15` 出现 30 次（每个 status bundle 一份）；两臂相同。

同一轮 logcat 的进程生命线（`r1-OFF.logcat`）：进程 8090 首行 `13.947`，`run finished: 15 tests, 0 failed, 0 ignored` 在 `24.058`——**整个 15 例 run 10.1 s**，其中 5.05 s 是探针自己的睡眠窗（`probe_window_ms=5046`）。也就是说，扣掉探针，15 例合计只用了约 5 s。

**注意 `stack` 在 `-4` 之前**：`isolation_pair_report.py` 的 docstring 已记录"用最近一个 `test=` 回填会把失败算到下一个用例头上"（`tools/ci/isolation_pair_report.py:8-11`）。同理，**按行号匹配堆栈会失效**：更早的 `build/ws4-isolation/manual-skip.log` 同一处是 `LowLoadProbe.kt:198`，当前源码是 `:209`。分类器必须按 `类名#方法名` 匹配。

### 2.2 witness 功能确实不存在

- 三文件（`VisualJsIsolationInstrumentedTest.kt` / `LowLoadProbe.kt` / `IsolationArms.kt`）对 `filesDir`/`writeText`/`FileOutputStream` 零命中（基线 §3.4，本轮复核一致）。
- 现存最接近物是 `SMB_T4_HOOK: begin/end`（`IsolationArms.kt:100,115`），但 `begin` 由规则在 `@Before` **之前**打印（`IsolationArms.kt:95-104`），只证明调度。

### 2.3 "零执行"的两个量化反证

1. **跳过轮的总时长**：`r1-OFF.logcat` 中 15 条缓存判词行（`verdict=UNGATED reason=instrumentation_self_probe:own_cpu_busy`）时间戳跨度 **820 ms**（`00:48:23.171 → 00:48:23.991`，本轮用脚本按 `MM-DD HH:MM:SS.mmm` 解析）。`build/ws4-isolation/manual-skip.log:290,292,295` 更直白：`Time: 0.262` / `OK (15 tests)` / `INSTRUMENTATION_CODE: -1`。
2. **全绿一轮的结构下限**：§5 的 15 例逐例下限求和 = **44 450 ms**。820 ms 与 44 450 ms 相差 54 倍；任何一例真跑过，都不可能落在这个区间。

### 2.4 shell 门 PASS 而 instrumentation 否决（关键矛盾）

`build/ws4-isolation-b42/gate-r1.json`：`verdict=PASS`、`reasons=[]`、`busy_p95=9.03`、`la_ratio_p95=0.1625`（本轮实读）。同一轮的 instrumentation 探针：

```
r1-OFF  SMB_LOWLOAD_GATE: verdict=UNGATED processors=4 jitter_p50_ms=7.5 jitter_p95_ms=51.6
        jitter_max_ms=177.8 own_cpu_ms=5650.0 probe_window_ms=5046 thermal_status=0 low_memory=false reason=own_cpu_busy
r1-ON   … own_cpu_ms=5090.0 probe_window_ms=4211 jitter_p95_ms=32.9 reason=own_cpu_busy
```

两臂 `own_cpu_ms / probe_window_ms` = **1.12 / 1.21**。`/proc/self/stat` 的 `utime+stime` 是**进程内所有线程之和**（`LowLoadProbe.kt:156-165` 只读这一对字段），所以比值 >1 不代表"宿主忙"，只代表"该进程此刻合计占用 >1 核"。判据 `ownCpuMs > 0.80 * windowMs`（`LowLoadProbe.kt:133`）按**单核分数**语义写的，对多线程进程天然接近不可满足。这是 §6 的 C2，标为**推断**。

同时，`jitter_p95_ms` 分别为 51.6 / 32.9，都超过 `ABSOLUTE_P95_LIMIT_MS = 30.0`（`LowLoadProbe.kt:62,137`）；只是 `own_cpu_busy` 在 `when` 链里先命中（`:130-140`），所以日志里看不到第二条。即 **C3 独立存在**，修掉 C2 也不会自动变绿。

---

## 3. 三分类的判据：8 个观测态 → 3 类

分类输入只有四样，全部可在不新增设备代码的前提下拿到：

| 证据 | 来源 | 能证明什么 |
|---|---|---|
| `CODE:1` / `CODE:-4` / `CODE:0` / `CODE:-2` / `CODE:-3` | `-r` 流 | 调度、终态 |
| `stack` 首帧（`类名#方法`） | `-r` 流 `-4` 块 | 在哪一层抛的 assumption |
| witness 行 `rule_begin / gate / body_begin / body_end` | `witness.jsonl` + logcat 镜像 | 进入点与退出点 |
| `wall_ms / cpu_delta_ms` | witness 行 | 体内是否消耗了时间与 CPU |

状态机（每方法每轮判一次，优先级自上而下；`irp` 是 `InstrumentationResultPrinter.java` 的简写，见 §1.3）：

| # | 观测组合 | 判定 | 归类 | 说明 |
|---|---|---|---|---|
| S1 | 无 `test=<m>` 块 **且** 无任何 witness 行 | `NOT_SCHEDULED` | **未执行** | 过滤器/roster 不匹配。与 `numtests` 和 `--expected-methods` 三方对账（`isolation_pair_report.py:300-322` 已 fail-closed） |
| S2 | 有 `CODE:1`（或 `rule_begin`）**但**无 `gate` 行，且终态缺失/进程崩溃文案 | `SCHEDULED_NOT_RUN` | **未执行** | `@Before` 前就死了（类初始化、runner 中止）。`reportProcessCrash` 文案可判（`irp:205-230`） |
| S3 | 终态 `-3`，无 `gate` 行 | `IGNORED` | **未执行（显式）** | `@Ignore`；当前套件零命中（本轮 `grep` 只命中注释） |
| S4 | 终态 `-4` + `stack` 首帧含 `IsolationGate#require`（`gate` 行可选；有行时必须 `verdict=UNGATED` 且无 `body_begin`） | `ENV_SKIP` | **环境不满足** | 唯一现存形态；`reason`/读数必须落盘，不能只有 `-4` |
| S5 | `gate` 行 `verdict=GATED` + 无 `body_begin` + 终态 `-4` + 首帧不是 gate | `FIXTURE_SKIP` | **夹具跳过** | 当前不可能出现（`@Before` 只有 gate 一个）；出现即"新增了未登记的前置" |
| S6 | `body_begin` + `body_end(outcome=ASSUMPTION)` + 终态 `-4` | `BODY_SKIP` | **夹具跳过（体内）** | 当前零命中；一旦出现，`skip.reason` 必须是登记过的 |
| S7 | `body_begin` + 无 `body_end` + 终态 `-2/-1` 或进程崩溃 | `BODY_CRASH` | **已执行未完成** | 与"跳过"严格区分：这例被计红，不是 SKIP |
| S8 | `body_begin` + `body_end` + 终态 `0/-2` | `EXECUTED` | **已执行** | `-2` 是执行过的红；`0` 是执行过的绿；都要过 §5 的效应判据 |

为什么这张表是"可判定"而不是"再猜一层"：

- `gate` 行**无条件写在 `Assume` 之前**（§4.2 的落点），所以"有 gate 行"= `require()` 真的进去了；"无 gate 行"排除 S4/S5，只剩进程层解释。
- `body_begin` 是**用例体的第一条语句**写出的，`@Before` 通过前不可能存在（§1.2）。所以它把 S4/S5 与 S6/S7/S8 切开。
- `stack` 首帧是 runner 自带的、与本设计无关的第三方证据；两条独立通道指向同一层才判 S4/S5。

反方向也要判：**有 witness 行但流里没有对应 `test=` 块**（logcat/`-r` 抓取被截断，或两份产物不是同一轮）→ `STREAM_INCOMPLETE`，不得当成 S1 或 S8。判据是"同一方法在两条通道里都要出现"，不是"任一条出现就算"。

**两级证据等级（必须写进消费端）**：

- `evidence=witness+stream`：witness 行齐全，可判 S1–S8 全部。
- `evidence=stream_only`：只有 `-r` 流（本批 r1 的产物就是这种）。此时只能判 S1/S3/S4 以及"`CODE:1` 但无终态"的 S2；S5/S6/S7/S8 **一律不可判**，必须输出 `UNDECIDABLE_NO_WITNESS` 而不是猜。这样既能让旧产物当回归夹具（§7 item 6），又不会把"没有 witness"洗成"已执行"。

"夹具跳过"的边界（本文件采用的定义）：**gate 通过之后、在夹具或体内因设备能力/前置不成立而 `Assume` 跳过**。今天整条隔离套件里没有任何 `Assume` 在 `@Before` 之外（本轮 `grep "Assume|assumeTrue|@Ignore"` 只命中 `:118`/`:139` 两行注释），所以 S5/S6 的**合法命中数应为 0**；任何非零都是"有人加了前置但没登记"，应报异常而不是记绿。

---

## 4. witness 设计

### 4.1 记录格式

一行一个 JSON 对象（JSONL），UTF-8，append-only（下例省略了中间的 72 行；绿轮总数 = 1 `suite_start` + 1 `canary` + 15×5 + 1 `suite_end` = 78）：

```json
{"v":1,"seq":1,"phase":"suite_start","run":"r1-OFF-20261105T101500Z","pid":8090,"uid":10123,
 "arm":"OFF","pkg":"com.tingyun.smartmistakebook.offline","numtests":15,
 "apk":{"source_dir":"/data/app/…/base.apk","sha256":"…","size":…,"version_code":…,"last_update_time":…},
 "t_ms":123456,"cpu_ms":840}
{"v":1,"seq":2,"phase":"rule_begin","method":"aHungWorkerReleasesTheCallerWithinHardPlus500ms","t_ms":123460,"cpu_ms":841}
{"v":1,"seq":3,"phase":"gate","method":"…","verdict":"UNGATED","reason":"instrumentation_self_probe:own_cpu_busy",
 "shell_gate":"PASS","shell_reason":"","cached":true,
 "reading":{"processors":4,"jitter_p50_ms":7.5,"jitter_p95_ms":51.6,"jitter_max_ms":177.8,
            "own_cpu_ms":5650.0,"probe_window_ms":5046,"thermal_status":0,"low_memory":false},
 "t_ms":123461,"cpu_ms":842}
{"v":1,"seq":4,"phase":"body_begin","method":"…","t_ms":123470,"cpu_ms":843,"t_ns":918273645}
{"v":1,"seq":5,"phase":"body_end","method":"…","outcome":"PASS","t_ms":125310,"cpu_ms":1204,
 "wall_ms":1840,"cpu_delta_ms":361,"threads":22}
{"v":1,"seq":78,"phase":"suite_end","rows":78,"expected_rows":78,"t_ms":190000,"cpu_ms":9000}
```

字段约定：

- `t_ms` = `SystemClock.uptimeMillis()`（与用例内计时同钟，`VisualJsIsolationInstrumentedTest.kt:540-542` 同源）。
- `cpu_ms` = `/proc/self/stat` 的 `utime+stime` 换算（复刻 `LowLoadProbe.kt:156-165` 的 `USER_HZ=100`）；读不到写 `-1` + `cpu_unreadable:true`，**不回退成 0**（0 会被误读成"没干活"）。
- `wall_ms` / `cpu_delta_ms` 由消费端相减得出，写入端也可直接写（双写便于人工核）。
- `cached` 标记该 `gate` 行是否复用了进程级缓存（`LowLoadProbe.kt:198-201`）。这个字段是 C1 的显微镜：今天 15 行全是 `cached=true`，且只有 1 行带读数。
- `outcome ∈ {PASS, FAIL, ASSUMPTION, EXCEPTION}`；崩溃不会由写入端产生（进程没了），由消费端从"缺 `body_end`"推出。

### 4.2 写入点（三级，各自证明不同的东西）

| 级别 | 落点 | 写什么 | 证明 |
|---|---|---|---|
| L0 | `T4SeamProbeRule.apply`（`IsolationArms.kt:95-121`）入口/`finally` | `rule_begin` / `rule_end` | 该用例被调度进入（与 `CODE:1` 互为独立通道） |
| L1 | `IsolationGate.require()` 开头，`Assume` **之前** | `gate`（含 `verdict/reason/reading/cached`） | `@Before` 真的进了门；为什么没通过 |
| L2 | 每个用例体第一句 / `finally` | `body_begin` / `body_end` | `@Before` 已通过、体内确实执行；墙钟与 CPU 窗 |
| L3 | 只在夹具/体内 `Assume` 包装器里 | `skip`（`stage=fixture` 或 `stage=body`，带 `reason`） | "夹具跳过"有归因（今天为 0） |
| L4 | `@BeforeClass`（或首次 `require()` 懒初始化） | `suite_start` + **canary 自检** | 写入端可用；APK 身份绑定 |
| L5 | `@AfterClass` | `suite_end` + 行数与期望行数 | 写入端完整收尾；缺行可判 |

**canary 自检（必须有）**：`suite_start` 之后立刻写一行 `phase=canary`，再把它读回并比对 `seq`。目的很具体：如果没有这次自检，"witness 文件为空"会被当成"没执行"，而它也可能是 `EACCES`/磁盘满/目录没建。本仓已有这种失败的实测先例——测试包自己的 `filesDir` 在设备上不可写会 EACCES（`PdfAigcDeviceRoundTripInstrumentedTest.kt:74-78` 的注释，实测见 `docs/research/2026-11-03-instrumentation-pdf-verification.md:268-275`）。canary 失败时写 `witness_error` 字段并把该轮标 `WITNESS_UNAVAILABLE`，**不得**降级成"零执行"。

**为什么不用 `TestWatcher`**：4.13.2 的 `TestWatcher.apply` 捕获 `AssumptionViolatedException` 后不直接抛，而是塞进 `errors`，最后由 `MultipleFailureException.assertEmpty(errors)` 重新抛出（`TestWatcher.java` apply/`skippedQuietly`；`MultipleFailureException.java:87-102`）。单条错误会原样 rethrow（仍是 `-4`），但**一旦 witness 回调自己也抛错凑成两条，就会包成 `MultipleFailureException` → runner 记 `-2`**。所以沿用手写的 `T4SeamProbeRule`（`IsolationArms.kt:95-121`，直接 `base.evaluate()` + `finally`），并规定 witness 写入**永不向外抛**。

### 4.3 最小 diff 形状（设计，不在本文件落地）

```kotlin
// 15 个用例体各一行包装；方法名由 rule 传入，避免 15 处字符串漂移
@Test
fun workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater() = witnessBody {
    runBlocking {
        val harness = harness()
        …
    }
}

// IsolationWitness.kt（androidTest 新文件）
internal inline fun witnessBody(block: () -> Unit) {
    val method = IsolationWitness.currentMethod   // 由 rule 的 apply() 在 @Before 前写入
    val t0 = IsolationWitness.mark(method, "body_begin")
    var outcome = "EXCEPTION"
    try {
        block()
        outcome = "PASS"
    } catch (a: org.junit.AssumptionViolatedException) {
        outcome = "ASSUMPTION"; throw a          // 重新抛出，保持 -4 语义（设备栈实测就是该类型）
    } finally {
        IsolationWitness.markEnd(method, t0, outcome)   // 内部 runCatching，绝不抛
    }
}
```

绑定：`T4SeamProbeRule.apply` 里加 `IsolationWitness.currentMethod = description.methodName`（`IsolationArms.kt:95-99` 处，无条件，不依赖 arm）。这样 15 个方法名只有一个来源（`Description`），与 `tools/ci/visual_js_isolation_methods.txt` 的 15 行互为校验。

写入文件：`InstrumentationRegistry.getInstrumentation().targetContext.filesDir/isolation-witness/run-<stamp>/witness.jsonl`。
- 用 `targetContext` 而不是 `instrumentation.context`：instrumentation 跑在被测应用 uid 下，测试包自己的 `filesDir` 属另一 uid（`PdfAigcDeviceRoundTripInstrumentedTest.kt:56-60` 的实测注释）。
- 每行 `flush()` + `FileDescriptor.sync()`：绿轮 78 行量级（1 suite_start + 1 canary + 15×5 + 1 suite_end），开销可忽略；换来"进程被 SIGKILL 时最后一行不丢"。
- logcat 镜像：`Log.i("SMB_ISOLATION_WITNESS", ...)`，与既有 `SMB_LOWLOAD_GATE:` 前缀不冲突（`isolation_pair_report.py:132-146` 的 `parse_gate_lines` 只切 `SMB_LOWLOAD_GATE:`）。

### 4.4 CPU 窗的定义与**不作为门**的红线

`body` 窗 = `(t_begin, t_end, wall_ms, cpu_delta_ms)`：

- `wall_ms = t_end - t_begin`（`SystemClock.uptimeMillis`）。
- `cpu_delta_ms` = 进程 `utime+stime` 差（`/proc/self/stat`，`USER_HZ=100`）。
- 判读只做两件事：① `wall_ms` 与 §5 的结构下限比（证明睡过/等过）；② `cpu_delta_ms >= 1`（证明体内有真实工作，而不是空转一个 if）。
- **红线**：`cpu_delta_ms / wall_ms` 只记录、**绝不设阈值**。本批已经有一份教训：`own_cpu_busy` 用单核分数判多线程进程（§2.4），把 5584 的三轮、5562 的 r1 两臂、5566 的 ON 臂都挡成 `-4`（基线 §3.2 表）；witness 若再引入同类阈值，就是把同一个 bug 搬到第二个位置。
- 环境负载的判定权仍然只在 shell 侧 `tools/ci/lowload_gate.py`（它读 `/proc/stat|loadavg|pressure`，instrumentation 读不到——`LowLoadProbe.kt:16-32` 的 KDoc 已写明）。witness 只回答"这一例跑没跑、跑了多久、期间本进程花了多少 CPU"。

### 4.5 它消灭的具体失败（新增机制的正当性）

| 编号 | 被消灭的具体失败 | 现状证据 |
|---|---|---|
| W1 | `OK (15 tests)` 被当"15 例通过" | §1.4 + `manual-skip.log:292` |
| W2 | `-4` 无法归因到"未执行 / 环境 / 夹具" | 基线 §3.4：witness 不存在 |
| W3 | 进程级单次探针把 15 例绑在同一个读数上 | `LowLoadProbe.kt:198-201`；logcat 1 条读数 + 15 条缓存判词 |
| W4 | witness 写入失败被误读成"零执行" | `PdfAigc…Test.kt:74-78` 的 EACCES 先例 |
| W5 | 方法清单/判据两处维护导致漂移 | `visual_js_isolation_methods.txt:1-4` 已要求"改 `@Test` 时同步本文件" |
| W6 | 红/绿只看 `-4/-2`，不看该例是否真产生过效应 | 本文件 §5 每例的"必现效应"列 |

### 4.6 拉取与消费

```bash
adb -s <serial> exec-out run-as com.tingyun.smartmistakebook.offline \
  cat files/isolation-witness/run-<stamp>/witness.jsonl > build/<run>/witness.jsonl
```

- `run-as` 只对 debug 构建可用；本仓实测 API 23 / API 34 均 `rc=0`，文件权限 `-rw-------`（`docs/research/2026-11-03-instrumentation-pdf-verification.md:268-275,481-482`）。
- 包的 flavor 决定包名：`applicationIdSuffix = ".offline" / ".localfirst"`（`app/build.gradle.kts:27,54,59`），默认 `com.tingyun.smartmistakebook.offline`。
- `<stamp>` 由 runner 通过 `-e smb.witness.run <label>` 注入（`run_isolation_pair.sh:283-292` 的 `am instrument` 处加一个 `-e`），保证主机侧 run 标签与设备侧目录一一对应；缺参数时设备侧自生成，并在 `suite_start` 里写 `run_source=generated`。

消费端建议**新建** `tools/ci/isolation_witness_check.py`，不改 `isolation_pair_report.py` 的既有语义（后者是 F7 修复的一部分，`:300-346` 的 fail-closed 已定稿）。新工具职责单一：

```
classify(method, rows, stream) -> S1..S8 + reasons[]
check_expectations(method, rows) -> 逐例 floor/效应检查
```

`isolation_pair_report.py` 保持"`SKIP`/`UNKNOWN` → `skipped_or_unknown` → `UNCONVERGED`"（`:341-346`）不变；witness 的作用是让上游先证明"这一轮到底有没有执行"，而不是放宽这条判定。

---

## 5. 15 例逐例可自证判据

前提：所有轮次都要求 L0–L2 行齐全（`rule_begin`→`gate`→`body_begin`→`body_end`），且 `body_end.outcome=PASS`。下表只列**该例额外**必须成立的量。`floor` 是 `wall_ms` 下限（毫秒）；`0` 表示没有结构性等待，只能靠效应与 `cpu_delta_ms>=1` 区分。

| # | 方法（源码） | floor | 必现效应（体内可观测，逐条对应断言） | 反证（出现即判未执行/异常） |
|---|---|---:|---|---|
| 1 | `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater` (`:171`) | 1750 | `service.workerUid() != Process.myUid()`；`protocolVersion` 相等；三拍 execute = `RESULT_OK`+`scene-1` / `RESULT_REJECTED`+`INVALID_PROGRAM` / `RESULT_OK`（`:177-214`） | 无 `body_begin`；`wall_ms<1750`（`delay(1800)` 在 `:207`） |
| 2 | `anUnattributedEngineFailureKeepsItsPrefixAcrossTheAidlWire` (`:228`) | 0 | `RESULT_REJECTED`+`INVALID_PROGRAM`；`diagnostics` 以 `[UNATTRIBUTED]` 开头；AIDL 参数与信封字段逐字相同（`:241-261`） | `cpu_delta_ms<=0`（等于没跑过 throw-null 往返） |
| 3 | `aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp` (`:265`) | 200 | `STATUS_STALE_GENERATION`；stale terminate 后 `currentGeneration()` 不变；live execute `RESULT_OK`（`:275-289`） | `wall_ms<200`（`delay(250)` 在 `:280`） |
| 4 | `theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` (`:293`) | 1400 | 6 s 内 `deathSignal()` 完成；hung 结果 ≠ `RESULT_OK`；新进程 `RESULT_OK`（`:303-334`）；logcat 该例有 1 条 `watchdog hard deadline` kill（`isolation_pair_report.py:73-77` 的 must-kill 集） | 无 kill；`wall_ms<1400`（watchdog ≈1500，`VisualExecutionEngine.kt:126`） |
| 5 | `theClientRebuildsAtMostOnceAfterConfirmedAbandons` (`:341`) | 0 | `connectionCount` 1→2；两次 `abandonForTest()` 均 true；第二次后 `connectForTest()` false（`:344-366`）；logcat 该例 `terminate` ≥2（`isolation_pair_report.py:78-81`） | 只有 1 次 terminate；`connectionCount` 停在 1 |
| 6 | `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram` (`:376`) | 7500 | 首次 execute 即 `Rendered`；`connectionCount==1`（`:381-387`） | `wall_ms<7500`（attach 延迟 `7600`，`:925`）——这一条最硬，跳过的实例不可能满足 |
| 7 | `theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` (`:391`) | 1400 | 首次 `HARD_DEADLINE_EXCEEDED`；重建后 generation 变化；同 digest 无重试 `PROGRAM_FUSED`；`Rendered`；再死；`connectionCount==2`；末次 `ENGINE_CLOSED`（`:400-444`） | 重建次数 ≠2；末态不是 `ENGINE_CLOSED` |
| 8 | `anExplicitRetryOfAnInjectedFusedProgramRendersAndReleasesTheFuse` (`:471`) | 0 | 注入 digest 后：`PROGRAM_FUSED` → `Rendered` → `Rendered`（`:479-496`）；零 kill | 末拍 `PROGRAM_FUSED`（解除缺失）；出现 kill |
| 9 | `aHungWorkerReleasesTheCallerWithinHardPlus500ms` (`:527`) | 1400 | `Rejected`+`HARD_DEADLINE_EXCEEDED`；`diagnostics` 含 `died`；`elapsed<=2000`（`hard 1500 + 500`）；`connectionCount==2`；fresh `Rendered`（`:544-588`） | `elapsed<1400`（不可能是真硬期限路径）；诊断不含 `died` |
| 10 | `aMalformedCallerRequestIsRejectedWithoutTakingTheFlightSlot` (`:599`) | 0 | 三次 `STATUS_BAD_REQUEST`（null requestId / descriptor / callback）+ 末拍 `RESULT_OK`（`:610-639`）；零 kill | 末拍非 `RESULT_OK`（槽位被负向请求占住） |
| 11 | `anOverBudgetRequestEnvelopeIsRejectedByTheBoundedRead` (`:647`) | 0 | 300 KiB 信封 → `INPUT_OVER_BUDGET`；随后 `RESULT_OK`（`:655-668`） | 拒绝理由不是 `INPUT_OVER_BUDGET`（说明有界读没走到） |
| 12 | `aSecondConcurrentRequestIsRejectedBusyWhileTheFirstIsInFlight` (`:677`) | 1400 | 第一请求 `STATUS_ACCEPTED`；第二请求 `STATUS_BUSY`；6 s 内死亡（`:687-707`）；该例 1 条 watchdog kill | 第二请求非 `BUSY`；无 kill（说明第一请求没真的挂住） |
| 13 | `anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled` (`:719`) | 0 | 首拍 ∈ {`SHUTTING_DOWN`,`RESULT_OK`,`RESULT_REJECTED`}；ready 后仍活（`!deathSignal().isCompleted`）；再拍 `RESULT_OK`（`:725-743`）；零 kill | ready 前出现死亡；出现 watchdog kill |
| 14 | `aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching` (`:753`) | 29000 | 两次均 `Rejected`+`ENGINE_CLOSED`；`diagnostics` 含 `loading`；`unavailableForTest()` 两次 false；`connectionCount==0`（`:757-785`） | `wall_ms<29000`（两次首连预算各 `15000`，`VisualJsClientEngine.kt:689,1301`） |
| 15 | `aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds` (`:796`) | 400 | `ENGINE_CLOSED`，诊断含 `not become ready` 或 `loading`；`connectionCount` 1→2；`unavailable` false（`:801-831`）；该例零 watchdog kill、零 terminate | `connectionCount==1`（未重新 bind）；出现 kill（fake binder 路径不应有进程被杀）；logcat 出现 `:visual_js` 的 ready 日志（说明夹具被绕过，应报异常而非记绿） |

合计 floor：**1750+200+1400+7500+1400+1400+1400+29000+400 = 44 450 ms**。这是"15 例全绿"的必要条件，不是充分条件；但它已经足以否证本批 r1 的 820 ms（§2.3）。

### 5.1 判据的机器形态（建议的 expectations 单一来源）

`tools/ci/isolation_witness_expectations.json`（15 条，与方法清单一一对应）：

```json
{"method":"aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram",
 "floor_ms":7500,
 "require_effects":["first_execute_rendered","connection_count==1"],
 "forbid":["watchdog_kill"]}
```

理由与 `tools/ci/visual_js_isolation_methods.txt:1-4` 同源：方法清单已经因为"改 `@Test` 忘同步"吃过一次假绿（报告工具的 `method_missing` 就是为此设的）。判据若写死在 Python 里、方法名写在 txt 里、理由写在 md 里，就是三处漂移；把"每例的下限与效应"放一份 JSON，md 只解释为什么，检查器直接消费它。新增该文件的正当性：消灭 W5（§4.5 表）。

---

## 6. `-4` 的根因分析（候选 + 证据强度 + 谁来裁定）

| 编号 | 候选 | 证据 | 强度 | 裁定方式 |
|---|---|---|---|---|
| C1 | 探针只测一次并缓存（进程级），15 例共用同一读数；且唯一一次测量发生在首次 `require()`——进程 8090 首行 `13.947`、判词行 `23.170`（探针窗 5046 ms，故测量起于 ~`18.12`，即进程启动后约 4 s） | `LowLoadProbe.kt:197-201`（`cached`/`cachedFailure`）+ `:224-228`（仅在 `cached==null` 时测）；`r1-OFF.logcat` 1 条带读数行 + 15 条仅 reason 行；跨度 820 ms | **事实**（源码 + 计数 + 时间戳） | 无需裁定；witness 每例写 `cached` 即可长期暴露 |
| C2 | `ownCpuMs > 0.80 * windowMs` 把"多线程 CPU 之和"当"单核占用分数"，对多线程进程不可满足 | 读数 5650/5046、5090/4211（比值 1.12/1.21）；`/proc/self/stat` 的 utime+stime 是全线程之和（`LowLoadProbe.kt:156-165`）；阈值单核语义（`:65,133`） | **推断**（读数确定，语义解释需实测） | item 20/21：在探针窗口内同时读 `/proc/self/task/*/stat` 拆线程，或改判据为 `ownCpu/(window*processors)`；**改判据前先跑 witness**，否则又是"改门迁就绿" |
| C3 | jitter 绝对阈值 30 ms 与模拟器地板不匹配 | r1-OFF p95=51.6 / r1-ON p95=32.9 vs `ABSOLUTE_P95_LIMIT_MS=30.0`（`:62,137`）；两臂都越界，只是被 `own_cpu_busy` 抢先命中（`:130-140`） | **事实**（读数与阈值都是确定值） | 同上；注意 `-e smb.lowload.jitter_p95_floor_ms` 存在但 runner 未传（`run_isolation_pair.sh:283-292` 只传 gate/reason/arm） |
| C4 | 宿主或模拟器真的忙 | r1 shell 门 `verdict=PASS`、`busy_p95=9.03`、`la_ratio_p95=0.1625`（`gate-r1.json`） | **被否**（同轮同机） | — |

结论：**r1 的 `-4` 归因是 `instrumentation_self_probe:own_cpu_busy`，其阈值语义未定案；不能写成"宿主忙导致未执行"，也不能写成"环境不满足"了事。** r2/r3 的 `-4` 是另一条路径（`shell_side_INVALID:la_ratio_p95_la_ratio_max`，基线 §3.2 表），那条确实是 shell 门判 INVALID——两类必须分开记账，这正是 witness 的 `reason` 字段要保留原始串的原因。

---

## 7. 落点（WS3 item 19–22 的最小清单）

单块 ≤6 路径，按顺序：

1. `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/IsolationWitness.kt`（新）——schema、canary、`witnessBody`、`mark`、sync。
2. `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/IsolationArms.kt`——`T4SeamProbeRule.apply` 两行（设 `currentMethod`；写 `rule_begin`/`rule_end`），不动臂语义。
3. `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt`——`lowLoadGate()` 里 gate 行（1 行调用）+ 15 个用例体包 `witnessBody`（机械改动，断言零改动）。
4. `tools/ci/isolation_witness_check.py`（新）——S1–S8 分类 + expectations 校验 + `--fail-on` 退出码。
5. `tools/ci/isolation_witness_expectations.json`（新）——15 条判据（§5.1）。
6. `tools/tests/test_isolation_witness_check.py`（新）——用 §2 的真实产物做夹具（`stream_only` 档）：断言 15 个 `-4` 全部判 `ENV_SKIP` 而不是 `NOT_SCHEDULED`；断言 820 ms 轮在 `floor` 检查下失败；再用合成行断言"缺 `body_end` → `BODY_CRASH`"与"有 gate 无 body → `FIXTURE_SKIP`"。

`tools/ci/run_isolation_pair.sh` 的改动（`-e smb.witness.run` + 拉取 + 交给检查器）单独一块，避免与上面 6 条混在同一提交。

**验证方式**（先红后绿，按全局纪律）：

- 检查器单测：`python -I -m pytest tools/tests/test_isolation_witness_check.py -q`（JVM 之外的纯 Python，可直接跑）。
- 编译面：`./gradlew :app:compileLocalFirstDebugAndroidTestKotlin` 与 `:app:compileOfflineDebugAndroidTestKotlin`（强门阶段串行跑，不走 connected 门）。
- 设备面：`tools/ci/run_isolation_pair.sh` 单臂重复（§9.3 第 1 层），禁止 `:app:connectedLocalFirstDebugAndroidTest`。

---

## 8. 来源（采纳 / 拒绝）

| 来源 | 取用内容 | 采纳理由 |
|---|---|---|
| `https://raw.githubusercontent.com/junit-team/junit4/r4.13.2/src/main/java/org/junit/runners/ParentRunner.java`（`:361-374`） | `runLeaf` 先 `fireTestStarted` 再 `evaluate`；assumption → `addFailedAssumption` | 与仓库实际依赖版本一致（`libs.versions.toml:24`=4.13.2），直接支撑 §1.1 |
| 同上 `BlockJUnit4ClassRunner.java`（`:303-324`、`:381-394`） | rule > after > before 的包裹顺序；`@After` 总会执行 | 支撑 §1.2，是"rule/after 不能证明体内执行"的唯一依据 |
| `…/junit4/r4.13.2/src/main/java/org/junit/runner/Result.java`（`:109-111,141-158`） | `wasSuccessful()=failures==0`；assumption 单独计数 | 支撑 §1.4 |
| `…/junit4/r4.13.2/src/main/java/org/junit/internal/TextListener.java`（`:80-90`） | `OK (N tests)` 的打印条件 | 与 `manual-skip.log:292` 逐字吻合 |
| `…/junit4/r4.13.2/src/main/java/org/junit/rules/TestWatcher.java`、`…/runners/model/MultipleFailureException.java`（`:87-102`） | TestWatcher 收集错误后经 `assertEmpty` 重抛；单条原样、多条包成 `MultipleFailureException` | 支撑 §4.2"不用 TestWatcher"的风险判断 |
| `https://raw.githubusercontent.com/android/android-test/main/runner/android_junit_runner/java/androidx/test/internal/runner/listener/InstrumentationResultPrinter.java`（`:59-94,123-152,154-186,205-246`） | `-4`/`1` 常量、start 缓存 Description、`stack` 键、崩溃文案、`TextListener` 收尾 | 与设备实测一致（§2.1）；版本差异不改变这些常量语义（runner 1.7.0，`libs.versions.toml:26`） |
| `…/androidx/test/internal/runner/RunnerArgs.java`（`:59,85`） | `-e log`（`ARGUMENT_LOG_ONLY`）与 `listTestsForOrchestrator` 存在 | **仅登记，不采纳为判据**：`-e log true` 的 dry-run 输出形状本轮未在任何设备上验证，标 UNVERIFIED，不作为 roster 证据（roster 用 `numtests`+`expected-methods` 已足够） |
| `https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/cmds/am/src/com/android/commands/am/Instrument.java`（`:160-205`） | `-r`/pretty 打印、`INSTRUMENTATION_CODE` 只是 instrumentation 结果码 | 支撑 §1.4 末段；master 分支，语义为打印逻辑，风险低（设备实测 `-1` 吻合） |
| 本仓 `docs/research/2026-11-03-instrumentation-pdf-verification.md:268-275,481-482` | `run-as` 在 debug 包 rc=0、文件 `-rw-------` | 支撑 §4.6；同一设备群、同一年内实测 |
| `app/build.gradle.kts:27,54,59`、`gradle/libs.versions.toml:24-27` | 包名后缀、JUnit/runner 版本 | 版本兼容核对（11.3 要求） |

拒绝项：无来源给出"`-4` 一定代表用例体未执行"的权威表述；本文件因此**不用"未执行"这个词做 `-4` 的同义词**，而是要求 witness 判定（§3）。本轮**未采信**任何论坛/博客对 `OK (N tests)` 或 `-4` 的解释（未将其作为依据），结论全部回到源码与盘上实测；唯一一处"存在但未采纳"的官方 API 是 `-e log`（`RunnerArgs.java:59`），因其输出形状未验证而只登记（§9）。

---

## 9. UNVERIFIED / 下一步

| 项 | 状态 | 说明 |
|---|---|---|
| C2（`own_cpu` 多线程累加） | **UNVERIFIED（推断）** | 需在探针窗口内拆 `/proc/self/task/*/stat` 才能定量；本文件只给读数比值 |
| C3（jitter 30 ms 阈值是否应随地板伸缩） | 读数事实成立；**是否改阈值 UNVERIFIED** | runner 未传 `smb.lowload.jitter_p95_floor_ms`；改阈值属行为改动，需单独裁定 |
| `-e log true` 的 dry-run 输出形状 | **UNVERIFIED** | 未在任何设备实跑；不作为判据来源 |
| 15 例 floor 的实测值 | **未实测** | 表内 floor 全部由源码常量推导（`delay`/attach 延迟/首连预算/watchdog 1.5 s）；item 22 单臂实跑后应用实测值替换并保留推导式 |
| 真机上 witness 的额外开销 | **未测** | 估计 78 行 × (write+fsync) < 80 ms，相对 44 s 的套件可忽略；需 item 22 实测核对（若 >1 s 必须记） |
| 设备实跑 | **BLOCKED** | 本文件为研究件；设备执行归 WS3 item 22/23，且依赖低负载窗（本批不做第 4 台，基线 §8） |

下一步（按计划顺序）：item 19 落 §7 的 1–3；item 20 用 witness 第一轮数据裁定 C2/C3（先测量、后改判据）；item 21 把 witness 的 `wall_ms` 作为**事后**负载证据接入报告，不动 shell 前门；item 22 单臂实跑 15 例，用 §5 判据逐例核对；item 24 才谈 T4 控制臂（§9.3 第 2 层，两臂身份可区分）。

---

## 10. 回链

- 计划：`.zcode/plans/plan-2026-11-05-batch-43.md` §1 R3、§2 WS3 19–22、§4 硬约束（witness 必填）、§5 验收。
- 上一批裁定：`docs/research/2026-11-04-batch-42-record.md` §9.3（两层实验不得混写）、§9.5（isolation UNCONVERGED、禁写 T4 排除）。
- 基线：`docs/research/2026-11-05-batch-43-baseline.md` §三（`-4` 原文、assumption 链、witness 不存在，`:189-256`）。
- 源码：`LowLoadProbe.kt`、`IsolationArms.kt`、`VisualJsIsolationInstrumentedTest.kt`、`VisualJsClientEngine.kt`、`core/domain/.../VisualExecutionEngine.kt`。
- 工具：`tools/ci/isolation_pair_report.py`、`tools/ci/run_isolation_pair.sh`、`tools/ci/lowload_gate.py`、`tools/ci/visual_js_isolation_methods.txt`。
- 盘上产物（gitignored，本轮实读）：`build/ws4-isolation-b42/{r1-OFF,r1-ON}.{instrument.log,logcat}`、`build/ws4-isolation-b42/gate-r{1,2,3}.json`、`build/ws4-isolation/manual-skip.log`。
