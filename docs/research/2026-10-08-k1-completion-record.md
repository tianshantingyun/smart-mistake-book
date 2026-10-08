# 3D · K1「内核侧余件与规模收尾」· 完成记录（2026-10-08/09）

> 依据：实施计划 `docs/research/2026-10-08-k1-plan.md`（`7ea8ba7e`，2026-10-08 批准）；
> D-0 完成记录 `docs/research/2026-10-07-d0-binding-verdict-completion-record.md`（内核侧已落，阶段 5 仍挂起）；
> 阶段 5 门核验 `docs/research/2026-10-04-stage5-gate-check.md`；`docs/kb-outstanding-research-2026-10-02.md`
> §④-6（`:416-423`）与分叉裁决 F6（`:1193-1196`）；S18 FTS 完成记录
> `docs/research/2026-10-06-s18-fts-completion-record.md` §5。
>
> 执行方式：两批**严格串行**——每批「实施 → 正式门（显式分支 exit code）→ 独立只读复核 → 显式清单提交」；
> 批 1 门在冷启机跑，性能/仪器化门一律遵守「冷启 `-wipe-data`」纪律（D-0 实证：劣化机打红 perf 门/UI 用例，
> `adb reboot` 不恢复）。综合门（全量 JVM + DB 全套 + app 全套 + R8 冒烟）与三处回填由协调方收尾。
>
> 交付边界：**内核侧余件闭合**（④-6 固定开销、50k FTS 规模、KF-32 空绑定重放、D-5 装机快路径钉子）；
> **不含** bm25/FTS5 换库（F6 已裁「不换」）、KB 线文件面（`tools/kb_*`、`docs/kb-*` 等一律未碰）、
> 台账折入（他线在飞文件，见 §8）。

## 1. 提交

| 批次 | 提交 | 内容 |
|---|---|---|
| 计划 | `7ea8ba7e` | K1 实施计划（④-6 + 50k + KF-32 空绑定重放 + D-5 钉子 + bm25→F6 回填） |
| 批 1 | `0b1b5a7f` | ④-6：`KnowledgeSearchIndexCompleteness`（判定缓存 + 计数缝 + 代际防并发）+ 四处写侧失效（内容安装 / 整科重建 / 只补缺分支 / 整理确认）；50k FTS 规模补测（count/ranking 两个记录类 + 夹具参数化）；定向仪器化 12 类（含新增 4 用例类与两个 50k 记录类）；零 schema |
| 批 2 | `65f77706` | KF-32 空绑定重放改挂 pseudo 兜底（`StudyProjectionDrainer` 物化伪绑定事实 + `LearningProjector` 兜底收敛 + 注释改准）；版本 `PROJECTOR` v12→v13、`ATTRIBUTION` v4→v5（复合串 `learning-core-v13`，台账 §3.19）；D-5 装机快路径判定抽 `isInstallUpToDate` 纯函数 + JVM 两态用例（纯抽取、行为零变化）；零 schema |
| 收尾 | （随本记录提交） | 本完成记录 + 主线 3C 行回填 + S18 记录 §5 两条回填 + 3C 批 1 记录 KF-32 登记回填 |

## 2. 交付内容（每项指认它消灭的失败）

| 交付 | 消灭的具体失败 |
|---|---|
| ④-6 判定缓存（每进程每科一次） | `ensureKnowledgeSearchIndex` 在锚点相等时**每次召回**仍跑两条 COUNT（S19 桌面探针 ≈12.6ms/次，**数字本身 UNVERIFIED**、探针未入库）——每次检索/讲题白付的固定成本；缓存命中时召回路径**零 DAO 调用**（连版本锚点都不读） |
| 四处写侧失效 + 代际防并发 | 漏接线 → 同进程「安装/确认后立刻召回」漏掉新节点（正确性风险）；在途验证把写路径失效前的旧读落成「完整」→ 假完整永久漏召回 |
| 50k FTS 规模补测 | 「`MATERIALIZE` 一次成本随目录行数线性」只有推断、没有 5 万行实测（S18 尾结论适用范围无界）；补测后如实记录 50k 三项计时与建库成本（记录项不设门，不把一次模拟器读数当墙） |
| KF-32 空绑定重放改挂 pseudo | 一道「诚实未分类」（空绑定）的题，历史证据在重放后仍挂旧知识点——复习按知识点扫描时把本不属于它的证据算进该知识点的掌握度（KF-32 此前只修了非空改绑路径） |
| `isInstallUpToDate` 纯函数 + 两态用例 | D-5 落账最易踩的静默失效（只写侧车不 bump `contentVersion` → 三元组静默不到设备）此前只有文档警示、无回归钉子 |
| `PracticeUnitBindingAttribution` 注释改准（空列表语义） | 注释称「没有任何可用绑定时返回 null」与实际（返回空列表、调用方各自兜底）不符——旧注释正是 KF-32 空绑定缺口留在代码里的登记面 |

