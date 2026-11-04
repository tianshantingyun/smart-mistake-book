# E-43-DEV-01：设备轨（WS1/WS2/WS3/WS4 用例实跑）——APK sha、源快照、逐轮 boot_id、witness

| 字段 | 值 |
|---|---|
| evidence-id | `E-43-DEV-01` |
| 计划 | `.zcode/plans/plan-2026-11-05-batch-43.md` §2 WS1–WS4（用例实跑）、§4 硬约束 |
| executed-at | 2026-11-04T00:44Z 起（本机 UTC；文件名按计划批次日 2026-11-05） |
| head | `0fef943641f53560b92ce2afec4da22bf52f5e86`（`git rev-parse HEAD`，本轮实跑） |
| 工作树 | 构建时点 578 脏项（124 `M` / 454 `??`）；收尾 598（143 `M` / 455 `??`）——增量来自他线并发编辑与本文件，**本轨未改任何源码/测试** |
| 构建 | `./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest :core:export:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest :core:database:assembleDebugAndroidTest --console=plain` → **BUILD SUCCESSFUL in 19s / 358 actionable tasks: 47 executed, 311 up-to-date**（`build/wsdevice-batch43/build-apk.log`） |
| 设备 | 5584（API34/x86_64/4 vCPU/PAGE 4096）、5566（API34/2 vCPU）、5570（API23/x86_64）；5562 在 A6 首轮后 system_server 死亡，弃用 |
| 执行者 | 设备轨子代理；共享工作树；**本文件是唯一写入的文档** |
| 未执行 | 任何源码/测试改动、任何 git 写、任何 Provider 调用、`:app:connectedLocalFirstDebugAndroidTest`（禁用） |

> 口径：**实跑** = 本轮执行并读退出码/输出；**实读** = 本轮读源码（给行号）；**转录** = 引既有证据。
> 设备结论只绑定下表 APK sha；证据原始产物在 `build/wsdevice-batch43/`（gitignored）。

---

## 0. 产物身份（本轮实测，host 与设备双向核对）

| 产物 | sha256 | host 文件 | 设备核对 |
|---|---|---|---|
| app `…localfirst` | `9b832f9673179f4077a5d6559b1e84d5852df0ff82f08f2961002e5bd7e29e16` | 183,659,071 B | 5584/5562/5566 字节一致（`pm path` + `sha256sum`）；5570 安装成功但该机无 `sha256sum`，未做字节核对 |
| app.test | `00bae34128b27a025a10c65ce4f5508525a8b35aea4053c191f681b105511a9c` | 1,473,420 B | 5584/5562/5566 一致 |
| export.test | `08ea26417f746daddf0344c8b12433702628e25670f7ca80283b2e2374e165c0` | 4,960,255 B | 5584/5562 一致（5566 上为旧包，未用于本轨） |
| data.test | `318a13ef95bdf583a966f2fcb422d62f2c320b15c7028b37bfd04e06d9d3eea9` | 194,657,264 B | 5584 一致 |
| database.test | `cdeb8c7be0b3d6f31146749e8074bddd448bf85a239b25a3503e74e09809d6fd` | 193,396,947 B | 5584 字节一致；5570 安装成功（该机无 `sha256sum`，未做字节核对） |

- host 摘要：`build/wsdevice-batch43/apk-sha-host.txt`；设备摘要：`build/wsdevice-batch43/apk-sha-device.txt`。
- 源快照：`build/wsdevice-batch43/source-snapshot.txt`（HEAD、脏项计数、7 个关键源文件 sha256）。
- 设备代际（5584）：`deviceGenerationId=5342940bb87fcbbc7a28380157f55c85afa4029bd5df5fbae0302767c44a423c`（`build/wsdevice-batch43/kd76-a6-5584/identity-precheck.json`）。

---

## 1. WS1：KD-75 C2/C4 设备用例

### 1.1 5584 首跑（`build/wsdevice-batch43/ws1/`）

```bash
adb -s emulator-5584 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.export.MistakePdfExporterAigcInstrumentedTest \
  com.tingyun.smartmistakebook.core.export.test/androidx.test.runner.AndroidJUnitRunner
```

