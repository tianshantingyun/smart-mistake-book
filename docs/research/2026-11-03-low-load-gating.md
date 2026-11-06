# R5 模拟器低负载窗的程序化判定（loadavg / `/proc/stat` / 进程快照 / 采样间隔 / 阈值）

| 项 | 值 |
|---|---|
| 编号 | R5（批次 41 计划 `.zcode/plans/plan-2026-11-03-batch-41.md:20`；落点 WS4 项 25/28） |
| 日期 | 2026-11-03（实测 2026-11-02 15:2x–15:5x +08:00，设备时钟 07:2x–07:5x） |
| 结论级别 | 判据已在本机 4 台在跑模拟器上**只读取样验证**（30 s 级可行性采样）；**5 min 正式门、instrumentation 探针均未实跑**（见 §8.3） |
| 实测设备 | `emulator-5584`（AVD `kb_test_device`，API 34，4 vCPU）、`emulator-5562`（`test_device`，API 34，4 vCPU）、`emulator-5566`（`audit_full_ui_forest_20261028`，2 vCPU）、`emulator-5596`（`api23_smoke`，kernel 3.10） |
| 方法约束 | 未跑 Gradle、未跑 instrumentation、未做 git 写、未改设备设置；采样全部为 `adb shell` 只读命令 |
| 输入 | 批次 39 记录 `docs/research/2026-11-01-batch-39-record.md:16,172`；批次 40 `docs/research/evidence/2026-11-02-batch-40/step04-isolation.md:11-18,36-54,67`；`docs/research/2026-11-02-batch-40-record.md:316` |

---

## 0. 结论摘要（照做清单）

1. **低负载门只能放在宿主 + shell 侧**：API 34 上 app UID 读 `/proc/stat`、`/proc/loadavg`、`/proc/uptime`、`/proc/pressure/*` 一律 `EACCES`（实测，§2.2），且 AOSP 侧对该读法自 Android O 起就是"禁读 + `dontaudit`"（§2.3）。instrumentation 内**读不到**这三类信号，任何在用例里实现的 loadavg 门若包 `try/catch` 就会静默放行。
2. **唯一可用的负载语义是相对地板，不是绝对常数**：`load < 1.0` 这类判据（批次 40 `step04:67` 的 I50-1）在 4 vCPU guest 上等于"常驻 1 个可运行任务 = 25% 容量"，在 2 vCPU 上等于 50%——同一个数字含义不同，且套件自身就会把 `la1` 顶到 1.0 附近。**门必须在运行前、按 AVD 标定地板后判定**（§4.2–4.3）。
3. **主判据用 `/proc/stat` 的 CPU 忙比与 `la1/nproc`，`procs_running` 只作诊断**：实测区分度 busy 1.4% vs 11.3%、`la1/nproc` 0.005 vs 0.23；而 `procs_running` 在忙机上多数样本仍是 1（§2.4、§4.3）。
4. **再加一条"唤醒延迟"探针**，它才是隔离套件真正敏感的量：套件最紧余量是 1500 ms 硬期限上的 500 ms（`VisualJsIsolationInstrumentedTest.kt:890-891`、`VisualJsService.kt:40`）。实测同一探针在静机 p95 超时 14 ms、忙机 95 ms（§2.5），4–7× 分离。
5. **采样间隔取 5 s，一段窗取 5 min（60 样本）**：5 s 是内核 `LOAD_FREQ`（`5*HZ+1`）的自然粒度（§3.1）；5 min = 5 个 `la1` 时间常数，窗口前的事件残余权重 < 1%（§3.1）。判读方式是"60 样本无缺失 + 每个样本过 `max` 界 + 分布过 `p95` 界"，不取平均、不剔最大值。
6. **模拟器上 `steal`（`top` 的 `%host`）、PSI、thermal 全部不可用**：`steal` 实测恒 0（忙机也是 0），PSI 文件存在但 shell/app 都读不到，`dumpsys thermalservice` 的 `Thermal Status: 0` 是假温度传感器（§2.2、§2.6）。宿主争用**不会**体现在 guest 的 loadavg/steal 上，只能靠宿主侧采样 + 单机独跑策略（§4.6）。
7. **红集比较的统计上限要写死**：15 个用例、每臂 3 轮时，即使"ON 全红 / OFF 全绿"完美分离，精确 Fisher 检验 p = 0.10，**不显著**；要 p < 0.05 至少 4 轮完美分离（p = 0.0286）。所以红集比较只能用于"窗口稳定性筛查"，不能用于单例因果归因（§5.3）。
8. **批次 40 的 A3"kill 总数必须 = 5 且集合严格相等"是过拟合判据**：负载敏感计数天然带 ±1 抖动，应改成结构性不变量 + 每方法归因表（§5.4）。

---

## 1. 为什么需要这个门（问题陈述）

批次 39 的 isolation 三窗结果：15 例分别在 6/7/5 例红、kill 计数 10/10/6（设计基数 5）、三窗红例集合各不相同，而同会话其余 199+ 例全绿（`2026-11-01-batch-39-record.md:16,172`）。这种"红集随窗口漂移"的形状只说明一件事：**窗口间的环境差异已经大于被测开关（T4）的效应**。批次 40 因此只敢记"单臂 15/15 绿"（`step04:11,75`），并明确"低负载门未建立"（`step04:67`）。

