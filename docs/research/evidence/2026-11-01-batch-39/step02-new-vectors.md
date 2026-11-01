# E-02 步骤二·新设备例（V-01④ / N 向量 / X-02 / X-03 / A-10 协调器级）

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-11-01-batch-39 |
| step | 步骤二·新设备例（判据 §一/§三/§四 落码批已交付的设备向量首跑） |
| gate-or-kd | D-04 预览行+16MiB 门（判据组 A/B）、X-02 源证据门、X-03 ①档、A-10 协调器级跨 method、V-01④ PDF 腿 |
| verdict | **PARTIAL**：V-01④ PASS；N 向量 4/4 PASS；X-02 6/6 PASS；A-10 协调器级 1/1 PASS；X-03 0/1 **FAIL（A 类夹具缺陷，源码归因，待落码批修复复跑）** |
| executed-at | 2026-11-01T10:24–10:26+08:00（五套件首跑窗口） |
| operator | 设备验证轨（批次 39 工作流子代理） |
| head | `a0641bb7392f380b5716afe95dd3c371dd0c7670` |
| worktree | 737 条（仅记录） |
| device-serial / avd / api / abi / page-size | emulator-5562 / test_device / 34 / x86_64 / 4096 |
| apk | app androidTest（④ 腿）+ data androidTest（N/X-02/X-03/A-10） |
| apk-sha256 | app 测试包 `e20d923a…b17d`；data 测试包 `5b8ba39c…53c3`（E-01 §4） |
| exit-code | 各套件 adb 层 0 |
| raw-log | `build/device-track-batch39/suite-{v01app,nvec,x02,x03,a10x}.log` + 同名 `-logcat.txt` |
| record-ref | `docs/research/2026-11-01-batch-39-record.md`（记录员落笔位） |
| sanitized | 是（见 00-index §6 统一扫描） |

## 1. 目的与判据

按判据 §一（D-04 预览行落库 + N5/N6/N7）、§三（X-02 五向量 + X-03 ①档）、§四（V-01④ 条件腿）、§1.B.7（A-10 协调器级）执行落码批交付的设备向量。判据 = 各套件 `OK (N)` 与构建前 `@Test` 计数逐一相符、断言面不缩水、零跳过、签名 0；红例按归因序处置。

## 2. 命令（可复跑，五套件同式）

```bash
export MSYS_NO_PATHCONV=1; A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r -e class <FQCN> <PKG>/androidx.test.runner.AndroidJUnitRunner
$A -s $S shell logcat -v threadtime -d
# v01app → com.tingyun.smartmistakebook.offline.test / visualjs.V01VisualChainInstrumentedTest
# nvec/x02/x03/a10x → com.tingyun.smartmistakebook.core.data.test / core.data.visual.<套件名>
```

## 3. 结果摘要

| 套件（slug） | 结果 | 判据对照 |
|---|---|---|
| `V01VisualChainInstrumentedTest`（v01app） | Tests run: 4, Failures: 1（红=③ `theFrozenDrawCommandsCommitAndSurviveARealReopenRenderedByTheIsolatedEngine`，**D 环境归因见 E-06 复跑**） | ④ 腿 `theRestoredChainWritesAPdfThroughTheTestSeamAndKeepsThePreviewReference` **首轮即绿**（INSTRUMENTATION_STATUS_CODE: 0 实读）；①②两例绿 |
| `TutorVisualPreviewRendererInstrumentedTest`（nvec） | **OK (4 tests)** | N5/N6/N7+取消重抛与判据 §3.1–3.3 一一对应；`@Test` 计数 4 相符 |
| `VisualSourceEvidenceDeviceVectorInstrumentedTest`（x02） | **OK (6 tests)** | 缺源/错图/SHA 变化/授权不足/预算不足 + 正向 witness 六位与判据 §三 X-02 对应 |
| `StrictOfflineRelayoutDeviceVectorInstrumentedTest`（x03） | Tests run: 1, Failures: 1（**A 类夹具缺陷**，§5） | ①档判定/0 派发三断言位未及执行——红在夹具前置断言 |
| `FigureCrossMethodFinalFigureDeviceVectorInstrumentedTest`（a10x） | **OK (1 test)** | `aThirdFinalFigureAcrossJsAndImageMethodsIsRejectedByTheCoordinatorOverTheRealLedger`——A-10 协调器级"同 operationId 先 JS 后 IMAGE 第 3 份拒"设备位 |

崩溃签名（8 类 × 5 窗）全 0（E-07 表）。

## 4. V-01④ 腿断言位实录（源码实读 + 首轮绿）

判据 §四要求的三约束均成立：断言只用缝公共返回值 + `PdfRenderer` 公共 API + 像素数组（`V01VisualChainInstrumentedTest.kt:420-484`：restoreDecision ②档 Replay/③档 ReadOnly + previewAssetId 透出 + PDF 单页/页宽高/圆心墨迹存在性）；缝 KDoc grep 门 = `tools/ci/check_visual_test_seams.py` 在树（未跟踪新文件）。D-04 预览行落库断言位（判据组 A①③）：previewAssetId 穿越真实重开同值（`:383-385`）+ `source_type=LOCAL_VISUAL_PREVIEW` 共享常量断言（`:386-393`）。

## 5. X-03 红=**A 类夹具缺陷**（源码归因，非环境非产品）

- 失败原文：`ComparisonFailure: 持久 program 行的 id 与快照引用一致 expected:<visual:operation-x03-relayout-chain:818cfa45f2e123bc> but was:<visual-program-2760ca529e995b28a7f947774fb55ebb91d1245c9e14543d4eb99a93c2022b46>`（`suite-x03.log` stack= 块；断言位 `StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt:219-223`）。
- 归因 **A（夹具）**：断言把两个**独立定义的 id 空间**划等号——
  ① 协调器 `FigureArtifact.Program.programId` 的派生式 = `visual:{operationId}:{sha256 前 16 位}`（同树 V-01 套件 KDoc `V01VisualChainInstrumentedTest.kt:208` 同式记载）；
  ② 仓储落库 id = `TutorVisualCommitRepository.programIdFor(TutorVisualProgramInput)` = `TutorVisualIdentity.deriveProgramId(源码内容六字段派生)`（`TutorVisualCommitRepository.kt:222-229`，KDoc"对外 visualId 与落库 program_id 的同一个值"），格式即实得的 `visual-program-<64hex>`。
  夹具向 `commitAssistantTurnWithVisual` 传入的 `TutorVisualProgramInput` **不含任何 id 字段**（`StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt:165-173`），结构上落库 id 只能是内容派生形——期望值不可能成立。
- 非 C（产品回归）：行经由 `readTutorVisualProgram(snapshotRow.programId)` 取得即已证明"快照引用与持久行 id 一致"（`requireNotNull` 通过）；V-01 ③ 同仓储路径首轮绿。非 D：失败消息含确定性哈希，同树两次同形（复跑登记 E-06）。非 B：不触引擎/计时面。
- **处置**：X-03 判据语义（①档 RelayoutAllowed + 0 远程派发三断言位）未获设备证据；修复归落码批（期望值改锚仓储派生 id 或删该跨空间断言），按"新增用例必须重跑"纪律复跑；本轨不改测试。

## 6. 脱敏与去噪

- 本文件不含密钥/凭据/邮箱/物理序列号/用户目录。
- 原始全量在 `build/device-track-batch39/`，本文件为关闭判据载体。
