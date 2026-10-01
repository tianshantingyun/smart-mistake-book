# Wave 3 批次 2 实施规格（P8 展示统一 · KF-20 机制层 · P9 rating 基址 · 仪器化旧串）

> **定位**：`docs/research/2026-09-30-wave3-plan.md` 批次 2（W3-2）四项的实施规格——
> ① P8 展示统一；② KF-20 OLM 机制层（视觉不动）；③ P9 rating 基址统一；④ 仪器化测试旧复合串
> 清单与应改值。每项按「决定 → 依据 → 消灭的失败 → 逐文件改法 → 测试锚点 → 版本 → 边界」写。
>
> **口径**：一律以台账「裁决 13 · 修订（E 判据）」为准——
> `docs/agent-first-refactor-decisions-2026-09-23.md:2011-2024`（MASTERED = 知识点记忆状态：稳定度
> ≥21 天 ∧ 当前召回概率 ≥0.9；β-二项 p̂ + Wilson 降为展示层）与 `:2059-2075`（落地补记）。
> 展示层保留「保守分（Wilson 下界）+ 区间 + 证据摘要」（`:2024`；`docs/research/2026-09-30-mastery-criterion-evidence.md:61`）。
>
> **证据口径**：本文每条决定的依据都标 `file:line`（本次读码核实，2026-10-01）或来源；引用
> 外部研究处按其本身等级标注（一手全文／摘要级／著录级），未核实项在 §2.3 末尾集中声明。

## 0. 范围与现状快照（开工前必读）

### 0.1 四项一句话

| 项 | 一句话 | 版本影响 |
|---|---|---|
| P8 | 三个掌握展示面统一到「Wilson 下界（保守分）+ 区间 + 档位」；档位切点单源进 `core:ui`，顶档边界落 0.85 | 零 bump（不动投影/落库值） |
| KF-20 | 机制层（视觉不动）：区间与证据摘要在 DB 读面 → 端口 → MASTERY_READ → UI 摘要读面暴露；术语按研究附录 E 对齐并补缺 | 零 bump（读面重算） |
| P9 | `core:model` 新增 `StoredReviewRating`（1-based 唯一基址）；`core:domain` 的 `FsrsRating` 收成唯一换算面；逐处替换 | 零 bump（落库值逐位不变） |
| ④ | 仪器化旧复合串 8 处 / 6 文件逐项裁定：**3 处有意遗留不改（#1–#3）**；**4 处陈旧「当前版」改绑 `LearningProjector.VERSION`（#4–#7）**；1 处投影名位对齐生产名（#8） | 测试文件改动，无产品版本影响 |

### 0.2 现状快照（哪些已在树里、哪些没有）

- 批次 1 已提交（`ec223a33`「β-二项掌握表示 + 知识点记忆卡」）：s/f 权重、记忆卡、`projector-v9 /
  learning-core-v9 / skip-v4`、v56 迁移；`MasteryEstimateMath`（`core/domain/…/MasteryEstimateMath.kt:1-77`）。
- 工作树有**未提交**的批次 2 在飞改动（另一会话，勿动；`git status --porcelain core/domain core/model`
  实测六项）：`core/domain` 的 `ClearlyMasteredForSkipPolicy.kt`（KF-16 的 `prerequisiteStabilityDays`，
  工作树 `:78-114`）、`KnowledgeReadiness.kt`（`effectiveStabilityDays`，工作树 `:82-96`）、
  `LearningProjector.kt`（P11：删 `generatePredictions`/`computeFeatureFingerprint` 与
  `FUTURE_TIMESTAMP_CLAMPED` 分支）、`KnowledgeReadinessTest.kt`、`MasteryMemoryCardTest.kt`，
  以及 `core/model` 的 `LearningState.kt`（P11 的枚举注释）；`core/data`、`core/database` 本次无在飞改动。
  本规格四项**均尚未落树**（逐项现状见各节）。
- 批次 2 的其余条目（`wave3-plan.md:64-76` 的 §3.1/3.2/3.6/3.7：判据出口函数收敛、`isSatisfied` 读取时
  重算、KF-16 接线、文档同步、E 边界测试）**不在本文四项内**；其中「跳过策略共用同一出口函数」这半句
  的排程侧（`ReviewPlanner.kt:456-467` 现仍用 `status + MAX_EVIDENCE_AGE_MILLIS` 判跳过）留给批次 2 实施者
  按 plan 处理，本文不动它。
- 共享工作树纪律：另一会话直接向 main 提交；提交用显式文件列表
  （`docs/research/2026-09-28-kernel-execution-steps.md:115`，§5 注意事项第 1 条；§5 标题在 `:113`）。

### 0.3 版本裁定（四项一次给全）

四项**都不动投影公式、不动落库值、不动 Room schema → 零 bump**：
- P8/KF-20 是展示与读面（读面从 s/f **重算**区间，不落库、不加列）；
- P9 只改换算写法，`review_log.rating` 的落库值逐位不变（`ReviewLogSink.kt:108` 的 `ordinal + 1`
  与新 `storedRating` 同值）。
依据：roadmap §0 第 1 条（动投影公式，`:17-18`）与第 3 条（动 Room schema，`:21-22`）——
`docs/research/2026-09-28-kernel-remediation-roadmap.md:17-22`。

---

## 1. P8 展示统一：Wilson 下界（保守分）+ 区间 + 档位

**现状（读码核实）**：三个掌握展示面已经都用 conservative（好），但**内部口径三分**、档位切点两处
各写一份字面量（审计 P8 行 `…kernel-scale-precision-audit.md:37`）：
- 掌握库 `app/…/LearningMasteryScreen.kt`：进度条取 conservative（`:345`）、状态词（`:384-390`）、
  证据档（`:424-426`，`evidenceMass >= 1.0 && count >= 2`）、风险档**自写** `0.7/0.4`（`:440-443`）；
- 学习档案 `feature/profile/…/ProfileRoute.kt`：`mastery = conservative`（`:266`）→ `masteryBandLabel`
  （`:293`）；detail 文案 `:265`；
- 复习页 `feature/review/…/ReviewRoute.kt`：`masteryBandLabel`（`:149-151`）。
- 共享函数在 `core/ui/…/MasteryBands.kt:8-18`（`0.7/0.4` 字面量；`retentionBandLabel` 共用同一对）。

### 1.1 决定

**决定 1-A（档位常量放哪）**：切点常量放 `core/ui/src/main/java/com/tingyun/smartmistakebook/core/ui/MasteryBands.kt`
（全仓唯一来源），`masteryBandLabel` / `retentionBandLabel` / `app` 的 `forgettingRiskLabel` 三处标签全部引用：

```kotlin
/** 顶档边界 = 裁决 13 修订保留的展示带边界 θ=0.85（不再作判据）。 */
const val MASTERY_STRONG_THRESHOLD = 0.85
/** 中档边界 = 现状值 0.4（无证据要求改）。 */
const val MASTERY_FAIR_THRESHOLD = 0.4
```

