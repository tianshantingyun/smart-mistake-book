# 内核修复路线图（实施级执行手册 · 2026-09-28）

> **总入口**：`docs/research/2026-09-28-kernel-execution-steps.md`（先读它——步骤顺序、每步的
> 读档与完成判定都在那里；本路线图是其 Step 4–9 的实施细节）。

- 定位：**给未来实施模型的执行手册**。每条目七段（证据→目标→改法→迁移→测试→依赖→完成判据），
  公式全量给出；标注"待裁"的数值**不得自行取值**，必须过用户裁决（已裁值见台账「3A 前裁决门 · 收口」）。
- 上游：`2026-09-28-algorithm-fix-plan.md`（KF-01~34）、`2026-09-28-kernel-scale-precision-audit.md`
  （P/F/Q/S 编号）、`2026-09-25-mastery-mechanism-review.md`（阶段 0 研究）。
- 波次依赖：Wave 0→1→2→3 严格串行；Wave 4 与 2/3 并行；Wave 5 依赖 3C 前提（监测先于自动化）；
  Wave X 不排期（待真实数据）。

---

## 0. 执行守则（开工前必读，违者返工）

1. **动投影公式**（任何改变投影输出的修改）→ 必 bump `LearningCoreVersions.PROJECTION_COMPOSITE`
   → 版本不匹配自动触发全量重放；**动之前**先落旧投影快照（Wave 0 的 `projection_archive`）。
2. **加模型输入字段** → strip 空载体 + bump 该任务的 schema 指纹 + 4 用例（旧行读取/新行写入/
   空载体/升级路径，`bf8be888` 教训，见 `docs/` 指纹纪律）。
3. **动 Room schema** → bump `STUDY_DATABASE_VERSION` + 非破坏迁移 + 导出 schema JSON +
   `git diff --exit-code -- core/database/schemas`。
4. SQL 一律**参数绑定**；测试先红后绿；提交用**显式文件列表**；每波结束跑定向测试 + 全仓编译；
   每波退出门逐条机械判定，不过不入下一波。
5. 已裁值一律按台账「3A 前裁决门 · 收口」照办；**之后新出现的"待裁"值不得自行取值**，须先过用户。
6. 所有修复完成后对照 `docs/current/` 的文档作废清单（spec §5），同步文档世界。

---

## Wave 0 · 地基（3A 首批）：先让算法改动"可逆、可校验"

### W0-1（Q2）投影版本回退/防降级

- **证据**：`StudyProjectionDrainer.kt:79-94` 版本不匹配触发重放，但 `replay` 不调
  `requireCompatibleSnapshot`（该守卫只在增量 `project()`，`LearningProjector.kt:91,642-655`）；
  `commitProjection`（`ProjectionTransactionDao.kt:635`）无版本比较，旧二进制会静默覆盖新投影。
- **目标**：① 旧二进制重放被拒；② 每次 bump 前旧投影可恢复。
- **改法**：
  1. `LearningProjector.replay` 入口加 `requireCompatibleSnapshot(previous)` 同款调用。
  2. `commitProjection` 增加断言：`commit.snapshot.projectorVersion == 当前二进制 VERSION`，
     不一致抛异常（拒绝降级写）。
  3. 新表 `projection_archive`：`(projection_name, learner_id, archived_at, snapshot_json,
     projector_version, schema_ddl)`；`commitFullReplay` 执行前把现 snapshot 序列化落一条。
  4. 回退流程（文档化）：恢复 = 手动工具读 archive 行 → 重建 snapshot → 写回 + 重新 drain。
- **迁移**：新表 + 非破坏迁移；无旧数据回填。
- **测试**：① 旧版本 snapshot 提交被拒；② replay 前 archive 有旧快照；③ 版本一致时行为不变。
- **依赖**：无。
- **完成判据**：降级写被拒、archive 落库、两用例绿。

### W0-2（Q4）计划指纹读时校验

- **证据**：`ReviewDao.kt:505` 只 CAS `projectionCheckpoint`；`inputFingerprint`/`planFingerprint`/
  `plannerVersion`（`StudyReviewPlannerService.kt:339-342`）落库后从不比对。
- **目标**：planner 版本/指纹变化 → 旧计划失效并重排。
- **改法**：读回 plan 处增加：`stored.plannerVersion != 当前 LearningCoreVersions.REVIEW_PLANNER
  → 标记 STALE、不续跑、触发重排`；指纹列合并为一列（两列恒同值，删一）。