- 结果：`OK (7 tests)`，`Time: 1.055`，`INSTRUMENTATION_CODE: -1`，`EXIT=0`（`ws1/exportaigc.am.txt`）。
- `boot_before == boot_after == 4d9a0729-c675-4a1f-91b8-9b2d3d709451`（`ws1/meta.txt`）。
- 7 例（`INSTRUMENTATION_STATUS: test=` 实读）：`realExportWithAiImageIsLabeled…`、`pureUserContentExportHasNoAigcInfoEntry`、`oldUnmarkedArtifactUnderTheSameKeyIsRejectedAndRebuiltWithAigc`、`secondPrepareReusesTheSameLabeledArtifact`、`modelParsedTextArtifactUnderTheOldKeyIsRejectedAndRebuiltWithAigc`、`wholePrintKeepsAigcWhilePartialPrintDropsTheDocumentLevelEntry`、`modelParsedTextOnlyExportIsLabeled`。

### 1.2 5570 复跑 + 宿主机 pypdf 独立读回（WS1 item 6/7）

同一测试包在 API 23 设备复跑：`OK (7 tests)`，`EXIT=0`，`boot_id=3d3900aa-7e01-4582-9416-be4d78f704a3`（`ws1/exportaigc-5570.am.txt`）。

从设备 `run-as` 拉回两件产物，**宿主用 pypdf 6.16.2 独立读**（`ws1/pdfs/*-5570.pdf`）：

```text
model-text-labeled-5570.pdf  pages 1  bytes 2514765  sha256 88f661a6bfe89e60…
  meta {'/AIGC': '{"Label":"1","ContentProducer":"SmartMistakeBook","ProduceID":"c52d2907…","ReservedCode1":"","ContentPropagator":"","PropagateID":"","ReservedCode2":""}'}
  contains_AIGC_bytes True
pure-user-unlabeled-5570.pdf  pages 1  bytes 2492555  sha256 1d510294582bb0dc…
  meta {}
  contains_AIGC_bytes False
```

- 正向（item 6）：**无任何 AI 图、只有模型解析文本**的导出带 `/AIGC`，`Label=1`。用例先断言 `input.paintsAiGeneratedImage()==false`（`MistakePdfExporterAigcInstrumentedTest.kt:146`），判据为 `PdfAigcContentPolicy.shouldLabel(hasAiGeneratedImage, hasModelParsedText)`（`PdfAigcContentPolicy.kt:47`）。
- 负向（item 7）：纯用户内容 `input.hasModelParsedText==false`、产物无 `/AIGC`（`:131-132`）。
- 旧缓存不回退（item 8）：`oldUnmarkedArtifactUnderTheSameKeyIsRejectedAndRebuiltWithAigc` 与 `modelParsedTextArtifactUnderTheOldKeyIsRejectedAndRebuiltWithAigc` 均绿——删除哨兵后同 key 旧产物被拒并重建（`:193-244`）。
- 限定：C4 的“字面版本升版”在本轨只由上述设备用例与 `PdfAigcContentPolicy.GENERATION == 2`（`:29`）旁证；JVM 三方一致用例未由本轨运行（非设备轨）。

---

## 2. WS2：KD-76 A6 两批独立 20 轮

### 2.1 5562 尝试：设备 system_server 死亡（基础设施失败，原样保留）

- 预注册 plan `build/wsdevice-batch43/kd76-a6/a6-plan.txt`，sha256 `a6399ca2…`；批 A round 1–3 全部 `G1 red: … connectForTest() == false`（`kd76-a6/batchA/round-01..03.am.txt`）。
- 随后 `adb -s emulator-5562 shell "service check activity"` → **`Service activity: not found`**；`am instrument` 报 `Can't find service: activity`（`build/wsdevice-batch43/5562-system-server-failure.txt`）。
- 处置：停止该轮，改绑 5584；5562 上**不得**产生任何产品结论。

### 2.2 5584 重跑：批 A 结果（`build/wsdevice-batch43/kd76-a6-5584/`）

- 预注册 plan sha256 `0d2f023e775ca60c20ff9e4d6a96204d6be064a0038bcef6885670295c21018a`；`minSeparationMinutes=30`；`mode=reboot`、`settle=0`、`rounds=20`。
- 已安装身份：app `9b832f96…`、test `00bae341…`（`identity-precheck.json`）。
- **批 A：10/20 轮通过，10 轮失败**（`driver.log`）：

