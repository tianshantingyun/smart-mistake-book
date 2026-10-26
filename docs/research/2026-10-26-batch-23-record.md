# 批次 23 记录（2026-10-26）：设备功能门收口 + 锁文件落地尝试 + KD-67 落库 + PDF 隐式标识研究

> 记录时点：2026-10-26（+08:00）。本文件由记录员在共享工作树 `D:\smart mistake book` 只读核对计划、基线、四研究文档、当前源码/测试、`git status`、`build/` 产物与设备证据目录后写入。**记录员未运行 Gradle、未运行 adb/am instrument、未执行任何 git 写操作、未提交/推送、未改生产代码。** 本文件是记录员本轮唯一的新增交付（另有五处文档同步/修正：`docs/REFACTOR-MASTER-LINE.md`、`docs/known-defects.md`、`docs/research/algorithm-version-ledger.md`、`docs/agent-first-refactor-decisions-2026-09-23.md`、R1 交叉引用，见 §7）。
> 计划：`.zcode/plans/plan-2026-10-26-batch-23-device-gates-lockfiles.md`（下称"计划"）。
> 基线：`docs/research/2026-10-26-batch-23-baseline.md`（下称"基线"；基线本身是未跟踪新文件）。
> **门禁状态（工单给定，非记录员实跑）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；`--write-locks` `exit=0`（stdout 尾部 `BUILD SUCCESSFUL in 2m 55s` / `228 actionable tasks: 22 executed, 206 up-to-date` / `Configuration cache entry stored.`）。仓内没有本批门禁日志，§4 只作转录。
> **本记录不声称"批次全绿"**：T1 设备功能门仍 1/5 红（client `abandon()` 死亡确认结构性失败）；KD-70 P2 仍红；锁文件轨只有 13/14 个未跟踪锁、`benchmark` 缺锁且与 HEAD 不一致，不能原样提交（KD-72 open）；T4 只做研究/登记、未改导出链（KD-75 open）；KD-74 证据已落盘但未关闭；真实 Provider 无凭据、模型质量 UNVERIFIED；JS 仍 UNAVAILABLE、不广告。

## 1. 范围与基线

### 1.1 计划范围（计划 §1）

- **做**：R1 PDF 隐式标识嵌入选型（pdfbox-android / 手写 PDF 对象注入 / 只保留显式图注三选一）；R2 issue #240 上游状态与 quickjs-ng 差异；R3 Gradle 锁文件在多模块 + 版本目录下的生成/校验/CI 形态；R4 设备证据落盘规范（KD-74）；T1 生成锁文件并落 CI（KD-72 关闭判据）；T2 KD-67 生产落库半边；T3 KD-68 数据侧；T4 PDF 隐式标识按 R1 落地或如实登记；T5 契约 §5 目标架构偏差收敛与证据落盘格式；设备轨（T1 隔离进程 dup 修复复跑 + KD-66 watchdog 真挂死 + KD-70 P2 + KD-69 R8 internal 含 Worker/OCR + 16KB 抽验）；双复核/修复/记录/提交/推送。
- **不做**（计划 §1/§2）：4E.2 交互图/保真升级与 JS 广告（T1 设备门全绿前不推进）；G1/G2、5b/5c、阶段 6、6B-3、AUD-08（热区）；真实 Provider；Wave X。

### 1.2 沿用裁定（本批执行，不得改）

1. **JS 方法不广告**：工具面、提示词、能力快照零改动；JS 仍 `UNAVAILABLE`。
2. **4E.2 交互与保真不做**：本批只做 L2 进程层设备验证与缺陷登记。
3. **AIDL 一律以 Gradle 任务验证**：禁止 `aidl.exe` 双输入形式（`INPUT [OUTPUT]` 会覆盖第二份输入）。
4. **AAR 钉版不动**：`quickjs-kt-android:1.0.15`；`kotlin-stdlib` 维持 2.3.21 约束（KD-73 未证前不升级）。
5. **真实 Provider 无凭据**：一切模型质量 UNVERIFIED。
6. **设备证据按 R4 规范落盘**：仓内 `.md` 为唯一关闭证据；`build/` 只留原始全量。

### 1.3 基线摘要与记录时点复核

- **HEAD**：`f0a145f8`（= 基线实测的 `origin/main`；**本轮未 `git fetch`**，远端实时 hash 未核）。HEAD 是批次 22 的纯文档尾提交，父提交 `69e5f591`。
- **工作树**：基线 **193 M + 449 ??**；记录时点 **220 M + 478 ?? = 698 项**（`git status --porcelain | wc -l`）。增量为本批落点 + 他线继续在飞，不能按模块 `git add`。
- **schema**：`STUDY_DATABASE_VERSION = 69`（`core/database/.../StudyDatabase.kt:134`），导出 schema 最高 `69.json`、无 `70.json`；请求 schema `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`（`core/model/.../ModelTasks.kt:546`）。记录时点 `git status --porcelain -- core/database/schemas .../StudyDatabase.kt .../ModelTasks.kt` **无输出**；本批零 DB/请求 schema、零版本串 bump（§2.5、算法台账 §3.33）。
- **热区记录时点计数**：`feature/library` **40**、`feature/tutor` **58**、`feature/capture` **16**、`feature/review` **12**、`core/ui` **35**（`git status --porcelain -- <dir> | wc -l`）；`SecondaryScreens.kt`、`SmartMistakeBookRoot.kt` 仍 M。
- **落点干净度**：基线逐项实测的 9 个落点干净（`build.gradle.kts`、`gradle/libs.versions.toml`、`app/build.gradle.kts`、core/database SeedAssessmentPort 面、core/domain 三文件、core/data 三文件、`docs/known-defects.md`、`docs/current/visual-generation-contract.md`）。**这是文件级干净，不是模块级**：`core/database` 8 M、`core/domain` 4 M、`core/data` 10 M、`docs` 大量 M/?? 仍在。
- **计划落点中本批零 diff**：`app/build.gradle.kts`、`gradle/libs.versions.toml` 记录时点仍干净（AAR 钉版与 stdlib 约束未动）。
- **基线与实际不符处（沿用基线 §8）**：① `SeedAssessmentPort` 没有单列 CAS 口，T2 必须新增 port 方法 + DAO + adapter（`verification_status` 列 v69 已存在，无需迁移）；② KD-72/73 的行号引用相对当前树漂移 4 行；③ `:core:markdown` 未跟踪且已被 `settings.gradle.kts` include，锁生成会与它正面相遇；④ `ps16k` AVD 在盘但基线时在线三台均为 4KB；⑤ origin/main 未 fetch。

## 2. 四研究线与复核结论

### 2.1 R1 PDF 隐式标识（`docs/research/2026-10-26-pdf-implicit-labeling.md`，571 行）

- **官方方法存在且明确**：TC260-PG-20258A §5.1 c) 把 `pdf` 列为文本文件格式，§6.3 规定存储位置为 **Document Information Dictionary**，写入方式 `/AIGC({7 字段 JSON})`。官方 PDF 本日从 tc260.org.cn 下载取证：20 页、3,272,625 B、sha256 `6bf6c118…3b0e`（与第三方引用前缀一致）；20259A 图片指南 11 页、sha256 `e4f7fe82…a02f`，其格式清单**不含 PDF**。
- **结论不是"法规未要求"**：按 R1 字面判断，AI 产物是图片；但 §10.3 已证明模型解析文本可进入导出，因此"PDF 不含 AI 文本"不是已证前提。该组合落在 20258A（文本）与 20259A（图片文件）之间的**空档**，不是豁免；显式图注满足《办法》§4(2)，不能替代 §5 的文件元数据义务。
- **选型**：**不引 pdfbox-android**（Apache-2.0，但最后发布 2.0.27.0/2023-01-02、最后提交 2023-12-29、125 open issues；AAR 3,254,019 B + POM 传递 BouncyCastle 1.72 三件共 11,042,824 B）；**零依赖手写 append-only 增量更新**，把 `/AIGC` 写进 Info 字典；**不写 XMP**（20258A 的 PDF 载体就是 Info 字典；XMP 是 20259A 的图片载体）。
- **写入判据**：只在 PDF 实际含 AI 生成内容时写；注入后以 `PdfRenderer` 解析页数 + pypdf/独立读者读回双重断言；失败 fail-closed；旧缓存需升 `mistake-export-v3 → v4`。
- **PoC（研究线实跑）**：在 TC260 官方 PDF 上追加 552 B 增量段，`pypdf` 读回完整 `/AIGC`、`PyMuPDF` 仍打开 20 页、原 `/Author`/`/CreationDate`/`/ModDate`/`/Producer`/`/Title` 全保留。**未验证**：Android PDFium 对增量段的接受度（U3）、Skia 实际产物形态、GB 45438 附录 E（U1）、20258A 对"仅 AI 图 PDF"的适用范围（U2）。
- **复核修正（R1 §10.3，记录员已加交叉引用）**：R1 原文 §0/§1.4 断言"当前 PDF 无 AI 生成文本块"，**不被代码支持**——`CaptureParseInput → OpenAiModelResponseParsers` 会给 `paragraph`/`figure_request`/`diagram_note` 写 `provenance = MODEL_DOCUMENT_PARSE`（`OpenAiModelResponseParsers.kt:190/:218/:235/:261`），提交校验不要求 provenance 改变，导出层 `MistakePdfExportInput` 没有 provenance 字段（`core/export/src/main` 对 `blockEvidence`/`QuestionBlockProvenance` 零命中）。因此写入判据必须同时覆盖 AI 图与模型解析文本，不能只按"仅 AI 图"。记录员在 R1 §0 第 2 条与 §1.4 表格加了指回 §10.3 的交叉引用，不改原结论。

