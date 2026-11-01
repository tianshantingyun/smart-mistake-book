# E-05 屏/PDF 一致性原型门 11 例：`VisualDrawOpsParityInstrumentedTest`（**FAIL 1/11**，P07；原型门，非产品门）

| 字段 | 值 |
|---|---|
| evidence-id | E-05 |
| convention-version | 1 |
| batch | 2026-10-29-batch-30 |
| step | 五（一致性门 11 例；判据 §2.5） |
| gate-or-kd | T4 原型门 `scope=PROTOTYPE product-gate=no ci-gate=no release-gate=no`；**不得**引用为产品/发版结论 |
| verdict | **FAIL**：`Tests run: 11, Failures: 1`；红例 `thePrototypeGateJudgesEveryPositiveFixture`（P07 `F4_GEOMETRY`，`centroid=0.5181 px > 0.5 px`）。归因 **A（原型门未标定阈值 × KD-78 ④ 文本 AA 关的已知结构差）**；**未放宽任何阈值/断言** |
| executed-at | 2026-10-29T02:29:43+0800 与 02:31:41+0800（两次，设备 UTC 18:29:43 / 18:31:41） |
| operator | 设备验证轨（subagent，批次 30） |
| head | `a806525e` |
| worktree | dirty（他线在飞；本轨零写入；parity 测试与度量源相对 HEAD **未改**，`git diff` 空） |
| device-serial | `emulator-5562`（API 34 / x86_64 / PAGE_SIZE 4096） |
| apk | `data-debug-androidTest.apk` sha256 `cb88851b…4b1951`（设备侧一致） |
| apk-version | 0.2.0-offline |
| exit-code | 宿主 shell `EXIT=0`（**非判据**） |
| raw-log | `build/device-track-batch30/step05-parity.log` / `step05-parity-logcat.txt`（211 行）、`step05-parity-rerun.log` / `step05-parity-rerun-logcat.txt` |
| record-ref | 批次 30 记录（后写） |
| sanitized | 是 |

## 1. 命令

```bash
export MSYS_NO_PATHCONV=1
A=C:/Android/Sdk/platform-tools/adb.exe; S=emulator-5562
$A -s $S logcat -c
$A -s $S shell am instrument -w -r \
  -e class com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner \
  > build/device-track-batch30/step05-parity.log 2>&1; echo "EXIT=$?"
$A -s $S logcat -v threadtime -d > build/device-track-batch30/step05-parity-logcat.txt
# 复跑（同命令，文件名加 -rerun）确认非抖动
```

## 2. 结果（原样）

```text
Time: 0.657
There was 1 failure:
1) thePrototypeGateJudgesEveryPositiveFixture(com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest)
java.lang.AssertionError: P07 must not be red: F4_GEOMETRY reasons=[text bboxDev=1px centroid=0.5181329158118464px]
	at org.junit.Assert.fail(Assert.java:89)
	at com.tingyun.smartmistakebook.core.data.visual.VisualDrawOpsParityInstrumentedTest.thePrototypeGateJudgesEveryPositiveFixture(VisualDrawOpsParityInstrumentedTest.kt:106)

FAILURES!!!
Tests run: 11,  Failures: 1
```

复跑同一断言、同一数值（`0.5181329158118464`）、同一掩膜（`maskS=1224 maskP=1431 union=1442`），
**确定性红，非抖动**。

**覆盖后果**：正例循环在 P07 `fail()` 处中断，**P08（非整数缩放）本批未被判决**；P01–P06 在中断前
已有读数（P01/P03/P04/P05 `PASS`，P02 `F3_ALPHA_ROUNDING`（非红，允许类别），P06 `F2_AA`）。
这条是红的直接后果，登记为覆盖缺口，不以"P08 没红"当通过。

## 3. 套件级读数（判据 §2.5）

| 项 | 期望 | 实测 | 判定 |
|---|---|---|---|
| summary | `OK (11 tests)` | `Tests run: 11, Failures: 1` | **FAIL** |
| `PARITY_GATE scope=PROTOTYPE …` | 判据写"有且仅有一轮" | **11**（`@Before announcePrototypeGate` 每例打印一次 = numtests） | 判据该条与实现不符，登记为判据漂移，**不是产品问题** |
| `PARITY_ENV` 含 `replayTextPaintFlags=0` | 是 | `…|replayTextPaintFlags=0|replayPathPaintFlags=1|pxPerDu=4.0|ptPerDu=1.0` | PASS |
| `PARITY_CONTROL` 条数 | 10 | **10** | PASS |
| 每行 `red=true` | 全部 | 非 `red=true` 行数 = **0** | PASS |
| `PARITY_GATE_DESENSITIZED` | 0 | 0 | PASS（门未失去分辨力） |
| `PARITY_SYSTEMATIC_DRIFT` | 0 | 0 | PASS |
| `PARITY_F5` | ≤2 | 0 | PASS |
| `PARITY_DIAGNOSTIC_WRITTEN` | 绿轮 0 | **1**（P07 失败诊断 PNG） | 与红轮一致 |

