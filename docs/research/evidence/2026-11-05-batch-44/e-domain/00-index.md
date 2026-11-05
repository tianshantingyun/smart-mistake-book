# E 域证据索引（批次 44，2026-11-04 实跑）

> 本文件是导航与口径说明，**不是证据**；台账 E 行只引用同目录下的
> `TEST-*.xml` 与 `*.cmd.log`。E 域是纯 JVM 算法域，本批没有设备/模拟器证据，
> 也不把 JVM 结果外推到设备。

## 1. 身份与源快照

| 项 | 值 |
|---|---|
| 源快照（记录时 HEAD） | `7683640dfd772293a5adc2d7d6c5034454a003d0`（记录期间由共享树其他工作推进，提交内容为 KB 批次 34，未触及任何 E 相关路径） |
| E 相关源码脏文件 | 仅 `core/domain/.../TutorJudgedReview.kt`（M，与本域算法无关）；`core/data/.../study/*`、`FsrsScheduleMath.kt`、`StudyDayMath.kt` 在记录时均为干净 |
| JVM 编译产物 | `core/data/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`、`core/domain/build/classes/kotlin/main`、`core/model/build/classes/kotlin/main` |
| 运行环境 | JDK 21.0.11、Gradle 9.6.1（`--offline`）、JUnit4 |
| 记录时间 | 2026-11-04 22:40–23:05（本机） |

**共享树并发披露**：`core:data` 的 `test-results` 目录在复制窗口内曾被并行任务
覆盖一次（出现 telemetry 类）。本轮随后重跑该模块，并在同一命令内按
“20 个 study 类名”立即复制；本目录的 20 份 study XML 已逐类名与测试数核对，
不是其他任务的产物。

## 2. 方法（台账每行的 method 只描述该行断言，不重复整条命令）

### 2.1 JUnit（本轮实跑，EXIT=0）

```bash
# 15 类 / 161 例
./gradlew --offline -q :core:domain:test \
  --tests com.tingyun.smartmistakebook.core.domain.FsrsScheduleMathTest \
  --tests com.tingyun.smartmistakebook.core.domain.FsrsProjectionBehaviorTest \
  --tests com.tingyun.smartmistakebook.core.domain.FsrsParameterRecoveryTest \
  --tests com.tingyun.smartmistakebook.core.domain.ForgettingCurveTest \
  --tests com.tingyun.smartmistakebook.core.domain.ReviewUndoReplayTest \
  --tests com.tingyun.smartmistakebook.core.domain.ReviewPlannerV2Test \
  --tests com.tingyun.smartmistakebook.core.domain.LearningProjectorTest \
  --tests com.tingyun.smartmistakebook.core.domain.StoredRatingBasisTest \
  --tests com.tingyun.smartmistakebook.core.domain.SchedulingEvaluationHarnessTest \
  --tests com.tingyun.smartmistakebook.core.domain.KnowledgeReviewQueueTest \
  --tests com.tingyun.smartmistakebook.core.domain.PlannedReasonCalibrationTest \
  --tests com.tingyun.smartmistakebook.core.domain.MasteryMemoryCardTest \
  --tests com.tingyun.smartmistakebook.core.domain.LongRangeSampleLedgerTest \
  --tests com.tingyun.smartmistakebook.core.domain.AttentionSignalTest \
  --tests com.tingyun.smartmistakebook.core.domain.StudyDayMathTest

# 20 类 / 150 例
./gradlew --offline -q :core:data:testDebugUnitTest \
  --tests com.tingyun.smartmistakebook.core.data.study.StudySchedulingCalibrationTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ReviewCapacitySchedulerTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ExamQuotaPlannerTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPolicyTest \
  --tests com.tingyun.smartmistakebook.core.data.study.MasteryIntervalMappingTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ReviewLogStateTest \
  --tests com.tingyun.smartmistakebook.core.data.study.StudyReviewPlannerExamWindowTest \
  --tests com.tingyun.smartmistakebook.core.data.study.MissedDayRebalancePolicyTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ErrorCauseExtractorTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ErrorCauseGateTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ErrorCauseRetractorTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ErrorCauseChainTest \
  --tests com.tingyun.smartmistakebook.core.data.study.AdaptiveSelectionAssemblerTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ReviewCapacityPreviewReaderTest \
  --tests com.tingyun.smartmistakebook.core.data.study.StudyReviewPlannerServiceCapacityPreviewTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ReviewSelfReportContractTest \
  --tests com.tingyun.smartmistakebook.core.data.study.TutorJudgedReviewSettlerTest \
  --tests com.tingyun.smartmistakebook.core.data.study.LongRangeSampleLedgerReadTest \
  --tests com.tingyun.smartmistakebook.core.data.study.ReadSideMasteryClosureTest \
  --tests com.tingyun.smartmistakebook.core.data.study.LatestCorrectedKeyOrderTest
```

