# V-01 ④ PDF 腿方案设计：core:data internal 符号跨模块的合规暴露（2026-11-01，批次 38）

> 研究轨（判据预研，计划 `.zcode/plans/plan-2026-11-01-batch-38-triple.md` §1 F.21 任务 21）。**只读/设计**：零 Gradle、零设备命令、零代码改动、零 git 写、不动热区。
> 方法：本会话实读两份 V-01 套件全文 + core:data `visual` 包可见性逐文件 grep + 构建文件/台账。行号为本会话实读；未实测项一律标 **UNVERIFIED**。
> 背景：V-01 = PARTIAL 3/4（批次 37 收口；`docs/research/2026-10-31-v01-isolated-wiring.md` §2.3-4 的"app 可达 VisualDrawOpsPainter/VisualSnapshotRestore"可达性判断**已被落码实读证伪**）。

## 0. 一句话结论

④ PDF 腿（与屏栅格/restore 腿同根）卡在**一组 core:data `internal` 编译单元符号对 app androidTest 不可达**（本会话逐文件 grep 实证：painter/PDF/restore/parser/label 全家 internal，§1）。本文件给出三个候选暴露机制的结构设计与评分，**推荐方案 A：core:data main 内新增一个薄公开测试缝 `VisualChainTestSeams`（单新文件、零既有文件改动、公开面仅一个 object、不透出任何 internal 类型）**；替代：方案 B（`testFixtures` 源集，零 main 公开面但 AGP 9.3 组合 UNVERIFIED）、方案 C（直接可见性翻转，改动最小但永久扩公开 API，不推荐）。**所有方案共有的硬前提**：painter/restore 在生产链路零调用点 → 任何被 androidTest 引用的暴露符号在 R-04 release 轨都会被主包 R8 收缩出悬空引用（批次 35/36 已实证的机制②同型）→ 必须配 keep 规则（P-12③ 移交项同型先例）或等 Keeper 的 androidTest 用法推断 keep（任务 22 预研对象）。**回滚**：推荐方案回滚 = 删 1 新文件 + 删测试增量（+ 可选 keep 行），零 schema/契约/快照影响。

## 1. 失败事实：不可达符号面（本会话逐文件 grep 实证）

