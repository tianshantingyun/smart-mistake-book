# E 域证据索引（批次 44，2026-11-04 实跑）

> 本文件是导航与口径说明，**不是证据**；台账 E 行只引用同目录下的
> `TEST-*.xml` 与 `*.cmd.log`。**批次 44 的记录范围**是纯 JVM 算法域，本目录没有
> 设备/模拟器证据，也不把 JVM 结果外推到设备。
>
> **批次 45 补充（不在本目录）**：E 域唯一写进设备升级路径的 E-061，其“真 SQLite
> 事务”面已由设备会话于 2026-11-05 实跑（`emulator-5562`，3/3 绿，含假绿控制）。
> 证据落在 `docs/research/evidence/2026-11-06-batch-45/e-device/`（21 文件）与验收侧
> 镜像 `docs/acceptance/evidence/2026-11-06-batch-45/e-device/`（10 文件）。该行检查的是
> “错因落账**并发**”，用例为串行 `runBlocking`，**故 E-061 仍为 UNVERIFIED，台账字节未改**。
> 详见本文件 §9 与 `2026-11-06-batch-45/e-device/00-index.md` §1.4。

## 1. 身份与源快照

| 项 | 值 |
|---|---|
| 源快照（记录时 HEAD） | `7683640dfd772293a5adc2d7d6c5034454a003d0`（记录期间由共享树其他工作推进，提交内容为 KB 批次 34，未触及任何 E 相关路径） |
| E 相关源码脏文件 | 仅 `core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/TutorJudgedReview.kt`（M，与本域算法无关）；`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/study/` 下文件、`FsrsScheduleMath.kt`、`StudyDayMath.kt` 在记录时均为干净 |
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
  （sha256 `8e8083f54fabfaa3cacebcd3296dd19eef85f8ac9ea28dbce68e8eed86a00720`），
  可复核但**不**参与编译门；可复现运行器为 `e-domain-probe-run.sh`
  （sha256 `b8c9157e532205b1cf1c7848aefd3d8c49bfade5c3db14cb61599c512d33165a`），
  运行日志 `e-domain-probe.cmd.log`
  （sha256 `0a8c050416784b2859e9c316299c225e2d767ee4141808c048cea457d2b4fa68`）。
- **反例尝试（红相）**：`e-domain-probe-mutate.cmd.log`
  （sha256 `dc4d12156cc24f5c179974d8bc72b4eccea0f38c8434d9b6a7d176343768bfb2`）
  用显式 `--mutate` 强制探针报错，输出 `PROBE_RESULT=VIOLATED`、`EXIT=1`，
  证明探针不是恒绿。该红相只用于证明探针可失败，**不进台账 PASS 证据**。
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
# JUnit：两条命令的逐字 CMD 行分别见
#   e-domain-domain-jvm.cmd.log 第 1 行（15 个全限定 --tests）
#   e-domain-data-jvm.cmd.log 第 1 行（20 个全限定 --tests）
# 原样复制该行执行即可，两行均以 EXIT=0 结束。

# 独立探针（编译 + 运行；脚本自己装配类路径、固定 kotlin-stdlib 2.3.21）
bash docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe-run.sh
bash docs/research/evidence/2026-11-05-batch-44/e-domain/e-domain-probe-run.sh --mutate

