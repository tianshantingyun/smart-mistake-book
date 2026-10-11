# K5 · R1/R2 落地（未知置信度改判显式策略门 + 候选 margin 与校准审计） · 完成记录（2026-10-11）

> 依据：研究文档 `docs/research/2026-10-11-r1-r2-label-accuracy-research.md`（`dda7b83c`；31 条外部来源、
> 证据分级与交叉验证。结论：R2 = 0.78 应**改判为显式策略门**、存量未知应可区分可落理由；R1 = 外部
> **没有**"零人工、零金标、纯系统自纠可靠"的先例，落点定位"**发现 + 降级/告警**"，不做静默自动改写）。
> 裁定：`docs/research/2026-10-09-kf30-semantics-rulings.md`（Q1 判据一行不动、Q2 的 NULL 口径为 interim、
> Q3 add-only 维持）＋ 台账裁定 39（`7809a0c1`：K5 研究 = **任务非门**，触发点 = D-1 过门后 / C2·C13 自动化
> 开工前；schema 65 必须早于阶段 7 迁移链重置）。
> 本计划：动态工作流草稿 `.zcode/workflow-drafts/k5-r1-r2-landing.dwf.ts`（批 1 = K5-A + 两处加固；
> 批 2 = K5-B；批 3 = 综合门 + 收尾；该文件被 `.gitignore` 忽略、未入库）。
> 执行方式：动态工作流两批（各自过门 + 独立复核）→ 综合门 → 收尾（本记录 + 台账折入 + 主线 3C 行回填）。

## 1. 提交

| 批次 | 提交 | 内容 |
|---|---|---|
| 研究 | `dda7b83c` | R1/R2 深度研究落档（外部高质资料 + 交叉验证 + 证据分级） |
| 裁定 | `7809a0c1` | 台账裁定 39：K5 = 任务非门（触发点与任务书钉死） |
| 批 1 | `7c5710b8` | K5-A：未知存量置信度改判为**显式策略门**（可区分 + 可审计理由）+ 两处加固（6 文件） |
| 批 2 | `64878741` | K5-B：分类**候选集落库（schema 65）** + margin「存疑」信号（只派生不改写）+ 校准审计件（代理金标标注）（18 文件） |
| 收尾 | 本记录随收尾提交落档 | 完成记录 + 台账折入（内核线 3C 前半 / 3C 后半 / S18 尾 / D-0 / K1 / K2 / K3 七条 + K5 + 阶段 5 挂起）+ 主线 3C 行回填 |

## 2. 交付（每项指认它消灭的失败）