app 版 `V01VisualChainInstrumentedTest`（3 例，`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/V01VisualChainInstrumentedTest.kt`：A 生成腿、生产装配负控、链腿"提交→真实重开"回读——**无 PDF/无屏栅格/无 restore 断言**）补 ④ 需要的符号，全部是 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/` 的 internal 顶层声明：

| 符号 | 声明锚（本会话 grep 实读） | ④ 腿里的用途（core/data 变体用例 D，`:330-400`） |
|---|---|---|
| `VisualDrawOpsPdfPainter` | `VisualDrawOpsPdfPainter.kt:55` `internal class` | `writeFile(document, target, ptPerDu, labelContent)`（D `:337`）；D1∧D2∧D3 完整性由其内部强制 |
| `VisualSnapshotRestore` | `VisualSnapshotRestore.kt:181` `internal object`；`:33` record / `:100` `VisualRestorePolicy` / `:128` `internal sealed interface VisualRestoreOutcome`（Replay/ReadOnly） | `restore(record, previewVerified, policy)` 判定重放档位（变体 `:537-545` 断言 `Replay`） |
| `VisualDrawOpsParser` | `VisualDrawOpsParser.kt:50` `internal object` | drawCommandsJson → `VisualDrawOpsDocument`（变体 `validatedDocument` `:880`） |
| `VisualDrawOpsDocument` | `VisualDrawOps.kt:165` `internal data class` | painter/restore 的载荷类型 |
| `VisualLabelContent` / `VisualLabelLine` | `VisualDrawOpsPainterPure.kt:194` `internal sealed interface`（等） | label 解析器返回值（变体 `:562-572`） |
| `REPLAY_TEXT_PAINT_FLAGS` | `VisualDrawOpsPainterPure.kt:87` `internal const val` = 0 | 测试内现算 Paint 同构（KD-78 ④） |
| `VISUAL_RENDERER_IMPLEMENTATION_VERSION` | `VisualRendererVersion.kt`（internal；app 套件 KDoc `:777-785` 自证"跨模块 internal 不可引用，故此处镜像字面量"） | 提交时 rendererVersion 列 |

Kotlin 可见性规则：`internal` = 编译单元（模块）内可见；app 的 androidTest 对 core:data 是**另一个编译单元**（friend path 只覆盖同模块 main↔androidTest），故不可达。仓内先例核对：`app/src/main`/`core/data/src/main` **零** `@VisibleForTesting`、构建文件**零** `testFixtures`（grep 实跑）——本方案将是仓内首个跨模块测试缝，命名沿 `...ForTest` 语义族。

④ 腿在 core/data 变体的既有断言形状（迁移目标）：PDF 发布存在/非空/尾窗 `%%EOF`（`:346-353`）+ `PdfRenderer` 回读单页/页尺寸=⌈du×ptPerDu⌉/栅格非空白/**label 带墨**（`:357-393`）+ restore 判定 `Replay`（`:542-545`）。

## 2. 三个候选机制（评分表 + 结构设计）

评分口径：GitHub 优先纪律（license/维护度）不适用（纯仓内方案）；改按**触面大小 / 公开面宽窄 / 机制不确定性 / 回滚净度**。

| 方案 | 形态 | 触面 | 公开面 | 机制不确定性 | 评分 |
|---|---|---|---|---|---|
| **A 薄公开测试缝（推荐）** | core:data main 新增 1 个 `public object VisualChainTestSeams`，对外只出 opaque 签名（公共小值类型），内部转调 internal 类 | **+1 新文件**（零既有文件编辑） | 1 object + 2-3 个小 public data class（缝内定义） | 低（纯 Kotlin 可见性，无构建机制依赖） | **4.5/5** |
| B `testFixtures` 源集 | `core/data` 启用 java-test-fixtures，缝代码放 `src/testFixtures/kotlin`；app androidTest `testFixtures(project(":core:data"))` | core/data + app 两个 build.gradle.kts（均现干净）+ 1 新文件 | main API 零扩（fixtures 独立变体） | **UNVERIFIED**：AGP 9.3 × Kotlin 2.3.21 的 fixtures friend-path（fixtures 编译能否见 main internal）仓内无先例 | 3.5/5 |
| C 可见性翻转 | 把 §1 的 7 类 internal 顶层声明改 public | 5-6 个既有文件的 ~10 处关键字（各文件现干净） | 永久扩 7-10 个领域类型 | 低 | 3/5（公开面过宽） |

被否决路径（不展开，一句归因）：把 `VisualJsService`/AIDL 下沉 core:data 让变体在库模块测（架构倒置，触热区面更大）；等 4E.1 PDF 导出把 painter 变生产可达（时间不受本批控制，且导出批有自己的判据）。

### 2.1 方案 A 结构设计（推荐；签名形态给实现批参考，行数级为预估）

```kotlin
// core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualChainTestSeams.kt
// KDoc 必写三件事：① 消灭的失败（§0 的 3/4 卡点）；② 仅 androidTest 引用约束（配套 grep 门）；
// ③ R-04 release 轨需 keep（或 Keeper 自动推断）——P-12③ 移交项同型。
public object VisualChainTestSeams {

    /** restore 判定：入参用 core:database 公共读侧行（与生产读链同形，夹具零漂移），出参只回档位与原因。 */
    public fun restoreDecision(
        snapshot: com.tingyun.smartmistakebook.core.database.TutorVisualSnapshotRecord,
        verifiedFontManifests: Set<String>,
    ): RestoreDecision   // REPLAY(reason=…)/READ_ONLY(reason=…) 两个公共值

