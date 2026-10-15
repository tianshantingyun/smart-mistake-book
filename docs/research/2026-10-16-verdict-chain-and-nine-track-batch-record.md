# 九轨并行批记录：判定链收口 + 切片补走 + P1 先修工程批（2026-10-15/16）

计划来源：`.zcode/plans/plan-sess_972ed30a-b136-40b0-8362-e7e2a8be3065.md`（T1–T9 九轨 + 设备/独立核对/独立复核）。
本记录只记事实并逐条注明来源（哪条轨自述、哪台设备/复核实测、哪些是本记录者本轮实测）；未执行的检查一律写
「未跑 / UNVERIFIED」，不当作通过。

**批次口径（开工给定）：** 强门禁 = true（既有红点名）；共享工作树。
**提交状态：** 全部改动在工作树、**未提交**。本记录者实测：`git log --oneline -5` HEAD=`f094b9ad`；
`git diff --cached --name-only | wc -l` = **0**（没有任何暂存内容）——即 KD-37/38 等修复均属「提交待补」。

---

## 0. 记录者本轮实测的核对（不依赖他轨自述的部分）

| 核对 | 命令 / 方式 | 结果 |
|---|---|---|
| 提交状态 | `git log --oneline -5`、`git diff --cached --name-only \| wc -l` | HEAD `f094b9ad`；暂存 0 |
| 树规模 | `git status --porcelain` | 482 条 = 82 个已跟踪文件被改 + 400 个未跟踪条目 |
| KD-37 修法 | 读 `AssessmentEvidenceSnapshotIdentity.kt:23-30`、`AttemptTransactionDao.kt:642-662` | 时间戳容忍比较在树内；`:651` 注释「时间戳不是快照身份的一部分」 |
| KD-37 用例存在性 | grep 四个用例名 | `StudyDatabaseInstrumentedTest.kt:911/972` + `AssessmentEvidenceSnapshotIdentityTest`（4 例）均存在 |
| KD-38 修法 | 读 parsers/adapters/ModelEgress/ModelTasks/gateway 对应行 | 6 处截断点（`:353/:357/:364/:408/:633/:646`）、两处提示词声明（`:252/:349`）、版本 `:55/:67`、异常 `ModelTextBudget.kt`、超预算抛点 `ModelTasks.kt:1219-1224`、网关两 catch（`:281-285/:441-452`）与常量 `:891-895` |
| 旧提示词字面量 | `grep -rn figure-conditional`（活代码/活文档） | 活代码零命中；仅历史文档 3 处 + `.artifacts/`、`build/agent-batch11/clean-head/` 快照副本 |
| APK 与修复 | `sha256sum` + python zipfile 读 dex 逐串计数 | APK 205,979,834B、sha256 `ecd9e905…94ea57`；dex 命中 `sameEvidenceSnapshotIdentity`×1、`ModelTextBudgetExceededException`×2、`tutor-plan-v19-move-label-budget`×2、`tutor-respond-v26-move-label-budget`×2、`exceeds budget`×8、`OUTPUT_BUDGET_EXCEEDED`×2 |
| 构建红根因 | grep `isCoreLibraryDesugaringEnabled`/`coreLibraryDesugaring` | `feature/library/build.gradle.kts` = 0 处（diff 只加 2 行注释 + 2 行 `androidTestImplementation`）；`core/data:22/:168`、`core/database:18/:30` 均有 |
| KD-33/34 残留 | grep + `git status --` | `OpenAiModelResponseParsers.kt:127-128` 注释仍在；`CaptureWorkflowRepository.kt:427` 第三份 `2..12` 仍在；`CaptureAssessmentTasks.kt` / `ModelTaskTransactionDao.kt` / wire 三协议族 / `RoomBatchImportRepository.kt` 本批均无改动 |
| 派遣预算改写 | 读 `RoomModelTaskRepository.kt:730-744` | retryable + 预算用满 → `PERMANENT_FAILURE`（对超预算失败码同样生效） |
| 状态页 | 读 `docs/status.md`（head、`:193`） | `Commit SHA a15aebb7…`、Run context `BOUND · sha a15aebb715ca（git HEAD）`、Overall `NOT_MEASURED`、tools tests `737/737 passed` |
| T7 索引状态 | `git status --porcelain --` 两个导出测试文件 | 均为 ` M`（仅工作树改动、**未暂存**）——与 T7 自述的「MM」不同，记录为现况 |
| T9 报告落盘 | `git status --porcelain` | `docs/research/2026-10-15-aud-11-verdict-granularity-cooldown-answer-key.md` 存在（`??`） |

**本记录者未做**：没有运行任何 Gradle、没有跑设备、没有执行任何被点名的测试用例。下文所有「通过」都注明是谁跑的。

---

## 1. 九轨结果总表

