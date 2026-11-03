<!-- 体积自查：约 150 行；R4 上限 400 行 / 65536 B -->
# E-42-WS4-01 isolation 因果定案：OFF/ON 配对未收敛、T4 控制轮缺失 → 不写因果

| 字段 | 值 |
|---|---|
| evidence-id | E-42-WS4-01 |
| convention-version | 1 |
| batch | 2026-11-04-batch-42 |
| step | WS4 / 计划项 34–41（`.zcode/plans/plan-2026-11-04-batch-42.md:64-72`） |
| gate-or-kd | isolation 配对（R2 §2–§5）；T4 控制轮（I50-6/7）；KD-76 关闭前置 |
| verdict | **UNCONVERGED / 不写因果**：低负载门 1/3 轮 PASS，但该窗内 instrumentation 自证 `own_cpu_busy`，6 个 run 的 15 例全部 `-4`；T4 控制轮**无 OFF 产物**（BLOCKED）。配对与控制轮**未同时成立** → 按计划项 38 禁止任何因果结论 |
| executed-at | 2026-11-03T07:42–09:10+08:00（UTC 2026-11-02T23:42–2026-11-03T01:10Z） |
| operator | WS4 设备稳定性轨 |
| head | `faab17f5`（共享工作树，`git status` 含他线 M/??；本项零 git 写） |
| device-serial | emulator-5562（AVD `kb_test_device`，API 34，x86_64，4 vCPU，PAGE_SIZE 4096） |
| device-fingerprint | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| apk-sha256 | app `fe9452aab64d6fcf9d840a0e5e5c542d43daed176da2bf5aa71370d766d69e3e`（设备 base.apk 与只读拉取逐字节一致）；androidTest `d300d8855c05a0ad8bdb8a372eebca3538bbebd0a960f36eeb4cbed7a7c9a721`（两臂同一份，安装后未再变） |
| exit-code | 修复后工具自测 `41 passed`；`run_isolation_pair.sh` 同 COMPONENT → **exit 2**；T4 marker emit/verify **exit 0**；配对报告 `REPORT_EXIT=1`（UNCONVERGED） |
| raw-log | `build/ws4-isolation-b42/`（gitignored）：`floor-off.json`、`floor-post.json`、`gate-r{1,2,3}.json`、`r{1,2,3}-{OFF,ON}.{instrument.log,logcat}`、`pair.json`、`pair.md`、`t4-on.marker.json`、`t4-verify.out`、`attempt1-contended/`、`installed-app.apk`、`installed-test.apk` |
| record-ref | R2 `docs/research/2026-11-04-isolation-causality.md`（§0/§3.3/§4.4/§8.3）；批次 41 记录 §9.3 F7/F8；`docs/research/evidence/2026-11-03-batch-41/step05-isolation-lowload.md`（D05） |
| sanitized | 是（无密钥/账号/Cookie；仅模拟器 serial） |

## 1. 目的与逐项状态

| 计划项 | 判据 | 本轮状态 |
|---|---|---|
| 34 用修复后的工具跑 OFF/ON 配对（同 APK sha） | `run_isolation_pair.sh` + `isolation_pair_report.py` | **部分**：报告工具判定 UNCONVERGED；runner 被 F8 物理门拒绝（§2.2），设备跑由等价的手工驱动完成（§5） |
| 35 T4 控制轮（可核构建标记 + 已安装 APK 比对） | `t4_control_marker.py emit/verify` + 全 dex 写点数 | **控制轮 BLOCKED**；标记/比对半边**实跑通过**（§4） |
| 36 低负载窗程序化前门（loadavg + 进程快照） | `lowload_gate.py snapshot/calibrate/gate` | **实跑**：快照 3/3 条为 0；地板两版；门 1 PASS / 2 INVALID（§3） |
| 37 红集逐方法比较（集合相等/差异清单） | 报告 `arms` 向量 + `missing_methods` | **实跑**：15 方法 × 2 臂 × 3 轮全 `SKIP`；`pair.md` 有 15 行逐方法表；无红集可比较（§6） |
| 38 仅当配对与控制轮同时成立才写因果 | R2 §4.4 | **执行**：两者均不成立 → 不写因果（§7） |
| 39 设备实跑（15 例 × 两臂） | `am instrument -e smb.t4.arm OFF/ON` | **执行**：6 run，每 run `numtests=15`；**15 例全部 `-4` 跳过**（§5） |
| 40 证据落盘 | 本文件 | 完成 |
| 41 台账回写 | `docs/known-defects.md` KD-76 进展块 | **UNCONVERGED** |

## 2. 工具前置（修复后）

### 2.1 单测