### 2.2 R2 QuickJS issue #240 上游状态（`docs/research/2026-10-26-quickjs-issue240-status.md`，335 行）

- **#240 未修复且上游无回应**：issue `open`、`comments=0`、`updated_at == created_at`（2026-09-26）；唯一事件是 2026-10-25 `pa2x2` 的 `referenced`；上游 `main` 停在 `a081755b`（2026-09-03），最新 release 与 Maven 最高版本都是 `1.0.15`；维护者 2026-01-21 在 #137 明说"I stopped Kotlin development over a year ago"。
- **修复只在 fork**：`pa2x2/quickjs-kt` 的 `fbe42274` + 8 个配套提交，领先上游 9 个提交，以 GitHub release `quickjs-kt-1.0.15-fork.1`（group `io.github.pa2x2`，非 Maven Central）发布；无 PR、0★、创建 1 天。AAR sha256 本地复算与发布 `SHA256SUMS` 一致（`57d7239f…8212`），ELF 形状与上游同（16KB/RELRO 不构成新阻断），但**不是可采纳依赖**。
- **不换 quickjs-ng**：ng v0.17.0 的 `promise_reaction_job` 对 uncatchable 异常直接 `return JS_EXCEPTION`，理论上可让排水循环在 `JS_ExecutePendingJob` 返回 -1 处停下；但 ① 没有维护中的 Kotlin/Android 绑定（官方 projects 清单无 Kotlin，`gh search repos` 0 命中）；② ng 的 `JS_ExecutePendingJob` 同样无上界，官方明说 job 循环由嵌入方负责；③ #1753（Array builtins 不 poll 中断）仍 open；④ master 已改 `JSInterruptHandler` 签名。**换 ng 不会减少对 L2 的依赖。**
- **判据**：维持 `quickjs-kt-android:1.0.15` + L2 `:visual_js` 进程级终止，直到 §4.3 的触发条件成立；升级上游修复版/换 fork/换 ng 均有明确前置条件，不因"#240 又出现"换栈。
- **未验证**：本线未跑 Gradle/设备；fork/ng 的实际行为、同实例复用、设备 16KB 装载均 UNVERIFIED。

### 2.3 R3 Gradle 锁文件机制（`docs/research/2026-10-26-lockfile-generation.md`，713 行）

- **生成**：每个 subproject 各跑一次 `:mod:dependencies --write-locks`（14 个任务；建议根聚合任务一次触发，避免 root-only 与手写列表漂移）；`--write-locks` 只写本次构建真正解析过的 lock id，构建失败不落盘；`--update-locks` 是定向更新器，**不能当漂移检测**。
- **LockMode**：保持默认 `DEFAULT`（有锁状态严格校验，缺锁状态放行）；`STRICT` 先做探针，不首批启用；`LENIENT` 不能用于供应链门。根注释原写"LENIENT"是事实错误，已在本批修正。
- **配置缓存/并行**：`--write-locks` 会让 CC 永不复用并强制 store；生成建议 `--no-configuration-cache --no-parallel`（后者避开 KGP 并行 race，#31875/KT-74241）。
- **CI**：`--write-locks` 后 `git status --porcelain -- '*.lockfile'` 判红（未跟踪文件必须包含），另加"每个 included project 都有锁"的计数检查；硬编码 `expected=13` 已被证伪（同时"多"含未跟踪 `:core:markdown`、"少"掉 `:benchmark`），改用 `tools/ci/check_lockfiles.py` 按 `settings.gradle.kts` 的 include 列表核对。
- **实际生成（R3 §10 回填，非本线运行）**：工作树有 **13 个模块锁 + `settings-gradle.lockfile`**；`:benchmark` **无锁**；全部未跟踪（`git ls-files '*gradle.lockfile'` = 0）；锁生成于他线未提交的 core/ui/core/export/feature/tutor/settings 构建改动之上，且只覆盖 23 个不同配置名（库模块基本只有 `debugCompileClasspath` + 工具链；app 只有 `localFirstDebugCompileClasspath`/`localFirstDebugRuntimeClasspath`/`localFirstDebugUnitTestCompileClasspath`），**没有 `unitTestRuntime`/androidTest 配置**。这批锁**不能原样提交**：core/ui/app 锁缺 HEAD 仍用的 `richtext-commonmark`，core/export 锁多出 HEAD 不解析的 `kotlinx-serialization-*`，DEFAULT 下会在解析期判红。**KD-72 仍 open。**

### 2.4 R4 设备证据落盘规范（`docs/research/2026-10-26-device-evidence-convention.md`，821 行）

- **落点**：`docs/research/evidence/<YYYY-MM-DD>-batch-<NN>/`，扩展名只用 `.md`（`.gitignore:15` 忽略 `**/build/`、`:31` 忽略 `*.log`、`:23` 忽略 `*.apk`）；`build/device-track-batchNN/` 只放原始全量，不是证据。
- **上限**：单文件 ≤ 64 KiB 且 ≤ 400 行；单批 ≤ 512 KiB 且 ≤ 16 个 `.md`；全证据目录 ≤ 8 MiB。模板为 `00-index.md` + `step00-environment.md` + `stepNN-*.md`（命令/退出码/计数/有界摘录/判定/脱敏/raw-log）。
- **引用**：`E-ID` + 反引号路径:行；记录用 `evidence/<dir>/file.md:line`，KD 用 `research/evidence/...`；没有 E-file 的数字只能写"工单转录"或 `UNVERIFIED`。
- **KD-74 关闭**：C1 四类结论各有 E-file；C2 T1 修复后复跑有 E-file；C3 每个声称的设备运行有 APK 身份（sha256/字节/versionName）；C4 record 每个设备数字有 E-ID + path:line；C5 脱敏/可跟踪；C6 record 写 "KD-74 closed" 并链接索引。**红绿中性**：KD-74 只追究证据，不要求设备门绿。
- **实际落盘（记录时点实测）**：15 个 `.md`、1,745 行、内容 **95,895 B**、`du -sk` = 124 KiB；`00-index.md` 自报 **95,850 B**（与实际差 45 B，见 §5 #9/#23）。C1/C2/C3/C5 已具备；C4 由本记录补齐；C6 因本条未关闭而不满足。

### 2.5 研究复核结论

- 四份研究文档均在盘、有来源分级与未验证清单，本批实施与它们逐项对齐；未发现需要推翻研究结论的新事实。
- 发现两类漂移：① **R1 的"当前 PDF 无 AI 文本"前提**被 T4 复核证伪（§10.3），本记录已要求按"AI 图或 `MODEL_DOCUMENT_PARSE` 文本"重写判据；② **R3 的"仓库无锁 / 15 个配置"等生成前表述**与批次 23 生成后的现状不一致，R3 §10 已回填、KD-72 文本已改，但本批锁仍不能提交。
- 零版本串：DB schema 69、请求 schema 18、`LearningCoreVersions.*`、`ReviewPlannerV2` 全部未动（算法台账 §3.33）。

## 3. 五实施轨 + 集成 + 设备 + 证据轨

> 判定口径：**代码级完成** = 改动在当前树且记录员静态核对属实；执行证据分列（设备实跑 / JVM 工件 / 只读日志 / 无证据=UNVERIFIED）。文件清单按工单 JSON 与 `git status` 交叉核对（§7）。
>
> 状态总表（工单状态原样保留，末列为记录员判定）：
>
> | 轨 | 工单状态 | 本记录判定 | 差异理由 |
> |---|---|---|---|
> | T2 KD67 落库 | 完成 | **本轨完成 / KD-67 仍 open** | 生产落库半边与设备 5/5 达成；feature 消费与生产调用点仍无 |
> | T3 KD68 数据侧 | 完成 | **数据侧完成 / KD-68 仍 open** | 只读派生与快照读面落地；UI 在 feature/review 热区 |
> | 锁文件轨 | 部分 | **部分（13/14 未入库）** | `benchmark` 缺锁、与 HEAD 不一致、负控未做，KD-72 不能关闭 |
> | T4 PDF 标识落地 | 部分 | **研究/登记完成 / 零代码** | 按纪律不动 core/export；KD-75 登记，导出物仍无 `/AIGC` |
> | T5 契约一致性 | 完成 | **完成（文档）** | §5.1 对照表 + §4 收敛；无独立 E-file |

### 3.1 T2 KD-67 生产落库半边 —— **本轨完成 / KD-67 仍 open**

