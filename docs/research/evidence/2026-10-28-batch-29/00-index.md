# 批次 29 设备证据索引（设备执行 2026-10-28 22:30–22:40 +08:00）

- 计划：`.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md`（先完整读）
- 判据：`docs/research/2026-10-28-batch-29-device-criteria.md`（T1；本轨按其 §6 串行）
- 规范版本：`docs/research/2026-10-26-device-evidence-convention.md` v1（R4）
- HEAD：`3d9372e5`；工作树 dirty（T2/T3/T5/T6 在飞，见各 step）
- 执行者：设备验证轨（subagent，批次 29）
- 时间口径：`executed-at` 用主机时钟（+08:00）；logcat 时间戳为设备 UTC（=主机 −8h）
- 纪律：零 `connectedAndroidTest`、零 `adb root`、零 git 写操作；Gradle 仅
  `assembleStrictOfflineDebug` / `assembleStrictOfflineDebugAndroidTest` /
  `core:data:assembleDebugAndroidTest`
- **结论先行**：6 步串行全部执行完毕；**4 个套件红、4 个套件绿**（签名面另计，全 0）。
  两个独立根因均归本批产品层（C 类），未放宽任何断言、未过滤任何用例。详细归因见 step01/step02/step04。
- 判据口径更正：T1 总表预填 painter `OK (19 tests)`；T5 已把套件改为 **21 例**（含 8 位色 alpha、
  replay flags 两新例），按 T1 §3.1"以落码后 `grep -c` 实测为准"，本轨判据为 `OK (21 tests)`。

## 1. 环境（§6.0 重新登记）

| 字段 | 值 |
|---|---|
| serial | `emulator-5562`（新建实例；**未触碰 `emulator-5584`/`kb_test_device`**，T1 §6.0/§8 判其属他线） |
| AVD | `test_device`（API 34 / google_apis / x86_64 / 4 KB 页，与批次 28 同型） |
| API / ABI | 34 / x86_64 |
| PAGE_SIZE | 4096 |
| fingerprint | `google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| 启动 | 冷启动 `-no-window -no-audio -no-boot-anim -no-snapshot-load`；`Boot completed in 22437 ms` |
| 沉降 | 登记时 uptime 65.15 s / load 6.70；安装前 load 3.73（`build/emulator-5562.log`） |
| 在线设备 | 执行时 `adb devices` = `emulator-5562`（本轨新建）+ `emulator-5584`（他线，未用） |

## 2. 构建与产物身份

```bash
./gradlew --console=plain :app:assembleStrictOfflineDebug \
  :app:assembleStrictOfflineDebugAndroidTest :core:data:assembleDebugAndroidTest