## 3. 逐项落证

### 3.1 ④-6 计数断言与失效面

**实现**（`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeSearchIndexCompleteness.kt`，99 行新增）：

- 判定口径与自愈门逐条一致：`已审校 = 0 或 已索引 ≥ 已审校` 才算「已核验完整」；**只有跑完两条 COUNT 且结论为完整**的那次验证可以落缓存。作用域按数据库实例隔离（生产装配=进程内每科一次；避免同进程多库——备份校验库/测试库——互相同意）。
- **断言手段**：计划优先探 `setQueryCallback`；KDoc 记「room3 3.0.0 已核实**没有**该 API（`javap androidx.room3.RoomDatabase$Builder` 只有 `setQueryCoroutineContext`）」→ 按计划回退到 internal 计数缝 `issuedVerificationQueries`（标签=DAO 方法名，仅测试可见）。**计数断言（不是计时）**由此成立。
- **失效面四处**（漏任一处 = 召回漏新装节点）：① `RoomKnowledgeContentReconciler.applyKnowledgeContentUpdate`（内容安装落点）；② `KnowledgeSearchIndexBuilder.rebuildSubject`（整科重建，进入与退出都失效）；③ `RoomKnowledgeBaseStore.ensureKnowledgeSearchIndex` 只补缺分支自身补完后；④ `RoomProblemOrganizationStore.confirm` 整理确认（用户纠正落 USER_CONFIRMED 节点时**不建特征行**、靠读时自愈补——`RoomProblemOrganizationStore.kt` 内 `.forEach(searchIndexCompleteness::invalidate)`）。**只失效不回填**：写路径只调 `invalidate`，回填只发生在读路径跑完两条 COUNT 之后。
- **并发防护**：读计数**之前**观察失效代号，落缓存走 `markVerifiedCompleteIfUnchanged(subject, observedGeneration)`——验证期间发生写路径失效则丢弃结论。

**测试证据（落盘 XML / logcat）**：

| 层 | 用例（节选） | 结果 |
|---|---|---|
| 仪器化（4 用例，生产写/读路径端到端） | `firstColdRecallRunsTheGateAndLaterRecallsIssueNoVerificationQueries`（正控制：冷召回**确实**下发 `readVersion` + 两条 COUNT，防断言空转；随后多次召回**零判定 SQL**）；`contentInstallKeepsNewNodesImmediatelyRecallable`；`userConfirmedKnowledgeNodeIsImmediatelyRecallableAfterConfirmation`（第四失效点）；`missingInvalidationWireCanaryHidesTheNewlyConfirmedNode`（canary：用测试开关摘掉失效接线 → 同一场景新节点**不可见**，证明主用例真的对失效敏感、非恒真） | 综合门 DB 套件内 4/4 绿（time=0.104 / 0.105 / 0.13 / 0.129s） |
| JVM（缓存单元语义） | 8 用例，含 `markIsRejectedWhenInvalidationHappenedDuringVerification`、`marksObservedBeforeAnInvalidationNeverSurviveTheInvalidation`、`disabledInvalidationCanaryLeavesTheStaleClaimInPlace`、`verificationQuerySeamRecordsDaoMethodLabels` | `KnowledgeSearchIndexCompletenessTest` **8/0/0/0**（`core/database/build/test-results/testDebugUnitTest/`） |

未改召回 SQL、未动排序语义、未动锚点协议（S21 安装期预热成果保持）；**零 schema**。

### 3.2 50k 三项计时与建库成本