```text
round 1 OK 63s   round 2 FAIL   round 3 FAIL   round 4 OK 37s   round 5 OK 36s
round 6 OK       round 7 OK     round 8 FAIL   round 9 OK 53s   round 10 OK
round 11 FAIL    round 12 FAIL  round 13 FAIL  round 14 FAIL    round 15 FAIL
round 16 OK 52s  round 17 FAIL  round 18 FAIL  round 19 OK      round 20 OK 46s
```

- 失败原因（10 轮全部同型，逐轮 `round-NN.am.txt` 实读）：`Rejected/HARD_DEADLINE_EXCEEDED: visual_js worker died: binder died instead of a rendered result` → 删失 ≥2500 ms，不是缺失值。
- 逐轮宿主 `boot_id`（`batchA/boot-ids.txt`，20 个两两不同；bootCount 105..124 严格递增）：

```text
d16cfb3e-809c-4da5-bd98-3b32f7d58942  9e345ed3-dc26-43e5-8f99-578d92047151
36c813fa-31c4-4f84-b52f-d82bad5bd633  9f2a1974-0031-4a77-bf21-ad2b49ed5e46
3d419661-f0a2-4f99-a428-6dc7ead39969  b976899b-a273-4e81-aedb-4c86536dbb72
ee7d5a3f-9d1f-446c-a9ab-aa9342d11e12  38fb031f-9d9c-4c08-8e60-d3cacf316de8
02a9c4c1-c121-4f71-9f42-9655f011421d  b70a56df-1716-4be9-a410-9fb521098b89
4e9165a1-b23f-4692-884a-b1676e98fa02  5aeb16f2-0502-452c-9387-0ecdf21d1506
6269291f-cb85-498d-8e81-e95dfd06be3e  13b105cd-d8c6-420a-bb12-5cc43ca34ad8
257f5298-7d00-4e49-8c2a-fac104bf20ee  02f89e1f-d326-4448-98e0-2a139c50e6fa
3a8b9fa1-c593-43dd-a409-7c33bb2416d2  f6cdc190-1a4e-4b34-9fef-2b9198f840dc
f0faccf5-3f23-4ab5-9c72-c826bbe4970b  5c235d0f-d8b8-4eff-9157-de1084f39f73
```

- `python -I tools/ci/summarize_kd76.py build/wsdevice-batch43/kd76-a6-5584/batchA` → **exit 2**：`samples: n=10/20 | G1: RED`；`G2: NOT EVALUABLE`。
- **证据缺陷（本轨实读，两批 40 轮全中）**：每轮 XML 都报 `round-NN has 2 KD76_SAMPLE lines`。根因在测试的 `emit()`：同一行同时 `Log.i(TAG, …)` 与 `println(…)`（`VisualJsConnectStageCalibrationInstrumentedTest.kt:866-869`），logcat 得到 `I/Kd76Calibration` 与 `I/System.out` 两条同内容行；`summarize_kd76.py` 把它判为“每轮多条终态样本”。即使 n=20，当前构建也无法出 p95。KD-76 不能据此关闭；同时这是 `summarize_kd76` 边界（计划 WS7 item 55）的实测输入。

### 2.3 批 B（同构建、同安装、30.2 min 分离后）

- 批 B 开跑前脚本重取身份，4 个字段逐项相等（`identity.json` vs `identity-b.json`）：app `9b832f96…`、test `00bae341…`、`deviceGenerationId=5342940b…`、`installGenerationId=22bd968f…`。批 B 未重装。
- **批 B：7/20 轮通过，13 轮失败**；逐轮序列：

```text
1 OK  2 OK  3 OK  4 FAIL  5 FAIL  6 FAIL  7 OK  8 FAIL  9 FAIL  10 FAIL
11 OK 12 FAIL 13 FAIL 14 OK 15 FAIL 16 FAIL 17 FAIL 18 FAIL 19 FAIL 20 OK
```

- 失败同型：`Rejected/HARD_DEADLINE_EXCEEDED: visual_js worker died: binder died instead of a rendered result`。
- 逐轮 boot_id：20 个、两两不同，且与批 A **交集为 0**（`batchB/boot-ids.txt`）：