[依据：`MasteryBands.kt:8-18` 已是三处档位函数的共享落点；`app` 与三个 feature 模块都已依赖 `core:ui`
（`app/build.gradle.kts:140`、`feature/profile/build.gradle.kts:20`、`feature/review/build.gradle.kts:20`、
`feature/tutor/build.gradle.kts:31`）。顶档取 0.85 的依据：`decisions:2024`「θ=0.85 保留为展示带边界，不再作判据」。]
[消灭的失败：`MasteryBands.kt:9-10` 与 `LearningMasteryScreen.kt:441-442` 两处同值字面量、无机械联系——
审计 R-03 的原话是「改其中一处的阈值不会让另一处编译失败或测试变红，**两个界面会对同一分数给出不同档位**
（例如把 MasteryBands 上调到 0.75 后，掌握页仍按 0.7 判档）」（`docs/audit-2026-09-12-kernel-readiness.md:1453`；
`:1457` 列出两处字面量与证据档的第三套阈值）。「同一张卡就会**同时**显示『较稳』与『遗忘风险：高』」
与「两者都在 `[0,1]` 上，语义都是『学生对它有多牢』」两句出自提交 `9522545d` 的提案说明与**未合并分支**
的文件头（`.worktrees/kernel-readiness/core/ui/…/MasteryBands.kt:8-10`、`:16-17`，本次实读）。
R-03 的修复只落在该未合并分支（`git merge-base --is-ancestor 9522545d HEAD` 实测 NOT ancestor），
主源集至今仍是缺陷态——本项把它落回 main。]

**决定 1-B（三面统一到的三元组）**：掌握类展示面统一为「状态（E 判据，投影产出，不动）+ 档位（保守分
切档，单源函数）+ 区间（s/f 重算的 Wilson 上下界，粗粒度文案）」，三项都从同一个 `StudyKnowledgeSummary`
取；任何一面不许自算、自抄阈值。
[依据：`wave3-plan.md:73`「三展示面统一 conservative + 区间 + 档位，档位常量单源」；roadmap `:218`
「P8：展示统一用 conservative+区间」；审计 P8 行 `…kernel-scale-precision-audit.md:37`。]

**决定 1-C（数据面）**：`StudyKnowledgeSummary`（core:domain）加两个**带默认值**的字段：
`masteryIntervalUpper: Double? = null`、`memoryStabilityDays: Double? = null`；由
`StudyExperienceMappers.toProfileOverview` 现算填入——区间上下界**同一次** `MasteryEstimateMath.interval(s, f)`
产出，下界写回既有 `conservativeMasteryScore` 字段（该字段即 summary 的保守分，排序也在用）。
[依据：`core/domain/…/StudyExperienceRepository.kt:118-146`（定义；带默认值让既有构造点全部不破——本次
实测构造点共 **23 处**（`grep -rn "StudyKnowledgeSummary("` 24 命中减 1 处 data class 声明）：生产 **2 处**
（`core/data/…/StudyExperienceMappers.kt:105/:130`）、测试夹具 **21 处**（`app/src/androidTest/…/LearningMasteryScreenInstrumentedTest.kt:101`、
`feature/profile/src/test/…/LearningMasterySubtitleTest.kt:39`、`feature/tutor/src/test/…/TutorModelTaskPolicyTest.kt`
19 处）；`MasteryEstimateMath.kt:43-67`；
投影侧同一函数 `LearningProjector.kt:651-655`、`:1052-1056`。]
[消灭的失败：读面只有单点、无区间——KF-20「区间而非单点」在 UI 面无处可拿（`decisions:1694-1698` 裁决 11）；
同时以「读面重算 + 与投影同函数」保住一个可测不变量（§1.4 锚 3）。]

**决定 1-D（区间是读面重算，不落库）**：不新增投影列、不加迁移——`learner_knowledge_mastery_state`
已有 `success_weight/failure_weight`（`core/database/…/entity/LearningEntities.kt:837-840`，v56 已落），
读面按需重算。
[消灭的失败：为显示一个**派生量**加列 = schema bump + 迁移 + 全量重放，成本与收益不成比例；且派生量
落库会造出「列值与 s/f 不一致」的新漂移面（本仓纪律：同值两处=漂移温床，`KnowledgeReadiness.kt` 文件头
既有明训，见审计 `:1453` 引用）。]

**决定 1-E（档位词与顶档边界）**：档位**词不变**（较稳/一般/薄弱；记忆档 记忆较稳/记忆减弱/记忆模糊），
顶档边界由 0.7 → 0.85。口径说明（写进代码注释与本文档）：档位 =「即使按保守估计，证据能撑到多高」的
**证据带**，不再是判据；「掌握/没掌握」只由状态列（E 判据）表达。**若裁决方有另一种读法（0.85 只当
参考线、切点仍 0.7），改一处常量即可**——这正是单源的意义；实施前把本条与 `decisions:2024` 原句对读一次。
[依据：`decisions:2024`（θ=0.85 保留为展示带边界）；现值 `MasteryBands.kt:9-10`。]

### 1.2 各面怎么改（逐面）

| 面 | 现状（file:line） | 改法 |
|---|---|---|
| 掌握库（app 主源集） | 行 = 名称 + 状态词（`:367`）+ 进度条（保守分，`:345`）+ 无障碍串（`:350-352`）；风险档自写 0.7/0.4（`:440-443`）；证据档自写 1.0/2（`:424-426`） | ① `forgettingRiskLabel` 改引 `MASTERY_STRONG_THRESHOLD / MASTERY_FAIR_THRESHOLD`（由 `private` 改 `internal` 供跨模块断言，R-03 先例 `audit:1884`）；② 行内**新增一行小字**区间文案（复用现有 `bodySmall`/`InkSecondary` 样式，不新造视觉组件）；③ 无障碍串（`:350-352`）补区间；④ 证据档阈值收进同文件常量、值不改（`1.0 / 2`）——它描述证据量不是掌握档，但同属「未单源的展示阈值」，审计 `:1457` 已点名，顺手收口 |
| 学习档案 | `WeaknessRow`：detail = 状态词 + 「根据多次独立作答估计」（`:265`）；主数值 = conservative（`:266`）；档位 = core:ui（`:293`） | detail 追加区间文案；档位引用不变（已单源）；`:293` 传参不变（conservative） |
| 复习页 | 薄弱点 detail =「独立作答把握：」+ 档位（`:149-151`） | detail 追加「（约 X–Y 成）」；档位引用不变 |
| 题卡记忆卡（feature:tutor，**非 KC 掌握面**） | `retentionBandLabel(memory.retrievabilityAtSnapshot)`（`SavedMistakeTutorRoute.kt:600-603`） | **代码不动**；因共用同一对常量，其顶档边界随之 0.7→0.85——**影响登记**：这是 R-03 明确的有意设计（「两者都在 `[0,1]` 上，语义都是『学生对它有多牢』」出自**未合并分支**的文件头 `.worktrees/kernel-readiness/core/ui/…/MasteryBands.kt:16-17`，本次实读），不做第二对常量（那会复活「同值两常量」模式） |

### 1.3 文案与格式规则（新增展示函数的协议）

在 `core/ui/…/MasteryBands.kt` 增加**唯一**的区间文案函数（三个面只准用它）：

```kotlin
/**
 * 粗粒度区间文案（学生语言；无小数、无内部术语）。
 * @param lower Wilson 下界（= conservativeMasteryScore）
 * @param upper Wilson 上界；null = 无证据（映射侧对 s+f<=0 传 null）
 */
fun masteryIntervalLabel(lower: Double, upper: Double?): String
```

规则（取整到 **10 个百分点**，宽区间不报数）：

1. `upper == null` → 「证据还少」；
2. `upper - lower >= 0.5` → 「证据还少」（宽区间 = 证据少，报数会被读成确信）；
3. 否则 `lo = floor(lower*10)`, `hi = ceil(upper*10)`（clamp `0..10`）→ `lo == hi` 时「约 $lo 成」，
   否则「约 $lo–$hi 成」。示例：`0.6058 / 0.9854 → 「约 6–10 成」`；`0.21 / 0.62 → 「约 2–7 成」`。

