# E-03 painter 设备冒烟：VisualDrawOpsPainterInstrumentedTest 17 例（含 compile→指令→Bitmap e2e）

| 字段 | 值 |
|---|---|
| evidence-id | E-03 |
| convention-version | 1 |
| batch | 2026-10-28-batch-28 |
| step | 三（painter 设备冒烟，T2 落码后） |
| gate-or-kd | R1 §4 设备判据面（T2 移交）+ 工单三"compile→指令→Bitmap 端到端一例（确定性断言）" |
| verdict | **PASS（含一次先红后绿夹具更正）**：17 例全绿（首跑 16/17，1 红归因=T2 夹具建位图顺序缺陷，painter 的 PAINTER_BOUNDS 入口门从未被执行到；更正后全绿）。e2e `aLegalSceneGoesFromCompileToColoredBitmap` 与同设备确定性 ×2 全绿 |
| executed-at | 2026-10-28T07:42:00+08:00 ～ 07:47:00+08:00 |
| operator | 设备验证轨（subagent，批次 28） |
| head | 8116ad98（夹具更正后重建 core:data androidTest v3 `a24d2c85…fd79857`，设备侧 sha256 实测一致） |
| worktree | dirty（本轨累计两处测试面更正） |
| device-serial | emulator-5554（test_device / API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | core:data androidTest v1（红轮 `5421a035…44cbe`）→ v3（绿轮 `a24d2c85…fd79857`） |
| apk-version | 0.2.0-offline |
| exit-code | 红轮套件完成 EXIT=0；绿轮 EXIT=0 |
| raw-log | `build/device-track-batch28/`：`step03-painter.log`（红轮）、`step03-painter-v2.log`（绿轮）、`step03-painter-logcat.txt`（115 行） |
| record-ref | 批次 28 记录（记录员后写） |
| sanitized | 是（本文件 §6） |

## 1. 目的与判据

T2 已落 painter 三件（`VisualDrawOpsPainter.kt`/`VisualDrawOpsPainterPure.kt` 主源 + 17 例仪器化套件，均为未跟踪新文件=本批落点）。工单三要求"compile→指令→Bitmap 端到端一例（确定性断言）"；本步跑**全量 17 例**（含 e2e 与确定性 ×2，超工单最小面、与 T2 移交判据对齐）：期望 `OK (17 tests)`。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe
$A -s emulator-5554 logcat -c
$A -s emulator-5554 shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsPainterInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 logcat -v threadtime -d > build/device-track-batch28/step03-painter-logcat.txt
# （v1 红 → 归因 → 夹具更正 → 重建 v3 重装 → 同命令复跑 = 绿轮）
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| v1（首跑） | Tests run: 17, **Failures: 1**（`anUnusableReplayScaleFailsClosed`） | 触发先红后绿归因（§4） |
| v2（更正后） | **`OK (17 tests)`** / Time 0.476 | 期望 17 全绿 |
| e2e 用例 | `aLegalSceneGoesFromCompileToColoredBitmap` 绿（真引擎+真编译器资产+校验器+painter→Bitmap 关键区域着色断言） | 工单三的"端到端一例" |
| 确定性 | `theSameOpsPaintIdenticalPixelsWithinOneMethod` / `…InAFreshTestMethod` 双绿（getPixels 整阵相等） | "确定性断言" |
| 9 类崩溃签名 | 9×0 | 期望全 0 |

## 4. 红例归因（先红后绿，不改产品层）

红轮 stack（`step03-painter.log`）：

```text
Error in anUnusableReplayScaleFailsClosed(…VisualDrawOpsPainterInstrumentedTest):
java.lang.IllegalArgumentException: width and height must be > 0
	at android.graphics.Bitmap.createBitmap(Bitmap.java:1197)
	at …VisualDrawOpsPainterInstrumentedTest.render(VisualDrawOpsPainterInstrumentedTest.kt:71)
	at …anUnusableReplayScaleFailsClosed(VisualDrawOpsPainterInstrumentedTest.kt:314)
```

归因（两源实读）：

1. 用例意图（`VisualDrawOpsPainterInstrumentedTest.kt:311-317` 原文）：pxPerDu 非有限/非正 → painter **绘制前** fail-closed（`PAINTER_BOUNDS`）；
2. painter 门在位且先于一切绘制：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter.kt:79-80` `if (!pxPerDu.isFinite() || pxPerDu <= 0f) throw paintingFailure(PAINTER_BOUNDS, …)`，先于 `:82` `rootMatrix.setScale` 与任何 Canvas 调用；
3. 缺陷在夹具顺序：`render` 辅助（原 `:69-71`）先用坏 scale 算位图尺寸 → `createBitmap(0,…)` 在**夹具**抛 IllegalArgumentException，`paintingFailureOf` 只接 `VisualDrawOpsPaintingException` → 用例红，painter 门从未被执行到。

→ **归因 = 本批 T2 测试夹具缺陷（设备首曝）；painter 产品源正确**；非环境、非引擎。修复 = 测试层给 `render` 增加第四参 `bitmapPxPerDu`（位图尺寸比例与回放比例解耦；默认=pxPerDu，16 个既有调用点行为逐位不变；3 个 fail-closed 调用点给 `bitmapPxPerDu = 2f` 合法建布），并注归因。**新增指认消灭的失败**：入口门用例崩在夹具建位图、painter 的 pxPerDu PAINTER_BOUNDS 门无任何设备执行证据。

**本轨源码更正登记（整文件纳入）**：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt` —— `render` 辅助签名+尺寸计算两行、`anUnusableReplayScaleFailsClosed` 三调用点、归因注释；其余 14 例与文件其余部分零改动。

## 5. 牵动面与重建

- 更正仅触测试夹具：painter 主源/纯计算层/校验器/编译器零改动。累计（E-02+E-03）本批 M 测试文件恰两个：`QuickJsSceneCompilerInstrumentedTest.kt`、`VisualDrawOpsPainterInstrumentedTest.kt`（`git status --porcelain -- core/data/src/androidTest` 实测）。
- 重建 v3 EXIT=0 → 重装 Success → 设备侧 sha256 `a24d2c85…` 与产物一致 → 同命令复跑 `OK (17 tests)`。v3 同时包含 E-02 的编译器断言更正（收口在装版本）。

## 6. 脱敏与去噪

- 删除：无。保留：红轮 stack（项目帧+框架帧首行）、签名计数（含 0）。扫描：见 `00-index.md` §6。二进制/截图：无。

## 7. 判定

**PASS**：17/17 断言在指定设备/产物上真实执行并成立；工单三的端到端与确定性面均有专用绿例；一次先红后绿闭环完成且归因到测试夹具层。T2 的 4 项移交（stroke 颜色缺口 / dash 奇数缺口 → known-defects 与 V1.1 契约收紧建议；stroke 中心线探针改几何存在性；measure/replay flags 指纹钉）不在本步执行面，维持移交原样。

## 8. 原始全量（不入仓）

- `build/device-track-batch28/step03-painter.log` / `step03-painter-v2.log` / `step03-painter-logcat.txt`（115 行）/ `build-data-test-v3.log`。
- `build/` 被 `.gitignore:15` 忽略；关闭判据只认本文件。