```text
8bf5b111-1593-4d84-848f-34f293f39c36  8dd6135d-66c3-4368-8502-172e10e07d56
7ff90e19-b915-4b18-b43f-5f734a394e55  68965616-f9f7-4100-85ac-16bc8b396fc7
a8e6abc2-7a66-4771-9542-05b67326ea65  d6211e68-d2ef-4ec7-99ba-62e0cddf901b
b35beda5-18a1-4ac1-bf73-894f1722d532  9043debb-7a6e-4a44-a486-44d878adbc51
59dd5613-a01b-4448-8995-d6461b9a857a  4421679c-d12c-4b36-80a7-666b9f5c2342
d23ec314-1b75-403e-94c3-38316c3421be  dcbc929e-6272-470d-8de8-ee439e239b1e
20a809c5-2130-4ce1-9b5b-827cea3ec562  fbbb43b7-162a-4b39-aeba-c77b452c982a
1101da3d-70a9-4b08-93ac-e19eb2ea8250  8e0582a7-cf7e-41a1-8f74-f241b07bd9b5
48fe10e1-729b-49d4-982e-fd76c578b298  2a0a9a73-df7b-4b5e-9033-f3e9db9332ce
7959b619-9b7d-4b28-9555-76c2cb42b588  5a34f877-606b-4e74-b7b8-84f6398faf9d
```

### 2.4 A6 判词（工具原文，`a6-report.txt`，exit 2）

```text
A6: NOT EVALUABLE
  batchA: n=10/20, G1 RED, M.p95=UNVERIFIED ms (gate 5000 ms, margin UNVERIFIED ms), max=8987 ms
  batchB: n=7/20,  G1 RED, M.p95=UNVERIFIED ms (gate 5000 ms, margin UNVERIFIED ms), max=8436 ms
  - failed_rounds: batchA failedRounds=10 (per-round product failure; censored sample, not a retry)
  - failed_rounds: batchB failedRounds=13 (per-round product failure; censored sample, not a retry)
  - …（每轮 2 条 KD76_SAMPLE 的重复行告警贯穿两批 40 轮）
  no A6 claim may be derived; per-batch numbers are not a substitute, and no batch PASS may be read as A6.
  observed gap: 30.2 min; plan sha256: 0d2f023e…
```

- 独立性可核项：`observed_gap_minutes=30.2 ≥ 30`；两批 `runId` 不同（`…-A-385222` / `…-B-385222`）；`started/finished` 无重叠；A/B boot_id 各 20 唯一且互斥；`p95_millis=null`（工具拒绝在 G1 红时出 p95，也拒绝合并 n）。
- **结论：A6 NOT EVALUABLE，KD-76 不能关闭。** 两批各自的 G1 都红，冷启动首个请求在本构建/本窗口的 40 轮里失败 23 轮（A 10 + B 13）。与批次 42 单批 1/20 的差异**只能作描述性观察**：构建不同（`ec135844…` vs `9b832f96…`）、宿主窗口不同，不能写成“回归”。

---

## 3. WS3：isolation 自证 witness 与 `-4` 根因

### 3.1 两次单臂实跑（5566，`build/wsdevice-batch43/ws3/`）

```bash
adb -s emulator-5566 shell am instrument -r -w --no-window-animation \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  -e smb.lowload.gate PASS -e smb.t4.arm OFF \
  -e smb.witness.run dev-b43-rN -e smb.witness.expected_tests 15 \
  -e smb.witness.apk_sha256 9b832f96… \
  com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
```

| run | instrument | witness 行 | 检查器（`--require-executed`） | 检查器（+`--require-floors`） | gate 行 | 设备 boot_id |
|---|---|---|---|---|---|---|
| r1 | `OK (15 tests)`，EXIT=0 | 78（77 + suite_end） | exit 0，`EXECUTED=15`，canary=true | **exit 1**：1 条 `cpu_delta_ms=0 < 1` | 15（reused 14/1） | `6def72cc-e33c-49ce-a724-b9804849b701`（跑前=跑后） |
| r2 | `OK (15 tests)`，EXIT=0 | 78 | exit 0，`EXECUTED=15`，canary=true | **exit 0** | 15（reused 14/1） | 同上（同 boot，无 reboot） |