| 交付 | 消灭的失败 |
|---|---|
| 命名策略常量 `UnknownIncumbentComparison.GATE` + `UNKNOWN_INCUMBENT_COMPARISON`（`RoomMistakeOrganizationRepository.kt:110`、`:120`，KDoc 声明"**策略，不是估计**"） | 门值 0.78 被读成"存量当时的估计值"（把未知伪造成精确值）——R2 结论：缺失 ≠ 精确值，等值/替换规则须显式、确定、可披露 |
| 合并真实执行体 `mergeAutomaticClassificationsWithReasons`（`:1035`，`mergeAutomaticClassifications` 退化为同判据投影、签名不变）+ `ClassificationMergeOutcome`（`:1137`）+ 理由 `incumbent_known` / `incumbent_unknown`（`IncumbentComparisonReason`，`:1150`）+ 显式分支 `incumbentComparisonBasis`（`:1113`） | "按策略替换"与"按分数替换"在证据上不可分开、不可审计（K3 遗留的可区分缺口）；理由与裁决同点产生（不是事后重算）⇒ 证据不可能与判定漂移 |
| **判据一行未改**（严格更高者胜、等值保留存量）；未知不冻结、不回填（`unknownAcceptedConfidenceKeys` 语义不变） | 借"改判"顺手改判据造成语义漂移；把门值写回列（重犯 KF-30 (a)(b) 的伪造根因） |
| 加固一：50k 结构/EQP 断言——count 档四条查询（count + 三分面）四合取判据 + 两条固定红样例（`LibraryFtsCountPathScale50kInstrumentedTest.kt:133`）；ranking 档用生产 live SQL EXPLAIN（`LibraryFtsRankingPathScale50kInstrumentedTest.kt:127`）；4 份 SQL 副本由 JVM 守卫同时钉住 10k/50k（`LibraryFtsCountPathQueryCopyContractTest.kt:58`） | 50k 档只证计时、驱动结构随规模退化不可见；SQL 副本与 DAO 漂移导致"绿得没有意义"。**不新增任何计时门** |
| 加固二：安装落点判定缓存必须在下次召回前失效且重核验（计数缝可观察，`KnowledgeSearchIndexCompletenessInstrumentedTest.kt:189`）+ canary 把失效置 no-op 后陈旧结论保留、下次召回零判定 SQL（`:237`） | 安装落点失效判断无敏感面（K1 §11#7 登记）；canary 使主用例的绿色成为"来自失效接线"的可复跑证据 |
| schema 65：`problem_classification_candidate` 表（`ProblemEntities.kt:626`；3 索引）+ 迁移 64→65（纯加表、旧库零回填、旧行不动；`ProblemClassificationCandidateMigration_64_65.kt`）+ `STUDY_DATABASE_VERSION = 65`（`StudyDatabase.kt:128`）+ 导出 `65.json` + 契约/校验/矩阵/升级演练测试 | 模型给过什么、给了多少分、哪些没过接受门或被预算挤掉——全部不可考；候选竞争关系（cleanlab `normalized_margin` 的对手集）与接受门的选择偏差没有信号可用（R1 机制 ② 的前置） |
| 被拒候选落库：`ClassificationCandidateSource` 必填参数（`:964`），`accepted = 键 ∈ 本轮确认集 ∧ ∈ 接受键`；自动 / 用户确认 / 离线纠正（`NONE`）三条路径各自显式；`candidateId` 按轮内容寻址（同轮重放幂等） | 漏传 `acceptedKeys` 会把全部候选静默标"被拒"、漏传 `candidates` 会让本项从源头消失（K3"忘传即伪造"教训的同型）；"自动没过门"与"用户没勾"两种被拒不可区分 |
| margin「存疑」：`normalizedMargin` 纯函数（`ClassificationCandidateMargin.kt:59`；无对手 ⇒ `null` = 不可判，一等输出）+ `acceptedLabelMarginSignals`（`:78`；负 margin 也落入存疑）+ 阈值常量（`:20`）与浮点容差（`:27`） | 几乎并列（含并列）的已接受标签无人可见。**只派生、不改写**——外部两条反向证据（自纠会自我强化错误；无外部反馈的自纠可使表现变差）否掉静默自纠 |
| 校准审计件 `ClassificationCalibrationAuditTest`（JVM、真 SQLite；按置信度分桶统计代理正确率 + 选择偏差 + 存疑清单；`SMARTMISTAKE_AUDIT_DATABASE` / 系统属性注入真库；无库/无候选如实报【无样本】、不给比例，两条用例钉住） | "自报各档置信度的实测正确率"没有可运行载体；把"未纠正"读成"正确"的 add-only 幻觉（口径与 KDoc 显式标注：**代理金标 = 用户纠正、不是真金标**；**未被人纠正 ≠ 正确**；样本从 schema 65 起积累；分数被门截断） |

零算法 bump（批 2 明细：未触碰投影/归因/版本串；重放指纹三用例实测绿；未改 `mergeAutomaticClassifications` 判据——批 1 的 reason 结构原样复用）。
批 2 另声明：`classificationCandidates` **不进**命令指纹（同 `acceptedConfidence` 的处理：是"本轮考虑过什么"的记录，不是"写了什么"的载荷；重复确认/幂等重放不因此变成载荷冲突）。

## 3. 门证据

| 门 | 结果 |
|---|---|
| 批 1 门（冷启机） | 全量 JVM `--rerun-tasks` + 仪器化四类（`core:data:…MistakeOrganizationRepositoryInstrumentedTest`；`core:database:…KnowledgeSearchIndexCompletenessInstrumentedTest`、`…LibraryFtsCountPathScale50kInstrumentedTest`、`…LibraryFtsRankingPathScale50kInstrumentedTest`）全绿——据批 1 提交消息（`7c5710b8`） |
| 批 2 门 | 全量 JVM `--rerun-tasks` + 仪器化三类（`core:data:…MistakeOrganizationRepositoryInstrumentedTest`；`core:database:…FullMigrationMatrixInstrumentedTest`、`…LegacyProjectionUpgradeDrillInstrumentedTest`）全绿——据批 2 提交消息（`64878741`） |
| 综合门 · 全量 JVM | **exit=0** |
| 综合门 · DB 全套 | **exit=1**：`tests=246 failures=1 errors=0 skipped=0`；唯一失败 = `catalogFacetsPlanAndLatency`。原文：目录 facet SECTION **p95=283ms 超过 backstop 200ms**；samples=[233, 240, 246, 225, 225, 244, 245, 238, 194, 241, 266, 256, 260, 246, 258, 264, 286, 259, 273, 283, 264, 283, 260, 252]。按既定口径（**唯一允许按已登记环境红接受者 = `catalogFacetsPlanAndLatency`**）接受。**未改任何断言/预算/夹具**——本地裸预算是否调整属规则变更、留给用户裁定 |
| 综合门 · app 全套 | **exit=0**（失败时已重试） |
| 综合门 · R8 冒烟 | 全链通过（本会话实测，2026-10-11）——构建、装机、启动、进程、logcat、截图、签名见下 |