| 轨 | 交付 | 结果 | 实跑证据（来源） | 未达 / 未验证 |
|---|---|---|---|---|
| T1 判定链收口（KD-37/38） | 14 文件：时间戳容忍比较 + 解析期预算截断 + 专用异常→可重试 + 提示词/版本 bump + 用例 | 修法落地、用例落盘 | KD-37 instrumented **2/2**（设备一实跑）；JVM 定向（独立复核实跑，见 §3.5） | T1 自身**零编译零测试**；设备级 UI 端到端未达（配额） |
| T2 失败诊断通道（debug-only） | 7 文件：诊断缝 + debug 写入 + 广播开关 + 7 条用例 | 代码落地、用例已写未跑 | T2 自跑 3 项非 Gradle 检查；设备一武装成功（16:22:39 日志） | 未触发（能力测试 429 不走该缝）；真 adb 广播投递未验证 |
| T3 切片补走（设备） | 设备走查三节 | **仅录入中断拿到正面结论** | 设备一/二/三 | 第二次复习 / 复习中断 / 讲题中断 / 导出未达；时间旅行**未做** |
| T4 AUD-03 切分逐题提交 | 17 文件：回执窄接口 + DAO 反查 + 逐题续做 + 用例 | 代码落地；core:data 仪器化 **3/3 绿** | 设备三实跑 3/3；独立核对自己重跑构建红 | **feature:library 两个新类构建红（0 用例执行）**；设备走查未达；回执窗口（独立复核 medium） |
| T5 AUD-15 Lint + 状态页诚实化 | 7 文件：码点扫描 + 生成器诚实口径 + 14 例单测 + 模板/状态页 | 代码落地 | T5 自跑 `python -m unittest discover -s tools/tests -t tools` = **737/737 OK（241s）**；独立复核实跑 DenseTokenizerParityTest **7/0/0** | `:feature:library:lintDebug`/`:core:data:lintDebug` 未由本批执行；status.md 绑定偏差（复核 low） |
| T6 AUD-05 备份一致性 | 7 文件：快照闭合 + codec 双向拒绝 + 3 条新用例 | 代码落地；SmbkArchiveCodecTest **13/13**、BackupRestoreInstrumentedTest **22/22 绿** | 设备三实跑 | T6 自身零测试执行 |
| T7 AUD-06 PDF 缓存键 | 2 个测试文件（实现属他线，只补测试） | 5 条新用例；T7 自跑 JUnitCore **OK (19 tests)** | T7 自跑（kotlinc 直编 COMPILE_OK；红灯变体 V-A/V-B 各 5 failures） | 项目门禁未跑；文件未被暂存 |
| T8 AUD-01/02 小测身份与恢复 | 18 文件：身份纳入会话 + 题面持久化 + 恢复不出网 + 用例 | 代码落地；五类用例全绿 | 设备三实跑：3/3、2/2、5/5、17/17、2/2 | app 层两条接线无单测；损坏行重试死路（复核 low） |
| T9 AUD-11 反例测量（研究轨） | 报告 `docs/research/2026-10-15-aud-11-verdict-granularity-cooldown-answer-key.md` | 三条反例/测量**成立**、建议留裁定 | T9 在冻结快照 `D:\aud11-measure` 实测（JVM 3/3 + connected 10/10） | 快照口径（两棵树当时编译不过）；UI/端到端未跑 |

---

## 2. 各轨明细

### T1 判定链收口（KD-37 / KD-38）

**KD-37（「重新判定」死路）**：`AttemptTransactionDao.saveEvidenceSnapshot` 的整实体比较改时间戳容忍——
新纯函数 `sameEvidenceSnapshotIdentity`（`AssessmentEvidenceSnapshotIdentity.kt:23-30`，`:6-22` KDoc 说明
「时间戳不是快照身份的一部分」）；同 `snapshotId` 且除 `capturedAtEpochMillis` 外全等（含归属行）即复用旧行、
返回 false；身份/内容/归属真变仍抛 `ImmutablePayloadConflictException`；`existing == null` 显式归到不可复用
（与旧行为等价）（`AttemptTransactionDao.kt:642-662`）。用例：JVM 4 例 + instrumented 2 例（复现
「首落 T1 → advance 失败回滚 → 同 snapshotId 换 T2 重写 → 重试成功且回读仍是 T1」，反例钉仍抛）。
**指针更正（T1 自述）**：任务书写的先例 `AttemptTransactionDao.kt:374-391` 实为 `recordAnswerReveal`；真正先例是
`ModelTaskTransactionDao.kt:374`（`sameImmutableTutorResponse`）/ `:385`（`withoutTutorResponseTiming`），已按后者实现。

**KD-38（动作标签超 32 字符整轮永久失败）**：① 解析期按格式预算截断短标签（动作标签 32 / 知识标签 96 / 锚词 64，
截断在 `distinct()` 之前；构造器未放松）；② 两条提示词补预算声明并 bump 版本
（`tutor-plan-v19-move-label-budget` / `tutor-respond-v26-move-label-budget`）；③ 超预算改抛专用
`ModelTextBudgetExceededException : IllegalArgumentException`，网关归可重试
`OUTPUT_BUDGET_EXCEEDED`（code=`INCOMPLETE_OUTPUT`、retryable=true），形状错误仍不可重试；④ 用例。
详见 `docs/known-defects.md` KD-38 的「修法 / 验收证据 / 未达 / 剩余」。

**T1 自述的未做与风险**：本轨**未运行任何 Gradle**（自写 python 静态括衡扫描不是编译器）；他线当时在飞
`OpenAiCompatibleModelGateway.kt` 与 `OpenAiCompatibleModelGatewayTest.kt`（日志上报口改写），合并时按区域核；
UI 文案未分叉；`TutorDebriefOutput.teachingFocusLabels` 未做截断；台账未登记 v19/v26。

### T2 讲题失败构成诊断通道（debug-only）

- 新增默认关闭诊断缝 `ModelCallFailureDiagnostics.sink` + `ModelCallFailureRecord`（响应原文不进 `toString`）；
  网关 7 处失败点统一走 `reportModelCallFailure`，其中上游非 2xx 分支此前**完全没有落点**，现在带
  `httpStatus` 与错误响应体；原日志行行为逐字不变。
- debug 专用写入 `DebugModelFailureCapture`（`filesDir/debug-model-failures/`、文件名带时间/序号/任务、
  原文截到 64 KiB 且不切代理对、目录封顶 20 条、写盘失败不抛出但留 tag）；广播开关
  `TestCaptureModelFailuresReceiver`（默认关）在 `app/src/debug/AndroidManifest.xml` 声明。
- 用例 3 条 JVM + 4 条 instrumented，**T2 侧未执行**；T2 自跑的非 Gradle 检查：python 复刻
  `truncateToUtf8Bytes`（5 组输入全过，720000B→65535B、mod 3=0）、`check_release_manifest.py` 的
  `debug_only_component_names()`（输出含 `TestCaptureModelFailuresReceiver`，共 4 个 debug 组件）、
  `check_file_size_gate.py` 行数（main 875 / main 112 / test 2614，均只告警）。