```bash
$ python -m pytest tools/tests/test_isolation_pair_report.py \
    tools/tests/test_t4_control_marker.py tools/tests/test_summarize_kd76.py -q
41 passed in 0.65s
```

覆盖 F7（缺失方法物化 UNKNOWN）、F8（APK 身份/类清单漂移）、F10（空 per-round sha）。本轮未重跑全仓门（属门禁阶段）。

### 2.2 F8 物理门：同 COMPONENT 被拒绝（实跑）

```bash
$ bash tools/ci/run_isolation_pair.sh --serial emulator-5562
run_isolation_pair: OFF and ON arms must use distinguishable build identities; both resolve to
com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner. Pass
--off-component/--on-component built from different snapshots (a shared COMPONENT cannot be a
control arm).
EXIT=2
```

**与计划项 34 的冲突（必须显式登记）**：计划要求“OFF/ON 配对（同 APK sha）”，而修复后的 runner
按 F8 **拒绝**同 COMPONENT；R2 §3.3 注已把该冲突提交主会话裁定（R2 建议“保留同 COMPONENT、
把身份绑到已安装 APK”，runner 实现选了“拒绝”分支）。本轮因此**不修改 runner**，改为：设备跑用
与 runner 相同的 `am instrument` 参数手工驱动（§5），判定仍由修复后的
`isolation_pair_report.py` 生成。这不是 runner 的通过记录。

## 3. 低负载前门（快照 + 地板 + 三轮门）

### 3.1 进程快照

```text
$ python -I tools/ci/lowload_gate.py snapshot --adb <adb> --serial emulator-5562
{"test_processes": 0, "runner_processes": 0, "orphan_workers": 0, ..., "ok": true}   # exit 0
```

### 3.2 地板（两版，R5 L-2）

| 地板 | 时间（UTC） | n | busy% p95/max | la1/nproc p95/max | ctxt/s p95 | 唤醒 p95/max |
|---|---|---|---|---|---|---|
| `floor-off.json`（换 test APK **前**） | 23:44–23:49 | 30 | 9.68 / 9.76 | 0.1525 / 0.165 | 400 | 85.5 / 214.0 ms |
| `floor-post.json`（换 test APK **后**，本轮采用） | 00:34–00:41 | 30 | 8.97 / 9.36 | 0.2025 / 0.21 | 410 | 78.3 / 102.9 ms |

先测的 pre-install 地板在装后门里因 `la_ratio_p95` 越界（0.2475 > 0.2025）被拒，符合 R5 L-2
“换 APK 重标定”；`floor-post` 的绝对上界仍受 `la_ratio_max ≤ 0.30` 硬约束，不是放宽门值。

### 3.3 门结果

| 轮 | verdict | busy p95（限） | la p95（限） | la max（限） | ctxt p95（限） | 唤醒 p95（限） |
|---|---|---|---|---|---|---|
| r1 | **PASS** | 9.03%（11.96） | 0.1625（0.2525） | 0.165（0.30） | 407（614） | 96.1（235.0） |
| r2 | INVALID `la_ratio_p95,la_ratio_max` | 8.01%（11.96） | 0.4100（0.2525） | 0.4425（0.30） | 452（614） | 60.4（235.0） |
| r3 | INVALID `la_ratio_p95,la_ratio_max` | 7.00%（11.96） | 0.3575（0.2525） | 0.4025（0.30） | 428（614） | 73.0（235.0） |

r2/r3 的越界量与“宿主上有他线 Gradle/Java 构建（`java.exe` 4.4 GB 峰值）+ 3 台模拟器同宿主”
同时出现；CPU busy% 反而下降，形状是**等待而非计算**（D 类争用）。首轮 `attempt1-contended/`
（pre-install 地板，r1/r2 各 2 run）同为 la 越界，已单独归档。

## 4. T4 控制轮：标记与已安装 APK 比对成立，控制臂不存在

### 4.1 标记 emit / verify（实跑，exit 0）

```text
$ python -I tools/ci/t4_control_marker.py emit --build-log build/ws4-isolation-b42/build-log-note.txt \
    --apk build/ws4-isolation-b42/installed-app.apk --serial emulator-5562 \
    --package com.tingyun.smartmistakebook.offline --out build/ws4-isolation-b42/t4-on.marker.json
EMIT_EXIT=0  ok=True reasons=[] arm=ON_PRODUCTION_T4_PRESENT anchor_count=6 identity_source=installed_apk
$ python -I tools/ci/t4_control_marker.py verify --marker build/ws4-isolation-b42/t4-on.marker.json
VERIFY_EXIT=0 {"ok": true, "reasons": [], "arm": "ON_PRODUCTION_T4_PRESENT", "anchor_count": 6,
               "identity_source": "installed_apk", "apk_sha256": "fe9452aa…"}
```

