# 批次 50 记录（2026-11-09）

> 本文件由 **3D D-2/D-3 子线**创建；其他子线/主会话请在文末**追加**，不要改写本节。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/{d2,d3}/`。
> 计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md`（D-2 / D-3 行）。

## 3D D-2/D-3（子线收口）

**终态：D-2 四项清零或登记；D-3 五红清零 + 召回实修 + 117/129 保持（设备与 JVM 双读数）。零阈值改动、零断言放宽、零包写入。**

### D-2（结构审计）

| 项 | 实测 | 处置 | 证据 |
|---|---|---|---|
| 近重复（节点级） | **0 组 / 0 对**（`scan_duplicate_nodes --json`） | 清零 | `d2/d2-near-dup-scan.json` |
| 近重复（候选区 2 组） | 两组**都在包内、同节点、正文 161/164 与 240/242 字近同**；材料级工具因实体保护门未退休 | 逐组裁决：化学保留 `cmp-che-02368` 退休 `cmp-che-02639`；数学保留 `cmp-mat-00280` 退休 `cmp-mat-00065`（**退休未执行**，写入窗口落） | `d2/d2-near-dup-dispositions.csv`、`d2/README.md §1.2` |
| 超配 | **2,481 节点 / 62,944 条 / 超出前 4 = 53,020**；最大 146（MATH 三角函数的图象与性质）；零 2-gram 5,954（超配段 4,918） | **RETAIN_NO_CAP**：运行时无每节点条数门，全部保留；分诊 49 优先 / 1,009 复核 / 1,423 无；节点级台账 2,481 行 | `d2/d2-overfull-ledger.csv`、`d2-overfull-report.txt` |
| 容量 91 条 | 22 已同节点入包 / 12 异节点入包 / 44 未入包 / 13 节点已退役 | 逐行登记；生产器废止补丁**未应用**（目标文件他线在飞） | `d2/d2-capacity-dispositions.csv`、`d2/d2-capacity-deprecation.patch` |
| 历史 type | 包内 64,853 条全 3 值，**三值外 = 0**；materialize/merge/protocol 三处白名单逐字 3 值 | 单源成立，清零 | `d2/d2-type-distribution.csv`、`d2-type-single-source.txt` |

工具测试：`pytest tools/tests/test_kb_scan_duplicate_nodes.py test_kb_overfull_nodes.py test_kb_materialize.py -q` → **26 passed**。

### D-3（检索质量）

1. **五红清零（当前树 12/12 绿）**：`GoldenRetrievalJvmTest` 2、`ProductionLexicalLegExportTest` 1、
   `Stage1LexicalLabTest` 1、`Stage2LexicalScoresExportTest` 1、`DenseTokenizerParityTest` 7，
   failures/errors/skipped 全 0（`--rerun`，BUILD SUCCESSFUL 2m13s / 修复后 1m3s）。
   如实边界：历史红的成因已在批次 13 重钉，本批**未回退重演**历史红态。
2. **dense 与包同源**：`check_asset.py` RC=0，11/11 OK（`.vec` sha `70329115…`、44,121 行、
   旁车 packSha256 == 包 sha `10ede1bf…`）。
3. **召回实修（先红后绿）**：父节点取行用了历史解释路径 `readKnowledgeNodesByIds`
   （刻意不过滤 RETIRED/verification），把**退役父主题**与 **MODEL_CANDIDATE 父主题**随 active
   子节点披露。修复 = 新增 `ProblemOrganizationDao.readRecallParentKnowledgeNodes`（subject+可信+
   非退役）并在 `RoomKnowledgeBaseStore` 使用。
   红：设备 `Tests run: 1, Failures: 1`，ids 含 `topic:retired`/`topic:candidate`；
   绿：`OK (1 test)`；同路径 `KnowledgeSearchIndexCompletenessInstrumentedTest` `OK (7 tests)`。
4. **117/129 保持**：
   - 设备融合 **117/129 = 0.9069767441860465**、MRR **0.7239018087855298**、p50/p95 = 38/58ms、
     overBudget 0、`denseLegLive=true`、**24 章逐章与批次30 v24 diff rc=0**；
   - JVM 融合同值 117/129、MRR 0.7239、24 章 diff rc=0；
   - 词面 101/129；12 条 MISS 同集（`等和线`/`活结与死结模型`/`力的分解` absent 边界不变）。
   - **口径注**：批次基线文档写的 MRR 0.7323 / 词面 102/129 是批次 19 时点值；v28 实测为
     MRR 0.7239 / 词面 101/129（与批次 30 v24 逐位一致）。

### 本子线改动文件

- 生产码（最小 hunk）：`core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/dao/ProblemOrganizationDao.kt`、
  `core/database/src/main/kotlin/com/tingyun/smartmistakebook/core/database/RoomKnowledgeBaseStore.kt`；
- 新增设备用例：`core/database/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/database/KnowledgeRecallParentScopeInstrumentedTest.kt`；
- 证据：`docs/research/evidence/2026-11-09-batch-50/d2/`（审计脚本 + 4 份台账 + 补丁 + README）、
  `…/d3/`（五红 XML/摘要、红绿转写、dense 校验、设备/JVM 读数与逐章 diff、README、summary.json）。

### 未做 / 未验证（如实）

- `:core:database:testDebugUnitTest` = 248 tests / **4 failures**，全部在 `KernelWave0SchemaContractTest`、
  `LibraryFtsCountPathQueryCopyContractTest`（2）、`LibraryFtsRankingQueryContractTest`；失败文本为
  schema 头/冻结 SQL 副本漂移，与本次改动文件无交集；**未在回退树上重演**该 4 条，故不记通过。
- 近重复 2 条退休未执行；容量补丁未应用；超配 2,481 节点只给保留策略与台账，未逐节点内容裁决。
- 未跑真机（仅 emulator-5590）、未跑 2h soak、未跑全模块单测门。
- `:core:data` 全量实跑 1688 tests / **4 failures**（`OpenAiCompatibleModelGatewayTest` ×3、`RoomBackedStudyExperienceRepositoryTest` ×1），
  均与 KB/D-3 无关、未归因本改动；不记通过。
- 副作用：`dedupe_near_dup_materials --dry-run` 会按工具设计重写 `build/agent-batch31c/{dedupe-clusters,dedupe-pairs,plan.json,anchors.txt}`
  （gitignored 产物；批次31c 原版未保留，`manual-review-20.csv` 未动）。
- 未改任何 KB 生产内容/包/promote；`core/data/src/main/resources/knowledge` porcelain 为空。

### D-1 引用口径（修复环补记；本节只记 D-2/D-3，D-1 在独立证据目录）

1. D-1 的门判是 **`PASS main / FAIL guardrail` → `overall_pass=false`**；**不得**写成"3D 通过"，
   也不得把主判据 0.9757 当可用门值：判定一致性 κ **NOT RUN**，按 R2 §4.5 只能读作
   **model-labeled 暂定值**（`d1/REPORT.md:20,133-136`）。
2. 抽样框 `build/kb-staging/overfull_nodes.csv` 在抽样后被 D-2 于 15:20 原地重写
   （`fce8269c…` → `ce7d6823…`），`sample_d1.py --check` 现为 match=false/exit 2；
   影响量化：层归属 0 条不一致、主判据 0.9756913→0.9756882（PASS）、normal 护栏仍 FAIL。
   详见 `d1/FRAME-DRIFT.md`。**"同一框必得同一样本"不再成立**。
3. 护栏对口径敏感：预注册 no-FPC 下 normal 0.8979148（FAIL）；FPC 口径下 0.9005766（通过）。
   按预注册口径判红，差异并列披露（`d1/REPORT.md §4.3`）。

---

## 3E（G2：全库薄弱点扫描 / 队列 / 预算）子线

> 本节由 **3E 子线**追加；上方 3D 节原文未改，仅在末尾追加了修复环的「D-1 引用口径」块（D-2/D-3 结论不变）。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/3e/`（`README.md` / `summary.json` / `00-snapshot.txt` / `logs/` / `jvm/`）。

**终态：3E 门通过（条件性）；生产码零改动；门值零改动（`FULL_LIBRARY_SCAN_DAILY_CAP` 仍为 10）；未提交。**

### 门判据实测

| 判据 | 读数 | 证据 |
|---|---|---|
| 离开今日并集可捞回 | 45 分钟预算下，队外 118 个薄弱点里恰好捞回 **10** 个，且 10 个全部在今日计划知识点并集之外 | 设备 `OK (4 tests)`（`logs/08-instrument-3e-green-final.log`） |
| F17 名额封顶 | 第二档每次排计划恰好 **10**（候选 118） | 同上 |
| F8 时间预算封顶 | 1200s 预算 → 知识点队列 **6** 项（6×180=1080 ≤ 1200 < 7×180），其中第二档仅占 4 个（名额剩 6 未用） | `#knowledgeQueueStopsAtTheTimeBudgetWhenItBindsBeforeTheDailyCap` |
| 每日/单计划口径 | 实现 = **每次排计划 ≤10，非严格每日累计（KD-46）**；同日两次排计划各 10、累计 20 > 10；纯函数同输入重算逐位一致（无"今日已扫集合"） | 设备 `#scanQuotaIsPerPlanNotADailyCumulativeTotal`；JVM `KnowledgeReviewQueueTest#scanQuotaIsPerPlanNotADailyAccumulator` |

设备：`emulator-5590`（x86_64 / API 34）——**非真机**。test APK sha256 `dc0abe9d…0147a82`；
HEAD `a87fc70d`。

### 关键发现

1. **既有 3E 设备用例早已是红的：首跑 `Tests run: 3, Failures: 3`**（`logs/01-instrument-3e-scan-existing.log`）。
   红因是夹具对两处后来才变的口径的假设，**不是实现缺陷**：
   - `BudgetResolution`（批次 49 `b6712a42`）后构造参数预算只在 `CONSTRUCTOR_ONLY` 下生效，真 DataStore
     默认 1200s 覆盖了用例显式传的 120/360（失败原文 `expected:<120> but was:<1200>`）；
   - `ea4348f9`（2026-11-06）把知识点队列预算从 `reviewTimeBudgetSeconds` 改成
     `currentPlan.plan.timeBudgetSeconds`（预算同源），"今日计划 120s + 知识实例 2700s"因此不可能成立。
   处置：**只改夹具不改生产码**，断言意图一条没删（节点 15→126；新增"已掌握点不被捞回"负向；
   新增"捞回 = 队外最老 10 个"的排序断言，强于原来的平局定序）。绿：`OK (4 tests)`。
2. **F17 的 10 是"每计划"上界，不是预留份额**：并集按分取队（`KnowledgeReviewQueue.kt:204-240`），
   默认 20 分钟 = 6 个知识点槽（1200/180）；若今日计划的题绑定节点已占满这些槽且分数更高，
   第二档贡献可以是 0。本轨 45 分钟夹具（4 个今日档 + 10 个第二档）证明"能捞回"成立，但
   "每个默认配置都能捞回"不成立。**这是产品判断（是否给第二档预留份额），本轨未改语义/门值，
   留给主会话裁定**（证据 README §5 R1）。

### 本子线改动文件

- `core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/study/StudyReviewPlanner3EScanInstrumentedTest.kt`
  （夹具修正 + 规模化 126 单/127 节点 + 新增 `#scanQuotaIsPerPlanNotADailyCumulativeTotal`；4 个方法名保留）
- `core/domain/src/test/kotlin/com/tingyun/smartmistakebook/core/domain/KnowledgeReviewQueueTest.kt`
  （新增 `#scanQuotaIsPerPlanNotADailyAccumulator`；该文件 23/23 绿）
- 证据：本目录 `3e/`

证据口径：设备绿相的 test APK（sha `dc0abe9d…`）构建自**当前工作树**（HEAD `a87fc70d` + 他线在飞
改动：`KnowledgeReviewQueue.kt` +21 行的批次 49 `reasons` 字段、`ReviewPlannerV2.kt` +79/-22、
`ProblemOrganizationDao.kt` 的 D-3 父节点读口）。本轨未改任何生产文件（`git diff` 仅命中上述两个
测试文件），3E 两档取队/预算/名额路径逐行未变。

### 命令（逐条，供复跑）

```text
./gradlew :core:data:assembleDebugAndroidTest --console=plain
adb -s emulator-5590 install -r -t core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk
adb -s emulator-5590 shell am instrument -w -e class \
  com.tingyun.smartmistakebook.core.data.study.StudyReviewPlanner3EScanInstrumentedTest \
  com.tingyun.smartmistakebook.core.data.test/androidx.test.runner.AndroidJUnitRunner   # OK (4 tests)
./gradlew :core:domain:test --tests "com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueTest" --console=plain
./gradlew :core:data:testDebugUnitTest --tests "…RoomBackedStudyExperienceRepositoryTest" \
  --tests "…StudyReviewPlannerExamWindowTest" --tests "…StudyReviewPlannerServiceCapacityPreviewTest" --console=plain
```

### 未做 / 未验证（如实）

- **未上真机**（仅 emulator-5590）；未跑 2h soak；未跑 `connectedLocalFirstDebugAndroidTest`（计划禁令）。
- **未用生产用户库导出**：种子是确定性合成内容（真 SQLite/Room schema + 真读路径 + 真材料写口），
  规模 126 在册题/127 节点；"真实/可复现"指真库真链路，不是真实用户数据。
- 设备侧扫描 SQL 未独立计时（整组 `Time: 3.668` 含建库/播种）；JVM 5000 候选规模门实测该用例 44 ms。
- `:core:data` JVM 本次只跑 3 个类（45 tests / 1 failed）：`RoomBackedStudyExperienceRepositoryTest
  #profileStrengthsDropForgottenAndPrerequisiteSuppressedNodes`（`expected:<[knowledge:m1:math.read-monotonicity-from-graph]> but was:<null>`）
  为**既有失败**（与上方 3D 节记录的批次 49 同一条），与 3E 无交集；本轨未修、未记为通过。
  另：工作树 `core/data/.../RoomBackedStudyExperienceRepository.kt` 有他线在飞新增（KD-67 装配 69 行），
  未归因。

---

## 切分 CV 预处理（G3 item 19–20；子线收口）

> 本節由**切分 CV 预处理子线**追加；上方 3D / 3E 节未改动。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/step19b-commands.md`、`step19b-dev-parity.txt`。

**终态：轻量 CV 已落生产（`core:model` 纯 JVM，零新依赖）+ 结构提示已进单页切分 wire；
dev 16 页与 Python 探针逐页一致；acceptance 8 页零运行；门值零改动；未提交。**

### 落地了什么

| 层 | 文件 | 内容 |
|---|---|---|
| 算法（纯 JVM） | `core/model/.../layout/LayoutStructure.kt`（新增） | Otsu 二值、纸面裁边窗、行谷分块（滑动中位数）、栏谷分栏（谷宽/两侧墨量/两侧同高三层 fail-closed + 谷区间隙合并）、题号候选（三条几何证据 + 分数，**只度量不进提示**）、提示块渲染（≤1200 字符，谷宽降序取 8 条，超限显式写"已截断"） |
| 输入契约 | `core/model/.../ModelTasks.kt` | `CaptureAssessmentInput.layoutHint`（`@EncodeDefault(NEVER)`，null 时请求体与指纹逐字节不变）+ `MAX_CAPTURE_LAYOUT_HINT_CHARS=1200` |
| 提示词 | `core/data/.../model/OpenAiModelTaskAdapters.kt` | 只在单页切分提示词末尾逐字追加；多页关系变体不带 |
| Android 端口 | `core/data/.../model/PageLayoutHint.kt`（新增） | 出网副本 → `BitmapFactory`（预算内采样 + 长边 1024）→ ITU-R 601 灰度（四舍五入，与 PIL 同口径）→ 提示；任何失败返回 null |
| 装配 | `core/data/.../model/OpenAiCompatibleModelGateway.kt` | 读图后算提示；预算按**带提示**的输入复算；`requestBody` 用同一提示值；生产工厂显式传 `AndroidPageLayoutHint` |

### 验证（本会话实跑）

- `:core:model:test --rerun` → **674 tests / 0 failures / 0 skipped**（69 类，含新增 19 例）。
- `:core:data` 目标类：`CaptureAssessmentLayoutHintPromptTest` **4/4**；
  `OpenAiCompatibleModelGatewayTest`+`OpenAiCaptureAssessmentAdapterTest`+`OpenAiModelProtocolMockWebServerTest`
  **81 tests / 3 failed**，三条均为**批次 49 已登记既有失败**（`unit-gate.md:85-87`），非本轨回归、不记通过。
- 设备 `emulator-5590`（x86_64/API34，**非真机**）：`PageLayoutHintInstrumentedTest` **OK (3 tests)**。
- dev 16 页 × Python 探针：栏判 **16/16 一致**、行带块数偏差 **≤2**、提示块 **113–286 字符**、
  候选命中率 **0.1086**（研究值 0.1111）。逐页表见 `step19b-dev-parity.txt`。

### 先红后绿（如实）

契约用例先红：`Unresolved reference 'layoutHint' / 'MAX_CAPTURE_LAYOUT_HINT_CHARS'`
（**编译级红**，不是行为断言失败）。行为级红→绿一处：设备用例报
`grayConversionUsesItuR601Luma: expected:<150> but was:<149>`，修的是实现（灰度改四舍五入，
与探针 PIL 同口径），不是断言。

### 未做 / 未验证（引用时必须带上）

- **acceptance 8 页零运行**（一次性红线）；**LLM-only / LLM+hint 两臂未跑**（本会话零 provider 调用；
  `local.properties` 有明文 key，本轨未使用、未复制进证据，建议轮换并移出仓库）。
- **门值未动**：`0.5714` 仍是当前值；`check_split_gate.py` 未跑（无新导出）。
- 三个 `0.5714` 失败页里，`…05605`/`…11126`（dev）现在有几何提示可发，`…04353` 属 acceptance 未跑；
  **"发了提示模型是否改对"没有证据**。
- 真机耗时、EXIF/旋转归一（研究 §3.6）、题号候选进产品（刻意不做）均未做。

### 已登记风险

1. **提示不进指纹/不落库**：由网关从出网副本算出，`ModelTaskRequest` 内仍是 `layoutHint=null`；
   CV 参数变更后同一 fingerprint 可能对应不同 wire 文本（同资产 sha 下确定）。不得声称"wire 文本被指纹覆盖"。
2. `PageLayoutHint.None` 是构造默认值（JVM 单测缝）：生产工厂已显式装配，但"漏传即静默无提示"无法被单测证明。
3. dev 判出双栏仅 4 页（`…04854` 旋转页、`…11649` 暗光页是已登记误判，真双栏 2 页）、
   acceptance 8 页**0 张双栏** → 栏逻辑在验收集上是盲区。

---

## 切分门（G3 item 21–23：dev 调参 / acceptance 一次 / 判门；子线收口）

> 本节由**切分门子线**追加；上方 3D / 3E / 切分 CV 预处理节未改动。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/split-gate/`（README + 142 份产物 + MANIFEST）。

**终态：门 FAIL（`check_split_gate.py` exit 1）。① 7/24=0.2917 < 0.9；③ 1/78=0.0128 ≤ 0.05
但硬红线 `fragmentedQuestions=1≠0` 未满足 → 门后自动化继续不启用。阈值/金标/口径/分母零改动；
生产代码零改动；acceptance 8 页只跑一次（本批）；未提交。**

### 实跑读数（全部本会话）