    /** PDF 写 + 页信息回读；入参 = drawCommandsJson + contentIds + label 三元组（app 测试自持）。 */
    public fun writeSnapshotPdf(
        drawCommandsJson: String,
        contentIds: Set<String>,
        label: TestLabelContent?,      // public 值类型：payload/sizeSp/fontScale/heightDu/lineLengths
        ptPerDu: Float,
        target: java.io.File,
    ): PdfWriteSummary                 // pageCount/widthPt/heightPt（公共值）；写失败按既有 PAINTER_PDF_* 码抛
}
```

- **不透出 internal 类型**（Kotlin 规则：public 签名不得暴露 internal 参数/返回类型）——`VisualDrawOpsDocument`/`VisualRestoreOutcome` 全部留在缝内。app 测试的断言改为用缝的公共返回值 + 既有公共面（drawCommandsJson 字节相等已在链腿钉住，`app` 套件 `:300-304`；PDF 页尺寸/墨迹断言只用 `PdfRenderer` 公共 API + 像素数组，与变体 `:357-393` 同型）。
- **入参对齐生产读链**：`TutorVisualSnapshotRecord` 是 `core:database` 公共类型（app 套件已在用，`:489`）；变体里"行→record 逐字段映射"（`:519-534`）由缝内完成，消除夹具映射漂移面。
- app 套件增量 = +1 例（`theRestoredDocumentWritesAPdfPassingIntegrityAndRendererReadback` 同语义迁移，3→4 例），复用真隔离引擎骨架（`runChainToCommittedReopen`），label 参数走 R1 §3.2-B 的"测试内现算"路径（引擎回传 contentIds + 测试内 Paint 现算），不需要也拿不到服务进程度量器。
- 覆盖差值（如实登记）：app ④ 不含变体 C 腿的"恢复文档重绘与重启前栅格逐位相等"（那需 `VisualDrawOpsPainter` 也入缝——本轮不纳入，理由：屏栅格相等断言的语义由变体承担，app ④ 的独有增量 = **真隔离引擎产出的 DrawOps 走 restore→PDF 全腿**）；若主会话要求对齐，缝加 `replayToPixels(...)` 一节即可，结构不变。

### 2.2 触面枚举（改一处先闭合，实施批逐项核对）

| 触面 | 现状（本会话实跑 `git status`） | 改动 |
|---|---|---|
| `core/data/.../visual/VisualChainTestSeams.kt` | 不存在（新增） | 新文件；core:data 其余 visual 文件**零编辑**（`DefaultFigureGenerationCoordinator.kt` 他线 M，本方案不触） |
| `app/.../visualjs/V01VisualChainInstrumentedTest.kt` | 干净 | +1 例 + label 现算助手（文件已 787 行，注意单文件膨胀） |
| `app/r04-test-keep.pro`（可选，仅当 R-04 release 轨要跑该例） | 干净 | +keep 行（缝类 + painter/restore 家族按名）；沿 P-12③ "internal seam 进 release 轨须补成员 keep"先例 |
| `tools/ci/check_visual_test_seams.py`（建议配套） | 不存在 | A-13 先例（`check_r04_keep_rules.py`）同型 grep 门：断言 `VisualChainTestSeams` 在 `*/src/main` 零引用（消灭"生产旁路直绘"这一具体未来失败） |
| 设备绿源快照 | — | core:data main 新文件 → 快照变化 → 绑定套件按纪律复跑；app ④ 为新增用例 → 必须重跑 |

### 2.3 R-04/release 轨相互作用（所有方案共有，提前写死）

- 主包 R8 输入不含 androidTest 源 → 只被 androidTest 引用的缝与 painter/restore 家族在 `strictOfflineRelease` 主包里不可达、可被收缩；release 轨测试 APK 按名引用 → 悬空 CNFE（批次 35 K6 机制②同型，`docs/research/2026-10-31-r04-k6-verify.md` §三）。debug 轨 `minifyEnabled=false` 不受影响。
- 处置二选一（实施批按 R-04 批次编排定）：① 显式 keep（`app/r04-test-keep.pro`，K4 门脚本 `tools/ci/check_r04_keep_rules.py` 纳入新行）；② 等 Keeper 接入后由其 androidTest 用法推断 keep 自动覆盖（任务 22 预研，AGP 9.3 兼容性见该文件）。**在二选一落地前，app ④ 不得进 R-04 release 轨用例清单**——这是登记项，不是可选项。

## 3. 回滚

- **单元**：删 `VisualChainTestSeams.kt` + 还原 app 套件增量（+ 删可选 keep 行/门脚本）→ 树回迁移前。生产代码零触碰（缝未被任何 src/main 引用——grep 门同时是回滚净度的机检）；无契约/schema/快照/DB 影响。
- **触发**（失败先归因）：app ④ 设备红且两轮修复仍无法归因夹具/环境 → 归因指向缝语义（如 restore 对真引擎产物的判定路径缺陷）→ 按阻塞登记回主会话，core/data 降级变体继续承担，V-01 维持 3/4 与现状等价；不删变体、不改判据。
- 中间态：只回滚 keep 行（若该行引发 K4 门红）不影响缝与用例；只回滚用例不影响缝（缝变孤儿——grep 门会红，提醒一起回滚或保留待用，实施批二选一登记）。

## 4. 决策点（归主会话）

1. **是否实施**：替代方案 = 不实施（V-01 维持 PARTIAL 3/4 + 变体降级标注，即批次 37 收口状态）。本研究的增量事实：暴露机制本身是**低成本、可回滚**的（方案 A 单新文件），卡点其实在 R-04 release 轨的 keep 前提与设备轨窗口，不在机制。
2. 方案 A/B/C 选型（本研究推荐 A；B 的 fixtures friend-path 需 15 分钟级沙盒验证后可升格）。
3. app ④ 是否同时纳入屏栅格腿（§2.1 覆盖差值）。
4. 缝命与门名（`VisualChainTestSeams`/`check_visual_test_seams.py` 为占位建议）。

## 5. 未验证项

| # | 项 | 状态 |
|---|---|---|
| U1 | 方案 B 的 AGP 9.3.0 × Kotlin 2.3.21 testFixtures friend-path | UNVERIFIED（沙盒构建归实施批） |
| U2 | 缝签名编译可行性（`TutorVisualSnapshotRecord` 字段完备性、PDF 异常码透出形状） | 设计稿，未编译 |
| U3 | app ④ 设备墙钟增量（真引擎冷启动 + PDF 写回读，估 +30–60 s） | UNVERIFIED，设备轨实测 |
| U4 | R8 对缝类 + painter 家族的收缩边界（keep 行的精确形状） | UNVERIFIED（需 K2 产物核，同 R-04 批） |

## 6. 来源（本会话实读/实跑）

- 两套件全文：`app/src/androidTest/.../visualjs/V01VisualChainInstrumentedTest.kt`（3 例，全读）；`core/data/src/androidTest/.../visual/V01VisualChainInstrumentedTest.kt`（4 例，`:283-553` 精读 C/D 腿与骨架）。
- 可见性 grep：core/data `visual` 包 10 文件顶层声明（§1 表）；`VisualLabelContent` 定位（`VisualDrawOpsPainterPure.kt:194`）；`VisualRestoreOutcome`（`VisualSnapshotRestore.kt:128`）。
- 构建/纪律面：`core/data/build.gradle.kts:169-208`（依赖区）、`app/build.gradle.kts`（插件区 :1-5）；grep `testFixtures`（构建文件 0 命中）、`VisibleForTesting`（main 0 命中）；台账 `docs/REFACTOR-MASTER-LINE.md:4`（批次 37 收口段：R1 §2.3-4 证伪登记、P-12③ 移交项）；`docs/research/2026-10-31-r04-k6-verify.md` §三/§五（机制②、Keeper 评分）；契约 `docs/current/visual-generation-contract.md` :208 区域（"PDF 复用 core:data `visual` 包共享 `VisualDrawOpsPainter`"——长期形态注记，本轮未触该热区）。