- witness 阶段计数（两次一致，`runN.check.json`）：`suite_start=1, canary=1, rule_begin=15, gate=15, body_begin=15, body_end=15, rule_end=15, suite_end=1`。
- `floor_sum_ms=44450`，`total_body_wall_ms` r1=66381 / r2=65019；15 个方法名与 `tools/ci/isolation_witness_expectations.json` 的 15 条一一对应。
- **两轮对照（环境漂移层，item 23/25）**：执行集合完全相同（15/15），红集为空；唯一差异是 r1 的 `anUnattributedEngineFailureKeepsItsPrefixAcrossTheAidlWire` 的 `cpu_delta_ms=0`（10 ms 记账户粒度），r2 无此读数。**这解释为测量地板，不是用例失败**：两轮 `outcome=PASS/status=PASS`。
- 限定：`-e smb.lowload.gate PASS` 是**合成判词**（shell 侧未跑 `lowload_gate.py gate`）；本轨只用它证明“用例真的执行”，不把它写成低负载门通过。真实门地板在 5566 上 `la_ratio_max=0.475 > 0.30`（转录自 `build/ws3-batch43/floor-5566.json`），既有证据已判该设备无可判 PASS 的窗。

### 3.2 `-4` 零执行的根因（item 20，本轨实读）

WS4 全量套件以**裸 `am instrument`** 跑 isolation（无 gate 参数）时，15 例全部 `INSTRUMENTATION_STATUS_CODE: -4`（`ws4/suites/suite-isolation.log` 中计数 **15**），栈原文：

```text
org.junit.AssumptionViolatedException: SMB_LOWLOAD_GATE UNGATED: no_shell_side_verdict;run_tools/ci/run_isolation_pair.sh
  at com.tingyun.smartmistakebook.visualjs.IsolationGate.require(LowLoadProbe.kt:243)
```

即 `-4` 不是“夹具炸了”，而是 **fail-closed 门在缺 shell 侧判词时按设计 skip**（`LowLoadProbe.kt:234-244`：先写 gate witness，再 `Assume.assumeTrue`）。同一 APK 在给 `smb.lowload.gate PASS` 时 15/15 执行（§3.1），因此“15 例零执行”的归因是**调用形状**，不是用例或产品缺陷。跑全量套件时 isolation 的 `OK (15 tests)` **不得记为通过**。

### 3.3 未执行（原因）

- **OFF/ON 配对/T4 控制轮（item 23/24）**：`run_isolation_pair.sh` 物理拒绝同一 COMPONENT（`run_isolation_pair.sh` 头部 F8 门）；要得到两臂必须构建两份可区分测试包，T4-OFF 还需回退生产打点并重建 APK——本轮设备轨无权改源码，且 24 需身份可核的控制构建。既有 WS3 证据（`ws3-isolation-selfwitness.md` §0）有 T4-OFF 构建，但其 APK 与本节 APK 不同，**不能混用**。

---

## 4. WS4：设备复基线

### 4.1 34 套件（5584；`build/wsdevice-batch43/ws4/suites/`）

命令形状：逐套件 `adb logcat -c` → `am instrument -w -r -e class <FQCN>` → `logcat -d`，套件间不重装、不 reboot；`progress.txt` 记录每套件 adb-exit。窗口 08:54:59–09:09:53；设备代际未变，但 **09:07:54 另一进程重装了 `core.database.test`**（见 §4.2），污染了 matrix/dbcommit 两套件。

| slug | 声明 | 结果 | slug | 声明 | 结果 |
|---|---:|---|---|---:|---|
| v01app | 4 | OK(4) | backup | 34 | OK(34) |
| a09 | 3 | OK(3) | rowcounts | 2 | OK(2) |
| p09 | 1 | **Tests run 1, Failures 1** | trimmer | 10 | OK(10) |
| p10 | 1 | OK(1) | c4 | 8 | OK(8) |
| p0405 | 1 | OK(1) | v4pack | 3 | OK(3) |
| flags | 2 | OK(2) | selrestore | 11 | OK(11) |
| isolation | 15 | **15× -4（skip）** | selcreate | 3 | OK(3) |
| nvec | 4 | OK(4) | errorcause | 3 | OK(3) |
| x02 | 6 | OK(6) | pdfaigc | 10 | OK(10) |
| x03 | 1 | OK(1) | exportaigc | 7 | OK(7) |
| a10x | 1 | OK(1) | gates | 5 | OK(5) |
| repo | 12 | OK(12) | matrix | 16 | **Process crashed** |
| parity | 16 | OK(16) | m6970 | 4 | OK(4) |
| painter | 24 | OK(24) | m7071 | 3 | OK(3) |
| pdf | 16 | OK(16) | dbcommit | 12 | **Process crashed** |
| ordinal | 1 | OK(1) | engine | 27 | OK(27) |
| reconciler | 6 | OK(6) | a10 | 3 | OK(3) |

