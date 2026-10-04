# KF-29 绑定正确性监测管线 · 首份报告（2026-10-04）

> 依据：`docs/research/2026-09-28-algorithm-fix-plan.md:372-385`（KF-29 实施级规格，权威）、
> `docs/research/2026-09-28-kernel-remediation-roadmap.md` Wave 5、`docs/research/2026-10-04-stage3c-part1-plan.md` 批 1。
> 范围：阶段 3C 前半批 1（KF-29 管线落地）。**本报告不是错误率结论**——见 §5「样本量」。

## 1. 管线做了什么（对应规格四条）

1. **新表 `binding_audit_sample`**（schema 61→62，非破坏迁移）：
   `sample_id` / `practice_unit_id` / `binding_snapshot_json` / `status(PENDING/REVIEWED)` /
   `verdict(CORRECT/WRONG/AMBIGUOUS)` / `reviewed_at`。纯新增：既有表一列不删、一行不动、不回填。
2. **抽样规则**：每科每周首 N 条（默认 5）新绑定入队。
3. **复核屏**：debug 构建专用（`BindingAuditActivity`，不进学生面），显示题面 + 绑定点 + 原文依据，
   一键 verdict（PENDING→REVIEWED，判定只落一次）。
4. **聚合**：错误率按科 / 模型版本聚合（`BindingAuditAggregator`，复核屏顶部同源展示）。

## 2. 口径（报告与复核屏共用同一份定义）

| 项 | 口径 |
| --- | --- |
| 一条样本 | 一次**新绑定写入**（某 practice unit 的绑定集合被确认），不是单个知识点行 |
| 抽样来源 | 组织路径三条写侧：自动接受（`applySuccessfulOrganization`）、用户确认（`confirm`）、离线纠正（`correctConfirmedOrganization`）；幂等重放不重复入队 |
| 周窗口 | UTC 计算的 ISO-8601 周（`YYYY-Www`）。它是配额窗口，不是学生日历；选 UTC 是为"同一次写入 → 同一周键"可复现 |
| 配额 | 每 (科, 周) 最多 N=5 条；计数与插入在**同一个写事务**内完成（并发下恰好封顶）；样本 id 内容寻址，重放不占第二条配额 |
| 判定 | `CORRECT` / `WRONG` / `AMBIGUOUS`；只对 PENDING 行生效，已 REVIEWED 的行不被第二次判定覆盖 |
| 错误率 | `WRONG / (CORRECT + WRONG)`；**AMBIGUOUS 不进分子也不进分母**（按组计数可见） |
| 分母为 0 | 错误率为 null（展示 `—`）——不许把"没有可判样本"写成 0 |
| 分组键 | `subject`（快照 `$.subject`）+ `modelVersion`（快照 `$.modelVersion`；离线纠正无模型版本 → `UNSPECIFIED`） |
| 不可读行 | REVIEWED 但快照解码失败 / verdict 缺失非法的行，计入 `unreadableReviewedCount`，不参与分组，**不从分母里静默蒸发** |
| 快照有界 | 题面超过 8,000 字符截断并置 `questionTruncated`（复核屏显示"超预算已截断"）；步骤证据最多 24 条；编码总预算 128,000 字符 |

## 3. 抽样 hook 点与后台任务选型（本批实施前定的两项）

**hook 点（真实代码）**：`core/data/.../mistake/RoomMistakeOrganizationRepository.kt`
- 自动路：`applySuccessfulOrganizationOnIo` 在 `database.confirmProblemOrganization` 返回且
  `result.created == true` 后构造并落样本；
- 用户路：`confirmOnIo` 同上（快照 `acceptanceSource = USER_CORRECTED`）；
- 离线纠正：`correctConfirmedOrganization` 同上（无模型任务 → `modelVersion = null`）。
- 写侧单点入口仍是 `RoomProblemOrganizationStore.confirm`（core:database）；抽样在它返回后
  紧邻执行，快照里带 `modelVersion` / `providerId`（`persisted.output.modelVersion` /
  `persisted.task.provider?.providerId`），这些信息在 DB 层拿不到，只有组织仓库路径同时握着
  "模型输出 + 已确认命令 + 题面文档"。

**后台任务选型：写入时确定性入队（不新增 WorkManager worker）**，理由：
1. **确定性**：快照内容（模型版本、分类理由、步骤证据、当时题面）只在写入那一刻完整可得；
   周期 worker 事后重建不出来，且"每科每周首 N 条"会依赖扫描时刻而与真实写入顺序漂移。
2. **封顶可靠**：配额计数 + 插入在同一 Room 写事务（`BindingAuditSampleDao.record` 的
   `@Transaction`）内完成，写事务串行化 ⇒ 并发确认下恰好 5 条；后台扫描做不到这一点
   （先读后写会超发）。
3. **D2 判据合规**（`docs/agent-first-refactor-decisions-2026-09-23.md` §D2：后台只做"产出能由
   已有事实重算"的活）：抽样行是绑定事实的**派生审计数据**，可重算、可丢弃、非学习事实，
   零模型额度、纯本地；本方案连"后台"都不需要——它在写入路径内完成，更不越界。
4. **不引入第二写者与系统调度不确定性**：WorkManager 周期任务最小 15 分钟、7 天周期不精确，
   对"首 N 条"这种顺序敏感规则没有收益。既有 WorkManager 用途（`ExportPdfWorker` /
   `BatchImportDriver` / `OrphanAssetGc`）都是"中断恢复/一次性任务"，不是周期抽样。