**R8 冒烟明细（本会话实测，2026-10-11）**：

1. **构建**：`RELEASE_STORE_FILE='C:/Users/听云/.android/debug.keystore' RELEASE_KEY_ALIAS=androiddebugkey
   RELEASE_KEY_PASSWORD=android RELEASE_STORE_PASSWORD=android
   JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot' MSYS_NO_PATHCONV=1
   cmd.exe /c "gradlew.bat :app:assembleLocalFirstRelease"` → **EXIT=0**；日志含
   `> Task :app:minifyLocalFirstReleaseWithR8`（实跑，非 UP-TO-DATE）与
   `BUILD SUCCESSFUL in 3m 37s`（376 tasks: 42 executed）。日志：`build/r8-smoke/build-2026-10-11.log`
   （记录撰写者复读核对：R8 任务行 `:6807`、`BUILD SUCCESSFUL` `:6828-6829`）。
   环境注记（如实）：前两次调用失败均系调用侧环境而非工程——① 首次用 `cmd //c` 命中 npm shim
   `C:\Users\听云\AppData\Roaming\npm\cmd`（报 `too many arguments`），改直呼 `cmd.exe`；
   ② `JAVA_HOME` 为 MSYS 形式 `/c/...` 被 `cmd.exe` 判 invalid，改 Windows 形式后成功。
2. **APK**：`app/build/outputs/apk/localFirst/release/app-localFirst-release.apk`（170,737,499 B，mtime 12:05）；
   sha256 = `736470b38b5fbfd0b588a186ffe42f7beb12711974c6a0fbb2cac13a86befd5c`
   （`build/r8-smoke/apk-sha256-2026-10-11.txt`，记录撰写者复读一致；`stat` 复核大小一致）。
3. **装机 → 启动 → 进程**：`adb install -r` → `Performing Streamed Install / Success`；
   `adb shell monkey -p com.tingyun.smartmistakebook.localfirst -c android.intent.category.LAUNCHER 1`
   → `Events injected: 1`；`adb shell pidof` → **8151**（稍后复检仍 8151）；
   `topResumedActivity=com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.MainActivity`；
   机上 `base.apk` 大小 170737499 == 本地构建件（toybox stat）；
   `versionName=0.2.0-localfirst` / `versionCode=2` / `flags=[HAS_CODE ALLOW_CLEAR_USER_DATA]`（无 DEBUGGABLE，证为 release 变体）。
4. **logcat 四类标记**：`adb logcat -d --pid=8151 > build/r8-smoke/logcat-pid-8151-2026-10-11.txt`（101 行，
   含应用自身日志如 nativeloader base.apk / GC / GraphicsEnvironment）；逐项 grep -c：**FATAL=0、
   ClassNotFound=0、NoSuchMethod=0、NoClassDefFound=0**，合并 grep -E 命中 0 行；附加 `AndroidRuntime=0`、
   `beginning of crash=0`（记录撰写者复跑同一 grep 核对，均为 0）。文件另含 1 条
   `E book.localfirst: Not starting debugger since process cannot load the jdwp agent.`——非四类标记、
   非调试器报错，属不可调试（release）应用启动常态行，**登记备查**。
5. **截图**：`adb exec-out screencap -p > build/r8-smoke/localfirst-release-monkey-2026-10-11.png`（PNG 1080x2400）；
   原图内容为应用首页：标题「晚上好，同学」、今日复习区「暂无学习记录」「暂无可复习题」、底部导航
   复习 / 智能体 / 错题本 / 我的——与历次 R8 冒烟截图口径一致
   （见 `docs/research/2026-10-03-stage4b-completion-record.md:81`、`docs/research/2026-10-06-s18-fts-completion-record.md:53`）。
6. **签名性质（登记用证据）**：`apksigner verify --print-certs` 显示 V2 Signer
   `DN=C=US, O=Android, CN=Android Debug`，SHA-256=`26466dbfb132af681aa0d7a7bea624d1456228dfd1086f8d4fc2dcf2e38dbba1`；
   `keytool -list -v -keystore C:\Users\听云\.android\debug.keystore -alias androiddebugkey` 得
   SHA256=`26:46:6D:BF:B1:32:AF:68:1A:A0:D7:A7:BE:A6:24:D1:45:62:28:DF:D1:08:6F:8D:4F:C2:DC:F2:E3:8D:BB:A1`
   （同一证书）——即 **release 变体由 Android debug key 签名**（非发布签名，登记事实）。
7. **设备**：`emulator-5554`，model `sdk_gphone64_x86_64`，Android 14 / API 34，x86_64，build tags `dev-keys`。

## 4. 独立复核（两批，只读）

- 批 1：**approve-with-notes**
- 批 2：**approve-with-notes**

