# isolation 归因研究：D-vs-T4 判别判据、控制轮最小方案、GitHub 参考评分

> 批次 39 收尾 / 批次 40 驱动（计划任务 R1，`.zcode/plans/plan-2026-11-01-batch-39-tail.md:22`）。
> 本文只做**研究、判据设计与命令方案**；本会话**零 Gradle、零设备测试、零构建、零提交**。
> 所有"实测"数字都来自仓内既有原始日志的离线复算（命令逐条给出，可复跑）。

---

## 0. 结论摘要

1. **D 归因成立且可量化，但批次 39 的归因叙述需三处修正**：
   - E-06 称"红形单一（全部为 watchdog 1500 ms 超限）"**不成立**。三窗原始日志里实测存在三类红因：
     **①非设计 watchdog 杀（5/5/3 次）**、**②10 s 连接超时（0/2/1 次，无 kill）**、**③晚到死亡信号与重建竞争（1/0/1 次）**。其中②③与 1500 ms JS 期限无关。
   - 计划 §1 任务 2 的 `kill ≤ 6` **不能作为通过判据**：第三窗 K=6 却 5 红、含 3 次非设计杀（见 §2.3）。正确判据是**逐方法归属后非设计 kill = 0**，且全绿时 K 恰为 **5**。
   - 计划 §1 任务 3 的回退开关写的是 `beforeRegistrationHookForTest`，但该缝在 isolation 套件 **0 引用**；有效回退对象是引擎的 **6 处打点写 + 访问器 + 类**（§4.2）。
2. **给出可执行的 D-vs-T4 判别判据**（§3）：低负载窗 = 程序性串行前置（P）+ 窗内**服务侧 worker warm-up 时长闸门**（G）+ 结果判据（A）。闸门 G 用绿基线（批次 38 step14b：p50 1.38 s / p95 5.11 s / max 7.00 s）标定；三个红窗全部超闸，绿窗全过闸。
3. **T4 机制反证加强**：watchdog 只计服务进程内 JS 阶段（`VisualJsService.kt:341-344,518-533`），且在投递前已 CAS 取消（`:474-489`）；T4 的 6 处 volatile 写全在宿主进程、每请求 ≤4 次 store、读循环每 8 KB 一次分支，量级 ns–µs，与 1.5 s/10 s 的观测差 **6–10 个数量级**。T4 无法解释同窗出现的 10 s **bind 超时**（T4 在 bind 路径上只有 `bindService` 前一次 store）。
4. **T4-reverted 控制轮最小方案**（§4）：只删 6 处写点 + 回退 `readResultPayload` 参数/首字节分支（M2），保留类/访问器/分配点；只构建 app 两个 APK；文件级备份还原（**禁 `git stash`**）；控制轮绿**不得**充当 seam 设备绿绑定。
5. **GitHub 参考评分**（§5）：首选 `android/android-test`（`LogRunListener` 逐方法边界为默认行为，Apache-2.0）与 `androidx/androidx` benchmark 的"设备状态受控测量"原则；`Marathon`（GPL-2.0）/`Flank` 概念可借、代码不可搬；`iDFlakies`/`DeFlaker` 无许可证不复用。

---

## 1. 材料与方法

### 1.1 实读材料

| 材料 | 位置 | 用途 |
|---|---|---|
| 批次 39 收尾计划 | `.zcode/plans/plan-2026-11-01-batch-39-tail.md`（全文 29 行） | 任务 2/3 判据原文 |
| 批次 39 E-06 归因 | `docs/research/evidence/2026-11-01-batch-39/step06-reruns-and-attribution.md` | 三窗 6/7/5、K=10/10/6、D/T4 论证 |
| 批次 39 E-03 首跑 | `.../step03-app-suites-first-run.md` | 第一窗 6 红方法名、red 堆栈、7 个 pid |
| 批次 38 E-06 / E-12 | `docs/research/evidence/2026-11-01-batch-38/step06-isolation.md`、`step12-crash-signatures-lifecycle.md` | 绿基线 15/15、设计杀基数、计数命令 |
| 原始 logcat（5 窗） | `build/device-track-batch38/step14-isolation-logcat.txt`、`step14b-isolation-rerun-logcat.txt`、`build/device-track-batch39/suite-isolation{,-rerun,-rerun2}-logcat.txt` | 本轮离线复算的全部数字 |
| 原始 instrument 输出 | 同名 `.log`（`-r` 原始流） | 红例方法名、`INSTRUMENTATION_STATUS_CODE` |
| 引擎 T4 提交 | `db1ce639`（`git show` 全文实读） | 6 写点/类/访问器/读循环，确认"零新增锁/线程" |
| 服务 watchdog 实现 | `app/src/main/.../visualjs/VisualJsService.kt:330-344,474-534` | 期限只覆盖 JS 阶段、投递前取消 |
| 隔离套件 | `app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt:100-1218` | 设计杀方法、超时与夹具 |
| AndroidJUnitRunner | `android/android-test`：`AndroidJUnitRunner.java:198-200,462-470,505-514`、`LogRunListener.java:32,49-56` | 逐方法边界日志的可用性；`-e log true` 的陷阱 |

### 1.2 本会话实跑命令（只读）

```bash
# 设备与负载（只读）
adb devices -l
adb -s emulator-5562 shell cat /proc/loadavg      # 10:57 时 0.57 0.57 0.60
cat /proc/loadavg                                 # MSYS 视角 14.77 14.77 14.77；32 逻辑核
adb -s emulator-5562 shell head -1 /proc/stat     # steal 字段恒 0（模拟器不暴露 steal）
adb -s emulator-5562 shell cat /proc/pressure/cpu # no-psi
# 代码面
git show db1ce639 -- app/src/main/kotlin/.../VisualJsClientEngine.kt
git status --porcelain -- app/src/main/kotlin/.../VisualJsClientEngine.kt   # 空（clean）
# 日志复算（python 内联脚本，见 §2.3/§2.4；不落盘）
```

