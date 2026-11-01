# 批次 39 基线核对（2026-11-01）：接线与设备校准批 §0/§1 逐项复核

> 基线轨（只读核对，零 Gradle/零设备写/零 git 写）。依据计划 `.zcode/plans/plan-2026-11-01-batch-39-wiring-calibration.md` §0/§1 逐项复核；每项给出命令与 `path:line` 证据。与计划 §0 不符处以 **§10 不符与注记** 汇总。

## 0. 核对方式

全部结论来自本会话实跑命令（Git Bash，`adb` 用 `/c/Android/Sdk/platform-tools/adb.exe`）与实读文件；转录他文档处逐条注明来源。未跑任何构建/测试（基线轨纪律），涉及执行状态的面一律记 UNVERIFIED。

## 1. 一 · HEAD / origin/main / 工作树 / DB schema

| 项 | 实测 | 命令/证据 |
|---|---|---|
| HEAD | `b95451c99a4e14e46c54ce89a87895887a140c30`（批次 38 收口提交，与计划 §0 记值一致） | `git rev-parse HEAD` |
| origin/main（本地引用） | `147c4b9feb8d8830c695ab216f3ae14b04d4fdc0`（`fix(kb): 批次30 收口…`）；`origin/main..HEAD` = **8 commits** | `git rev-parse origin/main`；`git rev-list --count origin/main..HEAD`；`git log --oneline -1 origin/main` |
| 工作树计数 | **712 条** = ` M` **223** + `??` **490**；暂存区 **0**（staged file count=0） | `git status --porcelain \| wc -l`；`awk '{print $1}' \| sort \| uniq -c`；`git diff --cached --name-only \| wc -l` |
| DB schema | **71**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139` `internal const val STUDY_DATABASE_VERSION = 71`（`@Database` `:148`、`version = STUDY_DATABASE_VERSION` `:242`）；`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json` 在树（同目录最新，次新 70/69/68） | `git grep -n "STUDY_DATABASE_VERSION"`；`ls core/database/schemas/…` |

- 计划 §0"推送 443 后台重试中"：本地远端跟踪引用停在批次 30 收口提交、HEAD 领先 8 commits——**推送进程本身 UNVERIFIED**（本轨只读引用状态，不可观测网络推送），与"本批开工不依赖远端"不冲突。
- 工作树大脏面（712 条）与批次 38 开工时点（786 条 = 246 M + 480 ??，`docs/research/evidence/2026-11-01-batch-38/step00-environment.md` 头表）同型——**他线在飞是本仓常态**，批次 39 硬约束"commit 前逐路径核对暂存区"必须执行。

## 2. 二 · 热区快照（含 RoomTutorToolRunner.kt 状态）

热区定义沿批次 38 计划 `:22`（core/domain、settings.gradle.kts、core/export、core/ui、feature/*）。本会话实跑 `git status --porcelain` 逐区：

| 热区 | 现状态 | 明细 |
|---|---|---|
| core/domain | **脏** | 4 M（BatchImportRepository / MistakeDetailRepository / SchedulingEvaluation / TutorJudgedReview）+ 1 ??（`core/domain/gradle.lockfile`） |
| settings.gradle.kts | **脏** | 1 M |
| core/export | **脏** | 6 M（build.gradle.kts、MistakeExportJobRunner.kt、MistakePdfEligibilityTest / MistakePdfPagePlannerTest / MistakePdfPlannerGoldenTest 等） |
| core/ui | **脏** | 35 M（含 build.gradle.kts、AiReplyRichMarkdown、AttachedImageCard、BoundedLocalImage、Components 等） |
| feature/* | **脏** | M 96（feature/tutor、feature/library、feature/review、feature/capture、feature/profile 各有；porcelain 总行含 ?? 为 134） |
| **core/data/…/study/RoomTutorToolRunner.kt** | **干净（非 M）** | `git status --porcelain -- <该文件>` 零输出；`RoomTutorToolRunnerTest.kt` 同干净 |

**P-11/P-12③ errorKind 半边前置判定（计划 §1.3 待核项）**：runner 文件**已清空**（不再处于他线 M 面）→ 按计划 §1.3 条件"若已清空则落类型化 errorKind"，**半边可落**。文件现状：`errorKind` 命中 **15 处**（`RoomTutorToolRunner.kt:346` `"failed"`、`:622` `"no_tutor_session"`、`:632` `"reference_not_found"`、`:643` `"not_ready"`、`:990` `"invalid_knowledge_code"`、`:1013` `"rejected:missing_semantics"` 等，均为字符串字面量）——类型化半边未落，现值为字符串 kind。

其余热区外脏面参考（批次 38 T13 同口径）：core/data 他线 M 面、docs/ 45 M、app/ 24 M、tools/ 4 M、knowledge-production 1、.gitignore 1（本会话 M 分布实测）。

## 3. 三 · D-04 生产接线缝

| 缝 | 现状 | 证据 |
|---|---|---|
| `previewAssetId` 透传点 ① | `TutorVisualSnapshotInput.previewAssetId: String? = null`（**`:103`**，与计划记值一致） | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualCommitRepository.kt:103` |
| `previewAssetId` 透传点 ② | `toWrite()` 展开内 `previewAssetId = previewAssetId`（**`:529`**，与计划记值一致）；stage（`stageSnapshot` `:253`，S2+S3）与 commit（`commitAssistantTurnWithVisual` `:298`，S4）两条路径共用 `toWrite` → 透传缝覆盖提交路径；pin/指针 CAS 路径 `repointMessageVisual` `:342`（不携带 preview 载荷，只换指针） | 同文件 `:253`/`:298`/`:342`/`:492-533` |
| 生产调用方 | **无**：`TutorVisualPreviewAssetPersister` 全仓命中仅测试（`TutorVisualPreviewAssetPersisterTest.kt:25/35/61/83`、`BackupVisualReferenceInstrumentedTest.kt:11/1313/1319`）+ 契约注记（`DatabaseContract.kt:131`）；`previewAssetId` 非测试命中全为 schema/write-side 管道，**生产提交路径恒 null** → 计划 §0"previewAssetId 无调用方"证实 | `git grep -n "TutorVisualPreviewAssetPersister"`；`git grep -n "previewAssetId" \| grep -iv test` |
| `TutorVisualPreviewAssetPersister.forProduction` 签名 | `fun forProduction(context: Context, database: StudyDatabasePort): TutorVisualPreviewAssetPersister`（companion，**`:68-71`**）；`persist(pngBytes: ByteArray, createdAtEpochMillis: Long): CanonicalSourceAssetRecord`（`:49`），内部走 `AndroidCanonicalAssetVault.persistCleanImageBytes` + `database.registerCanonicalSourceAsset`，sourceType=`StudyDbValue.SourceAssetType.LOCAL_VISUAL_PREVIEW`（`:56`） | `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/TutorVisualPreviewAssetPersister.kt:49`/`:56`/`:68-84` |
| 预览渲染输入 | `TutorVisualSnapshotInput.drawCommandsJson: String`（**`:87`**，KDoc"DrawOps V1 文本（重放唯一输入）"）已有——预览渲染的输入面在快照输入类型上现成；S0 侧 `drawCommandsJson` 受 `MAX_TRANSACTION_JSON_BYTES` 门（`:379-384`） | `TutorVisualCommitRepository.kt:87`/`:377-384` |