**实现**：两个新记录类 `LibraryFtsCountPathScale50kInstrumentedTest.kt` / `LibraryFtsRankingPathScale50kInstrumentedTest.kt`（各 248 行）+ `LibraryCatalogScaleFixture.kt` 参数化（20 行改动）。夹具 `seedLibraryCatalogScale(50_000, boundedTokenEvery = 500)` ⇒「独特检索」恰好 100 命中、「万级探针」1 万命中、「分页题目」5 万命中——**三项命中数与 FTS MATCH 探针逐条断言**（防夹具退化）。**门口径**：100 命中仍是门（P95 ≤ 500ms × `ciSlowRunner`，与 `PerformanceGateTest` 同值同口径）；1 万/5 万命中与排序各档**只记录不设门**；既有 10k 门类一字不改。

**读数（两次独立运行；均为 2 核模拟器 debug 构建口径）**：

| 项 | 批 1 门（提交信息 `0b1b5a7f` 所记） | 综合门重跑（本会话实读 logcat 原始行） |
|---|---|---|
| count 建库成本（50k 行） | seed=62.4s + bootstrap=23.7s | seed=51667ms + marker=139ms + bootstrap=20898ms |
| count 100 命中（**门**） | P95=242ms | P95=227ms（samples=[218…220]，30 条；budget=500ms） |
| count 1 万命中（记录） | — | P95=226ms（samples=[223, 225, 226, 224, 226]） |
| count 5 万命中（记录） | — | P95=236ms（samples=[236, 234, 234, 234, 241]） |
| ranking 建库成本（50k 行） | seed=54.9s + bootstrap=21.9s | seed=52060ms + markers=152ms + bootstrap=20676ms |
| ranking 100 命中（**门**） | P95=301ms | P95=275ms（budget=500ms） |
| ranking 1k 命中（**门**） | P95=304ms | P95=293ms（budget=500ms） |
| ranking 12 命中（记录） | — | P95=285ms |
| ranking 1 万命中（记录） | — | P95=344ms |
| ranking 5 万宽命中（记录） | P95=575ms | P95=553ms（samples=[546, 553, 553, 549, 546]） |

综合门 logcat 原始行（`core/database/build/outputs/androidTest-results/connected/debug/test_device(AVD) - 14/logcat-*Scale50k*.txt`，设备时间 10-08 22:37/22:39）：

```
K1 50k FTS count benchmark: match=100hits entries=50000 samples=[218, …] p95=227ms budget=500ms
K1 50k FTS count fixture cost: entries=50000 seed=51667ms marker=139ms bootstrap=20898ms
K1 50k FTS ranking tier=100 samples=[277, …] p95=275ms budget=500ms
K1 50k FTS ranking tier=50k-wide samples=[546, 553, 553, 549, 546] p95=553ms (recorded, not gated)
K1 50k FTS ranking fixture cost: entries=50000 seed=52060ms markers=152ms bootstrap=20676ms
```

**结论（如实）**：「`MATERIALIZE` 成本随目录行数线性」的推断在 50k 实测下未观察到超线性恶化——5 万命中 count P95=236ms 与 100 命中同量级（227ms），主成本在夹具建库（≈51.7s+20.9s）而非单次查询；排序 5 万宽命中 553ms 为**记录项**，无需求侧预算，不当墙。

### 3.3 KF-32 空绑定重放：新旧两侧断言（批 2）

**实现**（与写路径逐条同规则，不新增第二套创建路径）：

- 重放装配侧 `StudyProjectionDrainer.readCurrentPracticeUnitBindings`：对「当前绑定为空」的题复用既有 `ensurePseudoKnowledgeBinding` 物化 `pseudo:<科目>` **事实**，再交给投影器用 `derivePracticeUnitBindingAttributions` 现场派生（taxonomy 同常量 `PSEUDO_ATTRIBUTION_TAXONOMY_VERSION`、绑定 revision 取该题当前 revision、科目取 `problem.subject`、时间取 revision 创建时刻——确定性 ⇒ 重放幂等）。
- 物化**只对 attempt / 揭示两个归属消费点**触发：曝光-only 事件不读归属，不为它写行（`attributionConsumers` 集合把 `TutorAnswerExposureOutcome` 排除）。
- `LearningProjector.attributionsForAttempt` 的 `ifEmpty` 收敛为「绑定事实全被过滤」异常兜底；`PracticeUnitBindingAttribution.kt` 注释改为「没有任何可用绑定时返回空列表」。