- 文件（9）：`core/database/.../port/LearningProjectionPort.kt`（M）、`core/database/.../dao/LearningDao.kt`（M）、`core/database/.../DatabaseContract.kt`（M）、`core/database/.../RoomStudyDatabase.kt`（M）、`core/database/src/androidTest/.../AssessmentItemVerificationStatusInstrumentedTest.kt`（新）、`core/data/.../study/KnowledgeQuizItemVerificationUpgrader.kt`（M）、`core/data/src/test/.../KnowledgeQuizItemVerificationUpgraderTest.kt`（M）、`core/data/src/test/.../RoomBackedStudyExperienceRepositoryTest.kt`（M）、`docs/known-defects.md`（M，KD-67）。
- **存储口**：`SeedAssessmentPort.compareAndSetAssessmentItemVerificationStatus`（`LearningProjectionPort.kt:50-72`，方法 `:68`）返回**真实受影响行数**；DAO 参数化 `UPDATE assessment_item_snapshot SET verification_status = :new WHERE assessment_item_snapshot_id = :id AND verification_status = :expected`（`LearningDao.kt:183-204`，方法 `:201`）；Room adapter（`RoomStudyDatabase.kt:1578-1599`，方法 `:1586`）在写库前经 `DatabaseContractValidator.validateAssessmentItemVerificationStatusCas`（`DatabaseContract.kt:612-633`，函数 `:620`）收紧为仅 `UNKNOWN → VERIFIED/USER_ASSERTED`，已核验状态互换/降级抛 `DatabaseContractViolationException`；无行不插入。
- **core/data 生产接线**：`StudyDatabaseAssessmentItemVerificationStatusWriter`（`KnowledgeQuizItemVerificationUpgrader.kt:47-67`，类 `:55`）把 0/1 行数折成 Boolean；生产构造器 `(database, learnerId, sourceVerifier)`（`:141-154`，writer 在 `:152`）自动走单列 CAS；`upgrade()` 顺序未变（读行 → 题面可读 → 领域判据 → `sourceVerifier.verify`（`:213`，幂等出口 `:264`）→ CAS）。`verification_status` 列 v69 已存在，**零 schema 变更/迁移**。
- **JVM（记录员只读日志，非本轮运行）**：`tmp/verify-batch23-t2/green.log` → `KnowledgeQuizItemVerificationUpgraderTest` **OK (11 tests)**、`AdaptiveSelectionAssemblerTest` **OK (12 tests)**；红证据 `red.log` 首行 `unresolved reference 'StudyDatabaseAssessmentItemVerificationStatusWriter'`、`RED_OK: compile failed as expected (exit=1)`。
- **设备（E-07）**：`AssessmentItemVerificationStatusInstrumentedTest` 5/5 PASS（`evidence/2026-10-26-batch-23/step07-kd67-cas.md:82`），5 个用例逐个 `STATUS_CODE 0`；`OK (5 tests)` 见同文件 `:68`（记录员本轮实读确认该行为 `OK (5 tests)`）。**工单 T2 summary 仍写"仪器化用例已写待设备轨执行"，与 E-07 冲突；本记录以 E-07 为准**（复核 #1/#21 已指出该摘要过时）。
- **未达/残余**：① feature 侧 `AdaptiveClarificationConsumer` 与 UI；② 确认流生产调用点；③ 设备/集成用例"生产候选能进 ASK、未核验候选仍 CLARIFY"；④ `AnswerKeyVerificationSourceVerifier` 生产实现；⑤ `ConcurrentVerificationChange`（输家重读发现被改成别的状态）在 JVM 测试中零覆盖；⑥ `overwriteAssessmentItem` 仍可整行改写含 `verification_status`（KD-41 修复口，存储层无守卫）。**KD-67 保持 open**；"本轨完成"只指生产落库半边。

### 3.2 T3 KD-68 数据侧 —— **数据侧完成 / KD-68 仍 open**

- 文件（10）：`core/domain/.../NewIntroductionPolicy.kt`（M）、`core/domain/.../StudyExperienceRepository.kt`（M）、`core/domain/src/test/.../NewIntroductionPolicyTest.kt`（M）、`core/domain/src/test/.../StudyReviewOverviewIntakeTest.kt`（新）、`core/data/.../study/StudyExperienceMappers.kt`（M）、`core/data/.../study/StudySnapshotBuilder.kt`（M）、`core/data/.../study/StudyReviewPlannerService.kt`（M）、`core/data/src/test/.../StudyExperienceMappersIntakeTest.kt`（新）、`core/data/src/test/.../StudySnapshotIntakeUnschedulableTest.kt`（新）、`docs/specs/batch-intake-spec.md`（M）。
- **决策事实**：`IntakeDecision.unschedulableCount`（`NewIntroductionPolicy.kt:90-96`，实现 `:205`）只读派生，不参与发放算术；`introduced`/`deferred`/`grantedSliceSeconds` 逐位不变；因当日切片用完而 deferred 的题**不是** unschedulable。
- **如实预览 API**：`previewSchedulability` + `SchedulabilityPreview`（KD-68 Progress 记 `:359-424`）；`previewBacklog` 委托同一逐日模拟，旧口径合并计数逐位不变；`preview` 保留兼容、未加 `@Deprecated`（避免热区编译警告）。
- **快照读面**：`StudyReviewOverview.intakeUnschedulableCount`（`StudyExperienceRepository.kt:98-109`）；mappers 透传；`StudySnapshotBuilder` 经 `StudyReviewPlannerService.intakeUnschedulableCount`（`:299`；`readExams` `:308`、`difficultyTierByUnit` `:314`）派生，与 `createReviewPlan` 同一时长/考试预算口径（`expectedNewSeconds` `:264`）。
- **JVM（记录员只读日志，非本轮运行）**：`tmp/verify-batch23-t3/run-with-snapshot-test.log` → `NewIntroductionPolicyTest` **OK (19 tests)**、`StudyReviewOverviewIntakeTest` **OK (3 tests)**、`StudyExperienceMappersIntakeTest` **OK (2 tests)**、`StudySnapshotIntakeUnschedulableTest` **OK (1 test)**；旧金样 `NewIntroductionPolicyTest` 13、`PretestRoutingTest` 6、`PretestScoringModeAssemblyTest` 4、`IntakePreviewAssemblyTest` 4 全绿；红证据首行 `no parameter with name 'intakeUnschedulableCount'`（HEAD 源）。
- **未达/残余**：① `feature/review` 仍是他线热区——`ReviewRoute.kt:529` 仍用 `preview`、`:558` 仍固定 900，生产 main/feature/app 零消费者；② `ReviewIntakeBacklogPreviewInstrumentedTest` 未覆盖"全超预算项显示为不可调度/专项会话"；③ `IntakeDecision.unschedulableCount` KDoc 写"count over deferred"而实现计 `ordered`（今日可证等价，但文字与实现不一致）；④ `beyondHorizonCount` 只有 0 值断言；⑤ 每次快照多一次 `readExams()` + 全积压难度档批量读（与同一轮 planner 重复，未做基准）。**KD-68 保持 open**。

### 3.3 锁文件轨 —— **部分（13/14，未入库）**

- 文件：`build.gradle.kts`（M）、`.github/workflows/android-check.yml`（M）、`tools/ci/check_lockfiles.py`（新）、`tools/tests/test_check_lockfiles.py`（新）、`docs/research/2026-10-26-lockfile-generation.md`（新）。**13 个 `*/gradle.lockfile` 与 `settings-gradle.lockfile` 均为未跟踪，不得原样提交。** 计划落点里的根 `gradle.lockfile` 不存在（锁按模块落）。
- **生成结果**：13/14 模块有锁（app、core/model、core/domain、core/database、core/data、core/export、core/ui、core/markdown、feature×5）；**`:benchmark` 无锁**。`git ls-files '*gradle.lockfile'` = 0。**工单锁文件轨 summary 的"锁覆盖仅 15 个配置 / CI 无 write-locks 比对"是修复前状态**；当前树逐行解析为 23 个配置名，CI 已有两个锁 step（见下，未实跑）。
- **内容核验（记录员本轮实测）**：13 个锁均含 `org.jetbrains.kotlin:kotlin-stdlib:2.3.21`；`quickjs-kt-android:1.0.15` 在 `app/gradle.lockfile:217`（`localFirstDebugRuntimeClasspath`）与 `core/data/gradle.lockfile:97`（`debugCompileClasspath`）；`app/gradle.lockfile` 对 `strictOffline` **0 命中**、对 `localFirstDebugRuntimeClasspath` **232** 命中；逐行解析 13 个锁得到 **23 个不同配置名**（含 `androidApis`、`compileClasspath`、`debugCompileClasspath`、`localFirstDebugRuntimeClasspath`、KSP/Kotlin 工具链等），**缺 `unitTestRuntime`/androidTest 配置**（app 仅有一对 `localFirstDebugRuntimeClasspath` 与 `UnitTestCompileClasspath`，库模块只有 `debugCompileClasspath`）。
- **结构性问题（高）**：锁是在他线未提交的 core/ui/core/export/feature/tutor/settings 构建改动之上生成的，与 HEAD 不一致——`git show HEAD:core/ui/build.gradle.kts` 仍有 `richtext.commonmark`，而 core/ui/app 锁没有 `com.halilibo.compose-richtext:richtext-commonmark`；HEAD `core/export/build.gradle.kts` 无 serialization，而 core/export 锁含 `kotlinx-serialization-*`。DEFAULT 对"锁条目未被解析匹配"与"解析结果含锁外依赖"都判红（Gradle 9.6.1 官方文档）。**本批未运行 Gradle/CI，解析期失败行为是文档依据，未实测。**
- **门禁面已落（未实跑）**：根 `build.gradle.kts` 新增 `lockAllDependencies`（每子项目 `dependencies`）与 `resolveLockableClasspaths`（所有 `*CompileClasspath`/`*RuntimeClasspath`，含 unitTest/androidTest/runtime）；`tools/ci/check_lockfiles.py` 按 include 列表核对；CI 新增 `Dependency lockfile manifest and coverage gate` 与 `Dependency lockfiles are up to date` 两个 step（后者仅在已有跟踪锁时跑 `lockAllDependencies --write-locks` 并比较 `git status --porcelain -- '*.lockfile'`）。
- **记录员实跑（非 Gradle）**：`python tools/ci/check_lockfiles.py` → **exit 0**，输出 `OK: dependency locking 尚未落地：没有任何已跟踪的 gradle.lockfile，KD-72 保持 open，本门跳过（CI 的 --write-locks step 同样跳过）；工作区存在 13 个未跟踪锁文件（例如 app/gradle.lockfile），不能原样提交；缺锁 project：:benchmark（DEFAULT 模式下不会报错）`；`PYTHONPATH=tools python -m unittest discover -s tools/tests -p test_check_lockfiles.py -v` → **Ran 9 tests ... OK**。
- **未达**：锁重生成（干净树）、`:benchmark` 锁、`:core:markdown` 入库裁定、负控（抬版/加依赖解析期红）、CI 实跑、锁覆盖 test/androidTest/runtime 的实证。**KD-72 保持 open；T1 不得写成"已完成"。**

