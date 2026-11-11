# 批次 49 · R1 证据：标定方法论文档的行号与上游取数核对

| 字段 | 值 |
|---|---|
| 关联产物 | `docs/research/2026-11-08-calibration-method.md` |
| 角色 | R1 方法论（只读仓内源码 + `gh api` + `WebFetch`；未跑 Gradle/模拟/设备） |
| 写于 | 2026-11-08 |
| 用途 | 让方法论文档里的每一个 `path:line` 与每一条上游结论都可复核 |

> 所有输出块都是**本次会话实跑的原样 stdout**（含 `head` 截断处已注明）。未做的项列在 §D。

---

## A. 仓内行号核对

```bash
cd "D:/smart mistake book" && check() { printf '%-62s %s\n' "$1:$2" "$(sed -n "${2}p" "$1" | sed 's/^[[:space:]]*//' | cut -c1-72)"; }
```

对下列 `文件 行号` 逐个调用 `check`。输出：

```text
AlgorithmConstants.kt:63      const val MIN_STABILITY_DAYS: Double = 21.0
AlgorithmConstants.kt:66      const val MIN_CURRENT_RETENTION: Double = 0.9
AlgorithmConstants.kt:111     const val EARLY_REVIEW_MAX_RETRIEVABILITY: Double = 0.8
AlgorithmConstants.kt:148     const val EXAM_CAPACITY_RAMP_MAX_EXTRA_FRACTION: Double = 0.5
SchedulingEvaluation.kt:37    val isCorrect: Boolean get() = rating != FsrsRating.AGAIN
SchedulingEvaluation.kt:112   const val MIN_PAIRED_OUTCOMES = 30
SchedulingEvaluation.kt:140   const val MIN_EVALUATION_SAMPLES = 200
SchedulingEvaluation.kt:221   val clamped = min(max(probability, 1e-6), 1.0 - 1e-6)
SchedulingEvaluation.kt:339   trainFraction: Double = 0.7,
SchedulingEvaluation.kt:501   val baseIndices = (0..2).toList() + (4..14).toList() + listOf(20)
SchedulingEvaluation.kt:662   return if (pairs.isEmpty()) 10.0 else SchedulingReplay.bceLogLoss(pairs)
SchedulingEvaluation.kt:701   const val MIN_SAMPLES_FOR_FITTING = 400
SchedulingEvaluation.kt:713   const val TRAIN_FRACTION = 0.8
SchedulingEvaluation.kt:749   const val MIN_SAMPLES = 200
CalibrationReportBuilder.kt:15   private const val BUCKET_COUNT = 10
CalibrationReportBuilder.kt:74   val overallBrier = if (totalCount == 0) {
CalibrationReportBuilder.kt:84   val ece = nonEmptyBuckets.sumOf { bucket ->
ReviewPlannerV2.kt:1055          const val VERSION = "review-planner-v4"
ReviewPlannerV2.kt:1079          private const val MAX_WEAKNESS_ONLY_SHARE = 0.25
ReviewPlannerV2.kt:1092          private const val MAX_SAME_SUBJECT_RUN = 3
ReviewPlanner.kt:22              internal const val KC_DROP_WEIGHT = 2.5
OptimalRetention.kt:34           const val MIN_CARDS = 20
LongRangeSampleLedger.kt:106     val predictableLongRangeCount: Int
FsrsScheduleMath.kt:275          if (reported == FsrsRating.EASY) FsrsRating.GOOD else reported
FsrsParameterRecoveryTest.kt:22  private val trueDecay = -FsrsScheduleMath.DEFAULT_PARAMETERS[20]
ReviewPlannerScaleBenchmarkTest.kt:85  val score = java.lang.Double.toHexString(item.priorityScore)
ReviewCapacityPolicy.kt:66       const val MAX_DAILY_GROWTH: Double = 1.5
StudyReviewPlannerService.kt:393 effectiveBudgetSeconds.takeIf { it > baseBudgetSeconds }
StudyReviewPlannerService.kt:405 baseBudgetSeconds = effectiveBudgetSeconds,
StudyReviewPlannerService.kt:960 const val EXAM_RAMP_DAYS = 14
StudySchedulingCalibration.kt:134  internal fun shouldAdopt(
StudyExperienceRepository.kt:737   val dailyReviewMinutes: Int = 20,
mastery-scheduling-spec.md:108     review_log → 本地跑 BCE+BPTT 优化器 → 替换 w；必须 `evaluate()`（log-loss 目标 0.35–0.45）
mastery-scheduling-spec.md:138     ### 2.17 考前模式
mastery-scheduling-spec.md:148     回测：真实账本重放 + TimeSeriesSplit…
mastery-scheduling-spec.md:241     6. 权重重标定程序（2026-08-29）…
2026-09-25-mastery-mechanism-review.md:66   …输出 FSRS-6 21 参数…最小样本硬门：Anki 要求 ≥400 条 review…
2026-09-25-mastery-mechanism-review.md:105  …FSRS 官方唯一旋钮是 desired retention…
audit-2026-09-12-kernel-readiness.md:535    spec §2.20 的上线门 fsrsBeatsBaseline 全仓零消费者…
```