门要解决的不是"让结果变绿"，而是让**红集这个量有定义**：只有当两次运行处于同一可判定的低负载状态时，"红集不同"才可能被解释为代码或开关的差异；否则它只是宿主调度噪声的投影。批次 40 的 I50-1 写的是"load<1.0 持续 5 min"，方向对但阈值未归一化、且未规定采样与判读方式（§0 第 2 条）——本文把它替换成可执行、可复核、可复跑的判据。

---

## 2. 本机实测：环境、权限与信号可用性

### 2.1 环境

| serial | AVD | vCPU | kernel | 首个 `la1` |
|---|---|---|---|---|
| 5562 | `test_device` | 4 | 6.1.23-android14-4 | 1.38 |
| 5566 | `audit_full_ui_forest_20261028` | 2 | 6.1.23-android14-4 | 0.38 |
| 5584 | `kb_test_device` | 4 | 6.1.23-android14-4 | 0.01 |
| 5596 | `api23_smoke` | —（无 `nproc`） | 3.10.0+ | 0.09 |

四台同宿主并发在跑（宿主 32 逻辑核）；`/proc` 挂载为 `rw,relatime,gid=3009,hidepid=invisible`（实测 `grep -w /proc /proc/mounts`）。`5596` 上没有 `nproc`、没有 `/proc/pressure` —— **判据必须做能力探测，不能假定 API 34 的能力**。

### 2.2 权限矩阵（实测，`run-as com.tingyun.smartmistakebook.offline`，uid 10193，SELinux `Enforcing`）

| 信号 | shell UID | app UID | 结论 |
|---|---|---|---|
| `/proc/stat` | 可读 | **Permission denied** | 只能 shell 侧做门 |
| `/proc/loadavg` | 可读 | **Permission denied** | 同上 |
| `/proc/uptime` | 可读 | **Permission denied** | 设备侧时钟只能 shell 用 |
| `/proc/pressure/{cpu,io,irq,memory}` | **Permission denied** | **Permission denied** | PSI 在本镜像上**不可用**（文件存在，`ls` 显示 `?????????`，`stat` 报 EACCES） |
| `/proc/self/stat` | 可读 | 可读 | instrumentation 内唯一可用的 CPU 自证 |
| `/proc/<other-pid>/stat` | 可读 | **Permission denied**（pid 可枚举，内容不可读） | app 侧**无法**做进程快照 |
| `/proc/meminfo` | 可读 | 可读 | 只作辅助 |
| `/sys/devices/system/cpu/online` | 可读（`0-3`） | 可读 | `Runtime.availableProcessors()` 的交叉核对 |
| `/sys/.../cpu0/cpufreq/scaling_cur_freq` | 可读（约 5.3e6） | 可读 | 值是宿主频率量级的假值（同机 `cpuinfo_max_freq` = `2`），**不是利用率**，不得当负载 |
| `dumpsys thermalservice` | `Thermal Status: 0`、`test temperature sensor 30.8` | — | 模拟器无真实热源，thermal 门是空操作 |

### 2.3 机制来源（为什么 app 读不到）

AOSP sepolicy（API 34 预置）对全部 app 域的 `proc_stat`/`proc_uptime`/`proc_vmstat`/`proc_zoneinfo` 读操作是 **`dontaudit`**（只静音审计，拒绝依然生效），注释写明 "These have been disallowed since Android O."（`prebuilts/api/34.0/private/untrusted_app_all.te:143-153`，镜像来源见 §9 表 L10）。`shell` 域另有显式放行（`private/shell.te:417-436` 同时允许 `proc_stat`、`proc_loadavg`、`proc_pressure_io`），与实测"shell 可读 stat/loadavg"一致；但本镜像上 PSI 连 shell 都拒，**以实测为准，不以策略文本推断能力**。

### 2.4 CPU 与负载实测（同一脚本、30 s、5 s 间隔，6 样本）

| serial | busy% 均值/最大 | `la1` 范围 | `procs_running` | `ctxt/s` |
|---|---|---|---|---|
| 5584（静） | **1.4 / 1.4** | 0.01–0.02 | 1–2 | 332–401 |
| 5562（忙） | **11.3 / 13.1** | 0.84–0.95 | 1–4 | 377–432 |

`busy = Δ(user+nice+system+irq+softirq+steal) / Δ(user+nice+system+idle+iowait+irq+softirq+steal)`；`iowait` 进分母但**不计 busy**（内核文档明说不可靠）；`guest`/`guest_nice` 单列不加（本机恒 0，且"guest 是否已计入 user"的表述在文档中不可靠，加进去可能双计）。结论：**`procs_running` 和 `ctxt/s` 区分度弱，不得当主判据**（`procs_running` 含采样器自身 shell）；`busy%` 与 `la1/nproc` 才是主判据。

### 2.5 唤醒延迟探针（同一命令形状：`date +%s%N` → `sleep 0.05` → `date +%s%N`，n=40）

