# R2 isolation 因果实验设计：同构建身份下的 OFF/ON 配对、T4 控制轮与红集比较

| 项 | 值 |
|---|---|
| 编号 | R2（批次 42 计划 `.zcode/plans/plan-2026-11-04-batch-42.md:17`；落点 WS1 1/2/3/4、WS4 34–41） |
| 日期 | 2026-11-04 |
| 结论级别 | **设计裁定 + 工具缺陷实证**（本机 `python -I` 探针、`adb` 只读命令、`dexdump`/`apkanalyzer` 实跑）；**OFF/ON 真跑未执行**（需生产侧回退构建，属主会话/落码轨授权，见 §8.3） |
| 输入 | 批次 41 记录 `docs/research/2026-11-03-batch-41-record.md:298,318-319,433-435`；D05 `docs/research/evidence/2026-11-03-batch-41/step05-isolation-lowload.md:11,46-52,63,71`；R5 `docs/research/2026-11-03-low-load-gating.md`；归因 `docs/research/2026-11-01-isolation-attribution.md:159-187,191-261,274-309` |
| 实跑设备 | `emulator-5562`（API 36，x86_64，已装批次 41 的 `.offline` APK，`lastUpdateTime=2026-11-01 17:27:31`）；`emulator-5584/5566` 仅用于 `adb devices` 确认 |
| 未做 | 未构建 OFF 臂、未安装新 APK、未跑套件、未改任何源码/设置、未做 git 写 |

---

## 0. 结论摘要（照做清单）

1. **“同 APK sha 的 OFF/ON 配对”在现行实现下不存在**：T4 是编译进 APK 的 6 处写点（`docs/research/2026-11-01-isolation-attribution.md:159-168`），OFF 臂必须换一个 app APK。能同时成立的“同 sha”只有两条：**每臂各轮同 app sha**、**两臂 test APK 逐字节相同**；它们必须在报告里成为硬前置，而不是口头约定。
2. **当前 OFF/ON 的“臂”只是一个运行时参数**，同一 `COMPONENT`、同一测试类，日志里的 `t4_production=compiled_in` 是写死的常量（`IsolationArms.kt:69-70`；`run_isolation_pair.sh:49-51,217-222`）。所以“这次跑的是哪个构建”在产物上不可核——批次 41 的两套结论（app `665cd5e0…` vs `5793300c…`）正是这个缺口的后果（`2026-11-03-batch-41-record.md:191`）。
3. **臂身份必须从“已安装产物”推导**，检查链：预注册 app sha → 设备侧 `sha256sum base.apk` → `cmd package path`/`dumpsys package` → 全 dex 的 T4 setter 调用点数（ON=6，OFF=0）→ 类/方法名集合两臂相等 → test APK sha 两臂不变。其中设备侧 sha、`cmd package path`/`dumpsys`、全 dex 写点数、类清单四类检查本机已实跑（§3.5、§9）。
4. **测试侧钩子按臂开合是第二个处理**：`-e smb.t4.arm ON` 才注册 `T4SeamProbeRule` 并读时间戳（`VisualJsIsolationInstrumentedTest.kt:131-135,163-168`；`IsolationArms.kt:35,89-121`）。这使“OFF/ON 差异”同时包含“生产打点差异”和“测试观测差异”。控制轮的钩子必须两臂一致（建议恒开）。
5. **`isolation_pair_report.py` 的 F7 假绿已实证，比记录更严重**：OFF 臂**一条用例都没执行**（空 instrument 日志）仍输出 `PAIRED_RED_SET_IDENTICAL`（reasons 为空、paired=true）；空 logcat 的窗被判 `PASS`；round 编号不齐直接 `KeyError` 崩溃，重复 round 静默覆盖。缺失方法必须在向量里物化为 `SKIP/UNKNOWN`，且方法宇宙要外钉（15 条清单），不能取两臂并集。
6. **post 窗负载门是后处理变量，必须取消**：`run_isolation_pair.sh:226-237` 要求 pre 与 post 都 PASS；batch 41 实测 pre 6/6 PASS、post 5/6 INVALID（busy_max 6.9–10.4% vs 6.74%、ctxt 742–918 vs 632，`2026-11-03-batch-41-record.md:298`），原因是套件自身收尾落进 post 窗。改为：**有效性只看自身 pre 门 + 宿主侧跑中时间线（外生量）**；guest post 负载只作诊断。
7. **warm-up 闸门不能作为纳入条件**：T4 的 6 处写点里有两处就在 `readyAtMillis` 的 ready 路径上（`2026-11-01-isolation-attribution.md:165-166`），warm-up 是处理的下游变量；按它筛窗会引入选择偏差。它只能作预注册的敏感性分析。
8. **统计口径与设计不匹配**：设计是配对的（同 round、同宿主），现行因果门却用非配对 Fisher（`isolation_pair_report.py:390-394`），k=4 就 p=0.0286；配对 McNemar 在 k=4 只有 0.125，**k≥6 才 p<0.05**。若不做预注册主终点而检验 15 个方法，Bonferroni 下 McNemar 要 **k≥10**。
9. **“红集相同”不能写成“T4 排除”**：k 轮零分歧只给出**不一致概率**的上界 `1-0.05^(1/k)`（由 NIST 精确二项上界 `BINCDF(0;p_u,n)=α` 推得）：k=3 是 63%、k=8 是 31%、k=19 才 15%、k=29 才 10%。要解除批次 41 的“禁止 T4 排除”，必须先预注册可接受的等价界 δ，再取到使上界 ≤δ 的 k；否则只能写“未检出 ≥X% 的效应”。
10. **n 与随机化**：推荐 **k=8 个有效轮/臂 + 1 个弃置预热轮**，块随机化 AB/BA（种子入 manifest），每臂每轮重装对应 APK；成本约 2.2 h。若必须全 15 方法筛查 → k=10（约 2.6 h）；要写 ≤15% 的等价界 → k=19（约 4.5 h，分两场并做地板等价检查）。

---

## 1. 要分开的三个东西：D、B、T4

设红集向量为 R，目标是区分三种来源：