# BUILD SUCCESSFUL in 40s；293 actionable tasks: 17 executed, 276 up-to-date
```

| 产物 | 字节 | sha256（宿主=设备，逐字节一致） |
|---|---|---|
| `app-strictOffline-debug.apk` | 189,412,776 | `05a5b8bd28ece00b1d84ea086bc9b99a414d36e4111201214e6994c921cd24d7` |
| `app-strictOffline-debug-androidTest.apk` | 1,586,305 | `229cd58eb2fb68b83684498b75fbc21582544967c9a68fc6ad1ffd074f6edd68` |
| `data-debug-androidTest.apk` | 197,656,497 | `4d152b5afbd9f3fa5eb451774370fe56d683a1f17a7e42101c9a67e6fa72cd81` |

- 安装：三条 `adb install -r -t` 均 `Success`；`pm path` + 设备侧 `sha256sum` 与上表逐字节一致。
- **未复用批次 28 的 painter APK**（T1 §3 结论）。
- 新代码确实在包内（防 UP-TO-DATE 假象）：core:data 测试 dex 含
  `textPaintFlagsForTest`、`theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint`；
  app 主 dex 含 `paintFlagsForTest`/`forEnvironment`；app 测试 dex 含
  `VisualMeasureReplayPaintFlagsInstrumentedTest`。
- dex 符号门（T1 §6.3）：app 测试 APK 含 `VisualJsIsolationInstrumentedTest` 与
  `VisualJsHandshakeRaceInstrumentedTest`；core:data 测试 APK 含 `VisualDrawOpsPainterInstrumentedTest`、
  `VisualDrawOpsPainter`、`VisualDrawOpsPdfPainter`、`VisualDrawOpsParityInstrumentedTest`。

## 3. 步骤与结论

`am instrument` 的 shell exit 恒为 0，**判据只认 summary 行**（T1 §1）。下表 exit 指宿主 shell。

| step | 内容 | 用例数 | 结果 | 判定 |
|---|---|---|---|---|
| 01 | painter 全量 | 21 | `Tests run: 21, Failures: 1`（Time 0.265） | **FAIL**：`theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint`，expected 0 / actual 1283 |
| 02 | 隔离 13 + Race 2 | 13 / 2 | `Tests run: 13, Failures: 12`；`OK (2 tests)` | **隔离 FAIL / Race PASS**（隔离 12 红同一根因） |
| 03 | T5 语义设备面（flags 2 + 编译器 6） | 2 / 6 | `Tests run: 2, Failures: 1`；`OK (6 tests)` | **flags FAIL / 编译器 PASS**；T2 解析器无设备面 |
| 04 | PDF 13 + 一致性门 10 | 13 / 10 | `Tests run: 13, Failures: 1`；`OK (10 tests)` | **PDF FAIL / 一致性原型 PASS** |
| 05 | 备份抽验 | 34 | `OK (34 tests)`（Time 19.152） | **PASS**（与批次 27/28 一致） |
| 06 | 9 类签名 + worker 收口 | — | 8 份 logcat 签名全 0；隔离 spawn 14 / ready 0 / warm-up 异常 12 | **签名 PASS；worker 收口 FAIL（与隔离同因）** |

## 4. 两个根因（均为本批产品层，C 类）

### R1 — `Paint()` 默认 flags 不是 0（T5/KD-78 ④ 落码假设错误）

- 设备实测两处独立给出 **1283**：painter 设备断言（step01）与 worker warm-up 异常
  （step02，`measure paint flags 1283 do not match the environment fingerprint paintFlags=0`，
  `VisualContentMeasuring.kt:460`）。
- 权威源：AOSP `Paint.java`（android-14.0.0_r1）`public Paint() { this(ANTI_ALIAS_FLAG); }`，
  `HIDDEN_DEFAULT_PAINT_FLAGS = DEV_KERN_TEXT_FLAG | EMBEDDED_BITMAP_TEXT_FLAG | FILTER_BITMAP_FLAG`
  ⇒ `0x1|0x400|0x100|0x2 = 0x503 = 1283`。与设备实测逐位一致。
- 错误落点：`REPLAY_TEXT_PAINT_FLAGS = 0`（`VisualDrawOpsPainterPure.kt:78`）、
  `DEFAULT_PAINT_FLAGS = 0`（`VisualContentMeasuring.kt:184`）、
  `forEnvironment` 一致性门（`VisualContentMeasuring.kt:459-464`）。
- 影响面：painter 1 红、flags 1 红、隔离 12 红；**visual_js worker warm-up 在真机/模拟器上必失败**。
- 本轨未改代码（修复归修复环）；推荐修法见 step01 §4 / step03 §4。

### R2 — `PdfDocument.writeTo` 在 API 34 吞掉 OutputStream 的 IOException（T3 假设错误）

- 设备实测：`FailingOutputStream` 的 IOException 被 native `ExceptionDescribe` 打到 System.err 后
  **清除**，`writeTo` 正常返回，`pdfWriteFailureIsCoded` 拿不到 `PAINTER_PDF_IO`。
- 权威源：AOSP `CreateJavaOutputStreamAdaptor.cpp`（android-14.0.0_r1）`SkJavaOutputStream::write`
  在异常分支执行 `ExceptionDescribe()` → `ExceptionClear()` → `return false`。
- 影响面：`VisualDrawOpsPdfPainter.kt:88-95` 的 `catch (IOException)` 不可达；写盘失败会
  **静默截断 PDF 并报成功**（fail-open）。本轨未改代码。

## 5. 签名面（9 类，T1 §6.5 同口径）

| logcat | 9 类签名计数 |
|---|---|
| step01-painter | 0 |
| step02-isolation | 0（逐类：FATAL/ANR/NoSuchMethod/NoClassDef/UnsatisfiedLink/SIGSEGV/CANNOT LINK/failed to map/death not confirmed 全 0） |
| step02-race | 0 |
| step03-compiler | 0 |
| step03-flags | 0 |
| step04-pdf | 0 |
| step04-parity | 0 |
| step05-backup | 0 |

- 非 9 类签名的异常已抓栈：`VisualMeasurementEnvironmentException`（isolation 12 次）、
  `java.io.IOException: simulated write failure`（pdf **230 次**，每次写 chunk 一次，被 native `ExceptionClear` 吞掉；
  见 `step04-pdf-parity.md:67` 与 `step06-signatures.md:61`）、两处 JUnit `AssertionError`。
  见 step01 §3、step02 §3、step04 §3。
- 无存活 worker：`ps -A -o PID,NAME | grep visual_js` → 空。

## 6. 脱敏与体量

```bash
E=docs/research/evidence/2026-10-28-batch-29
grep -RInE 'sk-[A-Za-z0-9_-]{8,}|AIza[0-9A-Za-z_-]{20,}|ghp_[A-Za-z0-9]{20,}|Bearer[[:space:]]+[A-Za-z0-9._-]{10,}' "$E" || echo "0 hits: secrets"
grep -RInE '(api[_-]?[Kk]ey|password|passwd|secret|access[_-]?[Tt]oken)[[:space:]]*[:=][[:space:]]*[^ <]' "$E" || echo "0 hits: credential assignments"
grep -RInE '[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}' "$E" || echo "0 hits: emails"
grep -RInE '/home/[^/ ]+|/Users/[^/ ]+' "$E" || echo "0 hits: home paths"
find "$E" -type f -name '*.md' | wc -l
find "$E" -type f -name '*.md' -printf '%s\n' | awk '{s+=$1} END {print s " B total"}'
```

- 落盘扫描实跑结果（2026-10-28 证据落盘步复跑，命令即上块）：secrets **0** / credential assignments **0** /
  emails **0** / home paths **1**（= 上块扫描命令自身的正则文本自匹配，非真实路径）。
- 体量（**§9 判据结论与 §10 回链追加后**实测）：**7 个 `.md` / 46,281 B / `du -sk` 60 KiB**；
  单文件最大 `00-index.md` 16,066 B / 202 行（R4 上限 64 KiB/400 行、单批 16 个/512 KiB 均未触）。
  设备轨初扫为 38,901 B（最大 8,686 B/140 行）；差值 = 本步 §9/§10、230 次更正与占位符替换，纯记账，判定不受影响。
- raw log 全部留在 `build/device-track-batch29/`（gitignored），仓内只放摘要。

## 7. 复跑入口（最小）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
# 其余套件只换 -e class 的 FQCN 与 runner（app 套件用 com.tingyun.smartmistakebook.offline.test/…），
# 每个套件的完整命令见对应 step 文件的 §2。
# app 套件 runner: com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner
# core:data 套件 runner: com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
```