> `nproc`（guest）= 4；MemTotal 2,534,120 kB；host 逻辑核 32、`LoadPercentage=38`。

---

## 2. 证据复核（离线复算）

### 2.1 逐方法 kill 归属（复算方法）

AndroidJUnitRunner 在**普通模式**下默认挂 `LogRunListener`，每条用例向 logcat 打
`TestRunner: started: <method>(<class>)` / `finished:` / `failed:`（`LogRunListener.java:49-56`；
挂载点 `AndroidJUnitRunner.java:470,513` —— 仅在 `logOnly` 模式才不挂）。因此**不需要改命令、不需要改测试**，
用 logcat 的 `TestRunner:` 行即可把每条 `killing :visual_js worker: …` 精确归到用例区间。

> **陷阱（已核证）**：`-e log true` 在 AndroidJUnitRunner 里是 **"log only 模式"——只枚举用例、不执行**
> （`AndroidJUnitRunner.java:198-200`）。旧计划/后续批次**不得**用它来加日志，否则会得到 0 次真实执行。

复算脚本（内联，逐窗口输出"方法 → kill 原因计数"与 spawn/died）：

```python
import re, collections
started=re.compile(r"TestRunner: started: (\w+)\("); finish=re.compile(r"TestRunner: finished: (\w+)\(")
kill=re.compile(r"killing :visual_js worker: (.*)$")
cur=None; per=collections.defaultdict(collections.Counter); total=collections.Counter()
for line in open(LOG, encoding="utf-8", errors="replace"):
    m=started.search(line)
    if m: cur=m.group(1); continue
    if finish.search(line): cur=None; continue
    if cur is None: continue
    k=kill.search(line)
    if k: per[cur][k.group(1).strip()]+=1; total[k.group(1).strip()]+=1
    if "Start proc" in line and "visual_js" in line: per[cur]["spawn"]+=1; total["spawn"]+=1
    if "has died" in line and "visual_js" in line: per[cur]["died"]+=1; total["died"]+=1
```

**结果（`watchdog hard deadline` 一类）**：

| 窗口 | 设计杀方法（代码设计值） | 实测 | 非设计杀 |
|---|---|---|---|
| b38 step14b（绿 15/15，pre-T4） | aHungWorker 1 + aSecondConcurrent 1 + rebuildsOnce 2 + watchdogKills 1 = **5** | **5，分布完全一致** | **0** |
| b38 step14（红 2） | 同上 5 | **7**：anExplicitRetry 1、aColdStart 1 | **2**（恰等于 2 红） |
| b39 窗1（红 6） | 同上 5 | **10**：anExecuteAcquired 1、workerIsIsolated 1、anExplicitRetry 1、aColdStart 1、watchdogKills **2**（+1） | **5** |
| b39 窗2（红 7） | 同上 5 | **10**：aHungWorker **2**（+1）、aMalformed 1、workerIsIsolated 1、aColdStart 1、watchdogKills **2**（+1） | **5** |
| b39 窗3（红 5） | 同上 5，但 aHungWorker/aSecondConcurrent 提前失败未触发设计杀 | **6**：rebuildsOnce 2 + watchdogKills 2 中 1 设计/1 非设计 + aHungWorker 1 + anExecuteAcquired 1 | **3** |

设计值来自测试体：`theWatchdogKills…` 1 次（`:245-264`）、`theClientRebuildsOnce…` 2 次（`:352-385`）、
`aHungWorker…` 1 次（`:493-505`）、`aSecondConcurrent…` 1 次（`:639-659`）= **5**。

> **关键更正 1**：批次 38 的"设计基数 5"是对的，且**逐方法分布唯一**；
> 批次 39 三窗中 `theWatchdogKills` 恒为 2（多出的 1 次是**新 worker 的纯几何首帧被杀**，
> 正是该用例断言要排除的失败），所以"K=基数+1"在批次 39 是**非设计杀**，不是抖动余量。
>
> **关键更正 2**：窗 3 的 K=6 **同时是** 5 红。`kill ≤ 6` 若作为判据会把一个 5 红的窗判为"回落达标"。
> 窗 3 里之所以 K 只有 6，是因为 2 个设计杀用例提前失败（未触发设计杀），掩盖了 3 次非设计杀。

### 2.2 红因分类（逐窗实测）

| 窗口 | 红数 | 红因分布（按原始 `.log` 的 `Error in` 块 + logcat 归属） |
|---|---|---|
| 窗1 | 6 | **K 家族 6**：3×`status=-3`（anExecuteAcquired / workerIsIsolated / watchdogKills）、1×`HARD_DEADLINE_EXCEEDED … binder died`（anExplicitRetry）、1×冷启动 `HARD_DEADLINE`（aColdStart）、1×`the rebuilt worker was busy … binder died`（rebuildsOnce，晚到死亡信号） |
| 窗2 | 7 | **K 家族 5** + **超时 2**：`TimeoutCancellationException: Timed out waiting for 10000 ms`（anOverBudget、anUnattributed，其方法内 **0 kill**，spawn=1） |
| 窗3 | 5 | **K 家族 3**（aHungWorker 的 warm 帧、anExecuteAcquired、watchdogKills 的新进程首帧）+ **超时 1**（aSecondConcurrent，10 s connect）+ **晚到死亡/重建竞争 1**（rebuildsOnce） |