- **测试**：改 plannerVersion 常量 → 旧计划不续跑；checkpoint 匹配且版本匹配 → 续跑。
- **完成判据**：版本不匹配重排用例绿。

### W0-3（Q1）版本清单

- **改法**：新建 `docs/research/algorithm-version-ledger.md`，每次 bump 记：版本串、变更公式、
  数据来源、测试证据、archive 是否已落。
- **完成判据**：文件存在，Wave 3 第一次 bump 时有记录。

### W0-4（Q5）常数注册表

- **证据**：82 文件散落 `const val`；权重表两份分叉（`ReviewPlanner.kt:581-606` vs
  `ReviewPlannerV2.kt:872-912`）；`DAY_MILLIS` ≥4 处。
- **改法**：`core/domain/.../AlgorithmConstants.kt` 单源；迁移顺序：先 V1/V2 权重表合并
  （M6 删 V1 时自然完成）→ 再 DAY_MILLIS → 再按波次逐个并入。
- **测试**：常量引用编译期唯一（删旧定义即编译错，天然红）。
- **完成判据**：权重表单源；DAY_MILLIS 单源。

**退出门（Wave 0）**：W0-1/2 用例全绿 + W0-3 文件在 + W0-4 权重表单源。

---

## Wave 1 · 数据质量与正确性（3A 二批）：先让采集到的数是真数

### W1-1（P3）duration 毫秒化

- **证据**：`LearningState.kt:308` `durationSeconds: Int`；采集端 `ReviewSessionScreen.kt:925-927`
  `(now-started)/1000L` 截断；`ReviewLogSink.kt:93` 落 `durationSeconds*1000L`。
- **改法**：全链改 `durationMillis: Long`；采集端不除 1000；`review_log.duration_ms` 直接落。
- **迁移**：`attempt_event.duration_seconds` 与 `review_log.duration_ms` 列语义升级——旧行保持
  秒级并标记（或列改名 + 迁移 `*1000`），二选一：**推荐列改名**（`duration_ms`）+ 迁移乘 1000，
  杜绝口径混淆。
- **测试**：1500ms 作答落 1500；旧行迁移后 ×1000 可读。
- **完成判据**：采集链路无截断 + 迁移绿。

### W1-2（P4）展示时长采集

- **证据**：全库无 `presented_at_epoch_millis`/displayDuration（KF-15 前提不成立）。
- **改法**：`assessment_presentation` 加列 `presented_at_epoch_millis`；呈现侧挂载时写；
  提交时 RTE = duration / (submittedAt − presentedAt)。
- **测试**：展示→作答链路 RTE 计算正确；展示缺失时 RTE 保守默认（不惩罚）。
- **完成判据**：RTE 分母可算（为 KF-15 解锁）。

### W1-3（P6/F3 + KF-02）persistedAssistance 输入链

- **证据**：`StudySubmissionPreparer.kt:53-59` 从不传 `persistedAssistance`；`:96` 硬编码
  `revealedBeforeAnswer=false`。
- **数据流（逐文件）**：提交前查询该 presentation 的 `answer_reveal_outcome` →
  `RoomBackedStudyExperienceRepository.submitChoice/submitReviewChoice` 组装
  `PersistedAssessmentAssistance` → `prepareChoiceSubmission` 入参 → `AssessmentSubmissionContext`
  → `MasteryEvidencePolicy` 分支可达。
- **改法**：按上述数据流逐环补传；`revealedBeforeAnswer` 取真值落 attempt_event。
- **测试**：看答案后答对 → EXCLUDED w=0；答错 → INCORRECT_AFTER_REVEAL w=0.6；未看 → 不变；
  指纹 4 用例。
- **完成判据**：KF-02 全绿。

### W1-4（KF-03）看答案语义统一

- **两候选实现差异表**（**已裁：B**，台账裁决 1）：
  - A：`projectAnswerReveal` 输出 outcome=ANSWER_REVEALED 且 `isRetrievalFailure=false`；
    review_log 独立 `source_kind=REVEAL` 且拟合排除。
  - B：保持 AGAIN/lapse++，同时 policy 侧改为"计一次失败"。
- **共同部分**：review_log 增独立 `REVEAL` 值；`fittableReviewSamples` 排除之（无论 A/B）。
- **完成判据**：拟合集不含 reveal 行 + 语义单一口径。

