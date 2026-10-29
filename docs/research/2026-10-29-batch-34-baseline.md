# 批次 34 基线核对（2026-10-29，只读）

> 核对对象：`.zcode/plans/plan-2026-10-29-batch-34-dual-backend-kd80.md` §0。
> 本文全部结论来自本轮实际执行的只读命令与当前源码；凡未能验证者标 `UNVERIFIED`。
> 未运行 Gradle、未跑设备测试、未改任何文件（本文除外）。

---

## 一、HEAD / origin/main / 工作树 / schema

### 1.1 Git 引用

```
$ git rev-parse HEAD        → db792fa873f4bc150f415b67d1c2e3b1bab6c995
$ git rev-parse origin/main → db792fa873f4bc150f415b67d1c2e3b1bab6c995
$ git log --oneline -3
  db792fa8 docs(4e.2): 批次33 台账/判据回改 + 记录 + JVM 80-C3 补证（KD-80 剩设备侧 rowCounts 逐名一项）
  5cbce94e test(4e.2): 批次33 盲集诚实化（N13 降级为覆盖 ≥2px、代数注释改正、屏侧例数统一 24）
  a43d0863 feat(4e.2): 批次33 scene digest 内容绑定 + 预算口径统一用例 ...
```

- **HEAD 与本地 `origin/main` 引用相同**（`db792fa8`）。这是**本地引用**，远端实际推送状态未联网核对 → 远端是否已收到 UNVERIFIED（计划 §0 提到的 443 重试无法在本轮只读验证）。

### 1.2 工作树计数

```
$ git status --porcelain | wc -l                 → 689
$ git status --porcelain | grep -c '^??'          → 472   （未跟踪）
$ git status --porcelain | grep -vc '^??'         → 217   （已跟踪但已修改）
```

- 工作树**极脏**：689 条改动。未跟踪的量大头：`core/ui` 24、`feature/tutor` 22、`docs/research` 16、`feature/library` 13、`tools/kb_coverage` 11；另有 `tmp/`、`tmp_slice_dump.txt`、`output/`、`x` 等临时目录/文件。
- 未跟踪 `gradle.lockfile` **13 个**（计划 §2「锁文件不提交」→ 这些不得纳入提交）。

### 1.3 DB schema（应为 71）

- `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/StudyDatabase.kt:139`：`internal const val STUDY_DATABASE_VERSION = 71`。
- `71.json` 实读（本机 python）：`version=71`、`identityHash=714c0ecb85ee5edf74f0013740148138`、`entities=90`、`views=2`、`378961` 字节。
- `SmbkSelectionPlan.kt:44` `SCHEMA_VERSION = 71`、`:51` `IDENTITY_HASH = "714c0ecb85ee5edf74f0013740148138"`、`:54` `CLOSURE_VERSION = 3`、`:546` `require(allTables.size == 90)`、`:562` `require(softReferences.size == 21)`。
- **三处 schema 版本/身份一致（71 / 714c…）**，与计划 §0「DB schema 应 71」相符。

### 1.4 请求 schema 版本

- `core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/ModelTasks.kt:546` `TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION = 18`、`:547` `CURRENT_SCHEMA_VERSION = TUTOR_VISUAL_CONTEXT_SCHEMA_VERSION`。
- **请求 schema（ModelTaskRequest）= 18**。计划 §2 所述「A-03 的 `method.enum` 属请求 schema 而非 DB schema」即指这一层（与 DB schema 71 无关）。

---

## 二、热区快照 + 三条主要干的开工态

### 2.1 热区（计划 §2 禁改清单）开工即脏

逐路径 `git status --porcelain -- <path>` 计数：

| 热区路径 | 脏条目数 |
|---|---|
| `settings.gradle.kts` | 1（M） |
| `.gitignore` | 1（M） |
| `core/domain` | 5 |
| `core/ui` | 35（含大量 `??` 新文件，如 `Claude*.kt`） |
| `core/export` | 6 |
| `core/markdown` | 1（整个目录 `??`） |
| `SmartMistakeBookRoot.kt` | 1（M） |
| `feature/*` | 134 |

- **结论：计划 §2 列出的每一条热区在开工时都已是脏的**（他线未提交改动 + 未跟踪新文件混在同一工作树）。本轮不主张这些改动的来源，只登记"开工态非干净"这一事实；本批不得向这些路径追加改动。

### 2.2 `core/data` 开工态

`git status --porcelain -- core/data | wc -l` = **14**：