（行号取自主树 `D:/smart mistake book`，非 `.worktrees/` 下的副本；`mastery-scheduling-spec.md` 与两份研究/审计文档为相对路径 basename 显示。）

---

## B. 上游取数（本会话实跑，原样输出）

### B1 fsrs-optimizer：目标函数与 L2 罚项

```bash
gh api repos/open-spaced-repetition/fsrs-optimizer/contents/src/fsrs_optimizer/fsrs_optimizer.py \
  --jq '.content' 2>/dev/null | base64 -d \
  | grep -n "binary_cross_entropy\|def _loss\|penalty\|gamma\|seq_len\|DEFAULT_PARAMS_STDDEV" | head -40
```

```text
79:DEFAULT_PARAMS_STDDEV_TENSOR = torch.tensor(
116:        hard_penalty = torch.where(rating == 2, self.w[15], 1)
124:            * hard_penalty
215:        :param inputs: shape[seq_len, batch_size, 2]
278:        max_seq_len: float = math.inf,
283:        dataframe["seq_len"] = dataframe["tensor"].map(len)
284:        if dataframe["seq_len"].min() > max_seq_len:
286:        dataframe = dataframe[dataframe["seq_len"] <= max_seq_len]  # type: ignore[assignment]
288:            dataframe = dataframe.sort_values(by=["seq_len"], kind="stable")
289:        del dataframe["seq_len"]
300:        self.seq_len = torch.tensor(
323:        seq_lens = self.seq_len[start_index:end_index]
324:        max_seq_len_val = int(seq_lens.max().item())
325:        sequences_truncated = sequences[:, :max_seq_len_val]
331:            seq_lens,
446:        gamma: float = 1,
448:        max_seq_len: int = 64,
459:        self.gamma = gamma
461:        self.max_seq_len = max_seq_len
476:            train_set, batch_size=self.batch_size, max_seq_len=self.max_seq_len
484:                test_set, batch_size=self.batch_size, max_seq_len=self.max_seq_len
506:                sequences, delta_ts, labels, seq_lens, weights = batch
507:                real_batch_size = seq_lens.shape[0]
509:                stabilities = outputs[seq_lens - 1, torch.arange(real_batch_size), 0]
514:                penalty = (
517:                        / torch.square(DEFAULT_PARAMS_STDDEV_TENSOR)
519:                    * self.gamma
523:                loss += penalty
566:                sequences, delta_ts, labels, seq_lens, weights = (
570:                    dataset.seq_len,
573:                real_batch_size = seq_lens.shape[0]
575:                stabilities = outputs[seq_lens - 1, torch.arange(real_batch_size), 0]
580:                penalty = torch.sum(
582:                    / torch.square(DEFAULT_PARAMS_STDDEV_TENSOR)
584:                loss += penalty * self.gamma / train_set_size
626:                fast_dataset.seq_len - 1, torch.arange(len(fast_dataset))
1424:        gamma: float = 1.0,
1455:                    gamma=gamma,
1483:                gamma=gamma,
```

### B2 Anki：采纳门 / 健康检查 / 400 门槛注释

```bash
gh api repos/ankitects/anki/contents/rslib/src/scheduler/fsrs/params.rs \
  --jq '.content' 2>/dev/null | base64 -d \
  | grep -n "400\|MIN_\|log_loss\|RMSE\|rmse\|health" | head -30
```