### W1-5（KF-06）契约回写

- **改法（A 案）**：`ReviewLogSink.record` 调用点移到投影提交后、按 post-causality
  (reason, weight) 落 rating；或提交后补偿写入。B 案见 fix-plan。
- **完成判据**：看答案后答对的 review_log 不再是 GOOD。

### W1-6（P1 + P10）口径修正

- **P1**：统一日序 = "账本存原始时间戳+偏移，日序一律重放期派生"；`ReviewLogSink` 的写盘快照
  改存原始时间戳（可复算）。
- **P10**：`toFixedDays` 改银行家舍入：`val floor=floor(x); if (x-floor==0.5) floor+((floor.toInt()%2==0)?0:1) else round(x)`；
  补 tie 对照测试（x.5 且整数部分奇/偶各一例）。
- **完成判据**：三源日序同一时间戳同结果；tie 用例与 py-fsrs 一致。

**退出门（Wave 1）**：W1-3/4/5 全绿 + W1-1/2 迁移绿 + W1-6 口径用例绿。

---

## Wave 2 · 拟合可信（3A 三批）：让训练真正发生且可信

### W2-1（KF-01）w20 全调用点接线

- **全部调用点清单**：`FsrsScheduleMath.retention(:64)` / `factor(:71)` / `intervalDays(:78)`；
  `MemoryUpdateModel.kt:86,93`（两处 retention）与 `:101`（intervalDays）；
  `SchedulingReplay.predict`（`SchedulingEvaluation.kt:148`）。
- **改法**：三函数去掉默认参数，全部 6 个调用点显式传 `-parameters[20]`。
- **退出门实现**（合成数据恢复测试）：用默认参数生成 2000 条 review_log 序列（卡片×评级×间隔），
  扰动 w20 拟合 → 断言 loss 随 w20 变化且最优值回到真值 ±ε。
- **完成判据**：合成数据恢复测试绿。

### W2-2（KF-04 + KF-05）采纳门 + 400 硬门

- **改法**：`StudySchedulingCalibration`：<400 可预测样本 → 不拟合、保持现参数；
  ≥400 → 拟合 → 与存量参数同数据重放比 log loss，不劣才写。
- **测试**：399 不拟合；400 拟合；存量更优不覆盖；空存量直接写。
- **完成判据**：三用例绿 + 启动路径不再静默覆盖。

### W2-3（KF-12）w3 剔除

- **改法**：`fittedIndices` 剔除 3（与 16 并列）。
- **完成判据**：拟合后 w3 恒默认。

### W2-4（KF-23/24/25）state 列 / 每日首条 / day_start

- **state 列 DDL**：`review_log.state INTEGER NOT NULL DEFAULT 0`；取值规则：该卡首条=0(New)、
  同日重复=1(Learning)、跨日非 AGAIN 后=2(Review)、AGAIN 后=3(Relearning)；`SchedulingReplay`
  按 state 选初始稳定度（替换 elapsed<1 启发式）。
- **每日首条**：`readReviewLogSamples` 按 (card, study_day) 去重（拟合侧纯过滤）。
- **day_start**：常量 `DAY_START_HOUR=4`；`StudyWriteContext` 日界加偏移；历史断层处理：
  推荐 bump `PROJECTION_COMPOSITE` + 重放按新口径重算（账本存 offset 可复算）。
- **完成判据**：三件各自用例绿 + 迁移矩阵绿。

### W2-5（fsrs-optimizer 对齐三件，§B 附录）

- ① loss 只在序列末态：`predict` 改为每卡序列输出一对（末态 R, 实际）；
  ② L2 罚项 `γ·Σ(w−w_init)²/σ²`（γ **已裁**：对齐官方默认，裁决 14）；③ 无早停、best-by-eval-loss。
- **完成判据**：合成数据恢复测试在官方口径下仍绿（口径切换后重跑 W2-1 的退出门）。

**退出门（Wave 2）**：W2-1~5 全绿 + 个性化参数真实生效（模拟断言）。

---

## Wave 3 · 表示重做（3A 四批）：换框架

### W3-1（KF-09）β-二项 s/f 双计数

- **公式**：p = (s+α)/(s+f+α+β)；正向 s+=w、负向 f+=w；先验默认 Jeffreys (0.5,0.5)（**已裁**，裁决 12）。
- **DDL**：`learner_knowledge_mastery_state` 加 `success_weight REAL`、`failure_weight REAL`；
  非破坏迁移；**回填 = 全量重放**（账本→投影，无手工回填）。
