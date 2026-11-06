# C 域证据索引（批次 44，2026-11-04 实跑）

> 本文件是导航与口径说明，**不是证据**；台账 C 行只引用同目录下的
> `*.xml` / `*.device.json` / `*.log` / `*.png` / `*.xml`（uiautomator dump）。
> 所有设备证据绑定 `serial + boot_id + apk_sha256 + test_apk_sha256`。

## 1. 身份

| 项 | 值 |
|---|---|
| 源快照 | HEAD `26f2327b0f88d3cad7e076b967b62874e36ca07d` + 工作树脏（共享树，未提交） |
| app APK（已安装，设备跑的就是它） | `84cae864c42f7e26203d21f3901c06a4d0f02f2dd3b012bb63727ef32e5dab5b` |
| app androidTest APK | `aab6af01af75ae4d0c310e367a0d947ed44e76df56ef1566fc9936fd1022ac5e` |
| core:data androidTest APK（自测包 target==自身） | `c9943728ec7dfb699345f39af311c0c52e183fec70224a4952eae781000dc488` |
| 设备 | `emulator-5584`，API 34，x86_64，1080×2400，density 420，boot_id `5a34f877-606b-4e74-b7b8-84f6398faf9d` |
| 记录时间 | 2026-11-04 13:13–13:35 UTC（本机 21:13–21:35） |

**源漂移（必须随证据一起读）**：
- 本地 `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk` 为
  `c65d63f5…`（文件 mtime 16:58），但**设备上安装的是 `84cae864…`**（`pm path`
  + 设备端 `sha256sum` 实测）。本目录所有 app 侧设备证据只绑 `84cae864…`，
  不外推到本地最新构建。
- `StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt` 在 APK 构建后被改过
  （`git status` = M；diff 只把断言里的装箱比较统一成 `Long`，语义不变）。
  设备跑的是构建时字节，不是现树该文件的逐字版本。
- 生产视觉代码（`core/data/.../visual`、`core/data/src/main/resources/visual`、
  `core/export/src/main`、`app/src/main/.../visualjs`）在本轮 `git status` 中
  无改动，所以“设备行为 == 现树生产代码行为”成立；测试源集的漂移不影响这
  一句。

## 2. 口径修正：SceneV1 的“六原语”不是 line/rect/circle/arc/arrow/axis

台账骨架由 `tools/ci/acceptance_init.py:270-271` 生成，把六原语写成
`line/rect/circle/arc/arrow/axis`，并把这 30 行全部指向
`MistakePdfPlannerGoldenTest.kt`。这两处都与仓内事实冲突：

1. `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOps.kt:95`
   明写“DrawOps V1 词表六原语”，实际 sealed 子类是
   **Save / Concat / Clip / DrawPath / Label / Restore**（`:98`–`:139`）。
2. `visual-sdk.js` 的 `circle/arc/arrow/axis/polyline/random` 是 **SDK 辅助
   原语**，不是 DrawOps 词表；`rect` 明确“V1 不设”（`visual-sdk.js:14-16`
   注释），Node 台把 `{type:'rect'}` 当作未知节点拒绝
   （`reject/node/unknown-type`，`run_tests.mjs:328`）。
3. `MistakePdfPlannerGoldenTest.kt` 是 PDF 分页计划金样，与 SceneV1 绘制原语
   无关。

因此本轮把 C-001…C-030 重写为 **六个 DrawOps 原语 × 五条必守性质**，逐行
指向真正执行该性质的 Node 台检查或设备用例。证据索引里保留这条修正，是
为了让后续复核能区分“骨架的猜测”与“实跑过的检查”。

## 3. 设备用例（recorder 逐例 XML）

