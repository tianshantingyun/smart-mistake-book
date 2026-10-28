# E-01 painter 全量设备复跑：VisualDrawOpsPainterInstrumentedTest 21 例（FAIL 1/21）

| 字段 | 值 |
|---|---|
| evidence-id | E-01 |
| convention-version | 1 |
| batch | 2026-10-28-batch-29 |
| step | 一（painter 套件全量复跑） |
| gate-or-kd | 批次 29 计划 §0 遗留①（现树 painter 全量）；T1 §2 判据 |
| verdict | **FAIL**：`Tests run: 21, Failures: 1`；红例 `theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint`（expected 0 / actual **1283**），归因 C 类产品缺陷（T5 KD-78 ④ 假设 `Paint()` 默认 flags=0，实测 1283） |
| executed-at | 2026-10-28T22:33:00+08:00 前后（设备时钟 UTC 14:35） |
| operator | 设备验证轨（subagent，批次 29） |
| head | `3d9372e5` |
| worktree | dirty（T2/T3/T5/T6 在飞；painter 主源与测试面均为 M） |
| device-serial | emulator-5562（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | `data-debug-androidTest.apk` sha256 `4d152b5afbd9f3fa…fa72cd81`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 `EXIT=0`（`am instrument` 恒 0，非判据；判据 = summary） |
| raw-log | `build/device-track-batch29/step01-painter.log`、`step01-painter-logcat.txt` |
| record-ref | 批次 29 记录（后写） |
| sanitized | 是 |

## 1. 目的与判据

批次 28 的 painter 设备证据只有 17 例（修复轮前源）；现树 T5 已把套件改为 **21 例**
（`grep -c "@Test"` 实测；新增 8 位色 alpha 与 replay textPaint flags 两例，三例生成门负例改为
回放兜底形态）。T1 §3.1 明确"判据以落码后 `grep -c` 实测为准"，故本步判据 =
**`OK (21 tests)`**，不是 T1 总表预填的 19。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch29/step01-painter.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch29/step01-painter-logcat.txt
```

## 3. 结果

```text
Time: 0.265
There was 1 failure:
1) theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint(
     com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest)
java.lang.AssertionError: expected:<0> but was:<1283>
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest
	    .theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint(
	        VisualDrawOpsPainterInstrumentedTest.kt:267)
Tests run: 21,  Failures: 1
INSTRUMENTATION_CODE: -1
```

- 通过 20/21；其余 20 例（含 8 位色 alpha 新例 `anEightDigitColorAlphaMultipliesWithTheOpAlpha…`、
  残尾 dash、空 dash 回放兜底、e2e）全部绿。
- 9 类崩溃签名：0（见 step06）。

## 4. 归因（C 类：产品缺陷；未放宽断言）

### 4.1 失败点

`VisualDrawOpsPainterInstrumentedTest.kt:267`：

```kotlin
assertEquals(REPLAY_TEXT_PAINT_FLAGS, VisualDrawOpsPainter().textPaintFlagsForTest())
```

`VisualDrawOpsPainter() 的 textPaint = Paint()`（`VisualDrawOpsPainter.kt:51`），设备上
`textPaint.flags` = **1283**；常量 `REPLAY_TEXT_PAINT_FLAGS = 0`
（`VisualDrawOpsPainterPure.kt:78`）。

### 4.2 四源核对

| 源 | 结论 |
|---|---|
| 设备实测（本轨） | `textPaint.flags = 1283`（本用例）；worker warm-up 同值（step02：`measure paint flags 1283 … fingerprint paintFlags=0`） |
| AOSP `Paint.java` android-14.0.0_r1 | `public Paint() { this(ANTI_ALIAS_FLAG); }`；`HIDDEN_DEFAULT_PAINT_FLAGS = DEV_KERN_TEXT_FLAG \| EMBEDDED_BITMAP_TEXT_FLAG \| FILTER_BITMAP_FLAG` ⇒ `0x1\|0x400\|0x100\|0x2 = 1283` |
| 计划 §1.5 / T5 KDoc | 意图是"flags 进指纹、度量/回放同值"；**未**规定必须为 0 |
| 契约 `visual-generation-contract.md` §5 | 只要求 flags 进指纹；0 是落码者假设 |

→ 测试期望（`== 常量 == 0`）与产品常量一致，但**二者共同建立在一个错误平台假设上**；
测试不是"断言漂移"，产品常量才是缺陷本体。按 T1 §7.3 判 **C**。

### 4.3 推荐修法（本轨未执行，归修复环）

首选（保留 R1 §3.2"文本 AA 关"的设计意图，且让指纹字面 0 成真）：

1. `VisualDrawOpsPainter` 构造后显式 `textPaint.flags = REPLAY_TEXT_PAINT_FLAGS`（当前为 0）；
2. `PaintVisualTextMeasureBackend.forEnvironment` 在 `Paint()` 后显式
   `paint.flags = environment.paintFlags`，再做一致性校验（`VisualContentMeasuring.kt:459`）；
3. 重建重装后同命令复跑，期望 `OK (21 tests)`；同时复跑 step03 flags（2/2）。

备选（保留当前渲染，改指纹值）需把 `DEFAULT_PAINT_FLAGS`/`REPLAY_TEXT_PAINT_FLAGS` 与 JVM 字面
一起改为平台默认值；但该值随 API 可能变化，不推荐。

**牵动面**：`VisualDrawOpsPainter.kt`（1 行）、`VisualDrawOpsPainterPure.kt`（常量注释）、
`VisualContentMeasuring.kt`（1 行）、JVM 测字面（若选备选）、契约 §5/KD-78 记录。
选择首选时 core 侧测试零改动。

## 5. 判定

**FAIL（且非环境/夹具问题）**：20/21 真执行且成立；唯一红例由本批 T5 常量假设错误导致，
与隔离 12 红、flags 1 红同根因（R1）。**未**改测试、未放宽、未过滤。