- **测试**：8 次全对手算轨迹对照；1 对 1 错分叉；重放一致；迁移后旧行可读。
- **完成判据**：手算轨迹 + 重放一致绿。

### W3-2（KF-10）Wilson 下界

- **公式**（写全）：
  ```
  z = 1.96
  n = s + f
  p̂ = (s + α) / (n + α + β)
  下界 = ( p̂ + z²/(2n) − z·√( p̂(1−p̂)/n + z²/(4n²) ) ) / (1 + z²/n)   # n>0
  下界 = 先验均值   # n=0
  ```
- **MASTERED 判据**：p̂ ≥ θ ∧ (上界−下界) ≤ ε（θ/ε **已裁**：0.85 保持 + 0.15 起步，裁决 13）。
- **完成判据**：小 n 边界手算对照（n=1/2/8）。

### W3-3（KF-11 + KF-08 + P5/P8/P9/P11）

- KF-11：删 `LegacyExponentialMemoryUpdateModel` + `useFsrsScheduling` 分支与设置项（逐文件清单
  见 fix-plan）。
- KF-08：`scoreCandidate` 开头前置硬过滤（prereqGap>0 → return null）。
- P5：首答不算 cross-day；曝光计数语义统一。
- P8：展示统一用 conservative+区间；阈值体系并入 W3-1/2。
- P9：rating 基址统一枚举+换算函数。
- P11：预测审计语义修正或删除；死分支删除。
- **完成判据**：各条用例绿 + 全量重放一致 + 迁移矩阵绿（本波为最大 bump，W0-1 的 archive 在此
  第一次实战）。

**退出门（Wave 3）**：9 项"必须补"与本波相关项裁决完成 + 全部用例绿。

---

## Wave 4 · 规模化（3A 尾 + 3B）：让 5000 题不痛

- **S12–S16（排程放大，先做）**：候选池到期预筛（一刀切）；`antiOscillationPenalty` 用 fitting；
  `usedPracticeUnits` 增量 set；localSwap 限制未选池 top-M；confusable 按共享前置倒排；
  scoreCandidate 一遍扫描；组装 N+1 批量化。**退出门**：JVM 基准 5000 候选 <1s（实测前
  标 UNVERIFIED）。
- **S1/S3/S4/S7/S10（投影热路径）**：upsert 替代全删全插；observation 滑动窗；drain 批内复用
  快照；toSnapshot 只比对指纹；drain 切 Dispatchers.Default。
- **S19**：`knowledge_search_feature` 加 `(subject, search_feature)` 复合索引（DDL + 迁移）；
  退出门：检索 p95 下降、16.6s 长尾消失（真机实测）。
- **3B：S5/S6/S8 + Q2 实战**：ledger 联表批量读；generatePredictions 删除；SHA-256 只在不信任
  边界重算。
- **退出门（Wave 4）**：5000 候选基准 + 检索基准达标。

---

## Wave 5 · 监测与自动化（3C）：先看见，再自动

- **KF-29**：`binding_audit_sample` 表 DDL（sample_id/practice_unit_id/binding_snapshot_json/
  status/verdict/reviewed_at）；抽样 = 每科每周首 5 条新绑定（后台任务）；复核屏 debug 专用。
- **KF-30/KF-07**：自锁打破（auto 标签可被更高置信 auto 覆盖、user 不可）；兜底改落 pseudo。
- **KF-31**：后台重审任务（每周，diff 当前绑定 vs 新提议，落建议清单）；**只有 KF-29 有真实
  错误率数据后才开放**。
- **KF-32（3B）**：重放期 attribution 按 practiceUnitId → 当前绑定重挂；改绑落账本事件触发全量
  重放；upcaster 层解析旧 bindingId（successors 链）。
- **退出门（Wave 5）**：监测管线有数据 + 自动重审在数据达标后开启。

---

## Wave X · 数据依赖（内测后，不排期）

- 离线模拟（§A 配方）；门常数校准；分层经验先验选择；FSRS-7 评估。（fuzz 已裁不做、HLR 已裁删除——裁决 15/6。）
- **进入条件**：真实 review_log ≥ 400 条可预测样本（与 W2-2 同门槛）。

---

## 研究附录（公式全量，可抄）

### A. 离线模拟器设计（srs-benchmark 配方本地化）

