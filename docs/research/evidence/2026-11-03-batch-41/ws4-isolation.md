<!-- 体积自查：296 行 / 21020 B；R4 上限 400 行 / 65536 B -->
# E-41-WS4 isolation 低负载前门 + OFF/ON 配对轮 + T4 可核标记

| 字段 | 值 |
|---|---|
| evidence-id | E-41-WS4 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | WS4（计划项 25–31；判据 I50-1/5/6/7/9 的程序化替代） |
| gate-or-kd | 低负载窗（R5 L-1…L-7）；OFF/ON 配对（R5 §5）；T4 控制轮（I50-6/7） |
| verdict | **PARTIAL / 未收敛**：低负载门 PASS；6 个 run 全 15/15 PASS，但 5/6 个 post-gate INVALID → 配对判词 **UNCONVERGED**（§5）；T4 控制轮**未做**（无生产侧回退），不得写“T4 排除” |
| executed-at | 2026-11-02T19:00–21:05+08:00（设备时钟 11:00–13:04 UTC） |
| operator | WS4 设备稳定性轨 |
| head | `62522930bc8bae51be1523c954f39449e054b6af`（工作树含他线 M；本项不提交） |
| worktree | `git status --porcelain` 含他线 M/??；本项落点 = `tools/ci/*`、`tools/tests/*`、`app/src/androidTest/.../visualjs/*`、本文件；另按主会话裁定 A 修 `.../pdf/PdfAigcDeviceRoundTripInstrumentedTest.kt` 第 40 行注释（§4） |
| device-serial | emulator-5584（AVD `kb_test_device`，API 34，x86_64，4 vCPU，page-size 4096） |
| apk-sha256 | app `665cd5e0f91b149c499d187d65b3181cdbaf2d7c0e2b2dfa46e1fcbcb16d1e17`；androidTest `3d62128346b0829d6bf9b9f8d5a8928fa3030c1d41e42a97ea8f78143f53e4e0`（宿主=设备逐字节一致，§4） |
| exit-code | 构建 0；门 PASS（exit 0）；配对报告 `REPORT_EXIT=1`（UNCONVERGED，§5.3） |
| raw-log | `build/ws4-isolation/`（gitignored）：`floor*.json`、`pair3-final/`（6 run + 12 gate + pair.json/md）、`build4.log`、`src-sha-before3/after3.txt`、`python-tests.txt` |
| record-ref | `docs/research/2026-11-02-batch-40-record.md` §8 #7/#19、§9.1-3；本批计划 §2 WS4、R5 `docs/research/2026-11-03-low-load-gating.md` |
| sanitized | 是（无密钥/凭据；仅模拟器 serial） |

## 1. 目的与判据

批次 40 的 isolation 只能写“单臂 15/15 绿”（`evidence/2026-11-02-batch-40/step04-isolation.md` §4–§5），
低负载门、OFF/ON 配对、T4 控制轮全部缺失。本步把这三件事落成**可复跑的门与报告**，
并如实记录本轮设备结果。

| 计划项 | 判据/落点 | 本轮状态 |
|---|---|---|
| 25 低负载前门 | `tools/ci/lowload_gate.py`（snapshot/calibrate/gate/evaluate）+ 套件前置门 `IsolationGate` | ✅ 实跑（§3） |
| 26 OFF/ON 配对 | `tools/ci/run_isolation_pair.sh` + `tools/ci/isolation_pair_report.py` | ✅ 实跑 k=3；判词 **UNCONVERGED**（§5） |
| 27 T4 可核标记 | `tools/ci/t4_control_marker.py`（构建日志 `T4_REVERT_APPLIED` + 引擎 sha + 6 处行级 sha） | ✅ 工具落码/单测；控制轮 **未做**（§6） |
| 28 红集判定 | 只在低负载窗内比较；`-4/UNKNOWN` 不算通过；概率门槛写死（R5 §5.3） | ✅ 单测 + 设备报告（§5） |
| 29 设备实跑 | 15 例单臂 + 配对（k=3，前后门） | ✅ 6 run × 15 例；窗口 5/6 INVALID（§5） |
| 30 证据落盘 | 本文件 | ✅ |
| 31 结论口径 | 配对成立才可谈 T4 因果；否则“未收敛”；禁止“T4 排除” | ✅（§7） |

## 2. 新增件与它们消灭的具体失败