依据与边界：
- 「数值区间/证据量提示优于口头不确定词」「不要小数位精度」「粗到 5–10 个百分点」——外部调研三条
  （`van der Bles et al. 2020 PNAS` 摘要级、`Jerez-Fernandez et al. 2014` 著录级、`Hoekstra et al. 2014`
  著录级；分析见本 ask 提供的「外部·掌握度展示」结论第 2/3 条）。
- 「证据还少」措辞是本规格草案；若文案冻结方认为与 `docs/student-experience-spec.md:108` 的
  「证据不足」禁语相冲（该禁令语义范围是**没有任何可信学习事实**的空态，本处是**有证据但少**的行级
  提示），换成同义句即可——口径（宽区间不报数）不变。学生面禁词底线：`student-experience-spec.md:84`
  规则 11 + 实测禁词表 `app/src/androidTest/…/LearningMasteryScreenInstrumentedTest.kt:116-134`
  （`FORBIDDEN_STUDENT_TERMS`，在 `:75` 被使用；含「置信度/节点/数据库」等；建议把「保守分/下界/Wilson」
  一并加进该表，防内部术语漏出）。
- 不展示点估计小数（如 `0.87`）：`conservativeMasteryScore` 只驱动进度条与排序，不作为学生可读数字
  出现——`decisions:2024`「区间=透明度」＋外部调研「不要展示 87.3% 这类小数位精度」。

### 1.4 测试锚点

1. **`core/ui/src/test/…/MasteryBandsTest.kt`（新增，JVM）**：① 两个常量值钉死（0.85 / 0.4，变异即红）；
   ② 两档函数在**恰好等于切点**上的行为（`>=` 的边界格）；③ `masteryIntervalLabel` 的取整与分支
   （`null→证据还少`、宽区间→证据还少、`0.6058/0.9854→约 6–10 成`、`0.21/0.62→约 2–7 成`、`lo==hi` 单值）。
   依据：core:ui 已有测试源集与 JUnit（`core/ui/build.gradle.kts` 的 `testImplementation(libs.junit)`；
   现有 6 个 JVM 测试文件）。
2. **`app/src/test/…/MasteryBandConsistencyTest.kt`（新增，把 R-03 分支的 5 条搬回 main）**：强档/中档
   边界两处一致、常量是文档值、扫 `[0,1]` 101 点网格断言「掌握档说较稳 ⇒ 风险档必须说低」、保持率档
   共用同一对切点。**搬入时必须改一条**：分支的 `theCutPointsAreTheDocumentedOnes` 现断言
   `assertEquals(0.7, MASTERY_STRONG_THRESHOLD, 0.0)`（分支文件 `:83-87` 实读），搬迁后应改钉 **0.85**
   ——否则照字面搬就是一条红用例，且与锚 1 互斥。依据：R-03 的测试设计与变异证据（`audit:1884`）；
   该测试只存在于未合并分支（实测 `find` 命中 `.worktrees/kernel-readiness/app/src/test/…/MasteryBandConsistencyTest.kt`）。
3. **`core/data/src/test/…`（映射一致性，新增）**：给定 s/f 的 `KnowledgeMasteryState`，断言 mapper 输出
   `conservativeMasteryScore == MasteryEstimateMath.lowerBound(s,f)` 且 `masteryIntervalUpper == interval(s,f).upper`。
   消灭的失败：读面重算与投影落库若将来分叉（改了一处公式），此用例当场红。
4. **仪器化 `LearningMasteryScreenInstrumentedTest`**：禁词表（`:116-134`）继续全绿（新文案不得含禁词）；
   新增「行内含『约』或『证据还少』」的存在性断言（对应新文案）。
5. **回归**：`feature:profile` 的 `LearningMasterySubtitleTest`、`feature:tutor` 的 `TutorModelTaskPolicyTest`
   等 summary 夹具因新字段带默认值不需改，应保持绿。

### 1.5 边界与非目标

- **不做视觉编码**：附录 E 第 1 条的视觉变量（透明度=区间宽度、颜色=状态档）与学习档案整体视觉归
  插眼 4（`decisions:1694-1698` 裁决 11「视觉层不动」；`…algorithm-fix-plan.md:290-295`「与学习档案视觉
  （插眼 4，先问用户）联动」）。本项只做**文案层**（复用现有 Text/样式槽位）。
- 不改投影（状态列/排序/判据）、不把档位当判据。
- 掌握库顶部三计数（`:178-180`，计数在 `:144-149`「掌握较稳/正在巩固/有记录科目」）仍按状态（E）统计，不动。
- **出题提示里的 raw**（`feature/review/…/KnowledgeReviewRequests.kt:46` → `OpenAiModelTaskAdapters.kt:624`
  的 `lastMasteryScore`）**不在本项**：它不是展示面（prompt 已声明「不得在输出中提及」`:618`）；如要改
  口径属另一条决定，登记待裁。
- 展示面状态**不做**读取时 `now` 重算降档（读取时重算在判据消费点已有：`AdaptiveQuestionSelector.kt:144`
  以 `decisionAtEpochMillis` 调 `isSatisfied`）；若将来要求展示面同样按 now 降档，另立条目（新增机制）。

---

## 2. KF-20 OLM 机制层（视觉不动）：区间与证据摘要的读面暴露 + 术语

**口径来源（仓库一手）**：裁决 11 = **做机制层**——「随 KF-09/10（β-二项 + Wilson）同批：**区间而非单点**
（透明度=区间宽度、颜色=状态档）、**证据摘要下钻**、**scrutable 术语**（附录 E 规范）。**视觉层不动**——
归插眼 4」（`docs/agent-first-refactor-decisions-2026-09-23.md:1694-1698`）；批次计划句「读面暴露区间
（s/f 重算 Wilson 上下界）+ 证据摘要下钻」（`wave3-plan.md:71`）。

### 2.1 决定（读面清单：谁暴露什么）

| # | 读面 | 决定 | 依据 / 消灭的失败 |
|---|---|---|---|
| 1 | `MasteryOverviewDao.readSubjectMastery`（core:database，SQL）+ `SubjectMasteryRow` | **加 4 列**：`success_weight`、`failure_weight`、`memory_stability_days`、`last_attempt_at_epoch_millis`（SELECT 现有 `:39-46` 一段同处加） | 区间必须从 s/f 重算（`wave3-plan.md:71`）；记忆卡是 E 判据的展示依据（`decisions:2011-2024`）。失败：读面只有 `probability/lower_bound/evidence_mass/status`，区间与记忆在工具面不可得 |
| 2 | `TutorMasteryOverviewPort.SubjectMasteryRecord`（core:database port） | 同 4 字段补齐（`KnowledgePort.kt:276-289`）；由此 `RoomStudyDatabaseMappings.kt:463-476` 的 `toRecord` 与 `RoomMasteryOverviewStore.kt:21-28` 透传 | 端口是工具与存储的契约；不加字段则 #3 拿不到原料 |
| 3 | MASTERY_READ（core:data `RoomTutorToolRunner`，分派 `:205`，实现 `:548-631`，渲染 `:652-698`） | **行格式草案见 §2.2**；下钻（`:699-718`）加记忆行 | 裁决 11「区间而非单点 + 证据摘要下钻」；工具是讲题模型唯一的深读面 |
| 4 | UI 摘要读面：`StudyExperienceMappers.toProfileOverview` → `StudyKnowledgeSummary` | §1 决定 1-C 已定（区间 + 记忆稳定度进 summary，三展示面消费） | `wave3-plan.md:73`；`decisions:1694-1698` |
| 5 | 讲题模型 evidence 块（`feature/tutor/…/TutorModelTaskPolicy.kt:549-620`） | **本轮不加**（保持 lowerBound+mass+计数）。理由：它是**模型输入字段**（加字段触发 roadmap §0 第 2 条：抹平空载体 + 4 用例 + 指纹纪律），而块内已有下界/证据量/计数，区间缺失指认不出具体失败 | roadmap §0 第 2 条 `…kernel-remediation-roadmap.md:20`；「新增必指认」纪律 |
| 6 | 题卡记忆卡（`SavedMistakeTutorRoute.kt:600-603`，retrievability 面） | 不动（非 KC 掌握面；保持率是题卡快照量） | 与 §1.2 同（仅常量共用后的边界影响已登记） |
| 7 | 其它工具读面（KNOWLEDGE_READ / NOTEBOOK_READ / 会话快照等） | 不动 | 与本项无关；避免扩面 |