### 3.4 T4 PDF 标识落地 —— **研究/登记完成 / 零代码**

- 文件（3）：`docs/known-defects.md`（M，KD-75）、`docs/ai-content-disclosure-copy.md`（M）、`docs/research/2026-10-26-pdf-implicit-labeling.md`（新，含 §10 落地裁定/复核补记）。
- **本批处置**：按纪律 `core/export` 是热区（5 M），**不新增 `PdfAigcInfoInjector`、不改 `MistakePdfExporter.prepare`、不升 `mistake-export-v3`、不写读回测试**；`grep -rn AIGC core/export/src/main` = **0**，`MistakePdfEligibility.kt:186` 仍是 `mistake-export-v3`，导出物字节/签名未被本批触碰（**记录员未运行 PDF 测试**）。
- **双层缺口已登记 KD-75**：① PDF 无文档级隐式标识（官方载体 Info 字典 `/AIGC`，当前只有逐图显式图注）；② "仅 AI 图"写入判据会漏掉 `MODEL_DOCUMENT_PARSE` 文本（§2.1 复核修正）。
- **文案**：`docs/ai-content-disclosure-copy.md` 已改为"当前版本的 PDF 导出尚未写入文件级隐式信息（该缺口已登记，不再承诺 PDF 携带隐式标识）"，并明确不得表述为"法规未要求"。
- **未达**：任何代码/测试/设备验证；Android PDFium 对增量段的接受度（U3）；缓存指纹升级；打印子集；DOCX `docProps/custom.xml` 对称缺口。**KD-75 保持 open。**

### 3.5 T5 契约一致性 —— **完成（文档）**

- 文件（2）：`docs/current/visual-generation-contract.md`（M，218 行）、`docs/testing/device-state-safety.md`（M）。
- **§5.1 新增"目标架构 vs 现状"对照**（`visual-generation-contract.md:106`）：6 行表格逐项对照契约 §5 处方与现状——不存在 `AndroidVisualExecutionEngine`；不存在 `core:visual-js`，Service/AIDL/client 全在 app；直接引擎 `QuickJsVisualExecutionEngine` 落 `core:data`（不引用 AIDL/原生排版）；不存在 `core:visual`/`AndroidScenePainter`；core:ui/export 不依赖 JS worker；JS 未广告、无生产装配。**不新裁架构**，归位条件沿用批次 22。
- **§4 终止层收敛**（`visual-generation-contract.md:72`）：L2 设备复跑 5 例 4 绿 1 红、abandon 结构性红、冷启动加载态对照；AIDL 门改为"一律以 Gradle 任务验证"；调用方上界仍如实写最坏约 8.5 s；KD-66 关闭门值未动。
- **R4 引用**：`docs/testing/device-state-safety.md:22` 已链到 R4 规范 v1。
- **说明**：T5 summary 的"核对清单与发现见 evidence"在仓内**没有对应 E-file**（`grep -rln "目标架构 vs 现状\|视觉契约" docs/research/evidence/2026-10-26-batch-23/` 无命中）；核对内容实际落在契约 §5.1 表格与本记录 §5。记录员按"无 E-file 不称证据"处理，不把 T5 写成设备/门禁证据。

### 3.6 集成

- **无生产接线**：T2 的 writer 只在 core/data 的生产构造器里接线，feature 零消费者；T3 的读面零生产消费者；T4 零代码；T5 只改文档。JS 仍 `UNAVAILABLE`、不广告。
- **模块边界**：本批未新增 Gradle 模块、未改 `settings.gradle.kts`（该文件仍是他线 M，含 `:core:markdown` include）；Service/AIDL/client 仍在 app；`core/data`/`core/domain` 不引用 AIDL。
- **构建/供应链**：`build.gradle.kts` 新增聚合锁任务；`check_dependency_pins.py` 未改、本批判绿；`check_lockfiles.py` 新增；CI 两个锁 step 新增但未跑。
- **契约**：§5.1 对照表、§4 设备证据、KD-66/67/68/72/74/75 全部按本批证据回写；KD-66/67/68/72/73/74/75 全部仍 open。

### 3.7 设备轨（记录员未复跑；以下均来自 E-file，记录员实读判定行）

> 环境：`emulator-5554`（API 34，x86_64，4KB，`test_device`）与 `emulator-5556`（API 36，x86_64，**16384**，`ps16k_x86_64`）。协议 = `adb install -r -t` + `am instrument -w -r -e class ...`，未跑 `connectedAndroidTest`。产物身份见 `evidence/2026-10-26-batch-23/00-index.md` §2；**证据落盘轨记录称** 6 个 sha256/字节数逐项重算一致（记录员本轮未复算，只读索引）。

| 项 | E-ID | 判定 | 关键事实（记录员实读） |
|---|---|---|---|
| T1 隔离首轮 | E-01 | FAIL（2/5） | dup 修复本身成立（AIDL 往返 `RESULT_OK`、watchdog 真杀），两红为首次 attach 慢（`step01-visualjs-isolation.md:115`） |
| T1 复跑 | E-01b | FAIL（1/5） | 隔离 UID/AIDL 往返/过期 generation/watchdog 硬杀/死亡后重建绿；abandon 红（`step01b-visualjs-isolation-rerun.md:94`） |
| T1 abandon 单方法 | E-01c | FAIL（1/1） | `the first abandon did not confirm death` 稳定复现（`step01c-abandon-confirmed.md:107`） |
| T1 冷启动立即跑 | E-01d | FAIL（5/5，环境未稳定） | attach 4.6–7.5 s > 5 s 客户端预算；不作产品判定（`step01d-visualjs-isolation-coldboot.md:81`） |
| T1 沉降后复跑 | E-01e | FAIL（1/5） | 加载态超时与结构性失败已分离（`step01e-visualjs-isolation-postboot-settled.md:85`） |
| QuickJS 全量 | E-02 | FAIL（19/20） | P1 `MEMORY_LIMIT` 成立；P2 `raw=null, mallocSize=177216, mallocLimit=16777216` → `INVALID_PROGRAM`（`step02-quickjs-oom.md:112`） |
| 16KB 运行时 | E-03 | PASS（运行时） | `PAGE_SIZE=16384`、3/3、quickjs/LiteRt 装载、zipalign exit 0（`step03-page-size-16k.md:113`） |
| R8 internal | E-04 | PASS | 两次冷启动 ok/COLD、零崩溃签名、两个 Worker SUCCESS、一次中文 OCR 落库（`step04-r8-smoke.md:142`） |
| 备份 | E-05 | PASS（34/34） | 无跳过、无崩溃签名（`step05-backup-restore.md:70`） |
| 迁移 | E-06 | PASS（15/15） | 演练 5/6（1 设计跳过）+ kill-stage 设计内崩溃（`step06-migration.md:153`） |
| KD-67 CAS | E-07 | PASS（5/5） | 单列不变/过期 expected 0 行/无行不补/非法状态对拒绝/并发恰一赢家（`step07-kd67-cas.md:82`） |
| 签名全量 | E-08 | PASS（17/17） | 12×17=204 格；2 条命中为测试前 GMS/Primes，被测应用归因 0（`step08-signature-rescan.md:130`） |
| 16KB ELF 复核 | E-09 | PASS（14/14×2） | 官方 LLVM 22.1.8 `llvm-readobj`；`llvm-readelf` 别名缺失（`step09-elf-llvm-readobj.md:119`，缺失说明 `:107`） |

- **T1 结论**：dup 修复成立；`abandon()` 在 `unlinkDeath` 之后才等 `link.death`（`VisualJsClientEngine.kt:570-571`），服务已 SIGKILL 而客户端 1 s 内收不到 death → `unavailable=true`。E-01b/E-01c/E-01d/E-01e **四次独立运行稳定同形**。**设备功能门仍红，JS 继续不广告。**
- **KD-70**：P1 主路径成立；P2 中央分支未达成；原因已明确为夹具没有稳定推满堆（1.06% of 16 MiB），不是分类器阈值问题；不得下调 90%。
- **KD-69**：internal R8 + Worker + 中文 OCR 的设备判据满足（E-04）；但 Reopen condition 还要求"release 冒烟不得回退"，本批未重跑 release，`usage.txt` 未全量穷举、`ExportPdfWorker` 未单独触发，因此**整条 Reopen condition 未被本批完整证明**，KD-69 保持 open。
- **16KB**：x86_64 16KB 运行时成立；arm64 运行时未测；32 位 ML Kit `.so` 不合规但已被 ABI 裁剪。
- **KD-71**：设备全量 20 例中 `concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome` 与 `closeDuringAnInFlightTransactionWaitsAndRejectsIt` 均 `STATUS_CODE 0`（`evidence/2026-10-26-batch-23/step02-quickjs-oom.md:71`、`evidence/2026-10-26-batch-23/step02-quickjs-oom.md:76`），Reopen condition 的两条设备判据满足；**记录员不关闭 KD，关闭由主会话按本记录回链裁定**。
- **官方工具**：`llvm-readelf` 别名二进制不存在；E-09 用同发行版 `llvm-readobj --program-headers`，等价性待主会话一句裁定。

