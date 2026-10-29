# E-08 一致性原型门 15 例：`VisualDrawOpsParityInstrumentedTest`（FAIL 14/15：N09-P06 负控期望值算错，A 类）

| 字段 | 值 |
|---|---|
| evidence-id | E-08 |
| convention-version | 1 |
| batch | 2026-10-29-batch-31 |
| step | 二（八套件第 8；判据 §2.8、§5.5）+ 五（红例归因，判据 §6） |
| gate-or-kd | 一致性门（**原型门**，`product-gate=no`）；`calibrated-r1` 的 N09–N12 放宽许可证 |
| verdict | **FAIL**：`Tests run: 15, Failures: 1`（N09-P06）；归因 **A（原型门负控自身期望值算错）**，产品源零改动，**未放宽任何断言** |
| executed-at | 2026-10-29T06:40:01–06:40:02+0800 |
| operator | 设备验证轨（subagent，批次 31） |
| head | `59d4a03f` |
| worktree | dirty（本轨零写入；目标测试文件是他线 ` M`） |
| device-serial | `emulator-5562`（API 34 / x86_64 / 4096） |
| apk | `core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk` |
| apk-sha256 | `34151d4d9571daffc27e6a370172afb6775b7e730742dc3484ffd5fdb2e7b952`（设备侧一致） |
| apk-version | （测试包 versionName 空） |
| exit-code | 宿主 shell `EXIT=0`（**非判据**；判据 = `Tests run:`/`Failures:` 行） |
| raw-log | `build/device-track-batch31/step08-parity.log`（240 行 / 12801 B）/ `step08-parity-logcat.txt`（290 行 / 47578 B） |
| record-ref | `docs/research/2026-10-29-batch-31-record.md`（后写） |
| sanitized | 是（§6；扫描 0 命中） |

## 1. 目的与判据

判据 §2.8：`OK (15 tests)`；`calibrated-r1`（质心 0.75、墨量 0.93–1.07）下仍要求 N09–N12 全红，
否则 = `PARITY_GATE_DESENSITIZED`（A 类，门失去分辨力，标定作废）。**15/15 不等于产品门通过**。

## 2. 命令（可复跑）

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  | tee build/device-track-batch31/step08-parity.log
$A -s $S logcat -v threadtime -d > build/device-track-batch31/step08-parity-logcat.txt
```

## 3. 结果摘要

| 指标 | 值 | 判据 |
|---|---|---|
| tests run | 15 | 期望 15（构建前复读 15） |
| failures | **1**（`n09OnePixelTextShiftIsCaught`） | 期望 0 |
| errors / skipped | 0 / 0 | 期望 0 |
| `STATUS_CODE: 0` / `1` | 14 / 15 | 1 例中止 |
| `PARITY_GATE` 行 | 15，全部 `product-gate=no` + `thresholds=calibrated-r1` | = 测试数 |
| `PARITY_CONTROL `（带尾随空格） | **14** | 期望 15（N09-P07 因中止未执行） |
| 其中 `red=true` | **14/14** | 期望全红 |
| `PARITY_GATE_DESENSITIZED` | 0 | 期望 0 |
| `PARITY_SYSTEMATIC_DRIFT` | 0 | 期望 0 |
| `PARITY_DIAGNOSTIC_WRITTEN` | 0 | 绿轮期望 0 |
| `PARITY_F5`（全部 / `class=TEXT`） | 0 / 0 | TEXT 必须 0 |
| `PARITY_ENV` 末行 | 含 `replayTextPaintFlags=0` | 判据 §5.5 |

## 4. 失败原样（判据 §6.2-1：先抓原样，不删 log）

```text
INSTRUMENTATION_STATUS: test=n09OnePixelTextShiftIsCaught
INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: N09-P06: a whole-block 1 px shift must read
  centroid=1.000 (translation equivariance) expected:<1.0> but was:<1.2624767812258337>
  at org.junit.Assert.assertEquals(Assert.java:555)
  at com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest
     .n09OnePixelTextShiftIsCaught(VisualDrawOpsParityInstrumentedTest.kt:308)
```

第一项目帧：`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt:308`
（`assertEquals("N09-$id: …centroid=1.000…", 1.0, text.centroidDeltaPx, 1e-6)`）。

同轮同一用例在**该断言之前**已满足门级判据：

```text
step08-parity-logcat.txt:125  PARITY fixture=P06 … text=bboxDev=2 centroid=1.26 mass=0.991
                              … verdict=F4_GEOMETRY
