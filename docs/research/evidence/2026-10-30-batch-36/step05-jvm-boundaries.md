# E-05 A-10/A-11/A-12 JVM 边界与 golden（构建报告读值）

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-30-batch-36 |
| step | JVM 边界（非设备） |
| gate-or-kd | 计划任务 3（A-10/A-11/A-12）JVM 半边 |
| verdict | PASS |
| executed-at | 2026-10-30T07:56–07:59+08:00（测试报告 mtime；**执行者为批次构建轮，本会话未重跑 Gradle**） |
| operator | 证据落盘员（只读摘录报告） |
| head | `4f11cd5432e6c259038fc92bd2e975aee8d304b4` |
| worktree | 706 项（仅记录） |
| device-serial | —（JVM 测试，无设备） |
| device-avd | — |
| device-api | — |
| device-abi | — |
| device-page-size | — |
| apk | —（JVM 测试报告，非 APK） |
| apk-sha256 | — |
| apk-version | — |
| exit-code | 未固化（报告 XML 的 `failures="0"` 为准；Gradle 退出码本会话未取） |
| raw-log | `core/model/build/test-results/test/TEST-*.xml`、`core/data/build/test-results/testDebugUnitTest/TEST-*.xml`（本机，不入仓） |
| record-ref | `docs/research/2026-10-30-batch-36-record.md`（未落盘） |
| sanitized | 是（本批 `00-index.md` §7） |

## 1. 目的与判据

以 JVM 用例覆盖 A-10 的逐阈值边界、A-11 的三协议 × 向量 sourceJs 保真、A-12 的 golden 扩测（新增可空上下文不出现新键、逐字节稳定）。判据：计划 `.zcode/plans/plan-2026-10-30-batch-36-v01-r04-k6.md:21`；准入清单 A-10/A-11/A-12 行义。

## 2. 命令（可复跑）

```bash
./gradlew :core:model:testDebugUnitTest --tests '*ModelTaskVisualContextGoldenTest' \
  --tests '*ModelTaskFingerprintStabilityTest'
./gradlew :core:data:testDebugUnitTest --tests '*FigureGenerationLocalBudgetBoundaryTest' \
  --tests '*ModelWireProtocolSourceJsFidelityTest'
```

- 工作目录：仓库根
- 本会话**未运行**上述 Gradle 命令；下表数字读自批次构建轮留下的 JUnit XML（R4：报告是本机材料，判定由本文件承载）。

## 3. 结果摘要

| 套件 | 报告文件 | tests | failures | skipped | 判定 |
|---|---|---:|---:|---:|---|
| `core.data.visual.FigureGenerationLocalBudgetBoundaryTest`（A-10 JVM） | `core/data/build/test-results/testDebugUnitTest/TEST-…FigureGenerationLocalBudgetBoundaryTest.xml` | 5 | 0 | 0 | ✓ |
| `core.data.model.wire.ModelWireProtocolSourceJsFidelityTest`（A-11） | `core/data/build/test-results/testDebugUnitTest/TEST-…ModelWireProtocolSourceJsFidelityTest.xml` | 15 | 0 | 0 | ✓ |
| `core.model.ModelTaskVisualContextGoldenTest`（A-12 扩测） | `core/model/build/test-results/test/TEST-…ModelTaskVisualContextGoldenTest.xml` | 7 | 0 | 0 | ✓ |
| `core.model.ModelTaskFingerprintStabilityTest`（A-12 既有基线，未扩） | `core/model/build/test-results/test/TEST-…ModelTaskFingerprintStabilityTest.xml` | 42 | 0 | 0 | ✓（无回退） |

### 3.1 A-10（5 例，逐阈值）

```text
cumulativeJsSourcePastThe128KiBLineIsRejectedAndTheExactLineIsAdmitted          # 128 KiB 线：过线拒、恰在线收
aSeventhLocalJsLayoutAttemptIsRejectedBeforeReachingTheEngine                   # 第 7 次布局尝试拒
cumulativeJsWallClockIsRejectedOnceExactlyTenSecondsHaveAccumulated            # 累计墙钟 10 s 拒
aThirdFinalFigureIsRejectedBeforeTheEngineRunsForTheSameOperation              # 第 3 份最终图拒（引擎前）
aThirdFinalFigureIsRejectedBeforeTheBackendOpensForTheSameOperation            # 第 3 份最终图拒（后端前）
```

### 3.2 A-11（15 例 = 3 协议 × 5 例）

`anthropic*` / `gemini*` / `responses*` 三组各含：`ProgramCarrierSurvivesTheTextVectorsByteForByte`、`EmptyProgramContentIsRefusedByTheStrictSchemaAndNeverBecomesAProgram`、`CutStreamWithoutATerminalNeverParsesIntoAnExecutableProgram`、`TruncatedProgramStreamIsExplicitlyIncompleteAndNeverParses`、`EmptyContentDeltasDoNotCorruptTheReconstructedProgram`。测试源：`core/data/src/test/kotlin/…/model/wire/ModelWireProtocolSourceJsFidelityTest.kt`（新落，`git status` 为 `??`）。

### 3.3 A-12（7 例，扩测）

```text
theRespondOperationProjectionDropsTheNullContextKeyByteForByte
thePlanOperationProjectionDropsTheNullContextKeyByteForByte
aNonNullContextLandsItsKeyWithTheExactDeclaredShape
anInnerNullContextFieldKeepsItsExactShapeAndBothFingerprintsStable
aRealVisualContextChangesBothFingerprintsAgainstTheNullGolden
aSchema17RespondRowReencodesByteForByteAcrossTheContextFieldUpgrade
aSchema17PlanRowReencodesByteForByteAcrossTheContextFieldUpgrade
```

## 4. 关键输出（有界）

```text
<testsuite name="…FigureGenerationLocalBudgetBoundaryTest" tests="5" skipped="0" failures="0" errors="0" time="0.01">
<testsuite name="…ModelWireProtocolSourceJsFidelityTest" tests="15" skipped="0" failures="0" errors="0" time="0.006">
<testsuite name="…ModelTaskVisualContextGoldenTest" tests="7" skipped="0" failures="0" errors="0" time="0.008">
<testsuite name="…ModelTaskFingerprintStabilityTest" tests="42" skipped="0" failures="0" errors="0" time="0.059">
```

## 5. 失败 / 跳过 / 异常

无失败、无跳过（四份报告 `skipped="0"`）。口径限制：报告 XML 能证明"这些测试在报告生成时跑了并全过"，不能证明"报告对应现树"——但 E-00 §3 的 1548 文件 sha 全等把现树与设备轨快照绑定；JVM 报告生成于 07:56–07:59，早于设备快照（08:53）且其间源未变（`r04-src-sha-diff.txt` 空、现树全等）。A-11/A-12/A-10 的**设备侧**证据分别为无/E-03/E-02。

## 6. 脱敏与去噪

- 删除：XML 的 `system-out`、`properties` 块未转录。
- 保留：`tests/failures/skipped` 计数、用例方法名。
- 扫描：见 `00-index.md` §7；二进制/截图：无。

## 7. 判定

PASS：四套件 5/15/7/42 全过、0 失败 0 跳过；A-10 的 128 KiB 线、10 s 墙钟、第 7 次布局、第 3 份最终图四类阈值有 JVM 证据；A-11 三协议 × 5 例齐；A-12 新增可空上下文 golden 7 例齐且既有 42 例无回退。未覆盖：这些是 JVM 门，不等于设备/产品门；A-10 的"耗尽→文字降级"与设备真库终答位见 E-03（未证明）。