| 代号 | 内容 | 在本仓的具体形态 |
|---|---|---|
| **D** | 环境/宿主争用 | 他线 Gradle、同宿主 4 台模拟器、套件自身收尾（R5 §2.4–2.6；batch 41 post 5/6 INVALID） |
| **B** | 构建/产物差异 | OFF 臂要回退生产源码 → app APK 必然不同（`2026-11-01-isolation-attribution.md:282`） |
| **T4** | 6 处打点写 + 读循环首字节分支 | `VisualJsClientEngine.kt` 的 6 个写点（同文 §2.5） |

**关键裁定**：在编译期回退的控制轮里，**T4 与 B 对 app APK 是完全混杂的**；一次 OFF/ON 配对无法把二者拆开。能做的只有三件事：

1. **把 B 夹到预注册的最小 hunk**（源码 diff + 全 dex 调用点计数 + 类/方法名集合相等），使“B 的其余部分”成为可核的零；
2. **用时间上的交替/随机化把 D 与 (T4+B) 分开**；
3. 若将来给 T4 加运行时开关，才能做字面意义的“同 app APK sha”配对——但那时处理变成“6 次分支判断 + 不写”，估计量也随之改变（不是“T4 不存在”），本批不建议为此改生产码。

因此本批可写的因果陈述只有一种：**“在 B 被夹到预注册 hunk 的前提下，OFF 构建与 ON 构建的差异”**；把它简写成“T4 因果”时必须同时引用 B 的夹逼证据。

> 批次 41 的两套结论不能合并，根因就是 B 未被夹逼也没被记录成前置：WS4 与 D05 的 app/test sha 全不同（`2026-11-03-batch-41-record.md:191`）。

---

## 2. 环境（D）排除：低负载窗判据

### 2.1 窗结构（改掉 post 负载门）

现行：每臂 `pre 5 min → 套件 → post 5 min`，两者都 PASS 才记 `window=PASS`（`run_isolation_pair.sh:209-237`）。

**改为“门-跑-门”串联，每个门只服务下一臂**：

```
floor(OFF) → floor(ON) → [gate1] run1 → [gate2] run2 → [gate3] run3 → …
```

- `gate(i+1)` 只提供 arm i 的**设备侧外部证据**（有无其他包的测试进程、有无残留的外部 runner）；它发生在 arm i+1 的任何处理之前，因而不是 arm i+1 的后处理变量。
- arm i 的**事后**判定以**宿主侧时间线**为主：run 窗 `[t_start, t_end]` 内是否出现新的 `gradle/java/kotlin` 构建进程、其他模拟器的 qemu CPU 是否跃升超过地板 +0.5 核（R5 L-5）。这些是外生量，不随 T4 改变。
- **guest 侧 post 负载只作诊断，不判该臂无效**：套件自身收尾会必然把它顶高（batch 41：5/6 INVALID），而且“收尾有多脏”可能是处理的下游量——拿它筛样本就是后处理选择偏差。
- 一臂有效 = **自身 pre 门 PASS ∧ 宿主时间线无外部闯入**。外部闯入 → 该臂 `WINDOW_UNKNOWN`（已观测结果，不得回填）；下一臂的 pre 门若也不过 → 下一臂不跑（无结果，可重排）。
- 这消灭的具体失败：post 负载门把套件自身收尾算成环境违规，导致 6 个 run 里 5 个作废，配对永远无法收敛；同时避免用后处理量筛样本。

### 2.2 判据表（沿用 R5 L-3，不新增放宽参数）

| ID | 量 | 门值 | 实现位置 |
|---|---|---|---|
| W-a | `busy%` | `p95 ≤ floor_p95 + 3.0 pp` 且 `max ≤ floor_max + 5.0 pp` | `lowload_gate.py:69-70,228-240` |
| W-b | `la1/nproc` | `p95 ≤ floor_p95 + 0.05` 且 `max ≤ 0.30` | `:71-72,231-244` |
| W-c | `ctxt/s` | `p95 ≤ 1.5 × floor_p95` | `:73,234-246` |
| W-d | 唤醒探针 | `p95 ≤ max(3×floor_p95, 30 ms)`；`max ≤ 5×floor_max` | `:74-76,208-220` |
| W-e | 样本/间隔 | 恰 30 个差分；`Δt ∈ [8,15] s`；`nproc` 与地板相同 | `:77-78,195-206` |
| W-f | 快照 | 跑前三条全 0（`test_processes=runner_processes=orphan_workers=0`）；跑后只数**其他包**的 `.test` 进程与外部 runner（现实现 `:341-347,369-380` 只数总数，按包名过滤需改） | `:341-347,369-380` |

门值全部相对地板；地板是输入，不是常数（`lowload_gate.py:20-30`）。**禁止**“去掉最大值”“取平均”。

### 2.3 两臂地板等价门（新，消灭“各过各的门”）

OFF 与 ON 的 app APK 不同，按 R5 §4.2 必须各自标定地板；但两块地板不同就意味着两臂不在同一环境。故加一条**预注册**的等价判据（设计值，首轮实测后回填）：

| 量 | 两臂地板差上限 | 理由 |
|---|---|---|
| `busy_p95` | ≤ 1.0 pp | 必须显著小于门本身的 +3.0 pp 带宽，否则两臂的“通过”不是同一把尺 |
| `la_ratio_p95` | ≤ 0.02 | 同上（门带宽 +0.05） |
| `wakeup_p95_ms` | ≤ 10 ms | 门带宽是 3×（静机 14 ms 时约 42 ms），地板差 10 ms 已占 1/4 |
| `nproc` / 采样间隔 | 完全相同 | 归一化与采样口径必须一致 |

任一超限 → `FLOOR_NOT_EQUIVALENT`，配对不成立（不是 INVALID 单轮，而是整场作废）。首场建议把 OFF 地板测两次（OFF→ON→OFF），两次 OFF 的差也须落在同表内，作为标定自身的重测信度检查（+7 min）。

### 2.4 失效处置：只有“未观测结果”的 run 允许重排

| 情形 | 结果是否已观测 | 处置 |
|---|---|---|
| pre 门不过（套件未启动） | 否 | 允许等待后重开门；该位置不产生 run |
| 套件启动后崩溃/超时/宿主时间线显示跑中外部闯入 | 是 | 记 `INVALID`，**计入计划 k**，不得重跑替换 |
| 套件正常但全 `-4`（自证探针） | 是（全 SKIP） | 记 `INVALID`；见 §2.6 |
| 事后发现装错 APK | 是 | 全部作废，整场重来（不得只丢那一臂） |