5. **抽样失败不谎报**：抽样 best-effort——构造或落库异常只记 logcat（`BindingAudit`），不让一次
   已成功的组织写入被报成失败（见 `recordBindingAuditSampleBestEffort` 的 KDoc）。**两条代价如实登记**：
   ① 进程在"确认返回 → 样本插入"之间被杀会丢一条样本（审计辅助，可接受，见 §7 UNVERIFIED）；
   ② 确认调用**多一次"配额计数 + 插入"事务的延迟**（本地 SQLite、毫秒级，但确实加在确认关键路径上；
   配额已满时只多一次计数查询，不插入）。

## 4. 可复跑命令

```bash
# 1) 纯函数口径（周键/样本 id/快照编解码/聚合口径/快照构造）——JVM
./gradlew.bat :core:model:testDebugUnitTest :core:database:testDebugUnitTest :core:data:testDebugUnitTest

# 2) 真库：迁移矩阵 1→62 + 新表形状 + 读写口不变量 + 组织写侧抽样（协调方/真机执行）
./gradlew.bat :core:database:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.BindingAuditSampleInstrumentedTest,com.tingyun.smartmistakebook.core.database.FullMigrationMatrixInstrumentedTest"
./gradlew.bat :core:data:connectedDebugAndroidTest \
  "-Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.data.mistake.MistakeOrganizationRepositoryInstrumentedTest"

# 3) 真机取聚合口径（与报告同源：复核屏顶部就是 BindingAuditAggregator 的输出）
./gradlew.bat :app:installLocalFirstDebug
adb shell am start -n com.tingyun.smartmistakebook.localfirst/com.tingyun.smartmistakebook.BindingAuditActivity
```

（`binding_audit_sample` 表位于应用私有库 `smart-mistake-book.db`；若要离线检视，用
`adb exec-out run-as com.tingyun.smartmistakebook.localfirst cat databases/smart-mistake-book.db > audit.db`，
但**口径以 `BindingAuditAggregator` 为准**——minSdk 23 上不保证 `json_extract` 可用，
不要用 SQL 手算错误率。）

## 5. 样本量（如实）

- **本机/本仓库没有真实复核样本**：抽样只在真实组织写入发生时产生，而本机不跑真机业务；
  本批自验未执行 `connectedAndroidTest`（协调方正式门），也没有从任何真机库取数。
- 因此本报告**不给出任何错误率数字，也不宣称"错误率已达标"**。首份报告能给的是
  "管线可跑 + 口径已钉死"的证据（§6）。
- 错误率数字的产生条件：真机跑过 KF-29 管线 → 复核屏落判若干条 → 复核屏顶部 / 聚合函数
  按 §2 口径输出。下一份报告（样本量 > 0 时）按本文件模板追加真实分组数字。

## 6. 管线自检证据（2026-10-04，本批实测）

| 命令 | 结果 |
| --- | --- |
| `./gradlew.bat :core:model:testDebugUnitTest` | BUILD SUCCESSFUL；`BindingAuditSamplingTest` tests=5 failures=0（含 encode 对超预算载荷的拒绝，F4） |
| `./gradlew.bat :core:database:testDebugUnitTest` | BUILD SUCCESSFUL；`KernelWave9SchemaContractTest` tests=4 failures=0、`BindingAuditAggregatorTest` tests=4 failures=0（含 61→62 纯新增、DDL 与 62.json 逐字一致、列形状钉死） |
| `./gradlew.bat :core:data:testDebugUnitTest` | BUILD SUCCESSFUL；`BindingAuditSampleBuilderTest` tests=7 failures=0（快照带绑定点/理由/步骤证据/模型版本；超预算题面截断并标记；无绑定不产样本；构造失败/落库失败/null 样本三条 best-effort 反例） |
| `./gradlew.bat :core:database:compileDebugAndroidTestKotlin :core:data:compileDebugAndroidTestKotlin` | BUILD SUCCESSFUL（迁移矩阵 1→62、DAO 配额/幂等/落判/**并发封顶**、组织写侧抽样与"抽样失败不谎报"用例已编译，待真机执行） |
| `./gradlew.bat :app:compileLocalFirstDebugKotlin :app:processLocalFirstDebugMainManifest` | BUILD SUCCESSFUL；合并清单 `BindingAuditActivity` 为 `exported="true"`（F3 修复；真机 adb 拉起由协调方复测） |

## 7. 未验证 / 剩余风险

- 仪器化用例（迁移矩阵 1→62、配额封顶、**并发封顶**、判定只落一次、组织写侧抽样快照、
  抽样失败不谎报）**本机未执行**（不跑 connectedAndroidTest，协调方正式门）；本报告只声明"已编译"。
- 复核屏 UI 未真机走通（进入、渲染、一键判定写回）——修复轮已把 `exported` 改为 true 并验证
  合并清单，但 adb 拉起与截图仍待协调方在 debug 安装上实测（本机无设备）。
- "确认返回 → 样本插入"之间的进程死亡窗口会丢一条样本（best-effort 取舍，见 §3.5）。
- 抽样的"新绑定"粒度是**同一周内的绑定集合变化**：样本 id 含 `科:周` 前缀，并对
  (题版本, 绑定 id 集合) 内容寻址——**同一周内**同一集合的重复确认（含幂等重放）得到同一 id、
  不占第二条配额；绑定集合真正变化（新增/换标/换权威来源 → 新 binding id）产生新样本；
  同一集合**下一周**再确认会因周键不同产生新样本并占新周配额（这正是"每科每周首 N 条"的语义）。
  若未来需要"只抽首次绑定"，需在写入侧显式加判据——当前口径按规格原文取"新绑定事件"。