| 臂 | 页 | ① countExact | ② IoU≥0.5 | ③ fragment | 通道错误 |
|---|---|---:|---:|---:|---:|
| A. LLM-only（无 hint） | dev 16 | 4/16 = 0.2500 | 22/52 = 0.4231 | 0/52 | 0 |
| B. LLM+hint（产品路径） | dev 16 | 4/16 = 0.2500 | 16/52 = 0.3077 | 0/52 | 3 |
| B2. LLM+hint+提示词实验（已回退） | dev 16 | 3/16 = 0.1875 | 21/52 | 0/52 | 1 |
| **acceptance 单次（B 臂）** | acc 8 | **3/8 = 0.3750** | 11/26 = 0.4231 | **1/26 = 0.0385** | 2 |
| **判门（dev+acc 合并 24 页）** | 24 | **7/24 = 0.2917** | 27/78 = 0.3462 | **1/78 = 0.0128** | — |

- 裁定 `rulings/20261108T091855Z-013fad75.json`：`validate_rulings.py` **PASS**（R1–R9）；
  consistent 6 / fragmented 1 / inconsistentCount 17 / missingInApp 0 / notEvaluable 0。
- 根因分布（24 页）：`type` 键被 `requireOnlyKeys` 整轮拒收 4 页（dev 010306/010633、
  acc 004952/011800；其中 011800 是单题页，拒收后 0 框仍按口径记"一致"，不计入 17 页不一致）；
  `NEED_MORE_IMAGE` 判"未拍全" 2 页（004854/005605）；`NEED_MORE_IMAGE` 未配 `ADD_IMAGE`
  1 页（011017）；hint 臂 5 区域互相重叠 → 不可用 1 页（005144，A 臂同页 5 框）；`PASS` 漏读
  1 页（011126）；相邻题合并/多计 9 页。

### 做了什么 / 没做什么

- **新增** hint 臂评测通道 `core/data/src/test/.../GoldenSplitEvalHintChannelHarness.kt`
  （新文件；与批次 48 已提交通道同构，差异仅 `input.layoutHint`；默认 assumeTrue 跳过）。
- **dev 调参**：做了 1 组提示词规则实验（顶层键白名单 + 边缘裁切口径），先红后绿
  （红：`3 tests / 2 failed`；绿：`BUILD SUCCESSFUL`），但 dev 实测 ① 3/16 **无净收益**，
  按"未验证不进发版"**原字节回退**（实验用例同时删除）；生产提示词 sha 仍是
  `31aef2ab0f07b8d3…`（dev 无 hint 臂 16/16 页同一 sha）。
- **未调** 研究 §8.4 允许的 CV 参数（`LayoutConfig` 默认值原样）；`temperature`/模型参数未动。
- **未改** 他线在飞文件；`core/` 的 `git diff` 仍只有 CV 子线既有改动。

### 口径限制（引用门值必须带）

1. **acceptance 切片在批次 48 已消费一次**（`acceptance-once-01`，① 5/8、③ 3/26）；本轮是同一
   8 页在新配置下的**第二次观测**，held-out 属性不可恢复；`heldOutUnusedForTuning` 在本批语境
   不成立。跑前冻结见 `split-gate/acceptance-protocol.md`。
2. acceptance 8 页 **0 张双栏**；① 在该集是 8/8 全或无。
3. 金标 **G1**（单模型盲标、无人工专家）；`004854` 坐标系未统一（保留在分母，未剔除）。
4. 通道为 JVM harness（同一 `requestBody`/`parseCompletion`/`isUsableSplitRegionSet`，
   PIL 出网副本与灰度），**不等于**设备产品路径准确率，也不外推到 155 页总体。
5. 与批次 48 同输入对照只作登记、不作优劣结论：48 合并门 ① 10/24、③ 3/78、红线 3；
   本轮 ① 7/24、③ 1/78、红线 1；两次都是 FAIL，差值在模型随机性与通道错误个数量级内。

### 未做 / 未验证（如实）

- **未跑真机**；未跑 2h soak；未跑 `connectedLocalFirstDebugAndroidTest`（计划禁令）。
- 单轮采样（每臂一次），不报均值/显著性；模型版本只有 provider 名称（`deepseek-flash`）。
- `type` 键缺陷的**候选修复未进产品**（净效应未证明）；下一批须在新的 acceptance 切片上验证，
  不得拿本轮 acceptance 复跑换绿。
- hint 净效应不可分辨（A 4/16 vs B 4/16；② 22/52 vs 16/52，方向反）。

---

## MVA 门（G4：冻结 / 两臂 / 确定性检查 / VLM judge / 门判；子线收口）

> 本节由 **MVA 子线**追加；上方 3D / 3E / 切分 CV / 切分门节未改动。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/mva/`（README + `mva_preflight.json` + 两臂 harness 报告）。

**终态：门 FAIL（不可评，未达标）。零生图、零真实 Provider 调用、零 VLM 判官调用、零设备执行；
门阈值零改动；`figure_eval_gate.py` 本体零改动；JS 不广告；未提交。**

### 实跑读数

| 项 | 读数 | 依据 |
|---|---|---|
| 冻结 sha（2 文件）+ 生成器 sha | 全一致（`manifest_sha_verified=true`、`generator_sha_matches=true`） | `mva/mva_preflight.json#fixture` |
| 盲测/开发隔离 | 120 / 24，交集 **0**，dev 不进分母 | `#isolation` |
| 夹具自洽 | **288 条事实 PASS**（容差/枚举/类型/重复 key） | `#fixture_sanity` |
| A/B/C 通道（确定性派生） | 94 / 116 / 40 / 38；critical A 只 **87**；含 critical A 只的题 **57/120**；全 critical A 只的题 **27/120**；`judge_channel_map.json` sha `580c2f63…`，两次生成逐字节一致 | `#channel_totals`、`tools/figure_eval/judge_channel_map.json` |
| IMAGE_MODEL 臂 | fake 驱动 `machinery_verdict=PASS`、`real_provider_quality=UNVERIFIED`；**0 可评样本** | `arm-fake/`、`#readings.IMAGE_MODEL` |
| JS 臂 | `js_backend_status=NOT_AVAILABLE`；质量门 `NOT_EVALUABLE`；**0 可评样本** | `arm-js/`、`#readings.JS` |
| 首次 / 预算内 / 每组 / p95 | 全部 **null**（无样本记 null，不记 0 命中）；`evaluable=false` | `#readings` |
| 关键错误被接受 | 0 条但 **`citable=false`**；`:980` 硬编码恒真、`:1080` 读该字段 | `#readings.*.critical_errors_accepted` |

**为什么不可评**：IMG 臂无图像后端（`local.properties` 仅 `deepseek-flash` chat 通道，无图像端点/key）；
JS 臂无 Python 引擎适配器（`tools/figure_eval/README.md:83-84`）、无"题面→SceneV1"作者链、
无 `RenderScene→facts` 提取器。VLM judge 无实现（harness `judge` grep 0 命中）、
无 C 通道注入渲染件（`png|PIL|matplotlib` grep 0 命中）、无视觉凭据。

**关键错误单列**：E0-D（direction 24 critical）+ E0-O（ordering 16 critical）只能走 A|C，
C 通道未校准 → 40 条 critical 全部 UNVERIFIED；E0-N 的 85 条 A 只事实同理。
27/120 题的全部 critical 事实像素不可核验——仅靠栅格判官不可能证明这些题"关键错误=0"。

**R5 正文一处漂移（本轨实算更正）**：R5 §6.2 写"81 numeric + 9 大计数 = 90 条像素不可核验"；
按规则实算 numeric A 只为 **85**（1e-9×80 + 1e-6×5），合计 **94** 条（critical 87 一致）。
差 4 条为非 critical 的 1e-9 数值事实，以实算为准。

**JS 不广告（核验）**：`FigurePromptAssembler.kt:21-23` 明写生产三入口仍硬编码 `IMAGE_MODEL_ONLY`；
生产四处能力集合（`RoomDetailedSolutionRepository.kt:256`、`RoomMistakeEditRepository.kt:258`、
`ConfiguredCleanImageGenerator.kt:95`、`AttachedImageGenerator.kt:144`）全部 `IMAGE_MODEL_ONLY`。

### 本子线改动文件

- 新增 `tools/figure_eval/mva_gate.py`（读数器：冻结/隔离/通道映射/关键错误登记/门读数）；
- 新增 `tools/figure_eval/test_mva_gate.py`（16 例；初始 14 例先红后绿，另 2 例为同批回归用例）；
- 新增派生 `tools/figure_eval/judge_channel_map.json`（sha `580c2f63…`）；
- 证据 `docs/research/evidence/2026-11-09-batch-50/mva/`；
- 本记录小节。**未改** `figure_eval_gate.py`、120/24 集、`GATES`、任何产品代码。

### 未做 / 未验证（如实）

- 零真实生图：120 题质量结论不存在；不是"0% 命中"。
- 零 VLM 判官：C 通道不可入用，A|C 事实一律 UNVERIFIED。
- 零设备：`emulator-5590` 在线但未安装/未跑；JS 引擎装载/度量/隔离均未验。
- 零 Provider 调用：`deepseek-flash` 无图像能力的判断依据是"缺图像端点/key"，未做能力探测请求。
- 生成器重跑逐字节复现未复验（临时目录重跑被仓内写保护 hook 拦；改为只校验生成器 sha）。
- Wilson/零事件上界为描述性统计；每组 n=20 时 20/20 的 Wilson 下界 0.8389 < 0.90（点估计门）。

---

## 5c 错因 UI（G 产品面：详情页教学候选 / 用户确认 / 撤销）子线

> 本节由 **5c 子线**追加；上方 3D / 3E / 切分 / MVA 节未改动。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/5c/`（4 张设备截图 + README + 命令/读数）。

**终态：详情页面板落地并设备走查通过（注入候选 → 确认 → 撤销 → 清理），门值/阈值零改动、零断言放宽。**

### 5c-0 门态与口径（先定分支）

- 3D D-1 实测 `PASS main / FAIL guardrail` → `overall_pass=false`
  （`d1/stats.json`：normal 层 Wilson 下界 `0.8979148347511259` < 0.90；`d1/REPORT.md:24`）。
  **引用口径（修复环补记）**：主判据 0.9757 的判定一致性 κ 为 **NOT RUN**，按 R2 §4.5
  `κ<0.60 → 该轮判定不得用于门`，它只是 **model-labeled 暂定值**；本节按 `overall_pass=false`
  处理与 κ 无关（护栏跳闸已足以判未过门），但不得把 0.9757 写成"可用门值"。
  本节据此按"**未过门**"实现：**模型自动落账关闭**（`ErrorCauseReviewService.autoBookingClosed`
  恒 true，`ErrorCauseGate.isDefaultClosed`）。
- 「候选确认」是否可写，曾有两处相反读法（计划 `:61` 要求"确认/撤销"；ask 写"只读展示候选、
  不自动落账"）。本轮按主会话裁定取 **B：用户确认可写，仅模型自动落账关闭**。落地口径：
  1. 确认走 `ErrorCauseRetractor.confirmByUser`（`acceptanceSource=USER_CONFIRMED`，不带
     `gate_version`），与 D-1c 解耦；
  2. 撤销走既有 retraction 语义（插 `RETRACTED` + `ACTIVE→VOIDED`，同事务；无 DELETE）；
  3. 面板标题固定「教学候选（未成为标签）」，落款写明"不进入错题本的分类、筛选或卡片标签"——
     D-001 冻结合同（`docs/model-first-product-boundaries.md:9`、
     `docs/product-information-architecture.md:287,408`）的"教学候选"allowlist 内，
     **未改合同文件**。
- 与裁定的一处偏差（如实登记）：主会话口述"确认来源用 `USER_CORRECTED`"，实际落
  `USER_CONFIRMED`——`USER_CORRECTED` 是"改判/supersede"的语义，且
  `ErrorCauseRetractorTest.kt:82` 已用它钉住 `USER_CONFIRMED`；改常量会同时改语义与既有绿用例。
  故保留 `USER_CONFIRMED`，差异在此登记，供主会话复核。

### 5c-1 改动（新增为主，最小 hook）

新增：

- `core/data/src/main/kotlin/…/study/ErrorCauseReviewService.kt`（读候选/确认/撤销的唯一接缝；
  bindingId = `ecuser-` + observationId，重放幂等；跨修订候选一律拒绝且零写入）
- `core/data/src/test/…/study/ErrorCauseReviewServiceTest.kt`（6 例）
- `core/data/src/androidTest/…/study/ErrorCauseReviewServiceInstrumentedTest.kt`（真 Room，1 例）
- `feature/library/src/main/kotlin/…/MistakeErrorCausePanel.kt`（面板 + 展示映射）
- `feature/library/src/test/…/ErrorCausePanelStateTest.kt`（9 例）
- `feature/library/src/androidTest/…/MistakeErrorCausePanelInstrumentedTest.kt`（Compose，7 例）
- `app/src/main/kotlin/…/MistakeErrorCausePanelHost.kt`（app 装配：scope/刷新/回执）
- `app/src/test/…/ErrorCauseWriteMessageTest.kt`（3 例；由设备走查缺陷反推）

改动（最小 hunk，未整文件抢占）：

- `core/database/…/dao/ErrorCauseDao.kt`：新增只读 `proposedObservations(learner, unit)`。
- `core/database/…/port/ErrorCausePort.kt`：新增 `readProposedObservations`（默认抛，与既有约定一致）。
- `core/database/…/RoomStudyDatabase.kt`（**他线 M 文件**）：仅加 6 行 override；
  该文件的 M 来自 PR#13 T8 桥接（`git diff` 16 行新增），本轨未触碰其内容。
- `feature/library/…/MistakeDetailRoute.kt`：新增可选 slot `errorCausePanel`，只对当前修订调用。
- `app/…/SmartMistakeBookApplication.kt`：新增 `errorCauseReviewService`（lazy）。
- `app/…/SmartMistakeBookRoot.kt`：详情页 composable 一处参数（最小 hook）。

### 5c-2 设备走查（emulator-5590，非真机）

注入是必要的：生产**没有候选生产者**（`ErrorCauseBookingService`/`ErrorCauseExtractor` 无调用方），
真实用户只看到空态。注入行的 `observation_id` 必须等于 `ErrorCauseIdentity` 的内容寻址值，
否则 `confirmByUser` 在端口内找不到 observation（会退化成 CONFLICT）。字段序（长度前缀）：
`error-cause-observation-v1|learner|unit|revision|node|attempt|cause|detail|channel|extractorVersion|supersedes|ref(kind,id)`；
本轮实算 `ecobs-1d6379db…e25e40`。

| 步 | 读数 |
|---|---|
| 注入 1 条 PROPOSED | 面板显示候选（E01 / 讲题记录 / 86% / 证据 1 条） |
| 点「确认这条」 | `observation=ACCEPTED, decision_source=USER`；`binding=ecuser-…, acceptance_source=USER_CONFIRMED, gate_version=NULL, state=ACTIVE` |
| 点「撤销」 | 原 observation 保持 ACCEPTED；新增 `RETRACTED(USER, supersedes=原证据)`；binding `VOIDED`、`active_key=NULL` |
| 清理 | `count(error_cause_observation)=0`、`count(error_cause_binding)=0`（注入行全删，恢复他线可用状态） |

**走查发现并修复的缺陷**：撤销成功后回执仍显示"已记下"（确认文案被两个相反动作共用）。
修法 = `errorCauseWriteMessage(result, action)` 按动作分支；新增 `ErrorCauseWriteMessageTest`（3/0）。
修复前状态见 `5c/README.md §1` 说明（截图已用修复后复拍替换）。

### 5c-3 门禁读数（本轮当前树）

| 命令 | 读数 |
|---|---|
| `./gradlew :core:data:testDebugUnitTest --tests "*ErrorCause*"` | 41 tests / 0 failures |
| `./gradlew :feature:library:testDebugUnitTest --tests "…ErrorCausePanelStateTest"` | 9 / 0 |
| `./gradlew :app:testLocalFirstDebugUnitTest --tests "…ErrorCauseWriteMessageTest"` | 3 / 0 |
| `./gradlew :core:database:testDebugUnitTest --tests "*ErrorCauseSchemaContractTest"` | 3 / 0 |
| `am instrument -e class …ErrorCauseReviewServiceInstrumentedTest`（真库） | OK (1 test) |
| `am instrument -e class …MistakeErrorCausePanelInstrumentedTest`（Compose） | OK (7 tests) |

### 5c-4 未做 / 未验证（如实）

- **未上真机**；未跑 2h soak；未跑 `connectedLocalFirstDebugAndroidTest`（禁令）。
- **生产无候选生产者**：面板当前只展示空态；"讲题/重做 → 产候选"的写入方仍缺（批次 41 只落接缝）。
  本轮确认/撤销的写入证据是**注入候选后走真实 UI**，不是模型产出。
- 未做"用户确认后自动再判 D-1c"的回路（门认证需 P0–P5 证据链，非本轨范围）。
- 面板不提供"改判到另一类"（supersede）入口：`ErrorCauseReviewService` 只做确认/撤销；
  改判仍走 `ErrorCauseRetractor.supersedeByUser`，无 UI。
- 设备库为共享 `emulator-5590`：注入行已删、app 已重启；未触碰用户其它数据。

## 5b 容量 UI（子线收口；产品面 item 29 / KD-68）

**终态：`ReviewCapacityPreview` 已挂进快照/复习页（今日容量、考试缺口、欠账、逐科 granted/unmet、
`intakeUnschedulableCount`），无数据为如实空态；KD-68 关闭；零调度器语义改动。**

### 5b-1 落点

- `core/domain/.../StudyReviewCapacity.kt`（新）：`StudyReviewCapacity` / `StudySubjectCapacity`
  —— core/data 预览的 domain 镜像（feature 不能依赖 core:data）。
- `StudyReviewOverview.capacity: StudyReviewCapacity? = null`：null = 本轮没有容量事实
  （窗口内无考试且积压追赶未规划），读面显示"今天没有临近的考试，暂无容量读数"，不报"0 题"。
- `StudySnapshotBuilder` 每次快照经 `StudyReviewPlannerService.reviewCapacityPreview` 只读派生；
  `reviewCapacityPreview` 增可选 `precomputedIntakeUnschedulableCount`（默认 null，既有调用逐字不变）。
- `feature/review/.../ReviewCapacityCopy.kt`（新）+ `ReviewRoute`：容量区块（今日容量/考试缺口/
  逐科/再平衡状态）+ KD-68 改口（可调度余量才进"每天约 X 题，约 M 天安排完"；超整日预算单列
  "需专项会话，不会进入每日计划"）。

### 5b-2 门禁读数（本轮当前树）

| 命令 | 读数 |
|---|---|
| `:core:data:testDebugUnitTest`（8 个定向容量/积压类，含新增 3 例） | 39 / 0 / 0 / 0 |
| `:feature:review:testDebugUnitTest`（整模块，含新增 `ReviewCapacityCopyTest` 10/10） | 116 / 0 / 0 / 0 |
| `:core:domain:testDebugUnitTest`（整模块） | 742 / 0 / 0 / 1 skipped（既有 `ReviewCalibrationExperimentTest`） |
| `am instrument …ReviewIntakeBacklogPreviewInstrumentedTest`（emulator-5590，strictOffline debug） | tests=4 failures=0 errors=0 skipped=0 → **PASS** |
| `am instrument …ReviewCapacitySectionInstrumentedTest`（同上） | tests=3 failures=0 errors=0 skipped=0 → **PASS** |

装置绑定：app APK sha256 `518e900e…`、test APK `c5d99a80…`、`boot_id=31ecd364…`。
先红后绿：实现前 `compileDebugUnitTestKotlin` exit=1（unresolved `capacity` / `StudyReviewCapacity`）；
**未跑** `connected*AndroidTest`（禁令），用裸 `am instrument` + recorder 落 XML。

### 5b-3 未做 / 未验证（如实）

- **`rebalanceEnabled` 生产恒 false**（设置无开关）：生产读面"积压追赶：本轮未规划"是常态，
  planned 分支只有 JVM 用例覆盖；按"不改调度器语义"未接线。
- **欠账口径登记**：`ReviewCapacityPreview.rebalanceBacklogCount` = `facts.size`（有记忆状态的
  题数，再平衡策略作用域），不是"逾期题数"；UI 沿用读取器 KDoc 的"积压"措辞。若将来启用
  rebalance，数据侧应补真正的逾期/可补读数（本轨不改数据语义）。
- UI 对可调度余量仍用 `NewIntroductionPolicy.preview` 的算术估算（快照不带逐题时长，
  `previewSchedulability` 的逐日模拟不可达）；KD-68 关闭条件是"不再承诺不可调度项"，已满足。
- 每次快照多一次考试表 + 预算读取（纯读，无写）；**未做基准**。
- 设备用例为 Compose 夹具渲染；未在设备上种考试日历走真实库快照（JVM 用例覆盖该接线）。
- 证据根：`docs/research/evidence/2026-11-09-batch-50/5b/`（README + red/green 日志 + 双 JSON/XML）。

## 6B-3 改卷 UI（产品面 item 30；自然语言入口 / 预览 diff / 采用-取消）子线

> 本节由 **6B-3 子线**追加；上方 3D / 3E / 切分 / MVA / 5c / 5b 节未改动。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/6b-3/`（README + red/green 日志 + JUnit/Android XML）。
> **不改门值；不改 `PaperEditModelPort` 契约；不改 `core:data` 派发预算。**