### 3.8 证据轨 —— **按 R4 落盘并独立复核；KD-74 未关闭**

- **落盘**：`docs/research/evidence/2026-10-26-batch-23/`，15 个 `.md`（`00-index.md` + `step00`…`step09`，含 `step01b`–`step01e` 与 E-08/E-09），1,745 行、内容 **95,895 B**、`du -sk` = 124 KiB，未超单批 512 KiB/16 个上限。
- **独立复核**：证据落盘员重算 6 个产物 sha256/字节数、重扫 12 类签名（E-08）、用官方 LLVM `llvm-readobj` 复核 ELF（E-09）、复核两台设备指纹、重跑体量与脱敏扫描（`00-index.md` §6：secrets/credential/emails 0 命中；home paths 2 条为扫描正则自身）。
- **记录员本轮实测**：`cat .../*.md | wc -lc` → `1745 95895`；`ls .../*.md | wc -l` → 15；`du -sk` → 124。**`00-index.md:7` 自报 95,850 B，与实际差 45 B**（见 §5 #9/#23）。
- **KD-74 状态**：C1/C2/C3/C5 有证据；C4 由本记录 §4 回链补齐；**C6 不满足**（KD-74 不关闭，记录不能写 "KD-74 closed"）。另有未闭合子项：`llvm-readelf` 等价性、arm64 16KB 运行时。**KD-74 保持 open。**
- **索引口径**：`00-index.md` §3.1 写 KD-69 "Reopen condition 满足"但未带 "release 本批未重跑"的限定；KD-69 条目已带该限定。记录员不改证据文件，登记为残余（§5 #8）。

## 4. 门禁与设备证据

> **记录员未运行 Gradle/设备**。以下"工单给定"行来自任务材料，不是本轮实证；已实跑行单列。

| 门 | 状态 | 证据/说明 |
|---|---|---|
| 单测门（第 1 轮） | **exit=0**（工单给定） | 无本批仓内日志；记录员未复跑。 |
| 修复环复跑 | **exit=0**（工单给定） | 同上；修复环材料称 11 条复核发现全部属实（与 §5 的 24 条清单口径不一致，见 §5 引言）。 |
| 强门（第 1 轮） | **exit=0**（工单给定） | 同上。 |
| `--write-locks` | **exit=0**（工单给定） | stdout 尾部：`available at: file:///D:/smart%20mistake%20book/build/reports/problems/problems-report.html`、`Deprecated Gradle features were used…`、`BUILD SUCCESSFUL in 2m 55s` / `228 actionable tasks: 22 executed, 206 up-to-date` / `Configuration cache entry stored.`。**但工作树只有 13/14 个模块锁、`:benchmark` 缺锁**，故 exit=0 不能读成"14 个模块全覆盖"。仓内无生成日志（`find build -maxdepth 2 -iname '*lock*'` 无对应文件；R3 §8 仍把实际生成标 UNVERIFIED）。 |
| 依赖钉版门 | **记录员实跑 exit=0** | `python tools/ci/check_dependency_pins.py` → `resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`，末行 `OK: quickjs checksums, upstream POM requirement and resolved runtime pins match`。**只证版本/sha256，不证锁门/API 兼容**。 |
| 锁文件清单门 | **记录员实跑 exit=0** | `python tools/ci/check_lockfiles.py` → 13 个未跟踪锁不能原样提交、`:benchmark` 缺锁（原文见 §3.3）。 |
| 锁文件清单门单测 | **记录员实跑 9/9 OK** | `PYTHONPATH=tools python -m unittest discover -s tools/tests -p test_check_lockfiles.py -v` → `Ran 9 tests ... OK`。 |
| T2 JVM | **只读日志 11/11 + 12/12** | `tmp/verify-batch23-t2/green.log`；记录员未运行。 |
| T3 JVM | **只读日志 19/3/2/1 + 旧金样 13/6/4/4** | `tmp/verify-batch23-t3/run-with-snapshot-test.log`；记录员未运行。 |
| 设备 T1 | **FAIL 1/5（E-01b/E-01e）** | `evidence/2026-10-26-batch-23/step01b-visualjs-isolation-rerun.md:94`、`step01e-visualjs-isolation-postboot-settled.md:85`；abandon 单方法 E-01c `:107`。 |
| 设备 KD-70 | **FAIL 19/20（E-02）** | P2 诊断 `raw=null, mallocSize=177216`，`evidence/2026-10-26-batch-23/step02-quickjs-oom.md:112`。 |
| 设备 16KB | **PASS 运行时 3/3（E-03）+ ELF PASS（E-09）** | `evidence/2026-10-26-batch-23/step03-page-size-16k.md:113`、`step09-elf-llvm-readobj.md:119`；`llvm-readelf` 等价性待裁。 |
| 设备 KD-69 | **PASS（E-04，internal）** | `evidence/2026-10-26-batch-23/step04-r8-smoke.md:142`；release 本批未重跑，Reopen condition 未完整证明。 |
| 设备备份/迁移 | **PASS 34/34、15/15（E-05/E-06）** | `evidence/2026-10-26-batch-23/step05-backup-restore.md:70`、`step06-migration.md:153`。 |
| 设备 KD-67 | **PASS 5/5（E-07）** | `evidence/2026-10-26-batch-23/step07-kd67-cas.md:82`。 |
| 设备签名全量 | **PASS 17/17（E-08）** | `evidence/2026-10-26-batch-23/step08-signature-rescan.md:130`。 |
| 真实 Provider | **UNVERIFIED** | 无凭据、未调用；JS 不广告；模型质量/费用未验。 |

**记录员对门禁的保留**：单测门/强门/`--write-locks` 的 `exit=0` 没有本批日志落在仓内；设备数字均有 E-file，但均来自设备轨/证据落盘轨，记录员只实读判定行、未复跑。故本记录不把"门禁全绿/设备全绿"写成可审计结论。

## 5. 复核发现与处置

> **口径说明**：本节清单来自任务材料"五、复核发现与处置"的 JSON（24 条，含 3 组重复：#1/#21、#3/#22、#9/#23），severity/status 按原复核；"处置"为记录员按当前树核对后的状态。修复环材料另称"11 条复核发现"，与 24 条清单不一致，按实际清单逐条登记，不合并成"11 条全部关闭"。`已修` = 当前树文本/代码已按发现改；`已登记` = 写入 KD/研究/构建注释但事项未闭合；`残留` = 当前树仍未改且未关闭。

