# E-01 隔离套件 13 例复跑：`VisualJsIsolationInstrumentedTest`（PASS 13/13）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 一（隔离 13 例；判据 §2.2） |
| gate-or-kd | KD-78 ④-P3/P4/L2；批次 29 隔离 12 红修复后复跑 |
| verdict | **PASS**：`OK (13 tests)`；0 失败/错误/跳过；spawn↔ready **17↔17 且 pid 集合逐值相同**；`warm-up failed`=0；kill 四类之和 17=spawn；无存活 worker |
| executed-at | 2026-10-29T02:28:09–02:29:09+0800（设备 UTC 18:28:09–18:29:09） |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525e` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | `app-strictOffline-debug-androidTest.apk` sha256 `2dd22651…950d19`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 shell `EXIT=0`（**非判据**；判据 = summary 行） |
| raw-log | `build/device-track-batch30/step01-isolation.log` / `step01-isolation-logcat.txt`（1254 行） |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是 |

## 1. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch30/step01-isolation.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch30/step01-isolation-logcat.txt
```

## 2. 结果

```text
Time: 60.277
OK (13 tests)
INSTRUMENTATION_CODE: -1        # 恒 -1，不作判据（判据 §2-2）
```

## 3. 套件级判据（逐条，判据 §2.2）

| 判据 | 命令 | 实测 | 判定 |
|---|---|---|---|
| summary | `tail` log | `OK (13 tests)` | PASS |
| 失败/错误/跳过 | 同上 | 无 `Failures`/`Error`/`Skipped` | PASS |
| spawn 数 | `grep -c "Start proc .*:visual_js"` | **17** | — |
| ready 数 | `grep -c "visual_js warm-up finished"` | **17** | — |
| spawn↔ready pid 1:1 | spawn pid 集 vs warm-up 行 pid 集 | 两集合 `diff` 为空（17 个 pid 逐值相同） | PASS |
| warm-up 失败 | `grep -c "visual_js warm-up failed"` | **0** | PASS |
| 杀因归类 | 4 类求和 | `hard deadline (1500 ms) for request`=5 + `terminate(generation`=4 + `the host unbound the visual_js worker`=7 + `due to start instr`=1 = **17** = spawn | PASS |
| 未分类杀因 | 逐行读 `VisualJsService` kill 行 | 无；`AMS isolated not needed` 为系统回收（判据 §2.2-5 不计入） | PASS |
| 事后残留 | `ps -A -o PID,NAME \| grep visual_js` | 空（`NO-VISUAL_JS-PROCESS`） | PASS |
| 9 类签名 | 见 `step08-signatures.md` | 0 | PASS |

## 4. 与批次 29 对照（同设备）

批次 29 同套件为 `Tests run: 13, Failures: 12`，`Start proc`=14 / `warm-up finished`=0 / `warm-up failed`=12
（`docs/known-defects.md:2902`）。本批 spawn、ready 均为 17 且逐 pid 配对，**warm-up 全灭形态未复现**。

## 5. 未验证项

- **UNVERIFIED**：`emulator-5566/-5584` 未跑（他线设备）；真机/arm64 未跑。
- 本文件只证隔离套件；KD-78 ④ 的合并裁决见 `step02-flags.md` §4。

## 脱敏与去噪（R4 §4.4）

- 扫描：本批目录四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）；本文件无密钥/凭据/PII。
- 去噪：仅保留 summary、pid 配对与杀因计数行；未删除 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 二进制/截图：无。全量见头部 `raw-log`（`build/`，不入仓、不作关闭依据）。