理由：按结果决定是否重跑就是 optional stopping / 选择偏差；R5 L-7 已规定“不得反复重跑到过”（`docs/research/2026-11-03-low-load-gating.md:184-189`）。

### 2.5 warm-up 闸门为什么不能当纳入条件

`2026-11-01-isolation-attribution.md:214-223` 的 G 闸门（warm-up p50/p95/max）确实能分开历史红/绿窗，但 warm-up 的 ready 时刻正是 T4 第 3/4 个写点所在路径（同文 `:165-166`）。把它当**纳入条件**等于按处理的下游变量筛样本（选择偏差/碰撞）。裁定：

- **不纳入**配对有效性判定；
- 作为**预注册的敏感性分析**：用同一组阈值分别报告“含 G 过滤”和“不含 G 过滤”的红集，两者不一致时以不含 G 的为主结论，并把差异写进证据；
- 阈值只能来自**实验前**的绿基线（b38 step14b：p50 1.38 s / p95 5.11 s / max 7.00 s），不得用本场数据回头标定。

### 2.6 自证探针 `own_cpu_busy`：记录并继续跑，不要 Assume 全跳

D05 实测：shell 门 PASS，但 instrumentation 自证 3/3 次 `UNGATED reason=own_cpu_busy`（`own_cpu_ms` 3.17–3.49 s，`probe_window_ms` 3.10–3.14 s），15 例全 `-4`，adb exit=0、`code0=0`（`step05-isolation-lowload.md:11,46-52`）。约 1 核 × 3.1 s 的自耗出现在进程启动后，形状更像 JIT/类校验，而非宿主争用；根因未定（同文 `:71`）。

裁定：

1. 自证探针**不得**把“环境未知”变成“整套件零执行”；应记录 `UNGATED` 并继续跑，报告把该 run 判 INVALID（这符合 R5 §4.8“标 UNGATED 并保留数据，不 assumeTrue 跳过”，`docs/research/2026-11-03-low-load-gating.md:207`；现行 `VisualJsIsolationInstrumentedTest.kt:141-144` 的 `Assume` 与 R5 相反）；
2. 若保留 Assume 形态，则必须给出与 `own_cpu_busy` 可区分的**独立判词**，且不得让一次探针误报吃掉整场（D05 已连续误报 3 次）；
3. 探针窗口必须避开进程启动后的类加载期（例如先做一次不计分的预热测量），否则它测的是“自己刚启动”。

> 这条同时是“15/15 绿”与“15/15 跳过”都可能被写成通过的原因：`OK (15 tests)` 不表示 15 例通过（同文 `:52`）。

---

## 3. 打点（T4）排除：控制轮与构建身份

### 3.1 现行四处不可核（逐条读码）

| # | 事实 | 位置 | 后果 |
|---|---|---|---|
| 1 | 两臂同一 `COMPONENT`、同一测试类，臂只由 `-e smb.t4.arm` 区分 | `run_isolation_pair.sh:49-51,217-222` | 打错标签/装错 APK 在产物上不可见 |
| 2 | 日志自述 `t4_production=compiled_in` 是常量 | `IsolationArms.kt:69-70` | 自述不能证明安装的是哪个 APK |
| 3 | `t4_control_marker.py verify` 只重算**当前源码树**（引擎 sha + 锚点行） | `t4_control_marker.py:123-149` | 源码树对、设备上是旧 ON 包，仍会通过 |
| 4 | `--apk` 只把 sha 写进 manifest，从不与设备比对 | `run_isolation_pair.sh:128-133` | “同 APK sha”是手写声明，不是检查 |

### 3.2 身份链（从源码到设备，逐环可核）

```
源码 commit + 预注册 hunk sha
   → 构建命令/工具链/签名证书 sha
   → 构建产物 app.apk sha256（OFF / ON 各一）
   → 安装：adb install -r（禁 --fastdeploy/--incremental）
   → 设备侧 sha256(base.apk)  ==  预注册 sha
   → cmd package path / dumpsys package（包名、versionName、codePath）
   → 全 dex：T4 setter 调用点计数（ON=6 / OFF=0）
   → 类+方法名集合：两臂相等（只有方法体不同）
   → test APK sha：两臂同一个（且全程不重装）
   → instrumentation 进程自报 target APK sha（与 4 比对）
```

每一环的失败都必须是 **fail-closed**：不产出判词、退出非 0，不得“警告后继续”。

### 3.3 两臂交付形态：同包重装（推荐）vs 双包并存

| 方案 | 优点 | 缺点 | 裁定 |
|---|---|---|---|
| **同包重装**（`--off-apk`/`--on-apk`，每臂 `install -r`） | test APK 可只构建/安装一次 → 尺子逐字节相同；包名/data 路径不变；无需改构建脚本 | 每次切换 ~15–30 s；装包后首测是冷启动，需 settle；两臂共享 data，需 `pm clear` 统一初态 | **推荐** |
| 双包并存（applicationIdSuffix `.t4off`/`.t4on`） | 可无缝 AB/BA，不用重装 | 需要两套 test APK（targetPackage 不同）；包名变成变量；构建脚本改动更大 | 备选；若重装本身扰动首测，再评估 |

同包重装下 `pm list instrumentation` 仍能证明 runner 绑定到目标包（本机实测 `target=com.tingyun.smartmistakebook.offline`），但不能区分两臂——区分只能靠 sha/dex；这正是 3.2 要存在的理由。

> **与 F8 原文的差异（必须显式登记）**：计划 F8 给的是“两臂绑定不同构建身份（组件/类名可区分），**或**显式拒绝同 COMPONENT”。本文选第三条路：**保留同 COMPONENT，把身份绑到已安装 APK**（sha + 全 dex 写点数 + 类清单）。理由：① 组件名同样可能被错配，它不是产物事实；② 换成不同组件意味着两套 test APK（`targetPackage` 不同），测试尺子不再逐字节相同，等于用一个新的混淆项换掉旧的。若主会话坚持“必须拒绝同 COMPONENT”，则退回双包并存方案，并接受 §3.3 列的代价。