| # | 位置 | 发现 | 级别 | 原复核状态 | 本批处置（当前树核对） |
|---|---|---|---|---|---|
| 1 | `docs/known-defects.md` KD-67 Progress | KD-67 仍写"仪器化用例已写、未执行/UNVERIFIED"，与 E-07 5/5 PASS 冲突 | 中 | 已独立复核 | **已修**：当前 `known-defects.md:2619` 写 5/5 PASS、`OK (5 tests)`、E-07 回链；KD-67 保持 open 的理由只剩 feature 消费面。 |
| 2 | KD-66/69/70 条目 | 三条 KD 未随批次 23 设备证据更新 | 中 | 已独立复核 | **已修**：KD-66 批次 23 Progress（`known-defects.md:2588-2594`）、KD-69（`:2659`）、KD-70（`:2671`）均已回写设备事实。 |
| 3 | `docs/current/visual-generation-contract.md:72` | §4 仍写"5 例未设备执行/修复后 UNVERIFIED/watchdog 无绿证据" | 中 | 已独立复核 | **已修**：`:72` 已改为 5 例 4 绿 1 红、abandon 红、加载态对照，并写明"旧表述作废"。 |
| 4 | `app/gradle.lockfile`；`.github/workflows/android-check.yml:75,85`；`benchmark/` | 锁只覆盖 localFirst，CI 单测/debug 用 strictOffline；benchmark 无锁 | 低 | 已独立复核 | **未修（结构性）**：`grep -c strictOffline app/gradle.lockfile` = 0、`localFirstDebugRuntimeClasspath` = 232、`benchmark/gradle.lockfile` 不存在；KD-72 open。 |
| 5 | 锁文件轨自我报告；`docs/research/2026-10-26-lockfile-generation.md:584` | 报告称"锁覆盖仅 15 个配置"，实际 23 个不同配置名；无 `--write-locks` 生成日志；`settings-gradle.lockfile` 未说明 | 低 | 已独立复核 | **部分修**：记录员逐行解析 13 个锁确认 **23 个不同配置名**；R3 §10.1 已说明 `settings-gradle.lockfile` 是 settings 锁（不计项目锁）；**生成日志仍不在仓内**（R3 §8 仍标 UNVERIFIED）。 |
| 6 | `docs/known-defects.md` KD-72 Symptom | 仍写"仓库无任何锁文件"和"默认 LENIENT" | 低 | 已独立复核 | **已修**：KD-72 现写 13 个未跟踪锁 + `settings-gradle.lockfile`、`benchmark` 缺锁、默认 **DEFAULT**（`:2702`）；根 `build.gradle.kts` 注释同步。 |
| 7 | `core/ui/gradle.lockfile`、feature 四模块、`core/markdown/gradle.lockfile` | 锁生成写进热区模块目录，未按"变脏则整文件纳入/停项登记"记录 | 低 | 已独立复核 | **已登记**：KD-72 Progress、根构建注释、R3 §10.2 均写明 13 个未跟踪锁不能原样提交；`core/markdown/` 整体仍是 `??`。 |
| 8 | `evidence/2026-10-26-batch-23/00-index.md:59` | 索引写 KD-69 "Reopen condition 满足"，但 release 冒烟未重跑 | 低 | 已独立复核 | **部分修**：KD-69 条目 `:2659` 已加"release 本批未重跑/usage.txt 未全量穷举/ExportPdfWorker 未单独触发"；索引 §3.1 仍无该限定，登记为残留。 |
| 9 | `evidence/2026-10-26-batch-23/00-index.md:7` | 自报 95,850 B，实测 95,895 B（差 45 B）；行数/文件数/du 正确 | 低 | 已独立复核 | **残留**：记录员实测 `cat .../*.md \| wc -lc` = `1745 95895`；索引未改（证据文件非记录员本轮写域），本记录 §3.8 给出正确值。 |
| 10 | `KnowledgeQuizItemVerificationUpgraderTest.kt:428-480`；仪器化 `:144-165` | 并发只覆盖"两个写者改成同一目标"的 AlreadyUpgraded；`ConcurrentVerificationChange` 零覆盖 | 低 | 已独立复核 | **残留**：`grep -rn ConcurrentVerificationChange core/data/src core/domain/src` 只命中实现（`KnowledgeQuizItemVerificationUpgrader.kt:105/:130/:240`），无测试；已写入 KD-67 残余与决策台账。 |
| 11 | `NewIntroductionPolicyTest.kt:245,285,323-324` | `beyondHorizonCount` 只被断言为 0，无非零用例 | 低 | 已独立复核 | **残留**：当前树仍只有 0 值断言；已写入 KD-68 残余。 |
| 12 | 13 个锁文件 | 锁是在他线未提交的 core/ui/core/export/settings 构建改动上生成的，与 HEAD 不一致；原样提交会使 CI 在锁解析期红 | **高** | 已独立复核 | **未修（KD-72 open）**：记录员实跑 `git show HEAD:core/ui/build.gradle.kts \| grep richtext` → `:30 implementation(libs.richtext.commonmark)`；`grep -rn richtext-commonmark core/ui/gradle.lockfile app/gradle.lockfile` 无命中（锁只有 richtext-ui*/material3）；`git show HEAD:core/export/build.gradle.kts \| grep -c serialization` = 0，而 `grep -c kotlinx-serialization core/export/gradle.lockfile` = 5。**不得原样 `git add`**。 |
| 13 | `.github/workflows/android-check.yml:75-81,210`；各模块锁 | 锁未覆盖 test/androidTest/runtime，CI 的单测与 connectedAndroidTest 漂移不被拦 | 中 | 已独立复核 | **部分修**：新增 `resolveLockableClasspaths`、`check_lockfiles.py`、CI 两个 step；但**当前锁仍是旧的 23 个配置、缺 `unitTestRuntime`/androidTest 配置**，补覆盖需干净树重生成并入库。 |
| 14 | `benchmark/build.gradle.kts:34-40`；`settings.gradle.kts:19-34`；`core/markdown/` | benchmark 无锁；core/markdown 未跟踪锁会掩盖缺口，且不能单独提交 | 中 | 已独立复核 | **部分修**：`check_lockfiles.py` 按 include 列表点名 `:benchmark` 缺锁；P5 裁定（markdown 随批入库或排除）仍未执行，锁未重生成。 |
| 15 | `.github/workflows/android-check.yml:15-17`；KD-72；根 `build.gradle.kts:21-24` | T1 的"落 CI"半边未实现；KD-72/根注释仍写"无锁/LENIENT" | 中 | 已独立复核 | **部分修**：CI 已加 manifest gate + write-locks-diff 两个 step；根注释与 KD-72 已改；**CI/Gradle 未运行**，且无跟踪锁时 write-locks step 会跳过。 |
| 16 | 根 `build.gradle.kts:37-40` 与锁配置集合 | 对他线合法升级的影响不对称：命中已锁配置的升级会红，只命中 test/androidTest/runtime 的升级静默放行 | 中 | 已独立复核 | **部分修**：`resolveLockableClasspaths` 收窄不对称（新任务会把测试/运行面纳入），但**锁未按新任务重生成**；旧锁仍只覆盖 23 个配置。 |
| 17 | `LearningDao.kt:181`；`RoomStudyDatabase.kt:1610`；`LearningProjectionPort.kt:78-84` | T2 CAS 正确，但 `overwriteAssessmentItem` 仍可整行改写含 `verification_status`，存储层无守卫 | 低 | 已独立复核 | **残留（契约边界）**：`@Update overwriteAssessmentItem` 仍在；已写入 KD-67 残余与决策台账"不得把 CAS 失败当损坏行去走覆盖口"。 |
| 18 | `StudyReviewPlannerService.kt:299-327`；`StudySnapshotBuilder.kt:91-95` | T3 的不可调度计数无测试，红绿脚本只覆盖 policy/mappers/overview | 中 | 已独立复核 | **已修**：新增 `StudySnapshotIntakeUnschedulableTest`（新文件 1 例），经 `StudySnapshotBuilder.build` 走假库 + 真仓库，实测 `intakeUnschedulableCount == 1`；`tmp/verify-batch23-t3/run-with-snapshot-test.log` → `OK (1 test)`。 |
| 19 | `NewIntroductionPolicy.kt:90-96` 与 `:205` | KDoc 说 unschedulableCount 是 deferred 子集，实现对 ordered 计数 | 低 | 已独立复核 | **残留**：KDoc 仍写"a count over [deferred]"，实现仍 `ordered.count`；今日可证等价，已写入 KD-68 残余（未改代码/KDoc）。 |
| 20 | `StudySnapshotBuilder.kt:91-95`；`StudyReviewPlannerService.kt:308-314,383,505` | 每次快照多一次 `readExams()` + 全积压难度档批量读，与同一轮 planner 重复 | 低 | 已独立复核 | **残留（静态风险）**：代码仍在；未做基准，已写入 KD-68 残余。 |
| 21 | `docs/known-defects.md:2611`；E-07 | KD-67 台账（17:04 写入）仍称仪器化未执行，与 16:25 设备 5/5 自相矛盾 | 中 | 已独立复核 | **已修**：同 #1，当前 `:2619` 已回填设备 5/5。 |
| 22 | `known-defects.md:2584`；`visual-generation-contract.md:72` | KD-66 与契约仍称复跑 UNVERIFIED、第 5 例未执行 | 中 | 已独立复核 | **已修**：KD-66 `:2588-2594`、契约 `:72` 均已改 5 例 4 绿 1 红。 |
| 23 | `evidence/2026-10-26-batch-23/00-index.md:7`；`known-defects.md` KD-75 | 索引字节数差 45 B；KD-75 的枚举行号偏移 5 行 | 低 | 已独立复核 | **部分**：字节数仍差 45 B（同 #9）；**枚举行号不成立**——`CapturedQuestionDocument.kt:205` 是 `enum class QuestionBlockProvenance`，`:210` 是常量 `MODEL_DOCUMENT_PARSE`，KD-75 的 `:210` 指向常量，不算错。 |
| 24 | `pdf-implicit-labeling.md:13,92` 与 `:543-552` | R1 顶层仍写"当前 PDF 无 AI 生成文本块"，与 §10.3 矛盾，读者只引 §0/§1.4 会得到错误适用范围 | 低 | 已独立复核 | **已修（文档）**：记录员在 R1 §0 第 2 条与 §1.4 表格加"复核补记"指回 §10.3，明确"原断言不成立、判据须覆盖 `MODEL_DOCUMENT_PARSE` 文本"；不改原结论。 |

**记录员追加核对（不在原清单，低）**：

1. `--write-locks exit=0` 与 `:benchmark` 缺锁并存（§4）：`exit=0` 不能证明 14 模块全覆盖；且仓内无生成日志，生成命令的实际范围不可复核。
2. T5 summary 的"核对清单与发现见 evidence"在仓内无对应 E-file（§3.5）；T5 只能算文档完成，不能算设备/门禁证据。
3. `00-index.md` 之外的证据文件均未改；记录员只读，未把 `build/` 当证据。

## 6. 遗留 / UNVERIFIED / 待裁

**工程门**

1. **门禁 `exit=0` 无仓内日志**（单测门/修复环/强门/`--write-locks` 均为工单给定）；记录员未运行 Gradle。提交前建议主会话把命令与 tail 落盘到 `build/`（KD-74 不以门禁日志为判据）。
2. **`--write-locks exit=0` ≠ 14 模块全覆盖**：13/14、`:benchmark` 缺锁、无生成日志。
3. **锁文件结构性问题（高）**：13 个未跟踪锁与 HEAD 不一致（core/ui 缺 `richtext-commonmark`、core/export 多 serialization）、只覆盖 23 个配置（缺 `unitTestRuntime`/androidTest）、生成于热区脏树。**不得原样提交。**
4. **锁门未生效**：KD-72 保持 open；`check_lockfiles.py` 在无跟踪锁时只提示；CI 的 write-locks step 同样跳过。
5. **AIDL 门**：本批未跑 Gradle AIDL 任务；`aidl.exe` 双输入禁用规则继续有效。