各 step 的 FQCN/命令与原始输出路径见对应文件。

## 8. 未验证项

- **UNVERIFIED**：R1/R2 修复后的绿轮——本轨只做验证，未改产品代码；修复环复跑后需新证据。
- **UNVERIFIED**：T2 解析器 JVM 用例——本轨禁 `gradle test`；JVM 门由脚本执行（批次 29 单测门第 2 轮 exit=0），
  本轨未重跑，设备面不存在（见 step03 §2）。
- **UNVERIFIED**：跨 API/真机/arm64/16 KB 页——本机只有 x86_64 4 KB 模拟器。
- **UNVERIFIED（证据落盘时新增）**：R2（PDF fail-open）**未登记 KD**——本索引 §4 R2 是唯一登记处；
  修复环/主会话需决定是否新开 KD 或并入导出面既有 KD（KD-75 是 `/AIGC` 面，不覆盖本缺陷）。
  **（2026-10-28 复核修复轮更新）**：已新开 **[KD-79](../../../known-defects.md)** 并落码
  `PdfWriteFailureTrackingStream`；修复后 PDF 13 例的设备复跑仍 **UNVERIFIED**（修复轮零设备）。

## 9. 三条判据结论（本轮要求；口径与证据齐备）

> 判定分级照 R4 §5.4：`PASS` = 断言在指定设备/产物上成立；`FAIL` = 不成立并附失败行；
> `PARTIAL` = 部分通过/跳过（列计数）；`UNVERIFIED` = 未执行/工具缺失。设备执行本体归设备轨
> （本会话未复跑设备）；本节只做判据映射与结论，代码锚由证据落盘员本轮实读复核。