- 已改（M）13 个，含 `BackupRestoreInstrumentedTest.kt`、`DeviceMigrationDrillInstrumentedTest.kt`、`BatchImportRepositoryInstrumentedTest.kt`、`SplitImportCommitReceiptInstrumentedTest.kt`、`SplitImportReceiptReconciliationInstrumentedTest.kt`、`ModelTaskRepositoryInstrumentedTest.kt`、`StudySubmissionWithoutFixtureInstrumentedTest.kt`、`RoomMistakeDetailRepository.kt`、`RoomTutorToolRunner.kt`、`GoldenRetrievalJvmTest.kt`、`OpenAiNativeToolsProtocolTest.kt`、`TutorToolRequestDualParseTest.kt`、`VisualDrawOpsParserTest.kt`。
- 未跟踪 `??` 1 个：`core/data/gradle.lockfile`。

### 2.3 `core/database` 开工态

`git status --porcelain -- core/database` = **10**：

- 已改（M）9 个，含 `LibraryFtsCountPathInstrumentedTest.kt`、`LibraryFtsCountPathScale50kInstrumentedTest.kt`、`LibraryFtsRankingPathInstrumentedTest.kt`、`RoomBatchImportStore.kt`、`TutorConversationDatabaseContract.kt`、`dao/BatchImportDao.kt`、`dao/PendingCaptureDao.kt`、`dao/TutorConversationDao.kt`、`port/CleanRedrawTaskPort.kt`。
- 未跟踪 `??` 1 个：`core/database/gradle.lockfile`。

### 2.4 `app/build.gradle.kts` 开工态

- `git status --porcelain app/build.gradle.kts` → **空输出**（exit 0），`git diff --stat` 亦空 → **该文件干净、无未提交改动**。

---

## 三、KD-80 现状态与四条判据

### 3.1 现状态

`docs/known-defects.md:2985` 标题：**KD-80 (open …)**；`:3045` 收口句「剩余项收敛为一项：设备侧 rowCounts 90 键枚举（静态 `RESULT=PASS` 不计入）」。**保持 open**。

### 3.2 四条判据原文（`docs/research/2026-10-29-batch-31-device-criteria.md`）

- **80-C1**（§3.1，`:374-386`）：设备 `BackupRestoreInstrumentedTest` → `OK (34 tests)`；0 失败/错误/跳过；批次 30 的 8 条红例全绿；log 无 `BackupSnapshotTrimException`、无 `expected:<69>`。
- **80-C2**（§3.2，`:388-424`）：在仓库根跑 `S=71 python …` 脚本做表清单与 schema 逐名一致比对，要求 `RESULT=PASS`/`EXIT=0`；**且设备侧 `rowCounts` 90 键逐名进证据**。
- **80-C3**（§3.3，`:456-462`）：设备 `BackupSnapshotTrimmerInstrumentedTest` 9 例 `OK (9 tests)` + JVM `SmbkSelectionPlanTest` 12 例 `12/12`。
- **80-C4**（§3.4，`:464-473`）：备份/恢复后旧行与视觉四表（`tutor_visual_program/snapshot/message_visual/revision_visual`）引用关系保持，需显式断言（不得用"表在 90 张清单里"替代）。
- 关闭规则（§3.5，`:475-480`）：`80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4 → 可关闭`。

### 3.3 设备侧 rowCounts 是否已逐名（grep 证据）

- 设备测试里对 rowCounts 的断言**只有集合相等**，没有逐名打印：
  - `BackupRestoreInstrumentedTest.kt` 无 rowCounts 断言；
  - `BackupSnapshotTrimmerInstrumentedTest.kt:45` `assertEquals(SmbkSelectionPlan.rowCountKeys, report.postRowCounts.keys)`；
  - `BackupSelectionCreateInstrumentedTest.kt:96`、`:164` 与 `BackupSelectionScaleInstrumentedTest.kt:167` 同形 `assertEquals(SmbkSelectionPlan.rowCountKeys, …keys)`。
  - 以上是"90 键集合一次断言"，**不是** 90 行逐名证据。
- 现树没有 `rowCounts…forEach/println` 之类把键名打到 log 的用例；`SmbkSelectionPlan.kt:656` 的 `rowCounts.keys.sorted().forEach` 属于 digest 构造（主源码），不是设备证据。
- 批次 33 设备日志实读（本轮执行）：
  ```
  $ grep -c "rowCounts"            build/device-track-batch33/step01-backup-final.log → 0
  $ grep -c "tutor_visual_snapshot" build/device-track-batch33/step01-backup-final.log → 0
  $ grep -E "OK \(|Tests run"       build/device-track-batch33/step01-backup-final.log → OK (34 tests)
  ```
- **结论：设备侧 rowCounts 90 键仍未逐名（0 命中）**，与计划 §0 第 6 点一致 → KD-80 的 80-C2 设备半边仍不满足。