### 2.2 MASTERY_READ 行格式草案（含下钻）

现状（`RoomTutorToolRunner.kt:678-687`）：`列：序号. 名称|粒度|保守掌握度|证据量|状态|最近证据|最近独立错误|绑定错题数`。

**草案（行）**——只在「保守掌握度」后插入「区间」一列（`MasteryEstimateMath.interval(s, f)` 现算，两位小数）：

```
列：序号. 名称|粒度|保守掌握度|区间|证据量|状态|最近证据|最近独立错误|绑定错题数
1. 函数单调性|ATOMIC|0.12|0.12~0.99|1.00|LEARNING|3天前|15天前|2
```
（示例取自代码注释里的手算对照：s=1,f=0 → 下界 0.117906、上界 0.985366，两位小数即 `0.12~0.99`；
文案格式由实现定，口径 = 「在同一行里能同时读到下界与区间」。）

**草案（聚焦查询的下钻，接在 `appendAggregateDetail` 的三行之后）**——无记忆卡时不输出该行：

```
   记忆：稳定度 12.3 天，上次作答 5 天前
```

配套（缺一即脱节）：
- `renderMasteryRead` 的截断说明与预算不动（`TutorToolOutcome.MAX_TOOL_RESULT_CHARS = 2_000` /
  `_EXTENDED = 6_000`，`core/model/…/TutorToolLoop.kt:178`、`:197`）；**成本提示**：每行约 +10 字符，
  截断行为由既有 `BudgetedLines` 兜底（`:727-750`）——若实测行数明显缩水，退路是**只在下钻给区间**、
  行内保 `保守掌握度` 单值（记录该退路，实施时按实测选，口径仍是「有区间可看」）。
- 工具描述同步：`core/model/…/TutorToolDescriptions.kt:20-29`（MASTERY_READ 长版逐字列出列名）必须
  加上「区间」「记忆（稳定度/上次作答）」；该文件是工具面文案单源（文件头注释 `:3-10`）。
- 排序不变：仍按 `lowerBoundIndependentCorrect` 最弱优先（`:599-604`，注释「截断时丢的必须是最不紧急的」）。
  **不改**排序口径（改它属另一条决定；S9 预聚合归 3C）。

### 2.3 术语：附录 E 对齐 + 缺口补齐（含来源等级）

**附录 E 定位（已找到，核实结论）**：`docs/research/2026-09-28-kernel-remediation-roadmap.md:306`
「### E. OLM 可视化规范（KF-20 输入）」，正文 `:308-310` 三条规范句（逐字）：

1. 「不确定度视觉变量：**透明度=区间宽度、颜色=状态档**；区间而非单点。」
2. 「对齐展示：学生自评（若 D-Q9 提供入口）与系统估计并排 + 差异高亮（UMUAI 证据：提升知识监测）。」
3. 「Judy Kay scrutable 原则：每个数可下钻到证据摘要；术语全学生语言。」

**缺口（实测声明）**：§E **没有独立「术语→定义」词表**——`wave3-plan.md:111` 把「研究附录 E 术语表」
列为『待核清单』，本次核实结论是「只有三条规范句、无词表」（`grep -rn "scrutable" docs` 在本文落盘前
仅命中 `decisions:1698` 与 `roadmap:310` 两处，全仓无第二份附录 E）。因此按 ask 的兜底分支补表；「术语全学生语言」
的成文规则另见 `docs/controlled-knowledge-research-design.md:97`（学生端语言边界）与
`docs/student-experience-spec.md:84`（规则 11）。

**术语表（内部 → 学生可见 / 读面用词；来源等级逐条标）**：

| 内部术语 | 学生可见/读面措辞 | 来源与等级 |
|---|---|---|
| 区间（Wilson 上下界） | 「约 X–Y 成」/「证据还少」（§1.3 函数） | §E 第 1 句「区间而非单点」[仓库一手]；外部：数值区间不降信任、口头不确定词小幅降信任（van der Bles 2020 PNAS，**摘要级**）；小数位被读成确信（Jerez-Fernandez 2014，**著录级**）；区间本身会被误读（Hoekstra 2014，**著录级**） |
| 保守分（Wilson 下界） | 不直接给学生；**排序与门槛的用途**在详情注明 | Miller 2009《How Not To Sort By Average Rating》（**原文已抓取**）；裁决 12/13 修订 [仓库一手] |
| 档位（掌握带） | 词 + 数字口径 + 升降规则，≤4 档（现行 3 档） | Khan Academy 官方四档定义 [**官方一手**]；4 级可辨（Al-Shanfari 2020 转引 Boukhelifa 2012，[**转引**]）；`decisions:2024` |
| 状态档（颜色=状态档 的「状态」） | 沿用现有状态词（刚开始记录/正在学习/掌握较稳/最近有波动/可以回顾） | §E 第 1 句 [仓库一手]；词表在 `LearningMasteryScreen.kt:384-390` |
| 稳定度（S）21 天 | 「稳定：按估计，隔 3 周不看也还大概率记得（口径：稳定度 ≥21 天 且 当前回忆概率 ≥0.9；久不复习会自然回落）」（**改定稿**；首稿「连续复习间隔已到 3 周以上」作废——S 是模型对记忆耐久度的**估计**，不是已发生的复习间隔） | Anki 官方 stability 定义（「the amount of time required for the probability of recall to decrease from 100% to 90%」）[**官方一手**，本 ask 研报逐字给出]；21 天 mature 口径 [**官方一手**]；等价式「S ≥ 21 天 ⇔ 下次计划间隔 ≥ 21 天（r\*=0.9 时）」见 `2026-09-30-mastery-criterion-evidence.md:50`、「距上次作答 ≤ 稳定度天数」见同文 `:61`；裁决 13 修订门槛 [仓库裁定]；**FSRS stability 的论文式形式化定义本次未核实** |
| 当前召回概率 ≥0.9 | 「回忆概率掉到九成才安排复习」 | SuperMemo.com FAQ 阈值语义 [**官方一手**]；Anki 期望保留率 90% [**官方一手**] |
| scrutable / 证据摘要下钻 | 「为什么这么判断」→ 证据条数与来源（独立答对次数/题目族/学习日/最近；讲题证据接受与被拒） | §E 第 3 句 [仓库一手]；Bull & Kay SMILI [**仅著录/摘要级**，全文未读] |
| 对齐展示（学生自评×系统估计并列 + 纠错入口） | 详情页并列 + 「我不同意/纠错」入口 | §E 第 2 句 [仓库一手]；UMUAI/Al-Shanfari [**摘要级**]——**依赖 D-Q9 入口，本轮无落点，机制层不做** |