- 输入映射：review_log → (card_id, review_time=reviewed_at_utc, rating 1..4, state 0..3,
  duration)；排除 MODEL_JUDGED 与 REVEAL（W1-4 后）。
- 划分：per-user `TimeSeriesSplit` 5 折、**首折排除**、同日复习按 KF-24 首条口径。
- 指标：`LogLoss = −Σ[y·ln p + (1−y)·ln(1−p)] / n`；**RMSE(bins) 新口径**：按（间隔长度、
  复习次数、失误次数）三特征分桶（桶宽公式对齐 fsrs4anki wiki），桶内 √(Σ(p̂−y)²/桶数)；
  AUC（rank-based）。
- 置信：99% CI 用 **BCa bootstrap**：B=2000 次有放回重采样 → 偏置修正 a = Φ⁻¹(#{θ*<θ}/B)、
  加速 b 用 jackknife 估计 → CI = [Ĝ⁻¹(Φ(ẑ + (ẑ+z_α/2)/(1−a(ẑ+z_α/2)))), …]。
- 输出：与默认参数、FSRS-6、候选权重表方案的对比表（Wilcoxon + Cohen's d）。

### B. fsrs-optimizer 对齐清单（已取源码确认）

- loss：`(BCELoss(retentions, labels) × weights).sum() + γ·Σ(w−w_init)²/σ²·batch/epoch`；
- BPTT：前向循环 `state=step(X, state)`，loss 只取每序列末态 `outputs[seq_lens−1,...]`；
- 无早停；best_w 按最低 eval loss；评估 = BCE mean + RMSE(bins) + Brier(MAE/R²)。
- Kotlin 落地步骤：① `SchedulingReplay.predict` 改为序列末态输出；② optimizer 加罚项
  （γ/σ² 对齐官方，γ **已裁**：官方默认，裁决 14）；③ best 跟踪改 eval loss。

### C. FSRS-6 vs FSRS-7

- 21 参数 vs 35 参数；FSRS-7 有小数间隔（elapsed_seconds），会重写同日分支与 KF-23 的
  state 语义。
- **推荐**：留在 FSRS-6（35 参数在单学生数据下更过拟合；srs-benchmark 可后续对比）；
  FSRS-7 列 Wave X 评估项。

### D. 冷启动研究

- β-二项（主）：公式见 W3-1/2；先验 Jeffreys 默认。
- 备选 KT²：层次先修树 + 单步 EM 在线更新（50 学生×5 题可用）——若 KF-16（先修传递）做完后
  仍嫌先验弱，评估升级。
- van der Velde 2024：冷启动下概念中心建模优于学习者身份建模——验证"以 KB 坐标为轴"的设计。

### E. OLM 可视化规范（KF-20 输入）

- 不确定度视觉变量：**透明度=区间宽度、颜色=状态档**；区间而非单点。
- 对齐展示：学生自评（若 D-Q9 提供入口）与系统估计并排 + 差异高亮（UMUAI 证据：提升知识监测）。
- Judy Kay scrutable 原则：每个数可下钻到证据摘要；术语全学生语言。

---

## 待裁项汇总（已全部裁定 2026-09-30 → 台账「3A 前裁决门 · 收口」）

| # | 内容 | 裁定 |
|---|---|---|
| 1 | 看答案最终语义（A/B，W1-4） | **B**：评 AGAIN 计一次遗忘失败（裁决 1） |
| 2 | β-二项先验 α/β | Jeffreys (0.5, 0.5) 起步（裁决 12） |
| 3 | MASTERED 的 θ/ε（旧 0.85 为参照） | θ=0.85 保持 + ε=0.15 起步（裁决 13） |
| 4 | L2 罚项 γ | 对齐 fsrs-optimizer 官方默认（裁决 14） |
| 5 | fuzz 引入与否 | 不做（裁决 15） |
| 6 | FSRS-6 vs FSRS-7 | 留在 FSRS-6；7 列 Wave X 评估项（裁决 16） |
| 7 | 9 项"必须补"逐项 | 逐项已裁（裁决 5-11）；区间原注有误：KF-22 不存在，实为 KF-14~21 + 不确定度（KF-09/10 已排期视为已裁） |
| 8 | 排程权重离线模拟方案 | Wave X 离线模拟定案（裁决 17） |
| 9 | pretest×引导模式交叉（F7） | pretest 不给提示、封顶 L0（裁决 2） |