接线落点结论：D-04 生产接线 = 在提交/pin 路径装配 `forProduction` 产出 previewAssetId 并传入 `TutorVisualSnapshotInput`；输入（drawCommandsJson）与写侧三步收口（落字节→登记行→传 id）载体全部在树，缺的只是**装配调用方**。

## 4. 四 · M-03 16MiB 接线点

- **`requireBitmapBytesWithinBudget` 签名**：`fun requireBitmapBytesWithinBudget(widthPx: Int, heightPx: Int, bytesPerPixel: Int)`（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualLayoutBudget.kt:114`）；常量 `MAX_BITMAP_BYTES = 16_777_216L`（`:58`）、拒绝码 `BITMAP_BYTE_BUDGET`（`:67`）；KDoc 明示"分配不发生——恰达上限不拒"（`:108-128`）。
- **预览渲染的 createBitmap 分配点**：**尚不存在**。`git grep -n "createBitmap\|compress(" -- core/data/src/main/kotlin/…/visual/` 仅命中 `VisualLayoutBudget.kt` 的 KDoc 三处（`:18`/`:39`/`:109`）——生产 visual main 无任何 bitmap 分配/编码点。KDoc 自述（`:39-41`）："预览分配点：D-04 轨的 createBitmap 前调用 requireBitmapBytesWithinBudget（本批提供纯门，接线归 D-04）"。
- 结论：M-03 16MiB 门的接线点与 D-04 接线是**同一点**——预览渲染路径本身随 D-04 新建，`requireBitmapBytesWithinBudget` 在该新路径 createBitmap 前调用；纯门与 4MP 门（引擎侧 `QuickJsVisualExecutionEngine.kt:418-421` 已接线，台账补记 15 转录）现状分离。

## 5. 五 · KD-76 计时 seam 与 READY_TIMEOUT

- **`connectStageTimestampsForTest`：全仓 0 命中**（`git grep -n "connectStageTimestampsForTest" -- "*.kt"` 零输出）→ 计时 seam 未落，本批新增落 `VisualJsClientEngine`（计划 §1.5）。
- 落点文件 `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt` **git 干净**（`git status --porcelain` 零输出）——非热区，可改。
- **READY_TIMEOUT 常量行**：`VisualJsClientEngine.kt:1251` `const val READY_TIMEOUT_MILLIS: Long = 10_000L`（10 s，与计划 §1.6"READY_TIMEOUT=10s"一致）；消费点 `:810` `readyBudgetMillis(): Long = readyTimeoutOverrideForTest ?: READY_TIMEOUT_MILLIS`；KDoc `:73`（ready 轮询预算独立于 attach 预算与宿主 2500 ms）。
- 既有邻近 seam：`unavailableForTest()` `:1174`（KD-76⑤ 锁存位观测）、`readyTimeoutOverrideForTest: Long?` `:1183`（ready 预算覆盖，P-04 重建 10s 例用）——新计时 seam 与它们同族。

## 6. 六 · A-10 / X-02 / X-03 向量缺口登记原文

**A-10 协调器级跨 method**（三处登记互相一致）：
- 批次 38 证据索引：`docs/research/evidence/2026-11-01-batch-38/00-index.md:64`——"**A-10 协调器级"IMAGE+JS 混合合计第 3 份拒绝"向量**：仍无直接协调器级测试（清单登记口径维持）；JVM `FigureMethodSwitchTest`（V-02 轨件）+ 本批 E-11 设备位共同守卫，混合合计向量扩例不在本轨。"
- 批次 38 记录 `docs/research/2026-11-01-batch-38-record.md:291`——"**A-10 协调器级"IMAGE+JS 混合合计第 3 份拒绝"向量**仍无直接协调器级测试（JVM FigureMethodSwitchTest + E-11 设备位共同守卫）。"
- 清单行：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:199`（A-10 行现状格）——"跨 method 合计最终图无直接协调器级测试（覆盖缺口登记）"。
- **重要现状注记**：批次 38 记录 `:159`（§3.9）——JVM 预算门边界套件 `FigureGenerationLocalBudgetBoundaryTest` **5→6 例**已含"同 operationId 先 JS（第 1 份）后 IMAGE（第 2 份）后第 3 份 IMAGE 在开后端/预留/通道/进引擎前拒（四计数器逐一钉死）"；缺的是**协调器级**（经 `DefaultFigureGenerationCoordinator` 全路径）用例，非门本体。计划 §1.7 落例时应区分这两层，避免重复建门层用例。

