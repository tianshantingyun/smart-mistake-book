# E-02 跨侧 flags 2 例：`VisualMeasureReplayPaintFlagsInstrumentedTest`（PASS 2/2）＋ KD-78 ④ 逐项核销

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 二（flags 跨侧 2 例；判据 §2.3） |
| gate-or-kd | **KD-78 ④**（measure/replay flags 不在指纹；设备修复后复跑） |
| verdict | **PASS**：`OK (2 tests)`；KD-78 ④ 九项（P1–P4/N1–N2/L1–L3）**全部满足 → ④ 可关闭**（整条 KD-78 是否关闭按 `docs/known-defects.md:2888` 四项另行核销） |
| executed-at | 2026-10-29T02:29:20+0800 前后（设备 UTC 18:29） |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525e` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | `app-strictOffline-debug-androidTest.apk` sha256 `2dd22651…950d19`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch30/step02-flags.log` / `step02-flags-logcat.txt`（613 行） |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是 |

## 1. 命令

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch30/step02-flags.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch30/step02-flags-logcat.txt
```

## 2. 结果

```text
Time: 0.016
OK (2 tests)
INSTRUMENTATION_CODE: -1
```

## 3. KD-78 ④ 关闭判据逐条（判据 §3）

| ID | 检查 | 实测 | 判定 |
|---|---|---|---|
| ④-P1 | painter `theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint` | painter 套件 24/24 绿（`step03-painter.md`） | PASS |
| ④-P2 | flags 第 1 例：度量 Paint flags=0、`environment.paintFlags=0`、指纹以 `|paintFlags=0` 结尾、`Paint().flags != 0` | 2 例全绿；`PARITY_ENV` 同设备指纹含 `replayTextPaintFlags=0`（`step05-parity.md`） | PASS |
| ④-P3 | 隔离 13 全绿（7 例依赖真实 warm-up 的 `awaitReady()`） | `OK (13 tests)`（`step01-isolation.md`） | PASS |
| ④-P4 | `warm-up finished` pid 集 = `Start proc` pid 集；`warm-up failed`=0 | 17↔17 逐 pid 相同；failed=0 | PASS |
| ④-N1 | 坏指纹 `paintFlags=1` 被 `VisualMeasurementEnvironmentException` 拒绝且消息含 `paintFlags=1` | 第 2 例绿（`aFingerprintThatDisagreesWithTheActualPaintFailsClosed`） | PASS |
| ④-N2 | `Paint().flags != 0`（API 34 平台默认 1283，常驻钉子） | 第 1 例内含该断言，绿 | PASS |
| ④-L1 | 三份 logcat `measure paint flags .* do not match the environment fingerprint` = 0 | isolation/flags/painter 三份各 **0** | PASS |
| ④-L2 | `warm-up failed`=0；kill 四类之和=spawn；无未分类杀因 | 0；5+4+7+1=17；无未分类 | PASS |
| ④-L3 | 三份 logcat 9 类签名全 0、无 `FATAL EXCEPTION` | 三份各 0（`step08-signatures.md`） | PASS |

## 4. 裁决

- **④ 的九项全部满足 → ④ 可关闭。** 批次 29 的 `measure paint flags 1283 do not match the
  environment fingerprint paintFlags=0`（`docs/known-defects.md:2902`）在修复后设备绿轮中**未复现**
  （三份 logcat 0 命中）。
- **不越界**：本文件只裁决 ④；KD-78 整条（①stroke-only/②dash/③alpha/④flags）仍须按
  `docs/known-defects.md:2890` 四项逐项核销，① ② 的生成侧缺口不在本批设备面。
- 判据 §3 的"未执行的逻辑负例"仍未做：本轨未把常量改回平台默认，故无"回归会红"的执行证据。

## 5. 未验证项

- **UNVERIFIED**：逻辑负例（改回 1283 后 P1/P2 应变红）未执行。
- **UNVERIFIED**：真机/arm64 未跑；`-5566/-5584` 未碰。

## 脱敏与去噪（R4 §4.4）

- 扫描：本批目录四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）；本文件无密钥/凭据/PII。
- 去噪：仅保留 summary 与九项判据行；未删除 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 二进制/截图：无。全量见头部 `raw-log`（`build/`，不入仓、不作关闭依据）。