> **更正 3**：E-06 §2 "红形单一"与原始日志不符。10 s 超时来自 harness 的 `withTimeout(CONNECT_TIMEOUT_MILLIS=10_000)`
> （`VisualJsIsolationInstrumentedTest.kt:1089,1208`），是 **bind/AMS 侧**的停顿，不是 1500 ms JS 期限；
> "rebuilt worker busy … binder died"是死亡信号与重建执行的竞争。混为一谈会误导 T4 判别。

### 2.3 kill / spawn / died 汇总（与 E-06 对账）

| 窗口 | tests | red | K（watchdog） | 设计 | 非设计 | spawn | died | warm-up p50 / p95 / max |
|---|---|---|---|---|---|---|---|---|
| b38 step14（红 2） | 15 | 2 | 7 | 5 | 2 | 20 | 6 | 1.40 / 5.97 / 19.90 |
| **b38 step14b（绿）** | 15 | **0** | **5** | **5** | **0** | **19** | **5** | **1.38 / 5.11 / 7.00** |
| b39 窗1 | 15 | 6 | 10 | 5 | 5 | 20 | 10 | 3.89 / 11.47 / 13.20 |
| b39 窗2 | 15 | 7 | 10 | 5 | 5 | 20 | 10 | 6.24 / 11.54 / 12.91 |
| b39 窗3 | 15 | 5 | 6 | 3 | 3 | 19 | 6 | 2.20 / 8.88 / 15.01 |

E-06 原文 K=10/10/6、spawn 未列；本表复算**与 K 一致**，并补上 spawn/died 与非设计分解。
原始 `-r` 流交叉复核（`grep -c` 实跑）：绿窗 `code0=15 / code-2=0 / Error in=0`；
窗 3 `code0=10 / code-2=5 / Error in=5` —— §3.2 的 A1/A2 口径可直接落到这三个计数上。
`terminate(...) accepted`（abandon 路径）与 `the host unbound the visual_js worker`（`@After` 解绑）
是既有设计路径，**不计入 K**，三窗分别为 3/3/4 次与 5/3/6 次。

### 2.4 环境负载代理：worker warm-up 时长

**为什么需要它**：guest `/proc/stat` 的 **steal 字段恒为 0**、无 PSI（本会话实读），
host 争用对 guest **不可见**；host `/proc/loadavg`（MSYS 视角）在 3.2–14.8 间波动，与红/绿不同向
（b38 红窗 7.36、b39 红窗 3.2–4.0、当前 14.77，32 逻辑核），**不是可靠闸门**。
可靠的是**同一设备侧、同一工作负载的资源指标**：每个 `:visual_js` worker 的
`Start proc` → `VisualJsService: visual_js warm-up finished` 时长（同一 logcat 即得，无需新代码）。

复算命令：

```python
sp=re.compile(r"Start proc (\d+):.*visual_js")
# 记 proc[pid]=t；遇到 logger pid 相同的 "warm-up finished" 行取 t2-t1
```

结果见 §2.3 末列。绿基线（b38 step14b，N=19 次 warm-up）：**p50 1.38 s / p95 5.11 s / max 7.00 s**。
三个红窗全部显著抬高（p50 2.20–6.24 s，p95 8.88–11.54 s）。窗 3 是唯一"沉降后"窗口
（窗前 loadavg 0.66），其 warm-up 仍为 p50 2.20 / p95 8.88 / max 15.01 —— **远高于绿基线**，
即"guest loadavg 0.66"根本没有捕捉到真实争用。

> **结论**：负载闸门必须用 warm-up 尾部分布，不能用 guest loadavg，也不能只看 sheet 面的 host loadavg。

### 2.5 T4 变更面（现树 `db1ce639`，文件 clean）

| # | 行 | 写点 | 所在线程/阶段 | 频率 |
|---|---|---|---|---|
| 1 | `VisualJsClientEngine.kt:670` | `bindRequestedAtMillis` | 调用线程，`bindService` 前一刻 | 1 次/bind |
| 2 | `:1161` | `bindConnectedAtMillis` | Binder 回调线程 `markConnected` 锁内 | 1 次/bind |
| 3 | `:821` | `readyAtMillis`（轮询首真） | 调用协程 `awaitWorkerReady` | ≤1 次/wait |
| 4 | `:840` | `readyAtMillis`（预算边界首真） | 同上 | ≤1 次/wait |
| 5 | `:401` | `executeAcceptedAtMillis` | 执行协程，`STATUS_ACCEPTED` 后 | 1 次/请求 |
| 6 | `:570` | `firstByteAtMillis` | IO 协程读循环，首成功 read | 1 次/请求 |

附：`val timestamps = ConnectStageTimestamps()`（`:1019`，随 `Link` 每次 bind 分配）、
`connectStageTimestampsForTest`（`:1288-1292`）、`internal class ConnectStageTimestamps`（`:1400-1427`，5×`@Volatile Long`）、
`readResultPayload` 新增 `timestamps` 形参与 `firstByteStaged` 分支（`:542-580`）。
`git show db1ce639` 实读：**无新增锁、无新增 CAS、无新增线程/线程池、无 `synchronized` 扩大**；
生产分支语义零改动，`app/src/androidTest` 对该 seam **0 引用**（`connectStageTimestampsForTest`）。

**机制反证（逐条可核）**：
- watchdog 只在服务进程内、只覆盖 JS 阶段：`VisualJsService.kt:341-344`（请求读/Binder 传输不计入）、
  `:518-533`（`armWatchdog` 从 JS 工作起算 1500 ms）；
