# E-02 SceneV1 修复后复跑：编译器套件 6（设备首跑，先红后绿）+ 引擎套件 26

| 字段 | 值 |
|---|---|
| evidence-id | E-02 |
| convention-version | 1 |
| batch | 2026-10-28-batch-28 |
| step | 一（SceneV1 复跑·core:data 侧）+ 二（T1 判据 3/4 核销） |
| gate-or-kd | SceneV1 设备门（编译器套件设备首跑即判据）+ 引擎 26 例修复转绿 |
| verdict | **PASS（含一次先红后绿测试面更正）**：编译器 `OK (6 tests)`（首跑 5/6，1 红归因=测试期望与契约/编译器/Node 台三处权威矛盾，更正后全绿）；引擎 `OK (26 tests)`（批次 27 9 红→全绿）；签名 18 项全 0 |
| executed-at | 2026-10-28T07:28:00+08:00 ～ 07:40:00+08:00 |
| operator | 设备验证轨（subagent，批次 28） |
| head | 8116ad98（两处测试面更正后重建重装：core:data androidTest v2 `855a25a8…ec344b` → v3 `a24d2c85…fd79857`，设备侧 sha256 实测一致） |
| worktree | dirty（本轨新增两处测试面更正，见 §5 牵动面） |
| device-serial | emulator-5554（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | core:data androidTest v1→v2→v3（身份链见 `step00` §3） |
| apk-version | 0.2.0-offline |
| exit-code | v1 红 EXIT=0（套件本身完成）；v2/v3 EXIT=0 |
| raw-log | `build/device-track-batch28/`：`step02-compiler.log`（红轮）、`step02-compiler-v2.log`（绿轮）、`step02-compiler-logcat.txt`（93 行）、`step02-engine.log`、`step02-engine-logcat.txt`（136 行）、`probe-path-alpha.mjs`、`probe-path-alpha.log` |
| record-ref | 批次 28 记录（记录员后写） |
| sanitized | 是（本文件 §6） |

## 1. 目的与判据

① 编译器套件 `QuickJsSceneCompilerInstrumentedTest`（6 例）**从未上过设备**（批次 27 记录 §6 #1），修复后首跑即判据：期望 `OK (6 tests)`，其中 `aMaxBudgetSceneCompilesWithinTheCooperativePhaseWindow` 须把 256 节点/4080 展开命令落进 100 ms 协作期（超限红 PHASE_TIMEOUT 时按 R1 §8 如实复判、禁放宽协作期）。② 引擎套件 26 例：批次 27 `Tests run: 26, Failures: 9`（同因接线缺陷），修复后期望全绿。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsSceneCompilerInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch28/step02-compiler-logcat.txt
# （v1 红 → 归因 → 测试面更正 → 重建 v2 重装 → 同命令复跑 = v2 绿轮）
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch28/step02-engine-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| 编译器 v1（首跑） | Tests run: 6, **Failures: 1**（`aFullSceneCompilesIntoTypedDrawOps`，:144 `expected:<1.0> but was:<0.5>`） | 触发先红后绿归因（§4/§5） |
| 编译器 v2（更正后） | **`OK (6 tests)`** / Time 0.554 | 期望 6 全绿 |
| 引擎套件 | **`OK (26 tests)`** / Time 8.034 | 期望 26 全绿（批次 27 9 红） |
| 9 类崩溃签名（三份 logcat） | compiler 9×0；engine 9×0 | 期望全 0 |
| 协作期门 | 套件总时长 0.554 s（256 节点用例在内） | 未触 PHASE_TIMEOUT，无需预算复判 |

## 4. 编译器红例归因（先红后绿，不改产品层）

红轮输出（`step02-compiler.log:55`）：

```text
INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: expected:<1.0> but was:<0.5>
	at com.tingyun.smartmistakebook.core.data.visual.QuickJsSceneCompilerInstrumentedTest$aFullSceneCompilesIntoTypedDrawOps$1.invokeSuspend(QuickJsSceneCompilerInstrumentedTest.kt:144)
```

断言对象：夹具 FULL_SCENE_SOURCE 中组 `opacity: 0.5` 下的 stroke-only dot path 节点，测试期望 `dotPath.alpha == 1.0`，设备实际 `0.5`。归因证据链（四源一致，全部本会话实读/实跑）：