| 新增 | 消灭的具体失败 | 单测 |
|---|---|---|
| `tools/ci/lowload_gate.py` | 批次 40 “无 5 min 干净窗”：门停留在人手判断，`load<1.0` 未归一化且无采样口径 | `tools/tests/test_lowload_gate.py` 13 例 |
| `tools/ci/isolation_pair_report.py` | 批次 39 三窗红集 6/7/5 无法比较；A3 “总数必须 = 5 且集合严格相等”把噪声写成红 | `test_isolation_pair_report.py` 15 例 |
| `tools/ci/t4_control_marker.py` | 批次 40 I50-6/7 无机器可核产物：没人能从证据判断构建含不含 T4 打点 | `test_t4_control_marker.py` 4 例 |
| `tools/ci/run_isolation_pair.sh` | 单臂 + 无前后门：一轮快照不能证明“运行期没被污染” | `bash -n` 通过（§8） |
| `app/src/androidTest/.../LowLoadProbe.kt` | instrumentation 读不到系统负载却可能静默放行：`/proc/stat` 等 app 侧 EACCES（R5 §2.2），套件无前置门 | 设备实测（§3.4/§5） |
| `app/src/androidTest/.../IsolationArms.kt` | 同一构建无法切换 OFF/ON 臂；无 arm 声明时也会把运行当“控制臂” | 设备实测（§5） |

合计新增 Python 单测 **32 例**（13+15+4），本轮实跑全绿（`build/ws4-isolation/python-tests.txt`）。

## 3. 低负载门实跑

### 3.1 进程快照（L-4）

```text
$ python -I tools/ci/lowload_gate.py snapshot --serial emulator-5584
{"test_processes": 0, "runner_processes": 0, "orphan_workers": 0,
 "window_animation_scale": "1.0", "transition_animation_scale": "1.0",
 "animator_duration_scale": "null", "ok": true}   # exit=0
```

- 口径修正（本轮实测）：runner 计数**不能**用 `grep AndroidJUnitRunner`——grep 自身 argv
  就是该 pattern，恒得 2 个假并发。已改为 `awk` 并按 NAME 排除 `awk`/`sh`（单测
  `test_snapshot_script_does_not_self_match_the_runner_pattern` 钉住）。
- 能力探测：`emulator-5596`（API 23）`nproc` 不存在、`date +%s%N` 不支持 → snapshot 报
  `runner_processes: null`、gate 会因能力缺失 INVALID（exit 1），**不冒充 0**。
- 动画：三设备 `window/transition_animation_scale=1.0`（共享设备禁改设置）；runner 统一
  `--no-window-animation` 中和窗口动画，尺度值只入证据、不当门（R5 L-6 的偏差，见 §9）。

### 3.2 地板标定（L-2，装包前 / 装包后 / 最终 APK 各一次）

`python -I tools/ci/lowload_gate.py calibrate --serial emulator-5584 --samples 30 --interval 10 --settle 120`

| 地板 | 时间 | n | busy%（p95/max） | la1/nproc（p95/max） | ctxt/s p95 | 唤醒 p95/max |
|---|---|---|---|---|---|---|
| 装包前 `floor.json` | 19:04–19:12 | 30 | 1.52 / 1.87 | 0.045 / 0.0575 | 440 | 15.01 / 15.26 ms |
| 装包后 `floor2.json` | 19:39–19:47 | 30 | 1.59 / 1.62 | 0.0475 / 0.0525 | 401 | 13.88 / 14.63 ms |
| 最终 APK 后 `floor3.json` | 19:53–20:00 | 30 | 1.72 / 1.74 | 0.040 / 0.0425 | 422 | 11.39 / 13.05 ms |

三次地板一致（R5 静机实测 1.4% / 0.005 / 390 / 14 ms 同量级）。按 L-3 门值：
`busy p95 ≤ floor+3.0pp`、`la p95 ≤ floor+0.05`、`la max ≤ 0.30`、`ctxt p95 ≤ 1.5×floor`、
`wakeup p95 ≤ max(3×floor,30ms)`、`wakeup max ≤ 5×floor`。本轮采用**装包后地板**
（R5 L-2：换 APK 重标定），装包前地板只作一致性对照。

### 3.3 门 PASS（窗口 = 31 点 / 30 差分 / 301 s）