**未核实项（不得当事实）**：Bull & Kay 2007/2016 全文未读；van der Bles 2019 RSOS 综述细节未核实；
Zwick/Zapata-Rivera/Hegarty 2014 结论未核实；FSRS stability 形式化定义未核实；**未找到中国高三学生同
主题实证研究**（均为外部调研自陈，见本 ask「外部·掌握度展示」来源条与元信息条）。
**读面用词纪律**：MASTERY_READ 输出是**模型可见**文本（可含数字），学生可见文本一律走上表左列↔右列
映射；「保守掌握度」这类词只许出现在工具输出与内部文档，学生面继续受禁词表（`LearningMasteryScreenInstrumentedTest.kt:116-134`）约束。

### 2.4 测试锚点

1. `core:data` `RoomTutorToolRunnerTest`（既有，`:714-830`）：夹具 `masteryRow`（`:1093-1111`）补 4 个新
   字段；新增断言：① 行含区间串（如 `0.12~0.99`）；② 聚焦查询下钻含「记忆：稳定度」；③ 无记忆卡时不
   输出记忆行；④ 既有 **6 条** mastery 用例（`:717` 最弱优先、`:742` 余数计数、`:755` 聚焦+聚合、
   `:793` 聚焦命中无证据、`:810` 不串科目、`:826` 无科目=空范围）保持绿。
2. **`core:database`**（不是 core:data）：`SubjectMasteryRow→SubjectMasteryRecord` 直拷用例（新字段不丢），
   落 `core/database/src/test/…/RoomStudyDatabaseMappingsTest.kt`（该文件已存在）——`SubjectMasteryRow`、
   `SubjectMasteryRow.toRecord()`、`RoomMasteryOverviewStore` 都是 core:database 的 **internal**，core:data
   的测试源集按模块边界引用不到（Kotlin internal 按模块）。core:data 侧只能测**公开**的
   `SubjectMasteryRecord`（`KnowledgePort.kt:276`，public：构造 + 经既有 `masteryRow` 夹具喂工具）。
3. `core:database` 仪器化 `MasteryOverviewInstrumentedTest`：`readSubjectMastery` 返回的 s/f 与记忆列值
   与种子一致（该套件已有 mastery 种子夹具 `:258-292`，`masteryState(...)` 构造 + `projectionCommit()`）。
4. 工具描述同步冒烟：`OpenAiNativeToolsProtocolTest` / `TutorToolPromptInjectionTest`（`core:data/src/test`
   既有，检查描述文案进 prompt/schema）保持绿——描述改了必须过这两处。
5. `LearningMasteryScreenInstrumentedTest` 禁词表（`:116-134`）保持绿（读面术语不漏到学生面）。

### 2.5 边界与非目标

- **视觉编码不做**（透明度/颜色/图表/学习档案视觉）：插眼 4，先问用户（`decisions:1694-1698`；`fix-plan:295`）。
- 「对齐展示」（自评并列 + 纠错入口）无落点：D-Q9 入口不存在（§E 第 2 句自带条件「若 D-Q9 提供入口」）。
- 不新增列、不加迁移、不改排序、不动截断预算常量（§0.3 零 bump）。
- 不做「每个数可下钻」的学生 UI 下钻页——机制层只保证数据可达（summary/工具），UI 下钻随插眼 4。

---

## 3. P9 rating 基址统一

**问题（审计 P9 行 `…kernel-scale-precision-audit.md:38`）**：`review_log.rating` 的 1-based 基址在三处
各写一遍，且 `AVOIDANCE` 常量的基址语义悬空。现状逐处（本次读码核实）：

- `ReviewLogSink.kt:108` `rating = rating.ordinal + 1,`（0-based → 1-based，唯一写路径）；
- `ReviewLogSink.kt:143` + `:270-272` `ratingForOrdinal`（读回 1-based → 枚举，越界夹取）；
- `ReviewLogSink.kt:96`、`:197` 用 `WRONG_ATTEMPT_RATING`（`:312` `= 1`，注释自称 1-based）；
- `ReviewLogSink.kt:258` `isCorrect = row.rating > 1,`（裸 1）；
- `ReviewLogSink.kt:176-177` 拿 1-based 列直接与 `AttentionSignal.AVOIDANCE_MAX_RATING`（`AttentionSignal.kt:58` `= 2`）比较；
- `FsrsScheduleMath.kt:114`、`:118`（`parameters[rating.ordinal]`，= w 索引 G−1）、`:131`、`:184`
  （`rating.ordinal - 2` = G−3，注释自陈基址 `:129-130`、`:182-183`）；
- `SchedulingEvaluation.kt:284` `sample.rating < FsrsRating.HARD`（依赖序的大小比较）；
- `KernelWave2Migration_54_55.kt:50` `WHEN $PREVIOUS_RATING_SUBQUERY = 1 THEN 3`（SQL 裸 1；受
  `KernelWave2SchemaContractTest.kt:53` 的 `= 1 THEN 3` 字符串断言钉住）；
- `StudyDatabaseRecords.kt:1246` `require(rating in 1..4)`；`LearningEntities.kt:1097` 注释「FSRS rating 1..4」。

### 3.1 决定

**决定 3-A（基址枚举放 core:model）**：新增
`core/model/src/main/kotlin/com/tingyun/smartmistakebook/core/model/StoredReviewRating.kt`：

```kotlin
/** review_log.rating 的唯一 1-based 基址（DB 契约锚点）。 */
enum class StoredReviewRating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);
    companion object {
        val MIN = AGAIN.value      // 1
        val MAX = EASY.value       // 4
    }
}
```
（**删去研究草案里的 `POOR_MAX`**：AVOIDANCE 的「差评」语义由决定 3-B 的 `FsrsRating.isPoorGrade` 承担，
`POOR_MAX` 在替换清单里没有任何消费点——按本仓「新增必指认」纪律不留死常量。）

[依据：`core/database/build.gradle.kts:31` 只依赖 `:core:model`（core:domain 同）——SQL/存储层要共用
一处基址，枚举只能落 core:model。]
[消灭的失败：`KernelWave2Migration_54_55.kt:50`、`ReviewLogSink.kt:258/:312`、`AttentionSignal.kt:58`
各自手写 1/2，改枚举序或改语义时静默漂移（审计 P9 行）。]

**决定 3-B（换算收成唯一面）**：`core:domain` 的 `FsrsRating`（`FsrsScheduleMath.kt:190-195`）只保留
算法 0-based 序，新增四个换算成员 + 两个伴生函数（**签名即实现口径**）：

```kotlin
enum class FsrsRating {
    AGAIN, HARD, GOOD, EASY;
    val storedRating: Int get() = ordinal + 1        // 全仓唯一 0→1 换算；替代 ReviewLogSink.kt:108
    val parameterIndex: Int get() = ordinal          // w0..w3 索引（= rating−1，py-fsrs G−1）；命名 FsrsScheduleMath.kt:114/:118
    val gradeMinusThree: Int get() = ordinal - 2     // py-fsrs (G−3)；命名 FsrsScheduleMath.kt:131/:184
    val isPoorGrade: Boolean get() = this == AGAIN || this == HARD  // 替代 AVOIDANCE_MAX_RATING 语义
    companion object {
        /** 1-based 落库值 → 枚举；沿用 ReviewLogSink.kt:270-272 的 coerce 夹取语义。 */
        fun fromStored(value: Int): FsrsRating = entries[(value - 1).coerceIn(0, entries.size - 1)]
        /** 落库值是否 AGAIN；替代 ReviewLogSink.kt:96/:197/:258 的 `== WRONG_ATTEMPT_RATING` / `> 1`。 */
        fun storedIsAgain(value: Int): Boolean = fromStored(value) == AGAIN
    }
}
```