- 汇总：34 套件；**31 套 `OK`，累计 246 个 `OK` 计数**；其中 isolation 的 15 只是 skip，故**实际执行通过 231**。两处 `Process crashed`、一处 p09 红、一处 15×-4。
- 原始：`ws4/suites/suite-summary.json`、`progress.txt`、每套件 `suite-*.log` / `-logcat.txt`。

### 4.2 两处 `Process crashed` 的归因：并发重装（基础设施，非代码）

- 崩溃轮次：matrix 第 14 例 `everyExportedSchemaVersionMigratesToCurrentWithoutDestructiveFallback`；dbcommit 第 12 例 `sceneDigestThatDoesNotMatchItsContentIsRejectedBeforeAnyWrite`（日志末行 `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`）。
- 同刻 logcat（`suite-matrix-logcat.txt` 尾部）出现 `PACKAGE_REMOVED/ADDED/REPLACED`；设备 `dumpsys package com.tingyun.smartmistakebook.core.database.test` 的 `lastUpdateTime=2026-11-04 01:07:54`（设备时区）= 本机 09:07:54，正落在 matrix 运行窗内（09:01:07–09:09）。即另一进程在跑套件时 `install -r` 覆盖了 test 包，仪器进程被杀。
- 干净窗口复跑（5570，`build/wsdevice-batch43/ws4-rerun/`，boot `3d3900aa…`）：matrix **OK (16 tests)**、dbcommit **OK (12 tests)** → 崩溃不可复现，归因并发重装。
- 限定：5570 是 API 23，与 5584（API 34）不同代际；该复跑只证明这两个套件在当前字节上可绿，不替代 5584 的同代际复跑。

### 4.3 p09 定性（item 36）

- 5584 一次 + 5570 两次（共 3 次、2 台设备）**同一断言**失败：

```text
java.lang.AssertionError: the fourth distinct program must be rejected by the client queue,
got Rendered(sceneJson={…"sceneId":"scene-D"…})
  at VisualJsClientFrameQueueDeviceInstrumentedTest.assertRejected(…:235)
  at …:142
```

- 判定：**代码/用例期望不一致，不是环境抖动**（跨设备、跨 API、逐次同文）。该文件在本轮为未跟踪新文件（`app/src/androidTest/.../VisualJsClientFrameQueueDeviceInstrumentedTest.kt`，mtime 2026-11-04 05:24），晚于 WS4 既有 p09 结论的构建；本轨只定性，不修。

### 4.4 崩溃签名九类 + worker 计数（item 34/35）

- `python -I build/ws4-batch43/crash_scan.py build/wsdevice-batch43/ws4/suites`（34 份套件 logcat）：
  `FATAL EXCEPTION=0, ANR in=0, NoSuchMethodError=0, NoClassDefFoundError=0, UnsatisfiedLinkError=0, SIGSEGV=0, CANNOT LINK=0, failed to map segment=0`；第 9 类 `death was not confirmed=1`，出现在 **p10**（该套件正是“死亡未确认必须 fail-closed”的设计负向）。
  worker 计数：`spawn=10, warmup_finished=10, warmup_failed=0, kill_accepted=8, death_confirmed=1`（`ws4/crash-signatures.json`）。
- worker 生命周期/强停恢复（5570，`ws4-rerun/worker.txt`，boot `3d3900aa…`）：冷启 `TotalTime=1521`，launch 后 `:visual_js` 进程数 0；`am force-stop` 后主进程为空；重启 `TotalTime=882`，主进程回到前台；`run-as … sqlite3`：`PRAGMA quick_check=ok`、`user_version=73`、`sqlite_master` 表数 99（该机为复用库，未 `pm clear`，表数与他轨 92 不可直接比）。
- 设备内存/加载：5584 套件后/A6 前 loadavg 1.43/2.31/2.14（4 vCPU）；5570 0.80/0.62/0.35（API 23，无 `nproc`）。

---

## 5. 未执行/阻塞（如实）

