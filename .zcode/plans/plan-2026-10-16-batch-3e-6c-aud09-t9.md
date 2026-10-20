# 下一批（十二轨）：3E 全库扫描 + 6C 备份扩展 + AUD-09 净化作业 + T9 语义修复 + 小修包（十二轨并行）

> 2026-10-18后续接线更新：本文件保留历史任务/裁定与执行范围，不作为重跑旧MCP、固定图像后端或旧schema的指令。后续转写/净化/讲解/编辑/备份中的新图接线统一遵循[共享规则](../../docs/current/figure-generation-prompt-policy.md)、[4E的WG清单](../../docs/superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md#10-全入口接线任务与后续阶段承接)及[后续阶段计划](plan-2026-10-18-followup-figure-wiring.md)；完成/未完成状态以[主线](../../docs/REFACTOR-MASTER-LINE.md)最新记录为准。

## 0. 现状（已核实）

- 上批九轨已收口并推送（远端 `fa265487`：`038d50c0`/`6a69c82b`/`09f0c85c`/`a49b4c4f`/`53770c18`/`173ae620`/`f96464e9`/`f1f42e3f` + KB 线批次 11）。
- **opencode zen 配额仍 429**（本轮实测探测）→ 设备模型轨继续受阻；设备轨改做无模型项 + 探测。
- T9 报告（`docs/research/2026-10-15-aud-11-...md`）四条裁定已获用户批准（2026-10-16）：
  ① 判词粒度 = **锚定 + 归属落账**；② 冷却 = **限 MODEL_CHAT**；③ 答案键 = **可观察性先行**（纠正语义留 5B）；④ 3E 第二档 = **每日 10 个知识点**（时间预算沿用 20 分钟）。
- 三个探索代理已把 3E / 6C / AUD-09 的代码现状摸清（下列轨内均带行号证据）。
- 工作树另有他线在飞文件（Agent 底层小修 7 个 + KB 线 4 个）——提交只列本线文件。

## 1. 十二轨（每轨一个实施子代理 + 各自测试）

**T1 KD-39 切分回执对账（AUD-25，medium）**
- 现状：`RoomCaptureWorkflowRepository.confirmAndCommit:439-495`（`withContext(IO)` 非事务）在 commit 之后调 `recordSplitImportCommit`（:483）；两步之间被杀 → 题已入库、台账 PENDING、无补偿（KD-39）。
- 修法（首选读侧对账）：`RoomSplitImportStore.readNextUnsubmittedDraft` / 复核页装配处——PENDING 题若其 `splitDraftId` 的草稿已 `COMMITTED`（`readProblemDraft` 可读状态）→ 就地补写回执（幂等 `markQuestionSavedByDraftId`）；启动对账（`SmartMistakeBookApplication:431` split 台账对账处）加同一补偿。退路：回执并入 `confirmAndCommitProblemDraftFromWorkspace` 同一写事务。
- 顺带裁定：`markConfirmed` 无生产调用方 → **保留 + KDoc 注明**（不删）。
- 验收：instrumented「提交后被杀（模拟：只提交不写回执）→ 重启/进复核 → 台账追平 SAVED 且续做下一题」；既有 3/3（core:data）+ 7/7（feature:library）不回退。

**T2 KD-40 status.md 绑定说明（low）**
- `tools/ci/generate_status.py` 的 run_context 输出头部补「绑定时刻 = 生成时刻，非所描述代码的提交」；`.github/workflows/status-template.md` 同步；既有 14 例单测更新。

**T3 KD-41 小测损坏行（low）**
- `KnowledgeQuizItemStore.save()` 遇「既有行解不开」→ 覆盖重写（先删后写或替换），让「重试出题」可恢复；补「损坏行 → 重试可恢复」用例；既有 5 例不回退。

**T4 KD-42 staging 中断可见（low，最小方案无 schema）**
- 批量导入入口在 staging 目录非空时显示「上次导入被中断，可重导」；读口加在 `AndroidBatchImportSourceStaging`；`BatchImportRoute` 加 banner。与 reconcileOrphans 60 分钟宽限交互：提示仅在存在未回收 staging 时出现。
- 验收：单测（staging 非空→提示为真；回收后为假）+ 设备核（造 staging 目录→进导入页→见提示）。

**T5 3E 全库薄弱点扫描（阶段级）**
- 修法：第二档候选源 = 全库「有绑定题的活跃知识点 ∩ 材料可出题」− 今日队列；打分复用 `KnowledgeNodeScorer` / `selectKnowledgeReviewQueue`（core:domain 纯函数）；与今日队列并集按分取前 N；**N = 每日 10 个**（新常量，入 `AlgorithmConstants.kt` 或 3E 专属常量，命名 `FULL_LIBRARY_SCAN_DAILY_CAP` 类）；时间预算沿用 `reviewTimeBudgetSeconds`（20 分钟）双封顶。
- 需要的新读口：全库「每节点绑定题 id」（`KnowledgeReadPort` 扩展，避免 N+1；5000 节点级性能按 S9/S17 纪律）；quiz-able 全库过滤复用现有材料查询。
- spec 同批改写：`docs/specs/2026-09-06-dual-review-entry-design.md` §0:9 / §1:15 / §3.2:40 / §5-P3:69（文档与代码同批，不许只改一处）。
- 验收：单测（弱掌握点离开今日错题并集仍可捞回；每日 10 封顶生效；无绑定题节点不在范围；时间预算落选）；仓库层既有 `currentKnowledgeReviewPlanKeepsOnlyQuizAbleScopeFromTodaysQueue` 改写为新范围语义（**保留原断言作为子集断言，不许删弱**）。
- 版本：`KnowledgeNodeScorer` 不携带版本（知识点队列非持久化，ledger §3.12）→ 预期零 bump；若实现中落库/指纹化则按纪律 bump 并登记。

**T6 AUD-09 净化作业持久化与故障续跑（4D）**
- schema 65→66（非破坏迁移 + 迁移矩阵更新）：新表 `clean_redraw_task`——identity(`revisionId`,`sourceAssetId`,`sourceImageSha256`,`requestFingerprint`)、status(`PENDING`/`SUCCEEDED`/`FAILED`/`SUPERSEDED`)、`attemptCount`、`lastFailureCode`、时间戳。
- 行为：① classify 指纹加源图字节 sha（现 `ModelTasks.kt:250-266` 只含 id/尺寸）；② 同指纹已 SUCCEEDED → 跳过不重复付费；③ 失败落 `failureCode`（不再吞成 false/null）；④ 启动对账：PENDING/FAILED 且 `attemptCount < MAX_REDRAW_ATTEMPTS` → 重试（沿用 BatchImportDriver/ExportPdfWorker 先例；出网门/凭证门照旧 fail-closed）；⑤ 晚到结果：attach 前核「修订仍存在且为该草稿最新」→ 否则标 SUPERSEDED 不挂；返回值不再丢弃（记录）。
- 触发范围口径写进 KDoc/测试：仅提交成功触发；TEXT_ONLY 不动作（现状保持）。
- 验收：单测（指纹含 sha、同指纹跳过、失败落码）+ instrumented（PENDING 行重启后被重试；SUPERSEDED 不挂新修订）；既有 capture 仪器化不回退。

**T7 6C-1 备份扩展：设置迁移 + 元数据版本戳 + 旧包兼容（阶段级，第一批）**
- manifest `formatVersion` 1→2；v2 新增 `appVersionName`/`appVersionCode`/`contentVersion`（`content_install_state.content_version`）/`hasSettings`/`settingsDigest`；新 app 读 v1（无 settings，行为不变）与 v2；写永远 v2；>2 拒（既有文案）。
- 包内新增 `settings.json`（自带 schema 版本）：`desired_retention`/`exam_calendar`/`optimized_fsrs_parameters`/提醒 `enabled`+`minutes_after_midnight`；**排除** `notification_delivery_history`、API Key、授权租约、sleep journal。
- 恢复：设置版本兼容校验**在交换前**（未知版本 → 可读拒绝）；交换成功后写入 DataStore（失败 → 恢复回执标注 partial，不回滚已一致的 DB）；提醒不复活授权（只存偏好）。
- 验收：codec 单测（v2 round-trip、v1 兼容读、>2 拒、settingsDigest 校验）+ instrumented（备份→改设置→恢复→设置往返；v1 旧包恢复不炸）；既有 SmbkArchiveCodecTest 13 例 + BackupRestoreInstrumentedTest 不回退。
- 边界：**内容勾选（行级子集）不在本批**——整库快照形态下的子集导出需独立设计，登记为 6C-2。

**T8 T9① 判词粒度：锚定 + 归属落账（AUD-11，已裁定）**
- `TutorJudgedReviewSettler`（:252-255 取数、:282 落 0.5、:138-139 空归属）：整题判决要求 ≥1 条 accepted 证据锚在本题 KC 集（`MistakeRecord.knowledgeNodeIds`）；只有外部节点证据 → `NO_VERDICT`（保持阻塞、可见）；判决快照 `attributions` 记录驱动证据（节点 + 证据 id）。
- KD-36 修的「最近锚点会话」逻辑不动。归属落账若进入持久化指纹/投影 → 按纪律 bump；否则注明零 bump 依据（**实施者必须核实并写明**）。
- 验收：前置 KC 证据不再翻转整题（NO_VERDICT）；同节点证据仍判；无关节点证据 NO_VERDICT；attributions 非空且指向驱动证据；既有判定链测试不回退（`TutorChoiceSubmissionFailureTest` 等）。

**T9 T9② 冷却限 MODEL_CHAT（AUD-11，已裁定）**
- `ChatEvidenceDao.lastAcceptedChatEvidenceAtForKc`（:104-109）加 `source_kind='MODEL_CHAT'` 过滤（或门输入加通道维，取实现最小者）；小测通道靠自身幂等身份防重；UI 文案按真实拒因修正（`KnowledgeReviewSessionScreen:300`、`TutorToolTrace:371` 同查）；`ChatEvidenceGateCalibration` 分桶加 `source_kind` 维。
- 验收：instrumented（模型写后小测写放行且落库；反向亦然；小测同身份重放幂等不变；严格 `<` 边界保持）；既有 AUD-01/02 用例不回退；零 bump 依据写明。

**T10 T9③ 可观察性先行（AUD-11，已裁定）**
- ① 「accepted 被 IGNORE」变可观察（`insertAsLedgerEvents` 回报/日志，写入口不再无感知）；② 审计面：证据行 ↔ `assessment_item_snapshot` 可按 requestId/session 对上（补最小查询口）；③ 证据载荷带题面/键版本引用（最小改动面）。
- 纠正语义不动（留 5B）；**登记 KD-43**（答案键无纠正/撤销/重放落点 + 身份粘性静默丢写）到 `docs/known-defects.md`。
- 验收：单测/instrumented（IGNORE 被回报；审计查询能把证据行对上题面；既有幂等语义不变）。

**T11 KD-38 剩余 + 台账登记（小修）**
- `requireSafeModelText` 截断（`take(n)`）不切代理对；补 emoji 边界用例。
- `docs/research/algorithm-version-ledger.md` 补 v19/v26 一行（上批复核 low 遗留）。

**T12 设备轨（配额 429 已探）**
- ① 开工再探测一次配额：若恢复 → KD-37 UI 端到端（失败→重新判定→成功）+ KD-38 前后对照 + AUD-16 剩余（第二次复习/异常恢复/导出）按序做；仍 429 → 如实记未达（不重试、不换凭据）。
- ② 无模型项照做：AUD-05 恢复拒绝设备核（造包→删引用资产→恢复应拒）；KD-42 中断提示设备核；AUD-09 净化作业持久行设备核（模型不可达时 PENDING→FAILED 落码；重启续跑）。
- ③ 全程留证（命令 + 关键输出 + 截图）。

**T13 收尾（门禁 + 独立核对/复核 + 记录 + 同步）**
- 门禁：七模块单测（core:model / core:domain / core:data / core:database / core:export / feature:review / feature:library）+ 两个 androidTest 编译（core:data、feature:library）+ 整包编译（app:assembleLocalFirstDebug）；**既有红点名**（core:data 金标检索 4 条 KB 线在飞；SECTION facet 边际门环境红）照录，不许改绿。
- 独立核对（对设备材料逐条复核）+ 对抗性复核，专盯：对账补偿与幂等/并发冲突；3E 是否误纳「无绑定题节点」；AUD-09 重试是否可能重复付费；6C 设置恢复是否泄露 Key/复活授权；判词锚定是否误伤合法判定；冷却收窄是否打开洪泛面。
- 记录（批次记录文件）+ 主线 §7 与台账折入 + KD-43 登记 + T2 可变缝 KDoc 注记（保留 + 默认 null 断言）。
- 提交：显式文件列表（共享树纪律，只列本线文件）；**推送前问 owner**。

## 2. 未验证边界与风险（照录）

- 设备模型轨仍可能全程未达（配额）；KD-37/38 设备级证据不因本批自动补齐。
- T8/T9 改判词/冷却语义——判定链刚经 KD-35/36/37 修复，回归面必须点名（既有用例不许删弱）。
- T6 schema 65→66：与 KB 线内容版本无冲突（KB 不动 schema），但迁移矩阵测试必须同批更新。
- T7 只做 6C 第一批；内容勾选/行级子集 = 6C-2（需独立设计）。
- T5 性能：全库扫描查询须避免 N+1；规模断言按既有 S9/S17 纪律。
- 共享树：他线在飞文件（Agent 底层小修 + KB）不入本线提交；提交后如他线又前进，按区域核。