- 已知边界（T2 自述）：开关是**进程内状态**（重启回默认关，需重发广播）；捕获覆盖限于网关出网之后的失败
  （不含能力探测、不含无响应体的本地前置失败）；同毫秒序号与淘汰顺序不保证。
- 结构取舍待裁定：网关在 `core/data/src/main`、写入实现在 `app/src/debug`，因此 main 保留一个默认 null 的
  公共可变缝（release 恒 null 无 IO）；仓库 `docs/architecture-research-2026-09-22.md:222-225` 把「main 里的
  可变全局接缝」列为反面发现，形态相近，T2 请 owner 裁定。
- 设备侧：设备一武装成功（logcat `ModelFailureCapture: model failure capture ON`，16:22:39），但一条都没写出
  ——因为能力测试的 429 走的是 `ModelCapabilityTester.kt:209/219` 自己的记账，不经过该缝；该武装状态随后
  随卸载重装消失（独立核对）。

### T3 切片补走（设备为主）

见 §3 设备一/二/三。**唯一正面结论**：整卷导入在 **job 建立之后**被杀可完整续跑（WorkManager 重启把
`PROCESSING` 推到 `COMPLETED`、5/5 页 `READY`、无重复行、无新模型调用）；**新观测缺口**：job 建立**之前**的
staging 阶段被杀，该批静默丢弃（只留孤儿 staging 目录）——独立核对补正：该目录非永久残留，
`ORPHAN_GRACE_MILLIS=60min` + `reconcileOrphans` 会在下次启动且超宽限后回收（`AndroidBatchImportSourceStaging`
常量、`RoomBatchImportRepository.kt:89`）。其余四项（第二次复习 / 复习中断 / 讲题中断 / 导出）未达，
原因是同一组硬前提：库中零题 + 模型侧被本地能力门 fail-closed 钉死 + opencode zen 周配额耗尽。
**未做时间旅行**（无题可到期），设备时钟实测保持真实时间、无待还原项。

### T4 AUD-03 切分多题真实提交与 SAVED 语义

- 复核页「录入所选」不再预标 SAVED：只挑出 selected 且 `confirmState==PENDING` 中序号最小、带 `splitDraftId`
  的那道题交给 `onOpenDraft`；没有可打开的未提交题才 `complete(jobId)`（`SplitImportReviewRoute.kt:190-210`）。
- 新增跨层窄接口 `SplitImportCommitReceipts`（core:domain，两条：提交回执置 SAVED / 逐题续做），
  `SplitImportRepository` 继承它；core:database 加反查口（`readQuestionBySplitDraftId`、
  `markQuestionSavedByDraftId` 幂等、`readBySplitDraftId`），port 与两处 fake 同步。
- 回执写入点：`RoomCaptureWorkflowRepository.confirmAndCommit` 拿到真实回执后（重绘之前）调
  `recordSplitImportCommit`；`saveTutorSession` 同样回写（讲题来源的切分题）。
- UI 续做：`CaptureScreen` 新增 `splitImportReceipts` / `onOpenNextSplitQuestion` +
  `LaunchedEffect(committedEntryId, draftId)`；`SmartMistakeBookRoot` 的 `Routes.CaptureResume` 接上窄接口与
  `popUpTo(CaptureResume,inclusive)+navigate(captureResume(next))`；`SplitQuestionCard` 的「已录入」只看
  `confirmState==SAVED`，PENDING 显式写「待提交」。
- 测试基座：`feature/library/build.gradle.kts` 加两条 androidTest 依赖——**这就是 §4 构建红的断口**。
- 设备三实跑：`core:data` 仪器化 3/3 绿（真实 Room，逐题回执语义三条）；`feature:library` 两个新类
  **构建不过、0 用例执行**。
- T4 自述未做：零 Gradle；用 python sqlite3 按迁移 DDL 建表实跑新 DAO 查询（命中/更新/miss/FK 生效）；
  `markConfirmed` 现已无生产调用方（保留未删，待评审裁定）。
- 独立复核（medium，读码级）：SAVED 回执写在提交事务**之外**，两步之间被杀/被取消会「题已入库、台账仍
  PENDING」，且无补偿路径（`readNextUnsubmittedDraft` 排除刚提交的草稿；复核页不放弃；草稿 `COMMITTED`
  读不回）——该分题任务永久停在「还有一道待提交」。窗口窄（两次相邻数据库调用之间），未设备复现。
- 独立核对更正一条归因：两页 `split_after_status=SETTLED` 的 `updated_at`（17:13:40）早于点击时刻（17:20），
  来自导入驱动自身那一趟 settle，不是按钮点击所致。

### T5 AUD-15 Lint 6 错 + 状态页诚实化

- `DenseTokenizer.kt:165/191-213` 的 `piece.codePoints().toArray()`（API 24 默认方法，minSdk 23）换成手写码点
  扫描 `codePointsOf`（`codePointAt` + `Character.charCount`，API 1），语义逐码点等价；新增回归用例
  `DenseTokenizerParityTest`「word piece scans code points not utf16 units, including unpaired surrogates」
  （独立复核实跑 7/0/0）；等价性另有一次性 JVM 探针
  `build/agent-cpscan/CodePointScanProbe.java`（全 65536 BMP + 1000 组代理边界 + 50 万随机串，输出 ALL MATCH，
  T5 自跑；探针在 gitignored 的 `build/` 下不入库）。
- 另外 5 个 NewApi（`feature/library` 的 `java.time` 日期调用）**不是本轨所改**：会话开始时该文件已是未提交的
  等价替换（`SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)` + `Date`），
  `docs/research/2026-10-15-user-visible-fixes.md:22` 已记载同一处置（「没有提高最低版本或抑制 Lint」）；
  T5 只做静态核对、未再动。