1. 契约 `docs/current/visual-generation-contract.md:98`："**group alpha 乘到子节点**，不承诺 CSS 隔离透明组语义"；
2. 编译器实现 `core/data/src/main/resources/visual/visual-compiler.js`：`:860` 子树以 `opacity * groupOpacity` 合成下传，`:884` path op 与 `:937` label op 均发 `alpha: opacity`（合成值），KDoc `:29`"group alpha 乘入"；
3. Node 台 `tools/visual_compiler/run_tests.mjs:219`：同形状断言 `legal/full label alpha composed, label.alpha === 0.5`（Node 台只钉 label 面、未钉 path 面）；
4. V8 探针（`build/device-track-batch28/probe-path-alpha.mjs`，与仪器化夹具同文场景）：`q path alpha = 0.5` / `dot path alpha = 0.5` / `label alpha = 0.5` —— 与设备 QuickJS 输出一致，**跨引擎同值**，排除引擎差异读法。

→ **归因 = 批次 27 T1 新写测试的期望缺陷（:141-144 段），设备首曝**；非编译器缺陷、非环境、非 F1 接线修复面。修复 = 测试层把 `:144` 断言更正为契约值 `0.5`（delta 仍 0.0，非放宽；且补上 Node 台未覆盖的 path 面合成断言），并注四源出处。产品源零改动。

**本轨源码更正登记（整文件纳入）**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsSceneCompilerInstrumentedTest.kt` —— 仅 `aFullSceneCompilesIntoTypedDrawOps` 内 dot alpha 断言 `1.0→0.5` + 归因注释；其余 5 例与文件其余部分零改动。

## 5. 牵动面与重建

- 更正仅触测试文件一断言：产品源、编译器资产、契约、Node 台、painter 均未动（`git status` 复核：本批 M 文件新增仅上述两个 androidTest 文件，见 E-03 §5）。
- 重建 `:core:data:assembleDebugAndroidTest` v2 EXIT=0 → 重装 Success → 设备侧 sha256 `855a25a8…` 与产物一致 → 同命令复跑：`OK (6 tests)`。

## 6. 引擎套件（26 例全绿）

`step02-engine.log` 收尾：`Time: 8.034` / `OK (26 tests)` / `INSTRUMENTATION_CODE: -1`。批次 27 的 9 红例（`aSecondTransactionCannotObserveGlobalsDeclaredByTheFirst`、`rebindingObjectFreezeCannotDefeatTheDeepFreeze`、`bootstrapClosesDateAndReplaysMathRandomDeterministicallyFromTheSeed`、`aConcurrentExecutionIsRejectedAsBusy`、`aProgramRendersWithFrozenFrameInputAndFrozenContentReferences`、`mutatingFrozenMetricsSilentlyFailsAndContentIdGateStaysGreen`、`mutatingFrozenFrameInputSilentlyFailsAndReadsBackOriginalValues`、`frozenInputCoexistsWithSeededPrngAndClosedDate`、`theHostAbandoningAnInFlightTransactionFreesTheSingleFlightSlot`）全部转绿；17 绿例保持绿。logcat 9 签名全 0（引擎套件为进程内 QuickJS，无隔离 worker spawn，`Start proc.*visual_js` 0 行符合预期）。

## 7. 脱敏与去噪

- 删除：无。
- 保留：红轮 stack 全文（项目帧）、归因四源行号、签名计数（含 0）。
- 扫描：见 `00-index.md` §6。二进制/截图：无。

## 8. 判定

**PASS**：编译器 6/6（含 256 节点 100 ms 协作期门，无需预算复判）、引擎 26/26；一次先红后绿闭环完成且归因到测试层（四源证据）。T1 判据 3/4 核销。批次 27 记录"编译器套件从未上过设备"与"引擎 9 红"两个 UNVERIFIED/红 面自此解除。

## 9. 原始全量（不入仓）

- `build/device-track-batch28/step02-compiler.log`（156 行，红轮）/ `step02-compiler-v2.log` / `step02-compiler-logcat.txt`（93 行）/ `step02-engine.log` / `step02-engine-logcat.txt`（136 行）/ `probe-path-alpha.mjs` + `probe-path-alpha.log` / `build-data-test-v2.log`。
- `build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