### 6B-3-0 落点与边界

- 卷面页 `MistakePaperEditRoute` 顶部新增 `AI 改卷面（一句话）` → 输入要求 → **生成修改预览**
  → 逐条 diff（`NativePaperEditDiff`：题目标签 / 变化 / 改前 / 改后）→ **采用到卷面 / 取消**。
- 详情页当前版本新增 `AI 改卷面` chip（历史版本不显示），导航到同一卷面页并展开 AI 区
  （路由 `mistake/paper-edit/{entryIds}?ai=1`）。
- 模型链路：`NativePaperEditModel.buildInput`（`MistakePdfPaperEdit → 有界 PaperEditInput`，图/URI/
  摘要结构上不可表达）→ `PaperEditModelPort.commandsFor`（app 装配
  `RepositoryPaperEditModelPort(application.modelTaskRepository)`）→ 白名单命令在**副本**上
  逐条应用；采用做 CAS（必须仍是生成预览的同一卷面实例）。
- 失败（端口失败/取消/未落终态、命令非法/越界、空命令、无实际变化、改完不可导出）一律
  `Rejected`：卷面与输入框里的要求都保留，只给本地可判定事实，不回显供应商正文、不摆"已改好"。
- 新增：`NativePaperEditModel.kt`、`NativePaperEditModelSection.kt`、`NativePaperEditModelTest.kt`
  （17 例）、`NativePaperEditModelInstrumentedTest.kt`（7 例）。最小 hook：
  `MistakePaperEditRoute.kt`、`MistakeDetailRoute.kt`（5c 在飞）、`SmartMistakeBookRoot.kt`（5c 在飞）。

### 6B-3-1 门禁读数（本轮当前树）

| 命令 | 读数 |
|---|---|
| `:feature:library:testDebugUnitTest --tests …NativePaperEditModelTest` | tests=17 failures=0 errors=0 skipped=0 → PASS |
| `:feature:library:testDebugUnitTest`（整模块 17 个 XML） | 108 / 0 / 0 / 0 → PASS |
| `:feature:library:connectedDebugAndroidTest`（过滤 `NativePaperEditModelInstrumentedTest` + `NativePaperEditRouteInstrumentedTest`；emulator-5590） | tests=17 failures=0 errors=0 skipped=0 → PASS（7 + 10） |
| `:app:compileLocalFirstDebugKotlin :app:compileStrictOfflineDebugKotlin` | BUILD SUCCESSFUL |

- **未用 `connectedLocalFirstDebugAndroidTest`**；用的是 feature:library 模块级 connected 任务。
- 先红后绿：实现前 `:feature:library:compileDebugUnitTestKotlin` exit=1，
  `Unresolved reference 'NativePaperEditModel' / 'NativePaperEditPreview' / 'NativePaperEditAdoption'`。
- 设备为 **emulator-5590（x86_64 / API 34 / Android 14，模拟器，非真机）**。

### 6B-3-2 未做 / 未验证（如实）

- **真实 Provider 的改卷回执质量 UNVERIFIED**：设备/JVM 全用 fake `PaperEditModelPort`；没有凭据、
  未发起真实外发。`RepositoryPaperEditModelPort` 真实链路只由既有 core:data 用例覆盖（本轨未重跑）。
- **app 级端到端导航 UNVERIFIED**：详情 chip 的 `?ai=1` 未在模拟器走完整 NavHost；路由模式与既有
  `Routes.ExportResults?exportId=…` 相同，模块级详情回调与卷面页已验证。
- **共享树**：`MistakeDetailRoute.kt` / `SmartMistakeBookRoot.kt` 是 5c 在飞文件，本轨只加最小 hook；
  若 5c 整文件回写，需重新应用本轨的参数 + chip + 导航（当前树编译绿，5c 的 `errorCausePanel` 改动仍在）。
- **端口应用逻辑两份实现**：`NativePaperEditModel.previewFor` 与 `feature:tutor` 的
  `TutorPaperEditApplier` 同一套白名单/卷面方法；后者 `internal` 且不产出 diff，本批不跨模块重构。
- 未跑 lint / detekt / 独立复核；未在真机验证。
- 证据根：`docs/research/evidence/2026-11-09-batch-50/6b-3/`（README + `jvm-green.log` +
  `module-unit-suite.log` + `instrumented-green.log` + 双 XML + `app-compile.log`）。
- 安全登记：`local.properties` 含明文 `SMB_REAL_PROVIDER_KEY`（gitignored）；本会话读取该文件时看到
  该行（未在结果/证据中输出），建议轮换并改为环境变量注入。

---

## JS 子线（G 产品面任务 31：math 降级 / KD-78 ①② / 生产装配核查）

> 本节由 **JS 子线**追加；上方 3D / 3E / 切分 / MVA / 5c / 5b / 6B-3 节未改动。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/js/`（README + 5 份日志 + 7 份 JUnit XML）。
> 计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md:64`（任务 31）。

**终态：math 盒回放按契约**显式产品降级**（先红后绿）；KD-78 ①② 核销、整条关闭；④ flags 现树复验；
生产 visualEngine 不注入、JS 不广告（MVA FAIL + KD-76 open）；KD-76/71/73/82 只登记、均保持 open。
门阈值零改动、断言零放宽、未提交。**

### 改动文件（最小 hunk）

- `core/data/.../visual/VisualDrawOpsPainter.kt`（math 诊断，`:194-202`）；
- `core/data/.../visual/VisualDrawOpsPainterPure.kt`（`PAINTER_LABEL_MATH` / `Math` DTO KDoc，`:53-62`/`:223-231`）；
- `core/data/.../visual/VisualMeasuredLayoutArtifact.kt`（`:32` 消费表）；
- `core/data/src/androidTest/.../VisualDrawOpsPainterInstrumentedTest.kt`（`:475-480` 断言）；
- `docs/current/visual-generation-contract.md` §5 第六条（math 显式降级）；
- `docs/known-defects.md`（KD-78 关闭 + KD-76/71/73/82 Progress 块）；
- 本记录 + 证据目录。

### 门判

| 项 | 结论 | 证据 |
|---|---|---|
| math 盒回放 | **显式产品降级**：`PAINTER_LABEL_MATH` fail-closed，诊断含 `explicitly degraded`；设备红→绿 | `js/red-math-degrade.log`、`js/green-math-degrade.log` |
| KD-78 ① stroke-only | **核销** | 生成门 `visual-compiler.js:892-897` / `VisualDrawOpsParser.kt:442-454`；Node 94/0；JVM 35/26/13 全绿；painter 24/24 |
| KD-78 ② dash 形状 | **核销** | 生成门 `visual-compiler.js:483-492` / `VisualDrawOpsParser.kt:559-567`；同上负向矩阵 |
| KD-78 ④ flags | **维持关闭**（现树复验） | painter 24/24（`:264`）+ 跨侧 flags `OK (2 tests)` |
| KD-78 整条 | **关闭** | ①②③④ 全满足；边界见 KD 块 |
| visualEngine 注入 | **未做（BLOCKED）** | MVA FAIL + KD-76 open + 无宿主帧源；3 参装配 `SmartMistakeBookApplication.kt:386-392` |
| capability 广告 | **保持不广告** | 生产 6 处能力集合全 `IMAGE_MODEL_ONLY`（`git grep` 见证据 README §4.3）；引用 MVA 证据 **P7**（`mva/README.md` §7「JS 不广告」） |
| KD-76 | open（最新 A6 NOT EVALUABLE / batch45 UNVERIFIED） | 证据 README §5 |
| KD-71 | open（batch40 3×27 绿，G4 未做） | 证据 README §5 |
| KD-73 | open（静态 0 unresolved / 23859 refs；设备/R8 半边未做） | `check_stdlib_compat.py` EXIT=0 |
| KD-82 | open（两广告面已 /4；JVM 7/7；差广告解冻裁定） | `ProgramSchemaMaxLengthAlignmentTest` 7/0 |

### 未做 / 未验证（如实）

- **未实现 math 盒回放**：artifact 不携 MathBox/原文、几何唯一来源约束（core/ui + core/export）、
  生产回放不构造该 DTO；本批按任务书"实现或明确降级"选后者。
- **未注入生产 visualEngine、未编造宿主帧源**：在 MVA FAIL/KD-76 open 下注入会把"能力误广告 ⇒
  执行未验收路径"从结构性不可达变成可达（详见证据 README §4.4）。
- **设备只到 emulator-5590（x86_64/API 34/4096）**，未跑 arm64/真机；设备日志为单轮实跑。
- **未跑 KD-76/71/73/82 的关闭门**（校准 / G4 变异 / R8 冒烟 / 广告解冻裁定）。
- 未跑 full soak、全模块单测、release 构建（属批次 50 其他子线）。
- 协作边界：开工/收工 `git status --porcelain` 实读——`core/data/.../visual` 两个 androidTest 文件
  （`StrictOfflineRelayoutDeviceVectorInstrumentedTest.kt`、`TutorVisualRebuildRestoreInstrumentedTest.kt`）
  与 `app/src/androidTest/.../visualjs` 三个未跟踪设备用例属他线在飞，本轨**未触碰**；`SmartMistakeBookApplication.kt`
  的 5c 改动保持原样。

---

## KD-67 第二道校准题 + 真 Room 端到端（产品面 32；子线收口）