- `tools/ci/generate_status.py` 诚实化：lint 纳入 9 个库模块（清单与 `android-check.yml:100-112` 一致）+
  逐模块明细；编译/lint 证据做新鲜度判定（旧产物一律 NOT_MEASURED）；`JOB_STATUS` 去掉 `"success"` 缺省；
  新增 `run_context()`（COMMIT_SHA/BRANCH 与 `git rev-parse HEAD` 核对，不一致即 UNBOUND）；
  Overall 改三值（全绿且全部测到才 PASS）。
- `tools/tests/test_ci_generate_status.py` 新增 14 例；T5 实跑
  `python -m unittest discover -s tools/tests -t tools` = **Ran 737 tests / OK（241.234s）**（723 + 新增 14，
  未触碰 `tools/kb_*`；「19 项工具红」为已由 KB 批 10 收口的历史项）。
- `docs/status.md` 按新口径重生成（T5 实跑生成器两次）：本记录者实测该文件 head 为
  `Commit SHA a15aebb7…`、`Run context BOUND · sha a15aebb715ca（git HEAD）`、`:193` Overall = `NOT_MEASURED`
  （build job / lint / compile evidence 均 NOT_MEASURED，tools tests 737/737 passed）。
- 独立复核（low）：该页会随本批提交，却指向本批之前的提交（当前 HEAD 已 `f094b9ad`）；`run_context` 的 bound
  判据只是「git HEAD 读得到」，本地生成必然显示 BOUND。
- 未跑：`:feature:library:lintDebug` 与 `:core:data:lintDebug` 未由本批执行（T5 按纪律不跑 Gradle）；
  状态页的单测 XML / APK / APK sha 仍是「文件存在即取数」，未做新鲜度判定（T5 自述的刻意未越界）。

### T6 AUD-05 备份一致性

- 成包顺序改「先快照库 → 按快照库反查引用资产 → 只收这些资产」；每条被引用资产按 vault 单一判据核对
  存在/字节数/sha256，缺失或被改动即成包失败（不再静默跳过）；`problemCount` 读快照库端口；
  codec create 新增必填 `referencedAssets`（被引用资产必须真在包内，否则 IllegalArgumentException 且不落半包），
  引用集合写进 manifest（默认空表）；恢复前置闭合 `verifyStagedAssetClosure`（DB→资产对拍，缺一即拒并列出
  缺失项）；`inspectStagedDatabase` 顺带读归档库自己的题数与引用，恢复回执不再照抄 manifest。
- 数据库层：`CaptureReadPort.readReferencedCanonicalAssets`（默认实现抛异常而非返回空表）+
  `PendingCaptureDao.findAllCanonicalAssets` + `RoomStudyDatabase` 实现为「全表行 − 未引用行」的补集
  （四表引用口径仍只有 `findUnreferencedCanonicalAssets` 一处）。
- 设备三实跑：`SmbkArchiveCodecTest` **13/13**（含 3 条新增）、`BackupRestoreInstrumentedTest` **22/22**
  （含 2 条新增）全绿。
- 独立复核：不会误拒合法包（窗口内新登记资产是「不入选」而非「被拒」；`includeAssets` 恒 true；
  旧包 `referencedAssets` 默认空表行为不变）；路径形状要求与 vault `resolve` 判据同源。
- T6 自述未做：零 Gradle、零编译（一次「仅解析」探针被安全 hook 拦下，未绕过）；`formatVersion` 保持 1；
  create/restore 都绑定默认库名；`BackupOptions.includeAssets=false` 的将来形态会 fail-closed。

### T7 AUD-06 PDF 缓存键补测试（只补测试）

- 归属判定（T7 先查）：两处未提交实现改动属他线在飞（`docs/research/2026-10-15-user-visible-fixes.md:18`
  的 AUD-06 行 + `.artifacts/user-visible-fixes-2026-10-15/`；T7 原文记的 `:16` 与实测差 2 行），故本线
  **只补测试、未重改实现**（`MistakePdfEligibility.kt` / `MistakePdfBatchEligibility.kt` 逐字未动）。
- 新增 5 条用例（单题 3 + 批量 2）：clean-image 身份/字节变必换指纹、未渲染字段不误失效（含正对照）、
  相同内容重跑命中同一指纹、批量可见备注与净化图三变必换整批键、批量未渲染字段稳定 + 重跑命中。
- T7 实跑（非项目门禁）：kotlin-compiler-embeddable 直编两个测试文件（COMPILE_OK, exit 0）+
  JUnitCore 直跑 → **OK (19 tests)**；红灯敏感性：V-A（HEAD 实现变体）Tests run 19 / Failures 5、
  V-B（去 includeNote 守卫的误失效变体）Tests run 19 / Failures 5（证明「不误失效」方向也被钉住）；
  变体脚本与日志在 `%TEMP%/aud06/`，未写入仓库。
- 现状更正（本记录者实测）：两个测试文件现在都是 ` M`（仅工作树改动、**未暂存**）——T7 自述的「MM」
  已不成立；提交者需 `git add`，否则新增用例不会进提交（门禁仍会从工作树跑到）。
- 未覆盖：导出缓存命中端到端（`MistakePdfExporter.kt:120`）与设备侧未做。

### T8 AUD-01/02 小测身份与恢复

- AUD-01：提交身份纳入会话分量且不再由界面拼串
  （`knowledgeQuizSubmissionId = "knowledge-quiz:review:<会话起点>:<题面>:<选项>"`，
  `KnowledgeReviewSessionViewModel.kt:316-317`；屏幕不再拼串）；`ChatEvidenceDao.insertAll` 改 `@Insert IGNORE`
  返回 `List<Long>`，`insertAsLedgerEvents` 只对 −1 之外的新行分配序列并写 outbox——同身份重放＝静默 no-op，
  outbox 保持 ABORT 作完整性哨兵。
