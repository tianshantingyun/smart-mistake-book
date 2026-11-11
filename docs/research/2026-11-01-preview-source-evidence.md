# 批次 38 R3 持久化研究：预览 canonical 资产（D-04）与源证据边界（D-06 / X-02 / X-03）（2026-11-01）

> 研究人：R3 持久化研究（子代理，只读研究：零代码改动、零 Gradle、零 git 写、零设备执行）。
> 方法：全部结论来自**本会话实读文件与实跑命令**；命令与关键输出随条记录；无当前证据的结论一律标 **UNVERIFIED**。单通道失败换通道，未发生阻塞。
> 驱动任务：批次 38 D 轨（计划 §1 D.12–D.15）。对照材料：清单 v3（`docs/research/2026-10-26-batch-24-4e2-admission-checklist.md`，行号 `:NNN` 为该文件行号）、契约（`docs/current/visual-generation-contract.md`，行号同法）、基线文（`docs/research/2026-11-01-batch-38-baseline.md`）。
> 本文件为**新增未跟踪文件**，不与他线 `docs/research` 记录重叠（同名路径写入前 `ls` 确认不存在）。

## 0. 四轨一句话结论

| 轨 | 结论 |
|---|---|
| D-04 | **完备性两侧判据已存在且已覆盖预览引用**（第 6 支 `tutor_visual_snapshot.preview_asset_id` 进了六站点谓词；缺字节包在成包与恢复两侧都被拒绝）。缺口收窄为两件：`LOCAL_VISUAL_PREVIEW` 常量+写侧（生产零写入方，grep 实证）与备份/恢复设备 suite 的预览资产显式向量 |
| D-06 | **cache-only resolver 半边已落码且语义完整**（`resolveOne` 只读、缺失回 `NOT_GENERATED` 明示"不会自动联网生成"）；缺口只剩 **0 派发/0 计费观测门**——复用 `FigureHttpTransportBypassObservationTest` 的 MockWebServer 模式，离线 resolve 臂为零请求断言 |
| X-02 | 两符号全仓 0 命中维持（本会话实跑）；`VisualSourceBasis` 的可挂载体已定位——宿主冻结的 `FigureSourceEvidence` + 账本 `reservationId`/attempt 行的 `sourceSha256`；"Plan 取到原题资产"的缝在 `TutorQuestionContext`（现无原题资产字段，实读确认） |
| X-03 | **静态冷恢复半边已落码**（`VisualSnapshotRestore` 判定序完整、类型层防再执行）；strictOffline 现状只是**构建变体**（app/build.gradle.kts:52）；JS 本地重执行在结构上 0 远程派发，缺设备向量把它钉成证据 |

## 一、D-04：预览 canonical 资产与备份完备性

### 1.1 行原文与契约口径

- 清单 `:167`（D-04）："`LOCAL_VISUAL_PREVIEW` 预览 canonical 资产；备份含程序/快照/预览；缺必需字节拒绝完整包"；现状格"未落"；验收=备份/恢复 suite（设备）。
- 契约 `:176`："JS源码独立存，PNG预览可用LOCAL_VISUAL_PREVIEW技术标签；图像模型结果走canonical像素资产。标签只说明存储来源，不定义'是否生图'或费用"。

### 1.2 现状证据（本会话实读/实跑）