```text
62:    pub health_check: bool,
66:fn log_loss_adjustment(r: f32) -> f32 {
73:fn rmse_adjustment(r: f32, c: u32) -> f32 {
78:    /// Note this does not return an error if there are less than 400 items -
92:            health_check,
106:                health_check_passed: None,
153:            let current_log_loss = current_fsrs.evaluate(items.clone(), |_| true)?.log_loss;
155:            let optimized_log_loss = optimized_fsrs.evaluate(items.clone(), |_| true)?.log_loss;
156:            if current_log_loss <= optimized_log_loss {
182:        let health_check_passed = if health_check && input.train_set.len() > 300 {
195:                    let adjusted_log_loss = eval.log_loss / log_loss_adjustment(r);
196:                    let adjusted_rmse = eval.rmse_bins / rmse_adjustment(r, fsrs_items);
198:                    adjusted_log_loss <= 1.11 || adjusted_rmse <= 1.53
207:            health_check_passed,
419:        let interday = entry.interval >= 1 || entry.interval <= -86400;
544:        (next_day_at.elapsed_secs_since(self.id.as_secs()) / 86_400).max(0) as u32
573:    const NEXT_DAY_AT: TimestampSecs = TimestampSecs(86400 * 1000);
576:        ((NEXT_DAY_AT.0 - days_ago * 86400) * 1000).into()
```

### B3 Anki：单牌组杠杆只有 today-only 上限与 desired_retention

```bash
gh api repos/ankitects/anki/contents/proto/anki/decks.proto \
  --jq '.content' 2>/dev/null | base64 -d | grep -n "desired_retention\|today\|limit" | head -20
```

```text
74:      uint32 limit = 1;
75:      uint32 today = 2;
82:    optional uint32 review_limit = 6;
83:    optional uint32 new_limit = 7;
84:    DayLimit review_limit_today = 8;
85:    DayLimit new_limit_today = 9;
87:    optional float desired_retention = 10;
108:      uint32 limit = 2;
163:  // counts after adding children+applying limits
168:  // card counts without children or limits applied
175:  // with children, without any limits
```

### B4 负结果：上游没有 exam / deadline / quota 概念

```bash
gh api repos/ankitects/anki/contents/proto/anki/decks.proto --jq '.content' 2>/dev/null | base64 -d | grep -c -i "exam\|deadline"
gh api repos/open-spaced-repetition/fsrs-rs/contents/src/simulation.rs --jq '.content' 2>/dev/null | base64 -d | grep -c -i "exam\|deadline\|quota"
```

```text
0
0
```

### B5 fsrs-rs 模拟器：优先级钩子 + 每日成本上限 + CMRR 目标

```bash
gh api repos/open-spaced-repetition/fsrs-rs/contents/src/simulation.rs \
  --jq '.content' 2>/dev/null | base64 -d \
  | grep -n "review_priority_fn\|ReviewPriorityFn\|max_cost_perday: 1800\|total_cost\|total_memorized\|cost_per_memorized" | head -20
```

```text
281:pub struct ReviewPriorityFn(Arc<dyn Fn(&Card) -> i32 + Sync + Send>);
283:impl ReviewPriorityFn {
287:    /// use fsrs::{Card, ReviewPriorityFn};
289:    /// let callback = ReviewPriorityFn::new(|card: &Card| card.id as i32);
299:impl PartialEq for ReviewPriorityFn {
305:impl Deref for ReviewPriorityFn {
313:impl std::fmt::Debug for ReviewPriorityFn {
319:impl Default for ReviewPriorityFn {
579:            let total_memorized = memorized_cnt_per_day[memorized_cnt_per_day.len() - 1];
580:            let total_cost = cost_per_day.iter().sum::<f32>();
581:            total_cost / total_memorized
599:    pub review_priority_fn: Option<ReviewPriorityFn>,
613:            max_cost_perday: 1800.0,
622:            review_priority_fn: None,
658:        self.review_priority_fn = Some(ReviewPriorityFn::new(f));
```

`SimulatorConfig` 字段（另一条实跑命令，原样输出）：

```bash
gh api repos/open-spaced-repetition/fsrs-rs/contents/src/simulation.rs \
  --jq '.content' 2>/dev/null | base64 -d | grep -n "pub struct SimulatorConfig" -A 22 | head -26
```

