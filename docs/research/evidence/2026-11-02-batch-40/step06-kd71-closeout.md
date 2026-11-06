<!-- 体积自查：约 95 行；R4 上限 400 行 / 65536 B -->
# E-40-06 KD-71 关闭核销（G1–G6）

| 字段 | 值 |
|---|---|
| evidence-id | E-40-06 |
| convention-version | 1 |
| batch | 2026-11-02-batch-40 |
| step | 步骤五（判据 §51；KD-71） |
| gate-or-kd | R8 §5 G1–G6 |
| verdict | **PASS（条件式）**：G1/G2/G3/G5 满足；G4 未做（红向敏感性未实证）；建议“条件式关闭” |
| executed-at | 2026-11-02T10:55–10:57+08:00 |
| operator | 设备验证轨 |
| head | 执行时 `dee81ea7`；三文件与构建快照逐字节一致 |
| worktree | 源漂移仅 KB 线；KD-71 三文件 `git status` 为空 |
| device-serial | emulator-5584（AVD `kb_test_device`，API 34，x86_64，page-size 4096） |
| apk-sha256 | data test `34e3435d9fa5ef0b7f18dc0b02cd229d35c9913e2565174c5b619c77bf103179`（设备侧同值） |
| exit-code | 3× adb-exit=0；3× `OK (27 tests)`；code0=27，codeNeg=0 |
| raw-log | `build/device-track-batch40/suites/suite-engine{,-2,-3}.log` + `suite-engine{,-2,-3}-logcat.txt`；`src-sha-before.txt`/`src-sha-final.txt` |
| record-ref | `docs/research/2026-11-02-batch-40-record.md`；`docs/known-defects.md` KD-71 |
| sanitized | 是 |

## G1 源绑定（必要）— PASS

```text
7e6135be8e5d24155da3a549f9483027c799e767926b209a5e2bf1b6311a08b5  QuickJsVisualExecutionEngine.kt
6597f20f5ea3721b8379911c42e991262372274e56717ef18af0ca4462bf5022  QuickJsVisualExecutionEngineInstrumentedTest.kt
8b23975f171003f0ac7dedef19de27afa284b4178b54deaa993f4d0968e822e5  QuickJsVisualExecutionEngineJvmTest.kt
```

- 与题目/R8 §3.2 记录逐字节同值；`git status --porcelain -- <三文件>` 为空；`src-sha-before.txt` 与 `src-sha-final.txt` 均含上述三行。
- 说明：同批 KB 线使 `StudyDatabase.kt` 等 60 个文件漂移，但**不涉及** KD-71 三文件；KD-71 证据只绑定三文件 + APK sha。

## G2 套件与逐例（必要）— PASS

| 轮 | 命令 | 结果 | 时间 |
|---|---|---|---|
| 1 | `am instrument -e class ...QuickJsVisualExecutionEngineInstrumentedTest` | `OK (27 tests)`，code0=27，codeNeg=0 | 7.830 s |
| 2 | 同命令 | `OK (27 tests)`，code0=27，codeNeg=0 | 7.906 s |
| 3 | 同命令 | `OK (27 tests)`，code0=27，codeNeg=0 | 7.864 s |

- 3 次全绿 → 可写“**稳定绿**”。
- 逐例可见（三轮均在 `:189/:196`、`:301/:308`、`:343/:350`）：
  - `closeDuringAnInFlightTransactionWaitsAndRejectsIt`（code 0）
  - `concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome`（32 轮，code 0）
  - `closingIsIdempotentAndExecuteAfterCloseIsRejected`（code 0）
- 未用 `connectedAndroidTest` 合并口径。

## G3 环境与崩溃（必要）— PASS

- serial/AVD/API/ABI/page-size：`emulator-5584 / kb_test_device / 34 / x86_64 / 4096`。
- data androidTest APK sha 与安装包一致（`34e3435d…3179`）。
- 三轮 logcat 九类签名全 0（含 `FATAL EXCEPTION`、`ANR in`、`SIGSEGV`、`death was not confirmed`）；无 `Error in`。

## G4 变异/回退（强烈建议，不阻塞）— 未做

- 未做 `closed` 判定移出临界区 / 删 `abandoned.get()` 复查的变异；未产生变异 diff、红输出与还原复跑。
- 关闭文案必须写“**红向敏感性未实证**”。

## G5 关闭后守恒（必要）— PASS

- `closingIsIdempotentAndExecuteAfterCloseIsRejected` 绿；`close()` 幂等；三轮总时长 7.8–7.9 s（含 27 例与 32 轮压测），未见被卡满上界。

## G6 证据格式（必要）— PASS

- 本文件含 evidence-id/verdict/executed-at/head/worktree/device-serial/avd/api/abi/page-size/apk sha/exit-code/raw-log/record-ref；正文含 G2 逐例表与 G1 三哈希。
- 待台账：`docs/known-defects.md` KD-71 追加 Progress 并回链本文件（由记录员执行；本轨不做 git 写）。

## 裁决

- G1+G2+G3+G5 全满足 → 可“**条件式关闭**”。
- 建议文案：模拟器口径；G4 未做（红向敏感性未实证）；#240 上游仍 open；`close()` 返回不代表无 JS 在执行。
- 三文件任一改动（含 M-03 之外 hunks）→ 本证据失效，必须重跑后再关。