**X-02 设备向量缺口**：
- 批次 38 记录 `:62`（R3 转录）——"**X-02**：两符号全仓 0 命中维持（开工时点）；可挂载体已定位（`FigureSourceEvidence`/账本 reservation/attempt 回写点）；真缺口=`TutorQuestionContext`（feature/tutor 热区）无原题资产字段、Respond 只发 studentImageAssetRefs。"
- 清单行 `:241`（补记 15 刷新后）：落码半边（`VisualSourceBasis` + seen 观测 + 协调器 `:550` 接线）在树；"Plan 侧缺口维持 = `TutorQuestionContext` 无原题资产/canonical 引用字段（feature/tutor 禁改热区）"；验收用例列 = "**缺源/错图/SHA 变化/授权不足/预算不足**（JVM/设备）"——计划 §1.8 设备面向量的五个名字即此。
- 缺口定性：源证据载体/三门在树（JVM），设备面与 Plan 侧（热区）双缺。

**X-03 设备向量缺口**：
- 批次 38 记录 `:63`——"**X-03**：strictOffline 现状=构建变体；静态冷恢复半边已落码；本地 JS attempt `reservation_id=NULL`+operation 0/0 是"0 远程派发"的现成正向证据位；**缺口=①档交互重布局的 strictOffline 设备向量**。"
- 清单行 `:242`（补记 15 刷新后）：`StrictOfflineVisualRelayout` + JVM 门在树；"**缺口** = 交互重布局/拖动/播放设备向量（①档"API/SDK/运行库兼容门"现值判定 UNVERIFIED…）"。