| serial | 中位超时 | p95 | max |
|---|---|---|---|
| 5584（静） | +11 ms | **+14 ms** | +17 ms |
| 5562（忙） | +43 ms | **+95 ms** | +114 ms |

绝对底噪含 2 次进程派发（`date`），所以**门值要按同形状探针在本机标定**，跨机只比比值。这个探针直接对应"1500 ms 期限里还剩多少余量"，比 `busy%` 更贴近套件的失败机理。

### 2.6 三个"看起来能用、实测不能用"的信号

1. **`steal` / `top` 的 `%host`**：toybox 的 CPU 汇总列是 `user nice sys idle iow irq sirq host`，第 8 项就是 `/proc/stat` 的 `steal`（`toys/posix/ps.c:1540,1570-1575`，内核文档称其 "involuntary wait"）。实测静机 0%、忙机 0% —— 本镜像不做 steal 记账，**宿主争用不会进 guest**。
2. **PSI**：语义完备（`some/full` + `avg10/60/300` + `total`，`docs.kernel.org/accounting/psi.html`），但本镜像 shell/app 都读不到（§2.2）→ **拒绝作为判据输入**。
3. **thermal**：`PowerManager.currentThermalStatus` 是 androidx 官方在 API 29+ 用的热限流判据，但它对模拟器明确跳过（"don't bother with emulators, will always be unpredictable"，`ThrottleDetector.kt`）；本机 `Thermal Status: 0` 与假温度传感器证实这一点 → 只能记录，不能当门。

---

## 3. 指标语义与工具口径（判据的物理依据）

### 3.1 `loadavg`：EWMA(nr_running + nr_uninterruptible)

- 定义（内核注释）："The global load average is an exponentially decaying average of `nr_running + nr_uninterruptible`"（`kernel/sched/loadavg.c:18-19`）。
- 更新粒度："`LOAD_FREQ (5*HZ+1) /* 5 sec intervals */`"；衰减常数 "`EXP_1 1884 /* 1/exp(5sec/1min) */`"（`include/linux/sched/loadavg.h:20-23`）。
- 推论 1：**含 D 态**。virtio/vsock/磁盘 I/O 阻塞会抬高 `la1`，但 CPU 可能完全空闲 → 假"忙"。
- 推论 2：**必须除以 vCPU 数**。一个持续可运行线程的稳态 `la1 → 1.0`；4 vCPU 上那是 25% 容量，2 vCPU 上是 50%。
- 推论 3：5 min 窗 = 5τ，窗口开始前的事件残余权重 `e^-5 ≈ 0.7%` → "5 min 全过"是让早期尖峰自然衰减的**最短**合理窗。
- 推论 4：`la1` 的**瞬时**跳变幅度可估：4 线程忙 5 s 使 `la1` 上升 `4*(1-e^{-5/60}) ≈ 0.32`。

### 3.2 `/proc/stat`

- 字段（内核文档）：`user nice system idle iowait irq softirq steal guest guest_nice`，单位 USER_HZ（"typically hundredths of a second"）；`procs_running` = "total number of threads that are running or ready to run"；`ctxt` = 全 CPU 上下文切换总数。
- **`iowait` 不可靠**：文档原文 "the iowait is not reliable by reading from /proc/stat." → 计数时把 `iowait` 计入 idle（或单列，不进 busy）。
- 分辨率：100 Hz × 5 s × 4 vCPU = 2000 jiffies/样本 → 0.05% 量化，足够；1 s 间隔降到 400 jiffies（0.25%），仍够但无收益。
- `steal` 语义为 "involuntary wait"（即宿主侧被抢走的时间）——机制上正确，但本镜像恒 0（§2.6）。

### 3.3 三个 CLI 的"窗口"不一样（最容易踩的坑）

| 工具 | 数值窗口 | 依据 |
|---|---|---|
| `ps -o %CPU` | **进程生命周期均值**（"Total %CPU used since start"），看不到当前尖峰 | `toys/posix/ps.c:374,377`（字段语义）+ `:590`（除以 `SLOT_upticks` = `uptime-starttime`，见 `:832`） |
| `top -b -d N` | 窗口值（top 把 `SLOT_upticks` 改写成采样间隔，`:566` 注释、`:1478` 赋值），**但首轮只等 250 ms** | `toys/posix/ps.c:1578-1580`（"First time, wait a quarter of a second"）；实测静机首轮出现 `12% sys`，第二轮 `5% sys` |
| `dumpsys cpuinfo` | 一个**滞后约 5 min** 的 300 s 窗 | 实测首行 `CPU usage from 584532ms to 284523ms ago`（窗尾距当前约 4.7 min） |

→ 判据只用 `/proc/stat` 的原始差分自己算；`top` 只用于"谁在占"（并用 `-n 2` 丢弃首轮），`ps %CPU` 只用于长期大户排查。

---

## 4. 判据（L-1…L-7，可直接执行）

### 4.1 L-1 采样器：设备内循环、5 s、UTC 无关的单调时钟