**T1 / 4E.2 前置**

6. **T1 设备功能门 1/5 红**：`abandon()` 死亡确认结构性失败（`VisualJsClientEngine.kt:570-571`），四次独立运行稳定；建议另立 KD，不应放宽断言。
7. **加载态敏感性**：冷启动后立即跑 5/5 红（attach 4.6–7.5 s > 5 s）；沉降约 2 min 后 1/5 红。门本身未做"加载态放宽/重试"裁定。
8. **KD-66 关闭门未达**：现有 watchdog 用例以 ≤6 s 判死，弱于 hard+500 ms；#240 形状仍泄漏 daemon 线程。
9. **4E.2 交互/保真未做**；L3 无生产装配点；`UnwiredVisualContentMeasurer` 仍在；ContentProvider/Startup 隔离装配面未 dumpsys 审计。

**T2 / KD-67**

10. **生产未接线**：feature 消费者、确认流调用点、`sourceVerifier` 生产实现、ASK/CLARIFY 设备集成用例均无。
11. **并发矩阵缺口**：`ConcurrentVerificationChange` 零测试；`overwriteAssessmentItem` 可绕过 CAS（KD-41 边界）。

**T3 / KD-68**

12. **UI 热区**：`ReviewRoute.kt:529`/`:558` 仍旧口径；`ReviewIntakeBacklogPreviewInstrumentedTest` 未覆盖"不可调度/专项会话"。
13. **低风险残留**：KDoc 与实现口径不一致；`beyondHorizonCount` 无非零用例；快照重复读取未做基准。

**锁文件 / 供应链**

14. **KD-73 仍 open**：完整解析图 API 兼容无证明；pin gate 只校版本/sha256；解析图来源仍是 AIDL 修复前旧 XML。
15. **`:core:markdown` 所有权未裁**：未跟踪模块 + 锁不能单独入库（R3 P5）。
16. **负控未做**：人为抬版/加依赖在解析期红未执行。
17. **Gradle 9.6.x #38057**：锁定生效后 runtime classpath 顺序可能变化，锁落地后需 release/benchmark 冒烟（R3 P1）。

**T4 / KD-75**

18. **零代码**：PDF 仍无 `/AIGC`；Android PDFium 接受度（U3）、缓存指纹升级、打印子集、DOCX 对称缺口均未动。
19. **写入判据双条件**：必须先解决 `MODEL_DOCUMENT_PARSE` provenance 传递，才能安全实现"AI 图或模型文本"判据。
20. **文案**：已如实写"PDF 尚未写文件级隐式信息"，未承诺合规。

**设备 / 证据**

21. **KD-74 未关闭**：`llvm-readelf` 等价性待主会话裁定；arm64 16KB 运行时未测；C6 不满足。
22. **索引小偏差**：`00-index.md:7` 字节数 95,850 vs 实测 95,895；§3.1 的 KD-69 结论缺 release 限定。
23. **32 位 ML Kit `.so`**：`armeabi-v7a`/`x86` 的 `libmlkit_google_ocr_pipeline.so` `LOAD align=0x1000`；生产 App 已 ABI 裁剪，放开 ABI 会重新引入。
24. **构建日志编码**：Windows 非 ASCII 用户名使 AIDL 生成 Java 头注释含 GBK 字节，javac 打印不可映射字符但构建成功；未改构建文件。

**热区 / 他线 / 待裁**

25. **G1/G2、5b/5c、阶段 6、6B-3、AUD-08** 均因热区未空出，本批不做。
26. **真实 Provider / Wave X / 5B 出题回收接线**不在本批；模型质量 UNVERIFIED。
27. **origin/main 未 fetch**：远端实时 hash 未核。
28. **Mimosa 无完整扫描结论**，不宣称项目安全。

## 7. 提交清单建议（记录员只读核对 git status；**提交由主会话执行**）

**前置条件**：主会话在提交前复跑单测门/强门并留 `exit=0` 日志；**不要把 13 个未跟踪锁文件加入提交**（与 HEAD 不一致）；`core/export`、feature 四模块、core/ui、Root、SecondaryScreens、`settings.gradle.kts`、`.gitignore`、docs/current 其余 M 一律排除（热区）。本节按 ≤6 路径/块列出**建议纳入**的本批文件（新文件标"新"，M 标"改"）：

- **块 1（T2 存储层）**：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/port/LearningProjectionPort.kt`（M）、`.../dao/LearningDao.kt`（M）、`.../DatabaseContract.kt`（M）、`.../RoomStudyDatabase.kt`（M）、`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/AssessmentItemVerificationStatusInstrumentedTest.kt`（新）、`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizItemVerificationUpgrader.kt`（M）
- **块 2（T2 测试）**：`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/KnowledgeQuizItemVerificationUpgraderTest.kt`（M）、`.../RoomBackedStudyExperienceRepositoryTest.kt`（M）
- **块 3（T3 域层）**：`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/NewIntroductionPolicy.kt`（M）、`.../StudyExperienceRepository.kt`（M）、`core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/NewIntroductionPolicyTest.kt`（M）、`.../StudyReviewOverviewIntakeTest.kt`（新）
- **块 4（T3 数据层）**：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyExperienceMappers.kt`（M）、`.../StudySnapshotBuilder.kt`（M）、`.../StudyReviewPlannerService.kt`（M）、`core/data/src/test/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyExperienceMappersIntakeTest.kt`（新）、`.../StudySnapshotIntakeUnschedulableTest.kt`（新）、`docs/specs/batch-intake-spec.md`（M）
- **块 5（锁门代码/CI）**：`build.gradle.kts`（M）、`.github/workflows/android-check.yml`（M；本批新增两个锁 step，**整文件纳入并在提交说明注明**）、`tools/ci/check_lockfiles.py`（新）、`tools/tests/test_check_lockfiles.py`（新）
- **块 6（研究 R1/R2/R3/R4 + 基线）**：`docs/research/2026-10-26-pdf-implicit-labeling.md`（新）、`docs/research/2026-10-26-quickjs-issue240-status.md`（新）、`docs/research/2026-10-26-lockfile-generation.md`（新）、`docs/research/2026-10-26-device-evidence-convention.md`（新）、`docs/research/2026-10-26-batch-23-baseline.md`（新）
- **块 7（T4/T5 文档）**：`docs/ai-content-disclosure-copy.md`（M）、`docs/current/visual-generation-contract.md`（M；**热区重叠，计划/工单授权 T5；整文件纳入并注明**）、`docs/testing/device-state-safety.md`（M）
- **块 8（KD/台账/主线）**：`docs/known-defects.md`（M；**开工干净，本批 KD-66…75 + 本记录；整文件纳入**）、`docs/REFACTOR-MASTER-LINE.md`（M；顶部 + 补修 + 4E）、`docs/research/algorithm-version-ledger.md`（M；§3.33 零 bump）、`docs/agent-first-refactor-decisions-2026-09-23.md`（M；批次 23 T2 CAS 边界）
- **块 9（设备证据）**：`docs/research/evidence/2026-10-26-batch-23/00-index.md`、`step00-environment.md`、`step01-visualjs-isolation.md`、`step01b-visualjs-isolation-rerun.md`、`step01c-abandon-confirmed.md`、`step01d-visualjs-isolation-coldboot.md`
- **块 10（设备证据续）**：`step01e-visualjs-isolation-postboot-settled.md`、`step02-quickjs-oom.md`、`step03-page-size-16k.md`、`step04-r8-smoke.md`、`step05-backup-restore.md`、`step06-migration.md`
- **块 11（设备证据尾 + 本记录）**：`step07-kd67-cas.md`、`step08-signature-rescan.md`、`step09-elf-llvm-readobj.md`、`docs/research/2026-10-26-batch-23-record.md`（本文件）

**明确排除（他线在飞/不安全，一律不动）**：

- 13 个 `*/gradle.lockfile` 与 `settings-gradle.lockfile`（未跟踪，与 HEAD 不一致，KD-72 未关闭；等干净树重生成后再单独提交）。
- `core/export/**`（5 M）、`feature/{capture,review,tutor,library}/**`、`core/ui/**`、`app/src/main/kotlin/.../SmartMistakeBookRoot.kt`、`SecondaryScreens.kt`、`app` 其余开工即脏 M（LearningMasteryScreen/ReviewReminder*/SchedulingSettingsScreen/StartupStateBanner/SmartMistakeBookDestinations 等）、`settings.gradle.kts`、`.gitignore`。
- `core/domain` 他线 4 M（BatchImportRepository/MistakeDetailRepository/SchedulingEvaluation/TutorJudgedReview）；`core/data` 他线 M（`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`、capture/model 测试等，全路径见基线 §2.2）；`core/database` 他线其余 M（RoomBatchImportStore/TutorConversation*/BatchImportDao/CleanRedrawTaskPort/FTS 测试）。
- `docs/current` 其余 M、`docs/research` 他线未跟踪记录（`2026-10-26-kb-*`、`2026-10-26-dense-union-fusion-precedents.md`、`2026-10-26-ui-copy-refinement.md` 等）、`docs/superpowers/**`、`tools/kb*`、`knowledge-production/**`。
- `build/device-track-batch23/**`（原始全量，`.gitignore` 忽略）与 `tmp/verify_batch23_*.py` / `tmp/verify-batch23-*/`（临时复核脚本/日志，不入提交；复跑命令见 §4/附录）。
- `gradle/libs.versions.toml`、`app/build.gradle.kts`（本批零 diff）。