| label | 类 | tests | fail/err/skip | verdict |
|---|---|---:|---|---|
| `c-scene-compiler` | QuickJsSceneCompilerInstrumentedTest | 6 | 0/0/0 | PASS |
| `c-visualdrawopspainterinstrumentedtest` | VisualDrawOpsPainterInstrumentedTest | 24 | 0/0/0 | PASS |
| `c-visualdrawopspdfpainterinstrumentedtest` | VisualDrawOpsPdfPainterInstrumentedTest | 16 | 0/0/0 | PASS |
| `c-visualdrawopsparityinstrumentedtest` | VisualDrawOpsParityInstrumentedTest | 16 | 0/0/0 | PASS |
| `c-strictofflinerelayoutdevicevectorinstrumentedtest` | StrictOfflineRelayoutDeviceVectorInstrumentedTest | 1 | 0/0/0 | PASS |
| `c-v01-visual-chain` | V01VisualChainInstrumentedTest | 4 | 0/0/0 | PASS |
| `c-quickjs-engine` | QuickJsVisualExecutionEngineInstrumentedTest | 27 | 0/0/0 | PASS |
| `c-paint-flags` | VisualMeasureReplayPaintFlagsInstrumentedTest | 2 | 0/0/0 | PASS |
| `c-tutorvisualpreviewrendererinstrumentedtest` | TutorVisualPreviewRendererInstrumentedTest | 4 | 0/0/0 | PASS |
| `c-tutorvisualpreviewassemblyinstrumentedtest` | TutorVisualPreviewAssemblyInstrumentedTest | 3 | 0/0/0 | PASS |
| `c-restore-processrebuildcontinuesf` | TutorVisualRebuildRestoreInstrumentedTest#processRebuild… | 1 | 0/0/0 | PASS |
| `c-restore-everyrestoregatesurvives` | TutorVisualRebuildRestoreInstrumentedTest#everyRestoreGate… | 1 | 0/0/0 | PASS |
| `c-restore-single-rerun` | TutorVisualRebuildRestoreInstrumentedTest#theRestoreLegAlone… | 1 | **1/0/0** | **FAIL** |

合计：设备用例 106 例，105 绿、1 红（同一失败在类级轮与单例复跑中各出现一次，
见 §5）。

**共享证据说明**：一个类的 XML 含多条 `testcase`，C 行按性质逐条引用同一份
类级 XML/device JSON/log（lint 会报 `EVIDENCE_REUSED`，属预期）。这不是
summary 复用：每条 testcase 名可在 XML 中逐条核对；同一份 XML 也不跨设备轮次
或跨 APK 使用。

## 4. JVM/静态面（非 Gradle）

| 命令 | 结果 | 覆盖 |
|---|---|---|
| `node tools/visual_compiler/run_tests.mjs` | `94 passed, 0 failed`，EXIT=0 | SDK 原语形状、确定性、字段/节点/路径/样式拒绝、预算、dash/stroke-only 生成门 |
| `python -I tools/ci/check_visual_test_seams.py` | `OK: 0 main/test references and 1 androidTest reference`，EXIT=0 | 测试缝引用纪律 |
| `python -I tools/ci/metamorphic_checks.py --demo` | `ok=true`，EXIT=0 | 12 条蜕变关系可执行 |
| `… --pairs-dir c-domain/pairs --only render/theme_swap` | `ok=true`，EXIT=0 | 真实 UI dump：light→dark 文本/几何不变 |
| `… --pairs-dir c-domain/pairs --only render/font_scale_reflow` | `ok=true`，EXIT=0 | 真实 UI dump：fontScale 1→2 文本不变且节点在 viewBox 内 |
| 设备截图基线 `screenshot_baseline.py compare` | light-vs-dark EXIT=0（MAE 阈值 2.0 / 0.02）；light-vs-relaunch EXIT=0 | 暗色不改变应用窗口；浅色重启可复现 |
| `c-fontscale-probe.log` | 13 个标签文本集不变、全部在 1080×2400 内、高度单调不减，EXIT=0 | fontScale 1/1.3/2 |
| `c-theme-luminance.log` | 浅/暗窗口平均亮度差 0.14/255，EXIT=0 | 系统暗色未切换应用配色 |

## 5. 本轮发现的失败（不得写成通过）

**C-065 / `theRestoreLegAloneReplaysOfflineWithZeroDispatchBookings`**
（`c-restore-single-rerun.xml` + `c-restore-single-rerun.log`；类级轮
`c-tutorvisualrebuildrestoreinstrumentedtest.xml` 同样 1 failure）：

```
java.lang.AssertionError: the restored document must leave ink (restore is not a silent no-op)
    at TutorVisualRebuildRestoreInstrumentedTest.kt:158
Tests run: 1, Failures: 1
```

- **可复现**：类级轮与单例轮各跑一次，同一断言同一行失败；不是一次偶发。
- **定位**：用例把 `snapshotInput(fill="#112233")` 的 `pathDocument` 写成
  `M(0,0) L(10,10)`、`stroke=null`、只有 `fill` 的画布（`TutorVisualRebuildRestoreInstrumentedTest.kt:385-389`）。
  开放的两点路径按 fill 关闭后面积为零，Skia 不产生可观测墨水；因此该断言
  在当前夹具下**可能是测试夹具缺陷**，不是“恢复链一定坏了”。同一类轮里
  `processRebuildContinuesFromTheLatestLiveCheckpoint` 与
  `everyRestoreGateSurvivesRealRowsThroughThePortReadSide` 均绿，V01 的
  “冻结命令真回放”也绿，所以本行只判 **FAIL（该用例红）**，不判“恢复整体失效”。