原文与退出码见 `e-domain-domain-jvm.cmd.log`、`e-domain-data-jvm.cmd.log`
（均 `EXIT=0`，无 `SKIPPED`/`-4`/AssumptionViolated 标记）。逐类 XML 见
`TEST-com.tingyun.smartmistakebook.core.domain.*.xml`（15 份）与
`TEST-com.tingyun.smartmistakebook.core.data.study.*.xml`（20 份）。

`e-domain-jvm-invocation-error.log` 是首次调用 `:core:domain:testDebugUnitTest
--tests` 时的**命令形状错误**（该委托任务不接受 `--tests`），不是测试失败；
改用真实任务 `:core:domain:test` 后两模块全绿。保留此日志是为了不把失败调用
洗掉，它不作为任何 PASS 行的证据。

### 2.2 独立 Java 探针（不是重跑 JUnit）

`e-domain-probe.cmd.log`：Java 程序直接调用**真实编译产物**中的
`ReviewCapacityPolicy`、`FsrsScheduleMath`、`StudyDayMath`，用固定种子
（`Random(44)`）对 6 组不变量做随机对抗搜索，共 **298,277** 次断言，结果
`PROBE_RESULT=OK`、`EXIT=0`。

- 覆盖：`dailyQuota` 上下界/零语义/单调性、`examTargetRetention` 窗口与单调性、
  `allocateByDayWeights` 总量守恒与休息日、`examDeficit` 值域、FSRS
  retention 值域/单调/`R(0)=1`、`intervalDays` 界与单调、`shortTermStability`
  成功不降失败必降、`nextDifficulty` 界、`StudyDayMath` 每个偏移与
  `java.time` zone 规则逐值一致 + 04:00/午夜边界。
- 探针源码随证据保存为 `e-domain-probe.java.txt`
  （sha256 `8e8083f5…a00720`），可复核但**不**参与编译门；运行日志
  `e-domain-probe.cmd.log`（sha256 `0593dbd6…b1b7882`）。
- **反例尝试（红相）**：`e-domain-probe-mutate.cmd.log` 用显式 `--mutate`
  强制探针报错，输出 `PROBE_RESULT=VIOLATED`、`EXIT=1`，证明探针不是恒绿。
  该红相只用于证明探针可失败，**不进台账 PASS 证据**。
- 随机搜索未发现任何生产不变量反例；这只能证明“在本次输入空间内无反例”，
  不能证明全输入域。

### 2.3 证据类别的诚实说明

多数 PASS 行引用 `test:` XML + `cmd:` Gradle 日志。两者在工具口径上是两类，
但来自**同一次执行**，不是两个独立实现。覆盖探针的行另加 `cmd:` 探针日志；
探针是独立于 JUnit 的执行路径。本域没有 `device:` 或 `review:` 证据，不冒充。

## 3. 计数

| 项 | 值 |
|---|---|
| 台账 E 行 | **90**（E-001…E-090，无缺号/重号） |
| verdict | **80 PASS / 10 UNVERIFIED / 0 FAIL / 0 BLOCKED** |
| JUnit | 35 类 / **311 例**，0 fail / 0 error / 0 skip |
| 探针 | 298,277 次断言，0 反例 |

## 4. 主题到证据的映射（台账行的 source of truth 是台账本身）

