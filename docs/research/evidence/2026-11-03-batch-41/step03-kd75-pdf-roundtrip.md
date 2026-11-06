<!-- 体积自查：约 120 行；R4 上限 400 行 / 65536 B -->
# E-41-D03 KD-75 PDF 公开 API 设备往返（含一次证据目录缺陷修复）

| 字段 | 值 |
|---|---|
| evidence-id | E-41-D03 |
| convention-version | 1 |
| batch | 2026-11-03-batch-41 |
| step | 设备轨步骤三（计划 ask 3；计划 §2 WS2 11–16） |
| gate-or-kd | KD-75（批次 40 记录 §9.1-1；§8 #5 由"用例不在树"变为"用例在树且设备绿"） |
| verdict | **PASS（本机 + 本 APK）**：修复证据目录后 10/10 绿（`OK (6 tests)` + 4×`OK (1 test)`），skip=0；pypdf 独立读回 7 文件全部符合 |
| executed-at | 2026-11-03T00:57–01:01+08:00（UTC 16:57–17:01Z） |
| operator | 设备轨 |
| head | `62522930`；源快照 `src-sha-after-fix.txt`（`fc93c37b…`） |
| device-serial | emulator-5584 |
| apk-sha256 | app `5793300c…`；app androidTest 修复后 `0e906482a0a98d1e4b9f5bb56a3262a80a32d9f6404b80495289b22cec4e3aca`（宿主=设备） |
| exit-code | 5×`am instrument` exit=0；10 例 code0，skip=0 |
| raw-log | `build/device-track-batch41/pdf/*.log` + `*-logcat.txt`；设备证据已拉到 `build/device-track-batch41/pdf/device-evidence/*.pdf` |
| record-ref | 批次 40 记录 §8 #5、§9.1-1；研究 R9 `docs/research/2026-11-03-instrumentation-pdf-verification.md` |
| sanitized | 是 |

## 1. 覆盖与执行形状

`app/src/androidTest/.../pdf/PdfAigcDeviceRoundTripInstrumentedTest.kt`（WS2 新增，10 `@Test`）：

| 请求项 | 方法 | 执行形状 | 结果 |
|---|---|---|---|
| 11 公开 API 往返（表型） | `classicXrefTablePdfSurvivesInjectionAndOpensInPlatformRenderer` | 与另 5 例同进程 | PASS |
| 11 真实 PdfDocument 产物 | `pdfDocumentChineseTwoPagePdfSurvivesInjectionAndOpensInPlatformRenderer` | 同上 | PASS |
| 12 流型 xref | `xrefStreamPdfSurvivesInjectionAndOpensInPlatformRenderer` | 同上 | PASS |
| 13 幂等二次注入 | `secondInjectionWithSameMetadataKeepsOneInfoAndBytePrefix` | 同上 | PASS |
| 14 畸形 fail-closed 四类 | `malformedHeader…` / `missingStartXref…` / `malformedXrefTable…` / `encryptedPdf…` | **逐方法独立进程**（R9 §6.4） | 4×PASS |
| 15 内容政策 | `modelParsedTextForcesAigcLabelling` / `userOnlyContentStaysUnlabelled` | 与 6 例同进程 | PASS |

- 命令（修复后）：
  ```bash
  adb -s emulator-5584 shell am instrument -w -r \
    -e class com.tingyun.smartmistakebook.pdf.PdfAigcDeviceRoundTripInstrumentedTest \
    com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
  ```
  实际按 `roundtrip6`（6 方法）+ 4 个畸形方法分次执行：`OK (6 tests)`、4×`OK (1 test)`，时间 0.447 s / 0.016–0.033 s。
- 未使用 `:app:connectedLocalFirstDebugAndroidTest`。

## 2. 首次实跑：红（缺陷证据）