### 9.1 painter：19 例判据作废，现树判据 = **21 例**；设备结果 **FAIL（20/21）**

| 项 | 事实 | 出处 |
|---|---|---|
| 判据口径 | T1 总表预填 19 为 T5 落码**前**计数；T1 §3.1 明写"判据改为 `OK (新计数 tests)`（原文用尖括号占位），**不得沿用 19**"，"以落码后的 `grep -c "@Test"` 实测为准" | `docs/research/2026-10-28-batch-29-device-criteria.md:132`、`:139` |
| 现树计数 | `grep -c "@Test" VisualDrawOpsPainterInstrumentedTest.kt` = **21**（证据落盘员本轮实跑） | 该测试文件 |
| 设备结果 | `Tests run: 21, Failures: 1` / `Time: 0.265`；20 绿含 8 位色 alpha 新例、残尾 dash、空 dash 兜底、e2e | `step01-painter.md:46-56`、`:59-60` |
| 唯一红例 | `theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint`，`expected:<0> but was:<1283>`，首帧 `VisualDrawOpsPainterInstrumentedTest.kt:267` | `step01-painter.md:48-56`；锚已复核（`:267` 为 `assertEquals(REPLAY_TEXT_PAINT_FLAGS, …)`） |
| 根因 | `Paint()` 默认 flags = 1283（AOSP `Paint.java` android-14.0.0_r1：`Paint(){this(ANTI_ALIAS_FLAG);}` + `DEV_KERN_TEXT|EMBEDDED_BITMAP_TEXT|FILTER_BITMAP` = `0x1|0x400|0x100|0x2`），而常量 `REPLAY_TEXT_PAINT_FLAGS = 0`（`VisualDrawOpsPainterPure.kt:78`）、`textPaint = Paint()`（`VisualDrawOpsPainter.kt:51`） | `step01-painter.md:63-87` |
| 结论 | **判据 = `OK (21 tests)`，实际 20/21 → FAIL（C 类产品缺陷 R1）**；不是夹具/环境/断言漂移，未放宽任何断言 | `step01-painter.md:105-108` |

补充：T1 §3.1 同时列出另一条路（在 HEAD/独立 worktree 上跑 pre-T5 的 19 例）——本批未采用，
因为该绿不代表批次末态生成门（同文件 `docs/research/2026-10-28-batch-29-device-criteria.md:133-138`）。因此"painter 19 例绿/红"在本批**不是有效判据**。

### 9.2 PDF 回放：**FAIL 1/13**（`PAINTER_PDF_IO` 不可达，fail-open）

