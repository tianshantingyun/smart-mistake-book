# 批次 30 T12：文档计数回改 + KD 状态核对（2026-10-29，台账维护轨）

> **口径**：本轨只改 docs，未改任何 Kotlin/JS/Gradle 文件，未运行 Gradle，未执行任何 JVM/仪器化/设备用例，未做 git 写操作。所有"现树"数字均为本轮只读 `grep -c` / `wc -l` / `git status` 实测；设备侧只有批次 29 旧证据，**本轨写作时点（约 01:11）批次 30 修复后设备证据尚不存在**。
>
> **（2026-10-29 复核修复轮更正）** 设备轨在 02:25–02:41 已落盘 `docs/research/evidence/2026-10-29-batch-30/`（11 份：7 绿 + 2 红 + 1 部分红）；本文 §3/附录 C/附录 D 的"目录不存在 / 未出数 / 源文件干净"只对 01:11 时点成立。现树事实：painter 24/24、flags 2/2、隔离 13/13、PDF **16/16** 绿；parity 11 例 1 红（A 类阈值）；备份 34 例 8 红（D-01 未同步）；KD-71 的 T7 已落码（`QuickJsVisualExecutionEngine.kt` +20 / JvmTest +58）。KD-78 ④ 与 KD-79 台账最低口径由设备轨判定满足。下方历史数字保留原时点，不据此读成现树状态。
>
> 计划对照：`.zcode/plans/plan-2026-10-29-batch-30-device-persist-fixes.md:34`（任务 12）与 `:46`（验收：KD 按证据裁决、不虚报）。
> 基线对照：`docs/research/2026-10-29-batch-30-baseline.md` §3（在树修复锚点）与 §4（设备证据计数 vs 现树计数）。

---

## 1. 改动清单（逐文件 + 行号）

| 文件 | 行（改后） | 改动 | 依据 |
|---|---|---|---|
| `docs/research/evidence/2026-10-28-batch-29/step06-signatures.md` | `:78-79` | 杀因记账更正：13 杀因 = **12 warm-up fail + 1 `the host unbound`**；AMS `isolated not needed` 另有 2 行、不计入杀因。原文"1 AMS `isolated not needed`"作废；判定（worker 收口 FAIL）不变 | 批次 29 记录 §5 第 10 项登记；本轮 raw log 重算（附录 E） |
| `docs/known-defects.md` | `:2892` | KD-78 T5 Progress 标题"余 ③"就地回改注记：③ 设备像素证据已出数并绿，余项为 ④ 修复后设备复跑 | 批次 29 设备轨 `step01-painter.md:59-60`；本批计划任务 12 |
| `docs/known-defects.md` | `:2915` | KD-78 新增批次 30 T12 Progress：现树计数 24/11/13/13/2/13；修复后设备复跑**未出数**；维持 open；同块附**锚点行号回改**（`REPLAY_TEXT_PAINT_FLAGS :78→87`、`DEFAULT_PAINT_FLAGS :184→192`、`forEnvironment :456-464→463-481` 等，见附录 A 基线 §3） | 附录 A/B/C/E |
| `docs/known-defects.md` | `:2927` | KD-79 新增批次 30 T12 Progress：修复仍在树（跟踪流 `:98-109`、`PAINTER_PDF_IO` `:117-163`），PDF 154 行；设备复跑未出数；维持 open | 附录 B/C |
| `docs/known-defects.md` | `:2746` | KD-71 新增批次 30 T12 Progress：本批未见新增证据（引擎与用例源文件 git 干净、无本批证据目录）；维持 open | 附录 D/C |
| `docs/research/2026-10-28-pdf-replay-differences.md` | `:7` | 顶部"更正注记"：`writeTo` 的 `IOException` 会逃回 Java 的初稿结论被设备否证（native `ExceptionClear` 吞异常 → fail-open），引 KD-79；§7.2/§7.3 已改跟踪流口径 | 本批计划任务 12；KD-79 |
| `docs/research/2026-10-28-screen-pdf-parity-gate.md` | `:14`、`:80` | "设备 19 例 + JVM 10 例"→ 现树实测 **设备 24 / JVM 纯测 13**；注明原文为批次 28 时点、批次 29 设备实跑 21、修复后未出数 | 附录 A；批次 29 记录 §5 第 17 项 |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | `:58` | §3.2 `hitRegions[]`"必须为空"加作废注记（批次 29 裁定 1：可非空、painter 不绘制、交互层消费） | 批次 29 记录 §5 第 9 项 |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | `:144`-`:146` | S-04/S-05/S-06 现状刷新：painter 21→**24**、parity 10→**11**、PDF 104→**154 行**；"批次 29 设备证据未出数/未上设备"→ 已落盘（painter 21 / PDF 13 / 一致门 10 / flags 2；`pdfDoesNotPaintHitRegions` 为 12 绿例之一）；批次 30 复跑未出数 | 附录 A/B/E；批次 29 设备证据 |
| `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md` | `:609` | 附录 G 顶部加计数回改：21/10 是批次 29 设备实跑（修复前源），现树 24/11/13/13；批次 30 复跑未出数 | 附录 A/E |
| `docs/research/2026-10-28-batch-29-device-criteria.md` | `:104` | "painter JVM 纯测 10 例"→ 现树 **13 例**（批次 29 T5 +3） | 附录 A |
| `docs/current/visual-generation-contract.md` | `:118`、`:123` | 表头时点加"批次 30 T12 回改"；PDF painter"104 行"→ **154 行**（KD-79 跟踪流修复后）；PureTest 10→13；parity 10→11（N08 只在树） | 附录 A/B |