### 3.4 补注：套件用例数为静态文件计数（非执行）

本轮 `grep -c "@Test"` 实读（仅源计数，**未上设备**）：`BackupRestore=34`、`BackupSnapshotTrimmer=9`、`BackupVisualReference(Integer C4)=5`、`TutorVisualStructureGates=5`、`TutorVisualCommit=12`、`TutorVisualCommitRepository=12`。与计划 §0 第 5 行「备份 34、裁剪器 9、提交 12、结构门 5、C4 5」的**文件例数**相符；是否在设备上跑绿本批未执行 → UNVERIFIED。
（计划 §0 第 7 行「仓库 11 例 vs 现树 12 例」：现树 `TutorVisualCommitRepositoryInstrumentedTest` @Test=**12**，已核实。）

---

## 四、A-01 / A-02 / A-03 现状

### 4.1 A-01：coordinator 是否有 `VisualExecutionEngine` 注入点

- `core/data/.../visual/DefaultFigureGenerationCoordinator.kt:125-134` 构造器参数：`ledger`、`openBackend: suspend () -> FigureImageBackend?`、`now`、`audit`。**没有任何 `VisualExecutionEngine` 参数**（`grep -n "Engine\|VisualExecution" 该文件` → NONE）。
- `:182-190`：`method != FigureGenerationMethod.IMAGE_MODEL` → 直接返回 `UNAVAILABLE`（"本波次没有可用的 JS 运行库，未执行"）。
- `FigureGenerationCoordinationFactory.create`（`:571-586`）只装配 `RoomFigureDispatchLedger` + `openGuardedFigureImageBackend` + `RoomFigureGenerationAuditRecorder`，无引擎注入。
- 旁证：引擎实现本身已存在——`core/data/.../visual/QuickJsVisualExecutionEngine.kt`（实现 `VisualExecutionEngine`）、`app/.../visualjs/VisualJsClientEngine.kt:100`（经 AIDL 调 `:visual_js`）。即**引擎已落层但未接入 coordinator**。
- **结论：A-01 未开始**（无 engine 注入点、无 attempt 记录接入 JS、无 0 远程派发断言）。

### 4.2 A-02：工具环 method 枚举 / program 独立字段

- 原生 tool schema `core/data/.../model/OpenAiModelProtocol.kt:443-461`：`method.enum` **只含 `IMAGE_MODEL`**（`add(JsonPrimitive("IMAGE_MODEL"))`，`:453`），注释明写"本构建 availableMethods 只有 IMAGE_MODEL…未接通的 JS 不出现"。schema 里**没有** `program` 属性。
- 工具调用构造校验 `core/model/.../TutorToolLoop.kt:344-349`：
  - `:344` `require(figureProgram == null) { "The JS program carrier is unavailable in this build; do not send program" }`
  - `:347` `require(figureMethod == null || figureMethod == FigureGenerationMethod.IMAGE_MODEL)`
- 解析层**已接受** `program` / `method=JS` 的**字段位**并记录：`OpenAiModelResponseParsers.kt:307`（`FIGURE_GENERATION_WIRE_KEYS` 含 `program`）、`:333` `program = optionalString("program")`、`:621-622` `figureMethod`/`figureProgram`。但到 `TutorToolCall` 构造处即被拒。
- **结论：A-02 未开始**——schema `method.enum` 无 JS，`TutorToolCall` 仍拒绝 `program` 和 `method=JS`；"既有负向转正向"未做，负向仍保留。

### 4.3 A-03：`FigureGenerationRequest` 形态 / program 独立字段

- `core/model/.../FigureGeneration.kt:299-363` `FigureGenerationRequest` 字段：`operationId, purpose, target, capabilities, budget, dispatchParent, source, constraints, problemContext, prompt, sourceKind, sourceId, basisFingerprint`。**无 `program` 字段**。
- 对照：`FigureGenerationProposal`（`:372-398`）**已有** `program: String?`（`:382`，"JS 方法载荷；IMAGE_MODEL 方法不得携带"）与 `imageOperation`/`imagePrompt`。即"proposal 有 program 载体，request 没有"。
- 原生 schema 缺 `program`（见 4.2）。
- **结论：A-03 的请求侧 program 独立字段与 schema `method.enum` 加 JS 均未落层。**

### 4.4 能力快照位（广告面）

- 能力快照构造点（全部 `IMAGE_MODEL_ONLY`）：
  - `core/data/.../capture/ConfiguredCleanImageGenerator.kt:95`
  - `core/data/.../model/AttachedImageGenerator.kt:144`
  - `core/data/.../model/TutorFigureGenerator.kt:372`
  - `core/data/.../model/FigurePromptAssembler.kt:102`（`assembleForTutorTool` 默认参数）