**决定 3-C（签名收紧，基址进类型）**：
- `AttentionSignal.isAvoidanceSignal(switchCount: Int, rating: FsrsRating): Boolean`（判
  `switchCount >= AVOIDANCE_SWITCH_THRESHOLD && rating.isPoorGrade`）；**删** `AVOIDANCE_MAX_RATING`
  （`AttentionSignal.kt:58`）。依据：现签名是裸 `Int`，1-based、=HARD 的基址只在测试
  `AttentionSignalTest.kt:34-37`（传 1/2/3）里隐含，签名无基址声明。
- DB 边界：`StudyDatabaseRecords.kt:1246` 的 `require(rating in 1..4)` → `require(rating in StoredReviewRating.MIN..StoredReviewRating.MAX)`。
- SQL 单源：`core/database` 内加 `private val AGAIN_STORED = StoredReviewRating.AGAIN.value`（**必须是 `val`
  不能是 `const val`**），插值进 `KernelWave2Migration_54_55.kt:50`（`= $AGAIN_STORED THEN 3`）——
  `KernelWave2SchemaContractTest.kt:53` 的 `"= 1 THEN 3"` 断言原样通过（值不变）。
  [本次本地探针（kotlin-compiler-embeddable 2.3.21，`K2JVMCompiler @args` 编译临时文件）实测：
  `private const val AGAIN_STORED = StoredReviewRating.AGAIN.value` → **编译错误**
  `error: const 'val' initializer must be a constant value.`；改 `private val` 后编译并运行通过，
  输出 `WHEN prev.\`rating\` = 1 THEN 3`、`contains '= 1 THEN 3' -> true`、`MIN..MAX -> 1..4`。]

**决定 3-D（配对钉死）**：一条契约测试断言
`FsrsRating.entries.map { it.storedRating } == StoredReviewRating.entries.map { it.value }`。
[消灭的失败：`FsrsRating` 声明序与 `StoredReviewRating` 值脱钩——改名/重排任一侧即红。]
**不采用的备选**：把 `FsrsRating` 整体搬进 core:model 让 SQL 层直接用——代价是 core:domain 内外十几处
import 变动，本项不动投影，无对价。

### 3.2 替换清单（逐处；右列为改后）

| # | file:line | 现状 | 改后 |
|---|---|---|---|
| 1 | `ReviewLogSink.kt:108` | `rating = rating.ordinal + 1,` | `rating = rating.storedRating,` |
| 2 | `ReviewLogSink.kt:143` + `:270-272` | 私有 `ratingForOrdinal(...)`（函数体 `entries[(rating-1).coerceIn(...)]`） | 删该函数；`rating = FsrsRating.fromStored(row.rating),` |
| 3 | `ReviewLogSink.kt:96` | `previous.rating == WRONG_ATTEMPT_RATING` | `FsrsRating.storedIsAgain(previous.rating)` |
| 4 | `ReviewLogSink.kt:197` | `it.rating == WRONG_ATTEMPT_RATING` | `FsrsRating.storedIsAgain(it.rating)` |
| 5 | `ReviewLogSink.kt:258` | `isCorrect = row.rating > 1,` | `isCorrect = !FsrsRating.storedIsAgain(row.rating),` |
| 6 | `ReviewLogSink.kt:312` | `private const val WRONG_ATTEMPT_RATING = 1` | 删（无剩余调用点；#3/#4/#5 已替换） |
| 7 | `ReviewLogSink.kt:176-177` | `... && it.rating <= AttentionSignal.AVOIDANCE_MAX_RATING` | `AttentionSignal.isAvoidanceSignal(it.interruptionCount, FsrsRating.fromStored(it.rating))`（消灭内联副本，让共享函数成为唯一判据） |
| 8 | `AttentionSignal.kt:54-58` | 形参 `schedulingRating: Int`；`AVOIDANCE_MAX_RATING = 2` | 形参 `rating: FsrsRating`；判 `rating.isPoorGrade`；删常量 |
| 9 | `AttentionSignalTest.kt:34-37` | 传 `1 / 2 / 3` | 传 `FsrsRating.AGAIN / HARD / GOOD`（并保留「AGAIN|HARD 命中、GOOD 不命中」的四格） |
| 10 | `FsrsScheduleMath.kt:114` | `parameters[rating.ordinal]` | `parameters[rating.parameterIndex]` |
| 11 | `FsrsScheduleMath.kt:118` | `parameters[5] * (rating.ordinal)` | `parameters[5] * rating.parameterIndex` |
| 12 | `FsrsScheduleMath.kt:131` | `(rating.ordinal - 2 + parameters[18])` | `(rating.gradeMinusThree + parameters[18])` |
| 13 | `FsrsScheduleMath.kt:184` | `-(parameters[6] * (rating.ordinal - 2))` | `-(parameters[6] * rating.gradeMinusThree)` |
| 14 | `SchedulingEvaluation.kt:284` | `sample.rating < FsrsRating.HARD` | `sample.rating == FsrsRating.AGAIN`（**不是** `isPoorGrade`——见 §3.3 更正） |
| 15 | `StudyDatabaseRecords.kt:1246` | `require(rating in 1..4)` | `require(rating in StoredReviewRating.MIN..StoredReviewRating.MAX)` |
| 16 | `KernelWave2Migration_54_55.kt:50` | `WHEN $PREVIOUS_RATING_SUBQUERY = 1 THEN 3` | `WHEN $PREVIOUS_RATING_SUBQUERY = $AGAIN_STORED THEN 3`（新增 `private val AGAIN_STORED`——**非 `const`**，见 §3.1；值仍 1） |

### 3.3 不改名单与一处更正

**确认无需改（已有类型化枚举、不含基址算术；逐处复核过）**：`SchedulingEvaluation.kt:37`
（`rating != FsrsRating.AGAIN`）、`:527`（`== FsrsRating.HARD`）；`LearningProjector.kt:963-975`；
`MemoryUpdateModel.kt:114`；`FsrsScheduleMath.kt:133`/`:147`/`:148`（`== AGAIN/HARD/EASY`）；
`RoomStudyDatabaseMappings.kt:481`/`:502`（`rating` 直拷，无换算）；`KernelWave2Migration_54_55.kt`
的 state 值 `0..3`（`ReviewSample.STATE_*`，另一个枚举，不受影响）。

**更正研究草案一处**：外部研究给出的替换表把 `SchedulingEvaluation.kt:284` 并入 `isPoorGrade`
（AGAIN|HARD）——**语义错**：该行是「源校准的**正向**报读」过滤（`:273-289`，`rating < HARD` 只跳过
AGAIN，HARD 属正向档）；换成 `isPoorGrade` 会把 HARD 从正向样本里剔掉，改变 `calibrateSources` 的输出。
正确替换是 `== FsrsRating.AGAIN`（等价、且摆脱序比较）。

### 3.4 测试锚点与版本影响

1. `core:domain`（能同时看见两侧）：**配对契约测试**——`FsrsRating.entries.map { it.storedRating } ==
   StoredReviewRating.entries.map { it.value }`；外加 `fromStored` 夹取边界（1/4 过、0/5/99 夹到端点）。
2. `core:domain`：`FsrsScheduleMathTest` 既有手算对照（`:127`/`:138` 已按 `rating−3` 口径写死）保持绿——
   即 `parameterIndex`/`gradeMinusThree` 的命名不改变数值行为。
3. `core:data`：`ReviewLogSink` 往返用例——枚举 → `storedRating` → `fromStored` 得原枚举；`storedIsAgain`
   对 1 为真、2/3/4 为假；avoidance 过滤新用 `isAvoidanceSignal` 后行为与旧式一致（AGAIN/HARD 命中、
   GOOD/EASY 不命中——现状无此用例，新增）。