```text
$ python -I tools/ci/lowload_gate.py gate --serial emulator-5584 \
    --floor build/ws4-isolation/floor3.json --samples 30 --interval 10 \
    --out build/ws4-isolation/pair3-final/gate-pre-r1-OFF.json
{"verdict":"PASS","reasons":[],
 "summary":{"n":30,"busy_p95":1.6385,"busy_max":1.7193,"la_ratio_p95":0.0225,
            "la_ratio_max":0.0225,"ctxt_p95":436.2,"procs_running_max":2,
            "uptime_span_s":301.1,
            "wakeup":{"p50_ms":10.72,"p95_ms":12.54,"max_ms":13.36,"n":40}},
 "checks":{"busy_p95":{"limit":4.72},"busy_max":{"limit":6.74},
           "la_ratio_p95":{"limit":0.09},"la_ratio_max":{"limit":0.30},
           "ctxt_p95":{"limit":632.5},"wakeup_p95":{"limit":34.17},
           "wakeup_max":{"limit":65.26}}}
# exit=0
```

- 采样在**设备内**循环（`/proc/uptime` 计时、10 s 间隔），不逐样本 adb 往返；
  任一 `Δt` 落在 [8,15] s 之外即 INVALID。
- 缺 1 个样本、缺唤醒探针、缺地板、`nproc` 不符、任一点越界 → INVALID（exit 1）；
  不剔最大值、不重跑到过（R5 L-7）。

### 3.4 instrumentation 侧自证门（fail-closed）

```text
$ adb -s emulator-5584 shell am instrument -r -w --no-window-animation \
    -e class ...VisualJsIsolationInstrumentedTest \
    -e smb.lowload.gate INVALID -e smb.lowload.reason test_skip -e smb.t4.arm OFF \
    com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# 15× INSTRUMENTATION_STATUS_CODE: -4；汇总仍打印 “OK (15 tests)”；INSTRUMENTATION_CODE: -1
# logcat: SMB_LOWLOAD_GATE: verdict=UNGATED reason=shell_side_INVALID:test_skip （×15）
```

- 这条设备实测暴露了报告口径的关键陷阱：**全 skip 也会打印 `OK (15 tests)`**。
  `isolation_pair_report.py` 因此把 `-4` 单列为 `SKIP`，且把该 run 判 `INVALID`（→
  `UNCONVERGED`），不把汇总行当通过。
- 套件前置门读 `-e smb.lowload.gate PASS`（两个 token；实测 `-e key=value` 会让 am 报
  `Invalid userId -2`，本轮已修脚本与文档）；随后跑 `/proc/self/stat` + 200×10 ms 睡眠
  抖动探针；本进程 CPU > 80% 窗口、`lowMemory`、抖动 p95 > 30 ms（或无地板时
  p95 > 5 ms 且 p95/p50 > 2）、或读不到自证信号都判 `UNGATED`。
- **本轮实测到的设计缺陷与修正（如实）**：首版自证门只按 R5 §4.8 的自比值
  `p95/p50 > 2` 判读；同进程 `Thread.sleep(10 ms)` 在静机上实测 `p50=1.4 ms / p95=2.9 ms`
  （R5 的 11 ms/14 ms 来自 `date +%s%N` 子进程探针，含两次进程派发），比值 2.07 把
  **完全空闲**的设备误判为 `UNGATED`，导致首轮配对 15×`-4`。修正：绝对量 `p95 > 30 ms`
  独立成门；比值只在 `p95 > 5 ms`（可观测）时判读；日志判词改为按 `reading.gated`
  渲染（首版把 `GATED` 写死）。修正后 smoke：15/15 PASS、`Time: 63.682`、0 个 `-4`
  （`build/ws4-isolation/smoke.log`）。这是对 R5 判据形状的**偏差**，理由与实测值在案。

## 4. 源快照 / 构建 / 安装绑定

```text
$ find app/src core/model/src core/data/src core/database/src core/domain/src \
    -type f -print0 | sort -z | xargs -0 sha256sum > src-sha-before3.txt   # 1278 files
$ ./gradlew --console=plain :app:assembleStrictOfflineDebug :app:assembleStrictOfflineDebugAndroidTest
BUILD SUCCESSFUL in 10s   # exit=0
$ diff src-sha-before3.txt src-sha-after3.txt → 0 行（SOURCE STABLE）
```