- 首次在 `683cfafd…` 测试 APK 上跑：`Tests run: 6, Failures: 4`；四个畸形方法各 `Tests run: 1, Failures: 1`。
- 失败签名（会话内 `am instrument` 输出，原始 log 在重跑前被 `rm -rf pdf/` 覆盖，**不再可复跑**，故此处只作文本记录）：
  ```text
  java.lang.IllegalStateException: cannot create evidence directory
    /data/user/0/com.tingyun.smartmistakebook.localfirst.test/files/pdf-aigc-device/run-…
    at PdfAigcDeviceRoundTripInstrumentedTest.evidenceDirectory_delegate$lambda$0(...:76)
  ```
- 机制（本轮实读/实跑证明，不是猜测）：instrumentation 进程运行在**被测应用** uid 下。
  `run-as com.tingyun.smartmistakebook.localfirst id` → `uid=10193`；`run-as com.tingyun.smartmistakebook.localfirst.test id` → `uid=10194`。测试包自己的 `filesDir` 属 10194，被 10193 进程写入即 EACCES。
- 影响：4 个断言用例 + 4 个畸形用例在**执行断言之前**就失败；不是产品 PDF 代码红。

## 3. 最小修复（本项落点内，仅测试证据路径）

- 文件（WS2 新建，untracked）：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/pdf/PdfAigcDeviceRoundTripInstrumentedTest.kt`
- sha256：`eeae8609…1ba07` → `cf44836c…f34d`（`src-sha-before.txt` vs `src-sha-after-fix.txt`；全库 diff = 1 文件 / 4 行）。
- 改动：`instrumentation.context.filesDir` → `instrumentation.targetContext.filesDir`，并把 KDoc 的 `run-as <test-pkg>` 改为 `<app-pkg>` 并写明 uid 机制。**未改任何断言、未删用例、未改期望值。**
- 重建/重装：`./gradlew --console=plain :app:assembleLocalFirstDebugAndroidTest` → exit=0；测试 APK `683cfafd…` → `0e906482…`；`install -r -t` Success；设备侧 sha 同值（E-41-D01 §3）。
- 重跑：10/10 绿（§1）。

## 4. 设备产物与独立读者（pypdf）

- 证据目录（被测应用 filesDir）：`/data/user/0/com.tingyun.smartmistakebook.localfirst/files/pdf-aigc-device/run-1793638832491-7333/`；经 `run-as` 拉出 7 个 PDF（`build/device-track-batch41/pdf/device-evidence/`）。
- `python -I` + pypdf **6.16.2** 独立读取（不与 `PdfAigcReader` 共享解析器）：

| 文件 | 页 | 页面尺寸(pt) | `/AIGC` | 7 键 | Label |
|---|---|---|---|---|---|
| `classic-table-source.pdf` | 2 | 200×200 | NO | — | — |
| `classic-table-labeled.pdf` | 2 | 200×200 | YES | 7/7 | `1` |
| `classic-table-twice.pdf` | 2 | 200×200 | YES | 7/7 | `1` |
| `xref-stream-source.pdf` | 2 | 200×200 | NO | — | — |
| `xref-stream-labeled.pdf` | 2 | 200×200 | YES | 7/7 | `1` |
| `pdfdocument-chinese-source.pdf` | 2 | 595×842 | NO | — | — |
| `pdfdocument-chinese-labeled.pdf` | 2 | 595×842 | YES | 7/7 | `1` |

- 幂等：`classic-table-twice.pdf` 与 `classic-table-labeled.pdf` 字节完全相同（sha256 `ab76ae18…527c2b`）。
- pypdf 对全部 7 文件打印 `incorrect startxref pointer(1)` 后仍成功解析（读者容忍行为，R6 议题；本轮不据此改动注入器）。

## 5. 未验证 / 边界

- 不覆盖真实导出链（`core/export` 热区，仅 patch 草案由 WS2 出）；本类不依赖 `core/export`。
- 独立读者只做结构/元数据读回，**未做像素级渲染比对**（平台 `PdfRenderer` 的页数/尺寸/打开由设备用例断言）。
- 该红→绿修复改变了 WS2 的 untracked 测试文件 sha；WS2 若并行改同一文件需以本 sha 为基线复核。
