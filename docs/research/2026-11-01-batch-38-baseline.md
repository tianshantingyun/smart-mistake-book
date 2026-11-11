# 批次 38 基线核对（2026-11-01）

> 核对人：基线核对工程师（子代理，只读核对：零代码改动、零 Gradle、零设备写）。
> 方法：全部结论来自本会话实读文件与实跑命令，命令与关键输出随条记录；无当前证据的结论一律标 **UNVERIFIED**。
> 对照计划：`.zcode/plans/plan-2026-11-01-batch-38-triple.md` §0/§1（下称计划 §0/§1）。
> 清单：`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`（v3，69 行，下称**清单**；行号 `:NNN` 为该文件行号）。

## 0. 一句话结论

计划 §0 主干与现树相符：HEAD=`dcfdc5b5`（与 origin/main 本地引用同值）、DB schema=71、KB 线 75 文件随批次 37 上远端（record §10.1）、KD-77/80/81 closed、KD-82 open（imagePrompt 残余，台账明文"残余归批次 38"）、R-04 阻塞维持、V-01 PARTIAL（3/4）。**与计划 §0 不符/计划未记的四点**：① 计划 §0 未记工作树计数——现树 **726 条（480 `??` + 246 `M`）**，热区（core/domain、settings.gradle.kts、core/export、core/ui、feature/、docs/current）**全线非净**，均是他线在飞 diff（settings.gradle.kts 已含他线 `+:core:markdown` 一行——正是 R-04 解锁要触的文件）；② fetch 滞后：`.git/FETCH_HEAD` mtime 仍 Oct 27 00:39，远端是否有新提交 **UNVERIFIED**；③ 清单 **D-06/V-03 行现状已滞后于现树**（cache-only resolver、checkpoint/restore 半边已落码），批次 38 台账轨（计划 §1 G.24）回填时应按现树改写；④ A-12 的 golden 扩测 7 例已在树（v3 补记 10），批次 38 计划 §1 E.17 "A-12 扩测" 的落点应按行内缺口重新定位。另：**knowledge 面现归 KB 线维护=禁改**（record §10.1 归属条款）。

## 一、HEAD、origin/main、工作树、DB schema