> 本節由 **KD-67 第二题子线**追加；上方 3D/3E/切分/JS 各节未改动。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/kd67/`（README 为索引）。

**终态：候选池与单槽两处阻塞闭合；真 Room e2e 从"未跑"变为 `OK (3 tests)`（emulator-5590，APK
`ba005aa7…`）。零阈值改动、零断言放宽。第二道题的"生成 + 上屏 + 作答"仍无生产入口，不签
"第二道校准题已产品闭环"。**

### 落地（两条被终裁点名的阻塞逐条）

| 阻塞 | 闭合 | 文件 |
|---|---|---|
| 候选池只有会话题面（FOUNDATION），CALIBRATION 走廊无可选题面 | 装配器读两槽：`REVIEW`→`FOUNDATION`（逐字不变）、`CALIBRATION`→`AdaptiveTargetKind.CALIBRATION`；未核验的第二道题仍 `contentVerified=false` | `core/data/.../study/AdaptiveSelectionAssembler.kt` |
| `(会话, 知识点)` 单槽不允许第二道 | 题面行身份加槽位：`knowledge-quiz-item-calibration:<learner>:<会话>:<节点>`；第二道题题面 id `knowledge-quiz:calibration:<节点>`（与会话题面 id 不同）；store 槽位读/写/按题面 id 读；upgrader CAS 加 `slot`（默认 REVIEW）；repository 第二槽 persist/read/submit；writer 按题面 id 定位槽位 | `KnowledgeQuizItemStore.kt`、`KnowledgeQuizItemVerificationUpgrader.kt`、`KnowledgeQuizFeedbackWriter.kt`、`RoomBackedStudyExperienceRepository.kt`、`core/domain/.../StudyExperienceRepository.kt` |

### 验证（本会话实跑）

| 检查 | 命令（简） | 结果 |
|---|---|---|
| 行为红 | `:core:data:testDebugUnitTest --tests …KnowledgeCalibrationQuestionTest`（实现前） | 2 failed：候选只有 1 道；校准走廊 `CLARIFY_OR_CONFIRM` ≠ `ASK_CALIBRATION` |
| JVM 绿 | 同上（实现后） | `tests=5 / failures=0`（`kd67/jvm-…xml`） |
| 相关类回归 | 7 个类（assembler/writer/store/upgrader/audit/新类） | **55 / 0** |
| 全模块 | `:core:data:testDebugUnitTest` | **1707 / 4 failed / 2 skipped**；4 红与批次 49 基线逐字同组（`OpenAiCompatibleModelGatewayTest`×3 + `RoomBackedStudyExperienceRepositoryTest`），**无新增红** |
| 编译/feature | `:app:compileLocalFirstDebugKotlin` + `:feature:review:testDebugUnitTest` | BUILD SUCCESSFUL；feature:review **116 / 0** |
| 真 Room e2e | `adb … am instrument … KnowledgeCalibrationQuestionRoomE2eInstrumentedTest` | **`OK (3 tests)`** / 1.203s（`kd67/e2e-device.txt`） |

e2e 三条链（生产写路径、真 Room）：① 作答→用户断言→单列 CAS→**下次装配 contentVerified=true**；
② 第二道题独立槽 → 确认前必须 `CLARIFY_OR_CONFIRM` → 按自己的键判分 → CAS(calibration) →
`contentVerified=true` + `ASK_CALIBRATION` 选中第二道题（决策位点 ≥1，证明真投影）；
③ 反例：不带题面 id 的旧读口把第二道题误判 `ANSWER_KEY_VERSION_STALE`，带 id 才 `ANSWER_KEY_UNVERIFIED`。

### 未做 / 未验证（如实）

- **第二道题的生成/上屏/作答无生产入口**：屏幕仍只显示选择器决策文本；`KnowledgeReviewQuizLoader`
  无第二题入口；VM/Screen/Root 的 K5 动作未接（这些文件在批次 49/他线 M 面上，按协作规则不整文件抢占）。
- **Elo 选项数解析仍读会话题面槽**（`effectiveQuizStream`）：精度残余，不影响 fail-closed 与校准选择。
- **证据审计面**（`KnowledgeQuizEvidenceAudit`）仍把第二道题的证据映射到会话题面槽：只影响审计展示。
- **更正写口**在"题面 id 两槽都解析不到"时回落读会话题面槽（保持既有边界）。
- 设备仅 emulator-5590（x86_64/API34），未跑 UI 旅程/真机/soak。
- 协作边界：开工前目标 6 文件里仅 2 个为 M（批次 49 既有改动），只做最小追加；新增 2 个测试文件，
  未触碰 `feature/review`、`app` 实现文件。

---

## AIGC 覆盖（G 发布 · export/合规线）

> 本节由 **AIGC 子线**追加；上方各节未改动。证据根：`docs/research/evidence/2026-11-09-batch-50/aigc/`
> （`README.md` 有逐项命令/读数/APK sha）。设备 `emulator-5590`（x86_64/API34，非真机），HEAD `a87fc70d`。

**终态：五项全部有红→绿或"复核绿 + 历史红"证据；不伪造法规条款；备案号等外部项 BLOCKED。**

| 项 | 落点 | 红 | 绿 |
|---|---|---|---|
| ① 显式角标（UI/导出可见） | `core/ui/AiContentBadge.kt` 新增；`AttachedImageCard`/`GeneratedFigureCard` 卡头"AI 生成"；`TutorTurnContent` 回复尾部；PDF planner + DOCX 正文对模型文字加"本文件含 AI 生成内容" | `red-ui-app-labels.log`（4/4）、`red-ui-tutor-label.log`（1 失败）、`red-pdf-visible-notice.log`（1 失败） | `OK (4 tests)` / `OK (2 tests)` / BUILD SUCCESSFUL |
| ② DOCX 文档级标识 | `MinimalDocxWriter` 写 `docProps/custom.xml`（`property[@name=AIGC]/vt:lpwstr`，七键 JSON 同 `PdfAigcMetadata`）+ Content_Types Override + 包关系；`MistakeDocxExporter` 接同一代际哨兵 | `red-docx-docprops.log`（`docProps/custom.xml` 缺失） | `green-docx-docprops.log`；Python 独立读回 `docx-independent-readback.log` |
| ③ 模型改卷文本 KD-85 | `MistakePdfPaperEdit.modelTextApplied` + `toDraft()` OR；`TutorPaperEditApplier` 仅在**文本命令确实改变卷面**时置位；`GENERATION` 2→3；草稿 `FORMAT_VERSION` 2→3 | `red-kd85-model-text.log`（2/5） | `5/5`；设备 `OK (2 tests)`；pypdf 读回 `Label=1` |
| ④ 部分页打印 KD-84 | 批次 47 已修（`writeSubset` 缓冲 + `relabelSubset`），本批只复核 | 历史 `h-061-red-b47.log`（**未回退重演**） | `green-device-kd84-print-subset.log` `OK (7 tests)`；pypdf 子集 `/AIGC` 在 |
| ⑤ 材料 AI 整理 KD-29 | 两张材料卡（开场重教/前置补救）加"AI 整理"角标 + 一句说明；`core:ui` 常量单源 | 同 `red-ui-app-labels.log` | 同 `OK (4 tests)` |

**判据/法规口径**：DOCX 依据 **TC260-PG-20258A §6.1**（本批从官方 PDF 重取，sha256 `6bf6c118…3b0e`，
原文"存储路径为 docProps/custom.xml … name 为 AIGC … lpwstr 值为 json 字符串"）；PDF 沿用 §6.3。
未引入任何未见诸官方文本的条款。

**JVM 回归**：core:model 674/0、core:export 178/0、feature:review 116/0；feature:tutor 266/1——
唯一红是既有 `TutorExportSheetTest`（HEAD 文案"不含作答区" vs 用例"作答空白区"，两文件均非本线改动）。

**合规门**：`h_compliance_gate --all` → `H-052/H-061/H-062 PASS`；`H-053 FAIL（占位符，外部 BLOCKED）`。
`fix_relapse_gate --gate FIX-T04/FIX-T05/FIX-T07` → **3/3 PASS**，T05/T07 `--prove-red` **2/2 OK**（门升级为
"关闭态 + 设备/独立读回证据"，未放宽）；新增自测 `tools/tests/test_fix_relapse_gate_aigc_closed_state.py` 6 passed。
**冲突登记**：`tools/tests/test_fix_relapse_gate.py` 为他线**未跟踪**文件，本线未改；其 5 failed 中 T02/T04
为既有（`SecondaryScreens.kt` 仍拼接 `${failure.message`；T04 fixture 缺证据日志），T05/T07 为门升级后 fixture
缺证据目录（由新增自测覆盖）。

**未做**：Word/WPS 实开验证、UI 截图/TalkBack 视觉复核、真机/soak/release、KD-84 红态重演、
备案号/上线编号回填。详见证据 README §未做。

**改动文件**：
- 生产码：`core/ui/.../AiContentBadge.kt`（新）、`AttachedImageCard.kt`；`core/export/.../AiGeneratedDocumentNotice.kt`（新）、`MinimalDocxWriter.kt`、`DocxMistakeRenderer.kt`、`MistakeDocxExporter.kt`、`MistakePdfPagePlanner.kt`、`MistakePdfPaperEdit.kt`、`MistakePaperDraftStore.kt`；`core/model/.../PdfAigcContentPolicy.kt`（GENERATION 一行）；`feature/tutor/.../TutorPaperEditApplier.kt`、`TutorGeneratedTurn.kt`；`feature/review/.../ReviewSessionScreen.kt`。
- 用例：`DocxAigcDocumentLabelTest`（新）、`MistakePdfAiGeneratedNoticeTest`（新）、`MistakePdfPaperEditAigcInstrumentedTest`（新）、`TutorPaperEditModelTextTest`（新）、`TutorAiGeneratedLabelInstrumentedTest`（新）、`AiContentLabelsInstrumentedTest`（新）、`KnowledgeMaterialContentAttributeTest`（新）；`MistakePdfExporterAigcJvmTest` 代际断言 2→3。
- 文档：`docs/known-defects.md`（KD-29 fixed、KD-85 closed）、`docs/ai-content-disclosure-copy.md` 现状段与 §3/§4。

---

## 隐私备案（发布 · 隐私政策页 / data safety 清单 / 备案号占位）

> 本节由 **隐私备案子线**追加；上方各节未改动。证据根：`docs/research/evidence/2026-11-09-batch-50/privacy/`
> （README 有逐项命令/读数/APK sha）。设备 `emulator-5590`（x86_64/API34，非真机），HEAD `a87fc70d`。
> 计划行：任务 34（隐私政策/备案页 + data safety 清单，备案号外部占位）。

**终态：应用内《隐私政策》全文页落地（9 章，版本 2026-11-08），备案号/运营主体/联系方式如实写"尚未取得"
（零编造，用例反伪造扫描）；data safety 逐题清单落盘（事实层带证据、建议答案标"待后台核对"）；
H-025 由 FAIL 转 PASS；H-024（公开 URL）/H-036（Play 表单）仍 BLOCKED。未改任何门值/阈值/断言。**

### 门判据实测

| 检查 | 命令 | 读数 |
|---|---|---|
| 隐私 H 域（基线） | `python -I tools/ci/h_compliance_gate.py --topic privacy` | 7 PASS / **H-024、H-025 FAIL** |
| 隐私 H 域（收口） | 同上 | **8 PASS / H-024 FAIL（外部）**；H-025 转 PASS，无回退 |
| data safety H 域 | `python -I tools/ci/h_compliance_gate.py --topic data-safety` | 8 PASS / **H-036 FAIL（外部）**，与基线一致 |
| 文案 JVM | `:app:testLocalFirstDebugUnitTest --tests …PrivacyPolicyCopyTest` | 红=编译失败（`Unresolved reference`×12）；绿=**8/8, failures 0** |
| 全 app 单测 | `:app:testLocalFirstDebugUnitTest` | **198 / 2 failed**（见下"未做"） |
| 离线 flavor | `:app:compileStrictOfflineDebugKotlin` | BUILD SUCCESSFUL |

**H-025 的实质**：`docs/ai-content-disclosure-copy.md` §3 第 5 条要求家长"阅读《隐私政策》中未成年人个人信息
保护章节"，但隐私文本里此前**没有"未成年人"任何字样**——引用是悬空的。本线在
`docs/current/privacy-and-data-safety.md` 增"未成年人个人信息保护"章（PIPL 第三十一条：不满 14 周岁须监护人同意；
监护人可拒绝/撤回；遥测侧年龄门 fail-closed），判定随之转绿。

**门脚本口径**：本线**未改** `tools/ci/h_compliance_gate.py`。该文件开工时已是 **M**（AIGC 线的 H-018/H-061/H-062/H-066
升级），`git diff` 的 hunk 落在 76/296–305/626/631/656 行，**H-019…H-036 逐字未改**——上表隐私/data-safety 读数
建立在未改动的断言上。

### 设备实测（APK `43f1fe46…`）

我的 → 数据与隐私 →「查看隐私政策全文」→ 9 章全部上屏；末章三行均为"尚未取得"；全 UI dump 扫
`ICP备\d+` 与 8 位以上连续数字 **零命中**；系统返回键 policy→摘要→我的（首轮实测返回键会越过摘要直接弹回
"我的"，故补 `BackHandler`，属真实修复非"更完备"）。截图/dump 见证据目录 `03`–`05`、`back-to-summary-ui-dump.xml`。

### 改动文件（追加式，不抢占他线）

- 新增 `app/src/main/kotlin/com/tingyun/smartmistakebook/PrivacyPolicyCopy.kt`（政策唯一文案源 + 三处外部占位常量）
- 新增 `app/src/test/kotlin/com/tingyun/smartmistakebook/PrivacyPolicyCopyTest.kt`（8 条：缺章/空段、占位必须说"尚未取得"、反伪造号码、清单与删除范围）
- 追加 `app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt`（`git diff --stat` = **66 insertions(+), 0 deletions**；入口按钮 + 政策页 + BackHandler；开工时该文件干净）
- 新增 `docs/current/play-data-safety-answers.md`（data safety 逐题清单：12 条代码事实 + 建议答案 + 6 项外部 BLOCKED 映射）
- 修改 `docs/current/privacy-and-data-safety.md`（政策镜像 + 未成年人专章 + 外部项表；保留 H 门三处英文标题字面，含大小写 `Play Data safety answers`）
- 未触碰：`SmartMistakeBookRoot.kt`、`ProfileRoute`、`AndroidManifest.xml`、门脚本、`docs/current/README.md`

### 未做 / 未验证（如实）

- **H-024 公开 URL**：无域名/托管，未做外链（不跳不存在的地址）。
- **H-036 Play 表单**：未登录未提交；`developer.android.com` / `support.google.com` 本次 `WebFetch` 均超时，
  仅用搜索摘要确认表单结构，**数据类别字段名与 collected/shared 判定未逐字核对** → 清单 §2 标"待后台核对"。
- **备案号/主体/联系方式/模型备案号**：6 项全部 BLOCKED，代码留空 + "尚未取得"。
- 全 app 单测 2 红：`StorageRestoreFeedbackTest`（`不得回显 content:// URI`，目标函数在 `SecondaryScreens.kt:1508`，
  本线 hunk 仅 3 处均不触及）与 `TutorSettlementNoticeTest`（生产文件未改）。**未在干净 HEAD 树重演**，只记"与本线 diff 无交集"。
- strictOffline 只编译未上设备；真机/TalkBack/字号对比度/深浅色未跑；英文政策未产出（H-081）。
- `H-075` 的 `Text("` 计数 358→**359**（本线新增 1 行字面量）；该断言改动前即 FAIL，判定未变。

## 发布线（计划 item 35–37：versionCode 注入 / 本地 release key / APK·AAB 校验 / mapping）

- 证据目录：`docs/research/evidence/2026-11-09-batch-50/release/`（主文件 `release-50.md` + `release-gates-summary.json` + 原始工具输出）
- 基线：`HEAD=a87fc70d15c8ad726cee17422dbeef0b4977747d`；**构建时树脏**：`git status --porcelain` 开工 683 → 构建启动 684 行、`-uall` 4314 路径（写盘后 4315）。
  ⇒ 本轮产物**不是干净树产物**，`apk` 内嵌 `revision=HEAD` 只是 AGP 记的 HEAD sha，**不代表所编代码**；主会话提交后须复跑（见证据 §8.2）。

### 落地（每条都实跑过）

| 项 | 结果 |
|---|---|
| versionCode 注入 | `app/build.gradle.kts:20-21,43`：`N = 100000 + git rev-list --count HEAD`（本轮 1160 → **101160**）经 `-Psmbk.versionCode` 注入；debug 缺省仍 2 |
| git 算号器 | 新文件 `tools/ci/release_version_code.py`：实跑 `--root .` → `{"commit_count":1160,"shallow":false,"version_code":101160}`；shallow 仓库拒号；单测 4 passed（`docs/research/evidence/2026-11-09-batch-50/release/version-code-derivation.json`） |
| fail-closed 门 | `app/build.gradle.kts:172-188` 新增 `validateVersionCode`（与 `validateReleaseSigning` 同形、同挂法 `:190-197`）：缺参/非整数/越界 → exit 1，实跑三种负控全红、`101160` 绿 |
| verifier 兼容 | `tools/ci/j_release_verify.py:645-653,708-744` 加 `--expected-code`（注入形态不给期望值即红）；`tools/tests/test_j_release_verify.py` +3 用例：改前 2 failed → 改后 29 passed |
| AAB versionCode 读数 | 新文件 `tools/ci/aab_manifest_version.py`（aapt2/apkanalyzer 读不了 AAB、本机无 bundletool）：两 flavor AAB 自身 proto manifest 读出 `101160`（string 与 compiled int 一致）；单测 7 passed |
| 本地 release key | `.tmp/release-signing/smbk-local-release.keystore`（PKCS12/RSA4096/10000 天），DN `OU=NOT-PLAY-PRODUCTION`，SHA-256 `2c8415b2…5eba`；`git check-ignore` 命中 `.gitignore:9:.tmp/`，`git status` 0 条，仓内被跟踪 keystore 0 |
| 出包 | 一次串行 Gradle：`assemble+bundle` × (strictOffline, localFirst)，`-Psmbk.versionCode=101160` → **BUILD SUCCESSFUL 4m56s**，481 tasks |
| APK 验签 | v1+v2 true、v3/v4 false；DN/指纹 = keystore（两 flavor） |
| AAB 验签 | `jarsigner -verify` exit 0；`keytool -printcert` 指纹同 APK；`signing` 门 exit 0（APK/AAB 同身份、证书未过期、四 env 齐、0 tracked keystore） |
| 16KB | `zipalign -v -c -P 16 4` → `Verification successful`；`apk16k` 14/14 `.so` STORED 且 `offset%16384=0`、`elf_errors: []`；`aab16k` `alignment: 2`、ABI `{arm64-v8a,x86_64}`、7 库全在（两 flavor） |
| versionCode 三处一致 | 合并 manifest `101160` / APK `aapt2 badging 101160` / AAB proto manifest `101160`（两 flavor） |
| mapping 保留 | `mapping.txt` 140,341,720 B、968,647 行、R8 9.3.16；**与 AAB 内 `BUNDLE-METADATA/.../proguard.map` 逐字节同 sha** `ce595aa0…277e`；`r8` 门 exit 0（classes 10661 / renamed 10461 / removed 1199 / negative_control_hits 0） |

### 负控（门会红）

期望号 101161 → `version` 1 / `aab_manifest_version` 1；`apk16k --selftest` → `ok:true` + 3 条 `p_align=0x1000`；debug APK vs release AAB → `signing` 1（`identity differs`）；无 env → `validateReleaseSigning` 1。

### 未做 / 未验证（如实）

- **干净树 release 构建：BLOCKED（未做）**——`identity --require-clean` 实跑 exit 1（`dirty_paths: 4315`）。主会话提交后按证据 §8.2 复跑；不得把本轮的包当"HEAD 字节复现"。
- `bundletool dump config` 官方 AAB 交叉验证 NOT RUN（无可执行 fat jar）；16KB 运行时 NOT RUN（唯一设备 4096 页、x86_64、API 34）；无 arm64 真机。
- Play 生产签名 / App Signing 决策 / 上传证书指纹 / 备案号 / Play 账号 / 隐私政策 URL：外部 BLOCKED，未伪造（本地 key 明确 `NOT-PLAY-PRODUCTION`）。
- `aab16k --selftest` 不存在（R7 U-10 缺口，本轮未补）；成对 vc（N/N+1）升级/降级产物未做。
- `versionName` 仍字面量 `0.2.0`（本轮只注入 versionCode）；下次发版 versionCode 必须 `> 101160`。

### 改动文件

`app/build.gradle.kts`（+32/−1）；`tools/ci/j_release_verify.py` 与 `tools/tests/test_j_release_verify.py`（两者本就 `??` 未跟踪，最小 hook）；新增 `tools/ci/aab_manifest_version.py`、`tools/tests/test_aab_manifest_version.py`、`tools/ci/release_version_code.py`、`tools/tests/test_release_version_code.py`；新增本目录证据。未触碰 `settings.gradle.kts`/`gradle.properties`/`libs.versions.toml`/`.gitignore`/`feature/library/build.gradle.kts`（他线 M）/`docs/current/*`。

---

## 设备全量轨（计划 item 10 + 验收 39：设备矩阵 / 产品面 UI / release 冒烟 / 2h soak）

> 证据根：`docs/research/evidence/2026-11-09-batch-50/device/`（`README.md` 逐项命令/读数/APK sha，
> `summary.json` 机器摘要）。设备 `emulator-5590`（x86_64 / API 34 / Android 14 / 4096 页，
> **模拟器，非真机**）。全部走裸 `adb install` + `am instrument`；**未跑** `connected*AndroidTest`（禁令）。

**绑定（如实）**：HEAD `a87fc70d` + **脏工作树**（开工 `git status --porcelain` 688 行 / `-uall` 4336 路径；
tracked diff sha `8992552b…`）；app debug `43f1fe46…`、core:data test `0013cf37…`、core:database test
`db14c022…`、app test `2e32e085…`、feature:library test `14cdea1d…`、core:export test `07c71394…`、
release `1bf98b00…`（与 release 证据同值）。**收工时开工 diff 指纹不能复现**（排除本轨 3 处测试改动后为
`1467713e…`，status 688→690 = 688+3−1）；按 mtime 核查 `app|core|feature` 源树的 `.kt/.kts` 与资源在快照后
零改动（只有本轨 4 个测试文件，1 个 revert），故二进制不受影响，但**未能定位**被回写的具体路径。
⇒ 绑定以 HEAD + APK sha 为权威，diff 指纹不作可复现证据。

### 一屏读数

| 项 | 结果 |
|---|---|
| 迁移 1..75（`FullMigrationMatrixInstrumentedTest` 20 例） | 首跑 **19/20**（1 例旧列清单）→ 同步实体 → 单例绿 → **整类 OK (20 tests)** |
| 备份 34 / trimmer 10 / rowCounts 2 | **OK (34)** / **OK (10)** / **OK (2)**（同一最终 test APK `0013cf37…`） |
| drill 6 例 | 3 mask 首跑红（settings 六键白名单 vs 生产七键）→ 同步 → **OK (3)**；corrupted ✓；kill-stage 设计性 `Process crashed`；restore-handoff **OK (1)** |
| KD-67 真 Room e2e 3 例 | **OK (3 tests)**（第二道题的生成/上屏/作答 UI 无生产入口，未验） |
| 6B-3 改卷 UI 17 + 5c 错因面板 7 | **OK (24 tests)**；真实 app `AI 改卷面` chip / AI 区可见 |
| 5c 真库写入接缝 1 + JS painter 24 | **OK (25 tests)** |
| 5b 容量 / 新题预览 / AIGC 标签 / 能力屏 17 | 首跑 15/17（能力屏整串匹配红 2）→ 修 → **OK (17 tests)**；真实复习页容量空态 + 今日预览可见 |
| AIGC 导出（`core:export` 全模块） | **OK (32 tests)** |
| AIGC 导出 UI（delivery 2 + notifications 2 + flags 2） | 绿；notifications 拒绝态需先 `pm revoke` + `-e smb_notifications_denied=true` |
| AIGC 导出 background 2 例 | **FAIL（未转绿）**：见下"未验证" |
| JS 是否广告 | **不广告**（生产 6 处 `IMAGE_MODEL_ONLY`，无 JS 能力串；三参装配无引擎回 UNAVAILABLE） |
| release APK 安装冒烟 | `versionCode=101160` / `0.2.0-localfirst`；四 tab 渲染；`FATAL EXCEPTION` 计数 **0** |
| 2h soak（release APK） | **PASS**：7200s / 115 轮 / `crash_delta=0` / `dropbox_delta=0` / monkey 全 0 / pid 未变；TOTAL PSS 64.7→100.6 MB |

### 三处测试侧最小 hook（先红后绿，未放宽断言）

1. `FullMigrationMatrixInstrumentedTest.kt`（+5）：`mistake_export_record` 期望列清单停在 v61 十列，
   但 `75.json` 与 `MistakeExportRecordEntity` 已是十二列（多 `group_id`/`artifact_role`，T10 组导出）→
   补列；等值关系不变。
2. `DeviceMigrationDrillInstrumentedTest.kt`（+8/−4，该文件开工时已是他线 `M`）：`settings.json`
   白名单停在 v1 六键，生产 `SmbkSettingsDocumentV1`（schema 2）已写 `dailyReviewMinutes`
   （`AndroidBackupRepository.kt:297`）→ 补键并改"七键"文案；白名单仍是精确集合。
3. `CapabilityScreenInstrumentedTest.kt`（+14/−3）：生产文案在同一 `Text` 追加
   `editCapabilityDescription()`（`SecondaryScreens.kt:599`，T4 起恒非空），整串等值匹配永远找不到 →
   改 `substring = true` 并抽常量；仍要求"测试通过"主句上屏。

> 另有一次实验（给 `MistakeExportBackgroundInstrumentedTest` 加滚动 helper）**已 revert**，
> 该文件 `git status` 0 行；实验日志保留在 device/`43`/`46`。

### 未做 / 未验证（如实）

- **AIGC 导出 background 2 例 FAIL**：`waitForTag("library_item_entry:m1:closed-interval-extrema")`
  20s 超时。该条目在库（`sqlite3` 实查 ACTIVE）、真实 app 手工上滑到底**能看到**该卡片
  （device/`44-ui-library-bottom.png`）；把等待改成 `performScrollToNode` + 立刻点击仍红（`43`/`46`）
  ⇒ 属 Compose 测试与分页懒列表的时序/力学问题，**不是**"导出随页面丢"被证伪；该 claim 本轨 **UNVERIFIED**。
- 未上真机；无 arm64；16KB 运行时未验（4096 页/x86_64）。
- 5c 确认/撤销的注入走查未复跑（5c 线自有证据）；KD-67 第二道题无 UI 入口。
- AIGC 真实文件在设备第三方阅读器打开未验；真实 Provider 改卷回执未验（未调用真实 Provider）。
- 2h soak 只有"release + 空库"组合（debug→release 签名不同必须 uninstall；原 7 条库已备份到
  `.tmp/device-backup-2150/`，`run-as` 无法回灌 release）。
- 设备库在恢复 debug 后含测试 `@Before` 播种夹具（`entry:m1:*`），未再清理。

---

## 验收台账收口（批次 50；验收台账员）

> 本节由**验收台账子线**追加；上方各节未改动。证据根：
> `docs/acceptance/evidence/2026-11-09-batch-50/`（logs/junit/device/gate + 三份清单）。
> 台账：`docs/acceptance/ledger.md`；终裁投影：`docs/acceptance/verdicts.md`、`domain-index.md`。

### 终态

**台账 1200 行：PASS 933 / FAIL 13 / BLOCKED 254 / UNVERIFIED 0**（批49 终态 900/22/278/0）。
台账 sha256 `9044a7794dda196c6fc22c8e989a33099482ef26399e13ffbcbe28c0f045cb97`；
run-id `2026-11-09T00:00:00Z-batch50-ledger`。**没有新增门/阈值/断言；没有把 3D/3E/MVA/AUD-08 的门红洗成绿。**

### 新增 PASS（33 行）

| 域 | 行 | 依据 |
|---|---|---|
| H | H-008/H-018/H-025/H-043/H-062 | AIGC 显式角标/隐式文档级标识/KD-85 水印 + 隐私未成年人专章；H 门 `--only` exit 0 + 设备 6/9 例 + pypdf/Python 独立读回 |
| FIX | FIX-016..020、021..025、031..035、081..085 | FIX-T04/T05/T07/T17 关闭态门 3/3 + T17 1/1；prove-red 2/2；设备/独立读回/红绿日志 |
| J | J-022/J-024/J-025/J-027/J-033/J-034/J-041/J-051 | 缺签名变量负控 exit 1、APK/AAB 同身份、证书有效期、文档契约、AAB ABI/quickjs/无 32 位、versionCode 注入 |

### 证据换绑（verdict 未变）

- **B 001..072/073..090（除 077/078）**：换绑 schema 75 设备矩阵（迁移 1..75 20 例、备份 34、
  trimmer 10、rowCounts 2、drill 4 绿；设备 JSON + 原始日志）；旧 v73 batch-44 证据只作历史。
- A-028/A-037：5c/6B-3/5b/AIGC 导出设备走查；E-017/E-065/E-070/E-075/E-080/E-081：3E/5b/5c/KD-67；
  F-055/F-059/F-063：批次50 release APK 2h soak；GATE-091/092/099：批次50 切分门读数（维持 FAIL）。
- **29 份复核的源文件 sha 因批次50 AIGC/隐私/JS 改动漂移**，按当前字节重算 `file_sha256/witness_source`；
  这是**证据字节复算**，不是语义重判。另因批次50 新增文件，24 份新 PASS 复核从零生成；
  9 份原 FAIL 复核按新判词重建（H-018/H-062/J-024/J-025/J-027/J-033/J-034/J-041/J-051）。

### 实跑门禁（本会话）

```text
python -I tools/ci/acceptance_report.py --out build/b50-ledger/report-final.json
  exit 1; counts=933/13/254/0; violations=0; pass_with_review=933/933;
  pass_without_review=0; pass_with_invalid_review=0; pass_with_row_specific_review=478
python -I tools/ci/acceptance_report.py --require-complete
  exit 1; NOT_ACCEPTANCE_COMPLETE + REVIEW_CHECKS_SHARED
python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md
  exit 1; violations=162（全部 EVIDENCE_REUSED；无其他违规）
python -I tools/ci/acceptance_lint.py ... --warn-only   exit 0
python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md
  exit 0; gaps=0
python -I tools/ci/acceptance_init.py --check           exit 0
blocked-runbook.md 载体覆盖 254/254；runbook.md 默认载体 85/254（载体错位已登记）
```

哈希清单（`sha256sum -c` 实测）：`REVIEWS.sha256` **947/947 OK**（946 行级 JSON + schema）；
`GATES.sha256` **35/35 OK**（gate/logs/junit/device + 生成器，不含两份清单自身）；
`EVIDENCE.sha256` **673/673 OK**（排除 `.mimosa`）。
清单自身 sha256：`REVIEWS` `0881a961…ef4b1`、`GATES` `73ad608a…8627e`、`EVIDENCE` `953f1ebb…48537d`。

### 未做 / 未验证（如实）

- **3D D-1 仍 FAIL**（`d1/stats.json`：normal 层 Wilson 下界 `0.8979<0.90`，`overall_pass=false`）；
  主判据 0.9757 未过 κ 前置（NOT RUN），只可读作 model-labeled 暂定值，不得当门值引用；
  D-2 两项退休未执行、容量补丁未应用；D-3 117/129 保持。台账没有 3D 专属行，本轮未把 D-1 写成通过。
- **3E 条件性通过**：第二档无预留份额（默认 20 分钟预算下可被今日档占满），产品裁定留给主会话。
  **四门汇总不得把它计为无条件 PASS**：默认 20 分钟 = 6 个知识槽，今日档占满且分数更高时
  第二档贡献为 0；"能捞回"只在 45 分钟夹具成立（`3e/README.md §5 R1`）。
- **MVA 门不可评**：零生图/零真实 Provider/零 VLM judge；120 题质量结论不存在；JS 不广告保持。
  旧门 `critical_mismatch_accepted_zero=true` 是 `figure_eval_gate.py:980` 常量 false 读出的恒真值，
  已加 `citable=false` 机器标记，任何引用该 0 值的文本无效。
- **AUD-08 FAIL**：① 7/24=0.2917 <0.9；③ 0.0128≤0.05 但硬红线 fragmentedQuestions=1≠0；
  acceptance 8 页**在本批**只运行一次（唯一 run），但同一 8 页已在批次 48 消费，
  held-out 不可恢复（本批是第二次观测）——见 `split-gate/SCOPE-CORRECTION.md`。
- **发布外部项**：干净树构建、Play 生产签名/指纹、Play 账号/备案号/隐私政策 URL、arm64/16KB 运行时
  仍 BLOCKED/FAIL；本地 release key 明示 `NOT-PLAY-PRODUCTION`。
- **F-057 维持 FAIL**：批次50 2h soak 7200s/115 轮未命中超限路径（FATAL=0）；未命中不等于通过。
- **设备仅 emulator-5590（x86_64/API34，非真机）**；未跑真机/arm64/2h 重复窗。
- **行级复核是机械复算**（reviewer=`batch50-acceptance-ledger-mechanical`，`independent_agent=false`），
  不是第二人/第二模型语义复核；`pass_with_row_specific_review` 655/933（shared checks 20 组/202 行，
  主要来自 B 迁移矩阵与 FIX 修复单的组级证据）。
- 改动 `FIX-020/025/035/085` 的检查文本：由「不可修登记（登记 + 复发门在位）」改为「复发门在位
  （Txx 关闭态）」，因为缺陷已修、原文前提不再成立；门本身未放宽。
- **FIX-016 红态为批次47历史日志，本批未回退重演**；PASS 依赖历史红日志 + 当前关闭态门，已在行级 JSON
  notes 标注。

---

## 修复环（批次 50；只修高/中；2026-11-09）

> 详细逐条处置、前后对照与命令读数：`docs/research/evidence/2026-11-09-batch-50/repair/REPAIR-RING.md`；
> 修复前红读数：`repair/red-before.txt`；不能修的项：`repair/BLOCKED.md`。

**已修（口径/证据/标注，门值与失败结论均不动）**

1. **AUD-08 acceptance 一次性口径**：范围文案改为"本批只运行一次 + 同一 8 页已在批次 48 的
   `acceptance-once-01` 消费、held-out 不可恢复"；原 `gate-report-b50.json` / `gate-evidence-b50.md`
   保持原字节，取代声明见 `split-gate/SCOPE-CORRECTION.md`。门仍 FAIL（① 7/24、③ 1/78、frag=1），
   **未重跑 acceptance**。
2. **D-1 抽样框漂移**：`build/kb-staging/overfull_nodes.csv` 抽样后（14:30）被 D-2 于 15:20 原地重写
   （`fce8269c…`/63,102 行/2,482 节点 → `ce7d6823…`/62,944/2,481）。新增 `d1/frame_drift_check.py`
   → `frame-drift.json`/`FRAME-DRIFT.md`：`--check` 真实为 match=false/exit 2；1,600 条样本层归属
   **0 条不一致**；按当前 N 主判据 0.9756882（PASS）、normal 护栏 0.8979148（FAIL）；重抽 879/1600
   键不同。**结论不变，但"可复放"不再成立**；REPORT 的 match:true 已改为真实读数。
3. **κ 前置**：D-1 主判据 0.9757 的判定一致性 κ = NOT RUN，按 R2 §4.5 只能作 **model-labeled
   暂定值**、不得用于门——已写入本节 3D/D-1 口径块、5c 引用、终裁、`verdicts.md` 判词/不可变项
   与生成器。
4. **Wilson 护栏口径敏感性**：预注册 no-FPC 0.8979148（FAIL）vs FPC 0.9005766（抽样 N）/
   0.9005708（当前 N）（PASS）；`overall_pass=false` 按预注册口径成立，差异并列披露。
5. **MVA 旧门恒真字段**：`figure_eval_gate.py` 的 `machinery_check.critical_error_evidence`
   标 `citable=false` + reason，`:980` 注释"不可引用为门证据"；MVA 门仍 FAIL/NOT_EVALUABLE。
6. **soak PASS 边界**：`device/README.md §6` 写明 PASS 只等价于"无新增 crash/dropbox + monkey 全 0"，
   无 ANR 采样、无内存/电池阈值。

**阻塞（不能在本环修，已登记）**：5c 生产候选生产者（缺结构化 trace 采集 + 产品裁定）；
3E 第二档预留份额（改产品语义，留主会话）；JS 生产装配（他线在飞 + MVA FAIL/KD-76）；
干净树 release（须先提交）；Play 签名/账号/备案号（外部）。四门汇总**不得**把 3E 计无条件 PASS、
不得把 D-1 的 0.9757 当可用门值。

**验证**：`pytest tools/tests/test_batch50_repair_invariants.py -q` → 10 passed；
与既有套件合跑 71 passed；`tools/figure_eval/test_figure_eval_gate.py + test_mva_gate.py` → 46 passed；
`d1/frame_drift_check.py` → exit 0；`d1/recompute.py` → all_match=true；
`d1/sample_d1.py --check` → exit 2（如实红）。未跑 Gradle/设备（本环未改 Kotlin 生产码）。

---

# 批次 50 收口记录（§0–§8，2026-11-09，记录员）

> 本节在全部子线收口后追加；**上方各子线节原文未改**。子线节保留过程与一手读数，本节做跨线对账与终态。
> 计划：`.zcode/plans/plan-2026-11-09-batch-50-release.md`（89 行，已完整读取）。
> 基线：`docs/research/2026-11-09-batch-50-baseline.md`（开工 HEAD `a87fc70d`、581 脏路径、6 处先认账）。
> 终裁：`docs/acceptance/adjudication.md` §「批次 50 报告重生成终裁」B50-0…B50-6。
> 证据根：`docs/research/evidence/2026-11-09-batch-50/`（`EVIDENCE.sha256` 673 项）与
> `docs/acceptance/evidence/2026-11-09-batch-50/`（`REVIEWS.sha256` 947 项、`GATES.sha256` 35 项）。
> 本文件结构：§0 口径 → §1 阶段对账 → §2 四门结果 → §3 产品面 → §4 发布 → §5 门禁/设备 → §6 复核/修复 → §7 遗留 → §8 提交清单。

---

## 0. 口径与声明

### 0.1 判定与四值

- **verdict 四值**：PASS=行判据在当前证据下成立；FAIL=判据不成立且缺陷已登记；BLOCKED=解除条件缺失（**不是通过**）；UNVERIFIED=本环境无法复核（本批 UNVERIFIED=0）。
- **四门的结论必须按门定义读**：3D `overall_pass=false`（主判据 PASS/护栏 FAIL）；3E `PASS_WITH_FLAGGED_LIMITATION`（条件性，非无条件 PASS）；MVA `NOT_EVALUABLE`（不可评，不是 0%）；AUD-08 `FAIL`。**不得把四门合并写成"3D/3E 通过、MVA 未评、AUD-08 失败"之类的强口径**，也不得把 3E 计为无条件通过。
- **本批未改任何门阈值**：3D 0.95/0.90、3E 每日名额 10（`KnowledgeReviewFullLibraryScan.kt:30`）、MVA 90/95/90/0、AUD-08 0.9/0.05/切残硬红线 0。修复环与终裁只改标注/口径，不改门值（`repair/REPAIR-RING.md §2`）。

### 0.2 终态台账与迁移

| 量 | 批次 49 终态 | 批次 50 终态 | 变化 |
|---|---:|---:|---|
| PASS | 900 | **933** | +33 |
| FAIL | 22 | **13** | −9 |
| BLOCKED | 278 | **254** | −24 |
| UNVERIFIED | 0 | 0 | — |
| 合计 | 1200 | 1200 | 0 |

- 台账：`docs/acceptance/ledger.md`，sha256 `9044a7794dda196c6fc22c8e989a33099482ef26399e13ffbcbe28c0f045cb97`（本记录员本轮实测；mtime 01:05，未被我改动）。
- 13 FAIL 逐行（本记录员按台账复算）：**F-057**；**J-001/J-002/J-020/J-021/J-048/J-052**；**GATE-032/GATE-038/GATE-042/GATE-091/GATE-092/GATE-099**。域计数：F 1、J 6、GATE 6。
- 254 BLOCKED 域分布（本记录员复算）：A 25、B 2、C 2、D 8、E 9、F 32、G 7、H 16、I 37、J 47、FIX 65、GATE 4（合计 254）。
- 新增 PASS 33 行由验收轨自报：H-008/H-018/H-025/H-043/H-062；FIX-016…020、021…025、031…035、081…085（20 行）；J-022/J-024/J-025/J-027/J-033/J-034/J-041/J-051（8 行）。

### 0.3 四门与发布口径（引用时必须带）

1. **3D D-1 = model-labeled 暂定值**：标注为单一模型（`deepseek/deepseek-flash (ZCode harness)`，`human_expert=false`）；主判据判定一致性 **κ = NOT RUN**，按 R2 §4.5 不得用于门。主判据 0.9756913（`stats.json`）/ 0.9756882（当前抽样框复算）只能读作"模型标注下的暂定值"，**不得写成"3D 通过"**。
2. **3D 护栏按预注册 no-FPC 判红**：normal 层 Wilson 下界 **0.8979148 < 0.90**；FPC 口径 0.9005766/0.9005708 通过，差异并列披露，不改阈值。
3. **D-1 抽样框已被覆盖**：`build/kb-staging/overfull_nodes.csv` 从 `fce8269c…`/63,102 行/2,482 节点被 D-2 覆盖为 `ce7d6823…`/62,944/2,481；`sample_d1.py --check` = match=false/exit 2；重抽 879/1600 键不同；层归属 0 条不一致。"同一框必得同一样本"不成立。
4. **3E = 条件性通过**：三项判据在真 Room 设备与 JVM 双读数成立；但**第二档无预留份额**——并集按分取队，默认 20 分钟（6 个知识槽）可被今日档占满，第二档贡献可以为 0。这是产品判断，本批未改语义。
5. **MVA = 不可评**：零生图、零真实 Provider 调用、零 VLM judge、零设备执行；IMG/JS 两臂各 0 可评样本，p95/首次/预算内/分组全部 `null`（不记 0 命中）。旧门 `critical_error_accepted=0` 是 `figure_eval_gate.py:980` 恒真式，已加 `citable=false`。
6. **AUD-08 = FAIL 且 acceptance 只运行一次（本批）**：本批唯一 run 为 `split-gate/runs/acceptance-b50-01/`；但**同一 8 页已在批次 48 的 `acceptance-once-01` 消费**，held-out 不可恢复，本批是第二次观测（`split-gate/SCOPE-CORRECTION.md §2`）。门值 ① 7/24=0.2917、② 27/78=0.3462、③ 1/78=0.0128、硬红线 `fragmentedQuestions=1`；门后自动化保持不启用。
7. **发布签名 = 本地测试身份**：keystore DN 含 `OU=NOT-PLAY-PRODUCTION`，SHA-256 `2c8415b2…5eba`；**不是 Play 生产签名**。Play 账号/生产证书/备案号/公开隐私 URL 均为外部 BLOCKED，未伪造。

### 0.4 本记录员本轮实跑 / 未跑（2026-11-09）

**实跑（本会话命令与读数，输出落 gitignored `build/b50-record/`）**：

| 命令 | exit | 关键读数 |
|---|---:|---|
| `git rev-parse HEAD` | 0 | `a87fc70d15c8ad726cee17422dbeef0b4977747d` |
| `git status --porcelain \| wc -l` / `-uall` | 0 | **1643** / **5356** |
| `sha256sum docs/acceptance/ledger.md` | 0 | `9044a779…45cb97`（1200 行） |
| `python -I tools/ci/acceptance_report.py --out build/b50-record/report.json` | **1** | `state=FAIL`；**933/13/254/0**；`violations=[]`；`pass_with_review=933`、without=0、invalid=0、row_specific=**655**；head `a87fc70d`、dirty **5356** |
| 同脚本 `--require-complete --out …/report-require-complete.json` | **1** | `NOT_ACCEPTANCE_COMPLETE (UNVERIFIED=0 FAIL=13 BLOCKED=254)` + `REVIEW_CHECKS_SHARED`（20 组） |
| `python -I tools/ci/acceptance_lint.py --ledger … --out build/b50-record/lint.json` | **1** | total 1200；violations **162**，全部 `EVIDENCE_REUSED`；其中 **22 条**点名 `2026-11-09-batch-50` 证据路径 |
| `python -I tools/ci/trace_requirements.py --ledger … --out build/b50-record/trace.json` | **0** | total 1200、gaps **0** |
| `python -I tools/ci/acceptance_init.py --check` | **0** | 无输出 |
| `sha256sum -c …/REVIEWS.sha256` | **0** | **947/947 OK**（946 行级 JSON + schema） |
| `sha256sum -c …/GATES.sha256`（在 `docs/acceptance/evidence/2026-11-09-batch-50/` 内） | **1** | **34/35 OK**；`make_verdicts_report.py` FAILED（清单 01:06 冻结，文件 02:06 被修复环改） |
| `sha256sum -c docs/research/evidence/2026-11-09-batch-50/EVIDENCE.sha256` | **1** | **670/673 OK**；`d1/REPORT.md`、`device/README.md`、`mva/README.md` 三处 FAILED（均在 02:05–02:10 被修复环改，清单 01:06 冻结） |
| `cmp docs/acceptance/acceptance-report.json …/gate/acceptance-report.json` | 0 | **逐字节相同** |
| runbook 覆盖（进程内换载体，不改工具） | — | `blocked-runbook.md` **254/254**、missing=[]；默认 `runbook.md` **85/254** |

**未跑（不得读成通过）**：任何 Gradle 任务、任何 `am instrument`/设备 UI、2h soak、真实 Provider 调用、VLM 判官、16KB 运行时、切分门重跑（AUD-08 一次性红线，本记录员未重跑）、`connectedLocalFirstDebugAndroidTest`。下文 Gradle/设备/soak/发布读数均标为**归档读数（读自证据文档、日志、XML、JSON）**。

### 0.5 证据边界（必须与结论一起读）

1. **行级复核是机械复算**：933 个 PASS 行全部有行级 JSON，`pass_without_review=0`、`pass_with_invalid_review=0`、`review_validation_errors=[]`；但复核人 `independent_agent=false`，是脚本复算，不是第二人/第二模型语义复核。`pass_with_row_specific_review=655/933`，20 组 ≥5 行共用（202 行，最大一组 72 行）。
2. **哈希清单与终态有 4 处漂移**（本轮实测，见 §5.3）：GATES 1 项、EVIDENCE 3 项。漂移文件均为修复环在清单冻结后改动；这是**清单滞后**，不是证据链违规，但复现者按旧清单会得到 exit 1。
3. **测试侧 hook 的强度差异**：设备轨 3 处 hook 中，迁移列清单与 drill settings 白名单是"过期夹具同步到现行 schema"（等值关系不变）；能力屏由整串等值改为 `substring=true` **确实放宽了匹配方式**（仍要求"测试通过"主句上屏）。本节**不把它记作"断言零放宽"**，只记作"已登记的最小 hook"。
4. **3D/3E/MVA/AUD-08 的失败不因台账升级而改变**：台账 933 个 PASS 是行级验收面，不是四门门值；`verdicts.md §0` 与终裁 B50-3 同口径。
5. **共享脏树**：HEAD 未提交本批代码；发布产物是脏树构建，`apk` 内嵌 `revision=HEAD` 不代表所编代码（`release/release-gates-summary.json#git`）。

---

## 1. 阶段对账（计划 43 项）

图例：✓=完成且有证据在盘；✓*=完成但带如实保留/部分达成；✗=未执行；BLOCKED=外部条件缺失。

| # | 计划项 | 结果 | 证据 / 说明 |
|---:|---|---|---|
| 1 | P0 HEAD/工作树/热区快照 | ✓ | `2026-11-09-batch-50-baseline.md §1`：HEAD `a87fc70d`、581 脏（129 M + 452 ??）、他线热区清单、6 处先认账 |
| 2 | P0 协作规则（只新增/最小 hook/冲突停项） | ✓ | 基线 §6；各子线开工/收工 `git status --porcelain` 实读；设备轨 tracked diff 指纹收工不可复现，已登记（`device/summary.json#snapshot`） |
| 3 | R 切分 CV+LLM 方案 | ✓ | `docs/research/2026-11-09-split-cv-llm.md`；落地见 #19–#20 |
| 4 | R 3D 抽样与 Wilson 方案 | ✓ | `docs/research/2026-11-09-binding-audit.md`（R2）；D-1 报告引用其口径 |
| 5 | R MVA judge 协议 | ✓ | `docs/research/2026-11-09-mva-judge.md`；R5 正文一处漂移由 MVA 轨实算更正（numeric A 只 85 非 81，合计 94） |
| 6 | R 3E 真实验证方案 | ✓ | `docs/research/2026-11-09-3e-verification.md` |
| 7 | R 发布/签名/16KB/versionCode 方案 | ✓ | `docs/research/2026-11-09-release-signing.md` |
| 8 | G0 设计件机器块修复（单一 begin/end，75/99/closure6） | ✓ | `g0/00-index.md`：修复前 exit 2（0 个 v3 块）→ 修复后 exit 0；`pytest test_backup_plan_sync.py` 9 passed |
| 9 | G0 SmbkSelectionPlan/机器块/goldens/75.json 四方一致 | ✓ | `check_backup_plan_sync.py` → `PASS 四方一致` exit 0；空库全选摘要 `8567faae…` 三处同值；`identityHash` 取自 75.json 未手写 |
| 10 | G0 设备：迁移 1..75 矩阵、备份 34、trimmer、rowCounts、drill | ✓ | 设备轨（最终 test APK `0013cf37…`/`db14c022…`）：迁移 **OK(20)**、备份 **OK(34)**、trimmer **OK(10)**、rowCounts **OK(2)**；drill 6 例 = 3 mask 绿 + corrupted + kill-stage 设计性崩溃 + restore-handoff **OK(1)** |
| 11 | G0 台账 B 域更新到 75 + 旧 v73 标注历史 | ✓ | `g0/05-b-domain-annotation.txt`、`docs/acceptance/b-domain-schema75-annotation.md`；台账字节当时未动；验收轨后续把 B 001..090（除 077/078）换绑 75 设备矩阵 |
| 12 | G1 D-1 分层抽样与模型审计 | ✓* | 1600 样本（overfull 1300/normal 300）；主判据 lower_95 **0.9756913** PASS；normal 护栏 **0.8979148 FAIL**；`overall_pass=false`；model-labeled、κ NOT RUN（`d1/stats.json`、`d1/REPORT.md:8-24`） |
| 13 | G1 错误项改绑/剔除并复核 | **✗** | 清单已交付（43 条 = 39 REBIND + 4 NONE，`d1/errors.csv`），但 **D-1 报告明写"未执行任何改绑/删料动作"**（`d1/REPORT.md:148`）；KB 生产目录干净 |
| 14 | G1 D-2 结构清单清零 | ✓* | 近重复节点级 **0 组/0 对**；2 组候选裁决保留 A、退休 B（**退休未执行**）；超配 2,481 节点/62,944 条/超出前 4 = 53,020，**RETAIN_NO_CAP** + 台账；容量 91 条逐行登记（22/12/44/13）；type 三值外 **0**（`d2/d2-summary.json`） |
| 15 | G1 D-3 五红清零 + 召回实修 | ✓ | 五类 12 例 `--rerun` 全绿；dense 11/11；召回父节点作用域修复（新 DAO 读口）红→绿；融合 **117/129 = 0.9069767441860465**、MRR 0.7239018087855298、p50/p95 38/58ms、overBudget 0（`d3/d3-summary.json`） |
| 16 | G1 3D 门证据 | ✓ | `d1/`、`d2/`、`d3/` 三目录 + README/JSON/CSV/XML |
| 17 | G2 真实队列/预算设备验证 | ✓* | 设备 `StudyReviewPlanner3EScanInstrumentedTest` **OK(4)**；JVM `KnowledgeReviewQueueTest` **23/0**；45 分钟预算队外 118 弱项捞回 **10**；1200s 预算→6 知识槽（第二档 4、名额剩 6）；口径=每次排计划 ≤10（KD-46） |
| 18 | G2 3E 门证据 | ✓ | `3e/README.md`、`summary.json`、`logs/`、`jvm/` |
| 19 | G3 Android 轻量 CV 预处理 | ✓ | `core/model/.../layout/LayoutStructure.kt`、`core/data/.../model/PageLayoutHint.kt`；`:core:model:test` 674/0；设备 `PageLayoutHintInstrumentedTest` **OK(3)** |
| 20 | G3 结构提示进 LLM wire/prompt | ✓ | `ModelTasks.layoutHint`（`@EncodeDefault(NEVER)`，null 时请求体/指纹逐字节不变）+ 单页切分提示词尾部追加；多页关系变体不带 |
| 21 | G3 dev 16 页调参（先红后绿） | ✓* | 1 组提示词实验：红 3 tests/2 failed → 绿 BUILD SUCCESSFUL；但 dev ① 3/16 **无净收益**，按纪律原字节回退、实验用例删除；生产提示词 sha 未变（`31aef2ab…`） |
| 22 | G3 acceptance 8 页只跑一次 | ✓* | 本批唯一 run `acceptance-b50-01`；**同一 8 页已在批次 48 消费**，held-out 不可恢复（`SCOPE-CORRECTION.md`）；未重跑换绿 |
| 23 | G3 门 ≥0.9 或如实 FAIL | ✓ | `check_split_gate.py` exit **1**：① **7/24=0.2917**、③ **1/78=0.0128**、硬红线 frag=**1** → FAIL；自动化不启用 |
| 24 | G4 120 盲测集冻结/开发集隔离 | ✓ | `mva_preflight.json`：两文件 sha 全一致、generator sha 匹配；120/24 交集 **0**、dev 不进分母；288 条事实 PASS |
| 25 | G4 JS/IMG 两臂跑图 + 确定性检查 | ✓* | IMG 臂 fake 驱动 `machinery_verdict=PASS`/`real_provider_quality=UNVERIFIED`、**0 可评样本**；JS 臂 `NOT_AVAILABLE`、`NOT_EVALUABLE`、**0 可评样本**；三类确定性检查 `NOT_RUN` |
| 26 | G4 VLM judge + 关键错误清单 | **✗** | harness 无 `judge` 实现、无 C 通道渲染件注入、无视觉凭据；E0-D/E0-O 共 40 条 critical 只能走 A\|C，全部 UNVERIFIED；critical 计数 `citable=false` |
| 27 | G4 门判（90/95/90/0）与证据 | ✓* | 门 **FAIL/NOT_EVALUABLE**：首次/预算内/分组/p95 全 `null`；critical 0 不可引用；`mva/README.md` |
| 28 | 5c 错因 UI：标签/候选确认/撤销 | ✓* | 面板 + `ErrorCauseReviewService`；红→绿（撤销文案缺陷修复）；JVM 41/0 + 9/0 + 3/0；设备真库 **OK(1)** + Compose **OK(7)**；**生产无候选生产者**（只显空态） |
| 29 | 5b 容量 UI：preview 入快照/复习页 | ✓ | `StudyReviewCapacity` 入快照 + 复习页；定向 39/0、feature:review **116/0**、core:domain 742/0（1 既有 skip）；设备 4+3 绿；KD-68 关闭；`rebalanceEnabled` 生产恒 false |
| 30 | 6B-3 改卷 UI：入口/预览/采用 | ✓* | `NativePaperEditModel`；JVM 17/0、模块 108/0、设备 17/0；真实 Provider 回执 **UNVERIFIED**；app 级导航未走完整 NavHost |
| 31 | JS 生产装配：visualEngine 注入/能力广告/math 重放/KD-78 | ✓* | math 盒回放**显式产品降级**（红→绿）；KD-78 ①②核销、整条关闭；④ flags 复验；**visualEngine 未注入、能力不广告**（BLOCKED：MVA FAIL + KD-76 open + 无宿主帧源） |
| 32 | KD-67 第二道校准题 | ✓* | 候选池双槽 + 单槽加槽位 + 真 Room e2e **OK(3)**；第二道题的**生成/上屏/作答无生产入口**（UI 未接） |
| 33 | 发布 AIGC：角标/DOCX/改卷文本/部分页/材料 AI 整理 | ✓* | 五项均有红→绿或"复核绿+历史红"；DOCX `docProps/custom.xml` 独立读回；KD-85 GENERATION 2→3；KD-84 部分页复核；H-052/061/062 PASS；**H-053 门红（占位符/外部未取得）** |
| 34 | 隐私政策/备案页 + data safety 清单 | ✓* | 应用内 9 章全文页（版本 2026-11-08）；H-025 FAIL→**PASS**；H-024/H-036 **BLOCKED**；备案号/主体/联系方式/模型备案 6 项 BLOCKED，代码写"尚未取得" |
| 35 | versionCode 注入 + 本地 release keystore + AAB 签名 | ✓ | `101160 = 100000 + 1160`；`validateVersionCode` 负控 3 红/正控绿；PKCS12/RSA4096/10000 天、DN `OU=NOT-PLAY-PRODUCTION`、sha `2c8415b2…`、gitignored；双 flavor APK/AAB 出包 BUILD SUCCESSFUL 4m56s |
| 36 | AAB 16KB/ABI/证书/versionCode 校验 | ✓* | `zipalign -P 16` Verification successful；apk16k 14/14 `.so` STORED+16K 对齐、errors []；aab16k alignment 2、ABI `{arm64-v8a, x86_64}`；vc 三处 101160；**16KB 运行时 NOT RUN**（设备 4096 页/x86_64） |
| 37 | 干净树 release 构建 + mapping | ✓/BLOCKED | mapping **PASS**（140,341,720 B/968,647 行/R8 9.3.16，与 AAB 内 proguard.map 同 sha `ce595aa0…277e`）；**干净树构建 BLOCKED**：`identity --require-clean` exit 1、dirty 4315（构建时） |
| 38 | 商店素材/上架清单（Play 账号外部） | **BLOCKED** | Play 账号/生产签名外部未取得；本批证据目录**未发现商店素材/上架清单产物**；不伪造 |
| 39 | 全量设备矩阵 + 2h soak + R8 release | ✓* | 迁移/备份/trimmer/rowCounts/drill/KD-67/6B-3/5c/5b/AIGC 全绿（见 §5.2）；release 安装冒烟 vc 101160、FATAL 0；soak 7200s/115 轮 crash-free；**AIGC background 2 例 FAIL**；无真机/arm64 |
| 40 | 验收台账/证据/行级复核 | ✓* | 1200 行 933/13/254/0；`REVIEWS.sha256` **947/947**；`GATES.sha256` **34/35**、`EVIDENCE.sha256` **670/673**（4 处清单滞后，见 §5.3）；行级复核机械复算 |
| 41 | 独立 + 对抗复核 | **✗** | 无第二主体（第二人/第二模型）语义复核产物；行级复核 `independent_agent=false`；修复环 findings 的"已独立复核"无独立签名件可核。本批只有单会话复核 |
| 42 | 修复环 + 环后强门 | ✓* | 修复环 7 项已修、4 项阻塞（`repair/REPAIR-RING.md`）；环后强门 **7/7 exit 0**（只编译/打包）；10 模块单测 **4294/11 红/exit 1**（11 红均为既有，未回退树重演） |
| 43 | 记录/终裁 | ✓ | 本文件 + `adjudication.md` B50-0…B50-6 |

**对账结论**：43 项中 **✓ 21 项、✓* 17 项、✗ 3 项（#13 改绑未执行、#26 VLM judge 未实现、#41 独立/对抗复核未做）、纯 BLOCKED 1 项（#38 商店/上架）、混合 ✓/BLOCKED 1 项（#37 干净树）**。0 项洗绿；0 项门值改动；0 项外部项伪造。

---

## 2. 四门结果

### 2.1 3D（D-1 / D-2 / D-3）

| 子项 | 判据 | 实测 | 判定 | 证据 |
|---|---|---|---:|---|
| **D-1 主判据** | 分层加权绑定精确率单侧 95% Wilson 下界 ≥0.95 | **0.9756913**（抽样时点）/ **0.9756882**（当前框复算） | **PASS** | `d1/stats.json`、`d1/frame-drift.json` |
| **D-1 护栏** | 每层 Wilson 下界 ≥0.90 | overfull **0.9770088** PASS；normal **0.8979148** FAIL（差 0.2085pp；FPC 0.9005766 通过） | **FAIL** | `d1/REPORT.md §4.3/§4.4` |
| **D-1 总判** | 主判据 ∧ 护栏 | `PASS main / FAIL guardrail` → **`overall_pass=false`** | **未过门** | `d1/stats.json#verdict` |
| **D-1 标注** | 人工专家 | `labeler_kind=model`、`human_expert=false`、`blind_to_layer=true`；κ **NOT RUN** | **model-labeled 暂定值** | `d1/REPORT.md:8-24`、§7 |
| **D-2 近重复** | 节点级 0 组/0 对 | **0 组 / 0 对**；候选 2 组逐组裁决（化学保留 `cmp-che-02368` 退休 `cmp-che-02639`；数学保留 `cmp-mat-00280` 退休 `cmp-mat-00065`），**退休未执行** | 清零/登记 | `d2/d2-near-dup-scan.json`、`d2-near-dup-dispositions.csv` |
| **D-2 超配** | 结构清单清零或登记 | 2,481 节点 / 62,944 条 / 超出前 4 = **53,020**；最大 146（MATH 三角函数的图象与性质）；零 2-gram 5,954（超配段 4,918）；**RETAIN_NO_CAP**（运行时无每节点条数门），分诊 49/1,009/1,423，2,481 行台账 | 登记 | `d2/d2-overfull-ledger.csv` |
| **D-2 容量 91 条** | 逐行处置 | 22 同节点入包 / 12 异节点入包 / 44 未入包 / 13 节点退役；生产器废止补丁**未应用**（目标文件他线在飞） | 登记 | `d2/d2-capacity-dispositions.csv` |
| **D-2 历史 type** | 三值外 = 0 | 64,853 条全 3 值，**三值外 0**；materialize/merge/protocol 三处白名单逐字 3 值 | 单源成立 | `d2/d2-type-distribution.csv` |
| **D-3 五红** | failures/errors/skipped 全 0 | 5 类 12 例 `--rerun` **12/0/0/0**（`GoldenRetrievalJvmTest` 2、`ProductionLexicalLegExportTest` 1、`Stage1LexicalLabTest` 1、`Stage2LexicalScoresExportTest` 1、`DenseTokenizerParityTest` 7） | 清零 | `d3/TEST-*.xml`、`five-red-tests-green.txt` |
| **D-3 召回** | 保持 117/129 | 设备融合 **117/129 = 0.9069767441860465**、MRR **0.7239018087855298**、词面 101/129、p50/p95 38/58ms、overBudget 0、24 章逐章 diff rc=0；JVM 同值 | 保持 | `d3/d3-summary.json`、`device-golden-final-run.txt` |

**D-3 召回实修（先红后绿）**：父节点取行原先用历史解释路径 `readKnowledgeNodesByIds`（不过滤 RETIRED/verification），把退役父主题与 MODEL_CANDIDATE 父主题随 active 子节点披露；修法 = 新增 `ProblemOrganizationDao.readRecallParentKnowledgeNodes`（subject+可信+非退役）并在 `RoomKnowledgeBaseStore` 使用。红：设备 `Tests run: 1, Failures: 1`（ids 含 `topic:retired`/`topic:candidate`）→ 绿：`OK (1 test)`；同路径完整性用例 `OK (7 tests)`。

### 2.2 3E（队列/预算）

| 判据 | 读数 | 证据 |
|---|---|---|
| 离开今日并集可捞回 | 45 分钟预算下队外 118 弱项捞回 **10**，10 个全在今日计划知识点并集之外 | 设备 `OK (4 tests)`（`3e/logs/08-instrument-3e-green-final.log`） |
| F17 名额封顶 | 第二档每次排计划恰好 **10**（候选 118） | 同上 |
| F8 时间预算封顶 | 1200s → 知识队列 **6** 项（6×180=1080 ≤1200 <7×180），第二档仅占 4、名额剩 6 | `#knowledgeQueueStopsAtTheTimeBudget…` |
| 每日/单计划口径（KD-46） | 实现=**每次排计划 ≤10，非严格每日累计**；同日两次各 10、累计 20；纯函数同输入逐位一致 | 设备 `#scanQuotaIsPerPlanNotADailyCumulativeTotal`；JVM `#scanQuotaIsPerPlanNotADailyAccumulator` |
| **门判** | **`PASS_WITH_FLAGGED_LIMITATION`**：第二档无预留份额；默认 20 分钟预算可被今日档占满使第二档贡献为 0（`3e/README.md §5 R1`） | `3e/summary.json#gate` |

既有 3E 设备用例首跑 **3/3 红**（夹具假设过期：`BudgetResolution` 构造预算被设置默认 1200s 覆盖；`ea4348f9` 后知识点队列预算改当日计划预算），处置=**只改夹具不改生产码**，断言意图一条未删（节点 15→126；新增"已掌握点不被捞回"负向；新增"捞回=队外最老 10 个"排序断言），绿 `OK (4 tests)`。

### 2.3 MVA（视觉质量）

| 项 | 读数 | 依据 |
|---|---|---|
| 冻结 sha / 生成器 sha | 全一致（`manifest_sha_verified=true`、`generator_sha_matches=true`）；盲测 120 / 开发 24，交集 **0**、dev 不进分母 | `mva_preflight.json#fixture/#isolation` |
| 夹具自洽 | **288 条事实 PASS**（容差/枚举/类型/重复 key） | `#fixture_sanity` |
| A/B/C 通道 | 事实 A 94 / A\|B 116 / A\|C 40 / A\|B\|C 38；critical A 只 **87**、A\|B 82、A\|C 40；含 critical A 只题 **57/120**；全 critical A 只题 **27/120**；`judge_channel_map.json` sha `580c2f63…`，两次生成逐字节一致 | `#channel_totals` |
| IMAGE_MODEL 臂 | fake 驱动 machinery PASS / real quality **UNVERIFIED**；**0 可评样本** | `#readings.IMAGE_MODEL` |
| JS 臂 | `NOT_AVAILABLE`；质量门 `NOT_EVALUABLE`；**0 可评样本** | `#readings.JS` |
| 首次 / 预算内 / 分组 / p95 | 全部 **null**（无样本记 null，不记 0 命中）；`evaluable=false` | `#readings` |
| 确定性检查 | render/geometry/text 全 **0**、`status=NOT_RUN` | `#readings.*.deterministic_checks` |
| 关键错误被接受 | 0 条但 **`citable=false`**（`:980` 恒真式；`:1080` 读该字段） | `#readings.*.critical_errors_accepted` |
| **门判** | **FAIL / NOT_EVALUABLE**；JS 不广告（生产 6 处能力集合全 `IMAGE_MODEL_ONLY`） | `mva/README.md`、`FigurePromptAssembler.kt:21-23` |

**不可评的实质**：IMG 臂无图像后端（`local.properties` 仅 chat 通道）；JS 臂无 Python 引擎适配器、无"题面→SceneV1"作者链、无 `RenderScene→facts` 提取器；VLM judge 无实现、无 C 通道渲染件注入、无视觉凭据。**120 题质量结论不存在**。

### 2.4 AUD-08（切分）

| 指标 | 读数 | 门 | 判定 |
|---|---:|---|---|
| ① 题数完全一致 | **7/24 = 0.2917**（Wilson95 0.1491–0.4917，描述性） | ≥0.9 | **未过** |
| ② 边界 IoU≥0.5 | **27/78 = 0.3462** | 只度量 | — |
| ③ 切残率 | **1/78 = 0.0128**（Wilson95 0.0023–0.0691） | ≤0.05 | 过 |
| 切残硬红线 | `fragmentedQuestions=1` | =0 | **未满足** |
| **门判** | `check_split_gate.py` **exit 1**；`state=FAIL`；门后自动化不启用 | | **FAIL** |

- 两臂读数（dev 16）：A LLM-only ① 4/16、② 22/52、③ 0/52；B LLM+hint ① 4/16、② 16/52、③ 0/52、通道错误 3；B2 prompt 实验（已回退）① 3/16、② 21/52、③ 0/52、错误 1。acceptance 8（B 臂）① 3/8、② 11/26、③ 1/26、错误 2。
- **门值口径**：acceptance 8 页本批只运行一次（`acceptance-b50-01` 唯一 run），但同一 8 页已在批次 48 消费，held-out 不可恢复；金标 G1 为单模型盲标、无人工专家；通道为 JVM harness，不等于设备产品路径准确率，也不外推 155 页总体。

---

## 3. 产品面

| 产品项 | 落地 | 门禁读数（本会话实跑 / 归档） | 未闭合残余 |
|---|---|---|---|
| **5c 错因 UI** | 详情页「教学候选（未成为标签）」面板；候选 → 用户确认 → 撤销；模型自动落账关闭（`autoBookingClosed=true`） | core:data `*ErrorCause*` 41/0；feature:library `ErrorCausePanelStateTest` 9/0；app `ErrorCauseWriteMessageTest` 3/0；core:database `*ErrorCauseSchemaContractTest` 3/0；设备真库 `OK(1)`、Compose `OK(7)`（emulator-5590） | **生产无候选生产者**（`ErrorCauseBookingService`/`Extractor` 无调用方，真实用户只见空态）；设备走查是"注入候选后走真实 UI"；改判 supersede 无 UI |
| **5b 容量 UI** | `StudyReviewCapacity` 入快照 + 复习页（今日容量/考试缺口/欠账/逐科 granted-unmet/`intakeUnschedulableCount`）；KD-68 关闭 | 定向 39/0；feature:review **116/0**；core:domain 742/0（1 既有 skip）；设备 `ReviewIntakeBacklogPreview` 4/0、`ReviewCapacitySection` 3/0 | `rebalanceEnabled` 生产恒 false（设置无开关），planned 分支仅 JVM 覆盖；欠账口径=`facts.size`（非逾期题数）；每次快照多一次纯读；未做基准 |
| **6B-3 改卷 UI** | 卷面页「AI 改卷面（一句话）」→ 预览 diff → 采用/取消；详情 chip（`?ai=1`） | feature:library `NativePaperEditModelTest` 17/0、模块 108/0；设备 17/0（7+10）；app 双 flavor 编译绿 | 真实 Provider 回执 **UNVERIFIED**（全 fake port）；app 级 NavHost 端到端未走；端口应用逻辑两份实现（`NativePaperEditModel.previewFor` 与 `TutorPaperEditApplier`），本批不跨模块重构 |
| **JS** | math 盒回放**显式产品降级**（`PAINTER_LABEL_MATH` fail-closed）；KD-78 ①② 核销、整条关闭；④ flags 复验；visualEngine 不注入、能力不广告 | 设备红→绿（诊断含 `explicitly degraded`）；Node 94/0；JVM 35/26/13 全绿；painter 24/24；flags `OK(2)`；`check_stdlib_compat.py` EXIT=0 | **math 盒回放未实现**（artifact 不携 MathBox/原文；按任务书选"明确降级"）；**visualEngine 注入 BLOCKED**（MVA FAIL + KD-76 open + 无宿主帧源）；KD-76/71/73/82 保持 open |
| **KD-67 第二道校准题** | 候选池读两槽（REVIEW→FOUNDATION、CALIBRATION→CALIBRATION）；题面行身份加槽位；upgrader CAS 加 `slot`；repository 第二槽 persist/read/submit；writer 按题面 id 定位 | 行为红→绿 `KnowledgeCalibrationQuestionTest` 5/0；相关 7 类 55/0；core:data 全模块 **1707/4 红/2 skip**（4 红与批次 49 逐字同组，无新增）；真 Room e2e `OK(3)` / 1.203s | **第二道题的生成/上屏/作答无生产入口**（屏幕仍只显示选择器决策文本；`KnowledgeReviewQuizLoader` 无入口；VM/Screen/Root 动作未接）；Elo 选项数仍读会话题面槽；证据审计面仍映射会话题面槽 |

---

## 4. 发布（逐项判定）

> 依据：`release/release-gates-summary.json`、`release/release-50.md`；设备 `device/`；AIGC `aigc/`；隐私 `privacy/`。
> 本地 release key 明确非 Play 生产身份；Play 账号/备案号/公开 URL 为外部项，标 **BLOCKED**，未伪造。

| # | 项 | 实测 | 判定 |
|---:|---|---|---|
| 1 | **可签名 AAB** | 双 flavor AAB `jarsigner -verify` exit 0；`keytool -printcert` 指纹 = APK 证书 `2c8415b2…5eba`；signing 门 exit 0（APK/AAB 同身份、证书未过期、四 env 齐、0 tracked keystore） | **PASS（本地测试 key）**；**生产签名 FAIL/BLOCKED**（J-020/J-021 仍 FAIL） |
| 2 | **16KB** | `zipalign -v -c -P 16 4` → Verification successful；apk16k 14/14 `.so` STORED 且 `offset%16384=0`、`elf_errors: []`；aab16k `alignment: 2`、ABI `{arm64-v8a, x86_64}`、7 库全在 | **静态 PASS**；**运行时 UNVERIFIED**（唯一设备 4096 页/x86_64；无 arm64 真机） |
| 3 | **versionCode** | `101160 = 100000 + git rev-list --count HEAD(1160)`；合并 manifest / `aapt2 badging` / AAB proto manifest 三处 **101160**；`validateVersionCode` 缺参/非整数/越界 3 红、101160 绿；verifier `--expected-code` 双 flavor exit 0 | **PASS（注入与一致性）**；J-048/J-052 仍 FAIL（跨构建单调/历史 AAB 可比未做）；下次发版必须 >101160 |
| 4 | **mapping** | `mapping.txt` 140,341,720 B、968,647 行、R8 9.3.16；与 AAB 内 `BUNDLE-METADATA/.../proguard.map` **逐字节同 sha** `ce595aa0…277e`；`r8` 门 exit 0（classes 10661 / renamed 10461 / removed 1199 / negative_control_hits 0） | **PASS** |
| 5 | **干净树** | `j_release_verify.py identity --require-clean` → **exit 1**、`dirty_paths: 4315`（构建时）；当前树 `-uall` **5356**；AAB 由脏树构建，`revision=HEAD` 不等于所编代码 | **BLOCKED**（须主会话提交后复跑；不得把本轮包当 HEAD 字节复现） |
| 6 | **AIGC** | ① 显式角标（UI/导出/PDF/DOCX）；② DOCX `docProps/custom.xml` 文档级标识（TC260-PG-20258A §6.1，官方 PDF sha `6bf6c118…3b0e`）；③ KD-85 改卷文本 `GENERATION 2→3`；④ KD-84 部分页复核 `OK(7)`；⑤ KD-29 材料 AI 整理角标。`h_compliance_gate --all`：H-052/H-061/H-062 **PASS**，H-053 **红（占位符/外部未取得）** | **PASS（五项有证据）**；**H-053 外部 BLOCKED**（CAC 备案/上线编号未回填） |
| 7 | **隐私** | 应用内《隐私政策》9 章（版本 2026-11-08）；未成年人专章（PIPL 第三十一条）补上悬空引用 → H-025 **FAIL→PASS**；`data safety` 逐题清单落盘（`docs/current/play-data-safety-answers.md`）；全 UI dump 扫 `ICP备\d+`/8 位以上连续数字 **零命中** | **PARTIAL**：H-025 PASS；**H-024 公开 URL BLOCKED、H-036 Play 表单 BLOCKED**；备案号/运营主体/联系方式/模型备案 6 项 BLOCKED |
| 8 | **Play 账号 / 生产签名 / 备案号** | 无 Play 账号、无生产 keystore、无备案号；本地 key DN `OU=NOT-PLAY-PRODUCTION` | **BLOCKED（外部）** |
| 9 | **商店素材/上架清单** | 证据目录未发现商店素材/上架清单产物 | **BLOCKED（外部）/未产出** |

**发版判定总括**：**不成立**。工程侧可签名的本地 AAB、16KB 静态、versionCode、mapping 成立；**干净树、生产签名、Play 账号、备案号、公开隐私 URL 未取得**，AIGC 的 H-053 未回填。不得对外称"发版就绪"或"已可上架"。

---

## 5. 门禁 / 设备

### 5.1 门禁（归档读数 + 本会话实跑）

| 门 | 命令（简） | exit | 关键值 | 轮次 |
|---|---|---:|---|---|
| 强门（post-fix） | `bash build/run-batch50-strong-postfix.sh` | **0** | 7/7 任务 exit 0；`GRADLE_EXIT=0`；`^e: ` 0；日志 sha `453584f4…` | 归档（`postfix-gate.md §1`） |
| 本批 Kotlin 模块单测 | 10 模块 `testDebugUnitTest --continue --rerun` | **1** | **4294 例 / 11 红 / 0 error / 3 skip**；核心绿：core:model 675/0、core:domain 742/0、core:export 178/0、core:ui 56/0、feature:library 108/0、feature:review 116/0 | 归档（`postfix-gate.md §2.2`） |
| 11 红归因 | 逐名 | — | core:data 4（`OpenAiCompatibleModelGatewayTest`×3 + `RoomBackedStudyExperienceRepositoryTest`×1）；core:database 4（KernelWave0 schema 头 + LibraryFts×3）；feature:tutor 1（`TutorExportSheetTest`）；app 2（`StorageRestoreFeedbackTest` + `TutorSettlementNoticeTest`）；均为已登记既有红，**未在干净 HEAD 回退树重演** | 归档（`postfix-gate.md §2.3`） |
| 修复环定向 pytest | 5 文件 | **0** | **145 passed**（71 + 46 + 28）；另 `test_batch50_repair_invariants` 10 passed | 归档（`postfix-gate.md §2.1`） |
| `acceptance_report` | `python -I tools/ci/acceptance_report.py --out …` | **1** | **933/13/254/0**；`violations=[]`；`pass_with_review=933`；invalid/without 0；row_specific 655；shared 20 组 | **本会话实跑** |
| `--require-complete` | 同脚本 | **1** | `NOT_ACCEPTANCE_COMPLETE (0/13/254)` + `REVIEW_CHECKS_SHARED` 20 组 | **本会话实跑** |
| `acceptance_lint` | `--ledger …` | **1** | total 1200；**162** 条 `EVIDENCE_REUSED`（其中 **22 条**点名批次 50 证据，如 `b-migration-matrix.device.json` 被 72 行共用） | **本会话实跑** |
| `trace_requirements` | `--ledger …` | **0** | 1200 行、gaps **0** | **本会话实跑** |
| `acceptance_init --check` | — | **0** | 无输出 | **本会话实跑** |
| BLOCKED runbook 覆盖 | 进程内换载体 | — | `blocked-runbook.md` **254/254**；默认 `runbook.md` **85/254**（载体错位已登记） | **本会话实跑** |
| `REVIEWS.sha256` | `sha256sum -c` | **0** | **947/947 OK** | **本会话实跑** |
| `GATES.sha256` | `sha256sum -c` | **1** | **34/35 OK**；`make_verdicts_report.py` 不匹配（清单 01:06 冻结、文件 02:06 被修复环改） | **本会话实跑** |
| `EVIDENCE.sha256` | `sha256sum -c` | **1** | **670/673 OK**；`d1/REPORT.md`、`device/README.md`、`mva/README.md` 不匹配（均 02:05–02:10 被修复环改） | **本会话实跑** |
| D-1 漂移量化 | `frame_drift_check.py` | **0** | `conclusion_unchanged=true`；`replay.exit=2`；层归属 0 错配；重抽 879/1600 键不同；主判据 PASS、normal 护栏 FAIL | 归档（`postfix-gate.md §2.1`） |
| D-1 独立复算 | `recompute.py` | **0** | `all_match=true` | 归档 |
| D-1 样本键复放 | `sample_d1.py --check` | **2** | `match=false`（**预期红**，框已被覆盖） | 归档 |
| AUD-08 切分门 | `check_split_gate.py`（`gate-report-b50.json`） | **1** | ① 7/24、② 27/78、③ 1/78、frag=1 → FAIL | 归档（本会话**未重跑**） |
| MVA 门 | `figure_eval_gate.py` / `mva_gate.py` | — | FAIL/NOT_EVALUABLE；`test_mva_gate.py` 16 例在合并 pytest 中绿 | 归档 |

**不得读成通过**：强门 exit 0 只证明 androidTest 源码编译与打包，不执行测试；单测 exit 1 是真实未收口（11 红均为既有）；`acceptance_report` 与 `--require-complete` exit 1 是批次级未收口。

### 5.2 设备（emulator-5590，x86_64 / API 34 / Android 14 / 4096 页，**模拟器非真机**）

| 面 | 结果 |
|---|---|
| 迁移 1..75（`FullMigrationMatrixInstrumentedTest`） | 首跑 19/20（1 例旧列清单）→ 同步 75.json/实体 → **OK(20)**（Time 553.379） |
| 备份 34 / trimmer 10 / rowCounts 2 | **OK(34)** / **OK(10)** / **OK(2)**（最终 test APK `0013cf37…`） |
| drill 6 例 | 3 mask 首跑红（settings 六键白名单 vs 生产七键）→ 同步 → **OK(3)**；corrupted ✓；kill-stage 设计性 `Process crashed`；restore-handoff **OK(1)** |
| KD-67 真 Room e2e 3 例 | **OK(3)** |
| 6B-3 改卷 UI 17 + 5c 错因面板 7 | **OK(24)**；真实 app `AI 改卷面` chip / AI 区可见 |
| 5c 真库写入接缝 1 + JS painter 24 | **OK(25)** |
| 5b 容量 / 新题预览 / AIGC 标签 / 能力屏 17 | 首跑 15/17（能力屏整串匹配红 2）→ 修 → **OK(17)**；真实复习页容量空态 + 今日预览可见 |
| AIGC 导出 `core:export` 全模块 | **OK(32)** |
| AIGC 导出 UI（delivery 2 + notifications 2 + flags 2） | 绿；notifications 拒绝态需先 `pm revoke` + `-e smb_notifications_denied=true` |
| **AIGC 导出 background 2 例** | **FAIL（未转绿）**：`waitForTag("library_item_entry:m1:closed-interval-extrema")` 20s 超时；条目在库且真实 app 手工可见；改 `performScrollToNode` 仍红 → 时序/力学问题，**该 claim UNVERIFIED** |
| release APK 安装冒烟 | `versionCode=101160` / `0.2.0-localfirst`；四 tab 渲染；`FATAL EXCEPTION` **0** |
| 2h soak（release APK / 空库） | **PASS（仅 crash-free）**：7200s / 115 轮 / `crash_delta=0` / `dropbox_delta=0` / monkey 全 0 / pid 未变；TOTAL PSS 64.7→100.6 MB；无 ANR 采样、无内存/电池阈值 |
| 绑定（如实） | app debug `43f1fe46…`；test APK `2e32e085…`/`0013cf37…`/`db14c022…`/`14cdea1d…`/`07c71394…`；release `1bf98b00…`；HEAD `a87fc70d` + 脏树（开工 688 行 / `-uall` 4336 路径）；tracked diff 指纹收工不可复现（未定位），以 HEAD+APK sha 为权威 |

### 5.3 本轮新增的 4 处清单漂移（如实登记）

1. `GATES.sha256`（35 项，01:06 冻结）→ `make_verdicts_report.py` 在 02:06 被修复环改（加 κ 披露），哈希不匹配。
2. `EVIDENCE.sha256`（673 项，01:06 冻结）→ `d1/REPORT.md`（02:05）、`device/README.md`（02:10）、`mva/README.md`（02:09）被修复环改，3 项不匹配。
3. 结论：**这 4 项是清单滞后，不是内容被篡改**；修复环改动内容见 `REPAIR-RING.md §1`。重新生成清单不在本记录员范围（会改验收证据字节），留给主会话在提交前决定。

---

## 6. 复核 / 修复

### 6.1 修复环（高/中；`repair/REPAIR-RING.md`）

| # | finding | 处置 | 落点 |
|---:|---|---|---|
| 1 | AUD-08 acceptance"只跑一次"全局口径不成立 | **已修（口径）**：改为"本批只运行一次 + 同一 8 页已在批次 48 消费、held-out 不可恢复"；原 gate 报告/证据保持原字节 + 取代声明 | `tools/split_golden/check_split_gate.py:202-212`、`split-gate/SCOPE-CORRECTION.md`、`verdicts.md §0/§3` |
| 2 | D-1 抽样框被 D-2 原地重写，REPORT 却写 match=true | **已修（证据）**：新增漂移量化器/JSON，REPORT §2/§4.3/§7/§8/§9 改真实读数；不重抽、不换样本 | `d1/frame_drift_check.py`、`frame-drift.json`、`FRAME-DRIFT.md`、`REPORT.md` |
| 3 | 同一 finding 的重复条目 | **已修（同上）+ 硬规则**：抽样前必须把框复制进证据目录；写框工具不得原地覆盖 | `FRAME-DRIFT.md §5` |
| 4 | MVA 旧门 `critical_error_accepted` 恒真 | **已修（标注）**：`citable=false` + reason + `:980` 注释；门值/判定未变 | `tools/figure_eval/figure_eval_gate.py`、`mva/README.md §3` |
| 5 | D-1 主判据 κ NOT RUN 未随记录/终裁携带 | **已修（披露）**：record、5c 引用、终裁、`verdicts.md` 判词/不可变项、生成器均加"model-labeled 暂定值，按 R2 §4.5 不得用于门" | `record.md`、`verdicts.md`、`make_verdicts_report.py` |
| 6 | Wilson 护栏对 FPC 口径敏感 | **已修（披露）**：no-FPC 0.8979148（FAIL）与 FPC 0.9005766/0.9005708（PASS）并列；`overall_pass=false` 按预注册口径 | `d1/REPORT.md §4.3/§4.4` |
| 7 | 3E 第二档无预留份额 | **阻塞**（产品语义裁定） | `repair/BLOCKED.md B-2` |
| 8 | 5c 生产无候选生产者 | **阻塞**（缺结构化 trace 采集 + 产品裁定） | `repair/BLOCKED.md B-1` |
| 9 | JS 生产装配未完成 | **阻塞**（他线在飞 + MVA FAIL/KD-76） | `repair/BLOCKED.md B-3` |
| 10 | AAB/APK 是脏树产物 | **阻塞**（须提交后复跑干净树） | `repair/BLOCKED.md B-4` |
| 11 | soak 的 PASS 只能读作 crash-free | **已修（口径）**：README §6 增"PASS 判据边界" | `device/README.md §6` |

**修复环验证**：`pytest test_batch50_repair_invariants.py -q` **10 passed**；与既有套件合跑 **71 passed**；Figure/MVA **46 passed**；`frame_drift_check.py` exit 0；`recompute.py` all_match=true；`sample_d1.py --check` exit 2（如实红）。**未跑 Gradle/设备**（本环未改 Kotlin 生产码）。

### 6.2 本轮本记录员的复核发现（4 条，均已登记，不改 verdict）

1. **哈希清单滞后 4 项**（§5.3）：`GATES` 1 + `EVIDENCE` 3；复现者按旧清单会 exit 1。
2. **lint 162 条中 22 条点名批次 50 证据**（§5.1）：例如 B 迁移矩阵设备 JSON 被 72 行 PASS 共用；`--require-complete` 的 `REVIEW_CHECKS_SHARED` 20 组、202 行由此而来。批 49 的 lint 是 144 条且新证据 0 条；本批新增 18 条，**不能沿用"新证据 0 条入告警"的旧结论**。
3. **默认 runbook 载体覆盖 85/254**（计划载体 `blocked-runbook.md` 254/254）：载体错位批次 47 §6.4 已登记，本批未修；覆盖只证明出口条目存在，不证明条件取得。
4. **能力屏测试 hook 由整串等值改 `substring=true`**：已在 §0.5 登记为匹配放宽，不记"断言零放宽"。

### 6.3 复核边界

- 933 份行级复核全部为 `mechanical-recompute`、`independent_agent=false`；不是第二人/第二模型语义复核。
- **独立 + 对抗复核（计划 #41）未执行**：无第二主体签字件；修复环 findings 的"已独立复核"无独立产物可核。
- 本记录员未重跑 Gradle/设备/soak/Provider/VLM；§5 的 Gradle/设备读数为归档值，已逐项标注。

---

## 7. 遗留（全部如实，含 BLOCKED/UNVERIFIED）

1. **验收不通过**：`acceptance_report` exit 1；13 FAIL / 254 BLOCKED 未消除；`--require-complete` 两阻断（FAIL=13、20 组 shared checks）。不得签"批次 50 验收通过/发版就绪"。
2. **3D D-1 未过门**：`overall_pass=false`；normal 护栏 0.8979<0.90（差 1 条判定）；主判据 0.9757 的 κ **NOT RUN**，只是 model-labeled 暂定值；抽样框已覆盖、`--check` exit 2；D-2 两项退休未执行、容量补丁未应用。
3. **D-3 召回 117/129 保持**：12 条 MISS 同集（`等和线`/`活结与死结模型`/`力的分解` absent 边界不变）；词面 101/129（批次 19 文档的 102/129 是旧时点值）。
4. **3E 条件性通过**：第二档无预留份额；默认 20 分钟预算下第二档贡献可为 0；是否预留份额属产品裁定。
5. **MVA 不可评**：零生图/零真实 Provider/零 VLM judge/零设备；120 题质量结论不存在；`critical_errors_accepted=0` 不可引用；JS 不广告保持。
6. **AUD-08 FAIL**：① 7/24=0.2917<0.9；③ 1/78=0.0128 过但硬红线 frag=1；acceptance 8 页本批只跑一次但已在批次 48 消费（第二次观测）；`type` 键缺陷候选修复未进产品；hint 净效应不可分辨（A 4/16 vs B 4/16）。
7. **干净树 release 构建 BLOCKED**：`identity --require-clean` exit 1（构建时 4315 脏路径；当前 `-uall` 5356）；AAB/APK 非干净树产物，`revision=HEAD` 不代表所编代码；须提交后复跑。
8. **生产签名/Play 账号/备案号 BLOCKED**：J-020/J-021 仍 FAIL；本地 key `OU=NOT-PLAY-PRODUCTION`；H-024 公开 URL、H-036 Play 表单 BLOCKED；H-053 备案号/上线编号未回填；6 项外部信息全部"尚未取得"。
9. **16KB 运行时 UNVERIFIED**：唯一设备 4096 页/x86_64、API 34；无 arm64 真机；`aab16k --selftest` 不存在（R7 U-10 缺口）；成对 vc（N/N+1）升级/降级产物未做。
10. **商店素材/上架清单未产出**：Play 账号外部；证据目录未发现产物。
11. **AIGC 导出 background 2 例 FAIL**：`waitForTag` 20s 超时；条目在库且真实 app 手工可见；属 Compose 与分页懒列表时序/力学问题，"导出随页面丢"未被证伪也未被证明 → **UNVERIFIED**。
12. **5c 生产无候选生产者**：面板只显空态；确认/撤销证据是注入候选后走真实 UI；改判 supersede 无 UI。
13. **6B-3 真实 Provider 回执 UNVERIFIED**；app 级 NavHost 端到端未走。
14. **KD-67 第二道题无生产入口**：生成/上屏/作答 UI 未接；Elo 选项数与证据审计仍读会话题面槽。
15. **JS visualEngine 注入 BLOCKED**（MVA FAIL + KD-76 open + 无宿主帧源）；math 盒回放只做显式降级；KD-76/71/73/82 保持 open。
16. **单测门 exit 1**：10 模块 4294/11 红；11 红逐名均为已登记既有（core:data 4、core:database 4、feature:tutor 1、app 2），**未在干净 HEAD 回退树重演**，不记"HEAD 实证"。
17. **F-057 维持 FAIL**：2h soak 115 轮未命中"超限路径"（FATAL=0）；未命中不等于通过；soak 只覆盖 release+空库组合。
18. **行级复核机械 + shared checks**：933/933 有效但 `independent_agent=false`；655/933 行级特有；20 组 ≥5 行共用（202 行，最大 72）；`acceptance_lint` 162 条 `EVIDENCE_REUSED`（22 条点名批次 50）。
19. **哈希清单滞后**：GATES 1 项、EVIDENCE 3 项不匹配（修复环在冻结后改动）；提交前须决定是否重生成。
20. **独立/对抗复核未做**（计划 #41）；无第二主体语义复核产物。
21. **设备边界**：仅 emulator-5590（x86_64/API 34/4096 页），无真机/arm64；TalkBack/IME/对比度未做人工签字；设备库在恢复 debug 后含测试 `@Before` 播种夹具（`entry:m1:*`）。
22. **发布 versionName 仍字面量 `0.2.0`**（本批只注入 versionCode）；下次发版 versionCode 必须 `> 101160`。

---

## 8. 提交清单

**范围声明**：以下按各子线自报改动文件汇总，**不是全树 diff**；工作树当前 `git status --porcelain` **1643 行**、`-uall` **5356 路径**，含他线在飞文件（ReviewReminder/visualjs/知识线/kb 工具等）**不列入本清单**。提交时严禁 `git add -A` / `git add .`；以显式路径重列。`local.properties`（gitignored）、`build/**`、`.tmp/**`、`docs/research/evidence/**/*.log`（`*.log` 被 `.gitignore` 忽略）等不入库或需 owner 决定 `git add -f`。

> **状态时点**：HEAD `a87fc70d15c8ad726cee17422dbeef0b4977747d`（main），**批次 50 尚未提交**。`??`=未跟踪新增；`M`=已跟踪修改。台账/终裁/证据的最终字节以 §0.2/§5 的 sha 为准；提交会改变 `dirty_paths`，**不改变终裁**。

| 块 | 路径（组） | 状态 | 说明 |
|---|---|---|---|
| 1 3D D-2/D-3 | `core/database/.../dao/ProblemOrganizationDao.kt`、`core/database/.../RoomKnowledgeBaseStore.kt`（M）；`core/database/src/androidTest/.../KnowledgeRecallParentScopeInstrumentedTest.kt`（??）；`docs/research/evidence/2026-11-09-batch-50/{d1,d2,d3}/**`（??）；`docs/research/2026-11-09-binding-audit.md`（??） | M/?? | D-1 只交清单未改 KB；D-2 退休/容量补丁未执行；D-3 召回父节点作用域修复 |
| 2 3E | `core/data/src/androidTest/.../StudyReviewPlanner3EScanInstrumentedTest.kt`（M）；`core/domain/src/test/.../KnowledgeReviewQueueTest.kt`（M）；`.../3e/**`（??）；`docs/research/2026-11-09-3e-verification.md`（??） | M/?? | 只改夹具不改生产码；断言意图未删 |
| 3 AUD-08 切分 | `core/model/.../layout/LayoutStructure.kt`（??）、`core/model/.../ModelTasks.kt`（M）；`core/data/.../model/PageLayoutHint.kt`（??）、`OpenAiCompatibleModelTaskAdapters.kt`（M）、`OpenAiCompatibleModelGateway.kt`（M）；`core/data/src/test/.../GoldenSplitEvalHintChannelHarness.kt`（??）；`tools/split_golden/check_split_gate.py`（M）；`.../split-gate/**`、`step19b-*`（??）；`docs/research/2026-11-09-split-cv-llm.md`（??） | M/?? | 提示不进指纹/不落库；acceptance 只跑一次；门 FAIL |
| 4 MVA | `tools/figure_eval/mva_gate.py`（??）、`test_mva_gate.py`（??）、`judge_channel_map.json`（??）、`figure_eval_gate.py`（M，citable 标注）；`.../mva/**`（??）；`docs/research/2026-11-09-mva-judge.md`（??） | M/?? | 门值/判定未变；零生图 |
| 5 5c 错因 UI | `core/data/.../study/ErrorCauseReviewService.kt`（??）+ test（??）+ androidTest（??）；`core/database/.../dao/ErrorCauseDao.kt`、`port/ErrorCausePort.kt`、`RoomStudyDatabase.kt`（M，仅 override 6 行）；`feature/library/.../MistakeErrorCausePanel.kt`（??）+ 两测试（??）；`app/.../MistakeErrorCausePanelHost.kt`（??）、`ErrorCauseWriteMessageTest.kt`（??）；`MistakeDetailRoute.kt`、`SmartMistakeBookApplication.kt`、`SmartMistakeBookRoot.kt`（M，最小 hook） | M/?? | 生产无候选生产者；`USER_CONFIRMED` 与口述 `USER_CORRECTED` 的差异已登记 |
| 6 5b 容量 UI | `core/domain/.../StudyReviewCapacity.kt`（??）、`StudyExperienceRepository.kt`/`StudyReviewOverview` 等（M）；`core/data/.../StudySnapshotBuilder.kt`、`StudyReviewPlannerService.kt`（M）；`feature/review/.../ReviewCapacityCopy.kt`（??）、`ReviewRoute.kt`（M）；测试（??/M）；`.../5b/**`（??） | M/?? | KD-68 关闭；`rebalanceEnabled` 恒 false |
| 7 6B-3 改卷 UI | `feature/library/.../NativePaperEditModel.kt`、`NativePaperEditModelSection.kt`（??）+ JVM/设备测试（??）；`MistakePaperEditRoute.kt`、`MistakeDetailRoute.kt`、`SmartMistakeBookRoot.kt`（M，最小 hook）；`.../6b-3/**`（??） | M/?? | 真实 Provider UNVERIFIED；与 5c 共享 Root/Detail hook |
| 8 JS | `core/data/.../visual/VisualDrawOpsPainter.kt`、`VisualDrawOpsPainterPure.kt`、`VisualMeasuredLayoutArtifact.kt`（M）；`.../VisualDrawOpsPainterInstrumentedTest.kt`（M）；`docs/current/visual-generation-contract.md`、`docs/known-defects.md`（M）；`.../js/**`（??） | M/?? | math 降级；KD-78 关闭；visualEngine 未注入 |
| 9 KD-67 第二题 | `core/data/.../study/{AdaptiveSelectionAssembler,KnowledgeQuizItemStore,KnowledgeQuizItemVerificationUpgrader,KnowledgeQuizFeedbackWriter,RoomBackedStudyExperienceRepository}.kt`（M）；`core/domain/.../StudyExperienceRepository.kt`（M）；新增测试（??）；`.../kd67/**`（??） | M/?? | 真 Room e2e OK(3)；UI 入口缺 |
| 10 AIGC | `core/ui/.../AiContentBadge.kt`（??）、`AttachedImageCard.kt`（M）；`core/export/.../{AiGeneratedDocumentNotice.kt（??）,MinimalDocxWriter,DocxMistakeRenderer,MistakeDocxExporter,MistakePdfPagePlanner,MistakePdfPaperEdit,MistakePaperDraftStore}.kt`（M）；`core/model/.../PdfAigcContentPolicy.kt`（M）；`feature/tutor/.../{TutorPaperEditApplier,TutorGeneratedTurn}.kt`（M）；`feature/review/.../ReviewSessionScreen.kt`（M）；新增用例（??）；`docs/{known-defects,ai-content-disclosure-copy}.md`（M）；`.../aigc/**`（??） | M/?? | H-052/061/062 PASS；H-053 外部红；DOCX/PDF 独立读回 |
| 11 隐私 | `app/.../PrivacyPolicyCopy.kt`（??）、`PrivacyPolicyCopyTest.kt`（??）、`SecondaryScreens.kt`（M，66 insertions/0 deletions）；`docs/current/play-data-safety-answers.md`（??）、`privacy-and-data-safety.md`（M）；`.../privacy/**`（??） | M/?? | H-025 PASS；H-024/H-036 BLOCKED；6 项外部"尚未取得" |
| 12 发布 | `app/build.gradle.kts`（M，+32/−1）；`tools/ci/{release_version_code.py,aab_manifest_version.py}`（??）、`j_release_verify.py`（??）+ 三个测试（??）；`.../release/**`（??）；`docs/research/2026-11-09-release-signing.md`（??） | M/?? | versionCode 101160；本地 key 非 Play；脏树产物 |
| 13 设备/验收 | `docs/acceptance/ledger.md`（M）；`docs/acceptance/verdicts/rows/**`（M）；`docs/acceptance/{acceptance-report.json,verdicts.md,domain-index.md}`（M）；`docs/acceptance/evidence/2026-11-09-batch-50/**`（??）；`.../device/**`（??） | M/?? | 台账 sha `9044a779…`；REVIEWS 947/947、GATES 34/35、EVIDENCE 670/673；行级复核机械 |
| 14 记录/研究 | `docs/research/2026-11-09-batch-50-{baseline,record}.md`（??）；`2026-11-09-{3e-verification,binding-audit,mva-judge,release-signing,split-cv-llm}.md`（??）；`docs/acceptance/adjudication.md`（M，B50 终裁） | M/?? | 本块与 `adjudication.md` B50 终裁同时落盘 |

- **建议提交顺序**：块 1–2（KB/3E 数据层）→ 块 3–4（切分/MVA 工具）→ 块 5–9（产品面）→ 块 10–12（合规/发布）→ 块 13（验收证据，绑台账 sha）→ 块 14（记录/终裁）。
- **禁止**：`git add -A`；他线 `ReviewReminder*`/visualjs/知识线/kb 工具/`feature/library/build.gradle.kts`/`.gitignore`（非本批自报）；`local.properties`；`build/**`；`.tmp/**`；`docs/research/evidence/**/*.log`（除非 owner 决定 `git add -f`）。
- **提交前复核**：重跑 §5.1 的 `acceptance_report`（预期 exit 1、933/13/254/0）、`--require-complete`（预期 exit 1）、`trace_requirements`（0 gaps）；决定是否重生成 `GATES.sha256`/`EVIDENCE.sha256`（当前 34/35、670/673）；提交后按 `release/release-50.md §8.2` 复跑干净树 release 构建。**提交不改变终裁：NOT ACCEPTED 维持。**



## 9. 主会话收口（2026-11-09）

### 9.1 主会话实跑

| 项 | 结果 |
|---|---|
| 单测门（12 模块） | exit=1；9 红全部为 HEAD 既存他线语义（KernelWave0/LibraryFts×3/Gateway×3/profileStrengths/TutorExportSheet） |
| 强门（两 flavor androidTest 强制重编 + assemble） | **exit=0** |
| 验收报告 | **PASS 933 / FAIL 13 / BLOCKED 254 / UNVERIFIED 0**；`pass_with_review=933`；exit=1 |
| 四门 | **3D `PASS main / FAIL guardrail` → overall 不通过（normal 0.8979<0.90）**；3E 不得计无条件 PASS；MVA **FAIL（0 可评样本、零真实 Provider/judge）**；AUD-08 **FAIL（① 7/24=0.2917、③ 1/78=0.0128）** |
| 发布 | 本地签名 AAB、versionCode 101160、mapping、AAB 16KB 静态校验成立；**干净树构建 BLOCKED（dirty 4315）、生产签名/Play/备案/公开隐私 URL BLOCKED、H-053 未回填** → **发版判定不成立** |
| 复核 | 本批**无独立/对抗复核产物**（记录 #41 ✗），行级复核 `independent_agent=false`；不得称“已通过双复核” |

### 9.2 交付与残留

- 已落：数据安全机器块修复（?待核）+ 75 迁移/备份设备复现；D-2/D-3 子线；3E 真实队列验证；切分 CV 提示与门读数；MVA harness/两臂/确定性检查（质量门未过）；5c 教学候选面板、5b 容量区块、6B-3 改卷入口、JS math 显式降级/KD-78 ①②、KD-67 第二题；AIGC 五项、隐私政策、发布脚本/本地签名/AAB 校验。
- 残留：3D D-1 改绑未执行（guardrail 0.8979）、MVA 零真实样本/ VLM judge 未实现、AUD-08 0.2917、干净树/生产签名/Play/备案、独立复核缺失；KD-76/71/73/82 仍 open；JS 不广告。
- 提交：批次50 共 15 笔（版面/错因/容量/改卷/视觉/AIGC/隐私/发布/台账/行级/证据/记录），已推送 `5a959619`。

**一句话**：本轮把“缺的实现”基本落成了代码与证据，但**四门未过、发布外部项未取得、独立复核缺失**，因此“发版水平”不成立；下一轮应只做四门收口与发布外部项，不再扩功能。

## 10. 主会话纠偏（2026-11-09）：CV 切题算法删除

用户裁定：**切题靠模型识别，算法切分鸡肋，直接删掉**。已执行：

- `git revert 26ee51e1`（-1586 行）：删除 `core/model/.../layout/LayoutStructure.kt`、`PageLayoutHint.kt`、`LayoutHintContractTest`、`LayoutStructureTest`/DevParityHarness、`PageLayoutHintInstrumentedTest`、`CaptureAssessmentLayoutHintPromptTest`；
- 还原 `OpenAiCompatibleModelGateway.kt` 的 layoutHint 接线（版面测量端口/生产 `AndroidPageLayoutHint` 装配全部移除）；
- 删除未跟踪的 `GoldenSplitEvalHintChannelHarness.kt` 与 `tools/layout_probe/`；
- 保留 `docs/research/2026-11-09-split-cv-llm.md` 并在文首标 **REJECTED（仅负结果研究）**。

切题自此只有模型识别一条路径；`AUD-08` 的门值与结论须在模型-only 口径下重测，不能再引用 CV 臂/hint 臂读数。