- 结果投递前先 CAS + `cancel(false)`：`:474-489`，因此**宿主读结果慢不会拖长 JS 期限**；
- 服务是独立进程 `:visual_js`（隔离 UID），T4 全部在宿主进程；`VisualJsService.kt` 对
  `VisualJsClientEngine`/`ConnectStageTimestamps` **仅有一处注释引用、无代码引用**（grep 实跑），
  服务侧不会构造 `ConnectStageTimestamps`；
- 每请求 T4 最多 4 次 `@Volatile Long` store；x86_64 emulator 上为普通 `mov`（TSO），**无 fence**；
  ARM64 上为 `stlr`（单向屏障，ns 级）。读循环每 8 KB 一次布尔判断（`:563-575`）。
  → 最坏量级 µs，距 1.5 s 期限 6 个数量级、距 10 s 超时 7 个数量级。
- 同窗出现 **10 s bind 超时**（窗2 两例、窗3 一例）：T4 在 bind 路径上只有 `bindService` 前一次 store，
  且 `onServiceConnected` 的 store 发生在连接**已建立之后**，同样无法解释。

---

## 3. D-vs-T4 判别判据

### 3.1 设计原则

1. **可观测、与断言解耦**：闸门量必须来自与"用例是否通过"无关的通道（这里用服务侧 warm-up 时长与
   logcat kill 归属），避免"用结果证明结果"。
2. **能证伪**：判据必须允许出现"低负载仍红"的结论，而不是把任何红都吸收进 D。
3. **不靠单点**：红窗必须同时满足"结果红 + 闸门过 + 归因分类为 K 家族"，才算 D 反例。
4. **可复跑**：命令、采集物、判定表全部可重放；新增判据不引入新代码（本轮）。

### 3.2 低负载窗接受条件

**P：前置（程序性，必须做且必须记录时间戳）**

| 编号 | 条件 | 核验方式 |
|---|---|---|
| P1 | 串行：窗口内**无并发 Gradle / 无并发 `am instrument` / 无 `adb install`**（门禁由脚本串行执行） | 脚本时间线 + host 进程列表快照 |
| P2 | 静默：`install` 完成后 ≥10 min 无 instrumentation；其他线模拟器可在线但**不得跑本轨套件** | `adb devices -l` + 时间戳 |
| P3 | 采样：窗前 5 min、窗内每 10 s、窗后 5 min 采 `cat /proc/loadavg`（host）与 `adb shell cat /proc/loadavg`（guest），**只记录不作硬门槛** | 落盘 `*-load.txt` |
| P4 | 同一设备/API/ABI/页大小；`am instrument` 命令与套件类名逐字相同；记录 app/androidTest 两个 APK sha256 | E-00 体例 |

**G：窗内闸门（硬；从同一份 logcat 复算，无需新代码）**

| 指标 | 阈值 | 依据 |
|---|---|---|
| worker warm-up **p50** | ≤ 1.8 s | 绿基线 1.38 s × 1.30 |
| worker warm-up **p95** | ≤ 6.0 s | 绿基线 5.11 s × 1.17 |
| worker warm-up **max** | ≤ 8.0 s | 绿基线 7.00 s × 1.14 |
| `warm-up failed` 行 | = 0 | 服务侧进程级失败 |
| 未配对 spawn（spawn − warmN） | ≤ 1 | 绿基线为 0–1；>1 视为窗口异常，需记录 |

标定样本：**N=1 个绿窗**（b38 step14b，19 次 warm-up），属**单一样本**，阈值按"绿基线 + 小余量"设定，
后续每个绿窗应回填校准（见 §7 未验证项）。三个红窗全部至少超一项（p50 或 max），绿窗三项全过。

**A：结果判据（唯一通过口径）**

| 编号 | 条件 |
|---|---|
| A1 | `OK (15 tests)`；`Failures: 0`；`Skips: 0`；无 `Error in` 行；15 条 `INSTRUMENTATION_STATUS_CODE: 0` |
| A2 | **红例集合为空**（红集 = `.log` 中 `Error in <method>(` 的方法名集合 ∪ `INSTRUMENTATION_STATUS_CODE: -2` 对应方法） |
| A3 | **非设计 watchdog kill = 0**：逐方法计数 == §2.1 的设计分布 `{aHungWorker:1, aSecondConcurrent:1, rebuildsOnce:2, watchdogKills:1}`，且**无其他方法**出现 watchdog kill |
| A4 | `warm-up failed` = 0；`death was not confirmed` = 0；九类签名 = 0（沿用批 39 E-07 扫描） |

**低负载窗被接受为通过证据** = P 全部满足 ∧ G 全部满足 ∧ A 全部满足。

### 3.3 kill 计数回落阈值（对计划判据的修正）

- **通过阈值 = A3（非设计 kill 归零）**；全绿时 K 必然 == 5，且逐方法分布唯一。
- **`K ≤ 6` 不能作为通过或回落判据**：窗 3 实测 K=6、红 5、非设计 3（§2.3）。
- K 的正确用法是**诊断量**：
  - `K == 5` 且分布 == 设计分布 → 绿签名的必要条件（仍需 A1/A2）；
  - `K > 5` → 至少 `K − 设计实测` 次非设计杀，逐方法列名；
  - `K < 5` → 有设计杀方法提前失败（窗 3 的 aHungWorker/aSecondConcurrent），**不能**解读为"更健康"；
    此时红集必然非空，直接判红。
- `spawn`/`died` 只作交叉校验：绿 19/5、b38 红 20/6、b39 20/10/20/10/19/6；`died == K` 成立（本批全部窗口）。

### 3.4 决策表