- AUD-02：新 `KnowledgeQuizItemStore` 把题面按行身份写进**既有** `assessment_item_snapshot`（不新造表、
  无 schema/迁移；`options_snapshot` 存选项 JSON、`answer_spec_snapshot` 存带 schema 版本闸的答案规格），
  落库后回读返回库内那一行（首见即真）；恢复态语义反转——持久化的 LOADING/LOADED 不回 IDLE，改走只读
  `restoreCurrentQuiz`（读不到→FAILED 失败卡，不自动重出题、不出网）；分支规则抽成纯函数
  `knowledgeQuizLoadPath`；`SmartMistakeBookRoot` 把 restoreQuiz（只读）/ loadQuiz（生成后落库再交库内那一行）
  两条回调分开。
- 设备三实跑：core:data 仪器化 3/3 + 2/2、JVM 5/5、feature:review JVM **17/17**、app 仪器化 2/2，全绿。
- 替换（不是删除）一条旧语义用例：`restoredLoadedStatusReopensTheLoadPathAfterProcessDeath` →
  `restoredLoadedStatusKeepsTheQuizForReadBackInsteadOfRegenerating`（原用例钉的正是本次判为缺陷的旧语义）。
  两个既有幂等用例保留未删（`git diff` 为空 + 设备三实跑绿）。
- 未验证 / T8 自述残余：未跑 Gradle（零编译级验证，仅逐行读改 + 括号配平粗检）；force-stop 口径＝
  SavedStateHandle 可覆盖系统回收，force-stop/划掉任务＝新会话（新题面/新证据身份）；
  KNOWLEDGE_PREPARING 仍回 IDLE 自动重取；损坏行是硬出口（「重试出题」在该角落是死路）；
  呈现行无清理/保留策略（线性增长，列为残余风险）；`assessment_presentation` 未采用；
  `tutor_content_snapshot_id` 被复用来承载题面会话身份。
- 独立复核（low，findings 5）：`KnowledgeQuizItemStore.save()` 在「既有行存在但解不开」时返回 null →
  该知识点落 FAILED，且每次「重试出题」都重新生成、必再撞同一身份、再丢弃——每次白烧一次模型调用；
  是否接受该形态待裁定。

### T9 AUD-11 反例与分层测量（研究轨，不改产品）

报告：`docs/research/2026-10-15-aud-11-verdict-granularity-cooldown-answer-key.md`（本记录者实测文件存在）。

- ① 判词粒度：多 KC 题面上「前置 KC 有证据、整题未做对」被判成整题正确（`TutorJudgedReviewSettler.kt:252-255`
  只按会话取数、按 `created_at` 取最新、无节点过滤）——**成立**；较新那条换成完全无关节点，结果逐位相同
  （节点身份不参与）；同一句话在 KC 账本 0.075、在整题账本 0.5（放大 6.67×）。
- ② 冷却分层：12h 冷却与通道无关，模型写之后独立小测写被拒（`SAME_KC_IN_COOLDOWN`）——**成立**，两棵树都测了方向；
  一晚讲题 + 当晚全对 → 客观证据 0/3 落库；放到 13h 后 → 3/3 落库。
- ③ 答案键：错键判错后**纠正/撤销/重放的落点都不存在**——**三种落点实测都不存在**；附带测到「身份粘性导致的
  静默丢失」（先被拒的写占住 evidence_id，冷却过后同身份合法写通过门、回报 Accepted，但落库被 IGNORE 吞掉）。
- 实测口径（T9 自述）：测量跑在冻结快照 `D:\aud11-measure`（git worktree `a15aebb7`，叠加 2 项），因为
  测量时 HEAD 与工作区**当时都编译不过** `:core:data`；快照**不含**工作区在建的 AUD-02/AUD-05/KD-38 改动，
  因此报告 §3.2 的 store 结论是**读码、未执行**；三个测试类只在快照目录、未复制进工作区。
- 三条建议（留裁定）：整题判决需锚在题面自己的 KC 集 / 整题权重由生效证据派生 / 判决快照写进驱动节点；
  12h 冷却限定 `source_kind='MODEL_CHAT'`（或给 KNOWLEDGE_QUIZ 单独更短自锁窗）+ 按真实拒因改写 UI 文案 +
  把 source_kind 加进校准分桶；明确错键纠正语义 + 单独登记「Accepted 但静默丢失」+ 与键持久化口径对齐。

---

## 3. 设备证据

### 3.1 设备一（判定链复验；PIXEL/API 34 emulator-5554）

| 检查 | 结果 | 关键读数 |
|---|---|---|
| 前提核对 | 未达（前提不成立，按其规则重建） | 配置不在设备上（datastore 只有 `sleep_journal.preferences_pb`）、库内零题 |
| `install -r` 新包（未清数据） | 通过 | APK 205,979,834B / sha256 `ecd9e905…94ea57`（构建 2026-10-15 23:49，晚于 KD-37/38 源改动 22:05–22:31）；`firstInstallTime` 保留、DB 396MB、KB 计数不变 |
| 包内带两处修复（dex 扫描） | 通过 | `sameEvidenceSnapshotIdentity`、`ModelTextBudgetExceededException`、两个新提示词版本、`exceeds budget` 均在（**本记录者复算独立通过**，见 §0） |
| 模型配置重建 | 通过 | 广播播种 `opencode zen` / `https://opencode.ai/zen/go/v1` / `deepseek-v4.1-flash`；首两次因词切分静默失败（如实记录） |
| T2 捕获通道武装 | 通过（已武装、未触发） | logcat `model failure capture ON`（16:22:39）；目录为空——能力测试 429 不走该缝 |
| KD-38 设备前后对照 | **未达** | 429 `GoUsageLimitError` / `limitName=weekly`（应用内 bodyBytes=168；host 直连复现；加 `x-opencode-session` 仍 429）；链条断在第一环，计划失败次数/成功/失败原文分类**一项未测** |
| KD-37 设备「结算后连点重新判定」 | **未达** | 同配额原因，库内相关表均 0 行，不存在可触发状态 |
| KD-37 死亡路径的权威证据 | 通过（DAO 层） | 见 §3.3 与本记录 §0；**不等于 UI 端到端** |
| 崩溃计数 | 通过 | 本轮新增崩溃 0（另：`logcat -c` 清过缓冲，基线 57 行是清之前读数） |
| 截图留证 | 通过（含宿主异常） | 4 帧 `exec-out screencap` 返回全黑 15,580B，同时进程存活/UI 树完整 → 判为宿主取帧异常；force-stop 重启后恢复 |
| 结束时设备状态 | 通过 | 未清数据；配置与 T2 武装留在设备上（**随后被 17:07 重装推翻**，见 §3.4 独立核对） |