4. `core:database`：`KernelWave2SchemaContractTest` 原样绿（SQL 值不变）；`StudyDatabaseRecords`
   `require` 的 1/4 过、0/5 拒。
5. **版本影响：零 bump**——落库值、SQL 值、投影输出逐位不变（§0.3）。若实施中发现任一处值变化，
   立即停手回读本节并走 bump 流程（roadmap §0 第 1 条）。

---

## 4. 仪器化测试旧复合串清单与应改值

**检法（本次执行）**：`grep -rn "learning-core" core/*/src/androidTest feature/*/src/androidTest app/src/androidTest`
（不限扩展名、滤 `/build/`）——全仓 androidTest 命中**恰好 8 行 / 6 个文件**，全部在
`core/database/src/androidTest`；core:data、feature、app 的 androidTest 无 `learning-core` 字面量。

**为何现在不判红（读码结论，未跑套件）**：提交守卫只做「提交内自洽」比较——
`DatabaseContract.kt:756-772` 校验 `commit.snapshot.checkpoint.projectorVersion == commit.expectedProjectorVersion`
（经 `ProjectionTransactionDao.kt:639` 调用），两侧都由夹具提供；且快照读取签名
`LearningProjectionPort.kt:132-134` 不带版本参数。生产侧才绑真常量（`StudyProjectionDrainer.kt:136`、`:190`
用 `LearningProjector.VERSION`，其值 = `LearningCoreVersions.PROJECTION_COMPOSITE`，`LearningProjector.kt:1242`）。

### 4.1 全量清单与逐项裁定

| # | 位置 | 现串 | 性质 | **应改值（裁定）** |
|---|---|---|---|---|
| 1 | `FullMigrationMatrixInstrumentedTest.kt:229`（`LEGACY_PROJECTOR_VERSION`） | `"learning-core-v6(projector-v6,evidence-v4)"` | **有意遗留**：`:104` 断言归档表保留旧版本（迁移矩阵/归档保真被测点） | **不改**（值即用例语义） |
| 2 | `MasterySchedulingMigrationInstrumentedTest.kt:228` | SQL 种子 `1, 'learning-core-v4', 100,` | **有意遗留**：旧库迁移种子（被测点就是旧库升级） | **不改** |
| 3 | `MasterySchedulingMigrationInstrumentedTest.kt:249` | SQL 种子 `'learning-core-v4', 1` | 同上 | **不改** |
| 4 | `LibraryLeastMasteredSortInstrumentedTest.kt:265`（`PROJECTOR_VERSION`） | `"learning-core-v7"` | **陈旧「当前版」**：作为快照标签写入（`:224`/`:235`/`:247`/`:253`），落后现产 v9；无测试钉住 | **改绑** `LearningProjector.VERSION`（见 4.2 的两步改法） |
| 5 | `MasteryOverviewInstrumentedTest.kt:390`（`PROJECTOR_VERSION`） | `"learning-core-v6"` | 同上（`:238`/`:248`/`:285`/`:363` 同值自洽） | **改绑** `LearningProjector.VERSION` |
| 6 | `MistakeMemoryProjectionInstrumentedTest.kt:165`（`PROJECTOR_VERSION`） | `"learning-core-v6"` | 同上（`:85`/`:96`/`:102`） | **改绑** `LearningProjector.VERSION` |
| 7 | `StudyDatabaseInstrumentedTest.kt:2013`（`PROJECTOR_VERSION`） | `"learning-core-v2(projector-v2,evidence-v2,curve-v2,skip-v2,attribution-v1)"` | 同上（`:1696`/`:1754`/`:1935` 版本位自洽） | **改绑** `LearningProjector.VERSION` |
| 8 | `StudyDatabaseInstrumentedTest.kt:2011`（`PROJECTION`，**投影名位**） | `"learning-core-v2"` | 陈旧命名（旧时代「投影名=复合串」遗留）；与生产投影名不一致 | **改**为生产名 `"study-experience-v1"`（生产硬编码先例：`MasteryOverviewDao.kt:57`；该测试全文无 `library_catalog`/`study-experience` 断言——grep 实测，改动自洽安全；改后跑该套件确认） |

### 4.2 「改绑」的两步改法（消灭的失败写清）

1. `core/database/build.gradle.kts` 的 `androidTestImplementation` 组（`:40-42`）加一行
   `androidTestImplementation(project(":core:domain"))`——只进测试类路径，**不改生产依赖**（生产侧仍
   只有 `:core:model`，`:31`）。依据：core:database 的 androidTest 目前**零处** import `core.domain`
   （`grep -rl "core.domain" core/database/src/androidTest` 实测 0 命中），不加这行类路径取不到
   `LearningProjector`；core:domain 是纯 JVM 模块（`core/domain/build.gradle.kts:3-6`），Android
   androidTest 依赖它没有配置冲突。
2. 表 4.1 的 #4–#7 四处常量改为 `= LearningProjector.VERSION`。先例：`ChatEvidenceLedgerIntegrationTest.kt:122`
   （androidTest 里唯一绑真实常量的用法，本次已在树中核实存在）。

**消灭的失败**：这三类夹具的「当前版」标签与真实当前版脱钩（v6/v7 vs 现产 v9），且**没有任何测试钉住**
——它们只在将来有人把版本守门接到真实版本时**集中爆红**（研报「陈旧『当前版』占位」判读）；改绑后它们
随 `PROJECTION_COMPOSITE` 自动同源，未来 bump 不再积累暗债。
**不采用的备选**：把三处字面量更新成 `"learning-core-v9(projector-v9,evidence-v4,curve-v3,skip-v4,attribution-v2,ledger-v2)"`——
一次性同步、下次 bump 复发，正是要消灭的模式。

### 4.3 验证与范围外登记

**验证（实施时跑，本次未跑）**：
- `:core:database:connectedDebugAndroidTest` 的定向套件：`LibraryLeastMasteredSortInstrumentedTest`、
  `MasteryOverviewInstrumentedTest`、`MistakeMemoryProjectionInstrumentedTest`、`StudyDatabaseInstrumentedTest`
  （改绑/改名后自洽应全绿；#8 改投影名后单独复核）；
- 有意遗留的**三行（#1–#3，分布在两个套件）**必须**仍绿**：`FullMigrationMatrixInstrumentedTest`（#1）、
  `MasterySchedulingMigrationInstrumentedTest`（#2/#3）。

**范围外登记（非仪器化，同一次 grep 命中，本规格不动，供批次收尾时一并处理）**：
- `core/database/src/test/…/KernelWave0SchemaContractTest.kt:172` `CURRENT_PROJECTOR_VERSION = "learning-core-v8(projector-v8,evidence-v4)"`
  ——名字含 CURRENT 但落后 v9；同文件 `:33` 用 v6 字符（拒绝用例，属有意）。建议同 4.2 思路处理
  （JVM 侧需 `testImplementation(project(":core:domain"))` 或更新字面量）；
- `core/data/src/test/…/ProjectionArchiveDrainerTest.kt:30`、`core/domain/src/test/…/ProjectionVersionGuardTest.kt:36`/`:63`
  ——`learning-core-v6(...)` 作「前一版本」，属有意；`core/domain/src/test/…/ReTeachInjectionTest.kt:108`
  `"projector-v7"` 同理。
- 注：`wave3-plan.md:23` 曾预告「`KernelWave0SchemaContractTest:172` 硬编码复合串（v8）必红、须同步」——
  本次读码与批次 1 提交记录（全量 JVM 绿）核实：它**当前不红**（守门只做提交内自洽），已按上文登记，
  作为实施者不必被动等红。

---

## 附：本文的完成判定（收尾时逐条机械判）