# 台账门
python -I tools/ci/acceptance_report.py --ledger docs/acceptance/ledger.md
python -I tools/ci/acceptance_lint.py --ledger docs/acceptance/ledger.md
python -I tools/ci/trace_requirements.py --ledger docs/acceptance/ledger.md
```

批次 45 重录后的门结果：report 在 E 域 **0 违规**（批次 44 的 101 条
`PLACEHOLDER_CMD` 归零），但全局 report 仍 `exit 1`，原因不是 E 域——台账里
还有 21 条 FAIL / 73 条 BLOCKED / 438 条 UNVERIFIED（D0/FIX/GATE 三域整体
未判定），`state=FAIL`。全局 trace 仍有其他域 gap，不计入 E 域结论。

## 8. 批次 45 重录（2026-11-05，本段为 E 域命令权威）

批次 44 的 E 域 80 个 PASS 行引用 3 个日志，而日志第 1 行 `CMD:` 是尖括号占位
（类名未展开、探针类路径未展开），`acceptance_report.py` 据此判 101 条
`PLACEHOLDER_CMD`。本段把它换成逐字命令并**重新实跑**，不沿用批次 44 的退出码：

| 项 | 本轮实值 |
|---|---|
| 源快照 | HEAD `87604198feeef198d5dba7343b8c4692c5e8f937`（脏树，未提交改动 98 个已跟踪文件） |
| 运行环境 | JDK 21.0.11、Gradle 9.6.1（`--offline`）、JUnit4 |
| `:core:domain:test` | 15 类 / 161 例 / failures 0 / errors 0 / skipped 0，`GRADLE_EXIT=0` |
| `:core:data:testDebugUnitTest` | 20 类 / 150 例 / failures 0 / errors 0 / skipped 0，`GRADLE_EXIT=0` |
| 探针（绿相） | `PROBE_CHECKS=298277`、`PROBE_RESULT=OK`、`EXIT=0` |
| 探针（红相 `--mutate`） | `PROBE_RESULT=VIOLATED`、`PROBE_COUNTEREXAMPLES=1`、`EXIT=1` |
| 台账 verdict | 80 PASS / 10 UNVERIFIED 不变；重跑全绿，无行降级 |

- 逐类 XML 已用本轮 `test-results` 覆盖（35 份，mtime 2026-11-05），
  计数由 XML 逐份机械复算，不读 Gradle summary。
- 10 条 UNVERIFIED（E-007/016/025/034/043/052/061/070/079/086）**没有命令**，
  因为它们是并发/DST/真设备事务的未测残余；本次重录不把它们改判 PASS。
- 本轮重录改变了 4 个日志与 35 个 XML 的字节，因此 `sha256.txt` 必须重生成；
  `sha256.txt` 自身不列入清单（`--verify` 时排除）。

## 9. 文件清单与哈希（批次 45 收尾最终重生成，2026-11-05 13:40 +0800）

### 9.1 目录构成（48 个文件，`ls` 逐类计数）

| 组 | 个数 | 命名/用途 | 是否进台账 |
|---|---:|---|---|
| `core:domain` JUnit XML | 15 | `TEST-com.tingyun.smartmistakebook.core.domain.*.xml` | 是（`test:`） |
| `core:data.study` JUnit XML | 20 | `TEST-com.tingyun.smartmistakebook.core.data.study.*.xml` | 是（`test:`） |
| JUnit 运行日志 | 2 | `e-domain-domain-jvm.cmd.log`、`e-domain-data-jvm.cmd.log` | 是（`cmd:`） |
| 探针日志 | 2 | `e-domain-probe.cmd.log`（绿相）、`e-domain-probe-mutate.cmd.log`（红相） | 绿相进台账；红相只作反例 |
| 探针源与运行器 | 2 | `e-domain-probe.java.txt`、`e-domain-probe-run.sh` | 否（可复核件） |
| 失败调用留证 | 2 | `e-domain-harness-jvm.log`、`e-domain-jvm-invocation-error.log` | 否（不洗掉失败调用） |
| 门输出快照（**历史态**） | 3 | `e-lint.json`、`e-report.json`、`e-trace.json` | 否（只作历史姿势，不作当前判据，见 §9.4） |
| 索引与清单 | 2 | `00-index.md`、`sha256.txt` | 否 |
| **合计** | **48** | — | — |

### 9.2 哈希清单

- 生成：`sha256sum` 逐文件（仓库相对路径），共 **47** 条；`sha256.txt` 自身不列入。
- 校验（仓库根执行）：`sha256sum -c docs/research/evidence/2026-11-05-batch-44/e-domain/sha256.txt`
  → **47/47 OK、0 FAILED**（2026-11-05 13:40 +0800 本会话实跑）。
- 本轮（批次 45 收尾）只有 `00-index.md` 因 §9 追加而变字节，其余 47 个文件与批次 45
  重录后一致；清单已按当前字节重生成。
- 跨批清单 `docs/research/evidence/2026-11-06-batch-44/E-FILES-SHA256.txt` 的
  `e-domain/00-index.md` 条目同步重绑定到本版字节。

### 9.3 与台账/报告的关系（只读复算，不改台账）

- E 域 90 行：**80 PASS / 10 UNVERIFIED / 0 FAIL / 0 BLOCKED**；这 80 行的 `cmd:` 证据
  均无 `<...>` 占位，`acceptance_report` 的 `PLACEHOLDER_CMD` 在 E 域为 **0**。
- 台账 E 行引用 **0 个** `device:` 证据；E-061 的设备补证因此不进台账证据列
  （`file:` 单类 + `UNVERIFIED` 维持），避免把“真库事务已跑”误读成“并发已覆盖”。
- `sha256.txt` 只绑定字节，不构成任何 verdict；E 域结论只由台账 + 报告判定。

### 9.4 历史门输出快照的引用禁令（三个文件均本会话实读）

| 文件 | 自身绑定 | 关键值 | 为什么不能引用 |
|---|---|---|---|
| `e-report.json` | `ledger_sha256 = 5d773ccf…` | counts `392/1/38/769`、`ok=true`、`exit_code=0` | 它把 **769 条 UNVERIFIED** 的旧台账报成 `ok=true / exit 0`——正是“summary 假绿”形态；当前台账 `bb25ef23…` 的报告是 **exit 1** |
| `e-lint.json` | 只有 `ledger` 路径、无 sha | `violations=0`、`warnings=25` | 25 条 warnings 与当前 29 条不是同一集合，绑的是旧台账 |
| `e-trace.json` | 只有 `ledger` 路径、无 sha | `gaps=311`、`ok=false`、`exit_code=1` | 311 条缺口语义属旧工具缺陷期；当前 trace 是 **gaps=0** |

这三份只是批次 44 录制期的现场快照，保留是为了不洗掉当时的失败形态。
任何“E 域报告 exit 0 / 0 缺口”的说法只能引用 §9.3 的当前实跑（`violations=0`、`gaps=0`、`state=FAIL`、`exit_code=1`）。