**数据初态**：每臂跑前 `pm clear <pkg>`（两臂同一规则），否则 round 2 起继承上一轮数据，臂序与数据状态混杂。若套件依赖既有数据，须先在单臂上试跑验证；不成立就改回“不 clear”，并把“轮内数据累积”列为登记混淆项。

### 3.4 测试侧钩子必须两臂一致

`IsolationArms.kt:35` 让 `testSideHooks` 只在 ON 为真；`VisualJsIsolationInstrumentedTest.kt:163-168` 因此只在 ON 注册规则并读时间戳。控制轮的两臂差异应**只有 app APK**，所以：

- 把 `testSideHooks` 改为恒真（两臂都注册、都读时间戳、都打 `SMB_T4_HOOK`）；
- OFF 构建保留 `ConnectStageTimestamps` 类与 `connectStageTimestampsForTest()`（M2 只动 `VisualJsClientEngine.kt` 一个文件：6 处写点 + `readResultPayload` 首字节分支与形参，`2026-11-01-isolation-attribution.md:288-302`），因此恒开钩子可编译、读到 0 值也不断言；
- 这样就不需要 M1（整文件回退）；M1 会让类消失、测试编译失败，反而逼出“OFF 臂编译掉钩子”的第二个差异。

**消灭的具体失败**：现有 OFF/ON 的差异里混着“ON 臂多读 5 个时间戳 + 多打 2 行日志”，红集差异无法归给生产打点。

### 3.5 机械检查命令（本机已实跑，输出见 §9）

```bash
# 1) 安装路径与包元数据
adb -s $S shell cmd package path com.tingyun.smartmistakebook.offline
adb -s $S shell dumpsys package com.tingyun.smartmistakebook.offline \
  | grep -E "versionCode|versionName|codePath|lastUpdateTime|primaryCpuAbi"

# 2) 设备侧 sha（与构建产物 sha 比对）
adb -s $S shell sha256sum "$(adb -s $S shell cmd package path <pkg> | sed 's/^package://' | tr -d '\r')"

# 3) 取回已安装 APK（dex 分析用）
adb -s $S exec-out cat <codePath>/base.apk > installed-<arm>.apk

# 4) 全 dex 的 T4 setter 调用点计数（build-tools 36 的 dexdump 用 ';.'，不是 '->'）
unzip -o -q installed-<arm>.apk 'classes*.dex' -d dex-<arm>
for f in dex-<arm>/classes*.dex; do
  dexdump -d "$f" | grep -E "invoke-.*ConnectStageTimestamps[;.]set(BindRequested|BindConnected|Ready|ExecuteAccepted|FirstByte)AtMillis"
done   # ON 期望 6 条：bindRequested 1 / bindConnected 1 / ready 2 / executeAccepted 1 / firstByte 1；OFF 期望 0

# 5) 类/方法名集合（不看字节大小；仅对**未混淆的 debug 变体**成立）
#    行形状：`M d <n>\t<n>\t<n>\t<class> [返回类型] <方法签名>`；$6=类，$NF=方法签名
apkanalyzer dex packages --defined-only installed-<arm>.apk \
  | grep -E "^[CM] " \
  | awk '$1=="C"{print "C",$6} $1=="M"{print "M",$6,$NF}' | sort > names-<arm>.txt
diff names-OFF.txt names-ON.txt   # 期望空；非空 → BUILD_IDENTITY_MISMATCH

# 6) test APK：同一份、逐字节相同（同包重装方案下只需构建/安装一次）
adb -s $S shell sha256sum "$(adb -s $S shell cmd package path <pkg>.test | sed 's/^package://' | tr -d '\r')"
```

类/方法名比较的前提是**未混淆**（`app/build.gradle.kts:67-99` 只有 benchmark/internal/release 开 `isMinifyEnabled`，debug 不开）；若将来在 R8 变体上做，必须改用 `mapping.txt` 反混淆后再比较。本机已实跑“单臂提取”（§9），**两臂 diff 未跑**（没有 OFF 包）。

**多 dex 陷阱（实测）**：本 APK 有 10 个 `classes*.dex`，6 个写点**全部**在 `classes4.dex`，而主 dex `classes.dex`（41 MB，绝大多数应用代码）里 `ConnectStageTimestamps` 出现次数为 **0**。只扫 `classes.dex` 会把 ON 数成 0 条，直接误判成 OFF；必须遍历全部 `classes*.dex`。同类陷阱：写点分散在外层类与嵌套类（`Link`、`readResultPayload$2`）中，按类逐个 `apkanalyzer dex code` 容易漏。

**`dexdump` 格式陷阱（实测）**：build-tools 36 输出 `Lcom/.../ConnectStageTimestamps;.setXxx`，不是 `->`；正则写成 `->` 会零命中（我第一次就是这样数到 0 的）。检查脚本必须兼容两种写法，或用 `grep -E "invoke-.*setXxx"` 后再按接收者类名过滤。

### 3.6 标记文件应扩成“产物 + 安装态”两段

`t4_control_marker.py` 现有字段（`engine_sha256`、6 个 anchor 行 sha、`arm`、`revert_marker_seen`）只能证明源码树。建议新增（不删旧字段，保证向后兼容）：

```json
{
  "arm": "OFF_PRODUCTION_T4_REMOVED",
  "source": {"commit": "...", "hunk_sha256": "...", "engine_sha256": "..."},
  "artifact": {
    "app_apk_sha256": "...", "test_apk_sha256": "...",
    "signer_sha256": "...", "build_command": "...", "built_at_epoch": 0
  },
  "installed": {
    "serial": "...", "package": "...", "code_path": "...",
    "device_apk_sha256": "...", "last_update_time": "...",
    "t4_write_sites": {"bindRequested": 1, "bindConnected": 1, "ready": 2,
                        "executeAccepted": 1, "firstByte": 1},
    "class_method_names_sha256": "..."
  },
  "verdict": "MATCH"
}
```
（`verdict` 只允许 `MATCH` / `MISMATCH`；上面是字段形状示意。）

