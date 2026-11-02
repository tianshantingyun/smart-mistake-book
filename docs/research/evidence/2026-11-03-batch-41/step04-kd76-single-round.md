<!-- 体积自查：约 90 行；R4 上限 400 行 / 65536 B -->
# E-41-D04 KD-76 单轮入口设备实跑（1 轮；主口径不可评）

| 字段 | 值 |
|---|---|
| evidence-id | E-41-D04 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 设备轨步骤四（计划 ask 4；计划 §2 WS3 18–23） |
| gate-or-kd | KD-76（批次 40 记录 §9.1-2、§8 #6） |
| verdict | **PASS（仅单轮入口可用性）**；主口径 **NOT EVALUABLE**（n=1/20）：G1 green、G2 `NOT EVALUABLE`，不得据此引用 p95 或判词 |
| executed-at | 2026-11-03T01:02–01:03+08:00（UTC 17:02–17:03Z） |
| operator | 设备轨 |
| head | `62522930` |
| device-serial | emulator-5584（API 34 / x86_64 / 4096；本轮 reboot 后 bootCount=2） |
| apk-sha256 | app `5793300c5f453a45715e9846f1e16756dac1300e04dd68d026e6f7957bccd796`；app test `0e906482…` |
| exit-code | 脚本 exit=0；2×`am instrument` 内层 exit=0（脚本 1 轮） |
| raw-log | `build/device-track-batch41/kd76-single/{manifest.txt,round-01.xml,round-01.am.txt}` |
| record-ref | 计划 §2 WS3 18/19/23；`app/src/androidTest/.../VisualJsConnectStageCalibrationInstrumentedTest.kt:260-305,1044-1046` |
| sanitized | 是 |

## 1. 目的与命令

脚本可用性已验证（`tools/ci/run_kd76_rounds.sh` 与 `tools/ci/summarize_kd76.py` 在树；单轮入口 `readRoundFromInstrumentationArg` 存在）。本轮按 ask 跑**单轮**，不跑 20 轮（20 轮需 ≈20 次 reboot + 外部干净窗，属 WS3/门禁阶段判词工作）。

```bash
export ADB=/c/Android/Sdk/platform-tools/adb.exe
bash tools/ci/run_kd76_rounds.sh --serial emulator-5584 --rounds 1 \
  --target-package com.tingyun.smartmistakebook.localfirst \
  --component com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner \
  --out build/device-track-batch41/kd76-single
```

- `round 1/1: preparing (reboot)` → `round 1 OK 9s`；`failedRounds=0`；`roundsRequested=1`。
- 脚本自身警告：`n=1 != 20; summarize_kd76.py will report NOT EVALUABLE for the main criterion`。

## 2. 单轮原始样本（不是 p95、不是判词）

`KD76_SAMPLE round=1 status=SAMPLE`（`round-01.xml`）：

| 字段 | 值 |
|---|---|
| bind | 710 ms |
| warmup | 206 ms |
| readyGap | 16 ms |
| firstByte | 228 ms |
| total | 1160 ms |
| generation | -2863489379728732323 |
| loadBefore / loadAfter | -1.00 / -1.00（instrumentation 侧不可读 /proc，见 R5 §2.2） |
| apkSha256（设备内自报） | `5793300c…`（与宿主一致） |
| pageSize / sdk / abis | 4096 / 34 / x86_64,arm64-v8a |
| bootCount | 2（本轮 reboot 后的冷启动形状） |

## 3. 汇总脚本输出（原样要点）

```text
KD-76 main criterion M = per-round (S-bind + S-warmup), nearest-rank p95 (n must be 20)
samples: n=1/20 | G1: green
G2: NOT EVALUABLE (n=1/20; G1 green; snapshot consistent; input incomplete)
  - missing rounds: [2..20] (expected exactly 1..20)
  no verdict may be derived from this run; do not quote a p95 or a gate result
```

- 结论：单轮入口按"一轮 = 一次独立 `am instrument` 进程 + 外部 reboot"工作；**KD-76 主口径仍未评估**（n 不足），不得把 1160 ms 当 M，也不得回填台账的 p95。

## 4. 未验证 / 边界

- 20 轮（两次独立冷启动各 10 轮）、G1/G2 正式判词、29.5 s 分段脚本结论均**未在本 ask 运行** → UNVERIFIED。
- 本轮单次的 `loadBefore=6.49`（`KD76_ENV` 行）是 reboot 后系统启动残余；因不构成判词，未作取舍，也未删点。