- 首次 androidTest 编译红（**他线新增文件**）：`PdfAigcDeviceRoundTripInstrumentedTest.kt:40`
  的 KDoc `pdf-aigc-device/*.pdf` 触发 Kotlin 嵌套块注释 → `Unclosed comment`，整个
  `:app:compileStrictOfflineDebugAndroidTestKotlin` 失败。经主会话裁定 A，做**字符级注释修复**
  （`*.pdf` → “目录下的 `.pdf` 文件”），未动断言/用例；修复后同任务 exit=0（§8）。
- 最终 APK：app `665cd5e0f91b149c499d187d65b3181cdbaf2d7c0e2b2dfa46e1fcbcb16d1e17`（未变）、
  androidTest `3d62128346b0829d6bf9b9f8d5a8928fa3030c1d41e42a97ea8f78143f53e4e0`
  （probe 修正后重建）；设备侧 sha 逐字节一致（`device-apk-sha256.txt` / `pm path` 实测）。
- APK dex 含新符号：`SMB_LOWLOAD_GATE`×4、`SMB_T4_HOOK`×3、`SMB_ISOLATION_ARM`×1、
  `LowLoadProbe`×16、`IsolationGate`×7、`T4SeamProbeRule`×15（Python 扫 `classes*.dex`）。

## 5. OFF/ON 配对结果（k=3，前后门）

编排：`tools/ci/run_isolation_pair.sh --serial emulator-5584 --floor build/ws4-isolation/floor3.json
--rounds 3 --out-dir build/ws4-isolation/pair3-final --off-arm-production-t4 test-side-only`。
臂顺序交替 OFF ON / ON OFF / OFF ON；每个 run 前/后各一次 5 min 门（10 s × 30 差分）；
两次都 PASS 才把 window 记 PASS。同一 APK（app `665cd5e0…`、test `3d621283…`）、同一
serial、同一宿主时间带（11:53–13:04Z）。原始日志 `build/ws4-isolation/pair3-final/`。

### 5.1 窗口（门）与 run

| round | arm | pre-gate | 15 例结果 | post-gate | window | post-gate 越界值 |
|---|---|---|---|---|---|---|
| 1 | OFF | PASS | 15 PASS / 0 skip | **PASS** | **PASS** | —（busy_max 6.69 < 6.74） |
| 1 | ON | PASS | 15 PASS / 0 skip | INVALID | INVALID | busy_max 8.11；ctxt p95 918（限 632）；la p95 0.1125（限 0.09） |
| 2 | ON | PASS | 15 PASS / 0 skip | INVALID | INVALID | busy_max 10.37 |
| 2 | OFF | PASS | 15 PASS / 0 skip | INVALID | INVALID | busy_max 7.19；ctxt p95 791 |
| 3 | OFF | PASS | 15 PASS / 0 skip | INVALID | INVALID | busy_p95；busy_max 6.91；ctxt p95 742 |
| 3 | ON | PASS | 15 PASS / 0 skip | INVALID | INVALID | busy_max 9.54 |

- 6 个 run 全部 15/15 PASS、0 skip、0 FAIL/ERROR（`grep STATUS_CODE` 逐文件计数）。
  `OK (15 tests)` 不是判据本身——判据是 `-4`/`-2`/`-1` 的逐方法向量。
- OFF 臂 logcat 无 `SMB_T4_HOOK`（`test_side_hooks=false`）；ON 臂 15 条 begin + 15 条 end
  （`test_side_hooks=true`），end 均记 `no_timestamp_source`——规则包住 `@After`，引擎在
  `closeEverything()` 里已释放 link，钩子如实记“无源”，不伪造时间戳。

### 5.2 kill 归因（结构性不变量，两臂各 3 轮合计）

| arm | watchdog 总数 | 必须杀 3 方法 | terminate 总数 | 必须不杀命中 | 集合外 |
|---|---|---|---|---|---|
| OFF | 15（5/run） | 3/3 各 ≥1 | 15（5/run，`theClientRebuildsAtMostOnce` 2/run） | 0 | `theClientRebuildsOnceAfterADeath…` 2/run |
| ON | 15（5/run） | 3/3 各 ≥1 | 15（5/run，同上） | 0 | 同上 |

- 与批次 40 `step04` §2 的逐方法分布**逐项一致**（含 2 次 `theClientRebuildsOnce…`
  的集合外 kill）；R5 §5.4 的“总数偏离只记 WARN”落实后 `structurally_ok=true`。
- 这只能证明“kill 结构可复现”，**不**证明任何单例的 T4 因果。

### 5.3 机器判词（`pair.json` / `pair.md`）