### 3.2 设备二（切片补走：录入中断 / 第二次复习 / 导出）

- **2① 录入中断（唯一正面结论）**：`staging` 段被杀（force-stop @16:40:14，5 页里 4 页拷完）→ 该批**静默丢弃**、
  界面无痕、只留孤儿 staging 目录（独立核对补正其可被回收，见 §2 T3）；`PROCESSING` 段被杀
  （force-stop @16:43:27）→ 重启后 Worker `Starting work for …BatchImportDriverWorker`（16:44:06）→
  `Worker result SUCCESS`（16:44:11）→ job `COMPLETED`、5/5 页 `READY`、无重复行、page0 `attempt_count=2`。
- **不重复出网**：窗口内 `model_task` 计数 1→1（只有 16:17:10 旧行），无应用侧模型请求；唯一出站 HTTP 是
  ML Kit 的 `firebaselogging.googleapis.com` 与系统连通性探测（如实记录、未深究）。
- **3 导出成果**：错题本 →「导出成果」可开但只显空态（`mistake_export_hub_empty`）；发起导出的入口在
  错题详情/错题本列表，而 `problem=0` ⇒ **未达**（无对象）；`mistake_export_record=0`。
- **2② 复习中断 / 2③ 讲题中断**：均**未达**——`review_queue_item=0`（会话起不来）、讲题页
  `tutor_composer_block_reason`（能力门 fail-closed + 配额），不存在「会话中/生成中」状态可制造。
- **1 第二次复习 + 时间旅行**：**未达且未做时间旅行**；`adb shell date` 与 host 同刻、未改时钟、无未来日期账本行。
- 收尾：崩溃 0；留给下次的状态（staging 孤儿、`problem_draft=6`、job/pages、`canonical_source_asset=5`、
  那条 `CAPTURE_ASSESS|PERMANENT_FAILURE|PROVIDER_CAPABILITY_MISSING` 行）——**随后被 17:07 重装清空**。

### 3.3 设备三（点名仪器化/JVM；全部为设备三实跑，本记录者只核报告文件）

| 目标 | 命令 | 结果 |
|---|---|---|
| t6/t8 JVM（core:data 四类） | `:core:data:testDebugUnitTest --tests …SmbkArchiveCodecTest --tests …KnowledgeQuizItemStoreTest --tests …KnowledgeQuizFeedbackWriteTest --tests …MasteryUpdateEvidenceIdTest` | BUILD SUCCESSFUL 17s；**29 例 0 失败**（13/5/5/6） |
| t8 JVM（feature:review） | `:feature:review:testDebugUnitTest --tests …KnowledgeReviewSessionViewModelTest` | BUILD SUCCESSFUL 9s；**17/17** |
| t4/t6/t8 仪器化（core:data，真 Room） | `:core:data:connectedDebugAndroidTest -P…class=SplitImportCommitReceiptInstrumentedTest,BackupRestoreInstrumentedTest,KnowledgeQuizEvidenceIdentityInstrumentedTest,KnowledgeQuizItemSnapshotInstrumentedTest` | `Starting 30 tests / Finished 30 tests`、**30/30**（3+22+3+2，0 failure/0 error） |
| t4 仪器化（feature:library 两个新类） | `:feature:library:connectedDebugAndroidTest -P…class=SplitImportReviewCommitFlowInstrumentedTest,SplitReviewEntryInstrumentedTest` | **BUILD FAILED（16s）**：`:feature:library:checkDebugAndroidTestAarMetadata` → `Dependency ':core:data' requires core library desugaring…` + `:core:database` 同款；**7 个用例一个都没跑** |
| t8 仪器化（app，Compose） | `:app:connectedLocalFirstDebugAndroidTest -P…class=KnowledgeReviewSessionInstrumentedTest` | `Starting 2 tests / Finished 2 tests`、**2/2** |
| 保留未删的两个既有幂等用例 | `git diff --quiet` + 轮 1 实跑 | `git diff EMPTY`；报告中 5/0/0 与 6/0/0 |
| T4 设备走查 | 录入 2 页 →「开始分题」 | **未达**：UI「请先在“我的”里配置模型服务，再整理相邻页面。」；`model_task` 零新增、`split_import_job=0`、`split_import_question=0` |
| 崩溃计数与副作用 | `logcat -b crash`、`dumpsys package` | 新增崩溃 0；**:app 仪器化结束后 AGP 卸掉主 APK**（本轮数据丢失），已重装同一 APK + 重播种配置（KB 自动重装 3785 节点） |

### 3.4 独立核对（对三份设备材料逐条复核）

`trustworthy` 分级：**总体可信**。要点：

- **强可核且成立**：设备三全部报告（逐一读 XML 核对计数/用例名/时间戳）；feature:library 构建红
  （**独立核对自己重跑** `:feature:library:checkDebugAndroidTestAarMetadata` → EXIT=1，原文一致）；T4 走查的
  UI 原文与「2 页 READY / 零分题行」（实时读屏 + 截图 + read-only SQL 三方互证）；设备一 APK 哈希/尺寸/dex 四串
  （逐项复算）；模型配置重建（解析 prefs 字节）；崩溃 0（dropbox 旁证）；黑屏帧（读图确认全黑）。