1. **`LOCAL_VISUAL_PREVIEW` 0 命中**：`grep -rn "LOCAL_VISUAL_PREVIEW|LOCAL_VISUAL\b" --include="*.kt" core app feature`（排除 `.worktrees`）= **0 行输出**。与基线文 §五 D-04 行一致。
2. **sourceType 常量表现状**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDbValue.kt:40-52`）：`SourceAssetType` 只有 `CAMERA`/`PHOTO_PICKER`/`TUTOR_ATTACHED`/`GENERATED_FIGURE` 四值——无预览标签。
3. **预览引用列已落 schema 71**：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json:9903` 的 `tutor_visual_snapshot` 建表 SQL 含 `preview_asset_id TEXT` 且 `FOREIGN KEY(preview_asset_id) REFERENCES canonical_source_asset(source_asset_id) … ON DELETE RESTRICT`；索引 `index_tutor_visual_snapshot_preview_asset_id`（71.json:10101）。四张 visual 表全部在 71：`tutor_visual_program`（:9784）、`tutor_visual_snapshot`（:9902）、`tutor_message_visual`（:10146）、`problem_revision_visual`（:10266）——D-01/D-02/D-03 的表已落（清单 D-01 行"DB v69 未落"的现状已滞后，台账轨按现树改写）。
4. **写侧零生产写入方**：`grep -rn "previewAssetId = " core/data/src/main app/src/main`（排除定义文件）只命中 `VisualSnapshotRestore.kt` 的只读面——**没有任何调用方给 commit DTO 传过非空 previewAssetId**（`TutorVisualCommitRepository.kt:103` 的 `previewAssetId: String? = null` 默认值即全部现实）。JS 预览 PNG 的生产字节路径不存在。
5. **引用判定单源**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/CanonicalAssetReferenceSites.kt:31-54`）：六类 `NOT EXISTS` 谓词，**第 6 支就是 `tutor_visual_snapshot.preview_asset_id`**（批次 30 D-01 追加，KDoc :14-15 明言漏掉的失败=OrphanAssetGc 把正在显示的预览当孤儿删）。清理认领（`PendingCaptureDao.findUnreferencedCanonicalAssets`）与备份裁剪器 I5 对拍共用这一份 SQL。
6. **引用集合的推导**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomStudyDatabase.kt:922-940`）：`readReferencedCanonicalAssets` = 全表 − 未引用；因此**预览资产一旦被快照行引用，自动进入备份集合**，不需要第二个 EXISTS 列表。

### 1.3 "缺必需字节拒绝完整包"两侧判据（均已存在）

- **成包侧 fail-closed**（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/backup/AndroidBackupRepository.kt`）：
  - `create()`（:194-280）：先快照库 → 裁剪器 → 从**快照库**反查引用资产（:253）→ `resolveReferencedAssetFiles`（:374-386）逐条 `vault.resolve`——缺文件/被改动/越库**成包失败**（:381-383 错误信息"快照引用的题图缺失或已损坏，备份未完成"），不静默跳过；
  - 完整性判据单源：`canonicalAssetFileIsIntact`（`core/data/.../capture/AndroidCanonicalAssetVault.kt:44-50`）= 文件在 + 字节数等 + sha256 逐位等；vault `resolve`（同文件 :273-281）是唯一生产实现，备份不重写第二份。
- **恢复侧交换前拒绝**（同文件 `verifyStagedAssetClosure` :1192-1218）：解包出的资产目录里，归档库引用的每张图必须逐位过 `canonicalAssetFileIsIntact`，路径必须恰为 `source-assets/<文件名>`；缺一张即 `BackupRestoreException`，**此时尚未 swap**，原数据原图不动。清单声明 vs 包内库实测（Phase 2c.1 :1234-1310）与派生索引恒裁（Phase 2c.2）在同一交换前段。
- **结论**："缺必需字节拒绝完整包"不是要新建的机制，而是**已存在的两侧判据对第 6 支引用的自动覆盖**。D-04 的实际落点收窄为：
  1. `LOCAL_VISUAL_PREVIEW` 常量入 `SourceAssetType`（core/database，非热区）；
  2. 预览 PNG **写侧**：JS 快照提升 LIVE 时把预览字节经 `persistCleanImageBytes`（`AndroidCanonicalAssetVault.kt:196`）落成 canonical 资产（`figureIdentity` 或内容寻址按幂等语义定）、`registerCanonicalSourceAsset` 后把 id 填进 `tutor_visual_snapshot.preview_asset_id`——这是**新增必须指认的失败**：契约 §8.1 :190 恢复分层"RenderScene 也未知时只能用受预算校验的 canonical 预览/可读说明"（`VisualSnapshotRestore` 的 ③档 `ReadOnly(previewAssetId)`）——没有预览字节，未知版本/字体清单未验证时**只剩文字说明**；
  3. 备份/恢复设备 suite 补"含预览资产的快照"向量：恢复后 `tutor_visual_snapshot.preview_asset_id` 的 RESTRICT 引用逐位可核（验收行 `:167` 名义上的设备 suite）。
- **热区注意**：预览**渲染**面在 core/ui（`BoundedLocalImage.kt`，禁改）；预览**字节写入与引用**全在 core/data（非热区）。`VisualSnapshotRestore` 的 `previewVerified` 参数（:190-193, :202）就是字节核对与渲染面的既有接缝——预览 id 只有在调用方以 `canonicalAssetFileIsIntact` 核对后才被暴露，D-04 不需要为此改恢复门。

## 二、D-06：cache-only resolver 与"打开旧消息不出网"

### 2.1 行原文与契约口径

- 清单 `:169`（D-06）：旧 attachedImages cache-only resolver 与"打开旧消息不出网"（契约 §8:188）；现状格"部分落"；缺口=cache-only resolver + 打开旧消息 0 派发观测；验收=JVM/设备，复用 `FigureHttpTransportBypassObservationTest` 的 0 派发观测模式。
- 契约 `:188`："历史 attachedImages resolver 改为 cache-only，确定映射后补真实消息引用；缺失显示显式重新生成动作，打开旧消息不能悄悄出网收费。"

### 2.2 现状证据（本会话实读）

1. **两路径互斥已落码**（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/model/AttachedImageGenerator.kt`）：
   - KDoc :31-50 明文：`resolveOne`（打开消息/展开/旋转/进程恢复）**cache-only，绝不发起生图请求**；`regenerate`（用户显式）转调共享协调器；
   - `resolve` 批量 :77-83 → `resolveOne` :88-95 → `lookupResolution` :212-245：只有 `lookupExisting`（数据库读）+ `isIntact`（vault 完整性核对）；**缺失回 `NOT_GENERATED`，文案即纪律**："这条历史消息在本机没有已生成的副本；打开消息不会自动联网生成"（:226-229）；损坏回 `DAMAGED` :240-243；缺源回 `SOURCE_UNAVAILABLE` :90-93；
   - `regenerate` :105-210：幂等命中先查（:117-136），未注入协调器回 `REGENERATION_UNAVAILABLE` 不伪成功（:137-141），出网只经 `coordinator.generate`（:199-208）。