## 4. 归因（判据 §7.2/§7.3/§7.5 五步）

1. **抓原样**：失败行、断言消息、stack、`Tests run:` 见 §2；raw log 完整保留。
2. **第一项目帧**：`VisualDrawOpsParityInstrumentedTest.kt:106`（正例循环 `fail()` 处）。
3. **四源核对**：
   - **测试期望**：`VisualDrawOpsParityMetrics.kt:847-848`（`MAX_TEXT_BBOX_DEVIATION_PX=1`、
     `MAX_TEXT_CENTROID_DELTA_PX=0.5`）；`THRESHOLD_VERSION="R2-2026-10-28/§4.2-initial-uncalibrated"`
     （`:185`）；F4 判定 `:556-562`。
   - **R2 设计文档**：`docs/research/2026-10-28-screen-pdf-parity-gate.md:206`（"阈值表（**初始值**，
     未在设备上标定）"）、`:214`（文本 T：质心 ≤ 0.5 px）、`:71`（文本栅格化：屏 **AA 关** + 度量
     hinting；写侧 AA 开 + hinting 关；栅格化 PDFium AA）、`:204`（文本 IoU 只报不判，因"屏侧
     aliased vs PDF 侧 AA，掩膜天然差一圈 ≈ 1 px"）。
   - **产品**：`VisualDrawOpsPainter.kt:54` `textPaint = Paint().apply { flags = REPLAY_TEXT_PAINT_FLAGS }`
     （0，AA 关；KD-78 ④ 修复的**预期**行为）；`VisualDrawOpsPdfPainter.kt:46-47`（文本"AA 关" vs
     查看器栅格化；抗锯齿**不可比**）。
   - **同设备前后对照（一手 raw log）**：
     - 批次 29（KD-78 ④ 修复**前**，textPaint=1283，AA 开）：P07 `maskS=1434 maskP=1431
       iou=0.9424 … centroid=0.44 mass=1.006 → F2_AA`（`build/device-track-batch29/step04-parity-logcat.txt:118-119`）。
     - 批次 30（修复**后**，textPaint=0，AA 关）：P07 `maskS=1224 maskP=1431 iou=0.8412 …
       centroid=0.52 mass=0.957 → F4_GEOMETRY`（`build/device-track-batch30/step05-parity-logcat.txt:121-122`）。
     - **屏掩膜 1434→1224（−210 px），PDF 掩膜 1431 不变**；P06 同向（`maskS 754→667`）。与"屏侧文本
       AA 关、PDF 侧仍由 PDFium AA 栅格化"逐项一致。
4. **分类 = A（原型门自身未标定阈值/门逻辑）**，排除法：
   - 非 **B**：质心 0.5 px 不是契约/生成门给出的权威值，而是 R2 §4.2 自述"初始值未标定"；
     把期望改成实测 0.518 就是**放宽**，判据 §7.3 禁止。
   - 非 **C**：产品行为与 KD-78 ④ 的显式钉死（AA 关、flags=0）一致，且 R2 自己把文本 AA 标为"不可比"；
     没有权威源说屏侧文本应开 AA。
   - 非 **D**：同设备（API 34/x86_64/4096）、新 APK（sha256 与 dex 门已核）、构建晚于源改动
     （`step00-environment.md` §2）。
5. **先红后绿闭环**：**未做**——本轨不改门阈值/门逻辑，也不改产品；绿轮**不存在**。
   归因成立但修复归属 R2 门所有者（裁定后重取设备绿轮）。

## 5. 处置（本轨）

- **零代码改动**：未改 `VisualDrawOpsParityMetrics.kt`、未改测试、未改 painter、未改 PDF painter。
- 待裁定（交 R2 原型门所有者）：在"KD-78 ④ 文本 AA 固定关"的前提下，文本类质心阈值是否按设备数据
  重新标定（或不把质心作为 CJK 夹具的判决面）；无论选哪条，都必须在 R2 文档改阈值表版本号后重跑。
- **本红例不阻断** KD-78 ④（其判据不含 parity）与 KD-79；但它使"五套件全绿"的验收条件**不成立**。

## 6. 未验证项

- **UNVERIFIED**：绿轮（阈值裁定后需重跑同命令）。
- 原型门的任何读数**不构成**产品/发版一致性结论（判据 §2.5-7）。

## 脱敏与去噪（R4 §4.4）

- 扫描：本批目录四条正则各 **0 命中**（命令与结果见 `00-index.md` §8）。补充手机号形态扫描在本文件 §2 命中同一长小数（P07 质心值），**非 PII**。
- 去噪：保留失败行、复跑同值与门分辨力计数；未删除 `FATAL`/`ANR`/`chatty` 行（本文件无此类行）。
- 二进制/截图：无。失败诊断 PNG 在设备上，未入仓。全量见头部 `raw-log`。