- **不可再核（诚实缺口）**：设备一/二在 17:07 重装前的原始设备记录（`-r` 安装的 dumpsys 数值、staging 孤儿、
  5 页 job、WM 恢复日志、`CAPTURE_ASSESS` 失败行）已被卸载重装抹掉；logcat 现窗口起于 17:16:01。
  「TUTOR_PLAN 失败构成」的原始证据确认**不存在**（三段「未取样」表述如实）。
- **两处更正**：① T4 的 `split_after_status=SETTLED` 归因（见 §2 T4）；② 设备一「已武装 T2 通道」与其设备
  留用状态随重装失效。
- 独立核对自身副作用（如实登记）：只读命令在设备 crash buffer 新增 3 条 toybox `grep` SIGSEGV 转储（镜像
  toybox 正则的既有缺陷，与应用无关）；`/sdcard/ui4.xml`、`window.xml` 被更新；host 临时文件在仓库外。

### 3.5 独立复核（对抗性复核四条问题 + findings）

实跑清单（全部 exit 0；**由独立复核执行，本记录者未重跑**）：`:core:database:testDebugUnitTest
--tests "*AssessmentEvidenceSnapshotIdentityTest*"`（4/0/0）；`:core:data:testDebugUnitTest`
（`TutorLabelBudgetTruncationTest` 3、`TutorMoveLabelBudgetPromptTest` 4、`SmbkArchiveCodecTest` 13、
`DenseTokenizerParityTest` 7）；`:core:model:cleanTest :core:model:test :core:export:testDebugUnitTest`
（34/12/7）；`:core:model:test :core:data:testDebugUnitTest :feature:review:testDebugUnitTest`
（`OpenAiCompatibleModelGatewayTest` 63、`KnowledgeReviewSessionViewModelTest` 17、`KnowledgeQuizItemStoreTest` 5）；
`:app:assembleLocalFirstDebug` BUILD SUCCESSFUL；两个 `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL（**UP-TO-DATE**，
只算「当前工作树可编译」）；python 直调 `generate_status` 三值判定函数。

结论：**① T1 成立**（容忍比较只排除时间戳一列；截断不破坏 subset 校验；网关分流与 catch 顺序正确）；
**② T4 语义方向正确但回执写入有无补偿窗口**（§2 T4 finding 1，medium）；**③ T5 没有把门改绿**
（实跑判定函数：全绿才 PASS、缺证据 NOT_MEASURED、有红 FAIL；`JOB_STATUS` 无缺省；lint 缺报告≠OK；
DenseTokenizer 等价性成立）；**④ T6 不会误拒合法包、T8 保留「同次重试 1 笔」**。

Findings（5 条，供裁定/建议登记，本轮未开新 KD 号）：

| # | 位置 | 问题 | 严重度 |
|---|---|---|---|
| 1 | `RoomCaptureWorkflowRepository.kt:451-483`（tutor 路径 `:649-656`） | SAVED 回执写在提交事务之外，两步之间被杀/被取消 → 题已入库、台账仍 PENDING，且无补偿路径（复核称读码级、未设备复现） | medium |
| 2 | `OpenAiModelResponseParsers.kt:353,358,362,407,632,643` | KD-38 截断按 UTF-16 码元 `take(n)`，可切出孤立代理（同批 `DebugModelFailureCapture` 有 `truncateToUtf8Bytes` 保护）；只确认显示层损坏，未验证下游是否抛 | low |
| 3 | `docs/status.md:7-11`（`generate_status.py:432-470`） | 页面 `BOUND · sha a15aebb7` 指向本批之前的提交（现 HEAD `f094b9ad`）；本地生成必显示 BOUND | low |
| 4 | `ModelEgress.kt:55,64` 与 `algorithm-version-ledger.md:183,236` | 代码侧钉子已同步，但台账未登记 v19/v26（最新仍是批 4 的 lobby v17） | low |
| 5 | `KnowledgeQuizItemStore.kt:66-79` | 损坏行 → 每次「重试出题」都重新生成、必再撞同一身份、再丢弃（白烧模型调用且题面不显示） | low |

独立复核亦声明其未验证：全部设备/仪器化用例、七模块全量单测、两个 androidTest 全量执行、整包 release 编译、
`tools/tests` 全量；KD-38 设备侧触发频率仍未测。

---

## 4. 门禁与跑测口径

- **强门禁 = true（既有红点名）**（开工给定）。既有红不属本批；本批未自行跑门禁（按纪律由脚本/设备轨执行）。
- **实跑过的（逐条注来源）**：设备三的 5 个 Gradle 目标（3 绿 1 红 1 绿，§3.3）；独立复核的 7 项 Gradle/脚本；
  独立核对重跑的 `checkDebugAndroidTestAarMetadata`（EXIT=1）；T5 的 `python -m unittest discover -s tools/tests`
  （737 OK）；T7 的 kotlinc 直编 + JUnitCore（19 OK）；T9 的冻结快照测量（JVM 3/3 + connected 10/10）；
  设备一/二的 adb/sqlite 现场命令。
- **仍未跑**：七模块全量单测、两个 androidTest 全量执行、整包 release 编译、`tools/tests` 之外的 python 套件、
  `:feature:library` 那 7 个用例（构建断口未修）、KD-38 设备前后对照。
- **构建红（本批新引入，唯一一处）**：`feature/library` 的两个新类因缺 core library desugaring 配置不可执行。
  修法只需在 `feature/library/build.gradle.kts` 开 `isCoreLibraryDesugaringEnabled = true` +
  `coreLibraryDesugaring(libs.desugar.jdk.libs)`（同仓 `core/database` 的先例）；本记录者实测该模块当前 0 处相关配置。

---

## 5. 未验证项总表

1. KD-38 的**触发频率**与失败构成分类（标签越界/其他形状/截断/其他）：自登记起至今未取样（原始证据不存在）。
2. KD-37 的**设备级 UI 端到端**（失败 → 重新判定 → 成功）：未达（配额）；DAO 层证据 ≠ UI 通过。
3. 设备一/二在 **17:07 重装前**的全部设备侧原始记录：不可再核（卸载重装 + logcat 轮转抹掉）。
4. T1、T2、T4、T6、T8 各轨自述的**零编译零测试**部分：树内等价性/存在性只能读码核对，不能替代编译。
5. feature:library 两个新类的 7 个用例：**0 执行**（构建不过）。
6. T2 的**真实 adb 广播投递**、**真实设备上 64 KiB 截断与 20 条淘汰**（仅有算法复刻证据）。
7. T5 的 lint 门（`:feature:library:lintDebug` / `:core:data:lintDebug`）未由本批执行；状态页中单测 XML / APK /
   APK sha 仍是「文件存在即取数」（无新鲜度判定）。
8. T6 的恢复端到端「回执题数 == 归档库题数」在设备上未走查（仪器化覆盖）。
9. T8：app 层两条接线 lambda 无单测；破坏性场景（损坏行）是硬出口。
10. T7 的导出缓存命中端到端与设备侧未做。
11. 独立复核的 findings 1–5（§3.5）：均为读码级或静态级，未修复、未设备复现。

---

## 6. 共享树说明（提交者必读）

- **提交状态**：82 个已跟踪文件被改 + 400 个未跟踪条目（含他线文件与历史产物）；**暂存区为空**。
  新文件（`AssessmentEvidenceSnapshotIdentity.kt`、`ModelTextBudget.kt`、`ModelCallFailureDiagnostics.kt`、
  `KnowledgeQuizItemStore.kt`、`SplitImportCommitReceipts.kt`、各新测试类、T5 的 python 测试等）都在 `??` 下，
  **不 `git add` 不会进提交，但门禁仍会从工作树跑到**。
- **他线在飞（不属于本批，勿混提）**：`RoomStudyDatabase.kt` 的 `findAssessmentItemSnapshot` 等段、
  `AttemptTransactionDao` / `ChatEvidenceDao` / `LearningProjectionPort` 的改动面之外，
  `knowledge-production/wusan/materials.jsonl`、`tools/dense_build/*`、`.agent_audit_crops/*`、`.tmp_*` 等产物。
- **区域级撞车提醒**：T1 与 T2 都改了 `OpenAiCompatibleModelGateway.kt`（catch 区 / 上报区）与同一份
  `OpenAiCompatibleModelGatewayTest.kt`（失败用例区）；提交与后续改动请按区域核，复核时 grep
  `OUTPUT_BUDGET_EXCEEDED` 与 `aTutorBodyBeyondItsTextBudgetIsRetryableInsteadOfPermanentlyFailed`。
- **文件索引状态更正**：T7 报告两个导出测试文件为「MM」（索引里有他线版本）；本记录者实测现为 ` M`（未暂存）。
- **设备侧**：当前设备是 17:07 之后的**全新安装**（`firstInstallTime=lastUpdateTime=2026-10-15 17:07:26`），
  上一轮数据与 T2 武装均不存在；模型配置已按既有值重播种、KB 自动重装（3785 节点）。

---

## 7. 待裁定 / 建议登记

1. **独立复核 findings 1–5**（§3.5）：建议登记——finding 1（SAVED 回执窗口，medium）与 finding 5（损坏行重试死路）
   与 KD-37/KD-38 同类（「重试没有出路」）；finding 2 与 KD-38 截断同源。本轮**未开新 KD 号**，交主线决定。
2. **T2 结构取舍**：main 里的默认 null 公共可变缝 vs `docs/architecture-research-2026-09-22.md:222-225` 的反面发现。
3. **T4**：`markConfirmed` 已无生产调用方（保留未删）；回执写入点是否改由 feature 层事件触发（会引入漏写窗口）。
4. **T9 三条建议**（判词粒度 / 冷却分层 / 答案键纠正语义）——T9 明确留产品裁定，本轮不改语义。
5. **KD-38 剩余**：代理对截断是否修；UI 文案是否为新失败码分叉；`TutorDebriefOutput.teachingFocusLabels` 是否补截断。
6. **台账**：`algorithm-version-ledger.md` 是否补 v19/v26 一行（历史行无误，只是缺登记）。

---

## 8. 收尾补记（主会话，2026-10-16 提交后）

- **feature:library 构建红已修**（§4 唯一新引入红）：`feature/library/build.gradle.kts` 开 core library desugaring
  （同 `core/database` 先例），修法随 `6a69c82b` 入库；`checkDebugAndroidTestAarMetadata` +
  `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL（主会话实跑）。
- **7 个用例首跑（提交后补跑）：4 绿 3 红** —— `SplitReviewEntryInstrumentedTest` 4/4 绿；
  `SplitImportReviewCommitFlowInstrumentedTest` 3/3 红，**同一根因、夹具缺陷非产品缺陷**：夹具
  `QuestionBlockEvidence` 用 `LOCAL_OCR` 但未带 `producerVersion` → codec `validateDraft` 判
  `MISSING_PRODUCER_VERSION` → 指纹计算抛 `Captured question document is invalid`（`CapturedQuestionDocument.kt:205`）。
  这也解释了 §4「0 用例执行」之外它从未暴露过——该用例此前从未真正跑起来过。
- **修夹具（补 `producerVersion = "split-review-fixture-v1"`）后复跑：7/7 全绿**（设备 emulator-5554，
  `:feature:library:connectedDebugAndroidTest`，1m33s）。至此 §5 未验证项 5 关闭。
- **本批提交（未推送）**：`038d50c0`（T1/T2）→ `6a69c82b`（T4/T5）→ `09f0c85c`（T6/T8+T5 随附）→
  `a49b4c4f`（记录与登记）→ `53770c18`（T7 测试）→ `173ae620`（主线+台账同步）→ `f96464e9`（KD 提交号回填）
  → 本补记。共享树他线文件（协议/工具环/KB）未由本线提交。