## 7. 七 · V-01④ PDF 腿方案（T10 文档）与 internal 符号清单

方案文档：`docs/research/2026-11-01-v01-pdf-leg-exposure-design.md`（112 行，批次 38 T10 落盘）。原文要点（本会话实读）：
- **推荐方案 A**（§首页，评分 4.5/5）：core:data main 新增**薄公开测试缝 `VisualChainTestSeams`**（单新文件、零既有文件改动、公开面仅一个 object、不透出 internal 类型）；替代 B（`testFixtures` 源集，AGP 9.3 friend-path **UNVERIFIED**，3.5/5）、C（直接可见性翻转，永久扩公开面，3/5 不推荐）。
- **共有硬前提**（§首页原文）："painter/restore 在生产链路零调用点 → 任何被 androidTest 引用的暴露符号在 R-04 release 轨都会被主包 R8 收缩出悬空引用（批次 35/36 已实证的机制②同型）→ 必须配 keep 规则…或等 Keeper 的 androidTest 用法推断 keep（任务 22 预研对象）。"
- **回滚**："删 1 新文件 + 删测试增量（+ 可选 keep 行），零 schema/契约/快照影响。"

internal 符号清单（§1 表，本会话逐条 grep 复核锚点**全部与现树一致**）：

| 符号 | 现树锚点（实读核对） |
|---|---|
| `VisualDrawOpsPdfPainter` | `VisualDrawOpsPdfPainter.kt:55` `internal class`（写入口 `writeFile(document, target, ptPerDu, labelContent)`） |
| `VisualSnapshotRestore` | `VisualSnapshotRestore.kt:181` `internal object`（record `:33` / `VisualRestorePolicy` `:100` / `VisualRestoreOutcome` `:128`） |
| `VisualDrawOpsParser` | `VisualDrawOpsParser.kt:50` `internal object` |
| `VisualDrawOpsDocument` | `VisualDrawOps.kt:165` `internal data class` |
| `VisualLabelContent` / `VisualLabelLine` | `VisualDrawOpsPainterPure.kt:194` `internal sealed interface` |
| `REPLAY_TEXT_PAINT_FLAGS` | `VisualDrawOpsPainterPure.kt:87` `internal const val = 0` |
| `VISUAL_RENDERER_IMPLEMENTATION_VERSION` | `VisualRendererVersion.kt:20` `internal const val = "visual-renderer-v1"` |

批次 37/38 状态基线：app 版套件 3 例（无 PDF/无屏栅格/无 restore 断言）设备 3/3 绿；④ 无设备证据 → 严格口径 V-01 = PARTIAL（3/4）（清单 `:221` 补记 12 注记原文转录）。