**提交说明**：本批建议提交 11 块，每块 ≤6 路径；`.github/workflows/android-check.yml`（本批新增两个锁 step）、`docs/known-defects.md`（开工干净，本批 KD-66…75）、`docs/current/visual-generation-contract.md`（授权热区重叠）、`docs/REFACTOR-MASTER-LINE.md`、`docs/research/algorithm-version-ledger.md`、`docs/agent-first-refactor-decisions-2026-09-23.md` 均**整文件纳入并在提交说明中注明"整文件纳入，含既有改动"**。提交/推送由主会话执行；非快进 merge，不 rebase。

## 附录：本记录员实际执行的只读核对（可复跑；未运行 Gradle/设备/任何 git 写命令）

```bash
# 计划/基线/四研究/证据文件读取（Read/sed/grep）
git rev-parse --short HEAD                 # f0a145f8
git status --porcelain | wc -l             # 698
git status --porcelain | grep -c '^ M'     # 220
git status --porcelain | grep -c '^??'     # 478
for d in feature/library feature/tutor feature/capture feature/review core/ui; do
  printf "%s " "$d"; git status --porcelain -- "$d" | wc -l; done
#   → 40 / 58 / 16 / 12 / 35
git status --porcelain -- core/database/schemas \
  core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt \
  core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt
#   → 无输出（零 DB/请求 schema 变化）

# T2/T3 落点（porcelain）
git status --porcelain -- core/database/.../port/LearningProjectionPort.kt ... # 11 个文件：10 M + 1 新

# 锁文件（只读）
for f in app core/model core/domain core/database core/data core/export core/ui core/markdown \
         feature/capture feature/review feature/tutor feature/library feature/profile benchmark; do
  [ -f "$f/gradle.lockfile" ] && echo "OK $f" || echo "MISSING $f"; done
#   → 13 OK；MISSING benchmark
ls -la settings-gradle.lockfile            # 267 B
git ls-files '*gradle.lockfile' | wc -l    # 0
grep -c strictOffline app/gradle.lockfile  # 0
grep -c localFirstDebugRuntimeClasspath app/gradle.lockfile  # 232
grep -l "org.jetbrains.kotlin:kotlin-stdlib:2.3.21" */gradle.lockfile */*/gradle.lockfile | wc -l  # 13
grep -n "quickjs-kt-android" app/gradle.lockfile core/data/gradle.lockfile
#   → app:217 / core/data:97（均 1.0.15）
python - <<'PY'  # 逐行解析 13 个锁的配置名
#   → files 13, distinct configs 23, bad []
PY

# 门禁脚本（实跑；非 Gradle）
python tools/ci/check_dependency_pins.py
#   → resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']; OK: ...; exit 0
python tools/ci/check_lockfiles.py
#   → OK: 尚未落地 / 13 个未跟踪锁 / 缺锁 :benchmark; exit 0
PYTHONPATH=tools python -m unittest discover -s tools/tests -p test_check_lockfiles.py -v
#   → Ran 9 tests ... OK

# T2/T3 JVM（只读日志，非本轮运行）
tail -30 tmp/verify-batch23-t2/green.log
#   → KnowledgeQuizItemVerificationUpgraderTest OK (11 tests); AdaptiveSelectionAssemblerTest OK (12 tests)
grep -E "^=====|^OK \(" tmp/verify-batch23-t3/run-with-snapshot-test.log
#   → NewIntroductionPolicyTest 19; StudyReviewOverviewIntakeTest 3; StudyExperienceMappersIntakeTest 2;
#     StudySnapshotIntakeUnschedulableTest 1; 旧金样 13/6/4/4

# 证据体量与判定行（只读）
ls docs/research/evidence/2026-10-26-batch-23/*.md | wc -l   # 15
cat docs/research/evidence/2026-10-26-batch-23/*.md | wc -lc # 1745 95895
du -sk docs/research/evidence/2026-10-26-batch-23            # 124
grep -n "^## 7\. 判定" docs/research/evidence/2026-10-26-batch-23/step0*.md
#   → 判定行：step01:115 / step01b:94 / step01c:107 / step01d:81 / step01e:85 / step02:112 /
#     step03:113 / step04:142 / step05:70 / step06:153 / step07:82 / step08:130 / step09:119

# 关键源码锚点
grep -n "compareAndSetAssessmentItemVerificationStatus" \
  core/database/.../port/LearningProjectionPort.kt core/database/.../dao/LearningDao.kt \
  core/database/.../RoomStudyDatabase.kt
#   → port:68 / dao:201 / adapter:1586
grep -n "validateAssessmentItemVerificationStatusCas" core/database/.../DatabaseContract.kt  # :620
grep -n "@Test" core/database/.../AssessmentItemVerificationStatusInstrumentedTest.kt  # 5 处
grep -n "StudyDatabaseAssessmentItemVerificationStatusWriter\|sourceVerifier.verify" \
  core/data/.../study/KnowledgeQuizItemVerificationUpgrader.kt  # :55 / :213 / :264
grep -n "enum class QuestionBlockProvenance\|MODEL_DOCUMENT_PARSE" \
  core/model/.../CapturedQuestionDocument.kt  # :205 / :210
grep -rn "AIGC" core/export/src/main | wc -l  # 0
grep -n "mistake-export-v3" core/export/.../MistakePdfEligibility.kt  # :186

# 研究文档修正（本批记录员仅改文档）
#   docs/research/2026-10-26-pdf-implicit-labeling.md §0 第 2 条 / §1.4 表格：加指回 §10.3 的复核补记
```

> 未执行：任何 `./gradlew`、`adb`、`am instrument`、`connectedAndroidTest`；任何 git 写命令。门禁/设备/研究结论均标注来源（工单材料/证据 E-file/只读日志/研究文档），不冒充本记录员实跑。**证据链接检查**按 R4 §7.5 在写完本记录后执行（`cd docs/research` + 两条循环，预期无输出）。

## 8. 主会话实跑与收口状态（工单给定；待主会话追加提交/推送）

- **门禁（工单给定）**：单测门第 1 轮 `exit=0`；修复环复跑 `exit=0`；强门第 1 轮 `exit=0`；`--write-locks` `exit=0`（stdout 尾部 `BUILD SUCCESSFUL in 2m 55s` / `228 actionable tasks: 22 executed, 206 up-to-date` / `Configuration cache entry stored.`）。
- **修复环（工单给定）**：材料称 11 条复核发现全部属实；其中设备/文档类已修，锁文件结构性条目已修到"生成面/门禁面"，但重生成与 Gradle/CI 实跑受本任务约束阻塞，KD-72 如实保留 open。**本节与 §5 的 24 条清单口径不一致，记录员按实际清单登记，未把 11 条直接写成"全部关闭"。**
- **提交/推送**：截至本记录时点，**未提交、未推送**；建议清单与排除项见 §7。提交由主会话执行；非快进 merge，不 rebase。
- **本批不声称全绿**：T1 1/5 红（abandon 结构性缺陷）、KD-70 P2 红、锁文件 13/14 且未入库（KD-72）、T4 零代码（KD-75）、KD-74 未关闭、KD-73 未证、真实 Provider UNVERIFIED、JS 不广告。
- Mimosa 提交仍无完整扫描结论，不宣称项目安全。

## 8. 主会话收口（2026-10-26，实跑证据）

- **单测门**（11 模块 `--continue`）：**exit=0**（BUILD SUCCESSFUL，1 executed / 227 up-to-date——批次 23 修复环后的树未再变）。
- **强门**（六 androidTest 编译 + `:app:assembleLocalFirstDebug`）：**exit=0**（BUILD SUCCESSFUL，2 executed / 270 up-to-date）。
- **提交**：`f8a362ee`（50 文件），按 §7 的 11 块分块 add；**13 个未跟踪 `gradle.lockfile` 与 `settings-gradle.lockfile` 均未纳入**（与 HEAD 不一致，KD-72 保持 open，待干净树重生成后单独提交）；热区文件未触碰。
- **推送**：`f0a145f8..f8a362ee` 已上 origin/main（`ls-remote` 复核 = 本地 HEAD）。
- **本批不声称全绿**：T1 隔离 4/5（client `abandon()` 死亡确认结构性失败，4 次复现）；KD-70 P2 仍红（mallocSize 177216≈1.06% 未达 90% 阈值）；KD-72/73/74/75 open；T4 只研究+登记未改导出链；真实 Provider UNVERIFIED；JS 仍不广告。
- **主会话裁定（KD-74 证据等价性）**：`llvm-readobj --program-headers` 与官方判据所用 `llvm-readelf -l` 读取同一 ELF 程序头表；对"每 LOAD p_align≥2^14"与"RELRO 终点 mod 16K 及官方独占 LOAD 豁免"两项，字段级等价成立，接受为可复核证据（E-09 已附程序头原始字段）。KD-74 其余关闭项（记录回链）随本节成立。
- Mimosa 提交仍无完整扫描结论（scanner_enobufs）——不宣称项目安全。