- 标记里的 `installed_apk_identity` 与 `apk_identity` 同为 `fe9452aa…`，类清单 50 979 个（`installed_apk_differs_from_artifact` 未触发）。
- 失败模式也已实测：首版 `build-log-note.txt` 的说明文字里出现了回退标记字面量 → `reasons=["marker_claims_removed_but_anchors_present"]`、`arm=INVALID`、`EMIT_EXIT=1`（fail-closed 生效，随后改写说明文字）。

### 4.2 已安装 APK 全 dex 写点数（R2 §3.5 #4）

```text
classes4.dex invoke_sites=6
      1 .setBindConnectedAtMillis
      1 .setBindRequestedAtMillis
      1 .setExecuteAcceptedAtMillis
      1 .setFirstByteAtMillis
      2 .setReadyAtMillis
```

与源码 6 个锚点逐项对应（`VisualJsClientEngine.kt:401/570/670/821/840/1161`）。其它 9 个
`classes*.dex` 写点数 0（**只扫 `classes.dex` 会得到 0**——多 dex 陷阱复现）。

### 4.3 控制臂不存在（BLOCKED）

- `build/` 下无 `build/off`/`build/control` 目录，无任何声明回退的构建日志（`grep -rn T4_REVERT_APPLIED` 仅命中工具/测试/记录）；
- 本机两份 debug app 产物（`strictOffline`、`localFirst`，2026-11-03 08:24 重建）`classes4.dex` 写点数均 **6** → 全部是 ON 形态；
- 去 T4 控制臂需要回退 `VisualJsClientEngine.kt` 6 处写点后重建/重装（生产源码改动，属主会话/落码轨授权）。

**runbook（BLOCKED 解除用）**：按 `docs/research/2026-11-01-isolation-attribution.md:288-302` 的 M2
逐条去掉 6 处写点（保留 `ConnectStageTimestamps` 类与访问器）→ `assembleStrictOfflineDebug` +
`assembleStrictOfflineDebugAndroidTest` → 构建日志写入回退声明 → `t4_control_marker.py emit --apk
<off.apk> --serial <S>` 得 `arm=OFF_PRODUCTION_T4_REMOVED` → 两臂各装一次并 `sha256sum base.apk`
绑定 → 重跑 §5。**在此之前禁止任何“T4 排除/因果”表述。**

## 5. 设备实跑（15 例 × 两臂 × k=3）

命令形状（每轮每臂，臂序 AB/BA 交替；两臂同一 app sha、同一 test APK）：

```bash
adb -s emulator-5562 logcat -c
adb -s emulator-5562 shell am instrument -r -w --no-window-animation \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  -e smb.lowload.gate <gate 判词> -e smb.lowload.reason <reasons> -e smb.t4.arm OFF|ON \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
```

| run | 轮门 | numtests | PASS(0) | FAIL(-2) | ERROR(-1) | SKIP(-4) | 自证探针 |
|---|---|---|---|---|---|---|---|
| r1-OFF | PASS | 15 | 0 | 0 | 0 | **15** | UNGATED `own_cpu_busy`（own 5650 ms / 窗 5046 ms） |
| r1-ON | PASS | 15 | 0 | 0 | 0 | **15** | UNGATED `own_cpu_busy`（own 5090 ms / 窗 4211 ms） |
| r2-OFF | INVALID | 15 | 0 | 0 | 0 | **15** | `shell_side_INVALID:la_ratio_p95_la_ratio_max` |
| r2-ON | INVALID | 15 | 0 | 0 | 0 | **15** | 同上 |
| r3-OFF | INVALID | 15 | 0 | 0 | 0 | **15** | 同上 |
| r3-ON | INVALID | 15 | 0 | 0 | 0 | **15** | 同上 |

- 臂开关确实生效（非“无声同臂”）：`SMB_ISOLATION_ARM: arm=OFF … test_side_hooks=false` / `arm=ON … test_side_hooks=true`；ON 臂 logcat 有 **15 条** `SMB_T4_HOOK: begin`，OFF 臂 **0** 条。
- `OK (15 tests)` **不代表 15 例通过**：runner 把 assumption 失败记 `-4`，本轮 code0=0、skip=15。
- 装包前置：设备原有 test APK（`3c222fca…`，2026-11-01 19:04）的 dex 里**没有** `IsolationArms`/`IsolationGate`/`LowLoadProbe`（仅 22 个 `VisualJsIsolationInstrumentedTest*` 类），无法切臂也无法自证门；本轮改装批次 41 产物 `d300d885…`（2026-11-02 23:52 安装），臂开关与门随后可见。这是本轮唯一的设备状态变更（app 未动）；全部 run 结束后已把原 test APK `3c222fca…` 装回（2026-11-03 01:07 UTC，`sha256sum base.apk` 复核一致）。