**新旧两侧断言**（JVM `BindingChangedReplayDrainerTest` **5/0/0/0**，`core/data/build/test-results/testDebugUnitTest/`）：

| 侧 | 用例 | 断言要点 |
|---|---|---|
| 旧（夹具前提，正控制） | `empty bindings replay …` 前置段 | 重放前旧投影的证据**确实**挂在旧知识点上（`keys == [kc-old]`）——证明本批要修的状态真实存在 |
| 新 | 同上主断言 | 重放后 `knowledgeMasteryStates.keys == [pseudo:MATH]`（**旧节点归零**）；`pseudoBindingRequests.single()` 逐字段：taxonomy=同一常量、`problemRevisionId="revision-1"`、`subject="math"`、时间=revision 创建时刻；伪桶 observation `bindingId="pseudo-binding:$UNIT_ID:revision-1:pseudo-evidence-v1:pseudo:MATH"`、`evidenceWeight=1.0`（与写路径同规则，**证据不丢**） |
| 归档 | 同上尾段 | 被替换的旧投影**整份先归档**（`archivedProjectionSnapshots` 解出的 keys={kc-old}）——旧节点证据不是被删掉，仍可回退查看；当前绑定表按题读一次（整本重放共用） |
| 幂等（两态） | `replaying an empty-binding unit again is idempotent across both database states` | 同一账本在「伪绑定已可见」与「伪绑定不在当前集合」两种库状态下重放，掌握度/题卡逐位相同、bindingId 不变 |
| 负向 | `an exposure-only unit without bindings does not materialize the pseudo bucket` | 曝光-only 空绑定题**不**物化伪桶（且版本不匹配确实触发了重放——先归档，非空转） |
| 既有主路径（回归） | `drainer replays after a binding change and lands historical evidence on the current binding`；`a drain without a binding change does not replay and does not read the binding table` | 非空改绑行为不变；无改绑不重放、不读绑定表 |

**红样例（先红后绿实证）**：提交信息 `65f77706` 记——两个新增 KF-32 用例在**临时去掉伪绑定分支**时实测变红（`expected:<[pseudo:MATH]> but was:<[kc-old]>`），恢复后绿；非恒真。

**仪器化（批 2 门 :core:data 4 类）**：`BindingChangedReplayDrillInstrumentedTest` 3、`StudySubmissionWithoutFixtureInstrumentedTest` 2、`BundledKnowledgeBaseInstallerInstrumentedTest` 1、`BundledContentReconciliationInstrumentedTest` 1 = 落盘 XML **7/0/0/0**（`time=281.078s`）。批 2 门另跑 `:core:database` 的 `ProjectionRollbackDrillInstrumentedTest`、`MasterySchedulingMigrationInstrumentedTest`（提交信息记为全绿；其落盘 XML 随后被综合门 DB 全套覆盖，见 §5 注）。

### 3.4 D-5 纯函数两态用例

- `BundledKnowledgeBaseInstaller.isInstallUpToDate(manifest, recorded) = manifest != null && recorded != null && recorded.contentVersion == manifest.contentVersion`——`install()` 的「戳一致即跳过」判定**纯抽取**，调用点改为读同函数；无 manifest（2020 样例包这类无戳包）或没有进度行 → 走全量路径。判定口径与抽取前**逐字一致**、零行为变化（纯抽取）。
- JVM 两态用例 `install fast path predicate only skips when the recorded stamp matches the manifest`（`BundledKnowledgeBaseInstallerTest` **6/0/0/0**）：戳一致 → true（快路径）；戳不一致（只写侧车不 bump）→ **false（必须走全量，不得静默跳过）**；无进度行（首装）→ false；无 manifest → false。非恒真。
- **未做（按计划登记）**：仪器化「只写侧车不 bump → 三元组不到设备」——`install()` 无 manifest 注入口（lazy 全局 `updateManifest`），登记为遗留（见 §9.8）。

### 3.5 P95 两套公式并存（2c：记录互引，不改公式）

新门 `((n-1)*95)/100`（略严）vs `PerformanceGateTest` 的 `(n*0.95).toInt()`——两套口径并存，本批**未动任何公式**（改公式会动 SECTION facet 边际门松紧，需另裁）。互引：S18 记录 §5.5 与本记录本条；风险见 §10.3。

