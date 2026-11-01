# E-04 PDF 16 例：`VisualDrawOpsPdfPainterInstrumentedTest`（PASS 16/16）＋ KD-79 逐项核销

| 字段 | 值 |
|---|---|
| evidence-id | E-04 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 四（PDF 套件；判据 §2.4） |
| gate-or-kd | **KD-79**（`writeTo` fail-open；台账最低口径 + 计划 C.6 口径） |
| verdict | **PASS（台账最低口径满足）**：`OK (16 tests)`；`pdfWriteFailureIsCoded` 绿；logcat 230 条 native `java.io.IOException: simulated write failure`。**D3 的 ENOSPC/静默短写/页数不符/`renameTo` 失败在设备面仍无用例 → 部分 UNVERIFIED** |
| executed-at | 2026-10-29T02:30:20+0800 前后（设备 UTC 18:30） |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525e` |
| worktree | dirty（C.6 面 `VisualDrawOpsPdfPainter.kt`/`PdfWriteIntegrity.kt` 为 M/??；本轨零写入） |
| device-serial | `emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | `data-debug-androidTest.apk` sha256 `cb88851b…4b1951`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 shell `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch30/step04-pdf.log` / `step04-pdf-logcat.txt`（20583 行） |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是 |

## 1. 命令

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPdfPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch30/step04-pdf.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch30/step04-pdf-logcat.txt
```

## 2. 结果

```text
Time: 4.776
OK (16 tests)
INSTRUMENTATION_CODE: -1
```

## 3. KD-79 关闭判据逐条（判据 §4）

| ID | 检查 | 实测 | 判定 |
|---|---|---|---|
| 79-P1 | PDF 16 例全绿 | `OK (16 tests)` | PASS |
| 79-P2 | `pdfWriteFailureIsCoded` → `PAINTER_PDF_IO` | 套件绿（批次 29 此处 `expected VisualDrawOpsPaintingException` 红） | PASS |
| 79-P3 | `pdfFileWriteRoundTripsThroughReadback` | 套件绿（目标存在/非空/含 `%%EOF`/`PdfRenderer` 回读 48×32/无 `.pending-` 残留） | PASS |
| 79-N1 | 首写即抛 → 有码 | 同 79-P2 | PASS |
| 79-N2 | `pdfWriteFailureMidStreamKeepsTheFirstFailureText`（首块后抛，`acceptedBytes>0`） | 套件绿 | PASS |
| 79-N3 | 磁盘满（ENOSPC）模拟 | **无用例** | **UNVERIFIED** |
| 79-N4 | 静默短写/尾标缺失（设备面） | **无设备用例**（仅 JVM `PdfWriteIntegrityTest`，本轨不跑单测） | **UNVERIFIED** |
| 79-N5 | 页数回读不符注入 | **无用例** | **UNVERIFIED** |
| 79-N6 | 只读目标 → `PAINTER_PDF_IO` 且含 `not writable`；`renameTo` 失败注入 | 只读目标用例绿；`renameTo` 失败**无用例** | 部分 PASS / **UNVERIFIED** |
| 79-L1 | `grep -c "java.io.IOException: simulated write failure"` **> 0** | **230** | PASS |
| 79-L2 | 9 类签名全 0、无 `FATAL EXCEPTION` | 0（`step08-signatures.md`） | PASS |
| 79-L3 | 若 P2 红需显示期望/实际 | P2 绿，不适用 | — |

## 4. 裁决

- **关闭条件（`docs/known-defects.md:2925`；判据文档误标 `:2921`，见 `00-index.md` §5）= 79-P1 + 79-P2 +
  79-L1 + 79-L2 → 全部满足，KD-79 可按 KD 自身条件关闭。**
- **计划 C.6 口径**另需 79-P3 + 79-N2 → 亦满足；但 **N3/N4/N5 与 N6 的 `renameTo` 失败仍无设备用例**，
  因此 **D3 的"磁盘满/静默短写/页数不符/rename 失败"不得写成已覆盖**。磁盘满未尝试构造（避免破坏设备）。
- logcat 230 条 native 打印证明失败路径确实被走到；该条数随 chunk 浮动，**不作固定值断言**（判据 §4 注）。

## 5. 未验证项

- **UNVERIFIED**：ENOSPC 模拟、静默短写（设备面）、页数回读不符注入、`renameTo` 返回 false 注入。
- **UNVERIFIED**：`writeFile` 在真机外部存储/`/sdcard` 的行为（本轨只跑 app 私有 cacheDir 夹具）。

## 脱敏与去噪（R4 §4.4）

- 扫描：本批目录四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）；本文件无密钥/凭据/PII。
- 去噪：仅保留 summary、判据表与 230 计数；未删除 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 二进制/截图：无。全量见头部 `raw-log`（`build/`，不入仓、不作关闭依据）。