step08-parity-logcat.txt:126  PARITY_CONTROL N09-P06 fixture=P06 verdict=F4_GEOMETRY red=true
```

即 `assertRed(...)` 通过（门判红、`red=true`），失败只发生在随后的"质心必须恰好 1.000"等式上。

## 5. 归因（A/B → D → C 顺序）

### 5.1 D（设备/旧包）先排除

- 设备与产物：`emulator-5562` / API 34 / x86_64 / 4096；data 测试包 sha256 `34151d4d…` 设备侧一致；
  dex 门命中 `n09OnePixelTextShiftIsCaught` 与 `thresholds=calibrated-r1`（15 行）→ 不是旧包。
- 本轮 15 例全部启动（`STATUS_CODE: 1` × 15），非 11 例旧包形态。

### 5.2 再排 B（测试期望漂移）与 C（产品缺陷）：该等式测的量本身不是它声称的量

`measureText` 的 `centroidDeltaPx` 是**屏栅格与 PDF 栅格之间的质心距离**（`VisualDrawOpsParityMetrics.kt:798-802`：
`hypot(pdfCentroidX/pdfMass − screenCentroidX/screenMass, …)`），**不是**同侧平移前后的质心差。
`shift(pair.pdf, dx = 1)` 只平移 PDF 侧 1 px，因此读数是"基线向量 + (1,0)"的**向量和模长**，
而不是"基线模长 + 1"。测试注释（`…ParityInstrumentedTest.kt:295-300`）的"整块平移 t 使质心恰好变化 |t|"
忽略了基线的屏↔PDF 质心偏移——而该偏移正是同一轮正例里被判 **F2_AA** 的 AA/字体差：

```text
step08-parity-logcat.txt:218  PARITY fixture=P06 … text=bboxDev=1 centroid=0.32 mass=0.991 verdict=F2_AA
step08-parity-logcat.txt:220  PARITY fixture=P07 … text=bboxDev=1 centroid=0.52 mass=0.957 verdict=F2_AA
step08-parity-logcat.txt:236  PARITY fixture=P07 … text=bboxDev=1 centroid=0.52 mass=0.957 verdict=F2_AA
```

（`:220` 是正例门的 P07 读数，`:236` 是 N11 的基线读数；两者同值 = 同轮可复现。P07 的精确值
`0.5181329158118464` 见批次 30 `evidence/2026-10-29-batch-30/step05-parity.md`。）

数字自洽（模型检查，非重新测量）：设基线向量为 `v`，平移 `t` px 后读数 `= |v + (t,0)|`。

| 用例 | 基线模长（实测） | 平移后读数（实测） | 反解 `v`（derived） | 一致性 |
|---|---|---|---|---|
| N09-P06 | 0.32（`:218`） | `1.2624767812258337`（`:129`） | `(0.2457, 0.2050)`，模长 0.32 | 自洽 |
| N12-P07 | 0.5181（批 30 精确值；本轮 0.52 `:220`） | `3.56014654994809`（`:186`） | `(−0.4492, 0.2582)`，模长 0.5181 | 自洽 |

两例独立夹具、独立平移量（+1 / +4）用同一模型精确复现观测值 ⇒ 度量的平移等变性成立
（PDF 侧确实整体移动了 t px，墨量比 0.991 / 0.957 不变），**产品路径（painter / PDF 回放）没有错**。

### 5.3 结论：A 类（原型门负控自身的判定逻辑写错期望）

- 门级判据**满足**：N09-P06 `verdict=F4_GEOMETRY red=true`，`bboxDev=2 > 1`，`centroid 1.2625 > 0.75`；
  无 `PARITY_GATE_DESENSITIZED`。
- 红在负控**追加的等式**：它把"跨栅格质心距离"当成"同侧质心位移"，期望写死 `1.0`。
- 同族对照：N12（同一作者、同一平移思想，+4 px，`…ParityInstrumentedTest.kt:381-392`）只断言
  `centroid > 阈值`（`:392`），所以 N12 绿；N09 的严格等式（`:308-312`）是全族唯一的例外
  ——这是负控族内部的不自洽，不是产品行为差异。
- **不是 B**：权威源（平移等变性）约束的是"PDF 侧平移前后质心差 = t"，测试从未测量该量，
  因此不存在"期望常量写错、改成权威值即可"的简单替换；**不是 C**：产品侧观测与等变性一致。

## 6. 处置（未放宽、未改他线文件）

1. **零改动**：本轨不删/不弱化任何断言，也不把 `1.0` 改成实测值。目标文件
   `VisualDrawOpsParityInstrumentedTest.kt` / `VisualDrawOpsParityMetrics.kt` 均为他线在飞 ` M`，
   `core/data` 属本轨禁改热区 → **停项登记**（判据 §6.2-6：未复跑 = UNVERIFIED）。
2. **给 T4 的最小正确修法（未执行，供其落码）**：在 `TextParityMetrics` 暴露 pdf 侧逐轴质心
   （`pdfCentroidX/Y`、`screenCentroidX/Y`），把 N09 的等式改为**同侧**平移不变性：
   `assertEquals(1.0, |pdfCentroidX(shifted) − pdfCentroidX(base)|, 1e-6)`，并保留
   `assertRed(...)` 与 `centroidDelta > maxCentroidDeltaPx` 两条。这样断言更强（直接测声称的性质），
   不是放宽；N12 可同样加（+4.0）。
3. **影响面**：`calibrated-r1` 的"放宽许可证"本轮**只部分成立**——N10/N11/N12 全红、N09-P06 门级红，
   但 **N09-P07 因中止未执行**，且套件为 FAIL。因此 **parity 绿轮 UNVERIFIED**，
   `calibrated-r1` 不得写成"设备已验证"；一致性门仍是原型门（`product-gate=no`）。
4. **不牵连 KD-80 / 其余套件**：本红在第一项目帧即测试断言，产品源零改动；备份/迁移/painter/PDF/隔离/
   flags/提交/仓库/结构门/trimmer 各自独立绿轮（E-01…E-07、E-09、E-12）。

## 7. 判定

**FAIL（1/15）**，归因 A：负控期望值算错（跨栅格距离 vs 同侧位移），门级判红成立、产品零缺陷；
未放宽断言、未改他线文件、未复跑绿轮。**calibrated-r1 设备绿轮 UNVERIFIED**。

## 8. 原始全量（不入仓）

- 路径：`build/device-track-batch31/step08-parity.log` / `step08-parity-logcat.txt`
- 行数/大小：240 行 / 12801 B；logcat 290 行 / 47578 B
- 说明：`build/` 被 `.gitignore:15` 忽略。