```sh
# 60 样本 = 5 min；时钟取 guest 的 /proc/uptime，避免 adb 往返把间隔拉长
adb -s emulator-5584 shell 'i=0; while [ $i -lt 60 ]; do
  up=$(cut -d" " -f1 /proc/uptime); la=$(cat /proc/loadavg)
  cpu=$(grep -m1 "^cpu " /proc/stat); run=$(grep -m1 "^procs_running " /proc/stat)
  blk=$(grep -m1 "^procs_blocked " /proc/stat); ctx=$(grep -m1 "^ctxt " /proc/stat)
  echo "$up|$la|$cpu|$run|$blk|$ctx"; i=$((i+1)); sleep 5
done > /data/local/tmp/lowload.csv'
adb -s emulator-5584 pull /data/local/tmp/lowload.csv
```

要点：**必须**在设备侧循环并落盘，再 pull。宿主机逐样本 `adb shell` 会让间隔抖动到 5.4–6.0 s（本次实测 6 样本间隔 5.4/5.6/5.1/5.5/5.0/6.0 s），把 `busy%` 的系统误差拉到 10% 量级。

### 4.2 L-2 地板标定（每次换 AVD/宿主/APK 都要重做）

1. 装包后 **settle 120 s**（dex2oat/MediaProvider 扫描/首次 boot 作业都在前 1–2 min）。
2. 同一命令跑 5 min，得 `floor_p95`/`floor_max`：`busy%`、`la1/nproc`、`ctxt/s`、唤醒探针 p95。
3. 地板与门值一起写进 E 文件（`floor` 是可审计的输入，不是常数）。

### 4.3 L-3 统计判据（60 样本无缺失；每样本过 `max` 界，分布过 `p95` 界；不取平均、不剔最大值）

| ID | 量 | 门值 | 依据/实测 |
|---|---|---|---|
| L-3a | `busy%` | `p95 ≤ floor_p95 + 3.0 pp` 且 `max ≤ floor_max + 5.0 pp` | 静机 1.4%，忙机 11.3%（§2.4） |
| L-3b | `la1/nproc` | `p95 ≤ floor_p95 + 0.05` 且 `max ≤ 0.30` | 静机 0.005，忙机 0.225（§2.4） |
| L-3c | `ctxt/s` | `p95 ≤ 1.5 × floor_p95`（抖动代理） | 390 vs 400 区分弱，只作佐证 |
| L-3d | 唤醒探针 p95 | `≤ max(3 × floor_p95, 30 ms)`；`max ≤ 5 × floor_p95` | 静 14 ms / 忙 95 ms（§2.5） |
| L-3e | `procs_running` | 仅报告，不设门 | 忙机多数样本仍为 1（§2.4） |

按 §2.4/§2.5 的数字代入（以 5584 为地板）：`busy p95 ≤ 4.4%`、`la1/4 ≤ 0.055`、`探针 p95 ≤ 42 ms`。5562 三项分别为 13.1%/0.22/95 ms，**三项全不通过**——判据有区分力。注意这只是 30 s 可行性采样，**不是 5 min 正式门**。

### 4.4 L-4 离散排除（快照，不是统计）

```sh
adb -s <serial> shell 'ps -A -o NAME | grep -c "\.test$"; \
  ps -A -o NAME,ARGS | grep -c AndroidJUnitRunner; \
  ps -A -o NAME | grep -c ":visual_js$"'
```

三条都必须是 `0`（跑前）。注意：本镜像 `dumpsys activity | grep -i instrumentation` 什么都不输出（实测空），**不能**拿它当"无并发 instrumentation"的证据；`ps` 是可靠通道（批量列表实测 293 个任务可见，`hidepid=invisible` 只挡 `/proc/<pid>` 内容，不挡进程枚举）。

### 4.5 L-5 宿主侧（只判"本机是否在跑别的重活"，不当负载门）

```powershell
Get-Counter '\Processor(_Total)\% Processor Time' -SampleInterval 1 -MaxSamples 5
Get-CimInstance Win32_Process | ? { $_.Name -match 'java|kotlin|gradle' }
# 每台 emulator 的 qemu 进程 5 s CPU 增量（cores）
```

实测：宿主 %Processor Time 38.9/31.8/27.2（32 核）；qemu 5 s 增量 api23 0.09、5562 **1.86**、5566 1.38、5584 **1.14** cores。注意 5584 的 guest `busy` 只有 1.4% 却烧 1.14 宿主核——**qemu 的空闲轮询使"宿主 CPU"与"guest 负载"不是同一量**，所以宿主只能设**增量门**（`≤ floor + 0.5 core`）与"无构建进程"，不能设绝对百分比门（32 核与 8 核机器不可比）。

### 4.6 L-6 运行前确定性预置（缺一即门无效）

- `settings get global window_animation_scale` / `transition_animation_scale` 必须为 `0`（实测当前为 `1.0`/`1.0`，`animator_duration_scale` 为 `null`=默认）；并给 `am instrument` 加 `--no-window-animation`（`am help` 实测支持 `-r`、`-e`、`--no-window-animation`）。
- 同一时刻**只允许一台 emulator 跑重活**（本机 4 台并发：5562 1.86 + 5566 1.38 + 5584 1.14 cores）。
- APK sha256、源快照、AVD 名、serial、宿主标识与地板表一起入 E 文件。

### 4.7 L-7 门判定语义（fail-closed）