> **整文件纳入**：`docs/current/visual-generation-contract.md` 与 `docs/known-defects.md` 在本轨开工时干净（基线 §3），本轨改动后变 `M`；提交时须整文件纳入，不得只挑片段。其余五个 research/evidence 文件同样为整文件 `M`（`git status --porcelain` 实见附录 F）。

---

## 2. KD 状态裁决（按本批证据，不虚报）

| KD | 本批证据 | 裁决 |
|---|---|---|
| **KD-71**（close/execute 竞态） | 无本批新增代码/测试改动（`git status` 空），无本批设备证据目录；既有证据 = 批次 23 E-02（20 例，两条用例 0）+ 批次 28 T1 引擎整包 `OK (26 tests)` | **维持 open**（"判据已满足、待裁定"本批未重跑） |
| **KD-78**（DrawOps 四缺口） | ③ 设备像素证据批次 29 已出数并绿；④ 显式装配修复在树；**修复后设备复跑未出数** | **维持 open**（余项 = ④ 修复后设备复跑） |
| **KD-79**（PDF fail-open） | 跟踪流修复在树（`VisualDrawOpsPdfPainter.kt:98-109/117-163`）；**修复后设备复跑未出数** | **维持 open**（关闭条件 = API 34 复跑 PDF 13 例全绿 + native 模拟 IOException 仍在 logcat） |

"未出设备证据的一律标未出数"已逐条写入三块 Progress；没有任何一条被写成"已关闭"。

> **复核轮（2026-10-29 更正）**：设备轨 02:25–02:41 已出数，上表"未出数"只对 T12 时点成立——KD-71 的 T7 已落码（`QuickJsVisualExecutionEngine.kt` +20 / JvmTest +58），但 JVM/设备执行证据仍无；KD-78 ④ 实测跨侧 flags **2/2 绿**（`OK (2 tests)`，九项判据全满足）、painter 24/24 绿，设备轨判定 **④ 可关闭**；KD-79 PDF **16/16 绿** + 230 条 native `IOException`，设备轨判定台账最低口径满足、可按 KD 自身条件关闭。最终关闭仍由记录员/主会话按证据裁定，本文不代为关闭。

---

## 3. 未验证项（如实）

- **设备面（本轨时点未出数，复核轮已出数）**：本轨写作时（约 01:11）尚未见批次 30 设备证据；设备轨 02:25–02:41 已落盘 `docs/research/evidence/2026-10-29-batch-30/` 11 份（见文首更正与附录 C）。现树实测：painter 24/24、flags 2/2、隔离 13/13、PDF **16/16** 绿；parity 11 例 1 红（A 类阈值）。
- **UNVERIFIED**：JVM/Node 门与 Gradle 门——本轨零 Gradle、零 JVM 复跑；计数来自源文件 `@Test`，不是测试执行结果。
- **KD-71 T7（写作时点未落码，复核轮已落码）**：本轨写作时（01:11）该文件干净；复核轮 `git diff --stat` 实测 `QuickJsVisualExecutionEngine.kt` +20 / `QuickJsVisualExecutionEngineJvmTest.kt` +58（见附录 D）。但本轮/该轨的 JVM/设备**执行**证据仍未出现，故仍按 UNVERIFIED / open 处理。
- **未改**：批次 29 记录/研究稿中的历史时点计数（21/10/19 等）按补记惯例保留，只在关键"现状"落点加注；未把历史证据改写成现树数字。

---

## 附录 A：测试计数（现树）

命令：