| 项 | 事实 | 出处 |
|---|---|---|
| 判据 | `VisualDrawOpsPdfPainterInstrumentedTest` 13 例（`grep -c "@Test"` = 13，证据落盘员本轮实跑）、`OK (13 tests)` | `step04-pdf-parity.md:46-55` |
| 设备结果 | `Tests run: 13, Failures: 1` / `Time: 1.167`；12 绿含页尺寸/DPI/原点/同网格/alpha/背景/嵌套 clip/label/hitRegions 不绘制/栅格确定性/超页 fail-closed/共享码族穿透 | `step04-pdf-parity.md:57-59` |
| 唯一红例 | `pdfWriteFailureIsCoded`：`paintingFailureOf` 自抛 `AssertionError("expected VisualDrawOpsPaintingException")`（`VisualDrawOpsPdfPainterInstrumentedTest.kt:217`，调用点 `:446`，锚已复核） | `step04-pdf-parity.md:46-55` |
| 根因 R2 | `PdfDocument.writeTo` 走 native `SkJavaOutputStream::write`，OutputStream 异常被 `ExceptionDescribe()`→`ExceptionClear()`→`return false` 吞掉（AOSP `CreateJavaOutputStreamAdaptor.cpp` android-14.0.0_r1），故 `VisualDrawOpsPdfPainter.kt:88-95` 的 `catch (IOException)` **不可达** | `step04-pdf-parity.md:61-90`（logcat 230 次签名见 `:67-74`） |
| 后果 | 写盘失败 → PDF **静默截断且调用方认为成功**（fail-open）；无有码异常 | `step04-pdf-parity.md:76-81` |
| 结论 | **PDF 回放设备判据 FAIL（1/13）**，归因 C 类产品缺陷；修复建议（委托流置位/内存流二次写）见 `:83-88`。**R2 未登记 KD**（见 §8） | `step04-pdf-parity.md:109-113` |

### 9.3 一致性门原型：**PASS 10/10，但只是原型门**

| 项 | 事实 | 出处 |
|---|---|---|
| 判据 | `VisualDrawOpsParityInstrumentedTest` 10 例（`grep -c "@Test"` = 10，证据落盘员本轮实跑）；判据是**该套件自己的原型容差**，不接产品/CI/发版门 | `step04-pdf-parity.md:94-107` |
| 设备结果 | `OK (10 tests)` / `Time: 0.535`；P01–P08 聚合、P-BGNULL 只观察不判、度量自证、N01–N07 负向（位移/单通道/丢 clip/label 位移/字形替换/alpha/丢 op）全部被抓到 | `step04-pdf-parity.md:100-103` |
| 结论 | **PASS（仅原型门）**：可证明"对照方法能抓到注入的 7 类差异"，**不得**引用为产品/发版一致性结论；阈值与容差仍是原型值 | `step04-pdf-parity.md:105-107` |

## 10. 回链（记录 / KD / 契约 / 清单）

| 目标 | 动作 | 落点 |
|---|---|---|
| 批次记录 | **尚未创建** `docs/research/2026-10-28-batch-29-record.md`（记录步在修复环之后）；本索引 §3 已给出记录 §4 所需的 `E-ID ↔ 套件 ↔ 结论` 行映射，记录步按 R4 §7.2 写 `E-ID` + `` `evidence/2026-10-28-batch-29/step01-painter.md:55` ``（该相对路径以 `docs/research/` 为基准；本索引内的 step 引用是同目录裸名） | 本文件 §3 |
| KD-78 | 新增批次 29 设备证据 Progress：③ 设备像素正/负例已上设备并绿；④ 设备面红（R1），KD-78 维持 open | `docs/known-defects.md` KD-78 末块 |
| KD-76 | 新增批次 29 设备轨 Progress：隔离 12/13 红（worker warm-up 全灭）、Race 2/2 绿；KD-76 剩余缺口不随本批消失 | `docs/known-defects.md` KD-76 节内（T6 块之后） |
| 契约 | §5.1 行与归位段的两处"设备证据未出数/实测不存在"刷新为本索引结论 | `docs/current/visual-generation-contract.md:123`、`:127` |
| 准入清单 | 新增附录 G，取代 S-04/S-05/S-06 行与附录 F 的"设备证据未出数"表述 | `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` 附录 G |