本次运行结果只提供上述 verdict 值；两批复核注记的逐条原文、以及是否含 P0/P1 修正项，
未随结果集提供 ⇒ **UNVERIFIED**（登记）。两批均非 `block`。

## 5. 遗留与登记

1. **K5-C（多轮重跑一致性 + 非对称降级）待排**：研究 §4 定性为最重片；按裁定 39，触发点 =
   D-1 过门后 / C2·C13 自动化开工前——不阻塞当前工作。研究完成前 C2/C13 自动化不得开工。
2. **margin 阈值待定/待校准**：`SUSPECT_NORMALIZED_MARGIN_THRESHOLD = 0.02`
   （`ClassificationCandidateMargin.kt:20`）是**未验证的策略起点**（真实样本 0；cleanlab 官方不给固定阈值）；
   调整只改这一个常量，派生结果与审计同源。浮点容差 `1e-9`（`:27`）只吃表示噪声，不是第二个旋钮。
3. **校准审计暂无真实样本**：样本从 schema 65 起积累；真库路径经 `SMARTMISTAKE_AUDIT_DATABASE`
   （或系统属性 `smartmistake.audit.database`）注入；无样本如实报【无样本】、不给比例（已钉住）。
4. **margin 派生件当前无生产消费点**（只派生、只供审计）；"存疑"如何呈现/是否降级属后续出口机制
   取舍（研究 §4 问题 ①），依赖校准结论 → 归 K5-C 前置。
5. **加固二的已声明边界（照录，不夸大）**：安装期 `rebuildSubject` 对同一 subject 也失效
   （进入/退出各一次），故**单独摘除** `RoomKnowledgeContentReconciler.kt:117-119` 那行 invalidate
   不会改变任何现有行为、两条用例都不会红——该行是防"未来不经过 `rebuildSubject` 的索引写入"的
   纵深防御；要做到"单独摘除也红"需生产侧新增隔离测试缝，超出本批生产改动范围（已写进用例 KDoc）。
6. **零算法 bump 与指纹口径**：批 2 未触碰投影/归因/版本串；`classificationCandidates` 不进命令指纹；
   批 2 未改 `mergeAutomaticClassifications` 判据（批 1 结构原样复用）。
7. **裁定记录/台账状态同步（登记）**：`2026-10-09-kf30-semantics-rulings.md` 仍标 Q2 = interim、
   "正解挂 R2"、"台账折入仍待他线"；K5-A 已把 R2 结论落地为显式策略门（比较行为不变、判据未改），
   但该文件的 interim 标注与台账折入句**本轮未动**（本收尾批只动三份 docs）→ 待同步项，登记。
8. **他线文件未碰**：`tools/kb_*`、知识包、他线在飞文件一律未改（批 2 边界声明；收尾批只动
   完成记录 / 台账 / 主线三份）。
9. **环境红登记**：DB 唯一红 = `catalogFacetsPlanAndLatency`（已登记环境红第 6 次同形：
   S18 / D-0 / K1 / K2 / K3 / K5）；`未改任何断言/预算/夹具，本地裸预算是否调整属规则变更、留给用户裁定`。
10. **schema 65 与阶段 7 次序**：schema 65 先于阶段 7 迁移链重置落库（裁定 39 问题 ③ 的次序要求
    在构造上满足）。
11. **候选表只增**：`RoomProblemOrganizationStore` 校验 `classificationCandidates.size <= 32`
    （`RoomProblemOrganizationStore.kt:423`），本表"只写不改"（研究结论的落地）；清理/归档策略未定，
    长期体积登记待后续。

## 6. 剩余风险

1. **阈值 0.02 未经数据校准**：可能过松/过严；可调但调整前"存疑"名单的语义只有策略保证、无实测校准。
2. **代理金标偏差**：用户纠正稀疏且 add-only 下"未纠正"包含"从未复核"——校准审计的比例**不可读成
   正确率真值**（口径已显式标注，但消费方若误读仍会犯错）。
3. **K5-C 缺席**：无 K5-A/B 之外的自纠信号，错标签仍只能靠"模型重提同名标签刷新真值"这一既有路径；
   本批未引入任何自动改写（有意为之）。
4. **性能/规模口径**：50k 结构断言与 R8 冒烟均在模拟器口径下（性能结论的宿主限制沿袭）。
5. **R8 release 变体为 debug key 签名**：仅冒烟证明用，不代表发布签名链就绪。

## 7. UNVERIFIED 清单（照录）

1. 两批复核注记逐条原文与是否含 P0/P1 修正项——结果集未提供。
2. margin 阈值 0.02 的实测校准——无样本，未验证。
3. 校准审计在真实数据上的分桶比例——无样本，未验证（明确的"无样本"口径已钉住）。
4. "存疑"标签的未来出口（降级标记/不入库/告警的组合）——未裁定、未实施。
5. K5-C 未实施（待排）。