## 4. 批级门与综合门数字

| 门 | 结果 |
|---|---|
| 批 1 门：全量 JVM（`--rerun-tasks`）+ `core:database` 11 类 + `core:data` 1 类定向仪器化（**冷启机**） | 仪器化 XML 根 `tests=53 failures=1 errors=0 skipped=0 time=711.7s`——**1 红 = 已登记环境红**（§5），其余 52 绿（含新增 4 用例类与两个 50k 记录类） |
| 批 2 门：全量 JVM（`--rerun-tasks`）+ `core:data`/`core:database` 6 类定向仪器化 | 落盘 `:core:data` XML `7/0/0/0`（`time=281.078s`）；提交信息记六类全绿 |
| 综合门：全量 JVM | **exit=0**；落盘汇总（本会话复核：2026-10-09 06:23–06:26 修改的 344 个结果 XML 求和）= `2420 / 0 / 0 / 0`（tests / failures / errors / skipped） |
| 综合门：DB 全套 | **exit=0**；落盘 XML `238/0/0/0`，`time=924.045s`（`core/database/build/outputs/androidTest-results/connected/debug/TEST-test_device(AVD) - 14-_core_database-.xml`；设备时间戳 `2026-10-08T22:41:47Z` = 本地 2026-10-09 06:41）。同批内 `catalogFacetsPlanAndLatency` 的 SECTION facet **p95=191ms**（budget=200ms，samples 179–192）——环境恢复即绿 |
| 综合门：app 全套 | **exit=0**；落盘 XML `53/0/0/0`，`time=103.557s`（`app/build/outputs/androidTest-results/connected/debug/flavors/localFirst/`） |
| 综合门：R8 冒烟（协调方，冷启机） | 构建 `:app:assembleLocalFirstRelease`（带 RELEASE_* 签名环境变量）→ `BUILD SUCCESSFUL in 4m 50s`（`.r8_smoke/build.log:6786`；`> Task :app:validateReleaseSigning` 已执行，`:39`；全日志 `BUILD FAILED` / `FAILURE:` / `error:` / `Execution failed` 零行——本会话对 build.log 复核 `grep -cE` 得 0）→ APK `170,568,274` 字节、sha256=`6525d444e50e2e8df08ab75448d88e5112fbcf1ed3a09d0c29041add40f036c6`（本会话 `certutil -hashfile` 实测与落盘文件一致）、`aapt2 dump badging` 确认 `package=com.tingyun.smartmistakebook.localfirst` + launchable `MainActivity`、`apksigner verify` 确认 debug 证书签名 → 装机 ✓ → monkey 启动 ✓ → PID **7968** 三次检查不变（无崩溃重启）→ `dumpsys` 交叉核对 `topResumedActivity=…MainActivity` → `logcat --pid=7968` 124 行内 `FATAL|ClassNotFound|NoSuchMethod|NoClassDefFound` **命中 0**（`.r8_smoke/hits_final.txt` 0 字节）、`logcat -b crash` 全设备 0 行 → 截图（`.r8_smoke/r8_smoke_screenshot.png`）为真实渲染首页，非崩溃弹窗（**本会话打开图片核对**：`晚上好，同学` / `10月8日·星期四` / `今日复习` + `暂无学习记录` / `暂无待复习题` 按钮 / 底栏 `复习·智能体·错题本·我的`） |

> 注（口径）：①综合门的 JVM 汇总由本会话在落盘目录求和（`find … -newermt "2026-10-09 06:00"`，344 个 XML，mtime 全部落在 06:23–06:26 单轮窗口）——与 D-0 记录 2414 的差值来自 K1 新增用例；②DB/app 门「失败时已重试」的**中间失败明细未在本会话取证 → UNVERIFIED**，本记录只认最终落盘结果与门指令的 exit=0；③R8 冒烟环境为 Git Bash（非 cmd.exe）+ 显式 adb 路径（`ANDROID_HOME=C:\Android\Sdk`），目标 `emulator-5554` / API 34 / x86_64——非真机、非 release 签名，不当发布背书。

## 5. 已登记环境红：批 1 · SECTION facet（逐条原始数字与出处）