`verify` 必须重新从设备取值（`sha256sum`、`dexdump`），而不是只重算源码树；`arm` 与 `t4_write_sites` 不一致（例如声明 OFF 却数到 6）→ exit 1。

---

## 4. 红集比较规则

### 4.1 方法宇宙外钉，不取两臂并集

现行 `methods = OFF ∪ ON`（`isolation_pair_report.py:349-350`）。这使“两臂都缺同一个方法”完全不可见。改为：

- 仓库内钉一份清单 `tools/ci/isolation_methods.txt`（15 行，生成自 `VisualJsIsolationInstrumentedTest` 的 `@Test` 方法名，本机 `grep -c "@Test"` = **15**），文件 sha 入预注册；
- 每个 run 解析出方法集合后，与清单做**集合相等**比较；缺的每个方法在向量里物化为 `UNKNOWN`（并在报告里单列 `missing`），多的方法 → `method_universe_mismatch`；
- 另核每个状态块的 `numtests=` 与结束行 `OK (N tests)`：`N` 必须等于清单长度；缺失即 `run_invalid`。

### 4.2 缺失必须落在 SKIP/UNKNOWN，不能落在“非红”

F7 记录说“缺失方法不判 SKIP/UNKNOWN”，实测比记录更宽：**整个 OFF 日志为空**也判 IDENTICAL。

```text
$ python -I -c "…（构造 OFF 空日志 + ON 两方法全绿，3 轮，off_arm=removed）"
A empty OFF -> PAIRED_RED_SET_IDENTICAL [] paired= True
B missing mBeta -> PAIRED_RED_SET_IDENTICAL []
C missing vs red -> PAIRED_RED_SET_DIVERGES_T4_UNVERIFIED
D both skipped -> UNCONVERGED ['skipped_or_unknown']
```

- A/B 是假绿：`.get(method, UNKNOWN)` 只用于比较，不进 `vector.values()`，所以 `:298-306` 的 `skipped_or_unknown` 检查看不见“不存在的方法”；
- C 把“OFF 没跑”读成“OFF 绿”（`off_red=0`），方向上是**反保守**的；
- D 说明“显式 SKIP”已被正确处理——缺的只是“缺失=SKIP/UNKNOWN”的物化。

**规则**：在 `parse_instrument_log` 之后、任何比较之前，对每个 run 做 `vector = {m: vector.get(m, UNKNOWN) for m in pinned_methods}`，并把缺失数与名单写入报告；任一缺失 → `paired=false`、`UNCONVERGED`、reason `method_missing`。`PAIRED_RED_SET_IDENTICAL` 的前置必须包含“两臂 k 轮 × 15 方法全部有终态且无 SKIP/UNKNOWN”。

### 4.3 run 有效性前置（现在一个都没进报告）

| 前置 | 现状 | 需要的输入 |
|---|---|---|
| 门证据存在 | 空 logcat → `PASS`（实测 `_effective_window(...) = ('PASS', [])`） | 每个 run 至少 1 条 `SMB_LOWLOAD_GATE: verdict=GATED`，否则 `gate_evidence_missing` |
| instrument 退出码 | 脚本记录但**不传给报告**（`run_isolation_pair.sh:217-240,248-251`） | `exit=` 字段；非 0 → INVALID（注意 adb exit=0 也可能全 `-4`，见 D05） |
| 方法数/终态数 | 不检查 | `numtests`、`OK (N tests)`、终态条数 = 15 |
| round 对齐 | 不齐 → `KeyError`（实测 `EXC KeyError 3`）；重复 round 静默覆盖（实测 `rounds_per_arm` 从 3 掉到 1） | 两臂 round 集合必须相等；重复 round id → 输入错误 exit 2 |
| 构建身份 | 不检查 | `apk=` / `test_apk=` / `pkg=`；同臂全同、两臂 test 相同、OFF≠ON |

报告工具应把“输入不合法”（exit 2）与“判词为 UNCONVERGED”（exit 1）分开；现在 round 不齐是 traceback，不是判词。

### 4.4 判词规则（替代 `isolation_pair_report.py:349-408` 的现有判定）

| 条件（全部满足） | 判词 | 可否引用 |
|---|---|---|
| 任一构建身份/方法宇宙/round 对齐/门证据不符 | `UNCONVERGED`（带具体 reason） | 否 |
| 任一有效窗非 PASS，或臂内不稳定 | `UNCONVERGED` | 否 |
| 全部有效、15 方法全终态无 SKIP/UNKNOWN/缺失、两臂逐方法向量相同、k ≥ 6 | `PAIRED_RED_SET_IDENTICAL` + **等价上界** `1-0.05^(1/k)` | 只能说“未检出大于上界的差异” |
| 分歧 + 两臂写点数 0/6 与申报一致 + 预注册主方法 + 配对 McNemar p<0.05 | `T4_CAUSAL`（附 B 夹逼证据） | 是（限该主方法） |
| 其他分歧 | `PAIRED_RED_SET_DIVERGES_T4_UNVERIFIED` | 否 |

删除现有“identical 即 verdict、无任何附注”的形态（`:384-388`）：identical 必须带 `equivalence_upper_bound` 与 `k`，否则读者会把“没看到差异”当成“没有差异”。

### 4.5 统计：配对检验为主，Fisher 只作敏感性

- **主检验**：精确 McNemar，`p = 2·P(X ≤ min(b,c))`，`X~Bin(b+c,0.5)`（`isolation_pair_report.py:228-234`；NIST McNemar 页确认小样本用 `BINPPF(α/2,0.5,b+c)`）。全部分歧同向时 p = `2^(1-k)`：k=4→0.125、k=6→0.031、k=8→0.0078。
- **敏感性**：精确 Fisher 完美分离（现 `:390-394`），k=4→0.0286。它是**非配对**的，忽略了同 round 的相关性，偏乐观；只能并列报告，不能单独触发因果。
- **多重性**：不预注册主终点而检验 15 个方法 → Bonferroni α=0.00333；McNemar 最小 k=**10**，Fisher 最小 k=6。推荐**预注册 1 个主方法**（机制上最贴近 1500 ms 硬期限的 `aHungWorkerReleasesTheCallerWithinHardPlus500ms`），其余 14 个标 exploratory，避免多重性惩罚把 k 推到 10。
- **等价上界**：k 轮零分歧时，`|P(OFF red) − P(ON red)| ≤ P(discordant)`，后者 95% 单侧上界为 `1−0.05^(1/k)`（NIST 精确二项：解 `BINCDF(0;p_u,k)=0.05`）。

