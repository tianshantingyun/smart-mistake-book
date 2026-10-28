# E-03 painter 全量 24 例：`VisualDrawOpsPainterInstrumentedTest`（PASS 24/24）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 三（painter 套件；判据 §2.1） |
| gate-or-kd | 批次 29 painter 21 例 1 红修复后整包复跑；KD-78 ③ 设备像素正例、④-P1 |
| verdict | **PASS**：`OK (24 tests)`；0 失败/错误/跳过；9 类签名 0 |
| executed-at | 2026-10-29T02:29:40+0800 前后（设备 UTC 18:29） |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525e` |
| worktree | dirty（他线在飞；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | `data-debug-androidTest.apk` sha256 `cb88851b…4b1951`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch30/step03-painter.log` / `step03-painter-logcat.txt`（126 行） |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是 |

## 1. 命令

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch30/step03-painter.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch30/step03-painter-logcat.txt
```

## 2. 结果

```text
Time: 0.408
OK (24 tests)
INSTRUMENTATION_CODE: -1
```

判据 §2.1 逐例（24 条）的"设备必须成立"条件均由套件内断言执行；本文件不改写其数字，只在
本批记录"整包 24/24 绿"。关键闭合项：

| 面 | 用例 | 结论 |
|---|---|---|
| KD-78 ③ 设备像素正例 | `anEightDigitColorAlphaMultipliesWithTheOpAlphaInsteadOfBeingOverwritten`（`:253`） | 24 绿内含 |
| KD-78 ④-P1 | `theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint`（`:264`） | 24 绿内含；expected 0 == actual 0（批次 29 此处为 1283 红） |
| 8 位色 alpha、dash 残尾、空 dash 有码拒 | 批次 29 修复面 | 整包绿 |
| `drawRendered` 两例（冻结命令序列/缺命令 fail-closed） | `:489`/`:511` | 整包绿 |
| 真引擎 compile→校验→上色 | `:543` | 整包绿 |

## 3. 判据补充

- 判据 §2.1 的 flags 恒抛门：`grep -c "measure paint flags .* do not match the environment fingerprint"` = **0**（`step08-signatures.md`）。
- 本套件不涉及 PDF；屏/PDF 一致性见 `step05-parity.md`。

## 4. 未验证项

- **UNVERIFIED**：跨设备/arm64/真机像素；判据 §7.4-6 明确跨设备像素差不作产品结论。
- 判据 §2.1 逐例的像素期望值未在本文件重抄；以 raw log 与套件断言为准。

## 脱敏与去噪（R4 §4.4）

- 扫描：本批目录四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）；本文件无密钥/凭据/PII。
- 去噪：仅保留 summary 与套件级结论；未删除 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 二进制/截图：无。全量见头部 `raw-log`（`build/`，不入仓、不作关闭依据）。