2. **生产装配**（`core/data/.../model/AttachedImageGeneratorFactory.kt`）：`create`（:37-55）返回 `AttachedImageRuntimeResolver`（:129-142）——同时是旧 `String?` lambda 与 `AttachedImageResolutionPort`（resolve/regenerate）；`lookupExisting` 即 `database.readCanonicalSourceAsset`（:100）、`isIntact` 即 `vault.resolve`（:101）。**resolver 里没有任何网络类型**——cache-only 是结构事实，不只靠约定。
3. **观测门现状**：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureHttpTransportBypassObservationTest.kt`，本会话 `grep -c "@Test"` = **7**（基线文 §五记"5 例"已滞后，应为 7）。三业务入口（讲题工具/历史配图显式重生成/保存净化）接**真实 `OpenAiImageGenerationChannel` + MockWebServer**，以 `server.requestCount` 断言：账本拒绝 → 0 HTTP；授予 → 恰 1 HTTP 且路径/授权头与预留一致（KDoc :43-54，"授予臂同时是拒绝臂的非空转对照"）；另有无协调器 fail-closed 0 HTTP 例（:227）。
4. **缺口精确定义**：7 例全部是**显式生成/重生成臂**的观测；**没有**"离线打开旧消息 → `resolveOne`/`resolve` → 0 HTTP 且 0 计费"的专项。

### 2.3 落点（批次 38 T13 的 0 派发观测半边）

- 新增 JVM 观测用例，模式照抄该测试类：真实通道 + MockWebServer + `resolveOne`/`resolve` 直调（不注入协调器或注入 null），断言 `server.requestCount == 0` + 结果 `NOT_GENERATED`/`DAMAGED` 的原因正确；"0 计费"侧用 fake 账本计数器断言 `reserve` **从未被调用**（resolve 路径结构上不触账本——账本调用只出现在 coordinator，`DefaultFigureGenerationCoordinator.kt:497` "没有授予就没有派发"）。
- 消灭的具体失败：未来有人把"打开消息时顺手预取/补图"塞进 resolver 或其调用链时，没有任何东西会报红（各路径单看都"能跑"）；观测门把这个失败钉在 JVM 面。
- 设备侧（验收名义）：离线 flavor 打开含旧 attachedImages 的消息 + UI 呈现显式再生成动作——UI 面在 feature/tutor 与 core/ui（**禁改热区**），批次 38 只落 data 面 JVM 门，设备 UI 向量登记待热区窗口。

## 三、X-02：源证据（VisualSourceBasis + sourceSeenDispatchId）

### 3.1 行原文与契约口径

- 清单 `:240`：两符号零命中；缺口=源证据门；验收=缺源/错图/SHA 变化/授权不足/预算不足（JVM/设备）。
- 契约 `:37`："先冻结VisualSourceBasis（subject/revision、canonical引用/SHA、允许披露范围），各后端取得其必需证据：JS产码模型需实际看到原图或足够且可追溯的结构化源事实；IMAGE_MODEL编辑执行需收到已授权原图。不能把本地有图当参与生成的模型已看图"。
- 契约 `:39`："NEED_SOURCE/PREPARE_SOURCE统一准备缺失证据，下一预算内回合实际发送后记录sourceSeenDispatchId/字节摘要；同批准备不冒充已发送。"

### 3.2 现状证据（本会话实跑/实读）

1. **两符号 0 命中**：本会话 `grep -rln "VisualSourceBasis|sourceSeenDispatchId" --include="*.kt"`（主树）= **0 行输出**，维持未落。
2. **`TutorQuestionContext` 无原题资产**（`feature/tutor/src/main/java/com/tingyun/smartmistakebook/feature/tutor/TutorModelTaskPolicy.kt:115-141`）：字段=sessionId/revisionNumber/subject/title/questionDocument/learningMemory/relatedKnowledgeNodeIds/reviewedTeachingReferences/priorTeachingAdvisories/knowledgeCodes/teachingReferencesLoadFailed——**没有 canonical 引用字段**（与契约 :37 "当前TutorQuestionContext没有原题资产，Plan不发原题图"逐字对应）。该文件在 feature/tutor **禁改热区**。
3. **Respond 只发 studentImageAssetRefs**：`core/data/.../model/OpenAiCompatibleModelGateway.kt:637`（`is TutorRespondInput -> input.studentImageAssetRefs`）、`OpenAiModelTaskAdapters.kt:349-350`（附图注入文案）。
4. **可挂载体（不是从零造）**：
   - **宿主冻结的源证据原语已存在**：`FigureSourceEvidence`（`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/FigureGeneration.kt:224-250`）——`assetId`/`contentSha256`/`mimeType`/`imageBytes`，KDoc 明言"不是模型可填的图片/URL"、sha 与字节必须一致。`VisualSourceBasis` 的字段即它在 subject/revision 维度的扩展（subject/revision + canonical 引用 + 披露范围），落点在 core/model（非热区）。
   - **"实际发送"的持久记录已存在**：账本预留 `RemoteModelDispatchReservation.reservationId`（`core/domain/.../RemoteModelDispatchLedger.kt:69-97`，"由持久记录稳定派生，便于日志/审计关联"）+ `sourceImageSha256`（"实际要发送的源图字节的 sha"，:44-46）；协调器把预留写成 attempt 行（`core/data/.../visual/DefaultFigureGenerationCoordinator.kt:632-648` `beginAttempt`：`reservationId` + `sourceSha256` 落 `figure_generation_attempt`，schema 71 :9650-9651 两列在建表 SQL 内）。**`sourceSeenDispatchId` 的自然语义 = 该 attempt/reservation 链上"源图真的随这次派发发出去了"的记录**——不新增持久层形状，新增的是 Plan/Respond 路径把证据取到 + 发送后回写引用。
   - **派发绑定不可绕**：`FigureImageDispatchBinding`（`core/domain/.../FigureGenerationCoordinator.kt:41-87`）构造器 internal、生产唯一入口 `fromReservation`；后端发送前复核绑定。X-02 的"错图/SHA 变化"向量可直接钉在这里（sha 与请求绑定不一致时拒绝）。
5. **验收向量与账本拒绝码的映射**：缺源 → `SOURCE_UNAVAILABLE`（resolver）与 coordinator 的 NEED_SOURCE 语义；授权不足/预算不足 → `RemoteModelDispatchDenialCode`（`RemoteModelDispatchLedger.kt:108+`，如 PARENT_NOT_FOUND 等）+ 计数上限（A-10 面）。"同批准备不冒充已发送"的可测形态：PREPARE_SOURCE 只登记证据不写 attempt 行——attempt 行只在授予预留后产生（:507 注释"attempt 必须在 reservation 授予之后"）。

### 3.3 边界与风险

- Plan 路径在 feature/tutor（热区 M）与 core:data 适配层之间——**本批不动热区**，可先落 core/model 的 `VisualSourceBasis` 类型 + core:data 的证据核对面（字节/sha/授权），接线归热区窗口批。
- "足够且可追溯的结构化源事实"（JS 产码模型路线）没有现有载体：`TutorQuestionContext.questionDocument` 是文档快照非 canonical 资产引用——这是 X-02 的真缺口，不是可复用项，登记时不得混同。

## 四、X-03：strictOffline 重布局边界

### 4.1 行原文与契约口径

- 清单 `:241`：现状"未落（JS 无持久载体）"；验收=strictOffline 重放（设备）。
- 契约 `:149`："strictOffline 禁止外部派发和获取新模型源码，但允许已持久程序在 API/SDK/运行库兼容且本地执行未关闭时重新布局、拖动和播放；静态冷恢复优先读快照。版本未知或执行熔断时读兼容快照/预览/文字。禁止把'离线可交互'宣传成'离线能生成新讲解'。"

### 4.2 现状证据（本会话实跑/实读）

1. **strictOffline = 构建变体**：`app/build.gradle.kts:51-62` `flavorDimensions "networkMode"` + `create("strictOffline") { applicationIdSuffix ".offline" }`。app 壳内 4 处注释引用（`SmartMistakeBookDestinations.kt:360/380`、`SmartMistakeBookApplication.kt:308`、`SmartMistakeBookRoot.kt:541`）语义均为"该构建无网络/无模型可用"。`core/data` main **0 命中**（本会话 grep 实证）。
2. **静态冷恢复半边已落码**（`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualSnapshotRestore.kt` 全文实读）：
   - 判定序 :193-251：非 LIVE / scene schema 未知 / render schema 未知 / renderer 版本不符 / 字体清单未验证 → 全部**有码只读**（`VisualRestoreFallbackReason` :72-90，六码）；全过才 `VisualDrawOpsParser.parse` 重跑全部不变量（:235）；
   - **类型层防再执行**：`VisualSnapshotRestoreRecord`（:33-69）**结构上没有**源码字符串与运行时/编译器/API/SDK 版本字段——"恢复误执行 JS"编译不可表达（KDoc :10-19）；
   - `VERIFIED_FONT_MANIFESTS = emptySet()`（:125）：默认策略**结构上不可能**承诺像素一致，永远走只读——诚实降级已内建；
   - `previewVerified` 参数（:190-193）：预览 id 仅在调用方核对字节后暴露——与 D-04 预览资产同一条缝。
3. **JS 本地重执行面**：`QuickJsVisualExecutionEngine`（core/data/visual，本地执行不产生外部图像调用，契约 :165）；持久程序读口 `TutorVisualDao.kt:61`（`SELECT * FROM tutor_visual_program WHERE program_id = :programId`）；程序行含源码与 API/SDK/运行库版本（D-01 表）。
4. **0 Provider 派发的正向证据位**：本地 JS attempt `reservation_id = NULL`、operation 计数 0/0（`DefaultFigureGenerationCoordinator.kt:651-657` KDoc："本地执行不占远程派发，终态 operation 写 0/0 就是'0 远程派发'的正向证据"）。

### 4.3 边界与落点

- **已落**（复用，不重建）：静态冷恢复判定（②档重放/③档只读 + 六码）、恢复不执行 JS 的类型层约束、程序行持久载体与读口、审计 0/0 计数位。
- **缺口**（X-03 验收"strictOffline 重放（设备）"）：**交互重布局/拖动/播放**的设备向量——①档（用户明确重跑）读 program 行源码经 QuickJs 引擎本地执行，API/SDK/运行库兼容门 + 0 外发 + 审计 0/0。观测位全部现成：`server.requestCount`（若有 MockWebServer）或账本零调用 + `figure_generation_operation` 行 0/0。
- **版本未知/熔断分支**：读兼容快照/预览/文字 = `VisualSnapshotRestore` 的 `ReadOnly` 码——无需新增判定，需把它接进 strictOffline 设备向量作对照臂。
- **契约红线**："禁止把'离线可交互'宣传成'离线能生成新讲解'"——UI 文案面（feature/tutor/core/ui，禁改）在实现批必须走"本地交互可用、新讲解需联网"的双态，不新增能力开关。
- **热区注意**：`VisualJsClientEngine`/app 壳与 feature/tutor 均 M——X-03 实现缝应走 core:data 的引擎端口与审计面，不动 app/visualjs。

## 五、GitHub 参考（离线优先 / 来源追溯）评分

取证命令：`gh api repos/<owner>/<repo> --jq '{license, stars, pushed_at, archived}'` 与 `gh api repos/<owner>/<repo>/contents/...`，实跑时间 **2026-11-01**。评分维度（全局规则 11.2）：可复用性 / 兼容性（技术栈与本项目 Android/Kotlin/Room） / 质量（更新度、维护状态、license）。

| 项目 | 角色 | license（实证） | stars | 最近 push | 评分 | 采用方式 |
|---|---|---|---|---|---|---|
| [coil-kt/coil](https://github.com/coil-kt/coil) | **D-06 cache-only 的直接类比**：`CachePolicy { ENABLED/READ_ONLY/WRITE_ONLY/DISABLED }`（源文件 `coil-core/src/commonMain/kotlin/coil3/request/CachePolicy.kt` 本会话经 gh api 逐字取证）——"cache-only = readEnabled=true, writeEnabled=false" 的枚举语义与本项目 resolver 的只读路径同构 | Apache-2.0 | 11,954 | 2026-10-31 | **A** | 概念借鉴：resolver 不引依赖，但若将来给 `AttachedImageResolution` 加策略枚举，语义照此四值（项目自研 resolver 已落，此项只钉语义对齐） |
| [android/nowinandroid](https://github.com/android/nowinandroid) | 离线优先数据层架构参考（Google 官方样例：本地为唯一事实源、同步为派生） | Apache-2.0 | 21,990 | 2026-10-31 | **B+** | 架构对照，不引依赖 |
| [restic/restic](https://github.com/restic/restic) | **D-04 完备性设计参考**：内容寻址（对象 id=sha256）、恢复/校验双侧完整性、`restic check` 对缺包拒绝——本项目 `canonicalAssetFileIsIntact` 两侧判据的同款思路 | BSD-2-Clause | 36,900 | 2026-10-18 | **B** | 设计参考（Go，不集成）：成包/恢复双侧 fail-closed 已对齐其语义 |
| [contentauth/c2pa-rs](https://github.com/contentauth/c2pa-rs) | **X-02 媒体溯源规范参考**：C2PA manifest（生成工具/时间/来源声明随媒体走）——本项目 `GeneratedFigureProvenance` 已把隐式 AI 标识写进编码字节（`AndroidCanonicalAssetVault.kt:187-189, 372-373`），C2PA 是其工业规范形态 | MIT OR Apache-2.0（`LICENSE-APACHE`+`LICENSE-MIT` 实证） | 438 | 2026-10-31 | **B-** | 规范参考（Rust，不集成）：X-02 的 `VisualSourceBasis` 字段设计可对照其 claim 结构 |
| [in-toto/attestation](https://github.com/in-toto/attestation) | 结构化溯源声明规范（subject/predicate 模型，CNCF）——"可追溯结构化源事实"（契约 :37）的声明格式参照 | Apache-2.0（LICENSE 首行实证） | 389 | 2026-10-28 | **C+** | 规范参考，优先级低于 C2PA（更贴构建溯源而非媒体溯源） |
| [sqldelight/sqldelight](https://github.com/sqldelight/sqldelight) | 离线优先 DB（SQL 为源生成类型安全 API） | Apache-2.0 | 6,898 | 2026-10-25 | **C** | 不采用：本项目 Room 已定型，迁移成本与收益不匹配 |

**结论**：四条轨都不需要引入第三方依赖——每个机制在本仓已有对应实现或载体；GitHub 参考的价值在**语义对齐**（Coil 的 cache-only 枚举、restic 的双侧完备性、C2PA 的溯源声明结构），而非替代自研。

## 六、与基线文（2026-11-01-batch-38-baseline.md）的差勘

基线文与本文件同日、同主树；三处按本会话实跑修正/补强：

1. **D-06 观测门例数**：基线 §五记 `FigureHttpTransportBypassObservationTest` "5 例"；本会话 `grep -c "@Test"` = **7**（`clean redraw`×2、`tutor figure`×2、`attached-image regeneration`×2、无协调器 fail-closed×1）。
2. **D-04 新事实**：`tutor_visual_snapshot.preview_asset_id` 的引用支已在六站点谓词（`CanonicalAssetReferenceSites.kt:51-54`）——即"备份枚举"半边对预览资产**已自动覆盖**；基线"落点=常量 + 备份枚举 + 完整性门"中的"备份枚举"应改读为"预览**写侧**"（枚举已备）。
3. **D-01/D-02 表现状**：基线未展开；本会话以 schema 71 行号实证四张 visual 表全落（:9784/:9902/:10146/:10266），清单 D-01 行"DB v69 只有 figure 表"已滞后两代。

## 七、UNVERIFIED 与边界

- **UNVERIFIED**：
  - 设备面全部（本轨零设备执行）：D-04 备份/恢复 suite 的预览向量、D-06 打开旧消息设备臂、X-03 strictOffline 重放——均为验收名义（JVM/设备），批次 38 实施轨需真跑；
  - X-03 ①档"API/SDK/运行库兼容门"的现值判定逻辑（未读 QuickJsVisualExecutionEngine 的版本门全文，仅据契约 :198 分层与恢复 DTO 结构推断）——实施时重锚；
  - 远端仓库最新 release/issue 响应等维护面细节（gh api 只取了 push 时间与 stars，未做 issue 抽样）。
- **边界**：本文件只读研究产物；所有落点建议（常量/写侧/观测门/类型）属 core/data、core/database、core/model **非热区**；渲染面（core/ui）、UI 面（feature/*）、app 壳与 `VisualJsClientEngine` 均为禁改热区，涉及处的接线归热区窗口批并已逐处标注。
- **新增指认**（规则 12.2 自检）：本文每处"落点"均指认了它消灭的具体失败——D-04 预览写侧→"未知版本降级时只剩文字说明"；D-06 观测门→"resolver 被塞入网逻辑时无物报红"；X-02 证据类型→"本地有图被冒充为模型已看图"；X-03 设备向量→"0 派发只是口头承诺、无证据位"。未发现需要新增而指认不出失败的机制。

## 来源

- 仓内（path:line 见各节）：`CanonicalAssetReferenceSites.kt`、`PendingCaptureDao.kt`、`RoomStudyDatabase.kt:922-940`、`AndroidBackupRepository.kt`（:194-280/:374-386/:1192-1218/:1234-1310）、`AndroidCanonicalAssetVault.kt`（:44-50/:196/:273-281）、`StudyDbValue.kt:40-52`、`AttachedImageGenerator.kt`、`AttachedImageGeneratorFactory.kt`、`FigureHttpTransportBypassObservationTest.kt`、`FigureGeneration.kt:224-250`、`RemoteModelDispatchLedger.kt`、`FigureGenerationCoordinator.kt:41-87`、`DefaultFigureGenerationCoordinator.kt`（:497/:507/:632-648/:651-657）、`VisualSnapshotRestore.kt`、`TutorVisualDao.kt:61`、`TutorVisualCommitRepository.kt:103`、`TutorModelTaskPolicy.kt:115-141`、`OpenAiCompatibleModelGateway.kt:637`、`app/build.gradle.kts:51-62`、schema 71（:9784/:9902-9903/:10146/:10266/:9650-9651）。
- 文档：清单 v3 `:167/:169/:240/:241`；契约 `:37/:39/:149/:165/:176/:188/:190`；基线文 `docs/research/2026-11-01-batch-38-baseline.md` §五。
- GitHub（gh api，2026-11-01 实跑）：
  - https://github.com/coil-kt/coil （Apache-2.0；CachePolicy.kt 逐字取证）
  - https://github.com/android/nowinandroid （Apache-2.0）
  - https://github.com/restic/restic （BSD-2-Clause）
  - https://github.com/contentauth/c2pa-rs （LICENSE-APACHE + LICENSE-MIT）
  - https://github.com/in-toto/attestation （Apache-2.0）
  - https://github.com/sqldelight/sqldelight （Apache-2.0）