| k | 零分歧上界 | 含义 |
|---|---|---|
| 3 | 63% | 现行允许的最小轮数，几乎无信息 |
| 6 | 39% | McNemar 刚够显著 |
| 8 | 31% | 推荐默认 |
| 11 | 24% | |
| 19 | 15% | 要写“≤15%”的最低轮数 |
| 29 | 10% | |

---

## 5. n 与随机化

### 5.1 随机化：块内 AB/BA，不写死奇偶

现行 `round % 2` 决定顺序（`run_isolation_pair.sh:201-206`）：k=3 时 OFF 在 3 轮里有 2 轮跑第一，且第一轮永远最冷；臂位置与轮次/时间趋势相关。

**改为**：

1. **弃置 1 个预热轮**（不进分析；批次 40 §4.1 已提出“首窗不计入配对”）；
2. 有效轮按 **2 轮一块**：每块内严格一 AB、一 BA（平衡块），**首块的方向**由预注册种子决定（`random.Random(seed).random() < 0.5` 选 AB 或 BA），其后每块取相反方向；种子与生成脚本入 manifest；
3. 每轮记录 `arm_position ∈ {first, second}`；分析前检查每臂 first/second 计数差 ≤1，否则判 `DESIGN_UNBALANCED`；
4. 随机化单位是**轮**，不是方法；同一轮两臂共享时间/宿主，配对分析才成立；
5. 不允许“看结果再补轮”（§2.4）。

为什么不是完全随机：k 小的时候完全随机很容易 6:2 失衡，平衡块保证每个偶数 k 上位置严格对半；代价是块内顺序可预测（第二臂已知），但分析不依赖盲法，只依赖可核的顺序记录。

### 5.2 n 建议与成本

| 目标 | 每臂有效 k | 总轮数（含弃置） | 估算时长 | 说明 |
|---|---|---|---|---|
| 只做“可复现性筛查” | 6 | 7 | ~1.8 h | McNemar 刚过 0.05；零分歧上界 39%，不能写排除 |
| **默认** | **8** | **9** | **~2.2 h** | 主终点全分离 p=0.0078；上界 31% |
| 全 15 方法筛查 | 10 | 11 | ~2.6 h | Bonferroni 后 McNemar 仍可 <0.05 |
| 要写“未检出 ≥15%” | 19 | 20 | ~4.5 h | 分两场，每场 10 轮；两场之间重标地板并做 §2.3 等价检查 |

成本按实测件估算：套件串行约 61 s（R5 §7 引批次 40 `step04:29-30`）；每臂一个 5 min 门（`lowload_gate.py:78`）+ 跑 + 快照 ≈ 6.1 min；每轮两臂 ≈ 12.2 min；地板标定每臂 ≈ 7.3 min（settle 120 s + 窗 310 s，`:560-565`）；每臂重装 ≈ 15–30 s。

### 5.3 预注册清单（跑前冻结，写进证据目录）

- 方法清单文件 sha、主终点方法名、判词规则版本（`isolation_pair_report.py` sha）；
- 两臂 app APK sha、test APK sha、签名证书 sha、构建命令、hunk diff sha；
- 种子与块序、k、弃置轮编号、门的全部阈值与地板文件 sha；
- 等价界 δ（若打算写“排除”）与对应的最小 k；
- 失效处置规则（§2.4）与“不得补轮”声明。

---

## 6. 误判风险表

| # | 风险 | 触发 | 证据 | 后果 | 缓解 |
|---|---|---|---|---|---|
| 1 | 假绿：OFF 没跑也 IDENTICAL | 日志截断/空 | 实测 A/B | 把“零执行”写成“配对一致” | 方法宇宙 + 缺失物化 + 终态数 |
| 2 | 假绿：门证据缺失算 PASS | logcat 为空 | 实测 `('PASS', [])` | 无窗也通过 | 至少 1 条 `verdict=GATED` |
| 3 | 假绿：装错 APK | 标签与产物不符 | `IsolationArms.kt:69-70` 常量自述 | 两臂其实是同一构建 | 设备 sha + dex 写点数 |
| 4 | 假红：post 窗把收尾当环境 | 套件退出后立即开窗 | batch 41 5/6 INVALID | 配对永不收敛 | post 负载只作诊断；有效性看宿主时间线 |
| 5 | 假红：自证探针 own_cpu_busy | 进程启动期测自耗 | D05 3/3、3.1 s/3.1 s | 15 例全跳 | 记录并继续；探针避启动期 |
| 6 | 选择偏差：按 warm-up 筛窗 | T4 写点在 ready 路径 | 归因 §2.5 第 3/4 写点 | 因果估计有偏 | 只作敏感性分析 |
| 7 | 选择偏差：重跑到过 | 门失败后反复重开 | R5 L-7 | 红集被筛成绿 | 预注册失效规则 |
| 8 | 统计口径错配 | 配对设计用非配对 Fisher | `:390-394` | k=4 就宣称因果 | McNemar 为主 |
| 9 | 多重性 | 15 方法各测 | 15 个 @Test | 假阳性 ~1−(1−0.05)^15 | 预注册主终点或 k≥10 |
| 10 | 把“无差异”当“无效应” | identical 直接收口 | 上界表 | 以 63%（k=3）的精度宣称排除 | 报告等价上界 |
| 11 | 构建差异混入 | OFF 构建夹带其他改动 | 无 diff 夹逼 | 差异不能归 T4 | hunk sha + 类/方法名集合 + 写点数 |
| 12 | 测试尺子不同 | 两臂 test APK 不同 | batch 41 两套 test sha | 差异可能来自测试代码 | test APK 单次构建/安装 + sha 校验 |
| 13 | 多 dex 漏扫 | 只扫 classes.dex | 实测 6 个写点全在 classes4.dex，classes.dex 为 0 | ON 误判 OFF | 遍历全部 `classes*.dex` |
| 14 | 数据初态混杂 | 不 clear，round 2 起继承 | 未测 | 臂序与数据状态混杂 | 每臂 `pm clear` 或登记为混淆项 |
| 15 | round 不齐崩溃 | 缺轮/重复轮 | 实测 KeyError/覆盖 | 无判词或静默丢轮 | 输入校验 exit 2 |