- 常量定义 `core/model/.../FigureGeneration.kt:160-167` `IMAGE_MODEL_ONLY`（`availableMethods = setOf(IMAGE_MODEL)…`）。
- 广告/提示词侧 fail-closed：`FigurePromptAssembler.kt:40`、`:108` 均 `require(FigureGenerationMethod.JS !in capabilities.availableMethods)`。
- **能力快照位 = 上述 4 处 + 常量；计划 §2「能力快照 / prompt / 广告文本零改动直到全门绿」的现状是：全部仍只广告 IMAGE_MODEL，JS 未广告。**

---

## 五、KD-81 现状：`export_split_eval.py` 的 sha 与 `-wal` 处理

- 文件：`tools/split_golden/export_split_eval.py`。
- sha 计算函数（`:72-73`）：
  ```python
  def _sha256(path: Path) -> str:
      return hashlib.sha256(path.read_bytes()).hexdigest()
  ```
- DB 身份处（`:221`）：`"db": {"file": db_path.name, "sha256": _sha256(db_path), "bytes": db_path.stat().st_size}`。
- **只对主库文件字节求 sha，无任何 `-wal`/`-shm` 分支**；只读连接 `_connect_read_only`（`:76-79`）用 `mode=ro` 打开。
- `docs/known-defects.md:3047` KD-81 **open**：明写"`source.db.sha256` 只覆盖主库字节、不含 `-wal`/`-shm`：同一主库 + 不同 WAL 会得到不同导出却报同一快照身份"。
- `tools/split_golden/README.md:69-96` 已把该缺口写成复现与"检测（复跑前必跑）"说明，但**导出脚本本身未修**。
- **结论：KD-81 未修**（sha 仍只覆盖主库；`-wal` 处理仅存在于 README 的检测指引，不在代码里）。

---

## 六、R-04 现状：release 变体与 `testProguardFiles`

- `app/build.gradle.kts` **干净**（见 2.4）。
- `buildTypes`（`:57-86`）已有 `benchmark`、`internal` 两个 `create(...)`，以及 `release { ... }`（`:77-85`），三者均 `isMinifyEnabled = true` 且 `proguardFiles(getDefaultProguardFile(...), "proguard-rules.pro")`。
- **`testProguardFiles` 全文 0 命中**（`grep -n "testProguardFiles" app/build.gradle.kts` 无输出）。
- 无 `testOptions` 段（同样 grep 未命中的项之一）。
- **结论：R-04 未开始**——release 变体存在、minify 已开，但 `testProguardFiles` 路线尚未配置；`app/build.gradle.kts` 干净，未处于他线热区冲突状态。

---

## 七、设备在线状态（只读）

```
$ /c/Android/Sdk/platform-tools/adb.exe devices -l
List of devices attached
emulator-5562  device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:18
emulator-5566  device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:20
emulator-5584  device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64xa transport_id:7
```

- 三个模拟器均在线，`getprop ro.build.version.sdk` 均为 **34**。
- 注：`adb` 不在 PATH，需用绝对路径 `/c/Android/Sdk/platform-tools/adb.exe`（`ANDROID_HOME=C:\Android\Sdk`）。本轮只读，未安装/未运行任何测试。

---

## 八、与计划 §0 的不符 / 需注意处

1. **§0 第 3 行「HEAD 待提交」表述不准确**：HEAD 已是落地的提交 `db792fa8` 且与本地 `origin/main` 引用相同；真正"待提交"的是**工作树 689 条改动**（217 改 + 472 未跟踪）。远端是否已收到该提交无法只读验证 → UNVERIFIED。
2. **计划 §2 禁改的每一条热区在开工时都已是脏的**（settings/.gitignore/Root/core-domain/core-ui/core-export/core-markdown/feature 全部有未提交改动或未跟踪新文件）。本批只能"不追加改动"，无法得到"热区干净"的开工基线。
3. 其余 §0 断言与现树一致：DB schema=71（§0/§2）；仓库测试现树 12 例（§0 第 7 行）；KD-80 open 且只剩设备侧 rowCounts 逐名（§0 第 6 行）；R-04 无 `testProguardFiles`（§0 第 9 行）；A-01/A-02/A-03 确未开始（§0 第 9 行）；KD-81 sha 只覆盖主库（§0 第 8 行）。

### 未验证项（本批只读未跑）

- 远端 `origin/main` 实际内容 / 推送状态：UNVERIFIED。
- 上表所有套件的**设备执行**结果：UNVERIFIED（只做了 `@Test` 静态计数与既有日志 grep）。
- 设备侧 `rowCounts` 90 键逐名：**已确认未做**（日志 0 命中）。