## 8. 八 · KD-72 六 build 面 / 清单行原文 / 契约 :208

**KD-72 六 build 面（W1）现状态**——本会话逐文件 `git status --porcelain` 实跑：

| 文件 | 状态 |
|---|---|
| `settings.gradle.kts` | M |
| `core/ui/build.gradle.kts` | M |
| `feature/tutor/build.gradle.kts` | M |
| `core/export/build.gradle.kts` | M |
| `.gitignore` | M |
| `core/markdown/build.gradle.kts` | ?? |

→ **6/6 不干净，锁窗口仍不满足（分支 B 维持）**。锁面实测：`git ls-files '*lockfile'` = **0**（跟踪锁 0）；`git status --porcelain | grep -ci lockfile` = **13**（未跟踪）。最近登记：`docs/known-defects.md:2794`（批次 36 T6 全 W1–W7 实跑判分支 B）；批次 38 T11（`docs/research/2026-11-01-batch-38-record.md:172-175`）复跑同判"分支 B——锁窗口不可行…零动作，KD-72 保持 open"；KD-72 状态 open（`docs/known-defects.md:2758` 登记、`:2774` 四剩余条件）。

**清单行原文**（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`，本会话 Read 实读）：
- **V-02（`:222`，补记 15 刷新）**："**落码半边（JS→IMAGE 机械切换…）**——调用侧编排 `FigureMethodSwitchRunner`…+ JVM `FigureMethodSwitchTest`（7 @Test…）在树；**生产接线无调用方**（本会话 grep 全仓 `generateWithMethodFallback` 非测试源集 0 命中，X-01 归位裁定待主会话…）；IMAGE→JS 维持模型回合驱动设计（R1）；M-03×V-02 交互…双侧登记…行为裁定开放归主会话；LAYOUT_PIXEL_BUDGET 场景专项用例未落…"
- **A-09（`:198`，补记 15 刷新）**："**已落码 + 设备证据落盘…**——`VisualJsAdvertiseThenExecuteInstrumentedTest.kt`（app androidTest，注入式三例）在树；设备轨证据…step21b 同 APK 复跑 **OK (3 tests)** 判 PASS——正例四断言位…+ 两负控…；A-05 生产双就绪位仍 `false/false`（`RoomModelTaskRepository.kt:964-965`…）→ 生产广告面未开不变"
- **D 组（`:168`/`:170`）**：D-04 行——"**部分落**…`LOCAL_VISUAL_PREVIEW` 常量已落…+ 预览 PNG 写侧载体 `TutorVisualPreviewAssetPersister`…在树、生产接线未见…**缺口收窄（v3 补记 15 再收）** = 预览写侧生产接线 + 备份/恢复 suite 预览向量"；D-06 行——"**部分落**：显式再生成接线已测…cache-only resolver 半边已落码…0 派发观测门现状 = `FigureHttpTransportBypassObservationTest` **7 @Test**（补记 14 时点；补记 16 已实测 10 @Test 含离线打开三臂）…"
- **A-09 验收列**责任轨 = `后续-W` + `24-设备`；计划 §1.13 台账回填的"V-02 设备半边/A-09/D 组行刷新"即以上三处行内注记的下一轮滚动。

**契约 :208 现状**：`docs/current/visual-generation-contract.md:208` 本会话 `sed -n '208p'` 实读——KD-81 快照身份边界句**仍在树**（"`source.db.sha256`…**不含 `-wal`/`-shm`**…不得读作完整快照身份"），与批次 35 KD-81 关闭裁定（db+wal 进身份、`-shm` 排除）的矛盾未回改；`git status --porcelain -- docs/current/` = **7 条 M**（README/architecture-contract/data-schema/implementation-status/model-task-contracts/product-contract/release-gates，与批次 36–38 各补记记值同集合）→ **热区未清空，:208 维持登记分支不落笔**（计划 §1.13"契约 :208（若热区清空）"条件当前不成立）。

## 9. 九 · 设备在线状态（只读）

`adb devices -l` 实跑（2026-11-01 本会话）：

| serial | 型号 | 用途口径（沿批次 38 E-00 §2，本轨未发任何设备写命令） |
|---|---|---|
| emulator-5562 | sdk_gphone64_x86_64 | 批次 35–38 设备轨专用机（API 34/x86_64/4KB） |
| emulator-5566 | sdk_gphone64_x86_64 | 他线禁碰 |
| emulator-5584 | sdk_gphone64_x86_64 | 他线禁碰 |
| emulator-5596 | generic_x86_64（API 23） | 不跑套件（R-01 面另测） |

4 台在线，与批次 38 开工设备集一致；本轨仅此一条只读查询。

## 10. 与计划 §0/§1 不符处与注记

1. **P-11 vs P-12③ 编号差**：计划 §1.3 称"P-11 errorKind 半边"；台账补记 15（清单头部）与批次 38 记录同面均称"P-12③ errorKind 半边（runner 热区）"（清单 P-11 行 `:135` 行义是 `[UNATTRIBUTED]` 诊断跨进程保留，非 errorKind）。实施时台账回写应按 **P-12③** 对行，避免误刷 P-11 行。
2. **P-11/P-12③ 前置已解**：计划 §0 列"runner 热区状态待核"——实测 `RoomTutorToolRunner.kt` **已清空（git 干净）**，按计划 §1.3 条件 errorKind 半边**可落**；文件内 `errorKind` 现为 15 处字符串字面量（§2）。
3. **A-10 计划表述与现树差**：计划 §1.7"同 operationId 先 JS 后 IMAGE 第 3 份拒（协调器级用例）"——该场景在 **JVM 预算门边界层已落**（`FigureGenerationLocalBudgetBoundaryTest` 6 例，批次 38 记录 `:159`）；真正缺口是**协调器级**用例（§6）。扩例时应落协调器层、不重复门层。
4. **推送状态 UNVERIFIED**：本地 origin/main 引用停在 `147c4b9f`（批次 30 收口），HEAD 领先 8 commits；"443 后台重试中"的推送进程本轨不可观测，仅引用状态如实记录。
5. **热区"禁改"约束的现状语义**：五个热区在飞 M 面（§2）是**他线在飞改动**（批次 38 开工即如此，786 条），非本批违规——批次 39 的"热区禁改"落为：不新增触碰 + commit 前逐路径核对暂存区（该纪律在 712 条脏树上执行时逐路径核对是唯一防线）。
6. **D-04 与 M-03 接线同点**：预览渲染 createBitmap 分配点尚不存在（§4），16MiB 门接线随 D-04 新路径落地，计划把两者分列 §1.1/§1.2 两项——实施时是同一条渲染路径上的两个门（16MiB 前置 + previewAssetId 产出），共享输入 `drawCommandsJson`。

## 11. 本轨命令清单（可复跑）

```bash
git rev-parse HEAD && git rev-parse origin/main && git status --porcelain | wc -l
git rev-list --count origin/main..HEAD
git status --porcelain | awk '{print $1}' | sort | uniq -c
git diff --cached --name-only | wc -l
git grep -n "STUDY_DATABASE_VERSION" -- "*.kt"        # StudyDatabase.kt:139
ls core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/
git status --porcelain -- core/domain/ settings.gradle.kts core/export/ core/ui/ feature/
git status --porcelain -- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/RoomTutorToolRunner.kt   # 空=干净
git grep -n "TutorVisualPreviewAssetPersister" ; git grep -n "previewAssetId" -- "*.kt" | grep -iv test
git grep -n "requireBitmapBytesWithinBudget" ; git grep -n "connectStageTimestampsForTest" -- "*.kt"   # 后者 0 命中
git grep -n "READY_TIMEOUT" -- "*.kt"
git ls-files '*lockfile' | wc -l ; git status --porcelain | grep -ci lockfile
sed -n '208p' docs/current/visual-generation-contract.md ; git status --porcelain -- docs/current/
/c/Android/Sdk/platform-tools/adb.exe devices -l
```

（基线轨零 Gradle、零设备写、零 git 写；§7 internal 锚点逐条 grep 核对命令同上族。）