```text
verdict = UNCONVERGED
paired  = false
reasons = ["window_not_pass", "window_invalid"]
rounds_per_arm = 3
OFF windows = {1: PASS, 2: INVALID, 3: INVALID}
ON  windows = {1: INVALID, 2: INVALID, 3: INVALID}
mcnemar_p = null（未进入配对比较）
```

**未收敛的原因不是测试红**（两臂 6 轮全绿且红集（空集）一致），而是 post-gate 窗口
不满足：6 个 post-gate 中 5 个 INVALID，越界集中在 `busy_max`（6.9–10.4% vs 上限 6.74%）
与 `ctxt p95`（742–918/s vs 上限 632），即 1–2 个 10 s 样本越界。时序上 post-gate 在
suite 进程退出后约 1 s 开始，第一个样本包含套件自身收尾（进程死亡/GC/dex）；而
r1-OFF 的 post-gate（唯一 PASS）busy_max=6.69，紧贴上限。**归因假设**：post 窗首样本被
被测套件的收尾污染，不是外部闯入者；本轮**没有**做 `--post-settle` 对照，故只记为假设。
工具已加 `--post-settle SECONDS`（默认 0，保持原协议；本轮未使用），用于让收尾先衰减。

**报告生成的两次修正（如实）**：
1. `run_isolation_pair.sh` 收尾调用报告时，把 Git Bash 的 `/d/...` 绝对路径直接交给
   native Python，Windows 解析成 `\d\...` → `No such file`（脚本 `pair verdict=UNREADABLE`）。
   已把交给 Python 的路径统一 `cygpath -w`（本轮实测转换 `D:\smart mistake book\...` 可读）。
2. kill 归因首版按 `TestRunner: started:` 匹配，而真实 logcat 是
   `I/TestRunner(19648): started:`（PID 在括号里）→ 15 次 kill 全落 `_unattributed`。
   已改为 `TestRunner` + `: started: ` 双条件，并补真实形状单测。
   §5.3 的 `pair.json` 是在修正后用**同一批 6 个 run 输入**重新生成的（`REPORT_EXIT=1`，
   即 UNCONVERGED），不是重跑测试。

按 R5 L-7 与计划项 4：窗口 INVALID 的 run 不得进入红集比较，也不得“重跑到过”。
因此本步结论是 **未收敛**，不是“isolation 通过”，更不是“T4 排除”。

## 6. T4 控制轮可核标记（未做）

```text
$ python -I tools/ci/t4_control_marker.py emit --build-log build/ws4-isolation/build4.log \
    --out build/ws4-isolation/t4.marker.json          # EMIT_EXIT=0
$ python -I tools/ci/t4_control_marker.py verify --marker build/ws4-isolation/t4.marker.json
                                                      # VERIFY_EXIT=0, ok=true
arm=ON_PRODUCTION_T4_PRESENT
engine_sha256=f0786242633f72a3f912fcd0038161949ae39b94bcdca52d38320839bf6f9209
anchor_count=6
anchors: L401 executeAcceptedAt / L570 firstByteAt / L670 bindRequestedAt /
         L821 readyAt / L840 readyAt / L1161 bindConnectedAt
```

- 6 处写点与批次 39 记录的锚点逐一对上（`401/570/670/821/840/1161`），引擎 sha 绑定
  当前树；这就是“这一份 APK 含 T4 打点”的机器可核证据。
- **未做**生产侧回退控制轮：回退需要改 `app/src/main/.../VisualJsClientEngine.kt`（本轨
  落点之外），且必须“临时回退 → 构建 → 跑臂 → 还原 → sha 校验”。因此
  `--off-arm-production-t4` 只能记 `test-side-only`；OFF 臂只是**测试侧钩子未注册**，
  生产 6 处打点仍在。配对报告据此**拒绝**输出 T4 因果（§5 判词）。
- `verify` 的 fail-closed 已单测：日志声明 `T4_REVERT_APPLIED` 但源码仍有打点 → 标记无效；
  OFF 标记在引擎 sha 或行级 sha 漂移时 verify 失败。

## 7. 判定与不得写的结论

- 低负载门：**PASS**（装包前/后/最终 APK 三次地板；6 个 pre-gate、1 个 post-gate PASS）。
- OFF/ON：`k=3`、6 个 run 全部 15/15 PASS、两臂红集（空）一致；但 **5/6 个 post-gate
  INVALID** → 机器判词 **UNCONVERGED**（§5.3）。配对**不成立**，不得进入红集差异讨论。