- **失败用例**：`core:database:connectedDebugAndroidTest` → `LibraryCatalogScalePerformanceInstrumentedTest#catalogFacetsPlanAndLatency`（结构/EQP 断言全绿，**只**在计时 backstop 红）。
- **原始数字（XML + 同用例 logcat 采样）**：目录 facet **SECTION p95=207ms 超 backstop 200ms（超 3.5%）**；samples=[202, 205, 201, 201, 200, 206, 205, 199, 195, 268, 249, 194, 205, 194, 198, 199, 196, 203, 201, 200, 203, 201, 207, 204]。
- **门 stdout 另一次采样**：p95=211ms；samples=[211, 209, 205, 207, 204, 205, 204, 204, 199, 200, 201, 209, 206, 200, 205, 207, 217, 207, 199, 201, 203, 210, 219, 207]。
- **XML 证据（当时）**：`core/database/build/outputs/androidTest-results/connected/debug/TEST-test_device(AVD) - 14-_core_database-.xml`，run root `tests=53 failures=1 errors=0 skipped=0 time=711.7s`。
  - ⚠️ 该文件**已被综合门 DB 全套运行覆盖**（现盘上同一路径为 `tests=238 failures=0`）——复核者引用时须知原始 53 例根只存在于批 1 提交信息与会话回报中，本记录如实转录。
- **环境绑定先例（同形）**：S18 尾首跑 SECTION 220ms → 安静机器绿（`docs/research/2026-10-06-s18-fts-completion-record.md` §6 边际门登记）；D-0 综合门 221–247ms 四次连续失败 → 冷启 `-wipe-data` 后 173ms 绿。
- **本轮处置**：**未改任何断言/预算/夹具**（只读解析，`0b1b5a7f` 中无相关改动）；按门指令接受为已登记环境红。**本地裸预算是否调整属规则变更、留给用户裁定**——本记录不主张放宽。

## 6. 独立复核

- **批 1：approve-with-notes；批 2：approve-with-notes**（两批均为独立只读复核）。
- 复核意见的**明细未随提交留档、本会话未取证 → UNVERIFIED**（本记录不转录未读到的条目）。可确证的落地面：批 2 的版本台账 §3.19 明确记录了「计划 §4 零 bump 被裁定取代」的理由与归档保证（见 §7）；批 1 的既有 10k 门类一字未改、新增 50k 记录类与既有门类隔离（防 10k 锚点脱钩）。

## 7. 范围与版本变化：计划 §4「零 bump」由批 2 取代（须写明）

**计划 §4 原文**：「**零 schema**（两项实现均不该触及 Room schema / `LearningCoreVersions`）；若触及 → 停下报告再定。零算法版本 bump；若投影重放语义改动触发 `PROJECTION_COMPOSITE` 评估 → 按台账纪律先落快照，实施时报告。」

**实际**：批 2 **bump `PROJECTOR` v12→v13 + `ATTRIBUTION` v4→v5**（复合串 `learning-core-v12 → v13`；`SELECTOR_COMPOSITE` 同步；`STUDY_DATABASE_VERSION=61` 及其余串不动；**零 Room schema**——计划 §4 的「零 schema」成立，「零 bump」预期被取代)。取代理由与判据（台账 §3.19 已登记，本记录照录）：① 本批确实改变重放输出，而 §3.13 把旧行为（空绑定题退回写时快照）登记为 v12 既定语义，静默改会让台账失真；② 先例 v11→v12 属同一类重放语义变更；③ **不 bump 则「已按 v12 重放过的库」永远拿不到修复**——重放只由版本不匹配或改绑事件触发，静默无效。**归档保证**：`StudyProjectionDrainer.commitFullReplay` 先 `archiveDisplacedSnapshot` 再重放（W0-1 ③，既有机制）——本批不新增归档动作、不改归档路径；`KernelWave0SchemaContractTest` 7/0/0（定串同步属版本钉用例职责）。

## 8. 台账折入待办

`docs/agent-first-refactor-decisions-2026-09-23.md` **仍是另一条会话的在飞文件**（工作树 `M`，`git diff --stat` = 225 行未提交新增；本批全程未改动它，`git status` 前后一致）。按纪律**不改动**，以下条目登记为**台账折入待办**：