1. **KD-76 item 13 产品进程首请求**：结构性 BLOCKED（`SmartMistakeBookApplication` 装配与 JS 广告面），转录 `ws2-kd76-a6.md` §7，不在设备轨权限内。
2. **WS3 OFF/ON 两臂与 T4 控制轮**：需两份可区分测试包 + 源码回退重建；设备轨不改源码（见 §3.3）。
3. **真实 Provider / arm64 / 签名密钥 / 人工金标**：外部资源，未执行（runbook 见 WS5/WS8 证据）。
4. **`:app:connectedLocalFirstDebugAndroidTest`**：纪律禁用，未运行；全部走 `am instrument`。

---

## 6. 复跑命令（逐字）

```bash
export PATH="$PATH:/c/Android/Sdk/platform-tools"
./gradlew :app:assembleLocalFirstDebug :app:assembleLocalFirstDebugAndroidTest \
  :core:export:assembleDebugAndroidTest :core:data:assembleDebugAndroidTest \
  :core:database:assembleDebugAndroidTest --console=plain

# WS1
adb -s emulator-5584 install -r -t core/export/build/outputs/apk/androidTest/debug/export-debug-androidTest.apk
adb -s emulator-5584 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.export.MistakePdfExporterAigcInstrumentedTest \
  com.tingyun.smartmistakebook.core.export.test/androidx.test.runner.AndroidJUnitRunner

# WS2（同一构建、30 min 分离、逐轮 reboot）
tools/ci/run_kd76_a6.sh --serial emulator-5584 --out-root build/wsdevice-batch43/kd76-a6-5584 \
  --app-package com.tingyun.smartmistakebook.localfirst --min-separation-minutes 30

# WS3（单臂 witness）
adb -s emulator-5566 shell am instrument -r -w --no-window-animation \
  -e class com.tingyun.smartmistakebook.visualjs.VisualJsIsolationInstrumentedTest \
  -e smb.lowload.gate PASS -e smb.t4.arm OFF -e smb.witness.run dev-b43-r2 \
  -e smb.witness.expected_tests 15 -e smb.witness.apk_sha256 9b832f96… \
  com.tingyun.smartmistakebook.localfirst.test/androidx.test.runner.AndroidJUnitRunner
python -I tools/ci/isolation_witness_check.py --witness build/wsdevice-batch43/ws3/run2.witness.jsonl \
  --stream build/wsdevice-batch43/ws3/run2.instrument.log --logcat build/wsdevice-batch43/ws3/run2.logcat \
  --require-executed --require-floors --out build/wsdevice-batch43/ws3/run2.check.floors.json

# WS4
bash build/wsdevice-batch43/ws4/run-suites.sh
python -I build/ws4-batch43/crash_scan.py build/wsdevice-batch43/ws4/suites
```

---

## 7. 原始产物与复算

| 产物 | 路径（gitignored） |
|---|---|
| 构建日志 / source 快照 / APK sha | `build/wsdevice-batch43/{build-apk.log,source-snapshot.txt,apk-sha-host.txt,apk-sha-device.txt}` |
| WS1 仪器日志与 pypdf 输入 | `build/wsdevice-batch43/ws1/` |
| WS2 A6（5584） | `build/wsdevice-batch43/kd76-a6-5584/{driver.log,a6-report.txt,a6-report.json,identity.json,identity-b.json,batchA,batchB}` |
| WS2 5562 失败现场 | `build/wsdevice-batch43/{kd76-a6,5562-system-server-failure.txt}` |
| WS3 witness | `build/wsdevice-batch43/ws3/{run1,run2}.{instrument.log,logcat,witness.jsonl,check*.json}` |
| WS4 34 套件 | `build/wsdevice-batch43/ws4/{suites,suite-summary.json,crash-signatures.json}` |
| WS4 干净窗口复跑 | `build/wsdevice-batch43/ws4-rerun/` |

复算 A6 判词（不改证据）：

```bash
python -I tools/ci/summarize_kd76.py build/wsdevice-batch43/kd76-a6-5584/batchA
python -I tools/ci/summarize_kd76.py build/wsdevice-batch43/kd76-a6-5584/batchB
python -I tools/ci/kd76_a6_verify.py --plan build/wsdevice-batch43/kd76-a6-5584/a6-plan.txt \
  --batch-a build/wsdevice-batch43/kd76-a6-5584/batchA \
  --batch-b build/wsdevice-batch43/kd76-a6-5584/batchB --json
# 期望：exit 2，A6: NOT EVALUABLE
```