---

## 7. 来源表（URL + 采纳/拒绝 + 理由）

> 通道说明：本机 `developer.android.com` 连接超时（`74.125.*:443`，10 s），与 R5 §9 的观测一致；Android 侧结论改用**设备上实跑的命令输出**（`cmd package help`、`dumpsys package`、`sha256sum`）作为一手证据，官方网页只作交叉参考。

| ID | 来源 | 裁定 | 理由 |
|---|---|---|---|
| S1 | https://www.itl.nist.gov/div898/software/dataplot/refman1/auxillar/mcnemar.htm | 采纳 | 明确 McNemar 只由 `b+c` 决定、小样本用 `Bin(b+c,0.5)`；与 `isolation_pair_report.py:228-234` 的实现一致 |
| S2 | https://www.itl.nist.gov/div898/software/dataplot/refman2/auxillar/exacbino.htm | 采纳 | 精确二项单侧上界解 `BINCDF(0;p_u,n)=α` → 零事件上界 `1−α^(1/n)`；本机已用它复算 §4.5 表 |
| S3 | https://en.wikipedia.org/wiki/Rule_of_three_(statistics) | 采纳（仅作对照） | 3/n 是近似，且在小 n 偏保守（n=8 时 0.375 vs 精确 0.312）；本文用精确式，3/n 只作交叉检查 |
| S4 | https://pmc.ncbi.nlm.nih.gov/articles/PMC7808096/ | 采纳 | 非劣效/等价检验的定义与“必须在试验前设定并记录界值”；支持“identical ≠ 排除”和 δ 预注册 |
| S5 | https://www.bmj.com/content/311/7003/485 | 采纳 | “absence of evidence is not evidence of absence”的经典表述；支持判词不得写“排除” |
| S6 | https://ubc-stat.github.io/stat301book/ch02_abPeeking.html | 采纳（单一来源，待验证） | 早停/中途查看会抬高一类错误；与本仓 R5 L-7“不得重跑到过”同向。属课程教材，非标准机构文档 → 标待验证 |
| S7 | https://statacumen.com/teach/ADA2/notes/ADA2_notes_Ch05.pdf | 采纳 | 随机区组/配对实验把区组变异从处理比较中剔除；支持“以轮为块”的设计 |
| S8 | https://www.clinicalbiostats.com/tutorials/randomized-block-design/ | 采纳（与 S7 互证） | 区组内随机化处理，而非整场完全随机 |
| S9 | `adb -s emulator-5562 shell cmd package help`（实跑） | 采纳（一手） | `path` 的官方 shell 帮助原文：“Print the path to the .apk of the given PACKAGE.”；比不可达的网页更可靠 |
| S10 | https://developer.android.com/tools/adb | **拒绝（不可达）** | 连接超时；不作为任何结论的依据 |
| S11 | `apkanalyzer dex packages --defined-only` / `dexdump -d`（本机 build-tools 36.0.0 实跑） | 采纳（一手） | 类/方法清单与调用点计数的实际输出（§9）；官方页面不可达，以工具实跑为准 |

---

## 8. 落地改动清单、对应关系与阻塞

### 8.1 每项新件消灭的具体失败

| 改动 | 落点 | 消灭的具体失败 |
|---|---|---|
| 方法清单 + 缺失物化为 `UNKNOWN` + 终态数校验 | `tools/ci/isolation_pair_report.py`（WS1 1/2） | 实测的“OFF 空日志 → IDENTICAL”假绿 |
| `--run` 增 `apk/test_apk/pkg/exit`；round 集合校验 | 同上（WS1 1） | 装错 APK、round 不齐 KeyError、重复轮静默覆盖 |
| 门证据必须 ≥1 条 `GATED` | 同上（WS1 1） | 空 logcat 判 PASS |
| `t4_control_marker` 增 artifact/installed 段并从设备重算 | `tools/ci/t4_control_marker.py`（WS1 4） | 源码树对、设备是旧包仍通过 |
| 臂身份检查：预注册 sha ↔ 设备 sha ↔ 全 dex 写点数 ↔ 类清单 | `tools/ci/run_isolation_pair.sh`（WS1 3） | 臂只由 `-e` 参数区分，产物不可核（替代 F8 的“换组件”选项，理由见 §3.3 注） |
| 门-跑-门串联、取消 post 负载门（只留诊断） | 同上（WS4 36） | 5/6 post INVALID 导致永不收敛；且避免用后处理量筛样本 |
| 块随机化 + 弃置轮 + 顺序记录 | 同上（WS4 36） | 奇偶顺序与时间漂移混杂 |
| 测试侧钩子两臂恒开 | `androidTest/.../IsolationArms.kt`、`VisualJsIsolationInstrumentedTest.kt`（WS4 35） | 测试观测成为第二个处理 |
| 自证探针记录并继续 | `androidTest/.../LowLoadProbe.kt` 及门前置（WS4 36） | D05 全 `-4` 吃掉整场 |
| 地板等价门 | `tools/ci/lowload_gate.py` 旁路比较或新脚本（WS4 36） | 两臂各过各的门，环境不可比 |

### 8.2 与计划的对应

- WS1 1/2 → §4.1–4.4；WS1 3/4 → §3.1–3.6（F8 的“换组件/拒同 COMPONENT”被 §3.3 注的“产物身份”替代，需主会话确认）；WS4 34 → §3.2/§4；WS4 35 → §3.6；WS4 36 → §2.1–2.4；WS4 37 → §4.5；WS4 38 → §4.4；WS4 39/40/41 → §5。

### 8.3 本批未做 / BLOCKED（如实）