```bash
cd "D:/smart mistake book"
for f in \
 core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterInstrumentedTest.kt \
 core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsParityInstrumentedTest.kt \
 core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainterPureTest.kt \
 core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterInstrumentedTest.kt \
 app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt \
 app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt; do
  printf '%-62s %s\n' "$(basename $f)" "$(grep -c '@Test' "$f")"
done
```

输出：

```
VisualDrawOpsPainterInstrumentedTest.kt                        24
VisualDrawOpsParityInstrumentedTest.kt                         11
VisualDrawOpsPainterPureTest.kt                                13
VisualDrawOpsPdfPainterInstrumentedTest.kt                     13   # 复核轮现树 = 16
VisualMeasureReplayPaintFlagsInstrumentedTest.kt               2
VisualJsIsolationInstrumentedTest.kt                           13
```

## 附录 B：PDF painter 行数

命令：

```bash
wc -l core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt \
      core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterPure.kt
```

输出：

```
  154 core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt
   92 core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainterPure.kt
  246 total
```

## 附录 C：批次 30 设备证据目录（本轨时点不存在；复核轮已落盘）

**写作时点（约 01:11）命令与输出**：

```bash
ls docs/research/evidence/2026-10-29-batch-30
# ls: cannot access 'docs/research/evidence/2026-10-29-batch-30': No such file or directory
```

**复核轮（2026-10-29）实测**：该目录已存在 **11 份 `.md`**（`00-index.md`、`step00`–`step08`、`step-aud08-page-ruling.md`），设备轨 02:25–02:41 落盘；结论见 `00-index.md` §2。

## 附录 D：KD-71 源文件状态

**写作时点（约 01:11）命令与输出**：

```bash
git status --porcelain -- \
  core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt \
  core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt
# （空输出 = 该时点两文件干净，无本批改动）
```

**复核轮（2026-10-29）实测**：T7 已落码，`git diff --stat` 为
`QuickJsVisualExecutionEngine.kt +20`、`QuickJsVisualExecutionEngineJvmTest.kt +58`（新增
`beforeRegistrationHookForTest` 与确定性竞态用例）；"无本批改动"只对 01:11 时点成立。

## 附录 E：step06 杀因记账重算（批次 29 raw log）

命令：

```bash
f=build/device-track-batch29/step02-isolation-logcat.txt
for s in "Start proc .*:visual_js" "warm-up finished" "visual_js warm-up failed" \
         "killing :visual_js worker" "the host unbound the visual_js worker" "isolated not needed"; do
  printf '%-45s %s\n' "$s" "$(grep -cE "$s" "$f")"
done
```

输出：

```
Start proc .*:visual_js                       14
warm-up finished                              0
visual_js warm-up failed                      12
killing :visual_js worker                     13
the host unbound the visual_js worker         1
isolated not needed                           2
```

判定：`killing` 13 = 12 warm-up fail + 1 `the host unbound`；`isolated not needed` 2 行是系统日志、不是杀因。**worker 收口 FAIL（14 spawn / 0 ready）判定不变。**

## 附录 F：本轨改动的 git 状态

命令与输出：

```bash
git status --porcelain -- docs/known-defects.md docs/current/visual-generation-contract.md \
  docs/research/2026-10-26-batch-24-4e2-admission-checklist.md \
  docs/research/2026-10-28-pdf-replay-differences.md \
  docs/research/2026-10-28-screen-pdf-parity-gate.md \
  docs/research/2026-10-28-batch-29-device-criteria.md \
  docs/research/evidence/2026-10-28-batch-29/step06-signatures.md
```

输出：

```
 M docs/current/visual-generation-contract.md
 M docs/known-defects.md
 M docs/research/2026-10-26-batch-24-4e2-admission-checklist.md
 M docs/research/2026-10-28-batch-29-device-criteria.md
 M docs/research/2026-10-28-pdf-replay-differences.md
 M docs/research/2026-10-28-screen-pdf-parity-gate.md
 M docs/research/evidence/2026-10-28-batch-29/step06-signatures.md
```

## 附录 G：残留旧计数的处置口径

- `docs/research/2026-10-28-batch-29-record.md`、`docs/research/evidence/2026-10-28-batch-29/*` 中的 21/10/19 是**批次 29 时点证据**，按补记惯例保留；可在其上读到"修复后未出数"的注记（step06、附录 G、KD-78/79 新块）。
- 任何"设备绿"只能引用为对应批次/源的结论，不得套到现树 24/11/13/13；批次 30 关闭 KD-78 ④ / KD-79 的唯一依据是新的设备复跑。