| 项 | 结果 | 证据（本会话实跑） |
|---|---|---|
| HEAD | `dcfdc5b52f01cb67c1d0f88375bcc092a8f54d9a` | `git rev-parse HEAD`；`git log --oneline -3` = `dcfdc5b5`（批次 37 §10.1 登记）← `f18a09da`（决策台账节）← `fc8d3768`（批次 37 研究/判据/基线/设备证据） |
| origin/main（本地引用） | `dcfdc5b52f01cb67c1d0f88375bcc092a8f54d9a`，与 HEAD 逐字节同值；`git rev-list --left-right --count origin/main...HEAD` = `0 0` | 实跑。与计划 §0 "批次 37 已收口推送 `dcfdc5b5`" 一致 |
| fetch 新鲜度 | `.git/FETCH_HEAD` mtime = **Oct 27 00:39**（`ls -l` 实读）——批次 37 基线同款保留项，仍未 fetch；远端是否已有新提交 **UNVERIFIED**（本轨只读，未 fetch） | 实跑 |
| 工作树计数 | **726 条 = 246 `M` + 480 `??`**（`git status --porcelain \| wc -l` = 726；按状态列 awk 计数）。M 内容量：`git diff HEAD --stat` = **246 files changed, 6366 insertions(+), 3247 deletions(-)**（真实内容改动非换行噪音；git 另有 CRLF 提示若干，未计入计数）。顶层分布（前几名）：feature/tutor 59、feature/library 43、core/data 38、docs/research 35、core/ui 35、app/src 27、feature/capture 16、core/database 15、tools/kb_coverage 14 | 实跑。计划 §0 未记工作树计数；批次 37 record §10.1 时点值为 738（其文内引用），现值 726，滚动多线在飞属常态 |
| DB schema | **71**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139` `internal const val STUDY_DATABASE_VERSION = 71`（grep 实读）；`StudyDatabase.kt:242` `version = STUDY_DATABASE_VERSION`；该文件 `git status --porcelain` 干净（未被他线 diff 卷入） | ✅ 与计划 §2 "DB schema 71" 一致 |

## 二、热区快照（含 KB 线归属）

热区定义（计划 §2）：core/domain、settings.gradle.kts、core/export、core/ui、feature/*。本轮实跑 `git status --porcelain -- <热区路径>`：

| 热区 | M 计数 | 备注（本会话实跑） |
|---|---|---|
| core/domain/ | 5 | BatchImportRepository / MistakeDetailRepository / SchedulingEvaluation / TutorJudgedReview 等（他线在飞） |
| settings.gradle.kts | 1 | diff 唯一 hunk = `include(` 列表 **`+ ":core:markdown"`**（`git diff HEAD -- settings.gradle.kts` 实读）——他线（markdown 线）在飞；该文件同时是 R-04/Keeper 解锁要触的热区，批次 38 预研不得动它 |
| core/export/ | 6 | build.gradle.kts（+3）+ MistakeExportJobRunner + 3 个 PDF 测试 |
| core/ui/ | 35 | 含 build.gradle.kts（+2/−1）与 Components/Theme 等面 |
| feature/ | 134 | tutor 59 / library 43 / capture 16 / review 12 / profile 等 |
| docs/current/ | 7 | README / architecture-contract / data-schema / implementation-status / model-task-contracts / product-contract / release-gates——与清单 v3 补记 9/11 时点同集合；契约正文禁改纪律维持 |

**KB 线 75 文件归属（禁改依据）**：`dcfdc5b5`（批次 37 record §10.1）登记：`1912180a..HEAD` 实际提交 91 路径，约 75 个为 KB 线（另一会话）已暂存未提交成果——知识包资产（`core/data/src/main/resources/knowledge/*`、dense 向量）、`tools/kb_build`/`tools/kb_coverage`/`tools/ci/check_pack_sync.py`、core/data+core/database 的 knowledge 面（含 RoomStudyDatabase.kt）、`docs/kb-batch26~29` 等；**处置=保留不回滚，提交归属以 §10.1 为准**。本轮实跑：`core/data/src/main/resources/knowledge/` 现树仍有 6 条 M（KB 线在飞滚动）；`core/database/` 15 条 M（DenseRecall/RoomKnowledgeBaseStore/KnowledgePort 等 KB 面为主）。**批次 38 全轨禁改 knowledge 面与 `tools/kb_*`。**

锁文件面（KD-72 关联，详见 §七）：13 条 `??` 锁条目（`git status --porcelain | grep -i lockfile` 实跑：app、core×6、feature×5、settings-gradle.lockfile；`core/markdown/` 整目录未跟踪故其锁被折叠）；`git ls-files "*.lockfile"` = **0**（无已跟踪锁）。

## 三、清单行原文 vs 现树（V/Q/P 组）

> 行原文为清单 v3 现状格摘录（`:NNN` = 清单行号）；现树列为本轮 grep/ls 实证。

| 行 | 行原文（现状格，摘） | 现树核对（本会话实跑） |
|---|---|---|
| V-02（`:221`） | **未落**："方法切换：JS 失败 → IMAGE_MODEL、图像不佳 → JS；同 operationId、不新增确认、预算不重置、无双重载荷"；缺口=切换用例（契约 §1 `:11`、§7 `:155`） | ✅ 未落维持：`core/data/.../visual/` 测试目录 grep `switch/fallsBack` 0 命中（method 切换用例不存在）。注意：A-10 面已有"同 operationId 换 method 计数不重置"设备断言（清单 `:198` 批次 36/37 注记），但 JS↔IMAGE **切换语义**用例仍无——两行口径不混同 |
| V-03（`:222`） | **未落**："保存题/删除会话/GC：删会话不删保存题图；固定引用不回收；进程重建读最近 checkpoint；strictOffline 可重放已持久程序且 0 派发" | ⚠️ **行已滞后**：checkpoint/GC 与恢复门半边已落码——`core/data/.../visual/VisualCheckpointRetention.kt` + `VisualCheckpointRetentionTest`（8 例：初始/最新/钉住保留/未钉逐出，grep `fun \`` 实读）+ `VisualSnapshotRestore.kt` + `VisualSnapshotRestoreTest`（12 例：staging 不回放 `:241`、未知 schema/渲染器/字体门回退 `:116-:206` 等）。**仍未见**：删会话不删保存题图、保存额、strictOffline 重放 0 派发专项。批次 38 T2 落点=剩余半边；台账轨按现树改写该行 |
| V-04（`:223`） | **未落**："正文/trace/visual link 同事务；失败/取消清 staging；迟到结果不覆盖新 revision" | ⚠️ 半边推进：同事务提交载体 `TutorVisualCommitRepository.kt` 在树（ls 实证，本轮未读实现=同事务完整性 **UNVERIFIED**）；staging 面 `VisualSnapshotRestoreTest` "a staging snapshot is never replayed"（`:241`）在树；**未见**失败/取消清 staging、迟到结果不覆盖新 revision 的专项测试。行整体维持"未落→部分落待台账定谳" |
| Q-02（`:210`） | "入口传 `false`"；缺口=映射裁定/修正（`ConfiguredCleanImageGenerator.kt:103-107`） | ✅ 口径维持，锚点漂移：现树 `ConfiguredCleanImageGenerator.kt:102`/`:118` 两处 `PIXEL_CLEANUP`，`:104` `requiresPixelFidelity = false` 仍在（sed 实读）→ **映射裁定仍待主会话**（计划 §1 A.4） |
| Q-04（`:212`） | **未落**："六类 fixture 静态子集 + 新组合，独立预期；学科正确性与渲染 fixture 分开" | ✅ 未落维持：`波叠加/轨迹采样/waveSuperposition` 全仓 0 命中（grep 实跑）；androidTest assets 只有 dense/golden，无六类 fixture（ls 实证） |
| A-09（`:197`） | **未落**："广告-执行一致设备用例：ads JS → 执行成功，而非 `UNAVAILABLE`；0 远程派发" | ✅ 未落维持：`VisualJsAdvertiseThenExecute` 全仓 0 命中（grep 实跑）。**前置依赖注意**：A-05 生产双就绪位仍 `false/false`（`RoomModelTaskRepository.kt:964-965`，清单 `:193`），广告面未开——本用例的广告前提未成立 |
| P-09（`:132`） | 代码 + JVM 全落、设备 2/3（1 红=A-14 夹具缺陷已修未复跑）；客户端队列层设备负向未执行 | ✅ 载体在树：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsFrameQueue.kt` + `app/src/test/.../visualjs/VisualJsFrameQueueTest.kt`（ls 实证）。A-14 复跑后状态：批次 26 E-01 12+2 全绿（清单 v3 补记，行内注记）——本轮未复跑设备，转录不代判 |
| P-10（`:133`） | **部分落**：abandon 2 s 未确认时 `unavailable=true` 已落（`VisualJsClientEngine.kt:588-603`），无 fallback UI/加载态，无死亡未确认设备负向 | ✅ 语义在树（锚点漂移）：`app/.../visualjs/VisualJsClientEngine.kt:259` "death was not confirmed" 日志串、`:371/:407/:417/:427` `unavailable = true` 置位点（grep 实跑）。fallback UI 半边未见。计划 §1 B.7 收口时按符号重定位 |
| P-04（`:127`） | **代码已落 + 注入形状设备绿（部分）**；缺口=双态成对证据、满队列生命周期重调度、29.5 s worst-case、KD-76 ③ | 行原文已录；本轮未做树外验证（设备 UNVERIFIED 维持） |
| P-05（`:128`） | 现有用例 ≤6 s 判死弱于关闭门；KD-66 已于批次 26 关闭（批次 26 record §3.5）——本行关闭门判据由 KD-66 关闭块承接 | 行原文已录；`aHungWorkerReleasesTheCallerWithinHardPlus500ms` 关闭证据见 KD-66 `known-defects.md:2619`（closed 块，实读） |

## 四、M/S 组：行原文与度量面现树

| 行 | 行原文（现状格，摘） | 现树核对（本会话实跑） |
|---|---|---|
| M-01（`:155`） | **部分落**：端口有宽/高/baseline/字号/digest，**无 `inkBounds`**；验收=端口/度量批次测试（新增 JVM） | ✅ 端口面维持：`core/domain/.../VisualExecutionEngine.kt:162` `interface VisualContentMeasurer`、`VisualMeasuredContent(contentId, widthDu, heightDu, …)`、`:210` KDoc 自认 "inkBounds 的完整矩形随批次二类型化布局落地"。**新增事实（计划 §1 C.9 的现成落点）**：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt` 已有 **internal 完整布局 artifact** `VisualMeasuredLayout(kind, widthDu, heightDu, baselineDu, lines, degraded)`（该文件 :247 附近 data class，KDoc："端口五字段之上的行结构……本批不进端口、不序列化"）→ M-01 的缺口=把该 artifact **提进端口/类型化布局**，不是从零造 |
| M-03（`:157`） | **未落**："真实字形度量/字体白名单/后台布局/12..48 sp/fontScale/累计 4 MP/单 bitmap 16 MiB" | ⚠️ 子面在树：12..48 sp 门已落（`VisualContentMeasuring.kt:178` `MIN_SIZE_SP = 12f`、`:179` `MAX_SIZE_SP = 48f`、`:199` `CANONICAL_SIZE_SP = 16f`）；**4 MP / 16 MiB 预算常量 0 命中**（grep `4_194_304\|16_777_216\|MAX_TOTAL_PIXEL\|MAX_BITMAP` 于 app/core data/domain main = 0）；字体白名单仅占位 `SYSTEM_DEFAULT_TYPEFACE_ID = "system-default"`（`:194`，KDoc 自认"字体白名单/字体身份随 painter 批次落地"）。→ 批次 38 T10（排版预算）的 4MP/16MiB 半边为纯新增，12..48sp 半边已有门可复用 |
| S-08（`:148`） | **未落**："数学真实 lowering：复用 `MathBudget` parser/AST，未知 TeX 回可读文本"；`MathContentLowering` 零命中 | ✅ 未落维持：`MathContentLowering` 全仓 0 命中（grep 实跑）；复用对象在树——`core/model/src/main/.../MathBudget.kt`（+ enforcement/fuzz 测试 3 件，ls 实证）。**降级路径已存在**：`VisualContentMeasuring.kt` `measureMathBox`（`:314`）+ `degraded` 字段（降级 math 携带文本段列表）——S-08 的"未知 TeX 回可读文本"已有雏形，lowering 专项未立 |

度量器脏态：`VisualContentMeasuring.kt` 与其测试 `git status --porcelain` 干净（已入库态）。度量器现挂 `AndroidVisualContentMeasurer`（internal，`:53`），仍非隔离服务注入的真实共享 measurer（M-02 未在本轮范围，注记而已）。

## 五、D/X 组：行原文与现树

| 行 | 行原文（现状格，摘） | 现树核对（本会话实跑） |
|---|---|---|
| D-04（`:167`） | **未落**："`LOCAL_VISUAL_PREVIEW` 预览 canonical 资产；备份含程序/快照/预览；缺必需字节拒绝完整包" | ✅ 未落维持：`LOCAL_VISUAL_PREVIEW` 全仓 0 命中（grep 实跑）。周边事实：canonical 资产记录与 persist 缝已在 `AttachedImageGenerator`（WG-05 面，见 D-06 行）与 backup 面（Smbk/BackupVisualReference 测试在树）——D-04 落点=常量 + 备份枚举 + 完整性门 |
| D-06（`:169`） | **部分落**：显式再生成接线已测（WG-05）；**cache-only resolver 与"打开旧消息 0 派发"无行、无门** | ⚠️ **行已滞后（resolver 半边已落码）**：`core/data/.../model/AttachedImageGenerator.kt:33-47` KDoc 明示两路径——`resolveOne`（打开消息/展开/旋转/进程恢复）**cache-only，绝不发起生图请求**、缺失/损坏/缺源各回可读原因；`regenerate`（显式重新生成）转调共享协调器；`:75` `resolve(images)` 批量 cache-only 解析（sed 实读）。**0 派发观测门现状**：`FigureHttpTransportBypassObservationTest`（5 例，grep `fun \`` 实读）只覆盖 ledger 拒绝 0 派发/放行 1 派发 × 三入口 + fail-closed 例，**未见"离线打开旧消息"专项观测**。→ 批次 38 T13 落点=0 派发观测半边；台账轨改写该行现状 |
| X-02（`:240`） | **未落**："`VisualSourceBasis` + `sourceSeenDispatchId`；Plan 能取到原题资产或可追溯结构化源"；两符号零命中 | ✅ 未落维持：两符号全仓 0 命中（grep 实跑） |
| X-03（`:241`） | **未落**（JS 无持久载体）："strictOffline：禁止新外发/新源码，但已持久程序可重布局/交互且 0 Provider 派发" | ✅ 未落维持（JS 面）：`strictOffline` 命中仅在 app 壳/诊断面（SmartMistakeBookApplication/Root/Destinations 等 5 文件，构建变体与离线模式语义），`core/data/src/main` 0 命中——JS 持久程序重布局面无载体。恢复门半边（D-05/V-03 的 restore gates）已落码可复用 |

## 六、A 组残余：A-11 请求半边、A-12 扩测点、A-10 跨 method、KD-82

| 项 | 清单行原文（摘） | 现树核对（本会话实跑） |
|---|---|---|
| A-11（`:199`） | 响应半边：三协议 × 向量参数化套件已落 15 @Test（门禁串行 15/0）；**"请求半边（visualContext→prompt 载体）在树未接线"**；判据"21 向量全绿才可声称 Provider 已支持"不变 → 部分落 | ✅ 响应半边载体在树：`core/data/src/test/.../model/wire/ModelWireProtocolSourceJsFidelityTest.kt` 存在，`grep -c "@Test"` = **15**（anthropic/gemini/responses × 5）。**请求半边缺口实证**：`FigurePromptAssembler.kt` grep `visualContext\|VisualContext` = **0 命中** → 计划 §1 E.16 "A-11 请求半边" 的落点=visualContext 进请求/prompt 载体接线（注意计划 §2 热区：`FigurePromptAssembler.kt` 在 core/data，非热区，可改） |
| A-12（`:200`） | **部分落**：批次 36 注记——`ModelTaskVisualContextGoldenTest` 7 例已落（v17 往返逐字节、逻辑投影 SHA-256 golden、键形状立碑）；既有 `ModelTaskFingerprintStabilityTest` 42 例未动；缺口栏 "visual 上下文扩测行与 golden 载体" | ✅ 载体在树：`core/model/src/test/.../ModelTaskVisualContextGoldenTest.kt` 存在（`grep -c "@Test"` = 7）；`ModelTaskFingerprintStabilityTest.kt` = **42** @Test。→ 批次 38 §1 E.17 "A-12 扩测" 的现树落点应按行内验收列定位（golden 全绿+diff 零+新字段仅非 null 出现；新可空字段 NEVER/投影门），**不是**重补已在树的 7 例 |
| A-10（`:198`） | **部分落**：批次 37 注记——设备 3 例 `OK (3 tests)`（QUEUED 夹具+终答位子门）；"跨 method 合计最终图无直接协调器级测试（覆盖缺口登记）" | ✅ 跨 method 缺口实证：`FigureGenerationLocalBudgetBoundaryTest.kt` 5 例中终图门两例各单 method——`:154` `aThirdFinalFigureIsRejectedBeforeTheBackendOpensForTheSameOperation`（纯 IMAGE proposal ×3）、`:198` `aThirdFinalFigureIsRejectedBeforeTheEngineRunsForTheSameOperation`（纯 JS proposal ×3）（sed 实读）；**无"IMAGE+JS 混合合计第 3 份拒绝"向量**。设备夹具在树干净：`core/data/src/androidTest/.../visual/FigureDispatchBudgetBoundaryInstrumentedTest.kt`（git status 干净）。→ 计划 §1 E.18 "跨 method 合计向量" 落点确认 |
| KD-82 | 台账三块：登记头 `docs/known-defects.md:3147`（open，known limitation）；批次 37 复核轮 `:3161`；批次 37 收口 `:3164`（明文 **"imagePrompt 残余未处理……残余归批次 38"**） | ✅ 现树行号（grep/sed 实跑）：`core/data/.../model/OpenAiModelProtocol.kt:552-554` imagePrompt `maxLength = FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES`（**4096 码点**）；程序面对齐已落 `:590-592`（`MAX_PROGRAM_UTF8_BYTES / 4` = 16384，KD-82 注释 `:582-583`）。字节门：`core/model/.../FigureGenerationPolicy.kt:32` `MAX_IMAGE_PROMPT_UTF8_BYTES = 4 * 1_024`（执行门 `:274`）、`:33` `MAX_PROGRAM_UTF8_BYTES = 64 * 1_024`（执行门 `:222`）。→ 批次 38 §1 E.19 处方=① imagePrompt 4096→1024（⌊4096/4⌋）；**program 面 JVM 门 4 例已由批次 37 门禁轮出数（4/0，`:3164`）**，残余半边 JVM 用例待新落实跑 |

## 七、KD-72 六 build 面、KD-76 校准缺口、Keeper 预研前置

| 项 | 台账位置与状态 | 现树核对（本会话实跑） |
|---|---|---|
| KD-72（六 build 面/锁窗口） | `docs/known-defects.md:2758` **open**；`:2772`（批次 24 T3 块：13 条 git 可见锁、盘上 14 个、DEFAULT 模式、不得原样 add）；`:2774` 未闭合四条（干净树重生成 / :benchmark 缺锁+core:markdown 不得单独入库 / 负控未执行 / 13 条不得 add） | 锁面复现一致：13 条 `??` 锁条目 + `git ls-files "*.lockfile"` = 0（§二）。**build 文件面现树**：M 的 `build.gradle.kts` = **3 件**（core/export +3、core/ui +2/−1、feature/tutor +2，`git diff --stat` 实跑）+ `settings.gradle.kts` M（`+:core:markdown`）+ **`?? core/markdown/` 整目录未跟踪**（其 build.gradle.kts 被折叠）——批次 26 时点"六 build 文件"在现树呈 3 M + 1 新目录形态；KD-72 ①"锁与 HEAD 不一致、不得原样 add"语义不变。计划 §1 G.23 "KD-72 锁窗口" 的前置（干净待提交树）现树不满足 |
| KD-76（校准缺口） | `docs/known-defects.md:2863` **open**；批次 29 设备轨块 `:2903`（隔离 12/13 红归 KD-78 ④ 产品缺陷、⑤ 行为层该树红、修后须复跑）+ 批次 29 painter 块 `:2968`（④ flags 红已修方向登记） | 校准缺口清单（P-07 行 `:130` + KD-76 块，实读转录）：③ 产品进程/UI 首请求、29.5 s worst-case 启动面、低内存条件、`READY_TIMEOUT=10 s` 设备校准、arm64/真机口径——均 UNVERIFIED；`READY_TIMEOUT_MILLIS=10_000` 与轮询间隔 100 ms 现值锚点见清单 P-04 行（`VisualJsClientEngine.kt:1230`/`:1233` 时点值，本轮未复读该两行=行号漂移可能，**符号定位实施时重锚**）。计划 §1 F.20 "READY_TIMEOUT+29.5s 校准判据" 为只读/设计，不动热区 |
| Keeper 预研前置 | R-04 行 `:232` v3 补记 13（批次 37）：K1–K5 全绿、三值预检②不过→分支 P2 选项 B、**K6 红=机制①（L8 编译治 j$ 改名，`.pro` 不可解锁）**；可行解锁=slackhq/Keeper 类 L8 规则合并（Apache-2.0/269★，**AGP 9.3 兼容性 UNVERIFIED**，触 `settings.gradle.kts` 热区）；**关闭前置=主会话两合规路径裁定**（选项 A Keeper 结构解锁 vs 维持预检硬门口径） | 预研输入齐备（台账实读）：① 对象=Keeper 的 L8 规则合并能力（把 `-keep class j$.**` 转为 L8 输入规则）；② 热区约束=只读研究，不动 settings.gradle.kts（该文件已有他线 `+:core:markdown` diff，见 §二）；③ 兼容性问题=AGP 9.3.0（批次 37 R2 取证版本）对 Keeper 版本支持；④ 备选路径=维持预检硬门口径（R-04 永久阻塞口径）。计划 §1 F.22 与本节一致 |

## 八、设备在线状态（只读）

`C:\Android\Sdk\platform-tools\adb.exe devices`（路径取自根 `local.properties` 的 `sdk.dir`；只读，未安装/未启动任何东西）：

```
List of devices attached
emulator-5562	device
emulator-5566	device
emulator-5584	device
emulator-5596	device
```

`getprop`（只读）：5562/5566/5584 = **API 34 / x86_64**；5596 = **API 23 / x86_64**。emulator-5562 为批次 37 设备轨同机（record 引用）；API 23 在线与 KD-77 closed 的镜像面呼应（R-01 若立项可直接用 5596，本轮未做任何设备执行）。

## 九、与计划 §0 的相符/不符清单（汇总）

**相符**（逐条实证见上）：HEAD=`dcfdc5b5` 且已推送（origin/main 同值、0/0）；DB schema=71；KB 线 75 文件上远端 + §10.1 归属登记在仓；KD-77（`:2909` closed）/KD-80（`:3007` closed）/KD-81（`:3097` closed）/KD-82（`:3147` open，imagePrompt 残余归批次 38）；R-04 阻塞维持（L8 结构性，Keeper 解锁触 settings.gradle.kts）；V-01 PARTIAL（3/4，app 版 3 例绿、④ PDF 腿 core:data internal 不可达留降级变体）；契约 `:208` 快照身份边界句仍在树（本轮实读 `docs/current/visual-generation-contract.md:208` 行内含 KD-81 边界块；docs/current 7 M 未清空 → 不落笔纪律维持）。

**不符/计划未记四点**：
1. **工作树 726 条、热区全线非净**（§一/§二）——计划 §0 未记；批次 38 每次 commit 前按计划 §2 对照申报清单单独 reset 申报外路径（尤其 settings.gradle.kts 的他线 `+:core:markdown` 与 core/database 的 KB 面）。
2. **fetch 滞后**（FETCH_HEAD Oct 27 00:39）——远端新鲜度 UNVERIFIED。
3. **清单 D-06/V-03 行现状滞后于现树**（§三/§五：cache-only resolver 已落码、checkpoint/restore 半边已落码）——台账轨（§1 G.24）回填时按现树改写，勿按行内旧现状定计划。
4. **A-12 扩测点重定位**（§六：golden 7 例已在树）——§1 E.17 的缺口是行内验收列的 NEVER/投影门与 golden 回归面，不是补 7 例。

**UNVERIFIED 保留项**：远端是否有新提交（未 fetch）；V-04 同事务/迟到结果实现完整性（本轮未读 TutorVisualCommitRepository 实现）；P-04/P-09/P-10 设备面（本轮零设备执行，全部转录台账）；READY_TIMEOUT 锚点行号（符号重定位待实施轨）。