- 即使 pair 判词为 `PAIRED_RED_SET_IDENTICAL`，也只意味着“红集在低负载窗内可复现”，
  **不**等于“某用例因 T4 而红/绿”（R5 §5.3：n=15、k=3 时不存在 p<0.05 的因果证据）。
- T4 控制轮：**未做** → 不得写“T4 排除”、不得写“isolation 通过”、不得把 `-4`/skip
  或 `OK (15 tests)` 汇总行当通过。
- 本步结论：**未收敛**。原因已定位到 post-gate 窗的 1–2 个样本越界（§5.3），不是测试红；
  修复方向是 `--post-settle`（工具已提供，本轮未用），需下一轮以预注册方式验证。

## 8. 复跑命令与单测

```bash
export PATH="$PATH:/c/Android/Sdk/platform-tools"
python -I tools/ci/lowload_gate.py snapshot   --serial emulator-5584
python -I tools/ci/lowload_gate.py calibrate  --serial emulator-5584 --samples 30 --interval 10 --settle 120 --out build/ws4-isolation/floor.json
python -I tools/ci/lowload_gate.py gate       --serial emulator-5584 --floor build/ws4-isolation/floor.json --samples 30 --interval 10 --out build/ws4-isolation/gate.json
bash tools/ci/run_isolation_pair.sh --serial emulator-5584 --floor build/ws4-isolation/floor.json --rounds 3 --off-arm-production-t4 test-side-only
# 下一轮（post 窗收尾污染，E-41-WS4 §5.3）建议预注册：
bash tools/ci/run_isolation_pair.sh --serial emulator-5584 --floor build/ws4-isolation/floor.json --rounds 3 --post-settle 60 --off-arm-production-t4 test-side-only
python -m unittest discover -s tools/tests -t tools -k lowload_gate
python -m unittest discover -s tools/tests -t tools -k isolation_pair_report
python -m unittest discover -s tools/tests -t tools -k t4_control_marker
bash -n tools/ci/run_isolation_pair.sh
```

本轮单测实跑：`Ran 13 / OK`、`Ran 15 / OK`、`Ran 4 / OK`，共 32 例
（`build/ws4-isolation/python-tests.txt`）。

## 9. 偏差、未验证与风险

1. **10 s vs R5 的 5 s**：任务书要求逐 10 s 采样；窗口仍取 5 min（31 点 = 30 差分），
   与 R5 的 5 s×60 样本等价在窗口长度、不同在粒度。门值与判读方式未放宽。
2. **post-gate 的首样本污染**：6 个 post-gate 中 5 个 INVALID，越界集中在 1–2 个样本
   （`busy_max` 6.9–10.4% vs 6.74%），而唯一 PASS 的 r1-OFF 是 6.69%。时序上 post-gate
   在 suite 退出后约 1 s 开始 → 归因假设为“被测套件自身收尾”。未做 settle 对照，
   故只登记为假设；`run_isolation_pair.sh --post-settle N`（默认 0）是下一轮的预注册修复。
3. **自证探针的自比值形状**：R5 的 1.27/2.2 来自 `date` 子进程探针；同进程
   `Thread.sleep(10 ms)` 静机 p50≈1.4 ms、p95≈2.9 ms，纯比值会把空闲误判 UNGATED。
   已改为“绝对 p95>30 ms 独立成门 + 比值仅在 p95>5 ms 时判读”，并在 §3.4 记录实测值。
4. **动画设置未置 0**：共享设备禁改设置（批次 41 纪律），改由 `--no-window-animation`
   中和；尺度值入证据但**不作门**。R5 L-6 的“缺一即门无效”在此记为偏差，不冒充满足。
5. **宿主争用不进 guest**（R5 §2.6：`steal` 恒 0）：门只能证明 guest 侧低负载；宿主上
   他线构建会由 `busy_max`/`wakeup` 与进程快照部分捕获，但不完备。
6. **T4 生产侧控制轮未做**：见 §6；这是“T4 排除”的唯一充分证据，当前缺失。
7. **配对统计上限**：k=3 只支持“可复现”，不支持单例因果；k=4 完美分离 p=0.0286。
8. 未验证：API 23（emulator-5596）无 `nproc`/`%N`，门在该镜像上只能 UNKNOWN；
   16KB 页 / arm64 / 真机不在本步范围。