| 主题 | 行 | 主要测试类 |
|---|---|---|
| FSRS 不变量 | E-001…009 | FsrsScheduleMathTest、FsrsProjectionBehaviorTest、ForgettingCurveTest、StoredRatingBasisTest |
| 容量总量 | E-010…018 | ReviewCapacityPolicyTest、ReviewCapacitySchedulerTest、ReviewCapacityPreviewReaderTest |
| 考试配额 | E-019…027 | ExamQuotaPlannerTest、StudyReviewPlannerExamWindowTest |
| 欠账再平衡 | E-028…036 | MissedDayRebalancePolicyTest、ReviewCapacitySchedulerTest |
| 掌握判定 | E-037…045 | MasteryIntervalMappingTest、MasteryMemoryCardTest、ReadSideMasteryClosureTest、LearningProjectorTest |
| 错因候选 | E-046…054 | ErrorCauseExtractorTest、ErrorCauseGateTest、ErrorCauseChainTest |
| 错因落账 | E-055…063 | ErrorCauseChainTest、ErrorCauseGateTest、TutorJudgedReviewSettlerTest |
| 错因撤销 | E-064…072 | ErrorCauseRetractorTest、ErrorCauseChainTest |
| 排序稳定 | E-073…081 | ReviewPlannerV2Test、KnowledgeReviewQueueTest、AdaptiveSelectionAssemblerTest |
| 时间界 04:00/时区/DST | E-082…090 | StudyDayMathTest、ReviewLogStateTest、FsrsProjectionBehaviorTest、LearningProjectorTest |

## 5. UNVERIFIED（10 行，逐条理由与升级路径）

| 行 | 原因 | 升级路径 |
|---|---|---|
| E-007 | FSRS 并发未测 | 新增多线程调用 `FsrsScheduleMath` 的一致性用例 |
| E-016 | 容量纯策略并发未测 | 新增多线程调用 `ReviewCapacityPolicy` 的一致性用例 |
| E-025 | 考试配额并发未测 | 新增多线程调用 `ExamQuotaPlanner.plan` 的确定性用例 |
| E-034 | 再平衡并发未测 | 新增多线程调用 `MissedDayRebalancePolicy.plan` 的确定性用例 |
| E-043 | 投影并发未测 | 新增同 learner 并发投影的幂等/冲突用例 |
| E-052 | 提取器并发未测 | 新增同 scope 并发提取的候选唯一性用例 |
| E-061 | 真 SQLite 事务未跑 | 门禁阶段在 emulator 上跑 `ErrorCauseLedgerInstrumentedTest` |
| E-070 | 撤回并发未测 | 新增并发撤回与落账竞争用例 |
| E-079 | 规划并发未测 | 新增同快照并发 `plan` 的确定性用例 |
| E-086 | DST 切换未测 | `StudyDayMathTest` 只有 EST 冬季探针；补 2026-03-08 与 2026-11-01 切换前后用例 |

这 10 行不参与 PASS 计数，也不被任何 PASS 行引用成通过。

## 6. 观察（不构成 FAIL，但需登记）

1. **“15x”命名与实现不符**：`ReviewCapacityPolicy.MAX_DAILY_GROWTH = 1.5`
   （`ReviewCapacityPolicy.kt:66`），测试名也写 “15x ceiling”
   （如 `ReviewCapacitySchedulerTest.kt:196`），断言实际是 1.5×。行为正确，
   命名是旧口径残留；改名为 1.5× 属独立文案修复，本域不改测试。
2. **`StudySchedulingCalibrationTest` 不等于 FSRS 数学**：骨架把 E-001…009
   指向它，但该类的四个用例是“优化采纳门/稳定性”，FSRS 数学在
   `core:domain`。本域按 C 域同样做法重写行指向真实执行类，避免“骨架猜测”
   被当成证据。
3. **没有真实时钟/时区数据库**：`StudyDayMath` 的时区探针使用固定偏移；
   DST 的 zone 规则变化只有 `America/New_York` 冬季样本，见 E-086。
4. **探针的边界**：随机搜索只覆盖声明的输入空间；`ReviewPlannerV2`、
   `KnowledgeReviewQueue`、错因链未进入探针，只由 JUnit 覆盖。

## 7. 复跑

```bash
# JUnit（见 §2.1 的完整 --tests 列表；两条命令均须 EXIT=0）
./gradlew --offline -q :core:domain:test --tests <15 类>
./gradlew --offline -q :core:data:testDebugUnitTest --tests <20 类>

# 独立探针（类路径按 §1 的编译产物）
javac @args.txt
java -cp "<probe-out>;core/data/intermediates/.../classes;core/domain/build/classes/kotlin/main;core/model/build/classes/kotlin/main;<kotlin-stdlib.jar>" EDomainProbe

# 台账门
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md
python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md
python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md
```

本轮门结果：report `exit 0`（violations 0）、lint `exit 0`（violations 0）、
trace `E gaps = 0`。全局 trace 仍有其他域 gap，不计入 E 域结论。