设窗口原始结果为 R∈{绿, 红}，闸门 G∈{过, 不过}：

| # | R | G | A3 非设计杀 | 判定 | 动作 |
|---|---|---|---|---|---|
| 1 | 绿 | 过 | 0 | **PASS**：isolation 15/15 成立，seam 设备绿绑定成立 | 收口；把 warm-up 分布回填为新的绿基线 |
| 2 | 绿 | 不过 | 0 | **PASS（带告警）**：绿就是绿；说明 G 阈值偏紧 | 收口；记录 G 误报，考虑放宽 p95/max |
| 3 | 红 | 不过 | — | **窗口无效**：环境未达低负载 | 重跑；不得据此谈 T4/产品 |
| 4 | 红 | 过 | >0 且全为 K 家族 | **D 反例（低负载仍红）** | 进控制轮（§4）；同时按红因分类保留②超时/③竞争的子类 |
| 5 | 红 | 过 | 0（红因全是②超时/③竞争） | **非 1500 ms 家族**：T4 机制上更不可能 | 单独立案（bind/死亡信号竞争），控制轮仍可跑但预期为阴性 |
| 6 | 红 | 过 | >0 但存在非 K 家族红 | **混合** | 逐红分类；K 家族部分进控制轮，非 K 部分另立 |

> 判据 3 的"有效红窗"定义是本文的核心：**只有 G 过的红窗才有归因资格**。窗 3（loadavg 0.66 起跑）
> 之所以不能作为"D 已排除"的证据，正是因为它 G 不过（p50 2.20 / p95 8.88 / max 15.01）。

### 3.5 与计划原文的差异（明确列出）

| 计划表述 | 本文裁定 | 理由 |
|---|---|---|
| 任务 2 `OK (15 tests) + kill ≤6 + 红例集合空` | kill 判据改为 **A3 非设计 kill=0（分布唯一）** | 窗 3 K=6 与 5 红共存（实测） |
| 任务 2 "设备沉降后 φ 单窗复跑" | 沉降定义改为 **P1–P3 + G 闸门**（guest loadavg 不作判据） | guest 无 steal/PSI；窗 3 反例 |
| 任务 3 "`beforeRegistrationHookForTest`/打点开关回退" | 回退对象 = **6 处写点 + 访问器 + 类**（打点缝） | `beforeRegistrationHookForTest` 在 isolation 套件 0 引用（tail-baseline §3.3 已核，本会话沿用其结论） |
| E-06 "红形单一" | **三类红因**（K 杀 / 10 s 超时 / 晚到死亡竞争） | 原始 `.log` 逐条复算 |

---

## 4. T4-reverted 控制轮：最小构建与还原方案

### 4.1 控制变量与混淆项

- **自变量**：宿主侧 6 处打点写（+ 读循环首字节分支）。
- **必须不变**：androidTest APK 逐字节相同（测试仪器同一把尺）；设备/AVD/API/ABI/页大小相同；
  套件类名与命令逐字相同；G 闸门逐窗判定。
- **混淆项**（必须登记，不能靠控制轮消除）：
  1. 构建内容差异（b39 APK 含批次 39 全树改动，b38 绿基线是 pre-T4 且树不同）；
  2. 时间漂移（同一 host 上其他线随时可能起构建）→ 用 ABAB 交替 + G 闸门；
  3. 进程/设备预热（首窗更冷）→ 顺序 `OFF, ON, OFF, ON`，且首窗不计入配对（或加 1 次 discard 预热窗）。
- **推断上限**：控制轮只能回答"删掉打点后是否不再红"，**不能**单独证明"红由 T4 引起"；
  因果仍以 §2.5 的机制链为主，控制轮作一致性检验。

### 4.2 最小回退（M2：只删写点，推荐）

只改 `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt` 一个文件，
**保留** `ConnectStageTimestamps` 类、`connectStageTimestampsForTest` 访问器、`val timestamps` 分配点
（访问器 androidTest 0 引用，保留即零运行时影响；JVM 用例 `ConnectStageTimestampsTest` 继续可编译）。

| 编辑 | 位置 | 内容 |
|---|---|---|
| E1 | `:401` | `STATUS_ACCEPTED -> { … }` 还原为 `VisualJsWire.STATUS_ACCEPTED -> Unit` |
| E2 | `:542-546, 563-575` | `readResultPayload(payload: ParcelFileDescriptor)` 还原单参；删 `firstByteStaged` 与首字节写 |
| E3 | `:487` | 调用点还原为 `readResultPayload(it)` |
| E4 | `:667-670` | 删 `bindRequestedAtMillis` 写（注释可留可删，建议连同 3 行注释删，保持 diff 可读） |
| E5 | `:818-822` | `if (readyNow) { … }` 还原 `if (readyNow) return true` |
| E6 | `:835-842` | 删除边界 `readyNowAtDeadline` 的写，保留原 `return try { service.isReady() } …` 结构 |
| E7 | `:1157-1161` | 删 `bindConnectedAtMillis` 写 |

> **可选 M1（交叉校验，不推荐作主方案）**：`git show db1ce639^:app/src/main/kotlin/.../VisualJsClientEngine.kt > <path>`
> 直接还原为 pre-T4 文件。优点：字节级基线；缺点：会让 `app/src/test/.../ConnectStageTimestampsTest.kt`
> 编译失败（类不存在），若门禁跑 `:app:testDebugUnitTest` 会误红。**仅当 M2 构建受限时使用**，
> 且构建命令必须只含 `:app:assembleStrictOfflineDebug`/`:app:assembleStrictOfflineDebugAndroidTest`。
>
> 现树 `*.gradle.kts` 未检出 `allWarningsAsErrors`/`-Werror`（grep 实跑），M2 的死代码/未用参数只产生 warning。