1. `core:ui` 常量在且三处标签引用它（`grep -rn "MASTERY_STRONG_THRESHOLD\|MASTERY_FAIR_THRESHOLD"` 命中 ≥4；
   `grep -rn ">= 0\.7\|>= 0\.4" core/ui/src app/src/main feature/*/src/main` 在档位语境 0 命中）。
2. 三展示面文案含区间（仪器化断言绿）；`MasteryBandsTest`/`MasteryBandConsistencyTest` 绿。
3. MASTERY_READ 行含区间、聚焦下钻含记忆行；`TutorToolDescriptions.kt` 列名同步；`RoomTutorToolRunnerTest` 绿。
4. P9：`grep -rn "ordinal + 1\|ordinal - 2\|WRONG_ATTEMPT_RATING\|AVOIDANCE_MAX_RATING\|rating in 1\.\.4"` 在
   四项清单文件内 0 剩余；配对契约测试绿；零 bump 复核（落库值未变）。
5. §4：#4–#7 绑 `LearningProjector.VERSION`、#8 用生产投影名；定向仪器化套件与两个套件里的有意遗留三行全绿。
6. 未跑项如实报：仪器化套件在无设备/未连接时不跑，报告写 `UNVERIFIED`，不得当绿。

---

## 复核意见处置（独立复核回执，2026-10-01）

复核共 17 条可处置项：**15 条属实、已直接改本文**；1 组「核实为真」维持原样；1 条「未跑」声明与本规格一致。
下面逐条给出处置与复核依据（本方对每条独立复算/实读，不照抄复核方结论）。

| # | 复核意见 | 处置 | 本方的复核证据（本次执行） |
|---|---|---|---|
| 1 | SQL 单源 `const val` 编译不过 | **属实 → 已改**：决定 3-C 与 §3.2 #16 改 `private val AGAIN_STORED` | 本方自跑探针（kotlin-compiler-embeddable 2.3.21，`K2JVMCompiler @args`）：`const val` 版 → `error: const 'val' initializer must be a constant value.`（探针 :9:34）；`private val` 版编译并运行 → `WHEN prev.\`rating\` = 1 THEN 3` / `contains '= 1 THEN 3' -> true` / `MIN..MAX -> 1..4` |
| 2 | 搬回 R-03 用例钉的是 0.7，必红 | **属实 → 已改**：§1.4 锚 2 增「搬迁时该条改钉 0.85」 | 实读 `.worktrees/kernel-readiness/app/src/test/…/MasteryBandConsistencyTest.kt:83-87`（`assertEquals(0.7, MASTERY_STRONG_THRESHOLD, 0.0)`） |
| 3 | 映射用例放错模块（internal 按模块） | **属实 → 已改**：§2.4 锚 2 移到 `core:database/src/test/…/RoomStudyDatabaseMappingsTest.kt` | 实读 `MasteryOverviewDao.kt:172`（`internal data class SubjectMasteryRow`）、`RoomStudyDatabaseMappings.kt:463`、`RoomMasteryOverviewStore.kt:18`；`ls core/database/src/test/…/` 确认该测试文件存在 |
| 4 | 「稳定度」学生文案无依据且与 E 语义不符 | **属实 → 已改**：§2.3 该行改「稳定：按估计，隔 3 周以上不复习也还大概率记得」，并注首稿作废 | S 的语义按 Anki 官方定义（本 ask 研报逐字）与 `mastery-criterion-evidence.md:61`（「距上次作答 ≤ 稳定度天数」）；首稿「连续复习间隔已到 3 周以上」确实把 S 说成已发生间隔 |
| 5 | §0.1 与 §4.1 计数互换 | **属实 → 已改**：§0.1 ④ 改「3 不改 / 4 改绑 / 1 改名」 | 按 §4.1 表逐行数：#1–#3 不改、#4–#7 改绑、#8 改名 |
| 6 | 构造点计数「8 处 / 6 处」不实 | **属实 → 已改**：§1.1 1-C 改 23 处构造（生产 2 / 测试 21） | 重跑 `grep -rn "StudyKnowledgeSummary(" core app feature`（滤 build/worktrees）：24 命中 = 1 处 data class 声明 + 23 处构造；按文件计 19（tutor 测试）+1+1（另两处测试）+2（mappers） |
| 7 | 禁词表行号越界（文件 136 行） | **属实 → 已改**：四处 `:125-142` → `:116-134`（§1.3/§1.4 锚 4/§2.3/§2.4 锚 5） | `wc -l` = 136；`grep -n FORBIDDEN_STUDENT_TERMS` → 定义 `:116`、使用 `:75` |
| 8 | `wave3-plan.md:12` 引错 | **属实 → 已改**：→ `:23` | `grep -n "必红"` 命中 `:23` |
| 9 | 数据库 build.gradle 区块引错 | **属实 → 已改**：→ `:40-42` | `grep -n androidTestImplementation core/database/build.gradle.kts` → `:40/:41/:42` |
| 10 | `execution-steps.md:110 §5.1` 引错 | **属实 → 已改**：→ `:115`（§5 标题 `:113`） | `grep -n "共享工作树\|## 5"` |
| 11 | R-03「原话」查无出处 | **属实 → 已改**：改引提交 `9522545d` 提案说明 + 未合并分支文件头 | `grep -rn "同一张卡就会"`/`"学生对它有多牢"` 在 audit 内 0 命中；两句实现在 `.worktrees/kernel-readiness/core/ui/…/MasteryBands.kt:8-10`、`:16-17`；审计原文按 `:1453`/`:1457` 改写 |
| 12 | 「勿动」在飞清单漏项 | **属实 → 已改**：§0.2 列全六项 | `git status --porcelain core/domain core/model` 实测六项 |
| 13 | `POOR_MAX` 是无消费的死常量 | **属实 → 已改**：删；语义由 `FsrsRating.isPoorGrade` 承担 | 全规格 grep `POOR_MAX` 仅声明处原一行；§3.2 无消费点 |
| 14 | 漏算第 6 条 mastery 用例 | **属实 → 已改**：§2.4 锚 1 ④ 改 6 条并列行号 | `grep -n "@Test"` + 逐条实读：`:717/:742/:755/:793/:810/:826` |
| 15 | 零散错行五处 | **属实 → 已改**：`roadmap:19-22→17-22`、`:548-640→:548-631`、`:5-11→:3-10`、`fix-plan:294→:295`、`wave3-plan.md:70→:71`（两处） | 逐处实读：roadmap `:17` 起第 1 条、`RoomTutorToolRunner.kt:631` 收尾、`TutorToolDescriptions.kt` 头注 `:3-10`、`algorithm-fix-plan.md:295`、`wave3-plan.md:71` |
| 16 | §4 整节、§4.2 前提、#8 安全、P9 现状、E 口径、P8 现状、「既有测试不被打红」核实为真 | 维持原样（无改动） | 本方与复核一致：本次读码已在原文逐条标 `file:line`；复核另跑了 §4 点名 grep（8 行/6 文件）与本方一致 |
| 17 | 「未跑」声明 | 一致 | 本规格与本次复核均未运行 Gradle/仪器化套件；所有「应全绿」均属 `UNVERIFIED`，待实施时按 §4.3 与各节测试锚点执行 |

**本次复核未能本地复现的部分**（如实声明）：复核方对 `const val` 的探针本方已独立复现（见 #1）；
其余全部为读码/grep/git/文件实读。**外部文献**（Bull & Kay、van der Bles、Anki/SuperMemo/Khan 等）无法在
仓库内核实真伪，本文继续按复核方与研报的来源等级标注，不当事实使用。