- 60 样本缺 1 个 → 窗 **INVALID**（不是"少一个也算过"）。
- 任一判定项单点越界 → 窗 **INVALID**，等 60 s 重新开窗；禁止"去掉一个最大值"。
- 门不过 → 该臂结果标 `UNGATED`，**不得**标 PASS，也不得反复重跑到过（后者把选择偏差写进红集）。
- instrumentation 内探针读不到信号 → `UNKNOWN`（不是 PASS）；见 §4.8。

### 4.8 instrumentation 内还能做什么（权限内）

app 域只读得到自己的东西（§2.2），所以 instrumentation 内的探针只能回答"**本进程当前是否被拖慢**"，回答不了"系统是否空闲"：

```kotlin
// 门前置（@BeforeClass，只读、不改生产码）
val cpus = Runtime.availableProcessors()                     // 交叉核对 /sys/.../online
val jitter = (1..200).map {                                  // 10 ms 睡眠的真实迟到量
    val t0 = SystemClock.elapsedRealtimeNanos(); Thread.sleep(10)
    (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0 - 10.0
}.sorted().let { it[(it.size * 0.95).toInt()] }               // p95，单位 ms
val ownCpuMs = /* /proc/self/stat 字段 14+15 的 500 ms 差分 × 1000 / USER_HZ */
val lowMem = ActivityManager.memoryInfo.lowMemory
val thermal = (getSystemService(POWER_SERVICE) as PowerManager).currentThermalStatus  // 模拟器恒 0
```

判读（两条，任一条越界即 `UNGATED`）：①有基线时 `jitter p95` 超地板 3 倍；②无基线时的**自比值** `p95_over / p50_over > 2`（实测静机 1.27、忙机 2.2，n=40，仅示意，正式门仍需复核）。`ownCpuMs` 明显高于空跑也记 `UNGATED`。**标 `UNGATED` 并保留数据，不 assumeTrue 跳过**（跳过会变成绿，和批次 40 反对的"单臂绿"同类）。口径提醒：`UNGATED` 究竟是"红"还是"无效样本"需要主会话裁定，本文只给判据不给裁定。

---

## 5. 门控协议与红集比较口径

### 5.1 协议（每臂）

```
settle 120 s → 门 5 min（L-3：60 样本）→ 快照（L-4）→ 臂 A 跑 1 次 → 门 2 min → 快照 → 臂 B → …
```

- 每个臂**前后各一次门**，两次都必须 PASS 才承认该臂；只记"跑前"会漏掉运行期间他线启动构建。
- 交替顺序 `A B A B A B`（k=3/臂），不用 `AAA BBB`：宿主负载随时间漂移，交替让漂移落在两臂之间而不是臂内。
- 两臂必须同一 APK sha、同一 AVD/serial、同一宿主、同一时间带；换任一项都要重标定地板。
- 跑中读数（`la1 ≈ 1.0`、`busy` 上升）**不是**门违规：套件自己就在制造负载；跑中只记"是否有外部闯入者"（进程快照），不记负载阈值。

### 5.2 红集从哪来（`-r` 原始输出）

`am instrument -r -w` 的原始流是"`test=` → `CODE`"配对，失败块里 `stack=` 出现在 `test=` **之前**（实测 `build/device-track-batch40/suites/suite-p09.log:50-60`：先 stack、再 `test=`、再 `CODE: -2`）。因此解析必须**顺序扫描**，不能用"最近一个 `test=`"回填，否则把失败算到下一个用例头上——这本身就是一次假红集。字段形状实测见 `build/device-track-batch40/suites/suite-isolation.log:1-14`（`class/current/id/numtests/stream/test` + `STATUS_CODE`）。判定：`0`=过；`-2`=FAILURE；`-1`=ERROR；`-4`=跳过（跳过**不算过**，要单列）。

### 5.3 比较口径（先算清统计上限，再谈结论）