1. 3C 前半（`1d297efc`，`docs/research/2026-10-04-stage3c-part1-completion-record.md`）
2. 3C 后半（`docs/research/2026-10-05-stage3c-part2-completion-record.md`）
3. S18 尾（`docs/research/2026-10-06-s18-fts-completion-record.md`）
4. D-0 内核侧（`docs/research/2026-10-07-d0-binding-verdict-completion-record.md`）
5. **K1（本记录）**
6. 阶段 5 挂起（`docs/research/2026-10-04-stage5-gate-check.md`）

## 9. 遗留与 UNVERIFIED

1. **④-6 的 12.6ms 数字仍 UNVERIFIED**（S19 桌面 SQLite 探针，脚本未入库、本批未复测）；计划验收④「`KnowledgeContextRetrievalInstrumentedTest` p50/p95 不回归」——该类在批 1 门清单内且通过，但**本记录未取得其 p50/p95 数字**（落盘 XML 无 system-out）→ UNVERIFIED。
2. **`setQueryCallback` 不可用**按 KDoc 记「room3 3.0.0 已核实无此 API」采信，回退计数缝；该核实本身未在本会话复跑（UNVERIFIED，属实施期探针记录）。
3. **50k 各记录项不设门**（按计划设计）：5 万命中与排序宽集读数无自动告警；数字为 2 核模拟器 debug 口径，不当作真机/release 背书。
4. **P95 两套口径并存**（§3.5），未统一。
5. **批 1 环境红的 XML 原件已被综合门覆盖**（§5 注）；「失败时已重试」的 DB/app 中间失败明细未取证 → UNVERIFIED。
6. **D-0 记录 §6.7 的「bm25/rank FTS5 换库（3C 遗留，待用户裁定）」表述已成过期登记**——F6 已于 2026-10-03/04 裁「不换」（重开条件见 `docs/kb-outstanding-research-2026-10-02.md:1193-1196`）；该文件属 D-0 既成交付记录，本批未改动，只在本记录登记。
7. **台账折入待办**（§8，含 K1）；台账文件他线在飞，未碰。
8. **D-5 仪器化缺口**：`install()` 无 manifest 注入口（lazy 全局 `updateManifest`）→「只写侧车不 bump → 三元组不到设备」没有仪器化用例，按计划登记；现有回归钉是 JVM 纯函数两态。
9. **R8 冒烟为模拟器 + debug keystore 口径**（§4 注③），非真机、非发布签名；KB 线 D-5 写入窗口（真实包三元组落账，含「改侧车 + bump contentVersion」）仍**未开**。
10. **KB 线文件面全程未碰**（`tools/kb_*`、`docs/kb-*`、`docs/agent-first-refactor-decisions-*`、`docs/superpowers/specs/*`）——工作树中他线改动保持原样（提交一律显式清单）。

## 10. 剩余风险

1. **④-6 的正确性风险面 = 失效接线**（四处 + 代际防护已落，canary/负向用例已钉）；生产 `invalidationEnabled` 恒 true，若未来新增第五个写入口而漏接线，症状是「同进程召回漏新节点」——由 `KnowledgeSearchIndexCompleteness` KDoc 点名，无自动拦截器。
2. **50k 记录项无门**：模拟器劣化时这些路径的劣化不会被门捕获（只有 100 命中档有 500ms 预算）。
3. **P95 公式分叉**：新门略严于 `PerformanceGateTest` 口径；同一路径在两门口径下可能一门红一门绿（历史已出现）。
4. **v13 bump 的首次升级成本未实测**：存量库首次打开触发全量重放（先归档）；归档保证可回退，但耗时/空间在真实存量规模下 UNVERIFIED。
5. **KF-32 语义对存量「空绑定」题生效**：历史证据由旧知识点改挂 `pseudo:<科目>`——是正确性修复，但会改变用户可见的掌握度分布（旧节点掌握度归零、伪桶出现证据），属预期行为，未做用户侧沟通面（产品窗口内）。
6. **性能门的宿主环境敏感性**：S18 尾/D-0/K1 三次同形实证——本地裸预算 200ms 在当前宿主上状态紧（安静冷启 173–191ms，劣化 207–247ms）；CI ×4=800ms 为决定轮。
7. **bm25/FTS5 不换**：词面腿成为瓶颈且 D12 目标攻坚、或 LibrarySearchFts 因别的原因升 FTS5 时按 F6 重开条件重议。


## 11. 工作流复核发现与修复轮（2026-10-09，协调方）