```text
587:pub struct SimulatorConfig {
588-    pub deck_size: usize,
589-    pub learn_span: usize,
590-    pub max_cost_perday: f32,
591-    pub max_ivl: f32,
592-    pub first_rating_prob: [f32; 4],
593-    pub review_rating_prob: [f32; 3],
594-    pub learn_limit: usize,
595-    pub review_limit: usize,
596-    pub new_cards_ignore_review_limit: bool,
597-    pub suspend_after_lapses: Option<u32>,
598-    pub post_scheduling_fn: Option<PostSchedulingFn>,
599-    pub review_priority_fn: Option<ReviewPriorityFn>,
600-    pub review_rating_cost_fn: Option<ReviewRatingCostFn>,
601-    pub learning_step_transitions: [[f32; 4]; 3],
602-    pub relearning_step_transitions: [[f32; 4]; 3],
603-    pub state_rating_costs: [[f32; 4]; 3],
604-    pub learning_step_count: usize,
605-    pub relearning_step_count: usize,
606-}
```

### B6 srs-benchmark 协议

```bash
gh api repos/open-spaced-repetition/srs-benchmark/contents/README.md \
  --jq '.content' 2>/dev/null | base64 -d \
  | grep -n -i "split\|fold\|evaluat\|metric\|log loss\|rmse\|seed" | head -32
```

```text
19:### Data Split
21:In the SRS benchmark, we use a tool called `TimeSeriesSplit`. …older reviews are used for training and newer reviews for testing…
23:Note: TimeSeriesSplit will remove the first split from evaluation. This is because the first split is used for training…
25:RWKV and RMSE-BINS-EXPLOIT do not use TimeSeriesSplit.
27:### Metrics
29:We use three metrics in the SRS benchmark to evaluate how well these algorithms work: Log Loss, AUC, and a custom RMSE that we call RMSE (bins).
31:- Log Loss (also known as Binary Cross Entropy): …lower is better.
32:- Root Mean Square Error in Bins (RMSE (bins)): …predictions and review outcomes are grouped into bins based on three features: the interval length, the number of reviews, and the number of lapses…
33:- AUC…higher is better.
35:Log Loss and RMSE (bins) measure calibration…AUC measures discrimination…
44:…FSRS-5 uses the same-day review data to refine its prediction for the next review. Same-day reviews are used only for training, and not for evaluation.
68:…RWKV…is trained on 5 thousand users and evaluated on another 5 thousand…
82:Total number of reviews for evaluation: 349,923,850.
83:Same-day reviews are not used for evaluation…
140:Total number of reviews for evaluation: 519,296,315.
141:Same-day reviews are used for evaluation. Here the probability of recall is calculated for all reviews…
178:…the images below shows the percentage of users for whom algorithm A (row) has a lower Log Loss than algorithm B (column).
290:Instead of using a 5-way split, train the algorithm and evaluate it on the same data. This can be useful to determine how much the algorithm is overfitting.
310:| `--default` | Evaluate default parameters (no training). | Off |
320:| `--n_splits` | Number of TimeSeriesSplit folds. | Default: `5` |
329:| `--file` | Save per-user evaluation TSVs to `evaluation/<name>/`. | Off |
```

> 说明：本块按可读性对超长行做了中间省略（`…`），行号与断句位置未改；完整原文可用上面的命令重跑。B1/B2/B3/B5 均为原样未删节输出。

---

## C. WebFetch 原文摘录（Anki 官方文档）

| 来源 | 摘录 |
|---|---|
| `https://docs.ankiweb.net/deck-options.html` | "The default is 90%, which offers a good balance of retention and workload."；"Higher retention leads to shorter intervals and more reviews per day."；"Above 90% the workload increases very quickly, and above 97% the workload can be overwhelming."；"**Days to simulate** controls the duration of the simulated study history."；"This feature has been removed as of anki version 25.07"（CMRR） |
| `docs-site/faqs/settings-for-using-anki-to-prepare-for-a-large-exam.mdx` | 未提及任何 "exam mode"；建议按"总天数减去 14–30 天"折算新卡每日数量；临近考试用 filtered deck / custom study |

---

## D. 未跑 / 未验证

- 未跑任何 Gradle 任务、任何 JVM/设备用例、任何模拟（本批模拟器尚未实现）。
- 未对 fsrs-optimizer / anki / fsrs-rs 钉 tag 核对，读的是 `main` HEAD（2026-11-07）；仓内文档引用的 fsrs-optimizer v6.5.0 行号（`:79-100`、`:446`、`:1424`）与本次一致。
- Anki 400 门槛的**强制点**（collection/UI 层）未逐行定位，只采用 `params.rs:78` 的阈值语义。
- 文档中所有"本文件建议"的门槛（§6 #5/#8/#9/#11）是设计推导，未经实测复核。