### 4.3 构建、安装、运行、采集（命令方案；本会话**未执行**）

> 风味：设备轨用的是 **`strictOffline`**（`applicationIdSuffix=".offline"`，`app/build.gradle.kts:50-62`；
> 与日志里的 `com.tingyun.smartmistakebook.offline.test` 一致）。构建/安装路径必须带风味，
> 否则会装成 `localFirst` 包。

```bash
set -euo pipefail
FLAVOR=strictOffline
APP_APK=app/build/outputs/apk/$FLAVOR/debug/app-$FLAVOR-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/$FLAVOR/debug/app-$FLAVOR-debug-androidTest.apk
TMP="${TMPDIR:-/tmp}/t4-control-$(date +%s)"; mkdir -p "$TMP"
ENGINE=app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt

# 0) 前置快照（文件级，不用 git stash —— 工作树有 737 条他线改动；全树两项仅作记录）
git rev-parse HEAD > "$TMP/head.txt"
sha256sum "$ENGINE" | tee "$TMP/engine.pre.sha256"
git status --porcelain > "$TMP/status.pre.txt"
git diff --stat > "$TMP/diffstat.pre.txt"
cp "$ENGINE" "$TMP/VisualJsClientEngine.kt.bak"
sha256sum "$APP_APK" "$TEST_APK" > "$TMP/apk.t4on.sha256"

# 1) 施加 M2（人工编辑 E1–E7），确认只剩打点面差异
git --no-pager diff --stat -- "$ENGINE"          # 期望 1 file changed，行数 << 156
git --no-pager diff -- "$ENGINE" | grep -E '^[+-]' | grep -v '^[+-][+-]'   # 逐行核对

# 2) 只构建两包（不跑单测；控制轮不产生"通过"声明）
./gradlew :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest
sha256sum "$APP_APK" "$TEST_APK" | tee "$TMP/apk.t4off.sha256"
# 若 testBuildType 被 -Psmbk.testBuildType=release 覆盖，androidTest 路径随之改变（本轨不用）

# 3) 安装 + 逐窗运行（每窗都采 P3 负载 + logcat）
adb -s emulator-5562 install -r "$APP_APK"
adb -s emulator-5562 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$TMP/off-1.log"
adb -s emulator-5562 logcat -v threadtime -d > "$TMP/off-1-logcat.txt"

# 4) 还原（必须）——只对 ENGINE 做硬校验；全树只记录（他线在飞，全树必然漂移）
cp "$TMP/VisualJsClientEngine.kt.bak" "$ENGINE"
sha256sum -c "$TMP/engine.pre.sha256"                                   # 必须 OK
test -z "$(git status --porcelain -- "$ENGINE")" && echo "engine clean ✓" # 必须为空
git status --porcelain > "$TMP/status.post.txt"                          # 仅记录
git diff --stat > "$TMP/diffstat.post.txt"                               # 仅记录
# 5) 还原后**必须重新构建**规范包再装（磁盘上的 $APP_APK 此刻仍是控制包！）
./gradlew :app:assembleStrictOfflineDebug
sha256sum -c "$TMP/apk.t4on.sha256" 2>/dev/null || \
  echo "WARN: 重建 APK sha 与开跑前不同（非确定性构建），以台账新 shas 为准"
adb -s emulator-5562 install -r "$APP_APK"
```

> 步骤 0 的 `apk.t4on.sha256` 必须是**规范树当场构建**的产物，不能录"磁盘上碰巧存在的旧 APK"。
> 流程建议：门禁脚本先构建规范包 → 控制轮开跑前记录 shas → M2 → 构建控制包 → 跑 → 还原 →
> 重建规范包 → 用"重建后的 shas"覆盖台账（构建非确定性以 rebuild 结果为准，仍须与开跑前一致才可复用旧 sha）。

- 窗口顺序：`OFF-1 → ON-1 → OFF-2 → ON-2`（首窗作预热丢弃，或至少不与后续配对）。
- **两臂的 androidTest APK 必须 sha 相同**；不同则控制轮作废（先查构建确定性），不得用不同仪器比较。
- 每窗判定只用 §3.2 的 G + A；任一窗 G 不过即丢弃重跑。

### 4.4 结论规则（预注册）

| OFF 臂 | ON 臂 | 结论 |
|---|---|---|
| 2/2 绿（G 过） | 2/2 红（G 过，非设计杀>0） | **T4 关联成立**：打点是必要环境因子 → 必须先修/移除 T4 或重定预算，再谈绑定 |
| 1 绿 1 红 | 1 绿 1 红 | 与 T4 无关；回到 D 或另立（样本不足，不解释） |
| 2/2 红 | 2/2 红 | **T4 非因**；红由环境/其他改动/产品引起 → 重开归因（并检查是否有新的非 K 家族） |
| 绿 | 绿 | 套件在低负载下稳定绿；T4 未致红；**ON 臂的绿可作为绑定，OFF 臂不可** |
| 任一臂 G 不过 | — | 该窗作废，不计入配对 |

> **绑定口径（硬）**：seam 的"设备绿绑定"只认**规范树（T4-on）+ 规范 APK** 的绿窗。
> 控制轮 OFF 臂的绿**不得**写入台账作为 seam 绑定证据，只能作为归因/对照材料。

### 4.5 禁止事项

