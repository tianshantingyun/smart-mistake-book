<!-- 体积自查：约 110 行；R4 上限 400 行 / 65536 B -->
# E-41-D05 isolation：低负载门成立但 instrumentation 自证 UNGATED，套件未执行

| 字段 | 值 |
|---|---|
| evidence-id | E-41-D05 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 设备轨步骤五（计划 ask 5；计划 §2 WS4 25–31 的设备侧） |
| gate-or-kd | isolation 低负载门（R5 L-1…L-7）；批次 40 §9.1-3、§8 #7 |
| verdict | **未通过 / 未执行（NOT PASSED）**：shell 侧低负载门 `PASS`，但 instrumentation 自证探针 3/3 次 `UNGATED reason=own_cpu_busy`，15 例全部 `-4` 跳过（code0=0）。**不得记通过，不得写"T4 排除"** |
| executed-at | 2026-11-03T01:03–01:19+08:00（UTC 17:03–17:19Z） |
| operator | 设备轨 |
| head | `62522930`；源快照 `src-sha-after-fix.txt` |
| device-serial | emulator-5584；套件前系统负载 `0.08 0.13 0.24`（`/proc/loadavg`），`top` 392% idle |
| apk-sha256 | localFirst app `5793300c…` / test `0e906482…`；strictOffline app `9a5f6a6e…` / test `d300d885…` |
| exit-code | snapshot 0；calibrate 0；gate 0（PASS）；isolation 套件 adb exit=0 但 code0=0 / skip=15 |
| raw-log | `build/device-track-batch41/lowload/{snapshot.json,floor.json,gate-pre.json,gate-pre.out,instrument.args}`；`build/device-track-batch41/isolation/suite-isolation*.log` + `*-logcat.txt` |
| record-ref | 批次 40 记录 §8 #7/#19、§9.1-3；WS4 证据 `docs/research/evidence/2026-11-03-batch-41/ws4-isolation.md` |
| sanitized | 是 |

## 1. 低负载门（shell 侧，程序化）

| 步骤 | 命令 | 结果 |
|---|---|---|
| snapshot | `python -I tools/ci/lowload_gate.py snapshot --serial emulator-5584` | `test_processes=0 / runner_processes=0 / orphan_workers=0`，ok=true |
| calibrate | `… calibrate --serial emulator-5584 --out build/device-track-batch41/lowload/floor.json` | exit=0；30 样本/10 s；busy_p95 **1.515%**、la_ratio_p95 **0.085**、ctxt_p95 **720.2**、wakeup_p95 **11.86 ms** |
| gate（套件前） | `… gate --serial emulator-5584 --floor floor.json --out gate-pre.json --emit-args instrument.args` | **PASS**：wakeup_p95 24.10 ms（限 35.59）、busy_p95 0.98%（限 4.51）、la_ratio_p95 0.0175（限 0.135）、ctxt_p95 557.6（限 1080.4）；30 差分，间隔 10.03 s |

- `instrument.args` 实际内容：`-e smb.lowload.gate PASS`、`-e smb.lowload.reason`。
- 结论：**系统窗是干净的**（门脚本判定 PASS，且主机/设备负载低）。

## 2. 套件执行：全部跳过（关键反向结果）

执行命令（3 次，串行，`--no-window-animation`）：

```bash
adb -s emulator-5584 shell am instrument -r -w --no-window-animation \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  -e smb.lowload.gate PASS -e smb.lowload.reason <…> -e smb.t4.arm OFF \
  <pkg>.test/androidx.test.runner.AndroidJUnitRunner
```

| # | 包 | 结果 | code0 | skip(-4) | 自证探针 |
|---|---|---|---|---|---|
| 1 | localfirst | `OK (15 tests)`，Time 3.285 s | 0 | 15 | `verdict=UNGATED … own_cpu_ms=3190.0 probe_window_ms=3136 reason=own_cpu_busy` |
| 2 | localfirst（诊断重试） | `OK (15 tests)`，Time 3.305 s | 0 | 15 | `own_cpu_ms=3170.0 probe_window_ms=3116 reason=own_cpu_busy` |
| 3 | strictOffline（**诊断观察**；构建窗 01:15–01:17 与他线 KB 源漂移重叠，源码快照不能保证与 after-fix 完全一致） | `OK (15 tests)`，Time 3.256 s | 0 | 15 | `own_cpu_ms=3490.0 probe_window_ms=3100 reason=own_cpu_busy` |

- `SMB_ISOLATION_ARM: arm=OFF t4_production=compiled_in test_side_hooks=false`（第 1 次）。
- 每条用例前 `Assume` 跳过：`SMB_LOWLOAD_GATE UNGATED: instrumentation_self_probe:own_cpu_busy`。
- **`OK (15 tests)` 在这里不表示 15 例通过**：runner 把 assumption 失败计为 `-4`，全部未执行。

## 3. 归因与影响（不擅自改门）

- localFirst 两次**可复现**（同 APK/同快照）；strictOffline 单次同为 UNGATED，但其构建窗与他线源漂移重叠，只作现场观察，**不足以**证明或否定 flavor 归因。
- 同源比对：`app/src/main` 与 WS4 当时构建快照（`build/ws4-isolation/src-sha-after3.txt`）diff=0；因此不是本轮 app 源码漂移导致的固定占用。
- 未定案：约 1 核 × 3.1 s 的进程自耗（JIT/类校验/启动后台）在 WS4 的设备上未触发（其 6 run 均 gated 通过），本轮 reboot 后环境稳定复现。**归因标"未定案"**，不改 `LowLoadProbe` 阈值，也不把 UNGATED 当环境噪声。
- 后果：在当前 APK + 当前模拟器状态下，isolation 15 例**无法取得执行证据**；计划 ask 的"若低负载门成立则跑"条件在**系统门层成立、instrumentation 自证层不成立**，故按"不得记通过"处理。

## 4. 配对与控制轮

- 未运行 OFF/ON 配对：`tools/ci/run_isolation_pair.sh` 硬编码 `.offline` 包、要求 `--rounds >= 3`，且每一 run 同样受 `own_cpu_busy` 拦截（套件会全 `-4`，报告只会得到 UNCONVERGED/INVALID）。本轮不修改共享工具。
- T4 控制轮未做（需生产侧回退 + `t4_control_marker.py`），**禁止**写 "T4 排除"。
- WS4 在更早 APK 上的配对结果（E-41-WS4）为 **UNCONVERGED**，与本轮结论一致：isolation 仍未收敛。

## 5. 未验证 / 边界

- 15 例的实际行为（隔离 UID、watchdog、重建等）本轮**零执行** → 全部 UNVERIFIED。
- 未跑套件后门（套件未产生有效窗口，后门无判定意义）。
- `own_cpu_busy` 的根因未定位；若 WS4 调整探针窗口/口径，需以新证据重跑，不得沿用本文件当绿。