- **处置**：交 FIX 域先红后绿；修夹具（改用有面积的路径或加 stroke）或修
  恢复/绘制链，都必须保留这条断言，不得删除。

## 6. 暗色主题事实（不是失败，但必须说清）

`core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/Theme.kt:187-194`
的 `SmartMistakeBookTheme` 只传 `SmartLightColorScheme`，没有
`isSystemInDarkTheme()` 或 `darkColorScheme`。设备实测：
`cmd uimode night yes` 后状态栏变暗，但应用窗口像素与浅色基线在阈值内一致
（3104/2,592,000 像素变化，MAE≈0，变化集中在状态栏）。产品契约
`docs/current/product-contract.md` 未要求深色配色，因此 C 域把暗色检查写成
**不变性**（文本/几何/窗口像素不因系统暗色而变），不写成“深色主题通过”；
审美层面的深色方案缺失登记为残余，不冒充通过。

## 7. 对抗复核（每域至少一次反例）

| 反例尝试 | 期望 | 实测 | 证据 |
|---|---|---|---|
| 测试缝无 androidTest 引用 | 门红 | `FAIL: the test seam is orphaned…`，EXIT=1 | `c-adversarial-seams-red.log` |
| 篡改 theme_swap 的 after 文本 | 关系 VIOLATED | `"ok": false, "reason": "主题切换改变了文本或几何"`，EXIT=1，带 before/after 反例 | `c-adversarial-metamorphic-red.log` |

两条都证明门不是恒绿。反例文件在 `adversarial-pairs/`（只用于红相，不进台账
PASS 证据）。

## 8. UNVERIFIED 与升级路径

本域 12 行 UNVERIFIED，原因分三类：

1. **仅 JVM 单测覆盖，Gradle 只允许门禁阶段串行运行**：C-005（未闭合 save）、
   C-045（字体/渲染版本进快照身份）、C-071（4 MP 像素预算）。
2. **Node 台已过但只有 cmd 一类证据，缺第二类独立证据**：C-010（singular
   矩阵）、C-015（未知 fill rule）、C-020（非 M 开头路径）、C-066/067/068
   （未知字段/节点/路径命令）、C-089（hitRegions 129 上限）。
3. **无专项设备/Node 用例**：C-008（嵌套 concat 后乘）、C-088（屏幕侧不绘制
   hitRegions）。

升级路径：门禁阶段串行跑 `:core:data:testDebugUnitTest`、`:core:database:testDebugUnitTest`
后，把对应 XML 补进 C-005/010/015/020/045/066/067/068/071/089；C-008/C-088
需要新增用例（新增前先说明它消灭的失败：C-008 消灭列序/合成方向错位；
C-088 消灭屏/PDF 对 hitRegions 行为分叉）。

## 9. 复跑命令（逐字）

```bash
# Node 编译器台（非 Gradle）
node tools/visual_compiler/run_tests.mjs

# 设备（emulator-5584；APK 见 §1）
python -I tools/ci/am_instrument_recorder.py --serial emulator-5584 \
  --package com.tingyun.smartmistakebook.core.data.test \
  --class com.tingyun.smartmistakebook.core.data.visual.QuickJsSceneCompilerInstrumentedTest \
  --apk-sha c9943728ec7dfb699345f39af311c0c52e183fec70224a4952eae781000dc488 \
  --test-apk-sha c9943728ec7dfb699345f39af311c0c52e183fec70224a4952eae781000dc488 \
  --label c-scene-compiler --out-dir docs/research/evidence/2026-11-05-batch-44/c-domain

# 字体缩放 / 暗色（设备设置 + dump + 关系校验）
adb -s emulator-5584 shell settings put system font_scale 1.3
adb -s emulator-5584 shell cmd uimode night yes
python -I tools/ci/metamorphic_checks.py --pairs-dir \
  docs/research/evidence/2026-11-05-batch-44/c-domain/pairs --only render/theme_swap
python -I tools/ci/metamorphic_checks.py --pairs-dir \
  docs/research/evidence/2026-11-05-batch-44/c-domain/pairs --only render/font_scale_reflow
```

## 10. 证据文件哈希（本目录，辅助）

台账只把 `test/device/cmd` 计入独立证据类；本表用于复核文件未被替换。生成
命令：`python -I tools/ci/acceptance_hash_manifest.py`（批次门禁统一执行），
本域不另写清单文件，避免把“清单”当证据。