- 禁 `git stash` / `git checkout .` / `git clean` / `git restore .`（会动他线 737 条改动）。
- 禁改断言、禁 `@Ignore`/`Assume`、禁 `-e class …#method` 红例过滤、禁改 `OK (N)` 语义。
- 禁提交控制轮改动；禁把控制包 APK 留在设备上。
- 禁并发 Gradle；禁触碰热区文件（本方案只碰 `VisualJsClientEngine.kt`）。

---

## 5. GitHub 参考（仪器化并发 flaky 判别）评分

### 5.1 候选与元数据（`gh api` 实查，2026-11-01）

| 项目 | 许可 | ★ | 最后 push | 语言 | 与本问题的关系 |
|---|---|---|---|---|---|
| `android/android-test` | Apache-2.0 | 1230 | 2026-10-30 | Java | `LogRunListener` 逐方法边界；`FlakyTest` 只是标记+过滤 |
| `androidx/androidx`（benchmark） | Apache-2.0 | 6130 | 2026-11-01 | Kotlin | 热节流自动检测/暂停重试；重复测量口径 |
| AOSP Tradefed auto-retry | AOSP（本次未逐文件核证） | — | — | Java | `--retry-strategy ITERATIONS` 全量迭代检测 flaky |
| `Flank/flank` | Apache-2.0 | 701 | 2026-06-20 | Kotlin | 大规模并行跑 + 失败用例重跑（FTL） |
| `MarathonLabs/marathon` | **GPL-2.0** | 646 | 2026-09-21 | Kotlin | flakiness 概率模型 + 固定配额重试 |
| `openjdk/jcstress` | GPL-2.0(+CPE) | 531 | 2026-10-31 | Java | 并发压力正确性，非 flaky 判别 |
| `TestingResearchIllinois/NonDex` | NOASSERTION | 60 | 2026-03-23 | Java | JVM API 非确定性 |
| `UT-SE-Research/iDFlakies` | **无许可证** | 34 | 2026-10-04 | Java | 顺序依赖 flaky |
| `gmu-swe/deflaker` | **无许可证** | 39 | 2021-11-09 | Java | 覆盖差判别，已停滞 |

### 5.2 评分（1–5；权重：适用 30%、可复用 25%、维护 20%、证据 15%、成本 10%，成本=越省越高）

| 项目 | 适用 | 可复用 | 维护 | 证据 | 成本 | 加权 | 结论 |
|---|---|---|---|---|---|---|---|
| `android/android-test` | 4 | 5 | 5 | 5 | 5 | **4.70** | **必用**：默认逐方法日志（本次已在用），零改动 |
| `androidx/androidx` benchmark | 4 | 5 | 5 | 5 | 4 | **4.60** | **采纳原则**：受控设备状态 + 自动节流检测；不引依赖 |
| AOSP Tradefed | 3 | 3 | 5 | 5 | 3 | **3.70** | 采纳 `ITERATIONS` 概念；其 host 框架不适配本 adb 轨 |
| `Flank/flank` | 3 | 4 | 4 | 4 | 2 | **3.50** | 仅作并行/重试设计参考；本轨禁 connectedAndroidTest |
| `MarathonLabs/marathon` | 4 | 2 | 4 | 4 | 2 | **3.30** | 概率模型可借（`minSuccessRate → N`），**GPL-2.0 代码不搬** |
| `openjdk/jcstress` | 2 | 2 | 5 | 4 | 1 | **2.80** | 不适用（JVM 并发正确性） |
| `NonDex` | 2 | 2 | 4 | 3 | 1 | **2.45** | 不适用（JVM API 非确定性） |
| `iDFlakies` | 2 | 1 | 3 | 3 | 1 | **2.00** | 无许可证 → **不复用** |
| `deflaker` | 2 | 1 | 1 | 3 | 2 | **1.70** | 无许可证 + 停滞 → **不复用** |

### 5.3 采纳清单（可落地）

1. **逐方法 kill 归属**：直接依赖 `LogRunListener`（`LogRunListener.java:49-56`，默认挂载
   `AndroidJUnitRunner.java:470,513`），把 §2.1 脚本固化进证据轨。**不引入任何依赖**。
2. **窗口有效性**：借 benchmark 文档的"自动热节流检测→暂停→重试"原则（Android 官方
   Microbenchmark 页："the library performs automatic thermal throttling detection … pauses
   execution … and then retries the current benchmark"），替换为本文的 **warm-up 时长闸门 G**
   （更贴合本设备的可观测面）。
3. **重复策略**：Tradefed 的 `ITERATIONS`（全量重跑、任一失败即失败）与本批"整套件复跑"一致；
   **拒绝** Flank/Marathon 的"失败用例单独重跑并合并结果"作为通过口径（会掩盖 flaky，违反本仓纪律）。
4. **重复次数**：可借 Marathon 的概率式（`1 − p^N`）解释"为何两两配对足够/不足"，但本批判据仍是
   "低负载窗一次全绿 + 控制轮 2×2 配对"，不做概率性放行。

---

## 6. 对计划与台账的修正建议

1. 计划任务 2 判据改为：`OK (15 tests)` + **非设计 watchdog kill = 0（分布唯一）** + 红集空 +
   G 闸门（p50 ≤1.8 s / p95 ≤6.0 s / max ≤8.0 s）。
2. 计划任务 3 的开关对象改为"6 写点 + 访问器 + 类"，删除 `beforeRegistrationHookForTest` 表述
   （该缝在 isolation 套件 0 引用，tail-baseline §3.3 已核）。
3. 批次 39 E-06 §2 的"红形单一"应改为三类红因（K 杀 / 10 s 超时 / 晚到死亡竞争），并补
   spawn/died 与非设计 kill 分解（本文 §2.2–2.3 可直接引用）。
