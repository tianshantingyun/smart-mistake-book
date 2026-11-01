# E-03 T2 解析器 / T5 语义设备面（编译器 6/6 PASS；flags 跨侧 1/2 FAIL；T2 无设备面）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-28-batch-29 |
| step | 三（T2 解析器与 T5 语义用例，JVM 已跑则只核设备面） |
| gate-or-kd | T2 S-04 解析器 / T5 KD-78 ①–④ 设备面 |
| verdict | **编译器 PASS**（`OK (6 tests)`）；**flags 跨侧 FAIL**（`Tests run: 2, Failures: 1`，同 R1）；T2 解析器**无设备面**（0 个 androidTest 引用），JVM 面不在本轨 |
| executed-at | 2026-10-28T22:35:00+08:00 前后（设备 UTC 14:35） |
| operator | 设备验证轨（subagent，批次 29） |
| head | `3d9372e5` |
| worktree | dirty（`VisualDrawOpsParser.kt` 为 ??；`VisualContentMeasuring.kt` M） |
| device-serial | emulator-5562（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | core:data `4d152b5a…fa72cd81`；app `05a5b8bd…cd24d7` / app androidTest `229cd58e…6edd68` |
| apk-version | 0.2.0-offline |
| exit-code | 两条命令宿主 `EXIT=0`（非判据） |
| raw-log | `build/device-track-batch29/step03-compiler.log`/`-logcat.txt`、`step03-flags.log`/`-logcat.txt` |
| record-ref | 批次 29 记录（后写） |
| sanitized | 是 |

## 1. T2 解析器设备面：不存在

- 只读核对：`grep -rln "VisualDrawOpsParser" core/data/src/androidTest app/src/androidTest` → **0 文件**。
- 解析器用例全在 JVM（`core/data/src/test/.../visual/VisualDrawOpsParserTest.kt`）。本轨纪律只允许
  `assemble*`，不跑 `gradle test`；JVM 门由脚本执行（批次 29 单测门第 2 轮 exit=0，见工作流状态）。
- **本轨未重跑 T2 JVM 用例**，不为其结论背书；设备面无可执行面，故本步不产生 T2 设备证据。

## 2. T5 语义设备面命令

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
# 编译器 6 例（core:data）
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.QuickJsSceneCompilerInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch29/step03-compiler.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch29/step03-compiler-logcat.txt
# 度量/回放 flags 跨侧 2 例（app）
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest \
  com.tingyun.smartmistakebook.offline.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch29/step03-flags.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch29/step03-flags-logcat.txt
```

## 3. 结果

### 3.1 编译器（core:data）

```text
Time: 0.454
OK (6 tests)
INSTRUMENTATION_CODE: -1
```

6 例：`aFullSceneCompilesIntoTypedDrawOps`、`theSameSceneCompilesToByteIdenticalDrawOps`、
`anIllegalSceneShapeIsRejectedAsInvalidSceneWithTypedCode`、`anSdkArgumentViolationIsRejectedAsInvalidScene`、
`aSceneOverTheNodeBudgetIsRejectedWithTypedCode`、`aMaxBudgetSceneCompilesWithinTheCooperativePhaseWindow`。
注意：该套件**未含** T5 新增的 stroke-only/奇数 dash 生成门用例（那两门在 JVM
`VisualDrawOpsValidatorTest`/原生 parser 与 Node 台），本步只证明编译管线在设备上不回退。

### 3.2 flags 跨侧（app）

```text
Time: 0.018
There was 1 failure:
1) theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags(
     com.tingyun.smartmistakebook.visualjs.VisualMeasureReplayPaintFlagsInstrumentedTest)
com.tingyun.smartmistakebook.visualjs.VisualMeasurementEnvironmentException:
    measure paint flags 1283 do not match the environment fingerprint paintFlags=0
	at ...PaintVisualTextMeasureBackend$Companion.forEnvironment(VisualContentMeasuring.kt:460)
	at ...VisualMeasureReplayPaintFlagsInstrumentedTest
	    .theMeasuringPaintAndTheReplayTextPaintShareTheFingerprintedFlags(
	        VisualMeasureReplayPaintFlagsInstrumentedTest.kt:40)
Tests run: 2,  Failures: 1
INSTRUMENTATION_CODE: -1
```

- 红例在**第 40 行** `forEnvironment(environment)` 就抛出，尚未走到第 43-45 行的三面比对。
- 第 2 例 `aFingerprintThatDisagreesWithTheActualPaintFailsClosed` 绿——但它期望的正是"指纹 1 与
  实际不符即拒"，当前实现无论如何都会拒；**属同因绕过的绿，不当作跨侧一致性的正面证据**。
- 根因同 R1（step01 §4）：AOSP `Paint()` = 1283（`0x1|0x2|0x100|0x400`），
  `DEFAULT_PAINT_FLAGS = 0`（`VisualContentMeasuring.kt:184`）。
- 归因类别：**C（产品缺陷）**；非环境（数值确定、同机其他 app 套件绿）。

## 4. 判定

- 编译器设备面：**PASS 不回退**。
- T5 KD-78 ④ 设备面：**FAIL**（R1），且该缺陷已使真实 worker warm-up 全灭（step02）。
- T2 解析器：设备面无用例；JVM 面本轨未执行（UNVERIFIED，归脚本门）。