| 项 | 状态 | 原因 / runbook |
|---|---|---|
| OFF 臂构建 | **BLOCKED** | 需要回退 `VisualJsClientEngine.kt` 的 6 处写点（源码改动，属主会话/落码轨）；runbook：按 `2026-11-01-isolation-attribution.md:288-302` 的 M2 逐条编辑 → 只跑 `assembleStrictOfflineDebug` + `assembleStrictOfflineDebugAndroidTest` → 记录 hunk sha 与 APK sha |
| 两臂实跑（15 例 × k） | **BLOCKED** | 依赖上一条；且须按 §5.3 先冻结预注册 |
| OFF 臂 dex 写点数 = 0 | **UNVERIFIED** | 本机只验证了 ON 包（批次 41 旧包）数到 6；OFF 包未构建 |
| 地板等价阈值 | **UNVERIFIED（设计值）** | 需两臂各标定一次后回填 |
| `pm clear` 对套件初态的影响 | **UNVERIFIED** | 未实跑；首场前需单臂试跑 |
| 两场分跑的合并规则 | **UNVERIFIED** | 需两场地板等价 + 同 APK sha 才可合并；否则只能各自出报告 |
| `adb install` 后 base.apk 与构建产物逐字节相同 | **本机已验证一例** | 5562 上设备侧 `sha256sum` 与 `exec-out cat` 取回后本地 sha 相同（§9）；不同机型/安装器（incremental/fastdeploy）未验证 |
| instrumentation 自报 target APK sha | **未落码 / UNVERIFIED** | 需在 androidTest 里读 `targetContext.applicationInfo.sourceDir` 流式 sha256；本机未写、未跑 |

---

## 9. 复现命令与实际输出（本机 2026-11-03/04）

```text
$ adb devices -l
emulator-5562  device product:sdk_gphone64_x86_64 …
emulator-5566  device …
emulator-5584  device …

$ adb -s emulator-5562 shell pm list instrumentation
…（三条 core 模块的略）
instrumentation:com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner (target=com.tingyun.smartmistakebook.offline)

$ adb -s emulator-5562 shell cmd package path com.tingyun.smartmistakebook.offline
package:/data/app/~~gbUcA2FtAUABkQ1gRkOVwA==/com.tingyun.smartmistakebook.offline-ZhdWTMWKaIdRDNORiASQwQ==/base.apk

$ adb -s emulator-5562 shell dumpsys package com.tingyun.smartmistakebook.offline | grep -E "versionCode|versionName|codePath|lastUpdateTime"
codePath=/data/app/~~gbUcA2FtAUABkQ1gRkOVwA==/com.tingyun.smartmistakebook.offline-ZhdWTMWKaIdRDNORiASQwQ==
versionCode=2 minSdk=23 targetSdk=36
versionName=0.2.0-offline
lastUpdateTime=2026-11-01 17:27:31

$ adb -s emulator-5562 shell sha256sum <base.apk>
fe9452aab64d6fcf9d840a0e5e5c542d43daed176da2bf5aa71370d766d69e3e  /data/app/…/base.apk
$ adb -s emulator-5562 exec-out cat <base.apk> > /tmp/…/base.apk && sha256sum /tmp/…/base.apk
fe9452aab64d6fcf9d840a0e5e5c542d43daed176da2bf5aa71370d766d69e3e  /tmp/…/base.apk   # 设备侧与取回后一致

$ apkanalyzer dex packages --defined-only base.apk | grep "smartmistakebook.visualjs" | grep -E "^C "
C d 13 13 611 com.tingyun.smartmistakebook.visualjs.ConnectStageTimestamps

$ # 类/方法名提取（单臂验证；两臂 diff 未跑）
$ apkanalyzer dex packages --defined-only base.apk | grep -E "^[CM] " \
    | awk '$1=="C"{print "C",$6} $1=="M"{print "M",$6,$NF}' | grep ConnectStageTimestamps | head -3
M com.tingyun.smartmistakebook.visualjs.VisualJsClientEngine readResultPayload(android.os.ParcelFileDescriptor,com.tingyun.smartmistakebook.visualjs.ConnectStageTimestamps,kotlin.coroutines.Continuation)
C com.tingyun.smartmistakebook.visualjs.ConnectStageTimestamps
M com.tingyun.smartmistakebook.visualjs.ConnectStageTimestamps <init>()

$ # 全 dex 的 setter 调用点（dexdump 36 的 ';.' 形状）
== dex/classes4.dex
      1 setBindConnectedAtMillis
      1 setBindRequestedAtMillis
      1 setExecuteAcceptedAtMillis
      1 setFirstByteAtMillis
      2 setReadyAtMillis
# 合计 6，且全部在 classes4.dex；classes.dex 中 "ConnectStageTimestamps" 出现 0 次
# （只扫 classes.dex 会得到 0 → 把 ON 误判成 OFF）

$ # F7 探针（真实模块，非 fixture）
A empty OFF -> PAIRED_RED_SET_IDENTICAL [] paired= True
B missing mBeta -> PAIRED_RED_SET_IDENTICAL []
C missing vs red -> PAIRED_RED_SET_DIVERGES_T4_UNVERIFIED
D both skipped -> UNCONVERGED ['skipped_or_unknown']
R.empty_logcat_effective_window = ('PASS', [])
round mismatch -> EXC KeyError 3
duplicate rounds -> rounds_per_arm=1, UNCONVERGED ['rounds_short']

$ # 统计表（本机 python -I 复算）
k=4: fisher 0.028571 / mcnemar 0.125
k=6: fisher 0.002165 / mcnemar 0.03125
k=8: fisher 0.000155 / mcnemar 0.007812
零分歧上界 k=3/8/19/29 → 0.632/0.312/0.146/0.098
```

> 说明：5562 上的是批次 41 的旧 `.offline` 包（ON 形态），本节只证明“身份链的方法可跑”，不构成任何 OFF/ON 结论。

---

## 10. 一句话结论

在编译期回退的形态下，能成立的不是“同 APK sha 的 OFF/ON”，而是“**同 test APK、每臂内同 app sha、两臂 app 差异被夹到预注册 6 处写点**”的配对；D 的排除靠串联的低负载门 + 块随机化 + 地板等价，T4 的排除靠设备侧 sha/全 dex 写点数/类清单三件套，而“红集相同”只能给出 `1−0.05^(1/k)` 的等价上界——k<19 时连“≤15%”都写不了，因而 **k=3 的 `PAIRED_RED_SET_IDENTICAL` 不具备任何排除力**。