工作流收尾返回的两批独立复核清单共 22 条，**全部 low**：15 条为「已核，无发现」的逐点核验记录
（缓存命中条件与两级门等价且命中零 DAO、计数断言非恒真、负向用例可红、既有 100 命中门未被放宽、
边际门 XML 独立重算为真且测试/预算/夹具零改动、零 schema、D-5 纯抽取逐行等价、空绑定重放与
写路径逐条同规则/旧节点归零/幂等/写路径逐位不变 等）；7 条为需处置项，处置如下。

| # | 发现（出处） | 处置 |
|---|---|---|
| 1 | 生产注释仍断言旧重放语义：`RoomMistakeOrganizationRepository.kt:1287-1289` 写「重放退回写时快照…留 KF-32 后续」，与 K1 实现相反 | **修复轮改准**（改为 pseudo 兜底已实现的表述） |
| 2 | D-5 用例断言消息与契约 §5.2 相反：`BundledKnowledgeBaseInstallerTest.kt:159` 把「戳一致即跳过」的静默失效形态写成「只写侧车不 bump」 | **修复轮改措辞**（戳不一致=随包已换版、进度戳落后；断言数值未动） |
| 3 | 缓存失效面 KDoc 只列 4 处；另两条写特征路径（`ProblemOrganizationDao.importKnowledgeBase`、`KnowledgeGroundingDao.applyReviewedPack`）未列、也未记「为何不需失效」 | **修复轮补记**（同事务写入 ⇒ 「已索引≥已审校」不变量延续；且无 `src/main` 生产调用方） |
| 4 | 两处 `ifEmpty` 注释各说一半触发形态（`LearningProjector.kt:959-960` vs `StudyProjectionDrainer.kt:343-344`） | **修复轮补全**（projector 侧列出两种异常形态并互指 drainer） |
| 5 | 50k 排序类在 1k 档新增了一条 P95 门（计划只授权「100 命中仍为门」；1k 是否属「沿用 10k 口径」存口径歧义） | 登记：1k 档在 CI ×4 口径内通过；**不撤门**（撤门属放宽，需用户裁定） |
| 6 | 50k 两类只证计时，未复现既有结构/EQP/等价性断言（既有 10k 结构门一字未改、本轮全绿） | 登记（与 §9.3 同向） |
| 7 | 安装落点失效与 `rebuildSubject` 对同一 subject 重复失效；「安装后立即可见」用例对安装落点**不敏感**（摘掉不红），手工红证未留档 | 登记（confirm 路径 canary 已钉红敏感形态；为安装落点补敏感面属新增测试，另立） |
| 8 | 计数缝无上限增长、命名易被读作真实 SQL 计数；**复核方以 javap 独立证实 room3 3.0.0 无 `setQueryCallback`**（`RoomDatabase$Builder` 只有 `addCallback`/`createFrom*`/`setQueryCoroutineContext`） | 登记缝现状；§9.2 的 UNVERIFIED 借该独立证据**关闭**（「回退 internal 计数缝」为正确选择） |
| 9 | 「空绑定」判定两侧口径不同：写路径先按 `basisRevisionId == 该题当前 revision` 与非空过滤再判空，重放侧只判集合是否为空 | 登记（当前**不可达**：`practice_unit.problem_revision_id` 无 UPDATE 路径；属潜伏不一致，改动会动语义、需独立门） |
| 10 | 新增「revision 取该题当前 revision」断言不具判别力（夹具两处 revision 同为 `revision-1`） | 登记 |
| 11 | 无冷启/wipe 留档；批 1 另一次 211ms 采样未存档 | 登记（环境红归因独立成立，见 §5） |
| 12 | 台账「经裁定由本行取代」在仓库内无裁定原文 | 本记录 §7 已照录判据与三条理由；裁定本身由协调会话在工作流升级应答中出具（2026-10-09），以此说明补足 |

**修复轮提交**：`f38721a6`（4 处注释/措辞）；本 §11 随其后一次记录提交落档。
**修复轮门**：全量 JVM `--rerun-tasks` = **2426 / 0 / 0 / 0**（2026-10-09，345 个结果 XML 求和）；本轮改动仅注释与一条断言消息文本，
**仪器化行为零变化**故未重跑仪器化套件（综合门三套件的结论继续有效）。