4. 台账新增"窗口有效性"字段：P1–P3 时间戳 + G 三项 + 设计/非设计 kill 分布。
5. **不许**把窗 3 记为"低负载窗"：其 warm-up p50 2.20 s 是绿基线的 1.59 倍。

---

## 7. 未验证项（明确列出）

1. **G 阈值只由一个绿窗标定**（b38 step14b，N=1，19 次 warm-up）；p50/p95/max 的 1.30/1.17/1.14 倍余量
   是工程取整，**未做跨窗验证**。首批新绿窗必须回填。
2. **控制轮方案未构建、未运行**：M2 的 7 处编辑是纸面推演（行号基于 `db1ce639` 现树，已逐条 grep 核对），
   本会话**零 Gradle**；`allWarningsAsErrors` 仅以 grep 判定"未检出"，未跑编译验证。
   Gradle 任务名 `assembleStrictOfflineDebug`/`assembleStrictOfflineDebugAndroidTest` 按 AGP 变体命名约定
   （风味 + buildType，`app/build.gradle.kts:50-62`），**未跑 `:app:tasks` 核对**；报错时以 `./gradlew :app:tasks --all` 为准。
3. **T4 开销的 µs–ns 量级**是架构/指令级推断（x86 TSO `mov`、ARM `stlr`），**未做微基准**；
   结论使用"数量级"而非精确值。
4. **b38 与 b39 的 warm-up 对比含构建差异**（树不同、APK 不同），只能作"环境代理"用，不能当 T4 对照。
5. AndroidJUnitRunner `LogRunListener` 的默认挂载以 **androidx-main 源码**为准；本仓实际拉取的
   androidx.test 版本未逐版本核对（行为在本批 logcat 中已实证 15/15 行存在，属运行期验证）。
   `FlakyTest` 是否有重试参数**未完全核证**（官方页本轮被截断，只读到"标记/过滤"描述）。
6. AOSP Tradefed 许可未逐文件核证；`iDFlakies`/`deFlaker` 的"无许可证"来自 GitHub API 的 `license: null`，
   不等于仓库内无任何许可声明文件（未逐文件读）。
7. `deFlaker` 最后 push 2021-11-09 来自 `pushed_at`；`updated_at` 2026 是元数据变动，不代表代码维护。

---

## 8. 来源

**仓内（本轮实读/实算）**
- `.zcode/plans/plan-2026-11-01-batch-39-tail.md:16-19,28`
- `docs/research/evidence/2026-11-01-batch-39/step06-reruns-and-attribution.md:29,33-41`
- `docs/research/evidence/2026-11-01-batch-39/step03-app-suites-first-run.md:41-59`
- `docs/research/evidence/2026-11-01-batch-38/step06-isolation.md:40-47,61,65,69`
- `docs/research/evidence/2026-11-01-batch-38/step12-crash-signatures-lifecycle.md:52-60`
- `docs/research/2026-11-01-batch-39-tail-baseline.md:165-194`（T4 清单与缝引用数）
- 原始日志：`build/device-track-batch38/step14{,-rerun}-isolation*-logcat.txt`、
  `build/device-track-batch39/suite-isolation{,-rerun,-rerun2}-logcat.txt` 及同名 `.log`
- `app/src/main/.../visualjs/VisualJsClientEngine.kt:401,487,542-580,667-670,818-842,1019,1157-1161,1284-1292,1400-1427`
- `app/src/main/.../visualjs/VisualJsService.kt:341-344,474-489,518-533`
- `app/src/androidTest/.../visualjs/VisualJsIsolationInstrumentedTest.kt:245-290,343-397,479-541,629-660,1036-1218`
- `core/domain/src/main/.../VisualExecutionEngine.kt:126`（1500 ms）、`:129`（2500 ms）

**外部（本轮实取）**
- AndroidJUnitRunner `logOnly` 语义：https://github.com/android/android-test/blob/androidx-main/runner/android_junit_runner/java/androidx/test/runner/AndroidJUnitRunner.java#L198-L200
  （另 `:462-470`、`:505-514` 的 `LogRunListener` 挂载点）
- `LogRunListener` 逐方法日志：https://github.com/android/android-test/blob/androidx-main/runner/android_junit_runner/java/androidx/test/internal/runner/listener/LogRunListener.java#L32-L56
- `androidx.test.filters.FlakyTest`（API 参考本轮只读到"标记 + 可用 `-e annotation` 过滤"，
  **未读到重试参数**；完整页因体积被截断，属未完全核证）：
  https://developer.android.com/reference/androidx/test/filters/FlakyTest
- Tradefed 自动重试/迭代（`RETRY_ANY_FAILURE` / `ITERATIONS`）：
  https://source.android.com/docs/core/tests/tradefed/testing/through-tf/auto-retry
- Jetpack Microbenchmark（自动热节流检测→暂停→重试）：
  https://developer.android.com/topic/performance/benchmarking/microbenchmark-overview
- Marathon flakiness/retries 文档：
  https://github.com/MarathonLabs/marathon/blob/master/docs/runner/configuration/flakiness.md
  、https://github.com/MarathonLabs/marathon/blob/master/docs/runner/configuration/retries.md
- Flank 用例级重试设计（含"总计数不被重试污染"要求）：
  https://github.com/Flank/flank/blob/master/docs/feature/1692_test_case_based_retries.md
- 仓库元数据（stars/license/pushed_at）：`gh api repos/{Flank/flank,MarathonLabs/marathon,UT-SE-Research/iDFlakies,gmu-swe/deflaker,TestingResearchIllinois/NonDex,openjdk/jcstress,android/android-test,androidx/androidx}`