- 数据：`15 方法 × {PASS, FAIL, ERROR, SKIP, UNGATED}` 的**向量**，不是集合；两个向量逐项相同才叫"红集一致"。
- 单臂内收敛：3 轮逐项一致 → `STABLE`；否则该臂 `UNSTABLE`，不进配对（`UNSTABLE` 本身就是结论：环境或套件不确定性过大）。
- 跨臂：只统计 discordant（A 过 B 红 / A 红 B 过）。精确 McNemar：`p = 2·P(X ≤ min(b,c))`，`X~Bin(b+c, 0.5)`。实测临界值：discordant 1/2/3/4/5/6 → p = 1.0/0.5/0.25/0.125/0.0625/**0.0312**。
- 完美分离（每臂 k 轮全红/全绿）的精确 Fisher：k=1/2/3/4 → p = 1.0/0.333/0.100/**0.0286**。
- **结论**：n=15、k=3 时，**不存在**能支持"某个用例因 T4 而红"的统计证据；能做到的只是"在低负载窗内红集 3 轮一致"→ 这是"结果可复现"的声明，不是因果。因果仍须字节可核控制轮（批次 40 I50-6：6 处写点回退 + 文件级还原 + sha 校验），本文不替代它。

### 5.4 kill 计数判据的修正（批次 40 A3 的直接教训）

批次 40 的 A3 被判 `FAIL as written`：判据写"集合严格相等且总数 = 5"，实测出现 `theClientRebuildsOnceAfterADeath…` 额外 2 次 watchdog kill（`step04:36-54`）。在负载敏感的量上要求**精确等于**，等于把噪声写成红。建议改为结构性不变量：

1. 必须杀的方法（4 个）各 ≥ 1 次 watchdog kill；
2. 必须不杀的方法（warm-up/ready/连接超时类）为 0；
3. 总 kill 数 `≥ 5`；每方法 kill 数进归因表；
4. 总数偏离设计基数只记 `WARN`，只有当"必须不杀"出现 kill 时才判红。

---

## 6. 误判风险表

| # | 风险 | 触发条件 | 实测/依据 | 后果 | 缓解 |
|---|---|---|---|---|---|
| 1 | 假通过：宿主争用不进 guest | 4 台并发、他线构建 | `steal`/`%host` 恒 0（§2.6） | 门 PASS 但套件被拖慢 | 宿主增量门 + 独占窗口（L-5/L-6） |
| 2 | 假通过：app 侧静默读失败 | `try/catch` 包住 `/proc/stat` | app 读一律 EACCES（§2.2） | 门"通过"因为没有数据 | fail-closed（L-7），`UNKNOWN ≠ PASS` |
| 3 | 假通过：门只在跑前测 | 跑中他线起构建 | 本机 4 emulator 并发 | 运行期被污染 | 跑前+跑后双门（§5.1） |
| 4 | 假通过：`dumpsys cpuinfo` 当当前值 | 直接读首行 | 窗尾滞后 ~4.7 min（§3.3） | 用旧窗口判新状态 | 只用 `/proc/stat` 差分 |
| 5 | 假通过：thermal 门 | 以为模拟器会热限流 | `Thermal Status: 0` + 假传感器（§2.2） | 热限流不可观测 | 只记录，不当门；物理设备才用 |
| 6 | 假失败：`loadavg` 含 D 态 | virtio/vsock/磁盘抖动 | 内核定义含 `nr_uninterruptible`（§3.1） | CPU 空闲却"负载高" | busy% 为主，`la1` 只作辅证 |
| 7 | 假失败：EWMA 记忆 | 窗口早期一次尖峰 | `τ = 1 min`，5 min 才衰减到 0.7% | 静下来后仍被判不通过 | 等 5 min 重开窗；不降阈值 |
| 8 | 假失败：`top` 首轮 250 ms | `top -b -n1` | `ps.c:1578-1580`；实测首轮 12% sys、次轮 5% | 把工具启动当负载 | `-n 2` 丢首轮；门不用 `top` |
| 9 | 假失败：`ps %CPU` 当瞬时值 | 用 `ps` 找"当前大户" | 生命周期均值（`ps.c:374,377`） | 看不到尖峰、误判无事 | 用 `/proc/<pid>/stat` 差分或 `top -d 5 -n 2` |
| 10 | 假失败：采样器自身开销 | 每样本 fork `cut/cat/grep` | 静机底噪 1.4%（§2.4） | 极严格门（如 ≤1%）永远不过 | 门值相对地板；或设备侧 awk 合并读取 |
| 11 | 假失败：adb 往返拉长间隔 | 宿主机逐样本 `adb shell` | 实测 5.0–6.0 s 抖动（§4.1） | `busy%` 系统误差 ~10% | 设备侧循环 + `/proc/uptime` 计时 |
| 12 | 归一化遗漏 | 2 vCPU（5566）与 4 vCPU（5584）同门值 | 同 `la1=1.0` 分别 50%/25% 容量 | 跨 AVD 不可比 | 一切 load 量除 `nproc` |
| 13 | 装包后立刻开窗 | dex2oat/媒体扫描 | 常见 1–2 min 后台 | 首窗必红 | settle 120 s（L-2） |
| 14 | 套件自身顶高 `la1` | 跑中读数 | 单线程稳态 `la1→1.0`（§3.1） | 把被测对象当环境噪声 | 跑中不作负载门（§5.1） |
| 15 | 能力探测缺失 | 在 API 23 镜像上跑 | 5596 无 `nproc`、无 PSI（§2.1） | 脚本空值/静默失败 | 每项先探测，缺失即 `UNKNOWN` |

---

## 7. 官方与 CI 实践对照（采纳/拒绝）

| 实践 | 来源 | 裁定 | 理由 |
|---|---|---|---|
| 模拟器结果不可代表真机，必须显式标 `EMULATOR_` 前缀、需显式 suppress 才放行 | androidx `Errors.kt:80-91,289-296` | **采纳** | 与本仓"不作真机结论"口径一致；门结果必须带"模拟器"标签 |
| API 29+ 用 `PowerManager.currentThermalStatus > NONE` 判热限流；模拟器跳过基线（"will always be unpredictable"） | androidx `ThrottleDetector.kt` | **采纳**（物理设备）+ **拒绝**（模拟器当门） | 本机 `Thermal Status: 0` 恒真，门无信息量 |
| 性能跑独立于正确性 CI；按 `device_name`/`os_version` 分别建图；指标给 P50/P90/P95/P99 | androidx `docs/performance/monitoring.md` | **采纳** | 直接支持"按 AVD 标定地板 + 分位数"的判据形状 |
| Orchestrator：每测一个新 instrumentation 实例、崩溃不级联、建议 `clearPackageData` | Flank `docs/test_sharding.md` | **采纳**（一般套件）/ **暂缓**（本套件） | 本套件故意杀 worker、依赖跨用例的连接计数与冷启动观测；默认开 Orchestrator 会改变被测行为 → 只能作独立臂 |
| 重试不能改变总数、且不能覆盖首轮结果（FTL `num-flaky-test-attempts` 的已知坑） | Flank `docs/feature/1692_test_case_based_retries.md` | **采纳** | 红集研究必须用**首轮**向量；重试只作补充列 |
| 不用并行 shard 跑时序套件 | Flank `docs/test_sharding.md`（FTL 会把空 shard 判失败） | **采纳** | 同宿主并行直接破坏低负载窗；15 例串行仅 61 s（`step04:29-30`） |
| `lockClocks`（`./gradlew lockClocks`） | androidx benchmark 文档（release 页）/ `Errors.kt:118-126` | **拒绝（本仓）** | 条件要求 rooted 且非 emulator（`!isEmulator && isRooted`）；本批共享设备禁改、禁 `adb root`（会重启 adbd），镜像是否可 root 未验证 |

---

## 8. 落地方案（WS4 项 25/28）与未验证项

### 8.1 新增件（每个都指认它消灭的失败）

| 新增 | 消灭的具体失败 | 形状 |
|---|---|---|
| `tools/ci/lowload_gate.py` | 批次 40 "无 5 min 干净窗"（`step04:67`）——门停留在人手判断 | 子命令 `calibrate` / `gate`；输出 CSV + `floor`/`verdict`；非 0 退出码表示 INVALID；与 `tools/ci/check_*.py` 同风格（含"消灭的具体失败"头注） |
| `tools/ci/isolation_pair_report.py` | 批次 40 A3 归因歧义 + 红集跨窗不可比（`step04:36-54`） | 解析 `-r` 流（顺序扫描 `test=`→`CODE`）→ 逐方法向量、kill 归因表、配对/一致率、精确 McNemar p |
| `app/src/androidTest/.../visualjs/LowLoadProbe.kt` | instrumentation 内"读不到就静默通过" | 只读 `/proc/self/stat` + 睡眠抖动 + thermal；`UNKNOWN` 时把该臂标 `UNGATED` |

三者都不触碰热区（`feature/*`、`core/ui`、`core/export`、`core/domain`、`settings.gradle.kts`、Application/Root）；`LowLoadProbe` 只进 androidTest。

### 8.2 与现有判据的替换关系

- 替换 I50-1 的"load<1.0 持续 5 min"→ L-3a/L-3b（`la1/nproc` + `busy%`，相对地板）。
- 补 I50-1 缺的采样与判读：L-1（5 s / 60 样本 / 设备侧时钟）、L-7（样本无缺失 + fail-closed）。
- 补 I50-5 的配对统计：§5.3（k≥4 才有 p<0.05 的完美分离）。
- 修正 I50-6 之外的 A3：§5.4（结构性不变量替代"精确等于 5"）。

### 8.3 未验证（不得当通过）

1. **5 min 正式门**：本次只做了 30 s 级取样（n=6），没有 60 样本全过窗。
2. **instrumentation 内探针**：`LowLoadProbe` 未落码、未在设备跑；§4.8 的 p95 门槛是设计值。
3. **`steal` 在更强争用下是否仍为 0**：只观测了静/忙两种状态，未做"故意压满宿主"的实验（会干扰共享设备）。
4. **PSI 在 `adb root` 下是否可读**：未尝试（共享设备禁改/禁重启 adbd）。
5. **跨宿主可移植性**：全部数据来自同一台 32 核 Windows 宿主。
6. **正式门在 API 23 镜像（5596）上的行为**：仅有能力探测结果，未跑门。
7. **动画置 0 / `--no-window-animation` 对套件时序的实际影响**：未测。

---

## 9. 来源表（URL + 采纳/拒绝 + 理由）

> 通道说明：本机 `developer.android.com`、`android.googlesource.com`、`cloud.google.com`、`firebase.google.com`、`raw.githubusercontent.com` 均连接超时（curl 28/000）；`docs.kernel.org` 与 GitHub API（`gh api`）可达。官方 Android 文档因此改用 **androidx 源码/文档仓**（Apache-2.0，GitHub 官方组织）作等价来源。

| ID | 来源 | 裁定 | 理由 |
|---|---|---|---|
| L1 | https://docs.kernel.org/filesystems/proc.html | 采纳 | `/proc/stat` 字段、`iowait` 不可靠、`loadavg` 三段均值与 runnable/total 的权威定义 |
| L2 | https://docs.kernel.org/accounting/psi.html | 拒绝（作判据输入） | 语义权威，但本镜像 shell/app 均读不到（§2.2）；保留为"若换镜像再评估" |
| L3 | https://github.com/torvalds/linux/blob/master/kernel/sched/loadavg.c | 采纳 | 定义 `la = EWMA(nr_running + nr_uninterruptible)`，解释 D 态污染 |
| L4 | https://github.com/torvalds/linux/blob/master/include/linux/sched/loadavg.h | 采纳 | `LOAD_FREQ = 5*HZ+1`、`EXP_1=1884` → 5 s 采样与 5 min 窗的物理依据 |
| L5 | https://github.com/androidx/androidx/blob/androidx-main/benchmark/benchmark-common/src/main/java/androidx/benchmark/Errors.kt | 采纳 | 官方对"模拟器基准不代表真机"的显式警告与 suppress 机制 |
| L6 | https://github.com/androidx/androidx/blob/androidx-main/benchmark/benchmark-common/src/main/java/androidx/benchmark/ThrottleDetector.kt | 采纳（API 29+）/ 拒绝（模拟器门） | 官方热限流判据实现；同一文件明确"emulator 不可预测" |
| L7 | https://github.com/androidx/androidx/blob/androidx-main/docs/performance/monitoring.md | 采纳 | 官方 CI 实践：性能独立轨道、按设备建图、P50/P90/P95/P99 |
| L8 | https://github.com/Flank/flank/blob/master/docs/test_sharding.md | 采纳 | Orchestrator 每测一进程/崩溃不级联；FTL 空 shard 判失败 → 本套件不并行 |
| L9 | https://github.com/Flank/flank/blob/master/docs/feature/1692_test_case_based_retries.md | 采纳 | 重试不得改总数、不得覆盖首轮 → 红集数据用首轮向量 |
| L10 | https://github.com/LineageOS/android_system_sepolicy/blob/lineage-24.0/prebuilts/api/34.0/private/untrusted_app_all.te | 采纳（镜像，非 AOSP 正本） | 解释 app 读 `proc_stat` 被拒的机制（`dontaudit` + "disallowed since Android O"）；正本 googlesource 不可达，**单一镜像来源已标待验证** |
| L11 | https://github.com/landley/toybox/blob/master/toys/posix/ps.c | 采纳 | `ps %CPU` = 生命周期均值；`top` 窗口化但首轮 250 ms；`%host` 即 `steal` |

---

## 10. 复现命令与实际输出（本机 2026-11-02）

```text
$ adb -s emulator-5584 shell 'nproc; cat /proc/loadavg; grep -m1 "^cpu " /proc/stat; grep -E "^(procs_running|procs_blocked|ctxt)" /proc/stat; ls /proc/pressure'
4
0.01 0.03 0.00 1/1677 13271
cpu  33818 2742 186828 20263063 15637 2 1617 0 0 0
ctxt 23489094
procs_running 1
procs_blocked 0
cpu io irq memory

$ adb -s emulator-5584 shell 'cat /proc/pressure/cpu'
cat: /proc/pressure/cpu: Permission denied

$ adb -s emulator-5584 shell 'run-as com.tingyun.smartmistakebook.offline sh -c "id; head -2 /proc/stat"'
uid=10193(u0_a193) ... context=u:r:runas_app:s0:...
head: /proc/stat: Permission denied

$ # 30 s / 5 s 采样（probe.py，见下）
serial=emulator-5584 online=0-3 start_loadavg=0.03/0.04/0.01 runnable=1/1677
t+  5.4s la1=0.02 busy=  1.3% runq=1 blocked=0 ctxt/s=    332
...
SUMMARY n=6 busy_mean=1.4% busy_max=1.4% la1_max=0.02 runq_max=2

serial=emulator-5562 online=0-3 start_loadavg=1.00/0.90/0.89 runnable=1/1828
t+  5.6s la1=0.92 busy=  9.7% runq=1 blocked=0 ctxt/s=    377
...
SUMMARY n=6 busy_mean=11.3% busy_max=13.1% la1_max=0.95 runq_max=4

$ # 唤醒探针（n=40，目标 50 ms）
5584: median_over=11ms p95_over=14ms max_over=17ms
5562: median_over=43ms p95_over=95ms max_over=114ms

$ # 宿主
Get-Counter '\Processor(_Total)\% Processor Time' → 38.9 / 31.8 / 27.2
qemu 5 s cores: api23 0.09 | 5562 1.86 | 5566 1.38 | 5584 1.14
```

采样脚本（研究用，未入仓；正式件建议落 `tools/ci/lowload_gate.py`）：本次脚本在宿主临时目录（`C:\Users\<user>\AppData\Local\Temp\lowload\probe.py`，仓库外），逻辑 = "两次 `adb shell cat /proc/loadavg; grep '^cpu ' /proc/stat` 差分 + `procs_running/ctxt`"，与 §4.1 的设备侧循环等价（差别：本次间隔受 adb 往返影响 5.0–6.0 s，正式件必须修正）。

---

## 11. 一句话结论

在模拟器上，**低负载窗只能由"宿主侧 + shell 侧的相对地板门 + 设备内 5 s/5 min 采样 + 唤醒延迟探针"共同判定**；app/instrumentation 侧因 SELinux 读不到系统负载，只能做 fail-closed 的自证探针。即使门成立，15 例套件的红集比较也只能支撑"结果可复现"，不能支撑单个用例的因果归因——因果仍必须靠字节可核的控制轮（I50-6）。