## 6. 逐方法红集比较（修复后报告工具，实跑）

```bash
$ python -I tools/ci/isolation_pair_report.py --run arm=OFF,round=1,window=PASS,log=… \
    … --off-arm-production-t4 test-side-only --out build/ws4-isolation-b42/pair.json --md …/pair.md
REPORT_EXIT 1
verdict UNCONVERGED  paired False  rounds_per_arm 3
reasons ['window_not_pass', 'instrumentation_ungated', 'window_invalid', 'skipped_or_unknown']
missing_methods {'OFF': [], 'ON': []}
arms: OFF/ON × 3 轮 —— 15 个方法全部 SKIP
```

- **报告行为（如实区分新旧规则）**：每个 run 的 15 个方法都有**显式 `-4` 终态**，因此
  `skipped_or_unknown`（既有规则，非 F7 新增）与 `window_not_pass` 一起把判词压成
  `UNCONVERGED`，退出码 1。这是本轮唯一可写的“红集比较”结论：**没有红集，只有跳过向量**。
- **F7 的缺失物化路径本轮未被触发**：`missing_methods` 两臂均为空（日志完整、方法集合相同）。
  不能把本轮当作 F7 修复的运行时验证；F7 的先红后绿证据在
  `tools/tests/test_isolation_pair_report.py`（与 `pytest 41 passed` 同批执行）。

## 7. 结论判定（计划项 38）

| 条件（R2 §4.4） | 本轮 |
|---|---|
| 全部有效窗 PASS、15 方法全终态且无 SKIP/UNKNOWN、两臂向量相同、k ≥ 6 | ✗（1/3 窗 PASS、15/15 全 SKIP、k=3） |
| 控制轮：OFF 臂生产侧 T4 已移除 + 标记 verify | ✗（无 OFF 产物，标记只能得 ON_PRESENT） |
| 一致/分歧统计（McNemar/Fisher） | **不计算**——没有红集可配对 |

**裁定：UNCONVERGED；不写因果。** 具体是：
- 不能写“T4 无关/已排除”（控制轮缺失）；
- 不能写“两臂一致/等价”（全 SKIP，且 k=3 的零分歧上界也只有 63%，R2 §4.4）；
- 不能把 `OK (15 tests)` 读成 15 例通过（§5）。

## 8. 未验证 / 边界

- 15 例的真实行为（隔离 UID、watchdog、重建、连接预算）本轮**零执行** → 全部 UNVERIFIED。
- `own_cpu_busy` 根因仍未定案；本轮在 emulator-5562 上**独立复现** D05 在 5584 上的形状（own ≥ 窗长）。
- 配对未使用 runner 的“门-跑-门”串联与块随机化（runner 因 F8 拒绝驱动）；手工驱动只在每轮前跑一个 5 min 门，
  两臂共享该门判词，**post 窗未跑**（R2 §2.1 已裁定 post 仅作诊断）。
- 两臂差异本轮只含**测试侧钩子**（ON 多读时间戳/多打 2 行日志），不是生产侧 T4 差异；任何臂间差异都不能归给 T4。
- 设备状态变更：test APK 由 `3c222fca…` 换为 `d300d885…`（两臂内保持不变；`installed-test.apk` 留有旧包副本），跑完已装回 `3c222fca…`。

## 9. 复现命令

```bash
ADB=/c/Android/Sdk/platform-tools/adb.exe
python -I tools/ci/lowload_gate.py snapshot  --adb $ADB --serial emulator-5562 --out build/ws4-isolation-b42/snapshot-front.json
python -I tools/ci/lowload_gate.py calibrate --adb $ADB --serial emulator-5562 --samples 30 --interval 10 --settle 120 --out build/ws4-isolation-b42/floor-post.json
python -I tools/ci/lowload_gate.py gate      --adb $ADB --serial emulator-5562 --floor build/ws4-isolation-b42/floor-post.json --out build/ws4-isolation-b42/gate-r1.json
bash build/ws4-isolation-b42/run-pair.sh     # 3 轮 × 2 臂（脚本在 build/，不入库）
python -I tools/ci/t4_control_marker.py verify --marker build/ws4-isolation-b42/t4-on.marker.json
python -I tools/ci/isolation_pair_report.py --run … --off-arm-production-t4 test-side-only --out build/ws4-isolation-b42/pair.json
```

> 一句话：本轮把“报告工具在真实数据上判 UNCONVERGED（不假绿）”“T4 标记能绑到已安装 APK 并数出 6 处写点”
> 做成了**实跑事实**，但低负载窗与 self-probe 双重拦截、且无 OFF 控制臂，isolation 仍 **UNCONVERGED**，
> 按计划项 38 不写因果。
